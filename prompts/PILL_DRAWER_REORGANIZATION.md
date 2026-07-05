# BUILD SPEC — FINALIZED 2026-07-05, ready to implement — real pill drawers

```
# ⚠ DO NOT REMOVE
SCOPE: DEPENDS on prompts/SCALE_AND_UX_SWEEP.md §3 item 6 (duplicate pill-registration audit) —
already re-verified clean for every id this spec touches (clash/bbox/screenshot/cam-reset/cam-pivot
are each registered exactly once, all already `pill:false`, confirmed via grep on bim-ootb main
2026-07-05 — no duplicate-id or duplicate-state landmine found for this spec's scope).
Everything below is CONFIRMED by the user across a live design dialogue 2026-07-05 (superseding
ALL earlier drafts of this file). Do not re-litigate; implement as written. Read the log after every run.
```

## FINAL DRAWER MAP (4 real drawers)

1. **Visual FX** — master = existing Palette icon (`id:'palette'`, unchanged, already correct
   click-only-opens behavior, keeps key `p`). Absorbs, no longer separate top-level icons:
   - Night (`id:'night'`, key `n`, moon icon) — fires directly, same as today, just relocated inside.
   - **Shadow becomes a single 4-state cycle button** (see §SHADOW-GROUND MERGE below) — replaces
     both the old boolean Shadow toggle AND the 4 separate Ground text-buttons (None/Grass/Earth/Paved).
   - Reverse-background-for-print (`id:'background'`, key `b`, `toggleBackground()`).
   - **Audio** (`id:'audio'`, key `v`, Sound FX) — CONFIRMED IN per user 2026-07-05 (an earlier
     resolution of this file wrongly said "leave standalone" — that was a misread of the user's
     annoyance at the *uncertainty*, not agreement to exclude it. Audio IS in Visual FX.)

2. **Camera/View** — NEW master icon = the Camera glyph, freed by deleting Screenshot (see
   §DELETIONS). Absorbs:
   - Feather/Fine precision-drag (`id:'precision'`) — becomes a sub-item, fires `togglePrecisionFine`
     on its own click only (no more dual-fire: today Feather's click ALSO fires Fine directly AND
     long-press reveals Reset/Pivot — that dual behavior does NOT carry over, per §MASTER-ICON BEHAVIOR).
   - Reset Camera (`id:'cam-reset'`, already `pill:false` today, was long-press-chip-only).
   - Pivot / Auto-Pivot (`id:'cam-pivot'`, already `pill:false` today, was long-press-chip-only).
   - No keyboard shortcut assigned to the new master yet — tap/click-only unless the user asks
     for one later. Sub-items keep their existing keys (Feather=Caps Lock) firing directly.

3. **Navigate** — NEW master icon = **Sailboat** (Lucide `sailboat`, confirmed by user — NOT Eye,
   NOT Mailbox, NOT Compass). Absorbs:
   - Find (`id:'find'`, key `f`)
   - World History (`id:'worldhist'`, key `w`) — its OWN internal tap/long-press behavior (overlay
     vs Z-timeline+bomb drawer) is UNCHANGED, leave exactly as-is per explicit user instruction.
   - Home (`id:'home'`)
   - Walk (`id:'walk'`, `platform:'mobile'` only) — moves into this group on mobile; still never
     appears on desktop.
   - No keyboard shortcut for the Sailboat master itself — tap/click-only.

4. **Inspect** — NEW master icon = **HardHat** (Lucide `hard-hat`). Absorbs:
   - Measure (`id:'measure'`, key `m`) — keeps its own long-press→Clash chip-reveal UNCHANGED
     (Clash is already `pill:false`, already hold-chip-only; just relocates wholesale as one row).
   - X-Ray (`id:'xray'`, key `Alt+Z`) — **icon changes from Eye to Bone** (frees Eye; Bone is a
     better metaphor anyway — X-ray reveals bones). Keeps its own long-press→Bbox(Alt+X) chip-reveal
     UNCHANGED, same reasoning as Measure/Clash — user explicitly said "leave AltZ/X alone."
   - Section Cut (`id:'section'`, key `x`)
   - Time Machine (`id:'tm'`, key `t`)
   - 4D/5D Report (`id:'report'`, key `4`)
   - Fly Tour (`id:'fly'`, key `l`) — user moved this in from its own slot.
   - No keyboard shortcut for the HardHat master — tap/click-only.

## NOT drawers — stay exactly as individual top-level icons (just visually grouped/ordered)
- **Document** (label only, no master, no behavior change): Save (`Ctrl+S`), Open (`Ctrl+O`).
  User's own reasoning: these are single-shot, high-frequency actions — burying them behind a
  drawer tap adds friction to the two most-used actions, unlike the mode-toggle items above.
- Share, Settings, Help — untouched, standalone, same as today.

## §MASTER-ICON BEHAVIOR (applies to ALL 4 drawers, genuinely new rule, unchanged from earlier draft)
A drawer's master icon **only opens/closes the drawer** — it never itself fires a feature, even if
the absorbed action it replaces used to fire on click (Feather is the concrete example: today its
click fires Fine directly AND long-press reveals Reset/Pivot; that dual behavior must NOT carry
over to Camera/View's master). Palette needs no behavior change (its click already just opens a
panel, no dual-fire problem there).

## §INTERACTION MODEL — no more long-press for the 4 drawer masters
CONFIRMED 2026-07-05: tap the master → drawer/panel opens and **stays open until an explicit '✕'
close** (not click-outside-dismiss, not long-press-then-auto-collapse). Reuse
`viewer/hba_mobile_stack.js`'s existing collapsed/expanded visual vocabulary for the open/close
animation, but the CLOSE trigger is the explicit ✕, per this spec (not hba_mobile_stack's tap-to-
collapse). This applies to the 4 master icons ONLY — it does NOT apply to the NESTED long-press
chip-reveals already living inside Measure (→Clash) and X-Ray (→Bbox), which the user explicitly
said to leave alone; those keep their existing long-press-chip behavior, just relocated as rows
inside the Inspect drawer. World History's own tap/long-press behavior is also explicitly UNCHANGED.

## §SHADOW-GROUND MERGE — new behavior, confirmed 2026-07-05
Today: Shadow (`tools.js:640`, `A.toggleShadow`, boolean `A._shadowOn`, renderer.shadowMap on/off)
and Ground material (`panels.js:307-334`, `_buildGroundButtons`, 4 separate text buttons None/Grass/
Earth/Paved, `A.setGroundTexture(key)`) are TWO INDEPENDENT pieces of state, in different files.
**NEW: merge into ONE 4-state cycle button** living on the Ground row inside the Visual FX panel:
- **OFF** = Ground=None + Shadow off (matches today's shadow-off + no-ground-texture default)
- **ON-1** = Ground=Grass + Shadow on
- **ON-2** = Ground=Earth + Shadow on
- **ON-3** = Ground=Paved + Shadow on
- Repeated presses of the (cloud-icon) button cycle OFF→ON-1→ON-2→ON-3→OFF.
- Also apply the user's separate visual-quality ask here: the button/swatch should show an actual
  small cut-out of the current ground texture/color (a real thumbnail), not an abstract icon or
  text label, "so user knows their color/texture right away instead of trying to process mentally."
- Moon (Night) can sit ordered before Shadow/Ground in the panel layout if that reads better
  spatially — labels are removed, order is a layout call, not a locked requirement.

## §DELETIONS — confirmed dead, remove entirely (not just hide further)
- `id:'screenshot'` (`panels.js:1193`, already `pill:false`, was a long-press-chip off Background)
  — redundant with the OS's native Print Screen. Delete the action AND its hold-chip wiring at
  `panels.js:1191`. This frees the Camera glyph for Camera/View's new master (see #2 above).
- `id:'record'` (`panels.js:1204`, already `pill:false`, key `r`) — no longer in use, delete.
- `id:'2d'` (`panels.js:1200`, already `pill:false`, key `2`) — no longer in use, delete.
- None of these three were ever visible on the top-level rail (`pill:false`), so their removal
  doesn't change the visible rail count — it's dead-code/dead-shortcut cleanup, not a rail change.

## §NEW ICONS — sourced from Lucide (the ONLY icon set this codebase uses, `panels.js:7`,
ISC-licensed, style-consistent: 24×24 viewBox, `fill:none`, `stroke:currentColor`, `stroke-width:2`).
Deliberately NOT Flaticon — different visual style (usually filled/colored) and the free tier
typically requires attribution this codebase carries nowhere today. All 3 pulled live from
`unpkg.com/lucide-static` 2026-07-05 and confirmed real (not memory-guessed):

```
bone:     { svg: '<path d="M17 10c.7-.7 1.69 0 2.5 0a2.5 2.5 0 1 0 0-5 .5.5 0 0 1-.5-.5 2.5 2.5 0 1 0-5 0c0 .81.7 1.8 0 2.5l-7 7c-.7.7-1.69 0-2.5 0a2.5 2.5 0 0 0 0 5c.28 0 .5.22.5.5a2.5 2.5 0 1 0 5 0c0-.81-.7-1.8 0-2.5Z" />' }
hardHat:  { svg: '<path d="M10 10V5a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1v5" /><path d="M14 6a6 6 0 0 1 6 6v3" /><path d="M4 15v-3a6 6 0 0 1 6-6" /><rect x="2" y="15" width="20" height="4" rx="1" />' }
sailboat: { svg: '<path d="M10 2v15" /><path d="M7 22a4 4 0 0 1-4-4 1 1 0 0 1 1-1h16a1 1 0 0 1 1 1 4 4 0 0 1-4 4z" /><path d="M9.159 2.46a1 1 0 0 1 1.521-.193l9.977 8.98A1 1 0 0 1 20 13H4a1 1 0 0 1-.824-1.567z" />' }
```
X-Ray's existing `eye` glyph is freed (no longer used anywhere) once X-Ray switches to `bone`.

## FINAL RAIL ORDER
Document(Save, Open) → Navigate(drawer) → Inspect(drawer) → Visual FX(drawer) → Camera/View(drawer)
→ Share → Settings, Help.

## ICON COUNT — the actual win
Today: 20 standalone top-level rail icons (desktop). After this spec: **9** — Save, Open, Share,
Settings, Help (5 unchanged) + 4 drawer masters (Palette, Camera, Sailboat, HardHat). Verify this
count live, don't just eyeball it (§DONE WHEN below).

## PARKED — explicitly OUT OF SCOPE for this spec, tracked as a separate future item
User floated (2026-07-05): expand Save to cover IFC/BCF/DB, expand Open to accept DB/IFC/BCF,
deprecate the Buildings-landing "Drop IFC" flow in favor of Open, and decide same-name-reimport
semantics (merge vs. variant). Checked the code: Save/Open today are DB-only
(`scene.js:508`/`scene.js:550`), IFC import is a fully separate path (`import.js`), and there is
NO existing same-name merge/variant logic anywhere (existing tests explicitly `deleteProject()`
first to avoid collision). This is a real architecture decision (touches the signed op-log/kernel
chain, `kernel_ops.js`, `§KRN_CHAIN` — merge vs. fork has real integrity implications) — deserves
its own spec pass, NOT a decision folded into this drawer reorg. Do not implement any part of it
here; do not silently pick merge-or-variant. Revisit only when the user opens it explicitly.

## STEPS
1. Work in a fresh `/tmp/wt-*` worktree off `bim-ootb` main (never edit `~/bim-ootb` directly —
   PreToolUse hook blocks it). `git -C ~/bim-ootb fetch origin && git worktree add /tmp/wt-pill-drawers
   -b lane/pill-drawers origin/main`.
2. Add `bone`/`hardHat`/`sailboat` to the `ICONS` registry in `viewer/panels.js` (paths above).
3. Build ONE reusable drawer-panel mechanism (new small module or extend `common/pill_builder.js`):
   master button → toggles a panel open/closed only (§MASTER-ICON BEHAVIOR), panel renders a list
   of the group's sub-actions each with its own real icon/`fn`/`isActive` (reuse as-is, don't
   reinvent state), explicit ✕ closes (§INTERACTION MODEL). Palette's EXISTING sunglass panel is
   the template to extend for Visual FX (it already opens/closes correctly) — Camera/View,
   Navigate, Inspect need this NEW mechanism since no panel exists for them yet.
4. Migrate Visual FX first (extend the existing Palette panel with Night/Shadow-Ground-merge/
   Reverse-bg/Audio rows) — most fully specified, reuses an existing open panel.
5. Build Camera/View, Navigate, Inspect panels + their new master icons, remove the absorbed
   actions' standalone rail registration (set `pill:false` or delete the top-level entry, keep the
   `fn`/`isActive`/keyboard-shortcut wiring intact).
6. Implement §SHADOW-GROUND MERGE (new cycle function, replaces `toggleShadow` + the 4 Ground
   buttons with one function + one visual-thumbnail button).
7. Implement §DELETIONS (screenshot, record, 2d — full removal, not just hiding).
8. Reorder `_actions` to §FINAL RAIL ORDER.
9. Live-verify (headless browser, real page load, real clicks — not just code reading):
   - Rail visible icon count = 9.
   - Each of the 4 masters: click opens, click again or ✕ closes, NEVER fires a sub-action itself.
   - Every absorbed sub-action still fires correctly and its `isActive` still reflects real state
     inside its new panel.
   - Every original keyboard shortcut (`h`→now the merged shadow-ground cycle, `n`, `p`, `v`, `m`,
     `c`, `Alt+Z`, `Alt+X`, `x`, `t`, `4`, `l`, `f`, `w`, Caps Lock, `a`, `q`, `Ctrl+S`, `Ctrl+O`,
     `/`) still fires its underlying function directly, drawer-open-state notwithstanding.
   - Measure→Clash and X-Ray→Bbox long-press chip-reveals still work, unchanged, now inside Inspect.
   - World History tap/long-press unchanged.
   - 0 console errors.
   - § log line per claim above — this is the evidence, not a screenshot alone.

## DONE WHEN
1. All 4 drawers live: Visual FX (Palette-hosted), Camera/View, Navigate (Sailboat), Inspect
   (HardHat) — each master click-only-opens, ✕-closes, no dual-fire.
2. Shadow-Ground merge live: one 4-state cycle button, real texture-swatch thumbnail, replaces the
   old boolean Shadow + 4 separate Ground buttons.
3. Screenshot/Record/2D fully deleted (code + hold-chip wiring), no dead references left.
4. Rail visible icon count verified = 9 (not eyeballed — counted from the live DOM).
5. Every keyboard shortcut verified still firing its function directly, live.
6. Document (Save/Open), Share, Settings, Help, World History, Measure→Clash, X-Ray→Bbox all
   confirmed UNCHANGED from today's behavior (regression check, not just "didn't touch the code").
7. § log evidence for every claim in STEPS §9, not screenshots alone.
8. Save/Open format-expansion idea explicitly NOT touched, left parked per the note above.

---

## ▶ 2026-07-06 — REOPENED: user not satisfied with landed behavior, 3 items to review/fix

**User's own framing:** what was asked and what landed don't fully match — review pill behavior properly,
don't just re-confirm it shipped.

### Item 1 — Master icon should DE-highlight when its panel is closed via ✕
**Real lead found (checked `origin/main`, not guessed):** `_buildMasterDrawer()`'s panel `onClose` handler
(`viewer/panels.js` ~line 1394) does **only** `console.log('§DRAWER_CLOSE id=' + masterId)` — it never
triggers a re-sync of the master pill's own highlight state. Compare to `toggle()`'s "opening" path, which
explicitly re-syncs sub-row states (`rows.forEach(r => r._sync())`) but has no equivalent for the MASTER
icon itself, on either open or close. Likely fix: find whatever function actually repaints pill highlight
state (probably in `common/pill_builder.js` or wherever `isActive()` gets periodically re-checked) and call
it from `onClose` too — right now, closing via ✕ is a different code path than clicking the pill itself, and
only the latter seems to trigger a repaint.

### Item 2 — Space bar to accept/trigger the Tab/Arrow-focused item — NOT YET BUILT, a real feature ask
**Checked:** no `tabindex`, no Space-bar handling, and no arrow-key navigation exists anywhere in the drawer
row code (`viewer/panels.js`) — the only Arrow-key handling found in the file is for an unrelated list-nav
feature (cursor movement in some other list, not the drawers). This is a genuine NEW keyboard-accessibility
feature, not a bug — Tab-into-a-panel, Arrow-through-its-rows, and Space/Enter-to-activate the focused row
would need real building (tabindex on rows, a keydown handler per open drawer panel, visual focus ring).
Confirm with the user this is wanted as a real feature (not just this one question) before building — scope
it as its own small piece, verify no conflict with existing single-key shortcuts (`h`/`n`/`f`/etc. currently
fire directly without needing focus).

### Item 3 — Shadow+Ground cloud-icon cycle "not working at all"
**Real candidate causes found (not fixed, needs live browser confirmation — don't guess which one, verify):**
1. `_buildShadowGroundRow()`'s `cloudBtn` click handler (`panels.js` ~line 1351) does
   `if (act && act.fn) act.fn(); else if (typeof window.toggleShadow === 'function') window.toggleShadow();`
   — but `toggleShadow` is ONLY ever assigned as `A.toggleShadow` (`tools.js` ~line 674), **never** as
   `window.toggleShadow`. If `_actionById('shadow')` (`act`) is ever null/stale at click time for any reason
   (registration-order issue), the fallback silently does nothing — no error, matching "not working at all."
   Check first: log `act` at click time, confirm it's non-null and `act.fn` really calls `A.toggleShadow`.
2. `A.setGroundTexture` (`tools.js` ~line 155) is called at the end of every `toggleShadow()` cycle, but its
   own body is async (`A._loadGroundConfig().then(A._applyGroundTexture)`) — if `_loadGroundConfig()` ever
   rejects or hangs (e.g. `ground_config.json` fetch failure), the cycle-state (`_shadowGroundKey`) and the
   swatch border (`_paint()`, synchronous) would still update correctly while the ACTUAL 3D ground texture
   never visually changes — which would look exactly like "selection not working" even though the button
   itself is firing. Check the console for `§GROUND_MAP load FAIL` or a `_loadGroundConfig` rejection.
3. Confirm `A.ground` (the ground mesh) actually exists for whatever building was tested — some extractions
   may not have one.
**Live-test order:** open devtools console, click the cloud icon once, check for `§SHADOW_GROUND cycle=...`
and `§SHADOW_GROUND_SWATCH key=...` log lines (both should fire if `toggleShadow` ran at all) — their
presence/absence immediately narrows which of the 3 candidates above is the real cause.

## WATCHDOG NOTE
This spec had THREE rounds of user correction during design dialogue (2026-07-05): (1) the original
mapping was incomplete, (2) Camera/View's grouping and Palette's exact members shifted, (3) Audio's
Visual-FX membership was wrongly resolved as "excluded" at one point when the user always meant it
included, and Navigate/Inspect went from "just ordering labels" to "real drawers" mid-dialogue.
**Implement exactly what's written above — it is the reconciled, final state — do not re-derive
from any earlier draft, an earlier commit of this file, or assumptions about what "usually" goes
together.** If anything above seems to conflict with the live code once you start, that's a
signal to re-check with the user, not to silently pick the reading that seems more sensible.
