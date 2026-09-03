# ⚠ DO NOT REMOVE — RESUME CARD: detail-events in Z + the session multiverse (W)
# Scope: make the per-page Z timeline record the DETAIL actions a user does inside a building/doc
#        (clash-inspect, element-select, section-cut, measure …), and evolve W into a flat per-SESSION
#        index with Back-to-the-Future forking. NON-DISRUPTIVE + ADDITIVE: W stays milestone-sparse,
#        Z carries the richness; the existing fork-don't-wipe tree (history_bar.js PRs #5/#6) is the
#        substrate — do NOT rebuild it.
# Discipline: whitebox §-log FIRST (READ the log after every run; exit code is NOT evidence). EXTRACT,
#        don't invent — record the REAL action the user took, with the REAL recorded ts (integer ms).
# Lane: bim-ootb (viewer/ first, then erp/). Editing ~/bim-ootb is hook-blocked → work in /tmp/wt-*,
#        deploy via the owning app's sw.js CACHE_VERSION bump. Honour until ✅ DONE.

---

## ⏱ RESUME STATUS (2026-06-11) — read this first

SHIPPED: A1 [VIEWER] events ✅ (#245, viewer sw v637) · A2 [VIEWER] flat per-session W + read-only re-home ✅
(#246, viewer sw v638 / erp sw v638). Viewer LIGHT lane = A1 + W + read-only scrub.

✅ FIXED (bim-ootb PR #250, viewer sw v639) — 🐞 "new session — the Z line does not show the dots":
1. **A1 GAP closed — section TOGGLE now records.** `tools.js?v=30` emits
   `recordEvent('SECTION_CUT', 'Section '+axis, {axis})` at the `§SECTION ON` site (key `x` /
   section-btn → `A.toggleSection` → `applySectionAxis`); saveCut's emitter kept (coalesce dedups).
2. **Scrub-gate added** — restore drives the SAME real setters (`HistoryTap.applyView` → `section.write`
   → `toggleSection`), so `common/history_tap.js?v=5` holds an `isApplying()` latch during applyView and
   `universal_history.js?v=18` `recordEvent` gates on it → browsing the past mints NO fake dots.
3. **Diagnostic landed** — `§HIST_SESSION id=<sess> reHome=<bool> treeKey=<key> dots=<n>` logged right
   after HB.configure; the next empty-Z paste pinpoints fresh-session vs re-home vs non-recording action.
Witnesses (logs read): W-Z-EVENTS ALL PASS (poc_z_events.js extended: emitter + behavioral isApplying
latch + diag) · **W-HIST-SESSION ALL PASS** (NEW `viewer/tests/probe_hist_session.js`, REAL viewer on
Duplex: fresh open → BUILDING_OPEN dot + `reHome=false`; `x` → `§HIST_PUSH kind=event "Section Y"`;
scrub → `§EVT RESTORE`, no new dot; `&sess=` → `reHome=true`). Regression: poc_viewer_sessions /
poc_histbar 15/15 / poc_bomb_clears_world / poc_cherry_pick / poc_whole_deeplink PASS; audits green.

✅ A1-DOC [ERP] GLASSBOWL SLICE SHIPPED (bim-ootb PR #251, erp sw v640): committed doc changes
(Save/New/Delete/DocAction) now dot the glassbowl Z (#scrub) — PURE `CORE.docLabel(op)` + `docDot()` in the
crud_overlay commit funnel ONLY (5 sites; no keystroke path); `window.recordDocMoment` = PAGE-LOCAL §DOC-DOT
(no WholeHistory mirror — detail never crowds W), docSeq-distinct, vlRestoring-gated. Witnesses W-DOC-DOTS +
W-DOC-DOTS-DOM (live page: §DOC-DOT "Save Order", #scrub 1→2). crud_overlay.js synced to build/erp source.
SCOPE NOTE: glassbowl is the only ERP surface with a COMMIT funnel today — idempiere.html is a nav-only
renderer (its Z = idmp_history nav moments, already recorded), gravity is read-only. RULE/SIGN/PCLOSE dots
ride the same recordDocMoment hook when those gestures land on a page with a Z.

✅ A-GRAIL [ERP] GLASSBOWL FOLD-BACK SHIPPED (bim-ootb PR #254, erp sw v641): Z scrub now FOLDS the
doc back via the fold engine when scrubbing past a DOC_ACTION dot. crud_overlay.js: docDot(label,op)
passes op to recordDocMoment; foldBackDocOp/foldForwardDocOp call KernelOps.undoOp/redoOp + setDocStatus,
exposed as window.crudFoldBack/crudFoldForward. glassbowl.html: recordDocMoment stores v.docOp + syncs
window.__viewHist; new foldDocOps(from,to) wired into scrubTo — DOC_ACTION dots call fold engine,
read-only events restore LOOK only. W-FOLD-BACK + W-FOLD-BACK-DOM ALL PASS.
DEFERRED: refold = redo-to-tip (cheap, redo already exists); period-close SuperAdmin barrier;
A2 fork half = BTTF spawn-new-session on edit-from-past (ERP lane, the deep multiverse work).

---

## THE AGREED MODEL (user, 2026-06-10) — "Z is table stakes, W is the edge"

Two layers, two jobs:
- **W (World) = the coarse, cross-page, cross-SESSION chooser.** A FLAT list of sessions (one card per
  building/doc open). "Each W is a new one." It answers *where was I?* Stays sparse — detail NEVER goes here.
- **Z (page-local) = the fine detail of ONE session.** Every action done inside that building/doc. Answers
  *what did I do in here?* Read-scrub freely; full detail preserved long-term (NO prune — bomb is the only clear).

**Session = one open.** Tied to the browser TAB (sessionStorage): a reload CONTINUES the session (no Z loss);
closing the tab and reopening = a NEW session (fresh Z, new W card). Two visits in a day = two W cards.

**The Back-to-the-Future paradox (forking), resolved at SESSION granularity:**
- READING the past is free — scrub Z to any past step; pick any past session from W. No fork, no mutation.
- ACTING in the past is NOT "going back" — it SPAWNS A NEW SESSION. The old session is preserved intact under
  its own W card; a new W card is created, and its Z starts fresh from the STATE you were viewing.
- Worked example (user-confirmed): old session has 10 actions; you scrub to action 3 (a COLOR); you do a new
  action (CUT). → new W card; new Z = `[cut]` (the cut is action #1); the scene = **colored (inherited at
  step 3) + cut**. The old 10-action session is untouched. The 10 steps are NOT merged into the new Z — to see
  them, pick the old card in W.
- Forking is PER-SESSION, not per-node → W stays a clean flat list, not a busy multiverse tree.

**Why the incumbents don't have this:** Revit/Navisworks/iDempiere/Figma all have Z-class history (in-file undo
/ per-record log / per-file version history). None have a zero-backend, cross-surface (BIM↔ERP) W that travels
sessions and forks a new reality on edit-from-past — they're file/server-bound and siloed per app.

**Z is RANDOM-ACCESS, not a linear undo stack (already built — preserve it).** The `‹ dots ›` bar lets a user
HOVER a dot to preview that step and CLICK to jump straight to it — vs the incumbents' undo stack where getting
5 steps back means pressing undo 5× blind. The A1 events land as those same hover/click dots automatically, so
the random-access affordance extends to them for free. Keep dots click-to-jump + hover-preview.

---

## LANE PARTITION (user 2026-06-10) — the multiverse/refold is ERP-only; the viewer stays light

The heavy machinery (fork on edit-from-past, refold, period-close barrier, lazy tip-vs-past divergence) only
earns its keep where state is **DERIVED**. That is the ERP side: docs derive postings from rules, so "go back,
edit, re-derive" and "acting-in-the-past spawns a timeline" keep a *derived reality* consistent — the grail.

The **VIEWER is mostly read-only** (clash-inspect, measure, section, select, camera): nothing downstream is
derived, so there is nothing to refold and no alternate reality to spawn. Going back + doing a different view
is just navigating. Nuance: the viewer's *few* true model ops (grid-move, element-place, design edits via
KernelOps) already fork-don't-wipe in the existing tree for free — but they're the minority; the read-only
majority needs none of the divergence/refold ceremony.

- **VIEWER LANE (light):** `A1` events ✅ → `W` session chooser → read-only scrub. (≈complete after A1 + W.)
- **ERP LANE (the grail):** `A1-DOC` CRUD-at-save dots → `A-GRAIL` fold-back → refold-by-redo →
  period-close SuperAdmin barrier → lazy edit-from-past fork (`forked = kids.length > 0` = the divergence test).
  This is where the deep effort + the differentiation live.

`A2` (flat-session W + state-snapshot-at-fork) spans both, but the FORK half of A2 is ERP-lane; the viewer uses
A2 only for the flat per-session W list + read-only re-home, not for divergence accounting.

## INCREMENTS

### A1 [VIEWER] — record the DETAIL events into Z ✅ SHIPPED (bim-ootb #245, viewer sw v637)
Today Z records only model ops in the significance profile (default `high` = BUILDING_OPEN/GRID_MOVE/
ELEMENT_PLACE/DESIGN_*/CAPTURE_4D/CLASH_SNAG + Find nav). The user's detail actions are invisible:
- **clash-inspect** (`§CLASH_DETAIL`) — never calls the recorder at all (no §HIST_PUSH, not even §HIST_DROP).
- **element-select** (`ELEMENT_PICK`) — commits, but gated to depth `max` → dropped at default.
- **section-cut** (`SECTION_CUT`) — commits, but not in any profile → dropped.
- **measure** — never calls the recorder.

A1 = a read-only **EVENT** channel that records these as Z steps WITHOUT model-op/undo semantics:
- New `UniversalHistory.recordEvent(type, label, ref)` → `HB.push({ bucket:'event', kind:'event',
  readonly:true, type, label, viewState:_tapView(), ref, sigKey })`. Read-only → scrubbing onto it re-applies
  the stamped LOOK (camera/lens/section/xray), never mutates the kernel.
- New significance bucket `event`, ON at the default stop, covering: `CLASH_INSPECT`, `SECTION_CUT`,
  `MEASURE`, `ELEMENT_SELECT`. (The depth knob still gates verbosity; `low` keeps events off.)
- Wire emitters in the host (no logic rewrite — one call at the action site):
  - `clash_matrix.js` clash-detail select → `recordEvent('CLASH_INSPECT', 'Clash '+a+'×'+b, {guidA,guidB})`
  - `section_cut.js` cut applied → `recordEvent('SECTION_CUT', 'Section '+axis, {axis,constant})`
  - `measure.js` measurement complete → `recordEvent('MEASURE', dist+' '+unit, {a,b})`
  - `picking.js` element select → `recordEvent('ELEMENT_SELECT', name+' · '+cls, {guid})` (read-only event, in
    ADDITION to / instead of the max-gated ELEMENT_PICK op — pick the lighter event form for the default view)
- **Witness W-Z-EVENTS:** headless/node where possible (significance includes `event` bucket; recordEvent pushes
  a readonly moment that does NOT call KernelOps.undo/redo) + §-log in-browser: each action emits ONE
  `§HIST_PUSH … kind=event type=<T>` and stepping onto it logs a look-restore, NOT a §KRN op.
- Events stay PAGE-LOCAL — `docTypes` is unchanged, so W still shows only opens (model invariant: detail ≠ W).

### A1-DOC [ERP] — doc CRUD-at-save events into the iDempiere/Glassbowl/Gravity Z
GRANULARITY RULE (user 2026-06-10): **a dot = a COMMITTED change, never a keystroke.** Typing is the
browser/field's own native Ctrl+Z territory (character-level undo lives there); our Z records only at the
SAVE/commit boundary. NEVER record per character / word / sentence.
- Already recorded (keep): `_histPush('record'|'window'|'tab')` — navigation moments (which record/window/tab).
- ADD — one dot per COMMITTED doc change: record Save (Ctrl+S / field-Enter-commit), New, Delete, a DocAction
  (Complete/Void/Reverse), a rule edit (RULE), a signed op (SIGN). The §-sniffer already sees CRUD/RULE/SIGN
  tags in the stream (history_tap.js STOPS) — surface those as dots again (the depth-knob removal orphaned them),
  but ONLY at commit, deny-filtered against the firehose/lifecycle/intra-keystroke noise.
- Typing between commits → NO dot (native Ctrl+Z handles it). Witness: editing 10 chars then Save = ONE dot.
- Stays PAGE-LOCAL (Z), mirrored to W only as the existing nav milestones — CRUD detail never crowds W.

### A-GRAIL [ERP] — a save-dot FOLDS the model back (the payoff; leans on the existing fold engine)
Because a dot = a committed save, the dot BEFORE it = the pre-save state → Z is a user-facing FOLD control.
This is the SAME fold spine already proven on the ERP side (HolyGrail §RULE-EDIT; reverseCorrect/void nets
fact_acct to zero, W-FOLD-REVERSE; postings derived from the op-log) — surfaced as history. Stand on a past
save → fold the model back → act → (BTTF) spur a new timeline. No incumbent can re-derive downstream from an
edit; their Ctrl+Z is volatile, in-session, single-doc.
Honest layering (sets build order — do NOT overclaim):
- MODEL ops (grid move, element place) fold back via KernelOps undo — exists.
- DOC CRUD/postings fold back via the ENGINE (post_resolver/doc_poster/reverseCorrect) — real, built+witnessed.
- READ-ONLY events (measure, clash-view) have nothing to fold — scrubbing restores the LOOK only (correct).
A1 = RECORD the save-dots (foundation). A-GRAIL = wire a dot to FOLD via the engine (connect, don't invent).

REFOLD = REDO-REPLAY THROUGH THE DOTS (user 2026-06-10) — resolves the "refold breaks Z" worry: a rule edit
does NOT invalidate the recorded dots; you RE-WALK them forward (redo) and each REPLAYABLE action re-derives
under the new rule. The Z op-log IS the refold script. So a **"refold button" = "redo to tip"** — automating
click-each-forward-dot. Reuses the EXISTING redo path (HB.redo already walks forward re-applying each entry):
model ops re-apply, doc postings re-derive via the engine, read-only events (clash/measure) just re-apply their
look (nothing to derive — correct). DEFERRED (later) — but cheap, because redo already exists.

PERIOD-CLOSE BARRIER (user 2026-06-10): a period close is NOT an ordinary save — it is a SEALED checkpoint
(the existing signed balance-b/f checkpoint on the sidecar op-log; §INTEG-WIRE period-close). Fold-back is free
WITHIN the open period; folding ACROSS a period-close dot is a BARRIER requiring **SuperAdmin + a signed reopen**
(mirrors iDempiere's role-gated period reopen — EXTRACTED, not invented). Below the barrier the sealed history
stays viewable read-only but NOT foldable without elevation. Normal users: the period close is the floor of
their fold range. The fold engine must refuse to cross a checkpoint dot unless the actor is SuperAdmin.

### A2 [BOTH] — the session multiverse (viewer: flat-W + read-only re-home · ERP: + the fork half)
- Stamp a **sessionId** at each open (sessionStorage: reload continues, new tab = new session).
- Key Z by `(building/doc, sessionId)`; each open = a fresh Z + a new W card carrying the sessionId.
- W card click → load that session's Z read-only (land on its last state).
- Edit-from-past → mint a NEW sessionId, snapshot the FULL state at the fork point as the new Z's baseline,
  record the new action as its first step. Old session preserved.
- **STATE-SNAPSHOT-AT-FORK requirement:** the baseline must reproduce EVERY stateful action at the fork point
  (color, section, xray, isolate …), not just camera. Camera/section/xray are already in the §-tap view-state;
  **verify COLOR + any model-level change are captured** — if not, extend the snapshot, else a fork drops them.

---

## INVENTION BOUNDARY
Record only the REAL action the user performed, with the REAL recorded ts. No synthetic events, no faked
timestamps. The fork baseline is a SNAPSHOT of actual state, never a reconstruction.
