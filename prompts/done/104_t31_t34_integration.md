# DONE
# T3.1 + T3.4 — Multi-Discipline TE + RE Subset

**Spec:** DISC_VALIDATION_DB_SRS §10.4.11 T3.1 + T3.4, BBC §3.6
**Prereq:** T2.1–T2.4 DONE (all 6 MEP disciplines route with CrawlRouter).

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Integration test — no new routing logic. All disciplines already work individually. This prompt verifies they work together.

## Read first

1. `PROGRESS.md` §Current State
2. `docs/BOMBasedCompilation.md` §3.6.1 (8 OrderLines), §3.6.3 (all traces)
3. Previous prompt findings — all RouteBuilder implementations
4. `docs/TerminalAnalysis.md` §Element Inventory — expected counts per discipline

## T3.1 — Multi-discipline TE (all 8 OrderLines)

Run full compilation on TE with all 8 discipline OrderLines active:

```bash
./scripts/run_RosettaStones.sh classify_te.yaml
```

Verify discipline distribution matches extraction (BBC §3.6.3):

| Discipline | AD_Org | Expected elements | Source |
|-----------|--------|------------------|--------|
| ARC | 1 | ~34,724 | Extraction (spatial walk) |
| STR | 2 | ~1,429 | Extraction (spatial walk) |
| FP | 3 | ~6,863 | CrawlRouter (riser+header+grid) |
| ACMV | 4 | ~1,621 | CrawlRouter (duct+terminal) |
| ELEC | 5 | ~1,172 | CrawlRouter (tray+fixtures) |
| CW | 6 | ~1,431 | CrawlRouter (pipe+fixtures) |
| SP | 7 | ~979 | CrawlRouter (waste+stack) |
| LPG | 8 | ~209 | CrawlRouter (pipe+valves) |

Total must ≈ 48,428.

Check:
- No discipline overlaps (each element belongs to exactly one AD_Org)
- P17 SystemConnectedProof fires for each MEP discipline (6 connected graphs)
- P15 PipeInHostProof: all MEP elements within their host rooms
- P16 WasteGradientProof: SP waste slopes downward
- elements_meta matches c_orderline (P95 divergence should now be resolved)

## T3.4 — RE subset: SH with ARC + ELEC + SP

SH is RE (residential). Test with 3 disciplines only:

1. ARC (shell — existing, 7/7)
2. ELEC (light fixtures + outlets)
3. SP (bathroom drainage)

Verify:
- Callout creates 2 discipline OrderLines (ELEC + SP), not all 6
- CrawlRouter routes both disciplines
- SH 7/7 (no ARC/STR regression)
- 3 OrderLines total (ARC + ELEC + SP)

## Gate

- TE: all 8 disciplines in output, counts within 10% of extraction
- TE 6/7+WARN (C9 pre-existing)
- SH 7/7 with 3 discipline OrderLines
- BIMEyes P15/P16/P17 all fire (not SKIP) on TE

## What NOT to do

- Do NOT modify any RouteBuilder or CrawlRouter — this is verification only
- Do NOT modify existing migration files
- Do NOT fix C9 axis warnings — document them

## When Done

Prepend `# DONE`. Append findings: TE discipline distribution table (actual vs expected), P15/P16/P17 results, SH 3-discipline verification, elements_meta coherence check.

---

## Findings (S100-p104, 2026-03-29)

**Commit:** [a18ac379](https://github.com/red1oon/BIMCompiler/commit/a18ac379)

### T3.1 — TE Multi-Discipline Results

**VERDICT: PARTIAL PASS — c_orderline distribution EXACT, elements_meta divergence, P15/P16/P17 NOT firing.**

TE compiled 48,428 elements. 6/7 PASS, 1 WARN (C9: 60 axis mismatches, pre-existing).

**c_orderline discipline distribution (LEAF Qty sums):**

| Discipline | AD_Org | Expected | Actual | Delta | Status |
|-----------|--------|----------|--------|-------|--------|
| ARC | 1 | ~34,724 | 34,724 | 0 | EXACT |
| STR | 2 | ~1,429 | 1,429 | 0 | EXACT |
| FP | 3 | ~6,863 | 6,866 | +3 | PASS (<1%) |
| ACMV | 4 | ~1,621 | 1,621 | 0 | EXACT |
| ELEC | 5 | ~1,172 | 1,172 | 0 | EXACT |
| CW | 6 | ~1,431 | 1,431 | 0 | EXACT |
| SP | 7 | ~979 | 979 | 0 | EXACT |
| LPG | 8 | ~209 | 209 | 0 | EXACT |
| **Total** | | **48,428** | **48,431** | **+3** | P-QTY WARN |

Callout inserted 6 DISCIPLINE OrderLines (FP/ELEC/ACMV/CW/SP/LPG). ARC+STR from extraction. All 8 disciplines present in output. No discipline overlaps — each LEAF OrderLine belongs to exactly one AD_Org.

**P-QTY delta (+3):** c_orderline total 48,431 vs elements_meta 48,428. The 3 extra are FP parasitic qty lines (BOM recipe rounding). Non-blocking advisory WARN.

### elements_meta Coherence Check

**DIVERGENT.** elements_meta uses IFC federation labels, not c_orderline AD_Org discipline:

| elements_meta label | Count | Includes |
|-------------------|-------|----------|
| ARC | 4,378 | PipeSegment(2321), LightFixture(569), Proxy(486), + misc |
| STR | 35,394 | IfcPlate(33,324), Slab(705), Member(442), Beam(432), Wall(333), Column(158) |
| FP | 995 | FireSuppression(909), Alarm(80), Controller(6) |
| ELEC | 264 | LightFixture(245), Appliance(19) |
| ACMV | 220 | AirTerminal(220) |
| MEP | 7,177 | PipeFitting(4241), PipeSegment(1500), DuctFitting(691), DuctSegment(369), FlowTerminal(244), Valve(111), FlowController(21) |

**Root cause:** elements_meta.Discipline comes from IFC federation source model, not from the compilation pipeline. IfcPlate(33,324) is tagged STR (it was in the structural model file) but belongs to ARC per TerminalAnalysis.md. CW/SP/LPG are lumped into generic "MEP" because the IFC federation didn't separate those piping sub-disciplines.

**Action needed:** elements_meta.Discipline should be overwritten by c_orderline.Discipline during WriteStage to achieve coherence. This is a WriteStage enhancement, not a routing issue.

### P15/P16/P17 BIMEyes Proofs

**NOT FIRING.** Two blockers:

1. **hasRelationalData() = false** for TE — no `ad_room_boundary` rows in BOM DB (institutional building, not residential). EYES proofs are gated behind this check (CompilationPipeline.java:1247).
2. **No CONNECTS_TO edges** — W_Verb_Node(0), system_edges(0), system_nodes(0). RouteDocEvent.fireAll() is not wired into CompilationPipeline. The RouteBuilders work in unit tests (DisciplineRouteBuilderTest 15/15 PASS) but are not called during actual compilation.

**Action needed:**
- Wire RouteDocEvent.fireAll() into CompilationPipeline after callout, before WriteStage
- Populate system_edges/system_nodes from CrawlRouter results
- Either relax hasRelationalData() gate for CO buildings with DISCIPLINE OrderLines, or populate ad_room_boundary from extraction zones

### T3.4 — SH RE Subset

**GATE NOT MET.** SH compiled 58 elements, 7/7 PASS (zero regression). But:

- Callout returned 0 — **RE buildings are explicitly skipped** (OrderLineProductCallout.java:43)
- SH has ARC(27 LEAF), STR(2 LEAF), CW(2 LEAF) — CW here is "Curtain Wall" storey, NOT "Cold Water" discipline
- No ELEC or SP discipline OrderLines exist
- No DISCIPLINE host_type rows in c_orderline

**Root cause:** RE discipline routing is not implemented. The callout only handles CO/IN. For RE buildings, disciplines would need to come from:
1. A `disciplines:` section in classify_sh.yaml (like classify_te.yaml has), OR
2. A separate RE-specific callout that reads YAML config instead of ERP.db shared BOMs

**SH 7/7 confirmed** — no ARC/STR regression from P101 baseline.

### Summary

| Gate Criterion | Status | Notes |
|---------------|--------|-------|
| TE: all 8 disciplines in output | **PASS** | c_orderline has all 8 with correct AD_Org_ID |
| TE: counts within 10% of extraction | **PASS** | EXACT match (7 of 8), +3 on FP (<0.05%) |
| TE: 6/7+WARN (C9 pre-existing) | **PASS** | 60 axis mismatches, same as baseline |
| SH: 7/7 with 3 discipline OrderLines | **FAIL** | 7/7 PASS but 0 discipline OrderLines (RE callout not implemented) |
| BIMEyes P15/P16/P17 fire on TE | **FAIL** | Skipped — no CONNECTS_TO edges, hasRelationalData=false |

### Blockers for Full T3 Completion

1. **RouteDocEvent pipeline wiring** — RouteBuilders work in isolation but are not called during compilation. Needs a CompilationPipeline stage between callout and WriteStage.
2. **elements_meta discipline coherence** — WriteStage must propagate c_orderline.Discipline to elements_meta.
3. **RE discipline support** — Callout skips RE. Need YAML-driven or separate mechanism for residential MEP.
4. **EYES proof gate** — hasRelationalData() blocks proofs on CO/IN buildings without ad_room_boundary. Need alternative gate for buildings with DISCIPLINE OrderLines.
