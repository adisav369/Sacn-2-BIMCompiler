# The ERP World View — Why Construction Is Manufacturing

> **Read this first.** Before any spec, any code, any schema. This is the lens
> through which every design decision in this project makes sense.

<div style="max-width: 620px; margin: 24px auto; padding: 20px 32px; background: linear-gradient(to right, #fff8e1, #fffde7, #fff8e1); border-left: 4px solid #ff8f00; border-right: 4px solid #ff8f00;">
<b>A building is a manufactured product with coordinates.</b> ERP already knows how to explode a BOM, track orders, and validate rules. This project applies that 30-year-old pattern to construction — same tables, same logic, spatial output instead of shop-floor output.
</div>

---

## The Three Concerns

iDempiere separates documents into header (C_Order) and lines (C_OrderLine),
with its BOM spatiality validated against [Validation Rules](DocValidate.md).
We inherit the same separation:

| Concern | iDempiere | BIM Compiler | Table |
|---------|-----------|-------------|-------|
| **WHAT** to build | Orders, Categories, Products | Which products, classified by [M_Product_Category](BOMBasedCompilation.md) + [AD_Org](DISC_VALIDATION_DB_SRS.md) | c_orderline |
| **HOW** to validate | BOMs, AttributeSets, Validation | [Spatial rules propose, regulatory rules gate](DocValidate.md) — by discipline and jurisdiction | ad_val_rule |
| **WHERE** it lands | Output.db for [4D–8D](ProjectOrderBlueprint.md) | Compiled placements + ASIs — the single source of truth downstream | output.db |

These three concerns are **never merged**. A change to WHAT (swap a product)
does not require changes to HOW (rules are independent — swap jurisdiction
without touching products) or WHERE (compiled output recalculates automatically). This is the architectural invariant that makes exception-based
ordering possible: override one concern, inherit the others.

**The downstream payoff:** output.db with its ASIs (per-instance attributes) is
what every downstream dimension reads — [4D scheduling](ProjectOrderBlueprint.md#51-4d-schedule-topological-sort-of-bom-tree),
[5D cost](ProjectOrderBlueprint.md#52-5d-cost-inherent-in-the-data-model),
[6D carbon](TIER1_SRS.md), [7D facility management](TIER1_SRS.md), and
8D ERP integration. One compiled output, seven queries.

### WHAT: M_Product_Category (Classification) + AD_Org (Discipline)

The WHAT concern has two orthogonal axes — **what kind of thing** and **who is responsible**.

**M_Product_Category — Product classification (WHAT kind of thing).**
The same entity iDempiere uses to group products into swap pools. Categories
form a [BOM](https://en.wikipedia.org/wiki/Bill_of_materials) cascade — a
[Bill of Materials](https://wiki.idempiere.org/en/Manufacturing#Bill_of_Materials)
is a recipe: one parent product, N child products, each with a quantity. The magic
is that each child can itself be a BOM — a building contains floors, a floor
contains rooms, a room contains furniture, recursively. Each level is atomic
and self-contained:

```
Level       M_Product_Category    Examples
─────       ──────────────────    ────────
Building    RE (Residential)      SH, DX, DM, FK
  Floor     GF, L1, RF            ground, first, roof
    Room    LIVING, KITCHEN        swap pool — replace one LIVING layout for another
      Leaf  IFC_WALL, IFC_DOOR     element classification (→ component library geometry)
```

The category at each level defines the **swap pool** — you can replace one
LIVING room layout with another LIVING layout, but you can't swap a LIVING
room for a KITCHEN. This is iDempiere's Configure-to-Order constraint
applied to spatial BOMs.

**AD_Org — Discipline (WHO is responsible).** In iDempiere, `AD_Org` partitions
data by business unit. Here, engineering disciplines partition the BOM validation
space. Each discipline is an organisational concern with its own rules, its own
AD_Val_Rule set, its own validation pass — but sharing the same product catalog.

```
AD_Org (discipline)  → ARC, STR, FP, ELEC, ACMV, SP, CW, LPG, REB, MEP, ROAD, GEO, RAIL, LAND, SIGN
```

**The distinction matters.** Disciplines cut ACROSS the category tree — they
don't appear AS levels within it. Three products in the same room (same
category path) can have three different AD_Orgs:

```
Category cascade:     RE → GF → LIVING → SOFA_001       ← AD_Org = ARC
Category cascade:     RE → GF → LIVING → SPRINKLER_001  ← AD_Org = FP
Category cascade:     RE → GF → LIVING → LIGHT_001      ← AD_Org = ELEC
```

M_Product_Category answers "what kind of thing?" AD_Org answers "who installs it?"

### The Category Cascade — One Pattern, Three Domains

The cascade is universal. The same parent→child→grandchild pattern governs
residential, infrastructure, and commercial buildings. Only the category
names change:

**Residential:**
```
RE (Residential)
  └─ M_Product: SH (IsBOM=Y)              ← the building IS a product
       └─ GF (Ground Floor, IsBOM=Y)       ← floor is a product
            └─ LIVING (Room, IsBOM=Y)       ← room is a product
                 ├─ SOFA_001 (IsBOM=N)      ← leaf — geometry from component library
                 └─ TABLE_001 (IsBOM=N)     ← leaf — geometry from component library
```

**Infrastructure:**
```
IN (Infrastructure)
  └─ M_Product: BR (Bridge, IsBOM=Y)
       └─ SUP (Support, IsBOM=Y)           ← segment is a product
            └─ PILE_001 (IsBOM=N)           ← leaf — geometry from component library
```

**Commercial:**
```
CO (Commercial)
  └─ M_Product: WA (Warehouse A, IsBOM=Y)
       └─ L1 (Level 1, IsBOM=Y)            ← floor is a product
            └─ OFFICE (Space, IsBOM=Y)      ← space type — same swap-pool logic
                 └─ DOOR_001 (IsBOM=N)      ← leaf — geometry from component library
```

### Category Population — Current State

| M_Product_Category | Buildings | Sub-Categories |
|-------------------|-----------|----------------|
| **RE** (Residential) | SH, DM, DX, FK, AC | LIVING, KITCHEN, BEDROOM, BATHROOM, DINING, MASTER, CORRIDOR, OFFICE + floor-level (GF, RF, L1, L2) |
| **RE** (Residential, floor-only) | BA, BH, BS, CA, CE, CH, CL, CP, CS, ES, GH, HI, JE, JS, MO, NI, RA, RM, RS, SC, WB, WI | Floor-level categories only (GF, L1, L2, ROOF, FDN, MISC) — no room categories yet |
| **IN** (Infrastructure) | BR, RD, RL | SUP, DCK, ABT, TRK, ROAD, RAIL, GEO — segment types |
| **CO** (Commercial) | WA, WL, WT, TE | LOBBY, OFFICE, PLANT_ROOM, LOADING + floor-level (L1–L5) + discipline-driven (ARC, STR, FP, ACMV, ELEC, CW, SP, LPG) |
| **IP** (Industrial Plant) | IP | PROCESS, UTILITY, CONTROL, MISC + floor-level |

**Gaps:** 22 residential buildings have only floor-level categories (GF, L1, L2)
but no room-level categories (LIVING, KITCHEN, BEDROOM). These buildings were
extracted before room-level classification was implemented. Re-extraction with
the current pipeline would populate room categories automatically.

**Vertical BOM levels:** All buildings currently use `bom_level=SET`. The full
hierarchy (BUILDING → FLOOR → ROOM → SET → ITEM) is specified in BBC.md §1
but not yet populated — the BOM tree expresses hierarchy through parent-child
M_BOM_Line relationships, not through bom_level values.

---

## The Insight

In 2006, we built ADempiere's manufacturing BOM module. In 2010, we rebuilt it
for iDempiere. After two decades of watching M_Product, M_BOM, and C_Order
handle everything from circuit boards to patio furniture sets, the realisation
was unavoidable:

**A building is just a very large manufactured product with spatial coordinates.**

Every wall panel is an M_Product. Every floor plan is an M_BOM (assembly recipe).
Every construction project is a C_Order. The only thing manufacturing MRP lacks
is the *where* — the (x, y, z) coordinates that turn a flat bill of materials
into a three-dimensional building.

Manufacturing solved procurement, scheduling, cost control, and quality decades
ago. Construction never had a Bill of Materials. This project provides one.
The rest follows from that foundation.

---

## The Pattern: A Product IS a BOM

In iDempiere/Libero Manufacturing, there is one universal entity: **M_Product**.

A Patio Furniture Set is an M_Product. Set `IsBOM=Y` and it has children:
4 chairs, 1 table, 1 optional shade — each an M_Product in its own right.
The chairs are leaf products (`IsBOM=N`): they have no children, they reference
inventory directly. The set is a BOM product: it has a recipe (M_BOM) with
lines (M_BOM_Line) that point to its children.

**This is the entire model.** Products all the way down. BOMs are not a
separate entity — they are a property of a product. A building is a product.
A floor is a product. A room is a product. A door is a product.

```
BUILDING (M_Product, IsBOM=Y, M_Product_Category=RE)
  └─ FLOOR (M_Product, IsBOM=Y, M_Product_Category=GF)
       └─ ROOM (M_Product, IsBOM=Y, M_Product_Category=LIVING)
            ├─ WALL_PANEL (M_Product, IsBOM=N → geometry from component library)
            ├─ DOOR (M_Product, IsBOM=N → geometry from component library)
            └─ FURNITURE_SET (M_Product, IsBOM=Y, M_Product_Category=FR)
                 ├─ TABLE (M_Product, IsBOM=N → leaf)
                 └─ CHAIR × 4 (M_Product, IsBOM=N → leaf)
```

When the compiler encounters a BOM product, it **explodes** (recurses into
children). When it encounters a leaf product, it **resolves** (looks up geometry
in the component library). This is the same operation an ERP system performs
when it explodes a manufacturing BOM into work orders.

---

## The Order — Configure-to-Order

In iDempiere, there is **one C_DocType per document purpose**: SOO (Sales Order),
POO (Purchase Order), MOP (Manufacturing Order). Product classification lives
on M_Product → M_Product_Category — never on the document type.

**This project has exactly one document purpose: "Construction Order."** That is
the C_DocType. Classification lives where it belongs — on the product's
M_Product_Category, not on the order.

The BOM cascade gives you the FULL product tree (SH → floors → rooms → furniture →
thousands of leaves). But the C_Order does NOT repeat that tree. This is
iDempiere's Configure-to-Order pattern: the order carries only the EXCEPTIONS.

```
M_Product SH (BOM template):     1099 elements (full cascade)
C_Order "Build me SH":           0 lines (no exceptions — use template as-is)
C_Order "SH but no sofa":        1 line  (qty=0 at locator_ref RE.GF.LI.SOFA_001)
C_Order "SH Solar Premium":      6 lines (inherits from SH Solar, adds overrides)
C_Order "200 houses, 6 variants": 200 × ~3 lines = 600 lines (not 200 × 1099)
```

The BOM template is the PRODUCT (M_Product + M_BOM + M_BOM_Line cascade).
The Order is just the delta — Remove (qty=0), Compress (reference class × N),
Replace (swap product at locator_ref), Add (new line). Inheritance chains
(Ref_Order_ID) let you stack deltas: SH_BASE → SH_SOLAR → SH_SOLAR_PREMIUM.

The category at each level constrains what the thin order can override.
You can Replace a LIVING room layout with another LIVING layout, but you
can't swap it for a KITCHEN. M_Product_Category is the swap-pool guard.

---

## The Entity-Relationship Model

Every table in this project maps to a proven iDempiere entity. No invented
abstractions. No BIM-specific data models.

### The ERD

```
M_Product_Category (RE, IN, CO)          ← WHAT kind of thing (cascade level)
  └─ M_Product (SH, DX, BR)             ← the thing itself (IsBOM=Y or N)
       ├─ M_BOM + M_BOM_Line            ← children (cascade down, dx/dy/dz tack offsets)
       ├─ M_AttributeSet                ← WHICH attributes vary (BIM_Pipe, BIM_Component)
       │    └─ M_AttributeSetInstance   ← per-instance values (length=3200mm)
       └─ AD_Org                        ← WHO installs it (ARC, FP, ELEC) — tag, not level

C_DocType ("Construction Order")         ← ONE document type, always
  └─ C_Order ("Build me SH")            ← references M_Product
       ├─ C_OrderLine                   ← exceptions only (thin order)
       │    ├─ locator_ref              ← WHERE in the tree
       │    └─ M_Product_Category       ← swap pool constraint
       ├─ Ref_Order_ID                  ← inheritance chain
       └─ C_Campaign                    ← design theme (orthogonal)

AD_Val_Rule                              ← jurisdiction rules (MY/UBBL, US/IBC)
AD_ChangeLog                             ← full provenance (undo/redo stack)
```

### The Mapping: iDempiere → BIM

| iDempiere Manufacturing | BIM Compiler | What It Does |
|------------------------|-------------|-------------|
| **M_Product** | Building element or assembly | The universal entity. Everything is a product |
| **M_Product_Category** | RE, IN, CO, GF, LIVING, IFC_WALL... | Classifies products at every cascade level. WHAT kind of thing |
| **AD_Org** | ARC, STR, FP, ELEC, ACMV... | Discipline = organisational unit. WHO is responsible |
| **M_BOM + M_BOM_Line** | Assembly recipe + children | BOM explosion. Each line has dx/dy/dz tack offset |
| **C_Order** | Construction project | One order = one building. Carries exceptions only |
| **C_OrderLine** | Exception line | Deviation from BOM template (swap, remove, compress, add) |
| **C_DocType** | "Construction Order" | ONE document type. Classification lives on M_Product_Category |
| **DocAction lifecycle** | DR → IP → CO → AP | Draft → In Progress → Complete → Approved |
| **AD_Val_Rule** | Validation by jurisdiction | Same rule engine, construction codes instead of tax codes |
| **C_Campaign** | Design theme | Bali, Scandinavian, Industrial — marketing drives variant |
| **AD_PrintFormat** | Output selection | Which elements to render, which to hide |
| **M_AttributeSet** | Instance variation | Per-element customization (pipe length, colour, finish) |
| **C_Project** | Site development | 200 houses under one project. Groups C_Orders |

**The extension:** Manufacturing MRP has product + quantity + sequence.
We add three columns to M_BOM_Line: `dx`, `dy`, `dz` — parent-relative
tack offsets in millimetres. That is the entire difference between a flat
BOM and a spatial BOM. Three integers turn procurement into construction.

### Orthogonal Dimensions

Seven dimensions cut ACROSS the product category cascade. None of them
appear AS levels within the tree:

| Dimension | iDempiere Entity | What It Controls | Orthogonal To |
|-----------|-----------------|------------------|---------------|
| **Classification** | M_Product_Category | What kind of thing (swap pool) | — (this IS the cascade) |
| **Discipline** | AD_Org | Who installs it | Category |
| **Design theme** | C_Campaign | Bali, Scandinavian, Industrial | Category, Discipline |
| **Jurisdiction** | AD_Val_Rule | MY/UBBL, US/IBC rules | Category, Discipline, Theme |
| **Costing** | M_PriceList | Unit cost by region/contract | All above |
| **Instance variation** | M_AttributeSetInstance | Pipe length, colour, finish | All above |
| **Site grouping** | C_Project | 200 houses under one project | Everything |

### M_AttributeSet — Why Product Count Stays Small

Without AttributeSets, TE (Terminal) with 48,428 elements would need 48,428
separate M_Products. That's wrong. In iDempiere, a shirt comes in S/M/L/XL —
that's ONE M_Product with an M_AttributeSet (size) and 4 M_AttributeSetInstances.
Not 4 products.

Same pattern for construction. An FP (Fire Protection) route has:
- START (pipe segment)
- MID (pipe segment — different length)
- JOINT (elbow, tee, reducer — fixed geometry)
- DEVICE (sprinkler head, valve — fixed geometry)
- END (cap, terminal)

These are ~5 abstract M_Products, not thousands. The VARIABLE part (pipe length)
lives on M_AttributeSetInstance. The FIXED part (elbow geometry) has no instance
attributes — it's the same product everywhere.

```
M_Product: PIPE_CW_50MM (IsBOM=N, M_AttributeSet = BIM_Pipe)
  └─ Instance 1: {length_mm: 3200}    ← segment in corridor
  └─ Instance 2: {length_mm: 4800}    ← segment in main run
  └─ Instance 3: {length_mm: 1200}    ← branch to sprinkler

M_Product: ELBOW_90_50MM (IsBOM=N, M_AttributeSet = BIM_Component)
  └─ No instances — fixed geometry, same everywhere

M_Product: SPRINKLER_UPRIGHT_K80 (IsBOM=N, M_AttributeSet = BIM_Component)
  └─ No instances — placement varies, product doesn't
```

The ROUTE verb assembles these into a BOM tree with per-segment instance
attributes. TE's 9,345 FP/CW/SP/LPG pipe elements → ~20 abstract products
× many instances. Without this, the product table explodes.

---

## The Application Dictionary Heritage

Compiere introduced the Application Dictionary (AD) in 2000 — metadata that
defines the system itself. ADempiere inherited it. iDempiere perfected it.
This project leans on it heavily. If you've administered an iDempiere instance,
every pattern below will feel familiar.

*iDempiere references: [wiki.idempiere.org](https://wiki.idempiere.org) ·
[Application Dictionary](https://wiki.idempiere.org/en/Application_Dictionary) ·
[Manufacturing](https://wiki.idempiere.org/en/Manufacturing) ·
[Validation Rules](https://wiki.idempiere.org/en/Validation_Rules) ·
[DocAction](https://wiki.idempiere.org/en/Document_Process)*

**AD_Val_Rule — Validation rules as data, not code.**
In iDempiere, `AD_Val_Rule` restricts field values (e.g., "only active Business
Partners"). Here, the same table enforces building codes: sprinkler spacing
>= 3000mm, emergency light within 6m of exit, fire door on every corridor.
Jurisdiction-scoped — MY/UBBL rules fire for Malaysian buildings, US/IBC for
American ones. Exactly like tax rules scoped by `C_Country`.
→ [DocValidate.md](DocValidate.md) · [DocAction_SRS.md §5](DocAction_SRS.md)

**Column Callout — Reactive field logic.**
In iDempiere, a Callout fires when a user changes a field value (e.g., selecting
a Business Partner auto-fills the address). Here, `DiffVerb + Callout` means:
drag a wall in the viewport → cascading consequences fire (room AABB recalculates,
furniture re-validates, MEP re-routes). Same pattern, spatial domain.
→ [ProjectOrderBlueprint.md §9](ProjectOrderBlueprint.md)

**ModelValidator — Event-driven hooks.**
iDempiere's `ModelValidator` fires before/after save, before/after delete. Our
`processIt()` orchestration follows the identical lifecycle: `prepareIt()` →
`completeIt()` → `approveIt()`. Each discipline routes through DocEvent — the
validation engine discovers applicable rules and fires them. No hardcoded logic.
→ [DocAction_SRS.md §1](DocAction_SRS.md)

**C_Project — Multi-order grouping.**
Any ERP person recognises `C_Project` instantly: project accounting, milestone
tracking, cross-order budgets. Here, a housing development IS a C_Project.
200 houses = 200 C_Orders under one C_Project. Site layout = C_ProjectLine
per plot. The same entity that manages a manufacturing program manages a
construction site.
→ [ProjectOrderBlueprint.md §2](ProjectOrderBlueprint.md)

**AD_Org — Discipline as organisational unit.**
In iDempiere, `AD_Org` partitions data by business unit. Here, engineering
disciplines (ARC, STR, FP, ELEC, ACMV) partition the BOM validation space.
Each discipline is an organisational concern with its own rules, its own
AD_Val_Rule set, its own validation pass — but sharing the same product catalog.
→ [DISC_VALIDATION_DB_SRS.md](DISC_VALIDATION_DB_SRS.md)

**AD_ChangeLog — Full provenance and UNDO/REDO.**

iDempiere's `AD_ChangeLog` records every field change on every record: who
changed it, when, old value, new value, which transaction. This is Configure-to-Order's
audit trail — the record that proves a BOM recipe was built correctly.

Our `ChangelogDAO` applies the identical pattern to spatial operations. Every
PLACE, DELETE, MOVE, and RESIZE is logged with full before/after state. The
schema (`bim_changelog` table, migration V011) stores:

| Column | Content |
|--------|---------|
| building_id | Which building |
| entity_type + entity_id | What changed (M_BOM, M_BOM_Line, C_OrderLine) |
| action | SAVE / PLACE / DELETE / MOVE / RESIZE / PROMOTE / UNDO |
| field_name | Which field |
| old_value / new_value | Before and after |
| user_id | Who did it |
| timestamp | When |

This gives us a complete **UNDO/REDO stack**. Replay the log forward to
reconstruct any past state. Replay in reverse to undo. Like Wikipedia's edit
history: every BOM state that ever existed can be reconstructed from the
changelog, and every change is attributed to a user.

**Multi-user conflict detection** follows the iDempiere pattern: `AD_Session`
identifies the editing session, `user_id` identifies the author. Two users
editing the same BOM line produce two changelog entries — the system detects
the conflict at save time by comparing timestamps.

**Current status:** ChangelogDAO is fully implemented and tested (TIER1_SRS.md §3).
The `bim_changelog` table lives in the per-building output database (output.db). Wire protocol supports
`changelog` (query history) and `undoChanges` (revert N steps). Not yet
integrated into BOM databases — the audit trail currently covers design
edits in the viewport session.
→ [TIER1_SRS.md §3](TIER1_SRS.md) · [ProjectOrderBlueprint.md §10](ProjectOrderBlueprint.md)

**EntityType (D/U/A) — Dictionary vs User vs Application.**
iDempiere protects shipped dictionary records from user modification. Our
`X_M_BOM` enforces the same: Dictionary records (shipped BOM templates) are
read-only. User records (verb-created BOMs) are fully mutable. GodMode
bypass for migrations only. Three-tier protection at the ORM layer.
→ [BBC.md §1](BOMBasedCompilation.md) · [AUDIT Appendix O.7](AUDIT_S51_FOCUSED.md)

**AD_PrintFormat — Output selection.**
In iDempiere, `AD_PrintFormat` controls which columns appear on a printed
document. Here, the same concept controls which elements render in the
viewport, which disciplines show in the HTML UI, and which BOM levels
expand in the tree view. Presentation is configuration, not code.

**Configure-to-Order — Exception-based ordering.**
iDempiere's BOM Configurator lets a sales rep exclude optional components
or set quantities at order time. Our exception-based ordering (qty=0 removes
a subtree, reference class compresses N copies) is the identical pattern
applied to buildings. 200 houses, 6 lines of exceptions each.
→ [ProjectOrderBlueprint.md §1](ProjectOrderBlueprint.md)

---

## Why This Matters

**For construction:** The industry reportedly loses billions annually to the gap between
design tools (geometry) and ERP tools (data). This compiler bridges that gap
deterministically. Given a building design, it answers: what do I need to buy,
where does each piece go, and can I prove it?

**For iDempiere:** The manufacturing module, proven over two decades on discrete
products, turns out to handle the world's largest product — a building — with
minimal extension. This extends the iDempiere architecture to building scale — a validation
of the original design that its scope never anticipated.

**For the project:** Every decision traces to an iDempiere pattern. When we face
a design question, we ask: *how does iDempiere handle this for manufacturing?*
The answer is almost always directly applicable:

- **[Exception-based ordering](ProjectOrderBlueprint.md#1-exception-based-ordering-configure-to-order-for-buildings)** is iDempiere's Configure-to-Order. 200 houses, 6 lines of exceptions each — not 200 × 1099 elements. The BOM template is the product; the order is just the delta.
- **[Two kinds of rules](DocValidate.md)** work in symbiosis: *spatial rules* mined from 35 real buildings tell the compiler where things go; *regulatory rules* (UBBL, NFPA 13, IBC) tell it what the law requires. Spatial proposes, regulatory validates — the Three Concerns in action.
- **[4D scheduling](ProjectOrderBlueprint.md#51-4d-schedule-topological-sort-of-bom-tree)** is a topological sort of the BOM tree. No Primavera needed. Material logistics follow via M_InOut — iDempiere's goods receipt applied to construction deliveries.
- **[5D cost](ProjectOrderBlueprint.md#52-5d-cost-inherent-in-the-data-model)** is inherent in the data model. Every M_Product has a price. The cost of a building is `SUM(price × qty)` — a query, not a feature.
- **[Design themes](ProjectOrderBlueprint.md#1-exception-based-ordering-configure-to-order-for-buildings)** are C_Campaign — Bali, Scandinavian, Industrial. Marketing drives variant selection, orthogonal to product category and discipline.
- **[Site developments](ProjectOrderBlueprint.md#2-c_project-site-as-bom)** are C_Project. 200 houses under one project, each a C_Order on a plot. The same entity that manages a manufacturing program manages a construction site.

**The test:** 35 real buildings compiled. 48,428 elements in the largest.
6 verification gates. 19 buildings ALL GREEN. Not a prototype — a working
compiler with witness verification at every step.

---

## Reading Order

After this manifesto:

1. **[BBC.md](BOMBasedCompilation.md) §1** — the entity mapping table and technical detail
2. **[DATA_MODEL.md](DATA_MODEL.md)** — schema, 5-DB architecture
3. **[TestArchitecture.md](TestArchitecture.md)** — verification gates, tamper seal
4. **[ProjectOrderBlueprint.md](ProjectOrderBlueprint.md)** — what's next (exception ordering, inheritance, C_Project)
5. **[SourceCodeGuide.md](SourceCodeGuide.md)** — where the code is

For the full academic treatment: **[BIMERPPaper.md](BIMERPPaper.md)**
