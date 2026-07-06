Resume the Geometry Forge architect session. You were explaining the Forge
value chain and naming to the user. The user left to consult the watchdog,
came back with decisions. Continue from where you left off.

## Decisions made during watchdog review

### 1. Forge is verified and landed
- `cbec1985` [S99-forge] — 5 engines, 8 tests, all PASS
- Prompt 57 moved to `prompts/done/`
- `mvn compile -q` PASS, ForgeVerbTest 8/8 PASS

### 2. Visual strategy — "No graph, just a form"
The user asked how the Forge differs visually from Grasshopper/Dynamo.
The answer the watchdog gave (user accepted):

- **Grasshopper:** Node graph on the left, viewport on the right. User wires
  nodes together. Visual programming environment.
- **Our Forge:** User sees the building in Bonsai viewport. Parameters in a
  sidebar panel (pitch, span, width). Compliance checks show green/red inline.
  Cost delta updates live (CostDAO already wired). No graph, no nodes, no
  programming metaphor.

**Principle:** Bonsai is the viewport. We are the brain. We compute, they display.

### 3. Bonsai native tools — use, don't rebuild
The watchdog researched all Bonsai native modules. Decision:

| Bonsai native | Our strategy |
|---|---|
| Profile extrusion (ShapeBuilder) | ForgeMesh calls it (rafter = extruded rectangle) |
| bmesh primitives | ForgeMesh calls bmesh for dome panels, pipe fittings |
| MEP conduit routing + clash detection | Our PIPE_BEND feeds into their routing engine |
| Structural axes + connections | Our compiled beams/columns arrive with roles pre-assigned |
| Boolean voids | Our openings use their void operators |

Build on top only where Bonsai has gaps:
- No staircase generation → our STAIR_FLIGHT fills this
- No parametric beam/column placement → our compilation does this
- No multi-storey cloning → our STACK FLOORS verb does this
- No duct routing → future forge engine
- No procedural mesh from formulas → ForgeMesh (Part 2)

### 4. Rebar — already done, just needs Java port
Federation has `rebar_generator.py` + `rebar_standards.py` (MS 1347:2020,
BS 8110, Eurocode 2). Slab/beam/column rules, concrete grades, exposure
classes, IFC entity output, BOQ export. A `FORGE REBAR_SCHEDULE` engine
would port the formulas — the standards tables (the hard part) are done.

### 5. Cut-and-fill on terrain — user wants this
User's PDFTerrain extracts terrain (689 elevation points, POC done).
BBC.md already references `trim_action='CUT_FILL'`. Engineer confirmed
contouring (contour grading) is a follow-through task they do.

A `FORGE SITE_GRADE` engine would:
- Take terrain mesh + building footprint + finished floor level
- Compute cut volume vs fill volume (prismoidal method)
- Output grading contours
- Produce earthworks BOM (m³ cut, m³ fill, retaining walls if slope > threshold)
- Connect to 5D costing (earth-moving rates) and 4D scheduling (earthworks before foundations)

### 6. Prompt execution order (watchdog recommendation)
1. **54** — fix CompileBridgeTest (quick mechanical win)
2. **56** — verb scoreboard doc debt (lists all 75 verbs)
3. **53** — wire costOfChange (killer demo)
4. **55** — placeSet (GAP-DS-1)
5. **58** — capacity rules (AD_Val_Rule migration)
6. **46** — docs polish remainder
7. **50** — anchor fix (always last)

## Your task now

Continue the architect conversation. The user has three open threads:

1. **ForgeMesh (Part 2) — how does it work?** ForgeEngine produces dimensions
   (done). ForgeMesh turns those into Blender vertices. The question: does
   ForgeMesh live in Java (generating IFC geometry) or in Python (calling
   bmesh/ShapeBuilder in Bonsai)? Given "Bonsai is the viewport" principle,
   the answer is likely: Java sends GeometryRecord over BlenderBridge → Python
   side calls bmesh to create the mesh in viewport. Same pattern as db_loader.py
   which already imports bmesh.

2. **Next forge engines to spec:** SITE_GRADE (cut-and-fill) and REBAR_SCHEDULE
   (port from Python). User is excited about both. Spec them if asked.

3. **The 5-part naming** the user saw is confirmed good:
   ForgeEngine → ForgeMesh → ForgePanel → ForgePromotion → ForgeFabrication.
   Parts 1 is done. Roadmap the rest.

## What NOT to do
- Do NOT write code — this is an architect session
- Do NOT modify existing files
- Do NOT run the pipeline
