#!/usr/bin/env python3
"""Spike 1b: Tier-2 bands as VALIDATOR (does element fit its OWN class band,
cross-building) vs IDENTIFIER (already measured: 8.4% top-1).
(a) own-class in-band rate  (b) ambiguity: how many class bands contain the element."""
import sqlite3, os
import numpy as np
from collections import defaultdict

ROOT = "/home/red1/bim-compiler"
DBS = {
    "SH": "deploy/buildings/SampleHouse_extracted.db",
    "DX": "deploy/buildings/Duplex_extracted.db",
    "SC": "deploy/buildings/SampleCastle_extracted.db",
}
EPS = 1e-4

def load(tag, rel):
    p = os.path.join(ROOT, rel)
    c = sqlite3.connect(f"file:{p}?mode=ro", uri=True)
    cls_of = dict(c.execute("SELECT guid, ifc_class FROM elements_meta"))
    hash_of = dict(c.execute("SELECT guid, geometry_hash FROM element_instances"))
    feats = {}
    for h, blob in c.execute("SELECT geometry_hash, vertices FROM component_geometries"):
        v = np.frombuffer(blob, dtype=np.float32).reshape(-1, 3).astype(np.float64)
        if len(v) < 3: continue
        cc = v - v.mean(axis=0)
        w, vec = np.linalg.eigh(cc.T @ cc / len(v))
        proj = cc @ vec
        feats[h] = np.sort(proj.max(axis=0) - proj.min(axis=0))
    c.close()
    return [(cls, np.log(np.maximum(feats[hash_of[g]], EPS)))
            for g, cls in cls_of.items() if hash_of.get(g) in feats]

data = {t: load(t, r) for t, r in DBS.items()}
PAD = 0.10  # 10% dimension tolerance widening on band edges

for held in DBS:
    train = defaultdict(list)
    for t, rows in data.items():
        if t == held: continue
        for cls, f in rows: train[cls].append(f)
    band = {}
    for cls, arr in train.items():
        if len(arr) < 3: continue  # need some support
        a = np.array(arr)
        band[cls] = (np.percentile(a, 2.5, axis=0) - PAD, np.percentile(a, 97.5, axis=0) + PAD, len(arr))
    inband = tot = 0
    amb = []
    for cls, f in data[held]:
        if cls not in band: continue
        tot += 1
        lo, hi, n = band[cls]
        if np.all(f >= lo) & np.all(f <= hi): inband += 1
        amb.append(sum(1 for c2, (lo2, hi2, _) in band.items() if np.all(f >= lo2) and np.all(f <= hi2)))
    amb = np.array(amb)
    print(f"§VALID {held}: n={tot} own-class-in-band={inband} ({100.0*inband/max(tot,1):.1f}%) "
          f"| ambiguity: mean {amb.mean():.1f} classes contain element, median {np.median(amb):.0f}, "
          f"unique(=1) {100.0*np.sum(amb==1)/len(amb):.1f}%, zero-band {100.0*np.sum(amb==0)/len(amb):.1f}%")
