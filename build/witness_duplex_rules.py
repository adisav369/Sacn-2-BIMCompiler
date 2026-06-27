#!/usr/bin/env python3
"""
witness_duplex_rules.py — ROUND-TRIP oracle for the RESIDENTIAL standard.
Mirror of witness_terminal_rules.py (§TRM-RT): replay the LIVE-MINED
build/duplex_rules.db against the REAL build/Duplex_mep_meta.db and prove the
mined rules reproduce DX's OWN MEP. NON-INVENT — no synthetic positions; an
honest WEAK/RED is correct, never tuned to pass.

Difference from the Terminal witness: all DX MEP shares discipline='MEP' and the
sub-discipline (PLB/ELEC/ACMV) lives in the `mep_subdisc` table the miner wrote
(name + nearest-neighbour evidence). IFC2x3 uses GENERIC classes, so the SAME
ifc_class (e.g. IfcFlowSegment) appears under BOTH PLB and ELEC — real_elems
therefore filters STRICTLY by (subdisc, ifc_class) with NO class-only fallback
(a fallback would mix plumbing pipes with electrical conduit). Verdict rubric is
identical to §TRM-RT: GREEN = rmse<=band AND cover>=0.85 (placed) or chain
re-measures (routed); WEAK = partial; RED = does not reproduce.

Run:  python3 build/witness_duplex_rules.py
"""
import json, math, os, sqlite3, sys
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
RULES_DB = os.path.join(HERE, "duplex_rules.db")
META_DB = os.path.join(HERE, "Duplex_mep_meta.db")

rc = sqlite3.connect(RULES_DB)
mc = sqlite3.connect(META_DB)

# Generic IFC2x3 flow classes: a Segment/Fitting threads the network (ROUTED),
# a Terminal/Controller is a placed fixture/valve (z-band PLACED).
NETWORK_CLASSES = {"IfcFlowSegment", "IfcFlowFitting"}
_RANK = {"GREEN": 2, "WEAK": 1}


def real_elems(disc, ifc_class):
    """centres of (subdisc, ifc_class) — STRICT subdisc filter via mep_subdisc."""
    rows = mc.execute(
        """SELECT t.center_x,t.center_y,t.center_z
           FROM elements_meta e
           JOIN element_transforms t ON e.guid=t.guid
           JOIN mep_subdisc ms ON e.guid=ms.guid
           WHERE ms.subdisc=? AND e.ifc_class=?""", (disc, ifc_class)).fetchall()
    return np.array(rows, dtype=float) if rows else np.zeros((0, 3))


def real_count(disc, ifc_class):
    return mc.execute(
        """SELECT count(*) FROM elements_meta e JOIN mep_subdisc ms ON e.guid=ms.guid
           WHERE ms.subdisc=? AND e.ifc_class=?""", (disc, ifc_class)).fetchone()[0]


def nn_xy_median(pts):
    if len(pts) < 2:
        return float("nan")
    xy = pts[:, :2]
    out = np.empty(len(xy))
    for i in range(len(xy)):
        d = np.hypot(xy[:, 0] - xy[i, 0], xy[:, 1] - xy[i, 1])
        d[i] = np.inf
        out[i] = d.min()
    return float(np.median(out))


def nn_3d_avg(frm, to):
    if len(frm) == 0 or len(to) == 0:
        return float("nan")
    mins = np.empty(len(frm))
    CH = 256
    for s in range(0, len(frm), CH):
        blk = frm[s:s + CH]
        d = np.sqrt(((blk[:, None, :] - to[None, :, :]) ** 2).sum(-1))
        mins[s:s + CH] = d.min(1)
    return float(mins.mean())


print("§DXM-RT-BEGIN db=%s rules=%s" % (os.path.basename(META_DB),
                                        os.path.basename(RULES_DB)))

# ---- ROUTING nn round-trip (drives NETWORK verdicts) -----------------------
route_rows = rc.execute(
    """SELECT disc, from_kind, to_kind, pattern, params_json, n_measured
       FROM rule_routing ORDER BY disc""").fetchall()
route_results = []
class_route = {}
for disc, fk, tk, pat, pj, nm in route_rows:
    params = json.loads(pj) if pj else {}
    claim = params.get("avg_gap_m", params.get("nn_dist_avg_m"))
    if pat == "nn" and claim is not None:
        got = nn_3d_avg(real_elems(disc, fk), real_elems(disc, tk))
        if math.isnan(got):
            verdict = "WEAK"
        else:
            ratio = got / claim if claim else float("nan")
            verdict = "GREEN" if (got <= 1.0 and 0.3 <= ratio <= 3.0) else "WEAK"
    else:
        got, verdict = float("nan"), "WEAK"
    route_results.append((disc, fk, tk, pat, got, claim, verdict))
    for c in (fk, tk):
        if _RANK.get(class_route.get((disc, c), ""), 0) < _RANK[verdict]:
            class_route[(disc, c)] = verdict

# ---- ARRAY spacing re-measure (drives placed-class cadence) ----------------
arr_rows = rc.execute(
    """SELECT disc, ifc_class, z_band_lo, z_band_hi, spacing_x_m, spacing_y_m,
              storey_scope FROM rule_placement
       WHERE spacing_x_m IS NOT NULL AND spacing_x_m>0 ORDER BY disc""").fetchall()
array_results = []
class_array = {}
for disc, cls, lo, hi, sx, sy, scope in arr_rows:
    pts = real_elems(disc, cls)
    if len(pts) == 0:
        continue
    band = pts[(pts[:, 2] >= lo) & (pts[:, 2] <= hi)]
    if len(band) < 3:
        continue
    med = nn_xy_median(band)
    claim = min(sx, sy) if sy else sx
    ratio = med / claim if claim else float("nan")
    av = "GREEN" if 0.5 <= ratio <= 2.0 else "WEAK"
    array_results.append((disc, cls, scope, med, claim, ratio, av))
    class_array.setdefault((disc, cls), []).append(av)

# ---- PLACEMENT z-band round-trip (per subdisc+class) -----------------------
place = rc.execute(
    """SELECT disc, ifc_class, z_band_lo, z_band_hi, n_measured
       FROM rule_placement ORDER BY disc, ifc_class""").fetchall()
groups = {}
for disc, cls, lo, hi, nm in place:
    groups.setdefault((disc, cls), []).append((lo, hi, nm or 0))

green = weak = red = 0
disc_roll = {}
for (disc, cls), bands in sorted(groups.items()):
    pts = real_elems(disc, cls)
    count_real = len(pts)
    count_pred = sum(b[2] for b in bands)
    if count_real == 0:
        print("§DXM-RT disc=%s class=%s NO-REAL-ELEMS (refused)" % (disc, cls))
        red += 1
        continue
    mids = np.array([(lo + hi) / 2.0 for lo, hi, _ in bands])
    z = pts[:, 2]
    dz = np.abs(z[:, None] - mids[None, :])
    near = dz.min(1)
    rmse = float(math.sqrt(np.mean(near ** 2)))
    inside = np.zeros(count_real, dtype=bool)
    for lo, hi, _ in bands:
        inside |= (z >= lo) & (z <= hi)
    cover = float(inside.mean())
    bandh = float(np.mean([hi - lo for lo, hi, _ in bands])) or 0.0
    cnt_err = abs(count_pred - count_real) / count_real
    if cls in NETWORK_CLASSES:
        rv = class_route.get((disc, cls))
        verdict = rv if rv in ("GREEN", "WEAK") else "RED"
        mode = "routed"
    elif (disc, cls) in class_array:
        avs = class_array[(disc, cls)]
        verdict = "GREEN" if all(a == "GREEN" for a in avs) else \
            ("WEAK" if any(a in ("GREEN", "WEAK") for a in avs) else "RED")
        mode = "array"
    else:
        ok_rmse = rmse <= max(1.0, bandh)
        ok_cov = cover >= 0.85
        ok = ok_rmse and ok_cov
        verdict = "GREEN" if ok else ("WEAK" if (ok_rmse or ok_cov) else "RED")
        mode = "placed"
    if verdict == "GREEN":
        green += 1
    elif verdict == "WEAK":
        weak += 1
    else:
        red += 1
    disc_roll.setdefault(disc, []).append(verdict)
    extra = " array=%s" % "/".join(class_array[(disc, cls)]) if mode == "array" else ""
    print("§DXM-RT disc=%s class=%s mode=%s bands=%d rmse_z=%.3f cover=%.2f "
          "count_pred=%d count_real=%d cnt_err=%.2f ok=%s%s" %
          (disc, cls, mode, len(bands), rmse, cover, count_pred, count_real,
           cnt_err, verdict, extra))

for disc, cls, scope, med, claim, ratio, ok in array_results:
    print("§DXM-ARRAY disc=%s class=%s scope=%.18s nn_xy_med=%.2f "
          "spacing_claim=%.2f ratio=%.2f ok=%s" %
          (disc, cls, scope, med, claim, ratio, ok))

for disc, fk, tk, pat, got, claim, verdict in route_results:
    if math.isnan(got) or claim is None:
        print("§DXM-ROUTE disc=%s %s->%s pattern=%s structural=not-distance-scored" %
              (disc, fk, tk, pat))
        continue
    ratio = got / claim if claim else float("nan")
    print("§DXM-ROUTE disc=%s %s->%s nn3d_avg=%.3f claim=%.3f ratio=%.2f ok=%s" %
          (disc, fk, tk, got, claim, ratio, verdict))

for disc, scope, cls, cper, sp, nm in rc.execute(
        "SELECT disc,scope,ifc_class,count_per,spacing_m,n_measured FROM rule_space_bom"):
    print("§DXM-BOM disc=%s class=%s count_per=%s n_measured=%s real_total=%d" %
          (disc, cls, cper, nm, real_count(disc, cls)))

# ---- AVOIDANCE: the residential clearance, side-by-side vs Terminal ---------
print("§DXM-CLEAR (residential p05 NN clearance — the thesis):")
TERM = {}
try:
    tcon = sqlite3.connect(os.path.join(HERE, "terminal_rules.db"))
    for a, b, mc_m in tcon.execute(
            "SELECT disc_a,disc_b,min_clear_m FROM rule_avoidance"):
        TERM.setdefault(tuple(sorted((a, b))), []).append(mc_m)
    tcon.close()
except Exception:
    pass
for a, b, clr, yld, zb, nm, prov in rc.execute(
        "SELECT disc_a,disc_b,min_clear_m,yields,z_band,n_measured,provenance "
        "FROM rule_avoidance ORDER BY min_clear_m"):
    tk = tuple(sorted((a, b)))
    tcmp = ("  [Terminal %s p05~%.2f..%.2fm]" %
            ("/".join(sorted(set(tk))), min(TERM[tk]), max(TERM[tk]))) if tk in TERM else ""
    print("  %-5s|%-5s %-8s min_clear=%.3fm yields=%-5s n=%d%s" %
          (a, b, zb, clr, yld, nm, tcmp))

# ---- rollup ----------------------------------------------------------------
print("§DXM-RT-PLACEMENT-ROLLUP green=%d weak=%d red=%d total=%d" %
      (green, weak, red, green + weak + red))
for disc, vs in sorted(disc_roll.items()):
    g = vs.count("GREEN"); w = vs.count("WEAK"); r = vs.count("RED")
    tag = "GREEN" if r == 0 and w == 0 else ("WEAK" if g >= r else "RED")
    print("§DXM-RT-DISC disc=%s green=%d weak=%d red=%d -> %s" % (disc, g, w, r, tag))
print("§DXM-RT-END")
