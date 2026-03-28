# ERP.db SRS — Discipline Validation Database
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md)

<div class="bim-banner" markdown>
<b>ERP.db holds discipline metadata separate from products and BOMs.</b> Schedules, placement rules, alias cascades, and mined dimension rules — the HOW concern of the 5-DB split.
</div>

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

## 2. Solution — ERP.db (Third Database)

```
component_library.db — WHAT things look like (LOD catalog)
├── M_Product (608 products: dimensions, ifc_class)
├── component_definitions (23,888 LOD attachments)
├── component_geometries (23,901 mesh data)
└── surface_styles, material_layers

ERP.db — WHERE things go + HOW they connect (discipline metadata)
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
├── Schema config:        AD_SysConfig
├── Shared recipes:       M_BOM (1 FP_SYSTEM, DV025) — §10.4.6
└── Recipe lines:         M_BOM_Line (3 FP children, DV025)

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
| `ERP.db` | Discipline metadata (schedules, types, connectors) | DocEvent engine, CalibrationTest | Read at runtime, write at seed/migrate | ~50 KB (5K rows) |
| `validation.db` | Compliance rules + verdicts | PlacementValidator, InferenceEngine | Read rules, write results | ~20 KB |

### 2.2 Reference Pointers — No LOD Copies

ERP.db references component_library.db products by **name**, not
by FK or by copying LOD data:

```
ERP.db                          component_library.db
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
1. Reads discipline metadata from `ERP.db` (how many, where)
2. Reads product dimensions + LOD from `component_library.db` (what it looks like)
3. The link is `ad_element_mep.element_type` = `ad_space_type_mep_bom.mep_product_id`
   → at LOD fetch time, resolves to `M_Product` via alias cascade (§5.1)

**No geometry in ERP.db. No discipline metadata in component_library.db.**

---

## 3. Tables — What Moves, What Stays, What's New

### 3.1 Tables Moving FROM component_library.db TO ERP.db

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

### 3.4 NEW Tables in ERP.db

| Table | Purpose | Source |
|-------|---------|--------|
| `ad_element_mep_alias` | IFC version-agnostic product resolution (84 rows) | DV003: IFC4 spec + DX/TE mined |
| `ad_ifc_class_map` | IFC class extraction authority (46 rows): discipline, category, attachment per IFC type. Read by `extract.py` at startup — adding a new IFC type = one INSERT, zero code changes. | DV005: building + IFC4X3 infra census |
| `W_Calibration_Result` | Calibration test results (DocEvent vs Terminal) | CalibrationTest.java |
| `AD_SysConfig` | Schema version tracking | Standard |

---

## 4. Schema — ERP.db

**Authoritative schema:** `migration/DV001_ERP_schema.sql` (DDL),
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

| ERP.db column | Resolves to | Resolution method |
|--------------------------|-------------|-------------------|
| `ad_element_mep.element_type` | `M_Product` by alias cascade | Java: try ifc_class → predefined_type → type_class → element_name LIKE |
| `ad_space_type_mep_bom.mep_product_id` | `ad_element_mep.element_type` | SQL within ERP.db (same DB) |
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
// DocEvent placement: reads ERP.db + component_library.db
void placeElements(Connection discConn, Connection compConn, ...)

// Calibration: reads ERP.db + validation.db + TE reference DB
void calibrate(Connection discConn, Connection valConn, Connection teConn, ...)

// LOD fetch: reads component_library.db only
Geometry fetchLOD(Connection compConn, String productName)
```

### 5.2 IFC Class Extraction Authority — `ad_ifc_class_map` (DV005)

**Problem:** `extract.py` hardcoded 4 Python dicts (REFERENCE_CLASSES, DISCIPLINE_MAP,
CATEGORY_MAP, ATTACHMENT_MAP). Adding a new IFC element type required editing Python code.
Infrastructure IFC4X3 brought 11 new types (IfcTrackElement, IfcCourse, etc.) and more
will appear as new IFC domains are encountered.

**Solution:** Authority table `ad_ifc_class_map` in ERP.db. `extract.py`
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

### Phase 1: Create ERP.db (DV001+DV002+DV003+DV005) — DONE (session 33-34)
1. `DV001_ERP_schema.sql` — 19 tables matching component_library.db schemas
2. `DV002_seed_from_component.sql` — ATTACH + INSERT OR IGNORE (17 tables, 5613 rows)
3. `DV003_element_mep_alias.sql` — IFC version-agnostic alias cascade (84 rows)
4. `DV005_ifc_class_map.sql` — IFC class extraction authority (46 rows, building + infra)
4. `DiscValidationDBTest.java` — 12/12 witnesses pass (SCHEMA, SEED, REF, ALIAS, ND)

### Phase 2–3: COMPLETE (sessions 36b–41)

All discipline metadata migrated. Java code (CalibrationDAO, MEPAD, MEPBOMResolver,
ManifestResolver) reads from ERP.db. component_library.db reduced from
81→21 tables. See [`database/DATABASE_SCHEMA.md`](https://github.com/red1oon/BIMCompiler/blob/master/database/DATABASE_SCHEMA.md)
for the current table inventory.

---

## 7. Connection Map — Who Opens What

### Current (4 DBs, Phase 3 complete)
```
CompilationPipeline     → component_library.db (LOD only — 21 tables)
PlacementValidator      → validation.db (rules)
CalibrationDAO          → ERP.db + validation.db + TE_BOM.db
MEPAD/MEPBOMResolver    → ERP.db (discipline metadata)
ManifestResolver        → ERP.db (discipline metadata)
DocEvent (future)       → ERP.db (schedules) + component_library.db (LOD fetch)
Handler cascade H1-H6  → ERP.db (connectors, schedules) + validation.db (rules)
```

### Connection parameter naming convention
```java
Connection compConn;   // component_library.db — LOD catalog
Connection discConn;   // ERP.db   — discipline metadata
Connection valConn;    // validation.db         — compliance rules
Connection bomConn;    // {prefix}_BOM.db       — building BOM
Connection outConn;    // output.db              — compile output
Connection teConn;     // TE reference DB       — Terminal oracle (tests only)
```

---

## 8. File Location

```
library/
├── component_library.db     ← LOD catalog (M_Product, geometries)
├── ERP.db       ← NEW: discipline metadata (schedules, types, connectors)
├── validation.db            ← compliance rules (AD_Val_Rule)
├── work_*.db                ← per-building design workspaces
├── SH_BOM.db                ← Sample House BOM
├── DX_BOM.db                ← Duplex BOM
└── TE_BOM.db                ← Terminal BOM

migration/
├── DV001_ERP_schema.sql    ← schema DDL (19 tables)
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

---

## 10. Open Question — Application Dictionary Database (S62)

> **Status:** INVESTIGATE. Raised during S62 FP trial when M_Product_Category was
> added to component_library.db. Another session found M_Product rows dropped to 19
> after schema alignment — suggesting the DB boundaries need clarification.

### 10.1 Problem — M_Product Is Master Data, Not Geometry

`component_library.db` currently holds two unrelated concerns:

1. **Geometry catalog** — component_definitions (23,888), component_geometries (23,901),
   surface_styles, material_layers. This is LOD data: what things look like.
2. **Master data** — M_Product (product dimensions, IFC class), M_Product_Category
   (discipline hierarchy), 66 AD tables (ad_space_type, ad_wall_face, etc. — see §11.6.7).
   This is ERP configuration: what things are and where they belong.

In iDempiere, M_Product and all AD tables live in the central application database.
Geometry is an attachment, not co-located. Mixing them means:

- Schema changes to AD tables (e.g., adding M_Product_Category) risk disturbing
  23K geometry rows
- Querying "what products exist in FP category?" opens a 221MB geometry connection
- Migration scripts that target master data must be careful not to touch geometry
- DiscValidationDBTest.componentLibraryUndisturbed check fails when AD tables change

### 10.2 Options

**Option A: Split component_library.db** — Move M_Product, M_Product_Category, and
all 34 AD tables to ERP.db (renamed to `ad_dictionary.db`). Keep
component_library.db as pure geometry (component_definitions + component_geometries +
surface_styles + material_layers). The runtime join by name (§2.2) already supports this.

**Option B: Expand ERP.db** — Same as A but keep the ERP.db name.
Add M_Product + M_Product_Category there. component_library.db becomes geometry-only.

**Option C: Keep current split, fix the guard** — Leave M_Product in component_library.db
but update DiscValidationDBTest to expect schema evolution (M_Product_Category column,
product count changes). Accept the mixed concern.

### 10.3 Decision Criteria

- Which option minimizes code changes? (How many Java files open component_library.db
  to read M_Product vs component_definitions?)
- Which option aligns with iDempiere AD pattern? (M_Product belongs with AD tables)
- Which option avoids breaking the 5-table LOD chain (§9.1)?
- Does the BOM DB need M_Product? (m_bom_line.child_product_id resolves to M_Product)

### 10.4 AD_Org — Disciplines as Organizational Units

iDempiere uses AD_Org to partition data by organizational unit. In construction,
disciplines ARE organizational units — each is a trade with its own contractor,
products, rules, and scope of work.

```
AD_Client = 'BIM_PROJECT' (tenant — the whole project)
└── AD_Org = '*'    (shared data: building grid, space types, structural frame)
└── AD_Org = 'ARC'  (architectural: doors, windows, furniture, finishes)
└── AD_Org = 'STR'  (structural: beams, columns, slabs, foundations)
└── AD_Org = 'FP'   (fire protection: sprinklers, alarms, risers)
└── AD_Org = 'ELEC' (electrical: lights, outlets, switches, cable trays)
└── AD_Org = 'ACMV' (HVAC: ducts, diffusers, AHUs)
└── AD_Org = 'CW'   (cold water: pipes, fittings, valves)
└── AD_Org = 'SP'   (sanitary/plumbing: fixtures, waste pipes)
└── AD_Org = 'LPG'  (gas: gas pipes, meters)
```

**Two orthogonal axes:**
- `M_Product_Category` = taxonomy (WHAT type: sprinkler head, pipe segment, alarm)
- `AD_Org` = ownership (WHO manages: FP trade, ELEC trade)

**What AD_Org replaces:**
- `m_bom.bom_category` string → `AD_Org_ID` FK
- `C_OrderLine.Discipline` string → `AD_Org_ID` FK
- `component_types.discipline` string → `AD_Org_ID` FK
- Scattered `resolveDiscipline(ifcClass)` logic → single FK lookup

**iDempiere data partitioning:** Every row with `AD_Org_ID = 'FP'` is visible
only to the FP trade. Shared infrastructure (`AD_Org = '*'`) is visible to all.
This enables per-discipline BOM views, validation scoping, and trade-specific
product catalogs — all from a single FK.

### 10.4.1 Spatial Model — Space + Occupant + Verb + Rule

> *A discipline is a contractor with a checklist, not a room with walls.*

**TE finding (S99):** `DisciplineBomBuilder` created DISCIPLINE SET BOMs as
spatial containers between FLOOR and LEAF. This forced each discipline into
its own AABB — but disciplines are not spatial containers. A fire protection
pipe network spans the entire floor. Result: 471 tack overflows, 36
unbalanced BOMs. Root cause: discipline modelled as a tree level instead of
a line attribute. See [TerminalAnalysis.md §Compilation Status](TerminalAnalysis.md#te-compilation-status--honesty-report-s99-2026-03-27).

The BOM hierarchy is recursive and abstract:

```
SPACE (M_Product, IsBOM=Y)
  └── OCCUPANT line (M_BOM_Line, with AD_Org_ID + verb_ref)
```

A SPACE has an AABB (extent) and an M_Product_Category (what kind of space).
An OCCUPANT has an AD_Org_ID (who), a verb_ref (how), and AD_Val_Rule (checklist).

The compiler resolves placement through three stages, matching how
iDempiere processes documents:

```
┌─────────────────────────────────────────────────────────────┐
│ 1st: DocEvent per Org (discipline blanket + govt standards)  │
│   AD_DocEvent_Rule fires top-down as the walker traverses    │
│   root → leaf. AD_Org blanket-applies ALL rules for the      │
│   discipline — spacing, connectivity, host, AND government   │
│   standards (NFPA 13, UBBL). Jurisdiction-swappable here.    │
│   Same as iDempiere ModelValidator per organization.          │
│                                                              │
│ 2nd: AttributeSet (per-product / per-instance)               │
│   M_AttributeSet defines what CAN vary per product type.     │
│   M_AttributeSetInstance carries actual values per instance.  │
│   Resolved per line item — K-factor, dimensions, material.   │
│                                                              │
│ 3rd: AD_Val_Rule (user per-line override — last)             │
│   User sees exploded sub-lines, adds/changes/waives rules.   │
│   Same as iDempiere AD_Val_Rule — a lookup filter the user   │
│   attaches to specific lines. Not automatic. Not blanket.    │
└─────────────────────────────────────────────────────────────┘

for each BOM line in parent:
    verb    = line.verb_ref                → Strategy (GoF)
    org     = child.product.AD_Org_ID      → 1st: DocEvent blanket + standards
    asi     = orderline.ASI                → 2nd: per-instance attributes
    verb.place(child, parent.space, asi)
    // 3rd: AD_Val_Rule — only if user attached override to this line
```

This is standard iDempiere processing order: ModelValidator (Org-scoped)
→ line item resolution (ASI) → user validation rules (AD_Val_Rule).

**Anti-pattern: `shouldSkip()`.** There is ONE compile path, not two paths
with a skip. The walker always walks the BOM tree. The verb determines what
happens at each line — PLACE emits at tack offset, ROUTE generates from
rules, FRAME generates structural grid. A `shouldSkip()` that produces an
empty BuildingSpec and falls through to a separate emit path is the same
structural cheat as `if ("CO".equals(...))` — just checking verbs instead
of category. The fix is one walker, verb-dispatched, no skip.

No `if ("CO".equals(...))`. No `if (discipline == "FP")`. Behaviour from
metadata, not from code — same as iDempiere's DocAction pattern.

**Covering vs Inside — two spatial relationships, both just BOM lines:**

| Relationship | Verb family | Example |
|-------------|-------------|---------|
| **INSIDE** | PLACE | Sofa at (dx,dy,dz) in living room |
| **COVERING** | ROUTE, TILE, FRAME, WIRE | Sprinklers covering a floor per NFPA 13 |

INSIDE: child sits AT a point within the parent space (tack offset = position).
COVERING: child SPANS the parent space (verb determines pattern, rule determines density).

### 10.4.2 Discipline Profiles — Abstract Recipe, Space-Dependent Placement

Each discipline has a **recipe** (BOM cascade from its top-level Category)
and **Org defaults** (discipline-wide practice). The parent space determines
quantity and placement. Government standards validate the result post-hoc.

| AD_Org | Top Category | Verb | Spatial | Recipe cascade |
|--------|-------------|------|---------|----------------|
| ARC (1) | ARC_DESIGN | PLACE, TILE | INSIDE | Walls, doors, windows, plates, furniture |
| STR (2) | STR_FRAME | FRAME | COVERING | Column + Beam + Slab |
| FP (3) | FP_MAIN_ROOM | ROUTE | COVERING | Riser → branches → fittings → heads |
| ELEC (4) | ELEC_DISTRIBUTION | WIRE | COVERING | Panel → circuits → fixtures |
| ACMV (5) | ACMV_PLANT | ROUTE | COVERING | AHU → ducts → fittings → terminals |
| CW (6) | CW_SUPPLY | ROUTE | COVERING | Riser → pipe runs → fittings → valves |
| SP (7) | SP_DRAINAGE | ROUTE | COVERING | Stack → drainage pipes → fixtures |
| LPG (8) | LPG_SUPPLY | ROUTE | COVERING | Meter → gas piping → fittings |

**OrderLine entry point:** `C_OrderLine.Product` has a Category (the top
Category of that discipline). BomDrop explodes the product's BOM, cascading
through the discipline's own BOM tree. Category at each tier = the product
group (substitution shelf). The designer can swap any product for another
in the same Category without changing the BOM structure.

The recipe is abstract — "cover this zone with sprinklers." Processing
follows iDempiere order: DocEvent per Org (1st, discipline blanket +
government standards) → ASI resolution per instance (2nd) → AD_Val_Rule
user override on specific lines (3rd, on demand).

### 10.4.3 Three-Stage Validation — iDempiere Processing Order

| Stage | iDempiere parallel | What it does | Fires | Example |
|-------|-------------------|-------------|-------|---------|
| **1st: DocEvent per Org** | ModelValidator.docValidate() | Blanket discipline rules INCLUDING government standards, top-down | Automatically during BOM walk, per AD_Org | FP: NFPA 13 spacing, UBBL fire rating, general pipe sizing rules |
| **2nd: AttributeSet** | M_AttributeSetInstance | Per-product/per-instance resolution | Per line item during placement | K-factor=5.6, pipe_dia=50mm, material=copper |
| **3rd: AD_Val_Rule** | AD_Val_Rule (lookup filter) | User-initiated per-line rule addition or override | On demand, after explosion, on specific sub-lines | User adds stricter spacing rule to a particular FP branch |
| **Cross-discipline** | AD_Clash_Rule | Clearance between disciplines | After all disciplines placed | 150mm clearance FP vs ELEC |

**Key distinction:** In iDempiere, AD_Val_Rule is a **lookup filter** — it
narrows available choices per field, not a document validator. ModelValidator
(DocEvent) is where real validation lives. Government standards (NFPA 13,
UBBL, MS1183) are general enough to be 1st-stage blanket rules — they apply
to EVERY element in the discipline. 3rd-stage AD_Val_Rule is for when the
user sees specific exploded sub-lines and wants to add, change, or waive a
rule on THOSE lines.

```
1st:  DocEvent(org)          → blanket discipline rules + government standards
2nd:  ASI resolution         → per-instance attributes on each line
3rd:  AD_Val_Rule            → user adds/changes/waives rule on specific lines
```

**Jurisdiction-swappable:** Government standards live in 1st-stage DocEvent
rules, scoped by `jurisdiction`. Same BOM, same Org, different jurisdiction
→ different DocEvent rules fire. Malaysian building uses UBBL rules,
US building uses NFPA/IBC rules. The BOM and ASI don't change.

#### AD_DocEvent_Rule — 1st Stage Schema (ERP.db)

DocEvent rules are shared across all buildings (like AD_Org, M_Product).
They live in ERP.db. Each rule is scoped to an AD_Org (discipline) and
optionally to a jurisdiction.

```sql
-- Ready-made discipline event rules in ERP.db
-- Fires automatically during BOM walk when AD_Org matches
CREATE TABLE IF NOT EXISTS AD_DocEvent_Rule (
    ad_docevent_rule_id  INTEGER PRIMARY KEY AUTOINCREMENT,
    ad_org_id            INTEGER NOT NULL DEFAULT 0, -- 0=all, 3=FP, 4=ELEC, etc.
    name                 TEXT NOT NULL,               -- 'NFPA13_LH_SPACING'
    description          TEXT,
    rule_type            TEXT NOT NULL,               -- SPACING, CONNECTIVITY, HOST,
                                                      -- COMPLETENESS, DIMENSION, STANDARD
    standard_ref         TEXT,                         -- 'NFPA 13 §8.6.2.2.1', 'UBBL s.43(1)'
    jurisdiction         TEXT,                         -- MY, US, UK, SG, INTL, NULL=universal
    check_method         TEXT NOT NULL,                -- MIN_DISTANCE, MAX_DISTANCE,
                                                      -- REQUIRED_HOST, COUNT_PER_AREA,
                                                      -- MIN_DIMENSION, MAX_COVERAGE, DIMENSION_RANGE
    ifc_class            TEXT,                         -- target element (NULL=all in discipline)
    m_product_category_id TEXT,                        -- target category (NULL=all)
    severity             TEXT NOT NULL DEFAULT 'WARN', -- BLOCK, WARN
    firing_event         TEXT NOT NULL DEFAULT 'BEFORE_PLACE',
                                                      -- BEFORE_PLACE, AFTER_PLACE, AFTER_COMPLETE
    is_active            INTEGER NOT NULL DEFAULT 1,
    provenance           TEXT,                         -- EXTRACTED:UBBL_1984, MINED:TE, RESEARCHED
    FOREIGN KEY (ad_org_id) REFERENCES AD_Org(ad_org_id)
);

CREATE TABLE IF NOT EXISTS AD_DocEvent_Rule_Param (
    ad_docevent_rule_param_id INTEGER PRIMARY KEY AUTOINCREMENT,
    ad_docevent_rule_id       INTEGER NOT NULL,
    name                      TEXT NOT NULL,           -- max_spacing_mm, min_area_m2, min_count
    value                     TEXT NOT NULL,           -- '4600', '9.3', '1'
    value_type                TEXT DEFAULT 'NUM',      -- NUM, TEXT, BOOL
    condition_expr            TEXT,                    -- 'productCategory IN (BD,LR,DR)'
    FOREIGN KEY (ad_docevent_rule_id) REFERENCES AD_DocEvent_Rule(ad_docevent_rule_id)
);

CREATE INDEX idx_docevent_rule_org ON AD_DocEvent_Rule(ad_org_id);
CREATE INDEX idx_docevent_rule_jurisdiction ON AD_DocEvent_Rule(jurisdiction);
```

**Existing ERP.db `ad_val_rule` (415 rows):** These are mined DIMENSION_RANGE
observations from 34 buildings — they are effectively 1st-stage DocEvent rules.
Migration path: copy qualifying rows into AD_DocEvent_Rule with
`rule_type='DIMENSION'`, `check_method='DIMENSION_RANGE'`,
`provenance='MINED:{building}'`.

#### AD_Val_Rule — 3rd Stage (validation.db, unchanged)

The V001 schema in validation.db stays as-is. Its purpose changes:

- **Before (wrong):** Government standards, post-hoc compliance
- **After (correct):** User-initiated per-line rule addition/change/waiver

The user opens an exploded order, sees specific sub-lines, and attaches:
- **ADD:** "Apply stricter 3000mm spacing to THIS branch" (new rule on line)
- **CHANGE:** "Override threshold from 4600mm to 3800mm for THIS zone"
- **WAIVE:** "Acknowledge and accept this deviation" (AD_Val_Rule_Exception)

This is exactly how iDempiere's AD_Val_Rule works — a lookup filter that
the user configures on a specific field/line to narrow or adjust what's valid.

### 10.4.4 Impact on the BOM Tree

**Before (wrong):** `BUILDING → FLOOR → DISCIPLINE SET → LEAF` — discipline
as tree level with own AABB → 471 tack overflows.

**After (correct):** `BUILDING → FLOOR → LEAF` — same depth as SH/DX.
Tack is `element.minX - floor.minX`. Always positive. Discipline resolves
from the child product, not from the line: `m_bom_line.child_product_id →
M_Product → M_Product_Category → AD_Org_ID`. This is standard iDempiere —
every record carries AD_Org, the line is just a relationship.

| Component | Change |
|-----------|--------|
| `DisciplineBomBuilder` | Remove DISCIPLINE SET BOM. LEAF lines directly under FLOOR |
| `BomValidator` | W-TACK-1/W-BUFFER-1 check FLOOR→LEAF, not SET→LEAF |
| `CompilationPipeline` | Delete CO skip hack (line 352-354) |
| `PlacementCollectorVisitor` | Resolve AD_Org_ID from child product, not discipline stack |

### 10.4.5 The BOM Is Already Perfect

A BOM is a BOM — same as manufacturing a car. You don't label an engine
assembly with a tier tag. It's a product with children. The tree
structure IS the hierarchy:

- **Root:** `getParentBOM()` returns null
- **Any level:** `getChildren()` returns its children
- **Leaf:** `getChildren()` returns empty
- **Category:** `getProductCategory()` returns the substitution shelf

No level labels. No vocabulary. A building, a car, a bridge, a ship —
same three methods, different products. M_Product_Category groups
interchangeable products at each level (same shelf = same swap pool).

**VIEW_CONTRACTS.md `v_qualified_bom`** currently uses a legacy `bom_type`
bind parameter. Migration pending to use M_Product_Category instead.

### 10.4.6 Shared Discipline Recipes in ERP.db

Discipline BOMs are **shared across all buildings.** FP is FP — one recipe,
all buildings, same rules. ACMV is ACMV. The recipe does not change; the
space and rules determine the result.

**ERP.db** holds the shared recipes alongside AD_Org:

```
ERP.db
├── AD_Org                    (WHO: FP, ACMV, ELEC, CW, SP, LPG)
├── AD_SysConfig              (discipline-wide defaults per Org)
├── M_Product                 (WHAT: sprinklers, pipes, ducts, fittings)
├── M_Product_Category        (TIER: product taxonomy = substitution shelf)
└── M_BOM — shared discipline recipes (each a BOM cascade):
    ├── FP_SYSTEM             (Category=FP_MAIN_ROOM, Org=FP)
    │   ├── FP_RISER          (Category=FP_RISER, verb=ROUTE)
    │   ├── FP_SPRINKLER_LAYOUT (Category=FP_DISTRIBUTION, verb=ROUTE)
    │   └── FP_PUMP_LINK      (Category=FP_SUPPLY, verb=ROUTE)
    ├── ACMV_SYSTEM           (Category=ACMV_PLANT, Org=ACMV)
    ├── ELEC_SYSTEM           (Category=ELEC_DISTRIBUTION, Org=ELEC)
    └── CW_SYSTEM             (Category=CW_SUPPLY, Org=CW)

validation.db (separate — government standards only)
├── AD_Val_Rule               (NFPA 13, UBBL, MS1183 — post-hoc checks)
├── AD_Val_Rule_Param         (thresholds per rule)
└── AD_Clash_Rule             (cross-discipline clearance)
```

component_library.db is strictly **leaf geometry** (meshes, LODs). It never
holds BOMs or recipes — only what things look like, not how they assemble.

**OrderLine → top Category → BOM cascade:**

```
C_Order: "Build TE"
├── C_OrderLine #1: TE_ARC_STR     (Category=ARC, Org=ARC)  ← extracted BOM
├── C_OrderLine #2: FP_SYSTEM      (Category=FP_MAIN_ROOM, Org=FP)
├── C_OrderLine #3: ACMV_SYSTEM    (Category=ACMV_PLANT, Org=ACMV)
├── C_OrderLine #4: ELEC_SYSTEM    (Category=ELEC_DISTRIBUTION, Org=ELEC)
├── C_OrderLine #5: SP_SYSTEM      (Category=SP_DRAINAGE, Org=SP)
├── C_OrderLine #6: CW_SYSTEM      (Category=CW_SUPPLY, Org=CW)
├── C_OrderLine #7: LPG_SYSTEM     (Category=LPG_SUPPLY, Org=LPG)
└── C_OrderLine #8: STR_SYSTEM     (Category=STR_FRAME, Org=STR)
```

Eight lines. Each line's Product has a top Category — the entry point into
that discipline's BOM cascade. BomDrop explodes recursively. At each tier,
Category = the substitution shelf (designer can swap products within it).

**Processing order (same as iDempiere document processing):**
1. **DocEvent per Org** — discipline blanket rules + government standards (NFPA 13, UBBL, MS1183) apply top-down as walker traverses root→leaf. Jurisdiction-swappable at this stage.
2. **ASI resolution** — per-product/per-instance attributes (K-factor, dimensions, capacity)
3. **AD_Val_Rule** — user-initiated per-line override/addition on specific exploded sub-lines

Jurisdiction-swappable: same BOM, same Org, different AD_DocEvent_Rule set
for Malaysian (UBBL) vs US (NFPA) code. BOM and ASI don't change.

For **extracted buildings** (TE, DX), the designer already applied these
recipes manually. The extraction captured the result. The compiler emits
at tack offsets (PLACE verb). For **generative buildings**, the compiler
applies the shared recipes using verb Strategy + DocEvent per Org + ASI.
AD_Val_Rule validates the output.

### 10.4.7 GoF Design Patterns

| Pattern | Application |
|---------|-------------|
| **Composite** | BOM tree: SPACE contains OCCUPANT lines, recursively |
| **Visitor** | BOMWalker visits each line |
| **Strategy** | Verb determines placement method (DocEvent + ASI) |
| **Specification** | AD_DocEvent_Rule validates during walk (1st, blanket + standards). AD_Val_Rule = user override (3rd, per-line) |

### 10.4.8 Cross-References

| Building | Disciplines | Concern |
|----------|-------------|---------|
| **TE** | 8 (ARC,STR,FP,ACMV,ELEC,CW,SP,LPG) | SET as tree level → tack overflow. [TerminalAnalysis.md](TerminalAnalysis.md) |
| **DM** | 3 (ARC,STR,FP) | First FP trial — addDiscipline(). [DemoHouseAnalysis.md](DemoHouseAnalysis.md) |
| **FK** | 2 (ARC,STR) + ROOF debate | [FZKHausAnalysis.md](FZKHausAnalysis.md) |
| **Infrastructure** | ROAD,RAIL,GEO,LAND,SIGN | Extended codes. [InfrastructureAnalysis.md](InfrastructureAnalysis.md) |

### 10.4.9 Stair Validation Rules — Candidate AD Table Extensions (S100-p84)

Existing infrastructure: `ad_stair_requirement` (7 rows in BOM.db, seeded by
`scripts/create_ad_vertical_circulation.py`), `VerticalCirculationAD.java`,
`VerticalCirculationValidator.java`, `StairwellCheck.java`.

**Rules NOT yet in `ad_stair_requirement` — candidates for addition:**

| Rule ID | Parameter | Value | Standard |
|---------|-----------|-------|----------|
| STAIR_COMFORT_2RG | 2R+G check | 550-700mm (ideal 630) | Blondel formula |
| STAIR_HEADROOM | min_headroom_mm | 2000 | UBBL practice / BS 5395 |
| STAIR_MAX_FLIGHT | max_flight_rise_mm | 3000 | UBBL By-Law 168 |
| STAIR_RISER_UNIFORM | max_variance_mm | 9.5 | IBC s1011.5.4 |
| STAIR_GUARD_HEIGHT | min_guard_mm | 1070 | IBC s1015.3 |
| STAIR_GUARD_OPENING | max_sphere_mm | 100 | IBC s1015.4 (child safety) |
| STAIR_NOSING_MAX | max_nosing_mm | 32 | IBC s1011.5.5 |
| STAIR_PRESSURIZE | pressure_pa | 50-100 | UBBL By-Law 178 (>18m) |

**TE relevance:** 178 unfactored stair components across GF-L4. Building height
59.8m → >18m threshold → 2.0hr fire rating, min 1200mm width, pressurization
required, min 2 stairs. These rules + ASI (per-instance run length, landing width)
resolve stair geometry without manual pattern recognition. See
[TerminalAnalysis.md §Stair Validation Rules](TerminalAnalysis.md).

### 10.5 Investigation Tasks

1. Count Java files that read M_Product from component_library.db vs ERP.db
2. Count Java files that read component_definitions from component_library.db
3. Map the M_Product→component_definitions join path (is it by name? by FK?)
4. Check if BOM databases carry their own M_Product (TE_BOM.db has M_Product with
   different schema — 28 columns vs 9 in component_library.db)
5. Audit all `bom_category` / `discipline` string usage — candidates for AD_Org FK
6. Propose the split and migration plan (including AD_Org table placement)

---

## 11. Investigation Report — §10.5 Tasks 1–6 (S64, 2026-03-23)

> **Status:** COMPLETE (investigation). No code changes. Findings ready for implementation review.
> **Method:** Grep + read of all Java source, SQL migrations, schema snapshots.
> **Cross-referenced against:** AUDIT_S51_FOCUSED.md Appendix F, MANIFESTO.md, BBC.md §2.

---

### 11.1 Task 1 — Java Files Reading M_Product by Database

**85 Java files** reference M_Product. Breakdown by connection:

| Connection | Database | Files | Key Readers |
|------------|----------|-------|-------------|
| `compConn` | component_library.db | ~20 | BOMWalker, OrderLineWalker, ProductRegistrar, 4 BackOffice DAOs, ProductGeometry, MetadataValidator |
| `bomConn` | {PREFIX}_BOM.db | ~8 | PlacementCollectorVisitor (fallback dims), BomValidator (counts), BomDropper (FK ref only) |
| `conn` (PO layer) | any DB with M_Product | ~12 | MProduct.java, X_MProduct.java, X_M_BOM.java, X_M_BOMLine.java |
| Test files | mixed | ~45 | DataIntegrityTest, MetadataIntegrityTest, DiscValidationDBTest, Tier1Test, etc. |

**Key finding:** Zero files read M_Product from ERP.db. The discipline DB has no
M_Product table. All master product reads go through component_library.db (compConn).

**Column read patterns by purpose:**

| Purpose | Columns | Connection | Files |
|---------|---------|------------|-------|
| Identity | product_id, product_type, ifc_class, is_active | compConn | BOMWalker, OrderLineWalker, ProductRegistrar |
| Dimensions | width, depth, height (METRES, not mm) | compConn/bomConn | PlacementCollectorVisitor, MetadataValidator |
| 5D Cost | unit_cost_rm, currency_code, cost_source, cost_uom | compConn | CostDAO |
| 4D Schedule | construction_phase, construction_sequence, labor_resource, crew_size, productivity_rate | compConn | ScheduleDAO |
| 6D Sustainability | carbon_kg_per_unit, recyclability, eol_strategy | compConn | SustainabilityDAO |
| 7D Facility Mgmt | maintenance_schedule, warranty_period, replacement_cost | compConn | FacilityMgmtDAO |
| Geometry link | M_Product_ID (from M_Product_Image) | compConn | ProductGeometry, ComponentLibrary |

**Dead code alert:** ProductRegistrar.ensureProducts() still copies M_Product from
component_library.db to BOM DB, but BOMWalker was refactored (R7, S36) to read only
from compConn. The BOM DB copy is no longer read by production code.

---

### 11.2 Task 2 — Java Files Reading component_definitions / component_geometries

**18 files** reference component_definitions or component_geometries. All read from
component_library.db exclusively.

| File | What It Reads | Also Reads M_Product? |
|------|--------------|----------------------|
| ComponentLibrary.java | component_definitions: geometry_hash, local bounds, attachment_face, vertex/face counts | No |
| DoorWindowLibraryMapper.java | component_definitions: name, geometry_hash, local bounds, forward_axis via JOIN component_types | No |
| StairLibraryMapper.java | component_definitions: geometry_hash, local bounds via JOIN component_types | No |
| StandardsResolver.java | component_definitions: hardcoded geometry_hash lookups (FP components) | No |
| ExtractionPopulator.java | component_geometries: geometry_hash, vertices, faces (write path) | No |
| ProductGeometry.java | M_Product_Image JOIN component_geometries ON geometry_hash | Yes — via M_Product_Image.M_Product_ID |
| MetadataValidator.java | I_Geometry_Map + component_geometries (referential integrity check) | Yes — checks M_Product dimensions |
| MeshBinder.java | component_geometries indirectly via ComponentLibrary.resolveByProduct() | No |
| MEPWriter.java | component_geometries indirectly via DoorWindowLibraryMapper | No |

**Key finding:** M_Product and component_definitions are accessed by **different code paths**.
Only ProductGeometry.java and MetadataValidator.java touch both — and they join through
M_Product_Image, not directly. This confirms the tables can live in separate databases
with zero SQL JOIN impact.

---

### 11.3 Task 3 — M_Product → component_definitions Join Path

**The join is INDIRECT through M_Product_Image:**

```
M_Product.product_id
    → M_Product_Image.M_Product_ID  (name match)
    → M_Product_Image.geometry_hash
    → component_geometries.geometry_hash  (PK)
```

component_definitions is a **parallel path**, not part of the M_Product chain:

```
component_types.id
    → component_definitions.type_id  (FK)
    → component_definitions.geometry_hash
    → component_geometries.geometry_hash  (PK)
```

**No direct SQL JOIN between M_Product and component_definitions exists anywhere in
the codebase.** X_MProduct.java defines a `component_id` column (logical FK →
component_definitions) but it is **vestigial** — never populated or queried.

**Production resolution code (ProductGeometry.java:69-79):**
```java
SELECT mpi.M_Product_ID, mpi.geometry_hash, mpi.up_axis, mpi.forward_axis,
       mpi.attachment_face, cg.vertex_count, cg.face_count
FROM M_Product_Image mpi
JOIN component_geometries cg ON mpi.geometry_hash = cg.geometry_hash
```

**Implication for split:** M_Product can move to a different database without breaking
any JOIN. The runtime link (M_Product_Image → component_geometries) stays in
component_library.db alongside the geometry. M_Product_Image.M_Product_ID is a text
key resolved in Java, not a SQL FK.

---

### 11.4 Task 4 — BOM Database M_Product Schema

**YES: BOM databases carry their own M_Product tables.** Schema differs from component_library.db.

| Aspect | BOM M_Product | Component Library M_Product |
|--------|---------------|-----------------------------|
| Columns | 29 | 27 |
| Rows | 7–348 (varies by building) | ~2,472 (master catalog) |
| Has clearance rules | YES (clear_front/back/left/right/above/below) | NO |
| Has fitting rules | YES (fits_in, requires_host, host_min_width/height) | NO |
| Has qty rules | YES (qty_per_area, qty_per_room, qty_per_person, max_spacing) | NO |
| Has ERP columns | NO | YES (unit_cost, labor_*, carbon_*, maintenance_*) |
| Has conn_points | YES | NO |
| Populated by | schema_snapshot_bom.sql (DDL only) | ProductRegistrar (IFCtoBOM pipeline) |

**Schema mismatch is significant.** BOM M_Product has placement/fitting rules (LEGO
connection semantics). Component Library M_Product has ERP/lifecycle columns (4D–7D).
These are two different concerns wearing the same table name.

**BOM M_Product is effectively unused by production code.** After R7 refactor (S36),
BOMWalker reads M_Product from compConn (component_library.db), not bomConn. The BOM
copy exists for backward compatibility of deprecated single-arg constructors.

**m_bom_line → M_Product resolution (structural, not FK):**
1. `m_bom_line.child_product_id` → try as `m_bom.bom_id` → sub-assembly (recurse)
2. Else → `MProduct.get(compConn, childProductId)` → leaf product from component_library.db
3. Else → dangling reference (warn + skip)

---

### 11.5 Task 5 — Discipline String Usage Audit

**All discipline identifiers are currently TEXT/String.** No AD_Org FK exists anywhere.

**9 discipline codes** (from enhanced_federation_GI.db, defined in Discipline.java enum):
ARC (35338 elements), FP (6884), REB (2660), ACMV (1621), CW (1431), STR (1429),
ELEC (1172), SP (979), LPG (209).

**Discipline columns across the schema:**

| Table.Column | DB | Type | Current Value | AD_Org candidate? |
|-------------|-----|------|---------------|-------------------|
| C_OrderLine.Discipline | compile DB | TEXT DEFAULT 'ARC' | String literal | YES — primary |
| component_types.discipline | component_library.db | TEXT NOT NULL | 'ELEC', 'ACMV', 'FP' | YES |
| m_bom.bom_category | BOM DB | TEXT | 'RF', 'STR', 'FP', 'MEP' | YES (proxy for discipline) |
| AD_Val_Rule.discipline | validation.db | TEXT nullable | 'FPR', 'ELC', 'PLB' | YES |
| AD_Clash_Rule.discipline_a/b | validation.db | TEXT NOT NULL | 'ELC' vs 'PLB' | YES |
| ad_ifc_class_map.discipline | ERP.db | TEXT | 'ARC','STR','FP','ELEC','ACMV' etc. | YES |
| ad_element_mep.discipline | ERP.db | TEXT | 'FP','ELEC','ACMV','CW','SP' | YES |
| bad_discipline_priority.higher/lower | component_library.db | TEXT NOT NULL | Priority pairs | YES |

**Java code patterns:**

| Pattern | Files | Current Type |
|---------|-------|-------------|
| `getDiscipline()` / `setDiscipline(String)` | X_C_OrderLine.java | String getter/setter |
| `Discipline.fromString(s)` | Discipline.java | Enum conversion |
| `disciplineStack.push(productCategory)` | PlacementCollectorVisitor.java | Deque\<String\> |
| `rs.getString("discipline")` | FederatedDBReader, MEPAD, OrderLineWalker | Raw string from DB |
| `rs.getString("bom_category")` | SustainabilityDAO, FacilityMgmtDAO | Proxy discipline |
| TypeDisciplineMapping (static) | TypeDisciplineMapping.java | EnumMap (in-memory, not DB) |

**W003_orderline_discipline.sql backfill logic:**
```sql
UPDATE C_OrderLine SET Discipline = CASE
    WHEN bom_category IN ('RF', 'STR', 'SL') THEN 'STR'
    WHEN bom_category = 'FP' THEN 'FPR'
    WHEN bom_category IN ('MEP', 'ELEC', 'PLB', 'ACMV') THEN bom_category
    ELSE 'ARC'
END;
```

**Inconsistency:** validation.db uses 'FPR'/'ELC'/'PLB' (3-char codes) while
everywhere else uses 'FP'/'ELEC'/'SP' (variable-length). AD_Org would unify this.

---

### 11.6 Task 6 — Proposed Split and AD_Org Migration Plan

#### 11.6.1 Decision: Option A — Three Clean Databases

Based on investigation findings, **Option A** is confirmed as the right answer:

```
component_library.db  — WHAT things look like (geometry-only, 7 tables)
├── component_types           (taxonomy of geometry families)
├── component_definitions     (LOD mesh metadata: bounds, attachment)
├── component_geometries      (vertices, faces, normals — bulk data)
├── surface_styles            (material appearance)
├── material_layers           (wall/slab layer composition)
├── I_Geometry_Map            (extraction geometry mapping)
└── M_Product_Image           (product → geometry_hash link)

ERP.db  — WHERE things go + WHO owns them (AD Dictionary, ~30 tables)
├── AD_Org                    (NEW: discipline org units — 10 rows)
├── AD_Org_Type               (NEW: org type enum — DISCIPLINE, SHARED)
├── M_Product                 (MOVED: product master data — 2,472 rows)
├── M_Product_Category        (MOVED: product taxonomy — 46 rows)
├── ad_space_type             (existing: 41 space types)
├── ad_element_mep            (existing: 12 MEP types)
├── ad_space_type_mep_bom     (existing: 186 schedule rows)
├── ad_ifc_class_map          (existing: 46 IFC class authority)
├── ad_element_mep_alias      (existing: 84 alias rows)
├── placement_rules           (existing: 4,801 rules)
├── ad_wall_face              (existing: 204 faces)
├── component_types           (SHARED: discipline column → AD_Org_ID)
├── ... (remaining 15+ AD tables already in ERP.db)
├── bad_discipline_priority   (MOVED from component_library.db)
└── AD_SysConfig              (existing: version tracking)

validation.db  — RULES + VERDICTS (compliance engine, unchanged)
├── AD_Val_Rule + params      (existing)
├── AD_Clash_Rule             (existing)
└── AD_Validation_Result      (existing)
```

#### 11.6.2 AD_Org Table Design

```sql
CREATE TABLE AD_Org (
    AD_Org_ID     INTEGER PRIMARY KEY,
    AD_Client_ID  INTEGER NOT NULL DEFAULT 1,     -- BIM_PROJECT tenant
    Value         TEXT    NOT NULL UNIQUE,          -- 'ARC', 'STR', 'FP', etc.
    Name          TEXT    NOT NULL,                 -- 'Architecture', 'Structural', etc.
    Description   TEXT,
    IsSummary     TEXT    NOT NULL DEFAULT 'N',     -- 'Y' for roll-up orgs
    IsActive      TEXT    NOT NULL DEFAULT 'Y',
    AD_Org_Type   TEXT    NOT NULL DEFAULT 'DISCIPLINE',  -- DISCIPLINE | SHARED
    element_count INTEGER DEFAULT 0                -- TE census count (informational)
);

-- Seed data (from Discipline.java enum + '*' shared org)
INSERT INTO AD_Org (AD_Org_ID, Value, Name, AD_Org_Type, element_count) VALUES
    (0,  '*',    'Shared',              'SHARED',     0),
    (1,  'ARC',  'Architecture',        'DISCIPLINE', 35338),
    (2,  'STR',  'Structural',          'DISCIPLINE', 1429),
    (3,  'FP',   'Fire Protection',     'DISCIPLINE', 6884),
    (4,  'ELEC', 'Electrical',          'DISCIPLINE', 1172),
    (5,  'ACMV', 'HVAC',               'DISCIPLINE', 1621),
    (6,  'CW',   'Cold Water',          'DISCIPLINE', 1431),
    (7,  'SP',   'Sanitary/Plumbing',   'DISCIPLINE', 979),
    (8,  'LPG',  'Gas Piping',          'DISCIPLINE', 209),
    (9,  'REB',  'Reinforcement',       'DISCIPLINE', 2660);
```

**Placement:** AD_Org lives in ERP.db (the AD Dictionary). All databases
reference it by `AD_Org_ID` (integer FK) or `Value` (text lookup for human-readable
contexts). Same pattern as iDempiere: AD_Org is a central lookup table.

#### 11.6.3 What AD_Org Replaces

| Current | Table.Column | New | Migration |
|---------|-------------|-----|-----------|
| `m_bom.bom_category` = 'FP' | BOM DB | `m_bom.AD_Org_ID` = 3 | ALTER + UPDATE CASE |
| `C_OrderLine.Discipline` = 'FP' | compile DB | `C_OrderLine.AD_Org_ID` = 3 | ALTER + UPDATE CASE |
| `component_types.discipline` = 'FP' | component_library.db | `component_types.AD_Org_ID` = 3 | ALTER + UPDATE CASE |
| `AD_Val_Rule.discipline` = 'FPR' | validation.db | `AD_Val_Rule.AD_Org_ID` = 3 | ALTER + UPDATE (unify codes) |
| `AD_Clash_Rule.discipline_a` = 'ELC' | validation.db | `AD_Clash_Rule.AD_Org_A_ID` = 4 | ALTER + UPDATE |
| `ad_ifc_class_map.discipline` = 'FP' | ERP.db | `ad_ifc_class_map.AD_Org_ID` = 3 | ALTER + UPDATE |
| `ad_element_mep.discipline` = 'FP' | ERP.db | `ad_element_mep.AD_Org_ID` = 3 | ALTER + UPDATE |
| `bad_discipline_priority.higher_discipline` | component_library.db | `bad_discipline_priority.higher_AD_Org_ID` | MOVE table + ALTER |

**Bonus: unifies the code inconsistency.** 'FPR' (validation.db) and 'FP' (everywhere
else) both become `AD_Org_ID = 3`. 'ELC' and 'ELEC' both become `AD_Org_ID = 4`.

#### 11.6.3a Design Note — Inference vs Data (deriveDiscipline retirement)

The S60 wiring (BomDropper → C_OrderLine.Discipline → NodeContext → PlacementCollectorVisitor)
is **directionally correct** — it moves discipline from inference to data. AD_Org completes
this trajectory. Three patterns currently coexist:

1. **Data-driven (correct):** C_OrderLine.Discipline populated from m_bom.bom_category at
   BomDrop time. Discipline flows through the order context, not guessed.
2. **Inference-driven (legacy):** `ProductCategory.deriveDiscipline(ifcClass)` and
   `TypeDisciplineMapping` infer discipline from IFC class. This bypasses the org hierarchy
   — every element gets discipline guessed from its class, not inherited from its context.
3. **Stack-driven (transitional):** PlacementCollectorVisitor.disciplineStack pushes
   bom_category at SET level. Runtime hack for what should be AD_Org inheritance.

**When AD_Org is implemented:**
- Pattern 1 becomes: `C_OrderLine.AD_Org_ID` = FK to AD_Org (single source of truth)
- Pattern 2 (`deriveDiscipline()`) becomes a **fallback for extraction only** — when
  ingesting a new IFC file with no order context, infer AD_Org_ID from ifc_class via
  `ad_ifc_class_map.AD_Org_ID`. Once the element enters the order pipeline, AD_Org_ID
  is data, not inference. deriveDiscipline() should NOT be called in the compile path.
- Pattern 3 (disciplineStack) disappears — AD_Org_ID is on C_OrderLine, inherited down
  the BOM tree via the walker, not pushed/popped manually.

**The 77-file M_Product_Category cleanup (S60-S2 Task 5) is the same refactor.** bom_category
is a string proxy for AD_Org. Once AD_Org_ID exists on m_bom, bom_category becomes a
derived display field (or drops entirely). The 40+ files touching discipline strings
converge to a single FK lookup pattern.

**M_Product_Category already models this.** The 4 discipline-level categories (ARC, STR,
FP, MEP) in M_Product_Category ARE the AD_Org tree expressed as product taxonomy. AD_Org
makes the relationship explicit: `M_Product_Category.AD_Org_ID` links each product
category to its owning discipline. This enables per-discipline product catalog views,
validation scoping, and BOM filtering — all from standard iDempiere patterns.

#### 11.6.4 Java Code Impact

| Change | Files | Effort |
|--------|-------|--------|
| X_C_OrderLine: getDiscipline() → getAD_Org_ID() | 1 | LOW — PO column rename |
| OrderLineWalker: pass AD_Org_ID instead of String | 1 | LOW — type change |
| PlacementCollectorVisitor: disciplineStack → Deque\<Integer\> | 1 | LOW |
| BOMWalker.NodeContext: discipline field String → int | 1 | LOW |
| BomDropper: set AD_Org_ID from bom.AD_Org_ID | 1 | LOW |
| Discipline.java enum: add AD_Org_ID field | 1 | LOW |
| SustainabilityDAO/FacilityMgmtDAO: GROUP BY AD_Org_ID | 2 | LOW |
| BackOffice DAOs: compConn M_Product → discConn M_Product | 4 | MED — connection param change |
| ProductRegistrar: write M_Product to ERP.db | 1 | MED — target DB change |
| MProduct.get(): accept discConn instead of compConn | 1 | LOW — param type same |
| ComponentLibrary: no change (reads component_definitions only) | 0 | NONE |
| MeshBinder: no change (resolves via M_Product_Image → geometry) | 0 | NONE |

**Total: ~14 production files changed, ~10 test files changed, ~25 geometry files unchanged.**
No geometry code touched. File counts are estimates — implementation session should
grep for exact numbers before starting (audit concern #1, #2).

#### 11.6.5 Migration Sequence (6 steps, each independently committable)

**Step 0: Drop dead tables** (prerequisite, R18 debt)
- DROP: ad_bom, ad_bom_child, ad_bom_child_param, ad_product_dim from component_library.db
- Net: −4 tables, zero code impact (unused per Known Debt audit)

**Step 1: Create AD_Org in ERP.db**
- DDL: AD_Org table + seed 10 rows (0='*', 1–9=disciplines)
- Migration: `DV013_ad_org.sql` (DV011/DV012 already taken — audit concern #4)
- Gate: DiscValidationDBTest adds W-DV-DB-ORG witness

**Step 2: Add AD_Org_ID columns alongside existing TEXT columns**
- ALTER TABLE ad_ifc_class_map ADD COLUMN AD_Org_ID INTEGER
- ALTER TABLE ad_element_mep ADD COLUMN AD_Org_ID INTEGER
- UPDATE ... SET AD_Org_ID = (SELECT AD_Org_ID FROM AD_Org WHERE Value = discipline)
- Same for component_types, placement_rules
- Migration: `DV014_ad_org_columns.sql`
- Gate: dual-column reads pass, no code changes yet

**Step 3: Move M_Product + M_Product_Category to ERP.db** ⚠️ HIGH RISK — **DONE (S65)**
- Migration: `DV015_move_m_product.sql` — ATTACH + INSERT OR IGNORE. 2,475 M_Product + 46 M_Product_Category rows.
- Java: 13 files changed. All compConn/compLibConn M_Product reads → ERP.db.
  ProductRegistrar dual-writes (compConn for geometry join + discConn for master catalog).
  BOMWalker, OrderLineWalker, PlacementLoader, BuildingWriter, 3 BIM_COBOL verbs,
  BackOfficeServer, DesignerAPIImpl — all read M_Product from ERP.db.
- M_Product_Image stays in component_library.db (geometry link intact).
- component_library.db M_Product NOT deleted (Step 6).
- Gate: SH 7/7, FK 7/7 PASS. DiscValidationDBTest 27/27 GREEN (+3 product witnesses).

**Step 4: Move remaining AD tables from component_library.db**
- bad_discipline_priority, bad_rule, bad_rule_category, bad_rule_param → ERP.db
- Remove 15 duplicate tables from component_library.db (already in ERP.db)
- Net: component_library.db drops from ~66 tables to ~7
- Gate: DiscValidationDBTest updated. Rosetta Stones GREEN.

**Step 5: Switch Java code from TEXT discipline to AD_Org_ID**
- Update PO classes, walkers, DAOs to use integer FK
- Remove Discipline string columns (or keep as computed/derived)
- Gate: full test suite GREEN. Discipline.java enum gains AD_Org_ID field.

**Step 6: Clean up**
- Drop vestigial TEXT discipline columns (or leave for human readability)
- Drop M_Product from BOM databases (production code no longer reads it)
- Update schema snapshots
- Gate: all Rosetta Stones GREEN. Schema docs updated.

#### 11.6.6 Risks and Mitigations

| Risk | Mitigation |
|------|-----------|
| component_library.db is SACRED (no git operations) | Steps 3-4 only ATTACH+read from it. No schema changes to component_library.db until Step 4 (table drops). |
| BOM DB M_Product removal breaks backward compat | Step 6 only — verify zero reads from bomConn M_Product first (Task 4 confirms this). |
| 15 duplicate tables: which copy is authoritative? | ERP.db copy is authoritative (Phase 2-3 migration already completed). component_library.db copies are stale remnants. |
| AD_Org_ID = 0 ('*') conflicts with SQLite auto-increment | Use explicit INSERT, not AUTOINCREMENT. iDempiere convention: 0 = system org. |
| BackOffice DAOs need connection parameter change | All 4 DAOs (Cost, Schedule, Sustainability, FacilityMgmt) already receive compLibConn — just rename to discConn. Same JDBC pattern. |

#### 11.6.7 Appendix F Corrections

AUDIT_S51_FOCUSED.md Appendix F states "34 AD tables" in component_library.db.
**Actual count: 66 `ad_*` tables** (confirmed by Appendix F itself, which corrected
this in the audit). §10.1 should be updated from "34 AD tables" to "66 AD tables"
when implementation begins.

---

### 11.7 Summary — Decision Matrix

| Criterion | Option A (Split) | Option B (Expand DV) | Option C (Fix guard) |
|-----------|-----------------|---------------------|---------------------|
| Code changes | ~14 prod + ~10 test files | ~14 + ~10 (same) | ~2 files |
| iDempiere alignment | **FULL** — M_Product with AD tables | FULL (same as A) | PARTIAL — mixed DB |
| Geometry isolation | **CLEAN** — 7 tables, pure LOD | CLEAN (same) | MIXED — 66+ tables |
| LOD chain breakage | **NONE** — M_Product_Image stays in geometry DB | NONE | N/A |
| Future maintainability | **HIGH** — clear concern boundaries | HIGH | LOW — grows worse |
| Discipline unification | **YES** — AD_Org eliminates 'FPR'/'FP' inconsistency | YES | NO |
| Risk | MED overall, **HIGH for Step 3** (M_Product move) | MED/HIGH (same) | LOW |

**Recommendation: Option A.** It's the same work as Option B but with a clearer name
(ERP.db is already the AD Dictionary in practice). The 6-step migration
is independently committable, each step gated by existing Rosetta Stone tests.

---

*References:
[DISC_VALIDATE_SRS.md](DISC_VALIDATE_SRS.md) §9 (5-table LOD chain) |
[DocAction_SRS.md](DocAction_SRS.md) §1.3 (processIt DocEvent) |
[CALIBRATION_SRS.md](CALIBRATION_SRS.md) (DocEvent vs Terminal) |
[G4_SRS.md](G4_SRS.md) §2 (output.db pattern) |
[AUDIT_S51_FOCUSED.md](AUDIT_S51_FOCUSED.md) Appendix F (database reality check)*
