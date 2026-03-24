# The ERP World View — Why Construction Is Manufacturing

> **Read this first.** Before any spec, any code, any schema. This is the lens
> through which every design decision in this project makes sense.

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
Everything else follows.

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
BUILDING (M_Product, IsBOM=Y)
  └─ FLOOR (M_Product, IsBOM=Y)
       └─ ROOM (M_Product, IsBOM=Y)
            ├─ WALL_PANEL (M_Product, IsBOM=N → geometry from component library)
            ├─ DOOR (M_Product, IsBOM=N → geometry from component library)
            └─ FURNITURE_SET (M_Product, IsBOM=Y)
                 ├─ TABLE (M_Product, IsBOM=N → leaf)
                 └─ CHAIR × 4 (M_Product, IsBOM=N → leaf)
```

When the compiler encounters a BOM product, it **explodes** (recurses into
children). When it encounters a leaf product, it **resolves** (looks up geometry
in the component library). This is the same operation an ERP system performs
when it explodes a manufacturing BOM into work orders.

---

## The Mapping: iDempiere → BIM

Every table in this project maps to a proven iDempiere entity. No invented
abstractions. No BIM-specific data models. If iDempiere already solved the
problem, we use iDempiere's solution.

| iDempiere Manufacturing | BIM Compiler | What It Does |
|------------------------|-------------|-------------|
| **M_Product** | Building element or assembly | The universal entity. Everything is a product |
| **M_Product_Category** | ARC, STR, FP, MEP, ELEC... | Classifies products. Same category = same swap pool |
| **M_BOM + M_BOM_Line** | Assembly recipe + children | BOM explosion. Each line has dx/dy/dz tack offset |
| **C_Order** | Construction project | One order = one building. Carries design state |
| **C_OrderLine** | Element instance | WHAT to build. References M_Product |
| **C_DocType** | Building type classification | Routes compilation. Metadata, not logic |
| **PP_Order_Node** | Verb execution record | HOW it was placed. Full audit trail |
| **CO_EmptySpace** | Room/floor slot | WHERE things go. Warehouse slot analogy |
| **DocAction lifecycle** | DR → IP → CO → AP | Draft → In Progress → Complete → Approved |
| **AD_Val_Rule** | Validation by jurisdiction | Same rule engine, construction codes instead of tax codes |
| **C_Campaign** | Design theme | Bali, Scandinavian, Industrial — marketing drives variant |
| **AD_PrintFormat** | Output selection | Which elements to render, which to hide |

**The extension:** Manufacturing MRP has product + quantity + sequence.
We add three columns to M_BOM_Line: `dx`, `dy`, `dz` — parent-relative
tack offsets in millimetres. That is the entire difference between a flat
BOM and a spatial BOM. Three integers turn procurement into construction.

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

**AD_ChangeLog — Full provenance.**
iDempiere logs every field change: who, when, old value, new value. Our
`ChangelogDAO` does the same for every PLACE/DELETE/MOVE/RESIZE. Undo via
replay. Wikipedia edit history for BOMs. Auditors love this.
→ [ProjectOrderBlueprint.md §10](ProjectOrderBlueprint.md)

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

## The Three Concerns

iDempiere separates documents into header (C_Order) and lines (C_OrderLine),
with production detail in PP_Order. We inherit the same separation:

| Concern | iDempiere | BIM Compiler | Table |
|---------|-----------|-------------|-------|
| **WHAT** to build | C_OrderLine | Which products, how many | c_orderline |
| **HOW** to place | PP_Order_Node | Which verb, what parameters | pp_order_node |
| **WHERE** it goes | M_Locator / Warehouse | Room slot, tack offset | co_empty_space_line |

These three concerns are **never merged**. A change to WHAT (swap a product)
does not require changes to HOW (verb stays the same) or WHERE (slot stays
the same). This is the architectural invariant that makes exception-based
ordering possible: override one concern, inherit the others.

---

## Why This Matters

**For construction:** The industry loses billions annually to the gap between
design tools (geometry) and ERP tools (data). This compiler bridges that gap
deterministically. Given a building design, it answers: what do I need to buy,
where does each piece go, and can I prove it?

**For iDempiere:** The manufacturing module, proven over two decades on discrete
products, turns out to handle the world's largest product — a building — with
minimal extension. This validates the iDempiere architecture at a scale its
creators never anticipated.

**For the project:** Every decision traces to an iDempiere pattern. When we face
a design question, we ask: *how does iDempiere handle this for manufacturing?*
The answer is almost always directly applicable. Exception-based ordering is
Configure-to-Order. Design themes are C_Campaign. Jurisdiction rules are
AD_Val_Rule. Site developments are C_Project. The ERP vocabulary is the
project's vocabulary.

**The test:** 35 real buildings compiled. 48,428 elements in the largest.
6 verification gates. 19 buildings ALL GREEN. Not a prototype — a working
compiler with mathematical proof at every step.

---

## Reading Order

After this manifesto:

1. **[BBC.md](BOMBasedCompilation.md) §1** — the entity mapping table and technical detail
2. **[SystemContract.md](SystemContract.md)** — entity registry, three-concern matrix, gap register
3. **[DATA_MODEL.md](DATA_MODEL.md)** — schema, 4-DB architecture
4. **[TestArchitecture.md](TestArchitecture.md)** — verification gates, tamper seal
5. **[ProjectOrderBlueprint.md](ProjectOrderBlueprint.md)** — what's next (exception ordering, inheritance, C_Project)
6. **[SourceCodeGuide.md](SourceCodeGuide.md)** — where the code is

For the full academic treatment: **[BIMERPPaper.md](BIMERPPaper.md)**
