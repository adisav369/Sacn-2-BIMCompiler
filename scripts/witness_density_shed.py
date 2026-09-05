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
# ⚠ DO NOT REMOVE — WITNESS ONLY (no render). PROVE the density+proximity principle BEFORE wiring it:
#   "dense geometry nearby on a side = inward; open nearby = outward; dense+nearby on ALL sides = buried."
# Builds a voxel DENSITY grid from real mesh verts, then probes just outside each element's 6 faces.
#   open faces (low density nearby) → exterior-facing ; 0 open faces → buried/interior.
# Validate vs element-name ground truth. Read the §-log; exit code is not evidence.
import sqlite3, sys, time
import numpy as np

DB = sys.argv[1] if len(sys.argv) > 1 else 'deploy/buildings/Hospital_extracted.db'
CS = float(sys.argv[sys.argv.index('cs')+1]) if 'cs' in sys.argv else 1.0   # density cell size (m)
R  = float(sys.argv[sys.argv.index('r')+1])  if 'r'  in sys.argv else 1.5    # proximity probe offset (m)
T  = int(sys.argv[sys.argv.index('t')+1])    if 't'  in sys.argv else 3      # verts/cell <= T = "open"
CAP = 120
def log(t, m): print(f'§{t} {m}', flush=True)
t0 = time.time()
c = sqlite3.connect(DB)

geos = {}
for h, vb in c.execute('SELECT geometry_hash, vertices FROM component_geometries'):
    v = np.frombuffer(vb, np.float32).reshape(-1, 3)
    if len(v) > CAP: v = v[np.linspace(0, len(v)-1, CAP).astype(int)]
    geos[h] = v
rows = c.execute('''SELECT i.guid,i.geometry_hash,t.center_x,t.center_y,t.center_z,
    t.rotation_x,t.rotation_y,t.rotation_z,m.ifc_class,m.element_name
    FROM element_instances i JOIN element_transforms t ON t.guid=i.guid
    JOIN elements_meta m ON m.guid=i.guid''').fetchall()
N = len(rows)
def rot(rx,ry,rz):
    (cx,cy,cz)=np.cos([rx,ry,rz]);(sx,sy,sz)=np.sin([rx,ry,rz])
    return (np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]]) @ np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]]) @ np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]]))
allv, alli = [], []
for k,r in enumerate(rows):
    g = geos.get(r[1]);
    if g is None: continue
    w = g @ rot(r[5] or 0,r[6] or 0,r[7] or 0).T + np.array([r[2],r[3],r[4]])
    allv.append(w.astype(np.float32)); alli.append(np.full(len(w),k,np.int32))
V = np.concatenate(allv); EI = np.concatenate(alli)
log('DEN_WORLD', f'instances={N} world_verts={len(V)}  t={time.time()-t0:.1f}s')

# --- density grid (vertex count per cell) ---
gmin = V.min(0) - 5; gmax = V.max(0) + 5
dims = np.ceil((gmax-gmin)/CS).astype(int) + 1
grid = np.zeros(dims, np.int32)
ci = ((V-gmin)/CS).astype(int)
np.add.at(grid, (ci[:,0], ci[:,1], ci[:,2]), 1)
log('DEN_GRID', f'cell={CS}m dims={dims.tolist()} nonempty_cells={int((grid>0).sum())}  t={time.time()-t0:.1f}s')

# --- per-element world bbox from real verts ---
emn = np.full((N,3), 1e9); emx = np.full((N,3), -1e9)
np.minimum.at(emn, EI, V); np.maximum.at(emx, EI, V)
ctr = (emn+emx)/2
def dens_at(p):
    idx = np.clip(((p-gmin)/CS).astype(int), 0, dims-1)
    return grid[idx[:,0], idx[:,1], idx[:,2]]
# probe just outside each of the 6 faces
probes = {
  '+x': np.stack([emx[:,0]+R, ctr[:,1], ctr[:,2]],1), '-x': np.stack([emn[:,0]-R, ctr[:,1], ctr[:,2]],1),
  '+y': np.stack([ctr[:,0], emx[:,1]+R, ctr[:,2]],1), '-y': np.stack([ctr[:,0], emn[:,1]-R, ctr[:,2]],1),
  '+z': np.stack([ctr[:,0], ctr[:,1], emx[:,2]+R],1), '-z': np.stack([ctr[:,0], ctr[:,1], emn[:,2]-R],1)}
openf = np.zeros(N, int)
valid = np.array([geos.get(r[1]) is not None for r in rows])
for d,p in probes.items(): openf += (dens_at(p) <= T).astype(int)
openf[~valid] = -1
log('DEN_OPEN', f'probe_offset={R}m open_thresh<=({T} verts/cell)  mean_open_faces={openf[valid].mean():.2f}/6')

# --- GROUND TRUTH: openness should be HIGH for exterior skin, LOW(0)=buried for interior guts ---
def cohort(pred):
    idx = np.array([k for k in range(N) if valid[k] and pred(rows[k])])
    if not len(idx): return (0,0,0)
    o = openf[idx]
    return (o.mean(), int((o==0).sum())*100//len(idx), len(idx))   # mean open faces, %buried(0-open), n
def nm(r,kw): return (r[8] or '').startswith('IfcWall') and kw.lower() in (r[9] or '').lower()
log('DEN_GROUNDTRUTH', 'cohort                      mean_open/6   %buried(0open)   n   verdict')
for label,pred,want in [
    ('Exterior-named walls', lambda r: nm(r,'Exterior'), 'open hi, buried~0'),
    ('Interior-named walls',  lambda r: nm(r,'Interior'), 'mixed (rooms = open too)'),
    ('Roof/Slab',             lambda r: (r[8] or '') in ('IfcSlab','IfcRoof'), 'open hi'),
    ('MEP pipes+ducts',       lambda r: (r[8] or '').startswith(('IfcPipe','IfcDuct')), 'buried HI'),
    ('Furniture',             lambda r: (r[8] or '')=='IfcFurniture', 'open (in rooms)')]:
    mo, bp, n = cohort(pred)
    print(f'    {label:24s}   {mo:6.2f}      {bp:4d}%        {n:5d}   ({want})')

# the actionable splits
ext_buried = cohort(lambda r: nm(r,'Exterior'))[1]
log('DEN_VERDICT', f'KEY: exterior-walls-wrongly-buried={ext_buried}% (want ~0)  — if low, the open-side test keeps the skin')
log('DONE', f't={time.time()-t0:.1f}s')
