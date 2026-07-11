# ⚠ DO NOT REMOVE — Scope guard: MANAGER (cross-session review/admin role)
# Scope: the user runs multiple sessions (Fable5 workers, Sonnet sessions) in parallel and relays their
#   reports here. This session's job is MANAGER — review what's put in front of it, verify before
#   trusting, manage the branches/merges, and housekeep (PROGRESS.md, memory, lane files). The user is
#   the visionary/architect: they set direction and decide what's built. Do not re-derive that role or
#   restate it back to them — assume it and act.
# Read first: CLAUDE.md + feedback_act_autonomously_dont_ask.md (bim-compiler memory, consolidated
#   2026-07-10 — the definitive management-style reference, don't ask the user to re-explain it).

---

## ▶ WHAT MANAGER MEANS HERE
U ARE TO REVIEW OTHER SESSIONS PUT BEFORE U BY THE USER. MANAGE AND HOUSEKEEP.

- **Review:** when a session's report is relayed, verify it — re-run witnesses, reproduce claims from a
  genuinely fresh checkout, don't trust a "green" report. Don't wait to be asked; that's the job.
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

**Shipped this session (verified, not just claimed):**
1. Room habitability filter (`common/room_habitability.js`, shared Modeller+Viewer) — PR #732.
2. Room Lens real volume-box render (`room_guid` grouping, multi-rect union) — PR #733.
3. Terminal coordinate-frame fix (two stacked bugs, root-caused to real code, not guessed) — PR #41.
4. UBBL room-size demo gate (2 verified By-Law 42 thresholds only, honestly labeled) — PR #729.
5. Find-panel visibility bug (two-part root cause) — PR #728.

**In progress:** Room TYPE template classifier (`ROOM_TYPE_TEMPLATE_CLASSIFIER.md`) — the concrete
next piece of the confidence-everywhere thesis. Key finding mid-flight: `Duplex_extracted.db`'s
`object_type` column already carries REAL human-authored room-type labels (`Living Room`, `Kitchen`,
`Bedroom 1/2`, `Bathroom 1/2`, etc., straight from the source IFC's `LongName`) — a genuine
RosettaStone-quality reference, not an external guess. Fit templates from this real N=21 sample,
require ≥2 real occurrences before trusting a signature (Duplex's own mirror A/B-unit structure
already gives natural repeat-confirmation for every type), apply elsewhere as low-confidence
inference only. Config-editable (`config/room_templates.yaml`, same convention-default-with-citation
shape as `config/profiles/malaysian_residential.yaml`) — same "default by convention, human can
edit" pattern already accepted for other config surfaces.

**Named next axes, NOT yet built (logged, don't lose track):**
- **Grid/containment signal** — which walls bound a space, grid-cell alignment — a third
  classification axis beyond size/aspect-ratio. Ties into existing SEMI-GRID/emergent-datum work.
- **Door-access signal** — door count + adjacency (hallway ≥2 doors, bedroom exactly 1) as a
  discriminator; door-rescue/door-partition (`compile_rooms.py`) already computes adjacency, cheap
  to add.
- **Disqualifier categories** beyond the existing Roof/z-band check — space below a lift (shaft/pit
  void), the exterior void under a hanging roof overhang. Needs real geometric/label signals (lift
  adjacency, envelope-boundary test), not a hardcoded name match. Logged:
  `VIEWER_FIND_PANEL_ROOM_ACCURACY.md` §6.
- **Outliner wiring + correction flywheel** — once the classifier lands in the Modeller Outliner
  (VISION-LOCK sentence 5), a user correcting a room's type becomes a REAL measured label, signed
  as a `kernel_ops` op, feeding back to refine the template over time. This is what turns a
  small-sample fit into something genuinely calibrated — the actual differentiator, not a side effect.

## ▶ STANDING MODE — Push Pause (2026-07-11, until lifted)
See `CLAUDE.md` §⏸ PUSH PAUSE. New dispatched work commits locally, verifies on localhost, does NOT
push/open a PR, until the user names a "major breakthrough" or lifts the pause explicitly. Already-
merged work today is not being rolled back — forward-only pause.

## ▶ DELIVERABLE
Verified verdicts, merged/pushed work, current housekeeping — reported plainly, no process narration.
