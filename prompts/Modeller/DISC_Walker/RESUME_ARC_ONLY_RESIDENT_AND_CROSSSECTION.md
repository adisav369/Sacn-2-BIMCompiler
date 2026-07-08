# ⚠ DO NOT REMOVE — ARC-only resident consistency + PLB real cross-section extraction (2026-07-08)

**Read this doc in full before touching any of this again — it is the single source of truth for this thread.
Do not re-derive facts already established here; do not re-run checks already logged here. Cite this file.**

## §CONDUCT — how this thread was run, binding on whoever picks it up next (user's own repeated correction)

This investigation started from a narrow complaint (guide screenshots looked plain) and kept threatening to
sprawl into broad "let's build a witness/agent for this" work. The user corrected that same drift **repeatedly**
and explicitly — the pattern is durable, apply it to every future disc-walker/resident-DB session, not just
this one:
1. **Narrow, pinpoint checks over broad investigation.** "Very small check", "Just simple check the source of
   the DB. Narrow pinpoint.", "Go to the code itself.. need not even check Viewer. It is already proven truth
   to accept unconditionally" — every real finding in this doc came from a single targeted `sqlite3`/`grep`
   query or a single-purpose witness script, never a general-purpose sweep.
2. **No agent dispatch for something this direct.** "The agent to handle witness is redundant." Every fix in
   §DONE below was made directly in the file, verified directly, no `Agent` tool call. Reserve background
   agents for genuinely large, parallelizable, or long-running batches — not for a 4-line code change with an
   already-known root cause.
3. **Ask before executing, especially on real files — but once scoped, execute directly, no ceremony.**
   "Ask first, do not launch anything until i agree" was said explicitly before the DX/SC/Terminal DB edits
   below; once agreed, the fixes were applied directly (no witness-writing detour, no extra confirmation
   loops) — see point 2. The balance is: real/write actions get a checkpoint; read-only investigation and
   already-agreed fixes do not need repeated re-confirmation.
4. **Verify empirically, don't trust a doc's claim or your own last measurement.** The "Duplex is LOD-thin"
   conclusion from a PRIOR session was accepted at face value initially — turned out to be checking the wrong
   thing (see §LESSON-1 below). The "PLB REFUSE" from `discWalkAll` was accepted as a real finding initially —
   turned out to be a test-timing artifact needing a patient re-check (see §LESSON-4). Every "reduce X" or
   "walk uses Y" claim in this doc was re-verified against a live-running app or a live-queried DB, not
   inferred from a comment or a prior write-up alone.
5. **Reuse the exact proven recipe — don't reinvent.** "Such script is source of truth. No reinventing." The
   DX/SC ARC-only cleanup reused SampleCastle's own already-proven `4631d3564` cascade-delete verbatim (and the
   file-size match confirmed it reproduced byte-identically). The PLB cross-section extraction below is scoped
   to reuse `FP_Drop_Pipe`'s exact methodology, not invent a new one.
6. **GIGO is not a pipeline-fidelity violation; don't conflate a plain building with an invented one.**
   `WalkerDoctrine.md §11` was corrected mid-session from an overstated "detail-floor threshold, needs a design
   pass" framing down to its real, narrow scope: refuse-or-real at any code path with a real-match-or-not
   branch, nothing more. Don't re-introduce the wider framing if it resurfaces from an old doc revision.

## §DONE — with evidence, don't re-verify

1. **`modeller.html` MEP fixture placement no longer invents a wrong-class box on no-match.** `hashFor()`'s
   final fallback was a hardcoded `'ROLE__DIFFUSER'` box for ANY unmatched fixture (e.g. an unmatched toilet
   would render as a diffuser). Now refuses + counts + logs (`§WALKERDOCTRINE-11`), matching the cross-section
   refusal already correct 15 lines above it in the same function. bim-ootb PR **#708, MERGED**.
2. **`modeller_history.js`/`common/history_bar.js` de-duplicated Ctrl+Z/Ctrl+Y.** Found by a sibling session's
   witness batch: one real Ctrl+Z fired TWO undos (dual keyboard listeners since PR #675). Fixed via a new
   `skipKeyboard` config flag (additive, every other app unaffected). `witness_e2e_dm_gridundo.js` U6: 8/8
   post-fix (was pinned KNOWN-RED). bim-ootb PR **#707, MERGED**.
3. **DX/SC Modeller residents cut to ARC-only** (isolated worktree, NOT yet merged — see §NOT-MERGED below).
   Reused SampleCastle's own proven `4631d3564` cascade recipe verbatim: `DELETE ... discipline != 'ARC'` on
   `elements_meta`, cascaded by guid to `element_transforms`/`element_instances`(/`task_elements` where
   present). Real smoke-tested post-cleanup (`t.open()`, headless, 0 errors both):
   - `Duplex_extracted.db`: 265→253 elements (ARC=253/STR=12 → ARC=253 only), 950272→937984 bytes (**-1.3%
     bytes, -4.5% elements — modest, not dramatic**).
   - `SampleCastle_extracted.db`: 3621→3342 elements (ARC=3342/MEP=73/STR=206 → ARC=3342 only),
     7897088→**7770112 bytes — exact byte match to the already-existing `SampleCastle_ARC_extracted.db`
     diagnostic file**, strong confirmation the recipe reproduced the proven result exactly (**-1.6% bytes,
     -7.7% elements**).
   - **NOT applied to Terminal permanently** — its non-ARC rows are 100% real federated extraction (9 real
     discipline consultant models, confirmed via `docs/archive/TerminalAnalysis.md`), not walker output;
     stripping them would falsely flip the Disc-walk roster's present→absent and trigger a walk that REPLACES
     real data with a statistical approximation, on the one building nothing else in the system has a
     fallback source for. DX/SC carry no such downstream dependency.
   - **Checked, do not re-check: Clinic/Hospital/HHS_Office/Garage are NOT Modeller residents at all** — they
     exist only in `bim-ootb/buildings/`/`deploy/buildings/` (Viewer-side), never onboarded into
     `bim-ootb/modeller/`. The user's own conditional ("if much reduced, add the others too") does not apply —
     the reduction wasn't dramatic, AND there is no current Modeller-resident target for these four anyway.
4. **`modeller/mep_rw.db` was a 0-byte empty placeholder — real bug, now fixed.** `_rwReadyOnce()`/`rwInit('')`
   fetches `mep_rw.db` relative to `modeller.html`'s own location, but that copy was never populated (the real
   827KB file only existed at `viewer/mep_rw.db`). Confirmed via `RW_INIT` log line pre/post-fix: pre-fix
   silently produced a refuse (`pattern table not loaded`); post-fix `§RW_INIT mep_rw.db loaded bom=188
   patterns=9 anchors=5479 offsets=15 shims=11 arc_env=1`, real `CW_TERMINAL_01`/`SP_TERMINAL_01` patterns
   loaded, PLB's walk genuinely engaged the pattern bridge (`pattern:CW→PLB:7/8 pattern:SP→PLB:17/19` vs
   silent 0 before). **NOT yet copied into `bim-ootb` proper or committed anywhere — done only in the isolated
   worktree, see §NOT-MERGED.**

## §NOT-MERGED — everything in item 3/4 above lives ONLY in an isolated worktree, not shipped

`/tmp/wt-arc-only-cleanup`, branch `lane/dx-sc-arc-only-resident`, off `origin/main` (includes PR #707).
Contains: stripped `Duplex_extracted.db`/`SampleCastle_extracted.db` (+ `.pre-arc-only.bak` originals kept
alongside), the real `modeller/mep_rw.db` copy, and every diagnostic witness script written this session
(`witness_terminal_arc_walkback*.js`, `witness_plb_*.js`, `check_arc_seed_rows.js` — all under
`modeller/tests/`, logs under `modeller/tests/logs/`). **Nothing here has been committed, pushed, or PR'd** —
this was deliberately kept as an experiment until the findings below were understood. Next session: decide
whether to commit+PR the DX/SC cleanup and the `mep_rw.db` fix (both are real, tested, low-risk) as their own
small PRs, separate from the PLB cross-section work (which is bigger and not yet started).

## §TERMINAL SELF-WALK-BACK EXPERIMENT — findings, don't re-run

Reversible experiment (independently-preserved real backup verified first: `deploy/dev/buildings/
Terminal_meta.db`, byte-identical discipline counts, confirmed before touching anything). Stripped a COPY of
Terminal's resident to ARC-only, walked disciplines back via `terminal_rules.db` (Terminal's own NATIVE
primary rules, no borrow), compared against the real original. **Count-only comparison is the correct/only
fair one** — the walker is a statistical cadence model (median spacing/density), not a GUID-replay; verified
directly (`disc_walker.js` `repRules()`/`hostWalls()`/`occupancy()`) that it never reads or replays Terminal's
own stored real MEP positions, even though Terminal is both the training source AND the test subject here —
confirmed NOT cheating.

| Disc | real | walked | deviation | status |
|---|---|---|---|---|
| ACMV | 1570 | 1828 | +16.4% | reasonable, unexplained further, not investigated deeper |
| ELEC | 833 | 2071 | +148.6% | significant overshoot, NOT investigated — flagged, open |
| FP | 989 | 1193 | +20.6% | reasonable |
| PLB | 8175 | 28→34 (post `mep_rw.db` fix) | −99.6% | **NOT a bug — see §PLB-CROSSSECTION below, this is WalkerDoctrine §8's honest-refuse working correctly** |
| STR | 1032 | 0 | −100.0% | REAL gap, needs general rules — see §STR below |

## §PLB-CROSSSECTION — why PLB under-produces, and it's correct, plus the real next step

Full internal log (`modeller/tests/logs/W-PLB-DEEP2_*.log` in the worktree) traced the exact mechanism:
`§DW §WALK disc=PLB placed=28 chainSegs=24 [pattern:CW→PLB:7/8 pattern:SP→PLB:17/19]` — the pattern bridge
genuinely engages (post `mep_rw.db` fix) and finds real chain segments. But
`§ROUTER-CHAIN-REFUSE disc=PLB refused=24/24 (CW:7 SP:17 — no real cross-section product, WalkerDoctrine.md §8)`
— all 24 real chain segments get honestly refused at commit because no verified real pipe cross-section
product exists for CW/SP in `component_library.db` — **this is `WalkerDoctrine.md §8`'s hard rule working
exactly as designed**, the same "refuse, don't invent" principle applied to the fixture-box fix (§DONE item 1)
above, here already correct without anyone touching it. **PLB's low count is not a defect to fix — it is the
system correctly declining to fabricate a pipe network it can't back with real data.**

**The real next step, scoped, NOT started:** `component_library.db`'s `component_definitions` table DOES hold
Terminal's own real per-instance pipe segments (`IfcPipeSegment_SJTII_Terminal_<guid>`, raw dumps, no shared
reusable name yet — same state `FP_Drop_Pipe` was in before someone extracted IT). A quick real-data check
this session (read-only, `library/component_library.db`, not yet acted on) found **at least 3 distinct,
CONSISTENT, recurring real cross-sections** across many independent instances (not noise):
- ~26.7mm × 26.7mm (multiple instances, lengths 2.19m/8.76m/3.35m/...)
- ~42.2mm × 42.2mm (multiple instances, lengths 3.08m/4.69m/...)
- ~73.0mm × 73.0mm (multiple instances, lengths 4.68m/7.15m/4.25m/...)

**Why this is bigger than a same-recipe fix (unlike the FP case):** `FP_Drop_Pipe` was ONE real product to
extract. PLB genuinely has multiple real sizes in service — a rigorous fix means deciding how many sizes to
register as verified `RW_REAL_CROSSSECTION` products (by frequency? matching real nominal plumbing sizes like
25/32/65mm?), a real curation decision, not a one-line constant add. **A previously-rejected shortcut exists
and must NOT be reused**: `viewer/dagevu_catalog.json`'s named CW/SP entries (`PIPE_COLD_WATER_25MM` etc.)
were checked 2026-07-07 and found to have WRONG declared dimensions vs. their own linked real geometry
(a "250mm round duct" that measures 48mm×70mm) — do not fall back to that catalog to save time, it is known
unreliable.

**Options for whoever picks this up (not decided here):**
(a) minimal — register just the most common of the 3 sizes as a first real verified PLB product (strictly
better than today's zero, same honest-refuse pattern for anything that doesn't match one size);
(b) full — extract and register all real recurring sizes found (needs a fuller scan than this session's quick
check, which only sampled the first ~10 rows — get the real frequency distribution before deciding "most
common"); (c) treat as its own dedicated session given the curation-decision weight, not a quick add-on.

## §STR — needs general rules, explicitly deferred, with a stated design bias for whenever it's picked up

STR-surfacing (`swbTabData`/`§DISC-WALK STR surfaced`) is a SEPARATE mechanism from the MEP-family
`rule_placement` engine — it does not back-fill synthetic columns/girders once real STR rows are gone
(confirmed: `columns=0 girders=0` after the ARC-only strip, genuinely nothing placed, not a timing artifact).
**User's own words, verbatim, for whenever this is scoped:** "STR then needs general rules, this warrants
wider research... over STR'd a building is better than under in my non expert mind." — i.e. whenever a
structural-inference mechanism is eventually built for STR, bias it toward conservative OVER-provision
(placing more structure than strictly measured/necessary) rather than under-provision, matching real
engineering conservatism. **Not scoped further than this — a real, separate, future piece of work.**

## §DECIDED, NOT YET BUILT — record, don't re-litigate

1. **`roof` discipline stays Terminal-only.** The one `rule_placement` row for `roof` (`ref_kind=datum`) in
   `terminal_rules.db` was measured FROM Terminal — not to be offered as a general walkable discipline on
   other buildings until there's real confidence it generalizes. A dedicated, separate roof walker is the
   right future home for general-building roof handling. **Gate not yet implemented** — likely belongs next
   to `_dwRules()`'s existing building-class routing in `modeller.html`.
2. **A `walker.config` concept, agreed in shape, not yet in code.** Confirmed via grep: no such mechanism
   exists anywhere today. Agreed shape: a per-building(-class) config governing (a) which disciplines are even
   offered in the roster (the roof gate above is the first concrete case) and (b) guaranteeing any needed init
   (`_rwReadyOnce()`/`mep_rw.db` for PLB) fires before a walk regardless of entry path, rather than being
   best-effort/racy as it is today. **Not built.**

## §CANDIDATE-C-STATUS — Terminal roof/IfcPlate batch-signing, re-confirmed as the decided approach but NOT verified as implemented

`RESUME_MODELLER_TERMINAL_LOAD_LOD400.md` (2026-07-01) scoped 4 candidates (A-D) for Terminal's 14s open time
(6-7s of it pure crypto signing of 35,552 individual ARC ops, 33,324 of them roof `IfcPlate`s). Candidate C
("batch-sign bulk classes as ONE row, not N — try this FIRST") was the user's pick then, **re-confirmed
2026-07-08 ("we did decide to sign those cluster as single action")**. Checked today whether it shipped:
`grep -n "batch" arc_editable.js` → zero hits; a live empirical check (`window.Bonsai.oplog._geomOps()` count
right after `t.open('Terminal')`) returned `totalOps=0`, which is **inconclusive** (later patient re-checks in
this same session showed the ARC seed can legitimately take 30+ seconds to settle under load before producing
its real 35,552-op count — the 0 reading was almost certainly premature, not evidence either way). **Genuinely
unverified — do not assume shipped or not-shipped without a clean, patient re-check** (poll
`_geomOps().length` until it stabilizes non-zero, the way `witness_plb_patient.js` in the worktree does,
before concluding anything).

## §NEXT — prioritized, nothing here is started

1. Decide + commit/PR the DX/SC ARC-only cleanup + `mep_rw.db` fix from the worktree (both real, tested,
   low-risk, just sitting uncommitted).
2. Cleanly re-verify Candidate C's real implementation status (patient poll, not a premature read).
3. Build the `roof`-discipline gate + `walker.config` init-guarantee (both decided, neither built).
4. Scope + do the PLB real cross-section extraction (option a/b/c above — needs a decision on depth first).
5. STR general rules — explicitly a separate, later, "wider research" item; carry the over-provision bias
   forward when it's picked up.
