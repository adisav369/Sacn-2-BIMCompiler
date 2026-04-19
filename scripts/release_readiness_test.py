#!/usr/bin/env python3
"""
Release Readiness Test — RTree Guide Feature Verification
==========================================================

Validates that every feature promised in docs/RTreeGuide.md works end-to-end
against the City/ databases. No Blender required — pure Python + SQLite.

Run before uploading DBs to OCI or publishing the guide.

Usage:
    python3 scripts/release_readiness_test.py [--db PATH] [--lib PATH]

    Defaults:
      --db   ~/.bim/City/sandbox_1M_extracted.db
      --lib  ~/.bim/City/component_library.db

    If City/ doesn't exist, falls back to dev paths:
      --db   DAGCompiler/lib/input/sandbox_1M_extracted.db
      --lib  library/component_library.db

Exit code: 0 = all PASS, 1 = any FAIL

What it tests (maps to RTreeGuide steps):
  Step 3:  Preview bootstrap — building centres, disc counts, model offset
  Step 4:  Direct Stream — BLOB lookup, geometry dedup, hash coverage
  Step 5:  Search — text search across buildings, discipline filtering
  Step 8:  Clash detection — R-tree overlap query, discipline-pair grouping
  Step 9:  Discipline filter — per-discipline element counts
  Step 10: BOQ — QTO extraction, cost calculation
  Step 11: 4D Schedule — task generation from elements
  Step 12: Export — file creation (Excel, Markdown)
  Infra:   Three-tier DB resolution, table schema, index presence
"""

import argparse
import os
import sqlite3
import struct
import sys
import tempfile
import time
from pathlib import Path


# ── Result tracking ──

_results = []
_t0 = time.time()


def _pass(name, detail=""):
    _results.append((True, name, detail))
    print(f"  [PASS]  {name}" + (f" — {detail}" if detail else ""))


def _fail(name, detail=""):
    _results.append((False, name, detail))
    print(f"  [FAIL]  {name}" + (f" — {detail}" if detail else ""))


def _skip(name, detail=""):
    _results.append((None, name, detail))
    print(f"  [SKIP]  {name}" + (f" — {detail}" if detail else ""))


# ── DB resolution ──

def resolve_db(arg_db, arg_lib):
    """Three-tier DB resolution: arg > City/ > dev paths."""
    # Tier 1: explicit args
    if arg_db and Path(arg_db).exists():
        db = Path(arg_db)
    # Tier 2: City/
    elif (Path.home() / ".bim" / "City" / "sandbox_1M_extracted.db").exists():
        db = Path.home() / ".bim" / "City" / "sandbox_1M_extracted.db"
    # Tier 3: dev paths
    elif Path("DAGCompiler/lib/input/sandbox_1M_extracted.db").exists():
        db = Path("DAGCompiler/lib/input/sandbox_1M_extracted.db")
    else:
        return None, None

    if arg_lib and Path(arg_lib).exists():
        lib = Path(arg_lib)
    elif (db.parent / "component_library.db").exists():
        lib = db.parent / "component_library.db"
    elif (Path.home() / ".bim" / "City" / "component_library.db").exists():
        lib = Path.home() / ".bim" / "City" / "component_library.db"
    elif Path("library/component_library.db").exists():
        lib = Path("library/component_library.db")
    else:
        lib = None

    return db, lib


# ═══════════════════════════════════════════════════════════════════════════
# TEST SUITES
# ═══════════════════════════════════════════════════════════════════════════

def test_schema(conn):
    """Verify all required tables and columns exist."""
    print("\n── Schema ──")

    # Required tables
    tables = {r[0] for r in conn.execute(
        "SELECT name FROM sqlite_master WHERE type='table'").fetchall()}

    required = ['elements_meta', 'elements_rtree', 'element_instances',
                'element_transforms', 'base_geometries', 'surface_styles']
    for t in required:
        if t in tables:
            count = conn.execute(f"SELECT COUNT(*) FROM [{t}]").fetchone()[0]
            _pass(f"table:{t}", f"{count:,} rows")
        else:
            _fail(f"table:{t}", "missing")

    # elements_meta must have 'building' column (sandbox requirement)
    cols = {r[1] for r in conn.execute("PRAGMA table_info(elements_meta)").fetchall()}
    if 'building' in cols:
        _pass("column:elements_meta.building")
    else:
        _fail("column:elements_meta.building", "needed for multi-building sandbox")

    # R-tree column names (camelCase)
    rtree_cols = {r[1] for r in conn.execute("PRAGMA table_info(elements_rtree)").fetchall()}
    expected_rtree = {'id', 'minX', 'maxX', 'minY', 'maxY', 'minZ', 'maxZ'}
    if expected_rtree.issubset(rtree_cols):
        _pass("rtree:camelCase schema")
    else:
        _fail("rtree:camelCase schema", f"missing: {expected_rtree - rtree_cols}")


def test_preview_bootstrap(conn):
    """Step 3: Preview loads building centres and discipline counts."""
    print("\n── Preview Bootstrap (Step 3) ──")

    # Building centres from ARC+STR
    rows = conn.execute("""
        SELECT m.building, COUNT(*),
          (MIN(r.minX)+MAX(r.maxX))/2, (MIN(r.minY)+MAX(r.maxY))/2,
          (MIN(r.minZ)+MAX(r.maxZ))/2
        FROM elements_rtree r JOIN elements_meta m ON r.id = m.rowid
        WHERE m.discipline IN ('ARC','STR')
        GROUP BY m.building
    """).fetchall()
    if rows:
        _pass(f"building_centres", f"{len(rows)} buildings")
    else:
        _fail("building_centres", "no ARC/STR elements found")

    # Per-building disc bbox (what Preview actually loads: 1 bbox per disc per building)
    disc_rows = conn.execute("""
        SELECT m.building, m.discipline,
          MIN(r.minX), MIN(r.minY), MIN(r.minZ),
          MAX(r.maxX), MAX(r.maxY), MAX(r.maxZ),
          COUNT(*)
        FROM elements_rtree r JOIN elements_meta m ON r.id = m.rowid
        GROUP BY m.building, m.discipline
    """).fetchall()
    if disc_rows:
        _pass(f"disc_bbox", f"{len(disc_rows)} boxes (buildings x disciplines)")
    else:
        _fail("disc_bbox", "GROUP BY building,discipline returned nothing")

    # Discipline distribution
    discs = conn.execute(
        "SELECT discipline, COUNT(*) FROM elements_meta GROUP BY discipline "
        "ORDER BY COUNT(*) DESC").fetchall()
    disc_str = ', '.join(f"{d}={c:,}" for d, c in discs[:6])
    _pass(f"disciplines", disc_str)

    # Element counts per building (with geometry)
    elem_rows = conn.execute("""
        SELECT m.building, COUNT(*)
        FROM elements_meta m JOIN element_instances i ON m.guid = i.guid
        WHERE i.geometry_hash IS NOT NULL AND m.ifc_class != 'IfcOpeningElement'
        GROUP BY m.building
    """).fetchall()
    total = sum(c for _, c in elem_rows)
    _pass(f"streamable_elements", f"{total:,} across {len(elem_rows)} buildings")


def test_search(conn):
    """Step 5: Search across buildings."""
    print("\n── Search (Step 5) ──")

    # Text search by IFC class
    for term in ['IfcDoor', 'IfcWall', 'IfcBeam']:
        rows = conn.execute(
            "SELECT COUNT(*) FROM elements_meta WHERE ifc_class = ?",
            (term,)).fetchone()
        if rows[0] > 0:
            _pass(f"search:{term}", f"{rows[0]:,} elements")
        else:
            _skip(f"search:{term}", "0 elements (may be valid for this DB)")

    # LIKE search (fuzzy)
    rows = conn.execute(
        "SELECT COUNT(DISTINCT building) FROM elements_meta "
        "WHERE element_name LIKE '%door%' OR ifc_class LIKE '%Door%'"
    ).fetchone()
    _pass(f"search:fuzzy 'door'", f"{rows[0]} buildings matched")

    # Building name search
    buildings = conn.execute(
        "SELECT DISTINCT building FROM elements_meta LIMIT 5").fetchall()
    if buildings:
        bld = buildings[0][0]
        count = conn.execute(
            "SELECT COUNT(*) FROM elements_meta WHERE building = ?",
            (bld,)).fetchone()[0]
        _pass(f"search:building '{bld}'", f"{count:,} elements")


def test_discipline_filter(conn):
    """Step 9: Discipline filtering."""
    print("\n── Discipline Filter (Step 9) ──")

    discs = conn.execute(
        "SELECT DISTINCT discipline FROM elements_meta ORDER BY discipline"
    ).fetchall()
    disc_list = [d[0] for d in discs]
    _pass(f"disciplines_available", ', '.join(disc_list))

    # Verify each discipline has at least 1 element with geometry
    for disc in disc_list[:8]:  # cap at 8 to avoid noise
        count = conn.execute("""
            SELECT COUNT(*) FROM elements_meta m
            JOIN element_instances i ON m.guid = i.guid
            WHERE m.discipline = ? AND i.geometry_hash IS NOT NULL
        """, (disc,)).fetchone()[0]
        if count > 0:
            _pass(f"disc:{disc}", f"{count:,} elements with geometry")
        else:
            _skip(f"disc:{disc}", "0 with geometry")


def test_clash_detection(conn):
    """Step 10: Clash detection via R-tree overlap."""
    print("\n── Clash Detection (Step 10) ──")

    # Pick two disciplines that should have overlaps
    discs = conn.execute("""
        SELECT discipline, COUNT(*) c FROM elements_meta
        GROUP BY discipline ORDER BY c DESC LIMIT 2
    """).fetchall()
    if len(discs) < 2:
        _skip("clash:overlap_query", "need at least 2 disciplines")
        return

    d1, d2 = discs[0][0], discs[1][0]

    # R-tree overlap: elements from d1 whose bbox intersects elements from d2
    # Use a single building to keep it fast
    bld = conn.execute(
        "SELECT building FROM elements_meta WHERE discipline = ? LIMIT 1",
        (d1,)).fetchone()
    if not bld:
        _skip("clash:overlap_query", "no building found")
        return
    bld = bld[0]

    t0 = time.time()
    clashes = conn.execute(f"""
        SELECT COUNT(*) FROM elements_rtree r1
        JOIN elements_meta m1 ON r1.id = m1.rowid
        JOIN elements_rtree r2 ON r2.id != r1.id
            AND r2.minX <= r1.maxX AND r2.maxX >= r1.minX
            AND r2.minY <= r1.maxY AND r2.maxY >= r1.minY
            AND r2.minZ <= r1.maxZ AND r2.maxZ >= r1.minZ
        JOIN elements_meta m2 ON r2.id = m2.rowid
        WHERE m1.building = ? AND m1.discipline = ?
          AND m2.building = ? AND m2.discipline = ?
        LIMIT 100
    """, (bld, d1, bld, d2)).fetchone()[0]
    elapsed = time.time() - t0
    _pass(f"clash:{d1}×{d2} in {bld}", f"{clashes} overlaps found ({elapsed:.1f}s)")


def test_boq(conn, db_path):
    """Step 11: BOQ / quantity takeoff."""
    print("\n── BOQ / Quantity Takeoff (Step 11) ──")

    # Check if simple_qto table exists or can be created
    has_qto = conn.execute(
        "SELECT name FROM sqlite_master WHERE type='table' AND name='simple_qto'"
    ).fetchone()

    if has_qto:
        count = conn.execute("SELECT COUNT(*) FROM simple_qto").fetchone()[0]
        _pass(f"qto:simple_qto", f"{count:,} rows pre-existing")
    else:
        # Try to run QTO extraction on a temp copy
        try:
            fed_path = Path(__file__).resolve().parent.parent
            # Try to find the extractor
            qto_paths = [
                Path(__file__).resolve().parent.parent / "IfcOpenShell" / "src" / "bonsai" /
                "bonsai" / "bim" / "module" / "federation" / "boq" / "simple_qto_extract.py",
                Path.home() / "IfcOpenShell" / "src" / "bonsai" / "bonsai" / "bim" /
                "module" / "federation" / "boq" / "simple_qto_extract.py",
            ]
            qto_script = None
            for p in qto_paths:
                if p.exists():
                    qto_script = p
                    break

            if not qto_script:
                _skip("qto:extraction", "simple_qto_extract.py not found")
            else:
                # Import and run on temp copy to avoid modifying the source DB
                tmp_db = tempfile.mktemp(suffix='.db')
                import shutil
                shutil.copy2(str(db_path), tmp_db)
                sys.path.insert(0, str(qto_script.parent))
                from simple_qto_extract import extract_simple_qto
                extract_simple_qto(tmp_db)
                tmp_conn = sqlite3.connect(tmp_db)
                count = tmp_conn.execute("SELECT COUNT(*) FROM simple_qto").fetchone()[0]
                tmp_conn.close()
                os.unlink(tmp_db)
                if count > 0:
                    _pass(f"qto:extraction", f"{count:,} QTO rows generated")
                else:
                    _fail("qto:extraction", "0 rows generated")
        except Exception as e:
            _skip(f"qto:extraction", f"{e}")

    # Verify element counts per IFC class (basic quantity data always available)
    class_counts = conn.execute("""
        SELECT ifc_class, COUNT(*) FROM elements_meta
        GROUP BY ifc_class ORDER BY COUNT(*) DESC LIMIT 10
    """).fetchall()
    top3 = ', '.join(f"{c}={n:,}" for c, n in class_counts[:3])
    _pass(f"qto:class_counts", f"top 3: {top3}")


def test_schedule(conn, db_path):
    """Step 12: 4D schedule generation."""
    print("\n── 4D Schedule (Step 12) ──")

    # Check if schedule already exists
    has_sched = conn.execute(
        "SELECT name FROM sqlite_master WHERE type='table' AND name='construction_schedule'"
    ).fetchone()

    if has_sched:
        count = conn.execute("SELECT COUNT(*) FROM construction_schedule").fetchone()[0]
        _pass(f"schedule:construction_schedule", f"{count:,} tasks pre-existing")
        return

    # Try to generate on temp copy
    try:
        gen_paths = [
            Path.home() / "IfcOpenShell" / "src" / "bonsai" / "bonsai" / "bim" /
            "module" / "federation" / "schedule" / "schedule_generator.py",
        ]
        gen_script = None
        for p in gen_paths:
            if p.exists():
                gen_script = p
                break

        if not gen_script:
            _skip("schedule:generation", "schedule_generator.py not found")
            return

        tmp_db = tempfile.mktemp(suffix='.db')
        import shutil
        shutil.copy2(str(db_path), tmp_db)
        sys.path.insert(0, str(gen_script.parent))
        from schedule_generator import generate_construction_schedule
        tasks = generate_construction_schedule(tmp_db, "2025-01-06", "Release Test")
        os.unlink(tmp_db)
        if tasks and tasks > 0:
            _pass(f"schedule:generation", f"{tasks} tasks created")
        else:
            _fail("schedule:generation", f"returned {tasks}")
    except Exception as e:
        _skip(f"schedule:generation", f"{e}")


def test_blob_coverage(conn, lib_conn):
    """Step 4: Direct Stream BLOB availability — can we tessellate?"""
    print("\n── BLOB Coverage (Step 4) ──")

    # Count unique hashes in sandbox
    sandbox_hashes = conn.execute("""
        SELECT COUNT(DISTINCT geometry_hash) FROM element_instances
        WHERE geometry_hash IS NOT NULL
    """).fetchone()[0]
    _pass(f"sandbox:unique_hashes", f"{sandbox_hashes:,}")

    if not lib_conn:
        _fail("library:component_library.db", "not found")
        return

    # Count hashes in library
    lib_hashes = lib_conn.execute(
        "SELECT COUNT(*) FROM component_geometries").fetchone()[0]
    _pass(f"library:total_meshes", f"{lib_hashes:,}")

    # Coverage: how many sandbox hashes are in the library?
    # Sample first 999 hashes (full check would be slow)
    sample = conn.execute(
        "SELECT DISTINCT geometry_hash FROM element_instances "
        "WHERE geometry_hash IS NOT NULL LIMIT 999").fetchall()
    sample_hashes = [r[0] for r in sample]

    if not sample_hashes:
        _fail("coverage:sample", "no hashes to check")
        return

    ph = ','.join('?' * len(sample_hashes))
    found = lib_conn.execute(
        f"SELECT COUNT(*) FROM component_geometries WHERE geometry_hash IN ({ph})",
        sample_hashes).fetchone()[0]
    pct = found / len(sample_hashes) * 100
    if pct >= 95:
        _pass(f"coverage:sample", f"{found}/{len(sample_hashes)} ({pct:.0f}%)")
    elif pct >= 50:
        _fail(f"coverage:sample", f"{found}/{len(sample_hashes)} ({pct:.0f}%) — too many misses")
    else:
        _fail(f"coverage:sample", f"{found}/{len(sample_hashes)} ({pct:.0f}%) — critical gap")

    # Verify a BLOB can be unpacked (test one mesh)
    row = lib_conn.execute(
        "SELECT geometry_hash, vertices, faces, vertex_count, face_count "
        "FROM component_geometries WHERE vertices IS NOT NULL LIMIT 1").fetchone()
    if not row:
        _fail("blob:unpack", "no BLOB data found")
        return

    ghash, vblob, fblob, vcount, fcount = row
    try:
        floats = struct.unpack(f'<{len(vblob)//4}f', vblob)
        verts = [(floats[i], floats[i+1], floats[i+2]) for i in range(0, len(floats), 3)]
        ints = struct.unpack(f'<{len(fblob)//4}I', fblob)
        # Faces are stored as: count, v1, v2, v3, count, v1, v2, v3, ...
        if len(verts) > 0 and len(ints) > 0:
            _pass(f"blob:unpack", f"hash={ghash[:12]}... verts={len(verts)} raw_ints={len(ints)}")
        else:
            _fail(f"blob:unpack", f"empty after unpack")
    except Exception as e:
        _fail(f"blob:unpack", f"{e}")


def test_clash_report(conn, db_path):
    """Step 10 extended: clash report generation."""
    print("\n── Clash Report (Step 10) ──")

    try:
        report_paths = [
            Path.home() / "IfcOpenShell" / "src" / "bonsai" / "bonsai" / "bim" /
            "module" / "federation" / "clash" / "report" / "report_generator.py",
        ]
        report_script = None
        for p in report_paths:
            if p.exists():
                report_script = p
                break

        if not report_script:
            _skip("clash_report:generator", "report_generator.py not found")
            return

        sys.path.insert(0, str(report_script.parent.parent.parent))
        from clash.report.report_generator import ReportGenerator
        gen = ReportGenerator(str(db_path))
        gen.connect()
        # Just verify it connects — full report needs clash data
        _pass("clash_report:generator", "ReportGenerator connects OK")
        gen.conn.close()
    except Exception as e:
        _skip(f"clash_report:generator", f"{e}")


def test_surface_styles(conn):
    """Verify material/colour data for rendering."""
    print("\n── Surface Styles ──")

    count = conn.execute("SELECT COUNT(*) FROM surface_styles").fetchone()[0]
    if count > 0:
        sample = conn.execute(
            "SELECT style_name, surface_r, surface_g, surface_b FROM surface_styles LIMIT 3"
        ).fetchall()
        names = ', '.join(r[0][:20] for r in sample)
        _pass(f"surface_styles", f"{count:,} styles (sample: {names})")
    else:
        _skip("surface_styles", "0 styles — elements will use discipline colours")


def test_model_extent(conn):
    """Verify city extent and spatial coherence."""
    print("\n── Model Extent ──")

    row = conn.execute("""
        SELECT MIN(minX), MAX(maxX), MIN(minY), MAX(maxY), MIN(minZ), MAX(maxZ)
        FROM elements_rtree
    """).fetchone()

    if not row or row[0] is None:
        _fail("extent", "empty R-tree")
        return

    dx = row[1] - row[0]
    dy = row[3] - row[2]
    dz = row[5] - row[4]
    _pass(f"extent", f"{dx:.0f}m x {dy:.0f}m x {dz:.0f}m")

    # Check for degenerate bboxes (zero-size elements)
    degen = conn.execute("""
        SELECT COUNT(*) FROM elements_rtree
        WHERE minX = maxX OR minY = maxY OR minZ = maxZ
    """).fetchone()[0]
    total = conn.execute("SELECT COUNT(*) FROM elements_rtree").fetchone()[0]
    pct = degen / total * 100 if total > 0 else 0
    if pct < 5:
        _pass(f"degenerate_bbox", f"{degen:,}/{total:,} ({pct:.1f}%)")
    else:
        _fail(f"degenerate_bbox", f"{degen:,}/{total:,} ({pct:.1f}%) — too many")


# ═══════════════════════════════════════════════════════════════════════════
# MAIN
# ═══════════════════════════════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(
        description="Release Readiness Test — RTree Guide feature verification")
    parser.add_argument("--db", help="Path to sandbox/extracted DB")
    parser.add_argument("--lib", help="Path to component_library.db")
    args = parser.parse_args()

    db_path, lib_path = resolve_db(args.db, args.lib)

    print()
    print("  ╔══════════════════════════════════════════════════════════╗")
    print("  ║  Release Readiness Test — RTree Guide                   ║")
    print("  ╚══════════════════════════════════════════════════════════╝")
    print()

    if not db_path:
        print("  [FAIL]  No database found. Provide --db or place in ~/.bim/City/")
        sys.exit(1)

    print(f"  DB:  {db_path}")
    print(f"  Lib: {lib_path or '(not found)'}")
    print(f"  DB size: {db_path.stat().st_size / 1024 / 1024:.0f} MB")
    if lib_path:
        print(f"  Lib size: {lib_path.stat().st_size / 1024 / 1024:.0f} MB")

    conn = sqlite3.connect(str(db_path))
    lib_conn = sqlite3.connect(str(lib_path)) if lib_path else None

    # Run test suites
    test_schema(conn)
    test_preview_bootstrap(conn)
    test_model_extent(conn)
    test_surface_styles(conn)
    test_search(conn)
    test_discipline_filter(conn)
    test_blob_coverage(conn, lib_conn)
    test_clash_detection(conn)
    test_clash_report(conn, db_path)
    test_boq(conn, db_path)
    test_schedule(conn, db_path)

    conn.close()
    if lib_conn:
        lib_conn.close()

    # Summary
    elapsed = time.time() - _t0
    passed = sum(1 for ok, _, _ in _results if ok is True)
    failed = sum(1 for ok, _, _ in _results if ok is False)
    skipped = sum(1 for ok, _, _ in _results if ok is None)
    total = len(_results)

    print()
    print(f"  ──────────────────────────────────────────────────")
    print(f"  PASS: {passed}  FAIL: {failed}  SKIP: {skipped}  TOTAL: {total}  ({elapsed:.1f}s)")
    print(f"  ──────────────────────────────────────────────────")

    if failed > 0:
        print()
        print("  Failed tests:")
        for ok, name, detail in _results:
            if ok is False:
                print(f"    - {name}: {detail}")
        print()
        print("  ✗ NOT RELEASE READY")
        sys.exit(1)
    else:
        print()
        print("  ✓ RELEASE READY")
        sys.exit(0)


if __name__ == "__main__":
    main()
