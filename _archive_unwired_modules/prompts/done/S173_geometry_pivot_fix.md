# S173 — Geometry Pivot Fix + Extraction Pipeline Hardening

## What was done

### Root cause chain (discovered in order):
1. **Unit scale bug**: `geom.iterator()` returns metres natively — old `fix_unit_scale()` was double-converting. Fixed: no manual scaling in extractor.
2. **Per-discipline normalization**: each discipline subtracted its own centroid, destroying inter-discipline alignment. Fixed: `--skip-normalize` flag, merge script handles post-merge normalization.
3. **Database lock**: 7 parallel extractors writing to same library.db. Fixed: extractors write to temp DBs, merge script copies to library sequentially.
4. **Mesh pivot offset**: `USE_WORLD_COORDS=False` returns local verts offset from (0,0,0) by up to 121m. GN `Instance on Points` rotates around (0,0,0) → spikes. Fixed: recentre mesh to origin, absorb centroid into placement centre.

### Files changed:
- `DAGCompiler/python/extractIFCtoDB.py` — unit scale removal, mesh recentring, `--skip-normalize`, `§PROOF` block (ROT_TRUTH, SCALE, DEDUP, etc.), `BIM_LOG_LEVEL` gating
- `scripts/extract_merge_disciplines.py` — removed `--library` from subprocess, sequential library copy, `§PROOF` block (SCALE_*, ALIGN_*, LIBRARY_RESOLVE), persistent `.log` file, `§DIAG` dump
- `scripts/stress_blender_test.py` — loader reads `element_transforms` rotation, RECONSTRUCT proof, `BIM_LOG_LEVEL`
- `scripts/test_rotation_truth.py` — standalone rotation truth test (DB + library + IFC)
- `IfcOpenShell/.../federation/stage2_tessellation_loader.py` — reads rotation_x/y/z, applies `rotation_euler`
- `IfcOpenShell/.../federation/blend_cache.py` — `§PROOF BBOX_MATCH` + `§PROOF PIVOT_CHECK`
- `DAGCompiler/lib/input/IFC/IFCAnalysis.md` — S173 warning about iterator returning metres
- `docs/DISC_VALIDATE_SRS.md` §6.2.1 — port graph spec link
- `docs/DISC_VALIDATION_DB_SRS.md` §1b — S173 implementation refs

## What's running
- Hospital re-extraction with pivot fix (background, should be done)

## What's next (resume here)
1. **Verify pivot fix**: re-run standalone test on the new Hospital extracted DB:
   ```bash
   python3 scripts/test_rotation_truth.py \
     --db DAGCompiler/lib/input/Hospital_extracted.db \
     --library library/component_library.db \
     --ifc-dir DAGCompiler/lib/input/IFC/UNMERGED \
     --pattern "Hospital_IFC4_*.ifc"
   ```
   Also run the GN_SIM standalone proof (see session notes).

2. **Full Load in Blender**: restart Blender, load Hospital. Check `gn_cache_log.txt` for:
   - `§PROOF BBOX_MATCH PASS`
   - `§PROOF PIVOT_CHECK PASS`
   - No spikes in viewport

3. **Re-extract Clinic**: with the fixed pipeline (same merge script). Verify both work.

4. **Re-extract all multi-discipline buildings**: LTU_AHouse, Hospital, Clinic — parallel merge with pivot fix.

5. **Port graph extraction** (§1b): extract `IfcDistributionPort` + `IfcRelConnectsPorts` into `port_elements`/`port_connections` tables. Hospital has 85K ports, 43K connections. Spec in DISC_VALIDATION_DB_SRS §1b.

6. **Switch to INFO**: once Full Load proven, set `BIM_LOG_LEVEL=INFO` to skip proof checks.

## Key findings documented
- `IFCAnalysis.md` §0: iterator returns metres, no manual scaling
- `DISC_VALIDATE_SRS.md` §6.2.1: port graph extraction spec
- Standalone test: `scripts/test_rotation_truth.py` — full chain proof
- GN pivot issue: mesh centroid must be at origin for GN Instance on Points rotation
