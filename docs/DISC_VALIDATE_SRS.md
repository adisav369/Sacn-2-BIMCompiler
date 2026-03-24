# Multi-Discipline BOM Design
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [SystemContract](SystemContract.md) · [TestArchitecture](TestArchitecture.md)

## CTFL Review Status (session 33, 2026-03-19)

**Last reviewed:** 2026-03-19 session 34 — CTFL review: §10.4 H3 blocker added,
§10.5 handler witness claims + acceptance criteria added (12 witnesses, 6 auto-fix limits).
**Open:** H1-H6 handlers DESIGNED, NOT IMPLEMENTED. Remaining blockers:
AD_Clash_Rule (0 rows → H2), AD_Val_Rule SPACING (0 rows → H3),
AD_Val_Rule CONTINUITY (0 rows → H5). See §10.4 for full status matrix.

*How discipline-separated BOMs organize extracted buildings and prepare for generative placement*

> **Governing principle:** Each construction discipline (ARC, STR, PLB, ELC, FPR...)
> is a separate BOM sub-tree under its storey. This mirrors real-world drawing sheets
> (A-101, S-101, P-101, E-101) and construction contracts. The BOM structure is
> designed once for both extracted and generative modes — extraction populates
> positions, generative mode populates rules. Same schema, two modes.

---

## 1. Discipline Vocabulary

Derived from Terminal extraction (51,088 elements across 9 disciplines) and
validated against standard construction drawing sheet conventions.

| Code | Discipline | Drawing Sheet | Terminal Count | DX Count | IFC Classes |
|------|-----------|---------------|---------------:|----------:|------------|
| ARC | Architectural | A-series | 34,724 | 183 | IfcWall, IfcDoor, IfcWindow, IfcSlab, IfcFurnishingElement, IfcCovering, IfcRailing, IfcStairFlight, IfcRoof, IfcRampFlight |
| STR | Structural | S-series | 1,429 | 12 | IfcBeam, IfcMember, IfcColumn, IfcSlab*, IfcWall* |
| FP | Fire Protection | FP-series | 6,863 | — | IfcFireSuppressionTerminal, IfcAlarm, IfcPipeSegment*, IfcPipeFitting*, IfcValve*, IfcFlowController* |
| ACMV | Air Conditioning & Mechanical Ventilation | M-series | 1,621 | — | IfcDuctSegment, IfcDuctFitting, IfcAirTerminal |
| CW | Chilled Water | — | 1,431 | — | IfcPipeSegment*, IfcPipeFitting*, IfcFlowTerminal*, IfcValve* |
| ELEC | Electrical | E-series | 1,172 | — | IfcLightFixture, IfcElectricAppliance |
| SP | Sanitary Plumbing | P-series | 979 | — | IfcPipeSegment*, IfcPipeFitting*, IfcFlowTerminal*, IfcValve* |
| LPG | Liquefied Petroleum Gas | — | 209 | — | IfcPipeSegment*, IfcPipeFitting*, IfcValve* |
| REB | Reinforcing Bar | S-series (sub) | 2,660 | — | IfcReinforcingBar |

**\* Shared IFC classes:** IfcPipeSegment, IfcPipeFitting, IfcValve, IfcFlowTerminal
appear in multiple disciplines (FP, CW, SP, LPG). Disambiguation requires
`system_type` from IFC property sets — see §5.

**DX coarse mapping:** DX extraction currently classifies all piping/electrical as
`MEP` (904 elements). Reclassification into PLB/ELC/FPR needed for fine-grained
discipline BOMs. SH has no MEP elements (55 ARC+STR only).

**SH stays flat:** No discipline wrapper for proven single-discipline stone.
RosettaStone integrity preserved as-is for sanity.

---

## 2. BOM Tree Structure — 5 Levels

Mirrors real construction drawing sheet hierarchy: discipline letter + storey number.

```
L0: BUILDING_DX_STD                      (the project)
│
├── DUPLEX_MEP_TRUNK_STD                  (storey-spanning verticals: risers)
│   ├── PLB_RISER_DX                      (plumbing riser)
│   ├── ELC_RISER_DX                      (electrical riser, if any)
│   └── FPR_RISER_DX                      (fire protection riser, if any)
│
├── FLOOR_DX_L1_STD                       (L1: storey)
│   ├── ARC_DX_L1                         (A-101: discipline BOM)
│   │   ├── room SETs (LIVING, KITCHEN…)  (assembly)
│   │   │   └── wall, door, window, furniture (leaves)
│   │   └── structural-arch (slabs, stairs, railings)
│   ├── PLB_DX_L1                         (P-101: plumbing)
│   │   └── pipe runs, fittings, fixtures
│   ├── ELC_DX_L1                         (E-101: electrical)
│   │   └── conduit runs, receptacles, switches, lights
│   ├── FPR_DX_L1                         (FP-101: fire protection)
│   │   └── sprinkler mains, heads, alarms
│   └── STR_DX_L1                         (S-101: structural)
│       └── beams, members
│
└── FLOOR_DX_L2_STD                       (L2: storey)
    ├── ARC_DX_L2                         (A-201)
    ├── PLB_DX_L2                         (P-201)
    ├── ELC_DX_L2                         (E-201)
    ├── FPR_DX_L2                         (FP-201)
    └── STR_DX_L2                         (S-201)
```

**Terminal at scale (same structure):**
```
L0: BUILDING_TE_STD
├── [per storey]
│   ├── ARC_TE_LXX       (~34,724 elements / N storeys)
│   ├── STR_TE_LXX       (~1,429)
│   ├── FP_TE_LXX        (~6,863)
│   ├── ACMV_TE_LXX      (~1,621)
│   ├── CW_TE_LXX        (~1,431)
│   ├── ELEC_TE_LXX      (~1,172)
│   ├── SP_TE_LXX        (~979)
│   ├── LPG_TE_LXX       (~209)
│   └── REB_TE_LXX       (~2,660)
└── [risers / storey-spanning trunks]
```

**Why 5 levels is correct:** A general contractor doesn't hand the whole building
to the electrician. He hands him E-101 (Floor 1 Electrical), E-201 (Floor 2
Electrical), and the riser diagram. Three BOM nodes = three scopes of work. Each
discipline BOM per storey = one drawing sheet = one subcontract scope. The
hierarchy encodes contractual reality, not just element counts. Even FPR with
1 element on a floor gets its own node — fire protection is a separate contract,
separate permit, separate inspection.

**Storey first, then discipline:** The subcontractor works one floor at a time
within his trade. He completes Floor 1 electrical, then moves up. He doesn't
install all conduits in the building top-to-bottom. Exception: risers
(vertical trunks spanning storeys) sit at BUILDING level, not under any floor.

---

## 3. M_BomCategory as Discipline Classifier

`M_BomCategory` (= iDempiere M_Product_Category) serves as the shared catalog
classifier. Discipline codes become new bom_category values:

| bom_category | Current Use | New Use |
|---|---|---|
| GF, L1, L2, RF, FN, MS, CW | Storey codes | Unchanged |
| LI, BD, KT, FR, RE | Room/functional types | Unchanged |
| ST | Buffer space | Unchanged |
| HU | Half-unit | Unchanged |
| **ARC** | — | Architectural discipline BOM |
| **STR** | — | Structural discipline BOM |
| **PLB** | — | Plumbing discipline BOM |
| **ELC** | — | Electrical discipline BOM |
| **FPR** | — | Fire Protection discipline BOM |
| **ACMV** | — | HVAC/Mechanical discipline BOM |
| **SP** | — | Sanitary Plumbing discipline BOM |
| **LPG** | — | LPG discipline BOM |
| **REB** | — | Reinforcing Bar discipline BOM |

The discipline lives on `m_bom.bom_category`, NOT on M_Product. A product is
just a product (pipe, conduit, sprinkler head). Which discipline it belongs to
is determined by which discipline BOM tree it sits in. Same product can appear
in multiple discipline BOMs — same screw in 50 BOMs (pure iDempiere pattern).

---

## 4. Two Classifiers on M_Product — Independent Dimensions

iDempiere teaches us two orthogonal classifiers:

| Classifier | Lives On | What It Means | Example |
|---|---|---|---|
| **M_BomCategory** | `m_bom` (the BOM node) | Organizational grouping — which trade | PLB, ELC, FPR |
| **M_AttributeSet** | `M_Product` (the leaf) | Engineering behavior — how to parameterize | BIM_Pipe, BIM_Wall |

They correlate but don't collapse:
- A `BIM_Pipe` product lives inside a PLB discipline BOM — but could also appear
  in FPR (fire protection pipes use same fittings).
- A `BIM_Component` product (smoke detector) is discipline-agnostic at the product
  level — it becomes FPR by living under a FPR discipline BOM.

**M_AttributeSet is for generative mode** — when a user selects a wall variant
(material, thickness) like iDempiere GardenWorld's T-shirt (Size S/M/L × Color
Red/Blue). For extracted RosettaStones, there's no selection moment — the IFC
file already decided. AttributeSet columns stay NULL during extraction.

Five attribute sets (§11.38, designed for generative future):

| M_AttributeSet | IsInstance | Generative Role |
|---|---|---|
| `BIM_Pipe` | 1 (length varies) | Cross-section stamp; length = instance attribute |
| `BIM_Conduit` | 1 (length varies) | Same pattern as pipe |
| `BIM_Wall` | 1 (height varies) | Thickness stamp; height/length = instance |
| `BIM_Slab` | 1 (area varies) | Thickness stamp; area = instance |
| `BIM_Component` | 0 (identical) | Every instance is the same (qty on BOM line) |

---

## 5. YAML Schema v2 — Discipline Classification

### 5.1 Structure

```yaml
schema_version: 2

building:
  # ... existing fields (building_type, prefix, storeys, etc.) ...

  disciplines:
    ARC:
      ifc_classes: [IfcWall, IfcDoor, IfcWindow, IfcSlab,
                    IfcFurnishingElement, IfcCovering,
                    IfcRailing, IfcStairFlight, IfcRoof, IfcRampFlight]
      bom_category: ARC

    STR:
      ifc_classes: [IfcBeam, IfcMember, IfcColumn, IfcPlate]
      bom_category: STR

    PLB:
      ifc_classes: [IfcPipeSegment, IfcPipeFitting, IfcFlowTerminal, IfcValve]
      filter: { system_type: [Domestic Cold Water, Domestic Hot Water,
                Sanitary, Waste, Hydronic Supply, Hydronic Return] }
      bom_category: PLB

    ELC:
      ifc_classes: [IfcLightFixture, IfcElectricAppliance,
                    IfcFlowSegment, IfcFlowTerminal, IfcFlowController]
      filter: { system_type: [Electrical, Telecom, Lightning] }
      bom_category: ELC

    FPR:
      ifc_classes: [IfcFireSuppressionTerminal, IfcAlarm, IfcSensor,
                    IfcPipeSegment, IfcPipeFitting, IfcValve, IfcFlowController]
      filter: { system_type: [Fire Alarm, Sprinkler, Fire Suppression] }
      bom_category: FPR

    ACMV:
      ifc_classes: [IfcDuctSegment, IfcDuctFitting, IfcAirTerminal]
      bom_category: ACMV

    SP:
      ifc_classes: [IfcPipeSegment, IfcPipeFitting, IfcFlowTerminal, IfcValve]
      filter: { system_type: [Sanitary Waste, Storm Drain, Vent] }
      bom_category: SP

    LPG:
      ifc_classes: [IfcPipeSegment, IfcPipeFitting, IfcValve]
      filter: { system_type: [LPG, Gas] }
      bom_category: LPG

    REB:
      ifc_classes: [IfcReinforcingBar]
      bom_category: REB
```

### 5.2 Disambiguation via system_type Filter

Some IFC classes (IfcPipeSegment, IfcPipeFitting, IfcValve) appear across
multiple disciplines. The `filter.system_type` disambiguates using IFC property
set data (Pset_DistributionSystemCommon.PredefinedType or similar).

**Prerequisite:** The extraction pipeline must capture system_type from IFC
property sets into `I_Element_Extraction`. This column does not exist yet.
Until it does, DX MEP elements can be classified by `element_ref` string
parsing (e.g., "Domestic Cold Water" in the Revit type name) as an interim
heuristic.

### 5.3 Schema v1 Backward Compatibility

Buildings without a `disciplines:` key (schema_version 1, e.g. classify_sh.yaml)
default to single-discipline ARC — no discipline BOM level inserted. SH
RosettaStone is untouched.

---

## 6. Extracted vs Generative — One Schema, Two Modes

The BOM model is designed once. Extraction populates positions. Generative mode
(future) populates rules. Same columns, different fill patterns:

| Column | Extracted (current) | Generative (future) |
|---|---|---|
| `m_bom_line.dx/dy/dz` | Copied from IFC (parent-relative) | Computed from rules + AABB |
| `m_bom_line.layout_strategy` | NULL | GRID, CEILING_RUN, AXIS_ALIGNED |
| `m_bom_line.z_rule` | NULL | CEILING_OFFSET, WALL_HEIGHT, FLOOR_EMBED |
| `m_bom_line.anchor_face` | NULL | TOP, WALL, FLOOR |
| `m_bom_line.qty` | 1 (one line per element) | N (rule expands to N instances) |
| `m_attribute` regulation params | NULL | max_spacing, coverage_area, regulation_ref |

### 6.1 Generative Pattern Grammars (future reference)

Three discipline families produce three fundamentally different placement patterns:

**ARC = Containment (nested boxes)**
Room enclosure: walls forming polygon + openings + furniture placed within AABB.
Already proven in SH. Pattern = spatial arrangement within a bounding box.

**STR = Grid repetition (parametric spacing)**
Column-beam grid at regular intervals. BOM captures one bay; qty + parametric
offset reproduces the grid.

**MEP = Topology (directed connection graph)**
Pipe/conduit runs: source → segment → fitting → segment → terminal device.
Pattern = directed connection chain. Tack I/O captures which port connects where.

Examples (illustrative, not current scope):
- **Sprinkler grid:** NFPA 13 Light Hazard → max 4.6m spacing, 2.3m from wall,
  200mm below ceiling. Given room AABB, compiler produces head positions.
- **Conduit ceiling run:** NEC-compliant axis-aligned routing, 25mm below slab
  soffit, 90° turns only, hanger every 1500mm.

These rules slot into the existing `m_bom_line` columns (`layout_strategy`,
`z_rule`, `anchor_face`) and `m_attribute` overflow table without schema changes.

### 6.2 Tack I/O for MEP Topology (future)

Current `m_bom_line` has placement (dx/dy/dz) but not explicit connection ports.
For ARC containment, position is sufficient. For MEP topology, the walker needs
to know which port of a tee connects to which downstream pipe.

Future columns or m_attribute entries:
- `tack_in` — upstream connection port identifier
- `tack_out` — downstream connection port identifier(s)
- `connection_type` — rigid, flexible, sealed, open

Not needed for extraction (positions are explicit). Required for generative MEP
routing where the compiler must chain segments through fittings.

---

## 7. Implementation Sequence

### Phase 1: DX Discipline Reclassification
1. Reclassify DX's 904 `MEP` elements into PLB/ELC/FPR using `element_ref` heuristic
2. Update `I_Element_Extraction.discipline` for DX elements
3. Verify counts: PLB + ELC + FPR = 904

### Phase 2: DX Discipline BOM Generation
1. Update `classify_dx.yaml` to schema_version 2 with `disciplines:` map
2. Extend IFCtoBOM pipeline to read discipline map and create per-storey
   discipline BOM nodes (ARC_DX_L1, PLB_DX_L1, ELC_DX_L1, etc.)
3. Generate `DX_BOM.db` with discipline-organized BOM tree
4. Verify: BOM walk reproduces all 1099 elements

### Phase 3: Compilation Pipeline
1. BOM walker traverses discipline BOM level transparently (it's just another
   m_bom node — no walker changes needed if the tree is correct)
2. Verify: SH compilation unchanged (no discipline level in SH BOM)
3. Verify: DX compilation produces same 1099 elements via discipline BOMs
4. G1-G6 gates GREEN for both SH and DX

### Phase 4: Terminal Discipline Structure (Phase B scope)
1. Terminal already has 9 disciplines in `I_Element_Extraction.discipline`
2. Assign storey values (currently "Unknown" for all Terminal elements)
3. Create `classify_te.yaml` schema_version 2
4. Generate `TE_BOM.db` with 9 discipline sub-trees per storey
5. Scale proof: 51,088 elements organized into ~N×9 discipline BOMs

### Future: Generative Extension
1. Add `system_type` column to `I_Element_Extraction` (from IFC property sets)
2. Populate `layout_strategy`, `z_rule`, `anchor_face` on m_bom_line
3. Populate `m_attribute` with regulation parameters (spacing, clearance, code ref)
4. Compiler reads rules + AABB → produces positions (instead of copying from IFC)
5. Tack I/O for MEP connection topology

---

## 8. Design Decisions

**D1: Storey first, discipline second.**
Matches real construction sequencing — subcontractors work one floor at a time
within their trade. Exception: risers (storey-spanning verticals) sit at
BUILDING level.

**D2: Fine discipline split even for small counts.**
FPR with 1 element on a floor still gets its own BOM node. Fire protection is
a separate contract, separate permit, separate inspection. The BOM encodes
contractual reality, not element count.

**D3: SH stays flat.**
No discipline wrapper for proven single-discipline RosettaStone. Schema v1
backward compatibility — buildings without `disciplines:` key default to
single-discipline, no extra BOM level.

**D4: Discipline on BOM, not on Product.**
`m_bom.bom_category` carries the discipline. `M_Product` is discipline-agnostic.
Same product can appear in multiple discipline BOMs (iDempiere pattern: same
screw in 50 BOMs).

**D5: AttributeSet is for generative mode.**
M_AttributeSet on M_Product defines product variant behavior (pipe length varies,
component is identical). Irrelevant for extraction — relevant when the compiler
generates placements from rules instead of copying from IFC.

**D6: One schema, two modes.**
m_bom_line columns (layout_strategy, z_rule, anchor_face) and m_attribute
regulation params stay NULL for extraction, get populated for generative mode.
No schema changes when transitioning from extraction to generation.

---

## 9. LOD Assembly — Library Geometry Only

**No parametric mesh in pipeline** (feedback_no_parametric.md). Every element
gets its geometry from component_library.db:

```
C_OrderLine.family_ref (product_id)
  → M_Product (width, depth, height, ifc_class)
  → component_definitions (attachment_face, orientation, geometry_hash)
  → component_geometries (vertices, faces, normals via geometry_hash)

ASI overrides instance sizing:
  M_AttributeSetInstance → M_AttributeInstance (width_mm, depth_mm, height_mm)
  ASI controls scaling — library LOD provides base shape.
```

### 9.1 DocEvent LOD Resolution Chain

When `YAML.discipline = DocEvent`, the engine resolves default products per
space type and area via a 5-table chain spanning disc_validation.db (steps 1-3)
and component_library.db (steps 4-5):

```
DocEvent processIt() for discipline FP in a BEDROOM (12m²):

Step 1: ad_space_type_mep_bom
  WHERE space_type_id = 'BEDROOM' AND mep_product_id = 'SPRINKLER'
  → qty_normal = 0  (use per_area instead)
  → per_area_normal = 0.07/m²  → qty = ceil(12 × 0.07) = 1
  → placement_rule = 'CEILING_GRID'
  → host_surface = 'CEILING'
  → building_code = 'NFPA 13', code_clause = '8.5.5'

Step 2: ad_element_mep
  WHERE element_type = 'SPRINKLER'
  → ifc_class = 'IfcFireSuppressionTerminal'
  → discipline = 'FP'
  → host_type = 'CEILING'
  → ports = [{"id":"IN","size":0.015}]
  → code_ref = 'NFPA 13'

Step 3: ad_fp_coverage (FP-specific spacing)
  WHERE hazard_class = 'LIGHT'  (from YAML occupancy_class or project default)
  → max_spacing_m = 4.6, min_spacing_m = 1.8
  → wall_distance_m = 2.3
  → max_coverage_m2 = 18.6
  → k_factor = 5.6

Step 4: M_Product (actual product with dimensions)
  Resolved via ad_element_mep_alias cascade (DISC_VALIDATION_DB_SRS.md §5.1):
    P1: ifc_class match → P2: predefined_type → P3: type_class → P4: element_name LIKE
  → product_id, width, depth, height
  → Multiple products may match — select by building_type affinity
    or closest dimensions to room context

Step 5: component_definitions → component_geometries (LOD mesh)
  WHERE name LIKE '%sprinkler%' (matched via product_id → geometry_hash)
  → attachment_face = 'TOP' (pendant) or 'BOTTOM' (upright)
  → orientation = 'PENDANT' or 'UPRIGHT'
  → geometry_hash → vertices, faces, normals (actual mesh)
```

### 9.2 Metadata Tables — What Each Contributes

| Table | Role | Key Columns | Drives |
|-------|------|------------|--------|
| `ad_space_type_mep_bom` | Default product schedule per room type | space_type_id, mep_product_id, qty_normal, per_area_normal, placement_rule, host_surface, building_code | HOW MANY + WHERE |
| `ad_element_mep` | Logical MEP element definition | element_type, ifc_class, discipline, host_type, ports, mount_height, clearance | WHAT (ifc_class + ports) |
| `ad_fp_coverage` | FP-specific coverage by hazard class | hazard_class, max_spacing_m, max_coverage_m2, wall_distance_m, k_factor | SPACING (FP only) |
| `M_Product` | Physical product with dimensions | product_id, width, depth, height, ifc_class | DIMENSIONS |
| `component_definitions` | LOD geometry link + attachment convention | name, geometry_hash, attachment_face, orientation | LOD MESH + ATTACHMENT |
| `component_geometries` | Actual mesh data | geometry_hash, vertices, faces, normals | GEOMETRY |

### 9.3 Quantity Resolution — Fixed vs Per-Area

| Mode | When | Formula | Example |
|------|------|---------|---------|
| **Fixed qty** | `qty_normal > 0 AND per_area_normal = 0` | `qty = qty_normal` | BATHROOM/TOILET = 1, KITCHEN/SINK = 1 |
| **Per-area** | `per_area_normal > 0` | `qty = ceil(room_area_m2 × per_area_normal)` | OFFICE/SPRINKLER = ceil(50 × 0.07) = 4 |
| **Both zero** | `qty_normal = 0 AND per_area_normal = 0` | Not placed in this room type | CORRIDOR/OUTLET = 0 |

### 9.4 Placement Rule → Verb Mapping

| Placement Rule | Verb | Position Computation |
|---------------|------|---------------------|
| `CEILING_CENTER` | PLACE | Room centroid, at ceiling Z |
| `CEILING_GRID` | TILE / ALONG | Grid at spacing from ad_fp_coverage or AD_Val_Rule |
| `WALL_ENTRY` | PLACE | Near door, at mount_height from ad_element_mep |
| `WALL_SPACED` | ALONG | Evenly spaced along longest wall |
| `WALL_BACK` | PLACE | Against back wall, at mount_height |
| `WALL_SINK` | PLACE | Adjacent to sink, at mount_height |
| `WALL_COOKER` | PLACE | Above cooker position |
| `WALL_HIGH` | MOUNT | High on wall (AC point: ~2.3m) |
| `COUNTER_BACK` | PLACE | At counter height, against wall |
| `FLOOR_LOW` | PLACE | Floor level, low point for drainage |
| `AUTO` | *(engine decides)* | Fallback — centroid or grid based on qty |

### 9.5 Coverage by Space Type (current seed data)

| Space Type | SPRINKLER | LIGHT | OUTLET | EXHAUST | DIFFUSER | Code Base |
|-----------|-----------|-------|--------|---------|----------|-----------|
| BEDROOM | 0.07/m² | 1 | 3 | — | 1 | NFPA 13 + NEC + MS1525 |
| BATHROOM | 1 | 1 | 1 GFCI | 1 | — | NFPA 13 + NEC + IMC |
| KITCHEN | 1 | 2 | 3×20A + 2 GFCI | 1 | 1 | NFPA 13 + NEC + IMC |
| LIVING | 0.07/m² | 2 | 4 | — | 1 | NFPA 13 + NEC + MS1525 |
| OFFICE | 0.07/m² | 0.1/m² | 0.2/m² | — | 1 | NFPA 13 + NEC |
| CORRIDOR | 0.05/m² | 1 | — | — | 1 | NFPA 13 + NEC |
| ASSEMBLY_HALL | 0.07/m² | 0.1/m² | 6 | — | 1 | NFPA 13 + NEC |

Metadata-driven. Adding a new space type or changing a product count = SQL
UPDATE, no Java change.

---

## 10. Post-Placement Handlers — Connectivity, Clash, Completeness

After elements are placed (by either `{prefix}_BOM` pipeline or DocEvent),
handler routines run to ensure the placed elements form a valid construction
system. These are iDempiere `ModelValidator.afterSave()` equivalents — they
fire after each placement batch, not per-element.

### 10.1 Handler Cascade

```
Elements placed (from BOM pipeline or DocEvent)
  │
  ├── H1: CONNECTIVITY handler
  │   │  Ensures every terminal connects to a source via fittings/pipes.
  │   │
  │   │  IFC GROUPING SEMANTICS: IFC's IfcRelConnectsPortToElement and
  │   │  IfcDistributionPort define system topology. The BOM tree mirrors
  │   │  this: DISCIPLINE BOM → RISER (parent) → BRANCH (child) → HEAD
  │   │  (leaf). Parent-child in the BOM IS the connection graph. A
  │   │  disconnected element = orphan node with no parent in its
  │   │  discipline sub-tree. ad_assembly_connector.connects_to names
  │   │  the system network (PLUMBING_STACK, FP_MAIN, etc.) — the BOM
  │   │  tree path from leaf to root must traverse these system names.
  │   │
  │   │  For FP:  BFS from riser → every sprinkler head reachable?
  │   │  For CW:  BFS from supply → every fixture has supply_in?
  │   │  For SP:  BFS from stack → every fixture has waste_out?
  │   │  For ELEC: BFS from panel → every outlet/light has circuit?
  │   │
  │   │  Uses: ad_assembly_connector.connects_to (system graph)
  │   │        ad_element_mep.ports (port definitions)
  │   │        BOM tree Parent_OrderLine_ID (containment = connectivity)
  │   │
  │   │  If disconnected:
  │   │    → Auto-insert connecting pipe/conduit segments (ROUTE verb)
  │   │    → Or flag WARN: "sprinkler_23 unreachable from riser"
  │   │
  │   └── Writes: PP_Order_Node (verb_ref='CONNECT FITTINGS')
  │               W_Validation_Result (tier=1, CONNECTIVITY check)
  │
  ├── H2: NON-CLASH handler
  │   │  Ensures no hard/soft clashes between placed elements and
  │   │  elements from OTHER disciplines already in the BOM.
  │   │
  │   │  For each newly placed element:
  │   │    → ERP-maths clearance against all elements on same storey
  │   │      clearance = centroid_dist - radius_a - radius_b
  │   │    → Check AD_Clash_Rule for discipline pair
  │   │
  │   │  Uses: AD_Clash_Rule (discipline pairs, clash_type, min_distance_mm)
  │   │        M_Product dimensions (cross-section radii)
  │   │
  │   │  If clash detected:
  │   │    → HARD clash: nudge element to nearest clear position
  │   │    → SOFT clash: flag WARN with clearance value
  │   │    → MATERIAL clash: flag WARN with resolution_note
  │   │      ("Add fire collar at penetration point")
  │   │
  │   └── Writes: W_Validation_Result (tier=2, CLASH check)
  │               C_OrderLine.dx/dy/dz updated if nudged
  │
  ├── H3: SPACING COMPLIANCE handler
  │   │  Ensures placed elements meet code spacing requirements.
  │   │
  │   │  For FP:  NN distance between heads ∈ [min_spacing, max_spacing]
  │   │           Wall distance ≥ wall_distance_m (ad_fp_coverage)
  │   │  For ELEC: NN distance between fixtures ≤ max_spacing_mm
  │   │  For outlets: NEC 210.52 — max 1.8m from any point on wall
  │   │
  │   │  Uses: AD_Val_Rule (NFPA13_LH_SPACING, IES_LIGHT_SPACING, etc.)
  │   │        ad_fp_coverage (hazard-class-specific thresholds)
  │   │
  │   │  If spacing violated:
  │   │    → Adjust grid pitch to comply (recalc from processIt formula)
  │   │    → Or insert additional element to fill gap
  │   │    → Flag WARN if adjustment exceeds ±10% of typical_spacing
  │   │
  │   └── Writes: W_Validation_Result (tier=1, SPACING check)
  │
  ├── H4: HOST ATTACHMENT handler
  │   │  Ensures every placed element has a valid host surface.
  │   │
  │   │  IFC GROUPING SEMANTICS: IFC's IfcRelVoidsElement (openings in
  │   │  walls) and IfcRelFillsElement (doors/windows filling voids)
  │   │  encode host relationships explicitly. For MEP, IFC uses
  │   │  IfcRelContainedInSpatialStructure to place elements within
  │   │  their spatial container (storey/room). The BOM tree mirrors
  │   │  this: a CEILING-hosted element sits under a FLOOR node whose
  │   │  AABB defines the ceiling surface. A WALL-hosted element sits
  │   │  under a ROOM node with ad_wall_face defining available faces.
  │   │  Host existence = parent node + face existence in the BOM tree.
  │   │
  │   │  CEILING elements: parent FLOOR node has slab (AABB height > 0)
  │   │  WALL elements: parent ROOM node has ad_wall_face for that face
  │   │  FLOOR elements: parent FLOOR node has ground slab
  │   │
  │   │  Uses: ad_element_mep.host_type
  │   │        ad_space_type_mep_bom.host_surface
  │   │        ad_wall_face (room boundary faces)
  │   │        placement_rules (offset_from_host)
  │   │        BOM tree AABB (parent node dimensions)
  │   │
  │   │  If host missing:
  │   │    → Flag WARN: "light_01 has no ceiling host at Z=2.8m"
  │   │    → Snap to nearest valid host surface
  │   │
  │   └── Writes: W_Validation_Result (tier=1, HOST check)
  │
  ├── H5: VERTICAL CONTINUITY handler
  │   │  For risers/stacks that span storeys: verify X,Y alignment.
  │   │
  │   │  IFC GROUPING SEMANTICS: IFC's spatial containment hierarchy
  │   │  (IfcRelContainedInSpatialStructure, IfcRelAggregates) encodes
  │   │  the storey relationship. When elements sit under their correct
  │   │  IfcBuildingStorey, the absolute Z is inherited from the storey
  │   │  elevation. The BOM tree mirrors this exactly:
  │   │    BUILDING → FLOOR (dz = storey elevation) → DISCIPLINE → LEAF
  │   │  Tack dz on each node is RELATIVE to parent (BBC.md §4).
  │   │  World Z = sum of dz up the tree. This means vertical alignment
  │   │  is a TREE STRUCTURE concern, not a raw coordinate concern.
  │   │
  │   │  What H5 actually checks:
  │   │    → Riser elements exist under correct storey nodes
  │   │    → Same riser's dx/dy is IDENTICAL across storey nodes
  │   │      (only dz differs — inherited from parent FLOOR node)
  │   │    → If dx/dy differs across storeys → tree is malformed
  │   │
  │   │  Uses: AD_Val_Rule WHERE rule_type='CONTINUITY'
  │   │        max_xy_drift_mm per discipline
  │   │        BOM tree parent hierarchy (Parent_OrderLine_ID)
  │   │
  │   │  If drift detected:
  │   │    → Snap riser segment dx/dy to match storey below
  │   │    → Flag WARN if drift > tolerance
  │   │    → Root cause is almost always REPARENT to wrong node
  │   │
  │   └── Writes: W_Validation_Result (tier=3, CONTINUITY check)
  │
  └── H6: COMPLETENESS handler
      │  Ensures the room has all required MEP elements per
      │  ad_space_type_mep_bom schedule.
      │
      │  IFC GROUPING SEMANTICS: IFC's IfcRelAggregates decomposes a
      │  spatial element into its parts. A room (IfcSpace) aggregates
      │  all elements within it. The BOM tree mirrors this: ROOM node
      │  contains all LEAF children. Completeness = counting children
      │  of a ROOM node by mep_product_id and comparing to the schedule.
      │  The spatial container IS the count boundary — no spatial query
      │  needed, just a tree walk of C_OrderLine children.
      │
      │  For each space_type_id × mep_product_id row:
      │    → Count C_OrderLine children under ROOM node
      │      WHERE family_ref resolves to matching mep_product_id
      │    → Compare to qty_normal (or ceil(area × per_area_normal))
      │    → If count < required: flag WARN with missing items
      │
      │  Uses: ad_space_type_mep_bom (the schedule)
      │        BOM tree Parent_OrderLine_ID (room containment)
      │        bom_category on ROOM node (space_type lookup)
      │
      │  Example: BATHROOM missing EXHAUST_FAN
      │    → WARN: "BATHROOM requires 1 EXHAUST_FAN per IMC 2021 403.3"
      │
      └── Writes: W_Validation_Result (tier=1, COMPLETENESS check)
```

### 10.2 Handler Summary

**Common handlers** fire for ALL disciplines — both `{prefix}_BOM` and `DocEvent`.
They are shared cross-cutting checks, like iDempiere's tax calculation that
fires on every invoice line regardless of product category.

| Handler | What | Common? | Fires After | Auto-Fix | Tier |
|---------|------|---------|-------------|----------|------|
| **H1 CONNECTIVITY** | Every terminal reachable from source | **COMMON** | Each discipline batch | Insert connecting segments | 1 |
| **H2 NON-CLASH** | No hard/soft clashes across disciplines | **COMMON** | Each discipline batch | Nudge to clear position | 2 |
| H3 SPACING | Code-compliant spacing (NFPA, NEC, IES) | DocEvent only | FP, ELEC batches | Adjust grid pitch | 1 |
| **H4 HOST ATTACHMENT** | Every element has valid host surface | **COMMON** | Each discipline batch | Snap to nearest host | 1 |
| **H5 VERTICAL CONTINUITY** | Risers/stacks aligned across storeys | **COMMON** | After all storeys | Snap to match below | 3 |
| **H6 COMPLETENESS** | Room has all required MEP per schedule | **COMMON** | After all disciplines | Flag missing items | 1 |

H1, H2, H4, H5, H6 are **common** — they validate structural integrity
regardless of how elements arrived (BOM pipeline or DocEvent). A pipe from
`{prefix}_BOM` needs connectivity just as much as one placed by DocEvent.

H3 (SPACING) is DocEvent-only because `{prefix}_BOM` elements already have
their spacing baked into the BOM tack positions from extraction. DocEvent
computes spacing from rules, so it needs the compliance check.

### 10.3 Handler → Verb → Metadata Linkage

| Handler | Verb Used | Metadata Read |
|---------|----------|--------------|
| H1 | `CONNECT FITTINGS`, `JOIN` | `ad_assembly_connector`, `ad_element_mep.ports` |
| H2 | `CHECK CLASH` | `AD_Clash_Rule`, `M_Product` (cross-section) |
| H3 | `CHECK PLACEMENT` | `AD_Val_Rule`, `ad_fp_coverage` |
| H4 | `ATTACH`, `MOUNT`, `HANG` | `placement_rules`, `component_definitions.attachment_face` |
| H5 | *(vertical check)* | `AD_Val_Rule WHERE rule_type='CONTINUITY'` |
| H6 | *(schedule audit)* | `ad_space_type_mep_bom` |

All handlers write to `W_Validation_Result` in work_output.db. The ambient
compliance strip in BIM Designer reads these results to show live status.
Handlers that auto-fix also update `C_OrderLine.dx/dy/dz` (nudge/snap).

### 10.4 Implementation Status & Preconditions

**Status (session 34):** H1-H6 handlers are **DESIGNED, NOT IMPLEMENTED**.
Zero handler code exists. The cascade above is the target specification.

**Metadata table readiness (disc_validation.db created session 33):**

| Table | DB | Status | Seeded? | Blocks |
|-------|-----|--------|---------|--------|
| `ad_space_type_mep_bom` | disc_validation.db | CREATED (DV001+DV002) | YES — 186 rows (41 space types × 12 MEP products) | H6 |
| `ad_element_mep` | disc_validation.db | CREATED (DV001+DV002) | YES — 12 element types | H1, H4 |
| `ad_element_mep_alias` | disc_validation.db | CREATED (DV003) | YES — 84 alias rows (4-tier IFC cascade) | H1, H4 |
| `ad_fp_coverage` | disc_validation.db | CREATED (DV001+DV002) | YES — 4 hazard classes | H1, H3 |
| `ad_assembly_connector` | disc_validation.db | CREATED (DV001+DV002) | YES — 10 connector rows | H1 |
| `ad_wall_face` | disc_validation.db | CREATED (DV001+DV002) | YES — 204 rows | H4 |
| `placement_rules` | disc_validation.db | CREATED (DV001+DV002) | YES — 4801 rows | H4 |
| `AD_Clash_Rule` | validation.db | **SCHEMA ONLY** | NO — 0 rows | **H2 blocked** |
| `AD_Val_Rule` (SPACING) | validation.db | **SCHEMA ONLY** | NO — 0 rows of type SPACING | **H3 blocked** |
| `AD_Val_Rule` (CONTINUITY) | validation.db | **SCHEMA ONLY** | NO — 0 rows of type CONTINUITY | **H5 blocked** |

**H3 SPACING blocked:** H3 requires both `ad_fp_coverage` (seeded, 4 rows) AND
`AD_Val_Rule WHERE rule_type='SPACING'` (0 rows). The coverage thresholds exist
but the rule engine entry to trigger H3 does not. H3 fires DocEvent-only because
`{prefix}_BOM` elements have spacing baked into tack positions from extraction.

**Precondition for TE validation:** CLUSTER verb fidelity must improve before
handlers can produce meaningful results. Current 29mm max positional error
(improved from 29m after F3 sort fix, session 34) may still generate
false-positive clash/connectivity/spacing violations at tight tolerances.
Handler implementation should follow CLUSTER→exact verb promotion.

**Precondition for generative (DocEvent) validation:** All metadata tables
above must be seeded. H3 SPACING requires `ad_fp_coverage` + `AD_Val_Rule`
rows. H2 NON-CLASH requires `AD_Clash_Rule` discipline-pair rows.

### 10.5 Handler Witness Claims

Each handler requires witness claims BEFORE implementation (CTFL best practice).
Implementation is BLOCKED until these claims are written as `@Test` methods.

| Witness | Handler | What it Proves | Acceptance Criteria |
|---------|---------|---------------|-------------------|
| W-H1-CONNECT-1 | H1 | Every FP head reachable from riser via BOM tree path | BFS from riser node reaches all IfcFireSuppressionTerminal leaves. Path length ≤ 50 nodes. |
| W-H1-CONNECT-2 | H1 | Disconnected element flagged WARN | Orphan leaf with no parent in discipline sub-tree → W_Validation_Result(tier=1, result='WARN'). |
| W-H2-CLASH-1 | H2 | No hard clash between FP and ELEC on same storey | ERP-maths clearance ≥ AD_Clash_Rule.min_distance_mm for all FP×ELEC pairs. |
| W-H2-CLASH-2 | H2 | Clash detected and nudged | Element moved to nearest clear position. C_OrderLine.dx/dy/dz updated. W_Validation_Result(tier=2, result='WARN'). |
| W-H3-SPACING-1 | H3 | FP NN spacing within [min, max] | All head pairs ≥ min_spacing_m AND ≤ max_spacing_m from ad_fp_coverage. |
| W-H3-SPACING-2 | H3 | Wall distance ≥ wall_distance_m | Every head ≥ wall_distance_m from nearest wall face (ad_fp_coverage). |
| W-H4-HOST-1 | H4 | Ceiling element has valid host | Parent FLOOR node has slab (AABB height > 0). ad_element_mep.host_type = 'CEILING' matches. |
| W-H4-HOST-2 | H4 | Missing host flagged WARN | Element with no valid host surface → W_Validation_Result(tier=1, result='WARN'). |
| W-H5-CONT-1 | H5 | Riser X,Y identical across storeys | Same riser's dx,dy values differ ≤ max_xy_drift_mm across all FLOOR nodes. |
| W-H5-CONT-2 | H5 | XY drift flagged WARN | Riser with dx drift > max_xy_drift_mm → W_Validation_Result(tier=3, result='WARN'). |
| W-H6-COMPLETE-1 | H6 | Room has all required MEP per schedule | BATHROOM has ≥ 1 EXHAUST_FAN, 1 SPRINKLER, 1 LIGHT per ad_space_type_mep_bom. |
| W-H6-COMPLETE-2 | H6 | Missing element flagged WARN | BATHROOM missing EXHAUST_FAN → W_Validation_Result(tier=1, result='WARN', description contains 'IMC 2021 403.3'). |

**Auto-fix acceptance criteria:**

| Handler | Auto-fix Action | Limit | If Exceeded |
|---------|----------------|-------|-------------|
| H1 | Insert connecting pipe/conduit segment | ≤ 3 segments | WARN (manual routing needed) |
| H2 | Nudge element to clear position | ≤ 100mm | WARN (clash too severe for auto-fix) |
| H3 | Adjust grid pitch | ≤ ±10% of typical_spacing | WARN (room too small/large for standard grid) |
| H4 | Snap to nearest valid host surface | ≤ 200mm | WARN (no host nearby) |
| H5 | Snap riser dx/dy to match storey below | ≤ max_xy_drift_mm | BLOCK (tree structure error) |
| H6 | Flag missing items (no auto-insert) | — | WARN always (user decides) |

---

*References: BBC.md §1 (P0.1-DEDUP), BOMBasedCompilation.md §4 (tack convention),
TheRosettaStoneStrategy.md (discipline vocabulary), CONCEPTUAL BLUEPRINT.txt (MEP AttributeSet taxonomy),
DocAction_SRS.md §1 (processIt lifecycle), DocValidate.md §13 (three-tier cascade),
BIM_COBOL.md §4.6 (joining verbs)*
