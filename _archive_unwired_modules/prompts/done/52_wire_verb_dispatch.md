# DONE — Wire executeVerb() + Round-Trip Test — Designer ↔ Verb Loop
> Commit: 4d8198f2 [S99-verb-wire]

**Priority:** This is the killer demo blocker. The entire compilation pipeline
works (9 stages, 75 verbs, 19+ buildings proven). The Designer API has ONE stub
preventing the edit loop from closing. Wire it.

You are a coder for bim-compiler. Two bounded tasks.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** VerbRegistry and VerbContext already exist.
Wire them — don't reinvent.

## Read first

1. `BonsaiBIMDesigner/src/main/java/com/bim/designer/api/DesignerAPIImpl.java`
   — line 2815: `executeVerb()` stub. Line 71: `bomConn` field. Line 2628: lazy
   component_library.db access.
2. `BIM_COBOL/src/main/java/com/bim/cobol/VerbRegistry.java` — `createDefault()`
   factory (75 verbs), `dispatch(VerbContext, String)` method.
3. `BIM_COBOL/src/main/java/com/bim/cobol/VerbContext.java` — record with
   `ofBom()`, `of()`, `withOutput()` factories.
4. `BIM_COBOL/src/main/java/com/bim/cobol/VerbResult.java` — what dispatch returns.

## Task 1: Wire executeVerb()

Replace the stub in `DesignerAPIImpl.executeVerb()` (line 2815-2831) with
real dispatch:

```java
@Override
public VerbResponse executeVerb(String buildingId, String verbLine) {
    try {
        VerbRegistry registry = VerbRegistry.createDefault();
        VerbContext ctx = VerbContext.ofBom(bomConn);
        VerbResult<?> result = registry.dispatch(ctx, verbLine);

        BIMLogger.fine("VERB", "Designer dispatch: {} → {}",
                verbLine, result.summary());

        return new VerbResponse(result.ok(), result.keyword(),
                result.summary(), result.ok() ? null : result.summary());
    } catch (Exception e) {
        LOG.log(Level.WARNING, "Verb execution failed: " + verbLine, e);
        return new VerbResponse(false, null, null, e.getMessage());
    }
}
```

### What to check before wiring

1. **VerbResponse constructor** — verify it matches `(boolean ok, String verb,
   String summary, String error)`. Read the class.
2. **VerbResult API** — verify `ok()`, `keyword()`, `summary()` exist. Read
   the class.
3. **Import** — add `import com.bim.cobol.VerbRegistry;`,
   `import com.bim.cobol.VerbContext;`, `import com.bim.cobol.VerbResult;`
4. **Delete** the `KNOWN_VERB_PREFIXES` set and `extractVerbKeyword()` method
   (lines 2844-2865) — no longer needed, VerbRegistry does longest-prefix match.

### What NOT to do

- Do NOT create a new VerbRegistry per call if it's expensive. Check if
  `createDefault()` is cheap (it just instantiates 75 verb objects). If it's
  cheap, per-call is fine. If expensive, cache as a field.
- Do NOT add component_library or output connections yet — `ofBom(bomConn)` is
  sufficient for CHECK/HELLO/TRIM verbs. Verbs that need more connections will
  fail gracefully (they check for null connections). A future prompt will wire
  the full `VerbContext.withOutput()` for emitting verbs.

## Task 2: Add round-trip test

Create or extend a test that proves:

```
bomDrop(SH) → compile → output.db has N elements
    → executeVerb("SH", "CHECK BOM SH") → VerbResponse.ok() == true
```

This proves the Designer can trigger verb execution on a compiled building.
The verb doesn't need to MODIFY anything — CHECK BOM is read-only and
sufficient to prove the loop.

### Where to put it

Add to `BonsaiBIMDesigner/src/test/java/com/bim/designer/CompileBridgeTest.java`
or create `VerbDispatchTest.java` in the same package. Follow the existing
test pattern (look at CompileBridgeTest for how bomConn is set up).

### Witness

```java
/**
 * W-VERB-DISPATCH-1: Designer can dispatch a verb to VerbRegistry.
 * @Traces BBC.md §6 — verb dispatch is not stubbed
 */
@Test
void verbDispatchFromDesigner() throws Exception {
    // Setup: bomDrop + compile (reuse from CompileBridgeTest)
    // Act: api.executeVerb(buildingId, "CHECK BOM " + buildingId)
    // Assert: response.ok() == true, response.verb() == "CHECK BOM"
}
```

## Verify

1. `mvn compile -q` — PASS
2. The new test passes
3. `./scripts/run_RosettaStones.sh classify_sh.yaml` — SH 7/7 PASS (no regression)
4. FINE log shows: `[FINE ] VERB Designer dispatch: CHECK BOM SH → ...`

## Rules

- Do NOT change VerbRegistry, VerbContext, or any verb implementation
- Do NOT change the compilation pipeline
- Do NOT add new dependencies (BIM_COBOL is already a dep of BonsaiBIMDesigner)
- ONE file changed for Task 1 (DesignerAPIImpl.java), ONE file for Task 2

## Commit

```
[S##-verb-wire] Wire executeVerb() — Designer dispatches to VerbRegistry

Replace DesignerAPIImpl stub with real VerbRegistry.dispatch(). Delete
extractVerbKeyword() dead code. W-VERB-DISPATCH-1 proves round-trip:
bomDrop → compile → executeVerb(CHECK BOM) → ok.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
```

## When Done

Prepend `# DONE` + commit hash to this file's first line.

---
