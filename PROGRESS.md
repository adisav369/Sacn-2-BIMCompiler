# PROGRESS — Current Development State

**Last updated:** 2026-02-02
**Current version:** 0.50.3
**Current phase:** 50D ready

## Phase 50: School Building Typology — COMPLETE

### Session 2026-02-02: Witness Counter + Lintel Z Fixes

**Bug Fixes Applied:**

1. **Witness Summary Counter Bug**
   - `WitnessBuilder.java`: Summary was hardcoding `unprovable: 0` instead of counting
   - Fix: Replaced incremental counters with robust post-calculation from actual claim statuses
   - Now: `for (claim : claims) { count by status }` guarantees accurate summary

2. **Window Lintel Z Position Bug**
   - `BuildingCompiler.java` line 2680-2683: Window openings passed only `window.height()` for lintel placement
   - Should be: `window.sillHeight() + window.height()` (head height, not pane height)
   - Effect: Window lintels were at Z=1.2m (window height) instead of Z=2.1m (sill + window)
   - This caused 19 false switch/lintel clashes (switches at Z=1.2m overlapping lintels at Z=1.2m)
   - Fix: `double headHeight = window.sillHeight() + window.height();`

**Results After Fixes:**

| Building | Proven | Skipped | Unprovable | Total |
|----------|--------|---------|------------|-------|
| School   | 18     | 6       | 0          | 24    |
| TB-LKTN  | 17     | 7       | 0          | 24    |
| TBLKTN2S | 18     | 6       | 0          | 24    |

School: MEP_NO_STRUCTURAL_CLASH now PROVEN (0 clashes, was 19 false positives)

**Witness Coverage Gap Identified:**
- TB-LKTN had silently wrong lintel geometry (Z=1.2m instead of Z=2.1m) that passed all 17 claims
- `MEP_NO_STRUCTURAL_CLASH` only caught it in school because switches were nearby
- **Roadmap item:** `LINTEL_AT_HEAD_HEIGHT` claim — verify every lintel's minZ equals opening head height ± tolerance
- Not a 50D blocker; logged for witness hardening phase

### Session 2026-02-02: Phase 50C Complete (Previous)

**Phase 50C: School-Specific Witnesses** ✓

Added 5 new witness claims to WitnessBuilder:

1. **CLASSROOM_DAYLIGHT** (Claim 20)
   - Verifies all classrooms have windows for natural daylight
   - Tracks window count, window area, floor area, daylight ratio
   - School result: PROVEN (6 classrooms, 18 windows, 11.6% avg daylight ratio)

2. **TOILET_ACCESSIBLE** (Claim 21)
   - Verifies toilet doors meet MS 1184 accessibility (≥900mm)
   - School result: PROVEN (D2 doors updated to 900mm)

3. **CORRIDOR_CONNECTS_ALL** (Claim 22)
   - Verifies corridor provides access to all teaching spaces
   - School result: PROVEN (10 rooms connected via koridor)

4. **FIRE_TRAVEL_DISTANCE** (Claim 23)
   - Verifies max travel distance to NEAREST exit
   - Single-storey: 45m (IBC 1017.2 / UBBL Part VII relaxation)
   - Multi-storey: 30m dead-end (UBBL 2012 Clause 166)
   - School result: PROVEN (max 21m, well under 45m limit)

5. **STRUCTURAL_GRID_COMPLETE** (Claim 24)
   - Verifies grid beams/columns exist with correct IFC classes
   - Does NOT check span limits (documented as future hardening)
   - School result: PROVEN (2 columns, 3 beams, all IfcColumn/IfcBeam)

**Code Changes:**
- `WitnessBuilder.java`: Added data collection methods and claim builders for 5 claims
- `BuildingWriter.java`: Added `collectSchoolWitnessData()` to populate witness data
- `StructuralPlacer.java`: Added TODO comment documenting single-axis grid limitation

**Fixes Applied:**
- `Sekolah-Kebangsaan.bim`: Changed D2 toilet door from 750mm to 900mm (MS 1184 compliance)
- `BuildingWriter.java`: Fire travel finds NEAREST exit (was using wrong exit)
- `BuildingWriter.java`: Uses exterior wall info from BuildingDefinition for exit detection
- `WitnessBuilder.java`: Added `setFireTravelStandard()` for caller-specified code reference

**School Witness Summary:**
- Total claims: 24
- Proven: 18 (all 5 school claims PROVEN + MEP_NO_STRUCTURAL_CLASH fixed)
- Skipped: 6 (single-storey/single-unit claims + 3 plumbing system claims)
- Unprovable: 0

### Phase 50B.1: IFC Class Correctness + Grid Beams (Previous Session)

**Construction System Awareness:**
- Added `ConstructionSystem` enum (FRAMED, MASONRY)
- Modified BuildingWriter to branch on construction system
- Added column/beam writing with correct IFC classes (IfcColumn, IfcBeam)

**Grid Beam Placement:**
- Added `structural_grid: true` and `beam_max_span: 8.0` to ASSEMBLY_HALL
- StructuralPlacer places grid beams/columns for large-span rooms

**Known Limitation (Documented):**
- Single-axis grid: perpendicular spans not yet subdivided
- TODO in StructuralPlacer for two-way grid implementation

### Regression Tests

- TB-LKTN: PASS (FRAMED construction, 17/24 proven, 7 skipped)
- TBLKTN2S: PASS (multi-storey, 18/24 proven, 6 skipped)
- School: PASS (MASONRY construction, 18/24 proven, 6 skipped)

## Test Commands
```bash
# TB-LKTN test
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNEndToEndTest" -q

# School compilation
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.BuildingCompiler" \
  -Dexec.args="examples/Sekolah-Kebangsaan.bim output/sekolah_kebangsaan.db" -q

# Check school DB IFC classes
sqlite3 output/sekolah_kebangsaan.db \
  "SELECT ifc_class, COUNT(*) FROM elements_meta GROUP BY ifc_class ORDER BY 2 DESC"

# Check witness claims
python3 -c "import json; d=json.load(open('output/sekolah_kebangsaan_witness.json')); print(d['summary'])"
```

## Documentation Session 2026-02-02

**Created FOSS Developer's Guide** (`docs/FOSS_DEVELOPER_GUIDE.md`)

Comprehensive documentation for open source contributors covering:
- DAG compiler engine architecture (5-stage pipeline)
- Data flow from DSL input to DB/IFC output
- Artifact checklist for adding new building DSL files
- Configuration files (spacetypes.yaml)
- Witness system (24 claims)
- Key classes reference table
- DSL syntax reference appendix
- Example buildings appendix

**Watchdog Audit Applied** (8 findings addressed):

1. Added provenance tags to all constants (Finding 1)
2. Replaced "auto-generate" with "place per rules" (Finding 2)
3. Cited Malaysian standards (MS IEC 60364) alongside IBC (Finding 3)
4. Added Step 0: Provenance Declaration to developer workflow (Finding 4)
5. Added provenance requirements to spacetypes.yaml examples (Finding 5)
6. Added threshold_source requirement to witness template (Finding 6)
7. Marked tutorial examples as "EXAMPLE ONLY" (Finding 7)
8. Clarified Configuration+Code boundary in artifacts table (Finding 8)

**New appendices added:**
- Appendix C: Addon Framework reference
- Appendix D: Constant Reconciliation Notes (FOSS Guide vs Glossary)

**Watchdog Provenance Audit — Java Constants Tagged**

Applied Watchdog domain rulings to source code constants:

| File | Constant | Change | Source |
|------|----------|--------|--------|
| `StructuralPlacer.java` | `MIN_OPENING_FOR_LINTEL` | 0.9m → **0.6m** | JKR structural practice (half-brick can't arch) |
| `StructuralPlacer.java` | `LINTEL_DEPTH` | Tagged | BS 8110/EC2 (valid ≤1.2m span) |
| `StructuralPlacer.java` | `LINTEL_BEARING` | Tagged | BS 5628 clause 23.1.7 / EC6 |
| `StructuralPlacer.java` | `COLUMN_SIZE` | Tagged ○ ASSUMED | TODO: move to profile config |
| `BuildingCompiler.java` | `SEPARATING_SLAB_THICKNESS` | Tagged | UBBL fire + BS 8233 acoustic |
| `FixturePlacer.java` | `TOILET_SIDE_CLEARANCE` | Tagged | IPC 405.3.1 (non-accessible) |
| `FixturePlacer.java` | `TOILET_FRONT_CLEARANCE` | Tagged | IPC 405.3.1 (non-accessible) |
| `BIMConstants.java` | `STANDARD_WALL_THICKNESS` | Tagged | ✓ EXTRACTED: TERMINAL (profile-dependent) |
| `BIMConstants.java` | `STANDARD_SLAB_THICKNESS` | Tagged | ◆ RESEARCHED: Malaysian residential |
| `BIMConstants.java` | `STANDARD_SLAB_OVERLAP` | Tagged | ✓ EXTRACTED: TERMINAL G8 (profile-dependent) |
| `BIMConstants.java` | Door/Window dimensions | Tagged | ◆ RESEARCHED + ○ ASSUMED markers |

**Critical fix**: Lintel threshold changed from UK 900mm to Malaysian 600mm.

**Systemic finding**: Three constants are profile-dependent (wall thickness, slab overlap, column size) — should move to YAML config per addon framework proposal.

**Watchdog Final Rulings Applied:**
- `SLAB_OVERLAP` tag finalized: ✓ EXTRACTED (TERMINAL 175-250mm) + ○ ASSUMED mid-range + profile override note

**Output-Affecting Change (Watchdog note):**
The lintel threshold fix (900→600mm) is the only change affecting compiled output. Masonry openings 600–899mm previously missing lintels will now receive them. TB-LKTN has D3 bathroom doors at 700mm — next recompilation will produce additional structural elements. This is **correct behaviour**, not a regression. Expect witness/structural counts to change on next test run.

**Determinism Fixes Applied (Watchdog ruling 2026-02-02):**

HashMap iteration non-determinism bug found and fixed:

| File | Issue | Fix |
|------|-------|-----|
| `ElectricalPlacer.java:602` | HashMap iteration for circuit types affected `circuitCounter` | Sort keys before iteration |
| `SpaceSolver.java:181` | Choco default search strategy is version-dependent | Pin `Search.inputOrderLBSearch()` |
| `pom.xml` | Choco version bump could break determinism | Add warning comment |

**Verification (Watchdog follow-up):**
- IntVar creation: iterates over `List<RoomConstraint>` (line 119) — deterministic ✓
- PlumbingPlacer HashMap: `generatePlumbing()` NOT used in production — safe ✓
- groupingBy/Collectors.toMap: none found in production path ✓

**Determinism level documented:**
- Functional determinism: YES (geometry, topology, IDs)
- Byte-level: DB yes, witness JSON no (timestamp varies)

**Future obligation (Watchdog note):**
`PlumbingPlacer.generatePlumbing(Map<String, StackInfo>)` has HashMap iteration but is not currently called in production. When plumbing system graph generation enters the production path (required for PLUMBING_* witness claims), that HashMap will need sorted-keys treatment.

**Prioritized TODOs** (per Watchdog assessment):

1. **TODO 1: Move profile-dependent constants to YAML config** (HIGH PRIORITY)
   - Constants: `STANDARD_WALL_THICKNESS`, `STANDARD_SLAB_OVERLAP`, `COLUMN_SIZE`
   - Rationale: Unblocks addon framework — without YAML migration, addons have nowhere to write profile overrides
   - Reference: `docs/foss-guide-audit-addon-framework.md`, Part 2, "Profile Overrides" section

2. **TODO 2: Accessible vs non-accessible fixture placement** (MEDIUM PRIORITY)
   - Clearances: TOILET_SIDE (457mm ADA vs 381mm IPC), TOILET_FRONT (1219mm vs 533mm)
   - Rationale: Unblocks TOILET_BLOCK addon `READY` status (currently would be `READY_WITH_GAPS`)
   - Note: Malaysian UBBL requires ≥1 accessible WC per public building — blocks school compliance

---

## What's Next

Phase 50 is complete. School building typology now has:
- Masonry construction awareness
- Grid beams for assembly halls
- 5 school-specific witness claims
- Identified design issues (toilet accessibility, fire travel distance)

Potential next phases:
- Phase 51: Two-way structural grid (proper span subdivision)
- Phase 52: Stair placement for multi-storey schools
- Phase 53: Second storey classrooms
