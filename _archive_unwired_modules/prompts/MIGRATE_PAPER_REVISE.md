# ⚠ DO NOT REMOVE
**Scope:** Revise the *presentation* of `docs/MigrateComparisonPaper.md` (the "Migrate & Compare (ERP)" paper).
This is a mkdocs page published to https://red1oon.github.io/BIMCompiler/MigrateComparisonPaper/.
**Working rule for this prompt: PROPOSE BEFORE EDITING.** Do not touch the file until the user has approved a
written plan. State the proposed change (new structure / titles / wording) in chat, get an explicit "go", *then* edit.
Read the build log after every `mkdocs` run — exit code is not evidence.

---

## Why this prompt exists
A prior session reorganised this paper but churned through ~10 corrections because it **edited first and proposed
after**. The user wants the opposite: read the piece, form an opinion, **propose**, get sign-off, then make the change.
Subjective presentation work (titles, fold structure, tone) is decided by the user, not assumed.

## The standing agreement (how to work this file)
1. **Propose first.** For any change to structure, fold titles, or wording: describe it in chat (a short bullet plan or
   a before/after of the titles). Wait for approval. Only then edit. One proposal → one approval → one edit batch.
2. **Titles: short, professional, sourced.** Fold `<summary>` titles are *short noun phrases* that match the term they
   come from. Example settled this session: the scratch-tables fold is titled **"Report scratch tables"** because that
   is the exact label of the kill-points table row that links to it (`[below](#temp-tables)`). No long descriptive
   summaries, no `<span class="hint">…</span>` subtitles, no conversational/ceremony register ("the honest headline
   first", "a newbie can stop after step 1", "the gist", "made concrete", emoji-arrows). Plain, measured prose.
3. **One coherent fold tree.** A section's detail belongs in *one* nested fold tree, not scattered. Detail folds live in
   the lower fold cluster — never floating in the high-level prose region. Merge redundant folds (this session merged
   two duplicate `completeIt()` folds into one with a nested "Full listing").
4. **Preserve anchors + footnotes.** When moving/retitling, keep `id=`/`{#…}` anchors and `[^…]` footnote refs intact.
   Live cross-refs in this file: `#temp-tables` (kill-table row + capability table + the T_Aging fold all link to it),
   `#gap-analysis`, `#gap-in-code`, `#realistic-conversion-estimate-loc`, `#no-server`, `#speed`, `#dr-tco`,
   `#erp-as-git`, `#temp-tables`.
5. **Verify before claiming done.** After an edit: `<details>` open==close, `<div>` open==close, `mkdocs serve` returns
   HTTP 200, and **no warning naming `MigrateComparison`** in the serve log. Only then report.

## Current structure (as deployed this session)
Top (visible prose): banner · at-a-glance cards · Thesis · "What a fold is" (chess) · **The kill points** (+ 10-row
killtable) · How it differs (architecture) · But where's the server? (fan-out + git analogy).
Lower **fold cluster** (all `details.fold`):
- **Vitals** · **Disaster recovery & TCO** · **Method & honesty** · **Report scratch tables** · **Gap Analysis** ·
  **Roadmap** · **Further Reading** · **Status**
- **Gap Analysis** is one numbered tree: `1 · Coverage by capability` · `2 · A proven fold in code — MOrder.completeIt()`
  (nested "Full listing, shipped primitives & code-quality") · `3 · An unbuilt fold — the T_Aging aging report` ·
  `4 · Code size for full parity (LOC estimate)` (nested "Full breakdown by iDempiere module") · `5 · Open caveats —
  claims without a measured benchmark`.

## Mechanics
- **Local preview:** `mkdocs serve -a 127.0.0.1:8000` → open `/MigrateComparisonPaper/`. Show the user localhost for a
  once-over before deploying.
- **Deploy:** `mkdocs gh-deploy --message "<msg>"` from the working branch → red1oon.github.io/BIMCompiler/.
- **Tag-balance check:** `grep -o '<details' … | wc -l` vs `</details>`, same for `<div>`.

## Open follow-ups (propose, don't assume)
- The source edit to `docs/MigrateComparisonPaper.md` was deployed but may still be **uncommitted** on
  `feat/erp-substrate-phase012` — confirm git state at session start; commit if the user wants.
- Any further title/tone passes the user requests — same loop: propose → approve → edit → verify → (deploy on "go").
