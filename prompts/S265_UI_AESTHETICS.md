# ⚠ DO NOT REMOVE — Scope: S265 UI Aesthetics — Social-style minimal icons
# Read the log after every run. No inventions.

# S265: UI Aesthetics — TikTok/Facebook-style Minimal Controls

## Vision

Replace the current toolbar-heavy UI with a social-media-inspired minimal layout.
Study TikTok (vertical icon column, right side) and Facebook/Instagram (bottom bar + three-dot overflow).
BIM OOTB is a viewer first — controls should be invisible until needed.

## Design Principles

1. **Icons by the side** — vertical column, right edge, small recognizable icons (no text labels)
2. **Three-dot overflow** — corner menu opens secondary items (clash, 2D, export, settings)
3. **Share as first-class** — prominent share icon (refactors snag/clash/walk/site share into one `share.js`)
4. **Swipe/tap patterns** — mobile-first gestures, desktop gets hover tooltips
5. **HUD collapses** — building info panel auto-hides, tap to peek
6. **Dark glass** — consistent `backdrop-filter: blur(8px)` with `rgba(0,0,0,0.3)` background

## Reference: Social Media Icon Layouts

### TikTok (vertical right column)
- Profile pic (top)
- Heart (like)
- Comment bubble
- Share arrow
- Bookmark
- Three dots (more)
- All: 40px round icons, white on dark, slight shadow, no text

### Facebook/Instagram (bottom bar)
- Home, Search, Create(+), Reels, Profile
- Top-right: Messenger, Notifications (badge count)
- Three-dot / gear for settings

### What to learn
- Maximum 5-6 visible icons at any time
- Everything else behind a menu/drawer
- Icons are universally recognizable (no custom symbols)
- Active state = filled icon or accent color highlight

## Current State

Toolbar is horizontal top-right with 12+ buttons:
`Sunglasses | Measure | Clash | Scissors | 2D | Walk | Fly | Find | ⏳ | Screenshot | ? | Share`

Too many. Mobile is cluttered. Some overlap (Walk + Fly are both navigation).

## Proposed Layout

### Primary icons (always visible, vertical right column)
| Position | Icon | Action | Notes |
|----------|------|--------|-------|
| 1 | ⏳ | Time Machine | Most unique feature — top |
| 2 | 📐 | Measure | Tap for measure mode |
| 3 | 🔍 | Find/Search | Indoor wayfinding |
| 4 | 📤 | Share | System share sheet (S264 fix) — camera + element + clash state |
| 5 | ⋮ | More | Three-dot overflow menu |

### Overflow menu (three-dot opens)
| Item | Icon | Notes |
|------|------|-------|
| Clash Matrix | ⚠ | Opens clash detection panel |
| 2D Plans | 🏗 | Desktop only (S250 §1) |
| Sunglasses | 🕶 | Color studio / lighting |
| Walk Mode | 🚶 | Site walk + wall X-ray |
| Fly Tour | ✈ | Cinematic tour |
| Screenshot | 📷 | Capture current view |
| Night Mode | 🌙 | Toggle (currently in sunglasses) |
| Help | ? | Command palette |

### HUD (building info)
- Top-left, collapsed by default (just building name + element count)
- Tap to expand: storeys, disciplines, streaming progress
- Auto-collapse after 5s of no interaction

### Share refactor (absorbs S264 — S264_SHARE_FIX.md is superseded by this section)

**Problem (S264):** Share is broken in three ways:
1. WhatsApp-only — hardcoded `wa.me` instead of `navigator.share()`. User sees WhatsApp instead of system share sheet.
2. No scene state in URL — share URL is just `?db=filename.db`. Camera position, picked element, clash state NOT captured.
3. Tour not shareable — cinematic walkthrough state not in URL.

**Task 3a: navigator.share() with fallback**
- Replace `sendWhatsApp()` and `sendEmail()` with system share sheet
- `navigator.share({ title, url })` on mobile → shows all apps (WhatsApp, Telegram, FB, SMS, Email)
- Desktop fallback: `navigator.clipboard.writeText(url)` + toast
- Precedent: `excel.js` (S209, commit 30f6804c) already uses `navigator.share()` with `navigator.canShare()` guard

**Task 3b: Camera + element + clash state in share URL**
Build share URL with current scene state via hash params:
```
index.html?db=...#bld=X&cx=75&cy=79&cz=52&tx=0&ty=0&tz=0
```
- `pick=GUID` — highlighted element
- `storey=Level%201` — active storey filter
- `xray=1` — X-ray state
- `tm=N` — Time Machine cursor
- `clash=GUID1,GUID2` — clash pair
- `tour=play` — auto-start cinematic tour on load

The viewer already parses `cx/cy/cz/tx/ty/tz` from hash. Adding `pick`, `storey`, `xray`, `tm`, `tour`, `clash` requires small additions to hash parser in `main.js` or `streaming.js`.

**Task 3c: Context-aware share variants**
| Context | What's Shared | Hash |
|---|---|---|
| Default (orbiting) | Camera angle + building | `#bld=X&cx=...` |
| Element picked | Camera + highlighted element | `#bld=X&cx=...&pick=GUID` |
| Clash selected | Camera + clash pair | `#bld=X&cx=...&clash=GUID1,GUID2` |
| Tour playing | Building + auto-play tour | `#bld=X&tour=play` |
| Storey filtered | Camera + active filter | `#bld=X&cx=...&storey=Level%201` |

**§-tagged verification logs:**
- `§SHARE_URL state=camera|pick|clash|tour url=...` — what state was captured
- `§SHARE_METHOD native|clipboard` — which share path used (no more whatsapp|email)
- `§SHARE_PARSE pick=GUID storey=X tour=Y` — on load, what state was restored from hash

**Files:** `share.js` (rewrite send section), `main.js`/`streaming.js` (hash parser), `time_machine.js` (export `_tmActive`/`_tmCursor`)

## Progress

### DONE — Phase 1+2 (merged into one pass)

| What | Status | Commit |
|------|--------|--------|
| Vertical icon pill (6 icons: TM, Measure, Find, Share, Help, More) | Done | 2dad218f |
| Overflow grid (icons-only 4×4, no text labels) | Done | 2e7945cc |
| 31 Lucide SVG icons fetched and saved to `icons/lucide/` | Done | 2dad218f |
| All emoji replaced with inline Lucide SVGs | Done | 2e7945cc |
| Overflow grouped: Analysis, Navigation, Display, Export | Done | 2dad218f |
| Shadow, Background, Night moved from Palette panel to overflow | Done | 2e7945cc |
| Palette panel cleaned (sliders only) | Done | 2e7945cc |
| Help (lifebelt) in pill → opens command palette with icons + shortcuts | Done | 2e7945cc |
| Clash Matrix in overflow (direct `_loadClashRules` call) | Done | 2e7945cc |
| TM hourglass removed from overflow (`time_machine.js` no longer injects) | Done | 2e7945cc |
| Precision Cam: 👁 → feather icon | Done | 2e7945cc |
| Home: flag override blocked in `locale_loader.js`, house icon preserved | Done | 2e7945cc |
| Active state highlight on overflow open (cyan glow) | Done | 95e5b6ad |
| TM + 2D visible on mobile with "Desktop only" status message | Done | 2e7945cc |
| Mobile: 44px tap targets, bottom drawer overflow | Done | 2dad218f |
| Palette double-click fix (150ms delay) | Done | 2e7945cc |
| `backdrop-filter` removed from pill (CPU fix) | Done | 2dad218f |
| Old `#search-box` duplicate CSS removed | Done | 2dad218f |
| SW bumped v398→v404 | Done | 95e5b6ad |

### BUG — Active state cyan highlight incomplete

Overflow icons should glow cyan when their feature is active. `toggleOverflow()` in `panels.js` syncs state on open, but not all toggles update the overflow button when triggered from the pill or keyboard shortcut (e.g. Measure activated from pill doesn't highlight the Measure overflow icon until next overflow open). Each toggle function in `tools.js`/`measure.js`/`tour.js` needs to also set `.active` on its overflow button when the state changes — not just on overflow open.

### TODO — Phase 3: Share refactor (next session)

**Goal:** One Share icon in the pill. One `share.js`. Context-aware URL. Remove WhatsApp/Email hardcodes.

**Current state of share.js:**
- Still has `sendWhatsApp()` and `sendEmail()` hardcodes — REMOVE
- Still requires IndexedDB key (only works for imported buildings) — FIX
- No camera state in URL — ADD
- No `navigator.share()` — ADD

**What to do:**
1. Replace `sendWhatsApp()`/`sendEmail()` with `navigator.share()` + clipboard fallback
2. Add `buildShareUrl()` that captures camera + element + clash + TM + storey state in URL hash
3. Add hash parser on load to restore shared state (`pick`, `storey`, `xray`, `tm`, `tour`, `clash`)
4. Share pill button: if building is on OCI (`?db=http...`), share the current URL directly. If imported (IndexedDB), open the existing share sheet for Contribute flow.

**Snag/Clash share stays separate** — `measure.js` snag QR and `clash_snag.js` deep-links are their own flows at their own levels. Don't touch them.

**Files to modify:**
| File | Change |
|------|--------|
| `share.js` | Remove WhatsApp/Email, add `navigator.share()`, add `buildShareUrl()` |
| `main.js` or `streaming.js` | Parse `pick`, `storey`, `xray`, `tm`, `tour`, `clash` from URL hash on load |
| `index.html` | Share pill `onclick` already has fallback — refine after `share.js` rewrite |

### TODO — Phase 4: HUD polish (future session)

- Auto-collapse on mobile after 5s
- Storeys + disciplines inside HUD on mobile (no separate panels)
- Tap-to-peek

## DO NOT touch

- `measure.js` snag share — lives in clash flow, separate concern
- `clash_snag.js` deep-links — separate concern
- `streaming.js` — material pipeline stable
- `deploy/live/*` — production
- Storyboard/drone camera system — working well
