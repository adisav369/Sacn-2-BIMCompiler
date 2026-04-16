# Package & Distribution Spec

## Principle

The DB is the single source of truth. All `.blend` files are derived views —
reproducible from the extracted DB + library at any time. Two tiers optimise
for different consumers:

| Tier | Optimised for | Mesh storage | Open time | File size |
|------|---------------|--------------|-----------|-----------|
| **Fat .blend** (working) | Author, daily use | Appended (self-contained) | <1s | 20–65 MB |
| **Lean .blend** (distro) | Recipient, handover | Linked to trimmed library | 25–80s first open | 2–4 MB + library |

## Artifact Pipeline

```
extractIFCtoDB.py          ─→  extracted DB        (source of truth)
                                    │
bake_building_blend.py     ─→  fat .blend          (link=False, daily work)
                                    │
distro_package.py          ─→  lean .blend          (offline, fire-and-forget)
                                + trimmed library
                                + manifest
                                + signature
```

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

- **DB is the model** — .blend files are derived, disposable, reproducible
- **Bake time is free** — user walks away; front-load work into save
- **Open time is blocking** — user is waiting; minimise at all costs
- **Distro is offline** — no user session; can take any time needed
- **No new dependencies** — uses existing Blender `link=True/False` mechanics
- **Library.blend untouched** — full 299 MB library remains the extraction
  output; split/trimmed libraries are derived for distro only

## Source Files

- `scripts/bake_building_blend.py` — fat .blend producer (change `link=True` → `link=False`)
- `scripts/distro_package.py` — lean .blend producer (to be created)
- `federation/operator.py` — `_poll_bake_subprocess()` links baked .blend into session
- `federation/color_palette.py` — Color Studio operators (add DB write path)
- `federation/bbox_visualization.py` — `session_meshes` restore on Preview
