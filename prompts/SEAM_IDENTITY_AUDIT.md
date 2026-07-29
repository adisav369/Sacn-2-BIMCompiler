# ⚠ DO NOT REMOVE — SESSION: "Seam Identity Audit — one thing, named two ways"
# Model tier: **OPUS** (well-scoped but nontrivial, multi-file, sustained reasoning). NOT Fable —
#   Fable is mechanical execution; this needs judgement about whether two names mean the same thing.
#   See memory feedback_model_allocation_mastermind_vs_execution.md for the tiers.
# Scope: find every place where TWO OR MORE call sites independently construct the SAME identity
#   (a cache key, a url, a guid, a storey id, a phase token, a localStorage key, an IDB key) and
#   nothing enforces that they agree. **PASS 1 = LIST ONLY, FIX NOTHING** (see the ⛔ block below) —
#   comb to exhaustion, chase each to root cause, cluster by shared cause. Pass 2 does the refactor.
# PRIME RULE: EXTRACT ONLY. Every finding cites file:line and the two divergent constructions.
#   No speculative smells, no lint, no style opinions. Read the log after every run.
# Witness-led — but NOT in this pass: pass 2's fixes each ship a PURE test that FAILS on the old code
#   (the divergence assertion). Pass 1 writes no tests either.

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

## ⛔ PASS 1 IS LIST-ONLY — DO NOT FIX ANYTHING (user directive 2026-07-30)
**"make it comb deeper and chase issues till listed, not act upon yet, until we can review causes in one
more pass for refactoring opportunities."**

This session **writes zero production code.** No fixes, no pure-function extractions, no witnesses, no
PR. Touch only this file. The reason is deliberate: fixing findings one at a time hides the fact that
several of them are the SAME root cause and want ONE refactor, not five patches. Today's cache-key bug
had four call sites with three different notions of "is this cached" — patched individually that is
three fixes; seen together it is one missing pure function. **Pass 2 (a separate session, after the
user reviews this list) decides the refactor.** Anyone who starts editing code in pass 1 destroys the
only thing pass 1 is for.

If a finding looks trivially fixable — still don't. Write it down and keep combing.

## §DELIVERABLE — an exhaustive ranked list, chased to root cause
Append to THIS file as a dated section. **Comb to exhaustion — there is no finding cap.** Do not stop
at the interesting ones; the value is a complete list the refactor pass can pattern-match over.

Per finding:
- **The identity** — what one thing is being named.
- **Every construction site** (not just two) — `file:line` + the ACTUAL string/value each produces for
  the same real input. Show them side by side.
- **Where the paths meet** — the shared store/comparison that makes the divergence bite. If they never
  meet, say so and mark it `HARMLESS` rather than dropping it (pass 2 may still want it unified).
- **Radius × silence** — who breaks, at what scale, and why no log line or test catches it today.
- **Root cause, one line** — e.g. "no pure function owns this key", "two modules each re-derive the
  filename convention", "the writer and the reader were written in different sessions".
- **Suspected shared cause** — cross-reference other findings you think share a root. This is the raw
  material for pass 2; guess freely here, it costs nothing and is the whole point.

Then two summary sections:
- **§CLEAN** — every identity family you checked and found genuinely single-sourced. A clean family is a
  real result; it stops the next session re-walking it.
- **§CLUSTERS** — your grouping of the findings by shared root cause, with a one-line note on the
  refactor each cluster is pointing at. Do not design the refactor; just name what it would touch.

## §GUARDRAILS
- **Note, don't apply, the folding guard.** Where you propose that two names SHOULD fold, also name the
  case that must NOT fold. Today's fix had to keep `deploy/dev/buildings/Terminal_extracted.db` and
  `deploy/buildings/Terminal_extracted.db` distinct — same filename, different bytes. A folding rule
  without its NOT-folded counter-case trades a re-download for wrong geometry. Record the counter-case
  alongside the finding so pass 2 inherits it.
- **Prove divergence, don't assume it.** Two names that differ only in a variable you didn't resolve is
  not a finding. Resolve it or mark it `UNVERIFIED` explicitly.
- No new memory files (that's a Sonnet synthesis pass) — findings go in this file.
