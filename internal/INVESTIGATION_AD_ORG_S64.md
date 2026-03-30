# Investigation Report — AD_Org Tasks 1–5 (S64, 2026-03-23)

> **Status:** COMPLETE. Findings implemented starting S65.
> **Method:** Grep + read of all Java source, SQL migrations, schema snapshots.
> **Cross-referenced against:** AUDIT_S51_FOCUSED.md Appendix F, MANIFESTO.md, BBC.md §2.
> **Moved from:** `docs/DISC_VALIDATION_DB_SRS.md` §11.1–11.5 (cleanup, 2026-03-31)

---

## Task 1 — Java Files Reading M_Product by Database

**85 Java files** reference M_Product. Breakdown by connection:

| Connection | Database | Files | Key Readers |
|------------|----------|-------|-------------|
| `compConn` | component_library.db | ~20 | BOMWalker, OrderLineWalker, ProductRegistrar, 4 BackOffice DAOs, ProductGeometry, MetadataValidator |
| `bomConn` | {PREFIX}_BOM.db | ~8 | PlacementCollectorVisitor (fallback dims), BomValidator (counts), BomDropper (FK ref only) |
| `conn` (PO layer) | any DB with M_Product | ~12 | MProduct.java, X_MProduct.java, X_M_BOM.java, X_M_BOMLine.java |
| Test files | mixed | ~45 | DataIntegrityTest, MetadataIntegrityTest, DiscValidationDBTest, Tier1Test, etc. |

**Key finding:** Zero files read M_Product from ERP.db. The discipline DB has no
M_Product table. All master product reads go through component_library.db (compConn).

**Column read patterns by purpose:**

| Purpose | Columns | Connection | Files |
|---------|---------|------------|-------|
| Identity | product_id, product_type, ifc_class, is_active | compConn | BOMWalker, OrderLineWalker, ProductRegistrar |
| Dimensions | width, depth, height (METRES, not mm) | compConn/bomConn | PlacementCollectorVisitor, MetadataValidator |
| 5D Cost | unit_cost_rm, currency_code, cost_source, cost_uom | compConn | CostDAO |
| 4D Schedule | construction_phase, construction_sequence, labor_resource, crew_size, productivity_rate | compConn | ScheduleDAO |
| 6D Sustainability | carbon_kg_per_unit, recyclability, eol_strategy | compConn | SustainabilityDAO |
| 7D Facility Mgmt | maintenance_schedule, warranty_period, replacement_cost | compConn | FacilityMgmtDAO |
| Geometry link | M_Product_ID (from M_Product_Image) | compConn | ProductGeometry, ComponentLibrary |

**Dead code alert:** ProductRegistrar.ensureProducts() still copies M_Product from
component_library.db to BOM DB, but BOMWalker was refactored (R7, S36) to read only
from compConn. The BOM DB copy is no longer read by production code.

---

## Task 2 — Java Files Reading component_definitions / component_geometries

**18 files** reference component_definitions or component_geometries. All read from
component_library.db exclusively.

| File | What It Reads | Also Reads M_Product? |
|------|--------------|----------------------|
| ComponentLibrary.java | component_definitions: geometry_hash, local bounds, attachment_face, vertex/face counts | No |
| DoorWindowLibraryMapper.java | component_definitions: name, geometry_hash, local bounds, forward_axis via JOIN component_types | No |
| StairLibraryMapper.java | component_definitions: geometry_hash, local bounds via JOIN component_types | No |
| StandardsResolver.java | component_definitions: hardcoded geometry_hash lookups (FP components) | No |
| ExtractionPopulator.java | component_geometries: geometry_hash, vertices, faces (write path) | No |
| ProductGeometry.java | M_Product_Image JOIN component_geometries ON geometry_hash | Yes — via M_Product_Image.M_Product_ID |
| MetadataValidator.java | I_Geometry_Map + component_geometries (referential integrity check) | Yes — checks M_Product dimensions |
| MeshBinder.java | component_geometries indirectly via ComponentLibrary.resolveByProduct() | No |
| MEPWriter.java | component_geometries indirectly via DoorWindowLibraryMapper | No |

**Key finding:** M_Product and component_definitions are accessed by **different code paths**.
Only ProductGeometry.java and MetadataValidator.java touch both — and they join through
M_Product_Image, not directly. This confirms the tables can live in separate databases
with zero SQL JOIN impact.

---

## Task 3 — M_Product to component_definitions Join Path

**The join is INDIRECT through M_Product_Image:**

```
M_Product.product_id
    → M_Product_Image.M_Product_ID  (name match)
    → M_Product_Image.geometry_hash
    → component_geometries.geometry_hash  (PK)
```

component_definitions is a **parallel path**, not part of the M_Product chain:

```
component_types.id
    → component_definitions.type_id  (FK)
    → component_definitions.geometry_hash
    → component_geometries.geometry_hash  (PK)
```

**No direct SQL JOIN between M_Product and component_definitions exists anywhere in
the codebase.** X_MProduct.java defines a `component_id` column (logical FK →
component_definitions) but it is **vestigial** — never populated or queried.

**Production resolution code (ProductGeometry.java:69-79):**
```java
SELECT mpi.M_Product_ID, mpi.geometry_hash, mpi.up_axis, mpi.forward_axis,
       mpi.attachment_face, cg.vertex_count, cg.face_count
FROM M_Product_Image mpi
JOIN component_geometries cg ON mpi.geometry_hash = cg.geometry_hash
```

**Implication for split:** M_Product can move to a different database without breaking
any JOIN. The runtime link (M_Product_Image → component_geometries) stays in
component_library.db alongside the geometry. M_Product_Image.M_Product_ID is a text
key resolved in Java, not a SQL FK.

---

## Task 4 — BOM Database M_Product Schema

**YES: BOM databases carry their own M_Product tables.** Schema differs from component_library.db.

| Aspect | BOM M_Product | Component Library M_Product |
|--------|---------------|-----------------------------|
| Columns | 29 | 27 |
| Rows | 7-348 (varies by building) | ~2,472 (master catalog) |
| Has clearance rules | YES (clear_front/back/left/right/above/below) | NO |
| Has fitting rules | YES (fits_in, requires_host, host_min_width/height) | NO |
| Has qty rules | YES (qty_per_area, qty_per_room, qty_per_person, max_spacing) | NO |
| Has ERP columns | NO | YES (unit_cost, labor_*, carbon_*, maintenance_*) |
| Has conn_points | YES | NO |
| Populated by | schema_snapshot_bom.sql (DDL only) | ProductRegistrar (IFCtoBOM pipeline) |

**Schema mismatch is significant.** BOM M_Product has placement/fitting rules (LEGO
connection semantics). Component Library M_Product has ERP/lifecycle columns (4D-7D).
These are two different concerns wearing the same table name.

**BOM M_Product is effectively unused by production code.** After R7 refactor (S36),
BOMWalker reads M_Product from compConn (component_library.db), not bomConn. The BOM
copy exists for backward compatibility of single-arg constructors (to be removed).

**m_bom_line → M_Product resolution (structural, not FK):**
1. `m_bom_line.child_product_id` → try as `m_bom.bom_id` → sub-assembly (recurse)
2. Else → `MProduct.get(compConn, childProductId)` → leaf product from component_library.db
3. Else → dangling reference (warn + skip)

---

## Task 5 — Discipline String Usage Audit

**All discipline identifiers are currently TEXT/String.** No AD_Org FK exists anywhere.

**9 discipline codes** (from enhanced_federation_GI.db, defined in Discipline.java enum):
ARC (35338 elements), FP (6884), REB (2660), ACMV (1621), CW (1431), STR (1429),
ELEC (1172), SP (979), LPG (209).

**Discipline columns across the schema:**

| Table.Column | DB | Type | Current Value | AD_Org candidate? |
|-------------|-----|------|---------------|-------------------|
| C_OrderLine.Discipline | compile DB | TEXT DEFAULT 'ARC' | String literal | YES — primary |
| component_types.discipline | component_library.db | TEXT NOT NULL | 'ELEC', 'ACMV', 'FP' | YES |
| m_bom.bom_category | BOM DB | TEXT | 'RF', 'STR', 'FP', 'MEP' | YES (proxy for discipline) |
| AD_Val_Rule.discipline | ERP.db | TEXT nullable | 'FPR', 'ELC', 'PLB' | YES |
| AD_Clash_Rule.discipline_a/b | ERP.db | TEXT NOT NULL | 'ELC' vs 'PLB' | YES |
| ad_ifc_class_map.discipline | ERP.db | TEXT | 'ARC','STR','FP','ELEC','ACMV' etc. | YES |
| ad_element_mep.discipline | ERP.db | TEXT | 'FP','ELEC','ACMV','CW','SP' | YES |
| bad_discipline_priority.higher/lower | component_library.db | TEXT NOT NULL | Priority pairs | YES |

**Java code patterns:**

| Pattern | Files | Current Type |
|---------|-------|-------------|
| `getDiscipline()` / `setDiscipline(String)` | X_C_OrderLine.java | String getter/setter |
| `Discipline.fromString(s)` | Discipline.java | Enum conversion |
| `disciplineStack.push(productCategory)` | PlacementCollectorVisitor.java | Deque\<String\> |
| `rs.getString("discipline")` | FederatedDBReader, MEPAD, OrderLineWalker | Raw string from DB |
| `rs.getString("bom_category")` | SustainabilityDAO, FacilityMgmtDAO | Proxy discipline |
| TypeDisciplineMapping (static) | TypeDisciplineMapping.java | EnumMap (in-memory, not DB) |

**W003_orderline_discipline.sql backfill logic:**
```sql
UPDATE C_OrderLine SET Discipline = CASE
    WHEN bom_category IN ('RF', 'STR', 'SL') THEN 'STR'
    WHEN bom_category = 'FP' THEN 'FPR'
    WHEN bom_category IN ('MEP', 'ELEC', 'PLB', 'ACMV') THEN bom_category
    ELSE 'ARC'
END;
```

**Inconsistency:** compliance rules use 'FPR'/'ELC'/'PLB' (3-char codes) while
everywhere else uses 'FP'/'ELEC'/'SP' (variable-length). AD_Org would unify this.

---

## Summary — Decision Matrix

| Criterion | Option A (Split) | Option B (Expand DV) | Option C (Fix guard) |
|-----------|-----------------|---------------------|---------------------|
| Code changes | ~14 prod + ~10 test files | ~14 + ~10 (same) | ~2 files |
| iDempiere alignment | **FULL** — M_Product with AD tables | FULL (same as A) | PARTIAL — mixed DB |
| Geometry isolation | **CLEAN** — 7 tables, pure LOD | CLEAN (same) | MIXED — 66+ tables |
| LOD chain breakage | **NONE** — M_Product_Image stays in geometry DB | NONE | N/A |
| Future maintainability | **HIGH** — clear concern boundaries | HIGH | LOW — grows worse |
| Discipline unification | **YES** — AD_Org eliminates 'FPR'/'FP' inconsistency | YES | NO |
| Risk | MED overall, **HIGH for Step 3** (M_Product move) | MED/HIGH (same) | LOW |

**Recommendation: Option A.** It's the same work as Option B but with a clearer name
(ERP.db is already the AD Dictionary in practice). The 6-step migration
is independently committable, each step gated by existing Rosetta Stone tests.
