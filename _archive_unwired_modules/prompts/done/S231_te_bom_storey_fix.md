# S231 — Terminal BOM Storey Fix
# ⚠ DO NOT REMOVE — Scope: Fix TE_BOM.db storey mapping so IFCtoBOM populates it. Read the log after every run.

## Context

S230b (2026-04-27) discovered TE_BOM.db is EMPTY (0 tables). The IFCtoBOM pipeline
fails QA reconciliation because YAML storey keys don't match extraction container names.

**Do NOT act until you have completed the full read-back below.**

## Read First (mandatory, in order)

1. `docs/LAST_MILE_PROBLEM.md` — the 11 drift points. Every check in this prompt traces to an LMP section
2. `PROGRESS.md` §Current State — gate table, TE row
3. `docs/TerminalAnalysis.md` §BOM Factorization — the S230b root cause block
4. `docs/TerminalAnalysis.md` §C_Order/C_OrderLine — the SJTII = Terminal identity
5. `docs/TerminalAnalysis.md` §L3 — element_ref is NOT unique in federated IFC
6. `docs/BOMBasedCompilation.md` §2.1.8 — IFCtoBOM: two inputs, separation of concerns
7. `docs/WorkOrderGuide.md` §YAML Fidelity Mantra — YAML = Order input, keys = C_OrderLine
8. `docs/WorkOrderGuide.md` §Invention Boundary table — what comes from IFC vs YAML
9. `prompts/done/127_storey_auto_discovery.md` — the auto-discover implementation (P127)
10. `IFCtoBOM/src/main/java/com/bim/ifctobom/ClassificationYaml.java` — how storeys are parsed, auto-discover path
11. `IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java:190-209` — container resolution: YAML override vs auto-discover
12. `IFCtoBOM/src/main/java/com/bim/ifctobom/ExtractionPopulator.java:240-260` — deriveRows, the Terminal Z-band resolver
13. `IFCtoBOM/src/main/resources/classify_te.yaml` — current YAML (original, reverted)
14. `logs/pipeline_Terminal_ifctobom_20260427_020010.log` — the S230b failure log (21 containers dropped)

After reading all 13 items, write a §Review section in this prompt summarising what you found.
Only then proceed to §Fix.

## What the S230b Investigation Found

### The single break: storey name mismatch

YAML `classify_te.yaml` maps 7 English storey names ("Foundation", "Ground Floor", "Level 1-4", "Roof").
The extraction DB (`Terminal_extracted.db`) has 23 mixed Malay/English container names from the
SJTII per-discipline IFC files:

```
elements_meta.storey distribution (from extraction):
  Unknown              33,848  (70% — no IfcBuildingStorey in IFC)
  Aras Tanah            4,166  (= Ground Floor in Malay)
  Aras 02               2,765
  Aras 01               2,299
  Aras 03               1,564
  GROUND FLOOR LEVEL    1,288  (English duplicate of Aras Tanah)
  03 SECOND FLOOR LEVEL   475
  Aras 04                 400
  04 THIRD FLOOR LEVEL    382
  02 FIRST FLOOR LEVEL    370
  Ceiling Level Kedai     209
  Ceiling Level 02        178
  Ceiling Level 03        152
  Ceiling Level 01        115
  Aras Kedai               69
  Aras Jalan               45
  Aras Bumbung             39  (= Roof in Malay)
  Ceiling Level 04         19
  05 FOURTH FLOOR LEVEL    15
  Ground Lev               12
  06 ROOF LEVEL            10
  07 BEAM LEVEL             4
  00 Aras Asas              4  (= Foundation in Malay)
```

Only "Level 4" (50 el) and "Roof" (33,798 el) matched YAML keys.
Pipeline log: `[FAIL] Extraction reconciliation — 33848 extraction LEAFs vs 48428 extracted (delta=-14580)`

### Product chain is intact

Products exist in component_library.db under SJTII-* building_type names (4,092 products
across 8 discipline IFC files). ExtractionPopulator resolves products via ProductResolver
alias cascade (element_ref → M_Product_ID), not by building_type column. The populate
step's "0 new" means all products were already cataloged from prior extraction — not missing.

### Z-band resolver already exists for "Unknown"

ExtractionPopulator.java:257-258 has a hardcoded check:
```java
if ("Terminal".equals(buildingType) && "Unknown".equals(storey)) {
    storey = resolveStoreyByZBand(e);
}
```
The 33,848 "Unknown" elements may already be resolved to named storeys BEFORE they reach
the YAML matching. **You must verify:** what storey names does `resolveStoreyByZBand()` produce?
If it maps to "Level 1", "Aras 01", or something else — that determines which keys the YAML needs.

### Auto-discover path exists (P127)

When YAML has no `storeys:` section, `SpatialContainerConfig.discover()` auto-generates
containers from the extraction. RL (rail, 9/9 ALL GREEN) uses this successfully.
But auto-discover loses product_category control (GF, L1, RF etc.) — it generates
generic abbreviations from container names.

## Spec Constraints (do not violate)

Per BBC.md §2.1.8:
- **IFC extraction** = WHAT exists, WHERE it is (authoritative)
- **YAML** = HOW to organise into BOM tree (C_Order + C_OrderLine)
- **YAML storeys keys** = C_OrderLine — defines tree shape. Keys must reference real extraction containers.
- **IFCtoBOM does NOT invent elements** — every leaf traces to I_Element_Extraction

Per WorkOrderGuide.md:
- YAML is the **only human-crafted artifact**
- "If you need to change the pipeline output, change the YAML. Never patch data manually."

Per CLAUDE.md:
- component_library.db is untouchable (feedback_pipeline.md)
- Library filled at extraction ONLY (feedback_library_separation.md)

## Review (S231, 2026-04-27)

### Z-band resolver output (ExtractionPopulator.java:356-365)

The Z-band resolver maps Unknown elements to 7 English names using Z-centroid bands:
`<0.0→Foundation, <4.5→Ground Floor, <8.0→Level 1, <11.5→Level 2, <15.0→Level 3, <18.0→Level 4, ≥18.0→Roof`

From the S230b log (`pipeline_Terminal_ifctobom_20260427_020010.log`):
- Only **Level 4** (50 el) and **Roof** (33,798 el) appeared as matched containers
- The other 5 Z-band names (Foundation through Level 3) got 0 Unknown elements
- 33,798 of the 33,848 Unknown elements are roof-level (Metal Deck plates)

### Why 22 containers were dropped

The extraction DB has 23 named containers from SJTII per-discipline IFC files (Malay/English).
The Z-band resolver only handles `storey == "Unknown"` — elements with IFC-assigned storey names
bypass Z-band and keep their original container names. YAML had 7 English names; only Level 4
and Roof matched. Result: 14,580 elements in 22 unmapped containers dropped.

### DisciplineBomBuilder collision risk

`floorBomId = prefix + "_" + code` (line 100). Multiple containers sharing the same code would:
1. Overwrite the FLOOR BOM header via `INSERT OR REPLACE` (last container's AABB wins)
2. Create duplicate MAKE lines from BUILDING → FLOOR (same role/seq)

**Solution:** Each container gets a unique code, role, and seq. product_category is shared
for canonical floor grouping. This creates 29 FLOOR BOMs instead of 7, but captures all 48,428 elements.

### Container → Floor mapping (from log evidence)

| Canonical Floor | Z-band key (el) | IFC containers (el) |
|----------------|------------------|---------------------|
| Foundation (FN) | Foundation (0) | 00 Aras Asas (4) |
| Ground Floor (GF) | Ground Floor (0) | Aras Tanah (4166), GROUND FLOOR LEVEL (1288), Ground Lev (12), Aras Jalan (45), Aras Kedai (69) |
| Level 1 (L1) | Level 1 (0) | Aras 01 (2299), 02 FIRST FLOOR LEVEL (370), Ceiling Level Kedai (209), Ceiling Level 01 (115) |
| Level 2 (L2) | Level 2 (0) | Aras 02 (2765), 03 SECOND FLOOR LEVEL (475), Ceiling Level 02 (178) |
| Level 3 (L3) | Level 3 (0) | Aras 03 (1564), 04 THIRD FLOOR LEVEL (382), Ceiling Level 03 (152) |
| Level 4 (L4) | Level 4 (50) | Aras 04 (400), 05 FOURTH FLOOR LEVEL (OBSERVATORY DECK) (15), Ceiling Level 04 (19) |
| Roof (RF) | Roof (33798) | Aras Bumbung (39), 06 ROOF LEVEL (10), 07 BEAM LEVEL (OBSERVATORY) (4) |

**Total: 33,848 (Z-band) + 14,580 (IFC) = 48,428 ✓**

## Fix

After completing the read-back and writing §Review:

1. **Verify Z-band output.** Add FINE logging to `resolveStoreyByZBand()` if not already present,
   run IFCtoBOM for TE, read the log. What storey names does it produce for the 33,848 Unknown elements?

2. **Update classify_te.yaml.** YAML storeys keys must match the container names that appear
   in `storeyElements` after ExtractionPopulator runs (i.e., after Z-band resolution).
   Each key = one extraction container. Values provide canonical codes + product categories.

3. **Run pipeline.** `./scripts/run_RosettaStones.sh classify_te.yaml` — save log, read log.
   QA reconciliation must PASS. Check TE_BOM.db has m_bom + m_bom_line tables with content.

4. **Update TerminalAnalysis.md** §BOM Factorization with actual measured numbers from TE_BOM.db
   (not the designed numbers from sessions 8-11 which may differ).

5. **Update PROGRESS.md** gate table for TE.

## LMP Drift Check (mandatory after every pipeline run)

After each run, check the 11 drift points from `LAST_MILE_PROBLEM.md`:

1. **Input = Output?** (LMP §1) — SUM(non-PHANTOM qty) = output count. Quote the G1-COUNT line.
2. **LOD400 Geometry?** (LMP §2) — zero GEO_ fallback hashes. Quote G5-PROVENANCE line.
3. **Compiler Only?** (LMP §3) — no extraction DB references in compile path. Quote G4-TAMPER line.
4. **Tack Convention?** (LMP §4) — all dx/dy/dz >= 0, within parent AABB. Quote W-TACK-1 line.
5. **Separate from Input?** (LMP §7) — output coordinates reconstruct from tack accumulation.

If any drift point fails, stop and diagnose before proceeding.

## Regression Testing (mandatory)

After TE passes, recompile SH and DX to confirm nothing broke:

```bash
./scripts/run_RosettaStones.sh classify_sh.yaml > logs/regression_sh.log 2>&1
./scripts/run_RosettaStones.sh classify_dx.yaml > logs/regression_dx.log 2>&1
```

Read both logs. SH must remain 8/9 (MetadataMissing is pre-existing). DX must remain 8/9.
Any gate that was PASS and is now FAIL = regression caused by this session. Fix before closing.

**All judgements must cite log lines.** No "it looks fine" — quote the `VERDICT:` and `[PASS]`/`[FAIL]`
lines from the log. Exit code alone is not evidence (CLAUDE.md Log Mandate).

## What NOT to Do

- Don't change ExtractionPopulator.java or any pipeline code — fix the YAML only
- Don't change component_library.db
- Don't change ERP.db
- Don't run without reading the log afterward
- Don't claim PASS/FAIL without quoting the log line that proves it
- Don't assume the sessions 8-11 factorization numbers (58 BOMs, 1,131 lines) will match —
  the extraction and pipeline have changed since then. Measure fresh.
