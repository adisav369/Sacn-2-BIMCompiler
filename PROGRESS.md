# PROGRESS — Current Development State

**Last updated:** 2026-02-11
**Current phase:** Phase 115B completed — Assembly MANIFEST tables + ManifestResolver wiring
**Baseline:** 23/33 sanity (4 FAIL, 6 WARN), 6 E2E PASS, 10514 condo elements

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
