#!/usr/bin/env python3
"""Tier-3 POC: extract the 21 ground-truth IfcSpace footprints from DX ARC source IFC
(pure extraction) + frame sanity: do source-IFC world coords match the extracted DB's
world frame? (compare shared wall GUIDs' XY centers)."""
import sqlite3
import numpy as np
import ifcopenshell, ifcopenshell.geom

ROOT = "/home/red1/bim-compiler"
f = ifcopenshell.open(ROOT + "/internal/sources/Ifc2x3_Duplex_Architecture.ifc")
settings = ifcopenshell.geom.settings()
settings.set(settings.USE_WORLD_COORDS, True)

# ground-truth spaces
spaces = []
for sp in f.by_type("IfcSpace"):
    storey = None
    for rel in sp.Decomposes:
        if rel.RelatingObject.is_a("IfcBuildingStorey"):
            storey = rel.RelatingObject.Name
    try:
        shape = ifcopenshell.geom.create_shape(settings, sp)
        v = np.array(shape.geometry.verts).reshape(-1, 3)
        mn, mx = v.min(axis=0), v.max(axis=0)
        spaces.append((sp.GlobalId, sp.Name, sp.LongName, storey,
                       [round(x, 3) for x in mn], [round(x, 3) for x in mx]))
    except Exception as e:
        spaces.append((sp.GlobalId, sp.Name, sp.LongName, storey, "NO-GEOM", str(e)[:40]))
print(f"§GT spaces={len(spaces)}")
for s in spaces:
    print("   ", s)

# frame sanity: wall centers source vs DB
c = sqlite3.connect(f"file:{ROOT}/deploy/buildings/Duplex_extracted.db?mode=ro", uri=True)
db_ctr = dict((g, (x, y, z)) for g, x, y, z in c.execute(
    "SELECT em.guid, t.center_x, t.center_y, t.center_z FROM elements_meta em "
    "JOIN element_transforms t ON em.guid=t.guid WHERE em.ifc_class LIKE 'IfcWall%'"))
n = 0
for w in f.by_type("IfcWallStandardCase"):
    if w.GlobalId in db_ctr and n < 5:
        shape = ifcopenshell.geom.create_shape(settings, w)
        v = np.array(shape.geometry.verts).reshape(-1, 3)
        src_ctr = v.mean(axis=0)  # DB center = vertex centroid per F5
        print(f"§FRAME {w.GlobalId}: src=({src_ctr[0]:.3f},{src_ctr[1]:.3f},{src_ctr[2]:.3f}) "
              f"db={tuple(round(x,3) for x in db_ctr[w.GlobalId])}")
        n += 1
