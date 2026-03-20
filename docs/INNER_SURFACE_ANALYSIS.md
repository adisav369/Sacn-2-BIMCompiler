# Inner Surface Envelope Analysis

> **Origin:** Session 42 shadow analysis (CP-3 session observing main session's IN G3 drift investigation).
> **Status:** Analysis confirmed and implemented in session 43. Core findings now in
> [BBC.md](BOMBasedCompilation.md) §4.2.1 (qualifier table) and §4.2.2 (PHANTOM generation rule).

---

## Summary

The IN G3 window drift (70/206 windows, 10-90mm gradient) was diagnosed as an
**inner surface vs object envelope** mismatch, not a compilation bug.

**Root cause:** Extraction AABB captures the full window object (frame + trim + sill
projections). Library mesh captures canonical product geometry. These differ by
10-90mm depending on frame profile. SpatialDiff compared AABB edges, reporting
dimensional differences as position drift.

**Resolution (session 43, commit `d1b5af8`):**
- `SpatialDiff.classifyForClass()` — centroid comparison for `IfcWindow`/`IfcDoor`
  (centroids are invariant to asymmetric frame projection)
- `m_bom.aabb_qualifier` column: `INNER`/`STRUCTURAL`/`OUTER`/`OPENING`
- ScopeBomBuilder: SET BOMs tagged `OUTER`, PHANTOM lines fill INNER-OUTER gap
- FloorRoomBomBuilder: FLOOR ROOM BOMs tagged `INNER` (YAML architect intent)
- **Result:** DX 7→8/10 (W-TOT PASS via centroid fix). No regressions.

---

## The Algebraic Proof

The tack offset chain for any element resolves to:

```
worldPosition = allMinX + (floorLbd - allMinX) + (setMinX - floorLbd) + (element.minX - setMinX)
             = element.minX   ← always exact, regardless of intermediate values
```

**Conclusion:** ScopeBomBuilder's envelope computation cannot cause position drift.
The intermediate SET/FLOOR values cancel in the walker's anchor accumulation
(`PlacementCollectorVisitor.onSubAssembly()` lines 168-175). This was proven by
the main session and confirmed by shadow analysis.

---

## AABB Qualifier — Architectural Concept

A single AABB without qualification is ambiguous. Construction has four distinct
envelope concepts:

| Qualifier | What it measures | Use case |
|-----------|-----------------|----------|
| **INNER** | Room clear volume, finish-to-finish | Furniture placement, PHANTOM index, Click-to-Place (G-13) |
| **STRUCTURAL** | Centerline-to-centerline (structural grid) | Grid layout, structural analysis, column spacing |
| **OUTER** | Full object extent including projections | Clash detection, extraction AABB, element extents |
| **OPENING** | Clear opening in host element | Door/window placement, accessibility clearance |

**Implemented:** `m_bom.aabb_qualifier TEXT DEFAULT 'OUTER'` — see [BBC.md](BOMBasedCompilation.md) §4.2.1.

**Design implications:**
- Wireframe bounding boxes (WF-BB §26) render INNER for rooms, OUTER for elements
- PHANTOM lines represent remaining INNER volume after OUTER children subtracted
- Click-to-Place (G-13) queries PHANTOMs using INNER — "where can I place this?"
- Wall joints align by INNER face (not outer, not centerline)
- BomValidator can enforce: parent INNER >= SUM(children OUTER) + wall thickness

---

## Inner Surface vs Object Envelope

A window is a hosted element — it sits in a wall opening:

```
┌─────────────────────────┐
│  exterior trim/frame     │  ← extraction AABB (OUTER) includes this
│  ┌───────────────────┐  │
│  │                   │  │
│  │   glazing panel   │  │  ← inner surface = wall opening face (OPENING)
│  │                   │  │
│  └───────────────────┘  │
│  sill projection         │  ← extraction AABB minZ includes this
└─────────────────────────┘
```

The 10-90mm gradient across IN windows correlates with window type:
- Small bathroom windows: narrow frame → ~10mm projection
- Office windows: standard frame + external sill → ~30-50mm
- Large corridor/stairwell windows: deep frame → ~70-90mm

**Short-term fix (implemented):** Centroid comparison for hosted elements.
Centroids are symmetric — frame projections cancel.

**Long-term fix (R21 TODO):** Extract `host_element_ref` from `IfcRelVoidsElement`.
With the host wall known, the inner surface position is a column, not computed from AABB.
See [LAST_MILE_PROBLEM.md](LAST_MILE_PROBLEM.md) R21.

---

## PHANTOM Spatial Index (BBC.md §4.2)

PHANTOMs are the SAP empty storage bin principle applied to BOM spatial management:

```
INNER envelope (YAML aabb_mm) = room architect intent
OUTER envelope (computed)     = placed element extents
PHANTOM                       = INNER - OUTER (remaining capacity per axis)
```

**Session 43 result:** 66 PHANTOM lines across 82 IN SET BOMs.
BOMWalker skips PHANTOMs at output (line 347-357 — confirmed).
Click-to-Place (G-13) queries: `SELECT * FROM m_bom_line WHERE component_type='PHANTOM'`
→ instant spatial availability without geometry computation.

---

## Remaining Work

| Item | Status | What |
|------|--------|------|
| R21 | TODO | Extract `host_element_ref` from `IfcRelVoidsElement` — eliminates AABB-vs-opening ambiguity |
| IN G3 | 9/10 | 120 window shifts remain (CLUSTER expansion coordinates — separate root cause) |
| DX | 8/10 | C9 87 axis mismatches (MIRROR W↔D swap — CP-2 scope) |
| AABB-Q | DONE | `m_bom.aabb_qualifier` column implemented, SET=OUTER, FLOOR ROOM=INNER |
| PHANTOM | DONE | ScopeBomBuilder writes PHANTOM lines after SET children |
| Centroid fix | DONE | SpatialDiff uses centroid for IfcWindow/IfcDoor |

---

## Spec References

- [BBC.md](BOMBasedCompilation.md) §4.2.1 — AABB qualifier table (session 43)
- [BBC.md](BOMBasedCompilation.md) §4.2.2 — PHANTOM generation rule (session 43)
- [BBC.md](BOMBasedCompilation.md) §4 — tack convention (parent-relative offsets)
- [LAST_MILE_PROBLEM.md](LAST_MILE_PROBLEM.md) R21 — `host_element_ref` extraction
- [ACTION_ROADMAP.md](ACTION_ROADMAP.md) AABB-Q — known debt entry
- [DocValidate.md](DocValidate.md) §15.6 — Schema-Not-Geometry principle
- [ACInstituteAnalysis.md](ACInstituteAnalysis.md) — IN building analysis (699 elements)
