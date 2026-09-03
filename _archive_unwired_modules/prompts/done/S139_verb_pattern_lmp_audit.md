# S139 — Verb Pattern Integrity + LMP Boundary Audit

**Priority: CRITICAL — first principles**
**Spec foundation:** `docs/BOMBasedCompilation.md` (BBC.md) §2.1.7–2.1.9, §2.2.1
**Companion:** `docs/BIM_COBOL.md` §Verb Taxonomy, §Data Flow, §Non-Disturbance Principle
**Invariant under audit:** `docs/DISC_VALIDATION_DB_SRS.md §6.12.1` Compilation Isolation Invariant

## PRIME RULE

Read before writing. Do NOT fix anything in this session — **explain only**.
Every claim requires a git commit hash or a line number citation.

---

## Context (from S138)

S138 found two first-principle violations in the BIM compiler pipeline:

1. **CLUSTER is being used offensively** — as a primary verb for recognisable
   placement patterns (curtain wall mullions, dining chairs, windows) rather than
   as the last-resort catch-all its spec defines it as.

2. **Possible LMP boundary breach** — the compiled output may be seeded with
   coordinates that originated in the `*_extracted.db` (input DB) rather than
   being derived purely from `_BOM.db` + `component_library.db` at compile time.
   If so, **DAGCompiler is peeking into the input DB**, violating
   §6.12.1: "DAGCompiler SHALL NOT open the extraction DB."

The second violation is the most serious. Identify it **first**.

---

## Task 1 — Locate the LMP Boundary Breach (HIGHEST PRIORITY)

### Background

BBC §2.1.8 IFCtoBOM pipeline:
> "Two inputs: IFC extraction DB + Classification YAML."
> IFCtoBOM reads extraction. DAGCompiler reads ONLY `_BOM.db` + `component_library.db`.

BBC §2.2.1:
> "Every m_bom_line is a placement instruction: place this child's LBD at offset
> (dx, dy, dz) from my LBD. The compiler walks the BOM tree…"

§6.12.1 Compilation Isolation Invariant:
> "DAGCompiler SHALL NOT open the extraction DB."

### What to investigate

The current `SH_BOM.db` contains CLUSTER verb_refs with per-instance world
coordinates (dx, dy, dz, w, d, h) embedded as strings. These coordinates
originated from `SampleHouse_extracted.db`. The question is: **at what
point in the pipeline do extraction coordinates enter the BOM, and does that
constitute a breach of the isolation invariant?**

Two possible readings:
- **IFCtoBOM breach**: IFCtoBOM reads extracted.db and writes raw world coords
  into `m_bom_line.verb_ref` (CLUSTER). This is IFCtoBOM's job — it is ALLOWED
  to read extraction. No isolation breach here.
- **DAGCompiler breach**: DAGCompiler itself (or a class it calls) opens or
  queries the extracted.db at compile time — **this IS a breach**.

### Step 1a — Search for extracted.db access inside DAGCompiler

```bash
grep -rn "extracted\.db\|_extracted\|getConnection\|DriverManager\|jdbc:sqlite" \
  DAGCompiler/src/main/java/ --include="*.java" | grep -v "test\|Test"
```

If any result points to DAGCompiler opening a DB whose path contains "extracted",
that is the breach. Record: class name, method name, line number, git commit that
introduced it.

### Step 1b — Trace CLUSTER coordinate origin through IFCtoBOM

In `IFCtoBOM/src/main/java/com/bim/ifctobom/VerbDetector.java`:
- `detectCluster()` — what input does it receive? Are these extraction centroids
  (floor-relative) or world coordinates? Cite line numbers.
- The CLUSTER string stores `dx, dy, dz` relative to what origin?
  Floor AABB min? Building origin? Confirm from `detectCluster()` implementation.

### Step 1c — Verify PlacementCollectorVisitor only reads _BOM.db

In `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/PlacementCollectorVisitor.java`:
- Does it open any DB other than the BOM.db passed to its constructor?
- Does `expandVerb()` → `CLUSTER` path read any external source?
- Cite the `Connection` objects it holds and their origins.

### Step 1d — Check ExtractionPopulator

`IFCtoBOM/src/main/java/com/bim/ifctobom/ExtractionPopulator.java` — this class
is named "extraction" and lives in the IFCtoBOM module. Confirm:
- Does it write to `_BOM.db` or only to `component_library.db`?
- Is it ever called from DAGCompiler?

### Step 1e — Git blame: when did CLUSTER enter SH/DX BOM

```bash
git log --oneline IFCtoBOM/src/main/java/com/bim/ifctobom/VerbDetector.java
git log --oneline IFCtoBOM/src/main/java/com/bim/ifctobom/CompositionBomBuilder.java
```

- `9e73d753 [R16]` — CLUSTER verb introduced (replaces SPRAY)
- `ff0f344e [S101-p131]` — Z-guard + CLUSTER identity added
- Between these commits: did the CLUSTER coordinates shift from floor-relative
  to world-relative? Check `detectCluster()` diff between R16 and S101.

---

## Task 2 — CLUSTER as First-Principle Violation

### Background

BBC §2.1.7 / BIM_COBOL.md §Verb Taxonomy:
> "Priority: TILE > ROUTE > FRAME > CLUSTER."
> "CLUSTER is the catch-all: stores exact per-instance offsets (lossless)."
> "Non-uniform groups fall through to CLUSTER or flat writes."
> "Groups < 4 elements always fall through to unfactored writes (SH/DX safe)."

CLUSTER is justified for **genuinely irregular placements** — where no geometric
pattern exists. It is NOT justified when a TILE, ROUTE, or FRAME pattern could be
detected but wasn't.

### What to investigate

**2a. SH curtain wall (20 IfcMember → CLUSTER)**

The curtain wall has 10 vertical mullions (regularly spaced along Y) and 10
horizontal members (rails + glass panels at varying Z). Query:

```sql
-- From SH_BOM.db
SELECT bom_id, verb_ref FROM m_bom_line
WHERE verb_ref LIKE 'CLUSTER%' AND bom_id LIKE '%ASM%';
```

For the vertical mullion positions (Y = 0, 1.87, 3.755, 5.625): these are
regular — 4 positions with roughly equal spacing. Could FRAME or ROUTE have
detected this? Why did VerbDetector fail? Check `diagnoseTileFailure()`,
`diagnoseFrameFailure()`, `diagnoseRouteFailure()` diagnostic output in the
pipeline log:

```bash
grep "CLUSTER fallback" logs/pipeline_SH*.log | head -20
```

**2b. DX dining chairs (6 IfcFurnishingElement → CLUSTER)**

6 chairs around a dining table. Could TILE (2×3 grid) have been detected?
Check `detectTile()` — what tolerance does it use? Are the chair positions
within TILE tolerance? Check the diagnostic log.

**2c. Custom SQL per-building pattern?**

Search for hard-coded building prefixes or product IDs inside VerbDetector,
CompositionBomBuilder, ScopeBomBuilder:

```bash
grep -rn "\"SH\"\|\"DX\"\|\"SH_\|\"DX_\|Curtain\|curtain\|Chair\|chair\|Dining\|dining" \
  IFCtoBOM/src/main/java/ --include="*.java" | grep -v "test\|Test"
```

Any match = custom SQL / hard-coded pattern = anti-pattern. Report.

**2d. Flat writes vs CLUSTER for small groups**

BBC says groups < 4 elements → flat writes (qty=1 per line, no verb). Check:

```sql
-- In SH_BOM.db: find CLUSTER lines with < 4 instances
SELECT bom_id, child_product_id, LENGTH(verb_ref) - LENGTH(REPLACE(verb_ref,';','')) + 1 AS instance_count
FROM m_bom_line WHERE verb_ref LIKE 'CLUSTER%'
ORDER BY instance_count;
```

Any CLUSTER with < 4 instances is a spec violation (should be flat writes).

---

## Task 3 — DX MIRRORED_PAIR Cascade Gap

### Background

`classify_dx.yaml` defines:
```yaml
composition:
  type: MIRRORED_PAIR
  half_unit_bom_id: DUPLEX_SINGLE_UNIT_STD
  rotation: 3.141592653589793
```

S138 GEO log confirmed: the π rotation cascades to **direct leaf children** of
`DUPLEX_SINGLE_UNIT_STD` only. Room BOMs (`DX_B102_SET`, `DX_B203_SET`, etc.)
are under `DX_L1_STR` (structural floor), not under UNIT_B — so π is never applied.

### What to investigate

**3a. Where does CompositionBomBuilder place room BOMs?**

In `IFCtoBOM/src/main/java/com/bim/ifctobom/CompositionBomBuilder.java`:
- Which method writes `DX_B102_SET` as a child of which parent BOM?
- Is there a spec decision that intentionally places B-side rooms under the
  structural floor? Or is this an implementation gap?
- Check `[DX-1]` commit (`b4285d8c`): the fix corrected B-side element
  exclusion but did it also mandate where room BOMs live?

**3b. Has DUPLEX_SINGLE_UNIT_STD ever contained room BOMs as children?**

```bash
git log --all --oneline IFCtoBOM/src/main/java/com/bim/ifctobom/CompositionBomBuilder.java
```

Check each commit's diff for any change to where `half_unit_bom_id` children
are written. Report the first commit that established the current hierarchy.

**3c. Are B-side room positions in the BOM floor-relative or world-relative?**

```sql
-- In library/DX_BOM.db
SELECT bom_id, child_product_id, dx, dy, dz FROM m_bom_line
WHERE child_product_id LIKE 'DX_B%'
ORDER BY child_product_id;
```

Cross-check against extracted DB centroid for the same rooms. If dx/dy == IFC
world centroid → BOM is storing world coords (violation). If dx/dy is
floor-relative (centroid - floor_min_xyz) → conformant.

---

## Task 4 — Note for Spec (write to BBC.md appendix or open TODO)

After investigation, append a short note to `docs/BOMBasedCompilation.md`
under a new `## Audit Findings — S139` section recording:

1. Whether CLUSTER constitutes an isolation breach or is IFCtoBOM-only
2. Whether DAGCompiler opens extraction DB anywhere (yes/no + class if yes)
3. CLUSTER violation count by building (how many lines use CLUSTER where TILE/ROUTE/FRAME should apply)
4. DX room BOM placement gap — spec decision needed

Do NOT fix code. Write findings only.

---

## Session Gate

This session ends when you can answer with evidence:

1. **Does DAGCompiler open the extraction DB?** (Yes/No + class.method + commit)
2. **Are CLUSTER coordinates world-relative or floor-relative?** (cite detectCluster() line)
3. **How many CLUSTER lines in SH/DX BOM have < 4 instances?** (query result)
4. **When did CLUSTER replace SPRAY, and did it preserve floor-relative origins?** (R16 commit diff)
5. **Where in CompositionBomBuilder are B-side room BOMs written, and to which parent?** (line number)

---

## Commit (if BBC.md appendix written)

```bash
git add docs/BOMBasedCompilation.md
git commit -m "[S139] BBC audit findings: CLUSTER boundary analysis + DAGCompiler isolation check"
```

## Sequence

```
S139 (this)  — Investigate: LMP breach + CLUSTER origin + DX room hierarchy
S140         — Decide: fix CLUSTER strategy (promote TILE/ROUTE) + room BOM parent spec
S141         — Implement: verb detector improvements + composition cascade fix
```

---

# DONE — S139 Findings (commits 2f8b9034, 417f4b02)

## Gate answers (all 5 answered)

1. **DAGCompiler opens extraction DB?** NO. Zero `*_extracted.db` references in
   `DAGCompiler/src/main/java/`. `ExtractionPopulator` (IFCtoBOM only) is never called
   from DAGCompiler. §6.12.1 isolation invariant holds.

2. **CLUSTER coords world-relative or floor-relative?** GROUP-RELATIVE LBD-to-LBD.
   `VerbDetector.detectCluster()` line 454: `gMinX = elements.mapToDouble(minX).min()`.
   Line 463: `offsets[i][0] = e.minX() - gMinX`. `floorMin` params are received but
   unused in detectCluster(). Group origin in BOM = parent-relative via
   `VerbFactorizer.java:161`: `dx = gMinX - parentMinX`. At R16 (9e73d753) centroid-based;
   at S101 (ff0f344e) changed to LBD-to-LBD.

3. **CLUSTER lines < 4 instances?** ZERO. MIN_GROUP=4 enforced at VerbDetector:43.
   SH counts: 4 (windows), 6 (chairs), 10, 10 (curtain wall). CLUSTER is justified:
   chairs have rotated end-members (W/D swap), curtain wall mixes member types.

4. **When did CLUSTER replace SPRAY?** R16 `9e73d753` (2026-03-18). R16 used
   centroid-to-centroid. S101 `ff0f344e` (2026-03-30) changed to LBD-to-LBD. Neither
   stored floor-relative coords in verb_ref — floor anchoring is at parent-BOM level.

5. **DX room BOMs written where?** NOT by CompositionBomBuilder. Written by
   ScopeBomBuilder + placed by BomHierarchyBuilder:75-86 under DX_ROOM_L1/L2 (floor
   scope), NOT under DUPLEX_SINGLE_UNIT_STD. By design — IFC already has B-side
   room positions. The π rotation IS applied to structural shell (confirmed GEO log
   line: `ROT 3.1416rad: leaf=(3.7390,9.0990) → rotated=(-3.7390,-9.0990)`).
   Finding 4 in BBC.md was CORRECTED — it is not a gap, it is intentional.

## Additional findings beyond original scope (user Q&A)

### A. DX compilation is actively failing — VerbStage Step 7

Two VERB failures (not Rosetta Stone gates — those still pass G1-G6):

**Bug A: CHECK BOM → FAIL: 2 errors** (pre-existing since S100-p116)
- Source: `BomHierarchyBuilder.java:100` writes `.componentType("LEAF")` for
  DX_ROOM_L1 and DX_ROOM_L2 as children of BUILDING_DX_STD.
- Detector: `CheckBomVerb.walk()` switch handles BUY/MAKE/PHANTOM only.
  "LEAF" hits `default` → "unknown component_type=LEAF" × 2.
- Root cause: `4aaa0356 [DISC-3]` renamed BUY→LEAF; `5857d179 [S100-p116]` added
  CHECK BOM verb; neither was reconciled with the other.
- **Fix (one line):** `BomHierarchyBuilder.java:100` change `.componentType("LEAF")`
  to `.componentType("MAKE")`. DX_ROOM_L1/L2 ARE sub-BOMs with children — MAKE is
  semantically correct. Do NOT fix CheckBomVerb — the data is wrong, not the verb.

**Bug B: CHECK PLACEMENT → FAIL: 1119 elements, 4476 pass, 148 violated**
- CheckPlacementVerb detail not surfaced at INFO level (printed at FINE).
- Likely P03/P04/P05 on UNIT_B structural elements after π rotation produces
  positions outside expected range or near-duplicate of A-side party-wall elements.
- NOT the LMP issue — G1-G6 (Rosetta Stone gates) PASS separately.

**Collateral: 1381 `storey=Unknown` RouteWalker warnings**
- DX storey names not resolving in RouteWalker's lookup.
- P04 in CheckPlacementVerb SKIPS storey=Unknown — not causing the 148 violations.
- Separate issue, does not block compilation.

### B. YAML dual role — correctly understood

**IFCtoBOM role (geometric hint):**
`classify_dx.yaml` composition block (`mirror.axis=X, mirror.position=4.4`) is a
human-crafted hint telling CompositionBomBuilder.build() where the party wall is.
Without it, IFCtoBOM cannot distinguish A-side from B-side structural elements.
Code path: `BuildingConfig.composition()` → `CompositionBomBuilder.build():65`
(`"MIRRORED_PAIR".equals(comp.type())`) → partitions by X < 4.4.

**DAGCompiler role (baked into BOM DB — NOT re-read from YAML):**
DAGCompiler does NOT read the YAML composition block. By the time DAGCompiler runs,
the intent is already encoded in `m_bom_line.rotation_rule = "3.14159..."` on the
UNIT_B row. The BOMWalker reads that value:
`PlacementCollectorVisitor.visitBomLine():212` → `ROT 3.1416rad` logged.
The DSL file (`dsl_dx.bim`) drives compilation orchestration, not the composition YAML.

**DX_BOM data verdict: structurally correct.**
466 A-side elements in DUPLEX_SINGLE_UNIT_STD (S100-p128 scope-exclude fix verified).
UNIT_A rotation_rule="0", UNIT_B rotation_rule="3.14159..." confirmed in DB.
Only defect: component_type="LEAF" on 2 room-container BOM lines (Bug A above).
No LMP failure — G1-G6 pass, GEO positions verified by BomValidator second pass.

### C. Process weakness identified

VerbStage DocStatus=VO is not surfaced in fleet PASS/FAIL summary.
PROGRESS.md gate table tracks Rosetta Stone G1-G6 (Maven tests) independently.
A building can be "PASS" in the fleet table while silently failing VerbStage.
The DISC-3 BUY→LEAF rename and S100-p116 CHECK BOM addition were never cross-checked.
**Recommendation:** Fleet gate table should include a VerbStage column (DocStatus ≠ VO).

## What S140 should fix

Priority 1 (one-liner, immediate): `BomHierarchyBuilder.java:100`
  `.componentType("LEAF")` → `.componentType("MAKE")`
  Re-run DX: CHECK BOM should go from 2 errors → 0.

Priority 2 (investigate): CHECK PLACEMENT 148 violations.
  Enable FINE logging for VerbStage to see per-element detail.
  Suspect: UNIT_B elements near party wall / negative coords after π rotation.

Priority 3 (fleet hygiene): Add VerbStage DocStatus to fleet gate table in PROGRESS.md.

Priority 4 (S140 original scope): CLUSTER→TILE/ROUTE promotion decision for
  curtain wall mullions and MEP fittings — findings show CLUSTER is currently
  JUSTIFIED for DX/SH cases. No false-CLUSTER issue found.
