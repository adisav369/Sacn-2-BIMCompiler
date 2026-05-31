# BIM Intent Compiler — Session Protocol

## PRIME RULE
**EXTRACT OR COMPILE ONLY.** Query the database. Copy patterns you find. Compute positions via verbs. Never invent.

**NEVER TOUCH PRODUCTION.** `deploy/live/` is the production snapshot — do not edit directly. All dev work goes to `deploy/dev/` ONLY. Read `deploy/OCI_UPLOAD.md` §RULES before any OCI upload.

## BOM PRINCIPLE
A BOM is a recipe: one parent, N children, each with a quantity. Each child can itself be a BOM — building → floor → room → furniture → leaf, recursively. Each level is atomic and self-contained. **Three Concerns never merge:** WHAT (Orders, Categories, Products), HOW (BOMs, AttributeSets, Validation), WHERE (output.db for 4D–8D downstream).

## ERP Blueprint
ERP / secured-distributed / serverless work → **`docs/ERP.md`** is the overarching blueprint; its Companion-docs map fans out to `docs/DistributedERP.md` (the doctrine + edge suite) + the `scripts/poc_*.js` witnesses. Read it first for ERP-side sessions.

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
- **Deploy Flow (deploy/dev/ ONLY):** Edit → syntax check → verify all `§` tags exist → save test log → upload to dev bucket → smoke test URLs → fetch back and verify content → confirm file is loaded by viewer. ONE flow, never stop partway or ask user to check.
- **OCI MIME Rule:** EVERY `oci os object put` MUST include `--content-type` — OCI does NOT infer it from the extension; omitting it → `X-Content-Type-Options: nosniff` block + silent script failure. Full MIME table: `deploy/OCI_UPLOAD.md §RULES`.
- **Spec-First (ALL work):** Spec before code, spec before tests, spec before prompts. No implementation without a written spec section. New features: witness claim first, then implement.
- **Tests expose issues:** Every test must name the issue it proves or disproves. A test that passes without revealing whether the issue is solved is not a test.
- **Browser testing — §-log first, Playwright second:** Primary browser verification = whitebox `§`-tagged `console.log()` output. The coder reads `§` lines to confirm values, counts, and state are correct. Playwright is secondary — for wiring/deploy checks only (scripts load, buttons exist, DB returns data). Do NOT add Playwright tests for value verification — add a `§` log line instead. See `docs/TestArchitecture.md` §Browser Testing. Run `node deploy/dev/tests/audit_specs.js` after any Playwright changes — must exit 0.
- **Anti-Drift Policy:** Read `docs/TestArchitecture.md` §Anti-Drift before adding BOMs, products, or geometry paths
- **Pre-Flight Citation:** Before code changes, cite the spec: `// Implementing BBC.md §X.Y — Witness: W-NAME`
- **Traceability:** Check `TestArchitecture.md` §Traceability Matrix before and after changes

## Sacred Files (edit with extreme care)
- `deploy/live/*` — PRODUCTION snapshot, never edit (see PRIME RULE)
- `migration/*.sql` — append only, never modify existing migrations
- `BuildingCompiler.java` — main orchestrator, many dependencies
- `RosettaStoneGateTest.java` — defines G1-G6 gates, changes break CI
- `X_M_BOM.java` / `X_M_BOMLine.java` — EntityType guards, GodMode bypass
