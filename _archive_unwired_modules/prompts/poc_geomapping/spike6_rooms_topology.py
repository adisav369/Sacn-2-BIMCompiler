#!/usr/bin/env python3
"""Tier-3 POC (§TIER-3-FABLE5): room recovery via wall-centerline planar-graph
cycle detection, BLIND to IfcSpace. Graph edges snapped ONLY by the mined
IfcRelConnectsPathElements relations (+ logged real-contact fallback). Scored
vs the 21 ground-truth DX spaces, IoU>=0.5, same harness for the flood-fill
baseline. Deterministic throughout."""
import json, sqlite3, sys, math
import numpy as np
from shapely.geometry import LineString, MultiLineString, box, Polygon
from shapely.ops import unary_union, polygonize

ROOT = "/home/red1/bim-compiler"
sys.path.insert(0, ROOT + "/scripts")

MAX_EXTEND = 0.75   # m — refuse relation-snaps needing more than this reach (log)
MIN_AREA = 1.0      # m^2 — GT includes a 1.4 m^2 Utility; slivers below this are wall cavities
MAX_FRAC = 0.60     # drop faces covering >60% of the storey hull (not a room)
IOU_T = 0.5

# ── load DX walls with world-frame footprints ──
def load_walls():
    c = sqlite3.connect(f"file:{ROOT}/deploy/buildings/Duplex_extracted.db?mode=ro", uri=True)
    side = json.load(open(f"{ROOT}/geomap/relations_DX.json"))
    meta = {g: cls for g, cls in c.execute("SELECT guid, ifc_class FROM elements_meta")
            if cls.startswith("IfcWall")}
    h_of = dict(c.execute("SELECT guid, geometry_hash FROM element_instances"))
    ctr = {g: (x, y) for g, x, y in c.execute("SELECT guid, center_x, center_y FROM element_transforms")}
    mesh = {}
    for h, blob in c.execute("SELECT geometry_hash, vertices FROM component_geometries"):
        mesh[h] = np.frombuffer(blob, dtype=np.float32).reshape(-1, 3)
    walls = {}
    for g in sorted(meta):
        if g not in h_of or h_of[g] not in mesh or g not in ctr:
            continue
        v = mesh[h_of[g]][:, :2].astype(np.float64) + np.array(ctr[g])  # world XY
        cen = v.mean(axis=0)
        cc = v - cen
        w_, vec = np.linalg.eigh(cc.T @ cc / len(v))
        axis = vec[:, 1]                    # long axis (larger eigenvalue)
        t = cc @ axis
        p0, p1 = cen + axis * t.min(), cen + axis * t.max()
        thick = (cc @ vec[:, 0]).max() - (cc @ vec[:, 0]).min()
        storey = side["elements"].get(g, {}).get("storey")
        walls[g] = {"p0": p0, "p1": p1, "thick": thick, "storey": storey, "len": t.max() - t.min()}
    return walls, side

def _isect(w1, w2):
    """Infinite-line intersection of two wall centerlines. None if near-parallel."""
    p, r = w1["p0"], w1["p1"] - w1["p0"]
    q, s = w2["p0"], w2["p1"] - w2["p0"]
    denom = r[0] * s[1] - r[1] * s[0]
    if abs(denom) < 1e-9 * max(1.0, np.linalg.norm(r) * np.linalg.norm(s)):
        return None
    t = ((q - p)[0] * s[1] - (q - p)[1] * s[0]) / denom
    return p + t * r

def snap_by_relations(walls, connects):
    """Move endpoints named by IfcRelConnectsPathElements to the real centerline
    intersection. RelatingConnectionType describes side a; ATPATH = other tees in."""
    used = refused = 0
    for e in connects:
        a, b = e["a"], e["b"]
        if a not in walls or b not in walls:
            continue
        X = _isect(walls[a], walls[b])
        if X is None:
            refused += 1
            continue
        moved = False
        for guid, end in ((a, e["a_end"]), (b, e["b_end"])):
            w = walls[guid]
            if end == "ATSTART":
                if np.linalg.norm(X - w["p0"]) <= MAX_EXTEND: w["p0"] = X; moved = True
                else: refused += 1
            elif end == "ATEND":
                if np.linalg.norm(X - w["p1"]) <= MAX_EXTEND: w["p1"] = X; moved = True
                else: refused += 1
            # ATPATH: the other wall tees into this one mid-span — no endpoint to move here;
            # the union/polygonize step nodes the crossing automatically.
        used += 1 if moved else 0
    return used, refused

def rooms_for_storey(walls, storey):
    segs = [LineString([w["p0"], w["p1"]]) for w in walls.values()
            if w["storey"] == storey and w["len"] > 0.05]
    if len(segs) < 4:
        return [], 0.0
    merged = unary_union(MultiLineString(segs))
    faces = list(polygonize(merged))
    hull = unary_union(segs).convex_hull.area
    rooms = [f for f in faces
             if f.area >= MIN_AREA and (hull <= 0 or f.area <= hull * MAX_FRAC)]
    return rooms, hull

def iou(poly, gt_box):
    i = poly.intersection(gt_box).area
    u = poly.union(gt_box).area
    return i / u if u > 0 else 0.0

def score(cands_by_storey, gt):
    matched_gt, matched_cand, pairs = set(), set(), []
    for k, (gg, name, storey, mn, mx) in enumerate(gt):
        gt_box = box(mn[0], mn[1], mx[0], mx[1])
        best, bi = None, 0.0
        for ci, poly in enumerate(cands_by_storey.get(storey, [])):
            v = iou(poly, gt_box)
            if v > bi:
                bi, best = v, (storey, ci)
        if bi >= IOU_T:
            matched_gt.add(k); matched_cand.add(best); pairs.append((name, storey, round(bi, 2)))
    n_cand = sum(len(v) for v in cands_by_storey.values())
    return matched_gt, n_cand, len(matched_cand), pairs

# ── ground truth (pure extraction, spike5 logic) ──
def load_gt():
    import ifcopenshell, ifcopenshell.geom
    f = ifcopenshell.open(ROOT + "/internal/sources/Ifc2x3_Duplex_Architecture.ifc")
    st = ifcopenshell.geom.settings(); st.set(st.USE_WORLD_COORDS, True)
    out = []
    for sp in f.by_type("IfcSpace"):
        storey = None
        for rel in sp.Decomposes:
            if rel.RelatingObject.is_a("IfcBuildingStorey"):
                storey = rel.RelatingObject.Name
        v = np.array(ifcopenshell.geom.create_shape(st, sp).geometry.verts).reshape(-1, 3)
        out.append((sp.GlobalId, f"{sp.Name} {sp.LongName}", storey,
                    v.min(axis=0)[:2], v.max(axis=0)[:2]))
    return sorted(out, key=lambda s: s[0])

gt = load_gt()
walls, side = load_walls()
print(f"§WALLS loaded={len(walls)} storeys={sorted(set(w['storey'] for w in walls.values()))}")
used, refused = snap_by_relations(walls, side["wall_connects"])
print(f"§SNAP relation-snaps applied={used} refused(parallel/too-far)={refused} of {len(side['wall_connects'])} edges")

storeys = sorted(set(s for _, _, s, _, _ in gt))
cands = {}
for s in storeys:
    rooms, hull = rooms_for_storey(walls, s)
    cands[s] = rooms
    print(f"§FACES {s!r}: rooms={len(rooms)} areas={[round(r.area,1) for r in rooms]}")

m_gt, n_cand, m_cand, pairs = score(cands, gt)
print(f"\n§TOPOLOGY recall={len(m_gt)}/{len(gt)} ({100*len(m_gt)/len(gt):.0f}%) "
      f"precision={m_cand}/{n_cand} ({100*m_cand/max(n_cand,1):.0f}%) [IoU>={IOU_T}]")
for p in pairs: print("    matched:", p)
missed = [(g[1], g[2]) for k, g in enumerate(gt) if k not in m_gt]
print("    missed:", missed)

# ── baseline: compile_rooms flood-fill, same metric ──
import compile_rooms as cr
c = sqlite3.connect(f"file:{ROOT}/deploy/buildings/Duplex_extracted.db?mode=ro", uri=True).cursor()
by = cr.storey_walls(c)
stairs = [s for lst in cr.storey_stairs(c).values() for s in lst]
bl = {}
for st_name, ws in by.items():
    if len(ws) < 3: continue
    rooms = cr.flood_rooms(ws, stairs)
    bl[st_name] = [box(r["cx"]-r["sx"]/2, r["cy"]-r["sy"]/2, r["cx"]+r["sx"]/2, r["cy"]+r["sy"]/2) for r in rooms]
    print(f"§BASELINE {st_name!r}: rooms={len(rooms)}")
m_gt2, n2, mc2, pairs2 = score(bl, gt)
print(f"§BASELINE recall={len(m_gt2)}/{len(gt)} ({100*len(m_gt2)/len(gt):.0f}%) "
      f"precision={mc2}/{n2} ({100*mc2/max(n2,1):.0f}%) [same metric]")
for p in pairs2: print("    matched:", p)
