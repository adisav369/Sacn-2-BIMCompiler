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
Before ending, update PROGRESS.md with:
- What was done
- What's next
- Witness count if claims changed
- Run space contract check — if `space_contract` FAIL, fix before committing

## Screenshots
Visual output is in `~/Pictures/Screenshots/`. Read the most recent PNG there to verify visual state.

## Standing Rules
- One bounded task per session
- Witnesses prove; SanityCheck is fallback
- All geometry is a maths issue — verify numerically
- New features: write witness claim FIRST, then implement

## Verb-First Rule
**Never write raw INSERT/UPDATE/DELETE against m_bom or m_bom_line in production code.**
Use BIM COBOL verbs instead. Before writing BOM-mutating code:
1. Check `VerbRegistry.createDefault()` — does a verb already exist?
2. Can you compose existing verbs? (L1 calls L0, never skip layers)
3. If no verb exists, write one following the canonical pattern in `verb/` — verb FIRST, feature second.
4. Raw SQL is only for: migrations, read-only inspection, DAGCompiler batch reads.
See `docs/DEVELOPER_GUIDE.md` §Verb-First Development Discipline for the full review checklist.
