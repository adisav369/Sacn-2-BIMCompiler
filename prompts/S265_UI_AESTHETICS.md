# ⚠ DO NOT REMOVE — Scope: S265 UI Aesthetics — Sleek unified design
# Read the log after every run. No inventions.

# S265: UI Aesthetics — Sleek Unified Design

## Vision

All panels, popups, sliders, sub-panels, and buttons aligned to ONE design language. The icon pill (Phase 1-2) set the standard — everything else must match. Clean, minimal, icon-driven.

## Design Language

- **Container**: `border: 1px solid rgba(255,255,255,0.15); border-radius: 12px; background: rgba(10,10,30,0.85); backdrop-filter: blur(16px)`
- **Icons only**: No visible text labels. Lucide SVG 20×20, stroke-width 2, `currentColor`. Label as `title` attr (shows on hover/long-press).
- **Values**: Numeric value appears only while dragging slider, fades after 1s.
- **Active state**: `color: #4fc3f7` (cyan). Inactive: `color: #ddd`.
- **Close button**: × top-right, consistent across all panels.
- **No emoji**: Replace ALL emoji icons (🕶📸🏠🎤👤🔴📸) with Lucide SVG equivalents.
- **Draggable**: All panels via `_makeDraggable`.
- **Mobile**: 44px min tap targets, panels auto-size to viewport.

## Phase 5: Priority Order

### P1: Sunglass/Lighting Panel → Sleek Slider Panel

**Current** (`#sunglass-slider-panel`):
- Old labels: "Color Studio", "Exposure", "Sun", "Ambient", "Hemisphere"
- No glass effect, inconsistent styling, no icon-only

**Target**:
- White outline curved box, glass background
- 5 icon-only slider rows, compact vertical stack:
  | Icon | Slider | What | Current label |
  |------|--------|------|---------------|
  | Palette (Lucide) | 0-100 | Ambience/Color Studio | "Color Studio" |
  | Sun (Lucide) | 0-5.0 | Sun intensity | "Sun" |
  | Aperture (Lucide) | 0.1-3.0 | Exposure | "Exposure" |
  | Circle (Lucide) | 0-2.0 | Ambient | "Ambient" |
  | Sunset (Lucide) | 0-2.0 | Hemisphere | "Hemisphere" |
- Each row: `[icon 20px] [range input flex-grow] [value 32px]`
- Value text visible only while dragging, fades 1s after release
- × close button top-right

### P2: Section/Scissors Panel → Sleek

**Current** (`#section-slider-panel`):
- Labels, bookmark buttons with emoji, inconsistent styling

**Target**:
- Same curved box design
- Compact: `[scissors icon] [slider] [value]`
- Bookmark: small icon buttons (bookmark-plus, bookmark-x), no emoji

### P3: Info Panel → Sleek Card

**Current** (`#info-panel`):
- Old styling, emoji Snag button (📸), fixed position
- Labels: Class, Name, GUID, Building, Storey, Discipline, Material

**Target**:
- White outline curved box, glass background
- Compact key:value pairs, monospace values, truncated with ellipsis
- Snag button: camera Lucide icon, no emoji

### P4: Issues Panel → Sleek

**Current** (`#issues-panel`):
- Old button styling, emoji status 🔴

**Target**:
- White outline curved box, glass background
- Status: CSS colored dots, not emoji
- Consistent button styling matching overflow grid

### P5: HUD → Sleek

**Current** (`#hud`):
- Fixed top-left, accordion sections

**Target**:
- White outline curved box, glass background
- Icon-driven accordion headers (storey icon, disc icon)
- Disc bars match new color scheme

### P6: Find + Walk → Merge into Overflow

**Current**:
- `#walk-mode-btn`: standalone emoji button 🚶, top-right, CSS 162
- `#nlp-btn`: standalone emoji button 🎤, center-top, purple background

**Target**:
- Remove standalone Walk and Voice buttons from HTML
- Walk + Voice already in overflow grid — ensure they work without standalone buttons
- OR: merge into Search panel when Find icon tapped

### P7: Sub-panels (clash matrix, clash list, cost panel, command palette)

**Current**: Each has its own styling, inconsistent borders, backgrounds, font sizes.

**Target**: All sub-panels use the same curved box design. Consistent font: system-ui, 12px body, 11px secondary.

### P8: Status Bar

**Current** (`#status`): Fixed bottom-center, basic styling.

**Target**: Subtle, same glass background, compact.

### P9: Walk Anchor Prompt + Site Camera Overlay

**Current**: Old modal styling with emoji.

**Target**: Same glass card design, Lucide icons.

### P10: Command Palette (Help/F1) — Complete Panel Directory

**Current**: `showCommandPalette()` in `scene.js` — lists main shortcuts only.

**Target**: Reorganised as a full directory of ALL panels and features:
- **Primary** (pill icons): Time Machine, Measure, Find, Share, Help
- **Display** (overflow): Section Cut, X-Ray, Wireframe, Palette, Shadow, Background, Night
- **Navigation** (overflow): Fly Tour, Walk Mode, Voice Search, Precision Cam
- **Analysis** (overflow): Clash Matrix, 2D Plans, Issues, Export
- **Sub-panels**: Sunglass sliders, Section slider, Scissors bookmarks, Grid panel, Cost panel
- Each row: `[Lucide icon] [Name] [Shortcut key if any, right-aligned, muted]`
- Grouped with section headers (same categories as overflow grid)
- Search/filter input at top — but NO auto-focus on mobile (G5 fix)
- Links at bottom: Report Bug, Documentation

## Trivial Glitches (fix during redesign)

### G1: Overflow menu needs two clicks on first open
- `toggleOverflow()` in `panels.js:524` — class state stale on first click
- First click: `§UI_OVERFLOW open` then immediately `§UI_OVERFLOW close`
- Fix during redesign: ensure clean init or switch to explicit show/hide

### G2: Focus stack grows unbounded
- `stack=[sunglass,sunglass,sunglass,toolbar,grid,grid,toolbar,grid,grid,grid]`
- `_focusPanel` / `_blurPanel` in `scene.js:780+` — push without pop
- Fix: deduplicate stack entries or cap length

### G3: Stale saved 2D cuts persist after deletion
- `§SAVE_SECTION deleted id=1 remaining=0` but reappears on reload
- Saved sections in localStorage — deletion not persisting
- Investigate storage key and deletion path in grid_views.js / grid_scissors.js

### G4: Chrome disk cache serves stale icons
- SW version bump not clearing Chrome's disk cache
- Icons revert to old versions on browser restart
- Fix: ensure strong cache-bust on index.html

### G5: Help/Lifebelt triggers keyboard on mobile
- Command palette opens with search input focused
- On mobile this triggers the soft keyboard immediately
- Fix: don't `focus()` the search input on mobile until user taps it
- `showCommandPalette()` in `scene.js` — guard `input.focus()` with `!A._isMobile`

## Files to modify
- `deploy/dev/index.html` — all panel HTML + CSS (this is where most work is)
- `deploy/dev/tools.js` — sunglass panel logic, toggleSunglass, updateAmbience, updateLighting
- `deploy/dev/panels.js` — overflow toggle, panel init, focus stack
- `deploy/dev/scene.js` — panel registration, focus/blur, command palette
- `deploy/dev/issues.js` — issues panel toggle + render

### P11: Panel Toggle (−) — Focus Mode

**Current** (`#panel-toggle-btn`): Hides all UI chrome for screenshots.

**Target**: Smarter — when pressed, hides ALL panels/tools EXCEPT the currently active one. User gets a clutter-free view focused on one task (e.g. only the sunglass sliders visible, everything else gone). Press again to restore. This replaces the blunt "hide everything" behaviour.

- Track which panel is currently focused (`_focusedPanel` in scene.js)
- (−) hides pill, HUD, status, info, issues, overflow — keeps only `_focusedPanel` visible
- If no panel focused, hides everything (current behaviour)
- (+) restores all to previous visibility state

## Implementation: Reusable Panel Template

All panels share the same design. Instead of duplicating CSS/HTML per panel, use ONE factory function:

```javascript
// panels.js
A.createPanel = function(id, opts) {
  // opts: { title, icon, closable, draggable, content }
  // Returns a DOM element with standard styling:
  //   white outline curved box, glass bg, × close, drag handle
  //   All CSS from one class: .bim-panel
};
```

**One CSS class `.bim-panel`** in index.html covers all shared styling (border, radius, background, blur, font, close button, drag cursor). Individual panels only add their specific content.

**Benefits:**
- New panels = one `createPanel()` call + content
- Style change = one place
- Fixes (drag, close, z-index, mobile sizing) apply to all panels
- Glitch fixes (G1, G2) happen once in the template, not per-panel

**Icon registry** — same pattern for icons:

```javascript
// Standard icon button — used by pill, overflow grid, panel headers, command palette
A.icon = function(name, opts) {
  // name: Lucide icon name ('sun', 'scissors', 'palette', etc.)
  // opts: { size, title, active, onClick }
  // Returns a <button> with standard sizing, hover, active state, tooltip
  // SVG paths from a ICONS lookup table (inline, no fetch)
};
```

One `ICONS` registry holds everything about each icon in one place:

```javascript
var ICONS = {
  sun:      { svg: '<path d="M12 2v2"/>...', trl: 'ui_sun',      key: null,  desc: 'Sun intensity' },
  palette:  { svg: '<path d="M12 22a1..."/>',trl: 'ui_palette',  key: 'P',   desc: 'Color studio' },
  scissors: { svg: '<circle cx="6".../>',    trl: 'ui_section',  key: null,  desc: 'Section cut' },
  walk:     { svg: '<path .../>',            trl: 'ui_walk',     key: null,  desc: 'Walk mode' },
  // ... every icon in the app
};
```

- `svg`: inline Lucide SVG path (no fetch)
- `trl`: translation key — `locale_loader.js` uses this for i18n
- `key`: keyboard shortcut (shown in command palette, right-aligned muted)
- `desc`: English fallback description (tooltip, command palette, Help listing)

Every icon in the app — pill, overflow, panels, command palette, sliders — calls `A.icon()`. The command palette (P10) just iterates `ICONS` to build its listing. Localisation translates `trl` keys. Change anything once, applies everywhere.

**Migration:** Existing panels (`sunglass-slider-panel`, `section-slider-panel`, `info-panel`, `issues-panel`, `hud`) get wrapped or rebuilt with `createPanel()` one at a time. Keep existing IDs so all JS references still work. Inline SVGs in index.html get replaced with `A.icon()` calls during panel migration.

## DO NOT touch
- `deploy/dev/streaming.js` — material pipeline stable (§S265c, trust IFC data)
- `deploy/dev/main.js` — render loop stable (§S265c, unconditional render)
- `deploy/live/*` — production
- `measure.js` snag share — separate concern
- `clash_snag.js` deep-links — separate concern
- Icon pill (`#icon-pill`) — it's the design reference, do not change

## Done (previous phases)

### Phase 1+2: Icon Pill + Overflow (2026-05-19)
- TikTok-style vertical pill, 6 icons: TM, Measure, Find, Share, Help, More
- Overflow: 4×4 icon grid, Lucide SVGs, grouped by category
- All emoji replaced. Shadow/BG/Night moved to overflow. SW v398→v404.

### Phase 3: Share refactor (2026-05-19)
- `navigator.share()` with clipboard fallback. `buildShareUrl()` captures 7 contexts.
- Receiver side BUG OPEN: clash pair doesn't visually restore from new share URL.

### Phase 4: HUD + Keyboard (2026-05-19)
- Storey/Disc merged into HUD accordion. z-index layering fixed.
- Keyboard: P=Palette, 2=2D, T=TM, L=Fly, S=Screenshot, N=Night, B=Bg, H=Shadow, I=Issues, F1=Help.
- markDirty on Wireframe/X-Ray/Section. SW v404→v411.

### Material color fix (2026-05-20)
- Removed `_spread < 0.08` threshold. IFC colors as-is. NULL-only STD_MAT fallback.
- Sunglasses slider for grey buildings. §S265c. SW v414.

### Unconditional render (2026-05-20)
- Removed `_needsRender` gate in main.js. Every frame renders. §S265c. SW v415.

### Overflow init reset (2026-05-20)
- `overflow-open` class stripped on setupPanels init. Partially fixes G1. SW v416.

### Phase 5: Panel factory + Color Palette + Help tree + Find revamp (2026-05-21)
- Foundation: `.bim-panel` CSS, `ICONS` registry (24 icons), `A.icon()`, `A.createPanel()` factories
- P1: Color Palette — 5 icon-only slider rows (palette/sun/sunDim/lightbulb/sunrise), value fades 1s after drag
- P6: Standalone NLP 🎤 removed — mic merged into Find panel search bar
- P10: Help palette — 6 entries with expandable children (TM, Find, Section, Clash, Palette, Issues). Blue/red bar toggle, split click zones (left=expand, right=launch). G5: no mobile auto-focus.
- Find panel restyled `.bim-panel` glass — dual-purpose input (NLP queries + element search), context-aware chips from building
- All panels + pill + HUD at 50% opacity, blur(16px), unified borders
- G2 fixed: focus stack dedup. SW v416→v431.
- **BUG OPEN: Find panel mic button not rendering.** Next session: debug navigate_find.js mic wiring.

### S281: Input registry + tap/long-press standard + platform gating (2026-05-27)

Scope: the live pill is `#mobile-pill` (built by `_buildPill` from the `_actions` list in
panels.js) on BOTH desktop + mobile — the old `#icon-pill` is `display:none`. Red Pill doc-mode
(`toggleDocPill`, swaps `#icon-pill`) is a separate working "scene change" — left untouched.

- **Input registry** (`viewer/input_registry.js`, `window.InputReg`): facade over scene.js focus
  state. Live uses today: `syncActiveButtons()` (single icon-highlight authority, replaced the
  manual 7-button sync block) + `focusTop()` (fixed the `[]` double-tap focus-only-latest bug —
  it walked `_focusStack` which never holds the *current* panel).
- **Shortcut self-check** — `InputReg.checkShortcuts()` auto-runs 2.5s after load. CONSISTENT
  per-shortcut signature (matches §TAG key=val convention), one line each:
  `§SHORTCUT key=<k> status=ok|dead|inline target=<fn>` + summary
  `§SHORTCUT_AUDIT total=N ok=N inline=N dead=N deadKeys=<k,k>`. Grep `status=dead` to see exactly
  which shortcut fails, by key+target. Resolver skips keywords/object-prefixes/DOM-builtins to
  avoid false positives (e.g. `x`→`section-btn.click()` is inline-ok, not dead). `window._shortcuts`
  exposed for it. Static CI-style twin: `tests/audit_input_registry.js`.
  LIMIT (raised): verifies the target fn EXISTS/reachable — NOT that it does the right thing, so it
  would NOT catch a fn acting on a dead/hidden element (the original .→#search-box bug). Effect-level
  testing needs a Playwright keypress spec.
- **NOT-EASY-YET (one-place gap):** a shortcut still needs TWO edits — key→fn in `_shortcuts`
  (scene.js) AND the pill icon tap in `_actions` (panels.js). They can silently disagree. TRUE
  single-place authoring = each `_actions` entry declares `key:`, engine derives `_shortcuts`.
  Pending (central refactor of scene.js keydown).
- **Tap/long-press STANDARD** (reusable `_buildPill` hook: `act.hold` + `act.keepOpen`, buttons
  get stable id `pill-<id>`): TAP = primary toggle (`.active` highlight #4fc3f7); LONG-PRESS
  (450ms) = reveal secondary sideways via `_revealChip` (icon-only chip, no word labels).
    - Feather (Precision Camera): tap=Fine toggle, long-press=Reset chip. Restored — was knocked
      out by a stale `#search-body > div` self-mount selector; now a pill `_actions` entry.
    - Background+Screenshot pair: tap=White Background (prep), long-press=Screenshot chip.
      (Removed standalone `screenshot` pill entry.)
- **Platform gating** (`act.platform: 'mobile'|'desktop'`): wrong-platform icon greyed (opacity
  0.35, not-allowed) + tap shows status "Mobile use"/"Desktop use". Walk-on-site=mobile-only
  (GPS+orientation), Red Pill=desktop-only. Red Pill action rewired to the working
  `window.toggleDocPill` (was calling undefined `toggleDocMode`/`_enterRedPill`).
- `.` shortcut = ⋯ pill toggle (`toggleMobilePill`, not the dead `#search-box` `toggleOverflow`).
- Deployed to OCI `bim-ootb-dev` `viewer/` prefix (md5-verified; OCI edge cache makes curl 200s
  unreliable — verify by content-md5 via HEAD). sw v505. NOT yet on GitHub (branch
  `feat/input-registry-s281`, uncommitted).

HONEST FRAMING (confirmed with user): this is STILL icon-to-icon handling — each icon is its own
_actions entry, _actions and _shortcuts remain separate lists. Nothing was fundamentally
re-architected. What improved is MAINTAINABILITY + self-checking, not the model: behaviours are now
declarative tokens on the entry (platform:/hold:/keepOpen:), highlight has one authority
(syncActiveButtons), and the shortcut audit self-reports the whole map + breakage. Audit timing fixed:
poll for window._shortcuts (exported late inside async setupScene, gated on renderer+DB init) instead
of a blind 2.5s timer that raced the async setup and reported a false total=0 on DB loads.
Audit confirms targets RESOLVE (fn exists/reachable) — NOT that pressing produces the right effect.
Press-time layer: _fireShortcut wraps main dispatch → §SHORTCUT_FIRE key=X before, §SHORTCUT_FAIL
key=X error=... if it throws (loud, without killing keydown). POC scope: only main _dispatchSeq path
wrapped, not all 4 call sites.

VISION (not built): InputReg manages multiple named PILL SETS (main, doc, future) — doc-mode
becomes a registry pill-set rather than an innerHTML swap. Connect Red Pill as-is for now;
migrate to pill-set after full testing. THAT step (plus declaring key: on each entry, #15) is what
would make it genuinely NOT icon-to-icon anymore.
DONE (consolidation built this session): Measure+Clash (tap=Measure, long-press=Clash chip →
reuses 'c' handler; restores pill access removed in 726fe77). Background+Screenshot (tap=White
BG, long-press=Screenshot chip; removed standalone screenshot entry).
BACKLOG (consolidation, not built): Section, Palette(=legacy "Sunglass"), Night/lighting.

### BUG FIXES (2026-05-27)
- **X (Section Cut) was dead** — handler did `getElementById('section-btn').click()` but
  `section-btn` is `<button style="display:none"></button>` (empty + hidden) → no-op. Same class
  as the `.` bug. FIXED: call `A.toggleSection()` directly. Systematic grep confirmed X was the
  ONLY shortcut with the dead-button-click pattern.
- **F11 (fullscreen) not responding** — NOT our bug: browser natively intercepts F11 + Fullscreen
  API needs a user gesture. Working path exists: single-tap the `[]`/minmax button
  (panels.js:896 → toggleFullscreen). Left F11 as-is (browser-dependent).

### Input-event TEST suite (2026-05-27) — tests/specs/42-pill-shortcuts.spec.js
Per scope: registry RECEIVES/ROUTES events (keypress, panel focus, Tab, arrow) — NOT effect-level
(functions = NEXT PHASE). 4 tests assert purely on dispatcher §-signals: §SHORTCUT_FIRE/§KBD_SEQ_FIRE
(keypress), §KBD_ROUTE tab/§PANEL_TAB (Tab), §PANEL_FOCUS (focus), §KBD_ROUTE panel=X key=Arrow
(arrow traverse). Static twin: tests/audit_input_registry.js. (Deleted infer_shortcuts.js — function
tracing gave false positives, not needed.) RUN BLOCKER: harness openViewer loads /dev/index.html (HP
deploy/ layout) + Playwright not installed; run on HP tree or adapt openViewer to viewer/ paths.

### GitHub status (2026-05-27) — branch pushed, NOT yet in main
Branch `feat/input-registry-s281` (commit 95e9567) pushed to GitHub. NOT merged to main (main is
branch-protected → PR required; main HEAD still "Fix split-DB wiring T7/T8/T25/T26").
CI result (run 26506020613):
- ✅ fast-checks PASSED (syntax, audits, node tests — covers all S281 changes)
- ❌ e2e-tests FAILED — but NOT a S281 regression. Only GP.3 "Error reporter works" failed
  (s274-golden-path.spec.js:99): waitForFunction(APP.reportError) timed out 15s. GP.1/2/4 PASSED.
  input_registry.js loaded fine in CI (HTTP 200, no §LOAD_FAIL). The missing thing is
  APP.reportError (from error_reporter.js) — pre-existing/flaky (main's own CI is mixed
  success/failure on recent runs). UNRELATED to pill/registry work.
TO PROMOTE TO MAIN: next session — fix or confirm-flaky GP.3 (error_reporter.js APP.reportError
timing on slow load), then PR feat/input-registry-s281 → main. Do NOT bypass branch protection.

PUSH AUTH NOTE: the gho_ token works for READ (ls-remote) but git rejects it for PUSH in the
stored `red1oon:TOKEN` form ("Invalid username or token"). Working push form = token as USERNAME:
`git push https://<TOKEN>@github.com/red1oon/bim-ootb.git <branch>`. (Durable fix deferred — user
weighing token-in-URL plaintext vs gh auth keyring; classifier blocked auto re-embedding.)

NEXT PHASE: effect-level testing (does the handler do the right thing), + the #15 one-place
unification (declare key: on each icon entry, derive _shortcuts), + multi-pill-set registry.
Also: connect doc-mode as a registry pill-set; build the consolidation backlog (Section, Palette,
Night); decide push-auth durable fix.
