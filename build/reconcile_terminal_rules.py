#!/usr/bin/env python3
"""
reconcile_terminal_rules.py — flow the MEASURED Terminal rule-mining payload
(build/terminal_rules.db) into the PRIOR-ART ERP.db vocabulary, per
RESUME_TERMINAL_RULE_MINING.md §PRIOR-ART RECONCILIATION.

Doctrine: DON'T fork a parallel schema. The mined rules are a measured
re-derivation of the existing anchor/offset/pattern/space-type vocabulary
(DV042 ad_placement_offset, W019 ad_mep_anchor/ad_mep_pattern). They flow into
the EXISTING tables tagged provenance/source 'Terminal' / 'measured:terminal*',
COMPLEMENTARY to the prior hand-curated code-standard rows — same tables, two
provenances.

Mapping (terminal_rules.db -> ERP.db):
  rule_routing      -> ad_mep_pattern   (FLOW IN; source_building='Terminal',
                       abstract node vocab SEGMENT/JUNCTION/VALVE/FIXTURE/...,
                       n_measured+params carried in notes)
  rule_placement    -> ad_placement_measured  (NEW measured sibling that ADOPTS
                       ad_placement_offset's z_rule/z_offset/x_ref/y_ref/standard
                       vocabulary and FKs the named rule where one applies;
                       grain differs (per disc/class/storey) so it cannot be a
                       PK row of the named-rule dictionary, but it REUSES the
                       dictionary's columns + references it = adoption, not fork)
  src_guids         -> ad_mep_anchor    (FLOW IN; each measured guid -> a real
                       anchor with XYZ RESOLVED from Terminal_meta.db, never
                       fabricated. anchor_id='TRM:'+guid so prior/pipeline rows
                       are untouched. These anchors ARE the provenance of an
                       offset rule, exactly as W019 intended.)
  rule_place_order  -> ad_place_order        (genuinely NEW; reconcile map says keep)
  rule_avoidance    -> ad_clash_avoidance    (genuinely NEW; the AD_Clash_Rule
                       cross-discipline clearance layer the SRS named)
  rule_space_bom    -> ad_space_bom_measured (CANNOT flow into ad_space_type_mep_bom
                       honestly: Terminal IfcSpace=0 (LANDMINE) -> no space_type to
                       key on. Kept as a measured per-scope count companion + the
                       gap is documented here, NON-INVENT.)

NON-INVENT: every emitted row traces to a terminal_rules.db row; every anchor
XYZ traces to a real Terminal element. Re-runnable: regenerates the SQL file.
"""
import os
import sqlite3
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
RULES_DB = os.path.join(HERE, "terminal_rules.db")
OUT_SQL = os.path.normpath(os.path.join(HERE, "..", "migration",
                                        "TRM001_terminal_measured_rules.sql"))

# Terminal meta DB (for anchor XYZ resolution). First hit wins; all are copies.
META_CANDIDATES = [
    os.path.expanduser("~/bim-ootb/buildings/Terminal_meta.db"),
    os.path.expanduser("~/bim-ootb/modeller/Terminal_meta.db"),
    os.path.expanduser("~/bim-ootb/buildings/Terminal_extracted.db"),
]


# ── reconciliation vocab maps (auditable, explicit — not class whitelists that
#    drive geometry; they only translate IFC class -> the prior-art node/anchor
#    token vocabulary already present in ERP.db) ────────────────────────────────
def anchor_type(ifc_class):
    """ad_mep_anchor.anchor_type CHECK is METER/FIXTURE/VALVE/GENERIC."""
    if ifc_class == "IfcValve":
        return "VALVE"
    if ifc_class in ("IfcAirTerminal", "IfcFireSuppressionTerminal",
                     "IfcLightFixture", "IfcAlarm", "IfcElectricAppliance"):
        return "FIXTURE"
    return "GENERIC"


def node_token(ifc_class):
    """Translate IFC class -> ad_mep_pattern node-type token. Reuses the
    existing vocabulary (JUNCTION already denotes fittings, FIXTURE terminals)."""
    return {
        "IfcPipeSegment": "SEGMENT", "IfcDuctSegment": "SEGMENT",
        "IfcPipeFitting": "JUNCTION", "IfcDuctFitting": "JUNCTION",
        "IfcValve": "VALVE",
        "IfcAirTerminal": "FIXTURE", "IfcFireSuppressionTerminal": "FIXTURE",
        "IfcColumn": "COLUMN", "IfcBeam": "BEAM", "IfcMember": "MEMBER",
    }.get(ifc_class, "GENERIC")


def direction_axis(pattern):
    return {"riser": "Z", "main": "XY", "nn": "NN",
            "array": "XY", "grid": "XY"}.get(pattern, "NN")


def piece_type(disc, pattern):
    if disc == "PLB":
        return "PIPE_STRAIGHT"
    if disc == "ACMV":
        return "DUCT_STRAIGHT"
    if disc == "FP" and pattern == "array":
        return "SPRINKLER_GRID"
    if disc == "STR":
        return "FRAME"
    return "GENERIC"


def named_placement_rule(ifc_class):
    """Reconcile a measured class onto an EXISTING ad_placement_offset named
    rule + its building-code standard, ONLY for the unambiguous cases the
    prior-art dictionary already covers (NFPA-13 sprinkler grid; ceiling light).
    NULL otherwise — never force a code reference that wasn't measured/standard."""
    return {
        "IfcFireSuppressionTerminal": ("CEILING_GRID", "NFPA 13 §8.5"),
        "IfcLightFixture": ("CEILING_CENTER", None),
    }.get(ifc_class, (None, None))


# ── SQL emit helpers ────────────────────────────────────────────────────────
def prov_tag(p):
    """Every reconciled row carries the doctrine provenance 'measured:terminal',
    preserving any measured detail (e.g. 'median_z=6.88', 'p05_clearance=...') as
    a suffix so the statistic that produced the rule is never lost."""
    p = (p or "").strip()
    if not p:
        return "measured:terminal"
    if p.startswith("measured:terminal"):
        return p
    return "measured:terminal;" + p


def q(v):
    if v is None:
        return "NULL"
    if isinstance(v, (int, float)):
        return repr(v)
    return "'" + str(v).replace("'", "''") + "'"


def main():
    if not os.path.exists(RULES_DB):
        sys.exit(f"FATAL: {RULES_DB} not found (run bake_terminal_rules.py first)")
    meta_path = next((p for p in META_CANDIDATES if os.path.exists(p)), None)
    if not meta_path:
        sys.exit("FATAL: Terminal_meta.db not found in any candidate path "
                 "(needed to resolve anchor XYZ; NON-INVENT requires it)")

    rules = sqlite3.connect(RULES_DB)
    rules.row_factory = sqlite3.Row
    meta = sqlite3.connect(meta_path)

    def resolve_guid(g):
        row = meta.execute(
            "SELECT t.center_x, t.center_y, t.center_z, m.storey, m.ifc_class "
            "FROM element_transforms t LEFT JOIN elements_meta m USING (guid) "
            "WHERE t.guid=?", (g,)).fetchone()
        return row  # (x,y,z,storey,ifc_class) or None

    out = []
    a = out.append
    a("-- ════════════════════════════════════════════════════════════════════")
    a("-- TRM001: MEASURED Terminal rules reconciled into prior-art ERP.db vocab")
    a("-- GENERATED by build/reconcile_terminal_rules.py — DO NOT HAND-EDIT.")
    a("-- Source: build/terminal_rules.db (mined+verified) + Terminal_meta.db (XYZ)")
    a("-- Implements RESUME_TERMINAL_RULE_MINING.md §PRIOR-ART RECONCILIATION.")
    a("-- Doctrine: flow measured rules INTO existing tables (provenance tagged),")
    a("-- don't fork a parallel schema. z_offset is FLOOR-relative (measured,")
    a("-- transfer-correct = what disc_walker.js uses); the placement_rule FK")
    a("-- carries the prior-art datum intent (e.g. CEILING_GRID). Two provenances.")
    a("-- ════════════════════════════════════════════════════════════════════")
    a("")

    # ── NEW measured-layer tables (idempotent) ─────────────────────────────
    a("-- Adopts ad_placement_offset (DV042) vocabulary: z_rule/z_offset/x_ref/")
    a("-- y_ref/standard + a FK to the named rule. Measured grain (disc/class/")
    a("-- storey) -> a sibling of the named-rule dictionary, not a fork of it.")
    a("""CREATE TABLE IF NOT EXISTS ad_placement_measured (
    ad_placement_measured_id INTEGER PRIMARY KEY AUTOINCREMENT,
    disc            TEXT NOT NULL,
    ifc_class       TEXT NOT NULL,
    ref_kind        TEXT,                 -- room | grid | host | storey | datum
    storey_scope    TEXT,
    from_edge_x     REAL DEFAULT 0,       -- measured dx (room/grid-relative)
    from_edge_y     REAL DEFAULT 0,       -- measured dy
    z_rule          TEXT DEFAULT 'FLOOR', -- adopted datum vocabulary
    z_offset        REAL DEFAULT 0,       -- measured dz, FLOOR-relative
    x_ref           TEXT DEFAULT 'CENTER',
    y_ref           TEXT DEFAULT 'CENTER',
    spacing_x_m     REAL DEFAULT 0,
    spacing_y_m     REAL DEFAULT 0,
    z_band_lo       REAL,
    z_band_hi       REAL,
    placement_rule  TEXT,                 -- FK -> ad_placement_offset.placement_rule
    standard        TEXT,                 -- copied from the FK'd named rule
    n_measured      INTEGER,
    src_storey_area_m2 REAL,             -- measured source-storey footprint (area-density bound)
    provenance      TEXT,
    src_guids       TEXT
);""")
    a("""CREATE TABLE IF NOT EXISTS ad_place_order (
    disc            TEXT NOT NULL,
    order_index     INTEGER NOT NULL,
    storey_scope    TEXT,
    z_band_lo       REAL,
    z_band_hi       REAL,
    n_measured      INTEGER,
    provenance      TEXT
);""")
    a("-- Cross-discipline clearance / yield = the AD_Clash_Rule layer (SRS §6.3).")
    a("""CREATE TABLE IF NOT EXISTS ad_clash_avoidance (
    disc_a          TEXT NOT NULL,
    disc_b          TEXT NOT NULL,
    min_clear_m     REAL,
    yields          TEXT,                 -- discipline that re-routes on conflict
    z_band          TEXT,
    n_measured      INTEGER,
    provenance      TEXT
);""")
    a("-- Measured routing sibling (symmetric to ad_placement_measured): retains the")
    a("-- precise IFC classes the modeller's disc_walker Router matches against the")
    a("-- target building. ad_mep_pattern keeps the node-token rows for the prior-art")
    a("-- RouteWalker; this keeps IFC classes for the disc_walker. One mine, two views.")
    a("""CREATE TABLE IF NOT EXISTS ad_routing_measured (
    disc            TEXT NOT NULL,
    from_ifc_class  TEXT NOT NULL,
    to_ifc_class    TEXT NOT NULL,
    pattern         TEXT,                 -- nn | main | riser | bend | array | grid
    params_json     TEXT,
    n_measured      INTEGER,
    provenance      TEXT,
    src_guids       TEXT
);""")
    a("-- Terminal has IfcSpace=0 (LANDMINE) -> cannot key into ad_space_type_mep_bom")
    a("-- by space_type without inventing one. Kept as a measured per-scope count.")
    a("""CREATE TABLE IF NOT EXISTS ad_space_bom_measured (
    disc            TEXT NOT NULL,
    scope           TEXT,
    ifc_class       TEXT,
    count_per       INTEGER,
    spacing_m       REAL,
    n_measured      INTEGER,
    provenance      TEXT,
    src_guids       TEXT
);""")
    a("")

    # ── idempotent purge of any prior reconcile output (re-run safe) ───────
    a("-- Re-run safe: purge prior measured:terminal rows before re-insert.")
    a("DELETE FROM ad_mep_anchor  WHERE anchor_id LIKE 'TRM:%';")
    a("DELETE FROM ad_mep_pattern WHERE source_building='Terminal' AND pattern_id LIKE 'TRM\\_%' ESCAPE '\\';")
    a("DELETE FROM ad_routing_measured;")
    a("DELETE FROM ad_placement_measured;")
    a("DELETE FROM ad_place_order;")
    a("DELETE FROM ad_clash_avoidance;")
    a("DELETE FROM ad_space_bom_measured;")
    a("")

    counts = dict(anchor=0, pattern=0, placement=0, order=0, clash=0, spacebom=0)
    unresolved = []
    seen_anchor = set()

    def emit_anchors(src_guids_csv):
        if not src_guids_csv:
            return
        for g in src_guids_csv.split(","):
            g = g.strip()
            if not g or g in seen_anchor:
                continue
            row = resolve_guid(g)
            if not row:
                unresolved.append(g)
                continue
            seen_anchor.add(g)
            x, y, z, storey, icls = row
            a("INSERT INTO ad_mep_anchor "
              "(anchor_id,source_building,anchor_type,x_m,y_m,z_m,storey,ifc_guid) "
              "VALUES (%s,'Terminal',%s,%s,%s,%s,%s,%s);" % (
                  q("TRM:" + g), q(anchor_type(icls)),
                  q(x), q(y), q(z), q(storey), q(g)))
            counts["anchor"] += 1

    # ── rule_routing -> ad_mep_pattern (FLOW IN) ───────────────────────────
    a("-- ── rule_routing -> ad_mep_pattern (existing W019 table) ──")
    seq_by_disc = {}
    for r in rules.execute("SELECT * FROM rule_routing ORDER BY disc, from_kind, to_kind"):
        disc = r["disc"]
        seq_by_disc[disc] = seq_by_disc.get(disc, 0) + 10
        pat_id = "TRM_%s_01" % disc
        notes = "n_measured=%s; %s; params=%s" % (
            r["n_measured"], r["provenance"] or "measured:terminal",
            (r["params_json"] or "")[:400])
        a("INSERT INTO ad_mep_pattern "
          "(pattern_id,discipline,building_type,sequence,from_node_type,"
          "to_node_type,direction_axis,piece_type,offset_rule,gradient,notes,"
          "source_building) VALUES (%s,%s,'TERMINAL',%s,%s,%s,%s,%s,%s,NULL,%s,'Terminal');" % (
              q(pat_id), q(disc), q(seq_by_disc[disc]),
              q(node_token(r["from_kind"])), q(node_token(r["to_kind"])),
              q(direction_axis(r["pattern"])), q(piece_type(disc, r["pattern"])),
              q(r["pattern"]), q(notes)))
        counts["pattern"] += 1
        # symmetric measured sibling — keeps the precise IFC classes for disc_walker
        a("INSERT INTO ad_routing_measured "
          "(disc,from_ifc_class,to_ifc_class,pattern,params_json,n_measured,"
          "provenance,src_guids) VALUES (%s,%s,%s,%s,%s,%s,%s,%s);" % (
              q(disc), q(r["from_kind"]), q(r["to_kind"]), q(r["pattern"]),
              q(r["params_json"]), q(r["n_measured"]),
              q(prov_tag(r["provenance"])), q(r["src_guids"])))
        counts["routing_m"] = counts.get("routing_m", 0) + 1
        emit_anchors(r["src_guids"])
    a("")

    # ── rule_placement -> ad_placement_measured (ADOPT vocab) ──────────────
    a("-- ── rule_placement -> ad_placement_measured (adopts DV042 vocabulary) ──")
    for r in rules.execute("SELECT * FROM rule_placement ORDER BY disc, ifc_class, storey_scope"):
        named, std = named_placement_rule(r["ifc_class"])
        a("INSERT INTO ad_placement_measured "
          "(disc,ifc_class,ref_kind,storey_scope,from_edge_x,from_edge_y,"
          "z_rule,z_offset,x_ref,y_ref,spacing_x_m,spacing_y_m,z_band_lo,"
          "z_band_hi,placement_rule,standard,n_measured,src_storey_area_m2,provenance,src_guids) "
          "VALUES (%s,%s,%s,%s,%s,%s,'FLOOR',%s,'CENTER','CENTER',%s,%s,%s,%s,%s,%s,%s,%s,%s,%s);" % (
              q(r["disc"]), q(r["ifc_class"]), q(r["ref_kind"]), q(r["storey_scope"]),
              q(r["dx"] or 0), q(r["dy"] or 0), q(r["dz"]),
              q(r["spacing_x_m"] or 0), q(r["spacing_y_m"] or 0),
              q(r["z_band_lo"]), q(r["z_band_hi"]),
              q(named), q(std), q(r["n_measured"]),
              q(r["src_storey_area_m2"] if "src_storey_area_m2" in r.keys() else None),
              q(prov_tag(r["provenance"])), q(r["src_guids"])))
        counts["placement"] += 1
        emit_anchors(r["src_guids"])
    a("")

    # ── rule_space_bom -> ad_space_bom_measured ────────────────────────────
    a("-- ── rule_space_bom -> ad_space_bom_measured (IfcSpace=0 gap, see header) ──")
    for r in rules.execute("SELECT * FROM rule_space_bom ORDER BY disc, ifc_class"):
        a("INSERT INTO ad_space_bom_measured "
          "(disc,scope,ifc_class,count_per,spacing_m,n_measured,provenance,src_guids) "
          "VALUES (%s,%s,%s,%s,%s,%s,%s,%s);" % (
              q(r["disc"]), q(r["scope"]), q(r["ifc_class"]),
              q(r["count_per"]), q(r["spacing_m"]), q(r["n_measured"]),
              q(prov_tag(r["provenance"])), q(r["src_guids"])))
        counts["spacebom"] += 1
        emit_anchors(r["src_guids"])
    a("")

    # ── rule_place_order -> ad_place_order (NEW) ───────────────────────────
    a("-- ── rule_place_order -> ad_place_order (NEW measured layer) ──")
    for r in rules.execute("SELECT * FROM rule_place_order ORDER BY storey_scope, order_index"):
        a("INSERT INTO ad_place_order "
          "(disc,order_index,storey_scope,z_band_lo,z_band_hi,n_measured,provenance) "
          "VALUES (%s,%s,%s,%s,%s,%s,%s);" % (
              q(r["disc"]), q(r["order_index"]), q(r["storey_scope"]),
              q(r["z_band_lo"]), q(r["z_band_hi"]), q(r["n_measured"]),
              q(prov_tag(r["provenance"]))))
        counts["order"] += 1
    a("")

    # ── rule_avoidance -> ad_clash_avoidance (NEW) ─────────────────────────
    a("-- ── rule_avoidance -> ad_clash_avoidance (NEW measured layer) ──")
    for r in rules.execute("SELECT * FROM rule_avoidance ORDER BY z_band, disc_a, disc_b"):
        a("INSERT INTO ad_clash_avoidance "
          "(disc_a,disc_b,min_clear_m,yields,z_band,n_measured,provenance) "
          "VALUES (%s,%s,%s,%s,%s,%s,%s);" % (
              q(r["disc_a"]), q(r["disc_b"]), q(r["min_clear_m"]),
              q(r["yields"]), q(r["z_band"]), q(r["n_measured"]),
              q(prov_tag(r["provenance"]))))
        counts["clash"] += 1
    a("")

    # ── COMPATIBILITY VIEWS — make ERP.db a DROP-IN source for the modeller's
    #    disc_walker.js engine (§CONVERGENCE: "drop-in, not a re-interpretation").
    #    The engine reads rule_placement/rule_routing/rule_place_order/
    #    rule_avoidance by exact name+columns; these views project the reconciled
    #    measured tables back to that contract (z_offset->dz, IFC classes intact).
    #    The SAME engine can dwOpen(ERP.db) and walk identically to terminal_rules.db.
    a("-- ── disc_walker compatibility views (ERP.db as drop-in rule source) ──")
    a("DROP VIEW IF EXISTS rule_placement;")
    a("""CREATE VIEW rule_placement AS
  SELECT disc, ifc_class, ref_kind, from_edge_x AS dx, from_edge_y AS dy,
         z_offset AS dz, spacing_x_m, spacing_y_m, z_band_lo, z_band_hi,
         storey_scope, n_measured, src_storey_area_m2, provenance, src_guids
  FROM ad_placement_measured;""")
    a("DROP VIEW IF EXISTS rule_routing;")
    a("""CREATE VIEW rule_routing AS
  SELECT disc, from_ifc_class AS from_kind, to_ifc_class AS to_kind, pattern,
         params_json, n_measured, provenance, src_guids
  FROM ad_routing_measured;""")
    a("DROP VIEW IF EXISTS rule_place_order;")
    a("""CREATE VIEW rule_place_order AS
  SELECT disc, order_index, storey_scope, z_band_lo, z_band_hi, n_measured, provenance
  FROM ad_place_order;""")
    a("DROP VIEW IF EXISTS rule_avoidance;")
    a("""CREATE VIEW rule_avoidance AS
  SELECT disc_a, disc_b, min_clear_m, yields, z_band, n_measured, provenance
  FROM ad_clash_avoidance;""")
    a("DROP VIEW IF EXISTS rule_space_bom;")
    a("""CREATE VIEW rule_space_bom AS
  SELECT disc, scope, ifc_class, count_per, spacing_m, n_measured, provenance, src_guids
  FROM ad_space_bom_measured;""")
    a("")

    if unresolved:
        a("-- WARNING: %d src_guid(s) did NOT resolve to a Terminal element and were"
          % len(unresolved))
        a("-- NOT emitted as anchors (NON-INVENT): " + ",".join(unresolved[:20]))

    with open(OUT_SQL, "w") as fh:
        fh.write("\n".join(out) + "\n")

    print("generated %s" % OUT_SQL)
    print("  meta source     = %s" % meta_path)
    for k in ("anchor", "pattern", "routing_m", "placement", "spacebom", "order", "clash"):
        print("  %-16s= %d" % (k, counts.get(k, 0)))
    print("  unresolved guids= %d" % len(unresolved))
    rules.close()
    meta.close()


if __name__ == "__main__":
    main()
