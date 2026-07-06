# ForgeMesh — Bonsai Native Mesh Creation from ForgeEngine Results

**Spec:** `docs/FORGE_SUITE_SRS.md` §9 Part ②
**Depends on:** ForgePanel (prompt 60), BlenderBridge
**Priority:** Phase 4

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Call Bonsai's existing ShapeBuilder and bmesh.
Do NOT write raw vertex generation. Do NOT create a new mesh engine.
Bonsai creates geometry — we tell it what to create.

## Read first

1. `docs/FORGE_SUITE_SRS.md` §9 Part ② — implementation approach, code examples
2. `docs/BlenderBridge.md` — pipe protocol
3. Bonsai ShapeBuilder: `/home/red1/IfcOpenShell/src/ifcopenshell-python/ifcopenshell/util/shape_builder.py`
   — extrude(), rectangle(), circle(), profile()
4. Bonsai stair mesh generation: `model/stair.py` — how it uses bmesh
5. Bonsai profile generation: `model/profile.py` — DumbProfileGenerator
6. `BIM_COBOL/src/main/java/com/bim/cobol/forge/GeometryRecord.java` — what we receive

## Task

### A. Mesh creation functions

Create `forge_mesh.py` in the Bonsai forge module:

**For elongated members (SLOPE_CUT, pipe segments, stringers):**
- Receive GeometryRecord with length/width/depth + fabrication (cut angles)
- Call `ShapeBuilder.rectangle(width, depth)` → `ShapeBuilder.extrude(length)`
- Apply angled cuts from fabrication data (cut_angle_top, cut_angle_bottom)
- Position at GeometryRecord placement coordinates

**For panel arrays (DOME_SECTION, BARREL_VAULT):**
- Receive list of GeometryRecords with positions + dimensions
- For each: create flat quad via `bmesh.ops.contextual_create()`
- Position at computed (x, y, z) from GeometryRecord
- Rotate per GeometryRecord.rotation

**For assemblies (STAIR_FLIGHT):**
- Receive multiple GeometryRecords (stringer + landing)
- Create each as separate mesh object
- Group as parent-child in Blender scene hierarchy

### B. BlenderBridge commands

- `FORGE_MESH_EXTRUDE <json>` — create extruded member from single GeometryRecord
- `FORGE_MESH_PANELS <json>` — create panel array from GeometryRecord list
- `FORGE_MESH_ASSEMBLY <json>` — create multi-part assembly
- `FORGE_MESH_UPDATE <json>` — regenerate existing mesh (for gizmo drag)
- `FORGE_MESH_CLEAR` — remove preview mesh (cancel)

### C. IFC entity creation

Forged meshes must be proper IFC entities (not orphan Blender objects):
- Use `ifcopenshell.api.root.create_entity()` with appropriate IFC class
- Map piece types: SLOPE_CUT → IfcMember, STAIR_FLIGHT → IfcStairFlight,
  PIPE_BEND → IfcPipeSegment, DOME_SECTION → IfcPlate, BARREL_VAULT → IfcMember
- Assign to correct spatial container (building storey)

## What NOT to do

- Do NOT modify ForgeEngine (Java side)
- Do NOT rebuild ShapeBuilder or bmesh — CALL them
- Do NOT implement ForgeGizmo — that's prompt 62
- Do NOT implement ForgePromotion — that's prompt 63
- Do NOT generate raw vertex arrays — use Bonsai's geometry creation APIs

## Verify

1. FORGE SLOPE_CUT pitch:30 span:5200 width:90 depth:45 → extruded rafter appears in viewport
2. FORGE DOME_SECTION radius:8000 rings:6 segments:12 base_z:15000 → 72 panels visible
3. FORGE STAIR_FLIGHT height:2700 tread:250 riser:180 width:900 → stringer + landing visible
4. All created objects are valid IFC entities

## Commit message

```
[S##-forge] ForgeMesh — Bonsai native mesh from ForgeEngine results

forge_mesh.py: extrude (ShapeBuilder), panels (bmesh), assembly (grouped).
BlenderBridge FORGE_MESH_* commands. IFC entity mapping per piece type.
Calls Bonsai native tools — no raw vertex generation.
```
