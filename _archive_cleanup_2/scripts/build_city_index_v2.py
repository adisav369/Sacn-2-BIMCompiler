#!/usr/bin/env python3
"""
build_city_index_v2.py — Regenerate the city layout into a NEW, standalone file.

⚠ DO NOT REMOVE — Scope: produce deploy/buildings/city_index_v2.db with a clash-free,
footprint-aware layout. NON-DESTRUCTIVE: never touches the live city_index.db.
Read the output log after every run before drawing conclusions.

PRINCIPLE (non-invent): building footprints are the REAL IFC bounding boxes already
aggregated in the existing city_index.db (sourced from elements_rtree / extracted geometry).
We ONLY change each building's placement OFFSET — geometry extents are carried through
verbatim. No synthesized/calculated cubes.

What it does:
  1. Read per-archetype, per-discipline AABBs (min/max x,y,z) + element counts from the
     existing city_index.db (representative instance per archetype). Translate to
     building-local (subtract the building's min corner) → relative IFC AABBs.
  2. Drop DEPRECATED archetypes (no deployable geometry on OCI):
     HospitalAuckland, Ifc2x3_AC11Institute, Ifc4_FZKHaus.
  3. Lay buildings on a footprint-aware grid: pack left→right to a target row width;
     each row's Y pitch = max building depth in that row + GAP (so deep buildings like
     LTU @ 459m never bleed into the row behind → no clash).
  4. Repeat the roster (cycling) until total elements >= TARGET (~1M). Big buildings
     (LTU/Hospital/Terminal/Clinic) naturally recur — "another garage/hospital/clinic".
  5. Emit building_summary (world AABBs = relative + offset, center = midpoint) +
     building_archetype (building -> archetype, total_elements). Schema matches live file.

Run:  python3 scripts/build_city_index_v2.py
"""
import sqlite3
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SRC  = REPO / "deploy" / "buildings" / "city_index.db"
OUT  = REPO / "deploy" / "buildings" / "city_index_v2.db"

DEPRECATED = {"HospitalAuckland", "Ifc2x3_AC11Institute", "Ifc4_FZKHaus"}  # no OCI geometry

TARGET_ELEMENTS = 1_000_000
ROW_WIDTH       = 1800.0   # wrap to next row past this packed X width (metres)
GAP             = 30.0     # gap between buildings (X) and between rows (Y)


def log(msg):
    print(f"[CITY_V2] {msg}")


def load_archetypes(conn):
    """Per archetype: {disc -> (count, rel_min(x,y,z), rel_max(x,y,z))}, size, total.
    Relative = world AABB minus the building's min corner (origin). Real IFC extents."""
    rows = conn.execute("SELECT archetype, MIN(building) FROM building_archetype GROUP BY archetype").fetchall()
    archs = {}
    for arch, rep in rows:
        if arch in DEPRECATED:
            continue
        disc_rows = conn.execute(
            "SELECT discipline, element_count, min_x,min_y,min_z, max_x,max_y,max_z "
            "FROM building_summary WHERE building=?", (rep,)).fetchall()
        if not disc_rows:
            continue
        ox = min(r[2] for r in disc_rows); oy = min(r[3] for r in disc_rows); oz = min(r[4] for r in disc_rows)
        discs = []
        for d, cnt, mnx, mny, mnz, mxx, mxy, mxz in disc_rows:
            discs.append((d, cnt, mnx-ox, mny-oy, mnz-oz, mxx-ox, mxy-oy, mxz-oz))
        w = max(r[5] for r in discs); depth = max(r[6] for r in discs)
        total = sum(r[1] for r in discs)
        archs[arch] = {"discs": discs, "w": w, "depth": depth, "total": total}
    return archs


def main():
    if not SRC.exists():
        log(f"FATAL: source {SRC} not found"); return
    src = sqlite3.connect(str(SRC))
    archs = load_archetypes(src)
    src.close()
    log(f"Loaded {len(archs)} archetypes (dropped deprecated: {sorted(DEPRECATED)})")

    # Roster order: big landmarks first (front), then descending — stable, varied.
    order = sorted(archs.keys(), key=lambda a: -archs[a]["total"])

    if OUT.exists():
        OUT.unlink()
    out = sqlite3.connect(str(OUT))
    # Schema identical to the live city_index.db (parity — same constraints/keys).
    out.executescript("""
        CREATE TABLE building_summary (
          building TEXT NOT NULL,
          discipline TEXT NOT NULL,
          element_count INTEGER NOT NULL,
          center_x REAL, center_y REAL, center_z REAL,
          min_x REAL, min_y REAL, min_z REAL,
          max_x REAL, max_y REAL, max_z REAL,
          PRIMARY KEY (building, discipline)
        );
        CREATE TABLE building_archetype (
          building TEXT PRIMARY KEY, archetype TEXT NOT NULL, total_elements INTEGER);
    """)

    placed = 0
    elems = 0
    tile = 0
    cursor_x = 0.0
    row_y = 0.0
    row_depth = 0.0
    summary_rows = []
    arche_rows = []

    while elems < TARGET_ELEMENTS:
        for arch in order:
            if elems >= TARGET_ELEMENTS:
                break
            a = archs[arch]
            # wrap row if this building would exceed the target row width
            if cursor_x > 0 and cursor_x + a["w"] > ROW_WIDTH:
                row_y += row_depth + GAP          # Y pitch = deepest building in finished row
                cursor_x = 0.0
                row_depth = 0.0
            ox, oy = cursor_x, row_y
            bname = f"T{tile}_{arch}"
            for d, cnt, rmnx, rmny, rmnz, rmxx, rmxy, rmxz in a["discs"]:
                mnx, mny = rmnx + ox, rmny + oy
                mxx, mxy = rmxx + ox, rmxy + oy
                summary_rows.append((bname, d, cnt,
                    (mnx+mxx)/2, (mny+mxy)/2, (rmnz+rmxz)/2,
                    mnx, mny, rmnz, mxx, mxy, rmxz))
            arche_rows.append((bname, arch, a["total"]))
            elems += a["total"]; placed += 1; tile += 1
            cursor_x += a["w"] + GAP
            row_depth = max(row_depth, a["depth"])

    out.executemany("INSERT INTO building_summary VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", summary_rows)
    out.executemany("INSERT INTO building_archetype VALUES (?,?,?)", arche_rows)
    out.commit()

    # ── Verify: clash check (AABB overlap in XY between any two buildings) ──
    bb = {}
    for r in arche_rows:
        bname = r[0]
        rows = [s for s in summary_rows if s[0] == bname]
        bb[bname] = (min(s[6] for s in rows), min(s[7] for s in rows),
                     max(s[9] for s in rows), max(s[10] for s in rows))
    names = list(bb.keys())
    clashes = 0
    for i in range(len(names)):
        a1 = bb[names[i]]
        for j in range(i+1, len(names)):
            a2 = bb[names[j]]
            if a1[0] < a2[2] and a2[0] < a1[2] and a1[1] < a2[3] and a2[1] < a1[3]:
                clashes += 1
    out.close()
    log(f"DONE buildings={placed} elements={elems:,} archetypes={len(archs)} XY_clashes={clashes}")
    log(f"Output: {OUT} ({OUT.stat().st_size/1024:.0f} KB)")
    if clashes:
        log(f"WARNING: {clashes} XY overlaps remain — layout needs wider GAP/rows")


if __name__ == "__main__":
    main()
