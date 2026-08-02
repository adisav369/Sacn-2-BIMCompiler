# ⚠ DO NOT REMOVE
**SCOPE — one job, nothing else.** Re-sweep the two tuned constants (`voidMode` width cap W, and the
door `pierce` depth) on the CORRECTED void axes, and report the matrix. This lane's constants were
both fitted against a transposed carve (§21.43), so neither is meaningful until the axes are right.
**This is a MEASUREMENT job.** You may ship a new default ONLY if a cell beats baseline on all five
gates; otherwise you report and stop. Do not redesign the substrate.

**READ THE LOG AFTER EVERY RUN.** Exit code is not evidence — every witness here prints `§`-tagged
numbers and a VERDICT line, and a run can exit 0 with a FAIL verdict inside. Save each run to a log
file and read it before drawing any conclusion. Honour this preamble until the file is marked DONE.

**Model note:** written for a Fable-class execution session. Do not write to `MEMORY.md`. All
findings, status and proof go into THIS file as dated sections (project rule — a session working a
`prompts/*.md` file updates only that file).

---

## §0 READ FIRST, then do not re-derive any of it

1. `prompts/Modeller/DISC_Walker/ROOM_PATHING_SUBSTRATE.md` — the concept doc. §5 trials, §6 method
   rules, §9 the gate suite.
2. `prompts/Modeller/DISC_Walker/VIEWER_FIND_PANEL_ROOM_ACCURACY.md` **§21.43 → §21.44** only.
   Everything before §21.29 is superseded.

**SETTLED 2026-08-02 — these are closed. Re-deriving any of them is the failure this section exists
to prevent:**
- §21.41's doorway-merge fix: **falsified before coding.** It separates cleanly (0.0% of >10 m²
  pockets misclassify) but reaches 1 of 8 far-end groups. **Do not implement it.**
- §21.41's root cause ("the graph terminates inside the doorway"): **retracted.** Those pockets carry
  2–5 door-matched openings each.
- `rel_contained_in_space` as an oracle: **retracted, it is circular** — written by our own
  `scripts/compile_rooms.py:1295`, and every door names exactly ONE space so it cannot express an
  adjacency. This lane has no independent oracle. Do not go looking for one in this job.

## §1 Setup

```bash
cd ~/bim-ootb && git worktree list          # FIRST. Reuse /tmp/wt-roompath if it exists.
# only if it does not:
GIT_LFS_SKIP_SMUDGE=1 git worktree add /tmp/wt-roompath review/roompath-redundancy
```
Fixtures are `~/bim-ootb/buildings/{Clinic,LTU_AHouse}_extracted.db` — never OCI, never the
bim-compiler copy. Engine is `viewer/lib/room_walker.js`. `RES = 0.20`, `SEAL = 2`.

Apply the axis fix, which is already written and measured, just not shipped:
```bash
cd /tmp/wt-roompath && git apply roompath_diagnostics/patch_21_43_transpose.diff
```
It changes `_rasterizeSpine`'s carve from `max/min`-normalised (always long-along-world-x) to each
void's own axes. Output is identical wherever `bbox_x >= bbox_y`, so half the fleet is untouched by
construction.

## §2 The sweep

`pierce` is currently hardcoded (`var pierce = v[5] ? 10 * RES : RES`). **Add a knob, do not edit the
constant per run** — an env var read once (`ROOMPATH_PIERCE`, default `10`, in CELLS) or an `opts`
field threaded through `spineMap`/`storeySpine`. Default behaviour must be unchanged when the knob is
unset; prove that with one run before sweeping.

Matrix, both fixtures, every cell:
```
W       ∈  B, 1.5, 2.0, 3.0, 6.0, cur      (voidMode; B = door-hosted openings only)
pierce  ∈  4, 6, 8, 10, 12, 14  cells      (0.80m .. 2.80m)
```
Record per cell: `unroutable%`, `stranded n/total`, `§O3 phantom share + verdict`, `§T5 retention`,
`wall m²`, `enclosed m²`. 36 cells × 2 fixtures — batch it, write one CSV/TSV plus the logs, and
stream progress; do not run 72 silent invocations.

## §3 The gate — all five, no exceptions

| Witness | Asserts | Baseline to beat |
|---|---|---|
| `witness_room_path_aperture_tier.js` | §T1–§T5, retention ≥90% | 100% / 100% |
| `witness_room_path_overlink.js` | §O2 sweep, **§O3 phantom share** | LTU 20% PASS |
| `witness_room_path_stranded_cause.js` | §SC3 independent breaks | Clinic 9 · LTU 14 |
| `witness_room_path_cluster_boundary.js` | §CB5 sealed suites | Clinic 7/9 · LTU 8/14 |
| `roompath_diagnostics/doorprov6.js` | §DP9 stranded area · §DP10 blindness control | see §21.43b |

Baseline on the UNPATCHED tree, reproduced 2026-08-02 on this machine:
```
Clinic  50/186  unroutable 49.3%      LTU (W:3.0, live default)  18/277  unroutable 18.4%
                                      LTU (cur)                  15/263  unroutable 16.4%
```

**§O3 is the gate that decides.** It is the one that reads 20% PASS on the transposed carve and 94%
FAIL on the corrected one at the current constants. A cell is only a candidate if §O3 PASSES.

## §4 Acceptance, and the stop condition

- **Ship a new default** only if some cell has §O3 PASS, §T5 ≥90%, and unroutable at or below
  baseline on BOTH fixtures. Then: change the defaults, re-run all five gates once more on the final
  values, log them, commit, push, and record the numbers here.
- **If no cell qualifies — say so plainly and STOP.** That is a real result about this substrate, not
  a failure to try hard enough. Report the best cell per fixture with its §O3 verdict, and state
  explicitly that the corrected geometry cannot reach 18.4% at any (W, pierce). **Do not invent a
  third constant, a per-building value, or an area threshold to close the gap** — the standing
  constraint is that a rule must hold for any IFC a user imports (§7 of the concept doc).
- Report the MATRIX, not just the winning cell. A single number from a sweep hides whether the
  optimum is a plateau or a spike; a spike is a fitted constant and this lane does not accept one.

## §5 Rules that bind this job

- **Spec before code.** Any engine change gets a dated section here first, with its prediction, before
  it is written. §21.43 is the worked example: its prediction failed and the change was withdrawn.
- **Whitebox `§` logs are the proof.** No screenshots, no Playwright for value verification.
- **Push before you finish.** `git rev-list --count origin/<branch>..HEAD` must be 0 at session end.
- Nothing from this lane is deployed and nothing should be deployed by this job. `common/room_graph.js`
  and `viewer/navigate_find.js` stay byte-unchanged.
- If a run needs more than ~30s of `git push` it may be the LFS pre-push probe hanging — stop and
  report, do not retry in a loop.

## §6 Status log — append a dated section per run

*(empty — first worker fills this in)*
