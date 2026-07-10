#!/usr/bin/env python3
# ⚠ DO NOT REMOVE — SCOPE: §LIVEWIRE spec (prompts/Modeller/DISC_Walker/RESUME_DISC_WALKER_ENVELOPE_BOUND.md).
# Projects a building's REAL spatial_structure (IfcSpace rows + their storey parents) from an extracted DB
# into a shipped modeller *_ARC.db so disc_walker's spacesOf()/placeSchedule can engage in the live browser.
# Read the log after every run — exit code alone is not evidence.
"""
project_spaces_to_arcdb.py — verbatim table copy of spatial_structure from an extracted DB
(space LongNames already stamped into object_type by stamp_space_longnames.py) into an ARC db.

NON-INVENT: pure row copy, all 12 columns byte-verbatim. Refuses if the source has zero REAL
(non-synthetic) IfcSpace rows, or if any real space lacks a stamped object_type (the space-TYPE
key the schedule hangs on — shipping untyped spaces would make placeSchedule silently skip them).

Usage: project_spaces_to_arcdb.py <source_extracted_db> <target_arc_db>
Idempotent: DROP + CREATE + copy; re-runs converge.
"""
import sqlite3
import sys


def log(msg):
    print(f"§SPACEPROJ {msg}", flush=True)


def main():
    src_db, tgt_db = sys.argv[1], sys.argv[2]
    src = sqlite3.connect(f"file:{src_db}?mode=ro", uri=True)
    real = src.execute(
        "SELECT COUNT(*), SUM(COALESCE(object_type,'')!='') FROM spatial_structure "
        "WHERE type='IfcSpace' AND guid NOT LIKE 'RM\\_%' ESCAPE '\\' AND name NOT LIKE '%≈%'"
    ).fetchone()
    if not real[0]:
        log(f"FATAL {src_db} has no real IfcSpace rows — nothing to project")
        sys.exit(1)
    if real[1] != real[0]:
        log(f"FATAL only {real[1]}/{real[0]} real spaces carry a stamped object_type — "
            "run stamp_space_longnames.py on the source first")
        sys.exit(1)
    rows = src.execute("SELECT guid, type, name, parent_guid, object_type, predefined_type, "
                       "center_x, center_y, center_z, size_x, size_y, size_z "
                       "FROM spatial_structure").fetchall()
    ddl = src.execute("SELECT sql FROM sqlite_master WHERE name='spatial_structure'").fetchone()[0]
    src.close()

    tgt = sqlite3.connect(tgt_db)
    tgt.execute("DROP TABLE IF EXISTS spatial_structure")
    tgt.execute(ddl)
    tgt.executemany("INSERT INTO spatial_structure VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", rows)
    tgt.commit()
    back = tgt.execute("SELECT COUNT(*) FROM spatial_structure WHERE type='IfcSpace' "
                       "AND guid NOT LIKE 'RM\\_%' ESCAPE '\\' AND name NOT LIKE '%≈%' "
                       "AND COALESCE(object_type,'')!='' AND size_x>0.1 AND size_y>0.1").fetchone()[0]
    sample = tgt.execute("SELECT name||'→'||object_type FROM spatial_structure "
                         "WHERE type='IfcSpace' LIMIT 3").fetchall()
    tgt.close()
    log(f"DONE {len(rows)} rows → {tgt_db}; read-back real typed sized spaces={back} "
        f"(source said {real[0]}); sample {[s[0] for s in sample]}")
    if back == 0:
        log("FATAL read-back found 0 usable spaces")
        sys.exit(1)


if __name__ == "__main__":
    main()
