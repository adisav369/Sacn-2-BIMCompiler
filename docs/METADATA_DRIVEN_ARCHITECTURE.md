# BIM Compiler Domain Architecture: The iDempiere ERD Applied to Construction

**Version:** 2.4
**Date:** 2026-02-26
**Purpose:** Establish domain separation in the BIM compiler's data model following iDempiere's proven three-tier architecture: System Dictionary → Master Data Domains → Transaction Documents
**Insight:** A building is an order. Space is the product. The DSL is the order entry form.

**Changes in 2.4 (2026-02-26) — Reconciliation with ConstructionAsERP.md:**
- Section 4: `bt_*` transaction tables deprecated — C_Order proper (C_Order = Construction Order, C_OrderLine = Construction Order Details). Redirect to ConstructionAsERP.md §2.
- Section 5-8: `bt_*`/`cd_*`/`sd_*`/`rd_*`/`sf_*` references in domain map, tab cascade, DSL mapping, and lifecycle updated to actual table names.
- Section 9: Migration path replaced with iDempiere Entity Map — actual state, no aspirational renames.
- Section 11.0: Updated to reflect G-1 Steps 1-4 complete. FurniturePlacer/FurnitureTypeResolver DELETED.
- Section 11.6: MRP BOM Explosion — Step 5 pending (was "Steps 4-5").
- Section 17.6: `BOMCascadeResolver` → `BOMTierResolver` (actual class name).
- Prior intra-day v2.0-2.3 changelog entries collapsed.

---

## 1. The iDempiere Pattern

iDempiere separates concerns into three tiers that never cross boundaries without explicit foreign keys:

```
┌─────────────────────────────────────────────────────────┐
│  TIER 1: APPLICATION DICTIONARY (AD_*)                  │
│  Defines the system itself — tables, windows, processes │
│  Owned by: System Administrator                         │
│  Changes: Rarely, affects all clients                   │
├─────────────────────────────────────────────────────────┤
│  TIER 2: MASTER DATA DOMAINS                            │
│  Reusable entities shared across transactions           │
│  Each domain has a root entity + supporting tables      │
│                                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐             │
│  │ Product  │  │ BPartner │  │ DocType  │   ...       │
│  │ Domain   │  │ Domain   │  │ Domain   │             │
│  │          │  │          │  │          │             │
│  │ M_Product│  │C_BPartner│  │C_DocType │             │
│  │ M_BOM    │  │C_BPartner│  │C_DocType │             │
│  │ M_Price  │  │  _Loc    │  │  Action  │             │
│  │ M_Storage│  │C_BP_Group│  │C_Doc     │             │
│  │ M_Attrib │  │C_BP_Bank │  │  Sequence│             │
│  └──────────┘  └──────────┘  └──────────┘             │
│  Owned by: Client/Org                                   │
│  Changes: Periodic, by domain experts                   │
├─────────────────────────────────────────────────────────┤
│  TIER 3: TRANSACTION DOCUMENTS                          │
│  Specific business events referencing master data       │
│                                                         │
│  C_Order → lines → M_Product (what)                     │
│                   → C_BPartner (who)                    │
│                   → C_DocType (how)                     │
│                   → M_PriceList (how much)              │
│                                                         │
│  Owned by: Users                                        │
│  Changes: Constantly, per transaction                   │
│  Lifecycle: Draft → InProgress → Complete → Posted      │
└─────────────────────────────────────────────────────────┘
```

**Key principle:** The order (C_Order) doesn't define what a product is. It references a product. The product (M_Product) doesn't know about any specific order. The application dictionary (AD_*) doesn't know about any specific product or order — it defines the structure that both follow.

---

## 2. The BIM Compiler Equivalent

Direct domain mapping — same three tiers, construction vocabulary:

```
┌─────────────────────────────────────────────────────────┐
│  TIER 1: APPLICATION DICTIONARY (AD_*)                   │
│  Defines the compiler itself — stages, rules, types     │
│  Owned by: Compiler architects (red1 + watchdog)        │
│  Changes: Rarely, affects all buildings                  │
├─────────────────────────────────────────────────────────┤
│  TIER 2: MASTER DATA — M_BOM MODEL                      │
│  Reusable construction knowledge shared across projects │
│                                                         │
│  ┌──────────────────────────────┐  ┌──────────┐        │
│  │  M_BOM (the product)         │  │   Code   │        │
│  │  BOMCategory = WHAT          │  │  Domain   │        │
│  │  C_BPartner  = WHO           │  │(AD_Val_Rule)      │
│  │  M_BOM_Line  = children      │  │          │        │
│  │  M_Attribute = leaf params   │  │          │        │
│  │  M_Product   = LOD geometry  │  │          │        │
│  └──────────────────────────────┘  └──────────┘        │
│  Owned by: Domain experts (architects, engineers)       │
│  Changes: Periodic, per typology contribution           │
├─────────────────────────────────────────────────────────┤
│  TIER 3: C_Order (BUILDING TRANSACTION)                  │
│  Specific buildings referencing M_BOM master data       │
│                                                         │
│  DSL (the "order") → C_BPartner (WHO)                   │
│                     → AABB (HOW BIG)                    │
│  Compiler forwards M_BOM packages — doesn't care        │
│  what's inside. Full separation of concerns.            │
│                                                         │
│  Owned by: Architects / building designers              │
│  Changes: Constantly, per project                       │
│  Lifecycle: Draft → Compiling → Validated → Released    │
└─────────────────────────────────────────────────────────┘
```

---

## 3. The Three Remaining Domains

> **Note (v2.4):** The original §3.1 (Space Domain / `sd_*`) and §3.2 (Component Domain
> / `cd_*`) have been **deleted**. Both are fully realized by the M_BOM model:
>
> - **Space** = M_BOM with a BOMCategory (BD, KT, BT, LI...). Slots = M_BOM_Line children. Clearances = M_Attribute. Buffer space (BOMCategory=ST) is an explicit M_BOM_Line child with variable SpaceSize — fills remaining AABB so parent=SUM(children) invariant holds.
> - **Component** = M_Product (LOD geometry) + M_BOM (assembly recipe) + M_BOM_Line (child placement) + M_Attribute (leaf params). Buffer children are products too.
>
> The compiler is a **logistics forwarder** — it processes M_BOM packages without
> knowing or caring what's inside. Full separation of concerns. The BOM model IS the
> space-object-set model. See §4 for the mapping table and §9 for the entity map.
>
> The DDL schemas in §3.3–3.5 below remain as **conceptual domain models** showing
> how iDempiere's separation of concerns applies to regulatory and structural concerns.
> The custom prefixes (`rd_`, `sf_`) are aspirational vocabulary. Actual table names
> follow iDempiere convention — see §9.

### 3.3 CODE Domain (≈ C_BPartner / C_Tax)

**The regulatory constraints.** Building codes, fire codes, accessibility standards — the external authorities that constrain what's allowed. Like C_BPartner represents external entities the business must deal with, the Code Domain represents external authorities the building must comply with.

**Root entity:** `rd_code_authority` (Regulatory Domain)

```sql
-- The authority (≈ C_BPartner — external entity)
CREATE TABLE rd_code_authority (
    authority_id    TEXT PRIMARY KEY,      -- 'UBBL', 'BOMBA', 'JKR', 'IBC', 'UK_BUILDING_REGS'
    authority_name  TEXT NOT NULL,
    jurisdiction    TEXT NOT NULL,          -- 'MY', 'US', 'UK'
    version         TEXT,                  -- '2012_AMENDMENT_2020'
    is_active       INTEGER DEFAULT 1,
    provenance      TEXT NOT NULL
);

-- Specific code rules (≈ C_Tax_Rate — rates per jurisdiction)
CREATE TABLE rd_code_constraint (
    constraint_id   TEXT PRIMARY KEY,
    authority_id    TEXT NOT NULL REFERENCES rd_code_authority,
    clause_ref      TEXT NOT NULL,          -- 'UBBL Part III Section 40(1)' — exact citation
    applies_to      TEXT NOT NULL,          -- 'sd_space_type:BEDROOM_MY' or 'cd_product:*'
    property        TEXT NOT NULL,          -- 'min_area_sqm', 'min_width_mm', 'max_occupancy'
    operator        TEXT NOT NULL,          -- 'GTE', 'LTE', 'EQUALS', 'BETWEEN'
    value           TEXT NOT NULL,          -- '11.15' or '900|1200' for BETWEEN
    unit            TEXT NOT NULL,          -- 'sqm', 'mm', 'persons'
    severity        TEXT DEFAULT 'ERROR',   -- 'ERROR' = must comply, 'WARNING' = advisory
    is_active       INTEGER DEFAULT 1,
    provenance      TEXT NOT NULL           -- 'RESEARCHED_UBBL_PART_III_S40'
);

-- Fire protection requirements (≈ C_Tax per product category)
CREATE TABLE rd_fire_requirement (
    requirement_id  TEXT PRIMARY KEY,
    authority_id    TEXT NOT NULL REFERENCES rd_code_authority,
    space_category  TEXT NOT NULL,          -- 'HABITABLE', 'CIRCULATION', 'SERVICE'
    building_type   TEXT NOT NULL,          -- 'RESIDENTIAL', 'INSTITUTIONAL'
    sprinkler_req   INTEGER DEFAULT 0,     -- 1 = required
    smoke_det_req   INTEGER DEFAULT 0,
    exit_distance_m REAL,                  -- max travel distance to exit
    fire_rating_min INTEGER,               -- minutes of fire resistance
    clause_ref      TEXT NOT NULL,
    provenance      TEXT NOT NULL
);

-- MEP sizing rules (≈ C_Charge — rates per service type)
CREATE TABLE rd_mep_sizing (
    sizing_id       TEXT PRIMARY KEY,
    authority_id    TEXT NOT NULL REFERENCES rd_code_authority,
    system_type     TEXT NOT NULL,          -- 'COLD_WATER', 'WASTE', 'ELECTRICAL'
    fixture_type    TEXT NOT NULL,          -- 'TOILET', 'BASIN', 'SHOWER'
    fixture_units   REAL NOT NULL,          -- loading units per fixture
    min_pipe_dn     INTEGER,               -- minimum pipe diameter in mm
    clause_ref      TEXT NOT NULL,          -- 'MS 1228 Table 3.1'
    provenance      TEXT NOT NULL
);
```

**iDempiere parallel:**
| iDempiere | BIM Code Domain |
|-----------|----------------|
| C_BPartner | rd_code_authority (external entity we must satisfy) |
| C_Tax + C_Tax_Rate | rd_code_constraint (rules per jurisdiction) |
| C_Charge | rd_mep_sizing (rates per service type) |
| C_BP_Group | jurisdiction grouping |
| AD_Val_Rule | rd_code_constraint used as validation during editing |

### 3.4 STRUCTURE Domain (≈ C_AcctSchema / structural framework)

**The spatial framework that organises everything.** Grid systems, storey definitions, structural bays — the skeleton that spaces attach to.

**Root entity:** `sf_grid_system` (Structure Framework)

```sql
-- Grid system (≈ C_AcctSchema — the organising framework)
CREATE TABLE sf_grid_system (
    grid_id         TEXT PRIMARY KEY,
    grid_name       TEXT NOT NULL,
    grid_type       TEXT NOT NULL,          -- 'RECTANGULAR', 'RADIAL', 'IRREGULAR'
    origin_x_mm     INTEGER DEFAULT 0,
    origin_y_mm     INTEGER DEFAULT 0,
    provenance      TEXT NOT NULL
);

-- Grid lines (≈ C_ElementValue — the account codes)
CREATE TABLE sf_grid_line (
    grid_id         TEXT NOT NULL REFERENCES sf_grid_system,
    axis            TEXT NOT NULL,          -- 'X' or 'Y'
    line_label      TEXT NOT NULL,          -- 'A', 'B', 'C' or '1', '2', '3'
    offset_mm       INTEGER NOT NULL,       -- distance from origin
    seq_no          INTEGER NOT NULL,
    provenance      TEXT NOT NULL,
    PRIMARY KEY (grid_id, axis, line_label)
);

-- Storey definitions (≈ C_Period — time divisions become vertical divisions)
CREATE TABLE sf_storey (
    storey_id       TEXT PRIMARY KEY,
    building_id     TEXT NOT NULL,          -- FK to bt_building
    storey_name     TEXT NOT NULL,          -- 'Ground', 'First', 'Roof'
    floor_level_mm  INTEGER NOT NULL,       -- absolute Z from datum
    floor_to_floor  INTEGER NOT NULL,       -- height in mm
    storey_type     TEXT NOT NULL,          -- 'BASEMENT', 'GROUND', 'TYPICAL', 'ROOF'
    seq_no          INTEGER NOT NULL,       -- bottom to top
    provenance      TEXT NOT NULL
);

-- Structural members on grid intersections
CREATE TABLE sf_structural_member (
    member_id       TEXT PRIMARY KEY,
    grid_id         TEXT NOT NULL REFERENCES sf_grid_system,
    grid_x          TEXT NOT NULL,          -- grid line label on X
    grid_y          TEXT NOT NULL,          -- grid line label on Y
    storey_id       TEXT NOT NULL REFERENCES sf_storey,
    member_type     TEXT NOT NULL,          -- 'COLUMN', 'BEAM_X', 'BEAM_Y'
    product_id      TEXT NOT NULL,          -- REFERENCES cd_product (steel section, concrete size)
    provenance      TEXT NOT NULL
);
```

**iDempiere parallel:**
| iDempiere | BIM Structure Domain |
|-----------|---------------------|
| C_AcctSchema | sf_grid_system (the organising framework) |
| C_ElementValue | sf_grid_line (the classification codes) |
| C_Period | sf_storey (divisions along an axis) |
| C_AcctSchema_Element | sf_structural_member (intersections of framework) |

### 3.5 SYSTEM Domain (≈ AD_*)

**The compiler's self-knowledge.** Pipeline stages, validation rules, process definitions, the registry itself. This domain governs how everything else is processed.

```sql
-- Compiler pipeline definition (≈ AD_Process)
CREATE TABLE sys_compiler_stage (
    stage_id        TEXT PRIMARY KEY,
    stage_name      TEXT NOT NULL,
    java_class      TEXT NOT NULL,          -- 'com.bim.compiler.stage.GridResolver'
    seq_no          INTEGER NOT NULL,       -- execution order
    is_mandatory    INTEGER DEFAULT 1,      -- 1 = cannot skip
    is_active       INTEGER DEFAULT 1,
    description     TEXT
);

-- Validation rules for editor (≈ AD_Val_Rule)
CREATE TABLE sys_validation_rule (
    rule_id         TEXT PRIMARY KEY,
    table_name      TEXT NOT NULL,
    column_name     TEXT NOT NULL,
    rule_type       TEXT NOT NULL,          -- 'FK_CHECK', 'RANGE', 'SQL_WHERE', 'ENUM'
    rule_expression TEXT NOT NULL,          -- the actual constraint
    error_message   TEXT NOT NULL,
    is_active       INTEGER DEFAULT 1
);

-- Process registry for batch operations (≈ AD_Process)
CREATE TABLE sys_process (
    process_id      TEXT PRIMARY KEY,
    process_name    TEXT NOT NULL,
    java_class      TEXT NOT NULL,
    description     TEXT,
    is_active       INTEGER DEFAULT 1
);

-- Change log (≈ AD_ChangeLog)
CREATE TABLE sys_changelog (
    change_id       INTEGER PRIMARY KEY AUTOINCREMENT,
    table_name      TEXT NOT NULL,
    record_id       TEXT NOT NULL,
    column_name     TEXT NOT NULL,
    old_value       TEXT,
    new_value       TEXT,
    changed_by      TEXT DEFAULT 'SYSTEM',
    changed_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    session_id      TEXT                   -- which compilation session
);
```

---

## 4. The Transaction Layer: C_Order Proper

> **DEPRECATED (v2.4):** The `bt_*` table schema originally proposed here has been
> superseded. The transaction layer uses iDempiere entity names directly.
> See `docs/ConstructionAsERP.md` §2 for the authoritative model.

The building IS a C_Order — not a custom `bt_building`. The mapping:

| iDempiere | BIM Table | Role |
|-----------|-----------|------|
| C_Order (Construction Order) | `ad_building_registry` | The construction order. Two governing fields: `C_BPartner` (WHO) + AABB (HOW BIG) |
| C_BPartner | `bom_owner` column | Construction Building Pattern — SH/DX/TB/TE are design models, ST (Standard) triggers full processing. Scopes which M_BOM trees are visible. Present BOMs are all M_BOM.C_BPartner = SH or DX |
| C_OrderLine (Construction Order Details) | `ad_element_rule` | Selects M_BOMs from BOM.db, places them in rooms |
| CO_EmptySpace | `co_empty_space` | Construction space header (AABB, IsAvailable quality gate) |
| CO_EmptySpaceLine | `co_empty_space_line` | Spatial alignment per BOM level (before/next, orientation) |
| M_BOM | `m_bom` | Assembly definition: BOMCategory (WHAT) + C_BPartner (WHO) |
| M_BOM_Line | `m_bom_line` | Child placement: dx/dy/dz, rotation_rule, locator_ref, SpaceSize |
| M_Attribute | `m_attribute` | Leaf attributes: ports, clearances, UBBL rules |
| M_Product | `ad_product_dim` | Intrinsic geometry: width, depth, height (in metres) |

**C_BPartner legend (Construction Building Pattern):**

| Code | Pattern | Notes |
|------|---------|-------|
| SH | SampleHouse | Single-storey residential design model |
| DX | Duplex | Two-storey residential design model |
| TB | TB-LKTN | Institutional (Terminal Building Lekatan) |
| TE | Terminal | Commercial terminal |
| ST | Standard | Triggers full processing — all BOM categories available |

**M_BOM.BOMCategory legend (replaces abstract room_type):**

| Code | Category | Notes |
|------|----------|-------|
| LI | Living | Living room BOM |
| BD | Bedroom | Bedroom BOM |
| KT | Kitchen | Kitchen BOM |
| BT | Bathroom | Bathroom/wet area BOM |
| WC | Toilet | Water closet / toilet room BOM |
| FR | Furniture | Generic furniture set |
| ST | Buffer/Spacer | Buffer children for AABB invariant |
| L1 | Level 1 | Ground floor storey BOM |
| L2 | Level 2 | Upper floor storey BOM |
| UN | Unit | Top-level unit BOM (contains storeys) |
| SL | Slab | Structural slab |
| RF | Roof | Roof structure + covering |
| DN | Drain | Drainage assembly |
| CR | Corridor | Circulation space |
| MN | Main | Main/common area |

**The DSL (order entry form) reduces to two C_Order fields:**
`C_BPartner` (WHO) + `AABB` (HOW BIG). Everything else cascades.
See ConstructionAsERP.md §3.7 for the 1D Intent spec.

---

## 5. The Complete Domain Map

> **Note (v2.4):** The four master data domain names (Space, Component, Code, Structure)
> remain valid as conceptual guidance. However, the actual table prefixes follow iDempiere
> convention (`ad_*`, `m_*`, `co_*`) — not the aspirational `sd_`/`cd_`/`rd_`/`sf_` prefixes.
> See §9 for the actual entity map.

```
┌──────────────────────────────────────────────────────────────────────────┐
│                    SYSTEM DOMAIN (AD_*)                                   │
│         Pipeline stages, validation rules, processes, changelog          │
│                     ≈ AD_* in iDempiere                                  │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────┐  ┌──────────────────┐  │
│  │   SPACE     │  │  COMPONENT  │  │   CODE   │  │    STRUCTURE     │  │
│  │   DOMAIN    │  │   DOMAIN    │  │  DOMAIN  │  │     DOMAIN       │  │
│  │             │  │             │  │          │  │                  │  │
│  │ C_OrderLine │  │ M_BOM      │  │AD_Val_Rule │ Grid system      │  │
│  │ (placement) ┼──┤►M_BOM_Line │  │          │  │ Wall faces       │  │
│  │             │  │ M_Attribute │  │          │  │ Storey defs      │  │
│  │             │  │ M_Product   │  │          │  │                  │  │
│  │             │  │ (LOD fetch) │  │          │  │                  │  │
│  │ "What kind" │  │ "Made of"  │  │"Rules by"│  │ "Organised on"   │  │
│  │             │  │             │  │          │  │                  │  │
│  │ ≈ C_Order   │  │ ≈ M_BOM    │  │≈ C_Tax/ │  │ ≈ AcctSchema     │  │
│  │   Line      │  │  (product) │  │ Val_Rule │  │ + C_Period       │  │
│  └──────┬──────┘  └──────┬──────┘  └────┬─────┘  └────────┬─────────┘  │
│         │                │              │                  │             │
├─────────┼────────────────┼──────────────┼──────────────────┼─────────────┤
│         ▼                ▼              ▼                  ▼             │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │              BUILDING TRANSACTION: C_Order                       │   │
│  │                                                                  │   │
│  │  C_Order ──────────────────→ C_OrderLine                        │   │
│  │       │                           │                              │   │
│  │       │  C_BPartner (WHO)         │  selects:                    │   │
│  │       │  AABB (HOW BIG)           │  • M_BOM (assembly)         │   │
│  │       │  DSL (order entry)        │  • M_Product (LOD geometry)  │   │
│  │       │                           │    (via M_BOM_Line)          │   │
│  │       │                           │                              │   │
│  │       ▼                           ▼                              │   │
│  │   CO_EmptySpace (header)    CO_EmptySpaceLine                    │   │
│  │   IsAvailable quality gate  per-storey spatial alignment         │   │
│  │                                                                  │   │
│  │  Lifecycle: DRAFT → COMPILING → VALIDATED → RELEASED             │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 6. The Master-Detail Cascade (Editor Navigation)

The GUI editor navigates this hierarchy exactly like iDempiere's AD_Window → AD_Tab:

```
Window: Building Editor
├── Tab 0: C_Order (the construction order)
│   WHERE IsActive = 'Y' ORDER BY SeqNo
│   Fields: building_id, C_BPartner (Construction Building Pattern), AABB, DSL content, SpatialDigest
│
├── Tab 1: Room Boundaries (spatial extents per building)
│   WHERE C_Order_ID = @C_Order_ID ORDER BY Name
│   Fields: Name (extracted/abstracted from reference building), bounds
│
├── Tab 2: C_OrderLine (placement rules — selects M_BOMs, places in rooms)
│   WHERE C_Order_ID = @C_Order_ID AND Name = @Name
│   Fields: family_ref, wall_face, fraction, offset, orientation, height_extent_mm
│
├── Tab 3: M_BOM + M_BOM_Line (assembly recipe)
│   M_BOM WHERE BOMCategory = @category AND C_BPartner = @C_BPartner
│   M_BOM_Line WHERE M_BOM_ID = @M_BOM_ID ORDER BY SeqNo
│   Fields: role, dx, dy, dz, rotation_rule, locator_ref, SpaceSize
│   READ-ONLY from building editor (edit in BOM Library window)
│
├── Tab 4: M_Product (LOD geometry for leaf components)
│   WHERE M_Product_ID = @child_product_id
│   Fields: width, depth, height (metres), material_name, material_rgba
│   READ-ONLY from building editor (edit in Component Library window)
│
└── Tab 5: AD_Val_Rule (code compliance — UBBL, fire, accessibility)
    WHERE applies_to matches @BOMCategory
    Shows: all constraints that apply to the selected BOMCategory
    Status: PASS/FAIL per constraint based on current spatial extents
    READ-ONLY (edit in Code Authority window)
```

**Validation rules per tab (≈ AD_Val_Rule):**

```
Tab 2 (C_OrderLine):
  family_ref must reference a valid M_BOM for the C_Order's C_BPartner
  height_extent_mm MUST be set (if 0, dz=0 → P01/P03 CRITICAL failure)

Tab 3 (M_BOM):
  BOMCategory scoped by C_BPartner — M_BOM WHERE BOMCategory=? AND C_BPartner=?
  SpaceSize AABB must satisfy parent=SUM(children) invariant

Tab 5 (Compliance):
  Room area must satisfy AD_Val_Rule WHERE property = 'min_area_sqm'
  Displays RED if violated, GREEN if compliant
```

---

## 7. The DSL as Order Entry

The DSL maps directly to C_Order creation. The insight (v2.4) is that the DSL reduces
to **two C_Order fields**: `C_BPartner` (WHO) + `AABB` (HOW BIG). Everything else cascades
from M_BOM master data.

```
DSL Line                              → iDempiere Record
─────────────────────────────────────────────────────────────────
BUILDING "CitizenHome"                → C_Order (building_id, name)
  TYPE RESIDENTIAL                    → C_Order.building_type = 'RESIDENTIAL'
  OWNER SH                            → C_Order.C_BPartner = 'SH'

  -- Everything below is M_BOM tree, resolved from C_BPartner + AABB:
  -- UNIT (UN) → LEVEL (L1/L2) → ROOM BOMs (BD/KT/BT/LI) → items

  "Ground"                            → M_BOM BOMCategory=L1, C_BPartner='SH'
    "bilik_utama" BOUNDS C2-D4        → M_BOM BOMCategory=BD, C_BPartner='SH' + spatial extent
    "tandas"      BOUNDS D4-E4        → M_BOM BOMCategory=BT, C_BPartner='SH' + spatial extent
```

**The DSL parser becomes a C_Order document creator.** It doesn't compile geometry — it creates a C_Order with C_BPartner + AABB. The compilation pipeline reads the C_Order and resolves the entire building through M_BOM explosion: C_BPartner scopes which M_BOM trees are visible, BOMCategory selects which assembly at each level (UNIT → storeys → rooms → items). Spatial extents come from Room Boundaries.

This is the same separation as iDempiere: the order entry screen (DSL) creates C_Order + C_OrderLine records. The document processing (Complete action) resolves the lines against M_BOM, M_Product (LOD), AD_Val_Rule to produce CO_EmptySpace and IFC output. The user never touches the processing — they author the order, the system does the rest.

---

## 8. Document Lifecycle (≈ C_DocType + DocAction)

```sql
-- Building lifecycle states
-- DRAFT:     DSL being authored, PENDING provenance allowed
--            Editor: all fields editable
--            Compiler: can run but failures are expected
--
-- COMPILING: Compilation in progress
--            Editor: locked during compilation
--            Compiler: running pipeline
--
-- VALIDATED: All witness proofs pass, zero PENDING values
--            Editor: metadata editable but triggers revalidation
--            Compiler: last run was clean
--
-- RELEASED:  Frozen for construction/submission
--            Editor: READ-ONLY — no changes without reversal
--            Compiler: snapshot locked, spatial_digest frozen
--            Changelog: any attempted edit logged and rejected
--
-- VOIDED:    Design abandoned
--            Editor: READ-ONLY
--            Compiler: excluded from batch operations

-- On C_Order (the construction order):
-- doc_status TEXT DEFAULT 'DRAFT'
-- doc_action TEXT  -- next allowed action

-- Status transitions (≈ C_DocType allowed transitions)
CREATE TABLE sys_doc_transition (
    from_status     TEXT NOT NULL,
    to_status       TEXT NOT NULL,
    action_name     TEXT NOT NULL,          -- 'COMPILE', 'VALIDATE', 'RELEASE', 'VOID', 'REOPEN'
    requires        TEXT,                  -- 'ALL_PROOFS_PASS', 'ZERO_PENDING', NULL
    PRIMARY KEY (from_status, to_status)
);

INSERT INTO sys_doc_transition VALUES
('DRAFT',      'COMPILING', 'COMPILE',  NULL),
('COMPILING',  'DRAFT',     'FAIL',     NULL),           -- compilation failed
('COMPILING',  'VALIDATED', 'VALIDATE', 'ALL_PROOFS_PASS'),
('VALIDATED',  'DRAFT',     'REOPEN',   NULL),           -- user wants to edit
('VALIDATED',  'RELEASED',  'RELEASE',  'ZERO_PENDING'),  -- freeze for submission
('RELEASED',   'VALIDATED', 'REOPEN',   NULL),           -- unlock for revision
('DRAFT',      'VOIDED',    'VOID',     NULL),
('VALIDATED',  'VOIDED',    'VOID',     NULL);
```

---

## 9. The iDempiere Entity Map (Actual State)

The current tables map to iDempiere entities — no custom prefixes needed:

| BIM Table | iDempiere Entity | Database | Status |
|-----------|-----------------|----------|--------|
| `ad_building_registry` | C_Order (Construction Order) | BOM.db | LIVE — the construction order |
| `ad_element_rule` | C_OrderLine (Construction Order Details) | BOM.db | LIVE — selects M_BOMs, places in rooms |
| `bom_owner` (column) | C_BPartner | on C_Order + M_BOM | LIVE — Construction Building Pattern: SH/DX/TB/TE design models, ST=Standard (full processing) |
| `ad_product_dim` | M_Product | BOM.db | LIVE — LOD geometry (metres) |
| `ad_room_boundary` | (spatial) | BOM.db | LIVE — room bounds per building |
| `ad_building_grid` | (structural) | BOM.db | LIVE — grid system |
| `ad_ubbl_rule` | C_Tax / AD_Val_Rule | BOM.db | LIVE — regulatory constraints |
| `m_bom` | M_BOM | BOM.db | LIVE — assembly definition (3 dimensions) |
| `m_bom_line` | M_BOM_Line | BOM.db | LIVE — child placement + SpaceSize |
| `m_attribute` | M_Attribute | BOM.db | LIVE — leaf attributes |
| `M_BomCategory` | M_Product_Category | BOM.db | LIVE — 14 functional codes |
| `co_empty_space` | CO_EmptySpace | output.db | LIVE — construction space header |
| `co_empty_space_line` | CO_EmptySpaceLine | output.db | LIVE — spatial alignment per BOM level |
| `ad_room_slot` | (deprecated) | BOM.db | DEPRECATED — replaced by bom_category + bom_owner |

**No rename needed.** The `ad_*` tables are legitimately Application Dictionary
entries — they define the product catalog, placement rules, and building
registry. The `m_*` tables are Master Data (BOM assemblies). The `co_*` tables
are Construction Output. These ARE the iDempiere prefixes.

---

## 10. The Five Guarantees

When the domain architecture is complete, these invariants hold:

**1. Domain isolation.** A change to the Component Domain (new product, updated BOM) doesn't require changes to the Space Domain or Code Domain. A new Malaysian code (UBBL amendment) only touches rd_* tables. A new furniture set only touches cd_* tables.

**2. Transaction integrity.** A building (C_Order) references master data but doesn't duplicate it. Updating a product in M_Product affects all buildings that reference it on next compile. Like updating M_Product price affects all future C_OrderLines.

**3. Lifecycle enforcement.** A RELEASED building cannot be edited. A DRAFT building can have PENDING provenance. A VALIDATED building has zero PENDING. Status transitions are explicit and auditable.

**4. Editor navigability.** Every table is reachable from C_Order through an unbroken FK chain. The GUI follows the chain: building → storey → room → slot → assembly → child → params. No dead ends, no string-matched joins.

**5. Compilation determinism.** The same C_Order record with the same master data always produces the same IFC output with the same SpatialDigest. The compilation is a pure function of the transaction + master data state. The changelog tracks which master data state was active at compilation time.

---

*The BIM Intent Compiler is an ERP for buildings. The building is the order. Space is the product. Components are the bill of materials. Codes are the tax rules. The grid is the chart of accounts. The DSL is the order entry form. The compilation is the document posting. The witness proofs are the audit trail. Every pattern that makes iDempiere work for manufacturing and distribution makes this work for construction — because at the data level, building a house and building an order are the same operation: assembling configured components according to constrained rules.*

---

## 11. Java Code Evolution

The data model evolution (Sections 3–8) requires corresponding Java patterns. These follow iDempiere's proven architecture — every pattern below has a direct iDempiere equivalent that exists extensively in LLM training data.

### 11.0 Actual Current State (updated 2026-02-26, Phase G-1 Steps 1-4 complete)

Before reading the target patterns below, this is what actually exists in the codebase today.

**The orm-core DAO layer is LIVE.** `BasePO` + `ModelQuery<T>` (424 lines, 2 files) provide the iDempiere PO pattern — dirty tracking, lifecycle hooks, fluent query builder with `COLUMNNAME_*` compile-time safety. See `orm-core/docs/BIMDAOTechnicalFramework.md` for full specification.

**28 PO entity classes across 3 modules** (ORMSandbox: 13 X_ + 9 M_; TopologyMaker: 3 X_ + 3 M_). Each table has a structure layer (X_) and a model/business layer (M_) — exactly the iDempiere `X_`/`M_` pattern.

**Phase G-1 Steps 1-3: BOM pipeline unified.** `BOMTierResolver` — single resolver for ALL BOM tiers (furniture, fixtures, structural). Three-way dispatch: fixture params → GPD walk → FLOAT dx/dy. `BOMTreeLoader` — shared AD-layer tree infrastructure (iDempiere AD/Model separation). `FurnitureWorker` calls `BOMTierResolver.resolveForRoom()` directly — no `FurniturePlacer` intermediary. `normalizeRole()` passes BOM roles through as strings (`default → bomRole`), eliminating the 30-case `FurnitureType` enum switch. `FixtureWorker.java`, `FixturePlacer.java`, `FixturePlacerTest.java` deleted (dead code). `FurniturePlacer.java`, `FurnitureTypeResolver.java` DELETED (Step 4). StoreyCompiler fallback block eliminated — all BOM dispatch goes through `BOMTierResolver`. Fixture-param roles (TOILET, SINK, EXHAUST_FAN) now pass through correctly to MEPWriter for proper IFC class assignment.

| Pattern | Status | Actual Implementation |
|---------|--------|-----------------------|
| `CompilerStage` interface | **DONE** | `CompilationPipeline.java` — 7-stage typed chain |
| `MetadataValidator` as Stage 1 | **DONE** | Fires before all other stages, blocks on bad data |
| Sealed Placement types (partial) | **DONE** | `PositionRule.java` — sealed with DirectCoordinate, WallFraction, RoomFraction, BomAnchor |
| `SlotRegistry` reads `ad_room_slot` | **DONE** | Lazy singleton, profile-aware, `getSlotsForType(roomType, profile)` |
| `BasePO` + `ModelQuery<T>` (≈ PO + Query) | **DONE** | `orm-core/` — dirty tracking, `INSERT OR IGNORE`, `beforeSave()` hooks, fluent WHERE/JOIN/orderBy, `POFactory` lambda. Caller-managed transactions. |
| X_/M_ entity classes (≈ X_/MProduct) | **DONE (28 classes)** | `ORMSandbox/src/.../po/` — ad_product_dim, ad_element_rule, ad_room_boundary, ad_building_registry, ad_room_slot, ad_geometry_map, m_bom, m_bom_line, m_attribute, M_BomCategory, co_empty_space, co_empty_space_line. `TopologyMaker/src/.../po/` — ad_typology_template, ad_building_registry, ad_room_boundary. |
| `BuildingInspector` CLI (≈ iDempiere InfoWindow) | **DONE** | 8 commands: buildings, bom, rooms, rules, slots, product, preflight, dump. Read-only typed navigation of full BIM construct. |
| CO_EmptySpace pipeline (≈ C_Order processing) | **DONE** | `X_CO_EmptySpace`/`M_CO_EmptySpace` (header), `X_CO_EmptySpaceLine`/`M_CO_EmptySpaceLine` (line). WriteStage creates header + top-level + per-storey children. ProveStage runs IsAvailable quality gate. |
| IsAvailable quality gate (≈ DocAction) | **DONE** | IP→CO (is_available=0) on success, IP→RE on critical violations. Per-building in output DB. |
| 3-DB architecture | **DONE** | `BOM.db` (ad_* config + m_* BOM, ~73 tables), `component_library.db` (lod_* geometry, ~12 tables), output DBs (co_empty_space, elements, rtree). Split-connection pattern for cross-DB queries. |
| BOM table rename (ad_bom→m_bom etc.) | **DONE** | `m_bom`, `m_bom_line`, `m_attribute`, `M_BomCategory` — iDempiere M_ prefix in DB. Java POs aligned. |
| BOM 3 dimensions (Category+Owner+SpaceSize) | **DONE** | M_BomCategory (14 codes: LI/BD/KT/FR/ST/L1/L2/UN/SL/RF/DN/BT/CR/MN), bom_owner column, SpaceSize (AABB mm) on 72 m_bom_line records. |
| Buffer (ST) children in BOM | **DONE** | 16 buffer records across all room BOMs. BOM construct complete with spacers. |
| `DomainStore` with typed domain namespaces | **NOT DONE** | orm-core provides per-table typed access via ModelQuery, not unified domain routing. No SpaceDomain, ComponentDomain wrappers. |
| Full sealed `Placement` interface (contract) | **NOT DONE** | PositionRule exists for computation; the full contract from REFACTOR_SEALED_TYPES.md not implemented |
| `RoomContext` record | **NOT DONE** | CompilationContext carries pipeline state but is not room-scoped |
| `CompilerValidator` hooks (beforeResolve, afterResolve, beforeWrite) | **NOT DONE** | Ad-hoc validators. ProveStage is closest (runs after write, gates IsAvailable). |
| Domain table prefixes (sd_, cd_, rd_, sf_, bt_, sys_) | **PARTIAL** | BOM tables use m_* prefix (correct iDempiere convention). CO tables use co_* prefix. Remaining ad_* tables not yet renamed. |
| `provenance` column on all tables | **NOT DONE** | Only C_Order (Construction Order) and `ad_geometry_map` carry provenance |

**Phase B gate reality (revised):** The orm-core DAO layer provides typed domain records (X_/M_ classes) and fluent query access (ModelQuery) — these fulfil the *mechanism* of Phase B/C. What remains is the *organisation*: wrapping per-table access into domain-scoped stores (SpaceDomain, ComponentDomain) and wiring them into DAGCompiler's resolver path. The DAGCompiler still uses raw JDBC (Guardrail 2: orm-core is deliberately NOT a DAGCompiler dependency).

**Test gate:** 170 PASS / 1 RED (G8-DX intentional) / 3 SKIP (F2-DX + 2 @Disabled).

### 11.1 DomainStore — Domain-Routed Data Access (≈ MTable)

**Replaces:** Scattered SQL queries and flat `MetadataStore.getOrThrow(key)` calls.

```java
/**
 * Domain-aware data access. Each domain is a typed namespace.
 * Pattern: iDempiere MTable.get(ctx, tableName) → typed access per domain.
 */
public class DomainStore {
    private final SpaceDomain sd;        // sd_* tables
    private final ComponentDomain cd;    // cd_* tables
    private final RegulatoryDomain rd;   // rd_* tables
    private final StructureDomain sf;    // sf_* tables
    private final SystemDomain sys;      // sys_* tables

    public SpaceDomain sd() { return sd; }
    public ComponentDomain cd() { return cd; }
    public RegulatoryDomain rd() { return rd; }
    public StructureDomain sf() { return sf; }
    public SystemDomain sys() { return sys; }
}

// Usage in resolvers — clean, typed, domain-aware:
SdSpaceType bedroom = domainStore.sd().spaceType("BEDROOM_MY");
List<SdSpaceSlot> slots = domainStore.sd().slotsFor(bedroom);
for (SdSpaceSlot slot : slots) {
    CdBom assembly = domainStore.cd().bom(slot.getBomId());
    List<CdBomChild> children = domainStore.cd().bomChildren(assembly);
    // resolve each child through pipeline...
}
```

### 11.2 Typed Domain Records (≈ MProduct / MOrder)

**Replaces:** Raw `Map<String, String>` from SQL results with untyped, unsafe access.

```java
// CURRENT: unsafe, untyped
Map<String, String> row = metadataStore.getRow("ad_product_dim", productId);
int width = Integer.parseInt(row.get("width_mm"));  // can NPE, can NumberFormat

// EVOLUTION: typed domain record (pattern: MProduct.get(ctx, id))
public record CdProduct(
    String productId,       // PK — non-null
    String productName,     // non-null
    String productCategory, // non-null
    String ifcClass,        // non-null
    int widthMm,            // positive
    int depthMm,            // positive
    int heightMm,           // positive
    String mountingFace,    // nullable (not all products mount)
    String materialName,
    String materialRgba,
    String provenance        // non-null — EXTRACTED or RESEARCHED
) {
    public CdProduct {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(productName, "productName");
        Objects.requireNonNull(ifcClass, "ifcClass");
        Objects.requireNonNull(provenance, "provenance");
        if (widthMm <= 0 || depthMm <= 0 || heightMm <= 0)
            throw new DimensionalContractViolation(
                "Non-positive dimension for " + productId);
    }
}

// Access: throws if missing, typed if found
CdProduct product = domainStore.cd().product("TOILET_BACK_WALL_MY");
int width = product.widthMm();        // typed, guaranteed positive
String prov = product.provenance();   // guaranteed non-null
```

### 11.3 CompilerValidator Hooks (≈ ModelValidator)

**Replaces:** Inline validation scattered across pipeline stages.

```java
/**
 * Pattern: iDempiere ModelValidator — fires at lifecycle events.
 * Registered in sys_compiler_stage, not hardcoded.
 * New jurisdiction = new validator registration, same engine.
 */
public interface CompilerValidator {
    /** Before spatial resolution — check metadata completeness */
    String beforeResolve(BtBuilding building, CompilationContext ctx);
    /** After resolution — check spatial validity */
    String afterResolve(BtBuilding building, CompilationContext ctx);
    /** Before IFC write — check regulatory compliance */
    String beforeWrite(BtBuilding building, CompilationContext ctx);
}

// Regulatory domain validator
public class RegulatoryValidator implements CompilerValidator {
    @Override
    public String beforeWrite(BtBuilding building, CompilationContext ctx) {
        for (BtRoom room : building.getRooms()) {
            List<RdCodeConstraint> constraints =
                domainStore.rd().constraintsFor(room.getSpaceTypeId());
            for (RdCodeConstraint c : constraints) {
                if (!c.evaluate(room))
                    return "FAIL: " + c.getClauseRef() + " — "
                        + c.getProperty() + " requires " + c.getValue();
            }
        }
        return null;  // no error = pass
    }
}
```

### 11.4 DocAction Lifecycle (≈ MOrder.processIt())

**Replaces:** Binary compile/fail with no lifecycle tracking.

```java
/**
 * Pattern: iDempiere DocAction — status transitions with validation.
 * Every iDempiere developer knows this pattern by heart.
 */
public class BtBuilding extends DomainRecord {

    public static final String STATUS_DRAFT     = "DRAFT";
    public static final String STATUS_COMPILING = "COMPILING";
    public static final String STATUS_VALIDATED = "VALIDATED";
    public static final String STATUS_RELEASED  = "RELEASED";
    public static final String STATUS_VOIDED    = "VOIDED";

    public boolean processIt(String action) {
        return switch (action) {
            case "COMPILE"  -> compileIt();
            case "VALIDATE" -> validateIt();
            case "RELEASE"  -> releaseIt();
            case "REOPEN"   -> reopenIt();
            case "VOID"     -> voidIt();
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        };
    }

    private boolean validateIt() {
        // All witness proofs must pass
        if (getProofFailCount() > 0) return false;
        // Zero PENDING provenance
        if (getPendingCount() > 0) return false;
        setDocStatus(STATUS_VALIDATED);
        return true;
    }

    private boolean releaseIt() {
        // Snapshot — frozen for construction submission
        setFrozenDigest(getSpatialDigest());
        setDocStatus(STATUS_RELEASED);
        domainStore.sys().logChange(this, "RELEASED");
        return true;
    }

    private boolean reopenIt() {
        if (STATUS_RELEASED.equals(getDocStatus()))
            domainStore.sys().logChange(this, "REOPENED_FROM_RELEASED");
        setDocStatus(STATUS_DRAFT);
        return true;
    }
}
```

### 11.5 Callout Editor Hooks (≈ CalloutOrder)

**Replaces:** No editor validation (future GUI layer).

```java
/**
 * Pattern: iDempiere Callout — fires when field changes in editor.
 * Validates cross-domain references in real-time.
 */
public class CalloutRoom implements EditorCallout {

    // When user changes space_type_id in room editor
    public String onSpaceTypeChanged(BtRoom room, String newSpaceTypeId) {
        SdSpaceType spaceType = domainStore.sd().spaceType(newSpaceTypeId);

        // Auto-populate default slots (like product → price in order line)
        room.setDefaultSlots(domainStore.sd().slotsFor(spaceType));

        // Validate area against code constraints (like tax on order line)
        List<RdCodeConstraint> areaRules = domainStore.rd()
            .constraintsFor(newSpaceTypeId, "min_area_sqm");
        if (!areaRules.isEmpty()) {
            double minArea = areaRules.get(0).getValueAsDouble();
            if (room.getAreaSqm() < minArea)
                return "WARNING: " + spaceType.getSpaceName()
                    + " requires min " + minArea + " sqm per "
                    + areaRules.get(0).getClauseRef();
        }
        return null;  // no warning
    }
}
```

### 11.6 iDempiere Pattern Map (updated 2026-02-25)

| iDempiere Pattern | BIM Compiler Equivalent | Status |
|-------------------|------------------------|--------|
| `AD_Process` (pipeline stages) | `CompilerStage` interface | **DONE** — 7-stage chain in CompilationPipeline.java |
| `ModelValidator.fireDocValidate()` (before save) | `MetadataValidator` as Stage 1 | **DONE** — blocks compilation on bad metadata |
| `AD_Val_Rule` (field constraints) | `ad_room_slot` (slot dispatch), `ad_assembly_manifest` (clearances) | **DONE as data**; SlotRegistry + ManifestResolver read them |
| `M_BOM` / `M_BOM_Component` computation | `PositionRule` sealed — DirectCoordinate, WallFraction, RoomFraction, BomAnchor | **DONE (partial)** — computation semantics, not full placement contract |
| `MRP BOM Drop` — `M_BOM_Line → C_OrderLine` | `ad_room_slot × ad_room_boundary` → BOM anchor C_OrderLines → `BOMTierResolver` expansion | **DONE** — SH=63, DX=1197, TB-LKTN=138. UNIT→FLOOR BOM tree complete with slabs + roof. |
| `PO` (PersistentObject) | `BasePO` — dirty tracking, `save()/load()/delete()`, `beforeSave()` hooks, `isNewRecord` flag | **DONE** — `orm-core/src/.../BasePO.java` (250 lines). X_/M_ pattern across 28 entity classes. |
| `Query` (MTable.get / fluent query) | `ModelQuery<T>` — fluent WHERE/JOIN/orderBy, `POFactory` lambda, `list()/first()/count()` | **DONE** — `orm-core/src/.../ModelQuery.java` (174 lines). `COLUMNNAME_*` compile-time safety. |
| `M_Product` (typed entity) | `X_AdProductDim`/`M_AdProductDim` — typed getters, units in meters, dimension validation | **DONE** — ORMSandbox. S-ORM-3 smoke test enforces meter units. |
| `M_BOM` / `M_BOM_Line` (typed BOM) | `X_M_BOM`/`MBOM`, `X_M_BOMLine`/`MBOMLine` — 3 dimensions (category+owner+SpaceSize) | **DONE** — ORMSandbox. BOM.db split, 72 m_bom_line with SpaceSize, 16 buffer children. |
| `C_Order` (typed transaction) | `X_AdBuildingRegistry`/`M_AdBuildingRegistry` | **DONE** — ORMSandbox + TopologyMaker. 4 buildings, `getAll()`, `getByBuildingId()`. |
| `C_OrderLine` (typed line) | `X_AdElementRule`/`M_AdElementRule` — nullable getters (`getHeightMmOrNull()`) | **DONE** — ORMSandbox. `getByBuilding()` via ModelQuery. |
| `M_InOut` / CO (document output) | `X_CO_EmptySpace`/`M_CO_EmptySpace`, `X_CO_EmptySpaceLine`/`M_CO_EmptySpaceLine` | **DONE** — Output DB. WriteStage creates header + per-storey lines. IsAvailable quality gate. |
| `DocAction` (IsAvailable lifecycle) | ProveStage: IP→CO (is_available=0) on success, IP→RE on violations | **DONE (partial)** — quality gate live, but no full `processIt()` state machine yet. |
| `InfoWindow` (debug/inspect) | `BuildingInspector` — 8 CLI commands, typed PO navigation, preflight checks | **DONE** — `ORMSandbox/`. Diagnosed G8 frame-of-reference bug in one session. |
| `MRP BOM Explosion` — type-blind resolution | `BOMTierResolver` — unified resolver for furniture + fixtures + structural. Three-way dispatch: fixture params → GPD walk → FLOAT dx/dy. `BOMTreeLoader` — shared AD-layer tree infra. `FurnitureWorker` calls resolver directly (no intermediary). | **DONE (G-1 Steps 1-3)** — TOILET_BLOCK + DUPLEX_BATHROOM resolve through same path. FurniturePlacer intermediary eliminated. Step 5 pending: hardcoded stall dividers. |
| `ModelValidator` (full hook interface) | `CompilerValidator` with beforeResolve/afterResolve/beforeWrite | Future (Phase E) |
| `DocAction / processIt()` (full lifecycle) | `BtBuilding.processIt()` state machine: DRAFT→COMPILING→VALIDATED→RELEASED | Future (Phase E) |
| `Callout` | `CalloutRoom.onSpaceTypeChanged()` | Future (Phase F — GUI) |
| `AD_ChangeLog` | `sys_changelog` audit trail | Future |
| `M_PriceList` | M_ProductPrice per region | Future (5D BIM) |
| `AD_Window / AD_Tab` | Editor tab cascade (Section 6); abstract geometry block iteration (Section 13) | Future (Phase F — GUI / Phase G — abstract engine) |

---

## 12. Implementation Phasing — CRITICAL: READ BEFORE ANY CHANGES

**⚠️ DO NOT rename tables, create domain prefixes, or refactor Java patterns without explicit instruction from the architectural watchdog. This document is a REFERENCE MAP, not an execution plan.**

The migration happens in strict phases. Each phase has prerequisites and verification gates.

### Phase A: Awareness Only (COMPLETE — 2026-02-20)

Used the domain map to guide new table creation. New tables used correct prefixes: `m_bom`, `m_bom_line`, `m_attribute`, `M_BomCategory`, `co_empty_space`, `co_empty_space_line`. Existing `ad_*` tables untouched (except BOM renames).

### Phase BOM: BOM Dimension Model (COMPLETE — 2026-02-25)

**Delivered:**
- BOM table rename: `ad_bom`→`m_bom`, `ad_bom_child`→`m_bom_line`, `ad_bom_child_param`→`m_attribute`
- `M_BomCategory` lookup (14 functional codes: LI/BD/KT/FR/ST/L1/L2/UN/SL/RF/DN/BT/CR/MN)
- `bom_owner` column on m_bom (SH/DX/TB/TE vendor scoping)
- SpaceSize columns (AABB mm) on 72 m_bom_line records
- 16 buffer (ST) children across all room BOMs
- 4 new BOM records: FLOOR_SLAB_GF, FLOOR_SLAB_L2, ROOF_STRUCTURE, ROOF_COVERING
- DX UNIT tree: GROUND_SLAB(5,dz=0)→L1(10,dz=0)→UPPER_SLAB(15,dz=3m)→L2(20,dz=0)→ROOF(25,dz=6m)
- Java POs: X_M_BOM/MBOM, X_M_BOMLine/MBOMLine, X_M_Attribute/MAttribute, X_M_BomCategory/MBomCategory
- Migration scripts: `migration_bom_dimension_model.sql`, `migration_bom_dimension_phase1_records.sql`, `migration_bom_dimension_phase1_spacesize.sql`

### Phase 4: 3-DB Split + CO_EmptySpace Pipeline (COMPLETE — 2026-02-25)

**Delivered:**
- BOM.db extracted from component_library.db (`migration/migration_bom_db_extract.sh`, idempotent)
- `CompilerConfig.BOM_DB_PATH = "library/BOM.db"` — canonical path constant
- Split-connection pattern: files querying both ad_* and BOM tables open two connections
- CO_EmptySpace + CO_EmptySpaceLine output tables with WriteStage population
- Per-storey decomposition: UNIT BOM children → bom_level=1 lines with storey names
- IsAvailable quality gate: IP→CO (is_available=0) on success, IP→RE on critical violations
- Tests: `ATTACH DATABASE 'library/BOM.db' AS bom_db` for cross-DB integrity queries

### Phase DAO: orm-core Framework (COMPLETE — 2026-02-23)

**Delivered:**
- `orm-core/` module: `BasePO` (250 lines) + `ModelQuery<T>` (174 lines)
- iDempiere X_/M_ pattern: 28 PO classes across ORMSandbox + TopologyMaker
- `BuildingInspector` CLI: 8 commands for typed navigation of full BIM construct
- 7 guardrails (see `orm-core/docs/BIMDAOTechnicalFramework.md`)
- Key boundary: **DAGCompiler does NOT depend on orm-core** — systems share only the SQLite file
- Smoke tests: S-ORM-1 through S-ORM-6 + 13 inspector tests + 3 EmptySpace + 5 BOM witnesses

### Phase B: DomainStore Wrapper (NEXT — prerequisites mostly met)

**Prerequisites (revised):** MetadataValidator ✅, CompilerStage interface ✅, BasePO + ModelQuery (typed access mechanism) ✅, sealed Placement types (full contract) ❌ NOT DONE.

**What remains:** The orm-core DAO layer provides the typed access mechanism (BasePO/ModelQuery/X_/M_ classes), but it lives outside DAGCompiler (by design — Guardrail 2). DomainStore would be a **DAGCompiler-internal** wrapper that provides domain-routed access to the same tables the orm-core POs cover, using DAGCompiler's own raw JDBC connections. Two paths to this:

1. **Thin wrapper over existing resolvers** — `DomainStore.cd().product(id)` delegates to existing SQL in RelationalResolver/BOMTierResolver. Additive, no risk.
2. **Port ModelQuery into DAGCompiler** — copy the pattern (not the dependency) and build typed domain accessors directly. Higher effort, cleaner result.

```java
// Path 1: wrapper (minimal change)
public class ComponentDomain {
    private final Connection conn;
    public CdProduct product(String id) {
        // Uses DAGCompiler's own SQL — same query RelationalResolver uses
        // Returns typed record instead of Map<String, String>
    }
}

// Path 2: DAGCompiler-native ModelQuery (higher effort, cleaner)
// Duplicate BasePO/ModelQuery idiom inside DAGCompiler, define X_/M_ pairs
// Benefit: compile-time column safety in the hotpath
```

**Verification:** 170+ tests green. SpatialDigests unchanged.

### Phase C–D: Typed Records + Table Rename (Per domain, incremental)

**Context change:** With orm-core's X_/M_ classes already covering 10 tables, Phase C is partially delivered for the **inspection path**. The remaining gap is the **compilation hotpath** (DAGCompiler), which still uses raw JDBC. Phase C for DAGCompiler depends on the DomainStore decision (Phase B).

Table rename (Phase D) has partially happened: BOM tables use `m_*` prefix, CO tables use `co_*`. Remaining `ad_*` tables await domain-by-domain migration.

### Phase E: Lifecycle + Validators

**Prerequisites:** BtBuilding typed record, RdCodeConstraint typed record.

**What to do:** Full `processIt()` state machine on C_Order (DRAFT→COMPILING→VALIDATED→RELEASED→VOIDED), register CompilerValidator hooks (beforeResolve/afterResolve/beforeWrite). The IsAvailable quality gate (Phase 4) is a partial delivery of this — it implements the IP→CO→RE transitions on CO_EmptySpace, not on the building itself.

### Phase F: Editor Patterns (When GUI development begins)

Callout hooks, AD_Val_Rule equivalent, tab cascade navigation. Months away.

### Phase G-1: Type-Blind BOM Compilation (IN PROGRESS — 2026-02-26)

Collapse type-specific fixture/furniture dispatch into abstract, metadata-driven BOM resolution. Single resolver (`BOMTierResolver`) handles ALL BOM tiers through data-driven dispatch. 4 steps:

| Step | What | Status |
|------|------|--------|
| 1 | Unified BOMTierResolver — fixture params + GPD + FLOAT three-way dispatch, FixtureWorker collapsed | **DONE** (2026-02-26) |
| 2 | BOMTreeLoader — shared AD-layer tree loader, canonical BOMNode/BOMChild records, iDempiere AD/Model separation | **DONE** (2026-02-26) |
| 3 | Kill FurniturePlacer intermediary — FurnitureWorker calls BOMTierResolver directly, normalizeRole() pass-through, delete FixtureWorker/FixturePlacer/FixturePlacerTest | **DONE** (2026-02-26) |
| 4 | Eliminate fallback code paths — delete FurniturePlacer.java, FurnitureTypeResolver.java, StoreyCompiler fallback block, BOMResolver/roomBOMs, addFurnitureToCtx | **DONE** (2026-02-26) |
| 5 | Data-drive stall dividers — STALL_DIVIDER BOM child with BETWEEN_SIBLINGS layout, delete hardcoded StoreyCompiler logic | QUEUED |

### Phase ST: Standard Mode — C_BPartner='ST' (DESIGN)

C_BPartner-agnostic BOM compilation through full CO_EmptySpaceLine layer-by-layer
process. The 1D Intent reduces the entire DSL to two C_Order fields:
`C_BPartner` (WHO) + `AABB` (HOW BIG). Everything else cascades from these.

**AABB on C_Order — CONFIRMED** as the governing building definition. The simplest
possible construction order: WHO + HOW BIG. Three new AABB columns on C_Order.
NULL-safe (C_BPartner-matched builds work unchanged).
Impact: 1 migration + 4 PO files + 2 Java files + 1 inspector check.

**Prerequisites:** Phase G-1 complete (type-blind BOM), AABB migration (TODO-ST-1).

**POC:** `ST_SH` / `ST_DX` registry entries producing same SpatialDigest as
SH/DX through indirect BOM selection. Proves engine before TB-LKTN unlock.

**Full spec + 7 TODOs + impact inventory:** See `docs/ConstructionAsERP.md` §3.7.

### Phase G: Abstract Compilation Engine (North star — Section 13)

StoreyCompiler collapses into MetadataCompiler. Element-type paths replaced by generic geometry block iteration. MEP becomes BOM children, not hardcoded writer. Every intermediate phase moves toward this.

### Phase Summary (updated 2026-02-26)

```
Phase   What                            When                          Status
──────────────────────────────────────────────────────────────────────────────────
  A     Awareness only                  2026-02-20                    COMPLETE
  BOM   BOM Dimension Model             2026-02-25                    COMPLETE
  4     3-DB split + CO_EmptySpace      2026-02-25                    COMPLETE
  DAO   orm-core framework              2026-02-23                    COMPLETE
  G-1   Type-blind BOM compilation      2026-02-26                    IN PROGRESS (Steps 1-4/5 done)
  ST    Standard Mode (C_BPartner='ST') After G-1 — POC on SH/DX     DESIGN (§3.7 spec + 7 TODOs)
  B     DomainStore wrapper             After ST — prerequisites 3/4  OPEN
  C–D   Typed records + table rename    Per domain, incremental       PARTIAL (orm-core side done)
  E     Lifecycle + validators          After B for C_Order lifecycle  OPEN (IsAvailable partial)
  F     Editor callouts                 GUI development               Future
  G     Abstract compilation engine     After E, Section 13 vision    Future (north star)
```

**CRITICAL RULES FOR CODE:**
1. **New tables use correct domain prefix** — never use `ad_` for construction model tables:
   - `m_*` — master data (m_bom, m_bom_line, m_attribute, M_BomCategory) → BOM.db
   - `co_*` — construction output (co_empty_space, co_empty_space_line) → output.db
   - `ad_*` — application dictionary ONLY (system config, product catalog, placement rules) → BOM.db
   - **Trap:** `ad_` is the iDempiere system dictionary prefix. Using it for BOM or output tables conflates configuration with working construction data. This mistake was made historically (`ad_bom`, `ad_bom_child`) and corrected in the BOM Dimension migration.
2. **Do NOT rename existing ad_* tables** without explicit watchdog instruction.
3. **Do NOT create DomainStore** until metadata integrity + sealed types are complete.
4. **Do NOT implement DocAction lifecycle** until typed records exist for C_Order.
5. **Each phase requires all tests green before AND after.**
6. **This document is the architectural north star, not a task list.**

---

## 13. The Abstract Compilation Engine: AD_Menu/Window/Tab/Field for Geometry

### 13.1 The iDempiere Insight That Matters Most

The most powerful idea in Compiere/iDempiere is NOT the AD_Table/AD_Column dictionary — that is just a prerequisite. The powerful idea is the **generic runtime engine**. `GridController` iterates `AD_Tab` records. `GridTab` iterates `AD_Field` records. `GridField` reads `AD_Column` + `AD_Reference` + `AD_Val_Rule` to decide type, validation, and display. The engine has ONE code path that renders EVERY window, EVERY tab, EVERY field — Sales Order, Purchase Invoice, Material Receipt, General Ledger Journal. Adding a new document type = adding AD rows. **Zero Java changes.**

The hierarchy that the engine traverses:

```
AD_Menu               →  navigation entry point
  AD_Window           →  a business document (C_Order, M_Product, ...)
    AD_Tab[0]         →  header level (C_Order header)
      AD_Field[0..n]  →  data elements at this level
    AD_Tab[1]         →  line level (C_OrderLine)
      AD_Field[0..n]  →  data elements at this level
    AD_Tab[2]         →  sub-line (M_AttributeSetInstance)
      AD_Field[0..n]  →  data elements at this level
```

The engine does not contain `if (window == "Sales Order")` anywhere. It reads `AD_Tab.SeqNo`, iterates, reads `AD_Field.SeqNo`, iterates, reads `AD_Column.AD_Reference_ID` to decide rendering. The entire UI + validation + defaulting system is a single metadata-driven iterator.

Every iDempiere developer and every LLM trained on Compiere/ADempiere/iDempiere already knows this pattern by heart. It is the most documented, most forked, most studied open-source ERP architecture in existence.

### 13.2 The BIM Compiler Equivalent: Geometry Blocks, Not Named Types

Apply the same abstraction to BIM compilation. In the ideal state, the compiler does not know what a "room" is, what a "toilet" is, or what a "storey" is. It knows only **geometry object blocks** — containers with metadata-defined children, constraints, and placement rules. The hierarchy:

```
iDempiere AD               BIM Compilation Engine           What It Means
─────────────────────────── ─────────────────────────────── ──────────────────────────
AD_Menu                     BuildingRegistry                 Entry point: which buildings
AD_Window                   Building                         A compilation unit
  AD_Tab[0] (header)          Storey level                   Structural frame container
    AD_Field (floor_z, h)       Storey attributes            Z offset, floor-to-floor
  AD_Tab[1] (line)             Room level                    Spatial container
    AD_Field (bounds, type)     Room attributes              Bounds, BOMCategory FK
  AD_Tab[2] (sub-line)         Slot level                    Assembly anchor point
    AD_Field (bom_ref, rule)    Slot attributes              BOM FK, rotation_rule
  AD_Tab[3] (detail)           BOM_Line level                Leaf component placement
    AD_Field (dx, dy, dz)       Placement attributes         Relative offset, rotation
```

**The engine has ONE loop.** At each level of the hierarchy, it:
1. **Reads** the metadata definition for this level (what type of block, what attributes)
2. **Iterates** the children defined for this block (via FK chain in master data)
3. **Resolves** the geometry (position, rotation, dimensions) from the block's attributes
4. **Validates** against constraints attached to this block (via bad_rule / rd_code_constraint)
5. **Descends** into the next level if children exist

```
for each building in registry:                         // ≈ AD_Window list
    for each storey defined in BOM(UNIT):              // ≈ AD_Tab[0] — M_BOM_Line WHERE BOMCategory=L1/L2
        resolve(storey.z_offset, storey.height)
        for each room BOM in this storey:              // ≈ AD_Tab[1] — M_BOM WHERE BOMCategory=BD/KT/BT/LI...
            resolve(room.bounds, room.BOMCategory)
            for each slot in room's M_BOM:             // ≈ AD_Tab[2] — M_BOM_Line children
                resolve(slot.anchor, slot.rotation)
                for each child in slot's BOM:          // ≈ AD_Tab[3] — from m_bom_line children
                    resolve(child.dx, child.dy, child.dz, child.rotation_rule)
                    emit(geometry_block)
```

**This loop is identical for a toilet, a kitchen counter, a ceiling fan, a structural column, a drain pipe, or a roof truss.** The metadata tells the engine:
- **What goes here** — BOM reference (m_bom FK)
- **How it's positioned** — rotation_rule, dx/dy/dz, wall_rule, locator_ref
- **What constraints apply** — bad_rule / rd_code_constraint references
- **How it relates to neighbors** — adjacency rules, clearance rules
- **What its physical shape is** — M_Product FK (width, depth, height, mesh_ref)

The engine never contains `if (isToilet)` or `switch (roomType)`. It reads metadata and iterates. **The intelligence is in the data.**

### 13.3 The Eight Compose Primitives as AD_Reference

The Rosetta Stone Strategy defines 8 compose primitives: PLACE, STACK, ARRAY, MIRROR, OFFSET, ROTATE, TRIM, BOOLEAN. These are the spatial verbs — the operations the engine can perform at any level of the hierarchy.

In iDempiere terms, these are `AD_Reference` values. Just as `AD_Reference_ID=17` means "List" (validated dropdown) and `AD_Reference_ID=19` means "Table Direct" (FK lookup), the 8 BIM primitives define how a geometry block composes with its parent:

```
AD_Reference         BIM Compose Primitive    What It Does
─────────────────── ─────────────────────── ──────────────────────────────────
AD_Ref: List         PLACE                   Position at absolute or relative point
AD_Ref: Table        STACK                   Place above/below parent (Z axis)
AD_Ref: TableDir     ARRAY                   Repeat at interval (sprinklers, lights)
AD_Ref: Search       MIRROR                  Reflect across axis (duplex party wall)
AD_Ref: Number       OFFSET                  Shift by dx/dy/dz from anchor
AD_Ref: Amount       ROTATE                  Rotate by rule or literal radians
AD_Ref: Quantity     TRIM                    Cut to container bounds (slab to room)
AD_Ref: YesNo        BOOLEAN                 Union/subtract/intersect geometry
```

Each `m_bom_line` row already carries enough metadata to select the primitive:
- `dx/dy/dz` → OFFSET (and PLACE when combined with locator)
- `rotation_rule` → ROTATE
- `layout_strategy` → ARRAY (future: sprinkler grid)
- `wall_rule: OPPOSITE_WORK` → MIRROR (child faces opposite to parent)
- `locator_ref: NORTH_WALL` → PLACE (wall-anchored)

The compose primitive is NOT a hardcoded switch in the engine. It is a **metadata attribute** on the BOM line, read and dispatched generically. Adding a ninth primitive (e.g., TAPER for parametric columns) = adding a handler method + an AD_Reference value. The iterator does not change.

### 13.4 Where We Are vs. Where This Goes

**Already metadata-driven (correct pattern):**

| Component | Iterates Over | Hardcoded Dispatch? |
|-----------|---------------|---------------------|
| `CompilationPipeline` | `STAGES` list (7 entries) | No — sequential `.execute()` |
| `RelationalResolver.computeAll()` | `rules` (List<ElementRule> from C_OrderLine) | No — per-rule dispatch via sealed PositionRule |
| `BOMTierResolver.resolveForRoom()` | `roomFurniture.children()` — three-way dispatch: fixture params → GPD walk → FLOAT dx/dy | No — dispatch by m_attribute keys + locator_ref metadata |
| `BOMTierResolver.expandBOMNode()` | `node.children()` recursively | No — pure BOM metadata iteration |
| `BOMTierResolver.resolveFixtureChildren()` | m_attribute params: `placement_wall`, `position`, `qty_rule`, `rotation_rule` | No — wall placement from data, not room-type `if` chains **(G-1 Step 1)** |
| `BOMTreeLoader.load()` | m_bom_line + m_attribute → canonical BOMNode/BOMChild — shared AD layer for both resolvers | No — iDempiere AD/Model pattern: tree infra in loader, resolution in concrete classes **(G-1 Step 2)** |
| `WorkerRegistry` default factory | `SlotRegistry` → assembly_id → `FurnitureWorker(bomId)` | No — any BOM ID routes through same worker **(G-1 Step 1)** |
| `FurnitureWorker.normalizeRole()` | BOM role string → downstream role string via `default → bomRole` pass-through | No — only genuine remaps are explicit; identity and fixture-param roles pass through **(G-1 Step 3)** |
| `BuildingInspector` preflight | 8 checks A–H from data queries | No — each check reads tables generically |
| `ProveStage` CO_EmptySpace | BOM tree walk → per-storey CO lines | No — walks m_bom_line metadata |

**Still type-aware (needs migration to metadata):**

| Component | What It Knows | Target: What Metadata Should Know Instead |
|-----------|---------------|-------------------------------------------|
| `StoreyCompiler` — element type paths | Wall vs. Opening vs. Fixture vs. MEP as separate code blocks | Single metadata-driven placement loop per room |
| `BOMTierResolver.resolveWithGPD()` — wall switch | `NORTH_WALL / SOUTH_WALL / EAST_WALL / WEST_WALL` as hardcoded cases | `locator_ref` → metadata row mapping angle + origin |
| `StoreyCompiler` — hardcoded stall dividers | `toiletCount - 1` divider logic in Java | **Data-drive** — STALL_DIVIDER BOM child with `BETWEEN_SIBLINGS` layout **(G-1 Step 5)** |
| `MEPWriter` — pipe/drain hardcoded logic | Pipe diameter, gradient, material as Java constants | m_bom_line children for MEP assemblies, attributes in m_attribute |
| `StoreyCompiler.applyPlacementOverrides()` | Bridge between old element-rule coords and new BOM system | Disappears when BOM feeds StoreyCompiler directly |
| `SlotRegistry.getSlotsForType()` — BOMCategory dispatch | BOMCategory string → slot list | M_BOM WHERE BOMCategory + C_BPartner |

### 13.5 The Compiere Benefit — Why This Matters

Compiere proved across 25 years and thousands of production installs that when the engine is metadata-driven:

**1. The core is simple and testable.**
iDempiere's `GridController` + `GridTab` + `GridField` is ~3,000 lines that handles EVERY window. One test harness covers Sales Orders, Purchase Invoices, Journal Entries, and any custom document. The BIM equivalent: one test harness that compiles a toilet, a kitchen, a structural bay, and a roof truss through the SAME code path. If the iterator works for a toilet BOM, it works for a kitchen BOM. Fix a placement bug once → fixed for every room type.

**2. Extensibility is free.**
Adding `C_PaySelection` (a new document type) to iDempiere requires zero changes to `GridController`. It reads the new AD_Window/Tab/Field rows and renders it. The BIM equivalent: adding a new BOMCategory (e.g., `LY` for Laundry) requires SQL INSERTs into M_BOM + M_BOM_Line. Zero Java. Adding a new building (e.g., `SHOPHOUSE_MY`) requires one C_Order INSERT with C_BPartner + AABB. Zero Java.

**3. Domain experts author directly.**
In iDempiere, a functional consultant defines a new document type by adding AD rows through Pack In (2Pack XML) — no developer needed. The BIM equivalent: an architect defines a new room template by inserting BOM rows — slot definitions, furniture assemblies, clearance rules. The compiler consumes it without modification. The architect IS the programmer at the data level.

**4. The knowledge base IS the product.**
Anyone can fork iDempiere's Java code. What they cannot easily replicate is 20+ years of AD rows encoding accounting rules, tax logic, document flows, and industry customisations across dozens of countries. The BIM equivalent: the compiler engine is open. The knowledge — 165+ BAD rules, 72+ BOM lines with spatial placement, room type definitions across jurisdictions — is the moat. **Competitors must replicate the data, not just the code.**

**5. Bugs cluster in the engine, not in the data.**
When iDempiere's `MOrder.completeIt()` has a bug, fixing it fixes order completion for every document type that uses it. No per-type regression. The BIM equivalent: when the BOM iterator has a placement bug, fixing it fixes placement for every room type, every building type, every jurisdiction. The data rows don't have bugs — they have wrong values, fixed by SQL UPDATE.

### 13.6 The Ideal Core — What the Compiler Becomes

When this vision is fully realised, the BIM compiler's hot path reduces to:

```java
/**
 * The entire compilation engine. ≈ GridController in iDempiere.
 * Iterates metadata-defined geometry blocks at each level.
 * Does not know what a room, storey, toilet, or wall IS.
 * Knows only: container → children → placement → constraints → emit.
 */
public class MetadataCompiler {

    /**
     * Compile a building. ≈ MOrder.completeIt()
     * Reads building → storey → room → slot → BOM → leaf
     * through unbroken FK chains in metadata.
     */
    public CompilationResult compile(BuildingOrder order) {
        GeometryBlock root = blockFactory.fromBOM(order.unitBomId());
        return resolveBlock(root, order.context());
    }

    /**
     * Resolve one geometry block and all its children. Recursive.
     * ≈ GridTab.dataNew() + GridField.validate() in iDempiere.
     *
     * A "room" is a block whose children are "slot" blocks.
     * A "slot" is a block whose children are "BOM line" blocks.
     * A "storey" is a block whose children are "room" blocks.
     * The engine does not distinguish — it reads metadata at each level.
     */
    private CompilationResult resolveBlock(GeometryBlock block, Context ctx) {
        CompilationResult result = new CompilationResult();

        // 1. Resolve this block's geometry from metadata
        Placement placement = resolver.resolve(block.placementRule(), ctx);

        // 2. Validate against attached constraints
        for (Constraint c : block.constraints()) {
            c.validate(placement, ctx);
        }

        // 3. Emit this block's geometry
        result.add(emitter.emit(block, placement));

        // 4. Descend into children (recursive — same code path)
        Context childCtx = ctx.descend(placement);
        for (GeometryBlock child : block.children()) {
            result.merge(resolveBlock(child, childCtx));
        }

        return result;
    }
}
```

**The entire engine is ~50 lines of meaningful logic.** Everything else — what goes where, how it's rotated, what constraints apply, which children it has — lives in the metadata. Complex buildings emerge from rich data fed through a simple, tested, generic engine.

This is the Compiere promise applied to construction: **complex but elegant, where the complexity is in the data and the elegance is in the engine.** iDempiere developers recognise this pattern instantly. LLMs trained on Compiere/ADempiere/iDempiere can reason about it directly. The BIM compiler becomes not a bespoke construction tool, but an instance of the most proven metadata-driven architecture in enterprise software — applied to geometry instead of accounting.

### 13.7 Migration Reality Check

The vision in 13.6 is the north star, not today's code. The path:

```
TODAY                          StoreyCompiler has element-type code paths
                               BOMTierResolver does generic BOM walk
                               RelationalResolver does sealed-type dispatch
                               MEPWriter is hardcoded
                               ─────────────────────────────────────────
Phase B (DomainStore)          Unified typed access to all domains
Phase C (Typed records)        GeometryBlock concept emerges from typed POs
Phase D (Table rename)         Clean domain prefixes, FK chains explicit
Phase E (Validators)           Constraint attachment to blocks via metadata
Phase G (Abstract Engine)      StoreyCompiler collapses into MetadataCompiler
                               Element-type paths replaced by block iteration
                               MEP becomes BOM children, not hardcoded writer
                               ─────────────────────────────────────────
NORTH STAR                     ~50-line engine + rich metadata = any building
```

**Phase G is far.** But every intermediate phase moves toward it. Every time we:
- Move a hardcoded value into a table row → closer
- Replace a type switch with a metadata lookup → closer
- Express a placement rule as a BOM line attribute → closer
- Add a BAD rule instead of a Java if-statement → closer

The code gets simpler. The data gets richer. The tests get fewer but more powerful. And the whole thing becomes something an iDempiere developer can understand in an afternoon — because they have been building exactly this for 25 years, just for accounting instead of geometry.

---

## 14. AD_Column-Level Mechanisms: Light DAO, Not OSGi

### 14.1 The Four Mechanisms

iDempiere governs field-level behaviour through four mechanisms, all anchored at AD_Column. These are the mechanisms that make the AD *active* — not just a schema dictionary but a runtime behaviour engine. Each maps cleanly to our light DAO (BasePO + ModelQuery, 424 lines total) without OSGi, event bus, or service registry.

#### AD_Val_Rule → ModelQuery WHERE Scoping

In iDempiere, `AD_Val_Rule` stores a SQL WHERE clause that constrains a field's lookup dropdown. When a Purchase Order's BPartner field opens, the dropdown shows only `C_BPartner WHERE IsVendor='Y'`. The rule is **data on AD_Column**, not Java code.

In our project, ModelQuery's `.where()` + `.andWhere()` chain IS the AD_Val_Rule evaluation engine:

```java
// TODAY — BOMTierResolver.loadBOMTree() already scopes by BOM ID:
new ModelQuery<>(bomConn, MBOMLine::new, X_M_BOMLine.Table_Name)
    .where(X_M_BOMLine.COLUMNNAME_bom_id + " = ?", bomId)
    .andWhere(X_M_BOMLine.COLUMNNAME_is_active + " = ?", 1)
    .list();

// AD_Val_Rule EQUIVALENT — room-scoped BOM lookup:
// "When editing a room of type BEDROOM in building DX,
//  only show BOMs where bom_category='BD' AND (bom_owner='DX' OR bom_owner IS NULL)"
// The rule IS the WHERE clause. Store it as a bad_rule row, evaluate via ModelQuery.
```

The only missing piece is a table that stores scoping rules as data rows instead of hardcoding the WHERE in Java. The `bad_rule` table (BIM_APPLICATION_DICTIONARY.md) already does this for placement constraints — extend the pattern to field-scoping rules with a new `bad_rule_category = 'SCOPING'`.

#### Callouts → BasePO Dirty Set + beforeSave

In iDempiere, a Callout fires when a specific AD_Column value changes in the editor. `CalloutOrder.bPartner()` fires when you change the BPartner on an order line — it auto-populates price list, payment terms, shipping address.

BasePO already tracks exactly which fields changed:

```java
// BasePO — already exists:
private final Set<String> dirty = new LinkedHashSet<>();

protected void set_Value(String columnName, Object value) {
    values.put(columnName, value);
    dirty.add(columnName);  // ← THIS is the callout trigger
}
```

The callout pattern needs only a one-line addition to BasePO (`isDirty(String col)`) and field-aware logic in the M_ layer:

```java
// M_AdElementRule — callout-style cascading in beforeSave
@Override
protected void beforeSave(boolean newRecord) {
    if (isDirty(COLUMNNAME_family_ref)) {
        String bomId = getFamilyRef();
        if (bomId != null && !bomExists(bomId))
            throw new IllegalStateException(
                "family_ref '" + bomId + "' not found in m_bom");
    }
    if (isDirty(COLUMNNAME_room_ref)) {
        // auto-populate bom_category from room type — Callout equivalent
    }
}
```

No registration engine, no event bus. The M_ class checks its own dirty set in `beforeSave()`. At 28-PO scale, direct override is cleaner than indirection.

#### ModelValidator → beforeSave/afterSave (Already There)

In iDempiere, `IModelValidator` implementations register against table names and fire at `TYPE_BEFORE_NEW`, `TYPE_BEFORE_CHANGE`, `TYPE_AFTER_NEW`, etc. The registration is via `ModelValidationEngine.addModelValidator()`.

BasePO's `beforeSave(boolean newRecord)` / `afterSave(boolean newRecord)` IS this pattern — just without the indirection of a registration engine:

```java
// iDempiere:  engine.addModelValidator(new MyValidator(), "M_BOM");
// Ours:       M_ class overrides beforeSave() directly — same effect, zero overhead

// M_AdBom.beforeSave() — ALREADY EXISTS AND WORKING:
@Override
protected void beforeSave(boolean newRecord) {
    if (getGroupBy() == null)
        throw new IllegalStateException("group_by must not be null");
}
```

For **cross-table** validation (e.g., "when saving m_bom_line, check parent m_bom.is_active"), the M_ class queries the related PO in `beforeSave()`. It has the `conn` reference and can use ModelQuery. No engine needed — the M_ class IS the validator for its own table.

#### DocAction/WfMC → processIt() on M_ Class

In iDempiere, `DocAction` is the state machine: `MOrder.processIt("CO")` transitions Draft→Complete, fires validators, creates downstream documents (M_InOut, C_Invoice). The WfMC (Workflow Management Coalition) engine routes complex multi-step approvals.

CO_EmptySpace already has the embryo (DR→IP→CO→RE). The full building lifecycle is the same pattern:

```java
// M_AdBuildingRegistry — processIt() is just a switch
public boolean processIt(String action) {
    return switch (action) {
        case "COMPILE"  -> { setDocStatus("IP"); yield true; }
        case "VALIDATE" -> {
            if (getProofFailCount() > 0) yield false;
            setDocStatus("CO"); yield true;
        }
        case "RELEASE"  -> { setDocStatus("RE"); yield true; }
        case "REOPEN"   -> { setDocStatus("DR"); yield true; }
        default -> throw new IllegalArgumentException(action);
    };
}
```

**WfMC is overkill.** iDempiere's WfMC engine handles multi-step approval workflows (manager→finance→warehouse). We have no approval chain. Our "workflow" is: compile→validate→release. A switch statement on the M_ class handles this. If we ever need approval chains (plan submission to local authority), we add a `doc_approval` table with sequential steps — still data rows + a loop, not an OSGi workflow engine.

### 14.2 Why Light DAO, Not OSGi

| OSGi Feature | iDempiere Use | BIM Compiler Need |
|---|---|---|
| Hot-deploy without restart | Enterprise: add plugin while 200 users are online | None — we restart on every `./scripts/run_tests.sh` |
| Classloader isolation | Multi-tenant: plugin A can't see plugin B's classes | None — single codebase, single process |
| Service registry (publish/find/bind) | Runtime discovery: `IModelFactory` keyed by table name | None — direct Java references, compile-time checked |
| 2Pack XML migration | Ship AD rows as plugin artifact | SQL migration scripts in `/migration/` — simpler, auditable |
| Event bus (`IEventManager`) | Decouple validator registration from table class | Not needed at 28-PO scale — direct `beforeSave()` override |

**What we DO need from the pattern, and already have:**

| iDempiere Pattern | Our Implementation | Status |
|---|---|---|
| PO dirty tracking + lifecycle hooks | `BasePO.dirty` + `beforeSave()/afterSave()` | **DONE** (250 lines) |
| Field-level callout trigger | `dirty.contains(columnName)` | **1 line to add**: `isDirty()` on BasePO |
| Typed query with scoping | `ModelQuery<T>.where().andWhere()` | **DONE** (174 lines) |
| Document state machine | `processIt(String action)` on M_ class | **~20 lines** per document type |
| Validation rule evaluation | `bad_rule` table + evaluate loop | **~50 lines** (reads bad_rule, pattern-matches) |

**Total framework: 424 lines.** iDempiere's equivalent (`PO.java` + `Query.java` + `ModelValidationEngine.java` + `DocAction.java`) is ~15,000 lines. We get 90% of the benefit at 3% of the code because we don't need multi-tenant, hot-deploy, or event bus indirection.

### 14.3 Concrete Next Steps for AD_Column Mechanisms

```
Step  What                                     Lines   Disruption
─────────────────────────────────────────────────────────────────
 1    Add isDirty(String col) to BasePO         1       None — additive
 2    processIt() on M_AdBuildingRegistry       20      None — additive, M_ class only
 3    bad_rule_category = 'SCOPING' rows        SQL     None — data only, no Java
 4    M_ beforeSave() reads bad_rule for        50      None — additive validation
      its table (validation from data)
 5    Callout-style cascading in M_ classes     per M_  Incremental — one M_ at a time
```

Each step is additive. None disrupts the compilation hotpath (DAGCompiler doesn't depend on orm-core). The gap-closing session can continue uninterrupted while these mechanisms are layered in.

---

## 15. The AD Mapping Frontier — Cross-Domain Analogical Reasoning

### 15.1 The Hard Problem

The most valuable work in this project is **not** writing Java or SQL. It is recognising which construction concepts ARE iDempiere AD concepts in disguise. This recognition requires:

1. **Deep familiarity with one domain** (iDempiere AD model — 25 years of ERP architecture)
2. **Deep familiarity with a different domain** (construction, BIM, MEP, structural engineering)
3. **The ability to see structural isomorphism** — not surface similarity but deep equivalence

The third step is genuinely difficult. It is closer to analogical reasoning than pattern matching. The mappings that drive this architecture were not obvious — they were discovered through iterative exploration by someone who has lived inside both domains.

### 15.2 Mappings That Required Domain Insight

Each of these mappings was a conceptual leap, not a mechanical derivation:

| Construction Concept | iDempiere Mapping | Why It's Non-Obvious |
|---|---|---|
| A building | C_Order | Buildings don't "order" anything — but they ARE configured selections from a catalog, processed through a lifecycle, producing auditable output. The isomorphism is structural, not semantic. |
| A room | M_Product (+ C_DocType hybrid) | A room is both a *thing with dimensions* (product) and a *type that governs processing rules* (document type). Dual nature — no single iDempiere entity has both roles. |
| A storey | C_Period | Time periods and vertical divisions share the same structure: sequential, non-overlapping, collectively exhaustive, with a type (TYPICAL/GROUND/ROOF ≈ STANDARD/ADJUSTMENT/CLOSING). |
| Structural grid | C_AcctSchema + C_ElementValue | A grid system organises space the way an accounting schema organises value. Grid lines are to bays what account codes are to ledger entries. |
| Building codes | C_BPartner + C_Tax | External authorities that constrain the system. UBBL is a "business partner" the building must satisfy. Fire codes are "tax rates" per building category. |
| BOM buffer space | Phantom assembly spacer | Buffer children (bom_category='ST') are the spatial equivalent of MRP phantom components — no physical geometry, but structurally necessary for the parent's invariant (SUM(children) = parent). |
| CO_EmptySpace | M_Locator + availability | Construction space tracking: available capacity, bin allocation, spatial addressing. The IsAvailable flag is the "is this space free?" query. |

### 15.3 The MEP Frontier — An Open Mapping Problem

MEP (Mechanical, Electrical, Plumbing) systems **cut across** the container hierarchy. A riser shaft is not contained by a room or a storey — it pierces through multiple floors, connecting fixtures on Level 1 to fixtures on Level 2 through a vertical column. This cross-cutting nature makes it the hardest AD mapping problem in the project.

**Candidate iDempiere analogies (none fully satisfactory):**

| iDempiere Concept | MEP Parallel | Why It Falls Short |
|---|---|---|
| M_Product used in multiple C_OrderLines | A pipe fitting appears in plumbing order AND fire protection order | Captures reuse but not spatial continuity |
| C_Charge (cross-document cost) | A cost that cuts across orders (freight, handling) | Right shape (cross-cutting) but wrong domain (cost, not geometry) |
| GL posting rules | Balancing constraint across accounts when order completes | Closest: a spatial invariant that must balance across floors. Riser at (x,y) on floor N must connect to (x,y) on floor N+1. The "balancing" is geometric alignment, not monetary. |
| Inter-org transaction | C_Order in Org A creates M_InOut in Org B | Captures cross-boundary linkage but not vertical spatial continuity |
| M_Locator transfer | Stock moves from bin X to bin Y through a valid path | Closest spatial analogy: you can't teleport stock past a locked cage, you can't teleport water past a missing pipe joint. The "valid path" constraint is spatial and cross-container. |

**What may be needed: new AD vocabulary for spatial continuity.** The 8 compose primitives (Rosetta Stone: PLACE, STACK, ARRAY, MIRROR, OFFSET, ROTATE, TRIM, BOOLEAN) extend the AD into spatial territory that iDempiere never needed. These are spatial verbs with no accounting equivalent. They extend the AD pattern the same way iDempiere extended Compiere into manufacturing (PP_Product_BOM, PP_Order) — same architecture, new domain-specific tables and concepts.

MEP continuity may require a new primitive or a new table concept:
- **CONNECT** — a spatial verb meaning "this port on object A must align with this port on object B across a container boundary"
- **`bad_rule_category = 'INTER_FLOOR'`** — already defined in BIM_APPLICATION_DICTIONARY.md (IFL_001 through IFL_005), but the enforcement mechanism is unresolved
- **`m_attribute` port references** — leaf attributes already hold `port_count`, `port_diameter` etc. The missing piece is the **cross-floor FK chain** that says "port 1 on TOILET(L1) connects to port 1 on TOILET(L2) via RISER_SHAFT_01"

This is an open problem. The identification of the correct mapping requires the same kind of cross-domain analogical reasoning that produced the original "building = C_Order" insight. It cannot be derived mechanically from either the iDempiere codebase or the construction domain alone — it requires seeing the isomorphism between them.

### 15.4 What Can Be Done Mechanically vs. What Requires Insight

**Mechanical (automatable after mapping is identified):**
- Writing X_/M_ PO classes for a mapped table
- Generating ModelQuery WHERE clauses from bad_rule rows
- Implementing processIt() state machines from doc_status transitions
- Writing beforeSave() validators from constraint definitions
- Tracing FK chains and updating documentation
- Running tests and verifying SpatialDigests

**Requires domain insight (the architect's role):**
- Identifying that a construction concept IS an AD concept
- Choosing which iDempiere pattern applies (M_Product vs C_DocType vs C_BPartner)
- Recognising when NO existing iDempiere pattern fits and new vocabulary is needed
- Seeing cross-cutting constraints (MEP, fire escape paths, structural load paths) as AD-level problems
- Deciding when a BOM child is a phantom assembly vs. a real component
- Knowing which building code clause maps to which bad_rule property

**The architecture's strength is that once a mapping is identified, the mechanical elaboration is large and reliable.** One conceptual insight ("buffer space = phantom assembly spacer") produces dozens of correct SQL rows, Java classes, and test assertions. The insight is rare and expensive. The elaboration is common and cheap. The documentation captures both — the insight in the mapping tables, the elaboration in the code patterns — so that future sessions can build on identified mappings without re-deriving them.

---

## 16. AI-Assisted Architecture — The Human–AI Collaboration Model

### 16.1 This Document as a Demonstration

This document is itself a product of the collaboration model it describes. It was not written by the architect alone, nor by the AI alone. It was produced through a specific division of labour that exploits what each party does well:

**The architect (red1) provides:**
- The original conceptual mapping: "a building IS a C_Order"
- Domain expertise in both iDempiere and construction
- The judgment calls: which iDempiere pattern applies, when none fits, when new vocabulary is needed
- Architectural direction: "make the AD jump early," "use light DAO not OSGi," "MEP cuts across objects"
- Quality control: recognising when the AI's elaboration is correct vs. when it's plausible but wrong

**The AI (Claude) provides:**
- Codebase exploration: reading 28 PO classes, 1000+ line documents, git history, and cross-referencing them in minutes
- Mechanical elaboration: given "buffer space = phantom assembly," generating the SQL tables, Java patterns, test assertions, and documentation sections
- Pattern completion: given "AD_Val_Rule maps to ModelQuery WHERE," filling in the full comparison table, code examples, migration steps
- Consistency maintenance: updating Section 11.0, 11.6, and 12 to reflect the actual state of 28 PO classes, 3-DB split, 170 tests — catching every stale reference
- Documentation at scale: writing Sections 13–16 (~3000 words of structured architectural prose) in a single session, cross-referenced and internally consistent

### 16.2 The Collaboration Pattern

The working pattern that produced this architecture has a specific shape:

```
ITERATION N:
  1. Architect identifies a conceptual mapping
     ("rooms, storeys, furniture are just geometry blocks
      and the compilation iterates like AD_Menu/Window/Tab/Field")

  2. AI explores the codebase to ground the concept in reality
     (reads StoreyCompiler iteration patterns, BOMTierResolver tree walk,
      RelationalResolver sealed-type dispatch — 28 files, 5 minutes)

  3. AI elaborates the mapping into structured documentation
     (Section 13: abstract engine, 8 compose primitives, current vs. ideal audit,
      migration path — internally consistent with Sections 11–12)

  4. Architect reviews, corrects, and redirects
     ("also cover AD_Val_Rule, Callouts, ModelValidator, DocAction")

  5. AI elaborates the new direction
     (Section 14: four mechanisms, light DAO comparison, concrete steps)

  6. Architect identifies the frontier
     ("MEP cuts across objects — this is rather AGI-like")

  7. AI maps what it can, honestly flags what it cannot
     (Section 15: candidate analogies for MEP, none fully satisfactory,
      marks it as open problem requiring domain insight)

  8. Architect validates the honesty, adds new direction
     ("document your role in this — it's demonstrative of AI-assisted process")

  9. AI documents the meta-level (this section)
```

Each iteration amplifies the architect's insight. The AI's role is not to have the insight — it is to **make the insight productive at scale**. One sentence from the architect ("building = C_Order") produces 50 pages of consistent, cross-referenced, implementable documentation. Without the AI, the same elaboration would take weeks. Without the architect, the AI would produce plausible but wrong mappings.

### 16.3 Where This Model Breaks Down

The model has known failure modes:

**False confidence in analogies.** The AI can construct a convincing argument that MEP riser continuity maps to GL posting rules (Section 15.3). The argument is structurally coherent. It may also be wrong. The architect must evaluate analogies against domain experience, not against internal consistency. A beautifully documented wrong mapping is worse than no mapping.

**Mechanical drift.** When the AI updates 10 sections of a document in one session, it can introduce subtle inconsistencies with other documents (ConstructionAsERP.md, BIMDAOTechnicalFramework.md, PROGRESS.md) that it didn't read in full. Cross-document consistency requires the architect's eye or an explicit verification pass.

**Pattern completion without understanding.** The AI can generate `M_AdBuildingRegistry.processIt("COMPILE")` that looks exactly like `MOrder.processIt("CO")` because it has seen thousands of iDempiere examples. But it doesn't *understand* why the state machine exists — it pattern-matches. If the BIM domain needs a state transition that has no iDempiere precedent, the AI will either force-fit an existing pattern or flag the gap. The architect must watch for force-fitting.

**The AGI boundary.** The architect correctly identified (Section 15) that cross-domain analogical reasoning — seeing that two concepts from unrelated domains share deep structural equivalence — is at the frontier of what AI can do. The BIM compiler project sits exactly on this boundary: the mappings between iDempiere and construction are real and productive, but discovering them requires a kind of reasoning that is more synthesis than analysis. The AI assists the synthesis; it does not originate it.

### 16.4 Implications for the Project

**Documentation is the multiplier.** Every mapping the architect identifies and the AI documents becomes reusable context for future sessions. Session N+1 reads Sections 13–15 and can build on the mappings without re-deriving them. The documentation is not a record of past work — it is a context injection for future work.

**The architect's scarcest resource is insight, not time.** The AI handles the time-consuming mechanical work (codebase exploration, code generation, documentation, test writing). This frees the architect to spend their time on the high-value activity: identifying which construction concept maps to which AD concept, and recognising when no mapping exists.

**The light DAO decision was architecturally correct.** Using BasePO + ModelQuery (424 lines) instead of full iDempiere/OSGi means the AI can hold the entire framework in context and reason about it completely. A 15,000-line PO.java would exceed useful context. The light framework is not just simpler for humans — it is more effective for AI-assisted development because the AI can see all of it at once.

**Concurrent sessions are natural.** One session closes implementation gaps (data migration, test fixes, SpaceSize seeding). Another session works the AD mapping frontier (this document). They don't conflict because the architectural documentation is additive — it describes target patterns and mappings, not current code. The gap-closing session makes the code approach the documented target; the architecture session extends the target.

---

## 17. The Big Picture: From Downstream Handler to Construction Engine

### 17.1 The Journey — Dimension by Dimension

The project's origin was not this compiler. It began as a **downstream handler** for Autodesk: an IfcOpenShell addon (`github.com/red1oon/`) that migrated IFC files into a FederatedModel spatial database. The motivation was pragmatic — the 51,723-element SJTII Terminal complex simply crashed Bonsai when loaded as IFC. The FederatedModel DB approach made it smooth, because SQL queries scale where in-memory object graphs do not.

Once that database existed, higher BIM dimensions became natural extensions:

| Dimension | What it adds | How it fell out of the FederatedModel DB |
|---|---|---|
| **3D** | Geometry + spatial structure | The DB itself — elements, R*Tree, tessellation |
| **4D** | Time/scheduling | Storey sequences, construction phasing as query |
| **5D** | Cost/quantity | Element counts, material layers → BOQ extraction |
| **6D** | Sustainability/colouring | Surface styles, material RGBA, transparency |
| **7D** | IoT/facility management | IfcSensor elements, natural language query over DB |

The 4D–7D features were not planned. They emerged because the DB schema was **generic enough** that each new dimension was just another table join. Colouring (6D) was a surface_styles table. Natural language query (7D) was SQL generation over the existing schema. No architectural change — just more data.

This is the iDempiere lesson in miniature: **a generic schema with metadata-driven dispatch scales to use cases the designer never anticipated.** AD_Window was built for purchase orders and became the framework for manufacturing, CRM, and HR — not because it was designed for those domains, but because it was designed for *no specific domain*.

### 17.2 The Dissatisfaction — Why Downstream Is Not Enough

The FederatedModel addon saved users from investing further in Autodesk licensing over disparate applications. But it was still **downstream**: it consumed IFC that Autodesk (or ArchiCAD, or Tekla) produced. The value chain remained:

```
Autodesk (authoring) → IFC (exchange) → FederatedModel (viewing) → ERP (procurement)
```

The ambition was to replace the first link: authoring itself. But Autodesk has 40 years of CAD geometry kernel development — Revit, AutoCAD, Inventor, Civil 3D. Attacking the full 3D authoring problem head-on is too large for a small team.

### 17.3 The Flanking Move — From 3D Back to 1D

The first flanking move was **2D to 3D**: take floor plans and extrude them into buildings. But AI has a blindspot here. LLMs are trained on text, not spatial correlation. An LLM can describe a floor plan but cannot reliably compute that Wall A at (5.0, 0.0) and Wall B at (5.0, 4.0) form a room 4 metres deep. Geometry is not its training modality. Months of frustration confirmed this.

The breakthrough was an **abstract extrapolation**: if 2D→3D is too hard because AI cannot correlate geometry, go one step further back to **1D — pure intent**. A building is not a geometry problem first. It is a specification:

```
"5-bedroom house, Malaysian UBBL, 2 storeys, 1200 sq ft per floor"
```

One dimension. No coordinates. No geometry. Just **what the user wants**. The DAG Compiler DSL was born from this insight: the user declares intent, the compiler resolves it to geometry through metadata lookup, not spatial computation.

This is why the compiler works where the 2D→3D approach failed:
- **2D→3D** asks AI to do spatial reasoning (its weakness)
- **1D→3D via metadata** asks AI to do pattern matching and table lookup (its strength)

The AI never computes a wall position. It reads C_OrderLine rows that say "wall on north face at fraction 0.3" and resolves the coordinate from Room Boundary. Every value from the library. Nothing computed that could be looked up.

### 17.4 The Rosetta Stone — When There Is No Dictionary

But even metadata-driven compilation hits a wall: **who writes the metadata?** The reference buildings (SampleHouse, Duplex, Terminal) provided the initial dictionary — extract positions from IFC, store as AD rows, compile back. 100% fidelity proved the approach works.

The harder problem is compiling buildings that have **no reference IFC**. This is the Rosetta Stone challenge: how do linguists decode a language they have never heard? They find a bilingual inscription — the same text in a known language and the unknown one — and build a dictionary entry by entry.

The project's Rosetta Stones are the three reference buildings. Each one is a bilingual inscription:
- **Known language**: the IFC file (full geometry, every coordinate)
- **Unknown language**: the intent DSL (abstract specification)

By extracting the correspondence (IFC element → AD metadata row → DSL parameter), the project builds a dictionary of construction patterns. Each pattern becomes reusable: "a toilet room in a Malaysian residential building" is not a one-off extraction but a **library entry** that any future building can invoke.

But this dictionary-building is excruciating, because it requires introducing concepts from multiple domains simultaneously:
- **iDempiere Java patterns** (PO, ModelQuery, BOM explosion, DocAction)
- **Construction ontology** (storey, room, wall face, BOM hierarchy)
- **IFC semantics** (IfcSpace, IfcWall, IfcBuildingStorey relationships)
- **Spatial mathematics** (rotation rules, anchor resolution, Z offsets)

Each concept maps to the others, but the mapping must be **discovered through domain insight**, not mechanically derived. This is the same cross-domain analogical reasoning discussed in Section 15 — and it is why the dictionary grows slowly despite the tooling being fast.

### 17.5 The Endgame — Topology Library and Ontology Editor

Once the Rosetta dictionary is rich enough, the endgame becomes clear:

**Phase 1 (current): Build the dictionary.** Extract topologies from reference buildings. Map each topology to BOM constructs. Validate with witnesses. This is the excruciating phase — establishing the bilingual inscription for every construction pattern.

**Phase 2: Extrapolate the library.** With enough dictionary entries, new topologies become combinations of known patterns. A 3-bedroom house is a recombination of room BOMs, wall rules, and storey templates already in the library. The compiler dispatches from metadata; the user selects from a catalog. The library grows combinatorially while the compiler stays fixed.

**Phase 3: Ontology editor.** A GUI that manages the library orthogonally — room types on one axis, building profiles on another, BOM categories on a third. Users compose buildings by selecting from dimensions, not by drawing geometry. The editor writes AD rows; the compiler reads them. The same separation that makes iDempiere's Application Dictionary powerful: the dictionary editor and the runtime engine are independent.

```
┌─────────────────────────────────────────────────────────────┐
│  ONTOLOGY EDITOR (GUI)                                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐ │
│  │ Room     │  │ Building │  │ BOM      │  │ Compliance   │ │
│  │ Types    │  │ Profiles │  │ Category │  │ Profiles     │ │
│  │ (KT,BD, │  │ (SH,DX,  │  │ (LI,BD,  │  │ (MY-UBBL,   │ │
│  │  WB,LR) │  │  TB,TE)  │  │  KT,FR)  │  │  UK-Regs)   │ │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └──────┬───────┘ │
│       │              │             │               │          │
│       └──────────────┴─────────────┴───────────────┘          │
│                          │ writes                             │
│                          ▼                                    │
│              ┌───────────────────────┐                        │
│              │  AD Metadata Tables   │                        │
│              │  (component_library,  │                        │
│              │   BOM.db, rules)      │                        │
│              └───────────┬───────────┘                        │
│                          │ reads                              │
│                          ▼                                    │
│              ┌───────────────────────┐                        │
│              │  DAG Compiler Engine  │                        │
│              │  (metadata-driven,    │                        │
│              │   no hardcodes)       │                        │
│              └───────────┬───────────┘                        │
│                          │ emits                              │
│                          ▼                                    │
│              ┌───────────────────────┐                        │
│              │  Output DB → Bonsai   │                        │
│              │  → IFC → Permit       │                        │
│              └───────────────────────┘                        │
└─────────────────────────────────────────────────────────────┘
```

### 17.6 Why AD Is the Right Engine

The question is whether the iDempiere Application Dictionary pattern is powerful enough to serve as the underlying engine for this entire stack — not just the current compiler, but the library, the editor, and the scaling to arbitrary building types.

The answer is yes, and the evidence is structural:

**1. AD scales by data, not code.** iDempiere serves manufacturing, distribution, retail, healthcare, and government — not by having different codebases but by having different AD configurations. The same `MOrder.processIt()` handles a purchase order in a hospital and a sales order in a factory. The BIM compiler follows the same pattern: the same `BOMTierResolver.resolveForRoom()` handles a SampleHouse toilet and a Duplex kitchen. New room types, new building profiles, new compliance regimes — all are data changes. The compiler code is fixed.

**2. AD handles the combinatorial explosion.** A modest library of 20 room types × 10 building profiles × 5 compliance regimes × 3 BOM categories = 3,000 combinations. Without AD, this is 3,000 code paths. With AD, it is 3,000 rows in metadata tables dispatched by the same engine. This is exactly how iDempiere handles multi-tenant, multi-org, multi-warehouse configurations — not by code branching but by data scoping via `AD_Val_Rule`.

**3. AD separates the expert from the programmer.** In iDempiere, a business analyst configures windows, fields, and validation rules without writing Java. In the BIM compiler's endgame, an architect configures room BOMs, wall rules, and compliance profiles without writing Java. The ontology editor writes AD rows. The compiler reads them. The architect's domain expertise goes directly into the data layer, not through a programmer's translation.

**4. AD provides the audit trail.** Every AD row has provenance: `[EXTRACTED: SampleHouse]`, `[RESEARCHED: UBBL §41]`, or a C_BPartner tag. When a building fails compliance, the trail leads from the failed element back through the BOM line, through the element rule, to the AD row that specified it — and to the source that justified that row. This is `AD_ChangeLog` applied to construction: not just "what changed" but "why it was set that way."

**5. AD is the natural interface for ERP integration.** The project's subtitle is "Construction as ERP." When the compiler emits a `co_empty_space` with `is_available = 'CO'`, that is a completed document in iDempiere terms. The BOM explosion that produced it is `M_Production`. The material quantities are `C_InvoiceLine`. The AD pattern does not merely *model* these relationships — it **is** these relationships, waiting for the ERP bridge that connects `co_empty_space_line` to `M_InOutLine` for actual procurement.

### 17.7 The Full Arc

The project's trajectory, viewed whole:

```
FederatedModel addon (downstream IFC handler)
    ↓  "not satisfied being downstream"
2D → 3D attempt (AI geometry blindspot)
    ↓  "go back one more step"
1D Intent → DAG Compiler DSL
    ↓  "who writes the metadata?"
Rosetta Stone dictionary-building (current phase)
    ↓  "extrapolate known patterns"
Topology library (combinatorial scaling)
    ↓  "let architects manage it"
Ontology editor (AD-powered GUI)
    ↓  "connect to procurement"
ERP integration (C_Order → M_Production → C_Invoice)
```

Each step is a **flanking move** — avoiding the head-on assault on full CAD authoring by finding the abstraction level where metadata-driven compilation is more powerful than manual geometry. The iDempiere AD pattern is not just the implementation technique for one step; it is the **architectural spine** that connects all of them. The same tables, the same PO classes, the same validation rules, the same document lifecycle — from the first intent declaration to the final procurement order.

The FederatedModel addon proved that SQL databases can represent buildings. The DAG Compiler proved that metadata can generate buildings. The Rosetta Stones proved that the generation is faithful. What remains is scaling the dictionary and building the editor — and both are data problems, not code problems. That is the AD promise: the code is done when the engine is done. Everything after that is configuration.

---

## Cross-references

- **`docs/ConstructionAsERP.md`** — C_Order model, BOM explosion chain, CO_EmptySpace pipeline, IsAvailable lifecycle, variant selection, translation error diagnosis. The operational complement to this architectural document.
- **`orm-core/docs/BIMDAOTechnicalFramework.md`** — BasePO + ModelQuery specification, X_/M_ pattern, BuildingInspector CLI, 7 guardrails, entity coverage table. The implementation reference for the DAO layer described in Section 11.0.
- **`docs/BIM_APPLICATION_DICTIONARY.md`** — BAD rule tables (bad_rule, bad_rule_category, bad_discipline_priority). The constraint data layer referenced in Section 13.2 (block-level validation).
- **`docs/ADHistory.md`** — Historical lineage: Codd→Chen→ANSI/SPARC→Martin→SAP DDIC→Oracle AOL→Compiere→iDempiere. The intellectual heritage of the AD pattern this architecture extends to geometry.
- **`docs/BIMasBOMConcept.md`** — M_BOM 3 dimensions (Category + Owner + SpaceSize), ERD, buffer space model. The data model foundation for the BOM tables in Section 11.6.
- **`docs/PREFAB_ARCHITECTURE.md`** — Assembly hierarchy (6 levels), MRP BOM Drop chain. The structural BOM pattern behind the UNIT→FLOOR→ROOM→SET→ITEM traversal in Section 13.2.
- **`docs/TheRosettaStoneStrategy.txt`** — Linguist's Method, X-ray scoring, 8 compose primitives. The spatial verb vocabulary referenced in Section 13.3.
- **`docs/RELATIONAL_PLACEMENT_SPEC.md`** — Phase RM migration spec. The relational rules that replace flat coordinates, feeding the metadata-driven placement in Section 13.4.
- **`docs/concept-paper-compliance-gui.md`** — Bonsai addon architecture, FederatedModel DB integration, compiler ↔ spatial DB ↔ viewer loop. The downstream-to-upstream journey referenced in Section 17.
