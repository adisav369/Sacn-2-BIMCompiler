# S145 — InterimWorkshop: Envelope Trim via Workshop Sub-Verbs

**Spec:** `docs/BOMBasedCompilation.md` §6.1, `docs/DISC_VALIDATION_DB_SRS.md` §6.12.2
**Prior work:** S144 (GEO white-box logging, envelope overshoot detection)
**Prereq:** S144 DONE (GeoProofRecord with ENVELOPE_OVERSHOOT flagged)

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Query the database. Copy patterns you find. Compute positions via verbs. Never invent.

## Problem

Products in the catalog are standard rectangular LODs (uncut). The extraction
yields the pre-Boolean AABB extent. The compiler places them uncut. S144's
ENVELOPE_OVERSHOOT proves specific elements overshoot the building envelope.

InterimWorkshop currently handles 1D length adjustment for pipes (§6.12.2 §6).
This session extends it to 2D/3D envelope trim — the first real spatial
reasoning beyond round-tripping coordinates.

**Evidence (SH, S143):**
- WALL_EXT_NS_290x3358: top Z=3.828m, roof base Z=3.0m → +828mm overshoot
- WALL_EXT_EW_290x2821: top Z=3.291m → +291mm overshoot
- Curtain wall assembly: top Z=3.221m → +221mm overshoot
- The curved roof has ~70 unique Z levels (not flat)

**Construction parallel:** You order rectangular panels. Workshop drawings say
"cut top to match roof profile at location X." The cut is a fabrication
instruction, not a product property.

## Read First

1. `CLAUDE.md` + `PROGRESS.md` §Current State
2. `docs/BOMBasedCompilation.md` §6.1 (Workshop Verbs)
3. `docs/DISC_VALIDATION_DB_SRS.md` §6.12.2 §6 (InterimWorkshop)
4. `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/InterimWorkshop.java`
5. S144 GeoProofRecord — ENVELOPE_OVERSHOOT elements are the input to this session

## Design

See BBC.md §6.1 for full spec. Summary:

- Root verb (PLACE/CLUSTER/LINE) = WHERE the product goes
- Workshop sub-verb (CUT_TOP/CUT_BOTTOM/NOTCH) = HOW to fabricate
- M_AttributeSetInstance carries per-instance cut parameters
- InterimWorkshop computes the Boolean subtraction from envelope geometry

## When Done

Prepend `# DONE` to this file. Update PROGRESS.md §S145.
