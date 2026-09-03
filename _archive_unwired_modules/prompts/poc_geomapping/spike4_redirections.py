#!/usr/bin/env python3
"""Step 2.3: measure the three F3 redirections for Tier 2. Deterministic, glass-box.
  R-A per-building-class bands: split-half within building (even/odd by guid sort) —
      identifier top-1 + own-class in-band rate. Terminal included via bbox cols.
  R-B coarser BOM-category targets: LOBO across SH/DX/SC on PCA extents.
  R-C added features: LOBO ifc_class with dims + z-fraction + log-nverts (SH/DX/SC).
"""
import sqlite3, os
import numpy as np
from collections import defaultdict, Counter

ROOT = "/home/red1/bim-compiler"
EPS = 1e-4

COARSE = {
    "IfcWall": "WALL", "IfcWallStandardCase": "WALL", "IfcCurtainWall": "WALL",
    "IfcSlab": "SLAB", "IfcRoof": "SLAB", "IfcCovering": "COVERING",
    "IfcBeam": "FRAME", "IfcMember": "FRAME", "IfcColumn": "FRAME",
    "IfcDoor": "OPENING_FILL", "IfcWindow": "OPENING_FILL",
    "IfcStair": "STAIR", "IfcStairFlight": "STAIR", "IfcRampFlight": "STAIR",
    "IfcRailing": "RAILING",
    "IfcFurniture": "FURNITURE", "IfcFurnishingElement": "FURNITURE",
    "IfcFlowSegment": "MEP_RUN", "IfcPipeSegment": "MEP_RUN", "IfcDuctSegment": "MEP_RUN",
    "IfcFlowFitting": "MEP_FITTING", "IfcPipeFitting": "MEP_FITTING",
    "IfcDuctFitting": "MEP_FITTING", "IfcValve": "MEP_FITTING",
    "IfcFlowController": "MEP_FITTING",
    "IfcFlowTerminal": "MEP_TERMINAL", "IfcAirTerminal": "MEP_TERMINAL",
    "IfcLightFixture": "MEP_TERMINAL", "IfcFireSuppressionTerminal": "MEP_TERMINAL",
    "IfcSanitaryTerminal": "MEP_TERMINAL", "IfcElectricAppliance": "MEP_TERMINAL",
    "IfcPlate": "PANEL", "IfcBuildingElementPart": "PANEL",
    "IfcFooting": "FOOTING", "IfcDistributionElement": "MEP_TERMINAL",
    # IfcBuildingElementProxy deliberately absent -> honest-refuse territory
}

MESH_DBS = {
    "SH": "deploy/buildings/SampleHouse_extracted.db",
    "DX": "deploy/buildings/Duplex_extracted.db",
    "SC": "deploy/buildings/SampleCastle_extracted.db",
}

def load_mesh_building(rel):
    c = sqlite3.connect(f"file:{os.path.join(ROOT, rel)}?mode=ro", uri=True)
    cls_of = dict(c.execute("SELECT guid, ifc_class FROM elements_meta"))
    hash_of = dict(c.execute("SELECT guid, geometry_hash FROM element_instances"))
    feats = {}
    for h, blob in c.execute("SELECT geometry_hash, vertices FROM component_geometries"):
        v = np.frombuffer(blob, dtype=np.float32).reshape(-1, 3).astype(np.float64)
        if len(v) < 3: continue
        span = v.max(axis=0) - v.min(axis=0)          # world AABB (x,y,z) — z is vertical
        cc = v - v.mean(axis=0)
        w, vec = np.linalg.eigh(cc.T @ cc / len(v))
        proj = cc @ vec
        pca = np.sort(proj.max(axis=0) - proj.min(axis=0))
        feats[h] = (span, pca, len(v))
    c.close()
    rows = []
    for guid in sorted(cls_of):                        # deterministic order
        h = hash_of.get(guid)
        if h not in feats: continue
        span, pca, nv = feats[h]
        rows.append((guid, cls_of[guid], span, pca, nv))
    return rows

def load_terminal_bbox():
    c = sqlite3.connect(f"file:{os.path.join(ROOT,'deploy/buildings/Terminal_extracted.db')}?mode=ro", uri=True)
    rows = []
    for guid, cls, bx, by, bz in c.execute(
            "SELECT em.guid, em.ifc_class, t.bbox_x, t.bbox_y, t.bbox_z "
            "FROM elements_meta em JOIN element_transforms t ON em.guid=t.guid "
            "ORDER BY em.guid"):
        if bx is None or bx <= 0 or by is None or bz is None: continue
        rows.append((guid, cls, np.array([abs(bx), abs(by), abs(bz)]), None, None))
    c.close()
    return rows

def logf(x): return np.log(np.maximum(np.asarray(x, dtype=float), EPS))

def bands(pairs):
    by = defaultdict(list)
    for lbl, f in pairs: by[lbl].append(f)
    out = {}
    for lbl, arr in by.items():
        if len(arr) < 3: continue
        a = np.array(arr)
        med = np.median(a, axis=0)
        sig = np.maximum((np.percentile(a,75,axis=0)-np.percentile(a,25,axis=0))/1.349, 0.05)
        lo, hi = np.percentile(a,2.5,axis=0)-0.10, np.percentile(a,97.5,axis=0)+0.10
        out[lbl] = (med, sig, lo, hi, len(arr))
    return out

def evaluate(train_pairs, test_pairs, name):
    bnd = bands(train_pairs)
    n=top1=top3=inband=skip=0
    conf = Counter()
    for lbl, f in test_pairs:
        if lbl not in bnd: skip += 1; continue
        n += 1
        ranked = sorted((float(np.sum(np.abs(f-med)/sig)), l) for l,(med,sig,lo,hi,_) in bnd.items())
        pred = [l for _,l in ranked]
        if pred[0]==lbl: top1+=1
        else: conf[(lbl,pred[0])]+=1
        if lbl in pred[:3]: top3+=1
        med,sig,lo,hi,_ = bnd[lbl]
        if np.all(f>=lo) and np.all(f<=hi): inband+=1
    print(f"§{name}: n={n} skip={skip} top1={top1} ({100.0*top1/max(n,1):.1f}%) "
          f"top3={top3} ({100.0*top3/max(n,1):.1f}%) own-in-band={inband} ({100.0*inband/max(n,1):.1f}%)")
    if conf:
        print("    confusions: " + ", ".join(f"{a}->{b} x{c}" for (a,b),c in conf.most_common(4)))
    return top1, n

data = {t: load_mesh_building(r) for t, r in MESH_DBS.items()}
data["Terminal"] = load_terminal_bbox()
for t, rows in data.items(): print(f"§LOAD {t}: {len(rows)} usable elements")

print("\n==== R-A: per-building-class bands (split-half within building, sorted-dims) ====")
tots = [0,0]
for t, rows in data.items():
    pairs = [(cls, logf(np.sort(span))) for _, cls, span, _, _ in rows]
    train = [p for i,p in enumerate(pairs) if i%2==0]
    test  = [p for i,p in enumerate(pairs) if i%2==1]
    a,b = evaluate(train, test, f"R-A {t}")
    tots[0]+=a; tots[1]+=b
print(f"§R-A OVERALL: top1={tots[0]}/{tots[1]} ({100.0*tots[0]/max(tots[1],1):.1f}%)")

print("\n==== R-B: coarse BOM-category, LOBO across SH/DX/SC (PCA extents) ====")
tots = [0,0]
for held in MESH_DBS:
    train = [(COARSE[c], logf(p)) for t2 in MESH_DBS if t2!=held
             for _, c, s, p, _ in data[t2] if c in COARSE]
    test  = [(COARSE[c], logf(p)) for _, c, s, p, _ in data[held] if c in COARSE]
    a,b = evaluate(train, test, f"R-B heldout {held}")
    tots[0]+=a; tots[1]+=b
print(f"§R-B OVERALL: top1={tots[0]}/{tots[1]} ({100.0*tots[0]/max(tots[1],1):.1f}%)")

print("\n==== R-B2: coarse BOM-category, split-half WITHIN building ====")
tots = [0,0]
for t, rows in data.items():
    pairs = [(COARSE[c], logf(np.sort(s))) for _, c, s, _, _ in rows if c in COARSE]
    train = [p for i,p in enumerate(pairs) if i%2==0]
    test  = [p for i,p in enumerate(pairs) if i%2==1]
    a,b = evaluate(train, test, f"R-B2 {t}")
    tots[0]+=a; tots[1]+=b
print(f"§R-B2 OVERALL: top1={tots[0]}/{tots[1]} ({100.0*tots[0]/max(tots[1],1):.1f}%)")

print("\n==== R-C: ifc_class LOBO with ADDED features (sorted-PCA + z-fraction + log-nverts) ====")
def feat_c(span, pca, nv):
    zfrac = span[2] / max(np.max(span), EPS)          # verticality: z share of longest dim
    return np.concatenate([logf(pca), [zfrac*3.0], [np.log(max(nv,3))*0.5]])
tots = [0,0]
for held in MESH_DBS:
    train = [(c, feat_c(s,p,nv)) for t2 in MESH_DBS if t2!=held for _, c, s, p, nv in data[t2]]
    test  = [(c, feat_c(s,p,nv)) for _, c, s, p, nv in data[held]]
    a,b = evaluate(train, test, f"R-C heldout {held}")
    tots[0]+=a; tots[1]+=b
print(f"§R-C OVERALL: top1={tots[0]}/{tots[1]} ({100.0*tots[0]/max(tots[1],1):.1f}%)")

print("\n==== R-A+C combined: per-building split-half, ifc_class, added features ====")
tots = [0,0]
for t in MESH_DBS:
    pairs = [(c, feat_c(s,p,nv)) for _, c, s, p, nv in data[t]]
    train = [p for i,p in enumerate(pairs) if i%2==0]
    test  = [p for i,p in enumerate(pairs) if i%2==1]
    a,b = evaluate(train, test, f"R-AC {t}")
    tots[0]+=a; tots[1]+=b
print(f"§R-AC OVERALL: top1={tots[0]}/{tots[1]} ({100.0*tots[0]/max(tots[1],1):.1f}%)")
