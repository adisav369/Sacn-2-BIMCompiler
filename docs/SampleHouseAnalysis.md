# SampleHouse Analysis — Ifc4_SampleHouse Guardrails
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md)

<div class="bim-banner" markdown>
<b>55 elements, one storey — the hello-world proof.</b> Smallest Rosetta Stone validates the full pipeline end-to-end with ARC + STR disciplines.
</div>

**Stone:** 1 of 3 (smallest — hello-world proof)
**Updated:** 2026-03-28 (S100-p85 fleet audit recompilation)

---

## Building Identity

| Property | Value |
|----------|-------|
| Name | Ifc4_SampleHouse |
| IFC version | IFC4 (single file) |
| Country | N/A (reference building) |
| Type | Single-storey residential |
| Elements | 55 |
| Disciplines | 2 (ARC: 35, STR: 20) |
| M_Product_Category | RE (Residential) |
| Prefix | SH |
| C_DocType_ID | RE_SH |
| Reference DB | `DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db` |
| BOM DB | `library/SH_BOM.db` |

## Discipline Breakdown (verified against component_library.db)

| Discipline | Count | Elements |
|------------|-------|----------|
| ARC | 35 | Walls, doors, windows, furniture, slabs, coverings |
| STR | 20 | Slabs, walls (structural) |
| **Total** | **55** | |

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
| G1-COUNT | PASS | 55 |
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
