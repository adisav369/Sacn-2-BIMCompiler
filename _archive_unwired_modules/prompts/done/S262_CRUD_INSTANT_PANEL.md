/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */

# ⚠ DO NOT REMOVE — Scope guard
# Scope: CRUD inline editing + Instant Panel (drag-dismiss/reset) in accordion overlay.
# Read the log after every run. Exit code is not evidence.
# Spec-first: implement only what is described in a § section below.

---

# S262 — CRUD + Instant Panel in Table Overlay

## Context

S259c delivered the cascading drill accordion overlay — inline overlay (not window.open),
dynamic child tabs rebuilt per drilled PK, alternating bar tones, keyboard navigation (arrows,
Tab/Shift+Tab, Enter, Escape), auto-focus first row, scroll vs tap distinction. 107 tests.

The overlay is **display-only**. This session adds:
1. **CRUD** — inline cell editing with save-on-blur, +New, ×Delete, undo
2. **Instant Panel** — dismiss tabs by tapping title, Reset pops them back one by one via kernel_ops

All backend plumbing exists and must NOT be modified:
- `ADData.saveRecord(db, tableName, record, columns)` — INSERT/UPDATE (ad_data.js:62)
- `ADData.deleteRecord(db, tableName, keyCol, keyValue)` — DELETE (ad_data.js:106)
- `ADData.getNextId(db, tableName)` — next PK (ad_data.js:121)
- `KernelOps.commitOp(db, opType, params, inputGuids, outputGuid)` — audit (kernel_ops.js:48)
- `KernelOps.undoOp(db)` — undo last op (kernel_ops.js:90)
- `_getFieldsForTable(tableName)` — field metadata with isReadOnly, isMandatory, referenceType
- `_renderFieldValue(field, value, record)` — type-aware control (ad_ui.js:1803)

---

## §1. Inline Cell Editing (Header Grid)

**Currently:** `_openTableView` renders `<td>` with plain text via `_resolveDisplay()`.

**Change to:** Type-aware editable cells based on field metadata.

### §1.1 Field → Control Mapping

| referenceType | Control | Notes |
|---|---|---|
| string (10) | `<input type="text">` | |
| integer (11) | `<input type="number">` | step=1 |
| amount/number (12,22) | `<input type="number">` | step=0.01 |
| quantity (29) | `<input type="number">` | step=1 |
| date (15) | `<input type="date">` | |
| yesno (38) | `<button>` toggle Y/N | Tap toggles, auto-saves |
| list (17) | `<select>` | Options from AD_Ref_List |
| FK (19, 30) | `<span>` readonly | Show resolved name, not editable here |
| text (14) | `<input type="text">` | Truncated, double-tap for zoom edit |
| isReadOnly or isKey | `<span>` | Never editable |

### §1.2 Save on Blur

```
User edits cell → blur fires
  → read value from input
  → update record[colName] = newValue
  → ADData.saveRecord(_db, tableName, record, fields)
    → KernelOps.commitOp(db, 'AD_SAVE', {table, id, col, old, new})
  → show toast "Saved" (green, 1.5s fade)
  → if error, show toast "Save failed: {msg}" (red, 3s)
```

### §1.3 Mandatory Field Validation

- If field.isMandatory and value is empty/null on blur:
  - Cell gets `border-left: 3px solid #ff4444`
  - Toast: "Required: {fieldName}"
  - Do NOT save — wait for user to fill

### §1.4 Cell Styling

```css
/* Editable cell */
#table-overlay td input,
#table-overlay td select {
  background: transparent; border: none; color: #eee;
  font-size: 13px; padding: 0; width: 100%;
  border-bottom: 1px dashed rgba(108,159,255,0.2);
}
#table-overlay td input:focus,
#table-overlay td select:focus {
  border-bottom: 1px solid #6c9fff;
  outline: none;
}
```

---

## §2. Editable Child Tab Grids

Same pattern as §1. Child tab rows loaded by `openAcc()` get editable cells.
Store child records array so save can target the correct row.

---

## §3. CRUD Toolbar

Add to the overlay title bar (`.ti`), between record count and X close:

```
[BPartner Location] [16 records] [+ New] [× Del] [↺ Undo] [✕]
```

### §3.1 + New Button

1. `ADData.getNextId(_db, tableName)` → next PK
2. Build empty record: apply defaults from field.defaultValue metadata
3. `ADData.saveRecord()` → INSERT
4. Append new row to header grid
5. Focus first editable cell of new row
6. Toast "New record created"

### §3.2 × Delete Button

1. Require a selected row (`.sel` class)
2. Confirm: brief flash red on row (200ms)
3. `ADData.deleteRecord(_db, tableName, keyCol, pk)`
4. Remove row from DOM
5. Highlight next row (or previous if last)
6. Toast "Deleted"

### §3.3 ↺ Undo Button (+ Ctrl+Z + Android Back)

1. `KernelOps.undoOp(_db)` → marks most recent op as undone
2. If op was AD_SAVE: reload record from DB, refresh cell values
3. If op was AD_DELETE: re-insert (replay the record)
4. If op was TAB_DISMISS: slide tab back in (see §4)
5. Android back button: intercept `popstate` (already wired in ad_graph.js:1683)
6. Ctrl+Z on desktop: add to `_onKeyDown` handler

---

## §4. Instant Panel — Dismiss Tabs

### §4.1 Tap Tab Title = Collapse/Dismiss

- Tap a **closed** tab's title bar → dismiss it (slide out left, 200ms)
- `KernelOps.commitOp(_db, 'TAB_DISMISS', { table: ftName, fk: ftFk, pk: _selectedPk })`
- Tab removed from DOM; CSS nth-child alternating tones refresh
- Log: `§TAB_DISMISS table={name} pk={pk}`

### §4.2 Reset = Undo One Tab at a Time

- ↺ button in title bar (same as undo)
- If most recent op is TAB_DISMISS: restore that tab at original position
- Tab slides back in from left (200ms)
- Log: `§TAB_RESTORE table={name}`
- Long-press ↺ = restore ALL dismissed tabs at once

### §4.3 Constraint: Parent-Child Order

- Tabs can only be dismissed, not reordered
- Restored tabs return to their original position (by tableName sort order)
- A parent tab being open is NOT required for child tabs to show

---

## §5. Breadcrumb Trail

As user drills Header → child tab → next child:
```
[BPartner] > [Order] > [OrderLine]
```

- Max 3 visible segments; earlier ones collapse to `…`
- Tap any segment → jump back to that drill level (data intact)
- Displayed above the accordion tabs, below the title bar
- Compact: 12px font, subtle separator

---

## §6. Double-Tap Cell = Zoom Edit

- Double-tap a cell → expands into a mini overlay card:
  - Field name (bold), help text (from AD_Field.Description), validation rules
  - Larger input matching field type
  - Escape or tap outside = collapse back, save on collapse
- Useful on mobile where table cells are tiny

---

## §7. Hold-and-Peek FK

- Long-press (>500ms) an FK cell → tooltip bubble showing referenced record's key fields
- E.g., long-press "Seed Farm" in C_BPartner_ID column → shows Name, Value, Group, Phone
- Release = dismiss bubble
- No navigation away

---

## §8. Files to Modify

| File | What |
|------|------|
| `deploy/dev/ad_ui.js` | `_openTableView`: editable cells, CRUD toolbar, dismiss/restore, breadcrumb |
| `deploy/dev/tests/test_s262_crud.js` | New: 20+ whitebox tests |
| `deploy/dev/erp.html` | Bump ?v= after deploy |
| `deploy/dev/sw.js` | Bump CACHE_VERSION after deploy |

**Do NOT modify:** `ad_data.js`, `kernel_ops.js`, `ad_parser.js`, `ad_graph.js`

---

## §9. Test Plan

| Test | What it proves |
|------|----------------|
| T1: String field renders `<input type="text">` | §1.1 type-aware rendering |
| T2: ReadOnly field renders `<span>` | §1.1 respects isReadOnly |
| T3: Number field renders `<input type="number">` | §1.1 number type |
| T4: Mandatory empty field has red border | §1.3 validation display |
| T5: ADData.saveRecord called on blur | §1.2 save-on-blur |
| T6: KernelOps logs AD_SAVE with old/new values | §1.2 audit trail |
| T7: +New creates record with getNextId | §3.1 create path |
| T8: ×Delete removes record + logs AD_DELETE | §3.2 delete path |
| T9: Undo reverts last AD_SAVE | §3.3 undo save |
| T10: TAB_DISMISS logged on tap-dismiss | §4.1 dismiss audit |
| T11: Reset undoes TAB_DISMISS, tab slides back | §4.2 restore |
| T12: Breadcrumb shows drill path segments | §5 navigation |
| T13: Child tab cells are editable | §2 child CRUD |
| T14: FK cell renders readonly resolved name | §1.1 FK handling |
| T15: Existing 107 tests pass (no regression) | Safety net |

---

## §10. What NOT to Do

- Do NOT modify ad_data.js, kernel_ops.js, ad_parser.js — all plumbing exists
- Do NOT add FK picker dropdown yet — that's a separate session
- Do NOT add CRDT sync — that's R6 in roadmap
- Do NOT add OPFS persistence — that's R5
- Do NOT use window.open for any panel — inline overlay only (mobile popup blocker)
- Do NOT break existing keyboard navigation (arrows, Tab, Enter, Escape)
- Do NOT remove the scroll vs tap distinction (pointerdown distance check)

---

## §11. Demo Scenario

1. Globe → long-press "Products" → table overlay opens, 55 records
2. Arrow down to "Oak Table" → Enter → drill: header reduces, child tabs appear
3. Tap "Product Price" tab → opens with prices
4. Tap a price cell → edit from 200.00 to 250.00 → blur → §AD_SAVE toast
5. Ctrl+Z → price reverts to 200.00 → §UNDO toast
6. Tap "Accounting" tab title (closed) → dismissed, slides out
7. Tap ↺ → "Accounting" slides back in
8. Tap + New → new row appears → type product name → blur → saved
9. Tap × Del on the new row → row removed → §AD_DELETE

---

## §12. Known Issues to Fix First

- **Empty tabs still appearing** — `_openTableView` filters globally (`SELECT COUNT(*)`)
  but should also filter per-PK in `drillRecord`. Verify the `drillRecord` path checks
  `WHERE fk = pk` not just `SELECT COUNT(*) FROM table LIMIT 1`.
- **SW cache mismatch** — erp.html must register `sw.js?v={CACHE_VERSION}` matching
  sw.js's actual CACHE_VERSION. Mismatch = mobile serves stale JS.
