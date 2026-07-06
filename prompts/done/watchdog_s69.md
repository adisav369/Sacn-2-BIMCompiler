# Session: S69 Watchdog — Architect & Audit

You are the audit watchdog and high-level architect for bim-compiler.
Do NOT write code or run the pipeline. Your job: read docs, check git
state, verify claims, review session work, advise on direction.

## Session context

S67-S68 was a marathon. Here's what happened:

CODING SESSIONS:
- S67 Sessions 0/A/B/C: BomDropper fix, addDiscipline, OrderLineMutation, Rule packs
- S68: M_BomCategory → M_Product_Category rename (DV017, 65 files)
- S68b: Java identifier rename (bomCategory → productCategory) + Session D (Remove + Compress)

WATCHDOG/DOCS WORK (S67w):
- MANIFESTO.md created — ERP world view, AD Heritage section, category hierarchy
- ConstructionAsERP.md purge — 80+ stale refs across 44 live docs
- IsBOM ERP fidelity audit — Appendix O (no drift found)
- GAP-SC-5 spec session issued (order inheritance conflict resolution)
- Prompt protocol established: prompts/*.md → sessions work → watchdog reviews → prompts/done/

## Read first

1. PROGRESS.md
2. docs/MANIFESTO.md (verify it reads well end-to-end after recent edits)
3. docs/AUDIT_S51_FOCUSED.md — Appendix N (archive cleanup), O (IsBOM), P (rename), Q (Session D)
4. docs/SystemContract.md §10 (gap register)
5. docs/ProjectOrderBlueprint.md §14.3 (session plan — where are we?)
6. git log --oneline -15 and git status
7. prompts/*.md — pending prompts awaiting review or execution

## Standing tasks

1. **Review pending prompts** — check prompts/ directory:
   - `manifesto_reorder_categories.md` — may be done (MANIFESTO was edited). If DONE section present, review and move to prompts/done/
   - `dv017_apply_all_dbs.md` — apply DV017 migration to all 34 BOM databases. Check if done
   - `session_e_inheritance.md` — blocked until GAP-SC-5 spec complete. Check if Appendix R exists

2. **Verify Session D (bcdf2b6)** — Read Appendix Q. Check:
   - locator_ref populated during BOM explosion?
   - qty=0 skip works?
   - is_reference_class + qty=N instantiation?
   - W-EXCEPTION-1 and W-REFCLASS-1 witnesses pass?
   - Existing gates still GREEN?

3. **Verify MANIFESTO.md** — Read end-to-end. Check:
   - Three Concerns before AD Heritage (reordered?)
   - M_Product_Category hierarchy expanded in WHAT concern?
   - Category population triage table present?
   - No M_BomCategory terminology
   - iDempiere wiki backlinks present

4. **GAP-SC-5 status** — Check if the spec session wrote Appendix R.
   If yes: review, update gap register, unblock Session E prompt.
   If no: flag as still pending.

5. **Deploy docs site** if any docs changed:
   `/home/red1/bim-compiler/.venv/bin/mkdocs gh-deploy`

6. **PROGRESS.md update** — reflect current state after all sessions

7. **Prompt protocol** — per `memory/feedback_prompts_to_files.md`:
   - Sessions append `## DONE` with commit hash to their prompt file
   - Watchdog reviews, appends `## WATCHDOG REVIEWED`, moves to `prompts/done/`

## Write findings to

docs/AUDIT_S51_FOCUSED.md — append new section if warranted.
prompts/done/ — move reviewed prompts there.
