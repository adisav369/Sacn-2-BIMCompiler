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
