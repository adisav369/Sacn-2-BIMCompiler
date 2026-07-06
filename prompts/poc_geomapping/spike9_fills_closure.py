#!/usr/bin/env python3
"""Tier-3 POC v4: section-cut wall-footprint union + RELATION-BACKED doorway
closure: for each rel_fills_host (IfcRelVoidsElement+IfcRelFillsElement, R21),
close the host wall's opening with the filling element's real width along the
host's axis. Every closure cites its relation. Rooms = interior holes. Blind
to IfcSpace. Open-plan virtual boundaries are structurally unreachable —
reported, not papered over."""
import json, sqlite3, sys
import numpy as np
from shapely.geometry import Polygon, box, MultiPolygon
from shapely.ops import unary_union

ROOT = "/home/red1/bim-compiler"
sys.path.insert(0, ROOT + "/scripts")
MIN_AREA = 1.0
CUT_OFFSET = 1.3

def load_elements():
    c = sqlite3.connect(f"file:{ROOT}/deploy/buildings/Duplex_extracted.db?mode=ro", uri=True)
    meta = dict(c.execute("SELECT guid, ifc_class FROM elements_meta"))
    h_of = dict(c.execute("SELECT guid, geometry_hash FROM element_instances"))
    ctr = {g: (x, y, z) for g, x, y, z in c.execute(
        "SELECT guid, center_x, center_y, center_z FROM element_transforms")}
    mesh, tri = {}, {}
    for h, vb, fb in c.execute("SELECT geometry_hash, vertices, faces FROM component_geometries"):
        mesh[h] = np.frombuffer(vb, dtype=np.float32).reshape(-1, 3)
        tri[h] = np.frombuffer(fb, dtype=np.int32).reshape(-1, 3)
    els = {}
    for g in sorted(meta):
        h = h_of.get(g)
        if h not in mesh or g not in ctr:
            continue
        v = mesh[h].astype(np.float64) + np.array(ctr[g])
        polys = []
        for a, b, cc in tri[h]:
            p = Polygon([v[a][:2], v[b][:2], v[cc][:2]])
            if p.is_valid and p.area > 1e-9:
                polys.append(p)
        if not polys:
            continue
        xy = v[:, :2]
        cen = xy.mean(axis=0)
        cc2 = xy - cen
        w_, vec = np.linalg.eigh(cc2.T @ cc2 / len(xy))
        els[g] = {"cls": meta[g], "fp": unary_union(polys),
                  "zmin": v[:, 2].min(), "zmax": v[:, 2].max(),
                  "cen": cen, "axis": vec[:, 1],
                  "long": (cc2 @ vec[:, 1]).max() - (cc2 @ vec[:, 1]).min(),
                  "thick": (cc2 @ vec[:, 0]).max() - (cc2 @ vec[:, 0]).min()}
    return els

def closure_rects(els, fills):
    """One rectangle per R21 fill: filling's width along HOST axis x host thickness,
    centered at the filling's centroid projected onto the host centerline."""
    rects, cites = [], []
    for filling, host in sorted(fills.items()):
        if filling not in els or host not in els:
            continue
        F, H = els[filling], els[host]
        ax, cen = H["axis"], H["cen"]
        t = float((F["cen"] - cen) @ ax)
        c = cen + ax * t                          # opening center on host centerline
        half_w = max(F["long"], F["thick"]) / 2   # real leaf width (leaf may be modeled open)
        half_t = max(H["thick"], 0.05) / 2
        n = np.array([-ax[1], ax[0]])
        quad = [c - ax*half_w - n*half_t, c + ax*half_w - n*half_t,
                c + ax*half_w + n*half_t, c - ax*half_w + n*half_t]
        rects.append(Polygon([tuple(p) for p in quad]))
        cites.append((filling, host))
    return rects, cites

def rooms_at(els, closures, h):
    fps = [e["fp"] for e in els.values()
           if e["cls"].startswith(("IfcWall", "IfcCurtainWall", "IfcColumn"))
           and e["zmin"] <= h <= e["zmax"]]
    u = unary_union(fps + closures)
    if u.geom_type == "GeometryCollection":
        u = unary_union([g for g in u.geoms if g.geom_type in ("Polygon", "MultiPolygon")])
    polys = list(u.geoms) if isinstance(u, MultiPolygon) else [u]
    rooms = []
    for p in polys:
        if p.geom_type != "Polygon":
            continue
        for ring in p.interiors:
            r = Polygon(ring)
            if r.area >= MIN_AREA:
                rooms.append(r)
    return rooms, len(fps)

def iou(poly, gt_box):
    u = poly.union(gt_box).area
    return poly.intersection(gt_box).area / u if u > 0 else 0.0

def load_gt_and_levels():
    import ifcopenshell, ifcopenshell.geom
    f = ifcopenshell.open(ROOT + "/internal/sources/Ifc2x3_Duplex_Architecture.ifc")
    st = ifcopenshell.geom.settings(); st.set(st.USE_WORLD_COORDS, True)
    gt = []
    for sp in f.by_type("IfcSpace"):
        storey = None
        for rel in sp.Decomposes:
            if rel.RelatingObject.is_a("IfcBuildingStorey"):
                storey = rel.RelatingObject.Name
        shape = ifcopenshell.geom.create_shape(st, sp)
        v = np.array(shape.geometry.verts, dtype=np.float64).reshape(-1, 3)
        del shape
        gt.append((sp.GlobalId, f"{sp.Name} {sp.LongName}", storey, v.min(axis=0)[:2].copy(), v.max(axis=0)[:2].copy()))
    lv = {s.Name: float(s.Elevation or 0) for s in f.by_type("IfcBuildingStorey")}
    return sorted(gt, key=lambda s: s[0]), lv

gt, levels = load_gt_and_levels()
els = load_elements()
side = json.load(open(f"{ROOT}/geomap/relations_DX.json"))
fills = {g: e["fills_host"] for g, e in side["elements"].items() if "fills_host" in e}
closures, cites = closure_rects(els, fills)
print(f"§CLOSURES {len(closures)} relation-backed opening closures (R21 fills_host)")

cands = {}
for name in sorted(set(s for _, _, s, _, _ in gt)):
    if name not in levels:
        continue
    rooms, nsel = rooms_at(els, closures, levels[name] + CUT_OFFSET)
    cands[name] = rooms
    print(f"§CUT {name!r} z={levels[name]+CUT_OFFSET:.2f}: walls={nsel} rooms={len(rooms)} "
          f"areas={sorted(round(r.area,1) for r in rooms)}")

matched_gt, matched_cand, pairs = set(), set(), []
for k, (gg, name, storey, mn, mx) in enumerate(gt):
    gt_box = box(mn[0], mn[1], mx[0], mx[1])
    best, bi = None, 0.0
    for ci, poly in enumerate(cands.get(storey, [])):
        v = iou(poly, gt_box)
        if v > bi:
            bi, best = v, (storey, ci)
    if bi >= 0.5 and best not in matched_cand:
        matched_gt.add(k); matched_cand.add(best); pairs.append((name, storey, round(bi, 2)))
n_cand = sum(len(v) for v in cands.values())
print(f"\n§TIER3 recall={len(matched_gt)}/{len(gt)} ({100*len(matched_gt)/len(gt):.0f}%) "
      f"precision={len(matched_cand)}/{n_cand} ({100*len(matched_cand)/max(n_cand,1):.0f}%) [IoU>=0.5]")
for p in pairs:
    print("    matched:", p)
print("    missed:", [(g[1], g[2]) for k2, g in enumerate(gt) if k2 not in matched_gt])
