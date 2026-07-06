# ⚠ DO NOT REMOVE
# Scope: S185-DX — Duplex geometry in Sandbox1M and library.blend
# Read the log after every run. No claims without §PROOF log lines.
# STATUS: OPEN — original extraction process must be restored.

## Current Symptom (S185 finding)

In Sandbox1M RTree+MESH, Duplex shows **partial opening cutouts but solid boxes
inside the openings**. The wall mesh geometry IS correct (cutouts exist in the
mesh). The boxes are **50 `IfcOpeningElement` elements** extracted as visible
solid ARC geometry — they fill the void that should be open.

This is a **parametric fallback violation**: a void instruction rendered as
geometry is not allowed under the no-invent rule. The opening must be empty
space, not a placed element.

**The geometry BLOBs in `component_library.db` are correct.** Wall hashes have
5 unique Z levels (bottom / sill / lintel / above-lintel / top), confirming
proper cutout topology. The problem is in the extracted DB, not the library.

## Root Cause (S185 investigation)

The Apr 13 11:41 batch re-extracted Duplex (and all small buildings) using
`extractIFCtoDB_open.py` instead of `extractIFCtoDB.py`. These two extractors
differ fundamentally:

| Extractor | Method | `IfcOpeningElement` |
|-----------|--------|---------------------|
| `extractIFCtoDB.py` | `geom.iterator()` | Consumed as boolean cut — never emitted |
| `extractIFCtoDB_open.py` | `create_shape()` per `IfcProduct` | Emitted as solid box |

`_open.py` is only for `_merged.ifc` files from `extract_merge_disciplines.py`.
Duplex is a federated IFC — similar shape but wrong tool.
See `docs/DuplexAnalysis.md` §Extractor Choice for the full rule.

## Lessons Learnt — History of the Breakage

1. **S173** — library pipeline introduced. Duplex was pristine (648 hashes,
   correct geometry, proper wall cutouts). `geom.iterator()` handles boolean
   subtraction internally — openings never appear as elements.

2. **S175** — `bake_all_sandbox.sh` added. Duplex in PATH 2 (re-extract from
   IFC via `extractIFCtoDB.py`). Library expanded to 120K meshes. Still correct.

3. **Apr 13 11:41** — unknown batch re-extracted ALL buildings simultaneously
   using `extractIFCtoDB_open.py`. This replaced correct extracted DBs with
   broken ones containing 50 IfcOpeningElement rows each.

4. **S184** — corruption noticed. Mis-diagnosed as library.blend bake issue
   (the visual symptom looked like geometry corruption). Investigation trails:
   - library.blend swap (277MB → 281MB) — broke sandbox RTree, reverted
   - BLOB bytes verified correct — ruled out geometry corruption
   - Eventually traced to `element_instances` containing opening GUIDs

5. **S185** — `extractIFCtoDB_open.py` identified as the cause. Fix applied
   (`IfcOpeningElement` added to `SKIP_CLASSES`). But re-extraction with
   `_open.py` produced 935 hashes not in library (different hash method:
   `create_shape()` bbox-centred vs `geom.iterator()` transform-matrix).
   `extractIFCtoDB.py` re-extraction gives correct 648 hashes, 100% library
   coverage, BBOX PASS — but the current `library.blend` (277MB) was baked
   before Duplex hashes were populated, so Duplex still shows 0 elements.

## The Actual Blocker

`library.blend` (277MB, baked Apr 12 03:15) does **not** contain the 648 Duplex
mesh datablocks. The 281MB `library_s184_test.blend` does, but using it breaks
sandbox RTree LOD pulling (under investigation).

Until `library.blend` is re-baked to include Duplex hashes, or the sandbox
issue with `library_s184_test.blend` is resolved, Duplex will load 0 elements.

## Required Action

1. **Identify what broke sandbox** with `library_s184_test.blend` (281MB).
   Hypothesis: sandbox element_instances reference hashes from the `_open.py`
   batch that are in 281MB but structured differently, causing LOD pull failure.

2. **Restore original extraction** — re-extract Duplex with
   `extractIFCtoDB.py --library` so its hashes are fresh in
   `component_library.db`, then re-bake `library.blend` to include them.

3. **Do not use `_open.py` for Duplex** — ever. Rule documented in
   `docs/DuplexAnalysis.md` §Extractor Choice.

## What NOT to change

- `extractIFCtoDB.py` — the pristine original, never touch
- Terminal, Hospital, Clinic extracted DBs — working, do not re-extract
- `component_library.db` geometry BLOBs — correct, no purge needed
- Sandbox DB element counts — read-only during this investigation

## Files

| File | Role |
|------|------|
| `docs/DuplexAnalysis.md` §S184, §S185, §Extractor Choice | Full diagnosis |
| `DAGCompiler/lib/input/Duplex_extracted_original.db` | Pre-breakage backup (648 hashes) |
| `DAGCompiler/lib/input/Duplex_extracted.db` | Re-extracted with `extractIFCtoDB.py` — correct hashes, needs library re-bake |
| `library/component_library.db` | 121,441 hashes — correct BLOBs |
| `library/library.blend` | 277MB — missing Duplex 648 hashes |
| `library/library_s184_test.blend` | 281MB — has Duplex, breaks sandbox (under investigation) |
| `scripts/bake_library_blend.py` | Re-bake tool — unchanged since S173 |
| `scripts/extractIFCtoDB_open.py` | Fixed: `IfcOpeningElement` now in SKIP_CLASSES |
