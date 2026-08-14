# ⚠ DO NOT REMOVE
STATUS: SPEC ONLY, 2026-08-13. Nothing built. This is a new-feature spec (Spec-First per CLAUDE.md) —
no code until Open Question 1 below is resolved with a real measurement, not an eyeball call. Read the
log after every run once build starts. Origin: user idea, discussed same session as the doors-mystery
handoff and the prompts/ bloat consolidation (see `prompts/4D_SCHEDULE_PERFECTION.md` for the unrelated
active bug, not this file).

## ORIGIN — the ask, verbatim shape
During movie-making, a "Reveal" checkbox that does two things:
1. **Final-orbit discipline parade** — at the start of the final orbit, the movie slows, ARCH/STR
   ghosts to reveal the other disciplines, a status caption names which one ("Electrical", "Fire
   Protection", "Mechanical", ...), cycling through each, then all together, then ARCH/STR solidifies
   again as the orbit rises to close the film.
2. **In-transit ambient reveal** — while the camera is travelling through the building mid-film (not
   just the final orbit), wherever there's a dense cluster of already-meshed MEP nearby, ARCH/STR
   ghosts briefly (~2s) to let it read, then returns solid — without altering the baked camera path's
   timing/pacing at all, purely a render-layer overlay.

## CORRECTIONS TO THE USER'S OWN FRAMING — verified against current code, 2026-08-13
The user named "Alt-Z x-ray" as the reusable primitive. Checked live in `deploy/dev`:
- **Alt+Z X-ray is DEAD** — `deploy/dev/scene.js:1174`: `// §S280: Alt+Z X-Ray removed — too costly,
  OutlinePass replaces it`. Do not design against it; it no longer exists.
- **The live x-ray today is the plain `X` key** → `A.toggleXray` (`deploy/dev/tools.js:83`). It flips
  `transparent=true, opacity=0.3, side=DoubleSide` on every material already in `A._matCache` — a
  **global**, all-disciplines, flag-flip (no rebuild, cheap). Room lens / Find highlight already reuse
  this same primitive (`navigate_find.js:406-497`). It is NOT per-discipline as-is.
- **Alt+X is the wireframe bbox ghost** the user called "not good ghost" — confirmed still real
  (`tour.js:115` references "switching to Alt-X bbxes", 2026-07-16) and matches the standing finding
  below. Discipline-colored, instanced `BoxGeometry` wireframes, drawn from `element_transforms.bbox_*`
  — free, but reads as an outline, not a ghost-fill.

## THE HARD CONSTRAINT — already proven, do not re-test from scratch
Standing finding (Alt+X design history, 2026-06-07): **a filled/translucent whole-envelope ghost
WHITEWASHES dense buildings (Hospital 63k / Terminal 48k / LTU 122k) at ANY opacity.** 0.12→0.05 was
tried and did not fix it — the cause is **coverage** (every overlapping translucent face fills the
whole silhouette; MAX-blend already caps the stacking), not opacity magnitude. Only Duplex-scale
(~1.1k elements) looked fine filled. This is the exact reason Alt+X shipped as wireframe-bbox instead
of a filled shell.

**"Extra weak" opacity alone will NOT dodge this on the buildings that matter most (Hospital, Terminal,
LTU).** This is the single biggest risk in mechanism B below and must be measured on a real dense
building before any render-approach decision, not eyeballed — per this project's own FUNDAMENTAL LAW
(numeric proof, not screenshots).

## Mechanism A — in-transit ambient reveal pulses
- **Trigger (refined 2026-08-13, user clarification):** NOT a continuous per-frame density poll while
  moving — anchored to the path's own planted waypoints ("sticks", CPE's existing term for a dropped
  pin). Fires only from a STOP stick (a waypoint where the baked path pauses), not while the camera is
  still travelling. This directly answers/replaces Open Question 3 below (no re-fire/flicker risk from
  continuous polling, since it only evaluates at discrete stop points).
- **Duration/opacity, user's own reasoning:** ~2s, and only PARTIALLY faded (not near-invisible) —
  brief enough + partial enough that the user doesn't lose visual/spatial memory of the envelope while
  it's ghosted. First-guess parameter: **opacity 0.2** (fainter than the current live x-ray's 0.3
  default — that 0.3 is just the existing `toggleXray` value, not a proposed answer, see note below).
  0.2 is reasonable to try here specifically because A's footprint is local (see whitewash note below)
  — this number is NOT validated for Mechanism B.
- **At a stop stick, condition:** local MEP density near the camera (still) crosses the threshold —
  i.e. the stick location determines WHEN to check, the density check still determines WHETHER to fire.
- **Reuse, don't build new:** the R-tree already built for clash detection (`measure.js`, deferred to
  first clash-panel open) is the natural spatial index for "elements within radius R of camera position,
  discipline ≠ ARC/STR" — no new spatial structure needed.
- **Reuse:** `A.DISC_COLORS` (`config.js:20`) already enumerates the discipline codes (ARC, STR, MEP,
  ELEC, FP, ACMV, PLB, HEAT, HVAC, SAN, VENT) and every element already carries its discipline as a
  WHERE-column per `docs/internal/WalkerDoctrine.md` — this is the same data Alt+X's discipline-colored
  ghost already reads, reuse the same filter, don't invent a new discipline lookup.
- **Hard constraint from the user, verbatim:** must not break camera path stride — this has to be a
  pure render-layer opacity tween keyed to elapsed film-time or camera position, with the camera's own
  advancement completely untouched by whether a pulse is firing.
- **Possible escape from the whitewash problem:** because this only ghosts elements local to the
  camera (not the whole building), the affected silhouette is small against the rest of the solid
  scene — plausible it sidesteps the coverage problem above. **Unverified — first thing to witness
  before committing to filled-fill for A specifically** (A's exposure is much smaller than B's, so it
  may get away with filled where B can't).

## Mechanism B — final-orbit discipline parade
- Slow the orbit at its start; ghost ALL of ARCH/STR (whole-building, not local — this is the case most
  exposed to the whitewash problem above); cycle disciplines one at a time with a status caption
  ("Electrical", "Fire Protection", "Mechanical", ...); show all together; resolve ARCH/STR back to
  solid as the orbit completes.
- **Correction 2026-08-13 (user, verified against code) — the orbit is NOT a flat/level shot
  throughout, so the reveal can't run on its own independent clock.** `tour.js:1506-1519`, the `orbit`
  action, is 3 phases against progress `to` (0→1):
  1. `to < 0.2` — climb-in from the approach pose up to the orbit's elevated height.
  2. `0.2 ≤ to < 0.6` — **plateau at full `tiltDeg` (35-40°, an "atop"/elevated look-down angle)**,
     constant height (`orbitH`).
  3. `to ≥ 0.6` — descent: `effectiveTilt = tiltRad * (1 - descentProgress²)` unwinds the tilt toward
     flat/level WHILE camera height falls toward `_groundY`, both finishing together at `to = 1`.
  The discipline-cycle/solidify choreography needs to be keyed to these same three phases (e.g.
  cycling likely belongs in the elevated plateau where the view is widest/most stable; the flatten+
  descend phase is naturally where ARCH/STR resolving solid would read best) rather than assuming a
  constant camera angle and running an independent caption timer alongside it. Exact mapping of
  phase→discipline-step is NOT decided — needs a design pass once Open Question 1 is settled, since the
  render approach affects how long each discipline needs to read on screen.
- **Render-approach candidates for the ARCH/STR ghost (open, no decision yet):**
  1. Filled weak-opacity shell — the user's original suggestion. Proven risky per the hard constraint
     above on any building bigger than Duplex-scale.
  2. Reuse `OutlinePass` — already the current replacement for the old costly Alt+Z x-ray per
     §S280 — show ARCH/STR as a silhouette/outline instead of a filled translucent shell. Sidesteps
     coverage entirely by construction (no filled faces to stack).
  3. Reuse Alt+X's existing wireframe-bbox ghost as-is for B, rather than building a new filled
     variant — it's already proven not to whitewash, already discipline-colorable, already free.
- **Skip disciplines with zero elements in the building being filmed** — per WalkerDoctrine, small
  residential buildings (SH/DX/SC) walk `duplex_rules.db` and can be missing whole disciplines (e.g. no
  FP/sprinkler) that Terminal-class buildings have. Don't cycle an empty caption for a discipline the
  building never had.
- **Caption text/order:** reuse `A.DISC_COLORS`' existing code set for which disciplines exist and in
  what order; a human-readable label map (ELEC→"Electrical", FP→"Fire Protection", etc.) needs to be
  checked against the locale files (`deploy/dev/locales/*.js` already has `ui_xray_found` etc. — check
  for existing discipline label strings before inventing new ones) rather than hand-writing new labels.

## Pacing refinement (2026-08-14, user, extends Mechanism B — not yet reconciled with the phase-mapping above)
User's own words: "slowing down the cam last move after last element mesh appearance? That slowing
down is almost standstill adding perhaps 2 secs for each DISC with final 2 secs is all DISC together
before ARC/STR covers back and path resumes as normal. **No change in path.**"

Read as a concrete timing model for Mechanism B's already-open "exact mapping of phase→discipline-step"
question: the slow-down is triggered by an EVENT (the last element mesh of the current discipline
appearing/revealing), not a fixed clock offset — camera speed eases toward near-zero at that moment,
holds there for ~2s per discipline while its caption reads, then the same again for ~2s once every
discipline is shown together, then resumes normal orbit speed as ARCH/STR solidifies back. Confirms
explicitly what was already implied but not stated outright: **this is pacing/dwell-time only, added
ON TOP of the existing baked orbit geometry — the path itself (position/radius/tilt) is untouched**,
consistent with this file's own "pure render-layer, camera advancement untouched" constraint already
stated for Mechanism A, now extended to B (previously B's own text only said "slow the orbit," without
confirming path-invariance explicitly).

Not yet reconciled: how a "near-standstill, event-triggered" dwell interacts with the 3-phase orbit
structure above (climb-in / elevated plateau / flatten+descend) — a ~2s×N-discipline dwell needs to fit
inside one of those phases (almost certainly the elevated plateau, phase 2, since that's already
flagged as "where cycling likely belongs") without stealing time from the climb or descent, or the
orbit's own total duration needs to grow to absorb it. Needs a decision once Open Question 1 (render
approach) is settled, since render approach affects how long each discipline actually needs to read.

## Open questions — need a go before touching code
1. **Render approach for B** (filled-weak vs. OutlinePass silhouette vs. reused Alt+X wireframe-bbox).
   Decide only after measuring coverage% on one real dense building (Hospital or Terminal) — same
   discipline this project already applied to the original Alt+X whitewash finding. This is the one
   question that can sink the whole "extra weak x-ray" framing if the answer is "still whitewashes."
   **A specific opacity value (0.2, 0.3, or anything else in the filled-shell family) does NOT answer
   this question** — 0.12→0.05 was already tried on a filled ghost and didn't fix the whitewash, so no
   number in this family is expected to fix it either; the fix (if needed) is a different render
   technique (outline/wireframe), not a smaller opacity.
2. **Density threshold for A's trigger** — how close a radius, how many non-ARC/STR elements, before a
   pulse fires (now only evaluated AT a stop stick, per the 2026-08-13 refinement above, not continuously
   while moving). No existing precedent to reuse; first guess needs tuning against a real flythrough.
3. ~~Re-fire behavior for A~~ — **answered 2026-08-13**: tied to discrete stop sticks, not continuous
   polling, so no per-frame flicker risk. Residual question: does the SAME stop stick re-fire if the
   camera pauses there more than once (e.g. scrubbing back), or only once per bake?
4. **Caption/HUD mechanism** — checked `tour.js` for an existing status-text overlay during bake and
   found none obviously named; needs a dedicated look (or confirmation it doesn't exist yet) before
   assuming one can be reused for the discipline captions.
5. **Is "Reveal" a single global toggle or per-building?** Small residential buildings can have near-zero
   MEP disciplines extracted — the checkbox should probably no-op quietly there rather than cycle empty
   captions, needs a decision on how that's surfaced (disabled checkbox? silent skip?).

## Competitive scan — "does any other BIM viewer do this?"
Checked 2026-08-13 (web search, not a vendor-doc deep dive — read as "not found," not "proven absent"):
Navisworks, Twinmotion, Enscape, Fuzor, BIM 360/ACC. All of them expose manual discipline/category
visibility toggles. The cinematic-focused viz tools (Twinmotion, Fuzor) support scripted/keyframed
animations that CAN include visibility or material overrides at fixed points a human sets by hand —
Fuzor's cinematic mode specifically lists "visibility options" among its keyframeable effects. **No
tool found that automatically senses proximity/density of nearby disciplines during a generated
flythrough and auto-triggers a ghost-reveal choreography** — that combination (live density sensing +
auto-triggered pulses tied to a generated, not hand-keyframed, camera path) doesn't show up in what's
documented for these tools. If it holds up, this is a genuine differentiator, consistent with this
project's existing "not a smaller Revit" positioning (`prompts/PREFAB_LASSO_MACRO_LIBRARY_DIALOGUE.md`).

Sources:
- [Twinmotion vs Enscape in 2026 - Vagon](https://vagon.io/blog/twinmotion-vs-enscape)
- [Product Review: Real-Time Rendering with Fuzor | AUGI](https://www.augi.com/articles/detail/product-review-real-time-rendering-with-fuzor)
- [Twinmotion vs Enscape: In-Depth 2026 Comparison](https://www.myarchitectai.com/blog/twinmotion-vs-enscape)

## Status
SPEC ONLY. Needs a go on Open Question 1 (measured, not eyeballed) before B gets built at all. A is
lower-risk (localized, may dodge the whitewash problem) and could plausibly be witnessed/prototyped
first, independent of B's answer.
