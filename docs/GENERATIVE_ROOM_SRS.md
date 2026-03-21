# Generative Compilation — SRS

> **Foundation:** [BBC](BOMBasedCompilation.md) §1 (Three BOM Dimensions) · §3.4 (WALK-THRU) · §3.5 (Selection Cascade)
> **Traces:** BBC.md §3.6 (Rosetta Stone proves machinery, generative uses it)

*Date: 2026-03-21. Session: S51. Replaces: GenerativeRoomPopulator (deleted — wrong architecture).*

---

## 1. The Insight

A GENERATIVE building does not need new code. It needs **data** — BOM entries
selected from the library by the same three dimensions that govern all compilation:

1. **BomCategory** (WHAT) — LIVING, KITCHEN, BEDROOM, BATHROOM
2. **DocBaseType / DocSubType** (WHICH) — RE/DM borrows from RE/SH, RE/DX
3. **SpaceSize** (HOW MUCH) — AABB on the slot determines which child BOM fits

The BOM walker, tack convention, verb expansion, ESLine placement, and all 6 gates
work identically for generative and extracted buildings. BBC.md §3.6:

> *"The Rosetta Stone is a launch booster — it is abandoned once it has worked.
> Its purpose is to calibrate and prove the compilation machinery. Once proven,
> the same machinery drives generative compilation where the BOM is authored
> by a human or Designer, not extracted from IFC."*

**Zero new compilation code.** The generative path is a data authoring problem,
not a code problem.

## 2. How It Works

### 2.1 The DemoHouse Path

```
User: "Create 2BR house"
         │
         ▼
DesignerAPI.createNew()
    RoomLayoutGenerator → 5 DesignBBox items:
      LIVING   (bom_category=LIVING,   AABB=4000×3500×2800)
      KITCHEN  (bom_category=KITCHEN,  AABB=4000×3500×2800)
      BEDROOM1 (bom_category=BEDROOM,  AABB=5000×3500×2800)
      BEDROOM2 (bom_category=BEDROOM,  AABB=5000×3500×2800)
      BATHROOM (bom_category=BATHROOM, AABB=2000×1500×2800)
         │
         ▼
work_output.db: C_Order (DocStatus=DR) + C_OrderLine per room
    Each C_OrderLine carries: bom_category, AABB, parent linkage
         │
         ▼
Selection Cascade (BBC.md §3.5) — for each room slot:
    1. Scope:  bom_category = 'BATHROOM'
    2. Filter: DocBaseType = 'RE' (same base type as parent building)
              DocSubType = 'DM' preferred → fallback to 'SH', 'DX', etc.
    3. Fit:    child BOM AABB fits within slot's 2000×1500×2800
    4. Volume: largest fitting BOM wins
    5. seq_no: tiebreak
         │
         ▼
Matching BOM found → its children ARE the room contents:
    SH's BATHROOM BOM has: toilet, sink, door, window, exhaust fan...
    These children come with tack offsets (dx/dy/dz) already set.
    No placement code needed — the BOM IS the placement spec.
         │
         ▼
User reviews in Designer UI
    Sees: "BATHROOM will use SH's bathroom layout"
    Can adjust: swap products, move items, change dimensions
    All edits are C_OrderLine modifications in work_output.db
         │
         ▼
DesignerAPI.promote() — explicit user action
    C_OrderLine → m_bom + m_bom_line (entity_type='U', provenance='GENERATIVE')
    DocStatus: DR → AP → CO (frozen)
         │
         ▼
BuildingCompiler.compile() — SAME AS ROSETTA STONES
    Reads m_bom/m_bom_line (same walker, same tack, same gates)
    Resolves products from component_library.db (same library)
    BIMEyes proofs run on output (same proofs)
    → output.db
```

### 2.2 The Three BOM Dimensions at Work

| Dimension | iDempiere Pattern | DemoHouse Application |
|-----------|------------------|----------------------|
| **Category** | `M_BomCategory` — what kind of recipe | BATHROOM slot → look for BATHROOM BOMs |
| **Owner** | `C_DocType.DocSubType` — which variant | DM building → prefer DM BOMs, fallback to SH/DX BOMs with same DocBaseType |
| **SpaceSize** | `M_BOM_Line` AABB — how big | 2000×1500×2800 slot → child BOM must fit |

When the DocSubType doesn't match (DM has no dedicated BATHROOM BOM), the cascade
falls back to the same DocBaseType (RE). SH is RE. DX is RE. Their BOMs are available
to DM because they share the base type. This is the iDempiere pattern: a Sales Order
(SOO) uses the same products as a Purchase Order (POO) — different document type,
same product catalog.

### 2.3 What the Authority Data Tables Provide

The authority data tables are **validation and suggestion** resources — they don't
drive compilation. They help the Designer present intelligent defaults:

| Table | Role | When used |
|-------|------|-----------|
| `ad_space_type_opening` | "A BATHROOM should have 1 entry door + high window" | Design time: suggest openings if room BOM is empty |
| `ad_space_type_mep_bom` | "A BATHROOM needs 1 exhaust fan, 1 GFCI outlet, 1 toilet" | Design time: suggest MEP if not yet placed |
| `ad_opening_family` | "BATHROOM_DOOR = 750×2100mm IfcDoor" | Design time: default dimensions for suggested openings |
| `AD_Val_Rule` | "BATHROOM min area ≥ 1.5 m² (UBBL)" | Approve gate: blocks promotion if violated |

**At compile time, none of these tables are read.** The compiler reads only
m_bom / m_bom_line / M_Product / component_library. The authority data shaped
the BOM during design; the BOM carries the decisions forward.

### 2.4 What Gets Borrowed — Provenance Chain

```
Terminal (TE) ──extract──→ component_library.db (505 M_Products, 24K geometries)
SampleHouse (SH) ──extract──→ component_library.db (18 M_Products)
Duplex (DX) ──extract──→ component_library.db (78 M_Products)
FZKHaus (FK) ──extract──→ component_library.db (81 M_Products)
30 other IFCs ──extract──→ component_library.db (1777 more M_Products)
                                    │
                                    │  2,459 products total
                                    │  All with geometry, orientation, IFC class
                                    ▼
Building codes ──encode──→ disc_validation.db
  NEC 2020, IPC 2021      (MEP schedules, opening rules)
  NFPA 13, MS 1228         (37 residential MEP entries)
  UBBL (Malaysian)          (room compliance rules)
                                    │
                                    ▼
DemoHouse (DM) ──borrow──→ Same products, same BOMs, same rules
  Via Selection Cascade §3.5:
    bom_category + DocBaseType + AABB fit
  No new products created.
  No new geometry invented.
  Every element traces to a real IFC or a building code.
```

## 3. What the Designer Suggests

When a user creates a GENERATIVE building and the rooms are empty (no child BOM
selected yet), the Designer can **auto-suggest** using the BOM Chooser (§3.5):

```
DesignerAPI.suggestRoomContents(buildingId)
    For each room C_OrderLine with bom_category:
        1. Query m_bom WHERE bom_category = ? AND DocBaseType = parent's DocBaseType
        2. Filter by AABB fit (child ≤ room slot)
        3. Rank by §3.5 cascade
        4. Return best-match BOM as suggestion
    User sees: "Suggested: SH_BATHROOM_STD (from SampleHouse)"
    User clicks "Accept" or browses alternatives
```

This is **not new code** — it's the existing `browseItems` / BOM Chooser wired
to auto-select when the room is empty. The authority data tables (ad_space_type_*)
refine suggestions with domain knowledge ("a BATHROOM needs a toilet") but the
BOM cascade is the primary mechanism.

## 4. Compilation — Homogeneous with Rosetta Stones

After promotion, DM compiles through the same 9-stage pipeline as SH/DX/TE:

| Stage | What | Same for DM? |
|-------|------|-------------|
| 1 | Read building definition from C_DocType | Yes — DM has C_DocType row |
| 2 | Load BOM tree from m_bom / m_bom_line | Yes — promoted from C_OrderLine |
| 3 | Resolve M_Product from component_library.db | Yes — borrows SH/DX/TE products |
| 4 | BOM walker traverses tree (§2.2) | Yes — same walker |
| 5 | Tack convention computes world coords (§4) | Yes — same offsets |
| 6 | Verb expansion (TILE/ROUTE/CLUSTER) | Yes — if room BOMs use verbs |
| 7 | MeshBinder resolves geometry | Yes — same library |
| 8 | BIMEyes proofs verify output | Yes — same 26 proofs |
| 9 | Output DB written | Yes — same format |

**Gate verification:**

| Gate | What it proves for DM |
|------|----------------------|
| G1-COUNT | Element count matches BOM recipe |
| G2-VOLUME | Total volume conserved |
| G4-TAMPER | No extraction data read at compile time |
| G5-PROVENANCE | All geometry from library (no GEO_ fallback) |
| G6-ISOLATION | EN-BLOC == WALK-THRU element counts |

G3-DIGEST (spatial hash) requires a reference DB. For GENERATIVE buildings,
there is no reference — DM defines the reference. G3 can be used for
regression testing after the first successful compile.

## 5. The Same YAML, Two Modes

The classify YAML format is identical for extracted and generative buildings.
The only difference is what the Selection Cascade finds when it searches:

### 5.1 Rosetta Stone Mode (EN-BLOC)

```yaml
# classify_sh.yaml — EXTRACTED from IFC
building:
  building_type: Ifc4_SampleHouse
  prefix: SH
  provenance: EXTRACTED
  floor_rooms:
    Ground:
      spaces:
        - { template_bom: ROOM_SH_LI, role: LIVING, aabb_mm: [4645, 2145, 3300] }
```

The aabb_mm matches the extracted BOM exactly. Selection Cascade finds **one**
matching BOM (singularity). EN-BLOC takes the whole tree. The Rosetta Stone
proves the geometry machinery is correct.

### 5.2 Generative Mode (WALK-THRU)

```yaml
# classify_dm.yaml — GENERATIVE, no IFC source
building:
  building_type: DemoHouse_2BR
  prefix: DM
  provenance: GENERATIVE
  floor_rooms:
    Ground:
      spaces:
        - { template_bom: ROOM_DEMO_LI, role: LIVING, aabb_mm: [4000, 3500, 2800] }
```

The aabb_mm is different from any extracted building. Selection Cascade searches
for BOMs with `bom_category=LIVING` and `DocBaseType=RE` that **fit within**
4000×3500×2800. Multiple candidates may match (SH's living room, DX's living
room). The cascade picks the best fit by volume, or the user overrides via
the Designer.

### 5.3 Any Variant

You can even specify a new duplex with different sizing:

```yaml
# classify_mydx.yaml — a custom duplex variant
building:
  building_type: MyDuplex
  prefix: MX
  provenance: GENERATIVE
  doc_base_type: RE
  composition: { type: MIRROR, unit_count: 2 }
  floor_rooms:
    Ground:
      spaces:
        - { template_bom: ROOM_MX_LI, role: LIVING, aabb_mm: [6000, 4000, 3000] }
        - { template_bom: ROOM_MX_BD, role: BEDROOM, aabb_mm: [4000, 3500, 3000] }
```

The cascade finds DX's BOMs (same DocBaseType=RE), checks AABB fit, and places
what fits. A bigger living room gets DX's living layout with space to spare.
A smaller bedroom might not fit DX's bedroom → falls back to SH's bedroom.
The cascade handles it automatically.

### 5.4 Two Paths, One Machine

| | YAML-driven (default) | Designer (visual) |
|---|---|---|
| **Who selects BOMs** | Selection Cascade auto-picks | User browses + clicks |
| **Where decisions live** | C_OrderLine in work_output.db | Same |
| **Promote to BOM** | Same promote path | Same |
| **Compilation** | Same compiler, same gates | Same |
| **Purpose** | Prove WALK-THRU works | Production use |

The YAML-driven path with automatic selection is the **proof of concept** that
the WALK-THRU machinery works. The Designer's visual path is the same mechanism
with human choice replacing the cascade's automatic pick. Both write C_OrderLine.
Both promote to BOM. Both compile through the same 9-stage pipeline and 6 gates.

## 6. AttributeSetInstance — Per-Instance Customization

> **Canonical spec: [BBC.md §3.5.1](BOMBasedCompilation.md) — AttributeSetInstance.**
> This section summarizes the generative application. Full pattern, resolution rule,
> and IsInstanceAttribute taxonomy are defined in BBC.md §3.5.1.
> Field resolution matrix: BIM_Designer.md §8.

The cascade picks the BOM recipe. ASI customizes each instance — like ERP where one
T-shirt SKU has sizes S/M/L/XL. A WALL_EXT_150 is the product; the ASI says
`length_mm=12500, material=BrickPlaster`.

For generative buildings, ASI closes the gap between library BOMs (extracted at
original dimensions) and user-defined rooms (possibly bigger or smaller):

1. User defines room: `LIVING 6000×4500×3000` (bigger than SH original)
2. Cascade finds a matching SET that fits within the slot
3. Each element becomes a C_OrderLine
4. ASI overrides stretch walls to new room dimensions
5. Compiler resolves `effective = ASI ?? catalog` → scales library LOD
6. Same pipeline, same gates. Zero new code.

**Implementation state:** Schema DONE, FK DONE, ChangeSet.ASI DONE. Pending:
WorkOutputDAO ASI read/write, compiler effective_dimension resolution,
M_AttributeSet seed for major product types. See BBC.md §3.5.1 for details.

## 7. Test Cases — OrderLine-Driven Building Customization

> **Designer equivalence:** These test cases apply identically to the YAML-driven
> path and the BIM Designer visual path. Both write C_OrderLine. Both compile through
> the same pipeline. A user clicking "SH + add sprinklers" in the Designer produces
> the same output as a YAML with the same OrderLine + ASI. One engine, two interfaces.

### TC-1: Exact copy — "Give me SH"

```
C_OrderLine:
  M_Product_ID = BUILDING_SH_STD
  (no ASI overrides)

Expected:
  55 elements (identical to Rosetta Stone)
  G1-G6 GREEN
  All categories: LIVING + DINING + MASTER + structural + roof + curtain wall
```

**Witness:** Same as RosettaStoneGateTest for SH. The generative path with zero
overrides must produce the same output as the extracted path.

### TC-2: Drop a room — "SH but no dining room"

```
C_OrderLine:
  M_Product_ID = BUILDING_SH_STD
  ASI: { exclude_category: DINING }

Expected:
  55 - 2 (dining table + chairs) = 53 elements
  Validation: WARN "no dedicated dining area" (advisory, not blocking)
  G1-COUNT: 53
```

### TC-3: Add a room — "SH but add second bedroom"

```
C_OrderLine:
  M_Product_ID = BUILDING_SH_STD
  ASI: { add_category: BEDROOM }

Expected:
  Cascade finds BEDROOM SET (SH_BED_SET or FK_BEDROOM_SET)
  55 + 2 (bed + desk) + 7 structural (walls/door/window/slab) = ~64 elements
  Validation: UBBL min area for new bedroom → PASS or BLOCK
  Tack offsets auto-computed from available space
```

### TC-4: Swap roof type — "SH but pitched roof"

```
C_OrderLine:
  M_Product_ID = BUILDING_SH_STD
  ASI: { roof_type: PITCHED }

Expected:
  Cascade: replace SH_ROOF_STR (flat, 2 elements)
           with FK pitched roof components (IfcRoof + beams + trusses)
  Validation: curtain wall panels that exceed pitched roof envelope
              → WARN "glazing exceeds roof boundary" or auto-trim
  Element count increases (pitched roof has more components than flat)
```

### TC-5: Add discipline — "SH but with sprinklers"

```
C_OrderLine:
  M_Product_ID = BUILDING_SH_STD

C_OrderLine:
  discipline = FP (fire protection)

Expected:
  Engine reads ad_space_type_mep_bom WHERE discipline='FP':
    LIVING:  1 sprinkler per 12m² (NFPA 13) → 1 sprinkler
    BEDROOM: 1 sprinkler                     → 1 sprinkler
    DINING:  1 sprinkler                     → 1 sprinkler (shared with living)
  Cascade: selects sprinkler product from library
  Placement: ceiling center of each room (dx=W/2, dy=D/2, dz=H-50mm)
  55 + 3 sprinklers = 58 elements
  G5-PROVENANCE: all geometry from library (sprinkler has LOD mesh)
```

### TC-6: Resize — "SH but 20% bigger"

```
C_OrderLine:
  M_Product_ID = BUILDING_SH_STD
  ASI: { scale_factor: 1.2 }

Expected:
  Same 55 elements, same products
  All wall lengths scaled by 1.2 (ASI override on each wall OrderLine)
  Room areas increase → UBBL min area still PASS (bigger is easier)
  Furniture positions re-tacked proportionally
  G1-COUNT: 55 (same count, different dimensions)
```

### TC-7: Combine — "SH + pitched roof + sprinklers + extra bedroom"

```
C_OrderLine:
  M_Product_ID = BUILDING_SH_STD
  ASI: { roof_type: PITCHED, add_category: BEDROOM }

C_OrderLine:
  discipline = FP

Expected:
  Base SH (55) + pitched roof swap (~+30) + bedroom (+9) + sprinklers (+4)
  ≈ 98 elements, 8+ IFC classes
  Validation: all rules applied cumulatively
  Same pipeline, same gates. Human input: 2 OrderLines + 2 ASI attributes.
```

### TC-8: Different house type — "FK instead of SH"

```
C_OrderLine:
  M_Product_ID = BUILDING_FK_STD
  (no ASI overrides)

Expected:
  82 elements (identical to FK Rosetta Stone)
  2 storeys, pitched timber roof, 7 rooms
  G1-G6 GREEN
```

**Principle:** Any extracted house type (SH, FK, WB, JS, GH, NI, JE) can be the
starting point. The OrderLine picks the template. ASI customizes it. Validation
ensures the result is compliant. The engine is the same for all.

### Note: Topology Maker — Future Phase

TC-1 through TC-8 cover the immediate generative use cases using **existing library
houses as templates** with OrderLine + ASI customization. No topology maker is needed
for this level of functionality. The user picks a house, customizes via ASI, and the
validation rules ensure the result is compliant.

A topology maker (zone-based layout from functional requirements without a template)
becomes necessary only when the user wants to design from scratch — "3 bedrooms,
2 bathrooms, open plan kitchen-living, double storey" without referencing any existing
house type. At that point, the topology maker generates the room layout, and the
cascade + rules fill it exactly as TC-1 through TC-8 describe.

The Rosetta Stones provide the vocabulary (2,459 products, 7 house types). The
OrderLine + ASI + validation rules provide the grammar. That is sufficient for a
production-ready generative system. The topology maker is the creative writing course
— useful but not required to form valid sentences.

## 8. Witnesses

| ID | Claim | Mechanism |
|----|-------|-----------|
| W-GEN-1 | Selection Cascade finds matching BOMs by category + AABB fit | SelectionCascadeTest (7 sub-witnesses) |
| W-GEN-2 | Compilation produces same element count as selected BOMs predict | G1-COUNT gate |
| W-GEN-3 | All geometry resolves from library (no parametric fallback) | G5-PROVENANCE gate |
| W-GEN-4 | BIMEyes Tier 1 proofs pass on compiled output | EyesProofRunner.proveFromDB() |
| W-GEN-5 | UBBL compliance passes for all rooms | PlacementValidator + AD_Val_Rule |
| W-GEN-6 | Promoted BOM has entity_type='U', provenance='GENERATIVE' | PromoteTest assertions |
| W-GEN-7 | No new code in BuildingCompiler for GENERATIVE path | Same compiler, same gates |
