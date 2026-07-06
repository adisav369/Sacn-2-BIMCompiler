# ⚠ DO NOT REMOVE — Apply the §-tap history (knob · field · sniffer) to the iDempiere/ERP pages
# Scope: NON-DISRUPTIVE + ELEGANT. The viewer now has a proven §-stream history tap (sniffer = zero-wire
#        total recording; field() = one-line restore; knob = breadth dial; combine = vector union). Bring the
#        SAME primitives to the ERP pages WITHOUT rewriting what already works there. Whitebox §-log is the
#        witness — drive a real sequence, read the §-lines, the log IS the proof (never trust exit code/boolean
#        asserts alone). Honour until ✅ DONE.

## ▶ CONFIRM STAGE FIRST (don't blind-edit)
- ERP ENGINE source = `bim-compiler/build/erp/` ([erp-src](memory feedback_erp_source_of_truth.md)).
- ERP PAGES that ship (GH Pages) = `bim-ootb/erp/` — `idempiere.html`, `idmp_history.js`, `erp_*.js`, `ad_*.js`.
  The history work targets the PAGES. Editing `~/bim-ootb/` is hook-blocked → work in a `/tmp/wt-*` worktree.
- Reconcile drift: confirm which copy is canonical for the file you touch before editing. Deploy via `erp/sw.js`
  CACHE_VERSION bump (the ERP app owns its own `erp-ootb-` caches — see docs/ERP_FOLDER_HOME.md).

## ▶ WHAT ALREADY EXISTS (don't duplicate — extend)
- `common/history_tap.js` (SHIPPED, PRs #205/#207/#213): `S()` sink · `sniff()` log-sniffer · `field(name,read,write)`
  restore primitive · `currentView()`/`applyView()` · `combineViews()` · the knob STOPS + DENY/LIFECYCLE filters.
- `viewer/universal_history.js` = the VIEWER adapter: `_wireTap()` registers viewer fields (ghost/xray/cam/
  section/palette) + turns the sniffer on. COPY THIS SHAPE for ERP — it is the template.
- `erp/idmp_history.js` (138 lines, LIVE in idempiere.html) = the ERP's OWN scrubber. moment = {windowId, tabIdx,
  table, recordId}; `push()` by host; `registerRestore(fn)` → READ-ONLY re-open window/tab + re-select record;
  NEVER mutates the kernel op-log. **KEEP IT. Do not replace it.** It is the ERP equivalent of HistoryBar.

## ▶ THE NON-DISRUPTIVE RECIPE (three small, additive steps)
1. **Load the shared tap** — add `<script src="../common/history_tap.js?v=N">` to `idempiere.html` (and erp.html if
   it has a bar), BEFORE `idmp_history.js`. Best-effort load (onerror skip), same as the viewer.
2. **Turn the sniffer on (free total recording)** — one call `HistoryTap.sniff(true)` at ERP init. ERP already
   logs § everywhere (§AD_UI ×56, §CRUD, §KANBAN, §RULE, §OV, §NAVIGATE, §FOCUS_NODE, §ERP_PANEL, §ERP_SEARCH,
   §TAP, §SHOWME, §AD_GRAPH). The sniffer catches all of them, zero per-feature wiring. TUNE the ERP firehose:
   extend DENY/LIFECYCLE for ERP boot tags (`§AD_UI_LOADED`, `§*_LOADED`, `§*_HOST`) — verify against a real
   idempiere boot log which tags are noise vs act (don't guess; read the log).
3. **Register ERP `field()`s for restore-DEPTH** — mirror `universal_history.js _wireTap()` with ERP getters/setters
   so an idmp_history moment restores the LOOK, not just the record. Candidates (confirm each has a get + a set):
   - `search`  → ERP_SEARCH term (read current query / set it)        — `§ERP_SEARCH`
   - `kanban`  → KANBAN lens/grouping (read lens / re-apply)          — `§KANBAN`, `§KANBAN_LENS`
   - `graph`   → AD_GRAPH focus node                                  — `§FOCUS_NODE`, `§AD_GRAPH`
   - `filter`  → active AD-tab filter/where                           — `§CRUD`, `§AD_DATA`
   Then in `idmp_history.js registerRestore(fn)`, after the read-only re-open, call `HistoryTap.applyView(entry.view)`
   and stamp `view: HistoryTap.currentView()` onto each pushed moment (exactly the PR #2 pattern).

## ▶ INVARIANTS (must hold — this is what keeps it elegant + safe)
- **Read-only restore.** ERP restore re-opens window/tab/record + re-applies VIEW fields. It NEVER mutates the
  signed kernel op-log (idmp_history already guarantees this; the tap's field-restore is view-only by nature).
- **Couple to nothing.** ERP features get one `field()` line where they live (search module registers `search`,
  kanban registers `kanban`). The history layer stays ERP-agnostic. New ERP features appear in the sniffer the
  moment they log a §.
- **Two costs stay separate** (the settled lesson): recording = free via the sniffer (total); restoring = one
  `field()` line per state-holding ERP act. Don't aim for "all restorable" — only the look-defining ones.

## ▶ THE KNOB (shared concept, ERP surface)
`idmp_history.js` has its own dot bar + double-tap bloom. Adopt the §LOCKED-KNOB design (HISTORY_KNOB_SIGNAL_TAP.md):
5-stop drag (Off·Low·Mid·High·Max, default High) · press = richness (dot→chip→thumbnail; thumbnails desktop-only/
ephemeral) · pitched SFX detents (reuse the ERP/viewer SFX synth; respect mute). Either share one knob widget with
the viewer or mirror it — decide for least duplication. Map any persisted ERP depth setting onto the 5 stops.

## ▶ WITNESS PLAN (whitebox §-log — the user's required proof style)
On the LIVE idempiere page (headless Chrome + the ERP demo data), drive a REAL sequence and let the §-lines prove it:
1. open a window → switch AD-tab → select a record → set a search filter / change kanban lens (real §ERP_SEARCH/§KANBAN).
2. STAMP the moment (`currentView()`), log `§PROOF stamped.view=…`.
3. move on (clear filter / different lens).
4. click the earlier dot → `applyView` → show the REAL §ERP_SEARCH/§KANBAN lines RE-FIRE + `§EVT RESTORE keys=search,kanban,…`.
5. dump `HistoryTap._all` sniffer tags → prove zero-wire recording of ERP acts.
Save to a log, READ it, present the §-lines (mirror viewer `drive_whitebox.js`). PASS = the look returns from the log.

## ▶ STARTING POINTS
- Template: `bim-ootb/viewer/universal_history.js` `_wireTap()` (the field/sniffer wiring to copy).
- ERP § census: `grep -rhoE "§[A-Z_]+[A-Za-z_]*" erp/*.js | sort | uniq -c | sort -rn`.
- Spec: `prompts/HISTORY_KNOB_SIGNAL_TAP.md` §LOCKED-FIELD / §LOCKED-SNIFFER / §LOCKED-KNOB.
- Viewer proof to mirror: `/tmp/wt-field/drive_whitebox.js` (whitebox §-log driver).

## ▶ §SESSION 2026-06-16 — THE SUBSCRIBE ✅ DONE/LIVE (#341, erp sw v699) — coherent with viewer #340
SHIPPED + WITNESSED 6/6 (`erp/tests/poc_idmp_history_subscribe.js`). idmp_history now SUBSCRIBES to the §-tap:
`pushCrumb` read-only crumb dots + `idempiere.html _drainErpTap` (onFeed→historySince→pushCrumb, knob=high,
arms post-login, isApplying-gated); DENY tuned VFS·AD_PARSER·IDEMPIERE·WHOLE from the REAL firehose.
TROUBLESHOOT (future dev): no ERP crumb dots? check (1) `§IDMP-HIST-TAP … bar SUBSCRIBES tap (knob=high)` logged
= wired; (2) the act's §-tag is in a STOP set in `common/history_tap.js` STOPS (high adds KANBAN/ERP_SEARCH/
AD_GRAPH/AD_DATA/CRUD/RULE) AND not in DENY_TAG; (3) `_erpArmed` armed (first post-login act is skipped by design);
(4) `_histRestoring`/`isApplying()` not stuck true. Crumb has no view → click is a no-op look (breadcrumb only);
view-bearing crumbs come from S()-emitted acts, not raw console.log. Original spec below.

## ▶ §SESSION 2026-06-16 — THE SUBSCRIBE (coherent with viewer #340) — SPEC then build
Status found: fields(search,clean)+sniffer ON + stamp/restore ALREADY shipped (poc_idmp_history_tap PASS). The
MISSING half (same gap #340 closed on the viewer): **idmp_history does NOT consume the sniffed stream** — every
ERP § act records into `HistoryTap.all` but only window/tab/record `_histPush` choke points dot. Close it:
1. **DENY tune (NON-INVENT — from the REAL runtime firehose** `distinctTags=[IDMP,SETTINGS_SAVE,VFS,AD_PARSER,
   IDEMPIERE,HELP,ACTOR,AD_DATA,WHOLE,REDPILL,AD]`**):** add infra/echo tags `VFS·AD_PARSER·IDEMPIERE·WHOLE` to
   `common/history_tap.js` DENY (shared — all are infra on the viewer too; WHOLE = the cross-page log's own echo).
2. **idmp_history SUBSCRIBES:** new `IdmpHistory.pushCrumb(tag,label,view)` = a read-only CRUMB dot (windowId
   null, dimmer than the gold nav dots); `_histRestore` already handles a viewless/windowless moment (skips
   re-open, applies `view` only). Wire `HistoryTap.onFeed(_drainErpTap)` in `_wireErpTap` after `sniff(true)`:
   knob→`high` (spec default), drain `historySince(hw, _EXPLICIT)` into pushCrumb; re-entry + `isApplying` gated.
3. Coverage falls out: KANBAN/ERP_SEARCH/AD_GRAPH/AD_DATA/CRUD/RULE dot for free; IDMP chrome + denied infra do not.
WITNESS (extend `poc_idmp_history_tap.js`, live idempiere): drive a record-select (real act → §AD_DATA, high-set)
→ assert a CRUMB dot appears via the drain with NO matching `_histPush`, the denied §VFS/§AD_PARSER produce NO
dot, and clicking a stamped crumb re-applies its look read-only. Name the issue each assertion proves.

## ▶ DELIVERABLE
idempiere.html loads the shared tap → sniffer records every ERP § act for free → ~3-4 ERP `field()`s make moments
restore the look → idmp_history stays the bar, now depth-aware → whitebox §-log proves a moment's filter/lens
returns on click. Then (optional) the shared knob. The pitch: the SAME history that listens to one clean signal,
now on the ERP surface, coupling to nothing, reusing every primitive — not a second implementation.
