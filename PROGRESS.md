# PROGRESS — Current Development State

**Last updated:** 2026-02-23
**Tests:** DAGCompiler **4 FAILURES** (sh/dx bunching, sh fraction, dx envelope) + G8 intentional RED | TopologyMaker 15/15 | ORMSandbox 11/11
**SpatialDigests:** SH=1f325a98 DX=d3c779b9 TB=dd4345f4 Terminal=301b42b1 (stable)

---

## ⚡ IMMEDIATE — Do This First

**Step 1 — Apply Check F fix (unblocks X1, envelope, bunching failures):**
```sql
sqlite3 library/component_library.db < migration/migration_G8_DX_deactivate_furn_arc_rules.sql
```
Then run `mvn test -pl DAGCompiler` — X1, dx_s3_furnitureInEnvelope, dx_furnitureNotBunchedByStorey should turn GREEN.

**Step 2 — Resolve expected_elements discrepancy:**
- DB shows DX=**1467**, SH=**56**. After Step 1 (48 rules deactivated), recount and update:
  ```sql
  UPDATE ad_building_registry SET expected_elements=<new_count> WHERE building_id='Ifc2x3_Duplex';
  ```

**Step 3 — Fix remaining SH failures (2 tests):**
- `sh_furnitureNotBunchedByStorey`: SOFA + SOFA_B from SH_LIVING_SET placed identically (dx/dy offset issue in migration_G8_SH_bom_calibration.sql)
- `sh_s3_diningTableFraction`: DiningTable Y-fraction 0.15 outside [0.2,0.8] — SH_DINING_SET anchor miscalculation
- Use `BuildingInspector bom SH_LIVING_SET` and `bom SH_DINING_SET` to inspect current offsets.

**Step 4 — G8 calibration (intentional RED — deferred):**
- DX: 42/42 rooms LOCAL_MM → replace with 11 real IFC rooms from `Ifc2x3_Duplex_extracted.db`
- SH: 2 rooms already IFC_GLOBAL_MM; G8 failures may resolve after Step 1+3

---

## Active Regression (root-caused 2026-02-23)

**Cause:** `migration_G8_DX_restore_grid_rooms.sql` re-activated 715 DX ROOM_Level_* rules.
It correctly kept FURN rules (is_active=0) but missed 48 **ARC** rules that also carry `FURN_` family_refs.

**Effect:** 48 ARC rules with FURN_ family_refs produce BBox-only furniture via ARC path (not BOM cascade):
- X1: 48 BBox-only FURN elements (duplex_noOpaqueBBoxGeometry)
- dx_s3_furnitureInEnvelope: wrong positions from grid rooms
- dx_furnitureNotBunchedByStorey: overlapping Dining_Chair/Sofa

**Fix:** `migration_G8_DX_deactivate_furn_arc_rules.sql` (committed, not yet applied to DB)

**Lesson:** After any `is_active` restore migration, run `preflight <building>` to catch discipline mismatches before compiling.

---

## Preflight Tool (new — use before every compile)

```bash
java -cp ORMSandbox/target/... com.bim.ormsandbox.BuildingInspector \
     library/component_library.db preflight Ifc2x3_Duplex
```

| Check | Catches |
|---|---|
| A | Blank BOM leaf `child_name_pattern` — silent GEN-BOX dims |
| B | Room boundaries not `IFC_GLOBAL_MM` — G8 placement drift |
| C | Zero `height_extent_mm` / negative `height_mm` — P01/P03 CRITICAL |
| D | Non-FURN elements with no `geometry_map` entry — GEN-BOX summary |
| E | Orphaned `geometry_hash` — FK integrity |
| F | **ARC/STR rules with `FURN_` family_refs** — discipline mismatch regression |
| G | `expected_elements` vs active rule count + reachable room slots |

---

## Known Debt

| Item | Severity | Fix |
|---|---|---|
| DX: 48 FURN_ ARC rules ACTIVE | **BLOCKING** (X1, envelope, bunching) | Apply migration_G8_DX_deactivate_furn_arc_rules.sql |
| DX expected_elements=1467 (stale) | HIGH | Recount after Step 1 |
| SH: SOFA/SOFA_B bunching (0mm) | HIGH | Fix dx/dy in migration_G8_SH_bom_calibration.sql |
| SH: DiningTable Y-fraction 0.15 | HIGH | Fix anchor in SH_DINING_SET BOM children |
| G8-SH calibration (16/17 fail) | RED test (intentional) | Extract SH rooms from reference IFC |
| G8-DX calibration (139/173 fail) | RED test (intentional) | Replace LOCAL_MM grid rooms with IFC_GLOBAL_MM |
| migration_topology_maker_bootstrap.sql not applied | Medium | `sqlite3 library/component_library.db < migration/migration_topology_maker_bootstrap.sql` |
| Phase 1e: ad_room_boundary CHECK lacks DERIVED_MM | Medium | Table recreation required (SQLite cannot ALTER CHECK) |
| Terminal IfcReinforcingBar GIC failures | Advisory (8) | — |
| Duplex P23 drain corners | Advisory (364) | MEP flow fittings — expected |
| ad_bom_child fit_priority seeds | Data gap | Only COFFEE_TABLE seeded |
| Table renames (C_Element_Rule etc.) | REFACTOR session | 10 Java + 35 SQL files for ad_element_rule alone |

---

## Key Lessons (hard-won)

**Migration hygiene:**
- Every `is_active` restore must be followed by `preflight` — discipline mismatches are invisible until X1 fires.
- Incomplete migrations (forgot `is_active=1` activation) are worse than no migration — partial state is hardest to debug.
- `expected_elements` goes stale every time rules are activated/deactivated. Always recount.

**BOM dispatch vs direct geometry:**
- FURN discipline → always via BOM cascade (no direct `geometry_map` lookup)
- ARC/STR/MEP → via `geometry_map`; GEN-BOX if not found
- ARC rule with `FURN_` family_ref = wrong path = BBox-only geometry (X1 failure)

**room_boundary frames:**
- `IFC_GLOBAL_MM` = extracted from IFC, trusted for G8
- `LOCAL_MM` = artificial grid cells, NOT real room extents → G8 will fail
- `DERIVED_MM` = TopologyMaker-generated, valid for new buildings

**BasePO trap:**
- `isNew()` must use explicit `isNewRecord` flag, not PK presence. TEXT PKs are non-blank before save() but row doesn't exist yet → silent UPDATE (0 rows) instead of INSERT.

**Coordinate types (sealed, Phase BOM-2d):**
- `WorldCoord` ONLY via `LocalCoord.toWorld(StoreyCoord)` — D8 ArchUnit gate enforces.
- `computeBomAnchorForRoom` Z: uses `pf.z(), pf.z()+h` — if this reverts to `floorZ`, DX kitchen upper elements drop to floor silently.

**v_proven_geometry / v_component_leaf (Phase 3h):**
- Original view joined `ad_geometry_map ON gm.element_ref = cd.name` → zero rows (namespace mismatch: Revit family:type ≠ library name).
- Fix: filter `cd.vertex_count > 8 AND cd.geometry_hash IS NOT NULL` directly; `ifc_class` via correlated subquery.

---

## Phase History

| Phase | Status | What |
|---|---|---|
| RM-1 to RM-11 | ✅ DONE | Relational placement, registry pipeline, rotation_rule, MEP, GIC |
| BOM-1, BOM-2a/b/c/d | ✅ DONE | MRP BOM cascade, LOD geometry, Z-fix, OPPOSITE_WORK |
| DriftGuard | ✅ DONE | D1/D2/D5/D8 gates, AD Events schema, 119 tests |
| G8 Gate | ✅ DONE (2 RED intentional) | RosettaPlacementTest wired; calibration is the debt |
| Mesh2Library | ✅ DONE | HipRoof, HalfRoundDrain, GablePorch meshes wired |
| VIEW_CONTRACTS | ✅ DONE | 6 views live; v_qualified_bom = 10 rows (Phase 4a) |
| Phase 4a | ✅ DONE | product_ref FK in ad_bom_child; dim lookup fixed |
| TopologyMaker | ✅ DONE | T0–T6 + PO layer (15/15 tests) |
| orm-core + ORMSandbox | ✅ DONE | BasePO/ModelQuery shared; BuildingInspector; 11/11 tests |
| Preflight | ✅ DONE | 7 checks A–G; DX X1 regression surfaced |
| Phase 4b–4e | ⏳ QUEUED | ViewAccessLayer + BomTierResolver (spec in VIEW_CONTRACTS.md §6/§7) |
| G8 calibration | ⏳ QUEUED | Replace LOCAL_MM rooms with IFC_GLOBAL_MM for SH/DX |
| AD Events wiring | ⏳ QUEUED | SpatialRuleValidator, CalloutCascadeValidator |
| REFACTOR | ⏳ DEFERRED | Table renames (C_Element_Rule etc.) — dedicated session only |
