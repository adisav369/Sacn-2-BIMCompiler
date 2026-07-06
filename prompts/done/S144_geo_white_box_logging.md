# DONE
# S144 — GEO White-Box Logging: Input → Process → Output Proof Chain

**Spec:** `docs/BOMBasedCompilation.md` §6.1, `docs/DISC_VALIDATION_DB_SRS.md` §6.12.1
**Prior work:** S143 (forensic summary, containment check gaps found)
**Prereq:** S143 findings committed

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Query the database. Copy patterns you find. Compute positions via verbs. Never invent.

## Problem

The GEO logging in PlacementCollectorVisitor is custom scattered logging, not a
structured proof chain. It logs individual events (ENTER, LEAF, CONTAIN, ROT) but
does not prove the compilation is spatially valid. Specific gaps found in S143:

### Gap 1: Round-Trip Identity (the "lossless codec" problem)

The extraction pipeline (IFCtoBOM) computes BOM line offsets as:
```
dx = element.minX() - parentMinX    // VerbFactorizer.java:194
dy = element.minY() - parentMinY    // VerbFactorizer.java:195
dz = element.minZ() - parentMinZ    // VerbFactorizer.java:196
```

The compilation pipeline (DAGCompiler) recomputes world position as:
```
child_abs = parent_abs + rotated(dx, dy, dz) + bomOrigin
```

This is mathematically `(X - P) + P = X`. The C8/C9 gates verify this identity
passes — but that only proves the codec is lossless, not that the compiler did
spatial reasoning. The BOM structure is an intermediate encoding of the same
world positions extracted from the IFC.

**What's needed:** GEO must log the INPUT (extraction source), the PROCESS
(BOM tack chain computation), and the OUTPUT (placed position) as three
separate values, so the proof chain is visible:

```
[GEO] PROOF element_guid INPUT=(15.355,1.355,0.470) from elements_meta
[GEO] PROOF element_guid PROCESS parent=(0,0,0) + offset=(15.355,1.355,0.470) + rot=0
[GEO] PROOF element_guid OUTPUT=(15.355,1.355,0.470) centroid=(15.500,4.255,2.149)
[GEO] PROOF element_guid MATCH input==output → ROUND_TRIP (no spatial reasoning)
```

When these always match, the log proves the compiler is a codec. When workshop
trim is added (S145), the OUTPUT will differ from INPUT — that's the proof that
spatial reasoning happened.

### Gap 2: CONTAIN Check Is LMP-Only

The current containment check (`logContainmentCheck`) verifies:
```
child LBD >= parent anchor   (offset non-negative)
```

This only proves LMP (child doesn't precede parent origin). It does NOT prove:
- Child fits within parent AABB (envelope containment)
- Child doesn't overshoot roof, ground, or building perimeter
- Child doesn't clash with siblings

**Evidence (SH):** WALL_EXT_NS_290x3358 has top at Z=3.828m. Roof base at Z=3.0m.
CONTAIN check says OK because LBD Z=0.470 >= parent anchor Z=0.0. The 828mm
roof overshoot is invisible.

**Evidence (DX):** Mirror B-side containment used inverse rotation to show local
offsets are positive. This proves mirror symmetry but not envelope containment.
The maths in the inverse rotation check may also be wrong (user flagged this).

### Gap 3: No Structured Log Format

Current GEO logging is ad-hoc `BIMLogger.geo("TACK", ...)` calls scattered
through PlacementCollectorVisitor. Each call formats its own string. There is
no structured record that downstream tools can parse.

**What's needed:** A GEO proof record that captures the full data trail per
element, emitted once per LEAF, parseable by test code:

```
GEO_PROOF | guid | input_xyz | bom_chain | process_xyz | output_xyz |
           parent_aabb | envelope_check | verdict
```

## Read First

1. `CLAUDE.md` + `PROGRESS.md` §Current State
2. `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/PlacementCollectorVisitor.java`
   — lines 308-500 (onLeaf), 768-795 (logContainmentCheck)
3. `IFCtoBOM/src/main/java/com/bim/ifctobom/VerbFactorizer.java`
   — lines 148-210 (factorize: where dx/dy/dz are computed from extraction)
4. `IFCtoBOM/src/main/java/com/bim/ifctobom/ExtractionPopulator.java`
   — lines 82-142 (populate: where elements_meta is read)
5. `DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java`
   — lines 135-265 (emitForensicSummary)
6. `docs/BOMBasedCompilation.md` §6.1 (Workshop Verbs)
7. `docs/DISC_VALIDATION_DB_SRS.md` §6.12.1 (Compilation Isolation Invariant)

## Design Principle: Decoupled Proof, Not Custom Logging

GEO is NOT a logging framework. It is a **proof emitter**. The compilation
produces structured proof records. A separate formatter renders them to log.

```
Compilation (PlacementCollectorVisitor)
    │
    ├── produces: List<GeoProofRecord>
    │
    └── each record: {guid, inputXYZ, bomChain, parentAnchor, rotation,
                       processXYZ, outputXYZ, parentAABB, envelopeVerdict}

GeoProofFormatter (separate class, no compilation logic)
    │
    ├── consumes: List<GeoProofRecord>
    │
    ├── emits: structured log lines (parseable by tests)
    │
    └── emits: summary (CONTAIN counts, OVERSHOOT counts, envelope violations)
```

This follows the pattern we used before — results pass to a log formatter.
The formatter has no access to the extraction DB or BOM DB. It can only
report what the compilation produced.

## Tasks

### Task 1: Define GeoProofRecord

A structured record capturing the full proof chain per element:

```java
record GeoProofRecord(
    String guid,              // IFC GUID (from m_bom_line_ma)
    String productId,         // child_product_id
    String bomChain,          // "BUILDING→FLOOR→SET→LEAF" path
    double[] inputLBD,        // from extraction: element minX/minY/minZ (NULL if not available)
    double[] parentAnchor,    // accumulated anchor from walker stack
    double cumRotation,       // accumulated rotation (radians)
    double[] lineOffset,      // raw dx/dy/dz from BOM line (before rotation)
    double[] rotatedOffset,   // dx/dy/dz after rotation applied
    double[] outputCentroid,  // final placed centroid (world absolute)
    double[] outputLBD,       // final placed LBD (world absolute)
    double[] halfExtents,     // halfW, halfD, halfH
    double[] parentAABB,      // parent's width/depth/height (for envelope check)
    boolean roundTrip,        // inputLBD == outputLBD (codec identity)
    boolean lmpContained,     // outputLBD >= parentAnchor (current check)
    boolean envelopeContained // outputMAX <= parentMAX (new check)
) {}
```

**inputLBD source:** The extraction DB positions are NOT available at compile
time (§6.12.1 Compilation Isolation Invariant). The proof stage (post-walk)
reads the extraction DB for comparison. So inputLBD is populated by the proof
stage, not the walker. The walker emits records with inputLBD=null; the proof
stage enriches them.

### Task 2: Collect Records in PlacementCollectorVisitor

Replace scattered `BIMLogger.geo("TACK", ...)` calls in `onLeaf()` with
structured record creation. The walker builds `List<GeoProofRecord>` accessible
via `getProofRecords()`. Keep the existing TACK log lines as-is for backward
compatibility (they can be removed later once tests use proof records).

Key data points to capture per LEAF:
- `lineOffset` = raw dx/dy/dz from `line.getDx()/getDy()/getDz()`
- `rotatedOffset` = after cos/sin rotation applied
- `parentAnchor` = `anchorStack.peek()`
- `cumRotation` = `rotationStack.peek()`
- `outputCentroid` = computed cx/cy/cz
- `outputLBD` = cx-halfW, cy-halfD, cz-halfH
- `parentAABB` = from parent BOM's allocated_width/depth/height (need to track on stack)
- `bomChain` = storeyStack + current product path

### Task 3: GeoProofFormatter

Separate class that takes `List<GeoProofRecord>` and emits:

1. Per-element proof lines (INFO level):
```
[GEO] PROOF WALL_EXT_NS guid=3cUk... chain=BUILDING→SH_GF_STR→LEAF
  offset=(15.355,1.355,0.470) rot=0.00 → output=(15.355,1.355,0.470)
  LMP=OK envelope=OVERSHOOT(Z+828mm vs parent H=3000mm)
```

2. Summary block (INFO level):
```
[GEO] SUMMARY: 58 elements, 58 LMP_OK, 0 LMP_FAIL, 54 ENVELOPE_OK, 4 ENVELOPE_OVERSHOOT
  ROUND_TRIP: 58/58 (codec — no spatial reasoning applied)
  ENVELOPE_OVERSHOOT: WALL_EXT_NS(+828mm Z), WALL_EXT_EW(+291mm Z), ...
```

### Task 4: Envelope Containment Check

Add real AABB containment: child MAX <= parent MAX.

For this, the walker needs to track the parent's AABB on the stack (not just
the anchor point). When entering a sub-assembly, push its AABB dimensions.
When checking a LEAF, verify:

```
outputLBD >= parentLBD              (current LMP check)
outputLBD + 2*half <= parentLBD + parentAABB   (new envelope check)
```

For Z specifically (roof overshoot):
```
childTopZ = outputLBD.z + height
parentTopZ = parentAnchor.z + parentHeight
overshoot = childTopZ - parentTopZ    (positive = overshoots)
```

### Task 5: Wire Into Pipeline

- `PlacementCollectorVisitor.getProofRecords()` → `CompilationContext`
- `CompilationPipeline.emitForensicSummary()` calls `GeoProofFormatter`
- Proof stage enriches records with `inputLBD` from extraction DB (read-only)
- `ROUND_TRIP` count logged — when this equals element count, compiler is codec
- `ENVELOPE_OVERSHOOT` logged with specific elements and overshoot amounts

### Task 6: DX Mirror Proof (Mathematical)

The current inverse rotation in `logContainmentCheck` needs review (user flagged
maths as wrong). With GeoProofRecord, the proof is explicit:

```
B-side element:
  lineOffset = (3.3745, 9.0990, 3.8785)    // same as A-side (from BOM line)
  cumRotation = 3.14159                      // π from UNIT_B
  rotatedOffset = (-3.3745, -9.0990, 3.8785) // cos(π)=-1, sin(π)=0
  parentAnchor = (9.2145, 4.3827, 0.3000)   // B-side anchor
  outputLBD = (5.8400, -4.7163, 4.1785)     // anchor + rotatedOffset - half
```

The proof: `rotatedOffset` is the mathematical negation of `lineOffset` in X/Y
(as expected for π rotation). The `outputLBD` is the correct world position.
Envelope check against the DUPLEX AABB tells whether it fits.

## Gate

- GeoProofRecord emitted for every LEAF element
- GeoProofFormatter produces parseable structured output
- ROUND_TRIP count = element count (proves codec identity for now)
- ENVELOPE_OVERSHOOT lists specific elements with mm amounts
- SH: 4 elements flagged as ENVELOPE_OVERSHOOT (roof overshoot)
- DX: mirror elements show correct rotatedOffset (negation of lineOffset)
- No compilation code reads extraction DB (§6.12.1 maintained)
- Existing TACK log lines preserved (backward compat)

## Key Files

- `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/PlacementCollectorVisitor.java`
- `DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java`
- `DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationContext.java`
- `IFCtoBOM/src/main/java/com/bim/ifctobom/VerbFactorizer.java` (reference only — where dx/dy/dz originate)
- `IFCtoBOM/src/main/java/com/bim/ifctobom/ExtractionPopulator.java` (reference only — where elements_meta is read)

## When Done

Prepend `# DONE` to this file. Update PROGRESS.md §S144.

## What NOT to Do

- Do NOT read extraction DB from the walker (§6.12.1 isolation invariant)
- Do NOT remove existing TACK log lines (backward compat)
- Do NOT add envelope trim logic — that's S145 InterimWorkshop
- Do NOT invent spatial reasoning — this session is proof infrastructure only
