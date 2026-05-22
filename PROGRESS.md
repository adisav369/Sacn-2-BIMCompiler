# PROGRESS — Current Development State

> **Rule:** PROGRESS.md is a thin status file. No specs here — specs live in `docs/` and `prompts/`. Keep this file under 80 lines.

## Current State

**Gate:** `./scripts/run_RosettaStones.sh` — S190 fleet: 116/157 PASS, 4 ALL GREEN (BR,MO,RL,WI). 21 buildings. 9-gate system.

| PFX | EL | GATES | Notes |
|-----|----|-------|-------|
| BR | 33 | 9/9 | ALL GREEN |
| MO | 2791 | 9/9 | ALL GREEN |
| RL | 1 | 9/9 | ALL GREEN |
| WI | 1 | 9/9 | ALL GREEN |
| DX | 1169 | 8/9 | MetadataMissing (IfcOpeningElement) |
| SH | 65 | 8/9 | MetadataMissing (generative MEP) |
| TE | 48428 | 8/10 | C8 mesh diversity, GEO no pairs (federated) |

**Pipeline:** 11 stages. 77 verbs. 7403 products (ERP.db). 4-DB architecture.

## Active Work — Browser BIM OOTB

**S266 IN PROGRESS (2026-05-22): New From Reference — Doc Pill + JS BOM + Design Canvas + RouteWalker. See `docs/NEW_FROM_REFERENCE.md`.**
  - Red Pill icon replaces TM in main pill → swaps to 9-icon red glass Doc pill (Home/Grid/TM/Next/Disc/Open/Save/UBBL/Rosetta)
  - `bom_extract.js`: JS BOM extractor from elements_meta. Groups storey→discipline→ifc_class, envelope, storey heights, cadence. STD_MEP fallback. Cached in IndexedDB.
  - `doc_canvas.js` HARDENED: §6.4 BUG fixed (envelope-only step zero: 2+2 lines, no cadence). GRID_STRATEGY table (23 IFC classes → grid behavior). `_ifcToThree()` DRY coord transform. Discipline-scoped Next. Rosetta Stone template lines (gold/grey, drag-to-place instances). kernel_ops wired: GRID_ADD, GRID_CALIBRATE, DISC_SWITCH.
  - **S266b (2026-05-22):** BatchedMesh+InstancedMesh materialize fix. HUD grid bays accordion. Wall dedup + 30-line cap. Grid lines dashed, depthTest off, extend 8m. Rosetta templates 14m out.
  - **S266c (2026-05-22):** Grid rethink — AUTO-GRID REMOVED. User-initiated grid lines via double-click (add/remove toggle). Envelope tightened to structural-only AABB (excludes proxy/site). Design Gantt ordering: CLASS_PRIORITY (walls→openings→proxy, not alphabetical). Timeline slider (◀ scrubber ▶) with prevPhase/scrubToPhase. Grid select-then-drag (click→highlight→constrained drag). Both-end bubbles on grid lines.
  - `route_walker.js` NEW: JS port of Java RouteWalker (395→250 lines). Pattern applier for MEP.
  - `panels.js`: 8 discipline icons + discipline selector popup.
  - **Tests:** 54/54 PASS — `test_doc_canvas.js` updated for handleElementPick (user-pick, not auto-grid).
  - **Spec updated:** §17.7 Rotation. §17.9 Grid Rethink + BOM Completion Triage (A–I). §17.9H Print-Ready Mode.
  - **DECISION: 100% browser.** Java verb expanders are pure math (~200 lines) — port to JS like route_walker.js. BOM.db already exists for fleet (504KB–8MB), lazy-fetch on Red Pill. IFC Drop: web-ifc extracts IfcRel* (3 queries, ~30 lines in import_worker.js). No Java install needed for end users.
  - Spec: `docs/NEW_FROM_REFERENCE.md` §4-6, §9, §17

**S267 DONE (2026-05-22): BOM Walker + Verb Expansion — BOM.db drives phases, meshes follow grid. SW v436.**
  - `verb_expand.js` NEW: JS port of 7 Java verb expanders (TILE/ROUTE/FRAME/CLUSTER/SPRAY/LINE/LINE_MULTI). Pure math, zero deps.
  - `bom_walker.js` NEW: JS port of BOMWalker tree traversal via sql.js. Three-way dispatch, MAX_DEPTH=20 guard.
  - `doc_canvas.js` REWRITE: `_loadPhases` walks BOM tree (no flat CLASS_PRIORITY query). `_buildEnvelope` from BOM root AABB. Nearest-delta recomposition on grid drag.
  - `panels.js`: Lazy-fetch BOM.db fallback + IndexedDB cache. Reactivate Doc canvas when BOM.db arrives async.
  - `import_worker.js` +50 lines: IfcRelVoids/Fills/Aggregates → `bom_tree` table (IFC Drop path).
  - BOM data merged into extracted DBs: SH, DX, SC, HI, TE (m_bom + m_bom_line tables appended).
  - SH_BOM.db (127KB, 14 BOMs) and DX_BOM.db (283KB, 36 BOMs) created via IFCtoBOMMain pipeline.
  - 5 BOM.db files + 5 merged extracted DBs uploaded to OCI `bim-ootb` bucket.
  - **Tests:** 106/106 PASS — test_verb_expand (20), test_bom_walker (20), test_bom_phases (12), test_doc_canvas (54).
  - **Key finding:** BOM positions are floor-relative (local). Building origin bridges to world coords. Grid envelope from BOM root AABB (23.9×24.5m) is tighter than extracted scatter (43.5×43.5m) because BOM excludes outliers.
  - **Limitation:** Grid drag uses nearest-delta (dumb shift), not verb re-expansion. Roof slides instead of extending, tiles don't recount, openings don't cascade with host wall.
  - **Timeline note:** Timeline appears tied to Doc canvas, should be tied to TimeMachine ON/OFF state (spec update, not code fix).
  - **Next session (S268):** Verb re-expansion on grid drag + parent-child cascade. Prompt: `prompts/S268_RECOMPOSE_CASCADE.md`.
  - **Deferred (S269+):** Max+Photo icons. NEW geometry generation (Java Bridge). IFC export. Z-axis grids. GPU throttle. Save/recall. Timeline↔TM binding.

**S265 Phase 5 DONE (2026-05-21): UI Aesthetics Overhaul. SW v416→v431. See `prompts/S265_UI_AESTHETICS.md`.**
  - Foundation: `A.createPanel()` factory + `.bim-panel` CSS + `ICONS` registry (24 icons) + `A.icon()` factory
  - P1 DONE: Color Palette rebuilt — 5 icon-only slider rows (palette/sun/sunDim/lightbulb/sunrise), value fades on drag
  - P6 DONE: Standalone NLP 🎤 button removed — mic merged into Find panel search bar
  - P10 DONE: Help palette — 6 entries with expandable sub-items (blue/red bar toggle), G5 mobile focus guard
  - Find panel restyled to `.bim-panel` glass — dual-purpose input (NLP + element search), context-aware chips
  - All panels + pill + HUD at 50% opacity with blur(16px)
  - G1 partially fixed (overflow init reset), G2 DONE (focus stack dedup)
  - **Next:** Find panel mic not showing (debug), P2 Section slider, P3-P4 Info+Issues restyle

**S265c DONE (2026-05-20): Material color fix + unconditional render. SW v414→v416.**
  - Removed `_spread < 0.08` threshold — IFC colors as-is, NULL-only STD_MAT fallback
  - Sunglasses slider handles grey buildings (Terminal/LTU) on demand
  - Removed `_needsRender` gate — render every frame, sliders/palette/bbox instant
  - Overflow init reset (partially fixes double-click glitch)

**S265 Phase 4 DONE (2026-05-19): HUD unification + keyboard + z-index + markDirty. SW v404→v411.**

**S265 Phase 3 IN PROGRESS (2026-05-19→21): Share refactor. SW v411→v414. Deployed to ootb-dev.**
  - share.js rewritten as `setupShare(A)` — called by main.js `_mods` like sitecam.js (eager load, not lazy)
  - Pill Share onclick → `APP.quickShare()` direct call (preserves user gesture for `navigator.share`)
  - Mobile: canvas snapshot as JPEG File + `navigator.share({files:[photo], text})` — same pattern as sitecam.js
  - Desktop (Firefox, no Web Share API): preview card with Copy Link button
  - `buildShareUrl()` captures 7 contexts in URL hash: cam, tgt, pick, storey, xray, clash, tm, tour
  - Clash context delegates to `_buildClashDeepLink` (proven working S246 function, untouched)
  - Clash text body has discipline pair, element names, storey, overlap mm, severity — same as `_shareClashSnag`
  - Hash parser in main.js restores: pick, storey, xray, tour, tm, camera on load
  - `tmGetState()` exposed from time_machine.js for share URL
  - WhatsApp/Email hardcodes removed. Old sendWhatsApp/sendEmail functions deleted.
  - Whitebox: 13 share tests PASS (anchor URL, 10 context scenarios, clash text, delegation)
  - **BUG OPEN: Receiver-side clash restore not showing on Firefox.** Sender URL is correct (verified by whitebox anchor test). The hash parser + `_flyToClash` code is identical to the S246 deep-link that works. Clash context text appears in shared message. But recipient does not see clash highlights on load.
  - **WORKING URL (user-verified, old clash snag long-press):**
    `...index.html?db=https%3A%2F%2F...HospitalGarage_extracted.db#clash=01WyKs2cnByA_VPsbZDzFL~0GjpF04mX1K8P$TdM8fU2_&st=Existing%20Garage%20-%201st%20Level&cam=-49.82,-5.49,-33.29&tgt=-51.52,-6.91,-34.99&tol=25`
  - **NEW URL (quickShare → _buildClashDeepLink, same format but not restoring):**
    `...index.html?db=https%3A%2F%2F...HospitalGarage_extracted.db#clash=0IajW5Y89BRxvKnf5AfDkQ~1itcVTZhD87QA6mzuiLzD3&st=Existing%20Garage%20-%201st%20Level&cam=-50.02,-5.17,-22.33&tgt=-51.71,-6.58,-24.02&tol=25`
  - **Next session:** Debug receiver-side restore. Both URLs parse identically (whitebox proven). Issue likely in timing, Firefox console, or _flyToClash visual rendering. Must reproduce locally — do NOT deploy-and-ask-user.

**S2D30 Grid UX Troubleshoot DONE (2026-05-10): SW v293**
  - `grid_views.js` refactored — atomic single-responsibility: `classifyMesh`, `computeCutZ`, `applyFloorClip`, `clearFloorClip`, `boostLighting`, `restoreLighting`
  - IfcRoof/IfcCovering meshes fully hidden (`visible=false`) in floor plan — roof no longer bleeds through clip plane
  - Contours: white fill/stroke on dark bg, black on light — true reverse for print. No invented colors, no artificial ribbon.
  - Band filter: original next-storey lookup + 1.5m minimum clamp (fixes SampleHouse crushed band)
  - Cost panel: variance columns (Δ Qty, Δ Vol), ✕ close button fixed (innerHTML was overwriting it)
  - Panel toggle −/+ always visible (was mobile-only). Hides all UI chrome for screenshots.
  - Dwell/bookmark/flash removed from scissors — not requested.
  - Save Cut button on scissors — only in 2D mode (gated by `isIn2DView`)
  - `saveSectionFromScissors` exposed for card save from scissors slider
  - Snap-to-structural post-cluster alignment (§GD_SNAP_ALIGN)
  - 38 specs / 391 tests / 927 expects pass
  - **Next:** S251b polish (dwell flash, Save button in 2D, grid Enter)

**S251 Keyboard Modes DONE (2026-05-10): SW v295**
  - Key sequence engine: S=Sunglasses, SC=Screenshot, G/X/F/C/M/P/4/-/+/?
  - Command palette: ? key or 🛟 button, search filter, Report Bug + Documentation links
  - Panel focus: Tab/Shift+Tab cycles, blue glow, auto-expand collapsed, focus stack (Esc pops)
  - ListKeyNav: arrows, Shift+range, Ctrl+Space toggle, Ctrl+A, typeahead, slider step, PageUp/Down
  - Multi-select: storeys (show multiple), disciplines (hide unselected), clash list (red spheres + bbox frame)
  - Mutual exclusion: 2D↔Clash/Measure blocked in each other's mode
  - Static panel init: section/sunglasses/toolbar registered at page load
  - Zombie card fix: _noauto flag prevents autoCreateCards after user clears all
  - Title fixes: "Grid Dimensions"→"Plan Grid", Z ↗→Z ⊥, sunglasses × button
  - Mobile: zero impact (all keyboard paths guarded by _isMobile)
  - 197 tests (90 logic + 107 wiring), 40 specs / 407 tests / 1007 expects
  - **Open:** BUG-1 dwell flash, BUG-2 Save button in 2D, BUG-4 grid Enter → see `prompts/S251b_keyboard_polish.md`

**S2D31 Card-First View Model DONE (2026-05-10): SW v296**
  - Card = one SQL (queryStoreyGuids) → one scene pass (hide/fade/retain/clip) → contours
  - IfcCovering = wall/floor tiles, NOT roof — removed from HIDE_IN_FLOOR
  - `FADE_IN_FLOOR`: IfcSlab/IfcPlate → opacity 0.08
  - `autoCreateCards()`: door-count ranking (was lowest-z → picked basements, improved 19 buildings)
  - Save button always on when scissors ON (was wrongly gated)
  - 11 bugs found + fixed by CTFL analysis (state completeness, boundary, data flow)
  - Contour overlay meshes skipped in card pass (BUG W — was clipping 2D lines)
  - 41 specs / 417 tests / 1035 expects — all pass. Fleet: 30 buildings in 5s.
  - Deployed to ootb-dev. Tests: `specs/35-card-first-views.spec.js` + `specs/36-card-first-browser.spec.js`
  - **Next:** Browser smoke test, then `2D_024_editable_grid_lines.md` (drag highlight, hover, alignment)
  - **Unresolved 2D UX debt:** grid alignment, drag highlight, IFC popup, Terminal curtain walls, DX door arcs — see `prompts/2D_031_card_first_views.md` §Outstanding
  - **Known issues:**
    1. Terminal outer envelope walls: contour geometry may not cover curtain walls at cutZ=1.2m
    2. DX door arcs: `§DOOR_ARC_SKIP reason=no_leaf` — geometry BLOBs don't have door panels
    3. HITOS: verify GF wall visibility with current settings

**S226a DONE (2026-05-08): Localisation — rate JSONs + locale wiring + flag picker.**
  - 16 country rate JSONs (`deploy/dev/rates/`): MY, UK, US, AU, DE, FR, ES, CN, TH, JP, KR, SA, BR, ID, ZA, BD.
  - Each: 50 IFC materials, 10 trades, 6 equipment, full sequence/work_packages/provisions — all in native currency.
  - `LOCALE_RATE_MAP` in rates.js — auto-loads correct rate JSON per locale.
  - `_syncCur()` fix — CUR/CUR2/CUR_RATE as `var` so locale overrides work at render time.
  - boq_charts.html: BOQ table headers, summary footer, chart axis/legend labels all `_TRL.*`.
  - mep_report.html: all labels translated, charts-first layout, `initRateTemplate()` locale-aware, waits for `trl-ready`.
  - 🏠 Home + 🌐 Flag buttons on boq_charts, mep_report, clash_report, 2d.
  - `locale_loader.js` added to mep_report + clash_report.
  - Gantt chart height dynamic (scales with bar count).
  - Test: `26-locale-currency.spec.js` — 6/6 PASS.
  - **Next:** Phase 4 — translate ~30 remaining hardcoded strings in viewer JS modules (measure.js, city.js, import.js, main.js, tools.js, panels.js). See `prompts/S226_localisation.md` §Phase 4.

**S250b DONE (2026-05-07): Scissors-driven adaptive grids (2D_025 D1).**
  - `grid_scissors.js`: new module — 3-axis cut detection, debounced slider, dispose/restore lifecycle.
  - `grid_dims.js`: `detectGridsAtPlane(db, cutZ)` + hoisted filter/thin, sequential relabelling, IfcBeam/IfcMember.
  - `section_cut.js`: `lookupGeometry` fallback for BLOB-only DBs (SampleHouse schema).
  - `grid_door_arcs.js`: `extractLeafAxis` — real closed-polygon contours, bbox-based leaf detection. 3 arcs on SH verified (852mm double, 726mm singles).
  - `tools.js`: `localClippingEnabled` fix (pre-existing bug), slider/off callbacks.
  - Wall contour thickness from real mesh: 290mm exterior, 95mm interior (SH verified).
  - 114/114 grid module tests pass.
  - **Next:** D1 fine-tuning (stair symbols, roof removal, opening dims), then D2 (save section), D3 (print sheet).

**Spatial ERP P0-P2 DONE (2026-05-13): Core engine + registry + handlers — 79/79 tests.**
  - `deploy/dev/doc_engine.js` (253 lines): 6 tables (§3.1), StateMachine (5 states, 4 events), JournalEngine (rule-based auto-post on COMPLETED).
  - `deploy/dev/category_loader.js` (100 lines): getCategory, listCategories, renderLabel.
  - `deploy/dev/construction_seed.sql`: 8 containers, 2 docs, 2 lines, 4 categories, 8 metadata keys.
  - `deploy/dev/handlers/construction.js` (200 lines): 7 handlers — screenLead, planFAR, submitApproval, approve, reject, generateBOQ, closeLead.
  - `deploy/dev/kernel_ops.js`: +user_tag column (§2.3).
  - `SYSNOVA/index.html`: "ERP — GOD MODE" link in footer → `sandbox/erp.html`.
  - `deploy/dev/tests/test_doc_engine.js`: 79/79 tests — P0 core + P1 seed/registry + P2 full lead lifecycle.
  - **Next:** P3 — erp.html + swipe.js + role_band.js (UI layer). Keep offline-first (sw.js precache).

**S246b IN PROGRESS (2026-05-06): WASM/SW/panel hardening + local-first libs.**
  - Vendor libs (Three.js, OrbitControls, sql-wasm, SheetJS) localised to `lib/` in ootb-dev — single origin, CDN fallback. ootb-live stays CDN-only for A/B comparison.
  - SW v254: cache key strips `?v=N` (was causing cache miss → offline.html served as JS → initViewer undefined). `.js` fallback returns 503 not offline.html.
  - R-tree + indexes built eagerly after DB loads (was lazy on first clash open — caused matrix stall on large buildings).
  - `_countClashesRtree` rewritten as single SQL R-tree join (was N queries per discA element).
  - Clash snag: direct `drawImage` from WebGL canvas (was `toBlob→Image` roundtrip ~500ms → now ~5ms). `preserveDrawingBuffer` enabled.
  - Long-press: timer/flag cleaned on measure toggle off. Info card auto-dismisses instead of blocking.
  - Panel touch-through: `pointerdown.stopPropagation` on all static panels.
  - Swipe-hide exits measure mode. Swipe-show clears `collapsed` class.
  - Clash snag routing: Share/Save → clash-specific save with both GUIDs, overlap, deep-link.
  - Issues list filtered to active building.
  - 11 code quality fixes from codebase audit (setup guards, onerror on scripts, IDB error handling, etc.)
  - **Still investigating:** panel touch-through may still seep on some mobile browsers. Large building clash matrix may still have initial delay during R-tree population. InitViewer undefined may recur if SW cache cycle needs 2 reloads.
  - **Next session:** continue troubleshooting panel event propagation, verify R-tree eager build timing on large buildings, test SW v254 cache key fix across browsers.

**S246 DONE (2026-05-04): Clash Snag + R-tree perf + full-mesh LOD.**
  - Snag: long-press clash row → JPEG capture (async toBlob) → metadata strip (severity, GPS, timestamp) → freehand annotation → share via Web Share API + deep-link URL.
  - Deep-link: `#clash=guidA~guidB&cam=x,y,z&tgt=...` — recipient opens, 2s cinematic fly-to, red/orange highlights. Desktop + mobile.
  - Deep-link in Issues panel: "Fly to clash" (in-viewer, no reload) + Share button.
  - R-tree perf: pre-load discB into JS map (halves SQL calls), progressive loader + COUNT + EXISTS all R-tree.
  - SW v251, WASM preload (eliminates cold-start InitViewer), updateHash guards #clash= hash.
  - Home: 🌐 flag button navigates to landing page. Report: standards references, all-pair R-tree counts.
  - Accept propagation: Accepted status applies to all same IFC class pairs in session cache.
  - DLOD disabled: S232 InstancedMesh batching sufficient, full scene stays during clash analysis.
  - Clash viz: discipline-colored full mesh (25% opacity) + bright red/orange clipped overlap (depthWrite:false).
  - Report: R-tree counts across all pairs (envelope skip), max_report in clash_rules.json, standards references.
  - Dev banner baked into deploy/dev/index.html, absent from deploy/live/.

**S245c DONE (2026-05-04): R-tree + Clash Performance & UX overhaul.**
  - WASM swap: sql.js → rtree-sql.js@1.7.0 (CDN, SQLITE_ENABLE_RTREE). SW cache v249.
  - R-tree built async (5k batches, ~1.2s non-blocking). For S245d single-element lookups.
  - R-tree self-join O(N²) — not viable for pair-finding. All queries stay bbox arithmetic.
  - Matrix bg check: discipline envelope overlap (one GROUP BY, instant, accurate).
  - Cell click: LIMIT 30 storey-scoped (auto-picks top 5 storeys, avoids full-building N²).
  - Clash viz: clipped actual mesh (red+blue) at overlap zone. Camera targets overlap centre.
  - Overlap clipping padded to 0.3m min visibility. Row highlight on selected clash.
  - Matrix persists on cell click. Measure dots disabled while panels open.
  - Info card X close fixed. No auto-dismiss on canvas click.
  - Status: 🟡RVW 🟢SLV ⚪ACC with live counts. Right-click/long-press/double-click toggle.
  - clash_rules.json: 6→12 rules (added ELEC/FP/ACMV pairs, removed dead HVAC/PLUMB).
  - Right-click empty space = whole-building info card (all storeys, all disciplines).
  - HTML report: matrix snapshot, stat cards, 6 Chart.js charts (severity, status, disc pair,
    class pair, discipline risk radar, top offenders), matrix summary table, editable action sheet.
  - CSV export from HTML report (includes editable fields). Sorted by severity, capped at 100.
  - **Next:** S245d — see `prompts/S245c_rtree_clash.md` §S245d. Key: query heat problem.

**S245b DONE (2026-05-04): Clash Detection + Measure UX overhaul.**
  - Measure: tap-same-dot for area (replaces double-click), DB-backed info card, adaptive status text.
  - Clash Matrix: visual grid with 3D CSS spheres (pulsing grey → green/orange/red).
  - Lazy loading: matrix instant, async sampled check per pair, full query only on click.
  - Click cell → LIMIT 30 paginated clash list with tolerance slider (1-100mm).
  - Click clash row → fly-to + clipped actual meshes at overlap zone (depthTest:false).
  - Review status cycle: Reviewed → Resolved → Accepted (localStorage persisted).
  - Excel export from matrix title bar. clash_rules.json: 6 discipline pair rules.
  - Glass UI (backdrop-filter blur), draggable panels, constant-size measure dots.
  - Runtime SQL indexes (discipline, storey, center_x). Performance: skip dimming >3k meshes.

**S244 DONE (2026-05-03): Sunglasses — material contrast slider + theme toggle.**
  - 🕶 button replaces ☼: click = reverse background (light/dark), opens slider panel.
  - Slider (0–100, 10 zones): recolors all meshes by IFC class/storey/discipline with 10 strategies.
    Warm pastels → cool pastels → earth tones → storey warm/cool → discipline → zebra → mono → random → HARD.
  - Each IFC class gets unique color via golden-angle hue spacing. Largest classes get most contrasting slots.
  - Near-white materials (RGB > 0.85) auto-tamed in `_getMaterial` at streaming time.
  - `ifcClass` stored on mesh/instanced userData for grouping. Zero perf cost — just material swaps.
  - Deployed to `bim-ootb-live/sandbox/`. Proven on Terminal canteen (IfcSlab vs IfcFurniture contrast).

**S242 DONE (2026-05-03): Single-DB deployment, IFC bbox placeholders, instanced IFC export.**
  - Viewer: single DB only (`A.libDb = A.db`), no library fetch. Config: `LIB_URL` removed.
  - Bbox placeholders: use IFC `bbox_x/y/z` from `element_transforms` (not fixed cubes).
  - IFC export: IfcMappedItem instancing (geometry once per hash, elements reference via map).
    Batched geometry loading avoids OOM on large buildings (122K elements proven).
  - VALID_DISCS expanded: +AIR, DUCT, HVAC, MECH, FIRE, SPR, GAS, LIFT, CONV, etc.
  - All 22 buildings re-extracted as single DBs, deployed to `bim-ootb-live/buildings/`.
  - DB queryable immediately on load (IndexedDB cached) — bbox enables 4D/5D/clash without meshing.

**S241 DONE (2026-05-02): Drop IFC merge + disc from filename + Node.js extractor.**
  - Multi-disc merge: combines elements into one DB (not version stacking). Building name normalized.
  - Disc from filename: aliases (ELE→ELEC, FIRE→FP, MECH→ACMV, etc.). Landing-side override.
  - Variance only on "revised" filename. 10-col transforms fix (bbox columns).
  - Node.js extractor: `scripts/extractIFC2DB.js --disc HEAT`. All 25 OCI buildings re-extracted.
  - Proven: LTU SAN+VOID merge, all UNMERGED/ filenames.
  - Specs: `prompts/DropIFCMergeNoVarianceDISC.md`, `docs/SQLite3D_Schema.md`

**S239 DONE (2026-05-01): Deep refactor — `full` branch.**
  - helpers.js, 18 traverse→0, 31 db.exec→dbQuery, 4 SQL injections fixed
  - Lazy-load navigate/wizard, sw.js versioning, minify script (44% reduction)
  - `deploy/dev/` is canonical source. OCI full = minified dev.
  - Remaining: wizard.js traversals, measure.js traverse (low priority)

**S236 DONE: 2D Plans browser DXF viewer.** `deploy/dev/2d.html`, Canvas2D, dxf-parser.

**S243 DONE: Offline PWA.** SW precache, manifest, install prompt, offline/online toast. 45/45 Playwright PASS + 7/7 offline sandbox test. Mobile confirmed.

**S240 UPDATED: 4D Gantt Sync spec.** Added §0 Prelim Check (8-point audit), §0.3 Template System (rates.js-driven, user-checkable, export/import). Ready to implement.

**S233b DONE: Find & Navigate.** Indoor wayfinding. 26/26 Playwright PASS.

**S232 DONE: Mobile merge + InstancedMesh.** 95% draw call reduction on mobile.

**S228-S231 DONE: Drop Zone Multi-Format Import.** IFC/OBJ/DAE/GLB/FBX/3DS/STL.
  - Classification Wizard, IFC Export, InstancedMesh batching. 108/108 Playwright PASS.

**S225b DONE: Rates + Locale.** `rates.js`, 15 locale files.

**S222-S224 DONE: DB Refactor + Diff + VO + Versioned Cards.** Diff engine, VO Excel.

**S220 DONE: IFC Browser Import.** web-ifc WASM, IFC2x3+IFC4 proven at 122K elements.

## OCI Deployment

- Live: `bim-ootb-live` (SYSNOVA landing + viewer + single DBs). Always upload here.
- Single DB per building: `buildings/{Name}_extracted.db` (metadata + geometry + bbox).
- `deploy/sandbox/` stale (last ~S225) — not used for deploy. `deploy/dev/` is canonical.
- Deploy SOP: `deploy/OCI_UPLOAD.md`

## Earlier Work (compressed)

- **S200-S210:** BIM OOTB browser viewer, OCI deployment, BOQ charts, health checks
- **S195-S198:** Direct DB streaming (replaced Blender .blend pipeline)
- **S188-S193:** RTree, nD engine, DLOD — all Blender-era, superseded by browser viewer
- **S165-S186:** GN instances, chunked loading, cockpit UI — GN HALTED, RTree won
- **2D Layout:** Phase A closed, Java pipeline 5/5, 13/13 conformity. Browser DXF viewer (S236).
- **DAGCompiler:** S190 fleet 21 buildings. S104 IFCtoERP complete.

## Reference

- Docs site: https://red1oon.github.io/BIMCompiler/
- Academic paper: `docs/SPATIAL_COMPILATION_PAPER.md`
- OCI setup: `internal/OCI_SETUP.md`
