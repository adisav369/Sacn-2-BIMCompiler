# ⚠ DO NOT REMOVE — Revit+ Lens FIND: RESUME for a fresh session
# Scope: the Find panel lenses (Storey/Disc/Room/Material/Phase) in bim-ootb/viewer/navigate_find.js
# + the room/material DATA compiled into the served _meta.db. Edit shipping code in bim-ootb/viewer/
# (canonical, GH Pages). Whitebox §-log first; SAVE every run to a log file and READ it before any
# conclusion. Test on localhost; HOLD deploy until the user says go. Honour until ✅ DONE.

## ☠ HARD RULE (cost me the worst incident last session)
- **NEVER run `git checkout` / `restore` / `stash` / `reset` on this SHARED, DIRTY tree.** It silently
  wiped uncommitted navigate_find.js work; git could not recover it (recovered only from the browser's
  IndexedDB/Cache because the tab hadn't refreshed). Before ANY tree-mutating git op: `git diff --stat`,
  and if there are changes you didn't make, STOP. To undo your own edit, re-edit by hand. Commit ONLY
  your own paths: `git commit -m "…" -- <path>` (options BEFORE `--`). See [[feedback_no_destructive_git_shared_tree]].
- Cut ceremony/drama. Don't ask low-stakes questions — decide, test, proceed. The user reads §-logs, not prose.

## ✅ DONE + DEPLOYED + fetch-back verified (2026-06-04 → 05)
- **One unified shape-drill** for ALL Find lenses (navigate_find.js, committed bim-ootb@9c884ca):
  tap any leaf → that element's REAL LOD mesh shape lights cyan (reuses A.meshCache geometry +
  the renderer's exact placement: ifc2three + euler(rotX,rotZ,-rotY), scale 1), the PARENT GROUP
  stays SOLID (opaque clones of A._getMaterial), and the REST is x-rayed to **0.2**. No bbox boxes;
  the old room-volume "ghost over all storeys" is gone (room entry draws 0 boxes).
  - Phase: item lit, phase solid. Material: material's elements lit. Room: room's CONTENTS
    (rel_contained_in_space) lit, storey solid. Storey/Disc Type-leaf: type lit, storey/disc solid
    (was isolate/hide — `isolateLeaf` kept in file for easy revert).
  - Core fns: `_drillSelect(set,label,tag,groupSet)`, `_buildShapeMeshes(set,color)`,
    `_dimXrayTo(0.2)`, `_typeShapeDrill(col,val,ifc,label)`. §-tags: `§*_SELECT lit=N phaseSolid=M`,
    `§XRAY_DIM opacity=0.2`, `§SHAPE_MISS hashes_not_streamed=…`. Drill zoom tightened to ×1.6.
  - Cap: solid group caps at `_HL_CAP=4000` (logged, not silent) — a huge phase/storey renders
    solid only up to 4000. In headless tests `lit` < `elems` because geometry wasn't fully streamed;
    a real fully-loaded browser fills in.
- **DATA — 4 SPLIT buildings, _meta.db live on OCI bucket `bim-ootb`/buildings/ (verified online):**
  Terminal rooms=43 storeys=6 materials=41 · Hospital 142/7/17 · Clinic 118/3/12 · LTU_AHouse 332/5/25.
  - Rooms COMPILED from wall flood-fill (`scripts/compile_rooms.py --write`), APPROXIMATE (`≈`-labelled,
    object_type=COMPILED); now also emits `STC_<storey>` IfcBuildingStorey rows + links each room's
    parent_guid so the Room lens groups per floor (was 1 ghost group).
  - Materials: Terminal/LTU bridged from their _extracted.db by `material_rgba` (rgba→dominant name,
    `scripts/backfill_material_names.py`). Clinic/Hospital extracted carry NO material names → labelled
    by nearest colour (`≈ Grey`, `≈ Tan`…) via `scripts/name_materials_by_color.py` (deterministic,
    extracted-from-rgba, `≈`-marked — NOT invented).
- Viewer code = GH Pages (git push, bim-ootb main). Building DBs = OCI bucket `bim-ootb` ONLY
  (region ap-kulai-2, ns ax3cp6tzwuy2). EVERY `oci os object put` needs `--content-type application/octet-stream`.

## ✅ SESSION 2026-06-05 — DEPLOYED LIVE (bim-ootb PR #130 squash-merged → GH Pages, fetch-back verified: v590, IDLE-PARK, _typeItemChildren, _zoomStep all live). Deployed via isolated worktree off origin/main (shared tree was dirty+diverged — never rebased/stashed it).
### NEXT: A.highlight service refactor (user-approved) — consolidate the 5 duplicated box-highlight blocks (picking.js click-select, diff.js, time_machine.js, wizard_classify.js + dead navigate_find _highlightGuids) into ONE service that draws real meshCache shapes; box only as logged fallback for not-yet-streamed. Then #7: rooms/materials → the ~25 inline buildings (compile_rooms.py + name_materials, re-upload). No parking.
- **#1 four-level drill** (navigate_find.js): Storey/Disc → Type → **individual item**. The Type
  leaf now has an expand arrow → `_typeItemChildren(c,col,val,ifc)` lists the items (cap
  `_PHASE_ELEM_CAP=250`); tapping one lights JUST that item, the WHOLE TYPE stays solid (typeSet
  passed as the drill's solid group). Tapping the type TEXT still whole-type-drills as before —
  purely additive. Witness: `§TYPE_SELECT … lit=1343 phaseSolid=4000`, `§TYPE_ITEMS … items=N`,
  the `▸` arrow renders on type leaves.
- **CPU idle fix** (main.js): root cause = the §S286 gate only skipped the GPU *paint*; the rAF
  chain still re-scheduled every frame (main.js animate) → main thread ran `controls.update()` +
  tick fan-out ~60×/s on a static scene (worst on Terminal, the largest model). FIX = **self-parking
  loop**: when `!(_needsRender||streaming||walkModeActive||walkMode||flyActive||_orbiting||
  _pipelinesCompiling)` the loop nulls `_rafId` and STOPS; `markDirty`/controls-change/`start`/`end`
  + a pointerdown/wheel/keydown safety-net call `_startLoop()` to revive. Witness: idle frames
  3s = **2** (was ~180), `§IDLE_GATE park`, `§RENDER_LOOP start total 1→2` (parked loop restarted),
  re-parks after wake, no errors. `_zoomToBox`'s own rAF self-terminates (t≥1) so zoom moves still run.
- **`+/-` = standard zoom in/out** (best practice — KISS, after an over-engineered "tier" draft was
  stripped). `scene.js _zoomStep(dir)`: dolly camera toward(+)/away(−) from `controls.target`
  (×0.8 in / ×1.25 out). `_shortcuts['+']`=in, `['-']`=out (Shift+= → `+`; both keys were free).
  Surfaced in the **Help palette, NOT a pill**: two static rows pushed in scene.js `renderList`
  ("Zoom In" `+`, "Zoom Out" `-`). Witness: cam dist 120→96→77 on `+`, back to 120 on `-`;
  `§ZOOM dir=±1`; Help `hasIn/hasOut=true`; no errors.

## ARCHITECTURE (how the served meta renders)
- Large buildings ship SPLIT: `<B>_meta.db` (elements_meta + element_instances guid→geometry_hash +
  element_transforms + spatial_structure) **+** `<B>_geo.db` (component_geometries = real meshes).
  Loading `?db=…/<B>_extracted.db` makes streaming.js HEAD-probe `<B>_meta.db` → split → loads
  meta+geo (the matched pair; hashes match). The `_extracted.db` itself is a DIFFERENT/legacy
  extraction — do NOT inject rooms there (hashes won't match geo → §BLOB_MISS → bboxes). Rooms +
  materials belong in `_meta.db`.

## TEST LOOP (localhost, §-log first)
- Serve: `python3 -m http.server 8000 --directory /home/red1/bim-ootb` (already staged: Terminal +
  Hospital `_meta.db` with rooms+materials, + their `_geo.db`).
- URL: `http://localhost:8000/viewer/viewer.html?db=/buildings/<B>_extracted.db#bld=T0_<B>`
- Headless probes in /tmp from last session: probe_phase_term.js, probe_mat.js, probe_room.js,
  probe_storey.js, probe_hospital.js — each drives the real UI, dumps §-lines. SAVE to a .log, READ it.
- Playwright lives in bim-ootb/tests/node_modules. Row tap = dispatch pointerup on the row's
  **text span** (`row.children[1]`), expand = the arrow (`row.children[0]`). _treeNode returns a
  fragment [row, childContainer]; childContainer = `row.nextElementSibling`.

## ▶ NEXT SESSION — review & enhance the Find UX further (along these lines)
Open questions / candidate enhancements to weigh with the user (don't build blind — agree first):
1. ✅ DONE (2026-06-05) — 4th level under Storey/Disc Type (storey→type→individual item); single
   item lights while the whole type stays solid. Witness: §TYPE_ITEMS / §TYPE_SELECT. See top.
2. `_HL_CAP=4000` on the solid group — for a big phase/storey only 4000 render solid. Raise, or
   build the solid layer as InstancedMesh-per-hash with no cap (perf check first).
3. Real material names for Clinic/Hospital (currently colour-labelled `≈`) — is there any real source
   (IFC re-extract? surface_styles?) or is colour the honest ceiling?
4. Should Storey/Disc keep an ISOLATE option too (it now shape-drills; `isolateLeaf` still in file)?
5. Room "contents" lit = rel_contained_in_space — compiled rooms are APPROXIMATE (~5/21 recall on
   Duplex ground-truth). Worth tightening the room compiler, or good enough?
6. Zoom/feel polish: ×1.6 drill zoom, the 0.2 dim level, cyan colour, restore-on-deselect.
7. Apply the same compile+materials to the remaining INLINE buildings (25 of 29) if the user wants
   rooms/materials there too (cheap: small single-file re-upload each).

## DON'T TOUCH
- User's sfx is UNCOMMITTED in this tree (sfx.js untracked etc.) + other sessions' work (main.js,
  panels.js, scene.js, sw.js, time_machine.js, erp/*). Commit ONLY your own paths.
- `viewer/navigate_find.js.recovered.bak` + `~/navigate_find.recovered.1933.js` = recovery backups; leave them.
