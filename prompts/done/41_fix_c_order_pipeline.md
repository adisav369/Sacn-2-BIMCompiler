# DONE
# Fix C_Order Pipeline — The #1 Credibility Gap

You are a coder for bim-compiler. Highest priority fix in the project.

## Why this matters

The entire thesis is "a building is a C_Order." But `SELECT COUNT(*) FROM
c_order` returns 0 in every output.db. A skeptic dismisses the project in
5 seconds. This is the single deliverable that validates or invalidates
the iDempiere mapping.

## Root cause (from S92 Appendix V.5 + Phase E findings)

`CompilationPipeline.copyCOrderToOutput()` (:576) routes through
`ServiceLoader.load(VerbExecutor.class)` to dispatch a `REGISTER BUILDING`
verb. But BIM_COBOL is not on DAGCompiler's classpath during compilation
(by design — SPI pattern). `ServiceLoader.findFirst()` returns null.
Result: the verb never fires, c_order stays empty.

C_OrderLine works (37 rows for SH) because `copyCOrderLineToOutput()` (:623)
uses direct SQL, not SPI.

## Read first

1. `PROGRESS.md`
2. `DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java` lines 570-616
3. `BIM_COBOL/src/main/java/com/bim/cobol/verb/RegisterBuildingVerb.java` — the verb that should fire
4. `DAGCompiler/src/main/java/com/bim/compiler/bom/BomDropper.java` lines 156+ — `createOrder()` in compile DB

## The fix

**Move the c_order INSERT from BIM_COBOL SPI to direct SQL in DAGCompiler.**
Same pattern as `copyCOrderLineToOutput()` which already works.

### Option A (recommended): Direct SQL in copyCOrderToOutput()

Replace the SPI dispatch (lines 594-615) with a direct INSERT into output.db's
c_order table. The data is already available in `CompilationContext`:

```java
private static void copyCOrderToOutput(Connection outConn, CompilationContext ctx) {
    String buildingId = ctx.buildingId();
    var entry = ctx.entry();

    try (PreparedStatement ps = outConn.prepareStatement(
            "INSERT OR IGNORE INTO c_order (C_Order_ID, Value, Name, " +
            "DocStatus, C_DocType_ID, doc_sub_type, provenance, " +
            "expected_elements, aabb_width_mm, aabb_depth_mm, aabb_height_mm) " +
            "VALUES (1, ?, ?, 'IP', ?, ?, ?, ?, ?, ?, ?)")) {
        ps.setString(1, buildingId);           // Value = SearchKey
        ps.setString(2, entry.name());         // Name
        ps.setString(3, entry.docTypeId());    // C_DocType_ID
        ps.setString(4, entry.docSubType());   // doc_sub_type
        ps.setString(5, entry.provenance());   // provenance
        ps.setInt(6, entry.expectedElements());
        ps.setDouble(7, entry.aabbWidthMm());
        ps.setDouble(8, entry.aabbDepthMm());
        ps.setDouble(9, entry.aabbHeightMm());
        ps.executeUpdate();
    }
}
```

This is NOT violating the verb layer principle — the verb `REGISTER BUILDING`
was a workaround for putting SQL behind SPI. The data comes from
`BuildingEntry` which is already in DAGCompiler. C_OrderLine already uses
direct SQL in the same class.

### Option B (if T16 tamper rule blocks): Move INSERT to ORMSandbox

Create `OrderWriter.java` in ORMSandbox (which DAGCompiler depends on).
DAGCompiler calls `OrderWriter.writeOrder(outConn, entry)`. Same SQL,
different package. This satisfies T16 if it requires ORM-layer writes.

## Tasks

### Task 1: Fix copyCOrderToOutput()

Replace SPI dispatch with direct INSERT. Keep the SPI path as a fallback
comment for when BIM_COBOL is on the classpath (interactive Designer mode).

### Task 2: Verify c_order populated for SH

```bash
./scripts/run_RosettaStones.sh classify_sh.yaml
sqlite3 output/SH_output.db "SELECT * FROM c_order"
```

Must return 1 row with buildingId, DocStatus='IP', correct AABB.

### Task 3: Verify c_order populated for all 35 buildings

Run full Rosetta Stone suite (or at least SH + DX + TE + FK + DM):
```bash
for db in output/*_output.db; do
    echo "=== $(basename $db) ==="
    sqlite3 "$db" "SELECT C_Order_ID, Value, DocStatus, expected_elements FROM c_order"
done
```

Every building must have exactly 1 c_order row.

### Task 4: Update BuildingWriter DDL if needed

Check that `BuildingWriter.initSchema()` creates c_order with all the
columns the INSERT needs. If any column is missing, add it.

### Task 5: Verification

1. `mvn compile -q` PASS
2. `mvn test-compile -q` PASS
3. `./scripts/run_RosettaStones.sh classify_sh.yaml` — SH ALL GREEN
4. `sqlite3 output/SH_output.db "SELECT count(*) FROM c_order"` → 1
5. `sqlite3 output/SH_output.db "SELECT count(*) FROM c_orderline"` → 37+

### Task 6: Update PROGRESS.md

Note: c_order pipeline fixed. Every compiled building produces a C_Order.

## Rules

- This is a surgical fix — change copyCOrderToOutput() only
- Do NOT restructure CompilationPipeline
- Do NOT add BIM_COBOL as a DAGCompiler dependency
- Keep RegisterBuildingVerb alive for interactive Designer use
- SH gates must stay ALL GREEN

Commit: `[S##-c_order] Fix C_Order pipeline — every building produces a C_Order`

## When Done

Prepend `# DONE` + commit hash to this file's first line.
Append: c_order row counts for SH, DX, TE, FK, DM below `---`.

---

## c_order row counts (S94)
| Building | C_Order_ID | Value | DocStatus | ExpectedElements | C_DocType_ID |
|----------|------------|-------|-----------|------------------|--------------|
| SH | 1 | SampleHouse | IP | 58 | RE_SH |
| DX | 1 | Duplex | IP | 1099 | RE_DX |
| FK | 1 | Ifc4_FZKHaus | IP | 82 | RE_FK |
| IN | 1 | Ifc2x3_AC11Institute | IP | 699 | RE_IN |
