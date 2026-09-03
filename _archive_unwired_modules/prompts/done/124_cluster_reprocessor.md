# DONE — [d518e326](https://github.com/red1oon/BIMCompiler/commit/d518e326)

**Spec:** DISC_VALIDATION_DB_SRS §10.4.10, SPATIAL_COMPILATION_PAPER §4.3.1
**Prereq:** P123 DONE (GEO verification reveals CLUSTER dominance)

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Diagnose first. Convert only what clearly fits.

## Read first

1. `PROGRESS.md` §Current State
2. `docs/SPATIAL_COMPILATION_PAPER.md` §4.3.1 — CLUSTER honesty note
3. `IFCtoBOM/src/main/java/com/bim/ifctobom/VerbDetector.java`
   - lines 160-163: TILE/FRAME tried first, CLUSTER is fallback
   - lines 415-448: `detectCluster()` — the catch-all that stores raw offsets
   - lines 75-120: `detectTile()` — grid pattern detection
4. `library/CP_BOM.db` — query: `SELECT child_product_id, qty, substr(verb_ref,1,8) FROM m_bom_line WHERE qty > 5 ORDER BY qty DESC`
   - 922 elbows as CLUSTER (should be ROUTE — they follow pipe paths)
   - 590 cold water pipes as CLUSTER (should be ROUTE)

## Problem

CLUSTER is the catch-all fallback for elements VerbDetector couldn't
classify as TILE/FRAME/ROUTE. It stores exact extraction positions as a
semicolon-separated string. The compiler replays these verbatim — zero
drift is guaranteed but trivial (copy-paste, not computation).

Fleet GEO verification shows CP is 99.5% CLUSTER, TE is 97.4% CLUSTER.
The zero-drift results primarily prove lossless storage, not spatial
compilation. Converting CLUSTERs to formula verbs strengthens the
compilation claim.

## Fix — Two Phases: Diagnose First, Convert Second

### Phase 1: Diagnostic logging (this prompt)

Add FINE logging to VerbDetector that reports WHY each group became
CLUSTER — which pattern detection failed and why:

```java
BIMLogger.fine("VERB", "CLUSTER fallback: {} qty={} — TILE failed ({}), FRAME failed ({}), ROUTE failed ({})",
    productId, qty, tileReason, frameReason, routeReason);
```

Where the reasons are concrete: "TILE: X spacing irregular (std=0.65, mean=0.67, CV=0.97)", "FRAME: only 1 axis aligned", "ROUTE: 2D spread (not linear)".

This goes into the extraction log (IFCtoBOM), not the compilation log.

### Phase 2: Pattern analysis from diagnostic data

After Phase 1 runs on the fleet, examine the diagnostic log to identify
which CLUSTER categories have convertible patterns:

**Pre-diagnosis from SH analysis (this session):**

| Product | Qty | Why CLUSTER | Convertible? |
|---------|-----|-------------|-------------|
| Dining chairs | 6 | 50% grid occupancy (6/12 cells), irregular X spacing | No — table arrangement, domain-specific |
| Windows | 4 | 3 on wall Y=5.81, 1 on wall Y=0.00, irregular | No — multi-wall placement |
| Curtain wall | 20 | Mixed sizes (mullions 30mm + panels 1.6m) | No — hierarchical assembly grammar |
| CP elbows | 922 | At pipe bends (direction changes = not linear) | Maybe — ROUTE between bends, elbow at junction |
| CP pipes | 590 | Along pipe runs | **Yes** — straight segments between elbows are linear |

**Key insight:** most CLUSTERs are CLUSTER for good reason. The easy wins
are straight pipe/duct segments between fittings (linear runs → ROUTE).
Furniture, windows, and curtain walls are genuinely irregular.

### Implementation

In `VerbDetector.java`, at the point where `detectCluster()` is called
(~line 163), add the diagnostic log BEFORE returning the CLUSTER result:

```java
ClusterResult cluster = detectCluster(elements, floorMinX, floorMinY, floorMinZ);
if (cluster != null) {
    // Diagnostic: why did TILE/FRAME/ROUTE not match?
    String tileReason = diagnoseTileFailure(elements);
    String frameReason = diagnoseFrameFailure(elements);
    String routeReason = diagnoseRouteFailure(elements);
    BIMLogger.fine("VERB", "CLUSTER fallback: {} qty={} tile=[{}] frame=[{}] route=[{}]",
        productId, elements.size(), tileReason, frameReason, routeReason);
}
```

The `diagnose*Failure()` methods inspect the same data that `detect*()`
inspected but return a reason string instead of a result. ~10 lines each.

## Gate

Run SH:
```bash
rm library/SH_BOM.db
./scripts/run_RosettaStones.sh classify_sh.yaml
```
- SH 7/7 PASS (no regression)
- IFCtoBOM log contains "CLUSTER fallback" lines with reasons
- Verify: dining chairs reason = occupancy/spacing, windows = multi-wall

Run CP:
```bash
rm library/CP_BOM.db
./scripts/run_RosettaStones.sh classify_cp.yaml
```
- CP gate: no regression
- IFCtoBOM log: examine 922-elbow and 590-pipe CLUSTER reasons
- Report the top 5 reasons by element count

## What NOT to do

- Do NOT modify PlacementCollectorVisitor or the BOM walker
- Do NOT modify existing TILE/FRAME/ROUTE detection (first pass untouched)
- Do NOT modify existing migration files
- Do NOT lower detection thresholds on first-pass verbs
- Do NOT force elements into patterns they don't fit
- **If in doubt, leave as CLUSTER — false ROUTE is worse than honest CLUSTER**

## Spec citation

```java
// Implementing DISC_VALIDATION_DB_SRS §10.4.10 — ClusterReprocessor
// SPATIAL_COMPILATION_PAPER §4.3.1 — converting transcripts to recipes
```

## Commit

```bash
git add IFCtoBOM/src/main/java/com/bim/ifctobom/ClusterReprocessor.java \
        IFCtoBOM/src/main/java/com/bim/ifctobom/VerbDetector.java \
        PROGRESS.md
git commit -m "[S100-p124] ClusterReprocessor: second-pass pattern detection (ROUTE/TILE/MIRROR)"
```

## When Done

Prepend `# DONE — [commit_hash](https://github.com/red1oon/BIMCompiler/commit/commit_hash)` to this file's first line.

Append findings below `---`. The watchdog reads these:
- CP CLUSTER % before/after
- Which products converted? To which verb?
- How many elements moved from CLUSTER to ROUTE/TILE/MIRROR?
- SH/CP gate results
- Any surprises — document, do NOT fix

---
