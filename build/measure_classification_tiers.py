#!/usr/bin/env python3
"""§TIER-COVERAGE — merge the 7 per-discipline Hospital extracts on GUID, then measure the
classification provenance tier of every distinct Revit type.

PRIME RULE: EXTRACT ONLY. Nothing is written back into any DB. Tier 2 ("derived:sibling") is a pure
SQL join on ifc_class to an ALREADY-CODED type in the SAME model — it is the model's own assertion
generalised, never a guess from an element NAME.
"""
import csv
import os
import sqlite3
import sys
from collections import Counter, defaultdict

# S = the working dir holding db/Hospital_<DISC>.db (one per-discipline extractIFCtoDB.py run).
# Defaults to this script's dir; pass the scratch dir used for the extracts as argv[1].
S = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.abspath(__file__))
DISCS = ["ARC", "STR", "ELE", "MECH", "PLB", "FIRE", "SPR"]


def type_key(name):
    if not name:
        return "(no name)"
    p = name.split(":")
    return ":".join(p[:2]) if len(p) >= 2 else name


def main():
    merged = os.path.join(S, "db", "Hospital_merged.db")
    if os.path.exists(merged):
        os.remove(merged)
    out = sqlite3.connect(merged)
    out.execute("""CREATE TABLE elements_meta (
        guid TEXT PRIMARY KEY, ifc_class TEXT, element_name TEXT, discipline TEXT,
        classification_code TEXT, classification_system TEXT, classification_name TEXT)""")
    out.execute("""CREATE TABLE rel_aggregates (
        parent_guid TEXT, child_guid TEXT, parent_class TEXT, rel_type TEXT)""")

    per_disc = {}
    agg_total = 0
    for d in DISCS:
        p = os.path.join(S, "db", f"Hospital_{d}.db")
        if not os.path.exists(p):
            print(f"§MERGE MISSING {d} — {p} absent, ABORT")
            sys.exit(1)
        src = sqlite3.connect(p)
        cols = [r[1] for r in src.execute("PRAGMA table_info(elements_meta)")]
        for c in ("classification_code", "classification_system", "classification_name"):
            if c not in cols:
                print(f"§MERGE {d} elements_meta LACKS {c} — wrong extractor, ABORT")
                sys.exit(1)
        rows = src.execute(
            "SELECT guid, ifc_class, element_name, classification_code, "
            "classification_system, classification_name FROM elements_meta").fetchall()
        out.executemany(
            "INSERT OR IGNORE INTO elements_meta "
            "(guid, ifc_class, element_name, discipline, classification_code, "
            " classification_system, classification_name) VALUES (?,?,?,?,?,?,?)",
            [(r[0], r[1], r[2], d, r[3], r[4], r[5]) for r in rows])
        acols = [r[1] for r in src.execute("PRAGMA table_info(rel_aggregates)")]
        sel = "parent_guid, child_guid, " + \
              ("parent_class" if "parent_class" in acols else "NULL") + ", " + \
              ("rel_type" if "rel_type" in acols else "'aggregates'")
        arows = src.execute(f"SELECT {sel} FROM rel_aggregates").fetchall()
        out.executemany("INSERT INTO rel_aggregates VALUES (?,?,?,?)", arows)
        agg_total += len(arows)
        per_disc[d] = (len(rows), len(arows))
        src.close()
    out.commit()

    n_elem = out.execute("SELECT COUNT(*) FROM elements_meta").fetchone()[0]
    n_agg = out.execute("SELECT COUNT(*) FROM rel_aggregates").fetchone()[0]
    n_par = out.execute("SELECT COUNT(DISTINCT parent_guid) FROM rel_aggregates").fetchone()[0]
    n_coded = out.execute(
        "SELECT COUNT(*) FROM elements_meta WHERE classification_code IS NOT NULL").fetchone()[0]
    n_dist = out.execute(
        "SELECT COUNT(DISTINCT classification_code) FROM elements_meta "
        "WHERE classification_code IS NOT NULL").fetchone()[0]
    systems = ",".join(sorted(r[0] for r in out.execute(
        "SELECT DISTINCT classification_system FROM elements_meta "
        "WHERE classification_system IS NOT NULL") if r[0]))

    print("§MERGE per-discipline (elements, aggregate-edges):")
    for d in DISCS:
        print(f"   {d:5s} elements={per_disc[d][0]:6d}  agg={per_disc[d][1]:5d}")
    print(f"§MERGE_TOTAL elements_meta={n_elem} (sum-before-dedup={sum(v[0] for v in per_disc.values())})")
    print(f"§DECOMP_MERGED rel_aggregates={n_agg} rows / {n_par} distinct parents")
    print(f"§CLASSIFY_MERGED coded={n_coded}/{n_elem} ({100.0*n_coded/n_elem:.2f}%) "
          f"uncoded={n_elem-n_coded} distinct_codes={n_dist} systems={systems}")

    # ── type-level table ────────────────────────────────────────────────────
    rows = out.execute(
        "SELECT element_name, ifc_class, classification_code, classification_name, discipline "
        "FROM elements_meta").fetchall()

    # does a type_key ever span >1 ifc_class?
    tk_classes = defaultdict(set)
    for nm, cl, _, _, _ in rows:
        tk_classes[type_key(nm)].add(cl)
    span = {k: v for k, v in tk_classes.items() if len(v) > 1}
    print(f"§TYPE_KEY distinct type_keys={len(tk_classes)}  "
          f"spanning>1 ifc_class={len(span)}")
    for k, v in list(span.items())[:10]:
        print(f"   §TYPE_SPAN {k!r} -> {sorted(v)}")

    # row identity = (type_key, ifc_class)  — exact, no collapsing
    t_inst = Counter()
    t_coded = Counter()
    t_codes = defaultdict(Counter)     # (tk,cl) -> code counter
    t_disc = defaultdict(Counter)
    cls_inst = Counter()
    cls_coded = Counter()
    cls_codes = defaultdict(Counter)   # ifc_class -> code counter (instances)
    cls_code_types = defaultdict(lambda: defaultdict(Counter))  # cls -> code -> tk counter
    code_label = {}
    for nm, cl, code, cname, disc in rows:
        k = (type_key(nm), cl)
        t_inst[k] += 1
        t_disc[k][disc] += 1
        cls_inst[cl] += 1
        if code:
            t_coded[k] += 1
            t_codes[k][code] += 1
            cls_coded[cl] += 1
            cls_codes[cl][code] += 1
            cls_code_types[cl][code][type_key(nm)] += 1
            if cname:
                code_label[code] = cname

    def tier(k):
        if t_coded[k] > 0:
            return "ifc:recovered"
        if cls_coded[k[1]] > 0:
            return "derived:sibling"
        return "derived:ai"

    tiers = {k: tier(k) for k in t_inst}
    by_tier_types = Counter(tiers.values())
    by_tier_inst = Counter()
    for k, v in t_inst.items():
        by_tier_inst[tiers[k]] += v

    print("\n§TIER_SPLIT_BY_TYPE  (row = distinct (type_key, ifc_class))")
    tot_t = sum(by_tier_types.values())
    for t in ("ifc:recovered", "derived:sibling", "derived:ai"):
        print(f"   {t:16s} {by_tier_types[t]:5d} / {tot_t}  ({100.0*by_tier_types[t]/tot_t:5.2f}%)")
    print("§TIER_SPLIT_BY_INSTANCE")
    tot_i = sum(by_tier_inst.values())
    for t in ("ifc:recovered", "derived:sibling", "derived:ai"):
        print(f"   {t:16s} {by_tier_inst[t]:6d} / {tot_i}  ({100.0*by_tier_inst[t]/tot_i:5.2f}%)")

    # partial coding inside an ifc:recovered type — honesty line
    partial = [(k, t_coded[k], t_inst[k]) for k in t_inst
               if 0 < t_coded[k] < t_inst[k]]
    partial_uncoded_inst = sum(t_inst[k] - t_coded[k] for k, _, _ in partial)
    print(f"§PARTIAL types with SOME coded instances but not all = {len(partial)}; "
          f"their uncoded instances = {partial_uncoded_inst}")

    # ambiguity of the ifc_class join key
    amb_cls = {c: dict(cc) for c, cc in cls_codes.items() if len(cc) > 1}
    print(f"§JOINKEY ifc_classes carrying ANY code = {len(cls_codes)}; "
          f"of those, carrying >1 DISTINCT code = {len(amb_cls)}")
    for c, cc in sorted(amb_cls.items(), key=lambda x: -sum(x[1].values())):
        print(f"   §JOINKEY_AMBIG {c}: " +
              ", ".join(f"{k}={v}" for k, v in sorted(cc.items(), key=lambda x: -x[1])))

    # ── ranked worklist CSV: uncoded types ─────────────────────────────────
    work = [k for k in t_inst if t_coded[k] == 0]
    work.sort(key=lambda k: -t_inst[k])
    csv_path = os.path.join(S, "classification_tier_worklist_Hospital.csv")
    with open(csv_path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["rank", "type_key", "ifc_class", "instances", "discipline",
                    "tier", "proposed_code", "proposed_code_label",
                    "evidence_sibling_type", "evidence_sibling_instances",
                    "sibling_coded_instances_in_class", "sibling_distinct_codes_in_class",
                    "join_key_ambiguous"])
        for i, k in enumerate(work, 1):
            tk, cl = k
            t = tiers[k]
            prop = lbl = ev_type = ""
            ev_n = ""
            ambiguous = ""
            if t == "derived:sibling":
                cc = cls_codes[cl]
                prop, _n = cc.most_common(1)[0]
                lbl = code_label.get(prop, "")
                ev_type, ev_n = cls_code_types[cl][prop].most_common(1)[0]
                ambiguous = "YES" if len(cc) > 1 else "no"
            w.writerow([i, tk, cl, t_inst[k],
                        t_disc[k].most_common(1)[0][0], t,
                        prop, lbl, ev_type, ev_n,
                        cls_coded[cl], len(cls_codes[cl]),
                        ambiguous])
    print(f"\n§WORKLIST {len(work)} uncoded (type_key,ifc_class) rows -> {csv_path}")

    sib = [k for k in work if tiers[k] == "derived:sibling"]
    ai = [k for k in work if tiers[k] == "derived:ai"]
    sib_i = sum(t_inst[k] for k in sib)
    ai_i = sum(t_inst[k] for k in ai)
    amb_sib = [k for k in sib if len(cls_codes[k[1]]) > 1]
    print(f"§HEADLINE uncoded_types={len(work)} of {tot_t} | "
          f"sibling_types={len(sib)} covering {sib_i} instances | "
          f"ai_types={len(ai)} covering {ai_i} instances")
    print(f"§HEADLINE_AMBIG of the {len(sib)} sibling types, {len(amb_sib)} sit on an ifc_class "
          f"with >1 distinct code (covering {sum(t_inst[k] for k in amb_sib)} instances) — "
          f"the join proposes the MODAL code there, which is a judgement the data does not settle")

    print("\n§TOP20_UNCODED")
    for i, k in enumerate(work[:20], 1):
        tk, cl = k
        p = ""
        if tiers[k] == "derived:sibling":
            p = " -> " + cls_codes[cl].most_common(1)[0][0]
        print(f"  {i:2d}. {t_inst[k]:6d}  {tiers[k]:16s} {cl:24s} {tk[:52]}{p}")

    out.close()


if __name__ == "__main__":
    main()
