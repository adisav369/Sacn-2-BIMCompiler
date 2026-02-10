# LOD 400 Assemblies - Residential Patterns (Researched)

## Status: RESEARCHED from Published Standards

**Date:** 2026-01-30
**Sources:** AS 1684, NZS 3604, IRC R602, Simpson Strong-Tie, MiTek, Pryda

---

## 1. Timber Stud Wall Assembly

### Standards Reference
- [AS 1684](https://hia.com.au/resources-and-advice/building-it-right/australian-standards/articles/using-as-1684-for-timber-framing) - Australian Residential Timber Framing
- [NZS 3604](https://www.building.govt.nz/building-code-compliance/how-the-building-code-works/using-nzs-3604-timber-framed-buildings) - NZ Timber-framed Buildings
- [IRC R602](https://codes.iccsafe.org/s/IRC2021P2/chapter-6-wall-construction/IRC2021P2-Pt03-Ch06-SecR602.3.1) - US Wood Frame Construction

### Components

| Component | Role | Typical Size (Metric) | Typical Size (Imperial) |
|-----------|------|----------------------|------------------------|
| Top Plate | HEAD | 90×45mm | 2×4 |
| Bottom Plate | SOLE | 90×45mm | 2×4 |
| Studs | FRAME | 90×45mm | 2×4 |
| Noggings/Dwangs | BLOCKING | 70×35mm | 2×3 |

### Stud Spacing
| Application | Spacing (Metric) | Spacing (Imperial) |
|-------------|-----------------|-------------------|
| External walls (tile roof) | 450mm | 18" |
| External walls (metal roof) | 600mm | 24" |
| Internal walls | 600mm | 24" |

### Assembly Rules
- Noggings at max 1350mm centers (mid-height for 2.7m walls)
- Minimum nogging size: 25mm thick, 25mm less than stud depth
- Wall height max: 3000mm standard, 3600mm with engineering

### Fasteners
| Connection | Fastener | Quantity |
|------------|----------|----------|
| Stud to plate | 2× 75mm framing nails | Each end |
| Nogging to stud | 2× 75mm nails | Skew nailed |
| Double plate | 2× 75mm nails | 600mm centers |

### DSL Assembly Definition
```
ASSEMBLY "STUD_WALL" {
    components: {
        TOP_PLATE: 1 @ top
        BOTTOM_PLATE: 1 @ bottom
        STUD: calculated @ 450-600mm spacing
        NOGGING: calculated @ 1350mm height
    }
    fasteners: {
        STUD_PLATE: 2× FRAMING_NAIL_75
        NOGGING_STUD: 2× FRAMING_NAIL_75 skew
    }
}
```

---

## 2. Roof Truss Assembly

### Standards Reference
- [MiTek Roof Truss Installation Guide](https://www.mitek.com.au/wp-content/uploads/2021/08/MGB0618-MiTek-Guide-for-Roof-Truss-Installation_WEB_FA-2021.pdf)
- [Pryda Roof Truss Installation Guide](https://pryda.com.au/wp-content/uploads/Pryda-Roof-Truss-Installation-Guide.pdf)
- [Gang-Nail Residential Manual](https://miteknz.co.nz/wp-content/uploads/2024/03/Residential-Manual-March-2024.pdf)

### Components

| Component | Role | Description |
|-----------|------|-------------|
| Top Chord | RAFTER | Inclined member supporting roof |
| Bottom Chord | CEILING_TIE | Horizontal member (ceiling attachment) |
| Web | BRACE | Internal members (compression/tension) |
| Apex Joint | PEAK | Top chord junction at ridge |
| Heel Joint | BEARING | Bottom chord to top chord at wall |
| Nail Plate | CONNECTOR | Metal connector plate at joints |

### Connection Details
| Joint | Connection | Specification |
|-------|------------|---------------|
| Apex | Gang-nail plate | Both sides |
| Heel | Gang-nail plate | Min 10mm below bearing |
| Web joints | Gang-nail plate | Both sides |
| Truss to plate | Multigrip + nails | 3× 35mm connector nails |
| Bottom chord lateral | Ties at 1200mm | Prevents buckling |

### Truss Spacing
- Standard: 600mm centers
- Heavy load: 450mm centers
- Max span without support: varies by design

### DSL Assembly Definition
```
ASSEMBLY "ROOF_TRUSS" type:FINK {
    components: {
        TOP_CHORD: 2 @ pitch angle
        BOTTOM_CHORD: 1 @ horizontal
        WEB: 4-6 @ calculated
        NAIL_PLATE: calculated @ joints
    }
    fasteners: {
        CHORD_PLATE: NAIL_PLATE gang-nail
        TRUSS_WALL: 3× CONNECTOR_NAIL_35 + MULTIGRIP
    }
}
```

---

## 3. Door Assembly

### Standards Reference
- [Door Hardware Specifications](https://www.dkhardware.com/blog/hinge-size-guide-and-chart/)
- [Butt Hinge Guide](https://www.suffolklatchcompany.com/blogs/news/butt-hinges-explained-part-one)

### Components

| Component | Role | Specification |
|-----------|------|---------------|
| Frame/Lining | FRAME | Timber 25-32mm thick |
| Door Leaf | LEAF | Solid/hollow core |
| Hinges | HARDWARE | 3× 100mm butt hinge (external) |
| Handle Set | HARDWARE | Lever or knob + rose |
| Lock | HARDWARE | Mortice (external) or latch (internal) |
| Door Stop | TRIM | 12×40mm timber |

### Hinge Requirements
| Door Type | Hinge Size | Quantity |
|-----------|------------|----------|
| Light internal | 75mm (3") | 2 |
| Standard internal | 100mm (4") | 2-3 |
| Heavy external | 100mm (4") | 3 |
| Fire door | 100mm CE13 rated | 3 |

### Hinge Positioning
- Top hinge: 125-175mm from top
- Bottom hinge: 250-275mm from bottom
- Middle hinge: Centered (for 3-hinge doors)

### DSL Assembly Definition
```
ASSEMBLY "DOOR_ASSEMBLY" {
    components: {
        FRAME: 1 (head + 2 jambs)
        LEAF: 1
        HINGE: 2-3 @ 100mm butt
        HANDLE_SET: 1
        LOCK: 1 (external only)
        STOP: 1 (head + 2 jambs)
    }
    fasteners: {
        HINGE_FRAME: 3× SCREW_38
        HINGE_LEAF: 3× SCREW_25
        FRAME_WALL: FIXING_PLUGS @ 400mm
    }
}
```

---

## 4. Window Assembly

### Components

| Component | Role | Specification |
|-----------|------|---------------|
| Frame | FRAME | Aluminium/timber |
| Sash | SASH | Sliding/casement panel |
| Glazing | GLASS | 4-6mm float/toughened |
| Stays | HARDWARE | 2 per openable sash |
| Lock | HARDWARE | 1 per window |
| Weatherseal | SEAL | Rubber/foam strip |

### Assembly by Type
| Window Type | Sashes | Stays | Locks |
|-------------|--------|-------|-------|
| Fixed | 0 | 0 | 0 |
| Casement | 1-2 | 2 each | 1 each |
| Awning | 1 | 2 | 1 |
| Sliding | 2 | 0 | 1 |
| Double-hung | 2 | 0 | 1-2 |

### DSL Assembly Definition
```
ASSEMBLY "WINDOW_ASSEMBLY" type:CASEMENT {
    components: {
        FRAME: 1
        SASH: 2
        GLAZING: 2
        STAY: 4 (2 per sash)
        LOCK: 2
        WEATHERSEAL: perimeter
    }
    fasteners: {
        FRAME_WALL: FIXING_PLUGS @ 300mm
        SASH_HINGE: FRICTION_STAY
    }
}
```

---

## 5. Floor Joist Assembly

### Standards Reference
- [Simpson Strong-Tie LUS Joist Hangers](https://www.strongtie.com/facemounthangersssl_solidsawnlumberconnector/lus_hanger/p/lus)
- AS 1684 Floor Framing

### Components

| Component | Role | Typical Size |
|-----------|------|--------------|
| Bearer | SUPPORT | 90×45mm to 190×45mm |
| Joist | FLOOR | 190×45mm @ 450mm |
| Blocking | BRACING | 190×45mm @ mid-span |
| Joist Hanger | CONNECTOR | LUS series |
| Nogging | EDGE | Under walls |

### Joist Hanger Sizes (Simpson LUS)
| Joist Size | Hanger Model | Gauge |
|------------|--------------|-------|
| 2×6 (140×45) | LUS26 | 20ga |
| 2×8 (190×45) | LUS28 | 18ga |
| 2×10 (240×45) | LUS210 | 18ga |

### Connection Details
- Double-shear nailing for load distribution
- Speed prongs for temporary hold during installation
- Fewer nails required than face-nailing

### DSL Assembly Definition
```
ASSEMBLY "FLOOR_JOIST" {
    components: {
        BEARER: 2 @ supports
        JOIST: calculated @ 450mm spacing
        BLOCKING: calculated @ mid-span
        NOGGING: under load-bearing walls
    }
    connections: {
        JOIST_BEARER: JOIST_HANGER (LUS series)
        BLOCKING_JOIST: 2× NAIL_75 skew
    }
}
```

---

## 6. Fastener Schedule

### Framing Nails
| Code | Type | Size | Use |
|------|------|------|-----|
| FN-75 | Framing nail | 75×3.15mm | Stud to plate |
| FN-90 | Framing nail | 90×3.75mm | Heavy framing |
| CN-35 | Connector nail | 35×3.05mm | Truss connectors |
| CN-65 | Connector nail | 65×3.33mm | Joist hangers |

### Screws
| Code | Type | Size | Use |
|------|------|------|-----|
| SC-25 | Countersunk | 25×4mm | Hinge to leaf |
| SC-38 | Countersunk | 38×4mm | Hinge to frame |
| SC-50 | Countersunk | 50×4mm | General fixing |

### Brackets/Connectors
| Code | Type | Model | Use |
|------|------|-------|-----|
| JH-26 | Joist hanger | LUS26 | 2×6 joist |
| JH-28 | Joist hanger | LUS28 | 2×8 joist |
| MG-* | Multigrip | Pryda | Truss to plate |
| NP-* | Nail plate | Gang-nail | Truss joints |

---

## 7. Hardware Schedule

### Door Hardware
| Code | Type | Size | Per Door |
|------|------|------|----------|
| BH-75 | Butt hinge | 75mm | 2 (internal) |
| BH-100 | Butt hinge | 100mm | 3 (external) |
| HS-L | Handle set lever | - | 1 |
| ML-M | Mortice lock | - | 1 (external) |
| LT-T | Tubular latch | - | 1 (internal) |

### Window Hardware
| Code | Type | Per Window |
|------|------|------------|
| FS-* | Friction stay | 2 (casement) |
| WL-* | Window lock | 1 |
| WS-* | Weatherseal | Perimeter |

---

## 8. Acquisition Status

| Data Needed | Source | Status |
|-------------|--------|--------|
| Stud wall patterns | AS 1684, NZS 3604, IRC | ◐ RESEARCHED |
| Roof truss patterns | MiTek, Pryda manuals | ◐ RESEARCHED |
| Door hardware | Industry specs | ◐ RESEARCHED |
| Window hardware | Industry specs | ◐ RESEARCHED |
| Floor joist patterns | Simpson Strong-Tie | ◐ RESEARCHED |
| Fastener schedules | Manufacturer data | ◐ RESEARCHED |
| Actual geometry | Residential IFC model | ○ PENDING |

**Legend:**
- ✓ EXTRACTED (from existing DB)
- ◐ RESEARCHED (from standards/manufacturers)
- ○ PENDING (needs residential IFC extraction)

---

## Sources

### Standards
- [AS 1684 via HIA](https://hia.com.au/resources-and-advice/building-it-right/australian-standards/articles/using-as-1684-for-timber-framing)
- [NZS 3604 via Building.govt.nz](https://www.building.govt.nz/building-code-compliance/how-the-building-code-works/using-nzs-3604-timber-framed-buildings)
- [IRC 2021 R602](https://codes.iccsafe.org/s/IRC2021P2/chapter-6-wall-construction/IRC2021P2-Pt03-Ch06-SecR602.3.1)

### Manufacturers
- [MiTek Roof Truss Guide](https://www.mitek.com.au/wp-content/uploads/2021/08/MGB0618-MiTek-Guide-for-Roof-Truss-Installation_WEB_FA-2021.pdf)
- [Pryda Installation Guide](https://pryda.com.au/wp-content/uploads/Pryda-Roof-Truss-Installation-Guide.pdf)
- [Simpson Strong-Tie LUS](https://www.strongtie.com/facemounthangersssl_solidsawnlumberconnector/lus_hanger/p/lus)
- [DK Hardware Hinge Guide](https://www.dkhardware.com/blog/hinge-size-guide-and-chart/)

---

*Researched from published standards and manufacturer documentation - 2026-01-30*
