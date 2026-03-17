# DocValidate — Construction Validation Engine

**Version:** 1.1 (2026-03-18)
**Depends on:** [ConstructionAsERP.md](ConstructionAsERP.md), [BIM_Designer.md](BIM_Designer.md) §4/§9, [DISC_BOM_DESIGN.md](DISC_BOM_DESIGN.md)

*iDempiere's `IDocActionInterceptor` / `ModelValidator` architecture applied to
BIM placement compliance, clash detection, and building code enforcement.*

> **Governing principle:** Validation rules are external to the BOM and external
> to the compiled output. They do not own data — they validate data created by
> others. Like a tax table that applies rates to order lines, or a credit limit
> that constrains order totals, construction rules validate placements without
> being embedded in the placement data itself.

> **RosettaStone caveat:** Extracted buildings replicate the original engineer's
> design verbatim. Validation rules are NOT applied to extracted RosettaStones —
> the original engineer may not have resolved all compliance during design.
> Applying rules would break extraction fidelity. Rules apply when generating
> **variants** from proven placement behaviour — after the pattern is stable.

> **Generative prerequisite:** The generative path (Provenance='GENERATIVE',
> e.g. TB-LKTN affordable housing) requires DocValidate to ensure the compiler
> does not produce non-compliant geometry. Without validation rules, a generative
> building could place a 2.5m bedroom where UBBL requires minimum 3.0m — the
> compiler would happily compile it. DocValidate is the gate that prevents this.

### iDempiere DocValidate Mapping

In iDempiere, `DocValidate` is the event model for document lifecycle
validation. Three interfaces work together:

| iDempiere Interface | Fires when | BIM Compiler Equivalent |
|---------------------|-----------|------------------------|
| `ModelValidator.modelChange()` | PO beforeSave/afterSave | `MBOMLine.beforeSave()` — child fits parent AABB |
| `IDocActionInterceptor.docValidate()` | DocAction state change (DR→IP→CO) | `ProveStage` (Stage 9) — all proofs pass before CO |
| `AD_Val_Rule` (SQL lookup) | Field value constrained | `AD_Val_Rule` table — compliance params by jurisdiction |

The engine separation: **AD_Val_Rule** holds the rules (data). **ModelValidator**
fires the checks (engine). **DocAction** gates the lifecycle (process). Same
three concerns, same separation as iDempiere.

---

## 1. iDempiere Validation Patterns — What They Teach

### 1.1 Three Validation Layers in iDempiere

| Layer | iDempiere | Trigger | BIM Equivalent |
|-------|-----------|---------|----------------|
| **Field-level** | Column Callout (JSR223/Beanshell) | On field change | Placement parameter check (offset within range) |
| **Record-level** | ModelValidator (beforeSave/afterSave) | On PO save | BOM line validation (child fits in parent AABB) |
| **Document-level** | DocAction (prepareIt/completeIt) | On doc status change | Building compilation gate (all placements valid before CO) |

### 1.2 AD_Val_Rule — The SQL Validation Pattern

In iDempiere, `AD_Val_Rule` defines SQL-based validation rules that constrain
what values are valid for a field. The rule is **external** to the data — it
lives in the Application Dictionary, not in the transaction table.

```
AD_Val_Rule:  "C_Tax for Product Category"
  Code:       C_Tax_ID IN (SELECT t.C_Tax_ID FROM C_Tax t
              WHERE t.C_TaxCategory_ID = @C_TaxCategory_ID@
              AND t.ValidFrom <= @DateOrdered@)

The order line doesn't know tax rates.
The tax rule knows which rate applies to which product category.
The engine joins them at validation time.
```

**BIM mapping:** A placement doesn't know fire code spacing rules. The
validation rule knows which spacing applies to which product type in which
occupancy classification. The engine joins them at validation time.

### 1.3 ModelValidator — beforeSave / afterSave Hooks

iDempiere's `ModelValidator` interface fires on PO lifecycle events. Validators
are registered externally (plugin/OSGI) — the PO class doesn't know about them.

```java
// iDempiere pattern
public class FireCodeValidator implements ModelValidator {
    public String modelChange(PO po, int type) {
        if (type == TYPE_BEFORE_NEW && po instanceof X_M_BOMLine) {
            // Check placement against fire code rules
            // Return error message string to block save, or null to allow
        }
    }
}
```

**BIM mapping:** Our `ValidateBOM` (beforeSave on X_M_BOMLine) already uses this
pattern — it warns when child AABB exceeds parent. Construction validation rules
extend this: check spacing, clearance, material clash.

### 1.4 DocAction — Document Lifecycle Gates

iDempiere's document processing (prepareIt → completeIt → closeIt → voidIt)
validates the entire document before state transitions. A Sales Order can't
complete if credit is exceeded. A Manufacturing Order can't complete if BOM
components are unavailable.

**BIM mapping:** Our `ProveStage` (Stage 9) is exactly this — the building
can't reach `CO` (Completed) status until all placement proofs pass. Validation
rules would fire during `prepareIt` — before compilation commits to output.db.

---

## 2. Construction Validation Types

### 2.1 Compliance Validation (AD_Val_Rule pattern)

Rules from building codes and standards. External lookup — the rule table is
maintained by code officials, not by designers.

| Rule Domain | Standard | What It Validates | Example |
|-------------|----------|-------------------|---------|
| Fire sprinkler spacing | NFPA 13 / SS CP 52 | Head-to-head distance, wall offset, coverage area | max 4.6m spacing (Light Hazard) |
| Electrical clearance | NEC / SS CP 5 | Working space around panels, conductor fill | 900mm clearance in front of panel |
| Plumbing fixture spacing | IPC / SS CP 48 | Min distance between fixtures, trap arm length | max 1.5m trap arm for 38mm waste |
| Structural cover | ACI 318 / SS CP 65 | Rebar cover depth, splice length | min 40mm cover for interior beam |
| Fire rating | IBC / SS CP 24 | Wall/slab fire rating vs occupancy | 2-hour fire wall between dwelling units |
| Accessibility | ADA / BCA | Door width, turning radius, mounting heights | min 900mm clear door opening |
| Ventilation | ASHRAE 62.1 / SS CP 13 | Air changes per hour, duct velocity | min 0.3 L/s/m² outdoor air |

### 2.2 Clash Detection (ModelValidator pattern)

Spatial intersection rules. No element of one discipline may occupy space
claimed by another without explicit allowance.

| Clash Type | What Clashes | Resolution |
|------------|-------------|------------|
| **Hard clash** | Conduit through structural beam | Reroute conduit or add beam penetration sleeve |
| **Soft clash** | Pipe within 150mm of ductwork | Acceptable if insulated, reject if bare |
| **Material clash** | MEP penetration through fire-rated wall | Require fire stop / intumescent collar |
| **Clearance clash** | Valve handle obstructed by ceiling | Lower pipe or use different valve type |
| **Workflow clash** | Ceiling installed before above-ceiling MEP | Sequence constraint, not spatial |

**Key insight:** Clash rules are **conditional**, not absolute. A conduit CAN
pass through a wall — but only if the wall isn't fire-rated, or if a fire stop
is provided. The rule has **conditions and exceptions**, just like tax rules
have exemptions.

```
AD_Clash_Rule:  "MEP through Fire Wall"
  Discipline_A:  MEP (any)
  Discipline_B:  ARC (IfcWall where fire_rating IS NOT NULL)
  Verdict:       BLOCK unless fire_stop_product_id IS NOT NULL
  Resolution:    INSERT fire_stop product at intersection point
```

### 2.3 Clearance Validation (combined pattern)

Minimum distance between elements of different types. Neither compliance
(code-driven) nor clash (intersection) — a third category.

| Clearance Rule | Between | Min Distance | Standard |
|----------------|---------|-------------|----------|
| Electrical from plumbing | ELC conduit ↔ PLB pipe | 150mm | NEC 300.4 |
| Hot from cold water | PLB hot ↔ PLB cold | 150mm (insulated) | IPC |
| Sprinkler from obstruction | FPR head ↔ any beam/duct | 3× obstruction rule | NFPA 13 |
| Service access | any valve/panel ↔ wall/ceiling | per device spec | Manufacturer |

---

## 3. Validation Rule Database — The Fourth DB

Current architecture: 3 databases, 3 concerns.

| DB | Concern | Analogy |
|----|---------|---------|
| component_library.db | Product images (meshes, materials) | File server |
| `{PREFIX}_BOM.db` | Assembly recipes (what to build) | Product master |
| output.db | Compiled result (where things go) | Transaction |
| **validation.db** | **Rules (what's allowed)** | **Tax table / AD_Val_Rule** |

The validation DB is the fourth concern: **what's allowed**. It doesn't create
placements, it constrains them. Maintained separately from the BOM catalog and
the compiled output. Updated when codes change (annual code cycles), without
touching the BOM or recompiling existing buildings.

### 3.1 Proposed Tables

```sql
-- Compliance rules (sprinkler spacing, electrical clearance, etc.)
CREATE TABLE AD_Val_Rule (
    ad_val_rule_id    INTEGER PRIMARY KEY,
    name              TEXT NOT NULL,           -- 'NFPA13_LH_SPACING'
    description       TEXT,
    rule_type         TEXT NOT NULL,           -- 'COMPLIANCE', 'CLASH', 'CLEARANCE'
    discipline        TEXT,                    -- 'FPR', 'ELC', 'PLB', NULL=any
    standard_ref      TEXT,                    -- 'NFPA 13 §8.6.2.2.1'
    jurisdiction      TEXT,                    -- 'US', 'SG', 'MY', 'INTL'
    valid_from        TEXT,                    -- code edition date
    valid_to          TEXT,                    -- NULL = current
    is_active         INTEGER DEFAULT 1
);

-- Rule parameters (like C_Tax has rate, threshold, etc.)
CREATE TABLE AD_Val_Rule_Param (
    ad_val_rule_param_id  INTEGER PRIMARY KEY,
    ad_val_rule_id        INTEGER NOT NULL REFERENCES AD_Val_Rule,
    name                  TEXT NOT NULL,       -- 'max_spacing_mm', 'min_clearance_mm'
    value                 TEXT NOT NULL,       -- '4600', '150'
    value_type            TEXT DEFAULT 'NUM',  -- 'NUM', 'TEXT', 'BOOL'
    condition_expr        TEXT                 -- 'occupancy_class = LH' (optional)
);

-- Clash rules (which discipline pairs interact)
CREATE TABLE AD_Clash_Rule (
    ad_clash_rule_id  INTEGER PRIMARY KEY,
    discipline_a      TEXT NOT NULL,           -- 'ELC'
    discipline_b      TEXT NOT NULL,           -- 'PLB'
    element_filter_a  TEXT,                    -- 'ifc_class = IfcFlowSegment'
    element_filter_b  TEXT,                    -- 'fire_rating IS NOT NULL'
    clash_type        TEXT NOT NULL,           -- 'HARD', 'SOFT', 'MATERIAL', 'CLEARANCE'
    min_distance_mm   REAL,                    -- NULL for hard clash (=intersection)
    verdict           TEXT NOT NULL,           -- 'BLOCK', 'WARN', 'ALLOW_IF'
    resolution_note   TEXT,                    -- 'Add fire stop at penetration'
    ad_val_rule_id    INTEGER REFERENCES AD_Val_Rule  -- parent rule reference
);

-- Occupancy classification (drives which rules apply)
CREATE TABLE AD_Occupancy_Class (
    ad_occupancy_class_id  INTEGER PRIMARY KEY,
    code                   TEXT NOT NULL,       -- 'LH' (Light Hazard), 'OH1', 'OH2'
    name                   TEXT NOT NULL,       -- 'Light Hazard Occupancy'
    standard_ref           TEXT,                -- 'NFPA 13 §5.2'
    description            TEXT
);

-- Links rules to occupancy classes (many-to-many)
CREATE TABLE AD_Val_Rule_Occupancy (
    ad_val_rule_id         INTEGER NOT NULL REFERENCES AD_Val_Rule,
    ad_occupancy_class_id  INTEGER NOT NULL REFERENCES AD_Occupancy_Class,
    PRIMARY KEY (ad_val_rule_id, ad_occupancy_class_id)
);

-- Validation results (per instance, per rule — the audit trail)
CREATE TABLE AD_Validation_Result (
    ad_validation_result_id INTEGER PRIMARY KEY,
    c_orderline_id          INTEGER NOT NULL,      -- which placed instance
    ad_val_rule_id          INTEGER NOT NULL,      -- which rule checked
    rule_valid_from         TEXT,                   -- code edition snapshot
    result                  TEXT NOT NULL,         -- 'PASS', 'WARN', 'BLOCK'
    actual_value            REAL,                  -- measured (e.g., 2800mm)
    required_value          REAL,                  -- rule minimum (3000mm)
    message                 TEXT,                  -- human-readable verdict
    resolved_by_product_id  TEXT,                  -- if ALLOW_IF, which product fixed it
    created_at              TEXT DEFAULT (datetime('now'))
);

-- Documented deviations (engineer-approved exceptions)
CREATE TABLE AD_Val_Rule_Exception (
    ad_val_rule_exception_id INTEGER PRIMARY KEY,
    building_id              TEXT NOT NULL,         -- which building (e.g., 'Duplex_A_01')
    ad_val_rule_id           INTEGER NOT NULL,      -- which rule violated
    element_ref              TEXT,                  -- specific element(s) affected
    count                    INTEGER,               -- number of occurrences
    approved_by              TEXT,                  -- engineer who accepted deviation
    reason                   TEXT,                  -- why deviation accepted
    created_at               TEXT DEFAULT (datetime('now'))
);
```

### 3.2 Validation Engine — The Tax Calculation Analogy

```
iDempiere Tax Calculation:
  C_OrderLine (product, qty, price)
    → lookup C_TaxCategory (from M_Product)
    → lookup C_Tax (from C_TaxCategory + jurisdiction + date)
    → apply rate → tax amount

BIM Placement Validation:
  m_bom_line (product, position, parent_bom)
    → lookup discipline (from parent m_bom.bom_category)
    → lookup AD_Val_Rule (from discipline + occupancy_class + jurisdiction)
    → apply rule params → PASS / WARN / BLOCK

BIM Clash Detection:
  element_A (discipline_a, position_a, bbox_a)
  element_B (discipline_b, position_b, bbox_b)
    → lookup AD_Clash_Rule (from discipline_a × discipline_b)
    → spatial intersection test (bbox overlap or min_distance)
    → apply verdict → PASS / WARN / BLOCK with resolution
```

### 3.3 When Validation Fires

| Event | iDempiere Analogy | What Happens |
|-------|-------------------|-------------|
| BOM line created (generative) | ModelValidator.beforeSave | Check placement against AD_Val_Rule for discipline |
| BOM complete | DocAction.prepareIt | Run all applicable rules, collect violations |
| Cross-discipline check | Credit check on order | Run AD_Clash_Rule for all discipline pairs |
| Building compile | DocAction.completeIt | ProveStage includes validation gate |

**NOT fired for extracted RosettaStones.** Extraction copies the original
engineer's design. The engineer may have unresolved violations (DX has known
MEP corner issues — P23, 364 instances). Validation fires only during:
1. Generative variant creation (new placement from rules)
2. Manual BOM editing (user moves a sprinkler head)
3. Explicit validation request ("check this floor for compliance")

### 3.4 Integration with BomValidator and Project Context

**BomValidator integration:** PlacementValidator extends BomValidator, not
replaces it. Existing 9 checks + verb fidelity run first (structural integrity).
AD_Val_Rule checks run second (regulatory compliance). First violation blocks —
same as iDempiere ModelValidator returning the first error string.

**Project context:** Jurisdiction lives on C_Order header (the building project
record), following the iDempiere pattern where C_Order carries C_Country_ID:

```sql
-- Extension to c_order in output.db
ALTER TABLE c_order ADD COLUMN jurisdiction TEXT;     -- 'MY', 'US', 'UK', 'AU', 'SG'
ALTER TABLE c_order ADD COLUMN code_edition TEXT;     -- '2012', '2021'
```

When `Provenance='EXTRACTED'`, jurisdiction is NULL → validation skipped.
When `Provenance='GENERATIVE'`, jurisdiction must be set → validation fires.

**Performance:** Clash detection uses the existing `elements_rtree` spatial
index in output.db (already created by `BuildingWriter.initSchema()`). R-tree
query first narrows candidates, then AD_Clash_Rule checks apply to the
filtered set. Discipline pair filtering (AD_Clash_Rule defines which pairs)
+ storey partitioning keeps Terminal-scale checks tractable.

---

## 4. Clash Detection — Cross-Discipline Spatial Rules

### 4.1 The Conduit-Through-Expensive-Material Problem

A conduit running through a structural beam is a hard clash. A conduit through
a fire-rated wall requires a fire stop. A conduit through a standard partition
is allowed (with a grommet). The rule isn't "conduit can't penetrate" — it's
"conduit penetration has conditions."

```
AD_Clash_Rule examples:

Rule 1: ELC conduit through STR beam
  discipline_a = ELC, discipline_b = STR
  element_filter_b = ifc_class IN ('IfcBeam', 'IfcColumn')
  clash_type = HARD
  verdict = BLOCK
  resolution = 'Reroute conduit or coordinate beam penetration with structural engineer'

Rule 2: MEP pipe through ARC fire-rated wall
  discipline_a = MEP (any), discipline_b = ARC
  element_filter_b = fire_rating IS NOT NULL
  clash_type = MATERIAL
  verdict = ALLOW_IF
  condition = fire_stop_product_id IS NOT NULL
  resolution = 'Insert intumescent fire collar at penetration point'

Rule 3: PLB pipe near ELC conduit
  discipline_a = PLB, discipline_b = ELC
  clash_type = CLEARANCE
  min_distance_mm = 150
  verdict = WARN
  resolution = 'Maintain 150mm separation per NEC 300.4'

Rule 4: FPR sprinkler head near ACMV duct
  discipline_a = FPR, element_filter_a = ifc_class = 'IfcFireSuppressionTerminal'
  discipline_b = ACMV, element_filter_b = ifc_class IN ('IfcDuctSegment', 'IfcDuctFitting')
  clash_type = CLEARANCE
  min_distance_mm = 0  -- uses 3x obstruction rule, not simple distance
  verdict = BLOCK
  resolution = 'Reposition head per NFPA 13 obstruction rules'
```

### 4.2 Existing Cross-Discipline Checker

`tools/cross_discipline_checker.py` already exists in the codebase. It checks
spatial relationships between disciplines in output.db. The validation rule DB
would formalize what that script checks ad-hoc into declarative rules.

### 4.3 Resolution Products

When a clash verdict is `ALLOW_IF`, the resolution often requires inserting a
product (fire stop, sleeve, grommet). These are M_Product entries in `{PREFIX}_BOM.db` —
real products with real geometry. The validation engine doesn't just flag the
clash; it prescribes the fix as a BOM modification:

```
Clash detected: PLB waste pipe through ARC fire-rated wall
  → AD_Clash_Rule says: ALLOW_IF fire_stop
  → Resolution: INSERT m_bom_line (
      parent = PLB discipline BOM,
      child_product_id = 'FIRE_COLLAR_100MM',
      dx/dy/dz = intersection point,
      qty = 1
    )
```

This is the iDempiere pattern where a business rule doesn't just validate — it
can trigger a compensating action (like auto-creating a tax line on an invoice).

---

## 5. Jurisdiction and Code Editions

Construction codes vary by country and edition year. The same building type
in Singapore (SS CP 52) and the US (NFPA 13) has different sprinkler spacing
rules. The validation DB handles this the same way iDempiere handles
multi-country tax:

| iDempiere | BIM Validation |
|-----------|---------------|
| C_Tax.C_Country_ID | AD_Val_Rule.jurisdiction |
| C_Tax.ValidFrom | AD_Val_Rule.valid_from (code edition) |
| C_Tax.C_TaxCategory_ID | AD_Val_Rule.discipline |
| C_Tax.Rate | AD_Val_Rule_Param.value |

A building project declares its jurisdiction and applicable code edition.
The validation engine selects the matching rule set. When codes update
(annual cycle), new AD_Val_Rule rows are added with updated valid_from —
existing buildings validated against their original code remain valid.

---

## 6. Relationship to Discipline BOM Design

This paper complements `DISC_BOM_DESIGN.md`:

| Concern | Paper | What It Defines |
|---------|-------|----------------|
| BOM organization | DISC_BOM_DESIGN.md | How disciplines structure the BOM tree |
| Placement validation | This paper | How rules constrain what the BOM can contain |

The discipline BOM structure (§2 of DISC_BOM_DESIGN.md) enables validation:
clash detection is inherently cross-discipline — you need discipline BOMs to
know which elements belong to which trade. Without discipline separation, clash
detection is a flat scan of all elements against all elements (O(n²)). With
discipline BOMs, it's a targeted scan of discipline pairs (ARC×MEP, STR×ELC,
etc.) — the AD_Clash_Rule table defines which pairs to check.

---

## 7. Rule Mining from Terminal — The Non-Disturbance Principle

### 7.1 The Terminal as Rule Oracle

The Terminal (51,088 elements, 9 disciplines, laid out by real engineers) is the
source of truth for deriving validation rules. The methodology:

1. **Mine** actual spatial relationships from Terminal's extracted data:
   - Measure sprinkler head spacing (FPR: 909 heads → detect grid regularity)
   - Measure conduit-to-pipe clearances (ELC×PLB: pairwise minimum distances)
   - Detect penetration patterns (MEP through ARC/STR: intersection points)
   - Measure duct-to-sprinkler clearances (ACMV×FPR)
   - Catalogue material boundaries (fire-rated walls vs MEP penetrations)

2. **Encode** measured patterns as AD_Val_Rule + AD_Clash_Rule entries:
   - Observed spacing → `max_spacing_mm` parameter
   - Observed clearances → `min_distance_mm` on AD_Clash_Rule
   - Observed penetration types → clash verdicts (ALLOW_IF vs BLOCK)

3. **Validate** the Terminal against its own derived rules — **it MUST pass**.
   If any rule reports a violation against the Terminal, the rule is wrong,
   not the building. The engineer's design is the ground truth.

### 7.2 Non-Disturbance Test

```
Terminal (properly laid out, visually proven)
  → apply derived AD_Val_Rule set
  → expected: 0 violations (rules describe reality)
  → if violations > 0: the RULE is incorrectly encoded, fix the rule

DX (properly laid out, visually proven, known P23 corner issues)
  → apply derived AD_Val_Rule set
  → expected: 0 violations + known exceptions (P23 MEP corners = 364)
  → known exceptions are documented, not suppressed
```

**The building is the ground truth.** Rules must describe reality, not prescribe
it. Only after rules pass against extracted buildings do we trust them to
constrain generative ones. This is the same principle as the Rosetta Stone
compilation test: the stone teaches us, we encode, we prove by round-trip.

### 7.3 Known Exceptions (from extraction)

Real buildings have intentional or unresolved code deviations:

| Building | Known Exception | Count | Treatment |
|----------|----------------|-------|-----------|
| DX | P23 MEP corner clearance violations | 364 | Document as AD_Val_Rule_Exception |
| DX | "Room not enclosed" warnings | 21 | Architectural tolerance, not MEP |
| Terminal | IfcReinforcingBar GIC issues | 8 | Structural rebar, separate concern |

These exceptions are recorded in `AD_Val_Rule_Exception` (not shown in §3.1
schema — future extension). They don't suppress the rule; they document that
the original engineer accepted the deviation. Generative variants would need
explicit exception approval to deviate the same way.

### 7.4 Rule Mining Queries (Terminal)

Concrete SQL queries against Terminal extracted data to seed rules:

```sql
-- Sprinkler head spacing (FPR discipline)
-- Mine: what is the actual spacing between adjacent heads?
SELECT a.element_ref, b.element_ref,
       SQRT(POW(a.min_x - b.min_x, 2) + POW(a.min_y - b.min_y, 2)) as dist_mm
FROM I_Element_Extraction a, I_Element_Extraction b
WHERE a.building_type = 'SJTII_Terminal'
  AND b.building_type = 'SJTII_Terminal'
  AND a.ifc_class = 'IfcFireSuppressionTerminal'
  AND b.ifc_class = 'IfcFireSuppressionTerminal'
  AND a.storey = b.storey
  AND a.placement_id < b.placement_id
  AND SQRT(POW(a.min_x - b.min_x, 2) + POW(a.min_y - b.min_y, 2)) < 6000
ORDER BY dist_mm;
-- Expected: cluster around code-compliant spacing (e.g., 3600-4600mm)

-- Conduit-to-pipe clearance (ELC × PLB)
-- Mine: what is the minimum separation between electrical and plumbing?
SELECT MIN(CASE
    WHEN a.max_x < b.min_x THEN b.min_x - a.max_x
    WHEN b.max_x < a.min_x THEN a.min_x - b.max_x
    ELSE 0 END) as clearance_x_mm
FROM I_Element_Extraction a, I_Element_Extraction b
WHERE a.building_type = 'SJTII_Terminal'
  AND b.building_type = 'SJTII_Terminal'
  AND a.discipline = 'ELEC'
  AND b.discipline = 'SP'
  AND a.storey = b.storey;
-- Expected: >= 150mm (NEC 300.4 minimum)
```

These queries are the starting point for Phase 1 rule seeding. Run them,
observe the distributions, encode the boundaries as AD_Val_Rule parameters.

---

## 8. Implementation Sequence

### Phase 1: Mine Terminal + Seed validation.db
1. Create `validation.db` with schema from §3.1
2. Run rule mining queries (§7.4) against Terminal extracted data
3. Encode measured patterns as AD_Val_Rule + AD_Val_Rule_Param rows
4. Seed AD_Clash_Rule with basic hard clashes (MEP through STR)
5. No code changes — data only

### Phase 2: Non-Disturbance Proof
1. Run validation rules against Terminal — expect 0 violations
2. Run validation rules against DX — expect 0 + known exceptions
3. If violations found, fix the rule (not the building)
4. Gate: Non-Disturbance Test PASS for Terminal + DX

### Phase 3: Validation Engine (ModelValidator pattern)
1. `PlacementValidator.java` — reads AD_Val_Rule, checks m_bom_line placements
2. Fires during generative BOM creation (beforeSave on m_bom_line)
3. Returns PASS / WARN / BLOCK with rule reference
4. Does NOT fire for extracted data (unless explicitly requested)

### Phase 4: Clash Detection Engine
1. `ClashDetector.java` — reads AD_Clash_Rule, checks spatial intersections
2. Operates on output.db element pairs across disciplines
3. Replaces/extends `cross_discipline_checker.py`
4. Produces clash report with resolution prescriptions

### Phase 5: Integration with ProveStage
1. Add validation gate to Stage 9 (ProveStage)
2. Generative buildings must pass validation before CO status
3. Extracted buildings skip validation (fidelity preserved)
4. Non-Disturbance Test as regression gate

### Future: GUI Integration
1. Bonsai Editor highlights clashes in real-time
2. Resolution suggestions from AD_Clash_Rule.resolution_note
3. One-click fix: insert prescribed resolution product

---

## 9. Residential Compliance Rules — UBBL Seed Data

### 9.1 Why This Is a Generative Prerequisite

The generative path (`Provenance='GENERATIVE'`) creates buildings from intent,
not from reference IFC. Without validation rules, the compiler will place
anything that fits the AABB — including rooms below code minimum dimensions.

**TB-LKTN** (affordable terrace house, Phase 27 DSL) is the first generative
test case. Its rooms must comply with UBBL 1984/2012 minimum requirements.
The validation rules below are seeded from UBBL and applied during generative
compilation only.

### 9.2 UBBL Residential Room Minimums

Source: UBBL 2012 (Uniform Building By-Laws, Malaysia), Table 5.1 and §33.

```sql
-- Seed: Malaysian residential room minimums
INSERT INTO AD_Val_Rule (ad_val_rule_id, name, rule_type, discipline,
    standard_ref, jurisdiction, is_active)
VALUES
(101, 'UBBL_BEDROOM_MIN_AREA',      'COMPLIANCE', 'ARC', 'UBBL 2012 s33(1)', 'MY', 1),
(102, 'UBBL_BEDROOM_MIN_DIM',       'COMPLIANCE', 'ARC', 'UBBL 2012 s33(1)', 'MY', 1),
(103, 'UBBL_KITCHEN_MIN_AREA',      'COMPLIANCE', 'ARC', 'UBBL 2012 s33(2)', 'MY', 1),
(104, 'UBBL_KITCHEN_MIN_DIM',       'COMPLIANCE', 'ARC', 'UBBL 2012 s33(2)', 'MY', 1),
(105, 'UBBL_BATHROOM_MIN_AREA',     'COMPLIANCE', 'ARC', 'UBBL 2012 s33(3)', 'MY', 1),
(106, 'UBBL_LIVING_MIN_AREA',       'COMPLIANCE', 'ARC', 'UBBL 2012 s33(4)', 'MY', 1),
(107, 'UBBL_CEILING_MIN_HEIGHT',    'COMPLIANCE', 'ARC', 'UBBL 2012 s36',    'MY', 1),
(108, 'UBBL_CORRIDOR_MIN_WIDTH',    'COMPLIANCE', 'ARC', 'UBBL 2012 s40',    'MY', 1),
(109, 'UBBL_DOOR_MIN_WIDTH',        'COMPLIANCE', 'ARC', 'UBBL 2012 s41',    'MY', 1),
(110, 'UBBL_WINDOW_MIN_AREA_RATIO', 'COMPLIANCE', 'ARC', 'UBBL 2012 s39',    'MY', 1);

-- Seed: Rule parameters
INSERT INTO AD_Val_Rule_Param (ad_val_rule_param_id, ad_val_rule_id, name, value, value_type)
VALUES
-- Bedroom: min 9.2m², min dimension 3.0m
(1011, 101, 'min_area_m2',     '9.2',  'NUM'),
(1012, 101, 'bom_category',    'BEDROOM', 'TEXT'),
(1021, 102, 'min_dim_mm',      '3000', 'NUM'),
(1022, 102, 'bom_category',    'BEDROOM', 'TEXT'),
-- Kitchen: min 4.5m², min dimension 1.5m
(1031, 103, 'min_area_m2',     '4.5',  'NUM'),
(1032, 103, 'bom_category',    'KITCHEN', 'TEXT'),
(1041, 104, 'min_dim_mm',      '1500', 'NUM'),
(1042, 104, 'bom_category',    'KITCHEN', 'TEXT'),
-- Bathroom: min 1.5m²
(1051, 105, 'min_area_m2',     '1.5',  'NUM'),
(1052, 105, 'bom_category',    'BATHROOM', 'TEXT'),
-- Living: min 12.0m²
(1061, 106, 'min_area_m2',     '12.0', 'NUM'),
(1062, 106, 'bom_category',    'LIVING', 'TEXT'),
-- Ceiling height: min 2600mm
(1071, 107, 'min_height_mm',   '2600', 'NUM'),
-- Corridor: min 900mm width
(1081, 108, 'min_width_mm',    '900',  'NUM'),
(1082, 108, 'bom_category',    'CORRIDOR', 'TEXT'),
-- Door: min 750mm clear opening
(1091, 109, 'min_width_mm',    '750',  'NUM'),
-- Window: min 10% of floor area
(1101, 110, 'min_ratio',       '0.10', 'NUM');
```

### 9.3 TB-LKTN Compliance Check

Cross-referencing TB-LKTN room dimensions against UBBL rules:

| Room | Bounds | Area | Min Area | Min Dim | Min Dim Actual | Verdict |
|------|--------|------|----------|---------|----------------|---------|
| bilik_utama (master) | A2-C3 | 13.64m² | 9.2m² | 3000mm | 3100mm | PASS |
| bilik_2 | D2-E3 | 9.61m² | 9.2m² | 3000mm | 3100mm | PASS |
| bilik_3 | D3-E5 | 9.61m² | 9.2m² | 3000mm | 3100mm | PASS |
| common (LI+DN+KT) | B2-D5 | 42.16m² | 12.0m² | — | 6200mm | PASS |
| bilik_mandi | A3-B4 | 1.95m² | 1.5m² | — | 1300mm | PASS |
| tandas | A4-B5 | 2.08m² | 1.5m² | — | 1300mm | PASS |

**Total: 87.56m²** — within UBBL affordable housing limits.

This table is what the validation engine produces at compile time. Each row
is a `AD_Val_Rule` check with rule_id, parameter lookup, and PASS/BLOCK verdict.
The witness file records the full check set as a machine-readable compliance
certificate (BIM_Designer.md §4.3).

### 9.4 Validation Engine Pseudo-Code

```java
// PlacementValidator — fires during generative BOM creation
// Pattern: iDempiere ModelValidator.modelChange(TYPE_BEFORE_NEW)

public String validateBomLine(MBOMLine line, Connection valConn) {
    // 1. Determine applicable rules
    String category = line.getParentBom().getBomCategory();  // BEDROOM, KITCHEN, etc.
    String jurisdiction = line.getParentBom().getJurisdiction();  // MY, US, SG

    List<ValRule> rules = ValRuleDAO.findByCategory(valConn, category, jurisdiction);

    // 2. Check each rule
    for (ValRule rule : rules) {
        switch (rule.paramName()) {
            case "min_area_m2" -> {
                double area = line.getAllocatedWidthMm() * line.getAllocatedDepthMm() / 1_000_000.0;
                if (area < rule.numValue())
                    return String.format("BLOCK: %s area %.1fm² < minimum %.1fm² [%s]",
                        category, area, rule.numValue(), rule.standardRef());
            }
            case "min_dim_mm" -> {
                int minDim = Math.min(line.getAllocatedWidthMm(), line.getAllocatedDepthMm());
                if (minDim < rule.intValue())
                    return String.format("BLOCK: %s min dimension %dmm < minimum %dmm [%s]",
                        category, minDim, rule.intValue(), rule.standardRef());
            }
            case "min_height_mm" -> {
                if (line.getAllocatedHeightMm() < rule.intValue())
                    return String.format("BLOCK: ceiling height %dmm < minimum %dmm [%s]",
                        line.getAllocatedHeightMm(), rule.intValue(), rule.standardRef());
            }
        }
    }
    return null;  // PASS — no violations
}
```

**Key:** returns `null` = PASS (iDempiere convention). Returns error string = BLOCK.
Same pattern as `MBOMLine.beforeSave()` — the PO doesn't save if validation returns
a non-null message.

### 9.5 Container Constraint Integration (BIM_Designer.md §9)

DocValidate works with the container constraint system:

```
User creates BEDROOM 2800×2800mm (generative)
  → MBOMLine.beforeSave() fires
  → PlacementValidator checks AD_Val_Rule 102: min_dim_mm = 3000
  → BLOCK: "BEDROOM min dimension 2800mm < minimum 3000mm [UBBL 2012 s33(1)]"
  → BOM line rejected — compiler never sees it

User creates BEDROOM 3100×3100mm
  → PlacementValidator: PASS (3100 >= 3000)
  → Container constraint (§9): child 3100 <= parent floor width? PASS
  → BOM line saved → compiler places it
```

**The GUI (BIM_Designer.md §4.4) uses the same rules to set slider ranges:**
- Lower bound = AD_Val_Rule minimum (3000mm for bedroom)
- Upper bound = parent AABB (container maximum)
- The user never sees an illegal dimension

---

## 10. Generative House Prerequisites — What Must Exist Before "Create New"

The BonsaiBIMDesigner "Create New" button needs these prerequisites before it
can produce a compliant generative building:

| Prerequisite | Status | Where |
|-------------|--------|-------|
| AD_Val_Rule schema + UBBL seed data | **Planned** | validation.db (§3.1 + §9.2) |
| PlacementValidator (ModelValidator pattern) | **Planned** | §9.4 pseudo-code |
| M_Product catalog (doors, windows, fixtures) | **Partial** — assembly stubs exist | component_library.db |
| TB-LKTN DSL template (generative reference) | **DONE** | phase27-tb-lktn/DSL_DICTIONARY.md |
| C_DocType entry (Provenance='GENERATIVE') | **DONE** — ST_SH, ST_DX exist | {PREFIX}_BOM.db |
| BonsaiBIMDesigner server + API | **DONE** — 14/14 GREEN | BonsaiBIMDesigner/ (§11 of BIM_Designer.md) |
| BomValidator (9 checks + verb fidelity) | **DONE** | IFCtoBOM/BomValidator.java |
| Container constraints (child <= parent) | **Designed** | BIM_Designer.md §9 |
| Pattern multiplication (spacing rules) | **Designed** | BIM_Designer.md §10 |

### 10.1 Where Validation Writes — ASI and C_OrderLine, Never Library or BOM Templates

**Critical architectural rule:** Validation rules affect instances, not templates.
This follows the iDempiere pattern exactly:

| Layer | What it is | Validation touches? | Example |
|-------|-----------|--------------------:|---------|
| `component_library.db` | Product catalog (M_Product images) | **NEVER** | A door's geometry doesn't change because of UBBL |
| `m_bom` / `m_bom_line` | BOM templates (recipes) | **NEVER** | A BEDROOM_3100 template stays 3100 regardless of jurisdiction |
| `M_AttributeSetInstance` | Per-instance overrides | **YES** | ASI records that this bedroom was validated against UBBL s33(1) |
| `C_OrderLine` | Construction order instances | **YES** | OrderLine gets validation status, rule_ref, jurisdiction |

In iDempiere: `AD_Val_Rule` constrains what values are valid on a `C_OrderLine`
field. It never modifies `M_Product` or `M_BOM`. The tax rate applies to the
invoice line, not to the product master. Same here: UBBL minimum room size
applies to the placed instance (C_OrderLine + ASI), not to the room template
(m_bom).

**The OSGi activation analogy:** Validation rules activate like iDempiere OSGi
components — they register as `ModelValidator` plugins and fire on events.
When a jurisdiction is selected (e.g., MY for Malaysia), the UBBL rule set
activates. When deactivated, the same building compiles without validation.
The rules are external to the pipeline, not embedded in it.

```
Pipeline WITHOUT DocValidate (current):
  BOM → CompilationPipeline → output.db
  (no compliance checks — anything that fits AABB compiles)

Pipeline WITH DocValidate (activated):
  BOM → ModelValidator.beforeSave (AD_Val_Rule lookup) → CompilationPipeline → output.db
  (non-compliant rooms rejected at BOM creation, never reach pipeline)

Same pipeline. Same BOM templates. Same library.
Only the validation gate is added — like starting an OSGi bundle.
```

**Minimum viable generative flow:**
1. User clicks "Create New" → dialog: building type, jurisdiction, rooms
2. Server reads AD_Val_Rule for jurisdiction → sets dimension bounds
3. User adjusts room sizes (slider constrained by validation rules)
4. Server generates BOM (BUILDING→FLOOR→ROOM→LEAF)
5. PlacementValidator checks each BOM line against AD_Val_Rule
6. CompilationPipeline.run() → output.db
7. Bonsai reloads viewport

---

---

## 11. World Construction Standards — AD_Val_Rule Seed Data

### 11.1 The Multi-Jurisdiction Model

Like iDempiere's multi-country tax engine (C_Tax × C_Country × ValidFrom),
the validation engine supports jurisdiction stacking. A building project
declares its jurisdiction; the engine selects the matching AD_Val_Rule set.

**Activation is per-project, not global.** A Malaysian building checks UBBL.
A US building checks IRC. A UK building checks Building Regulations. Same
engine, different data — like starting different OSGi bundles.

### 11.2 Residential Room Minimums — Cross-Jurisdiction Comparison

Compiled from world standards research. Each row becomes AD_Val_Rule +
AD_Val_Rule_Param entries in validation.db.

**Room Area Minimums (m²):**

| Room | MY (UBBL 2012) | US (IRC 2021) | UK (NDSS 2015) | AU (NCC 2022) | SG (BCA) | EU (varies) |
|------|---------------|---------------|----------------|---------------|----------|-------------|
| Habitable room (any) | — | 6.5 (70 sq ft) | — | — | — | 7-9 (varies) |
| Single bedroom | 9.2 | 6.5 | 7.5 | — | — | 7 (FR), 9 (IT) |
| Double bedroom | 9.2 | 6.5 | 11.5 | — | — | 9-14 (varies) |
| Living room | 12.0 | 6.5 | — | — | — | — |
| Kitchen | 4.5 | exempt | — | — | — | — |
| Bathroom | 1.5 | — | — | — | — | — |

**Minimum Dimensions (mm):**

| Parameter | MY (UBBL) | US (IRC) | UK (NDSS) | AU (NCC) | SG (BCA) |
|-----------|-----------|----------|-----------|----------|----------|
| Bedroom min dim | 3000 | 2134 (7 ft) | 2150 | — | — |
| Ceiling height (habitable) | 2600 | 2134 (7 ft) | 2300 | 2400 | 2400 |
| Ceiling height (bathroom) | — | 2032 (6'8") | — | 2100 | — |
| Corridor width | 900 | 914 (3 ft) | 900 | 1000 | 1200 |
| Door clear opening | 750 | 813 (32") | 750 | 820 | 850 |

**Fire and Safety:**

| Parameter | MY (UBBL) | US (IBC/IRC) | UK (Part B) | AU (NCC) |
|-----------|-----------|-------------|-------------|----------|
| Sprinkler spacing (LH) | per SS CP 52 | 4600mm (NFPA 13) | BS 9251 | AS 2118.1 |
| Fire wall between units | 2-hour | 1-hour (IRC) | 1-hour (Part B) | FRL 60/60/60 |
| Egress door width | 850mm | 813mm (32") | 850mm | 850mm |
| Secondary stair (height) | — | — | 18m (2024 amend) | — |

### 11.3 AD_Val_Rule Seed — Multi-Jurisdiction

```sql
-- US IRC 2021 residential rules
INSERT INTO AD_Val_Rule VALUES
(201, 'IRC_HABITABLE_MIN_AREA',  'COMPLIANCE', 'ARC', 'IRC 2021 R304.1', 'US', 1),
(202, 'IRC_HABITABLE_MIN_DIM',   'COMPLIANCE', 'ARC', 'IRC 2021 R304.2', 'US', 1),
(203, 'IRC_CEILING_MIN_HEIGHT',  'COMPLIANCE', 'ARC', 'IRC 2021 R305.1', 'US', 1),
(204, 'IRC_BATH_CEILING_HEIGHT', 'COMPLIANCE', 'ARC', 'IRC 2021 R305.1', 'US', 1),
(205, 'IRC_DOOR_MIN_WIDTH',      'COMPLIANCE', 'ARC', 'IRC 2021 R311.2', 'US', 1);

INSERT INTO AD_Val_Rule_Param VALUES
(2011, 201, 'min_area_m2',  '6.5',  'NUM'),   -- 70 sq ft
(2021, 202, 'min_dim_mm',   '2134', 'NUM'),   -- 7 ft
(2031, 203, 'min_height_mm','2134', 'NUM'),   -- 7 ft
(2041, 204, 'min_height_mm','2032', 'NUM'),   -- 6'8"
(2042, 204, 'bom_category', 'BATHROOM', 'TEXT'),
(2051, 205, 'min_width_mm', '813',  'NUM');   -- 32"

-- UK NDSS 2015 / Building Regulations
INSERT INTO AD_Val_Rule VALUES
(301, 'UK_SINGLE_BED_MIN_AREA', 'COMPLIANCE', 'ARC', 'NDSS 2015', 'UK', 1),
(302, 'UK_DOUBLE_BED_MIN_AREA', 'COMPLIANCE', 'ARC', 'NDSS 2015', 'UK', 1),
(303, 'UK_SINGLE_BED_MIN_DIM',  'COMPLIANCE', 'ARC', 'NDSS 2015', 'UK', 1),
(304, 'UK_CEILING_MIN_HEIGHT',  'COMPLIANCE', 'ARC', 'UK Regs',   'UK', 1);

INSERT INTO AD_Val_Rule_Param VALUES
(3011, 301, 'min_area_m2',  '7.5',  'NUM'),
(3012, 301, 'bom_category', 'BEDROOM', 'TEXT'),
(3013, 301, 'bed_type',     'SINGLE', 'TEXT'),
(3021, 302, 'min_area_m2',  '11.5', 'NUM'),
(3022, 302, 'bom_category', 'BEDROOM', 'TEXT'),
(3023, 302, 'bed_type',     'DOUBLE', 'TEXT'),
(3031, 303, 'min_dim_mm',   '2150', 'NUM'),
(3041, 304, 'min_height_mm','2300', 'NUM');

-- Australia NCC 2022
INSERT INTO AD_Val_Rule VALUES
(401, 'AU_CEILING_HABITABLE',   'COMPLIANCE', 'ARC', 'NCC 2022 F5/10.3', 'AU', 1),
(402, 'AU_CEILING_SERVICE',     'COMPLIANCE', 'ARC', 'NCC 2022 F5/10.3', 'AU', 1),
(403, 'AU_DOOR_MIN_WIDTH',      'COMPLIANCE', 'ARC', 'NCC 2022',         'AU', 1),
(404, 'AU_CORRIDOR_MIN_WIDTH',  'COMPLIANCE', 'ARC', 'NCC 2022',         'AU', 1);

INSERT INTO AD_Val_Rule_Param VALUES
(4011, 401, 'min_height_mm','2400', 'NUM'),
(4021, 402, 'min_height_mm','2100', 'NUM'),
(4022, 402, 'bom_category', 'BATHROOM,KITCHEN,LAUNDRY,CORRIDOR', 'TEXT'),
(4031, 403, 'min_width_mm', '820',  'NUM'),
(4041, 404, 'min_width_mm', '1000', 'NUM');

-- Singapore BCA
INSERT INTO AD_Val_Rule VALUES
(501, 'SG_CEILING_MIN_HEIGHT',  'COMPLIANCE', 'ARC', 'BCA Approved Document', 'SG', 1),
(502, 'SG_CORRIDOR_MIN_WIDTH',  'COMPLIANCE', 'ARC', 'BCA Approved Document', 'SG', 1),
(503, 'SG_DOOR_MIN_WIDTH',      'COMPLIANCE', 'ARC', 'BCA Approved Document', 'SG', 1);

INSERT INTO AD_Val_Rule_Param VALUES
(5011, 501, 'min_height_mm','2400', 'NUM'),
(5021, 502, 'min_width_mm', '1200', 'NUM'),
(5031, 503, 'min_width_mm', '850',  'NUM');
```

### 11.4 How Jurisdiction Activation Works

```
DocValidate activation — iDempiere OSGi analogy:

iDempiere:
  ComponentFactory.start(TaxValidator.class)
    → registers ModelValidator for C_InvoiceLine
    → fires on every invoice line save
    → checks C_Tax × C_Country × ValidFrom
    → blocks if tax rate missing or invalid

BIM Compiler:
  DocValidate.activate("MY")                    // jurisdiction = Malaysia
    → registers PlacementValidator for MBOMLine
    → fires on every generative BOM line save
    → checks AD_Val_Rule WHERE jurisdiction = 'MY'
    → BLOCK if room dimension < UBBL minimum

  DocValidate.activate("US")                    // jurisdiction = USA
    → same validator, different rule set
    → checks AD_Val_Rule WHERE jurisdiction = 'US'
    → BLOCK if habitable room < 70 sq ft (IRC R304.1)

  DocValidate.deactivate()                      // extracted buildings
    → validator unregistered
    → BOM lines save without compliance checks
    → same as iDempiere running without tax plugin
```

**The rule data drives behaviour, not the code.** Adding a new jurisdiction =
SQL INSERTs into AD_Val_Rule + AD_Val_Rule_Param. No Java change. No
recompile. Same engine, different parameters — the iDempiere way.

### 11.5 Impact on C_OrderLine and ASI — Never Library or BOM Templates

When DocValidate fires, it writes validation results to the **instance** layer:

```sql
-- On C_OrderLine (the construction order instance)
UPDATE c_orderline SET
    validation_status = 'PASS',             -- or 'BLOCK'
    validation_rule_ref = 'UBBL 2012 s33(1)',
    validation_jurisdiction = 'MY',
    validated_at = datetime('now')
WHERE C_OrderLine_ID = ?;

-- On M_AttributeSetInstance (per-instance override)
INSERT INTO M_AttributeSetInstance (name, value) VALUES
    ('validated_min_area_m2', '9.61'),       -- actual area
    ('validated_min_dim_mm', '3100'),         -- actual min dimension
    ('validation_result', 'PASS'),
    ('validation_code_ref', 'UBBL 2012 s33(1)');
```

**What is NEVER modified:**
- `component_library.db` — product geometry is physics, not regulation
- `m_bom` templates — a BEDROOM_3100 template is a recipe, regulation-neutral
- `m_bom_line` templates — tack offsets are spatial, not jurisdictional

The templates are universal. The validation is per-instance.

---

*References:
[DISC_BOM_DESIGN.md](DISC_BOM_DESIGN.md) (discipline BOM structure) |
[ConstructionAsERP.md](ConstructionAsERP.md) §11 (C_Order model) |
[BIM_Designer.md](BIM_Designer.md) §4 (compliance as compilation constraint), §9 (container rules), §11 (BonsaiBIMDesigner) |
[TestArchitecture.md](TestArchitecture.md) (ProveStage gates) |
[phase27-tb-lktn/DSL_DICTIONARY.md](phase27-tb-lktn/DSL_DICTIONARY.md) (TB-LKTN generative reference) |
tools/cross_discipline_checker.py (existing checker)*

Sources:
- [IRC Minimum Room Sizes - Building Code Trainer](https://buildingcodetrainer.com/minimum-bedroom-size/)
- [Minimum Room Sizes and Ceiling Heights - EVstudio](https://evstudio.com/minimum-room-sizes-and-minimum-ceiling-heights/)
- [Minimum Dimensions in IRC - Fine Homebuilding](https://www.finehomebuilding.com/2024/01/10/minimum-dimensions-in-the-irc)
- [UK Minimum Room Sizes - Design for Me](https://designfor-me.com/project-types/conversions/minimum-room-sizes-for-a-house/)
- [UK Minimum Room Size - Designing Buildings](https://www.designingbuildings.co.uk/wiki/Minimum_room_size)
- [Australia NCC Room Heights](https://ncc.abcb.gov.au/editions/ncc-2022/adopted/volume-one/f-health-and-amenity/part-f5-room-heights)
- [Australia Common House Room Sizes](https://www.thirdistudio.com.au/What_Are_Common_House_Room_Sizes.html)
- [Singapore BCA Codes](https://www1.bca.gov.sg/about-us/news-and-publications/publications-reports/codes-acts-and-regulations)
- [European Housing Spaces Comparison (MDPI)](https://www.mdpi.com/1660-4601/18/8/4278)
