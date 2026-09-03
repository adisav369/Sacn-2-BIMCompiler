# DONE — [c78c743b](https://github.com/red1oon/BIMCompiler/commit/c78c743b)
# P06 Structural Joint Tolerance — GEO White Box Replaces Black Box Heuristic

**Spec:** EYES_SRS.md §4, LMP §7 (GEO smoking gun), TestArchitecture.md §Traceability
**Prereq:** P123 DONE (GEO tack chain logging), P129 DONE (IFC assembly BOMs)

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** The tack chain already has the evidence. Read it. No invention.

## Read first

1. `PROGRESS.md` §Current State
2. `DAGCompiler/src/main/java/com/bim/compiler/validation/PlacementProver.java` — P06 implementation
3. `DAGCompiler/src/main/java/com/bim/compiler/dsl/PlacementCollectorVisitor.java` — GEO TACK logging
4. `docs/LAST_MILE_PROBLEM.md` §7 — GEO as smoking gun
5. `docs/EYES_SRS.md` §4 — proof tiers
6. GEO log from DX compilation: grep `[GEO]` lines for overlapping slabs

## Design Principle: White Box > Black Box

**Black box (current P06):** Looks at compiled output AABBs. Sees overlap, flags
violation. No idea WHY. Post-hoc guessing — same as traditional testing.

**White box (GEO tack chain):** The compiler already logs every transform as it
happens: ENTER parent → TACK offset → LEAF placed → EXIT. Two overlapping slabs
have distinct tack chains (different BOM parent, different offset). The evidence
of intentional placement is IN the compilation trace, not derivable from output.

P06 should consume GEO evidence as its primary authority. The tack chain is the
proof — dimension heuristics are just a fallback.

## Problem

P06_NO_SAME_CLASS_OVERLAP flags 83 critical violations on DX (all structural slab
joints), 15 on FK, 107 on IN. These are valid construction — L-shaped floors modeled
as overlapping rectangular slabs. The IFC intended this. The compiler faithfully
reproduced it. P06 then calls it a bug.

## Fix

### Phase A: Expose tack chain identity to PlacementProver

The GEO tack chain (BUILDING→FLOOR→LEAF path) is currently log-only. Make it
available as proof data:

1. `PlacementCollectorVisitor` already tracks the walk path. Add a `tackChain`
   field to the placement record: the BOM-line path that produced this element
   (e.g. `BUILDING_DX_STD/DX_L1_STR/SLAB_MD_LEVEL_1_142`).

2. The tack chain is the element's **provenance** — how it got to its world
   position. Two elements with identical tack chains = BOM duplication bug.
   Different tack chains = distinct placements that happen to overlap.

### Phase B: P06 uses tack chain as primary evidence

In the P06 overlap check, after detecting AABB overlap between two same-class
elements:

- **Same tack chain** → CRITICAL (real BOM duplication — identical walk path
  produced two elements at the same position)
- **Different tack chains** → check if positions are identical (within 1mm):
  - Identical position + identical dimensions → CRITICAL (copy-paste duplication
    from different BOM paths)
  - Otherwise → ADVISORY (structural joint, log overlap volume)

The tack chain is the primary classifier. Position/dimension comparison is the
fallback for elements from different BOM paths that still look duplicated.

### Phase C: Do NOT change the test threshold

`BuildingRegistryTest:117` stays at threshold 0 for EXTRACTED buildings.
The fix is in proof classification (CRITICAL vs ADVISORY), not threshold relaxation.

## BIM.properties

```properties
bim.log.level=INFO
bim.geo.debug=true
```

## Gate

- DX: critical violations 83→0 (structural joints reclassified as ADVISORY)
- FK: critical violations 15→0
- IN: critical violations →0
- SH: 7/7 PASS (zero regression)
- Real duplicates found: expect 0 (no identical tack chains at same position)
- GEO log confirms: every overlapping pair has distinct tack chains

## What NOT to do

- Do NOT add per-building thresholds or custom treatment
- Do NOT modify BomValidator or IFCtoBOM
- Do NOT weaken duplicate detection (identical tack chain = always CRITICAL)
- Do NOT make GEO logging required for proofs (tack chain data in placement record, not log parsing)
- **All logging via BIMLogger — no System.out.println**

## Spec citation

```java
// Implementing LMP §7 + EYES_SRS §4 — GEO white-box proof for P06
// Tack chain provenance distinguishes duplicates from construction joints
```

## Commit

```bash
git add DAGCompiler/src/main/java/com/bim/compiler/validation/PlacementProver.java \
        DAGCompiler/src/main/java/com/bim/compiler/dsl/PlacementCollectorVisitor.java \
        PROGRESS.md
git commit -m "[S100-p130] P06 GEO white-box proof: tack chain distinguishes duplicates from joints"
```

## When Done

Prepend `# DONE — [commit_hash](https://github.com/red1oon/BIMCompiler/commit/commit_hash)` to this file's first line.

Append findings below `---`. The watchdog reads these:
- DX/FK/IN: critical violations before/after
- How many real duplicates found (expect 0)?
- How many structural joints reclassified to ADVISORY?
- Tack chain examples: two overlapping slabs with distinct chains
- GEO evidence: does the log confirm distinct provenance?
- Any surprises — document, do NOT fix

---
