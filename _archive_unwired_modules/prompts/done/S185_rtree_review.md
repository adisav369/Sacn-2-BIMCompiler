# ⚠ DO NOT REMOVE
# Scope: S185 — RTree sandbox review + Duplex geometry investigation
# Read the log after every run. No claims without §PROOF log lines.
# STATUS: SPEC — start at session open.

## Context

S184 DONE: RTree MESH speed — viewport-centre query, batch transforms, pre-warm
on drill-in. MESH loads in <1s when pre-warmed, 2-3s cold. GN mode halted.

## Resume with sandbox RTree

1. Load `sandbox_1M_extracted.db` via RTree Preview
2. Verify S184 fixes:
   - Viewport-centre query: pan → +ARC → meshes appear where camera looks
   - Pre-warm: drill into building → check `§PROOF PREWARM` fires within 2s
   - Batch transforms: `sql=<50ms` in LOAD_MESH log
   - Collections persist: load Hospital ARC, fly to Clinic, load Clinic ARC → both remain
   - Unicode discipline bars in cockpit
   - Dedup: search "door" → Terminal visible in results (141 doors)
   - Material colors: `mat=N` in log, visible in Solid mode (color_type=MATERIAL)
3. Test shred workflow: select objects → SHRED → only selected removed

## Duplex Geometry Corruption — INVESTIGATE

**Symptom:** Duplex and small houses show blocky walls (no opening cutouts) in
Full Load. Terminal, Hospital, SampleHouse are fine.

**Documented in:** `docs/DuplexAnalysis.md` §S184

**What S184 changed that could affect Full Load:**
- Added `building` column (ALTER TABLE) to 36 extracted DBs including Duplex — data untouched
- Added `rotation_x/y/z` columns (ALTER TABLE) to 22 extracted DBs — Duplex already had them, skipped
- Added `_db_path_cache`, `_model_offset`, state clearing in `_load_common()` (Full Load setup)
- Bypassed `gn_mode` gate — Full Load always uses `_load_per_element` now (was same when gn_mode=False)
- `stage2_library_linker.py` (actual Full Load engine) — ZERO changes
- `component_library.db` — 970 new Duplex re-extraction hashes added (but original hashes still present)
- `library.blend` — restored to pre-S184 (289MB, Apr 12 bake). NOT re-baked yet.

**Investigation path:**
1. Load Duplex via Full Load with current library.blend
2. Select a wall object in viewport → check `mesh.vertices` count in Python console
3. Compare against DB: `SELECT vertex_count FROM component_geometries WHERE geometry_hash='<hash>'`
4. If viewport mesh has fewer vertices than DB → bake lost geometry
5. If same vertex count → rendering/material issue, not geometry

**Backup:** `Duplex_extracted_original.db` preserved
**Standby bake:** `library_s184_test.blend` (includes re-extracted Duplex hashes)
may be available — check if bake completed.

## library.blend bake standby

A re-bake was started in S184 as `library/library_s184_test.blend` with 121,441
meshes (120,471 original + 970 new Duplex re-extraction hashes). If it completed:
```
mv library/library.blend library/library_pre_s185.blend
mv library/library_s184_test.blend library/library.blend
```
Then test Duplex Full Load to see if re-extracted geometry fixes the blocky issue.

## What NOT to change

- Stingy Mesh Loader (FedRTreeLoadMesh) — working, <1s loads
- Pre-warm mechanism — working
- RTree GPU path — untouched
- GN/DLOD files — halted

## Standing rules

- Spec before code — this file is the spec
- Read the log after every run
- Camera IS the selector — MESH loads what the viewport shows
