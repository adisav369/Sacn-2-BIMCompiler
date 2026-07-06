# DONE
# BBC Spec Refresh — Fix 6 stale items from S89 audit

You are a docs-only session for bim-compiler. No code changes.

## Context

The S89 BBC code audit (prompts/done/31_bbc_code_audit.md) found 6 items
where the spec diverges from current code. All are spec-stale, not code bugs.

## Read first

1. `prompts/done/31_bbc_code_audit.md` — full audit findings (Traces 1-8 + Summary)
2. `docs/BOMBasedCompilation.md` — the spec to update

## Fixes (all in BBC.md unless noted)

### Fix 1: Leaf resolver DB (Trace 1)

BBC §2.2.1 says "resolves to M_Product in component_library.db". Code uses
ERP.db since S65 (Step 3 migration). Geometry (meshes, images) stays in
component_library.db.

Update: "resolves to M_Product in ERP.db (product catalog migrated S65;
geometry meshes remain in component_library.db via MeshBinder)"

### Fix 2: BomValidator check count (Trace 2)

BBC §2.1 says "9 BomValidator checks". Code has 12 checks + verb fidelity.

Update count to 12 (or "12+" to future-proof).

### Fix 3: Product write target (Trace 2)

BBC §2.1 says "products written to component_library.db first, then to BOM DB."
Code writes to component_library.db AND ERP.db. BOM DB copy is dead code.

Update to reflect dual write (CL + ERP.db) and note BOM DB copy is deprecated.

### Fix 4: Verb count (Trace 3)

BBC §6 says "64 verbs". VerbRegistry has 75 (post-S89-trim1).

Update to 75. Also check §5 pipeline section header if it mentions verb count.

### Fix 5: SPRAY verb (Trace 3)

SPRAY exists in VerbDetector but is not in BBC §2.1.6 verb table. Superseded
by CLUSTER in detection cascade.

Add a note to the factorization table: "SPRAY (legacy, superseded by CLUSTER)"
or remove if fully dead. Check `VerbDetector.java` to confirm SPRAY still exists.

### Fix 6: Python output_schema.sql divergence (Trace 8)

`simple_qto` schemas differ between Python and Java. Multiple tables/views
in Java are missing from Python DDL.

Add a note in BBC §10 (or wherever output schema is described):
"Note: `output_schema.sql` (Python) is stale — Java `BuildingWriter.initSchema()`
is the authoritative DDL. Python downstream tools may need schema updates."

### Bonus: MAX_DEPTH (Trace 1)

BBC §2.2 says recursion depth is "unlimited". Code has `MAX_DEPTH = 20`.
Add: "Recursion depth capped at 20 levels (safety guard; practical buildings
use 4-5 levels)."

## Verification

After all fixes:
```bash
# Check no stale counts remain
grep -n "64 verbs\|9 BomValidator\|9 checks" docs/BOMBasedCompilation.md
# Should return 0 hits

# mkdocs build clean
.venv/bin/mkdocs build 2>&1 | grep -i warning | head -5
```

## Rules

- Docs only — no Java, SQL, or test changes
- Surgical edits — fix the specific claim, don't rewrite paragraphs
- Keep BBC.md tone (confident, technical, qualified)

Commit: `[S##-bbc-refresh] Fix 6 stale BBC claims from S89 audit`

## When Done

Prepend `# DONE` + commit hash to this file's first line.

---

