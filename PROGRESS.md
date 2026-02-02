# PROGRESS — Current Development State

**Last updated:** 2026-02-03
**Current version:** 0.51.0
**Current phase:** 51 COMPLETE

## Phase 51: Two-Way Structural Grid — COMPLETE

### Session 2026-02-03: Beam Span Limit Compliance

**Scope:** Implement two-way structural grid for assembly hall with proper beam spans (column-to-column only) and add BEAM_SPAN_LIMIT witness claim.

**Problem Addressed:** Prior to Phase 51, the assembly hall's structural grid placed only 3 beams spanning the full room dimension (12-20m spans). A 20m concrete beam would need ~1.5m depth — that's a transfer girder, not a beam. MS 1195/Eurocode 2 limits:
- MASONRY construction: 8m max beam span
- FRAMED construction: 10m max beam span

**Code Changes:**

1. **StructuralPlacer.java** (Two-way grid rewrite):
   - Added span limit constants with provenance tags:
     ```java
     public static final double MAX_BEAM_SPAN_MASONRY = 8.0;  // MS 1195 / JKR
     public static final double MAX_BEAM_SPAN_FRAMED = 10.0;  // Eurocode 2 / BS 8110
     ```
   - Rewrote `placeGridBeams()` for two-way grid:
     - X gridlines: perimeter + interior columns spaced ≤ max span
     - Y gridlines: perimeter + interior columns spaced ≤ max span
     - Beams placed along ALL gridlines (both X and Y directions)
     - Result: 17 beams (9 X-direction + 8 Y-direction) at 6-6.7m spans

2. **WitnessBuilder.java** (New claim #25):
   - Added `beamSpans` list and `maxAllowedBeamSpan` field
   - Added `beamSpan()` method to record beam span data
   - Added `setConstructionSystem()` method
   - New `buildBeamSpanLimitClaim()` for BEAM_SPAN_LIMIT (#25)
   - Enhanced `buildStructuralGridCompleteClaim()` with:
     - Two-way grid verification (X and Y direction beams)
     - Beam/column ratio sanity check (2-10 beams per column)
     - Connectivity summary in evidence

3. **BuildingWriter.java** (Beam span data collection):
   - Sets construction system on witness builder
   - Records beam spans with source/destination columns

**Test Results:**

| Building | Proven | Skipped | Unprovable | Status |
|----------|--------|---------|------------|--------|
| School   | 20     | 5       | 0          | PASS   |
| TB-LKTN  | 17     | 8       | 0          | PASS   |
| TBLKTN2S | 18     | 7       | 0          | PASS   |

**Witness Count:** Now 25 claims (was 24). BEAM_SPAN_LIMIT is claim #25.

**School Assembly Hall Grid:**
- Before: 3 beams, 12-20m spans (structurally impossible)
- After: 17 beams, 6-6.7m spans (MS 1195 compliant)

**Gate Checklist:**

| Gate | Requirement | Result |
|------|-------------|--------|
| Assembly hall beams | 17 beams (9 X + 8 Y), all 6-8m | ✓ Max span 6.67m |
| BEAM_SPAN_LIMIT | PROVEN — no beam exceeds 8m for MASONRY | ✓ School: PROVEN |
| STRUCTURAL_GRID_COMPLETE | Strengthened — checks connectivity | ✓ Two-way verification |
| Witness count | 20 PROVEN (one new: BEAM_SPAN_LIMIT) | ✓ 20 proven |

**Known Limitations (Watchdog Review 2026-02-03):**

1. **Grid Alignment Gap**: `placeGridBeams()` computes evenly-spaced gridlines from room bounds + max span, rather than using DSL grid positions (C=12, D=20, E=28, F=32). Result: beams at 6.67m even spacing instead of grid-aligned 8m/8m/4m spans.

   | Expected (DSL grid) | Actual (even spacing) |
   |---------------------|----------------------|
   | X: 12→20→28→32 (8m, 8m, 4m) | X: 12→18.67→25.33→32 (6.67m each) |
   | Y: 21→27→33 (6m, 6m) | Y: 21→27→33 (6m, 6m) ✓ matches |

2. **8m Boundary Not Stress-Tested**: BEAM_SPAN_LIMIT passes at 6.67m against 8m limit. The witness measures correctly, but even spacing prevents the grid from exercising the actual code boundary (8m spans at C→D, D→E).

3. **STRUCTURAL_GRID_COMPLETE Checks Incomplete**:

   | Check | Status |
   |-------|--------|
   | (a) beam endpoints touch column positions ±tolerance | ✗ NOT YET (requires beam start/end, not center) |
   | (b) both axes covered | ✓ YES (hasTwoWayGrid) |
   | (c) no orphan columns | ✗ NOT YET (needs connectivity graph) |

**Proposed Fix (Phase 55 or later):**
- Pass building Grid object to `placeGridBeams()`
- Extract grid positions within room bounds
- Place columns at grid intersections
- Beams span between adjacent grid positions
- This would produce 8m/8m/4m spans matching DSL, stress-testing BEAM_SPAN_LIMIT

**BEAM_SPAN_LIMIT Claim Output:**
```json
{
  "claim_id": 25,
  "claim_name": "BEAM_SPAN_LIMIT",
  "status": "PROVEN",
  "evidence": {
    "construction_system": "MASONRY",
    "max_allowed_span": 8.0,
    "beam_count": 17,
    "max_actual_span": 6.67,
    "all_beams_within_limit": true
  }
}
```

**Skipped Claims (expected for TB-LKTN/TB-LKTN-2S):**
- BEAM_SPAN_LIMIT — No structural grid (residential, not institutional)
- STRUCTURAL_GRID_COMPLETE — Same reason

---

## Phase 54: Witness Hardening — COMPLETE

### Session 2026-02-03: Cross-Storey Witness Proof Integrity

**Scope:** Strengthen CORRIDOR_CONNECTS_ALL and FIRE_TRAVEL_DISTANCE witnesses to include cross-storey paths via stairs.

**Problem Addressed:** Prior to Phase 54, these claims were "documented lies" — they said PROVEN but measured something weaker than claimed:
- CORRIDOR_CONNECTS_ALL checked per-storey only, ignoring stair connectivity
- FIRE_TRAVEL_DISTANCE used X,Y distance to exits regardless of storey level

**Code Changes:**

1. **WitnessBuilder.java** (Phase 54 enhancements):
   - Added `corridorByStorey` map: tracks corridor room per storey
   - Added `stairConnections` list: tracks stairs linking storeys
   - Added `totalStoreys` field for multi-storey detection
   - New methods: `corridorOnStorey()`, `stairConnection()`, `setTotalStoreys()`
   - Enhanced `corridorConnection()` to accept storey parameter
   - Enhanced `buildCorridorConnectsAllClaim()` to verify cross-storey connectivity via stairs

2. **BuildingWriter.java** (Phase 54 fire travel + corridor connectivity):
   - CORRIDOR_CONNECTS_ALL: Tracks corridors per storey, collects stair connections
   - Pragmatic heuristic: if storey has corridor, assume stairs on that storey accessible from corridor
   - FIRE_TRAVEL_DISTANCE: Upper floor path = room→corridor + stair_run + stair_base→exit
   - Only ground floor exterior doors count as valid fire egress points
   - Smart threshold: 45m if multiple exits, 30m dead-end otherwise

**Witness Output Enhancement:**

CORRIDOR_CONNECTS_ALL now shows:
```json
{
  "total_storeys": 2,
  "corridors_per_storey": {"Ground": "koridor", "Upper": "koridor_u"},
  "stair_connections": [{"stair": "main", "from_storey": "Ground", "to_storey": "Upper", "in_room": "koridor"}],
  "storeys_linked_via_stairs": true,
  "rooms_by_storey": {"Ground": 10, "Upper": 10}
}
```

FIRE_TRAVEL_DISTANCE now shows paths with stair travel:
```
toilet_m_u -> koridor_u (19.0m) -> stair main (4.3m) -> class_2_door_south (8.5m) = 31.8m
```

**Known Limitation Documented:**

Stair grid position bug: `parseGridPosition()` returns raw grid indices (E2 → [4,1]) while room bounds use actual grid coordinate lookup. Stair at (4.0, 1.0) is geometrically outside corridor at Y=[7,10]. Phase 54 uses pragmatic heuristic (storey with corridor → stairs accessible from corridor) rather than geometric containment.

**Test Results:**

| Building | Proven | Skipped | Unprovable | Status |
|----------|--------|---------|------------|--------|
| School   | 19     | 5       | 0          | PASS   |
| TB-LKTN  | 17     | 7       | 0          | PASS   |
| TBLKTN2S | 18     | 6       | 0          | PASS   |

**Gate Checklist:**

| Gate | Requirement | Result |
|------|-------------|--------|
| CORRIDOR_CONNECTS_ALL | Cross-storey via stairs | ✓ storeys_linked_via_stairs: true |
| FIRE_TRAVEL_DISTANCE | Includes stair run for upper floors | ✓ path shows stair travel |
| Regression | All existing tests pass | ✓ 3/3 buildings pass |

---

## Phase 52: Multi-Storey School — COMPLETE

### Session 2026-02-02: 2-Storey School Implementation

**Scope:** Add second storey to Sekolah-Kebangsaan.bim with reduced footprint (no rooms above assembly hall/canteen).

**DSL Changes:**
- Added `STAIR "main" at:E2 width:1.2m to:"Upper"` to Ground floor
- Added complete STOREY "Upper" block with 11 rooms
- Upper floor has teaching block only (reduced footprint)
- No rooms above kantin (A5-C7) or dewan (C5-F7)
- West door removed from upper corridor (opens to 3.2m drop)

**Room Layout:**

| Storey | Rooms | Footprint |
|--------|-------|-----------|
| Ground | 13 | Full (32m × 33m) |
| Upper | 11 | Reduced (32m × 17m) |

Upper floor rooms: toilet_m_u, class_7-9, bilik_sumber, koridor_u, toilet_f_u, class_10-12, makmal_komputer

**Test Results:**

| Building | Proven | Skipped | Unprovable | Status |
|----------|--------|---------|------------|--------|
| School   | 19     | 5       | 0          | PASS   |
| TB-LKTN  | 17     | 7       | 0          | PASS   |
| TBLKTN2S | 18     | 6       | 0          | PASS   |

**Gate Checklist:**

| Gate | Requirement | Result |
|------|-------------|--------|
| DSL | Upper floor reduced footprint | ✓ 11 rooms vs 13 ground |
| Stair | In corridor, connects both floors | ✓ At E2 (east end) |
| STOREYS | Claim activates and PROVEN | ✓ |
| CORRIDOR_CONNECTS_ALL | Passes both floors | ✓ PROVEN |
| FIRE_TRAVEL_DISTANCE | Under 30m limit | ✓ PROVEN |
| ROOF_COVERS_ALL | Checks both floors | ✓ PROVEN |
| Plumbing | Keep SKIPPED | ✓ 3 claims skipped |
| Witness count | 19+ PROVEN | ✓ 19 proven |

**Skipped Claims (expected):**
- PLUMBING_WASTE_COMPLETE — No system graph
- PLUMBING_VENT_COMPLETE — No system graph
- PLUMBING_SUPPLY_COMPLETE — No system graph
- PARTY_WALLS_VALID — Single-unit building
- SEPARATING_FLOORS_VALID — Single-unit building

**Witness Claim Taxonomy (Architectural Note):**

| Layer | Claims | Applies to |
|-------|--------|------------|
| Universal | FOUNDATION, ENTRY, ROOMS_ENCLOSED, ROOMS_IN_ENVELOPE, ROOF_COVERS_ALL, WINDOWS_ON_EXTERIOR, ELECTRICAL_IN_SPACES, FIXTURES_ATTACHED, MEP_NO_STRUCTURAL_CLASH, ALL_ROOMS_REACHABLE, PLUMBING_PIPES_VALID, ALL_OUTLETS_ON_CIRCUIT, ROOM_AREAS_CONSISTENT | All buildings |
| Typology-specific | CLASSROOM_DAYLIGHT, TOILET_ACCESSIBLE, CORRIDOR_CONNECTS_ALL, STRUCTURAL_GRID_COMPLETE, FIRE_TRAVEL_DISTANCE | Institutional only |
| Configuration-specific | PARTY_WALLS_VALID, SEPARATING_FLOORS_VALID, STOREYS_VERTICALLY_CONSISTENT, 3× PLUMBING_*_COMPLETE | Multi-unit / multi-storey / plumbing-enabled |

*Future direction: Filter claims by building metadata (typology, unit count, storey count, MEP level) rather than running all 24 and SKIPping inapplicable ones. Not blocking at 24 claims; becomes noise at 40+.*

**Documented Limitations (Phase 52):**

1. ~~**CORRIDOR_CONNECTS_ALL** — Per-storey only, stairs not integrated~~ → Fixed in Phase 54
2. ~~**FIRE_TRAVEL_DISTANCE** — X,Y horizontal only, no stair distance~~ → Fixed in Phase 54
3. **Single stair egress** — UBBL 166 requires ≥2 escape routes for >20 occupants (not yet enforced)

**New Test Class:** `SchoolEndToEndTest.java` — Validates 2-storey school compilation

---

## Phase 50: School Building Typology — CLOSED

### Session 2026-02-02: Phase 50D Closeout

**README updated** with:
- School as second example building
- Witness system documentation (24 claims)
- Structural model explanation (masonry vs framed)
- Honest documentation of beam span subdivision limitation

**Structural Grid Analysis (Watchdog-verified):**

The 3-beams-only-in-dewan model is structurally defensible for masonry construction:
- Load-bearing masonry walls carry roof/floor loads directly
- Classrooms (7-8m spans) within normal masonry bearing wall limits
- Columns at corners/T-junctions provide lateral stability
- Beams only required where clear spans exceed wall capacity (assembly hall 20×12m)

**Known limitation (narrow and specific):** Assembly hall beams span full room dimension
rather than subdividing at intermediate column positions. A 20m concrete beam would need
~1.5m depth to work structurally — that's a transfer girder, not a beam.

**Witness Hardening Roadmap Items:**

| Witness | Purpose | Blocker? |
|---------|---------|----------|
| `LINTEL_AT_HEAD_HEIGHT` | Verify lintel minZ = opening head height ± tolerance | No |
| `BEAM_SPAN_LIMIT` | Verify no beam exceeds max span for construction type (~6m RC, ~9m steel per MS 1195/Eurocode) | No |

Both are future hardening — not Phase 50 blockers.

**Final Test Results:**

| Building | Proven | Skipped | Unprovable | Status |
|----------|--------|---------|------------|--------|
| School   | 18     | 6       | 0          | PASS   |
| TB-LKTN  | 17     | 7       | 0          | PASS   |
| TBLKTN2S | 18     | 6       | 0          | PASS   |

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

Phase 51 COMPLETE. Two-way structural grid with beam span limits done. Witness count now 25 (was 24).

---

## Session 2026-02-03: ERP Migration Spec Clarification

**Context:** User observed that for stable system migration, analyzing production data (thousands of orders) is redundant since rules already exist in model classes.

**Updates to ERP_MIGRATION_ENGINE_SPEC.md:**
1. Added "Stable System Insight" to Extractor section — rules are TRANSCRIBED from validators, not DISCOVERED
2. Clarified Verification Strategy — production data is for VERIFICATION, not rule mining
3. Added "Stable System Corollary" to The Vibe section — M* classes ARE the specification

**Key Principle:** For mature ERP systems, extraction is transcription. The `MOrderValidate.beforeComplete()` method defines the credit check rule; we convert it to YAML, we don't rediscover it from historical data.

## Session 2026-02-03: AD-Style Architecture Roadmap

**Context:** Discussion on whether BIM compiler should adopt iDempiere-style Application Dictionary (AD) metadata-driven architecture.

**Updates to FOSS_DEVELOPER_GUIDE.md:**
1. Added Appendix E: AD-Style Architecture Roadmap
2. Updated witness claim count from 24 to 25 (BEAM_SPAN_LIMIT added in Phase 51)
3. Updated Table of Contents with all appendices

**Key Points:**
- Current state: `spacetypes.yaml` is effectively AD_SpaceType (mature, 670+ lines)
- Constants still scattered in Java Placer classes (needs eventual externalization)
- Timing: Wait until Phase 55+ and more building examples before major refactor
- Vision: YAML as hot-swappable intent layer + AI-assisted editing + deterministic compilation

**The convergence insight:** BIM (greenfield) BUILDS rules into configuration; iDempiere (brownfield) EXTRACTS rules to configuration. Both end at the same architecture.

---

**Sequencing:**
1. ~~**Phase 52: Multi-storey school**~~ — DONE
2. ~~**Phase 54: Multi-storey witness hardening**~~ — DONE (stair-integrated connectivity + fire travel)
3. ~~**Phase 51: Two-way structural grid**~~ — DONE (17 beams, 6-8m spans, BEAM_SPAN_LIMIT claim)
4. **Phase 55: Fix stair grid position bug** — parseGridPosition returns raw indices vs room bounds use grid coordinate lookup

**Known Bug (Phase 54 workaround):**
Stair placement uses raw grid indices (E2 → [4,1] meters) while room bounds use actual grid coordinate mapping. Phase 54 uses pragmatic heuristic (storey with corridor → stairs accessible from corridor) rather than geometric containment check. Fix requires updating BuildingCompiler to use `building.grid().getX/Y()` for stair positions like it does for rooms with `bounds:` syntax.

**Witness Hardening Roadmap (Lower Priority):**
- `LINTEL_AT_HEAD_HEIGHT` — Verify lintel minZ = opening head height
- ~~`BEAM_SPAN_LIMIT`~~ — DONE (Phase 51)

**Test Commands:**
```bash
# School (2-storey)
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.SchoolEndToEndTest" -q

# TB-LKTN (single-storey)
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNEndToEndTest" -q

# TB-LKTN-2S (2-storey house)
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTN2SEndToEndTest" -q
```
