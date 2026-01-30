# PRIME RULE

**EXTRACT, DON'T IMAGINE.**

Query the federated model DB. Copy patterns you find. Never invent.

---

**When lost:** `cat SESSION_STATE.md` then query DB.

**After token refresh:** Read this file → state phase → continue.

---

# CURRENT STATUS (2026-01-30)

| Phase | Status | Description |
|-------|--------|-------------|
| Phases 0-24 | ✓ Complete | Foundation through MEP trifecta |
| Phase 25: Outlier Handling | ✓ Complete | Graceful degradation + developer guidance |
| Phase 28: Validation Factory | ✓ Complete | Dynamic validator composition + TB-LKTN proof |
| **Phase 29: LOD400 Reconnect** | ✓ Complete | Assembly verification (105/105 checks) |
| **Phase 30: Priority 2+4** | ✓ Complete | Config-driven extensibility + outlier integration |

**GitHub:** https://github.com/red1oon/BIMCompiler

---

# PHASE 30 COMPLETION SUMMARY

## Priority 2: Abstracting - COMPLETE

| Component | Location | Count |
|-----------|----------|-------|
| SpaceTypes | `config/spacetypes.yaml` | 21 types |
| Assemblies | `config/assemblies.yaml` | 7 templates |
| Profiles | `config/profiles/*.yaml` | base + malaysian_residential |

**SpaceTypeRegistry.java** loads YAML at runtime with hot-reload capability.
**Proof:** STUDY_NOOK added via YAML only - no recompile needed.

## Priority 4: Outlier Handling - COMPLETE

**OutlierLogger** integrated into 7 components:
- RoomType.java - SpaceType fallback
- SpaceTypeRegistry.java - YAML lookup fallback
- ComponentLibrary.java - Missing component
- SpaceSolver.java - Constraint relaxation
- FixturePlacer.java - Geometry impossible (7 points)
- HabitabilityValidator.java - Validation unknown
- BuildingCompiler.java - Summary generation

**Feedback loop operational:**
```
Unknown input → Graceful fallback → Actionable log → Developer backlog
```

## Tolerance Source Documentation

BIMConstants.java now documents tolerance sources:
```java
TOLERANCE (5mm)           - ✓ EXTRACTED: TERMINAL FACT 1
PLANE_TOLERANCE (50mm)    - ○ ASSUMED + TODO: validate
ASSEMBLY_TOLERANCE (10mm) - ○ ASSUMED + TODO: validate
STUD_HEIGHT_TOLERANCE     - ◆ RESEARCHED: AS 1684
```

---

# TB-LKTN BASELINE OUTLIER REPORT

```
=== OUTLIER SUMMARY ===
Compilation: TB-LKTN
Total: 1 outliers
Outlier rate: 1/71 elements = 1.4%

GEOMETRY_IMPOSSIBLE: 1
  - exhaust_fan (bathroom too small)

Doors: 4 LOD400, 0 parametric (100% library)
Windows: 0 LOD400, 7 parametric (expected - TERMINAL has commercial windows)
```

**Window gap is domain mismatch, not bug.** TERMINAL library has commercial curtain walls (1200-4800mm tall), not residential windows (500-1000mm tall). Parametric fallback is correct behavior.

---

# NEXT: PRIORITY 3 (Extending with Research)

Data-driven approach enabled by outlier logging:
- Run compilations
- Collect outlier logs
- Prioritize based on frequency

Potential targets:
1. Window LOD400 library (requires residential IFC reference)
2. Roof assembly completion
3. Foundation assembly research
4. MEP expansion

---

# RESUME PROMPT

```
Continue BIM Compiler project. Phase 30 complete.

Current state:
- Priority 2 (Abstracting): COMPLETE
  - 21 SpaceTypes in config/spacetypes.yaml
  - 7 Assembly templates in config/assemblies.yaml
  - Profiles: base.yaml, malaysian_residential.yaml
  - SpaceTypeRegistry with hot-reload + OutlierLogger

- Priority 4 (Outlier Handling): COMPLETE
  - OutlierLogger utility (6 categories)
  - Integrated into 7 components
  - Feedback loop operational
  - 1.4% outlier rate baseline

- Tolerance documentation updated with sources
  - 5mm EXTRACTED from TERMINAL
  - 10mm, 50mm marked as ASSUMED with TODOs

Next: Priority 3 (Extending with Research)
- Data-driven via outlier logs
- Window library gap is known (domain mismatch)

Key docs:
- docs/bim-compiler-progress-update-phase-30.md
- docs/code-task-outlier-handling.md (completed)
- config/spacetypes.yaml (21 types)

Standing rules: PRIME RULE (extract don't imagine), mathematical proof, vocabulary as data
```

---

# KEY FILES (Updated)

```
config/
├── spacetypes.yaml           # 21 space types (Phase 29)
├── assemblies.yaml           # 7 assembly templates (Phase 29)
└── profiles/
    ├── base.yaml             # IRC 2021 defaults
    └── malaysian_residential.yaml  # UBBL 1984

src/main/java/com/bim/compiler/
├── BIMConstants.java         # 35+ constants with source markers
├── dsl/
│   ├── SpaceTypeRegistry.java    # YAML loader + OutlierLogger
│   ├── RoomType.java             # SpaceType enum + OutlierLogger
│   ├── BuildingCompiler.java     # OutlierLogger summary
│   └── DoorWindowLibraryMapper.java  # LOD400 reconnection
├── validation/
│   └── AssemblyGeometryValidator.java  # 105 checks
├── library/
│   ├── ComponentLibrary.java     # + OutlierLogger
│   └── FixturePlacer.java        # + OutlierLogger (7 points)
├── solver/
│   └── SpaceSolver.java          # + OutlierLogger relaxation
└── util/
    └── OutlierLogger.java        # 6 categories, metrics, summary

docs/
├── bim-compiler-progress-update-phase-30.md  # Latest progress
├── bim-dsl-dictionary.md         # 2100+ lines vocabulary spec
├── bim-compiler-glossary.md      # Terminology reference
└── code-task-outlier-handling.md # Task spec (completed)
```

---

# MATHEMATICAL VERIFICATION

| Check | Method | Tolerance | Source |
|-------|--------|-----------|--------|
| Wall gaps | Euclidean distance | 5mm | ✓ TERMINAL |
| Room enclosure | Edge overlap | 5mm | ✓ TERMINAL |
| Room overlap | Area intersection | 25mm² | derived |
| Door in wall | BBox containment | 10mm | ○ ASSUMED |
| Door on plane | Center alignment | 50mm | ○ ASSUMED |
| Stud height | Height comparison | 500mm | ◆ AS 1684 |

**105/105 assembly geometry checks passing.**

---

# TEST COMMANDS

```bash
# TB-LKTN complete test
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNCompleteTest" -q

# End-to-end with outlier report
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNEndToEndTest" -q

# Outlier handling tests
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.OutlierHandlingTest" -q

# SpaceTypeRegistry test
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.SpaceTypeRegistry" -q

# Door/Window library mapping
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.DoorWindowLibraryMapper" -q
```

---

# ARCHITECTURE (Phase 30)

```
PURE CORE (unchanging)
├── Federated DB Schema (TERMINAL-proven)
├── DSL Grammar (SPACE as universal primitive)
├── Compiler Engine (reads configuration)
├── Factory Pattern (routes to library/parametric)
├── Validator Framework (composes dynamically)
├── OutlierLogger (feedback loop)           ← Phase 30
└── Blender Pipeline (federation_viz_helper.py)

DYNAMIC VOCABULARY (growing via configuration)
├── SpaceTypes (config/spacetypes.yaml)     ← Phase 30
├── Assemblies (config/assemblies.yaml)     ← Phase 30
├── Profiles (config/profiles/*.yaml)       ← Phase 30
├── Protocols (building type templates)
├── Schedules (door/window/material types)
└── Validation Rules (per SpaceType)
```

**System is production-ready with 1.4% outlier rate baseline.**
