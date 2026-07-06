# DONE — Propagate corrected ERD model from MANIFESTO to all specs
> Commit: ca5a116 [S71]

You are a docs architect for bim-compiler. Docs only — NO code changes.

Read first:
1. docs/MANIFESTO.md — this is the SINGLE SOURCE OF TRUTH. The ERD, category
   cascade, orthogonal dimensions, C_DocType = ONE, M_AttributeSet pattern —
   all corrected in S70. Every other doc must align to this.
2. docs/DATA_MODEL.md §7.2 — the correction list from S70 analysis
3. PROGRESS.md

## THE RULE

MANIFESTO.md is correct. If a spec disagrees with MANIFESTO, the spec is wrong.
Fix the spec to match MANIFESTO. Do NOT change MANIFESTO.

## CORRECTION LIST (from DATA_MODEL.md §7.2)

| Doc | What to fix |
|-----|------------|
| **BOMBasedCompilation.md** | L44-55: C_DocType.DocBaseType analogy. L297: "DocBaseType on M_Product_Category". L1149-1186: YAML schema refs |
| **SystemContract.md** | L63: C_DocType = "Building type classification" → "Construction Order". L434: "per DocBaseType + DocSubType" |
| **DATA_MODEL.md** | §1.4: doc_base_type/doc_sub_type on m_bom description. §2 C_DocType/m_bom schema description |
| **TerminalAnalysis.md** | L661-697: entire "DocBaseType — Real Semantic Work" section. M_Product_Category scoped by doc_type column |
| **SourceCodeGuide.md** | L292: "DocBaseType=CO" skip logic. L589: C_DocType identity definition |

ProjectOrderBlueprint.md, DocAction_SRS.md, DISC_VALIDATION_DB_SRS.md are CLEAN — no changes needed.

## HOW TO FIX EACH DOC

For each doc in the list above:

1. **Read the full doc** (not just the flagged lines — there may be unflagged refs)
2. **Grep for:** `DocBaseType`, `doc_base_type`, `DocSubType`, `doc_sub_type`,
   `docBaseType`, `docSubType` — catch every reference
3. **For each reference, decide:**
   - If it describes ROUTING logic → rewrite to use M_Product_Category
   - If it describes SCHEMA (what the column IS) → keep but mark as deprecated
     artifact: "doc_base_type exists on m_bom but is redundant with m_product_category_id
     (see DATA_MODEL.md §7 migration plan)"
   - If it describes C_DocType as multiple types → fix: ONE C_DocType = "Construction Order"
4. **Also check for:**
   - Discipline (ARC/STR/FP) listed as M_Product_Category → fix: discipline = AD_Org
   - Any text implying M_Product_Category routes by building type → fix: category
     cascade is RE→floor→room→leaf, classification at every level
5. **Preserve meaning.** The fix is terminology and model alignment, not content
   removal. If a section explains how BOM selection works, keep the explanation
   but use M_Product_Category language instead of DocBaseType.

## ALSO CHECK (beyond §7.2 list)

Grep ALL docs for DocBaseType/DocSubType — the §7.2 list may have missed some:

```bash
grep -rn "DocBaseType\|DocSubType\|doc_base_type\|doc_sub_type" docs/ --include="*.md" | grep -v "DATA_MODEL.md" | grep -v "MANIFESTO.md"
```

Fix any additional hits found.

## CONSTRAINTS

- Docs only — NO Java, NO SQL, NO migration files
- Do NOT change MANIFESTO.md
- Do NOT change DATA_MODEL.md §7 (it's the correction anchor — it documents
  what EXISTS, including the deprecated columns)
- Gate: docs must still render cleanly with mkdocs
- Pre-flight: each fix should note which MANIFESTO section it aligns to

## When Done

Prepend `# DONE` + commit hash to this file's first line before committing.
Commit with `[S71] Propagate MANIFESTO ERD to specs: DocBaseType → M_Product_Category across 5 docs`.
Deploy docs: `/home/red1/bim-compiler/.venv/bin/mkdocs gh-deploy`

## WATCHDOG REVIEWED
**S69 Watchdog** — 2026-03-25

Verified against commit ca5a116:
1. **19 docs corrected** (exceeded the 5-doc spec — session grepped and found 14 more). All priority docs from §7.2 covered. — PASS
2. **Remaining refs:** A few DocBaseType mentions survive in INFRA_DESIGNER_SRS (YAML examples — marked deprecated), InfrastructureAnalysis (correction notes referencing S71), TerminalAnalysis (deprecation note), TestArchitecture (code snippets — these describe CURRENT code, not target). Acceptable — these are schema descriptions or historical notes, not routing claims.
3. **Compile:** No code changes, docs only — N/A
4. **MANIFESTO unchanged** — confirmed — PASS
5. **DATA_MODEL.md §7 unchanged** — confirmed — PASS

Session did not deploy docs or mark prompt DONE. Watchdog handled.
