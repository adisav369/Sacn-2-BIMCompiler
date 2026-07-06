# INVESTIGATION — BOM-drop spatial child relationship (relative-vs-absolute offsets)

```
# ⚠ DO NOT REMOVE
SCOPE: FIX SESSION. The diagnosis is DONE — read §RESULT (bottom) FIRST. Symptom: modeller INSERT›BOM-catalog
drop of an ASSEMBLY places its children SCATTERED (~/Pictures/Screenshots/ModelBOMDrop.png). VERDICT (proven by
numbers + Java source): the extraction is CORRECT (dx/dy/dz are parent-corner-relative, no 2-month regression) —
the scatter is a BROWSER anchor-convention mismatch (children corner-anchored [0..W] vs the ghost box center-
anchored [-W/2..+W/2]). DO NOT re-run the extraction; DO NOT patch the browser to subtract absolute coords.
THIS SESSION = implement the browser-side fix, witness-first. DECISION TO MAKE WITH THE USER FIRST: (a) re-center
on drop (recommended) vs (b) filter BUILDING/FLOOR out of the droppable catalog — see §RESULT FIX SCOPE. The §1-§5
METHOD below is the (completed) investigation trail, kept for traceability. NON-INVENT, whitebox §-log is the proof.
```

## THE HYPOTHESIS (the user's, 2026-06-20)
The BOM child offsets `m_bom_line.dx/dy/dz` for BUILDING assemblies may be **absolute world coords** rather than
**parent-LBD-relative** offsets. If so, the browser fold DOUBLE-COUNTS (parent placement + an already-absolute
child offset) → children scatter. Suspected regression: **~2 months ago a session may have copied dx/dy/dz from a
SOURCE-EXTRACTED DB (absolute IFC coords) instead of the BOM-CALCULATED recipe (relative offsets).** Confirm or
refute with numbers.

## THE TWO SEAMS (cite when reporting)
- **Browser fold** — `bim-ootb/viewer/bonsai_library.js` `expandAssembly(id, placement, …)`: a child's world pos is
  `wx = px + (cos·ch.dx − sin·ch.dy)`, `wy = py + (sin·ch.dx + cos·ch.dy)`, `wz = pz + ch.dz` (parent placement +
  rotated child offset). This is CORRECT **iff** `ch.dx/dy/dz` are parent-relative. If they are absolute, every
  child is offset by the parent twice → scatter.
- **Intended convention** — `bim-compiler/scripts/compile_warehouse.js:109-111`: "verbs compute the offsets …
  dx/dy/dz per line follow the §4 tack convention (**parent-LBD-relative**)". The WAREHOUSE compiler honors this.
  The BUILDING BOM path is the suspect (different extractor — the Java/`extract.py` side, "the source we take from").
- **LBD cascade** — `compile_warehouse.js:210`: `lbd = [p[0]+ctx.line.dx, p[1]+ctx.line.dy, p[2]+ctx.line.dz]`.
  LBD (Linked Building Data) = the absolute coord = parent-LBD + relative line offset; it is the per-level cascade.
  Use LBD tracking to verify cascade accuracy at EACH nesting level (building→floor→room→fixture): the relative
  offset must be measured against the IMMEDIATE parent's LBD, not the site origin.

## JAVA SOURCE TO AUDIT (read, do not edit)
- `ORMSandbox/src/main/java/com/bim/ormsandbox/po/MBOMLine.java` — the dx/dy/dz columns: written relative or absolute?
- `ORMSandbox/src/main/java/com/bim/ormsandbox/BuildingInspector.java` — how lines are extracted/computed.
- `tools/sanity-checker/src/main/java/com/bim/tools/sanity/checks/BOMSpatialCheck.java` — existing spatial check:
  what invariant does it assert (relative? absolute?), and does it currently PASS on the building DBs in the shot?
- `tools/sanity-checker/.../checks/BOMCoverageCheck.java` — coverage sibling (cross-check).

## METHOD (whitebox §-log first, then the maths, then the Java)
1. **Reproduce + log:** drive the modeller assembly drop (the `Bathroom`/`PA Terrace`/Duplex assemblies visible in
   the catalog) headless; capture the §-log of `expandAssembly` — for each child print `parent.placement(px,py,pz)`,
   `child(dx,dy,dz)`, computed `world(wx,wy,wz)`. (Add a §-log line if absent — that's allowed for diagnosis.)
2. **The maths (the test):** for each child compute `Δ = world − parent.placement`. If `Δ ≈ child.dx/dy/dz` AND the
   children form a tight, sensible footprint → offsets are RELATIVE = correct (look elsewhere for the scatter). If
   `child.dx/dy/dz` ≈ the child's ABSOLUTE IFC coordinate (≈ world with parent≈0) and the spread ≈ the building
   extent → offsets are ABSOLUTE = the extractor is faulty. Quantify: report the actual numbers + which branch.
3. **Trace to source:** pull the SAME bom_id/child rows straight from the building DB (`library/component_library.db`
   or the served `*_extracted.db` / `bom.db`) and diff against the IFC source coords. Confirm whether the DB stores
   relative or absolute — and whether the served assembly JSON matches the BOM-calculated recipe or a source-extract.
4. **Date the regression:** `git log -p` the building-BOM extraction/compile path (and any `*_extracted.db` copy step)
   around ~2 months back; find the commit where dx/dy/dz semantics changed from BOM-calculated to source-extracted-copy.
5. **LBD cascade check:** verify the relative offset is taken against the IMMEDIATE parent LBD at every level (a
   deep child measured against the site origin instead of its room would scatter exactly like the screenshot).

## VERDICT TEMPLATE (what to return)
- RELATIVE-and-correct → scatter is elsewhere (rotation? wz seating? a wrong parent placement at drop time?) — name it.
- ABSOLUTE-leaked → cite the faulty extractor file:line + the regression commit/date + the rows that prove it, and
  scope the fix (re-run the BOM-calc extraction so dx/dy/dz are parent-LBD-relative; do NOT patch the browser fold
  to subtract — fix the source).

## §RESULT — VERDICT 2026-06-20 (read-only agent, numbers-grounded)
**RELATIVE-correct, hypothesis REFUTED. The Java extraction is NOT faulty; no 2-month regression.** dx/dy/dz are
genuinely **parent-corner-relative**, computed by subtraction per-level against the IMMEDIATE parent (LBD cascade
intact). The scatter is a browser **anchor-convention mismatch**, not bad data.

- **The maths:** `DX_L1_STR` (a FLOOR assembly, aabb 9.21×26.57×6.76m, 571 children) — child dx∈[0.37,8.98],
  dy∈[2.19,24.37]: spans == the floor's full aabb, centroid at (+W/2,+D/2) ⇒ **corner-anchored offsets in [0..W]**,
  NOT absolute (IFC source spans negatives, e.g. SampleHouse x∈[-7.74,6.24]; BOM dx∈[0,W] = that range shifted to a
  0-corner). `X_M_BOMLine.java:227 validateParentRelative()` PASSES (0 violations). Curated ROOM/SET assemblies are
  correctly tight (Kitchen maxAbs 1.8m, Bathroom 0.75m, Duplex/SH-GF sets 0.0) → they drop fine.
- **Source confirmed CORRECT:** `IFCtoBOM/.../StructuralBomBuilder.java` (BUILDING→FLOOR `makeDx=fMinX−allMinX` L273;
  FLOOR→ASSEMBLY `asmDx=aMinX−fMinX` L245) + `VerbFactorizer.java:161` (`dx=element.minX−parentMinX`). LBD measured
  vs immediate parent at each level, not site origin. `BOMSpatialCheck.java` is unrelated (rtree overlaps, not this).
- **The TWO real causes of scatter:**
  1. **Anchor mismatch (the bug):** children are CORNER-anchored ([0..W]) but the modeller ghost preview box is
     CENTER-anchored (`modeller.html:998` `[-asm.w/2..+asm.w/2]`). `expandAssembly` (`bonsai_library.js:119`) places
     each leaf at `drop + ch.dx`, so the whole set lands in the +X/+Y quadrant, offset from the drop point by
     ≈(+W/2,+D/2) ≈ (4.6m,13.3m) — not under the ghost.
  2. **A FLOOR is genuinely building-scale** (571 parts over 9×26m) — correct by design, just not a tight cluster.
     The screenshot's strewn slabs/beams are previously-dropped `*_STR` FLOOR entries behaving as authored (the
     status bar "Bathroom — PA Terrace (2 parts)" is only the armed pick).
- **FIX SCOPE (do NOT re-run extraction; do NOT patch browser to subtract absolutes):** a render/placement-convention
  alignment in the browser fold —
  (a) **re-center on drop:** in `expandAssembly`/`placeAssembly` (`modeller.html:1016`) place at `drop + ch.dx − W/2`
      so corner-anchored children sit under the center-anchored ghost (or make the ghost corner-anchored to match); and/or
  (b) **filter `bom_type IN ('BUILDING','FLOOR')` out of the droppable INSERT catalog** (or gate behind a "place at
      world origin" mode), leaving ROOM/SET assemblies (the coherent-cluster ones).
- **Files:** `~/bim-ootb/viewer/bonsai_library.js:119` · `modeller.html:998` (ghost) & `:1016` (placeAssembly);
  source-of-truth (correct) `~/bim-compiler/IFCtoBOM/.../StructuralBomBuilder.java` · `VerbFactorizer.java:161` ·
  `ORMSandbox/.../X_M_BOMLine.java:227`. **Next session = implement the fix (decide re-center vs filter-floors first).**

## NOTES
- Direct-manipulation lane is DONE (see `RESUME_MODELLER_P3.md`); this is orthogonal, about DATA correctness.
- `expandAssembly` itself looked correct on read (parent + rotated relative offset) — the suspicion is its INPUT.
- Run this as a read-only Explore/general agent; produce the maths + Java citation, then hand back a fix-scope.
```
