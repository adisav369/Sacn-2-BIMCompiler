# DocValidate — Construction Validation Engine
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md)

<div class="bim-banner" markdown>
<b>Spatial rules from real buildings + regulatory rules from building codes.</b> Together they validate every placement. Rules are external to the BOM — swap jurisdiction without touching products, swap products without touching rules.
</div>

**Version:** 1.1 (2026-03-18)
**Depends on:** [MANIFESTO.md](MANIFESTO.md), [BIM_Designer.md](BIM_Designer.md) §4/§9, [DISC_VALIDATION_DB_SRS.md](DISC_VALIDATION_DB_SRS.md)

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

<!-- Implementing SpecsAnalysis.txt §14 — spatial + regulatory symbiosis -->

## 0. Two Kinds of Rules — Why This Changes Everything

### Two Rule Databases That Compose at Compile Time

The industry separates spatial knowledge (engineer's head) from regulatory knowledge (code books), reconciled manually at enormous cost. This compiler maintains **two kinds of rules** in symbiosis:

| Rule Source | Origin | Stored In | Role | Three Concerns |
|-------------|--------|-----------|------|----------------|
| **Spatial rules** | Mined from 35 real buildings | `ad_space_type`, `ad_element_mep`, `ad_space_dim`, `placement_rules` in [ERP.db](DISC_VALIDATION_DB_SRS.md) | **Propose** placements | **WHERE** it goes |
| **Regulatory rules** | From standards bodies (UBBL, NFPA 13, IRC, BCA) | `AD_Val_Rule` with jurisdiction scope + `pack_id` + `valid_from/to` | **Validate** placements | **HOW** it's allowed |

**Spatial rules propose. Regulatory rules validate.** One compiler, two
knowledge bases. The compiler compiles freely (WHAT), spatial rules guide
WHERE, regulatory rules gate HOW — the [Three Concerns](MANIFESTO.md#the-three-concerns)
in action.

### iDempiere Parallel — Configure-to-Order Applied to Compliance

Same pattern as iDempiere C_Tax: spatial rules propose (like BOM Configurator), regulatory rules constrain by jurisdiction (like C_Tax by territory). Exception-based — a placement is valid unless a rule says otherwise.

**Same building, different jurisdictions:** Switch `AD_Val_Rule WHERE jurisdiction = 'MY'` to `'AU'` or `'US'`. Same spatial knowledge, different regulatory pack — like switching tax jurisdictions in iDempiere.

### How They Interact

```
EXTRACTION (IFC → BOM)
  ↓
SPATIAL RULES (mined from 35 buildings)
  ↓
COMPILER PROPOSES PLACEMENT (using spatial knowledge)
  ↓
REGULATORY RULES (from standards bodies, jurisdiction-scoped)
  "NFPA 13 requires sprinkler coverage ≥ 3000mm for Light Hazard"
  "UBBL 2012 s33(1) requires bedroom ≥ 9.2m²"
  "NCC 2022 requires ceiling height ≥ 2400mm"
  ↓
VALIDATOR GATES PLACEMENT (pass / warn / block)
  ↓
RESULT: Compliant building, provably correct for its jurisdiction
```

→ [MANIFESTO.md §Why This Matters](MANIFESTO.md#why-this-matters) · [ProjectOrderBlueprint.md §13](ProjectOrderBlueprint.md#13-rule-driven-discipline--validation-as-ordering) · [§7 Rule Mining](#7-rule-mining-from-terminal--the-non-disturbance-principle) · [§11 World Standards](#11-world-construction-standards--ad_val_rule-seed-data)

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

### 1.5 Column.Callout — BIM Spatial Callout Wiring

In iDempiere, `CalloutOrder.amt()` is a generic callout that fires on ANY order
line change and calculates totals. It doesn't know specific products — tax rules
(`C_Tax`) provide the specifics as data. The callout is the **engine** (generic,
code); the rules are the **data** (AD_Rule rows, ASI values).

**BIM mapping:** A generic spatial callout fires on ANY C_OrderLine placement or
dimension change. It reads per-instance ASI for verb overrides and queries AD_Rule
for matching spatial conditions. New verb associations = SQL INSERT, not code change.

#### 1.5.1 Where the User Works — The OrderLine

The user never touches M_Product, M_AttributeSet, or AD_Rule. Those are
catalog and engine concerns — set up once by the implementor.

The user works on **C_OrderLine** — the order line. When they place a wall,
edit its position, or customise its behaviour, they edit the order line.
The ASI attributes appear as editable fields on the order line because the
product declared which attributes apply (via M_AttributeSet_ID).

```
Catalog (set up once, never touched by user):
  M_Product (Wall_EXT_150)
    └─ M_AttributeSet_ID = 'BIM_Wall'     ← declares vocabulary
         └─ trim_action, trim_tolerance_mm, joint_type ...

User workspace (edited per instance):
  C_OrderLine #37 (this specific wall on grid A-1)
    └─ M_AttributeSetInstance_ID → ASI #47
         └─ trim_action = CUT_FILL         ← user's choice
         └─ trim_tolerance_mm = 25          ← user's override
```

**Having M_AttributeSet_ID on the product does NOT mean the verb will fire.**
It means the attribute is AVAILABLE for the user to set. The callout still
detects the spatial condition (is there a roof above?). If no roof, nothing
happens regardless of the attribute.

| ASI value | Callout behaviour |
|-----------|------------------|
| No ASI at all | Callout decides from AD_Rule defaults |
| `trim_action = DEFAULT` | Callout decides (same as no ASI) |
| `trim_action = SKIP` | Callout skips — user said no |
| `trim_action = CUT_ONLY` | Force CUT, skip FILL |
| `trim_action = CUT_FILL` | Force both CUT and FILL |

The product declares the vocabulary. The order line declares the intent.
The callout does the work.

#### 1.5.2 Dispatch Chain

```
C_OrderLine field change (dx, dy, dz, M_Product_ID)
  │
  ├─ 1. CalloutEngine queries AD_Rule WHERE source_column = changed_field
  │     AND is_active = 1, ordered by depends_on (Kahn topo-sort)
  │
  ├─ 2. For each matching rule, read ASI from C_OrderLine.M_AttributeSetInstance_ID
  │     → M_AttributeInstance name/value pairs (trim_action, tolerance, etc.)
  │
  ├─ 3. ASI overrides compose with AD_Rule.rule_type to select domain callout:
  │     POSITIONAL  → CalloutSPATIAL  (AABB recalc, parent cascade)
  │     DIMENSIONAL → CalloutTRIM     (trim verb family: detect→filter→decide→act)
  │     CONSTRAINT  → CalloutJOIN     (joint/connection type enforcement)
  │     REROUTE     → CalloutMEP      (MEP path rerouting)
  │
  └─ 4. Domain callout orchestrates its verb family via VerbRegistry
```

#### 1.5.3 How AD_Rule + ASI Compose

The key separation: AD_Rule defines WHEN a callout fires and WHAT type it is.
ASI on the C_OrderLine defines HOW the verb executes for this specific instance.

| Layer | Source | Example |
|-------|--------|---------|
| **When** | AD_Rule.source_column | `dx` changes on C_OrderLine |
| **What** | AD_Rule.rule_type | `DIMENSIONAL` → trim family |
| **How** | M_AttributeInstance via ASI | `trim_action=CUT_FILL`, `trim_tolerance_mm=25` |
| **Who** | M_Product.M_AttributeSet_ID | `BIM_Wall` → defines which attributes are valid |

#### 1.5.4 Resolution Order

```
1. AD_Rule match:     source_table='C_OrderLine' AND source_column=<changed>
2. Product filter:    M_Product.M_Product_Category constrains which rules apply
3. ASI resolution:    effective = ASI_override ?? DefaultValue (from M_Attribute)
4. Verb dispatch:     VerbRegistry.resolve(rule_type, ASI params)
```

This mirrors iDempiere exactly: `CalloutOrder.amt()` doesn't know tax rates —
it queries `C_Tax` by product category and date. Our `CalloutEngine` doesn't
know trim tolerances — it queries ASI by attribute set and instance.

#### 1.5.5 Extending with Data, Not Code

Adding a new callout rule (e.g., "when a duct moves, reroute connected pipes"):

```sql
INSERT INTO AD_Rule (ad_rule_id, name, event_type, source_table, source_column,
    rule_type, expression, seq_no)
VALUES (10, 'DUCT_REROUTE_DX', 'FIELD_CHANGE', 'C_OrderLine', 'dx',
    'REROUTE', 'REROUTE_CONNECTED', 20);
```

Adding a per-instance override (e.g., "this wall skips trim"):

```sql
-- Create ASI for the wall instance
INSERT INTO M_AttributeSetInstance (M_AttributeSet_ID, Description)
VALUES ('BIM_Wall', 'Wall A-1: skip trim');

INSERT INTO M_AttributeInstance (M_AttributeSetInstance_ID, Name, Value, ValueType)
VALUES (last_insert_rowid(), 'trim_action', 'SKIP', 'TEXT');

-- Link to order line
UPDATE c_orderline SET M_AttributeSetInstance_ID = <asi_id>
WHERE locator_ref = 'SH/GF/LIVING/WALL_EXT_A1';
```

No Java change. The callout engine discovers the SKIP override at dispatch time.

*Cross-references: BBC.md §3.5.2 (Product → Verb Routing), BIM_Designer_SRS.md §31
(ASI Attribute Chain), AUDIT Appendix T.3 (AD_Rule schema).*

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

## 3. Validation Rule Database — ERP.db

Current architecture: 3 databases, 3 concerns. Since S76, validation rules
live in **ERP.db** alongside discipline metadata — there is no separate fourth DB.
ERP.db serves both "discipline metadata" and "validation rules" roles, mirroring
iDempiere's Application Dictionary where AD_Val_Rule sits next to AD_Column.

| DB | Concern | Analogy |
|----|---------|---------|
| component_library.db | Product geometry (meshes, materials) | File server |
| `{PREFIX}_BOM.db` | Assembly recipes (what to build) | Product master |
| output.db | Compiled result (where things go) | Transaction |
| **ERP.db** | **Discipline metadata + validation rules (what's allowed)** | **Application Dictionary (AD_Val_Rule + AD tables)** |

ERP.db holds both spatial rules (ad_space_type, placement_rules, ad_element_mep)
and regulatory rules (AD_Val_Rule, AD_Val_Rule_Param, AD_Clash_Rule). It doesn't
create placements, it constrains them. Updated when codes change (annual code
cycles), without touching the BOM or recompiling existing buildings.
See [DISC_VALIDATION_DB_SRS.md §1](DISC_VALIDATION_DB_SRS.md#1-schema-erpdb-22-tables)
for the full table inventory.

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

This paper complements `DISC_VALIDATION_DB_SRS.md`:

| Concern | Paper | What It Defines |
|---------|-------|----------------|
| BOM organization | DISC_VALIDATION_DB_SRS.md | How disciplines structure the BOM tree |
| Placement validation | This paper | How rules constrain what the BOM can contain |

The discipline BOM structure (§2 of DISC_VALIDATION_DB_SRS.md) enables validation:
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
WHERE a.building_type = 'Terminal'
  AND b.building_type = 'Terminal'
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
WHERE a.building_type = 'Terminal'
  AND b.building_type = 'Terminal'
  AND a.discipline = 'ELEC'
  AND b.discipline = 'SP'
  AND a.storey = b.storey;
-- Expected: >= 150mm (NEC 300.4 minimum)
```

These queries are the starting point for Phase 1 rule seeding. Run them,
observe the distributions, encode the boundaries as AD_Val_Rule parameters.

---

### 7.5 Rule Provenance Chain — From Building to Verdict

<!-- Implementing SpecsAnalysis.txt §14 — rule lifecycle cross-links -->

Every validation rule traces through five stages. Each stage is documented
in a specific spec section:

```
EXTRACTION → MINING → ENCODING → VALIDATION → EXCEPTION
    ↓            ↓         ↓           ↓            ↓
IFC→BOM     DocValidate  DV_*_rules  Placement     AD_Val_Rule
 pipeline    §7.1-§7.4   migration   Validator      _Exception
                                      §15.1          §15.3
```

| Stage | What Happens | Spec Reference |
|-------|-------------|----------------|
| **1. EXTRACTION** | IFC file → BOM database. Real building becomes queryable data | [BBC.md §3](BOMBasedCompilation.md) (pipeline), [WorkOrderGuide.md §5-6](WorkOrderGuide.md) (extract config) |
| **2. MINING** | Query extracted data for spatial patterns (spacing, clearance, sizing) | [§7.1-§7.4](#7-rule-mining-from-terminal--the-non-disturbance-principle) (Terminal as oracle), [§15.4-§15.5](#154-rosetta-stone-rule-mining-pipeline) (RuleMiner + mining table) |
| **3. ENCODING** | Mined patterns → AD_Val_Rule + AD_Val_Rule_Param SQL rows in ERP.db | [DISC_VALIDATION_DB_SRS.md §11](DISC_VALIDATION_DB_SRS.md) (AD dictionary), migration/DV_*.sql |
| **4. VALIDATION** | PlacementValidator fires rules against BOM lines (3-tier cascade) | [§13](#13-rule-application-order--the-three-tier-cascade) (cascade), [§15.1](#151-placementvalidator--the-validation-engine) (engine spec) |
| **5. EXCEPTION** | Non-Disturbance protocol: known deviations documented, rules stay active | [§7.2-§7.3](#72-non-disturbance-test) (protocol), [§15.3](#153-ad_val_rule_exception--non-disturbance-protocol) (exception DAO) |

**Regulatory rules** (UBBL, NFPA 13, IRC) enter at stage 3 directly — they
are authored from standards documents, not mined. **Spatial rules** flow
through all five stages. Both converge at stage 4 (validation).

---

## 8. Implementation Sequence

### Phase 1: Mine Terminal + Seed ERP.db
1. Create validation tables in ERP.db with schema from §3.1
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
    String category = line.getParentBom().getProductCategory();  // BEDROOM, KITCHEN, etc.
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
| AD_Val_Rule schema + UBBL seed data | **Planned** | ERP.db (§3.1 + §9.2) |
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
AD_Val_Rule_Param entries in ERP.db.

**Room Area Minimums (m²):**

| Room | MY (UBBL 2012) | US (IRC 2021) | UK (NDSS 2015) | AU (NCC 2022) | SG (BCA) | IN (NBC 2016) | JP (BSA) | CN (GB 50096) | EU (varies) |
|------|---------------|---------------|----------------|---------------|----------|---------------|----------|---------------|-------------|
| Habitable room (any) | — | 6.5 (70 sq ft) | — | — | — | 9.5 | — | — | 7-9 (varies) |
| Single bedroom | 9.2 | 6.5 | 7.5 | — | — | 9.5 | — | 5.0 | 7 (FR), 9 (IT) |
| Double bedroom | 9.2 | 6.5 | 11.5 | — | — | 9.5 | — | 10.0 | 9-14 (varies) |
| Living room | 12.0 | 6.5 | — | — | — | 9.5 | — | 12.0 | — |
| Kitchen | 4.5 | exempt | — | — | — | 5.0 | — | 4.0 | — |
| Bathroom | 1.5 | — | — | — | — | 1.8 | — | 2.5 | — |

**Minimum Dimensions (mm):**

| Parameter | MY (UBBL) | US (IRC) | UK (NDSS) | AU (NCC) | SG (BCA) | IN (NBC) | JP (BSA) | CN (GB 50096) |
|-----------|-----------|----------|-----------|----------|----------|----------|----------|---------------|
| Bedroom min dim | 3000 | 2134 (7 ft) | 2150 | — | — | 2400 | — | 2400 |
| Ceiling height (habitable) | 2600 | 2134 (7 ft) | 2300 | 2400 | 2400 | 2750 | 2100 | 2800 |
| Ceiling height (bathroom) | — | 2032 (6'8") | — | 2100 | — | 2200 | 2100 | 2400 |
| Corridor width | 900 | 914 (3 ft) | 900 | 1000 | 1200 | 1000 | 780 | 1100 |
| Door clear opening | 750 | 813 (32") | 750 | 820 | 850 | 900 | 750 | 900 |

**Fire and Safety:**

| Parameter | MY (UBBL) | US (IBC/IRC) | UK (Part B) | AU (NCC) | IN (NBC) | JP (BSA) | CN (GB 50016) |
|-----------|-----------|-------------|-------------|----------|----------|----------|---------------|
| Sprinkler spacing (LH) | per SS CP 52 | 4600mm (NFPA 13) | BS 9251 | AS 2118.1 | IS 15105 | per FDMA | GB 50084 |
| Fire wall between units | 2-hour | 1-hour (IRC) | 1-hour (Part B) | FRL 60/60/60 | 2-hour | 1-hour | 2-hour |
| Egress door width | 850mm | 813mm (32") | 850mm | 850mm | 1000mm | 800mm | 900mm |
| Secondary stair (height) | — | — | 18m (2024 amend) | — | 15m (NBC) | 31m | 33m (11 storeys) |

**Accessibility:**

| Parameter | MY (UBBL) | US (ADA/ICC A117.1) | UK (Part M/BS 8300) | AU (AS 1428) | IN (NBC Ch.11) | JP (HBFL) | CN (GB 50763) |
|-----------|-----------|--------------------|--------------------|-------------|----------------|-----------|---------------|
| Wheelchair turning radius | 1500mm | 1525mm (60") | 1500mm | 1540mm | 1500mm | 1400mm | 1500mm |
| Accessible door width | 900mm | 815mm (32") | 800mm | 850mm | 900mm | 800mm | 900mm |
| Ramp gradient (max) | 1:12 | 1:12 | 1:12 (Part M) | 1:14 | 1:12 | 1:12 | 1:12 |

**Energy Efficiency:**

| Parameter | Standard | Scope |
|-----------|----------|-------|
| MY | MS 1525:2019 (ETTV ≤ 50 W/m²) | Non-residential envelope |
| US | ASHRAE 90.1 / IECC 2021 | Envelope + HVAC + lighting |
| UK | Part L 2021 (SAP rating) | Fabric-first, u-value limits |
| AU | NCC Section J (NatHERS) | Star rating, DTS pathway |
| IN | ECBC 2017 (BEE star rating) | Commercial, voluntary residential |
| JP | Act on Rational Use of Energy | ZEB targets by 2030 |
| CN | GB 50189 / GB 55015 | Mandatory for all new buildings |

**Acoustic:**

| Parameter | Standard | Residential limit |
|-----------|----------|-------------------|
| MY | MS 1525 (guideline) | — |
| US | IBC §1207 (STC/IIC ≥ 50) | Party wall/floor |
| UK | Part E (Rw ≥ 45 dB airborne, Lnw ≤ 62 dB impact) | Between dwellings |
| AU | NCC F5/Vol 1 (Rw + Ctr ≥ 50) | Between units |
| IN | NBC Part 8 (guideline) | — |
| JP | JIS A 1419 (D-class rating) | Between dwellings |
| CN | GB 50118 (≤ 45 dB daytime) | Environmental noise |

> **Note:** India (NBC 2016 / IS 16700), Japan (Building Standards Act / HBFL),
> and China (GB 50096 / GB 50016) represent the world's three largest
> construction markets by volume. Accessibility (DDA/ADA), energy efficiency,
> and acoustic rules are **future seed data** — not blocking current work, but
> essential for the github.io site to demonstrate comprehensive jurisdiction
> coverage. Each row above maps to a future AD_Val_Rule + AD_Val_Rule_Param
> INSERT in ERP.db.

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

-- India NBC 2016
INSERT INTO AD_Val_Rule VALUES
(601, 'IN_HABITABLE_MIN_AREA',   'COMPLIANCE', 'ARC', 'NBC 2016 Part 3',  'IN', 1),
(602, 'IN_KITCHEN_MIN_AREA',     'COMPLIANCE', 'ARC', 'NBC 2016 Part 3',  'IN', 1),
(603, 'IN_CEILING_MIN_HEIGHT',   'COMPLIANCE', 'ARC', 'NBC 2016 Part 3',  'IN', 1),
(604, 'IN_DOOR_MIN_WIDTH',       'COMPLIANCE', 'ARC', 'NBC 2016 Part 3',  'IN', 1),
(605, 'IN_CORRIDOR_MIN_WIDTH',   'COMPLIANCE', 'ARC', 'NBC 2016 Part 3',  'IN', 1);

INSERT INTO AD_Val_Rule_Param VALUES
(6011, 601, 'min_area_m2',  '9.5',  'NUM'),
(6021, 602, 'min_area_m2',  '5.0',  'NUM'),
(6022, 602, 'bom_category', 'KITCHEN', 'TEXT'),
(6031, 603, 'min_height_mm','2750', 'NUM'),
(6041, 604, 'min_width_mm', '900',  'NUM'),
(6051, 605, 'min_width_mm', '1000', 'NUM');

-- Japan Building Standards Act (BSA)
INSERT INTO AD_Val_Rule VALUES
(701, 'JP_CEILING_HABITABLE',   'COMPLIANCE', 'ARC', 'BSA Art.21',       'JP', 1),
(702, 'JP_CEILING_SERVICE',     'COMPLIANCE', 'ARC', 'BSA Art.21',       'JP', 1),
(703, 'JP_CORRIDOR_MIN_WIDTH',  'COMPLIANCE', 'ARC', 'BSA Art.119',      'JP', 1),
(704, 'JP_DOOR_MIN_WIDTH',      'COMPLIANCE', 'ARC', 'BSA Art.126',      'JP', 1);

INSERT INTO AD_Val_Rule_Param VALUES
(7011, 701, 'min_height_mm','2100', 'NUM'),
(7021, 702, 'min_height_mm','2100', 'NUM'),
(7022, 702, 'bom_category', 'BATHROOM,CORRIDOR', 'TEXT'),
(7031, 703, 'min_width_mm', '780',  'NUM'),
(7041, 704, 'min_width_mm', '750',  'NUM');

-- China GB 50096-2011 (Residential)
INSERT INTO AD_Val_Rule VALUES
(801, 'CN_BEDROOM_MIN_AREA',    'COMPLIANCE', 'ARC', 'GB 50096 s5.2',   'CN', 1),
(802, 'CN_LIVING_MIN_AREA',     'COMPLIANCE', 'ARC', 'GB 50096 s5.2',   'CN', 1),
(803, 'CN_KITCHEN_MIN_AREA',    'COMPLIANCE', 'ARC', 'GB 50096 s5.2',   'CN', 1),
(804, 'CN_CEILING_MIN_HEIGHT',  'COMPLIANCE', 'ARC', 'GB 50096 s5.5',   'CN', 1),
(805, 'CN_DOOR_MIN_WIDTH',      'COMPLIANCE', 'ARC', 'GB 50096',        'CN', 1),
(806, 'CN_CORRIDOR_MIN_WIDTH',  'COMPLIANCE', 'ARC', 'GB 50096',        'CN', 1);

INSERT INTO AD_Val_Rule_Param VALUES
(8011, 801, 'min_area_m2',  '5.0',  'NUM'),   -- single bedroom minimum
(8012, 801, 'bom_category', 'BEDROOM', 'TEXT'),
(8021, 802, 'min_area_m2',  '12.0', 'NUM'),
(8022, 802, 'bom_category', 'LIVING', 'TEXT'),
(8031, 803, 'min_area_m2',  '4.0',  'NUM'),
(8032, 803, 'bom_category', 'KITCHEN', 'TEXT'),
(8041, 804, 'min_height_mm','2800', 'NUM'),
(8051, 805, 'min_width_mm', '900',  'NUM'),
(8061, 806, 'min_width_mm', '1100', 'NUM');
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

## 12. Vertical Rules — Cross-Storey Continuity

### 12.1 The Problem

Certain building elements MUST maintain vertical alignment across storeys.
In iDempiere Manufacturing, this is the multi-level BOM problem: a work order
for Level 2 depends on Level 1's output (the slab it sits on). Construction
has the same constraint physically.

**Vertical elements (cross-storey by nature):**

| Element Type | Discipline | Vertical Constraint | iDempiere Analogue |
|-------------|-----------|--------------------|--------------------|
| MEP risers | CW/SP/FP/LPG | X,Y fixed across storeys; only Z changes | W_Verb_Node sequence (operation depends on prior) |
| Stairs | ARC | Flight connects floor-to-floor; X,Y within stairwell envelope | M_BOM with sub-BOMs per flight (stringer, treads, landing) |
| Lifts/elevators | ARC/ELEC | Shaft perfectly vertical; X,Y identical all storeys | M_Product with IsInstance=1 (shaft height varies) |
| Chutes | ARC | Garbage/laundry chute; X,Y fixed | Same as lift shaft |
| Columns | STR | Grid position maintained vertically | M_BOM_Line with qty=storey_count |
| Structural walls | STR | Shear walls continuous ground to roof | Structural continuity rule |

### 12.2 How Vertical Rules Manifest in the BOM

In the current BOM hierarchy:
```
BUILDING (origin = world LBD)
  → FLOOR_GF → DISC_CW → riser_segment_GF (dx=X₀, dy=Y₀, dz=0)
  → FLOOR_L1 → DISC_CW → riser_segment_L1 (dx=X₀, dy=Y₀, dz=0)
  → FLOOR_L2 → DISC_CW → riser_segment_L2 (dx=X₀, dy=Y₀, dz=0)
```

The vertical rule: `riser_segment.dx` and `riser_segment.dy` must be
**identical** across all storeys where the riser exists. Only the floor's
tack (BUILDING→FLOOR dz) changes the world Z position.

**DX demonstrates this today:** The party wall trunk (shared risers between
units A and B) has MEP elements at X ≈ 4.400m on both Level 1 and Level 2.
The HalfUnit rotation preserves this alignment. The mirror plane is itself
a vertical rule — it defines where shared infrastructure runs.

### 12.3 Vertical Rule as AD_Val_Rule

```sql
-- Vertical continuity rule: MEP risers must maintain X,Y across storeys
INSERT INTO AD_Val_Rule (ad_val_rule_id, name, rule_type, discipline,
    standard_ref, jurisdiction, is_active)
VALUES (601, 'VERT_RISER_CONTINUITY', 'CONTINUITY', NULL,
    'Engineering practice', 'INTL', 1);

INSERT INTO AD_Val_Rule_Param VALUES
(6011, 601, 'element_classes', 'IfcPipeSegment,IfcDuctSegment,IfcCableCarrierSegment', 'TEXT'),
(6012, 601, 'max_xy_drift_mm', '50', 'NUM'),  -- tolerance for alignment
(6013, 601, 'check_across', 'STOREY', 'TEXT'), -- cross-storey check
(6014, 601, 'axis', 'Z', 'TEXT');              -- which axis is "vertical"

-- Column grid continuity
INSERT INTO AD_Val_Rule VALUES
(602, 'VERT_COLUMN_GRID', 'CONTINUITY', 'STR',
    'Engineering practice', 'INTL', 1);

INSERT INTO AD_Val_Rule_Param VALUES
(6021, 602, 'element_classes', 'IfcColumn', 'TEXT'),
(6022, 602, 'max_xy_drift_mm', '25', 'NUM'),
(6023, 602, 'check_across', 'STOREY', 'TEXT');
```

### 12.4 Mining Vertical Rules from Terminal

The Terminal has 7 storeys and 8 disciplines. Vertical continuity is directly
mineable:

```sql
-- Find elements that repeat at same X,Y across multiple storeys
-- (candidates for vertical continuity rules)
SELECT a.ifc_class, a.discipline,
       ROUND(a.min_x, 1) as x, ROUND(a.min_y, 1) as y,
       COUNT(DISTINCT a.storey) as storey_count,
       GROUP_CONCAT(DISTINCT a.storey) as storeys
FROM I_Element_Extraction a
WHERE a.building_type = 'Terminal'
  AND a.ifc_class IN ('IfcPipeSegment', 'IfcDuctSegment',
                       'IfcColumn', 'IfcWall')
GROUP BY a.ifc_class, a.discipline,
         ROUND(a.min_x, 1), ROUND(a.min_y, 1)
HAVING COUNT(DISTINCT a.storey) >= 3
ORDER BY storey_count DESC;
-- Expected: columns at grid intersections, riser stacks, shear walls
```

**DX vertical pattern:**
```sql
-- DX party wall trunk: elements at mirror plane across levels
SELECT a.ifc_class, a.storey, a.min_x, a.min_y,
       ABS(a.min_x - 4.400) as mirror_dist
FROM I_Element_Extraction a
WHERE a.building_type = 'Duplex'
  AND a.discipline IN ('CW', 'SP')
  AND ABS(a.min_x - 4.400) < 0.5  -- within 500mm of party wall
ORDER BY a.storey, a.min_y;
-- Expected: riser stack elements aligned across Level 1 and Level 2
```

### 12.5 Vertical Rule Application

Vertical rules are Tier 3 in the rule cascade (§13 below). They run AFTER
per-discipline and cross-discipline checks because they span multiple FLOOR
BOMs. The check compares element positions across floors using the
BUILDING→FLOOR tack offsets to reconstruct world X,Y.

**For Rosetta Stones:** Vertical rules are mined and verified — the building
is ground truth. Any "violation" means the rule tolerance is too tight.

**For generative (BIM Designer):** Vertical rules constrain the designer.
When the user places a riser on Level 1, the engine auto-extends it through
all storeys (or prompts for confirmation). When the user moves a column on
one floor, the engine warns if it breaks grid continuity above/below.

---

## 13. Rule Application Order — The Three-Tier Cascade

Like iDempiere's document processing where tax → charges → financial posting
run in order, BIM validation fires in three tiers:

### 13.1 Tier 1: Per-Discipline (C_Tax Equivalent)

Each discipline runs through its own AD_Val_Rule set independently.
A sprinkler doesn't know about a duct. A beam doesn't know about a pipe.
Each is validated against its discipline's rules in isolation.

```
Tier 1 cascade (per m_bom_line save in generative mode):

  m_bom_line.product → parent m_bom.bom_category → discipline
  discipline + jurisdiction → AD_Val_Rule set
  for each applicable rule:
    check placement against rule params
    → PASS / WARN / BLOCK
```

**Firing point:** `ModelValidator.beforeSave(MBOMLine)` — every generative
BOM line insertion. Same as iDempiere's tax calculation firing on every
`C_InvoiceLine.beforeSave()`.

**Examples:**
- FP: sprinkler spacing ≤ 4600mm (NFPA 13 LH)
- ARC: bedroom min area ≥ 9.2m² (UBBL s33(1))
- STR: column grid spacing within bay tolerance

### 13.2 Tier 2: Cross-Discipline (C_Charge Equivalent)

After all discipline BOMs are populated, cross-discipline checks run.
Like iDempiere charges that are computed after line items but before
document completion.

```
Tier 2 cascade (per floor, after all discipline BOMs complete):

  for each AD_Clash_Rule:
    get elements from discipline_a on this floor
    get elements from discipline_b on this floor
    for each (a, b) pair within proximity:
      check clash_type (HARD / SOFT / CLEARANCE / MATERIAL)
      → PASS / WARN / BLOCK / ALLOW_IF
```

**Firing point:** `DocAction.prepareIt()` equivalent — before the floor's
BOM is "completed" (committed to output.db).

**The STR beam envelope scenario:** AABB intersection between a beam and a
pipe route may flag a HARD clash. But the real geometry may have web holes
for MEP penetration. Resolution:

```
AD_Clash_Rule:
  discipline_a = MEP (any), discipline_b = STR
  element_filter_b = ifc_class IN ('IfcBeam')
  clash_type = HARD
  verdict = ALLOW_IF
  condition = beam_has_penetration OR penetration_sleeve_product_id IS NOT NULL
```

For Rosetta Stones: if Terminal has pipes routing through beam zones, the
mined rule must encode ALLOW_IF — otherwise the Non-Disturbance Test fails.
The building teaches us which intersections are designed vs accidental.

**Important:** Cross-discipline rules are DATA in AD_Clash_Rule, not code.
Adding a new discipline pair = SQL INSERT. Same engine, same checker.

### 13.3 Tier 3: Cross-Storey / Vertical (Financial Reporting Equivalent)

After all floors are validated, vertical continuity checks span the entire
building. Like iDempiere's financial reporting that aggregates across all
documents in a period.

```
Tier 3 cascade (per building, after all floors validated):

  for each AD_Val_Rule WHERE check_across = 'STOREY':
    group elements by (discipline, product_type, ROUND(x), ROUND(y))
    for each group spanning multiple storeys:
      check X,Y drift across storeys ≤ max_xy_drift_mm
      → PASS / WARN / BLOCK
```

**Firing point:** After all floors committed — building-level validation.

**Examples:**
- Column grid continuity (STR): same X,Y across all storeys
- Riser alignment (CW/SP/FP): pipes at same X,Y across served storeys
- Stairwell envelope (ARC): stair landing X,Y within envelope all storeys
- Shaft alignment (ARC/ELEC): lift shaft perfectly vertical

### 13.4 The Complete Cascade

```
Document lifecycle analogy:

iDempiere C_Order:                    BIM Building:
─────────────────                     ─────────────
1. Line item tax       (beforeSave)   1. Per-discipline rules    (Tier 1)
2. Document charges    (prepareIt)    2. Cross-discipline clash  (Tier 2)
3. Financial posting   (completeIt)   3. Vertical continuity     (Tier 3)
4. Period close        (post)         4. Building-level summary  (report)

Each tier uses the same AD_Val_Rule engine. The difference is SCOPE:
- Tier 1: single m_bom_line
- Tier 2: pairs across disciplines on one floor
- Tier 3: groups across floors in one building
- Report: aggregated results for the whole building
```

**For Rosetta Stones:** All three tiers run in READ-ONLY mode. No BLOCK
verdicts — only LOG. The stone must pass; rules that flag violations are
adjusted. Once rules pass Non-Disturbance for all three stones (SH, DX, TE),
they are promoted to ACTIVE for generative mode.

**For generative (BIM Designer):** All three tiers run in ACTIVE mode.
Tier 1 fires on every BOM line save (real-time feedback). Tier 2 fires on
floor completion (section-level check). Tier 3 fires on building completion
(whole-building check). The ambient compliance strip (BIM_Designer.md §18.4)
shows live status from all three tiers.

---

## 14. Auto-Population Engine — Spawning the Construction Model

### 14.1 The Objective

When a user starts a new building in BIM Designer, the engine auto-populates
the iDempiere construction model: C_Order, C_OrderLine, spatial relationships
(from M_BOM_Line dx/dy/dz), default M_AttributeSetInstance, and W_Verb_Node. The user then ALTERS these
defaults — they don't build from scratch. This is the iDempiere pattern:
`MOrder.prepareIt()` creates default lines, the user modifies before completing.

### 14.2 What Gets Spawned

```
User action: "Create New" → selects building product + jurisdiction + AABB

Engine spawns (in output.db):

1. C_Order
   ├── M_Product_Category = RE/CO/IN (from building product)
   ├── M_Product_ID = user's choice (building product identity)
   ├── jurisdiction = user's choice (MY/US/UK/AU/SG)
   └── aabb = user's envelope dimensions

2. C_OrderLine (one per BOM-level selection)
   ├── #1: BUILDING BOM → family_ref, host_type=BUILDING
   ├── #2: FLOOR BOM per storey → family_ref, host_type=FLOOR
   ├── #3: ROOM/DISCIPLINE sets → family_ref per room or discipline
   └── each carries: default M_AttributeSetInstance_ID

3. Spatial relationships (from M_BOM_Line dx/dy/dz)
   ├── parent BOM's tack offset (attachment origin via dx/dy/dz)
   ├── AABB capacity derived from BOM spatial data
   └── (co_empty_space tables removed S74 — placement via M_BOM_Line dx/dy/dz)

4. M_AttributeSetInstance (default per OrderLine)
   ├── customer-facing defaults (finish=DEFAULT, config=STANDARD)
   └── user can alter: change material, adjust dimensions, pick variant

5. W_Verb_Node (assembly sequence — default routing)
   ├── STR first (structural frame)
   ├── ARC second (architectural envelope)
   ├── MEP third (FP, ACMV, ELEC, CW, SP, LPG)
   └── user can reorder for construction phasing
```

### 14.3 Spawning Sequence (iDempiere Pattern)

```
iDempiere MOrder.prepareIt():              BIM CreateNew:
──────────────────────────────             ──────────────
1. Validate header (C_DocType)             1. Validate M_Product_Category + AABB
2. Create default lines from template      2. Create C_OrderLines from BOM tree
3. Calculate taxes per line                3. Run Tier 1 validation per line
4. Check credit limit                      4. Check AABB containment
5. Set document status = IN_PROGRESS       5. Set order status = DRAFT
6. User modifies lines                     6. User modifies in BIM Designer
7. completeIt() → final validation         7. Promote → Tier 2+3 validation
```

**The engine assists, the user decides.** Default C_Order says "build STR
first, then ARC, then MEP." The user can say "I'm doing prefab — MEP is
pre-installed in wall panels, so MEP goes WITH ARC, not after." The engine
adjusts the W_Verb_Node sequence. Same as iDempiere where a user can
reorder manufacturing operations.

### 14.4 W_Verb_Node — Assembly Routing for Construction

In iDempiere Manufacturing, W_Verb_Node defines the sequence of operations
to produce a product. For construction:

```sql
CREATE TABLE W_Verb_Node (
    pp_order_node_id  INTEGER PRIMARY KEY,
    c_order_id        INTEGER NOT NULL REFERENCES C_Order,
    name              TEXT NOT NULL,           -- 'Foundation', 'Frame', 'Envelope'
    sequence          INTEGER NOT NULL,        -- execution order
    discipline        TEXT,                    -- 'STR', 'ARC', 'FP', etc.
    duration_days     INTEGER,                -- estimated duration
    depends_on        INTEGER REFERENCES W_Verb_Node, -- predecessor
    status            TEXT DEFAULT 'PENDING',  -- PENDING/ACTIVE/COMPLETE
    description       TEXT
);
```

**Default routing for residential (RE):**

| Seq | Node | Discipline | Depends On | Notes |
|-----|------|-----------|------------|-------|
| 10 | Foundation | STR | — | Footings, ground slab |
| 20 | Frame | STR | Foundation | Columns, beams, upper slabs |
| 30 | Envelope | ARC | Frame | Walls, roof, openings |
| 40 | Fire Protection | FP | Envelope | Sprinklers, alarms (if required) |
| 50 | Plumbing | CW/SP | Envelope | Risers, fixtures |
| 60 | HVAC | ACMV | Envelope | Ducts, terminals |
| 70 | Electrical | ELEC | Envelope | Wiring, fixtures, panels |
| 80 | Gas | LPG | Plumbing | Gas lines (if applicable) |
| 90 | Finishes | ARC | all MEP | Paint, trim, flooring |

**Default routing for commercial (CO):**

| Seq | Node | Discipline | Depends On | Notes |
|-----|------|-----------|------------|-------|
| 10 | Substructure | STR | — | Piles, pile caps, ground beams |
| 20 | Superstructure | STR | Substructure | Columns, beams, slabs per floor |
| 30 | Envelope | ARC | Superstructure | Curtain wall, roof, partitions |
| 40 | MEP Risers | CW/SP/FP/ELEC | Superstructure | Vertical runs first |
| 50 | MEP Horizontal | CW/SP/FP/ACMV/ELEC | MEP Risers | Branch runs per floor |
| 60 | Finishes | ARC | MEP Horizontal | Ceilings, floors, partitions |

Note: MEP risers (seq 40) depend on superstructure, not envelope — in
commercial construction, risers are installed into the structural frame
before the facade goes on. This is a **vertical rule dependency**: the
riser stack must be continuous before branch runs connect to it.

---

## 15. Code-Level Specifications

### 15.1 PlacementValidator — The Validation Engine

```java
/**
 * Validates BOM placements against AD_Val_Rule per jurisdiction.
 * Fires during generative BOM creation (Tier 1) and on-demand for
 * Rosetta Stone verification (read-only mode).
 *
 * <p>iDempiere analogue: ModelValidator registered via ComponentFactory.
 * Fires on MBOMLine.beforeSave() in generative mode.
 *
 * <p>Three execution modes:
 * <ul>
 *   <li>ACTIVE — blocks on FAIL (generative/BIM Designer)</li>
 *   <li>READONLY — logs only, never blocks (Rosetta Stone verification)</li>
 *   <li>DISABLED — no validation (extracted EN-BLOC compilation)</li>
 * </ul>
 */
public class PlacementValidator {

    /** Validate a single BOM line against discipline rules (Tier 1). */
    ValidationResult validateLine(Connection bomConn, Connection valConn,
                                  MBomLine line, String jurisdiction);

    /** Validate cross-discipline clashes for one floor (Tier 2). */
    List<ClashResult> validateFloor(Connection bomConn, Connection valConn,
                                    String floorBomId, String jurisdiction);

    /** Validate vertical continuity across all floors (Tier 3). */
    List<ContinuityResult> validateBuilding(Connection bomConn, Connection valConn,
                                             String buildingBomId, String jurisdiction);

    /** Run all three tiers. Returns aggregated report. */
    ValidationReport validateAll(Connection bomConn, Connection valConn,
                                  String buildingBomId, String jurisdiction,
                                  ValidationMode mode);
}
```

**Data flow:**

```
Input:
  bomConn   → {PREFIX}_BOM.db (m_bom, m_bom_line — spatial arrangement)
  valConn   → ERP.db (AD_Val_Rule, AD_Clash_Rule — rule data)
  jurisdiction → "MY", "US", "UK", "AU", "SG", "INTL"
  mode      → ACTIVE / READONLY / DISABLED

Tier 1 (per line):
  line.parent_bom → m_bom.bom_category → discipline
  discipline + jurisdiction → SELECT * FROM AD_Val_Rule
    WHERE discipline = ? AND jurisdiction IN (?, 'INTL') AND is_active = 1
  for each rule: evaluate params against line position + AABB
  → ValidationResult { rule_ref, status (PASS/WARN/BLOCK), message }

Tier 2 (per floor):
  SELECT * FROM AD_Clash_Rule WHERE is_active = 1
  for each rule: get elements from both disciplines on this floor
  spatial proximity check (AABB overlap or min_distance)
  → ClashResult { rule_ref, element_a, element_b, clash_type, verdict }

Tier 3 (per building):
  SELECT * FROM AD_Val_Rule WHERE check_across = 'STOREY'
  group elements by (discipline, product_type, rounded X, rounded Y)
  check X,Y drift across storeys
  → ContinuityResult { rule_ref, element_group, storeys, max_drift_mm }

Output:
  ValidationReport {
    tier1Results: List<ValidationResult>,
    tier2Results: List<ClashResult>,
    tier3Results: List<ContinuityResult>,
    overallStatus: PASS / WARN / BLOCK,
    exceptionCount: int  // known exceptions (AD_Val_Rule_Exception)
  }
```

### 15.2 ConstructionModelSpawner — Auto-Population

```java
/**
 * Spawns the iDempiere construction model when user creates a new building.
 * Populates C_Order, C_OrderLine, spatial slots (from M_BOM_Line dx/dy/dz),
 * default ASI, and W_Verb_Node into output.db (compile DB).
 *
 * <p>iDempiere analogue: MOrder.prepareIt() + MInOut.createFrom().
 * The engine creates default records; the user alters in BIM Designer.
 *
 * <p>Entry point: called by DesignerAPIImpl.createNew() after initial
 * BOM compilation. Reads the compiled BOM tree and spawns one C_OrderLine
 * per BOM level, one M_BOM_Line offset per slot, default ASI per product.
 */
public class ConstructionModelSpawner {

    /**
     * Spawn full construction model from compiled BOM.
     *
     * @param workConn     writable connection to output.db (compile DB)
     * @param bomConn      read-only connection to {PREFIX}_BOM.db
     * @param compConn     read-only connection to component_library.db
     * @param buildingBomId root BOM (e.g., "BUILDING_SH_STD")
     * @param jurisdiction  jurisdiction code for default rules
     * @return spawn result with counts
     */
    SpawnResult spawn(Connection workConn, Connection bomConn,
                      Connection compConn, String buildingBomId,
                      String jurisdiction);
}
```

**Spawn sequence:**

```
1. Create C_Order (building-level header)
   → M_Product_Category, building product, jurisdiction, aabb from BOM

2. Walk BOM tree (same BOMWalker used for compilation)
   For each m_bom encountered during walk:
     → Create C_OrderLine (family_ref = Value, host_type = M_Product_Category)
     → Spatial slot from M_BOM_Line dx/dy/dz (co_empty_space removed S74)
     → Create default M_AttributeSetInstance (from M_Product.M_AttributeSet_ID)

3. Create W_Verb_Node (default routing from M_Product_Category template)
   → RE: Foundation → Frame → Envelope → MEP → Finishes
   → CO: Substructure → Superstructure → Envelope → MEP Risers → MEP Horizontal → Finishes

4. Run Tier 1 validation on each spawned C_OrderLine
   → READONLY mode — log results, don't block
   → Attach validation_status to each C_OrderLine

5. Return SpawnResult { orderLineCount, esLineCount, asiCount, ppNodeCount,
                        validationSummary }
```

### 15.3 AD_Val_Rule_Exception — Non-Disturbance Protocol

```java
/**
 * Records known exceptions from Rosetta Stone verification.
 * When a mined rule flags a violation against an extracted building,
 * and the violation is determined to be design intent (not a real issue),
 * it is recorded here. The rule stays ACTIVE; the exception is documented.
 *
 * <p>Workflow:
 * 1. Mine rule from Terminal data (§7.1)
 * 2. Run rule against Terminal → if violation found:
 *    a. Inspect the specific elements
 *    b. If design intent → INSERT AD_Val_Rule_Exception
 *    c. If rule too strict → adjust rule params (tolerance, filter)
 *    d. If genuine issue → document as known defect in extraction
 * 3. Run rule against DX → same protocol
 * 4. Run rule against SH → same protocol
 * 5. Rule passes Non-Disturbance for ALL stones → promote to ACTIVE
 */
```

**Exception vs rule adjustment decision tree:**

```
Rule violation found on Rosetta Stone:
│
├─ Is this design intent?
│   YES → AD_Val_Rule_Exception (document, stone stays GREEN)
│   │     Example: DX P23 MEP corners (364 instances, known)
│   │
│   NO ──┐
│        │
├─ Is the rule tolerance too tight?
│   YES → Adjust AD_Val_Rule_Param (widen tolerance)
│   │     Example: column drift tolerance 25mm → 50mm
│   │
│   NO ──┐
│        │
├─ Is this a real defect in the reference building?
│   YES → Document as known defect, keep rule as-is
│   │     Example: IfcReinforcingBar GIC issues (8 instances)
│   │
│   NO → Rule is fundamentally wrong → DELETE and re-derive
```

### 15.4 Rosetta Stone Rule Mining Pipeline

```java
/**
 * Mines spatial rules from Rosetta Stone extraction data.
 * Produces AD_Val_Rule + AD_Val_Rule_Param candidates.
 *
 * <p>The RSS.txt methodology: trace the fossil to get real geometry.
 * Rules are EXTRACTED from data, never invented. Same principle as
 * the BOM pipeline: EXTRACT OR COMPILE ONLY.
 *
 * <p>Mining phases:
 * 1. Per-discipline: spacing, coverage, sizing patterns
 * 2. Cross-discipline: clearances, penetrations, clash zones
 * 3. Cross-storey: vertical continuity, riser alignment
 *
 * <p>Output: SQL INSERT statements for ERP.db
 * These are CANDIDATES until they pass Non-Disturbance (§7.2).
 */
public class RuleMiner {

    /** Mine Tier 1 rules: per-discipline spacing/sizing. */
    List<CandidateRule> mineTier1(Connection compConn, String buildingType);

    /** Mine Tier 2 rules: cross-discipline clearances. */
    List<CandidateRule> mineTier2(Connection compConn, String buildingType);

    /** Mine Tier 3 rules: cross-storey vertical continuity. */
    List<CandidateRule> mineTier3(Connection compConn, String buildingType);

    /** Run Non-Disturbance test: all mined rules vs all stones. */
    NonDisturbanceReport verify(Connection valConn, Connection compConn,
                                 List<String> buildingTypes);
}
```

### 15.5 Concrete Mining Table — Terminal Rules to Derive

| # | Discipline | Rule Name | What to Mine | Expected Value | SQL Pattern | Tier |
|---|-----------|-----------|-------------|----------------|-------------|------|
| M1 | FP | Sprinkler NN spacing | Head-to-head distance, 909 heads | 3000-4600mm | §7.4 query 1 | 1 |
| M2 | FP | Branch pipe max length | TEE-to-last-head per run | ≤12000mm | ROUTE segment sum | 1 |
| M3 | FP | Riser diameter | Pipe segment diameter per storey | ≥50mm main, ≥25mm branch | M_Product dims | 1 |
| M4 | ELEC | Light fixture grid | NN spacing of 814 IfcLightFixture | 2500-3500mm | XY distance per zone | 1 |
| M5 | ELEC | Ceiling offset | Z distance from slab soffit | constant per storey | Z relative to floor | 1 |
| M6 | STR | Column grid | Column-to-column spacing | bay dimensions | XY distance cluster | 1 |
| M7 | STR | Beam span | Beam length vs column spacing | ≤bay width | M_Product.width | 1 |
| M8 | ARC | Roof tile pitch | TILE step consistency | 495×150mm | TILE verb_ref params | 1 |
| M9 | FP×ACMV | Sprinkler-duct clearance | Head-to-duct min distance | ≥300mm (NFPA 13 obstruction) | AABB proximity | 2 |
| M10 | MEP×STR | Beam penetration | Pipe routing through beam zone | ALLOW_IF web hole | AABB intersection | 2 |
| M11 | MEP×ARC | Fire wall penetration | Pipe through fire-rated wall | ALLOW_IF fire collar | AABB + fire_rating | 2 |
| M12 | ELC×PLB | Conduit-pipe separation | Min distance between systems | ≥150mm (NEC 300.4) | §7.4 query 2 | 2 |
| M13 | CW | Riser vertical continuity | Same X,Y across ≥3 storeys | drift ≤50mm | §12.4 query | 3 |
| M14 | STR | Column vertical continuity | Column grid across all storeys | drift ≤25mm | §12.4 query | 3 |
| M15 | FP | Riser vertical continuity | Fire protection riser alignment | drift ≤50mm | same as M13 | 3 |

| M16 | ARC | Opening face-anchor consistency | Opening centroid depth vs host wall center | ≤5mm (centered) or declared INT/EXT | Centroid offset formula; validate against `component_definitions.attachment_face` + ASI `face_anchor` override | 1 |
| M17 | ARC | Opening host association | Every IfcDoor/IfcWindow has a host IfcWall | host_id NOT NULL | FK check; `component_definitions.forward_axis` determines facing direction | 1 |

**17 rules to mine. Each becomes an AD_Val_Rule row (or set of rows).**
All 17 must pass Non-Disturbance against TE and DX before activation.
SH has only ARC discipline, so M8/M16/M17 apply to SH.
SQL seeded: `migration/V004_mined_rules.sql` (M2-M8, M11, M13-M17).
Non-Disturbance analysis: `G4_SRS.md` §6.

### 15.6 Schema-Not-Geometry Rule

> **If a validation check uses AABB arithmetic, that is evidence of a missing
> extraction column. Fix the schema, do not add geometry code.**

Every AD_Val_Rule check must be expressible as a SQL query over the 4-database
schema. The extraction pipeline (`ifc_geometry_extractor.py` + `ExtractionPopulator`)
is responsible for pre-digesting ALL IFC relationships the rules need into
relational columns. If a rule implementation falls back to computing distances
from AABB coordinates, the question is: does the IFC schema have a relationship
that could be captured as a FK column instead?

**Decision tree for new rules:**

```
New rule needs spatial data:
│
├─ Can the check be done with existing columns?
│   YES → Write the SQL. Done.
│   NO ──┐
│        │
├─ Does IFC have a relationship for this? (IfcRelVoidsElement,
│   IfcRelConnectsPathElements, IfcRelContainedInSpatialStructure, etc.)
│   YES → Add column to I_Element_Extraction schema (R20-class gap).
│   │     Extraction pipeline extracts it. Rule becomes a JOIN.
│   │
│   NO ──┐
│        │
├─ Is ERP-maths the correct method? (arithmetic on M_Product dimensions
│   + placement positions — e.g., M12 pipe clearance)
│   YES → Use ERP-maths. Document WHY in the AD_Val_Rule_Param.
│   │     This is legitimate — no IFC relationship would improve it.
│   │
│   NO → The rule cannot be implemented in this architecture.
│        Document as out-of-scope (requires mesh-level geometry).
│        Defer to BlenderBridge Phase G-8.
```

**Audit of M1-M17 against this rule:**

| Rule | Current method | AABB arithmetic? | IFC relationship available? | Verdict |
|------|---------------|-----------------|---------------------------|---------|
| M1 | NN centroid distance | YES | No — spacing is positional, no IFC rel | **ERP-maths OK** (distance between product positions) |
| M2 | ROUTE segment sum | NO | — | **SQL OK** (verb_ref params) |
| M3 | M_Product cross-section | NO | — | **SQL OK** (product dimensions) |
| M4 | NN centroid distance | YES | No — spacing is positional | **ERP-maths OK** (same as M1) |
| M5 | Z deviation per storey | YES | `IfcRelContainedInSpatialStructure` → storey | **Partial gap**: storey already captured, Z offset is positional → ERP-maths OK |
| M6 | NN centroid distance | YES | No — column grid is positional | **ERP-maths OK** |
| M7 | M_Product.width vs bay | NO | — | **SQL OK** |
| M8 | TILE verb params | NO | — | **SQL OK** (verb fidelity) |
| M9 | AABB proximity | YES | `IfcRelInterferesElements` (IFC4) | **SCHEMA GAP**: extract interference relationship → FK check. Fallback: ERP-maths OK (cross-section radii) |
| M10 | AABB intersection | YES | `IfcRelInterferesElements` + beam web holes | **SCHEMA GAP**: extract penetration data. Fallback: ALLOW_IF verdict |
| M11 | AABB + fire_rating | YES | `IfcRelFillsElement` (fire stops in openings) | **SCHEMA GAP**: extract fire stop elements. Fallback: WARN verdict |
| M12 | ERP-maths clearance | YES | No — pipe clearance IS arithmetic on cross-section radii | **ERP-maths OK** (centreline - radius_a - radius_b is the correct method) |
| M13 | ROUND(x,y) grouping | YES | `IfcRelConnectsElements` (riser stack) | **SCHEMA GAP**: extract riser connectivity. Fallback: positional grouping OK |
| M14 | ROUND(x,y) grouping | YES | `IfcRelConnectsElements` (column grid) | **SCHEMA GAP**: extract grid. Fallback: positional grouping OK |
| M15 | ROUND(x,y) grouping | YES | Same as M13 | **SCHEMA GAP** |
| M16 | Centroid depth offset | YES | `IfcRelVoidsElement` (opening → host wall) | **SCHEMA GAP**: extract host_element_ref column. See G4_SRS §6.1 |
| M17 | AABB proximity | YES | `IfcRelVoidsElement` | **SCHEMA GAP**: same as M16. See G4_SRS §6.2 |

**Summary:**

| Verdict | Rules | Count |
|---------|-------|-------|
| SQL OK (no AABB) | M2, M3, M7, M8 | 4 |
| ERP-maths OK (AABB is correct method) | M1, M4, M5, M6, M12 | 5 |
| SCHEMA GAP (IFC rel → extraction column) | M9, M10, M11, M13, M14, M15, M16, M17 | 8 |

**8 schema gaps identified.** Each maps to an IFC relationship that could be
extracted as a column in `I_Element_Extraction` or a new linking table.
Priority order for R20 extraction pipeline expansion:

| Priority | Gap | IFC Relationship | New Column/Table |
|----------|-----|-----------------|-----------------|
| 1 | M16/M17 | `IfcRelVoidsElement` | `host_element_ref TEXT` on I_Element_Extraction |
| 2 | M13/M14/M15 | `IfcRelConnectsElements` | `I_Element_Connectivity` linking table |
| 3 | M9/M10 | `IfcRelInterferesElements` | `I_Element_Interference` linking table |
| 4 | M11 | `IfcRelFillsElement` | `fire_stop_product_ref TEXT` on I_Element_Extraction |

Until these columns exist, the rules use ERP-maths or AABB fallback with
explicit `check_method` documentation in AD_Val_Rule_Param (e.g.,
`AABB_PROXIMITY`, `CENTROID_DEPTH_VS_HOST_CENTER`). The `check_method` param
is the signal: when the extraction column arrives, the rule upgrades from
AABB fallback to FK join — zero rule schema change, just a param update.

**Spatial Predicate Verbs** (BIM_COBOL.md §20): All ERP-maths spatial queries
(distance, clearance, containment, nearest-neighbour, vertical alignment) are
standardised as reusable predicates over output.db. M-rules call predicates
instead of writing ad-hoc SQL. When R21-R24 extraction columns land, the
predicate upgrades internally (AABB fallback → FK join) without changing callers.

---

*References:
[DISC_VALIDATION_DB_SRS.md](DISC_VALIDATION_DB_SRS.md) (discipline BOM structure) |
[BBC.md](BOMBasedCompilation.md) §1 (C_Order model) |
[BIM_Designer.md](BIM_Designer.md) §4 (compliance as compilation constraint), §9 (container rules), §11 (BonsaiBIMDesigner) |
[TestArchitecture.md](TestArchitecture.md) (ProveStage gates) |
`phase27-tb-lktn/DSL_DICTIONARY.md` (TB-LKTN generative reference) |
tools/cross_discipline_checker.py (existing checker) |
[BOMBasedCompilation.md](BOMBasedCompilation.md) §3.3 (EN-BLOC), §4 (tack convention) |
[TerminalAnalysis.md](TerminalAnalysis.md) (discipline inventory, verb patterns)*

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
