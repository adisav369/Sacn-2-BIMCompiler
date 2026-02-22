# PROGRESS — Current Development State

**Last updated:** 2026-02-23
**Current phase:** VIEW_CONTRACTS — 5 of 6 views live; Phase 4 watchdog call required before next session
**Tests:** 119 total | 117 GREEN | 2 RED (G8-SH, G8-DX — intentional, calibration debt)
**SpatialDigests:** SH=1f325a98 DX=d3c779b9 TB=dd4345f4 Terminal=301b42b1 (stable)

---

## What Was Done This Session (2026-02-23 — VIEW_CONTRACTS closeout)

**VIEW_CONTRACTS.md architecture locked (v1.9)**

Resolved the DocStatus table-type confusion by DB evidence:

| Table | iDempiere analogue | Status column |
|---|---|---|
| `ad_building_registry` | C_Order (order header) | DocStatus deferred — future session |
| `ad_element_rule` | C_OrderLine — `provenance DEFAULT 'BUILDING_DSL'` confirms it | `doc_status = 'CO'` |
| `ad_room_boundary` | M_ spatial quantity (Qty × UOM) | `extracted_from` + `coordinate_frame` gates only |
| `ad_bom`, `ad_bom_child` | M_BOM / M_BOMLine | `is_active = 1` only |
| `component_definitions`, `ad_product_dim` | M_Product | `extracted_from NOT LIKE '%PENDING%'` |
| `ad_geometry_map` | M_Product geometry | `provenance NOT LIKE '%PENDING%'` |

**Rename mapping confirmed (execution deferred — REFACTOR session):**
- `ad_building_registry` → `C_Building_Order` (5 Java, 6 SQL files)
- `ad_element_rule` → `C_Element_Rule` (10 Java, 35 SQL files)
- `ad_room_boundary` → `M_Room_Boundary` (11 Java, 11 SQL files)

**DB migrations applied — all stable:**
- `doc_status` now on exactly ONE table: `ad_element_rule` ✓
- `extracted_from` added to `component_definitions` and `ad_product_dim` (DEFAULT 'PENDING')
- `bom_type` added to `ad_bom`; seeded: 9 ROOM (FLOOR_*/UNIT_*), 26 SET
- `fit_priority` + `min_space_mm` added to `ad_bom_child`
- 1263 `ad_element_rule` rows seeded CO (DX:1115, SH:62, TB:86)
- All six views created in `library/component_library.db`

**Watchdog review (v1.7→v1.8):** §4.3/§4.4 target-state disclaimers added;
Phase 4 watchdog gate recorded in §5.1 and §10.

**Phase 3g (DONE):** `UPDATE ad_room_boundary SET extracted_from='TB_LKTN_DSL' WHERE building_type='TB_LKTN'`
→ v_verified_room_boundary: 0 → **7 rows** (BATHROOM, BEDROOM×3, COMMON, PORCH, TOILET).
Tests stable: 119/2 RED (G8 calibration — unchanged).

**Phase 3h (DONE):**
- `UPDATE component_definitions SET extracted_from='LIBRARY' WHERE geometry_hash IS NOT NULL` → 23,888 rows
- `UPDATE ad_product_dim SET extracted_from=provenance` → 52 rows
- **View SQL correction required:** both v_proven_geometry and v_component_leaf returned 0 rows
  despite seeding. Root cause: original SQL joined `ad_geometry_map ON gm.element_ref = cd.name`
  — namespace mismatch (Revit family:type vs library component name). Zero intersection.
  Fix applied: filter on `cd.vertex_count > 8` + `cd.geometry_hash IS NOT NULL` directly;
  `ifc_class` via correlated subquery `MIN(gm.ifc_class) WHERE gm.geometry_hash = cd.geometry_hash`.
- v_proven_geometry: 0 → **22,013 rows**
- v_component_leaf: 0 → **28 rows**
- VIEW_CONTRACTS.md bumped to **v1.9** — §5.4, §5.6, §8, §10 corrected to match live SQL.
Tests stable: 119/2 RED (G8 calibration — unchanged).

**Migration file:** `migration/migration_VIEW_CONTRACTS_v15_phases.sql`

**Six views — current status:**

| View | Rows | Blocker |
|---|---|---|
| `v_qualified_bom` | 0 | Join key gap: `bc.role` ≠ `pd.product_id` namespace. Phase 4 watchdog call required. |
| `v_verified_room_boundary` | **7** | **LIVE** — TB-LKTN 7 rooms (Phase 3g). SH/DX excluded: calibration debt. |
| `v_compilable_element_rule` | **95** | **LIVE** — DX:61, SH:14, TB:20 |
| `v_proven_geometry` | **22,013** | **LIVE** — Phase 3h + view SQL correction (§5.4). |
| `v_active_bom_assembly` | **29** | **LIVE** — ROOM:9, SET:20 |
| `v_component_leaf` | **28** | **LIVE** — Phase 3h + view SQL correction (§5.6). |

---

## NEXT SESSION — Start Here

**FIRST ACTION (5 min) — v_verified_room_boundary contract violation:**

Live view has 3 values in coordinate_frame IN clause. §2.3 + §5.2 mandate 5.
Any future DERIVED_MM (Template Topology Path) or CONSTRAINT_SOLVED (SpaceSolver)
row will be invisible to the compiler until this is fixed.

```sql
DROP VIEW IF EXISTS v_verified_room_boundary;
CREATE VIEW v_verified_room_boundary AS
SELECT
    rb.id                           AS room_id,
    rb.building_type,
    rb.room_type,
    rb.min_x_mm,
    rb.max_x_mm,
    rb.min_y_mm,
    rb.max_y_mm,
    rb.storey                       AS storey_id,
    rb.extracted_from,
    rb.coordinate_frame,
    (rb.max_x_mm - rb.min_x_mm)    AS width_mm,
    (rb.max_y_mm - rb.min_y_mm)    AS depth_mm
FROM ad_room_boundary rb
WHERE rb.coordinate_frame IN (
        'IFC_GLOBAL_MM',
        'LOCAL_MM',
        'DRAWING_MM',
        'CONSTRAINT_SOLVED',
        'DERIVED_MM'
    )
    AND rb.extracted_from NOT IN ('PENDING','','TODO','UNKNOWN','GRID_DERIVED');
```

Verify: `SELECT COUNT(*) FROM v_verified_room_boundary;` → still 7 (TB-LKTN rows use LOCAL_MM — unaffected).
Run `mvn test`. Digests stable. Then proceed.

---

**GATE: Phase 4 requires a watchdog call first.**
The `v_qualified_bom` join resolution (option a: `product_ref` FK vs option b: bbox from
`component_definitions`) is an architectural decision with downstream consequences for the
entire BOM cascade. Present both options to watchdog before any Phase 4 Java or migration.

Read `docs/VIEW_CONTRACTS.md` §5.1 and §10 for the full join-resolution context.

**Phase 4 (after watchdog call — separate session):**
- Resolve `v_qualified_bom` join per watchdog decision
- `ViewAccessLayer.java` — single access class, queries views only
- `BomTierResolver.java` — cascade state machine (ROOM → SET → ITEM)
- ArchUnit gate — compiler never queries base tables directly
- `ad_building_registry.doc_status` — C_Order lifecycle (DR/IP/CO/VO)

**Phase 1e (prerequisite for Template Topology Path — not optional once SpaceSolver fires):**
Extend `ad_room_boundary.coordinate_frame` CHECK to add `DERIVED_MM` and `CONSTRAINT_SOLVED`.
Requires table recreation (SQLite cannot ALTER CHECK). See `docs/VIEW_CONTRACTS.md §4.6`.
Upgrade from "optional" — DERIVED_MM rows will fail CHECK on INSERT without this migration.

**Deferred watchdog items (documentation-only — do not block Phase 4):**
1. `space_solver_research.md`: scale transform formula applies to dimensions only, not world
   coords. `min_x`/`max_x` are not scalable — they carry world origin offset. Add caveat.
2. `PREFAB_ARCHITECTURE.md` "Three Compilation Modes": SH/DX listed as Mode A examples
   but are currently calibration debt. Add TARGET STATE caveat (same treatment as §4.3/§4.4).
3. Phase 1e: upgrade from "optional" to "prerequisite for Template Topology Path" in §10.
4. §4.6 migration script: add `PRAGMA table_info` warning before table recreation SQL.

**REFACTOR session (large — do not mix with other work):**
- `ad_element_rule` → `C_Element_Rule` (10 Java + 35 SQL files)
- `ad_room_boundary` → `M_Room_Boundary` (11 Java + 11 SQL files)
- `ad_building_registry` → `C_Building_Order` (5 Java + 6 SQL files)
- No FK cascade risk confirmed — safe to rename when dedicated session is available.

---

## G8 Calibration Debt (intentional RED — 2 tests)

**G8-SH (16/17 fail):** SH living-room furniture ~7m off. `ad_room_boundary` LIVING zone
at X(1.62,6.27) but reference IFC is at X(−7.5,−0.01). Bedroom X off ~1.2m.
Fix: re-extract SH room boundaries from reference IFC → update min_x/max_x/min_y/max_y
→ update `extracted_from` to 'IFC_EXTRACTED' → `v_verified_room_boundary` serves them.

**G8-DX (139/173 fail):** DX kitchen calibrated (dist 270–370mm). Living room,
upper-floor bedrooms, bathroom rooms miscalibrated (44 artificial grid cells, no relation
to actual IfcSpace extents).
Fix: extract 11 real IFC rooms from `Ifc2x3_Duplex_extracted.db` → replace grid cells.

Both fixes deferred. The view layer (`v_verified_room_boundary`) now makes the gate
explicit — once real coordinates are loaded with correct `extracted_from`, G8 will pass.

---

## Known Debt

| Item | Count | Severity | Gate? |
|------|-------|----------|-------|
| G8-SH room boundary calibration | 16 fails | RED test | No (intentional) |
| G8-DX room boundary calibration | 139 fails | RED test | No (intentional) |
| Terminal IfcReinforcingBar GIC failures | 8 | Advisory | No |
| Duplex P23 drain corners | 364 | Advisory (MEP flow fittings) | No |
| TB-LKTN P23 drain corners | 6 | Advisory (drain U-shape) | No |
| v_qualified_bom join key gap (bc.role ≠ pd.product_id) | — | Phase 4 gap | No |
| ad_bom_child fit_priority seeds (only COFFEE_TABLE matched) | — | Data gap | No |
| ad_building_registry lacks DocStatus | — | Future session | No |

---

## Phase History (Summary)

| Phase | Status | What |
|-------|--------|------|
| RM-1 | ✅ DONE | 5 relational tables + populated rules (SH 55, DX 1085) |
| RM-2 | ✅ DONE | RelationalResolver shadow validation (SH 100%, DX 100%) |
| RM-3 | ✅ DONE | PlacementAD cutover — RELATIONAL mode live |
| RM-4 | ✅ DONE | TB-LKTN 58→69 elements, LOD400 library, GeometryIntegrityChecker |
| RM-5 | DEFERRED | Flat table → computed cache (non-blocking, data still valid) |
| RM-6 | ✅ DONE | Plumbing system + CONNECTS_TO topology proofs |
| RM-7 | ✅ DONE | Visual defect audit: geometry_map storey, familyRef, P19-P21 |
| RM-8 | ✅ DONE | Registry-driven pipeline — one engine, N buildings from metadata |
| RM-9 | ✅ DONE | rotation_rule authority: 3-table contract, FixturePlacer hardened |
| RM-10 | ✅ DONE | Window depth-cap, GIC LOD_ check, furniture scaling, P22/P23 proofs |
| RM-11 | ✅ DONE | family_ref gates, conn_points orientation, adaptive BOM cascade, MEP GIC fixes |
| B1-B5 | ✅ DONE | TB-LKTN data fixes: doors, water heater, kitchen counter, toilet rotation, ParametricMesh |
| BOM-1 | ✅ DONE | MRP BOM Drop → Schedule → Compile: ad_room_slot × ad_room_boundary → BOM anchors |
| BOM-2a | ✅ DONE | LOD geometry + GGF/GF BOM hierarchy (UNIT_*_STD + FLOOR_*_STD) |
| BOM-2b/c | ✅ DONE | DX storey names, spacing facts, unit orderlines, beam ordinals, floor dZ, W3 beam fix |
| DriftGuard | ✅ DONE | D1/D2/D5 gates fixed, AD Events schema (4 tables), 119 tests |
| G8 Gate | ✅ DONE | RosettaPlacementTest wired, 2 RED = calibration bug list (expected) |
| Mesh2Library | ✅ DONE | HipRoofMesh + HalfRoundDrainMesh grammar wired, LOD400 drain patch |
| VIEW_CONTRACTS | ✅ DONE (5/6 live) | 6 views created; 5 live (3g+3h done); v_qualified_bom awaits Phase 4 watchdog call |
