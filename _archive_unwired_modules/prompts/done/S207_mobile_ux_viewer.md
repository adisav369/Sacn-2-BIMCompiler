# S207 — Mobile UX & Viewer Polish
# STATUS: DONE

## Context
BIM OOTB browser viewer (`deploy/rtree_browser_demo.html`) and landing page deployed to OCI.
25 buildings live. GoatCounter analytics at `red1oon.goatcounter.com`.
Majority of visitors are on mobile phones.

## What Was Done
- **Remove +/- panel toggles**: Tap panel header to toggle. No more +/- spans.
- **GoatCounter**: Added to viewer. Landing page already had it (fetched from OCI).
- **Mobile panel layout**: z-index stack, fixed widths, `!important` overrides.
- **Landscape**: Walk/Site persist, all panels tight, separate `@media` block.
- **Swipe-to-dismiss**: Any horizontal swipe hides ALL panels (except Walk/Site). Swipe again to restore. Uses `.swipe-hidden` class with ID+class specificity to beat `display:block !important`.
- **Walk Mode compass fix**: Uses existing `_camHeading` from Site Camera handler (not a redundant second listener). Starts orientation listener if not already running.
- **Walk Mode pan reversed**: Negated azimuth to correct left/right direction.
- **Walk Mode tilt**: Beta smoothing (0.1 factor), works independently of compass.
- **Tools panel**: Removed search field, "Tools" label, flex-wrap buttons (2 lines not 3), removed Clear button, removed 'Issues' text (icon only), ruler → triangle ruler with status hint.
- **Section cut**: Multi-axis Y/X/Z buttons + close — already in S206 commit.
- **Auto-collapse timers**: Storeys 5s, Disciplines 4s on mobile.
- **Default DB**: `buildings/Duplex_extracted.db` — already correct from S205.
- **Playwright test**: `deploy/test_browser.py` — already existed from S205.
- **Building count**: Fixed 30→25 in `docs/BIM_Designer_Browser.md`.
- **Landing page**: Synced from OCI back to local (had GoatCounter, Live Stats, ordering).

## Final Mobile Layout
```
TOP-RIGHT:    [Walk] [Site]           z20
              [Tools] (tap=toggle)    z12
TOP-LEFT:     [Disc] (45vw)          z20
              [Storeys] (below Disc)  z19
RIGHT:        [Info] (on tap)         z15
BOTTOM:       [HUD] (full width)      z9
              [Status bar]            z10
```

## Landscape Layout
```
TOP-RIGHT:    [Walk] [Site]
              [Tools] (28vw)
TOP-LEFT:     [Disc] (35vw)
              [Storeys] (20vw)
BOTTOM-RIGHT: [HUD] (45vw, tappable to minimize)
```

## Lessons Learnt (CRITICAL)
1. **ID+!important beats class+!important** — `.swipe-hidden { display:none !important }` doesn't override `#disc-panel { display:block !important }`. Need `#disc-panel.swipe-hidden` selector.
2. **`window.innerWidth > 600` fails in landscape** — phone rotated = wider than 600px. Use `'ontouchstart' in window` to detect touch devices.
3. **Orientation listeners don't auto-start** — `_camHeading` was null because `_camOrientHandler` only started inside `openSiteCamera()`. Walk Mode must bootstrap its own if needed.
4. **Compass azimuth is inverted** — `(heading - trueNorth)` gives reversed pan. Need negative: `-(heading - trueNorth)`.
5. **Don't change UI without asking** — user specifies exact panel positions. State proposed layout in ASCII art, get confirmation, then change. Each wrong guess = another OCI upload + test cycle.
6. **Commit after EVERY change** — previous session lost 2 hours of uncommitted work. Small commits = safe.
7. **Inline styles beat @media CSS** — disc-panel has inline styles from JS. Must use `!important` in mobile queries.
8. **`display:none` via JS `.style` loses to CSS `display:block !important`** — use class-based hiding instead.

## Walk Mode Architecture (Final)
- `setWalkAnchor()`: Snaps camera to nearest IfcDoor facing building centre. Starts orientation.
- `startWalkOrientation()`: Bootstraps `_camOrientHandler` if not running (for compass). Adds tilt handler (beta smoothing 0.1).
- `walkModeGpsTick()`: Uses live `_camHeading` for pan, `walkLiveTilt` for pitch. No locking — compass is always live.
- GPS: Optional background, updates camera position from deltas.
- No blue dot — camera IS the user.

## Files
- Viewer: `deploy/rtree_browser_demo.html` (live on OCI)
- Landing: `deploy/landing.html` (synced from OCI)
- Charts: `deploy/boq_charts.html` (live on OCI)
- Playwright test: `deploy/test_browser.py`
- OCI docs: `deploy/OCI_UPLOAD.md`

## OCI Deploy Commands
```bash
oci os object put --bucket-name bim-ootb-full \
  --file deploy/rtree_browser_demo.html \
  --name rtree_browser_demo.html --content-type text/html --force

oci os object put --bucket-name bim-ootb-full \
  --file deploy/landing.html --name index.html \
  --content-type text/html --force

oci os object put --bucket-name bim-ootb-full \
  --file deploy/boq_charts.html \
  --name boq_charts.html --content-type text/html --force
```
