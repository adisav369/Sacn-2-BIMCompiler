# DONE
# BOM SQL Consolidation — Single Write Path for m_bom + m_bom_line

**Priority:** 18 hand-written INSERT statements (11 m_bom_line, 7 m_bom) spread
across 6 files. Adding a column (like AD_Org_ID in P92) requires touching 4+ files.
This will drift. Consolidate into a single shared writer.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** This is a pure refactor — zero behaviour change.
Every BOM.db produced after must be byte-identical to before (same rows, same values).

## Read first

1. `PROGRESS.md` §Current State
2. `docs/BOMBasedCompilation.md` §2.1 (IFCtoBOM pipeline), §4 (tack convention)
3. `docs/SourceCodeGuide.md` §Java — IFCtoBOM (class table)
4. `library/schema_snapshot_bom.sql` — canonical column list for m_bom + m_bom_line
5. All 6 files with INSERT statements (scan the private static helpers):
   - `IFCtoBOM/src/main/java/com/bim/ifctobom/VerbFactorizer.java` — `insertLeafLine()` (the most complete, 24+ bind params)
   - `IFCtoBOM/src/main/java/com/bim/ifctobom/DisciplineBomBuilder.java` — `insertBomHeader()`, `insertBomLine()`
   - `IFCtoBOM/src/main/java/com/bim/ifctobom/StructuralBomBuilder.java` — `insertBomHeader()`, `insertBomLine()` (2 overloads)
   - `IFCtoBOM/src/main/java/com/bim/ifctobom/ScopeBomBuilder.java` — `insertSetBomHeader()`, `insertPhantomLine()`, `insertLeafLine()`
   - `IFCtoBOM/src/main/java/com/bim/ifctobom/CompositionBomBuilder.java` — `insertBomHeader()`, `insertLeafLine()`, `insertPairChild()`
   - `IFCtoBOM/src/main/java/com/bim/ifctobom/FloorRoomBomBuilder.java` — `insertBomHeader()`, `insertEmptyBomHeader()`, `insertSpaceLine()`, `insertBuildingChild()`, `insertStaticChild()`
6. `IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java` — DDL (m_bom + m_bom_line CREATE TABLE)

## Step 0: Write spec FIRST — before any code

**No code until the spec is committed.** Add the following to existing docs:

### 0a. BBC.md §2.1 — add §2.1.9 "BOM Write Path"

After §2.1.8 "What IFCtoBOM Does NOT Do", add a new subsection documenting the
single-writer pattern. Content must cover:

- **Problem:** 18 INSERT statements across 6 builder files, each with a different
  column subset. DDL changes require N-file edits. P92 (AD_Org_ID) proved the drift.
- **Solution:** `BomWriter` static utility — one INSERT per table, builder-pattern
  rows (`BomRow`, `BomLineRow`) carry all columns with defaults.
- **Invariant:** Every m_bom and m_bom_line write in IFCtoBOM goes through BomWriter.
  No builder owns raw SQL for these tables.
- **Column truth:** `BomRow` mirrors m_bom DDL in `IFCtoBOMPipeline.java`.
  `BomLineRow` mirrors m_bom_line DDL. Adding a column = add to DDL + add to row record
  + add to BomWriter bind list. One place, not six.

### 0b. SourceCodeGuide.md §Java — IFCtoBOM — add BomWriter row

Add `BomWriter` to the class table:
```
| `BomWriter` | Single write path for m_bom + m_bom_line (all builders delegate here) |
```

### 0c. Commit the spec

```bash
git add docs/BOMBasedCompilation.md docs/SourceCodeGuide.md
git commit -m "[S100-p94] Spec: BomWriter single write path (BBC §2.1.9)"
```

Only after this commit passes do you proceed to Step 1.

## The problem

Each builder has its own private `insertBomHeader()` and `insertBomLine()` / `insertLeafLine()`.
They all write the same two tables but with different subsets of columns. When DDL changes
(new column, rename, type change), every INSERT must be found and updated manually.

Current INSERT surface:
| File | m_bom INSERTs | m_bom_line INSERTs |
|------|---------------|--------------------|
| VerbFactorizer | 0 | 2 (insertLeafLine + insertMaRows) |
| DisciplineBomBuilder | 1 | 1 |
| StructuralBomBuilder | 1 | 1 (2 overloads) |
| ScopeBomBuilder | 2 | 2 |
| CompositionBomBuilder | 1 | 2 |
| FloorRoomBomBuilder | 2 | 3 |
| **Total** | **7** | **11** |

## The fix

### Step 1: Create `BomWriter.java` utility class

New file: `IFCtoBOM/src/main/java/com/bim/ifctobom/BomWriter.java`

A static utility (same pattern as `ProductRegistrar`) with two core methods:

```java
public class BomWriter {

    /** Insert or replace an m_bom header. All columns explicit. */
    public static void insertBom(Connection conn, BomRow row) throws SQLException { ... }

    /** Insert an m_bom_line. All columns explicit. */
    public static void insertBomLine(Connection conn, BomLineRow row) throws SQLException { ... }
}
```

Use **builder-pattern records** (or a plain record with defaults) so callers only set
the columns they need:

```java
public record BomRow(
    String bomId, String value, String bomName, String bomType,
    String groupBy, int productCategoryId, String entityType,
    double originX, double originY, double originZ,
    int aabbW, int aabbD, int aabbH, String aabbQualifier,
    boolean isActive
) {
    // Builder or static factory with sensible defaults
}
```

Similarly for `BomLineRow` — mirror the full m_bom_line column set from
`schema_snapshot_bom.sql`. Every column present, defaults for optional ones.

### Step 2: Migrate each builder

For each of the 6 files, replace private `insertBomHeader()` / `insertBomLine()`
with calls to `BomWriter.insertBom()` / `BomWriter.insertBomLine()`.

**Order:** Start with the simplest (FloorRoomBomBuilder), then StructuralBomBuilder,
DisciplineBomBuilder, ScopeBomBuilder, CompositionBomBuilder, VerbFactorizer last
(most complex — 24 bind params).

After each file: `mvn compile -q` must PASS. Don't batch.

### Step 3: Delete dead private methods

Once all callers migrated, each builder's private insert helpers should be gone.
Grep to confirm zero remaining `INSERT INTO m_bom` outside BomWriter.

### Step 4: Verify

```bash
rm library/SH_BOM.db library/FK_BOM.db
./scripts/run_RosettaStones.sh classify_sh.yaml   # SH 7/7 (no regression)
./scripts/run_RosettaStones.sh classify_fk.yaml   # FK 7/7 (no regression)
```

Then diff the BOM content:
```sql
-- Before and after should match exactly
sqlite3 library/SH_BOM.db "SELECT count(*) FROM m_bom; SELECT count(*) FROM m_bom_line;"
```

## What NOT to do

- Do NOT change the builder dispatch pattern (no interfaces, no inheritance)
- Do NOT change column names, types, or defaults — pure refactor
- Do NOT touch IFCtoBOMPipeline DDL (CREATE TABLE stays where it is)
- Do NOT modify BomDropper or anything in DAGCompiler — this is IFCtoBOM only
- Do NOT rename the builder classes — they stay as-is (each builds a different BOM shape)
- Do NOT add new columns or features — zero functional change
- Do NOT modify existing migration SQL files (sacred, append only)

## Design constraints

- `BomWriter` is a **static utility**, not a base class. No inheritance.
- The SQL string lives in ONE place per table. Column list = single source of truth.
- `BomRow` / `BomLineRow` carry all columns with defaults. Builders construct them
  with only the fields they care about (builder pattern or `with` methods).
- `insertMaRows` / `insertMaRow` in VerbFactorizer writes m_bom_line_ma, not m_bom_line.
  Leave it as-is unless it's trivially similar.
- PreparedStatement column binding order is internal to BomWriter — callers never see SQL.

## When Done

Prepend `# DONE` to this file's first line.

Append findings:
- Spec commit hash (Step 0)
- How many INSERT statements consolidated (before → after)
- BomRow / BomLineRow field count
- Which builders migrated (all 6 or subset)
- Compile result
- Gate results (SH, FK)
- BOM row counts before/after (must match)

## Findings

- **Spec commit:** c21e5ea7 (BBC §2.1.9 + SourceCodeGuide BomWriter row)
- **Code commit:** 92d016b4 (7 files changed, +369 -493 lines)
- **INSERT statements consolidated:** 18 → 2 (1 m_bom, 1 m_bom_line in BomWriter). m_bom_line_ma left in VerbFactorizer (different table).
- **BomRow fields:** 14 (bomId, bomName, bomType, groupBy, entityType, docSubType, productCategoryId, originX/Y/Z, aabbW/D/H, aabbQualifier, isActive, orIgnore)
- **BomLineRow fields:** 24 (bomId, childProductId, componentType, role, sequence, rotationRule, fitPriority, minSpaceMm, dx/dy/dz, isActive, entityType, qty, verbRef, allocW/D/H, storey, elementRef, ordinal, orientation, materialName, materialRgba, shapeArchetype, scaleBand, hostElementRef, adOrgId)
- **Builders migrated:** All 6 — FloorRoomBomBuilder (5 methods), StructuralBomBuilder (2), DisciplineBomBuilder (2), ScopeBomBuilder (4), CompositionBomBuilder (3), VerbFactorizer (1)
- **Compile:** `mvn compile -q` PASS after each builder migration (6/6)
- **Gate results:** SH 7/7 PASS, FK 7/7 PASS
- **BOM row counts (before/after):** SH m_bom 11/11, SH m_bom_line 39/39, FK m_bom 12/12, FK m_bom_line 99/99 — identical
- **Note:** First SH run hit SQLITE_BUSY (known zombie process issue from p93). Retry after killing stale process succeeded.
