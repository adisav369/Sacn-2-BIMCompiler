# Multi-Discipline BOM Design

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

*References: ConstructionAsERP.md §11.38 (P0.1-DEDUP), BOMBasedCompilation.md §3.4 (tack convention),
TheRosettaStoneStrategy.txt (discipline vocabulary), CONCEPTUAL BLUEPRINT.txt (MEP AttributeSet taxonomy)*
