#!/usr/bin/env python3
"""normalize_storey.py — clear the corrupted `storey` field in an existing building DB.

WHY: Revit exports its reference PLANES (Ceiling, Top-of-Steel, …) as IfcBuildingStorey, so MEP/STR
elements land on "Level 2 Ceiling" / "Level 2 TOS" instead of the real "Level 2" — junk sibling
storeys that pollute the Find Storey/Room trees (Hospital: ~26% of elements). The injector is fixed in
tools/extract.py (normalize_storey, applied at every storey write); this script cleans DBs that were
already built. DETERMINISTIC: strip a known reference-plane suffix → base level. Idempotent. Never
invents a level. Run again any time (e.g. after a regen) — it's a no-op once clean.

Usage: python3 scripts/normalize_storey.py <db> [<db> ...]
"""
import sys
# --- utf8-console guard (2026-09-05) ---------------------------------------------
# This script prints non-ASCII (box-drawing, arrows, section marks). On a console whose
# encoding is not UTF-8 -- Windows cp1252 is the common case -- print() raises
# UnicodeEncodeError and kills the script mid-run. That is not hypothetical: it aborted
# scripts/restore_generative_meshes.py immediately after it created its back-compat view
# but BEFORE it restored any mesh, which is why the component_library repair silently
# needed two passes to converge. errors="replace" is deliberate: a mangled glyph in a log
# line is always better than a dead pipeline stage.
for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError, OSError):
        pass  # already-wrapped, detached, or replaced by a non-TextIOWrapper (e.g. in tests)
# ---------------------------------------------------------------------------------
import sqlite3
import sys

# Reference-plane qualifier as SUFFIX ("Level 2 Ceiling"), PREFIX ("Ceiling Level 01") or bare
# ("Ceiling"). Strip in any position; bare → "Unknown". Mirrors tools/extract.py.
REF_LEVEL_TOKENS = ("Ceiling", "TOS", "T.O.S.", "Top of Steel", "Soffit")


def normalize_storey(name):
    if not name:
        return name
    s = str(name).strip()
    changed = True
    while changed:
        changed = False
        low = s.lower()
        for tok in REF_LEVEL_TOKENS:
            t = tok.lower()
            if low == t:
                s = ""; changed = True; break
            if low.endswith(" " + t):
                s = s[:-(len(t) + 1)].strip(); changed = True; break
            if low.startswith(t + " "):
                s = s[len(t) + 1:].strip(); changed = True; break
    return s if s else "Unknown"


def _has_col(conn, table, col):
    try:
        return any(r[1] == col for r in conn.execute(f"PRAGMA table_info({table})"))
    except sqlite3.Error:
        return False


def _table_exists(conn, table):
    return conn.execute(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", (table,)).fetchone() is not None


def normalize_db(path):
    conn = sqlite3.connect(path)
    changed = {}
    # 1) elements_meta.storey — the field the Find Storey lens groups by.
    if _table_exists(conn, "elements_meta") and _has_col(conn, "elements_meta", "storey"):
        rows = conn.execute(
            "SELECT DISTINCT storey FROM elements_meta WHERE storey IS NOT NULL").fetchall()
        for (old,) in rows:
            new = normalize_storey(old)
            if new != old:
                n = conn.execute("UPDATE elements_meta SET storey=? WHERE storey=?", (new, old)).rowcount
                changed[old] = (new, n)
    # 2) spatial_structure (IfcBuildingStorey) — normalize names + MERGE rows that collapse to the
    #    same level (re-point children's parent_guid to the canonical storey, drop the duplicate),
    #    so the Room lens groups rooms under the real floor. Skipped if the table/cols are absent.
    merged = 0
    if _table_exists(conn, "spatial_structure") and _has_col(conn, "spatial_structure", "name"):
        st = conn.execute(
            "SELECT guid, name FROM spatial_structure WHERE type='IfcBuildingStorey'").fetchall()
        canon = {}                       # normalized name -> canonical guid (kept)
        remap = {}                       # dup guid -> canonical guid
        for guid, name in st:
            nm = normalize_storey(name)
            if nm not in canon:
                canon[nm] = guid
                if nm != name:
                    conn.execute("UPDATE spatial_structure SET name=? WHERE guid=?", (nm, guid))
            else:
                remap[guid] = canon[nm]
        for dup, keep in remap.items():
            if _has_col(conn, "spatial_structure", "parent_guid"):
                conn.execute("UPDATE spatial_structure SET parent_guid=? WHERE parent_guid=?", (keep, dup))
            conn.execute("DELETE FROM spatial_structure WHERE guid=?", (dup,))
            merged += 1
    conn.commit()
    # Witness
    pseudo = 0
    if _table_exists(conn, "elements_meta") and _has_col(conn, "elements_meta", "storey"):
        pseudo = conn.execute(
            "SELECT COUNT(*) FROM elements_meta WHERE " +
            " OR ".join("storey LIKE ?" for _ in REF_LEVEL_TOKENS),
            tuple("%" + s.strip() + "%" for s in REF_LEVEL_TOKENS)).fetchone()[0]
        distinct = conn.execute("SELECT COUNT(DISTINCT storey) FROM elements_meta").fetchone()[0]
    else:
        distinct = 0
    conn.close()
    print(f"§NORMALIZE {path}")
    for old, (new, n) in sorted(changed.items()):
        print(f"   '{old}' -> '{new}'  ({n} elems)")
    print(f"   storey rows merged: {merged} · distinct storeys now: {distinct} · "
          f"residual reference-suffix elems: {pseudo}  ({'CLEAN' if pseudo == 0 else 'CHECK'})")
    return pseudo


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__); sys.exit(2)
    bad = 0
    for p in sys.argv[1:]:
        try:
            bad += normalize_db(p)
        except sqlite3.Error as e:
            print(f"§NORMALIZE_ERR {p}: {e}"); bad += 1
    sys.exit(1 if bad else 0)
