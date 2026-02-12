# PROGRESS — Current Development State

**Last updated:** 2026-02-12
**Current phase:** Phase 119F — Multi-Discipline Thesaurus from Federation DB
**Baseline:** 7 E2E PASS, SampleHouse 26% ARC fidelity, Duplex 36% ARC fidelity, 3 Rosetta stones, 14 grammar rules

---

## Session Summary — Phase 119F: Multi-Discipline Thesaurus from Federation DB

### What Was Done

Applied the Linguist's Method to the 3rd Rosetta Stone (SJTII Terminal — Malaysian airport
terminal, 15,104 elements, 8 disciplines). First non-residential stone; first with real
multi-discipline overlap data.

**1. Dictionary extraction with discipline filter** (`tools/rosetta_dictionary.py`):
- Added `--discipline` flag (comma-separated: ARC,STR,FP,ACMV,CW,ELEC,SP,LPG)
- Generated per-discipline dictionaries: ARC (1790 lines), STR (1545), FP (106), ACMV (99)
- Saved to `reference/rosetta/Terminal_dictionary_*.txt`

**2. Cross-discipline overlap tool** (`tools/cross_discipline_checker.py`):
- New single-purpose tool: 9 analysis sections using rtree bbox overlap queries
- Sections: pipe-in-wall, duct-slab, column-wall, beam inventory, sprinkler-ceiling,
  storey mapping, wall finish types, column grid regularity, discipline overlap matrix
- Saved analysis to `reference/rosetta/Terminal_cross_discipline.txt`

**3. Terminal Thesaurus** (`reference/rosetta/TERMINAL_THESAURUS.md`):
- 6-section cross-reference: Walls, Openings, Furniture, Structural, Cross-Discipline, Storey Convention
- 3rd dialect column added (MY institutional alongside UK residential and US residential)
- Key discoveries documented with evidence counts

**4. Grammar expanded** (`reference/rosetta/GRAMMAR.md`):
- Rule 1 updated with Malaysian dialect (150mm BrickPlaster — verified across 3 stones)
- Rule 7 upgraded from "unverified" to "partially verified" (3rd stone confirms template_grid)
- 6 new cross-discipline rules (Rules 9-14) — all evidence-based

### Key Discoveries

| Finding | Evidence | Grammar Rule |
|---------|----------|-------------|
| Pipes fit inside walls | 766 overlaps, 99.3% pipe.dia < wall.thick | Rule 9 |
| Ceramic finish = wet room | 70/330 walls (21%), co-located with SP/CW | Rule 10 |
| Columns at wall junctions | 45/59 positions (76%) at ≥2 wall intersections | Rule 11 |
| RC beam D/S ratio | 248 beams, D/S = 0.10-0.15 | Rule 12 |
| Ceiling void ~1m | 4 storeys consistent ~900-1000mm | Rule 13 |
| Sprinkler drop 0.5-1.2m | 909 terminals measured | Rule 14 |

**Malaysian wall simplification**: 150mm single-type for 93% of walls (no insulation
cavity in tropical climate). Grammar Rule 1 still holds — just simpler layers.

**Dual storey naming**: Malay (Aras 01-04, Aras Tanah, Aras Bumbung) vs English
(GROUND FLOOR LEVEL, FIRST-FOURTH FLOOR LEVEL). Not an error — reflects different
discipline perspectives on "which floor": ARC/STR = structural level, MEP = service zone.

### Files

| File | Action |
|------|--------|
| `tools/rosetta_dictionary.py` | MODIFIED — added `--discipline` filter via argparse |
| `tools/cross_discipline_checker.py` | NEW — 9-section bbox overlap analysis tool |
| `reference/rosetta/Terminal_dictionary_ARC.txt` | NEW — ARC discipline dictionary (1790 lines) |
| `reference/rosetta/Terminal_dictionary_STR.txt` | NEW — STR discipline dictionary (1545 lines) |
| `reference/rosetta/Terminal_dictionary_FP.txt` | NEW — FP discipline dictionary (106 lines) |
| `reference/rosetta/Terminal_dictionary_ACMV.txt` | NEW — ACMV discipline dictionary (99 lines) |
| `reference/rosetta/Terminal_cross_discipline.txt` | NEW — cross-discipline analysis output |
| `reference/rosetta/TERMINAL_THESAURUS.md` | NEW — 3rd stone thesaurus (6 sections) |
| `reference/rosetta/GRAMMAR.md` | MODIFIED — Rules 9-14 added, Rule 1+7 updated |
| `PROGRESS.md` | MODIFIED — session closeout |

### What's Next — Phase 119G

From the ARC-focused action list (ordered by X-ray impact):
1. Add missing Duplex window types — 750x2200 (4 ref), 2800x2410 (4 ref)
2. Wall near→exact — Duplex 14/57 near-match; 0 exact
3. Add finish floor slabs — Duplex ref has 21 slabs (structural+finish per zone)
4. Reduce structural overcount — SH: 26→20 members, Duplex: 154→4 members

From cross-discipline grammar (new from 119F):
5. Add Malaysian wall types to `ad_wall_type` — 150mm BrickPlaster, 250mm
6. Add wall finish → room function mapping to `ad_wall_type`
7. Column grid resolver for institutional/commercial templates

---

## Session Addendum — Phase 119E: Unified Extract Tool + Federation as 3rd Stone

### Unified `tools/extract.py`
Single tool replaces 5 bespoke scripts:
- `extract_all_components.py`, `import_ifc_furniture.py`, `extract_duplex_components.py` (Layer 1)
- `populate_sample_house_db.py`, `populate_duplex_db.py` (Layer 3)

Handles both `.ifc` (tessellates via ifcopenshell) and `.db` (copies pre-tessellated data).
Modes: `--to library` (LOD400) or `--to reference` (Rosetta). Filters: `--classes`, `--exclude`, `--discipline`.

### Federation DB as 3rd Rosetta Stone
Extracted `database/enhanced_federation_GI.db` → `reference/rosetta/SJTII_Terminal_extracted.db`:
- **15,104 elements** across 8 disciplines (excluded IfcPlate 33K, IfcOpeningElement 631, IfcReinforcingBar 2660)
- ARC: 1,400 elements (330 walls, 236 windows, 176 furniture, 135 doors, 91 slabs)
- FP: 6,863 | ACMV: 1,621 | CW: 1,431 | STR: 1,429 | ELEC: 1,172 | SP: 979 | LPG: 209

### Developer Guide Updated
- Full "Data Provenance" section with 3-layer diagram (Geometry / Metadata / Rosetta)
- Extraction tool documentation
- Rosetta Stone pairs table (all 3 active)

---

## Session Summary — Phase 119E: Opening Count Alignment

### Problem
The compiler generated too many openings vs reference IFC models — the #1 action by ARC X-ray impact. Two mechanisms double-generated doors, and auto-windows fired on walls that shouldn't have them.

### Fixes Applied

1. **BOM Auto-Door Duplicate** (`StoreyCompiler.java`):
   - Pre-compute `adjacencyParticipants` set (rooms with intra-storey `adjacent:` constraints)
   - BOM auto-door now skipped for rooms that get their door from the adjacency handler
   - Condo unaffected (no `adjacent:` constraints → empty set)

2. **Auto-Window Overcount** (`StoreyCompiler.java`):
   - If room has ANY explicit WINDOW declarations → skip all auto-windows for that room
   - If a wall already has a DOOR → skip auto-window on that wall
   - Prevents phantom windows on entrance walls and rooms with deliberate window specs

3. **SampleHouse DSL Fix** (`examples/Ifc4_SampleHouse.bim`):
   - Bedroom `WINDOW south` → `WINDOW north` (south is interior toward entrance)

### Score Impact

| Pair | ARC X-ray Before | ARC X-ray After | ARC Overall Before | ARC Overall After |
|------|-----------------|-----------------|-------------------|-------------------|
| SampleHouse | 17% (6/35) | **17%** (6/35) | 28% | **26%** |
| Duplex | 27% (51/183) | **27%** (51/183) | 37% | **36%** |

**Opening count alignment** (the real win):

| Pair | Metric | Before | After | Reference |
|------|--------|--------|-------|-----------|
| SampleHouse | Doors | 5 | **3** | 3 (EXACT) |
| SampleHouse | Windows | 7 | **4** | 4 (EXACT) |
| SampleHouse | Opening match | — | **71%** (5/7) | — |
| Duplex | Doors | ~26 | **12** | 14 (0.86x) |
| Duplex | Windows | ~10 | **8** | 24 (0.33x) |
| Duplex | Opening match | — | **39%** (15/38) | — |

### Element Counts

| Building | Before | After | Change |
|----------|--------|-------|--------|
| CONDO-MID | 10,516 | **10,516** | 0 (safe) |
| SampleHouse | 116 | **109** | -7 |
| Duplex | 559 | **533** | -26 |

### New Spatial Digests

| Building | Elements | SHA256 Digest |
|----------|----------|--------------|
| CONDO-MID | 10,516 | `406d7b96f01157fcb6927c45c57cce5e8c6f37bd6be91fb6b1456974d3e1aa5c` |
| SampleHouse | 109 | `90130860921943e8b3dff44c9f28368a54708cdc46d9422fbc4f56cb6f92ca24` |
| Duplex | 533 | `f4ddf9996df30844b71fa3766b6d93916426caa533204e71958973c3a95a4f9c` |

### All 7 E2E Tests Pass
Condo, SampleHouse, Duplex, TB-LKTN, TB-LKTN-2S, TB-LKTN-Compact, School

### What's Next — Phase 119F
From the ARC-focused action list (ordered by X-ray impact):
1. ~~Prune opening overcount~~ ✅ DONE (Phase 119E)
2. Add missing Duplex window types — 750x2200 (4 ref), 2800x2410 (4 ref)
3. Wall near→exact — Duplex 14/57 near-match; 0 exact. Close but dims not bucketizing
4. Add finish floor slabs — Duplex ref has 21 slabs (structural+finish per zone)
5. Reduce structural overcount — SH: 26→20 members, Duplex: 154→4 members

---

## Session Summary — Phase 119D: Linguist's Method Epistemology

### Approach: Dictionary → Thesaurus → Grammar

**Step 1 (Dictionary)**: Extracted all spatial facts from both reference stones.
- Tool: `tools/rosetta_dictionary.py` — 11-section spatial skeleton extractor
- Saved: `reference/rosetta/SampleHouse_dictionary.txt`, `reference/rosetta/Duplex_dictionary.txt`
- Key insight: read both stones completely before writing any rules

**Step 2 (Thesaurus)**: Mapped equivalent concepts across UK/US dialects.
- Document: `reference/rosetta/THESAURUS.md`
- Key discovery: Duplex window thin = wall thickness (417mm); opening mesh depth ≠ spatial depth
- Cross-stone equivalences: wall=Σ(layers), opening.thin=frame_depth, furniture=catalog(profile,room)

**Step 3 (Grammar)**: Formalized 8 rules from first principles.
- Document: `reference/rosetta/GRAMMAR.md`
- 6 verified rules (common to both stones), 2 unverified (need more data)

### Concrete Fixes Applied

1. **Migration 119D** (`migration/migration_119D_opening_depth_alignment.sql`):
   - Fixed US window depth: 100→417mm (fill wall void)
   - Fixed US skylight depth: 100→178mm
   - Fixed Duplex door depths: entry=467mm, glass=467mm, interior=174mm
   - Updated generic family depths to reasonable frame values

2. **OpeningWriter.java** — Family depth overrides mesh depth in bbox:
   - Library door path: `physicalDepth = door.depth()` when family depth available
   - Library window path: same override for windows
   - LOD400 mesh geometry unchanged — only spatial envelope (bbox) corrected

### Score Impact

| Pair | X-ray Before | X-ray After | Overall Before | Overall After |
|------|-------------|-------------|----------------|---------------|
| SampleHouse | 2% | **10%** | 15% | **21%** |
| Duplex | 5% | **7%** | 12% | **13%** |

**ARC-only scores** (with `--discipline ARC` filter):

| Pair | ARC X-ray | ARC Overall | Notes |
|------|----------|-------------|-------|
| SampleHouse | **17%** | **28%** | 6/35 near-matched |
| Duplex | **27%** | **37%** | 51/183 near-matched |

MEP-only Duplex: 3% (32/890) — tracked separately.

### Key Rosetta Documents
- Strategy: `docs/TheRosettaStoneStrategy.txt` (updated with idioms + discipline separation)
- Dictionary: `reference/rosetta/SampleHouse_dictionary.txt`, `Duplex_dictionary.txt`
- Thesaurus: `reference/rosetta/THESAURUS.md`
- Grammar: `reference/rosetta/GRAMMAR.md`
- Spatial checker: `python3 tools/spatial_checker.py [out] [ref] --discipline ARC|MEP|STR`

### What's Next — Phase 119E: Cross-Discipline Grammar + Federation DB

**Key insight**: all construction needs ARC+MEP+STR working together. Previous low scores came from treating them independently. The grammar must derive CROSS-DISCIPLINE phrases:

**Cross-discipline correlations to extract:**
- **Wall↔MEP**: Plumbing walls (184mm) exist BECAUSE of pipes inside. MEP routing drives wall type classification. Grammar: `WET_ROOM adjacent → PLUMBING_WALL (not PARTITION)`
- **Slab↔Room function**: Finish floor type depends on room (wet=ceramic 13mm, dry=wood 19mm). Grammar: `ROOM.function → SLAB.finish_type`
- **Structure↔ARC**: Beam placement follows room span. Column grid follows room layout. Grammar: `ROOM.span > threshold → BEAM at midspan`
- **Opening↔MEP**: Ventilation windows in wet rooms. Electrical outlets near doors. Grammar: `WET_ROOM + exterior → VENTILATION window`

**Federation DB as 3rd+ Rosetta Stone:**
`database/enhanced_federation_GI.db` contains many building types beyond residential (offices, commercial, institutional). Adding it to Rosetta gives:
- More stones = more grammar coverage + cross-type validation
- MEP patterns differ by building type (office HVAC vs residential plumbing)
- Structural patterns (steel frame office vs timber residential)
- The dictionary extractor (`tools/rosetta_dictionary.py`) already works on any DB

**ARC-focused actions (ordered by X-ray impact):**
1. Prune opening overcount — SH: 5→3 doors, 7→4 windows. Duplex: 26→14 doors
2. Add missing Duplex window types — 750x2200 (4 ref), 2800x2410 (4 ref)
3. Wall near→exact — Duplex 14/57 near-match; 0 exact. Close but dims not bucketizing
4. Add finish floor slabs — Duplex ref has 21 slabs (structural+finish per zone)
5. Reduce structural overcount — SH: 26→20 members, Duplex: 154→4 members

---

## Session Summary — Phase 118C: SlotRegistry + WorkerRegistry + Rosetta Stone Strategy

### Rosetta Stone Setup

**Strategy**: Use real IFC files as fossil truth to measure compiler fidelity.
See `docs/TheRosettaStoneStrategy.txt` for full rationale.

| Pair | Source IFC | Reference DB | DSL | Output DB | X-ray Baseline |
|------|-----------|-------------|-----|-----------|----------------|
| SampleHouse | `Ifc4_SampleHouse.ifc` | `reference/rosetta/Ifc4_SampleHouse_extracted.db` (55 elem) | `examples/Ifc4_SampleHouse.bim` | `output/ifc4_sample_house.db` (116 elem) | **2%** |
| Duplex | `Ifc2x3_Duplex_Architecture.ifc` | `reference/rosetta/Ifc2x3_Duplex_extracted.db` (1085 elem) | `examples/Ifc2x3_Duplex.bim` | `output/ifc2x3_duplex.db` (559 elem) | **5%** |

Run spatial checker:
```
python3 tools/spatial_checker.py  # defaults to Duplex
python3 tools/spatial_checker.py output/ifc4_sample_house.db reference/rosetta/Ifc4_SampleHouse_extracted.db
```

### SlotRegistry + WorkerRegistry

### What Was Done

**Replaced ad_space_type_furniture lookup with ad_room_slot as single source of truth for furniture dispatch — zero behavior change:**

1. **`migration/migration_118C_slot_registry.sql`** — Aligned ad_room_slot FURNITURE slots with ad_space_type_furniture:
   - UPDATE OFFICE assembly_id: WORKSTATION_SET → ROOM_FURNITURE (was a mismatch)
   - INSERT 3 missing FURNITURE slots: LOBBY, OPEN_PLAN, STAFFROOM (all ROOM_FURNITURE)
   - Now 8 FURNITURE slots match exactly the 8 bomId entries in ad_space_type_furniture

2. **`SlotRegistry.java`** — Lazy singleton reads ad_room_slot (follows WallTypeResolver pattern):
   - `getFurnitureAssemblyId(roomType)` → returns assembly_id for FURNITURE slot, or null
   - `getSlotsForType(roomType)` → returns all slots ordered by priority (for Phase 118D)
   - Graceful degradation if table missing

3. **`WorkerRegistry.java`** — Maps assembly_id → BundleWorker with caching:
   - `registerDefault(factory)` — catch-all factory (118C: all → FurnitureWorker)
   - `getWorker(assemblyId)` → cached BundleWorker instance
   - Replaces `Map<String, FurnitureWorker> workerCache` from 118B

4. **`StoreyCompiler.java`** — Rewired furniture routing:
   - Primary path: `SlotRegistry.getFurnitureAssemblyId()` → `WorkerRegistry.getWorker()` → `execute()`
   - Fallback path: `FurnitureTypeResolver` kept only for CANTEEN/SEATING/WORKSTATION (no ad_room_slot entry)
   - Same behavior for all 8 BOM-driven room types + 3 fallback types

### Golden Digests (Phase 118C — Unchanged)

| Building | Elements | SHA256 Digest |
|----------|----------|--------------|
| CONDO-MID | 10,516 | `18b3062356ecef701d6c80bb979f9f02668e69fa202976925b297b88405b44c6` |
| TB-LKTN-DUPLEX | 559 | `f0ff9b03dfcb3006de4ea8dbb1169b383da06b4384f8e8474e3fefd06679a180` |

### Verification

| Check | Result |
|-------|--------|
| `mvn compile -q` | PASS |
| CondoMidEndToEndTest | PASS (10516 elements, digest unchanged) |
| SchoolEndToEndTest | PASS |
| TBLKTNEndToEndTest | PASS |
| TBLKTN2SEndToEndTest | PASS |
| TBLKTNCompactEndToEndTest | PASS |
| TBLKTNDuplexEndToEndTest | PASS (559 elements, digest unchanged) |
| Sanity (condo_mid.db) | 23 PASS, 4 FAIL, 6 WARN (identical to baseline) |

### Files

| File | Action |
|------|--------|
| `migration/migration_118C_slot_registry.sql` | NEW — align ad_room_slot with ad_space_type_furniture |
| `src/main/java/com/bim/compiler/library/SlotRegistry.java` | NEW — lazy singleton reads ad_room_slot |
| `src/main/java/com/bim/compiler/library/WorkerRegistry.java` | NEW — assembly_id → BundleWorker cache |
| `src/main/java/com/bim/compiler/dsl/StoreyCompiler.java` | MODIFIED — SlotRegistry+WorkerRegistry replaces FurnitureTypeResolver+workerCache |
| `examples/Ifc2x3_Duplex.bim` | NEW — Rosetta Stone DSL for Duplex (renamed from TB-LKTN-DUPLEX) |
| `examples/Ifc4_SampleHouse.bim` | NEW — Rosetta Stone DSL for SampleHouse |
| `src/main/java/com/bim/compiler/dsl/SampleHouseEndToEndTest.java` | NEW — E2E test for SampleHouse |
| `src/main/java/com/bim/compiler/dsl/TBLKTNDuplexEndToEndTest.java` | MODIFIED — points to Ifc2x3_Duplex.bim, output/ifc2x3_duplex.db |
| `reference/rosetta/Ifc2x3_Duplex_extracted.db` | NEW — extracted ground truth from Duplex IFC |
| `reference/rosetta/Ifc4_SampleHouse_extracted.db` | NEW — extracted ground truth from SampleHouse IFC |
| `scripts/populate_sample_house_db.py` | NEW — IFC extraction for SampleHouse |
| `tools/spatial_checker.py` | MODIFIED — defaults updated to new paths |
| `docs/TheRosettaStoneStrategy.txt` | NEW — strategy document |

## Session Summary — Phase 119 Step 1: Wall Thickness Alignment + Spatial Checker Overhaul

### What Was Done

**1. Profile-aware wall thickness resolution — all walls now use `ad_wall_type_rule` with profile matching:**

- **`migration/migration_119_wall_alignment.sql`** — Added `profile` column to `ad_wall_type_rule`, UK residential wall types (290mm exterior brick, 95mm partition), and profile-specific rules (priority 50 beats generic 100).
- **`WallTypeResolver.java`** — `profile` field on `WallTypeRule`, 5-arg `resolveThickness` overload with two-pass resolution: profile-specific first, generic fallback. Graceful degradation if `profile` column missing.
- **`StoreyCompiler.java`** — Perimeter walls resolve exterior thickness via profile. Interior walls AND partition walls (uncovered edges) also pass profile. New `compilePerimeterWall` overload with thickness+cladding.
- **`MultiUnitCompiler.java`** — Profile threaded through wall classification chain. **Fixed `createUnitBuildingDefinition`/`createSharedBuildingDefinition`** which dropped profile/protocol/lod/facade from unit-level BuildingDefinition.

**2. Spatial Checker overhaul — broader and smarter comparison:**

- **IFC class dictionary**: `IfcPlate→WALL` (was PANEL), so compiled walls can match reference `IfcWall` in X-ray
- **Slab/Roof comparison** (Section 7): new section comparing slab and roof bounding box signatures
- **Two-tier X-ray matching**: exact (10mm bucket) + near-match (thin dim ±5mm, long dims ±10%)
- **Near-match summary**: OVERALL score now uses near-match, giving credit for elements with correct thickness but different lengths

### Rosetta Stone Scores

| Pair | Wall Match | Overall (was) | Overall (now) |
|------|-----------|---------------|---------------|
| SampleHouse | **5/5 (100%)** | 2% | **8%** |
| Duplex | **42/57 (73%)** | 5% | **8%** |

Duplex X-ray near-matches: WALL 14, PIPE 14, FITTING 14, FURNITURE 12, TERMINAL 4 = 58/1085

### Golden Digests (Phase 119)

| Building | Elements | SHA256 Digest |
|----------|----------|--------------|
| CONDO-MID | 10,516 | `eabe90b1c919d49a27f9a42c51ead9da2e87486343123be21a560e24ed5c94c3` |
| SampleHouse | 116 | `81c78863dc36773445c2b45a56e06dcc2198b24d34da1bbbbca709f9f58080f1` |
| Duplex | 559 | `a7da6e84a8645df750a20deb3abd607c82bf61133b2c42eed50682443c0e7669` |

All digests changed from Phase 118C baseline (partition walls now properly resolved via ad_wall_type).

### Verification

| Check | Result |
|-------|--------|
| `mvn compile -q` | PASS |
| CondoMidEndToEndTest | PASS (10516 elements) |
| SampleHouseEndToEndTest | PASS (116 elements) |
| TBLKTNDuplexEndToEndTest | PASS (559 elements) |
| SchoolEndToEndTest | PASS |
| TBLKTNEndToEndTest | PASS |
| TBLKTN2SEndToEndTest | PASS |

### Files

| File | Action |
|------|--------|
| `migration/migration_119_wall_alignment.sql` | NEW — profile column + UK wall types + rules |
| `src/.../library/WallTypeResolver.java` | MODIFIED — profile-aware 5-arg overload, two-pass resolution |
| `src/.../dsl/StoreyCompiler.java` | MODIFIED — all wall paths use resolver with profile |
| `src/.../dsl/MultiUnitCompiler.java` | MODIFIED — profile threaded through wall classification + unit def creation |
| `tools/spatial_checker.py` | MODIFIED — IfcPlate→WALL mapping, slab/roof section, near-match X-ray |

### What's Next — Phase 119 Steps 2-6: Rosetta Stone Convergence

Current scores (Phase 119 Step 1 baseline):

| Check | SampleHouse | Duplex |
|-------|------------|--------|
| Wall thickness | **5/5 (100%)** | **42/57 (73%)** |
| Opening sizes | 0/7 (0%) | 0/38 (0%) |
| Furniture sizes | 1/14 (7%) | 12/61 (19%) |
| Slab/Roof sizes | 0/3 (0%) | 0/21 (0%) |
| X-ray near-match | 1/55 (1%) | 58/1085 (5%) |
| **OVERALL** | **7/84 (8%)** | **112/1262 (8%)** |

---

#### Step 0: Name Dictionary (extraction-time mapping)

**Problem**: Spatial checker matches by bounding-box buckets, but cannot do 1:1 element pairing. Reference element names (e.g. `Doors_ExtDbl_Flush:1810x2110mm`) encode semantic info the checker ignores.

**Solution**: Add `element_dictionary` table to reference DBs during IFC extraction.

```sql
CREATE TABLE element_dictionary (
    ref_ifc_class TEXT,      -- IfcDoor, IfcWindow, IfcFurnishingElement, etc.
    ref_name_pattern TEXT,   -- e.g. "Doors_ExtDbl_Flush:1810x2110mm"
    compiler_category TEXT,  -- DOOR, WINDOW, FURNITURE, WALL, SLAB, ROOF
    compiler_subtype TEXT,   -- D_EXT_DBL, W_SGL_1810x1210, BED, COUCH, etc.
    width_mm INTEGER,        -- nominal width from IFC name (1810)
    height_mm INTEGER,       -- nominal height from IFC name (2110)
    depth_mm INTEGER,        -- nominal depth/thickness (199)
    notes TEXT
);
```

Extraction script changes (`populate_sample_house_db.py`, `populate_duplex_db.py`):
- Parse element_name for dimensional tokens (e.g. `1810x2110mm`)
- Map IFC class → compiler_category using existing CATEGORY_MAP
- Map element type names → compiler_subtype (e.g. `Wall-Ext_102Bwk...` → `EXTERIOR_UK_BRICK_290`)
- Output counts per mapping for validation

Spatial checker changes:
- Load dictionary when available
- Use dictionary for targeted 1:1 matching alongside bucket-based matching
- Report "dictionary coverage" as additional metric

**Files**: `scripts/populate_sample_house_db.py`, `scripts/populate_duplex_db.py`, `tools/spatial_checker.py`

---

#### Step 2: Opening Size Alignment (SampleHouse 0/7 → 7/7, Duplex 0/38 → ~20/38)

**Root cause**: No `ad_opening_schedule` table exists. Compiler uses `BIMConstants.STANDARD_DOOR_WIDTH=0.9m`, `STANDARD_DOOR_HEIGHT=2.1m`, `STANDARD_WINDOW_WIDTH=1.2m`, `STANDARD_WINDOW_HEIGHT=1.2m` for all openings. DSL has no `type:` schedule ref on most openings.

**Reference opening data (SampleHouse)**:
| Type | Name | Width (mm) | Height (mm) | Count |
|------|------|-----------|-------------|-------|
| IfcDoor | IntSgl:810x2110mm | 880 (bbox) | 2145 | 2 |
| IfcDoor | ExtDbl:1810x2110mm | 1860 (bbox) | 2110 | 1 |
| IfcWindow | Sgl_Plain:1810x1210mm | 1860 (bbox) | 1210 | 4 |

**Reference opening data (Duplex)** — 14 doors, 24 windows:
| Type | Nominal | Bbox dims | Count |
|------|---------|-----------|-------|
| IfcDoor | 0864x2032mm | 1016x174x2108 | 6 |
| IfcDoor | 0762x2032mm | 914x174x2108 | 4 |
| IfcDoor | 1250x2010mm | 1402x467x2086 | 2 |
| IfcDoor | 0813x2420mm | 965x467x2496 | 2 |
| IfcWindow | 750x2200mm | 750x417x2200 | 4 |
| IfcWindow | 819x759mm | 819x417x759 | 12 |
| IfcWindow | 2800x2410mm | 2800x417x2410 | 4 |
| IfcWindow | 4835x2420mm | 4835x417x2420 | 2 |
| IfcWindow | Skylight 1180x1170mm | 1173x1225x178 | 2 |

**Compiler output (SampleHouse)** — all wrong:
- Doors: 750x150x2100 (2), 900x150x2100 (3) — should be 880x178x2145, 1860x199x2110
- Windows: 150x1250x1200 (1), 833x150x1200 (1), 1200x150x1000 (4), 1200x150x1200 (1) — should be 1860x353x1210

**Plan**:
1. **Create `ad_opening_schedule` table** in migration:
   ```sql
   CREATE TABLE ad_opening_schedule (
       schedule_id TEXT PRIMARY KEY,
       category TEXT NOT NULL,        -- DOOR or WINDOW
       width_mm INTEGER NOT NULL,
       height_mm INTEGER NOT NULL,
       depth_mm INTEGER DEFAULT 150,
       profile TEXT,                  -- UK_Residential, Malaysian_Residential, etc.
       description TEXT
   );
   ```
2. **Populate with reference-matched entries**:
   - `D_INT_SGL_810` → 810x2110mm (UK), `D_EXT_DBL_1810` → 1810x2110mm (UK)
   - `D_INT_864` → 864x2032mm (US/MY), `D_INT_762` → 762x2032mm (US/MY)
   - `W_SGL_1810x1210` → 1810x1210mm (UK), etc.
3. **Add profile-based default resolution in BuildingCompiler** — when DSL has no `type:` ref on a DOOR/WINDOW, resolve from `ad_opening_schedule` using building profile:
   - UK_Residential: ext door → 1810x2110, int door → 810x2110, window → 1810x1210
   - Malaysian_Residential: ext door → 864x2032, int door → 762x2032, window → varies
4. **Wire DSL `DOOR south type:D_EXT_DBL`** → look up schedule_id `D_EXT_DBL` in `ad_opening_schedule`

**Files**: `migration/migration_119B_opening_schedule.sql`, `BuildingCompiler.java`, `OpeningWriter.java`

---

#### Step 3: Slab/Roof Alignment (SampleHouse 0/3 → 2/3, Duplex 0/21 → ~5/21)

**Root cause**: Compiler produces ONE slab per storey spanning entire building footprint. Reference models have per-room slabs with different thicknesses and finishes.

**Reference slab data (SampleHouse)**:
| Type | Name | Dims (m) |
|------|------|----------|
| IfcSlab | Ground floor (suspended) | 16.87 x 8.67 x 0.47 |
| IfcSlab | Upper floor (simple) | 13.97 x 5.77 x 0.17 |
| IfcRoof | Flat roof (4-felt) | 14.84 x 7.29 x 1.73 |

**Compiler output**: Foundation slab 8.1x14.8x0.15, Roof 9.3x16.0x0.0 (flat degenerate)

**Reference slab data (Duplex)** — 21 slabs at 3 categories:
- Structural: 150mm ext slab-on-grade (2), 127mm slab-on-grade (2), 305mm wood joist (2), 457mm roof joist (1)
- Finish floor: ceramic tile 13mm (5), wood floor 19mm (5)
- Foundation: 2 slabs at 150mm, 2 at 127mm

**Plan**:
1. **Profile-based slab thickness** — `ad_slab_type` table (like `ad_wall_type`):
   - UK_Residential: foundation 470mm, floor 165mm
   - Malaysian_Residential: foundation 150mm, floor 127mm
2. **Roof volume** — compiler currently writes zero-height roof; need actual roof volume from profile:
   - UK_Residential flat: 150mm concrete + insulation = ~200mm min
   - Pitched: calculate volume from pitch angle and overhang
3. **Slab footprint** — compiler slab extends beyond building with SLAB_OVERLAP; reference slab matches exact building footprint. Consider making overlap profile-specific.

**Files**: `migration/migration_119C_slab_types.sql`, `StoreyCompiler.java` (slab compilation), `BuildingWriter.java` (roof writing)

---

#### Step 4: Furniture Tuning (SampleHouse 1/14 → 8/14, Duplex 12/61 → 30/61)

**Current match**: Only items where library component_definitions bounding box falls within 10% tolerance of reference.

**SampleHouse misses** (13/14):
| Ref item | Ref dims (m) | Compiler produces | Gap |
|----------|-------------|-------------------|-----|
| 6x Dining Chair | 0.44 x 0.43 x 1.23 | Not generated (no DINING BOM recipe child) | Need DINING_SET with 6 chairs |
| Bed (Queen) | 2.01 x 1.80 x 0.48 | ~1.9x0.9 (single) | Need queen bed in BED_SET |
| 2x Armchair | 1.43 x 1.40 x 0.92 | Not generated | Need armchair in LIVING_SET |
| Desk | 1.56 x 0.82 x 0.76 | Not generated | Need desk in BEDROOM or STUDY BOM |
| Piano | 1.37 x 0.60 x 1.17 | Not generated (no piano component) | Low priority |
| Coffee Table | 1.20 x 0.55 x 0.45 | Not generated | Need in LIVING_SET |
| Dining Table | 2.00 x 1.00 x 0.75 | Not generated | Need DINING_SET with table |

**Plan**:
1. **Add missing furniture to BOM recipes**: DINING_SET (table + 6 chairs), LIVING_SET add armchair + coffee table
2. **Check/add component_definitions** for queen bed, armchair, coffee table, dining chair, dining table
3. **Profile-specific BOM variants** — SampleHouse BED_SET → queen bed (not single)

**Files**: `migration/migration_119D_furniture_bom.sql`, BOM recipes in component_library.db

---

#### Step 5: Remaining Wall Gaps (Duplex 42/57 → 50/57)

**Missing Duplex wall types**:
| Thickness | Count | Type | Status |
|-----------|-------|------|--------|
| 54mm | 8 | Furring (drywall on stud) | `INTERIOR_FURRING_38` exists (54mm total) — need rule mapping |
| 435mm | 2 | Foundation wall | `FOUNDATION_435` exists — need rule for foundation context |
| 493mm | 2 | Party/CMU wall | `PARTY_CMU` exists — need rule for party wall context |
| 550mm | 2 | Not in DB | Need new wall type (possibly thick foundation + waterproofing) |

**Plan**: Add `ad_wall_type_rule` entries mapping these types to the correct contexts for `Malaysian_Residential` profile. Add 550mm wall type.

**Files**: `migration/migration_119_wall_alignment.sql` (append) or new `migration_119E_wall_rules.sql`

---

#### Step 6: Structural Members — Curtain Wall (SampleHouse +26 elements)

**Reference**: SampleHouse west face has 20 IfcMember (curtain wall mullions, 30mm) + 6 IfcPlate (glazing panels, 25mm). These form a curtain wall grid of vertical/horizontal mullions + glass infill.

**Plan**: This is a larger feature (curtain wall assembly type). Defer until Steps 2-5 deliver >30% overall score.

---

#### Priority Order

| Step | Effort | Expected Score Gain |
|------|--------|-------------------|
| Step 0: Name Dictionary | 1 session | Enables smarter matching (no score change directly) |
| Step 2: Opening Schedule | 1 session | +7 SampleHouse, +20 Duplex |
| Step 3: Slab/Roof | 1 session | +2 SampleHouse, +5 Duplex |
| Step 4: Furniture BOM | 1-2 sessions | +7 SampleHouse, +18 Duplex |
| Step 5: Wall rules | 0.5 session | +8 Duplex |
| Step 6: Curtain wall | 2+ sessions | +26 SampleHouse |

**Target after Steps 0-5**: SampleHouse ~23/84 (27%), Duplex ~205/1262 (16%)

After convergence on these two fossils → language proven → apply to new metadata sets.

---

## Session Summary — Phase 118B: FurnitureWorker — First BundleWorker Adapter

### What Was Done

**Wrapped existing FurniturePlacer pipeline behind BundleWorker interface — zero behavior change:**

1. **`BundleWorker.java` modified** — Added `OpeningInfo` record and `openings` field to `RoomEnvelope`:
   - `record OpeningInfo(String type, String wall, double width)` — decoupled from OpeningSpec
   - `List<OpeningInfo> openings` field added between `maxZ` and `reservedZones`
   - No existing code constructs RoomEnvelope yet, so no breakage

2. **`FurnitureWorker.java` created** — First concrete `BundleWorker` adapter:
   - `implements BundleWorker`, constructor takes `(String bomId, ComponentLibrary library)`
   - `themeId()` returns dynamic bomId (BED_SET, WORKSTATION_SET, LIVING_SET, etc.)
   - `anchorFace()` returns `"BACK"` per framework convention
   - `execute()` converts `BundleWorker.OpeningInfo` → `FurnitureBOMResolver.OpeningInfo`,
     delegates to `FurniturePlacer.placeUniversalFurniture()`,
     converts `FurnitureInstance` → `PlacedElement`
   - Identity chain preserved: `fi.type().name()` → `PlacedElement.role()` → downstream `.toLowerCase()`

3. **`StoreyCompiler.java` rewired** — BOM-driven furniture path uses FurnitureWorker:
   - Added `Map<String, FurnitureWorker> workerCache` (keyed by bomId, avoids duplicate BOM loads)
   - BOM branch builds `RoomEnvelope` + `PlacementContext`, calls `worker.execute()`
   - New `addPlacedElementsToCtx()` converts `PlacedElement` → `FixtureSpec`
   - Fallback paths (CANTEEN, SEATING, WORKSTATION) remain unchanged

### Golden Digests (Phase 118B — Unchanged)

| Building | Elements | SHA256 Digest |
|----------|----------|--------------|
| CONDO-MID | 10,516 | `18b3062356ecef701d6c80bb979f9f02668e69fa202976925b297b88405b44c6` |
| TB-LKTN-DUPLEX | 559 | `f0ff9b03dfcb3006de4ea8dbb1169b383da06b4384f8e8474e3fefd06679a180` |

### Verification

| Check | Result |
|-------|--------|
| `mvn compile -q` | PASS |
| CondoMidEndToEndTest | PASS (10516 elements, digest unchanged) |
| SchoolEndToEndTest | PASS |
| TBLKTNEndToEndTest | PASS |
| TBLKTN2SEndToEndTest | PASS |
| TBLKTNCompactEndToEndTest | PASS |
| TBLKTNDuplexEndToEndTest | PASS (559 elements, digest unchanged) |
| Sanity (condo_mid.db) | 23 PASS, 4 FAIL, 6 WARN (identical to baseline) |

### Files

| File | Action |
|------|--------|
| `src/main/java/com/bim/compiler/contract/BundleWorker.java` | MODIFIED — added OpeningInfo record + openings field to RoomEnvelope |
| `src/main/java/com/bim/compiler/library/FurnitureWorker.java` | NEW — BundleWorker adapter wrapping FurniturePlacer |
| `src/main/java/com/bim/compiler/dsl/StoreyCompiler.java` | MODIFIED — BOM branch uses FurnitureWorker + addPlacedElementsToCtx |

### What's Next (Progressive Path)

1. **Phase 118C**: WorkerRegistry + SlotDispatcher loop in StoreyCompiler
2. **Phase 118D**: Adapt remaining 7 placers to BundleWorker
3. **Phase 118E**: Spatial reservation (zero-clash guarantee)
4. **Fire compartment walls** — address 1241m² violation
5. **Furniture for interior rooms** — 256 rooms >6m² have none

---

## Session Summary — Phase 118: BundleWorker Framework + SpatialDigest

### What Was Done

**OSGi-inspired construction worker pattern + deterministic spatial testing:**

1. **`docs/BUNDLE_WORKER_FRAMEWORK.md`** — Framework vision document:
   - Construction site metaphor (workers arrive, read blueprint, do job, leave)
   - BundleWorker interface design (themeId, anchorFace, execute, reservedEnvelope)
   - RoomEnvelope, PlacementContext, PlacedElement records
   - Current vs target dispatch pattern (hardcoded → slot-dispatched)
   - Face-aligned placement contract
   - SpatialDigest concept (two birds, one stone)
   - Progressive migration path (118A→118E phases)
   - OSGi lineage mapping table

2. **`BundleWorker.java`** — Interface in `contract/` package:
   - `themeId()` — matching ad_room_slot.assembly_id
   - `anchorFace()` — BACK/FRONT/LEFT/RIGHT/TOP/BOTTOM
   - `execute(RoomEnvelope, PlacementContext)` → `List<PlacedElement>`
   - Nested records: RoomEnvelope, PlacementContext, PlacedElement

3. **`SpatialDigest.java`** — Deterministic verification:
   - SHA256 of all element bounding boxes (sorted, 1mm precision)
   - `compute(dbPath)` → 64-char hex digest
   - `computeWithReport(dbPath)` → digest + element count + class breakdown
   - `matches(dbA, dbB)` → boolean spatial identity check
   - Two birds: sizing verification + regression testing

4. **E2E tests wired** — SpatialDigest added to CondoMidEndToEndTest and TBLKTNDuplexEndToEndTest

### Golden Digests (Phase 118 Baseline)

| Building | Elements | SHA256 Digest |
|----------|----------|--------------|
| CONDO-MID | 10,516 | `18b3062356ecef701d6c80bb979f9f02668e69fa202976925b297b88405b44c6` |
| TB-LKTN-DUPLEX | 559 | `f0ff9b03dfcb3006de4ea8dbb1169b383da06b4384f8e8474e3fefd06679a180` |

Verified deterministic: two consecutive runs produce identical digest.

### Verification

| Check | Result |
|-------|--------|
| `mvn compile -q` | PASS |
| CondoMidEndToEndTest | PASS (10516 elements, digest stable) |
| SchoolEndToEndTest | PASS |
| TBLKTNEndToEndTest | PASS |
| TBLKTN2SEndToEndTest | PASS |
| TBLKTNCompactEndToEndTest | PASS |
| TBLKTNDuplexEndToEndTest | PASS (559 elements, digest stable) |
| Sanity (condo_mid.db) | 23 PASS, 4 FAIL, 6 WARN (identical to baseline) |

### Files

| File | Action |
|------|--------|
| `docs/BUNDLE_WORKER_FRAMEWORK.md` | NEW — OSGi worker vision, dispatch protocol, migration path |
| `docs/ARCHITECTURE.md` | MODIFIED — added Contract Layer + Verification Layer sections |
| `src/main/java/com/bim/compiler/contract/BundleWorker.java` | NEW — worker interface + 3 nested records |
| `src/main/java/com/bim/compiler/validation/SpatialDigest.java` | NEW — SHA256 deterministic fingerprint |
| `src/main/java/com/bim/compiler/dsl/CondoMidEndToEndTest.java` | MODIFIED — added SpatialDigest |
| `src/main/java/com/bim/compiler/dsl/TBLKTNDuplexEndToEndTest.java` | MODIFIED — added SpatialDigest |

### What's Next (Progressive Path)

1. **Phase 118B**: Adapt FurnitureBOMResolver → FurnitureWorker implementing BundleWorker
2. **Phase 118C**: WorkerRegistry + SlotDispatcher loop in StoreyCompiler
3. **Phase 118D**: Adapt remaining 7 placers to BundleWorker
4. **Phase 118E**: Spatial reservation (zero-clash guarantee)
5. **Add SpatialDigest assertions**: Lock golden digests as regression guards

---

## Session Summary — Phase 117: CatalogContract Enforcement

### What Was Done

**Java contract + validation enforcing DSL-as-Catalog-Selector principle:**

1. **`CatalogContract.java`** — interface in `contract/` package:
   - Defines the enforcement contract: every DSL reference must resolve to an existing catalog entry
   - `CatalogViolation` record: referenceType, referenceValue, table, message
   - `CatalogResult` record: violations list, resolved count, total count, coverage %
   - Javadoc documents OSGI MANIFEST analogy and separation of concerns

2. **`CatalogValidator.java`** — implementation in `validation/building/`:
   - Checks 4 reference types against component_library.db:
     - `floor_bom:` → ad_bom.bom_id
     - Room type keywords → ad_space_type.space_type_id + ad_space_type_alias
     - Door schedule entries → ad_opening_family (type='DOOR')
     - Window schedule entries → ad_opening_family (type='WINDOW')
   - Graceful degradation: if DB missing or tables don't exist, returns clean result
   - `toValidationResult()` bridges to existing `BuildingValidationResult` for chain integration

3. **ValidatorFactory updated** — CatalogValidator wired into validation chain:
   - Runs as advisory (non-blocking) BuildingValidator
   - Pre-computed from BuildingDefinition, wrapped for chain integration
   - Reports warnings for unresolved references; silent when clean

4. **ARCHITECTURE.md §1.3 strengthened** — catalog selector principle:
   - Added "Who Edits" column (Layman / Hobbyist expert / Developer)
   - Added OSGI MANIFEST analogy table
   - Added enforcement section referencing CatalogContract

### Verification

| Check | Result |
|-------|--------|
| `mvn compile -q` | PASS |
| CondoMidEndToEndTest | PASS (10516 elements, unchanged) |
| SchoolEndToEndTest | PASS |
| TBLKTNEndToEndTest | PASS |
| TBLKTN2SEndToEndTest | PASS |
| TBLKTNCompactEndToEndTest | PASS |
| TBLKTNDuplexEndToEndTest | PASS |
| Sanity (condo_mid.db) | 23 PASS, 4 FAIL, 6 WARN (identical to baseline) |
| CatalogValidator (condo) | Clean — all DSL references resolve to catalog |

### Files

| File | Action |
|------|--------|
| `src/main/java/com/bim/compiler/contract/CatalogContract.java` | NEW — interface + CatalogViolation + CatalogResult records |
| `src/main/java/com/bim/compiler/validation/building/CatalogValidator.java` | NEW — checks DSL refs against component_library.db |
| `src/main/java/com/bim/compiler/validation/building/ValidatorFactory.java` | MODIFIED — wired CatalogValidator into chain |
| `docs/ARCHITECTURE.md` | MODIFIED — §1.3 strengthened with OSGI analogy + enforcement |

### What's Next

1. **Wire `ad_room_slot` into FurnitureBOMResolver** — unified slot dispatch (PREFAB_ARCHITECTURE.md Room Slot Protocol)
2. **Duplex-exact BOM assembly set** — metadata-only reproduction of Stacked_Duplex.db
3. **Fire compartment walls** — address 1241m² violation
4. **Furniture for interior rooms** — 256 rooms >6m² have none
5. **Extend CatalogValidator** — add unit_type → ad_unit_type, wall_type → ad_wall_type checks

---

## Session Summary — Phase 116: Grammar Extraction from IFC Rosetta Stone

### What Was Done

**Reference corpus organization + metadata grammar extraction from Duplex IFC:**

1. **Reference corpus reorganized** — `reference/` directory:
   - `reference/residential/` — 4 IFC files (Duplex Arch, Duplex MEP, SampleHouse, WallElementedCase)
   - `reference/infrastructure/` — 9 PCERT IFC 4.3.2.0 certification samples
   - `reference/README.md` — corpus documentation with extraction process
   - Removed stale `prefabs/*.bim` files, moved `archive/IFC_source_files/` to `reference/`

2. **Migration 116** — `migration/migration_116_grammar_extraction.sql`:
   - `ad_wall_type` table — 8 wall types extracted from Duplex IFC geometry truth
   - `ad_wall_type_rule` table — 8 adjacency-based resolution rules (context × space → type)
   - 10 Duplex-exact opening families in `ad_opening_family` with `ad_product_dim` entries
   - BOM recipe fixes: LIVING_SET (deactivated TV/LOUNGE_CHAIR, added SOFA_B/SIDE_TABLE), BED_SET/BED_SET_MASTER (added TALL_CABINET_A/B), new BATHROOM_VANITY_SET
   - Room slot update: BATHROOM BASIN → BATHROOM_VANITY_SET

3. **Grammar extraction document** — `reference/GRAMMAR_EXTRACTION.md`:
   - Dewey Decimal classification (000-900) for building element taxonomy
   - Complete Duplex IFC inventory mapped to grammar tables (7/13 COMPLETE, 5/13 PARTIAL, 1/13 NONE = 73%)
   - MANIFEST face contracts mapped to Duplex rooms
   - 5 priority gaps identified (ceiling, floor finish, beam type, conduit, telephone)

4. **WallTypeResolver.java** — lazy singleton resolver for `ad_wall_type` + `ad_wall_type_rule`:
   - Loads 8 wall types and 8 resolution rules on first access
   - Resolves wall thickness from context (INTERIOR/EXTERIOR/PARTY/FOUNDATION) + adjacent room types
   - Wired into StoreyCompiler (interior walls) and MultiUnitCompiler (party walls, classified walls)
   - Graceful degradation: falls back to WallType enum defaults if tables missing
   - Bathroom walls now get 184mm (plumbing), kitchen/stair walls get 152mm (furring)

5. **Spatial verification tools** — `tools/spatial_checker.py` + `tools/grammar_checker.py`:
   - `spatial_checker.py`: X-ray comparison of output DB vs reference DB spatial signatures
     - Dimension fingerprinting: (category, L_mm, W_mm, H_mm) buckets, ignores names
     - Self-comparison = 100%, cross-building = shows exact overlap %
   - `grammar_checker.py`: Grammar coverage audit (reference IFC vs ad_* tables)
     - Walls: 55/57 EXACT (96%), Openings: 38/38 EXACT (100%), Furniture: 32/61 (52%)
   - Linguistic model: Corpus → Grammar → Compiler (decode → encode → construct)

### Verification

| Check | Result |
|-------|--------|
| Migration applied | 8 wall types + 8 rules + 10 openings + BOM fixes |
| `mvn compile -q` | PASS |
| CondoMidEndToEndTest | PASS (10516 elements, +2 from resolver) |
| SchoolEndToEndTest | PASS |
| TBLKTNEndToEndTest | PASS |
| TBLKTN2SEndToEndTest | PASS |
| TBLKTNCompactEndToEndTest | PASS |
| TBLKTNDuplexEndToEndTest | PASS |
| Sanity (condo_mid.db) | 23 PASS, 4 FAIL, 6 WARN (identical to baseline) |
| WallTypeResolver | Loaded 8 types, 8 rules — bathroom→184mm, kitchen→152mm |
| Grammar checker | Walls 96%, Openings 100%, Furniture 52% |
| Spatial X-ray (self) | 100% signature match |

### Files

| File | Action |
|------|--------|
| `migration/migration_116_grammar_extraction.sql` | NEW — ad_wall_type, ad_wall_type_rule, opening families, BOM fixes |
| `reference/README.md` | NEW — corpus documentation |
| `reference/GRAMMAR_EXTRACTION.md` | NEW — linguistic mapping, Dewey classification, completeness matrix |
| `reference/residential/` | NEW — 4 IFC reference files |
| `reference/infrastructure/` | NEW — 9 IFC reference files |
| `prefabs/` | DELETED — stale .bim files replaced by reference corpus |
| `src/main/java/.../library/WallTypeResolver.java` | NEW — lazy singleton, 8 types + 8 rules |
| `src/main/java/.../dsl/StoreyCompiler.java` | MODIFIED — interior walls use resolver |
| `src/main/java/.../dsl/MultiUnitCompiler.java` | MODIFIED — party/classified walls use resolver |
| `tools/spatial_checker.py` | NEW — X-ray spatial fidelity comparison |
| `tools/grammar_checker.py` | NEW — grammar coverage audit |

### What's Next

1. **Wire `ad_wall_type` into compiler** — StoreyCompiler/MultiUnitCompiler currently hardcode wall thickness; need WallTypeResolver
2. **Wire `ad_room_slot` into FurnitureBOMResolver** — unified slot dispatch (PREFAB_ARCHITECTURE.md Room Slot Protocol)
3. **Extract SampleHouse.ifc** → `database/SampleHouse.db` — validate grammar generativity on single-storey
4. **Fill grammar gaps** — ad_ceiling_type (13 elements), ad_floor_finish (wood vs tile), ad_beam_type
5. **Fire compartment walls** — address 1241m² violation
6. **Furniture for interior rooms** — 256 rooms >6m² have none

---

## Session Summary — Phase 115B: Assembly MANIFEST Tables + ManifestResolver Wiring

### What Was Done

**Database tables + Java resolver for assembly MANIFEST contracts:**

1. **Migration script** — `migration/migration_115B_assembly_manifest.sql`:
   - 3 new tables: `ad_assembly_manifest`, `ad_assembly_connector`, `ad_room_slot`
   - 35 manifest face entries for 11 BOMs (WORKSTATION_SET through SPRINKLER_PENDANT_ASSEMBLY)
   - 10 MEP connector entries for 5 BOMs (pipe/duct diameter + target system)
   - 21 room slot entries for 8 room types (priority-ordered, slot_face anchored)

2. **ManifestResolver.java** — lazy singleton, direct JDBC to component_library.db:
   - Loads all `ad_assembly_manifest` rows (version='1.0.0') into `Map<String, List<ManifestEntry>>`
   - Key method: `getClearance(assemblyId, face, defaultValue)` — CLEARANCE or WALL_BACK types
   - Graceful degradation: if table missing, catches SQLException, returns defaults

3. **FixturePlacer wired to ManifestResolver** — 4 constants → accessor methods:
   - `TOILET_SIDE_CLEARANCE` → `getToiletSideClearance()` reads LEFT face (default 0.38)
   - `TOILET_FRONT_CLEARANCE` → `getToiletFrontClearance()` reads FRONT face (default 0.533)
   - `SINK_FRONT_CLEARANCE` → `getSinkFrontClearance()` reads FRONT face (default 0.533)
   - `WALL_OFFSET` → `getWallOffset()` reads BACK face (default 0.05)
   - All 16+ usage sites updated; original constants kept as `DEFAULT_*` fallbacks
   - **NOT changed:** FurnitureBOMResolver.WALL_OFFSET (0.5 = placement inset, not code clearance)

### Verification

| Check | Result |
|-------|--------|
| Migration applied | 35 manifest + 10 connector + 21 room_slot = 66 rows |
| `mvn compile -q` | PASS |
| CondoMidEndToEndTest | PASS (10514 elements) |
| SchoolEndToEndTest | PASS |
| TBLKTNEndToEndTest | PASS |
| TBLKTN2SEndToEndTest | PASS |
| TBLKTNCompactEndToEndTest | PASS |
| TBLKTNDuplexEndToEndTest | PASS |
| Sanity (condo_mid.db) | 23 PASS, 4 FAIL, 6 WARN (identical to baseline) |
| Baseline comparison | Stashed changes, re-ran — same results → **zero behavior change** |

### Files

| File | Action |
|------|--------|
| `migration/migration_115B_assembly_manifest.sql` | NEW — 3 tables + 66 data rows |
| `src/main/java/com/bim/compiler/library/ManifestResolver.java` | NEW — lazy singleton resolver |
| `src/main/java/com/bim/compiler/library/FixturePlacer.java` | MODIFIED — 4 constants → accessor methods |

### What's Next

1. **BOM variant for 12m tower units** — units share Y-sections with core
2. **Fire compartment walls** — address 1241m² violation
3. **Furniture for interior rooms** — 256 rooms >6m² have none
4. **Wire ad_room_slot** into FurnitureBOMResolver as unified slot dispatch
5. **Remaining unit type room layouts** — 6 types still need ad_unit_type_room entries

---

## Session Summary — Phase 115A: MANIFEST Contracts & Fixture Lego Pieces

### What Was Done

**Documentation-only phase** — extended `docs/PREFAB_ARCHITECTURE.md` with bottom-up fixture contracts.

1. **Extended hierarchy to 6 levels** (was 4): Level -1 FIXTURE ARRANGEMENT through Level 4 BUILDING
2. **MANIFEST contract specification**: 6-face interface model, interface types, MEP connector types
3. **6 concrete fixture arrangements** with full MANIFEST contracts
4. **Room Slot Protocol** — unified resolution replacing 3-way split
5. **3 new database table schemas** designed (implemented in 115B)

### Files Modified

| File | Action |
|------|--------|
| `docs/PREFAB_ARCHITECTURE.md` | EXTENDED — 6-level hierarchy, MANIFEST contracts, fixture arrangements, room slots |

---

## Session Summary — Phase 114: Duplex Extraction + Record Refactoring

### What Was Done

1. **MEP component extraction** — 11 new unique components from Duplex IFC
2. **Kitchen + Bathroom BOM recipes** — KITCHEN_CABINET_SET (4 children), DUPLEX_BATHROOM_SET (4 children)
3. **Stacked_Duplex.db populated** — 1,085 elements, queryable reference
4. **BuildingSpecs.java refactored** — all 26 record types extracted from BuildingCompiler (~700 lines)
5. **Import cleanup** — 48 files updated with `import BuildingSpecs.*`

### Files

| File | Action |
|------|--------|
| `BuildingSpecs.java` | NEW — 26 record types (~700 lines) |
| `BuildingCompiler.java` | REDUCED by ~700 lines |
| `migration/migration_114_duplex_bom.sql` | NEW — kitchen/bathroom BOM recipes |
| 48 files | Import updates for BuildingSpecs |

---

## Session Summary — Phase 113: Baseline Consolidation

### What Was Done

1. **Water tank migration** — 3 FRP tanks migrated to component_library.db
2. **IFC naming convention** — `docs/IFC_NAMING_CONVENTION.md`
3. **Architectural commitment** — `docs/DSL_AS_CATALOG_SELECTOR.md`
4. **Source consolidation** — `docs/SOURCE_CONSOLIDATION.md`
5. **Cleanup** — Removed dead files

---

## Consolidated Phase History (90A–112)

| Phase | Date | Key Achievement |
|-------|------|-----------------|
| 112 | 2026-02-10 | Per-storey slab boundary, floor plate envelope, UnitInteriorResolver activated |
| 111 | 2026-02-10 | TB-LKTN-DUPLEX full compilation (2 storeys, 555 elements) |
| 110 | 2026-02-10 | Consolidation: 2 new E2E tests, orphaned check cleanup, Git LFS |
| 109B | 2026-02-10 | House design templates + variants POC (LANDED_1S, compact, duplex) |
| 108C/D | 2026-02-10 | Metadata gap closure (ad_product_dim, ad_system_type) |
| 108B | 2026-02-10 | Furniture type routing + metadata (FurnitureTypeResolver) |
| 108A | 2026-02-09 | Attempted + reverted (FurniturePlacer approach abandoned) |
| 104–107 | 2026-02-09 | Codebase simplification + completeness layers |
| 103 | 2026-02-09 | BOM prefab fixes + BuildingWriter split into sub-writers |
| 102A+B | 2026-02-09 | Infrastructure fixes + AD-driven compilation |
| 100 | 2026-02-09 | Standards resolution engine + lessons learned |
| 99 | 2026-02-09 | Structural integrity + toilet + glass facade |
| 98 | 2026-02-09 | Floor plate rethink + stall dividers |
| 97 | 2026-02-09 | Window fix + LOD400 stairs + sink fix |
| 96B | 2026-02-09 | Toilet north end + BOM-driven fixture placement |
| 96 | 2026-02-09 | Toilet relocation + roof fix |
| 95A/B | 2026-02-09 | FloorPlateBOMResolver integration |
| 94A–C | 2026-02-09 | Toilet blocks + condo corridor rework |
| 93/93B | 2026-02-08 | Furniture assembly BOM + bus factor refactoring |
| 92B/C | 2026-02-07 | LOD400 ceiling fan + full-floor MEP + unified BOM bounding boxes |
| 91 | 2026-02-07 | Full MEP set + universal furniture + FP pipe cleanup |
| 90A | 2026-02-07 | Ground floor access + MEP + NLP fixes (first "recognizably a house") |

*Detailed logs: `docs/archive/PROGRESS_ARCHIVE_phases90-112.md`, `docs/archive/PROGRESS_ARCHIVE_phases62-89.md`, `docs/archive/PROGRESS_ARCHIVE_2026-02-04.md`.*

---

## Foundation Compliance Check

| Principle | Status |
|-----------|--------|
| EXTRACT, DON'T IMAGINE | ✓ All constants traced to AD tables or code standards |
| 5-Stage DAG | ✓ Parse → Resolve → Compile → Place → Write |
| Witness System | ✓ 18/22 applicable witnesses proven |
| Configuration over Code | ✓ ManifestResolver enables runtime clearance customization |
| Deterministic | ✓ Same input → same output |

---

## Test Commands

```bash
# CONDO-MID (high-rise)
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.CondoMidEndToEndTest" -q

# School (2-storey)
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.SchoolEndToEndTest" -q

# Landed houses
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNEndToEndTest" -q
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTN2SEndToEndTest" -q
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNCompactEndToEndTest" -q
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNDuplexEndToEndTest" -q

# Sanity checker
mvn -f tools/sanity-checker/pom.xml exec:java -Dexec.mainClass="com.bim.tools.sanity.HouseSanityChecker" -Dexec.args="output/condo_mid.db" -q
```

---

## Known Issues

### 1. Compartment FAIL on typical floors
1241m² > 1000m² limit — needs fire compartment walls. Real building code issue, not a code bug.

### 2. Stacked-Duplex exhaust fan geometry
Small bathroom can't fit exhaust fan. 2.9% outlier rate (acceptable).

### 3. hall_b/master_b overlap 0.5 m²
Integer-grid solver rounds 3.7m→4m. Adjacent rooms can have slight overlaps when mapped to physical coordinates. Non-fatal.

---

## Compiled Outputs

| DSL | Output | Status |
|-----|--------|--------|
| `CONDO-MID.bim` | `condo_mid.db` | ✓ PASS |
| `Sekolah-Kebangsaan.bim` | `sekolah_kebangsaan.db` | ✓ PASS |
| `TB-LKTN` (tests) | `tb_lktn.db` | ✓ PASS |
| `TB-LKTN-2S.bim` | `tb_lktn_2s.db` | ✓ PASS |
| `TB-LKTN-COMPACT.bim` | — | ✓ PASS |
| `TB-LKTN-DUPLEX.bim` | `tb_lktn_duplex.db` | ✓ PASS |
