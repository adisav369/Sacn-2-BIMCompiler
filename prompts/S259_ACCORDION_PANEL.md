/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */

# ⚠ DO NOT REMOVE — Scope guard
# Scope: Accordion Grid Record Panel — replaces current card-style record view
#        in ad_ui.js with a spreadsheet-accordion hybrid layout.
# Read the log after every run. Exit code is not evidence.
# Spec-first: implement only what is described in a § section below.

---

# S259b — Accordion Grid Record Panel

## Context

The current record panel in `ad_ui.js` renders AD fields as a vertical card with
tabs across the top. This works but is not visually impressive for power users
who need to scan multiple fields quickly.

**Replace with:** An accordion where each tab is a full-width horizontal row.
Clicking a tab expands it to reveal fields arranged as COLUMNS (horizontal),
with data rows expanding downward. Only one tab is expanded at a time.

## §1. Layout Structure — Cascading Drill

Each panel opens in a **new browser tab** (multi-screen, drag/arrange).
No scrollbars — natural mobile touch drag up/down.

### §1.0 Cascading Drill Rule

- ONE tab open at a time. Full-width horizontal grid below it.
- **Tap a closed tab** → that tab opens, current open tab CLOSES.
- **Tap a row in the open grid** → current tab CLOSES, NEXT tab below OPENS
  (filtered to selected row's FK). This is the cascade follow-through.
- **Tap title bar** → Header re-opens, all below close. Reset to top.
- Drag up/down = natural page scroll through stacked tabs. No scrollbar needed.
- When too many tabs or rows → drag brings them into view.

### §1.0.1 Flow Example

```
STATE 1: Header open (user sees all records or one record)
  [Header]  ← OPEN: grid showing Name | Value | Group | ...
  [Lines 27]  ← closed, waiting
  [Accounting 3]  ← closed
  [Contacts 5]  ← closed

USER TAPS "Joe Block" row in Header grid:
  → Header CLOSES
  → Lines OPENS (filtered: WHERE C_BPartner_ID = Joe's ID)

STATE 2: Lines open
  [Header]  ← closed
  [Lines]  ← OPEN: grid showing Product | Qty | Price | Amount | ...
  [Accounting 3]  ← closed, waiting (next in line)
  [Contacts 5]  ← closed

USER TAPS "Oak Table" row in Lines grid:
  → Lines CLOSES
  → Accounting OPENS (filtered to Oak Table's FK)

STATE 3: Accounting open
  [Header]  ← closed
  [Lines]  ← closed
  [Accounting]  ← OPEN: grid showing Account | Debit | Credit | ...
  [Contacts 5]  ← closed, waiting
```

---

## §1-OLD. Layout Structure (reference — superseded by §1.0)

```
┌──────────────────────────────────────────────────────────────┐
│ Business Partner: JOE BLOCK                                  │  ← §1.1 Big title
├──────────────────────────────────────────────────────────────┤
│ ▼ Header                                                     │  ← §1.2 Expanded tab
├──────────┬──────────┬──────────┬──────────┬──────────── →    │
│ Name     │ Value    │ Group    │ Customer │ Vendor  │ ...    │  ← §1.3 Fields ACROSS
├──────────┼──────────┼──────────┼──────────┼─────────┤        │
│ Joe Blk  │ JB-001   │ Standard │ Y        │ N       │ ...   │  ← §1.4 Data rows
│          │          │          │          │         │        │
├──────────────────────────────────────────────────────────────┤
│ ► Lines                                                      │  ← §1.5 Collapsed tab
├──────────────────────────────────────────────────────────────┤
│ ► Accounting                                                 │  ← collapsed
├───���─────────────────────────────────────────────────────���────┤
│ ► Contacts                                                   │  ← collapsed
└──────────────────────────────────────────────────────────────┘
```

### §1.1 Big Title

- First line: `{WindowName}: {Record.Name || Record.DocumentNo}`
- Bold, 18px, full width
- Derived from Header tab's first Identifier field (AD_Column.IsIdentifier = 'Y')

### §1.2 Tab Row (Accordion)

- Each AD_Tab becomes a full-width row
- Collapsed: `► Tab Name` (14px, subtle border bottom, 44px height for touch)
- Expanded: `▼ Tab Name` (14px bold, reveals grid below)
- Click = expand this tab, collapse the previously expanded one
- Animation: expand slides down (200ms ease), collapse slides up

### §1.3 Fields as Columns (Horizontal)

When a tab expands, its AD_Fields become **column headers**:
- Each field = one column, ordered by AD_Field.SeqNo
- Column width: auto (min 80px, max 200px)
- Horizontal scroll if fields exceed viewport width (← → arrow keys)
- Only show fields where `IsDisplayed = 'Y'`
- Column header: field Name (not ColumnName)

### §1.4 Data Rows

For the Header tab (TabLevel=0): **one row** = the current record.
For child tabs (TabLevel>0, e.g. Lines): **multiple rows** = child records.

- Max visible rows before scrolling: 5 (avoid pushing tabs offscreen)
- Arrow ↑↓ scrolls rows, ←→ scrolls columns
- Active cell highlighted (subtle blue border)
- Empty/null cells show `—` in grey

### §1.5 Collapsed Tabs

- Show tab name + record count badge (e.g. `► Lines (27)`)
- Count fetched lazily: `SELECT COUNT(*) FROM {tab.TableName} WHERE {parentFK} = {recordId}`
- Grey text until expanded

---

## §2. Keyboard Navigation

| Key | Action |
|-----|--------|
| ← → | Scroll columns into frame |
| ↑ ↓ | Scroll rows (within expanded tab) |
| Tab | Move to next tab (expand it) |
| Shift+Tab | Move to previous tab |
| Enter | On a cell: edit mode (future). On a collapsed tab: expand it |
| Escape | Collapse current tab, return to globe |

---

## §3. Animation

| Transition | Duration | Easing |
|------------|----------|--------|
| Tab expand | 200ms | ease-out |
| Tab collapse | 150ms | ease-in |
| Column scroll | 100ms | linear |
| Row scroll | 100ms | linear |
| Panel open (from globe) | 300ms | ease-out (slide up from bottom) |
| Panel close (to globe) | 200ms | ease-in (slide down) |

---

## §4. Data Loading

### §4.1 Header Tab (Immediate)

- Record already in memory (from globe node.record)
- Fields from AD_Field WHERE AD_Tab_ID = header tab
- Display immediately on panel open

### §4.2 Child Tabs (Lazy)

- Count on first render: `SELECT COUNT(*)...`
- Full data on expand: `SELECT {fields} FROM {table} WHERE {FK} = ? ORDER BY {orderBy}`
- Cache result — don't re-query on re-expand within same session

### §4.3 Field Resolution

For FK fields (AD_Reference_ID = 19 or 30):
- Show resolved Name, not raw ID
- Use `ADData.resolveFK(db, columnName, value)` from ad_data.js

---

## §5. Integration with Globe

### §5.1 Open Panel

When `_onDrill(tableName, windowId, record)` fires (from globe CHILD tap or Properties gateway):
- Panel slides up from bottom, covers lower 60% of screen
- Globe stays visible in upper 40% (dimmed slightly)
- Globe continues to rotate/respond to pinch

### §5.2 Close Panel

- Tap globe area above panel → close panel
- Escape key → close panel
- Swipe down on panel → close
- Panel slides down, globe restores full brightness

### §5.3 Properties Gateway Filter

When opened from Properties gateway (§9 in ad_graph.js):
- Pre-filter: only show columns where value IS NOT NULL for this record
- Visual indicator: "Showing 12 of 26 fields (non-empty)"
- Toggle button: "Show All" to reveal full field set

---

## §6. Files to Modify

| File | Changes |
|------|---------|
| `ad_ui.js` | Replace `_renderRecord()` / `openWindow()` panel rendering with accordion grid |
| `erp.html` | Add CSS for accordion layout (grid, transitions, scrollbar) |
| `ad_data.js` | No changes (already has resolveFK, readRecords) |
| `ad_graph.js` | No changes (already fires _onDrill with correct params) |

---

## §7. Performance Targets

| Scenario | Target |
|----------|--------|
| Panel open (header fields) | < 50ms |
| Child tab count query | < 10ms |
| Child tab expand (30 rows) | < 100ms |
| Column scroll (50 fields) | 60fps |
| FK resolution (per cell) | < 5ms (cached) |

---

## §8. What NOT to Do

- Do NOT add a separate panel framework (no React, no Lit)
- Do NOT use `<table>` elements — use CSS Grid for column layout
- Do NOT load all child tab data on panel open — lazy load on expand
- Do NOT change the globe rendering — panel overlays it
- Do NOT add edit capability yet — read-only in this iteration
- Do NOT change the existing search or globe navigation
- Do NOT allow Data bubble children to drill further into sub-records (messy). They open card only.

## §8.1 Parent Context in Panel (Order → OrderLine relationship)

When a child record is opened (e.g. C_OrderLine), the panel MUST show the parent
context at the top. Layout:

```
┌──────────────────────────────────────────────────────────────┐
│ Order: 10001 (Completed)                                     │  ← parent title
├──────────────────────────────────────────────────────────────┤
│ ▼ Order Lines                                                │  ← child tab (auto-expanded)
├──────────┬──────────┬──────────┬──────────┬──────────── →    │
│ Line     │ Product  │ Qty      │ Price    │ Amount   │ ...   │
├──────────┼──────────┼──────────┼──────────┼─────────┤        │
│ 10       │ Oak Tbl  │ 5        │ 200.00   │ 1000.00 │ ...   │  ← filtered to FK
│ 20       │ Chair    │ 10       │ 80.00    │ 800.00  │ ...   │
├──────────────────────────────────────────────────────────────┤
│ ► Header (Order details)                                     │  ← parent tab available
└──────────────────────────────────────────────────────────────┘
```

**Rule:** When navigating from globe (Order → OrderLine via Data bubble), the panel
opens with:
1. Parent record Name/DocumentNo as the big title
2. Child tab auto-expanded showing filtered child records
3. Parent's own tab collapsed but available at bottom (click to see Order header fields)

This gives the Order → OrderLine drill-down that users expect, without messy
sub-sub-record bubbles on the globe. The globe shows relationships spatially;
the panel shows data in familiar tabular form.

---

## §9. Test Plan

New tests in `test_ad_ui.js` or extend existing:

| Section | Tests |
|---------|-------|
| A: Panel open | _onDrill fires → panel DOM appears, title shows record Name |
| B: Tab accordion | Click tab → expands, previous collapses, animation CSS active |
| C: Fields across | Header tab shows fields as columns, correct count |
| D: Child rows | Lines tab expand → correct row count from DB |
| E: Keyboard | ←→ scrolls columns, ↑↓ scrolls rows, Escape closes |
| F: Properties filter | Gateway filter shows only non-null fields |
| G: Performance | Panel open < 50ms, child expand < 100ms |

---

## §10. Demo Scenario

1. Globe shows C_BPartner entity (18 records)
2. Tap "Seed Farm Inc." → Properties + Data gateways appear
3. Tap Properties → accordion panel slides up
4. Title: "Business Partner: Seed Farm Inc."
5. Header tab expanded: Name | Value | Group | Customer | Vendor ... across
6. One data row: "Seed Farm Inc." | "SeedFarm" | "Standard" | "Y" | "N" ...
7. Click "► Customer Acct (2)" → expands, Header collapses
8. Customer Acct shows 2 rows with accounting fields across
9. ← → arrows scroll to see more fields
10. Escape → panel closes, globe resumes
