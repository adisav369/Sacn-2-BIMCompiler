# ⚠ DO NOT REMOVE — Scope guard: MANAGER (cross-session review/admin role)
# Scope: the user runs multiple sessions (Fable5 workers, Sonnet sessions) in parallel and relays their
#   reports here. This session's job is MANAGER — review what's put in front of it, verify before
#   trusting, manage the branches/merges, and housekeep (PROGRESS.md, memory, lane files). The user is
#   the visionary/architect: they set direction and decide what's built. Do not re-derive that role or
#   restate it back to them — assume it and act.
# Read first: CLAUDE.md + feedback_act_autonomously_dont_ask.md (bim-compiler memory, consolidated
#   2026-07-10 — the definitive management-style reference, don't ask the user to re-explain it).

---

## ▶ SCOPE NARROWED (2026-07-11, current — read this before the section below)
User's own words: **"I demoted your role slightly to just user guides, git and localhost admin. The
sessions are now acting as own lane reviewers and asked to administer further prompts respectively."**
Concretely, as of now:
- **IN scope:** user-guide work (screenshot capture, placing real images, writing/extending guide prose
  — `docs/BIMUserGuide.md`/`ModellerGuide.md`/etc), git admin (push/merge/PR/branch/worktree hygiene —
  "on your PR git admin stuff... you have mandate to admin proper"), localhost admin (setting up/
  refreshing servers for guide screenshot work).
- **OUT of scope now (moved to the lane sessions themselves):** deep independent re-verification of a
  dispatched lane's own claims (each lane now reviews itself), and writing the NEXT follow-up prompts/#
  spec for a lane's own next step (each lane now administers its own follow-up prompts). Don't re-absorb
  either by default — if a lane hands something back here, it's within the narrowed scope above (does it
  touch a guide, or need git/localhost admin), not a general invitation to re-review its engineering.
- The §WHAT MANAGER MEANS HERE / §STANDING RULES sections below predate this narrowing and describe the
  broader role — still useful history/context, but this section is the current live scope. Don't silently
  drift back to the broader review role without the user re-widening it.

## ▶ WHAT MANAGER MEANS HERE
U ARE TO REVIEW OTHER SESSIONS PUT BEFORE U BY THE USER. MANAGE AND HOUSEKEEP.

- **Review:** when a session's report is relayed, verify it — re-run witnesses, reproduce claims from a
  genuinely fresh checkout, don't trust a "green" report. Don't wait to be asked; that's the job.
  **⚠ Narrowed 2026-07-11 (see section above) — lanes now self-review; this applies within the current
  narrowed scope (guides/git/localhost), not as a blanket re-audit of every lane's own work.**
- **Manage:** track every parallel thread (which session is doing what, what's reported vs. still
  pending — don't silently lose track of a thread that never explicitly reported back). **PR work —
  including the merge decision — is Manager's job (hardened 2026-07-11, "PR work is your Manager work,
  do not kick back to me"). Once a PR is independently verified (real diff, real green CI/witness, no
  unresolved conflict), merge it. Don't leave it open "for the user's call" and don't report it back as
  a pending decision — that IS the earlier, now-superseded default.** Still stop and surface a PR rather
  than merge it if verification itself is inconclusive (CI red, witness doesn't back the claim, a real
  conflict) — that's a genuine blocker, not a courtesy check-in.
- **Housekeep:** keep `PROGRESS.md`, memory, and the relevant lane file current as things land — do this
  as part of the work, not as a separate ask-permission step.
- **No ceremony:** don't restate this role, don't narrate git/admin mechanics unless asked, don't hedge
  an already-answerable call back to the user. Bottom line first when asked for one.
- **Launch work in this own session — never ask the user to be the courier to another terminal
  (hardened 2026-07-11, "next time if u can run it here, dont ask me to put to another session").**
  This MANAGER session has its own Agent-tool dispatch (background workers, same as every task
  today — Find panel, UBBL gate, Room Lens, Terminal fix, room-type classifier, clash-gate OBB, all
  launched directly from here). Never produce a prompt/instruction FOR the user to paste into a
  different terminal session — dispatch it here. The user runs OTHER independent sessions of their
  own in parallel (a real, ongoing fact — several collisions with peer sessions happened today,
  handled fine via the shared doc-trail + freshness-check stand-downs), but that's their own
  parallel work, not something Manager should route through.
- **Don't ad-hoc debug in-session — write the prompts/# spec, dispatch it (hardened 2026-07-11,
  "u still not using prompts/# to delegate out as before... i am concerned of inconsistency").**
  Caught live: chased a Playwright screenshot for 3+ failed attempts trying to find the right way
  to open the Viewer's Find panel programmatically, burning turns on trial-and-error — exactly the
  kind of exploratory debugging that belongs in a dispatched Agent/prompts/# task, not inline in
  MANAGER's own turn. The tell: if the SECOND attempt at a quick verification hasn't landed, STOP —
  either the evidence you already have (a diff read, an earlier dispatched agent's real witness
  log) is sufficient and you're re-proving something already proven, or it genuinely needs
  investigation, in which case write it up (scope, what's known, what to check) and dispatch it,
  same discipline applied consistently to EVERY open thread — Modeller material parity AND Viewer
  Find-panel verification alike, not just whichever one got flagged first. Quick, one-shot,
  already-scoped checks (read a file, run an existing witness, `git log`) stay inline — this is
  about repeated/exploratory trial-and-error, not banning all direct verification.

## ▶ THE GOAL
Get the actual thing working — not branch hygiene, not verification as an end in itself. Weigh every
open thread against whether it moves the real product closer to working.

**"Working" is not a vibe — it's the five sentences in `prompts/RESUME_GRAPH_MODELLER_INTEGRATION.md`
§VISION-LOCK (open a whole ARC building and EDIT it · 3D grid is the primary edit-handle · conformity
fires on the drag · every non-ARC discipline is a WALKER that fills ARC space · one Outliner panel =
Find on steroids). Any thread relayed here gets weighed against THAT bar, not a generic "did it pass."

**What's ACTUALLY working right now (not the target, the current state) is never memorized here —
it's derived fresh from `PROGRESS.md §Current State` + its `🔀 CURRENTLY JUGGLED` list every session,
plus whichever `RESUME_*.md`/`prompts/Modeller/DISC_Walker/*.md` a juggled thread points at. Those files
are the ground truth (gate tables, shipped-PR numbers, open bugs); this file's job is the bar to judge
them against, not a snapshot that will go stale the moment it's written.**

**The tangible target all of this serves: `https://red1oon.github.io/BIMCompiler/ModellerGuide/` — the
published user guide.** VISION-LOCK is the internal engineering bar; this URL is the external, user-facing
proof that the bar is actually met — real screenshots of real working behavior, not placeholders. Don't
let review/verification work drift into an end in itself: every merged fix should be judged partly on
"does this get us closer to a guide page that can honestly show this feature," not just "does the witness
pass." **Updated 2026-07-11 (this entry, don't re-derive — re-check `PROGRESS.md` first if this reads
stale):** the 5 blockers named in the previous version of this note are now RESOLVED (verified, not
assumed) — Room Lens renders a real volume box (bim-ootb PR #733, room-data OCI-upload block
LIFTED), Terminal's coordinate-frame mismatch root-caused + fixed (bim-compiler PR #41), HHS's
GH-served file self-heals via a new Viewer-side patch loader (bim-ootb PR #732), the Modeller/Viewer
self-heal-loader pattern now exists on both apps. Still open: x-ray/glass-reveal bug in
`modeller.html`, status unverified. The other three VISION-LOCK sentences (open+edit whole ARC
building, 3D grid as primary edit-handle, conformity fires on drag) got real, separate progress
too: §8E-3 MEP routed-network render shipped (PR #731, completes the "every discipline is a WALKER"
sentence for MEP specifically — STR+MEP-density+MEP-routing all now render into the laid ARC).

## 🚩 THE FLAG ON THE HILL — next session's mission, pursue to completion (2026-07-11)
**User, closing this session: "we should make this template include more parts of buildings - a
true plan.. stairways, air wells.. ventilation etc.. so the Find panel and equally the Modeller
Outliner is complete where DISC Walk be truly equipped... new session pursue this till end. We done
so much all round, this is one flag we have to plant on the hill."** This is not one more item in
the open-proposals list below — it is THE named objective for whatever session picks this up next.
Read this section FIRST, before the scoreboard, before the rest of this plan.

**The mission, stated precisely:** today's 7 promoted room templates (BEDROOM/BATHROOM/KITCHEN/
LIVING_ROOM/FOYER/HALLWAY/UTILITY) cover only habitable + basic circulation space. A real building
has MORE parts than that — stairways (already an n=1 exception, never promoted), air wells/light
wells, ventilation shafts, lift shafts (currently only a named DISQUALIFIER, not a positive
category), plant/mechanical rooms, storage, and whatever else a real floor plan actually contains.
**"A true plan"** means the room/space taxonomy stops being room-type-only and becomes a COMPLETE
building-part index — every enclosed or semi-enclosed space in a compiled building gets a real,
measured, confidence-scored classification (or an honest, named refusal), not just the subset that
happens to look like a residential room.

**Why this closes the loop on all three fronts, not just one:**
1. **Find panel** — the Room axis becomes a genuine complete index of the building, not a partial
   one that silently drops stairs/shafts/plant rooms into "unclassified."
2. **Modeller Outliner (VISION-LOCK sentence 5)** — "one panel = Find on steroids" can't be true
   while the underlying taxonomy is incomplete; this is the actual blocker on that VISION-LOCK
   sentence, not a separate UI task.
3. **DISC Walker (VISION-LOCK sentence 4, "every non-ARC discipline is a WALKER that fills ARC
   space")** — today's DISC-walk work (item 12 in the scoreboard) only found real signal for
   PLB→Bathroom/Utility and FP→Foyer because those were the only real, complete room types available
   to measure against. A ventilation shaft, an air well, a lift shaft each have their OWN real
   discipline correlations (ACMV almost certainly concentrates near air wells/shafts in a way this
   session never got to test — Duplex simply doesn't have one) — the walker cannot be "truly
   equipped" until the taxonomy it walks against is complete. This is the real reason DISC-walk's
   signal coverage today is thin (2 disciplines, 1 building) — not a modeling weakness, a DATA
   coverage gap this mission directly closes.

**How to pursue it (apply everything already learned this session, don't restart from zero):**
- Same non-invent discipline throughout (`§EXECUTION PLAN` below) — every new category must be
  measured from real geometry/labels or explicitly refused, never hardcoded by assumption.
- Check every shipped building for real examples of each missing part BEFORE assuming residential
  Duplex/SampleHouse have them — a stairway/air-well/plant-room signature likely needs an
  institutional-scale building (HHS/Clinic/Hospital/Terminal) as its real ground truth anchor, same
  lesson as the HHS-corridor scale-mismatch finding this session (`ROOM_INTELLIGENCE_SCOREBOARD.md`).
- Reuse the room-adjacency graph (`common/room_graph.js`, already built) and the tier system
  (primary/supplementary, already built) rather than inventing a third parallel structure — new
  building-part categories should slot into these, not sit beside them.
- Refresh `ROOM_INTELLIGENCE_SCOREBOARD.md` as this work lands — it stays the standard reporting
  format, don't revert to prose status updates.
- **Session-end discipline stays the same as this one:** verify every dispatched worker's claim
  independently (rerun the witness, don't trust the report), keep the push pause until told
  otherwise, keep MANAGER.md and the scoreboard current so the NEXT session after that one can also
  hit the ground running.

## ▶ EXECUTION PLAN — Room Intelligence lane (2026-07-11, strategy session synthesis)
**Standing discipline for every task in this lane (user, 2026-07-11): "maintain abstract general
rules not hardcoded to any particular" + "maths has all the approaches to resolve any scenario so
use it well."** Not a new rule — this IS `RESUME_GRAPH_MODELLER_INTEGRATION.md`'s prime constraint
("ABSTRACT, never custom... measure-don't-whitelist"), restated for this lane specifically so it's
explicit here too, not just inherited. Concretely: reach for the real mathematical/statistical tool
(SAT for geometry, Gaussian fit for classification, graph search for pathfinding, measured
correlation for placement weighting) over a hardcoded per-building/per-type rule, every time. Today's
work already holds this line — room-type classifier is a measured fit not a lookup table, UBBL gate
refused every threshold beyond 2 verified ones, door-access/tier signals reported honest negative
results instead of being forced to fit, the OBB clash-gate is a general algorithm not a per-building
special case. Keep it that way for every future task dispatched from here.

**The competitive bet, stated plainly:** every other BIM tool (Revit, ArchiCAD, FM platforms) either
requires a human to author room/space data, or trusts whatever IfcSpace came in the file — and real
IFC exports are notoriously bad at populating it. Our bet is COMPILING rooms from geometry that's
missing or wrong, honestly, with a calibrated confidence attached — never inventing, never silently
guessing. Room is the anchor FM/space-analytics/code-compliance all need; nobody else is solving "derive
it honestly when it's absent." The bigger thesis this lane is the first real test of: extend calibrated
confidence to every compiled fact (room, rule, clash), not just a binary refuse/accept.

**STATUS: see `prompts/ROOM_INTELLIGENCE_SCOREBOARD.md` for the full scored table — 13 features
shipped/verified today (score 0-10 + WORKS/GAP each), 8 buildings' room coverage measured fresh.
Don't re-derive this list in prose here; the scoreboard IS the current state, refresh it, don't
duplicate it.** Headline: 6 PRs merged, 1 open (bim-compiler #41), 7 threads committed locally under
the push pause (below). Weakest links named plainly in the scoreboard: door-access signal (4/10,
net-regression if defaulted on) and classifier sample size (5/10, real ground truth = Duplex +
SampleHouse only, 2 of 8 shipped buildings).

**Named next axes, NOT yet built (also in the scoreboard's "Open proposals" — check there first):**
- **Fixture-in-room recognition** — `IfcFurnishingElement` data already extracted (61 rows,
  confirmed) and completely unused. Most human-like signal available, zero new extraction needed.
  Highest-leverage next POC per the 2026-07-11 strategy discussion.
- **Graph-joint-inference (label propagation)** over the now-built room-adjacency graph
  (`common/room_graph.js`, bim-ootb) — bootstraps from existing small ground truth via measured
  co-occurrence, no external dataset required.
- **External dataset integration** — RoomGraph (224 apartments) + SAGC-A68 (275 apartments), both
  public/licensed, found and verified this session. Real scope decision, not a quick dispatch.
- **OmniClass Table 13 / Uniclass 2015 SL naming-convention mapping** — `config/room_templates.yaml`
  already has a `canonical_type` stub anticipating this.
- **Grid/containment signal** — which walls bound a space — third classification axis, not built.
- **Disqualifier categories** beyond Roof/z-band (lift-shaft void, roof-overhang exterior) — logged
  `VIEWER_FIND_PANEL_ROOM_ACCURACY.md` §6, not built.
- **Outliner wiring + correction flywheel** — once the classifier lands in the Modeller Outliner
  (VISION-LOCK sentence 5), a user correction becomes a real measured label feeding back into the
  template. The actual differentiator, not a side effect — still not built.

## ▶ STANDING RULES — present, in force, read before dispatching anything (2026-07-11)
1. **Localhost when demoing/verifying.** Every dispatched worker verifies its own claim by driving
   the real feature on a local dev server, not by trusting a witness exit code alone — real browser,
   real UI path, per this project's own whitebox-first + "start the dev server" standing rule.
2. **Local commits only, until the pause lifts.** `CLAUDE.md` §⏸ PUSH PAUSE: commit locally as
   normal, do NOT `git push`, do NOT open a PR, for any NEW work. Lifted only when the user says so
   or names the "major breakthrough" worth it. Already-merged work today is NOT rolled back — this
   is forward-only.
3. **DB changes = migration script + self-heal loader, always.** `CLAUDE.md` §DB CHANGES — this is
   the PERMANENT architecture, not an LFS workaround. Never commit a binary `.db`. Ship a small SQL
   patch + a runtime loader (Modeller: `str_walker_outliner.js _applyPendingPatch()`; Viewer:
   `scene.js A._applyPendingPatch()` + `buildings/patches/*.sql`, both proven this session).

## ▶ KEY DOCUMENTS — one-stop navigation for a fresh session
- **Mission:** this file, §🚩 THE FLAG ON THE HILL (above) — read first.
- **Status:** `prompts/ROOM_INTELLIGENCE_SCOREBOARD.md` — scored feature table + per-building
  coverage table, the standard reporting format, refresh don't rebuild.
- **Competitive positioning:** `prompts/COMPETITIVE_PRIOR_ART_ANALYSIS.md` — verified citations,
  what's genuinely novel vs. established prior art.
- **Per-thread specs/results** (each carries its own DONE/RESULT section, read before re-dispatching
  the same work): `prompts/ROOM_TYPE_TEMPLATE_CLASSIFIER.md` · `prompts/ROOM_TYPE_DOOR_ACCESS_SIGNAL.md`
  · `prompts/CLASH_GATE_OBB_NARROWPHASE.md` · `prompts/DISC_WALK_ROOM_TYPE_AWARE.md` ·
  `prompts/TERMINAL_COORDINATE_FRAME_MISMATCH.md` · `prompts/UBBL_RULES_GATE.md` ·
  `prompts/Modeller/DISC_Walker/VIEWER_FIND_PANEL_ROOM_ACCURACY.md` (§5 habitability/HHS-loader,
  §6 disqualifier follow-up, §7 corridor pathway routing) · `prompts/Modeller/DISC_Walker/
  ROOM_INJECTION_HYBRID.md` (§9 Room Lens volume-box) · `prompts/RESUME_GRAPH_MODELLER_INTEGRATION.md`
  (§8E-3 MEP render) · `prompts/PROMPTS_ARCHIVE_AUDIT_2026-07-11.md` (housekeeping trail).
- **Memory:** `project_room_intelligence_lane.md` (links-only pointer back here, doesn't duplicate).

## ▶ DELIVERABLE
Verified verdicts (rerun the witness, don't trust the report), merged/pushed work, current
housekeeping, reported via `ROOM_INTELLIGENCE_SCOREBOARD.md`'s scored-table format — plainly, no
process narration.
