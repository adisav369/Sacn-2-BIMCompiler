# ⚠ DO NOT REMOVE
## ▶ RESUME 2026-08-14 (session end) — read this block first, it supersedes older status prose below
Reveal (Mechanism C) is BUILT AND MERGED: panel #1349, geometry/timeline #1350, visuals #1352, and TWO
real bug-fixes found from live tests — #1353 (buildup topout) and #1354 (preview never replans). **BOTH
FIXES CONFIRMED WORKING on a real building (HHS_Office_Federated) via a fresh Preview log the same
day, independent of the Hospital run that originally found them:** `§CPE_BUILDUP_TOPOUT topoutU=0.333
src=plan.beats.out (reveal round active)` now fires correctly right after `§CPE_REVEAL ON` (previously
stuck at `src=plan.beats.rise`), and `[S200] §DISC_FILTER [MEP]` fires repeatedly through the scrub —
the ARC/STR-hide is genuinely engaging during preview now. User confirmed directly: "Reveal preview POV
timeline issue resolved, and preview does plays back the MEP." **What's left is narrower still:**

0. **⚠ Process note, read before trusting `/home/red1/bim-ootb` as canon again:** mid-session, a
   "cursory examination" read directly from that shared checkout gave FALSE results — its `HEAD` was
   behind `origin/main` (missing #1352/#1353 entirely) AND it had ~117 lines of another session's
   uncommitted, unrelated edits sitting in `viewer/cinema_path_editor.js`. Caught only because the
   evidence (grep for `cpeRevealApplyVisual`) came back empty when it shouldn't have. Every fix this
   session was built and witnessed in a clean worktree off a freshly-fetched `origin/main` — verify
   against that (or `git show origin/main:<path>`), never the shared checkout directly, until this
   project gets the same PreToolUse block bim-ootb's OTHER shared paths already have (see CLAUDE.md's
   own standing warning about this checkout).
1. **CLOSED — SW cache-version was never the real blocker.** #1353+#1354 alone explain everything
   observed, confirmed live on HHS without needing a cache-clear discussion. Still true that no PR in
   this lane bumped `viewer/sw.js`'s `CACHE_VERSION` (still `v1026`) — harmless so far, but bump it on
   the next real content change in this lane rather than relying on luck again.
2. **CLOSED — `§CPE_BUILDUP_TOPOUT` (PR #1353) confirmed live on HHS**, not just witnessed synthetically.
   `topoutU=0.333 src=plan.beats.out (reveal round active)` in the real preview log.
3. **CLOSED — preview-replan (PR #1354) confirmed live on HHS.** `§DISC_FILTER [MEP]` firing repeatedly
   during scrub is the real, user-visible proof the ARC/STR-hide engages in Preview now, not just in a
   witness. User confirmed both the timeline-numbering issue and MEP playback directly.
4. **"Last stick rule" claim — STILL not independently confirmed either way.** User: "code did not obey
   the last stick rule." #1353+#1354 explain every OTHER symptom reported, and the HHS Preview
   confirmation covers the same ground the "last stick" complaint was about (the round behaving
   correctly relative to the stop stick) — but this was never tested as its own explicit claim. Treat as
   very likely already resolved by #1353+#1354; only reopen if something stop-stick-specific is reported
   again after a full bake.
5. **Next verification step, as the user already planned:** bake ONE fresh run (Alt+C, not just
   Preview) — HHS or Hospital — and read the REAL `§CPE_BUILDUP_TOPOUT` / `§CPE_DAY_COUNTER` /
   `§CINEMA_BEATS` / `§CPE_REVEAL_ROUND` log lines from THAT run. Both pasted logs so far were Preview
   runs (`§CPE_PREVIEW click...`), not bakes — Preview is now confirmed working end to end, but the
   actual exported MP4 path (`cinema_maxq.js`'s frame loop, not `_previewFly`) has not been independently
   re-confirmed since #1353/#1354 landed, even though it shares the same `cpeRevealApplyVisual`/
   `buildupTopoutU` calls and should behave identically by construction.

## 6. Surface/material colouring — separate from Mechanism C, hand to its own agent investigation
User, same mp4 review: MEP shows as "metallic blue" where it should read as standard piping/duct
colours (red/brick for fire protection, orange, grey, etc.) — "or the code replaced surfaces in 2nd
round." Also flagged: "the often over use of blue in structure and stairs etc is also boring" — stairs
suggested as concrete grey or cream instead. **User's explicit ask: dispatch this as its own agent
investigation, pointed at whatever existing prompts/# file already owns surface/material color
handling — do not invent a new colour scheme without reading it first.**

**Ruled out already, checked this session, do not re-suspect:** Mechanism C's own `A.filterDiscs`/
`A._applyDiscVisibility` (`panels.js:816-833`) only ever sets `.visible` (plain Mesh) or uses a
zero-scale-matrix/`setVisibleAt` trick (Instanced/BatchedMesh, `helpers.js:36-73`) — none of the three
paths touch `.material`/color/emissive at all. "The code replaced surfaces in 2nd round" is NOT what's
happening; this needs an independent explanation.

**Found this session — read before touching anything, this is a HARD, previously-learned constraint,
not a starting-from-scratch design question:**
- `[[feedback_ifc_colors]]` (memory) — **"Trust IFC colors."** `_getMaterial` (`streaming.js`) uses the
  REAL, PARSED `material_rgba` from the IFC as-is; a class-based fallback (`STD_MAT`) only fires when
  `material_rgba` is NULL. A prior heuristic (`isMonoGrey`/`CLASS_COLOR_FALLBACK`) that overrode
  "monochrome"-looking real IFC colors with class-based ones was REMOVED (commit `9204febc`, S265,
  see `[[project_sc_coloring]]`) because it wrongly clobbered 57% of a real building's genuine (muted,
  earthy) IFC colors. **Read: if MEP genuinely looks "metallic blue," the FIRST hypothesis to check is
  that this is REAL data from the source IFC** (the authoring tool's own default pipe material), not a
  renderer bug — before building any "fix" that overrides real colors, which is the exact mistake this
  memory entry documents already happened once and was reverted.
- **`prompts/MEP_CLASH_REVEAL_MOVIE.md:36-39,106-109`** — this is almost certainly the "related
  prompts/#" the user means. Documents `A.DISC_COLORS` (`viewer/config.js:43`, a 12-discipline hex
  palette) and leaves an EXPLICIT OPEN QUESTION: does the real render path (`_getMaterial`) ever
  consult `DISC_COLORS`, or is that palette only used for placeholders/highlights/nav (never the
  actually-rendered geometry)? Lines 107-108 already predict "blue-duct/yellow-conduit/red-pipe" as
  the convention worth checking for, and propose a discipline-driven base color IF the gap is
  confirmed — read this section fully before proposing anything new, it may already be most of the
  answer.
- **`prompts/WALKER_FIXTURE_RENDER_MATERIAL_AND_GEOMETRY_CHECK.md:26-28`** — a SEPARATE render path
  (walker-placed fixtures) uses a flat `DW_COLOR[disc]` per-discipline color, no `material_rgba` read
  at all — distinct from streaming.js's real-IFC-color-first path. Worth checking which path Hospital's
  MEP geometry actually goes through before assuming it's the same mechanism `MEP_CLASH_REVEAL_MOVIE.md`
  is asking about.
- `docs/FeatureComparison.md:43` — the DOCUMENTED, intended class-based-fallback behavior: "concrete is
  warm grey, steel is reflective, glass is smooth and blue-tinted" — glass, not MEP/structural, is the
  one thing genuinely SPECCED to default blue. If stairs/structure are reading as blue, that is either
  real IFC data, or a class landing in the wrong fallback bucket — not the glass rule misfiring on the
  wrong geometry, unless that's independently confirmed.

**Real design change from the original ask, decided live before building (not silently invented):**
the "20% translucent ARC/STR ghost" is a FULL HIDE instead — checked live on Duplex, ~80% of ARC/STR
geometry is batched/instanced with materials SHARED across disciplines by colour (not owned per-
discipline), so a scoped translucent fade needs per-instance shader work, real effort out of scope
for this pass. Full hide reuses `A.filterDiscs` (already existed) and gets "sunlight plays through"
for free (Three.js excludes invisible objects from the shadow pass) — the user picked this explicitly
over building the shader work. If true partial translucency on batched/instanced content is ever
wanted, that is a real, separate, named follow-up (per-instance alpha via a custom shader or material
cloning), not a quick tweak.

Mechanisms A and B below are SUPERSEDED
by §Mechanism C — the retrace reveal round (2026-08-14, same session, converged through live design
back-and-forth with the user), which folds both into one concrete design, NOW BUILT — see §Mechanism
C's own status note for exactly what shipped and what (if anything) is still open. Read the log after
every run once build
starts. Origin: user idea, discussed same session as the doors-mystery handoff and the prompts/ bloat
consolidation (see `prompts/4D_SCHEDULE_PERFECTION.md` for the unrelated active bug, not this file).

**Built 2026-08-14 (user, "begin to construct that new feature into the alt-c panel as 'Reveal' just
beside the show room titles box"), bim-ootb PR #1349:** a 'Reveal' checkbox now exists in the Alt+C
Film-Maker panel, same row as 'room titles' — `viewer/cinema_path_editor.js` (`#cpe-reveal`, `_state.reveal`/
`origReveal`, threaded through `_buildOverride`/`_isEdited`/`_capturePanelState`/`_syncPanelControls`/
`_applyPanelState`/`_pathsApply` exactly like `roomTitle`) and `viewer/cinema_maxq.js` (`_reveal` captured
at bake time from `_ov.reveal`). **Panel/state plumbing only — no render or pacing behavior.** Hint text
next to the checkbox and the bake-time console log both say so explicitly, so an ON flag is never
silently swallowed or mistaken for a working feature. Witnessed: `witness_cpe_reveal_panel.js` 7/7
(checkbox exists in the same row, off by default, click toggles + fires `§CPE_REVEAL`, OK-with-only-this-
box still returns `override.reveal=true` per Guardrail 2, zero pageerrors); regression-checked
`witness_cpe_room_title.js` still 11/11 (every shared function this touched still clean).

## Mechanism C — retrace reveal round (2026-08-14, supersedes A+B below, BUILT — PR #1350 + #1352)
Converged live with the user through several corrections this session — this is the concrete design to
build, superseding Mechanism A's "scattered pulses along the outbound walk" and Mechanism B's "cycle
during the closing orbit itself" framing. Both problems A and B were trying to solve (MEP visible
in-transit; a per-discipline parade before the film closes) are folded into ONE extra round, inserted
between the outbound walk and the closing orbit.

### The flow, user's own words assembled from the design conversation
1. **Normal** — dive → spin → walk-the-bands, completely unchanged, arriving at the walk's own end
   (the LAST band/stick — see "STOP stick" correction below) exactly as every film does today.
2. **If Reveal is ON, at that point the closing orbit is SUSPENDED (not started yet) and a new round
   begins:**
   a. **Backward leg** — camera retraces the SAME walked path from the last stick back to the first
      stick ("not absolute start but where the first stick is"). No ghost effect specified for this
      leg — plain travel back to the beginning. *(Assumption, not stated explicitly — flagged, not
      invented as fact.)*
   b. **Forward leg** — camera walks the same path again, first stick → last stick ("resume till last
      stick as usual"). This time ARC/STR is ghosted to ~20% opacity for the whole leg, and any
      discipline the camera comes across (proximity-triggered — this is Mechanism A's original density
      idea, now running continuously through this leg instead of only at scattered stop-sticks) reveals
      through the ghost.
   c. **Tail — discipline parade, easing into the orbit** — as this forward leg closes in on the last
      stick, cycle each discipline present in the building one at a time, ~2s each, with ARC/STR FULLY
      REMOVED (not 20% — gone) for that window; camera eases to near-standstill for each; continues
      until every discipline present has had its turn ("exhausted"). Carries over, not restated but not
      contradicted, from the earlier 2026-08-14 pacing quote: a final ~2s with all disciplines shown
      together before ARC/STR covers back — still standing unless the user says otherwise.
   d. ARC/STR restores solid, the suspended orbit resumes and closes the film exactly as it does today
      — Beat 4 (turn-and-rise) → orbit, UNTOUCHED code, per the research below.
3. **Time Machine buildup — CHECKED, not frozen (revised 2026-08-14, supersedes the "disengaged" ask
   below the line).** User asked to verify rather than assume: "the stop or last stick does not stop
   buildup it seems. Check that first." **Confirmed in code, `cinema_maxq.js:144-168`
   (`§CPE_BUILDUP_TOPOUT`, shipped 2026-08-02):** buildup is deliberately paced to reach 100% at
   `plan.beats.rise` — the START OF THE ORBIT — not at the stop stick. It is explicitly still climbing
   through the whole turn-and-rise beat today, on every existing film; that's the fix that stopped
   Hospital's roof/solar panels finishing inside the closing orbit's last ~1.4s where nobody could see
   them land. **Consequence for round 2: do NOT freeze/disengage buildup.** Once round 2 is inserted
   between the walk and the (now-later) orbit, `plan.beats.rise` shifts later automatically, and the
   EXISTING `_buildupTAt`/`_workCursorAt` machinery keeps buildup climbing smoothly through round 2,
   finishing right before the (shifted) orbit start — same mechanism every film already uses, zero new
   pacing code needed. This also means buildup is naturally closest to complete near round 2's TAIL
   (approaching the last stick again), which is exactly where step 2c's discipline-parade/full-reveal
   already sits — the two were never actually in conflict once this was checked. The §STOP-stick reveal
   breather section below is RETRACTED by the user for the same reason ("since we are flying thru 2nd
   round, thus we need not adjust present stop to have all finishes ended before it") — moot now that
   round 2 gives buildup the extra real seconds it needs, no 2s-margin bookkeeping required.
4. **Preview parity, hard requirement** (user, 2026-08-14: "during preview, it can also go along so
   user confident it is working"): round 2 must play in the live in-editor Preview/scrub fly-through,
   not only the exported bake — the user wants to see it working before committing to a full export.
   Precedent already in this codebase: `roomTitle`'s `roomTitleLiveStart`/`roomTitleLiveTick`/
   `roomTitleLiveStop` triple, called from the preview-fly tick loop AND driving the same visual state
   the bake reads — same pattern to follow here, not a new architecture.

### Corrected terminology and facts (code research this session, viewer/effects.js + viewer/tools.js +
viewer/panels.js — read this before writing anything, several things in Mechanism A/B below drifted)
- **Beat order of a baked film**: dive → spin → walk (bands/hose) → turn-and-rise → orbit. Composed by
  `poseAt(tNorm)` inside `_cinemaPathPlan()`, `viewer/effects.js:4687` (builder) / `~6296-6360`
  (`poseAt`'s if/else chain over beat boundaries `tD,tS,tO,tR`). `cinema_maxq.js` does not reimplement
  this — it calls `plan.poseAt(tNorm)` (`cinema_maxq.js:862-868`).
- **"STOP stick" is a real, specific thing in the code**: `_labelOf()`/`ROW_LABEL` in
  `cinema_path_editor.js` (~602-623) name exactly the LAST band `'stop'` — "end of the WALK, not the
  film; its far end stretches the orbit." That is what "the STOP stick"/"last stick" means throughout
  this file. (Separately, the `hold>0` pause mechanism — §CPE_STICK_HOLD — can fire on ANY interior
  band, not just this one; do not conflate "a stick with a hold" with "the STOP stick.")
- **The discipline code is `ARC`, not `ARCH`**, in `A.DISC_COLORS` (`viewer/config.js:43-48`). This
  file has been writing "ARCH/STR" — that's this file's own drift, not the user's; the user's own
  quotes always said "ARC/STR." Use `ARC` going forward to match the real data.
- **The closing orbit does not care where the camera physically is.** Its start pose (`pivot`,
  `exitAz`, `orbitRadius`) is computed once from the ARC bounding box + exit-door geometry — nothing
  runtime-camera-dependent (`viewer/effects.js:4709-4718`, `5241`, `5250-5256`, `6217-6259`). The
  turn-and-rise beat (Beat 4, `viewer/effects.js:6361-6374`) is what bridges "wherever the camera last
  was" to that fixed pose, and lands exactly on it by construction. **This means round 2 can be inserted
  right after the outbound walk (Beat 3) and hand off into the existing Beat 4→orbit unchanged**, as
  long as Beat 4 receives round 2's actual end pose as its new starting point instead of the walk's raw
  `exitOuter` — and since round 2 ends back at the last stick (same point `exitOuter` already is), Beat
  4 needs NO retuning at all.
- **No reverse/retrace utility exists** (checked `cinema_path_editor.js`, `cinema_maxq.js`,
  `effects.js` for reverse/retrace/backward — nothing). Don't reverse the `bands` array — the walk is
  already parameterised by fraction `f∈[0,1]` via `_outPos(f)` (`viewer/effects.js:5120`, called from
  Beat 3's `_beat3Pose`). Round 2's backward leg is just `_outPos(f)` sampled 1→0; the forward leg is
  the same curve sampled 0→1 again. Reuses the exact walked geometry, no second path to keep in sync.
- **A full-hide, discipline-scoped primitive already exists and is reusable as-is**:
  `A.filterDiscs(list)` / `A.filterDisc(disc)` (`viewer/panels.js:746-779`) + `A._applyDiscVisibility`
  (`~816-833`) — shows ONLY the disciplines named, hiding the rest via `.visible=false` on every
  mesh/instanced/batched element keyed by its own `userData.disc`/`meta.disc`. This is exactly the tail
  step (2c above, "ARC/STR FULLY REMOVED") — call it with the complement of `['ARC','STR']`. Because
  it's a hard `.visible=false`, Three.js already excludes it from the shadow pass for free — this is
  the primitive that makes the "sunlight plays through" full-reveal moment work correctly with zero
  extra shadow code.
- **No existing primitive does a discipline-SCOPED translucent ghost** (the 20% forward-leg effect,
  2b above). `A.toggleXray` (`viewer/tools.js:228-267`) is the only opacity-ghost mechanism that exists,
  and it is global — every material, no discipline filter. Building the scoped version means mirroring
  its opacity-flip logic but walking only ARC/STR elements, using the same `userData.disc`/`meta.disc`
  lookup `filterDiscs` already reads.
- **`toggleXray` never touches `castShadow`** (confirmed reading `tools.js:228-267` line by line — only
  `transparent`/`opacity`/`side` change). A translucent mesh still casts a full, solid-looking shadow in
  Three.js by default. For the user's "will sunlight play through" ask during the 20% forward leg (not
  just the fully-hidden tail, which already gets this for free per above): the new scoped-ghost function
  must ALSO set `castShadow=false` on the same ARC/STR elements while ghosted, and restore it after —
  this is real, separate work, not a side effect of the opacity change.

### What actually got built (2026-08-14, bim-ootb PR #1350 geometry + #1352 visuals)
- **New beat inserted between Beat 3 (walk) and Beat 4 (turn-and-rise)**, `effects.js`'s
  `_cinemaPathPlan`/`poseAt`, gated on a new `_cpeReveal` module var (set from `ov.reveal` in
  `A.cinemaPathPlan`'s wrapper, same pattern as `_cpeBands`/`_cpeHose`). A new boundary `tV` (between
  `tO` and `tR`) is zero-width — `tV===tO` exactly — whenever reveal is off or the building has no
  non-ARC/STR discipline, so `poseAt`'s new branch is unreachable and every existing film is
  byte-identical to before this feature (Guardrail 2, verified).
- **Duration is real and additive**, not squeezed from the existing runtime: `A.cpeRevealDiscsPresent()`
  (one shared implementation, called from both `effects.js`'s plan builder and
  `cinema_path_editor.js`'s own `_naturalDuration()`) counts actual live non-ARC/STR elements; the
  round costs `2×(walk length/speed) + 2s/discipline + 2s`, and the panel's own duration estimate
  includes it so `nFrames` grows to fit.
- **Position** reuses `_outPos(f)` — the exact curve Beat 3 already walks — sampled backward (1→0)
  then forward (0→1) again. No reverse-path utility existed or was needed.
- **Gaze**, found and fixed while witnessing (not assumed correct on paper): the round's seam blends
  must anchor to the ACTUAL rate-limited gaze Beat 3/4 render at `tO`/`tV` (`_gazeRateAt`), not the
  raw `_beat3EndDir` — anchoring to raw measured a real 63°/12° instantaneous jump on Duplex; now
  0.39°/0.02°. A latent bug in `_gazeRateBuild`'s own copy of the Beat 4 formula (still referencing
  the old `tO` boundary) was caught and fixed at the same time.
- **Visual layer — real design change from the original ask, decided live, not silently substituted:**
  the "20% translucent ARC/STR ghost" became a FULL HIDE. Checked live on Duplex: ~80% of ARC/STR
  geometry is batched/instanced with materials SHARED across disciplines by colour, not owned
  per-discipline — true partial translucency needs per-instance shader work, real effort out of scope
  for this pass. Full hide reuses `A.filterDiscs` (already existed, `panels.js` §NAV_FIND_002) and gets
  "sunlight plays through" for free (Three.js excludes invisible objects from the shadow pass) — no
  `castShadow` code needed after all. `A.cpeRevealVisualAt(plan, tNorm)` is a pure function (phase +
  which discs to show, computed from `plan.beats`/`plan.reveal`); `A.cpeRevealApplyVisual(plan, tNorm)`
  applies it, snapshotting whatever discipline filter was already active (Role filter, Find isolate) on
  entry and restoring exactly that on exit — never a blind "show everything" — and skips the
  scene-traversing `_applyDiscVisibility` call when nothing changed since the last tick.
- **Preview parity** (user: "during preview, it can also go along so user confident it is working"):
  both `cpeRevealVisualAt`/`cpeRevealApplyVisual` are called identically from the bake loop
  (`cinema_maxq.js`, per-frame) and the preview-fly tick loop (`cinema_path_editor.js`'s
  `_previewFly`) — camera geometry already gets this for free (both read the same `plan.poseAt`), the
  visual layer needed the explicit second call site. Restore-on-exit added to both loops' existing
  exit contracts (same pattern as `ghostGroundRestore`/`roomTitleLiveStop`) — normal completion,
  cancel, and the bake's throw/finally path.
- **TM buildup — checked, not frozen** (see the dated note above): naturally keeps climbing through
  the round via the existing `plan.beats.rise` topout mechanism, no new pacing code needed.
- Witnessed 27/27 (`witness_cpe_reveal_round.js`) + regression-clean on `witness_cpe_reveal_panel.js`
  (7/7) and `witness_cpe_room_title.js` (11/11).

### Retired / superseded open items (were unresolved when this file was spec-only, resolved by the
above before or during build — kept for the record, not to be re-litigated)
- ~~Backward leg ghost?~~ Resolved: plain retrace, no visual override — confirmed correct, matches
  the user's own description of the flow.
- ~~Forward leg pace?~~ Resolved: same speed as the outbound walk (`totalLen/CINEMA_WALK_MPS`).
- ~~Whitewash risk for a translucent ghost (Open Question 1)?~~ MOOT — there is no translucent ghost;
  full hide has no coverage-stacking failure mode to measure.
- **Still genuinely open, not addressed by the above:** the density-threshold question from the
  original Mechanism A ("comes across" a discipline) doesn't apply to the built design either — the
  forward leg now shows ALL non-ARC/STR disciplines together for its whole duration, not a
  proximity-triggered subset. If a future ask wants the forward leg to be selective (only reveal what's
  actually nearby, not everything at once), that is new scope, not a bug in what shipped.

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
- **Orbit is 3 phases, not a flat shot throughout (2026-08-13, user, verified against code)** —
  `tour.js:1506-1519`, the `orbit` action, is 3 phases against progress `to` (0→1):
  1. `to < 0.2` — climb-in from the approach pose up to the orbit's elevated height.
  2. `0.2 ≤ to < 0.6` — **plateau at full `tiltDeg` (35-40°, an "atop"/elevated look-down angle)**,
     constant height (`orbitH`).
  3. `to ≥ 0.6` — descent: `effectiveTilt = tiltRad * (1 - descentProgress²)` unwinds the tilt toward
     flat/level WHILE camera height falls toward `_groundY`, both finishing together at `to = 1`.
  The discipline-cycle/solidify choreography needs to be keyed to these same three phases rather than
  assuming a constant camera angle and running an independent caption timer alongside it.
- **Pacing/dwell model (2026-08-14, user, verbatim):** "slowing down the cam last move after last
  element mesh appearance? That slowing down is almost standstill adding perhaps 2 secs for each DISC
  with final 2 secs is all DISC together before ARC/STR covers back and path resumes as normal. **No
  change in path.**" Read as the timing model for the phase-mapping above: the slow-down is
  EVENT-triggered (the last element mesh of the current discipline appearing), not a fixed clock offset
  — camera speed eases toward near-zero at that moment, holds ~2s per discipline while its caption
  reads, then ~2s once more for all-disciplines-together, then resumes normal orbit speed as ARCH/STR
  solidifies. Pacing/dwell only, added ON TOP of the existing baked orbit geometry — path
  (position/radius/tilt) untouched, the same "pure render-layer, camera advancement untouched"
  constraint already stated for Mechanism A.
- **Not yet reconciled (one open item — this used to be duplicated across two sections, consolidated
  2026-08-14):** how the ~2s×N-discipline dwell fits inside the 3-phase orbit above — almost certainly
  the elevated plateau (phase 2, already the natural fit for "widest/most stable view"), without
  stealing time from climb-in or descent, or the orbit's total duration needs to grow to absorb it.
  Decide once Open Question 1 (render approach) is settled — render approach affects how long each
  discipline actually needs to read on screen.
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

## STOP-stick reveal breather — RETRACTED (2026-08-14, user, superseded by §Mechanism C)
**Retracted by the user same day it was written:** "since we are flying thru 2nd round, thus we need
not adjust present stop to have all finishes ended before it." Moot once round 2 exists — buildup keeps
climbing through round 2 exactly as it already does through today's turn-and-rise beat
(`§CPE_BUILDUP_TOPOUT`, see §Mechanism C item 3), so round 2's own length is the breather; no schedule-
side or stick-placement enforcement needed. Kept below for the record, not to be resumed.

**Original text (historical, do not act on this):** Now that MEP's 4D schedule is pushed toward the end of
the buildup timeline (schedule-generator side, out of scope for this file), the risk is the LAST MEP
mesh appearing AT OR AFTER a STOP stick's own timestamp — leaving zero runway for either the
discipline-parade dwell (Mechanism B, above) or the ambient pulse (Mechanism A, which fires only from a
STOP stick) to actually read before the path holds/resumes. User's own framing: **the very last mesh
appearance must land at STOP-stick time minus 2s, never after** — a minimum 2s breather between "last
thing appears" and "path holds at the stick," so the reveal has room to sink in rather than firing on
top of geometry that is still appearing.
- **Where this constraint is enforced is NOT decided** — could be the schedule generator leaving 2s of
  slack before a stick's timestamp, or the stick-placement logic checking the schedule and
  refusing/nudging a stick that would otherwise land within 2s of the last mesh. Needs a design pass;
  not assumed to be either side by default.
- **Relates to, does not resolve:** the open Auto-4D heavy-item-duration item ([[project_cpm_4d_generator_lane]],
  #1317, still open) — that lane controls WHEN MEP elements finish appearing; this constraint is a
  downstream consumer of that timeline, not a fix to it.
- **Interacts with Open Question 2** (density threshold for Mechanism A's trigger) — a stick that fails
  the 2s-breather check may also need excluding from firing a pulse at all: nothing has finished
  revealing yet to reward looking at.
- Not built. Same "measured, not eyeballed" discipline as everything else here — first step once
  prioritized is measuring how often a real bake's STOP sticks currently violate this 2s minimum, on a
  building with MEP scheduled late (Hospital or Terminal), before designing the fix.

## Open questions (Mechanism A/B, RETIRED — kept for the record, not applicable to the built §Mechanism C)
Mechanism C's own build notes above are the current status; these predate it and were never
individually re-answered once C replaced A/B wholesale — most are moot by construction (there is no
translucent ghost to whitewash, no proximity-triggered pulse, no discipline caption/HUD in what
shipped). Do not resume any of these unprompted; they describe a design that was superseded, not one
still being decided.
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
6. ~~STOP-stick reveal breather enforcement point~~ — **RETRACTED 2026-08-14**, see the dedicated
   section above. Superseded by §Mechanism C existing at all.

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
BUILT AND MERGED, 2026-08-14 — bim-ootb PR #1349 (panel), #1350 (geometry/timeline), #1352 (visuals),
#1353 (buildup-topout fix), #1354 (preview-replan fix). Both #1353/#1354 were found from the user's
OWN live Hospital test, not pre-emptively — see the `▶ RESUME` block at the top of this file for the
full diagnosis chain and what's left to re-verify. The 'Reveal' checkbox in the Alt+C Film-Maker panel
now bakes AND previews a real out-and-back retrace round with ARC/STR hidden to show every other
discipline, cycling one-at-a-time in the tail, buildup guaranteed complete before the round starts.
Witnessed 36/36 on `witness_cpe_reveal_round.js` + regression-clean on both sibling witnesses.
Mechanisms A and B (and their own Open Questions above) are retired, superseded by §Mechanism C
wholesale — do not resume them unprompted. Named, real follow-up if ever wanted: true per-instance
translucency for the ~80% of ARC/STR geometry that's batched/instanced (would need custom shader work
or material cloning) — full hide was the user's own explicit choice over building that, not a
placeholder awaiting it.

## Findings 2026-08-14 — surface/material colour investigation (dispatched agent, §6 handoff)

**Task:** answer why HHS_Office_Federated's cinema-reveal bake shows MEP as "metallic blue" instead of
trade colours, and STR/stairs as "boring blue," per §6 above. Investigation only, per the handoff's own
instruction — nothing built, nothing pushed.

### Method — real numbers, not a guess
Queried `buildings/HHS_Office_Federated_extracted.db` (`elements_meta` table) directly — the actual
extracted DB this building's bake reads from — and traced `A._getMaterial`
(`viewer/streaming.js:338-491`, read at `origin/main` via `git show`, since the shared `~/bim-ootb`
checkout was dirty + stale this session, same caution the `▶ RESUME` block at the top of this file
already flags) line by line, plus every `A.DISC_COLORS` consumer across the whole `viewer/` tree
(`git grep DISC_COLORS origin/main -- viewer/`).

### §6's three named things, resolved
1. **`[[feedback_ifc_colors]]` "trust IFC colors" — checked, correctly implemented, NOT the bug.**
   `_getMaterial` line 475: `if (!rgbaStr && stdMat) { ... }` — the `STD_MAT` class fallback strictly
   only fires when `material_rgba` is NULL/empty. Confirmed real IFC colour is used as-is whenever
   present. This part of the code is doing exactly what the doctrine requires.
2. **`MEP_CLASH_REVEAL_MOVIE.md`'s open question — ANSWERED, definitively: `_getMaterial` NEVER reaches
   `A.DISC_COLORS`.** Grepped every `DISC_COLORS` reference in `viewer/` (13 files: `city.js`,
   `dlod_nav.js`, `export_5d.js`, `measure.js`, `navigate_find.js`, `panels.js`, `time_machine.js`,
   `streaming.js:266`, plus standalone copies in `boq_charts.html`/`import.js`/`rates.js`/
   `wizard_classify.js`). Every single one is a placeholder/highlight/nav-minimap/UI-swatch/BOQ-chart/
   import-preview/wizard-preview consumer. `streaming.js:266` — the one hit inside `streaming.js`
   itself — is `_drawBboxPlaceholders`, the wireframe LOADING placeholder shown before real geometry
   streams in, not the real render. `_getMaterial` (the function that actually ships colour in a
   recorded/baked frame) has zero references to `DISC_COLORS` anywhere in its 150-odd lines. Confirmed,
   not assumed.
3. **`docs/FeatureComparison.md:43`'s glass-blue rule — real, and NOT what's causing MEP/STR/stairs.**
   Checked directly: `IfcCurtainWall`/`IfcWindow` STD_MAT entries are the only two genuinely blue-tinted
   glass entries, and HHS's own curtain-wall glazing (438 `IfcPlate` elements, real populated
   `material_rgba` = `0.502,0.502,1.000,0.250` — genuinely blue, 25% opaque) independently confirms this
   convention is real and is being read correctly. This is a real, correct, unrelated contributor to the
   overall "everything reads blue" impression in the shot — not a misfire, not MEP/STR's cause.

### The real root cause — direct DB query, `elements_meta` in `HHS_Office_Federated_extracted.db`
```
discipline counts:      MEP=3399  ARC=1774  STR=1707
MEP material_rgba:      NULL=3390 (99.7%)   has_rgba=9
STR material_rgba:      NULL=0    (0%)      has_rgba=1707 (100%)
```
**MEP — root cause (b)+(c), a real, confirmed gap, NOT real IFC data:**
Essentially every MEP element in HHS (3390/3399) has NULL `material_rgba`. Their `ifc_class` breakdown:
`IfcFlowFitting`=1381, `IfcFlowSegment`=1284, `IfcFlowTerminal`=725 — exactly the "IFC2x3 generic-MEP
convention (Clinic/LTU/**HHS**...)" `streaming.js` already names in its own comment at line ~457. All
three land in `STD_MAT` (streaming.js:376-378):
```
IfcFlowSegment:  r:0.48 g:0.52 b:0.58  rough:0.40 metal:0.30
IfcFlowFitting:  r:0.50 g:0.53 b:0.57  rough:0.40 metal:0.30
IfcFlowTerminal: r:0.45 g:0.50 b:0.55  rough:0.40 metal:0.30
```
All three are blue-leaning (b>r), moderate-metal, low-roughness — glossy, cool-toned metal. Under this
viewer's PBR pipeline (envMapIntensity 0.6 + ACES) this is exactly what reads on screen as "metallic
blue." **This is effectively HHS's WHOLE MEP discipline** — confirmed by direct query, not eyeballed.
It is a discipline-BLIND class fallback: `STD_MAT` is keyed only on `ifc_class`, and IFC2x3's generic
`IfcFlow*` classes carry no trade information in the class name itself — so fire protection, plumbing,
HVAC, all identically-classed elements get the identical flat blue-grey metal look regardless of
`elements_meta.discipline` (FP/PLB/ACMV/etc.), even though that column is already populated and already
selected in the same SQL row (`streaming.js:109,170,2067` all `SELECT ... m.discipline ...`).
`A.DISC_COLORS` already has the variety the user wants (FP=`0xcc8844` brick/orange, ACMV=`0xcc4444`
red, PLB=`0x8844cc` purple, HEAT=`0xff6644` red-orange) — it is simply never consulted here.

**STR — root cause (a), genuinely real IFC-authored data, per doctrine correctly trusted, no code fix
warranted:** 100% of STR elements (1707/1707) have real, populated `material_rgba`.
`IfcMember` (1450 elements, `element_name` = "Rechteckiger Pfosten..." — German for curtain-wall
mullion/post, "with cover profile") carries `material_rgba = 0.384,0.400,0.463` and
`material_name = "≈ Purple"`. `IfcColumn` (257) carries `material_rgba = 0.937,0.969,0.969` and
`material_name = "≈ White"`. The `≈` prefix is the exporting authoring tool's own generic
approximate-colour naming for an unassigned/default material — not a deliberately chosen "structure =
blue" trade convention by anyone — but it is genuinely real, populated data sitting in the source IFC.
Per `[[feedback_ifc_colors]]`, this must NOT be overridden by any fix; the correct action is telling the
user this is a fact about HHS's own source authoring, not a renderer bug.

**Stairs — root cause (c), same fallback-bucket mechanism as MEP but a DIFFERENT class, not the stair
tread itself:** `IfcStair` (12) + `IfcStairFlight` (8), all 20 elements NULL `material_rgba`, all
discipline ARC. `STD_MAT[IfcStair]` = warm grey (`r:0.68 g:0.66 b:0.63`, metal:0.00) — NOT blue.
`IfcStairFlight` has no `STD_MAT` entry at all → the generic default (`r=g=b=0.7`, metal:0.08) — also
NOT blue. **The stair tread material is not the source of "boring blue."** The far more likely
contributor: HHS's 14 `IfcRailing` elements (`element_name` = "Railing:Stahl (1) - Horizontal" — German
for steel railing), both `material_name` AND `material_rgba` blank/NULL, landing in
`STD_MAT[IfcRailing]` = `r:0.40 g:0.42 b:0.45, metal:0.55` — the single bluest-leaning, most reflective
entry in the entire `STD_MAT` table. Stair railings are visually prominent, highly reflective, and
directly adjacent to every stair a camera passes — the most plausible source of "stairs read blue,"
same fallback-bucket mechanism as MEP, a different `ifc_class`.

### Verdict, per §6's (a)-(d) options
Not a single answer — genuinely a mix, confirmed with real numbers for each piece:
- **MEP "metallic blue" = (b)+(c) combined, a real and fixable gap.** `A.DISC_COLORS` exists and already
  has the trade variety wanted; `_getMaterial` never reaches it; HHS's actual MEP data (IFC2x3 generic
  `IfcFlow*` classes, 99.7% NULL rgba) falls into a class-only, discipline-blind fallback bucket that
  happens to be uniformly blue-grey metal for every trade alike.
- **STR "boring blue" = (a), real data, no fix.** 100% populated, genuinely blue-leaning per the source
  IFC's own generic material naming — this is a fact to tell the user, not a bug to patch.
- **Stairs "boring blue" = (c), same mechanism as MEP, wrong element (railings, not treads).** The tread
  fallback is already warm/neutral grey; the adjacent steel railings' fallback is the bluest, most
  metallic entry in the whole table.
- **A fourth, unprompted finding:** HHS's curtain-wall glazing (438 `IfcPlate`, real blue-translucent
  data) reinforces the overall "everything reads blue" impression across the whole bake, independent of
  MEP/STR — correct behaviour per the documented glass-blue rule, not a bug, just worth naming so the
  next session doesn't re-attribute it to the MEP or STR mechanisms above.

### Precise fix — named, NOT implemented (per this task's explicit scope)
Thread `discipline` (already selected in every relevant SQL row, `streaming.js:109,170,2067`, column
index 3 in the row array — see `_drawBboxPlaceholders`'s own `rows[i][3]` read at line 246 for the same
pattern already in use elsewhere in this file) as a 4th argument:
`A._getMaterial(rgbaStr, ifcClass, matVariant, discipline)`. Inside `_getMaterial`, for exactly the
discipline-ambiguous IFC2x3 generic classes (`IfcFlowSegment`, `IfcFlowFitting`, `IfcFlowTerminal`,
`IfcFlowController`, `IfcFlowMovingDevice`, `IfcFlowTreatmentDevice`, `IfcEnergyConversionDevice` — the
ones whose `STD_MAT` entries currently share one flat "generic metal" look regardless of trade), when
`!rgbaStr` (still real-IFC-colour-first, unchanged), tint the base colour toward
`A.DISC_COLORS[discipline]` instead of the current flat blue-grey value — reusing DISC_COLORS' EXISTING
12 hex values, inventing nothing new, exactly what `MEP_CLASH_REVEAL_MOVIE.md`'s own item 5 already
specified. Does **not** touch classes with real material-specific `STD_MAT` data already (`IfcPipe`/
`IfcDuct`/`IfcCableCarrier` in the IFC4 convention already look like pipe/duct) and does **not** touch
anything with a real, non-null `material_rgba` (STR/glazing stay exactly as they are, per doctrine).
Every `_getMaterial` call site (`streaming.js:1228,1281,1360,1482,1630,1652,1828`) already has the same
row that supplies `rgba`/`ifcClass` — this is a threading change, not a new query.
Separately flagged, not resolved: whether `IfcRailing` should also join this discipline-tint list, or
get a distinct warmer/neutral-metal `STD_MAT` value instead (railings aren't really a "trade," so tinting
them toward `DISC_COLORS[ARC]` — itself blue, `0x4488ff` — would not obviously fix "stairs read blue");
needs its own small decision, not assumed here.

### Note on `A.DISC_COLORS` itself
Even if the fix above ships, `A.DISC_COLORS.STR = 0x44cccc` (teal/cyan) and `.ARC = 0x4488ff` (blue) are
themselves cool-toned — the palette does NOT give a warm/grey/brownish option for structure by default.
The user's ask for structure/stairs specifically ("brownish, grey-metallic") is not answered by wiring
DISC_COLORS in; it would need either a different STR hex value in `config.js:43-48`, or (since STR's
actual HHS colour is real IFC data anyway, per the verdict above) no code change at all — just user
awareness. FP (`0xcc8844` brick/orange), ACMV (`0xcc4444` red), PLB (`0x8844cc` purple), and HEAT
(`0xff6644` red-orange) already cover the "red/brick/orange" MEP variety the user asked for, unchanged.

## Findings 2026-08-14b — Hospital beams/railings still read blue AFTER #1356 ships (real IFC albedo,
## not a NULL-rgba case — a different, more fundamental mechanism than the finding above)

**Task:** after `#1356` (MEP_DISC_TINT + `IfcRailing` STD_MAT recolor to warm grey) shipped, the user
re-checked on Hospital and beams/railings still read blue. Hospital's `IfcBeam`/`IfcRailing` elements
have REAL, populated, warm `material_rgba` (unlike HHS's NULL-rgba MEP case above) — so the STD_MAT
NULL-fallback mechanism this whole §6 thread has been fixing so far cannot be the cause here. Worktree
`/tmp/wt-hospital-blue-fix` (branch `fix/hospital-blue-color`), investigation only, clean tree, nothing
built yet.

**Method, per explicit user steering correction this session: lead from the formula/config tables and
real PBR math, DB/live-pixel readback as confirmation only, not primary discovery.**

### 1. The formula tables, read directly (not queried)
- `viewer/streaming.js` `_getMaterial`, `STD_MAT.IfcBeam` = `{ rough: 0.35, metal: 0.65 }` (steel
  I-beam), `STD_MAT.IfcRailing` = `{ rough: 0.35, metal: 0.55 }` (brushed-steel, already recolored
  warm-grey by #1356 — the recolor only changed `r/g/b`, not `rough`/`metal`).
- **The actual bug, found by reading the assignment logic, not a DB row:** `var stdMat = (ifcClass &&
  STD_MAT[ifcClass]) ? STD_MAT[ifcClass] : null;` is keyed on `ifcClass` alone — no `rgbaStr` check.
  Two lines later, the color assignment correctly gates on real-vs-fallback (`if (!rgbaStr && stdMat)
  { r = stdMat.r; ... }` — trust-IFC-colors, doctrine honored). But roughness/metalness do NOT share
  that gate: `opts.roughness = Math.max(0.08, _rough * 0.75); opts.metalness = stdMat ? stdMat.metal :
  0.08;` — both keyed on `stdMat` truthy (i.e. class found in the table) alone. **A beam with 100% real,
  correctly-populated, warm IFC albedo still gets forced into STD_MAT's metal=0.65/rough≈0.26 "steel
  armor" PBR response**, because IFC extraction never carries physical metalness/roughness — those two
  channels are ALWAYS class-table defaults, real color or not. This is an asymmetry in how the
  "trust IFC colors" doctrine got implemented: honored for hue, silently not applicable to the two
  channels that actually control how much of the final pixel is environment-reflection vs albedo.
- `envMapIntensity = 0.6` (`streaming.js:547`, applied to `A._envMap` on every material with no
  per-class exception — contrast the ground material's own `envMapIntensity: 0.15` for the SAME reason,
  §S276b comment "subtle sky reflection," `scene.js:383` — precedent for lowering it per-class exists
  already, just never applied to beam/railing).
- Sky/env source (`scene.js`): Preetham `Sky.js` shader, `turbidity=4` (fairly clear), `rayleigh=2`
  (Rayleigh/blue-sky scattering strength), `mieCoefficient=0.005` (low — little whitish haze, so blue
  dominates most of the dome), `mieDirectionalG=0.8` (strong forward scattering — the warm/white halo
  stays tightly concentrated around the sun disk, not spread across the sky). Default sun at
  elevation 45°/azimuth 180° (`A.updateSky(45,180)`, `scene.js:289`). The code's OWN stated
  approximation of this sky's color exists as a real constant, not eyeballed: the fog-color formula a
  few lines above, `dayT=(elevation+10)/55` → at elevation 45°, `dayT=1` → `fogR=0.65, fogG=0.70,
  fogB=0.73` (comment: "blend from dark (night) to light blue (day)") — B leads R by 0.08 (~12%), a
  mild but deliberate, dev-authored blue bias baked into the same file that drives the env map.

### 2. PBR math, computed from those numbers (glTF/three.js metallic-roughness model)
`F0 = mix(0.04, albedo, metalness)`; Schlick: `F(θ) = F0 + (1-F0)(1-cosθ)^5` where θ is the
view/normal grazing angle — at grazing incidence `(1-cosθ)^5 → 1`, so **`F(θ) → 1` for every channel
regardless of F0**, i.e. any material becomes a near-mirror of the environment at its silhouette edges,
independent of its own albedo hue. This is a geometric property of the Fresnel equation, not a rendering
bug, and it applies to dielectrics too (baseline F0=0.04), just weaker.
- **IfcBeam** (real albedo 0.920/0.900/0.850, metal=0.65): `F0 = 0.014 + 0.65·albedo` = (0.612, 0.599,
  0.567) — near-normal incidence still leans warm (R−B = 0.045, ~8%), so a face viewed near head-on
  should still read warm. Diffuse weight `(1−metal) = 0.35`.
- **IfcRailing** (real albedo 0.741/0.733/0.725, metal=0.55): `F0 = 0.018 + 0.55·albedo` = (0.426,
  0.421, 0.417) — spread of only 0.009 (<1%) even at normal incidence: this material's specular term
  carries almost NO inherent hue of its own at any angle, so whatever the environment reflects shows
  through nearly unfiltered. Railings are also geometrically the class most exposed to grazing/raking
  view angles in a flythrough (thin, mostly seen edge-on) — compounding the effect on the very class
  most likely to be visually prominent as a screen-space edge.
- **Conclusion from the math alone, before any pixel readback:** grazing-angle Fresnel + a blue-leaning
  env map (per §1) predicts a visible, angle-dependent blue shift on beam/railing SILHOUETTE pixels
  specifically, layered on top of correctly-warm diffuse albedo elsewhere on the same surface — and this
  mechanism is completely independent of whether `material_rgba` is NULL or real, because it enters
  through `metal`/`rough`/`envMapIntensity`, none of which are gated on `rgbaStr` the way color is.

### 3. Confirmation only — real headless pixel readback (already run this session, kept as the
### final check per the corrected methodology, not the discovery method)
`/tmp/claude-1000/.../scratchpad/measure_hospital_blue.js` — real production `A._getMaterial`, real
Hospital DB (`~/bim-ootb/buildings/Hospital_extracted.db`, confirmed newer than the stale OCI copy),
real ACES/PMREM pipeline, `renderer.readRenderTargetPixels` (no screenshot). Single-variable ablation on
a beam SIDE face (front face saturated to white from direct light, uninformative):
```
BEAM_side_albedo_only  (no lighting model)              hue= 40°  (warm — matches real IFC cream)
BEAM_side_no_envmap    (real metal .65/rough .26, no envMap) hue= 45°  (still warm)
BEAM_side_production   (same material, envMap ON, 0.6)       hue=249°  (blue)
BEAM_side_pmrem_probe  (white/metal=1, isolates env only)    RGB(70,70,93) hue=240° (the sky's own reflected color)
RAIL_no_metal          (metal→0, envMap still ON)             hue=252°, light=0.68 (bright — diffuse-dominated)
RAIL_production        (metal=.55, envMap ON)                 hue=248°, light=0.51 (darker — more specular-weighted)
```
envMap on/off is the ONLY variable between the 45°-warm and 249°-blue readings on the identical
material — exactly what the Fresnel/grazing-angle math in §2 predicts, and orthogonal to the NULL-rgba
mechanism already fixed in #1356. `RAIL_no_metal` still shows a (weaker, brighter) blue shift even at
metal=0, matching the dielectric-baseline-F0=0.04 term in the same equation.

### Verdict
Confirmed, numbers-first: Hospital's "beams/railings still read blue" is a SEPARATE, more fundamental
mechanism than the HHS MEP NULL-rgba STD_MAT fallback (#1356 already fixed that one). It fires on
classes with 100% real, correctly-populated, warm IFC albedo, because `metal`/`rough` are assigned from
`STD_MAT` unconditionally on `ifcClass` (no `!rgbaStr` gate, unlike the color assignment two lines
above), and grazing-angle Fresnel reflectance genuinely approaches 100% environment-color regardless of
albedo at silhouette/edge pixels — a real optical fact, not a bug in the Fresnel term itself.

### Named fix candidates — NOT implemented (investigation-only scope, per this session's task)
Two independent levers, neither touches the "trust IFC colors" rule for albedo:
1. **Per-class `envMapIntensity` reduction** for `IfcBeam`/`IfcRailing` (and siblings sharing their
   STD_MAT metal>0.3 bracket) — direct precedent already exists in this same file, the ground material's
   `envMapIntensity: 0.15` vs the 0.6 default (`scene.js:383`, "subtle sky reflection"). Attenuates the
   grazing-Fresnel contribution's brightness without touching metal/rough/albedo.
2. **Lower `STD_MAT.IfcBeam`/`IfcRailing`'s `metal` value.** Raises F0's baseline toward the pure-
   dielectric 0.04, shifting more energy weight to the diffuse `(1-metal)` term — does NOT eliminate the
   grazing-Fresnel edge effect itself (that term saturates toward 1 regardless of F0, a geometric fact
   of Schlick's equation), but raises the diffuse-dominated majority of each surface's visible pixels at
   typical flythrough angles (only true silhouette pixels are extreme-grazing).
Branch `fix/hospital-blue-color` (`/tmp/wt-hospital-blue-fix`) is currently a clean checkout with no
code diff — this finding is written up, nothing shipped yet.
