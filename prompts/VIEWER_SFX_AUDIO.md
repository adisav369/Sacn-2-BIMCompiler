# ⚠ DO NOT REMOVE — Scope: Optional synthesized audio SFX overlay for the BIM viewer (bim-ootb/viewer)
# Read the §-log after every run. Honour this block until every Scope item is ✅ DONE or ⛔ BLOCKED.
# Target tree = ~/bim-ootb/viewer (canonical — the ONLY tree with the Settings JSON editor). NOT deploy/dev.

## One-line goal
Add an **optional, default-OFF, zero-asset** audio-feedback layer to the viewer: a self-contained
`sfx.js` overlay that synthesizes short WebAudio tones (no `.mp3`/`.wav` files shipped), toggled by a
speaker entry in the pill registry (→ pill icon + Help/command-palette row + keyboard shortcut), and
configured by `sfx.json` that appears in the existing **Settings → Edit Project JSON** hub.

## Why (research-grounded — see the session brief)
No mainstream BIM viewer ships a designed UI-sound layer (Revit's only sound is the Windows warning
chime, which users mute). So this is novel. UX evidence converges on three hard rules, which this spec
enforces: **(1) default OFF, (2) one-tap mute, (3) persisted preference.** The strongest, lowest-risk
use case is the **4D Time Machine play-through + Fly tour** — a bounded "watch-it" moment (like a video
soundtrack), where per-construction-phase earcons convey concurrent trades. Per-click UI sounds are the
highest-annoyance slice → first build ships ONLY a single soft pill-tap tick beyond TM + Fly.

## Non-negotiable constraints
- **No audio asset files.** All sound is WebAudio `OscillatorGain` synthesis (`beep(freq, ms, wave, gain, pan)`).
  Zero KB, zero network, no decode.
- **Default OFF.** Master `enabled:false` in `sfx.json`. Nothing plays until the user toggles the speaker.
- **One AudioContext, lazy-init on first user gesture** (browser autoplay policy forbids earlier). Toggling
  the speaker on IS the gesture that creates/resumes the context.
- **Separation of concern / overlay.** ALL sound logic lives in `sfx.js`. Core files expose at most ONE
  tiny guarded seam each; when `sfx.js` is absent or audio is OFF the seam is a no-op (one property check).
  - **Fly = ZERO core edit** — `sfx.js` polls `A.flyActive` / `A.walkActionIdx` / `A.walkActions` (rAF while flying).
  - **Time Machine = ONE guarded seam line** — `time_machine.js` setCursor reports the active frontier
    phase signature; `sfx.js` decides whether/what to play (TM stays sound-agnostic).
- **No sound for non-existent ops.** First build wires ONLY: TM phase change, Fly waypoint, pill tap.
  (Select/isolate/lens/clash are deferred — they have no event hooks and would need core wrapping.)
- **Witness-first.** Every claim below has a `§SFX_*` log line; read the log, never trust exit code.

## Files (all in ~/bim-ootb/viewer unless noted)
| File | Change |
|------|--------|
| `sfx.js` | **NEW** — overlay IIFE: AudioContext + `beep()` synth, config loader, speaker toggle, TM seam handler, Fly poller, pill-tap listener. ZERO references to THREE; only `window.A`, `window.loadJsonWithOverrides`. |
| `sfx.json` | **NEW** — config: `master` (enabled/volume/fly_positional/ui_clicks) + `construction_sounds` (array keyed by phase) + `ui_sounds` (array). Conforms to the JSON-editor standard (auto-infer: object→sections, array-of-objects→reorderable rows). |
| `panels.js` | `_actions[]` += `{id:'audio', name:'Sound FX', key:'v', icon:<speaker svg>, fn:toggle, isActive}`; `_defaultOrder` += `'audio'`; `_jsonRegistry[]` += `{id:'sfx', label:'Sound Effects', url:'sfx.json', storageKey:'json_sfx'}`. |
| `time_machine.js` | ONE guarded seam after the frontier loop (~L614): build a sorted unique-phase signature of frontier ops; if changed since last tick AND `window.__sfxTM`, call `window.__sfxTM(phasesArr, repPosVec3)`. No-op when sfx absent. |
| `viewer.html` | `<script src="sfx.js?v=1" onerror="…§LOAD_FAIL sfx.js…">` immediately before `main.js` (after time_machine.js). |
| `sw.js` | `PRECACHE_ASSETS` += `'sfx.js'`, `'sfx.json'`; bump `CACHE_VERSION` v577→v578. |

## sfx.json shape (the contract; values are placeholders, user-editable in Settings)
```json
{
  "master":   { "enabled": false, "volume": 0.5, "fly_positional": true, "ui_clicks": true },
  "construction_sounds": [
    { "id": "substructure",   "phase": "Substructure",   "wave": "sine",     "freq": 110, "ms": 240 },
    { "id": "superstructure", "phase": "Superstructure", "wave": "triangle", "freq": 196, "ms": 200 },
    { "id": "architecture",   "phase": "Architecture",   "wave": "square",   "freq": 262, "ms": 170 },
    { "id": "mep_rough",      "phase": "MEP Rough-in",   "wave": "sawtooth", "freq": 392, "ms": 150 },
    { "id": "mep_final",      "phase": "MEP Final",      "wave": "sawtooth", "freq": 523, "ms": 150 },
    { "id": "finishes",       "phase": "Finishes",       "wave": "sine",     "freq": 659, "ms": 200 },
    { "id": "default",        "phase": "*",              "wave": "sine",     "freq": 330, "ms": 160 }
  ],
  "ui_sounds": [
    { "id": "tap",      "wave": "sine",     "freq": 880, "ms": 30,  "label": "Pill / button tap" },
    { "id": "fly_spot", "wave": "triangle", "freq": 520, "ms": 130, "label": "Fly waypoint pass" }
  ]
}
```
- Phase strings are the REAL `SEQUENCE_RULES` phases (extracted from rates.js) — non-invent. `phase:'*'`
  is the fallback when a TM op's phase matches no row (still deterministic: explicit default row, no guessing).
- `sfx.js` reads config via `loadJsonWithOverrides('sfx.json','json_sfx')` (same path every other consumer
  uses) so Settings edits apply on next load.

## Behaviour
1. **Load:** `sfx.js` fetches config, builds a phase→sound map, leaves AudioContext uncreated. Speaker
   pill shows inactive. `§SFX_INIT enabled=<bool> sounds=<n>`.
2. **Toggle ON (speaker / key `v` / palette row):** create-or-resume AudioContext (the user gesture),
   set `_on=true`, persist `localStorage['sfx_on']`, update pill active state, play one confirm tick.
   `§SFX_TOGGLE on=true ctx=<state>`.
3. **Time Machine playback:** on each setCursor, if the frontier phase signature changed, `__sfxTM` fires;
   `sfx.js` plays the matched construction earcon for the newly-dominant phase (one per phase transition,
   NOT per element — avoids 40k-op cacophony). `§SFX_PLAY src=tm phase=<P> freq=<f>`.
4. **Fly tour:** while `A.flyActive`, poll `A.walkActionIdx`; on increment, play `fly_spot`, panned by the
   waypoint's X vs building centre when `master.fly_positional`. `§SFX_PLAY src=fly idx=<i> name=<n>`.
5. **Pill tap:** one delegated `pointerup` listener on the pill container plays `tap` when `master.ui_clicks`.
   `§SFX_PLAY src=ui id=tap`.
6. **Settings:** "Sound Effects" appears in Edit Project JSON; editing volume/enabled persists `json_sfx`;
   reload reflects it. `§SFX_CONFIG loaded=json|json+override sounds=<n>`.
7. **OFF / absent:** master off OR `_on=false` ⇒ every play path early-returns before touching the context.
   Core seams are no-ops (`window.__sfxTM` undefined). Zero render/perf impact.

## Witnesses (whitebox §-log FIRST; read the log; each names the issue it proves)
- `§SFX_INIT enabled=false sounds=N` — proves DEFAULT-OFF on a fresh load (no override).
- `§SFX_TOGGLE on=true ctx=running` — proves toggle creates/resumes the context on user gesture.
- `§SFX_PLAY src=tm phase=Superstructure freq=196` (≥2 distinct phases over a TM play) — proves
  per-construction-type earcons key off the REAL op phase, one per transition (not per element).
- `§SFX_PLAY src=fly idx=K name=…` — proves Fly waypoint cues fire WITHOUT a core edit (poller).
- `§SFX_PLAY src=ui id=tap` — proves pill-tap tick.
- `§SFX_CONFIG loaded=json+override sounds=N` — proves Settings edit to `json_sfx` is honoured on reload.
- TM-OFF regression: with audio OFF, run a TM play → ZERO `§SFX_PLAY` lines + `§SFX_INIT enabled=false`
  (proves no sound leaks when off; proves the seam is inert).
- Playwright (secondary, wiring only): `sfx.js` loads, `#mobile-pill` contains the speaker button, the
  command palette renders a "Sound FX" row with `<kbd>v</kbd>`, Settings hub lists "Sound Effects".
- Gate: `node --check` on every edited JS; `node tests/audit_script_tags.js` + `audit_sw_precache.js` exit 0.

## Deploy flow (DO NOT COMMIT until served + witnessed on localhost — user directive)
Edit → `node --check` all → serve `viewer.html` on localhost → load a building → toggle audio → run TM +
Fly + pill tap → capture `§SFX_*` lines from the console → confirm each witness above → THEN report.
No git commit, no OCI upload, no sw deploy in this pass.

---
## STATUS
- [ ] sfx.js + sfx.json created
- [ ] panels.js registry + order + json registry
- [ ] time_machine.js seam (1 guarded line)
- [ ] viewer.html script tag + sw.js precache/version
- [ ] node --check clean
- [ ] localhost serve + §SFX_* witnesses captured
