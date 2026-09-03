# RESUME — FACING / Up-Front-Left frame

## ⚠ DO NOT REMOVE — scope + standing rules (oft-repeated; honour every session)
Fix the SOURCE (IFC2BOM + component-library extraction), never patch the drop/test. NON-INVENT: a face/up
is EXTRACTED from IFC metadata or mesh, never synthesised. Hard-fail (no fallback) on missing data. Read the
Java + IFC spec FIRST. [[feedback_read_java_spec_first]] Whitebox §-log / JUnit witness is the proof — NOT a
browser, NOT a self-referential in-file oracle. The fidelity oracle is the REAL Java RosettaStone /
`geo_verify` / the captured IFC value — NEVER hand-roll a reconstruct yardstick (2026-06-21 false-drift lesson).
Deploy: `git -C ~/bim-ootb fetch origin` FIRST (stale-checkout trap), work in `/tmp/wt-*` off origin/main, bump
sw `CACHE_VERSION` + the asset `?v=`, PR→auto-merge→VERIFY LIVE by fetching the served file.

## ✅ DONE — Fix 1 + end-to-end drop + LIVE deploy (2026-06-22)
- **Fix 1 (capture) DONE/PUSHED** bim-compiler `lane/benchmark-clash-resolution` `b9c08478`. Root was DATA
  STALENESS not missing capture: current extractor `DAGCompiler/python/extractIFCtoDB.py` (S172,
  USE_WORLD_COORDS=False) already captures real IFC ObjectPlacement yaw → `element_transforms.rotation_z`
  (stamped `transform_source='ifc_extract'`). `ExtractionPopulator`: `RawElement`+=`Double rotationZ`(+13-arg
  compat ctor); `readReferenceDb` LEFT JOIN element_transforms freshness-gated by `transform_source`;
  `FRONT_SOURCE`=rotationZ==null?null:literal-radians; deriveRows passes v1 EW/NS run-axis bucket to resolver
  (product naming byte-identical → 0 Rosetta drift), facing radians → rotation_rule only. Witnesses
  `ExtractionFacingCaptureTest` W-FACING-CAPTURE 4/4 + shipped `ExtractionFacingGateV2Test` W-FACING-GATE 5/5.
- **End-to-end + LIVE** `e576ba38` (`scripts/witness_drop_facing.js` W-DROP-FACING): re-extracted SH+DX →
  `run_RosettaStones.sh classify_{sh,dx}.yaml` → `{SH,DX}_BOM.db` w/ real rotation_rule →
  `extract_dagevu_catalog.py` → catalog `SH::`/`DX::` sets. Drop reproduces IFC yaw 0 mismatch keyed by
  bom_id|pid. DEPLOYED bim-ootb PR#494 (`a105416`, sw v702, catalog ?v=8, geom ?v=7); LIVE-VERIFIED: SH chairs
  face 37/90/-90/0/-35°, DX mirror B-unit bed=90°.
- **Foundation resync DONE:** re-extraction's 1388 dangling component_geometries refs → 0 (clear SH/DX
  I_Geometry_Map+M_Product_Image, re-run Rosetta re-imports fresh meshes). Local-only (component_library.db=LFS).
- **Fix 1b (UP) = NOT a defect:** all SH rot_x=rot_y=0, IFC UP=Z correct → a UP gate would fire on correct data.

## ✅ DONE — Gate reconciliation (task #1, 2026-06-22 session 2)
**SH now 9/9 ALL GREEN; DX 9/10 (IfcCovering binding fixed, only the geo_verify task-#2 gate red).** Two root
causes, both DATA-staleness from the facing-session resync, both fixed NON-INVENT + reproducibly:
- **(1) Generative-device geometry — the actual SH/DX `MetadataMissing` chain.** The 2026-06-22 foundation
  resync rebuilt `I_Geometry_Map` to hold ONLY freshly re-extracted SH+DX rows (449), DROPPING the generative
  MEP device type-rows (Fancoil/AIRCON_POINT, fans, sprinkler, diffuser, outlets, floor-trap) the discipline
  callout + CL_001 depend on, AND deleting the meshes 6 surviving M_Product_Image rows pointed at (→ dangling).
  FIX = `scripts/restore_generative_meshes.py` (W-GEN-GEOMAP-RESTORE, wired into run_RosettaStones pre-flight
  BEFORE CL_001): re-seeds each device's real mesh + type-level I_Geometry_Map row from the genuine master
  `ad_geometry_map` OR (where absent there) the committed source extraction that originally provided it —
  SPRINKLER+SUPPLY_DIFFUSER←HHS_Office, OUTLET_20A/GFCI←Clinic, FLOOR_TRAP←jkrAR16 master, Fancoil/fans/
  toilet/sink/light←master. Blobs copied verbatim (counts = len/12); 0 unresolved for SH. Drops dangling
  M_Product_Image so CL_001 re-seeds. Idempotent. (component_library.db = LFS local-only → the SCRIPT is the
  durable reproducible artifact.)
- **(2) IfcCovering unit-prefix binding** (the card's old `A_<guid>` item). `A_`/`B_` are UNIT prefixes
  (UNIT_A/UNIT_B mirror, Duplex) — geometry is stored under the BARE 22-char baseGuid. `MeshBinder.bind` Step 1b
  looked up by `p.elementRef()` (the 24-char `A_<guid>`, len≠22 → skipped) → hard-fail though the mesh exists.
  FIX = look up by `p.baseGuid()` (strips the unit prefix; units A/B share the base mesh, walker applies mirror).
  One-line-correct, SH (bare guids) unaffected → still 9/9.
- **C8/C9 now PASS** (the card's `unk=33/61` were never gate FAILs — `unk` is the C9 position-match unknown
  count, reported alongside a PASS).
- **geo_verify.py parser RESTORED** (was stale: GEO log field renamed `LBD=(x,y,z)` → `→ AABB X=[..]Y=[..]Z=[..]`;
  parser now reads the AABB-MIN corner = the identical LBD value, old-format compat kept). This re-enables the
  task-#2 oracle. ⚠ It now surfaces broad compiled-vs-extraction position DRIFT on DX (and SH, 9.7m worst on
  same-name walls) that CONTRADICTS the sanctioned canvas zero-drift proof (`rosetta_canvas_sh.js` SH 55/55
  0.000mm) — i.e. a geo_verify COMPARISON/frame issue (cross-unit bare-guid collision + frame), NOT a real
  compiler drift and NOT my parser (Check A: GEO==output 65/65 within 1mm). This is the task-#2 investigation —
  reconcile against the REAL Java GEO oracle, do NOT hand-roll (2026-06-21 false-drift lesson). Left red on DX.

## ⚖ §JAVA VERDICT (user asked 2026-06-23: "was our Java perfect?") — **YES, LOCK THE COMPILER.**
The spatial-compilation core (BOM walk, anchor/placement math, LBD relative-offset, Rosetta G1–G6 round-trip)
was UNTOUCHED this session and is proven correct three ways: (1) every gate failure was the Java being
CORRECTLY STRICT — `MetadataMissingException` hard-fails were it REFUSING to fabricate geometry a prior resync
deleted from the DATA (cure = restore data, not change Java); (2) C8/C9 dimension fidelity PASS, G1–G6 PASS;
(3) geo_verify's "drift" CONTRADICTS the sanctioned canvas proof (SH 55/55 @0.000mm) + Check-A compiled==GEO
within 1mm → the HARNESS is stale, the compiler OUTPUT is right (drift is evidence FOR the Java).
**The ONE Java edit was NOT in the compiler** — it's one line in `MeshBinder` (the LOD mesh-ATTACH layer that
drapes a library mesh inside a bbox the compiler ALREADY computed). For Duplex mirrored units the placement ref
carries a unit prefix (`A_`/`B_`); the binder looked up the 24-char prefixed ref instead of the bare 22-char
`baseGuid` it already held → skipped → hard-failed. Fixed to `p.baseGuid()`. It touches ZERO position/dimension
math (turns a hard-fail into a successful attach, multi-unit only, SH unchanged). Core stays sovereign. So:
**compiler = perfect/locked; the only fix was a downstream binding lookup for mirrored units + DATA + TOOLING.**

## 🔬 §DROP DIAGNOSTIC VERDICT (2026-06-23, real-oracle diff — answers "is the drop a show-stopper?")
**Root LOCALIZED to the CATALOG EXTRACTOR, top of cascade — NOT expandAssembly, NOT the compiler.**
The droppable catalog `BUILDING_SH_STD` is built from the GENERIC `library/archive/BOM.db` template: its children
are structural shells only (`SH_GF_STR/SH_ROOF_STR/SH_CW_STR`) with placeholder box dims (walls d=1.0,h=1.0) and
ZERO furniture. The REAL building (`library/SH_BOM.db`, the SAME DB the proven compiler walks) is
`BUILDING_SH_STD → SH_UN_STR → SH_1_LIVING_ROOM_SET / SH_2_BEDROOM_SET / SH_3_ENTRANCE_HALL_SET` with REAL
furniture (`Chair - Dining` 0.43×0.44×**1.227**, real ring positions) → compiler round-trips it to ZERO DRIFT.
The user's "small dining set" = the SEPARATE generic `DINING_SET` catalog entry (0.45³ placeholder CUBES, hand-grid,
no real facing); "geometry hell" = archive placeholder box dims. The tautology that hid this: `witness_drop_vs_java.js`
`oracle()` is a JS transcription of the placer reading the SAME catalog as `dropLeaves` → any catalog error cancels.
**NOT a scary show-stopper:** real data EXISTS + is PROVEN (compiler consumes SH_BOM.db). FIX = `scripts/extract_dagevu_catalog.py`
must source the droppable building cascade from `SH_BOM.db` VERBATIM (the PER_BLDG merge already proves the mechanism),
not the archive template; witness drop-leaf AABBs == compiler `output.db` (samplehouse.db) leaf AABBs (the REAL
independent oracle), NOT catalog-vs-itself. Bounded, one script, converges to a known-correct target. Locked core untouched.

## ✅ DROP FIX PART 1 — geometry hell ELIMINATED (2026-06-23)
**Real-oracle witness built + archive stub retired.** New `scripts/witness_drop_vs_compiler.js` (W-DROP-VS-COMPILER)
compares the drop to the PROVEN compiler `output.db` (samplehouse.db), NOT the catalog (killed the tautology).
It PROVED there were TWO `level=BUILDING` "Sample House"es: the generic archive `BUILDING_SH_STD` (AXIS-SCRAMBLED —
0.47m slab rendered 8.6m tall, walls on edge — + placeholder 0.45³ cube chairs = the geometry hell) and the real
`SH::BUILDING_SH_STD` (every element dim matches compiler to the mm). FIX in `extract_dagevu_catalog.py`: retire any
generic archive assembly with a real per-building `XX::` twin, then PROMOTE the real building to the clean `BUILDING_XX_STD`
id so all witnesses/modeller/deep-links resolve to it. Result: `BUILDING_SH_STD` now = real building (walls 4.45×0.29×2.47,
slab 16.87×8.67×0.47, roof/plate all match compiler). Tautological `witness_drop_vs_java.js` DEPRECATED→stub.
**RESIDUAL (next):** positional SPREAD — drop extent 18.87×13.00×4.96 vs compiler 16.87×8.68×3.95 (ratio 1.12/1.50/1.26),
+ inflated coverings (7.97×9.31 vs 4.45×3.46), + missing elements (WINDOW 1 vs 4, FURN 11 vs 14). Elements right,
ROOM POSITIONS over-spread (synthesized envelope). That's the bounded ~1.12m layout gap — converge each via the
compiler oracle. ⚠ `witness_modeller_drop.js` has a stale PIANO content assertion (archive-era) → update or retire.

## 📌 DESIGN RATIONALE (user footnote 2026-06-23) — WHY drop the BOM, not extracted.db
The modeller drops the BOM (catalog derived from `*_BOM.db`), NOT the cooked `*_extracted.db`, ON PURPOSE: the BOM
carries READY-MADE, ADDRESSABLE LAYERS (building→floor→room→set→leaf) that are easy to EDIT. extracted.db is flat
cooked geometry — not editable layers. **Consequence for task #2:** do NOT close the spread by copying absolute
positions from extracted.db (that kills editability). The compiler PROVES the BOM layers already carry enough to
reconstruct the real building (it reads the same `SH_BOM.db` → zero drift), so converge the DROP to the COMPILER
(compose the BOM offsets the way `PlacementCollectorVisitor` does) — that preserves every editable layer AND yields
the real layout. The witness oracle stays the compiler `output.db`, never extracted.db.

## §SPEC — DROP FIX PART 2 (2026-06-23, this session): per-instance allocated dims closes the spread
**DIAGNOSIS (real-oracle, NON-INVENT — read `output.db` + `SH_BOM.db`, never the catalog):** the over-spread has
ONE driver, and the "missing elements" have a SEPARATE upstream root. Two distinct findings:
- **(A) over-spread + inflated coverings = ONE cause, fully in the extractor.** `CEILING_57t` is placed 3× in
  `SH_GF_STR` at DISTINCT instance sizes carried by the BOM line's `allocated_width/depth/height_mm`
  (9.31×5.66, 4.45×1.95, 4.45×3.46) — EXACTLY the compiler's 3 coverings. The catalog collapsed them to ONE
  product box, AND that box was the WRONG one: DX's `CEILING_57t` (7.97×9.31) shadowed SH's via
  `mprod_extra.setdefault` (cross-building leaf-id COLLISION; per-bldg merge prefixes bom_ids + nested refs but
  NOT leaf product_ids). PROVEN the d=9.308 covering is the SOLE element extending past the slab → it alone makes
  extent 18.87×13.00 vs 16.87×8.68. FIX (extractor only, NON-INVENT, preserves editable layers): override each
  LEAF child's box to its line's `allocated_*` dims whenever they differ from the resolved product (>1mm), via a
  per-instance synthetic product (asmOnly, reuses base mesh — same pattern as ROLE__ products). This fixes the
  covering dims, their LBD-corner centres (half-extent uses the box dims), the over-spread, AND every
  cross-building dim collision in one stroke (the instance's allocated dims are SH's truth, on the line).
- **(B) missing qty>1 instances = the DROP discarded `verb_ref` (CORRECTED — was wrongly blamed on BOM-build).**
  My first read ("positions LOST, compiler reads extracted.db, fix upstream") was WRONG and the user caught it on
  the red line. TRUTH (Explore + Java read): the compiler reads ONLY the BOM (`CompilationPipeline`/
  `PlacementCollectorVisitor`, never an extraction db at compile time). A factored leaf carries qty=N + a
  **`verb_ref`** (`TILE:2:2:0.70:1.0125`, `CLUSTER:…`, `LINE:X:…`) written by `VerbFactorizer` that encodes the N
  real placements (+ per-instance GUIDs in `m_bom_line_ma`); the compiler reconstructs them via
  `PlacementCollectorVisitor.expandVerb`. The BOM IS self-sufficient — red line intact. The DROP simply threw
  `verb_ref` away → 1 box per line. FIX (extractor, NON-INVENT): port `expandVerb` to Python and expand each
  verb leaf into N real editable leaf children (own LBD offset; CLUSTER carries per-instance dims) → the proven
  host places them unchanged. Comparing to `extracted.db` is a black-box post-compile test that can collude (user
  decree) — the witness oracle is ONLY the compiler `output.db`, and the proof is the drop running the compiler's
  OWN verb algorithm over the BOM.

## ✅ DROP FIX PART 2 — over-spread CLOSED (2026-06-23, bim-compiler `76948b7e`, branch lane/benchmark-clash-resolution, PUSHED)
**Root A DONE.** `extract_dagevu_catalog.py` PASS 1.5: per-instance leaf box dims from the line's `allocated_*_mm`
(NON-INVENT, the editable layer) via asmOnly synthetic products → fixes inflated coverings + their LBD centres +
the W/D over-spread + cross-building leaf-id dim collision (DX `CEILING_57t` 7.97×9.31 shadowing SH). **W-DROP-VS-COMPILER
extent ratio 1.12/1.50/1.26 → 1.00/1.00/1.00** (SH AND DX); `§GEO_SUMMARY` DRIFT=0 worst=0mm; W-MODELLER-DROP host==oracle
0.000mm + PIANO 0.04mm vs compiler oracle; W-DROP-FACING 25/25 unaffected. Also fixed the witness Z-model artifact
(drop seats box BASE at z, not centre → H 1.26 was harness-only) + re-anchored the stale FURN_PIANO hand-calc to the
compiler output.db oracle. 520 instance products; geom unchanged (meshes shared by hash). NOT deployed to bim-ootb yet.

## ➡ NEXT SESSION STARTS HERE (task #2 PART 2 — remaining)
1. ~~Gate reconciliation~~ ✅.  2. ~~Drop geometry hell~~ ✅.  3. ~~Over-spread + inflated coverings (A)~~ ✅ (above).
4. ~~qty>1 missing instances (B)~~ ✅ DONE this session (verb_ref expansion port). Drop now = compiler EXACTLY:
   65 leaves == compiler's 65 placeable elements; per-class count + position congruence ≤0.07mm (W-DROP-VS-COMPILER
   is now a HARD GATE: §POS-CONGRUENCE PASS, exit 0). Only FlowTerminal 43 (compiler-GENERATED MEP) absent — correct
   for a BOM drop. The BOM round-trips 1:1, no extracted.db.
5. **DEPLOY** the regenerated catalog to bim-ootb (verb-expansion adds the missing instances on top of the v705
   per-instance-dims deploy): /tmp/wt-* off origin/main, bump sw `CACHE_VERSION` + catalog `?v=`, PR→auto-merge→
   verify served file.
2. **Layout gap (the ~1.12m, SESSION-2):** `BUILDING_*_STD` structural shell still from generic
   `archive/BOM.db`; furniture SETs placed in a SYNTHESIZED envelope (documented ceiling). To close absolute-
   position congruence: source the real per-building structural shell + room positions. See
   `prompts/RESUME_DROP_VS_JAVA_STRICT.md`. ⚠ Oracle = real Java GEO log / geo_verify, correspondence-free
   all-pairs; the existing `witness_drop_vs_java.js` S1 is a TAUTOLOGY (compares catalog to itself) — flip it.

## CONSISTENCY TRAP (carry forward)
Radian `rotation_rule` is only correct paired with LOCAL-coord meshes (current extractor). Stale world-coords
mesh + radians = DOUBLE-rotation. Always deploy catalog + geometries as one freshly-regenerated pair.

## WHERE WE ARE (DONE this session — committed+pushed on bim-compiler `lane/benchmark-clash-resolution`)
**Fix 2 = the GIGO hard-fail gate is BUILT + proven** (commits `bcaa5621` then widened `84041fd0`):
- `IFCtoBOM/.../ExtractionPopulator.java`: `classifyOrientationV2` + `FACING_GATE_V2` (ON by default) +
  `FacingNotCapturedException`. Any element WITH a front (walls/plates inward-vs-outward + directional
  furniture) MUST carry a REAL captured front, else HARD FAIL — no `"0"` fallback. AABB `EW/NS` no longer
  counts (can't tell inward/outward). `extract()` collects ALL offenders, fails once. v1
  `classifyOrientation` retained for `FACING_GATE_V2=false`.
- Seam to fill: `static Function<RawElement,String> FRONT_SOURCE` (default `null` → everything hard-fails today,
  which is the intended GIGO exposure — nothing real is captured yet).
- Witness `IFCtoBOM/src/test/.../ExtractionFacingGateV2Test.java` W-FACING-GATE 1..5 = 5/5.
  RUN: `cd IFCtoBOM && mvn -o test -Dtest=ExtractionFacingGateV2Test -Dpipeline.tests.skip=false`
  (tests are skip-by-default via `${pipeline.tests.skip}`; the module builds offline, BUILD SUCCESS).

## WHY (the root, proven this session — don't re-derive)
A piece's facing is captured NOWHERE: `element_transforms.rotation_z = 0` for EVERY SH and DX element (facing is
baked in the MESH, not the transform), and all ~24,000 `component_definitions` rows are schema-default
`up_axis=Z / forward_axis=Y / default_rotation=0`. v1 silently wrote `rotation_rule="0"` → chairs face one way,
a 180°-flipped wall passes with brick facing inward. The modeller box-proxy then has no orientation at all.
Same IFC2BOM code for SH & DX; the difference was DATA (DX BOM has EW/NS on WALLS only; furniture facing absent
in both). [[feedback_read_java_spec_first]]

## NEXT — Fix 1 (capture FRONT) + Fix 1b (capture UP)
1. **Fix 1 — fill `FRONT_SOURCE` from REAL data** so fronted elements pass:
   - WALLS: IFC material-layer-set direction (`LayerSetDirection`/`DirectionSense`) OR space boundary (which side
     the `IfcSpace` sits on) → inward/outward. (Needs `RawElement`/extraction to carry the layer-set or boundary.)
   - FURNITURE: component `forward_axis` (once real) OR derive the front normal from the MESH (component_geometries
     vertices/normals). The front is baked in geometry — extract its principal front, NON-INVENT.
   - Then real SH/DX builds go GREEN instead of hard-failing.
2. **Fix 1b — the UP frame (sibling gate, DIFFERENT table)**: "even a round table's top must be up." UP lives in
   `component_definitions.up_axis` (component-library stage), NOT `rotation_rule` (BOM line). Populate up_axis/
   forward_axis per component from IFC/mesh + hard-fail on schema-default. Round = yaw-free but UP required.
3. **Rectangular vs round table**: needs geometry to tell (round = no horizontal front; rect = has one). Decide in
   Fix 1 from the mesh; today `hasFront` conservatively does NOT require furniture front unless role is directional.
4. **Authoritative "has a front" signal** to wire into `hasFront`: `ad_product_dim.clear_front > 0` /
   `ad_placement_rule.clearance_front_m` (already in the schema).

## §FIX-1 SPEC (resolved + implemented this session — the root was DATA STALENESS, not missing capture)
**Discovery (empirical, NON-INVENT):** the *current* extractor `DAGCompiler/python/extractIFCtoDB.py` (S172
iterator path, `USE_WORLD_COORDS=False`) ALREADY captures real per-element yaw from the IFC ObjectPlacement into
`element_transforms.rotation_z`, stamped `transform_source='ifc_extract'`. Re-extracting `Ifc4_SampleHouse.ifc`
→ `/tmp/SH_reextract.db` gave the 4 dining chairs `0, π, 0, π` (facing each other across the table), the 6th-seat
chairs `±π/2`, bed `-π/2`, desk `π`, coffee table `π/2` — REAL varied facing. The deployed
`SampleHouse_extracted.db` (Jun-16) has `rotation_z=0` everywhere because it was extracted by an OLDER (world-coords)
extractor that baked facing into the mesh and zeroed the transform. **So facing IS captured — the DB was stale.**

**rotation_rule format:** `LocalCoord.resolveRotation` parses a *literal radian string* (`Double.parseDouble`), so
the captured `rotation_z` is directly usable as `rotation_rule`. NON-INVENT: it is the exact IFC-captured yaw.

**Discriminator (captured vs not):** presence of an `element_transforms` row stamped `transform_source='ifc_extract'`
— NOT the value (rotation_z=0 is a VALID captured front). This also fences out STALE all-zero DBs whose
`element_transforms` lacks `transform_source` (older schema had `bbox_*` cols, no `transform_source`).

**Implementation (ExtractionPopulator.java):**
1. `RawElement` += `Double rotationZ` (null = no captured placement) + a 13-arg compat ctor (delegates rotationZ=null,
   so the shipped W-FACING-GATE witness compiles unchanged and still hard-fails as designed).
2. `readReferenceDb`: `LEFT JOIN element_transforms t ON m.guid=t.guid` guarded by table-exists +
   `transform_source` non-null (freshness gate) → set `rotationZ`.
3. `FRONT_SOURCE` default: `e.rotationZ()==null ? null : String.valueOf(e.rotationZ())` (real radians or hard-fail).
4. `deriveRows`: SEPARATE the two concerns the single `orientation` field had conflated — pass the v1 run-axis
   bucket (`classifyOrientation` EW/NS) to `resolver.resolve` for PRODUCT NAMING (byte-identical to before → zero
   RosettaStone drift), while the row's `orientation` (→ rotation_rule) carries the captured facing radians.
**Consistency note (downstream):** radian `rotation_rule` is only correct paired with LOCAL-coord meshes (current
extractor). Mixing it with a stale world-coords mesh double-rotates → re-extract ref DBs when wiring the modeller drop.

## §FIX-1b FINDING (UP axis) — NOT a real defect for these models, no gate built (would be invention)
All ~24k `component_definitions` are default `up_axis=Z/forward_axis=Y` AND every SH element has `rot_x=rot_y=0`
(verified). In IFC, UP=Z is the correct convention and furniture is not tilted; the per-instance YAW (Fix-1) carries
the only real orientation. There is no captured non-Z up to extract here, so a hard-fail UP gate would fire on
correct data = GIGO inverted. Deferred until a model with real tilted placements exists (rot_x/rot_y ≠ 0).

## TRAPS / NOTES
- `SHPipelineTest` is PRE-EXISTING broken in this env (`m_bom_line.M_BOM_ID NOT NULL` at `BomHierarchyBuilder`,
  identical on pristine code via stash baseline) — NOT caused by the gate; the gate sits earlier (populate).
- Consumers that had the silent fallback: `CompositionBomBuilder` / `VerbFactorizer` (`orientation()!=null?:"0"`).
  The gate stops a fronted element before it reaches them; once Fix 1 lands, they receive a real front.
- Related strict lane (separate): `prompts/RESUME_DROP_VS_JAVA_STRICT.md §SESSION-2` (the drop witness was
  self-referential; SH drop ≠ real building by ~1m — that's the DOWNSTREAM symptom of this SAME missing-data root).
