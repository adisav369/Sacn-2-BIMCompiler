# S173 — Continue 1M Federation Mission

## DO NOT REMOVE
Scope: Continue S170-S172 work. Test LOD with Hospital (63K), verify discipline
alignment + rotation, re-extract remaining buildings, rebuild sandbox_1M.db.
Read the log after every run.

You are a coder. One bounded task per section.

---

## What was done (S170-S172 session, 2026-04-11)

### S170 — LOD Manager (committed to IfcOpenShell repo)
- `federation/lod_manager.py` — KDTree spatial index, discipline toggle, camera
  distance query (50m radius, 0.5s timer, batch limit 500/tick)
- `federation/blend_cache.py` — lazy template creation (empty meshes, ARC pre-fill
  only), Euler→Rotation GN node fix, rotation applied in small model path,
  LOD-aware `restore_template_meshes`, batch-commit for concurrent extraction
- `federation/__init__.py` — LOD timer lifecycle, depsgraph handler for discipline
  visibility change detection, LOD rebuild on file open
- **NOT YET TESTED** with >50K element DB — Hospital (63K) should be ready after extraction

### S172 — Iterator + Parallel Merge (committed to bim-compiler, multiple fixes)
- `extractIFCtoDB.py` — switched from `create_shape()` to `geom.iterator()`
  (v0.8 built-in C++ dedup, same as Bonsai uses)
- `extract_merge_disciplines.py` — parallel discipline extraction (all disciplines
  concurrently via Popen, merge sequentially), `--library` flag, geolocation
  alignment using `auto_xyz2enh` (same logic as IfcPatch MergeProjects),
  rotation columns in merge schema, `fix_unit_scale()` for mm→metre conversion
- `align_discipline_origins.py` — standalone post-DB alignment tool
- `reference/README.md` — IfcOpenShell/Bonsai community alignment doc

### Bugs found and fixed during session
1. **Rotation columns missing from merge schema** — all per-discipline builds lost
   rotation data. Fixed: added rotation_x/y/z to schema + merge copy.
2. **Unit scale not applied** — `geom.iterator()` returns native IFC units (mm for
   mm-unit files). Old `fix_mm_outliers` only scaled outliers > 500. Replaced with
   `fix_unit_scale()` that reads `calculate_unit_scale()` and scales ALL coords.
3. **Dead `outliers` variable** — leftover from old code crashed merge, causing
   HVAC/Plumbing/STR disciplines to never merge (Clinic lost roofs again). Removed.
4. **Euler→Rotation GN node** — Blender 5.0 needs conversion node between
   FLOAT_VECTOR attribute and Rotation input on Instance on Points.
5. **`obj.rotation_euler` never set** — small model path had rotation data in query
   but never applied it to objects.
6. **`import time` missing** — extractIFCtoDB.py used `time.time()` without import.
7. **Pre-existing indentation bugs** — `lib_conn` at column 0 in blend_cache.py (×2).

### Extraction status (2026-04-11 ~05:00)

**Running NOW (parallel extraction with unit scale + rotation + alignment):**
- Hospital (7 disciplines) — PIDs active, big files ARC/MECH/PLB still tessellating
- Terminal (8 disciplines) — PIDs active, ARC (85 MB) still tessellating
- Clinic (5 disciplines) — PIDs active, HVAC/Plumbing still tessellating

**Check logs:** `scripts/logs/extract_{Hospital,Terminal,Clinic}_log.txt`
**WARNING:** Logs may have stale output from killed previous runs. Look for
`§UNIT_SCALE`, `merged`, and `✓ Done:` lines to confirm current run completed.
Truncate logs before restarting: `> scripts/logs/extract_X_log.txt`

**Done (S168 + rotation + library) — single IFC extraction:**

| Building | Elements | Notes |
|----------|----------|-------|
| SampleHouse | 65 | |
| VogelGesamt | 160 | |
| SmileyWest | 531 | |
| Duplex | 1,169 | |
| HITOS | 1,000 | re-extracted with normalization |
| HospitalGarage | 1,463 | |
| BimWhale_Advanced | 2,176 | |
| HHS_Office_ARC | 2,745 | |
| HHS_Office_MEP | 3,964 | |
| Ifc4_Revit | 5,002 | |
| WBDG_Office_MEP | 5,678 | |
| HHS_Office_Federated | 7,042 | mm-unit, may need unit scale check |

**Need re-extraction:**

| Building | Issue |
|----------|-------|
| LTU_AHouse | partial (4,785 of 126K). Use per-discipline merge with disc-map |
| Ifc4_SampleHouse | partial S168, no rotation |
| Esplanades | pre-S168 |
| ~20 others | pre-S168, see DAGCompiler/lib/input/ |

### Deleted files
- `Clinic_Federated.ifc` — dropped entire STR discipline (bad IFC merge)
- `HHS_Office_Federated.ifc` — same problem

---

## Issues to solve

### 1. Test S170 LOD with Hospital (63K) — PRIORITY
Load Hospital in Blender. Expected behavior:
- `create_cache_gn_instances` triggers (>50K threshold)
- Phase 2: empty templates created (not all 63K meshes baked)
- Phase 4b: only ARC templates pre-filled (~15K of 63K)
- Phase 8: LOD manager initializes, timer starts
- Toggle MEP discipline ON → MEP templates load from library
- Move camera → nearby templates fill, distant ones clear
- Ctrl+S → thin .blend (~15 MB, meshes stripped)

If it doesn't work, check:
- Does `_GN_Templates` collection exist?
- Do template meshes have `geometry_hash` custom property?
- Is `component_library.db` accessible from Blender?
- Check Blender console for `[S170]` log lines

### 2. Discipline alignment — VERIFY after extraction completes
Geolocation alignment (`auto_xyz2enh`) + unit scale (`fix_unit_scale`) are
now in the merge pipeline. Terminal ARC has site offset (-244, 132, -3) in
mm while others are (0,0,0). The `_compute_alignment` function should
correct this during merge. VERIFY in Blender:
- Do all disciplines overlap correctly?
- If not, check `§ALIGN` and `§UNIT_SCALE` lines in logs
- Hospital had correct XY alignment but may have Z issues (site at 165.8m)
- The standalone `align_discipline_origins.py` was tested but made Terminal
  WORSE — the pipeline-integrated approach should work better since it
  applies corrections before merge, not after

### 3. Rotation verification
Rotation columns now in merge schema (was missing — all previous per-discipline
extractions lost rotation). Verify in Blender:
- Clinic: doors/furniture should face correct directions (was "all facing one way" before fix)
- Hospital: MEP equipment oriented correctly
- Small model path: `obj.rotation_euler` now set (was missing)
- GN path: `Euler to Rotation` node added (Blender 5.0 requirement)

### 3b. Unit scale verification
mm-unit IFCs (Terminal, Clinic HVAC/Plumbing) now get `fix_unit_scale()` applied.
Verify: element coordinates should be in metres (a room is ~5m, not 5000).
If still in mm, check `§UNIT_SCALE` log lines.

### 4. Re-extract LTU_AHouse (126K elements)
Largest building. Use per-discipline merge:
```bash
python3 -u scripts/extract_merge_disciplines.py \
  --ifc-dir DAGCompiler/lib/input/IFC/UNMERGED \
  --pattern "LTU_AHouse_*.ifc" \
  --output DAGCompiler/lib/input/LTU_AHouse_extracted.db \
  --library library/component_library.db \
  --disc-map LTU_AHouse_PLB=MEP LTU_AHouse_SAN=MEP LTU_AHouse_HEAT=MEP \
             LTU_AHouse_VENT=MEP LTU_AHouse_HVAC=MEP LTU_AHouse_ELEC=MEP
```
Check `DAGCompiler/lib/input/IFC/UNMERGED/` for actual filenames.

### 5. Re-extract remaining ~20 buildings
All pre-S168 buildings need re-extraction with:
- `geom.iterator()` (S172)
- `--library` for component_library.db
- Local coords + rotation (S168/S169)
- Site normalization
Use single IFC extraction for buildings without discipline splits.

### 6. Rebuild sandbox_1M.db
After all buildings re-extracted, merge into sandbox_1M.db.
Script needed (or modify existing sandbox builder).

### 7. Save_pre/load_post test with real data
S169 handlers strip/restore meshes on save/load.
S170 made restore LOD-aware (only restore loaded hashes).
Test: open Hospital, navigate, Ctrl+S, close, reopen. Should restore.

---

## Key learnings from this session

1. **Use the community API** — `geom.iterator()` not `create_shape()`.
   Check `reference/README.md` §IfcOpenShell/Bonsai before coding.

2. **DB-level merge > IFC-level merge** — avoids dropped disciplines
   (Clinic lost 1,100 STR elements including 12 roofs in IFC merge).

3. **Parallel discipline extraction works** — wall-clock = slowest discipline,
   not sum of all. Hospital 7 disciplines in ~10 min vs 2 hours single file.

4. **Rotation was missing from merge schema** — all per-discipline extractions
   lost rotation data. Fixed: rotation columns in merge schema + copy during merge.

5. **Geolocation alignment needed** — different discipline IFCs can have different
   IfcSite placements. Use `auto_xyz2enh` from IfcOpenShell (same as MergeProjects).

6. **component_library.db is append-only** — never mutate, never delete. Extraction
   adds new hashes, Blender reads them. LOD fills/clears template meshes but
   never writes to the library.

---

## Files reference

| File | Location | What |
|------|----------|------|
| LOD manager | `federation/lod_manager.py` (IfcOpenShell repo) | KDTree + discipline toggle + camera distance |
| Blend cache | `federation/blend_cache.py` (IfcOpenShell repo) | Lazy templates, GN setup, save/restore |
| Init | `federation/__init__.py` (IfcOpenShell repo) | Handlers, timer, depsgraph |
| Extractor | `DAGCompiler/python/extractIFCtoDB.py` | Core extraction with iterator |
| Merge | `scripts/extract_merge_disciplines.py` | Parallel discipline extraction + DB merge |
| Align | `scripts/align_discipline_origins.py` | Post-DB alignment (standalone) |
| Community doc | `reference/README.md` §IfcOpenShell/Bonsai | What we use, what to adopt |
| IFC analysis | `DAGCompiler/lib/input/IFC/IFCAnalysis.md` | Extraction pipeline docs |
| Extraction README | `scripts/README_extraction.md` | Script inventory + design decisions |

## DO NOT
- Use `create_shape()` — use `geom.iterator()` (S172)
- Use IFC-level merge for production — use DB-level merge
- Modify component_library.db from IFCtoBOM (read-only consumer)
- Bbox-center library meshes (tack point = IFC origin, ALWAYS)
- Save mesh BLOBs in .blend files (S169 strips them)
