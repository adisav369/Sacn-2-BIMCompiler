#!/usr/bin/env python3
"""
project_rule_joint_piece.py — project the catalog parts from disc_patterns.db `_import_joint_piece_types`
into a per-building-class `*_rules.db` as a first-class `rule_joint_piece` table (RESUME_DISC_WALKER_
ENVELOPE_BOUND.md roadmap #3 follow-up (a)). This makes the ASSEMBLE flow like routing/placement/shim:
`disc_walker.assemble()` reads the catalog part (type + measured Ø + length) per (disc, ifc_class) FROM
THE PROJECTION (the lean rules DB), not from a caller-passed `opts.catalog`.

KEY = (disc, ifc_class). The `disc` is taken from the rules DB's own `rule_routing` (the discipline that
actually walks/routes that ifc_class), NOT the catalog's own `discipline` label (which is a coarse
mining tag, e.g. all Duplex flow → 'CW'). For each (disc, ifc_class) the walker routes, the catalog
piece is the MEASURED median over that ifc_class's rows in the matching SOURCE building.

SOURCE filter: a rules DB is mined from ONE building (rules_meta.built_from); pass the matching
`source_like` (e.g. 'Terminal' or '%Duplex%') so the projected Ø is the one this building's network was
measured from — never cross-contaminated by another building's catalog rows for the same class.

NON-INVENT: every row is a real measurement (median diameter_mm / length_mm over real catalog pieces of
that ifc_class in the named source). A (disc, ifc_class) with NO catalog piece in the source → NO row
(honest; assemble then SKIPS that class — no fabricated part). The only derived value is the median.

IDEMPOTENT + ISOLATED: DROP+CREATE+INSERT `rule_joint_piece` ONLY — the mined rule tables (and rule_shim)
are untouched (re-run safe, no drift). Usage:
  project_rule_joint_piece.py <rules_db> <source_like>
"""
import os
import sqlite3
import statistics
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
# disc_patterns.db (the pattern store) — symlink to ERP.db today; new code names disc_patterns.
DISC_PATTERNS = os.path.join(HERE, "..", "library", "disc_patterns.db")


def project(rules_db, source_like, log=print):
    con = sqlite3.connect(rules_db)
    cur = con.cursor()

    # (disc, ifc_class) the walker ROUTES — both endpoints of every nn rule. These are exactly the nodes
    # assemble() instantiates a part at; only these need a catalog row.
    try:
        rr = cur.execute("SELECT DISTINCT disc, from_kind, to_kind FROM rule_routing").fetchall()
    except sqlite3.OperationalError:
        rr = []
    needed = set()
    for disc, fk, tk in rr:
        if fk:
            needed.add((disc, fk))
        if tk:
            needed.add((disc, tk))

    # MEASURED catalog pieces for each ifc_class, from the matching source building.
    pat = sqlite3.connect(DISC_PATTERNS)
    pcur = pat.cursor()

    def _pieces(ifc_class):
        return pcur.execute(
            "SELECT piece_type, diameter_mm, length_mm FROM _import_joint_piece_types "
            "WHERE ifc_class=? AND source_building LIKE ?",
            (ifc_class, source_like),
        ).fetchall()

    cur.executescript(
        """
        DROP TABLE IF EXISTS rule_joint_piece;
        CREATE TABLE rule_joint_piece(
            disc TEXT, ifc_class TEXT, piece_type TEXT,
            diameter_mm REAL, length_mm REAL, n_measured INTEGER,
            source_building TEXT, provenance TEXT);
        """
    )

    n_rows, skipped = 0, []
    for disc, ifc_class in sorted(needed):
        rows = _pieces(ifc_class)
        if not rows:
            skipped.append((disc, ifc_class))
            continue
        # representative piece_type = the most common; Ø/length = MEASURED median over real pieces.
        types = [r[0] for r in rows if r[0]]
        piece_type = statistics.mode(types) if types else None
        dia = statistics.median([r[1] for r in rows if r[1] is not None])
        lens = [r[2] for r in rows if r[2] is not None]
        length = statistics.median(lens) if lens else None
        cur.execute(
            "INSERT INTO rule_joint_piece VALUES (?,?,?,?,?,?,?,?)",
            (disc, ifc_class, piece_type, dia, length, len(rows), source_like,
             "projected:disc_patterns._import_joint_piece_types@" + str(source_like)),
        )
        n_rows += 1

    con.commit()
    con.close()
    pat.close()
    log(f"§JOINT-PROJ {os.path.basename(rules_db)} ← disc_patterns._import_joint_piece_types "
        f"(source LIKE '{source_like}'): {n_rows} rule_joint_piece rows over "
        f"{len({d for d, _ in needed})} disciplines; skipped (no catalog) = "
        f"{[d + '/' + c.replace('Ifc', '') for d, c in skipped] if skipped else 'none'}")
    return n_rows


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("usage: project_rule_joint_piece.py <rules_db> <source_like>", file=sys.stderr)
        sys.exit(2)
    project(sys.argv[1], sys.argv[2])
