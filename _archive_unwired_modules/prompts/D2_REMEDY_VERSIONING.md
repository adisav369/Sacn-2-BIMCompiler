# ⚠ DO NOT REMOVE — Scope & Standing Rules (honour until ✅ DONE)

**Scope:** the D2 remedy — event/schema versioning on the SIGNED op-log. Measured mandatory by
`scripts/spike_d2_versioning.js` (§SPIKE-D2) after Ludwikowski's *"Event Sourcing — what could possibly go
wrong?"*. **Log Mandate · Non-invent · Deterministic.** ERP-engine source = `build/erp/`.

## §SPEC (the forced strategy — a signed log permits only ONE migration path)

`op_hash = SHA-256(prev_hash | canonical(op))` and canonical includes `JSON.stringify(parameters)`. Therefore:
- **You may never rewrite a stored op to migrate it** (breaks the chain + signature → `payload altered`).
- The only legal migration is **upcast-on-read**: transform an old-shape op to current IN MEMORY, just before
  the reducer; the stored op is untouched, the chain stays valid.

**Module `build/erp/op_upcaster.js`:**
- `stamp(opType, params)` → adds `params._sv = currentOf(opType)`. `_sv` lives inside `parameters`, so it is
  hashed → the declared version is a **signed, tamper-evident fact**.
- `commitVersioned(kernel, db, opType, params, …)` — the write seam: stamp then `kernel.commitOp` (no fork).
- `register(opType, fromVersion, fn)` — a single-step upcaster `N → N+1`. Forward-only (mirrors `migration/*.sql`).
- `upcast(op)` / `upcastAll(ops)` — chain registered upcasters from the op's version up to current, on read.
- `supports(op)` — false if the op is from a FUTURE version or a rung is missing → `upcast` throws
  `D2_UNSUPPORTED` (refuse loudly; ask the client to update — never silently misapply).
- legacy ops with no `_sv` = version 1.

## §WITNESS CLAIMS — `scripts/poc_d2_versioning.js` (PASS 7/7, real kernel + real signer)
- **W-D2-FREEZE** — v1 ops upcast→current fold to their ORIGINAL effect (maxDiff=0). History frozen.
- **W-D2-CONVERGE** — two clients on a MIXED v1+v2 log fold identically (maxDiff=0). Offline migration converges.
- **W-D2-REFUSE** — a future-version op throws `D2_UNSUPPORTED`, never folds silently.
- **W-D2-SIGNED** — after folding upcasted ops the signed chain still `verifyChain` ok (stored op untouched).
- **W-D2-TAMPER** — forging a stored op's `_sv` breaks the chain (version is signed).

## §STATUS & NEXT
- ✅ Engine core built + witnessed (the 3 register test-plan rows are green). D2 → 🟡 PARTIAL.
- Remaining (field/operational, lower-pri): (1) wire `commitVersioned` as the app's DEFAULT write seam (today
  opt-in); (2) manifest-version distribution telemetry (a System Monitor widget candidate); (3) a recurring
  chaos drill that migrates a snapshot of REAL field logs (not a fixture). **Do-not-ship rule relaxes:** a
  breaking change is shippable IFF it ships with its registered upcaster + a version bump.
