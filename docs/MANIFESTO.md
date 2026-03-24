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
