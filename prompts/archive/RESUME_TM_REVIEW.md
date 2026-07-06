# RESUME — review the Time Machine workings (next session)

# ⚠ DO NOT REMOVE
**Scope (user, 2026-06-24):** "review further the Time Machine workings." Broad review of how the viewer's
Time Machine actually works — not (yet) a feature. Read the code + exercise it; surface findings before
changing anything. **Standing rules:** read the run log after every run; whitebox §-log first; edit
shipping code in `~/bim-ootb/viewer/` via a `/tmp/wt-*` worktree off fresh `origin/main`; Spec-First for
any change. Honour until the review is reported.

## Where the Time Machine lives + what to review
- **`viewer/time_machine.js`** (~3.5k lines) — the whole 4D playback engine + panel. Review entry points:
  - **Panel build** (~line 1835): the toolbar buttons — `tm-share` · `tm-sun` · `tm-eye` (drone) ·
    `tm-gantt` (📊 the inline read-only playback gantt drawer, `#tm-gantt-box` canvas) · `tm-author` (✎
    Author wizard) · `tm-whatif` (⑂ what-if) · **`tm-editor` (↗ Editor → opens `schedule_editor.html`,
    shipped this lane)** · `tm-dash` · `tm-var` · the big DAY/HR counter · `tm-close`. Handlers ~line 1933+.
  - **`_cap` overlay** (the IIFE that reads dated leaf `tasks` + maps `guidTask`, overlays the real task
    window onto covered elements; uncovered → generative). This is the playback's schedule source.
  - **Playback transport** (start/end/rev/play, the slider `configSlider`, `renderAtTime`/`renderAtCursor`,
    `anchorFromCursor`), the DAY/HR/MIN modes, ghost-glass animation (`ghostglass.js`, `4D_PLAY`).
  - **The `bim_4d` BroadcastChannel listener** (main.js S240) — `4D_PING/PLAY/RESET/RESOURCES/HIGHLIGHT/
    SCHEDULE_RESPONSE` AND the **`4D_SCHED_EDIT` consumer** added this lane (replays a Schedule-Editor op
    on `APP.db` + re-folds the TM via `toggleTimeMachine()` off→on). Worth confirming end-to-end live.
  - **S-curve / shopfloor fold** (`_loadShopfloor`, PP_Order + PP_Order_Cost) and the dashboard/variance
    (`tm-dash`/`tm-var`).
- **Adjacent:** `viewer/schedule_author.js` (the authoring + WBS/deps/CPM/move engine — §SE arc), `viewer/
  schedule_author_ui.js` (✎ wizard), `viewer/whatif.js` + `whatif_panel.js` (⑂), `viewer/schedule_editor*.js`
  + `schedule_sync.js` (the new-tab editor + live sync), `viewer/import_db_builder.js` (the IFC-native 4D
  tables DDL the TM reads).

## What "review" should produce
A short written map: what each TM control does, where the schedule data comes from (`_cap` vs rule-
generated vs authored vs captured/imported), the playback math, the cross-surface messages, and any
gaps/bugs/confusions worth fixing. Exercise it live (headless boot is available — see the §SE smokes for
the pattern: serve a worktree with a real building, open TM, drive the buttons). Then propose next steps;
do NOT change behaviour mid-review without flagging it.

## Context carried in (this session's lane, all LIVE)
The **§SCHEDULE-EDITOR arc is complete + live** (FUSED_4D5D_WEDGE_LANE — see its ★ REVIEW CARD): WBS +
deps + CPM + drag-Gantt + live cross-surface sync, reachable from the TM's new **↗ Editor** button
(bim-ootb PR #503-506, #508; sw up to v716). Also live: the **Find → ERP "open ↗" existing-order link**
(PR #512, sw v720 — see FIND_OPENLINK_EXISTING_ORDERLINE.md). ERPUserGuide §"Schedule Editor" is published.
Nothing is committed-but-unpushed.
