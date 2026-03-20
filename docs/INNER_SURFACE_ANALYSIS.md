# IN G3 Window Drift — Inner Surface Envelope Analysis

> **Context:** Session 42 CP-3 shadow analysis. IN G3 shows 70/206 windows with
> 10-90mm gradient X drift. Main session proved the tack chain is algebraically
> self-cancelling — SET envelope is NOT the root cause.

---

## The Algebraic Proof (Main Session Finding)

The tack offset chain for any element resolves to:

```
worldPosition = allMinX + (floorLbd - allMinX) + (setMinX - floorLbd) + (element.minX - setMinX)
             = element.minX   ← always exact, regardless of intermediate values
```

**Conclusion:** ScopeBomBuilder's envelope computation cannot cause position drift.
The intermediate SET/FLOOR values cancel in the walker's anchor accumulation
(`PlacementCollectorVisitor.onSubAssembly()` lines 168-175).

---

## Inner Surface vs Object Envelope

### The architectural reality

A window is a hosted element — it sits in a wall opening. The extraction AABB
captures the full 3D extent of the window **object**:

```
┌─────────────────────────┐
│  exterior trim/frame     │  ← extraction maxX includes this
│  ┌───────────────────┐  │
│  │                   │  │
│  │   glazing panel   │  │  ← inner surface = wall opening face
│  │                   │  │
│  └───────────────────┘  │
│  sill projection         │  ← extraction minZ includes this
└─────────────────────────┘
```

| Measurement | What it captures | Typical overshoot |
|-------------|-----------------|-------------------|
| Extraction AABB | Full object extent (frame + trim + sill + projections) | 10-90mm per axis |
| Inner surface | Wall opening face (functional placement boundary) | 0mm (definitional) |
| Library mesh | Canonical product geometry (may differ from instance) | Variable per product |

### Why this explains the 10-90mm gradient

IN is a German institutional building (AC11 Institute, IFC2x3). Window types vary
across 5 storeys and 82 rooms:

- **Small bathroom windows:** narrow frame, minimal sill → ~10mm projection
- **Office windows:** standard frame + external sill → ~30-50mm projection
- **Large corridor/stairwell windows:** deep frame + wide sill → ~70-90mm projection

The drift **increases with window size** because frame projection scales with window
dimensions. This produces the observed gradient pattern — not a systematic offset,
but a per-product-type dimensional difference between extraction AABB and library mesh.

---

## Three Possible Mechanisms

### 1. MeshBinder dimensional difference (most likely)

`MeshBinder.bind()` resolves geometry per-instance via GUID (R31). The resolved
library mesh may have different dimensions than the extraction AABB:

- Extraction: 1210mm wide (includes 5mm frame projection each side)
- Library mesh: 1200mm wide (canonical product geometry)
- Delta: 10mm — appears as position shift in `elements_rtree` comparison

The output `elements_rtree` reflects the **library mesh bounds**, not the extraction
AABB. SpatialDiff compares output rtree vs reference rtree → reports 10mm "drift"
that is actually a dimensional difference.

**Diagnostic:** Compare `m_bom_line.allocated_width_mm` (from extraction) against
output `elements_rtree.(maxX-minX)*1000` for IfcWindow in IN. If they differ by
10-90mm, this is confirmed.

### 2. SpatialDiff matching artifact (possible)

If IN's SpatialDiff falls back to position-based matching (no guid→element_ref
overlap), windows of the same type on adjacent rooms could cross-match:

- Room A window at X=4.500 matched to Room B window at X=4.550
- Reported as 50mm drift, but it's a matching error

**Diagnostic:** Check whether IN has guid→element_ref identity matching or uses
position fallback. If identity works, this hypothesis is ruled out.

### 3. Inner surface offset not extracted (root cause)

The IFC model contains `IfcRelVoidsElement` — the relationship that says "this
window is hosted in this wall, at this opening position." The opening position is
the **inner surface** — the wall face where the window meets the room.

Currently not extracted (R21 TODO). Without it, the pipeline uses the object AABB
as the position reference. The AABB includes frame projections that vary per product.

**Fix (long-term):** R21 extracts `host_element_ref` from `IfcRelVoidsElement`.
With the host wall known, the inner surface position can be computed:
`wall.innerFace + opening.offset` — invariant to frame projection.

**Fix (short-term):** SpatialDiff should compare **centroids** instead of AABB
edges for hosted elements (IfcDoor, IfcWindow). Centroids are symmetric — frame
projections add equally on both sides, so the centroid stays at the opening center
regardless of frame depth.

---

## Recommendation

1. **Immediate:** Run diagnostic for mechanism #1 (MeshBinder dimension comparison)
2. **If confirmed:** This is not a compilation drift — it's a comparison artifact.
   Fix SpatialDiff to use centroid comparison for hosted elements (doors/windows)
3. **Long-term:** R21 (extract `IfcRelVoidsElement`) eliminates the problem at source
   by providing inner surface position as a column, not computed from AABB

---

## Spec References

- [BBC.md](BOMBasedCompilation.md) §4 — tack convention (parent-relative offsets)
- [BBC.md](BOMBasedCompilation.md) §4.2 — BUFFER completeness invariant
- [LAST_MILE_PROBLEM.md](LAST_MILE_PROBLEM.md) R21 — `host_element_ref` extraction
- [LAST_MILE_PROBLEM.md](LAST_MILE_PROBLEM.md) §Gap 9.4 — per-instance geometry (R31)
- [DocValidate.md](DocValidate.md) §15.6 — Schema-Not-Geometry principle
- [ACInstituteAnalysis.md](ACInstituteAnalysis.md) — IN building analysis (699 elements)
