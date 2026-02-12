#!/usr/bin/env python3
"""
Rosetta Dictionary — maps IFC reference element names to compiler categories.

Called by populate_sample_house_db.py and populate_duplex_db.py during extraction
to populate element_dictionary table in reference DBs.

The dictionary enables targeted 1:1 element matching in spatial_checker.py
instead of blind bounding-box bucket comparison.
"""

import re
import sqlite3
from collections import defaultdict


# ── IFC class → compiler category ──────────────────────────────────────────
CATEGORY_MAP = {
    "IfcWall":               "WALL",
    "IfcWallStandardCase":   "WALL",
    "IfcPlate":              "PANEL",      # glazing panels, curtain wall infill
    "IfcDoor":               "DOOR",
    "IfcWindow":             "WINDOW",
    "IfcSlab":               "SLAB",
    "IfcRoof":               "ROOF",
    "IfcColumn":             "COLUMN",
    "IfcBeam":               "BEAM",
    "IfcMember":             "MEMBER",
    "IfcStairFlight":        "STAIR",
    "IfcRailing":            "RAILING",
    "IfcFurnishingElement":  "FURNITURE",
    "IfcFurniture":          "FURNITURE",
    "IfcFlowTerminal":       "TERMINAL",
    "IfcFlowSegment":        "PIPE",
    "IfcFlowFitting":        "FITTING",
}


def create_dictionary_schema(conn):
    """Create element_dictionary table in reference DB."""
    conn.execute("""
        CREATE TABLE IF NOT EXISTS element_dictionary (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            ref_ifc_class TEXT NOT NULL,
            ref_type_name TEXT,
            compiler_category TEXT NOT NULL,
            compiler_subtype TEXT,
            width_mm INTEGER,
            height_mm INTEGER,
            depth_mm INTEGER,
            instance_count INTEGER DEFAULT 1,
            notes TEXT
        )
    """)
    conn.execute("DELETE FROM element_dictionary")  # rebuild each time
    conn.commit()


# ── Dimension parsing from IFC type names ──────────────────────────────────

# Pattern: "1810x2110mm" or "1810 x 2110mm"
_DIM_WxH_MM = re.compile(r'(\d{3,5})\s*x\s*(\d{3,5})\s*mm', re.IGNORECASE)

# Pattern: "0864 x 2032mm" (Duplex door format with leading zero)
_DIM_DOOR = re.compile(r'0?(\d{3,4})\s*x\s*(\d{3,4})\s*mm', re.IGNORECASE)

# Pattern: "1525x2007x355mm" (furniture with 3 dims)
_DIM_3D = re.compile(r'(\d{3,5})\s*x\s*(\d{3,5})\s*x\s*(\d{3,5})\s*mm', re.IGNORECASE)

# Pattern: "1000mm" (single dimension, e.g. cabinet width)
_DIM_SINGLE = re.compile(r'(\d{3,5})\s*mm(?:\s|$|[^x])', re.IGNORECASE)

# Pattern: "650 mmx485 mm" or "650 mm x 485 mm" (terminal fixtures)
_DIM_MM_X_MM = re.compile(r'(\d{3,5})\s*mm\s*x\s*(\d{3,5})\s*mm', re.IGNORECASE)

# Pattern: "W310X60" (steel beam designation)
_BEAM_SIZE = re.compile(r'W(\d{3,4})X(\d{2,3})')


def parse_dimensions(name, element_type):
    """Parse width_mm, height_mm, depth_mm from element name/type strings."""
    text = f"{name or ''} {element_type or ''}"

    # Try 3D first (furniture)
    m = _DIM_3D.search(text)
    if m:
        return int(m.group(1)), int(m.group(2)), int(m.group(3))

    # Try mm x mm (terminal fixtures)
    m = _DIM_MM_X_MM.search(text)
    if m:
        return int(m.group(1)), int(m.group(2)), None

    # Try WxH mm (doors, windows)
    m = _DIM_WxH_MM.search(text)
    if m:
        return int(m.group(1)), int(m.group(2)), None

    # Try door format with leading zero
    m = _DIM_DOOR.search(text)
    if m:
        return int(m.group(1)), int(m.group(2)), None

    # Steel beam
    m = _BEAM_SIZE.search(text)
    if m:
        return int(m.group(1)), None, None

    # Single dimension
    m = _DIM_SINGLE.search(text)
    if m:
        return int(m.group(1)), None, None

    return None, None, None


# ── Subtype inference ──────────────────────────────────────────────────────

def infer_subtype(ifc_class, name, element_type, width_mm, height_mm):
    """Infer compiler subtype from IFC name and parsed dimensions."""
    text = f"{name or ''} {element_type or ''}".lower()

    if ifc_class == "IfcDoor":
        style = "SGL"
        if "dbl" in text or "double" in text:
            style = "DBL"
        if "glass" in text:
            style = "GLASS"
        location = "INT"
        if "ext" in text:
            location = "EXT"
        if width_mm and height_mm:
            return f"D_{location}_{style}_{width_mm}x{height_mm}"
        return f"D_{location}_{style}"

    if ifc_class == "IfcWindow":
        style = "FIXED"
        if "casement" in text:
            style = "CASEMENT"
        if "skylight" in text:
            style = "SKYLIGHT"
        if width_mm and height_mm:
            return f"W_{style}_{width_mm}x{height_mm}"
        return f"W_{style}"

    if ifc_class in ("IfcWall", "IfcWallStandardCase"):
        if "ext" in text:
            # Try to extract construction system from type name
            if "bwk" in text or "brick" in text:
                return "WALL_EXT_BRICK"
            if "cmu" in text or "block" in text:
                return "WALL_EXT_CMU"
            return "WALL_EXT"
        if "partn" in text or "partition" in text:
            return "WALL_PARTITION"
        if "party" in text:
            return "WALL_PARTY"
        if "fdn" in text or "found" in text:
            return "WALL_FOUNDATION"
        if "furring" in text:
            return "WALL_FURRING"
        return "WALL_GENERIC"

    if ifc_class == "IfcSlab":
        if "roof" in text:
            return "SLAB_ROOF"
        if "grnd" in text or "grade" in text or "ground" in text:
            return "SLAB_GROUND"
        if "finish" in text and "ceramic" in text:
            return "SLAB_FINISH_CERAMIC"
        if "finish" in text and "wood" in text:
            return "SLAB_FINISH_WOOD"
        if "joist" in text or "wood joist" in text:
            return "SLAB_WOOD_JOIST"
        if "simple" in text:
            return "SLAB_FLOOR"
        return "SLAB_GENERIC"

    if ifc_class == "IfcRoof":
        if "flat" in text:
            return "ROOF_FLAT"
        return "ROOF_GENERIC"

    if ifc_class in ("IfcFurnishingElement", "IfcFurniture"):
        if "bed" in text:
            if "king" in text:
                return "BED_KING"
            if "queen" in text:
                return "BED_QUEEN"
            return "BED"
        if "sofa" in text or "couch" in text:
            return "SOFA"
        if "chair" in text:
            if "dining" in text:
                return "DINING_CHAIR"
            if "viper" in text or "arm" in text:
                return "ARMCHAIR"
            return "CHAIR"
        if "desk" in text:
            return "DESK"
        if "piano" in text:
            return "PIANO"
        if "table" in text:
            if "coffee" in text:
                return "COFFEE_TABLE"
            if "dining" in text:
                return "DINING_TABLE"
            return "TABLE"
        if "cabinet" in text:
            if "base" in text:
                return "BASE_CABINET"
            if "tall" in text:
                return "TALL_CABINET"
            if "upper" in text:
                return "UPPER_CABINET"
            return "CABINET"
        if "counter" in text:
            if "sink" in text:
                return "COUNTER_SINK"
            return "COUNTER"
        if "vanity" in text:
            return "VANITY"
        return "FURNITURE_OTHER"

    if ifc_class == "IfcFlowTerminal":
        if "light" in text or "pendant" in text or "sconce" in text:
            return "LIGHT"
        if "receptacle" in text or "outlet" in text:
            return "OUTLET"
        if "switch" in text:
            return "SWITCH"
        if "lavatory" in text or "sink" in text:
            return "LAVATORY"
        if "closet" in text or "toilet" in text or "wc" in text:
            return "TOILET"
        if "bath" in text or "tub" in text:
            return "BATHTUB"
        if "shower" in text:
            return "SHOWER"
        if "range" in text or "stove" in text:
            return "RANGE"
        if "refrigerator" in text or "fridge" in text:
            return "REFRIGERATOR"
        if "microwave" in text:
            return "MICROWAVE"
        if "panel" in text:
            return "ELEC_PANEL"
        if "telephone" in text:
            return "TELEPHONE"
        if "fire alarm" in text:
            return "FIRE_ALARM"
        return "TERMINAL_OTHER"

    if ifc_class == "IfcFlowSegment":
        if "duct" in text:
            return "DUCT"
        if "conduit" in text:
            return "CONDUIT"
        if "cold water" in text:
            return "PIPE_COLD"
        if "hot water" in text:
            return "PIPE_HOT"
        if "waste" in text:
            return "PIPE_WASTE"
        if "pvc" in text:
            return "PIPE_PVC"
        if "mechanical" in text:
            return "PIPE_MECH"
        return "PIPE_OTHER"

    if ifc_class == "IfcFlowFitting":
        if "elbow" in text:
            return "ELBOW"
        if "tee" in text:
            return "TEE"
        if "transition" in text:
            return "TRANSITION"
        if "bend" in text:
            return "BEND"
        if "drain" in text:
            return "DRAIN"
        return "FITTING_OTHER"

    if ifc_class == "IfcBeam":
        return "BEAM_STEEL"

    if ifc_class == "IfcMember":
        if "curtain" in text:
            return "CURTAIN_MULLION"
        if "stair" in text:
            return "STAIR_MEMBER"
        return "MEMBER_OTHER"

    if ifc_class == "IfcColumn":
        return "COLUMN"

    if ifc_class == "IfcPlate":
        if "glazed" in text or "glass" in text:
            return "GLAZING_PANEL"
        return "PLATE_OTHER"

    if ifc_class == "IfcStairFlight":
        return "STAIR_FLIGHT"

    if ifc_class == "IfcRailing":
        return "RAILING"

    return None


def populate_dictionary(conn):
    """
    Read elements_meta, normalize names (strip GUIDs), group by
    (ifc_class, normalized_type), parse dimensions, and populate element_dictionary.
    """
    rows = conn.execute("""
        SELECT ifc_class, element_name, element_type
        FROM elements_meta
        ORDER BY ifc_class
    """).fetchall()

    # Group by (ifc_class, normalized_type) to collapse per-instance GUIDs
    groups = defaultdict(lambda: {"count": 0, "name": None, "type": None})
    for ifc_class, element_name, element_type in rows:
        category = CATEGORY_MAP.get(ifc_class)
        if not category:
            continue

        norm_name = _strip_guid(element_name)
        norm_type = _strip_guid(element_type)
        key = (ifc_class, norm_name, norm_type)
        groups[key]["count"] += 1
        groups[key]["name"] = norm_name
        groups[key]["type"] = norm_type

    entries = []
    for (ifc_class, norm_name, norm_type), info in sorted(groups.items()):
        category = CATEGORY_MAP[ifc_class]
        width_mm, height_mm, depth_mm = parse_dimensions(norm_name, norm_type)
        subtype = infer_subtype(ifc_class, norm_name, norm_type, width_mm, height_mm)
        ref_type = norm_type or norm_name or ""

        entries.append((
            ifc_class, ref_type, category, subtype,
            width_mm, height_mm, depth_mm,
            info["count"], None
        ))

    conn.executemany("""
        INSERT INTO element_dictionary
        (ref_ifc_class, ref_type_name, compiler_category, compiler_subtype,
         width_mm, height_mm, depth_mm, instance_count, notes)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, entries)
    conn.commit()

    return entries


def _strip_guid(name):
    """Strip trailing :GUID from element name like 'Foo:Bar:123456'.
    Handles chained GUIDs: 'M_Foo:Type:Type:123456' → 'M_Foo:Type'
    """
    if not name:
        return name
    parts = name.split(":")
    # Strip trailing numeric GUIDs (5+ digits)
    while len(parts) >= 2 and parts[-1].strip().isdigit() and len(parts[-1].strip()) >= 5:
        parts = parts[:-1]
    # Strip duplicate trailing segments: 'M_Foo:Type:Type' → 'M_Foo:Type'
    while len(parts) >= 2 and parts[-1] == parts[-2]:
        parts = parts[:-1]
    return ":".join(parts)


def print_dictionary(conn):
    """Print dictionary summary for verification."""
    rows = conn.execute("""
        SELECT compiler_category, compiler_subtype, ref_ifc_class,
               ref_type_name, width_mm, height_mm, depth_mm, instance_count
        FROM element_dictionary
        ORDER BY compiler_category, compiler_subtype
    """).fetchall()

    print(f"\n  Element Dictionary: {len(rows)} type entries")
    print(f"  {'Category':<12} {'Subtype':<25} {'IFC Class':<22} {'Type':<30} {'W':>5} {'H':>5} {'D':>5} {'#':>3}")
    print(f"  {'-'*12} {'-'*25} {'-'*22} {'-'*30} {'-'*5} {'-'*5} {'-'*5} {'-'*3}")

    total_instances = 0
    for cat, sub, cls, typ, w, h, d, cnt in rows:
        typ_short = (typ or "")[:30]
        w_s = str(w) if w else ""
        h_s = str(h) if h else ""
        d_s = str(d) if d else ""
        print(f"  {cat:<12} {(sub or '?'):<25} {cls:<22} {typ_short:<30} {w_s:>5} {h_s:>5} {d_s:>5} {cnt:>3}")
        total_instances += cnt

    print(f"\n  Total: {len(rows)} types, {total_instances} instances")
