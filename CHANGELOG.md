# Changelog

All notable changes to BIM OOTB (Browser-native BIM Viewer) are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Fixed
- Double-stream on walk/site exit — guard `streamBuilding()` for already-rendered buildings

---

## [S250] — 2026-05-07

### Added
- Clash report 3-column layout: Title | Compliance Standards | Matrix snapshot
- Charts auto-populate without clicking a cell — loads all pairs via R-tree
- Share Report downloads full self-contained HTML file
- Clash share: Copy URL with dialog, image-to-clipboard for WhatsApp/Email
- Share button on landing page building cards (replaces Save)
- `§BBOX_DEBUG` logging on every element pick for ongoing diagnostics
- Face-direction opacity, screen-space density filter
- Z-axis storey grids, long-press drag, wall contrast
- 2D views: print sheet, wall fill, density filter, lighting

### Changed
- Help icon: ❓ → 🛟 life buoy, moved to last toolbar position
- Clash report chart order: Discipline Pair + Risk Profile first, Matrix Summary + CSV at bottom
- Pie charts: smaller with top-aligned larger legends
- 6 docs files: `deploy/sandbox/` → `deploy/live/`/`deploy/dev/`, CDN → local-first

### Fixed
- BUG-1: BBox highlight for merged meshes — queries per-element DB data instead of group bbox
- BUG-2: Panel click seep-through on mobile — added `pointer-events: auto` to panels
- Accidental long-press during pinch-to-zoom — re-checks pointer count inside timeout
- SW `isNetworkFirst()` stripped `?v=N` — was serving all versioned JS from stale cache
- Measure/2D mutual exclusion (can't be active simultaneously)
- Tooltip hover on greyed-out buttons

## [S248–S249] — 2026-04-28

### Added
- Grid drag editing with rules-driven cascade
- S250 mobile/desktop polish spec (11 items)
- Elevation full projection with bay highlight (orange slab)
- Door arc swing direction from wall×door cross product
- DRY grid config, extracted DimChains module
- JSON-driven 2D architectural grid modules
- 83 grid view tests, 106 grid drag tests

### Fixed
- R-tree single-SQL clash query, pick-through, export async, issues fly-to
- `clearMeasures` try/catch + force-restore toolbox on measure exit
- Storey query → `elements_meta`, removed stale WASM preload
- Local-first vendor libs (Three.js, sql.js, SheetJS in `lib/`), WASM init fix

## [S247] — 2026-04-20

### Added
- 2D Grid Overlay Mode — floor plans, elevations, section cuts
- On-scene dimensions, elevation presets, wall-fallback
- Floor plan section cut with storey buttons, high-contrast lock
- Dynamic DXF generation from DB — section cut, elevation, grid, DXF export

### Fixed
- Floor plan keeps furniture, revert elevation forced theme
- Storey-aware GF/L1 section cut using `detectStoreys()`
- Ortho bubble/dim capping — zoom-aware scale clamp + density filter

## [S246] — 2026-04-14

### Added
- Clash Snag — snap + share clash with GPS, deep-link, annotated photo
- R-tree accelerated clash detection — eliminates O(n²) cross-join lag on mobile
- Clash DLOD mode — lightweight bbox cloud for mobile/desktop analysis
- Deep-link fly animation with camera precision
- Issues panel (scrollable, Excel export, clear all)

### Fixed
- Home button, SW cache versioning, hash guard
- Desktop deep-link, WASM preload reliability
- R-tree eager build deferred on mobile (memory audit)

## [S245] — 2026-04-07

### Added
- Clash detection matrix — discipline × discipline, R-tree WASM spatial index
- 12 clash rules, whole-building analysis, radar + top offender charts
- HTML clash report with matrix snapshot, 6 Chart.js visualisations
- Excel coordination template with assignment columns + tolerance sheet
- Draggable clash panels, sampled matrix check, clipped mesh overlap
- Measure: double-click area, right-click volume info, long-press mobile
- Progressive clash queries, mobile UX, COUNT per storey

### Fixed
- Envelope quick check, storey-scoped cell query, capped export
- XLSX export from memory only, sticky header, CSV fallback

## [S239] — 2026-03-25

### Added
- Lazy-load `navigate.js`/`wizard.js` on demand
- Minify script for production deployment
- Find & Navigate: indoor wayfinding, route template, 26 Playwright tests

### Changed
- Deep refactor: eliminate `scene.traverse` duplication → `collectMeshes()`
- SQL injection fixes (parameterized queries throughout)

### Fixed
- SW versioning (query-string aware caching)
- `helpers.js` crash on production OCI deployment

## [S238] — 2026-03-18

### Added
- 2D Plans: browser DXF viewer with BIMSRC xdata correlation
- Building-aware 2D sheets, full DXF set
- Annotation density config, wall hatches, furniture tags, room labels
- Grid filter, title block, scale bar, north arrow
- BOQ charts: read DB from IndexedDB cache first

### Fixed
- Grid bubbles scale, dim visibility, layer colours
- Grid/fitView coordinate mismatch — building fills canvas
- Auto-clip large buildings in 2D mode

## [S235] — 2026-03-10

### Added
- 4D/5D Excel export: pie charts, Gantt, phase-span timeline
- Rate extraction + 15 `_TRL` locale files (iDempiere pattern)

### Fixed
- Excel export leaving charts enlarged, z-index overlap
- Chart sizing restore after export

## [S231–S234] — 2026-03-03

### Added
- InstancedMesh batching — 85% draw call reduction
- Mobile merged-mesh pipeline (storey|disc|rgba buckets)
- Playwright watchdog: audit guard, test scope rules
- 70/70 E2E test suite

### Changed
- One `MeshPhongMaterial` per unique RGBA (material dedup)
- `test_all.js` → Playwright-based `deploy/dev/tests/`

## [S229–S230] — 2026-02-24

### Added
- Localisation: 18 locales (including Bengali, Banglish, Afrikaans)
- About box, flag picker, landing page i18n
- Drop Zone multi-format import: OBJ/STL → IFC DB pipeline
- IFC export from viewer
- Wizard: flip rotation, 3-step flow, save persistence

### Fixed
- Manifest fetch retry with backoff, empty building cards guard

## [S225–S228] — 2026-02-17

### Added
- Variance detection end-to-end: diff direction, added element rendering
- Merge/New UX, versioned import cards, compare flow
- Variation Order cost engine, hero landing page
- IFC import: coord fix, unit scaling, material extraction, boolean openings

### Fixed
- Chart readability: larger axis labels, NUM! fix in VO rates

## [S222–S224] — 2026-02-10

### Added
- DB refactor: incremental diff, per-building single-DB architecture
- Discipline from filename, variance only on 'revised'

### Changed
- Camera envelope for re-centred DBs

## [S210–S220] — 2026-02-01

### Added
- Site Camera: mobile inspection with GPS, BIM snapshot, WhatsApp share
- Walk Mode: step detection, compass orientation, drive-thru arrow
- NLP voice search in browser
- Sunglasses — material contrast slider with 10 coloring strategies
- Cinematic fly tour — spline flythrough, adaptive smoothing
- Bug reporter with GitHub + email, HELP FAB
- GoatCounter analytics

### Fixed
- Walk mode compass reversal, landscape panel sizing
- X-ray restore skips unsaved materials

## [S207–S209] — 2026-01-28

### Added
- Mobile UX: swipe panels, landscape layout, tap-to-toggle
- Snag-to-BIM: tap element → photo → punch list
- Drive-Thru mode, angle-of-attack movement
- `test_all.js` — 93/93 comprehensive test suite
- Excel export module (`excel.js`)

### Fixed
- Excel export on mobile (blob download, navigator.share)
- IDB version conflicts, text markup inline input

## [S200–S206] — 2026-01-25

### Added
- **BIM OOTB v1.0** — Browser-native BIM viewer + 4D/5D analytics
- Single HTML + SQLite DB + sql.js WASM + Three.js — zero install
- 9 charts matching Python Excel output (4D schedule, 5D cost, discipline breakdown)
- Per-building DB architecture with IndexedDB caching
- OCI Object Storage deployment (Always Free tier)
- City mode: 786 buildings, fly-to navigation
- Direct download viewer — no server dependency

---

_BIM OOTB: Frictionless BIM. Two DBs. One browser. Zero install._
_Copyright (c) 2025-2026 Redhuan D. Oon. MIT License._
