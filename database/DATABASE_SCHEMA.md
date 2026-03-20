# BIM Intent Compiler — Database Schema Reference

> **Foundation:** [BBC](../docs/BOMBasedCompilation.md) · [DATA_MODEL](../docs/DATA_MODEL.md) · [ConstructionAsERP](../docs/ConstructionAsERP.md) · [DISC_VALIDATION_DB_SRS](../docs/DISC_VALIDATION_DB_SRS.md)

**Version:** 1.0 | **Date:** 2026-03-20
**Scope:** Complete table inventory across all 4 databases with purpose, Java access, and review status.

**Interactive ERD:** [`bim_architecture_viz.html`](bim_architecture_viz.html) — clickable 4-DB architecture, compilation pipeline, BOM tree
**Live browser:** Datasette at `http://localhost:8001/` (see [Systems Installer Guide §4.3](../docs/SYSTEMS_INSTALLER_GUIDE.md))

---

## 1. component_library.db — LOD Catalog (21 tables, ~214 MB)

Read-only geometry oracle. Populated by IFC extraction (Python + IfcOpenShell). Java never writes during compilation.

### Core Product Tables

| Table | Rows | Purpose | Key Java Access | Status |
|-------|------|---------|-----------------|--------|
| **M_Product** | 800 | Master product catalog: ifc_class, dims, cost, carbon, lifecycle | ProductRegistrar (W), BOMWalker, DesignerDAO, CostDAO (R) | ACTIVE — 30+ files |
| **M_Product_Image** | 616 | Product → geometry_hash linkage (1:1), orientation axes | ProductRegistrar (W), ProductGeometry, MeshBinder (R) | ACTIVE — 9 files |
| **component_geometries** | 23,900 | Mesh BLOBs (vertices, faces, normals) keyed by geometry_hash | ExtractionPopulator (W), ProductGeometry, DoorWindowLibraryMapper (R) | ACTIVE — 13 files |
| **component_definitions** | ~600 | LOD500 catalog: local AABB, attachment face, orientation, rotation | ComponentLibrary, MeshBinder, DoorWindowLibraryMapper (R) | ACTIVE — 12 files |
| **component_types** | ~50 | IFC class → category → discipline mapping | ComponentLibrary, DoorWindowLibraryMapper (R) | ACTIVE — 5 files |
| **I_Geometry_Map** | 49,415 | Element reference → geometry_hash mapping per building/storey | ExtractionPopulator (W), MeshBinder, BuildingWriter (R) | ACTIVE — 12 files |

### Material Tables

| Table | Rows | Purpose | Key Java Access | Status |
|-------|------|---------|-----------------|--------|
| **material_layers** | ~200 | Wall/slab material layer stacks (set_name, thickness, sequence, role) | AssemblyDAO, BuildingWriter (R) | ACTIVE — 7 files |
| **ad_material_thermal** | ~40 | Thermal conductivity per material (W/mK) for U-value calculation | AssemblyDAO.getConductivity() (R) | ACTIVE — 3 files |
| **surface_styles** | ~100 | Material surface RGB appearance | BuildingWriter.copySurfaceStyles() (R) | ACTIVE — 5 files |

### Spatial Reference Tables

| Table | Rows | Purpose | Key Java Access | Status |
|-------|------|---------|-----------------|--------|
| **ad_room_boundary** | varies | Spatial room definitions: min/max x/y/z_mm, room_type, is_active | TopologyWriter (W), CompilationPipeline, PlacementProver, verb classes (R) | **HIGHLY ACTIVE** — 30+ files |
| **ad_building_grid** | varies | Building grid cells with mm coordinates | MetadataValidator, MetadataIntegrityTest (R) | ACTIVE — 7 files |
| **ad_building** | 4 | Building definitions (FK parent for grid, assertions, room_boundary) | FK reference only — no direct SELECT | STRUCTURAL REF |
| **ad_building_assertions** | 3 | Per-building constraint assertions (assertion_sql) | BuildingRegistry.loadAssertions() (R) | ACTIVE — 2 files |
| **ad_product_dim** | varies | Furniture/product dimensions (**units: METRES, not mm**) | BOMTierResolver, QualifiedBom (R) | ACTIVE — 10 files |
| **ad_opening_family** | varies | Door/window family definitions (family_id, default dims) | MetadataValidator, CatalogValidator (R) | ACTIVE — 7 files |
| **ad_fire_compartment** | varies | Fire compartment rules (requires_detection, max_area_m2) | StandardsResolver, FireProtectionAD (R) | ACTIVE — 3 files |
| **ad_covering_type** | varies | Ceiling tile/covering definitions (name, thickness_mm) | CoveringWriter (R) | ACTIVE — 1 file |
| **placement_rules** | 4,801 | Component placement grid specs (spacing, clearance) | ComponentLibrary.getPlacementRules() (R) | ACTIVE — 2 files |
| **ad_check_applicability** | varies | Check metadata registry (check_id, ifc_class, context) | SanityCheckAD (R) | ACTIVE — 4 files |
| **ad_check_threshold** | varies | Numeric check thresholds (property, value, unit) | SanityCheckAD (R) | ACTIVE — 2 files |

### Review Items

| Table | Rows | Purpose | Status |
|-------|------|---------|--------|
| **sqlite_sequence** | auto | SQLite AUTOINCREMENT tracking | System table — auto-managed |

---

## 2. disc_validation.db — Discipline Metadata (20 tables, ~1 MB)

Migration-seeded, read-only at runtime. Populated by `migration/DV*.sql` scripts. All discipline metadata migrated here from component_library.db in session 41 (Phase 2b+3).

### MEP Element Tables

| Table | Rows | Purpose | Key Java Access | Status |
|-------|------|---------|-----------------|--------|
| **ad_element_mep** | varies | MEP element definitions (host_type, mount_height, clearance, ports, dims) | MEPAD.getElement(), MEPAD.getElementsByDiscipline() (R) | ACTIVE — 2 files |
| **ad_element_mep_alias** | 84 | IFC version-agnostic alias cascade (match by ifc_class, predefined_type, name) | DiscValidationDBTest (R) | ACTIVE — 1 file (test) |
| **ad_space_type_mep_bom** | varies | Space → MEP product BOM (qty per area, placement rule, building code) | CalibrationDAO.docEventQty(), MEPBOMResolver (R) | ACTIVE — 5 files |
| **ad_space_type_mep** | varies | Space type → MEP element density rules | SpaceTypeAD.querySpaceType() LEFT JOIN (R) | ACTIVE — 1 file |

### Fire Protection Tables

| Table | Rows | Purpose | Key Java Access | Status |
|-------|------|---------|-----------------|--------|
| **ad_fp_coverage** | varies | Sprinkler coverage rules: max_coverage_m², spacing, k_factor (NFPA 13) | MEPAD.getSprinklerCoverage() (R) | ACTIVE — 6 files |
| **ad_fp_trigger** | varies | Fire protection trigger conditions per jurisdiction | FireProtectionAD.getTriggersFor() (R) | ACTIVE — 6 files |

### Space Type Tables

| Table | Rows | Purpose | Key Java Access | Status |
|-------|------|---------|-----------------|--------|
| **ad_space_type** | varies | Space classification (office, corridor, etc.) with code requirements | M_AdSpaceType.listActive(), SpaceTypeAD (R) | ACTIVE — 11 files |
| **ad_space_dim** | varies | Space dimension contracts (min area, min dimension, proportions) | SpaceDimResolver.loadContracts() (R) | ACTIVE — 2 files |
| **ad_space_adjacency** | varies | Adjacency rules between space types | DiscValidationDBTest seed check (R) | ACTIVE — 1 file (test only) |
| **ad_space_exterior_rule** | varies | Exterior wall requirements per space type | ExteriorRuleAD.ensureLoaded() (R) | ACTIVE — 1 file |
| **ad_space_type_opening** | varies | Door/window count rules per space type | OpeningBomAD, SpaceContractCheck (R) | ACTIVE — 2 files |

### Assembly & Placement Tables

| Table | Rows | Purpose | Key Java Access | Status |
|-------|------|---------|-----------------|--------|
| **ad_assembly_manifest** | varies | Assembly version manifests (face, interface_type, clearance_m) | ManifestResolver.getClearance() (R) | ACTIVE — 2 files |
| **ad_assembly_connector** | varies | Assembly face connectors (position, diameter, connects_to) | DiscValidationDBTest seed check (R) | ACTIVE — 1 file (test only) |
| **ad_wall_face** | varies | Wall face placement rules per building/room | M_AdWallFace.getByBuilding(), MetadataValidator (R) | ACTIVE — 3 files |
| **ad_room_slot** | varies | Room slot dispatch (furniture, prefab, assembly assignments) | SlotRegistry.getSlotsForType() (R) | ACTIVE — 6 files |
| **ad_code_requirement** | varies | Building code requirements per space type | MEPBomAD, SpaceContractCheck (R) | ACTIVE — 2 files |
| **placement_rules** | varies | Discipline placement rules (spacing, offsets) — shared with component_library | ComponentLibrary.getPlacementRules() (R) | ACTIVE — 2 files |

### System Tables

| Table | Rows | Purpose | Key Java Access | Status |
|-------|------|---------|-----------------|--------|
| **AD_SysConfig** | 2 | Schema version + seed version tracking | DiscValidationDBTest.schemaVersionCorrect() (R) | ACTIVE — metadata |
| **W_Calibration_Result** | varies | Calibration test results (predicted vs actual counts) | CalibrationTest (W), WorkOutputDAO (W) | ACTIVE — 2 files |

### Review Items

| Table | Status | Notes |
|-------|--------|-------|
| **ad_ifc_class_map** (46 rows) | Used by Python `tools/extract.py` only — 0 Java references | Seeded by DV005 migration. Python extraction pipeline reads at startup (`_load_ifc_class_map()`). Keep for now. |
| **ad_space_adjacency** | LOW USE — only referenced in seed count test | No runtime Java queries. May be reserved for future adjacency validation. |
| **ad_assembly_connector** | LOW USE — only referenced in seed count test | No runtime Java queries. Used by assembly builder (future). |

---

## 3. {PREFIX}_BOM.db — Per-Building BOM (6-7 tables)

One per building (SH, DX, TE, BR, RD, RL, IN, DM). Built by IFCtoBOM pipeline. Read-only at compile time.

| Table | Purpose | Key Java Access | Status |
|-------|---------|-----------------|--------|
| **m_bom** | BOM headers: building, storey, discipline groupings (BUILDING→FLOOR→ROOM→SET→ITEM) | BOMBuilder, BOMWalker, DesignerDAO, verb classes (R/W) | ACTIVE |
| **m_bom_line** | BOM lines: one per element with dx/dy/dz tack offsets, verb_formula, allocated dims | BOMWalker, AddLineVerb, SetDimensionsVerb, MeshBinder (R/W) | ACTIVE |
| **m_bom_line_ma** | Material allocation instances per BOM line | VerbFactorizer, PlacementCollectorVisitor (R/W) | ACTIVE |
| **M_Product** | Product snapshot (transitional copy for BOMWalker — target: read from library only) | ProductRegistrar (W), MeshBinder (R) | TRANSITIONAL |
| **C_DocType** | Document type definitions (doc_base_type, doc_sub_type, building dims) | DesignerDAO, BuildingWriter, IFCtoBOMPipeline (R) | ACTIVE |
| **ad_sysconfig** | Per-building configuration key-value pairs | CompilerConfig.load() (R) | ACTIVE |

---

## 4. output.db / work_output.db — Compilation & Design Output

Written fresh each compile. Schema created from `output_template.db`.

### Design State (work_output.db)

| Table | Purpose | Key Java Access |
|-------|---------|-----------------|
| **C_Order** | Order header: WHAT building to build (doc_status lifecycle DR→IP→CO→AP) | WorkOutputDAO (R/W) |
| **C_OrderLine** | Per-element placement (product, qty, x/y/z position) | WorkOutputDAO.save/recall (R/W) |
| **W_Variant** | Design snapshots (cheap saves, many per session) | WorkOutputDAO.listVariants (R/W) |
| **PP_Order_Node** | HOW: verb invocation audit trail (verb_name, params, results) | VerbNodePersister (W), M_PP_Order_Node (R) |
| **bim_changelog** | WHO changed WHAT: action, entity, old→new, user, timestamp | ChangelogDAO (R/W) |

### Compiled Output (output.db)

| Table | Purpose | Key Java Access |
|-------|---------|-----------------|
| **elements_meta** | Core element catalog (GUID, discipline, IFC class, storey, material) | ElementPersistence (W), SpatialStructureBuilder (R) |
| **elements_rtree** | R-tree spatial index (minX/maxX, minY/maxY, minZ/maxZ) | ElementPersistence (W), containment queries (R) |
| **base_geometries** | Deduplicated geometry cache (content-hash keyed) | ElementPersistence (W) |
| **element_instances** | Element → geometry linkage | ElementPersistence (W) |
| **element_transforms** | Element placement (center_x/y/z) | ElementPersistence (W) |
| **element_assemblies** | Assembly headers (type, dims) | AssemblyStructureVisitor (R/W) |
| **assembly_components** | Assembly child components (role, local offsets) | AssemblyStructureVisitor (R/W) |
| **spatial_structure** | IFC spatial hierarchy (IfcBuilding → IfcBuildingStorey → IfcSpace) | SpatialStructureBuilder (R/W) |
| **rel_contained_in_space** | Element → space containment relationships | SpatialStructureBuilder (W) |
| **mep_systems** | MEP system headers | MEPWriter (W) |
| **system_nodes** | MEP equipment nodes | MEPWriter (W) |
| **system_edges** | MEP connection edges | MEPWriter (W) |
| **element_properties** | IFC property sets (pset_name, property_name, value) | Sub-writers (W) |
| **surface_styles** | Material styles (copied from component_library.db) | BuildingWriter (W) |
| **material_layers** | Material layers (copied from component_library.db) | BuildingWriter (W) |
| **simple_qto** | Quantity takeoff aggregation | Aggregation verbs (W) |

**Views:** `room_areas`, `area_by_storey`, `area_by_type`, `building_summary` — computed from spatial_structure + elements_rtree.

---

## 5. Staleness & Review Summary

### Stale References Fixed (session 41)

| File | Issue | Fix |
|------|-------|-----|
| BIM_COBOL.md:197 | Said ROUTE reads from {PREFIX}_BOM.db | Fixed: reads component_library.db + disc_validation.db |
| ConstructionAsERP.md:2737,3092 | "3-DB separation/split" | Fixed: "4-DB" |
| INDEX.md:12 | "3-DB architecture" | Fixed: "4-DB" |
| SourceCodeGuide.md:606 | "3-DB schema" | Fixed: "4-DB" |
| BIM_Designer.md:1313 | "3-DB architecture" | Fixed: "4-DB" |
| TerminalAnalysis.md:1400 | "3-DB architecture" | Fixed: "4-DB" |
| DISC_VALIDATION_DB_SRS.md §6 | Phase 2b/3 marked as "future" | Fixed: all phases marked DONE |
| DATA_MODEL.md §0.2 | Said tables "currently exist in BOTH" | Fixed: migration complete note |

### Tables Dropped (session 41)

| Table | Database | Reason |
|-------|----------|--------|
| ad_building_registry | component_library.db | 0 Java references — orphaned schema |
| ad_geometry_map | component_library.db | Renamed to I_Geometry_Map — old name was orphan |
| ad_space_type_furniture | disc_validation.db | Only in archived FurnitureTypeResolver — deprecated |

### Remaining Low-Use Tables

| Table | Database | Status |
|-------|----------|--------|
| **ad_ifc_class_map** | disc_validation.db | Used by Python `tools/extract.py` only — keep |
| **ad_space_adjacency** | disc_validation.db | Test seed only — reserved for future adjacency validation |
| **ad_assembly_connector** | disc_validation.db | Test seed only — assembly builder feature in progress |
| **M_Product (BOM copy)** | {PREFIX}_BOM.db | Transitional — target: read from library only |

---

*Schema snapshots: [`schema_snapshot_component_library.sql`](schema_snapshot_component_library.sql) · [`schema_snapshot_component_library_before_cleanup.sql`](schema_snapshot_component_library_before_cleanup.sql)*
