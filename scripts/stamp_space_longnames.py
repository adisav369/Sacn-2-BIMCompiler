#!/usr/bin/env python3
"""
stamp_space_longnames.py — copy each IfcSpace's LongName VERBATIM from the source IFC
into spatial_structure.object_type of an extracted DB (object_type is empty for these rows).

WHY (2026-07-10, Step 2 of the geometry-hell fix): Duplex_extracted.db carries 21 REAL
IfcSpace rows (real GUIDs, real bboxes) in spatial_structure, but extraction kept only the
room NUMBER (Name 'A101') and dropped the LongName ('Foyer', 'Bathroom 1', ...) — and the
LongName is the space-TYPE key the per-space schedule (rule_space_schedule/rule_space_alias)
hangs on. NON-INVENT: pure guid→LongName copy from the IFC's own IFCSPACE records; rows whose
guid is not found in the IFC are left untouched and reported.

Usage: stamp_space_longnames.py <extracted_db> <source_ifc>
"""
import re
import sqlite3
import sys


def parse_ifcspace_longnames(ifc_path):
    """guid → LongName from IFCSPACE records. IFC2X3 IfcSpace: (GlobalId, OwnerHistory,
    Name, Description, ObjectType, ObjectPlacement, Representation, LongName, ...)."""
    out = {}
    rx = re.compile(r"=\s*IFCSPACE\s*\(", re.IGNORECASE)
    with open(ifc_path, "r", errors="ignore") as f:
        for line in f:
            if not rx.search(line):
                continue
            args, cur, inq = [], [], False
            body = line[line.index("(") + 1:]
            for ch in body:
                if ch == "'":
                    inq = not inq
                    cur.append(ch)
                elif ch == "," and not inq:
                    args.append("".join(cur).strip())
                    cur = []
                elif ch == ")" and not inq:
                    args.append("".join(cur).strip())
                    break
                else:
                    cur.append(ch)
            if len(args) < 8:
                continue
            guid = args[0].strip("'")
            longname = args[7].strip("'") if args[7].startswith("'") else None
            if guid and longname:
                out[guid] = longname
    return out


def main():
    if len(sys.argv) != 3:
        print("usage: stamp_space_longnames.py <extracted_db> <source_ifc>", file=sys.stderr)
        sys.exit(2)
    db_path, ifc_path = sys.argv[1], sys.argv[2]
    names = parse_ifcspace_longnames(ifc_path)
    print(f"§STAMP-LN {ifc_path}: {len(names)} IFCSPACE LongNames parsed")
    con = sqlite3.connect(db_path)
    cur = con.cursor()
    spaces = cur.execute(
        "SELECT guid, name FROM spatial_structure WHERE type='IfcSpace'").fetchall()
    stamped, missing = 0, []
    for guid, name in spaces:
        ln = names.get(guid)
        if ln:
            cur.execute("UPDATE spatial_structure SET object_type=? WHERE guid=?", (ln, guid))
            stamped += 1
            print(f"§STAMP-LN {guid} {name} → '{ln}'")
        else:
            missing.append(f"{guid}({name})")
    con.commit()
    con.close()
    print(f"§STAMP-LN {db_path}: {stamped}/{len(spaces)} spaces stamped"
          + (f"; NOT in IFC (untouched): {missing}" if missing else ""))


if __name__ == "__main__":
    main()
