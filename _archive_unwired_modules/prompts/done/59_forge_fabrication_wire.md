# DONE 48d5f4f9
# Forge Fabrication Wiring — Cut List Data to Work Order Output

**Spec:** `docs/FORGE_SUITE_SRS.md` §9 Part ⑤
**Priority:** Phase 2 — smallest effort, highest immediate value

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Follow existing output.db patterns. No new
infrastructure — this is column additions and a new companion table.

## Read first

1. `docs/FORGE_SUITE_SRS.md` §9 Part ⑤ — what gets emitted
2. `docs/DATA_MODEL.md` — output.db schema, existing element output tables
3. `BIM_COBOL/src/main/java/com/bim/cobol/forge/ForgeResult.java` — what the engine produces
4. `BIM_COBOL/src/main/java/com/bim/cobol/forge/GeometryRecord.java` — fabrication map
5. Existing output patterns: how elements currently write to output.db

## Task

### A. Create `ad_forge_fabrication` table in output.db

```sql
CREATE TABLE ad_forge_fabrication (
    AD_Forge_Fabrication_ID  INTEGER PRIMARY KEY AUTOINCREMENT,
    Element_ID               TEXT NOT NULL,      -- element_ref GUID, traces to compiled element
    PieceType                TEXT NOT NULL,      -- SLOPE_CUT, STAIR_FLIGHT, etc.
    ParamName                TEXT NOT NULL,      -- cut_angle_top, step_count, etc.
    ParamValue               REAL NOT NULL,      -- numeric value
    Unit                     TEXT,               -- degrees, mm, count
    FOREIGN KEY (Element_ID) REFERENCES ... -- match existing FK pattern
);
```

### B. Write ForgeResult fabrication data to output.db

After ForgeEngine.compute() returns a ForgeResult, each GeometryRecord's
fabrication map entries become rows in `ad_forge_fabrication`.

Example: A SLOPE_CUT with `{cut_angle_top: 60, cut_angle_bottom: 30, birdsmouth_depth: 14.9}`
becomes 3 rows.

### C. Wire to existing work order output path

The work order PDF generation already reads from output.db. Add a section
that queries `ad_forge_fabrication` and formats as a cut list table.

## What NOT to do

- Do NOT modify ForgeEngine or ForgeResult
- Do NOT modify existing output.db tables — add a new companion table
- Do NOT add external dependencies
- Do NOT implement the full work order PDF rendering (just the data path)

## Verify

1. `mvn compile -q` — PASS
2. Forge a SLOPE_CUT → check ad_forge_fabrication has 4 rows
3. SH 7/7 no regression

## Commit message

```
[S##-forge] Wire forge fabrication data to output.db

ad_forge_fabrication companion table in output.db. ForgeResult fabrication
map → per-param rows. Cut list data path for work order output.
```
