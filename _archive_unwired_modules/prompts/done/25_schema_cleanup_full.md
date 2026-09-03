# DONE — Schema cleanup: doc_base_type dropped, doc_sub_type stays (structural)
> Commit: 580e6b0d [S84-cleanup]

Full schema cleanup session. Use agents to parallelize. One commit at the end.

Reference: prompts/23 (pre-flight findings), prompts/24 (doc_sub_type is STRUCTURAL),
SpecsAnalysis.txt §10, ACTION_ROADMAP.md Schema Migration Backlog.

## Key findings from prior prompts

- doc_sub_type on m_bom = STRUCTURAL (variant scoping SH/DX/FK). Do NOT drop.
- doc_base_type on m_bom = write-only (INSERTs only, zero reads). SAFE to drop.
- DocBaseType/DocSubType on C_DocType = fully active model. Do NOT touch.
- bom_category on m_bom = needs verification (YAML field vs column).

## Tasks (run in parallel where independent)

### Task 1: Pre-flight verification
- Verify doc_base_type is write-only: `grep -rn "doc_base_type" src/main/java/ --include='*.java' | grep -v INSERT | grep -v @Deprecated | grep -v "//"`
- Verify bom_category INSERT path: `grep -rn "bom_category" src/main/java/ --include='*.java' | grep -i "insert\|ps\.set"`
- Check if bom_category column exists on m_bom in SH_BOM.db: `sqlite3 library/SH_BOM.db "PRAGMA table_info(m_bom)" | grep bom_category`
- Report findings before proceeding.

### Task 2: Drop doc_base_type from m_bom (if Task 1 confirms write-only)
- W012 migration: SQLite rename-copy-drop to remove doc_base_type from m_bom
- Remove doc_base_type from INSERT in StructuralBomBuilder.java (and any other builder)
- Remove @Deprecated getDocBaseType/setDocBaseType from X_M_BOM.java (Sacred File — only touch these accessors)
- Apply migration to SH_BOM.db as proof

### Task 3: Drop bom_category from m_bom (if Task 1 confirms no active writes)
- W013 migration: SQLite rename-copy-drop to remove bom_category from m_bom
- Remove bom_category from any INSERT that writes it
- Remove @Deprecated getBomCategory/setBomCategory from X_M_BOM.java
- Apply migration to SH_BOM.db as proof

### Task 4: Fix @Deprecated annotations (independent of drops)
- MBOM.java: REMOVE @Deprecated from getBuildingBom(), getByDocSubType(),
  findByCategory() — these use doc_sub_type which is STRUCTURAL
- X_M_BOM.java: REMOVE @Deprecated from getDocSubType/setDocSubType — column is staying
- Any other file with @Deprecated on doc_sub_type methods — fix them

### Task 5: Drop vestigial doc_base_type INSERT in DisciplineBomBuilder
- Prompt 23 found DisciplineBomBuilder:207 writes doc_base_type. Remove from INSERT.

### Task 6: Update docs and specs
- SpecsAnalysis.txt §10: add note to step 6 — doc_sub_type is STRUCTURAL
  (variant scoping SH/DX/FK), only doc_base_type dropped
- ACTION_ROADMAP.md Schema Migration Backlog step 6: update to "Drop doc_base_type
  (DONE). doc_sub_type is structural — stays."
- PROGRESS.md: add S84 session log entry
- Update SCHEMA_QUICKREF if m_bom columns changed

### Task 7: Verification (after all tasks)
1. `mvn compile -q` PASS
2. `mvn test-compile -q` PASS
3. `./scripts/run_RosettaStones.sh classify_sh.yaml` — SH ALL GREEN
4. `grep -rn "doc_base_type" src/main/java/ --include='*.java'` — expect zero non-comment refs
5. `grep -rn "@Deprecated" src/main/java/ --include='*.java' | grep -i "doc_sub_type"` — expect zero

## Rules
- If Task 1 pre-flight fails for a column, skip that column's drop task and report why
- X_M_BOM.java is Sacred — only remove the specific @Deprecated accessors for dropped columns
- Do NOT touch C_DocType or its Java classes
- Do NOT drop doc_sub_type from anywhere
- Remaining 33 BOM DBs get the migration via re-extract (run_RosettaStones.sh), not manual apply

Commit message prefix: [S84-cleanup].

## Watchdog Review (S84, commit 580e6b0)

## Watchdog review (2026-03-26)
- W012 migration landed. doc_base_type dropped from m_bom. 14 files changed.
- @Deprecated removed from MBOM doc_sub_type methods (structural, staying).
- Seal bumped. Tests pass.

## Remaining drift risk (next session)
1. **WorkOutputDAO.java:1131** — DDL still creates `doc_base_type` column in output.db. BUG.
2. **ClassificationYaml.java:230** — reads `doc_base_type` from YAML. YAML field, not column.
3. **NewBuildingGenerator.java:146** — writes `doc_base_type` to YAML. Same.
4. YAML `bom_category` field (GF/RF/L1) is storey codes, NOT the dropped m_bom column. Different concept, confusing name.
