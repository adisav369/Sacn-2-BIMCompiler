# Ground Realism — Static Map + Cheap Moving Shadow — Spec

> Status: SPEC (no code yet). Spec-first per CLAUDE.md. Witness claims below must
> be proven by §-tagged runtime logs / screenshots on real data before any deploy.
>
> **Scope pivot (2026-05-30):** AccumulativeShadows / baked shadow (earlier draft)
> is SUPERSEDED. User confirmed: the moving sun shadow ("sky shadow effect") is fine
> and stays. Clouds + cloud shadows are OUT (blocky, already removed S277b). The
> realism win wanted = a **static ground map** (zero runtime cost), plus optionally
> making the existing moving shadow free at idle.

## §0 Goal
A **scenic** static ground that simply looks good under the building (grass / earth /
soft terrain backdrop) — NOT a geographically accurate map. It's a static texture on
the existing ground plane → **zero per-frame cost**. Keep the moving sun shadow.

1. **Scenic ground texture** — one good static texture (or procedural gradient) on the
   ground plane, tiled/sized to the footprint. Static → zero runtime cost.
2. **Keep the moving sun shadow** on the structure unchanged (TM sun cycle + H toggle).
3. **Optional**: make that moving shadow cost ~nothing when the sun isn't moving, so
   Time Machine pays only on sun-move ticks, and free-orbit idle is free.

Non-goals (explicitly dropped by user): **georeferenced / real satellite ground**
(buildings DO carry coordinates — SampleHouse=London, Duplex=Chicago — but accuracy is
not wanted; scenic look only). Clouds (blocky — stay removed). Terrain heightfield.

## §1 What we have (researched 2026-05-30)
**Shadows — dynamic, and re-rendering every frame:**
- PCF shadow map 4096², single `A.sun` DirectionalLight. Frustum sized to building
  envelope. Setup in `tools.js:520-599` (`A.toggleShadow`, H key / Sunglass panel).
- TM sun cycle moves the sun every storyboard *tick* and sets
  `renderer.shadowMap.needsUpdate = true` (`time_machine.js:1377`). Sun arc computed
  in `applySunCycle` (`time_machine.js:1317-1386`). Shadow casters capped at 500,
  frontier-promoted (`time_machine.js:811-828`).
- **Cost finding:** `renderer.shadowMap.autoUpdate` is never set false → default `true`.
  Desktop renders unconditionally every rAF frame (`main.js:589-593`). So while shadows
  are on, the 4096² depth pass for up to 500 casters re-renders **every frame even when
  the sun is parked**. That is the avoidable runtime cost. (three.js docs / forum:
  set `autoUpdate=false` + `needsUpdate=true` on change → shadow renders only when the
  light or geometry moves.)

**Ground — flat colored plane, no texture:**
- `scene.js:300-309`: 50000² `PlaneGeometry`, `MeshStandardMaterial` solid color
  (brown `0x5C4033`; theme/night recolors in `tools.js`). `receiveShadow=true`,
  hidden until shadow toggle. Y placed by `A._calcGroundY()` (`tools.js:6-62`) — finds
  the ground-floor slab bottom. In the render loop the ground is hidden when the camera
  drops below it (`main.js:570-572`).
- `A._envMap` is generated from the Preetham sky and applied per-material
  (`scene.js:189-201`); `scene.environment` is deliberately NOT set (would white-flash
  the ground).

**Sky — fine, keep:** Preetham shader (`lib/Sky.js`, `A.updateSky` `scene.js:157-210`).
Clouds removed S277b (`scene.js:247-250`).

**Geo data — NOT available:** IFC import excludes `IfcSite` (`import_worker.js:335`);
no RefLatitude/RefLongitude stored. Only GPS is the live device camera for AR snapshots
(`sitecam.js`). ⇒ a real satellite tile keyed to the building location is out of scope
unless geo-extraction is added later.

## §2 Design — three independent, composable pieces
### A. Static ground map (the main ask) — RESOLVED 2026-05-30
**Decision:** ship **three** seamless CC0 site textures + an **None** option, all
**runtime-selectable** from the **Sunglass panel**, with the option list driven by a
**JSON config file** (no hard-coded paths in JS). Picking a row swaps
`A.ground.material.map`; zero per-frame cost regardless of which is active.

Three shipped textures (CC0 — Poly Haven / ambientCG, seamless, 1K, tile across plane):
- **grass** — aerial grass/meadow (the user's lead choice; default-on look).
- **earth** — bare soil / gravel construction-site (closest to today's `0x5C4033`).
- **paved** — concrete/paving plaza (clean CAD-presentation neutral grey).
Plus **none** — the current flat colored plane (texture cleared), so nothing regresses.

**Config-driven (JSON).** The viewer reads a `ground` block from the settings JSON
(consumer of `settings_editor.js`, like corporate/grid/clash). The Sunglass "Ground:"
row is rendered *from* this config — adding a 4th texture later = a JSON edit, no code.

```jsonc
// settings JSON — new "ground" consumer block
"ground": {
  "default": "grass",            // which option is active on load ("none" = legacy flat)
  "repeat": 64,                   // tiles across the 50000² plane (tune to footprint)
  "anisotropy": 8,
  "options": [
    { "key": "none",  "label": "None",  "src": null },
    { "key": "grass", "label": "Grass", "src": "textures/ground/grass_1k.jpg" },
    { "key": "earth", "label": "Earth", "src": "textures/ground/earth_1k.jpg" },
    { "key": "paved", "label": "Paved", "src": "textures/ground/paved_1k.jpg" }
  ]
}
```

**Loader behaviour:** lazy `TextureLoader` per option, decoded on first selection (or
prefetched during load), `RepeatWrapping` + `repeat` from config, `SRGBColorSpace`,
`anisotropy`. Cache loaded textures on `A._groundTex[key]` so switching is instant.

Constraints (unchanged): must not white-flash (keep ground off `scene.environment`, low
`envMapIntensity` as today); must still receive the moving shadow (keep
`receiveShadow=true`); recolor hooks in `tools.js` (theme/night/white-bg) must set
`material.color` to **white** when a map is present (color *multiplies* the texture —
overwriting it would tint the photo), and dim (not black-fill) for night.

### B. Free-at-idle shadow (the "shadows-ON is heavy" fix) — DEFERRED, see §6
- Set `A.renderer.shadowMap.autoUpdate = false` once shadows are initialized.
- Set `A.renderer.shadowMap.needsUpdate = true` exactly where the sun/geometry changes:
  TM tick (already at `time_machine.js:1377`), Sunglass sun slider, shadow toggle on,
  and after the chunked caster traverse (`tools.js:581`).
- Result: moving shadow unchanged during TM; at idle/free-orbit the shadow pass is
  skipped → measurable frame-time drop. No visual change.

### C. Clouds — OUT. No work. Confirm they stay removed.

## §3 Witnesses (prove before deploy)
- **W-GROUND** *(visual)*: static texture renders on the plane, tiled to the footprint,
  no white flash on sky/env regen, shadow still lands on it. Screenshot + `§GROUND_MAP
  src=… repeat=…`.
- **W-IDLE** *(if B done)*: with `autoUpdate=false`, idle frame time drops vs current;
  log shadow renders only on sun-move — `§SHADOW_UPDATE reason=tm-tick|slider|toggle`
  and confirm none fire during a still free-orbit. Compare ms idle on a real building.
- **W-TM**: TM playback shadow still tracks the sun every tick (no regression) —
  existing `§SHADOW_FRONTIER` logs unchanged.
- **W-THEME**: ground texture survives theme / night / white-background toggles
  (`tools.js`) without going black/white-flat.

## §4 Decisions — RESOLVED 2026-05-30
- **Ground source:** ship **three** CC0 textures (grass / earth / paved) **+ None**,
  runtime-selectable, list driven by **JSON config** (§2.A). Grass is the lead/default.
- **Chooser UI:** a "Ground:" row in the **Sunglass panel** (`panels.js` sunglass
  builder), rendered from the JSON `ground.options`.
- **Idle shadow gate (§2.B):** **DEFERRED** to its own session. User confirmed the
  existing moving sun shadow is good and must not be disturbed; the gate is the only
  change that *reduces* the heavy shadows-ON cost, but carries a stale-shadow risk, so it
  ships standalone only with §-log proof (see §6). This session = ground textures only.
- **Asset acquisition:** 3 seamless 1K CC0 JPGs sourced from Poly Haven / ambientCG,
  vendored under `viewer/textures/ground/`, added to SW precache (one CACHE_VERSION bump).

## §4b FEASIBILITY & COST — Photographic terrain ground (decision: deferred to future update)
User chose **Photographic terrain** (seamless real photo, tiled, fading to fog horizon),
then scoped this session to *calculate possibility + added cost only* — no code now.

**Feasibility: HIGH / trivial.** The ground plane already exists (`A.ground`,
`scene.js:300-309`), already `receiveShadow=true`, already positioned by `_calcGroundY`.
The change is: set `material.map = texture` + `RepeatWrapping` + `repeat` sized to the
envelope. THREE r184 supports it natively. No new system, no new draw call (the plane is
already drawn), no shadow change, no streaming impact.

**Added cost:**
| Dimension | Cost |
|---|---|
| Runtime / per-frame | **ZERO** — texture lookup on an already-rendered quad. |
| Download payload | One seamless albedo JPG: 1K ≈ 150–300KB (recommended), 2K ≈ 0.4–0.8MB. Added to SW precache (one CACHE_VERSION bump). |
| GPU memory | 1K RGBA + mips ≈ 5MB VRAM; 2K ≈ 21MB. One texture. Fine desktop+mobile at 1K. |
| Load-time | One async decode + GPU upload, once (~few ms). Do during load to avoid an interaction hitch. |
| Dev effort | ~1 small session, ~30–60 lines: lazy `TextureLoader`, `RepeatWrapping`, `SRGBColorSpace`, anisotropy, apply to ground; integrate the 5 theme/night/white-bg recolor sites (`tools.js:280,612,617,664,814`); SW precache + version bump. |

**Gotchas (future maintenance):**
- Visible tile repetition on the 50000² plane → mitigate with macro/detail blend or let
  fog (`FogExp2`) + below-camera hide (`main.js:570-572`) swallow the far field.
- Must keep ground off `scene.environment` + low `envMapIntensity` (white-flash, already
  a known constraint at `scene.js:303`).
- Theme/night recolor sites set `material.color`; with a map, color *multiplies* it — set
  color to white (show photo) / dim for night, don't overwrite.
- Asset must be CC0/owned (e.g. Poly Haven, ambientCG) — not a scraped image (licensing).
- Mobile: cap at 1K.

**Verdict:** low-cost, low-risk, ~zero runtime. Per §4 we ship **3** selectable textures,
so payload is ~3× one tile (~450–900KB at 1K) + ~5–15MB VRAM (only the selected map is
resident; others lazy-load on pick). The shadow `autoUpdate=false` idle gate is **split
off to §6** (separate session) — it is the only thing that *reduces* current shadows-ON
cost, but it touches the working sun shadow so it ships standalone with §-log proof.

## §6 DEFERRED — Standalone spec: free-at-idle shadow gate
> Separate future session. Do NOT bundle with the ground textures. The user's working
> sun shadow is sacred — this ships ONLY after §-logs prove it never stales.

**Problem it solves:** shadows-ON is heavy because `renderer.shadowMap.autoUpdate`
defaults `true`, so the 4096² depth pass (≤500 casters) re-renders **every rAF frame**
even when the sun is parked and the user is only orbiting (`main.js:589-593`). That
constant re-render is the "heavy cost when shadows are ON" the user feels.

**The change (small):**
- After shadows are initialized, set `A.renderer.shadowMap.autoUpdate = false` once.
- Set `A.renderer.shadowMap.needsUpdate = true` at EVERY point the sun or casters move:
  1. TM sun-cycle tick (already `time_machine.js:1377`),
  2. Sunglass sun/time slider,
  3. shadow toggle ON (`tools.js` `toggleShadow`),
  4. after the chunked caster traverse completes (`tools.js:~581`),
  5. any frontier promotion that adds casters (`time_machine.js:811-828`).
- Each with a §-log: `§SHADOW_UPDATE reason=tm-tick|slider|toggle|casters|frontier`.

**The risk (why it's deferred):** miss ANY of those sites → the shadow freezes (goes
stale) until the next update — visibly lagging the sun. That would "bother" the good
shadow. So the acceptance bar is exhaustive coverage, proven by log.

**W-IDLE acceptance (prove ALL before deploy):**
- Idle free-orbit (sun parked): `§SHADOW_UPDATE` fires **zero** times over N frames, and
  measured frame time drops vs. current build on a real building. → confirms saving.
- TM playback: `§SHADOW_UPDATE reason=tm-tick` fires every tick; shadow tracks the sun
  identically to today (diff screenshots). → confirms no stale regression.
- Sun slider drag: one `§SHADOW_UPDATE reason=slider` per change, shadow follows. 
- Toggle off→on, building load mid-session, frontier promotion: shadow correct each time.
- W-TM existing `§SHADOW_FRONTIER` logs unchanged.

## §5 Out of scope
Live satellite/geo ground (needs geo-extraction), terrain heightfield, clouds, mobile
tuning beyond existing gates, city-wide multi-building ground.
