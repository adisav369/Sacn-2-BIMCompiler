# ⚠ DO NOT REMOVE — LANDING APP-SHELL REVAMP (BIM/ERP front door)
**Scope:** Revamp the GH-Pages landing into an *app-shell* — a Blender-style live canvas + pill chrome,
Morpheus cold-start gate, one chooser panel (Sample.db / Your IFC.db), About box with trust/install.
Edit target = `~/bim-ootb` (viewer/ + erp/). **bim-ootb is hook-blocked → work in a `/tmp/wt-*` worktree.**
web-ifc STAYS the in-browser IFC parser (local-first). NO server, NO upload, NO forced install.
**Read the §-log after EVERY run before any conclusion. Spec-first, witness-claim-first. Honour until DONE.**

---

## 0. PRIME DIRECTIVE FOR THIS CARD
Deterministic. Non-invent. Extract. Every behavior traces to the audit below or to existing shipped code.
No fake timers, no fake progress, no dishonest trust claims. The SLA box may only state what the
Network tab can prove. Reuse the existing pill-registry / `icons.js` / settings-editor / SFX / sw.js.

---

## 1. VERIFIED ASSET AUDIT (the cold-start fetch list — measured 2026-06-18)
Source files cited so the "online once" claim is non-invented.

### BIM (`viewer/sw.js` v666)
- **CDN, cached-on-first-use** (`viewer/sw.js:45-48`) — needed to render ANY building:
  - `cdnjs.cloudflare.com/.../three.js/r128/three.min.js`
  - `cdn.jsdelivr.net/.../three@0.128.0/.../OrbitControls.js`
  - `cdn.jsdelivr.net/.../rtree-sql.js@1.7.0/.../sql-wasm.js` + `.wasm`
  - `cdn.sheetjs.com/.../xlsx.full.min.js` (Excel/Ninja only)
- **web-ifc = LOCAL, same-origin** (`viewer/lib/web-ifc-api-iife.js` 6 MB + `web-ifc.wasm` 1.3 MB):
  `import_worker.js:20` loads local first; unpkg (`:24`) is FALLBACK only. DEFERRED → caches on first
  IFC drop (it's a 7 MB giant), OR via the offline-download button (`GET_PRECACHE` full set).
  ⇒ **Drop IFC needs NO external CDN** — only its local lib warming on first use.

### ERP (`erp/sw.js` v703)
- **CDN, cached-on-first-use** (`erp/sw.js:23-24`) — needed to fold SQLite WASM at all:
  `cdn.jsdelivr.net/.../sql.js-fts5@1.4.0/.../sql-wasm.js` + `.wasm`
- **`initbubble.json` is ALREADY auto-precached** (`erp/sw.js:114`, 2.2 KB) — init-bubble node data.

### CONCLUSION (drives the Morpheus cold gate)
The one-time online touch on a COLD cache fetches the **shared render/run libs** (THREE + OrbitControls +
rtree-sql for BIM; sql.js-fts5 for ERP) — NOT specifically Drop IFC. web-ifc is local and warms on first
drop. After first warm (or the offline button), the app runs fully offline. **Optional hardening (later
card):** self-host THREE + sql.js into `lib/` to remove the cold CDN touch entirely. NOT in this card.

---

## 2. THE FLOW (front door → destination)

```
LAND (any visit)
  └─ boot probe: is the cache COLD or WARM?   (§2.1)
        ├─ COLD (no buildings cached AND no ERP db cached, OR just Clear-Cached/first-ever)
        │     → MORPHEUS screen  (§3)  — STATIC image, ZERO network/load (consent-first)
        │         ├─ BLUE pill  → graceful "the story ends" + quiet way back (§3.3)
        │         │              (nothing was ever fetched — truly nothing happened)
        │         └─ RED pill   → unlocks AUDIO + entry SFX (§4) AND only NOW the glow/fade begins
        │                         → THE GLOW IS THE LOAD: cold fetch runs (shared libs / initbubble.json)
        │                         → "BIM or ERP?"  (§2.2)
        └─ WARM (buildings or ERP db present in cache)
              → SKIP Morpheus → straight to "BIM or ERP?"  (§2.2)

"BIM or ERP?"  (the tap here is ALSO the audio-unlock gesture on the warm path)
  ├─ BIM → app-shell canvas  (§5)  — cube + soft grid, pills, chooser
  └─ ERP → erp.html (idempiere.html) — unchanged destination
```

### 2.1 Cold/Warm detection (REAL, non-invent) — `W-SHELL-COLDWARM`
On boot, before render, probe persistence and branch:
- `caches.has(CACHE_PREFIX+VERSION)` AND/OR `cache.match()` the precached building shells.
- `indexedDB` open of the buildings store (`bim_ootb_imports`) + the ERP shard/db store → any rows?
- **COLD** = nothing cached (first-ever OR after Clear-Cache/Delete drained both). **WARM** = either present.
- Clearing cache (§7 Delete) naturally returns the app to COLD ⇒ Morpheus reappears next boot.
- §-log: `§COLDWARM state=<cold|warm> buildings=<n> erpdb=<0|1>` — witness reads this, no Playwright.

### 2.2 "BIM or ERP?" choice
- Two large targets. Tap = navigation AND (on warm path) the audio-unlock gesture (§4).
- Remember last choice (optional) but ALWAYS show the choice on cold; deep-links (`?building=`, `?client=`,
  any ERP link) BYPASS both Morpheus and the choice → straight to destination.

---

## 3. MORPHEUS SCREEN (cold only)  — `W-MORPHEUS`
Already-existing concept in ERP (`erp/tests/poc_idmp_redpill.js`, `pill_builder.js`); extend its styling.
- **Backdrop:** full-screen BLACK. **Image:** `2hands.jpg` (⛔ file not yet on disk — drop into the deploy
  tree, e.g. `erp/assets/2hands.jpg` / `viewer/assets/`), stretched to fill ~half the screen, centered.
- **CONSENT-FIRST — ZERO load while Morpheus is displayed.** No fetch, no JS warm, no network until the
  user chooses. The screen is STATIC (image + the two pills). This is what makes BLUE truly "nothing
  happened" — nothing was ever fetched.
- **Glow/fade begins ONLY on RED-pill click** (§3.4) and IS the honest loading indicator — it pulses while
  the real cold fetch runs (shared libs / `initbubble.json`), tied to actual fetch promises, NOT a timer.
  §-log: `§MORPHEUS_LOAD started_by=red asset=<url> ms=<n>` per asset; glow stops when the set resolves.
- **Two pills:** BLUE + RED (reuse pill-registry; Lucide line style; consistent with HMI).
- `prefers-reduced-motion` → no pulse (still indicate progress non-animated). Full ceremony first cold
  visit; warm = never shown.

### 3.3 BLUE pill — honest "nothing happens"
- Browser CANNOT `window.close()` a user-navigated tab — DO NOT pretend. Instead:
  fade-to-black + the Morpheus line *"the story ends … you wake up in your bed and believe whatever you
  want to believe"* → blank/goodbye state with a quiet **← back** (never trap the user).
- §-log: `§MORPHEUS_BLUE shown=1 closed=0 (browser-blocked, graceful-end rendered)`.

### 3.4 RED pill — the consent that STARTS the load
- The tap (a) unlocks audio + entry SFX (§4) AND (b) **kicks off the cold fetch** — until this click NOTHING
  was loaded. The image **glows/fades slightly** for the duration of that real fetch, then → "BIM or ERP?".
- The glow's lifetime == the fetch promises' lifetime (honest; no decorative delay if cache is already warm).
- §-log: `§MORPHEUS_RED audio_unlocked=1 load_started=1`.

---

## 4. AUDIO  — `W-SHELL-AUDIO`
- Browsers BLOCK autoplay until a user gesture. So: the **RED pill** (cold) or the **BIM/ERP tap** (warm)
  is the gesture that unlocks audio + fires the entry SFX. Reuse the uncommitted viewer SFX work.
- Sound pill (§6) reflects state (on/muted), persists to settings. §-log: `§AUDIO unlocked_by=<red|choice>`.

---

## 5. APP-SHELL CANVAS (BIM destination)  — `W-SHELL-CANVAS`
- Live THREE viewport AS the page: default **cube + soft grid**, mouse-playable (orbit/pan/zoom).
- Cube/grid = the honest "empty scene" + proof-of-life. Opening a building REPLACES the cube (Blender feel).
- All chrome = pills overlaid on the canvas (§6). Reuse existing viewer THREE/Orbit setup; lazy-init THREE.
- §-log: `§SHELL_CANVAS cube=1 grid=1 helpers=<on|off>`.

---

## 6. PILL CHROME (Lucide line icons, pill-registry; tooltip on hover)  — `W-SHELL-PILLS`
Each pill: **hover → popup the respective message (tooltip)**. Clean cluster, "rearranged neatly".
- **Eye** — toggle scene helpers (cube + grid) on/off. §`§PILL_EYE helpers=<on|off>`.
- **Sound** — mute / entry SFX state (§4).
- **Sample** — opens the chooser panel (§7).
- **Language flag** — language chooser (i18n). Hover = current language.
- **Know More `?`** — opens the About box (§8).
- (Credits/links are NOT a footer — they live in the About box as link rows / their own pills.)
- §-log per toggle so witnesses read state, never Playwright for value checks.

---

## 7. CHOOSER PANEL — one panel, two sections  — `W-SHELL-CHOOSER`
Opened from the **Sample** pill. Single panel, two labelled sections, same row styling:
- **Section A — `Sample.db`** : curated/hosted buildings (as-is list).
- **Section B — `Your IFC.db`** : the user's imported buildings (IndexedDB `bim_ootb_imports`).
- **Per-row actions on BOTH sections: `Save As` + `Delete`** (unified; the standalone Clear-Cache button is
  RETIRED — per-row Delete subsumes it):
  - **Save As** → write the real `.db` to disk. Chromium: File System Access `showSaveFilePicker` (Path A).
    Else: plain download (Path B). §`§ROW_SAVEAS row=<name> mode=<fsa|download>`.
  - **Delete** = consequence-aware (same icon, different guard):
    - `Sample.db` row → quiet **evict** cached copy (re-downloadable). No warning.
    - `Your IFC.db` row → if NOT yet Saved-As to disk → GUARD: *"This is your only copy — Save As first?"*;
      if already saved → quiet. §`§ROW_DELETE row=<name> section=<sample|yourifc> guarded=<0|1>`.
- Selecting any row LOADS it into the same canvas in-place (the page IS the viewer). §`§CHOOSER_OPEN row=<name>`.
- Display: hide raw `import://…` URLs — show building name + thumbnail.
- Empty `Your IFC.db` → onboarding affordance: "Drop or Open an IFC".

---

## 8. ABOUT BOX (Know More `?`)  — `W-SHELL-ABOUT`
One box = learn-more + trust + install.
- **Links:** video, docs, etc. (rows; or pills inside).
- **SLA / trust note (only verifiable claims):** *"Your model never leaves your device — open the Network
  tab and watch: zero upload. Open source — inspect any line in the console."* State what Save-As / Share do
  so "no backdoor" stays literally true.
- **Install button** (the ONLY install trigger — no auto-install, no Morpheus-install):
  - Drops the app icon (PWA install) AND warms the FULL cache for true offline (`GET_PRECACHE` full set).
  - Chromium: stash `beforeinstallprompt`, fire native dialog on click (user still confirms — can't bypass).
  - iOS Safari: no API → show "Add to Home Screen" hint. Keep a persistent fallback if prompt didn't fire.
  - Brief online flash ("fetching — online for a sec") covering the real warm fetch (§1). §`§INSTALL fired=<n>`.

---

## 9. STORAGE MODE (settings)  — `W-SHELL-STORAGE`
- Setting (settings-editor / settings.json): **cache-in-browser (DEFAULT)** vs **save-direct**.
- Default = cache so first-try is frictionless; the per-row **Save As** affordance (§7) is what carries the
  trust moment, not a setting the user must find. Power users flip to save-direct.
- §`§STORAGE mode=<cache|direct>`.

---

## 10. OPEN / BLOCKED ITEMS
- ⚠ PENDING ASSET: image is **`2hands.jpg`** (name confirmed by user). NOT yet on disk anywhere
  (`find ~/Pictures ~/bim-ootb ~/bim-compiler` = empty). Drop it into the deploy tree
  (`erp/assets/2hands.jpg` / `viewer/assets/`) + add to the sw precache shell before wiring Morpheus.
- ⛔ DECISION (was open, now SIMPLIFIED by user): Share icon — DROPPED. Rows carry only Save As + Delete.
- Optional later card: self-host THREE + sql.js to `lib/` → kill the cold CDN touch entirely.

---

## 11. WITNESS LEDGER (claim-first; §-log proves each — NO Playwright for value checks)
| Witness | Proves |
|---|---|
| W-SHELL-COLDWARM | boot probe branches cold→Morpheus / warm→choice; Clear-Cache returns to cold |
| W-MORPHEUS | ZERO load until red click; glow==fetch lifetime; blue graceful-end (nothing fetched); red unlocks audio+load |
| W-SHELL-AUDIO | audio unlocked only by a real gesture (red pill / BIM-ERP tap) |
| W-SHELL-CANVAS | cube+grid render; Eye toggles helpers; building replaces cube |
| W-SHELL-PILLS | each pill toggles state + hover tooltip pops correct msg |
| W-SHELL-CHOOSER | two sections; Save As (fsa/download); consequence-aware Delete guard; in-place load |
| W-SHELL-ABOUT | verifiable SLA copy; install fires native prompt (Chromium) / hint (iOS); cache warmed |
| W-SHELL-STORAGE | cache default; save-direct flip honored |

Build order suggestion: §2.1 cold/warm probe → §3 Morpheus → §5 canvas → §6 pills → §7 chooser → §8 About/install → §9 storage.
Each: spec → implement → §-log run → witness → mark ✅ in this ledger → next. Work to zero.

---

## 12. REVISION 2 — 2026-06-18  (MINIMALIST RAIL + BONSAI SYMMETRY)  [agreed in discussion]

### 12.1 PRODUCT DOCTRINE — the symmetry law
- **BIM surface → Bonsai (BlenderBIM) look-alike.  ERP surface → iDempiere look-alike.**
- **The `⋯` three-dots rail is the ONLY proprietary chrome on BOTH surfaces** — cross-app activity + the extras.
- Mirrors the ERP FUNDAMENTAL LAW (anything NOT-iDempiere → `⋯` rail). Same `⋯`, same behaviour, every page.
  Zero learning curve on BOTH audiences (Blender/Bonsai users + iDempiere users) = the wow factor.

### 12.2 THE `⋯` RAIL — extracted, REAL component (NOT invented)
- Use REAL `window.PillBuilder` (`erp/pill_builder.js`) + `window.ICONS` (`erp/icons.js`). Bottom-right dock; strip
  RISES UP on tap (`pill-revealing`), collapses back to `⋯`. Rail CSS = VERBATIM from `erp_pills.js _injectStyle`.
- **Stays COLLAPSED by default on every page + a slight HOBBLE/peek** — EXTRACT `@keyframes erp-pill-peek` +
  `.erp-pill-attract` (erp_pills.js) / the idmp_pills bob. Let the user figure it out (minimalist).
- **DROP the auto-open of World History** — the reveal ENDS at the `⋯` rail present + hobbling. User explores.
- Pill set (icons EXTRACTED from ICONS — confirm each, never inline a new glyph except the one Bonsai tree below):
  | Pill | Real icon key | Action | Source |
  |---|---|---|---|
  | World History | `worldHist` | open the real WholeHistory panel | whole_history.js |
  | Read/Compare (lightbulb) | `lightbulb` | Migrate/Compare paper | real pills.json id `erpdoc` |
  | Share | `share` | share | ICONS.share |
  | ERP | ⛔ NO native glyph | → erp.html | DECIDE next session: img (icon-192) or a glyph — extract, don't invent |
  | About (?) | `circleHelp` | About box | real pills.json id `guide` |
  | **Save** | `save` | write resulting `.db` to disk (FSA `showSaveFilePicker` / download — §7 path) | ICONS.save (erp/icons.js) |
  | **Open** | `folderOpen` | re-open a saved `.db` + bulk import-all-from-disc (S222) + Merge/New (S224/S225) — NO chooser card | viewer/panels.js folderOpen |
  | **Bonsai** | ⛔ none yet | → the Bonsai look-alike UI | the ONE new tree icon to add (user-authorised); source a Lucide tree/sprout SVG |
- **Save + Open are LIT ONLY once a model is open** (publish-boundary; dark/disabled on cold viewer). User decree
  2026-06-19: **NO `.db` chooser card on the landing** (reads as "fishy") — these two pills carry Save/Open/import/merge
  instead. They are the only home for those verbs; §7's per-row card is superseded by these pills.
- "may add from the list" — other real ICONS keys are available (home, eye, grid, layout, settings, maximize, …).

### 12.3 BLUE PILL EXIT
- Keep EXACTLY as-is (approved — "nice, well invented"). ADD a low **"bum"** audio cue on the blue-pill exit.

### 12.4 ⭐ NEXT-SESSION AGENDA — TRIAGE BONSAI TOGETHER (do FIRST, before any build)
Bonsai is huge (full IFC authoring). Triage scope **together** before building:
- **Viability / scoping:** which Bonsai panels are in-scope — 3D viewport · spatial/IFC outliner tree · properties
  (Pset) · tool shelf · drawings/sheets · 4D/5D · clash · classification.
- **DROP what is already FOLDED:** features we already cover — 4D capture, costing-via-ERP, clash panels, whole/branch
  history, typed-NL decoder, Find/Revit+ lens, share sheet. Don't rebuild — fold/link them in.
- **Real-data-backed ONLY:** every panel must map to data we HAVE (`elements_meta`, `spatial_structure`, `bom_tree`,
  Psets-if-extracted). NEVER a dead panel implying function we don't ship (same rule as the iDempiere shell).
- Output = the scoped **Bonsai UI look-alike spec** (the LAYOUT + which panels light up). THEN build.

### 12.5 index2.html — CURRENT STATE (this session)
- ✅ Morpheus hands (pure image, half-hotspots, hover tips) · red→deep orchestral PAD + glow · blue→graceful end ·
  cold/warm gate (warm=real buildings).  ✅ REAL `⋯` PillBuilder rail extracted (worldHist + about wired) + real
  `.whole-chip` look understood.  Image `assets/2hands.jpg` in place.
- ⛔ TODO next session: trim to MINIMALIST per §12.2 (rail reveals collapsed + hobble, NO auto World History) ·
  add full pill set · add Bonsai pill + tree icon · add blue "bum" sound · resolve ERP pill icon · then §12.4 triage.
