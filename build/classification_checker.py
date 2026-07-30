#!/usr/bin/env python3
"""§CLASSIFY_CHECK — the pluggable asset-classification checker (MECHANISM ONLY).

Spec: prompts/JKR_SKATA_COMPLIANCE_LANE.md §PHASE-C (2026-07-30).
User directive: "keep the code format pluggable via its locale json settings."

WHAT IT PROVES/DISPROVES
  "Can we SAY whether a dataset is compliant?" — for any locale whose scheme is REAL, yes: this
  reports coded/uncoded/invalid as numbers per building. For a locale whose scheme is an EXAMPLE
  placeholder (SKATA today), it REFUSES to emit a verdict and says so. A checker that returns a
  confident pass/fail against a made-up format is worse than no checker.

WHAT IT NEVER DOES
  It never derives, guesses or repairs a code. It reads elements_meta.classification_code, which
  extractIFCtoDB.py §CLASSIFY recovered verbatim from IfcRelAssociatesClassification. Absent is NULL,
  NULL is COUNTED. Nothing is inferred from element_name — that is the exact failure mode this lane
  exists to avoid (see the glazed-facade name-rule blind spot, RESUME_4D §STAGE A A.3).

CONFIG (all data, no code changes to add a jurisdiction)
  config/classification/locales.json          — locale -> scheme(s) + level-naming conventions
  config/classification/schemes/<id>.json     — one descriptor per scheme

USAGE
  python3 build/classification_checker.py --db <building.db> [--locale ms-MY] [--scheme uniformat]
  python3 build/classification_checker.py --db a.db --db b.db          # several buildings

Read the §-log lines. Exit code alone is not evidence.
"""
import argparse
import json
import os
import re
import sqlite3
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CFG = os.path.join(ROOT, "config", "classification")


def load_cfg():
    with open(os.path.join(CFG, "locales.json")) as f:
        loc = json.load(f)
    schemes = {}
    sdir = os.path.join(CFG, "schemes")
    for fn in sorted(os.listdir(sdir)):
        if fn.endswith(".json"):
            with open(os.path.join(sdir, fn)) as f:
                s = json.load(f)
            schemes[s["id"]] = s
    return loc, schemes


def table_exists(c, name):
    return c.execute("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
                     (name,)).fetchone()[0] > 0


def col_exists(c, table, col):
    return col in {r[1] for r in c.execute("PRAGMA table_info(%s)" % table)}


def resolve_locale(c, loc_cfg, schemes, cli_locale, cli_scheme):
    """1 CLI  2 project_metadata.locale  3 detect by classification_system  4 default."""
    if cli_scheme:
        return None, cli_scheme, "cli --scheme"
    if cli_locale:
        return cli_locale, loc_cfg["locales"][cli_locale]["primary"], "cli --locale"
    if table_exists(c, "project_metadata"):
        row = c.execute("SELECT value FROM project_metadata WHERE key='locale'").fetchone()
        if row and row[0] in loc_cfg["locales"]:
            return row[0], loc_cfg["locales"][row[0]]["primary"], "project_metadata.locale"
    if table_exists(c, "elements_meta") and col_exists(c, "elements_meta", "classification_system"):
        systems = [r[0] for r in c.execute(
            "SELECT DISTINCT classification_system FROM elements_meta "
            "WHERE classification_system IS NOT NULL") if r[0]]
        for sid, s in schemes.items():
            names = [n.lower() for n in s.get("detect", {}).get("ifc_classification_name", [])]
            for sysname in systems:
                if sysname.lower() in names:
                    for lid, l in loc_cfg["locales"].items():
                        if l.get("primary") == sid:
                            return lid, sid, "detect(classification_system=%s)" % sysname
                    return None, sid, "detect(classification_system=%s)" % sysname
    d = loc_cfg["default"]
    return d, loc_cfg["locales"][d]["primary"], "default"


def check_codes(c, scheme):
    """Coverage + shape validity. Returns a dict of NUMBERS; nothing is repaired."""
    total = c.execute("SELECT COUNT(*) FROM elements_meta").fetchone()[0]
    if not col_exists(c, "elements_meta", "classification_code"):
        return {"schema": False, "total": total}
    rows = c.execute(
        "SELECT classification_code, COUNT(*) FROM elements_meta "
        "WHERE classification_code IS NOT NULL GROUP BY 1").fetchall()
    coded = sum(n for _, n in rows)
    pat = re.compile(scheme["code"]["pattern"])
    lens = set(scheme["code"].get("permitted_lengths") or [])
    ok = bad = badlen = 0
    bad_ex = []
    for code, n in rows:
        good = bool(pat.match(code))
        if lens and len(code) not in lens:
            badlen += n
            good = False
        if good:
            ok += n
        else:
            bad += n
            if len(bad_ex) < 5:
                bad_ex.append("%s (n=%d, len=%d)" % (code, n, len(code)))
    # segment-facet occupancy: which declared facets this scheme can actually fill today
    facets = {k: v.get("applies") for k, v in scheme.get("facets", {}).items()}
    return {"schema": True, "total": total, "coded": coded, "uncoded": total - coded,
            "distinct": len(rows), "valid": ok, "invalid": bad, "badlen": badlen,
            "bad_examples": bad_ex, "facets": facets}


def check_levels(c, rules):
    """Classify the STOREY NAMING CONVENTION in use. Never maps a name to a code."""
    if not table_exists(c, "elements_meta"):
        return None
    storeys = c.execute(
        "SELECT COALESCE(storey,''), COUNT(*) FROM elements_meta GROUP BY 1 ORDER BY 1").fetchall()
    unknown_tok = {t.lower() for t in rules.get("unknown_tokens", [])}
    convs = rules.get("conventions", [])
    hits = {cv["id"]: 0 for cv in convs}
    unmatched = unknown = 0
    prefix_map = {}
    for name, n in storeys:
        if name.strip().lower() in unknown_tok:
            unknown += n
            continue
        for cv in convs:
            m = re.match(cv["pattern"], name)
            if m:
                hits[cv["id"]] += n
                pg = cv.get("prefix_group")
                if pg:
                    prefix_map.setdefault(m.group(pg), set()).add(name)
                break
        else:
            unmatched += n
    collisions = {k: sorted(v) for k, v in prefix_map.items() if len(v) > 1}
    return {"distinct_storeys": len(storeys), "hits": hits, "unknown": unknown,
            "unmatched": unmatched, "in_use": sum(1 for v in hits.values() if v),
            "collisions": collisions}


def main():
    ap = argparse.ArgumentParser(description="§CLASSIFY_CHECK — pluggable classification checker")
    ap.add_argument("--db", action="append", required=True, help="building DB (repeatable)")
    ap.add_argument("--locale")
    ap.add_argument("--scheme")
    a = ap.parse_args()

    loc_cfg, schemes = load_cfg()
    if a.locale and a.locale not in loc_cfg["locales"]:
        print("§CLASSIFY_CHECK FATAL unknown locale %r; known=%s"
              % (a.locale, sorted(loc_cfg["locales"])))
        return 2
    if a.scheme and a.scheme not in schemes:
        print("§CLASSIFY_CHECK FATAL unknown scheme %r; known=%s" % (a.scheme, sorted(schemes)))
        return 2

    print("§CLASSIFY_CHECK config locales=%s schemes=%s"
          % (sorted(loc_cfg["locales"]), sorted(schemes)))
    rc = 0
    for db in a.db:
        label = os.path.basename(db)
        if not os.path.exists(db):
            print("\n§CLASSIFY_CHECK[%s] SKIP — no such DB" % label)
            continue
        c = sqlite3.connect("file:%s?mode=ro" % db, uri=True)
        locale, sid, how = resolve_locale(c, loc_cfg, schemes, a.locale, a.scheme)
        s = schemes[sid]
        example = s.get("status") == "EXAMPLE-NOT-SPECIFIED"
        print("\n§CLASSIFY_CHECK[%s] locale=%s scheme=%s (%s) status=%s"
              % (label, locale, sid, how, s.get("status")))

        r = check_codes(c, s)
        if not r["schema"]:
            print("  §CLASSIFY_CODES SCHEMA-MISSING — elements_meta has no classification_code column. "
                  "This DB predates the 2026-07-30 extraction change; re-extract or --enrich it. "
                  "elements=%d" % r["total"])
        else:
            print("  §CLASSIFY_CODES coded=%d/%d (%.2f%%) uncoded=%d distinct=%d "
                  "valid=%d invalid=%d badLength=%d"
                  % (r["coded"], r["total"], 100.0 * r["coded"] / r["total"] if r["total"] else 0.0,
                     r["uncoded"], r["distinct"], r["valid"], r["invalid"], r["badlen"]))
            for e in r["bad_examples"]:
                print("     invalid e.g. %s" % e)
            print("  §CLASSIFY_FACETS %s" % json.dumps(r["facets"]))

        lvl_id = (loc_cfg["locales"].get(locale) or {}).get("level_naming")
        rules = loc_cfg.get("level_naming", {}).get(lvl_id) if lvl_id else None
        if rules:
            base = rules.get("extends")
            merged = dict(loc_cfg["level_naming"][base]) if base else {}
            merged.update(rules)
            lv = check_levels(c, merged)
            if lv:
                print("  §CLASSIFY_LEVELS distinctStoreys=%d conventionsInUse=%d hits=%s "
                      "noPrefixOrUnmatched=%d unknown=%d prefixCollisions=%d"
                      % (lv["distinct_storeys"], lv["in_use"], json.dumps(lv["hits"]),
                         lv["unmatched"], lv["unknown"], len(lv["collisions"])))
                for pfx, names in sorted(lv["collisions"].items()):
                    print("     §LEVEL_COLLISION prefix %r names %d distinct storeys: %s"
                          % (pfx, len(names), names))
                if lv["in_use"] > 1:
                    print("     §LEVEL_MIXED the model uses %d naming conventions at once "
                          "— a level-code checker keyed on the prefix would be wrong here"
                          % lv["in_use"])

        if example:
            print("  §CLASSIFY_CHECK EXAMPLE-SCHEME — NO COMPLIANCE VERDICT EMITTED.")
            print("     %r is a placeholder shape, not the real format. Coverage above is real; any"
                  " pass/fail against it would not be." % sid)
            print("     Supply the primary JKR/CIDB spec and fill "
                  "config/classification/schemes/%s.json _TODO_FROM_PRIMARY_SPEC." % sid.split(".")[0])
        elif r.get("schema"):
            verdict = "GREEN" if r["invalid"] == 0 else "RED"
            if r["invalid"]:
                rc = 1
            print("  §CLASSIFY_VERDICT %s — %d/%d carried codes are well-formed for %s "
                  "(%d elements carry no code at all; that is coverage, not a format error)"
                  % (verdict, r["valid"], r["coded"], sid, r["uncoded"]))
        c.close()
    print("\n§CLASSIFY_CHECK done rc=%d" % rc)
    return rc


if __name__ == "__main__":
    sys.exit(main())
