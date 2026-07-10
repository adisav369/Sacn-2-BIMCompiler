# BIM Intent Compiler — Session Protocol

## PRIME RULE
**EXTRACT OR COMPILE ONLY.** Query the database. Copy patterns you find. Compute positions via verbs. Never invent.

**NEVER TOUCH PRODUCTION.** `deploy/live/` is the production snapshot — do not edit directly. All dev work goes to `deploy/dev/` ONLY. Read `deploy/OCI_UPLOAD.md` §RULES before any OCI upload.

## WORK-TO-ZERO (the backlog contract — enforced every session)
**No standing backlog file right now — `prompts/FRONTEND_LANE_MASTER.md §NEW BACKLOG` DRAINED 2026-07-08 (every
top-level item `✅`; same retirement treatment as the earlier `§OUTSTANDING` band, RETIRED 2026-06-20 → archived
to `prompts/archive/FRONTEND_LANE_MASTER_OUTSTANDING_drained_2026-06-20.md`). Do NOT re-walk either band, and
do NOT re-derive "is it still stale" by re-reading that 523-line file — it's settled. Two small real items were
found buried in its already-✅ prose (not lost, just not worth their own file): (1) bim-ootb `runSave()`'s 57s
wall-clock never profiled to WHERE the time goes (message-clarity was fixed, the budget wasn't); (2) bim-ootb
`38-offline-pwa.spec.js`'s hardcoded `VIEWER_URL` path blocks CI-wiring 3 offline/PWA specs (fix named in
`GH_DEPLOY_ISSUES.md` Issue 4). Pick either up directly if ever prioritized — no rediscovery needed.**

**The RULE stays live even with no active list** — the next time a dictated multi-item job is given, work it
top-to-bottom to zero in whatever file it lives:
- take the top open item → spec → implement → witness/§-log → mark it `✅ DONE (witness)` in the list → next item.
- **Do NOT stop and report "it's parked."** Keep going through the list. The default is *continue*, not *hand back*.
- **Stop only when:** (a) the user interrupts (their call, any time), or (b) an item genuinely needs a user
  fact/decision you cannot EXTRACT — then mark it `⛔ BLOCKED: <the one question>` and **move to the next item**
  (never loop on it, never silently drop it).
- **Session end** = every item is `✅` or `⛔`. Report only the ✅ list + the ⛔ questions. If the list isn't zero
  and you weren't interrupted, you stopped too early — that is the failure this rule exists to kill.
- Shared working tree: editing `~/bim-ootb/` is now **BLOCKED by a PreToolUse hook** (verified 2026-06-06) —
  work in a `/tmp/wt-*` worktree, never the shared checkout. See `~/.claude/hooks/block-shared-tree.sh`.
- **Concurrent branches (N-terminal workflow):** with multiple terminals, `main` advances under you.
  - A PR showing **`BEHIND`/`DIRTY` is *sync*, NOT a redo** — `git fetch origin && git merge origin/main`,
    re-run witnesses, push. Your commits are preserved (you layer main's in).
  - Let **auto-merge** keep it current (`gh pr merge <n> --auto --squash`; the github-actions bot also enables it)
    — but **verify it actually landed**: a squash-merge + a late push *orphans* the new commit (observed PR #138,
    2026-06-05). After a branch is squash-merged, start the follow-up off **fresh `origin/main`** — never re-use it
    (its history collides with the squash → `DIRTY`).
  - **`sw.js` is the conflict magnet** (every deploy bumps `CACHE_VERSION` + `PRECACHE_ASSETS`). On conflict: KEEP
    BOTH precache additions, take the HIGHER `CACHE_VERSION`. Never resolve by dropping the other session's hunk.
  - The worktree isolates working-dir + checked-out branch, **NOT** line-level merge conflicts on shared files.
- **Docs deploy (READ `prompts/DOCS_DEPLOY_POLICY.md` + `prompts/DOCS_DEPLOY_GUARD.md`):** publish docs ONLY
  via **`scripts/safe_gh_deploy.sh`** (the no-shrink seatbelt — W-DEPLOY-GUARD) from your FULL working branch.
  **NEVER run bare `mkdocs gh-deploy`** — it overwrites the whole `gh-pages` site and a stale/thin tree silently
  wipes live pages (happened twice). The seatbelt aborts SOFT (exit 1, `gh-pages` untouched) if a publish would
  DELETE a live page or SHRINK an html/asset >`SHRINK_TOL`% — recoverable, never a hard lock. `docs.yml` is
  DISARMED (manual-only + a no-deletion guard). **Do NOT re-arm its master auto-deploy.** `master` was reconciled
  to the live superset 2026-06-16 (`21f7bbd2`). If the guard ABORTS: your tree is missing live pages — `git merge
  origin/master` to become the superset (or `ALLOW_SHRINK=1 paths=...` to bless an intentional removal), then
  re-deploy. Never `--force` a thin tree over `gh-pages`.
- **Push before you finish (every session):** committing saves work to THIS disk only — `git push` is the backup
  and the only thing that lets work reach `master`/other branches. Leave NO committed-but-unpushed branch at
  session end (it caused a 63-commit single-copy near-miss, 2026-06-16). Pushing is a clean fast-forward to your
  own branch = pure upload, deletes nothing. Verify zero local-only: `git rev-list --count origin/<branch>..HEAD`.

## BOM PRINCIPLE
A BOM is a recipe: one parent, N children, each with a quantity. Each child can itself be a BOM — building → floor → room → furniture → leaf, recursively. Each level is atomic and self-contained. **Three Concerns never merge:** WHAT (Orders, Categories, Products), HOW (BOMs, AttributeSets, Validation), WHERE (output.db for 4D–8D downstream).

## ERP Blueprint
ERP / secured-distributed / serverless work → **`docs/ERP.md`** is the overarching blueprint; its Companion-docs map fans out to `docs/DistributedERP.md` (the doctrine + edge suite) + the `scripts/poc_*.js` witnesses. Read it first for ERP-side sessions.

## Walker Doctrine (ANTI-DRIFT — read before ANY disc-walker / MEP-walk / rules-DB work)
**`docs/internal/WalkerDoctrine.md`** is the LOCKED core doc. The settled fundamentals (do NOT re-litigate or override): small/residential
buildings (SH/DX/**SC**) walk **`duplex_rules.db`** — they do NOT use Terminal rules in production; the walk axis is BUILDING-CLASS,
discipline is a `WHERE` column (never a per-building file). Terminal = the LOD400 reference + a BORROW source for disciplines ABSENT
from residential (e.g. FP/sprinkler) rendered as a SEPARATE class with LOD400-mesh priority — borrowing a discipline's measured rows,
NOT switching the building to Terminal rules. ⚠ `disc_walker.dwInit` DEFAULTS to `terminal_rules.db` (back-compat) — a residential
caller MUST pass `duplex_rules.db`. `§DWG` walks Terminal-on-small as a GENERALIZATION TEST, not the production path.

## Session Startup
0. Before reading `~/bim-ootb` as canon: `git -C ~/bim-ootb fetch origin && git -C ~/bim-ootb merge --ff-only origin/main`
   (clean tree only). A 21-commit-stale checkout made a review report SHIPPED code as missing (2026-06-12).
1. User states activity category (BOM/geometry | schema/ERP | IFC/extraction | SRS/spec | pipeline/debug) → read only matching [category] feedback files from MEMORY.md
2. Read PROGRESS.md §Current State (gate table, what's next)
3. Read `docs/WorkOrderGuide.md` §Invention Boundary + §Step 5-6 (pipeline flow)
4. Read the analysis doc for the building you're working on (`docs/{Building}Analysis.md`)
5. Read the Java interface of whatever you're modifying
6. Run `./scripts/run_RosettaStones.sh classify_{prefix}.yaml` to verify current state

## Session Closeout
**Auto-compact is OFF.** When context reaches ~5%, wrap up and exit cleanly to a new session.

Before ending, update PROGRESS.md with:
- What was done
- What's next
- Witness count if claims changed
- Run space contract check — if `space_contract` FAIL, fix before committing

### Housekeeping (every session end)
- Update MEMORY.md. Delete obsolete topic files. Keep MEMORY.md ≤80 lines. Screenshots: `~/Pictures/Screenshots/`
- If PROGRESS.md > 80 lines, archive DONE items as single-line pointers to spec docs

## Watchdog Protocol (runs in same session after every coder task)
- Read the coder's `# DONE` appendix — every claim must have a `§` log line proving it. No log line = not done. Flag it.
- If log doesn't cover a claim — coder must add `_log()`, rerun, and produce the evidence before closing.

## Standing Rules
- One bounded task per session
- Witnesses prove; SanityCheck is fallback
- All geometry is a maths issue — verify numerically via pipeline logs, not manual DB queries
- **Log Mandate:** After ANY run, save output to a log file, read the log before conclusions — exit code is not evidence. Never rely on inline terminal output. Improve FINE logging to reveal issues; extract insights from log only, never invent. Every prompt file opens with `# ⚠ DO NOT REMOVE` block stating scope + "read the log." Honour until DONE.
- **Anti-Drift / No Self-Invented Rules (HARD RULE, non-negotiable — 2026-07-06):** Every role boundary, admin-scope
  question, and workflow decision on this project is ALREADY DEFINED in this file + `MEMORY.md`'s feedback entries.
  There is nothing left to improvise. When challenged ("aren't you supposed to X?", "isn't this already defined?")
  or when unsure whether an action is in-scope: **STOP. Grep this file + the feedback memory files for the actual
  rule FIRST. Never fabricate a plausible-sounding self-restriction on the spot** — inventing a NEW boundary under
  pressure (e.g. "that's not my role" with no memory citation) is ITSELF the drift, not a correction. State the
  verified rule with its source, then act — don't oscillate, don't ask a permission question a disciplined read
  would have already resolved. Documenting findings/recommendations into a canonical `prompts/*.md` file (append
  a dated section) is ALWAYS in scope — see `feedback_prompt_file_organization.md` — it is not something to ask
  permission for, and it is not something to retract under pushback either. This is a DETERMINISM requirement:
  the whole project runs on documented, checkable rules, not per-session judgment calls that reset every drift.
- **Housekeeping is a standing duty, done unprompted, every time — not a crisis cleanup (2026-07-06):** Every
  session that adds to `MEMORY.md` or a `prompts/*.md` file checks, in that same turn: is this a NEW file when
  a canonical one already owns the topic (merge it in, delete the new one)? Did the entry add prose to
  `MEMORY.md` instead of a bare link (fix it now, don't wait to be told)? Is `PROGRESS.md`/`MEMORY.md` back over
  their line budgets (compact now)? This is not something the user asks for — `MEMORY.md` itself has carried a
  "links only, ≤80 lines" rule (`feedback_memory_links_only.md`) since 2026-06-04 and drifted back to 264 lines
  of prose TWICE before this rule was written. Check after every edit, not when it's already a mess again.
- **A session working a `prompts/#.md` file updates ONLY that file, never `MEMORY.md` (2026-07-06):** this is the
  root cause of the repeated `MEMORY.md` bloat above — task/builder sessions kept creating their own memory
  entries duplicating what their own prompts file already recorded in full. Findings/status/proof go in the
  prompts file's dated section, full stop. Writing to memory is a separate, deliberate synthesis pass for
  durable cross-session lessons, not a byproduct of finishing a task — see `feedback_prompt_file_organization.md`
  rule 0.
- **Deploy Flow (deploy/dev/ ONLY):** Edit → syntax check → verify all `§` tags exist → save test log → upload to dev bucket → smoke test URLs → fetch back and verify content → confirm file is loaded by viewer. ONE flow, never stop partway or ask user to check.
- **OCI MIME Rule:** EVERY `oci os object put` MUST include `--content-type` — OCI does NOT infer it from the extension; omitting it → `X-Content-Type-Options: nosniff` block + silent script failure. Full MIME table: `deploy/OCI_UPLOAD.md §RULES`.
- **Spec-First (ALL work):** Spec before code, spec before tests, spec before prompts. No implementation without a written spec section. New features: witness claim first, then implement.
- **Tests expose issues:** Every test must name the issue it proves or disproves. A test that passes without revealing whether the issue is solved is not a test.
- **Browser testing — §-log first, Playwright second:** Primary browser verification = whitebox `§`-tagged `console.log()` output. The coder reads `§` lines to confirm values, counts, and state are correct. Playwright is secondary — for wiring/deploy checks only (scripts load, buttons exist, DB returns data). Do NOT add Playwright tests for value verification — add a `§` log line instead. See `docs/TestArchitecture.md` §Browser Testing. Run `node deploy/dev/tests/audit_specs.js` after any Playwright changes — must exit 0.
- **Anti-Drift Policy:** Read `docs/TestArchitecture.md` §Anti-Drift before adding BOMs, products, or geometry paths
- **Pre-Flight Citation:** Before code changes, cite the spec: `// Implementing BBC.md §X.Y — Witness: W-NAME`
- **Traceability:** Check `TestArchitecture.md` §Traceability Matrix before and after changes

## ⛔ LFS QUOTA EXHAUSTED — HARD BLOCK, ALL LOCAL, NO EXCEPTIONS (2026-07-11)
**GitHub confirmed the LFS bandwidth quota is now at 0 — not "approaching the cap," actually exhausted.
Resets on the 1st of every month (user-confirmed) — next reset 2026-08-01.** Until that date (or an
explicit user confirmation it topped up early), treat ALL git push/fetch against `bim-ootb` and
`bim-compiler` as at risk — **confirmed empirically 2026-07-11: a push whose diff touched ZERO LFS-tracked
files still hung for 2+ minutes and never landed** (`bim-ootb` branch `fix/dw-datum-port` @ `4ff22c0` — the
`git-lfs` pre-push hook appears to probe the LFS endpoint regardless of whether the push actually needs to
upload anything, and that probe stalls against an exhausted quota). **So the rule is NOT "only pushes that
touch LFS content are blocked" — it's "any push to either repo may hang until reset."** Don't retry a
hung push repeatedly (each attempt risks re-triggering the same stall); if a push doesn't return within
~30s, stop and report it rather than let it sit.

This supersedes the softer "reduce usage" framing of the two sections below (still read them for the
mechanism/cleanup already done — this section is the escalation on top). Until reset:
- **NEVER push a commit whose diff touches ANY already-LFS-tracked file's content** — not just new `.db`
  additions (already banned below), but ANY change to an existing tracked binary (a rules-DB update, a
  regenerated mesh/geo file, anything matching `.gitattributes`' `filter=lfs` patterns). If a task needs to
  change one, commit it LOCALLY only and stop there — do not `git push` that commit. Say so explicitly when
  reporting the work, don't push-and-hope.
- **NEVER create a worktree/checkout a branch whose LFS blobs aren't already in the local cache**
  (`.git/lfs/objects`) — that triggers a fetch against the exhausted quota. Before `git worktree add` or
  `git checkout` of a branch you haven't already worked with locally, check whether its LFS content is
  already cached; if unsure, ask rather than risk a failed/quota-consuming fetch. Branches already checked
  out in an existing worktree are safe to keep using (their blobs are already local).
- **This applies to every Agent-tool prompt that touches git** — spawned verification/build agents must be
  told explicitly not to push LFS-touching commits and not to fetch new LFS content, same as this session.
- **Non-LFS-tracked files (code, docs, most everything) are unaffected** — normal commit/push continues for
  anything that isn't itself an LFS-tracked binary or doesn't modify one.

## DB Storage Policy — GitHub LFS bandwidth (2026-07-10)
**GitHub emailed a 10GB/month LFS bandwidth-cap warning.** `.gitattributes` has a blanket `*.db filter=lfs`
rule and 59+ `.db` files are already LFS-tracked (`deploy/buildings/*_extracted.db`/`*_library.db`,
`build/*.db`, etc.) — every clone/fetch/checkout of a branch touching these re-downloads them through the
capped quota. **From now on: do NOT commit/force-add a `.db` file to git, even if `.gitattributes` would
route it through LFS.** Most DB paths are already `.gitignore`d (`/library/*`, `deploy/buildings/`,
`database/*.db`, `/build/erp/*.db`, etc. — see `.gitignore`) — that's correct, keep it that way, don't
`git add -f` past it. Two channels replace committing the binary:
- **Schema/rules/pattern DBs** (small, structurally regenerable — `duplex_rules.db`, `terminal_rules.db`,
  `ERP.db` seed data): migration scripts in `migration/*.sql` (see Sacred Files below — `DV_<prefix>_rules.sql`
  is already this pattern for mined rules) or the mining scripts that generate them
  (`run_RosettaStones.sh`/`onboard_ifc.sh`/`project_rule_mesh_binding.py` etc.) — regenerate on demand, don't
  version the binary.
- **Extracted/derived building DBs** (`deploy/buildings/*_extracted.db`/`*_library.db`, mesh/geo DBs — NOT
  reproducible from a short SQL script, only from re-running extraction on the source IFC): distribute via
  **OCI** (`deploy/OCI_UPLOAD.md` §RULES — the existing dev/live snapshot channel), not git/LFS. If a building
  needs to travel with a branch for local dev, keep it gitignored and hand it off out-of-band (OCI, or a
  read-only copy), never commit it.
- **Already-LFS-tracked files are sunk cost** — not retroactively purged as part of this rule (that's a
  separate, disruptive history-rewrite decision, not taken here). This rule only stops the bleeding going
  forward: no NEW `.db` commits.

## Worktree Hygiene — the OTHER LFS bandwidth drain (2026-07-10, same root cause, different mechanism)
**Committing new DBs (above) was only half the bandwidth story.** The other half: bim-ootb's
`modeller/mesh.db` (120MB) + `modeller/*_geo.db` are LFS-tracked, and DOZENS of parallel worktrees
(peaked at 49 in bim-ootb, 20 in bim-compiler) each pull a fresh 100–250MB blob from GitHub the moment a
branch whose mesh/geo data isn't already in the local LFS cache gets checked out. **Real end users never
cost LFS bandwidth at all** — they hit the deployed static site (GH Pages / OCI), never `git clone` — so
100% of the quota is dev/agent-side worktree churn. A second, fully separate clone (`~/Projects/bim-ootb`,
its own independent LFS cache) doubled bandwidth on any overlapping blob and has been removed (2026-07-10,
confirmed clean + 100+ commits stale first). Rules going forward:
- **Before `git worktree add`, always run `git worktree list` first.** If a worktree already exists for
  the branch/commit you need, reuse it (`cd` in, `git fetch`/`pull` if needed) — do NOT create a second one
  at a different path. This applies to Agent-tool prompts too: any prompt that spawns a verification/build
  agent must instruct it to check for and reuse an existing worktree before creating a fresh one.
- **One local clone per repo, no exceptions** — never `git clone` a second copy of `bim-ootb` or
  `bim-compiler` alongside the primary checkout.
- **Prune on sight, not on a schedule.** When a worktree's branch is fully pushed (`git rev-list --count
  origin/<branch>..<branch>` = 0) AND clean (`git status --short` empty), remove it
  (`git worktree remove <path>`) — don't let dozens accumulate "just in case." A worktree with unpushed
  commits or uncommitted changes is NOT safe to prune — leave those alone, they're someone's in-progress work.
- **`.claude/worktrees/agent-*` are harness-managed** — never manually `git worktree remove` these; they're
  the Claude Code tool's own isolation mechanism, not dev-created clutter.

## Sacred Files (edit with extreme care)
- `deploy/live/*` — PRODUCTION snapshot, never edit (see PRIME RULE)
- `migration/*.sql` — append only, never modify existing migrations. EXEMPT: `DV_<prefix>_rules.sql` — regenerated
  mined artifacts (written by `run_RosettaStones.sh`/`onboard_ifc.sh` each gate run, applied by `rebuild_erp.sh`),
  not ledger migrations; in-place regeneration is their normal lifecycle (decided 2026-07-03).
- `BuildingCompiler.java` — main orchestrator, many dependencies
- `RosettaStoneGateTest.java` — defines G1-G6 gates (compiler-reconstruction truth). NOTE: no CI in this repo runs it — "GREEN before commit" is a LOCAL discipline (Anti-Drift #5), not automation. See `docs/TestArchitecture.md` §Truth Model (2026). The headless smoke subset runs via `.github/workflows/ci.yml` + `scripts/system_is_real.sh`.
- `X_M_BOM.java` / `X_M_BOMLine.java` — EntityType guards, GodMode bypass
