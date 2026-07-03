#!/usr/bin/env python3
"""
project_rule_connector.py — project the FIXTURE→SERVICE hookup from disc_patterns.db
`ad_assembly_connector` (+ `ad_assembly_manifest` standoff) into a per-building-class `*_rules.db` as a
first-class `rule_connector` table (RESUME_DISC_WALKER_ENVELOPE_BOUND.md §3c / roadmap #3b follow-up).
This makes the CONNECTOR flow like routing/placement/shim/joint_piece: `disc_walker.connectorEnrich()`
reads the hookup per (disc, ifc_class) FROM THE PROJECTION (the lean rules DB), so the modeller — which
carries only the `*_rules.db`, never `disc_patterns.db` — can render connector edges with NO caller percept.

KEY = (disc, ifc_class), keyed off the (disc, ifc_class) the rules DB actually WALKS (rule_placement ∪
rule_routing endpoints). The ifc_class → assembly_id resolution is via disc_patterns `ad_element_mep`
(ifc_class → element_type) ∪ `ad_element_mep_alias` (ifc_class → canonical_type); the resolved name is kept
ONLY when it is an EXACT `assembly_id` in `ad_assembly_connector` that declares a SERVICE connector
(`connects_to` non-null) — the hookup that orients the part. DECISIVE-ONLY: if an ifc_class resolves to
0 or >1 such assemblies the row is SKIPPED (honest; connectorEnrich then leaves that fixture untouched).

NON-INVENT: face / connector_type / Ø / connects_to are READ VERBATIM from ad_assembly_connector; standoff
is the matching ad_assembly_manifest.clearance_m for that (assembly, face) (0 = flush, the common case).
Nothing is fabricated; an absent mapping yields NO row.

IDEMPOTENT + ISOLATED: DROP+CREATE+INSERT `rule_connector` ONLY — the mined rule tables (rule_avoidance,
rule_placement, rule_routing, rule_shim, rule_joint_piece) are untouched (re-run safe, no bake drift). Usage:
  project_rule_connector.py <rules_db>
"""
import os
import sqlite3
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
# disc_patterns.db (the pattern store) — symlink to ERP.db today; new code names disc_patterns.
DISC_PATTERNS = os.path.join(HERE, "..", "library", "disc_patterns.db")


def project(rules_db, log=print):
    con = sqlite3.connect(rules_db)
    cur = con.cursor()

    # (disc, ifc_class) the walker WALKS — placements (fixtures connectorEnrich enriches) ∪ routing
    # endpoints (nodes assemble instantiates parts at). Only these can carry a hookup.
    walked = set()
    try:
        for d, c in cur.execute("SELECT DISTINCT disc, ifc_class FROM rule_placement").fetchall():
            if d and c:
                walked.add((d, c))
    except sqlite3.OperationalError:
        pass
    try:
        for d, fk, tk in cur.execute("SELECT DISTINCT disc, from_kind, to_kind FROM rule_routing").fetchall():
            if d and fk:
                walked.add((d, fk))
            if d and tk:
                walked.add((d, tk))
    except sqlite3.OperationalError:
        pass

    pat = sqlite3.connect(DISC_PATTERNS)
    pcur = pat.cursor()

    # ifc_class → candidate assembly names (element_type via ad_element_mep ∪ canonical via alias).
    def _assemblies_for(ifc_class):
        names = set()
        for (v,) in pcur.execute("SELECT Value FROM ad_element_mep WHERE ifc_class=?", (ifc_class,)).fetchall():
            if v:
                names.add(v)
        for (v,) in pcur.execute(
            "SELECT canonical_type FROM ad_element_mep_alias WHERE match_field='ifc_class' AND match_value=?",
            (ifc_class,),
        ).fetchall():
            if v:
                names.add(v)
        return names

    # an assembly's SERVICE connector (connects_to non-null) — the hookup that orients the part.
    def _service_connector(assembly_id):
        rows = pcur.execute(
            "SELECT face, connector_type, diameter_mm, connects_to FROM ad_assembly_connector "
            "WHERE assembly_id=? AND connects_to IS NOT NULL AND connects_to<>''",
            (assembly_id,),
        ).fetchall()
        return rows

    def _standoff(assembly_id, face):
        r = pcur.execute(
            "SELECT clearance_m FROM ad_assembly_manifest WHERE assembly_id=? AND face=?",
            (assembly_id, face),
        ).fetchone()
        return float(r[0]) if r and r[0] is not None else 0.0

    cur.executescript(
        """
        DROP TABLE IF EXISTS rule_connector;
        CREATE TABLE rule_connector(
            disc TEXT, ifc_class TEXT, assembly_id TEXT,
            face TEXT, connector_type TEXT, diameter_mm REAL,
            connects_to TEXT, standoff_m REAL, provenance TEXT);
        """
    )

    n_rows, skipped = 0, []
    for disc, ifc_class in sorted(walked):
        names = _assemblies_for(ifc_class)
        # DECISIVE: exactly one candidate assembly that declares a SERVICE connector.
        hits = [(a, _service_connector(a)) for a in sorted(names)]
        hits = [(a, c) for a, c in hits if c]
        if len(hits) != 1:
            skipped.append((disc, ifc_class, "%d assemblies w/ service connector" % len(hits)))
            continue
        assembly_id, conns = hits[0]
        # the orienting connector = the (first) service connector; multiple services on one face → first.
        face, connector_type, dia, connects_to = conns[0]
        standoff = _standoff(assembly_id, face)
        cur.execute(
            "INSERT INTO rule_connector VALUES (?,?,?,?,?,?,?,?,?)",
            (disc, ifc_class, assembly_id, face, connector_type, dia, connects_to, standoff,
             "projected:ad_assembly_connector+manifest@" + assembly_id),
        )
        n_rows += 1

    con.commit()
    con.close()
    pat.close()
    log(f"§CONN-PROJ {os.path.basename(rules_db)} ← disc_patterns.ad_assembly_connector/manifest: "
        f"{n_rows} rule_connector rows; skipped (no decisive assembly) = "
        f"{[d + '/' + c.replace('Ifc', '') + ':' + why for d, c, why in skipped] if skipped else 'none'}")
    return n_rows


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("usage: project_rule_connector.py <rules_db>", file=sys.stderr)
        sys.exit(2)
    project(sys.argv[1])
