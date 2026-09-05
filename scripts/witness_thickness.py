#!/usr/bin/env python3
import sys
# --- utf8-console guard (2026-09-05) ---------------------------------------------
# This script prints non-ASCII (box-drawing, arrows, section marks). On a console whose
# encoding is not UTF-8 -- Windows cp1252 is the common case -- print() raises
# UnicodeEncodeError and kills the script mid-run. That is not hypothetical: it aborted
# scripts/restore_generative_meshes.py immediately after it created its back-compat view
# but BEFORE it restored any mesh, which is why the component_library repair silently
# needed two passes to converge. errors="replace" is deliberate: a mangled glyph in a log
# line is always better than a dead pipeline stage.
for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError, OSError):
        pass  # already-wrapped, detached, or replaced by a non-TextIOWrapper (e.g. in tests)
# ---------------------------------------------------------------------------------
# WITNESS: what THICKNESS do the ghost-shell meshes actually have? (per element = smallest local bbox dim).
# Also: is each mesh 2-sided (a solid with an inner+outer face = real thickness to shed) or a single surface
# (nothing to shed → explains "no diff")? And: does the current exterior SET even contain the roof?
import sqlite3, sys, json, numpy as np
DB = 'deploy/buildings/Hospital_extracted.db'
def log(t, m): print(f'§{t} {m}', flush=True)
c = sqlite3.connect(DB)
ful, fac = {}, {}
for h, vb in c.execute('SELECT geometry_hash, vertices FROM component_geometries'): ful[h] = np.frombuffer(vb, np.float32).reshape(-1,3)
for h, fb in c.execute('SELECT geometry_hash, faces FROM component_geometries'): fac[h] = np.frombuffer(fb, np.uint32).reshape(-1,3)
rows = c.execute('''SELECT i.guid,i.geometry_hash,m.ifc_class,m.element_name,t.center_z
    FROM element_instances i JOIN element_transforms t ON t.guid=i.guid JOIN elements_meta m ON m.guid=i.guid''').fetchall()

SHELL = ('IfcWall','IfcSlab','IfcRoof','IfcCovering','IfcCurtainWall','IfcPlate','IfcWallStandardCase')
def thickness(h):
    v = ful.get(h)
    if v is None or len(v) < 3: return None
    d = np.sort(v.max(0) - v.min(0))   # sorted local bbox dims; smallest = thickness
    return d[0]
# per-class thickness distribution
from collections import defaultdict
buckets = defaultdict(lambda: [0,0,0,0])  # surface<1cm, thin1-15, mid15-40, thick>40
samp = defaultdict(list)
for r in rows:
    cl = r[2] or '?'
    if not cl.startswith(SHELL): continue
    th = thickness(r[1])
    if th is None: continue
    samp[cl].append(th)
    b = buckets[cl]; b[0 if th<0.01 else 1 if th<0.15 else 2 if th<0.40 else 3] += 1
log('THK_BYCLASS', 'class                 n     median_thick  [surface<1cm | thin<15 | mid<40 | thick>40cm]')
for cl in sorted(samp, key=lambda k:-len(samp[k])):
    b = buckets[cl]; med = np.median(samp[cl])
    print(f'    {cl:22s} {len(samp[cl]):5d}  {med*100:6.1f}cm   [{b[0]:4d} | {b[1]:4d} | {b[2]:4d} | {b[3]:4d}]')

# 2-sided test: a solid box mesh has triangle normals pointing BOTH ways along the thin axis.
def two_sided(h):
    v, F = ful.get(h), fac.get(h)
    if v is None or F is None or len(F) < 4: return None
    thin_axis = np.argmin(v.max(0)-v.min(0))
    a,b,cc = v[F[:,0]],v[F[:,1]],v[F[:,2]]
    n = np.cross(b-a, cc-a); comp = n[:, thin_axis]
    pos, neg = (comp > 0).sum(), (comp < 0).sum()
    return min(pos,neg)/max(pos+neg,1)   # ~0.5 = balanced (solid 2-sided); ~0 = single surface
ts = [two_sided(r[1]) for r in rows if (r[2] or '').startswith(SHELL) and two_sided(r[1]) is not None]
ts = np.array(ts)
log('THK_2SIDED', f'mean_balance={ts.mean():.2f} (0.5=solid 2-sided, 0=single-surface)  solid(>0.3)={100*(ts>0.3).mean():.0f}%  surface(<0.1)={100*(ts<0.1).mean():.0f}%')

# roof / top coverage vs the current dumped SET
try:
    txt = open('/tmp/wt-veil-strip/viewer/ext_shell_preview.js').read()
    arr = json.loads(txt[txt.index('=', txt.index('__EXT_SHELL[')) + 1: txt.rindex(']')+1])
    S = set(arr); log('SET_LOADED', f'{len(S)} guids')
    zmax = max(r[4] for r in rows if r[4] is not None)
    top = [r for r in rows if r[4] is not None and r[4] > zmax - 3 and (r[2] or '').startswith(('IfcSlab','IfcRoof','IfcCovering'))]
    intop = sum(1 for r in top if r[0] in S)
    log('ROOF_COVERAGE', f'top slab/roof/covering elements={len(top)}  in_set={intop}  ({100*intop/max(len(top),1):.0f}%) — low% = roof missing')
except Exception as e: log('SET_ERR', str(e))
