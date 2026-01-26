# PRIME RULE

**EXTRACT, DON'T IMAGINE.**

Query the federated model DB. Copy patterns you find. Never invent.

---

**When lost:** `cat SESSION_STATE.md` then query DB.

**After token refresh:** Read this file → state phase → continue.

---

# CURRENT STATUS (2026-01-27)

| Phase | Status | Description |
|-------|--------|-------------|
| Phase 0: Extraction | ✓ Complete | 12 patterns in SESSION_STATE.md |
| Phase 1: Interfaces | ✓ Complete | 21 files |
| Phase 2: Validators | ✓ Calibrated | 7 validators |
| Phase 3: Builders | ✓ Complete | Wall + Pipe builders |
| Phase 4: Connections | ✓ Passed | 98.1% validation |
| Phase 5: IFC Export | ✓ Complete | 6/6 round-trip tests |
| **Phase 6: DSL Compiler** | ✓ Complete | DSL → IFC pipeline working |
| **Phase 7: Code Validation** | ✓ Complete | IRC 2021 room requirements |

**GitHub:** https://github.com/red1oon/BIMCompiler

---

# DSL COMPILER (Complete)

```
DSL Input (.bim)
    │
    ▼
DSLParser → StoreyDefinition + RoomDefinitions
    │
    ▼
RoomRequirements → Validates against IRC 2021 (warnings)
    │
    ▼
GridLayoutResolver → RoomPolygons (coordinates)
    │
    ▼
RoomCompiler → WallSpecs + OpeningSpecs
    │         (interior=150mm, exterior=250mm)
    │
    ▼
DSLExporter → JSON → Python → IFC4
    │
    ▼
Valid IFC File (IfcWall, IfcSpace, IfcOpeningElement)
```

**Usage:**
```bash
java -cp target/bim-compiler-1.0.jar com.bim.compiler.dsl.BIMCompiler \
    examples/test-house.bim output/test-house.ifc
```

**DSL Syntax:**
```
STOREY "Ground" height:2.8m {
    BEDROOM "master" at:A1 size:5x4m {
        DOOR south to:corridor
        WINDOW north
    }
    CORRIDOR "corridor" at:A2 size:8x1.5m {
        DOOR north to:master
    }
}
```

---

# BUILDING CODE VALIDATION (Complete)

**RoomRequirements.java** - IRC 2021 standards (not invented):

| Room Type | Min Area | Min Dim | Egress | Code Reference |
|-----------|----------|---------|--------|----------------|
| BEDROOM | 6.5 m² | 2.134 m | Yes | IRC R304.1, R310.1 |
| BATHROOM | - | - | No | IRC R303.3, M1507.3 |
| KITCHEN | - | - | No | IRC R304.1 Exception |
| LIVING | 6.5 m² | 2.134 m | No | IRC R304.1, R303.1 |
| CORRIDOR | - | 0.914 m | No | IBC dwelling unit |

**Validation output:**
```
Step 4: Validating against IRC 2021...
  WARNING: Room 'tiny_bed' (BEDROOM): area 4.0 m² below code minimum 6.5 m²
  1 code warning(s) - see above
```

---

# VERIFICATION AUDIT (7/7 PASS)

All values traced to documented sources:

| Item | Status | Source |
|------|--------|--------|
| Wall Thickness (150/230/250/300mm) | ✓ | Terminal FACT3 |
| Tolerance (5mm) | ✓ | Terminal FACT1 |
| IRC Room Requirements | ✓ | IRC 2021 R303/R304/R310 |
| Grid Resolution | ✓ | DSL spec |
| IFC Relationships | ✓ | IFC4 schema |
| Opening Ratios (55%/65%) | ✓ | Terminal G7 |
| Interior/Exterior Logic | ✓ | RoomCompiler spec |

---

# KEY FILES

**DSL Package (14 files):**
- `dsl/BIMCompiler.java` - Main entry point
- `dsl/DSLParser.java` - Regex parser
- `dsl/GridLayoutResolver.java` - Grid → coordinates
- `dsl/RoomCompiler.java` - Rooms → walls/openings
- `dsl/RoomRequirements.java` - IRC 2021 validation
- `dsl/DSLExporter.java` - Java → Python bridge

**Export Package:**
- `export/IFCExportConfig.java` - Config-driven patterns
- `export/IFCExporter.java` - Java wrapper
- `scripts/export_dsl_to_ifc.py` - IfcSpace support
- `scripts/export_to_ifc.py` - Wall/pipe export

**Examples:**
- `examples/test-house.bim` - 3-room house
- `examples/test-undersized.bim` - Fails code validation

---

# DATABASE

```
DB: /home/red1/IfcOpenShell/WORK_DIR/databases/enhanced_federation_GI.db
51,723 elements, 234MB
```

---

# NEXT STEPS (Optional Enhancements)

1. **More Builders** - DuctBuilder, FittingBuilder
2. **More Room Types** - DINING, UTILITY, LAUNDRY
3. **Multi-Storey** - Stack storeys, add stairs
4. **Space Solver** - Auto-layout from room list (research problem)

Current pipeline is **complete and working**.
