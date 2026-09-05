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
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT
# fix_mm_scale_blobs.py — repair extracted DBs whose component_geometries vertices were
# crushed x1/1000 (mm-unit georeferenced Revit IFC imported via web-ifc; jkr series 2026-07-12).
#
# Deterministic, EXTRACT-only: for each geometry blob, compare its vertex extent against the
# bbox_x/y/z of every element that references it. If ALL referencing elements agree the blob is
# ~1000x too small (ratio>30, median in [900,1100]) → scale vertices by exactly 1000. If all
# agree it's already correct (ratio<30) → leave. Mixed evidence → leave + report (never guess).
# Normals (unit vectors) and faces (indices) are untouched. Writes a NEW file, never in place.
#
# Usage: python3 fix_mm_scale_blobs.py <src.db> <dst.db>
# Witness: §JKR_SCALE_FIX line + per-discipline POST distribution — read it, don't trust exit 0.
import sqlite3, struct, shutil, statistics, sys
from collections import defaultdict

src, dst = sys.argv[1], sys.argv[2]
shutil.copyfile(src, dst)
db = sqlite3.connect(dst)
bbox = {g: (x, y, z) for g, x, y, z in db.execute(
    "SELECT guid,bbox_x,bbox_y,bbox_z FROM element_transforms WHERE bbox_x IS NOT NULL")}
ghash = defaultdict(list)
for g, h in db.execute("SELECT guid, geometry_hash FROM element_instances"):
    ghash[h].append(g)
scaled = left = mixed = 0
updates = []
for h, v in db.execute("SELECT geometry_hash, vertices FROM component_geometries"):
    n = len(v) // 4
    f = struct.unpack(f'<{n}f', v)
    xs, ys, zs = f[0::3], f[1::3], f[2::3]
    if not xs:
        continue
    ext = max(max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs))
    if ext <= 0:
        continue
    rs = [max(bbox[g]) / ext for g in ghash.get(h, []) if g in bbox and max(bbox[g]) > 0]
    if not rs:
        left += 1
        continue
    med, lo, hi = statistics.median(rs), min(rs), max(rs)
    if lo > 30 and 900 <= med <= 1100:
        updates.append((struct.pack(f'<{n}f', *[x * 1000.0 for x in f]), h))
        scaled += 1
    elif hi < 30:
        left += 1
    else:
        mixed += 1
        print(f'MIXED {h} ratios {lo:.1f}..{hi:.1f} elements {len(rs)} — untouched')
db.executemany("UPDATE component_geometries SET vertices=? WHERE geometry_hash=?", updates)
db.commit()
print(f'§JKR_SCALE_FIX scaled={scaled} left_correct={left} mixed_untouched={mixed}')
