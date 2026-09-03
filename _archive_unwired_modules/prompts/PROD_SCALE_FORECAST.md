# ⚠ DO NOT REMOVE
**Scope:** A scale/lag forecast harness. Drive the REAL fold engine at full-production iDempiere
cardinalities, measure where its OWN internal lag (op-log growth, per-write re-fold, projection bloat,
cold-boot cliff) becomes user-perceptible, and turn the measured curves into a **business-capacity
forecast**: documents/day it sustains, and the size of ERP department it can carry — WITH and WITHOUT
each mitigation (checkpoint / batch / prune).
**Non-invent law:** every forecast number is a *fit to measured points*, never a typed-in guess. The
production profile is *sized from the real iDempiere oracle's table cardinalities*, not assumed. An
extrapolated cell is tagged `(forecast)`; a measured cell is tagged `(measured)`. Never present the two
as the same kind of number.
**Read the log after EVERY run.** Exit code is not evidence. `bash build/erp/run_witness.sh
scripts/poc_scale_forecast.js` → read `build/erp/poc_scale_forecast.log` before any conclusion.

---

## §0 The claim under test
> *Stated once.* At a robust, non-trivial production load — a multi-year iDempiere tenant with a real
> document mix — the Fold Engine's internal lag stays under the user-perceptible budget on every surface
> (write, boot, storage), and the deployed strategies (checkpoint bootstrap, batch `commitGroup`,
> projection prune) are what keep it there. We can **forecast the docs/day and the ERP-department size**
> the system carries before any surface crosses its budget.

This is a *falsifiable forecast*, not a benchmark vanity run. Each witness names the lag surface it
proves or refutes (Standing Rule: tests expose issues).

## §1 The four lag surfaces (what we are actually racing)
Anchored to the engine's already-measured reality — do NOT re-derive, EXTEND these points:

| Surface | Cost shape | Driver var | Measured anchor (source) | Budget |
|---|---|---|---|---|
| **S-WRITE** per-commit latency | re-fold = full GROUP BY per write → **O(live docs)** (I-5) | `D` = open docs | ~500 op/s end-to-end (`spike_writepath.log`) | <100 ms (feels instant); <16 ms ideal |
| **S-BOOT** cold start | genesis re-fold replays whole log → **O(N)** | `N` = total op-log len | 100M ops = **25 s** mobile; checkpoint boot **~9 ms FLAT** (`bootstrap_path.js`) | <2 s good, >10 s broken |
| **S-STORE** projection size | rich JSON payload/op → **O(N)** bytes | `N` | **~481 B/op** (52→992 KB / 2000 ops, `spike_writepath.log` I-3) | IDB/OPFS practical + mobile RAM |
| **S-THRU** sustained throughput | batch amortises commit+seal | batch `B` | naive **9,390** vs `commitGroup` **22,492 ops/s** = **2.4×** (`sync_poc_smoke.log`) | ≥ daily op load ÷ working seconds |

The two re-fold costs are DISTINCT and must never be conflated: **S-WRITE is O(live docs)** (per keystroke-commit), **S-BOOT is O(total ops)** (cold start). They scale on different variables and have different fixes.

## §2 Production profile — SIZED FROM THE ORACLE, not invented
Before any sweep, derive the load shape from real iDempiere, so "production" is extracted, not guessed.

1. **ops-per-document (the conversion constant).** Drive ONE real `C_Order` through the live 6-verb O2C+FI
   fold (create → complete → ship → invoice → post → allocate) and **count `kernel_ops` rows appended**.
   Repeat for invoice-only, payment, GL journal. Emit `§SCALE-OPSDOC doctype=<t> ops=<k>`. This `k` is the
   bridge from business docs to engine ops — MEASURED, never assumed.
2. **doc mix + GL depth.** From an iDempiere reference DB (`build/erp/13-idempiere.db` / the
   `idempiere_test` oracle), count `C_Order`, `C_Invoice`, `M_InOut`, `C_Payment`, `Fact_Acct` rows and
   `Fact_Acct` lines per posted doc. Emit `§SCALE-MIX orders=… invoices=… shipments=… factlines/doc=…`.
   This sets the realistic ratio of cheap vs GL-heavy documents.
3. **production envelope.** Scale the measured per-period mix by `YEARS × VOLUME_MULT` to a stated
   envelope. Label every envelope input `(assumption: …)` and make it a CLI arg — never bake a guess in.
   Default envelope to sweep: small / mid / large tenant (see §4).

## §3 Harness — reuse, do not reinvent the timing path
`scripts/poc_scale_forecast.js` is a *parametric driver* over the EXISTING measured paths. It must call the
real engine, never a synthetic stopwatch loop:
- per-write latency → the `spike_writepath.js` write-path core (real fold + project + persist).
- throughput naive vs batch → the `sync_poc` commit/seal path.
- cold boot ON/OFF checkpoint → `bootstrap_path.js` genesis vs checkpoint bootstrap.
- prune ON/OFF → the I-3 compaction path (`erp_period_close.js` checkpoint drops pre-anchor ops).
Each inner run already §-logs; the forecast harness aggregates their `§` lines, it does not re-time them.

## §4 Sweep matrix (measure a few points per axis, then FIT)
Measure ≥3 points per axis so a curve can be fit (2 points can't reveal super-linearity):

- **S-WRITE vs D (live docs):** D ∈ {100, 1 000, 10 000, 50 000} → fit `write_ms(D) ≈ a + b·D`. Report the
  D where `write_ms` crosses 100 ms. Run prune ON and OFF.
- **S-BOOT vs N (total ops):** N ∈ {10 k, 100 k, 1 M, 10 M} → fit `boot_ms(N) ≈ c + d·N`; extrapolate to the
  envelope N. Run **checkpoint OFF (genesis)** vs **ON** — expect ON to flatten toward the ~9 ms constant.
- **S-STORE vs N:** confirm `bytes(N) ≈ 481·N` holds across the sweep; with prune, `bytes ≈ 481·(N since
  last checkpoint)`. Report projected DB size at envelope N.
- **S-THRU vs B:** B ∈ {1, 10, 100, 1 000} → confirm the 2.4× batch speedup and find where it saturates.

Envelope tiers to forecast against (each an `(assumption)` arg, tune from the oracle in §2):
- **small** ≈ 5 k docs/yr · 1 yr · ~10 users
- **mid** ≈ 50 k docs/yr · 3 yr · ~50 users
- **large** ≈ 500 k docs/yr · 5 yr · ~200 users

## §5 Forecast model (the fit — and its honesty rules)
For each surface, fit the measured points to its KNOWN complexity (§1), then extrapolate to envelope N/D.
- State the fit (`a,b,c,d`, R²) in the log. A poor R² (<0.95) = the cost shape is wrong → STOP, do not
  extrapolate a bad model.
- Every extrapolated cell is tagged `(forecast)` with the fit it came from; measured cells `(measured)`.
- Report the **crossover**: the N or D at which each surface first exceeds its §1 budget — that is the
  capacity ceiling, with and without the mitigation.

## §6 BUSINESS TARGETS — docs/day and ERP-department size
Translate engine numbers into the only units a buyer cares about. All derived from §2's measured `k`
(ops/doc) and §4's throughput — arithmetic over measured constants, nothing invented:

- **Daily op budget:** `ops/day = sustained_ops_per_s × working_seconds/day` (default working window an
  `(assumption)` arg, e.g. 8 h = 28 800 s; or 24 h for batch/integration load).
- **Docs/day sustained:** `docs/day = ops/day ÷ k_weighted` (k weighted by the §2 doc mix).
- **ERP department it carries:** `clerks = docs/day ÷ docs_per_clerk_day`, where `docs_per_clerk_day` is a
  stated `(assumption)` band (e.g. AP/AR clerk ~50–200 posted docs/day; tune per role). Report a RANGE,
  not a false-precision single number.
- **Concurrency note:** S-WRITE (O(live docs)) is the *interactive* ceiling (how laggy each clerk's Save
  feels at the day's open-doc count); S-THRU is the *aggregate* ceiling (how many the whole dept posts).
  Report BOTH — a system can be fast per-click yet capped in aggregate, or vice-versa.

**Target table to emit (`build/erp/scale_forecast.md`):**

| Tier | docs/yr | live-doc D | total-op N | write ms (D) | boot s (N) | DB size (N) | **docs/day** | **ERP dept (clerks)** | limiting surface |
|---|---|---|---|---|---|---|---|---|---|
| small | … | … | … | …(measured) | …(measured) | … | … | ~N–M | … |
| mid | … | … | … | …(forecast) | …(forecast) | … | … | ~N–M | … |
| large | … | … | … | …(forecast) | …(forecast) | … | … | ~N–M | … |
| large, **no checkpoint** | … | … | … | … | …(forecast, 25 s cliff) | … | … | — | S-BOOT |
| large, **no batch** | … | … | … | … | … | … | … | ~N–M (÷2.4) | S-THRU |

## §7 Witnesses (the §-tagged claims — no log line = not done)
- `§SCALE-OPSDOC` — ops/doc measured per doctype from the real fold (§2.1).
- `§SCALE-MIX` — doc mix + Fact_Acct depth read from the oracle (§2.2).
- `W-SCALE-WRITE` — `§SCALE-WRITE D=… ms=… fit b=… R2=… cross100ms@D=…` (prune ON/OFF).
- `W-SCALE-BOOT` — `§SCALE-BOOT N=… genesis_ms=… checkpoint_ms=… flat=Y/N` — proves checkpoint flattens O(N)→~const.
- `W-SCALE-STORE` — `§SCALE-STORE N=… bytes=… B/op=481±… pruned_bytes=…`.
- `W-SCALE-THRU` — `§SCALE-THRU B=… naive=… batch=… speedup=2.4×… saturate@B=…`.
- `W-SCALE-FORECAST` — emits the §6 table; each cell tagged (measured)/(forecast); names the limiting
  surface per tier and the crossover N/D.

## §8 Acceptance / DONE
- All §7 witnesses PASS with a `§` log line each (Watchdog: claim without a log line = not done).
- Fits have R² ≥ 0.95 or the row is reported as "shape uncertain — not extrapolated."
- `build/erp/scale_forecast.md` exists with the small/mid/large tiers + the two no-mitigation rows, every
  forecast cell tagged and traceable to a fit.
- A one-paragraph verdict: *largest ERP department the system carries before the first surface crosses
  budget, and which surface that is* — the honest ceiling, stated plainly (no hype).

## §9 Strategy on/off matrix (what the forecast must isolate)
Run the envelope with each mitigation independently toggled so the forecast SHOWS each one's contribution:
| Strategy | OFF behaviour (the lag it removes) | ON behaviour |
|---|---|---|
| **Checkpoint bootstrap** | S-BOOT = O(N) genesis, 25 s cliff at large N | ~9 ms flat (open-period fold only) |
| **Batch `commitGroup`** | S-THRU at naive 9,390 ops/s | 22,492 ops/s (2.4× → 2.4× more docs/day) |
| **Projection prune** | S-STORE = 481 B/op unbounded → mobile RAM / IDB wall | bytes bounded to ops-since-checkpoint |

> Spec lineage: extends `docs/FoldEngineConstraints.md` (§2 cliff, §4 genesis) + the measured logs
> `spike_writepath.log` (I-3/I-5), `sync_poc_smoke.log` (2.4×), `bootstrap_path.js` (25 s vs 9 ms). New
> driver: `scripts/poc_scale_forecast.js`. Output: `build/erp/scale_forecast.md` + `…_forecast.json`.
