# ⚠ DO NOT REMOVE
## ▶ RESUME 2026-08-14 (session end) — read this block first, it supersedes older status prose below
Reveal (Mechanism C) is BUILT AND MERGED: panel #1349, geometry/timeline #1350, visuals #1352, and
TWO real bug-fixes found from a live Hospital test — #1353 (buildup topout) and #1354 (preview never
replans). **What's left for the next session is narrower now — mainly re-verification with fresh code
and cache, plus one still-unconfirmed claim:**

0. **⚠ Process note, read before trusting `/home/red1/bim-ootb` as canon again:** mid-session, a
   "cursory examination" read directly from that shared checkout gave FALSE results — its `HEAD` was
   behind `origin/main` (missing #1352/#1353 entirely) AND it had ~117 lines of another session's
   uncommitted, unrelated edits sitting in `viewer/cinema_path_editor.js`. Caught only because the
   evidence (grep for `cpeRevealApplyVisual`) came back empty when it shouldn't have. Every fix this
   session was built and witnessed in a clean worktree off a freshly-fetched `origin/main` — verify
   against that (or `git show origin/main:<path>`), never the shared checkout directly, until this
   project gets the same PreToolUse block bim-ootb's OTHER shared paths already have (see CLAUDE.md's
   own standing warning about this checkout).
1. **SW cache-version — user confirmed likely ("yes perhaps i didnt clear right v?").** None of
   #1349/#1350/#1352/#1353/#1354 bumped `viewer/sw.js`'s `CACHE_VERSION` (still `v1026`). Still worth a
   hard-refresh/fresh-SW check before the next bake, though the two real bugs found and fixed below
   (#1353, #1354) independently explain everything observed so far WITHOUT needing to invoke stale
   cache at all — so this is a "do it anyway, cheap insurance" item, not a blocking prerequisite.
2. **`§CPE_BUILDUP_TOPOUT`, FIXED AND MERGED (PR #1353).** Buildup was completing at `plan.beats.rise`
   (orbit start, AFTER the whole round) instead of `plan.beats.out` (the stop stick, the round's own
   start). Witnessed: buildup verified =1 exactly at round-start and through the tail.
3. **Preview never replanned on Reveal toggle, FIXED AND MERGED (PR #1354) — this is what the user's
   pasted preview console log actually diagnosed, end to end.** User: "Preview also do not go 2nd
   round" / "the pov timeline numbering did double but the alt-c still remains not." Root cause: the
   `#cpe-reveal` checkbox handler called `_markPreviewStale()` (bumps a counter) but never
   `_replanFilm()` (the only thing that rebuilds `_state.plan`) — so Preview kept flying the
   pre-toggle plan (reveal effectively inert) until an unrelated band-drag happened to trigger a
   replan. This is confirmed directly in the user's own pasted log: `§CPE_BUILDUP_TOPOUT topoutU=0.848
   src=plan.beats.rise` appears UNCHANGED immediately after `§CPE_REVEAL ON` fires — #1353's fix exists
   and is correct, it just never got a plan with `reveal:true` to act on. **This also resolves the
   session's earlier open worry about whether #1353 alone would explain the "282 stops way before full
   round" bake report** — #1353 was never actually being exercised in preview at all until #1354 shipped,
   so there was nothing more to find there once #1354 is verified live.
4. **"Last stick rule" claim — still not independently confirmed, but no longer the leading suspect.**
   User: "code did not obey the last stick rule." Given #1353+#1354 together explain every OTHER
   symptom reported (incomplete buildup during the round, preview not showing it, duration numbers not
   matching the visible camera path), this is most likely the SAME complaint restated in different
   words, not a third, separate defect. Re-check only if it's still reported after a fresh bake with
   #1353+#1354 live and cache cleared.
5. **Next verification step, as the user already planned:** bake ONE fresh Hospital run (Alt+C, not
   just Preview) with a cleared cache, and read the REAL `§CPE_BUILDUP_TOPOUT` / `§CPE_DAY_COUNTER` /
   `§CINEMA_BEATS` / `§CPE_REVEAL_ROUND` log lines from THAT run — the pasted log this session was a
   PREVIEW run (`§CPE_PREVIEW click...`), not a bake, and predates #1354, so it's already fully
   explained above, not fresh evidence to re-diagnose.

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
