# ⚠ DO NOT REMOVE — Scope & Standing Rules (honour until ✅ DONE)

**Scope (ONE bounded task):** build the **New-Paradigm System Monitor** — 4 field-health widgets that turn the
S1 discovery-spike signals into live **SysAdmin optics** (`docs/ProductionRisks.md §H`). The classic iDempiere
System Monitor watched the *server's* vitals (JVM heap, DB pool); with no server, this watches the *paradigm's*
vitals. **Each widget folds a REAL signal — NON-INVENT.** No fabricated readouts; a missing signal renders
`n/a`, never a guess.

**Log Mandate:** `§MON-*` witness lines ARE the proof. Whitebox `§`-log first. Read the log before conclusions.
**Standing rules:** Deterministic · Non-invent · Extract. ERP-engine source = `build/erp/`. Push before finishing.

---

## §SPEC — the 4 widgets (each folds one spike's signal)

The monitor is a pure fold: `SystemMonitor.fold(signals) -> { widgets:[…], overall }`. Each widget is
`{ id, label, value, status: ok|warn|alert|na, detail }`. A separate `render(readout, el)` paints the DOM —
the fold is testable headless without a browser (holy-grail law: declarative fold + rules exposed).

| # | Widget id | Folds (signal source) | Risk | Thresholds (FoldEngineConstraints §6) |
|---|---|---|---|---|
| 1 | `field_errors` | `window.__ERR_BEACON__.captured` + `.list()` (error_beacon.js) | **G2** | 0 = ok · ≥1 = warn · with `kind=error`/`promise` recent = alert |
| 2 | `durability_ladder` | OfflineQueue: `count`/relayed/`oldestAgeMs`; `quota_used_pct`; `persisted()` | **A1** | oldestUnacked >6 d **or** quota >70% **or** persisted=false = warn; any unrelayed shown "safe" = **alert** |
| 3 | `db_size_gauge` | op-log DB MB = `PRAGMA page_count × page_size` vs 200 MB ceiling | **B1 (real)** | <100 MB = ok · >100 MB = warn (compact) · >200 MB = alert (OOM) |
| 4 | `environment` | `vfs_detect.detect()` backend + `bootstrap_path` | **C1** | opfs/idb ok · `misconfig` (idb on COOP/COEP) = warn · `genesis` on mobile = alert |

### §WITNESS CLAIMS (prove-first, per CLAUDE.md)

- **W-MON-FIELD-ERRORS** — `fold` reports `captured=N` from the beacon; 0 → status=ok; inject 2 errors → warn,
  value=2. `§MON-FIELD captured=N status=…`. Falsifier: a captured error that the widget reads as 0 = FAIL.
- **W-MON-DURABILITY** — with 30 relayed + 20 unrelayed queued, widget shows `durable=30 inflight=20`, status=warn
  (unsynced exist), and **never** labels an unrelayed op "durable". `§MON-DUR durable=N inflight=N status=…`.
  Falsifier: inflight counted as durable = FAIL (this is the A1 "UI never lies about safety" invariant).
- **W-MON-DBSIZE** — fold reads true `PRAGMA page_count×page_size` from a REAL sql.js op-log DB; a ~13 MB log →
  value≈13, status=ok, headroom shown vs 200 MB (the reassuring B1 optic). `§MON-DBSIZE mb=… ceiling=200 status=ok`.
  Falsifier: a >200 MB DB reported ok = FAIL.
- **W-MON-ENV** — fold reads `vfs_detect` backend; idb-on-GH-Pages → status=ok reason-named; idb-on-isolated →
  warn `misconfig`. `§MON-ENV backend=… status=…`. Falsifier: misconfig reported ok = FAIL.
- **W-MON-OVERALL** — `overall` = worst widget status; all-ok → ok; any alert → alert. `§MON-OVERALL=…`.

### §DELIVERABLES
- `build/erp/system_monitor.js` — `fold` + `render` (UMD; render no-ops headless).
- `build/erp/system_monitor.html` — standalone host (the demo/test surface; Classic↔Angelic toggle).
- `scripts/poc_system_monitor.js` — the §-log witness (REAL sql.js DB + REAL OfflineQueue + REAL beacon + VFS).
- Deploy-stage to `deploy/dev/` (the editable viewer copy) so it is reachable; wire into the ERP login-panel
  `idempiere.html` monitor when `bim-ootb` is reachable (shared-tree hook blocks it this session).
