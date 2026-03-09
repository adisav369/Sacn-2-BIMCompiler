# BIM Intent Compiler — Session Protocol

## PRIME RULE
**EXTRACT OR COMPILE ONLY.** Query the database. Copy patterns you find. Compute positions via verbs. Never invent.

## Session Startup
1. Read this file
2. Read PROGRESS.md (current state, what's next)
3. Read BIMConstants.java if working with thresholds
4. Read the Java interface of whatever you're modifying
5. Run witnesses to verify current state if unsure

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

## Sacred Files (edit with extreme care)
- `migration/*.sql` — append only, never modify existing migrations
- `BuildingCompiler.java` — main orchestrator, many dependencies
- `RosettaStoneGateTest.java` — defines G1-G6 gates, changes break CI
- `X_M_BOM.java` / `X_M_BOMLine.java` — EntityType guards, GodMode bypass
