---
description: Contribute to BIM OOTB — start with a rate template, a test building, a product catalog, a translation, or a format importer. Most contributions need no kernel knowledge and land in an afternoon.
---
# Contribute — start here

This project is **open source (MIT)** and built to be pushed forward by a crowd, not one
person. The fastest way in is **data and content** — most of it needs no knowledge of the
engine, can't break the core, and lands in an afternoon. Pick one, send a PR.

> **One rule:** *extract or compile, never invent.* Every value you add must trace to a
> real source — a published rate, a real building, a real product. No made-up numbers.

---

## Good first issues — pick one

| # | Contribution | What to do | Knowledge needed | Effort |
|---|---|---|---|---|
| 1 | **Add a rate template** | Copy an existing per-country rates JSON, swap in your jurisdiction's published unit rates (labour, material, equipment). We ship ~17 countries; the world has ~195. | Your local QS rates | hours |
| 2 | **Add a test building** | Drop any IFC into the viewer — it self-verifies through the Rosetta gates. Open a PR adding the file + the gate result. Each new building hardens the compiler. | None — just a file | minutes |
| 3 | **Add a product catalog** | Add `M_Product` rows for a region (real products, units of measure, prices) so BOM costing prices *actual* products, not generic IFC classes. | Product data | hours |
| 4 | **Add a locale** | We auto-detect 18 languages. Add the string file for one more. | Bilingual | hours |
| 5 | **Add a format importer** | One isolated importer per new mesh/BIM format. Well-bounded, can't touch the core. | JS + the format | 1–2 days |
| 6 | **Improve IFC export fidelity** | Carry property sets / openings / type objects through export (a known, scoped code gap). | IFC + JS | a few days |

Items 1–4 are the true low-hanging fruit: **no core code, fully parallel, each one makes
the tool more useful to the next contributor's country or format.**

---

## How to send a PR (the whole flow)

1. **Fork** the repo on GitHub and clone your fork.
2. **Branch:** `git checkout -b add-<thing>` (e.g. `add-rate-template-india`).
3. **Make the change** — one contribution per PR, kept small.
4. **Prove it:** if there's a witness for that area, run it and paste the `§` log line in
   your PR. For data (rates, buildings, catalogs), show it loading in the browser.
5. **Open the PR** with a one-line "what + source." Cite where the data came from.

That's it. No CLA, no ceremony. Small, sourced, witnessed.

---

## What we especially want

- **Rate templates** for any country not yet covered.
- **Real IFC buildings** from any authoring tool — the more tools, the stronger the proof.
- **Bugs, broken edge cases, "this didn't work for my file"** — open an issue with the file.
- **Translations** for your language.

## What to avoid

- **Invented data.** No synthetic rates, fabricated products, or guessed geometry.
- **Big, sprawling PRs.** One contribution at a time.
- **Touching `deploy/live/` or the production snapshot.**

---

For where the project is and where it's going, see
[`StrategicIndustryPositioning.md`](StrategicIndustryPositioning.md) (what's landed vs the
frontier) and [`PROJECT_CHRONOLOGY.md`](PROJECT_CHRONOLOGY.md) (how it got here).

*Copyright (c) 2025–2026 Redhuan D. Oon. MIT Licensed.*
