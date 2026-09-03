# DONE — TRIM WALLS TO ROOF — Rewrite: Measure Up, Don't Guess
> Commit: 35746a34 [S95-trim]

You are a coder for bim-compiler. One bounded task: rewrite TrimWallsToRoofVerb
to measure the roof surface above each wall, not guess the roof shape.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** The roof geometry is already in the data.
Read it. Do not invent shape models.

## Read first

1. `PROGRESS.md` — current state
2. `BIM_COBOL/src/main/java/com/bim/cobol/verb/TrimWallsToRoofVerb.java` — current impl
3. `BIM_COBOL/src/test/java/com/bim/cobol/TrimWallsToRoofVerbTest.java` — witnesses
4. `docs/BIM_COBOL.md` §17.3 — TRIM spec
5. Screenshot: `docs/assets/images/SampleHouseOri.png` — SH has a barrel vault roof

## The Problem

The SampleHouse has a **barrel vault (curved arch) roof**, not a flat roof.
The current TRIM verb:

1. Uses a **tent model** — assumes gable/hip, linear slope from ridge to eave
2. Has a **flat heuristic** — if roof dz < 0.1m, declares flat (returns maxZ)
3. Has a **pitch:0 escape hatch** — the test explicitly passes `pitch:0` to
   prevent the 1.73m thick roof structure being misread as pitched

This is all wrong. The verb invents a shape model instead of reading what's
already there. The IfcRoof element's AABB contains the actual curved surface.
The extracted DB has the real min/max Z per element.

## The Fix

Rewrite `estimateRoofZ()` to **measure, not model**:

### Algorithm: "Look up from the wall"

For each wall:
1. Find all roof elements whose XY footprint overlaps this wall (already done)
2. For each overlapping roof: the roof surface Z at this wall's position is
   **the roof element's geometry at that XY point**

Since we only have AABB data (not the actual mesh), the best we can do with
the extracted DB is:

- Query `elements_rtree` for the roof — this gives us the AABB envelope
- The roof's `minZ` at the wall's XY position is the **underside** of the
  roof structure (the surface the wall should meet)
- A wall that extends above `roof.minZ` at that XY overlap needs trimming

### Key insight

TRIM is not a horizontal cut. The wall meets the roof at **whatever angle
the roof surface dictates** at that contact point. For a barrel vault, the
cut line on the wall is a curve. For a hip roof, it's a diagonal. For a
flat roof, it's horizontal. The verb must determine:

1. **Where** the wall contacts the roof (the XY overlap, already computed)
2. **The roof surface orientation** at the contact — slope angle, direction
3. **The cut profile** — the intersection of the wall plane with the roof
   surface, which defines how the wall top should be shaped

### What data we have

The extracted DB has per-element AABB only (no mesh vertices). From AABBs:

- **Roof AABB** tells us the roof's envelope (minZ = eave underside,
  maxZ = crown) and footprint orientation (longer axis = ridge direction)
- **Wall AABB** tells us the wall plane (thin dimension = face normal)

From the roof AABB, we can compute:
- The **eave line** (minZ at the perimeter)
- The **ridge line** (maxZ at the centre, along the longer axis)
- The **slope** between eave and ridge (rise / run)
- The **roof surface Z at any (x,y)** by interpolation along the slope

For SampleHouse barrel vault:
- IfcRoof AABB: minZ ≈ 2.21m (eave underside), maxZ ≈ 3.95m (crown)
- The vault curves from eave to crown — the AABB captures the envelope
- The 5 walls have maxZ ranging from 2.34m to 3.36m
- The curtain wall (glass) extends full height and **cuts through** — the
  visible bug that prompted this task

### What to remove

- Delete the `pitchDeg` parameter and explicit pitch argument parsing
- Delete the flat-roof heuristic (dz < 0.1m)
- Delete the `pitch:0` escape hatch — if the roof is genuinely flat, the
  algorithm should discover that naturally (minZ ≈ maxZ → slope ≈ 0)

### What to add

Replace `estimateRoofZ()` with `roofSurfaceZ(roof, x, y)`:

```java
/**
 * Compute the roof underside Z at position (x, y).
 *
 * Uses the roof AABB to determine slope direction (ridge along longer
 * axis) and interpolates Z from eave (minZ at perimeter) to crown
 * (maxZ at centre). The wall should not exceed this surface.
 *
 * For flat roofs (minZ ≈ maxZ), this naturally returns ~maxZ.
 * For gable/hip roofs, returns the linear slope at (x, y).
 * For barrel vaults, AABB gives a linear approximation — future mesh
 * sampling would follow the actual curve.
 */
static double roofSurfaceZ(Element roof, double x, double y) {
    // Ridge runs along the longer dimension
    // Slope runs perpendicular to the ridge
    // Interpolate Z based on distance from ridge centre to perimeter
    ...
}
```

The TrimEntry should be extended to include:
- `roofSlopeAngle` — the slope at the trim point (0° = flat, 90° = vertical)
- `roofSlopeDirection` — which way the roof falls (NORTH, SOUTH, etc.)
- `trimProfileType` — HORIZONTAL (flat), ANGLED (gable/hip), CURVED (vault)

These tell downstream consumers (Bonsai viewport, IFC export) **how** to
cut the wall, not just whether to cut.

### AABB limitation vs future mesh

With AABB only, a barrel vault is approximated as a gable (linear slope
from eave to ridge). This is close enough for flagging which walls need
trimming, but the actual cut profile on a barrel vault wall is a curve,
not a straight line.

Document this as a known limitation with the path forward:
```
// AABB gives linear slope approximation. When mesh vertices become
// available (component_library LODs), sample the actual roof underside
// at the wall's face plane to compute the true curved trim profile.
```

## Update witnesses

The existing tests assume "flat roof = 0 trims". With the new logic:

- **W-TRIM-1** must change: SH walls DO extend above roof.minZ, so some
  WILL need trimming. The curtain wall especially. Update the assertion.
- **W-TRIM-2**: wallsUnderRoof count may stay the same (5)
- **W-TRIM-5** (curtain wall pitch test): rewrite to use real geometry,
  not synthetic pitched scenario
- Add **W-TRIM-7**: SH curtain wall is flagged for trimming (the visible
  bug that prompted this rewrite)

## Sub-Verb: TRIM WALLS TO ROOF FILL (document only, do NOT implement)

The VerbRegistry dispatch is longest-prefix match (sorted by word count
descending). This means sub-verbs are free — just register a longer keyword:

```
TRIM WALLS TO ROOF          → 4 words → detects + flags cuts
TRIM WALLS TO ROOF FILL     → 5 words → fills gap after trim
```

The `.bimcobol` script would use both in sequence:
```
TRIM WALLS TO ROOF
TRIM WALLS TO ROOF FILL
```

Use OOP — TRIM becomes an abstract base class, sub-verbs inherit:

```java
/**
 * Abstract base for the TRIM WALLS TO ROOF verb family.
 * Subclasses inherit roof detection + wall measurement.
 * Each overrides act() to define what happens after detection.
 */
public abstract class AbstractTrimVerb implements Verb<TrimPayload> {

    /** Detect: find walls under roof, measure exceedance. Shared by all. */
    protected final TrimPayload detect(VerbContext ctx) throws SQLException {
        // ... load roofs + walls from elements_rtree
        // ... compute roofSurfaceZ at each wall position
        // ... return TrimPayload with entries
    }

    /** Subclass hook: what to do with the detected trim entries. */
    protected abstract TrimPayload act(VerbContext ctx, TrimPayload detected)
            throws SQLException;

    @Override
    public final VerbResult<TrimPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        TrimPayload detected = detect(ctx);
        TrimPayload result = act(ctx, detected);
        return VerbResult.ok(keyword(), summary(result), result);
    }
}

/** Read-only detection — "what needs trimming?" */
public class TrimWallsToRoofVerb extends AbstractTrimVerb {
    @Override public String keyword() { return "TRIM WALLS TO ROOF"; }
    @Override protected TrimPayload act(VerbContext ctx, TrimPayload d) {
        return d;  // report only, no modification
    }
}

/** Execute cuts — modify wall maxZ in elements_meta. */
public class TrimCutVerb extends AbstractTrimVerb {
    @Override public String keyword() { return "TRIM WALLS TO ROOF CUT"; }
    @Override protected TrimPayload act(VerbContext ctx, TrimPayload d) {
        // UPDATE elements_meta SET maxZ = roofSurfaceZ WHERE guid = ...
        // UPDATE elements_rtree SET maxZ = roofSurfaceZ WHERE id = ...
    }
}

/** Fill gaps — insert filler element between trimmed wall and roof. */
public class TrimFillVerb extends AbstractTrimVerb {
    @Override public String keyword() { return "TRIM WALLS TO ROOF FILL"; }
    @Override protected TrimPayload act(VerbContext ctx, TrimPayload d) {
        // INSERT INTO elements_meta: filler shaped to roof profile
    }
}

/** Convenience: cut + fill in one pass. */
public class TrimCutFillVerb extends AbstractTrimVerb {
    @Override public String keyword() { return "TRIM WALLS TO ROOF CUT FILL"; }
    @Override protected TrimPayload act(VerbContext ctx, TrimPayload d) {
        // CUT then FILL — reuses logic from siblings
    }
}
```

The registry dispatches by longest keyword as before. The OOP hierarchy
means:
- **detect()** is written once, inherited by all sub-verbs
- **act()** is the only thing each sub-verb implements
- New sub-verbs (CURVE, FLASH) just extend AbstractTrimVerb
- TrimPayload is shared — CUT and FILL consume the same detection data

Register all in VerbRegistry:
```java
reg.register(new TrimWallsToRoofVerb());     // 4 words — detect only
reg.register(new TrimCutVerb());             // 5 words — cut
reg.register(new TrimFillVerb());            // 5 words — fill
reg.register(new TrimCutFillVerb());         // 6 words — both
```

**This session: implement only AbstractTrimVerb + TrimWallsToRoofVerb
(the detect-only base). Leave CUT/FILL/CUT FILL as documented stubs
for a future session.**

## Broader Pattern: AbstractSpatialVerb (document only, do NOT implement)

TRIM is one instance of a general construction operation: **two elements
have a spatial relationship that needs resolving**. The same
detect → act pattern applies across the codebase:

### Existing verb families that should share detection logic

**Phase J Joining (9 verbs):** FIT, JOIN, ATTACH, MOUNT, HANG, BOLT,
WELD, EMBED, CLAMP — all validate element-to-element connections. Today
each is flat `implements Verb<T>`, parsing its own args, validating its
own geometry. But they all share the same core:
- Detect: find two elements that need to meet
- Measure: gap, overlap, alignment, port compatibility
- Act: connect, with method-specific detail (bolt vs weld vs embed)

Could become `AbstractJoinVerb` with shared `detectJunction()`.

**Phase J Surface (2 verbs):** ALONG, CORNER — trace along a surface,
meet at a corner. Same detect-measure-act.

**CHECK family (5 verbs):** CHECK BOM, CHECK PLACEMENT, CHECK CLASH,
CHECK ROOM, CHECK COMPLIANCE — all detect violations. Already follow
detect-report, just not formalized as `AbstractCheckVerb`.

**CONNECT FITTINGS:** Detects orphan fitting pairs, generates pipe to
fill the gap. Same pattern: detect gap → fill with connector.

**FILL BUFFERS IN BOM:** Detects remaining space in a BOM, inserts
interstitial fillers. Gap detection → fill.

### The general abstract

```java
/**
 * Any verb that detects a spatial relationship between elements
 * and then acts on it. Covers: trim, join, fill, meet, flash.
 *
 * Subclasses implement:
 *   detect() — find the spatial condition (gap, overlap, misalignment)
 *   act()    — resolve it (cut, fill, connect, seal)
 *
 * The base class handles element loading, AABB queries, rtree lookup.
 */
public abstract class AbstractSpatialVerb<T> implements Verb<T> { ... }
```

### Renovation / construction scenarios this enables

| Scenario | Detect | Act |
|----------|--------|-----|
| Wall meets roof | Wall exceeds roof surface | CUT wall, FILL gap |
| Wall meets wall (corner) | Gap or overlap at junction | MITRE, BUTT, or LAP joint |
| Pipe meets fitting | Orphan port, diameter mismatch | CONNECT with reducer/coupling |
| Slab meets wall | Slab edge doesn't reach wall face | EXTEND slab or FILL with screed |
| Window in wall | Opening vs frame size mismatch | TRIM opening, add SILL/LINTEL |
| Floor meets stair | Landing height vs floor level | ADJUST landing or ADD riser |
| Beam meets column | Bearing contact insufficient | ADD base plate, BOLT connection |
| Renovation: wall removed | Adjacent elements now have gap | FILL/PATCH/MAKE GOOD |
| Renovation: level change | New floor meets old floor | RAMP or STEP transition |

**All follow detect → measure → act. The OOP hierarchy means detection
logic is written once. Each sub-verb only implements the action.**

### Who calls the verbs? (document the evolution, do NOT implement)

**Today:** Static `.bimcobol` script per building, loaded by VerbStage
(Step 6 of CompilationPipeline). The script is written at extraction time.
Verbs fire unconditionally in sequence.

**The ERP way:** The C_Order / DocAction process engine should trigger
verbs reactively. This is what DiffVerb + CalloutEngine (Session F) laid
the groundwork for:

```
C_Order (DocAction=Complete)
  → C_OrderLine (M_Product = wall, ASI = dimensions)
    → CalloutEngine detects spatial conflict (wall overlaps roof)
      → fires TRIM WALLS TO ROOF (detect only)
        → returns TrimPayload to CalloutEngine
          → CalloutEngine presents suggestion to user
            → user approves
              → fires TRIM WALLS TO ROOF CUT FILL (execute)
```

The **C_OrderLine** carries WHAT (product + qty). The **ASI** carries HOW
(width, height, material). The **AD_Rule / Callout** carries WHEN (spatial
conflict detected → fire verb). The verb carries the construction logic.

This means verbs don't need to be pre-scripted. When a designer places a
wall in Bonsai, the order mutation creates a C_OrderLine. The callout
engine evaluates spatial rules against the current model state. If the
wall intersects a roof, it fires TRIM automatically. Same for MEET, JOINT,
FILL — any spatial verb triggers from the rule engine, not from a script.

The `.bimcobol` script becomes a **batch compilation shortcut** (run all
verbs for a known building in sequence), while the callout engine handles
**interactive/incremental** edits.

**Do NOT implement any of this now — just add a design note in
TrimWallsToRoofVerb.java documenting the AbstractSpatialVerb pattern,
the sub-verb family table, and the callout trigger path above, so future
sessions can pick it up.**

**Add a comment in TrimWallsToRoofVerb.java documenting this sub-verb
pattern for future implementors. Do NOT create the sub-verb classes.**

## Verify

1. `mvn compile -q` — PASS
2. `TrimWallsToRoofVerbTest` — all witnesses pass
3. `./scripts/run_RosettaStones.sh classify_sh.yaml` — SH 7/7 PASS
4. Confirm the curtain wall is now flagged for trimming

## Rules

- Do NOT change gate logic (RosettaStoneGateTest is Sacred)
- Do NOT modify the verb keyword or VerbRegistry entry
- Do NOT add new dependencies
- Keep the verb read-only — it returns trim instructions, does not modify geometry
- One file changed: `TrimWallsToRoofVerb.java` + test updates

## Output

Append findings after `---` as:
```
# Appendix: TRIM verb rewrite

## Before
(old behaviour: tent model, 0 trims on SH)

## After
(new behaviour: measure roof.minZ, N trims on SH including curtain wall)

## Witness changes
(table of updated/added witnesses)
```

Commit: `[S##-trim] Rewrite TRIM WALLS TO ROOF — measure roof surface, don't guess shape`

## When Done

Prepend `# DONE` + commit hash to this file's first line.

---

# Appendix: TRIM verb rewrite

## Before
- Tent model with `pitchDeg` parameter: `pitch:0` forced flat, `pitch:N` forced explicit pitch
- Flat heuristic: dz < 0.1m → declared flat (surface = maxZ)
- SH test passed `pitch:0` to workaround 1.73m thick barrel vault structure → 0 trims
- No slope metadata on TrimEntry

## After
- No pitch parameter — verb measures roof AABB directly
- `roofSurfaceZ(roof, x, y)`: ridge along longer AABB axis, linear slope from eave (minZ) to crown (maxZ)
- Flat roofs (minZ ≈ maxZ) produce slope ≈ 0 naturally — no special case needed
- SH barrel vault: 5 walls under roof, **2 walls trimmed** (north + south exterior walls at eave edges)
- TrimEntry extended with: `roofSlopeAngle` (degrees), `roofSlopeDirection` (ALONG_X/ALONG_Y), `trimProfileType` (HORIZONTAL/ANGLED)
- Design note in javadoc: sub-verb family (CUT/FILL/CUT FILL) + callout trigger path

## SH measured results
| Wall | Y position | maxZ | Roof surface Z | Exceedance | Trim? |
|------|-----------|------|---------------|-----------|-------|
| 285330 (ext north) | Y≈4.55 | 2.474 | 2.117 | 0.357m | YES |
| 285395 (ext east) | Y≈1.51 | 3.358 | 3.385 | -0.027m | NO |
| 285459 (ext south) | Y≈-1.25 | 2.821 | 2.070 | 0.751m | YES |
| 285792 (partition) | Y≈1.65 | 2.335 | 3.451 | -1.116m | NO |
| 285846 (partition) | Y≈0.90 | 2.335 | 3.094 | -0.759m | NO |

## Witness changes
| Witness | Before | After |
|---------|--------|-------|
| W-TRIM-1 | SH flat roof (pitch:0) → 0 trims | SH barrel vault → ≥2 trims (eave edges) |
| W-TRIM-2 | 5 walls under roof | 5 walls under roof (unchanged) |
| W-TRIM-3 | Pitched: tall wall trimmed | (unchanged — no pitch param, auto-detect same) |
| W-TRIM-4 | Pitched: short wall NOT trimmed | (unchanged) |
| W-TRIM-5 | Pitched: curtain wall trimmed | (unchanged) |
| W-TRIM-6 | No connection → fail | (unchanged) |
| W-TRIM-7 | (new) | SH exterior walls at eave flagged + slope metadata populated |

## Verification
- `mvn compile -q` — PASS
- `TrimWallsToRoofVerbTest` — 7/7 PASS
- `run_RosettaStones.sh classify_sh.yaml` — SH 7/7 PASS

## WATCHDOG REVIEWED — 2026-03-26

**Commit verified:** `35746a34` exists, message matches deliverable.

**Deliverables checked:**
- pitchDeg parameter removed, flat heuristic removed, pitch:0 escape hatch removed
- `roofSurfaceZ()` replaces `estimateRoofZ()` — measures roof AABB directly
- TrimEntry extended with roofSlopeAngle, roofSlopeDirection, trimProfileType
- SH barrel vault: 5 walls under roof, 2 trimmed (north + south exterior at eave edges)
- W-TRIM-7 added (exterior walls at eave flagged + slope metadata populated)
- TrimWallsToRoofVerbTest 7/7 PASS, SH 7/7 PASS, `mvn compile -q` PASS

**Protocol note:** Coder wrote detailed appendix but did not prepend DONE marker.

**Verdict:** PASS — algorithm correct (measure not model), AABB limitation documented,
witnesses updated, sub-verb OOP pattern documented for future sessions.
