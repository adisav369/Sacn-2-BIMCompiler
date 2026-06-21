#!/usr/bin/env python3
# WITNESS: does the density+proximity formula actually SHED a wall's inner face (thin it)?
# keep triangle if outward side (along normal) is LESS dense than inward side. Measure % shed per cohort.
import sqlite3, sys, time, numpy as np
DB='deploy/buildings/Hospital_extracted.db'; CS=1.0; S=0.6   # probe step along normal (m)
def log(t,m): print(f'§{t} {m}',flush=True)
c=sqlite3.connect(DB)
# density grid from subsampled verts (density only)
geos={}
for h,vb in c.execute('SELECT geometry_hash,vertices FROM component_geometries'):
    v=np.frombuffer(vb,np.float32).reshape(-1,3)
    geos[h]=v[np.linspace(0,len(v)-1,min(120,len(v))).astype(int)]
rows=c.execute('''SELECT i.guid,i.geometry_hash,t.center_x,t.center_y,t.center_z,t.rotation_x,t.rotation_y,t.rotation_z,m.ifc_class,m.element_name
 FROM element_instances i JOIN element_transforms t ON t.guid=i.guid JOIN elements_meta m ON m.guid=i.guid''').fetchall()
def rot(rx,ry,rz):
    (cx,cy,cz)=np.cos([rx,ry,rz]);(sx,sy,sz)=np.sin([rx,ry,rz])
    return np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])
allv=[np.atleast_2d(geos[r[1]]@rot(r[5] or 0,r[6] or 0,r[7] or 0).T+np.array([r[2],r[3],r[4]])) for r in rows if geos.get(r[1]) is not None]
V=np.concatenate(allv); gmin=V.min(0)-5; dims=np.ceil((V.max(0)+5-gmin)/CS).astype(int)+1
grid=np.zeros(dims,np.int32); ci=((V-gmin)/CS).astype(int); np.add.at(grid,(ci[:,0],ci[:,1],ci[:,2]),1)
def dens(p):
    i=np.clip(((p-gmin)/CS).astype(int),0,dims-1); return grid[i[:,0],i[:,1],i[:,2]]
# full faces for wall test
fac={}
for h,fb in c.execute('SELECT geometry_hash,faces FROM component_geometries'): fac[h]=np.frombuffer(fb,np.uint32).reshape(-1,3)
ful={}
for h,vb in c.execute('SELECT geometry_hash,vertices FROM component_geometries'): ful[h]=np.frombuffer(vb,np.float32).reshape(-1,3)
def shed_frac(pred):
    kept=tot=0
    for r in rows:
        if not pred(r) or r[1] not in fac: continue
        V0=ful[r[1]]@rot(r[5] or 0,r[6] or 0,r[7] or 0).T+np.array([r[2],r[3],r[4]]); F=fac[r[1]]
        a,b,cc=V0[F[:,0]],V0[F[:,1]],V0[F[:,2]]; cen=(a+b+cc)/3
        n=np.cross(b-a,cc-a); ln=np.linalg.norm(n,axis=1,keepdims=True); n=n/np.where(ln==0,1,ln)
        do=dens(cen+S*n); di=dens(cen-S*n); keep=do<=di
        kept+=int(keep.sum()); tot+=len(F)
    return kept,tot
def nm(r,kw): return (r[8] or '').startswith('IfcWall') and kw.lower() in (r[9] or '').lower()
log('SHED','cohort                 kept/total tris   shed%')
for label,pred in [('Exterior walls',lambda r:nm(r,'Exterior')),('Interior walls',lambda r:nm(r,'Interior'))]:
    k,t=shed_frac(pred); print(f'    {label:20s} {k}/{t}   shed={100*(t-k)/max(t,1):.0f}%')
log('NOTE','want exterior walls to SHED ~50% (inner face gone). If shed~0, local density cannot thin (both faces open-in-front)')
