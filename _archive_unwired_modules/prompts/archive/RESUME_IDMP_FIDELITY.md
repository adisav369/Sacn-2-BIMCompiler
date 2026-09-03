# ⚠ DO NOT REMOVE — RESUME CARD: iDempiere L&F FIDELITY (the FUNDAMENTAL LAW lane)
# Authored 2026-06-18 (session handoff). Read the doctrine FIRST: prompts/GRAND_LANE_STRATEGY.md §0
#   (the ⚖ FUNDAMENTAL LAW, user decree 2026-06-18). Then this card. One bounded leg per session, R→E→V.

## THE LAW (locked, in GRAND_LANE §0)
The iDempiere surface is **INDISTINGUISHABLE from real iDempiere — zero learning curve = the wow factor.**
- iDempiere-native chrome ONLY: classic ADWindow toolbar = the CRUD surface; header = menu·logo·search·role/
  client·logout; folder/box tabs; ONE toolbar (no duplicate inline bar); clean status bar (record info only).
- Anything NOT in iDempiere → the `⋯` pill rail (extras: share, home, graph, kanban, POS, Ninja, plugin, audio,
  history). Blue-Future band + history scrubber HIDDEN until History is pressed.
- **VERIFY against the real iDempiere source — NEVER assume.** Oracle source on disk:
  `~/idempiere-dev-setup/idempiere`. The toolbar truth = `org.adempiere.ui.zk/WEB-INF/src/org/adempiere/webui/
  adwindow/ADWindowToolbar.java`; theme icons = `org.idempiere.zk.breeze.theme/src/web/theme/default/images/
  *.png`; status bar = `.../adwindow/StatusBar.java`.

## DONE / LIVE THIS SESSION (do NOT redo)
- **S6 LENS-SWAP** ✅ PR #365, sw v712, W-CRITIC-LENS-SWAP 3/3 — kanban drag rides the SHARED `_fsmCtx →
  __crud.process` signed lane (one owned model: grid ⇆ kanban one store, survives reload, ring never opens).
- **Classic chrome / law** ✅ PR #366, sw v713, W-CLASSIC-CHROME 4/4 — red pill + "just the pill" clean mode
  RETIRED; classic ADWindow toolbar carries New/Copy/Save/Save&New/Delete/Ignore (wired to the existing in-place
  seams, no forked verb); inline duplicate verb bar hidden; folder tabs (no "└" glyph); Share/Home → pills;
  Blue-Future + history scrubber hidden until History pressed.
- **Toolbar fidelity** ✅ PR #368 (auto-merge pending green e2e at handoff; sw v716 after origin/main sync from
  v715) — Process = the iDempiere GEAR (verified vs `Process24.png`), greyed when the record has no runnable
  process; status bar cleaned (removed the "folded from SQLite WASM (ad_seed.db)" tell + the "· <TableName>"
  suffix; provenance kept in the status-src title attr).
  ⚠ VERIFY #368 LANDED on origin/main before starting (`gh pr view 368 --json state`). If BEHIND: it's a sync,
  not a redo — `git merge origin/main`, take the HIGHER sw CACHE_VERSION, keep BOTH precache hunks, re-run the
  witness, push. sw.js is the conflict magnet (origin raced from v713→v715 during this session).
- Witness: `bim-ootb/erp/tests/poc_classic_chrome_live.js` (W-CLASSIC-CHROME 4/4) + `poc_critic_lensswap_live.js`
  (W-CRITIC-LENS-SWAP 3/3). Run from `bim-ootb/erp`: `node tests/poc_*.js`.

## NEXT — OPEN LEGS (work top-to-bottom; one PR each, witness, auto-merge, VERIFY it lands)
1. ✅ **ZOOM button — DONE 2026-06-19 (PR #402, sw v718, W-ZOOM-ACROSS 3/3 + W-CLASSIC-CHROME 4/4 regression).**
   Chose **Zoom Across (where-used)** = iDempiere's actual `btnZoomAcross` (magnifier, verified vs `ZoomAcross24.png`).
   Folded (non-invent) from AD+data: tables with a col named the record's KC, header(TabLevel0) tab of their default
   `AD_Table.AD_Window_ID`, COUNT WHERE KC=K, keep>0. Popup "Window (n)" → opens that window FILTERED to KC=K
   (`_zoomFilter`/§ZOOM-ACROSS-FILTER). Also fixed pre-existing toolbar enable-state staleness (renderActiveTab now
   re-renders the toolbar after _records loads → Copy/Delete/Process/Zoom no longer stale on first grid load).
   Screenshot `~/Pictures/Screenshots/zoom_across_leg1.png`. ⚠ VERIFY #402 landed on origin/main before next leg.
2. ✅ **Header Logout + Exit — DONE 2026-06-19 (PR #430, sw v733, W-HEADER-LOGOUT-EXIT PASS).** The "title/logo
   trim" sub-task was MOOT — the Kernel-ERP rebrand (#418/#413) already replaced the "iDempiere-like" title + "A+"
   logo with the deliberate "Kernel-ERP" brand + doublebubble glass-logo (do NOT undo). The REAL leg (user-steered):
   the header carries iDempiere's single **Logout** (UserPanel.java:126 parity — Lucide logOut door-arrow glyph +
   click → showLogin('user') → login overlay; the old ambiguous "⇄ Switch role / Log out" retired, role-switch is
   the overlay's Back step); and the ⋯ rail's `home` pill became **Exit** → `../index.html` (the landing/app-shell
   front door — index.html is the live landing; index2.html was swapped out by another session). Exit-to-landing is
   an app-shell action → stays in the rail per the LAW; the header stays iDempiere-pure. Witness
   `scripts/poc_header_logout_exit_live.js`; screenshots header_logout_full.png (glyph) + header_logout_exit.png.
3. ✅ **Posted COLUMN/button (role-gated accounting) — DONE 2026-06-20 (PR #433, sw v735, W-POSTED-COLUMN 6/6 +
   W-CRITIC-POST-LIVE 16/17→17/17 + W-CLASSIC-CHROME regression).** The Accts-Posted / Posting-Preview `⋯` pills
   RETIRED; the per-record accounting now surfaces as iDempiere's native **Posted** field ON THE DOCUMENT WINDOW —
   a grid COLUMN + mobile-card chip + form BUTTON (bookOpen ledger glyph, closest Lucide to InfoAccount16.png) —
   visible ONLY to accounting roles. Non-invent: gate = existing §AD posting-doc answer (table carries AD `Posted`
   col) AND the role's `MRole.isShowAcct` (read from `ad_role.isshowacct` onto `role.isShowAcct` in
   idmp_session.buildContext); label folds from the `_Posted Status` ref-list (AD_Reference 234); click reuses the
   EXISTING seams — posted→`openPostedFor` (actual GL facts), draft→`openPreviewFor` (pure dry-run), now behind the
   single Posted affordance (`_openPostedAffordance`). `IdmpPillActions.posted/preview` pulled → `window.IdmpPosting`
   host seam carries the two GL views. Witness proves: Admin (role 102, isshowacct=Y) sees the column+button on
   C_Invoice → click folds a to-the-cent balanced preview; ⋯ rail no longer carries posted/preview; User (103,
   isshowacct=N) sees NOTHING; non-posting window (C_BPartner) has no column; 0 pageerrors. Stale pill witnesses
   reconciled to the new registry (poc_idmp_pills/lifecycle/redpill/pill_mobile). Witness
   `tests/poc_posted_column_live.js`; screenshot `~/Pictures/Screenshots/posted_column_leg3.png` (form button on the
   GardenWorld-Admin Sales Invoice). ⚠ VERIFY #433 landed on origin/main before next leg (it did: a18fe3a).
4. ✅ **Dirty-exit gate — DONE 2026-06-19 (PR #416, sw v727, W-DIRTY-GATE 4/4 + W-DRAFT-RESTORE 10/10 coexist).**
   `onExit → "CloseUnSave?"` replicated: `formNeedsSave()` (content-aware) + `_dirtyGuard`/`_confirmDirty` on all
   user nav choke points; Cancel keeps dirty, Discard buffers+proceeds; untouched New still auto-discards;
   beforeunload native prompt. Draft-pip buffer PRESERVED (coexists, not ripped out). History session was CEASED.
5. ✅ **Pill-rail cleanup + Print/statements split — DONE 2026-06-20.** Sub-parts:
   - `editmode` ✅ pruned (PR #419, ring Glass-only).
   - **Process tooltip Alt+P→Alt+O** ✅ (PR #435, sw v736) — oracle-verified (`ADWindowToolbar` altKeyMap
     `VK_O=btnProcess`, `VK_P=btnPrint`); the gear tooltip now reads "Process (Alt+O)".
   - **Toolbar Print button** ✅ (PR #438, sw v737, W-PRINT-TOOLBAR 4/4). User cleared me to make the call
     ("follow your best judgement … as long follow idempiere convention"). The faithful split turned out to need NO
     prune: the `reports` pill (`__report.menu`→`openMenu`) was ALREADY statements-only (Performance-Analysis
     Financial Report menu) — the missing half was the per-doc **Print** (`foldReceipt`), which only had a retired
     Glass-ring bus path. Added iDempiere's `btnPrint` (Alt+P, printer glyph, after Process) → new
     `__report.printRecord(key,recordId)` folds THE OPEN record's receipt (not `show()`'s lit demo doc); enabled only
     on a printable doc (header/line table in `REPORT_MAP`), greyed otherwise (enablePrint parity). So: per-doc Print
     = toolbar, financial statements = the `reports` menu pill — iDempiere's actual split, `reports` left intact (not
     faked, not pruned — it was already correct). Mirrored `printRecord` to `bim-compiler/build/erp` source-of-truth
     (drifted copy, patched independently). Witness `tests/poc_print_toolbar_live.js`; screenshot
     `~/Pictures/Screenshots/print_toolbar_leg5.png`.
6. ✅ **Ninja spec note — DONE (PR #419).** DESIGN NOTE added to `ninja_pill.js` (+ mirrored to the
   `bim-compiler/build/erp` source-of-truth): Ninja/Red1 stay in the `⋯` rail and will be tuned to act on the
   CURRENT ACTIVE WINDOW (zero-friction).

## SPEC — Leg 1: ZOOM ACROSS (where-used drill) — IN PROGRESS 2026-06-19
**Issue proven:** the toolbar lacked iDempiere's Zoom button; users can't drill from a record to the documents
that reference it. **Source-verified:** `ADWindowToolbar.btnZoomAcross` (magnifier glyph `ZoomAcross24.png`),
`AbstractADWindowContent.onZoomAcross` → `WZoomAcross`/`ZoomInfoFactory` = WHERE-USED: a popup of the windows
whose records reference THIS record, each with a count; pick one → that window opens FILTERED to the referencers.
**Fold (non-invent):** for current table T, key col KC (e.g. `C_BPartner_ID`), pk value K —
`AD_Column.ColumnName=KC` in tables ≠ T that are the **header (TabLevel 0) tab** of their default
`AD_Table.AD_Window_ID`; for each, `COUNT(*) FROM <table> WHERE KC=K`; keep count>0 (skip tables absent from the
seed). Popup lists `WindowName (n)`; click → `_zoomFilter={windowId,table,col,val}` + `openWindow(win,name,keepZoom)`;
`renderActiveTab` adds `KC=K` to the header tab's clauses while `_zoomFilter.windowId===_activeWin`. Icon = `search`
(magnifier, faithful to iDempiere). Enabled only with a real PK row; empty popup → honest status, never a dead btn.
**Witness:** `W-ZOOM-ACROSS` (`tests/poc_zoom_across_live.js`) — BP#118 zoom lists Sales Order/Invoice/Shipment etc.
with counts; clicking Sales Order opens win 143 filtered to C_BPartner_ID=118; §ZOOM-ACROSS-FILTER logged; 0 pageerrors.

## SPEC — Leg 4: DIRTY-EXIT GATE — IN PROGRESS 2026-06-19 (History session confirmed CEASED: idmp_history.js last moved 06-16, no history branch/worktree touched today → user cleared me to proceed)
**Issue proven:** leaving a record with unsaved edits SILENTLY buffered the draft (W-DRAFT-RESTORE) — iDempiere instead
PROMPTS. **Source-verified:** `AbstractADWindowContent.onExit` → `Dialog.ask("CloseUnSave?")` (close without saving?
Yes/No; No → cancel + restore focus); needSave drives it. **Build:** `formNeedsSave()` on `__crud` = content-aware
dirty (update→changed cols; New→typed vals differ from the captured `_inlineBaseline`, so an UNTOUCHED New still
auto-discards, a TYPED New prompts). Host `_dirtyGuard(proceed)`: if `#idmp-inline-mount` open AND formNeedsSave →
iDempiere-style confirm [Cancel]/[Discard changes]; Cancel→stay (`§DIRTY-GATE choice=cancel`), Discard→buffer the
private draft (W-DRAFT-RESTORE COEXISTS) + clear `_newMode` + `proceed()` (`§DIRTY-GATE choice=discard`). Wired into
the nav choke points: record-nav arrows, grid/card row→form click, window switch (wintab + menu leaf), closeWindow ×,
tab-strip; `beforeunload` → native prompt when content-dirty (replaces the silent buffer). **Witness W-DIRTY-GATE:**
a typed New blocked on nav (Cancel keeps the form dirty; Discard proceeds + buffers); an untouched New auto-discards
(no prompt); §DIRTY-GATE logged; 0 pageerrors.

## SPEC — Leg 3: POSTED COLUMN/BUTTON (role-gated accounting) — NEXT, for a fresh session
**Issue:** the per-record accounting (Accts-Posted actual GL + Posting-Preview dry-run) lives as `⋯` pills
(`posted` "Accts Posted" + `preview`; `preview` already gates `showWhen:"posting-doc"`). iDempiere instead shows
**Posted** as a field IN THE DOCUMENT WINDOW, visible ONLY to accounting roles.
**Source-verified (NEVER assume — re-check before building):**
- `WButtonEditor.java:198` — the AD `Posted` column (DisplayType Button, AD_Reference 28 "_Posted Status") renders
  as a BUTTON whose LABEL is the posting-status ref-list value (Posted / Not Posted / Error / …), glyph =
  `Icon.INFO_ACCOUNT` / `InfoAccount16.png`; click → the accounting viewer (`WAcctViewer`/`WAcctViewerData`) = the GL facts.
- ROLE GATE = `AD_Role.IsShowAcct` (`MRole.setIsShowAcct`/`isShowAcct()`): a role WITHOUT it sees NO accounting
  (no Posted column/button, no acct tabs). **Our seed already carries `ad_role.isshowacct`** — 102 GardenWorld
  Admin=Y, 12000 Odoo Admin=Y, 200001 GW Admin-Not-Advanced=Y; 103 GardenWorld User=N, 0 System=N.
- "Appears as a column in window" (user): the Posted field is a grid COLUMN + the form BUTTON on posting-doc windows.
**Fold / build (non-invent, consume existing seams — do NOT fork a verb):**
- GATE: render the Posted affordance only when BOTH (a) the open table carries the AD `Posted` column (the EXISTING
  `§AD-GATE showWhen:"posting-doc"` host answer — reuse it) AND (b) `_session.role.isshowacct === 'Y'` (verify the
  session object carries the role's isshowacct; if not, read `ad_role.isshowacct` for the active role — add it to the
  session role load). GardenWorld User (103) must see NOTHING; GardenWorld Admin (102) sees it.
- SURFACE: a Posted COLUMN in the grid (label = posting status from the row's `Posted` value via the _Posted Status
  ref-list) + a Posted BUTTON on the form (INFO_ACCOUNT-faithful glyph — add a Lucide equivalent to icons.js, e.g.
  a book/receipt/scroll line glyph; pick the closest to InfoAccount). Click → `openPostedFor()` if the doc IS posted
  (actual GL facts), else `openPreviewFor()` (the dry-run "what Complete would post") — UNIFYING both existing
  renderers behind iDempiere's single Posted affordance. Both seams already exist in idempiere.html (~L2957/L2978).
- RETIRE: pull `posted` + `preview` from `pills_idmp.json` (and their IdmpPillActions bindings) once the column/button
  carries them — the ⋯ rail keeps only non-iDempiere extras (LAW).
**Witness `W-POSTED-COLUMN` (live + screenshot):** on GardenWorld **Admin** a posting doc (e.g. C_Invoice/C_Order)
shows the Posted column + form button → click → the accounting fold opens (posted→GL facts, draft→preview); on
GardenWorld **User** (103, isshowacct=N) the column/button is ABSENT; a non-posting window has no Posted column;
`posted`/`preview` no longer in the rendered pill rail; 0 pageerrors. Screenshot the Admin doc window with the column.
**Watch:** sw.js conflict-magnet (origin raced v730→v733 across this session's two legs — fresh `/tmp/wt-*` off
origin/main, take the HIGHER CACHE_VERSION, keep both precache hunks). Deploy: PR + auto-merge + VERIFY landed.

## CONCURRENT SESSION (do not collide)
- A separate session is taking the **history timeline** issue (user: "returning to a page should give that page's
  last history"). Shared files: `idempiere.html`, `sw.js` (conflict magnet), `idmp_history.js`. Work in a fresh
  `/tmp/wt-*` off origin/main, keep diffs scoped, resync on merge (higher sw version, keep both precache hunks).

## DEPLOY DISCIPLINE
Fresh `/tmp/wt-*` off origin/main · sw CACHE_VERSION clean bump · localhost witness + SCREENSHOT for any visual
leg (user can't always review visually — describe what you verified vs source) · auto-merge + VERIFY it lands.
Screenshots → `~/Pictures/Screenshots/`. The current iDempiere look: `~/Pictures/Screenshots/classic_chrome_leg1.png`.
