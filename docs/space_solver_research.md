# Space Solver Research

**Status:** Research / Design — not yet implemented
**Supplements:** PREFAB_ARCHITECTURE.md (Three Compilation Modes), VIEW_CONTRACTS.md §2.3

---

## Context

When a new building has no Rosetta Stone reference IFC, `v_verified_room_boundary` returns
zero rows. The compiler falls back to DSL-qty generative mode (Mode B-pure). Before invoking
a full constraint solver, a simpler path exists that covers the majority of production cases:
the **Template Topology Path**.

---

## Template Topology Path

Before invoking the constraint solver, find the closest matching Rosetta Stone room by type
and dimension, apply a coordinate scale transform, and write the result as `DERIVED_MM`.

This path is cheaper than the constraint solver and sufficient for buildings that are
topologically similar to existing Rosetta Stones. The constraint solver is needed only when
no close match exists.

### Lookup Query

```sql
SELECT *
FROM v_verified_room_boundary
WHERE room_type = ?
ORDER BY ABS(width_mm - ?) + ABS(depth_mm - ?) ASC
LIMIT 1;
-- bind: (target_room_type, target_width_mm, target_depth_mm)
```

This returns the Rosetta Stone room whose dimensions are closest to the target by L1 distance.
`v_verified_room_boundary` serves only rows with `coordinate_frame IN ('IFC_GLOBAL_MM',
'LOCAL_MM', 'DRAWING_MM', 'CONSTRAINT_SOLVED', 'DERIVED_MM')` — no hand-approximated data
enters the match pool.

### Scale Transform

Given source room `s` (from the Stone) and target dimensions:

```
scale_x = target_width_mm  / s.width_mm
scale_y = target_depth_mm  / s.depth_mm

scaled_min_x = s.min_x_mm * scale_x
scaled_max_x = s.max_x_mm * scale_x
scaled_min_y = s.min_y_mm * scale_y
scaled_max_y = s.max_y_mm * scale_y
```

Child element offsets from `ad_bom_child_param` scale proportionally:
```
child_dx_scaled = child_dx * scale_x
child_dy_scaled = child_dy * scale_y
child_dz        = unchanged  (vertical positions are not scaled)
```

Wall fraction positions (`fractionX`, `fractionY` in `ad_element_rule`) are **scale-invariant**
— a door at 30% along a wall stays at 30% regardless of the wall's new length. No
`ad_element_rule` fraction values need updating.

### Provenance Record

Write the derived room boundary with:

```sql
INSERT INTO ad_room_boundary (
    building_type, room_type, storey,
    min_x_mm, max_x_mm, min_y_mm, max_y_mm,
    extracted_from,
    coordinate_frame
) VALUES (
    ?, ?, ?,
    ?, ?, ?, ?,
    'TRANSFORMED_FROM_{source_room_id}',   -- e.g. 'TRANSFORMED_FROM_42'
    'DERIVED_MM'
);
```

`extracted_from` records the source room's primary key so the derivation is traceable.
`coordinate_frame = 'DERIVED_MM'` makes the row visible to `v_verified_room_boundary`
and therefore to the compiler.

---

## When to Use Template Topology vs Constraint Solver

| Situation | Path |
|---|---|
| New room type matches an existing Stone room type, dimensions within ~30% | Template Topology |
| New room has same adjacency pattern as a Stone (e.g., bathroom shares wall with bedroom) | Template Topology |
| New room type has no match in the Stone pool (new typology) | Constraint Solver |
| Code-mandated minimum clearances differ from Stone (different jurisdiction) | Constraint Solver |
| Room shape is non-rectangular (L-shaped, angled wall) | Constraint Solver |

The 30% threshold is a heuristic. Scale distortion above ~30% in either axis starts producing
BOM child offsets that violate clearance rules (e.g., wardrobe pushed past the door). Validate
with the G8 nearest-neighbour gate after writing DERIVED_MM rows.

---

## Constraint Solver (Future)

When no close Stone match exists, the SpaceSolver derives room boundaries from:
- UBBL minimum room dimensions (by room type)
- Adjacency rules (bathroom must share wall with bedroom or corridor)
- Building envelope constraints (DSL floor dimensions)

Output: `ad_room_boundary` rows with `coordinate_frame = 'CONSTRAINT_SOLVED'`.

These rows are served by `v_verified_room_boundary` and pass through to the compiler
identically to IFC-extracted rows. The solver writes data; the compiler reads data;
no compiler code changes.

Research references:
- `docs/AD_Events_Spatial_Rules.docx` — spatial rule and adjacency rule schema
- `ad_spatial_rule`, `ad_callout_rule` tables — rule storage layer

---

*BIM Intent Compiler | Space Solver Research | February 2026*
