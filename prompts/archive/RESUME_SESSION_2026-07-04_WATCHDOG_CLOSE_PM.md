# ⚠ DO NOT REMOVE — Session resume: watchdog role, 2026-07-04 PM (supersedes
`prompts/archive/RESUME_SESSION_2026-07-04_WATCHDOG_CLOSE.md`)

**Token conservation is the priority right now.** Keep this session light: no heavy multi-agent fan-outs
unless a finding genuinely demands it, no re-deriving things already settled below. Read this doc, then go
straight to the task in `§NEXT SESSION'S JOB`.

## §NEXT SESSION'S JOB (the actual ask)
Scan recent `prompts/*` files for finished work (`# DONE`, `✅ DONE`, closed spec docs) and do a **QA review
WITH THE USER** — not a solo code-correctness pass — on whether the *direction* of each finished
function/feature is right (product/UX call), not just whether it's implemented and green. This is a
dialogue task: surface what's recently landed, ask the user's read on direction, don't just self-verify and
report done. Keep it bounded — a survey + discussion, not a deep-dive per item unless the user wants one.

## §ALSO START THIS SESSION — comb transaction/persistence code for the TOCTOU shape
User's explicit ask (2026-07-04): don't just file the T7 race-condition bug away as a lesson — actively
START auditing for it. **The shape to grep for:** any async function that (1) reads/captures a boundary,
cutoff, or ID value, (2) then hits an `await` (crypto op, IndexedDB/store write, network), (3) then
deletes/mutates data keyed on that earlier-captured value. That's the exact TOCTOU pattern §T7-RACE found in
`erp/erp_shard.js` (a mid-shard commit could be silently deleted + never archived = a signed sale vanishing).
Full writeup + why normal functional testing can't catch it: `feedback_toctou_race_scrutiny_pattern.md`.
**Where to start looking (real transaction/persistence code, not everything):** `erp/kernel_ops.js`,
`erp/pos_lens.js` (other commit paths beyond the one already fixed), `erp/crud_overlay.js`, any other
`archive`/`shard`/`snapshot`/`compact`-style function touching `kernel_ops` or `glassbowl_kernel_ops`. This
is a CHEAP, targeted grep-and-read pass — not a Workflow/multi-agent review (that's still hard-denied, see
below). Report any candidates found; don't fix speculatively without a constructed race witness proving it's
real first (same discipline §T7-RACE used).

## §CLOSED THIS SESSION
| What | Where | Verification |
|---|---|---|
| FABLE5_FOLLOWUP_2026-07-04.md — all 3 items | bim-ootb PR #639 (`lane/fable5-followup`, pushed, **not yet merged — human call**) | W-T7-HOST+W-T7-INC 62/62, W-TEAM-WIRE 6/6, W-POS-PILLBAR new+pass; spec doc updated with DONE markers |
| Self-caught TOCTOU race in T7 sharding (§T7-RACE) | `erp/erp_shard.js`, same PR | Verified for real via the log (`build/t7_incremental.log`), not just trusted from commit prose |
| **Workflow tool ran away twice, ~1.5M tokens each, user locked out ~4hrs** | `~/.claude/settings.json` | Root cause found: `skipWorkflowUsageWarning` was silently `true` → set `false`. Also **hard-denied**: `permissions.deny:["Workflow"]` — re-enable deliberately only, once risk-gating discipline (gate by files-touched, not task-size-vibes) is actually applied. See `feedback_workflow_scope_to_risk.md` + `feedback_toctou_race_scrutiny_pattern.md` in memory. |
| SQLiteProjectMap.png fact-check | `docs/SQLiteWasmArchitectureActual.html`, live on gh-pages (an EARLIER iteration — see below) | 6/7 of the screenshot's project-specific claims were false vs actual code (sql.js not sqlite3.wasm, no OPFS-SAH, no Promiser, separate BIM/ERP DBs — confirmed, matches doctrine) |
| CI/CD investigation — "do we have a must-pass drop-IFC→Viewer test?" | `~/bim-ootb/.github/workflows/ci.yml` `e2e-tests` job → `tests/specs/s274-golden-path.spec.js` | **Already exists, already required** in branch protection (`fast-checks`+`e2e-tests`). Auto-merge (`gh pr merge --auto --squash`) already wired post-e2e. One real gap: assertion is whitebox-log-based, not a hard `renderer.info.render.triangles > 0` check — optional hardening, not urgent, user hasn't asked for it yet. |

## §STILL OPEN
- **PR #639** — needs the user's merge decision (code is solid, witnessed; see table above).
- **`docs/SQLiteWasmArchitectureActual.html` — LOCAL iteration ahead of live, NOT deployed.** Committed as
  WIP (`9fce2ac96`) so it isn't lost, but gh-pages still serves the earlier "How the Database Works" 5-panel
  poster. The local file is a VFS-centered animated swipe diagram (before/after: real disk vs
  MEMFS/OPFS, clip-path wipe animation) built through live back-and-forth this session. **Known gap:** the
  durability/op-log footer was dropped in the last rewrite and needs re-adding with the VERIFIED-accurate
  framing (boot loads a baked `.db` snapshot directly, not a replay; op-log records local edits + supports
  manual chainVerify; full replay-to-fresh-db code exists but is unused at boot — see commit `9fce2ac96` for
  citations). Preview locally first (`mkdocs serve -a 127.0.0.1:8765` may still be running, pid check via
  `pgrep -fl "mkdocs serve"`; restart if not), get the user's sign-off on the visual, THEN redeploy via
  `scripts/safe_gh_deploy.sh` (guarded, bless `.nojekyll` + this file's shrink as usual).
- ARC occupancy density drift (99%→92-95%, `W-DW-DENSITY-TE`) — real, low-priority, unexplained, carried
  forward, not urgent (see `project_arc_meshreadpixels_branch_unmerged.md`).
- Kernel T4+T5 (unify 3 kernel copies) — still ⛔ browser-gated, unchanged.
- HBA §P11 deep-link windows 53042/316/53036 — still unseeded, blocked on a real spec from the user.
- Modeller §NEEDS-DESIGN remainder — PBR, SSAO. Not started.
- `prompts/` archiving remainder — ~70 old misc files, thematic only, never got a concrete pass. Low priority.

## §PROCESS NOTES worth repeating
- **Merge-to-main is always the user's call**, even fully witnessed/green work — push branch + open PR is
  routine admin scope, `gh pr merge` is not (see `feedback_act_autonomously_dont_ask.md`, refined 2026-07-04:
  "git admin decision to push. I have play direction role").
- **Verify claims against actual code before they go in a public doc or a design decision** — this session
  caught itself twice: an inaccurate "op-log replays into memory" claim (via a targeted agent check against
  real boot-sequence code), and the original screenshot's 6/7 false project-specific claims.
- **Gate deep multi-agent review by files touched** (kernel/signed-log/persistence/security), not by a vague
  sense of task size — and even then, cap the fan-out. Don't re-run the full adversarial treatment on a
  second pass over already-hardened code.
