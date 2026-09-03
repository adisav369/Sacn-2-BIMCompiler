# ✅ VERIFIED DONE 2026-07-05 — bim-ootb PR #661 MERGED (watchdog-checked, not trust-on-recap)
Checked the actual diff, not just the PR description. §0 pre-flight substantively done (real guide-text
rewrite in `modeller.html` distinguishing Teams from HBA — quoted, not paraphrased-and-trusted). Real bug
found+fixed: `window.__teamsPeerBeats` was read every render but never written — presence heartbeats were
sent but nothing subscribed to receive one; fixed via a proper `_conn.bus.on()` wire-up
(`modeller/teams_embed.js`). Honestly scoped: discovered fork/post-it/bundle/gate/merge has ZERO clickable
UI anywhere (every "S1-S12 DONE" witness calls engine functions directly in Node, never through rendered
DOM) — correctly narrowed the E2E to the one piece with real production UI (the presence embed) instead of
pretending to test something that doesn't exist. Documented a real platform constraint (BroadcastChannel
can't cross separate BrowserContexts) and named, not silently patched, a second gap (late-joining peers stay
invisible until they re-announce). Witness `teams/tests/witness_e2e_dual_presence_modeller.js` 11/11 +
`teams/tests/run_all.js` 26/26, all green. Exemplary — this is the bar future watchdog passes should hold to.

## Below this line is the original spec, kept for history.

# TEST TASK (not a build spec) — Teams overlay real dual-session live E2E

```
# ⚠ DO NOT REMOVE
SCOPE: TEST ONLY. Distributed Design Branches (Teams overlay) is reported SHIPPED (suite 18/18, OCI demo live
per project_teams_distributed_branches memory) but has NEVER been driven through a real dual-user path. This
project has been burned exactly once by trusting green seam-witnesses over a real user path (the Walk tool was
silently broken for a whole session while witnesses stayed green — see feedback_test_real_user_path_not_seams).
Do not repeat that here. Read the log after every run.
```

## WHY THIS IS THE TOP-PRIORITY ITEM RIGHT NOW
Flagged 2 sessions ago as "cheapest, highest-risk-of-false-done" of the four honest gaps vs. incumbent
modellers — cheapest because nothing needs building, only proving; highest-risk because a shipped-but-untested
claim is exactly the shape of bug this project has hidden before.

## §0 — MANDATORY PRE-FLIGHT: resolve the Teams/HBA guide overlap BEFORE touching the E2E test
User-confirmed 2026-07-05: the in-app User Guide (`#b-guide` panel) currently has a genuinely confusing overlap
area between Teams and HBA — do not treat this as a false alarm, it's real. Both features overlay the SAME
host surface (HHS) and the existing separation doctrine
(`feedback_hba_teams_share_hhs_no_collision` memory) says they coexist via DIFFERENT seams: Teams = self-mounted
`teams_pill` + DOM dots (NEVER touches `viewer/panels.js`); HBA = emissive `MeshPort` + data-gated pills INSIDE
`panels.js`. Before writing or running any E2E test:
1. Read `feedback_hba_teams_share_hhs_no_collision` (memory) + `teams/ROADMAP.md` + whatever the current guide
   text actually says about Teams and HBA, side by side.
2. Confirm the ACTUAL current code still respects that seam split (Teams still never touches `panels.js`, HBA's
   pills are still the only thing inside it) — don't assume the doctrine is still true just because it was true
   when written; a later session could have drifted it without meaning to.
3. Rewrite/clarify whatever part of the guide text is causing the confusion so a reader can tell the two apart
   without needing to read source code — this is a real, named documentation defect, not busywork.
4. Only proceed to the dual-session E2E test below once the guide is fixed and the seam separation is
   reconfirmed in code, not just in doctrine.

## WHAT TO ACTUALLY DO (after §0)
1. Read `teams/ROADMAP.md` + whatever the S1-S9 phase docs are to know what "a real dual-user path" even means
   for this feature (branches, dots, post-its, bundles, the-gate, sync, pill) — don't assume, confirm the
   intended real workflow from the spec before scripting a test around a guessed one.
2. Drive TWO real, separate browser sessions (two real Playwright contexts, not one context simulating two
   users) through: both open the same building, one forks a design branch, makes a real edit, posts a post-it,
   the other sees the post-it/dot live (or on next sync — confirm which is the real design), the first bundles
   the change, it goes through "the-gate," gets merged — assert the SECOND session's state actually reflects
   the merged change afterward, not just that the first session's local state looks right.
3. Assert with §-tagged log lines at every real state transition (branch created, edit committed+signed, sync
   delivered, gate passed/blocked, merge landed, second session sees the result) — per this project's own
   Watchdog Protocol, "verified live" without a § line is not evidence.
4. If it breaks: name exactly what broke (which step, what was expected vs. observed) — do not silently patch
   and re-run until green without recording what was actually wrong, that's how the Walk-tool bug hid before.

## DONE WHEN
0. §0's guide overlap is resolved: the User Guide text clearly distinguishes Teams vs. HBA, and the seam
   separation (Teams never touches `panels.js`, HBA owns the pills inside it) is reconfirmed true in the
   CURRENT code, not just cited from doctrine.
1. A genuine 2-real-session E2E witness exists and is green, OR a real, named bug is found and fixed with the
   fix's own witness.
2. Every state-transition claim above has a § log line, not just a final "pass/fail".
3. Verdict is explicit: "Teams overlay is proven for a real dual-user path" or "Teams overlay LOOKS shipped but
   X doesn't actually work for a second real user" — no soft middle answer.

## WATCHDOG NOTE
Tracked from `prompts/FRONTEND_LANE_MASTER.md §NEW BACKLOG`. Closing session's `# DONE` appendix needs a §
log line per claim above — no log line, not done.
