# Reference IFC Corpus — The Rosetta Stone

> **Note:** The primary Rosetta Stone reference DBs and IFC source files have been
> consolidated into `DAGCompiler/lib/input/`. See `DAGCompiler/README.md` for current layout.
> This directory retains the original IFC corpus for reference.

These IFC files are the **ground truth** for the BIM compiler's metadata grammar.
Every table, column, and row in `component_library.db` must trace back to a real
element in one of these files. The compiler proves itself by reproducing them
from metadata alone.

## Residential (Grammar Baseline)

| File | IFC Version | Elements | Role |
|------|-------------|----------|------|
| `Ifc2x3_Duplex_Architecture.ifc` | 2x3 | 218 | Walls, doors, windows, furniture, stairs, slabs |
| `Ifc2x3_Duplex_MEP.ifc` | 2x3 | ~900 | Plumbing, electrical, HVAC piping |
| `Ifc4_SampleHouse.ifc` | 4.0 | ~100 | Single-storey house — simplest grammar test |
| `Ifc4_WallElementedCase.ifc` | 4.0 | ~10 | Wall construction layers — stud/cladding detail |

**Source:** youshengCode IFC samples (open license)

### Duplex Architecture (218 entities)
- 4 storeys (T/FDN, Level 1, Level 2, Roof)
- 21 rooms (2 mirrored units × 10 rooms + 1 roof)
- 57 walls (8 construction types)
- 14 doors (4 size variants)
- 24 windows (6 type/size variants incl. skylight)
- 61 furnishing elements (12 distinct types)
- 2 stairs (each: flight + 2 stringers + 2 railings)
- 21 slabs (6 types incl. finish floors)
- 8 steel beams, 7 footings, 13 ceilings

### Extraction Status
- Stacked_Duplex.db: `database/Stacked_Duplex.db` (1085 elements, queryable)
- SampleHouse.db: Not yet extracted
- Duplex MEP.db: Not yet extracted as separate DB

## Infrastructure (IFC 4.3 Grammar Extension)

| File | Domain | Role |
|------|--------|------|
| `Building-Architecture.ifc` | Architectural | Multi-discipline building |
| `Building-Hvac.ifc` | HVAC | Ductwork, AHU, terminals |
| `Building-Landscaping.ifc` | Landscape | Site elements |
| `Building-Structural.ifc` | Structural | Frame, foundation |
| `Infra-Bridge.ifc` | Civil | Bridge geometry |
| `Infra-Landscaping.ifc` | Civil | Roads + landscape |
| `Infra-Plumbing.ifc` | Civil | Underground services |
| `Infra-Rail.ifc` | Rail | Railway alignment |
| `Infra-Road.ifc` | Road | Road geometry |

**Source:** PCERT IFC 4.3.2.0 (IFC4X3_ADD2) certification samples

## Grammar Extraction Process

1. **Read the IFC** — parse entity types, counts, spatial hierarchy
2. **Map to AD tables** — which metadata table describes each entity class?
3. **Identify gaps** — entity types with no metadata grammar = language deficiency
4. **Fill gaps** — add table/column/row to express what the IFC says
5. **Compile** — DSL → metadata → output DB
6. **Compare** — output geometry vs reference geometry (maths truth)
7. **Iterate** — fix discrepancies until exact match

The grammar is complete when ALL reference entities can be reproduced from
metadata catalog selection alone, with zero hardcoded geometry.
