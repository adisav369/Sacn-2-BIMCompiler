# ARCHIVE: Intent-to-DAG Compiler Method

**Source:** https://gitlab.com/red1org/hongkong/-/issues/69  
**Status:** READ-ONLY REFERENCE — Do not modify  
**Purpose:** Preserve design rationale to prevent formula drift

---

## Core Insight

**Construction is a COMPILATION PROBLEM, not a drawing interpretation problem.**

```
Intent Document → Compiler → Bytecode → VM → Building
```

This mirrors: Source Code → Compiler → Bytecode → JVM → Program

---

## The Four Algorithmic Approaches Considered

### 1. Turtle Construction (Sequential)
Like G-code for CNC. Cursor moves, places elements.
- **Pro:** Debuggable, deterministic
- **Con:** Not parallelizable, brittle

### 2. Dependency Graph (DAG) ← CHOSEN
Topological sort determines build order.
- **Pro:** Automatic ordering, parallelizable where graph allows
- **Con:** Requires complete dependency inference

### 3. Cellular Automaton
Local rules produce global structure.
- **Pro:** Self-organizing
- **Con:** Hard to control, unpredictable

### 4. Procedural Bytecode ← CHOSEN
Construction as instruction set executed by VM.
- **Pro:** Debuggable, testable, versionable, replayable
- **Con:** Requires complete instruction set design

**Final Choice:** Combine #2 (DAG for sequencing) + #4 (Bytecode for execution)

---

## Critical Failure Point: DAG Resolution

### The Balcony Problem

User says: "Add cantilevered balcony"

Naive system thinks:
```
balcony.dependsOn = [wall]
```

Reality requires:
```
balcony.dependsOn = [
    extendedSlab,      // Backspan for cantilever
    dowelBars,         // Reinforcement continuity  
    tensionTies,       // Uplift resistance
    edgeBeam,          // Railing support
    formworkSupport    // Construction access
]
```

### The Resolution Pattern

```
// Multi-pass dependency analysis
Pass 1: Geometric dependencies (what touches what)
Pass 2: Structural dependencies (load paths)
Pass 3: Construction dependencies (access, sequencing)
Pass 4: Code compliance (inspections)
Pass 5: Resource dependencies (crane, formwork)
```

### Implicit Component Discovery

The compiler's real job:
1. Find what user forgot to specify
2. Find what can't work as specified
3. Find cheapest way to make it work
4. Find when to ask for human help

---

## Geometry Pipeline

### Three-Tier Representation

```
Tier 1: Construction Instruction (Abstract)
    ↓
Tier 2: B-Rep Solid (Mathematical)
    ↓
Tier 3: Renderable Mesh (Blender)
```

### B-Rep Structure

```
Solid {
    vertices: Point3D[]
    faces: Face[]
    
    Face {
        vertices: Point3D[]  // Winding order matters
        plane: Plane
        material: Material
    }
}
```

### Wall Example (8 vertices, 6 faces)

```
V0-V3: Bottom rectangle
V4-V7: Top rectangle (V0-V3 + height)

Faces:
- Front (exterior finish)
- Back (interior finish)  
- Top (wall cap)
- Bottom (foundation contact)
- Left end
- Right end
```

### Opening Cut (Boolean Subtraction)

Before: 6 faces
After window cut: 10+ faces (strips around opening + reveals)

---

## Instruction Set (Sample Opcodes)

```
0x01 PLACE_FOUNDATION_CORNER (x, y, z)
0x02 EXTEND_WALL (from, to, height, material)
0x03 CUT_OPENING (host, type, dimensions)
0x04 PLACE_COLUMN (position, section)
0x05 INSTALL_BEAM (from_col, to_col, section)
0x06 POUR_SLAB (boundary, thickness)
0x07 CAP_WITH_ROOF (type, pitch)
0x08 VALIDATE_CONNECTION (comp_a, comp_b)
```

---

## Why Previous Approaches Failed

### Google Vision AI
- Sees pixels, not construction logic
- No knowledge of dependencies
- No structural understanding

### Template Matching
- Combinatorial explosion
- Can't handle variations
- No semantic understanding

### Direct 2D→3D Conversion
- Interpretation gap
- Missing implicit elements
- No construction sequence

---

## The Compiler Advantage

```
// Template approach (fails)
template_wall_type_A_variant_1
template_wall_type_A_variant_2
... (infinite variants)

// Compiler approach (works)
Rule: wall.on(foundation)
Rule: opening.within(wall)
Rule: cantilever.maxRatio(1/3)
→ Compiler enforces, generates valid geometry
```

---

## Key Equations

### Cantilever Rule
```
backspan >= cantilever * 3
```

### Opening Ratio
```
openingArea <= wallArea * maxOpeningRatio(material)
```

### Construction Access
```
for each component in buildOrder:
    assert isAccessible(component, alreadyBuilt)
    assert !blocksAccess(component, toBuild)
```

---

## Database Schema (Core)

```sql
components (id, geometry, type, properties, state)
dependencies (component_id, depends_on_id, type)
instructions (id, opcode, parameters, executed_at)
```

---

## Integration Points

```
Bonsai Drawing → Intent Extraction → CCVM Compiler → 3D Model
                                   ↓
                            Spatial DB (PostGIS)
                                   ↓
                            Blender Visualization
                                   ↓
                            IFC Export
```

---

## Research Precedents (from DeepSeek)

- **Shape Grammars:** Stiny & Gips (1972)
- **CGA/CityEngine:** Wonka et al. (SIGGRAPH 2001)
- **Construction Process Modelling:** Sriprasert & Dawood (2003)
- **BIM 4D/5D:** ISO 19650 standard
- **Robotic Fabrication:** ETH Zurich NCCR Digital Fabrication

---

## Summary

The breakthrough: **Don't interpret drawings. Execute construction intent.**

Drawings are one view of the model, not the source of truth.
Intent document is the source code.
Compiler enforces construction logic.
VM executes deterministic sequence.
Federated DB is the symbol table.
Blender is the debugger/visualizer.

---

**END OF ARCHIVE — DO NOT MODIFY**
