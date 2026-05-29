/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */

# ERP OOTB — iDempiere Application Dictionary in a Browser

## The Big Show

iDempiere's Application Dictionary (AD) is the most powerful metadata-driven
UI framework in open-source ERP. It defines windows, tabs, fields, menus,
validation rules, and display logic — ALL as database rows, not code. Change
a row in AD_Field → the UI changes. No recompile.

**What if the entire AD ran in a browser?**

Same menu tree. Same windows. Same tabs and fields. Same validation logic.
But: no JVM, no PostgreSQL, no OSGi, no server. SQLite WASM in the browser.
The AD metadata exported from a live iDempiere PostgreSQL instance, loaded
via sql.js, parsed by a JavaScript AD renderer.

**This has never been done.** And it's the natural culmination of Spatial ERP
OOTB — the construction POC (C_Project) is just one menu node in the full
AD tree.

---

## §0. Storage Model Decision (2026-05-29) — chosen path: (c) The Bridge

**Chosen:** 5-table runtime (`containers`, `items`, `documents`, `document_lines`,
`journal`) + `kernel_ops`. **AD is the compiler input** (→ `manifest.json`); the
runtime never touches `C_Order` etc. directly. `ad_data.js` is the **explicit swap
layer**, mapping every AD window to its 5-table slot via an `ad_table_map`
(e.g. `C_Order → {storage:'documents', docType:'PURCHASE_ORDER', fk_map:{…}}`).

**Why:** `Doc = Event` (§18.8) wants a `documents` table whose rows ARE the
event-bearing entities; the AD-faithful compiler (proven by §BOM_TEST) becomes
the front-end that feeds it. The bridge keeps the pivot auditable and reversible.

**Costs (recorded, not hidden):** storage schema rebuilt; `ad_data` CRUD rewritten
(<200 lines); the already-shipped real-table `erp.html` needs a one-time data
migration (not a UI rewrite); P0's manifest compiler **survives**, P0's wire-in is
partially redone.

**Validation GATE (discipline — do before any 5-table build):**
`scripts/test_5table_bom.js` must show the `ad_table_map` is **expressive enough**
to represent every edge §BOM_TEST classified (mapping-completeness, ~0 unmappable)
AND that the derivation hub ranking survives the table→`doc_type` collapse
(`C_Order` group still #1). Acyclicity is now a *runtime instance* invariant
(no document is its own ancestor via `source_id`), enforced by the kernel, not a
schema property. The §-log makes the call.

**Status:** P0 (AD-faithful manifest + wire-in) stays as the compiler front-end and
the interim shipped product until the bridge validation passes.

**Gate RESULT (witness: §5TBL, `scripts/test_5table_bom.js`, 2026-05-29):**
**98.4% of business edges mappable** (2,156 / 2,192); **hub `C_Order` #1, preserved.**
The residual 1.6% (36 edges) are slotting refinements for the real `ad_table_map`
(line-level junction/confirm/allocation records mis-slotted as `items` — e.g.
`M_MatchInv`, `M_MatchPO`, `M_*LineMA`, `C_LandedCost*` are all `document_lines`),
**not** expressiveness gaps. The gate **derived the minimal runtime schema**:

| Slot | + columns derived by the gate |
|------|-------------------------------|
| `containers` | `parent_id` (Site→Building→Floor) |
| `items` | **`parent_id`** — master data is recursive (Product→Category, Product→Price/Substitute) |
| `documents` | `source_id` (derivation lineage), `container_id`, `doc_type` |
| `document_lines` | `document_id`, **`source_line_id`** — lineage recurses to the line (InvoiceLine→OrderLine) |
| `journal` | Batch→Journal→Line; **entries only** (`Fact_Acct`, `GL_Journal*`). `GL_Category`/`GL_Budget` are accounting MASTER → `items` |
| + `kernel_ops` | the log |

Plus two rules the gate forced: **`AD_*` / `_Trl` / `RV_*` are compiler input, never
runtime** (§3); **citations are reference-ids in metadata-JSON, always representable.**
GATE = OPEN (residue is `ad_table_map` slotting, tracked into PB).

### §0.1 PB explicit slotting (resolves the 36 residual edges → unmapped=0)

The PV gate's heuristic dumped 32 tables into the `items` fallback (or the
`/Year|Project/` `containers` regex) when they are really lines / sub-documents /
junctions. The explicit `ad_table_map.js` (PB artifact 2) overrides them. Each
override is **extract-derived** — it resolves a specific unmappable edge logged by
`test_5table_bom.js` (witness: `/tmp/pb/unmappable_all.txt`, 2026-05-29) and is
confirmed against `DocStatus` membership (isDoc query, same date):

| Override slot | Tables | Why (the edge it fixes) |
|---|---|---|
| `documents` (sub-doc, `parent_id`) | `M_ProductionPlan` | groups lines under `M_Production`; no `DocStatus` so heuristic mis-slotted it `items`. `M_ProductionLine→M_ProductionPlan` then = line→doc. |
| `document_lines` (line / MA / confirm) | `C_InvoicePaySchedule` `C_OrderPaySchedule` `C_OrderLandedCost` `C_PaymentAllocate` `C_POSPayment` `M_Package` `M_InOutLineConfirm` `M_InOutLineMA` `M_InventoryLineMA` `M_MovementLineConfirm` `M_MovementLineMA` `M_ProductionLineMA` `PP_Cost_CollectorMA` `PP_Order_BOM` `PP_Order_Cost` `PP_Order_Workflow` `PP_Order_Node_Asset` `PP_Order_NodeNext` `PP_Order_Node_Product` `MP_Maintain_Task` `MP_OT_Task` `A_Depreciation_Exp` | child of a document (→`document_id`) or of another line (→`source_line_id`); parents confirmed `documents` by isDoc (`M_InOutConfirm`, `MP_Maintain`, `PP_Order_Node`, …). |
| `document_lines` (settlement, `match_type`) | `M_MatchInv` `M_MatchPO` `C_LandedCost` `C_LandedCostAllocation` | §18.2 three-way-match: a row LINKS ≥2 lines across documents — `source_line_id` to one line, counterpart line-ref in `metadata`, tagged `match_type`. An edge, not content. |
| `items` (master / link) | `R_IssueProject` `HR_Year` | not spatial containers — the `/Project|Year/` regex over-grabbed; both reduce to `items→items`. |

Two `representable()` refinements the explicit slots expose (the true 5-table
relationship set, not heuristic gaps): **(a)** a line citing master data
(`document_lines → items`, e.g. `S_TimeExpenseLine → C_BPartner`) is a metadata
reference — representable, same as the already-allowed `documents → items`;
**(b)** a document posting to the ledger (`documents → journal`, e.g.
`A_Asset_Addition → GL_JournalBatch`) is a posting reference carried in
`metadata`/`journal.source` — not a lifecycle derivation.

**2nd-order cascade (found by running the explicit gate, `test_bridge.js`):** moving
a table to `document_lines`/`documents` re-slots its *children*. 8 more tables
slotted: child lines `M_PackageLine` `M_PackageMPS` `PP_Order_BOMLine`
`MP_Maintain_Resource` `MP_OT_Resource` `C_OrderLandedCostAllocation` →
`document_lines` (→`source_line_id`); `PP_Order_Workflow` → `documents` (sub-doc
header that `PP_Order_Node` nests under); `HR_Period` → `items` (sibling of
`HR_Year`). **GATE RESULT (witness `§BRIDGE`, `scripts/test_bridge.js`, 2026-05-29):
`map windows=7 docTypes=49 unmapped=0`; round-trip/lineage/match all PASS; PB gate
GREEN.**

---

## §1. iDempiere AD → SQLite Table Mapping

### Source: live PostgreSQL (docker)

```
docker start postgres
docker exec postgres psql -U adempiere -d idempiere
```

### AD metadata tables to export

| PostgreSQL Table | Rows | Purpose | SQLite mapping |
|---|---|---|---|
| AD_Menu | 826 | Menu tree nodes | Direct copy |
| AD_TreeNodeMM | 828 | Parent-child hierarchy for menu | Direct copy |
| AD_Window | 458 | Window definitions | Direct copy |
| AD_Tab | 1,167 | Tabs within windows | Direct copy |
| AD_Field | 21,432 | Fields within tabs | Direct copy |
| AD_Column | 26,519 | Column metadata (type, length, mandatory) | Direct copy |
| AD_Table | 1,076 | Table definitions | Direct copy |
| AD_Reference | 606 | Reference types (dropdown, search, etc.) | Direct copy |
| AD_Ref_List | 1,545 | Dropdown option values | Direct copy |
| AD_Element | 6,026 | UI labels and descriptions | Direct copy |

**Total: ~60,000 rows of metadata. Estimated SQLite size: ~8-12 MB.**

All tables use `CREATE TABLE IF NOT EXISTS` — safe to add to any existing DB.

### Column subset (strip unused)

Each AD table has ~30-60 columns. Many are unused by the UI renderer
(CreatedBy, UpdatedBy, AD_Client_ID, AD_Org_ID for multi-tenant). We export
a focused subset:

**AD_Menu:**
```sql
CREATE TABLE IF NOT EXISTS AD_Menu (
    AD_Menu_ID    INTEGER PRIMARY KEY,
    Name          TEXT NOT NULL,
    Description   TEXT,
    IsSummary     TEXT DEFAULT 'N',      -- Y = folder, N = leaf
    Action        TEXT,                   -- W=Window, R=Report, P=Process, X=Form
    AD_Window_ID  INTEGER,
    AD_Process_ID INTEGER,
    AD_Form_ID    INTEGER,
    IsActive      TEXT DEFAULT 'Y'
);
```

**AD_TreeNodeMM** (menu hierarchy):
```sql
CREATE TABLE IF NOT EXISTS AD_TreeNodeMM (
    AD_Tree_ID  INTEGER NOT NULL,        -- 10 = main menu tree
    Node_ID     INTEGER NOT NULL,        -- = AD_Menu_ID
    Parent_ID   INTEGER NOT NULL,        -- 0 = root
    SeqNo       INTEGER DEFAULT 0,
    IsActive    TEXT DEFAULT 'Y',
    PRIMARY KEY (AD_Tree_ID, Node_ID)
);
```

**AD_Window:**
```sql
CREATE TABLE IF NOT EXISTS AD_Window (
    AD_Window_ID  INTEGER PRIMARY KEY,
    Name          TEXT NOT NULL,
    Description   TEXT,
    Help          TEXT,
    WindowType    TEXT DEFAULT 'M',      -- M=Maintain, T=Transaction, Q=Query
    IsActive      TEXT DEFAULT 'Y'
);
```

**AD_Tab:**
```sql
CREATE TABLE IF NOT EXISTS AD_Tab (
    AD_Tab_ID     INTEGER PRIMARY KEY,
    AD_Window_ID  INTEGER NOT NULL,
    Name          TEXT NOT NULL,
    Description   TEXT,
    Help          TEXT,
    AD_Table_ID   INTEGER NOT NULL,
    TabLevel      INTEGER DEFAULT 0,     -- 0=header, 1=detail, 2=sub-detail
    SeqNo         INTEGER DEFAULT 10,
    IsSingleRow   TEXT DEFAULT 'N',
    IsReadOnly    TEXT DEFAULT 'N',
    WhereClause   TEXT,
    OrderByClause TEXT,
    IsActive      TEXT DEFAULT 'Y'
);
```

**AD_Field:**
```sql
CREATE TABLE IF NOT EXISTS AD_Field (
    AD_Field_ID   INTEGER PRIMARY KEY,
    AD_Tab_ID     INTEGER NOT NULL,
    AD_Column_ID  INTEGER NOT NULL,
    Name          TEXT NOT NULL,
    Description   TEXT,
    Help          TEXT,
    SeqNo         INTEGER DEFAULT 10,
    IsDisplayed   TEXT DEFAULT 'Y',
    DisplayLogic  TEXT,                  -- iDempiere logic expression
    IsMandatory   TEXT,                  -- Y/N or logic expression
    IsReadOnly    TEXT DEFAULT 'N',
    DefaultValue  TEXT,
    IsActive      TEXT DEFAULT 'Y'
);
```

**AD_Column:**
```sql
CREATE TABLE IF NOT EXISTS AD_Column (
    AD_Column_ID    INTEGER PRIMARY KEY,
    AD_Table_ID     INTEGER NOT NULL,
    ColumnName      TEXT NOT NULL,
    Name            TEXT,
    Description     TEXT,
    AD_Reference_ID INTEGER,             -- field type: 10=String, 11=Integer, 19=TableDirect, etc.
    AD_Val_Rule_ID  INTEGER,
    FieldLength     INTEGER DEFAULT 0,
    IsMandatory     TEXT DEFAULT 'N',
    IsKey           TEXT DEFAULT 'N',
    IsIdentifier    TEXT DEFAULT 'N',
    DefaultValue    TEXT,
    ValueMin        TEXT,
    ValueMax        TEXT,
    IsActive        TEXT DEFAULT 'Y'
);
```

**AD_Table:**
```sql
CREATE TABLE IF NOT EXISTS AD_Table (
    AD_Table_ID   INTEGER PRIMARY KEY,
    TableName     TEXT NOT NULL,
    Name          TEXT,
    Description   TEXT,
    AD_Window_ID  INTEGER,
    IsActive      TEXT DEFAULT 'Y'
);
```

**AD_Reference & AD_Ref_List:**
```sql
CREATE TABLE IF NOT EXISTS AD_Reference (
    AD_Reference_ID INTEGER PRIMARY KEY,
    Name            TEXT NOT NULL,
    Description     TEXT,
    ValidationType  TEXT,                -- L=List, T=Table, D=DataType
    IsActive        TEXT DEFAULT 'Y'
);

CREATE TABLE IF NOT EXISTS AD_Ref_List (
    AD_Ref_List_ID  INTEGER PRIMARY KEY,
    AD_Reference_ID INTEGER NOT NULL,
    Value           TEXT NOT NULL,
    Name            TEXT NOT NULL,
    Description     TEXT,
    IsActive        TEXT DEFAULT 'Y'
);
```

**AD_Element** (labels):
```sql
CREATE TABLE IF NOT EXISTS AD_Element (
    AD_Element_ID INTEGER PRIMARY KEY,
    ColumnName    TEXT,
    Name          TEXT NOT NULL,
    PrintName     TEXT,
    Description   TEXT,
    IsActive      TEXT DEFAULT 'Y'
);
```

---

## §2. PostgreSQL Export Strategy

### Script: `scripts/export_ad.sh`

```bash
#!/bin/bash
# Export iDempiere AD metadata from PostgreSQL to SQLite
# Requires: docker postgres container running with idempiere DB

CONTAINER=postgres
DB=idempiere
USER=adempiere
OUTPUT=deploy/dev/ad_seed.sql

TABLES=(
  "AD_Menu:AD_Menu_ID,Name,Description,IsSummary,Action,AD_Window_ID,AD_Process_ID,AD_Form_ID,IsActive"
  "AD_TreeNodeMM:AD_Tree_ID,Node_ID,Parent_ID,SeqNo,IsActive"
  "AD_Window:AD_Window_ID,Name,Description,Help,WindowType,IsActive"
  "AD_Tab:AD_Tab_ID,AD_Window_ID,Name,Description,Help,AD_Table_ID,TabLevel,SeqNo,IsSingleRow,IsReadOnly,WhereClause,OrderByClause,IsActive"
  "AD_Field:AD_Field_ID,AD_Tab_ID,AD_Column_ID,Name,Description,Help,SeqNo,IsDisplayed,DisplayLogic,IsMandatory,IsReadOnly,DefaultValue,IsActive"
  "AD_Column:AD_Column_ID,AD_Table_ID,ColumnName,Name,Description,AD_Reference_ID,AD_Val_Rule_ID,FieldLength,IsMandatory,IsKey,IsIdentifier,DefaultValue,ValueMin,ValueMax,IsActive"
  "AD_Table:AD_Table_ID,TableName,Name,Description,AD_Window_ID,IsActive"
  "AD_Reference:AD_Reference_ID,Name,Description,ValidationType,IsActive"
  "AD_Ref_List:AD_Ref_List_ID,AD_Reference_ID,Value,Name,Description,IsActive"
  "AD_Element:AD_Element_ID,ColumnName,Name,PrintName,Description,IsActive"
)
```

For each table: `COPY (SELECT columns FROM table WHERE IsActive='Y') TO STDOUT CSV`
→ parse CSV → generate `INSERT OR IGNORE INTO` statements → write to ad_seed.sql.

### Data sanitization
- Strip `IsActive='N'` rows (reduces noise)
- NULL → empty string for TEXT, 0 for INTEGER
- Escape single quotes in text values
- Filter AD_TreeNodeMM to `AD_Tree_ID = 10` (main menu tree only)
- No BLOBs in AD tables — all text/integer

### Expected output size
- ~60,000 INSERT statements
- ~4-8 MB SQL file
- Loads in < 2 seconds via sql.js

---

## §3. AD Parser Design — `ad_parser.js`

Pure data reader. No UI. No side effects except §-log.

### API

```javascript
ADParser.init(db)
  // §AD_PARSER init
  // Reads AD table counts, logs: menu=826 windows=458 tabs=1167 fields=21432

ADParser.getMenuTree(db)
  // §AD_PARSER menuTree nodes=826 roots=15
  // Returns: { id, name, children: [...], action, windowId, isSummary }
  // Builds tree from AD_Menu + AD_TreeNodeMM (Parent_ID → children)

ADParser.getWindow(db, windowId)
  // §AD_PARSER getWindow id=130 name=Project tabs=8
  // Returns: { id, name, description, windowType, tabs: [...] }

ADParser.getTabs(db, windowId)
  // §AD_PARSER getTabs windowId=130 count=8
  // Returns: [{ id, name, tabLevel, seqNo, tableName, fields: [...] }]
  // Sorted by SeqNo. Each tab includes its fields.

ADParser.getFields(db, tabId)
  // §AD_PARSER getFields tabId=157 count=45
  // Returns: [{ id, name, columnName, referenceId, seqNo, isDisplayed,
  //             displayLogic, isMandatory, isReadOnly, defaultValue }]
  // Joins AD_Field → AD_Column for column metadata

ADParser.resolveReference(db, referenceId)
  // §AD_PARSER resolveRef id=319 type=List options=5
  // Returns: [{ value, name, description }] for List references
  // Returns: { tableName, keyColumn, displayColumn } for Table references

ADParser.getTableName(db, tableId)
  // Returns TableName from AD_Table
```

### iDempiere Reference Types (AD_Reference_ID values)

| ID | Name | Renderer |
|---|---|---|
| 10 | String | `<input type="text">` |
| 11 | Integer | `<input type="number">` |
| 12 | Amount | `<input type="number" step="0.01">` |
| 13 | ID | hidden (primary key) |
| 14 | Text | `<textarea>` |
| 15 | Date | `<input type="date">` |
| 16 | DateTime | `<input type="datetime-local">` |
| 17 | List | `<select>` from AD_Ref_List |
| 19 | TableDirect | `<select>` from referenced table |
| 20 | Table | `<select>` with AD_Val_Rule filter |
| 22 | Number | `<input type="number" step="any">` |
| 28 | Button | `<button>` (triggers DocAction) |
| 29 | Quantity | `<input type="number">` |
| 30 | Search | `<input>` with typeahead lookup |
| 38 | YesNo | `<input type="checkbox">` |

---

## §4. Card-First AD Renderer — `ad_ui.js`

Mobile-first. Dark theme. Same visual language as BIM OOTB.

### Menu — hamburger sidebar

```
┌──────────────────────────────────┐
│ ☰ ERP OOTB           [🔍] [👤]  │  App bar
├──────────────────────────────────┤
│                                  │
│  ┌────────────────────────────┐  │
│  │  C_Project: ABC Tower      │  │  Current window
│  │  Phase: Foundation          │  │  as card
│  │  Status: ⬤ IN_PROGRESS      │  │
│  │  ...                        │  │
│  └────────────────────────────┘  │
│                                  │
│  ← swipe → (next record)        │
│  ↑ swipe ↑ (next tab)           │
│                                  │
└──────────────────────────────────┘
```

Tap ☰ → sidebar slides in:

```
┌────────────────────┬─────────────┐
│ ☰ Menu             │             │
│                    │  (dimmed)   │
│ ▼ Partner Relations│             │
│   ● Business Partn │             │
│   ○ Contact        │             │
│ ▼ Materials Mgmt   │             │
│   ● Product        │             │
│   ○ Price List     │             │
│ ▼ Project Mgmt     │             │
│   ● Project ←ACTIVE│             │
│ ▼ Financial        │             │
│   ○ GL Journal     │             │
│ ▼ Manufacturing    │             │
│   ○ BOM            │             │
│ ...                │             │
└────────────────────┴─────────────┘
```

### Window → tab cards

Each AD_Window renders as a card stack. Tabs are horizontal swipe pages.
Reuses `swipe.js` from P3 UI.

Tab header bar:
```
┌──────────────────────────────────┐
│ [Project] [Phase] [Task] [Line] │  Tab bar (scroll horizontal)
│  ════════                       │  Underline = active tab
├──────────────────────────────────┤
│                                  │
│  Name: ABC Tower Construction    │  Fields rendered from AD_Field
│  Status: [▼ In Progress]        │  Reference types determine widget
│  Start Date: [2026-05-13]       │
│  End Date: [2026-12-31]         │
│  ...                            │
│                                  │
│  [Save] [Delete] [New]          │  Actions
│                                  │
└──────────────────────────────────┘
```

### Field rendering rules

```javascript
// For each AD_Field in the tab:
// 1. Get AD_Column via AD_Column_ID
// 2. Get Reference type via AD_Reference_ID
// 3. Render appropriate input widget
// 4. Apply DisplayLogic (show/hide)
// 5. Apply IsMandatory (red border if empty)
// 6. Apply IsReadOnly (disabled)
// 7. Apply DefaultValue on new record
```

### DisplayLogic parser

iDempiere display logic format: `@ColumnName@='value'&@Other@!''`

```javascript
function evaluateDisplayLogic(logic, record) {
  // Replace @ColumnName@ with record[ColumnName]
  // Evaluate: = (equals), ! (not equals), > < >= <=
  // Combine: & (AND), | (OR)
  // Returns: true (show) or false (hide)
}
```

---

## §4b. HTML-Native UI — Replacing ZK Patterns

> **Principle:** iDempiere uses ZK Framework (server-side Java → HTML widget rendering, ~2006 architecture). Every click is a server round-trip. HTML Living Standard (2024+) provides native equivalents that are faster, offline-capable, and GPU-accelerated — no framework needed.

### Accordion Data Panels — `<details>` + CSS

ZK uses `Groupbox` with server-managed open/close state. HTML `<details>` is native, zero-JS, accessible:

```html
<!-- Native accordion — no JS, no framework, keyboard accessible -->
<details open>
  <summary>Partner Relations</summary>
  <div class="accordion-body">
    <a href="#" data-window="C_BPartner">Business Partner</a>
    <a href="#" data-window="AD_User">Contact</a>
  </div>
</details>
<details>
  <summary>Materials Management</summary>
  <div class="accordion-body">
    <a href="#" data-window="M_Product">Product</a>
    <a href="#" data-window="M_PriceList">Price List</a>
  </div>
</details>
```

**Enhancements over ZK:**

| ZK Pattern | HTML-Native Replacement | Advantage |
|---|---|---|
| `Groupbox` open/close | `<details>/<summary>` | Zero JS, keyboard accessible, animated via CSS `transition` |
| `Tabbox` tab switching | CSS `scroll-snap` + swipe | Native momentum, touch-friendly, no server round-trip |
| `Listbox` row selection | `<dialog>` + popover list | Native modal, backdrop, Escape key, focus trap |
| `Textbox` validation | `<input>` + `pattern` + `:invalid` CSS | Browser-native, no server validation round-trip |
| `Datebox` calendar | `<input type="date">` | Native picker on all platforms, locale-aware |
| `Combobox` dropdown | `<datalist>` + `<input list>` | Native search-as-you-type, no custom JS |
| `Tree` hierarchy | Nested `<details>` | Collapsible tree with zero JS, infinite nesting |
| `Grid` data table | CSS Grid + `subgrid` | Responsive, sticky headers, no fixed columns |
| `Messagebox` alert | `<dialog>` element | Native modal, animatable, no Z-index wars |

### Animated Accordion (CSS only)

```css
details .accordion-body {
  display: grid;
  grid-template-rows: 0fr;
  transition: grid-template-rows 0.3s ease;
  overflow: hidden;
}
details[open] .accordion-body {
  grid-template-rows: 1fr;
}
details summary {
  cursor: pointer;
  padding: 12px 16px;
  background: rgba(255,255,255,0.05);
  border-radius: 8px;
  list-style: none;  /* remove default triangle */
}
details summary::before {
  content: '▸';
  display: inline-block;
  transition: transform 0.2s;
  margin-right: 8px;
}
details[open] summary::before {
  transform: rotate(90deg);
}
```

### View Transitions (panel morphing)

When user navigates between ERP windows (e.g. Project → Phase → Task), the DOM morphs with cross-fade:

```javascript
// One line wraps any DOM change into a smooth transition
document.startViewTransition(() => {
  renderWindow(nextWindowId);  // swaps DOM content
});
```

Browser automatically:
1. Snapshots old state
2. Renders new state
3. Cross-fades between them (GPU-accelerated, ~16ms)
4. Falls back to instant swap if unsupported

**No animation library needed.** This replaces ZK's `Clients.evalJavascript()` animation hacks.

### Scroll-Snap for Tab Navigation

Instead of ZK `Tabbox` (click tab header → server fetches panel → re-renders):

```css
.tab-container {
  display: flex;
  overflow-x: auto;
  scroll-snap-type: x mandatory;
  -webkit-overflow-scrolling: touch;
}
.tab-panel {
  flex: 0 0 100%;
  scroll-snap-align: start;
}
```

User swipes left/right between tab panels. Native momentum, no JS event handling, works on mobile. Tab header highlights sync via `IntersectionObserver`:

```javascript
const observer = new IntersectionObserver(entries => {
  entries.forEach(e => {
    if (e.isIntersecting) highlightTab(e.target.dataset.tabId);
  });
}, { root: tabContainer, threshold: 0.5 });
tabPanels.forEach(p => observer.observe(p));
```

### Popover for Field Help + FK Lookup

```html
<!-- Field with popover help -->
<label>
  Business Partner
  <button popovertarget="bp-help">?</button>
</label>
<input type="text" list="bp-list" />
<datalist id="bp-list">
  <!-- populated from AD_Ref_List or FK query -->
</datalist>
<div id="bp-help" popover>
  Select the business partner for this transaction.
  Links to C_BPartner table.
</div>
```

`popover` attribute: native positioning, auto-dismiss on outside click, no Z-index management. Replaces ZK's `Popup` component entirely.

### Container Queries for Responsive Panels

Each ERP panel resizes based on its own width, not the viewport:

```css
.erp-panel {
  container-type: inline-size;
}
@container (max-width: 400px) {
  .field-row { flex-direction: column; }  /* stack labels above inputs */
}
@container (min-width: 600px) {
  .field-row { display: grid; grid-template-columns: 150px 1fr; }  /* label left, input right */
}
```

This means the same panel works in:
- Full-screen desktop (wide layout)
- Side-by-side with 3D viewer (narrow layout)
- Mobile (stacked layout)

No media queries, no breakpoint math — each panel is self-contained.

### Performance Comparison

| Operation | ZK (iDempiere) | HTML-Native (ERP OOTB) |
|---|---|---|
| Open accordion | ~200ms (server round-trip + re-render) | ~16ms (CSS transition, no JS) |
| Switch tab | ~300-500ms (AJAX + DOM replace) | ~0ms (scroll-snap, already loaded) |
| Show tooltip | ~150ms (server → Popup component) | ~1ms (popover, native) |
| Validate field | ~200ms (server → Callout) | ~0ms (browser `:invalid`, pattern match) |
| Load dropdown | ~300ms (AJAX → Listbox) | ~5ms (`<datalist>` from IndexedDB) |
| Render 100 rows | ~1-2s (server → Grid → DOM) | ~10ms (CSS Grid, virtual scroll) |

**Total tab-to-data: ZK ~2-5s per interaction vs HTML-native ~20ms.**

### Implementation in `ad_ui.js`

The existing card renderer (`ad_ui.js`) already uses dark-theme cards with swipe. The accordion pattern replaces the sidebar menu:

1. **Menu**: nested `<details>` tree (replaces current sidebar list)
2. **Window tabs**: `scroll-snap` container (replaces current tab bar click handlers)
3. **Field rendering**: `<datalist>` for FK lookups, `<input type="date/number">` for typed fields, `<dialog>` for confirmations
4. **Transitions**: `document.startViewTransition()` wrapping `renderWindow()`
5. **Responsive**: `container-type: inline-size` on `.erp-panel`

All native. No framework. No build step. Same pattern as the BIM viewer: single HTML, browser does the work.

---

## §5. C_Project = Construction POC Bridge

The existing construction POC handlers map directly to C_Project AD window:

| POC Concept | AD Concept | Table |
|---|---|---|
| Lead/Document | C_Project record | C_Project |
| Phase container | C_ProjectPhase tab | C_ProjectPhase |
| Task | C_ProjectTask tab | C_ProjectTask |
| BOQ line | C_ProjectLine tab | C_ProjectLine |
| Status lifecycle | DocAction (DR→IP→CO→VO) | C_Project.DocStatus |

### BIM integration

C_Project window gets a special "BIM" action button:
- If the project has a `geometry_id` or IFC link → show "Open in 3D"
- BroadcastChannel posts to viewer for cross-tab highlighting
- Reuses existing `bim_4d` channel pattern from boq_charts.html

---

## §6. Cross-Table Analytics — Surpassing Odoo

**The killer feature.** Because ALL data is in one SQLite DB in the browser,
any SQL query across any tables renders as a chart instantly. No server.

### Built-in chart views

Each AD_Window gets a [📊] button that opens an analytics overlay:

```sql
-- Projects by status (C_Project)
SELECT DocStatus, COUNT(*), SUM(PlannedAmt)
FROM C_Project GROUP BY DocStatus

-- Products by category with total value
SELECT pc.Name, COUNT(*), SUM(pp.PriceStd * pp.PriceLimit)
FROM M_Product p
JOIN M_Product_Category pc ON p.M_Product_Category_ID = pc.M_Product_Category_ID
LEFT JOIN M_ProductPrice pp ON p.M_Product_ID = pp.M_Product_ID
GROUP BY pc.Name

-- Partner aging (open invoices)
SELECT bp.Name, SUM(inv.GrandTotal), MIN(inv.DateInvoiced)
FROM C_Invoice inv
JOIN C_BPartner bp ON inv.C_BPartner_ID = bp.C_BPartner_ID
WHERE inv.IsPaid = 'N'
GROUP BY bp.Name ORDER BY MIN(inv.DateInvoiced)
```

Rendered using Canvas bar/pie charts — same pattern as `boq_charts.html`.

**Why this surpasses Odoo:**
- Odoo needs server + ORM + Python for cross-table queries
- We need one SQL statement + Canvas — executes in milliseconds, offline
- User can type ANY SQL in a query box and see results as a chart
- The query IS the report. No Jasper. No report designer. Just SQL.

---

## §7. BroadcastChannel Integration

### Channel: `bim_erp`

Following the existing `bim_4d` pattern in `main.js` line 154.

| Direction | Message | Payload | Action |
|---|---|---|---|
| ERP → Viewer | `ERP_FOCUS_STOREY` | `{ storey: 'Ground Floor' }` | Viewer filters to storey |
| ERP → Viewer | `ERP_HIGHLIGHT` | `{ guids: [...], color }` | Viewer highlights elements |
| ERP → Viewer | `ERP_PING` | `{}` | Check if viewer is open |
| Viewer → ERP | `ERP_ELEMENT_PICKED` | `{ guid, ifc_class, storey }` | ERP shows related records |
| Viewer → ERP | `ERP_PONG` | `{}` | Viewer is alive |

---

## §8. Landing Page Integration

### SYSNOVA/index.html changes

1. Each building card gets an amber "ERP" button alongside "3D Viewer"
2. `openERP(archName)` opens `sandbox/erp.html?db=buildings/{name}_extracted.db`
3. Footer "ERP — GOD MODE" becomes "ERP OOTB" linking to `sandbox/erp.html`
4. Tab tracker shows ERP tabs alongside 3D tabs

### Standalone mode

`erp.html` with no `?db=` parameter → loads `ad_seed.sql` with full AD metadata
+ sample data for C_Project, C_BPartner, M_Product. This is the demo mode.

`erp.html?db=Hospital.db` → loads building DB, adds AD tables on top,
bootstraps containers from BIM elements_meta storeys.

---

## §9. Implementation Phases

### Phase 1 — This session (spec + export + parser)

| Step | Deliverable | Verify |
|---|---|---|
| 1 | `docs/ERP.md` (this file) | Spec complete |
| 2 | `scripts/export_ad.sh` | Exports 10 AD tables from PostgreSQL |
| 3 | `deploy/dev/ad_seed.sql` | ~60K rows of real iDempiere AD metadata |
| 4 | `deploy/dev/ad_parser.js` | getMenuTree, getWindow, getTabs, getFields |
| 5 | `tests/test_ad_parser.js` | §-log verified: menu=826, windows=458, tabs=1167 |

### Phase 2 — Next session (renderer + CRUD)

| Step | Deliverable | Verify |
|---|---|---|
| 6 | `deploy/dev/ad_ui.js` | Menu sidebar, window cards, tab swipe, field inputs |
| 7 | `deploy/dev/ad_data.js` | Generic CRUD for any AD_Table |
| 8 | `deploy/dev/erp.html` rewrite | Full AD-driven UI |
| 9 | `deploy/dev/ad_charts.js` | Cross-table analytics charts |
| 10 | Landing page integration | ERP buttons on building cards |

### Phase 3 — Showcase session

| Step | Deliverable | Verify |
|---|---|---|
| 11 | Sample data for 3 windows | C_Project, C_BPartner, M_Product with real records |
| 12 | BroadcastChannel wiring | ERP ↔ BIM viewer cross-tab |
| 13 | Deploy to OCI dev bucket | Live at sandbox/erp.html |
| 14 | Video demo | Full AD menu → C_Project → BIM link → chart |

---

## §10. The Seismic Claim

**iDempiere's Application Dictionary — 826 menu nodes, 458 windows, 1167 tabs,
21,432 fields — running in a browser from a 12MB SQLite file loaded via
WebAssembly. Zero server. Zero install. Offline capable.**

No one in the FOSS ERP world has done this. Not SAP. Not Odoo. Not ERPNext.
Not even iDempiere itself.

The AD is iDempiere's crown jewel — 20 years of metadata accumulated by a
global community. We're not replacing it. We're **liberating it** from the
JVM/PostgreSQL/OSGi stack and putting it in every browser on every phone.

The construction POC (C_Project) is the proof. The 3 live windows are the
demo. The full 826-node menu tree is the mic drop.

---

## §11. The Three-Layer Architecture

§1-§10 proved the AD can run in a browser. But running it unchanged —
13MB of metadata, 1003 tables, 20,911 field definitions — is carrying
the server's luggage into a building designed for hand baggage.

The next evolution separates the ERP into three clean layers:

```
┌─────────────────────────────────────────────────────────┐
│  PRESENTATION — what the user sees and touches          │
│  Globe constellation, accordion drill, pills, settings  │
│  Reads data via SQL. Triggers logic via user actions.    │
│  Files: ad_ui.js, ad_graph.js, erp.html                │
├─────────────────────────────────────────────────────────┤
│  LOGIC — business rules, pure functions                 │
│  "When payment completes, allocate against invoices"     │
│  Takes db + document. Produces kernel_ops. Testable.     │
│  Files: rules/*.js (allocation, pricing, tax, bom, etc) │
├─────────────────────────────────────────────────────────┤
│  DATA — 5 tables + kernel_ops + journal                 │
│  Dumb storage. No triggers. No procedures. Portable.     │
│  The op log IS the audit trail. Journal IS the books.    │
│  Files: SQLite WASM (ad_seed.db or business.db)         │
└─────────────────────────────────────────────────────────┘
```

**Boundaries (enforced, not suggested):**
- Presentation → Logic: calls rule functions on user action
- Logic → Data: `commitOp()` is the ONLY write path
- Data → Presentation: SQL queries are the ONLY read path
- No layer reaches into another's internals

### §11.1 Why separation matters

In iDempiere, logic is scattered across 6 extension points:
ModelValidator, Callout, DocAction, Process, DB triggers, AD_Val_Rule.
A developer changing "what happens when an invoice completes" must check
all six. This is the real ERP monster — not the data model, not the UI,
but the logic spaghetti.

In ERP OOTB, logic lives in ONE place: rule functions. Each is a pure
function that takes a database handle and a document ID, reads what it
needs, and produces kernel_ops. No framework. No base class. No interface
to implement. Just: data in, ops out.

```javascript
// rules/allocation.js — pure function, testable, readable
function allocatePayment(db, paymentId) {
  var payment = getDoc(db, paymentId);
  var invoices = getOpenInvoices(db, payment.metadata.partner_id);
  var remaining = payment.metadata.amount;
  for (var i = 0; i < invoices.length && remaining > 0; i++) {
    var allocated = Math.min(remaining, invoices[i].metadata.amount);
    commitOp(db, 'ALLOCATE', {
      payment: paymentId, invoice: invoices[i].id, amount: allocated
    });
    remaining -= allocated;
  }
}
```

This function is:
- **Testable** — pass it a mock db, verify the ops it produces
- **Readable** — a business person can follow the logic
- **Reversible** — undo the commitOps, the allocation unwinds
- **Auditable** — kernel_ops records which rule fired and why

---

## §12. The 5-Table Foundation

iDempiere has 1003 tables because it grew organically over 20 years —
each module adding its own tables. The 5-table design (from SpatialERP
POC §3.1) says: **a document is a document.**

| Table | What | Replaces in iDempiere |
|---|---|---|
| `containers` | Spatial hierarchy: Site→Building→Floor | C_Project, C_ProjectPhase, M_Warehouse |
| `items` | Things in containers: products, partners, materials | M_Product, C_BPartner, M_BOM |
| `documents` | ALL doc types in ONE table | C_Order, C_Invoice, C_Payment, M_InOut |
| `document_lines` | Lines for any document | C_OrderLine, C_InvoiceLine, M_InOutLine |
| `journal` | Auto-posted double-entry accounting | GL_Journal, Fact_Acct |
| + `kernel_ops` | Event log, undo/redo, audit trail | AD_ChangeLog (no undo equivalent) |

A Purchase Order, an Invoice, a Lead, a Credit Memo — they're all rows
in `documents` with different `doc_type`. The structure is identical.
Only the business rules differ, and those live in the logic layer, not
the schema.

### §12.1 The journal as proof of correctness

Every document completion produces journal entries:
```
Complete PO #1001 → debit INVENTORY, credit ACCOUNTS_PAYABLE
Complete Invoice #5001 → debit ACCOUNTS_PAYABLE, credit CASH
```

Undo a completion → `journalReverse()` posts counter-entries → net = 0.
This is double-entry bookkeeping — Luca Pacioli's 1494 invention — in
30 lines of JavaScript. The journal is the source of truth for finance.
If the journal balances, the books are correct. Everything else is UI.

### §12.2 The metadata JSON column

Each table has a `metadata TEXT DEFAULT '{}'` column. This is where
domain-specific fields live: `land_size` for a property lead,
`payment_terms` for a PO, `tax_rate` for an invoice line.

This is a deliberate trade-off: schemaless flexibility over rigid columns.
The compiled manifest (§13) tells the UI which metadata fields to render
for each doc_type. The kernel enforces mandatory metadata fields at
commitOp time. But the table schema never changes.

---

## §13. The Compiled Manifest — AD as Compiler Input

The full AD (13MB, 1003 tables, 20,911 fields) is **compile-time input**,
not a runtime dependency. A build script reads ad_seed.db and outputs a
slim manifest:

```
ad_seed.db (13MB)  →  compile_manifest.js  →  manifest.json (~2KB)
   source code              compiler               compiled artifact
```

The manifest contains only what the browser needs:

```json
{
  "windows": {
    "123": {
      "name": "Business Partner",
      "table": "items",
      "doc_type": null,
      "tabs": [
        { "name": "Partner", "level": 0,
          "fields": [
            {"col": "Name", "type": "string", "mandatory": true},
            {"col": "IsCustomer", "type": "yesno"}
          ]},
        { "name": "Contact", "level": 1, "fk": "partner_id",
          "fields": [
            {"col": "Name", "type": "string", "mandatory": true},
            {"col": "EMail", "type": "string"}
          ]}
      ]
    }
  },
  "state_machine": {
    "PURCHASE_ORDER": ["Drafted","Completed","Voided","Closed"],
    "INVOICE": ["Drafted","Completed","Reversed","Voided"]
  },
  "downstream": {
    "PURCHASE_ORDER": ["INVOICE","SHIPMENT"],
    "INVOICE": ["PAYMENT"]
  }
}
```

**Runtime payload:** initbubble.json (2KB) + manifest.json (2KB) = 4KB.
Everything the globe, accordion, and kernel enforcement need. WASM + the
business data SQLite only loads when the user drills into actual records.

The AD stays on the shelf as the reference — the source code. The manifest
is the compiled binary. The browser runs the binary. You edit the source
when you need to change the UI structure, then recompile.

---

## §14. Kernel Gravity — The Op Log as Constellation Driver

Traditional ERP dashboards query data volume: `SELECT COUNT(*) FROM C_Order`.
This tells you how much data exists, not what matters right now.

kernel_ops provides **activity gravity**: what's being worked on, by whom,
how recently. One query replaces N per-table counts:

```sql
SELECT json_extract(parameters, '$.table') AS tbl,
       COUNT(DISTINCT json_extract(parameters, '$.id')) AS records,
       MAX(timestamp) AS last_touch
FROM kernel_ops
WHERE undone = 0 AND timestamp > ?
GROUP BY tbl
```

The constellation's bubble aura (glow radius) is driven by this gravity.
Bright = active. Dim = dormant. Pulsing = just changed. The globe becomes
a **live operations monitor** — the user's activity shapes the view.

Op type weighting prevents noise (20 typo edits ≠ 20 meaningful actions):

| Op type | Gravity weight |
|---|---|
| AD_SAVE (new record) | 1.0 |
| AD_SAVE (update) | 0.2 |
| DOC_COMPLETE | 2.0 |
| DOC_VOID | 1.5 |
| ALLOCATE | 1.0 |
| AD_DELETE | 0.5 |
| SESSION_START | 0.0 |

---

## §15. The Known Monsters — Local-First Risk Assessment

Replacing a server-mediated ERP with browser-only storage creates three
known failure modes. Each is bounded and solvable.

**Full analysis:** `prompts/ERP_KERNEL_MONSTERS.md`

| Monster | Risk | Core mitigation |
|---|---|---|
| Two tabs overwrite same record | LOW (single user) | BroadcastChannel awareness toast |
| Orphaned documents (undo parent with children) | MEDIUM | Downstream FK check in kernel (50 lines) |
| Gravity noise (edits ≠ significance) | LOW (UX only) | Distinct record count + op type weighting |

### §15.1 The 10 Invariants

Business rules ERP OOTB must enforce. If the kernel handles all 10,
AD field settings become optional structural metadata.

| # | Invariant | Kernel enforcement |
|---|---|---|
| 1 | Mandatory fields | commitOp rejects null on mandatory |
| 2 | FK integrity | commitOp checks referenced record exists |
| 3 | Doc state transitions | State map: `{Drafted:[Complete,Void]}` |
| 4 | Downstream protection | DOWNSTREAM map check before undo |
| 5 | Sequence numbering | MAX+1 (single-user = no collision) |
| 6 | Period closing | Check C_Period.IsActive on DOC_COMPLETE |
| 7 | Duplicate prevention | SELECT COUNT WHERE Value = ? before INSERT |
| 8 | Audit trail | kernel_ops logs everything (already done) |
| 9 | Undo boundary | compact() prunes past 2 sessions (already done) |
| 10 | Currency consistency | Store CurrencyISO in metadata |

~150 lines total for all 10. iDempiere uses ~15,000 lines for the same
invariants plus 200 others that single-user browser ERP doesn't need.

### §15.2 What we deliberately don't solve (yet)

- Multi-device sync → only if users need phone + laptop simultaneously
- Multi-currency conversion → only if users operate across currencies
- Full AD runtime loading → compiled manifest replaces it
- Server-side locking → single-user browser makes it unnecessary

---

## §16. Database Coverage and Storage Economics

### §16.1 Current coverage of iDempiere schema

ad_seed.db contains 356 of iDempiere's 1,003 table definitions (35%).

| Category | Missing | Examples |
|---|---|---|
| AD_ (system/admin) | 160 | Alert, Auth, Processor, Archive, ChangeLog |
| Views + translations (_V, _Trl) | 41 | Report views, multi-language overlays |
| I_ (import) | 18 | Data migration staging tables |
| GL_ (accounting) | 4 | GL_Distribution, GL_Fund, GL_FundRestriction |
| C_ (commercial) | 120 | Commission, Dunning, RfQ, Subscription, POS |
| M_ (material) | 65 | Production, QualityTest, CostQueue, LotCtl |
| Other (workflow, HR, asset, mfg) | 239 | A_Asset, PP_*, HR_*, W_* |

The missing 647 tables fall into two groups:
- **Server-only plumbing** (~400): processors, schedulers, auth, import,
  views, translations. These have no function in a browser runtime.
- **Advanced business modules** (~250): manufacturing, commissions, dunning,
  subscriptions, RfQ, quality control. These serve enterprises with
  dedicated ERP administrators.

The 356 present tables include all AD metadata (10 tables, 59K rows) and
all GardenWorld business data (C_BPartner, M_Product, C_Order, C_Invoice,
C_Payment, M_InOut, etc.).

### §16.2 Storage comparison: PostgreSQL vs SQLite

Measured from GardenWorld seed data:

| Metric | PostgreSQL | SQLite (ad_seed.db) |
|---|---|---|
| Full GardenWorld install | ~350MB | 12.1MB |
| Compression ratio | — | **29x smaller** |
| Total rows | ~84K | 84,134 |
| Bytes per row | ~500-800 | 151 |
| AD metadata (70% of rows) | ~250MB | ~8.4MB |
| Business data only (30%) | ~100MB | ~3.6MB |

PostgreSQL overhead comes from: WAL (write-ahead log), TOAST (large
object storage), MVCC (multi-version concurrency control — stores
multiple row versions for concurrent transactions), per-row transaction
IDs, system catalogs, and index structures. All required for a multi-user
server. None needed in a single-user browser.

SQLite stores page-aligned data with no concurrency overhead. One file,
no fragmentation, no transaction versioning.

### §16.3 Browser storage limits

IndexedDB quotas (where the SQLite file is cached):

| Browser | Default | With `navigator.storage.persist()` |
|---|---|---|
| Chrome | 60% of device disk | Same, marked non-evictable |
| Firefox | 50% of device disk | Same, marked non-evictable |
| Safari/iOS | 1GB per origin | Up to 20% of disk on request |

A medium business with 100K documents would produce ~50MB in SQLite.
A 256GB phone allows ~150GB in IndexedDB. Storage is not the constraint.

Transfer time is the constraint: 12MB on 3G = ~30 seconds. This is why
the architecture uses lazy fetch — load only the tables the user drills
into. The manifest (2KB) + initbubble.json (2KB) + a single table
fetch (~3KB for 18 partners) = under 10KB for first interaction.

### §16.4 Metadata JSON queryability

The `metadata TEXT` column on core tables stores domain-specific fields
as JSON. SQLite's `json_extract()` is slower than real column access.

Measured bounds:
- `json_extract` over 5,000 rows: <10ms desktop, <30ms mobile
- Over 84K rows (full ad_seed.db): <50ms
- Acceptable for interactive drill; insufficient for batch reporting

Mitigation: fields that become frequent query targets are promoted to
real columns via `ALTER TABLE ADD COLUMN` (SQLite supports this without
table rebuild). The JSON column is a staging area for schemaless fields,
not the permanent home for high-frequency analytics.

### §16.5 Manifest override mechanism

The compiled manifest (§13) is the base configuration. Two override
paths exist for runtime customisation without recompilation:

1. **Direct JSON edit** — manifest.json is human-readable. Change a
   field's mandatory flag, reorder tabs, add a field. Reload the page.
   No compiler, no server, no PostgreSQL.

2. **Settings JSON (localStorage)** — the S282 settings template stores
   user preferences that patch the manifest at load time: hidden fields,
   reordered tabs, custom labels, bubble order. The manifest is the
   default; localStorage overrides are the personalisation layer.

The compiler (`scripts/compile_manifest.js`) is for cold-start generation
from the AD source. It is not required for day-to-day configuration.

### §16.6 Relationship between legacy tables and 5-table model

The 5 core tables (§12) and the legacy iDempiere tables coexist in the
same SQLite database. They serve different roles:

| Concern | Legacy tables (C_*, M_*) | 5-table model |
|---|---|---|
| Role | Imported reference data | Runtime working data |
| Schema | Fixed columns per table | Generic + metadata JSON |
| Source | PostgreSQL export (ad_seed.db) | Created by doc_engine.js |
| Growth | Static (GardenWorld demo) | Grows with user activity |
| Example | C_BPartner has 30+ columns | items has 8 columns + metadata JSON |

The compiled manifest maps each window to its backing table — whether
that table is `C_BPartner` (legacy) or `items` (5-table). The accordion
and kernel_ops work identically with both.

When a feature requires a table not in the 5-table model (e.g.,
C_Commission for sales commissions), the lazy fetch retrieves that
table's schema from the manifest and its data from the server or
ad_seed.db. The 5-table runtime does not grow unless needed.

Tables that exist in both models (e.g., partners exist in C_BPartner
AND could be stored in items) are not migrated. Legacy data stays in
legacy tables. New data created by the user goes to the 5-table model.
The manifest tells the UI which table to query for each window.

---

## §17. The Seismic Claim (Revised)

§10 claimed: the AD running in a browser. That was the first act.

The second act is larger: **an ERP engine in 5 tables, with business
logic as pure functions, undo/redo via event sourcing, and a spatial
constellation UI that replaces the 1990s menu tree.**

No JVM. No PostgreSQL. No OSGi. No server. No 2-week training course.
Open a URL. See your business as a constellation of glowing entities.
Tap one. Drill. Edit. Done.

The 13MB Application Dictionary — 20 years of iDempiere community
wisdom — compiled down to a 2KB manifest that tells the browser what
to render. The full AD stays as the source code, available for reference
and recompilation. But the runtime is hand baggage.

150 lines of kernel enforcement. 5 tables. One write path (commitOp).
One audit trail (kernel_ops). One accounting proof (journal).

This is not iDempiere in a browser. This is what iDempiere would be
if it were designed in 2026 for the device everyone actually carries.

---

## §18. The Unified Model — BOM + WfMC on One Log (empirically validated)

This section is not theory. Every claim below was tested against the WHOLE
iDempiere dictionary (1003 tables, 8,957 FK columns) by
`scripts/test_bom_theory.js` (in the bim-compiler repo). The §BOM_TEST log is
the witness. Re-run it to reproduce.

### §18.1 Every relationship is one of two edges

Strip the labels off iDempiere's schema and only two relationship kinds remain:

1. **Structure edge** — recursive parent→children (the BOM). Two flavours:
   - **Containment** ("has-a"): `Building→Floors`, `Order→OrderLines`,
     `Window→Tabs→Fields`. The parent *holds* the child.
   - **Derivation** ("made-from, by a verb"): `Order→Invoice→Shipment→Payment`.
     The child is *computed from* the source document by a DocAction (see §18.3).
2. **Component citation** — a leaf names independently-existing master/reference
   data by quantity (`OrderLine→Product`, `Invoice→Partner`). Cited, not composed.

This is the BOM principle (`docs/BOMBasedCompilation.md`) applied to business
documents. **There is one compiler.** A building is a BOM; a purchase order is a
BOM; the AD window hierarchy is a BOM. BIM and ERP are the same recursion over
different leaves. The op-log (`kernel_ops`) is the BOM's composition history.

### §18.2 What the data said (witness: §BOM_TEST, 2026-05-29)

| Test | Result | Verdict |
|---|---|---|
| T1 resolution | 3,465 / 5,231 domain FKs resolve (66%) via `_ID` convention | conclusions robust on the resolved sample |
| T2 acyclicity | TWO cycles found | naive "structure = DAG" **falsified — and refined** |
| T5 hubs | `C_Order` in-degree **23** (#1), then Invoice 20, Payment 17, RMA 12, PP_Order 10 | source-replication **CONFIRMED** |

The two T2 cycles are not refutations — they forced two true distinctions:

- **Derivation cycle `Order→Payment→Invoice→Order`** = the **three-way match**.
  Resolved by *direction*: forward **lineage** (Invoice derived-from Order) is a
  DAG; the closing edge is a **settlement back-reference** — a third relation, the
  reconciliation citation. This is how a source document stays bound to its replicas.
- **Containment cycle `OrderLine↔RequisitionLine`** = **component reuse**. A part
  appears in two BOMs (Order, Requisition). **Per-context, containment is always a
  DAG** (tab levels strictly increase). Reuse across assemblies is the BOM
  superpower, not a cycle. Containment must be compiled *per-window*, never as a
  global tree.

**The refined rule:** containment is per-context; derivation is causal-forward;
settlement back-references close reconciliation loops. Nothing fell outside the model.

### §18.3 A document is a *lifecycle*, not a table

Witness §BOM_TEST: 51 tables carry **both** `DocStatus` and `DocAction`; 52
`C_DocType` configurations exist. The definitive split is **lifecycle vs none**:

> **Document** = `DocType` (configures) + `DocAction` (drives — the verbs) +
> `DocStatus` (records) + `kernel_ops` (audits). **Master data** has none — it is
> a lifeless citation.

- **DocAction *is* the verb.** The 14 actions — `PR` Prepare, `CO` Complete,
  `AP` Approve, `RJ` Reject, `WC` Wait-Complete, `VO` Void, `CL` Close,
  `RE` Re-activate, `RC` Reverse-Correct, `RA` Reverse-Accrual, `PO` Post,
  `IN` Invalidate, `XL` Unlock — are the operators that advance state **and fire
  derivations**. `Complete` on `C_Order` is what produces the 23 replicas T5 measured.
- **`C_DocType` is the parameterization** — "one source, many purposes." Same
  `C_Order` table; DocType selects which sequence, which downstream docs, which
  GL treatment.

So the *behavioural* surface of all 1003 tables is tiny: **~51 documents × 52
DocTypes × 14 verbs.** The bloat is parts on a shelf; the behaviour is small and
WfMC-shaped.

### §18.4 WfMC from the ground up — and it collapses into one log

| WfMC reference element | Compiled artifact |
|---|---|
| Process definition | `C_DocType` (+ optional `AD_Workflow`) |
| States / activities | `DocStatus` values |
| Transitions | the 14 `DocAction` verbs |
| Worklist | the **gravity globe** — what needs action, glowing |
| Audit trail | **`kernel_ops`** |

`kernel_ops` is therefore *simultaneously*: WfMC execution history, sync/replication
substrate (§ multi-node), undo/time-machine, and gravity source (§14 already weights
`DOC_COMPLETE`=2.0, `DOC_VOID`=1.5). **One append-only log, replayed, is the whole
engine.** The workflow engine and the replication engine were never separate.

**Correction this forces:** the manifest's `state_machine` must be compiled
**per `C_DocType`** (from the DocAction set + transition rules), NOT hardcoded per
table — because one `C_Order` table behaves differently by DocType. The current
`compile_manifest.js` §4 hardcoding is a placeholder, replaced in the build plan.

### §18.5 Local-first mesh — no one node holds the whole

Each user is a local-first node owning their ~10% working set; the org ERP is the
**union of nodes**, synced user-to-user by merging op-logs (Git-style DAG: parent
pointers, fetch-fills-gaps, deterministic replay). "Heavy mode" is the mesh, not a
monolith. Integrity goes eventually-consistent and conflicts cluster at **document
handoffs** (the derivation seams) — the same few places where global invariants
(single-invoice-per-order, cross-user budget) live. Those few get *detect-and-reconcile*
or a *designated owner node*; everything else is enforced locally. Causality follows
the **lineage spine** (compile-time taggable: FK to a lifecycle table = lineage;
else citation), not the full FK tangle.

**Build order is therefore hub-first** — `C_Order → C_Invoice → C_Payment → M_RMA
→ PP_Order` — because the derivation hubs are where usage, money, WfMC richness, and
gravity all concentrate. See `prompts/ERP_KERNEL_BUILD.md` for the phased plan.

### §18.6 Scaffold vs business logic — the handler registry

The state machine is **not** where the complexity lives. The bloat-and-hell is the
**business logic**: pre-conditions, side effects, post-conditions, and conditional
fan-out (`Order→Invoice only if IsInvoice`). Every hellish rule attaches to exactly
one **cell**: `(DocType, currentStatus, action)`.

**The scaffold contains the hell; it does not remove it.**
`DocType + DocAction + DocStatus + kernel_ops` is scaffold — it guarantees you only
call `completeInvoice()` on a valid Invoice, never on a voided Order. The mechanism
is **dispatch by cell**:

- **State machine** — which cells are *reachable* (legal transitions). Compiled data.
- **Handler registry** — the *behavior at a cell*: `handler[(DocType, action)] =
  fn(doc, ctx) → ops[]`. Contains all the bespoke logic. A handler may invoke other
  handlers → composed workflows. **Each handler touches only its own DocType**, so
  the bloat scattered across 1003 tables is partitioned into isolated, nameable units.
- **kernel_ops** — every effect a handler produces is an op. **A side effect that
  bypasses the log is a violation, and it is detectable.** Handlers *return* ops; the
  kernel *applies* them (this is the path to B1: kernel owns the write). That makes
  every handler a pure, testable function and the log the single source of truth.

**Why this tames the 15,000 lines:**
- The hell becomes **visible** (one cell, one handler), **testable** (given doc+ctx,
  assert emitted ops), **replayable/undoable/auditable** (it's all ops).
- Handlers are written **hot-first**, ranked by gravity (§14). Most cells are never
  exercised — so most handlers are never written. The product compiles its own
  business-logic backlog from observed usage. The 90% nobody uses costs nothing.

**Summary for code:**
- State machine = *allowed transitions* (compiled — build P2).
- Handler registry = *business logic* (per-cell handlers — build phase, gravity-ranked).
- Log = *single source of truth* (already `kernel_ops`).

### §18.7 The mailbox and the rulebook — out-of-band, content-addressed, never a server brain

**The engine lives where the user lives.** Every peer runs the same deterministic
kernel on its local SQLite; there is no server-side engine. The server, if any, is a
**pure relay** — "an S3 bucket with auth" — storing op-logs and snapshots for peers
to push/pull (Git-remote style). It never applies, merges, or arbitrates. Removing
the central brain does not delete the hard problems; it **relocates** them — into the
op-log protocol and into *peer roles*, never back into the server:

- **Visibility/auth** — the relay still needs *addressed* inboxes (per-org/per-user)
  and keys. It reads the envelope address; it never opens the letter.
- **Global invariants** (single-invoice-per-order, cross-user budget) — moved to a
  **designated peer role** (the AP node owns invoice issuance; others request) or
  detect-and-reconcile at merge. Coordination is a *user with a hat*, never a server.
- **Hints** ("hot ops you may want") — computed from op *metadata* (table, type, ts,
  user_tag) only, never by applying the op; or dropped entirely in favour of peer
  gossip. Encrypt op bodies → the relay sees only routing headers.
- **Version skew is THE hard problem** — each PWA self-updates, so ops from mixed
  kernel/handler versions coexist with no central migration. Defence: every op carries
  `kernel_version`; handlers stay deterministic AND backward-compatible.

**Rules are two layers, not one:**
- **Handler *code*** (imperative `completeOrder()` logic) ships *with* the PWA
  (service worker), versioned with the kernel. **Never** hot-swapped as remote code.
- **Policy *data*** (declarative flags: `(Order,Complete):{creditCheck:false}`) is a
  **signed, content-addressed manifest** (`policy_vN.json`, Git/IPFS) the admin
  publishes; handlers *read* it at write time. Hot-swappable, governable, auditable.

**Two append-only logs, one link.** The event log (`kernel_ops`) and the policy/config
log (content-addressed, signed) are stitched by a `policy_hash` stamped on every op —
not needed for replay (effects are frozen, §18.8) but required for AUDIT: *"why did
this complete with no credit check?"* → the op cites `policy_v2`. Local rule overrides
must be **marked** so un-synced policy can't silently pollute shared history; admin-key
trust (who publishes, rotation/compromise) is an org-root-key / designated-peer concern.

### §18.8 Doc is the Event — the document-event as the atomic unit

A document is **not** a row that events happen *to*. Its lifecycle **is** the event
stream: PO #5 *is* `created → completed → invoiced → paid`. The row (current
`DocStatus`) is the fold; the `DocAction`s are the events.

"The transaction" is the giveaway: the **document-event is the atomicity boundary.**
When `Complete` fires, the handler returns a *group* of effect-ops (status change +
invoice + lines + journal). That group is the single unit of four things at once:

- **Atomic** — commits or rolls back together (the transaction).
- **Undoable** — undo a document-event undoes the whole group, never a stray op.
- **Syncable** — the causal unit that ships and replays together.
- **Auditable** — stamped with `policy_hash` + `kernel_version`.

This is the unit that makes the op-log coherent rather than a flat stream — and it is
exactly P3b's handler contract (`handler → ops[]`). In the 5-table model, each
`documents` row **is** the event-carrying aggregate root.

### §18.9 Aggregates are checkpointed projections (precise rule)

Not "no mutable aggregates." The authoritative state is the log; aggregates
(`StorageOnHand`, `OrderLine.Price`) are **rebuildable projections**, never sources of
truth. But a naive JOIN-over-all-history is O(history) and won't scale — so they MAY
be **materialised as checkpoints**: `StorageOnHand = checkpoint_at_cursor_N +
Σ(mutations since N)`. The kernel treats them as a cache the log can always
regenerate. This is the same snapshot+delta needed for mesh compaction/bootstrap.

### §18.10 The oracle: iDempiere's model classes validate handlers (extract, don't port)

We have the golden reference — iDempiere's 20-year Java source. Use it to **validate**
that each JS handler implements the same business rules, **never to port blindly**.
This is EXTRACT-don't-invent applied to behaviour (the §18.6 "hell").

- **Per `(DocType, action)`, the Java method is the spec.** Before writing
  `completeOrder()`, open `org.compiere.model.MOrder.completeIt()`; copy its
  pre-conditions, side effects and post-conditions as the handler's pre-flight
  citation (`// Oracle: MOrder.completeIt() — checks: isProcessed, credit, …`).
  Each check → one test fixture (a test names the rule it proves).
- **Diff-oracle (Docker) — the strongest test.** Run the same transaction in a real
  iDempiere (`docker start postgres`), dump the affected rows, run the JS handler on
  the same input, compare. **The schemas differ** (real tables vs 5-table) — so
  compare at the **semantic/op level, normalised through `ad_table_map`** (a created
  `C_Invoice` ⇄ `documents[INVOICE]` + lines), or compare the effect-ops both sides
  emit. Mismatch = bug OR a *documented* intentional divergence.
- **Gravity-ordered.** Extract oracle rules HOT-CELL-FIRST — `MOrder.completeIt()`
  before the long tail — the same backlog as the handler registry (§18.6, §14).
- **The oracle grounds the policy schema (§18.7).** The flags (`creditCheck`,
  `autoCreateShipment`) are *read off* what `completeIt()` conditionally does; the
  `Order→Invoice iff IsInvoice` fan-out (§BOM_TEST T3) is literally a branch in the Java.
- **Scope the diff to the document-event (§18.8)** — compare the documents/lines/journal
  the transaction produced (the atomic op-group), not transient state. iDempiere posts
  accounting asynchronously; our `journal` is synchronous — normalise that.
- **Don't copy:** concurrency locking, server round-trips, OSGi/plugins, the framework.
  The ~150-line kernel + per-cell handlers replace ~15,000 lines.

---

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
