# SampleHouse Analysis — Ifc4_SampleHouse Guardrails

**Stone:** 1 of 3 (smallest — hello-world proof)
**Updated:** 2026-03-19 (session 24)

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
| DocSubType | SH |
| DocBaseType | RE (Residential) |
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
| BOM hierarchy | BUILDING → FLOOR → ROOM → SET → ITEM | RE pattern |
| Origin convention | BUILDING has world LBD; FLOOR/ROOM zeroed (R16) | BBC.md §4.1 |
| YAML | `classify_sh.yaml` | Single storey, 5 rooms |
| Mirror | None (single unit) | — |
| Scope boxes | `floor_rooms:` section with room origins | classify_sh.yaml |

## Gate Status

| Gate | Status | Value |
|------|--------|-------|
| G1-COUNT | PASS | 55 |
| G2-VOLUME | PASS | +0.00% |
| G3-DIGEST | PASS | 496022db |
| G4-TAMPER | PASS | 0 violations / 20 rules |
| G5-PROVENANCE | PASS | 7 checks |
| G6-ISOLATION | PASS | — |

## Guardrails

1. **Smallest stone — any regression shows here first.** Run SH before DX/TE.
2. **EN-BLOC only** — no WALK-THRU yet (W-WALKTHRU-DIFFERS-1 PENDING).
3. **TACK-FIX applied but untested** — ScopeBomBuilder uses minX() (session 21).
   Re-run after SRS expedition to verify W-TACK-1 PASS.
4. **Scope space origins** — room assignments depend on `origin_m` in YAML.
   Currently scope spaces populated; verify after any YAML change.
5. **No verb factorization** — 55 elements are flat (qty=1 each). No TILE/ROUTE.
   SH proves the base pipeline, not verb compression.

## Known Issues

- **Scope space data completeness:** Room scope boxes have origins but furniture
  SET assignment needs verification after TACK-FIX re-test.
- **W-TACK-1:** Advisory (WARN). Will be promoted to FAIL after TACK-FIX testing.

---

*SH is the hello-world proof. If SH breaks, nothing else can be trusted.
See `BOMBasedCompilation.md` §3.3 (EN-BLOC = HelloWorld).*
