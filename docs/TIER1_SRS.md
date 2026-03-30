# Tier 1 SRS — 4D, 5D, 6D, 7D, Audit Trail, 3D Native
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md)

<div class="bim-banner" markdown>
<b>4D scheduling, 5D costing, 6D carbon, 7D facilities management.</b> Six bounded items built on Java DAOs with thin ndjson bridge to Bonsai panels.
</div>

**Version:** 1.1 | **Date:** 2026-03-20
**Scope:** Six bounded items, +4 scorecard points (27→31/36) + live 4D/5D DAOs
**Architecture:** Java DAO backend + thin bridge (ndjson TCP) + light Bonsai panel
**Companion:** `StrategicIndustryPositioning.md` (scorecard)
**IfcOpenShell reference:** Federation addon `tandem/` — Python PoC proves all 4 capabilities

---

## Architecture Principle

> **Java-smart / Python-dumb.** The DAO layer owns all SQL, rollup logic, and
> business rules. The Bonsai addon receives pre-computed JSON and renders it.
> The thin bridge is ndjson over TCP — same pattern as the existing 28 actions
> in `DesignerServer.java`. Each new capability = 1 DAO + 1 wire action + 1 panel.

```
┌─────────────────────────────────────────────────────────┐
│  Bonsai Addon (Python)          LIGHT UI                │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────┐  │
│  │ 6D Panel │ │ 7D Panel │ │ Audit    │ │ 3D View   │  │
│  │ Carbon   │ │ FM Sched │ │ History  │ │ (existing)│  │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └───────────┘  │
│       │             │            │                       │
│       └─────────────┴────────────┘                       │
│                     │ ndjson TCP                         │
├─────────────────────┼───────────────────────────────────┤
│  Java Backend       │          SOLID DAO                │
│  ┌──────────────────▼──────────────────────────────┐    │
│  │ DesignerServer.java (action dispatcher)         │    │
│  │   case "carbonFootprint" → api.carbonFootprint()│    │
│  │   case "maintenanceSchedule" → api.maintenance()│    │
│  │   case "changelog" → api.changelog()            │    │
│  └──────────────────┬──────────────────────────────┘    │
│  ┌──────────────────▼──────────────────────────────┐    │
│  │ DesignerAPIImpl.java (orchestration)            │    │
│  │   opens component_library.db + BOM.db           │    │
│  │   delegates to SustainabilityDAO / FacilityDAO  │    │
│  │   delegates to ChangelogDAO                     │    │
│  └──────────────────┬──────────────────────────────┘    │
│  ┌──────────────────▼──────────────────────────────┐    │
│  │ DAO Layer (SQL against existing databases)      │    │
│  │   SustainabilityDAO → component_library.db      │    │
│  │   FacilityMgmtDAO  → component_library.db       │    │
│  │   ChangelogDAO     → output.db                   │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
│  databases: component_library.db  {P}_BOM.db            │
│             output.db              ERP.db     │
└─────────────────────────────────────────────────────────┘
```

---

## §1 — 6D Sustainability (embodied carbon + BOM rollup)

### §1.1 Problem

The scorecard shows 6D=1\* (PoC only). The Federation addon's
`carbon_pipeline.py` hardcodes ICE Database factors into Python. We need the
same capability re-grounded on ERP columns so the compiler can rollup carbon
per BOM tree — same pattern as cost rollup.

### §1.2 Schema (migration V010 — already exists)

`migration/V010_sustainability_columns.sql` adds to `M_Product` in
`component_library.db`:

| Column | Type | Default | Source |
|--------|------|---------|--------|
| `carbon_kg_per_unit` | REAL | 0 | ICE Database v3.0 (Bath Uni) |
| `recyclability` | TEXT | 'UNKNOWN' | UNKNOWN / HIGH / MEDIUM / LOW / NONE |
| `eol_strategy` | TEXT | 'LANDFILL' | LANDFILL / RECYCLE / REUSE / DECONSTRUCT |
| `lifespan_years` | INTEGER | 50 | Product-specific, CIBSE Guide M |
| `maintenance_interval_months` | INTEGER | 12 | Product-specific (shared with §2) |

**Seed data:** Populate from ICE Database factors for the ~30 product types
already in component_library.db. Example:

```sql
UPDATE M_Product SET carbon_kg_per_unit = 0.15, recyclability = 'HIGH'
WHERE product_type = 'concrete' AND name LIKE '%C30%';
-- Concrete C30: 0.15 kgCO2e/kg (ICE v3.0)

UPDATE M_Product SET carbon_kg_per_unit = 1.55, recyclability = 'HIGH'
WHERE product_type = 'steel' AND name LIKE '%structural%';
-- Structural steel: 1.55 kgCO2e/kg (ICE v3.0, world average)
```

Migration: `V010b_carbon_seed_data.sql` (seed only, append-only).

### §1.3 DAO — SustainabilityDAO

```java
// Implementing TIER1_SRS.md §1.3 — Witness: W-6D-CARBON-1
public class SustainabilityDAO {

    private final Connection compLibConn;   // component_library.db
    private final Connection bomConn;       // {PREFIX}_BOM.db

    // ── Record types ────────────────────────────────────
    record CarbonLine(String bomId, String productName, String material,
                      int qty, double carbonPerUnit, double totalCarbon,
                      String recyclability, String eolStrategy) {}

    record CarbonSummary(double totalCarbonKg, double carbonPerSqM,
                         double grossFloorAreaSqM,
                         List<CarbonLine> lines,
                         Map<String, Double> byDiscipline,
                         Map<String, Double> byMaterial) {}

    // ── Queries ─────────────────────────────────────────

    /**
     * Rollup embodied carbon for a building.
     * Joins m_bom_line (qty) × M_Product (carbon_kg_per_unit).
     * Groups by discipline and material for breakdown charts.
     */
    CarbonSummary carbonFootprint(String buildingId);

    /**
     * Single-element carbon lookup — for the properties popup (§26.6).
     * Returns carbon + recyclability + EOL for one M_Product.
     */
    CarbonLine elementCarbon(String bomId);

    /**
     * Material passport — circular economy inventory.
     * Groups all BOM lines by material, sums mass, carbon, recyclability.
     */
    List<MaterialPassportLine> materialPassport(String buildingId);

    record MaterialPassportLine(String material, double totalMassKg,
                                double totalCarbonKg, String avgRecyclability,
                                int productCount) {}
}
```

**SQL pattern (carbonFootprint):**

```sql
SELECT bl.bom_id, p.name, p.product_type,
       bl.qty, p.carbon_kg_per_unit,
       (bl.qty * p.carbon_kg_per_unit) AS total_carbon,
       p.recyclability, p.eol_strategy,
       bom.m_product_category_id AS discipline
FROM m_bom_line bl
JOIN m_bom bom ON bl.m_bom_id = bom.m_bom_id
JOIN M_Product p ON bl.m_product_id = p.m_product_id
WHERE bom.building_id = ?
ORDER BY total_carbon DESC;
```

### §1.4 Wire Protocol

```
→ {"action":"carbonFootprint","buildingId":"Terminal_KLIA"}
← {"success":true,"totalCarbonKg":284500.0,"carbonPerSqM":42.3,
   "grossFloorAreaSqM":6726.0,
   "lines":[{"bomId":"TE_GF_SLAB_01","productName":"C30 Slab",
             "material":"concrete","qty":120,"carbonPerUnit":0.15,
             "totalCarbon":18.0,"recyclability":"HIGH","eolStrategy":"RECYCLE"},...],
   "byDiscipline":{"STR":180000,"ARC":60000,"MEP":44500},
   "byMaterial":{"concrete":180000,"steel":80000,"glass":24500}}
```

### §1.5 Light UI — Carbon Panel

```
┌─ 6D SUSTAINABILITY ──────────────────────────┐
│ Building: Terminal KLIA                       │
│ Total Embodied Carbon: 284,500 kgCO₂e        │
│ Carbon Intensity: 42.3 kgCO₂e/m²             │
│                                               │
│ By Discipline:          By Material:          │
│ ■ STR  180,000 (63%)    ■ Concrete 180,000    │
│ ■ ARC   60,000 (21%)    ■ Steel     80,000    │
│ ■ MEP   44,500 (16%)    ■ Glass     24,500    │
│                                               │
│ [Refresh] [Export CSV] [Material Passport]    │
└───────────────────────────────────────────────┘
```

Python addon: `panel_sustainability.py` — one `draw()` method, reads JSON
from bridge, renders labels + proportion bars. No SQL, no logic.

### §1.6 Witnesses

| ID | Claim | Gate |
|----|-------|------|
| W-6D-CARBON-1 | `SustainabilityDAO.carbonFootprint()` returns non-empty `CarbonSummary` for SH building | Unit |
| W-6D-CARBON-2 | Total carbon = SUM(qty × carbon_kg_per_unit) across all BOM lines | Unit |
| W-6D-CARBON-3 | `byDiscipline` map keys match BOM discipline categories | Unit |
| W-6D-CARBON-4 | Wire action `carbonFootprint` returns JSON with `totalCarbonKg > 0` | Integration |
| W-6D-SEED-1 | V010b seed data populates ≥ 10 products with `carbon_kg_per_unit > 0` | Migration |

### §1.7 Scorecard Impact

6D: 1\* → **2** (built-in). The DAO rollups carbon from ERP product table,
not a hardcoded script. The data comes from the same M_Product catalog that
drives procurement — carbon is a first-class product attribute.

---

## §2 — 7D Facility Management (maintenance schedule + lifecycle)

### §2.1 Problem

Scorecard 7D=1\* (PoC only). The Federation addon's `maintenance_manager.py`
+ `pm_scheduler.py` implement full work-order CRUD in Python. We need the
same query capability on the ERP side: given a compiled building, generate
a maintenance schedule from product attributes.

### §2.2 Schema (shared with §1 — V010 already exists)

Uses the same `M_Product` columns from V010:
- `maintenance_interval_months` — how often this product type needs service
- `lifespan_years` — expected replacement age

No new tables needed. The maintenance schedule is a **computed view** over
the BOM — not a stored work order. Work orders are the ERP system's job
(iDempiere C_Order). We provide the data to generate them.

**Seed data** (V010b, same migration as §1):

```sql
UPDATE M_Product SET maintenance_interval_months = 3, lifespan_years = 15
WHERE product_type = 'mechanical' AND name LIKE '%AHU%';
-- Air handling units: quarterly inspection, 15-year life

UPDATE M_Product SET maintenance_interval_months = 6, lifespan_years = 25
WHERE product_type = 'electrical' AND name LIKE '%DB%';
-- Distribution boards: 6-monthly check, 25-year life

UPDATE M_Product SET maintenance_interval_months = 60, lifespan_years = 100
WHERE product_type = 'concrete';
-- Structural concrete: 5-year inspection, 100-year design life
```

### §2.3 DAO — FacilityMgmtDAO

```java
// Implementing TIER1_SRS.md §2.3 — Witness: W-7D-FM-1
public class FacilityMgmtDAO {

    private final Connection compLibConn;
    private final Connection bomConn;

    // ── Record types ────────────────────────────────────
    record MaintenanceItem(String bomId, String productName,
                           String location, String discipline,
                           int intervalMonths, int lifespanYears,
                           int qtyInBuilding) {}

    record MaintenanceSchedule(String buildingId,
                               List<MaintenanceItem> items,
                               int totalAssets,
                               Map<String, Integer> byInterval,
                               double annualMaintenanceEvents) {}

    record LifecycleItem(String productName, String material,
                         int lifespanYears, int qty,
                         double replacementCostUnit,
                         double totalReplacementCost) {}

    record LifecycleSummary(String buildingId,
                            List<LifecycleItem> items,
                            double totalReplacementCost,
                            Map<Integer, Double> costByDecade) {}

    // ── Queries ─────────────────────────────────────────

    /**
     * Generate maintenance schedule for a compiled building.
     * Joins m_bom_line × M_Product(maintenance_interval_months).
     * Groups by interval for calendar planning.
     */
    MaintenanceSchedule maintenanceSchedule(String buildingId);

    /**
     * Lifecycle cost projection — replacement cost over building life.
     * Groups by decade: what needs replacing at 10/20/30/40/50 years.
     */
    LifecycleSummary lifecycleCost(String buildingId, int horizonYears);

    /**
     * Asset register — COBie-compatible list of maintainable items.
     * Returns location (storey + room), type, interval, manufacturer.
     */
    List<AssetRecord> assetRegister(String buildingId);

    record AssetRecord(String guid, String type, String location,
                       String floor, String system, String manufacturer,
                       int intervalMonths) {}
}
```

**SQL pattern (maintenanceSchedule):**

```sql
SELECT bl.bom_id, p.name, bom.m_product_category_id AS discipline,
       parent_bom.name AS location,
       p.maintenance_interval_months, p.lifespan_years,
       COUNT(*) AS qty_in_building
FROM m_bom_line bl
JOIN m_bom bom ON bl.m_bom_id = bom.m_bom_id
JOIN M_Product p ON bl.m_product_id = p.m_product_id
LEFT JOIN m_bom parent_bom ON bom.parent_bom_id = parent_bom.m_bom_id
WHERE bom.building_id = ?
  AND p.maintenance_interval_months > 0
GROUP BY p.m_product_id, bom.m_product_category_id
ORDER BY p.maintenance_interval_months ASC;
```

**Annual maintenance events** = SUM(12 / interval_months × qty) across items.

### §2.4 Wire Protocol

```
→ {"action":"maintenanceSchedule","buildingId":"Terminal_KLIA"}
← {"success":true,"buildingId":"Terminal_KLIA","totalAssets":342,
   "annualMaintenanceEvents":1860.0,
   "byInterval":{"3":120,"6":80,"12":100,"60":42},
   "items":[{"bomId":"TE_3F_AHU_01","productName":"AHU-140",
             "location":"Level 3","discipline":"MEP",
             "intervalMonths":3,"lifespanYears":15,"qtyInBuilding":24},...]}

→ {"action":"lifecycleCost","buildingId":"Terminal_KLIA","horizonYears":50}
← {"success":true,"totalReplacementCost":45000000.0,
   "costByDecade":{"10":2000000,"20":8000000,"30":15000000,"40":12000000,"50":8000000},
   "items":[...]}
```

### §2.5 Light UI — FM Panel

```
┌─ 7D FACILITY MANAGEMENT ─────────────────────┐
│ Building: Terminal KLIA                        │
│ Total Maintainable Assets: 342                 │
│ Annual Maintenance Events: 1,860               │
│                                                │
│ By Interval:                                   │
│ ■ Quarterly (3mo)  120 assets  480 events/yr   │
│ ■ Bi-annual (6mo)   80 assets  160 events/yr   │
│ ■ Annual   (12mo)  100 assets  100 events/yr   │
│ ■ 5-yearly (60mo)   42 assets    8 events/yr   │
│                                                │
│ Lifecycle Cost (50yr): $45,000,000             │
│ Peak decade: 30yr ($15M replacements)          │
│                                                │
│ [Refresh] [Export COBie] [Lifecycle Chart]      │
└────────────────────────────────────────────────┘
```

### §2.6 Witnesses

| ID | Claim | Gate |
|----|-------|------|
| W-7D-FM-1 | `FacilityMgmtDAO.maintenanceSchedule()` returns items with `intervalMonths > 0` for SH building | Unit |
| W-7D-FM-2 | `annualMaintenanceEvents` = SUM(12 / intervalMonths × qty) | Unit |
| W-7D-FM-3 | `lifecycleCost()` `costByDecade` sums to `totalReplacementCost` | Unit |
| W-7D-FM-4 | `assetRegister()` returns COBie-compatible records with location hierarchy | Unit |
| W-7D-FM-5 | Wire action `maintenanceSchedule` returns JSON with `totalAssets > 0` | Integration |

### §2.7 Scorecard Impact

7D: 1\* → **2** (built-in). Maintenance schedule derived from ERP product
master — same M_Product table that drives procurement. Lifecycle cost uses
product cost × lifespan, not a separate FM system.

---

## §3 — Audit Trail (bim_changelog + DAO interceptor)

### §3.1 Problem

Scorecard CR/Audit=1\* (spec only). The Federation addon's
`asset_history` table tracks asset changes. We need the same pattern on
the output.db side: every `save()` logs what changed, enabling
undo and multi-user conflict detection.

### §3.2 Schema — migration V011_changelog.sql

New table in **output.db** (not component_library.db — changelog
tracks design edits, not catalog changes):

```sql
-- V011_changelog.sql
-- Audit trail for design changes in output.db
-- APPEND-ONLY: never modify this migration after first run
-- Implementing TIER1_SRS.md §3.2 — Witness: W-AUDIT-DDL-1

CREATE TABLE IF NOT EXISTS bim_changelog (
    changelog_id    INTEGER PRIMARY KEY AUTOINCREMENT,
    building_id     TEXT NOT NULL,
    variant_id      TEXT,               -- W_Variant.variant_id if save-triggered
    user_id         TEXT DEFAULT 'local',-- future: AD_User.user_id
    timestamp       TEXT NOT NULL,       -- ISO 8601: 2026-03-20T14:30:00Z
    action          TEXT NOT NULL,       -- SAVE, PLACE, MOVE, RESIZE, DELETE, PROMOTE
    entity_type     TEXT NOT NULL,       -- C_OrderLine, W_Variant, M_BOM
    entity_id       TEXT NOT NULL,       -- the row ID that changed
    field_name      TEXT,                -- column name (null for INSERT/DELETE)
    old_value       TEXT,                -- previous value (null for INSERT)
    new_value       TEXT,                -- new value (null for DELETE)
    m_bom_id        INTEGER,             -- FK to m_bom(M_BOM_ID) — spatial context
    CONSTRAINT ck_action CHECK (action IN
        ('SAVE','PLACE','MOVE','RESIZE','DELETE','PROMOTE','UNDO'))
);

CREATE INDEX IF NOT EXISTS idx_changelog_building
    ON bim_changelog(building_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_changelog_entity
    ON bim_changelog(entity_type, entity_id);
```

### §3.3 DAO — ChangelogDAO

```java
// Implementing TIER1_SRS.md §3.3 — Witness: W-AUDIT-DAO-1
public class ChangelogDAO {

    private final Connection outputConn;  // output.db

    // ── Record types ────────────────────────────────────
    record ChangeEntry(long changelogId, String buildingId,
                       String variantId, String userId,
                       String timestamp, String action,
                       String entityType, String entityId,
                       String fieldName, String oldValue,
                       String newValue, String bomId) {}

    // ── Write ───────────────────────────────────────────

    /**
     * Log a single field change. Called by the interceptor wrapper
     * around WorkOutputDAO.save().
     */
    void logChange(String buildingId, String variantId,
                   String action, String entityType, String entityId,
                   String fieldName, String oldValue, String newValue,
                   String bomId);

    /**
     * Log a batch of changes from a save() — one entry per bbox
     * that differs from its previous state.
     */
    void logSave(String buildingId, String variantId,
                 List<DesignBBox> oldBboxes, List<DesignBBox> newBboxes);

    // ── Read ────────────────────────────────────────────

    /**
     * Changelog for a building, newest first.
     * Used by the Audit History panel.
     */
    List<ChangeEntry> getChangelog(String buildingId, int limit);

    /**
     * Changes between two variants — the diff.
     */
    List<ChangeEntry> getChangesBetween(String buildingId,
                                         String fromVariantId,
                                         String toVariantId);

    // ── Undo ────────────────────────────────────────────

    /**
     * Undo the most recent N changes for a building.
     * Reads old_value from changelog, writes it back to C_OrderLine.
     * Logs the undo itself as action='UNDO'.
     *
     * @return number of changes reverted
     */
    int undoChanges(String buildingId, int count);
}
```

### §3.4 Interceptor Pattern

The interceptor wraps `WorkOutputDAO.save()` — no changes to save() itself:

```java
// In DesignerAPIImpl.save():
public SaveResponse save(String buildingId, List<DesignBBox> bboxes, String label) {
    // 1. Read current state (old bboxes)
    List<DesignBBox> oldBboxes = workDao.recallLatest(buildingId);

    // 2. Delegate to existing save
    SaveResponse resp = workDao.save(buildingId, bboxes, label);

    // 3. Log the diff (interceptor pattern)
    if (resp.success()) {
        changelogDao.logSave(buildingId, resp.variantId(),
                             oldBboxes, bboxes);
    }
    return resp;
}
```

**Diff logic in `logSave()`:** Compare old vs new bbox lists by bomId.
For each bbox that exists in both:
- If position changed → log MOVE with old/new x,y,z
- If dimensions changed → log RESIZE with old/new w,d,h

For bboxes only in new → log PLACE.
For bboxes only in old → log DELETE.

### §3.5 Wire Protocol

```
→ {"action":"changelog","buildingId":"SampleHouse","limit":20}
← {"success":true,"entries":[
     {"changelogId":42,"buildingId":"SampleHouse","userId":"local",
      "timestamp":"2026-03-20T14:30:00Z","action":"MOVE",
      "entityType":"C_OrderLine","entityId":"OL_102",
      "fieldName":"offset_x_mm","oldValue":"1500","newValue":"2000",
      "bomId":"SH_GF_BD_01"},
     ...]}

→ {"action":"undoChanges","buildingId":"SampleHouse","count":1}
← {"success":true,"reverted":1,"currentVariantId":"v_003"}
```

### §3.6 Light UI — Audit History Panel

```
┌─ AUDIT TRAIL ─────────────────────────────────┐
│ Building: SampleHouse                          │
│                                                │
│ 14:30 MOVE   SH_GF_BD_01  x: 1500 → 2000    │
│ 14:28 RESIZE SH_GF_LR_01  w: 4000 → 4500    │
│ 14:25 PLACE  SH_GF_KT_01  (new kitchen)      │
│ 14:20 SAVE   variant: "Option A"              │
│ 14:15 DELETE SH_GF_ST_02  (removed store)     │
│                                                │
│ [Undo Last] [Undo 5] [Export Log]              │
└────────────────────────────────────────────────┘
```

### §3.7 Witnesses

| ID | Claim | Gate |
|----|-------|------|
| W-AUDIT-DDL-1 | `bim_changelog` table created by V011 migration, 11 columns + 2 indexes | Migration |
| W-AUDIT-DAO-1 | `ChangelogDAO.logChange()` inserts row with correct timestamp + action | Unit |
| W-AUDIT-DAO-2 | `logSave()` detects MOVE (position diff) and RESIZE (dimension diff) | Unit |
| W-AUDIT-DAO-3 | `undoChanges(1)` reverts most recent change and logs UNDO action | Unit |
| W-AUDIT-WIRE-1 | Wire action `changelog` returns entries with `action ∈ {SAVE,MOVE,RESIZE,PLACE,DELETE}` | Integration |
| W-AUDIT-INTERCEPT-1 | `save()` with changed bboxes produces changelog entries; save() with identical bboxes produces zero entries | Integration |

### §3.8 Scorecard Impact

CR/Audit: 1\* → **2** (built-in). Every design edit logged with old/new
values, undo capability, diff between variants. Multi-user is separate
(Tier 3) — this gives single-user audit trail now.

---

## §4 — 3D Score Bump (2→3, native via Blender viewport)

### §4.1 Problem

Scorecard 3D=2 (WF-BB Phase 2 + Federation 50K loading). Score 3 requires
"native 3D" — prove the compiler's output is visually correct in a real
3D viewport, not just mathematically.

### §4.2 What Already Exists

1. **Federation addon** — loads `output.db` → Blender objects with materials,
   storey hierarchy, discipline colours. Proven on 48K+ elements.
2. **WF-BB Phase 2** — wireframe bbox rendering with per-object BOUNDS
   display. Working in Bonsai viewport.
3. **BlenderBridge** — incremental delta update (spec, partially wired).

### §4.3 What's Needed

A single visual proof: compile SH (SampleHouse), load in Bonsai, screenshot,
add to test evidence. No new code — just exercise the existing pipeline
end-to-end and capture the result.

**Steps:**

1. Run `./scripts/run_RosettaStones.sh classify_sh.yaml` → SH output.db
2. Open Bonsai, load output.db via Federation addon's "Load Federation DB"
3. Verify: 58 elements visible, correct storey hierarchy, materials assigned
4. Screenshot → `~/Pictures/Screenshots/SH_3D_proof.png`
5. Add screenshot path to `RosettaStoneGateTest.java` G7 claim comment

### §4.4 Wire Action (optional — for programmatic screenshot)

```
→ {"action":"screenshot","buildingId":"Ifc4_SampleHouse","outputPath":"/tmp/sh_3d.png"}
← {"success":true,"path":"/tmp/sh_3d.png","elementCount":58,"format":"PNG"}
```

This is a **convenience action** — the real proof is the manual screenshot.
The wire action calls `bpy.ops.render.opengl()` via the bridge.

### §4.5 Witnesses

| ID | Claim | Gate |
|----|-------|------|
| W-3D-VIS-1 | SH building loads in Bonsai with 58 visible objects | Visual |
| W-3D-VIS-2 | Storey hierarchy visible in outliner (GF, FF, Roof) | Visual |
| W-3D-VIS-3 | Screenshot saved to `~/Pictures/Screenshots/` | Visual |

### §4.6 Scorecard Impact

3D: 2 → **3** (native). The output is rendered in Blender's native viewport
via the Federation addon. Not a plugin viewer, not a web viewer — the same
GPU-accelerated viewport used by 3D artists. Blender IS the viewer.

---

## §5 — Implementation Order

| Step | Item | Effort | Depends On | Deliverable |
|------|------|--------|-----------|-------------|
| 1 | V010 migration (run) | 10 min | — | Columns exist in component_library.db |
| 2 | V010b seed data | 30 min | Step 1 | ≥ 10 products with carbon + interval data |
| 3 | SustainabilityDAO + test | 1.5 hrs | Step 2 | W-6D-CARBON-1..3 GREEN |
| 4 | FacilityMgmtDAO + test | 1.5 hrs | Step 2 | W-7D-FM-1..4 GREEN |
| 5 | V011 changelog migration | 10 min | — | bim_changelog table in output.db |
| 6 | ChangelogDAO + interceptor + test | 2 hrs | Step 5 | W-AUDIT-DAO-1..3, W-AUDIT-INTERCEPT-1 GREEN |
| 7 | Wire actions (3 new) | 1 hr | Steps 3,4,6 | W-6D-CARBON-4, W-7D-FM-5, W-AUDIT-WIRE-1 GREEN |
| 8 | 3D visual proof | 30 min | — | W-3D-VIS-1..3 (screenshot) |
| 9 | Panel stubs (Python) | 30 min | Step 7 | 3 light panels render JSON from bridge |

**Total: ~8 hours, +4 scorecard points (27→31/36).**

Steps 1-2 are shared between §1 and §2 (one migration serves both).
Steps 3+4 can run in parallel (independent DAOs).
Step 8 is independent of everything else.

---

## §6 — What We Do NOT Build

| Out of Scope | Why | Where Instead |
|-------------|-----|---------------|
| Work order CRUD | ERP system's job (iDempiere C_Order) | `DocAction_SRS.md` §1 |
| Technician assignment | FM system's job | Federation addon PoC |
| Sensor/IoT monitoring | Requires live infra, not compile-time | Federation addon PoC |
| Multi-user audit | Needs AD_User + AD_Session | Tier 3 |
| PDF report generation | Needs template engine | Phase RE (ACTION_ROADMAP.md) |
| COBie XLSX export | Needs 19-sheet writer | Phase RE (ACTION_ROADMAP.md) |

The boundary is clear: **we add product attributes + rollup queries + audit
logging.** The ERP/FM systems consume our data. We don't replicate them.

---

## §7 — Traceability Matrix

| SRS Section | Migration | DAO | Wire Action | Panel | Witnesses |
|-------------|-----------|-----|-------------|-------|-----------|
| §1 6D Carbon | V010 + V010b | SustainabilityDAO | `carbonFootprint` | panel_sustainability.py | W-6D-CARBON-1..5 |
| §2 7D FM | V010 (shared) | FacilityMgmtDAO | `maintenanceSchedule`, `lifecycleCost` | panel_fm.py | W-7D-FM-1..5 |
| §3 Audit | V011 | ChangelogDAO | `changelog`, `undoChanges` | panel_audit.py | W-AUDIT-DDL-1, W-AUDIT-DAO-1..3, W-AUDIT-WIRE-1, W-AUDIT-INTERCEPT-1 |
| §4 3D | — | — | `screenshot` (optional) | — | W-3D-VIS-1..3 |

| §5 4D Schedule | V012 + V012b | ScheduleDAO | `constructionSchedule` | panel_schedule.py | W-4D-SCHED-1..5 |
| §6 5D Cost | V012 (shared) | CostDAO | `costBreakdown` | panel_cost.py | W-5D-COST-1..6 |

**Total new witnesses: 29** (18 from §1-§4 + 11 from §5-§6)

---

## §5 — 4D Construction Schedule (CIDB sequence rules)

### §5.1 Problem

4D scheduling was offline — W_Verb_Node verb ordering in the compiler pipeline.
No live wire action for the UI to query a Gantt schedule.

### §5.2 Schema (migration V012)

New columns on M_Product in component_library.db:

| Column | Type | Source |
|--------|------|--------|
| `construction_phase` | TEXT | Substructure / Superstructure / Architecture / Finishes |
| `construction_sequence` | INTEGER | 1=footings, 2=columns, 3=beams, 4=slabs, 6=walls, 7=doors, 8=roof, 10=finishes |
| `labor_resource` | TEXT | CONCRETE_GANG / STEEL_ERECTOR / MASON / CARPENTER / ROOFER |
| `labor_rate_rm_per_day` | REAL | CIDB 2024 daily rate (RM) |
| `labor_crew_size` | INTEGER | Workers per gang |
| `productivity_rate` | REAL | Units per day (M/day, M²/day, EA/day) |
| `equipment_type` | TEXT | MOBILE_CRANE_20T / CONCRETE_PUMP / SCISSOR_LIFT_8M |
| `equipment_rate_rm_per_day` | REAL | Daily hire rate (RM) |
| `equipment_duration_factor` | REAL | Fraction of labor days needing equipment |

### §5.3 DAO — ScheduleDAO

Algorithm: BOM lines → group by (phase, sequence, discipline) → calculate durations
(qty / productivity) → chain phases → Malaysian calendar (Mon-Sat, skip Sunday).

**Wire action:** `{"action":"constructionSchedule","buildingId":"SH","projectStartDate":"2026-04-01"}`

### §5.4 Witnesses

| ID | Claim |
|----|-------|
| W-4D-SCHED-1 | Tasks ordered by construction sequence |
| W-4D-SCHED-2 | Phases follow order: Substructure → Superstructure → Architecture → Finishes |
| W-4D-SCHED-3 | All dates valid, finish > start |
| W-4D-SCHED-4 | Labor days by resource populated with ≥3 resources |
| W-4D-SCHED-5 | addWorkingDays skips Sundays (Malaysian calendar) |

---

## §6 — 5D Cost Breakdown (CIDB Malaysia 2024 rates)

### §6.1 Problem

5D cost was offline BOM → C_OrderLine. No live 3-component cost breakdown.

### §6.2 Schema (shared with §5 — V012)

Additional M_Product columns:

| Column | Type | Source |
|--------|------|--------|
| `unit_cost_rm` | REAL | CIDB/PWD 203A 2024 material rate (RM per unit) |
| `cost_uom` | TEXT | Trade UOM: EA (fittings, terminals), M (pipe/duct/beam), M2 (wall/slab/covering), KG (rebar). M3 for concrete volume only. See DISC_VALIDATION_DB_SRS §10.4.11 T3.5 |
| `cost_spec` | TEXT | Spec description (e.g. "Grade 50, shop fab, fire protection") |

### §6.3 DAO — CostDAO

Three-component cost per BOM line:
- **Material** = qty × unit_cost_rm
- **Labor** = (qty / productivity) × crew_size × labor_rate_rm_per_day
- **Equipment** = labor_days × equipment_duration_factor × equipment_rate_rm_per_day

**Wire action:** `{"action":"costBreakdown","buildingId":"SH"}`

### §6.4 Dual Access Pattern

The same DAO serves both UI and back-office:
- **Frontend (Bonsai)**: Panel sends `costBreakdown` for the loaded building → renders bar chart
- **Back-office**: CLI/REST client sends same JSON, selects any project by buildingId
- **Print format**: Backend controls JSON structure; client renders as needed (table, chart, PDF)
- **Templates**: M_Product rates are the template — users adjust via direct SQL or a rate editor

### §6.5 Witnesses

| ID | Claim |
|----|-------|
| W-5D-COST-1 | costBreakdown returns lines with material + labor + equipment > 0 |
| W-5D-COST-2 | grandTotal = materialTotal + laborTotal + equipmentTotal |
| W-5D-COST-3 | Percentages sum to 100% and material > labor |
| W-5D-COST-4 | byDiscipline keys match BOM categories (STR, ARC) |
| W-5D-COST-5 | byPhase covers construction phases (Superstructure etc) |
| W-5D-COST-6 | elementCost returns single-product lookup (10 beams × RM680 = RM6800) |

---

*TIER1_SRS.md v1.1 — 4D-7D + Audit + 3D, 29 witnesses, 242 tests GREEN*
*Architecture: Java DAO (solid) + thin bridge (ndjson) + light panel (Python)*
*Cross-references: StrategicIndustryPositioning.md, BlenderBridge.md*
*CIDB rates: Federation addon boq/comprehensive_boq_export.py → M_Product columns*
