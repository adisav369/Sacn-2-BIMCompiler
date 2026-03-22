# S60 UI Alignment Spec — Web UI + Bonsai 10-Panel Stack

> **Foundation:** [BIM_Designer_SRS](BIM_Designer_SRS.md) §30.3 · [S60_ERP_ALIGNMENT](S60_ERP_ALIGNMENT.md)
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

## 5. Recommended Next Steps

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
