# Bundle Worker Framework

*Phase 118 — OSGi-Inspired Construction Worker Pattern*
*Extends: ARCHITECTURE.md §1.3, PREFAB_ARCHITECTURE.md*

> **Staleness note (2026-02-26):** Phase G-1 completed. `FixturePlacer` and
> `FurnitureTypeResolver` are **deleted**. `FurnitureBOMResolver` renamed to
> `BOMTierResolver`. `FurnitureWorker` now calls `BOMTierResolver.resolveForRoom()`
> directly — no intermediary. `ad_room_slot` deprecated by `bom_category` on M_BOM.
> Checklists below reflect original design targets; some items are done, others superseded.
>
> **Canonical references:** `docs/ConstructionAsERP.md`, `docs/METADATA_DRIVEN_ARCHITECTURE.md`

## Principle

A building is assembled by **specialized workers**, each responsible for one theme.
Like OSGi bundles, each worker declares what it provides (placed elements) and what
it requires (room envelope, face anchors, clearance). Workers don't know each other —
they communicate only through the room envelope and spatial reservation system.

```
Current:  StoreyCompiler calls 8 placers explicitly, each with different signatures
Target:   StoreyCompiler dispatches to N workers via ad_room_slot, all same interface
```

## The Construction Site Metaphor

| Construction Site | BIM Compiler |
|---|---|
| Architect selects from catalog | DSL selects building template (MANIFEST) |
| Foreman reads blueprint | StoreyCompiler reads room slots |
| Plumber arrives, does plumbing | FIXTURE_SANITARY worker places toilet + basin |
| Electrician arrives, does wiring | MEP_ELECTRICAL worker places outlets + switches |
| Carpenter arrives, does furniture | FURNITURE worker places desks + beds |
| Inspector checks result | SpatialDigest verifies deterministic output |

Each worker:
1. **Arrives** with their theme ID (e.g., "FURNITURE", "MEP_CEILING")
2. **Reads** the room envelope (bounds, faces, reserved zones)
3. **Places** elements according to face alignment + dimension properties
4. **Reserves** their clearance envelope for the next worker
5. **Leaves** — no coupling to other workers

## BundleWorker Interface

```java
/**
 * OSGi-inspired construction worker. Each worker handles one theme,
 * reads from one AD table section, and places elements by face alignment.
 */
public interface BundleWorker {

    /** Theme ID matching ad_room_slot.assembly_id (e.g., "BED_SET", "BATHROOM_WC_SET") */
    String themeId();

    /** Which face this worker anchors to (BACK, FRONT, LEFT, RIGHT, TOP, BOTTOM) */
    String anchorFace();

    /** Execute placement within room envelope. Returns placed elements. */
    List<PlacedElement> execute(RoomEnvelope room, PlacementContext ctx);

    /** Clearance envelope reserved by this worker (for spatial reservation). */
    BoundingBox reservedEnvelope();
}
```

### RoomEnvelope

What every worker receives — the room as a standardized workspace:

```java
record RoomEnvelope(
    String roomName,
    String roomType,          // BEDROOM, KITCHEN, BATHROOM, etc.
    double minX, double minY, double minZ,
    double maxX, double maxY, double maxZ,
    Map<String, WallFace> faces,     // NORTH/SOUTH/EAST/WEST → wall info
    List<BoundingBox> reservedZones  // already claimed by prior workers
) {
    double width()  { return maxX - minX; }
    double depth()  { return maxY - minY; }
    double height() { return maxZ - minZ; }

    /** Get the wall face for a slot_face anchor (BACK, FRONT, LEFT, RIGHT) */
    WallFace getFace(String slotFace) { ... }
}
```

### PlacementContext

Shared context passed to all workers:

```java
record PlacementContext(
    double storeyZ,           // floor level Z
    double ceilingZ,          // ceiling level Z
    double slabThickness,     // structural slab thickness
    String constructionSystem // FRAMED or MASONRY
) {}
```

### PlacedElement

What every worker returns — uniform output:

```java
record PlacedElement(
    String name,
    String ifcClass,
    double x, double y, double z,
    double width, double depth, double height,
    double rotation,
    String geometryHash,      // LOD400 library geometry (nullable for parametric)
    String role,              // BOM role (DESK, TOILET, SPRINKLER_HEAD, etc.)
    String assemblyId         // parent assembly (nullable)
) {}
```

## Dispatch Protocol

### Current (Hardcoded)

```java
// StoreyCompiler.java — 8 explicit calls, each different signature
furniturePlacer.place(room, bounds, openings, floorZ);
fixturePlacer.placeToilet(bounds, wallSide, floorZ);
sprinklerPlacer.place(room, ceilingZ, slabT);
electricalPlacer.placeOutlets(room, floorZ);
hvacPlacer.place(room, ceilingZ);
plumbingPlacer.place(room, stackX);
fireSuppressionPlacer.place(room, ceilingZ, mainZ);
structuralPlacer.place(room, floorZ);
```

### Target (Slot-Dispatched)

```java
// StoreyCompiler.java — one loop, N workers
List<RoomSlot> slots = SlotRegistry.getSlotsForType(room.type());
List<BoundingBox> reserved = new ArrayList<>();

for (RoomSlot slot : slots) {                    // ordered by priority
    BundleWorker worker = WorkerRegistry.get(slot.assemblyId());
    RoomEnvelope envelope = new RoomEnvelope(room, reserved);
    List<PlacedElement> elements = worker.execute(envelope, ctx);
    reserved.add(worker.reservedEnvelope());     // spatial reservation
    allElements.addAll(elements);
}
```

### SlotRegistry reads from ad_room_slot

```
BATHROOM:
  priority=10  SANITARY        → BathroomFixtureWorker    face=BACK
  priority=20  BASIN           → BasinWorker              face=LEFT
  priority=30  EXHAUST         → ExhaustFanWorker         face=TOP
  priority=40  CEILING_MEP     → CeilingMEPWorker         face=TOP

BEDROOM:
  priority=10  FURNITURE       → FurnitureWorker          face=BACK
  priority=20  CEILING_MEP     → CeilingMEPWorker         face=TOP

KITCHEN:
  priority=10  COUNTER         → KitchenCounterWorker     face=BACK
  priority=20  CEILING_MEP     → CeilingMEPWorker         face=TOP
```

Adding a new worker = SQL INSERT into ad_room_slot + one Java class implementing BundleWorker.
No StoreyCompiler change. No recompilation of other workers.

## Face-Aligned Placement

Every element declares which face it anchors to. The placement engine resolves
face → absolute coordinates. Workers declare intent, not positions.

```
Face BACK  → element.y = room.maxY - clearance
Face FRONT → element.y = room.minY + clearance
Face LEFT  → element.x = room.minX + clearance
Face RIGHT → element.x = room.maxX - clearance
Face TOP   → element.z = ceiling.z - offset
Face BOTTOM→ element.z = floor.z + offset
```

The MANIFEST contract (ad_assembly_manifest) already defines clearance per face.
ManifestResolver already loads these. The missing piece: wiring face resolution
into the placement pipeline.

## SpatialDigest — Deterministic Verification

### The Problem

Current E2E tests check element counts ("10516 elements"). But:
- Count doesn't catch position drift (element moved 0.1m = same count, wrong building)
- Count doesn't catch dimension changes (wall got thinner = same count, wrong wall)
- Visual verification in Blender is tedious and non-reproducible

### The Solution

**SpatialDigest**: SHA256 hash of all element bounding boxes, sorted deterministically.

```java
public class SpatialDigest {

    /** Compute SHA256 digest of all elements in output DB. */
    public static String compute(String dbPath) {
        // 1. Query all elements: SELECT name, ifc_class, minX..maxZ
        // 2. Sort by (ifc_class, name, minX, minY, minZ)
        // 3. Round coordinates to 1mm precision (avoid float noise)
        // 4. Concatenate as "IfcWall|wall_north|1234|5678|0|1384|5828|3100\n"
        // 5. SHA256 the concatenated string
        return sha256hex;
    }
}
```

### What It Catches

| Change | Count Test | SpatialDigest |
|--------|-----------|---------------|
| Element moved 0.1m | PASS (same count) | **FAIL** (different hash) |
| Wall thickness changed | PASS (same count) | **FAIL** (different bbox) |
| Element added/removed | FAIL | **FAIL** |
| Element renamed | PASS (same count) | **FAIL** (different sort) |
| Identical rebuild | PASS | **PASS** (same hash) |
| Float rounding drift | PASS | **PASS** (1mm rounding) |

### Usage in E2E Tests

```java
// First run: compute and record digest
String digest = SpatialDigest.compute("output/condo_mid.db");
System.out.println("Digest: " + digest);

// Regression: assert digest matches known-good value
assertEquals("a1b2c3d4...", digest, "Spatial regression detected!");
```

### Two Birds, One Stone

1. **Sizing verification**: The digest encodes every element's bounding box.
   If any dimension changes (wall thickness, room size, furniture placement),
   the hash changes. No need for separate dimension assertions.

2. **Regression testing**: The digest is the "golden master" of the build.
   Commit the known-good digest. Any code change that moves geometry = test failure.
   No visual inspection needed.

## Migration Path (Progressive)

### Phase 118A: Foundation (This Session)
- [x] BundleWorker interface (contract/)
- [x] SpatialDigest utility
- [x] Wire digest into one E2E test
- [x] This vision document

### Phase 118B: First Worker
- [ ] Adapt FurnitureBOMResolver → FurnitureWorker implementing BundleWorker
- [ ] Wire ad_room_slot dispatch for BEDROOM slot (simplest case)
- [ ] Face-aligned placement for BACK anchor
- [ ] Verify SpatialDigest unchanged (zero behavior change)

### Phase 118C: Slot Dispatcher
- [ ] WorkerRegistry (themeId → BundleWorker instance)
- [ ] SlotDispatcher loop in StoreyCompiler (replaces explicit placer calls)
- [ ] Spatial reservation (reserved zones passed to next worker)
- [ ] Adapt FixturePlacer → BathroomFixtureWorker

### Phase 118D: Full Migration
- [ ] Adapt remaining 6 placers to BundleWorker
- [ ] Remove explicit placer calls from StoreyCompiler
- [ ] StoreyCompiler shrinks to ~200 lines (slot dispatch only)
- [ ] Full SpatialDigest regression suite (all 6 E2E tests)

### Phase 118E: Spatial Reservation
- [ ] Envelope algebra (BoundingBox intersection/subtraction)
- [ ] Reserved zone enforcement (worker sees only free space)
- [ ] Zero-clash guarantee (mathematical, not visual)

## Relationship to Existing Architecture

```
ARCHITECTURE.md §1.3      "DSL Selects. BOM Parameterises. Java Resolves."
                            ↓
PREFAB_ARCHITECTURE.md     Assembly hierarchy (Level -1 to Level 4)
                            ↓
BUNDLE_WORKER_FRAMEWORK.md  Workers execute slots, face-aligned, spatially reserved
                            ↓
CatalogContract.java        DSL references must exist in catalog (Phase 117)
BundleWorker.java           Workers must implement this interface (Phase 118)
SpatialDigest.java          Output must be deterministically reproducible (Phase 118)
```

## OSGi Lineage

| OSGi Concept | BIM Compiler Equivalent |
|---|---|
| Bundle | BundleWorker implementation class |
| Bundle-SymbolicName | themeId() |
| Export-Package | PlacedElement list (output) |
| Import-Package | RoomEnvelope + PlacementContext (input) |
| Require-Capability | anchorFace() + MANIFEST clearances |
| Provide-Capability | reservedEnvelope() |
| Service Registry | WorkerRegistry (themeId → worker) |
| Declarative Services | ad_room_slot (SQL-driven registration) |
| Bundle Lifecycle | execute() = start/run/stop in one call |

The key insight from OSGi: **loose coupling, tight cohesion**. Each worker owns
its placement logic completely (cohesion) but communicates only through typed
interfaces (coupling). Adding a new worker never requires changing existing workers.

---

*"Each worker arrives, reads the blueprint, does their job, and leaves.
The building emerges from the collaboration of specialists, not from
a single omniscient compiler."*
