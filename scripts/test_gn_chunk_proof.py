#!/usr/bin/env python3
"""
# ⚠ DO NOT REMOVE
# Scope: S176 — Chunked sub-collection proof tests
# Read the log after every run.

Standalone proof: chunked GN sub-collection index mapping is correct.
No Blender needed — pure math on the extracted DB.

Proves:
  §PROOF CHUNK_PARTITION      — every hash lands in exactly one chunk
  §PROOF LOCAL_INDEX_RANGE    — all local indices in [0, CHUNK_SIZE]
  §PROOF CHUNK_BBOX_ZERO      — slot 0 in every chunk is bbox proxy
  §PROOF CROSS_CHUNK_ISOLATION — no element references an index in wrong chunk
  §PROOF CHUNK_ROUNDTRIP      — hash → chunk,local_idx → hash survives
  §PROOF HALT_SUPPRESS        — no swaps fire during simulated orbit
  §PROOF HALT_FIRE            — swap fires after debounce delay

Usage:
  python3 scripts/test_gn_chunk_proof.py Hospital
  python3 scripts/test_gn_chunk_proof.py Clinic Terminal
"""
import sqlite3
import sys
import os
import time
import numpy as np
from math import ceil

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

CHUNK_SIZE = 2000  # max template objects per sub-collection (excl. bbox proxy) — mirrors production


# ── Chunking math (mirrors stage2_library_linker.py S176 implementation) ──

def build_chunks(unique_hashes):
    """Partition sorted hashes into chunks of CHUNK_SIZE.

    Returns:
        chunks: list of (chunk_id, [hashes])
        hash_to_chunk: dict ghash -> chunk_id
        hash_to_local_index: dict ghash -> int (1-based; 0 = bbox)
    """
    sorted_hashes = sorted(unique_hashes)
    n_chunks = ceil(len(sorted_hashes) / CHUNK_SIZE)

    chunks = []
    hash_to_chunk = {}
    hash_to_local_index = {}

    for chunk_id in range(n_chunks):
        start = chunk_id * CHUNK_SIZE
        end = min(start + CHUNK_SIZE, len(sorted_hashes))
        chunk_hashes = sorted_hashes[start:end]
        chunks.append((chunk_id, chunk_hashes))

        # Build names in the same alphabetical order GN uses
        # S178: use full hash (T_{gh}) — no collision at any chunk size
        bbox_name = f"!bbox_{chunk_id:03d}"
        tpl_names = [(f"T_{gh}", gh) for gh in chunk_hashes]
        all_names = sorted([bbox_name] + [n for n, _ in tpl_names])
        name_to_idx = {name: i for i, name in enumerate(all_names)}

        for tpl_name, gh in tpl_names:
            local_idx = name_to_idx[tpl_name]
            hash_to_chunk[gh] = chunk_id
            hash_to_local_index[gh] = local_idx

    return chunks, hash_to_chunk, hash_to_local_index


# ── Halt mode state machine (mirrors dlod_handler.py S176) ──

class HaltStateMachine:
    """Simulates the camera debounce state machine."""

    def __init__(self, halt_delay=0.200):
        self.halt_delay = halt_delay
        self._last_move_time = 0.0
        self._is_orbiting = False
        self.swap_count = 0

    def camera_moved(self, t):
        """Call when camera position changes."""
        self._last_move_time = t
        self._is_orbiting = True

    def tick(self, t):
        """Returns True if LOD swap should fire."""
        if self._is_orbiting:
            elapsed = t - self._last_move_time
            if elapsed < self.halt_delay:
                return False  # still within debounce window
            self._is_orbiting = False
            self.swap_count += 1
            return True  # halt fired — do LOD swap
        return False  # stationary, no pending swap


# ── Tests ──

def test_building_chunks(name):
    """Test chunked index mapping for a building."""
    db_path = os.path.join(PROJECT, "DAGCompiler/lib/input", f"{name}_extracted.db")
    if not os.path.exists(db_path):
        print(f"SKIP — {db_path} not found")
        return None

    db = sqlite3.connect(db_path)
    tables = {r[0] for r in db.execute(
        "SELECT name FROM sqlite_master WHERE type='table'")}

    if "elements_meta" not in tables:
        print(f"SKIP — no elements_meta in {name}")
        return None

    rows = db.execute("""
        SELECT m.guid, m.discipline, ei.geometry_hash
        FROM elements_meta m
        JOIN element_instances ei ON m.guid = ei.guid
    """).fetchall()
    db.close()

    if not rows:
        print(f"SKIP — 0 elements in {name}")
        return None

    unique_hashes = sorted(set(r[2] for r in rows if r[2]))
    n_chunks_expected = ceil(len(unique_hashes) / CHUNK_SIZE)

    print(f"  elements={len(rows):,}  hashes={len(unique_hashes):,}  "
          f"chunks={n_chunks_expected}")

    # Build chunks
    chunks, hash_to_chunk, hash_to_local_index = build_chunks(unique_hashes)

    passed = 0
    failed = 0

    # TEST 1: CHUNK_PARTITION — every hash in exactly one chunk
    all_chunk_hashes = set()
    dupes = 0
    for chunk_id, chunk_hashes in chunks:
        for gh in chunk_hashes:
            if gh in all_chunk_hashes:
                dupes += 1
            all_chunk_hashes.add(gh)
    missing = set(unique_hashes) - all_chunk_hashes
    extra = all_chunk_hashes - set(unique_hashes)

    if dupes == 0 and len(missing) == 0 and len(extra) == 0:
        print(f"  §PROOF CHUNK_PARTITION      {len(unique_hashes):,} hashes → "
              f"{len(chunks)} chunks, no dupes/missing — PASS")
        passed += 1
    else:
        print(f"  §FAIL  CHUNK_PARTITION      dupes={dupes} missing={len(missing)} "
              f"extra={len(extra)}")
        failed += 1

    # TEST 2: LOCAL_INDEX_RANGE — all local indices in [0, CHUNK_SIZE]
    out_of_range = 0
    for chunk_id, chunk_hashes in chunks:
        chunk_size_with_bbox = len(chunk_hashes) + 1  # +1 for bbox
        for gh in chunk_hashes:
            local_idx = hash_to_local_index[gh]
            if local_idx < 0 or local_idx >= chunk_size_with_bbox:
                out_of_range += 1
                if out_of_range <= 3:
                    print(f"    OUT_OF_RANGE: chunk={chunk_id} {gh[:12]} "
                          f"local_idx={local_idx} max={chunk_size_with_bbox-1}")

    if out_of_range == 0:
        print(f"  §PROOF LOCAL_INDEX_RANGE    all indices valid — PASS")
        passed += 1
    else:
        print(f"  §FAIL  LOCAL_INDEX_RANGE    {out_of_range} out of range")
        failed += 1

    # TEST 3: CHUNK_BBOX_ZERO — slot 0 is bbox in every chunk
    bbox_fail = 0
    for chunk_id, chunk_hashes in chunks:
        # Build chunk names and check slot 0
        bbox_name = f"!bbox_{chunk_id:03d}"
        tpl_names = [f"T_{gh}" for gh in chunk_hashes]
        all_names = sorted([bbox_name] + tpl_names)
        if all_names[0] != bbox_name:
            bbox_fail += 1
            if bbox_fail <= 3:
                print(f"    BBOX NOT AT ZERO: chunk={chunk_id} "
                      f"slot0={all_names[0]} bbox={bbox_name}")

    if bbox_fail == 0:
        print(f"  §PROOF CHUNK_BBOX_ZERO      !bbox at slot 0 in all "
              f"{len(chunks)} chunks — PASS")
        passed += 1
    else:
        print(f"  §FAIL  CHUNK_BBOX_ZERO      {bbox_fail} chunks have bbox NOT at slot 0")
        failed += 1

    # TEST 3B: NAME_COLLISION — no two hashes in same chunk share an object name
    # S178 fix: T_{full_hash} eliminates the Tpl_{hash[:12]} collision at 2000/chunk
    collision_fail = 0
    for chunk_id, chunk_hashes in chunks:
        names = [f"T_{gh}" for gh in chunk_hashes]
        if len(names) != len(set(names)):
            from collections import Counter
            duped = [n for n, c in Counter(names).items() if c > 1]
            collision_fail += len(duped)
            if collision_fail <= 3:
                print(f"    NAME_COLLISION: chunk={chunk_id} dupes={duped[:3]}")

    if collision_fail == 0:
        print(f"  §PROOF NAME_COLLISION       no name collisions in any chunk "
              f"(T_{{hash}} naming) — PASS")
        passed += 1
    else:
        print(f"  §FAIL  NAME_COLLISION       {collision_fail} name collisions detected")
        failed += 1

    # TEST 4: CROSS_CHUNK_ISOLATION — no element references wrong chunk index
    cross_fail = 0
    for r in rows:
        gh = r[2]
        if gh not in hash_to_chunk:
            continue
        chunk_id = hash_to_chunk[gh]
        local_idx = hash_to_local_index[gh]
        # Verify the chunk actually contains this hash
        _, chunk_hashes = chunks[chunk_id]
        if gh not in chunk_hashes:
            cross_fail += 1
            if cross_fail <= 3:
                print(f"    CROSS_CHUNK: {gh[:12]} claims chunk={chunk_id} "
                      f"but not in chunk's hash list")

    if cross_fail == 0:
        print(f"  §PROOF CROSS_CHUNK_ISOLATION {len(rows):,} elements correct — PASS")
        passed += 1
    else:
        print(f"  §FAIL  CROSS_CHUNK_ISOLATION {cross_fail} cross-chunk refs")
        failed += 1

    # TEST 5: CHUNK_ROUNDTRIP — hash → (chunk, local_idx) → hash
    roundtrip_fail = 0
    # Build reverse map: (chunk_id, local_idx) → hash
    chunk_local_to_hash = {}
    for gh in unique_hashes:
        cid = hash_to_chunk[gh]
        lid = hash_to_local_index[gh]
        chunk_local_to_hash[(cid, lid)] = gh

    for gh in unique_hashes:
        cid = hash_to_chunk[gh]
        lid = hash_to_local_index[gh]
        recovered = chunk_local_to_hash.get((cid, lid))
        if recovered != gh:
            roundtrip_fail += 1
            if roundtrip_fail <= 3:
                print(f"    ROUNDTRIP FAIL: {gh[:12]} → ({cid},{lid}) → {recovered}")

    if roundtrip_fail == 0:
        print(f"  §PROOF CHUNK_ROUNDTRIP      {len(unique_hashes):,} hashes survive — PASS")
        passed += 1
    else:
        print(f"  §FAIL  CHUNK_ROUNDTRIP      {roundtrip_fail} fail")
        failed += 1

    return (passed, failed)


def test_halt_mode():
    """Test camera debounce state machine."""
    print(f"\n── HALT MODE ──")
    passed = 0
    failed = 0

    hsm = HaltStateMachine(halt_delay=0.200)

    # Simulate orbit: camera moves at t=0, 0.05, 0.10, 0.15
    for t in [0.0, 0.05, 0.10, 0.15]:
        hsm.camera_moved(t)
        fired = hsm.tick(t)
        if fired:
            failed += 1
            print(f"  §FAIL  HALT_SUPPRESS  swap fired during orbit at t={t}")

    if hsm.swap_count == 0:
        print(f"  §PROOF HALT_SUPPRESS        0 swaps during 4-frame orbit — PASS")
        passed += 1
    else:
        print(f"  §FAIL  HALT_SUPPRESS        {hsm.swap_count} swaps during orbit")
        failed += 1

    # Camera stops — tick at t=0.20 (still within 200ms of last move at 0.15)
    fired = hsm.tick(0.20)
    if fired:
        print(f"  §FAIL  HALT_DEBOUNCE   fired too early at t=0.20 (last_move=0.15)")
        failed += 1

    # Tick at t=0.36 (>200ms after last move at 0.15)
    fired = hsm.tick(0.36)
    if fired:
        print(f"  §PROOF HALT_FIRE            swap fired at t=0.36 "
              f"(200ms after stop at 0.15) — PASS")
        passed += 1
    else:
        print(f"  §FAIL  HALT_FIRE            swap did NOT fire at t=0.36")
        failed += 1

    # Subsequent ticks should NOT fire (no new movement)
    fired2 = hsm.tick(0.50)
    if not fired2:
        print(f"  §PROOF HALT_IDLE            no spurious fire at t=0.50 — PASS")
        passed += 1
    else:
        print(f"  §FAIL  HALT_IDLE            spurious fire at t=0.50")
        failed += 1

    return (passed, failed)


if __name__ == "__main__":
    buildings = sys.argv[1:] if len(sys.argv) > 1 else ["Hospital", "Clinic", "Terminal"]
    print("=" * 60)
    print("S176 CHUNK + HALT PROOF — standalone, no Blender")
    print(f"CHUNK_SIZE = {CHUNK_SIZE}")
    print("=" * 60)

    total_pass = 0
    total_fail = 0

    # Chunk partition tests per building
    for b in buildings:
        print(f"\n── {b} ──")
        result = test_building_chunks(b)
        if result:
            total_pass += result[0]
            total_fail += result[1]

    # Halt mode test
    result = test_halt_mode()
    total_pass += result[0]
    total_fail += result[1]

    # Summary
    print(f"\n{'=' * 60}")
    tag = "PASS" if total_fail == 0 else "FAIL"
    print(f"§PROOF S176_CHUNK_HALT {tag}  "
          f"{total_pass} pass, {total_fail} fail")
    print(f"{'=' * 60}")
    sys.exit(0 if total_fail == 0 else 1)
