# BIM Intent Compiler — Session Protocol

## PRIME RULE
**EXTRACT OR COMPILE ONLY.** Query the database. Copy patterns you find. Compute positions via verbs. Never invent.

**NEVER TOUCH PRODUCTION.** `deploy/sandbox/` is LIVE. Do not read it to edit, do not edit it, do not write to it. All dev work goes to `deploy/dev/` ONLY. Promote to sandbox only when the user explicitly says "promote to prod".

## BOM PRINCIPLE
A BOM is a recipe: one parent, N children, each with a quantity. Each child can itself be a BOM — building → floor → room → furniture → leaf, recursively. Each level is atomic and self-contained. **Three Concerns never merge:** WHAT (Orders, Categories, Products), HOW (BOMs, AttributeSets, Validation), WHERE (output.db for 4D–8D downstream).

## Session Startup
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
- Update MEMORY.md (witness/verb counts), SCHEMA_QUICKREF.md (row counts). Delete obsolete topic files. Keep MEMORY.md ≤80 lines, CLAUDE.md ≤45 lines. Screenshots: `~/Pictures/Screenshots/`
- If PROGRESS.md > 80 lines, archive DONE items as single-line pointers to spec docs

## Watchdog Protocol (runs in same session after every coder task)
- Read the coder's `# DONE` appendix — every claim must have a `§` log line proving it. No log line = not done. Flag it.
- Read `OPEN_ISSUES.txt` (mirrors §18.1 open issues) — an issue is only removed when a `§` log line in this session proves it fixed. No log line = stays open.
- If log doesn't cover a claim — coder must add `_log()`, rerun, and produce the evidence before closing.

## Standing Rules
- One bounded task per session
- Witnesses prove; SanityCheck is fallback
- All geometry is a maths issue — verify numerically via pipeline logs, not manual DB queries
- **Log Mandate:** After ANY run, save output to a log file, read the log before conclusions — exit code is not evidence. Never rely on inline terminal output. Improve FINE logging to reveal issues; extract insights from log only, never invent. Every prompt file opens with `# ⚠ DO NOT REMOVE` block stating scope + "read the log." Honour until DONE.
- **Deploy Flow (deploy/dev/ ONLY):** Edit → syntax check → verify all `§` tags exist → save test log → upload to dev bucket → smoke test URLs → fetch back and verify content → confirm file is loaded by viewer. ONE flow, never stop partway or ask user to check.
- **Spec-First (ALL work):** Spec before code, spec before tests, spec before prompts. No implementation without a written spec section. New features: witness claim first, then implement.
- **Tests expose issues:** Every test must name the issue it proves or disproves. A test that passes without revealing whether the issue is solved is not a test.
- **Anti-Drift Policy:** Read `docs/TestArchitecture.md` §Anti-Drift before adding BOMs, products, or geometry paths
- **Pre-Flight Citation:** Before code changes, cite the spec: `// Implementing BBC.md §X.Y — Witness: W-NAME`
- **Traceability:** Check `TestArchitecture.md` §Traceability Matrix before and after changes

## Sacred Files (edit with extreme care)
- `deploy/sandbox/*` — PRODUCTION, never edit (see PRIME RULE)
- `migration/*.sql` — append only, never modify existing migrations
- `BuildingCompiler.java` — main orchestrator, many dependencies
- `RosettaStoneGateTest.java` — defines G1-G6 gates, changes break CI
- `X_M_BOM.java` / `X_M_BOMLine.java` — EntityType guards, GodMode bypass
