# ⚠ DO NOT REMOVE — SESSION: "Seam Identity Audit — one thing, named two ways"
# Model tier: **OPUS** (well-scoped but nontrivial, multi-file, sustained reasoning). NOT Fable —
#   Fable is mechanical execution; this needs judgement about whether two names mean the same thing.
#   See memory feedback_model_allocation_mastermind_vs_execution.md for the tiers.
# Scope: find every place where TWO OR MORE call sites independently construct the SAME identity
#   (a cache key, a url, a guid, a storey id, a phase token, a localStorage key, an IDB key) and
#   nothing enforces that they agree. Report + fix the ones with real blast radius.
# PRIME RULE: EXTRACT ONLY. Every finding cites file:line and the two divergent constructions.
#   No speculative smells, no lint, no style opinions. Read the log after every run.
# Witness-led: each fix ships a PURE test that FAILS on the old code (the divergence assertion).

## §WHY THIS SESSION EXISTS — the bug that named the pattern (2026-07-30)
The Viewer cached each building in IndexedDB **keyed on the raw url string**. Two entry points built
two different strings for the same 251MB file:

| entry point | file:line | string |
|---|---|---|
| Landing hub card | `index.html:489` | `<prodBase>buildings/Hospital_extracted.db` |
| ERP red pill | `erp/idempiere.html:4716` | `../buildings/Hospital_extracted.db` |

Result: opening from the landing cached it; red-pilling across missed, 404'd, re-downloaded the full
251MB, and wrote a SECOND copy under the other key. ~2 minutes of user wait, every single click, half
a gigabyte per building. **Live for months. Zero tests failed. Nothing in the log said "wrong key".**

The tell was there and unread: the landing's green "cached" badge matched on the **filename stem**
(`index.html:528`) while the fetch matched on the **whole url** (`scene.js` `cachedFetch`). Two
different notions of "is this building cached" in one repo, 40 lines apart in behaviour. A third
call site (`A._checkCache`, feeding `streaming.js`'s size probe) had a fourth. Fixed 2026-07-30 —
see `prompts/HISTORY_PERSIST_RECALL.md` §VERIFY-FIRST ITEM 1 for the full post-mortem.

**The generalisable defect:** an identity is CONSTRUCTED at N call sites instead of DERIVED from one
pure function. Nothing fails loudly; you just silently do the work twice. This audit hunts that shape.

## §THE LENS (per memory feedback_audit_landmines_not_lint.md — landmines, not lint)
Weight every finding by **blast radius × silence**. This bug scored high on both: whole-building
re-download (radius), and a log line that looked NORMAL (`§CACHE_MISS_READ`) rather than wrong
(silence). Do NOT report doc drift, naming style, dead code, or anything that announces itself the
first time a user hits it. A latent divergence that costs bandwidth/time forever outranks a hundred
cosmetic issues.

Rank each finding: `radius (who/what breaks, at what scale) × silence (why no log/test catches it)`.

## §METHOD — mechanical, then judged
1. **Enumerate identity constructions.** For each identity family below, grep every construction site
   across `viewer/`, `erp/`, `modeller/`, `landing`/`index.html`, and the tests:
   - IndexedDB / cache keys (`objectStore(...).put/get`, `idbPut`, `idbGet`)
   - `localStorage`/`sessionStorage` key strings (`pwa_last_db`, `bim.hist.tree.*`, `bim.universalHist.*`)
   - building/db URLs (`?db=`, `_extracted.db`, `buildings/`, `PROD_BASE`, `_prodBase`, `gh` overrides)
   - element/room identity (guid ↔ mesh id ↔ instance row ↔ `spatial_structure` id)
   - schedule/phase tokens (`Description` splits, phase names, gantt band keys)
   - `BroadcastChannel` channel names + message `type` strings across viewer/erp/landing
2. **For each family, ask the one question:** is there ONE pure function that returns this identity,
   and does every site call it? If N sites each build it inline → **FINDING**.
3. **Prove divergence before claiming it.** Construct the identity from two real call sites with the
   same real input and show the strings differ. A finding without that comparison is a guess — drop it.
4. **Judge blast radius.** Divergence that is harmless (two names that never meet) is NOT a finding.
   The bug above mattered because both paths hit the same store. Say why the two paths meet.

## §DELIVERABLE
- Append findings to THIS file as a dated section, ranked. Per finding: the identity, the ≥2
  divergent construction sites (file:line + actual strings), why the paths meet, radius × silence,
  and the one-pure-function fix.
- Fix the top findings that clear the landmine bar. Each fix = one pure `*.js` decision function +
  a witness that FAILS on the old inline construction (copy the shape of
  `viewer/tests/witness_db_cache_key.js`, which asserts `keyFromRouteA === keyFromRouteB`).
- Explicitly list what you checked and found CLEAN — a clean family is a real result and stops the
  next session re-walking it.

## §GUARDRAILS
- **Do not fold identities that must stay apart.** The cache-key fix had to keep
  `deploy/dev/buildings/Terminal_extracted.db` and `deploy/buildings/Terminal_extracted.db` distinct —
  same filename, different bytes. Every folding rule needs its matching NOT-folded guard case in the
  witness, or you have traded a re-download for wrong geometry.
- One bounded task. If the audit surfaces more than ~5 real findings, report them all and fix the top
  ones; do not let it become an open-ended refactor.
- No new memory files (that's a Sonnet synthesis pass) — findings go in this file.
