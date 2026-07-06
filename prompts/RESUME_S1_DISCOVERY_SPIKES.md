# ⚠ DO NOT REMOVE — Scope & Standing Rules (honour until this prompt is ✅ DONE)

**Scope (ONE bounded task — MEASURE, do not remedy):** Run the **S1 discovery spikes** from
`docs/ProductionRisks.md` — find out *how bad* the three highest production risks actually are, on real
conditions, and record measured numbers. This is the **front-loaded discovery** the governance
"Sequencing" note mandates: *never delay the discovery, only the build.* **STOP before building any
remedy** (remedies are tenant-gated — out of scope here).

**Why now (read `docs/ProductionRisks.md §Governance` + §G2):** this paradigm's failures are *silent*
(no server log), so an unmeasured S1 risk is **unknown**, not deferred — and the findings reshape the
roadmap (e.g. a bad B1 makes §13 sharding urgent). Cheapest moment to find the angel's gaps is *before*
more is built on top.

**Read first:** `docs/ProductionRisks.md` rows **A1, B1, C1, G2** (the failure scenarios + test plans);
`docs/FoldEngineConstraints.md §6` (the signals: `quota_used_pct`, `offline_queue_mb`, `bootstrap_path`);
`build/erp/vfs_detect.js` / `offline_queue.js`.

**Log Mandate:** save each spike's output to a log file and **read the log before any conclusion** — the
`§SPIKE-*` line IS the result. Whitebox `§`-log first; Playwright/real-device only to drive the run.
Witnesses prove; **never invent a number** — if a device/condition is unavailable, record `blocked` honestly.

**Standing rules:** Deterministic · Non-invent · Extract. Edit `deploy/dev/` ONLY. Push before finishing
(`git rev-list --count origin/<branch>..HEAD` == 0).

---

## The spikes (each NAMES the issue it proves/disproves)

Each is a ~half-day measurement, not a build. Output = a number + a verdict, written back into the register.

### Spike A1 — does durability beat eviction?
- **Question:** when storage is evicted (Safari ITP / low-disk / user-clear), did an auto-snapshot fire
  for the un-acked tail *first*, and does recovery replay it?
- **Method:** append N ops offline → force eviction (devtools "clear storage" / Safari 7-day clock) →
  attempt recovery from the latest signed snapshot.
- **`§`-log:** `§SPIKE-A1 unackedAtEvict=N snapshotFiredBefore=Y/N recoveredOps=N lostOps=N`
- **Proves/disproves:** whether the A1 data-loss window is actually closed, or open.

### Spike B1 — does the biggest building survive a real mid-tier phone?
- **Question:** peak heap + time-to-interactive opening the largest real building (+ full AD) on a real
  ~$300 Android and an older iPhone — does it stay under budget, or crash?
- **Method:** load the largest committed building in the dev viewer on the device; capture
  `performance.memory` peak, TTI, and any tab crash. Use a real device; if only headless, mark `proxy`.
- **`§`-log:** `§SPIKE-B1 device=… elements=N peakHeapMB=… budgetMB=… crashed=Y/N ttiMs=…`
- **Proves/disproves:** how close the in-memory ceiling is — and whether §13 sharding is urgent or not.

### Spike C1 — does it even run on iOS Safari?
- **Question:** on current + one-back iOS Safari, do scripts load, does the DB return data, does it render,
  does share work?
- **Method:** real iOS Safari (BrowserStack/Sauce or a physical device). If none available, record
  `blocked: needs device` — do NOT fake it.
- **`§`-log:** `§SPIKE-C1 browser=… loads=Y/N dbReturns=Y/N renders=Y/N shares=Y/N notes=…`
- **Proves/disproves:** whether the browser-zoo assumption holds on the hostile target.

### Beacon (minimal, not the pipeline) — stop flying blind
- **Do only this much of G2:** wire `window.onerror` + `unhandledrejection` to a `§`-tagged console line +
  an offline buffer. **No telemetry pipe, no PII** — just make field errors *capturable*.
- **`§`-log:** `§SPIKE-BEACON installed=Y captured=N sample=…`

## Done =
- Four `§SPIKE-*` lines in saved logs, each with a real measured value (or an honest `blocked`).
- A one-paragraph **verdict per spike** ("how bad is it") written into the matching A1/B1/C1 register
  rows in `docs/ProductionRisks.md`, updating severity/likelihood from *estimated* to *measured*.
- The minimal error beacon live in `deploy/dev/`; branch pushed (0 local-only).
- **Explicitly NOT done here:** any remedy build (snapshot-on-eviction, sharding code, device-lab CI,
  telemetry pipeline). Those are tenant-gated — they become their own prompts *after* these numbers land.

## NEXT (separate sessions, ordered by what the numbers say)
- Worst spike first → its remedy (e.g. B1 bad → bring `§13` gravity-sharding from spec to code).
- Widget ① build runs in parallel: `prompts/RESUME_PROVE_BOOKS_MONITOR.md`.
