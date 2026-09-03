# DONE 0a43a674
# Rebar Java Port — Federation Standards to ForgeEngine

**Spec:** `docs/FORGE_SUITE_SRS.md` §10 Phase 7
**Source:** Federation `rebar_generator.py` + `rebar_standards.py`
**Priority:** Phase 7

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Port existing Python formulas to Java ForgeEngine.
The standards tables (MS 1347:2020, BS 8110, Eurocode 2) are the asset —
they are DONE in Python. The Java port is mechanical translation.

## Read first

1. `docs/FORGE_SUITE_SRS.md` §10 Phase 7
2. Federation rebar generator:
   `/home/red1/IfcOpenShell/src/bonsai/bonsai/bim/module/federation/structural/rebar_generator.py`
   (678 lines)
3. Federation rebar standards:
   `/home/red1/IfcOpenShell/src/bonsai/bonsai/bim/module/federation/structural/rebar_standards.py`
   (540 lines) — ConcreteGrade, ExposureClass, BarDiameter, CoverRequirements,
   SlabReinforcementRules, BeamReinforcementRules, ColumnReinforcementRules
4. `BIM_COBOL/src/main/java/com/bim/cobol/forge/ForgeEngine.java` — interface to implement
5. `BIM_COBOL/src/main/java/com/bim/cobol/forge/SlopeCutForge.java` — pattern to follow

## Task

### A. RebarCageForge implements ForgeEngine

Piece type: `REBAR_CAGE`

Grammar: `FORGE REBAR_CAGE type:SLAB grade:GRADE_30 exposure:XC1 width:6000 depth:4000 thickness:200`

Port the Python formulas:
- Slab reinforcement: min ratio 0.13%, max spacing 300mm main / 400mm dist
- Beam reinforcement: min tension 0.13%, stirrup spacing 0.75 × effective depth
- Column reinforcement: min ratio 0.8%, max ratio 4%, link spacing 12 × bar diameter

### B. Standards tables as Java enums

Port from `rebar_standards.py`:
- `ConcreteGrade` enum (GRADE_20 through GRADE_45)
- `ExposureClass` enum (XC1-XC4, XD1-XD3, XS1-XS3)
- `BarDiameter` enum (T6 through T40) with area/weight calculations
- `CoverRequirements` per MS 1347:2020 Table 4.2

### C. Register in ForgeVerb

Add `register(new RebarCageForge())` in ForgeVerb constructor.

### D. Tests

- W-FORGE-9: Slab rebar for 6m × 4m × 200mm → correct bar count + spacing
- W-FORGE-10: Column rebar for 400mm × 400mm → correct ratio + link spacing
- W-FORGE-11: Exposure class XS3 → increased cover requirement

## What NOT to do

- Do NOT modify the Python Federation code
- Do NOT add external dependencies
- Do NOT implement mesh generation (that's ForgeMesh's job)
- Do NOT change the ForgeEngine interface

## Verify

1. `mvn compile -q` — PASS
2. W-FORGE-9, W-FORGE-10, W-FORGE-11 — PASS
3. SH 7/7 no regression

## Commit message

```
[S##-forge] RebarCageForge — port Federation rebar standards to Java

MS 1347:2020 + BS 8110 + EC2 standards as Java enums. Slab/beam/column
reinforcement rules. ConcreteGrade, ExposureClass, BarDiameter, CoverRequirements.
W-FORGE-9..11.
```
