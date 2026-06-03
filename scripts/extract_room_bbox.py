#!/usr/bin/env python3
# ⚠ DO NOT REMOVE
# Scope: tessellate each IfcSpace in the Duplex IFC, compute world-space bbox
#   center+size, calibrate to the extraction pipeline's coordinate frame (offset
#   derived from known wall GlobalIds present in both the IFC and the DB), then
#   write center_x/y/z + size_x/y/z columns into spatial_structure of the NEW DB.
# Non-invent: every number traces to ifcopenshell tessellation; offset is measured,
#   not guessed. Read the log after the run.
import sys, sqlite3
import numpy as np
import ifcopenshell
import ifcopenshell.geom

IFC = "reference/residential/Ifc2x3_Duplex_Federated.ifc"
DB  = "deploy/dev/buildings/Duplex_rooms_vol.db"

# Calibration elements: GlobalId -> DB center (x,y,z) in the pipeline frame.
CALIB = {
    "2O2Fr$t4X7Zf8NOew3FNtn": (4.53812947190469, -0.208499999999996, 1.46027778221511),
    "2O2Fr$t4X7Zf8NOew3FNqI": (8.59149999999999, -9.91752774797548, 1.35458333219101),
    "2O2Fr$t4X7Zf8NOew3FNr2": (4.19815280388459, -17.5915, 1.46027778221511),
    "2O2Fr$t4X7Zf8NOew3FNhv": (0.208500000000002, -7.95197223027547, 1.35458333219101),
    "2O2Fr$t4X7Zf8NOew3FKau": (4.39999999999999, -8.9, 4.57999998514508),
}

def log(m): print(m); sys.stdout.flush()

settings = ifcopenshell.geom.settings()
settings.set(settings.USE_WORLD_COORDS, True)

log("§ROOM_BBOX loading IFC " + IFC)
model = ifcopenshell.open(IFC)

def world_bbox(prod):
    """Return (center np3, size np3) in IFC world coords, or None if no geometry."""
    try:
        shape = ifcopenshell.geom.create_shape(settings, prod)
    except Exception as e:
        return None
    verts = shape.geometry.verts
    if not verts:
        return None
    arr = np.array(verts, dtype=float).reshape(-1, 3)
    mn = arr.min(axis=0)
    mx = arr.max(axis=0)
    return (mn + mx) / 2.0, (mx - mn)

# ── Calibrate offset: ifc_world_center - db_center, averaged over calib elements ──
offsets = []
for gid, dbc in CALIB.items():
    prods = model.by_guid(gid)
    if prods is None:
        log("§ROOM_BBOX_CALIB miss gid=" + gid)
        continue
    bb = world_bbox(prods)
    if bb is None:
        log("§ROOM_BBOX_CALIB no-geom gid=" + gid)
        continue
    c, s = bb
    off = np.array(c) - np.array(dbc)
    offsets.append(off)
    log("§ROOM_BBOX_CALIB gid=%s ifc_c=(%.3f,%.3f,%.3f) db_c=(%.3f,%.3f,%.3f) off=(%.3f,%.3f,%.3f)"
        % (gid, c[0], c[1], c[2], dbc[0], dbc[1], dbc[2], off[0], off[1], off[2]))

if not offsets:
    log("§ROOM_BBOX_FATAL no calibration elements resolved — aborting")
    sys.exit(1)

# Median offset — robust to a single federated-geometry outlier (one wall whose
# Arch vs MEP centroid differs). The pipeline frame is a rigid translation of IFC
# world coords; the per-axis median of measured offsets recovers it.
offset = np.median(np.array(offsets), axis=0)
spread = np.max(offsets, axis=0) - np.min(offsets, axis=0)
log("§ROOM_BBOX offset=(%.4f,%.4f,%.4f) spread=(%.4f,%.4f,%.4f) n=%d (median)"
    % (offset[0], offset[1], offset[2], spread[0], spread[1], spread[2], len(offsets)))
if np.max(np.abs(spread)) > 0.05:
    log("§ROOM_BBOX_NOTE calibration spread > 5cm on some axis — using median to reject outliers")

# ── DB: add columns if absent ──
conn = sqlite3.connect(DB)
cur = conn.cursor()
cols = [r[1] for r in cur.execute("PRAGMA table_info(spatial_structure)").fetchall()]
for c in ("center_x", "center_y", "center_z", "size_x", "size_y", "size_z"):
    if c not in cols:
        cur.execute("ALTER TABLE spatial_structure ADD COLUMN %s REAL" % c)
        log("§ROOM_BBOX add column " + c)
conn.commit()

# ── Tessellate each IfcSpace, write calibrated center+size ──
spaces = cur.execute(
    "SELECT guid, name FROM spatial_structure WHERE type='IfcSpace'").fetchall()
log("§ROOM_BBOX spaces_in_db=%d" % len(spaces))

written = 0
skipped = []
for gid, name in spaces:
    prod = model.by_guid(gid)
    if prod is None:
        skipped.append((gid, name, "not in IFC"))
        continue
    bb = world_bbox(prod)
    if bb is None:
        skipped.append((gid, name, "no geometry"))
        continue
    c, s = bb
    cc = np.array(c) - offset  # into pipeline frame
    cur.execute(
        "UPDATE spatial_structure SET center_x=?, center_y=?, center_z=?,"
        " size_x=?, size_y=?, size_z=? WHERE guid=?",
        (float(cc[0]), float(cc[1]), float(cc[2]),
         float(s[0]), float(s[1]), float(s[2]), gid))
    written += 1
    log("§ROOM_BBOX room name=%s center=(%.2f,%.2f,%.2f) size=(%.2f,%.2f,%.2f)m"
        % (name, cc[0], cc[1], cc[2], s[0], s[1], s[2]))

conn.commit()
conn.close()

for gid, name, why in skipped:
    log("§ROOM_BBOX_SKIP name=%s gid=%s why=%s" % (name, gid, why))
log("§ROOM_BBOX DONE written=%d skipped=%d total=%d" % (written, len(skipped), len(spaces)))
