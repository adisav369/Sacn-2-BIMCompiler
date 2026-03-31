# ERP.db SRS — Discipline Validation Database
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md)

<div class="bim-banner" markdown>
<b>ERP.db holds discipline metadata AND compliance rules, separate from products and BOMs.</b> Schedules, placement rules, alias cascades, mined dimension rules, and validation rules (AD_Val_Rule, AD_Clash_Rule, AD_Occupancy_Class) — the HOW concern per [MANIFESTO](MANIFESTO.md) §Three Concerns.
</div>

**Version:** 1.3 (2026-03-31)
**Depends on:** [DISC_VALIDATE_SRS.md](DISC_VALIDATE_SRS.md) §9-10, [DocAction_SRS.md](DocAction_SRS.md) §1.3, [CALIBRATION_SRS.md](CALIBRATION_SRS.md)

---

## 1. Schema — ERP.db (22 tables)

**Authoritative DDL:** `migration/DV001_ERP_schema.sql`, `migration/DV003_element_mep_alias.sql`

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
| `ad_element_mep_alias` | alias_id | 84 | IFC version-agnostic product resolution (§2.1) |
| `ad_ifc_class_map` | ifc_class | 46 | IFC class extraction authority (§2.2) |
| `ad_val_rule` | ad_val_rule_id | 415 | Mined dimension rules: typical W/D/H per (ifc_class, storey) |
| `ad_val_rule_param` | ad_val_rule_param_id | 1245 | Rule parameters (typical_width_mm, typical_depth_mm, typical_height_mm) |
| `W_Calibration_Result` | id | 0 | CalibrationTest output (runtime writes) |
| `AD_SysConfig` | Name | 3 | Schema/seed/alias version tracking |

Compliance tables (AD_Val_Rule, AD_Clash_Rule, AD_Occupancy_Class, AD_Validation_Result)
and shared discipline recipes (M_BOM, M_BOM_Line) also live in ERP.db. Full compliance
schema: see [DocValidate.md](DocValidate.md).

---

## 2. Cross-Database References

SQLite has no cross-database FK. References use **name convention** — same
pattern as iDempiere `AD_Reference` lookups:

| ERP.db column | Resolves to | Method |
|--------------------------|-------------|--------|
| `ad_element_mep.element_type` | `M_Product` by alias cascade | Java: try ifc_class → predefined_type → type_class → element_name LIKE |
| `ad_space_type_mep_bom.mep_product_id` | `ad_element_mep.element_type` | SQL within ERP.db (same DB) |
| `ad_assembly_connector.assembly_id` | `M_Product.name` | Java: `SELECT * FROM M_Product WHERE name = ?` |
| `placement_rules.element_name` | `M_Product.name` or `m_bom.bom_id` | Java: lookup by name |

```
ERP.db                          component_library.db
┌─────────────────────────┐                ┌──────────────────────┐
│ ad_element_mep          │                │ M_Product            │
│   element_type: SPRINKLER──── name ────▶│   name: SPRINKLER    │
│   ifc_class: IfcFire... │                │   width, depth, height│
│   discipline: FP        │                │   ifc_class          │
│   ports: [{"IN":0.015}] │                └──────────────────────┘
└─────────────────────────┘
```

No geometry in ERP.db. No discipline metadata in component_library.db.

### 2.1 IFC Version-Agnostic Resolution — `ad_element_mep_alias`

IFC2x3 lumps all MEP into generic classes (IfcFlowTerminal). IFC4 splits
them into specific subtypes (IfcOutlet). Real-world IFC files use
vendor-specific naming. 4-tier resolution cascade:

```
Priority 1: ifc_class        — IfcOutlet → OUTLET (IFC4 direct match)
Priority 2: predefined_type  — POWEROUTLET → OUTLET (IFC4 enum)
Priority 3: type_class       — IfcOutletType → OUTLET (IFC2x3 via IfcRelDefinesByType)
Priority 4: element_name     — %Receptacle% → OUTLET (name pattern, last resort)
```

84 aliases covering all 12 canonical MEP types. DX resolution: 101/119 distinct
MEP names (85%).

### 2.2 IFC Class Extraction Authority — `ad_ifc_class_map`

Authority table for `extract.py`. Read at startup — adding a new IFC type
= one INSERT, zero code changes.

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

See [`InfrastructureAnalysis.md`](InfrastructureAnalysis.md) §3.3.

---

## 3. Connection Map

```
CompilationPipeline     → component_library.db (LOD)
PlacementValidator      → ERP.db (compliance rules)
CalibrationDAO          → ERP.db + TE_BOM.db
MEPAD/MEPBOMResolver    → ERP.db (discipline metadata)
ManifestResolver        → ERP.db (discipline metadata)
DocEvent                → ERP.db (schedules) + component_library.db (LOD fetch)
Handler cascade H1-H6  → ERP.db (discipline metadata + compliance rules)
```

```java
Connection compConn;   // component_library.db — LOD catalog
Connection discConn;   // ERP.db               — discipline metadata + compliance rules
Connection bomConn;    // {prefix}_BOM.db       — building BOM
```

---

## 4. File Location

```
library/
├── component_library.db     ← LOD catalog (M_Product, geometries)
├── ERP.db                   ← discipline metadata + compliance rules
└── {PREFIX}_BOM.db          ← per-building BOM

migration/
├── DV001_ERP_schema.sql     ← schema DDL (19 tables)
├── DV002_seed_from_component.sql ← seed via ATTACH (17 tables)
├── DV003_element_mep_alias.sql   ← IFC alias cascade (84 rows)
├── DV005_ifc_class_map.sql       ← IFC class extraction authority (46 rows)
└── V001..V006               ← compliance rule migrations
```

---

## 5. Traceability

| Witness | What it Proves | Test |
|---------|---------------|------|
| W-DV-DB-SCHEMA | DDL creates all 20 required tables | DiscValidationDBTest |
| W-DV-DB-SEED | Seed data matches component_library.db source counts | DiscValidationDBTest |
| W-DV-DB-REF | Reference pointers resolve across databases | DiscValidationDBTest |
| W-DV-DB-ALIAS | Alias cascade resolves IFC2x3↔IFC4 (84 rows, 4 tiers) | DiscValidationDBTest |
| W-DV-DB-ND | Schema changes do not disturb component_library.db | DiscValidationDBTest |

---

## 6. AD_Org — Disciplines as Organizational Units

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

### 6.1 Spatial Model — Space + Occupant + Verb + Rule

> *A discipline is a contractor with a checklist, not a room with walls.*

Disciplines are not spatial containers — a fire protection pipe network
spans the entire floor. Discipline is a line attribute (AD_Org_ID), not a
tree level. See [TerminalAnalysis.md §Compilation Status](TerminalAnalysis.md#te-compilation-status--honesty-report-s99-2026-03-27).

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

### 6.2 Discipline Profiles — Abstract Recipe, Space-Dependent Placement

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

### 6.3 Three-Stage Validation — iDempiere Processing Order

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

**Per-space compliance (S100-p82, f3c4d793):** Room dimension evaluation (area, width, height) against AD_DocEvent_Rule is implemented. SKIP propagation cascades through the BOM walk.

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

#### AD_Val_Rule — 3rd Stage (ERP.db, compliance rules)

The V001 schema stays as-is within ERP.db. AD_Val_Rule is a user-initiated
per-line rule addition/change/waiver (government standards are 1st-stage
DocEvent, not 3rd-stage AD_Val_Rule).

The user opens an exploded order, sees specific sub-lines, and attaches:
- **ADD:** "Apply stricter 3000mm spacing to THIS branch" (new rule on line)
- **CHANGE:** "Override threshold from 4600mm to 3800mm for THIS zone"
- **WAIVE:** "Acknowledge and accept this deviation" (AD_Val_Rule_Exception)

This is exactly how iDempiere's AD_Val_Rule works — a lookup filter that
the user configures on a specific field/line to narrow or adjust what's valid.

### 6.4 BOM Tree Structure

`BUILDING → FLOOR → LEAF` — same depth for all building categories.
Tack is `element.minX - floor.minX`. Always positive. Discipline resolves
from the child product: `m_bom_line.child_product_id →
M_Product → M_Product_Category → AD_Org_ID`. Standard iDempiere —
every record carries AD_Org, the line is just a relationship.

| Component | Responsibility |
|-----------|----------------|
| `DisciplineBomBuilder` | LEAF lines directly under FLOOR (no DISCIPLINE SET level) |
| `BomValidator` | W-TACK-1/W-BUFFER-1 check FLOOR→LEAF |
| `CompilationPipeline` | Single walk path, no category skip hack |
| `PlacementCollectorVisitor` | Resolve AD_Org_ID from child product, not discipline stack |

### 6.5 The BOM Is Already Perfect

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

### 6.6 Shared Discipline Recipes in ERP.db

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

ERP.db also contains (government standards):
├── AD_Val_Rule               (NFPA 13, UBBL, MS1183 — compliance checks)
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

### 6.6.1 Discipline Separation — Two-Class Architecture

Discipline separation spans two pipeline classes:

**Class A — IFCtoBOM (extraction):** Produces the ARC+STR envelope only.
MEP elements (FP, ACMV, ELEC, CW, SP, LPG) are **not** written into the
per-building `*_BOM.db`. IFCtoBOM counts MEP elements per discipline from
`elements_meta.discipline` and writes them back into the YAML
(`disciplines: [{disc: FP, qty: 99}]`) AND to `ad_sysconfig` in BOM DB.
The YAML becomes the reusable preset template for that building type.

**YAML as chooser:** Designer opens the YAML, sees pre-populated discipline
qtys from extraction, can reduce scope (e.g., qty: 50 for a partial wing).
Qty=0 or qty>capacity both mean "fill the whole building per rules" —
the compiler fills until no more space and stops gracefully, never forces.
For RE buildings, `mep_disciplines:` controls which DISCs the Callout
creates (default: ELEC + SP). Deleting a discipline from YAML removes it.
Without YAML (direct OrderLine), user gets default DISCs from Callout.

**Class B — DAGCompiler (compilation):** Product Callout reads YAML
`disciplines:` qty, then creates DISCIPLINE OrderLines pointing to shared
recipes in ERP.db (`FP_SYSTEM`, `ACMV_SYSTEM`, etc.). DocEvent per Org
fires on each discipline OrderLine — applies jurisdiction rules (NFPA 13,
UBBL, MS 1228). RouteBuilder/CrawlRouter generates MEP routing until qty
terminals served. AD_Val_Rule validates the output.

**Pending:** `DisciplineBomBuilder` currently writes all elements flat
under FLOOR. Corrective in prompt `00b_discipline_separation.txt` —
IFCtoBOM produces ARC+STR only, MEP comes from ERP.db shared recipes
via DAGCompiler Callout.

For **generative buildings**, the compiler applies the shared recipes
using verb Strategy + DocEvent per Org + ASI. AD_Val_Rule validates
the output.

### 6.7 GoF Design Patterns

| Pattern | Application |
|---------|-------------|
| **Composite** | BOM tree: SPACE contains OCCUPANT lines, recursively |
| **Visitor** | BOMWalker visits each line |
| **Strategy** | Verb determines placement method (DocEvent + ASI) |
| **Specification** | AD_DocEvent_Rule validates during walk (1st, blanket + standards). AD_Val_Rule = user override (3rd, per-line) |

### 6.8 Cross-References

| Building | Disciplines | Concern |
|----------|-------------|---------|
| **TE** | 8 (ARC,STR,FP,ACMV,ELEC,CW,SP,LPG) | SET as tree level → tack overflow. [TerminalAnalysis.md](TerminalAnalysis.md) |
| **DM** | 3 (ARC,STR,FP) | First FP trial — addDiscipline(). [DemoHouseAnalysis.md](DemoHouseAnalysis.md) |
| **FK** | 2 (ARC,STR) + ROOF debate | [FZKHausAnalysis.md](FZKHausAnalysis.md) |
| **Infrastructure** | ROAD,RAIL,GEO,LAND,SIGN | Extended codes. [InfrastructureAnalysis.md](InfrastructureAnalysis.md) |

### 6.9 Stair Validation Rules — Candidate AD Table Extensions (S100-p84)

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

### 6.10 Movement Verbs — Routing Linear Elements Through Buildings

Linear MEP elements (pipes, ducts, cables) don't just get placed at points.
They **move** through the building — following surfaces, bending at corners,
branching at junctions, penetrating floors. Each direction change or connection
produces a **joint fitting** product alongside the segment.

TE extraction proves fittings outnumber segments in most disciplines:

| Discipline | Segments | Fittings | Ratio | Implication |
|-----------|----------|----------|-------|-------------|
| FP | 2,672 | 3,146 | 1.18× | More joints than pipes |
| ACMV | 568 | 713 | 1.26× | Every duct turn = fitting |
| CW | 619 | 638 | 1.03× | Nearly 1:1 |
| SP | 455 | 372 | 0.82× | Longer runs, fewer turns |
| LPG | 75 | 87 | 1.16× | Small system, many valves |

#### Movement Verb Catalogue

Each movement verb produces **two BOM lines**: a segment (pipe/duct/cable)
and a joint fitting (elbow/tee/reducer/sleeve). The fitting is an M_Product
from the component library with its own LOD mesh.

| Verb | Action | Joint product | Geometry |
|------|--------|--------------|----------|
| **FOLLOW** | Trace along surface (wall, ceiling, beam) | None — straight run | PipeSegment / DuctSegment, qty = length ÷ stock_size |
| **BEND** | Change direction at angle | Elbow fitting (90°, 45°, custom) | ForgeEngine: PIPE_BEND (arc geometry, S99) |
| **RISE / DROP** | Change elevation (through floor or along wall) | Elbow or offset fitting | Vertical segment + 2 elbows |
| **BRANCH** | Split into sub-paths | Tee or Wye fitting | T-junction, diameters from parent + children |
| **REDUCE** | Change diameter | Reducer fitting | Concentric or eccentric reducer |
| **PENETRATE** | Pass through floor or wall | Sleeve + fire collar (if fire-rated) | Hole + sleeve product + sealant |

#### Composition in BIM COBOL

Movement verbs compose into a routing script. Each line in the script
produces BOM lines (segments + fittings):

```
ROUTE FP FROM PUMP_ROOM
  FOLLOW CEILING SPACING 4500        → PipeSegment × N
  BEND 90 AT GRID_A                  → PipeFitting (elbow)
  BRANCH TEE TO ROOM_101 ROOM_102    → PipeFitting (tee) + 2 sub-routes
  REDUCE 50mm TO 25mm                → PipeFitting (reducer)
  PENETRATE SLAB WITH FIRE_COLLAR    → Sleeve + FireCollar products
```

The BOM IS the routing. No graph data structure needed — parent-child
with sequence controlling path order. Each fitting is a BOM child with
qty=1 (EA) at the transition point.

**UOM conversion:** CrawlOps produce lengths in mm internally. RouteStage
converts to the product's cost_uom at persistence: mm ÷ 1000 → M for
pipe/duct segments; fitting qty stays 1 (EA). This is the single
conversion point — all internal geometry is mm, all persisted qty
matches M_Product.cost_uom.

#### Joint Product Resolution

When a movement verb needs a fitting, it resolves from the component library:

```
Inputs:  verb (BEND), angle (90°), parent_diameter (50mm), material (Poly Steel)
Lookup:  M_Product WHERE ifc_class='IfcPipeFitting'
           AND diameter=50 AND material='Poly Steel' AND angle=90
Result:  Product_ID → LOD mesh from component_library.db
```

If no exact match: ForgeEngine computes the geometry (PIPE_BEND, S99).
ASI carries per-instance overrides (actual angle, actual diameter).

#### Movement Verbs per Discipline

| Discipline | Primary verbs | Typical route | Standards governing routing |
|-----------|--------------|--------------|---------------------------|
| **FP** | FOLLOW ceiling, BRANCH tee, PENETRATE slab | Riser → floor header → branches → heads | NFPA 13 §8 (spacing), UBBL Part VII |
| **ACMV** | FOLLOW ceiling void, BEND, BRANCH, REDUCE | AHU → main duct → branches → diffusers | MS 1525 (duct sizing), ASHRAE 62.1 |
| **ELEC** | FOLLOW cable tray, BRANCH, PENETRATE | DB → riser → tray → outlets/lights | MS IEC 60364 (cable sizing), NEC 300.4 |
| **CW** | FOLLOW wall/ceiling, RISE, BRANCH, REDUCE | Tank → riser → floor header → fixtures | MS 1228 (pipe sizing by fixture unit) |
| **SP** | DROP (gravity), FOLLOW gradient, BRANCH wye | Fixtures → waste → stack → drain | MS 1228 (min gradient 1:40 / 1:60) |
| **LPG** | FOLLOW ext wall, BRANCH, REDUCE | Meter → riser → kitchen → gas points | MS 830 (gas installation) |

**SP is special:** all other disciplines flow outward/upward from a source.
SP flows **downward by gravity**. FOLLOW must maintain minimum gradient
(1:40 for 100mm pipe). The verb checks slope at each segment.

### 6.11 Parasitic Discipline Implementation Tasks

Implementation in 4 phases: POC first to prove assumptions, then build out.

#### Phase 0 — POC: Prove the Wiring (2-3 prompts)

Early proof-of-concept tasks that validate the architecture before
committing to the full build. Each is a standalone Rosetta Stone test.

| Task | What it proves | Deliverable | Gate |
|------|---------------|-------------|------|
| **T0.1** Service room categories | ARC rooms with discipline-typed M_Product_Category tack correctly; FP_SYSTEM origin resolves from ARC pump room | Seed 6 service room products (FP, ACMV, ELEC, CW, SP, LPG) in ERP.db M_Product. Add to SH YAML as dummy rooms. Verify category match query returns correct dx/dy/dz. | SH 7/7, query returns pump room coords |
| **T0.2** OrderLine callout POC | Callout reads CO BOM children and auto-creates discipline OrderLines | Implement `OrderLineProductCallout.java`. Wire to C_OrderLine.M_Product_ID. Test: set product=BUILDING_TE_STD → verify 8 OrderLines created with correct AD_Org_ID and sequence. | Unit test: 8 lines, correct orgs |
| **T0.3** Parasitic qty walk | Walker handles qty-only BOM lines (no dx/dy/dz) without crashing; produces container c_orderlines with correct qty | Add FP_SYSTEM as 2nd OrderLine on SH (qty=2 sprinklers, dummy). Verify walker produces c_orderline with qty=2, host_type=LEAF, AD_Org_ID=3. No placement — just qty passthrough. | SH 7/7 (no regression), FP orderline exists |
| **T0.4** FOLLOW verb POC | ROUTE verb extended: FOLLOW a ceiling surface, lay N segments of stock length | Add FOLLOW as ROUTE sub-mode. Test: FOLLOW ceiling in SH living room → produces PipeSegment × ceil(room_length / stock_pipe_length). Fitting count = 0 (straight run). | Witness: W-FOLLOW-1 |

#### Phase 1 — Movement Verbs (3-4 prompts)

Core routing verbs, each tested independently on SH before fleet.

| Task | Verb | Joint product | Test |
|------|------|--------------|------|
| **T1.1** BEND | Change direction, insert elbow | Elbow fitting from component library or ForgeEngine PIPE_BEND | W-BEND-1: angle + diameter → correct fitting product |
| **T1.2** BRANCH | Split path, insert tee/wye | Tee fitting, parent + child diameters | W-BRANCH-1: main → 2 sub-routes, tee inserted |
| **T1.3** REDUCE | Change diameter, insert reducer | Reducer fitting | W-REDUCE-1: 50mm→25mm, reducer product |
| **T1.4** PENETRATE | Pass through slab/wall | Sleeve + fire collar (if fire-rated) | W-PENETRATE-1: sleeve inserted at floor crossing |

#### Phase 2 — Discipline Routing (3-4 prompts)

Wire movement verbs into discipline-specific DocEvent rules.

| Task | Discipline | Route pattern | Standard |
|------|-----------|--------------|----------|
| **T2.1** FP routing | FP | Riser → floor header → branches → sprinkler grid | NFPA 13 §8 spacing, `ad_fp_coverage` hazard class |
| **T2.2** ELEC routing | ELEC | DB → cable tray → light fixture grid per room | MS 1525 lighting power density, IES lux |
| **T2.3** CW + SP routing | CW, SP | CW: tank → riser → fixtures. SP: fixtures → stack → drain (gravity) | MS 1228, UPC gradient rules |
| **T2.4** ACMV routing | ACMV | AHU → main duct → branches → air terminals | MS 1525, ASHRAE 62.1 air changes |

#### Phase 3 — Integration (2-3 prompts)

| Task | What | Deliverable |
|------|------|-------------|
| **T3.1** Multi-discipline TE | All 8 OrderLines explode on TE with parasitic walk | TE discipline distribution matches extraction (§3.6.3 expected counts) |
| **T3.2** Cross-discipline clearance | NEC_ELEC_SP_CLEARANCE fires after all disciplines placed | Detect 11 known overlaps from TE mining (M12) |
| **T3.3** Infrastructure POC | BR (bridge) with zone-based anchors instead of rooms | BR 7/7 with STR + DRAIN discipline OrderLines |
| **T3.4** RE subset | SH with ARC + ELEC + SP (3 disciplines, subset callout) | SH 7/7, 3 OrderLines, light fixtures + plumbing placed |

##### T3.1 Implementation — Pipeline Wiring

P104 verification found 4 blockers. Resolution:

**B1 — RouteDocEvent not in pipeline.** RouteDocEvent.fireAll() must be called
during compilation, after CompileStage (which produces ARC c_orderline positions)
and before WriteStage (which writes output.db). The callout
(OrderLineProductCallout.onProductChanged + expandDisciplineLines) must also
move from BuildingRegistryTest into the pipeline at the same point.

Pipeline sequence with routing:
```
CompileStage → [callout + RouteDocEvent.fireAll()] → WriteStage
```

The callout reads ERP.db shared BOMs, creates DISCIPLINE OrderLines, expands
LEAF children with qty. RouteDocEvent reads BuildingGeometry (ARC c_orderline
positions) and produces RouteResult per discipline. Both must fire before
WriteStage persists to output.db.

**B2 — No edge persistence.** CrawlRouter produces CONNECTS_TO edges in-memory.
BIMEyes P16 (WasteGradientProof) and P17 (SystemConnectedProof) need queryable
data in output.db.

Output tables:

| Table | Columns | Source | Consumer |
|-------|---------|--------|----------|
| system_edges | discipline, from_index, to_index, from_xyz, to_xyz, edge_type | RouteResult.edges() | P17 BFS connectivity |
| system_nodes | discipline, node_index, node_type, xyz, diameter_mm, product, length_mm | RouteResult.segments() + fittings() | P16 SP gradient check |

**B4 — BIMEyes gate.** ProveStage gates P15/P16/P17 behind hasRelationalData()
which checks ad_room_boundary. CO buildings have DISCIPLINE OrderLines but no
ad_room_boundary. Gate must also check system_edges > 0.

##### T3.4 Implementation — RE Subset

**B3 — Callout pre-populates by category, YAML removes exceptions.**

The Callout inserts a **sensible default** per M_Product_Category. The user
(or YAML) always sees something — never starts from blank:

| Category | Callout default | Rationale |
|----------|----------------|-----------|
| CO (Commercial) | all 6 MEP (FP, ELEC, ACMV, CW, SP, LPG) | Commercial buildings need full MEP |
| RE (Residential) | ELEC, SP | Every house needs electrical + plumbing at minimum |
| IN (Infrastructure) | none | Roads/bridges have no building MEP |

**Two-phase flow:**

1. **Callout fires** → inserts default discipline OrderLines for the category
2. **YAML handling** → reads `remove_disciplines`, deactivates (`IsActive='N'`)
   those entries. The OrderLine row stays visible so the user can re-enable.

```yaml
# RE house — default [ELEC, SP] already inserted by Callout
# No YAML key needed — user sees ELEC + SP and can add/remove in GUI

# RE house that doesn't want plumbing
remove_disciplines: [SP]    # deactivates SP, keeps ELEC

# CO warehouse that doesn't need gas
remove_disciplines: [LPG]   # deactivates LPG, keeps 5 others

# RE house that wants fire protection added beyond default
add_disciplines: [FP]       # adds FP to the ELEC + SP default
```

This is the iDempiere Configure-to-Order pattern: category provides the
template, user modifies by exception. Deactivation (not deletion) preserves
the discipline row for audit trail and re-enablement.

**BIM Designer GUI equivalent:** user selects building category → discipline
OrderLines appear pre-populated. Toggle switch per discipline to
enable/disable. Same `IsActive` column that YAML's `remove_disciplines`
controls. Adding a discipline not in the default = `addDiscipline()` mutation
(Session A).

**Code changes needed:**

1. `OrderLineProductCallout.onProductChanged()` — add RE default set
   `[ELEC, SP]`. Currently RE returns 0 unless YAML whitelist present.
   New logic: if no YAML override, insert category default. IN stays at 0.

2. New: `applyYamlOverrides()` — after callout, read `remove_disciplines`
   from ad_sysconfig, SET `IsActive='N'` on matching DISCIPLINE OrderLines.
   Read `add_disciplines`, insert any not already present.

3. Remove current `mep_disciplines` whitelist logic (lines 56-60, 87-91)
   — replaced by the two-phase default+override pattern.

SH: `mep_disciplines: [ELEC, SP]` → 2 DISCIPLINE OrderLines + ARC + STR = 4.

##### T3.5 Finding — MEP UOM Correction

**Finding (S100 watchdog):** `M_Product.cost_uom` in ERP.db is M3 (cubic meters)
for nearly all MEP products (pipe segments, fittings, ducts, terminals, valves).
This came from extraction — bounding box volume was computed for all elements.

ARC/STR products have correct UOM (walls=M2, beams=M, doors=EA). MEP products
do not. Correct trade UOM by ifc_class:

| ifc_class | Current | Correct | Reason |
|-----------|---------|---------|--------|
| IfcPipeSegment | M3 | **M** | Pipe bought by linear meter |
| IfcPipeFitting | M3 | **EA** | Fittings bought per piece |
| IfcDuctSegment | M3 | **M** | Duct bought by linear meter |
| IfcDuctFitting | M3 | **EA** | Fittings bought per piece |
| IfcFlowTerminal | M3 | **EA** | Sprinkler heads, taps = each |
| IfcFlowFitting | M3 | **EA** | Couplings, adapters = each |
| IfcFlowController | M3 | **EA** | Valves, dampers = each |
| IfcAirTerminal | M3 | **EA** | Diffusers, grilles = each |
| IfcLightFixture | M3 | **EA** | Light fixtures = each |
| IfcFireSuppressionTerminal | M3 | **EA** | Sprinkler heads = each |
| IfcValve | M3 | **EA** | Valves = each |
| IfcAlarm | M3 | **EA** | Alarms = each |
| IfcReinforcingBar | M3 | **KG** | Rebar bought by weight (PWD 203A, NRM2) |
| IfcCourse | M3 | **M2** | Masonry = wall face area (PWD 203A §G) |
| IfcCovering | M3 | **M2** | Cladding, insulation, ceiling tiles = area (PWD 203A §H) |
| IfcFurnishingElement | M3 | **EA** | Furniture bought per piece |

**Impact:** RouteStage persists qty from CrawlRouter. Pipe segment qty should be
in meters (length from FollowOp), fitting qty should be 1 (each from BendOp/
BranchOp/ReduceOp). If cost_uom is M3, the 5D cost engine will multiply qty ×
unit_cost incorrectly (volume vs length vs count).

**Fix:** DV migration updating cost_uom by ifc_class for MEP products. iDempiere
convention: `C_UOM_ID` FK to `C_UOM` table. Current schema uses TEXT `cost_uom`
— acceptable for now, align to `C_UOM_ID` INTEGER FK in a future PK conformance
pass.

**Migration SQL pattern (for coder to implement as DV029):**

```sql
-- Linear segments: M3 → M (bought by linear meter)
UPDATE M_Product SET cost_uom = 'M'
WHERE ifc_class IN ('IfcPipeSegment', 'IfcDuctSegment', 'IfcFlowSegment')
  AND cost_uom = 'M3';

-- Discrete fittings/terminals: M3 → EA (bought per piece)
UPDATE M_Product SET cost_uom = 'EA'
WHERE ifc_class IN ('IfcPipeFitting', 'IfcDuctFitting', 'IfcFlowTerminal',
    'IfcFlowFitting', 'IfcFlowController', 'IfcAirTerminal',
    'IfcLightFixture', 'IfcFireSuppressionTerminal', 'IfcValve', 'IfcAlarm')
  AND cost_uom = 'M3';

-- Furnishings: M3 → EA (bought per piece)
UPDATE M_Product SET cost_uom = 'EA'
WHERE ifc_class IN ('IfcFurnishingElement', 'IfcFurniture')
  AND cost_uom = 'M3';

-- Rebar: M3 → KG (bought by weight — PWD 203A, NRM2, AIQS)
UPDATE M_Product SET cost_uom = 'KG'
WHERE ifc_class = 'IfcReinforcingBar'
  AND cost_uom = 'M3';

-- Masonry courses: M3 → M2 (wall face area — PWD 203A §G)
UPDATE M_Product SET cost_uom = 'M2'
WHERE ifc_class = 'IfcCourse'
  AND cost_uom = 'M3';

-- Coverings: M3 → M2 (area coverage — PWD 203A §H)
UPDATE M_Product SET cost_uom = 'M2'
WHERE ifc_class = 'IfcCovering'
  AND cost_uom = 'M3';
```

**Scope:** ERP.db M_Product table (~320 MEP + 346 rebar + 7 course + 35 covering rows).
component_library.db M_Product must also be updated (same SQL).
BOM.db copies are regenerated on next extraction — no manual fix needed.
**New UOM value:** KG (not previously used). No schema change — cost_uom is TEXT.

#### Dependencies

```
T0.1 (service rooms) ──→ T0.3 (qty walk) ──→ T2.x (discipline routing)
T0.2 (callout)       ──→ T3.1 (multi-disc TE)
T0.4 (FOLLOW)        ──→ T1.x (movement verbs) ──→ T2.x
P94  (BomWriter)     ──→ T1.x (new BOM lines need single write path)
```

**T0.1 is the critical first task.** If the category-match query doesn't
return the right room coordinates, the entire parasitic model breaks.
Prove it on SH first (small, fast, 7/7 GREEN).

### 6.12 Routing Architecture Assessment — Industry Position & Known Gaps

The CrawlRouter (§10.4.10) is a **prescriptive recipe engine**: each discipline
builder encodes a standard installation pattern as a sequence of CrawlOps.
This section assesses the approach against industry practice and documents
known gaps for future work.

#### Industry Comparison

| Approach | Used by | How it works |
|----------|---------|-------------|
| Search-based (A*/Dijkstra on voxel grid) | Revit auto-route, GenMEP, academia | Voxelize building → pathfind → connect endpoints |
| Pathway/spine fill | EVOLVE MEP | User defines routing spine, tool fills segments along it |
| Port-to-port connector | Bentley OpenPlant | Shortest path between two component connection ports |
| **Prescriptive recipe** | **This compiler (CrawlOp)** | Discipline builder encodes installation pattern as ops |
| AI/generative | Auto BIM Route AI | ML generates candidate routes within constraints |

No other tool produces **BOM-integrated, discipline-aware, deterministic routing**.
Search-based tools produce geometry first; BOM is an afterthought. Our CrawlOp
model produces the BOM as primary output — segments and fittings are product
references with diameter, length, and position. Geometry is derived from the BOM.

#### Strengths of Prescriptive Recipe

1. **BOM is primary output.** Every segment and fitting is an M_Product with
   qty, UOM, and cost. No post-processing step to extract material take-off.

2. **Deterministic.** Same building + same recipe = same output, always.
   Auditable: every segment traces to a CrawlOp in a spec-cited builder.

3. **Discipline-aware.** Each builder encodes domain knowledge — FP knows
   riser→header→branch (NFPA 13), SP knows gravity drainage (MS 1228 gradient
   1:40), ACMV knows duct sizing (ASHRAE 62.1). A* does not know any of this.

4. **O(n) not O(V log V).** No voxel grid needed. Routing scales linearly
   with op count, not building volume.

5. **IFC-compatible.** `system_edges`/`system_nodes` map to
   `IfcRelConnectsPorts` + `IfcFlowSegment`/`IfcFlowFitting`. CrawlOp is the
   generation engine; IFC is the serialization target.

#### Known Gaps

##### Gap 1: Ceiling void routing — CLOSED (P118)

**Problem:** All 6 builders route at `floor.zMm()` (floor slab top). In
practice, pipes and ducts run in the ceiling void — underside of the slab
above, minus clearance.

**Fix (P118, S100-p118):** `ceilingHeightMm(floorRef)` added to
`BuildingGeometry` + `SqlBuildingGeometry`. All 6 RouteBuilders updated:
horizontal MEP runs at ceiling void Z (`nextFloor.z - slabThickness - 50mm
clearance`). P118b: `SqlBuildingGeometry.floors()` now uses absolute Z from
walked placements instead of BOM-relative `c_orderline dz`.

##### Gap 2: No obstacle avoidance during routing

**Problem:** CrawlOps execute sequentially with no spatial awareness of other
routes or ARC elements. If FP riser and ACMV duct occupy the same shaft,
the router does not detect or avoid the conflict.

**Current mitigation:** `CheckClashVerb` (BIM COBOL verb) runs in VerbStage
(Step 7) as a post-route R-tree overlap check with 50mm clearance
(`BIMConstants.MEP_STRUCTURE_CLEARANCE`). Clash is detected after the fact,
not prevented.

**Industry practice:** GenMEP and academia use obstacle-aware A* (voxels
blocked by existing elements). This is the main advantage of search-based
routing.

**Future hybrid:** Prescriptive recipes for the discipline skeleton
(riser→header→branch pattern) + search-based pathfinding for last-mile
segment paths within each branch. BlenderBIM Issue #6521 proposes a
voxel-A* orthogonal pathfinder that could serve as the last-mile solver.

##### Gap 3: Verb bypass — CLOSED (P119)

**Problem:** RouteBuilders composed CrawlOps directly, bypassing VerbRegistry.
Routing was not auditable as verb lines in the pipeline log.

**Fix (P119, [f9fc4bc9](https://github.com/red1oon/BIMCompiler/commit/f9fc4bc9)):**
`CrawlOp.toVerbLine()` on all 5 ops. `DisciplineRouteBuilder.plan()` replaces
`buildRoute()` — builders return `RoutePlan`, default `buildRoute()` logs verb
lines at INFO then executes via CrawlRouter. All 6 builders refactored.
SH: ELEC 15 + SP 13 verb lines. DX: ELEC 25 + SP 23.

##### Gap 4: Missing real-world concerns

| Concern | Status | Path to fix |
|---------|--------|-------------|
| Hanger/support spacing | EXISTS (`HangVerb`, SMACNA 1200mm) | Already a verb — wire into RouteBuilder output |
| Insulation | **CLOSED** (P121) | Insulation as BOM child per discipline: FP 25mm (fire-rated), ACMV 50mm (thermal), CW 25mm (condensation), SP/ELEC 0mm |
| Soffit clearance | MISSING | Derive from ceiling void height. Minimum 50mm below soffit per MS 1525 |
| Access/maintenance points | MISSING | Valves and cleanouts at branch points. SP needs cleanout access per MS 1228 §5 |
| LPG wall thickness | **CLOSED** (P121) | `wallThickness(floorRef)` added to `BuildingGeometry`. Queries `c_orderline` for LEAF WALL elements. Fallback: 200mm |

##### Gap 5: Standard citation depth — CLOSED (P120)

**Problem:** RouteBuilders cited standards but didn't trace to specific clauses.

**Fix (P120, [c78c743b](https://github.com/red1oon/BIMCompiler/commit/c78c743b)):**
`standardRefs()` on all 6 builders — NFPA 13, MS 1228, MS 1525, MS 830,
ASHRAE 62.1. Logged at INFO with parameter values for compliance audit.

#### Proof Consumption (P15/P16/P17)

The three BIMEyes proofs that consume routing edges are fully implemented:

| Proof | What it checks | Data consumed | Status |
|-------|---------------|---------------|--------|
| P15 PIPE_IN_HOST | Pipe bbox within host room bbox | PlacementData + RoomData | Gated by relational data |
| P16 WASTE_GRADIENT | SP pipes slope downward | CONNECTS_TO edges, `fromZ >= toZ` | Gated by system_edges > 0 |
| P17 SYSTEM_CONNECTED | BFS — every terminal reaches a source | CONNECTS_TO adjacency graph | Gated by system_edges > 0 |

Gate at ProveStage (Step 11): `hasRelationalData() OR isGenerative() OR hasSystemEdges()`.
Currently blocked for most buildings because `ad_element_dependency` (the
CONNECTS_TO edge source for P16/P17) is populated only for legacy buildings.
The RouteStage edges land in `system_edges`/`system_nodes` — wiring these
into P16/P17 is the next integration step.

#### Room-Aware Branching (Level 2)

Gaps 1–5 fix the routing **skeleton** — correct Z, auditable verbs,
traceable standards, missing products. This section addresses the next
layer: making branches spatially intelligent within rooms.

##### Current behaviour

When a route branches to a room, every builder does the same thing:

```java
RoomDimensions roomDims = geo.roomDimensions(room.ref());
double roomRun = roomDims != null ? roomDims.longestAxis() : 3000;
branchOps.add(new FollowOp(roomRun, STOCK_LENGTH_MM));
```

The branch enters the room and follows the longest axis for its full length.
It does not know where it enters, where fixtures are, or how the room is
shaped. Every room gets the same treatment regardless of discipline.

##### What each discipline actually needs

| Discipline | In-room behaviour | Standard | What's missing |
|-----------|-------------------|----------|----------------|
| **FP** | Grid of sprinkler heads at ceiling, max spacing per hazard class | NFPA 13 §8.6.2 (LH 4.6m), §8.6.3 (OH 4.0m) | Grid layout from room AABB, head count = `ceil(width/spacing) × ceil(depth/spacing)` |
| **ELEC** | Perimeter run at dado height (sockets), ceiling run (lights) | MS IEC 60364, IES lux tables | Two sub-routes per room: ceiling grid + wall perimeter |
| **CW** | Drop from ceiling to fixture positions (basin, sink, WC) | MS 1228 fixture unit table | Fixture type → position offset from room origin. `ad_fixture_type` table |
| **SP** | Drop from fixture to floor waste, connect to horizontal waste | MS 1228 §5 gradient tables | Gravity: fixture height → floor level. Gradient maintained on horizontal |
| **ACMV** | Ceiling diffuser grid, spacing per air changes/hour | ASHRAE 62.1, MS 1525 §6 | Diffuser count = `room_volume × ACH / diffuser_capacity`. Room height matters |
| **LPG** | Single drop from ceiling to gas point (kitchen range, heater) | MS 830 §4 | One connection point per room. Gas cock fitting at drop |

##### Data already available

`RoomTarget` has `ref`, `position`, `discipline`. `RoomDimensions` has
`widthMm`, `depthMm`, `heightMm`. This is enough for grid layouts
(FP sprinklers, ACMV diffusers) and perimeter runs (ELEC sockets).

What's **not** available: fixture positions within a room. CW needs to know
where the basin is. SP needs to know where the WC is. These come from the
BOM — `m_bom_line` LEAF elements with product category matching
`IfcSanitaryTerminal`, `IfcFlowTerminal`, etc. The BOM walker (CompileStage,
Step 3) already placed these elements with world coordinates.

##### Proposed: `BuildingGeometry.fixturesInRoom(roomRef, discipline)`

New query method on the interface:

```
record FixtureTarget(String ref, Point3D position, String ifcClass, String product)
List<FixtureTarget> fixturesInRoom(String roomRef, String discipline)
```

Implementation in `SqlBuildingGeometry`: query `elements_meta` or
`c_orderline` LEAF rows whose parent chain includes the room and whose
discipline matches. These are the ARC-placed elements from CompileStage —
real positions, not invented.

##### Branch sub-route composition

With fixture positions available, each discipline's branch becomes a
mini-route within the room:

**FP (sprinkler grid):**
```
enter room at ceiling Z → compute grid from AABB + NFPA spacing
  → FOLLOW to first row → BRANCH to each head position
  → FOLLOW to next row → BRANCH to each head position
```

**CW (fixture drops):**
```
enter room at ceiling Z → for each fixture:
  → FOLLOW along ceiling to fixture X,Y
  → BEND 90° down → FOLLOW to fixture Z (basin height ~850mm)
  → fitting: stop valve + connector
```

**SP (waste collection):**
```
for each fixture → DROP to floor (fixture Z → floor Z)
  → FOLLOW along floor to waste pipe (gradient 1:40)
  → connect to floor waste header
```

Each sub-route uses the same CrawlOps (FollowOp, BendOp, BranchOp) but
now with real target positions instead of "follow longest axis."

##### Phasing

Level 2 builds on Gaps 1–5:

| Phase | Prereq | Scope |
|-------|--------|-------|
| L2.1 | P118 (ceiling Z) | `fixturesInRoom()` query on BuildingGeometry |
| L2.2 | L2.1 | FP grid layout — sprinkler head spacing from NFPA 13 per room AABB |
| L2.3 | L2.2 | CW/SP fixture drops — real fixture positions from BOM |
| L2.4 | L2.3 | ELEC dual-route — ceiling lights + perimeter sockets |
| L2.5 | L2.4 | ACMV diffuser grid — air changes per room volume |

Each phase is one bounded task for a coder. FP grid (L2.2) is the natural
first because sprinkler spacing is the most formula-driven (NFPA 13 table
lookup from hazard class + room AABB → grid dimensions → head count).

### 6.12.1 Compilation Isolation Invariant (S103)

**Rule:** The DAGCompiler SHALL NOT open any extraction DB, IFC file, or `input/`
source during compilation. The only permitted connections are:

| Connection | Purpose | Direction |
|-----------|---------|-----------|
| `bom.db` (System.getProperty) | BOM recipes, C_OrderLine, ad_sysconfig | Read + Write |
| `library/ERP.db` | Shared discipline recipes, products, validation rules | Read-only |
| `output.db` | Compilation output (elements_rtree, c_orderline, system_edges) | Write |

**Verification:** GEO (PlacementCollectorVisitor.emitGeoSummary) opens the extraction
DB in a **separate proof stage** (read-only comparison). It cannot feed back into the
walk. GEO is an auditor, not a participant.

**Why this matters for LMP:** The tack chain in `m_bom_line.dx/dy/dz` was computed
at extraction time from IFC positions. At compilation time, the BOM walker reads
only `m_bom_line` — it never sees the IFC source. The BUILDING origin is the single
anchor point; everything below cascades parent-relative. No absolute borrowing.

**Enforcement:** Code review. No `*_extracted.db` or `*_input*` import exists in
`DAGCompiler/src/main/java/`. Any future addition must pass this gate.

### 6.12.2 MEP Placement — Shim + Joint Piece Architecture (S103)

MEP placement follows the same BOM principle as ARC/STR. No special routing
engine. No canvas. No pathfinding. The walker reads M_BOM → M_BOM_Line →
dx/dy/dz and places — same code path as placing furniture in a room.

The key insight: MEP elements attach to ARC/STR surfaces (ceilings, walls,
floors). The **shim** is the adapter piece that bridges the two BOMs without
coupling them.

#### 1. The Shim — MEP's First Piece

A shim is an M_Product in ERP.db. It represents a small patch of the host
surface that the MEP element attaches to. Like the plastic pin-cover that
comes with a light bulb — it has the exact shape of the connection interface.
The blind walker matches it to an ARC element by `ifc_class`, and the shim's
position becomes the MEP tack origin.

```
ERP.db M_Product — Shims (host surface adapters, ~10-15 types)

FP_CEILING_SHIM       host_ifc_class=IfcCovering   mount=BOTTOM   offset_mm=5
ELEC_CEILING_SHIM     host_ifc_class=IfcCovering   mount=BOTTOM   offset_mm=10
ELEC_WALL_SHIM        host_ifc_class=IfcWall       mount=SIDE     offset_mm=0  height_mm=1200
CW_CEILING_SHIM       host_ifc_class=IfcCovering   mount=BOTTOM   offset_mm=5
SP_FLOOR_SHIM         host_ifc_class=IfcSlab       mount=TOP      offset_mm=0
ACMV_CEILING_SHIM     host_ifc_class=IfcCovering   mount=BOTTOM   offset_mm=50
LPG_WALL_SHIM         host_ifc_class=IfcWall       mount=SIDE     offset_mm=0  height_mm=2100
```

The shim is the same scale as the first MEP piece — not room-sized, not
floor-sized. A small adapter at the exact attachment point. Without it,
every MEP piece would tack relative to the FLOOR origin with large offsets
(dx=15000, dy=8000) — the same tack overflow problem that broke TE before
room-level BOMs were added.

**Why the shim prevents geometry hell:** It absorbs the building-specific
position lookup ONCE. The shim sits at `ceiling_z - 5mm`. Every piece on
it uses small relative offsets (`dz=-300mm` for a sprinkler drop). Without
it, every piece computes `floor_z + storey_height - slab_thickness -
clearance - 300mm` individually — thousands of chances to drift.

#### 2. Shim Matching — Loose Coupling Between ARC and MEP

The shim matches to an ARC element at compile time. The match is by
`ifc_class` + position containment. ARC doesn't know MEP exists. MEP
doesn't reference the ARC BOM directly. The shim is the interface — like
OSGi: loosely coupled, highly cohesive.

```
Compile time:
  1. ARC BOM compiled → ceiling in KITCHEN at (3200, 1500, 2700) in *_BOM.db
  2. ad_space_type_mep_bom says: KITCHEN needs FP
  3. Callout creates DISC OrderLine: product = FP_CEILING_SHIM on this floor
  4. Walker matches shim to ARC ceiling by host_ifc_class=IfcCovering
     → ceiling at (3200, 1500, 2700), offset 5mm → shim at (3200, 1500, 2695)
  5. Walker recurses into shim's M_BOM children:
     PIPE_STRAIGHT tacks at dx=0 relative to shim
     SPRINKLER_HEAD tacks at dz=-300 → placed at (3200, 1500, 2395)
```

The shim IS the root BOM for MEP in this room. Matching resolves its
position. Everything below is standard BOM walk.

#### 3. The Toolbox — Joint Pieces in ERP.db

Joint pieces are the Lego bricks. Each is an M_Product with connection
properties. Extracted from IFC RosettaStones — the fleet teaches us what
pieces exist and their typical dimensions.

```
ERP.db M_Product — Joint pieces (~30-50 types from fleet)

PIPE_STRAIGHT_50MM     length=6000mm   diameter=50mm   ports=2 (END,END)
PIPE_ELBOW_90_50MM     angle=90°       diameter=50mm   ports=2 (END,END)
PIPE_TEE_50_25MM       main=50mm       branch=25mm     ports=3 (END,END,BRANCH)
PIPE_REDUCER_50_25MM   in=50mm         out=25mm        ports=2 (END,END)
SPRINKLER_HEAD_K56     k_factor=5.6    diameter=15mm   ports=1 (IN)
DUCT_STRAIGHT_300MM    width=300mm     height=200mm    ports=2 (END,END)
DUCT_TEE_300_150MM     main=300mm      branch=150mm    ports=3
AIR_DIFFUSER_600MM     size=600mm      capacity=150L/s ports=1 (IN)
```

**No `IfcRelConnectsPorts` needed.** Our RosettaStone IFCs do not carry port
connectivity data. The joint pieces are extracted from element geometry and
`ifc_class` — a pipe segment IS a straight piece, a `IfcPipeFitting` IS a
tee or elbow (identified by geometry shape or `PredefinedType`). The
RosettaStones teach us the vocabulary by example.

#### 4. The MEP Recipe — Shim First, Then Chain

The shim IS the root BOM for MEP in that room. No wrapper. No FP_SYSTEM
abstraction layer. Like the base plate in a Lego box — the first piece
you pull out, the one everything else snaps onto.

The Callout reads `ad_space_type_mep_bom`: "KITCHEN needs FP." It creates
a DISC OrderLine whose product IS `FP_CEILING_SHIM`. One shim per room
per discipline. The shim matches to the ceiling, gets its position, and
its M_BOM children are the pieces.

```
FLOOR (in *_BOM.db — from ARC extraction)
  └── DISC OrderLine: product = FP_CEILING_SHIM    ← the base plate IS the root
        └── PIPE_STRAIGHT_50MM      dx=0  dz=0     ← header from shim origin
        └── PIPE_TEE_50_25MM        dx=L  dz=0     ← tee at branch point
        └── PIPE_STRAIGHT_25MM      dy=S  dz=0     ← branch from tee
        └── SPRINKLER_HEAD_K56      dy=0  dz=-300   ← terminal drop
```

The walker recurses: FLOOR → FP_CEILING_SHIM → pieces. Same recursion
as FLOOR → ROOM → FURNITURE. The shim IS the room equivalent for MEP.
All children use small, local, verifiable offsets relative to it.

Multiple rooms needing FP = multiple shim OrderLines on the same floor:
```
FLOOR
  └── FP_CEILING_SHIM  (KITCHEN)    ← matched to kitchen ceiling
  └── FP_CEILING_SHIM  (CORRIDOR)   ← matched to corridor ceiling
  └── FP_CEILING_SHIM  (OFFICE)     ← matched to office ceiling
  └── ELEC_WALL_SHIM   (KITCHEN)    ← matched to kitchen wall at dado height
  └── SP_FLOOR_SHIM    (BATHROOM)   ← matched to bathroom floor
```

#### 5. What IFC RosettaStones Teach Us

The RosettaStones are the BOM ground truth. The IFC tells us what pieces
exist, their dimensions, and how they arrange — by example, not by port
connectivity.

| From IFC | To ERP.db | How |
|----------|-----------|-----|
| `IfcPipeSegment` elements | PIPE_STRAIGHT M_Products | Geometry → length, diameter |
| `IfcPipeFitting` elements | TEE/ELBOW/REDUCER M_Products | `PredefinedType` or shape classification |
| `IfcFlowTerminal` elements | SPRINKLER/DIFFUSER M_Products | `ifc_class` + dimensions |
| Element-to-storey containment | Shim host_ifc_class | `IfcRelContainedInSpatialStructure` |
| Element positions per room | Recipe arrangement (qty, spacing) | Mined patterns → AD_Val_Rule |

The fleet grows the vocabulary. TE contributes ~30 joint types.
Each new building adds patterns via `INSERT OR IGNORE`.

#### 6. Validation — Standards Confirm the Walk

NFPA 13, MS 1228, ASHRAE 62.1 etc. validate placement AFTER the walk:

| Standard | AD_Val_Rule check | Input |
|----------|-------------------|-------|
| NFPA 13 §8.6 | Sprinkler spacing ≤ 4600mm (LH) | Placed sprinkler positions |
| MS 1228 §5 | Waste gradient ≥ 1:40 | SP pipe segment Z values |
| ASHRAE 62.1 | Air changes per room | Diffuser count vs room volume |
| MS 830 §4 | Gas clearance ≥ 150mm | LPG pipe vs ignition sources |

Standards do NOT drive the walk. They confirm it. The BOM IS the walk.

#### 7. Phasing

| Phase | Scope | Prereq |
|-------|-------|--------|
| J1 | Joint piece vocabulary: extract M_Products from RosettaStone IFCs into ERP.db | S103 |
| J2 | Shim products: define host surface adapters in ERP.db (ceiling, wall, floor, slab) | J1 |
| J3 | MEP recipes: M_BOM assemblies (shim + joint pieces) in ERP.db | J2 |
| J4 | Shim matching: walker matches shim to ARC element by ifc_class + position | J3 |
| J5 | Validation: AD_Val_Rule for NFPA/MS/ASHRAE post-walk + P15/P16/P17 proofs | J4 |
| J6 | Fleet: TE + RM, measure coverage per discipline per room | J5 |

J2 is the critical task — without the shim, MEP has no tack origin.

#### Existing Infrastructure

| Component | Status | Role |
|-----------|--------|------|
| PlacementCollectorVisitor | DONE | Walks M_BOM → M_BOM_Line, accumulates dx/dy/dz — handles MEP identically to ARC |
| BomDropper | DONE | Explodes recipes recursively — handles MEP sub-BOMs identically to ARC |
| BuildingGeometry | DONE (S100) | Room dimensions, ceiling Z, wall thickness — input for shim matching |
| CrawlRouter + RouteBuilders | DONE (S100) | Generative fallback for buildings without IFC MEP data |
| system_edges | DONE (S100) | P15/P16/P17 proof input |
| M_BOM / M_BOM_Line | EXISTS (ERP.db) | Tables ready — needs joint piece and shim recipes |
| ad_element_mep | EXISTS (ERP.db, 12 rows) | Canonical MEP types with ports — connection interface |

### 6.13 IFC-Driven Extraction

**Status:** DONE (S100-p125, commit [3e056227](https://github.com/red1oon/BIMCompiler/commit/3e056227)). SH IFC-driven, FK scope box fallback.

#### The finding

The extraction pipeline (`ScopeBomBuilder`) assigns elements to SET BOMs
using YAML-authored scope boxes (`origin_m`, `aabb_mm`). This is manual —
the human defines rectangular containment volumes for each room zone.

But the IFC file already carries this information:

```
spatial_structure:
  IfcBuilding
    IfcBuildingStorey "Ground Floor"
      IfcSpace "1 - Living room"    ← 12 elements contained
      IfcSpace "2 - Bedroom"        ← 2 elements contained
      IfcSpace "3 - Entrance hall"  ← 0 elements
    IfcBuildingStorey "Roof"
      IfcSpace "4 - Roof"           ← 0 elements

rel_contained_in_space:   element_guid → space_guid (14 assignments)
rel_fills_host:           element_guid → host_guid  (7 door/window → wall)
```

Dry run on SH (58 elements): 14 elements assigned to spaces by IFC, 44
orphans (structural: walls, slabs, ceilings, curtain wall). The orphans
are correctly structural — not in any room.

#### Extraction flow

**IFC spatial containment (S100-p125):**
```
Read rel_contained_in_space from extracted.db
  → "1 - Living room" contains 12 elements
  → "2 - Bedroom" contains 2 elements
YAML maps: ifc_space "1 - Living room" → template SH_LIVING_SET
           ifc_space "2 - Bedroom" → template SH_BED_SET
VerbDetector groups within each IFC space
```

#### YAML format

```yaml
floor_rooms:
  Ground Floor:
    bom_id: FLOOR_SH_GF_STD
    product_category: GF
    spaces:
      - { ifc_space: "1 - Living room", template_bom: SH_LIVING_SET, role: LIVING, seq: 10 }
      - { ifc_space: "2 - Bedroom", template_bom: SH_BED_SET, role: MASTER, seq: 30 }
```

No `origin_m`, no `aabb_mm`. IFC spatial containment is the sole source
during extraction. YAML maps space names to BOM templates. Scope boxes are
an **Order processing** concern — the BIM Designer GUI and BOM Drop use
scope boxes when the user defines sub-room zones at order time (e.g.,
splitting a Living room into dining + seating zones).

For buildings without IfcSpace data, extraction groups by storey only
(existing `StructuralBomBuilder` behaviour). Sub-room grouping is deferred
to order time.

#### Impact on CLUSTER

IFC-driven extraction doesn't eliminate CLUSTER directly — the 6 dining
chairs are still 6 identical products in one space. But it changes the
extraction architecture from "sort by manual box" to "sort by IFC
containment" which:

1. Removes human coordinate authoring errors (wrong scope box origin)
2. Uses the architect's spatial intent (they modelled the IfcSpaces)
3. Enables IFC `rel_fills_host` for door/window→wall BOM nesting
4. Reduces YAML from ~15 lines per room to ~2 lines per room

#### Structural orphans

44 elements not in any IfcSpace are structural: walls (5), slabs (2),
ceilings (3), curtain wall (26), doors (3), windows (4), roof (1).

Doors and windows have `rel_fills_host` → they belong to their host wall.
Walls and slabs are floor-level structural → `StructuralBomBuilder` handles
them (unchanged).

The current extraction already handles orphans correctly —
`StructuralBomBuilder` picks up everything not assigned to a SET.

#### IfcRelAggregates extraction (S100-p126)

**Status:** DONE ([48d14537](https://github.com/red1oon/BIMCompiler/commit/48d14537))

New extraction table `rel_aggregates` captures IFC parent-child assembly
decomposition:

```sql
CREATE TABLE rel_aggregates (
    parent_guid TEXT NOT NULL,
    child_guid  TEXT NOT NULL,
    PRIMARY KEY (parent_guid, child_guid)
);
```

Populated from `IfcRelAggregates` relationships in the IFC file. Results:

- **SH:** 34 rows. 2 assemblies with 13 children each (curtain wall halves),
  1 with 3, 2 with 2, 2 singletons.
- **DX:** 38 rows. 2 assemblies with 10 children each (stair assemblies),
  2 with 5, 1 with 4, 3 singletons.

**Phantom parents:** Parent GUIDs have no entry in `elements_meta` because
`IfcCurtainWall` and `IfcStair` are not in the extraction class list. Assembly
structure is visible only via `rel_aggregates` join to `elements_meta` on
`child_guid`.

Java pipeline unchanged — `rel_aggregates` is read-only context for P129
(assembly BOMs).

#### Spatial container auto-discovery (S100-p127)

**Status:** DONE ([7745affd](https://github.com/red1oon/BIMCompiler/commit/7745affd))

`StoreyConfig` renamed to `SpatialContainerConfig` — abstract naming that
works for buildings (storeys) and infrastructure (segments).

**Auto-discovery:** When YAML `storeys:` section is empty, containers are
auto-discovered from `storeyElements` keys:

- Sort by min Z of their elements (seq: 1010, 1020, ...)
- `code` = generic abbreviation (first letter of each word, uppercase)
- No hardcoded name→code mapping — algorithm works for any building

Results:

- **SH:** 3 containers — Ground Floor→GF, Roof→RO, Unknown→UN
- **DX:** 5 containers — T/FDN→TF, Level 1→L1, Unknown→UN, Level 2→L2, Roof→RO

YAML `storeys:` kept as Order override (backward compat). Empty YAML =
auto-discover. BOM IDs change with auto-derived codes (e.g. `SH_ROOF_STR` →
`SH_RO_STR`) — opaque keys, no functional impact.

#### Scope excludes for CompositionBomBuilder (S100-p128)

**Status:** DONE ([f807bb3c](https://github.com/red1oon/BIMCompiler/commit/f807bb3c))

Elements assigned to SET BOMs by ScopeBomBuilder are excluded from
CompositionBomBuilder mirror partition. Fixes DX reconciliation delta +50→0
(furniture was double-counted in both SET BOMs and half-unit).

DX YAML `floor_rooms` removed — dead code since P125.

#### IFC assembly BOMs (S100-p129)

**Status:** DONE ([1153671c](https://github.com/red1oon/BIMCompiler/commit/1153671c))

StructuralBomBuilder reads `rel_aggregates` from extraction DB. Groups children
by parent GUID, creates ASSEMBLY BOMs for groups with 2+ children. MAKE lines
link FLOOR → ASSEMBLY. Elements not in any assembly stay as flat leaves.

- **SH:** 2 curtain wall assemblies (13 children each, factorized to 4 BOM lines)
- **DX:** 2 stair assemblies (3 children each)
- **FK/IN:** No rel_aggregates matches — zero regression

Phantom parents (IfcCurtainWall, IfcStair) have no `elements_meta` entry —
assembly structure visible only via `rel_aggregates` child_guid join.

---

---

*References:
[DISC_VALIDATE_SRS.md](DISC_VALIDATE_SRS.md) §9 (5-table LOD chain) |
[DocAction_SRS.md](DocAction_SRS.md) §1.3 (processIt DocEvent) |
[CALIBRATION_SRS.md](CALIBRATION_SRS.md) (DocEvent vs Terminal) |
[G4_SRS.md](G4_SRS.md) §2 (output.db pattern)*
