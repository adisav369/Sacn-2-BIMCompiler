# S163 — Triage: R-tree Instant Bbox Load on .blend Open

**Spec:** `docs/Enterprise.md` §"There Is More — The Streaming `.blend`"
**Related coder prompt:** `prompts/S162_streaming_blend.md`
**Target repo:** `/home/red1/IfcOpenShell/src/bonsai/bonsai/bim/module/federation/`

You are a **triage expert**, not a coder. One bounded task: assess how the
streaming `.blend` feature can show bounding boxes **instantly on file open**
before any mesh geometry is fetched from the DB. Read the code, answer the
questions below, then write your findings in plain language.

---

## The problem

`S162_streaming_blend.md` specifies a `blend_load_post` handler that rehydrates
full LOD meshes from the DB on file open. That fetch takes the same time as
Stage 2 today (9–27 seconds for large buildings).

The **gap**: the user opens the `.blend` and sees nothing for up to 27 seconds.
We already solved this in the live federation loader with Stage 1 wireframes —
44K elements in **0.5 s** from `elements_rtree`. The streaming open should do
the same: show coloured bbox wireframes instantly, then stream LOD meshes in
the background.

---

## What already exists (read these files)

1. **Stage 1 wireframe loader** —
   `federation/loading/stage1_wireframes.py`
   Query: `SELECT guid, discipline, r.minX…maxZ FROM elements_meta m JOIN elements_rtree r ON m.id = r.id`
   Creates edge-only mesh (8 vertices, 12 edges, no faces) per element.
   Validated: 44,190 elements in 0.5 s.

2. **Fast bbox loader (alternative)** —
   `federation/core/fast_bbox_loader.py`
   Reads directly from the R-tree. Creates cube meshes (8 vertices, 12 tris).
   Target stated: 2 s for 49K elements.

3. **Bbox GPU overlay** —
   `federation/bbox_visualization.py`
   Discipline colour palette already defined (`DISCIPLINE_COLORS`).

4. **Streaming handler stub** (to be written per S162) —
   `federation/loading/streaming_blend.py` → `blend_load_post`
   Will rehydrate full LOD meshes from `base_geometries` / `component_geometries`.

---

## Questions for the triage expert

Answer each one based on reading the existing code — no invention.

**Q1 — Can `blend_load_post` call Stage 1 first?**
Can `blend_load_post` call `stage1_wireframes.create_wireframe_boxes()` immediately
(synchronously, sub-second), then hand off the full LOD rehydration to a
background thread or Blender's modal operator? What is the blocking constraint?

**Q2 — R-tree availability**
When a streaming `.blend` is opened, the DB path comes from
`BIMFederationProperties.federation_database_path`. Is `elements_rtree`
guaranteed to exist in that DB? Is `elements_meta` joined correctly?
Check `extract_ifc_to_database.sh` or the schema for the table names.

**Q3 — Object identity conflict**
`blend_load_post` will create Blender objects with `geometry_hash` custom props
(stubs from save). `stage1_wireframes` creates *new* objects named `WF_{guid}`.
If we run Stage 1 inside `load_post`, do we get duplicate objects? What is the
right approach — reuse the existing stubs and set wireframe display on them, or
create new WF_ objects and remove stubs?

**Q4 — GPU overlay vs mesh objects**
`bbox_visualization.py` draws bboxes as a GPU overlay (no Blender objects, no
Outliner entries). `stage1_wireframes.py` creates real mesh objects.
For the streaming open, which is preferable? Consider: the stubs from `save_pre`
are real objects already in the Outliner — does the GPU overlay work on top of
them, or is the mesh-object approach cleaner?

**Q5 — Threading safety**
Blender's Python API is not thread-safe. If LOD rehydration runs in a background
thread, it cannot call `bpy.data.meshes.new()` or `obj.data = mesh`. Is there a
safe pattern already used in this codebase (modal operator + timer, or
`bpy.app.timers`)? Point to the file/line where it's used.

---

## Deliverable

A short findings report (plain text, no code changes):

```
FINDING 1 — [Q1 answer]
FINDING 2 — [Q2 answer]
FINDING 3 — [Q3 answer]
FINDING 4 — [Q4 answer]
FINDING 5 — [Q5 answer]
RECOMMENDATION — Proposed load_post sequence: step 1 → step 2 → step 3
RISK — Any showstoppers that block the instant-bbox approach
```

Do not write implementation code. Flag any assumption that requires a coder to verify.
