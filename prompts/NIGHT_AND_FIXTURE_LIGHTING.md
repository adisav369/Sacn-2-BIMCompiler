# ⚠ DO NOT REMOVE — Night mode + fixture lighting: settings, costs, and the calculations
**Scope:** every light source the viewer creates from BIM data — night-mode point lights, the
night material glow, and the Alt+S still's §PHOTO_EMBER/§PHOTO_BLOOM layer. **Read the log after
every run.** Honour until DONE.

**Why this file exists (created 2026-07-27):** the user asked for "the prompts/# on it [that] has
the setting and calcn" after warning *"be careful as we have lighting hell before too.. ie over
shot.. too many lamps cost RAM too"*. **There was no such file.** Every setting and every reason
lived only in `§S277d` comments inside `viewer/tools.js`, which is why the same ground was re-walked
twice. This is now the record. Update it whenever a constant below changes.

---

## The constants, and what each one is actually protecting
`viewer/tools.js`, top of the night-mode block:

| constant | value | what it protects / why |
|---|---|---|
| `A._nightMaxLights` | **12** | NAVIGATION budget. Every three.js point light costs per-fragment work on **every lit material, every frame**, with no distance culling in the forward renderer. 12 is what a 60fps orbit carries. |
| `A._nightMaxLightsStill` | **48** | FROZEN-STILL budget. A still renders once and then sits there, so the 60fps budget does not apply. Raised by `startStillRefine`, **restored on teardown** — if it is not handed back, the 4x set follows the user into their next orbit and the frame rate goes with it. |
| `NIGHT_LIGHT_INTENSITY` | 8.0 | Candela-ish. Illuminance falls as `intensity / d^decay`. |
| `NIGHT_LIGHT_DECAY` | 1.5 | Between linear (1) and quadratic (2) — reaches further than physics, deliberately. |
| `NIGHT_LIGHT_RANGE` | 0 | Infinite; no artificial cutoff, so overhang/doorway/corridor spillover survives. |
| `A._nightNearFadeFloor` | **0.3** | NAVIGATION anti-blowout: a light AT the eye runs at 30%. |
| `A._nightNearFadeFloorStill` | **1.0** | STILL: no proximity penalty (see §NIGHT_NEAR_FADE below). |

### The RAM question — measured, not assumed
**Night point lights cost almost no RAM.** They never set `castShadow` (only `A.sun` does, tools.js:710),
so **no shadow map is allocated per light** — a shadow-casting point light would allocate a cube map
each, which is where the "too many lamps cost RAM" intuition comes from, and that is exactly what is
NOT happening here. Per-light cost is a handful of shader uniforms.
**The real cost is GPU shader work, not memory:** per-fragment lighting on every lit material, plus a
**shader recompile whenever the light COUNT changes** (which is why the count is switched once at
still-start and once at teardown, never per frame).
**If lighting hell returns, suspect the count and the intensity, not RAM** — and check the still
actually handed its 48 back.

---

## §NIGHT_GLOW_CLASS_GATE — the bug that made the Clinic pitch black (fixed 2026-07-27)
User: *"In Night mode, these were not identified, thus the Alt-s also didn't pick it up nor alt-c"*,
reporting `M_Troffer Light - Parabolic Rectangular`.

The gate read:
```sql
ifc_class='IfcLightFixture' OR LOWER(element_name) LIKE '%light%' OR ...
```
and controlled only whether to widen `_nightGlowClasses` to `IfcFlowTerminal`. It mixes two
questions. The Clinic **has** 1105 name-matching lights but **zero** `IfcLightFixture` — so the gate
returned true, the widening was SKIPPED, the glow class list stayed `['IfcLightFixture']`, and
**nothing glowed at all, at any distance**. Having named lights disabled the fallback that would
have caught them. Now the gate tests for the CLASS only, since the class list is all it controls.

## §NIGHT_FIXTURE_VOCAB — 961 power sockets were light sources
The point-light POSITIONS query took every `IfcFlowTerminal`. On the Clinic that is 961
`M_Duplex Receptacle` and 236 `M_Lighting Switches` as well as the real luminaires. The user had
asked for a `'light'` filter a month earlier; it existed in that query **only as a test, never as
the selector**. Now name-filtered with the shared vocabulary.

## §NIGHT_NEAR_FADE — the lights nearest the camera were the weakest
User: *"They dont catch even lights right near to cam"*. `intensity = 8.0 * (0.3 + 0.7*min(1,dist/15))`
means a light AT the camera runs at 30% — the dimmest in the scene, by design, added to fix "inside
too bright". Exactly backwards for an interior shot. Kept for navigation, lifted to 1.0 for the still.

## §NIGHT_LIGHT_MIX — colour by fitting type
User: *"if we can have a mix of amber, and bluish etc"*. One flat `0xffe4b5` for every fixture makes a
building read as one lamp repeated N times. Derived from the fitting, not randomised:
`cool 0xdce8ff` (troffer/batten/T8/low-bay), `warm 0xffdca8` (downlight/sconce/pendant/surface),
`exit 0x9bffc0` (exit/keluar/signage), `amber 0xffe4b5` fallback. **Where the model STATES the
temperature it wins** — Terminal's families carry `cw`/`ww` in the name, and stated data outranks a
type convention.

---

## The luminaire vocabulary — the one rule every path must share
Three consumers now select luminaires (night positions, night glow, §PHOTO_EMBER). They must agree.
- **NOT by `ifc_class`** — Terminal/Hospital use `IfcLightFixture`, the Clinic uses `IfcFlowTerminal`.
  Keying on the class finds ZERO in the Clinic.
- **NOT by a bare `%light%`** — that matches `M_Lighting Switches` (236) and `M_Lighting and
  Appliance Panelboard` (28). Measured on the Clinic: **1105 naive matches -> 841 real luminaires**.
- **Include:** light, troffer, downlight, luminaire, lamp, sconce, pendant.
- **Exclude:** switch, receptacle, panelboard, socket, outlet.

Verified per family on the Clinic (all mapped to `MeshStandardMaterial` with an emissive channel):
`Troffer Rectangular 443 · Troffer Square 158 · Downlight 147 · Pendant 70 · Surface 11 · Sconce 8 ·
Signage 4`.

## Related, and NOT to be re-derived
- **`rel_contained_in_space` has NO ELEC rows** (ACMV/ARC/STR only). Group fixtures to rooms by
  POSITION, via `element_transforms` against the space's `center_*`/`size_*`.
- **The Clinic is 5 federated models**; `A.streamBuilding()` must be called ONE AT A TIME, waiting
  for the guid count to settle, or only one lands.
- **Use `A.ifc2three(x,y,z)`** for DB->world. Three attempts to reinvent that mapping put a probe
  camera outside the building and inside walls.
- **Emissive alone is invisible** (measured 56.13 -> 56.13 mean luminance). Bloom is not an
  increment on glow, it is the half that makes glow exist. See PHOTOREAL_STILL_RENDER.md §PHOTO_EMBER.
- **`toneMappingExposure = 0.45` is the DAY value and stays.** The lift belongs in the photoshoot
  (`PHOTO_EXPOSURE_LIFT`), because raising the renderer default brightens day navigation — and the
  user recalls overexposure trouble from doing exactly that.

## Measured result of the 2026-07-27 batch
Night still vs night nav, Clinic, same Alt+C pose: **mean luminance 61.77 -> 103.17 (+67%)**,
hot pixels 1.289% -> 2.323%. Stills: `~/Pictures/Screenshots/ember/night_{1_nav,2_still}.png`.

## Open
- The near-fade is still 0.3 in NAVIGATION — correct there, but it means walking a corridor at night
  is dimmer than the still of the same spot. Judge before changing; it is anti-blowout, not an oversight.
- 48 lights across 841 fixtures still leaves rooms unlit. Raising it is a measured decision, not a
  dial: check shader-recompile stalls and per-frame cost on the biggest building before moving it.

---

# ▶▶ NEXT SESSION — START HERE. Everything above is settled background.
**Written 2026-07-27 at session close. The still-lighting feature is BUILT, MEASURED, and
DELIBERATELY TURNED OFF.** `A._emberEnabled = false` in `viewer/effects.js`. Re-arm it to experiment;
do not ship it re-armed without solving §THE BLOCKER below.

## What is live on `main` right now (keep — these fix real bugs)
| § | what it fixes | proof |
|---|---|---|
| §NIGHT_GLOW_CLASS_GATE | the Clinic lit **nothing at all** before it | `fixtures=841 source=IFC+fallback` |
| §NIGHT_FIXTURE_VOCAB | 961 receptacles + 236 switches were light sources | 1105 naive → 841 real |
| §NIGHT_LIGHT_MIX / §NIGHT_MIX_RATIO | colour by fitting type + a 20/20 blue/amber share | amber 20.1%, blue 17.7%, deterministic |
| §MAXQ_HIDDEN_PAUSE | a backgrounded tab silently ruining bakes | witness 6/6, RED 0/6 |

## What is OFF, and why (do not simply re-enable)
`§PHOTO_EMBER`, `§PHOTO_BLOOM`, `PHOTO_EXPOSURE_LIFT` (2.2→1.0), and the 48-light still boost — one
look, judged together. User, live on Hospital: **black rectangles and lit wall panels.**

## §THE BLOCKER — per-material emissive cannot work at this scale
Their own log is the entire diagnosis:
```
§PHOTO_EMBER lit 1216 luminaires -> 1216 guidMap hits, 86 meshes, 7 materials
```
**Seven materials for 1216 luminaires in a 63,182-element building.** Batched and instanced meshes
share ONE material across everything they draw. So:
- setting `emissive` on those 7 lit **walls, beams and railings** — everything else drawn with them;
- `toneMapped=false` on a material shared by a **transparent** panel renders it **pure black** —
  those are the black rectangles, not a bloom artifact.

An exclusivity guard (`§PHOTO_EMBER_EXCLUSIVE`, in the code, working) skips any material shared with
a non-luminaire. Measured on the Clinic: **6 materials → 4 applied, 2 skipped.** It cuts the damage
**and simultaneously proves the approach is a dead end** — the same sharing that causes the collateral
is what the fixtures themselves are drawn with, so a correct guard also starves most fixtures of glow.
**This needs a different mechanism, not a better filter.** Do not spend the next session tuning it.

### Mechanisms worth evaluating instead (none tried yet)
1. **Additive glow sprites/quads at fixture positions.** Decoupled from the geometry entirely, so
   material sharing becomes irrelevant — the collateral problem disappears by construction. Positions
   are already computed and correct (`A._nightFixtures` + `A.ifc2three`, 841/1216 verified). Bloom
   picks them up. This is the strongest candidate and the cheapest.
2. **Per-instance emissive via a custom attribute** on InstancedMesh/BatchedMesh, injected through
   the existing `onBeforeCompile` hook that triplanar already uses. Precise, but touches the hot
   material path that Layer 3 depends on.
3. **A dedicated luminaire mesh layer** — clone luminaire geometry into its own mesh with its own
   material at stage time, restore on teardown. Simple, costs geometry.

## Also unresolved
- **`§PHOTO_BLOOM` has never been judged on its own.** The user has not seen it work — it shipped
  attached to a broken ember. `viewer/lib/BloomPass.js` is written, wired before `OutputPass`, and
  measured harmless. Judge it against emissive that actually lands.
- **AO does not reduce cost — it adds it.** User asked "is it really using occlusion to lessen its
  burden?" No: `§PHOTO_AO done frames=24 totalMs=635 avgRenderMs=23.8`. AO is shading, not culling.
  **The `o` key is `toggleDlodNav` (DLOD), which IS the culling system** — that is the one that hides
  distant/small geometry to save cost. AO and DLOD are unrelated; do not conflate them again.
- Night NAVIGATION is dimmer than a still of the same spot (0.3 near-fade, correct as anti-blowout).

## How to verify anything here
Rig `/tmp/wt-turn` on port 8403, buildings symlinked (Clinic included).
`PORT=8403 node probe_night_still.js` — night mode + Alt+S, reports lights nav/still/restored and
every `§PHOTO_EMBER` / `§NIGHT_*` line. `probe_mix.js` checks the colour ratio and its determinism.
**Test on Hospital, not only the Clinic** — the Clinic's 33-element collateral read as a footnote and
is exactly why this shipped broken; Hospital is where the same ratio becomes a broken render.
