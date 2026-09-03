# DONE 867ed058
# Doc Debt DD-1 — List All 75 Verbs in BIM_COBOL.md Scoreboard

**Priority:** 19 verbs registered in VerbRegistry but unlisted in the spec
scoreboard (§2.4). Verb dispatch is now live — the scoreboard must match.

You are a coder for bim-compiler. One bounded task — docs only.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Read VerbRegistry.createDefault(). Count verbs.
Cross-reference with BIM_COBOL.md §2.4 scoreboard. Add missing entries.

## Read first

1. `BIM_COBOL/src/main/java/com/bim/cobol/VerbRegistry.java` — `createDefault()`
   lists all 75 registered verbs with their class names.
2. `docs/BIM_COBOL.md` §2.4 — the verb scoreboard table. Note which verbs
   are listed and which are missing.
3. For each missing verb, read its `keyword()` from the verb class in
   `BIM_COBOL/src/main/java/com/bim/cobol/verb/`.

## Task: Update scoreboard

- Add every missing verb to the scoreboard table in BIM_COBOL.md §2.4
- Use the same column format as existing entries
- Group by phase/category (match the existing grouping)
- Verify final count matches VerbRegistry.size() (75)

### What NOT to do

- Do NOT change any Java code
- Do NOT change VerbRegistry or any verb implementation
- Do NOT rewrite existing scoreboard entries — only add missing ones

## Verify

1. Count of verbs in scoreboard = 75
2. `mvn compile -q` — PASS (no code changes, but confirm)
3. mkdocs build if available — no broken links

## When Done

Prepend `# DONE` + commit hash to this file's first line.
