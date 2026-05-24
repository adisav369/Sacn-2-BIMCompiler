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

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
