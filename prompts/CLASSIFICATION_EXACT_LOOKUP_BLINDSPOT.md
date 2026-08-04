# ⚠ DO NOT REMOVE — LANE: exact-lookup consumers bypass matchRule's tier 1+2 entirely
# Opened 2026-08-05, spun out of a Find Panel blast-radius check on §CLASS_UNMATCHED_INHERITED
# (bim-ootb PR #1191). NOT a §CLASS_UNMATCHED_FALLBACK/§CLASS_UNMATCHED_INHERITED regression — those
# only ever touched the 3 matchRule() copies. This is a SEPARATE, pre-existing population of
# consumers that never went through matchRule at all, in any prior fix (#1186/#1187/#1191).
# PRIME RULE: EXTRACT ONLY. Every line/consumer below is grepped and read directly, not inferred.

## §WHAT'S ALREADY COVERED — don't re-litigate
- Find Panel's Phase axis (`viewer/navigate_find.js`) — reads `kernel_ops`, populated by
  `time_machine.js`'s `injectGantt()`, which IS one of the 3 `matchRule` copies, wired for tier 1+2 by
  construction (`rates.js:555`'s eager `IFC_SCHEMA_HIERARCHY` preload exists specifically so the live
  viewer path gets it). Confirmed unaffected/safe.
- `boq_charts.html`'s three stale hardcoded `PHASE_ORDER` arrays (`generateSchedule`,
  `audit4DSchedule`, `buildScheduleFromOps`) — different bug (wrong phase ORDER, not missing
  CLASSIFICATION), already fixed and shipped: `4D_SCHEDULE_PERFECTION.md` §GANTT_EDIT/BOQ4D,
  `witness_boq_charts_real_schedule.js` 91/91, 6 buildings. Do not re-open.
- `schedule_diff.js:144`'s 3-arg `matchRule()` call — structurally moot, `cls` there is a free-text P6
  activity name, not a real IFC class; `hierarchy[cls]` could never match regardless of arg count.

## §THE GAP — 6 consumers, exact `SEQUENCE_RULES[cls]` dictionary lookup, zero substring/tier-2
None of these call `matchRule()`. They do `rules[cls]` / `SR[cls]` / `SEQUENCE_RULES[cls]` — an exact
key match. `SEQUENCE_RULES` has no `...Type`-suffixed keys at all (no `IfcDoorType`, `IfcSensorType`,
etc. as literal keys — only `matchRule`'s substring test bridges those to their base-name entry). So
every one of these silently lands on ITS OWN generic default the moment a `...Type`-suffixed class, or
any of the 58 real occurrence classes named in the parent lane as still-unclassified (`IfcPile`,
`IfcVehicle`, `IfcTendon`, etc.), shows up — **with no `§CLASS_UNMATCHED*` log line at all**, unlike
`matchRule`'s tier 3. These consumers don't even get the visibility win #1186 shipped, let alone #1191's
tier 2.

**Higher priority — live on real authored schedules, not fallback-only:**
- `viewer/boq_charts.html:1808,1813` — the resource/crew-count chart. Runs on `scheduleData` regardless
  of whether it came from a real authored schedule or the fallback generator (BOQ4D's redirect fixed
  the SCHEDULE data source, not this downstream per-class resource re-lookup for the chart). A real
  authored building with an unclassified class would show wrong crew/equipment counts here today.
- `viewer/proj_fold.js:150` — feeds the "BIM→Project" ERP push, triggered from *inside* the Find Panel
  itself (`navigate_find.js:1921-1937`, `_pushToErp`). User-facing, one hop from the panel already
  confirmed safe — worth closing so the whole Find-Panel-adjacent surface is consistent.
- `viewer/rates.js:247` `getPhase()` → consumed by `viewer/variation_order.js:133,154,176` (S222
  Variation Order Excel export) — a real financial/contractual document.
- `viewer/rates.js:267` `WORK_PACKAGES` → consumed by `viewer/export_5d.js:234-244` (5D BOQ "Work
  Packages" export sheet). **Structurally different from the other 5** — not even keyed off
  `SEQUENCE_RULES`, it's a 4th, fully separate hardcoded classification: 6 packages, each an explicit
  `classes: [...]` membership array matched by `.includes()`. Unmatched classes land in a synthesized
  "PACKAGE 6: OTHER" bucket (`export_5d.js:237-240`) — silent, no log, and this list has its own
  independent maintenance burden separate from `SEQUENCE_RULES`'s 57 keys.

**Lower priority — bounded to the intentionally-preserved fallback-only path:**
- `viewer/boq_charts.html:477` (`generateSchedule()`) and `:908` (`buildScheduleFromOps()`) — both
  confirmed by direct read to run ONLY "because no real schedule is authored for this building"
  (`:471`) / only when `resolve4D` has no authored schedule to prefer (BOQ4D §B4). Per BOQ4D's own
  explicit ruling ("Do NOT patch `generateSchedule()` into a second engine — it stays exactly as-is, as
  the no-authored-schedule FALLBACK only"), these are lower risk by design — worth fixing for
  consistency, not urgent the way the two "higher priority" items are.
- `viewer/schedule_read_4d.js:140,186` — fallback path only; the PRIMARY path (task-name parsing) already
  inherits `matchRule`'s real classification via the persisted task name string, so this only fires
  when a task name doesn't parse cleanly.

## §MEASURED EXPOSURE
Same 58 real occurrence classes named in `BUILDINGSMART_IFC_SCHEMA_CLASSIFICATION.md`'s parent lane
(`IfcPile`, `IfcVehicle`, `IfcTendon`, `IfcFastener`, `IfcKerb`, `IfcRail`, etc.) plus any
`...Type`-suffixed class — none of these are caught by any of the 6 consumers above even after #1191,
because none of them call `matchRule()` at all.

## §PROPOSED FIX (spec only — not yet implemented, not yet approved)
Export a single `classify(cls, hierarchy)` helper from `schedule_author.js` (wrapping the existing
`matchRule` tier 1→2→3 logic, already proven and witnessed) and route all 5 `SEQUENCE_RULES`-based
consumers through it instead of a raw dictionary lookup — same "N independent copies" discipline this
whole lane already runs on, don't grow a 4th/5th/6th copy of the matching logic itself.

`WORK_PACKAGES` is the one structurally different case: decide whether to retire its own hardcoded
`classes: [...]` lists in favor of DERIVING packages from `SEQUENCE_RULES`' own `phase` field (the same
derivation `proj_fold.js:140`'s `phaseSeq` already does, and the same one `4D_SCHEDULE_PERFECTION.md`
§B0 used to fix `boq_charts.html`'s phase order) — this would collapse a 4th classification system into
the same one substrate, rather than adding a 4th consumer of the shared helper.

## §BUILD PLAN
- **P1 ✅ DONE (witness)** — `bim-ootb` PR #1196 (`fix/exact-lookup-classify-p1`), base=
  `feat/ifc-schema-classification` (stacked on OPEN #1191, not `main` — `classify()` wraps
  `matchRule`'s real tier 1→2→3 which only exists on that branch). Added
  `classify(cls, hierarchy, rules, dflt)` to `viewer/schedule_author.js`
  (pure pass-through to the existing `matchRule`, rules/dflt/hierarchy default from the same `window.*`
  globals its other exports already default from; trailing `rules`/`dflt` overrides exist only so a
  Node witness can drive it without a `window` to read from — real browser call sites just do
  `classify(cls)` or `classify(cls, hierarchy)`). Exported via `ScheduleAuthor.classify`.
  New witness `viewer/tests/witness_schema_exhaustive_classify.js`: for all 1006 real IFC schema
  classes, asserts `classify()` returns the byte-identical rule object + console.warn as `matchRule()`
  (G-H, 0 mismatches) and pins the tier split to the existing witness's own baseline — tier1=132,
  tier2=53, tier3=821 (G-I) — plus a no-`hierarchy`-arg call still resolves tier 1 correctly (G-J).
  5/5 pass. Reran `witness_schema_exhaustive_fallback.js` unchanged — still 6/6, confirming `matchRule`
  itself untouched. `eslint viewer/schedule_author.js` clean. No consumer wired yet — P2 does that.
- **P2** — wire the two higher-priority live consumers (`boq_charts.html:1808/1813` chart,
  `proj_fold.js:150` ERP push) through it. Witness: rerun `witness_schema_exhaustive_fallback.js`-style
  sweep against these two call sites specifically.
- **P3** — wire `variation_order.js`'s `getPhase()` path and decide+implement the `WORK_PACKAGES`
  derivation question above.
- **P4** — lower-priority fallback-only sites (`boq_charts.html:477/908`, `schedule_read_4d.js`
  fallback) for full consistency, once P2/P3 are proven.
- Full existing regression suite must stay green at every step (this lane's own standing discipline).

## §RELATED
- `prompts/4D_SCHEDULE_PERFECTION.md` §CLASS_UNMATCHED_FALLBACK, §GANTT_EDIT/BOQ4D — the classification
  fix and the (separate, already-closed) phase-order fix this lane must not duplicate
- `prompts/BUILDINGSMART_IFC_SCHEMA_CLASSIFICATION.md` — the tier 1→2→3 substrate this lane extends
  coverage of, not a new mechanism
