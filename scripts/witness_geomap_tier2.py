#!/usr/bin/env python3
"""
W-GEOMAP-TIER2 — prompts/RESUME_IFC_BOM_GEOMAPPING.md §WITNESS.

ISSUE PROVED: every accuracy number stored in geomap/geomap_rules.json
is REAL — recomputing the split-half protocol from the shipped corpus DBs
reproduces the artifact's measured {n, top1, top3, own_class_in_band} EXACTLY,
per building. This kills the "unmeasured 90% accurate" failure mode the spec
was written against: if the artifact ever claims a number the data doesn't
reproduce, this witness goes RED.

Also asserts the two structural guarantees the Tier-2 design rests on:
  - no band ships with <3 supporting samples (no 1-sample "bands"),
  - the artifact refuses cross-building use (measured-dead, 8-17% top-1).
Exit 0 only if every assertion holds. Read the §-log, not the exit code alone.
"""
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
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import mine_geomap as mg  # noqa: E402

fails = 0


def check(name, cond, detail=""):
    global fails
    status = "PASS" if cond else "FAIL"
    if not cond:
        fails += 1
    print(f"§W-GEOMAP-TIER2 {status}: {name} {detail}")


def main():
    rules = json.load(open(os.path.join(ROOT, "geomap", "geomap_rules.json")))
    for tag, entry in sorted(rules["buildings"].items()):
        rows = mg.load_dims(tag, mg.MANIFEST[tag])
        measured = mg.measure_split_half(rows)
        stored = entry["measured"]
        check(f"{tag} measured accuracy reproduces", measured == stored,
              f"(recomputed {measured} vs stored {stored})")
        weak = [c for c, b in entry["classes"].items() if b["count"] < 3]
        check(f"{tag} no under-supported bands", not weak, f"({weak})")
        check(f"{tag} frame tag present", bool(entry["frame"].get("frame")),
              f"({entry['frame']})")
    check("cross-building refuse encoded",
          "REFUSE" in rules.get("cross_building", ""),
          f"({rules.get('cross_building','MISSING')[:60]})")
    print(f"§W-GEOMAP-TIER2 RESULT: {'GREEN' if fails == 0 else f'RED ({fails} failures)'}")
    sys.exit(1 if fails else 0)


if __name__ == "__main__":
    main()
