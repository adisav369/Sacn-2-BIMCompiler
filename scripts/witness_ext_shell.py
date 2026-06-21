#!/usr/bin/env python3
# ⚠ DO NOT REMOVE — WITNESS ONLY (no render). Scope: PROVE the "exterior-visible shell" set maths
# before any viewer code is touched (prompts discussion 2026-06-07). Read the §-log after every run;
# exit code is not evidence. This computes WHICH elements form the outward-facing shell via a pure
# bbox voxel flood-fill ("air reachable from outside the building"), and validates the set against
# element-name ground truth. It renders nothing and ports 1:1 to navigate_find.js later.
#
# Algorithm (language-agnostic, float == JS double):
#   1. AABB per element from element_transforms (bbox_* are FULL dims → half = /2).
#   2. Voxelize: mark every cell an AABB overlaps as OCCUPIED (overlap-mark, not point-sample → thin
#      walls seal, no flood leak).
#   3. Flood FREE space inward from the padded border (6-conn) → "exterior air".
#   4. A cell is SHELL if OCCUPIED and face-adjacent to exterior air.
#   5. Element is EXTERIOR if any of its cells is a shell cell.
# Acceptance (the maths is right iff): Exterior-named walls mostly IN; Interior/Partition walls mostly
# OUT; MEP (pipe/duct ~37k) almost all OUT; total exterior is a small fraction of 63k.

import sqlite3, sys, time
import numpy as np

DB = sys.argv[1] if len(sys.argv) > 1 else '/home/red1/bim-compiler/deploy/buildings/Hospital_extracted.db'
GRID_N = int(sys.argv[2]) if len(sys.argv) > 2 else 160   # target max cells on longest axis (tunable)
BIG_FRAC = 0.9                                            # skip artifact elements bigger than this * span

def log(tag, msg): print(f'§{tag} {msg}', flush=True)

t0 = time.time()
c = sqlite3.connect(DB)
rows = c.execute("""SELECT t.guid, t.center_x, t.center_y, t.center_z, t.bbox_x, t.bbox_y, t.bbox_z,
                           m.ifc_class, m.element_name
                    FROM element_transforms t LEFT JOIN elements_meta m ON m.guid = t.guid
                    WHERE t.center_x IS NOT NULL""").fetchall()
N = len(rows)
log('EXT_LOAD', f'db={DB.split("/")[-1]} elements={N} grid_target={GRID_N}')

# --- AABBs ---
cx = np.array([r[1] for r in rows]); cy = np.array([r[2] for r in rows]); cz = np.array([r[3] for r in rows])
hx = np.array([(r[4] or 0)/2 for r in rows]); hy = np.array([(r[5] or 0)/2 for r in rows]); hz = np.array([(r[6] or 0)/2 for r in rows])
mn = np.stack([cx-hx, cy-hy, cz-hz], 1); mx = np.stack([cx+hx, cy+hy, cz+hz], 1)

gmin = mn.min(0); gmax = mx.max(0); span = gmax - gmin
# skip degenerate/artifact elements (bbox spanning ~the whole building → would seal the grid)
big = ((mx - mn) > BIG_FRAC * span).any(1)
log('EXT_BBOX', f'span={span.round(1).tolist()} artifacts_skipped={int(big.sum())} (bbox>{BIG_FRAC}*span)')

cs = span.max() / GRID_N                       # cube cell size
dims = np.ceil(span / cs).astype(int) + 3      # +pad (1 free border each side, +1 slack)
log('EXT_GRID', f'cell_size={cs:.3f}m dims={dims.tolist()} cells={int(np.prod(dims))}')

occ = np.zeros(dims, bool)
def cell(p): return np.clip(((p - gmin)/cs).astype(int) + 1, 0, dims-1)   # +1 pad offset
i0 = cell(mn); i1 = cell(mx)
for k in range(N):
    if big[k]: continue
    a, b = i0[k], i1[k]
    occ[a[0]:b[0]+1, a[1]:b[1]+1, a[2]:b[2]+1] = True
log('EXT_OCC', f'occupied_cells={int(occ.sum())} ({100*occ.sum()/occ.size:.1f}% of grid)  t={time.time()-t0:.1f}s')

# --- flood FREE space from the border (6-conn) ---
free = ~occ
reached = np.zeros(dims, bool)
reached[0,:,:] = free[0,:,:]; reached[-1,:,:] = free[-1,:,:]
reached[:,0,:] |= free[:,0,:]; reached[:,-1,:] |= free[:,-1,:]
reached[:,:,0] |= free[:,:,0]; reached[:,:,-1] |= free[:,:,-1]
def dilate(m):
    d = m.copy()
    d[1:,:,:] |= m[:-1,:,:]; d[:-1,:,:] |= m[1:,:,:]
    d[:,1:,:] |= m[:,:-1,:]; d[:,:-1,:] |= m[:,1:,:]
    d[:,:,1:] |= m[:,:,:-1]; d[:,:,:-1] |= m[:,:,1:]
    return d
depth = np.full(dims, -1, np.int32); depth[reached] = 0
it = 0
while True:
    nxt = dilate(reached) & free
    it += 1
    newly = nxt & ~reached
    if not newly.any(): break
    depth[newly] = it
    reached = nxt
log('EXT_FLOOD', f'iterations={it} exterior_air_cells={int(reached.sum())}  t={time.time()-t0:.1f}s')

# --- shell cells = occupied AND adjacent to exterior air ---
shell = occ & dilate(reached)
log('EXT_SHELL', f'shell_cells={int(shell.sum())}')

# --- classify each element ---
ext = np.zeros(N, bool)
for k in range(N):
    if big[k]: continue
    a, b = i0[k], i1[k]
    ext[k] = shell[a[0]:b[0]+1, a[1]:b[1]+1, a[2]:b[2]+1].any()
log('EXT_RESULT', f'EXTERIOR={int(ext.sum())} / {N}  ({100*ext.sum()/N:.1f}%)  INTERIOR_dropped={N-int(ext.sum())}  t={time.time()-t0:.1f}s')

# --- class breakdown ---
from collections import Counter
cls = [r[7] or '?' for r in rows]
tot = Counter(cls); ex = Counter(c for c,e in zip(cls, ext) if e)
log('EXT_BYCLASS', 'class  exterior/total  %in')
for cl,_ in tot.most_common(16):
    print(f'    {cl:28s} {ex.get(cl,0):5d}/{tot[cl]:<6d} {100*ex.get(cl,0)/tot[cl]:5.1f}%')

# --- GROUND-TRUTH accuracy from element names ---
def frac(pred):
    idx = [k for k in range(N) if pred(rows[k])]
    return (sum(ext[k] for k in idx), len(idx))
def wall(r): return (r[7] or '').startswith('IfcWall')
def named(r, kw): return wall(r) and kw.lower() in (r[8] or '').lower()
ein, en = frac(lambda r: named(r,'Exterior'))
iin, ino = frac(lambda r: named(r,'Interior'))
pin, pn  = frac(lambda r: named(r,'Partition'))
mep, mept = frac(lambda r: (r[7] or '').startswith(('IfcPipe','IfcDuct')))
fur, furt = frac(lambda r: (r[7] or '') in ('IfcFurniture','IfcLightFixture'))
log('EXT_GROUNDTRUTH', 'cohort                 marked_exterior   verdict')
print(f"    Exterior-named walls   {ein}/{en} = {100*ein/max(en,1):.0f}%   (want HIGH)")
print(f"    Interior-named walls   {iin}/{ino} = {100*iin/max(ino,1):.0f}%   (want LOW)")
print(f"    Partition-named walls  {pin}/{pn} = {100*pin/max(pn,1):.0f}%   (want LOW)")
print(f"    MEP pipes+ducts        {mep}/{mept} = {100*mep/max(mept,1):.1f}%  (want ~0)")
print(f"    Furniture+lights       {fur}/{furt} = {100*fur/max(furt,1):.0f}%   (want LOW)")
# ============================================================================
# METHOD 2 — DIRECTIONAL FIRST-HIT (line-of-sight visibility, not air-reachability).
# A ray from far outside stops at the FIRST occupied cell along its column → interior walls
# behind the facade are occluded (can't snake through doors like the flood does). 6 axis faces.
# ============================================================================
def first_hit_shell(occ):
    vis = np.zeros_like(occ)
    for ax in range(3):
        o = np.moveaxis(occ, ax, 0); v = np.moveaxis(vis, ax, 0)   # moveaxis = view → writes hit vis
        anyo = o.any(0); Y, Z = np.indices(anyo.shape)
        flo = o.argmax(0)                       # first occupied from low side
        v[flo[anyo], Y[anyo], Z[anyo]] = True
        fhi = (o.shape[0]-1) - o[::-1].argmax(0)  # first occupied from high side
        v[fhi[anyo], Y[anyo], Z[anyo]] = True
    return vis

vshell = first_hit_shell(occ)
extd = np.zeros(N, bool)
for k in range(N):
    if big[k]: continue
    a, b = i0[k], i1[k]
    extd[k] = vshell[a[0]:b[0]+1, a[1]:b[1]+1, a[2]:b[2]+1].any()
log('DIR_RESULT', f'EXTERIOR(6-axis)={int(extd.sum())} / {N}  ({100*extd.sum()/N:.1f}%)')
def fracd(pred):
    idx = [k for k in range(N) if pred(rows[k])]
    return (sum(extd[k] for k in idx), len(idx))
ein2,en2 = fracd(lambda r: named(r,'Exterior')); iin2,ino2 = fracd(lambda r: named(r,'Interior'))
pin2,pn2 = fracd(lambda r: named(r,'Partition')); mep2,mept2 = fracd(lambda r: (r[7] or '').startswith(('IfcPipe','IfcDuct')))
log('DIR_GROUNDTRUTH', 'cohort                 marked_exterior   verdict')
print(f"    Exterior-named walls   {ein2}/{en2} = {100*ein2/max(en2,1):.0f}%   (want HIGH)")
print(f"    Interior-named walls   {iin2}/{ino2} = {100*iin2/max(ino2,1):.0f}%   (want LOW)")
print(f"    Partition-named walls  {pin2}/{pn2} = {100*pin2/max(pn2,1):.0f}%   (want LOW)")
print(f"    MEP pipes+ducts        {mep2}/{mept2} = {100*mep2/max(mept2,1):.1f}%  (want ~0)")

# ============================================================================
# METHOD 3 — DEPTH-LIMITED FLOOD: keep only elements touching SHALLOW exterior air (within K cells,
# geodesic, of the outside). Facade walls touch depth~0-1 air → kept (high recall). Interior walls
# reached only deep through doorways/corridors → pruned (low leak). Best of both, ports as flood+threshold.
# ============================================================================
log('DEPTHK_SWEEP', 'K_cells K_m  exterior  ExtWall%(hi) IntWall%(lo) Part%(lo) MEP%(~0)')
def fr(e, pred):
    idx = [k for k in range(N) if pred(rows[k])]
    return (sum(e[k] for k in idx), len(idx))
best = None
for K in [2, 3, 4, 6, 10, 99]:
    air = reached & (depth >= 0) & (depth <= K)
    sh = occ & dilate(air)
    e = np.zeros(N, bool)
    for k in range(N):
        if big[k]: continue
        a, b = i0[k], i1[k]
        e[k] = sh[a[0]:b[0]+1, a[1]:b[1]+1, a[2]:b[2]+1].any()
    ew, ewt = fr(e, lambda r: named(r,'Exterior')); iw, iwt = fr(e, lambda r: named(r,'Interior'))
    pw, pwt = fr(e, lambda r: named(r,'Partition')); mp, mpt = fr(e, lambda r:(r[7] or '').startswith(('IfcPipe','IfcDuct')))
    exP, inP = 100*ew/max(ewt,1), 100*iw/max(iwt,1)
    print(f'    K={K:2d} {K*cs:4.1f}m {int(e.sum()):6d}    {exP:5.0f}%      {inP:5.0f}%     {100*pw/max(pwt,1):5.0f}%    {100*mp/max(mpt,1):4.1f}%')
    if exP >= 85 and inP <= 18 and (best is None or inP < best[1]): best = (K, inP, exP)
log('DEPTHK_BEST', f'{best}  (target: ExtWall>=85% AND IntWall<=18%)')
# ============================================================================
# METHOD 4 — MULTI-DIRECTION Z-BUFFER (the real "rays from many outside angles" over bboxes).
# For each view direction: project every element's bbox to the screen plane, keep the NEAREST per
# pixel (a z-buffer) → first-hit = visible from that angle. Union over 14 dirs (6 faces + 8 corners)
# catches oblique facades the 6-axis missed, while line-of-sight still occludes the interior.
# ============================================================================
RES = int(sys.argv[sys.argv.index('res')+1]) if 'res' in sys.argv else 320
H = np.stack([hx, hy, hz], 1); C = np.stack([cx, cy, cz], 1)
dirs = []
for ax in [(1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)]: dirs.append(np.array(ax, float))   # 6 faces
for sx in (1,-1):                                                                                    # 8 corners
    for sy in (1,-1):
        for sz in (1,-1): dirs.append(np.array([sx,sy,sz], float)/np.sqrt(3))
for a,b,c0 in [(1,1,0),(1,-1,0),(0,1,1),(0,1,-1),(1,0,1),(1,0,-1)]:                                  # 12 edges
    for s in (1,-1): dirs.append(np.array([a*s,b*s,c0*s], float)/np.sqrt(a*a+b*b+c0*c0))
vis_any = np.zeros(N, bool)
for d in dirs:
    up = np.array([0,0,1.0]) if abs(d[2]) < 0.9 else np.array([0,1.0,0])
    u = np.cross(up, d); u /= np.linalg.norm(u); v = np.cross(d, u)
    su = C @ u; sv = C @ v; dep = C @ d                       # screen coords + depth
    ru = H @ np.abs(u); rv = H @ np.abs(v)                     # projected AABB half-extents
    u0, u1, v0, v1 = su.min()-ru.max(), su.max()+ru.max(), sv.min()-rv.max(), sv.max()+rv.max()
    def to_px(s, lo, hi): return np.clip(((s-lo)/(hi-lo)*RES).astype(int), 0, RES-1)
    iu0 = to_px(su-ru,u0,u1); iu1 = to_px(su+ru,u0,u1); iv0 = to_px(sv-rv,v0,v1); iv1 = to_px(sv+rv,v0,v1)
    zbuf = np.full((RES,RES), np.inf)
    order = np.argsort(dep)                                    # near→far; first paint wins
    for k in order:
        if big[k]: continue
        zbuf[iu0[k]:iu1[k]+1, iv0[k]:iv1[k]+1] = np.minimum(zbuf[iu0[k]:iu1[k]+1, iv0[k]:iv1[k]+1], dep[k])
    for k in range(N):
        if big[k] or vis_any[k]: continue
        if (dep[k] <= zbuf[iu0[k]:iu1[k]+1, iv0[k]:iv1[k]+1] + 1e-6).any(): vis_any[k] = True
log('RAY14_RESULT', f'EXTERIOR(14-dir)={int(vis_any.sum())} / {N}  ({100*vis_any.sum()/N:.1f}%)  t={time.time()-t0:.1f}s')
ew,ewt = fr(vis_any, lambda r: named(r,'Exterior')); iw,iwt = fr(vis_any, lambda r: named(r,'Interior'))
pw,pwt = fr(vis_any, lambda r: named(r,'Partition')); mp,mpt = fr(vis_any, lambda r:(r[7] or '').startswith(('IfcPipe','IfcDuct')))
fu,fut = fr(vis_any, lambda r:(r[7] or '') in ('IfcFurniture','IfcLightFixture'))
log('RAY14_GROUNDTRUTH', 'cohort')
print(f"    Exterior-named walls   {ew}/{ewt} = {100*ew/max(ewt,1):.0f}%   (want HIGH)")
print(f"    Interior-named walls   {iw}/{iwt} = {100*iw/max(iwt,1):.0f}%   (want LOW)")
print(f"    Partition-named walls  {pw}/{pwt} = {100*pw/max(pwt,1):.0f}%   (want LOW)")
print(f"    MEP pipes+ducts        {mp}/{mpt} = {100*mp/max(mpt,1):.1f}%  (want ~0)")
print(f"    Furniture+lights       {fu}/{fut} = {100*fu/max(fut,1):.0f}%   (want LOW)")
if 'dump' in sys.argv:
    import json, os
    guids = [rows[k][0] for k in range(N) if vis_any[k]]
    out = '/tmp/wt-veil-strip/viewer/ext_shell_preview.js'
    bld = os.path.basename(DB).split('_')[0]
    with open(out, 'w') as f:
        f.write('// PREVIEW ONLY (witness dump, not committed) — exterior-visible guid set for the ghost shell\n')
        f.write('window.__EXT_SHELL = window.__EXT_SHELL || {};\n')
        f.write('window.__EXT_SHELL[%s] = %s;\n' % (json.dumps(bld), json.dumps(guids)))
    log('DUMP', f'wrote {len(guids)} guids for "{bld}" -> {out}')

log('EXT_DONE', f'total_time={time.time()-t0:.1f}s  — read the §-log above; SET proof only, render look still needs an eyeball')
