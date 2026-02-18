# Duplex Finish Floor Matching Gap Analysis

**Date**: 2026-02-15
**Scope**: Research only - no changes made
**Objective**: Identify room fraction adjustments to maximize finish floor slab matches

---

## Executive Summary

The Duplex has potential to gain **+14 finish floor slab matches** (7 per unit × 2 units) by adjusting room fractions in the `ad_unit_type_room` table. Current finish floors don't match reference dimensions because room boundaries don't align with reference layout.

### Key Finding: Room Overlap Required

The reference design uses **overlapping finish floors**:
- Ground floor: Full-unit wood (7.966 × 9.308m) as base layer, with kitchen/bath tiles overlaid in specific zones
- Upper floor: Bedroom + hallway + bathroom zones overlap (total depth fractions = 1.94 > 1.0)

Our current model may not support overlapping room finish floors, which limits achievable matches.

---

## Reference vs Output Dimensions

### Ground Floor (per unit)

| Element | Reference | Output | Delta | Status |
|---------|-----------|--------|-------|--------|
| Kitchen tile | 5.809 × 2.230 m | 5.815 × 2.141 m | +6mm W, -89mm D | Width ✓, Depth ✗ |
| Bath tile | 1.456 × 2.171 m | 2.947 × 1.303 m | +1491mm W, -868mm D | Both ✗✗ |
| Full-unit wood | 7.966 × 9.308 m | 2.151 × 9.308 (foyer)<br>+ 5.815 × 5.119 (living) | Segmented | Layout ✗ |

### Upper Floor (per unit)

| Element | Reference | Output | Delta | Status |
|---------|-----------|--------|-------|--------|
| Bedroom wood | 3.708 × 6.249 m | 5.975 × 4.654 m | +2267mm W, -1595mm D | Both ✗✗ |
| Hallway wood | 2.089 × 5.548 m | 1.992 × 5.212 m | -97mm W, -336mm D | Both ✗ |
| Bath tile | 1.524 × 4.160 m | 1.992 × 2.979 m | +468mm W, -1181mm D | Both ✗ |

---

## Prioritized Recommendations

### Phase 1: Immediate Wins (High Confidence, +6 slabs)

#### 1. Ground Floor Kitchen Depth (+2 slabs)
**Current**: 5.815 × 2.141 m (frac: 0.730 × 0.230)
**Target**: 5.809 × 2.230 m
**Confidence**: HIGH (width already perfect, minimal layout disruption)

**Required Change**:
```sql
UPDATE ad_unit_type_room
SET frac_min_y = 0.514, frac_max_y = 0.753
WHERE unit_type_id = 'DUPLEX_GROUND' AND room_key = 'kitchen';
```

**Calculation**:
- Target depth fraction: 2.230 / 9.308 = 0.240
- Position in unit: Y:[4.783m, 7.013m] → frac [0.514, 0.753]

**Impact**: Kitchen tile matches in both units (+2 slabs)

---

#### 2. Upper Floor Bedroom Dimensions (+4 slabs)
**Current**: 5.975 × 4.654 m (frac: 0.750 × 0.500)
**Target**: 3.708 × 6.249 m
**Confidence**: HIGH (dimensions achievable)

**Required Changes**:
```sql
UPDATE ad_unit_type_room
SET frac_max_x = 0.7155, frac_max_y = 0.6713
WHERE unit_type_id = 'DUPLEX_UPPER' AND room_key = 'bedroom_1';

UPDATE ad_unit_type_room
SET frac_max_x = 0.7155, frac_min_y = 0.3287
WHERE unit_type_id = 'DUPLEX_UPPER' AND room_key = 'bedroom_2';
```

**Calculation**:
- Width: 3.708 / 7.966 = 0.4655
- Depth: 6.249 / 9.308 = 0.6713
- Bedroom_1: X:[0.25, 0.7155], Y:[0.0, 0.6713] → 3.708 × 6.249 ✓
- Bedroom_2: X:[0.25, 0.7155], Y:[0.3287, 1.0] → 3.708 × 6.249 ✓

**Side Effects**:
- Bedrooms shrink significantly (narrower, deeper)
- Creates 0.2845 extra X-fraction (275mm) for hallway/utility expansion
- **NOTE**: Bedrooms overlap in Y-range [0.3287, 0.6713] (3.189m overlap)

**Impact**: 4 bedroom wood matches (2 per unit × 2 units)

---

### Phase 2: Medium Complexity (+4 slabs)

#### 3. Upper Floor Hallway (+2 slabs)
**Current**: 1.992 × 5.212 m (frac: 0.250 × 0.560)
**Target**: 2.089 × 5.548 m
**Confidence**: MEDIUM (requires utility room adjustment)

**Required Change**:
```sql
UPDATE ad_unit_type_room
SET frac_max_x = 0.2622, frac_min_y = 0.4039
WHERE unit_type_id = 'DUPLEX_UPPER' AND room_key = 'hallway';
```

**Conflict**:
- New hallway Y-range [0.4039, 1.0] overlaps utility [0.32, 0.44]
- Need to shrink utility to Y:[0.32, 0.4039] (reduces from 1.117m to 0.781m)

**Impact**: +2 hallway wood slabs

---

#### 4. Upper Floor Bathroom (+2 slabs)
**Current**: 1.992 × 2.979 m (frac: 0.250 × 0.320)
**Target**: 1.524 × 4.160 m
**Confidence**: MEDIUM (requires utility room handling)

**Required Change**:
```sql
UPDATE ad_unit_type_room
SET frac_max_x = 0.1913, frac_max_y = 0.4469
WHERE unit_type_id = 'DUPLEX_UPPER' AND room_key = 'bathroom_2';
```

**Conflict**:
- New bathroom Y:[0.0, 0.4469] overlaps utility Y:[0.32, 0.44]
- Reference has NO utility room finish floor (likely same as hallway wood)
- **Recommendation**: Merge utility into hallway or assign wood finish

**Impact**: +2 bathroom tile slabs

---

### Phase 3: Complex (+4 slabs, major redesign)

#### 5. Ground Floor Bathroom (+2 slabs)
**Current**: 2.947 × 1.303 m (horizontal orientation)
**Target**: 1.456 × 2.171 m (vertical orientation)
**Confidence**: LOW (requires layout restructuring)

**Problem**: Bathroom needs complete reorientation:
- Width: 2.947m → 1.456m (cut in half)
- Depth: 1.303m → 2.171m (expand 67%)

**Reference Position** (Unit 1):
- X:[4.770, 6.226] → within kitchen's X-footprint
- Y:[-10.246, -8.075] → at unit rear, adjacent to kitchen
- Kitchen Y:[-12.600, -10.370], Bath Y:[-10.246, -8.075] (124mm gap = wall)

**Required Fractions**:
- X: [0.817, 1.0] (right side of unit)
- Y: [0.767, 1.0] (rear of unit, after kitchen)

**Conflict**: This requires kitchen repositioning from current Y:[0.55, 0.78] to [0.514, 0.753]

**Impact**: +2 bathroom tile slabs (but HIGH risk of layout disruption)

---

#### 6. Ground Floor Full-Unit Wood (+2 slabs)
**Current**: Foyer (2.151 × 9.308) + Living (5.815 × 5.119) = segmented
**Target**: 7.966 × 9.308 (entire unit, continuous)
**Confidence**: VERY LOW (requires architectural redesign)

**Problem**:
- Reference has ONE large wood floor covering entire unit
- Kitchen/bath tiles are OVERLAID on top (room overlap)
- Our model may not support overlapping finish floors

**Solution Approaches**:
1. Merge foyer + living into single "living" room spanning full unit (X:[0, 1.0], Y:[0, 1.0])
2. Apply wood as base finish, kitchen/bath tiles override in specific zones
3. Investigate if finish floor compose logic supports room overlap

**Impact**: +2 full-unit wood slabs (but requires major DSL/compose changes)

---

## Near-Match Analysis

**Question**: Are any slabs "close enough" but missed by checker tolerance?

**Answer**: NO. All mismatches exceed typical 50mm tolerance:
- Kitchen depth: 89mm off
- Hallway width: 97mm off, depth 336mm off
- Bedroom: 2267mm (width) and 1595mm (depth) off

**Conclusion**: Checker is working correctly. All improvements require actual dimension changes via fraction updates.

---

## Room Overlap Constraints

### Upper Floor Overlap Issue
Total depth fractions: 0.6713 (bedroom_1) + 0.6713 (bedroom_2) + 0.5961 (hallway) = **1.9387 > 1.0**

This means rooms MUST overlap to match reference, or sizes must be reduced below reference targets.

### Ground Floor Overlap Issue
Reference shows kitchen/bath tiles overlaid on full-unit wood (same footprint, different finish layers).

### Critical Question
**Does our current compose logic support overlapping room finish floors?**

If NO → cannot achieve full matches without redesigning room boundary constraints
If YES → proceed with fraction updates per recommendations above

---

## Implementation Sequence

### Recommended Approach
1. **Phase 1 Only**: Kitchen + Bedrooms (+6 slabs, safest wins)
2. **Run spatial checker** to verify no regressions
3. **Investigate overlap capability** before Phase 2/3
4. **Decide**: Continue with overlap-dependent changes OR redesign compose logic

### Expected Gains by Phase
- Phase 1: +6 slabs (46% → 50% estimated)
- Phase 2: +4 more slabs (cumulative 54% estimated)
- Phase 3: +4 more slabs (cumulative 58% estimated) — IF overlap supported

---

## Next Steps (Research Complete, No Changes Made)

1. **Decision needed**: Proceed with Phase 1 fraction updates?
2. **Code investigation**: Does `StoreyCompiler` or `BuildingWriter` support overlapping room finish floors?
3. **Test approach**: Apply Phase 1 changes to test database, run spatial checker
4. **Fallback**: If overlap not supported, redesign compose logic OR accept lower match ceiling

---

## Reference Data Sources

- Reference DB: `/home/red1/bim-compiler/reference/rosetta/Ifc2x3_Duplex_extracted.db`
- Output DB: `/home/red1/bim-compiler/output/ifc2x3_duplex.db`
- Library table: `library/component_library.db::ad_unit_type_room`
- Unit dimensions: unitWidth=7.966m, unitDepth=9.308m (hardcoded in Duplex E2E test)

---

**End of Analysis**
