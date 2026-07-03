# Sky Realism — Static HDRI / Painted Clouds ("Bonsai-style") — Spec

> Status: SPEC (no code yet). Spec-first per CLAUDE.md. Witness claims must be proven by
> §-tagged runtime logs / screenshots on real data before any deploy.
>
> **Origin (2026-05-30):** user misses cloud realism "like Bonsai has." Bonsai's clouds
> look good because Blender/Cycles renders an **HDRI world** (a baked photographic sky
> dome), NOT a runtime cloud simulation. That is the SAME trick as the static ground map
> (`docs/GROUND_SHADOW_BAKING.md`): bake a real photo onto static geometry, pay **zero
> per-frame cost**. This is explicitly NOT the blocky billboard/volumetric clouds removed
> in S277b — those were cheap real-time approximations and were correctly killed.

## §0 Goal
Photographic clouds in the sky with **zero per-frame cost**, without disturbing the
moving sun shadow / day-night cycle the user likes. A static, scenic sky — sibling of the
ground map, not a cloud engine.

Non-goals: volumetric/raymarched clouds, animated/drifting clouds, billboard sprites
(all removed or rejected), per-frame cloud cost of any kind.

## §1 What we have (researched 2026-05-30, `bim-ootb/viewer/scene.js`)
- **Procedural sky** = Preetham shader `Sky` mesh (`lib/Sky.js`), `A._sky`
  (`scene.js:155`). `A.updateSky(elevation, azimuth)` (`scene.js:160-213`) sets the sun
  uniform; the sky darkens naturally below horizon (day↔night). **This is sun-driven and
  the user wants to keep it.**
- **Sun-coupled extras** all track `updateSky`: the `DirectionalLight` sun
  (`scene.js:169`), **lensflare** (`scene.js:171-190`), **fog color** day/night blend
  (`scene.js:205-212`), and the **env map** for reflections (`A._envMap`, PMREM'd from
  the Preetham sky, applied **per-material** — never `scene.environment`, which would
  white-flash the ground, `scene.js:192-204`).
- Time Machine drives `updateSky` every tick → the whole sky/sun/fog animates with the
  4D playback. (Same sun that casts the shadow.)
- Clouds removed S277b. Ground stays off `scene.environment` (white-flash constraint).

## §2 The core tension (the design problem)
A photographic HDRI/cloud image has its **own baked sun and lighting direction**. Our
sky's sun **moves** (free-orbit time-of-day + TM playback) and drives the shadow, fog,
lensflare and reflections. So a naive "replace the Preetham sky with an HDRI" would:
- freeze the sky while the shadow keeps moving → sun in the photo disagrees with the cast
  shadow (uncanny),
- break the day↔night darkening and fog/lensflare coupling,
- and the env map would need to come from the HDRI instead.

Every option below is a different way to resolve this tension. **This is the open
decision for the user (§4).**

## §3 Design options (composable; pick in §4)
### C1. Cloud layer OVER the procedural sky (keeps moving sun) — RECOMMENDED
Keep the Preetham sky exactly as-is (sun, day/night, fog, lensflare untouched). Add a
**separate large cloud dome/shell** just inside the sky: an **equirectangular cloud-only
texture with alpha** (transparent where there's blue sky), on an inverted sphere,
`depthWrite=false`, rendered after the sky. Tint the cloud layer's color/opacity from the
same `elevation` `updateSky` already computes → clouds go warm at sunset, grey at dusk,
hidden at night. Sun still moves; clouds are static geometry (a slow optional yaw is
possible but default static = zero cost).
- **Pros:** preserves the loved moving-sun system; cheapest; clouds light-tint with TOD.
- **Cons:** clouds don't cast onto the building (fine — scenic only); finding a good
  alpha cloud equirect (CC0). Lighting match to the sun is approximate (tint, not baked).
- **Cost:** one extra inverted-sphere draw (cheap, no per-frame compute), ~0.3–1MB PNG/
  WebP with alpha, ~5–20MB VRAM at 2K.

### C2. Full HDRI sky-dome MODE (toggle), Bonsai-faithful
Ship a curated **HDRI/equirect sky-with-clouds** as an OPTIONAL sky mode (Sunglass / JSON
config, like the ground map). When ON: swap the Preetham sky for the HDRI dome AND derive
`A._envMap` from the HDRI (consistent reflections). The moving sun light still casts
shadows, but the sky image is static (best for hero stills / free-orbit, not TM).
- **Pros:** most photoreal, true Bonsai look, correct image-based reflections.
- **Cons:** static sun in the image vs. moving shadow — so gate it (e.g. auto-revert to
  Preetham during TM playback, or accept the mismatch in still mode). Larger asset (HDR
  or 4K JPG). More integration (env map swap, day/night handling).
- **Cost:** 2–4K equirect (~1–4MB), env map regen on toggle, ~20–60MB VRAM.

### C3. Procedural cloud shader (rejected unless asked)
Raymarched/noise clouds in the sky shader — per-frame GPU cost, the very thing we're
avoiding. Listed only for completeness; **not recommended**.

## §4 Open decisions (ask user)
- **Approach:** C1 (cloud layer over the live moving sky — cheapest, keeps everything) vs
  C2 (full HDRI mode — most photoreal, needs a TM gate). Could ship C1 first, C2 later.
- **Where it lives:** same pattern as ground map — **Sunglass row driven by JSON config**
  (`sky.clouds`: none / soft / overcast / dramatic), or a single toggle.
- **TM behaviour (if C2):** auto-revert to procedural sky during playback, or allow the
  static HDRI throughout.
- **Asset(s):** CC0 only (Poly Haven HDRIs / equirect skies). Which cloud mood(s).

## §5 Witnesses (prove before deploy)
- **W-CLOUD** *(visual)*: clouds render in the sky, no per-frame cost regression (frame
  time unchanged vs current at idle), shadow/sun still move. Screenshot + `§SKY_CLOUDS
  mode=… src=… drawcost=static`.
- **W-TOD** *(C1)*: clouds tint with time-of-day and vanish at night via `updateSky`
  `elevation` — `§SKY_CLOUDS elev=… tint=… opacity=…` across a TM cycle.
- **W-NOFLASH**: adding the cloud layer / HDRI does not white-flash the ground (env map
  rule preserved — ground stays off `scene.environment`).
- **W-TM**: TM playback unaffected (C1) or correctly gated (C2); existing sun/fog/
  lensflare coupling logs unchanged.

## §6 Cost summary
| Dimension | C1 (cloud layer) | C2 (HDRI mode) |
|---|---|---|
| Per-frame runtime | ~zero (1 static draw) | ~zero (1 static draw) + env regen on toggle |
| Payload | ~0.3–1MB alpha equirect | ~1–4MB HDR/JPG per mood |
| VRAM | ~5–20MB (2K) | ~20–60MB (4K) |
| Integration risk | LOW (additive, sky untouched) | MED (env-map swap, TM gate, day/night) |
| Bonsai-likeness | good | best |

## §7 CHOSEN BUILD (2026-05-30) — Procedural drifting clouds + sweeping ground shadow
User decisions: **drifting clouds + sweeping shadows** tier (most alive); **choices like the
Ground texture** (config-driven picker with an OFF/Clear option); **OFF = zero cost** (gated);
control reachable from the **day/night icon** so it's one obvious kill-switch. True volumetric
cloud-shadows stay OUT (per-pixel raymarch = framerate death on mobile / 40k-element scenes).
This is the cheap, honest approximation.

### Why procedural (not photographic) for THIS tier
Moving clouds can't use a baked-sun HDRI (rotating a baked sun looks wrong) and CC0 **alpha
cloud** equirects aren't readily available. A **procedural FBM-noise cloud field** solves it:
zero asset/licensing, the "choices like ground" fall out as coverage presets of ONE field,
and the same field drives both the sky clouds and the ground shadow so they stay in sync.
Keeps the live moving sun + day/night + TM cycle untouched.

### Design (3 cheap, composable pieces)
1. **Drifting cloud field (sky):** FBM/noise cloud layer over the Preetham sky (cloud plane or
   sky-dome pass), alpha = coverage, drifts via one `time` uniform/frame. Tints via `updateSky`
   elevation (warm at sunset).
2. **Sweeping ground shadow (LOW RISK — ground only):** sample the SAME drifting field in the
   **`A.ground` material only** (scrolling cloud-shadow texture / small onBeforeCompile inject)
   → soft moving dark patches sweep the ground in sync. We own `A.ground`; no other material
   touched → no trust-IFC-colors / sun-shadow-map risk.
3. **Subtle global dim (building):** modulate `A.sun`/ambient intensity by the field's local
   average as cover passes overhead. Cheap (a couple light writes/frame), no per-material shader.

### Choices like Ground + day/night kill-switch
`sky_config.json` (sibling of `ground_config.json`), picker row in the Palette panel; the
day/night icon can also set clouds→`none`. **Default `none` → fully gated → zero cost.**
```jsonc
"clouds": {
  "default": "none",          // OFF by default; day/night icon can also clear to none
  "driftSpeed": 0.01,
  "options": [
    { "key": "none",     "label": "Clear",    "coverage": 0.0 },
    { "key": "fair",     "label": "Fair",     "coverage": 0.35 },
    { "key": "cloudy",   "label": "Cloudy",   "coverage": 0.6 },
    { "key": "overcast", "label": "Overcast", "coverage": 0.85 }
  ]
}
```
`coverage` = noise threshold (only knob differing per preset). Add a 5th = JSON edit.

### Cost (researched)
| Dimension | Cost |
|---|---|
| Asset / download | **ZERO** (procedural; no texture, no licensing) |
| Per-frame (ON) | LOW: 1 cloud shader pass + ground field-sample + ~2 light writes. No raymarch. |
| Per-frame (OFF, default) | **ZERO** — whole path gated on `clouds!=none`. |
| Pipeline risk | LOW-MED: cloud pass isolated; ground shadow = `A.ground` ONLY; dim = lights only. No building-material shaders, no sun-shadow-map edits. |
| Mobile | cap octaves / drop ground-shadow inject on mobile (gate like S271). |

### Witnesses (prove before deploy)
- **W-CLOUD-DRIFT**: clouds drift over the live sky; sun still moves; idle FPS unchanged vs
  clouds=none. `§SKY_CLOUDS preset=… coverage=… drift=on`.
- **W-CLOUD-SHADOW**: soft patches sweep the **ground** in sync; building dims slightly under
  cover; NO banding on IFC colours (building materials untouched). `§CLOUD_SHADOW ground=on dim=…`.
- **W-CLOUD-OFF**: preset=none → cloud pass + ground sample + dim fully skipped; frame time
  identical to today. `§SKY_CLOUDS preset=none cost=0`.
- **W-CLOUD-TM**: TM day/night cycle unaffected; clouds tint across the cycle; no `§SHADOW_FRONTIER`
  / sun-shadow regression.
- **W-CLOUD-CHOICES**: Palette picker shows Clear/Fair/Cloudy/Overcast from `sky_config.json`;
  instant switch; `§SKY_ROW built opts=4`.

### Build order (spec-first, witness-gated, separate session from Gantt work)
(a) sky_config.json + Palette picker row (reuse ground row's deferred-init fix).
(b) drifting cloud field shader (sky) — prove drift + OFF gate + TM tint.
(c) ground-only sweeping shadow — prove patches, no building-material change.
(d) subtle global dim — prove bounded building dim under cover.
(e) mobile gate + day/night-icon wiring + witnesses + deploy.

**Recommendation:** start with **C1** — additive, cheap, keeps the moving sun the user
loves, and reads as "real clouds" immediately. Reserve **C2** as an optional hero-shot
sky mode later. Both are static, both honour the zero-per-frame principle, neither is the
blocky-billboard cloud that was rightly removed.
