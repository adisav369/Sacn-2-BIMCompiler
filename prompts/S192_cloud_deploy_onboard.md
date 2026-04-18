# ⚠ DO NOT REMOVE
# Scope: S192 — Cloud deployment + community onboarding pipeline
# Read the log after every run. No claims without §PROOF log lines.
# STATUS: SPEC READY

## Context

S191 delivered the Progress HUD, save-to-link pipeline, and baked/{project}/ file
management. The RTree viewer is proven at 1M elements with BLOB tessellation,
discipline-based baking, and GPU progress overlay.

ACTION_ROADMAP Phase 5-6 targets: Blender addon packaging, zero-config onboarding,
cloud deployment on OCI by July 2026. This session bridges the gap from developer
tool to installable product.

## Vision — Gallery-first Onboarding

A new user installs the addon and immediately explores pre-compiled reference
buildings via Direct Stream — Hospital, Terminal, Clinic, residential houses.
No IFC file needed. No extraction. No command-line. Buildings stream from
local City/ cache or OCI on demand.

The reference gallery is the training ramp: users learn search, drill-down,
discipline filtering, and nD queries on real data. When ready, they DROP their
own IFC. Eventually, they design from scratch via BIM Designer.

```
Onboarding ladder:
  1. Install addon                    → 30s
  2. Direct Stream reference gallery  → instant (City/ or OCI fetch)
  3. Explore: search, filter, nD      → learn the tool on real buildings
  4. DROP own IFC → extract → stream  → when ready
  5. BIM Designer → create from zero  → eventual
```

## Architecture (S197 — Direct Stream + OCI)

```
USER'S MACHINE                             OCI (Oracle Cloud Free Tier)
─────────────                              ─────────────────────────────
Blender 4.0+ with Bonsai                   Object Storage bucket: bomtree-library
  └─ Federation addon (ZIP install)          ├─ component_library.db  (305MB, shared BLOBs)
       │                                     ├─ Hospital_extracted.db (pre-extracted)
       │  GALLERY PATH (no IFC needed)       ├─ Terminal_extracted.db
       │  ─────────────────────────          ├─ Clinic_extracted.db
       ├─ Direct Stream (Ctrl+Shift+A)       ├─ SampleHouse_extracted.db
       │    ├─ sandbox.db = merged City/ DBs └─ ... (35 reference buildings)
       │    ├─ component_library.db from City/
       │    ├─ tessellate per-tick from BLOBs
       │    ├─ largest elements first (shell in seconds)
       │    └─ camera-aware: stream near, shred far
       │
       │  OWN-IFC PATH (user's files)
       │  ───────────────────────────
       ├─ DROP IFC → extractIFCtoDB_open.py
       │    ├─ {building}_extracted.db   (project/)
       │    └─ component_library.db      (BLOBs, --library-db)
       ├─ Direct Stream (same engine, user's DB)
       │
       │  DESIGN PATH (future)
       │  ─────────────────────
       └─ BIM Designer → compile → stream

DB RESOLUTION (three-tier, highest priority first)
  1. project/{user_building}_extracted.db     ← user's own compilations
  2. City/{reference}_extracted.db            ← local cache of OCI gallery
  3. OCI bucket fetch → cache to City/        ← on-demand, one-time download

DAGCompiler/baked/
  ├─ City/                           # Shared reference cache (fetched from OCI)
  │   ├─ sandbox.db                  # Merged rtree index for all cached buildings
  │   ├─ Hospital_extracted.db       # Downloaded once, never re-extracted
  │   ├─ Terminal_extracted.db
  │   └─ component_library.db        # Shared geometry BLOBs
  │
  └─ {project}/                      # User's own compilations
      ├─ MyBuilding_extracted.db     # From DROP IFC
      └─ component_library.db        # User's geometry (merged with City/)

OCI deployment (minimal — no compute, no server, no auth):
  Object Storage bucket (public read, HTTPS)
    ├─ Pre-extracted DBs: ~50-200MB each
    ├─ component_library.db: 305MB (123K+ meshes)
    └─ manifest.json: building list, sizes, checksums
  DNS: bomtree.io → Object Storage HTTPS endpoint
  Total: < 2GB static files. No ARM instance needed for gallery path.
```

## Analysis — Why Direct Stream Changes Everything

### What the bake/link pipeline was (S189-S191)

Extract → Bake subprocess (10-60s) → .blend file → Save → Link → view.
User waits for full bake before seeing any geometry. Output is a .blend
(100-300MB) — heavy to host, heavy to download, heavy to store.

### What Direct Stream is (S195-S197)

Extract → press Ctrl+Shift+A → geometry appears while you watch.
Largest elements first (ORDER BY bbox volume DESC) — building shell in seconds.
Camera-aware: streams what's near, shreds what's behind. Budget-capped.
Cinematic camera auto-pans. In-sight scoring prefers facing direction.

| Aspect | Bake/Link | Direct Stream |
|--------|-----------|---------------|
| Time to first pixel | 10-60s (full bake) | 2-3s (shell walls) |
| File format | .blend (100-300MB) | .db (50-200MB) |
| Cloud hosting | Impractical (huge .blend) | Simple (static .db) |
| Network dependency | Download full .blend | Fetch .db once, cache |
| Memory ceiling | Full scene in RAM | Budget-capped, auto-shred |
| Persistence | Reopen .blend = instant | Re-stream each session |
| Distro bundle | .blend + library.blend | .db + component_library.db |

### The consolidation opportunity

Direct Stream currently resolves `_library_db_cache` by walking up from
the sandbox DB to find `library/component_library.db`. This is a dev-machine
assumption. For deployed onboarding:

1. **City/ becomes the resolver** — Direct Stream should look in City/ for
   both `{building}_extracted.db` and `component_library.db` before walking
   up to the repo root.

2. **sandbox.db becomes a City/ artifact** — the merged rtree index of all
   cached reference buildings. Pre-built on OCI, downloaded once.

3. **OCI fetch is lazy** — first stream of Hospital checks City/ first.
   Missing? Download `Hospital_extracted.db` from OCI → cache to City/.
   `component_library.db` downloaded once (305MB), shared by all buildings.

4. **User's own files go to project/** — DROP IFC writes to project/.
   Direct Stream checks project/ before City/ (three-tier resolution).

### What needs to change in Direct Stream

Current bootstrap (operator.py `FedRTreeDirectStream.execute`, lines 668-676):
```python
# Walks up from DB path to find library/component_library.db
for _anc in _P(_db).resolve().parents:
    _ldb = _anc / "library" / "component_library.db"
    if _ldb.exists():
        bv._library_db_cache = str(_ldb)
        break
```

Needed: three-tier resolution:
```python
# 1. Sibling of the DB (project-local library)
# 2. City/component_library.db (cached reference library)
# 3. Walk up to repo library/ (dev fallback)
```

## Implementation — Triaged Sessions

### S192a DONE: baked/ → DAGCompiler/baked/

Compile outputs grouped under DAGCompiler. All path references updated:
`operator.py`, `__init__.py`, `bake_building_blend.py`, `assemble_city_blend.py`,
`distro_package.py`, `distro_roundtrip_test.py`.

---

### S192b: Three-tier DB resolution — P1

**What:** Direct Stream bootstrap resolves `_library_db_cache` and `_db_path_cache`
from a fixed dev-machine layout. Change to three-tier: project/ → City/ → walk-up.

**Why:** Without this, gallery path won't work (no `library/` on user machine).
This is the gate for everything else.

**Files:**
- `federation/direct_stream.py` `FedRTreeDirectStream.execute()` lines 668-676
- `federation/operator.py` `_tessellate_from_blobs()` (if it resolves library)
- `federation/bbox_visualization.py` (if library cache vars need defaults)

**Spec:**
```python
def _resolve_library_db(db_path):
    """Three-tier component_library.db resolution."""
    db_dir = Path(db_path).parent
    # 1. Sibling (project-local or City-local)
    sibling = db_dir / "component_library.db"
    if sibling.exists():
        return str(sibling)
    # 2. City/ (shared reference cache)
    for anc in Path(db_path).resolve().parents:
        city = anc / "DAGCompiler" / "baked" / "City" / "component_library.db"
        if city.exists():
            return str(city)
    # 3. Walk-up to repo library/ (dev fallback)
    for anc in Path(db_path).resolve().parents:
        repo_lib = anc / "library" / "component_library.db"
        if repo_lib.exists():
            return str(repo_lib)
    return None
```

**Log lines:**
```
[S192] §RESOLVE_LIB tier=sibling path={path}
[S192] §RESOLVE_LIB tier=city path={path}
[S192] §RESOLVE_LIB tier=repo path={path}
[S192] §RESOLVE_LIB FAIL — no component_library.db found
```

**Exit criterion:** Point `federation_database_path` at a DB in `City/`.
Direct Stream resolves `component_library.db` from `City/` (tier 2).
Log shows `§RESOLVE_LIB tier=city`. Streaming works.

---

### S192c: BLOB gap bridge — P1

**What:** `extractIFCtoDB_open.py` gains `--library-db` flag. Writes
`component_geometries` rows alongside `base_geometries` in one pass.

**Why:** User's own IFC path (DROP IFC) needs this. Without it,
Direct Stream finds zero BLOBs for new buildings (`blob_miss=ALL`).
Not needed for gallery path (reference buildings pre-compiled).

**Files:**
- `scripts/extractIFCtoDB_open.py` — add `--library-db` arg, dual-write

**Spec:**
```python
# After line 868 (base_geometries INSERT):
if lib_conn and ghash not in lib_existing_hashes:
    lib_conn.execute(
        "INSERT OR IGNORE INTO component_geometries "
        "(geometry_hash, vertices, faces, vertex_count, face_count) "
        "VALUES (?,?,?,?,?)",
        (ghash, vblob, fblob, len(v_centered), len(faces)))
    lib_existing_hashes.add(ghash)
```

**Log lines:**
```
[S192] §LIB_WRITE library_db={path} hashes_written={n} hashes_skipped={n}
[S192] §LIB_SKIP --library-db not set, skipping component_geometries write
```

**Exit criterion:**
```bash
python3 extractIFCtoDB_open.py --ifc test.ifc -o test_ext.db --library-db test_lib.db
# Log: §LIB_WRITE library_db=test_lib.db hashes_written=N
sqlite3 test_lib.db "SELECT COUNT(*) FROM component_geometries"  # > 0
```

---

### S192d: Auto-cache .blend after stream — P1

**What:** After `§DS_DETAIL_DONE` for a building, auto-save the streamed
collections to `project/{building}_cache.blend` via `bpy.data.libraries.write()`.
On reopen, check cache freshness (DB mtime vs cache mtime). Fresh → link.
Stale → re-stream and rebuild cache.

**Why:** Gallery explorers don't need this (Scenario A). But Scenario B
(architect reviewing own IFC daily) needs instant reopen without re-streaming.

**Files:**
- `federation/direct_stream.py` — after `§DS_DETAIL_DONE` (line 473), trigger save
- `federation/__init__.py` — `load_post` handler checks for cache, links if fresh

**Log lines:**
```
[S192] §CACHE_SAVE bld={bld} objects={n} path={cache_blend}
[S192] §CACHE_HIT bld={bld} cache_mtime > db_mtime — linking
[S192] §CACHE_STALE bld={bld} db_mtime={t1} cache_mtime={t2} — re-stream
[S192] §CACHE_MISS bld={bld} no cache file — streaming
```

**Exit criterion:** Stream Hospital. Log shows `§CACHE_SAVE`. Close Blender.
Reopen. Log shows `§CACHE_HIT`. Hospital visible instantly (no re-stream).
Touch the extracted DB. Reopen. Log shows `§CACHE_STALE`. Re-streams.

---

### S192e: City/ gallery + OCI fetch — P2

**What:** Pre-extracted reference DBs on OCI Object Storage. Addon fetches
to `City/` on demand. `manifest.json` lists available buildings.

**Why:** The "delight" factor — newbie installs addon and has real buildings
to explore immediately. No IFC file needed.

**Files:**
- `federation/operator.py` — `FedRTreeFetchGallery` operator (urllib download)
- `federation/ui.py` — Gallery panel with building list + FETCH buttons
- `federation/progress_hud.py` — FETCHING / CACHED status
- OCI: upload pre-extracted DBs + manifest.json to Object Storage bucket

**Log lines:**
```
[S192] §FETCH_START bld={bld} url={url} size_mb={size}
[S192] §FETCH_DONE bld={bld} cached_to={city_path} elapsed={t}s
[S192] §FETCH_SKIP bld={bld} already cached at {city_path}
[S192] §FETCH_ERROR bld={bld} {error}
[S192] §MANIFEST_LOAD buildings={n} total_size_mb={total}
```

**Exit criterion:** Fresh machine, empty `City/`. Open addon. Gallery shows
building list from manifest. Click Hospital → `§FETCH_START` → downloads →
`§FETCH_DONE` → auto-Direct Stream. Second launch → `§FETCH_SKIP`.

---

### S192f: DROP IFC operator — P2

**What:** N-panel button → file browser → extraction subprocess → auto-stream.
Output to `project/`. Uses `--library-db` from S192c.

**Why:** After exploring gallery, user's next step is "try my own building."
Follows proven subprocess pattern from `ExtractSampleDatabase` (operator.py:4628).

**Files:**
- `federation/operator.py` — `FedDropIFC` class (ImportHelper + subprocess)
- `federation/ui.py` — DROP IFC button in N-panel (shown when streaming)
- `federation/__init__.py` — register in classes tuple
- `federation/progress_hud.py` — EXTRACTING / EXTRACTED states

**Log lines:**
```
[S192] §DROP_IFC ifc={path} output_dir=project/{name}
[S192] §EXTRACT_START pid={pid} ifc={path}
[S192] §EXTRACT_DONE pid={pid} elapsed={t}s elements={n} hashes={n}
[S192] §EXTRACT_ERROR pid={pid} rc={rc} stderr={msg}
[S192] §EXTRACT_AUTO_STREAM db={extracted_db}
```

**Exit criterion:** Click DROP IFC → select .ifc → HUD: EXTRACTING... →
log: `§EXTRACT_DONE` → auto-sets DB path → Direct Stream starts →
building appears. Log shows full chain from `§DROP_IFC` to `§DS_START`.

---

### S192g: Addon ZIP packaging — P2

**What:** Build script producing `federation.zip` for Blender Preferences install.

**Why:** Distribution mechanism. Depends on S192b-f being done (ZIP should
contain the complete onboarding path).

**Files:**
- `scripts/build_addon_zip.py` — new packaging script
- `federation/ui.py`, `federation/ui_federation_project.py` — guard `bonsai.tool`

**Exit criterion:** `python3 build_addon_zip.py` → `federation_addon.zip`.
Install in fresh Blender + Bonsai. N-panel appears. Gallery + DROP IFC work.

---

### S192h: DB-based distro packaging — P3

**What:** EXPORT DISTRO ships `_extracted.db` + trimmed `component_library.db`.
Recipient drops into City/ → Direct Stream.

**Files:**
- `scripts/distro_package.py` — adapt strip/fatten for DB-only bundles
- `federation/operator.py` — `FedExportDistro` operator
- `federation/ui.py` — EXPORT DISTRO button

**Log lines:**
```
[S192] §DISTRO_STRIP bld={bld} extracted_db={size}MB library_db={size}MB
[S192] §DISTRO_BUNDLE zip={path} total_size={size}MB
```

**Exit criterion:** Stream Hospital → EXPORT DISTRO → zip created.
Recipient: unzip to City/ → Direct Stream → building appears, counts match.

---

### S192-future: Multi-DB federation, BOM-to-stream — P3

Not in scope for S192. Spec'd in Scenarios C and E above.
BOM-to-stream needs BIM Designer. Multi-DB needs sandbox merger.

---

## Priority Matrix

| Session | Priority | Depends on | Effort | Gate for |
|---------|----------|------------|--------|----------|
| S192a ✓ | P0 | — | Small | All |
| S192b | P1 | S192a | Small | Gallery, own-IFC |
| S192c | P1 | — | Small | Own-IFC path |
| S192d | P1 | S192b | Medium | Daily-use architects |
| S192e | P2 | S192b | Medium | Onboarding wow |
| S192f | P2 | S192b,c | Medium | Own-IFC onboarding |
| S192g | P2 | S192b-f | Medium | Distribution |
| S192h | P3 | S192b,g | Small | Sharing |

**Critical path:** S192a → S192b → S192d (gallery streams, cache works)
**Own-IFC path:** S192c → S192f (extract + stream own files)
**Distribution:** S192g → S192h (package + share)

## User Scenarios — Flow Analysis

### The persistence question

Direct Stream re-tessellates every session. For reference gallery browsing
this is fine — stream, explore, close. But for a user's **own project** they
work on daily, re-streaming 60K elements every open is friction.

**Recommendation: DB is design, .blend is cache.**

```
                    ┌─────────────────────────┐
                    │   component_library.db   │  shared geometry (BLOBs)
                    └──────────┬──────────────┘
                               │ geometry_hash
     ┌─────────────────────────┼─────────────────────────┐
     │                         │                         │
  City/sandbox.db        project/MyHotel.db        project/MyHotel_BOM.db
  (reference, read-only) (extracted, user's IFC)   (user's design, BOM tree)
     │                         │                         │
     │                         │                         │
  Direct Stream            Direct Stream            BIM Designer
  (explore, learn)         (review, QA)             (compose, compile)
     │                         │                         │
     └── no save needed ──┐    │                         │
                          │    ▼                         │
                          │  project/MyHotel_cache.blend │
                          │  (auto-saved after §DS_      │
                          │   DETAIL_DONE, derived only) │
                          │    │                         │
                          │    ▼                         │
                          │  Reopen: §CACHE_HIT → link   │
                          │  = instant viewport          │
                          │  DB changed? §CACHE_STALE    │
                          │  → re-stream delta           │
                          │                              │
                          └──────────────────────────────┘
```

### Scenario A — Gallery explorer (no saves)
```
Install addon → Direct Stream sandbox.db → Hospital, Terminal appear
Explore: search, filter discs, nD queries → learn the tool
Close Blender → nothing saved. Next session: re-stream (acceptable).
```
No friction. Streaming speed is the only UX metric.

**Debug trail:** `§RESOLVE_LIB tier=city` → `§DS_ON` → `§DS_START` →
`§DS_SHELL_DONE` → `§DS_DETAIL_DONE`. No `§CACHE_SAVE` (no project/).

### Scenario B — Architect reviewing own IFC (daily use)
```
DROP own IFC → extract to project/MyHotel_extracted.db
Direct Stream → 60K elements stream in ~90s (largest first)
First session: auto-save mesh cache → project/MyHotel_cache.blend
Next reopen: link cache.blend = instant viewport (like old bake/link)
Edit IFC and re-extract? DB timestamp > cache timestamp → re-stream delta
```
**Key insight:** the .blend cache is derived, not authored. It can be
regenerated from the DB at any time. Delete it = re-stream. The DB is
the source of truth. The .blend is just a viewport accelerator.

**Debug trail:** `§DROP_IFC` → `§EXTRACT_DONE` → `§RESOLVE_LIB tier=sibling`
→ `§DS_START` → `§DS_DETAIL_DONE` → `§CACHE_SAVE`. Reopen: `§CACHE_HIT`.
After re-extract: `§CACHE_STALE` → `§DS_START` (delta re-stream).

### Scenario C — Designer composing from library (BOM path, future)
```
Exploring Hospital → sees IfcDoor assembly (geometry_hash=abc123)
Opens BIM Designer → creates own BOM referencing abc123
Compile BOM → project/MyHotel_output.db
Direct Stream output.db → door geometry resolves from shared library
```
The library bridges reference buildings and user designs. The user never
touches .blend files for design — BOM tree IS the design. geometry_hash
links their BOM to tessellated meshes in the library.

### Scenario D — Contractor receiving distro
```
Receives: MyHotel_extracted.db + component_library.db (trimmed)
Drops both into City/ → Direct Stream → full building in seconds
Runs nD: 4D schedule, 5D cost, 6D carbon queries from same DB
Exports BOQ report directly. No Blender expertise needed.
```

**Debug trail:** `§RESOLVE_LIB tier=sibling` (library next to extracted.db
in City/) → `§DS_START` → streaming.

### Scenario E — Multi-user federation (future)
```
Architect: project/MyHotel.db, Engineer: project/MyHotel_MEP.db
Both federate into one sandbox for site coordination.
Each user's edits stay in project/. Shared reference in City/.
```

## Debug Log Guidance

### Log line format

All S192 log lines use `[S192] §TAG key=value key=value` format.
Existing S195 lines (`§DS_START`, `§DS_SHELL_DONE`, etc.) remain unchanged.

### Key diagnostic chains

**Gallery onboarding (happy path):**
```
[S192] §MANIFEST_LOAD buildings=35 total_size_mb=1847
[S192] §FETCH_SKIP bld=Hospital already cached at City/Hospital_extracted.db
[S192] §RESOLVE_LIB tier=city path=City/component_library.db
[S195] §BOOTSTRAP db_path=City/sandbox.db
[S195] §BOOTSTRAP centres=35 total_elements=1,061,736
[S195] §DS_ON radius=100m buildings=35
[S195] §DS_START Hospital elements=49,002
[S195] §DS_SHELL_DONE Hospital arc_str=31,500
[S195] §DS_DETAIL_DONE Hospital elements=49,002
```

**Own-IFC extraction (happy path):**
```
[S192] §DROP_IFC ifc=/home/user/MyHotel.ifc output_dir=project/MyHotel
[S192] §EXTRACT_START pid=12345 ifc=MyHotel.ifc
[S192] §LIB_WRITE library_db=project/MyHotel/component_library.db hashes_written=3421
[S192] §EXTRACT_DONE pid=12345 elapsed=47s elements=58201 hashes=3421
[S192] §RESOLVE_LIB tier=sibling path=project/MyHotel/component_library.db
[S192] §EXTRACT_AUTO_STREAM db=project/MyHotel/MyHotel_extracted.db
[S195] §DS_START MyHotel elements=58,201
```

**Cache hit on reopen:**
```
[S192] §CACHE_HIT bld=MyHotel cache_mtime=1713400000 > db_mtime=1713399000 — linking
```

**Cache stale after re-extract:**
```
[S192] §CACHE_STALE bld=MyHotel db_mtime=1713410000 cache_mtime=1713400000 — re-stream
[S195] §DS_START MyHotel elements=58,201
```

**Failure: library not found:**
```
[S192] §RESOLVE_LIB FAIL — no component_library.db found
```
**Fix:** check that `component_library.db` exists in City/ or as sibling of DB.

**Failure: BLOB miss during stream:**
```
[S195] §DS_TICK Hospital phase=shell batch=500 placed=0 new_meshes=0 miss=500
```
**Fix:** extraction didn't write BLOBs. Re-run with `--library-db`. Check `§LIB_WRITE`.

**Failure: OCI fetch error:**
```
[S192] §FETCH_ERROR bld=Hospital ConnectionError: Network unreachable
```
**Fix:** check network. Addon works offline with existing City/ cache.

### What to check after every run

1. **§RESOLVE_LIB** — which tier? If `FAIL`, nothing will stream.
2. **§DS_START** — did streaming begin? If missing, bootstrap failed.
3. **§DS_DETAIL_DONE** — did it finish? If stuck at shell, check library DB.
4. **§CACHE_SAVE** — did auto-cache fire? Only for project/ buildings.
5. **§LIB_WRITE** — after extraction, did BLOBs land? If 0, check schema.

## Source files

| File | Role |
|------|------|
| `federation/direct_stream.py` | Direct DB Streaming engine (S195-S197) |
| `federation/mesh_utils.py` | Shared tessellation (ensure_meshes, apply_material) |
| `federation/operator.py` | _tessellate_from_blobs, bake pipeline, Preview |
| `federation/__init__.py` | Addon registration, save_post, load_post handlers |
| `federation/progress_hud.py` | GPU overlay: discipline bars, status text |
| `federation/bbox_visualization.py` | RTree GPU rendering, discipline colors |
| `scripts/extractIFCtoDB_open.py` | IFC → extracted.db + library BLOBs |
| `scripts/blob_tessellate_worker.py` | Bake subprocess (optional export path) |
| `scripts/distro_package.py` | DB-based distro packaging |
| `docs/RTree.md` | Viewer architecture spec |
| `docs/ACTION_ROADMAP.md` | Phase 5-6 targets |

## Standing rules

- Bump `_FED_VERSION` on every code change
- §PROOF log lines for all changes
- Test in Blender after each change
- Local-first: zero network dependency for core workflow
- Cloud is optional acceleration, never required
- No Java dependency in the viewer/addon path
