#!/usr/bin/env python3
"""POC Spike 1 (RESUME_IFC_BOM_GEOMAPPING.md §WORKFLOW Phase 2 item 1):
Tier-2 core bet — do measured per-class dimension bands separate real ifc_class
values across SH/DX/SC/Terminal with usable precision?

Method (deterministic, glass-box):
  - Per unique mesh: sorted PCA extents (rotation-invariant) + sorted AABB dims
    (frame-contaminated naive variant) + stored bbox_x/y/z cols (what a naive
    Phase-3 would read).
  - Leave-one-building-out: per-class robust bands (median/IQR in log space)
    mined from the other 3 buildings; classify held-out elements by min summed
    robust-z; report measured top-1/top-3 accuracy + confusion.
Read-only. Disposable POC — not the library.
"""
import sqlite3, os, sys, math
import numpy as np
from collections import defaultdict, Counter

ROOT = "/home/red1/bim-compiler"
DBS = {
    "SH": ("deploy/buildings/SampleHouse_extracted.db", None),
    "DX": ("deploy/buildings/Duplex_extracted.db", None),
    "SC": ("deploy/buildings/SampleCastle_extracted.db", None),
    "Terminal": ("deploy/buildings/Terminal_extracted.db",
                 "deploy/dev/buildings/Terminal_geo.db"),
}
EPS = 1e-4  # 0.1mm floor for degenerate extents

def mesh_features(blob):
    v = np.frombuffer(blob, dtype=np.float32).reshape(-1, 3).astype(np.float64)
    if len(v) < 3:
        return None
    aabb = np.sort(v.max(axis=0) - v.min(axis=0))
    c = v - v.mean(axis=0)
    cov = c.T @ c / len(v)
    w, vec = np.linalg.eigh(cov)          # ascending eigenvalues
    proj = c @ vec
    pca = np.sort(proj.max(axis=0) - proj.min(axis=0))
    return aabb, pca

def load_building(tag, meta_rel, geo_rel):
    p = os.path.join(ROOT, meta_rel)
    c = sqlite3.connect(f"file:{p}?mode=ro", uri=True)
    cls_of = dict(c.execute("SELECT guid, ifc_class FROM elements_meta"))
    hash_of = dict(c.execute("SELECT guid, geometry_hash FROM element_instances"))
    bbox_of = {g: (bx, by, bz) for g, bx, by, bz in
               c.execute("SELECT guid, bbox_x, bbox_y, bbox_z FROM element_transforms")}
    gp = os.path.join(ROOT, geo_rel) if geo_rel else p
    gc = sqlite3.connect(f"file:{gp}?mode=ro", uri=True)
    feats = {}
    for h, blob in gc.execute("SELECT geometry_hash, vertices FROM component_geometries"):
        f = mesh_features(blob)
        if f:
            feats[h] = f
    gc.close(); c.close()
    rows = []
    miss_geo = 0
    for guid, cls in cls_of.items():
        h = hash_of.get(guid)
        f = feats.get(h) if h else None
        if not f:
            miss_geo += 1
            continue
        aabb, pca = f
        sb = bbox_of.get(guid)
        stored = np.sort(np.array([abs(x or 0) for x in sb])) if sb and all(x is not None for x in sb) else None
        rows.append((cls, aabb, pca, stored))
    print(f"§LOAD {tag}: elements={len(cls_of)} with-mesh={len(rows)} no-mesh={miss_geo} uniq-geo={len(feats)}")
    return rows

def logf(x):
    return np.log(np.maximum(x, EPS))

def bands(train_rows, fidx):
    """per-class median + robust sigma (IQR/1.349) of log sorted dims"""
    by_cls = defaultdict(list)
    for cls, aabb, pca, stored in train_rows:
        f = (aabb, pca, stored)[fidx]
        if f is not None:
            by_cls[cls].append(logf(f))
    out = {}
    for cls, arr in by_cls.items():
        a = np.array(arr)
        med = np.median(a, axis=0)
        iqr = np.percentile(a, 75, axis=0) - np.percentile(a, 25, axis=0)
        sig = np.maximum(iqr / 1.349, 0.05)  # floor: 5% dimension tolerance
        out[cls] = (med, sig, len(arr))
    return out

def classify(bnd, f):
    lf = logf(f)
    scores = sorted((float(np.sum(np.abs(lf - med) / sig)), cls)
                    for cls, (med, sig, n) in bnd.items())
    return [cls for _, cls in scores]

def main():
    data = {tag: load_building(tag, m, g) for tag, (m, g) in DBS.items()}

    # stored-bbox vs mesh-AABB divergence (spike-3 side evidence of frame mixing)
    for tag, rows in data.items():
        n = tot = 0
        for cls, aabb, pca, stored in rows:
            if stored is None:
                continue
            tot += 1
            rel = np.abs(stored - aabb) / np.maximum(aabb, EPS)
            if np.any(rel > 0.05):
                n += 1
        print(f"§BBOXMIX {tag}: stored bbox differs >5% from mesh AABB on {n}/{tot} ({100.0*n/max(tot,1):.1f}%)")

    for fidx, fname in ((1, "PCA-extents(rot-invariant)"), (0, "mesh-AABB(sorted)"), (2, "stored-bbox-cols(sorted)")):
        print(f"\n======== FEATURE SET: {fname} ========")
        agg_top1 = agg_top3 = agg_n = 0
        for held in DBS:
            train = [r for t, rows in data.items() if t != held for r in rows]
            bnd = bands(train, fidx)
            test = data[held]
            n = top1 = top3 = skipped = 0
            conf = Counter()
            per_cls = defaultdict(lambda: [0, 0])  # cls -> [correct, total]
            for cls, aabb, pca, stored in test:
                f = (aabb, pca, stored)[fidx]
                if cls not in bnd or f is None:
                    skipped += 1
                    continue
                ranked = classify(bnd, f)
                n += 1
                per_cls[cls][1] += 1
                if ranked[0] == cls:
                    top1 += 1; per_cls[cls][0] += 1
                else:
                    conf[(cls, ranked[0])] += 1
                if cls in ranked[:3]:
                    top3 += 1
            agg_top1 += top1; agg_top3 += top3; agg_n += n
            print(f"§HELDOUT {held}: n={n} skipped(class-not-in-train/no-feat)={skipped} "
                  f"top1={top1} ({100.0*top1/max(n,1):.1f}%) top3={top3} ({100.0*top3/max(n,1):.1f}%)")
            worst = sorted(per_cls.items(), key=lambda kv: kv[1][0]/max(kv[1][1],1))[:5]
            print(f"   worst classes: " + ", ".join(f"{c} {a}/{b}" for c, (a, b) in worst))
            print(f"   top confusions: " + ", ".join(f"{a}->{b} x{n2}" for (a, b), n2 in conf.most_common(5)))
        print(f"§OVERALL {fname}: top1={agg_top1}/{agg_n} ({100.0*agg_top1/max(agg_n,1):.1f}%) "
              f"top3={agg_top3}/{agg_n} ({100.0*agg_top3/max(agg_n,1):.1f}%)")

if __name__ == "__main__":
    main()
