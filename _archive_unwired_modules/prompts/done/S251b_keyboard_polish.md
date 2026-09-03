# ⚠ DO NOT REMOVE — S251b Keyboard Polish + 2D Panel Fixes
# Scope: scene.js, panels.js, tools.js, grid_overlay.js, grid_scissors.js
# Read the log after every run. Exit code is not evidence.

---

## S251b — Open Issues from S251 Session (2026-05-10)

### Session Context
S251 implemented: key sequence engine, command palette (?/🛟), Tab panel focus with
ListKeyNav, multi-select storeys/disciplines/clashes, Esc closes panels, mutual
exclusion (2D↔Clash/Measure), clash multi-select with red shine-through spheres.

Deployed to ootb-dev. SW v295. 197 tests (90 logic + 107 wiring). All pass.
Commits: 04cd8e46 → 6676b586 (8 commits on `full` branch).

---

## BUG-1: Dwell Flash Not Working (3 cuts snap)

**Symptom:** Scissors slider sweep should detect pauses (dwells) and flash the screen
white at each capture. The flash and red dwell markers don't appear.

**Root cause investigation needed:**
- `grid_scissors.js` has `dwellTrack()` (line 55) and `flashDwellCapture()` (line 199)
- Check if `onSectionSliderChange` is wired to call `dwellTrack()`
- Check if `grid_scissors.js` is loaded before `grid_overlay.js` calls `GridScissors.init()`
- Read `§SMART_SAVE` log lines in browser console while dragging slider
- Verify `dwellCheckTimer` is set and running (setInterval)

**Files:** `grid_scissors.js`, `grid_overlay.js`

---

## BUG-2: No Save Button in 2D + Section Cut

**Symptom:** When in 2D grid mode and scissors/section is ON, the "Save cut" button
does not appear on the section slider panel.

**Root cause:** `tools.js` line 66-84 creates the Save button inside `toggleSection()`.
But in 2D mode, scissors is activated via `grid_scissors.js` → `onOff()` (line 494),
which does NOT go through `toggleSection()`. So the Save button is never created.

**Fix:** Either:
1. Make `grid_scissors.js` call `toggleSection()` when activating, OR
2. Move Save button creation to a shared function called from both paths, OR
3. Have `grid_overlay.js` inject the Save button when scissors activates in 2D mode

**Files:** `tools.js`, `grid_scissors.js`, `grid_overlay.js`

---

## BUG-3: Zombie Saved Sections — PARTIALLY FIXED

**Status:** `_noauto` flag added to suppress `autoCreateCards()` after user deletes all.
But need to verify: does deleting individual cards (not all) still work correctly?
Does saving a new card after deletion clear the `_noauto` flag properly?

**Test:** Delete one card (not all) → re-enter 2D → card should stay deleted, other should remain.
Delete all cards → re-enter 2D → no cards should appear.
Save new card after deleting all → delete it → re-enter → auto-create should now work again.

---

## BUG-4: Grid Panel Arrow+Enter — GF/L1 buttons don't activate

**Symptom:** Tab to grid panel works, arrows highlight GF/L1/Roof buttons, but Enter
doesn't click them (or clicks the wrong thing).

**Root cause:** `buildPanel()` is called multiple times, destroying and recreating buttons.
The `_gridNav` `getItems()` returns fresh DOM nodes each call so cursor refs are valid,
but the `onActivate` callback captures `gridPanel` which may be stale.

**Investigation:** Add `§` log in grid `onActivate` to see which item index and label
is received. Check if `items[idx].click()` actually fires the view button's onclick.

**Files:** `grid_overlay.js`

---

## BUG-5: Clash List Re-focus on Return to Matrix

**Symptom:** After viewing a clash list, pressing Esc goes back to matrix. Clicking
another matrix cell opens a new clash list. Arrow keys don't work in the new list
until Tab cycles back to it.

**Status:** Watcher re-arms on new list (fixed). But auto-focus may not trigger because
the `_clashListWatcher` setInterval checks `A._clashListDiv !== _lastClashList` — if
measure.js removes and re-creates the div, the ref changes and it SHOULD register.
Need `§` log verification.

**Files:** `scene.js` (clash watcher), `measure.js` (list creation)

---

## POLISH: Esc Behaviour Standardization

| Panel | Current Esc | Expected |
|---|---|---|
| Section slider | ✓ closes (closeFn = toggleSection) | Verify after fix |
| Sunglasses slider | ✓ closes (closeFn = toggleSunglass) | Verify after fix |
| Grid (2D) | ✓ exits 2D (closeFn = toggleGridOverlay) | OK |
| Clash matrix | ✓ closes | OK |
| Clash list | ✓ closes, pops to matrix | OK |
| Storey/DISC | ✓ blurs only (no closeFn) | OK |
| Toolbar | ✓ blurs only | OK |

Verify section + sunglasses Esc work now that static panel init is in place.

---

## POLISH: Voice Commands Prompt

`prompts/S252_voice_commands.md` — written but not implemented. Mobile users
can tap 🎤, speak a command, see filtered list, say "down/up/yes" to navigate
and confirm. Same commands as keyboard palette. Separate session.

---

## What's Deployed (SW v295 — ootb-dev)

| File | Version | Key changes |
|---|---|---|
| `scene.js` | v=8 | Sequence engine, command palette, panel focus, clash multi-select |
| `panels.js` | v=8 | ListKeyNav, multi-select storey/disc, static panel init |
| `grid_overlay.js` | v=31 | Plan Grid title, auto-focus, Esc exits 2D, zombie fix, card system |
| `index.html` | v=8 | Sunglasses ×, Z ⊥, 🛟→palette |
| `sw.js` | v295 | Cache version |
| `SYSNOVA/index_dev.html` | — | Documentation link (BIM_Designer_Browser) |

---

## Test Suites

| Suite | Tests | What it covers |
|---|---|---|
| `test_s251_keyboard.js` | 107 | Wiring: source file string checks for all §tags, functions, shortcuts |
| `test_s251_logic.js` | 90 | Logic: actual code execution with mock DOM — arrows, range, toggle, slider, typeahead, sequence engine, panel cycle, focus stack |
| `audit_specs.js` | 40 specs, 407 tests, 1007 expects | Full Playwright audit |

---

## Implementation Steps (next session)

1. Read browser console `§` logs while testing each BUG — don't guess from code
2. Fix BUG-1 (dwell flash) — check `dwellTrack` wiring
3. Fix BUG-2 (Save button in 2D) — share button creation between toggleSection and scissors
4. Verify BUG-3 (zombie) — test all 3 scenarios
5. Fix BUG-4 (grid Enter) — add §log, check stale ref
6. Verify BUG-5 (clash re-focus) — check § watcher logs
7. **Update test_s251_logic.js** — add tests for every fix:
   - Dwell tracker: mock slider ticks → verify dwell detection fires
   - Save button: verify created in both toggleSection AND scissors-in-2D paths
   - Zombie: simulate delete all → autoCreateCards returns without creating
   - Zombie: simulate save after delete → _noauto cleared
   - Grid onActivate: verify click fires on correct DOM item after rebuild
   - Clash re-focus: verify panel unregister + re-register cycle
8. **Update test_s251_keyboard.js** — add wiring checks:
   - `dwellTrack` exists in grid_scissors.js
   - `flashDwellCapture` exists in grid_scissors.js
   - `section-save-cut-btn` creation in tools.js
   - `_noauto` flag in grid_overlay.js
   - `§SMART_SAVE` log tag in grid_scissors.js
9. Run all 3 test suites — read logs, not exit codes
10. Deploy to ootb-dev, verify with curl
