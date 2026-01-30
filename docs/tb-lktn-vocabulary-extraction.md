# TB-LKTN Vocabulary Extraction (Google Vision AI)

## Source
- **Pages**: 8 architectural drawing sheets from TB-LKTN (Rumah Rakyat)
- **Extraction**: Google Vision AI OCR
- **Date**: 2026-01-30

---

## Malaysian Room Names → SpaceType Mapping

| Malay Term | Abbreviation | English | SpaceType |
|------------|--------------|---------|-----------|
| BILIK UTAMA | MR | Master Room | `MASTER_BEDROOM` |
| BILIK TIDUR | BT | Bedroom | `BEDROOM` |
| BILIK MANDI | - | Bathroom | `BATHROOM` |
| RUANG TAMU | RT | Living Room | `LIVING` |
| RUANG MAKAN | RM | Dining Room | `DINING` |
| DAPUR | - | Kitchen | `KITCHEN` |
| DAPUR BASUH | - | Wet Kitchen | `WET_KITCHEN` |
| TANDAS | WC | Toilet | `BATHROOM` |
| ANJUNG | - | Porch/Verandah | `PORCH` |
| CAR PORCH | CP | Car Porch | `CAR_PORCH` |
| CORRIDOR | - | Corridor | `CORRIDOR` |

### New SpaceTypes to Add
```java
// Malaysian residential types
MASTER_BEDROOM,    // Bilik Utama
WET_KITCHEN,       // Dapur Basuh (laundry/utility)
CAR_PORCH,         // Anjung Kereta
VERANDAH,          // Anjung
```

---

## Grid System Extracted

### Axes
- **X-axis**: A, B, C, D, E
- **Y-axis**: 1, 2, 3, 4, 5

### Typical Spacings (mm)
| Dimension | Usage |
|-----------|-------|
| 750 | Door/window offset from corner |
| 1300 | Small room/corridor width |
| 1500 | Standard door bay |
| 1600 | Standard room module |
| 2300 | Porch width |
| 3000 | Standard floor height |
| 3100 | Main room module |
| 3200 | Large room depth |
| 3700 | Main span |
| 8500 | Building width |
| 9900 | Building length |

### DSL Syntax
```dsl
GRID {
    axes: A, B, C, D, E / 1, 2, 3, 4, 5
    spacing: 750, 2300, 3100, 3100, 750 / 750, 3100, 3100, 3700, 750
}
```

---

## Door & Window Schedule

### Door Types
| Code | Description | Size (mm) |
|------|-------------|-----------|
| D1 | Main entry | 900 × 2100 |
| D2 | Internal | 800 × 2100 |
| D3 | Bathroom | 700 × 2100 |

### Window Types
| Code | Description | Size (mm) |
|------|-------------|-----------|
| W1 | Standard | 1200 × 1200 |
| W2 | Large | 1800 × 1200 |
| W3 | Small/toilet | 600 × 600 |
| W4 | Louvre | 600 × 900 |

### DSL Syntax
```dsl
SCHEDULE doors {
    D1: 900x2100 type:panel material:timber
    D2: 800x2100 type:panel material:timber
    D3: 700x2100 type:panel material:timber
}

SCHEDULE windows {
    W1: 1200x1200 type:casement material:aluminium
    W2: 1800x1200 type:casement material:aluminium
    W3: 600x600 type:louvre material:aluminium
    W4: 600x900 type:louvre material:aluminium
}
```

---

## Electrical Points (MEP Vocabulary)

### Extracted Components
| Symbol | Description | Qty (per TB-LKTN) |
|--------|-------------|-------------------|
| M | Electricity Meter | 1 |
| - | Fuse and Distribution Board | 1 |
| ○ | Ceiling Light Point | 9 |
| ⊗ | Ceiling Fan Point | 5 |
| ≡ | One Way Switch (1,2,3 gang) | 7 |
| ⊡ | 3 pin 13 amp power point | 8 |
| A | Wall Mounted Light Point | 1 |

### MEP DSL Extension
```dsl
ELECTRICAL {
    METER at:exterior
    DB at:A2  // Distribution Board

    CEILING_LIGHT in:bilik_utama, ruang_tamu, ruang_makan, dapur, bilik_tidur
    CEILING_FAN in:bilik_utama, ruang_tamu, bilik_tidur
    POWER_POINT in:bilik_utama qty:2, ruang_tamu qty:2, dapur qty:3
    SWITCH at:door_adjacent
}
```

---

## Drawing Level References

| Code | Description | Elevation |
|------|-------------|-----------|
| GRD. LEVEL | Ground Level | 0.000 |
| APRON LEVEL | Porch/driveway level | -0.150 |
| GRD. FLOOR LEVEL | Finished Floor Level (FFL) | +0.150 |
| BEAM/CEILING LEVEL | Ceiling height | +3.000 |

---

## Profile: Malaysian_Residential

### Building Code Reference
- **UBBL 1984** (Undang-Undang Kecil Bangunan Seragam 1984)
- Building Type: RUMAH RAKYAT (People's Housing)

### Defaults from Extraction
```java
Profile Malaysian_Residential {
    wall_thickness: 150mm  // Standard brick
    floor_to_floor: 3000mm
    ceiling_height: 2700mm
    door_height: 2100mm
    window_sill: 900mm

    // Room minimums
    bedroom_min: 9.0sqm
    kitchen_min: 4.5sqm
    bathroom_min: 2.5sqm

    // Code reference
    code: "UBBL 1984"
}
```

---

## Malay Building Terminology

| Malay | English | Usage |
|-------|---------|-------|
| PEMILIK | Owner | Title block |
| ARKITEK | Architect | Title block |
| PROJEK | Project | Title block |
| JENIS BANGUNAN | Building Type | Classification |
| TAJUK LUKISAN | Drawing Title | Sheet title |
| DILUKIS | Drawn by | Author |
| DISEMAK | Checked by | Reviewer |
| UKURAN | Scale | Drawing scale |
| HARIBULAN | Date | Timestamp |
| NO. LUKISAN | Drawing Number | Sheet ID |
| PINDAAN | Revision | Amendment |
| NO. KEPINGAN | Sheet Number | Sheet count |
| PELAN | Plan | Floor plan |
| KERATAN | Section | Building section |
| PANDANGAN | Elevation | Building elevation |

---

## Validation Rules (from drawings)

### Discharge Points
- Surface water discharge to municipal drain
- Multiple discharge points per elevation (extracted: 6+ per building)

### Roof
- Pitch: typical Malaysian (15-25°)
- Overhang: 600mm (extracted from perimeter drain offset)

### Construction Notes
- Wall: 150mm brick
- Slab: RC 150mm
- Roof: Lightweight metal deck

---

## Implementation Priority

### Phase 1: SpaceType Aliases
Add Malay room name aliases to RoomType.java:
```java
case "BILIK_UTAMA", "MR" -> MASTER_BEDROOM;
case "BILIK_TIDUR", "BT" -> BEDROOM;
case "BILIK_MANDI" -> BATHROOM;
case "RUANG_TAMU", "RT" -> LIVING;
case "RUANG_MAKAN" -> DINING;
case "DAPUR" -> KITCHEN;
case "DAPUR_BASUH" -> WET_KITCHEN;
case "TANDAS", "WC" -> BATHROOM;
case "ANJUNG" -> PORCH;
case "CAR_PORCH", "CP" -> CAR_PORCH;
```

### Phase 2: Schedule Integration
- Door/window types from schedule
- Reference by code (D1, W2) in DSL

### Phase 3: MEP Vocabulary
- Electrical point types
- Auto-placement rules (ceiling points per room type)

---

*Extracted from TB-LKTN Vision AI JSON - 2026-01-30*
