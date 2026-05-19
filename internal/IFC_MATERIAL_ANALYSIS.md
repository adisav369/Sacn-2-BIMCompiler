# IFC Material Pipeline Analysis

## How Colors Reached Their Best State (S260d-S262)

### The Problem (pre-S260d)

All buildings rendered in uniform grey. `MeshPhongMaterial(flatShading:true, shininess:30)` with a grey-blue ambient light washed out every IFC color. The Hospital helicopter was black. Concrete and steel looked identical. MEP pipes were indistinguishable from walls.

### The Solution: Three-Layer Color Pipeline

The current material system is a **three-layer cascade** — each layer only activates when the previous one fails to produce a distinctive color.

#### Layer 1: IFC-Extracted Color (extract.py)

Colors are extracted directly from IFC `IfcSurfaceStyle` / `IfcStyledItem` during DB creation.

**Two extraction paths:**

1. **Material association:** `elem.HasAssociations` -> `IfcRelAssociatesMaterial` -> material chain -> `IfcStyledItem` -> `IfcSurfaceStyleRendering.SurfaceColour`
2. **Direct representation:** `elem.Representation.Representations[].Items[]` -> `IfcStyledItem` (handles `IfcMappedItem` recursion)

Both paths resolve to `SurfaceColour(Red, Green, Blue)` + `Transparency`. Stored as `material_rgba` in `elements_meta` table: `"R,G,B,A"` (0.0-1.0 normalised).

**Handles both IFC2x3** (`IfcSurfaceStyle` direct) **and IFC4** (wrapped in `IfcPresentationStyleAssignment`).

This is real IFC data. Not invented.

#### Layer 2: Monochrome Grey Fallback (streaming.js `_getMaterial`)

Many IFC authors assign no meaningful color — elements arrive as `0.7,0.7,0.7` (Revit default grey), `0,0,0` (black), or `1,1,1` (white). These are technically "IFC-extracted" but carry no visual information.

**Detection:** `spread = max(R,G,B) - min(R,G,B)`. If `spread < 0.08`, the color is monochrome — channels within 8% of each other, no hue.

**Replacement:** `CLASS_COLOR_FALLBACK[ifcClass]` — a hand-tuned palette of 30+ IFC classes:
- `IfcSlab: 0.78,0.76,0.72` (warm concrete)
- `IfcBeam: 0.60,0.62,0.65` (steel grey)
- `IfcWindow: 0.75,0.85,0.90` (glass blue)
- `IfcDoor: 0.55,0.40,0.25` (wood brown)
- `IfcBuildingElementProxy: 0.00,0.78,0.78` (teal — distinctive for unclassified elements)

This is why the Hospital helicopter turned from black to teal. The IFC file stores `0,0,0,1` (pure black) — monochrome, no hue — so the class fallback for `IfcBuildingElementProxy` applies. This is **not** the helicopter's "real" color — the IFC author never assigned a meaningful one. The fallback makes it visible and distinguishable.

#### Layer 3: PBR Material Properties (per IFC class)

`MeshStandardMaterial` with per-class roughness and metalness:

| Surface | roughness | metalness | Why |
|---------|-----------|-----------|-----|
| Steel (Beam, Column) | 0.3-0.4 | 0.5-0.7 | Smooth, reflective |
| Concrete (Slab, Wall) | 0.75-0.9 | 0.05 | Rough, matte |
| Glass (Window, CurtainWall) | 0.05-0.1 | 0.0-0.1 | Very smooth, non-metallic |
| MEP (Duct, Pipe) | 0.35-0.5 | 0.4-0.5 | Semi-smooth, metallic |
| Wood (Door, Furniture) | 0.6 | 0.05 | Medium rough, non-metallic |

Combined with `ACESFilmic` tone mapping (exposure 0.45) and vertex-color gradient sky environment map.

### Near-White Taming

IFC files often have `0.97,0.97,0.97` — technically not monochrome-grey (would need spread < 0.08 to trigger fallback), but blindingly white under PBR lighting. Fix: if all channels > 0.85, multiply by 0.92. This preserves the slight warmth/coolness of near-white materials while preventing washout.

### Verification: Is a Color IFC or Fallback?

Query the DB:
```sql
SELECT element_name, ifc_class, material_rgba
FROM elements_meta WHERE guid = '...'
```

- If `material_rgba` is non-null, has a hue (spread >= 0.08) -> **IFC color, displayed as-is**
- If `material_rgba` is null -> **default grey, CLASS_COLOR_FALLBACK applied**
- If `material_rgba` is monochrome (spread < 0.08) -> **IFC stored a non-color, CLASS_COLOR_FALLBACK replaces it**

### Evidence: Hospital Helicopter

```
guid: 0MIJdjtET7sB44JdNPCav6
class: IfcBuildingElementProxy
name: Life_Flight_Helicopter
material_rgba: 0.000,0.000,0.000,1.000  <-- IFC says pure black
```

Black spread = 0 < 0.08 -> monochrome -> `CLASS_COLOR_FALLBACK[IfcBuildingElementProxy]` = `0.00,0.78,0.78` (teal).

The helicopter was never blue in the IFC. The IFC author assigned black (absence of color). The viewer's fallback makes it teal for visibility. This is documented, not invented — the pipeline trace proves the source.

### Files

| File | Role |
|------|------|
| `tools/extract.py:98-188` | IFC color extraction (IfcSurfaceStyle/IfcStyledItem) |
| `deploy/dev/streaming.js:243-316` | `_getMaterial()` — PBR, fallback, caching |
| `deploy/dev/scene.js:22,114` | ACESFilmic tone mapping, environment map |
| `docs/SQLite3D_Schema.md` | `material_rgba` column spec |
