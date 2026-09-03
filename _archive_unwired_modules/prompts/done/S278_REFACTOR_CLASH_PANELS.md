# S278: Refactor Clash & Panel Wiring Out of scene.js

## Goal
scene.js (1355 lines) has grown into a dumping ground. Extract clash-list LISTNAV wiring, panel focus system, and MutationObserver clash watcher into dedicated files. measure.js (2227 lines) is also large — the clash matrix + heatmap should be a separate file.

## Current state

### scene.js owns too much
| Lines | Concern | Should live in |
|-------|---------|---------------|
| 782-910 | Clash matrix/list LISTNAV wiring + MutationObserver | `clash_panels.js` |
| 1163-1230 | `_registerPanel`, `_focusPanel`, `_focusStack` | `panels.js` (merge) |
| 311-367 | EffectComposer setup (SSAO, Outline, Output) | `effects.js` |

### measure.js owns too much
| Lines | Concern | Should live in |
|-------|---------|---------------|
| 1099-1430 | `_showClashMatrix`, envelope check, count pass, cell click | `clash_matrix.js` |
| 384-435 | `_countClashesRtree` | `clash_matrix.js` |
| 302-382 | `_queryClashesPairRtree` | keep in measure.js (core query) |
| 654-825 | `_flyToClash` | keep in measure.js (core viz) |
| 840-1050 | `_revealClashes` + list building + event handlers | keep in measure.js |

### Already extracted
- `clash_report.js` (502 lines) — HTML export
- `clash_snag.js` (298 lines) — snag capture

## Extraction plan

### Phase 1: `clash_panels.js` — extract from scene.js
**What moves:**
- MutationObserver for `_clashListDiv` (lines 782-910)
- `makeListKeyNav` call for clash matrix and clash list
- `clashListClose` function
- `_registerPanel('clash', ...)` and `_registerPanel('clashlist', ...)`

**Interface:**
- Receives `A` (app object), `_registerPanel`, `_focusPanel`, `makeListKeyNav`
- Sets `A._clashListNav` for measure.js Ctrl/Shift+click routing
- Called from scene.js after panels.js loads

**Constraint:** The MutationObserver watches `document.body` for `_clashListDiv` and `_clashMatrixDiv` appearing. This wiring must run AFTER measure.js creates those divs.

### Phase 2: `clash_matrix.js` — extract from measure.js
**What moves:**
- `_showClashMatrix` (builds grid, envelope check, async count pass)
- `_countClashesRtree` (R-tree count for heatmap)
- `_clashDiscCache` management
- Matrix cell click handler + offset reset logic

**Interface:**
- Receives `A`, `rules`, `anchorDiv`
- Uses `A.dbQuery`, `A._clashRtreeReady`, `A._queryClashesPair`, `A._revealClashes`
- Sets `A._clashMatrixDiv`, `A._clashEnvelopes`

### Phase 3: `effects.js` — extract from scene.js
**What moves:**
- EffectComposer creation (lines 311-367)
- `toggleSSAO()`, `setOutline()`
- Mobile skip guard

**Interface:**
- Receives `A`, `renderer`, `scene`, `camera`
- Sets `A._composer`, `A._ssaoPass`, `A._outlinePass`, `A._composerEnabled`

## Rules
1. **One extraction per session** — spec, extract, test, deploy
2. **Run full Playwright suite** after each extraction: `node deploy/dev/tests/audit_specs.js`
3. **No behavior change** — pure refactor, same functionality
4. **Script load order matters** — new files must load AFTER their dependencies
5. **Update viewer.html** — add `<script src="new_file.js?v=1">` in correct order
6. **Bump sw.js** CACHE_VERSION after each extraction

## Phase 4: Palette (Sunglass) — DONE (S279)

Fixed in S279: `_restoreSunglass` clears isolation before restore, `_recolorMesh` guards colorless materials and resets dimmed opacity on clones. Slider compressed to 9 combos (3 palettes × 3 groupings) + zebra/mono/gradient/hard. Base saturation boosted +0.15.

## Phase 5: Pill Icon State — fix toggle visual sync

**Problem:** Pill buttons and overflow buttons don't truthfully show selected state.

**Root cause:** Two competing visual state systems:
- Toggle functions (tools.js, measure.js, walk.js, tour.js) set inline `style.background`
- Overflow open sync (panels.js:735) uses `.classList.toggle('active')`
- Inline styles always override CSS classes → `.active` class has no effect

**Affected buttons:**
| Button | Toggle function | State indicator | Problem |
|--------|----------------|-----------------|---------|
| xray-btn | tools.js:84 | `style.background` | Inline overrides .active |
| section-btn | tools.js:153 | `style.background` | Inline overrides .active |
| sunglass-btn | tools.js:320 | `style.background` | Inline overrides .active |
| night-btn | tools.js:691 | `style.background` | No overflow sync at all |
| shadow-overflow-btn | tools.js:647 | `style.background` | Inline overrides .active |
| fly-btn | tour.js:5 | `style.background` | Inline overrides .active |
| measure-btn | measure.js:1455 | `style.background` | Inline overrides .active |
| pill-walk | walk.js:190 | `style.background` | No .active, uses inline |
| pill-tm | - | NONE | No state sync at all |

**Fix:** Convert ALL toggle functions to use `.classList.toggle('active')` instead of inline `style.background`. Remove inline style manipulation. The CSS already defines `#icon-pill button.active` and overflow button `.active` styling.

**Night button:** Missing from overflow sync (panels.js:735-741). Add `_s('night-btn', A._nightMode)`.

## Phase 6: Mobile Performance — safe improvements

**Done in S279:**
- Gate `updateMeasureLabels` + ground check behind dirty flag on mobile (main.js)
- Reuse static Vector3 in `updateMeasureLabels` (measure.js)
- Single-pass `_collectAllMeshes` (tools.js)
- Cache streaming DOM refs, skip unchanged updates (streaming.js)
- Reuse Matrix3 in mobile merge loop (streaming.js)
- Hoist flush temp objects to module scope (streaming.js)

**Remaining opportunities (safe, no behavior change):**
| What | Where | Impact |
|------|-------|--------|
| Replace 300ms clash watcher setInterval with event-driven callback | scene.js:800 | Eliminates 3.3 polls/sec |
| Cache `collectMeshes` result in hover highlight, invalidate on stream | tools.js:946 | Skip full traverse per mousemove |
| Use requestIdleCallback for clash matrix background checks | measure.js:1365-1415 | Reduce UI thread pressure |

## Verification
- All §-tagged logs must appear unchanged
- Clash matrix opens and shows heatmap
- Clash list Shift+Arrow multi-select works (red spheres)
- Ctrl+click multi-select works
- Single click fly-to works
- Mobile: no composer created
- Desktop: SSAO/Outline toggle works
- Palette slider ticks 1-100 all show distinct color schemes
- Palette off restores original IFC colors
- Palette works after night mode toggle (on/off cycle)
- Palette works after pick isolation (click element, then adjust palette)
- All pill/overflow buttons show correct active state after toggle
- Night mode: exterior surfaces visible, LEDs glow, POL lights nearby surfaces
