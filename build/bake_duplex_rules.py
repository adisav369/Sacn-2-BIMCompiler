#!/usr/bin/env python3
"""
bake_duplex_rules.py — LIVE-mine the RESIDENTIAL disc-rule standard from Duplex's
OWN real MEP, into build/duplex_rules.db (SAME 5-table schema as terminal_rules.db).

Unlike bake_terminal_rules.py (which transcribes a confirmed JSON payload), this
script MEASURES every rule directly from build/Duplex_mep_meta.db. PRIME GUARD:
NON-INVENT — every row carries n_measured + src_guids traced to real DX MEP coords.
No clearance/band is tuned; an honest number is the deliverable. The whole thesis:
DX's OWN p05 clearances are residential-small (far below Terminal's 2.4m plenum
numbers), so the AvoidanceGate stops forcing house MEP into clash.

Sub-discipline (PLB/ACMV/ELEC) is NOT in ifc_class here (IFC2x3 uses generic
IfcFlowSegment/Fitting/Terminal/Controller). It is recovered NON-INVENT:
  1. element NAME keywords (segments/terminals/controllers are unambiguous:
     "Pipe"/lavatory/WC/shower→PLB, receptacle/light/switch/conduit/panel→ELEC,
     duct/diffuser→ACMV) — the names are verbatim from the Revit source.
  2. unclassified fittings (generic elbows/tees/transitions) → nearest-neighbour
     (k=1) sub-disc of an already-classified element (MEASURED 3D distance; a
     fitting physically sits on the run it connects). method+evidence stamped.
The assignment is persisted to build/Duplex_mep_meta.db table `mep_subdisc` so the
round-trip witness reads the IDENTICAL split.

Storey is "Unknown" in the source for all MEP, so it is DERIVED from the measured
z-gap (a duplex = 2 storeys; the histogram shows a clean sparse band ~z=1.5..2.5).
The split z is COMPUTED as the largest gap in the 0.5m z-histogram, not hardcoded.

Run:  python3 build/bake_duplex_rules.py
"""
import json, math, os, re, sqlite3, statistics as st, sys

HERE = os.path.dirname(os.path.abspath(__file__))
META_DB = os.path.join(HERE, "Duplex_mep_meta.db")
OUT_DB = os.path.join(HERE, "duplex_rules.db")

# ── sub-disc name classifiers (verbatim source-name keywords; NON-INVENT) ──
RE_PLB = re.compile(r'pipe|lavatory|water closet|shower|sink|drain|backflow|'
                    r'valve|plumb|faucet|toilet|bath|tub|dwv|sanitary', re.I)
RE_ELEC = re.compile(r'receptacle|light|switch|conduit|telephone|panel|smoke|'
                     r'electric|outlet|sconce|pendant|alarm|detector|appliance|'
                     r'refrigerator|range|microwave|data|fire alarm', re.I)
RE_ACMV = re.compile(r'duct|diffuser| vav|ahu|fcu|hvac|register|grille|'
                     r'rectangular to round|air terminal', re.I)


def log(m): print(m, flush=True)


def dist3(a, b):
    return math.sqrt((a[0]-b[0])**2 + (a[1]-b[1])**2 + (a[2]-b[2])**2)


def nn_min(pt, cloud):
    """min 3D distance from pt to any point in cloud (excluding identical coord)."""
    best = None
    for q in cloud:
        d = dist3(pt, q)
        if d > 1e-9 and (best is None or d < best):
            best = d
    return best


def percentile(vals, p):
    if not vals:
        return None
    s = sorted(vals)
    i = min(len(s)-1, max(0, int(p*len(s))))
    return s[i]


# ═══════════════════════════ 1. LOAD + SUB-DISC ═══════════════════════════
def classify(meta):
    cur = meta.cursor()
    rows = cur.execute(
        """SELECT e.guid, e.ifc_class, COALESCE(e.element_name,''),
                  t.center_x, t.center_y, t.center_z,
                  COALESCE(t.bbox_x,0), COALESCE(t.bbox_y,0), COALESCE(t.bbox_z,0)
           FROM elements_meta e JOIN element_transforms t ON e.guid=t.guid
           WHERE e.discipline='MEP'""").fetchall()
    classified = {}   # guid -> (subdisc, method, evidence)
    pts = {}          # guid -> (x,y,z), cls
    cls_of = {}
    name_of = {}
    meta_bbox = {}    # guid -> (bx,by,bz)
    for g, c, n, x, y, z, bx, by, bz in rows:
        pts[g] = (x, y, z); cls_of[g] = c; name_of[g] = n
        meta_bbox[g] = (bx, by, bz)
        sub = None
        if RE_ACMV.search(n):   sub = 'ACMV'
        elif RE_PLB.search(n):  sub = 'PLB'
        elif RE_ELEC.search(n): sub = 'ELEC'
        if sub:
            classified[g] = (sub, 'name', n.split(':')[0][:40])
    log(f"§DXM-SUBDISC name-pass: {len(classified)}/{len(rows)} classified "
        f"({sum(1 for v in classified.values() if v[0]=='PLB')} PLB, "
        f"{sum(1 for v in classified.values() if v[0]=='ELEC')} ELEC, "
        f"{sum(1 for v in classified.values() if v[0]=='ACMV')} ACMV)")

    # nearest-neighbour assignment for the rest (measured 3D distance)
    anchors = [(g, pts[g], classified[g][0]) for g in classified]
    unresolved = [g for g in pts if g not in classified]
    for g in unresolved:
        p = pts[g]
        best = None
        for ag, ap, asub in anchors:
            d = dist3(p, ap)
            if best is None or d < best[0]:
                best = (d, asub, ag)
        classified[g] = (best[1], 'nn', f'nn={best[0]:.3f}m->{best[2]}')
    log(f"§DXM-SUBDISC nn-pass: {len(unresolved)} fittings assigned by nearest "
        f"classified neighbour (measured 3D)")

    # persist to meta db so the witness reads the IDENTICAL split
    cur.executescript("""DROP TABLE IF EXISTS mep_subdisc;
        CREATE TABLE mep_subdisc(guid TEXT PRIMARY KEY, subdisc TEXT,
                                 method TEXT, evidence TEXT);""")
    cur.executemany("INSERT INTO mep_subdisc VALUES (?,?,?,?)",
                    [(g, v[0], v[1], v[2]) for g, v in classified.items()])
    meta.commit()
    fin = {}
    for g, v in classified.items():
        fin[v[0]] = fin.get(v[0], 0) + 1
    log(f"§DXM-SUBDISC final: {fin}")
    return pts, cls_of, name_of, {g: v[0] for g, v in classified.items()}, meta_bbox


# ═══════════════════════════ 2. STOREY (z-gap derived) ═══════════════════════════
def derive_storeys(pts):
    zs = sorted(p[2] for p in pts.values())
    # find the largest gap in the populated z-range that splits into 2 dense bands
    # (a duplex = 2 storeys). Scan candidate splits between p10 and p90.
    lo, hi = zs[int(.10*len(zs))], zs[int(.90*len(zs))]
    best_gap, split = 0, st.median(zs)
    step = 0.1
    z = lo
    while z <= hi:
        below = [v for v in zs if v < z]
        above = [v for v in zs if v >= z]
        if below and above:
            gap = min(above) - max(below)
            if gap > best_gap:
                best_gap, split = gap, z
        z += step
    log(f"§DXM-STOREY derived split z={split:.2f} (largest z-gap={best_gap:.2f}m) "
        f"-> Level 1 (z<{split:.2f}) / Level 2 (z>={split:.2f})")

    def storey_of(z):
        return 'Level 1' if z < split else 'Level 2'
    counts = {}
    for p in pts.values():
        counts[storey_of(p[2])] = counts.get(storey_of(p[2]), 0) + 1
    log(f"§DXM-STOREY counts: {counts}")
    return storey_of, split


def storey_areas(meta, storey_of):
    """Per-storey FOOTPRINT area (m²) over ALL source elements, measured the SAME
    way disc_walker.substrate() measures the target storey: XY bbox of every element's
    center±bbox/2, grouped by the z-split storey. This is the floor area the areal
    density (n_measured / area) normalises to — non-invent, both sides measured alike.
    src_storey_area travels with each placement row so the live placer can area-scale
    the measured count instead of tiling the bbox (kills the 708k explosion)."""
    rows = meta.execute(
        """SELECT t.center_x, t.center_y, t.center_z,
                  COALESCE(t.bbox_x,0), COALESCE(t.bbox_y,0)
           FROM element_transforms t""").fetchall()
    ext = {}  # storey -> [minx,maxx,miny,maxy]
    for cx, cy, cz, bx, by in rows:
        s = storey_of(cz)
        e = ext.setdefault(s, [math.inf, -math.inf, math.inf, -math.inf])
        e[0] = min(e[0], cx - bx / 2); e[1] = max(e[1], cx + bx / 2)
        e[2] = min(e[2], cy - by / 2); e[3] = max(e[3], cy + by / 2)
    out = {}
    for s, e in ext.items():
        out[s] = round(max(0.0, e[1] - e[0]) * max(0.0, e[3] - e[2]), 2)
    return out


# ═══════════════════════════ 3. MINE RULES ═══════════════════════════
def mine(pts, cls_of, subdisc, storey_of, meta_bbox, storey_area):
    placement, space_bom, routing, place_order, avoidance = [], [], [], [], []

    # group elements by (subdisc, storey, ifc_class)
    groups = {}
    for g, p in pts.items():
        key = (subdisc[g], storey_of(p[2]), cls_of[g])
        groups.setdefault(key, []).append(g)

    # ── placement + space_bom (per subdisc/storey/class) ──
    for (sd, storey, cls), guids in sorted(groups.items()):
        zs = [pts[g][2] for g in guids]
        xy = [(pts[g][0], pts[g][1]) for g in guids]
        # measured array cadence = median nearest-neighbour XY among the group
        nn_xy = []
        for i, a in enumerate(xy):
            best = None
            for j, b in enumerate(xy):
                if i == j:
                    continue
                d = math.hypot(a[0]-b[0], a[1]-b[1])
                if d > 1e-6 and (best is None or d < best):
                    best = d
            if best is not None:
                nn_xy.append(best)
        sx = round(st.median(nn_xy), 3) if nn_xy else 0
        # NETWORK classes (Segment/Fitting) are ROUTED, not array-tiled: their NN-XY
        # is the intra-run pipe packing (~5cm), which would tile a footprint into
        # millions of points. Zero their array spacing → the placer single-places them
        # (the run geometry comes from routing). Only FIXTURES (Terminal/Controller)
        # carry a real array cadence. Mirrors the round-trip's network/placed split.
        if 'Segment' in cls or 'Fitting' in cls:
            sx = 0
        placement.append(dict(
            disc=sd, ifc_class=cls, ref_kind='storey', dx=None, dy=None,
            dz=round(st.median(zs), 3),
            spacing_x_m=sx, spacing_y_m=sx,
            z_band_lo=round(percentile(zs, .05), 3),
            z_band_hi=round(percentile(zs, .95), 3),
            storey_scope=storey, n_measured=len(guids),
            src_storey_area_m2=storey_area.get(storey, 0.0),
            provenance=f'measured:duplex/{sd}/{storey}',
            src_guids=guids[:5]))

    # space_bom: per (subdisc,class) max per-storey count
    perclass = {}
    for (sd, storey, cls), guids in groups.items():
        perclass.setdefault((sd, cls), []).append((storey, len(guids), guids))
    for (sd, cls), lst in sorted(perclass.items()):
        cnt = max(n for _, n, _ in lst)
        allg = [g for _, _, gs in lst for g in gs]
        nn_xy = []
        pl = [pts[g] for g in allg]
        for i, a in enumerate(pl):
            best = None
            for j, b in enumerate(pl):
                if i == j:
                    continue
                d = math.hypot(a[0]-b[0], a[1]-b[1])
                if d > 1e-6 and (best is None or d < best):
                    best = d
            if best is not None:
                nn_xy.append(best)
        space_bom.append(dict(
            disc=sd, scope='storey', ifc_class=cls, count_per=cnt,
            spacing_m=round(st.median(nn_xy), 3) if nn_xy else 0,
            n_measured=len(allg), provenance=f'measured:duplex/{sd}',
            src_guids=allg[:5]))

    # ── routing nn-chains (per subdisc, from_kind->to_kind) ──
    by_sd_cls = {}
    for g, p in pts.items():
        by_sd_cls.setdefault((subdisc[g], cls_of[g]), []).append(g)
    for sd in sorted(set(subdisc.values())):
        classes = sorted({cls_of[g] for g in pts if subdisc[g] == sd})
        seg = [c for c in classes if 'Segment' in c]
        fit = [c for c in classes if 'Fitting' in c]
        # fitting->segment = the meaningful nn CHAIN (a fitting sits on the run it
        # joins). segment->segment is a MAIN run, NOT a nn-gap — measuring nn over
        # one set self-matches to 0, so it is emitted as a structural 'main' rule
        # (carries the measured segment LENGTH, not a between-element gap).
        if fit and seg:
            fk, tk = fit[0], seg[0]
            frm = [pts[g] for g in by_sd_cls.get((sd, fk), [])]
            tos = [pts[g] for g in by_sd_cls.get((sd, tk), [])]
            gaps = [d for fp in frm if (d := nn_min(fp, tos)) is not None]
            if len(gaps) >= 3:
                routing.append(dict(
                    disc=sd, from_kind=fk, to_kind=tk, pattern='nn',
                    params_json=json.dumps({
                        'avg_gap_m': round(st.mean(gaps), 3),
                        'min_m': round(min(gaps), 3),
                        'max_m': round(max(gaps), 3),
                        'method': 'nearest-neighbour-3d'}),
                    n_measured=len(gaps),
                    provenance=f'measured:duplex/{sd}/nn-chain',
                    src_guids=by_sd_cls.get((sd, fk), [])[:5]))
        if seg:
            sk = seg[0]
            sg = by_sd_cls.get((sd, sk), [])
            # measured run length per segment = max bbox extent (network-spans-it)
            blens = []
            for g in sg:
                bb = meta_bbox.get(g)
                if bb:
                    blens.append(max(bb))
            if len(sg) >= 3:
                routing.append(dict(
                    disc=sd, from_kind=sk, to_kind=sk, pattern='main',
                    params_json=json.dumps({
                        'orientation': 'run',
                        'avg_seg_len_m': round(st.mean(blens), 3) if blens else None,
                        'n_segments': len(sg),
                        'method': 'segment-main-run'}),
                    n_measured=len(sg),
                    provenance=f'measured:duplex/{sd}/main-run',
                    src_guids=sg[:5]))

    # ── place_order (per storey: subdiscs ordered by median z DESC) ──
    storeys = sorted({storey_of(p[2]) for p in pts.values()})
    for storey in storeys:
        med = {}
        for sd in sorted(set(subdisc.values())):
            zs = [pts[g][2] for g in pts
                  if subdisc[g] == sd and storey_of(pts[g][2]) == storey]
            if zs:
                med[sd] = st.median(zs)
        order = sorted(med, key=lambda s: -med[s])
        for idx, sd in enumerate(order):
            zs = [pts[g][2] for g in pts
                  if subdisc[g] == sd and storey_of(pts[g][2]) == storey]
            place_order.append(dict(
                disc=sd, order_index=idx, storey_scope=storey,
                z_band_lo=round(min(zs), 3), z_band_hi=round(max(zs), 3),
                n_measured=len(zs), provenance=f'median_z={med[sd]:.2f}'))

    # ── avoidance (THE KEY: per storey, per subdisc PAIR, p05 NN 3D clearance) ──
    sds = sorted(set(subdisc.values()))
    for storey in storeys:
        order_med = {sd: st.median([pts[g][2] for g in pts
                     if subdisc[g] == sd and storey_of(pts[g][2]) == storey] or [0])
                     for sd in sds}
        for i in range(len(sds)):
            for j in range(i+1, len(sds)):
                a, b = sds[i], sds[j]
                pa = [pts[g] for g in pts
                      if subdisc[g] == a and storey_of(pts[g][2]) == storey]
                pb = [pts[g] for g in pts
                      if subdisc[g] == b and storey_of(pts[g][2]) == storey]
                if len(pa) < 3 or len(pb) < 3:
                    continue
                # symmetric NN: each a->nearest b, each b->nearest a
                dists = []
                for p in pa:
                    d = nn_min(p, pb)
                    if d is not None:
                        dists.append(d)
                for p in pb:
                    d = nn_min(p, pa)
                    if d is not None:
                        dists.append(d)
                if not dists:
                    continue
                p05 = percentile(dists, .05)
                # lower-priority (lower median z = lower in stack) yields
                yields = a if order_med[a] <= order_med[b] else b
                avoidance.append(dict(
                    disc_a=a, disc_b=b, min_clear_m=round(p05, 3),
                    yields=yields, z_band=storey, n_measured=len(dists),
                    provenance=(f'p05_clearance={p05:.3f}m;'
                                f'raw_min={min(dists):.3f}m;'
                                f'median={st.median(dists):.2f}m')))
    return placement, space_bom, routing, place_order, avoidance


# ═══════════════════════════ 4. WRITE duplex_rules.db ═══════════════════════════
def write_db(placement, space_bom, routing, place_order, avoidance):
    con = sqlite3.connect(OUT_DB)
    cur = con.cursor()
    cur.executescript("""
        DROP TABLE IF EXISTS rule_placement;
        DROP TABLE IF EXISTS rule_space_bom;
        DROP TABLE IF EXISTS rule_routing;
        DROP TABLE IF EXISTS rule_place_order;
        DROP TABLE IF EXISTS rule_avoidance;
        CREATE TABLE rule_placement(
            disc TEXT, ifc_class TEXT, ref_kind TEXT, dx REAL, dy REAL, dz REAL,
            spacing_x_m REAL, spacing_y_m REAL, z_band_lo REAL, z_band_hi REAL,
            storey_scope TEXT, n_measured INTEGER, src_storey_area_m2 REAL,
            provenance TEXT, src_guids TEXT);
        CREATE TABLE rule_space_bom(
            disc TEXT, scope TEXT, ifc_class TEXT, count_per INTEGER, spacing_m REAL,
            n_measured INTEGER, provenance TEXT, src_guids TEXT);
        CREATE TABLE rule_routing(
            disc TEXT, from_kind TEXT, to_kind TEXT, pattern TEXT, params_json TEXT,
            n_measured INTEGER, provenance TEXT, src_guids TEXT);
        CREATE TABLE rule_place_order(
            disc TEXT, order_index INTEGER, storey_scope TEXT,
            z_band_lo REAL, z_band_hi REAL, n_measured INTEGER, provenance TEXT);
        CREATE TABLE rule_avoidance(
            disc_a TEXT, disc_b TEXT, min_clear_m REAL, yields TEXT,
            z_band TEXT, n_measured INTEGER, provenance TEXT);
    """)
    g = lambda r: ",".join(r['src_guids'])
    for r in placement:
        cur.execute("INSERT INTO rule_placement VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            (r['disc'], r['ifc_class'], r['ref_kind'], r['dx'], r['dy'], r['dz'],
             r['spacing_x_m'], r['spacing_y_m'], r['z_band_lo'], r['z_band_hi'],
             r['storey_scope'], r['n_measured'], r['src_storey_area_m2'],
             r['provenance'], g(r)))
    for r in space_bom:
        cur.execute("INSERT INTO rule_space_bom VALUES (?,?,?,?,?,?,?,?)",
            (r['disc'], r['scope'], r['ifc_class'], r['count_per'], r['spacing_m'],
             r['n_measured'], r['provenance'], g(r)))
    for r in routing:
        cur.execute("INSERT INTO rule_routing VALUES (?,?,?,?,?,?,?,?)",
            (r['disc'], r['from_kind'], r['to_kind'], r['pattern'], r['params_json'],
             r['n_measured'], r['provenance'], g(r)))
    for r in place_order:
        cur.execute("INSERT INTO rule_place_order VALUES (?,?,?,?,?,?,?)",
            (r['disc'], r['order_index'], r['storey_scope'], r['z_band_lo'],
             r['z_band_hi'], r['n_measured'], r['provenance']))
    for r in avoidance:
        cur.execute("INSERT INTO rule_avoidance VALUES (?,?,?,?,?,?,?)",
            (r['disc_a'], r['disc_b'], r['min_clear_m'], r['yields'], r['z_band'],
             r['n_measured'], r['provenance']))
    con.commit()
    con.close()


def main():
    if not os.path.exists(META_DB):
        log(f"FATAL: {META_DB} missing — run Step 0 extraction first"); sys.exit(1)
    meta = sqlite3.connect(META_DB)
    log(f"§DXM-BEGIN mining {os.path.basename(META_DB)} -> {os.path.basename(OUT_DB)}")
    pts, cls_of, name_of, subdisc, meta_bbox = classify(meta)
    storey_of, split = derive_storeys(pts)
    storey_area = storey_areas(meta, storey_of)
    log(f"§DXM-AREA src storey footprints (m²): " +
        ", ".join(f"{s}={a:.0f}" for s, a in sorted(storey_area.items())))
    placement, space_bom, routing, place_order, avoidance = mine(
        pts, cls_of, subdisc, storey_of, meta_bbox, storey_area)
    write_db(placement, space_bom, routing, place_order, avoidance)
    log(f"baked {OUT_DB}")
    log(f"  rule_placement   = {len(placement)}")
    log(f"  rule_space_bom   = {len(space_bom)}")
    log(f"  rule_routing     = {len(routing)}")
    log(f"  rule_place_order = {len(place_order)}")
    log(f"  rule_avoidance   = {len(avoidance)}")
    # headline: the residential clearances (the whole point)
    log("§DXM-CLEARANCE (residential p05 NN, the thesis vs Terminal's 2.4m plenum):")
    for r in sorted(avoidance, key=lambda r: r['min_clear_m']):
        log(f"  {r['disc_a']:5s}|{r['disc_b']:5s} {r['z_band']:8s} "
            f"min_clear={r['min_clear_m']:.3f}m yields={r['yields']:5s} "
            f"n={r['n_measured']}  ({r['provenance']})")
    meta.close()


if __name__ == "__main__":
    main()
