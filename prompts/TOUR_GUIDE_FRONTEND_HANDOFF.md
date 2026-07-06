# Tour Guide → Frontend (record-panel host) — single handoff

> One page. From the **Tour / ShowMe overlay** lane to the **idempiere record-panel** lane. Pass this as-is.
> Full detail (witness ledger, contract spec) lives in `prompts/IDEMPIERE_TOUR_GUIDE.md` + `docs/TourGuideHostContract.md` — you don't need them to act on this.

## Status — the Tour is DONE and BOUND to your host contract
You shipped the host contract on `bim-ootb/erp/idempiere.html` (`window.IdmpHost`, `data-ad-table/record` tagging,
inlined `__helpIdmpKeymap`, `#idmp-content` mount, the 2 scripts, `§SEAM-FROZEN`). I bound + verified, **no chrome edits**:
- `forked=0` — your deployed `help_overlay.js` + `help_idmp.js` are **byte-identical** to my source.
- **W-TOUR-BIND 11/11** + suite green (coach 21 · nextgate 11 · drift 24 · bind 11) — ShowMe drives the *real* `IdmpHost.focus→openWindow` for #80001; NeedHelp? lights a badge gated on your real `[data-ad-table]`.
- **The only thing you still own:** a live-browser screenshot of NeedHelp? lit on the rendered page (I have no puppeteer/Playwright; idempiere needs SQLite-WASM+server to paint).

## A. Keep the contract stable through UI finishing (4 invariants)
Cosmetic/layout/theme/reflow changes don't touch the Tour — it binds by key and reads `locate()` live, so badges follow elements anywhere. The Tour only breaks if one of these regresses:
1. `window.IdmpHost` keeps its 5 methods + semantics.
2. **The render path keeps tagging `data-ad-table/record` on rows/forms** — ⚠ the one real risk in a render-path rewrite; drop it and badges go *silent* (no error).
3. `#idmp-content` mount stays (or broadcast a rename — adapter is one line).
4. Keymap window names keep matching the AD menu (`Sales Order`, `Shipment (Customer)`, …) so `focus()` resolves.
- **Suggested guard:** add a `§`-assertion in your poc that `[data-ad-table]` count > 0 after render — so a UI rewrite that drops tagging fails *your* gate, not a user's eyes.

## B. ONE decision you own — what does the Install icon trigger?
You're adding an Install icon ("install for full editing + streaming + full AD metadata"). Per the docs, **"install" is multi-tier** — and the Tour must point honestly (non-invent), so I need to know which tier the icon kicks off:

| Benefit | Delivered by | Note |
|---|---|---|
| Browsable master data | **MigrateShowMe** (existing first-mile) | `MIGRATE_SHOWME_OVERLAY.md` — **master data ONLY**; excludes docs/txn/posting/Fact_Acct |
| `coverage:complete` (full posted history) | **S1 Fact_Acct import** (separate) | §13.6 cent-gated — *not* from MigrateShowMe, and never from POST |
| Full AD metadata | **shard streaming** (DataSource tier) | separate axis from data install |
| Full editing | **T3 write-loop** (`push=live`) | parked behind T3 |

**→ Tell me: does the icon launch MigrateShowMe (master-data only), or a unified full-install?** That decides the Tour's pointer copy. If it's MigrateShowMe, the Tour must say *"install to browse your masters"* — **not** "full editing + posted history" (that would over-promise a tier the install doesn't deliver).

## C. What I'll build once you answer (no overclaim, reuse the doc wordings)
A thin, keyed handoff (no fork, Tour stays read-only): when a user is **not installed**, the Tour points at your Install icon, **per coverage-state**, reusing the §13.7 note wordings verbatim — `absent`→"install local data first", `partial`→"run local install for the full posted history". The Tour **points**; MigrateShowMe / streaming / T3 **deliver**. Until you answer B, I won't wire it — pointing at a promise the tier can't keep breaks the non-invent rule the whole degrade contract protects.

## D. Not the Tour's job (so we don't double-own)
- Install walkthrough = **MigrateShowMe** lane. Streaming = **backend**. Editing = **T3**. The Tour never installs, streams, or writes.
- **Bonus, free:** as install/streaming brings more windows on board, more elements get tagged → the Tour lights badges on more records automatically, no Tour edit. Coverage flips `partial→complete` with **no panel change**.

## What I need back from you
1. **B answer** — Install icon → MigrateShowMe or unified full-install? (sets the pointer copy)
2. The live-render screenshot of NeedHelp? lit (closes my one open verification item).
3. A ping if any UI finishing changes invariants A1–A4 (esp. A2 tagging).
