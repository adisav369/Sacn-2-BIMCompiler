# BIM Compiler Domain Architecture: The iDempiere ERD Applied to Construction

**Version:** 1.0  
**Date:** 2026-02-20  
**Purpose:** Establish domain separation in the BIM compiler's data model following iDempiere's proven three-tier architecture: System Dictionary → Master Data Domains → Transaction Documents  
**Insight:** A building is an order. Space is the product. The DSL is the order entry form.

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
│  TIER 1: COMPILER DICTIONARY (cd_*)                     │
│  Defines the compiler itself — stages, rules, types     │
│  Owned by: Compiler architects (red1 + watchdog)        │
│  Changes: Rarely, affects all buildings                  │
├─────────────────────────────────────────────────────────┤
│  TIER 2: MASTER DATA DOMAINS                            │
│  Reusable construction knowledge shared across projects │
│                                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐             │
│  │  Space   │  │Component │  │  Code    │   ...       │
│  │  Domain  │  │  Domain  │  │  Domain  │             │
│  │ (DocType/│  │(Product) │  │(BPartner)│             │
│  │  Product)│  │          │  │          │             │
│  └──────────┘  └──────────┘  └──────────┘             │
│  Owned by: Domain experts (architects, engineers)       │
│  Changes: Periodic, per typology contribution           │
├─────────────────────────────────────────────────────────┤
│  TIER 3: BUILDING TRANSACTIONS (bt_*)                   │
│  Specific buildings referencing master data              │
│                                                         │
│  DSL (the "order") → Space Domain (what)                │
│                     → Component Domain (with what)      │
│                     → Code Domain (constrained by)      │
│                                                         │
│  Owned by: Architects / building designers              │
│  Changes: Constantly, per project                       │
│  Lifecycle: Draft → Compiling → Validated → Released    │
└─────────────────────────────────────────────────────────┘
```

---

## 3. The Five Domains

### 3.1 SPACE Domain (≈ C_DocType + M_Product hybrid)

**The core insight:** Space is both the product being configured AND the document type that governs how it's processed. A BEDROOM is a product (it has dimensions, requirements, slots) and a document type (it determines which validation rules apply, which assemblies are allowed, which compliance checks run).

**Root entity:** `sd_space_type` (Space Domain)

```sql
-- The "product" definition of a space
CREATE TABLE sd_space_type (
    space_type_id   TEXT PRIMARY KEY,      -- 'BEDROOM_MY', 'BATHROOM_UK', 'OFFICE_SG'
    space_name      TEXT NOT NULL,
    space_category  TEXT NOT NULL,          -- 'HABITABLE', 'WET', 'CIRCULATION', 'SERVICE'
    min_area_sqm    REAL,                  -- from code domain
    min_width_mm    INTEGER,               -- from code domain  
    min_height_mm   INTEGER,               -- from code domain
    ventilation     TEXT,                  -- 'NATURAL', 'MECHANICAL', 'BOTH'
    daylight_req    INTEGER DEFAULT 0,     -- 1 = requires window
    wet_area        INTEGER DEFAULT 0,     -- 1 = requires waterproofing
    region          TEXT,                  -- 'MY', 'UK', 'US', NULL = universal
    provenance      TEXT NOT NULL
);

-- What can go in this space (≈ M_Product_BOM for allowed components)
CREATE TABLE sd_space_slot (
    space_type_id   TEXT NOT NULL REFERENCES sd_space_type,
    slot_id         TEXT NOT NULL,
    slot_role       TEXT NOT NULL,          -- 'FURNITURE', 'SANITARY', 'MEP', 'LIGHTING'
    bom_id          TEXT NOT NULL,          -- REFERENCES cd_bom (component domain)
    is_mandatory     INTEGER DEFAULT 1,     -- 1 = must have, 0 = optional
    is_active       INTEGER DEFAULT 1,
    seq_no          INTEGER NOT NULL,
    provenance      TEXT NOT NULL,
    PRIMARY KEY (space_type_id, slot_id)
);

-- Clearance and relationship rules within the space
CREATE TABLE sd_space_clearance (
    space_type_id   TEXT NOT NULL REFERENCES sd_space_type,
    from_slot       TEXT NOT NULL,          -- slot_id or '*'
    to_slot         TEXT NOT NULL,          -- slot_id or 'WALL' or 'DOOR'
    min_gap_mm      INTEGER NOT NULL,
    context         TEXT,                  -- 'WALKTHROUGH', 'FUNCTIONAL', 'ACCESS'
    provenance      TEXT NOT NULL,
    PRIMARY KEY (space_type_id, from_slot, to_slot)
);

-- How spaces connect (adjacency requirements)
CREATE TABLE sd_space_adjacency (
    space_type_id   TEXT NOT NULL REFERENCES sd_space_type,
    adjacent_to     TEXT NOT NULL,          -- another space_type_id or category
    relationship    TEXT NOT NULL,          -- 'MUST_ADJOIN', 'SHOULD_ADJOIN', 'MUST_NOT_ADJOIN'
    reason          TEXT,                  -- 'UBBL: wet areas share plumbing wall'
    provenance      TEXT NOT NULL,
    PRIMARY KEY (space_type_id, adjacent_to)
);
```

**iDempiere parallel:**
| iDempiere | BIM Space Domain |
|-----------|-----------------|
| C_DocType | sd_space_type (governs processing rules) |
| M_Product | sd_space_type (has dimensions, attributes) |
| M_Product_BOM | sd_space_slot (what goes inside) |
| M_Product_Category | space_category (HABITABLE, WET, etc.) |
| C_DocType_Action | space validation rules per category |

### 3.2 COMPONENT Domain (≈ M_Product)

**The physical things that go into spaces.** Products with intrinsic geometry, material, and assembly relationships. This domain already largely exists in the codebase.

**Root entity:** `cd_product` (Component Domain)

```sql
-- The physical component (≈ M_Product)
CREATE TABLE cd_product (
    product_id      TEXT PRIMARY KEY,      -- 'TOILET_BACK_WALL_MY', 'BED_QUEEN'
    product_name    TEXT NOT NULL,
    product_category TEXT NOT NULL,         -- 'SANITARY', 'FURNITURE', 'STRUCTURAL', 'MEP'
    ifc_class       TEXT NOT NULL,          -- 'IfcSanitaryTerminal', 'IfcFurniture'
    width_mm        INTEGER NOT NULL,
    depth_mm        INTEGER NOT NULL,
    height_mm       INTEGER NOT NULL,
    mounting_face   TEXT,                  -- 'BACK', 'BOTTOM', 'NONE'
    material_name   TEXT,
    material_rgba   TEXT,
    is_active       INTEGER DEFAULT 1,
    provenance      TEXT NOT NULL
);

-- Assembly recipes (≈ M_BOM)
CREATE TABLE cd_bom (
    bom_id          TEXT PRIMARY KEY,      -- 'BED_SET', 'TOILET_BLOCK', 'FP_PIPE_ASSEMBLY'
    bom_name        TEXT NOT NULL,
    bom_type        TEXT NOT NULL,          -- 'ASSEMBLY', 'PHANTOM', 'KIT'
    target_ifc_class TEXT,
    is_active       INTEGER DEFAULT 1,
    provenance      TEXT NOT NULL
);

-- Assembly children with placement (≈ M_BOM_Component + offsets)
CREATE TABLE cd_bom_child (
    bom_child_id    INTEGER PRIMARY KEY AUTOINCREMENT,
    bom_id          TEXT NOT NULL REFERENCES cd_bom,
    child_product_id TEXT REFERENCES cd_product,     -- leaf component
    child_bom_id    TEXT REFERENCES cd_bom,           -- nested assembly (one of these two)
    role            TEXT NOT NULL,                     -- 'DESK', 'CHAIR', 'LAMP'
    seq_no          INTEGER NOT NULL,
    is_active       INTEGER DEFAULT 1,
    provenance      TEXT NOT NULL
);

-- Child placement parameters (≈ extended M_BOM_Component attributes)
CREATE TABLE cd_bom_child_param (
    bom_child_id    INTEGER NOT NULL REFERENCES cd_bom_child,
    param_key       TEXT NOT NULL,          -- 'rotation_rule', 'dx', 'dy', 'dz', 'wall_rule'
    param_value     TEXT NOT NULL,
    provenance      TEXT NOT NULL,
    PRIMARY KEY (bom_child_id, param_key)
);

-- Parametric mesh definitions (components generated by arithmetic, not imported)
CREATE TABLE cd_parametric_mesh (
    mesh_type       TEXT PRIMARY KEY,      -- 'GABLE_ROOF_MY', 'HIP_ROOF_UK'
    generator_class TEXT NOT NULL,          -- sealed interface permit name
    provenance      TEXT NOT NULL
);

CREATE TABLE cd_parametric_mesh_param (
    mesh_type       TEXT NOT NULL REFERENCES cd_parametric_mesh,
    param_key       TEXT NOT NULL,
    param_value     TEXT NOT NULL,
    param_unit      TEXT NOT NULL,
    provenance      TEXT NOT NULL,
    PRIMARY KEY (mesh_type, param_key)
);

-- Pricing per region (≈ M_ProductPrice per M_PriceList)
CREATE TABLE cd_product_price (
    product_id      TEXT NOT NULL REFERENCES cd_product,
    price_list_id   TEXT NOT NULL,          -- 'MY_PENINSULA', 'MY_SABAH', 'UK_SOUTH'
    unit_cost       REAL NOT NULL,
    currency        TEXT NOT NULL,          -- 'MYR', 'GBP', 'USD'
    lead_days       INTEGER,
    supplier        TEXT,
    valid_from      DATE,
    valid_to        DATE,
    provenance      TEXT NOT NULL,
    PRIMARY KEY (product_id, price_list_id)
);
```

**iDempiere parallel:**
| iDempiere | BIM Component Domain |
|-----------|---------------------|
| M_Product | cd_product (physical thing with dimensions) |
| M_BOM | cd_bom (assembly recipe) |
| M_BOM_Component | cd_bom_child + cd_bom_child_param |
| M_ProductPrice | cd_product_price (cost per region) |
| M_Product_Category | product_category |
| M_AttributeSet | cd_parametric_mesh_param (shape params) |

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

## 4. The Transaction Layer: Building as Order

**A building is a C_Order.** It references master data from all domains but doesn't own any of it. The DSL is the order entry form.

```sql
-- The building project (≈ C_Order header)
CREATE TABLE bt_building (
    building_id     TEXT PRIMARY KEY,
    building_name   TEXT NOT NULL,
    building_type   TEXT NOT NULL,          -- 'RESIDENTIAL', 'INSTITUTIONAL'
    region          TEXT NOT NULL,          -- 'MY', 'UK', 'US'
    grid_id         TEXT REFERENCES sf_grid_system,
    price_list_id   TEXT,                  -- for 5D costing
    doc_status      TEXT DEFAULT 'DRAFT',  -- DRAFT → COMPILING → VALIDATED → RELEASED
    dsl_content     TEXT NOT NULL,          -- the order entry (what the user authored)
    expected_elements INTEGER,
    spatial_digest  TEXT,
    is_active       INTEGER DEFAULT 1,
    seq_no          INTEGER DEFAULT 10,
    provenance      TEXT NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Building storeys (≈ C_OrderLine — each line is a storey)
-- Storey references sf_storey for structure, but is owned by the building transaction
CREATE TABLE bt_building_storey (
    building_id     TEXT NOT NULL REFERENCES bt_building,
    storey_id       TEXT NOT NULL REFERENCES sf_storey,
    is_active       INTEGER DEFAULT 1,
    seq_no          INTEGER NOT NULL,
    PRIMARY KEY (building_id, storey_id)
);

-- Room instances in this building (≈ C_OrderLine detail — specific rooms)
CREATE TABLE bt_room (
    room_id         TEXT PRIMARY KEY,
    building_id     TEXT NOT NULL REFERENCES bt_building,
    storey_id       TEXT NOT NULL REFERENCES sf_storey,
    space_type_id   TEXT NOT NULL REFERENCES sd_space_type,  -- what kind of room
    room_name       TEXT NOT NULL,                            -- 'bilik_utama', 'tandas_1'
    grid_bounds     TEXT,                                     -- 'C2-D4' grid reference
    x_min_mm        INTEGER,
    y_min_mm        INTEGER,
    x_max_mm        INTEGER,
    y_max_mm        INTEGER,
    unit_id         TEXT,                  -- for multi-unit: 'A', 'B'
    provenance      TEXT NOT NULL
);

-- Slot overrides per room instance (≈ C_OrderLine attribute overrides)
-- By default, room gets slots from sd_space_slot for its space_type_id
-- This table overrides: swap BED_SET for BED_SET_QUEEN, deactivate optional slot, etc.
CREATE TABLE bt_room_slot_override (
    room_id         TEXT NOT NULL REFERENCES bt_room,
    slot_id         TEXT NOT NULL,          -- from sd_space_slot
    override_bom_id TEXT REFERENCES cd_bom, -- NULL = use default, set = override
    is_active       INTEGER,               -- NULL = use default, 0 = disable, 1 = enable
    provenance      TEXT NOT NULL,
    PRIMARY KEY (room_id, slot_id)
);
```

---

## 5. The Complete Domain Map

```
┌──────────────────────────────────────────────────────────────────────────┐
│                    SYSTEM DOMAIN (sys_*)                                 │
│         Pipeline stages, validation rules, processes, changelog         │
│                     ≈ AD_* in iDempiere                                 │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────┐  ┌──────────────────┐  │
│  │   SPACE     │  │  COMPONENT  │  │   CODE   │  │    STRUCTURE     │  │
│  │   DOMAIN    │  │   DOMAIN    │  │  DOMAIN  │  │     DOMAIN       │  │
│  │   (sd_*)    │  │   (cd_*)    │  │  (rd_*)  │  │     (sf_*)       │  │
│  │             │  │             │  │          │  │                  │  │
│  │ space_type  │  │ product     │  │ authority│  │ grid_system      │  │
│  │ space_slot ─┼──┤►bom        │  │ constraint  │ grid_line        │  │
│  │ space_clear │  │ bom_child   │  │ fire_req │  │ storey           │  │
│  │ space_adj   │  │ bom_param   │  │ mep_size │  │ structural_member│  │
│  │             │  │ product_price  │          │  │                  │  │
│  │ "What kind" │  │ "Made of"   │  │"Rules by"│  │ "Organised on"   │  │
│  │             │  │             │  │          │  │                  │  │
│  │ ≈ DocType + │  │ ≈ M_Product │  │≈ BPartner│  │ ≈ AcctSchema     │  │
│  │   Product   │  │ + M_BOM     │  │ + C_Tax  │  │ + C_Period       │  │
│  └──────┬──────┘  └──────┬──────┘  └────┬─────┘  └────────┬─────────┘  │
│         │                │              │                  │             │
├─────────┼────────────────┼──────────────┼──────────────────┼─────────────┤
│         ▼                ▼              ▼                  ▼             │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │              BUILDING TRANSACTION (bt_*)                         │   │
│  │                    ≈ C_Order + C_OrderLine                       │   │
│  │                                                                  │   │
│  │  bt_building ──→ bt_building_storey ──→ bt_room                 │   │
│  │       │                                    │                     │   │
│  │       │         references:                │  references:        │   │
│  │       │         • sf_grid_system           │  • sd_space_type    │   │
│  │       │         • sf_storey                │  • sd_space_slot    │   │
│  │       │         • price_list_id            │  • cd_bom (via slot)│   │
│  │       │         • rd_code_authority         │  • cd_product       │   │
│  │       │           (via region)             │    (via bom_child)  │   │
│  │       │                                    │                     │   │
│  │       ▼                                    ▼                     │   │
│  │   DSL content                        bt_room_slot_override       │   │
│  │   (the order                         (swap BED_SET for           │   │
│  │    entry form)                        BED_SET_QUEEN)             │   │
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
├── Tab 0: Building (bt_building)
│   SELECT * FROM bt_building WHERE is_active = 1 ORDER BY seq_no
│   Fields: name, type, region, doc_status, dsl_content
│   
├── Tab 1: Storeys (bt_building_storey → sf_storey)
│   WHERE building_id = @building_id ORDER BY seq_no
│   Fields: storey_name, floor_level, floor_to_floor, storey_type
│   
├── Tab 2: Rooms (bt_room)
│   WHERE building_id = @building_id AND storey_id = @storey_id
│   Fields: room_name, space_type_id (dropdown filtered by region), grid_bounds
│   
├── Tab 3: Room Slots (sd_space_slot + bt_room_slot_override)
│   Base: sd_space_slot WHERE space_type_id = @space_type_id
│   Override: bt_room_slot_override WHERE room_id = @room_id
│   Fields: slot_role, bom_id (dropdown filtered by slot_role + region), is_active
│   
├── Tab 4: Assembly Detail (cd_bom_child)
│   WHERE bom_id = @selected_bom_id ORDER BY seq_no
│   Fields: role, child_product_id, rotation_rule, dx, dy, dz
│   READ-ONLY from building editor (edit in Component Library window)
│   
└── Tab 5: Code Compliance (rd_code_constraint)
    WHERE applies_to LIKE 'sd_space_type:' || @space_type_id
    Shows: all constraints that apply to the selected room type
    Status: PASS/FAIL per constraint based on current room dimensions
    READ-ONLY (edit in Code Authority window)
```

**Validation rules per tab (≈ AD_Val_Rule):**

```
Tab 2 (Rooms):
  space_type_id must be from sd_space_type WHERE region = @building.region OR region IS NULL
  
Tab 3 (Slots):
  bom_id must be from cd_bom WHERE bom_type matches slot_role
  Cannot deactivate mandatory slot (sd_space_slot.is_mandatory = 1)
  
Tab 5 (Compliance):
  Room area must satisfy rd_code_constraint WHERE property = 'min_area_sqm'
  Displays RED if violated, GREEN if compliant
```

---

## 7. The DSL as Order Entry

The DSL maps directly to transaction table inserts:

```
DSL Line                              → Transaction Record
─────────────────────────────────────────────────────────────────
BUILDING "CitizenHome"                → bt_building (building_id, name)
  TYPE RESIDENTIAL                    → bt_building.building_type = 'RESIDENTIAL'
  REGION MY                           → bt_building.region = 'MY'
  
  STOREY "Ground" HEIGHT 3000         → sf_storey + bt_building_storey
  
    ROOM "bilik_utama" TYPE BEDROOM_MY → bt_room (space_type_id = 'BEDROOM_MY')
      BOUNDS C2-D4                    → bt_room (grid_bounds, computed x/y)
      FURNISH SET BED_SET_QUEEN       → bt_room_slot_override (override default BED_SET)
      
    ROOM "tandas" TYPE BATHROOM_MY    → bt_room (space_type_id = 'BATHROOM_MY')
      BOUNDS D4-E4                    → bt_room (grid_bounds)
      -- no FURNISH override          → uses sd_space_slot defaults for BATHROOM_MY
```

**The DSL parser becomes a transaction document creator.** It doesn't compile geometry — it creates `bt_*` records that reference master data from all four domains. The compilation pipeline then reads the transaction records and resolves them through the domain references.

This is the same separation as iDempiere: the order entry screen (DSL) creates C_Order + C_OrderLine records. The document processing (Complete action) resolves the lines against M_Product, C_Tax, M_PriceList to produce M_InOut, C_Invoice, and GL postings. The user never touches the processing — they author the order, the system does the rest.

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

ALTER TABLE bt_building ADD COLUMN doc_status TEXT DEFAULT 'DRAFT';
ALTER TABLE bt_building ADD COLUMN doc_action TEXT;  -- next allowed action

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

## 9. Migration Path from Current ad_* Tables

The current `ad_*` tables map to the new domain prefixes:

```
CURRENT TABLE                 → NEW DOMAIN        → NEW TABLE
──────────────────────────────────────────────────────────────
ad_building_registry          → bt_ (transaction)  → bt_building
ad_room_boundary              → bt_ (transaction)  → bt_room
ad_storey_definition          → sf_ (structure)    → sf_storey
ad_element_rule               → bt_ (transaction)  → bt_element_rule (room-specific)
ad_wall_type                  → cd_ (component)    → cd_wall_type
ad_product_dim                → cd_ (component)    → cd_product
ad_bom                        → cd_ (component)    → cd_bom
ad_bom_child                  → cd_ (component)    → cd_bom_child
ad_bom_child_param            → cd_ (component)    → cd_bom_child_param
ad_room_slot                  → sd_ (space)        → sd_space_slot
ad_pipe_spec                  → rd_ (regulatory)   → rd_mep_sizing
ad_compiler_config            → sys_ (system)      → sys_compiler_config
ad_building_assertions        → sys_ (system)      → sys_building_assertions
(missing)                     → sd_ (space)        → sd_space_type
(missing)                     → rd_ (regulatory)   → rd_code_authority
(missing)                     → rd_ (regulatory)   → rd_code_constraint
(missing)                     → sf_ (structure)    → sf_grid_system
(missing)                     → sys_ (system)      → sys_doc_transition
(missing)                     → sys_ (system)      → sys_changelog
```

**Migration strategy:** Do NOT rename all at once. The domain prefix is the target. Current `ad_*` tables continue working. New tables use the correct prefix. Gradually migrate as each domain solidifies. The important thing is the **conceptual separation** — knowing which domain each table belongs to — not the prefix.

---

## 10. The Five Guarantees

When the domain architecture is complete, these invariants hold:

**1. Domain isolation.** A change to the Component Domain (new product, updated BOM) doesn't require changes to the Space Domain or Code Domain. A new Malaysian code (UBBL amendment) only touches rd_* tables. A new furniture set only touches cd_* tables.

**2. Transaction integrity.** A building (bt_building) references master data but doesn't duplicate it. Updating a wall type in cd_wall_type affects all buildings that reference it on next compile. Like updating M_Product price affects all future C_OrderLines.

**3. Lifecycle enforcement.** A RELEASED building cannot be edited. A DRAFT building can have PENDING provenance. A VALIDATED building has zero PENDING. Status transitions are explicit and auditable.

**4. Editor navigability.** Every table is reachable from bt_building through an unbroken FK chain. The GUI follows the chain: building → storey → room → slot → assembly → child → params. No dead ends, no string-matched joins.

**5. Compilation determinism.** The same bt_building record with the same master data always produces the same IFC output with the same SpatialDigest. The compilation is a pure function of the transaction + master data state. The sys_changelog tracks which master data state was active at compilation time.

---

*The BIM Intent Compiler is an ERP for buildings. The building is the order. Space is the product. Components are the bill of materials. Codes are the tax rules. The grid is the chart of accounts. The DSL is the order entry form. The compilation is the document posting. The witness proofs are the audit trail. Every pattern that makes iDempiere work for manufacturing and distribution makes this work for construction — because at the data level, building a house and building an order are the same operation: assembling configured components according to constrained rules.*

---

## 11. Java Code Evolution

The data model evolution (Sections 3–8) requires corresponding Java patterns. These follow iDempiere's proven architecture — every pattern below has a direct iDempiere equivalent that exists extensively in LLM training data.

### 11.0 Actual Current State (from codebase audit, 2026-02-20)

Before reading the patterns below, this is what actually exists in the codebase today:

| Pattern | Status | Actual Implementation |
|---------|--------|-----------------------|
| `CompilerStage` interface | **DONE** | `CompilationPipeline.java` — 8-stage typed chain |
| `MetadataValidator` as Stage 1 | **DONE** | Fires before all other stages, blocks on bad data |
| Sealed Placement types (partial) | **DONE** | `PositionRule.java` — sealed with DirectCoordinate, WallFraction, RoomFraction — this is placement computation, not the full Placement contract |
| `SlotRegistry` reads `ad_room_slot` | **DONE** | Lazy singleton, profile-aware (Phase 122D), `getSlotsForType(roomType, profile)` |
| `ManifestResolver` reads `ad_assembly_manifest` | **DONE** | Lazy singleton, graceful degradation if table absent |
| `MetadataStore` class with getOrThrow | **NOT DONE** | Pattern is table-specific lazy singleton Resolvers (SlotRegistry, ManifestResolver, BuildingRegistry). No unified store, no getOrThrow |
| `DomainStore` with typed domain namespaces | **NOT DONE** | No SpaceDomain, ComponentDomain, etc. |
| Typed domain records (CdProduct, BtBuilding) | **NOT DONE** | Raw SQL rows + Maps |
| Full sealed `Placement` interface (contract) | **NOT DONE** | PositionRule exists for computation; the placement contract sealed type from REFACTOR_SEALED_TYPES.md is not implemented |
| `RoomContext` record | **NOT DONE** | CompilationContext carries pipeline state but is not room-scoped |
| `CompilerValidator` hooks (beforeResolve, afterResolve, beforeWrite) | **NOT DONE** | Ad-hoc validators (PlacementValidator, OpeningPlacementValidator) |
| Domain table prefixes (sd_, cd_, rd_, sf_, bt_, sys_) | **NOT DONE** | All 66 tables use ad_* prefix |
| `provenance` column on all tables | **NOT DONE** | Only `ad_building_registry` and `ad_geometry_map` carry provenance |

**Phase B gate reality:** CompilerStage and MetadataValidator are done (2 of 4 prerequisites). MetadataStore.getOrThrow() and sealed Placement types are not. Phase B is not yet unlocked.

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

### 11.6 iDempiere Pattern Map

| iDempiere Pattern | BIM Compiler Equivalent | Status |
|-------------------|------------------------|--------|
| `AD_Process` (pipeline stages) | `CompilerStage` interface | **DONE** — 8-stage chain in CompilationPipeline.java |
| `ModelValidator.fireDocValidate()` (before save) | `MetadataValidator` as Stage 1 | **DONE** — blocks compilation on bad metadata |
| `AD_Val_Rule` (field constraints) | `ad_room_slot` (slot dispatch), `ad_assembly_manifest` (clearances) | **DONE as data**; SlotRegistry + ManifestResolver read them |
| `M_BOM` / `M_BOM_Component` computation | `PositionRule` sealed — DirectCoordinate, WallFraction, RoomFraction | **DONE (partial)** — computation semantics, not full placement contract |
| `PO` (PersistentObject) | Typed domain records (CdProduct, BtBuilding) | Future (Phase C) |
| `MTable.get()` | `DomainStore.cd().product(id)` | Future (Phase B) |
| `ModelValidator` (full hook interface) | `CompilerValidator` with beforeResolve/afterResolve/beforeWrite | Future (Phase E) |
| `DocAction / processIt()` | `BtBuilding.processIt()` lifecycle | Future (Phase E) |
| `Callout` | `CalloutRoom.onSpaceTypeChanged()` | Future (Phase F — GUI) |
| `AD_ChangeLog` | `sys_changelog` audit trail | Future |
| `M_PriceList` | `cd_product_price` per region | Future (5D BIM) |
| `AD_Window / AD_Tab` | Editor tab cascade (Section 6) | Future (Phase F — GUI) |

---

## 12. Implementation Phasing — CRITICAL: READ BEFORE ANY CHANGES

**⚠️ DO NOT rename tables, create domain prefixes, or refactor Java patterns without explicit instruction from the architectural watchdog. This document is a REFERENCE MAP, not an execution plan.**

The migration happens in strict phases. Each phase has prerequisites and verification gates.

### Phase A: Awareness Only (CURRENT PHASE)

**What to do:** Nothing changes in existing tables or Java. Use this document as the domain map. When creating NEW tables, use the correct domain prefix.

**Actual state from codebase audit (2026-02-20):**
- 66 tables in component_library.db — all `ad_*`, zero domain-prefix tables exist
- No `bad_*`, `sd_*`, `cd_*`, `rd_*`, `sf_*`, `bt_*`, `sys_*` tables exist yet
- Only 2 tables carry `provenance` column: `ad_building_registry`, `ad_geometry_map`
- `ad_room_slot` has a `profile` column (jurisdiction discriminator) — only populated for `Malaysian_Institutional` rows

**Phase A concrete action available now:** Create `bad_*` tables (BIM_APPLICATION_DICTIONARY.md) — they are new tables, additive, zero Java changes, correct prefix.

**Rules:**
- Existing `ad_*` tables: untouched
- Existing Java: untouched
- New tables: correct domain prefix
- Tests: 58 green, must stay green
- No migration SQL on existing tables

### Phase B: DomainStore Wrapper (After metadata integrity + sealed types complete)

**Prerequisites:** MetadataValidator as pipeline stage ✅ DONE, MetadataStore.getOrThrow() pattern throughout ❌ NOT DONE, sealed Placement types (full contract) ❌ NOT DONE, CompilerStage interface ✅ DONE.

**Phase B is 2/4 prerequisites met.** Remaining gates: unified MetadataStore replacing lazy singleton Resolvers, and full sealed Placement interface replacing PositionRule (computation) with Placement (contract).

**What to do:** Create DomainStore that wraps existing MetadataStore with domain-typed access. Internal implementation still reads `ad_*` tables.

```java
// DomainStore wraps existing tables — no rename needed
public class ComponentDomain {
    public CdProduct product(String id) {
        // Internally reads ad_product_dim — same table, typed access
        return CdProduct.fromRow(metadataStore.getRow("ad_product_dim", id));
    }
}
```

**Verification:** All 44+ tests green. SpatialDigests unchanged. DomainStore is additive — old MetadataStore still works.

### Phase C: Typed Domain Records (Per domain, one at a time)

**Prerequisites:** DomainStore wrapper working.

**What to do:** Create typed records for one domain at a time. Start with Component Domain (cd_*) because it has the most tables and the most unsafe access patterns.

**Sequence:** cd_ (Component) → sd_ (Space) → rd_ (Regulatory) → sf_ (Structure) → bt_ (Transaction)

**Verification per domain:** All tests green. SpatialDigests unchanged. Each domain is a pure refactor — behaviour identical, types added.

### Phase D: Table Rename (Optional, per domain, during domain refactor)

**Prerequisites:** DomainStore and typed records for that domain complete.

**What to do:** `ALTER TABLE RENAME` + find-and-replace in Java. One domain at a time. SQL views for backward compatibility if needed.

**Verification:** All tests green. SpatialDigests unchanged.

### Phase E: Lifecycle + Validators (After Phases B–C for bt_ and rd_ domains)

**Prerequisites:** BtBuilding typed record, RdCodeConstraint typed record.

**What to do:** Add `doc_status` to bt_building, implement `processIt()`, register CompilerValidator hooks.

**Verification:** All tests green. New lifecycle tests added. Existing buildings default to DRAFT status.

### Phase F: Editor Patterns (When GUI development begins)

**Prerequisites:** All typed domain records, lifecycle, validation rules.

**What to do:** Callout hooks, AD_Val_Rule equivalent, tab cascade navigation.

**This phase is months away. Do not anticipate it in current code.**

### Phase Summary

```
Phase  What                         When                          Disruptive?
─────────────────────────────────────────────────────────────────────────────
  A    Awareness only               NOW                           No
  B    DomainStore wrapper           After current refactors       No — additive
  C    Typed domain records          Per domain, incremental       No — pure refactor
  D    Table rename                  Optional, per domain          Low — mechanical
  E    Lifecycle + validators        After B+C for bt_ and rd_     Medium — new behaviour
  F    Editor callouts               GUI development               N/A — future
```

**CRITICAL RULES FOR CODE:**
1. **Phase A is active NOW.** Only use correct prefixes for genuinely NEW tables.
2. **Do NOT rename existing tables** without explicit watchdog instruction.
3. **Do NOT create DomainStore** until metadata integrity + sealed types are complete.
4. **Do NOT implement DocAction lifecycle** until typed records exist for bt_building.
5. **Each phase requires all tests green before AND after.**
6. **This document is the architectural north star, not a task list.**
