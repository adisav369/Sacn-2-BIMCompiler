#!/usr/bin/env python3
"""Tier-3 POC v3: floor-plan SECTION CUT. Per storey, take every wall whose real
Z-interval straddles the storey's mid-height (walls selected by GEOMETRY, not by
the storey label — v2 diagnosis: labels split connected walls across storeys),
union their projected footprints, rooms = interior holes. Blind to IfcSpace.
Storey elevations from IfcBuildingStorey (relational, not room data)."""
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
    meta = {g: cls for g, cls in c.execute("SELECT guid, ifc_class FROM elements_meta")
            if cls.startswith("IfcWall")}
    h_of = dict(c.execute("SELECT guid, geometry_hash FROM element_instances"))
    ctr = {g: (x, y, z) for g, x, y, z in c.execute(
        "SELECT guid, center_x, center_y, center_z FROM element_transforms")}
    mesh, tris = {}, {}
    for h, vb, fb in c.execute("SELECT geometry_hash, vertices, faces FROM component_geometries"):
        mesh[h] = np.frombuffer(vb, dtype=np.float32).reshape(-1, 3)
        tris[h] = np.frombuffer(fb, dtype=np.int32).reshape(-1, 3)
    walls = {}
    for g in sorted(meta):
        h = h_of.get(g)
        if h not in mesh or g not in ctr:
            continue
        v = mesh[h].astype(np.float64) + np.array(ctr[g])
        polys = []
        for a, b, cc in tris[h]:
            p = Polygon([v[a][:2], v[b][:2], v[cc][:2]])
            if p.is_valid and p.area > 1e-9:
                polys.append(p)
        if not polys:
            continue
        walls[g] = {"fp": unary_union(polys), "zmin": v[:, 2].min(), "zmax": v[:, 2].max()}
    return walls

def rooms_at_height(walls, h):
    fps = [w["fp"] for w in walls.values() if w["zmin"] <= h <= w["zmax"]]
    u = unary_union(fps)
    polys = list(u.geoms) if isinstance(u, MultiPolygon) else [u]
    rooms = []
    for p in polys:
        for ring in p.interiors:
            r = Polygon(ring)
            if r.area >= MIN_AREA:
                rooms.append(r)
    return rooms, len(fps)

def iou(poly, gt_box):
    u = poly.union(gt_box).area
    return poly.intersection(gt_box).area / u if u > 0 else 0.0

def score(cands, gt, metric="iou"):
    matched_gt, matched_cand, pairs = set(), set(), []
    for k, (gg, name, storey, mn, mx) in enumerate(gt):
        gt_box = box(mn[0], mn[1], mx[0], mx[1])
        best, bi = None, 0.0
        for ci, poly in enumerate(cands.get(storey, [])):
            v = iou(poly, gt_box) if metric == "iou" else (1.0 if poly.buffer(0).contains(gt_box.centroid) else 0.0)
            if v > bi:
                bi, best = v, (storey, ci)
        if bi >= 0.5 and best not in matched_cand:
            matched_gt.add(k); matched_cand.add(best); pairs.append((name, storey, round(bi, 2)))
    n_cand = sum(len(v) for v in cands.values())
    return matched_gt, n_cand, len(matched_cand), pairs

def load_gt_and_storeys():
    import ifcopenshell, ifcopenshell.geom
    f = ifcopenshell.open(ROOT + "/internal/sources/Ifc2x3_Duplex_Architecture.ifc")
    st = ifcopenshell.geom.settings(); st.set(st.USE_WORLD_COORDS, True)
    gt = []
    for sp in f.by_type("IfcSpace"):
        storey = None
        for rel in sp.Decomposes:
            if rel.RelatingObject.is_a("IfcBuildingStorey"):
                storey = rel.RelatingObject.Name
        v = np.array(ifcopenshell.geom.create_shape(st, sp).geometry.verts).reshape(-1, 3)
        gt.append((sp.GlobalId, f"{sp.Name} {sp.LongName}", storey, v.min(axis=0)[:2], v.max(axis=0)[:2]))
    # storey elevations: IfcBuildingStorey.Elevation — building structure, not room data
    lv = {s.Name: float(s.Elevation or 0) for s in f.by_type("IfcBuildingStorey")}
    return sorted(gt, key=lambda s: s[0]), lv

gt, levels = load_gt_and_storeys()
print(f"§LEVELS {levels}")
walls = load_walls()
print(f"§WALLS {len(walls)}")

# section height per storey: elevation + 1.3m (mid-door height, standard plan cut)
names = sorted(levels, key=lambda n: levels[n])
cands = {}
for name in set(s for _, _, s, _, _ in gt):
    if name not in levels:
        continue
    h = levels[name] + 1.3
    rooms, nsel = rooms_at_height(walls, h)
    cands[name] = rooms
    print(f"§CUT {name!r} at z={h:.2f}: walls-in-cut={nsel} rooms={len(rooms)} "
          f"areas={sorted(round(r.area,1) for r in rooms)}")

for metric in ("iou", "centroid"):
    m_gt, n_cand, m_cand, pairs = score(cands, gt, metric)
    print(f"§SECTION-CUT[{metric}] recall={len(m_gt)}/{len(gt)} ({100*len(m_gt)/len(gt):.0f}%) "
          f"precision={m_cand}/{n_cand} ({100*m_cand/max(n_cand,1):.0f}%)")
    if metric == "iou":
        for p in pairs: print("    matched:", p)
        print("    missed:", [(g[1], g[2]) for k, g in enumerate(gt) if k not in m_gt])
