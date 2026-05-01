# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
layout_audit.py — DXF layout quality checker.

Reads every DXF in output/DXF/, runs geometric checks, writes
output/layout_audit.txt with PASS/WARN/FAIL per issue.

Checks (all threshold values come from drawing_template.json):
  T01  Text-text overlap         — any two text bboxes intersect
  T02  Text bleeds outside border — text bbox exceeds sheet border
  T03  Text in title block zone  — drawing-side text inside panel x-range
  T04  Dim text crowding         — dim text bbox < crowding_threshold_mm apart
  G01  Grid count                — x or y grids > max_grids_per_axis
  G02  Grid bay too small        — any bay < min_bay_mm (paper mm)
  P01  Paper aspect ratio        — paper w/h outside expected range
  P02  Building fill ratio       — building bbox > max_fill of content area
  L01  Level marker clip         — level marker text x < border x + clearance
  S01  Schedule overflow         — schedule bottom overlaps field rows top
"""

import os, sys, json, math
import ezdxf

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DXF_DIR    = os.path.join(SCRIPT_DIR, '..', 'output', 'DXF')
TPL_PATH   = os.path.join(SCRIPT_DIR, '..', 'drawing_template.json')
LOG_PATH   = os.path.join(SCRIPT_DIR, '..', 'output', 'layout_audit.txt')

SCALE = 100   # model-space mm per paper mm

# ── thresholds (all overridable from template under "layout_audit" key) ──
DEFAULTS = {
    'text_overlap_tolerance_mm':   0.5,   # paper mm — allow tiny overlap from rounding
    'text_border_clearance_mm':    1.0,   # paper mm — text must be >= this from border
    'text_panel_clearance_mm':     1.0,   # paper mm — drawing text must not enter panel
    'dim_crowding_threshold_mm':  10.0,   # paper mm — bay width below this = crowded
    'max_grids_per_axis':         8,      # more than this = over-gridded
    'min_bay_mm':                  5.0,   # paper mm — bay narrower than this is suspect
    'paper_min_aspect':            1.2,   # W/H — landscape min
    'paper_max_aspect':            4.0,   # W/H — landscape max
    'building_max_fill':           0.85,  # fraction of content area
    'level_marker_clearance_mm':   2.0,   # paper mm — level text must not touch border
    'title_block_layer':       'A-TTLB',
    'grid_layer':              'A-GRID',
    'anno_layer':              'A-ANNO-DIMS',
    'anno_text_layer':         'A-ANNO-TEXT',
    'level_layer':             'A-ANNO-LEVL',
}


def load_template():
    with open(TPL_PATH) as f:
        tpl = json.load(f)
    cfg = dict(DEFAULTS)
    cfg.update(tpl.get('layout_audit', {}))
    return tpl, cfg


def _paper_bounds(msp):
    """Return (x0,y0,x1,y1) of the layer-0 background rect = sheet boundary."""
    for e in msp:
        if e.dxf.layer == '0' and e.dxftype() == 'LWPOLYLINE':
            pts = list(e.get_points(format='xy'))
            if pts:
                xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
                return min(xs), min(ys), max(xs), max(ys)
    return None


def _border_bounds(msp):
    """Return (x0,y0,x1,y1) of the A-TTLB border rect (inner sheet boundary)."""
    best = None; best_area = 0
    for e in msp:
        if e.dxf.layer == 'A-TTLB' and e.dxftype() == 'LWPOLYLINE':
            pts = list(e.get_points(format='xy'))
            if len(pts) >= 4:
                xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
                w = max(xs)-min(xs); h = max(ys)-min(ys)
                area = w * h
                if area > best_area:
                    best_area = area
                    best = (min(xs), min(ys), max(xs), max(ys))
    return best


def _title_block_left(msp):
    """Return x of the vertical A-TTLB line that separates content from panel."""
    # It's the A-TTLB LINE with x1==x2 (vertical) closest to right side of border
    border = _border_bounds(msp)
    if not border:
        return None
    bx0, by0, bx1, by1 = border
    best_x = None; best_dist = float('inf')
    for e in msp:
        if e.dxf.layer == 'A-TTLB' and e.dxftype() == 'LINE':
            s = e.dxf.start; en = e.dxf.end
            if abs(s[0] - en[0]) < 1:   # vertical line
                x = (s[0] + en[0]) / 2
                if bx0 < x < bx1:        # within border
                    dist = bx1 - x
                    if dist < best_dist:
                        best_dist = dist
                        best_x = x
    return best_x


def _text_bbox(e, sc=SCALE):
    """Return (x0_mm, y0_mm, x1_mm, y1_mm) paper-mm bbox for a TEXT entity.
    Approximation: width = height * char_count * 0.6."""
    try:
        x  = e.dxf.insert[0] / sc
        y  = e.dxf.insert[1] / sc
        h  = e.dxf.height    / sc
        txt = e.dxf.text or ''
        w   = h * max(len(txt), 1) * 0.6
        # alignment adjust — MIDDLE_CENTER is most common
        al  = getattr(e.dxf, 'halign', 0)   # 0=left,1=center,2=right,4=middle
        vl  = getattr(e.dxf, 'valign', 0)   # 0=baseline,2=middle
        if al == 4:   # MIDDLE_CENTER stored as halign=4 in ezdxf attribs? check:
            x -= w/2; y -= h/2
        elif al == 1:
            x -= w/2; y -= 0
        elif al == 2:
            x -= w;   y -= 0
        # Use align_point if present (set_placement stores it there)
        try:
            ap = e.dxf.align_point
            ax = ap[0] / sc; ay = ap[1] / sc
            # override x/y with align_point as center
            x = ax - w/2; y = ay - h/2
        except Exception:
            pass
        return (x, y, x+w, y+h)
    except Exception:
        return None


def _get_texts(msp, layers=None, sc=SCALE):
    """Return list of (layer, text_str, bbox) for all TEXT entities."""
    out = []
    for e in msp:
        if e.dxftype() != 'TEXT':
            continue
        lyr = e.dxf.layer
        if layers and lyr not in layers:
            continue
        bb = _text_bbox(e, sc)
        if bb:
            out.append((lyr, e.dxf.text or '', bb))
    return out


def _grid_lines(msp, cfg):
    """Return (x_positions_mm, y_positions_mm) of grid lines."""
    glayer = cfg['grid_layer']
    xs = set(); ys = set()
    for e in msp:
        if e.dxf.layer != glayer:
            continue
        if e.dxftype() == 'LINE':
            s = e.dxf.start; en = e.dxf.end
            if abs(s[0]-en[0]) < 1:   # vertical grid line
                xs.add(round((s[0]+en[0])/2 / SCALE, 2))
            if abs(s[1]-en[1]) < 1:   # horizontal grid line
                ys.add(round((s[1]+en[1])/2 / SCALE, 2))
    return sorted(xs), sorted(ys)


def _bboxes_overlap(a, b, tol=0):
    """True if two (x0,y0,x1,y1) bboxes overlap (minus tolerance)."""
    return (a[0] < b[2]-tol and a[2] > b[0]+tol and
            a[1] < b[3]-tol and a[3] > b[1]+tol)


def audit_dxf(dxf_path, tpl, cfg, log):
    name = os.path.basename(dxf_path)
    doc  = ezdxf.readfile(dxf_path)
    msp  = doc.modelspace()
    sc   = SCALE

    issues = []
    info   = []

    def warn(code, msg):  issues.append(f'  WARN {code}  {msg}')
    def fail(code, msg):  issues.append(f'  FAIL {code}  {msg}')
    def ok(code, msg):    info.append(  f'  PASS {code}  {msg}')

    # ── Geometry anchors ──
    paper  = _paper_bounds(msp)
    border = _border_bounds(msp)
    tb_x   = _title_block_left(msp)

    if not paper:
        fail('P00', 'No layer-0 background rect found — cannot measure sheet')
        log.write(f'\n{name}\n' + '\n'.join(issues+info) + '\n')
        return

    px0, py0, px1, py1 = [v/sc for v in paper]    # paper mm
    pw = px1-px0; ph = py1-py0
    info.append(f'  INFO SHEET  paper={pw:.1f}x{ph:.1f}mm  aspect={pw/ph:.2f}')

    bx0=bx1=by0=by1=None
    if border:
        bx0,by0,bx1,by1 = [v/sc for v in border]

    tb_x_mm = tb_x/sc if tb_x else None

    # P01 — paper aspect ratio
    aspect = pw / ph
    mn = cfg['paper_min_aspect']; mx = cfg['paper_max_aspect']
    if aspect < mn:
        fail('P01', f'Paper aspect {aspect:.2f} < {mn} — sheet is near-portrait (paper_min_aspect={mn})')
    elif aspect > mx:
        warn('P01', f'Paper aspect {aspect:.2f} > {mx} — unusually wide')
    else:
        ok('P01', f'Aspect ratio {aspect:.2f} within [{mn},{mx}]')

    # P02 — building fill ratio in content area
    # Estimate building bbox from A-WALL-FULL + A-WALL-PRTN layers
    bld_xs=[]; bld_ys=[]
    for e in msp:
        if e.dxf.layer in ('A-WALL-FULL','A-WALL-PRTN','A-GLAZ') and e.dxftype()=='LWPOLYLINE':
            for pt in e.get_points(format='xy'):
                bld_xs.append(pt[0]/sc); bld_ys.append(pt[1]/sc)
    if bld_xs and bx0 is not None and tb_x_mm is not None:
        bw = max(bld_xs)-min(bld_xs); bh = max(bld_ys)-min(bld_ys)
        content_w = tb_x_mm - bx0
        content_h = by1 - by0
        fill_x = bw / content_w if content_w > 0 else 0
        fill_y = bh / content_h if content_h > 0 else 0
        mx_fill = cfg['building_max_fill']
        if fill_y > mx_fill:
            fail('P02', f'Building Y fill {fill_y:.0%} > {mx_fill:.0%} of content area height ({bh:.0f}mm/{content_h:.0f}mm) — no room for annotation')
        elif fill_x > mx_fill:
            warn('P02', f'Building X fill {fill_x:.0%} > {mx_fill:.0%} of content width ({bw:.0f}mm/{content_w:.0f}mm)')
        else:
            ok('P02', f'Building fill x={fill_x:.0%} y={fill_y:.0%} within limit')

    # G01 — grid count per axis
    x_grids, y_grids = _grid_lines(msp, cfg)
    max_g = cfg['max_grids_per_axis']
    if len(x_grids) > max_g:
        fail('G01', f'X-axis grids={len(x_grids)} > max {max_g} — over-gridded (positions: {[round(x,1) for x in x_grids]})')
    else:
        ok('G01', f'X-axis grids={len(x_grids)}')
    if len(y_grids) > max_g:
        fail('G01', f'Y-axis grids={len(y_grids)} > max {max_g} — over-gridded (positions: {[round(y,1) for y in y_grids]})')
    else:
        ok('G01', f'Y-axis grids={len(y_grids)}')

    # G02 — minimum bay size (paper mm)
    min_bay = cfg['min_bay_mm']
    for gs, axis in [(x_grids,'X'), (y_grids,'Y')]:
        for i in range(len(gs)-1):
            bay = gs[i+1] - gs[i]
            if bay < min_bay:
                fail('G02', f'{axis}-axis bay {gs[i]:.1f}→{gs[i+1]:.1f} = {bay:.1f}mm paper < min {min_bay}mm — will crowd dim text')

    # T01 — text-text overlap
    tol = cfg['text_overlap_tolerance_mm']
    all_layers = {cfg['title_block_layer'], cfg['anno_layer'],
                  cfg['anno_text_layer'], cfg['level_layer'], 'A-ANNO-TEXT'}
    all_texts = _get_texts(msp, sc=sc)
    overlaps = 0
    for i, (la, ta, ba) in enumerate(all_texts):
        for j, (lb, tb2, bb) in enumerate(all_texts):
            if j <= i: continue
            if _bboxes_overlap(ba, bb, tol=tol):
                overlaps += 1
                if overlaps <= 12:   # cap log verbosity
                    warn('T01', f'Text overlap: "{ta[:20]}" ({la}) ↔ "{tb2[:20]}" ({lb})  '
                                f'bboxes ({ba[0]:.1f},{ba[1]:.1f})-({ba[2]:.1f},{ba[3]:.1f}) '
                                f'↔ ({bb[0]:.1f},{bb[1]:.1f})-({bb[2]:.1f},{bb[3]:.1f})')
    if overlaps == 0:
        ok('T01', 'No text-text overlaps detected')
    elif overlaps > 12:
        warn('T01', f'... and {overlaps-12} more overlaps (total={overlaps})')

    # T02 — text bleeds outside border
    clearance = cfg['text_border_clearance_mm']
    if bx0 is not None:
        bleed = 0
        for la, ta, ba in all_texts:
            if (ba[0] < bx0 - clearance or ba[2] > bx1 + clearance or
                ba[1] < by0 - clearance or ba[3] > by1 + clearance):
                bleed += 1
                if bleed <= 8:
                    warn('T02', f'Text "{ta[:20]}" ({la}) bbox ({ba[0]:.1f},{ba[1]:.1f})-({ba[2]:.1f},{ba[3]:.1f}) outside border ({bx0:.1f},{by0:.1f})-({bx1:.1f},{by1:.1f})')
        if bleed == 0:
            ok('T02', 'No text outside border')
        elif bleed > 8:
            warn('T02', f'... and {bleed-8} more outside-border texts (total={bleed})')

    # T03 — drawing-side text (non-title-block) inside title block zone
    if tb_x_mm is not None:
        intrude = 0
        drawing_layers = {cfg['anno_layer'], cfg['anno_text_layer'],
                          cfg['level_layer'], 'A-ANNO-TEXT', 'A-ANNO-LEVL',
                          'A-ANNO-DIMS', 'A-GRID'}
        for la, ta, ba in all_texts:
            if la not in drawing_layers:
                continue
            if ba[0] > tb_x_mm - cfg['text_panel_clearance_mm']:
                intrude += 1
                if intrude <= 8:
                    fail('T03', f'Drawing text "{ta[:20]}" ({la}) x0={ba[0]:.1f}mm enters title block zone (tb_left={tb_x_mm:.1f}mm)')
        if intrude == 0:
            ok('T03', 'No drawing text in title block zone')
        elif intrude > 8:
            fail('T03', f'... and {intrude-8} more intrusions (total={intrude})')

    # T04 — dimension text crowding: bay paper-width < threshold
    thr = cfg['dim_crowding_threshold_mm']
    crowded_bays = 0
    for gs, axis in [(x_grids,'X'),(y_grids,'Y')]:
        for i in range(len(gs)-1):
            bay = gs[i+1] - gs[i]
            if 0 < bay < thr:
                crowded_bays += 1
                warn('T04', f'{axis}-axis bay {gs[i]:.1f}→{gs[i+1]:.1f} = {bay:.1f}mm < crowding threshold {thr}mm — dim text will overlap')
    if crowded_bays == 0:
        ok('T04', 'No crowded dimension bays')

    # L01 — level marker text left-clipped
    lm_clear = cfg['level_marker_clearance_mm']
    if bx0 is not None:
        for la, ta, ba in all_texts:
            if la in ('A-ANNO-LEVL', 'A-ANNO-TEXT') and ba[0] < bx0 + lm_clear:
                warn('L01', f'Level text "{ta[:30]}" x0={ba[0]:.1f}mm < border+clearance ({bx0+lm_clear:.1f}mm) — clipped at left edge')

    # S01 — title block: warn only if a GENERIC default value co-exists with the identity block
    # (When sync_building_type_field=true the field IS the specific type — not a conflict)
    tb_texts = [t for la, t, ba in all_texts if la == cfg['title_block_layer']]
    _specific = {'DUPLEX RESIDENTIAL', 'SINGLE STOREY RESIDENTIAL'}
    has_bld_type_field = any(
        ('RESIDENTIAL' in t or 'RUMAH' in t) and t not in _specific
        for t in tb_texts)
    has_identity_block = any(t in tb_texts for t in _specific)
    if has_bld_type_field and has_identity_block:
        warn('S01', 'BUILDING TYPE field row shows generic value AND identity block shows specific type — redundant/conflicting')

    # SEM01 — §20: BIMSRC xdata coverage
    bimsrc_count = 0
    for e in msp:
        try:
            xd = e.get_xdata('BIMSRC')
            if xd:
                bimsrc_count += 1
        except Exception:
            pass
    if bimsrc_count > 0:
        ok('SEM01', f'{bimsrc_count} entities with BIMSRC xdata')
    else:
        # Only floor plans have BIMSRC currently — info, not fail
        info.append(f'  INFO SEM01  0 BIMSRC entities (non-floor view or pre-§20 DXF)')

    # Summary
    n_fail = sum(1 for l in issues if 'FAIL' in l)
    n_warn = sum(1 for l in issues if 'WARN' in l)
    summary = f'  → {n_fail} FAIL, {n_warn} WARN, {len(info)} PASS'

    log.write(f'\n{"="*60}\n{name}\n{"="*60}\n')
    log.write('\n'.join(info) + '\n')
    log.write('\n'.join(issues) + '\n')
    log.write(summary + '\n')


def main():
    tpl, cfg = load_template()
    dxfs = sorted(f for f in os.listdir(DXF_DIR) if f.endswith('.dxf'))
    # Keep only latest generation per view (last alphabetically = highest timestamp)
    latest = {}
    for f in dxfs:
        # e.g. DX_FLOOR_20260408_1622.dxf  → key = DX_FLOOR
        parts = f.replace('.dxf','').rsplit('_', 2)
        key = parts[0] if len(parts) >= 1 else f
        latest[key] = f
    dxfs_to_audit = sorted(latest.values())

    os.makedirs(os.path.dirname(LOG_PATH), exist_ok=True)
    with open(LOG_PATH, 'w') as log:
        log.write('layout_audit.py — DXF layout quality report\n')
        log.write(f'Checking {len(dxfs_to_audit)} DXF files in {DXF_DIR}\n')
        log.write(f'Thresholds: overlap_tol={cfg["text_overlap_tolerance_mm"]}mm  '
                  f'crowding={cfg["dim_crowding_threshold_mm"]}mm  '
                  f'max_grids={cfg["max_grids_per_axis"]}\n')
        total_fail = total_warn = 0
        for f in dxfs_to_audit:
            path = os.path.join(DXF_DIR, f)
            # capture per-file counts
            pre_pos = log.tell()
            audit_dxf(path, tpl, cfg, log)
            log.flush()
        log.write('\n\nDONE\n')
        log.write('\nCHECK PROBLEMS SECTION §18.1 IF LOGS PASSED THEM\n')
    print(f'Audit written to {LOG_PATH}')
    print('CHECK PROBLEMS SECTION §18.1 IF LOGS PASSED THEM')


if __name__ == '__main__':
    main()
