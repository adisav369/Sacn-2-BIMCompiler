# DONE — Eliminate DocBaseType/DocSubType — Products ARE the classification + ERP.db
> Commit: 4a63579 [S70]

You are an architect/coder for bim-compiler. Study + docs + migration.

Read first:
1. docs/MANIFESTO.md — especially §Three Concerns, the Mapping table, Category Population
2. docs/DATA_MODEL.md §6 (ERP.db proposal — investigation done, execution pending)
3. docs/BOMBasedCompilation.md §1 (entity mapping)
4. docs/SystemContract.md §2 (entity registry) + §10 (gap register)
5. docs/ProjectOrderBlueprint.md §3 (abstract category tree)
6. PROGRESS.md

## THE INSIGHT

In iDempiere ERP, there is ONE C_DocType per document purpose:
- SOO = Sales Order
- POO = Purchase Order
- MOP = Manufacturing Order

Product classification lives on M_Product → M_Product_Category. You never put
"what kind of product" on the document type — that's the product's job.

**Our project has exactly one document purpose:** "Construction Order." That's
the C_DocType. There is no DocBaseType=RE or DocBaseType=IN — those are
PRODUCT CATEGORIES, not document types.

The correct model — and understanding the cascade:

### How the ERP cascade works (essential — read carefully)

In iDempiere, a Patio Furniture Set is an M_Product (IsBOM=Y). It belongs to
M_Product_Category "OUTDOOR_FURNITURE". When a customer orders it, the ERP
**explodes** the BOM: Set → Table + 4×Chair + Umbrella. Each child is also an
M_Product. The child's category tells you what KIND of thing it is (TABLE is
category FURNITURE). The parent's category tells you what POOL it belongs to
for swapping (you can swap one OUTDOOR_FURNITURE set for another, but not for
a sprinkler system).

**A building works identically.** SH (Sample House) is an M_Product (IsBOM=Y).
It belongs to M_Product_Category "RE" (Residential). When you "order" it, the
compiler explodes the BOM:

```
M_Product_Category: RE (Residential)         ← top-level category
  └─ M_Product: SH (IsBOM=Y)                ← the building IS a product
       └─ M_BOM_Line → FLOOR_GF (IsBOM=Y)   ← floor is a product
            └─ M_BOM_Line → ROOM_LI (IsBOM=Y)  ← room is a product
                 ├─ M_BOM_Line → SOFA_001 (IsBOM=N, leaf)  ← resolved from component library
                 └─ M_BOM_Line → TABLE_001 (IsBOM=N, leaf) ← resolved from component library
```

**The cascade:** Each child's M_Product_Category is the NEXT level down.
The category tells you what kind of thing it is AT THAT LEVEL:

Residential cascade:
```
Level       M_Product_Category    Examples
─────       ──────────────────    ────────
Building    RE (Residential)      SH, DX, DM, FK
  Floor     GF, L1, RF            ground, first, roof
    Room    LIVING, KITCHEN        swap pool — replace one LIVING layout for another
      Leaf  (component library)    SOFA_001, TABLE_001 — geometry from library
```

Infrastructure cascade (same pattern, different categories):
```
Level       M_Product_Category    Examples
─────       ──────────────────    ────────
Structure   IN (Infrastructure)   BR, RD, RL
  Segment   SUP, DCK, ABT         support, deck, abutment
    Element STR, GEO, CW          structural, geotechnical, cold water
      Leaf  (component library)    BEAM_001, PILE_001 — geometry from library
```

Commercial cascade:
```
Level       M_Product_Category    Examples
─────       ──────────────────    ────────
Building    CO (Commercial)       WA, WL, WT
  Floor     L1, L2, L3            floor levels
    Space   LOBBY, OFFICE, PLANT  space types — same swap-pool logic as rooms
      Leaf  (component library)    DOOR_001, DUCT_001
```

The pattern is universal: **parent category → child category → grandchild category**.
Each level's category defines its swap pool — you can swap one LIVING room layout
for another LIVING layout, but you can't swap a LIVING room for a KITCHEN.

### AD_Org is orthogonal — not a level in the tree

Disciplines (AD_Org: ARC, STR, FP, ELEC, ACMV...) are NOT categories in the
product cascade. They are a tag ON each leaf product — WHO installs it:

```
Category cascade:     RE → GF → LIVING → SOFA_001       ← AD_Org = ARC
Category cascade:     RE → GF → LIVING → SPRINKLER_001  ← AD_Org = FP
Category cascade:     RE → GF → LIVING → LIGHT_001      ← AD_Org = ELEC
```

Three products in the SAME room (same category path), three different AD_Orgs.
The discipline cuts ACROSS the tree — it doesn't appear AS a level within it.
M_Product_Category answers "what kind of thing?" AD_Org answers "who installs it?"

### The AD is already preset — extract, don't invent

Like iDempiere, the Application Dictionary data is mostly already seeded:

- `AD_Org` — 16 disciplines in ERP.db (DV013 migration)
- `M_Product_Category` — 46 rows in ERP.db (element classification)
- `ad_val_rule` — 63 validation rules (jurisdiction-scoped)
- `ad_space_type_mep_bom` — 186 rows (per-room MEP qty rules)

These are the "factory settings" — preset like iDempiere ships with its AD tables
populated. The task is to CONNECT them correctly (category cascade replaces
DocBaseType routing), not to create new data. Check what exists first, then
map how it fits the cascade model described above.

### Think like an ERP/MFG person — more orthogonal dimensions

AD_Org (discipline) is one orthogonal dimension on the product tree. But an ERP
person familiar with manufacturing BOMs knows there are several others. Think
through which of these already exist in the data model and how they relate:

- **AD_Org** (WHO installs) — discipline tag on each product. Already seeded.
- **C_Campaign** (design theme) — Bali, Scandinavian, Industrial. Drives variant
  selection: same LIVING room, different furniture set. Orthogonal to category.
- **AD_Val_Rule** (jurisdiction) — MY/UBBL, US/IBC. Same product tree, different
  validation rules fire depending on WHERE you're building. Orthogonal to both
  category and discipline.
- **M_PriceList** (costing context) — same product, different unit cost by
  region/contract. Already have `unit_cost_rm` on M_Product (5D).
- **M_AttributeSet / M_AttributeSetInstance (ASI)** — per-instance customization.
  Same product, different colour/finish/size. Partially implemented.
- **C_Project** (site grouping) — 200 houses under one project. Orthogonal to
  everything — it groups C_Orders, not products.
- **AD_PrintFormat** (output selection) — which elements render, which disciplines
  show. View configuration, not data.

All of these cut ACROSS the product category cascade — none of them appear AS
levels within it. The category cascade is purely: what kind of THING is this
product? Everything else (who installs it, what style, what jurisdiction, what
price, what customization) is an orthogonal dimension attached to the product
or the order, not to the category tree.

### M_AttributeSet — why product count stays small

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

**This is already spec'd:** See `docs/TerminalAnalysis.md` §ROUTE + ASI section.
Verify the spec is consistent with the ERD model below and flag any conflicts.

### The ERD — how it all connects

Map out the full entity-relationship model. These entities exist. The session
must verify they connect correctly and document the ERD in DATA_MODEL.md §7:

```
M_Product_Category (RE, IN, CO)          ← WHAT kind of thing (cascade level)
  └─ M_Product (SH, DX, BR)             ← the thing itself (IsBOM=Y or N)
       ├─ M_BOM + M_BOM_Line            ← children (cascade down)
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
PP_Order_Node                            ← HOW it was placed (verb audit)
CO_EmptySpaceLine                        ← WHERE it goes (spatial slot)
```

**Your job in this session:**
1. Verify each entity exists in the database and Java code
2. Verify the connections match (FK relationships, column names)
3. Check TerminalAnalysis.md ROUTE + ASI spec — is it consistent with this ERD?
4. Check that NO orthogonal dimension is incorrectly embedded in M_Product_Category
5. Document the full ERD in DATA_MODEL.md §7 so future sessions have the
   ERP mental model written down, not just in someone's head

**The key insight:** You never need DocBaseType=RE because the BUILDING PRODUCT
already has M_Product_Category=RE. You never need DocSubType=SH because the
BUILDING PRODUCT already IS "SH". The product tree carries its own classification
at every level. The C_Order just says "build me product SH" — one C_DocType
("Construction Order"), one DocAction lifecycle (DR→IP→CO→AP).

```
M_Product_Category: RE (Residential)
  └─ M_Product: SH (Sample House)      ← IsBOM=Y, children via M_BOM_Line
  └─ M_Product: DX (Duplex)            ← IsBOM=Y, children via M_BOM_Line
  └─ M_Product: DM (Demo House)        ← IsBOM=Y, children via M_BOM_Line

M_Product_Category: IN (Infrastructure)
  └─ M_Product: BR (Bridge)            ← IsBOM=Y
  └─ M_Product: RD (Road)              ← IsBOM=Y

M_Product_Category: CO (Commercial)
  └─ M_Product: WA (Warehouse A)       ← IsBOM=Y

C_DocType: "Construction Order" (ONE — always)
C_Order: "Build me SH" → references M_Product SH → BOM explosion walks the tree
```

### The Order is thin — Configure-to-Order

The BOM cascade gives you the FULL product tree (SH → floors → rooms → furniture →
thousands of leaves). But the C_Order does NOT repeat that tree. This is iDempiere's
Configure-to-Order pattern: the Order carries only the EXCEPTIONS.

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

This is why the product cascade matters: the category at each level defines the
SWAP POOL for exceptions. You can Replace a LIVING room layout with another
LIVING layout, but you can't swap it for a KITCHEN. The category constrains
what the thin order is allowed to override.

DocBaseType and DocSubType are redundant artifacts from before M_Product_Category
was mature. The category cascade already carries the full classification at every
level. When the compiler needs to know "is this residential?" it checks the
product's category, not the document type.

## THE CURRENT PROBLEM

The codebase still routes through DocBaseType/DocSubType:
- `C_DocType` has `DocBaseType=RE, DocSubType=SH` (per building)
- `m_bom` has `doc_base_type=RE, doc_sub_type=SH`
- `BomDropper.findBuildingBom()` does `WHERE doc_base_type = ? AND doc_sub_type = ?`
- `BuildingRegistry` joins on `doc_base_type = DocBaseType`
- MANIFESTO says "DocBaseType=RE" as if it means something

But `doc_base_type + doc_sub_type` on m_bom is IDENTICAL to
`m_product_category_id + bom_id` — it's the same lookup with different column names.
The three-key match (AABB + DocBaseType + DocSubType) should be
(AABB + M_Product_Category + M_Product).

## TASK 1: Analyse — read all docs, map the current model vs correct model

Read each doc below. For each, note every statement that conflicts with the
ERD model above. Don't fix anything yet — just build the evidence.

```bash
# Where does Java still route on DocBaseType/DocSubType? These are the files to fix later.
grep -rn "DocBaseType\|doc_base_type\|docBaseType" --include="*.java" DAGCompiler/ BIM_COBOL/ ORMSandbox/ BIMBackOffice/
grep -rn "DocSubType\|doc_sub_type\|docSubType" --include="*.java" DAGCompiler/ BIM_COBOL/ ORMSandbox/ BIMBackOffice/

# Current M_Product_Category hierarchy — what exists, what's missing?
sqlite3 library/ERP.db "SELECT * FROM M_Product_Category;"

# How m_bom currently stores classification (redundant doc_base_type vs m_product_category_id)
sqlite3 library/SH_BOM.db "SELECT bom_id, doc_base_type, doc_sub_type, m_product_category_id FROM m_bom;"
```

**Do NOT loop across all 34 BOM databases checking C_DocType.** There is ONE
C_DocType = "Construction Order". DocBaseType/DocSubType on C_DocType are the
problem — they put product classification on the wrong table. The grep of Java
code is what matters: which files still route on these columns?

**Docs to check (for each, answer: does it conflict with the ERD?):**

| Doc | What to check |
|-----|---------------|
| `MANIFESTO.md` | DocBaseType refs, discipline-in-category, C_DocType role |
| `BOMBasedCompilation.md` | Entity mapping table, BOM Drop flow, product hierarchy |
| `SystemContract.md` | Entity registry §2, three-concern matrix §4 |
| `DATA_MODEL.md` | DB architecture, table assignments |
| `ProjectOrderBlueprint.md` | §3 category tree, §14 session plan |
| `TerminalAnalysis.md` | ROUTE + ASI spec, product count, BOM tree model |
| `DISC_VALIDATION_DB_SRS.md` | AD_Org vs M_Product_Category roles |
| `DocAction_SRS.md` | DocType lifecycle |
| `SourceCodeGuide.md` | Code entry points |

## TASK 2: Write the corrected MANIFESTO

Based on Task 1 findings, rewrite MANIFESTO.md to embody the correct ERD:

1. **No DocBaseType/DocSubType language anywhere.** Classification = M_Product_Category.
2. **C_DocType = ONE "Construction Order"** — not "building type classification."
3. **Category cascade clearly shown** — RE→floor→room→leaf, IN→segment→element→leaf.
4. **AD_Org orthogonal** — discipline is a tag on products, not a cascade level.
5. **M_AttributeSet explained** — why ~20 abstract products + ASI handles 48K elements.
6. **Configure-to-Order** — thin order with exceptions, not full BOM repetition.
7. **Full ERD** — all entities, all orthogonal dimensions, all connections.

The MANIFESTO is the single source of truth for the ERP mental model.
All other docs and code will be corrected to match it in FOLLOW-UP sessions —
this session produces the corrected MANIFESTO only.

**STOP after Task 2.** Do NOT fix other docs or code yet. The watchdog will
audit the corrected MANIFESTO, and a follow-up prompt will propagate fixes
to the remaining docs and code specs.

## TASK 3: Findings summary for follow-up

Write a concise summary to DATA_MODEL.md §7 listing:
- Every doc that needs correction (from Task 1 analysis)
- Every Java file that routes on DocBaseType (grep results)
- M_Product_Category hierarchy status (what exists, what's missing)
- ERP.db rename touchpoint count

This becomes the work list for the next prompt.

## Constraints

- Append-only migrations
- Do NOT break BomDropper's BOM selection in this session. If the Java change
  (doc_base_type → m_product_category_id) is too large, do MANIFESTO + docs +
  category hierarchy first, and document the Java migration plan for a follow-up.
- Gate: `mvn compile -q` must pass. Rosetta Stones must stay GREEN.
- Pre-flight: `// Implementing DATA_MODEL.md §7 — DocBaseType → M_Product_Category alignment`

## When Done

Prepend `# DONE` + commit hash to this file's first line before committing.
Commit with `[S70] DocBaseType → M_Product_Category: data model study + MANIFESTO fix + category hierarchy`.
Deploy docs: `/home/red1/bim-compiler/.venv/bin/mkdocs gh-deploy`

## WATCHDOG REVIEWED
**S69 Watchdog** — 2026-03-25

Deliverables verified against commit 4a63579:
1. **MANIFESTO.md:** Zero DocBaseType/DocSubType refs (grep confirms 0 hits). Category cascade shown correctly (RE→floor→room→leaf). AD_Org orthogonal. ERD with 7 dimensions. ASI explained. Configure-to-Order thin order. — PASS
2. **DATA_MODEL.md §7:** Full findings — 6 docs needing correction, 19 Java source files + 12 test files routing on DocBaseType, M_Product_Category gap analysis (missing RE/IN/CO/IP top-level), ERP.db 40-60 touchpoints, 6-session migration plan. — PASS
3. **Compile:** `mvn compile -q` clean — PASS
4. **No code changes:** Docs only, as instructed — PASS

Note: Session did not mark prompt as DONE or deploy docs site. Watchdog handled both.
