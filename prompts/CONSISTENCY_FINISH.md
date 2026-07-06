# ⚠ DO NOT REMOVE — Scope guard
# Scope: CONSISTENCY FINISH lane — the finish items from the 2026-06-12 cross-lane review
# (Import iDempiere/Odoo × World/History timeline). The review confirmed the two lanes do NOT
# collide (disjoint storage/op-log paths; pill registry + ?window=&record= deep-links are the
# designed shared points). What remains is FINISH, not architecture. Work this card §K-1→§K-5
# to zero (✅/⛔). Read the log after EVERY run — exit code is not evidence.
# Surfaces: ~/bim-ootb erp/ + common/ (worktree /tmp/wt-*, NEVER the shared checkout) and this
# repo's CLAUDE.md. Engine files (doc_poster/post_resolver/erp_kernel/kernel_ops) are FROZEN.

# CONSISTENCY_FINISH — one overlay kit, Lucide sweep, install dot, history hygiene

**Why this lane**: the picker/showme overlays read like a different author than the rest of the
surface — ~300 lines of byte-identical utilities duplicated across `erp_picker.js` and
`migrate_showme.js`, two parallel overlay CSS systems (`ep-*` / `ms-*`), and unicode glyphs
(🟧🟣🔵🔴🟦 ⬇ 🔒 🔌 ✓ ✗) where every other surface uses Lucide line icons from `icons.js`
(violates feedback_pill_icon_consistency). Plus three small history-lane blemishes from the
review. Everything additive; no engine, no pill manifest, no behavior change beyond icons.

## §K-1 — Shared overlay kit (`erp/overlay_kit.js`, NEW)
Extract ONLY what is literally duplicated — no framework invention (KISS):
- `OverlayKit.el(tag, attrs, html)` — the `_el` helper (identical in both modules today).
- `OverlayKit.ensureSql()` — the promise-cached sql.js loader (identical in both).
- `OverlayKit.ico(name, size, color?)` — inline-SVG renderer over `window.ICONS` (the same
  24×24 viewBox/stroke-2/round-caps contract pill_builder uses). THE one place icons render
  in these overlays.
- Both `erp_picker.js` and `migrate_showme.js` consume the kit; their per-overlay rendering
  and CSS stay theirs (ep-*/ms-* class unification is NOT in scope — visual surface unchanged).
- Load order: `overlay_kit.js?v=1` before both consumers on erp.html + idempiere.html (+ any
  other page that loads erp_picker — grep first). Add to erp/sw.js PRECACHE_ASSETS.

## §K-2 — Lucide sweep on the import overlays
- `erp/icons.js` += verbatim Lucide: `check`, `x`, `lock`, `plug` (and reuse existing
  `download`). No invented paths — copy from Lucide.
- `erp_picker.js`: ERP brand marks 🟧🟣🔵🔴🟦 → clean CSS color-dot spans (brand hue kept,
  emoji rendering variance gone); header ⬇/🔌 → `ico('download')`/`ico('plug')`; 🔒 banner →
  `ico('lock')`; result-table ✓/✗ → `ico('check')`/`ico('x')` (12px, colored); Install CTA
  ⬇ → `ico('download')`.
- `migrate_showme.js`: ⬇ Download → `ico('download')`; Copied ✓ / ms-tick ✓ / MATCH ✓ /
  DIFFER ✗ → kit icons.
- Copy text untouched (the 🔒 *doctrine wording* stays; only the glyph becomes an icon).

## §K-3 — Install becomes a World-history dot
The W timeline records BUILDING_OPEN and doc moments but NOT the largest state change a user
can make — a tenant becoming resident. In `idempiere.html installShard()`, on the persisted
success path only: guarded `WholeHistory.record({page:'idempiere', kind:'op',
label:'Installed <shard> (merged N rows)'})` (skip silently when WholeHistory absent).
Install stays OUTSIDE the signed op-log (infrastructure, not a doc op — NOT Z-fold-able;
that asymmetry is deliberate and documented here).

## §K-4 — History-lane hygiene (from the review)
- (a) `common/history_bar.js`: DELETE the dead detent-sound remnants (`_DETENT_HZ` /
  `_detentTick`) — orphaned since the knob was scrapped in v633. Verify zero call sites
  first; bump `?v=` at every referencing page; viewer sw bump if a viewer page references it.
- (b) `erp/crud_overlay.js`: `foldBackDocOp`/`foldForwardDocOp` RETURN success boolean
  (today they log but return undefined — callers can't tell if the sidecar was absent).
  Additive: callers that ignore the return are unaffected.
- (c) `erp/idmp_history.js`: warn once (`§IDMP-HIST no-restoreFn`) when a moment is clicked
  but the host never called `registerRestore()` — today it navigates silently into nothing.
- DEFERRED (named, not parked): `_combine()` JSON.stringify equality (viewer) — stamps come
  from one producer so key order is stable in practice; no observed failure; fix when
  cross-branch combine gets real use.

## §K-5 — Stale-tree guard (this repo)
A 21-commit-stale `~/bim-ootb` checkout fooled a review agent this session into reporting
shipped code (idmpInstallShard, 13-idempiere.db, both install witnesses) as missing.
Add one line to bim-compiler CLAUDE.md Session Startup: fetch + ff `~/bim-ootb` main (clean
tree only) before reading it as canon.

## Witnesses (each names the issue it proves/disproves)
- **W-OVERLAY-KIT** `erp/tests/poc_overlay_kit.js` — PROVES "two overlay author-styles" is
  closed: served worktree; ErpPicker.open (both modes) + MigrateShowMe.open render; kit
  present + consumed (no local `_ensureSql`/`_el` remain — static grep step in-test);
  emoji-range scan of the rendered ep-*/ms-* subtrees = ZERO glyphs. §OVERLAY-KIT PASS.
- **W-INSTALL-DOT** `erp/tests/poc_install_histdot.js` — PROVES "install leaves no history
  trace": dialog-drive install (poc_install_persist skeleton) → persisted=Y → `bim.docHistory`
  gains the Installed entry (§WHOLE-REC page=idempiere) → W overlay shows it.
- **W-FOLD-RET** — extend `erp/tests/poc_a_grail.js` (additive §FOLD-RET asserts):
  `crudFoldBack` returns true on success / false when sidecar absent.
- **Regressions, all green before push**: poc_install_persist · poc_install_idmp ·
  poc_client_switcher · common/tests/poc_whole_deeplink · poc_a_grail (+ viewer
  poc_z_events/poc_viewer_sessions IF history_bar.js touched a viewer-referenced path).

## Deploy
Worktree off fresh origin/main → implement → run every witness, READ each log →
erp/sw.js CACHE_VERSION v656→v657 (+ overlay_kit.js precache, ?v= bumps:
erp_picker 27→28 / 2→3, migrate_showme 22→23, icons erp 25→26 / idmp 4→5) →
viewer/sw.js bump ONLY if a viewer-fetched file changed → ONE PR, auto-merge squash,
verify it lands (PR #138 orphan trap). sw.js conflict: keep both hunks, higher version.

# DONE (append witnesses + § evidence here)

**ALL ✅ 2026-06-12 — bim-ootb PR #278 (feat/consistency-finish), erp sw v658 / viewer sw v646.**
(v657 was claimed by #277 mid-flight → sync-merge per the conflict-magnet rule: both notes kept, mine re-banded v658.)

- §K-1 ✅ NEW `erp/overlay_kit.js` (precached; `window.OverlayKit` = el/ensureSql/ico). Both overlays consume it;
  zero local `_el`/`_ensureSql` remain (static grep in-witness). `§OVERLAY-KIT sql=reused` — kit picks up the page's
  booted `window.SQL`, one WASM init per page.
- §K-2 ✅ Lucide sweep. icons.js +check/xmark/lock/plug/chevronLeft (verbatim Lucide; circleHelp precedent — ERP-only,
  not panels.js). Brand emoji→CSS color dots; ⬇/🔌/🔒/✓/✗/⧉/←/▶/●/⬑/📱 all replaced; step-done CSS `content:"✓"`→
  data-URI check. Script order fix: idempiere.html loads icons+kit BEFORE migrate_showme (STEPS render at module load);
  duplicate icons.js tag dropped. Witness **W-OVERLAY-KIT** (`erp/tests/poc_overlay_kit.js`, 16 asserts): glyph-scan
  `glyphs=0` on picker grid / iDempiere install-tenant / Odoo staged / fold-result + all 5 ShowMe steps.
- §K-3 ✅ `installShard()` persisted path → `WholeHistory.record` dot. Witness **W-INSTALL-DOT**
  (`erp/tests/poc_install_histdot.js`): `§WHOLE-REC page=idempiere kind=op label="Installed 12-odoo.db (1068 rows,
  clients 1→3)"` + entry in `bim.docHistory` + §C kernel-op-lines=none (mirror only, outside the signed op-log, as spec'd).
  Test-harness note: the witness server must serve `/common/*` from the sibling dir (deployment layout) or
  whole_history.js 404s — fixed in-witness, not a code bug.
- §K-4a ✅ detent remnants deleted from `common/history_bar.js` (grep detent==0); viewer.html ?v=5, viewer sw v646.
  Regressions W-Z-EVENTS + W-VIEWER-SESSION ALL PASS.
  §K-4b ✅ fold fns return **Promise<boolean>** (sidecar opens async — a sync bool would be invented); `§FOLD-BACK/-FORWARD … ok=Y|N`.
  Witness **W-FOLD-RET** (`erp/tests/poc_fold_ret.js`): both branches proven (fwd ok=Y · back-with-nothing-to-undo ok=N).
  §K-4c ✅ warn-once `§IDMP-HIST no-restoreFn`. DEFERRED as spec'd: `_combine()` stringify-equality (named, not parked).
- §K-5 ✅ CLAUDE.md Session Startup step 0: ff-sync `~/bim-ootb` before reading it as canon.
- Regressions all green post-merge: W-INSTALL-PERSIST · W-INSTALL-IDMP · W-CLIENT-SWITCHER · W-FOLD-BACK (a_grail) ·
  W-WHOLE-DEEPLINK · sw-precache audit 102/0. Witness logs: `erp/tests/poc_{overlay_kit,install_histdot,fold_ret}.log`
  (repo-ignored `*.log` per .gitignore — evidence lives in the run, scripts in the repo).
