# ⚠ DO NOT REMOVE — MANDATORY PREAMBLE
# Scope: 2D Drawing Engine — hard-fail rules, roof hull, zone layout, MEP from IFC, Java migration study
# Read the log after every run. No claims without §PROOF log lines.
# STATUS: DONE

## Context

Session 2026-04-17 audit found 5 real issues not in OPEN_ISSUES.txt. All tracked
issues (I-31 through I-37) are genuinely closed. These are new findings.

### Issue A: SH Roof — Curved Mesh Drawn as BBox Rectangle (HIGH)

**Symptom:** SH roof has 197 mesh vertices (barrel vault) but `_convex_hull_2d()` returns
only 4 hull points — a rectangle. The convex hull is mathematically correct (barrel vault
projects to rectangle corners) but **visually wrong** for elevation view. Violates §1 R1.

**Root cause:** Convex hull is the wrong algorithm for curved roofs. A barrel vault's
projected vertices cluster at extremes. Need concave hull or alpha-shape instead.

**The artificial closing line:** Even when the hull has more points, a polyline close
draws a straight segment between endpoints, inventing a line not in the mesh. Current
guard (`hull_close_SUPPRESSED`) uses `close=False` but the 4-point rectangle is still wrong.

**User directive:** The thickness from exact cross-section is OK, but the artificial
closing line is a hard-break violation. The drawing engine must handle open curves
(curved roof without invented closure).

**Files:**
- `2D_Layout/python/drawing_writer.py` line 590-615: `_convex_hull_2d()` — Andrew's monotone chain
- `2D_Layout/python/drawing_writer.py` line 618-721: `roof_silhouette()` — mesh query + hull
- `2D_Layout/python/drawing_writer_dxf.py` line 2775-2827: roof rendering on elevation
- `2D_Layout/python/drawing_writer_dxf.py` line 3133-3188: `_mesh_hulls()` — roof plan

**Fix approach:**
1. Replace convex hull with **concave hull / alpha-shape** for elements with >20 vertices
2. For open curves (roof edge), draw as open polyline — never close with invented segment
3. Add hard-fail assertion: if element has >20 mesh vertices but hull returns <=4 points,
   RAISE exception — do not silently draw a rectangle
4. Log: `§HARD_FAIL ROOF_HULL vertex_count=197 hull_points=4 — refusing to draw bbox as curve`

### Issue B: DX Front/Rear Elevation Level Labels Missing (MEDIUM)

**Symptom:** DX front/rear elevations have `level_w=0mm` → no level markers drawn.
Left/right elevations work fine (`level_w=30mm`).

**Root cause:** Face classification at line 2618-2629: DX front/rear are `form_face`
(aspect=3.0, X>Y). Form faces get `form_face_level_zone_width_mm=0` (hardcoded default).
Level marker rendering at line 2879 has guard: `if not is_form_face` → skips form faces entirely.

**Fix:** Level markers are required furniture on ALL elevation sheets (§6.2, §8).
Remove the form_face exclusion for level markers. Form faces should still get level
markers — just with a narrower zone if needed.

**Files:**
- `2D_Layout/python/drawing_writer_dxf.py` line 2618-2629: face classification
- `2D_Layout/python/drawing_writer_dxf.py` line 2631-2639: zone width defaults
- `2D_Layout/python/drawing_writer_dxf.py` line 2878-2940: level marker rendering guard

### Issue C: MEP PLUMBING Skipped in Conformity Test (LOW)

**Symptom:** `_identify_drawing_type()` doesn't recognize PLUMBING → conformity test never runs.
**Fix:** Add PLUMBING to template's `drawing_types[]` array.

### Issue D: Duplicate DXF Entity Handles (LOW)

**Symptom:** ezdxf warns about non-unique handles (#1C7, #1C9, #1C8).
**Fix:** Ensure handle uniqueness in entity creation. Low practical impact.

### Issue E: Text Overlaps on DX Floor Plan (LOW)

**Symptom:** 14 label collisions (room names, window labels, area labels).
**Fix:** Collision avoidance pass after label placement. Low priority.

---

## Hard-Fail Rules (implement in this session)

The drawing engine must NEVER silently produce invented geometry. Add these assertions:

```python
class DrawingInventionError(Exception):
    """Raised when the engine would invent geometry not in the source data."""
    pass

# Rule 1: Curved mesh → must produce curved hull (not bbox)
if vertex_count > 20 and hull_point_count <= 4:
    raise DrawingInventionError(
        f"§HARD_FAIL ROOF_HULL: {vertex_count} vertices produced {hull_point_count}-point hull "
        f"(bbox). Curved element requires concave hull or alpha-shape.")

# Rule 2: Open curve → never close with invented segment
if is_open_curve and close_gap_mm > threshold:
    raise DrawingInventionError(
        f"§HARD_FAIL CLOSE_INVENTED: gap={close_gap_mm:.1f}mm between endpoints. "
        f"Cannot close polyline — would invent geometry.")

# Rule 3: Required elevation furniture must exist
if level_marker_count == 0 and face in ('front', 'rear', 'left', 'right'):
    raise DrawingInventionError(
        f"§HARD_FAIL LEVEL_MARKERS_MISSING: face={face} has 0 level markers. "
        f"§6.2 requires level markers on all elevation sheets.")
```

Log every hard-fail with `§HARD_FAIL` prefix so Watchdog can verify.

---

## MEP Drawing: Use IFC Element Info

**User directive:** MEP drawings should use IFC element information to guide rendering,
not generic symbols. The `elements_meta` table has:
- `ifc_class` — IfcFlowTerminal, IfcFlowSegment, IfcFlowFitting, etc.
- `element_name` — "Sprinkler - Pendent", "Elbow Reducing - Threaded", etc.
- `element_type` — Revit family type
- Mesh vertices in `base_geometries` — actual 3D shape

MEP plan should render actual IFC footprints (projected mesh XY hull), not hardcoded
symbols. Same `_mesh_hulls()` code used for roof plan should work for MEP elements.

**Spec ref:** §10.5, §1 R1 (no invention applies to MEP too)

---

## Java Migration Study

**User question:** Should the 2D drawing engine migrate to Java?

**Arguments for Java:**
- Main BIM compiler is Java (DAGCompiler, IFCtoBOM) — unified codebase
- 2D→3D round-trip spec'd in `docs/BIM_Designer_SRS.md` §28 — Java backend natural fit
- Type safety, exception handling, compilation checks
- Grows with the compiler — not a standalone script
- ezdxf (Python) → could use Apache POI or jDXF for DXF generation

**Arguments against:**
- 4,269-line Python codebase already working
- ezdxf is mature, well-tested DXF library
- Python is faster to iterate on drawing logic
- Java DXF libraries less mature than ezdxf

**Recommendation for this session:** Study the migration path. Don't rewrite yet.
Document which Java DXF library to use, what the class hierarchy would look like,
and what the 2D→3D round-trip interface needs. Write findings to `internal/2D_JAVA_MIGRATION.md`.

---

## Pre-flight (every session start)

1. Read `2D_Layout/OPEN_ISSUES.txt`
2. Read `2D_Layout/docs/2D_ARCHITECTURAL_LAYOUT.md` §1 (rules), §5.3 (roof), §12a (zones)
3. Read latest logs in `2D_Layout/output/`
4. Run both buildings:
   ```
   cd 2D_Layout/python
   python3 drawing_writer_dxf.py --all --proof ../input/SH_extracted.db
   python3 drawing_writer_dxf.py --all --proof ../input/DX_extracted.db
   ```
5. Read logs BEFORE any code changes

## Task Order

1. Add `DrawingInventionError` exception class + 3 hard-fail rules
2. Fix Issue A: replace convex hull with concave hull for curved elements
3. Fix Issue B: remove form_face exclusion for level markers
4. Fix Issue C: add PLUMBING to conformity test template
5. MEP: wire `_mesh_hulls()` for MEP element footprints
6. Study: Java migration path → `internal/2D_JAVA_MIGRATION.md`
7. Run both buildings, read logs, verify all §HARD_FAIL rules trigger correctly
8. Update OPEN_ISSUES.txt with any remaining items

## What NOT to Do

- Do NOT invent parametric shapes for MEP — use IFC mesh data
- Do NOT rewrite to Java in this session — study only
- Do NOT change the floor plan or existing working elevation code
- Do NOT touch `component_library.db`

## When Done

Update OPEN_ISSUES.txt. Prepend `# DONE` to this file. Update PROGRESS.md.
