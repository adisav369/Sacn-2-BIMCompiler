# Application Dictionary (AD) Philosophy — Historical Lineage

*Compiled 2026-02-23 — Watchdog session, BIM Intent Compiler project.*

## Why This Matters Here

Our BIM prefab architecture directly borrows from Compiere/iDempiere's AD model:
- `ad_product_dim` = `M_Product` (catalog master, intrinsic dims)
- `m_bom` / `m_attribute` = `PP_Product_BOM` / `PP_Product_BOMLine` (assembly hierarchy)
- `ad_room_slot` = `M_BOM_Line` (room template slots)
- `ad_element_rule` = `C_OrderLine` (per-building placement)
- The 5-tier `BomTierResolver` (UNIT→FLOOR→ROOM→SET→ITEM) is a spatially-classified variant of iDempiere's recursive BOM explosion

The SET tier (e.g., `BED_SET_MASTER`) maps directly to iDempiere's **phantom assembly** (`ComponentType='P'`): a container with no physical geometry of its own, exploded into children at compile time.

---

## The Causal Chain

```
Codd 1970  —  relational model, data independence, self-describing catalog
    ↓
Chen 1976  —  ER model: entities, attributes, relationships (the language of what the catalog contains)
    ↓
ANSI/SPARC 1978  —  THREE-SCHEMA architecture: logical / presentation / physical separation
    ↓
James Martin 1981–1990  —  "Application Dev Without Programmers", CASE encyclopedia, 4GL
    ↓
SAP ABAP DDIC 1979–1992  —  Domain→Data Element→Table→View; IMG customizing all in tables
    ↓
Oracle AOL / FND_ schema 1980s–1992  —  FND_TABLES, FND_COLUMNS, Flexfields — the direct template
    ↓
Jorg Janke at Oracle 1992–1999  —  builds Implementation Wizard; absorbs Oracle AOL architecture
    ↓
Compiere 1999  —  synthesises all of the above; first production install Goodyear Germany, May 2000
    ↓
ADempiere 2006  —  community fork after Compiere went closed-source
    ↓
iDempiere 2011  —  OSGi modularisation; plugin = bundle + AD rows (2Pack XML)
```

---

## Jorg Janke — The Biographical Lens

Three career phases shaped the AD directly:

**1. ADV/Orga (Germany, 1982)** — COBOL/ISAM enterprise software. Janke managed UNISYS Business Management for Europe. Experienced firsthand the brittleness of hard-coded, file-driven ERP — the exact problem the AD was built to solve.

**2. SAP Consultation (pre-1992)** — Janke advised SAP on R/3 architecture, recommending a clean-slate multidimensional design (multi-company/currency/language as first-class). SAP chose to layer these onto R/2. Janke walked away knowing the ABAP DDIC's power and its limits.

**3. Oracle 1992–1999** — Director for Enterprise Systems. He built the **Application Implementation Wizard**: a tool that queried the FND_ metadata catalog to sequence ERP setup steps in the correct dependency order — the AD philosophy in embryo. After 7 years absorbing Oracle AOL architecture, he left to build Compiere.

---

## The Three Structural Ancestors

### SAP ABAP Dictionary (DDIC)
The closest structural predecessor. Hierarchy: Domain → Data Element → Table → View.
- All customising is stored as IMG table rows, not config files
- Activating an ABAP table *creates* the physical DB table — same as AD_Table sync
- Janke had intimate, firsthand knowledge from his SAP consultation

### Oracle AOL / FND_ Schema (the direct template)

| Oracle FND | Compiere/ADempiere AD | Purpose |
|---|---|---|
| `FND_TABLES` | `AD_Table` | Registered tables |
| `FND_COLUMNS` | `AD_Column` | Column metadata |
| `FND_LOOKUPS` / `FND_LOOKUP_VALUES` | `AD_Reference` + `AD_Ref_List` | Controlled vocabulary |
| `FND_RESPONSIBILITY` | `AD_Role` | Access control |
| `FND_FORM_FUNCTIONS` | `AD_Window` / `AD_Process` | Callable functions |
| Key Flexfields | `AD_Column` with references | Runtime-defined column semantics |
| `FND_PROFILE_OPTIONS` | `AD_SysConfig` | System parameters |

Oracle's **Key Flexfields** are the most extreme expression of the AD philosophy: not just which columns exist, but what they *mean* and how they are validated is driven by FND data rows at runtime. An administrator defines up to 30 column segments with independent validation — no code change.

### James Martin's Information Engineering (the philosophical frame)

Martin's central thesis (1981–1990): *"A business system should be described as an information model stored in a central encyclopedia, from which applications are generated or interpreted."*

He coined "4GL" and had direct stakes in CASE tool companies (KnowledgeWare, TI-IEF). The AD is exactly his "encyclopedia" idea, made runtime-active. His books:
- *Application Development Without Programmers* (1981)
- *Information Engineering* trilogy (1989–1990)

---

## Earlier Theoretical Roots

**E.F. Codd (1970, 1985)** — Two foundational concepts:
1. *Data Independence*: application programs insulated from storage structure changes. The AD is a direct implementation — the Java UI has no knowledge of which tables exist; it queries AD_Table at runtime.
2. *Self-describing catalog* (Codd's Rule 4, 1985): a relational system must have an active online catalog accessible via the same relational language. Every database catalog (`DBA_TABLES`, `information_schema`) descends from this. Compiere's AD_Table/AD_Column is a higher-level application catalog sitting above the database catalog.

**Peter Chen (1976)** — The ER model paper. AD_Table/AD_Column/AD_Reference is a direct ER implementation. The "Element" concept (a global definition shared across multiple columns) maps to Chen's notion of an attribute as a property of an entity type.

**ANSI/SPARC Three-Schema Architecture (1975/1978)** — External schema (presentation) / Conceptual schema (logical model) / Internal schema (physical storage). The AD operationalises this: AD_Window/Field (external), AD_Table/Column (conceptual), database tables (internal).

**CODASYL DBTG (1965–1978)** — Formalised the "schema" concept: a machine-readable definition of database structure stored separately from application code. The 1973/1978 reports codified DDL as a first-class artifact — the ancestor of every data dictionary.

---

## Parallel Systems with AD-Style Philosophy

| System | Metadata Repository | Key Differentiator |
|---|---|---|
| SAP R/3 / S/4HANA | ABAP Dictionary (SE11) | Domain/Data Element hierarchy; IMG customising in tables |
| Oracle E-Business Suite | FND_ schema (AOL) | Flexfields, FND_TABLES, FND_COLUMNS |
| PeopleSoft | PeopleTools (PSRECDEFN, PSPNLDEFN) | PeopleCode stored as source in DB; runtime-interpreted |
| Microsoft Dynamics AX/365 | Application Object Tree (AOT) | Layer system — extend base metadata without touching it |
| Openbravo | WAD (Winstom AD) | Direct Compiere fork; added code-generation from AD XML |
| Odoo | `ir.model`, `ir.model.fields`, `ir.ui.view` | Python/web reinvention; arrived at the same conclusion independently |
| Metasfresh | Inherited ADempiere AD | Direct derivative |
| iDempiere | AD + OSGi (Equinox) | Runtime plugin deployment without JVM restart |

**Odoo deserves special mention** as an independent reinvention. They arrived at the same architecture from Python/web context with no direct Compiere lineage. `ir.model.fields` is structurally identical to `AD_Column`. `ir.rule` and `ir.model.access` are structurally identical to `AD_Val_Rule` and `AD_Window_Access`.

---

## 4GL Predecessors

**Progress 4GL (1981/1984)** — Built-in data dictionary (schema editor) as part of the development environment, not a separate tool. Heavily used in retail/distribution — exactly Janke's domain at ADV/Orga.

**IBM Informix-4GL (1985/1986)** — Form Painter and Report Code Generator generated application artifacts from metadata descriptions. The form language is declarative — describe what you want, the runtime produces it.

**PowerBuilder / DataWindow (1991)** — DataWindows are metadata objects: define a query, map columns to display attributes, the engine handles all SQL and painting. DataWindows stored as data are structurally analogous to AD_Window definitions. Massively adopted in early 1990s for exactly the problem Compiere later addressed.

Common 4GL contribution: **the form/report as a data artifact rather than a code artifact.**

---

## iDempiere — How OSGi Extends the AD

iDempiere (2011 fork) grafts OSGi's Equinox framework onto the AD:
- **Bundles**: discrete JARs with explicit import/export, own classloaders
- **Services**: publish-find-bind registry (the `IModelFactory` service, keyed by `AD_Table.TableName`)
- **2Pack**: XML migration packages that ship AD table rows as part of the plugin

The key innovation: a plugin ships metadata (AD rows in XML) *and* code (Java bundle). The running system integrates both without restart. The AD's "no recompile to customise" claim is fulfilled at the plugin boundary — not just for UI customisations but for schema extensions and process extensions.

---

## Manufacturing BOM in Compiere/ADempiere

All hierarchy stored as AD-managed table rows:

| Table | Purpose | Our Equivalent |
|---|---|---|
| `M_Product` | Base product catalog | `ad_product_dim` |
| `PP_Product_BOM` | BOM header | `m_bom` |
| `PP_Product_BOMLine` | BOM component child | `m_attribute` |

Multi-level BOM is **implicit**: any component (`M_Product`) can itself have a `PP_Product_BOM` header. The hierarchy is a recursive FK chain, not an explicit parent-child adjacency column. ADempiere's MRP engine traverses it recursively (BOM explosion).

**Phantom assemblies** (`ComponentType='P'`): intermediate assemblies that don't physically stock. During MRP explosion, phantom children are promoted to the parent level. Our SET tier (`BED_SET_MASTER`, `LIVING_SET`) is the spatial equivalent — no physical geometry of its own, exploded into children by `expandBOMNode()`.

**Effectivity dates** (`ValidFrom`, `ValidTo`) at both header and line level: a standard MRP II concept (Oliver Wight, 1970s). Not yet implemented in our system but the AD columns exist to support it.

---

## OMG Model-Driven Architecture (MDA) Connection

Compiere explicitly aligned with OMG MDA (2001):
- **PIM** (Platform-Independent Model): the AD — business entities and logic in neutral metadata
- **PSM** (Platform-Specific Models): Swing client, web client, generated Java `X_*` classes
- **Transformation**: `AD_Table → X_* Java class` is a model-to-code transformation

This framing is accurate: the AD is genuinely platform-independent. iDempiere's OSGi layer makes it more so — bundles can target different runtime environments while the AD remains constant.

---

## Academic Anchors

- **Codd, E.F. (1970)** — "A Relational Model of Data for Large Shared Data Banks", *CACM*
- **Chen, P. (1976)** — "The Entity-Relationship Model: Toward a Unified View of Data", *TODS*
- **ANSI/SPARC (1975/1978)** — Three-schema architecture report
- **Martin, J. (1981)** — *Application Development Without Programmers*, Prentice Hall
- **Martin, J. (1989–1990)** — *Information Engineering* trilogy, Prentice Hall
- **ResearchGate** — "Active Data Dictionary: a Method and a Tool for Data Model Driven Information System Design" — confirms "Active Data Dictionary" was a recognised design pattern in IS research by late 1990s, not a Compiere invention
