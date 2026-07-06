# DONE a75962f4
# Wire costOfChange() — Live Cost Delta from CostDAO

**Priority:** Killer demo feature. "Drag a room, see cost delta live." No
competitor shows cost-of-change in real time. CostDAO already works
(Schedule5DCostTest passes). Just pipe it through the stub.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** CostDAO and costBreakdown() already exist.
Wire them — don't reinvent.

## Read first

1. `BonsaiBIMDesigner/src/main/java/com/bim/designer/api/DesignerAPIImpl.java`
   — line 2887: `costOfChange()` stub returning zeroes.
2. `BonsaiBIMDesigner/src/main/java/com/bim/designer/api/DesignerAPI.java`
   — `CostOfChangeResponse` record (line ~942): fields are materialDeltaM,
   fittingsDelta, labourHrs, costDelta, currency, compliance, newClashes.
3. `BIMBackOffice/src/main/java/com/bim/backoffice/dao/CostDAO.java`
   — `costBreakdown()` method (line 62): takes bomConn + compLibConn +
   buildingId, returns CostSummary with grandTotal, materialTotal, etc.
   — `CostDelta` record (line 52): per-BOM-line old/new/delta.
4. `BonsaiBIMDesigner/src/main/java/com/bim/designer/api/DesignerAPIImpl.java`
   — line 71: `bomConn` field. Line 2628: lazy component_library.db access
   (`getOrOpenCompLib()`).

## Task: Wire costOfChange()

The `costOfChange()` method receives a JsonObject with `buildingId` and a
proposed change (chain GUIDs + new positions). For now, we don't need the
full BOM diff — just compute the CURRENT cost as baseline so the response
is real, not zeroed.

### Phase 1 (this prompt): Return real baseline cost

Replace the stub with:

```java
@Override
public CostOfChangeResponse costOfChange(com.google.gson.JsonObject rawRequest) {
    // Implementing BIM_Designer_SRS.md §26.12.3 — Witness: W-WF-COST
    try {
        String buildingId = rawRequest.has("buildingId")
                ? rawRequest.get("buildingId").getAsString() : null;
        if (buildingId == null)
            return new CostOfChangeResponse(false, 0, 0, 0, 0, "MYR",
                    List.of(), List.of(), "buildingId required");

        Connection compLibConn = getOrOpenCompLib();
        CostDAO costDao = new CostDAO();
        CostDAO.CostSummary baseline = costDao.costBreakdown(
                bomConn, compLibConn, buildingId);

        BIMLogger.fine(TAG, "costOfChange {} → baseline RM {:.2f}",
                buildingId, baseline.grandTotal());

        // Phase 1: return baseline as costDelta=0 (no diff yet)
        // Phase 2 (future prompt): compute actual BOM diff from proposed move
        return new CostOfChangeResponse(
                true,
                baseline.materialTotal(),
                baseline.lines().size(),
                baseline.laborTotal() > 0
                        ? baseline.laborTotal() / 150.0  // approx labour hours (RM150/hr)
                        : 0.0,
                0.0,  // costDelta = 0 until Phase 2 computes diff
                "MYR",
                List.of(),
                List.of(),
                null);
    } catch (Exception e) {
        LOG.log(Level.WARNING, "costOfChange failed", e);
        return new CostOfChangeResponse(false, 0, 0, 0, 0, "MYR",
                List.of(), List.of(), e.getMessage());
    }
}
```

### What to check before wiring

1. **Import** — add `import com.bim.backoffice.dao.CostDAO;` if not present.
2. **getOrOpenCompLib()** — verify it returns a Connection to component_library.db.
   Read the method (around line 2628).
3. **CostDAO dependency** — BIMBackOffice is already a dep of BonsaiBIMDesigner
   (check pom.xml if unsure).
4. **BIMLogger.fine format** — verify it accepts `{:.2f}` or use `%.2f` with
   String.format instead.

### What NOT to do

- Do NOT compute the actual BOM diff (Phase 2 — needs moveChain + before/after).
- Do NOT add new tables or schema changes.
- Do NOT modify CostDAO.

## Test: W-WF-COST-1

Add to `BonsaiBIMDesigner/src/test/java/com/bim/designer/` as
`CostOfChangeTest.java`. Pattern: open SH_BOM.db, create DesignerAPIImpl,
call costOfChange with `{"buildingId": "SH"}`.

```java
/**
 * W-WF-COST-1: costOfChange returns real baseline, not zeroes.
 * @Traces BIM_Designer_SRS.md §26.12.3 — cost-of-change is not stubbed
 */
@Test
void costOfChangeReturnsBaseline() {
    JsonObject req = new JsonObject();
    req.addProperty("buildingId", "SH");
    DesignerAPI.CostOfChangeResponse resp = api.costOfChange(req);

    assertTrue(resp.success(), "Should succeed: " + resp.error());
    assertTrue(resp.materialDeltaM() > 0,
            "Material baseline should be > 0, got " + resp.materialDeltaM());
    assertEquals("MYR", resp.currency());
}
```

## Verify

1. `mvn compile -q` — PASS
2. CostOfChangeTest passes
3. `./scripts/run_RosettaStones.sh classify_sh.yaml` — SH 7/7 PASS

## Rules

- Do NOT change CostDAO or any BackOffice class
- Do NOT change the compilation pipeline
- ONE file changed (DesignerAPIImpl.java), ONE file created (test)

## When Done

Prepend `# DONE` + commit hash to this file's first line.
