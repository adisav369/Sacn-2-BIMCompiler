# S60 UI Alignment Spec — Web UI + Bonsai 10-Panel Stack

> **Foundation:** [BIM_Designer_SRS](../BIM_Designer_SRS.md) §30.3 · [S60_ERP_ALIGNMENT](S60_ERP_ALIGNMENT.md)
> **Source of truth:** `/home/red1/IfcOpenShell/src/bonsai/bonsai/bim/module/federation/`

## Purpose

Align the Web UI (webui/) with:
1. The §30.3 10-panel spec mapping
2. Features already implemented in the IfcOpenShell federation module
3. The Bonsai client.py 10-item operation flow

---

## 1. Tab Alignment (Done — S60 this session)

| Tab | Old Name | New Name (§30.3) | Status |
|-----|----------|-------------------|--------|
| 1 | 1D Order | 1D Order | OK |
| 2 | 2D Import | **2D Spatial** | RENAMED — storey browser + containment tree + import/export |
| 3 | 3D Federation | **3D Geometry** | RENAMED — federation is a sub-function of geometry |
| 4 | 4D Schedule | 4D Schedule | OK |
| 5 | 5D Cost | 5D Cost | OK |
| 6 | 6D Sustain | 6D Sustain | OK |
| 7 | 7D Facility | 7D Facility | OK |
| 8 | 8 Reports | **8 Validate** | RENAMED — advisories + compliance + reports merged |
| 9 | 9 Query | **9 BOM** | RENAMED — BOM outliner primary, NLP query + product search secondary |
| 10 | 10 Color | **10 Colour** | Spelling fix |

## 2. HTML Fixes (Done — S60 this session)

- **BOM tree container** — `#bomTree` and `#bomCount` added to 1D tab (was missing, renderBomTree() failed silently)
- **Split pane layout** — 1D tab: BOM tree (left) + ASI panel (right) + Order Lines below
- **Orphan ASI fragment** — removed duplicate ASI card-header that was outside any parent card
- **Global search** — header bar search routes to NLP query or product search
- **Stub tab placeholders** — 4D/5D/6D/7D show descriptive placeholders with DAO source info
- **BOM outliner sync** — Tab 9 mirrors BOM tree from Tab 1 on building select
- **Validation tab** — Tab 8 shows advisory summary cards + advisory table + portfolio/BSC
- **Footer** — version updated to v1.0-S60, compliance summary added

---

## 3. Federation Module Gap Analysis

The IfcOpenShell federation module at `/home/red1/IfcOpenShell/src/bonsai/bonsai/bim/module/federation/`
implements **70+ operators** across 186 Python files. Below are features that the Web UI should expose
but does not yet call through to the backend.

### 3.1 Already Wired (Web UI → Backend)

| Feature | Web UI Tab | Backend API | Client.py Method | Status |
|---------|-----------|-------------|------------------|--------|
| Building scan | 3D | `scanLibrary` | `list_buildings()` | LIVE |
| Compile | 1D/3D | `compile` | `compile()` | LIVE |
| BOM Drop | 1D | `bomDrop` | `bom_drop()` | LIVE |
| Order Lines | 1D | `listOrderLines` | `list_order_lines()` | LIVE |
| ASI read/write | 1D | `readASI`/`updateASI` | (direct send) | LIVE |
| Approve | 1D | `approve` | `approve()` | LIVE |
| Save/Recall | 1D | `save`/`recall` | `save()`/`recall()` | LIVE |
| Promote | 1D | `promote` | `promote()` | LIVE |
| NLP Query | 9 | `executeNlpQuery` | (direct send) | LIVE |
| Product Search | 9 | `browseItems` | `browse_items()` | LIVE |
| Color Scheme | 10 | `applyScheme` | (HTTP POST) | LIVE |
| SSE Sync | 10 | `/events` SSE | `poll_commands()` | LIVE |
| Bonsai Preview | 3D | `applyScheme:loadOutput` | `poll_commands()` | LIVE |

### 3.2 In Federation Module, NOT yet in Web UI (Recommendations)

#### HIGH PRIORITY — Already implemented in federation, need Web UI surface

| Feature | Federation Operator | Suggested Tab | Backend API needed | Notes |
|---------|-------------------|---------------|-------------------|-------|
| **Clash detection** | `BIM_OT_clash_by_discipline` (28 ops) | 8 Validate | `clashByDiscipline` | Show clash groups + resolution suggestions in advisory table |
| **4D schedule generation** | `BIM_OT_generate_construction_schedule` | 4D | `generateSchedule` | Already has export to XML/Excel. Wire loadSchedule → real data |
| **4D animation** | `BIM_OT_animate_4d_construction` | 4D | `animate4D` | Bonsai-only (viewport animation) |
| **5D BOQ generation** | `BIM_OT_export_comprehensive_boq` | 5D | `generateBoq` | Already exports BOQ. Wire loadCost → real data |
| **BCF export** | `BIM_OT_export_bcf` | 8 Validate | `exportBcf` | BCF 2.1 clash report with snapshots |
| **Schedule export** | `BIM_OT_export_mpp_schedule` | 4D | `exportSchedule` | MPP XML + Excel |
| **Structural rebar** | `BIM_OT_generate_rebar_structural` | 5D | `generateRebar` | Concrete grades, exposure class |
| **Asset import** | `BIM_OT_import_assets_from_federation` | 6D/7D | `importAssets` | Digital twin asset list |

#### MEDIUM PRIORITY — Useful but not critical path

| Feature | Federation Operator | Tab | Notes |
|---------|-------------------|-----|-------|
| **Visualization mode** | BBox/Semantic/Materials toggle | 3D | Bonsai viewport only, Web UI could show mode selector |
| **Discipline legend** | 2D discipline overlay | 10 | Already have discipline buttons; legend is Bonsai-side |
| **Asset condition viz** | `BIM_OT_visualize_assets_by_condition` | 7D | Excellent/Good/Fair/Poor/Failed status |
| **PM schedule** | `BIM_OT_generate_pm_schedule` | 7D | Maintenance work orders |
| **IoT sensor dashboard** | Tandem IoT operators | 7D | Live sensor monitoring |
| **Color scheme save/load** | `BIM_OT_save_color_scheme` | 10 | Persist named schemes |

#### LOW PRIORITY — Specialized features

| Feature | Federation Module | Notes |
|---------|-----------------|-------|
| River equipment monitoring | `/river/` — 16 equipment types, GPS | Domain-specific, not general BIM |
| Carbon credits | Biochar pipeline | Domain-specific |
| PDF terrain extraction | `/pdf2blend/` | Bonsai-only workflow |
| Resolution suggestions | Clash resolution AI | Bonsai-only (3D interaction) |

---

## 4. Bonsai 10-Item Client Flow Verification

The 10 core operations from `client.py` and their backend wiring:

| # | Operation | Client Method | Action String | Web UI Calls | Status |
|---|-----------|--------------|---------------|-------------|--------|
| 1 | **Create New** | `create_new()` | `createNew` | Not in HTML (Bonsai panel A.3 only) | OK — Bonsai-primary |
| 2 | **Snap/Validate** | `snap()` | `snap` | Not in HTML (Bonsai design mode only) | OK — Bonsai-primary |
| 3 | **Save** | `save()` | `save` | `BIM.save()` | WIRED |
| 4 | **List Order Lines** | `list_order_lines()` | `listOrderLines` | `BIM.loadOrderLines()` | WIRED |
| 5 | **BOM Tree** | `get_bom_tree()` | `getBomTree` | `BIM.loadBomTree()` | WIRED |
| 6 | **BOM Drop** | `bom_drop()` | `bomDrop` | `BIM.bomDrop()` | WIRED |
| 7 | **Approve** | `approve()` | `approve` | `BIM.approve()` | WIRED |
| 8 | **Promote** | `promote()` | `promote` | `BIM.promote()` | WIRED |
| 9 | **Compile** | `compile()` | `compile` | `BIM.compile()` / `BIM.completeOrder()` | WIRED |
| 10 | **Poll Commands** | `poll_commands()` | HTTP `pollCommands` | SSE `/events` + `applyScheme` | WIRED |

**All 10 items are wired and call the backend correctly.** The action strings in client.py
match what app.js sends. The protocol is ndjson over TCP (port 9876) for Bonsai, HTTP POST
(port 9878) for Web UI. Both dispatch to the same DesignerServer.dispatch().

### Bonsai Operator Verification (30 operators in operator.py)

All 30 registered operators verified end-to-end:

| Operator | Client Method | Action | Verified |
|----------|--------------|--------|----------|
| `BIM_OT_designer_connect` | `connect()` | TCP socket | YES |
| `BIM_OT_designer_disconnect` | `disconnect()` | TCP close | YES |
| `BIM_OT_designer_list_buildings` | `list_buildings()` | `listBuildings` | YES |
| `BIM_OT_designer_compile` | `compile()` | `compile` | YES |
| `BIM_OT_designer_create_new` | `create_new()` | `createNew` | YES |
| `BIM_OT_designer_toggle_mode` | — (local) | GPU overlay | YES |
| `BIM_OT_designer_focus_section` | — (local) | GPU focus | YES |
| `BIM_OT_designer_peek_metadata` | `get_element_metadata()` | `getElementMetadata` | YES |
| `BIM_OT_designer_snap` | `snap()` | `snap` | YES |
| `BIM_OT_designer_save` | `save()` | `save` | YES |
| `BIM_OT_designer_promote` | `promote()` | `promote` | YES |
| `BIM_OT_designer_browse_items` | `browse_items()` | `browseItems` | YES |
| `BIM_OT_designer_browse_next` | — (via browse_items) | `browseItems` | YES |
| `BIM_OT_designer_browse_prev` | — (via browse_items) | `browseItems` | YES |
| `BIM_OT_designer_place_item` | `place_item()` | `placeItem` | YES |
| `BIM_OT_designer_click_to_place` | `click_to_place()` | `clickToPlace` | YES |
| `BIM_OT_designer_find_similar` | `find_similar()` | `findSimilar` | YES |
| `BIM_OT_designer_add_room` | `add_room()` | `addRoom` | YES |
| `BIM_OT_designer_remove_room` | `remove_room()` | `removeRoom` | YES |
| `BIM_OT_designer_add_storey` | `add_storey()` | `addStorey` | YES |
| `BIM_OT_designer_auto_fix` | `snap(fixRule, fixBomId)` | `snap` | YES |
| `BIM_OT_designer_set_jurisdiction` | `set_jurisdiction()` | `setJurisdiction` | YES |
| `BIM_OT_designer_update_room_dims` | `snap()` | `snap` | YES |
| `BIM_OT_designer_execute_verb` | `execute_verb()` | `verb` | YES |
| `BIM_OT_designer_list_order_lines` | `list_order_lines()` | `listOrderLines` | YES |
| `BIM_OT_designer_update_order_line` | `update_order_line()` | `updateOrderLine` | YES |
| `BIM_OT_designer_get_bom_tree` | `get_bom_tree()` | `getBomTree` | YES |
| `BIM_OT_designer_bom_drop` | `bom_drop()` | `bomDrop` | YES |
| `BIM_OT_designer_approve` | `approve()` | `approve` | YES |
| `BIM_OT_designer_bom_drop_popup` | `bom_drop()` | `bomDrop` | YES |
| `BIM_OT_designer_list_advisories` | `list_advisories()` | `listAdvisories` | YES |

Poll timer: `_poll_commands_timer()` runs every 2s, calls `poll_commands()` via HTTP POST
to port 9878, handles `loadOutput` (loads output.db via db_loader) and `applyScheme` commands.

### Missing from client.py (available in federation but not exposed)

| Method | Federation Operator | Priority |
|--------|-------------------|----------|
| `clash_by_discipline()` | `BIM_OT_clash_by_discipline` | HIGH — Tab 8 needs this |
| `generate_schedule()` | `BIM_OT_generate_construction_schedule` | HIGH — Tab 4 needs this |
| `generate_boq()` | `BIM_OT_export_comprehensive_boq` | HIGH — Tab 5 needs this |
| `import_assets()` | `BIM_OT_import_assets_from_federation` | MEDIUM — Tab 6/7 |
| `export_bcf()` | `BIM_OT_export_bcf` | MEDIUM — Tab 8 |

---

## 5. Federation Preview — Must Remain Intact

The federation addon's native **Preview BBoxes** button (`bpy.ops.bim.preview_federation_viewport`)
renders wireframe bounding boxes from any SQLite federation database. This is the **production path**
for Show in Bonsai.

**Production flow:**

```
BOM Drop → C_Order + C_OrderLine tree (HTML shows BOM tree, no compile)
    ↓
Save → persist order to work_output.db (just save, no compile)
    ↓
Show in Bonsai → compile → output.db → push loadOutput → federation preview
  (each click re-compiles, bboxes always reflect current order with correct positions)
    ↓
Complete → same as Show in Bonsai + finalise order status to CO
    ↓
Full Load → user clicks in Bonsai panel when ready for full mesh geometry
```

**Key design rules:**
- **BOM Drop** → creates order, no compile
- **Save** → persists order only, no compile
- **Show in Bonsai** → always compiles to output.db first, then pushes federation
  preview. Each click re-compiles fresh so bboxes reflect current edits with correct
  spatial positions (tack offsets, storey elevations, room packing from compiler).
  No origin-bunching — the compiler produces real coordinates.
- **Complete** → compile + finalise order (CO status)
- **Full Load** → user-initiated in Bonsai panel for full mesh geometry.
  Creates a `.blend` file. If `.blend` already exists for this output.db,
  skip reload (backend should check and return early). The `.blend` file
  is the source of truth for whether Full Load was already done.

**Do not break:**
- `webui_sync._load_output_command()` — sets `federation_database_path`, calls preview operator
- `bpy.ops.bim.preview_federation_viewport()` — the fast bbox loader from federation module
- `bpy.ops.bim.load_full_federation_viewport_gi()` — the fallback full geometry loader
- The `pollCommands` → `loadOutput` flow in `webui_sync.py`

**S60 proof of concept** (257 wireframe cubes) used a temporary cube-creation fallback from
raw order lines (dx/dy/dz=0, all at origin). The production path compiles first, so the
output.db has correct positions — no origin-bunching.

**What's new vs what existed:**
- **Existed:** Federation preview from a local DB file (click button in Bonsai panel)
- **New (S60):** Browser triggers the preview remotely via HTTP poll. First browser-to-Bonsai
  BIM preview push. The individual pieces (HTTP poll, wireframe, BIM data) all existed —
  the integration of a web BIM configurator pushing to Bonsai's federation viewport is new.

---

## 6. Recommended Next Steps

### Backend actions needed by HTML UI (for backend session)

| Action | Purpose | HTML caller | Priority |
|--------|---------|-------------|----------|
| `scanOutputDbs` | List `.db` files in a given folder (default `DAGCompiler/lib/output`) with name, path, element count, file size. Tab 3 "Show Output DBs" button calls this. | `toggleOutputView()` | **HIGH** — needed for Tab 3 to browse compiled outputs |
| `getConfig` | Return `{libraryDir, outputDir, projectRoot}` so HTML knows server paths. Currently hardcoded. | `init()` on page load | MEDIUM — needed when project folder is on a different path |
| `loadOutput` should NOT be auto-queued by `afterCompile` | Already removed in S60-UI. Confirm backend does not re-add it. | — | **HIGH** — prevents accidental Bonsai loads during testing |
| `completeIt` must return `outputDbPath` | Show in Bonsai needs this to push `loadOutput`. Already works. | `showInBonsai()` | Confirmed working |

### Immediate (S60 scope — no backend changes)

1. **Wire 4D tab to real data** — If `constructionSchedule` action already returns data from
   ScheduleDAO, hide the placeholder and show the table. Test with DemoHouse.
2. **Wire 5D tab to real data** — Same for `costBreakdown` → CostDAO.
3. **Wire 6D tab to real data** — Same for `carbonFootprint` → SustainabilityDAO.
4. **Wire 7D tab to real data** — Same for `maintenanceSchedule` → FacilityMgmtDAO.
5. **Wire 8 Validate to advisories** — `listAdvisories` action already exists in
   DesignerAPI. The new loadValidation() function calls it.

### Next session (S61 — requires backend migration)

1. **Migrate federation operators to DesignerServer** — The 70+ operators in the federation
   module dispatch via Blender's `bpy.ops`. To make them available from Web UI, the Java
   DesignerServer needs action handlers that replicate the logic:
   - `clashByDiscipline` → clash detection from spatial index
   - `generateSchedule` → 4D schedule from compiled output
   - `generateBoq` → BOQ from BOM tree
   - `importAssets` → digital twin asset import

2. **Add client.py methods** — For each new action, add a method to DesignerClient so
   Bonsai operators can also use the Java backend instead of local Python logic.

3. **Clash visualization in Web UI** — Tab 8 Validate should show clash groups with
   severity badges. BCF export button.

### Deferred (S62+)

- IoT sensor dashboard (7D)
- River equipment monitoring
- PDF terrain extraction
- Ghost drag / chain interaction (WF-12..25)
- Multi-user changelog

---

## 7. Lessons Learned (S60-UI session)

Hard-won knowledge from this session. Future sessions MUST read this before touching
the Web UI ↔ Bonsai integration.

### 7.1 Java Server Command Queue — Whitelisted Fields Only

`WebUIServer.handleApplyScheme()` hardcodes `command='applyScheme'` and only copies
whitelisted fields: `schemeName`, `objectName`, `guid`, `color`, `filterDiscipline`,
`filterIfcType`. **Any other field is silently dropped.**

To pass custom data through the queue, encode it in whitelisted fields:
- `schemeName` → command type (e.g. `'previewBBoxes'`, `'loadOutput'`)
- `guid` → payload (e.g. output.db path, building ID)
- `objectName` → display name

**Do not** add a `command` field inside the payload — the server overwrites it with
`'applyScheme'`. Do not add `buildingId` — it's not whitelisted and gets dropped.

### 7.2 Blender 5.0 — No Arbitrary Attributes on bpy.app

Blender 5.0 locked down `bpy.app`. Setting `bpy.app._custom_attr = True` throws
`AttributeError`. Use module-level variables instead:

```python
# WRONG (crashes on Blender 5.0):
bpy.app._sync_active = True

# RIGHT:
_sync_active = False  # module level
def my_function():
    global _sync_active
    _sync_active = True
```

The `global` declaration must come **before** the variable is read in the function,
or Python raises `SyntaxError: name used prior to global declaration`.

### 7.3 Federation Module Import Chain — One Crash Kills All Panels

`federation/__init__.py` line 42 imports ALL submodules in one line:
```python
from . import ui, prop, operator, discipline_legend, cache_monitor, color_palette, crud_operators, webui_sync
```

If **any** submodule has a SyntaxError or ImportError, the **entire federation module**
fails to register. All 10+ federation panels disappear from Bonsai. The `mep_engineering`
module also fails because it imports from `federation.core.gpu_utils`.

**Always verify syntax before committing:**
```bash
python3 -c "import py_compile; py_compile.compile('webui_sync.py', doraise=True)"
```

### 7.4 Snap Blender Version Pinning

Snap auto-updates Blender. Version 5.0→5.1 breaks addon paths because:
- 5.0 uses Python 3.11, config at `~/.config/blender/5.0/extensions/.local/lib/python3.11/`
- 5.1 uses Python 3.13, config at `~/.config/blender/5.1/extensions/.local/lib/python3.13/`

Symlinks across versions don't work (binary-incompatible Python). The `bonsai` alias
in `~/.bashrc` must pin to the specific snap revision:
```bash
alias bonsai="__NV_PRIME_RENDER_OFFLOAD=1 __GLX_VENDOR_LIBRARY_NAME=nvidia /snap/blender/6898/blender"
```

The alias takes precedence over `~/bin/bonsai` script. Check `~/.bashrc` first.

### 7.5 Python Bytecode Cache (.pyc)

After editing federation Python files, Blender may load stale `.pyc` from `__pycache__/`.
Clear before testing:
```bash
find ~/.config/blender/ -path "*federation*" -name "*.pyc" -delete
```

Since the installed addon is symlinked to the source repo (`~/IfcOpenShell/...`),
edits to source files are immediately visible — but only after clearing `.pyc` cache
and restarting Blender.

### 7.6 Two Separate Poll Loops

Bonsai has TWO independent poll timers that both call `pollCommands`:

1. **Federation sync** (`webui_sync.py`) — 0.5s interval, started by "Start Web UI Sync"
   button in federation panel. Handles `applyScheme`, `loadOutput`, `previewBBoxes`.

2. **BIM Designer** (`operator.py`) — 2s interval, started by "Connect" in A.1 panel.
   Handles `loadOutput`, `applyScheme`, `previewBBoxes`.

Commands are consumed from the queue on first poll. If federation sync polls first,
the BIM Designer timer won't see the command, and vice versa. In practice, users
activate one or the other, not both.

### 7.7 HTML is Data Assistant, Bonsai is Viewport

The HTML UI shows order data, BOM trees, validation status. The Bonsai viewport shows
3D geometry. They complement each other:

- **Incremental editing with highlighted bboxes** → Bonsai (Design Mode, GPU overlay)
- **Order line table, ASI attributes, compliance** → HTML (Tab 1, Tab 8)
- **Show in Bonsai** → HTML compiles, pushes result to Bonsai for preview
- **Full Load** → user-initiated in Bonsai panel only (creates .blend)

Do not try to duplicate viewport features in HTML or order features in Bonsai.

### 7.8 Tab 3 Database Path — Input or Output

Tab 3 (Geometry) can load **any** SQLite database — BOM input or compiled output.
Federation Preview and Full Load don't care which type. The "Show Output DBs" toggle
switches the building list between library (BOM input) and output (compiled) databases.

When Show in Bonsai compiles from Tab 1, it sets Tab 3's DB path to the output.db.
Tab 3's Preview/Full Load then work on that same database. One database, multiple views.
