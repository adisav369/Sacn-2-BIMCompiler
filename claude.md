# PRIME RULE

**EXTRACT, DON'T IMAGINE.**

Query the federated model DB. Copy patterns you find. Never invent.

---

**When lost:** `cat SESSION_STATE.md` then query DB.

**After token refresh:** Read SESSION_STATE.md → Read workdiary.txt → state phase → continue.

---

# CURRENT STATUS (2026-01-25)

| Phase | Status | Files |
|-------|--------|-------|
| Phase 0: Extraction | ✓ Complete | 12 patterns in SESSION_STATE.md |
| Phase 1: Interfaces | ✓ Complete | 21 files |
| Phase 2: Validators | ✓ Calibrated | 7 validators |
| Phase 3: Builders | ✓ Complete | Wall + Pipe builders |
| Phase 4 Gate | ✓ Passed | 98.1% connection validation |

**GitHub:** https://github.com/red1oon/BIMCompiler

---

# NEXT TASKS

## Option A: Continue Phase 3 (More Builders)

1. **DuctBuilder** (ACMV discipline)
   - Rectangular extrusion (PATTERN 7: 166 vertices)
   - Standard sizes from RULE 4: 150x150, 300x300, etc.
   - Sample: Query `SELECT guid FROM elements_meta WHERE ifc_class='IfcDuctSegment' LIMIT 1`

2. **FittingBuilder** (Pipe/Duct fittings)
   - PATTERN G10: FP has 1.18 fittings/pipe
   - More complex geometry (120 vertices avg)

## Option B: Jump to Phase 7 (IFC Export - Recommended)

Close the loop with full round-trip proof:
```
DB → Extract → Generate → IFC Export → Reimport → Compare
```

This proves deterministic generation without Blender visual inspection.

Steps:
1. Add IFC export using IfcOpenShell Java bindings
2. Export generated wall with openings to .ifc file
3. Reimport and compare bboxes

---

# DATABASE

```
DB: /home/red1/IfcOpenShell/WORK_DIR/databases/enhanced_federation_GI.db
51,723 elements, 234MB
```

# KEY FILES

- SESSION_STATE.md - All patterns, phase progress
- workdiary.txt - Work history, test results
- CONFIG.md - Paths
