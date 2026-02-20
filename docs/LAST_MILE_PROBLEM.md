# The Last Mile Problem: From Replica to Creation

**Date:** 2026-02-20  
**Context:** BIM Intent Compiler — Watchdog assessment for new session  
**Status:** Brainstorming — the hardest unsolved problem in the project

---

## 1. The Two Modes and Their Gap

The compiler operates in two modes with dramatically different quality:

### Mode 1: Rosetta Stone Replication (WORKS)

SampleHouse (55 elements) and Duplex (1085 elements) achieve 100% spatial recall against their reference IFC files. Every wall, window, door, fixture, pipe — byte-identical to the original. This works because the reference IFC IS the ground truth. The compiler extracts coordinates, dimensions, and relationships from a proven building and reproduces them through the relational metadata layer.

The path: Reference IFC → Extraction → ad_* tables → Relational Resolver → Flat Cache → IFC Output. At every step, the original building's coordinates validate the output. Shadow validation confirms 0 mismatches at 0.001mm tolerance.

### Mode 2: Generative Creation (FAILS AT LAST MILE)

CitizenHome / TB-LKTN (69 elements) is the first building with NO reference IFC. It's compiled from a 2D PDF floor plan (RUMAH RAKYAT programme) translated into DSL, with placement rules in ad_element_rule. The outer envelope works — walls form rooms at correct grid positions. But element placement WITHIN rooms fails repeatedly.

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
| `PositionRule` sealed interface | DirectCoordinate, WallFraction, RoomFraction — encodes position computation from ad_element_rule | Partial — types are sealed but don't verify correctness, only structure |
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

2. **Three-table authority rule**: ad_product_dim (geometry), ad_bom_child_param (assembly), ad_element_rule (room). Each layer adds, none overrides.

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
