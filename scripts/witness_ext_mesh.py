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
# ⚠ DO NOT REMOVE — WITNESS ONLY (no render). Blast exterior visibility against the REAL MESH (actual
# triangle vertices), not bboxes. Fixes the two issues bbox could not: (1) GAPS — thin columns/beams no
# longer over-occlude (their real geometry is thin, not a fat box); (2) THICKNESS — a vertex is "visible"
# only if it's the nearest at its pixel, so inner faces (behind the outer skin) never qualify → thickness
# strips itself. Output = visible-element guid set. Read the §-log; validate vs element-name ground truth.
import sqlite3, sys, time, json, os
import numpy as np

DB  = sys.argv[1] if len(sys.argv) > 1 else 'deploy/buildings/Hospital_extracted.db'
RES = int(sys.argv[sys.argv.index('res')+1]) if 'res' in sys.argv else 600
CAP = 120   # max vertices sampled per geometry (speed); thin elements keep all

def log(t, m): print(f'§{t} {m}', flush=True)
t0 = time.time()
c = sqlite3.connect(DB)

geos = {}
for h, vb in c.execute('SELECT geometry_hash, vertices FROM component_geometries'):
    v = np.frombuffer(vb, np.float32).reshape(-1, 3)
    if len(v) > CAP: v = v[np.linspace(0, len(v)-1, CAP).astype(int)]
    geos[h] = v
log('MESH_GEO', f'unique_geometries={len(geos)} vert_cap={CAP}')

rows = c.execute('''SELECT i.guid, i.geometry_hash, t.center_x,t.center_y,t.center_z,
    t.rotation_x,t.rotation_y,t.rotation_z, m.ifc_class, m.element_name
    FROM element_instances i JOIN element_transforms t ON t.guid=i.guid
    JOIN elements_meta m ON m.guid=i.guid''').fetchall()
N = len(rows)

def rot(rx, ry, rz):
    (cx,cy,cz) = np.cos([rx,ry,rz]); (sx,sy,sz) = np.sin([rx,ry,rz])
    Rx = np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])
    Ry = np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])
    Rz = np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])
    return Rz @ Ry @ Rx
allv, alli = [], []
for k, r in enumerate(rows):
    g = geos.get(r[1])
    if g is None: continue
    w = g @ rot(r[5] or 0, r[6] or 0, r[7] or 0).T + np.array([r[2], r[3], r[4]])
    allv.append(w.astype(np.float32)); alli.append(np.full(len(w), k, np.int32))
V = np.concatenate(allv); EI = np.concatenate(alli)
log('MESH_WORLD', f'instances={N} world_verts={len(V)}  t={time.time()-t0:.1f}s')

dirs = []
for ax in [(1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)]: dirs.append(np.array(ax, float))
for sx in (1,-1):
    for sy in (1,-1):
        for sz in (1,-1): dirs.append(np.array([sx,sy,sz], float)/np.sqrt(3))
for a,b,c0 in [(1,1,0),(1,-1,0),(0,1,1),(0,1,-1),(1,0,1),(1,0,-1)]:
    for s in (1,-1): dirs.append(np.array([a*s,b*s,c0*s], float)/np.sqrt(a*a+b*b+c0*c0))

SPLAT = int(sys.argv[sys.argv.index('splat')+1]) if 'splat' in sys.argv else 2  # disc radius (px) → surfaces occlude
def minfilt(z2, r):                                   # grow nearest-depth into a r-px disc (fills point gaps)
    out = z2.copy()
    for _ in range(r):
        m = out.copy()
        m[1:,:] = np.minimum(m[1:,:], out[:-1,:]); m[:-1,:] = np.minimum(m[:-1,:], out[1:,:])
        m[:,1:] = np.minimum(m[:,1:], out[:,:-1]); m[:,:-1] = np.minimum(m[:,:-1], out[:,1:])
        out = m
    return out
vis = np.zeros(N, bool)
for d in dirs:
    up = np.array([0,0,1.]) if abs(d[2]) < 0.9 else np.array([0,1.,0])
    u = np.cross(up, d); u /= np.linalg.norm(u); v = np.cross(d, u)
    su = V @ u; sv = V @ v; dep = V @ d
    iu = np.clip(((su-su.min())/(su.max()-su.min()+1e-9)*(RES-1)).astype(int), 0, RES-1)
    iv = np.clip(((sv-sv.min())/(sv.max()-sv.min()+1e-9)*(RES-1)).astype(int), 0, RES-1)
    pix = iu*RES + iv
    zbuf = np.full(RES*RES, np.inf); np.minimum.at(zbuf, pix, dep)
    zf = minfilt(zbuf.reshape(RES, RES), SPLAT).ravel()   # filled occluder
    vis[np.unique(EI[dep <= zf[pix] + 1e-3])] = True       # a vert survives only if not behind a nearer splat
log('MESH_RESULT', f'EXTERIOR(real-mesh)={int(vis.sum())}/{N} ({100*vis.sum()/N:.1f}%)  dirs={len(dirs)} res={RES}  t={time.time()-t0:.1f}s')

def fr(pred):
    idx = [k for k in range(N) if pred(rows[k])]; return (sum(vis[k] for k in idx), len(idx))
def nm(r, kw): return (r[8] or '').startswith('IfcWall') and kw.lower() in (r[9] or '').lower()
log('MESH_GROUNDTRUTH', '')
for label, pred in [('Exterior-named walls (want HI)', lambda r: nm(r,'Exterior')),
                    ('Interior-named walls (want LO)', lambda r: nm(r,'Interior')),
                    ('Partition-named walls(want LO)', lambda r: nm(r,'Partition')),
                    ('MEP pipes+ducts   (want ~0)',    lambda r: (r[8] or '').startswith(('IfcPipe','IfcDuct'))),
                    ('Furniture+lights  (want LO)',    lambda r: (r[8] or '') in ('IfcFurniture','IfcLightFixture'))]:
    a, b = fr(pred); print(f'    {label:32s} {a}/{b} = {100*a/max(b,1):.0f}%')

if 'dump' in sys.argv:
    guids = [rows[k][0] for k in range(N) if vis[k]]
    bld = os.path.basename(DB).split('_')[0]
    open('/tmp/wt-veil-strip/viewer/ext_shell_preview.js','w').write(
        '// PREVIEW ONLY (real-mesh witness dump, not committed)\n'
        'window.__EXT_SHELL = window.__EXT_SHELL || {};\nwindow.__EXT_SHELL[%s] = %s;\n' % (json.dumps(bld), json.dumps(guids)))
    log('DUMP', f'{len(guids)} guids -> ext_shell_preview.js')
log('DONE', f'total={time.time()-t0:.1f}s')
