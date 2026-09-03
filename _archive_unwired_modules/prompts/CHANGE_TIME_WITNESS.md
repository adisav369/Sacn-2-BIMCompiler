# ⚠ DO NOT REMOVE — W-CHANGE-EFFORT (effort surface per change, both sides)
# Paste-to-start: `proceed with prompts/CHANGE_TIME_WITNESS.md`
# Scope: COUNT the developer effort surface for ONE identical change, twice — iDempiere OSGi vs fold here.
#   Three DETERMINISTIC, countable metrics (NOT wall-clock):
#     L = lines touched · C = handling checks (steps that can fail) · D = data/DB artifacts + migrations.
# NON-INVENT: every line/check/artifact is NAMED. C, D, build/restart are structural facts (exact now);
#   L needs a built artifact to confirm (marked ⧖).

## §1 · The one task
> Block completing a Sales Order when the customer's open balance + this order exceeds a custom credit
> ceiling field `CreditCeiling` on the Business Partner. Under → completes; over → blocked.
> (Not iDempiere's built-in credit-status, so the Java side genuinely needs a custom `ModelValidator`.)

## §2 · iDempiere / OSGi — effort surface
- **L ≈ 50 ⧖** across **F = 4 files**: `CreditCheckValidator.java` (~35) · registration (~8) · `MANIFEST.MF`
  Import-Package (~3) · migration SQL (~5)
- **C = 9 checks**: import-package resolves · validator registered · AD column synced · **build** · bundle
  resolves at runtime · **restart to 200** · log clean (no ClassNotFound) · acceptance under · acceptance over
- **D = 5 artifacts + 1 migration ×N**: AD_Element · AD_Column · AD_Field · AD_ModelValidator (4 AD rows) +
  1 physical-column DDL; carried by a **2Pack/SQL migration re-applied + verified on each target instance**
- **B = 1 build + 1 restart**

## §3 · Here / fold — effort surface
- **L ≈ 16 ⧖** across **F = 2 files**: AD slice column row (~2) · `AD_Val_Rule`/`.foldbundle` compare+veto (~12) ·
  pinned handler registration (~2)
- **C = 4 checks**: re-fold reads the slice · handler registered (default set stays pinned) · acceptance under ·
  acceptance over
- **D = 1 declarative edit + 0 migrations**: the AD slice **is** the schema (no dual write); a single **signed
  op-log append self-replays to every node** (`W-AD-OPLOG-DISTRIB`, `verifyChain` ok) — the op-log *is* the
  migration, for N nodes at once, not per-instance
- **B = 0 builds, 0 restarts** — their absence is the measured result

## §4 · The reading
| | L lines | C checks | D data/DB | B build+restart |
|---|---|---|---|---|
| iDempiere OSGi | ~50 ⧖ | **9** | **5 artifacts + 1 migration ×N** | **2** |
| Here (fold) | ~16 ⧖ | **4** | **1 edit + 0 migration** (op-log, N nodes at once) | **0** |
| ratio | ~3× ⧖ | ~2.25× | per-instance → once | **2 → 0** |

**Headline (deterministic, no stopwatch): checks 9→4, build+restart 2→0, and a per-instance migration ×N
collapses to one signed op-log append.** L (~3×) is enumerated, ⧖ to confirm by `wc -l` on a built artifact.
This is the §7 "imperative → recompile+redeploy" row and the §9 OSGi-tax, *counted*.

## §STATUS
- ⬜ Structural counts (C/D/B/F) banked + exact 2026-06-16; **L ⧖** pending a minimal artifact each side.
  Publish only "checks 9→4 · build+restart 2→0 · migration ×N→1 op" until L is `wc -l`-confirmed.
