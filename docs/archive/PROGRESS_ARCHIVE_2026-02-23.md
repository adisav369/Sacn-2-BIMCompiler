# PROGRESS ARCHIVE — 2026-02-23 Sessions

Archived from PROGRESS.md. Historical session logs — not current state.
Current state: see PROGRESS.md.

---

## Session: ORMSandbox Preflight Enrichment

**Phase PREFLIGHT COMPLETE — 11/11 ORMSandbox tests GREEN (was 6)**

Added `preflight <buildingType>` to BuildingInspector + M_AdGeometryMap PO.
Zero DAGCompiler touch. Zero DB changes — report only.

Deliverables:
- `X_AdGeometryMap.java` + `M_AdGeometryMap.java` — typed PO for ad_geometry_map (9 cols)
- `BuildingInspector.dumpPreflight(String)` — 7 checks (A–G) returning warning count
- CLI: `java -cp ... BuildingInspector library/component_library.db preflight Ifc2x3_Duplex`
- Tests: S-ORM-7/S-ORM-8/S-ORM-8b/S-ORM-8c — all GREEN

Issues surfaced (no DB touched):
- Check F: 48 ARC ROOM_Level_* rules with FURN_ family_ref ACTIVE — root cause of X1 regression
- Check G: expected_elements=1467 in DB (PROGRESS.md said 1206 was applied — discrepancy)
- Check B: 42/42 DX rooms LOCAL_MM (G8 calibration debt)

---

## Session: Regression Investigation (2026-02-23)

Root cause: Three untracked migration files partially applied:
1. `migration_G8_DX_restore_grid_rooms.sql` — re-activated 715 DX ROOM_Level_* rules.
   DX compiled to 1206 (was 1197). expected_elements was stale.
2. `migration_G8_SH_bom_calibration.sql` — created SH-specific BOMs but forgot
   `UPDATE ad_element_rule SET is_active=1 WHERE id IN (8189, 8190, 8192)`.
   SH compiled to 56 instead of 71.
3. `migration_CONSOLIDATED_catchup_2026_02_23.sql` — column/view additions only.

Fixes applied to library DB:
- `UPDATE ad_element_rule SET is_active=1 WHERE id IN (8189, 8190, 8192)`
- `UPDATE ad_building_registry SET expected_elements=1206 WHERE building_id='Ifc2x3_Duplex'`
- `UPDATE ad_building_registry SET expected_elements=71 WHERE building_id='Ifc4_SampleHouse'`
- migration_G8_SH_bom_calibration.sql: added Step 6 activation block

BuildingRegistryTest 4/4 GREEN after fixes.

Cascade failures discovered after activation:
- X1 (duplex_noOpaqueBBoxGeometry): 48 BBox-only FURN from ARC ROOM_Level_* rules with FURN_ family_refs
- dx_s3_furnitureInEnvelope: wrong positions from ROOM_Level_* grid rooms
- dx_furnitureNotBunchedByStorey: Dining_Chair + Sofa 70.9mm apart
- sh_furnitureNotBunchedByStorey: SOFA + SOFA_B from SH_LIVING_SET placed identically
- sh_s3_diningTableFraction: DiningTable Y-fraction 0.15 outside [0.2,0.8]

---

## Session: orm-core extraction + ORMSandbox

**Phase ORM-SANDBOX COMPLETE — orm-core + ORMSandbox both GREEN**

Extracted BasePO + ModelQuery from TopologyMaker into shared `orm-core` Maven module.
Created `ORMSandbox` module with X_/M_ for all 8 key DAGCompiler tables + BuildingInspector.

orm-core/ (com.bim:orm-core):
- BasePO — shared persistent object base
- ModelQuery — shared fluent OQL builder
- root pom.xml: orm-core + ORMSandbox added to modules

TopologyMaker/ — now imports from orm-core; 15/15 tests still GREEN

ORMSandbox/ (com.bim:orm-sandbox):
- 8 X_ + 8 M_ classes for key DAGCompiler tables
- BuildingInspector — dumpBuildings, dumpElementRules, dumpRoomBoundaries, dumpBomChain,
  dumpRoomSlots, dumpProductDim; CLI main()
- BuildingInspectorTest — 6 smoke tests S-ORM-1 through S-ORM-6

Critical trap: isNew() must use explicit isNewRecord flag, not PK presence.
TEXT PKs are non-blank before save() but row not in DB → silent UPDATE (0 rows) instead of INSERT.

---

## Session: TopologyMaker PO Layer (TM-PO)

**Phase TM-PO COMPLETE — 15/15 tests GREEN (7 existing + 8 new)**

New po/ package in TopologyMaker — zero DAGCompiler touch.

Key deliverables:
- BasePO — explicit isNewRecord flag (critical trap above)
- ModelQuery — fluent OQL; SELECT alias.* for aliased tables
- X_AdTypologyPattern, X_AdRoomBoundary, X_AdBuildingRegistry — COLUMNNAME constants + typed getters
- M_AdTypologyPattern — get() factory, beforeSave() zone_json/dims validation
- M_AdRoomBoundary — fromCell() factory, DERIVED_MM guard, areaMm2()
- M_AdBuildingRegistry — completeIt() DR/IP→CO, voidIt() →VO+inactive
- TopologyWriter refactored to use M_ classes; TopologyAccessLayer delegates to M_AdTypologyPattern

---

## Session: TopologyMaker T0–T6

**TopologyMaker module COMPLETE — 7/7 tests GREEN**

New sibling Maven module (TopologyMaker/) — zero DAGCompiler source touched.

Key deliverables:
- migration_topology_maker_bootstrap.sql — ad_typology_pattern + ad_spatial_rule tables,
  TERRACE_MY_1S seed, UBBL rules, 3 wall prefab BOMs, 4 room prefab BOMs
- Records: DocStatus, SiteEnvelope, RoomCell, TopologyOrder, TopologyResult
- GridStrategy interface + StripZoneStrategy (STRIP_ZONES)
- UbblValidator — AREA + MIN_DIM checks
- TopologyAccessLayer — reads ad_typology_pattern + ad_spatial_rule
- TopologyWriter — writes ad_room_boundary (DERIVED_MM) + ad_bom/child (FLOOR+UNIT) + ad_building_registry
- TopologyBatchProcess — orchestrator

THREE-TABLE AUTHORITY compliance:
- ad_room_boundary: DERIVED_MM only
- ad_bom/ad_bom_child: FLOOR + UNIT generated per order
- Never writes to ad_element_rule or ad_product_dim

Note: migration_topology_maker_bootstrap.sql NOT YET applied to canonical DB.

---

## Session: VIEW_CONTRACTS closeout

**VIEW_CONTRACTS.md architecture locked (v1.9 → v2.0 after Phase 4a)**

DocStatus table-type mapping (by DB evidence):
- ad_element_rule → C_OrderLine: doc_status='CO'
- ad_room_boundary → spatial quantity: extracted_from + coordinate_frame gates
- ad_bom/ad_bom_child → M_BOM/M_BOMLine: is_active=1 only
- component_definitions, ad_product_dim → M_Product: extracted_from NOT LIKE '%PENDING%'

Phase 3g: TB_LKTN extracted_from set → v_verified_room_boundary: 0 → 7 rows
Phase 3h: component_definitions extracted_from='LIBRARY' (23,888 rows)
  View bug: original SQL joined gm.element_ref = cd.name (namespace mismatch → 0 rows)
  Fix: filter cd.vertex_count > 8 directly; ifc_class via correlated subquery on geometry_hash
  v_proven_geometry: 0 → 22,013 rows; v_component_leaf: 0 → 28 rows

Phase 4a: product_ref FK in ad_bom_child → v_qualified_bom: 0 → 10 rows
  RelationalResolver dim key: pf.productRef() ?: pf.namePattern() (not always namePattern)
  Previously: always namePattern → null in productDims map → bbox fallback (silent)

Six views — status at Phase 4a:
- v_qualified_bom: 10 rows (product_ref FK)
- v_verified_room_boundary: 7 rows (TB-LKTN; SH/DX = calibration debt)
- v_compilable_element_rule: 95 rows (DX:61, SH:14, TB:20)
- v_proven_geometry: 22,013 rows
- v_active_bom_assembly: 29 rows (ROOM:9, SET:20)
- v_component_leaf: 28 rows

Phase 4b–4e queued: ViewAccessLayer.java + BomTierResolver.java (spec in VIEW_CONTRACTS.md §6/§7)
