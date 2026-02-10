# Continuous Hardening Roadmap

## Priority 1: Refactoring (Code Quality)

```
Prompt for Code: Refactor for maintainability

1. Extract magic numbers to BIMConstants.java
   - PLANE_TOLERANCE = 50mm (currently in AssemblyGeometryValidator)
   - STUD_SPACING = 450mm or 600mm
   - MIN_STUD_HEIGHT = 2.4m
   - Any other hardcoded values

2. Consolidate factory pattern
   - HybridFactory → LibraryFactory path is working
   - But BuildingWriter still has direct geometry creation
   - All geometry creation should route through factory

3. Validator naming consistency
   - GeometryValidator (room level)
   - AssemblyGeometryValidator (component level)
   - Consider: RoomGeometryValidator + ComponentGeometryValidator?

4. Test organization
   - Unit tests: *Test.java (single class)
   - Integration tests: *IntegrationTest.java (multi-class)
   - Proof tests: *ProofTest.java (mathematical verification)

Report: Files changed, constants extracted, patterns unified
```

---

## Priority 2: Abstracting (Architecture)

```
Prompt for Code: Abstract for extensibility

1. Dictionary as loadable data
   Current: SpaceType is Java enum (compile-time)
   Target: SpaceType loaded from dictionary file (runtime)

   Create: SpaceTypeRegistry.java
   - Loads from bim-vocabulary.json or bim-vocabulary.yaml
   - New SpaceType = new dictionary entry, no recompile

2. Validation rules as configuration
   Current: Min dimensions hardcoded per validator
   Target: Rules loaded from dictionary

   Create: ValidationRuleLoader.java
   - Reads SpaceType.validation from dictionary
   - GeometryValidator uses loaded rules

3. Assembly templates as configuration
   Current: WALL_PANEL structure in code
   Target: Assembly templates in dictionary

   Create: AssemblyTemplateRegistry.java
   - Defines: WALL_PANEL = {FRAME: [top_plate, bottom_plate, studs], CLADDING: [...]}
   - New assembly type = dictionary entry

4. Profile/Protocol as loadable files
   Current: ProfileRegistry.java with hardcoded profiles
   Target: profiles/*.yaml loaded at runtime

Report: What's now configurable vs compiled
```

---

## Priority 3: Extending with Research (Vocabulary Growth)

```
Prompt for Code: Research and extend vocabulary

1. Complete window reconnection (if not done)
   - Verify windows use library geometry like doors
   - Create WINDOW_ASSEMBLY structure
   - Add hardware (stays, locks) as BOM-only

2. Roof assembly research
   - Query TERMINAL for any roof-related components
   - Research: AS 4440 (roof trusses), manufacturer specs
   - Document in docs/lod400-roof-assembly-research.md
   - Status: ✓ EXTRACTED / ◐ RESEARCHED / ○ PENDING

3. Foundation assembly research
   - TERMINAL has slabs - extract patterns
   - Research: strip footings, pad footings for residential
   - Document connection to wall (anchor bolts, starter bars)

4. MEP assembly research
   - Sprinklers already working (Phase 8)
   - Research: electrical (GPO, switches, circuits)
   - Research: plumbing (fixtures, drainage)
   - What's in TERMINAL? What needs residential IFC?

5. Fastener/hardware expansion
   - Current: door hinges, handles
   - Add: window hardware (from research)
   - Add: structural connectors (joist hangers, post anchors)
   - Source: Simpson Strong-Tie, Pryda, MiTek catalogs

Report: What was added to vocabulary, what remains PENDING
```

---

## Priority 4: Hardening Tests (Confidence)

```
Prompt for Code: Expand test coverage

1. Edge case tests
   - Minimum size room (what's the smallest valid BEDROOM?)
   - Maximum size room (any limits?)
   - Room at grid edge (boundary conditions)
   - OPEN_PLAN with single zone (degenerate case)

2. Failure mode tests
   - Invalid SCHEDULE reference (type:D99 not defined)
   - Overlapping room bounds
   - Unsatisfiable constraints
   - Missing required spaces (Protocol violation)

3. Round-trip tests
   - DSL → DB → query → verify matches DSL intent
   - Modify DSL slightly → verify DB changes correctly

4. Performance tests (if not exists)
   - 10 rooms: time to compile?
   - 50 rooms: time to compile?
   - 100 rooms: still works?

5. Regression test suite
   - TB-LKTN must always pass (baseline)
   - Shed must always pass (baseline)
   - Any new building becomes baseline after verification

Report: Test count before/after, coverage gaps identified
```

---

## Priority 5: Documentation Sync

```
Prompt for Code: Sync documentation with implementation

1. Verify bim-dsl-dictionary.md matches code
   - Every DSL keyword parseable?
   - Every SpaceType implemented?
   - Every constraint working?
   - Mark any gaps: "DOCUMENTED BUT NOT IMPLEMENTED"

2. Update glossary with new terms
   - AssemblyGeometryValidator
   - PLANE_TOLERANCE
   - Any new patterns

3. Create CHANGELOG.md
   - Phase 28: DSL Dictionary v2.0, Profiles, Protocols
   - Phase 29: LOD400 reconnection, Assembly verification
   - Track what changed when

4. Update PROJECT_KNOWLEDGE.md
   - Current phase: 29
   - Test count: 105+ assembly checks
   - Verification chain complete

Report: Docs updated, gaps marked
```

---

## Suggested Sequence

| Week | Focus | Prompt |
|------|-------|--------|
| 1 | Refactoring | Priority 1 |
| 2 | Abstracting | Priority 2 |
| 3 | Extending | Priority 3 |
| 4 | Hardening | Priority 4 |
| Ongoing | Documentation | Priority 5 (after each phase) |

## Standing Rules for Code

```
STANDING RULES (include in every prompt):

1. PRIME RULE: Extract, don't imagine
   - New constants → query TERMINAL or cite standard
   - New patterns → extract from reference IFC

2. Mathematical proof over visual
   - Every geometry change → add verification test
   - No "it looks right" - numbers must match

3. Mark uncertainty honestly
   - ✓ EXTRACTED (from data)
   - ◐ RESEARCHED (from standards)
   - ○ PENDING (needs source)

4. Vocabulary as data
   - Prefer configuration over code
   - New type = dictionary entry, not Java change

5. TERMINAL conformance
   - Output must match proven schema
   - Same queries work on TERMINAL and generated DB
```

---

*Created: 2026-01-30*
*Status: Phase 29 complete, roadmap for continuous improvement*
