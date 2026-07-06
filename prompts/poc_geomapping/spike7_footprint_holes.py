#!/usr/bin/env python3
"""Tier-3 POC v2: rooms = interior HOLES of the union of REAL projected wall
footprints (exact triangle projection — no centerline abstraction, no invented
adjacency; a hole exists only where walls genuinely enclose). Relation-backed
bridging (wall_connects) reserved for gaps, applied only if needed + logged.
Also: diagnose v1 (how many walls are non-straight in footprint) + re-score the
flood-fill baseline under BOTH metrics (IoU>=0.5 and the looser centroid-in)."""
import json, sqlite3, sys
import numpy as np
from shapely.geometry import Polygon, box, MultiPolygon
from shapely.ops import unary_union

ROOT = "/home/red1/bim-compiler"
sys.path.insert(0, ROOT + "/scripts")
MIN_AREA = 1.0
IOU_T = 0.5

def load_walls():
    c = sqlite3.connect(f"file:{ROOT}/deploy/buildings/Duplex_extracted.db?mode=ro", uri=True)
    side = json.load(open(f"{ROOT}/geomap/relations_DX.json"))
    meta = {g: cls for g, cls in c.execute("SELECT guid, ifc_class FROM elements_meta")
            if cls.startswith("IfcWall")}
    h_of = dict(c.execute("SELECT guid, geometry_hash FROM element_instances"))
    ctr = {g: (x, y) for g, x, y in c.execute("SELECT guid, center_x, center_y FROM element_transforms")}
    mesh, faces = {}, {}
    for h, vb, fb in c.execute("SELECT geometry_hash, vertices, faces FROM component_geometries"):
        mesh[h] = np.frombuffer(vb, dtype=np.float32).reshape(-1, 3)
        faces[h] = np.frombuffer(fb, dtype=np.int32).reshape(-1, 3)
    walls = {}
    for g in sorted(meta):
        h = h_of.get(g)
        if h not in mesh or g not in ctr:
            continue
        v = mesh[h][:, :2].astype(np.float64) + np.array(ctr[g])
        tris = []
        for a, b, cc in faces[h]:
            p = Polygon([v[a], v[b], v[cc]])
            if p.is_valid and p.area > 1e-9:
                tris.append(p)
        if not tris:
            continue
        fp = unary_union(tris)
        # straightness diagnostic: oriented-rect area vs true footprint area
        rect = fp.minimum_rotated_rectangle
        straight = fp.area / max(rect.area, 1e-9)
        walls[g] = {"fp": fp, "storey": side["elements"].get(g, {}).get("storey"),
                    "straight": straight}
    return walls, side

def rooms_for_storey(walls, storey):
    fps = [w["fp"] for w in walls.values() if w["storey"] == storey]
    if not fps:
        return []
    u = unary_union(fps)
    polys = list(u.geoms) if isinstance(u, MultiPolygon) else [u]
    rooms = []
    for p in polys:
        for ring in p.interiors:
            r = Polygon(ring)
            if r.area >= MIN_AREA:
                rooms.append(r)
    return rooms

def iou(poly, gt_box):
    u = poly.union(gt_box).area
    return poly.intersection(gt_box).area / u if u > 0 else 0.0

def score(cands, gt, metric="iou"):
    matched_gt, matched_cand, pairs = set(), set(), []
    for k, (gg, name, storey, mn, mx) in enumerate(gt):
        gt_box = box(mn[0], mn[1], mx[0], mx[1])
        best, bi = None, 0.0
        for ci, poly in enumerate(cands.get(storey, [])):
            if metric == "iou":
                v = iou(poly, gt_box)
            else:  # centroid-in: GT centroid inside candidate (the folklore-5/21-style loose match)
                v = 1.0 if poly.contains(gt_box.centroid) else 0.0
            if v > bi:
                bi, best = v, (storey, ci)
        thr = IOU_T if metric == "iou" else 0.5
        if bi >= thr and best not in matched_cand:
            matched_gt.add(k); matched_cand.add(best); pairs.append((name, storey, round(bi, 2)))
    n_cand = sum(len(v) for v in cands.values())
    return matched_gt, n_cand, len(matched_cand), pairs

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
        out.append((sp.GlobalId, f"{sp.Name} {sp.LongName}", storey, v.min(axis=0)[:2], v.max(axis=0)[:2]))
    return sorted(out, key=lambda s: s[0])

gt = load_gt()
walls, side = load_walls()
bent = {g: round(w["straight"], 2) for g, w in walls.items() if w["straight"] < 0.85}
print(f"§WALLS {len(walls)} loaded; NON-STRAIGHT footprints (fp/rect<0.85): {len(bent)} -> {bent}")

storeys = sorted(set(s for _, _, s, _, _ in gt))
cands = {s: rooms_for_storey(walls, s) for s in storeys}
for s in storeys:
    print(f"§HOLES {s!r}: rooms={len(cands[s])} areas={[round(r.area,1) for r in cands[s]]}")

for metric in ("iou", "centroid"):
    m_gt, n_cand, m_cand, pairs = score(cands, gt, metric)
    print(f"§TOPOLOGY[{metric}] recall={len(m_gt)}/{len(gt)} ({100*len(m_gt)/len(gt):.0f}%) "
          f"precision={m_cand}/{n_cand} ({100*m_cand/max(n_cand,1):.0f}%)")
    if metric == "iou":
        for p in pairs: print("    matched:", p)
        print("    missed:", [(g[1], g[2]) for k, g in enumerate(gt) if k not in m_gt])

# baseline under both metrics
import compile_rooms as cr
c = sqlite3.connect(f"file:{ROOT}/deploy/buildings/Duplex_extracted.db?mode=ro", uri=True).cursor()
by = cr.storey_walls(c)
stairs = [s for lst in cr.storey_stairs(c).values() for s in lst]
bl = {}
for st_name, ws in by.items():
    if len(ws) < 3: continue
    rooms = cr.flood_rooms(ws, stairs)
    bl[st_name] = [box(r["cx"]-r["sx"]/2, r["cy"]-r["sy"]/2, r["cx"]+r["sx"]/2, r["cy"]+r["sy"]/2) for r in rooms]
for metric in ("iou", "centroid"):
    m_gt, n_cand, m_cand, pairs = score(bl, gt, metric)
    print(f"§BASELINE[{metric}] recall={len(m_gt)}/{len(gt)} ({100*len(m_gt)/len(gt):.0f}%) "
          f"precision={m_cand}/{n_cand} ({100*m_cand/max(n_cand,1):.0f}%)")
