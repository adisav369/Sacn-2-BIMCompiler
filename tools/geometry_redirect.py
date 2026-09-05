#!/usr/bin/env python3
"""
Geometry Hash Redirect Tool — Back-Office Admin CLI

Scans all *_extracted.db files for duplicate element meshes (identified by
element name pattern), shows a breakdown per sub-type with vertex/face counts
and building/storey/discipline coverage, lets the admin pick a canonical hash,
then writes geometry_hash_redirect rows to component_library.db.

The lod_manager resolves deprecated hashes to canonical at load time via this
table. Extracted DBs are NEVER modified.

Usage:
  python3 tools/geometry_redirect.py                        # dry-run
  python3 tools/geometry_redirect.py --confirm              # commit redirects
  python3 tools/geometry_redirect.py --element-filter "fan" # other element types
  python3 tools/geometry_redirect.py --list                 # show existing redirects
  python3 tools/geometry_redirect.py --remove <deprecated_hash>  # remove a redirect

Requires:
  DAGCompiler/lib/input/*_extracted.db
  library/component_library.db
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

import argparse
import shutil
import sqlite3
import sys
from collections import defaultdict
from datetime import datetime
from pathlib import Path

# ── paths ──────────────────────────────────────────────────────────────────────
REPO_ROOT    = Path(__file__).resolve().parent.parent
INPUT_DIR    = REPO_ROOT / "DAGCompiler" / "lib" / "input"
LIBRARY_DB   = REPO_ROOT / "library" / "component_library.db"

REDIRECT_DDL = """
CREATE TABLE IF NOT EXISTS geometry_hash_redirect (
    deprecated_hash  TEXT PRIMARY KEY,
    canonical_hash   TEXT NOT NULL,
    sub_type         TEXT,
    note             TEXT,
    created_at       TEXT NOT NULL
);
"""

# ── element pattern helpers ────────────────────────────────────────────────────

def classify_subtype(name: str) -> str:
    """Classify element sub-type from name. Extend as needed."""
    n = name.lower()
    if "upright" in n:
        return "upright"
    if "sidewall" in n or "side wall" in n:
        return "sidewall"
    return "pendent"   # default for sprinklers; neutral for other types


# ── scan ───────────────────────────────────────────────────────────────────────

def scan_extracted_dbs(element_filter: str):
    """
    Scan all *_extracted.db files.
    Returns:
      hash_info  : dict  geometry_hash -> {vertex_count, face_count, sub_type,
                                            sample_name, buildings, storeys, discs,
                                            total_instances, dominant_rotation}
    """
    dbs = sorted(INPUT_DIR.glob("*_extracted.db"))
    if not dbs:
        print(f"ERROR: no *_extracted.db found in {INPUT_DIR}")
        sys.exit(1)

    pattern = f"%{element_filter.lower()}%"
    hash_info = {}   # geometry_hash -> aggregated info

    print(f"\nScanning {len(dbs)} extracted DBs for '{element_filter}' ...\n")

    for db_path in dbs:
        try:
            conn = sqlite3.connect(str(db_path))
            conn.row_factory = sqlite3.Row
        except Exception as e:
            print(f"  SKIP {db_path.name}: {e}")
            continue

        # check tables exist
        tables = {r[0] for r in conn.execute(
            "SELECT name FROM sqlite_master WHERE type='table'").fetchall()}
        if not {"element_instances", "elements_meta"} <= tables:
            conn.close()
            continue

        building_name = db_path.stem.replace("_extracted", "")

        # detect whether base_geometries has vertex_count/face_count columns
        bg_cols = set()
        if "base_geometries" in tables:
            bg_cols = {r[1] for r in conn.execute(
                "PRAGMA table_info(base_geometries)").fetchall()}

        has_geom = "base_geometries" in tables and "vertex_count" in bg_cols

        # Check if element_transforms exists for rotation data
        has_transforms = "element_transforms" in tables

        if has_geom:
            rows = conn.execute(f"""
                SELECT
                    i.geometry_hash,
                    bg.vertex_count,
                    bg.face_count,
                    m.element_name,
                    m.storey,
                    m.discipline
                    {', t.rotation_x, t.rotation_y, t.rotation_z' if has_transforms else ', NULL, NULL, NULL'}
                FROM element_instances i
                JOIN elements_meta m  ON i.guid = m.guid
                JOIN base_geometries bg ON i.geometry_hash = bg.geometry_hash
                {'JOIN element_transforms t ON m.guid = t.guid' if has_transforms else ''}
                WHERE (
                    lower(m.element_name) LIKE ?
                    OR lower(m.ifc_class)    LIKE ?
                    OR lower(m.element_type) LIKE ?
                )
                AND i.geometry_hash IS NOT NULL
            """, (pattern, pattern, pattern)).fetchall()
        else:
            rows = conn.execute(f"""
                SELECT
                    i.geometry_hash,
                    NULL as vertex_count,
                    NULL as face_count,
                    m.element_name,
                    m.storey,
                    m.discipline
                    {', t.rotation_x, t.rotation_y, t.rotation_z' if has_transforms else ', NULL, NULL, NULL'}
                FROM element_instances i
                JOIN elements_meta m ON i.guid = m.guid
                {'JOIN element_transforms t ON m.guid = t.guid' if has_transforms else ''}
                WHERE (
                    lower(m.element_name) LIKE ?
                    OR lower(m.ifc_class)    LIKE ?
                    OR lower(m.element_type) LIKE ?
                )
                AND i.geometry_hash IS NOT NULL
            """, (pattern, pattern, pattern)).fetchall()

        conn.close()

        for row in rows:
            ghash = row[0]
            if ghash not in hash_info:
                hash_info[ghash] = {
                    "vertex_count":     row[1] or 0,
                    "face_count":       row[2] or 0,
                    "sub_type":         classify_subtype(row[3] or ""),
                    "sample_name":      (row[3] or "")[:60],
                    "buildings":        defaultdict(int),
                    "storeys":          defaultdict(int),
                    "discs":            defaultdict(int),
                    "total_instances":  0,
                    "rotations":        defaultdict(int),  # (rx,ry,rz) rounded → count
                }
            info = hash_info[ghash]
            info["buildings"][building_name] += 1
            info["storeys"][(row[4] or "?")]  += 1
            info["discs"][(row[5] or "?")]  += 1
            info["total_instances"] += 1
            # Track rotation pattern (rounded to 0.01 rad)
            rx = round(row[6] or 0.0, 2)
            ry = round(row[7] or 0.0, 2)
            rz = round(row[8] or 0.0, 2)
            info["rotations"][(rx, ry, rz)] += 1

    return hash_info


# ── display ────────────────────────────────────────────────────────────────────

def fmt_top(counter: dict, n=3) -> str:
    top = sorted(counter.items(), key=lambda x: -x[1])[:n]
    return ", ".join(f"{k}({v})" for k, v in top)


def dominant_rotation(info: dict):
    """Return the most common (rx, ry, rz) tuple for a hash."""
    rots = info.get("rotations", {})
    if not rots:
        return (0.0, 0.0, 0.0)
    return max(rots.items(), key=lambda x: x[1])[0]


def display_subtype_table(sub_type: str, candidates: list):
    """Print numbered selection table for one sub-type."""
    bar = "─" * 82
    print(f"\n{bar}")
    print(f"  Sub-type: {sub_type.upper()}")
    print(bar)
    print(f"  {'#':>2}  {'HASH':16}  {'VERTS':>6}  {'FACES':>6}  {'INST':>5}  "
          f"{'DOMINANT ROT':>20}  BUILDINGS")
    print(bar)
    for idx, (ghash, info) in enumerate(candidates, start=1):
        buildings_str = fmt_top(info["buildings"])
        storeys_str   = fmt_top(info["storeys"])
        discs_str     = fmt_top(info["discs"])
        dom_rot       = dominant_rotation(info)
        rot_str       = f"({dom_rot[0]:.2f}, {dom_rot[1]:.2f}, {dom_rot[2]:.2f})"
        print(f"  [{idx}] {ghash:16}  "
              f"verts={info['vertex_count']:>5}  "
              f"faces={info['face_count']:>5}  "
              f"inst={info['total_instances']:>4}  "
              f"{rot_str:>20}  "
              f"{buildings_str}")
        print(f"       {'':16}  {'':>6}  {'':>6}  {'':>5}  "
              f"{'':>20}  storeys: {storeys_str}")
        print(f"       {'':16}  {'':>6}  {'':>6}  {'':>5}  "
              f"{'':>20}  disc:    {discs_str}")
        print()
    print(bar)


# ── interactive selection ──────────────────────────────────────────────────────

def pick_canonical(sub_type: str, candidates: list) -> str | None:
    """Ask user to pick canonical hash. Returns hash or None to skip."""
    display_subtype_table(sub_type, candidates)

    if len(candidates) == 1:
        print(f"  Only one hash for {sub_type} — nothing to redirect.")
        return None

    while True:
        raw = input(f"  Pick canonical for {sub_type} [1-{len(candidates)}, s=skip]: ").strip()
        if raw.lower() == "s":
            return None
        try:
            idx = int(raw)
            if 1 <= idx <= len(candidates):
                return candidates[idx - 1][0]
        except ValueError:
            pass
        print(f"  Enter a number between 1 and {len(candidates)}, or 's' to skip.")


# ── library.db operations ──────────────────────────────────────────────────────

def backup_library(library_db: Path) -> Path:
    ts  = datetime.now().strftime("%Y%m%d_%H%M%S")
    dst = library_db.with_suffix(f".db.bak_{ts}")
    shutil.copy2(str(library_db), str(dst))
    print(f"\n  §PROOF BACKUP  {dst.name}")
    return dst


def ensure_redirect_table(conn: sqlite3.Connection):
    conn.executescript(REDIRECT_DDL)
    conn.commit()


def compute_rotation_correction(dep_info: dict, can_info: dict):
    """Auto-compute rotation correction between deprecated and canonical hashes.

    Compares dominant rotation of deprecated elements vs canonical elements.
    The correction = canonical_dominant - deprecated_dominant (so that
    deprecated_rotation + correction ≈ canonical_rotation).

    Returns (rx_corr, ry_corr, rz_corr) or (0, 0, 0) if no mismatch.
    """
    dep_rot = dominant_rotation(dep_info)
    can_rot = dominant_rotation(can_info)
    rx = can_rot[0] - dep_rot[0]
    ry = can_rot[1] - dep_rot[1]
    rz = can_rot[2] - dep_rot[2]
    # Only report if any axis differs by > 0.1 rad (~6°)
    if abs(rx) < 0.1 and abs(ry) < 0.1 and abs(rz) < 0.1:
        return (0.0, 0.0, 0.0)
    return (round(rx, 6), round(ry, 6), round(rz, 6))


def write_redirects(library_db: Path, redirects: list, hash_info: dict, dry_run: bool):
    """
    redirects: list of (deprecated_hash, canonical_hash, sub_type)
    hash_info: scan results with rotation data for auto-correction
    """
    if not redirects:
        print("\n  Nothing to redirect.")
        return

    # Auto-compute rotation corrections
    corrections = {}  # deprecated_hash → (rx, ry, rz)
    for dep, can, st in redirects:
        dep_info = hash_info.get(dep)
        can_info = hash_info.get(can)
        if dep_info and can_info:
            corr = compute_rotation_correction(dep_info, can_info)
            corrections[dep] = corr

    print("\n  Planned redirects:")
    for dep, can, st in redirects:
        corr = corrections.get(dep, (0, 0, 0))
        corr_str = ""
        if any(c != 0 for c in corr):
            corr_str = f"  rot_correction=({corr[0]:.2f}, {corr[1]:.2f}, {corr[2]:.2f})"
        print(f"    {dep[:16]}  →  {can[:16]}  ({st}){corr_str}")

    if dry_run:
        print("\n  DRY-RUN: no changes written. Re-run with --confirm to commit.")
        return

    # confirm
    print()
    ans = input("  Type YES to commit to component_library.db: ").strip()
    if ans != "YES":
        print("  Aborted.")
        return

    backup_library(library_db)

    conn = sqlite3.connect(str(library_db))
    ensure_redirect_table(conn)

    # Ensure rotation correction columns exist
    existing_cols = {r[1] for r in conn.execute(
        "PRAGMA table_info(geometry_hash_redirect)").fetchall()}
    for col in ['rotation_x_correction', 'rotation_y_correction', 'rotation_z_correction']:
        if col not in existing_cols:
            conn.execute(f"ALTER TABLE geometry_hash_redirect ADD COLUMN {col} REAL DEFAULT 0.0")
    conn.commit()

    ts = datetime.now().isoformat(timespec="seconds")
    for dep, can, st in redirects:
        corr = corrections.get(dep, (0, 0, 0))
        conn.execute("""
            INSERT OR REPLACE INTO geometry_hash_redirect
                (deprecated_hash, canonical_hash, sub_type, created_at,
                 rotation_x_correction, rotation_y_correction, rotation_z_correction)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, (dep, can, st, ts, corr[0], corr[1], corr[2]))
        corr_str = ""
        if any(c != 0 for c in corr):
            corr_str = f"  rot=({corr[0]:.2f}, {corr[1]:.2f}, {corr[2]:.2f})"
        print(f"  §PROOF REDIRECT  {dep[:16]} → {can[:16]}  sub_type={st}{corr_str}")

    conn.commit()
    conn.close()
    print(f"\n  §PROOF COMMITTED  {len(redirects)} redirect(s) written to component_library.db")
    print_apply_note()


# ── list / remove ──────────────────────────────────────────────────────────────

def list_redirects(library_db: Path):
    if not library_db.exists():
        print("ERROR: component_library.db not found.")
        sys.exit(1)
    conn = sqlite3.connect(str(library_db))
    tables = {r[0] for r in conn.execute(
        "SELECT name FROM sqlite_master WHERE type='table'").fetchall()}
    if "geometry_hash_redirect" not in tables:
        print("No geometry_hash_redirect table — no redirects defined.")
        conn.close()
        return
    rows = conn.execute(
        "SELECT deprecated_hash, canonical_hash, sub_type, created_at "
        "FROM geometry_hash_redirect ORDER BY sub_type, created_at"
    ).fetchall()
    conn.close()
    if not rows:
        print("geometry_hash_redirect table exists but is empty.")
        return
    print(f"\n{'DEPRECATED':16}  {'CANONICAL':16}  {'SUB_TYPE':10}  CREATED_AT")
    print("─" * 70)
    for dep, can, st, ts in rows:
        print(f"{dep[:16]:16}  {can[:16]:16}  {(st or ''):10}  {ts}")
    print(f"\n{len(rows)} redirect(s) total.")


def remove_redirect(library_db: Path, deprecated_hash: str):
    conn = sqlite3.connect(str(library_db))
    cur = conn.execute(
        "DELETE FROM geometry_hash_redirect WHERE deprecated_hash = ?",
        (deprecated_hash,))
    conn.commit()
    conn.close()
    if cur.rowcount:
        print(f"Removed redirect for {deprecated_hash}.")
    else:
        print(f"No redirect found for {deprecated_hash}.")


def undo_redirects(library_db: Path, element_filter: str):
    """Remove ALL redirects whose sub_type matches element_filter scan results.

    Scans extracted DBs to collect every hash for the element type, then
    deletes any redirect row whose deprecated_hash is in that set.
    Uses the backup made at commit time as the hard fallback.
    """
    conn = sqlite3.connect(str(library_db))
    tables = {r[0] for r in conn.execute(
        "SELECT name FROM sqlite_master WHERE type='table'").fetchall()}
    if "geometry_hash_redirect" not in tables:
        print("No geometry_hash_redirect table — nothing to undo.")
        conn.close()
        return

    # collect all hashes for this element type across extracted DBs
    hash_info = scan_extracted_dbs(element_filter)
    if not hash_info:
        print(f"No elements matching '{element_filter}' found — nothing to undo.")
        conn.close()
        return

    hashes = list(hash_info.keys())
    placeholders = ",".join("?" * len(hashes))
    rows = conn.execute(
        f"SELECT deprecated_hash, canonical_hash, sub_type "
        f"FROM geometry_hash_redirect WHERE deprecated_hash IN ({placeholders})",
        hashes).fetchall()

    if not rows:
        print(f"No redirects found for '{element_filter}'.")
        conn.close()
        return

    print(f"\n  Redirects to remove for '{element_filter}':")
    for dep, can, st in rows:
        print(f"    {dep[:16]}  →  {can[:16]}  ({st})")

    ans = input("\n  Type YES to remove these redirects: ").strip()
    if ans != "YES":
        print("  Aborted.")
        conn.close()
        return

    backup_library(library_db)
    cur = conn.execute(
        f"DELETE FROM geometry_hash_redirect WHERE deprecated_hash IN ({placeholders})",
        hashes)
    conn.commit()
    conn.close()
    print(f"  §PROOF UNDO  {cur.rowcount} redirect(s) removed for '{element_filter}'")


# ── apply note ────────────────────────────────────────────────────────────────

def print_apply_note():
    print("""
  ── HOW TO APPLY in lod_manager.py ──────────────────────────────────────
  In fetch_geometry_blobs(), before the component_geometries lookup, add:

      # Resolve hash redirects (geometry_redirect.py)
      redirect_rows = conn.execute(
          f"SELECT deprecated_hash, canonical_hash FROM geometry_hash_redirect "
          f"WHERE deprecated_hash IN ({placeholders})", batch
      ).fetchall()
      redirect_map = {dep: can for dep, can in redirect_rows}
      batch = [redirect_map.get(h, h) for h in batch]

  Then re-run the loader — no re-bake of library.blend required.
  ─────────────────────────────────────────────────────────────────────────
""")


# ── main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Back-office geometry hash redirect tool.")
    parser.add_argument("--element-filter", default="sprinkler",
                        help="Element name pattern to scan (default: sprinkler)")
    parser.add_argument("--confirm", action="store_true",
                        help="Commit redirects to component_library.db (default: dry-run)")
    parser.add_argument("--list", action="store_true",
                        help="Show existing redirects and exit")
    parser.add_argument("--remove", metavar="DEPRECATED_HASH",
                        help="Remove a single redirect by deprecated hash")
    parser.add_argument("--undo", metavar="ELEMENT_FILTER",
                        help="Remove ALL redirects for an element filter (e.g. sprinkler)")
    args = parser.parse_args()

    if not LIBRARY_DB.exists():
        print(f"ERROR: {LIBRARY_DB} not found.")
        sys.exit(1)

    if args.list:
        list_redirects(LIBRARY_DB)
        return

    if args.remove:
        remove_redirect(LIBRARY_DB, args.remove)
        return

    if args.undo:
        undo_redirects(LIBRARY_DB, args.undo)
        return

    # ── scan ──
    hash_info = scan_extracted_dbs(args.element_filter)

    if not hash_info:
        print(f"No elements matching '{args.element_filter}' found in any extracted DB.")
        sys.exit(0)

    # group by sub-type, sort by vertex count desc within each group
    by_subtype = defaultdict(list)
    for ghash, info in hash_info.items():
        by_subtype[info["sub_type"]].append((ghash, info))
    for st in by_subtype:
        by_subtype[st].sort(key=lambda x: -x[1]["vertex_count"])

    print(f"\nFound {len(hash_info)} unique geometry hash(es) across "
          f"{len(by_subtype)} sub-type(s): {', '.join(sorted(by_subtype))}")

    # ── interactive selection ──
    redirects = []   # (deprecated_hash, canonical_hash, sub_type)

    for sub_type in sorted(by_subtype):
        candidates = by_subtype[sub_type]
        canonical  = pick_canonical(sub_type, candidates)
        if canonical is None:
            continue
        for ghash, _ in candidates:
            if ghash != canonical:
                redirects.append((ghash, canonical, sub_type))

    # ── write ──
    write_redirects(LIBRARY_DB, redirects, hash_info, dry_run=not args.confirm)


if __name__ == "__main__":
    main()
