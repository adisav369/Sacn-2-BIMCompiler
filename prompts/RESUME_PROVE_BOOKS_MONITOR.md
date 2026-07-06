# ⚠ DO NOT REMOVE — Scope & Standing Rules (honour until this prompt is ✅ DONE)

**Scope (ONE bounded task):** Build **W-MON-PROVE-BOOKS** — the first widget of the *New-Paradigm Monitor*:
a *Verify* button in the iDempiere login-panel System Monitor that **rebuilds the books from zero events,
live**, and shows `replay-hash == live-hash`. This is the cheapest-highest-impact slice of the **G2 —
observability** remedy in `docs/ProductionRisks.md §H`.

**Read first (do not skip):**
- `docs/ProductionRisks.md §H` (the widget set + the three build-ready specs; this is spec ①).
- `docs/ProductionRisks.md §G2` (why observability is the operational blind spot of "no server").
- `docs/DistributedERP.md §7` (determinism = infrastructure; `replay-hash == live-hash`).
- `docs/FoldEngineConstraints.md §6` (the existing monitor signals you wire beside this).

**Log Mandate (every run):** save output to a log file and **read the log before any conclusion** — exit
code is not evidence. The `§`-log line IS the proof. Whitebox `§`-log first; Playwright only for wiring
(scripts load, button exists, click fires). See `docs/TestArchitecture.md §Browser Testing`.

**Standing rules:** Deterministic · Non-invent · Extract. Edit `deploy/dev/` ONLY (never `deploy/live/`).
Deploy flow = edit → syntax check → verify `§` tags → save test log → upload to dev bucket (every
`oci os object put` MUST set `--content-type`) → smoke URLs → fetch back → confirm loaded. Push before
you finish (`git rev-list --count origin/<branch>..HEAD` must be 0).

---

## The spec (witness-claim first)

**Claim (W-MON-PROVE-BOOKS):** On *Verify*, the monitor clones the current op-log into a **fresh**
in-memory kernel, replays it deterministically, hashes the folded state, and compares to the live hash —
proving to the user, on demand, that every number is a **fold over a signed log**, not a stored cell.

**Surface:** a button in the login-panel System Monitor (Classic ↔ Angelic toggle, `§H`). Render result:
`✓ books rebuilt — N ops, tip <hash8>, <ms> ms` (green) or a red mismatch.

**Reuse (do NOT re-implement the kernel):** the `replay-hash == live-hash` path already proven in
`scripts/erp_kernel.js` / `poc_kernel.js` / `poc_longtail.js` and the live `build/erp/kernel_ops.js`.
This task is a **button over an existing witness**, not new engine code.

**Acceptance `§`-log (the proof):**
```
§MON-REPLAY ops=N replayHash=<hex> liveHash=<hex> match=Y ms=<n>
```
- `match=Y` on a real op-log (use a real SampleHouse / seeded `kernel_ops`, never synthetic).
- **Falsifier (must also pass):** corrupt exactly one replayed op → `§MON-REPLAY … match=N` and the UI
  shows red. A test that can't show the mismatch is not a test (name the issue it proves).

## Steps

1. **Spec line** in code: `// Implementing ProductionRisks.md §H ① — Witness: W-MON-PROVE-BOOKS`.
2. Locate the login-panel System Monitor render in `deploy/dev/` (the ERP app surface) and the
   `kernel_ops` accessor; add the *Verify* button (Angelic mode).
3. Wire `verifyBooks()`: snapshot op-log → fresh kernel → replay → hash → compare → emit `§MON-REPLAY`.
4. **Whitebox witness** (node/headless, primary): real op-log → `match=Y`; tamper one op → `match=N`.
   Save to `build/erp/poc_mon_prove_books.log`; READ the log; confirm both lines.
5. Playwright (secondary, wiring only): button exists, click emits `§MON-REPLAY` to console.
6. Deploy to dev bucket (MIME!), smoke the URL, fetch back, confirm the button renders + logs fire.
7. Append a `# DONE` block: each claim → its `§MON-REPLAY` log line (watchdog will check).

## Done = 
- `§MON-REPLAY match=Y` on real data **and** `match=N` on the tamper falsifier, both in the saved log.
- Button live in the dev monitor; whitebox + wiring tests green; branch pushed (0 local-only).
- Flip `docs/ProductionRisks.md §H ①` status 🟡→✅ citing the log line; note in the G2 row.

## NEXT (do not start here — separate sessions)
- **② W-MON-DURABILITY-LADDER** (`§H ②`) — the most important field-health widget (A1/G2).
- **③ W-MON-SERVERLESS-METER** (`§H ③`) — the "feel the zero" wow widget (§11.1).
