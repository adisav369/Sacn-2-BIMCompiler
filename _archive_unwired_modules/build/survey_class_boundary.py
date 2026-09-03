#!/usr/bin/env python3
"""
survey_class_boundary.py — Step 4 of the residential-standard arc: locate the
BUILDING-CLASS BOUNDARY empirically. For each building measure its OWN cross-
discipline p05 NN 3D clearance (the clash-relevant separation between trades) and
compare to the two mined standards:
  - Duplex (residential):     ELEC|PLB ~0.42-0.50m  (trades share one tight void)
  - Terminal (large-complex): ELEC-pairs ~2.27-2.82m (deep plenums separate trades)
The question: do Clinic/Hospital fit Terminal-class, Duplex-class, or need their OWN
mining? NON-INVENT — every number is a measured p05 over real element_transforms.

Duplex MEP is generic (one 'MEP' discipline) → its sub-disc comes from the
mep_subdisc table the miner wrote. Clinic/Hospital/Terminal carry REAL discipline
tags (PLB/ACMV/ELEC/FP) → measured directly. Big discs are sampled (cap, LOGGED).

Run:  python3 build/survey_class_boundary.py
"""
import os, sqlite3, random
import numpy as np
random.seed(7)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
CAP = 3000  # per-disc sample cap for the O(n*cap) NN (LOGGED when it bites)

BUILDINGS = [
    ("Duplex (residential)",      os.path.join(HERE, "Duplex_mep_meta.db"), "subdisc"),
    ("Clinic (healthcare)",       os.path.join(ROOT, "deploy/buildings/Clinic_meta.db"), "disc"),
    ("Hospital (LOD400 complex)", os.path.join(ROOT, "deploy/buildings/Hospital_meta.db"), "disc"),
    ("Terminal (LOD400 airport)", os.path.join(ROOT, "deploy/buildings/Terminal_extracted.db"), "disc"),
]
PAIRS = [("ELEC", "PLB"), ("ACMV", "PLB"), ("ACMV", "ELEC"), ("FP", "ELEC"),
         ("FP", "PLB"), ("PLB", "MEP"), ("ACMV", "MEP")]


def load_disc(con, mode):
    """{disc: Nx3 array of centres}. mode='disc' uses elements_meta.discipline;
    mode='subdisc' joins mep_subdisc (Duplex's generic MEP split)."""
    out = {}
    if mode == "subdisc":
        q = """SELECT ms.subdisc, t.center_x,t.center_y,t.center_z
               FROM elements_meta e JOIN element_transforms t ON e.guid=t.guid
               JOIN mep_subdisc ms ON e.guid=ms.guid"""
    else:
        q = """SELECT e.discipline, t.center_x,t.center_y,t.center_z
               FROM elements_meta e JOIN element_transforms t ON e.guid=t.guid
               WHERE e.discipline NOT IN ('ARC','STR')"""
    rows = con.execute(q).fetchall()
    by = {}
    for d, x, y, z in rows:
        by.setdefault(d, []).append((x, y, z))
    for d, pts in by.items():
        out[d] = np.array(pts, dtype=float)
    return out


def p05_clearance(A, B, cap=CAP):
    """symmetric p05 of nearest-neighbour 3D distance between disc clouds A,B."""
    capped = False
    if len(A) > cap:
        A = A[random.sample(range(len(A)), cap)]; capped = True
    if len(B) > cap:
        B = B[random.sample(range(len(B)), cap)]; capped = True
    if len(A) < 3 or len(B) < 3:
        return None, 0, capped
    def nn(frm, to):
        mins = np.empty(len(frm)); CH = 256
        for s in range(0, len(frm), CH):
            blk = frm[s:s+CH]
            d = np.sqrt(((blk[:, None, :] - to[None, :, :])**2).sum(-1))
            mins[s:s+len(blk)] = d.min(1)
        return mins
    dists = np.concatenate([nn(A, B), nn(B, A)])
    dists = dists[dists > 1e-9]
    if not len(dists):
        return None, 0, capped, 0, 0
    dists.sort()
    # phantom-clash fractions: what % of this REAL coordinated building's trade
    # pairs each STANDARD would wrongly FLAG as clashing (the building is built &
    # works → anything flagged is a phantom). DX_CLR/TM_CLR = the median ELEC|PLB
    # min_clear each mined standard carries.
    frac_dx = float((dists < DX_CLR).mean())
    frac_tm = float((dists < TM_CLR).mean())
    return float(dists[int(.05*len(dists))]), len(dists), capped, frac_dx, frac_tm


def _live_clr(db_path):
    """ELEC|PLB median min_clear from a rules DB (reads the LIVE value, so the
    terminal re-mine fix is reflected automatically)."""
    try:
        con = sqlite3.connect(db_path)
        vs = [r[0] for r in con.execute(
            "SELECT min_clear_m FROM rule_avoidance WHERE "
            "(disc_a='ELEC' AND disc_b='PLB') OR (disc_a='PLB' AND disc_b='ELEC')").fetchall()]
        con.close()
        return float(np.median(vs)) if vs else None
    except Exception:
        return None


DX_CLR = _live_clr(os.path.join(HERE, "duplex_rules.db")) or 0.499
TM_CLR = _live_clr(os.path.join(HERE, "terminal_rules.db")) or 1.287


def classify(p05):
    if p05 is None:
        return "?"
    if p05 < 0.65:
        return "RESIDENTIAL"     # Duplex-class: trades share one tight void
    if p05 > 1.6:
        return "LARGE-COMPLEX"   # Terminal-class: deep plenums separate trades
    return "MID/OWN"             # neither — candidate for its own standard


def main():
    print("§CB-BEGIN class-boundary survey — cross-disc p05 NN clearance (m)\n")
    summary = {}
    for name, path, mode in BUILDINGS:
        if not os.path.exists(path):
            print(f"  {name}: MISSING {path}"); continue
        con = sqlite3.connect(path)
        discs = load_disc(con, mode)
        con.close()
        present = {d: len(v) for d, v in discs.items()}
        print(f"── {name} ── discs: " +
              ", ".join(f"{d}={n}" for d, n in sorted(present.items(), key=lambda x: -x[1])))
        vals, fdx, ftm = [], [], []
        for a, b in PAIRS:
            if a not in discs or b not in discs:
                continue
            p05, n, capped, frac_dx, frac_tm = p05_clearance(discs[a], discs[b])
            if p05 is None:
                continue
            cls = classify(p05)
            vals.append(p05); fdx.append(frac_dx); ftm.append(frac_tm)
            tag = "  ⚠sampled" if capped else ""
            print(f"    {a:5s}|{b:5s}  p05={p05:6.3f}m  n={n:6d}  "
                  f"phantom-flagged: residential={frac_dx*100:4.1f}% terminal={frac_tm*100:4.1f}%  → {cls}{tag}")
        if vals:
            med = float(np.median(vals))
            summary[name] = (med, classify(med), float(np.mean(fdx)), float(np.mean(ftm)))
            print(f"    ▸ median cross-disc p05 = {med:.3f}m → {classify(med)}  "
                  f"| mean phantom-flag: residential={np.mean(fdx)*100:.1f}% terminal={np.mean(ftm)*100:.1f}%\n")
        else:
            print("    ▸ no measurable cross-disc pair\n")
    print("§CB-VERDICT (median cross-disc p05 → class; phantom-flag = %% of this REAL")
    print("            coordinated building's trade-pairs each STANDARD wrongly calls a clash):")
    print(f"  {'building':32s} {'p05':>7s}  {'class':13s} {'residential':>12s} {'terminal':>10s}")
    for name, (med, cls, fdx, ftm) in summary.items():
        print(f"  {name:32s} {med:6.3f}m  {cls:13s} {fdx*100:10.1f}% {ftm*100:8.1f}%")
    print("\n§CB-END")


if __name__ == "__main__":
    main()
