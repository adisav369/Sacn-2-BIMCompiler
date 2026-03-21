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
User: "Create 2BR house with pitched roof and sprinklers"
         │
         ▼
C_Order created in work_output.db
         │
         ▼
C_OrderLine: M_Product_ID = BUILDING_SH_STD
    (Start with SH as the base — 1 line, not exploded)
         │
         ▼
BOM Drop — user navigates the tree:
    BUILDING_SH_STD
    ├─ SH_GF_STR         (floor structure — keep)
    ├─ FLOOR_SH_GF_STD   (floor rooms — keep)
    │  ├─ SH_LIVING_SET  (keep)
    │  ├─ SH_DINING_SET  (keep)
    │  ├─ SH_BED_SET     (keep)
    │  └─ TOILET_BLOCK   (keep)
    ├─ SH_ROOF_STR       ← SWAP: chooser shows RF products → FK_PITCHED_ROOF
    ├─ SH_CW_STR         (keep)
    └─ FLOOR_SLAB_GF     (keep)
         │
         ▼
Add C_OrderLine: FP sprinkler product
    Validation rules compute per-room placement
         │
         ▼
User reviews in Designer UI (BOM tree navigator = BOM Outliner G-9)
    Can further swap, add, remove, adjust ASI
    All edits are C_OrderLine modifications in work_output.db
         │
         ▼
BuildingCompiler.compile() — processes the order
    Explodes BOM tree (same walker, same tack, same gates)
    Resolves products from component_library.db (same library)
    BIMEyes proofs run on output (same proofs)
    → output.db
```

For TC-1 "Give me SH" — skip the BOM Drop. One C_OrderLine, no changes.
Compile explodes the tree. Instant BOM Drop pattern.

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

## 5. One Engine, Two Interfaces

The same BOM tree processing drives both YAML-automated and Designer-visual paths.
The only difference is who creates the C_OrderLine:

### 5.1 YAML Path (automated)

The classification YAML creates the C_Order + C_OrderLine automatically:

```yaml
# classify_sh.yaml — 1 OrderLine, Instant Drop
building:
  building_type: Ifc4_SampleHouse
  prefix: SH
  provenance: EXTRACTED
```

This creates 1 C_OrderLine referencing BUILDING_SH_STD. No BOM Drop.
Compile explodes the tree → 55 elements.

### 5.2 Designer Path (interactive)

The BIM Designer lets users create C_OrderLines visually:

1. Pick a building product from the catalog (e.g. BUILDING_SH_STD)
2. BOM Drop to navigate and modify the tree
3. Swap/add/remove products at any level
4. Compile the modified order

### 5.3 Both Paths, One Engine

| | YAML-driven | Designer (visual) |
|---|---|---|
| **Who creates OrderLines** | YAML auto-creates | User browses + clicks |
| **BOM Drop** | Not needed (Instant Drop) | Interactive tree navigation |
| **Where decisions live** | C_OrderLine in work_output.db | Same |
| **Compilation** | Same compiler, same gates | Same |

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

**Implementation state (S52b):** Schema DONE, FK DONE, ChangeSet.ASI DONE,
WorkOutputDAO ASI CRUD DONE (9 witnesses GREEN), DesignerAPI autoPopulate/readASI/
updateASI DONE, M_AttributeSet seed for 11 product types DONE. Pending: compiler
effective_dimension resolution (ASI > allocated > catalog). See BBC.md §3.5.1.

## 7. Test Cases — OrderLine-Driven Building Customization

> **Designer equivalence:** These test cases apply identically to the YAML-driven
> path and the BIM Designer visual path. Both write C_OrderLine. Both compile through
> the same pipeline. A user clicking "SH + add sprinklers" in the Designer produces
> the same output as a YAML with the same OrderLine + ASI. One engine, two interfaces.

**Universal flow:** All test cases follow the same path:
C_Order → C_OrderLine (references BOM product) → optional BOM Drop → compile.
TC-1 and TC-8 use Instant Drop (no modifications).
TC-2 through TC-7 use BOM Drop to swap/add/remove.

### TC-1: Instant Drop — "Give me SH"

```
C_Order in work_output.db
C_OrderLine: M_Product_ID = BUILDING_SH_STD
(no BOM Drop, no ASI overrides — Instant Drop)

Compile explodes BOM tree → 55 elements in output.db

Expected:
  55 elements (identical to Rosetta Stone)
  G1-G6 GREEN
  All categories: LIVING + DINING + MASTER + structural + roof + curtain wall
```

**Witness:** `BomDropTest` W-DROP-1..6 (S54). bomDrop("BUILDING_SH_STD") creates
C_Order, auto-explodes BOM tree into C_OrderLine hierarchy, returns BomTreeNode
with 55 leaf elements matching Rosetta Stone SH G1-COUNT. **GREEN (6 witnesses).**

**DocAction lifecycle:** Save(validate) → Approve(new product config) → Complete(compile+viewport).
iDempiere pattern: C_OrderLine references M_Product_ID. IsBOM products explode recursively.
Backend auto-explodes; GUI shows collapsed tree (expand/collapse per node).

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

## 8. Construction Completeness — Beyond Room Interiors

> **Problem:** DemoHouseTest currently generates room interiors only (walls, doors,
> windows, furniture, basic MEP). A real house needs a roof, foundation, wall-roof
> junctions, and building envelope closure. All these products **already exist** in
> component_library.db from extracted buildings. No new code is needed — only BOM
> template authoring via YAML and OrderLine + ASI.

### 8.1 What a Complete Generative House Needs

Three construction layers beyond room interiors:

**Layer A: Envelope (weather-tight shell)**

| Element | IFC Class | Library Source | How it gets into OrderLine |
|---------|-----------|---------------|---------------------------|
| Roof deck (flat) | IfcRoof | SH: `Roof_Flat-4Felt-150Ins-50Scr-150Conc-12Plr` (14.8×7.3×1.7m) | BUILDING-level static child |
| Roof deck (pitched) | IfcSlab | FK: `Dach-1`, `Dach-2` (13×5.5×3.4m each slope) | BUILDING-level, 2 per pitch |
| Roof framing (pitched) | IfcBeam | FK: `Sparren` (rafter 8/80mm, 23 members), `Pfette` (purlin), `First` (ridge) | CLUSTER verb on roof SET |
| Roof insulation | IfcCovering | SC: `dakisolatie` (1.0×0.2×2.0m) | Material layer on roof product |
| Roof weatherproofing | IfcCovering | SC: `dakpan`, SH: 4mm felt membrane | Top layer of roof assembly |
| Roof edge trim / fascia | IfcCovering | SC: `dakopstand`, `zinkwerk`, `dakschroot` | BUILDING-level static children |
| Curtain wall / glazed facade | IfcMember | SH: `Curtain_Wall-Exterior_Glazing` (21 segments) | CLUSTER verb on facade SET |
| Wall cladding / finish | IfcCovering | SC: `gevelisolatie`, `buitenblad` | Material layer on wall product |

**Layer B: Structure (load path)**

| Element | IFC Class | Library Source | How it gets into OrderLine |
|---------|-----------|---------------|---------------------------|
| Foundation slab | IfcSlab | Generic: 150mm concrete slab-on-grade | BUILDING-level, z=-0.15m |
| Strip footings | IfcFooting | Bridge/infra library (8+ products) | Below load-bearing walls |
| Floor beams (2+ storey) | IfcBeam | 63 beam products (steel W-flanges, timber joists) | FLOOR-level static children |
| Columns / posts | IfcColumn | 31 column products (concrete, steel) | Frame-based only |
| Window lintels | IfcBeam | Concrete/steel lintels | Above each opening, verb-driven |

**Layer C: Finishes (livability)**

| Element | IFC Class | Library Source | How it gets into OrderLine |
|---------|-----------|---------------|---------------------------|
| Ceiling finish | IfcCovering | 35+ covering products (gypsum, ACT grid) | Per-room, dz = room height - ceiling thickness |
| Floor finish | IfcCovering | Tile, timber, carpet products | Per-room, dz = 0 |
| Roof skylight | IfcWindow | SC: `dakkoepel` (1.2×1.2×0.5m) | Optional, on roof deck |
| Handrails / guardrails | IfcRailing | Library railing products | Balcony/stair edges |

### 8.2 The Roof-Wall Junction Problem

When a pitched roof meets walls, three things happen:

1. **Walls terminate at top plate height** — wall `allocated_height_mm` = storey height
   (e.g. 2800mm). The wall does NOT extend into the roof triangle. This is already
   correct in FK extraction: walls end at ~2.5m, rafters begin above.

2. **Gable walls follow the roof slope** — end walls that fill the triangle between
   top plate and ridge. These are separate products with a triangular profile.
   FK stores these as regular IfcWall elements with non-rectangular `allocated_height_mm`
   that represents the peak height.

3. **Curtain walls cut at roof line** — SH's curtain wall segments are pre-cut in the
   IFC (authoring tool did the intersection). For generative buildings, the ASI needs
   a `top_trim_mm` attribute or the auto-populate needs to compute per-panel height
   from the roof slope function.

**Resolution for generative path:**

```
BIM_Roof ASI template (new):
  pitch_deg       — 0 (flat) to 45 (steep)
  overhang_mm     — eaves projection beyond wall face
  ridge_direction — ALONG_WIDTH or ALONG_DEPTH
  material        — CONCRETE / TIMBER / METAL

BIM_Wall ASI addition:
  top_trim_mm     — if wall meets pitched roof, trim height at this point
                    (computed from: wall_x_position × tan(pitch_deg))

BIM_GableWall (new ASI template, IsInstanceAttribute=1):
  peak_height_mm  — height at the ridge (above top plate)
  base_width_mm   — width at the top plate
```

**Auto-populate logic for pitched roof:**
```
1. User selects roof_type: PITCHED (or cascade defaults from building template)
2. Auto-populate creates:
   a. 2× roof deck slabs (one per slope) — ASI: {pitch_deg, span_mm}
   b. N× rafters via CLUSTER verb — spacing from FK pattern (600mm o.c.)
   c. 2× purlins (front + back) — ASI: {span_mm = building length}
   d. 1× ridge beam — ASI: {span_mm = building length}
   e. 2× gable walls — ASI: {peak_height_mm, base_width_mm}
3. For each exterior wall touching the roof:
   a. Compute trim height from roof slope at wall position
   b. Set wall ASI: {top_trim_mm}
4. For curtain wall panels:
   a. Compute per-panel height from roof slope function
   b. Set ASI: {height_mm = min(panel_height, roof_z_at_panel_x)}
```

### 8.3 FP Clash Detection During Auto-Populate

Fire protection elements (sprinklers, fire pipes) must not collide with structural
elements or ACMV ductwork. The rules already exist in `AD_Clash_Rule`:

| Rule | Discipline A | Discipline B | Type | Min Distance | Verdict |
|------|-------------|-------------|------|-------------|---------|
| 4 | FP (pipes) | STR (beams/columns) | HARD | 0mm | BLOCK — reroute pipe |
| 6 | FP (sprinkler heads) | ACMV (ducts) | CLEARANCE | 300mm | BLOCK — NFPA 13 obstruction |

**Wiring needed:** After `autoPopulate()` places all elements (structural + MEP),
call `PlacementValidatorImpl.validateBatch()` with all placements to detect clashes.
The validator already implements `checkClearance()` (lines 698-730) for cross-discipline
spatial checks — it just needs to be called from the auto-populate flow.

**Expected auto-populate behavior:**
```
1. Place all room contents (walls, doors, windows, furniture)
2. Place MEP per ad_space_type_mep_bom (sprinklers, lights, outlets)
3. Run validateBatch() — clash detection
4. For each BLOCK clash:
   a. Reposition MEP element (move sprinkler 300mm from duct)
   b. Or flag as WARNING for user review
5. Return AutoPopulateResponse with validationWarnings
```

### 8.4 Validation Rules That Close Gaps

Three rule tables drive gap-filling. All exist, need wiring to auto-populate:

**`ad_space_type_opening`** (103 rules) — "A KITCHEN needs 1 exterior window":
```
After cascade fills room contents, for each room:
  1. Query ad_space_type_opening WHERE space_type_id = room.category
  2. Count existing openings in room's C_OrderLine children
  3. If qty < qty_rule → auto-add missing opening as new C_OrderLine
  4. Select opening product from family_id (e.g. INST_CURTAIN_WINDOW)
  5. Place on specified wall face (placement_wall = 'exterior')
```

**`ad_space_type_mep_bom`** (186 rules) — "A BATHROOM needs 1 exhaust fan + GFCI":
```
After cascade fills room contents, for each room:
  1. Query ad_space_type_mep_bom WHERE space_type_id = room.category
  2. Count existing MEP of each type in room's C_OrderLine children
  3. If qty < qty_normal → auto-add missing MEP as new C_OrderLine
  4. Select product from mep_product_id (e.g. EXHAUST_FAN, OUTLET_GFCI)
  5. Place per placement_rule on host_surface (WALL/CEILING)
```

**`AD_Val_Rule`** (63 rules) — "Window-to-floor area ratio ≥ 10% (UBBL §39)":
```
Rule 110: UBBL_WINDOW_MIN_AREA_RATIO (param: min_ratio=0.10)
  Currently: defined but not evaluated during auto-populate
  Wire: after all openings placed, compute total glazing area vs floor area
        If ratio < 0.10 → WARN "insufficient natural light"
        Or auto-add window to increase ratio
```

### 8.5 Construction Tools Summary — What S53 Needs

| Tool | Exists? | Wire to auto-populate? |
|------|---------|----------------------|
| Selection Cascade (room contents) | YES — `findMatchingSets()` | YES — S52b done |
| ASI authoring (per-instance dims) | YES — `WorkOutputDAO` CRUD | YES — S52b done |
| Opening gap-fill (`ad_space_type_opening`) | DATA exists (103 rules) | NOT WIRED — needs query + insert loop |
| MEP gap-fill (`ad_space_type_mep_bom`) | DATA exists (186 rules) | NOT WIRED — needs query + insert loop |
| FP clash detection (`AD_Clash_Rule`) | CODE exists (`checkClearance()`) | NOT WIRED — needs `validateBatch()` call |
| Glazing ratio check (Rule 110) | DATA exists | NOT WIRED — needs evaluation in approve gate |
| Roof BOM template | PRODUCTS exist in library | NOT AUTHORED — needs YAML + ASI template |
| Foundation BOM template | PRODUCTS exist (8+ footings) | NOT AUTHORED — needs YAML |
| Wall-roof junction | FK extraction shows the pattern | NEEDS: `BIM_Roof` ASI + `top_trim_mm` on walls |
| Ceiling/floor finish | PRODUCTS exist (35+ coverings) | NOT AUTHORED — needs per-room static children |

**Key insight:** The compiler, pipeline, and gates handle all these element types today
(proven by FK, SH, SC, TE extractions). The generative gap is **data authoring** (YAML
templates + ASI attributes), not code. The exception is the `validateBatch()` wiring
for clash detection — that's a small code change to call existing logic.

## 9. Witnesses

| ID | Claim | Status |
|----|-------|--------|
| W-GEN-1 | Selection Cascade finds matching BOMs by category + AABB fit | GREEN (S52, 7 sub-witnesses) |
| W-GEN-2 | Compilation produces same element count as selected BOMs predict | SPEC |
| W-GEN-3 | All geometry resolves from library (no parametric fallback) | SPEC |
| W-GEN-4 | BIMEyes Tier 1 proofs pass on compiled output | SPEC |
| W-GEN-5 | UBBL compliance passes for all rooms | SPEC |
| W-GEN-6 | Promoted BOM has entity_type='U', provenance='GENERATIVE' | SPEC |
| W-GEN-7 | No new code in BuildingCompiler for GENERATIVE path | PROVEN (same compiler) |
| W-DROP-1 | bomDrop creates C_Order + explodes BOM tree into C_OrderLine hierarchy | GREEN (S54) |
| W-DROP-2 | Instant Drop totalElements = 55 (SH Rosetta Stone match) | GREEN (S54) |
| W-DROP-3 | Tree has FLOOR-level children (IsBOM sub-assemblies) | GREEN (S54) |
| W-DROP-4 | FLOOR_SH_GF_STD contains ROOM sub-assemblies with LEAF children | GREEN (S54) |
| W-DROP-5 | Non-BOM product (IsBOM=false) returns error | GREEN (S54) |
| W-DROP-6 | Each node carries bom_category for category-based swap browsing | GREEN (S54) |
| W-ASI-AUTO-1 | autoPopulate fills all empty rooms with cascade matches | GREEN (S52b) |
| W-ASI-AUTO-2 | Auto-populated walls have ASI.length_mm scaled to room width | GREEN (S52b) |
| W-ASI-AUTO-3 | IsInstanceAttribute=0 products have no ASI after auto-populate | GREEN (S52b) |
| W-ASI-READ-1 | readASI returns correct fields for a wall with ASI override | GREEN (S52b) |
| W-ASI-EDIT-1 | updateASI changes single field, effective dimension reflects override | GREEN (S52b) |
| W-GEN-OPEN-1 | Opening gap-fill adds missing doors/windows per ad_space_type_opening | SPEC |
| W-GEN-MEP-1 | MEP gap-fill adds missing fixtures per ad_space_type_mep_bom | SPEC |
| W-GEN-CLASH-1 | FP vs STR clash detected after auto-populate | SPEC |
| W-GEN-ROOF-1 | Pitched roof template places rafters + purlins + ridge via CLUSTER | SPEC |
| W-GEN-TRIM-1 | Wall top_trim_mm computed from roof pitch at wall position | SPEC |
