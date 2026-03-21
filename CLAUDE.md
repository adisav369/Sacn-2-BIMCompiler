# BIM Intent Compiler — Session Protocol

## PRIME RULE
**EXTRACT OR COMPILE ONLY.** Query the database. Copy patterns you find. Compute positions via verbs. Never invent.

## Session Startup
1. Read this file
2. Read PROGRESS.md §Current State (gate table, what's next)
3. Read `docs/YAMLGuide.md` §Invention Boundary + §Step 5-6 (pipeline flow)
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
- If verb/witness count changed → update MEMORY.md canonical line
- If SCHEMA_QUICKREF.md row counts changed (migration ran) → update counts
- If a topic file is now obsolete → delete it, remove from MEMORY.md index
- If a new stable pattern emerged → add to appropriate topic file or MEMORY.md
- Keep MEMORY.md under 80 lines, CLAUDE.md under 45 lines

## Screenshots
Visual output is in `~/Pictures/Screenshots/`. Read the most recent PNG there to verify visual state.

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
