# ERP.db SRS — Discipline Validation Database
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md)

<div class="bim-banner" markdown>
<b>ERP.db holds discipline metadata AND compliance rules, separate from products and BOMs.</b> Schedules, placement rules, alias cascades, mined dimension rules, and validation rules (AD_Val_Rule, AD_Clash_Rule, AD_Occupancy_Class) — the HOW concern per [MANIFESTO](MANIFESTO.md) §Three Concerns.
</div>

**Version:** 1.3 (2026-03-31)
**Depends on:** [DISC_VALIDATE_SRS.md](DISC_VALIDATE_SRS.md) §9-10, [DocAction_SRS.md](DocAction_SRS.md) §1.3, [CALIBRATION_SRS.md](CALIBRATION_SRS.md)

---

## 1. Schema — ERP.db (44 tables)

**Authoritative DDL:** `migration/DV001_ERP_schema.sql`, `migration/DV003_element_mep_alias.sql`
**From-scratch rebuild:** `scripts/rebuild_erp.sh` (DV001→DV036 + W019, zero manual SQL)

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
| `ad_verb_pattern` | pattern_id | 9 | Verb detection hints: (product_type, ifc_class) → expected verb (DV035) |
| `ad_element_product_alias` | alias_id | 79 | Abstract product naming: ifc_class/element_name → product_id (DV034) |
| `ad_mep_anchor` | anchor_id | 0 | MEP anchor points extracted by IFCtoERP (W019, runtime populated) |
| `ad_mep_pattern` | pattern_id | 0 | MEP topology patterns mined by RouteWalker (W019, runtime populated) |

`M_Product_Category` carries `AD_Org_ID` (DV036) wiring the discipline chain:
`M_Product → M_Product_Category → AD_Org_ID` per §6.4. 127 seed categories
(IFC leaf classes + discipline parents + floor/room codes). Products auto-categorized
by `ProductRegistrar.ensureProductCatalog()` from `ifc_class` lookup.

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
| W-TACK-STABLE | Tack chain FP error ≤ 0.005mm per pair across all fleet buildings (evidence: ≤ 0.002mm, 1,653 pairs) | GEO gate G3-DIGEST |

---

## 6. AD_Org ↔ M_Product_Category — The Abstract Discipline Model

### Core coupling

Every IFC product belongs to a **category** (WHAT it is) and every category
belongs to a **discipline** (WHO manages it). One FK each — no strings, no
switch statements, no per-building logic.

```
AD_Org (discipline)          M_Product_Category (taxonomy)        M_Product
┌──────────────┐             ┌───────────────────────┐            ┌──────────────┐
│ AD_Org_ID: 1 │◄── AD_Org_ID ──│ IFC_WALL            │◄── Cat_ID ──│ WALL_EXT_290 │
│ Value: ARC   │             │ IFC_DOOR              │            │ DOOR_INT_810 │
│              │             │ IFC_WINDOW            │            │ ...          │
├──────────────┤             │ IFC_SLAB  ...13 total │            └──────────────┘
│ AD_Org_ID: 2 │◄────────────│ IFC_BEAM              │
│ Value: STR   │             │ IFC_COLUMN ...6 total │
├──────────────┤             ├───────────────────────┤
│ AD_Org_ID: 3 │◄────────────│ IFC_FIRESUPPTERM      │
│ Value: FP    │             │ IFC_ALARM    ...2 total│
├──────────────┤             ├───────────────────────┤
│ AD_Org_ID: 4 │◄────────────│ IFC_LIGHTFIXTURE      │
│ Value: ELEC  │             │ IFC_OUTLET   ...6 total│
├──────────────┤             ├───────────────────────┤
│ AD_Org_ID: 10│◄────────────│ IFC_DUCTSEGMENT       │
│ Value: MEP   │             │ IFC_PIPESEGMENT       │
│              │             │ IFC_VALVE    ...9 total│
├──────────────┤             ├───────────────────────┤
│ Value: ACMV  │◄────────────│ IFC_AIRTERMINAL       │
│ Value: CW    │◄────────────│ CW (parent)           │
│ Value: SP    │◄────────────│ IFC_SANITARYTERM      │
│ Value: LPG   │◄────────────│ LPG (parent)          │
│ Value: *     │◄────────────│ ASM (assembly)        │
└──────────────┘             └───────────────────────┘
```

**49 categories** are linked to disciplines (DV036). Any IFC file — residential,
commercial, industrial — drops products into these categories via `ifc_class`
lookup. The discipline resolves automatically: `M_Product.ifc_class → M_Product_Category.IFC_Class → AD_Org_ID`.

### Why this scales

The coupling is **industry-level**, not building-level. A residential house (RE)
and a commercial terminal (CO) share the same 49-category discipline map.
Adding a new industry vertical means adding IFC class categories, not code.

| Archetype | Example buildings | Disciplines exercised |
|-----------|-------------------|----------------------|
| RE (Residential) | SH, DX, FK | ARC, STR, MEP |
| CO (Commercial) | TE (Terminal) | ARC, STR, FP, ELEC, ACMV, CW, SP, LPG |
| IN (Infrastructure) | RD, RL | ARC, STR |

**Archetype patterns** (§6.12.3) are also abstract — CW_TERMINAL_01 was mined
from a commercial terminal but describes universal cold-water topology
(meter → junction → junction → fixture). Any building's anchors match against it.

### What AD_Org replaces

All former string-based discipline resolution collapses to one FK:

- `m_bom.bom_category` string → `AD_Org_ID` FK on M_Product_Category
- `C_OrderLine.Discipline` string → `AD_Org_ID` FK
- Scattered `resolveDiscipline(ifcClass)` logic → single FK lookup

### Forensic verification

The pipeline emits a `[FORENSIC] DISC CHAIN` log line after every compilation
showing the live AD_Org ↔ M_Product_Category coupling with product counts.
Any regression (uncategorized products, missing disciplines) triggers a WARN.

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

> **Cardinal rules (also in class Javadoc):**
> - **MEP_RECIPE is abstract and reusable.** It encodes a geometric pattern (standoff,
>   chain geometry). Never explode recipe runs into per-instance LEAF rows in compilation.
> - **Validation Rules drive final expression.** DV rules (AD_Rule / M_BOM Validation
>   Layer) resolve a recipe to a specific project. The callout only registers scope and Qty.
> - **One DISCIPLINE row per discipline.** Qty = IFC element count from extraction
>   (ad_sysconfig MEP_*_COUNT), not recipe archetype count.
> - **LEAF children from *_SYSTEM BOM only** (FP_RISER, FP_SPRINKLER_LAYOUT, etc.).
>
> See: `OrderLineProductCallout.java` class Javadoc, `IFCtoERP.java` class Javadoc.

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

#### 6.3.1 Current DV Rule Coverage — MEP Gap

Audit findings (S104-closeout, 2026-04-01). Queries run against `library/ERP.db`.

**Stage 1 — AD_DocEvent_Rule (8 rows total):**
- 7 rows at AD_Org_ID=0 (blanket/ARC): UBBL dimension rules (room area, kitchen dim,
  corridor width, staircase width, ceiling height, bathroom area, egress distance).
  All `FiringEvent=BEFORE_COMPILE`. CheckMethods: MIN_AREA, MIN_DIMENSION, MIN_WIDTH,
  MIN_HEIGHT, MAX_DISTANCE.
- 1 row at AD_Org_ID=3 (FP): `UBBL_PVII_SPRINKLER_SPACING` — SPACING, MAX_SPACING.
- **Gap:** ACMV, CW, ELEC, SP, LPG have zero Stage 1 DocEvent rules. §6.3 declares
  "FP: NFPA 13 spacing, UBBL fire rating, general pipe sizing rules" — only UBBL
  sprinkler spacing is present. NFPA 13 coverage area, pipe sizing, flow rate rules
  are absent from AD_DocEvent_Rule.

**Stage 3 — ad_val_rule (415 rows):**
- All rows are `check_method='DIMENSION_RANGE'` (physical dimension observations
  mined from 34 buildings). No AD_Org_ID column — mapped by ifc_class.
- MEP IFC classes covered: IfcAirTerminal (3), IfcDuctFitting (4), IfcDuctSegment (4),
  IfcFireSuppressionTerminal (1), IfcFlowController (5), IfcFlowFitting (10),
  IfcFlowSegment (11), IfcFlowTerminal (29). Total MEP dimension rules: ~67 rows.
- These are mined dimension ranges (W/D/H observations), not code requirement checks.
  No spacing limits, flow rates, clearance requirements, or coverage area rules.
- **Gap:** No Stage 3 rules that enforce NFPA, IMC, IPC, NEC, UBBL MEP clauses.

**Hardcoded Java thresholds (geometric heuristics, not code requirements):**
- `RoutingConstraints.java` (all values extracted from PATTERN G3, IFC-mined):
  - Wall clearance by discipline: ACMV 175mm, FP 173mm, CW 216mm, SP 223mm, LPG 106mm
  - Ceiling clearance averages and minimums per discipline
  - 50mm wall clearance tolerance (`isValidWallClearance` line 101)
  - 10mm ceiling clearance tolerance (`isValidCeilingClearance` line 116)
- `FederationConstants.java:94`: MEP-to-structure minimum clearance constant
- Assessment: these are geometric routing heuristics from observed IFC data, **not**
  codified requirements. They belong in `RoutingConstraints.java` or could migrate
  to `ad_code_requirement` but are not missing from any DV stage.

**ad_code_requirement table (23 rows):**
- Populated with real code requirements: NFPA_13 (sprinkler spacing/coverage by room),
  NEC_2020 (outlet spacing, GFCI, lighting, switch clearance), IMC_2021 (exhaust fan),
  IPC_2021 (sink/toilet), MS_1184 (door/toilet), MS_1228 (floor trap), NFPA_101
  (emergency lighting distance).
- Schema has: `max_spacing_m`, `min_clearance_m`, `coverage_radius`, `qty_per_area` etc.
- **Not yet connected to DV rule generation.** No query path from `ad_code_requirement`
  → `AD_DocEvent_Rule` or `ad_val_rule`. This table is the intended home for NFPA/UBBL
  MEP code requirements (confirmed by schema), but migration to Stage 1 is not done.

**Conclusion:** MEP DV rule coverage is a real gap. Stage 1 has 1 FP rule (UBBL sprinkler
spacing). Stages 2–3 have no MEP code requirement checks. `ad_code_requirement` has the
right data but is not wired into the validation pipeline. A future migration prompt should
bridge `ad_code_requirement` → `AD_DocEvent_Rule` for each discipline's code references.

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

### 6.12.2 MEP Placement — Shim + Joint Piece Architecture (S103/S104)

**There is no difference between ARC placement and MEP placement.** The walker
reads M_BOM → M_BOM_Line → dx/dy/dz and accumulates. Same code path for
placing a wall, a desk, a pipe tee, or a sprinkler head. Same
PlacementCollectorVisitor. Same maths. Same GEO proof. No routing engine,
no canvas, no pathfinding, no runtime computation.

#### 1. Tack Point — Same as ARC

A tack point is just a point. Parent tack + dx/dy/dz = child tack. The
walker accumulates. That's the whole algorithm — same for ARC, same for MEP.

The tack point is NOT tied to AABB or LBD convention. What the point
physically represents depends on the product — LBD corner of a wall,
centre of a pipe end, centre of a screw hole. The BOM doesn't care. It
stores a number. The walker adds numbers. The product's geometry knows
where to render relative to its tack point.

**Tack stability invariant:** `PlacementCollectorVisitor` computes each
element's world absolute tack as:

```
child_abs = parent_abs + rotated(dx, dy, dz) + bomOrigin
```

where `parent_abs` is the immediately preceding ancestor's absolute tack,
read from the anchor stack (`anchorStack.peek()`). Computation is O(1) per
node — no recomputation from root, no mutable running counter. The stack is
the cache of all ancestor absolute tacks; each node reads only its direct
parent. Accumulated FP error across a chain is bounded by the GEO proof
(evidence: ≤ 0.002mm over 1,653 element pairs, SH/TE/RM fleet). The
tolerance for GEO gate passage is ≤ 0.005mm per pair (LMP threshold).

#### 2. The Shim — Zero Offset Host Alignment

The shim is a **phantom product** — no geometry, no mass, not rendered.
It represents the host surface where MEP attaches. The shim **melts into
the host**: its position IS the host surface position. Zero offset.

A shim placed against a wall has the wall's XYZ. A shim placed against a
ceiling has the ceiling's XYZ. No gap, no clearance, no 5mm standoff.
The shim IS the surface in coordinate terms.

The device that attaches to the host (pipe bracket, sprinkler mount)
carries its own standoff intrinsically. A bracket that sits 5mm proud
of the wall? That 5mm is in the device's M_BOM_Line dx/dy/dz within the
recipe — the product knows its own offset. The shim contributes nothing
except the coordinate frame origin at the host surface.

```
ERP.db M_Product — Shims (phantom host surface anchors, ~10-15 types)

FP_CEILING_SHIM       host_ifc_class=IfcCovering   mount=BOTTOM
ELEC_CEILING_SHIM     host_ifc_class=IfcCovering   mount=BOTTOM
ELEC_WALL_SHIM        host_ifc_class=IfcWall       mount=SIDE
CW_CEILING_SHIM       host_ifc_class=IfcCovering   mount=BOTTOM
SP_FLOOR_SHIM         host_ifc_class=IfcSlab        mount=TOP
ACMV_CEILING_SHIM     host_ifc_class=IfcCovering   mount=BOTTOM
LPG_WALL_SHIM         host_ifc_class=IfcWall       mount=SIDE
```

The shim's position comes from the MAKE line dx/dy/dz in the BOM tree —
same as any other BOM line. No runtime host matching. No query. No
ShimMatcher computation. The position was extracted from the IFC once and
stored as a number on the M_BOM_Line.

#### 3. Entry Point — OrderLine → MEP_System BOM (abstract, VR-resolved)

The MEP walk enters through an OrderLine whose Product has AD_Org = discipline
(FP, ELEC, ACMV, CW, SP, LPG). That product IS the root MEP_System BOM in
ERP.db. BomDropper explodes it — finds the shim (phantom parent) — recurses
into children. Same explosion as ARC OrderLine → BUILDING BOM → FLOOR → ROOM.

```
OrderLine (AD_Org=FP, Product=FP_CEILING_SHIM)
  → BomDropper explodes FP_CEILING_SHIM M_BOM
    → children: PIPE_STRAIGHT_50MM, PIPE_TEE_50_25MM, SPRINKLER_HEAD_K56...
      → walker accumulates dx/dy/dz for each child
```

Each child is identified by **exact product ID** — no fuzzy matching, no
proximity search. The recipe says `PIPE_STRAIGHT_50MM_POLY_STEEL`, that's
what gets placed.

#### 4. Joint Pieces — The Toolbox

Joint pieces are M_Products in ERP.db. Extracted from RosettaStone IFCs
by `ifc_class` and `PredefinedType` — a pipe segment IS a straight piece,
an `IfcPipeFitting` IS a tee or elbow. No `IfcRelConnectsPorts` needed
(our IFCs don't carry port data). The fleet teaches vocabulary by example.

Each piece in `component_library.db` carries LOD metadata for orientation:
- `attachment_face` — CENTER / TOP / BOTTOM / SIDE
- `up_axis` — which local axis points up (Z)
- `forward_axis` — which local axis is the run direction (Y or X)
- `orientation` — PENDANT / UPRIGHT / HORIZONTAL / VERTICAL / MIXED

This is facing data only — no tack-in/tack-out ports. Rotation from tack
point is on `M_BOM_Line.rotation_rule`. The walker reads it, applies it.
The piece itself is the simplest BOM — one product with orientation metadata.

#### 5. The MEP BOM — Shim Root, Joint Piece Children

```
FLOOR (in *_BOM.db)
  └── FP_CEILING_SHIM (KITCHEN)     ← phantom, melts into ceiling XYZ
        ├── PIPE_STRAIGHT_50MM      dx=0  dz=-5    ← 5mm below ceiling (device offset)
        ├── PIPE_TEE_50_25MM        dx=L  dz=-5    ← tee at branch point
        ├── PIPE_STRAIGHT_25MM      dy=S  dz=-5    ← branch from tee
        └── SPRINKLER_HEAD_K56      dy=0  dz=-305   ← terminal drop (300mm pendant)

FLOOR
  └── FP_CEILING_SHIM  (KITCHEN)     ← one shim per room per discipline
  └── FP_CEILING_SHIM  (CORRIDOR)
  └── ELEC_WALL_SHIM   (KITCHEN)
  └── SP_FLOOR_SHIM    (BATHROOM)
```

The walker recurses: FLOOR → shim → pieces. Same as FLOOR → ROOM →
FURNITURE. All dx/dy/dz are small, local, verifiable — the shim absorbed
the building-scale positioning. Device standoffs are intrinsic to each
child's BOM line offset — the shim contributes zero.

#### 6. InterimWorkshop — Variable-Length Pieces

A pipe run between two fittings may need a non-standard length. Like a
real contractor who cuts pipe from stock to fit the gap.

##### BOM Line Model Extension (from Compiere/e-Evolution Manufacturing)

The M_BOM_Line gains `c_uom_id` — the Unit of Measure from iDempiere.
`qty` becomes REAL (was INTEGER). The UOM tells the walker how to
interpret qty:

| c_uom_id | qty meaning | Walker action |
|----------|-------------|---------------|
| EA | Instance count (default) | expandVerb() — TILE/ROUTE/CLUSTER/etc |
| MM | Length in millimetres | InterimWorkshop — recompute primitive |
| M | Length in metres | InterimWorkshop — recompute primitive |

When `c_uom_id` is a length unit (MM/M), the walker calls
InterimWorkshop instead of expandVerb(). No CUT verb needed — the
**UOM is the signal**. This follows Compiere convention where BOMQty +
C_UOM_ID drive the BOM Drop interpretation.

`qty_type` (VARIABLE/FIXED) guards the line: VARIABLE allows runtime
adjustment, FIXED rejects it. A tee fitting is FIXED (tack i/o
predetermined). A straight pipe is VARIABLE (outlet shifts with length).

##### Mathematical Primitives, Not Parametric

MEP pieces are mathematical primitives — cylinder, box, torus arc.
The workshop does not stretch or deform an existing mesh. It recomputes
the primitive from first principles:

```
Cylinder(diameter=50mm, length=2345mm, LOD=16 faces)
→ vertices: r*cos(θ), r*sin(θ), z ∈ [0, 2345mm]
→ 16 side faces + 2 end caps
```

This is NOT parametric mesh generation (which is forbidden). Parametric
means exploring design space with arbitrary parameters. This is geometry
from data — same principle as the entire compiler. The LOD (face count,
tessellation) comes from the library template. The dimensions come from
the BOM line. The maths produces the mesh.

Materials are flat RGBA per face (no UV mapping, no textures). A shorter
cylinder has shorter rectangular side faces with the same uniform colour.
No stretching, no shrinking artifact.

##### InterimWorkshop Interface

```java
/**
 * Runtime mesh computation for variable-length pieces.
 * Like a contractor's workshop: recompute the primitive at the required
 * length. LOD from library template. Dimensions from BOM line.
 * Result is ephemeral — not stored back.
 */
public class InterimWorkshop {
    /** Recompute primitive mesh at target length along forward axis. */
    static MeshResult recompute(MProduct product, String forwardAxis,
                                double targetLengthMm, int lodFaceCount);
}
```

##### Workshop Extension — Envelope Trim (BBC §6.1)

InterimWorkshop §6 above handles 1D length adjustment (pipes). The same
pattern extends to 2D/3D fabrication — trimming products against the
building envelope (roof, ground, perimeter walls).

**Evidence (S143):** SH Sample House has 4 elements overshooting the
curved roof (Z=3.0m base): WALL_EXT_NS at 3.828m (+828mm), WALL_EXT_EW
at 3.291m (+291mm), curtain wall at 3.221m (+221mm). These are correct
rectangular LODs from IFC extraction — the trim is a workshop operation.

**Workshop sub-verb model:**

| Sub-verb | c_uom_id | Workshop action |
|----------|----------|-----------------|
| (none) | EA | No workshop — place as-is |
| (none) | MM/M | Length recompute (current InterimWorkshop) |
| `CUT_TOP` | EA | Trim upper extent to constraining surface |
| `CUT_BOTTOM` | EA | Trim lower extent to ground/slab |
| `NOTCH` | EA | Rectangular cutout for penetration |

Sub-verb is stored on `M_AttributeSetInstance` (per-instance, not per-product).
The catalog product stays rectangular. Each placed instance gets an ASI with:
`cut_face`, `cut_ref_bom_id` (constraining element), `cut_profile` (FLAT/CURVED/PITCHED),
`cut_offset_mm` (overshoot depth).

**Envelope pass:** After placement, a single pass identifies all elements whose
AABB overshoots the building envelope (roof, ground). For each overshoot, it
generates an ASI with the cut instruction. The constraining element (roof BOM)
defines the cut profile. This is the same pattern as Compiere's BOM Drop +
ASI resolution — placement first, then per-instance attributes.

##### Flat Sibling Pattern — Small BOMs

MEP pieces under the shim are flat siblings, not nested chains. Each
is a 1-2 piece mini-BOM — like a contractor's work packet:

```
FP_CEILING_SHIM (KITCHEN)
  ├── PIPE_STRAIGHT_50MM  dx=0     qty=2345 c_uom_id=MM  qty_type=VARIABLE
  ├── PIPE_TEE_50_25MM    dx=2.345 qty=1    c_uom_id=EA  qty_type=FIXED
  ├── PIPE_STRAIGHT_50MM  dx=2.345 qty=1800 c_uom_id=MM  qty_type=VARIABLE
  ├── PIPE_TEE_50_25MM    dx=4.145 qty=1    c_uom_id=EA  qty_type=FIXED
  └── SPRINKLER_HEAD_K56  dx=2.345 dy=0.5 dz=-0.3 qty=1 c_uom_id=EA
```

Fittings are at fixed positions (from spacing rules / extraction).
Straight pipes fill gaps between fittings — their length (qty in MM)
is exactly the gap. Each sibling is independently tacked from the shim.
Shortening one pipe does NOT cascade to neighbours — the next fitting's
dx is independently defined.

The tack i/o for a VARIABLE piece: inlet = dx (BOM line offset from
parent). Outlet = dx + qty(mm) along forward_axis. The next sibling's
dx equals that outlet point. Deterministic, no drift.

#### 7. IFCtoERP — Tack Extraction

Tack offsets between joined MEP pieces are extracted by IFCtoERP while
reading RosettaStone IFC geometry. Only at extraction time are both
pieces' positions available. The maths: child_pos - parent_pos = dx/dy/dz.
Same subtraction as ARC walls and slabs.

IFCtoERP writes directly to ERP.db (no intermediate database):
- Joint piece M_Products (piece type + diameter from AABB)
- Shim M_Products (phantom, per discipline x host surface type)
- M_BOM recipes (shim-rooted, children with dx/dy/dz tack offsets)

The dx/dy/dz on each M_BOM_Line IS the vector from parent tack to child
tack. Extracted once. Stored as data. Walked as numbers.

#### 8. Slope, Branching, and Risers — Three Triaged Edge Cases

These three patterns are not new walker code. They are **data on the
BOM line** — the walker already handles them through existing mechanisms
(dz accumulation, sub-assemblies, rotation). The triage closes each gap
by defining how the metadata tables express the pattern.

##### 8a. Slope / Gradient (Sanitary Drainage)

MS 1228 §5 requires waste pipes at minimum 1:40 gradient (25mm drop per
metre). This is NOT a runtime computation — the gradient is baked into
each successive pipe segment's dz on the M_BOM_Line.

```
SP_FLOOR_SHIM (BATHROOM)
  ├── PIPE_STRAIGHT_50MM  dx=0     dz=0       qty=1000 c_uom_id=MM
  ├── PIPE_STRAIGHT_50MM  dx=1.0   dz=-0.025  qty=1000 c_uom_id=MM  ← 25mm drop
  ├── PIPE_STRAIGHT_50MM  dx=2.0   dz=-0.050  qty=1000 c_uom_id=MM  ← 50mm drop
  └── FLOOR_TRAP          dx=0.5   dz=-0.010  qty=1    c_uom_id=EA
```

The gradient is expressed as metadata in `ad_mep_laying_rule`:

| rule_id | discipline | rule_type | parameter | value | standard | section |
|---------|------------|-----------|-----------|-------|----------|---------|
| LAY-SP-001 | SP | GRADIENT | min_slope | 0.025 | MS 1228 | §5.3 |
| LAY-SP-002 | SP | GRADIENT | max_slope | 0.040 | MS 1228 | §5.3 |
| LAY-LPG-001 | LPG | GRADIENT | min_slope | 0.010 | MS 830 | §4.2 |

The IFCtoERP extraction computes per-segment dz from IFC positions.
The validation rule (AD_Val_Rule) checks placed pipe Z-deltas against
the table. The code is abstract — it reads `min_slope` from the table,
compares against placed dz/dx ratio. No hardcoded gradients.

##### 8b. Branching at Tees

A tee fitting has 3 connections: main-in, main-out, branch-out. The
main run continues as flat siblings. The branch is a **nested sub-BOM**
under the tee — the tee IS a sub-assembly with its own children.

```
FP_CEILING_SHIM (KITCHEN)
  ├── PIPE_STRAIGHT_50MM   dx=0      qty=2345 c_uom_id=MM   ← main run
  ├── PIPE_TEE_50_25MM     dx=2.345  qty=1    c_uom_id=EA   ← tee (sub-assembly)
  │     └── PIPE_STRAIGHT_25MM  dy=0  qty=1500 c_uom_id=MM  ← branch child
  │     └── SPRINKLER_HEAD_K56  dy=1.5 dz=-0.3 qty=1 c_uom_id=EA
  ├── PIPE_STRAIGHT_50MM   dx=2.345  qty=1800 c_uom_id=MM   ← main run continues
  └── PIPE_TEE_50_25MM     dx=4.145  qty=1    c_uom_id=EA   ← next tee
```

The tee's branch children have dx/dy/dz relative to the tee's tack
point (centre of the fitting). The walker recurses into the tee sub-BOM
(onSubAssembly), walks its children, exits (onSubAssemblyComplete), then
continues with the next main-run sibling. Standard BOM recursion — no
branching-specific code.

Branch metadata in `ad_mep_fitting_rule`:

| rule_id | piece_type | connection | axis | offset_rule | standard |
|---------|------------|------------|------|-------------|----------|
| FIT-TEE-001 | PIPE_TEE | MAIN_OUT | X | diameter/2 from centre | — |
| FIT-TEE-002 | PIPE_TEE | BRANCH_OUT | Y | diameter/2 from centre | — |
| FIT-ELB-001 | PIPE_ELBOW | OUT | varies | angle × bend_radius | — |
| FIT-RED-001 | PIPE_REDUCER | OUT | X | length along axis | — |

The table defines each fitting's connection offsets. IFCtoERP reads
these when building M_BOM recipes. The walker doesn't know about fittings
— it just sees sub-assemblies with dx/dy/dz.

##### 8c. Vertical Risers

A riser is a pipe running vertically through floors. The BOM line
carries `rotation_rule` = 90° around the horizontal axis, rotating the
pipe's forward_axis from Y (horizontal default) to Z (vertical).

```
BUILDING
  └── FP_RISER_ASSEMBLY (bom_type=MEP)
        ├── PIPE_STRAIGHT_50MM  dz=0     rotation_rule=PI/2  qty=3000 c_uom_id=MM
        ├── PIPE_STRAIGHT_50MM  dz=3.0   rotation_rule=PI/2  qty=3000 c_uom_id=MM
        └── PIPE_STRAIGHT_50MM  dz=6.0   rotation_rule=PI/2  qty=3000 c_uom_id=MM
```

The walker's existing rotation stack handles this — cumulative rotation
is pushed on onSubAssembly, applied to dx/dy/dz in onLeaf, popped on
exit. InterimWorkshop recomputes along the library's forward_axis (Y);
the walker's rotation transforms the half-extents to world frame (Z).

Riser metadata in `ad_mep_riser_rule`:

| rule_id | discipline | parameter | value | standard | section |
|---------|------------|-----------|-------|----------|---------|
| RSR-FP-001 | FP | max_floor_height_mm | 4000 | NFPA 13 | §8.15 |
| RSR-FP-002 | FP | riser_diameter_mm | 65 | NFPA 13 | §8.15.1 |
| RSR-CW-001 | CW | min_pressure_kpa | 150 | MS 1228 | §3.4 |
| RSR-SP-001 | SP | stack_min_diameter_mm | 100 | MS 1228 | §5.7 |

##### 8d. Route Direction → Piece Orientation (Triage)

**Context:** In `_BOM.db` (ARC/STR), orientation is inherited from the parent BOM
hierarchy — BUILDING → FLOOR → ROOM → LEAF. Each level carries dx/dy/dz forming
a spatial chain. MEP mini BOMs in ERP.db have no such parent hierarchy. The
mini BOM is standalone; bom-to-bom connection is via anchors, not tree nesting.

**Extracted path (§6.12.2):** No gap. `buildMepBomRecipes()` extracts `rotation_rule`
from IFC direction changes (atan2 of perpendicular vs along-axis). Each piece's
dx/dy/dz encodes the run direction implicitly. The Walker reads `rotation_rule`
from M_BOM_Line and applies it via the rotation stack. Complete.

**Generated path (§6.12.3):** Gap. RouteWalker generates CW/SP c_orderline rows
from pattern steps. Each step has `direction_axis` (X, Y, Z, GRADIENT). But
RouteWalker does NOT set `rotation_rule` on generated lines. For horizontal
runs (default), this works (rotation_rule defaults to 0, forward_axis aligned
with run). For vertical drops or direction changes, rotation_rule is missing.

**Walk direction per discipline** (from BBC.md §3.6):

| Discipline | Walk direction | Implication |
|------------|---------------|-------------|
| CW | Ground up ↑ then horizontal → | Riser needs rotation_rule=PI/2 |
| SP | Top down ↓ (gravity) | Gradient pieces: rotation_rule from dz/dx |
| FP | Vertical ↑ then horizontal → | Same as CW |
| ELEC | Vertical ↑ then radial → | Radial = per-room walk direction varies |
| ACMV | Horizontal from AHU → | Usually single-axis per floor |
| LPG | Horizontal from meter → | Single-axis, no vertical |

This table is spec text only — not stored in any runtime-accessible table.

**Three gaps to close:**

1. **RouteWalker must set rotation_rule** on generated lines when direction_axis
   changes between consecutive steps (horizontal → vertical, or axis change).
   Computable: if step N is X-axis and step N+1 is Z-axis, the transition
   piece gets rotation_rule = PI/2.

2. **Walk direction as metadata:** The per-discipline walk direction (BBC.md §3.6
   table) should be stored on the system BOM or on ad_mep_laying_rule so the
   Walker can read it at runtime. Candidate column: `walk_direction` on M_BOM
   WHERE AD_Org_ID > 0.

3. **Piece orientation alignment:** At runtime, the Walker must align each piece's
   `forward_axis` (from component_library.db) with the route direction. This is
   a rotation: `rotation = angle_between(forward_axis, route_direction)`. The
   existing rotation stack handles it — but the route_direction must be available
   as data, not inferred.

**Resolution sequence:** Gap 2 first (store walk direction as data), then Gap 1
(RouteWalker sets rotation_rule), then Gap 3 (Walker aligns forward_axis). All
three are data additions — no new walker logic beyond reading existing columns.

#### 9. Metadata Tables — Standards as Data, Code as Abstract Walker

All laying rules, fitting rules, and riser rules live in ERP.db as
metadata tables. The code reads them. The code never embeds standard
numbers, gradient values, spacing limits, or diameter rules.

```
ERP.db metadata tables (read by walker + validation, never by user):

ad_mep_laying_rule     — slope gradients, clearances, material rules
ad_mep_fitting_rule    — connection offsets per fitting type
ad_mep_riser_rule      — vertical run constraints per discipline
ad_val_rule            — post-walk validation (existing, P15/P16/P17)
ad_space_type_mep_bom  — room type → discipline mapping (existing, 186 rows)
```

**Why this matters for usability:** A user (engineer, project manager)
can open ERP.db, read the laying rule table, see "MS 1228 §5.3: min
slope = 0.025", and understand exactly what the system enforces. No
source code reading required. Changing a regulation = updating a row
in the table, not editing Java. The compiler is truly user-friendly
when the rules are visible metadata and the code is an abstract walker.

**Validation flow:**
1. Walker places pieces (accumulates dx/dy/dz, calls InterimWorkshop)
2. AD_Val_Rule reads placed positions from output.db
3. Compares against `ad_mep_laying_rule` / `ad_mep_riser_rule` thresholds
4. Emits PASS/FAIL per rule per element — no hardcoded thresholds in code

#### 10. Validation — Standards Confirm the Walk

Standards validate placement AFTER the walk. They do NOT drive it.

**Validation layering (three tiers):**

| Tier | Mechanism | When | Scope |
|------|-----------|------|-------|
| 1 — GEO proof | G3-DIGEST gate (W-TACK-STABLE) | Per RosettaStone run | Tack FP stability ≤ 0.005mm across all fleet elements |
| 2 — CheckClashVerb (primary) | AD_Val_Rule clash rules | Post-walk audit pass | Cross-discipline penetration; host-surface clearance |
| 3 — Per-element soft check (supplementary) | PlacementValidator.isValidPlacement() | During BOM walk, per leaf | Room-bounds overreach; early-warning log only, no throw |

Tier 3 is **supplementary** — it logs early warnings but does not replace Tier 2.
CheckClashVerb remains the primary clearance enforcement mechanism. A Tier 3
violation is a warning; a Tier 2 violation is a FAIL. Tier 3 MUST NOT be
promoted to a hard fail without first confirming CheckClashVerb cannot detect
the same violation.

| Standard | AD_Val_Rule check | Source table | Input |
|----------|-------------------|-------------|-------|
| NFPA 13 §8.6 | Sprinkler spacing ≤ 4600mm (LH) | ad_mep_laying_rule | Placed sprinkler positions |
| NFPA 13 §8.15 | Riser diameter ≥ 65mm | ad_mep_riser_rule | Riser product dimensions |
| MS 1228 §5.3 | Waste gradient ≥ 1:40 | ad_mep_laying_rule | SP pipe dz/dx ratio |
| MS 1228 §3.4 | Min pressure at highest fixture | ad_mep_riser_rule | Riser height vs pressure |
| ASHRAE 62.1 | Air changes per room | ad_mep_laying_rule | Diffuser count vs room volume |
| MS 830 §4.2 | Gas clearance ≥ 150mm | ad_mep_laying_rule | LPG pipe vs ignition sources |
| MS 830 §4.2 | Gas pipe gradient ≥ 1:100 | ad_mep_laying_rule | LPG pipe dz/dx ratio |

#### 11. Phasing

| Phase | Scope | Prereq |
|-------|-------|--------|
| J1 | IFCtoERP: extract joint piece M_Products + tack offsets from RosettaStone IFCs → ERP.db | S103 |
| J2 | Shim products: phantom host surface anchors in ERP.db (zero offset) | J1 |
| J3 | MEP recipes: shim-rooted M_BOM assemblies with dx/dy/dz in ERP.db | J2 |
| J4 | M_BOM_Line model: c_uom_id (EA/MM/M), qty→REAL, qty_type guard (FIXED/VARIABLE) | J3 |
| J5 | InterimWorkshop: mathematical primitive recomputation for length-unit lines | J4 |
| J6 | Metadata tables: ad_mep_laying_rule, ad_mep_fitting_rule, ad_mep_riser_rule | J5 |
| J7 | Validation: AD_Val_Rule reads metadata tables, post-walk PASS/FAIL per rule | J6 |
| J8 | Fleet: TE + RM, measure coverage per discipline per room | J7 |

J1-J3 are data preparation (ERP.db). J4 is the BOM line model extension
(iDempiere convention: BOMQty + C_UOM_ID). J5 is the only new walker code
(mathematical primitive recomputation — not parametric). J6 defines the
metadata tables that make standards visible to users without reading code.
J7-J8 are verification. The walker itself (PlacementCollectorVisitor)
gains a UOM check in onLeaf() but no MEP-specific logic — UOM drives
the dispatch. The code stays abstract; the rules live in tables.

#### Existing Infrastructure

| Component | Status | Role |
|-----------|--------|------|
| PlacementCollectorVisitor | DONE | Walks M_BOM, accumulates dx/dy/dz — identical for ARC and MEP |
| BomDropper | DONE | Explodes recipes recursively — identical for ARC and MEP |
| IFCtoERP | DONE (J1+J2) | 785 joint piece + 11 shim M_Products in ERP.db |
| DisciplineBomBuilder | DONE (J3) | MEP elements in BOM with shim as parent |
| X_M_BOMLine | EXTEND (J4) | Add c_uom_id, qty→REAL, qty_type FIXED/VARIABLE guard |
| InterimWorkshop | PROMPT (00f) | Primitive recomputation: cylinder/box/torus from LOD + dimensions |
| ad_mep_laying_rule | NEW (J6) | Slope gradients, clearances, spacing — standards as visible data |
| ad_mep_fitting_rule | NEW (J6) | Connection offsets per fitting type (tee, elbow, reducer) |
| ad_mep_riser_rule | NEW (J6) | Vertical run constraints per discipline |
| CrawlRouter + RouteBuilders | DONE (S100) | Generative fallback (buildings without IFC MEP data) |
| system_edges | DONE (S100) | P15/P16/P17 proof input |
| ad_space_type_mep_bom | EXISTS (ERP.db, 186 rows) | Room type → discipline mapping |

### 6.12.3 Hybrid Pattern Architecture — RouteWalker for Unclassified Buildings (S104)

**Problem statement:** Some IFC models (RM and equivalent IFC2x3 buildings) carry MEP geometry
as generic `IfcFlowSegment`/`IfcFlowFitting` with no sub-discipline attribute. Per §11.1, CW and
SP cannot be disambiguated at the element level for these buildings (G2). The §6.12.2 shim+recipe
architecture requires element-level discipline assignment, which G2 blocks.

**Solution:** Pattern-over-anchors. The RouteWalker mines topology patterns from discipline-complete
buildings (TE: CW 619 segments, SP 455 segments, discipline confirmed via `elements_meta.discipline`)
and applies them to anchor points extracted from the unclassified building (RM). Individual pipe
classification is not required — the pattern defines the system.

**This does not violate §6.12.1.** IFCtoERP extracts anchor coordinates from the RM extraction DB
at extraction time and writes them to ERP.db. DAGCompiler reads ERP.db only. No extraction DB
access at compile time.

#### 1. Anchors — Source and Terminal Points

An anchor is an extracted coordinate pair (source_xyz, terminal_xyz) representing a connection
between MEP endpoints. The 491 RM generic pipe elements yield anchor pairs — pipe start and end
points — without discipline assignment.

Anchor types:
- **METER** — connection from water source/riser to fixture (identified by flanking fixture presence)
- **FIXTURE** — terminal endpoint: sink, basin, toilet, WC, floor trap (from element_type keywords)
- **VALVE** — flow controller (from IfcFlowController)
- **GENERIC** — pipe endpoint with no identifiable context

IFCtoERP writes anchors to `ad_mep_anchor`:

```sql
CREATE TABLE ad_mep_anchor (
    anchor_id    TEXT PRIMARY KEY,
    source_building TEXT NOT NULL,
    anchor_type  TEXT NOT NULL CHECK(anchor_type IN ('METER','FIXTURE','VALVE','GENERIC')),
    x_m          REAL NOT NULL,
    y_m          REAL NOT NULL,
    z_m          REAL NOT NULL,
    storey       TEXT,
    ifc_guid     TEXT
);
```

Anchor extraction rule: for each MEP element AABB in the extraction DB, compute the geometric
centre (cx, cy, cz). If the element has two spatially distinct ends (length > 3× min AABB dim),
emit two anchors (start and end). Otherwise emit one anchor at centre. Fixture-type elements
(toilet/sink/drain keywords) are typed as FIXTURE.

#### 2. Pattern — Topology Rows in `ad_mep_pattern`

A pattern is a sequence of routing steps mined from a discipline-complete building. Each row is
one step: a node type transition (from → to), the direction axis, and the offset rule. Steps are
ordered by `sequence`. RouteWalker iterates steps in order against the anchor set.

```sql
CREATE TABLE ad_mep_pattern (
    pattern_id       TEXT NOT NULL,
    discipline       TEXT NOT NULL,    -- CW, SP, FP, ACMV, ELEC
    building_type    TEXT NOT NULL,    -- COMMERCIAL, RESIDENTIAL, TERMINAL, CLINIC
    sequence         INTEGER NOT NULL,
    from_node_type   TEXT NOT NULL,    -- METER, FIXTURE, VALVE, RISER, JUNCTION, STACK
    to_node_type     TEXT NOT NULL,
    direction_axis   TEXT NOT NULL,    -- X, Y, Z, GRADIENT
    piece_type       TEXT NOT NULL,    -- PIPE_STRAIGHT, PIPE_ELBOW, FLOOR_TRAP, etc.
    offset_rule      TEXT,             -- DIRECT, MIN_GRADIENT, STACK_OFFSET
    gradient         REAL,             -- for GRADIENT axis: dz per metre (e.g. 0.025 for SP)
    notes            TEXT,
    source_building  TEXT,             -- which building this pattern was mined from
    PRIMARY KEY (pattern_id, sequence)
);
```

**CW pattern** (mined from TE, discipline=CW): supply run from meter/riser → horizontal main →
branch to fixture. Direction: horizontal (X or Y), then vertical drop (Z) to fixture connection.

**SP pattern** (mined from TE, discipline=SP): fixture drain → horizontal run with gradient →
stack/waste riser. Direction: horizontal with GRADIENT (dz/dx = 0.025), then vertical stack (Z).

Example SP pattern rows (mined from TE 455 SP segments):

```
pattern_id=SP_TERMINAL_01  discipline=SP  building_type=TERMINAL
seq  from_node_type  to_node_type  direction_axis  piece_type        offset_rule    gradient
10   FIXTURE         JUNCTION      GRADIENT        PIPE_STRAIGHT     MIN_GRADIENT   0.025
20   JUNCTION        JUNCTION      GRADIENT        PIPE_STRAIGHT     MIN_GRADIENT   0.025
30   JUNCTION        STACK         Z               PIPE_STRAIGHT     STACK_OFFSET   —
```

Example CW pattern rows:

```
pattern_id=CW_TERMINAL_01  discipline=CW  building_type=TERMINAL
seq  from_node_type  to_node_type  direction_axis  piece_type        offset_rule
10   METER           JUNCTION      X               PIPE_STRAIGHT     DIRECT
20   JUNCTION        FIXTURE       Y               PIPE_STRAIGHT     DIRECT
30   JUNCTION        FIXTURE       Z               PIPE_STRAIGHT     DIRECT
```

Building type matching: RouteWalker selects patterns by `building_type`. RM is RESIDENTIAL.
TE is TERMINAL. If no exact match, fall back to the nearest pattern by element count similarity.
Pattern mining from TE is a one-time extraction step (00q-mine prompt).

#### 3. RouteWalker — Pattern Application Within Envelope

RouteWalker takes:
- **Anchor set** from `ad_mep_anchor` (for the target building)
- **Pattern** from `ad_mep_pattern` (by discipline + building_type)
- **Envelope** from compile DB `c_orderline` WHERE `Discipline='ARC'` (walls, slabs, ceilings)

Algorithm:
1. Select anchors by storey
2. For each pattern step (seq order): connect nearest unconnected anchor pair matching (from_node_type → to_node_type)
3. Generate M_BOM_Line entries: piece_type from step row, dz from gradient rule, length from anchor distance
4. ARC envelope used for constraint: generated pipe must not penetrate ARC AABB (clash check)
5. Write to compile DB `c_orderline` with `Discipline=discipline`

RouteWalker operates entirely within DAGCompiler. It reads ERP.db (anchors + patterns) and the
compile DB (ARC envelope from c_orderline). It writes to the compile DB. No extraction DB access.

RouteWalker is NOT a routing engine (no A* pathfinding, no graph search). It is a pattern
applier: for each pattern step, find the nearest matching anchor pair, emit BOM lines. The
pattern encodes all routing intelligence. The walker only matches and emits.

#### 4. Witness Claims

**W-PATTERN-CW** — RouteWalker generates CW pipe network for RM:
> For HospitalAuckland, RouteWalker with CW_TERMINAL_01 pattern applied to METER+FIXTURE anchors
> produces a connected CW network: all FIXTURE anchors reachable from at least one METER anchor,
> zero CW pipes intersecting ARC AABB (clash=0), all generated segments horizontal or vertical (no
> diagonal), pipe count within 20% of TE CW segment count scaled by floor area ratio.

**W-PATTERN-SP** — RouteWalker generates SP pipe network for RM:
> For HospitalAuckland, RouteWalker with SP_TERMINAL_01 pattern applied to FIXTURE+STACK anchors
> produces a connected SP network: all FIXTURE anchors drain to at least one STACK anchor,
> all generated GRADIENT segments have dz/dx ≥ 0.025 (MS 1228 §5.3), zero SP pipes intersecting
> ARC AABB (clash=0), STACK anchor count ≥ 1 per storey.

**GEO DRIFT scope:** W-PATTERN-CW and W-PATTERN-SP use clash+gradient assertions, not centroid
matching. Centroid DRIFT (±50mm) applies only to extracted elements (FP, ACMV in RM; all disciplines
in TE). Generated CW/SP geometry for RM is not compared against IFC positions (none exist at
discipline level) — it is validated structurally (clash, connectivity, gradient).

#### GEO Forensic Ceiling — G1 vs G2

`emitGeoSummary()` compares compiled placements against source extraction positions
by IFC GUID. RouteWalker-generated rows have no IFC GUID and are excluded.

| Class | Example | GEO scope | Discipline proof | Generated-route proof |
|-------|---------|-----------|-----------------|----------------------|
| G1 | TE (IFC4) | All extracted GUIDs | W-TE-DISC (elements_meta.discipline) | — |
| G2 | RM (IFC2x3) | Extracted GUIDs only; routes excluded | Typed classes only | W-PATTERN-CW/SP |

**G2 invariant:** For G2 buildings, pattern+connectivity proofs are the primary validation
mechanism for generated pipe routes. GEO comparison covers extraction-origin elements only.

#### 5. Phasing

| Phase | Scope | Prereq |
|-------|-------|--------|
| 00q-schema | DDL: `ad_mep_anchor` + `ad_mep_pattern` in ERP.db migration | G1 fix: add discipline to `_import_joint_piece_types` |
| 00q-mine | Mine CW+SP patterns from TE (Terminal_Extracted.db → ad_mep_pattern rows) | 00q-schema |
| 00q-anchor | IFCtoERP: extract anchor points from RM into `ad_mep_anchor` | 00q-schema |
| 00r-walker | DAGCompiler: RouteWalker class — pattern select + anchor match + BOM line emit | 00q-anchor |
| 00r-envelope | RouteWalker: ARC envelope clash check using compile DB c_orderline | 00r-walker |
| 00s-witness | W-PATTERN-CW + W-PATTERN-SP gate tests | 00r-envelope |
| 00t-g3fix | IFCtoERP.discFromClass(): read elements_meta.discipline first (G3 fix) | 00q-anchor |

#### 6. What This Does Not Change

- §6.12.1 Compilation Isolation Invariant: unchanged. IFCtoERP extracts; DAGCompiler compiles.
- §6.12.2 shim+recipe architecture: unchanged for TE (all disciplines extracted, classified, recipe-walked).
- GEO DRIFT proof for TE: unchanged (extracted elements, centroid matching).
- RM FP, ACMV: continue on shim+recipe path (those IFC classes are typed).
- RouteWalker is additive — it supplements the shim+recipe walk for CW/SP in buildings with G2.

### 6.12.4 Space Identity — Room Type Bridges MEP to Furniture (S149b)

**Problem:** MEP recipes (`MEP_RECIPE` BOMs in ERP.db) know how to place pipes
geometrically but don't know which room they serve. Room SET BOMs (in `*_BOM.db`)
contain furniture but don't claim MEP terminals. A pipe run that ends at a sink
has no compiler-visible link to the KITCHEN room where the sink lives.

**Why this matters:** Without space identity, the compiler can place all 162 DX
pipe runs at correct offsets but cannot answer: "does every BATHROOM have a waste
pipe to a STACK?" or "does this KITCHEN's cold water run reach the sink?" The pipe
ends geometrically near the sink but the compiler has no proof — only coincidence.

#### 1. The Abstract Model

Space identity is the bridge between three existing data structures:

```
Room SET BOM (in *_BOM.db)          ad_space_type_mep_bom (in ERP.db)       MEP Recipe (in ERP.db)
┌─────────────────────────┐         ┌───────────────────────────┐           ┌──────────────────────┐
│ DX_A104_SET             │         │ BATHROOM needs:           │           │ D_CW_U_RUN_7         │
│  bom_type=SET           │────────→│   TOILET  → anchor=STACK  │←──────────│  bom_type=MEP_RECIPE │
│  role=BATHROOM          │  lookup │   SINK    → anchor=RISER  │  claimed  │  target_space=BATHROOM│
│  children: furniture    │         │   EXHAUST → anchor=PANEL  │  by       │  anchor_end=RISER    │
└─────────────────────────┘         └───────────────────────────┘           └──────────────────────┘
```

**Three data facts, no routing logic:**
1. **Room capability** — inferred from fixture/pipe presence (PLUMBABLE, ELECTRIFIED, etc.)
2. **MEP schedule** — what each concrete room type needs (`ad_space_type_mep_bom`, 186 rows) — compliance only
3. **Recipe claim** — which capability a recipe serves (`target_space_type_id` on M_BOM)

**The compiler never mentions KITCHEN or BATHROOM.** It asks: "is this room PLUMBABLE?"
A CW pipe needs a PLUMBABLE room. An ELEC conduit needs an ELECTRIFIED room.
Concrete names (KITCHEN, BATHROOM) live in `ad_space_type` for compliance rules and
building code references. The crawler reads `ad_discipline_capability` to map
discipline → capability, then matches recipes to rooms by capability.

The compiler never routes pipes to rooms. It verifies that every room with a
capability has at least one recipe claiming that capability. The geometry is already
correct (§6.12.2 tack chain). Space identity adds the semantic proof.

#### 2. Data Model — Abstract Capabilities

**The Two Layers:**

| Layer | Table | Contains | Used by |
|-------|-------|----------|---------|
| Abstract | `ad_discipline_capability` | CW→PLUMBABLE, ELEC→ELECTRIFIED | Crawler (code) |
| Abstract | `ad_space_type.is_plumbable` etc. | Boolean capability flags per room type | Crawler (code) |
| Concrete | `ad_space_type.Value` | KITCHEN, BATHROOM, BEDROOM | Compliance rules |
| Concrete | `ad_space_type_mep_bom` | BATHROOM→TOILET, KITCHEN→SINK | Building code |

The code path: recipe discipline (CW) → `ad_discipline_capability` → capability
(PLUMBABLE) → rooms where `is_plumbable=1`. No room name in the code.

**Capabilities** (inferred from fixture/pipe presence at extraction time):

| Capability | Inferred when | Discipline |
|-----------|--------------|-----------|
| PLUMBABLE | sink, WC, shower, floor trap, or CW/HW/WASTE pipes | CW, SP |
| ELECTRIFIED | outlet, light, switch, or conduit | ELEC |
| FIRE_PROTECTED | sprinkler, smoke detector | FP |
| VENTILATED | exhaust fan, diffuser, HVAC duct | ACMV |
| GAS_SERVED | gas range, water heater | LPG |

**M_BOM** — two columns on MEP recipes (DV040):

| Column | Type | Purpose |
|--------|------|---------|
| `target_space_type_id` | TEXT | **Capability** this recipe serves (PLUMBABLE, not KITCHEN) |
| `anchor_end` | TEXT | Infrastructure endpoint: RISER, STACK, or PANEL |

**ad_discipline_capability** (DV041):

| discipline | capability |
|-----------|-----------|
| CW | PLUMBABLE |
| SP | PLUMBABLE |
| ELEC | ELECTRIFIED |
| FP | FIRE_PROTECTED |
| ACMV | VENTILATED |
| LPG | GAS_SERVED |

**Why this extends to any building:** A hospital OPERATING_THEATER is PLUMBABLE +
ELECTRIFIED + VENTILATED + FIRE_PROTECTED. A warehouse STORAGE is ELECTRIFIED +
FIRE_PROTECTED. The capabilities are universal; the concrete names are domain-specific.
Adding a new building type means adding rows to `ad_space_type` — no code changes.

#### 3. Extraction Flow (IFCtoERP)

```
IFC extraction DB
  │
  ├── rel_contained_in_space → furniture in rooms → inferSpaceType()
  │     → writes doc_sub_type on SET BOM in *_BOM.db
  │
  ├── IfcFlowTerminal names → classifyFixtureName()
  │     → maps terminal to mep_product_id (SINK, TOILET, etc.)
  │
  └── MEP chain detection → buildMepBomRecipes()
        → last piece in chain → nearest room AABB → room's space_type
        → writes target_space_type_id + anchor_end on M_BOM in ERP.db
```

**GEO logging (black-box — inference):**
```
[MEP-SPACE] room=A104 space_type=BATHROOM capabilities={PLUMBABLE,ELECTRIFIED,VENTILATED}
[MEP-SPACE] room=B103 space_type=KITCHEN  capabilities={PLUMBABLE,ELECTRIFIED,GAS_SERVED}
[MEP-SPACE] room=A202 space_type=HABITABLE capabilities={ELECTRIFIED}
```

**GEO logging (white-box — linkage):**
```
[MEP-SPACE-LINK] recipe=D_CW_U_RUN_1 disc=CW capability=PLUMBABLE room=B103 concrete_type=KITCHEN anchor_end=RISER
[MEP-SPACE-LINK] recipe=D_CW_U_RUN_3 disc=CW capability=PLUMBABLE room=A104 concrete_type=BATHROOM anchor_end=RISER
[MEP-SPACE-LINK] Duplex: 12 linked, 150 no room, 0 no capability
```

Note: `concrete_type` is logged for human traceability but never stored on M_BOM.
The recipe carries `PLUMBABLE`, not `KITCHEN`.

#### 4. Compile-Time Validation

At compile time, the walker reads `target_space_type_id` from the MEP recipe
and checks that the room it serves exists in the BOM hierarchy. No routing — just
a foreign key walk:

```
For each MEP_RECIPE M_BOM where target_space_type_id IS NOT NULL:
  1. Find all SET BOMs where doc_sub_type = target_space_type_id
  2. Verify at least one exists in the same building
  3. Log: MEP-SPACE-AUDIT PASS/FAIL per recipe
```

For each Room SET BOM where doc_sub_type IS NOT NULL:
```
  1. Look up ad_space_type_mep_bom WHERE space_type_id = doc_sub_type
  2. For each scheduled (mep_product_id, anchor_end) pair:
     Find a MEP_RECIPE with matching target_space_type_id + anchor_end
  3. Log: MEP-SPACE-COVERAGE PASS/FAIL per room
```

#### 5. Why This Is Abstract

**The compiler never says KITCHEN.** It says PLUMBABLE.

The model works for any building because:
- **Capabilities** are universal: PLUMBABLE, ELECTRIFIED, FIRE_PROTECTED, VENTILATED, GAS_SERVED
- **Concrete names** are domain-specific: KITCHEN, BATHROOM, OPERATING_THEATER — metadata only
- **Discipline→capability** mapping is data (`ad_discipline_capability`), not code
- **Inference** comes from fixture/pipe presence — IFC-universal, no domain knowledge in code
- Adding a new building type = new rows in `ad_space_type` + `ad_space_type_mep_bom`. Zero code changes.

A fridge is furniture. A fridge in a room with a sink makes it a KITCHEN (concrete
name for compliance). But the compiler only sees: this room has plumbing fixtures →
PLUMBABLE. A CW pipe recipe needs a PLUMBABLE room. Match.

A hospital OPERATING_THEATER is PLUMBABLE + ELECTRIFIED + VENTILATED + FIRE_PROTECTED.
A warehouse STORAGE is ELECTRIFIED + FIRE_PROTECTED. The code path is identical.

**What a newbie needs to know:**
1. The crawler reads `ad_discipline_capability` — never hard-codes room names
2. Room capabilities are inferred from IFC fixtures, not from room names
3. Concrete names (KITCHEN, BATHROOM) are compliance metadata — they appear in
   `ad_space_type_mep_bom` for building code rules, never in crawler logic
4. To add a new discipline: one row in `ad_discipline_capability`, one capability
   flag on `ad_space_type`, one inference rule in `inferCapabilities()`

#### 6. Rosetta Stone vs Compiled Output — What Goes Where

**Critical distinction for newbies:**

| Data | Where | Status | Used for |
|------|-------|--------|----------|
| Sink position | extracted DB (Rosetta Stone) | Reference only | Convergence proof (§6 below) |
| Pipe recipe offsets | ERP.db (M_BOM) | Compiled | Walker placement |
| Room capability | inferred at extraction | Metadata | Linkage + gap analysis |
| Placement offsets | ad_placement_offset (DV042) | Metadata | Gap target computation |
| Fixture gap INSERTs | console output | Actionable | User/script seeds missing targets |

The sink in the extracted DB is a **Rosetta Stone witness** — it proves the recipe
geometry matches reality. It is NOT a compiled product. The DX pipeline compiles
ARC/STR only. MEP recipes are extracted into ERP.db for the walker to use.

The convergence proof compares recipe endpoints against Rosetta Stone terminal
positions to verify the extraction is correct. It does NOT mean the walker placed
the sink — the sink was already there in the IFC.

For generative buildings (no IFC source), the walker will place fixtures using
`ad_placement_offset` rules + room AABBs. The gap analysis tells the user what's
missing and where to put it.

#### 7. How the Pipe Reaches the Sink

**Q: How does the walker know where to put the sink?**

The walker doesn't decide. The sink's position is already in the BOM — extracted
from IFC. The pipe recipe's last piece offset converges on the sink's position
because both were extracted from the same IFC model.

**Extracted buildings (DX, SH, TE):**
```
IFC file → extractIFCtoDB → element_transforms (sink at 2.97, -10.66, 0.93)
         → buildMepBomRecipes → chain detection → pipe recipe with offsets
         → shim origin = first pipe position (3.55, -17.42, 2.75)
         → last piece offset = cumulative from shim
         → absolute position = origin + offset → (6.73, -17.42, 2.74)
         → nearest PLUMBABLE terminal = Sink at 2.97m XY distance
```

**Generative buildings (DM, future):**
```
ad_space_type_mep_bom → KITCHEN needs SINK at WALL_SIDE
RouteWalker → generates pipe from RISER anchor to SINK position
SINK position → from placement_rule (WALL_SIDE) + room AABB
```

**Convergence proof (forensic, zero speculation):**

Every pipe recipe stores its shim's world position (`origin_x/y/z` on M_BOM).
At extraction time, `linkRecipesToSpaces` computes: absolute endpoint = origin +
last piece offset, then finds the nearest plumbing terminal. The result:

| Verdict | Distance | Meaning |
|---------|----------|---------|
| CONVERGED | < 1m | Pipe serves this fixture directly |
| NEAR | 1–3m | Pipe in same room zone as fixture |
| FAR | > 3m | Pipe is a main run, not a terminal branch |

DX result: **133/162 recipes converged** (< 3m to nearest terminal).
29 FAR = main ceiling runs between storeys, no terminal nearby.

**GEO log format** (self-documenting, no human interpretation needed):
```
[MEP-CONVERGE] recipe=D_CW_U_RUN_3 disc=CW endpoint=(8.42,-17.41,2.75)
               terminal=PANELBOARD at (7.39,-17.41,1.80) dist_xy=1.03m → CONVERGED
```

**The pipe is "in the wall" because the shim attaches to the wall surface.**
`CW_CEILING_SHIM` has `host_ifc_class=IfcCovering, mount=BOTTOM`. The pipe hangs
from the ceiling. `ELEC_WALL_SHIM` has `host_ifc_class=IfcWall, mount=SIDE`. The
conduit runs along the wall. The shim IS the wall/ceiling attachment — no routing.

#### 8. Order Qty → Room Coverage — How MEP Quantities Work

The YAML order (user input) has `AD_Org=MEP` with a qty. This qty is NOT
per-fixture — it's a **coverage level** that the walker resolves per room
using the schedule in `ad_space_type_mep_bom`.

| Order qty | Walker interpretation |
|-----------|---------------------|
| 99 (or blank) | Use `qty_normal` for each fixture in each room (standard fit-out) |
| 0 | Fill to `qty_max` for all fixtures (maximum coverage — FP, ELEC, ACMV) |
| N (specific) | Cap total fixtures at N across the building (budget constraint) |

**No separate qty per sub-discipline needed.** The sub-discipline breakdown
is implicit from room capabilities:

```
Order: AD_Org=MEP, qty=99
  ↓
Room A104 (BATHROOM, PLUMBABLE+ELECTRIFIED+FIRE_PROTECTED):
  TOILET  → qty_normal=1 (from schedule)  → CW discipline, 1 pipe run
  SINK    → qty_normal=1                  → CW discipline, 1 pipe run
  OUTLET  → qty_normal=1                  → ELEC discipline, 1 conduit
  LIGHT   → qty_normal=1                  → ELEC discipline, 1 circuit
  SPRINKLER → qty_normal=1               → FP discipline, 1 drop
```

The schedule already says "1 SINK per KITCHEN (min=1, normal=1, max=2)."
The order qty controls coverage level; the schedule controls per-room counts.

**Area-based quantities** (sprinklers, outlets):

When `per_area_normal > 0`, the qty is computed from room area:
```
Room area = AABB_width × AABB_depth (from room SET BOM)
FP sprinkler: per_area_normal = 0.07/m²
Room area = 12m² → 0.07 × 12 = 0.84 → round up → 1 sprinkler
Room area = 50m² → 0.07 × 50 = 3.5 → round up → 4 sprinklers
```

| Schedule column | When used | Example |
|----------------|-----------|---------|
| `qty_min` | Minimum required by code (order qty irrelevant) | TOILET in BATHROOM: always ≥1 |
| `qty_normal` | Standard fit-out (order qty=99) | OUTLET in BEDROOM: 3 |
| `qty_max` | Maximum allowed / fill target (order qty=0) | OUTLET in BEDROOM: 4 |
| `per_area_normal` | Area-proportional (overrides qty when > 0) | SPRINKLER: 0.07/m² |

**Wiring: YAML → ad_sysconfig → Walker (S151)**

The coverage level flows through one key: `MEP_ORDER_QTY` in `ad_sysconfig`.

```
YAML: mep_order_qty: 99           ← user sets coverage level
  ↓  IFCtoBOMPipeline stage 10c
ad_sysconfig: MEP_ORDER_QTY=99    ← stored in BOM DB
  ↓  CompilationPipeline.BomWalkStage
PlacementCollectorVisitor.setMepOrderQty(99) ← walker reads
  ↓  onSubAssembly (SET BOM)
SpaceScheduleDAO.resolveQty(99, entry, area) ← per-room resolution
```

Default: 99 (standard coverage). Fallback chain: `ad_sysconfig` → `System.getProperty("mep.order.qty")` → 99.

Log channel: `GENERATIVE SUMMARY orderQty=N` traces the value used.

#### 9. Fixture Gap Analysis — How Newbies Mark Targets

When the pipeline runs, `emitFixtureGapAnalysis` checks every room against
`ad_space_type_mep_bom`. For each fixture the schedule requires but the room
doesn't have, it:

1. Reads `ad_placement_offset` (DV042) for the placement rule's offsets
2. Computes a target position from room AABB + offsets (zero hardcoded distances)
3. Emits an INSERT statement the user can apply

**Example output:**
```
[MEP-GAP] room=A103 type=KITCHEN fixture=FLOOR_TRAP rule=FLOOR_LOW host=FLOOR anchor=STACK
          → GAP target=(3.852,-11.491,0.000) source=ad_placement_offset
-- A103 needs FLOOR_TRAP at FLOOR_LOW (FLOOR surface, anchor=STACK)
INSERT INTO fixture_target (...) VALUES ('A103', 'FLOOR_TRAP', 'FLOOR_LOW', ...);
```

**Modeller workflow:**
1. Run the pipeline — read the GAP output
2. For each GAP: either (a) apply the INSERT to seed the target, or
   (b) use BonsaiBIMDesigner Outliner to drag the fixture to the correct position
3. Re-run the pipeline — GAP becomes SATISFIED

**To customise placement offsets** (e.g. different building codes):
```sql
-- Change sink height from 850mm to 900mm for commercial kitchens
UPDATE ad_placement_offset SET z_offset = 0.9 WHERE placement_rule = 'WALL_SIDE';
-- Add a new placement rule for hospital oxygen outlets
INSERT INTO ad_placement_offset VALUES ('WALL_BED_HEAD', 'Bed head wall, 1.4m',
    0.15, 0, 'FLOOR', 1.4, 'MIN', 'MAX', 'AS/NZS 2896', 'Oxygen 1400mm above floor');
```

All placement offsets are data. The code reads `ad_placement_offset` and computes.
No recompilation needed to change where fixtures go.

#### 10. Witnesses

| Witness | What it Proves | Test |
|---------|---------------|------|
| W-SPACE-LINK | MEP recipes carry target_space_type_id from extraction | MepRouteGeometryTest S7 |
| W-SPACE-COVER | Every room's MEP schedule is satisfied by at least one recipe | MepRouteGeometryTest S8 |
| W-LOD-BRIDGE | Every generative product has source_element_ref → LOD geometry resolves | MepRouteGeometryTest S19 |
| W-SHIM-DEVICE | Generative devices attach via shim (not bare AABB offset) | MepRouteGeometryTest S20 |
| W-END-JOIN | Walker routes to generative fixture tack point (last piece converges) | MepRouteGeometryTest S21 |
| W-TACK-POINT | Every plumbed fixture has connector with non-zero position + diameter | MepRouteGeometryTest S22 |
| W-DISC-RESOLVE | Generative device discipline matches connects_to, not anchorEnd | MepRouteGeometryTest S23 |

#### 11. LOD Geometry Bridge — source_element_ref (S152)

**First Principle:** Every M_Product that the compiler emits MUST resolve to LOD
geometry. A product without `source_element_ref` renders as a flat AABB box —
that is a first-principle failure, not a cosmetic issue.

**The gap:** Generative device products (TOILET, LIGHT, SINK, FRIDGE, etc.) are
created with `extracted_from='SHARED_RECIPE'` and `source_element_ref=NULL`.
Meanwhile, the IFC file contains real geometry for these same devices under
their Revit family names:

| Abstract Token | IFC Family Name (in component_library) |
|---|---|
| TOILET | `M_Water Closet - Flush Tank:Private - 6.1 Lpf:Private - 6.1 Lpf` |
| SINK | `M_Sink - Island - Single:455 mmx455 mm - Private:455 mmx455 mm - Private` |
| LIGHT | `M_Pendant Light - Hemisphere:150W - 120V:150W - 120V` |
| SWITCH | `M_Lighting Switches:Single Pole:Single Pole` |
| OUTLET | `M_Duplex Receptacle:Duplex Receptacle:Duplex Receptacle` |
| FRIDGE | `M_Refrigerator:850 x 760mm:850 x 760mm` |

The alias system (`DV003_element_mep_alias.sql`) already maps IFC names → abstract
tokens. But this mapping is used only at extraction time (IFC name → product_id).
It is never used in reverse (product_id → source_element_ref → geometry_hash).

**Root cause:** `ProductRegistrar.ensureProductCatalog()` creates products from
two paths: (A) `ExtractionElement` objects that carry `elementRef` → sets
`source_element_ref`, and (B) schedule/recipe products that carry only the
abstract token → `source_element_ref` stays NULL. Path B never looks up the
alias table to find a matching IFC family name.

**Fix:** When creating or updating a SHARED_RECIPE M_Product with NULL
source_element_ref, reverse-lookup the alias table:

```
SELECT element_ref FROM ad_element_mep_alias
WHERE mep_product_id = 'TOILET' LIMIT 1
```

If found, set `source_element_ref = element_ref`. This closes the bridge:

```
M_Product.source_element_ref → I_Geometry_Map.element_ref → geometry_hash → mesh
```

**Invariant (test-first):** After IFCtoERP completes, every product referenced
in `ad_space_type_mep_bom` MUST have either:
- `source_element_ref IS NOT NULL`, OR
- A corresponding row in `M_Product_Image`

A NULL source_element_ref with no M_Product_Image is a test FAIL.

**Test spec (W-LOD-BRIDGE, S19):**
```
1. Run IFCtoERP for DX (or any building with MEP terminals)
2. For each mep_product_id in ad_space_type_mep_bom:
   a. SELECT source_element_ref FROM M_Product WHERE product_id = mep_product_id
   b. Assert source_element_ref IS NOT NULL
   c. SELECT geometry_hash FROM I_Geometry_Map WHERE element_ref = source_element_ref
   d. Assert at least one geometry_hash exists
3. FAIL message: "{product_id} has no LOD geometry bridge — source_element_ref missing"
```

#### 12. Generative Device Shim Architecture — Place Then Route (S152)

**First Principle:** The compiler's output is always Walker-compiled. Input
sources differ (IFC file, schedule data, YAML order) but the output is
c_orderline with positions. **Input** = IFC + YAML + ERP.db rules.
**Output** = compiled ARC/STR from BOM walk + MEP from Walker. No
"extracted" vs "generative" distinction in the output.

**Problem (three findings, one root cause):**

| Finding | Symptom | Root cause |
|---------|---------|------------|
| Toilet inside cupboard | No furniture collision check | PLACE_DEVICE ignores sibling furniture |
| Toilet not facing right | No rotation on generative devices | No shim → no wall-normal → no facing |
| Pipe doesn't reach toilet | No route to generative fixture | Fixture placed but no END-join route |

All three resolve from one architectural decision: **generative devices must go
through shims, not bare AABB placement.**

**12a. Shim-Based Device Placement**

Current (wrong):
```
Walker enters SET BOM (BATHROOM)
  → MEPDevicePlacer computes position from room AABB + ad_placement_offset
  → Creates Placement directly (no shim, no rotation, no collision check)
  → Pipe recipes from ERP.db target original IFC positions, not generative positions
```

Correct:
```
Walker enters SET BOM (BATHROOM)
  1. Read schedule: ad_space_type_mep_bom → BATHROOM needs TOILET at WALL_BACK
  2. Select target wall from placement_rule (WALL_BACK → Y-MAX wall of room AABB)
  3. Check wall zone for existing furniture (sibling LEAFs under same SET BOM)
     → If occupied, shift along wall or flag as CONFLICT
  4. Create phantom SHIM on target wall:
     → host_ifc_class from placement_rule (WALL for WALL_BACK, CEILING for CEILING_CENTER)
     → mount = SIDE (wall) or BOTTOM (ceiling) or TOP (floor)
     → shim origin = wall surface point at placement offset
  5. Attach device as child of SHIM:
     → offset = standoff distance (e.g. 5mm from wall for toilet)
     → facing = inherited from shim's wall normal (no rotation math needed)
  6. Device now has world position = shim origin + child offset
     → same code path as existing MEP shim walk
```

**12b. Fixture Tack Points — Where the Pipe Connects**

A pipe's last piece must connect to a specific point on the fixture body,
not to the fixture's AABB center. A toilet has a waste shank at the bottom-
rear; a sink has a drain at the bottom-center and supply valves underneath.
Without tack points, the pipe has nowhere to aim.

**Existing infrastructure:** `ad_assembly_connector` already has the right
schema (face, connector_type, position_x/y/z, diameter_mm, connects_to).
It has room-level entries (TOILET_BLOCK_FIXTURES → WASTE_OUT 100mm → STACK)
but not individual M_Product-level entries. Positions are `(0,0,0)` placeholders.

**What's needed:** Populate `ad_assembly_connector` for individual M_Products:

```
TOILET → WASTE_OUT  face=BOTTOM  pos=(0.0, -0.15, 0.05)  dia=100mm  → PLUMBING_STACK
TOILET → SUPPLY_IN  face=BOTTOM  pos=(-0.15, -0.15, 0.15) dia=15mm  → WATER_RISER
SINK   → WASTE_OUT  face=BOTTOM  pos=(0.0, 0.0, 0.0)      dia=40mm  → PLUMBING_STACK
SINK   → SUPPLY_IN  face=BOTTOM  pos=(0.0, 0.0, 0.15)     dia=15mm  → WATER_RISER
LIGHT  → SUPPLY_IN  face=TOP     pos=(0.0, 0.0, 0.0)      dia=20mm  → ELEC_CONDUIT
OUTLET → SUPPLY_IN  face=BACK    pos=(0.0, 0.0, 0.0)      dia=20mm  → ELEC_CONDUIT
```

Positions are relative to the fixture's local origin (same frame as
`component_definitions` local_min/max). The Walker reads the tack point
to compute the pipe's final segment offset.

**Tack point resolution chain:**
```
M_Product (TOILET) → ad_assembly_connector WHERE assembly_id = 'TOILET'
  → connector_type = 'WASTE_OUT'
  → tack point = fixture_world_position + connector.position (rotated by shim normal)
  → pipe last piece targets this tack point
```

**Data source:** For input buildings with IFC, tack points can be extracted
from `IfcDistributionPort` (if present) or inferred from fixture AABB +
connector face. For schedule-only buildings, tack points come from the
product catalog (seeded once, reused across buildings).

**Invariant:** Every M_Product referenced in `ad_space_type_mep_bom` that
has `anchor_end` (STACK, RISER, PANEL) MUST have at least one connector
in `ad_assembly_connector` with a non-zero position. Zero-position = test FAIL.

**Test spec (W-TACK-POINT, S22):**
```
1. For each mep_product_id in ad_space_type_mep_bom WHERE anchor_end IS NOT NULL:
   a. SELECT * FROM ad_assembly_connector WHERE assembly_id = mep_product_id
   b. Assert at least one connector exists
   c. Assert position is non-zero (not placeholder 0,0,0)
   d. Assert diameter_mm > 0
   e. Assert connects_to matches anchor_end pattern (WASTE_OUT→STACK, SUPPLY_IN→RISER)
2. FAIL message: "{product_id} has no tack point — pipe cannot connect"
```

**12c. END-Join Route — Walker Routes to Tack Point**

After PLACE_DEVICE creates the fixture at its shim-anchored position, the
Walker must generate a pipe/conduit route from infrastructure to the
fixture's tack-to point (not its center).

**Every fixture is a mini BOM with tack metadata:**
```
TOILET (mini BOM):
  ├── shim → WALL_BACK (solves position + facing)
  ├── tack-FROM: shim origin (where it sits)
  └── tack-TO:   WASTE_OUT at (0, -0.15, 0.05) — shank connection
                 SUPPLY_IN at (-0.15, -0.15, 0.15) — valve connection
                 ↑ incoming pipes look for these points

LIGHT (mini BOM):
  ├── shim → CEILING_CENTER (hangs from ceiling)
  └── tack-TO:   SUPPLY_IN at (0, 0, 0) on TOP — junction box
                 ↑ conduit from panel END-joins here
```

The tack-to points live in `ad_assembly_connector` at the **M_Product level**
(not room assembly level). Each fixture declares its connection points:

| assembly_id | face | connector_type | position (local) | dia_mm | connects_to |
|---|---|---|---|---|---|
| TOILET | BOTTOM | WASTE_OUT | (0, -0.15, 0.05) | 100 | PLUMBING_STACK |
| TOILET | BOTTOM | SUPPLY_IN | (-0.15, -0.15, 0.15) | 15 | WATER_RISER |
| SINK | BOTTOM | WASTE_OUT | (0, 0, -0.05) | 40 | PLUMBING_STACK |
| SINK | BOTTOM | SUPPLY_IN | (-0.1, 0, 0.15) | 15 | WATER_RISER |
| LIGHT | TOP | SUPPLY_IN | (0, 0, 0) | 20 | ELEC_CONDUIT |
| OUTLET | BACK | SUPPLY_IN | (0, 0, 0) | 20 | ELEC_CONDUIT |

`connects_to` tells the Walker which infrastructure to route FROM: PLUMBING_STACK
→ find nearest stack anchor, WATER_RISER → find nearest riser, ELEC_CONDUIT →
find nearest panel.

**The route sequence:**
```
Walker has placed TOILET at position T = (3.2, 8.1, 0.2) via shim
TOILET.WASTE_OUT tack-to = T + rotated(0.0, -0.15, 0.05) = (3.2, 7.95, 0.25)

  1. Read fixture's tack-to from ad_assembly_connector:
     → assembly_id='TOILET', connector_type='WASTE_OUT'
     → tack-to world pos P = fixture_origin + rotated(connector.position)

  2. Find nearest infrastructure anchor:
     → connects_to = PLUMBING_STACK → find nearest STACK in ad_mep_anchor
     → anchor world pos A = (3.5, 5.0, 2.75)

  3. Generate route segments from A toward P:
     → Horizontal run along ceiling from A
     → Vertical drop toward P height
     → Standard-length pieces from joint vocabulary (PIPE_STRAIGHT, PIPE_ELBOW)
```

**Step 4 — Halt and Recalculate (last mile):**

The Walker MUST NOT blindly extend the last segment. Standard pieces have
fixed lengths from the joint vocabulary. The gap between the penultimate
piece's endpoint and the tack-to point P is almost never an exact multiple
of a standard piece length. Without a halt, the pipe overshoots past P.

```
  4. Halt before overshoot:
     → After each segment, compute remaining_distance to P
     → When remaining_distance < next_standard_piece_length:
        a. STOP generating standard pieces
        b. Create a VARIABLE-length terminal piece:
           → c_uom_id = MM, qty_type = VARIABLE, qty = remaining_distance_mm
           → InterimWorkshop recomputes primitive to exact length (§6)
        c. Terminal piece endpoint = P (the tack-to point, exactly)

  5. Convergence proof:
     → Assert: terminal piece endpoint == P within 1mm
     → Same CONVERGED proof as §7 but with 1mm tolerance (not 1m)
     → This is a JOIN, not a proximity check — pipe meets fixture
```

**Why InterimWorkshop (§6):** The terminal piece is a VARIABLE-length
pipe straight, same as a contractor cutting pipe from stock to fit the
remaining gap. `c_uom_id=MM` + `qty_type=VARIABLE` triggers InterimWorkshop
— no CUT verb, no special code path. The UOM is the signal.

**Overshoot detection (test invariant):** If any route's terminal piece
endpoint exceeds P by more than 1mm on any axis, the test FAILs with
`OVERSHOOT: pipe extends {N}mm past fixture tack-to point`. This is not
a warning — overshoot means the pipe goes through the wall or into the
next room.

The sequence is: **place fixture → read tack-to → route toward it →
halt at last mile → trim terminal piece to exact length → join.**

**12d. Discipline Resolution — connects_to, Not anchorEnd**

`resolveDeviceDiscipline()` (PlacementCollectorVisitor:1411) maps discipline
from `anchorEnd` (PANEL/RISER/STACK). This is wrong — `anchorEnd` is an
infrastructure endpoint type, not a discipline. SPRINKLER connects to a
FP panel, not an electrical panel. All PANEL devices default to ELEC.

**Fix:** Read `connects_to` from `ad_assembly_connector` (seeded by DV047):

| connects_to | Discipline |
|---|---|
| ELEC_CONDUIT | ELEC |
| WATER_RISER | CW |
| PLUMBING_STACK | SP |
| FP_MAIN | FP |
| ACMV_DUCT | ACMV |

The resolver should query `ad_assembly_connector WHERE assembly_id = deviceId`
and map `connects_to` → discipline. Fallback to ELEC only if no connector row.

**Test spec (W-DISC-RESOLVE, S23):**
```
1. For each generative device in SH/DX pipeline output:
   a. Read Discipline from c_orderline
   b. Read connects_to from ad_assembly_connector WHERE assembly_id = productId
   c. Assert discipline matches connects_to mapping:
      SPRINKLER → FP (not ELEC)
      EXHAUST_FAN → ACMV (not ELEC)
      SUPPLY_DIFFUSER → ACMV (not ELEC)
      LIGHT → ELEC
      OUTLET → ELEC
2. FAIL: "{device} discipline={actual} but connects_to={infra} → expected {correct}"
```

**12e. Furniture Collision Avoidance**

When selecting a wall zone for device placement (step 3 above), the placer
must check for existing furniture occupying that zone.

```
Room SET BOM children (already walked):
  CUPBOARD at (2.8, 7.9, 0.0) AABB 0.6×0.4×2.0m  ← occupies WALL_BACK zone

PLACE_DEVICE wants TOILET at WALL_BACK:
  1. Compute candidate position from ad_placement_offset
  2. Check: does candidate AABB overlap any sibling furniture AABB?
  3. If overlap:
     a. Shift along wall (try next available segment)
     b. If no space on target wall: try adjacent wall with same orientation
     c. If no wall available: emit CONFLICT warning (do not invent position)
  4. Log: GENERATIVE COLLISION_CHECK {device} zone={wall} siblings={N} result={OK|SHIFT|CONFLICT}
```

**Invariant:** A generative device MUST NOT overlap with any existing furniture
LEAF. Overlap = test FAIL.

**12f. Test Specs**

**W-SHIM-DEVICE (S20):** Generative devices use shim architecture
```
1. Walk DX_BOM.db with erpConn (same as S16)
2. For each generative placement:
   a. Assert it has a parent shim in the placement hierarchy
   b. Assert shim has host_ifc_class (WALL, CEILING, or SLAB)
   c. Assert device offset from shim is small (<0.5m) — standoff, not room-scale
   d. Assert device facing direction matches shim wall normal
3. For each room with generative devices:
   a. Assert no generative device AABB overlaps any furniture LEAF AABB
   b. Log any SHIFT events (device moved to avoid furniture)
```

**W-END-JOIN (S21):** Walker routes to generative fixture tack-to points
```
1. Walk DX_BOM.db with erpConn and route generation enabled
2. For each generative PLUMBABLE fixture (TOILET, SINK):
   a. Read tack-to from ad_assembly_connector (WASTE_OUT or SUPPLY_IN)
   b. Compute tack-to world pos P = fixture_origin + rotated(connector.position)
   c. Assert a pipe route exists from infrastructure anchor to P
   d. Assert terminal piece is VARIABLE (c_uom_id=MM, qty_type=VARIABLE)
   e. Assert terminal piece endpoint == P within 1mm (exact join, not proximity)
   f. Assert NO OVERSHOOT: terminal endpoint must not exceed P on any axis by >1mm
3. For each generative ELECTRIFIED fixture (OUTLET, LIGHT, SWITCH):
   a. Read tack-to from ad_assembly_connector (SUPPLY_IN)
   b. Assert conduit route from PANEL anchor to tack-to point
   c. Assert terminal piece endpoint == tack-to within 1mm
   d. Assert no overshoot
4. FAIL if any fixture has no route (gap = test failure, not a warning)
5. FAIL if any route overshoots: "OVERSHOOT: pipe extends {N}mm past tack-to"
```

**W-TACK-POINT (S22):** Fixture tack points exist and are non-placeholder
```
1. For each mep_product_id in ad_space_type_mep_bom WHERE anchor_end IS NOT NULL:
   a. SELECT * FROM ad_assembly_connector WHERE assembly_id = mep_product_id
   b. Assert at least one connector row exists
   c. Assert position is non-zero (not placeholder 0,0,0)
   d. Assert diameter_mm > 0
   e. Assert connects_to matches discipline pattern:
      WASTE_OUT → PLUMBING_STACK, SUPPLY_IN → WATER_RISER/ELEC_CONDUIT
2. For TOILET specifically:
   a. Assert WASTE_OUT connector exists (shank)
   b. Assert SUPPLY_IN connector exists (valve)
   c. Assert positions are physically plausible (within fixture AABB)
3. FAIL message: "{product_id} has no tack point — pipe cannot connect"
```

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

---

## §11 — DISC BOM Single Source of Truth — Audit Findings (S104)

**Audit scope:** Read-only forensics. Two test stones: HospitalAuckland (RM) + Terminal (TE).
**DBs queried:** `library/ERP.db`, `DAGCompiler/lib/input/HospitalAuckland_extracted.db`, `DAGCompiler/lib/input/Terminal_Extracted.db`.

---

### §11.1 — CW/SP Disambiguation Rule

#### TE (Terminal) — RESOLVED

`elements_meta.discipline` carries the sub-discipline per element row in the extraction DB.
This column is populated at IFC extraction time and is the authoritative source.

Evidence (Terminal_Extracted.db):

```
ifc_class                    discipline  count
─────────────────────────────────────────────
IfcPipeSegment               FP          2672
IfcPipeSegment               CW           619
IfcPipeSegment               SP           455
IfcPipeSegment               LPG           75
IfcPipeFitting               FP          3146
IfcPipeFitting               CW           638
IfcPipeFitting               SP           372
IfcPipeFitting               LPG           87
IfcFlowController            FP            14
IfcFlowController            CW             7
IfcFlowTerminal              SP           150
IfcFlowTerminal              CW           106
IfcFireSuppressionTerminal   FP           909
```

**CW/SP rule for TE:** read `elements_meta.discipline` → map to AD_Org_ID (CW=6, SP=7, FP=3, LPG=8).
No keyword heuristic needed. No geometry needed.

#### RM (HospitalAuckland) — G2: CW/SP_UNRESOLVABLE

RM uses IFC2x3 generic classes. All pipe/fitting elements have `discipline='MEP'` (flat, no sub-type).

- `mep_systems` table: **0 rows** (system membership not extracted)
- `system_nodes` table: **0 rows**
- `element_properties`: only `Reference` with values (FITTING, FRAME, CURTAIN_PANEL) — no system names
- No `PredefinedType`, `ObjectType`, `FlowDirection`, `SystemType`, or `ServiceType` properties on any pipe element
- Element names: `Pipe Types:Standard` covers 491 of 1060 IfcFlowSegment/IfcFlowFitting instances — no system info

Partial name-based discrimination exists:
- "DWV" / "Sanitary" in element_name → SP
- "Conduit" → ELEC
- Light fixture names (Recessed/Sconce/Pendant Light) → ELEC

But the dominant generic `Pipe Types:Standard` class (~46% of flow elements) has no IFC attribute that
distinguishes CW from SP.

**G2: CW/SP_UNRESOLVABLE for RM.**

Resolution path: the RM IFC model requires re-export from Revit with IfcSystem assignments
(`IfcDistributionSystem.SystemType`) populated per piping network. This is a model quality gap,
not a compiler gap.

---

### §11.2 — Piece-Type to Discipline Map (RM + TE)

Column headers: `→ discipline` = AD_Org_ID, `source attribute` = what drives the classification.

```
piece_type            → discipline         source attribute
──────────────────────────────────────────────────────────────────────
DUCT_STRAIGHT         → ACMV (5)          IfcDuctSegment (class, unambiguous)
DUCT_TEE              → ACMV (5)          IfcDuctFitting (class, unambiguous)
DUCT_ELBOW            → ACMV (5)          IfcDuctFitting (class, unambiguous)
DUCT_TRANSITION       → ACMV (5)          IfcDuctFitting (class, unambiguous)
DUCT_CROSS            → ACMV (5)          IfcDuctFitting (class, unambiguous)
DUCT_TAKEOFF          → ACMV (5)          IfcDuctFitting (class, unambiguous)
DUCT_WYE              → ACMV (5)          IfcDuctFitting (class, unambiguous)
DUCT_FITTING          → ACMV (5)          IfcDuctFitting (class, unambiguous)
AIR_TERMINAL          → ACMV (5)          IfcAirTerminal (class, unambiguous)
AIR_DIFFUSER          → ACMV (5)          IfcAirTerminal (class, unambiguous)
AIR_GRILLE            → ACMV (5)          IfcAirTerminal (class, unambiguous)
SPRINKLER_HEAD        → FP (3)            IfcFireSuppressionTerminal (class, unambiguous)
HOSE_REEL             → FP (3)            IfcFireSuppressionTerminal (class, unambiguous)
SMOKE_DETECTOR        → FP (3)            IfcFireSuppressionTerminal (class, unambiguous)
LIGHT_FIXTURE         → ELEC (4)          IfcLightFixture (class, unambiguous)
TOILET                → SP (7)            element_type keyword (toilet/wc)
URINAL                → SP (7)            element_type keyword (urinal)
FLOOR_TRAP            → SP (7)            element_type keyword (trap)
BIDET_SPRAY           → SP (7)            element_type keyword (bidet)
INSPECTION_CHAMBER    → SP (7)            element_type keyword (inspection)
GREASE_TRAP           → SP (7)            element_type keyword (interceptor/grease)

── AMBIGUOUS — needs elements_meta.discipline ──────────────────────

PIPE_STRAIGHT         → FP/CW/SP/LPG     elements_meta.discipline (TE: resolved)
                                          RM: UNRESOLVABLE (G2)
PIPE_ELBOW            → FP/CW/SP/LPG     elements_meta.discipline (TE: resolved)
                                          RM: UNRESOLVABLE (G2)
PIPE_TEE              → FP/CW/SP/LPG     elements_meta.discipline (TE: resolved)
                                          RM: UNRESOLVABLE (G2)
PIPE_REDUCER          → FP/CW/SP/LPG     elements_meta.discipline (TE: resolved)
                                          RM: UNRESOLVABLE (G2)
PIPE_CROSS            → FP/CW/SP/LPG     elements_meta.discipline (TE: resolved)
                                          RM: UNRESOLVABLE (G2)
PIPE_COUPLING         → FP/CW/SP/LPG     elements_meta.discipline (TE: resolved)
                                          RM: UNRESOLVABLE (G2)
PIPE_FLANGE           → FP/CW/SP/LPG     elements_meta.discipline (TE: resolved)
PIPE_FITTING          → FP/CW/SP/LPG     elements_meta.discipline (TE: resolved)
VALVE                 → FP/CW            elements_meta.discipline (TE: FP=14, CW=7)
SINK                  → CW or SP         elements_meta.discipline (TE: CW=106 via FlowTerminal)
SHOWER                → CW or SP         elements_meta.discipline
TAP                   → CW               elements_meta.discipline (TE: CW FlowTerminal)
PLUMBING_FIXTURE      → CW or SP         elements_meta.discipline (ambiguous by keyword alone)

── RM IFC2x3 generics — AMBIGUOUS (G2 applies) ────────────────────

FLOW_SEGMENT          → MEP (undivided)  IfcFlowSegment, RM only, discipline='MEP'
FLOW_FITTING          → MEP (undivided)  IfcFlowFitting, RM only, discipline='MEP'
```

**AMBIGUOUS count:** 14 pipe/fitting/valve/fixture piece types require `elements_meta.discipline`
to resolve. For TE this is available. For RM the 14 types remain unresolvable (G2).

---

### §11.3 — FP Archetype Pattern + ELEC/ACMV/CW/SP equivalents

#### DISC BOM (abstract layer) — FP_SYSTEM in ERP.db

```
bom_id      seq  child_product_id      verb_ref
──────────────────────────────────────────────
FP_SYSTEM   10   FP_RISER              ROUTE
FP_SYSTEM   20   FP_SPRINKLER_LAYOUT   ROUTE
FP_SYSTEM   30   FP_PUMP_LINK          ROUTE
```

ELEC_SYSTEM, ACMV_SYSTEM, CW_SYSTEM, SP_SYSTEM, LPG_SYSTEM: **0 ROUTE lines** (empty shells).

#### MEP_RECIPE BOMs (spatial layer) — current state

FP recipes confirmed in library/ERP.db:
- **RM** (routing topology): `RM_FP_L2_RUN_1` = FP_CEILING_SHIM(seq=10) + SPRINKLER_HEAD_15MM(seq=20–70)
  → maps to **FP_SPRINKLER_LAYOUT** only. No riser or pump recipe.
- **ST** (coverage topology): 41 archetypes `ST_FP_ARCH_*` = FP_CEILING_SHIM(seq=10) + SPRINKLER_HEAD_30MM(seq=20, dz=standoff)
  → all map to **FP_SPRINKLER_LAYOUT** archetype (coverage, no routing chain).

FP_RISER and FP_PUMP_LINK are **not yet represented** in any MEP_RECIPE BOM.
TE has 2672 FP IfcPipeSegment elements (the riser network) but they are not extracted into recipes.

#### FP → ELEC/ACMV/CW/SP archetype equivalents

| DISC BOM verb  | FP piece types         | ACMV equivalent      | CW/SP equivalent    | ELEC equivalent     |
|----------------|------------------------|----------------------|---------------------|---------------------|
| FP_RISER       | PIPE_STRAIGHT (FP)     | DUCT_STRAIGHT (ACMV) | PIPE_STRAIGHT (CW/SP)| FLOW_SEGMENT (ELEC) |
| FP_SPRINKLER_LAYOUT | SPRINKLER_HEAD    | AIR_DIFFUSER/GRILLE  | TOILET/SINK/TAP     | LIGHT_FIXTURE       |
| FP_PUMP_LINK   | VALVE (FP)             | —                    | VALVE (CW)          | —                   |

Recipe count by discipline (library/ERP.db, bom_type=MEP_RECIPE):

```
building   FP   ACMV   CW    SP   ELEC
─────────────────────────────────────
RM          1    441   341     0     0
ST (TE)    41    169   582     1    45
CE (clinic) 0      0   434     0     0
CH           0      0   588     0     0
CP           0      0  1005     0     0
```

Note: CE/CH/CP CW recipe explosion (434/588/1005) from misclassified IfcFlowTerminal light fixtures → see §11.4.

---

### §11.4 — IFCtoERP Discipline Assignment (current state)

Method: `IFCtoERP.discFromClass(String ifcClass, String elementType)` (line 698, IFCtoERP.java).

```java
case "IfcFireSuppressionTerminal" -> "FP"           // CORRECT — class is unique
case "IfcDuctSegment", "IfcDuctFitting",
     "IfcAirTerminal"            -> "ACMV"          // CORRECT — classes are unique
case "IfcLightFixture"           -> "ELEC"          // CORRECT — class is unique
case "IfcFlowTerminal"           -> keyword check:  // PARTIAL
    drain/waste/soil/inspection/interceptor → SP
    else → CW                                       // BUG: light fixtures in RM are IfcFlowTerminal
case "IfcPipeSegment", "IfcPipeFitting",
     "IfcFlowController"         -> keyword check:  // PARTIAL
    soil/drain/sp_              → SP
    else → CW                                       // OK for TE, misses LPG
default                          -> CW              // BUG: IfcFlowSegment/IfcFlowFitting → forced CW
```

**G3: IFCtoERP_DISCIPLINE_BLIND**

`discFromClass()` does not read `elements_meta.discipline`. It re-derives discipline from IFC class
and element_type keyword heuristics. Two concrete failures:

1. **RM IfcFlowTerminal → CW (wrong):** RM contains light fixtures as `IfcFlowTerminal`
   (e.g. `M_Plain Recessed Lighting Fixture`, `M_Pendant Light`). These have no "drain/waste" keyword
   so they fall through to CW. Effect: CE/CH/CP produce hundreds of CW MEP_RECIPE BOMs for light fixtures.

2. **RM IfcFlowSegment/IfcFlowFitting → CW (default, wrong):** These generic IFC2x3 classes cover
   all pipe types (CW+SP+FP conduit). Default=CW silently classifies SP drains and electrical conduit as CW.

**Fix scope:** IFCtoERP only. Strategy:
- In `readMepElementsWithPositions()`, read `elements_meta.discipline` alongside ifc_class.
- If `discipline` is a known sub-discipline code (FP/CW/SP/ACMV/ELEC/LPG), use it directly.
- Fall back to `discFromClass()` keyword heuristic only when `discipline` is null or 'MEP'.
- Add keyword: `IfcFlowTerminal` with "light"/"fixture"/"lamp" → ELEC (catches RM light fixture misclassification).

---

### §11.5 — Gaps Blocking 00q–00t

| Gap | Description | Scope | Resolution |
|-----|-------------|-------|------------|
| G1  | `_import_joint_piece_types` has no `discipline`/`AD_Org_ID` column | Schema | Add column in 00q SQL migration; populate from elements_meta.discipline at extract time |
| G2  | RM CW/SP UNRESOLVABLE — IFC2x3 model lacks system membership, no sub-discipline attribute for generic pipe elements | IFC model (Revit) | RESOLVED via RouteWalker pattern approach (00r/00s). RM Rosetta Stone 8/8 PASS. |
| G3  | IFCtoERP `discFromClass()` ignores `elements_meta.discipline`; defaults IfcFlowTerminal light fixtures to CW; defaults all IfcFlowSegment/FlowFitting to CW | IFCtoERP.java | Fix: read `discipline` column first; keyword fallback only if null/'MEP'; add light-fixture keyword for IfcFlowTerminal |

**G1 RESOLVED (00q):** `_import_joint_piece_types.discipline` column added.
**G2 RESOLVED (00r/00s):** RouteWalker pattern approach for G2 buildings. RM 8/8.
**G3 TARGET (00t):** Routing topology branch at IFCtoERP.java line 799 still calls 2-arg `discFromClass`. Fix: pass `e.discipline`. Affects TE (CW/SP/FP/LPG correctly separated) and RM (light fixtures stopped from routing as CW).

**W-TE-DISC** — IFCtoERP correctly assigns discipline from elements_meta for TE:
> After 00t fix, `_import_joint_piece_types.discipline` breakdown for Terminal matches
> `elements_meta` source counts: IfcPipeSegment CW≥619, SP≥455, FP≥2672, LPG≥75.
> No IfcFlowTerminal rows assigned CW when element_type contains "light"/"lamp"/"fixture".
> TE routing topology groups split correctly by discipline (not all collapsed to CW).

**00q safe to proceed?**

- **For TE:** YES. `elements_meta.discipline` resolves CW/SP/FP/LPG. G3 fix required to populate
  `AD_Org_ID` correctly in `_import_joint_piece_types`. G1 schema extension required.
- **For RM:** PARTIAL. FP, ACMV, ELEC, SPRINKLER_HEAD resolved. CW/SP pipes blocked by G2.
  RM can proceed for typed-class disciplines (FP=6 elements, ACMV=2081 elements) but CW/SP pipe
  recipes will be inaccurate until G2 is resolved at the source model level.

**00q prerequisite:** G1 (schema) + G3 (discipline read) must be fixed before writing ROUTE lines
for ELEC/ACMV/CW/SP/GAS. Otherwise the staging table cannot carry AD_Org_ID per row and the
single-source-of-truth architecture is broken.
