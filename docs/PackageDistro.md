# Package & Distribution Spec

## Principle

The DB is the single source of truth. All `.blend` files are derived views —
reproducible from the extracted DB + component_library.db at any time.

| Tier | Optimised for | Mesh source | File size |
|------|---------------|-------------|-----------|
| **Session .blend** (working) | Author, daily use | Linked to `baked/*.blend` | ~1 MB |
| **Baked .blend** (per-building) | BACKEND output | BLOB-tessellated, inline meshes | 17–74 MB |
| **Package** (distro) | Recipient, handover | `session.blend` + `component_library.db` | ~1 MB + 200 MB DB |

## Architecture (S189)

```
component_library.db       ─→  geometry BLOBs      (123K meshes, SQLite indexed)
                                    │
blob_tessellate_worker.py  ─→  baked/*.blend        (per-building, self-contained)
                                    │  (link=True)
                              session.blend          (tiny, references baked files)
                                    │
                              Ctrl+S                 (instant save, links persist)
```

### Working Session (S189 live-link)

BACKEND bakes buildings in background subprocesses (~25s for 126K elements).
When each bake finishes, `_live_link_baked()` links the baked file into the
live session via `libraries.load(link=True)`. Building appears in viewport
with brief pause (~10s). User saves normally — session file stays ~1MB.

### Distribution Package

Two options for shipping to recipients:

**Option A — DB-based (recommended, future):**
Ship `session.blend` (~1 MB) + `component_library.db` (~200 MB).
The DB can be hosted online — recipient's Blender fetches meshes on demand.
First open tessellates from BLOBs. Save caches meshes locally.

**Option B — Self-contained .blend:**
Resolve all links into one file (`link=False`). All meshes inline.
No external dependencies. Larger file (~90 MB for 3 buildings).

## 1. Fat .blend — Working Session

### Producer
`bake_building_blend.py` with `link=False` (append, not link).

### Change from current
One line in `bake_building_blend.py`:

```python
# Current (linked — small file, slow open)
with bpy.data.libraries.load(str(library), link=True) as (src, dst):

# New (appended — self-contained, fast open)
with bpy.data.libraries.load(str(library), link=False) as (src, dst):
```

### Cost at bake time
Negligible. File write grows from ~4 MB to ~20–65 MB (+0.5s). User has
already walked away — bake time is free.

### Benefit at open time
Eliminates the 25–80s library chain resolution. Blender reads one
self-contained file with no external dependencies.

### Proven benchmarks (projected)

| Building | Elements | Unique meshes | Fat size (est) | Open time (est) |
|----------|----------|---------------|----------------|-----------------|
| Duplex | 1,169 | 650 | ~2 MB | <0.5s |
| Terminal | 48,428 | 7,150 | ~20 MB | <1s |
| Hospital | 63,917 | 23,045 | ~65 MB | ~1.5s |
| LTU_AHouse | 125,698 | 51,392 | ~140 MB | ~3s |

## 2. Session Mesh Persistence

### Problem
User loads partial state (one discipline, a few storeys, some colored
elements). On close + reopen, that working set is lost.

### Mechanism
No new infrastructure — the fat .blend already contains the working set.
LOAD MESH creates Blender objects in memory. Saving the .blend saves them.
Reopening restores them. No library resolution needed because meshes are
local (appended, not linked).

### Shred-before-save (optional)
A `save_pre` handler can strip `Baked_*` collections the user has shredded
during the session, keeping the file lean. Elements the user actively loaded
or colored remain.

### DB flag table (supplementary)
For cases where the .blend is not saved but the DB is:

```sql
CREATE TABLE IF NOT EXISTS session_meshes (
    guid TEXT PRIMARY KEY,
    geometry_hash TEXT NOT NULL,
    building TEXT NOT NULL,
    loaded_at TEXT DEFAULT (datetime('now'))
);
-- Populated by LOAD MESH / overnight / bake-link
-- Cleared by SHRED (per element) or full reset
```

On next Preview, query `session_meshes` and restore only those elements.
Cost: sub-second for typical working sets (hundreds of elements).

## 3. Lean .blend — Distribution Package

### Producer
`scripts/distro_package.py` — offline script, not part of the user session.

### Process
1. Read fat .blend
2. Strip mesh data — replace appended meshes with library links
3. Produce trimmed per-building library containing only referenced hashes
4. Validate: element count matches DB, no orphan refs, no missing hashes
5. Generate manifest (hash list, element counts, discipline breakdown)
6. Sign package (hash manifest, GPG or equivalent)
7. Write output bundle

### Output structure
```
distro/
  {Building}_distro.blend          # lean, linked to library below
  {Building}_library.blend         # trimmed, only this building's meshes
  {Building}_manifest.json         # element counts, hash list, checksums
  {Building}_manifest.json.sig     # signature
```

### Recipient workflow
1. Receive package
2. Open `{Building}_distro.blend` — first open resolves library (~25–80s)
3. Blender resolves links from co-located `{Building}_library.blend`
4. Recipient saves → becomes fat .blend (self-contained, fast reopen)
5. Trimmed library can be deleted after first save

### Validation on receipt
Recipient can verify integrity before opening:
- Check signature against manifest
- Compare manifest hash list against their own DB (if they have one)
- Confirm element counts match expected

## 4. Color Studio Integration

Color changes made via Color Studio write to the DB:

```sql
UPDATE elements_meta SET material_rgba = ? WHERE guid = ?
```

This means:
- Fat .blend reflects colors via `obj.color` (immediate, in-session)
- DB holds the authoritative color (survives shred/reopen/re-bake)
- Next bake picks up updated `material_rgba` automatically
- Distro package carries colors in the DB, not the .blend

## 5. Design Constraints

- **DB is the model** — `.blend` files are derived, disposable, reproducible
- **Bake time is free** — BACKEND runs in background; user keeps working
- **Link, don't copy** — session references baked files, never duplicates meshes
- **Save is instant** — ~1 MB session file with link references
- **component_library.db is the mesh source** — not library.blend (S189)
- **Online DB future** — component_library.db can be hosted; `.blend` fetches on demand

## Source Files

- `scripts/blob_tessellate_worker.py` — BLOB bake worker (per-chunk subprocess, S189)
- `scripts/bake_building_blend.py` — legacy fat .blend producer (fallback)
- `federation/operator.py` — `_live_link_baked()` links baked .blend into live session
- `federation/bbox_visualization.py` — `_library_db_cache` resolves component_library.db
- `library/component_library.db` — 123,573 tessellated meshes (geometry_hash indexed)
