/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */

# ⚠ DO NOT REMOVE — Scope guard
# Scope: Globe UX triage — fix Properties/Data gateway behaviour,
#        collapse animation, and sub-constellation dimming in ad_graph.js.
# Read the log after every run. Exit code is not evidence.
# Spec-first: implement only what is described in a § section below.

---

# S259c — Globe UX Triage

## Context

S259 introduced Properties/Data gateway bubbles, collapse animation, and sibling
dimming. The intent was correct but the implementation has issues that need fixing.
All work is in `deploy/dev/ad_graph.js`.

---

## §1. Collapse Animation — Current Bug

**What should happen:**
When user taps to close a sub-constellation (toggle collapse), the children
should animate shrinking back to the parent centre (reverse of spawn), then
disappear. The parent and all sibling bubbles return to their NORMAL appearance
(full brightness, original size). No re-burst, no flicker.

**What actually happens:**
- Siblings remain dimmed after collapse completes
- Sometimes the parent re-expands immediately (re-burst)
- The `_activeExpandedNode` state is not reliably cleared

**Root cause:**
`_activeExpandedNode` can point to a gateway/child node (set during Data expand)
while collapse is triggered on the parent node. The clear logic was checking
`=== node` which missed the nested case. Current fix uses unconditional clear
but timing may still be off — the animation runs over 400ms but dim is cleared
immediately on `_collapseNode` call, before children finish animating.

**Fix spec:**
1. On `_collapseNode(node)` call: immediately set `_activeExpandedNode = null`
2. Children animate back to parent over 400ms (ease-in)
3. During animation: collapsing children fade out (reduce alpha from 1→0)
4. After animation completes: remove nodes from `_nodes` array
5. Parent node must NOT re-trigger expansion — `_gatewaysSpawned` must be reset
   to `false` so the record CAN be re-expanded on next tap, but doesn't auto-expand
6. Verify: after collapse, ALL remaining nodes render at full brightness (no dim)

**Test:** Expand a record → see children + dim → collapse → ALL nodes return to
full brightness within one frame of animation completing.

---

## §2. Properties/Data Gateway Bubbles — Current Bug + Correct Spec

**What was intended (user spec):**

When tapping a RECORD bubble (e.g. an AD_Column entity in System view, or a
C_BPartner in GardenWorld), two gateway bubbles spawn:

### §2.1 Properties Bubble (orange/red outer, yellow inner if content exists)

- **Purpose:** A clever filter entry point. NOT "open the record card".
- **What it does on tap:** Opens the SAME listing panel as Data, BUT pre-filtered
  to show only columns where this record has non-null content.
- **SQL equivalent:**
  ```sql
  SELECT Name, Value, {property_column}
  FROM AD_Column
  WHERE {property_column} IS NOT NULL
  ORDER BY {property_column}
  ```
- **Visual:** Red outer ring / yellow blend inner = "ripe" (has content).
  All grey = empty (no non-null properties worth showing).
- **Key insight:** Properties and Data lead to the SAME listing view, just
  Properties is a clever pre-filter (non-null content only). It gives visual
  comfort — user knows this record HAS interesting data before diving in.

### §2.2 Data Bubble (blue if FK children exist, grey if empty)

- **Purpose:** Standard sub-bubble for ALL AD items. Opens a listing of the
  record's fields with a smart search box.
- **What it does on tap:** Opens the listing panel showing ALL columns/fields
  of this record. Arrow scroll down through fields. Search box appears for
  intelligent search within the record's data.
- **Visual:** Blue = has data/relationships. Grey = empty table.
- **Key constraint:** Data children DO NOT drill further into sub-records on
  the globe. Tapping a Data child opens the record card — no further bubble
  expansion. This prevents visual mess.

### §2.3 What Both Lead To

Properties and Data both open the **same listing panel** (the accordion panel
from `S259_ACCORDION_PANEL.md`). The only difference:
- **Properties:** pre-filtered to non-null fields, sorted by that property
- **Data:** all fields, with search box for finding specific values

### §2.4 Example: Opening AD_Column Record "C_BPartner_ID"

```
[AD_Column: C_BPartner_ID]  ← RECORD bubble tapped
         │
    ┌────┴────┐
    ▼         ▼
[Properties] [Data]
  (12/26)     (all)
  non-null    + search

Tap Properties → Panel opens showing:
  Name        | C_BPartner_ID
  ColumnName  | C_BPartner_ID
  AD_Reference_ID | 19
  FieldLength | 10
  IsMandatory | Y
  IsKey       | N
  ... (only non-null fields, sorted)

Tap Data → Panel opens showing:
  ALL 26 columns of this AD_Column record
  + search box at top: type "Reference" → jumps to AD_Reference_ID field
```

### §2.5 Example: Opening C_BPartner "Seed Farm Inc."

```
[C_BPartner: Seed Farm Inc.]  ← RECORD bubble tapped
         │
    ┌────┴────┐
    ▼         ▼
[Properties] [Data]
  (8/12)      (48 FK)
  non-null    tables

Tap Properties → Panel: shows 8 non-null fields of Seed Farm
Tap Data → Panel: shows Order/Invoice/Payment tabs (FK children as tabs)
           Each tab = one FK table, fields across, rows of child records
           This is where Order → OrderLine drill happens (in the PANEL, not globe)
```

---

## §3. Sub-Constellation Dimming — Correct Behaviour

**What should happen:**
1. User taps RECORD → Properties + Data gateways appear
2. All OTHER sibling records dim to ~40% opacity
3. The tapped RECORD + its 2 gateways stay at 100%
4. User taps Data → FK children appear around the Data gateway
5. Now: Data gateway + its children = 100%, everything else = 40%
6. User taps empty space or collapses → ALL return to 100% immediately

**Current issue:**
- Step 6 fails: nodes stay dimmed after collapse
- The dimming state (`_activeExpandedNode`) outlives the expansion

**Fix:** `_activeExpandedNode` must be set ONLY when children are actively
displayed. Cleared on:
- `_collapseNode()` — immediately
- `_collapseAll()` — immediately
- `_goBack()` — immediately
- `_buildHomeNodes()` — on any view rebuild
- When last collapsing child finishes animation — redundant safety clear

---

## §4. Files to Modify

| File | What |
|------|------|
| `deploy/dev/ad_graph.js` | Fix `_collapseNode`, `_spawnGateways`, `_expandRecord`, `_drawNode` dimming logic |
| `deploy/dev/ad_ui.js` | Wire Properties/Data tap to open panel with correct filter mode |

---

## §5. Test Plan

| Test | What it proves |
|------|----------------|
| T1: Expand → collapse → check all nodes alpha = 1.0 | Dim clears on collapse |
| T2: Expand → expand Data → collapse parent → all alpha = 1.0 | Nested dim clears |
| T3: Properties tap → onDrill called with filter='properties' | Gateway passes filter mode |
| T4: Data tap → onDrill called with filter='data' | Gateway passes filter mode |
| T5: Data children tap → onDrill (no further expand) | No messy sub-drill |
| T6: Rapid expand/collapse cycling → no stuck dim | State machine is clean |
| T7: Gateway colours: orange if non-null, grey if empty | Visual correctness |
| T8: Gateway colours: blue if FK exists, grey if empty | Visual correctness |

---

## §6. What NOT to Do

- Do NOT allow Data child bubbles to spawn their own children on the globe
- Do NOT open different panels for Properties vs Data — same panel, different filter
- Do NOT leave any dim state after collapse — brightness must fully restore
- Do NOT re-burst parent node after collapse (no auto-re-expand)
- Do NOT remove the gateway concept — it gives visual comfort and avoids
  immediate information overload from 48 FK children appearing at once
