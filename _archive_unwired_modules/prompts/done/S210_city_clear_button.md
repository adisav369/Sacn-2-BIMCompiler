# S210 — City Mode: Clear Button Visibility

## Problem
When the user launches City Mode (LAUNCH CITY 1M), there is no Clear button visible in the city viewer. The user needs a way to clear cached/loaded buildings and start fresh while in city mode, without having to go back to the landing page.

## Fix
- Add a CLEAR button to the city viewer UI (visible only when `?city=` param is active)
- Button should clear all streamed building meshes from the scene, reset `buildingsRendered`, and return to the bbox-only wireframe view
- Keep the cached DBs in `cityBuildingDbs` (no re-download needed) — just remove the meshes from the Three.js scene
- Place button near the existing HUD controls (top-right or alongside discipline bars)

## Files
- `deploy/sandbox/city.js` — add clear logic
- `deploy/sandbox/index.html` — add button element (hidden by default, shown when city mode)

## Acceptance
- CLEAR button visible in city mode
- Click removes all streamed meshes, keeps wireframe bboxes
- Status resets to "Click a building to load"
- Button hidden in single-building mode

## DO — Testing & Logging

All test output to `deploy/dev/tests/log/`.

### Playwright — NEW `11-city.spec.js`

City mode requires `?city=` param, which needs a city DB. Tests can exercise
the city.js functions via `page.evaluate()` even without a full city DB.

| Test | What | §-tag |
|------|------|-------|
| 11.1 | setupCity wired (function exists on APP) | `§PW_CITY_SETUP` |
| 11.2 | Clear button hidden in single-building mode | `§PW_CITY_CLEAR_HIDDEN` |
| 11.3 | clearCity removes meshes from scene | `§PW_CITY_CLEAR` |

### test_all.js — add to §6 or new §20
```javascript
// City mode wiring
const citySrc = fs.readFileSync(path.join(DIR, 'city.js'), 'utf8');
ok('city.js has setupCity', citySrc.includes('function setupCity'));
ok('city.js has clear logic', citySrc.includes('Clear') || citySrc.includes('clear'));
ok('city.js loaded by viewer', html.includes('city.js'));
```

## DO NOT
- Do not modify `deploy/sandbox/` — production
