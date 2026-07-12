#!/usr/bin/env python3
# ⚠ DO NOT REMOVE — SCOPE: builds a standalone <Building>_ARC.db from a multi-discipline
# <Building>_extracted.db, filtering elements_meta by discipline='ARC' (Walker Doctrine: discipline
# is a WHERE column, never a per-building file — see docs/internal/WalkerDoctrine.md).
# Read the log after every run — exit code alone is not evidence.
"""
extract_arc_discipline.py — filter an extracted DB down to its ARC-discipline elements and shape
it into the modeller *_ARC.db schema (project_metadata, elements_meta, element_transforms,
element_instances, spatial_structure, schedules, tasks, task_sequences, task_elements — confirmed
via `sqlite3 Duplex_ARC.db .schema`).

NON-INVENT: no filter logic invented here — reuses the discipline='ARC' predicate already applied
client-side by modeller/str_walker_outliner.js's _filterArc(), and the verbatim-column-copy shape
of scripts/project_spaces_to_arcdb.py. Refuses if the source has zero discipline='ARC' rows.

- elements_meta: WHERE discipline='ARC', verbatim columns.
- element_transforms / element_instances: cascaded via guid join to the filtered elements_meta.
- spatial_structure: copied verbatim in full (no discipline column — storeys/spaces are structural
  context, not per-discipline; same choice project_spaces_to_arcdb.py makes).
- project_metadata: copied verbatim in full.
- schedules / tasks / task_sequences / task_elements: created empty (matches observed convention —
  these are 0 rows in every existing *_ARC.db AND in the source *_extracted.db; not extraction output).
- component_geometries / bom_tree / rel_contained_in_space: intentionally NOT carried — absent from
  every existing *_ARC.db (mesh ships separately, e.g. mesh.db; bom_tree/rel_contained_in_space are
  not part of the ARC-walk schema).

Usage: extract_arc_discipline.py <source_extracted_db> <target_arc_db>
Idempotent: DROP + CREATE + copy; re-runs converge.
"""
import sqlite3
import sys

ARC_SCHEMA = {
    "project_metadata": "CREATE TABLE project_metadata (key TEXT PRIMARY KEY, value TEXT)",
    "elements_meta": "CREATE TABLE elements_meta (guid TEXT PRIMARY KEY, ifc_class TEXT, "
        "element_name TEXT, storey TEXT, discipline TEXT, material_name TEXT, material_rgba TEXT, "
        "building TEXT)",
    "element_transforms": "CREATE TABLE element_transforms (guid TEXT PRIMARY KEY, center_x REAL, "
        "center_y REAL, center_z REAL, rotation_x REAL, rotation_y REAL, rotation_z REAL, "
        "bbox_x REAL, bbox_y REAL, bbox_z REAL)",
    "element_instances": "CREATE TABLE element_instances (guid TEXT PRIMARY KEY, geometry_hash TEXT)",
    "schedules": "CREATE TABLE schedules (schedule_id TEXT PRIMARY KEY, name TEXT, status TEXT, "
        "created_date TEXT)",
    "tasks": "CREATE TABLE tasks (task_id TEXT PRIMARY KEY, schedule_id TEXT, name TEXT, "
        "start_date TEXT, finish_date TEXT, duration_days REAL, status TEXT)",
    "task_sequences": "CREATE TABLE task_sequences (predecessor_id TEXT, successor_id TEXT, "
        "sequence_type TEXT, lag_days REAL DEFAULT 0, PRIMARY KEY (predecessor_id, successor_id))",
    "task_elements": "CREATE TABLE task_elements (task_id TEXT, guid TEXT, "
        "PRIMARY KEY (task_id, guid))",
}


def log(msg):
    print(f"§ARCFILTER {msg}", flush=True)


def main():
    src_db, tgt_db = sys.argv[1], sys.argv[2]
    src = sqlite3.connect(f"file:{src_db}?mode=ro", uri=True)

    arc_count = src.execute("SELECT COUNT(*) FROM elements_meta WHERE discipline='ARC'").fetchone()[0]
    if not arc_count:
        log(f"FATAL {src_db} has 0 discipline='ARC' rows in elements_meta — nothing to extract")
        sys.exit(1)

    elements_meta = src.execute(
        "SELECT guid, ifc_class, element_name, storey, discipline, material_name, material_rgba, "
        "building FROM elements_meta WHERE discipline='ARC'").fetchall()
    arc_guids = [r[0] for r in elements_meta]
    placeholders = ",".join("?" * len(arc_guids))
    transforms = src.execute(
        f"SELECT guid, center_x, center_y, center_z, rotation_x, rotation_y, rotation_z, "
        f"bbox_x, bbox_y, bbox_z FROM element_transforms WHERE guid IN ({placeholders})",
        arc_guids).fetchall()
    instances = src.execute(
        f"SELECT guid, geometry_hash FROM element_instances WHERE guid IN ({placeholders})",
        arc_guids).fetchall()
    spatial = src.execute(
        "SELECT guid, type, name, parent_guid, object_type, predefined_type, "
        "center_x, center_y, center_z, size_x, size_y, size_z FROM spatial_structure").fetchall()
    metadata = src.execute("SELECT key, value FROM project_metadata").fetchall()
    src.close()

    tgt = sqlite3.connect(tgt_db)
    for name, ddl in ARC_SCHEMA.items():
        tgt.execute(f"DROP TABLE IF EXISTS {name}")
        tgt.execute(ddl)
    tgt.execute("DROP TABLE IF EXISTS spatial_structure")
    tgt.execute("CREATE TABLE spatial_structure(guid TEXT, type TEXT, name TEXT, parent_guid TEXT, "
                "object_type TEXT, predefined_type TEXT, center_x REAL, center_y REAL, "
                "center_z REAL, size_x REAL, size_y REAL, size_z REAL)")

    tgt.executemany("INSERT INTO elements_meta VALUES (?,?,?,?,?,?,?,?)", elements_meta)
    tgt.executemany("INSERT INTO element_transforms VALUES (?,?,?,?,?,?,?,?,?,?)", transforms)
    tgt.executemany("INSERT INTO element_instances VALUES (?,?)", instances)
    tgt.executemany("INSERT INTO spatial_structure VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", spatial)
    tgt.executemany("INSERT INTO project_metadata VALUES (?,?)", metadata)
    tgt.commit()

    back_meta = tgt.execute("SELECT COUNT(*) FROM elements_meta").fetchone()[0]
    back_xf = tgt.execute("SELECT COUNT(*) FROM element_transforms").fetchone()[0]
    back_inst = tgt.execute("SELECT COUNT(*) FROM element_instances").fetchone()[0]
    back_sp = tgt.execute("SELECT COUNT(*) FROM spatial_structure").fetchone()[0]
    tgt.close()

    log(f"DONE source={src_db} target={tgt_db}")
    log(f"elements_meta ARC rows: source={arc_count} written={back_meta}")
    log(f"element_transforms written={back_xf} (expect {len(transforms)})")
    log(f"element_instances written={back_inst} (expect {len(instances)})")
    log(f"spatial_structure written={back_sp} (verbatim full copy)")
    if back_meta != arc_count or back_xf != len(transforms) or back_inst != len(instances):
        log("FATAL read-back count mismatch")
        sys.exit(1)


if __name__ == "__main__":
    main()
