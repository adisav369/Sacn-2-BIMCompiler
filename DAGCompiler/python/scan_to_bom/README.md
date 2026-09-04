# scan_to_bom — Phase 2 through 5: segmentation, reunification, classification, instance merge, reference-DB writing, BOM assembly

Status: Phases 2, 2.5, 3, 3.5, 4, and 5 of the Scan-to-BIM roadmap (see `docs/ScanToBOM_ReferenceDB_Spec.md`
for Phase 1's schema contract this all feeds into and now writes to). Produces confidence-scored
geometric segments from a raw point cloud, reunifies fragments of one continuous physical surface,
assigns each segment an IFC-class-shaped label + discipline + placement from measurable geometry
alone, reunites disconnected fragments of one real furniture instance, serializes the result into
the exact reference-DB schema the existing IFC-side pipeline consumes, and — Phase 5 — runs that
straight through the real, **unmodified** `IFCtoBOMMain` Java pipeline (`ExtractionPopulator`,
`StructuralBomBuilder`, `ScopeBomBuilder`, `BomValidator`) to produce a real `*_BOM.db`, no Java
code touched — no material/RGB, no ML model. Nothing is silently invented: every segment and every
label carries a traceable confidence score, and low-confidence output is flagged, not hidden or
force-classified. `IfcBuildingElementProxy` — a real, standard IFC4 class — is the deliberate
"real object, type not determined" fallback for anything that doesn't clear a specific class's
evidence bar.

## Files

- `pointcloud_io.py` — ingestion. `.ply` (ASCII/binary), `.xyz`/`.txt`, and `.las`/`.laz` (via
  `laspy` + a LAZ backend — `lazrs` or `laszip`; both confirmed installable with native wheels for
  this environment, see "LAS/LAZ ingestion" below). Deliberately does not touch coordinate
  magnitude or read the LAS classification byte — see that section.
- `normalize.py` — coordinate normalization, between ingestion and segmentation. Computes and
  logs one "tack point" (bbox-center) per file, matching `extractIFCtoDB.py`'s own tack-point
  convention; see "Coordinate normalization" below.
- `segment.py` — the pipeline: iterative RANSAC plane extraction (floor/ceiling/wall/oblique),
  connected-component splitting per plane, DBSCAN clustering of everything left over, then
  `merge_coplanar_fragments()` (Phase 2.5) reuniting same-surface plane fragments by shared plane
  equation + proximity. Every tunable constant is a named module-level variable, not a magic
  number buried in logic.
- `classify.py` — Phase 3: assigns each Segment an IFC-class-shaped label (`IfcWall`,
  `IfcSlab`, `IfcRoof`, `IfcWindow`, `IfcDoor`, `IfcFurniture`, or `IfcBuildingElementProxy`),
  discipline (via a verbatim copy of `extractIFCtoDB.py`'s `DISCIPLINE_MAP`), and placement
  (`center`, `rotation_z`, `bbox`) — geometry-only rules, every threshold named and logged. See
  "Classification" below.
- `merge_instances.py` — Phase 3.5: reunites disconnected cluster fragments of ONE real object
  (a chair's legs + seat, each their own DBSCAN cluster) into one instance, now that Phase 3
  provides a predicted type. Only within the same type, and only for types where "same type"
  is real evidence — see "Instance merging" below for why that restriction matters.
- `gen_synthetic_pointcloud.py` — validation fixture generator (not part of the shipped
  pipeline). Samples points off Sample House's own real, already-extracted geometry
  (`DAGCompiler/lib/input/SampleHouse_extracted.db` + `library/component_library.db`'s mesh
  BLOBs), with configurable density and Gaussian scan noise, and writes a held-out ground-truth
  sidecar the segmentation code never reads.
- `validate_segmentation.py` — runs `segment.py` (+ merge) blind, then scores the output against
  the held-out ground truth: per-segment purity, coverage, fragmentation (plane vs cluster
  broken out separately — see "Validated results" below for why that split matters).
- `validate_classification.py` — runs the full chain (ingest → normalize → segment → merge →
  classify) blind, then scores predicted `ifc_class` against held-out ground truth via an
  equivalence set (e.g. predicting `IfcWall` for a true `IfcWallStandardCase` counts as
  correct). `IfcBuildingElementProxy` predictions are scored as "deferred," never as wrong.
  Specifically flags CONFIDENT-AND-WRONG predictions — a high `classification_confidence`
  paired with an incorrect label — as the failure mode that actually matters, distinct from a
  flagged low-confidence guess.
- `validate_instance_merge.py` — runs the full chain including `merge_instances.py`, blind,
  and scores on the two axes that matter: did merging reduce fragmentation of real furniture
  elements (the goal), and did it introduce any cross-contamination between different real
  objects (the failure the `CLUSTER_EPS_M` widening experiment in Phase 2 already showed is
  easy to hit by being too generous with proximity)?
- `write_reference_db.py` — Phase 4: serializes the final `list[ClassifiedSegment]` (post
  instance-merge) into the Phase 1 schema — `elements_meta`, `elements_rtree`,
  `element_transforms`, `element_instances`, `base_geometries` (a coarse box mesh per element,
  per the spec's decision #5 — mesh fidelity is explicitly deferred past v1), and
  `spatial_structure` (one `IfcBuilding` + one `IfcBuildingStorey`, since this pipeline has no
  multi-storey detection yet). See "Reference-DB writing" below.
- `validate_reference_db.py` — runs the full chain including `write_reference_db.py`, blind,
  then scores the WRITTEN DATABASE (not just the in-memory objects) three ways: schema/FK
  integrity, row-level classification accuracy against the same held-out per-point ground truth
  every earlier phase uses, and — new for Phase 4 — dimensional fidelity against the REAL
  `element_transforms` rows in `SampleHouse_extracted.db`, the actual source the synthetic cloud
  was sampled from. See "Reference-DB writing" below for what that check found.
- `run_scan_to_bom.py` — Phase 5's production entry point (not a validator). Runs the full
  chain and writes to `DAGCompiler/lib/input/<building_type>_extracted.db` — the exact
  hardcoded path convention `ExtractionPopulator`/`IFCtoBOMPipeline` already read a real IFC
  extraction from, so the existing, unmodified Java `IFCtoBOMMain` CLI treats this pipeline's
  output identically to an IFC extraction. See "BOM assembly" below.
- `validate_bom.py` — Phase 5's blind validator. Doesn't run either pipeline itself (both
  `*_BOM.db` files are built separately by the real Maven commands, see "Running it"); reads
  the two already-committed `*_BOM.db` files back and compares the point-cloud-derived one
  against the real, IFC-extracted `SH_BOM.db` — building envelope, QA-gate pass evidence, and
  per-class composition via the same equivalence set `validate_classification.py` uses.

## Running it

```bash
cd DAGCompiler/python/scan_to_bom

# regenerate the synthetic test cloud (only needed once, or after changing --density/--noise-mm)
python3 gen_synthetic_pointcloud.py \
    --db ../../lib/input/SampleHouse_extracted.db \
    --library ../../../library/component_library.db \
    --out ../../lib/input/pointcloud/samplehouse_synthetic.ply \
    --density 400 --noise-mm 3.0

# segment + score
python3 validate_segmentation.py --ply ../../lib/input/pointcloud/samplehouse_synthetic.ply --save-labeled

# classify + score
python3 validate_classification.py --ply ../../lib/input/pointcloud/samplehouse_synthetic.ply

# instance-merge + score
python3 validate_instance_merge.py --ply ../../lib/input/pointcloud/samplehouse_synthetic.ply

# write the Phase 1 reference DB + score the written file (schema, classification, dimensions)
python3 validate_reference_db.py \
    --ply ../../lib/input/pointcloud/samplehouse_synthetic.ply \
    --source-db ../../lib/input/SampleHouse_extracted.db \
    --out ../../lib/input/pointcloud/samplehouse_reference.db
```

`--save-labeled` writes a `.segmented.ply` sidecar with a per-point segment-id label, for
loading into any point-cloud viewer that supports custom vertex properties. Phase 2.5 merge runs
by default; pass `--no-merge` to score raw Phase 2 output for comparison.

For Phase 5 (running the point-cloud output through the real, unmodified Java BOM pipeline and
scoring the result against the real IFC-extracted BOM), see "BOM assembly" below — it needs the
Maven/Java commands, not just Python, so it's kept as its own command block there.

## LAS/LAZ ingestion + coordinate normalization

Confirmed working, not just "downloads successfully" — installed `laspy` 2.7.0 (pure-Python
wheel, zero platform risk) + `lazrs` 0.8.2 (native `cp314-win_amd64` wheel) and did a real
round-trip: wrote a synthetic compressed `.laz` with RGB, read it back through the actual
`load_pointcloud()` entry point, confirmed coordinates and RGB survive and that realistic
UTM-scale coordinates (~500,000m) pass through **untouched** — ingestion does not normalize.

**Coordinate normalization is a distinct step, `normalize.py`, deliberately not folded into
ingestion** — approved and implemented. `normalize_pointcloud()` computes one "tack point"
(bounding-box center) per file, mirroring `extractIFCtoDB.py`'s own `USE_WORLD_COORDS=False,
tack point = IFC origin` convention, and logs it (`§NORMALIZE` lines). Verified two ways:

- **On the synthetic (already building-local) cloud**: coverage/fragmentation scores are
  identical with and without normalization (58/58 coverage, 55/58 plane fragmentation, 21/58
  cluster fragmentation, all unchanged) — confirms it doesn't silently damage already-correct
  data. Segment count shows small variance (181→176, ~3%) from floating-point sensitivity in
  RANSAC's distance-threshold tests near a shifted origin, not a scoring regression — every
  metric that actually matters is unchanged.
- **On simulated realistic UTM-scale coordinates** (easting/northing ~6,543,000m, the range a
  real DeKH-style survey scan would use): raw `max|xyz|` = 6,543,115m, which would fail
  `WORLD_COORD_THRESHOLD_M`'s 500m gate outright. After normalization: 10.0m — comfortably
  under the gate. Confirmed fully reversible: `normalized.xyz + tack_point` reproduces the
  original coordinates exactly, so the transform back to real-world/survey coordinates is
  never lost — `save_tack_point()` persists it as a `.tackpoint.json` sidecar next to the
  point cloud file.

Pass an explicit `tack_point` to `normalize_pointcloud()` to align multiple point clouds to a
shared reference instead of each self-centering independently (site federation).

### Documented open items (not solved, written down so they aren't rediscovered blind)

- **Possible non-gravity-aligned Z.** Segment.py's horizontal/vertical plane classification
  assumes Z is true vertical. Tripod-mounted terrestrial scanners are normally self-leveled;
  SLAM-based handheld/drone capture often isn't. Unknown until a real DeKH file is inspected.
- **LAS classification byte** (ASPRS ground/building/vegetation/etc.) is a legitimate,
  extract-don't-invent signal if DeKH's point format carries it — real, already-measured data,
  not something to invent. `_load_las()` deliberately doesn't read it yet; worth checking DeKH's
  actual point format before Phase 3, since it could directly seed floor detection or discipline
  hints instead of being re-derived from scratch.
- **Scale/performance at real point counts.** The 670,965-point synthetic test takes ~24s to
  segment with the current unaccelerated vectorized-numpy RANSAC. Real building scans commonly
  run into the millions of points — likely needs voxel downsampling or spatial indexing before
  this becomes real-data-viable. Not an ingestion problem; will surface immediately once a real
  DeKH file is run through `segment_pointcloud()`.

## Classification (Phase 3)

Rules, all geometry-only, every threshold a named constant in `classify.py`:

- **Plane, floor-oriented → `IfcSlab`** (conf 0.9). **Oblique → `IfcRoof`** (conf 0.85).
  **Ceiling-oriented → `IfcSlab`** (conf **0.55**, deliberately below the 0.7 "confident"
  bar — genuinely ambiguous with `IfcCovering` without more signal, and flagged as such, not
  presented as certain).
- **Plane, vertical, large (≥2m² area, ≥1.5m height) AND reaching within 0.5m of the detected
  floor → `IfcWall`** (conf 0.9). Large-and-tall alone isn't enough — added the floor-touching
  requirement after finding it matters (see below).
- **Plane, vertical, small → `IfcDoor` if its bottom edge reaches the floor (±0.15m), else
  `IfcWindow`** (conf 0.6 either way — a real but simple heuristic, not a measurement).
- **Cluster, furniture-scale volume/height AND sitting on the floor (within 0.3m) → `IfcFurniture`**
  (conf 0.7). Everything else → **`IfcBuildingElementProxy`** (conf 0.3) — the honest "real
  object, unclear type" fallback, never a forced guess.

### Validated, and iterated on real evidence — not "looks right"

First blind run against ground truth: **20 confident-wrong predictions** (high
`classification_confidence`, wrong label) out of 176 segments. Didn't report that as done.
Traced the two biggest contributors directly against ground truth rather than guessing a fix:

- `IfcFurniture → IfcWindow` (11 cases) and `IfcFurniture → IfcWallStandardCase` (6-7 cases) —
  small wall/window fragments that landed in the cluster pool (missed RANSAC plane detection)
  and happened to fall inside the furniture volume/height envelope. **First hypothesis
  (flatness — these are thin fragments, not solid objects) was checked and was wrong**: printed
  their actual AABB extents and found genuine 3D box shapes (0.1-0.3m³, real depth in every
  axis) — window frame/sill/jamb geometry, not flat glass. Flatness filter barely moved the
  needle (20→19). **Second check (height above floor) found the real signal**: every one of
  these false positives sat 1.36-2.31m above the detected floor; real furniture sits on it.
  Added a floor-proximity requirement to furniture classification. Confident-wrong dropped
  **20 → 2** (90% reduction) on the re-run.
- The 2 remaining confident-wrong cases (`IfcWall → IfcPlate`) were checked individually: both
  are large (14.9m² / 21.1m² area) vertical planes that genuinely reach within 0.49m of the
  floor — they pass every geometric test a real wall would. They're structural roof-gable
  plates that happen to be wall-sized and floor-touching in this dataset. Genuinely
  indistinguishable from a wall using size + orientation + floor-contact alone; would need
  material or structural-vs-architectural context this pipeline doesn't have. Documented as a
  real, understood residual limit (1.1% of segments), not chased further with more geometric
  heuristics that would just be curve-fitting to two examples.

**Scored result at this point: 19/176 correct, 15/176 wrong (13 already flagged low-confidence
— only 2 confident-wrong), 142/176 (81%) deferred to `IfcBuildingElementProxy`.** The high
deferral rate here is a real trade-off, not automatically a shortfall — extract-or-compile-only,
never invent, means a geometry-only classifier that can't distinguish a chair from a table
*should* defer rather than guess. But "defer instead of guess" is only the right call when
deferring is actually necessary — Phase 3.5's work below found it wasn't, for furniture
specifically, and pushed the correct/deferred balance a long way further without weakening that
discipline. See "Instance merging" for the rest of this story — the furniture classification
numbers change substantially there, for reasons worth reading in full since two of them were
real mistakes, not just tuning.

## Instance merging (Phase 3.5)

Reunites disconnected cluster fragments of ONE real object — a chair's legs + seat, each its
own DBSCAN cluster since they're not point-connected — into one instance, now that Phase 3
provides a predicted type. Union-find on AABB proximity (`INSTANCE_MAX_GAP_M = 0.5`), same
`aabb_gap` helper Phase 2.5 uses, same safety principle: only merge within a category where
"same label" is real evidence.

### Restricting to a real signal — checked, not assumed

First run merged all same-type cluster pairs within the gap, including `IfcBuildingElementProxy`
fragments. Blind-scored result: purity violations *dropped* in aggregate (43→13), which looked
like an improvement — but checking individual merged segments found every single one of those
13 remaining violations was `IfcBuildingElementProxy`, some fusing up to 5 different real
elements into one "instance" (2,542 points, purity 0.76). The aggregate number was misleading:
143 small, mostly-pure fragments got consolidated into fewer, larger, worse ones. `Proxy` means
"unknown" — two nearby unknowns sharing that label is not evidence they're the same object, and
the data confirmed it. `IfcFurniture` showed zero cross-contamination across the same run — a
real predicted type is real evidence; "unknown" is not. Fixed by restricting `MERGEABLE_CLASSES`
to `{"IfcFurniture"}` — not by tuning the gap distance, since the problem wasn't the threshold.

### That fix exposed a bigger, pre-existing problem in Phase 3's furniture classification

With Proxy correctly excluded, merging found **zero** eligible pairs — because zero segments
were being predicted `IfcFurniture` at all. Traced why: Phase 3's original hard elevation cutoff
(`FURNITURE_MAX_HEIGHT_ABOVE_FLOOR_M`, added in the Phase 3 session) required a furniture
cluster's lowest point to sit within 0.3m of the floor. Checked the real data behind that
assumption for the first time here and found it false: **no cluster segment in this entire
dataset has a point within 0.49m of the floor** — furniture's floor-touching legs get absorbed
into the floor plane's own RANSAC inlier set, and its flat tops get carved off as separate plane
segments during Phase 2. What survives as a *cluster* is the mid-body, starting well above the
cutoff. The 0.3m threshold silently zeroed real furniture recall to reject a few false
positives — a materially worse trade than the problem it was fixing, and the earlier Phase 3
report didn't check recall before calling that fix done.

Rebuilt in two steps, each checked against ground truth before moving to the next, not assumed:

1. **Dropped the volume floor** (was `0.01`, based on nothing — checked and found real furniture
   fragments as small as 0.0006m³, thin leg/frame remnants once tops and bases are stripped
   away). Recall recovered to 62 furniture clusters found, but wrong predictions exploded
   32→86 (still zero *confident*-wrong — the humbled 0.55 confidence held — but precision
   collapsed to ~46%). Checked whether a middle threshold would help: plotted true-furniture
   volumes against non-furniture volumes and found **total overlap across the entire range**,
   0.00001 to 0.02+ — no volume threshold, anywhere, separates these two populations in this
   dataset. Confirmed, not guessed.
2. **Found a real separator**: distance from each cluster to its nearest wall-oriented plane.
   Checked against ground truth: every non-furniture fragment (wall/window/door/plate debris
   that broke off its own parent plane) measured **exactly 0.00m** from a wall plane at every
   percentile, 10th through 90th. True furniture ranged 0.07–0.95m, median 0.35m. A clean, real
   gap — unlike volume or elevation, both of which were tried and both of which genuinely
   overlap. Added `FURNITURE_MAX_WALL_DIST_M = 0.05` as a hard reject (elevation stayed as a
   soft confidence adjustment only, since it doesn't cleanly separate on its own).

**Result: 79.0% recall, 100.0% precision** for furniture cluster classification (49/62 true
furniture clusters correctly identified; every one of the 49 predictions correct — checked
directly, not inferred from the aggregate score). Overall classification: 68/176 correct,
15/176 wrong, confident-wrong still 2 (the same genuinely hard `IfcWall`/`IfcPlate` case from
Phase 3, unaffected by any of this).

### Instance merge, on the now-fixed classifier

Blind-scored, before/after, same discipline as Phase 2.5: **7/7 real furniture elements were
fragmented before merging** (up to 14 separate segments for one chair) — **6/7 collapse to
exactly 1 segment after merging**, the 7th down to 2 (its two pieces sit further apart than
`INSTANCE_MAX_GAP_M`). Zero new cross-contamination: purity violations unchanged at 43 — all of
them pre-existing in the untouched `Proxy` bucket (see the correction above), none introduced by
instance merging, confirmed by checking merged-segment purity individually, not just the
aggregate count.

## Reference-DB writing (Phase 4)

`write_reference_db.py` serializes the post-instance-merge `ClassifiedSegment` list into the
exact SQLite schema `docs/ScanToBOM_ReferenceDB_Spec.md` defines — the same schema
`extractIFCtoDB.py` produces from an IFC file, verified against real Java consumer code, not
assumed. Per element: a deterministic `guid` (`PC_<ifc_class>_<segment id>`), an `elements_meta`
row, an `elements_rtree` bound, an `element_transforms` row (`center`, `rotation_z`, `bbox` —
straight from `ClassifiedSegment`), a coarse box mesh in `base_geometries` fitted to its own AABB
(deduplicated by rounded dimensions — identical-sized elements share one mesh row), and an
`element_instances` link. One `IfcBuilding` + one `IfcBuildingStorey` row cover `spatial_structure`
— honest about the current lack of multi-storey detection rather than guessing a floor count.

**A real bug found immediately on the first blind run, before any scoring**: SQLite's `rtree`
module stores bounds as `float32`, not the `float64` a segment's AABB is computed in. A min/max
pair that's merely very close (not exactly equal) can flip order on that silent downcast, which
the module rejects outright (`IntegrityError: rtree constraint failed: minZ<=maxZ`) — hit for
real on this dataset's near-degenerate plane AABBs. Fixed by padding every axis by a fixed
epsilon (1e-4, several orders above float32 rounding error) in `_rtree_bounds()`, so ordering
survives the downcast on every axis, even a numerically flat one.

### Blind-scored against the written database itself, not the in-memory objects

`validate_reference_db.py` runs the full chain blind, writes the DB, then re-opens *that file*
(never touching the objects that produced it) and scores three independent things:

- **Schema/FK integrity**: row counts consistent across all per-element tables, no
  `element_instances` row pointing at a missing `base_geometries` hash, every `elements_meta` row
  has an `element_transforms` row, `elements_rtree` bounds reconstruct from `element_transforms`
  center+bbox, exactly one `IfcBuilding` + one `IfcBuildingStorey`. **Result: OK**, no failures.
- **Row-level classification accuracy** (same held-out per-point ground truth used since Phase
  3): **27/135 correct, 15/135 wrong, 93/135 deferred, confident-wrong=2** — identical wrong/
  confident-wrong counts to `validate_classification.py`'s direct Phase 3 run, confirming
  `write_reference_db.py` is pure serialization and introduces no new classification behavior.
  (Total row count, 135, differs from Phase 3.5's 176-segment classification count because
  instance merging collapses many small furniture fragments into fewer rows — expected, not a
  discrepancy.)
- **New: dimensional fidelity against the REAL `element_transforms` rows in
  `SampleHouse_extracted.db`** — the actual source the synthetic cloud was sampled from, a file
  neither `classify.py` nor `write_reference_db.py` ever reads. For each written element
  resolvable to one dominant real source `guid` (purity ≥0.95) with `rotation_z == 0` (a rotated
  real element's world-frame AABB isn't comparable to its local-frame bbox, so those are skipped
  rather than scored against a frame mismatch, not a real error), compared `bbox_x/y/z` against
  the real element's own. Split the comparison by axis rather than an all-or-nothing per-element
  check, because a single-sided scan has a genuine, physical blind spot: **the axis with the
  smallest real extent on a thin/planar element — a slab's material thickness, a wall's depth —
  is usually a face the scan never sees the far side of.** Its measured extent is just that
  surface's noise band, not the element's true thickness; inventing one would violate the prime
  rule. Scored separately as the "thin axis," not counted as a Phase 4 defect.
  - **Footprint axes (the 2 larger real dimensions), the ones a single-sided scan genuinely can
    measure: 4/21 within 35% on both.** Investigated rather than accepted: for the largest
    failure cluster (guid `3cUkl32yn9qRSPvBJVyWh4`, a large sloped roof/ceiling surface), the
    *union* of all 6 segments dominated by that one real element reconstructs its real footprint
    closely (14.86m vs 14.84m, 6.14m vs 7.28m, 1.56m vs 1.73m) — confirming this is the
    **already-documented, pre-existing Phase 2.5 plane-fragmentation gap** ("merging
    same-entity-but-different-face planes," flagged below) surfacing through a new, sharper lens,
    not a Phase 4 writer defect. `write_reference_db.py` correctly serializes whatever segment
    boundaries Phases 2–3.5 hand it; it doesn't (and shouldn't) silently merge or inflate them.
  - **Thin axis: median rel_err 0.76, only 10% within 35%** — consistent with the physical
    single-sided-scan limit above, not investigated further as a defect.

**Phase 4 is schema-correct, integration-consistent with Phase 3.5's own classification numbers,
and honestly scored against real ground truth on every axis it can be — including a new,
concrete, real-numbers instance of an already-flagged upstream limitation, rather than declaring
success on schema-validity alone.**

## BOM assembly (Phase 5)

Per CLAUDE.md's mandate — "keep the BOM/verb/compile/gate back end unchanged" — Phase 5 is
**not** new BOM logic. It's proving the Phase 4 reference DB is a drop-in replacement for an
IFC extraction at the one seam that matters: the real `IFCtoBOMMain` Java CLI, completely
unmodified.

`ExtractionPopulator` / `IFCtoBOMPipeline` don't take the reference-DB path as a CLI argument —
they hardcode it by convention from the classification YAML's `building_type` field:
`DAGCompiler/lib/input/<building_type>_extracted.db`. So the whole integration is: give the
point-cloud pipeline's output a `building_type` distinct from the real IFC-extracted
`SampleHouse`, write it there (`run_scan_to_bom.py`), point a classification YAML at it
(`IFCtoBOM/src/main/resources/classify_shpc.yaml`, `building_type: SampleHousePC`), and run the
exact same two Maven commands `scripts/run_RosettaStones.sh` already runs for every real
building. No `rel_aggregates`/`rel_contained_in_space`/`rel_fills_host` tables are written —
checked in `StructuralBomBuilder`/`ScopeBomBuilder`/`ExtractionPopulator` source first, not
assumed: all three catch the missing-table case and degrade to flat, non-nested BOMs, exactly
the behavior `docs/ScanToBOM_ReferenceDB_Spec.md` documents for a reference DB that skips those
optional tables — exercised for real here, not just specified.

```bash
# Phase 2-4: write DAGCompiler/lib/input/SampleHousePC_extracted.db
cd DAGCompiler/python/scan_to_bom
python3 run_scan_to_bom.py --ply ../../lib/input/pointcloud/samplehouse_synthetic.ply \
    --building-type SampleHousePC --lib-input ../../lib/input

# Phase 5: the real, unmodified Java BOM pipeline — identical commands to any IFC building
cd ../../..
mvn exec:java -pl IFCtoBOM -Dexec.mainClass=com.bim.ifctobom.IFCtoBOMMain \
    -Dexec.args="--populate --classify IFCtoBOM/src/main/resources/classify_shpc.yaml" -q
mvn exec:java -pl IFCtoBOM -Dexec.mainClass=com.bim.ifctobom.IFCtoBOMMain \
    -Dexec.args="--classify IFCtoBOM/src/main/resources/classify_shpc.yaml --bom-db library/SHPC_BOM.db" -q

# blind-then-score against the real IFC-extracted BOM for the same building
cd DAGCompiler/python/scan_to_bom
python3 validate_bom.py --real ../../../library/SH_BOM.db --pc ../../../library/SHPC_BOM.db
```

### First run, no Java changes: full QA gate cleared

Both Maven commands ran clean on the first try — `--populate` imported 135 geometry blobs and
cataloged 135 new products with zero errors, and the BOM build passed **all 18 of
`BomValidator`'s pre-commit QA checks** (BOM/line counts, duplicate/orphan detection,
world-coordinate and AABB-containment checks, LBD convention, product linkage, extraction
reconciliation, shape consistency) — the same gate a bad extraction would fail and roll back
from, cleared without any Java code aware a point cloud was ever involved.

### Blind-scored against the real IFC-extracted BOM for the same physical building

- **Building envelope: within 0.6% on every axis**, measured completely independently —
  16882×8686×3968mm (point cloud) vs 16867×8667×3945mm (real IFC), a 0.1%/0.2%/0.6% relative
  error on width/depth/height. This number depends on none of the classification or
  fragmentation machinery — it's the outermost scanned points on each axis — and it's the
  strongest single piece of evidence that the geometric pipeline underneath is measuring the
  real building correctly, independent of any label a segment ends up with.
- **Per-class composition, checked against the real BOM's `M_Product.ifc_class` breakdown**:
  `IfcWindow` matched exactly (1/1). `IfcFurniture` came close (8 vs 10 real). `IfcWall`
  (11 vs 5 real) and `IfcSlab`/`IfcRoof`-equivalent (22 vs 19 real, split differently across
  the two labels) both over-count relative to real elements — **not a new Phase 5 defect**;
  it's the already-documented Phase 2.5 plane-fragmentation gap (see "Reference-DB writing"
  above) surfacing a third time, now visible as inflated BOM line counts rather than inflated
  segment counts, exactly what you'd expect it to look like at this layer.
- **New finding, specific to this cross-check: `IfcDoor` came back 0/2.** Traced directly
  (`validate_classification.py`'s per-segment log): both real doors' points landed in
  **cluster**, not plane, segments — meaning Phase 2's RANSAC never found them a clean enough
  plane fit, so they fell into the DBSCAN pool. `classify.py`'s door-vs-window
  floor-proximity check only runs inside `_classify_plane()` — a cluster-geometry segment has
  no path to `IfcDoor` at all, only `IfcFurniture` or `IfcBuildingElementProxy` (which is what
  they honestly got: `DEFERRED`, not `WRONG` — confirmed, this is a real *deferral* gap, not a
  misclassification). Not fixed here — flagged below as a concrete, now-quantified instance of
  the general "cluster fragments never get door/window disambiguation" gap.

**Phase 5 is real: the unmodified Java BOM pipeline runs on point-cloud-derived input, clears
its own QA gate, and produces a building envelope within 0.6% of the real IFC extraction on
every axis. The composition differences it also surfaces all trace to already-documented,
already-understood upstream gaps — Phase 5 adds a genuinely independent (BOM-line-count-level)
confirmation of those gaps' real-world size, not a new failure mode of its own.**

## Window detection for cluster segments (Phase 5 follow-up)

Phase 5's BOM cross-check found cluster-geometry segments had **no path to `IfcWindow` at
all** — `_classify_cluster()` only ever returned `IfcFurniture` or `IfcBuildingElementProxy`,
so any window whose points missed RANSAC plane detection (and fell into the DBSCAN pool) was
deferred, never correctly labeled, no matter how clean the scan. Same blind-validation
discipline as the Phase 3 furniture fix: don't report a fix done without re-scoring against
held-out ground truth, and if the first geometric hypothesis doesn't cleanly separate true
positives, go find a different one rather than tuning the same one further.

**First checked what was actually available to work with.** All 30 true-window cluster
segments in this dataset sit at `wall_dist=0.00m` from a wall plane — i.e. every one of them
was already being correctly excluded from `IfcFurniture` by the existing wall-distance check
(`FURNITURE_MAX_WALL_DIST_M`), then unconditionally dumped into `IfcBuildingElementProxy` along
with genuine wall/plate debris fragments, which sit at the *same* `wall_dist=0.00m` — so
wall-distance, precise as it is for furniture-vs-debris, carries zero information for
window-vs-debris. Checked volume, flatness, point count, and max AABB extent next (the other
signals already in use elsewhere in `classify.py`) — **all four showed the same kind of total
population overlap volume did for the original furniture problem**: window and debris ranges
overlapped almost entirely on every one of them. Printed full sorted lists, not just
percentiles, to be sure — no threshold on any single existing feature separates the two
populations here.

**Found a real, different signal: how much actual point *depth* a cluster has relative to the
wall's own normal direction**, not just whether its AABB touches the wall. A window is real
relief set into a wall's opening — the glass and frame sit at a range of positions along the
wall's depth (recessed, or spanning the wall's thickness), not sitting flush on a single
surface the way debris does. Computed `points[cluster] @ wall_normal`, then `max - min` of
that projection — genuinely different information from `aabb_gap`, which only measures whether
the AABBs touch, not how deep the cluster's own points go relative to the wall plane's face.
True window clusters ranged 0.084–0.251m on this measure; debris was concentrated lower (median
0.10m) but — unlike the furniture/wall-distance split, which had zero overlap — **this one
genuinely doesn't separate cleanly**: some wall-corner debris chips and some furniture pressed
against a wall (a wardrobe has real depth too) land in the same range as true windows.

**Swept thresholds 0.10–0.18m against held-out ground truth rather than picking one and hoping**
(precision/recall/F1 at each): 0.12m gave the best F1 (0.72) found — precision 0.68, recall
0.77 on the geometric feature alone. Because this signal is real but not clean,
`classification_confidence` is set to **0.55, deliberately below the 0.7 "confident" bar** — a
wrong prediction here should show up as `WRONG`, never `CONFIDENT-WRONG`.

**Re-scored blind, on the live pipeline, against held-out ground truth** (not just the offline
threshold sweep): cluster-only window predictions came back **21 true positives, 10 false
positives, 9 false negatives — precision 0.68, recall 0.70**, matching the offline sweep
closely. Checked for regressions on everything the fix touches:
- **Furniture classification: unchanged, exactly** — 49 TP, 0 FP, 79.03% recall / 100%
  precision, identical to before this change. The new window path only fires for clusters that
  were *already* being rejected from `IfcFurniture` (wall-touching), so it cannot affect any
  segment that was already correctly classified as furniture — confirmed, not just expected
  from reading the code.
- **Confident-wrong count: unchanged, exactly 2** — the same two genuinely-hard `IfcWall`→
  `IfcPlate` cases from Phase 3, untouched. The 10 new false-positive window predictions all
  score `WRONG`, none `CONFIDENT-WRONG`, exactly as the confidence choice intended.
- Overall classification: 68/176 → **89/176 correct**, 93 → 62 deferred to
  `IfcBuildingElementProxy`. Phase 4's written-row accuracy check: 27/135 → **48/135 correct**,
  schema integrity and dimensional-fidelity numbers unaffected (this change only touches
  labels, never geometry).

**Honest limit, not chased further: doors.** The same investigation traced why all 3 true door
clusters in this dataset have *low* wall-normal spread, like debris, not like windows: their
actual point counts (41–83) and volumes (0.002–0.007m³) are far too small to be a door leaf —
consistent with the door leaf's front face being **nearly coplanar with the closed wall's own
plane**, so RANSAC's plane detection absorbs it into the wall segment itself (a real,
single-scan-of-a-closed-door limit — you can't tell there's a door there from outside without
the frame reveal or the door being open). What's left as a cluster is a few points of leftover
hardware (handle, hinge), not enough real evidence to defensibly call `IfcDoor` — extending
the same wall-normal-spread signal to doors was checked and rejected here, honestly, rather
than forced: `IfcDoor` stays `IfcBuildingElementProxy` (deferred, not guessed) for this dataset.

## Real-world validation (Phase 6 — DeKH_B_ICU)

First run against a real terrestrial LiDAR scan, not the synthetic cloud every earlier phase
was validated against — DeKH (German Hospital Dataset, published alongside the BIMStruct3D
paper — model card: https://huggingface.co/dfki-av/BIMStruct3D-segmentation),
`Buildings/B/DeKH_B_ICU`, CC BY-NC-SA 4.0, permission confirmed from the authors for this use.
Kept entirely outside this repo (licensed third-party data); every checkpoint, the written
reference DB, and the run log stayed in a local scratch directory, never committed. Only the
pipeline code that processes it — `pointcloud_io.load_las_downsampled()`, `smoke_test_dekh.py`,
`validate_real_world.py`, `run_dekh_staged.py` — is committed here.

**Scale forced two real infrastructure changes before any DeKH-specific work could even run**:
- 437,277,430 raw points — far too many to load in one shot (tens of GB) or run
  RANSAC/DBSCAN on directly. `load_las_downsampled()` reads and voxel-downsamples chunk by
  chunk (1cm voxels, keeping exactly one real measured point per cell — never an invented
  average), bounding memory by the downsampled count (23.7M, 5.4% retained) regardless of raw
  file size, and returns the kept points' original indices so an external per-point label
  array (DeKH's own `.npy` ground truth) stays aligned after downsampling.
- This environment could not sustain a background or even a fully OS-detached process across
  a conversation turn boundary — four full-run attempts died silently before this was
  understood. The only execution mode confirmed to survive is a single foreground call, capped
  at ~10 minutes. `run_dekh_staged.py` splits the pipeline into 3 checkpointed stages
  (downsample / segment / classify), each its own process, each checkpointing to disk, with
  within-stage progress logging (RANSAC iteration counts, per-search timing) added to
  `segment.py` so a run that does die mid-stage shows exactly how far it got, not just that
  it's gone.

**A real bug caught in the scoring before trusting any result**: the first scoring pass showed
8/82 ground-truth elements matched (9.8%) with one axis at 111.3 (11,100%) relative error —
not "real data is hard," a bug. Predictions had gone through `normalize_pointcloud()`
(coordinates shifted by a computed tack point), but the ground-truth IFC was extracted with
`--skip-normalize` (its own raw frame) — comparing across two different origins. Fixed by
un-shifting predicted AABBs back to the raw frame before matching; confirmed the fix was
correctly scoped because the baseline comparison (extracted the same `--skip-normalize` way)
was unaffected before or after.

**Couldn't verify DeKH's own per-point label scheme, so didn't invent one.** The `.npy` ground
truth is real (`float32`, one label per raw point, index-aligned with the `.laz`) but checking
the dataset README, the segmentation model's own config, the source paper (Kaufmann et al.,
EC3 2023) in full, and the external annotation-guideline repo (login-gated) found no
authoritative numeric-ID-to-class-name table. Used as an unnamed grouping signal only
(purity/coverage, same as `validate_segmentation.py` already does for the synthetic ground
truth) — no class names inferred from the raw label IDs anywhere in this pipeline.

### The wall-detection investigation — three real causes checked, in order, not assumed

Composition on the real scan: `IfcBuildingElementProxy` 164-175 (65%+, honest deferral, by
design), `IfcFurniture` 51-52 (the real GT has **zero** furniture/equipment entities — Building-
category only, per Kaufmann et al.'s own category split — so every furniture prediction is
structurally unscoreable here, not wrong), and real structural predictions
(`IfcSlab`/`IfcWindow`/`IfcWall`/`IfcDoor`) badly under-counting the real 124-element ground
truth. Spatial IoU-style matching (same kind of metric this research area's own literature,
BIMStruct3D, reports for itself) found only 13/82 scoreable GT elements matched (15.9%) against
the published baseline's 47.6% — and specifically only 5 of 31 real `IfcWall` elements
recovered. Investigated why, checking each hypothesis against real data rather than reporting
the first plausible one:

1. **Occlusion — checked, mostly ruled out.** Compared point density and `.npy` label
   composition inside each GT wall's footprint, matched vs. unmatched. Matched-wall median
   density 125,679 pts/m³; unmatched-wall median 101,690 pts/m³ — comparable, not
   systematically sparse. Only 2 of 20 unmatched walls showed a real occlusion signature (density
   under ~3,200 pts/m³, or label values that never appear in matched walls' point clouds). The
   other 18 looked statistically identical to matched walls yet still went unmatched — occlusion
   is real for a small minority, not the dominant cause.
2. **Piecemeal rediscovery — checked, found real, fixed.** Checked whether a vertical-oriented
   plane segment existed over each unmatched wall's footprint at all, regardless of
   classification: 19 of 20 had **zero**, despite comparable point density to matched walls —
   never discovered as a wall-orientation plane in the first place. Checked the segmentation log
   directly: all 40 of `MAX_PLANES=40`'s RANSAC searches ran (budget exhausted, not "ran out of
   planes"), and 35 of those 40 found a horizontal-orientation plane, only 5 vertical. The 35
   horizontal results clustered into just two real height bands (ceiling, floor) — the same two
   real surfaces being re-discovered piecemeal each search (real noise/slight non-planarity means
   one plane equation doesn't fit the whole surface at once), not 35 distinct objects, burning
   the budget before most walls got a turn.

   **Fixed**: `_same_plane_equation()` (shared with Phase 2.5's `merge_coplanar_fragments` —
   same `NORMAL_ANGLE_TOL_DEG`/`OFFSET_TOL_M` criteria, same real question, asked one phase
   earlier) checks each newly-found plane component against already-accepted plane segments of
   the same orientation; a match absorbs the component's points into the existing segment
   instead of spending a fresh `MAX_PLANES` budget slot. Verified two ways before trusting it:
   the synthetic Sample House baseline first (coverage 58/58 unchanged, confident-wrong
   unchanged at exactly 2, classification correct-rate actually *improved* 51.5%→66.7% — fewer,
   more-complete segments classify more confidently), then re-ran DeKH_B_ICU itself: raw plane
   count dropped 508→113 (78% fewer, 497 real components absorbed as genuine rediscoveries) —
   the mechanism demonstrably works.
3. **Real horizontal clutter competing with walls — the actual dominant cause, found after
   re-scoring the fix rather than assuming it worked.** `IfcWall` recovery barely moved (5→6);
   spatial match stayed at 13/82. Checked why: even with rediscovery-absorption, the loop still
   spent all 40 accepted-plane slots (107 horizontal, only 6 vertical) — because a real,
   cluttered ICU room has **many genuinely distinct real horizontal surfaces** (equipment,
   furniture, counters at different heights — not near-duplicates of the ceiling/floor, so
   correctly *not* absorbed by the fix above), which RANSAC's biggest-remaining-plane-first
   search keeps finding ahead of walls regardless. The absorption fix caught exactly the failure
   mode it was designed for; this is a different, larger problem it was never meant to solve.

**Fixed: interleaved per-orientation search.** Before picking an approach, checked the actual
`segment_pointcloud()` loop against three named candidates (reserve a fixed search-budget split
per orientation; interleave best-vertical/best-horizontal each round; a two-pass floor/ceiling-
first extraction) — the floor/ceiling-first option turned out to already be mostly captured by
the rediscovery-absorption fix above (floor/ceiling re-discovery is already free; the real
remaining competitor is *other* horizontal clutter, not floor/ceiling), so it wouldn't have
generalized to the actual cause. Went with interleaving: `_fit_plane_ransac_multi()` tracks the
best-supported candidate **per orientation class** (vertical/horizontal/oblique) within one
RANSAC pass — no extra iterations, same compute — instead of only the single biggest candidate
overall, and each round now accepts every orientation whose candidate clears
`MIN_PLANE_INLIERS`, processed vertical-first so a boundary point contested between two
orientations' inlier sets goes to the wall candidate, not the (usually larger) floor/ceiling/
clutter one.

Verified two ways before trusting it, same discipline as every fix this project: the synthetic
Sample House baseline first (coverage 58/58 unchanged, cluster fragmentation actually improved
12/58→1/58 — fewer stray points fall through to the clustering stage now that more real planes
get found; one small regression traced to its real cause — a single `IfcMember` picked up four
1-point boundary-noise contacts from having 2.5x more accepted planes nearby, not a structural
break, its own dominant segment stayed at ~95% purity), then re-ran DeKH_B_ICU itself: **wall
recovery 5/31 → 29/31 (93.5%)**, overall spatial match 13/82 (15.9%) → 36/82 (43.9%), closing
most of the gap to the published baseline's 39/82 (47.6%). `MAX_PLANES=40` still means 40 search
*rounds*, not 40 accepted planes — each round can now accept up to 3 plane equations (one per
orientation) for the same RANSAC compute, so raw accepted-plane count before Phase 2.5 merge
rose accordingly (45 → 551 on the full DeKH scan) — real, evidence-gated surfaces (each still
individually clears `MIN_PLANE_INLIERS`), not an invented cap change.

2 of 31 real walls remain unmatched — not yet traced to a specific cause; worth a quick look in
a future session but not blocking Buildings A/C.

## Validated results (Sample House synthetic cloud, 670,965 points, 3mm noise, 400 pts/m²)

Numbers below are from the pre-normalization investigation (`--no-normalize`), kept as the
original reference run. With normalization on (the default now), segment counts shift by ~3%
(e.g. 181→176 post-merge) from floating-point sensitivity near the shifted origin — coverage
and fragmentation scores are identical either way; see "coordinate normalization" above.

**Phase 2 (raw, before merge): 213 segments** (71 planes, 142 clusters), 130 flagged
low-confidence, 100% coverage of all 58 scannable real elements (excludes the 7
`IfcOpeningElement`s, which have no real surface to scan).

**Phase 2.5 (geometric merge, blind-scored against the same held-out ground truth): 213 → 181
segments.** Plane count specifically: 71 → 39 (a 45% reduction on exactly the category the merge
targets; cluster count, deliberately untouched, stayed at 142). 17 merge groups formed from 41
pairwise unions, all logged with source segment ids and point counts (`§MERGE` lines) — e.g. the
two largest ceiling fragments (95,012 + 33,597 points, same plane family, split by the component
step) reunited into one 128,609-point segment.

- **Cluster segments — correction to an earlier claim in this file.** This README previously
  said cluster purity was "100%, always." That was **wrong** — not re-verified carefully enough
  before writing it down, an eyeballed impression from a long printed log, not a full check.
  Phase 3.5's validation work re-checked every cluster against ground truth and found 43/142
  (30%) with purity below 0.95 — genuine cross-contamination between different real elements
  within Phase 2's raw DBSCAN clustering, present with or without coordinate normalization
  (checked both, identical counts, so that's not the cause either). Left as a documented,
  pre-existing limitation for now (see "Instance merging" below for why it doesn't block that
  work) rather than silently corrected without saying so.
- **Plane segments: purity varies, same "correct, not a defect" reasoning as Phase 2** — merged
  segments still legitimately span multiple real elements when those elements are genuinely
  coplanar (e.g. several furniture tops at the same height); the merge doesn't introduce new
  false-merges, it consolidates fragments of surfaces that were already established as
  legitimately shared.
- **Important correction to the original framing, found while validating:** "a wall arrives as
  3-4 plane pieces" undersells the real geometry. Checked the 5 actual wall entities
  (`IfcWall`×3, `IfcWallStandardCase`×2) directly against ground truth: each substantially
  touches **8–13 distinct plane segments even after the merge** (down from 10–18 before). This
  is not a shortfall of the merge — it's because a real modeled wall in this dataset is a
  multi-layer, multi-face solid (e.g. `Wall-Ext_102Bwk-75Ins-100LBlk-12P`: 102mm brick + 75mm
  insulation + 100mm block + 12mm plaster) with genuinely distinct parallel faces ~100-300mm
  apart, well outside `OFFSET_TOL_M`'s 5cm tolerance — plus, where a wall run turns a corner,
  each straight segment has a different normal entirely. Those are real, physically different
  planes; merging them requires knowing "these parallel planes ~wall-thickness apart belong to
  one wall assembly," which is domain/semantic reasoning, not pure geometry. **That reunification
  is correctly Phase 3's job** (classify first, then recognize the wall-face pattern), not
  something Phase 2.5 should attempt blind. What Phase 2.5 does correctly handle — confirmed via
  the `§MERGE` log — is fragments of the literal same face split by an occluder (a doorway gap,
  furniture against the wall): same normal, same offset, proximate.

### One tuning experiment worth recording

Widening `CLUSTER_EPS_M` from 0.08m to 0.15m (to try to reduce furniture fragmentation) was
**measurably worse on both axes**: segment count rose 213→318, low-confidence count rose
130→193, and — critically — cluster purity dropped below 1.00 for the first time (0.68, 0.53),
meaning the wider epsilon started bridging gaps between adjacent-but-different real objects, not
just within one object's disconnected parts. Reverted to 0.08. Recorded here so a future session
doesn't re-try the same knob expecting a different result — the fix for fragmentation is a
proximity-aware instance-merging pass in a later phase, not a bigger DBSCAN epsilon.

## What's still not done — deliberately deferred

- **2 of 31 real DeKH_B_ICU walls still unmatched** after the interleaved per-orientation search
  fix (see "Real-world validation (Phase 6)" above — wall recovery is 29/31 now, up from 5/31).
  Not yet traced to a specific cause.
- **Merging same-entity-but-different-face planes** (a wall's inner vs outer face, or its faces
  across a corner turn) — needs semantic/domain reasoning about wall assembly (e.g. "two
  parallel planes ~wall-thickness apart"), not pure geometry; see the wall-face finding in
  Phase 2.5's results. Not attempted — classify.py currently treats each merged plane as its
  own independent element. **Phase 4's dimensional-fidelity check gave this a concrete, blind-
  scored number for the first time**: only 4/21 axis-aligned, purity-resolvable written elements
  matched their real element's footprint within 35% — most of the misses are the same
  fragmentation pattern (e.g. one real sloped-roof element split across 6 written segments,
  whose *union* does reconstruct the real footprint, confirming it's this issue and not
  something new). Still not attempted here — same reasoning as before, it needs domain
  knowledge about surface continuity that pure per-segment geometry doesn't have.
- **Material thickness / depth is not measurable from a single-sided scan** — Phase 4's
  dimensional check found the axis with the smallest real extent on thin/planar elements (a
  slab's thickness, a wall's depth) reads as just the scan's noise band, not the true thickness
  (median rel_err 0.76 against real values). This is a genuine physics limit of surface scanning,
  not a bug — flagged so no future session tries to "fix" it with a geometric heuristic; it would
  need either a second scan pass of the hidden face or a catalog/BOM-supplied nominal thickness
  (extract-or-compile-only still applies: never invent one from the visible face alone).
- **Cluster-geometry windows now have a real detection path** (`WINDOW_WALL_NORMAL_SPREAD_M`,
  see "Window detection for cluster segments" above) — precision 0.68, recall 0.70 on held-out
  ground truth, confidence deliberately kept below the 0.7 "confident" bar since the signal,
  while real, isn't clean. **Doors remain undetectable from cluster geometry in this dataset**,
  root-caused (not just observed): a closed door's leaf is nearly coplanar with its wall, so
  RANSAC absorbs it into the wall's own plane segment — what's left as a cluster is a few
  points of hardware (handle/hinge), genuinely too little evidence to call `IfcDoor` rather
  than defer. `classify.py`'s floor-proximity door check still only runs inside
  `_classify_plane()`; a cluster segment has no path to `IfcDoor` at all. This was quantified
  first via Phase 5's BOM-level cross-check (both real doors in this dataset are cluster
  segments, so `IfcDoor` came back 0/2 against the real IFC BOM's 2) and root-caused via direct
  point-count/volume inspection of those 3 fragments — not attempted further here, since the
  evidence genuinely doesn't support a specific label, only "something real but small was
  here."
- No room/space segmentation — per `docs/ScanToBOM_ReferenceDB_Spec.md` §1, this is optional
  for v1 (`ScopeBomBuilder` already degrades gracefully to flat floor-level BOMs without it —
  confirmed for real in Phase 5's run, not just per the spec: `rel_contained_in_space` isn't
  written, and the point-cloud BOM committed with 0 SET BOMs, catching that degradation path
  live rather than only reading that it exists in source).
- No material/color inference — classification is geometry-only throughout.
- No handling of tilted scans (`rotation_x`/`rotation_y` assumed ~0) — documented open item,
  see "LAS/LAZ ingestion" above.
- No MEP element classification — explicitly deferred per the original roadmap; real point
  clouds capture visible surfaces well and embedded/occluded MEP very poorly.
- **Pre-existing Phase 2 cluster-purity gap, newly discovered while validating Phase 3.5** (see
  the correction under "Validated results" above): 43/142 (30%) of Phase 2's raw DBSCAN
  clusters mix points from more than one real element, purity below 0.95. Doesn't block
  furniture classification (the wall-distance/volume/flatness filters + instance merge operate
  fine regardless) or the instance merge itself (confirmed zero regression from it), but it is
  a real, unfixed gap in Phase 2's own output quality that the original Phase 2 report
  incorrectly claimed didn't exist. Not investigated further here — flagged for whoever picks
  it up next rather than guessed at now.
