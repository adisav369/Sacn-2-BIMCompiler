#!/usr/bin/env python3
"""
Generate SQL migration: migration_G8_DX_restore_grid_rooms.sql

Purpose:
  - Update ad_room_boundary bounds for all Ifc2x3_Duplex ROOM_Level_* rooms
    to IFC-sourced coordinates from elements_rtree.
  - Fix ad_wall_face storey naming: delete stale 'Level 1'/'Level 2' rows,
    insert correct 'Ground'/'Upper' rows.
  - Does NOT touch c_orderline.is_active.
  - Detects actual DB schema (handles presence/absence of extracted_from and
    coordinate_frame columns).
  - Idempotent: INSERT OR IGNORE + UPDATE bounds.

Inputs:
  - Library DB    : /home/red1/bim-compiler/library/BOM.db
  - Extracted DB  : /home/red1/bim-compiler/DAGCompiler/lib/input/Ifc2x3_Duplex_extracted.db

Output:
  - /home/red1/bim-compiler/migration/migration_G8_DX_restore_grid_rooms.sql
"""

import sqlite3
import re

LIBRARY_DB   = "/home/red1/bim-compiler/library/BOM.db"
EXTRACTED_DB = "/home/red1/bim-compiler/DAGCompiler/lib/input/Ifc2x3_Duplex_extracted.db"
OUTPUT_SQL   = "/home/red1/bim-compiler/migration/migration_G8_DX_restore_grid_rooms.sql"

BUILDING_TYPE = "Ifc2x3_Duplex"
BUILDING_ID   = 2

# Storey name map: host_ref Level prefix → canonical storey name
LEVEL_TO_STOREY = {
    "Level_1": "Ground",
    "Level_2": "Upper",
}
Z_OFFSET_MAP = {
    "Ground": 0.0,
    "Upper":  3100.0,
}

FALLBACK_SIZE = 1000.0   # mm, for rooms with no matched rtree elements

BOM_ROOM_TYPE_MAP = [
    ("BOM_BED_SET",              "BEDROOM"),
    ("BOM_WARDROBE_SET",         "BEDROOM"),
    ("BOM_KITCHEN_CABINET_SET",  "KITCHEN"),
    ("BOM_DINING_SET",           "LIVING"),
    ("BOM_LIVING_SET",           "LIVING"),
    ("BOM_SANITARY",             "BATHROOM"),
    ("BOM_TOILET",               "BATHROOM"),
    ("BOM_BATHROOM",             "BATHROOM"),
]

ORDINAL_RE = re.compile(r'_(\d+)$')

STALE_STOREYS = ("Level 1", "Level 2")


def get_cols(con, table):
    return {r[1] for r in con.execute(f"PRAGMA table_info({table})").fetchall()}


def extract_ordinal(element_ref):
    if element_ref.startswith("BOM_"):
        return None
    m = ORDINAL_RE.search(element_ref)
    return int(m.group(1)) if m else None


def classify_room_type(furn_refs):
    for ref in furn_refs:
        for pattern, rtype in BOM_ROOM_TYPE_MAP:
            if pattern in ref:
                return rtype
    return "GENERIC"


def level_prefix(host_ref):
    """Extract 'Level_1' or 'Level_2' from 'ROOM_Level_1_5'."""
    m = re.match(r'ROOM_(Level_\d+)_', host_ref)
    return m.group(1) if m else None


def main():
    lib_con = sqlite3.connect(LIBRARY_DB)
    lib_cur = lib_con.cursor()
    ext_con = sqlite3.connect(EXTRACTED_DB)
    ext_cur = ext_con.cursor()

    # ── Schema discovery ──────────────────────────────────────────────────────
    rb_cols = get_cols(lib_con, "ad_room_boundary")
    has_extracted_from   = "extracted_from"   in rb_cols
    has_coordinate_frame = "coordinate_frame" in rb_cols
    print(f"Schema check — extracted_from: {has_extracted_from}, "
          f"coordinate_frame: {has_coordinate_frame}")

    # ── 1. Collect all ROOM_Level_* host_refs (any is_active) ────────────────
    lib_cur.execute("""
        SELECT DISTINCT host_ref, storey
        FROM c_orderline
        WHERE building_type = ?
          AND host_ref LIKE 'ROOM_Level_%'
        ORDER BY storey, host_ref
    """, (BUILDING_TYPE,))
    rooms_raw = lib_cur.fetchall()
    print(f"Found {len(rooms_raw)} unique ROOM_Level_* host_refs")

    # ── 2. Collect element_refs ───────────────────────────────────────────────
    lib_cur.execute("""
        SELECT host_ref, element_ref, discipline
        FROM c_orderline
        WHERE building_type = ?
          AND host_ref LIKE 'ROOM_Level_%'
        ORDER BY host_ref, element_ref
    """, (BUILDING_TYPE,))
    room_info = {h: {"ordinals": [], "furn_refs": [], "storey": s}
                 for h, s in rooms_raw}
    for host_ref, element_ref, discipline in lib_cur.fetchall():
        if host_ref not in room_info:
            continue
        ordinal = extract_ordinal(element_ref)
        if ordinal is not None:
            room_info[host_ref]["ordinals"].append(ordinal)
        if discipline == "FURN":
            room_info[host_ref]["furn_refs"].append(element_ref)

    # ── 3. Look up bounding boxes ─────────────────────────────────────────────
    stats = {"with_bounds": 0, "fallback": 0}
    room_data = {}

    for host_ref, storey_from_rule in rooms_raw:
        info     = room_info[host_ref]
        ordinals = info["ordinals"]

        # Storey in c_orderline is already canonical (Ground/Upper)
        st       = storey_from_rule
        z_offset = Z_OFFSET_MAP.get(st, 0.0)
        rt       = classify_room_type(info["furn_refs"])

        min_x = min_y =  float("inf")
        max_x = max_y = -float("inf")
        matched = 0

        if ordinals:
            ph = ",".join("?" * len(ordinals))
            ext_cur.execute(
                f"SELECT minX*1000, maxX*1000, minY*1000, maxY*1000 "
                f"FROM elements_rtree WHERE id IN ({ph})",
                ordinals
            )
            for mnx, mxx, mny, mxy in ext_cur.fetchall():
                if mnx < min_x: min_x = mnx
                if mxx > max_x: max_x = mxx
                if mny < min_y: min_y = mny
                if mxy > max_y: max_y = mxy
                matched += 1

        if matched > 0 and min_x < max_x and min_y < max_y:
            stats["with_bounds"] += 1
            min_x_mm = round(min_x, 1)
            max_x_mm = round(max_x, 1)
            min_y_mm = round(min_y, 1)
            max_y_mm = round(max_y, 1)
            source = "IFC_RTREE"
        else:
            stats["fallback"] += 1
            min_x_mm = 0.0
            max_x_mm = FALLBACK_SIZE
            min_y_mm = 0.0
            max_y_mm = FALLBACK_SIZE
            source = "FALLBACK"

        room_data[host_ref] = {
            "storey":     st,
            "room_type":  rt,
            "z_offset":   z_offset,
            "min_x_mm":   min_x_mm,
            "max_x_mm":   max_x_mm,
            "min_y_mm":   min_y_mm,
            "max_y_mm":   max_y_mm,
            "source":     source,
            "n_elements": len(ordinals),
            "n_matched":  matched,
        }

    print(f"  With IFC bounds  : {stats['with_bounds']}")
    print(f"  Fallback (0→1000): {stats['fallback']}")

    # ── 4. Snapshot existing DB state ─────────────────────────────────────────
    lib_cur.execute(
        "SELECT room_name FROM ad_room_boundary WHERE building_type = ?",
        (BUILDING_TYPE,)
    )
    existing_rb = {row[0] for row in lib_cur.fetchall()}

    # Count stale wall_face rows (old storey names)
    stale_wf_placeholders = ",".join("?" * len(STALE_STOREYS))
    lib_cur.execute(
        f"SELECT COUNT(*) FROM ad_wall_face"
        f" WHERE building_type = ? AND room_name LIKE 'ROOM_Level_%'"
        f" AND storey IN ({stale_wf_placeholders})",
        (BUILDING_TYPE,) + STALE_STOREYS
    )
    n_stale_wf = lib_cur.fetchone()[0]

    lib_con.close()
    ext_con.close()

    n_new_rb  = sum(1 for h in room_data if h not in existing_rb)
    n_upd_rb  = sum(1 for h in room_data if h in existing_rb)
    print(f"\nad_room_boundary: {n_new_rb} INSERT, {n_upd_rb} UPDATE (bounds refresh)")
    print(f"ad_wall_face    : {n_stale_wf} stale rows to DELETE, "
          f"{len(room_data)*4} rows to INSERT OR IGNORE")

    # ── 5. Build INSERT column list ───────────────────────────────────────────
    rb_ins_cols = [
        "building_type", "storey", "room_name", "room_type",
        "grid_min_x", "grid_max_x", "grid_min_y", "grid_max_y",
        "z_offset_mm", "is_active",
        "min_x_mm", "max_x_mm", "min_y_mm", "max_y_mm",
        "building_id",
    ]
    rb_upd_extra = []
    if has_extracted_from:
        rb_ins_cols.append("extracted_from")
        rb_upd_extra.append("extracted_from='IFC_RTREE'")
    if has_coordinate_frame:
        rb_ins_cols.append("coordinate_frame")

    # ── 6. Generate SQL ───────────────────────────────────────────────────────
    lines = [
        "-- ============================================================",
        "-- migration_G8_DX_restore_grid_rooms.sql",
        "-- Restore ad_room_boundary bounds + fix ad_wall_face storey names",
        "-- for Ifc2x3_Duplex ROOM_Level_* grid rooms.",
        "--",
        "-- Bounds sourced from elements_rtree (IFC meters → mm).",
        "-- Generated: 2026-02-23",
        f"-- Rooms : {len(room_data)}  "
        f"(IFC bounds: {stats['with_bounds']}, fallback: {stats['fallback']})",
        "--",
        "-- Does NOT modify c_orderline.is_active.",
        "-- Safe to re-apply (INSERT OR IGNORE + idempotent UPDATEs).",
        "-- ============================================================",
        "",
        "BEGIN TRANSACTION;",
        "",
    ]

    # Section 0: Delete stale wall_face rows (old storey names)
    if n_stale_wf > 0:
        lines += [
            "-- ── Section 0: Remove stale wall_face rows (old 'Level 1'/'Level 2' storey) ──",
            f"-- {n_stale_wf} rows will be deleted.",
            "DELETE FROM ad_wall_face",
            f"  WHERE building_type = '{BUILDING_TYPE}'",
            "    AND room_name LIKE 'ROOM_Level_%'",
            "    AND storey IN ('Level 1', 'Level 2');",
            "",
        ]

    # Section A: ad_room_boundary — INSERT OR IGNORE + UPDATE bounds
    lines += [
        "-- ── Section A: ad_room_boundary ─────────────────────────────",
        "",
    ]
    for host_ref in sorted(room_data.keys()):
        d   = room_data[host_ref]
        rn  = host_ref
        st  = d["storey"]
        rt  = d["room_type"]
        z   = d["z_offset"]
        mnx = d["min_x_mm"]
        mxx = d["max_x_mm"]
        mny = d["min_y_mm"]
        mxy = d["max_y_mm"]

        val_parts = [
            f"'{BUILDING_TYPE}'", f"'{st}'", f"'{rn}'", f"'{rt}'",
            "''", "''", "''", "''",
            str(z), "1",
            str(mnx), str(mxx), str(mny), str(mxy),
            str(BUILDING_ID),
        ]
        if has_extracted_from:
            val_parts.append("'IFC_RTREE'")
        if has_coordinate_frame:
            val_parts.append("'LOCAL_MM'")

        cols_str = ", ".join(rb_ins_cols)
        vals_str = ", ".join(val_parts)

        upd_set = [
            f"min_x_mm={mnx}", f"max_x_mm={mxx}",
            f"min_y_mm={mny}", f"max_y_mm={mxy}",
            f"room_type='{rt}'", f"z_offset_mm={z}",
        ] + rb_upd_extra

        lines.append(
            f"-- {rn} | {st} | {rt} | {d['source']} | "
            f"elements={d['n_elements']} matched={d['n_matched']}"
        )
        lines.append(
            f"INSERT OR IGNORE INTO ad_room_boundary ({cols_str})"
            f" VALUES ({vals_str});"
        )
        lines.append(
            f"UPDATE ad_room_boundary SET {', '.join(upd_set)}"
            f" WHERE building_type='{BUILDING_TYPE}'"
            f" AND storey='{st}' AND room_name='{rn}';"
        )
        lines.append("")

    # Section B: ad_wall_face (4 faces per room, correct storey names)
    FACES = ["NORTH", "SOUTH", "EAST", "WEST"]
    lines += [
        "",
        "-- ── Section B: ad_wall_face ──────────────────────────────────",
        f"-- Inserts {len(room_data)*4} rows with correct storey='Ground'/'Upper'.",
        "",
    ]
    for host_ref in sorted(room_data.keys()):
        d  = room_data[host_ref]
        rn = host_ref
        st = d["storey"]
        for face in FACES:
            lines.append(
                f"INSERT OR IGNORE INTO ad_wall_face"
                f" (building_type, storey, room_name, face,"
                f" wall_type_id, is_exterior, is_active, building_id)"
                f" VALUES"
                f" ('{BUILDING_TYPE}', '{st}', '{rn}', '{face}',"
                f" 'INTERIOR_PARTITION_92', 0, 1, {BUILDING_ID});"
            )
        lines.append("")

    lines += [
        "",
        "COMMIT;",
        "",
        "-- Verification queries (run after applying):",
        f"-- SELECT COUNT(*) FROM ad_room_boundary",
        f"--   WHERE building_type='{BUILDING_TYPE}' AND room_name LIKE 'ROOM_Level_%';",
        f"-- Expected: {len(room_data)}",
        "",
        f"-- SELECT COUNT(*) FROM ad_wall_face",
        f"--   WHERE building_type='{BUILDING_TYPE}' AND room_name LIKE 'ROOM_Level_%'",
        f"--   AND storey IN ('Ground','Upper');",
        f"-- Expected: {len(room_data)*4}",
        "",
        f"-- SELECT COUNT(*) FROM c_orderline",
        f"--   WHERE building_type='{BUILDING_TYPE}' AND is_active=1;",
        f"-- Expected: unchanged (this migration does not modify element_rules).",
        "",
        "-- ── End of migration ─────────────────────────────────────────",
    ]

    sql_text = "\n".join(lines)
    with open(OUTPUT_SQL, "w") as f:
        f.write(sql_text)

    print(f"\nSQL written to: {OUTPUT_SQL}")
    print(f"Total lines: {len(lines)}")

    # Summary table
    print(f"\n{'host_ref':<25} {'storey':<8} {'type':<10} {'src':<10} "
          f"{'X (mm)':<24} {'Y (mm)'}")
    print("-" * 100)
    for host_ref in sorted(room_data.keys()):
        d  = room_data[host_ref]
        xr = f"{d['min_x_mm']:.0f}..{d['max_x_mm']:.0f}"
        yr = f"{d['min_y_mm']:.0f}..{d['max_y_mm']:.0f}"
        flag = " [FALLBACK]" if d["source"] == "FALLBACK" else ""
        print(f"{host_ref:<25} {d['storey']:<8} {d['room_type']:<10} "
              f"{d['source']:<10} {xr:<24} {yr}{flag}")


if __name__ == "__main__":
    main()
