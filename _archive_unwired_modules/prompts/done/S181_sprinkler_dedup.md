# ⚠ DO NOT REMOVE
# Scope: S181 — Sprinkler Geometry Dedup (analyse → replace → prune)
# Read the log after every run. No claims without §PROOF log lines.

## Context

The RTree stingy loader revealed that sprinkler meshes differ in quality across buildings.
Terminal building (`T0_Terminal`) uses JKR family sprinklers which render correctly at LOD400.
Other buildings use Revit/Autodesk generic families with lower-quality or mis-oriented geometry.

**Goal:** make all sprinkler `element_instances` rows reference the canonical Terminal hash
for each sub-type (pendent / upright), then prune orphaned rows from `base_geometries`.
Library.blend is rebuilt separately — do NOT touch it in this session.

## Phase 0: Analysis (MANDATORY — run first, verify output)

Run `scripts/S181_analyse_sprinklers.py` against every `*_extracted.db` in
`DAGCompiler/lib/input/`. Output: `scripts/S181_sprinkler_analysis.txt`.

The analysis must produce, for each DB:

```
=== <db_filename> ===
HASH               BUILDINGS  INSTANCES  TYPE     SAMPLE_NAME
bae71afd973eed3a   2          2636       pendent  Sprinkler - Pendent - Hosted:1/2" Pendent:...
0d509e532be0f5f2   1          565        pendent  jkrME18_spr_sprinkler head_pendent:...
bd5df7dd600f7582   2          72         pendent  Sprinkler - Pendent - Hosted:1/2" Pendent:...
f3b8de02e5e03caa   1          132        pendent  jkrME18_spr_sprinkler head_pendent:...
795e6eb5665d5b31   1          175        upright  jkrME18_spr_sprinkler head_upright:...
49f8fcde5a3bb02e   1          27         upright  jkrME18_spr_sprinkler head_upright:...
92605cd3f82bcf3d   1          292        pendent  M_Sprinkler - Pendent - Hosted:...
5d058cfe6e236b89   1          6          pendent  M_Sprinkler - Pendent - Hosted:...
389dd0da96979230   1          6          pendent  M_Sprinkler - Pendent - Hosted:...
...
CANONICAL_PENDENT: <hash of highest-instance Terminal pendent>
CANONICAL_UPRIGHT: <hash of highest-instance Terminal upright>
ORPHANS_AFTER_DEDUP: <count of hashes that would become unreferenced>
```

### Sub-type classification

Classify each hash as `pendent` or `upright` by inspecting `element_name` of any row
that uses that hash:

```python
def classify(element_name: str) -> str:
    name_lower = element_name.lower()
    if 'upright' in name_lower:
        return 'upright'
    return 'pendent'   # default: pendent, on_drop, sidewall all map here
```

### Canonical hash selection

For each sub-type, the canonical hash is the Terminal hash with the most instances:

```python
# pendent canonical = hash with most instances among Terminal pendent hashes
# upright canonical = hash with most instances among Terminal upright hashes
```

**Current values from sandbox analysis (verify against each real DB):**

| Sub-type | Canonical hash       | Instances | Source building |
|----------|----------------------|-----------|-----------------|
| pendent  | `0d509e532be0f5f2`   | 565       | T0_Terminal     |
| upright  | `795e6eb5665d5b31`   | 175       | T0_Terminal     |

Log: `§PROOF CANONICAL pendent=<hash> instances=N` and `§PROOF CANONICAL upright=<hash> instances=N`

**STOP here. Read `S181_sprinkler_analysis.txt`. Confirm canonical hashes before Phase 1.**

---

## Phase 1: Replacement

For each `*_extracted.db`, for each element whose current geometry_hash is a
**non-canonical sprinkler hash** of that sub-type:

```sql
-- Replace pendent sprinklers with canonical pendent hash
UPDATE element_instances
SET geometry_hash = :canonical_pendent
WHERE guid IN (
    SELECT m.guid
    FROM elements_meta m
    JOIN element_instances i ON m.guid = i.guid
    WHERE i.geometry_hash = :old_hash
)
AND :old_hash != :canonical_pendent;

-- Repeat for each deprecated pendent hash
-- Repeat the same pattern for upright hashes
```

**Constraints:**
- Never replace a `pendent` hash with `upright` or vice versa
- Never replace the canonical hash itself
- Skip any hash not in the sprinkler set (non-sprinkler elements not touched)
- Wrap each DB in a transaction — rollback on any error

Log per DB: `§PROOF REPLACE db=<name> subtype=<pendent|upright> old=<hash> instances_updated=N`
Log summary: `§PROOF DEDUP_DONE db=<name> total_replaced=N`

---

## Phase 2: Prune base_geometries

After replacement, any hash in `base_geometries` that is no longer referenced by
`element_instances` is an orphan. Remove it.

```sql
DELETE FROM base_geometries
WHERE geometry_hash NOT IN (
    SELECT DISTINCT geometry_hash FROM element_instances
    WHERE geometry_hash IS NOT NULL
);
```

Log: `§PROOF PRUNE db=<name> rows_deleted=N`

**Do NOT touch library.blend.** Orphaned meshes remain in library.blend until a
scheduled re-bake (separate session). They are harmless — just unused.

---

## Phase 3: Verify

For each DB, after all changes:

```sql
SELECT i.geometry_hash, COUNT(*) as n
FROM elements_meta m
JOIN element_instances i ON m.guid = i.guid
WHERE lower(m.element_name) LIKE '%sprinkler%'
   OR lower(m.ifc_class)    LIKE '%sprinkler%'
   OR lower(m.element_type) LIKE '%sprinkler%'
GROUP BY i.geometry_hash
ORDER BY n DESC;
```

Expected: only canonical pendent and canonical upright hashes remain.
Any other hash = FAIL.

Log: `§PROOF VERIFY db=<name> distinct_hashes=N` (expected N=2 or fewer per DB)

---

## Script layout

```
scripts/
  S181_analyse_sprinklers.py    # Phase 0 — read-only, outputs .txt
  S181_dedup_sprinklers.py      # Phase 1+2+3 — mutates DBs, requires --confirm flag
  S181_sprinkler_analysis.txt   # Phase 0 output (created by analysis script)
```

`S181_dedup_sprinklers.py` must require `--confirm` flag to run mutations.
Without `--confirm`, it prints what it WOULD do (dry-run) and exits 0.

```
python3 scripts/S181_dedup_sprinklers.py                  # dry-run
python3 scripts/S181_dedup_sprinklers.py --confirm        # mutates DBs
```

---

## Target DBs

Operate on all `DAGCompiler/lib/input/*_extracted.db` files.
Skip any DB that does not have `element_instances` + `base_geometries` tables.

---

## Standing rules

- Phase 0 (analysis) before ANY mutation
- Read the analysis .txt before starting Phase 1
- `--confirm` required for mutation — no auto-run
- Wrap each DB mutation in a transaction
- No geometry invention — only hash pointer updates
- Log every replacement with §PROOF lines
- DO NOT touch library.blend in this session
