# DiscValidation.db SRS — Discipline Validation Database
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [ConstructionAsERP](ConstructionAsERP.md) · [TestArchitecture](TestArchitecture.md)

**Version:** 1.2 (2026-03-19) — Phase 1 DONE, Phase 2 STARTED (CalibrationDAO dual-read)
**Depends on:** [DISC_VALIDATE_SRS.md](DISC_VALIDATE_SRS.md) §9-10, [DocAction_SRS.md](DocAction_SRS.md) §1.3, [CALIBRATION_SRS.md](CALIBRATION_SRS.md)

---

## 1. Problem — Three Databases, Confused Boundaries

Current state mixes three concerns into two databases:

```
component_library.db (23,888 component_definitions + 23,901 geometries)
├── LOD concern:       M_Product, component_definitions, component_geometries  ← CORRECT
├── Discipline concern: ad_space_type_mep_bom, ad_element_mep, ad_fp_coverage  ← WRONG DB
├── Space concern:     ad_space_type, ad_wall_face, placement_rules            ← WRONG DB
└── Assembly concern:  ad_assembly_connector, ad_assembly_manifest             ← WRONG DB

validation.db (AD_Val_Rule + params)
├── Rules concern:     AD_Val_Rule, AD_Val_Rule_Param, AD_Clash_Rule           ← CORRECT
└── Results concern:   AD_Validation_Result                                     ← CORRECT
```

**Problem:** `component_library.db` is 23,901 geometry rows — it's a product
catalog. Discipline metadata (what MEP goes where, how many, what spacing,
what connects to what) is NOT product geometry. Mixing them means:

1. Querying "how many sprinklers in a bedroom?" loads 23K geometry rows into the connection
2. Updating discipline rules risks touching the LOD catalog
3. No clean separation between "what the product looks like" (LOD) and
   "where the product goes" (discipline placement)

---

## 2. Solution — DiscValidation.db (Third Database)

```
component_library.db — WHAT things look like (LOD catalog)
├── M_Product (608 products: dimensions, ifc_class)
├── component_definitions (23,888 LOD attachments)
├── component_geometries (23,901 mesh data)
└── surface_styles, material_layers

DiscValidation.db — WHERE things go + HOW they connect (discipline metadata)  ← NEW
├── Discipline schedule:  ad_space_type_mep_bom (186 rows)
├── MEP element types:    ad_element_mep (12 rows)
├── FP coverage rules:    ad_fp_coverage (4 hazard classes)
├── Space types:          ad_space_type (41 types)
├── Assembly connectors:  ad_assembly_connector (10 rows)
├── Placement rules:      placement_rules (4,801 rows)
├── Wall faces:           ad_wall_face (204 rows)
├── Space adjacency:      ad_space_adjacency
├── FP triggers:          ad_fp_trigger
├── Code requirements:    ad_code_requirement
├── IFC alias cascade:    ad_element_mep_alias (84 rows, DV003)
├── IFC class map:        ad_ifc_class_map (46 rows, DV005) — authority table for extract.py
├── Calibration results:  W_Calibration_Result (from CalibrationTest)
└── Schema config:        AD_SysConfig

validation.db — RULES + VERDICTS (compliance engine)
├── AD_Val_Rule + AD_Val_Rule_Param (thresholds)
├── AD_Clash_Rule (cross-discipline pairs)
├── AD_Occupancy_Class (occupancy classification)
└── AD_Validation_Result (pass/warn/block verdicts)
```

### 2.1 Three Databases, Three Concerns

| Database | Concern | Opened By | Read/Write | Size |
|----------|---------|-----------|------------|------|
| `component_library.db` | Product LOD (geometry, dimensions) | Compile pipeline, Designer LOD fetch | Read-only at runtime | ~5 MB (23K geometries) |
| `disc_validation.db` | Discipline metadata (schedules, types, connectors) | DocEvent engine, CalibrationTest | Read at runtime, write at seed/migrate | ~50 KB (5K rows) |
| `validation.db` | Compliance rules + verdicts | PlacementValidator, InferenceEngine | Read rules, write results | ~20 KB |

### 2.2 Reference Pointers — No LOD Copies

DiscValidation.db references component_library.db products by **name**, not
by FK or by copying LOD data:

```
disc_validation.db                          component_library.db
┌─────────────────────────┐                ┌──────────────────────┐
│ ad_element_mep          │                │ M_Product            │
│   element_type: SPRINKLER──── name ────▶│   name: SPRINKLER    │
│   ifc_class: IfcFire... │                │   width, depth, height│
│   discipline: FP        │                │   ifc_class          │
│   ports: [{"IN":0.015}] │                └──────────┬───────────┘
└─────────────────────────┘                           │
                                                      ▼
┌─────────────────────────┐                ┌──────────────────────┐
│ ad_space_type_mep_bom   │                │ component_definitions│
│   mep_product_id: SPRINKLER── name ────▶│   name LIKE '%sprink%│
│   qty_normal: 0         │                │   geometry_hash      │
│   per_area_normal: 0.07 │                │   attachment_face    │
│   placement_rule: GRID  │                └──────────┬───────────┘
└─────────────────────────┘                           │
                                                      ▼
                                           ┌──────────────────────┐
                                           │ component_geometries │
                                           │   vertices, faces    │
                                           └──────────────────────┘
```

**The join is at runtime, in Java**, not via SQL FK. When DocEvent places a
SPRINKLER, it:
1. Reads discipline metadata from `disc_validation.db` (how many, where)
2. Reads product dimensions + LOD from `component_library.db` (what it looks like)
3. The link is `ad_element_mep.element_type` = `ad_space_type_mep_bom.mep_product_id`
   → at LOD fetch time, resolves to `M_Product` via alias cascade (§5.1)

**No geometry in disc_validation.db. No discipline metadata in component_library.db.**

---

## 3. Tables — What Moves, What Stays, What's New

### 3.1 Tables Moving FROM component_library.db TO disc_validation.db

| Table | Rows | Why It Moves |
|-------|------|-------------|
| `ad_space_type_mep_bom` | 186 | Discipline schedule — not product geometry |
| `ad_element_mep` | 12 | MEP element type definitions — not LODs |
| `ad_fp_coverage` | 4 | FP hazard class thresholds — rule data |
| `ad_space_type` | 41 | Space type taxonomy — not product data |
| `ad_space_adjacency` | ~20 | Space relationship rules |
| `ad_assembly_connector` | 10 | Connection topology — not geometry |
| `ad_assembly_manifest` | ~5 | Assembly composition |
| `ad_wall_face` | 204 | Room boundary faces — spatial, not LOD |
| `placement_rules` | 4,801 | Placement strategy rules |
| `ad_fp_trigger` | ~10 | FP trigger conditions |
| `ad_code_requirement` | ~20 | Building code refs |
| `ad_room_slot` | ~50 | Room slot definitions |
| `ad_space_dim` | ~30 | Space dimension rules |
| `ad_space_exterior_rule` | ~10 | Exterior exposure rules |
| `ad_space_type_opening` | ~20 | Opening requirements per space |
| `ad_space_type_furniture` | ~20 | Furniture schedule per space |
| `ad_space_type_mep` | ~20 | MEP services per space |

### 3.2 Tables STAYING in component_library.db

| Table | Rows | Why It Stays |
|-------|------|-------------|
| `M_Product` | 608 | Product catalog — dimensions, ifc_class |
| `component_definitions` | 23,888 | LOD mesh attachments |
| `component_geometries` | 23,901 | Actual mesh data (vertices, faces, normals) |
| `surface_styles` | ~50 | Material appearance |
| `material_layers` | ~20 | Wall/slab layer composition |
| `I_Geometry_Map` | ~200 | Extraction geometry mapping |
| `M_Product_Image` | ~10 | Product thumbnails |

### 3.3 Tables STAYING in validation.db

| Table | Rows | Why It Stays |
|-------|------|-------------|
| `AD_Val_Rule` | ~30 | Compliance rule definitions |
| `AD_Val_Rule_Param` | ~100 | Rule threshold parameters |
| `AD_Clash_Rule` | ~10 | Cross-discipline clash pairs |
| `AD_Occupancy_Class` | 6 | Occupancy classifications |
| `AD_Val_Rule_Occupancy` | ~15 | Rule-occupancy links |
| `AD_Val_Rule_Exception` | ~5 | Documented exceptions |
| `AD_Val_Rule_Mining_Source` | ~15 | Mining provenance |
| `AD_Validation_Result` | writes | Runtime validation results |

### 3.4 NEW Tables in disc_validation.db

| Table | Purpose | Source |
|-------|---------|--------|
| `ad_element_mep_alias` | IFC version-agnostic product resolution (84 rows) | DV003: IFC4 spec + DX/TE mined |
| `ad_ifc_class_map` | IFC class extraction authority (46 rows): discipline, category, attachment per IFC type. Read by `extract.py` at startup — adding a new IFC type = one INSERT, zero code changes. | DV005: building + IFC4X3 infra census |
| `W_Calibration_Result` | Calibration test results (DocEvent vs Terminal) | CalibrationTest.java |
| `AD_SysConfig` | Schema version tracking | Standard |

---

## 4. Schema — disc_validation.db

**Authoritative schema:** `migration/DV001_disc_validation_schema.sql` (DDL),
`migration/DV003_element_mep_alias.sql` (alias cascade).
Schemas match the actual column layout in component_library.db source tables.

### 4.1 Table Summary (22 tables)

| Table | PK | Rows | Purpose |
|-------|----|------|---------|
| `ad_space_type` | space_type_id | 41 | Space type taxonomy (BEDROOM, OFFICE, etc.) |
| `ad_element_mep` | element_type | 12 | Canonical MEP types (OUTLET, SPRINKLER, etc.) |
| `ad_space_type_mep_bom` | (space_type_id, mep_product_id) | 186 | Discipline schedule: what MEP goes in each room |
| `ad_fp_coverage` | hazard_class | 4 | NFPA 13 sprinkler coverage thresholds |
| `ad_assembly_connector` | connector_id | 10 | Assembly connection topology |
| `ad_assembly_manifest` | manifest_id | 37 | Assembly interface definitions |
| `ad_wall_face` | id | 204 | Room boundary faces per building |
| `placement_rules` | id | 4801 | Host/offset placement rules |
| `ad_space_adjacency` | (space_type_a, space_type_b) | 22 | Room adjacency relationships |
| `ad_fp_trigger` | trigger_id | 12 | FP system trigger conditions |
| `ad_code_requirement` | (code_id, clause, element_type, space_type) | 23 | Building code requirements |
| `ad_room_slot` | slot_id | 38 | Room assembly slot definitions |
| `ad_space_dim` | space_type | 37 | Space dimension constraints |
| `ad_space_exterior_rule` | space_type_id | 24 | Exterior exposure rules |
| `ad_space_type_opening` | (space_type_id, opening_role, family_id) | 103 | Opening requirements per space |
| `ad_space_type_furniture` | space_type_id | 37 | Furniture schedule per space |
| `ad_space_type_mep` | space_type_id | 22 | MEP service requirements per space |
| `ad_element_mep_alias` | alias_id | 84 | IFC version-agnostic product resolution (§5.1) |
| `ad_ifc_class_map` | ifc_class | 46 | IFC class extraction authority — discipline, category, attachment, domain per type. `extract.py` reads at startup. See §5.2. |
| `ad_val_rule` | ad_val_rule_id | 415 | Mined dimension rules: typical W/D/H per (ifc_class, storey) from 20 buildings. DV010 migration. |
| `ad_val_rule_param` | ad_val_rule_param_id | 1245 | Rule parameters (typical_width_mm, typical_depth_mm, typical_height_mm). |
| `W_Calibration_Result` | id | 0 | CalibrationTest output (runtime writes) |
| `AD_SysConfig` | Name | 3 | Schema/seed/alias version tracking |

---

## 5. Reference Pointer Pattern — No FK Across Databases

SQLite does not support cross-database foreign keys. The reference is by
**name convention** — same pattern as iDempiere's `AD_Reference` lookups:

| disc_validation.db column | Resolves to | Resolution method |
|--------------------------|-------------|-------------------|
| `ad_element_mep.element_type` | `M_Product` by alias cascade | Java: try ifc_class → predefined_type → type_class → element_name LIKE |
| `ad_space_type_mep_bom.mep_product_id` | `ad_element_mep.element_type` | SQL within disc_validation.db (same DB) |
| `ad_assembly_connector.assembly_id` | `M_Product.name` | Java: `SELECT * FROM M_Product WHERE name = ?` |
| `placement_rules.element_name` | `M_Product.name` or `m_bom.bom_id` | Java: lookup by name |

### 5.1 IFC Version-Agnostic Resolution — `ad_element_mep_alias` (DV003)

**Problem:** IFC2x3 lumps all MEP into generic classes (IfcFlowTerminal,
IfcFlowController). IFC4 splits them into specific subtypes (IfcOutlet,
IfcSwitchingDevice). Real-world IFC files use vendor-specific naming.
Matching by `ifc_class` alone fails for 8/12 canonical types.

**Solution:** 4-tier resolution cascade using `ad_element_mep_alias`:

```
Priority 1: ifc_class        — IfcOutlet → OUTLET (IFC4 direct match)
Priority 2: predefined_type  — POWEROUTLET → OUTLET (IFC4 enum)
Priority 3: type_class       — IfcOutletType → OUTLET (IFC2x3 via IfcRelDefinesByType)
Priority 4: element_name     — %Receptacle% → OUTLET (name pattern, last resort)
```

**Java resolver pseudocode:**
```java
String resolve(String ifcClass, String predefinedType, String typeClass, String elementName) {
    // Try each priority in order — first match wins
    for (Alias a : aliases) {  // sorted by priority ASC
        if ("ifc_class".equals(a.matchField) && a.matchValue.equals(ifcClass)) return a.canonicalType;
        if ("predefined_type".equals(a.matchField) && a.matchValue.equals(predefinedType)) return a.canonicalType;
        if ("type_class".equals(a.matchField) && a.matchValue.equals(typeClass)) return a.canonicalType;
        if ("element_name".equals(a.matchField) && likeMatch(elementName, a.matchValue)) return a.canonicalType;
    }
    return null; // unresolvable
}
```

**Coverage (seeded from DX + TE reference models):**
- IFC4 class: 14 aliases (all 12 canonical types)
- PredefinedType: 24 aliases (IFC4 standard enums)
- IFC2x3 type class: 12 aliases (via IfcRelDefinesByType)
- Element name: 34 aliases (mined from DX=12, TE=22 patterns)
- DX resolution: 101/119 distinct MEP names (85%)
- Unmatched: kitchen appliances (Range, Microwave, Refrigerator), plumbing valves

**The Java DAO joins across databases.** Each method receives the connections
it needs:

```java
// DocEvent placement: reads disc_validation.db + component_library.db
void placeElements(Connection discConn, Connection compConn, ...)

// Calibration: reads disc_validation.db + validation.db + TE reference DB
void calibrate(Connection discConn, Connection valConn, Connection teConn, ...)

// LOD fetch: reads component_library.db only
Geometry fetchLOD(Connection compConn, String productName)
```

### 5.2 IFC Class Extraction Authority — `ad_ifc_class_map` (DV005)

**Problem:** `extract.py` hardcoded 4 Python dicts (REFERENCE_CLASSES, DISCIPLINE_MAP,
CATEGORY_MAP, ATTACHMENT_MAP). Adding a new IFC element type required editing Python code.
Infrastructure IFC4X3 brought 11 new types (IfcTrackElement, IfcCourse, etc.) and more
will appear as new IFC domains are encountered.

**Solution:** Authority table `ad_ifc_class_map` in disc_validation.db. `extract.py`
reads this table at startup and populates all 4 maps from it. Falls back to hardcoded
defaults if DB is unavailable.

```
ad_ifc_class_map (46 rows)
┌──────────────────────┬────────────┬─────────────────┬─────────────────┬──────────┬───────────┐
│ ifc_class (PK)       │ discipline │ category        │ attachment_face │ ifc_schema│ domain    │
├──────────────────────┼────────────┼─────────────────┼─────────────────┼──────────┼───────────┤
│ IfcTrackElement      │ RAIL       │ TRACK_ELEMENT   │ BOTTOM          │ IFC4X3   │ RAIL      │
│ IfcCourse            │ ROAD       │ PAVEMENT_LAYER  │ BOTTOM          │ IFC4X3   │ ROAD      │
│ IfcBeam              │ STR        │ BEAM            │ ENDS            │ IFC4     │ BUILDING  │
│ IfcLightFixture      │ ELEC       │ LIGHT           │ TOP             │ IFC4     │ BUILDING  │
│ ...                  │ ...        │ ...             │ ...             │ ...      │ ...       │
└──────────────────────┴────────────┴─────────────────┴─────────────────┴──────────┴───────────┘
```

**Adding a new IFC type:**
```sql
INSERT INTO ad_ifc_class_map
    (ifc_class, discipline, category, attachment_face, ifc_schema, domain, description)
VALUES
    ('IfcCableCarrierSegment', 'ELEC', 'CABLE_TRAY', 'BOTTOM', 'IFC4', 'BUILDING', 'Cable tray');
```

Zero code changes. Same data-not-code pattern as AD_Val_Rule.

**Columns:**

| Column | Type | Purpose |
|--------|------|---------|
| `ifc_class` | TEXT PK | IFC entity type name |
| `discipline` | TEXT | Extraction discipline: ARC, STR, MEP, FP, ELEC, ACMV, ROAD, RAIL, GEO, LAND, SIGN |
| `category` | TEXT | Component library category: BEAM, SPRINKLER, TRACK_ELEMENT, etc. |
| `attachment_face` | TEXT | Placement attachment: TOP, BOTTOM, SIDE, ENDS, CENTER |
| `ifc_schema` | TEXT | Schema version: IFC2X3, IFC4, IFC4X3 |
| `domain` | TEXT | Domain: BUILDING, ROAD, BRIDGE, RAIL, LANDSCAPE, MEP |
| `is_active` | INTEGER | Toggle without deleting (1=active, 0=disabled) |
| `description` | TEXT | Human-readable description |

**Migration:** `DV005_ifc_class_map.sql`. See [`InfrastructureAnalysis.md`](InfrastructureAnalysis.md) §3.3.

---

## 6. Migration Plan — Phased, Non-Destructive

### Phase 1: Create disc_validation.db (DV001+DV002+DV003+DV005) — DONE (session 33-34)
1. `DV001_disc_validation_schema.sql` — 19 tables matching component_library.db schemas
2. `DV002_seed_from_component.sql` — ATTACH + INSERT OR IGNORE (17 tables, 5613 rows)
3. `DV003_element_mep_alias.sql` — IFC version-agnostic alias cascade (84 rows)
4. `DV005_ifc_class_map.sql` — IFC class extraction authority (46 rows, building + infra)
4. `DiscValidationDBTest.java` — 12/12 witnesses pass (SCHEMA, SEED, REF, ALIAS, ND)

### Phase 2–3: COMPLETE (sessions 36b–41)

All discipline metadata migrated. Java code (CalibrationDAO, MEPAD, MEPBOMResolver,
ManifestResolver) reads from disc_validation.db. component_library.db reduced from
81→21 tables. See [`database/DATABASE_SCHEMA.md`](../database/DATABASE_SCHEMA.md)
for the current table inventory.

---

## 7. Connection Map — Who Opens What

### Current (4 DBs, Phase 3 complete)
```
CompilationPipeline     → component_library.db (LOD only — 21 tables)
PlacementValidator      → validation.db (rules)
CalibrationDAO          → disc_validation.db + validation.db + TE_BOM.db
MEPAD/MEPBOMResolver    → disc_validation.db (discipline metadata)
ManifestResolver        → disc_validation.db (discipline metadata)
DocEvent (future)       → disc_validation.db (schedules) + component_library.db (LOD fetch)
Handler cascade H1-H6  → disc_validation.db (connectors, schedules) + validation.db (rules)
```

### Connection parameter naming convention
```java
Connection compConn;   // component_library.db — LOD catalog
Connection discConn;   // disc_validation.db   — discipline metadata
Connection valConn;    // validation.db         — compliance rules
Connection bomConn;    // {prefix}_BOM.db       — building BOM
Connection workConn;   // work_output.db        — design workspace
Connection teConn;     // TE reference DB       — Terminal oracle (tests only)
```

---

## 8. File Location

```
library/
├── component_library.db     ← LOD catalog (M_Product, geometries)
├── disc_validation.db       ← NEW: discipline metadata (schedules, types, connectors)
├── validation.db            ← compliance rules (AD_Val_Rule)
├── work_*.db                ← per-building design workspaces
├── SH_BOM.db                ← Sample House BOM
├── DX_BOM.db                ← Duplex BOM
└── TE_BOM.db                ← Terminal BOM

migration/
├── DV001_disc_validation_schema.sql    ← schema DDL (19 tables)
├── DV002_seed_from_component.sql       ← seed via ATTACH (17 tables)
├── DV003_element_mep_alias.sql         ← IFC alias cascade (84 rows)
├── V001..V006                          ← validation.db migrations
```

---

## 9. Traceability

| Witness | What it Proves | Test |
|---------|---------------|------|
| W-DV-DB-SCHEMA | DV001+DV003 creates all 20 required tables | DiscValidationDBTest |
| W-DV-DB-SEED | Seed data matches component_library.db source counts | DiscValidationDBTest |
| W-DV-DB-REF | Reference pointers resolve across databases | DiscValidationDBTest |
| W-DV-DB-ALIAS | Alias cascade resolves IFC2x3↔IFC4 (84 rows, 4 tiers) | DiscValidationDBTest |
| W-DV-DB-ND | Migration does not disturb component_library.db | DiscValidationDBTest |

---

*References:
[DISC_VALIDATE_SRS.md](DISC_VALIDATE_SRS.md) §9 (5-table LOD chain) |
[DocAction_SRS.md](DocAction_SRS.md) §1.3 (processIt DocEvent) |
[CALIBRATION_SRS.md](CALIBRATION_SRS.md) (DocEvent vs Terminal) |
[G4_SRS.md](G4_SRS.md) §2 (work_output.db pattern)*
