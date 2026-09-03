# S149 — DX MEP System BOMs: START/END Anchors + Walker Fixture Placement

**Prior work:** S148 (MEP-SPACE logging, DV037 anchor_end, DV038 C_BPartner, library README)
**Analysis:** `docs/DuplexAnalysis.md` §MEP Symmetry, `docs/BOMBasedCompilation.md` §6.12.2

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Query the database. Copy patterns you find. Compute positions via verbs. Never invent.

## S148 What Was Done

### MEP-SPACE diagnostic logging
- `emitMepSpaceLog()` in IFCtoERP.java — infers room function from furniture containment.
- 11 DX rooms classified: 2 KITCHEN, 2 BATHROOM (L1), 2 BATHROOM (L2), 4 HABITABLE, 10 EMPTY.
- 63/119 fixtures mapped, 360/785 pipes mapped. Perfect A/B mirror symmetry.
- Uses BIMLogger (not System.out). Grep `MEP-SPACE` in pipeline log.

### DV037: anchor_end on ad_space_type_mep_bom
- Added `anchor_end` column to existing 186-row table in ERP.db.
- Values: RISER (23 plumbing supply), STACK (5 waste drain), PANEL (158 electrical).
- This is the END of each fixture's route. START is implicit (the fixture itself).

### DV038: C_BPartner — manufacturer identity
- C_BPartner table in ERP.db: 3 rows (AUTODESK_REVIT, UNIV_AUCKLAND, SJTII_KLIA).
- C_BPartner_ID on M_Product (3096/3096 linked) and M_BOM (172 linked).
- Traces every product back to its originating IFC source.
- BBC.md §1 updated with C_BPartner entity mapping → PREFAB_ARCHITECTURE.md.

### Library README
- `library/README.md` rewritten: DB boundary table, MEP tables state, compilation flow.
- Rule: discipline metadata in ERP.db, LOD catalog in component_library.db (DATA_MODEL.md §6.3).

### Cleanup
- Removed premature `ad_mep_fixture_route` table (redundant with ad_space_type_mep_bom).
- Removed premature `seedFixtureRecipes()` (wrong BOM hierarchy — flat, not shim-rooted).
- Stale DX_extracted.db copies removed.

### Gate: DX 8/8, SH 8/8 — zero regression.

## S148 Key Learnings — Must Internalise

1. **DB boundary:** discipline/placement/routing tables → ERP.db. LOD catalog → component_library.db. Never cross. (DATA_MODEL.md §6.3)
2. **Logging:** Use BIMLogger always, never System.out. PATTERN level for forensic traces.
3. **No premature tables:** Check if existing tables already cover the need. DV037 was redundant — `ad_space_type_mep_bom` just needed one column.
4. **C_BPartner = WHO:** Three orthogonal dimensions: M_Product_Category (WHAT), C_BPartner (WHO), SpaceSize (HOW MUCH). (PREFAB_ARCHITECTURE.md)
5. **Abstract means local-to-room:** Fixtures don't know their building. Shim absorbs room position. Fixture carries only local standoff + orientation.

## Current State of ERP.db MEP Tables

| Table | Rows | Status |
|-------|------|--------|
| M_BOM (AD_Org>0) | 6 | System BOMs: FP/ELEC/ACMV/CW/SP/LPG_SYSTEM. **CW/SP/ELEC are EMPTY — no children.** |
| M_BOM (MEP_RECIPE) | 166 | Pipe chain recipes (162 DX + 4 RM). Shim-rooted, dx/dy/dz tack offsets. |
| M_Product (SHIM) | 11 | Phantom host-surface anchors per discipline × surface. |
| ad_space_type_mep_bom | 186 | Room type → fixture mapping + anchor_end. Populated. |
| ad_mep_anchor | 2924 | Building-specific anchor points (runtime). |
| ad_mep_pattern | 9 | CW + SP topology patterns (mined from TE). |
| ad_mep_laying_rule | — | **NOT YET CREATED** (§8a: gradient/slope). |
| ad_mep_fitting_rule | — | **NOT YET CREATED** (§8b: tee/elbow offsets). |
| ad_mep_riser_rule | — | **NOT YET CREATED** (§8c: vertical constraints). |
| C_BPartner | 3 | Manufacturer identity. All products linked. |

## Task 1 — Populate System BOMs with START/END Entries

The 6 discipline system BOMs (CW_SYSTEM, SP_SYSTEM, ELEC_SYSTEM...) are empty shells.
They need minimalist START and END anchor children — not the full pipe chains, just
the terminal points that the Walker routes between.

Per §6.12.2 §5:
```
CW_SYSTEM
  ├── CW_MAIN_RISER   (START — supply entry from below house)
  ├── SINK_CW          (END — anchor_end=RISER, per ad_space_type_mep_bom)
  └── TOILET_CW        (END — anchor_end=RISER)

SP_SYSTEM
  ├── SP_MAIN_STACK    (START — waste exit below house)
  ├── TOILET_WASTE     (END — anchor_end=STACK)
  └── FLOOR_TRAP_WASTE (END — anchor_end=STACK)

ELEC_SYSTEM
  ├── ELEC_MAIN_PANEL  (START — power mains near main door)
  ├── OUTLET_ELEC      (END — anchor_end=PANEL)
  └── LIGHT_ELEC       (END — anchor_end=PANEL)
```

The Walker then finds paths FROM start TO each end. The pipe chain recipes
(MEP_RECIPE BOMs) fill the route between them.

**Read:** DISC_VALIDATION_DB_SRS.md §6.12.2 §5 (shim root, joint piece children).

## Task 2 — Create the 3 Missing Rule Tables

Seed `ad_mep_laying_rule`, `ad_mep_fitting_rule`, `ad_mep_riser_rule` from the
spec values already written in DISC_VALIDATION_DB_SRS.md §8a-§8c.

These are metadata — the Walker reads them for constraint validation, not for BOM building.

## Task 3 — Verify Walker Consumes DX MEP Recipes

The 162 DX MEP_RECIPE BOMs were written by `buildMepBomRecipes()`. Verify they are
actually exploded during DX compile:
- Check output c_orderline: CW/SP should show fixture placements, not just pipe counts.
- locator_ref should show the room path (RE.GF.BT.CW.SINK).
- C_BPartner_ID = 1 (AUTODESK_REVIT) on all DX MEP products.

## Read First

1. `CLAUDE.md` + `PROGRESS.md` §Current State
2. `docs/DISC_VALIDATION_DB_SRS.md` §6.12.2 §5-§9 (shim root, slope, branching, risers, metadata tables)
3. `docs/BOMBasedCompilation.md` §1 (entity mapping — C_BPartner), §3.8 (locator_ref), §6.12.2
4. `docs/PREFAB_ARCHITECTURE.md` §Three Orthogonal Dimensions (Category × BPartner × SpaceSize)
5. `library/README.md` (DB boundary, MEP tables state)
6. Run `./scripts/run_RosettaStones.sh classify_dx.yaml`

## Gate

- DX: 8/8 PASS (must not regress)
- SH: 8/8 PASS (no regression)
- System BOMs populated: CW_SYSTEM, SP_SYSTEM, ELEC_SYSTEM each have ≥2 children
- MEP-SPACE log: 11 rooms classified (grep pipeline log)
