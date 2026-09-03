# ⚠ DO NOT REMOVE — SCOPE & DISCIPLINE
**Scope:** Make the REAL ARC building load as gizmo-editable, guid-carrying meshes in the **modeller** (bim-ootb),
so the Phase-2 SDG forward-fold cascade (drag wall → door rides) can fire on REAL extracted data. This is the
PREREQUISITE substrate slice the cascade rests on. **Read the log after every run.** Honour until ✅ DONE.
**Repo:** code = bim-ootb worktree `/tmp/wt-*` (branch lane/arc-editable-substrate); spec = this file (bim-compiler).
**NON-INVENT:** every seeded element's bbox + position is MEASURED from `*_extracted.db` (`element_transforms`);
nothing is fabricated. Provenance stamped `recovered:extracted` on every seed op.

---

## WHY (the finding that forced this slice — 2026-06-29)
Two independent Explore passes proved: the modeller's **editable scene is synthetic-only** — the gizmo can only
select/`GEOM_MOVE` user-authored primitives (`GEOM_INSERT` library components, sketched walls, routed MEP).
`featureId = kernel_ops.id`. Real building elements (walls/doors/windows from `*_extracted.db`) are **never editable
meshes** — they exist only as walker DATA substrate + ephemeral overlay markers (`_renderDiscWalk` InstancedMesh,
no featureId). So "drag wall → door rides" has **no wall to grab**. The VISION-LOCK line *"open a WHOLE ARC building
& EDIT it"* is aspirational, not yet shipped. The earlier "featureId↔guid bridges via elements_meta" claim was
WRONG — they are disjoint namespaces (op-row id vs IFC guid), and real elements never become op rows.

**Decision (user, 2026-06-29):** build the editable-ARC substrate FIRST (Option A). Then the cascade drops in.

## ARC of two slices
- **(1) THIS slice — load-as-editable + guid bridge.** Real ARC elements become signed `GEOM_INSERT` op-rows
  (measured box proxies) the gizmo can select + `GEOM_MOVE`; each op carries `output_guid` = the real IFC guid →
  `featureId → guid` bridge is queryable + persisted. Witness: W-ARC-EDITABLE.
- **(2) NEXT slice — cascade on drag** (hosted-by + contains). Reuses the proven `sdg_fold` math + this bridge:
  drag a wall → `swXEdges.fills`/`.aggregates` neighbour lookup by guid → emit induced `GEOM_MOVE`s → existing fold
  redraws them riding → rosetta 0.000mm round-trip oracle. Witness: W-SDG-CASCADE-MODELLER. (NOT in this slice.)

---

## §ARC-1 SPEC — load the ARC building as editable, guid-carrying meshes

### Data source (MEASURED, non-invent)
`*_extracted.db` (the SAME db str_walker opens): `element_transforms` (guid, center_x/y/z, bbox_x/y/z) JOIN
`elements_meta` (guid, ifc_class, discipline). Filter **discipline='ARC'** (VISION-LOCK: ARC = sole edited
substrate). AABB convention (backfill_bbox.py): `bbox_k` = FULL extent; `min_k = center_k − bbox_k/2`.

### Seed op (reuse the proven GEOM_INSERT → editable-mesh path)
For each ARC element, commit ONE signed `GEOM_INSERT` op carrying:
- `parameters.bbox` = `[−bx/2, bx/2, −by/2, by/2, −bz/2, bz/2]` (measured local box; centred x/y, centred z)
- `parameters.placement` = `{ x: center_x, y: center_y, z: center_z − bz/2, rot: rotation_z||0 }`
  (ground-seat: `place()` subtracts `bbox[4]` = −bz/2, so world centre lands at center_z — matches extraction)
- `parameters.color` = discipline/class hex (ARC palette)
- `parameters.provenance` = `'recovered:extracted'`
- `output_guid` = the real IFC guid  ← **the bridge carrier** (kernel_ops.output_guid, already supported by
  `KernelOps.commitGroup` line 319/323; idempotent by deterministic `gid='arcseed-'+guid`)

### foldInsert raw-bbox extension (minimal, additive, grep-clean)
`foldInsert` today requires `P.hash` → `library.get(hash)`. Add a guard: **when `P.hash` is absent but `P.bbox`
present → build a raw LOD-200 box from `P.bbox`** (skip LOD-300 mesh path; box only). One branch, no consumer edits:
```
const c = P.hash ? this.get(P.hash) : null;
if (P.hash && !c) throw …;                 // unchanged: unknown hash still errors
const bb = c ? c.bbox : P.bbox;            // raw measured box when no catalog component
const base = (c && lod==='300') ? this.meshArrays(P.hash) : boxArrays(bb);
```

### The bridge (queryable + persisted)
- PERSISTED: `kernel_ops.output_guid` per seed op (survives replay; `featureId = id`, so
  `SELECT output_guid FROM kernel_ops WHERE id=?` resolves featureId→guid).
- LIVE map: at seed, build `window.__arcGuidByFid` (fid→guid) + `window.__arcFidByGuid` (guid→fid) for O(1)
  cascade lookup. Rebuilt on every Open from the committed rows (no Date/random; deterministic gids).

### Idempotency / re-open
Deterministic `gid='arcseed-'+guid` → re-opening the SAME building is a `commitGroup` idempotent no-op (no double
seed). Different building = different guids = fresh seed. (Switching buildings = separate concern; out of scope —
log it, don't solve here.)

### Guards (non-invent, honest-refuse)
- Element with NULL bbox or NULL center → SKIP + `§ARC-SEED skip guid=… reason=no-bbox` (never fabricate a box).
- `discipline` absent → fall back to `ifc_class IN (IfcWall*, IfcDoor, IfcWindow, IfcSlab, IfcColumn, IfcBeam,
  IfcRoof, IfcCovering, IfcStair, IfcRailing)` ARC-ish set, log the fallback. (measure-don't-whitelist caveat:
  this is a discipline-recovery fallback only, logged.)

---

## §ARC-1 WITNESS — W-ARC-EDITABLE (claim FIRST, node, REAL SampleHouse)
Run against a REAL `SampleHouse_extracted.db` (node + sql.js, like the other modeller witnesses). Assert:
1. **count** — N ARC elements in db ⇒ N `GEOM_INSERT` seed ops committed (no silent drop; skips logged + counted).
2. **bridge bijective** — every seed op's `output_guid` ∈ db guids; `__arcFidByGuid[guid]` round-trips to the op id;
   no collisions (|fid set| == |guid set|).
3. **position fidelity (the non-invent oracle)** — folded mesh world-centre == `element_transforms.center_xyz`
   within 1e-6 (the box IS the measured bbox at the measured centre; NO constant offset).
4. **bbox fidelity** — folded mesh extent == `(bbox_x,bbox_y,bbox_z)` within 1e-6.
5. **gizmo-ready** — each folded mesh carries `userData.featureId` == its op id (selectable + GEOM_MOVE-able).
6. **idempotent re-seed** — running the seed twice ⇒ same op count (commitGroup idempotent by gid), bridge unchanged.
7. **honest skip** — an element with NULLed bbox is SKIPPED (not boxed), logged, and excluded from the count delta.

Browser §-smoke (secondary, headless Chromium): open SampleHouse in modeller → `§ARC-SEED committed=N` →
select a seeded wall → `§MODELLER select featureId=…` fires → one GEOM_MOVE shifts it (existing path).

---

## STATUS — §ARC-1 ✅ DONE+WITNESSED 2026-06-29 (branch lane/arc-editable-substrate, bim-ootb worktree)
- [x] foldInsert RAW-BBOX extension (bonsai_library.js): hash-less GEOM_INSERT with measured P.bbox → LOD-200 box;
      present-hash path byte-identical; unknown hash + no-hash-no-bbox both throw. Move/rotate branches raw-safe.
- [x] arc_editable.js (dual-export): buildSeedOps(db) measured ARC ops; seedArc orchestrator; bridge maps.
- [x] commitSeedGroup(opsArray, gid) on bonsai_oplog.js: ONE signed group, verify+fold+persist, idempotent by gid.
- [x] seed-on-open wired: str_walker_outliner `_forkEditable` → `_seedArcEditable` (re-opens __dwBuf read-only,
      seeds via oplog). Auto on every building Open; idempotent 'arcseed-<key>' → no double-seed.
- [x] bridge: window.__arcFidByGuid / __arcGuidByFid + PERSISTED kernel_ops.output_guid (queryable by featureId).
- [x] **W-ARC-EDITABLE 8/8** (node, REAL SampleHouse): 39 ARC → 39 ops; bridge bijective+all-guids-real+round-trips;
      folded world-centre == measured center_xyz <1e-6 (NO invented offset); box extent exact + rot≈0 AABB exact
      (20/20); featureId==op id; output_guid persisted; idempotent re-seed; honest NULL-bbox skip.
- [x] **§ARC-SEED-SMOKE 8/8** (headless): seed fires on Open, bridge on window, 39 gizmo-selectable meshes, a
      seeded element is GEOM_MOVE-able (signed+verified), no LOAD_FAIL.
- [x] **§ARC-1 REGRESSION 5/5** (node): catalog-hash foldInsert path unperturbed; guards preserved.
- [x] sw.js v15→v16 + precache arc_editable.js; modeller.html script tag (arc_editable.js?v=1).
- [x] visual: seeded building renders coherently (slabs/walls/color-coded doors/furniture at measured pos; signed
      tip 0cb08e60 == witness). Shot: scratchpad/arc_seeded.png.

## ⚠ FINDING that shapes slice (2) — contains/aggregates is NOT element↔element in SampleHouse
W-ARC-EDITABLE S6 surfaced: SampleHouse `rel_fills_host` = 7 edges, ALL **ARC↔ARC, fully bridge-resolvable (7/7)**
→ the **hosted-by cascade (drag wall → door rides) is LIVE on real data**. But `rel_aggregates` (34) has ALL
parents **non-element** (`not-in-meta` STR-assembly roots) and children **STR** IfcMember/IfcPlate (curtain-wall) →
**ZERO ARC element↔element aggregates**. Per VISION-LOCK (STR = a walker, not edited substrate) this is correct,
not a gap. **Consequence for slice (2):** on SampleHouse the cascade can only demonstrate **hosted-by**; the
**contains** half (the user picked hosted-by + contains) needs a building whose aggregates are element↔element —
OR seeding STR-assemblies as a separate (non-ARC) editable layer (out of current VISION-LOCK scope). Decide at
slice (2) start: ship hosted-by cascade on SampleHouse now, revisit contains when a building exposes element-level
aggregates. (Duplex/SampleCastle aggregates not yet surveyed — check before assuming.)

## slice (2) §SDG-CASCADE — ✅ DONE+WITNESSED 2026-06-29 (bim-ootb PR #573, sw v16→v17, auto-merge armed)
**Hosted-by ride: drag wall → door/window rides.** User chose hosted-by ONLY (survey: contains/aggregates has 0
ARC element↔element data across SH/Duplex/SampleCastle — it's spatial/STR-assembly, out of ARC scope).
- `sdg_cascade.js` (pure, dual-export): `ridersFor(movedFids, guidByFid, fidByGuid, fills)` → hosted fillings of
  moved HOSTS via the §ARC-1 bridge over real `swXEdges.fills`. Directional (door never drags wall), one-hop, deduped.
- `commitMove`: after the dragged GEOM_MOVEs, emit induced GEOM_MOVE per rider (same delta, induced:'hosted-by').
- **W-SDG-CASCADE-MODELLER 7/7** (node, real SH): directional; all k fillings ride; rigid ride by exact delta;
  door↔wall offset invariant; rosetta −delta recovers original maxErr 7e-8mm; non-invent (ifc:recovered only).
- **§SDG-CASCADE-SMOKE 6/6** (headless): drag wall #3 → door #13 rides +1m, same delta, signed+verified, §-logged.
- sw v17. Script tag sdg_cascade.js?v=1.

## NEXT (open, lower-pri)
- contains cascade: only when a building exposes ARC element↔element aggregates (none in simple set), OR a separate
  STR/spatial editable layer (out of VISION-LOCK). Survey new buildings before assuming.
- multi-hop / transitive cascade if a filling is itself a host (none today; doors aren't hosts).
- abuts/anchored/spans cascades (soft adjacency) — the ORANGE accept/ignore half of the spine (backprop), not the
  hard hosted-by ride. Separate slice.
