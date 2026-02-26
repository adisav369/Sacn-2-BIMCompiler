# The Last Mile Problem: From Replica to Creation

**Date:** 2026-02-20 (updated 2026-02-20 session 2)
**Context:** BIM Intent Compiler — Watchdog assessment for new session
**Status:** ROOT CAUSES IDENTIFIED — ordered fix plan below. Start here.

---

## 0. SESSION 2 FINDINGS — READ THIS FIRST (2026-02-20)

### Root Cause 1: `conn_points` in `ad_product_dim` has no consumer

`ad_product_dim.conn_points` encodes which face of every product connects to its host:
```
FIXTURE_TOILET  → [{"face":"BACK","type":"WASTE"},{"face":"LEFT","type":"SUPPLY"}]
FIXTURE_SINK    → [{"face":"BACK","type":"WASTE"},{"face":"BACK","type":"SUPPLY"}]
FIXTURE_SHOWER  → [{"face":"WALL","type":"SUPPLY"},{"face":"FLOOR","type":"WASTE"}]
```
BACK face = must be placed against wall = faces INTO room. This is the rotation rule.
**No Java code reads conn_points.** RelationalResolver ignores it entirely.

### Root Cause 2: `family_ref` is NULL for all TB-LKTN furniture

C_OrderLine's `family_ref` is the link to `ad_product_dim`. For TB-LKTN all
IfcFurnishingElement rows have `family_ref = NULL`. Without it, conn_points
cannot be looked up. Orientation falls back to `NS`/`EW` wall-axis labels —
meaningless for fixture facing.

**SH/DX do not have this visual error because** their orientations are verbatim
extracted angles from the reference IFC. This is cheating — it works by accident
not by rule. See Root Cause 3.

### Root Cause 3: SH/DX are ~25% flat extracted data

| Building | ABSOLUTE (flat coords) | Relational (FRACTION/BOUNDARY) |
|---|---|---|
| SampleHouse (55) | 15 elements (27%) | 40 elements |
| Duplex (1085) | 269 elements (25%) | 816 elements |

ABSOLUTE rows store verbatim extracted centroids (e.g. `-5059.012, -67.78`).
When room boundaries shift, these items drift independently — they have no
declared relationship to each other or their room. This is the overlap/offset
regression source.

### Root Cause 4: FurnitureBOMResolver exists and works — but is bypassed

`FurnitureBOMResolver` correctly reads `dx`, `dy`, `dz`, `back_to_wall`,
`face_table`, `opposite_wall`, `rotation_rule` from `m_attribute`.
BOM assemblies are correctly defined:
- `BED_SET` — bed (back_to_wall) + side tables (dx=±0.98) + wardrobe (dx=±1.2)
- `LIVING_SET` — sofa (back_to_wall) + coffee table (dy=0.80) + TV (opposite_wall)
- `DINING_SET` — table (center) + 6 chairs (dx/dy offsets, face_table=true)

SH/DX furniture bypasses this via ABSOLUTE flat coords. Items were coincidentally
near each other in the original IFC, never declared as an assembly. Fix = SQL
migration replacing ABSOLUTE furniture rows with BOM anchor rules.

### Root Cause 5: `clear_front` in `ad_product_dim` has no consumer

```
DOOR_D1        clear_front = 0.900m  ← door swing zone
FIXTURE_TOILET clear_front = 0.533m
FURN_BED_*     clear_front = 0.600m
FURN_WARDROBE  clear_front = 0.600m
```
No placement code enforces these clearances. Furniture overlapping door swing
is entirely caused by this gap.

---

## 0.1 ORDERED FIX PLAN — Execute in this sequence

### Step 1: MetadataValidator gate — `family_ref` mandatory for fixtures/furniture ✅ DONE (Phase RM-11 + BOM-1)
**File:** `MetadataValidator.java`
**Done:** Gate is live. `checkFamilyRefMandatory()` blocks null family_ref for all
fixture/furniture classes. BOM anchor rows (`discipline='FURN'`) are excluded — their
`family_ref` holds a `bom_id`, not a product_id. 58/58 tests gate-enforced.

### Step 2: SQL migration — populate `family_ref` for TB-LKTN furniture
**File:** new `migration/migration_TBLKTN_family_ref.sql`
Map each TB-LKTN IfcFurnishingElement to its `ad_product_dim` product_id:
- IfcFurnishingElement_5 (WC) → FIXTURE_TOILET
- IfcFurnishingElement_4 (shower) → FIXTURE_SHOWER
- IfcFurnishingElement_6 (bed) → FURN_BED_DOUBLE
- IfcFurnishingElement_1..3 (other) → verify from PDF, assign correct product
Verify by running `sqlite3` query after migration — zero NULL family_ref for TB_LKTN.

### Step 3: RelationalResolver — read conn_points to derive orientation
**File:** `RelationalResolver.java`
**Change:** ~25 lines. New private method `resolveOrientation(conn, rule, ctx)`:
- Query `ad_product_dim.conn_points` WHERE `product_id = rule.familyRef`
- Parse JSON → find face with BACK/WALL type → that face goes against host wall
- Host wall = from C_OrderLine `.host_ref` (WALL_roomname_FACE)
- Return rotation = `FixturePlacer.rotationFacingInto(hostWall)`
- Call from `computeOne()` when `rule.orientation` is null or NS/EW for non-wall elements

### Step 4: SQL migration — replace ABSOLUTE furniture in SH/DX with BOM anchors ✅ DONE (Phase BOM-1, 2026-02-21)
**Files:** `migration/migration_RM6_bom_anchors.sql` + `migration/migration_RM6b_bom_product_dims.sql`
**Done:**
- Individual FURN_* leaf rows deactivated: SH=14, DX=56, TB-LKTN=10
- BOM Drop INSERT: `ad_room_slot × ad_room_boundary` → anchor rows (SH=5, DX=27, TB-LKTN=12)
- `RelationalResolver` detects BOM anchors (`family_ref ∈ m_bom.bom_id`) → `FurnitureBOMResolver`
- 18 furniture `ad_product_dim` entries added for BOM child name_patterns
- COMMON space type added for TB-LKTN open-plan room
**Result:** SH=63, DX=1197, TB-LKTN=138. All 58 tests pass.

**Remaining ABSOLUTE rows (not furniture — next phase):**
- SH: 3 IfcWindow (Revit-extracted corner windows, correct world coords)
- DX: 261 MEP+structural (IfcFlowSegment×151, IfcFlowFitting×70, IfcWindow×22, structural×18)
- These require `family_ref` normalisation: Revit strings → `ad_product_dim` catalog IDs (Phase BOM-2)

### Step 5: MetadataValidator gate 2 — block ABSOLUTE for furniture class
**File:** `MetadataValidator.java`
**Change:** ~5 lines. Add check: `position_rule = ABSOLUTE` is forbidden for
`ifc_class IN ('IfcFurnishingElement','IfcFurniture')`. Must use FRACTION or
a BOM anchor pattern. Prevents regression to flat data.

### Step 6: clear_front enforcement in FurnitureBOMResolver
**File:** `FurnitureBOMResolver.java`
**Change:** After placing each child, check: does its bbox overlap any element
within `clear_front` distance of a door opening? Read `clear_front` from
`ad_product_dim` for the door product. If overlap, reject placement and log.
This fixes furniture-in-door-swing permanently.

---

## 0.2 WHAT THIS SOLVES

| TB-LKTN Defect | Fixed by Step |
|---|---|
| Latrine/WC misrotation | Step 2 + 3 |
| Furniture askew (bed, wardrobe) | Step 2 + 3 |
| Furniture overlapping door | Step 6 |
| SH/DX furniture offset regression | Step 4 |
| Bath strange box (wrong mesh from null family_ref) | Step 2 |

**NOT solved by this plan** (separate root causes):
- Walls too thin → `RelationalResolver.loadWalls()` ignores `ad_wall_type` thickness
- Porch canopy orientation → roof writer `writeGableGeometry()` direction
- Outer drain U-shape/corners → component library missing corner pieces

---

## 1. The Two Modes and Their Gap

The compiler operates in two modes with dramatically different quality:

### Mode 1: Rosetta Stone Replication (WORKS)

SampleHouse (55 elements) and Duplex (1085 elements) achieve 100% spatial recall against their reference IFC files. Every wall, window, door, fixture, pipe — byte-identical to the original. This works because the reference IFC IS the ground truth. The compiler extracts coordinates, dimensions, and relationships from a proven building and reproduces them through the relational metadata layer.

The path: Reference IFC → Extraction → ad_* tables → Relational Resolver → Flat Cache → IFC Output. At every step, the original building's coordinates validate the output. Shadow validation confirms 0 mismatches at 0.001mm tolerance.

### Mode 2: Generative Creation (FAILS AT LAST MILE)

CitizenHome / TB-LKTN (69 elements) is the first building with NO reference IFC. It's compiled from a 2D PDF floor plan (RUMAH RAKYAT programme) translated into DSL, with placement rules as C_OrderLines. The outer envelope works — walls form rooms at correct grid positions. But element placement WITHIN rooms fails repeatedly.

**Visible defects in the attached Blender screenshot (top-down view of CitizenHome):**

- **Inner walls too thin** — wall_type thickness from metadata doesn't match visual expectation. Partition walls appear as lines, not 100mm-150mm thick partitions. Likely reading wrong wall_type or thickness not applied to geometry.

- **Furniture askew** — bed, wardrobe, desk not aligned to walls. Rotation rules either missing or resolving incorrectly. Items appear rotated to arbitrary angles rather than snapping to wall faces.

- **Main door too small** — entry door appears undersized compared to room proportions. Likely dimension from ad_product_dim doesn't match Malaysian standard (900mm × 2100mm for main entrance per JKR).

- **Windows highlighted orange but too thin** — windows placed but with near-zero depth. The depth_mm in ad_product_dim likely stores room depth instead of window frame depth (150mm typical). Same defect documented in Duplex's stairwell windows (CurrentState.txt Section 2.3).

- **Toilet bowl never right** — back-to-wall alignment fails repeatedly. The rotation_rule says FACE_AWAY_FROM_DOOR but the resolver doesn't compute which wall is opposite the door and place the toilet's back face against it. This has been reported and partially fixed multiple times but regresses.

- **Kitchen furniture can't replicate** — kitchen counter, sink, cabinet assembly that works in Duplex (via BOM) doesn't transfer. The BOM references are building-specific or the kitchen space_type doesn't activate the correct furniture slots.

- **Roof hidden but incomplete** — doesn't include porch overhang. The roof resolver uses room boundaries but porch is outside the main building envelope, so the roof stops at the wall line.

- **Outer drain badly drawn** — not U-shaped channel, no connecting corners. Drain geometry is a flat rectangle instead of a proper cross-section. No corner pieces at direction changes. Missing from component library or parametric mesh not generating the profile.

- **MEP not attempted yet** — plumbing, electrical still pending for generative buildings.

---

## 2. Why Replication Works But Creation Doesn't

The fundamental asymmetry:

```
REPLICATION (SH, DX):
  Every coordinate comes from the reference IFC.
  If the reference says toilet is at (3.2, 1.5, 0.0) with rotation PI,
  the compiler places it at (3.2, 1.5, 0.0) with rotation PI.
  The proof is: output == reference. Simple equality check.

CREATION (CitizenHome):
  Every coordinate must be COMPUTED from rules.
  Toilet goes in BATHROOM. Which wall? The one opposite the door.
  How far from corner? Clearance from ad_space_clearance (if it exists).
  What rotation? FACE_AWAY_FROM_DOOR → resolve door position → compute opposite.
  What height? Floor-mounted, so Z = floor level from sf_storey.
  
  Each step is a metadata lookup + spatial computation.
  If ANY step returns wrong data or missing data, the element is wrong.
  And there's no reference to check against — the compiler IS the authority.
```

The compiler currently has no mechanism to PROVE a computed placement is correct when there's no reference to compare against. BoundElement proves mesh-fits-bbox but not bbox-is-in-right-place. PlacementProver audits after the fact but is advisory, not blocking.

---

## 3. The Recurring Pain Pattern

This cycle repeats for every element type in every new room:

```
Step 1: User prompts Code to place toilet in bathroom
Step 2: Code writes resolver logic, toilet appears in Blender
Step 3: User sees toilet facing wrong way → prompts correction
Step 4: Code fixes rotation → toilet now correct
Step 5: User sees basin not adjacent to toilet → prompts correction
Step 6: Code fixes basin position → basin now correct
Step 7: User moves to bedroom, finds wardrobe blocking door
Step 8: Code fixes wardrobe → but now toilet has regressed (rotation=0 again)
Step 9: Repeat for 20+ element types × N rooms

Total: ~50-100 prompts to get one building's furniture right.
Each fix is ad-hoc Java code, not metadata-driven.
Next building: start over. Nothing learned permanently.
```

The problem is not that Code can't place a toilet correctly. It's that:

1. **Each placement is invented per-session** — no persistent rule
2. **Fixing one element breaks another** — no contract enforcement
3. **Nothing transfers between buildings** — rules live in Java, not metadata
4. **Human visual inspection is the only QA** — no math proof for computed placements
5. **Code optimises for "test passes" not "construction correct"** — will hardcode `rotation = PI` to make one test green instead of solving the general rule

---

## 4. The Root Cause: No PO Backbone

In iDempiere, every database record passes through PO.save(). PO.beforeSave() fires validation. ModelValidator fires business rules. There is no backdoor. You cannot persist a record without the framework validating it.

The BIM compiler has no equivalent. Elements are created by various Placer/Resolver classes, each with their own logic, their own defaults, their own shortcuts. There is no single gate that every element passes through that demands: "prove your placement is correct."

**What exists today (verified from codebase audit, 2026-02-20):**

| Layer | What It Does | Blocks Bad Output? |
|-------|-------------|-------------------|
| `MetadataValidator` (Stage 1) | Checks BOM chain, geometry hashes, positive dimensions, building_type refs, wall_face refs, room_boundary refs | Yes — blocks compilation before any element is emitted |
| `BoundElement` constructor | Checks mesh fits bbox, scale [0.3, 3.0] | Yes — but only checks MESH, not POSITION |
| `PositionRule` sealed interface | DirectCoordinate, WallFraction, RoomFraction — encodes position computation from C_OrderLine | Partial — types are sealed but don't verify correctness, only structure |
| `SlotRegistry` | Reads `ad_room_slot` — maps room_type → assembly_id → slot_face | No — loaded but not wired into placement path for generative buildings |
| `ManifestResolver` | Reads `ad_assembly_manifest` — clearance_m per face | No — loaded but used only as fallback defaults in FixturePlacer |
| `PlacementProver` | Audits 23 spatial properties post-compilation (P01–P23) | Critical provers (P01-P03, P16-P17, P22) gate; rest advisory |
| `SanityChecker` | Checks zero-volume, out-of-bounds | Partially — catches extreme errors |
| `CompilerContractTest` | Build-time assertions with coordinates | Yes — but only for KNOWN elements with WRITTEN tests |
| Human visual inspection | Catches everything above misses | Yes — but requires 50-100 prompts per building |

**Three gaps identified (2026-02-20 analysis):**

1. **Wiring gap** — `SlotRegistry` reads `ad_room_slot` but its output is not used by the generative placement path. `FixturePlacer` still uses hardcoded logic. The data is there; the resolver doesn't read it for placement decisions.

2. **Face resolution gap** — `ad_room_slot.slot_face = BACK` is semantic. No Java resolves BACK → concrete wall without knowing which wall is the room's entry (door) face. This requires door placement to precede fixture placement — the dependency is implicit in stage ordering but not enforced.

3. **Data gap** — Several slots in `ad_room_slot` have null `assembly_id` (EXHAUST, FLOOR_TRAP in BATHROOM; BASIN in TOILET_BLOCK). The slot table knows these elements must exist; the library hasn't populated what fills them.

**What's still missing: a ProvenElement gate (the PO.save() equivalent):**

Every element, at construction time, must carry a PlacementProof — a sealed type that states the mathematical equation proving this element is where it should be. `PositionRule` is the start of this (sealed, three permitted types) but it encodes computation inputs, not proof of correctness. No proof = element cannot be constructed = Java compiler rejects it. This gate does not yet exist.

---

## 5. The iDempiere AD Parallel

The deeper insight from this session: placement rules should be DATA, not CODE.

Just as iDempiere's AD_Val_Rule governs what values are valid in a field, a Construction Rule Dictionary (CRD) should govern what placements are valid in a space. The rules are knowable, codifiable, and source-citable:

- Toilet: back to wall, faces away from door, 200mm side clearance, 600mm front clearance (UBBL)
- Basin: adjacent to toilet, same wall or perpendicular, 100mm gap minimum
- Bed: headboard to wall, 600mm clearance both sides, 900mm clearance foot
- Door: swing into room (except bathroom outward per UBBL), centred in opening
- Window: centred on external wall, sill height 900mm (UBBL), frame depth 150mm
- Kitchen counter: along wall, 600mm depth, 900mm height, with clearance for door swing

These are NOT building-specific. They apply to EVERY Malaysian residential building. They should be in metadata tables (crd_rule), not in Java resolver code that Code reinvents every session.

The CRD was discussed and schema designed in this session but NOT created. See the transcript for full schema design including crd_rule, crd_rule_category, crd_rule_param, and crd_discipline_priority tables.

---

## 6. What Needs to Happen

### Immediate: Diagnostic First (Extract, Don't Imagine)

Before designing the fix, we need to see what Code actually has. A diagnostic prompt was prepared (see Section 7 below) to extract:

- BoundElement's actual fields and constructor
- Every call site that creates BoundElements
- PlacementProver's actual proof methods
- The emit path from resolvers to output
- The pipeline stage order

**Run this diagnostic FIRST in the new session.** The fix must graft onto the existing codebase, not replace it.

### Then: The PO Backbone Design

Based on what the diagnostic reveals, design a ProvenElement record that:

1. **Wraps BoundElement** — adds mandatory PlacementProof field
2. **PlacementProof is sealed** — finite set of proof types (WallAligned, FractionAlongWall, RoomCentroid, GridIntersection, BOMChildOffset, ExtractedReference, etc.)
3. **Each proof type verifies itself** — proof.verify(bounds) fires at construction time
4. **No element exists without proof** — like PO.save(), there is no shortcut
5. **Proofs cite metadata** — every proof references which ad_* row it came from
6. **Proofs compose** — BOM children carry offset proofs relative to parent

### Then: CRD Population (Separate Workstream)

Populate crd_rule tables with placement rules from UBBL, construction practice, and extracted patterns from Duplex/SampleHouse. This is domain knowledge capture, independent from compiler code. Can be done by construction professionals reading standards.

### Then: CRDValidator in Pipeline

Wire CRDValidator as a CompilerStage that reads crd_rule and validates every ProvenElement against applicable rules. Like ModelValidator in iDempiere — fires for every element, checks jurisdiction-specific rules, blocks non-compliant output.

---

## 7. Diagnostic Prompt for Code

Give this to Code at the start of the new session:

```
Read docs/TEST_ARCHITECTURE.md and docs/CODE_WATCHDOG.md first.
Then run this diagnostic and paste FULL output — do not summarise:

echo "=== PROVEN ELEMENT DIAGNOSTIC ==="
echo ""
echo "--- 1. BoundElement: full class source ---"
find . -name "BoundElement.java" -type f | head -3
find . -name "BoundElement.java" -type f -exec cat {} \;
echo ""
echo "--- 2. Every 'new BoundElement' call site ---"
grep -rn "new BoundElement\|BoundElement\.builder\|BoundElement\.of\|BoundElement\.create" --include="*.java" .
echo ""
echo "--- 3. PlacementProver: full class source ---"
find . -name "PlacementProver.java" -type f | head -3
find . -name "PlacementProver.java" -type f -exec cat {} \;
echo ""
echo "--- 4. Where PlacementProver is called ---"
grep -rn "PlacementProver\|placementProver\|PlacementProof" --include="*.java" .
echo ""
echo "--- 5. MeshBinder: full class source ---"
find . -name "MeshBinder.java" -type f | head -3
find . -name "MeshBinder.java" -type f -exec cat {} \;
echo ""
echo "--- 6. Element emit path ---"
grep -rn "addElement\|emitElement\|writeElement\|elements\.add\|boundElements\|placedElements" --include="*.java" . | grep -v "test\|Test" | head -40
echo ""
echo "--- 7. CompilationPipeline stage list ---"
find . -name "CompilationPipeline.java" -type f -exec cat {} \;
echo ""
echo "--- 8. Any sealed interfaces or proof types ---"
grep -rn "sealed interface\|sealed class\|PlacementProof\|ProvenElement\|WitnessClaim" --include="*.java" .
echo ""
echo "--- 9. SanityChecker checks ---"
find . -name "SanityChecker.java" -type f -exec cat {} \;
echo ""
echo "--- 10. All Placer/Resolver/Writer classes ---"
grep -rn "class.*Placer\|class.*Resolver\|class.*Writer" --include="*.java" . | grep -v "test\|Test"
echo ""
echo "--- 11. BoundElement record/class signature ---"
grep -rn "record BoundElement\|class BoundElement" --include="*.java" .
echo ""
echo "--- 12. How rotation/position enter BoundElement ---"
grep -B5 -A5 "new BoundElement" --include="*.java" -r . | head -100
echo ""
echo "=== END DIAGNOSTIC ==="
```

Paste the full output back to the watchdog session. Do not fix anything yet.

---

## 8. The Compound Enrichment Vision

Once the PO backbone exists and CRD is populated:

```
Building 1 (CitizenHome):  HARD — building the proof framework + populating rules
Building 2 (PR1MA terrace): EASIER — reuses 80% of rules, only layout changes
Building 3 (Johor shophouse): EASIER — adds SHOP_GROUND room type, reuses everything else
Building 4+ : CONFIGURATION — write DSL, SQL INSERT to registry, compile
```

The proof framework makes each building provably correct WITHOUT visual inspection. The CRD rules make each placement follow construction standards WITHOUT Code inventing positions. The metadata-driven architecture makes each new building a data exercise WITHOUT new Java code.

This is the iDempiere moment: when the AD layer is complete, new business processes are configuration, not development. When the CRD is complete, new buildings are configuration, not debugging.

---

## 9. Key Project Documents

These should be in the project knowledge and/or docs/ folder:

| Document | Purpose | Status |
|----------|---------|--------|
| METADATA_DRIVEN_ARCHITECTURE.md | Five-domain architecture, iDempiere mapping, migration phases | In project knowledge |
| TEST_ARCHITECTURE.md | Test structure, pom.xml, drift diagnostic, math-first principle | Created this session — ADD to project |
| ai-watchdog-development-process.md | Seven drift patterns, enforcement stack, watchdog creed | In project knowledge |
| REFACTOR_SEALED_TYPES.md | Sealed Placement types, RoomContext, builder pattern | In project knowledge |
| REFACTOR_METADATA_INTEGRITY.md | FK constraints, MetadataValidator, provenance | In project knowledge |
| math-first-visual-resolution.md | Why geometry is maths, visual QA taxonomy | In project knowledge |
| witness-system-specification.md | Witness claims, proof architecture | In project knowledge |
| CurrentState.txt | Stubborn recurring issues, expert review brief | In project knowledge |

---

## 10. Session Notes for Watchdog

This session (2026-02-20) covered:

1. **Methodology insight**: translating untrained BIM domain into iDempiere patterns Code recognises from training data — the "2nd Rosetta Stone" (ERP patterns → BIM patterns)

2. **Three-table authority rule**: ad_product_dim (geometry), m_attribute (assembly), C_OrderLine (room). Each layer adds, none overrides.

3. **rotation_rule migration**: 16 params normalised, zero old rotation/facing columns, FixturePlacer hardened

4. **Pipeline refactor**: CompilerStage interface, 7-stage typed loop, checkRelationalData hardcode killed

5. **Five-domain architecture**: sd_* (Space), cd_* (Component), rd_* (Regulatory), sf_* (Structure), bt_* (Building Transaction), sys_* (System) — with Java evolution patterns (DomainStore, typed records, CompilerValidator, DocAction lifecycle)

6. **CRD concept**: Construction Rule Dictionary as separate subproject, AD_Val_Rule equivalent for BIM

7. **ProvenElement concept**: PO-backbone that forces math proof at element construction time — DESIGNED but NOT IMPLEMENTED

8. **Key pending decision**: the PO backbone design depends on seeing what BoundElement actually looks like in the codebase → diagnostic prompt prepared

**Drift diagnostic baseline:** OrThrow=8, OrDefault=133, BoundElement null-checks 3/11, 49 legacy test files, 59/61 tables lack provenance.

**Tests:** 44/44 green. SH=55, DX=1085, TB-LKTN=69, Terminal=51088.

---

*"The viewer is a confirmation tool, not a discovery tool. You open it to see what you've already proven, not to find what might be wrong."*

*We're not there yet for generative buildings. This document describes how to get there.*
