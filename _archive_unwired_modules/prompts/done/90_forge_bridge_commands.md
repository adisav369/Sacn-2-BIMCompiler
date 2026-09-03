# DONE
# Forge Bridge Commands — Java Side of BlenderBridge Forge Protocol

**Priority:** Unblock the Forge UI chain (P60-P63). Wire `forgeCompute`
and `forgeCost` actions into WebUIServer dispatch. ForgeEngine and
CostDAO already work — this connects them to the API layer so Bonsai
(or any HTTP client) can call them.

**Prerequisite:** P59 (ForgeFabricationWriter) and P64 (RebarCageForge)
are DONE. ForgeEngine has 6 implementations. CostDAO is wired (P53).

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Follow the existing WebUIServer dispatch
pattern (line 222 switch). No new infrastructure — add cases to the
existing switch, call existing engines.

## Read first

1. `BonsaiBIMDesigner/src/main/java/com/bim/designer/api/WebUIServer.java`
   — line 222: action dispatch switch. Add `forgeCompute` and `forgeCost`
   cases here. Follow the `handleColorScheme` / `handleNlpQuery` pattern.
2. `BIM_COBOL/src/main/java/com/bim/cobol/forge/ForgeEngine.java` — interface
3. `BIM_COBOL/src/main/java/com/bim/cobol/forge/ForgeResult.java` — result type
4. `BIM_COBOL/src/main/java/com/bim/cobol/verb/ForgeVerb.java` — has the
   registry of all ForgeEngine implementations. Reuse its lookup.
5. `BIMBackOffice/src/main/java/com/bim/backoffice/dao/CostDAO.java`
   — `costBreakdown()` method for `forgeCost` action.
6. `docs/BlenderBridge.md` — protocol spec for forge commands.

## Task 1: forgeCompute Action

Add to WebUIServer dispatch:

```java
case "forgeCompute" -> handleForgeCompute(obj);
```

Handler receives JSON:
```json
{
  "action": "forgeCompute",
  "pieceType": "SLOPE_CUT",
  "params": { "pitch": 30.0, "span": 5200, "material": "C30" }
}
```

Implementation:
1. Read `pieceType` and `params` from JSON
2. Look up the ForgeEngine by piece type (same registry as ForgeVerb)
3. Build a VerbContext or pass params directly
4. Call `engine.compute(ctx, params)` → ForgeResult
5. Return ForgeResult as JSON (geometry records, compliance checks,
   fabrication map)

**ForgeResult already has fields:** `success`, `pieceType`, `geometryRecords`,
`complianceChecks`, `fabricationMap`. Serialize to JSON.

## Task 2: forgeCost Action

Add to WebUIServer dispatch:

```java
case "forgeCost" -> handleForgeCost(obj);
```

Handler receives JSON:
```json
{
  "action": "forgeCost",
  "pieceType": "SLOPE_CUT",
  "params": { "pitch": 30.0, "span": 5200, "material": "C30" }
}
```

Implementation:
1. First call `handleForgeCompute` internally to get ForgeResult
2. Extract material quantities from fabrication map
3. Call `CostDAO.costBreakdown()` with quantities
4. Return combined JSON: forge result + cost breakdown

If CostDAO connection is not available (no output.db loaded), return
forge result with `"cost": null` — don't fail.

## Task 3: forgeList Action (bonus — quick)

```java
case "forgeList" -> handleForgeList(obj);
```

Returns list of registered piece types and their parameter schemas:
```json
{
  "pieceTypes": [
    { "type": "SLOPE_CUT", "params": ["pitch", "span", "material", "crossSection"] },
    { "type": "REBAR_CAGE", "params": ["type", "grade", "exposure", "width", "depth", "thickness"] }
  ]
}
```

This lets the UI build parameter forms dynamically (metadata-driven).

## What NOT to do

- Do NOT modify ForgeEngine or any forge implementation
- Do NOT modify CostDAO
- Do NOT add external dependencies
- Do NOT create new server infrastructure — use existing WebUIServer
- Do NOT implement Python/Bonsai UI (that's P60b)
- Do NOT write to any database — these are read-only query actions

## Verify

1. `mvn compile -q` — PASS
2. Add a test: send `{"action":"forgeCompute","pieceType":"SLOPE_CUT","params":{"pitch":30,"span":5200}}`
   to WebUIServer dispatch → verify ForgeResult JSON returned with geometry records
3. `./scripts/run_RosettaStones.sh classify_sh.yaml` — SH 7/7 (no regression)

## When Done

Prepend `# DONE` + commit hash to this file's first line.
