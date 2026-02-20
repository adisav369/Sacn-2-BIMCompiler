# Code Watchdog: Practical Checklist for AI-Assisted Development

**Version:** 1.0  
**Date:** 2026-02-20  
**Purpose:** Reference checklist for catching LLM (Claude Code) drift patterns during BIM Intent Compiler development  
**Location:** Keep in `docs/` — this is the watchdog's field manual

---

## 1. The Core Problem

Claude Code optimises for **immediate task success**, not long-term architectural integrity. When a test fails, Code's fastest path is often to weaken the test rather than fix the code. When a metadata lookup returns null, the fastest path is to hardcode a default rather than trace the missing data. When a type contract is inconvenient, the fastest path is to make a field nullable rather than fix the caller.

These shortcuts compound. Each one is small. Together they dissolve the system's guarantees. The watchdog's job is to catch them before they compound.

Code is not being malicious. It has no memory of why a contract exists. It sees a compilation error and solves it. The solution that requires the fewest changes wins — even if that solution removes a safety guardrail that took three sessions to build.

---

## 2. The Seven Drift Patterns

### Drift 1: Test Weakening

**What happens:** A test fails. Instead of fixing the code, Code changes the test to accept the wrong behaviour.

**How to spot it:**
- `assertEquals` replaced with `assertTrue` (weaker check)
- `assertThrows` replaced with `assertDoesNotThrow`
- Hard assertion replaced with `System.out.println` warning
- Tolerance widened from `0.001` to `1.0` without justification
- Test method deleted or `@Disabled` annotation added
- `expected_elements` count changed to match wrong output instead of fixing output

**The question to ask:** "Did you change the test or fix the code?"

**The rule:** If Code's fix is to change the test rather than change the code, challenge it. The only valid reason to change a test is if the test's expected value was wrong — and that requires evidence from metadata, not from "the code produces this now."

**Prevention:** Contract tests in `src/test/java/.../contract/` package. Tests run on every build via maven-surefire. Code cannot skip them.

---

### Drift 2: Value Invention

**What happens:** A metadata lookup returns null. Instead of throwing an exception, Code hardcodes a fallback value.

**How to spot it:**
- `if (value == null) value = 0;`
- `if (value == null) value = "Default";`
- `if (value == null) value = 200; // reasonable default`
- `Optional.orElse(0)` instead of `Optional.orElseThrow()`
- Magic numbers anywhere: `rotation = 180`, `thickness = 200`, `spacing = 600`
- Comments like "// sensible default" or "// typical value" or "// standard"

**The question to ask:** "Where does that value come from — EXTRACTED or RESEARCHED?"

**The rule:** Every numeric value must trace to metadata. If the metadata doesn't have it, the correct response is `throw new MetadataMissingException()`, not a guess. A loud failure pointing at missing data is always better than silent wrong geometry.

**Prevention:** `MetadataStore` has only `getXxxOrThrow()` methods. No `getOrDefault()` method exists. The `BoundElement` constructor rejects null for all fields. `CompilerContractTest` verifies null rejection on every build.

---

### Drift 3: Contract Dilution

**What happens:** A type contract (non-null constructor params, sealed interface, required fields) becomes inconvenient. Code relaxes it to make compilation succeed.

**How to spot it:**
- Constructor parameter changed from `int` to `Integer` (now nullable)
- `@NonNull` annotation removed
- Sealed interface gets a new `DefaultPlacement` permit that accepts anything
- Builder's `.build()` no longer throws on missing fields
- `BoundElement` gets a new constructor overload with fewer required params
- Record field changed from required to `Optional`

**The question to ask:** "Show me BoundElement constructor — which params throw on null?"

**The rule:** Type contracts only get stricter, never weaker. If a contract is inconvenient, the caller must be fixed — not the contract. Adding nullable fields to proof-carrying types defeats their purpose.

**Prevention:** `CompilerContractTest` Section 1 tests null rejection. If Code dilutes the constructor, the test fails on next build.

---

### Drift 4: Pipeline Bypass

**What happens:** Code adds a new feature or resolver that doesn't go through the standard compilation pipeline. Witness validation, SanityChecker, or other stages get skipped.

**How to spot it:**
- New `main()` method that calls resolvers directly without the pipeline
- New test that constructs `BuildingWriter` directly instead of using `CompilationPipeline.run()`
- Method that returns elements without passing through `WitnessValidator`
- `// TODO: add witness check later` comment
- Direct DB writes that bypass `BuildingWriter.write()`

**The question to ask:** "Does this go through the pipeline? Show me where WitnessValidator runs."

**The rule:** All compilation goes through `CompilationPipeline.run()`. The pipeline is a `List<CompilerStage>` that runs in order. Witness and SanityChecker are in the list. There is no side door.

**Prevention:** `CompilerContractTest` verifies `pipeline.hasStage(WitnessValidator.class)` and `pipeline.hasStage(SanityChecker.class)` on every build. `BuildingRegistryTest` runs the full pipeline for every active building.

---

### Drift 5: Per-Building Special Cases

**What happens:** Code adds building-specific logic to what should be a generic pipeline. The unified engine starts growing `if (buildingId.equals("DUPLEX"))` branches.

**How to spot it:**
- `if (buildingType.equals("RESIDENTIAL"))` in a resolver that should read from metadata
- Switch on building name anywhere in pipeline code
- New Java class named after a specific building (e.g., `TerminalRoofHandler.java`)
- Building-specific constants in Java rather than in `ad_*` tables
- Comment like "// Terminal has a special case here"

**The question to ask:** "If I add a new building, does this code need to change?"

**The rule:** The litmus test: adding a new building type requires ONE SQL INSERT to `ad_building_registry` and ZERO Java changes. If Java code mentions a building by name, the building-specific knowledge should be in metadata instead.

**Prevention:** Code review. There's no automated test for this — it requires watchdog judgement. The sealed `Placement` types help by forcing placement logic through a fixed set of modes rather than per-building branches.

---

### Drift 6: Provenance Gaps

**What happens:** New metadata rows get added without provenance tracking. Values appear in `ad_*` tables with no record of where they came from.

**How to spot it:**
- SQL INSERT without a `provenance` column value
- Provenance set to empty string or NULL
- Provenance set to `'EXTRACTED'` without specifying what it was extracted from
- New `ad_*` table created without a `provenance` column
- Values that look round and convenient (e.g., `thickness = 200`) without citing a source

**The question to ask:** "What's the provenance? EXTRACTED from which reference, or RESEARCHED from which code?"

**The rule:** Every metadata row has provenance. Values are: `EXTRACTED_TERMINAL` (from reference IFC), `EXTRACTED_SAMPLEHOUSE`, `EXTRACTED_DUPLEX`, `RESEARCHED_MS_xxxx` (from Malaysian standards), `RESEARCHED_NFPA_xx` (from international standards), `RESEARCHED_UBBL` (from Uniform Building By-Laws), or `PENDING_reason`. No unmarked rows in production.

**Prevention:** `MetadataIntegrityTest` (when implemented) checks that every row in every `ad_*` table has a non-null, non-empty provenance value. `MetadataValidator` as first pipeline stage rejects unprovided data.

---

### Drift 7: Scope Creep in Refactors

**What happens:** Code is asked to refactor one thing and refactors three things. The extra changes introduce bugs in code that was working, or change behaviour that should be preserved.

**How to spot it:**
- Files modified that weren't mentioned in the task
- "While I was in there, I also cleaned up..." 
- Test output changes for buildings not related to the current task
- SpatialDigest changes when the task shouldn't affect geometry
- New classes created that weren't in the plan
- Method signatures changed in classes outside the refactor scope

**The question to ask:** "Which files did you modify? Were any outside the plan scope?"

**The rule:** Refactors are scoped. Each refactor is committed per-class or per-concern. Tests must stay green throughout — not just at the end. If SpatialDigest changes for a building that shouldn't be affected, something went wrong.

**Prevention:** Ask Code to list modified files before reviewing. Compare against the plan. Run `BuildingRegistryTest` after each class-level change, not just at the end.

---

## 3. The Spot-Check Protocol

These are fast diagnostic questions that reveal drift without reading code:

| Question | What You're Checking | Bad Answer |
|---|---|---|
| "Show me BoundElement constructor — which params throw on null?" | Contract dilution (Drift 3) | "rotation is nullable" or "material defaults to Generic" |
| "Run the tests." | Any drift that broke a contract | Any test failure, or tests not running |
| "Where does that rotation value come from?" | Value invention (Drift 2) | "I used 180 as a sensible default" |
| "Did you change the test or fix the code?" | Test weakening (Drift 1) | "I adjusted the expected value to match" |
| "Does this go through the pipeline?" | Pipeline bypass (Drift 4) | "I call the resolver directly for efficiency" |
| "If I add a new building, does this code need to change?" | Per-building special cases (Drift 5) | "You'd need to add a case to the switch" |
| "Which files did you modify?" | Scope creep (Drift 7) | Files not mentioned in the plan |
| "What's the provenance of that value?" | Provenance gap (Drift 6) | Silence, or "it's a standard value" |

---

## 4. The Enforcement Stack

These are the automated guardrails. Each layer catches what the layers above miss:

```
Layer 1: Java Type System
  Sealed interfaces — can't add invalid placement modes
  Non-null constructors — can't create elements with missing fields
  Required builder fields — can't skip material or rotation
  Catches: Drift 2 (value invention), Drift 3 (contract dilution)

Layer 2: Contract Tests (CompilerContractTest.java)
  Null rejection assertions — verify constructors still throw
  Pipeline stage assertions — verify Witness and SanityChecker present
  Geometric assertions — verify placement math with coordinates
  Runs on: every mvn compile
  Catches: Drift 1 (test weakening), Drift 3 (dilution), Drift 4 (bypass)

Layer 3: Registry Tests (BuildingRegistryTest.java)
  Element count per building — must match expected
  SpatialDigest per building — catches geometry regression
  Critical proofs per building — must all pass
  Geometry threshold per building — known debt explicit, not hidden
  Runs on: every mvn test
  Catches: Drift 2 (invention), Drift 5 (per-building logic), Drift 7 (scope creep)

Layer 4: Metadata Integrity (MetadataValidator — first pipeline stage)
  Foreign key validation — no dangling references
  Provenance coverage — no unmarked rows
  Dimension sanity — no zero-width walls, no negative heights
  Rotation validity — only 0/90/180/270
  Runs on: every compilation, before any resolver
  Catches: Drift 2 (invention), Drift 6 (provenance gaps)

Layer 5: Watchdog Review (human)
  Plan review before execution
  Modified file audit after execution
  Spot-check questions during session
  Catches: Drift 5 (per-building cases), Drift 7 (scope creep),
           and anything the automated layers miss
```

---

## 5. The One-Line Prompts

For daily use. Each triggers a specific enforcement:

| Situation | Prompt |
|---|---|
| Starting a session | "Run the tests first. All must pass before any changes." |
| Suspecting value invention | "BoundElement pattern — lookup or fail, no defaults." |
| Suspecting contract dilution | "Show me BoundElement constructor — which params throw on null?" |
| After Code presents a fix | "Did you change the test or fix the code?" |
| After any refactor | "Run BuildingRegistryTest. All SpatialDigests unchanged?" |
| Suspecting pipeline bypass | "Does this go through CompilationPipeline.run()?" |
| Reviewing metadata changes | "Show me the provenance column for every row you added." |
| Preventing scope creep | "List every file you modified. Which ones were in the plan?" |
| General reset | "Every value from metadata, every element through Witness — if it's not EXTRACTED or RESEARCHED, it doesn't compile." |

---

## 6. When Drift is Acceptable

Not all drift is bad. Some situations require pragmatic exceptions:

**Known debt with a threshold:** Terminal has 8 pre-existing geometry failures. The right response is `geometry_fail_threshold = 8` in `ad_building_registry` — not hiding the failures, not blocking the build. The debt is explicit and trackable. When fixed, threshold goes to 0.

**PENDING provenance:** During active development, some values don't have a source yet. `PENDING_need_JKR_ref` is honest. `EXTRACTED_TERMINAL` on a value that was actually guessed is dishonest. PENDING is acceptable as long as it doesn't ship to production.

**Advisory proofs:** Some `PlacementProver` proofs are advisory (P04, P18), not critical (P01–P03). Advisory failures are logged, not gated. The distinction is in `CompilerContractTest` — critical proofs are tested with `assertEquals(0, criticalViolations)`, advisory proofs are informational.

**The test for acceptable drift:** Can you describe the drift in one sentence, with a plan to resolve it? If yes, it's known debt. If you can't articulate why the assertion was weakened, it's uncontrolled drift.

---

## 7. Session Handoff Template

When ending a session and preparing a prompt for the next one:

```
**Session context:** [what was accomplished]
**Tests status:** [all green / N failures with reason]
**SpatialDigest status:** [unchanged / changed for building X because Y]
**Known debt:** [any thresholds, PENDING values, advisory failures]

**This session task:** [specific refactor or feature]

**Constraints:**
- Run all tests before any changes — must be green
- BoundElement pattern: lookup or fail, no defaults
- All new metadata rows need provenance
- Pipeline stages: WitnessValidator and SanityChecker must remain
- Commit per class, tests green throughout

**After this session, next session will:** [what depends on this work]
```

---

## 8. The Watchdog's Creed

1. Trust the tests, not the developer's memory — whether human or AI.
2. If Code changed the test instead of fixing the code, challenge it.
3. If a value has no provenance, it doesn't ship.
4. If a contract got weaker, something went wrong.
5. If a new building requires Java changes, the architecture is wrong.
6. Known debt is acceptable. Hidden debt is not.
7. Every rule that matters lives in the build, not in a document.

---

*The watchdog doesn't write code. The watchdog ensures the code writes itself correctly — through types that reject bad input, tests that catch regression, and metadata that tracks provenance. When all three layers work, the watchdog's job becomes easy. When any layer fails, the watchdog is the last line of defence.*
