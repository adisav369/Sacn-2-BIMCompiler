# REAL P6 EXPORT — the proof fixture that retires the synthetic-demo caveat

# ⚠ DO NOT REMOVE
**Scope:** replace the synthetic demo fixtures (`tests/fixtures/Hospital_GW_*`) with a **real exported
Primavera P6 / MS Project file** as the correctness fixture, and add a witness that proves our readers
survive *real-world* P6 quirks — not just our own well-formed output. **Read the §-log after every run.**
**Non-invent:** the fixture MUST be a genuine export; do NOT hand-author one (a fabricated file proves
nothing about the quirks this lane exists to catch). Honour until DONE.

## §WHY — what the demo fixtures CANNOT prove
`W-FGN` 28/28 proves the readers parse *our generator's* output and that the adopt→CPM→bind→5D chain
works on the real Hospital model. It does **not** prove the readers handle a real P6/MSP export, which
differs in ways a self-authored file never will:
- column REORDER + extra/unknown tables in XER (`%F` order varies by P6 version; we're `%F`-driven, but
  unexercised on real headers); UDF/Activity-Code/Resource tables we currently ignore.
- real CALENDAR rows (multiple calendars, non-8h/day, exceptions) → the hour→day divisor.
- date locale/format variants; `target_drtn_hr_cnt` fractional; negative lag; `WBS` depth > 1.
- PMXML namespace prefixes, self-closing/empty elements, ObjectId vs Id reference styles.
- MSPDI `Duration` in non-`PT` forms, `OutlineLevel` gaps, milestones (`Duration`=0), summary roll-ups.

## §FIXTURE — obtain it (the gate; ⛔ user-supplied)
Acquire ONE, in order of preference (cite the source in the fixture header):
1. **User-exported file** — smallest real project they have (ideally Hospital-adjacent), `.xer` and/or
   P6 `.xml` and/or MS Project `.xml`. Best fidelity.
2. **A cited public sample** — an open P6/MSP training export (cite the URL). Acceptable if (1) absent.
Place under `tests/fixtures/real/` with a `SOURCE.md` (provenance + any anonymisation note). Until one
exists this lane is **⛔ BLOCKED: provide one real exported P6/MSP file (or approve a cited public sample).**

## §SLICES (spec-first; each names its witness)

### §R1 — structural-sanity witness (no known-answer oracle — a real plan's CP is unknown)
`erp/tests/foreign_real_witness.js` over the real file. Because we can't assert "300d / 13 critical",
assert **internal consistency + cross-table integrity** instead:
- **W-REAL-PARSE**: parsed counts == a raw count of records in the file (`%R` per table for XER; element
  counts for XML); **0 unmapped relationship types**; **0 dangling** TASKPRED/PredecessorLink (every
  pred/succ resolves to a parsed task); every leaf has a parseable duration; dates ISO-valid.
- **W-REAL-CAL**: the hour→day divisor came from the file's CALENDAR/`MinutesPerDay`, not the 8h default
  (or, if it IS 8h, that's what the file says) — §-log which calendar + divisor was used.

### §R2 — fidelity vs the file's OWN CPM (the real-world correctness claim)
A real P6/MSP file already carries P6's computed `driving_path_flag` / `total_float` (XER) or
`Critical`/`TotalSlack` (MSPDI). Adopt it, run our `computeCpm`, and **compare our critical set to the
file's** — this is the genuine fidelity proof (does our FS/SS/FF/SF+lag pass agree with Oracle's?).
- **W-REAL-CPM**: on the comparable subgraph (tasks with no resource-calendar / constraint divergence),
  our `is_critical` set **matches** the file's flagged-critical set; divergences are **itemised**
  (count + task ids + likely cause: calendar, constraint, lag-calendar) — **reported, never hidden,
  never loosened**. A material agreement (e.g. ≥90% of flagged-critical reproduced) is the pass bar;
  the itemised remainder is the honest tail (real P6 honours calendars/constraints we don't model).

### §R3 — adopt + render on a real model (the visual proof)
Adopt into a real building DB, open the Schedule Editor, screenshot — the real WBS/deps/CPM/Gantt.
- **§REAL-SMOKE**: headless import of the real file renders WBS + activities + links, Compute CPM
  lights a critical path, zero page errors (wiring check, secondary to W-REAL-*).

### §R4 — promote + retire the caveat
Once W-REAL-* green: `PROOFS_INDEX.md` records the real fixture as the authoritative proof; the
ERPUserGuide demo-files note + `prompts/XER_IMPORT_P6_ADOPT_LANE.md §FIXTURE` lose the "synthetic demo"
caveat (the generator + `Hospital_GW_*` stay as a *demo convenience*, no longer the proof).

## §LOG
- 2026-06-25 — Spec opened from XER_IMPORT_P6_ADOPT_LANE §FIXTURE open item. BLOCKED on a real export.
  Readers are LIVE (`viewer/foreign_schedule.js`, all 3 formats) so R1–R3 are code-ready the moment a
  file lands. Cross-ref [[project_foreign_schedule_import]] and [[feedback_rosetta_proof_real_building]]
  (the proof is the REAL artifact, never synthetic).
- 2026-06-25 — **W-REAL-PARSE ✅ + W-REAL-CPM ✅ (XER); MSPDI/PMXML real-fixture ⛔ (await a genuine
  Project/P6 export)** (bim-ootb PR #524 off fresh `origin/main`, auto-merge SQUASH armed). User decision:
  use a genuinely P6-emitted PUBLIC sample (not hand-authored) + explain in the guide at best effort —
  *"there may be a gap but it's smaller than before, later projects can close it."* Source =
  `ASHspace/PrimeveraXEREditor` `sample.xer` (ERMHDR 20.12, 2021-03-11, "Task SE": 1 PROJECT / 1 CALENDAR /
  6 PROJWBS / 52 TASK / 61 TASKPRED). **Repo has NO declared license → NOT vendored**; the witness
  `erp/tests/real_xer_witness.js` FETCHES it at runtime (node https, SHA-pinned
  `68ce5f0b…`) and SKIPs cleanly offline — same contract as the Hospital-DB skip. **W-REALXER 12/12:**
  - W-REAL-PARSE — parsed counts MATCH raw `%R` rows (6/52/61/1), 0 unmapped link types, 0 dangling
    relationships, real dates on every activity, adopt→`activeSchedule` captured, 61 sequences 0 null-type.
  - W-REAL-CPM — our `computeCpm` critical set **== P6's own `driving_path_flag` 52/52** (topology +
    critical fidelity against P6's authoritative output, NOT our own — retires the tautology). Start dates:
    3 exact / 49 up to 59d earlier, **proven one-directional (ours never later)** → the gap is the **P6
    WORKING CALENDAR** (P6 skips weekends/holidays; our CPM uses calendar days). NAMED + reported
    (`§REALXER-FINDING`), never loosened. **Follow-up (parked): model the working calendar to close the
    date gap.** This file is fully critical (all `driving_path_flag=Y`, float not computed at export) — a
    real-P6 quirk; our CPM independently also computes all-52 critical here, so the set still matches.
  - Guide: ERPUserGuide §Import gained a "Does it survive a *real* P6 file?" note (best-effort framing +
    the named calendar gap). MSPDI/PMXML real-fixture proof still ⛔ until a genuine Project/P6-emitted
    `.xml` lands (the demo MSPDI is ours = tautology for fidelity). [[feedback_rosetta_proof_real_building]]
