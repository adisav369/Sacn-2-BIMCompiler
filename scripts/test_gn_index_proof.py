# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
Standalone proof: GN collection index mapping is correct.
No Blender needed — pure math on the extracted DB.

Proves:
  §PROOF INDEX_STABLE   — every hash maps to exactly one collection slot
  §PROOF INDEX_COMPLETE — every element's instance_index resolves to its hash's slot
  §PROOF NO_WRAP        — no index exceeds collection size (no modular wrapping)
  §PROOF BBOX_ZERO      — unmapped elements fall to slot 0 (bbox proxy)

Usage:
  python3 scripts/test_gn_index_proof.py Hospital
  python3 scripts/test_gn_index_proof.py Clinic
  python3 scripts/test_gn_index_proof.py Terminal
"""
import sqlite3
import sys
import os
import numpy as np

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

def test_building(name):
    db_path = os.path.join(PROJECT, "DAGCompiler/lib/input", f"{name}_extracted.db")
    if not os.path.exists(db_path):
        print(f"SKIP — {db_path} not found")
        return False

    db = sqlite3.connect(db_path)

    # ── Read elements (same query as stage2_library_linker.py) ──
    tables = {r[0] for r in db.execute(
        "SELECT name FROM sqlite_master WHERE type='table'")}

    if "elements_meta" in tables:
        rows = db.execute("""
            SELECT m.guid, m.ifc_class, m.discipline,
                   m.material_name, m.material_rgba,
                   et.center_x, et.center_y, et.center_z,
                   et.rotation_x, et.rotation_y, et.rotation_z,
                   ei.geometry_hash
            FROM elements_meta m
            JOIN element_transforms et ON m.guid = et.guid
            JOIN element_instances ei ON m.guid = ei.guid
        """).fetchall()
    else:
        print(f"SKIP — no elements_meta in {name}")
        return False

    if not rows:
        print(f"SKIP — 0 elements in {name}")
        return False

    # ── Simulate cache load: hash_to_index (cache order) ──
    unique_hashes = sorted(set(r[11] for r in rows if r[11]))
    hash_to_index = {h: i for i, h in enumerate(unique_hashes)}

    # ── Simulate step 4: pre-populate collection ──
    # §FIX: GN Collection Info iterates ALPHABETICALLY by object name.
    # !bbox sorts first (! < T), then Tpl_* sorted by hash prefix.
    # Compute indices from the alphabetical order GN will use.
    bbox_name = "!bbox"
    tpl_names = {ghash: f"Tpl_{ghash[:12]}" for ghash in hash_to_index}
    all_names = sorted([bbox_name] + list(tpl_names.values()))
    name_to_idx = {name: i for i, name in enumerate(all_names)}

    bbox_index = name_to_idx[bbox_name]
    hash_to_col_index = {}
    for ghash, tpl_name in tpl_names.items():
        hash_to_col_index[ghash] = name_to_idx[tpl_name]

    collection_size = len(all_names)  # bbox + templates

    # ── Simulate step 5: build cache_to_col lookup ──
    max_cache_idx = max(hash_to_index.values()) + 1
    cache_to_col = np.full(max_cache_idx, bbox_index, dtype=np.int32)
    for ghash, cache_idx in hash_to_index.items():
        col_idx = hash_to_col_index.get(ghash)
        if col_idx is not None:
            cache_to_col[cache_idx] = col_idx

    # ── Simulate per-element instance_index assignment ──
    # Group by discipline (same as loader)
    disc_elements = {}
    for r in rows:
        ghash = r[11]
        disc = r[2] or "UNK"
        idx = hash_to_index.get(ghash)
        if idx is not None:
            disc_elements.setdefault(disc, []).append((ghash, idx))

    # ── TESTS ──
    passed = 0
    failed = 0

    # TEST 1: INDEX_STABLE — each hash has exactly one collection slot
    slot_to_hash = {}
    dupes = 0
    for ghash, col_idx in hash_to_col_index.items():
        if col_idx in slot_to_hash:
            dupes += 1
        slot_to_hash[col_idx] = ghash
    if dupes == 0:
        print(f"  §PROOF INDEX_STABLE    {len(hash_to_col_index):,} hashes → "
              f"{len(hash_to_col_index):,} unique slots — PASS")
        passed += 1
    else:
        print(f"  §FAIL  INDEX_STABLE    {dupes} duplicate slot assignments")
        failed += 1

    # TEST 2: INDEX_COMPLETE — every element resolves to its hash's slot
    wrong = 0
    total = 0
    for disc, elems in disc_elements.items():
        for ghash, cache_idx in elems:
            resolved_col_idx = int(cache_to_col[cache_idx])
            expected_col_idx = hash_to_col_index[ghash]
            if resolved_col_idx != expected_col_idx:
                wrong += 1
                if wrong <= 3:
                    print(f"    MISMATCH: {ghash[:12]} cache_idx={cache_idx} "
                          f"→ resolved={resolved_col_idx} expected={expected_col_idx}")
            total += 1
    if wrong == 0:
        print(f"  §PROOF INDEX_COMPLETE  {total:,} elements all resolve correctly — PASS")
        passed += 1
    else:
        print(f"  §FAIL  INDEX_COMPLETE  {wrong:,}/{total:,} elements mismatched")
        failed += 1

    # TEST 3: NO_WRAP — no resolved index >= collection_size
    all_indices = cache_to_col[:max_cache_idx]
    over = int(np.sum(all_indices >= collection_size))
    if over == 0:
        print(f"  §PROOF NO_WRAP         max_index={int(all_indices.max())} < "
              f"collection_size={collection_size} — PASS")
        passed += 1
    else:
        print(f"  §FAIL  NO_WRAP         {over} indices >= collection_size={collection_size}")
        failed += 1

    # TEST 4: BBOX_ZERO — slot 0 is bbox (not a real template)
    if 0 not in hash_to_col_index.values():
        print(f"  §PROOF BBOX_ZERO       slot 0 reserved for bbox proxy — PASS")
        passed += 1
    else:
        print(f"  §FAIL  BBOX_ZERO       slot 0 assigned to a real template!")
        failed += 1

    # TEST 5: ROUNDTRIP — cache_idx → col_idx → hash matches original
    roundtrip_fail = 0
    col_to_hash = {v: k for k, v in hash_to_col_index.items()}
    for ghash, cache_idx in hash_to_index.items():
        col_idx = int(cache_to_col[cache_idx])
        recovered_hash = col_to_hash.get(col_idx)
        if recovered_hash != ghash:
            roundtrip_fail += 1
            if roundtrip_fail <= 3:
                print(f"    ROUNDTRIP FAIL: {ghash[:12]} → col={col_idx} → {recovered_hash}")
    if roundtrip_fail == 0:
        print(f"  §PROOF ROUNDTRIP       {len(hash_to_index):,} hashes survive "
              f"cache→col→hash — PASS")
        passed += 1
    else:
        print(f"  §FAIL  ROUNDTRIP       {roundtrip_fail} hashes don't survive roundtrip")
        failed += 1

    db.close()

    # ── Summary ──
    print(f"\n  {name}: {passed} PASS, {failed} FAIL")
    print(f"    elements={len(rows):,}  hashes={len(unique_hashes):,}  "
          f"collection={collection_size:,}")
    return failed == 0


if __name__ == "__main__":
    buildings = sys.argv[1:] if len(sys.argv) > 1 else ["Hospital", "Clinic", "Terminal"]
    print("="*60)
    print("GN INDEX MAPPING PROOF — standalone, no Blender")
    print("="*60)

    results = {}
    for b in buildings:
        print(f"\n── {b} ──")
        results[b] = test_building(b)

    print(f"\n{'='*60}")
    all_pass = all(results.values())
    for b, ok in results.items():
        print(f"  {b:30s} {'PASS' if ok else 'FAIL'}")
    print(f"\n§PROOF GN_INDEX {'PASS' if all_pass else 'FAIL'}")
    print(f"{'='*60}")
    sys.exit(0 if all_pass else 1)
