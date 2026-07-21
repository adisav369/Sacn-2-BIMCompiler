# ⚠ DO NOT REMOVE — ONE shared About/DIY modal across landing + ERP + viewer; Install/Migrate retired
# STATUS: ✅ SHIPPED & LIVE 2026-06-16. bim-ootb PR #339 MERGED to main (09d41e2; erp sw v700, viewer sw v665).
#   Docs PUBLISHED to red1oon.github.io/BIMCompiler (ERPUserGuide §2 revamp + Spatial section + cross-link).
#   See §NEXT at the bottom for the open follow-ups.
# DOCTRINE: Spec-first · §-log first · NON-INVENT · delegate-to-install (browser never touches the source DB).
# EDIT RULE: ~/bim-ootb hook-blocked → edit in /tmp/wt-aboutbox. ERP deploy = PR→main (GH Pages). SW changelog DEPRECATED.

## WHY (user reasoning, 2026-06-16 — evolved over the session)
- Install + Migrate pills are the same picker; for real ERPs the route ignores the mode → both land on the
  staged delegate-to-install box. "Migrate" promises an in-browser transfer that never happens here → drop it.
- The old migrate_showme (5 steps) has nothing left to show → retire it.
- The landing already tells the project story (About) + has a DIY Downloader → REUSE, don't fork. Make it ONE
  shared module so any change is one spot (same ref). Mount it from landing + ERP Help pill + viewer Help.
- The README is the single "what to run" — UI never re-authors commands; you copy-paste from the README.
- Per-ERP **self-contained zips** (like the existing odoo_agent.zip): pick the ERP in the modal → download a zip
  that unzips to its own folder with its README + env props. NOT a repo reorg.
- Roadmap ERPs (SAP/Oracle/Dynamics) shown GREYED + "Under construction" (honest roadmap, no invention).
- Audio "ring" on open; Lucide icons only (feedback_pill_icon_consistency); brand BIM-blue / ERP-amber.

## BUILT SO FAR (in /tmp/wt-aboutbox)
- `common/about_diy.js` — `window.AboutDIY.open(tab?)/.openAbout()/.openDIY()/.close()`. Dark brand shell,
  segmented About/DIY tabs, Lucide feature grid, self-contained WebAudio ring, agent pills (Odoo+iDempiere live,
  SAP/Oracle/Dynamics greyed). Witnesses `§ABOUT-DIY open=Y tab=… audio=Y`, `tab=…`, `agent=…`, `diy-download os=…`.
  Visually validated (screenshots aboutdiy_about/diy.png).
- `erp/idempiere_agent.zip` — NEW bundle (migrate_agent.js + package.json[better-sqlite3] + README with env table).
  Mirrors odoo_agent.zip. Modal AGENTS → both point at zips.
- **Self-host installer FIXED** (was broken: `full` branch 404 + `BIMCompiler-full/deploy/dev` wrong folder → empty
  :8080). Now `main.zip` → `bim-ootb-main/` → serves repo ROOT → the LANDING (BIM + ERP) on localhost:8080.

## REMAINING (the wire-up — needs the visual go for the landing restyle)
1. Landing `index.html`: About link + DIY button → AboutDIY; add includes (icons.js, overlay_kit.js,
   common/about_diy.js); retire inline #about-dialog/#diy-dialog; fix/replace the broken downloadDIYScript.
2. ERP: idempiere `showme` + erp `guide` pills → AboutDIY.open(); REMOVE install+migrate pills from
   pills.json/pills_idmp.json + bindings (erp_pills.js, idmp_pills.js + §IDMP-LIFECYCLE gate); add include.
3. Viewer: wire its Help/About affordance → AboutDIY (confirm element; do NOT clobber the shortcuts palette).
4. Restyle the landing PRO to match the modal language (keep brand + content; screenshot before PR).
5. `sw.js` bump + precache common/about_diy.js + erp/idempiere_agent.zip; retire migrate_showme.js.
6. Witness (jsdom/playwright DOM) + ONE PR → main.

## ENV PROPS (sourced, for the READMEs — NON-INVENT)
- Odoo (erp/odoo_agent/README.md): ODOO_HOST=localhost · ODOO_PORT=8069 · ODOO_DB=odoodemo · ODOO_LOGIN=admin
  · ODOO_PASSWORD=admin · ODOO_SO=S00023.
- iDempiere (erp/migrate_agent.js): ERP_PG_CONTAINER=postgres · ERP_PG_DB=idempiere · ERP_PG_USER=adempiere
  · ERP_PG_SCHEMA=adempiere · ERP_OUT · ERP_TABLES · flags --masters / --list-clients.

## DOCS (separate PR, sourced, awaiting publish go)
- MigrateComparisonPaper → prominent cross-link to ERPUserGuide.
- ERPUserGuide += "Running the scripts" + "Spatial BIM→ERP: Find→Project Order (LIVE, proj_fold.js / PR#316)".
- BIMtoERP.md "Check ERP" read-chip is PAPER — do NOT advertise.

## NOTE — adjacent (NOT this work): tenant-delete trashcan already fixed on origin/main (#333, 307f015). Stale
## local screenshots only. No work needed.

## ▶ NEXT (open follow-ups, 2026-06-16 — what this session did NOT finish)
1. **Landing FULL pro-restyle** — only the header action-links were polished (→ uniform `.hlink` pill-chips,
   shipped in #339). The hero lockup, the import-zone, the feature/DIY card sections are UNTOUCHED. This wants
   a look→approve loop (it's the public home page, 2,400 lines of bespoke inline style) — do it incrementally,
   screenshot each block. `index.html` in a fresh worktree off origin/main (NOT the merged about-box branch).
2. **Viewer Help pill** now opens AboutDIY (was `showCommandPalette`). Decide: is the keyboard/gesture
   shortcuts palette lost from that pill acceptable, or re-home it (e.g. a separate entry / keep F1=palette,
   Help-pill=AboutDIY)? `viewer/panels.js:1124`.
3. **DIY self-host README as the single "what to run" for BOTH halves** — today the install script serves the
   app on :8080 (BIM+ERP) and the ERP agents are separate per-ERP zips. The user's "one README explains launch
   + migrate" idea could go further: the self-host bundle's top README could point into the agent zips. Optional.
4. **Real SAP / Oracle / Dynamics agents** — currently greyed "under construction" in the DIY box (honest
   roadmap). When an agent ships, flip `soon:false` + add its zip + a line in `AGENTS` (common/about_diy.js) —
   single spot. (Build the zip like erp/idempiere_agent/.)
5. **Stale deploy canaries** — `safe_gh_deploy.sh` §STEP6 warns `RetailScaleStory` + `glassbowl` → 404 (not a
   regression; pre-existing). Either publish those pages or prune them from the canary list (DOCS_DEPLOY_GUARD).
6. **bim-compiler working tree** has ~144 pre-existing non-doc changes (build/erp, deploy, scripts) untouched by
   this session — the user's own pending work; commit/triage separately.
7. **The real ERP roadmap spine** is unchanged: GRAND_LANE S3/J6 POST → P2 (this About/DIY lane was a tributary).

## §2026-07-22 AUDIT — user asked "does the self-host installer have a witness run log, and is it in the guide"

**No execution witness exists for the self-host installer itself.** `viewer/tests/poc_about_help_restore.js` and
`erp/tests/poc_overlay_kit.js` (the only tests touching this area) prove UI WIRING only — clicking the DIY
download button triggers a Blob download and logs `§ABOUT-DIY diy-download os=...`. Neither one, nor anything
else found in either repo, actually RUNS the generated `bim-ootb-install.sh`/`.bat` (curl the zip → extract →
`python -m http.server 8080` → confirm the landing serves). The "Self-host installer FIXED" line above (correcting
the old broken `full` branch / `BIMCompiler-full/deploy/dev` 404) was a **code-read fix, not an executed one** —
no `.log` file for this flow exists anywhere. Per this project's own Log Mandate, "fixed" here means "read and
reasoned to be correct," not "proven."

**Guide coverage — already there, but check for staleness in a sibling doc:**
- `docs/ERPUserGuide.md` §2 (line 163-164) + pill table (line 645) DO document this, briefly and accurately
  (doesn't over-claim beyond what the code does).
- `docs/AboutMore.md` §"DIY Self-Host" (lines 120-143) gives a MORE DETAILED but possibly STALE manual recipe —
  it instructs downloading from `github.com/red1oon/BIMCompiler`'s `deploy/dev/`, which reads like the OLD path
  this lane's 2026-06-16 fix replaced (corrected script pulls `bim-ootb`'s own `main.zip` instead). It also lists
  bundle contents (DXF templates, locales, `rates.js`) that look like they describe the SEPARATE "Save Offline
  Copy" (`packageLandingPage`) bundle, not the git-zip self-host installer — possibly two different downloads
  conflated in one doc section. Not confirmed either way — flagging for the next session to verify against
  current `about_diy.js` behavior before editing.

**NEXT TASK for a future session (spec, not yet built):**
1. Write an actual execution witness: either a Playwright/Node test that runs the generated script (or its
   logic directly) against a mocked/real zip fetch and asserts `localhost:8080` serves the landing page, or at
   minimum a manually-run, logged (`tee`'d) end-to-end pass on each OS this claims to support — Windows path
   especially, since `winget install Python.Python.3.12` inside the `.bat` has never been observed to run.
2. Re-verify `docs/AboutMore.md` §"DIY Self-Host" against current `about_diy.js` (`_downloadInstaller()`,
   `REPO_ZIP` constant) and fix the stale `BIMCompiler`/`deploy/dev` instructions if confirmed wrong; separate
   its "What gets installed" content into the correct one of the two flows (self-host installer vs. Save
   Offline Copy) rather than presenting them as one.

## §2026-07-22 AUDIT PART 2 — CONFIRMED BUG: self-host install breaks all 8 Modeller buildings' geometry

User asked to trace whether the self-host installer truly enables "download once (incl. CDN), then run fully
offline" — including IFC drop-import, ERP seeds, and Modeller's 8 embedded buildings. Traced end-to-end:

**Working as intended:**
- IFC drop-import (`viewer/import_worker.js`) is genuinely local-first (§S284c): loads `lib/web-ifc-api-iife.js`/
  `.wasm` from same-origin first, CDN (`unpkg.com`) only on local-load failure. `lib/` (three.js, web-ifc,
  sql.js, xlsx, chart.js — both `viewer/lib/` and `modeller/lib/`) are ORDINARY git files (confirmed via
  `git check-attr filter` — unspecified, not LFS) — survive a GitHub codeload zip intact.
- ERP seed DBs (`erp/ad_seed.db` + `12-odoo.db`/`13-idempiere.db`/`14-sap.db`/`15-oracle.db`/`16-dynamics.db`)
  are ALSO ordinary git blobs (`.gitattributes` only LFS-filters `modeller/*_geo.db` + `modeller/mesh.db`) —
  survive the zip fine, persist to IndexedDB on first load same as live.
- Some secondary CDN refs remain live (newer OrbitControls/three-mesh-bvh in `viewer/loader.js`, ERP's
  `sql-wasm-fts5`, clash-report's Chart.js tag) — by design these rely on the SW precaching them on one real
  online visit, offline after. Not a bug, but means "truly zero-network from install onward" isn't quite
  accurate for those specific features — only "online once, offline after," which matches what the user described.

**CONFIRMED BUG — `modeller/mesh.db` (115MB, THE shared geometry for all 8 Modeller buildings: SampleHouse,
Duplex, SampleCastle, HHS, Clinic, Hospital, HospitalGarage, Terminal — `modeller/str_walker_outliner.js:36-46`
`EMBED_8_ARC_BUILDINGS_MESH_DB`) is Git-LFS-tracked** (`.gitattributes`: `modeller/mesh.db filter=lfs`).
GitHub's repo-archive ZIP endpoint (`archive/refs/heads/main.zip` — exactly what `about_diy.js`'s
`_downloadInstaller()` fetches via the `REPO_ZIP` constant) does **not** resolve LFS pointers — it ships the
~130-byte pointer text, not the real binary. **Empirically confirmed**: `git show HEAD:modeller/mesh.db`
returns the literal `version https://git-lfs.github.com/spec/v1...` pointer, not mesh data. Each building's own
per-building DB (`Clinic_ARC.db` etc, `Terminal_meta.db`) IS an ordinary git blob and would come through fine —
only the SHARED geometry backing all 8 is broken. Net effect: **after running today's self-host installer, all
8 Modeller buildings load structure/metadata but have NO real mesh geometry** — silent breakage (no error
surfaced), not a crash. `modeller/sw.js` doesn't help here either — like the ERP/viewer SWs, it deliberately
skips `.db` from precache (`url.endsWith('.db') → skip SW`, relies on IndexedDB after a real fetch succeeds) —
irrelevant when the fetched file itself is a corrupt pointer from the start.

**NEXT TASK for a future session:** fix `_downloadInstaller()` (or the generated script) to pull `mesh.db`'s
real LFS content — options: (a) have the generated script run `git clone` + `git lfs pull` instead of a plain
zip download (needs git+git-lfs on the user's machine — heavier prerequisite than today's curl/unzip-only
approach), or (b) host `mesh.db` on OCI (matching this project's own DB-distribution doctrine — see
`feedback_db_change_via_sql_migration_not_binary` memory — LFS-tracked runtime data arguably belongs there
already) and have the installer/served app fetch it from there directly, independent of the git-zip. Whichever
direction, this needs an ACTUAL execution witness afterward (per Part 1's finding above — none exists today):
run the real installer, load a Modeller building, confirm real geometry renders, not just that files exist.
