#!/usr/bin/env python3
"""
remine_terminal_avoidance.py — FIX the inflated Terminal clearances (Step 4 follow-up).

The original `terminal_rules.db` rule_avoidance was mined PER-STOREY-banded; the gate
takes the MEDIAN over storeys, which systematically OVER-states the building's real trade
separation (e.g. ELEC|PLB median 1.287m, FP|ELEC ~2.0m). Direct global measurement of
Terminal's OWN coordinated MEP shows the true p05 is 0.15-0.45m — and the inflated rule
flagged 37.5% of Terminal's own built MEP as clashing (a standard inconsistent with the
building it came from). See memory project_dx_mep_class_boundary.

FIX (NON-INVENT): recompute each disc pair's min_clear as the DIRECT GLOBAL p05 of the
symmetric nearest-neighbour 3D distance over the REAL element_transforms — the tightest
coordination the building actually demonstrates, which IS the honest clash threshold. The
old per-storey rows are archived to rule_avoidance_perstorey_archived for audit; the
yields (drop direction) and pair set are preserved — only the magnitude is corrected.

Other tables (placement/routing/space_bom/place_order) are NOT touched — only avoidance
was inflated.

Run:  python3 build/remine_terminal_avoidance.py
"""
import os, sqlite3
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RULES = os.path.join(HERE, "terminal_rules.db")
TERM = os.path.join(ROOT, "deploy/buildings/Terminal_extracted.db")

# Terminal disc → ifc_class map (read from the rules' own placement/routing; MEP = the
# generic IfcFlowTerminal/Controller leftovers under the MEP tag).
DMAP = {
    'PLB':  ('IfcPipeFitting', 'IfcPipeSegment', 'IfcValve'),
    'ACMV': ('IfcAirTerminal', 'IfcDuctFitting', 'IfcDuctSegment'),
    'FP':   ('IfcAlarm', 'IfcFireSuppressionTerminal'),
    'ELEC': ('IfcElectricAppliance', 'IfcLightFixture'),
    'MEP':  ('IfcFlowTerminal', 'IfcFlowController'),
}


def load(con, disc):
    q = ("SELECT t.center_x,t.center_y,t.center_z FROM elements_meta e "
         "JOIN element_transforms t ON e.guid=t.guid WHERE e.ifc_class IN (%s)"
         % ",".join("?" * len(DMAP[disc])))
    return np.array(con.execute(q, DMAP[disc]).fetchall(), dtype=float)


def nn(frm, to):
    m = np.empty(len(frm)); CH = 256
    for s in range(0, len(frm), CH):
        b = frm[s:s+CH]
        d = np.sqrt(((b[:, None, :] - to[None, :, :])**2).sum(-1))
        m[s:s+len(b)] = d.min(1)
    return m


def global_p05(A, B):
    d = np.concatenate([nn(A, B), nn(B, A)])
    d = d[d > 1e-9]; d.sort()
    return float(d[int(.05*len(d))]), float(d[0]), float(np.median(d)), len(d)


def main():
    rc = sqlite3.connect(RULES)
    tc = sqlite3.connect(TERM)
    clouds = {d: load(tc, d) for d in DMAP}
    tc.close()
    for d, pts in clouds.items():
        print(f"  {d:5s} n={len(pts)}")

    # pairs + preserved yields + old median (grouped order-agnostically by sorted key,
    # since stored rows use the original unsorted disc_a|disc_b order).
    rows = rc.execute("SELECT disc_a,disc_b,min_clear_m,yields FROM rule_avoidance").fetchall()
    pairs = {}
    for a, b, mc, y in rows:
        k = tuple(sorted((a, b)))
        p = pairs.setdefault(k, {'yields': {}, 'clears': []})
        p['yields'][y] = p['yields'].get(y, 0) + 1
        p['clears'].append(mc)
    oldmed = {k: float(np.median(v['clears'])) for k, v in pairs.items()}

    # archive old, rebuild global
    rc.execute("DROP TABLE IF EXISTS rule_avoidance_perstorey_archived")
    rc.execute("CREATE TABLE rule_avoidance_perstorey_archived AS SELECT * FROM rule_avoidance")
    rc.execute("DELETE FROM rule_avoidance")

    print("\n§TRA-REMINE disc_pair  old_median → global_p05  (raw_min, median)")
    n = 0
    for (a, b), meta in sorted(pairs.items()):
        A, B = clouds.get(a), clouds.get(b)
        if A is None or B is None or len(A) < 3 or len(B) < 3:
            continue
        p05, rawmin, med, cnt = global_p05(A, B)
        yields = max(meta['yields'], key=meta['yields'].get)
        om = oldmed[(a, b)]
        prov = (f"measured:terminal/global-p05;raw_min={rawmin:.3f}m;median={med:.2f}m;"
                f"was_perstorey_median={om:.3f}m")
        rc.execute("INSERT INTO rule_avoidance VALUES (?,?,?,?,?,?,?)",
                   (a, b, round(p05, 3), yields, 'global', cnt, prov))
        n += 1
        arrow = "↓" if p05 < om else ("↑" if p05 > om else " ")
        print(f"  {a:5s}|{b:5s}  {round(om,3):>6}  → {p05:6.3f} {arrow}"
              f"  (raw_min={rawmin:.3f} median={med:.2f} n={cnt})")
    rc.commit()
    rc.close()
    print(f"\n§TRA-DONE rebuilt {n} global-p05 avoidance rows "
          f"(old per-storey archived to rule_avoidance_perstorey_archived)")


if __name__ == "__main__":
    main()
