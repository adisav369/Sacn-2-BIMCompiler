# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
Top-up an existing _extracted.db with elements missed by a previous extraction.

Useful when the extractor code was updated (e.g. new classes added, whitelist
replaced by blacklist) and you want to add missing elements without re-running
the full extraction from scratch.

Usage:
    python3 scripts/topup_extracted_db.py \
        --ifc DAGCompiler/lib/input/IFC/UNMERGED/Hospital_IFC4_ELE.ifc \
        --db  DAGCompiler/lib/input/Hospital_PerDisc_extracted.db

    # Top-up from multiple source files:
    for f in DAGCompiler/lib/input/IFC/UNMERGED/Hospital_IFC4_*.ifc; do
        python3 scripts/topup_extracted_db.py --ifc "$f" --db Hospital_PerDisc_extracted.db
    done
"""

import argparse
import sqlite3
import sys
from pathlib import Path


def topup(ifc_path: str, db_path: str, dry_run: bool = False):
    import ifcopenshell
    import ifcopenshell.geom
    import numpy as np
    import hashlib

    sys.path.insert(0, str(Path(__file__).parent.parent / "DAGCompiler/python"))
    from extractIFCtoDB import (
        NON_GEOMETRIC_CLASSES, DISCIPLINE_MAP, infer_discipline,
        element_boolean_depth, bbox_from_placement, geometry_hash,
        get_material_for_element, get_colour_for_element,
        get_storey_for_element, get_space_for_element,
    )

    import ifcopenshell.util.unit as ifcunit

    import ifcopenshell.util.placement as ifcplace

    print(f"Opening IFC: {ifc_path}")
    ifc_file = ifcopenshell.open(ifc_path)
    unit_scale = ifcunit.calculate_unit_scale(ifc_file, 'LENGTHUNIT')

    # Site origin — subtract so topup coords stay consistent with a normalised DB
    site_ox = site_oy = site_oz = 0.0
    try:
        sites = ifc_file.by_type('IfcSite')
        if sites and sites[0].ObjectPlacement:
            m = ifcplace.get_local_placement(sites[0].ObjectPlacement)
            site_ox = float(m[0][3]) * unit_scale
            site_oy = float(m[1][3]) * unit_scale
            site_oz = float(m[2][3]) * unit_scale
    except Exception:
        pass
    if abs(site_oz) > 1.0:
        print(f"  Site origin: ({site_ox:.3f}, {site_oy:.3f}, {site_oz:.3f})m — will subtract")

    conn = sqlite3.connect(db_path)

    # Get existing GUIDs — skip anything already in the DB
    existing_guids = set(r[0] for r in conn.execute("SELECT guid FROM elements_meta"))
    print(f"  Existing elements in DB: {len(existing_guids)}")

    # Find all classes in this file not yet in the DB
    missing_classes = set()
    for elem in ifc_file.by_type("IfcProduct"):
        if elem.GlobalId not in existing_guids and \
           elem.is_a() not in NON_GEOMETRIC_CLASSES and \
           elem.Representation is not None:
            missing_classes.add(elem.is_a())

    if not missing_classes:
        print("  Nothing to top up — all elements already in DB.")
        conn.close()
        return

    print(f"  New classes to add: {sorted(missing_classes)}")

    settings = ifcopenshell.geom.settings()
    settings.set(settings.USE_WORLD_COORDS, True)
    settings.set(settings.WELD_VERTICES, True)

    settings_no_bool = ifcopenshell.geom.settings()
    settings_no_bool.set(settings_no_bool.USE_WORLD_COORDS, True)
    settings_no_bool.set(settings_no_bool.WELD_VERTICES, True)
    settings_no_bool.set(settings_no_bool.DISABLE_BOOLEAN_RESULT, True)

    BOOL_DEPTH_THRESHOLD = 3

    existing_hashes = set(r[0] for r in conn.execute("SELECT geometry_hash FROM base_geometries"))
    next_id = (conn.execute("SELECT MAX(id) FROM elements_meta").fetchone()[0] or 0) + 1

    added = skipped = 0

    for cls in sorted(missing_classes):
        for elem in ifc_file.by_type(cls):
            if elem.GlobalId in existing_guids or elem.is_a() != cls:
                continue
            try:
                use_bbox = False
                depth = element_boolean_depth(elem)
                if depth > BOOL_DEPTH_THRESHOLD:
                    try:
                        shape = ifcopenshell.geom.create_shape(settings_no_bool, elem)
                    except Exception:
                        use_bbox = True
                else:
                    try:
                        shape = ifcopenshell.geom.create_shape(settings, elem)
                    except Exception:
                        try:
                            shape = ifcopenshell.geom.create_shape(settings_no_bool, elem)
                        except Exception:
                            use_bbox = True

                if use_bbox:
                    vblob, fblob, center, minXYZ, maxXYZ = bbox_from_placement(elem)
                    # bbox_from_placement uses get_local_placement (native file units),
                    # but create_shape(USE_WORLD_COORDS) returns metres — apply scale.
                    center  = center  * unit_scale
                    minXYZ  = minXYZ  * unit_scale
                    maxXYZ  = maxXYZ  * unit_scale
                else:
                    geo = shape.geometry
                    verts = np.array(geo.verts, dtype=np.float64).reshape(-1, 3)
                    faces = np.array(geo.faces, dtype=np.int32).reshape(-1, 3)
                    if len(verts) < 3 or len(faces) < 1:
                        vblob, fblob, center, minXYZ, maxXYZ = bbox_from_placement(elem)
                        center  = center  * unit_scale
                        minXYZ  = minXYZ  * unit_scale
                        maxXYZ  = maxXYZ  * unit_scale
                    else:
                        minXYZ = verts.min(axis=0)
                        maxXYZ = verts.max(axis=0)
                        center = (minXYZ + maxXYZ) / 2.0
                        v_centered = (verts - center).astype(np.float32)
                        vblob = v_centered.tobytes()
                        fblob = faces.astype(np.int32).tobytes()

                # Subtract site origin so coords align with a normalised DB
                center  = center  - np.array([site_ox, site_oy, site_oz])
                minXYZ  = minXYZ  - np.array([site_ox, site_oy, site_oz])
                maxXYZ  = maxXYZ  - np.array([site_ox, site_oy, site_oz])

                ghash = geometry_hash(vblob, fblob)
                guid = elem.GlobalId

                if not dry_run:
                    if ghash not in existing_hashes:
                        conn.execute(
                            "INSERT OR IGNORE INTO base_geometries "
                            "(geometry_hash, vertices, faces, vertex_count, face_count) "
                            "VALUES (?,?,?,?,?)",
                            (ghash, vblob, fblob,
                             len(vblob) // 12, len(fblob) // 12))
                        existing_hashes.add(ghash)

                    discipline = infer_discipline(cls)
                    storey = get_storey_for_element(elem)
                    material_name = get_material_for_element(elem)
                    material_rgba = get_colour_for_element(elem)
                    elem_type = None
                    try:
                        for rel in elem.IsDefinedBy:
                            if rel.is_a("IfcRelDefinesByType"):
                                elem_type = rel.RelatingType.Name
                                break
                    except Exception:
                        pass

                    eid = next_id
                    next_id += 1
                    conn.execute(
                        "INSERT OR IGNORE INTO elements_meta "
                        "(id, guid, discipline, ifc_class, element_name, element_type, "
                        "storey, material_name, material_rgba) VALUES (?,?,?,?,?,?,?,?,?)",
                        (eid, guid, discipline, cls, getattr(elem, 'Name', None),
                         elem_type, storey, material_name, material_rgba))
                    conn.execute(
                        "INSERT OR IGNORE INTO element_instances (guid, geometry_hash) "
                        "VALUES (?,?)", (guid, ghash))
                    conn.execute(
                        "INSERT OR IGNORE INTO element_transforms "
                        "(guid, center_x, center_y, center_z, transform_source) "
                        "VALUES (?,?,?,?,'ifc_extract')",
                        (guid, float(center[0]), float(center[1]), float(center[2])))
                    conn.execute(
                        "INSERT OR IGNORE INTO elements_rtree "
                        "(id, minX, maxX, minY, maxY, minZ, maxZ) "
                        "VALUES (?,?,?,?,?,?,?)",
                        (eid, float(minXYZ[0]), float(maxXYZ[0]),
                         float(minXYZ[1]), float(maxXYZ[1]),
                         float(minXYZ[2]), float(maxXYZ[2])))
                    space = get_space_for_element(elem)
                    if space:
                        conn.execute(
                            "INSERT OR IGNORE INTO rel_contained_in_space VALUES (?,?)",
                            (guid, space.GlobalId))

                    existing_guids.add(guid)
                added += 1

            except Exception as e:
                skipped += 1

        if added % 500 == 0 and added > 0:
            conn.commit()

    conn.commit()
    conn.close()

    total = added + skipped
    print(f"  Added: {added}, Skipped: {skipped} — DB now has elements from {Path(ifc_path).name}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ifc", required=True, help="IFC source file")
    parser.add_argument("--db", required=True, help="Target extracted.db to top up")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    topup(args.ifc, args.db, dry_run=args.dry_run)


if __name__ == "__main__":
    main()
