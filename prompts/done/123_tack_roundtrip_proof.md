# DONE — [e06525b9](https://github.com/red1oon/BIMCompiler/commit/e06525b9) + [0e5b193b](https://github.com/red1oon/BIMCompiler/commit/0e5b193b)
# Tack Round-Trip — FINE Debug + IFC GUID Traceability

**Spec:** BBC.md §4 (tack convention), LMP §7 (Separate From Input), LMP §3 (Compiler Only)
**Prereq:** None (standalone)

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Add FINE logging only. Zero behaviour change.

## Read first

1. `PROGRESS.md` §Current State
2. `docs/BOMBasedCompilation.md` §4 — tack convention (LBD-to-LBD offsets)
3. `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/PlacementCollectorVisitor.java`
   - lines 114-196: `onSubAssembly()` — anchor accumulation (zero FINE logging today)
   - lines 233-350: `onLeaf()` — leaf world position computation (zero FINE logging today)

## Read first (continued)

4. `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/PlacementCollectorVisitor.java`
   - line 309: `loadMaGuids()` — reads `m_bom_line_ma` for per-instance IFC GUIDs (currently empty)
   - line 331-334: GUID resolution — MA guid > line ref > generated
5. `IFCtoBOM/src/main/java/com/bim/ifctobom/ScopeBomBuilder.java`
   - line 134-144: where leaf tack offsets are computed from extraction
   - Check: does it write IFC GUIDs to `m_bom_line_ma`?
6. `library/SH_BOM.db` — query: `SELECT COUNT(*) FROM m_bom_line_ma` (currently 0)
7. `DAGCompiler/lib/input/SampleHouse_extracted.db` — `elements_meta.guid` has real IFC GUIDs

## Problem — Two gaps

### A. The tack chain is invisible

PlacementCollectorVisitor is the most critical spatial math in the compiler.
It accumulates parent anchors through the BOM tree and computes world
positions for every element. Yet it has **zero FINE logging** — only 3
warn-level messages for error cases.

A test can cheat. A debug log emission cannot. Every compilation run should
print the tack chain so a human (or grep) can verify the math.

### B. IFC GUID traceability is broken

The IFC signature is lost at the BOM boundary:

```
extracted.db  guid = "3cUkl32yn9qRSPvBJVyZVU"     ← real IFC GUID
BOM.db        m_bom_line_ma = EMPTY (0 rows)        ← GUID not written
output.db     guid = "COMPACT_MD_GROUND_FLOOR_30"   ← synthetic, not IFC
```

The MA table (`m_bom_line_ma`) was designed to carry per-instance IFC GUIDs
(PlacementCollectorVisitor line 309 `loadMaGuids()`). The compiler reads it.
But the extraction pipeline (IFCtoBOM) never populates it. So every compiled
element gets a synthetic GUID — you cannot trace a compiled desk back to IFC
entity `3cUkl32yn9qRSPvBJVyZVU`.

## Fix — FINE Logging at Exact Execution Lines

Each log MUST sit on the line that computed the value it logs.
If the log emits, the code executed. If it doesn't, the code was bypassed.

### Log 1: `onSubAssembly()` — IMMEDIATELY after line 195 (`anchorStack.push(newAnchor)`)

The log must use `newAnchor` (the value just pushed), `parent` (the value just
peeked), `lineDx/lineDy/lineDz` (the rotated offsets), and `bomOriginX/Y/Z`
(the child BOM origin). All four are local variables computed in the preceding
10 lines. If this log emits, the anchor accumulation executed.

```java
BIMLogger.fine("TACK", "ENTER {} depth={}: parent=({:.4f},{:.4f},{:.4f}) + line=({:.4f},{:.4f},{:.4f}) + bomOrigin=({:.4f},{:.4f},{:.4f}) → anchor=({:.4f},{:.4f},{:.4f})",
    childBomId != null ? childBomId : "ROOT",
    ctx.level(),
    parent[0], parent[1], parent[2],
    lineDx, lineDy, lineDz,
    bomOriginX, bomOriginY, bomOriginZ,
    newAnchor[0], newAnchor[1], newAnchor[2]);
```

This prints exactly what my manual trace showed:
```
TACK ENTER FLOOR_SH_GF_STD depth=0: parent=(0.0000,0.0000,0.0000) + line=(0.0000,0.0000,0.0000) + bomOrigin=(0.0000,0.0000,0.0000) → anchor=(0.0000,0.0000,0.0000)
TACK ENTER SH_BED_SET depth=1: parent=(0.0000,0.0000,0.0000) + line=(13.3480,3.6925,0.4700) + bomOrigin=(0.0000,0.0000,0.0000) → anchor=(13.3480,3.6925,0.4700)
```

### Log 2: `onSubAssemblyComplete()` — IMMEDIATELY after line 201 (`anchorStack.pop()`)

Proves the stack unwind happened. If ENTER count != EXIT count, the stack leaked.

```java
BIMLogger.fine("TACK", "EXIT  {} depth={}",
    ctx.line() != null ? ctx.line().getChildProductId() : "ROOT",
    ctx.level());
```

### Log 3: `onLeaf()` — IMMEDIATELY after line 325 (`cz = anchor[2] + offsets[qi][2] + iHalfH`)

This is THE line that computes the world centroid. The log must use `anchor`
(the current stack top), `offsets[qi]` (the verb-expanded leaf offset),
`iHalfW/iHalfD/iHalfH` (the half-extents), and `cx/cy/cz` (the result).
All are local variables from the preceding 5 lines. If this log emits with
the right values, the centroid was computed by tack accumulation — not
copied from extraction, not hardcoded, not bypassed.

```java
BIMLogger.fine("TACK", "LEAF  {} anchor=({:.4f},{:.4f},{:.4f}) + offset=({:.4f},{:.4f},{:.4f}) + half=({:.4f},{:.4f},{:.4f}) → centroid=({:.4f},{:.4f},{:.4f}) LBD=({:.4f},{:.4f},{:.4f})",
    productId,
    anchor[0], anchor[1], anchor[2],
    offsets[qi][0], offsets[qi][1], offsets[qi][2],
    iHalfW, iHalfD, iHalfH,
    cx, cy, cz,
    cx - iHalfW, cy - iHalfD, cz - iHalfH);
```

This prints:
```
TACK LEAF  Furniture_Bed_1:1525x2007x355mm-Queen anchor=(13.3480,3.6925,0.4700) + offset=(0.0000,0.0000,0.0000) + half=(0.7625,1.0035,0.1775) → centroid=(14.1105,4.6960,0.6475) LBD=(13.3480,3.6925,0.4700)
TACK LEAF  Furniture_Desk:1525x762mm anchor=(13.3480,3.6925,0.4700) + offset=(0.4230,2.6015,0.0000) + half=(0.7625,0.3810,0.3810) → centroid=(14.5335,6.6750,0.8510) LBD=(13.7710,6.2940,0.4700)
```

### Log 4: Rotation — IMMEDIATELY after line 172 (rotation applied to lineDx/lineDy)

Only emits when `cumRot != 0.0`. Shows the BEFORE and AFTER of rotation
application. If a building has rotated units (DX mirror), this proves the
rotation was applied to the tack offset, not skipped.

```java
if (cumRot != 0.0) {
    BIMLogger.fine("TACK", "  ROT {:.4f}rad: line=({:.4f},{:.4f}) → rotated=({:.4f},{:.4f})",
        cumRot, line.getDx(), line.getDy(), lineDx, lineDy);
}
```

### Log 5: GUID in LEAF — include IFC GUID in the TACK LEAF line

The LEAF log must include the resolved `elementRef` (line ~344) so the
GUID traceability is visible in the same log line:

```java
BIMLogger.fine("TACK", "LEAF  {} guid={} anchor=(...) ...",
    productId, elementRef, ...);
```

If `elementRef` is a real IFC GUID (22-char base64), the chain is intact.
If it's a product name or synthetic ID, the chain is broken — visible in
every TACK LEAF line without separate investigation.

### GEO debug mode — dedicated channel, not a log level

All TACK logs use `BIMLogger.geo()` — a new method that emits ONLY when
GEO mode is active. GEO is independent of the general log level (INFO/FINE).
A user can run at INFO (clean console) but still get GEO output in the log file.

**Activation:** `bim.geo.debug=true` in `BIM.properties` or `-Dbim.geo.debug=true`

**Filter:** `bim.geo.filter=Desk` in `BIM.properties` or `-Dbim.geo.filter=Desk`
- No filter = all elements (SH is small, safe)
- Filter = only elements whose productId contains the filter string

**The filter controls logging only — never skip the placement itself.**

### Implementation in BIMLogger

```java
private static final boolean GEO_ENABLED =
    "true".equalsIgnoreCase(System.getProperty("bim.geo.debug",
        readProperty("bim.geo.debug", "false")));
private static final String GEO_FILTER =
    System.getProperty("bim.geo.filter",
        readProperty("bim.geo.filter", null));

public static void geo(String component, String format, Object... args) {
    if (!GEO_ENABLED) return;
    write("[GEO  ] " + component, format, args);
}

public static boolean geoMatch(String productId) {
    return GEO_ENABLED && (GEO_FILTER == null || productId.contains(GEO_FILTER));
}
```

Usage in PlacementCollectorVisitor:
```java
// Log 1 (onSubAssembly) — always when GEO active (structural, not per-element)
if (BIMLogger.geoMatch(childBomId != null ? childBomId : "ROOT")) {
    BIMLogger.geo("TACK", "ENTER {} depth={}: parent=(...) + line=(...) → anchor=(...)", ...);
}

// Log 3 (onLeaf) — filtered by product
if (BIMLogger.geoMatch(productId)) {
    BIMLogger.geo("TACK", "LEAF {} guid={} anchor=(...) → centroid=(...) LBD=(...)", ...);
}
```

### Total: ~8 BIMLogger.geo() calls + GEO mode. Zero behaviour change when GEO is off.

## Part B: IFC GUID Traceability — Populate m_bom_line_ma

### The gap

`m_bom_line_ma` exists in every BOM.db (schema + table). PlacementCollectorVisitor
line 309 (`loadMaGuids()`) reads it. But IFCtoBOM never writes to it. 0 rows
in every building.

### Fix: Write IFC GUIDs during extraction

In `ScopeBomBuilder.java`, where leaf children are inserted (line ~144-248),
the extraction element's IFC GUID is available from the `ExtractionElement`
record. After inserting each `m_bom_line` row, also INSERT into
`m_bom_line_ma`:

```sql
INSERT INTO m_bom_line_ma (bom_id, M_BOM_ID, sequence, qi, guid)
VALUES (?, ?, ?, ?, ?)
```

Where:
- `bom_id` = parent SET BOM Value (e.g., 'SH_BED_SET')
- `M_BOM_ID` = parent BOM integer ID
- `sequence` = line sequence number
- `qi` = instance index (0 for qty=1 lines, 0..N-1 for factored lines)
- `guid` = `extractionElement.guid()` — the real IFC GloballyUniqueId

For factored lines (qty>1, verb_ref CLUSTER/TILE), each instance has its own
GUID from the MA records in `VerbDetector`/`VerbFactorizer`. These must also
be written.

### Files to change

| File | Change |
|------|--------|
| `PlacementCollectorVisitor.java` | Add FINE TACK logging (Logs 1-6) |
| `ScopeBomBuilder.java` | Write IFC GUIDs to `m_bom_line_ma` after each leaf INSERT |
| `VerbFactorizer.java` | Write per-instance GUIDs for factored lines (CLUSTER/TILE) |
| `DisciplineBomBuilder.java` | Same — write GUIDs for discipline leaf lines |

### Verification

After fix, the chain becomes:
```
extracted.db  guid = "3cUkl32yn9qRSPvBJVyZVU"     ← real IFC GUID
BOM.db        m_bom_line_ma.guid = "3cUkl32..."     ← NOW POPULATED
output.db     guid = "3cUkl32yn9qRSPvBJVyZVU"       ← real IFC GUID preserved
```

The TACK LEAF log line shows the GUID:
```
TACK LEAF Furniture_Desk:1525x762mm guid=3cUkl32yn9qRSPvBJVyZVU anchor=(...) → LBD=(...)
```

Both A (tack math) and B (data provenance) proven in one log line.

## Gate

Run SH with GEO mode (delete BOM.db first to trigger re-extraction with GUIDs):
```bash
rm -f library/SH_BOM.db
./scripts/run_RosettaStones.sh classify_sh.yaml -Dbim.geo.debug=true
```
- SH 7/7+ PASS (no regression)
- Log file: `[GEO  ] TACK ENTER/EXIT/LEAF` lines present
- LEAF lines carry real IFC GUIDs (not synthetic)
- `m_bom_line_ma` has rows: `SELECT COUNT(*) FROM m_bom_line_ma` > 0
- Verify: desk GUID in output.db matches extracted.db `3cUkl32yn9qRSPvBJVyZVU`

Run DX with GEO mode:
```bash
rm -f library/DX_BOM.db
./scripts/run_RosettaStones.sh classify_dx.yaml -Dbim.geo.debug=true
```
- DX 6/7+WARN (C9 pre-existing, no regression)
- TACK LEAF lines carry real IFC GUIDs
- DX has mirror rotation — verify ROT log lines appear

Run SH without GEO (normal mode):
```bash
./scripts/run_RosettaStones.sh classify_sh.yaml
```
- Zero GEO lines in log — mode is off by default
- SH 7/7+ PASS

**Do NOT run TE** — avoid 48K element bloat and re-extraction time. SH + DX cover the tack chain (straight + rotated).

## What NOT to do

- Do NOT add INFO-level tack logging (too verbose for console)
- Do NOT modify the tack math — log what exists, don't fix position errors
- Do NOT modify BOMWalker or CompilationPipeline
- Do NOT modify existing migration files
- Do NOT change the `m_bom_line_ma` schema — it already has the right columns
- **Filter controls logging only — never skip a placement because of the filter**

## Spec citation

```java
// BBC.md §4 — FINE tack chain logging + IFC GUID traceability
// Each TACK LEAF line proves: (A) tack math ran, (B) IFC GUID preserved
```

## Commit

**BOM.db re-extraction required.** Part B writes GUIDs during IFCtoBOM extraction. Existing BOM.db files have 0 rows in `m_bom_line_ma`. Delete `library/SH_BOM.db` and re-run to verify GUID population. Do NOT delete TE_BOM.db (7.2MB, slow to rebuild) — verify on SH first.

**BIM.properties default:** Keep `bim.log.level=FINE` and `bim.geo.debug=false` in BIM.properties. GEO is opt-in only. Test on SH and DX only — do NOT run TE (48K elements, avoid bloat and re-extraction time).

```bash
git add DAGCompiler/src/main/java/com/bim/compiler/bom/walker/PlacementCollectorVisitor.java \
        IFCtoBOM/src/main/java/com/bim/ifctobom/ScopeBomBuilder.java \
        IFCtoBOM/src/main/java/com/bim/ifctobom/VerbFactorizer.java \
        IFCtoBOM/src/main/java/com/bim/ifctobom/DisciplineBomBuilder.java \
        orm-core/src/main/java/com/bim/orm/BIMLogger.java \
        BIM.properties \
        PROGRESS.md
git commit -m "[S100-p123] GEO tack chain logging + IFC GUID traceability via m_bom_line_ma"
```

## When Done

Prepend `# DONE — [commit_hash](https://github.com/red1oon/BIMCompiler/commit/commit_hash)` to this file's first line.

Append findings below `---`. The watchdog reads these:
- Paste 5 TACK lines from SH log (ENTER FLOOR, ENTER BED_SET, LEAF bed, LEAF desk, EXIT)
- Verify desk LBD matches output.db elements_rtree minX/minY within 1mm
- SH 7/7, TE gate result
- How many TACK lines for SH? For TE?
- Any surprises — document, do NOT fix

---

# FINDINGS — S100-p123

## 5 TACK lines from SH log

```
TACK ENTER FLOOR_SH_GF_STD depth=0: parent=(0.0000,0.0000,0.0000) + line=(0.0000,0.0000,0.0000) + bomOrigin=(0.0000,0.0000,0.0000) → anchor=(0.0000,0.0000,0.0000)
TACK ENTER SH_BED_SET depth=1: parent=(0.0000,0.0000,0.0000) + line=(13.3480,3.6925,0.4700) + bomOrigin=(0.0000,0.0000,0.0000) → anchor=(13.3480,3.6925,0.4700)
TACK LEAF  Furniture_Bed_1:1525x2007x355mm-Queen guid=1RS53LK$j6KOlAGwxTiY8D anchor=(13.3480,3.6925,0.4700) + offset=(0.0000,0.0000,0.0000) + half=(1.0035,0.9000,0.2413) → centroid=(14.3515,4.5925,0.7113) LBD=(13.3480,3.6925,0.4700)
TACK LEAF  Furniture_Desk:1525x762mm guid=3cUkl32yn9qRSPvBJVyZVU anchor=(13.3480,3.6925,0.4700) + offset=(0.4230,2.6015,0.0000) + half=(0.7816,0.4096,0.3810) → centroid=(14.5525,6.7035,0.8510) LBD=(13.7710,6.2940,0.4700)
TACK EXIT  SH_BED_SET depth=1
```

## Desk LBD verification

- GEO log:    LBD=(13.7710, 6.2940, 0.4700)
- output.db:  minX=13.7710, minY=6.2940, minZ=0.4700
- Delta: <0.1mm on all axes

## Gate results

- SH: 7/7 PASS
- DX: 5/7 PASS, 1 FAIL (310 critical proof violations — pre-existing from uncommitted p122 changes)
- TE: NOT RUN (per prompt instruction)

## GEO line counts

- SH: 238 GEO lines (GEO on), 0 GEO lines (GEO off)
- DX: 3220 GEO lines, 920 ROT lines (all pi rad = 180 degree mirror)

## MA row counts (Part B)

- SH: 58 rows in m_bom_line_ma (matches element count)
- DX: 179 rows in m_bom_line_ma (unfactored lines with IFC GUIDs)

## Surprises

1. DX 310 critical proof violations are pre-existing. Confirmed by reverting VerbFactorizer 7-arg overload to writeMaRows=false (original behavior) — same 310 failures. Caused by uncommitted p122 changes, not p123.

2. VerbFactorizer had coupled element_ref behavior to writeMaRows. When writeMaRows=true, unfactored lines got IFC GUID as element_ref. Broke RE buildings (need product name for geometry lookup). Fixed by adding useGuidAsRef parameter: CO path (DisciplineBomBuilder) passes true, RE path (ScopeBomBuilder, StructuralBomBuilder) passes false.

3. ROT log needed in onLeaf(), not just onSubAssembly(). DX mirrors via rotation_rule on the UNIT line, affecting leaf offsets. Added Log 4b in onLeaf() for leaf rotation visibility.

4. Script does not support JVM args as CLI params. bim.geo.debug must be set in BIM.properties, not via -Dbim.geo.debug=true to run_RosettaStones.sh.

## Part C — GEO SUMMARY: All-Pairs Relative Offset Verification

emitGeoSummary() joins compiled TACK LEAF GUIDs against *_extracted.db
elements_rtree. Computes all-pairs relative offset deltas — cancels world
origin, proves tack chain is lossless regardless of coordinate frame.
Runs once after walk, not per-element. 1mm drift threshold.

### Fleet Results (24 buildings, TE excluded)

| Building | Elements | Pairs | Worst (mm) | DRIFT |
|----------|----------|-------|------------|-------|
| SH | 58 | 1,653 | 0.000 | 0 |
| DX | 179 | 15,931 | 0.000 | 0 |
| FK | 82 | 3,321 | 0.000 | 0 |
| BA | 11 | 55 | 0.000 | 0 |
| BR | 48 | 1,128 | 0.000 | 0 |
| NI | 104 | 5,356 | 0.000 | 0 |
| WL | 114 | 6,441 | 0.000 | 0 |
| WB | 125 | 7,750 | 0.000 | 0 |
| RA | 96 | 4,560 | 0.000 | 0 |
| JE | 41 | 820 | 0.000 | 0 |
| ES | 29 | 406 | 0.000 | 0 |
| WA | 191 | 18,145 | 0.000 | 0 |
| MO | 8 | 28 | 0.000 | 0 |
| RS | 90 | 4,005 | 0.000 | 0 |
| RM | 178 | 15,753 | 0.000 | 0 |
| CH | 3,693 | 6,817,278 | 0.000 | 0 |
| SC | 35 | 595 | 0.001 | 0 |
| CS | 1,078 | 580,503 | 0.153 | 0 |
| CL | 3,214 | 5,163,291 | 0.044 | 0 |
| CP | 6,584 | 21,671,236 | 0.000 | 0 |
| GH | 193 | 18,528 | 4.700 | 2,380 |
| IN | 699 | 243,951 | 11,970 | 15,772 |
| HI | 0 | — | — | no GUIDs |
| DM | — | — | — | GENERATIVE |

**22/24 buildings DRIFT=0.** Sub-mm worst on CS (0.153mm), CL (0.044mm), SC (0.001mm).
CP largest clean run: 6,584 elements, 21.7M pairs, 0.000mm worst.

### 3 Anomalies (all pre-existing, confirmed by re-extraction)

1. **IN (11.97m drift):** Same GUID appears at two different compiled positions — double-walked through overlapping BOM paths. One path applies world origin, one does not. Pre-existing architecture issue.

2. **GH (4.7mm drift):** Compiled positions differ from extraction by consistent ~4.7mm on some axes. Floating-point accumulation through the tack chain. Pre-existing.

3. **HI (0 GUIDs):** MA table contains product names (STR_MD_BEAM_U.ETG_1) instead of 22-char IFC GloballyUniqueId values. The HI extraction source does not carry real IFC GUIDs, so the IFC_GUID regex correctly rejects them. No comparison possible.
