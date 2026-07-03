# Note to paste to other ERP/renderer sessions — the ERP code MOVED

The ERP app now has its own folder home. If your session edits ERP files, **rebase onto
this move and check your paths.**

**What moved:** everything ERP went from `bim-ootb/viewer/` → `bim-ootb/erp/` —
`erp.html`, `idempiere.html`, `ad_charts/ad_data/ad_graph/ad_parser/ad_table_map/ad_ui.js`,
`erp_panel/erp_persist/erp_pills/erp_replay/erp_search/erp_signer.js`,
`idmp_session/menu_seed/role_band/icons.js`, `pills.json`, `initbubble.json`,
`ad_seed.db`, `aplus.png`, `redpill.png`. (git tracked these as renames — history intact.)

**Where:** branch `feat/erp-folder-home`, commit `b6ae505`, based off `feat/idempiere-login`
(so it carries the live login work). Spec: `bim-compiler/docs/ERP_FOLDER_HOME.md`.

**Check these in your branch:**
- Your edits to any `ad_*`/`erp_*`/`idmp_session.js`/`erp.html`/`idempiere.html` now live
  under `erp/`, not `viewer/`. Rebase onto `feat/erp-folder-home` (or merge it) before you
  continue, or your changes will conflict / land in the wrong copy.
- New ERP service worker `erp/sw.js` (scope `/erp/`, cache prefix `erp-ootb-`, bumped
  `v562`). `viewer/sw.js` also bumped `v562` and its purge scoped to `bim-ootb-` so the two
  don't delete each other's caches. If you add an ERP asset, add it to `erp/sw.js` precache.
- Old URLs still work: `viewer/erp.html` and `viewer/idempiere.html` are now **reroute
  stubs** → `../erp/…` (query+hash preserved). Renderer #1 is LIVE, so don't delete them.
- **Shared-infra duplicated** for a self-contained home: `pill_builder.js` (BIM keeps its
  own), `kernel_ops.js`, `manifest.json`, `lib/sql-wasm-fts5.{js,wasm}` were COPIED into
  `erp/`. These are flagged to dedupe into a `common/` later — if you edit the canonical
  `viewer/` copy of `kernel_ops.js`/`manifest.json`, mirror it into `erp/` until then.

**Not deployed.** Local commit only — PR to protected `main` pending explicit GO + a browser
smoke of `/erp/erp.html` and `/erp/idempiere.html` (pageerror=0).
