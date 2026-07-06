# S161 — Walker Scale Test: AC Institute (IN)

**Prior work:** S160 closed. Generative MEP walker proven on DX (329 el) and SH (82 el).
Gate: SH 9/9, DX 9/9. BPartnerCatalogTest 4/4 PASS.

You are a coder for bim-compiler. One bounded task.

## Goal

Run the generative MEP walker on AC Institute (IN) — 699 extracted elements,
pure ARC/STR, **no MEP in the IFC**. This is the first real scale test of the
generative pipeline on an institutional building with non-residential room types.

## Why IN

- 699 elements across multiple storeys — larger than DX (329) and SH (82)
- No MEP extracted → walker generates 100% of MEP from scratch
- Institutional rooms (labs, offices, corridors, toilets) → `inferRoleFromContent()`
  may misclassify or produce null space_type → zero devices per room
- Multi-storey → slab detection per storey must work across more floors than DX
- `geometry_fail_threshold` not yet set for IN

## Step 1 — Run the pipeline, read the logs

```bash
./scripts/run_RosettaStones.sh classify_in.yaml 2>&1 | tee logs/s161_in_run.txt
```

Then grep the GENERATIVE channels:
```bash
grep "GENERATIVE" logs/pipeline_IN_*.log | grep -E "ROOM|BREACH|SUMMARY|PLACE"
```

## Step 2 — Triage one finding at a time (per feedback_narrow_triage.md)

Expected failure modes (in priority order):

1. **Room classification** — `inferRoleFromContent()` returns null for lab/office/corridor.
   Fix: extend space_type inference for institutional content words.

2. **Anchor Z miss** — slab detection fails on upper storeys of IN.
   Fix: same ScopeBomBuilder slab-top approach used in S159.

3. **geometry_fail_threshold** — IN has no threshold set, pipeline may abort.
   Fix: add IN entry to BuildingRegistry after first run reveals actual fail count.

4. **BREACH count** — generative devices outside room AABB.
   Fix: per breach log, adjust placement rule or room AABB computation.

## Gate

- IN 9/9 PASS (no regression on existing gates)
- Generative elements present in IN output (G1-COUNT shows IN el > 699)
- Zero GENERATIVE BREACH in logs
- SH 9/9, DX 9/9 (no regression)

## Witness

W-IN-GENERATIVE: IN output contains generative MEP elements (guid LIKE '%_MD_%')
with correct Z (bottom at storey floor, within 50mm).
