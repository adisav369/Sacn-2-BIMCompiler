# ⚠ DO NOT REMOVE — Scope & Working Rules
**Scope:** SIX delight items for the Cinema Path Editor, agreed with the user 2026-07-29. Nothing else.
Each is specced here with the user's own words, the decision, what it CONSUMES that already exists, the
open questions, its witness claims and an honest cost. **No item may be implemented before its open
questions are answered** — three of them change what gets built.
**Read the log after every run.** Verification on this project is `§`-tagged console output, not
screenshots — and for anything continuous (camera path, angles, rates) it is the NUMBERS, per CLAUDE.md's
FUNDAMENTAL LAW. Honour this block until this file is DONE.
**Canonical parent:** `prompts/CINEMA_PATH_EDITOR.md` (2,400+ lines) owns the editor's settled doctrine —
§CPE_BANDS, §CPE_EVEN_TURN, §CPE_JERK_DEFINITION, §CPE_HOSE, §CPE_STICK, §CPE_IDB_PATH_STORE. **Read its
settled rules before touching anything here.** This file is the forward queue, not a replacement.

## Shipped state this builds on (2026-07-29)
- `bim-ootb` **PR #1074 MERGED** — §CPE_HOSE, §CPE_STICK, §CPE_CLIP, §CPE_BUILDUP, §CPE_AIM_DENSITY,
  §CPE_PREVIEW_BUTTON. **PR #1078 MERGED** — §CPE_BUILDUP_REAL_SCHEDULE (another session; reveals by a
  real schedule when the DB has one, camera-path order otherwise).
- Branch `feat/cpe-hose` @ `b63a0d1` — §CPE_STICK_ANCHOR, §CPE_HOSE_REANCHOR, §CPE_IDB_PATH_STORE.
  CPE **v13**, MAXQ **v15**, sw **v875**.
- Witness `witness_cpe_hose.js` — 29/29 on Duplex, D1 known-limit on Hospital_3 (recorded, not tuned).

## ⚠ THE COST THAT WILL BITE FIRST — read before adding any feature
**`§CPE_REPLAN_SLOW ms=600–1000` on EVERY edit on Terminal** (user's own log, 2026-07-28, 48,433
elements, 4 bands). Every drag re-runs the WHOLE plan — `§CINEMA_PLAN_MS ~550` of which is
`fanRays=32 spaceCands=52 exitCands=135`, i.e. the BVH fan and the exit-door scoring — even though a
band drag changes only the walk geometry and cannot change which door the dive exits through.
**This grows with band count, and §CPE_STICK now lets the user add bands freely.** Every item below
makes the editor more inviting to fiddle with, which makes this worse.

### §CPE_REPLAN_LAZY — the user's diagnosis, confirmed by their own log
> User, 2026-07-29: *"is it tied to the best alt pipe feature? Cant that be lazy only when user press
> that feature?"*

**Yes — and the proof is already in their console.** Across EVERY drag in that session, the expensive
block printed **byte-identical values**:
```
§CINEMA_SPACE cand=CORRIDOR_ROOM::Aras Tanah|y|119.95 area=131.5 enclosed=100% chosen=true
§CINEMA_DIVE  settle=(3.7,-15.7,3.4) floorY=-17.39 nudge=5.93m yaw0=-152.4° diveDist=53.0m
§CINEMA_EXIT  chosen=1hnR05M7n4hAsCinhpv9DL dist=6.4 cost=11.3 candidates=135 runnerUp=…@12.8
```
Identical settle point, identical door, identical cost, identical runner-up — **on eight consecutive
re-plans.** That is ~550 ms of BVH fan raycasts, 52 space candidates and 135 scored doors producing
the same answer every time, because that block depends only on (a) the MODEL and (b) the camera basis
— and §CPE_PREVIEW_DIVERGENCE already **pins the basis to the pose the editor opened with**, precisely
so that orbiting cannot change the film. The cache key is therefore trivially stable for the whole
editor session: *nothing the user can do while editing can change it.*

**The seam is clean.** In `_cinemaPathPlan`, everything from `arcBbox`/`pivot` through §CINEMA_SPACE,
§CINEMA_DIVE and §CINEMA_EXIT runs BEFORE the `if (_cpeBands …)` block that expands bands into the
route. Bands, hose and clip all act after it. So: compute that prefix once on editor open, hand it to
subsequent re-plans, and re-run only the parts that legitimately changed — band flow, hose, pacing,
§CPE_NOISE_LAW probes, `poseAt`.

**Better than lazy: give the user the button they implied.** Re-deriving the entry is a real thing they
may WANT (a different room to dive into, a different door to walk out of) — it just should not happen
eight times by accident while dragging a stick. So: cache by default, plus a **`re-derive entry`**
control that recomputes the prefix on demand. That turns a hidden 550 ms tax into an explicit feature.

**⚠ Gate it on equivalence, not on speed.** The failure mode is a stale cache silently pinning the film
to a settle point or door that no longer applies (e.g. after a building switch or a `_metaGen` bump).
**W-REPLAN-CACHE:** for N random band/hose edits, the cached plan's sampled poses must equal the
uncached plan's within 1e-6 m — same discipline as `§INCR_VERIFY mismatch=0` in the Time Machine work,
where an equivalence witness caught exactly this class of bug. Report `§CPE_REPLAN_LAZY hit=<n>
miss=<n> savedMs=<n> prefixMs=<n>` so the saving is a number, not a hope.
**Cost: MEDIUM, and it pays for every other item in this file.** Do it first.

---

## 1. §CPE_HOVER_SCRUB — scrub the film by moving the mouse along the pipe
> User: *"i reckoned it allows a 1 sec stop to take effect of intent? Or a pop up eye 'see'?"*

**Agreed: hover the tube, the camera flies to that point in the film.** The user already named the
value: *"Corelation with the whole pipe during the journey is great instant feedback."* This makes the
pipe scrubbable without committing to a 10 s preview.

**The intent question is theirs and it is the design decision here.** Two shapes, and they are not
equivalent:
- **(a) DWELL** — hover ~1 s over one spot, THEN the camera goes there. Deliberate, no accidental
  flights while reaching for a band handle, but every look costs a second.
- **(b) EYE BADGE** — a small "👁 see" chip appears at the hovered point; the camera moves only if you
  click it. Zero accidental motion, always instant when wanted, one extra click.
**Recommendation: (a) dwell, with the dwell timer visible as a filling ring on the hover point** — it
answers "is it about to do something?" without a second affordance, and the ring IS the feedback.
⛔ **Ask the user which before building.** They floated both and did not choose.

**Consumes, already exists:** `_hitTestPath` (the pipe hit-test, screen-space, already used by
§CPE_HOSE/§CPE_STICK), `plan.poseAt(t)` (pure function of t), `_state.flowFrac`.
**⚠ Traps:** (i) the camera must return to the editing pose on hover-out, exactly as `_previewFly`
does, or the user loses their working viewpoint; (ii) §CPE_PREVIEW_DIVERGENCE — the plan is pinned to
the pose the editor OPENED with, so hover-flying must NOT re-pin it; (iii) `§IDLE_GATE` parks the rAF
chain — hover motion needs `markDirty()` or it will not render.
**Witness:** `§CPE_HOVER t=<f> dwellMs=<n> flewMs=<n> restored=1` + assert the camera pose after
hover-out equals the pre-hover pose within 1e-3 m.
**Cost: SMALL** — half a session. No re-plan involved (this is why it is first).

## 2. §CPE_ROOM_TITLE — the room name appears as the film enters it
> User: *"2 is a wow but should have a checkbox allowing"*

**Agreed, behind a checkbox, OFF by default.** The plan already knows the space —
`§CINEMA_DIVE src=room-graph space="≈ Aras Tanah Hall/Corridor 1" areaM2=131.5`.

**⚠ THE REAL COST IS NOT THE LOOKUP, IT IS THE COMPOSITE.** A DOM overlay will NOT appear in the baked
film: `cinema_maxq.js` captures the RENDERER CANVAS (`_captureFrame`), not the page. So the title must
be drawn INTO the captured frame — either a canvas texture in the scene, or a 2D composite step
between capture and IDB write. The second is simpler and cannot disturb the Alt+S fold. **Do not
prototype this as a DOM caption and declare it done — it will be invisible in the mp4.**
**Also decide (ask):** name per room the walk PASSES THROUGH (needs per-`t` room lookup along the
path), or only the settle room and the exit? The first is the "narrated tour" the user is imagining;
the second is nearly free. Recommend starting with per-room and a 1.2 s fade, since the room graph
already supports containment queries.
**Witness:** `§CPE_ROOM_TITLE t=<f> room="<name>" src=room-graph composited=1` and a pixel assertion
that the captured frame differs from an uncomposited capture in the title band only.
**Cost: MEDIUM** — a session. Most of it is the composite path and the fade timing.

## 3. §CPE_SUGGEST — ⚠ TWO DIFFERENT FEATURES, AND THE USER'S READING IS THE BETTER ONE
> User: *"3 i need more clarification, is this secondary hoses appearing on canvas too as well as in
> panel, and user just accept it adjust to that? I was thinking same thing ie climb the stairscase
> nearby etc"*

**Clarifying honestly: I meant (3a). The user is describing (3b). They are different features and
(3b) is the more delightful one.**

**3a §CPE_SUGGEST_CLIP — best-N-seconds windows.** `§CPE_NOISE_LAW` already scores every probe along
the walk by how fast the bbox neighbourhood is CHANGING (`probes=33 maxChange=2930`). The
highest-scoring contiguous window IS the most visually eventful stretch of the film. Offer the top 3
as one-click §CPE_CLIP presets. **Panel only, no canvas geometry.** The measurement exists; only the
pick is missing. **Cost: SMALL.**

**3b §CPE_SUGGEST_DETOUR — "there is a staircase nearby, want to climb it?"** THIS is what the user
described: proposals rendered ON THE CANVAS as ghost sticks/branches the user can accept, adjust or
ignore. Source is the room graph, not the noise series: find spaces adjacent to the walk that it does
NOT enter — a stair core, a double-height space, a room the path passes the door of — and draw a
dashed ghost branch into it with an accept affordance. Accepting inserts real §CPE_STICK bands.
- **Why it fits this project:** it is the compile-not-model doctrine applied to authoring — the MODEL
  proposes the shot, the user accepts or overrides. Same inversion that makes the derived path
  interesting in the first place.
- **⚠ Do NOT invent interest.** A suggestion must cite what made it a candidate (room type, area,
  vertical connection, floor change) in its own `§` line. A proposal with no traceable reason is
  exactly the invention this project's Prime Directive forbids.
- **Open:** how many at once (recommend ≤3, or the canvas becomes noise), and does declining one
  suppress it permanently for that building?
**Witness:** `§CPE_SUGGEST_DETOUR n=<k> room="<name>" reason=<stair|volume|adjacency> areaM2=<n>
dGraph=<n>` per proposal, plus: accepting one produces bands whose flown path enters that room's
bbox (assert containment, not appearance).
**Cost: 3a SMALL (half a session) · 3b MEDIUM-LARGE (one to two sessions), mostly the candidate query
and making the ghosts readable without cluttering the pipe.**

## 4. §CPE_FILM_AUDIO — sound embedded only when the user has it on
> User: *"yes when V is ON it is recorded so user can control not to have it embedded"*

**Agreed: the film carries audio only if sound is ON at bake time, and the bake states which it did.**

**⚠ HONEST COST WARNING — this is the MOST EXPENSIVE of the six, despite sounding the smallest.**
`cinema_maxq.js`'s mp4 path (`_stitchMp4`) is **video-only**: it configures a `VideoEncoder`, collects
chunks and muxes with `lib/mp4_mux.js`. Adding sound means (i) an `AudioEncoder` (AAC) track, (ii)
interleaving audio and video chunks in the muxer, (iii) producing a continuous audio BED — today's
`§SFX_NAV`/`§SFX_PLAY` are discrete UI events tied to user input, not a timeline, so there is nothing
to record; a bake is silent by construction. So this is really "author an ambience bed keyed to the
beats" PLUS "add an audio track to the muxer", and the webm fallback path needs the same treatment.
**Recommend deferring until 1, 2, 3a, 5 and 6 are in** — it is the only item here that is mostly new
subsystem rather than new use of existing data.
**Witness:** `§MAXQ_AUDIO enabled=<0|1> reason=<sfx-off|sfx-on> track=aac bedSec=<n> muxed=<1|0>` and
a probe that the output container actually HAS an audio track (parse it — do not trust the flag).
**Cost: LARGE** — two-plus sessions, and it touches the mux path every other feature depends on.

## 5. §CPE_FIND_TO_FILM — pick rooms in Find, get a path, film it
> User: *"that is good reuse, open same time Find get room2room pathing was what i tot shuld be done
> there, thus a alt-s button there besides path to '>' Film?"*

**Agreed, and this is the highest PRODUCT value of the six** — "show me the lobby, the ward, the roof"
is what a client actually asks for, and it turns the film from a demo into a deliverable.
Shape: select rooms in the Find panel → room-to-room route → a **`▶ Film`** button beside it hands
that route to the cinema planner as authored bands (the same override object §CPE_IDB_PATH_STORE
already stores), which opens the editor with the path pre-built.
- **Consumes:** the room graph that already picks the dive target and scores exit doors; the
  `bands`-shaped override the plan already accepts; §CPE_STICK's now-arbitrary band count (a 5-room
  tour is simply 5+ bands — impossible before that shipped).
- **Open (ask):** does `▶ Film` bake immediately, or open the editor pre-loaded? Recommend the editor
  — the user has consistently wanted to see and adjust before committing to a cook.
- **⚠ Trap:** the route between rooms must come from the room graph, NOT from straight lines between
  room centres, or the camera walks through walls. §CPE_BANDS rule 8 ("authored is authored") permits a
  user to drag a path through a wall; it does not license the GENERATOR to do it.
**Witness:** `§CPE_FIND_FILM rooms=<n> routeLen=<m> bands=<n> src=room-graph` plus assert the flown
path enters every selected room's bbox, in the selected order.
**Cost: MEDIUM** — a session, assuming room-to-room routing is reusable as-is. If it is not, this
becomes large and should be re-scoped rather than half-built.

## 6. §CPE_SPEED_RAMP — linger here, hurry there
> User: *"6 agreed"*

Per-section pace multiplier, dragged on the pipe: slow the atrium, hurry the corridor.
**⚠ HIGHEST REGRESSION RISK OF THE SIX — it touches the pacing law.** §CPE_EVEN_TURN's whole guarantee
is that frames advance by equal increments of a blended cost, which BOUNDS turn-per-frame and
distance-per-frame. A raw user multiplier on top of that breaks the bound and the jerk comes back —
that lane cost several sessions and three dead ends to settle. **The ramp must be folded INTO the cost
integrand (a per-section weight before `_paceBuildRemap` consumes it), never applied as a multiplier
after it**, and `PACE_SWING` must still bound the delivered range.
**Gate: the existing peak-deg/frame witness must not regress**, exactly as §CPE_AIM_DENSITY was gated.
**Witness:** `§CPE_SPEED_RAMP sections=<n> factors=[…] deliveredRange=<x> peakTurnDeg=<n> (bound=<n>)`.
**Cost: MEDIUM** — a session, but with the highest chance of needing a second one to re-earn the jerk
gates. Do it LAST of the cheap items, and never in the same PR as another pacing change.

---

## Cost summary, and the build order
| # | item | cost | risk | why this order |
|---|---|---|---|---|
| 0 | **§CPE_REPLAN_LAZY** (cache the dive/exit prefix) | medium | low | user's own log shows ~550 ms producing IDENTICAL output on 8 consecutive drags; pays for every item below |
| 1 | §CPE_HOVER_SCRUB | small | low | biggest felt win per line; no re-plan involved |
| 3a | §CPE_SUGGEST_CLIP | small | low | the ranking already exists, panel-only |
| 5 | §CPE_FIND_TO_FILM | medium | medium | highest product value; needs room-graph routing to be reusable |
| 2 | §CPE_ROOM_TITLE | medium | medium | the composite path is the real work, not the lookup |
| 6 | §CPE_SPEED_RAMP | medium | **high** | touches the settled pacing law; gate on peak deg/frame |
| 3b | §CPE_SUGGEST_DETOUR | medium-large | medium | the most delightful, and the most design-dependent |
| 4 | §CPE_FILM_AUDIO | **large** | medium | mostly new subsystem (AAC track + muxer + an ambience bed) |

## ⚖ "Does this push us up the world ranking of BIM apps?" — the honest answer
**No, and it is worth being precise about why, because the honest answer is more useful than yes.**
BIM app ranking is decided by import fidelity, model scale, collaboration/permissions, clash and
quantity workflows, and ecosystem — not by film-making. Nothing in this batch moves those.

**What this batch actually does is make ONE capability distinctive rather than comparable.** The
defensible claim is narrow and true: *a model-derived cinematic path you edit by dragging the flight
itself, baked to photoreal video entirely in a browser tab, with the construction reveal following the
camera.* Every word of that is witnessed. Items 1, 3b and 5 strengthen exactly that claim — the model
proposing shots, and a tour assembled from named rooms, are the parts nobody else's camera tool does,
because nobody else's camera tool knows what a room IS.
**Do not claim "first" or "only" without a survey** — see `project_bim5d_outreach_lane`. One
counterexample makes a good post look sloppy to the audience it is aimed at.

## ⛔ Open questions to put to the user BEFORE building (3 of them change the build)
1. **§CPE_HOVER_SCRUB:** dwell-to-fly, or eye badge + click? (recommended: dwell with a filling ring)
2. **§CPE_ROOM_TITLE:** every room the walk passes through, or only settle + exit? (recommended: every)
3. **§CPE_SUGGEST_DETOUR:** how many proposals on canvas at once, and does declining suppress it for
   that building? (recommended: ≤3, and yes, remembered per building)
4. **§CPE_FIND_TO_FILM:** does `▶ Film` bake immediately or open the editor pre-loaded? (recommended:
   the editor)
