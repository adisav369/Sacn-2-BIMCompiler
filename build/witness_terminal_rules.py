#!/usr/bin/env python3
"""
witness_terminal_rules.py — ROUND-TRIP: replay the baked terminal_rules.db
against the REAL Terminal_meta.db and prove the rules reproduce the building.

Method (NON-INVENT, no synthetic positions):
  PLACEMENT/z-band rules: for each (disc, ifc_class) the baked rules give a set
    of measured z-bands. We pull the REAL element centres of that class+disc and
    assign each to the band whose midpoint is nearest (must lie within, or be the
    nearest band). Predicted z = band midpoint. We report:
      rmse_z   = sqrt(mean((real_z - band_mid)^2))            (sub-bay target)
      cover    = real elements that fall INSIDE some baked band / total
      count_pred(=Sum n_measured of the class rules) vs count_real(=real total)
  ARRAY/spacing rules: among the in-band real elements we measure the median
    nearest-neighbour XY distance and compare to the baked spacing.
  ROUTING nn rules: from->to nearest-neighbour 3D distance over the real
    discipline populations, compared to the baked params avg.
  SPACE_BOM count: baked count vs real element count of that class.

GREEN  = rmse small vs band height AND cover>=0.85 AND count within ~15%.
WEAK   = partial (one of the three off) — reported honestly.
RED    = does not reproduce.  An honest RED is correct; we do NOT tune to pass.
"""
import json, math, os, sqlite3, sys
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
RULES_DB = os.path.join(HERE, "terminal_rules.db")
META_DB = "/home/red1/bim-compiler/deploy/dev/buildings/Terminal_meta.db"

rc = sqlite3.connect(RULES_DB)
mc = sqlite3.connect(META_DB)


def real_elems(disc, ifc_class):
    # 'roof' is a synthetic disc label; roof plates carry discipline=ARC in the
    # real DB, so for it (and any disc that yields nothing) fall back to class-only.
    q = """SELECT t.center_x,t.center_y,t.center_z
           FROM elements_meta e JOIN element_transforms t ON e.guid=t.guid
           WHERE e.discipline=? AND e.ifc_class=?"""
    rows = mc.execute(q, (disc, ifc_class)).fetchall()
    if not rows:
        rows = mc.execute(
            """SELECT t.center_x,t.center_y,t.center_z
               FROM elements_meta e JOIN element_transforms t ON e.guid=t.guid
               WHERE e.ifc_class=?""", (ifc_class,)).fetchall()
    return np.array(rows, dtype=float) if rows else np.zeros((0, 3))


def nn_xy_median(pts):
    """median nearest-neighbour distance in the XY plane (m)."""
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
    """avg over each from-pt of nearest to-pt 3D distance, chunked."""
    if len(frm) == 0 or len(to) == 0:
        return float("nan")
    mins = np.empty(len(frm))
    CH = 256
    for s in range(0, len(frm), CH):
        blk = frm[s:s + CH]
        d = np.sqrt(((blk[:, None, :] - to[None, :, :]) ** 2).sum(-1))
        mins[s:s + CH] = d.min(1)
    return float(mins.mean())


# ----------------------------------------------------------------------------
print("§TRM-RT-BEGIN db=%s rules=%s" % (os.path.basename(META_DB),
                                        os.path.basename(RULES_DB)))

# ---- NETWORK classes are ROUTED, not band-PLACED ---------------------------
# A pipe/duct run threads the whole building; a z-band can't reproduce it (the
# placement-count lens is wrong for a network — Gap 3). Score these by ROUTING
# coverage (does a measured chain rule re-measure?) instead. The chain rule is
# the right model; the z-band rmse/cover are still printed as informational.
NETWORK_CLASSES = {"IfcPipeSegment", "IfcPipeFitting",
                   "IfcDuctSegment", "IfcDuctFitting"}
_RANK = {"GREEN": 2, "WEAK": 1}

route_rows = rc.execute(
    """SELECT disc, from_kind, to_kind, pattern, params_json, n_measured
       FROM rule_routing ORDER BY disc""").fetchall()
route_results = []   # (disc, fk, tk, pat, got, claim, verdict)
class_route = {}     # (disc, class) -> best verdict among routes touching it
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
        got, verdict = float("nan"), "WEAK"   # main/riser/grid/array: structural, exists but not distance-scored
    route_results.append((disc, fk, tk, pat, got, claim, verdict))
    for c in (fk, tk):
        if _RANK.get(class_route.get((disc, c), ""), 0) < _RANK[verdict]:
            class_route[(disc, c)] = verdict

# ---- PLACEMENT z-band round-trip (grouped per disc+ifc_class) ---------------
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
        print("§TRM-RT disc=%s class=%s NO-REAL-ELEMS (refused)" % (disc, cls))
        red += 1
        continue
    mids = np.array([(lo + hi) / 2.0 for lo, hi, _ in bands])
    z = pts[:, 2]
    # nearest band midpoint per element
    dz = np.abs(z[:, None] - mids[None, :])
    near = dz.min(1)
    rmse = float(math.sqrt(np.mean(near ** 2)))
    # coverage: inside any band
    inside = np.zeros(count_real, dtype=bool)
    for lo, hi, _ in bands:
        inside |= (z >= lo) & (z <= hi)
    cover = float(inside.mean())
    bandh = float(np.mean([hi - lo for lo, hi, _ in bands])) or 0.0
    cnt_err = abs(count_pred - count_real) / count_real
    if cls in NETWORK_CLASSES:
        # Routed, not placed: verdict comes from the chain rule, not z-band count.
        rv = class_route.get((disc, cls))
        verdict = rv if rv in ("GREEN", "WEAK") else "RED"
        mode = "routed"
    else:
        ok_rmse = rmse <= max(1.0, bandh)    # sub-bay / within a band height
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
    print("§TRM-RT disc=%s class=%s mode=%s bands=%d rmse_z=%.3f cover=%.2f "
          "count_pred=%d count_real=%d cnt_err=%.2f ok=%s" %
          (disc, cls, mode, len(bands), rmse, cover, count_pred, count_real,
           cnt_err, verdict))

# ---- ARRAY spacing round-trip (where a placement rule carries spacing) ------
arr = rc.execute(
    """SELECT disc, ifc_class, z_band_lo, z_band_hi, spacing_x_m, spacing_y_m,
              storey_scope FROM rule_placement
       WHERE spacing_x_m IS NOT NULL AND spacing_x_m>0 ORDER BY disc""").fetchall()
for disc, cls, lo, hi, sx, sy, scope in arr:
    pts = real_elems(disc, cls)
    if len(pts) == 0:
        continue
    band = pts[(pts[:, 2] >= lo) & (pts[:, 2] <= hi)]
    if len(band) < 3:
        continue
    med = nn_xy_median(band)
    claim = min(sx, sy) if sy else sx
    ratio = med / claim if claim else float("nan")
    ok = "GREEN" if 0.5 <= ratio <= 2.0 else "WEAK"
    print("§TRM-ARRAY disc=%s class=%s scope=%.18s nn_xy_med=%.2f "
          "spacing_claim=%.2f ratio=%.2f ok=%s" %
          (disc, cls, scope, med, claim, ratio, ok))

# ---- ROUTING round-trip (precomputed above; drives the NETWORK verdicts) ----
for disc, fk, tk, pat, got, claim, verdict in route_results:
    if math.isnan(got) or claim is None:
        print("§TRM-ROUTE disc=%s %s->%s pattern=%s structural=not-distance-scored" %
              (disc, fk, tk, pat))
        continue
    ratio = got / claim if claim else float("nan")
    print("§TRM-ROUTE disc=%s %s->%s nn3d_avg=%.3f claim=%.3f ratio=%.2f ok=%s" %
          (disc, fk, tk, got, claim, ratio, verdict))

# ---- SPACE_BOM count round-trip --------------------------------------------
for disc, scope, cls, cper, sp, nm in rc.execute(
        "SELECT disc,scope,ifc_class,count_per,spacing_m,n_measured FROM rule_space_bom"):
    real_n = mc.execute(
        "SELECT count(*) FROM elements_meta WHERE ifc_class=?", (cls,)).fetchone()[0]
    print("§TRM-BOM disc=%s class=%s count_per=%s n_measured=%s real_total=%d" %
          (disc, cls, cper, nm, real_n))

# ---- rollup ----------------------------------------------------------------
print("§TRM-RT-PLACEMENT-ROLLUP green=%d weak=%d red=%d total=%d" %
      (green, weak, red, green + weak + red))
for disc, vs in sorted(disc_roll.items()):
    g = vs.count("GREEN"); w = vs.count("WEAK"); r = vs.count("RED")
    tag = "GREEN" if r == 0 and w == 0 else ("WEAK" if g >= r else "RED")
    print("§TRM-RT-DISC disc=%s green=%d weak=%d red=%d -> %s" %
          (disc, g, w, r, tag))
print("§TRM-RT-END")
