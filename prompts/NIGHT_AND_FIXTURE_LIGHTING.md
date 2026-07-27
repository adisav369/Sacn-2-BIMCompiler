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
