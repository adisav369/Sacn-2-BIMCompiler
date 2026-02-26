# View Contracts — The Compiler's Data Access Layer

**Version:** 1.0  
**Date:** 2026-02-22  
**Status:** GOVERNING — defines the data contract between base AD tables and the compiler  
**Authors:** red1 (architect) + Claude Watchdog (reviewer)  
**Supplements:** PREFAB_ARCHITECTURE.md, METADATA_DRIVEN_ARCHITECTURE.md, AD_Events_Spatial_Rules.md  
**iDempiere analogues:** AD_Role permissions, DocStatus filter, MTable/MColumn validation, DB.executeQuery() access layer

---

> *"The base tables are the workshop. The views are the showroom.  
> The compiler only walks through the showroom."*

---

## 1. Why This Document Exists

### 1.1 The iDempiere Lineage

iDempiere never lets application code decide what data is valid. The Application Dictionary defines validity. The application reads only what the AD says is ready. This is why adding a new document type in iDempiere requires no Java — the AD already knows how to handle it.

The BIM compiler has accumulated the same knowledge encoded differently:
- Java resolver methods that check for null and throw exceptions
- MetadataMissingException as a runtime gate
- Silent fallbacks that mask missing data

All of these are symptoms of the same problem: **the compiler is deciding validity at runtime instead of the data layer deciding at query time.**

The view contract layer solves this permanently. A compiler query that returns zero rows is not an error — it means the data is not yet construction-ready. No exception. No fallback. No silent failure. The data simply does not exist in the compiler's world until it meets the contract.

### 1.2 The Three-Layer Stack

```
┌─────────────────────────────────────────────────────┐
│  COMPILER (Java)                                    │
│  Reads ONLY from v_verified_* views                 │
│  Never touches base ad_* tables directly            │
├─────────────────────────────────────────────────────┤
│  VIEW CONTRACTS (SQL Views)                         │  ← THIS DOCUMENT
│  Define what is construction-ready                  │
│  Filter: doc_status=CO, provenance≠PENDING,         │
│          dimensions>0, geometry extracted           │
├─────────────────────────────────────────────────────┤
│  BASE TABLES (ad_* SQLite)                          │
│  Workshop — partial data, migrations in progress    │
│  Protected by CHECK constraints                     │
│  Write path: migrations and extractions only        │
└─────────────────────────────────────────────────────┘
```

### 1.3 Relationship to Existing Documents

| Document | Layer | Relationship |
|---|---|---|
| PREFAB_ARCHITECTURE.md | Assembly structure | Defines WHAT assemblies exist and their dimensions |
| METADATA_DRIVEN_ARCHITECTURE.md | Passive AD tables | Defines the base table schemas |
| AD_Events_Spatial_Rules.md | Reactive layer | Defines callouts that fire when data changes |
| **VIEW_CONTRACTS.md** (this doc) | **Access layer** | **Defines what the compiler can see** |

The view layer sits between passive data and reactive events. It is the filter that makes incomplete data invisible.

---

## 2. Document Status Pattern — Borrowed from iDempiere

### 2.1 The DocStatus Contract

In iDempiere, `C_Invoice.DocStatus` determines what the accounting engine can see:
- `DR` (Draft) — being prepared, invisible to posting
- `CO` (Completed) — verified, visible to posting  
- `VO` (Voided) — superseded, invisible to everything

The BIM compiler adopts the same pattern for every AD row that feeds compilation:

```sql
-- doc_status applies at BOM/OrderLine level only
-- iDempiere principle: M_Product has no DocStatus —
-- a product exists or doesn't.
-- C_OrderLine/M_BOMLine has DocStatus —
-- this instance is Draft, Complete, or Voided.

-- M_BOM uses active flag only — not a document, no DocStatus
-- (doc_status belongs on C_ tables only)

-- M_BOMLine uses active flag only — not a document, no DocStatus
-- Activation controlled by: active = 1
-- (doc_status belongs on C_ tables only)

-- Placement instance — element rule readiness (C_OrderLine analogue)
ALTER TABLE ad_element_rule
ADD COLUMN doc_status TEXT NOT NULL DEFAULT 'DR'
    CHECK(doc_status IN ('DR', 'CO', 'VO'));

-- Room boundary — spatial placement instance readiness
ALTER TABLE ad_room_boundary
ADD COLUMN doc_status TEXT NOT NULL DEFAULT 'DR'
    CHECK(doc_status IN ('DR', 'CO', 'VO'));

-- Product-level tables use extracted_from provenance, NOT doc_status
-- A product fact either has proven extraction or it doesn't —
-- that is not a document state, it is a data quality flag.

ALTER TABLE component_definitions
ADD COLUMN extracted_from TEXT NOT NULL DEFAULT 'PENDING'
    CHECK(length(extracted_from) > 0);

ALTER TABLE ad_product_dim
ADD COLUMN extracted_from TEXT NOT NULL DEFAULT 'PENDING'
    CHECK(extracted_from != 'PENDING');
```

### 2.2 What CO Means in Construction Terms

A row is `CO` (Completed) when:

| Table | Level | CO/provenance condition |
|---|---|---|
| `M_BOM` | BOM header | active=1 only — no DocStatus |
| `M_BOMLine` | BOM line | active=1, min_space_mm declared — no DocStatus |
| `ad_element_rule` | Placement instance (C_OrderLine) | position values non-null, storey exists, family_ref resolves |
| `ad_room_boundary` | Spatial instance (C_OrderLine) | coordinates extracted from reference IFC, coordinate_frame declared |
| `component_definitions` | Product (M_Product) | extracted_from ≠ PENDING — no doc_status |
| `ad_product_dim` | Product attributes | extracted_from ≠ PENDING — no doc_status |

### 2.3 Terminal and Partial Extraction

Terminal (Rosetta Stone 3) is in FLAT mode — relational recompilation pending T1-T5. Its rows are legitimately `DR`. The view layer makes this honest rather than hiding it:

- Terminal FLAT-extracted elements: `doc_status = CO` (extracted, proven)
- Terminal relational rows pending T1-T5: `doc_status = DR` (in progress)
- The compiler sees only CO rows — Terminal compiles from what is proven

This is not a regression. It is honest compilation.

---

## 3. Core View Definitions

### 3.1 v_placeable_bom_child

The compiler's entry point for BOM expansion. A BOM child only appears here if the full chain — component, geometry, dimensions — is simultaneously verified.

```sql
CREATE VIEW v_placeable_bom_child AS
SELECT
    bc.bom_id,
    bc.child_ref,
    bc.role,
    bc.seq_no,
    bc.wall_rule,
    bc.opposite_wall,
    bc.child_name_pattern,
    pd.width_mm,
    pd.depth_mm,
    pd.height_mm,
    cd.id            AS component_id,
    cd.name          AS component_name,
    gm.geometry_hash,
    gm.extracted_from AS geometry_source
FROM ad_bom_child bc
-- Must be completed and active
JOIN ad_bom b ON b.bom_id = bc.bom_id
    AND b.doc_status = 'CO'
-- Component must exist by name pattern
JOIN component_definitions cd
    ON cd.name LIKE '%' || REPLACE(bc.child_name_pattern, '%', '') || '%'
    AND cd.extracted_from NOT LIKE '%PENDING%'
-- Geometry must be extracted not invented
JOIN ad_geometry_map gm
    ON gm.element_ref = cd.name
    AND gm.extracted_from NOT LIKE '%PENDING%'
-- Dimensions must be proven
JOIN ad_product_dim pd
    ON pd.product_id = bc.role
    AND pd.width_mm > 0
    AND pd.depth_mm > 0
    AND pd.height_mm > 0
    AND pd.extracted_from NOT LIKE '%PENDING%'
WHERE bc.active = 1
    AND bc.doc_status = 'CO';
```

**What this eliminates:**
- MetadataMissingException for missing geometry_map (child absent from view)
- dims=null fallback to 0.5×0.5×1.0m (child absent from view)
- % wildcard resolving to wrong component (JOIN filters to correct match)

### 3.1b BOM Layering and Spatial Qualification

The view handles multi-level BOM hierarchy naturally. A parent BOM (e.g. `SH_LIVING_FULLSET`) contains child BOMs (`DINING_SET`, `SOFA_SET`) as M_BOMLine entries. The spatial filter gates at the parent line level — if a child BOM set requires 3000mm and the room is 2800mm, the entire child BOM is excluded. The remaining child BOMs expand normally.

```
C_RoomBoundary: LIVING_ROOM, width=2800mm
  → AD_RoomSlot → M_BOM: SH_LIVING_FULLSET
      → M_BOMLine: DINING_SET  fit_priority=10, min_space_mm=2400 → FITS   → expands all children
      → M_BOMLine: SOFA_SET    fit_priority=20, min_space_mm=3000 → FILTERED OUT (2800 < 3000)
      → M_BOMLine: COFFEE_TABLE fit_priority=30, min_space_mm=0   → FITS   → expands all children
```

The same `DINING_SET` and `SOFA_SET` M_BOMs serve SH, DX, and TB-LKTN without redefinition.
TB-LKTN's smaller rooms simply qualify fewer parent lines. No new BOMs required.

**Two new columns on M_BOMLine:**

```sql
ALTER TABLE M_BOMLine
ADD COLUMN fit_priority INTEGER NOT NULL DEFAULT 20
    CHECK(fit_priority IN (10, 20, 30));
-- 10 = essential: always include if room type matches
-- 20 = standard:  include if min_space_mm satisfied
-- 30 = optional:  include only if space remaining after 10+20

ADD COLUMN min_space_mm INTEGER NOT NULL DEFAULT 0
    CHECK(min_space_mm >= 0);
-- Minimum room width OR depth required for this line
-- 0 = always fits (small items, accessories)
-- Checked against: MIN(room_width_mm, room_depth_mm)
```

**Seeded values from PREFAB_ARCHITECTURE.md room dimensions [RESEARCHED]:**

```sql
-- LIVING room sets
UPDATE M_BOMLine SET fit_priority=10, min_space_mm=2400
    WHERE role='DINING_SET';
UPDATE M_BOMLine SET fit_priority=20, min_space_mm=3000
    WHERE role='SOFA_SET';
UPDATE M_BOMLine SET fit_priority=30, min_space_mm=0
    WHERE role='COFFEE_TABLE';

-- BEDROOM sets
UPDATE M_BOMLine SET fit_priority=10, min_space_mm=1800
    WHERE role='BED_KING';
UPDATE M_BOMLine SET fit_priority=10, min_space_mm=1400
    WHERE role='BED_QUEEN';
UPDATE M_BOMLine SET fit_priority=20, min_space_mm=2400
    WHERE role='WARDROBE_PAIR';
UPDATE M_BOMLine SET fit_priority=20, min_space_mm=1200
    WHERE role='WARDROBE_SINGLE';
UPDATE M_BOMLine SET fit_priority=30, min_space_mm=0
    WHERE role='BEDSIDE_TABLE';
```

### 3.2 v_verified_room_boundary

Room boundaries with proven coordinate provenance. The living-room-9m-east drift cannot recur — unprovenanced boundaries are invisible.

```sql
CREATE VIEW v_verified_room_boundary AS
SELECT
    rb.room_id,
    rb.building_type,
    rb.room_type,
    rb.min_x_mm,
    rb.max_x_mm,
    rb.min_y_mm,
    rb.max_y_mm,
    rb.storey_id,
    rb.extracted_from,
    rb.coordinate_frame
FROM ad_room_boundary rb
WHERE rb.doc_status = 'CO'
    AND rb.extracted_from NOT LIKE '%PENDING%'
    AND rb.coordinate_frame IN ('IFC_GLOBAL_MM', 'LOCAL_MM', 'DRAWING_MM');
```

**What this eliminates:**
- Hand-generated grid coordinates (never reach CO unless extracted)
- Wrong coordinate frame (unknown frame rows invisible)
- The SH living-room-9m-east class of drift

### 3.3 v_compilable_element_rule

Element placement rules where every referenced entity exists and is verified.

```sql
CREATE VIEW v_compilable_element_rule AS
SELECT
    er.element_ref,
    er.ifc_class,
    er.family_ref,
    er.building_type,
    er.storey_id,
    er.position_rule,
    er.position_value_1,
    er.position_value_2,
    er.position_value_3,
    er.orientation,
    pd.width_mm,
    pd.depth_mm,
    pd.height_mm
FROM ad_element_rule er
-- Family ref must resolve to verified product
JOIN ad_product_dim pd
    ON pd.product_id = er.family_ref
    AND pd.width_mm > 0
-- Storey Z must be non-negative
WHERE er.position_value_3 >= 0
    AND er.doc_status = 'CO'
    AND er.family_ref IS NOT NULL;
```

### 3.4 v_proven_geometry

Components where geometry is extracted from a real reference, not generated as a fallback.

```sql
CREATE VIEW v_proven_geometry AS
SELECT
    cd.id,
    cd.name,
    cd.ifc_class,
    cd.component_type,
    gm.geometry_hash,
    cg.vertex_count,
    gm.extracted_from
FROM component_definitions cd
JOIN ad_geometry_map gm
    ON gm.element_ref = cd.name
JOIN component_geometries cg
    ON cg.geometry_hash = gm.geometry_hash
-- Real geometry only — BBox has exactly 8 vertices
WHERE cg.vertex_count > 8
    AND gm.extracted_from NOT LIKE '%PENDING%'
    AND cd.doc_status = 'CO';
```

**What this eliminates:**
- BBox placeholders reaching IFC output (vertex_count > 8 filter)
- The entire LodGeometryXRayTest X1-X4 class of detection (prevention replaces detection)

### 3.5 v_active_bom_assembly

Complete BOM assemblies where every level of the hierarchy is verified.

```sql
CREATE VIEW v_active_bom_assembly AS
SELECT
    b.bom_id,
    b.bom_name,
    b.bom_type,
    b.building_type,
    COUNT(bc.child_ref) AS child_count,
    SUM(CASE WHEN bc.doc_status = 'CO' THEN 1 ELSE 0 END) AS completed_children
FROM ad_bom b
JOIN ad_bom_child bc ON bc.bom_id = b.bom_id
WHERE b.doc_status = 'CO'
    AND b.active = 1
GROUP BY b.bom_id
-- Only assemblies where ALL children are completed
HAVING child_count = completed_children;
```

---

## 4. Java Refactoring — Compiler Access Layer

### 4.1 The Access Contract

Every compiler query must go through a typed access object that only queries views:

```java
/**
 * ViewAccessLayer — the compiler's only data access path.
 * Never queries base ad_* tables directly.
 * All methods return Optional — absent = not construction-ready.
 * No exceptions for missing data. Missing = not in view = not ready.
 */
public final class ViewAccessLayer {

    // ONLY query target — views, never base tables
    private static final String BOM_CHILDREN =
        "SELECT * FROM v_placeable_bom_child WHERE bom_id = ?";

    private static final String ROOM_BOUNDARY =
        "SELECT * FROM v_verified_room_boundary " +
        "WHERE building_type = ? AND room_type = ?";

    private static final String ELEMENT_RULE =
        "SELECT * FROM v_compilable_element_rule " +
        "WHERE element_ref = ? AND building_type = ?";

    private static final String PROVEN_GEOMETRY =
        "SELECT * FROM v_proven_geometry WHERE name LIKE ?";

    /**
     * Returns empty list if BOM not CO or children incomplete.
     * Caller does not need to handle missing data — 
     * empty list is the signal.
     */
    public List<PlaceableBomChild> getBomChildren(String bomId) {
        return query(BOM_CHILDREN, bomId, PlaceableBomChild::from);
    }

    public Optional<VerifiedRoomBoundary> getRoomBoundary(
            String buildingType, String roomType) {
        return queryOne(ROOM_BOUNDARY, buildingType, roomType,
            VerifiedRoomBoundary::from);
    }

    public Optional<ProvenGeometry> getGeometry(String namePattern) {
        return queryOne(PROVEN_GEOMETRY, namePattern,
            ProvenGeometry::from);
    }
}
```

### 4.2 What This Removes from Java

| Current Java | Replaced by |
|---|---|
| `MetadataMissingException` in hot paths | Empty Optional from view |
| `if (geometry == null) throw...` | `Optional.empty()` from v_proven_geometry |
| `dims == null` → 0.5×0.5×1.0m fallback | Child absent from v_placeable_bom_child |
| `getOrDefault(ref, null)` | Not needed — view only returns complete rows |
| `bindParametric()` BBox fallback | Not needed — incomplete geometry not in view |

The resolver classes become simpler:

```java
// BEFORE — defensive, exception-heavy
Geometry g = geometryMap.get(ref);
if (g == null) throw new MetadataMissingException(
    "No geometry for " + ref);
ProductDim dims = productDims.get(role);
if (dims == null) return bindParametric(bounds); // BBox fallback

// AFTER — view-backed, clean
Optional<ProvenGeometry> g = viewAccess.getGeometry(ref);
if (g.isEmpty()) return; // Not construction-ready — skip cleanly
// dims are already in the view row — always present if geometry is
```

### 4.3 ArchUnit Gate

```java
@Test void compilerOnlyReadsViews() {
    noClasses()
        .that().resideInAPackage("..compiler..")
        .should().callMethodWhere(
            target().hasNameMatching(".*ad_bom_child.*") 
            .or(target().hasNameMatching(".*ad_element_rule.*"))
            .or(target().hasNameMatching(".*ad_room_boundary.*"))
            .or(target().hasNameMatching(".*component_definitions.*"))
        )
        .because("Compiler must query views only via ViewAccessLayer. " +
                 "Direct base table access bypasses construction-ready filter.");
}
```

---

## 5. MColumn-Style Validation — Self-Describing Schema

Borrowed from iDempiere's `AD_Column` — every column declares its own validation rule. MetadataIntegrityTest reads this table rather than having hardcoded per-column assertions.

```sql
CREATE TABLE ad_column_contract (
    table_name      TEXT NOT NULL,
    column_name     TEXT NOT NULL,
    ref_type        TEXT NOT NULL CHECK(ref_type IN (
                        'FOREIGN_KEY',    -- references another table
                        'VOCABULARY',     -- closed set of values
                        'POSITIVE_NUMBER',-- must be > 0
                        'COORDINATE_MM',  -- valid coordinate range
                        'PROVENANCE',     -- must not be PENDING
                        'ROTATION_DEG',   -- 0/90/180/270 only
                        'DOC_STATUS'      -- DR/CO/VO only
                    )),
    ref_target      TEXT,   -- for FOREIGN_KEY: target table.column
                            -- for VOCABULARY: CSV of valid values
                            -- for COORDINATE_MM: MIN,MAX
    is_mandatory    INTEGER NOT NULL DEFAULT 1 CHECK(is_mandatory IN (0,1)),
    provenance      TEXT NOT NULL,
    PRIMARY KEY (table_name, column_name)
);

-- Self-registering examples
INSERT INTO ad_column_contract VALUES
('ad_bom_child', 'seq_no', 'POSITIVE_NUMBER', NULL, 1,
 'RESEARCHED: iDempiere BOM sequence pattern'),
('ad_bom_child', 'doc_status', 'VOCABULARY', 'DR,CO,VO', 1,
 'RESEARCHED: iDempiere DocStatus pattern'),
('ad_bom_child_param', 'rotation_deg', 'VOCABULARY', '0,90,180,270', 1,
 'RESEARCHED: IFC orthogonal rotation constraint'),
('ad_product_dim', 'width_mm', 'POSITIVE_NUMBER', NULL, 1,
 'RESEARCHED: physical dimension must be positive'),
('ad_room_boundary', 'coordinate_frame', 'VOCABULARY',
 'IFC_GLOBAL_MM,LOCAL_MM,DRAWING_MM', 1,
 'RESEARCHED: coordinate frame must be declared'),
('ad_room_boundary', 'extracted_from', 'PROVENANCE', NULL, 1,
 'PRIME RULE: all coordinates must have extraction source');
```

MetadataIntegrityTest then becomes:

```java
@Test void allColumnContractsEnforced() {
    // Read ad_column_contract
    // For each row: query the named table.column
    // Apply the ref_type validation rule
    // Fail with: "{table}.{column}: {value} violates {ref_type} contract"
    // Zero hardcoded column names — the data drives the test
}
```

---

## 6. View Maintenance Rules

**Rule 1 — Views are read-only to the compiler.**
No INSERT, UPDATE, or DELETE via views. The compiler is a reader. Migrations are writers.

**Rule 2 — A view filter change is an architecture change.**
Any modification to view WHERE clauses must be reviewed by the watchdog. Loosening a filter is equivalent to allowing incomplete data into compilation.

**Rule 3 — New tables need views before compiler use.**
When a new AD table is created (e.g., ad_spatial_rule), its corresponding view (v_active_spatial_rule) must be defined before any compiler code references the table.

**Rule 4 — SpatialDigest stability proves view correctness.**
After redirecting any compiler query from base table to view, SpatialDigest for all four buildings must be identical. If a digest changes, the view is filtering differently from the base table query it replaced. Find the delta before proceeding.

---

## 7. Implementation Sequence

| Phase | Action | Prerequisite | Unlocks |
|---|---|---|---|
| 1 | Add doc_status to C_PlacementOrder, C_RoomBoundary only | MetadataIntegrityTest green | DocStatus filter in views |
| 1a | Add active=1 default to M_BOM, M_BOMLine (already exists — verify) | Phase 1 | Active filter in views |
| 1b | Add extracted_from to component_definitions, ad_product_dim | Phase 1 | Product provenance enforced |
| 2 | Seed CO for all current verified BOM/OrderLine rows | Phase 1 | Views return correct data |
| 3 | Create 5 core views | Phase 2 | Compiler can be redirected |
| 4 | Create ViewAccessLayer.java | Phase 3 | Compiler reads views |
| 5 | Redirect resolvers to ViewAccessLayer | Phase 4 | MetadataMissingException eliminated |
| 6 | Add ArchUnit gate | Phase 5 | Direct base table access blocked |
| 7 | Create ad_column_contract + self-describing tests | Phase 6 | Schema is self-validating |

**Each phase: verify SpatialDigests identical before proceeding to next.**

---

## 8. Watchdog Constraints

- A view that returns zero rows is correct behaviour — it means data is not CO. It is never a bug to fix by loosening the view filter.
- The `doc_status = CO` filter is the strongest gate. Never add `OR doc_status = 'DR'` to any view. That defeats the entire pattern.
- Views must never contain subqueries that write data or call procedures. Pure SELECT only.
- Performance: all JOIN columns (bom_id, element_ref, product_id, geometry_hash) must have indexes on base tables. View performance depends entirely on base table indexes.
- The iDempiere codebase is the Rosetta Stone for this layer. When in doubt about a pattern, consult how iDempiere implements the equivalent mechanism.

---

*BIM Intent Compiler | View Contracts — Compiler Data Access Layer | v1.0 | February 2026*

*"The base tables are the workshop. The views are the showroom. The compiler only walks through the showroom."*
