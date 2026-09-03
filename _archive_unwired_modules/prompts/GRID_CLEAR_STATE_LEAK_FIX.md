# ⚠ DO NOT REMOVE — Scope guard
# Findings-only doc from the walking-tests session (2026-07-04, bim-ootb `lane/modeller-ifc-open`,
# PR #642 already open+pushed with this bug as a KNOWN RED witness — see `witness_e2e_gridstretch_multi.js`
# §KNOWN RED comment). Root cause is fully traced (step-tagged diagnostic, reproduced 2×, ruled out timing).
# NOT implemented — the fix needs the ONE decision in §DECISION first. Read the §-log after every run.

## THE BUG (one sentence)
`#b-clear` resets the THREE scene + the signed op-log, but not `str_walker_outliner.js`'s own module-local
STR-walker state (`ready`, the loaded column/girder skeleton) — so after clearing a building and authoring
something unrelated, a grid-drag can still fire a stale re-walk against the CLEARED building's skeleton,
whose commits collide on `kernel_ops.id` and roll back.

## REPRO (exact steps, confirmed reproducible 2×)
1. Open SampleCastle (`.db`-open) — real STR skeleton loads: 23 columns, 13 girders, `ready = true` inside
   `str_walker_outliner.js`'s closure.
2. Click `#b-clear` — scene + op-log go to 0. `ready` and the skeleton are UNTOUCHED (no reset hook exists).
3. Author an unrelated synthetic grid (`Bonsai.grid.define(...)`) + a wall (`GEOM_EXTRUDE_POLY`), completely
   independent of SampleCastle.
4. Enter Move-Grid (`#b-gridmove`), drag a gridline. `bonsai_gridmove.js`'s wrapped `commit` (installed by
   `str_walker_outliner.js`'s `wrapGridMove()`) checks `if (ready && window.swbOnGridMove)` — TRUE, because
   `ready` is still SampleCastle's from step 1 — and fires a real STR re-walk against SampleCastle's
   still-resident (in-memory, off-canvas) skeleton.
5. That re-walk computes 4 STR ops and tries to commit them (`geom-grp-4..7`). They collide on `kernel_ops.id`
   against the freshly-cleared op-log's own counter and roll back: `§KRN_GROUP ROLLBACK … UNIQUE constraint
   failed: kernel_ops.id … committedRows=0 (all-or-NONE)` — SAFE (no partial/corrupt commit), but a loud
   rejected-toast + 4 console errors the user never asked for and can't explain.

Witness: `modeller/tests/witness_e2e_gridstretch_multi.js` (bim-ootb, `lane/modeller-ifc-open`) — X7 NO-ERROR
fails on the SampleCastle leg only; SampleHouse/Duplex legs (opened via the newer IFC-open path, which never
seeds a real STR skeleton the same way) don't hit it, which is WHY this is a "more residents" finding, not
something the pre-existing single-building `witness_e2e_gridstretch.js` (Duplex-only) could ever have shown.

## §DECISION — the one call before this is a mechanical fix
`#b-clear`'s CURRENT contract (per its own inline comment in `witness_e2e_gridstretch.js`) is narrowly scoped:
"clears BOTH the THREE group AND the oplog" — deliberately, to avoid a *different* known problem (a bare
`oplog.clear()` alone leaves stale meshes a fresh LEAF extrude optimistic-appends onto → featureId collision).
It was never designed to also reset per-walker MODULE state (STR's `ready`/skeleton, and — unaudited, not yet
checked — possibly `DiscWalker`'s MEP roster/`__dwWalks`, `CrossEdges`' `swXEdges`, the bom-graph tab). Pick one:

- **(A) Full reset** — `#b-clear` also resets EVERY walker module's in-memory state (STR ready/skeleton,
  DiscWalker roster, cross-edges, bom-graph) back to "no building open." Matches user intuition ("Clear means
  clear"), touches the most files, needs an audit of every module with clear-worthy module-local state (not
  just STR — this doc only traced STR because that's what the repro hit).
- **(B) Narrow reset** — only fix `wrapGridMove`'s specific guard: gate `ready` (or the whole re-walk call) on
  "is there STILL a loaded building substrate" (e.g. check `window.__dwBuf` is the SAME one STR walked, or
  just AND the guard with a live building-open flag that `#b-clear` DOES reset). Smaller blast radius, doesn't
  require enumerating every walker module, but leaves other maybe-stale module state (if any exists) unaudited.
- **(C) Re-scope `#b-clear` semantics** — treat Clear as "clear the AUTHORED scratch content of the CURRENT
  building" (not a full app reset) and instead make grid-move's STR-rewalk hook explicitly opt-in / require an
  explicit "this drag is happening on a building with a live STR skeleton" check at the call site, independent
  of what Clear does or doesn't reset.

Recommendation if a default is wanted: **(B)** — smallest, most targeted, doesn't require auditing every
walker module's state up front, and directly closes the repro without changing `#b-clear`'s existing (already
deliberately narrow, comment-documented) contract for unrelated reasons. But this is a genuine judgment call
on user-facing behavior ("what should Clear mean"), not something to pick silently.

## FIX PLAN (once §DECISION is made)
1. Implement the chosen option in `str_walker_outliner.js` (the `wrapGridMove`/`ready` closure) — likely just
   a few lines regardless of which option, since the root cause is a single stale boolean guard.
2. Re-run `modeller/tests/witness_e2e_gridstretch_multi.js` — must go 21/21 (X7 NO-ERROR clears on the
   SampleCastle leg), with NO regression on `witness_e2e_gridstretch.js` (8/8... wait, 6 claims X1-X6, currently
   green) or `witness_e2e_walk_ifcopen.js` (18/18, unrelated but shares `str_walker_outliner.js`).
3. If option (A) is chosen: audit `DiscWalker`/`CrossEdges`/bom-graph module state for the SAME class of leak
   before declaring done (this doc only traced STR because that's what the repro hit — don't assume the
   other modules are clean without checking, per PRIME RULE).
4. Push to `lane/modeller-ifc-open` (already has PR #642 open) or a fresh branch if #642 has merged by then.

## Witness log location
Run from repo root (`~/bim-ootb` or its `lane/modeller-ifc-open` worktree):
`node modeller/tests/witness_e2e_gridstretch_multi.js` — save output to a log file, read the log, don't trust
exit code alone (CLAUDE.md Log Mandate).
