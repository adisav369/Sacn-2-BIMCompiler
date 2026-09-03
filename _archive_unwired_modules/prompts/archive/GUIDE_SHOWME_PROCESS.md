# ⚠ DO NOT REMOVE — Scope guard
# Scope: SEPARATION-OF-CONCERNS PROOF — port the two matured overlays (help_overlay.js = Guide,
#        crud_overlay.js = CRUD+Process) to a SECOND surface, glassbowl_gravity.html (Gravity), with
#        NO engine change — only a per-surface keyed store + walk. Porting them to Gravity proves the
#        overlay layer is the unit of separation; BIM is then a short curve (a third keyed store + walk).
# DONE + LIVE (do NOT redo): GP1 coach vocab · GP2 Next-gate · GP3 Process▶ real signed write (sidecar
#        log, read-the-tip) · E2E full loop · sticky ring — all on glassbowl.html, sw glassbowl-offline-v7.
# Spec-first; witness-led (each task names the issue it proves); §-log first — save run output to a log,
#        READ the log before any conclusion (exit code is NOT evidence). EXPLICIT GO before any deploy.
# Read FIRST: prompts/READSHOWME_DYNAMIC_SPEC.md §"The Guide is a surface-agnostic overlay" (the
#        through-line) + §guide-vocabulary + §Next-gated + §error-path. These ARE the design contract.

---

# Overlays → Gravity (then BIM) — push-on prompt (handoff, 2026-06-01)

## DONE + LIVE on glassbowl.html (do NOT redo) — red1oon.github.io/BIMCompiler/glassbowl.html, sw v7
Latest glassbowl commit `c5dbbfc2` on `full`. All §-witnessed (logs under `build/erp/`) + browser-proven.
- **GP1** (`help_overlay.js`): pure COACH core (`coachPlan`/`legalNext`/`isVeer`) + node export; ShowMe
  drives `reveal`+`pulse`(key-addressed bus)+`highlight:statusbar`, **asserts 0**; veer→`suspend`
  (NeedHelp on, no tag, `resume`). CRUD bus ends: `overlay:action` emit + `pulse` receiver → reveal+pulse
  ▶ (never auto-fire). help_ops O2C steps `kind:process`. **W-HELP-COACH 21/21**.
- **GP2** (`help_overlay.js`): pure `nextGate` (CO→advance / IP→hold / exception→errorReport / none→hold);
  `tryNext` reads live `#docStatusBar` (+`#crudModeCk` edit-gate, both by id) → gates Next; read-mode
  bypass for the retrospective trace. **W-HELP-NEXTGATE 11/11**.
- **GP3** (`crud_overlay.js` + `kernel_ops.js` copy + `glassbowl.html`): DECIDED seam = **sidecar log,
  read-the-tip**. Process▶ commits a REAL signed `SET_STATUS` op (`commitOp→sealChain→verifyChain`) to a
  separate `kernel_ops` DB under IDB key `glassbowl_kernel_ops`; `glassbowl_data.db` stays the IMMUTABLE
  baseline; docstatus = latest non-undone op's `to` per (table,id); **1GB-safe** (log is O(actions), the
  baseline is never re-exported per commit). Kernel = canonical `bim-ootb/viewer/kernel_ops.js` verbatim.
  CORE `kernelParamsFor`/`readTip`; `__crud.history`/`kernelDb`/`readTip`. **W-CRUD-WRITELOOP-OVERLAY 12/12**
  + browser smoke (`drive_glassbowl.js`, pageerror=0, `§CRUD process committed op_uuid=… to=CO verifyChain=ok`).
- **E2E** (`scripts/drive_help_process_e2e.js`, real browser): gate-hold → ShowMe drives coach + reveals ▶
  → real signed commit verifyChain=ok → bar s-CO + read-the-tip CO → Next gate reads CO and ADVANCES →
  off-path action suspends. The units COMPOSE via the bus + shared DOM; no glue gap. **W-GUIDE-PROCESS-E2E 14/14**.
- **Sticky ring** (`crud_overlay.js`, sw v6→v7): removed hover-leave auto-close — the ring of fire STAYS
  once revealed (small fabs don't vanish; the Guide's pulse-reveal persists). Dismiss is explicit only:
  pick a verb / open another bubble / pointerdown outside. **W-RING-STICKY 5/5** (`scripts/drive_ring_sticky.js`).

## History — ALREADY EXISTS (correction, do NOT rebuild)
The **Dossier → History tab** (`renderHistoryTab`) shows the real op-log (`G.oplog`) most-recent-first with
a `↶` **read-only reversal PREVIEW** (className toggle only — the documented read-only undo seam to T3).
There is ALSO **W-VIEWLOG** (read-only undo/redo over VIEWS) and the **RECENT stack** ("the engine kept N
runs in the op-log"). History is NOT a missing UI. Only follow-ons (NOT this push, T3-gated): (a) merge the
LIVE GP3 sidecar ops (`__crud.history()`) into the Dossier History tab so new signed Process writes show
alongside `G.oplog`; (b) un-park `↶` to a REAL reverse (`kernel.undoOp`). **T3 PARKED** by design.

## NEXT (next session) — PORT BOTH OVERLAYS TO GRAVITY (the separation-of-concerns proof)
R: "porting the 2 overlays to Gravity will be proof that separation of concerns works; then a short curve
to do BIM." The overlay ENGINE does not change — only a per-surface keyed store + walk (READSHOWME
§surface-agnostic / §third-consumer: a gravity cell already carries its table id). `glassbowl_gravity.html`
exists (hand-authored, shares Layout/toast `SYNC-POINT`s with glassbowl; already references `kernel_ops`)
but does NOT yet load the overlays.

Bounded tasks (in order; each names its witness; nothing deploys without EXPLICIT GO):
### PV1 — Mount both overlays on glassbowl_gravity.html, keyed to gravity bubbles
- Add `<script>` `help_overlay.js` + `kernel_ops.js` + `crud_overlay.js` (same order as glassbowl). Confirm
  they attach BY KEY to the gravity-ranked bubbles. NO engine edit — a missing hook is a gravity-page
  window-expose task (the page must expose its `idx/N/project/px/py/k/radius` + `setFocus`-equivalents), NOT
  an overlay change. HONEST BOUNDARY: the overlays can only drive what the surface already exposes as drivable.
- **Witness:** `scripts/drive_gravity_overlays.js` (puppeteer) — both layers mount (§HELP/§CRUD layer mounted),
  NeedHelp→badges on gravity bubbles, Edit-mode→rings, pageerror=0.
### PV2 — Gravity keyed store + walk; Process▶ hits the SAME sidecar seam
- Decide: reuse the SAME `help_ops.json`/`crud_ops.json` keyed by table id (gravity bubbles carry table
  ids), or a gravity-specific walk. Prove a Process▶ on a gravity bubble commits to the SAME signed sidecar.
- **Witness:** extend the drive — Process▶ on a gravity bubble → `§CRUD process committed verifyChain=ok`;
  the Guide pulses ▶ + gates Next on the gravity surface (SAME engine, different walk).
### PV3 — Confirm separation: ONE overlay source, TWO surfaces, no fork
- diff-check `help_overlay.js`/`crud_overlay.js`/`kernel_ops.js` are byte-identical across glassbowl + gravity
  (one source, copied — same discipline as single-DB / one `whitebox_regression.js`). Document the SYNC-POINT.

## THEN — BIM (the short curve)
Same three ingredients (READSHOWME §ERP-today→BIM-later): (a) a BIM keyed store, (b) a BIM declarative walk,
(c) BIM drive-surfaces window-exposed (`flyTo`/`isolate`/`setStoreyVisible`). A different sequence script keyed
to BIM element ids; NO engine change. Out of scope until PV1–PV3 prove the model on Gravity.

## Parallel reuse target — PILLS → ERP (same separation, other direction; R, 2026-06-01)
R: "likewise porting the pilled icons back to ERP will be smooth." The PillBuilder/pill-registry framework
([[project_s281_pill_registry]]) is the SAME layered move applied to a different layer: register erp.html's
affordances as pills (order/icon/handler) instead of hand-wiring. Smooth because the registry is already the
unit of separation. Separate track from the overlay port; recorded here as the confirming reuse. Spec when picked up.

## Other open (not blocking, not this push)
- `error_reporter.js` factor-out → `bim-ootb/viewer/erp/` (precondition for the REAL exception path; the
  canned guide step + best-effort call already wired). READSHOWME §class-placement.
- GP4 **ProcessBatch** (the named next O2C extension) — same engine, a batch walk. After the Gravity port.

## Discipline (carry every task)
- Spec-first; pre-flight cite the spec. Deterministic / non-invent — extract or compile, never fabricate.
- §-log first; save logs under `build/erp/`; READ before concluding.
- Deploy = Glassbowl-way (copy `build/erp/{glassbowl.html,glassbowl_gravity.html,help_overlay.js,kernel_ops.js,
  crud_overlay.js,crud_ops.json,help_ops.json,sw.js,glassbowl_data.db,sqljs/}`→`docs/`, **BUMP sw
  CACHE_VERSION** (now v7), commit `full`, `mkdocs gh-deploy --force`, origin=BIMCompiler), **EXPLICIT GO
  only**; then fetch-back-verify the live URL.
- Subagents are WRITE-BLOCKED in this harness — read-only investigation only; the main session does the writes.
