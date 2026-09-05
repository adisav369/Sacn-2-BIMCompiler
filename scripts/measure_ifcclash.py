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
# measure_ifcclash.py — W-IFCCLASH-TERMINAL
# HONEST head-to-head: run the canonical Bonsai/IfcOpenShell clash engine (IfcClash) on the
# REAL Terminal IFC, STR vs MEP, intersection mode. Times it END-TO-END including the cost IfcClash
# pays every run that we DON'T: parsing the 593MB SPF + tessellating every selected element.
#
# Apples-to-apples discipline split (mirrors our extracted Terminal_meta.elements_meta):
#   STR  = IfcBeam, IfcColumn, IfcMember        (1032 elements in our split)
#   MEP  = IfcFlowTerminal, IfcFlowController    (277 elements in our split)
# IfcClash filters by IFC class (mode 'i'), tessellating ONLY the selected elements (include=...).
#
# Run: python3 scripts/measure_ifcclash.py   (reads build/erp/measure_ifcclash.log)
# NON-INVENT: every number below is wall-clocked here; nothing is estimated.
import sys, os, time, json, logging

sys.path.insert(0, "/home/red1/IfcOpenShell/src/ifcclash")
from ifcclash.ifcclash import Clasher, ClashSettings  # noqa: E402

IFC = "/home/red1/Downloads/TerminalMerged.ifc"
OUT = "/home/red1/bim-compiler/build/erp/ifcclash_terminal_out.json"

CLASH_SETS = [{
    "name": "Terminal STR vs MEP",
    "a": [{"file": IFC, "mode": "i", "selector": "IfcBeam, IfcColumn, IfcMember"}],
    "b": [{"file": IFC, "mode": "i", "selector": "IfcFlowTerminal, IfcFlowController"}],
    "mode": "intersection",
    "tolerance": 0.01,     # 10mm — matches the coordination tolerance we report
    "check_all": True,
}]

print("\n═══ W-IFCCLASH-TERMINAL — canonical IfcClash, STR vs MEP on Terminal (593MB IFC) ═══\n")
print("  IFC: %s  (%.0f MB)" % (IFC, os.path.getsize(IFC) / 1e6))
print("  STR selector = IfcBeam, IfcColumn, IfcMember")
print("  MEP selector = IfcFlowTerminal, IfcFlowController")
print("  mode = intersection, tolerance = 10mm, check_all = true\n")

settings = ClashSettings()
settings.output = OUT
settings.logger = logging.getLogger("Clash")
settings.logger.setLevel(logging.INFO)
h = logging.StreamHandler(sys.stdout)
h.setLevel(logging.INFO)
h.setFormatter(logging.Formatter("  §IFCCLASH %(message)s"))
settings.logger.addHandler(h)

clasher = Clasher(settings)
clasher.clash_sets = CLASH_SETS

t0 = time.time()
clasher.clash()
total = time.time() - t0

clashes = CLASH_SETS[0].get("clashes", {})
print("\n  §IFCCLASH-RESULT end_to_end_s=%.2f clashes=%d" % (total, len(clashes)))

# sample a few real clashes with their reported penetration distance (the depth we will match in B/§3)
sample = list(clashes.values())[:6]
for c in sample:
    print("  §IFCCLASH verdict %s" % json.dumps({
        "type": c.get("type"),
        "a_class": c.get("a_ifc_class"),
        "b_class": c.get("b_ifc_class"),
        "distance_mm": round((c.get("distance") or 0) * 1000, 1),
        "p1": [round(v, 3) for v in (c.get("p1") or [])],
    }))

print("\n── VERDICT (what IfcClash pays that we don't) ──")
print("  End-to-end (parse 593MB SPF + tessellate selected + clash): %.2f s" % total)
print("  Real geometric clashes (STR∩MEP, intersection): %d" % len(clashes))
print("  This is the REAL head-to-head number — replaces the 'tens of seconds' estimate.")
print("  Our path skips the per-run tessellation: geometry is pre-extracted, broadphase is a SQL R-tree.\n")
