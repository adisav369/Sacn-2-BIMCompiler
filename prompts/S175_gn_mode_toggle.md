# S175 — GN Mode Toggle: Dual-Path Viewport Architecture

## Scope
Implement GN ON/OFF toggle for the Library linker path, so users can switch between:
- **GN OFF (default):** per-element objects in Outliner, full colors, selection, element names — daily backoffice work
- **GN ON:** per-discipline GN point clouds (few Outliner items), DLOD active, smaller .blend saves — scale/presentation mode

## Prerequisites (from S174)
- `dlod_handler.py` exists and is wired (blend_cache + __init__.py)
- Library linker uses `link=False` (local meshes, material slots work, `diffuse_color` set)
- 3 buildings extracted: Clinic (16K), Hospital (64K), Terminal (49K merged)
- Library: 41,372 meshes in library.blend (118MB)
- `§PROOF MATERIALS` log shows 3-path breakdown (direct/surface_styles/discipline)
- Terminal merged IFC at `DAGCompiler/lib/input/IFC/TerminalMerged.ifc`

## Tasks

### 1. GN Mode Library Linker
Refactor `stage2_library_linker.py` to support GN mode:
- When GN ON: create one GN point cloud per discipline (same as `create_cache_gn_instances` pattern in blend_cache.py)
  - Each point has: `hash_index` (int), `rotation` (Euler XYZ), `scale` (vec3)
  - GN node tree: Instance on Points → pick mesh from template collection by hash_index
  - Templates from library.blend (appended, local)
  - DLOD handler hooks in automatically
- When GN OFF: current per-element object path (unchanged)
- Toggle: collection visibility in Outliner — show Library collections OR GN collection
- Both can coexist in same scene

### 2. Test Reduced .blend Size
- Load Hospital via GN mode
- Save .blend → measure file size
- Compare with: GN OFF save, old tessellation save
- Log: `§PROOF BLEND_SIZE gn_on=XMB gn_off=YMB`

### 3. Reopen Auto GN Mode
- On file open, detect if GN collections exist → auto-activate DLOD handler
- If GN OFF collections exist → normal open, no DLOD
- Log: `§FINE open: GN mode detected, DLOD handler activated`

### 4. Toggle Non-GN
- User can toggle: hide GN collection, show Library collections → per-element mode
- DLOD handler deactivates when GN collection hidden
- No data loss — both representations exist, just visibility toggle
- Log: `§FINE toggle: GN→Library (DLOD off)` / `§FINE toggle: Library→GN (DLOD on)`

### 5. Verify Colors
- GN OFF: `diffuse_color` fix from S174 — verify colors in SOLID mode
- GN ON: discipline-level colors via GN attribute materials
- Log: `§PROOF COLOR_VISIBLE` with viewport shading + material samples

### 6. Search in GN Mode
- Element search queries extracted.db → highlights GN instance points by index
- Click in viewport → reverse-lookup point index → show element metadata in properties panel
- Spec only if time permits — this is a stretch goal

## Standing Rules
- FINE logging on every operation — the log speaks, no manual checking
- `diffuse_color` trap: always set both `mat.diffuse_color` AND `bsdf.inputs["Base Color"]`
- Backward compatible: Hospital/Clinic/Terminal must work in both modes
- No disruption to Bonsai core save/open
- Read the log after every run

## Reference
- `internal/DLOD_SPEC.md` — DLOD handler design
- `internal/FULL_LOADER2_SRS.md` §13 — old loader retirement
- `internal/THIN_SAVE_SPEC.md` §9 — DLOD supersedes thin save
- `docs/TerminalAnalysis.md` §S174 — alignment fix
- `blend_cache.py` `create_cache_gn_instances()` — GN instance pattern to follow
- `dlod_handler.py` — already wired, needs GN point cloud data from Library linker
