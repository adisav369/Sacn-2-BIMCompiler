# DONE — Fix output.db DDL drift from S84
> Commit: b2648e03 [S86-ddl-fix]

Fix drift from S84 (doc_base_type drop). The column was dropped from m_bom
in BOM.db but WorkOutputDAO still creates it in output.db DDL.

## Tasks

1. **WorkOutputDAO.java:1131** — remove `doc_base_type TEXT NOT NULL` from the
   DDL that creates tables in output.db. If this column is referenced in any
   INSERT or SELECT in WorkOutputDAO, remove those too.

2. **OutputTemplateGenerator** — if it generates output_template.db schema,
   check for doc_base_type there too and remove.

3. **Verify no output.db consumer reads doc_base_type:**
   `grep -rn "doc_base_type" --include='*.java' | grep -v "YAML\|yaml\|classify\|Yaml\|NewBuilding\|Classification\|//\|@Deprecated"`
   After fix, only YAML-related refs should remain.

4. **YAML fields (analysis only, no changes):**
   - ClassificationYaml.java reads `doc_base_type` from YAML
   - NewBuildingGenerator.java writes `doc_base_type` to YAML
   - These are YAML config fields, not DB columns. Report whether they're
     still consumed downstream or dead. Do NOT change them in this session.

5. `mvn compile -q` PASS
6. `mvn test-compile -q` PASS

Commit separately from any prior uncommitted work.
Commit message prefix: [S86-ddl-fix].
