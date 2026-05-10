# KERNEL_OPS_ROADMAP: Transactional Design Intent Log

# ⚠ DO NOT REMOVE — Read the log after every run. Scope: deploy/dev/ ONLY.

## Status: PLANNING — draft for future session

## What exists (2026-05-09)

- `kernel_ops.js` v4: commitOp, undoOp, redoOp, replayOps, compact, sessionStart
- DB table: `kernel_ops` (id, timestamp, op_type, parameters, input_guids, output_guid, undone)
- Op types in use: `GRID_MOVE`, `GRID_DETECT`, `VIEW_FILTER`, `SESSION_START`
- Undo/redo: Ctrl+Z/Y keyboard + bottom-right ↩↪ buttons (grid overlay mode)
- Compact: collapses consecutive same-label GRID_MOVE, prunes undone, trims to 2 sessions
- Wired in: grid_drag.js (GRID_MOVE on pointerup), grid_dims.js (GRID_DETECT on detection),
  grid_overlay.js (SESSION_START + compact on grid mode enter, ↩↪ buttons)

## What to encode next (Route Walk MEP)

### Phase 1 — MEP Route parameterization
Same elements get parameterized across multiple passes. kernel_ops tracks each:

| Op Type | Trigger | Parameters | Undo action |
|---------|---------|------------|-------------|
| `MEP_ROUTE` | Route Walk completes a path | route_id, segments[], device_guids[] | Remove route path |
| `MEP_DEVICE` | Device snapped to route node | guid, route_id, node_idx, device_type | Unplace device |
| `MEP_PARAM` | Device spec changed | guid, param_name, old_value, new_value | Restore old_value |

### Phase 2 — Transactional causality
When a GRID_MOVE shifts a bay, downstream ops that depended on that bay position are flagged:
- `input_guids` on MEP_ROUTE references wall/column GUIDs that define the bay
- If those GUIDs' positions change (GRID_MOVE), the route is "stale"
- UI indicator: stale routes shown in orange, user can re-walk or accept

### Phase 3 — Clash pair replay
| Op Type | Trigger | Parameters |
|---------|---------|------------|
| `CLASH_DETECT` | Clash analysis run | rule_set, pair_count, hard/soft counts |
| `CLASH_RESOLVE` | User marks clash resolved | clash_id, resolution (move/resize/accept) |

Undo a CLASH_RESOLVE → clash reappears in the panel with its original color pair.

## Design principles

1. **Only useful ops** — don't log view rotations, mouse moves, panel opens. Log state changes.
2. **Compact aggressively** — consecutive same-element ops collapse to final state.
3. **Two sessions max** — old history is audit, not undo. Prune on session start.
4. **input_guids = causality** — every op declares what it read. This is the dependency graph.
5. **parameters = reversible** — always store old + new so undo doesn't need a separate query.

## Smart Section Save — Gestural Multi-Band Capture

### The Gesture
User drags scissors slider through the building. Each **pause ≥ 1s** = a capture point.
No extra buttons mid-gesture. Save commits whatever was paused at.

```
drag ─── PAUSE 1s ─── drag ─── PAUSE 1s ─── PAUSE 1s ─── Save
           │                      │              │
        ceiling               furniture       openings
        Z=2.8m                Z=0.8m          Z=1.2m
```

### Dwell Detection Rules
1. **Velocity threshold**: pointer speed < 0.01m/s for ≥ `dwell_threshold_s` (default 1.0s) = dwell.
2. **Proximity dedup**: if new dwell is within `face_cluster_tol_m` (0.3m) of existing capture,
   replace — don't add. Same clustering logic as grid votes. Reuse, don't invent.
3. **Three-strike lock**: 3 dwells at the same band = convergence. Lock that point,
   stop accepting more in that band. User found it — system moves on.
4. **Gesture timeout**: total gesture window = `gesture_timeout_s` (default 5s from last movement).
   If slider is idle > 5s without Save, gesture resets.

### Use Cases
| Gesture | Result |
|---------|--------|
| Pause at ceiling only | Fans, sprinklers, lighting |
| Quick sweep floor→ceiling→pause at window level | Walls + doors + windows (standard floor plan) |
| Pause at lower slab, pause at main GF | Two-level GF — both slabs in one composite view |
| Three pauses at ~same Z | Single capture point, locked (user was hunting) |

### Data Model
```json
{
  "name": "GF Services",
  "dwell_points": [
    { "z": 2.80, "duration_s": 1.2, "locked": false },
    { "z": 0.80, "duration_s": 1.1, "locked": false },
    { "z": 1.20, "duration_s": 1.4, "locked": true }
  ],
  "gesture_time_s": 4.8
}
```

### Rendering — Painter's Algorithm by Dwell Order
Each dwell point queries its own element set via sectionCut.
**Dwell order = paint order** — NOT Z order. The user's gesture sequence IS the compositing stack.

```
for each dwellPoint in capture order:
  contours = sectionCut(db, libDb, dwellPoint.z)
  render contours — OVERWRITES previous layers where they overlap
```

Example — double-height hall with partial upper floor:
1. Dwell at lower floor → hall contours (base layer)
2. Dwell at upper semi-floor → rooms overwrite hall where they exist;
   hall shows through where upper floor is void (double-height space)
3. Dwell at ceiling → devices overwrite everything

The `dwell_points[]` array order in saved JSON is sacred — sorted by capture time,
not by Z. Restoring the section replays the paint in the same order.

Visual weight per layer (configurable):
- Latest dwell (top layer): solid contours, full opacity
- Earlier dwells: progressively lighter, thinner stroke
- First dwell (base): lightest / dashed — context, not focus

### UI: Save button + dwell limit
```
[ Save 💾 ] [3]     ← max dwell points (user setting, not live count)
```
- `[3]` is a preset — default 3, user taps to change, stays until changed
- Dwell detector stops capturing after this many confirmed stops
- Default 3 covers common case: floor + openings + ceiling
- `[1]` = single-cut mode (legacy behaviour)

### Config in grid_rules.json
```json
"smart_save": {
  "dwell_threshold_s": 1.0,
  "gesture_timeout_s": 5.0,
  "proximity_tol_m": 0.3,
  "max_dwells": 3,
  "lock_after_hits": 3
}
```

### Implementation
- `grid_scissors.js` — already gets `onSectionSliderChange` on every tick.
  Add: track `lastZ`, `lastTime`, `dwellPoints[]`, velocity computation.
- `grid_overlay.js` — Save button reads `dwellPoints[]` instead of `sectionPlane.constant`.
- `section_cut.js` — `sectionCut` already accepts `cutZ`. Call once per dwell point.
- `kernel_ops.js` — `SMART_SAVE` op type: stores the dwell_points array.

## Phase 4 — Grid Drag Complete Feature (Highlight, Cascade, BuildWalker, Variant)

### 4.1 Drag Highlight UX

**Red highlight** = wall at OLD position (ghost outline, stays visible until save).
**Green highlight** = wall at NEW position (live outline, shows proposed state).

Both are Line meshes in a `dragHighlightGroup`, disposed on save or cancel.
Wall contours + openings + balcony elements all move as a unit (they share the grid line's bay).

```
User long-presses a grid line → line turns red (dragStart)
User drags → green ghost appears at cursor pos, red stays at origin
User releases → green solidifies, red remains as "old" reference
User clicks Save on variant panel → red disappears, DB committed
```

Implementation:
- `grid_drag.js` already has `shadowGroup` — extend with red/green material distinction
- Red = `LineBasicMaterial({ color: 0xff4444 })` cloned from original wall contour
- Green = `LineBasicMaterial({ color: 0x44ff44 })` at new position
- Both get `userData = { isDragHighlight: true, isContour: true }` for cleanup

### 4.2 Tandem Movement (wall carries its furniture)

When a grid line moves, EVERYTHING on that side of the bay moves with it:
- The **wall contour** (section_cut geometry at cutZ)
- **Openings** in that wall (doors, windows — their contours and arcs)
- **Balconies** / external items attached to the wall face
- **Internal items** between old and new position get squeezed or gain space

This already works via `cascadeElements()` — it queries all elements in the affected bay
and shifts them by the delta. The contour re-renders after move because `renderContoursForView`
is called post-drag.

**Extending outward:** Dragging an exterior grid line forward:
- Wall + balcony + external elements move OUT
- Room behind gains depth
- Outside compound loses space (if boundary exists, flag as site boundary violation)
- kernel_ops logs: `{ direction: 'outward', bay_delta: +1.2, site_remaining: 42.3 }`

### 4.3 Internal Grid Line Drag (Room/Lounge Boundary)

Interior grid lines = partition walls between rooms. Dragging them:
- Room A shrinks, Room B grows (or vice versa)
- Furniture in the shrinking room may no longer fit → BuildWalker flags it
- The lounge/corridor boundary is just another internal grid line
- Door positions on that wall re-cascade (stay at same relative offset, or snap to clearance rule)

Implementation:
- Already supported by `cascadeElements` — no axis restriction (X or Y)
- Just needs the grid line to be detected as "internal" (between two structural bays)
- `grid_dims.js` `detectGridsAtPlane()` already classifies: structural (weight ≥ 3) vs infill
- Internal lines: weight < structural threshold → lighter visual weight, same drag behaviour

### 4.4 BuildWalker — Post-Drag Spatial Impact Analysis

After any grid drag (external or internal), **BuildWalker combs the affected bay**:

1. **Identify affected elements** — all GUIDs in the bay between old grid pos and new grid pos
2. **Compute new clearances** — each element's bbox vs its surrounding walls/boundaries
3. **Flag violations:**
   - Element bbox overlaps another → CRUSH (needs resize or remove)
   - Element-to-wall clearance < code minimum → SQUEEZE (needs smaller product)
   - Door swing arc hits new wall → BLOCK (needs rehang or resize)
   - Duct/pipe run truncated → REROUTE (needs MEP re-walk)
4. **Parameterize LOD** — for each flagged element, propose:
   - Swap to smaller product from library (query `component_library.db` for alternatives)
   - Remove (if no suitable alternative)
   - Accept (user override — "it fits tight but we allow it")

**Promotion back to DB:**
Each BuildWalker finding is written as a kernel_op:
```
op_type: 'BUILD_WALK_FINDING'
parameters: {
  trigger_op_id: <the GRID_MOVE op that caused this>,
  guid: <affected element>,
  finding: 'CRUSH' | 'SQUEEZE' | 'BLOCK' | 'REROUTE',
  current_clearance_m: 0.12,
  required_clearance_m: 0.60,
  proposed_action: 'SWAP',
  proposed_product_id: <from library>,
  proposed_dimensions: { w: 1200, d: 600, h: 750 }
}
```

This is a **proposed change** — not executed until user accepts on the variant panel.
Once accepted, it becomes a `BUILD_WALK_ACCEPT` op (or `BUILD_WALK_REJECT`).

**4D/5D variance integration:**
- BuildWalker findings feed into `cost_panel.js` as Δ items
- Swapped product → Δ Cost (price difference), Δ Time (procurement lead time)
- Rerouted duct → Δ Time (additional labour), Δ Qty (new fittings)
- Removed item → negative Δ Cost, negative Δ Qty

### 4.5 Variant Panel — Save + Info Icons

The variant panel (already exists in `cost_panel.js`) becomes the **single display layer** for:
- Grid drag diffs (which lines moved, how far)
- BuildWalker findings (crush/squeeze/block per element)
- Per-item info icons:
  - 📦 **Resource** — what product/material is affected
  - ⏱ **Time** — schedule impact (days added/removed)
  - 💲 **Cost** — unit rate × Δ volume/quantity

**Save button lives on the variant panel** (not floating, not in toolbar):
- Variant panel auto-opens after drag
- Shows diff summary: "3 elements repositioned, 1 needs resize, 0 clashes"
- Each row: element name | finding icon | resource icon | time icon | cost icon
- Save button at bottom: commits all GRID_MOVE + accepted BUILD_WALK findings
- Cancel button: reverts to pre-drag state (undo all pending ops)

```
┌─ Variant Panel ──────────────────────────────┐
│ Grid A3 moved +1.2m East                      │
│                                               │
│ Desk_1525x762  📦 swap 1200x600  ⏱+0d  💲-$45 │
│ Chair_Dining   📦 ok             ⏱ 0d  💲 $0  │
│ Door_IntSgl    📦 ok (swing ok)  ⏱ 0d  💲 $0  │
│ Duct_S1        📦 reroute +2.1m  ⏱+1d  💲+$80 │
│                                               │
│ Net: Δ Cost = +$35  Δ Time = +1 day           │
│                                               │
│        [ Cancel ]         [ Save ✓ ]          │
└───────────────────────────────────────────────┘
```

### 4.6 Why kernel_ops makes this efficient

Without kernel_ops, every drag would require:
1. Immediate DB writes (slow, irreversible)
2. Manual diff tracking (fragile, custom code per feature)
3. No causality chain (can't tell which move caused which clash)

With kernel_ops:
1. **Deferred commit** — GRID_MOVE is logged but DB positions only update on Save. Undo is instant (replay without the last op). No wasted DB writes during exploration.
2. **Causality via input_guids** — BUILD_WALK_FINDING references the GRID_MOVE's `output_guid`. If user undoes the move, all downstream findings auto-invalidate. No orphan findings.
3. **Compact history** — user drags same line 5 times exploring. kernel_ops compact() collapses to final position. BuildWalker only runs once on the final state.
4. **Replay on reload** — `replayOps('GRID_MOVE')` restores positions. `replayOps('BUILD_WALK_ACCEPT')` applies accepted swaps. Page reload = exact same state, no "unsaved changes" anxiety.
5. **Variance is free** — the op log IS the diff. `cost_panel.js` reads kernel_ops WHERE op_type IN ('GRID_MOVE','BUILD_WALK_ACCEPT') and computes Δ directly. No separate variance table.
6. **Session boundary** — compact prunes after 2 sessions. Old explorations don't bloat the DB. But accepted findings persist as `BUILD_WALK_ACCEPT` (never pruned — they're design decisions).

### 4.7 Implementation Order

| Step | What | Files | Status |
|------|------|-------|--------|
| D1 | Red/green highlight on drag | `grid_drag.js` | DONE — shows extent of movement |
| D2 | Internal grid line drag (room boundary) | `grid_drag.js`, `grid_dims.js` | PARTIAL — some elements follow, most don't |
| D3 | Variant panel auto-open after drag | `cost_panel.js`, `grid_drag.js` | BUG — only shows on undo/redo, not on drag release |
| D4 | BuildWalker clearance check | new `build_walker.js` | NOT STARTED |
| D5 | Library swap proposals | `build_walker.js`, `component_library.db` | NOT STARTED |
| D6 | kernel_ops integration (deferred commit) | `kernel_ops.js`, `grid_drag.js` | DONE — undo/redo working |
| D7 | 4D/5D variance from op log | `cost_panel.js` | BUG — variance only appears on undo/redo touch |
| D8 | Save/Cancel on variant panel | `cost_panel.js`, `grid_overlay.js` | NOT STARTED |

### 4.8 Field Observations (2026-05-11)

**Grid detection gaps:** Some buildings have grid lines that should appear but don't.
This is a `grid_dims.js` `detectGridsAtPlane()` issue — the wall clustering / vote
algorithm misses lines. Every session touching grid detection MUST add §-whitebox logs
proving where lines were found AND where they were expected but absent.

**Tandem movement incomplete (D2):** `cascadeElements()` moves some elements in the
affected bay but misses most. The contour re-render works, but the element query scope
is too narrow — likely only catches elements whose center is exactly on the grid line,
not elements whose bbox spans the bay. Fix: query by bay range, not grid line position.

**Variance auto-show (D3/D7):** `cost_panel.js` `refresh()` is wired to undo/redo
dispatch but NOT to the drag pointerup event. Fix: call `refresh()` from `grid_drag.js`
`_onDragEnd()` after the GRID_MOVE kernel_op is committed.

## Files

- `deploy/dev/kernel_ops.js` — core log engine (extend, don't replace)
- `deploy/dev/grid_drag.js` — GRID_MOVE commits (pattern to follow for MEP)
- `deploy/dev/grid_overlay.js` — ↩↪ buttons + dispatch (extend doUndo/doRedo for new op types)
- `deploy/dev/grid_scissors.js` — slider events, dwell detection (future: smart save)
- `deploy/dev/build_walker.js` — NEW: post-drag spatial impact analysis engine
- `deploy/dev/cost_panel.js` — variant display layer with info icons
