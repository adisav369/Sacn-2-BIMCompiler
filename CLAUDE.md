# BIM Intent Compiler — Session Protocol

## PRIME RULE
**EXTRACT OR COMPILE ONLY.** Query the database. Copy patterns you find. Compute positions via verbs. Never invent.

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
- Changed verb/witness count → update MEMORY.md. Changed schema row counts → update SCHEMA_QUICKREF.md
- Obsolete topic file → delete + remove from MEMORY.md. New pattern → add to topic file or MEMORY.md
- Keep MEMORY.md under 80 lines, CLAUDE.md under 45 lines. Screenshots: `~/Pictures/Screenshots/`
- If PROGRESS.md > 80 lines, archive DONE items as single-line pointers to spec docs

## Standing Rules
- One bounded task per session
- Witnesses prove; SanityCheck is fallback
- All geometry is a maths issue — verify numerically
- New features: write witness claim FIRST, then implement
- **Anti-Drift Policy:** Read `docs/TestArchitecture.md` §Anti-Drift before adding BOMs, products, or geometry paths
- **Pre-Flight Citation:** Before code changes, cite the spec: `// Implementing BBC.md §X.Y — Witness: W-NAME`
- **Traceability:** Check `TestArchitecture.md` §Traceability Matrix before and after changes

## Sacred Files (edit with extreme care)
- `migration/*.sql` — append only, never modify existing migrations
- `BuildingCompiler.java` — main orchestrator, many dependencies
- `RosettaStoneGateTest.java` — defines G1-G6 gates, changes break CI
- `X_M_BOM.java` / `X_M_BOMLine.java` — EntityType guards, GodMode bypass
