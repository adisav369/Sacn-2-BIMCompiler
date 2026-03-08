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
