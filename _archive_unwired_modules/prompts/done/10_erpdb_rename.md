# DONE cad9e46
# Rename disc_validation.db → ERP.db

You are a coder for bim-compiler. Rename + grep-replace.

Read first:
1. docs/MANIFESTO.md
2. docs/DATA_MODEL.md §6 (ERP.db proposal) + §7.5 (touchpoint estimate: 40-60)
3. PROGRESS.md

## Prerequisites

- Prompt 09 DONE — category hierarchy and AD tables consolidated in ERP.db

## Task

Rename ERP.db → ERP.db. Single clean pass.

1. `cp library/ERP.db library/ERP.db`

2. Grep ALL references:
```bash
grep -rn "ERP" --include="*.java" --include="*.sh" --include="*.py" --include="*.sql" --include="*.md" --include="*.yaml" --include="*.properties" .
```

3. Replace every reference: `ERP` → `ERP` (or `erp` for file paths as appropriate)

4. Update Java system properties, connection strings, shell scripts, Python scripts, docs

5. `mvn compile -q` at the end only

6. If green, remove ERP.db (or leave as symlink temporarily)

## Constraints

- Do NOT run tests — compile check only
- Do NOT change MANIFESTO.md (already uses correct terminology)
- Append-only: no migration needed — this is a file rename + reference update

## When Done

Prepend `# DONE` + commit hash to this file's first line before committing.
Commit with `[S76] Rename ERP.db → ERP.db`.
