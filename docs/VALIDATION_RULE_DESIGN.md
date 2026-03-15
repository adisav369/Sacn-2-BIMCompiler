# Construction Validation Rules — AD_Val_Rule Pattern

*How iDempiere's validation architecture applies to BIM placement compliance and clash detection*

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

*References: DISC_BOM_DESIGN.md (discipline BOM structure), ConstructionAsERP.md §11 (C_Order model),
TestArchitecture.md (ProveStage gates), tools/cross_discipline_checker.py (existing checker)*
