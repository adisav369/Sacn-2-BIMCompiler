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

## Phase 4: Palette (Sunglass) — restore coloring

**Problem:** Palette/sunglass coloring may have been broken by S277/S278 changes (night mode glow mats, material cloning, isolation dimming all touch mesh materials).

**Investigation:**
- `tools.js` lines 312-450: `_restoreSunglass`, `_recolorMesh`, `applyPalette`, `_sunglassBackups`
- Night mode `_nightGlowMats` modifies `emissive`/`emissiveIntensity` — may conflict with palette
- S277d isolation `_pickIsolated` sets `opacity: 0.15` — may overwrite palette opacity
- `_matCache` keys are `rgba|ifcClass` — palette recolor changes color but matCache key stays old

**Task:** Compare `tools.js` sunglass code against the last commit before S277c (`74b1e9a`) and restore any broken behavior. Do NOT invent — only restore what was working.

## Verification
- All §-tagged logs must appear unchanged
- Clash matrix opens and shows heatmap
- Clash list Shift+Arrow multi-select works (red spheres)
- Ctrl+click multi-select works
- Single click fly-to works
- Mobile: no composer created
- Desktop: SSAO/Outline toggle works
- Palette slider ticks 1-30 cycle through warm/cool/earth palettes
- Palette off restores original IFC colors
- Palette works after night mode toggle (on/off cycle)
- Palette works after pick isolation (click element, then adjust palette)
