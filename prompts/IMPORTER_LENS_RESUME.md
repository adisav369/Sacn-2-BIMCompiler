# ⚠ DO NOT REMOVE — Importer-lens deploy + highlight refactor: RESUME for a fresh session
# Scope: (1) review + DEPLOY the drop-IFC importer fix (rooms+materials for imported IFCs),
#        (2) the A.highlight service refactor (consolidate box-highlights → real-shape, user-approved).
# Edit shipping code in bim-ootb/viewer/ (canonical, GH Pages). Whitebox §-log FIRST; SAVE every run
# to a log file and READ it before any conclusion. Test on localhost; deploy via the clean-worktree
# method below. Honour until ✅ DONE.

## ☠ HARD RULES (cost real incidents)
- **NEVER `git checkout`/`restore`/`stash`/`reset`/`rebase`/`pull` on the SHARED bim-ootb tree.** It is
  DIRTY (uncommitted sfx + others) and DIVERGED from origin/main (ERP PRs land remotely). Deploy ONLY
  via an **isolated worktree off origin/main** (see DEPLOY). Commit only your own paths: `git commit -m … -- <path>`.
- **Headless test browsers LEAK CPU.** swiftshader software-WebGL pins ~14 cores if a probe hangs/orphans.
  ALWAYS wrap probes: `timeout --signal=KILL <90s> node probe.js ; pkill -9 -f chrome-headless-shell`, and
  the probe must `try{…}finally{await b.close()}`. Verify `ps -eo comm | grep -i chrome` = clean after. Run
  ≤1 browser at a time (never 25 in parallel). Testing CPU is fine; a *leak* is not.
- Cut ceremony. Known convention (e.g. +/- = zoom in/out) → just do it, no abstractions. The user reads §-logs.

## ✅ ALREADY DONE + LIVE (do not redo)
- **Viewer v590 release (bim-ootb PR #130 merged → GH Pages):** idle-CPU self-park loop (main.js),
  Find item-drill L4 (navigate_find.js `_typeItemChildren`), standard `+/-` zoom (scene.js `_zoomStep`,
  in Help not pill). Fetch-back verified live.
- **Rooms+materials on ALL 25 inline buildings — LIVE on OCI bucket `bim-ootb`/buildings/, witnessed.**
  Recipe (proven, metadata-only so rendering unaffected): `python3 scripts/compile_rooms.py <db> --write`
  (flood-fill IfcSpace rows + rel_contained + storey rows) then `python3 scripts/name_materials_by_color.py
  <db> --write` (rgba→`≈ Colour` names; SKIP if the DB already has real material_name, e.g. Schependomlaan).
  Upload: `oci os object put -bn bim-ootb --name buildings/<B>_extracted.db --file <db> --content-type
  application/octet-stream --force`. Witnessed in browser: Duplex room elems=7 lit=7, mat 99/99;
  Hospital_3 room 20/16, mat capped at _HL_CAP=4000 (huge 56k material). §LENS_PROBE room/material/phase=true.
- The 4 SPLIT buildings (Terminal/Hospital/Clinic/LTU_AHouse) already had rooms+materials in their _meta.db.

## ▶ TASK 1 (APPROVED — DEPLOY IT): drop-IFC importer fix
Done by agent, **uncommitted** in bim-ootb/viewer/ (`git status` shows M import_db_builder.js, M import_worker.js).
syntax-checked (node --check both = OK). What it does:
- `import_db_builder.js` (~L86): creates `spatial_structure(guid,type,name,parent_guid,object_type,predefined_type,
  center_x/y/z,size_x/y/z)` + `rel_contained_in_space(element_guid,space_guid)` — schema MATCHES served DBs
  (viewer filters `type='IfcSpace'`). Always created (empty if no spaces) → graceful. material_name now written.
- `import_worker.js`: collects native IfcSpace (metadata only — NOT in PRODUCT_TYPES so its box never renders),
  wires parent_guid + rel_contained via IFCRELCONTAINEDINSPATIALSTRUCTURE + IFCRELAGGREGATES, extracts
  material_name via IFCRELASSOCIATESMATERIAL→IfcMaterial.Name (absent → NULL, non-invent), space-bbox pass for center/size.
- §-witness tags: `§SPATIAL_TABLES`, `§SPATIAL_EXTRACT spatial_rows= spaces= rel_contained=`, `§MATERIAL_NAMES
  associations=`, `§NAMED_MATERIALS renderable_with_material_name=n/total` (worker + builder).
- **HONEST LIMIT (state it, don't fix):** native IfcSpace ONLY — JS worker can't flood-fill like the Python
  pipeline. IFCs with no IfcSpace get the (empty) tables + colour materials but no Room lens. Most Revit/ArchiCAD
  exports have spaces, so they're covered.
- **TO DO:** (a) READ the diff (`git diff -- viewer/import_db_builder.js viewer/import_worker.js`), sanity-check
  against the style of those files. (b) WITNESS: import a real IFC with spaces on localhost (drop-import flow) and
  read the §-log tags — confirm spatial_rows>0, named materials>0, then open Find → Room/Material lit. A space-LESS
  IFC should still import cleanly (empty spatial tables, colour mats). Save the log, read it. (c) DEPLOY via the method below.

## ▶ TASK 2 (APPROVED, NOT STARTED): A.highlight service refactor
The user confirmed this. Currently the wireframe-box highlight block (`BoxGeometry→EdgesGeometry→LineSegments(_bboxMaterial)`)
is DUPLICATED in 5 files: **picking.js (click-to-select — the big one), diff.js, time_machine.js, wizard_classify.js**,
+ a DEAD `_highlightGuids` in navigate_find.js (no callers — delete it). Plan: one service
`A.highlight(guids,{color,dimRest,allowBoxFallback})` that draws the REAL meshCache shape (reuse navigate_find's
`_buildShapeMeshes` engine), box ONLY as a logged fallback for not-yet-streamed hashes (never default). Migrate the 5
callers one at a time, witness each with: accounting `lit + not_streamed == elems`, and "overlay geom is BufferGeometry
from meshCache, NOT BoxGeometry" (probe: `o.geometry.type` + `geomIsFromMeshCache` check — see prior session's probe_mesh.js).
Boxes are useful ONLY as the un-streamed fallback; otherwise ban. This IS good separation-of-concern (cross-cutting concern,
one owner, callers delegate) — the abstraction is warranted here (unlike +/-).

## DEPLOY (clean-worktree method — shared tree is dirty+diverged)
1. Bump `viewer/sw.js` CACHE_VERSION (currently v590 → **v591**). Bump `navigate_find.js?v=` etc. in main.js IF you changed those modules.
2. Commit ONLY your paths on the dirty tree: `git -C /home/red1/bim-ootb commit -m "…" -- viewer/<files>`.
3. `git worktree add -b feat/<name> /tmp/wt origin/main` ; `git -C /tmp/wt checkout <yourcommit> -- viewer/<files>` ;
   `node --check` each ; `git -C /tmp/wt commit -m "…" -- viewer/<files>` ; `git -C /tmp/wt push -u origin feat/<name>`.
4. `gh pr create --base main` ; wait `gh pr checks <#>` (e2e + fast-checks green) ; `gh pr merge <#> --squash --delete-branch`.
5. Verify: `pages-build-deployment sha=<mergeSHA> success`, then curl live https://red1oon.github.io/bim-ootb/viewer/sw.js → new version.
6. `git worktree remove /tmp/wt --force`.
- Building DBs deploy by direct OCI upload (bypass SW, live on next load) — no PR needed. EVERY put needs `--content-type`.

## REPO / STATE
- Repo map: see MEMORY.md (canonical = bim-ootb/; bim-compiler = compiler+scripts SOURCE). Local bim-ootb/buildings/
  copies are STALE/drifted vs OCI — OCI is truth. (Duplex + Hospital_3 local were re-synced from OCI during witness.)
- Witness probe template (bounded): see prior session probes /tmp/probe_wit.js, probe_truth.js, probe_mesh.js (URL
  `localhost:8000/viewer/viewer.html?db=/buildings/<B>_extracted.db&bld=T0_<B>`; serve `python3 -m http.server 8000 --directory /home/red1/bim-ootb`).
