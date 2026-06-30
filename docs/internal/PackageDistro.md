# Package & Distribution Spec

## Principle

The DB is the single source of truth. No .blend files in the pipeline.
Direct Stream tessellates geometry from SQLite BLOBs into Blender's viewport
in real time. Distribution is shipping two .db files.

## Architecture (S197 — Direct Stream)

```
IFC files → extractIFCtoDB.py → _extracted.db + component_library.db
                                        ↓
                              Ctrl+Shift+A → Direct Stream
                                        ↓
                              SQL → BLOB unpack → from_pydata() → viewport
```

**Two files = complete viewer input:**
- `_extracted.db` (50-835MB) — elements, transforms, rtree, surface_styles
- `component_library.db` (305MB) — 50K geometry BLOBs (vertices + faces)

## Distribution

Ship the two .db files. Recipient installs the Bonsai addon, drops the DBs,
presses Ctrl+Shift+A. Buildings stream into viewport.

**Package structure:**
```
distro/
  {Building}_extracted.db       # elements, transforms, rtree
  component_library.db          # trimmed to this building's hashes only
  manifest.json                 # element counts, hash list, checksums
```

**Trimming:** `distro_package.py` creates a subset of component_library.db
containing only the geometry hashes referenced by the building's
element_instances table. A 60K-element hospital needs ~3K unique hashes
(~50MB trimmed library vs 305MB full).

## Color Studio Integration

Color changes write to the DB:

```sql
UPDATE elements_meta SET material_rgba = ? WHERE guid = ?
```

- Direct Stream picks up updated `material_rgba` on next tick
- DB holds the authoritative color (survives shred/re-stream)
- Distro package carries colors in the DB

## Design Constraints

- **DB is the model** — no .blend files in the pipeline
- **Streaming is instant** — no bake step, no waiting
- **Two files only** — _extracted.db + component_library.db
- **component_library.db is the mesh source** — geometry BLOBs, SQLite indexed
- **Online DB future** — host on OCI, fetch once, stream forever

## Historical Note

S189-S193 used a .blend-based pipeline: bake subprocess → .blend file →
link into Blender session. This required library.blend (300MB), per-building
baked .blend files (1.8GB total), save-post handlers, and DLOD auto-linker.
S195-S197 replaced it entirely with Direct DB Streaming. The baked/ directory
and library.blend have been deleted. See `prompts/S193_dlod_auto_linker.md`
§Historical Note for details.

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
