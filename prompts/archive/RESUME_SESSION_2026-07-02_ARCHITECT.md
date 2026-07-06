# ⚠ DO NOT REMOVE — Session resume: architect/watchdog role, 2026-07-02

**Read this first if picking up cold.** This session ran the 4th-terminal watchdog role (per
`bim-ootb/prompts/RESUME_WATCHDOG_THREE_SESSIONS.md`'s original setup) and evolved into the architect/mastermind
dialogue role across several concurrent threads. It does NOT itself contain the detailed state of any thread —
it's the connective reasoning trail + pointers to the docs that do. Read those docs, not just this one, before
acting on any thread.

## What this session actually did, in order (context for WHY the pointers below say what they say)

1. **Watchdog pass #1** — independently verified 3 concurrent sessions' claims (§F2 guide polish, Terminal/
   LOD400, HR/Teams overlay). Found #1 and #3 genuinely done (verified against live gh-pages + re-run witnesses
   myself, not trusted secondhand), #2 unstarted/handwaved. Full detail: git history of
   `bim-ootb/prompts/RESUME_WATCHDOG_THREE_SESSIONS.md` (now closed, superseded by watchdog pass #2 below).
2. **Live geometry bug diagnosis** — a Modeller screenshot showed giant diagonal walls + floating furniture on
   Duplex. Root-caused via direct DB query (not screenshot-eyeballing): furniture `center_z`/`bbox_z` genuinely
   wrong in the extracted DB; separately, a radians-vs-degrees rotation bug affected every building. Both fixed
   and merged since (see watchdog pass #2, thread A).
3. **HR_BIM_Asset payroll-gap analysis** — user asked what's outstanding on "payroll proper." Read the full
   897-line spec, found the payslip UI was never built despite the engine being done, wrote P7/P8/P9 backlog
   items into `bim-ootb/prompts/RESUME_HR_BIM_ASSET.md`. A concurrent session then found something bigger: HBA
   had reinvented schema that iDempiere's real AD dictionary already has, dormant (§CRITICAL "Compile not
   Model" correction, 2026-07-02) — P7-PRE now supersedes P7/P8/P9 ordering. That session has since made real
   progress (`/tmp/wt-hr`: `eb69978` P7 payslip UI, `fb29e40` M_Locator/Strata correction) — **UNVERIFIED by me,
   flagged in watchdog pass #2 thread F as the highest-risk claim to check.**
4. **`bim-compiler/prompts/` reinstituted** — user directive: stop treating this directory as migrating away to
   bim-ootb; it's gitignored local working files, treat it as live. Restored 5 accidentally-deleted files, then
   corrected to archive 3 of them (they were old) rather than leave them in root — see
   [[feedback_admin_housekeeping_no_ask]] for the process lesson (routine file hygiene shouldn't be surfaced as
   a question). Also copied 6 then-current bim-ootb spec docs into bim-compiler as local working copies (bim-ootb
   originals, git-tracked there, untouched) — see [[feedback_prompts_migrating_check_other_repos]] for the
   corrected framing.
5. **IFC→BOM geomapping library designed** — user's own long-standing idea ("determine from IFC alone, build a
   library like Storey/Rooms/TimeMachine already do"), scoped via dialogue into 3 tiers (relationship-walk →
   curated-table → geometry-last-resort), grounded in a real Explore investigation of how Storey (works),
   4D/5D (works, the pattern to generalize), and Room (24% recall, NOT 90% as originally believed — corrected a
   wrong premise) actually function today. Full spec: `bim-compiler/prompts/RESUME_IFC_BOM_GEOMAPPING.md`.
6. **Model-allocation lesson, twice** — first from the user directly (mastermind/insight-generation stays in
   Sonnet dialogue, Fable5 is for execution efficiency, not the source of hard thinking —
   [[feedback_model_allocation_mastermind_vs_execution]]); then refined via an external (Gemini) review that
   caught my own inconsistency (I'd routed POC/validation work — actually a subtle-assumption-catching task —
   to Fable5 under a banner that should have kept it in Sonnet). Fable5 then ran the POC work anyway and
   produced genuinely rigorous findings (F1–F6 in the geomapping spec), which prompted a SECOND refinement:
   the real distinguishing axis isn't a fixed tier split, it's "does this task require catching subtle wrong
   assumptions" (worth the stronger model) vs. "is this mechanical execution against an already-validated spec"
   (isn't). Current state: Fable5 continues through Tiers 1+2 AND Tier 3 of the geomapping library — see that
   spec's §Status "round 2" note.
7. **Coordination catch** — a separate Modeller-session conversation about a "Geometry EYES" module (helping
   Walkers "see" space) turned out to be the SAME idea as item 5, proposed independently. Caught before any
   duplicate worktree existed; the geomapping spec now has a coordination note pointing future sessions at it.

## Threads to check on next (don't re-derive — these docs have the real detail)

| Thread | Where | What's actually unverified |
|---|---|---|
| A–G (rotation fix, outliner stall, signing-speed, real-geo render, walk-all-disciplines, **HR native re-point**, geomapping coordination) | `bim-ootb/prompts/RESUME_WATCHDOG_2026-07-02.md` (pushed `d051a8b`) | Everything in that doc's table — none of it has been independently verified yet this pass, only claimed via commit messages. Thread F (HR) is flagged highest-risk. |
| IFC→BOM geomapping / Geometry EYES | `bim-compiler/prompts/RESUME_IFC_BOM_GEOMAPPING.md` + `project_ifc_bom_geomapping.md` (memory) | Fable5 was mid-flow on Tier 2 continuation as of this session's end — check what it produced since the F1–F6 POC findings, whether it shipped real Tier 1/2 code + witnesses, and whether Tier 3's extraction-side prerequisite work has started. |
| HR_BIM_Asset P7-PRE + P7 + Strata/M_Locator | `bim-ootb/prompts/RESUME_HR_BIM_ASSET.md` §CRITICAL block + watchdog thread F | Per watchdog pass #2: confirm the payroll engine actually reads/writes real `hr_*` AD tables now (not a renamed still-separate seed), confirm the payslip pane renders off those real rows, re-run the GL-balance witness against the native path. |

## Standing lessons this session produced (apply going forward, don't re-litigate)
- [[feedback_admin_housekeeping_no_ask]] — routine file hygiene: just do it, don't ask.
- [[feedback_model_allocation_mastermind_vs_execution]] — refined further by the Gemini-review episode above;
  the sharper rule is assumption-testing-worth-the-model vs. mechanical-execution-doesn't, not a fixed tier split.
- [[feedback_prompts_migrating_check_other_repos]] — bim-compiler/prompts is live/local again, not migrating away.
- [[feedback_test_real_user_path_not_seams]], [[feedback_architect_first_before_tasking]] — pre-existing, both
  still load-bearing across every thread above.

Relates: this doc is deliberately NOT a duplicate of `RESUME_WATCHDOG_2026-07-02.md` or
`RESUME_IFC_BOM_GEOMAPPING.md` — it's the connective session narrative. Read it once, then go to whichever
thread's own doc for the real state.
