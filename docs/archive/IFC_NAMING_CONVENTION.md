# IFC Element Naming Convention

## Principle

Element names must be **Outliner-friendly**: users find elements by searching intuitively in any IFC viewer. Category comes FIRST so alphabetical sort groups related elements together.

No JKR prefixes. No Revit IDs. No GUIDs in names.

## Pattern

```
{Category}_{Variant}_{Room}_{Storey}
```

| Segment | Description | Examples |
|---------|-------------|----------|
| Category | What the element IS | `Door`, `Light`, `Alarm`, `Water_Tank` |
| Variant | Specific type/size | `D1_900x2100`, `Downlight`, `Smoke`, `FRP_5x6` |
| Room | Where it is | `Living`, `Kitchen`, `Corridor`, `Bath` |
| Storey | Which floor | `G` (ground), `F2`, `F3`, `Roof` |

## Standard Names

| User searches | element_name pattern | IFC class |
|---------------|---------------------|-----------|
| "water tank" | `Water_Tank_FRP_5x6_Roof` | IfcBuildingElementProxy |
| "alarm" | `Alarm_Smoke_Corridor_F3` | IfcAlarm |
| "toilet" | `Toilet_WC_Suite_Bath_G` | IfcFlowTerminal |
| "vent" / "exhaust" | `Vent_Exhaust_Fan_Kitchen_F2` | IfcFlowTerminal |
| "sprinkler" | `Sprinkler_Pendant_Living_F5` | IfcFireSuppressionTerminal |
| "light" | `Light_Downlight_Office_F3` | IfcLightFixture |
| "bed" / "sofa" | `Bed_King_Master_F2` | IfcFurniture |
| "door" | `Door_D1_900x2100_Living_G` | IfcDoor |
| "window" | `Window_W1_1800x1200_North_G` | IfcWindow |
| "column" | `Column_300x300_Grid_A2_G` | IfcColumn |
| "beam" | `Beam_200x400_Grid_A_F2` | IfcBeam |
| "slab" | `Floor_Slab_150mm_F2` | IfcSlab |
| "wall" | `Wall_Panel_Ext_North_G` | IfcPlate |
| "stair" | `Stair_Flight_Core_G` | IfcStairFlight |
| "railing" | `Railing_Stair_Core_G` | IfcRailing |
| "diffuser" | `Diffuser_Supply_Office_F3` | IfcAirTerminal |
| "pipe" | `Pipe_CW_Riser_Core_F2` | IfcPipeSegment |
| "fan" | `Fan_Exhaust_Kitchen_F2` | IfcFan |
| "furniture" | `Desk_WorkStation_Office_F3` | IfcFurniture |

## Rules

1. **Category first** — enables alphabetical grouping in Outliner
2. **Underscores** — word separator (not spaces, not camelCase)
3. **No abbreviations** except standard: `WC`, `CW` (cold water), `HW` (hot water), `FP` (fire protection), `Ext` (exterior)
4. **Storey suffix** — `G` for ground, `B1` for basement, `F2`-`Fn` for upper floors, `Roof` for roof
5. **Room name matches DSL** — whatever the DSL calls the room, the element name uses the same word
6. **Size in variant** — doors/windows include schedule ref + dimensions: `D1_900x2100`

## Migration from Legacy Names

| Legacy (federation) | New convention |
|---------------------|----------------|
| `jkrME_mec-eq_tank_frp_5x6_cw:2 x 2 x 1:2492753` | `Water_Tank_FRP_5x6` |
| `jkrEL_fire-alarm_smoke-detector:Type 2:1234` | `Alarm_Smoke` |
| `jkrAR_door_single_flush:Type D2:5678` | `Door_D2_900x2100` |

Legacy names with JKR prefixes, Revit type suffixes, and element IDs are replaced by clean category-first names. The component library stores only clean names.
