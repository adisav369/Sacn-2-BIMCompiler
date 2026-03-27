# AD_Org Discipline Model — Parallel Orgs, Not Spatial Nesting

> **Foundation:** [MANIFESTO](MANIFESTO.md) §AD_Org · [BBC](BOMBasedCompilation.md) §4 · [TestArchitecture](TestArchitecture.md) · [TerminalAnalysis](TerminalAnalysis.md) §Compilation Status

<div class="bim-banner" markdown>
<b>A discipline is a contractor with a checklist, not a room with walls.</b>
AD_Org partitions the BOM validation space — each discipline runs its own
rules on the same spatial container. The BOM tree stays flat. Discipline
is a line attribute, not a tree level.
</div>

## §1 The Problem This Solves

TE (Terminal, 48,428 elements, 8 disciplines) exposed a fundamental error:
`DisciplineBomBuilder` created DISCIPLINE SET BOMs as spatial containers
between FLOOR and LEAF. This forced each discipline into its own AABB —
but disciplines are not spatial containers. A fire protection pipe network
spans the entire floor. An awning overhangs the structural frame by 14m.
A cold water riser crosses storey boundaries.

Result: 471 tack overflows, 36 unbalanced BOMs. IFCtoBOM QA aborted.
TE_BOM.db never written. Gates compared extraction-vs-extraction.
See [TerminalAnalysis.md §Compilation Status](TerminalAnalysis.md#te-compilation-status--honesty-report-s99-2026-03-27).

**Root cause:** discipline was modelled as a tree level instead of a
line attribute. The fix is architectural, not mathematical.

## §2 The iDempiere Pattern

In iDempiere, `AD_Org` partitions data by business unit. Every record
carries an `AD_Org_ID`. The same product catalog, same warehouse, same
price list — but filtered by who is responsible.

In BIM compilation:

| iDempiere | BIM Compiler |
|-----------|-------------|
| AD_Org = business unit | AD_Org = engineering discipline |
| Org partitions transactions | Discipline partitions validation rules |
| Same product catalog, different org | Same M_Product, different AD_Org_ID |
| AD_Val_Rule scoped per org | ad_fp_coverage, ad_acmv_sizing scoped per discipline |

**Key principle:** AD_Org is a COLUMN on the data, not a LEVEL in the
hierarchy. Three products in the same room can have three different orgs:

```
FLOOR (GF) → LEAF: SOFA_001        AD_Org=ARC
FLOOR (GF) → LEAF: SPRINKLER_001   AD_Org=FP
FLOOR (GF) → LEAF: LIGHT_001       AD_Org=ELEC
```

All three share the same parent space (Ground Floor). Each follows its
own discipline's rules.

## §3 The BOM Model — Space + Occupant + Verb + Rule

The BOM hierarchy is recursive and abstract:

```
SPACE (M_Product, IsBOM=Y)
  └── OCCUPANT line (M_BOM_Line, with AD_Org_ID + verb_ref)
```

A SPACE does not know it is a "floor" or a "building." It has:
- An AABB (its spatial extent)
- An M_Product_Category (what kind of space — GF, RF, DEPARTURE_HALL)

An OCCUPANT does not know it is "fire protection" or "architecture." It has:
- An AD_Org_ID (which contractor is responsible)
- A verb_ref (HOW it occupies the space — PLACE, ROUTE, FRAME, TILE, WIRE)
- A reference to AD_Val_Rule (the checklist)

The compiler resolves placement from metadata:

```
for each BOM line in parent:
    verb  = line.verb_ref        → Strategy (GoF)
    rule  = AD_Val_Rule.lookup(line.AD_Org_ID, parent.M_Product_Category)
    verb.place(child, parent.space, rule)
```

No `if ("CO".equals(...))`. No `if (discipline == "FP")`. The verb is the
Strategy pattern. The rule is the Specification pattern. The BOM is the
Composite pattern. The walker is the Visitor pattern. All resolved from
metadata, not from code.

### Covering vs Inside

Two spatial relationships, both just BOM lines:

| Relationship | Verb family | Example |
|-------------|-------------|---------|
| **INSIDE** | PLACE | Sofa at (dx,dy,dz) in living room |
| **COVERING** | ROUTE, TILE, FRAME, WIRE | Sprinklers covering a floor per NFPA 13 |

INSIDE: child sits AT a point within the parent space. The tack offset is
the position. Child is smaller than parent.

COVERING: child SPANS the parent space. The verb determines the coverage
pattern. The validation rule determines density and spacing. Quantity is
derived from the parent's extent + the rule.

Both are M_BOM_Line rows. The verb differentiates.

## §4 Discipline Profiles — Abstract Recipe, Space-Dependent Placement

Each discipline has a **recipe** (products + topology) and a **rule set**
(constraints). The parent space determines quantity and placement.

### ARC — Architecture (AD_Org_ID=1)

```
Recipe:   Walls, doors, windows, plates, furniture
Verb:     PLACE (flat), TILE (roof panels)
Rule:     Architect's design — no code-governed rules
Spatial:  INSIDE. Each element at explicit position within floor.
```

### STR — Structural (AD_Org_ID=2)

```
Recipe:   Column + Beam + Slab products
Verb:     FRAME (grid intersections)
Rule:     Structural grid from engineer's design
Spatial:  COVERING. Columns at intersections, beams spanning between.
          Grid changes per floor → different FRAME params, same products.
```

### FP — Fire Protection (AD_Org_ID=3)

```
Recipe:   Main pipe + branch pipes + fittings + sprinkler heads
Verb:     ROUTE
Rule:     ad_fp_coverage (NFPA 13 / MS 1910)
          max_spacing_mm ≤ 4600, branch_length_mm ≤ 12000
          routing_method: TREE / LOOP / GRID
Spatial:  COVERING. Pipe network spans the floor.
          Space area determines: how many runs, how many heads.
          Rule determines: spacing, pipe diameter, routing method.
```

### ELEC — Electrical (AD_Org_ID=4)

```
Recipe:   Light fixtures (identical units)
Verb:     WIRE (2D ceiling grid)
Rule:     ad_space_type_mep (receptacle_count ≥ area_sqm / 10)
Spatial:  COVERING. Regular grid on ceiling plane.
          Space type determines density. Contractor goes by context.
```

### ACMV — HVAC (AD_Org_ID=5)

```
Recipe:   Duct segments + fittings + air terminals
Verb:     ROUTE DUCTS
Rule:     ad_acmv_sizing (duct_area_mm² ≥ cfm / velocity)
Spatial:  COVERING. Zone volume (m³) → airflow → duct diameter.
          Minimal leaf set: segments + fittings + terminals.
```

### CW / SP / LPG — Piping (AD_Org_ID=6,7,8)

```
Recipe:   Pipe segments + fittings + valves + terminals
Verb:     ROUTE
Rule:     Per-discipline AD tables (sizing, pressure drop)
Spatial:  COVERING. Plumbing fixtures determine network extent.
          BOM is "fatter" — fewer product types, simpler topology
          than FP. CW riser may span Foundation→Roof (multi-storey).
```

## §5 The Contractor's Checklist — AD_Val_Rule as Scope of Work

The AD_Val_Rule is the contractor's checklist. In ERP terms:

| Checklist item | ERP concept | Example |
|---------------|-------------|---------|
| **WHO** | AD_Org_ID | FP contractor |
| **WHERE to** | M_Product_Category filter | GF, L1, L2 (not Foundation) |
| **WHERE NOT to** | Category exclusion | FN excluded — no MEP below grade |
| **HOW** | Rule parameters | max_spacing=4600mm, routing=TREE |
| **HOW MANY** | Rule + parent space | coverage_area=12.1 m² per head |
| **WHAT WITH** | M_Product reference | SPRINKLER_K80, PIPE_CW_50MM |
| **CHECK AGAINST** | Cross-discipline rule | 150mm clearance from ELEC |

The FP contractor reads the scope of work (AD_Org assignment), checks the
building code (AD_Val_Rule), looks at the floor plan (parent space AABB),
and applies the rule. The compiler does the same.

### M_Product_Category as the Bridge

M_Product_Category connects disciplines to spaces. A category like `GF`
(Ground Floor) is shared across all disciplines. FP, ELEC, ARC all operate
on GF. The category tells the discipline what space it's working in.
The validation rule is scoped to the category:

```sql
-- FP rules for ground floor
SELECT * FROM ad_fp_coverage WHERE m_product_category = 'GF'
  AND hazard_class = 'ORDINARY'

-- ELEC rules for ground floor
SELECT * FROM ad_space_type_mep WHERE m_product_category = 'GF'
  AND space_type = 'DEPARTURE_HALL'
```

Different disciplines, same space, different rules — all resolved from
metadata. No discipline-specific code in the compiler.

## §6 Impact on the BOM Tree

### Before (wrong — discipline as tree level)

```
BUILDING → FLOOR → DISCIPLINE SET → LEAF
                   ↑ spatial container with own AABB
                   ↑ causes 471 tack overflows
```

### After (correct — discipline as line attribute)

```
BUILDING → FLOOR → LEAF (each line carries AD_Org_ID)
```

Same depth as SH/DX (residential). Each LEAF line has:
- `child_product_id` — what product
- `dx/dy/dz` — where in the floor (tack offset, LBD convention)
- `AD_Org_ID` — which discipline is responsible
- `verb_ref` — how it's placed (PLACE, ROUTE, FRAME, TILE, WIRE)

The tack is always `element.minX - floor.minX`. Always positive. Always
within the floor. No intermediate discipline AABB to violate.

### What Changes in Code

| Component | Change |
|-----------|--------|
| `DisciplineBomBuilder` | Remove DISCIPLINE SET BOM creation. Write LEAF lines directly under FLOOR with `AD_Org_ID` column. Tack parent = floor, not discipline set |
| `BomValidator` | W-TACK-1 and W-BUFFER-1 check FLOOR→LEAF, not SET→LEAF. Per-discipline validation via AD_Org_ID filter, not tree level |
| `CompilationPipeline` | Delete CO skip hack (line 352-354). CO buildings compile through same path as RE |
| `PlacementCollectorVisitor` | Read AD_Org_ID from BOM line, not from discipline stack pushed by SET BOM |
| `VerbFactorizer` | Parent min = floor min (already correct at line 172 — the intermediate SET was the bug) |

## §7 GoF Design Patterns

| Pattern | Application |
|---------|-------------|
| **Composite** | BOM tree: SPACE contains OCCUPANT lines, recursively |
| **Visitor** | BOMWalker visits each line. Already exists |
| **Strategy** | Verb determines placement method. ROUTE, FRAME, TILE, WIRE, PLACE |
| **Specification** | AD_Val_Rule determines constraints. Verb queries it |

No Factory for disciplines. No switch on category. The walker visits a
line, reads its `verb_ref`, resolves the Strategy, passes the parent
space and the rule. The Strategy does the rest. Behaviour from metadata,
not from code — same as iDempiere's DocAction pattern.

## §8 Cross-References

### Building analyses with discipline concerns

| Building | Disciplines | Issue | Reference |
|----------|-------------|-------|-----------|
| **TE** (Terminal) | 8 active (ARC,STR,FP,ACMV,ELEC,CW,SP,LPG) | DISCIPLINE SET as tree level → 471 tack overflows | [TerminalAnalysis.md §Compilation Status](TerminalAnalysis.md#te-compilation-status--honesty-report-s99-2026-03-27) |
| **DM** (DemoHouse) | 3 (ARC,STR,FP) | First FP discipline trial — addDiscipline() mutation | [DemoHouseAnalysis.md](DemoHouseAnalysis.md) |
| **FK** (FZKHaus) | 2 (ARC,STR) + ROOF debate | Roof as sub-discipline of STR vs new discipline | [FZKHausAnalysis.md](FZKHausAnalysis.md) |
| **Infrastructure** | ROAD,RAIL,GEO,LAND,SIGN | Extended discipline codes, no extraction yet | [InfrastructureAnalysis.md](InfrastructureAnalysis.md) |
| **SH** (SampleHouse) | 2 (ARC,STR) | Hello-world proof, no discipline issues | [SampleHouseAnalysis.md](SampleHouseAnalysis.md) |

### Spec dependencies

- [MANIFESTO.md §AD_Org](MANIFESTO.md#ad_org--discipline-as-organisational-unit) — full 16-discipline table
- [BBC.md §4](BOMBasedCompilation.md) — tack convention (governs all offset math)
- [DISC_VALIDATION_DB_SRS.md](DISC_VALIDATION_DB_SRS.md) — per-discipline validation scoping
- [DocValidate.md](DocValidate.md) — 3-tier cascade (per-discipline → cross-discipline → vertical)
- [BIM_COBOL.md §19](BIM_COBOL.md) — verb taxonomy and detection algorithms
- [TerminalAnalysis.md §Val_Rule](TerminalAnalysis.md#val_rule--regulations-as-domain-ad-tables) — regulation tables per discipline
