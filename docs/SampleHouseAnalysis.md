# SampleHouse Analysis — SampleHouse Guardrails
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md)

<div class="bim-banner" markdown>
<b>58 elements, one storey — the hello-world proof.</b> Smallest Rosetta Stone validates the full pipeline end-to-end with ARC + STR + CW disciplines.
</div>

**Stone:** 1 of 3 (smallest — hello-world proof)
**Updated:** 2026-03-28 (S100-p85 fleet audit recompilation)

---

## Building Identity

| Property | Value |
|----------|-------|
| Name | SampleHouse |
| IFC version | IFC4 (single file) |
| Country | N/A (reference building) |
| Type | Single-storey residential |
| Elements | 58 |
| Disciplines | 3 (ARC: 35, STR: 20, CW: 3) |
| M_Product_Category | RE (Residential) |
| Prefix | SH |
| C_DocType_ID | RE_SH |
| Reference DB | `DAGCompiler/lib/input/SampleHouse_extracted.db` |
| BOM DB | `library/SH_BOM.db` |

## Discipline Breakdown (verified against component_library.db)

| Discipline | Count | Elements |
|------------|-------|----------|
| ARC | 35 | Walls, doors, windows, furniture, slabs, coverings |
| STR | 20 | Slabs, walls (structural) |
| CW | 3 | Cold water pipes |
| **Total** | **58** | |

## Key Settings

| Setting | Value | Source |
|---------|-------|--------|
| Compilation mode | EN-BLOC (singularity) | BBC.md §3.3 |
| BOM tree shape | Self-describing (BBC.md §1): BUILDING → FLOOR → ROOM → SET → LEAF for SH | RE pattern |
| Origin convention | BUILDING has world LBD; FLOOR/ROOM zeroed (R16) | BBC.md §4.1 |
| YAML | `classify_sh.yaml` | Single storey, 5 rooms |
| Mirror | None (single unit) | — |
| Scope spaces | `floor_rooms:` IFC space → BOM template mapping | classify_sh.yaml |

## Gate Status

| Gate | Status | Value |
|------|--------|-------|
| G1-COUNT | PASS | 58 |
| G2-VOLUME | PASS | +0.00% |
| G3-DIGEST | PASS | 496022db |
| G4-TAMPER | PASS | 0 violations / 20 rules |
| G5-PROVENANCE | PASS | 7 checks |
| G6-ISOLATION | PASS | — |

## Recompilation (S100-p85 Fleet Audit, 2026-03-28)

BOM walk recompilation via `CompileStage` → `writeFromBomWalk()`. **7/7 PASS.**

| Metric | Value |
|--------|-------|
| Elements | 58 (was 55 — 3 additional from CW discipline) |
| Root BOM | BUILDING_SH_STD, origin=(-9.235, -2.746, -0.470) |
| Verbs | 22 PLACE, 4 CLUSTER |
| Disciplines | ARC=24, STR=2, CW=2 |
| LOD binding | 58 LOD, 0 fallback, 0 missing |
| H6 WARNs | 24 (MEP schedule vs actual — no MEP elements yet) |

**LMP Drift Check:** 6 pass, 0 fail, 2 deferred

| § | Check | Verdict |
|---|-------|---------|
| §1 | Input=Output | PASS (58/58) |
| §2 | LOD400 | PASS (58/58 OK, 0 warn, 0 fail) |
| §3 | Compiler Only | PASS (T18/T19/T20 guard) |
| §6 | Output Path | PASS (single path) |
| §7 | Separate From Input | PASS (bom.db=library/_SH_compile.db) |
| §8 | Visual Fidelity | PASS (geometry OK) |
| §4 | Openings | deferred (no proof aggregate) |
| §9 | Orientation | deferred (no proof aggregate) |

## BOM Compilation Model (S147)

The Sample House is the simplest Rosetta Stone — 58 elements, no mirroring,
single storey + roof. It proves the BOM compilation concept in its purest form.

```
BUILDING_SH_STD (BUILDING)                     ← the order: "build this house"
 ├─ SH_GF_STR (FLOOR)       13 → 16 instances ← ground floor structure (walls, slabs)
 │                                               verbs: TILE (curtain wall mullions)
 ├─ SH_RO_STR (FLOOR)        2 → 2 instances  ← roof structure
 ├─ SH_UN_STR (FLOOR)        2 → 2 instances  ← unknown storey (2 beams)
 ├─ SH_ROOM_GF (FLOOR)       2 rooms          ← room index
 │   ├─ SH_1_LIVING_ROOM_SET (SET)  9 → 12    ← living room: sofa, table, chairs, piano
 │   │                                           verb: CLUSTER (chair group)
 │   └─ SH_2_BEDROOM_SET (SET)      2 → 2     ← bedroom: bed, wardrobe
 ├─ FLOOR_SLAB_GF (ASSEMBLY) 0                 ← ground slab (stub)
 ├─ ROOF_ASSEMBLY (ASSEMBLY)  0                 ← roof assembly (stub)
 ├─ SH_UN_ASM_1 (ASSEMBLY)  13 → 13           ← assembly: windows, doors
 └─ SH_UN_ASM_2 (ASSEMBLY)   7 → 13           ← assembly: coverings, verbs expand
```

**11 BOMs, 56 lines, 58 instances.** Almost 1:1 — minimal factorization because
SH has few repeated elements. The living room chair CLUSTER (3 chairs at explicit
offsets) is the only verb-expanded group.

**No mirroring, no rotation.** SH is a simple box — IDENTITY transform on every
element. This makes it the baseline: if SH breaks, the core BOM walk is broken.
If SH passes but DX fails, the issue is in the mirroring/rotation layer.

**SPATIAL-REPORT:** `outliers=4 sym=4 asym=0` — 4 IfcMember (rafters) with
stepped X offsets. These are real position differences from the LOD placement
vs IFC reference, not compilation errors.

**Logging proof:** `grep BOM-SUMMARY logs/*SampleHouse*ifctobom*.log` shows
this tree. `grep SPATIAL-REPORT logs/pipeline_SH*.log` for spatial accuracy.

## Guardrails

1. **Smallest stone — any regression shows here first.** Run SH before DX/TE.
2. **BOM walk path** — single compilation path for all buildings (S100-p72).
3. **TRIM verb active** — glass curtain wall panels trimmed to the pitched roof intersection.
   SH now uses 22 PLACE + 4 CLUSTER verbs. CLUSTER factorizes windows.
4. **ProveStage skipped** — no relational data (§4, §9 deferred).

## Known Issues

- **H6 WARNs (24):** MEP schedule expects products in rooms but no MEP elements compiled yet.
  These are advisory findings in LOG mode — will resolve when discipline recipes produce elements.
- **ProveStage 0ms:** Prover skipped for all non-relational buildings. Not SH-specific.

---

*SH is the hello-world proof. If SH breaks, nothing else can be trusted.
See `BOMBasedCompilation.md` §3.3 (EN-BLOC = HelloWorld).*

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
