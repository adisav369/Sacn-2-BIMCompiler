# ⚠ DO NOT REMOVE — Scope: make docs publishing un-wipeable. Read the log after every run.
# Prompt: DOCS_DEPLOY_GUARD — stop stale/thin-tree deploys from silently wiping live pages.
# Companion to (does not replace) prompts/DOCS_DEPLOY_POLICY.md + CLAUDE.md §"Docs deploy".

## The incident this prevents (root cause, plain)
`mkdocs gh-deploy` **rebuilds the whole site from ONE tree and force-replaces `gh-pages`** — it does not
merge, it overwrites. So **last-deploy-wins, totally**: whatever the deploying tree lacks disappears from the
live site, even though it's safe in git history.

Observed 2026-06-16: work (the MigrateComparisonPaper headline-popover + carrier animation + newer teasers)
lived only in a **feat branch**, never merged to `master`. A deploy was then run from a **worktree off
`master`** (a "clean superset of master" — but `master` was itself stale vs feat), so the live page reverted
to the older version. Nobody was editing the file at deploy time — it was simply **stale at the publish step**.

**The precise failure condition (both must hold):**
1. an edit lives in one tree and is **not merged into the tree that publishes**, AND
2. a **full-overwrite publish** runs from that other (stale/thin) tree.

A "who else is editing this file?" lock does NOT catch this — the file wasn't being edited, it was stale.
The only reliable checkpoint is **at publish time**.

## The fix — two parts (part 2 is the load-bearing one)
1. **Merge-before-publish (process).** Before any deploy: `git fetch origin && git merge origin/master` so
   the publishing tree is a **superset** of what's live (it can then only ADD, never DROP). Fragile alone —
   humans forget.
2. **No-shrink publish guard (the seatbelt — AUTOMATE THIS).** A wrapper that refuses to deploy a build that
   would make the live site smaller. It does not trust anyone to remember step 1; it blocks a thinning
   publish regardless of who runs it or how stale their tree is.

## Build: `scripts/safe_gh_deploy.sh` (replace bare `mkdocs gh-deploy` everywhere)
Behaviour (each step logs to `build/docs_deploy.log` — read it before concluding):
1. `git fetch origin -q`; `git merge --no-edit origin/master`. On conflict → **ABORT**, print the conflicted
   files, tell the human to resolve (never auto-pick). (Spec-first: this is the "become the superset" step.)
2. `mkdocs build -q -d /tmp/site_new` (the about-to-deploy tree).
3. Fetch current live: `git fetch origin gh-pages`; materialize its file list + sizes (e.g.
   `git ls-tree -r -l origin/gh-pages`).
4. **GUARD (the falsifier):** diff live vs new:
   - any path present in **live** but **absent** in new → **ABORT** (deletion).
   - any `*.html`/asset whose new size is **smaller than live by more than `SHRINK_TOL` (default 5%)** →
     **ABORT** (content wipe, e.g. the MCP case — same path, fewer bytes).
   - print the exact offending paths + old→new sizes. Exit non-zero. Do **not** deploy.
5. Only if the guard passes: `mkdocs gh-deploy` (or `ghp-import` of `/tmp/site_new`).
6. Post-deploy: poll the live URLs of the changed pages + a fixed canary set (MigrateComparisonPaper,
   DistributedERP, HolyGrail, ERP, RetailScaleStory, glassbowl, POS_WAN_SCALE_BENCH) → all must be HTTP 200.

`SHRINK_TOL` is an env knob; legitimate big deletions (rare) are done by re-running with
`ALLOW_SHRINK=1 paths="a,b"` naming exactly what may shrink — so an intentional removal is **explicit**,
never silent.

## Also
- Keep `docs.yml` **disarmed** for master auto-deploy (CLAUDE.md). The CI guard there only protects the CI
  path; the **manual CLI path is the gap this prompt closes**. Wire the SAME guard into both.
- Root-cause hygiene: **merge doc edits to `master` promptly** — don't let docs hang in a feat branch. One
  source of truth means no tree is ever stale enough to wipe.

## Witness (prove it, don't assert it) — W-DEPLOY-GUARD
- §GUARD-DELETE: build a /tmp site missing a page that exists live → run guard → MUST abort, naming the page.
- §GUARD-SHRINK: take a live page, truncate it >5% in the new build → guard MUST abort (the MCP scenario).
- §GUARD-PASS: a true superset build (live + one added page) → guard MUST allow.
- §GUARD-OVERRIDE: `ALLOW_SHRINK=1 paths=...` on a named shrink → allowed; an UNnamed shrink still aborts.
Each test names the issue it proves; a pass that can't tell whether the guard works is not a test.

## Acceptance
Bare `mkdocs gh-deploy` is no longer run by any session; all publishing goes through `safe_gh_deploy.sh`;
W-DEPLOY-GUARD is green; a deliberately stale tree **cannot** shrink the live site (it aborts with the list).
