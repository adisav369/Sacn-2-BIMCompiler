# ⚠ DO NOT REMOVE
# Scope: S191 — RTree federation viewer UX polish
# Read the log after every run. No claims without §PROOF log lines.
# STATUS: IN PROGRESS

## Context

S189 delivered the core RTree federation viewer: BLOB tessellation, discipline-based
baking, live-link architecture, Outliner hierarchy, distro round-trip. The viewer is
functional and proven at city-scale (1M elements, 12 disciplines).

This session focuses on **user experience polish** — visual feedback, navigation feel,
and the small details that make the difference between a tool and a product.

## What to implement

### 1. Discipline bar progress coloring

Currently discipline bars in the N-panel cockpit are flat colored. Change to a
**gradient progress indicator**:

- **Red** → 0% loaded (no meshes, bbox only)
- **Orange** → 1-25% loaded
- **Yellow** → 25-75% loaded
- **Green** → 75-99% loaded
- **Bright green** → 100% fully loaded

The bar fill width shows element count. The color shows load completion for that
discipline. During Overnight, bars animate from red → green as meshes load.

### 2. Navigation polish

- Fly-to building: verify yellow bbox visible + distance comfortable
- Fly-to storey: tighten to storey bbox, not building envelope
- Fly-to element: inspection distance (diag×2, currently implemented S189z)
- Discipline bar click: fly to discipline centroid (currently no fly)
- Back navigation: clear breadcrumb, zoom out to parent level

### 3. Status bar lifecycle

- During bake: "Baking {building}... {elapsed}s ({n} chunks)"
- During link: "Linking ({i}/{n}): {file} ({MB}MB) — {elapsed}s" (implemented S189z)
- After link: "Linked {n} buildings — {total}s" (keep visible 5s, then clear)
- During overnight: "Loading {building} {disc} — {placed}/{total} ({pct}%)"

### 4. Outliner cleanup

- Verify building → disc hierarchy in all paths (Overnight, LOAD MESH, Preview link)
- Remove empty collections on shred
- Consistent naming: `Loaded_{building}_{disc}` for runtime, `{building}_{disc}` for baked

### 5. Distro integration

- Auto-reconstitute on file open (detect empty meshes + geometry_hash custom prop)
- Status bar: "Reconstituting {building}... {filled}/{total} meshes"
- Save after reconstitute to cache meshes locally

## Source files

- `federation/operator.py` — operators, bake pipeline, navigation
- `federation/bbox_visualization.py` — GPU draw, fly-to, search, progress state
- `federation/ui.py` — N-panel layout, discipline bars, element list
- `scripts/blob_tessellate_worker.py` — bake subprocess
- `scripts/distro_roundtrip_test.py` — lean distro verification

### 6. Community onboarding — install to first view

End-to-end flow for a new user:

1. **Install:** Blender 4.0+ → Bonsai addon → BIM Compiler federation module
2. **Extract:** user drops their IFC file → `extractIFCtoDB_open.py` produces extracted.db
3. **Library:** geometry BLOBs written to local `component_library.db` during extraction
4. **Preview:** open Blender → set DB path → Preview → instant RTree bboxes
5. **Bake:** BACKEND → tessellates from local library DB → baked .blend in viewport
6. **Save:** session.blend (~1MB) links to baked files → instant reopen

### 7. Cloud + local library architecture

Two-tier library:
- **Cloud library** — shared `component_library.db` hosted online (S3/CDN).
  Contains geometry BLOBs for all reference buildings (123K+ meshes).
  Bonsai fetches missing hashes on demand. Read-only.
- **Local library** — user's `~/.bim/component_library.db`.
  Populated during extraction of their own IFC files.
  Merged with cloud hashes for baking. Read-write.

```
User drops IFC file
     ↓
extractIFCtoDB_open.py
     ↓
extracted.db (elements, rtree, transforms)
     +
local component_library.db (new geometry BLOBs)
     ↓
BACKEND bake
     ↓
Fetch missing hashes from cloud library (if any)
     ↓
baked/*.blend → viewport
```

Priority: local library first (zero network dependency). Cloud is optional
acceleration — pre-populated hashes avoid re-tessellation for known buildings.

### 8. Packaging for distribution

- `distro_roundtrip_test.py` proven: fat→lean→reconstitute 7/7 PASS
- Lean bundle: stripped .blend (~7MB) + trimmed library.db (~59MB per building)
- Multi-building: one shared library.db + multiple lean .blend files
- Auto-reconstitute on open: detect empty meshes → tessellate from local/cloud DB
- Manifest: JSON with element counts, hash list, checksums for integrity

## Standing rules

- Bump `_FED_VERSION` on every code change
- §PROOF log lines for all changes
- Test in Blender after each change — visual verification required
- Dynamic disc suffixes from DB (no hardcoded lists)
