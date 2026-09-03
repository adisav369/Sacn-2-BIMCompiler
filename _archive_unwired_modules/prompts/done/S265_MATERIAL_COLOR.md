# ⚠ DO NOT REMOVE — read the log after every run

# S265: Material Color Investigation

## Goal
SampleCastle should look like `~/Downloads/ColurfulSampleCastle.jpeg` — brown brick walls, yellow roof, grey concrete, white windows. Currently looks bland/washed out despite correct data.

## Status: RESOLVED — threshold removed, IFC data shown as-is

### Resolution (2026-05-20)
**Root cause:** `_spread < 0.08` threshold in `_getMaterial` was replacing 57% of SampleCastle's real IFC colors with generic STD_MAT class colors. Introduced at `a1aeeb33` to help Terminal/LTU (grey buildings), but killed SampleCastle's real palette.

**Fix:** Removed all color filtering. Only NULL `material_rgba` gets STD_MAT fallback. All real IFC colors pass through untouched. For grey buildings (Terminal/LTU), user applies Sunglasses slider on demand — no automatic override.

**Principle:** Let the system be genuine for universal usage. Code does not second-guess IFC data. User controls appearance via Sunglasses slider.

**Deployed:** ootb-dev SW v416. `streaming.js` §S265c, `main.js` §S265c, `panels.js` §S265c.

### Also fixed: Unconditional render (2026-05-20)
**Root cause:** On-demand render gate (`_needsRender` flag in `main.js`) meant `renderer.render()` only ran when camera moved or streaming was active. Every UI change (sliders, palette, bbox loading) was invisible until user touched the scene. Introduced at `7d941768` as a GPU optimization.

**Fix:** Removed the `if (_needsRender || streaming ...)` condition. Render runs every frame unconditionally, like the original code before the gate was added. All 20+ `markDirty()` calls in the codebase remain harmless.

**Symptoms this fixed:**
- Slider color changes not visible until scene touched
- Building disappearing until user orbits
- Bbox placeholders not appearing during load
- Any material/visibility change requiring a scene touch to take effect

### Open glitches (studied, not fixed — low priority)

#### 1. Overflow menu needs two clicks on first open
- First click: `§UI_OVERFLOW open` then immediately `§UI_OVERFLOW close`
- Second click works normally, subsequent clicks all work
- Init reset of `overflow-open` class added in `panels.js` but didn't fully fix
- Likely cause: event propagation or bfcache DOM state restoration
- Observed in both Chrome and Firefox
- Also: focus stack leaks — `stack=[sunglass,sunglass,sunglass,toolbar,grid,grid...]` grows unbounded

#### 2. Chrome disk cache serves stale icons
- Overflow icons show old eye/Feather SVGs despite SW version bump
- Hard reset (Ctrl+Shift+R) fixes for current session only
- Reopening Chrome restores stale cache
- Full fix: Ctrl+Shift+Delete → Clear cached images and files
- Only affects Chrome — Firefox clears correctly

#### 3. Stale saved 2D cuts (Plan Grid) persist after deletion
- User deletes saved sections, logs confirm deletion: `§SAVE_SECTION deleted id=1 remaining=0`
- On next open/reload, deleted sections reappear: `§SAVE_SECTION loaded=1`
- Auto-create suppression works (`§SAVE_SECTION all deleted — auto-create suppressed`)
- But previously deleted sections come back — storage (localStorage/IndexedDB) not reflecting deletion
- Needs investigation: where are saved sections stored and why deletions don't persist

### What we know (proven)

1. **DB is correct.** SampleCastle re-extracted with `scripts/extractIFC2DB.js` (Node.js, web-ifc). 3621 elements, only 117 null colors. Brown brick `0.447,0.200,0.055` (298 elements), yellow `1.000,1.000,0.659` (223), warm beige `0.920,0.900,0.850` (514) all present. Uploaded to `bim-ootb` common bucket 2026-05-20.

2. **Python extractor loses colors.** `extractIFCtoDB_open.py` produced 1830 nulls (57%) — doesn't resolve `IfcRelAssociatesMaterial` chain or capture web-ifc `bestColor`. Archived to `scripts/archive/`. Memory: `feedback_extractor.md`.

3. **`_spread < 0.08` overrides real IFC colors.** 1594 SampleCastle elements with intentional IFC colors (beige, olive, grey) have spread < 0.08 and get replaced by STD_MAT fallbacks. This makes Terminal/LTU (many monochrome greys) look better but kills SampleCastle's real colors.

4. **`0.7±0.02` keeps real colors but was tested twice with wrong context:**
   - First attempt: bad DB (Python-extracted, 1830 nulls) → white. Reverted.
   - Second attempt: good DB but user says sides still worse. Not properly tested before deploy. Reverted.

5. **The colorful screenshot was at commit `3c1d0192`:**
   - `MeshStandardMaterial`, `flatShading: false`
   - `ACESFilmicToneMapping`, `exposure: 1.15`
   - `AmbientLight(0x606080, 0.8)`, `HemisphereLight(0x8888cc, 0x444422, 0.5)`
   - Grey threshold: `isDefaultGrey = (Math.abs(r-0.7)<0.02 ...)`
   - No `_spread` threshold, no STD_MAT
   - Separate `ROUGHNESS_MAP` + `METALNESS_MAP` + `CLASS_COLOR_FALLBACK`
   - envMap: vertex-color gradient sphere

6. **Current state (deployed, `_spread < 0.08`):**
   - `MeshStandardMaterial`, `flatShading: false`
   - `ACESFilmicToneMapping`, `exposure: 0.45`
   - `AmbientLight(0xffffff, 0.25)`, `HemisphereLight(0xb0c4de, 0x8b7355, 0.40)`
   - STD_MAT unified table with `_spread < 0.08`
   - envMap: same vertex-color gradient

### What changed between colorful and now

| Setting | Colorful (3c1d0192) | Current | Impact |
|---|---|---|---|
| exposure | 1.15 | 0.45 | User says NOT the issue — slider tested |
| ambient | 0x606080, 0.8 | 0xffffff, 0.25 | Different color AND intensity |
| hemi | 0x8888cc, 0x444422, 0.5 | 0xb0c4de, 0x8b7355, 0.40 | Different sky/ground colors |
| grey threshold | 0.7±0.02 | spread<0.08 | Overrides 1594 real IFC colors |
| material table | 3 separate maps | STD_MAT unified | Different roughness/metalness values |

### Rules for next session

1. **DO NOT deploy changes for user to test.** Memory: `feedback_no_deploy_without_proof.md`.
2. **Build a local sandbox test** — serve index.html locally, load SampleCastle DB, screenshot or use §-tagged logs to prove colors match the reference image.
3. **Test one variable at a time** — don't change threshold + lighting + material table simultaneously.
4. **The reference image** is at `~/Downloads/ColurfulSampleCastle.jpeg` — brown walls, yellow roof, grey slabs, white windows.
5. **User controls exposure via slider** — exposure is NOT the issue per user confirmation.

### Files
- `deploy/dev/streaming.js` — `_getMaterial()`, STD_MAT table, threshold
- `deploy/dev/scene.js` — tone mapping, lighting, envMap
- `deploy/buildings/SampleCastle_extracted.db` — re-extracted, correct
- `deploy/buildings/blandcoloring/SampleCastle_extracted.db` — bad Python extraction, archived
- `deploy/dev/tests/whitebox_regression.js` — material audit test

### Ruled out by user
- **ALL lighting sliders** (exposure, sun, ambient, hemisphere) — user tested every slider, NOT the issue
- **ALL scene controls** — user: "not scene control as when they are fixed no matter what color slider u do they are there!"
- **Threshold `0.7±0.02`** — tested with correct DB, "only roof seems more grey which is good but only roof affected. EVEN THE SIDES ARE WORSE"
- **Threshold `0.01`** — "completely white"
- **Threshold `0.08`** — current stable state, "still coming out bland"
- **Exposure value** — user explicitly confirmed multiple times: IT IS NOT EXPOSURE

### Critical user observations
1. > "Only roof affected" when threshold changed to `0.7±0.02`
   Walls are NOT affected by threshold changes — they have enough spread. Yet walls still look bland.

2. > "When they are fixed no matter what color slider u do they are there!"
   The fix is in the CODE + MATERIAL pipeline, not scene controls. When materials are correct, colors are resilient to any slider position.

3. > "It was intermittently happening over course of time"
   The bland coloring comes and goes — it was solved before and regressed.

4. > "The solution was found few times in our history" / "It was very good at least twice, first some time and the last only few days ago"
   The fix EXISTS in git history. Applied at least twice. Most recent fix was around 2026-05-17/18 (few days before 2026-05-20). Then it regressed with subsequent commits.

5. > "There is some confluence between code and material of course."
   The answer is in how code interacts with material properties — not lighting/scene.

### What git history shows (session 2026-05-20)
- `3c1d0192` (S261, ~May 18): COLORFUL — `isDefaultGrey (0.7±0.02)`, ROUGHNESS_MAP/METALNESS_MAP separate, CLASS_COLOR_FALLBACK, exposure 1.15
- `a1aeeb33` (S260e, May 19 05:06): exposure→0.45, threshold→`_spread<0.08`
- `fb520ffd` (S265, May 19 21:01): same `_spread<0.08`, still ROUGHNESS_MAP/CLASS_COLOR_FALLBACK
- `9204febc` (S265, May 19 22:02): replaced ROUGHNESS_MAP+METALNESS_MAP+CLASS_COLOR_FALLBACK with unified STD_MAT, ambient 0.35→0.25, hemi 0.6→0.40
- `3e77afec` (S265, May 20 07:42): same STD_MAT, refined comments

For walls (brown 0.447,0.200,0.055, spread=0.39): color value reaching Three.js is IDENTICAL across all commits. Roughness nearly identical (0.8→0.85). The _getMaterial color path is NOT the variable.

### Remaining suspects (NOT sliders, NOT scene controls, NOT threshold)
- **Material type interaction**: MeshStandardMaterial PBR rendering of the SAME color value looks different depending on roughness/metalness/envMap combination. The "confluence between code and material."
- **envMapIntensity (0.3)**: applied to ALL materials. The envMap is a brown-ground→blue-sky gradient. On rough materials this averages to a flat tint that can wash out colors.
- **near-white taming `*= 0.92`**: dimming IFC pastels (beige elements 0.920,0.900,0.850)
- **Something else in _getMaterial or the batching pipeline** that was present "few days ago" but removed/changed in the S265 commits

### Approach for next session
1. DO NOT deploy. DO NOT change code and ask user to test.
2. The fix EXISTS in history. Find it by diffing streaming.js between the "few days ago" working state and the current broken state.
3. The fix is in the code+material pipeline (streaming.js `_getMaterial`), NOT in scene.js lighting/sliders.
4. Check what was different at the commit just BEFORE fb520ffd or 9204febc — the user says it was good "only few days ago."
5. Build local test, prove with §-tagged logs before touching deployed code.
