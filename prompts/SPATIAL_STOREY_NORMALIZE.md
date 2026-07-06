# ⚠ DO NOT REMOVE — Extraction: normalize the storey field (MEP/STR reference levels pollute the spatial tree)
# Scope: the EXTRACTION/COMPILE step writes `elements_meta.storey` verbatim from each element's Revit
#        reference level, so MEP/STR reference planes ("Level 2 Ceiling", "Level 3 TOS") + "Unknown"
#        land as FLAT SIBLING storeys instead of nesting under the real building level ("Level 2").
#        This corrupts the Find Storey/Room lens trees (extra junk "storeys"; can't form a clean
#        parent→child chain for the depth model). FIX IS UPSTREAM (extraction), NOT the viewer.
# Rules: EXTRACT/COMPILE ONLY — never invent. Read the run log after every run. Spec before code.
#        Append-only migrations. Don't edit deploy/live/. Witness by re-querying the regenerated DB.

## ✅ The viewer side is already CORRECT (do not touch it for this)
The Find-lens depth model is done + live (bim-ootb v601: windowed parent-relative 0.2/solid/shine,
one shared `_drillSelect` reused by every category). It was verified correct on the **MEP/discipline
lens** (clean data → multi-layer 0.2 renders). The **Storey and Room** lenses look broken ONLY because
their trees contain these corrupted pseudo-storeys — confirmed by the user across both spatial lenses.
This work-order is the DATA fix that makes those lenses clean too.

## 🔬 EVIDENCE (read-only queries on the LIVE served DB `deploy/buildings/Hospital_meta.db`, 2026-06-05)
`elements_meta` cols: guid · ifc_class · element_name · storey · discipline · material_name · material_rgba · building.
Disciplines present: ARC · ELEC · FP · MEP · PLB · STR.

Real vs pseudo storey split:
```
real        46593
PSEUDO/MEP  16822      (~26% of 63415 — i.e. a quarter of the model is mis-levelled)
```
The pseudo-storeys (storey × discipline × count):
```
Unknown           ARC   9454     ← architectural elements with NO real level at all
Level 3 Ceiling   MEP   1874     ┐
Level 5 Ceiling   MEP   1539     │  "<Level N> Ceiling"  = MEP/ELEC reference plane
Level 2 Ceiling   MEP   1351     │   (should nest under "Level N")
Level 6 Ceiling   MEP    447     │
Level 1 Ceiling   ELEC    79     ┘
Level 3 TOS       STR    464     ┐
Level 5 TOS       STR    406     │  "<Level N> TOS" (top-of-steel) = STR reference plane
Level 2 TOS       STR    400     │   (should nest under "Level N")
Level 4/6/7/7A TOS STR  ~800     ┘
```
So two distinct defects: (a) **reference-level suffixes** ` Ceiling` / ` TOS` (and likely others) leak
into `storey`; (b) **`Unknown`** = elements that never got a level resolved.

## ▶ THE FIX (in the extractor — find it first)
1. **Locate the extractor.** Likely `tools/extract.py` and/or `scripts/extract_per_building.py` (grep
   for where `storey` / `elements_meta` is written, and how the Revit/IFC level/`IfcBuildingStorey` name
   is read). Read the code + its spec before changing anything. Cite the spec line in the diff.
2. **Normalize storey to the canonical building level.** Map a reference-level name to its base storey:
   strip the known reference suffixes (` Ceiling`, ` TOS`, and any siblings you find — ENUMERATE them
   from the data, do not assume just these two) so "Level 2 Ceiling"/"Level 2 TOS" → "Level 2".
   - Prefer a DETERMINISTIC source: if the IFC carries the real `IfcBuildingStorey` containment
     (`rel_contained_in_spatial_structure`/elevation), use THAT as the storey and treat the Revit
     reference level only as metadata. Suffix-stripping is the fallback when only the level name exists.
   - Keep the original reference-level name in a SEPARATE column (e.g. `ref_level`) if downstream needs
     it — don't destroy data, relocate it. (Append-only / additive schema.)
3. **Resolve `Unknown`.** 9454 ARC elements have no level. Derive it deterministically from geometry
   (element Z / `element_transforms.center_z` vs the storey elevations) or from spatial containment.
   If genuinely underivable for some, keep `Unknown` but LOG the residual count — never invent a level.
4. Do NOT special-case the viewer. The viewer groups by whatever `storey` says; fixing `storey` fixes
   Storey + Room lenses together.

## WITNESS (re-query the regenerated DB; §-log the numbers, read the log before concluding)
- `SELECT DISTINCT storey ...` → only real building levels remain (no ` Ceiling`/` TOS`).
- `PSEUDO/MEP` bucket count → **0** (or a logged, justified residual for true-Unknown only).
- Per-storey element counts are conserved (sum unchanged; the 16822 fold INTO their base levels, not lost).
- Spot-check: a known "Level 2 Ceiling" MEP duct now reports storey "Level 2"; its `ref_level` (if added)
  still says "Level 2 Ceiling".
- Re-serve the building and confirm the Find Storey lens lists ONLY real levels, each forming a clean
  storey → type → item chain (the depth model then renders 0.2 for intermediates, as it already does on MEP).

## DELIVERY
Regenerate the affected building DB(s) via the normal pipeline (no hand-editing the .db), witness as above,
then follow the deploy flow for the served `_meta.db`/`_extracted.db`. One bounded task: storey normalization.
