# MORPHEUS PLATE — Landing rebuild (Matrix portal entrance + IFC hub) — New-Session Card

```
# ⚠ DO NOT REMOVE
SCOPE: Rebuild the Morpheus front-door "plate" (index2.html) so it (a) opens with the binary-vortex
portal, the SAME existing rail icons shown a bit larger in the hole, and (b) absorbs the WHOLE present
landing (index.html) as a unified Buildings/IFC hub. NO new icons, NO invented UI — EXTRACT the real
present-landing features + the real PillBuilder/ICONS. This is UI iteration: build→show on localhost,
iterate with the user, DEPLOY only on their nod (GH Pages base = /bim-ootb/). NOT index.html (that's live).
LOG MANDATE: after any witness run, read the .log. Deterministic, non-invent, EXTRACT not fabricate.
DOCTRINE (LANDING_APP_SHELL_SPEC.md §12): BIM→Bonsai look-alike · ERP→iDempiere · ⋯ rail = the ONLY
proprietary chrome. Same ⋯, every page. The icons are the SAME standard Lucide set — no new glyphs.
```

## STATE (shipped this session, 2026-06-18 — do NOT redo)
- Modeller LIVE: `https://red1oon.github.io/bim-ootb/viewer/modeller.html` (PR #370). Lucide icons + authoring
  audio + Sound toggle (PR #371). Morpheus v1 wiring (PR #372): red→preload shower→⋯ rail Bonsai-tree pill→modeller.
- `index2.html` (the plate) is committed at repo root (PR #372). Current contents: Morpheus gate (`assets/2hands.jpg`,
  red/blue half-hotspots), red→orchestral swell + glow + `preloadFaces` shower, blue→graceful `#ended`, cold/warm gate,
  REAL `⋯` PillBuilder rail (`buildRail`: bonsai/worldhist/about), `launchModeller()`. `erp/icons.js` has `bonsaiTree`.
- Assets staged: `assets/2hands.jpg` (gate), `assets/round01.jpg` (the portal — source `~/Downloads/round01.jpg`,
  COMMIT it). `sphere.jpeg` was DROPPED (do not use — reverted to standard icons).

## THE ENTRANCE — Matrix portal (replaces the current reveal)
- Red pill → `assets/round01.jpg` (green-binary vortex with a dark central hole) fills the screen on black.
  Slow CSS rotate + faint pulse for life. The vortex IS the loader ("THE GLOW IS THE LOAD", §spec) — it covers a
  background cache-warm; it REPLACES the "warming your workspace" progress shower.
- The SAME existing rail icons (below) appear IN THE HOLE, **a bit larger** than rail size, glowing cyan/green with a
  soft halo so they read off the binary. They appear **one-by-one, each with an audio drop cue, as if dropping into
  the scene** (coalesce as assets finish). Keep them clearly tappable (decent hit targets). NO new icons, NO padding
  with dead tiles — **7 icons** (see table) in a centered cluster ("3×3" vibe, 7 of 9 — a ring/arc or loose grid).
- Pick one → "fall through the hole" (vortex zooms in / fades) and that icon **docks to the `⋯` corner** (bottom-right).
  That collapse-to-rail teaches where the `⋯` lives — no words. The `⋯` rail then holds the same icons.
- **Lazy-mount:** warm the cache behind the vortex so clicks are instant, but each surface MOUNTS only on its
  icon-click — especially Bonsai (do NOT spin the 22MB occt worker until the tree is clicked).

## THE ICON SET — 7 icons in the opening. SAME standard Lucide, NO invention (just larger in the hole)
| # | Icon (ICONS key) | Goes to | Notes |
|---|---|---|---|
| 1 | GPS-dot (location pin) | the Buildings/IFC HUB panel | mainstream BIM entry; largest/first in the hole + pinned in rail |
| 2 | `bonsaiTree` | `viewer/modeller.html` | author-new; lazy-mount worker on click |
| 3 | `globe` | `erp/erp.html` | ADD verbatim Lucide `globe` to icons.js. erp.html IS the globe/bubble launcher — icon matches destination |
| 4 | `lightbulb` | the docs / compare paper | already in icons.js (memo id `erpdoc`) |
| 5 | `circleHelp` (?) | `AboutDIY.open` | REPLACE the lifebuoy with (?) now (clearer). SHARED About/DIY modal (ABOUT_BOX_CONSOLIDATE.md) — do NOT invent an About card |
| 6 | `flag` | `_TRL_LOADER.openFlagPicker()` | ADD verbatim Lucide `flag` (NOT a globe — globe is ERP). language picker, `viewer/locale_loader.js` |
| 7 | `play` / `film` | watch demo (`youtu.be/hnLYNcRihzs`) | standard Lucide play/film glyph |

All 7 drop into the hole one-by-one (audio cue each). On pick: **ALL 7 dock to the `⋯` rail** (flag + watch-demo
included — no corner pins, no one-offs; the rail holds all 7). The picked one opens its surface/action.

## CONTEXT-AWARE `⋯` RAIL — the 7 are the OPENING pills ONLY (user 2026-06-18)
The 7 icons are the **launcher** set (landing/desktop context). Once INSIDE a surface, the `⋯` rail **follows context**
— it becomes THAT surface's own pill set, NOT the 7 launchers:
- **Inside BIM** (viewer/modeller) → the BIM rail: history · IFC · grid · find · share … (the viewer's existing rail).
- **Inside ERP** (`erp.html`/idempiere) → the ERP rail: share · graph · kanban · POS · Ninja · history … (FUNDAMENTAL LAW: anything not-iDempiere → `⋯`).
- **Opening/desktop** → the 7 launchers (dock here on entrance).
Doctrine "same `⋯` every page, contents = the non-native extras" — the launcher rail is just one context.
**⛔ DO NOT MESS WITH THE SUBSEQUENT/IN-SURFACE PILLS.** This session builds ONLY the opening (portal + 7 launchers
+ hub). Leave the ERP rail and the Viewer rail EXACTLY as they are — do NOT modify, fork, or "improve" them. (The
Viewer will be deprecated by Bonsai anyway, so there is zero reason to touch it.) Out of scope, full stop.

## THE BUILDINGS / IFC HUB — one panel = the present landing's body (EXTRACT, don't rebuild)
Unify "drop your own IFC" + "fetch a ready building" into ONE panel (standard recent/samples + browse pattern).
Port the REAL present-landing features from `index.html` (grounded — these exist there):
- **Import (S222):** `#import-zone` "Drop IFC or 3D file here", format detection (S228: IFC vs mesh worker route),
  `#import-progress-bar`, `#import-status`. → the drop/open-file half.
- **Fetch grids:** `#my-buildings-grid` (S220 imported) · `#instant-grid` (samples) · `#large-grid` (S224 split) ·
  `#community-grid` (S250 contributed). → the catalog half. Card→viewer flow.
- **Version mgmt (S224/S225):** Merge/New modal + Compare version picker (re-import existing project).
- **Watch demo (movie):** the YouTube link (`youtu.be/hnLYNcRihzs`) — KEEP (one of only two present-landing extras
  the user wants, 2026-06-18).
- **Document verbs in the hub, NOT the rail:** Save · Share · Export-IFC live HERE, lit only once a model is open
  (publish-boundary rule). Keeping them in the hub is what keeps the rail tiny.
- **About** → `AboutDIY.openAbout()` is the (?) Help pill — not a separate hub item.

### DROPPED from the plate (user trim 2026-06-18 — "only language + watch demo relevant")
Do NOT port these present-landing features into the plate: **City unlock** (launchCity/city-count/progress) ·
**Voice & NLP search** · **DIY/offline export** (packageLandingPage/downloadDIYScript) · **live stats** ·
**CI-status** header link. (They stay in `index.html`; the plate is leaner.)

### ⚑ NOT DROPPED — Save / Open / bulk-import / Merge → MOVE TO VIEWER PILLS, NO LANDING CARD (user 2026-06-19)
The earlier trim wrongly cut **version Merge/Compare (S224/S225)** and the chooser card carried Save/Open. User
correction: **a `.db` chooser card on the landing reads as "fishy" — DO NOT show one.** Instead these verbs live as
**pills in the Viewer `⋯` rail, lit ONLY once a model is open** (publish-boundary rule — dark/disabled before that):
- **Save pill** — icon `ICONS.save` (`erp/icons.js`, real). Writes the resulting `.db` to disk: Chromium FSA
  `showSaveFilePicker` (Path A), else plain download (Path B). Reuse `LANDING_APP_SHELL_SPEC.md §7` Save-As path +
  `§ROW_SAVEAS` log tag. NO new glyph.
- **Open pill** — icon `folderOpen` (`viewer/panels.js`, real). ONE pill folds three flows behind it (no card):
  (a) re-open a saved `.db`, (b) **bulk import "all from disc"** (S222 `#import-zone` / format detect S228), and
  (c) **Merge/New** (S224/S225 — re-import existing project, merge or new version). NO new glyph.
These are the **2 new pill icons** the user asked for. They dock in the SAME `⋯` rail (§12.2 of the spec), they are
NOT separate hub cards. Witness: cold viewer → Save/Open pills DARK; open a model → both LIGHT; Save writes a real
`.db` back; Open re-loads it + bulk-import + merge all reachable with no chooser card on the page.

## ⚑ LANGUAGE CHOOSER (flag = icon #6) — do NOT lose this (user-flagged)
The `flag` icon (#6) opens the language picker — EXTRACT the real one: present landing `#header-flag-btn` →
`_TRL_LOADER.openFlagPicker()` (`viewer/locale_loader.js`, `AVAILABLE_LOCALES`/`detectLocale`), driving the whole
`data-trl` i18n. Like the other 6, it drops into the hole and **docks to the `⋯` rail**. Do NOT reinvent the picker;
keep the `data-trl` keys.

## SMALLER FIXES (fold into the same pass)
- **DROP World History at the front door:** remove the auto-open + the two seeded BIM/ERP cards (redundant now the
  icons are the entry). `whole_history.js` stays for the surfaces — just no front-door auto-open/seed.
- **Blue pill:** keep the graceful exit; ADD a low/dull "bum" audio cue (§12.3).
- **Red/blue hover messages + the blue exit quote:** larger italics, old-English/cursive (silent-movie-intertitle)
  font — try a blackletter or formal-cursive web font; match the blue-exit `.quote` style across all three.

## EXTRACT FROM (real components — never mock)
`erp/icons.js` (ICONS) · `erp/pill_builder.js` (PillBuilder; supports `img:` and `icon:`) · `index.html` (the present
landing body — the hub source of truth) · `common/whole_history.js` · `erp/help_overlay.js` + `AboutDIY` (the shared
About). Current `index2.html` `buildRail`/`preloadFaces`/`launchModeller` are the seam to evolve.

## DISCIPLINE
- UI iteration: edit `index2.html`, serve locally (`node /tmp/serve_morpheus.js` → http://localhost:8778/), SHOW the
  user, iterate. Deploy (merge → GH Pages /bim-ootb/) ONLY on the user's nod — this is outward-facing aesthetic work.
- Witness the wiring (puppeteer + §-log, pattern in `viewer/tests/bonsai_morpheus_live.js`): cold→portal→icons drop
  in→pick docks to ⋯→surface mounts. Read the .log.
- Branch off fresh `origin/main` (index2.html + the modeller are merged). Commit `assets/round01.jpg`.
```
