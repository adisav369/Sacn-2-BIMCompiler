#!/usr/bin/env python3
"""
compile_rooms.py — COMPILE rooms from wall/door enclosure (deterministic, not invented).

Per storey: rasterize wall + door footprints into a 2D plan grid, flood-fill the exterior
from the border, and treat each connected pocket of free space that the exterior cannot reach
as a ROOM (enclosed by walls). Output = spatial_structure IfcSpace rows (guid/name/parent +
center_x/y/z, size_x/y/z) + rel_contained_in_space (elements whose XY centre falls in a room).

This is COMPILE, not invent: every room is a region enclosed by REAL wall geometry. guid/name
are deterministic labels for the computed cell. Geometry tables are never touched.

Usage:
  compile_rooms.py <db>            # DRY: print detected rooms per storey, write nothing
  compile_rooms.py <db> --write    # inject spatial_structure + rel_contained_in_space
"""
import sqlite3, sys, math
import numpy as np

RES = 0.20          # grid cell size (m)
MIN_AREA = 4.0      # m^2 — drop slivers / wall cavities
MAX_AREA_ABS = 150.0  # m^2 — drop exterior-leak blobs (a real room is rarely bigger)
MAX_AREA_FRAC = 0.92  # also drop anything ~the whole storey plan
SEAL = 2            # dilate walls this many cells (×RES) to close hairline corner/door gaps
WALL_LIKE = ("IfcWall%", "IfcDoor%", "IfcCurtainWall%", "IfcColumn%", "IfcWindow%")
# §STAIR-EXCLUDE: a stairwell is a wall-enclosed pocket, so the flood-fill flags it as a "room".
# It is circulation, NOT a room. Reject any compiled pocket that a stair footprint substantially
# overlaps. IfcStair% LIKE also covers IfcStairFlight. (User: "staircase is also marked as room".)
STAIR_LIKE = ("IfcStair%", "IfcRamp%")
STAIR_OVERLAP_REJECT = 0.35   # drop a pocket if a stair footprint covers ≥35% of its area
# §APPROX: these rooms are COMPILED from wall enclosure (flood-fill), NOT extracted IfcSpace.
# Validated ~5/21 recall on ground-truth Duplex → treat as APPROXIMATE. Labelled '≈' + COMPILED.

def storey_walls(c):
    cond = " OR ".join("m.ifc_class LIKE ?" for _ in WALL_LIKE)
    rows = c.execute(
        f"SELECT m.storey, t.center_x,t.center_y,t.center_z, t.bbox_x,t.bbox_y,t.bbox_z "
        f"FROM elements_meta m JOIN element_transforms t ON t.guid=m.guid "
        f"WHERE ({cond}) AND t.center_x IS NOT NULL", WALL_LIKE).fetchall()
    by = {}
    for st, cx, cy, cz, bx, by_, bz in rows:
        by.setdefault(st or "Unknown", []).append((cx, cy, cz, bx, by_, bz))
    return by

def storey_stairs(c):
    """Per-storey stair/ramp footprints (cx,cy,bx,by) — circulation cores to exclude from rooms."""
    cond = " OR ".join("m.ifc_class LIKE ?" for _ in STAIR_LIKE)
    rows = c.execute(
        f"SELECT m.storey, t.center_x,t.center_y, t.bbox_x,t.bbox_y "
        f"FROM elements_meta m JOIN element_transforms t ON t.guid=m.guid "
        f"WHERE ({cond}) AND t.center_x IS NOT NULL", STAIR_LIKE).fetchall()
    by = {}
    for st, cx, cy, bx, by_ in rows:
        by.setdefault(st or "Unknown", []).append((cx, cy, bx, by_))
    return by

def _stair_overlap_frac(rx0, ry0, rx1, ry1, stairs):
    """Largest fraction of room rect [rx0,ry0,rx1,ry1] covered by any single stair footprint."""
    room_area = max(1e-6, (rx1 - rx0) * (ry1 - ry0))
    best = 0.0
    for scx, scy, sbx, sby in stairs:
        sx0, sx1 = scx - sbx / 2, scx + sbx / 2
        sy0, sy1 = scy - sby / 2, scy + sby / 2
        ox = max(0.0, min(rx1, sx1) - max(rx0, sx0))
        oy = max(0.0, min(ry1, sy1) - max(ry0, sy0))
        best = max(best, (ox * oy) / room_area)
    return best

def flood_rooms(walls, stairs=None):
    stairs = stairs or []
    xs0 = min(w[0] - w[3] / 2 for w in walls); xs1 = max(w[0] + w[3] / 2 for w in walls)
    ys0 = min(w[1] - w[4] / 2 for w in walls); ys1 = max(w[1] + w[4] / 2 for w in walls)
    pad = RES * 2
    xs0 -= pad; ys0 -= pad; xs1 += pad; ys1 += pad
    nx = max(4, int(math.ceil((xs1 - xs0) / RES)))
    ny = max(4, int(math.ceil((ys1 - ys0) / RES)))
    blocked = np.zeros((nx, ny), dtype=bool)
    def ix(x): return min(nx - 1, max(0, int((x - xs0) / RES)))
    def iy(y): return min(ny - 1, max(0, int((y - ys0) / RES)))
    for cx, cy, cz, bx, by_, bz in walls:
        i0, i1 = ix(cx - bx / 2), ix(cx + bx / 2)
        j0, j1 = iy(cy - by_ / 2), iy(cy + by_ / 2)
        blocked[i0:i1 + 1, j0:j1 + 1] = True
    # Morphological close: dilate walls SEAL cells to seal hairline corner/door-jamb gaps so the
    # exterior flood can't leak into a room through a 1–2 cell crack (it still leaves real ~1m
    # doorways open — by design those connect rooms, handled by the area filter / per-room split).
    if SEAL > 0:
        b = blocked.copy()
        for _ in range(SEAL):
            d = b.copy()
            d[1:, :] |= b[:-1, :]; d[:-1, :] |= b[1:, :]
            d[:, 1:] |= b[:, :-1]; d[:, :-1] |= b[:, 1:]
            b = d
        blocked = b
    free = ~blocked
    # exterior flood from border free cells (4-connectivity, iterative stack)
    ext = np.zeros_like(free)
    stack = []
    for i in range(nx):
        for j in (0, ny - 1):
            if free[i, j] and not ext[i, j]: ext[i, j] = True; stack.append((i, j))
    for j in range(ny):
        for i in (0, nx - 1):
            if free[i, j] and not ext[i, j]: ext[i, j] = True; stack.append((i, j))
    while stack:
        i, j = stack.pop()
        for di, dj in ((1,0),(-1,0),(0,1),(0,-1)):
            a, b = i + di, j + dj
            if 0 <= a < nx and 0 <= b < ny and free[a, b] and not ext[a, b]:
                ext[a, b] = True; stack.append((a, b))
    enclosed = free & ~ext
    # connected components on enclosed
    rooms = []
    seen = np.zeros_like(enclosed)
    cell_area = RES * RES
    plan_area = nx * ny * cell_area
    cz = float(np.mean([w[2] for w in walls])); bz = float(np.mean([w[5] for w in walls]))
    for si in range(nx):
        for sj in range(ny):
            if not enclosed[si, sj] or seen[si, sj]: continue
            comp = []; st = [(si, sj)]; seen[si, sj] = True
            mni = mxi = si; mnj = mxj = sj
            while st:
                i, j = st.pop(); comp.append((i, j))
                mni = min(mni, i); mxi = max(mxi, i); mnj = min(mnj, j); mxj = max(mxj, j)
                for di, dj in ((1,0),(-1,0),(0,1),(0,-1)):
                    a, b = i + di, j + dj
                    if 0 <= a < nx and 0 <= b < ny and enclosed[a, b] and not seen[a, b]:
                        seen[a, b] = True; st.append((a, b))
            area = len(comp) * cell_area
            if area < MIN_AREA or area > MAX_AREA_ABS or area > plan_area * MAX_AREA_FRAC: continue
            wx0 = xs0 + mni * RES; wx1 = xs0 + (mxi + 1) * RES
            wy0 = ys0 + mnj * RES; wy1 = ys0 + (mxj + 1) * RES
            # §STAIR-EXCLUDE: a stair/ramp footprint covering this pocket → it's a circulation
            # shaft, not a room. Drop it (the lens was showing staircases as rooms).
            sf = _stair_overlap_frac(wx0, wy0, wx1, wy1, stairs)
            if sf >= STAIR_OVERLAP_REJECT:
                print(f"    skip stair-shaft pocket area={round(area)} stair_overlap={sf:.0%}"); continue
            rooms.append({
                "cx": (wx0 + wx1) / 2, "cy": (wy0 + wy1) / 2, "cz": cz,
                "sx": wx1 - wx0, "sy": wy1 - wy0, "sz": max(bz, 2.0), "area": area})
    return rooms

def main():
    if len(sys.argv) < 2:
        print(__doc__); return
    db = sys.argv[1]; write = "--write" in sys.argv
    con = sqlite3.connect(db); c = con.cursor()
    # storey guid map (for parent_guid)
    st_guid = {}
    try:
        for g, n in c.execute("SELECT guid, name FROM spatial_structure WHERE type='IfcBuildingStorey'").fetchall():
            st_guid[n] = g
    except Exception:
        pass
    by = storey_walls(c)
    stairs_by = storey_stairs(c)
    # §STAIR-EXCLUDE: stair storey is often 'Unknown'/unassigned in the extract, and a stair is a
    # CONTINUOUS vertical shaft anyway — so test every room pocket against the UNION of all stair
    # footprints by XY (not per-storey). A staircase at an XY is circulation on whatever floor it cuts.
    all_stairs = [s for lst in stairs_by.values() for s in lst]
    total = 0; allrooms = []; st_z = {}
    for st in sorted(by):
        ws = by[st]
        if len(ws) < 3:
            print(f"  storey {st!r}: walls={len(ws)} (too few — skip)"); continue
        rooms = flood_rooms(ws, all_stairs)
        total += len(rooms)
        st_z[st] = sum(w[2] for w in ws) / len(ws)  # storey z = mean wall centre-z
        print(f"  storey {st!r}: walls={len(ws)} → rooms={len(rooms)}  areas={[round(r['area']) for r in rooms]}")
        for k, r in enumerate(rooms):
            r["storey"] = st; r["guid"] = f"RM_{st}_{k+1}".replace(" ", "_")
            # §APPROX: '≈' marks the room as compiled/approximate in the lens label.
            # parent_guid → a compiled storey row (created below) so the Room lens groups per floor.
            r["name"] = f"≈ {st} R{k+1}"; r["parent"] = st_guid.get(st) or ("STC_" + st).replace(" ", "_")
            allrooms.append(r)
    print(f"TOTAL compiled rooms = {total}")
    if not write:
        print("(dry run — pass --write to inject)"); return
    # ensure spatial_structure has bbox columns
    cols = [r[1] for r in c.execute("PRAGMA table_info(spatial_structure)").fetchall()] if \
        c.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name='spatial_structure'").fetchall() else None
    if cols is None:
        c.execute("""CREATE TABLE spatial_structure (guid TEXT, type TEXT, name TEXT, parent_guid TEXT,
                     object_type TEXT, predefined_type TEXT, center_x REAL, center_y REAL, center_z REAL,
                     size_x REAL, size_y REAL, size_z REAL)"""); cols = []
    def _addcol(table, col, typ):
        try: c.execute(f"ALTER TABLE {table} ADD COLUMN {col} {typ}")
        except sqlite3.OperationalError: pass  # already exists — fine
    for col in ("center_x","center_y","center_z","size_x","size_y","size_z"):
        _addcol("spatial_structure", col, "REAL")
    for col in ("object_type","predefined_type"):
        _addcol("spatial_structure", col, "TEXT")
    # §APPROX: compiled storey rows (only where the DB has none) so the Room lens can group
    # rooms per floor via parent_guid → IfcBuildingStorey.name. Idempotent on the STC_ prefix.
    c.execute("DELETE FROM spatial_structure WHERE type='IfcBuildingStorey' AND guid LIKE 'STC_%'")
    for st in sorted(st_z):
        if not any(r["storey"] == st for r in allrooms):
            continue
        c.execute("INSERT INTO spatial_structure (guid,type,name,parent_guid,object_type,"
                  "predefined_type,center_z) VALUES (?,?,?,?,?,?,?)",
                  (("STC_" + st).replace(" ", "_"), "IfcBuildingStorey", st, None, "COMPILED", None, st_z[st]))
    # remove any prior compiled rooms (idempotent)
    c.execute("DELETE FROM spatial_structure WHERE type='IfcSpace' AND guid LIKE 'RM_%'")
    for r in allrooms:
        c.execute("INSERT INTO spatial_structure (guid,type,name,parent_guid,object_type,predefined_type,"
                  "center_x,center_y,center_z,size_x,size_y,size_z) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                  (r["guid"], "IfcSpace", r["name"], r["parent"], "COMPILED", "INTERNAL",
                   r["cx"], r["cy"], r["cz"], r["sx"], r["sy"], r["sz"]))
    # rel_contained_in_space: elements whose XY centre falls inside a room (compiled)
    if not c.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name='rel_contained_in_space'").fetchall():
        c.execute("CREATE TABLE rel_contained_in_space (space_guid TEXT, element_guid TEXT)")
    c.execute("DELETE FROM rel_contained_in_space WHERE space_guid LIKE 'RM_%'")
    els = c.execute("SELECT m.guid, m.storey, t.center_x, t.center_y FROM elements_meta m "
                    "JOIN element_transforms t ON t.guid=m.guid WHERE t.center_x IS NOT NULL").fetchall()
    rel = 0
    byst = {}
    for r in allrooms: byst.setdefault(r["storey"], []).append(r)
    for g, st, ex, ey in els:
        for r in byst.get(st, []):
            if abs(ex - r["cx"]) <= r["sx"]/2 and abs(ey - r["cy"]) <= r["sy"]/2:
                c.execute("INSERT INTO rel_contained_in_space (space_guid, element_guid) VALUES (?,?)", (r["guid"], g)); rel += 1
                break
    con.commit()
    print(f"WROTE {len(allrooms)} IfcSpace rows + {rel} rel_contained_in_space rows")

if __name__ == "__main__":
    main()
