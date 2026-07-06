# FIND → ERP : "Open existing Project Order Line" (idempotent push)

# ⚠ DO NOT REMOVE

## ★ STATUS — SLICE-1 ✅ LIVE (2026-06-24)
**Done + LIVE (bim-ootb PR #512 — auto-merged to main by the github-actions bot, then auto-deployed via
`deploy-pages.yml`; live `viewer/navigate_find.js` serves `_surfaceExistingOrder`, `main.js` loads
`navigate_find.js?v=43`, sw v720). To SEE it: open the Hospital model on the live site, hard-refresh so
the v720 service worker takes, then Find → select a type → the open ↗ link appears (record 990000).** Additive `_surfaceExistingOrder(set)` in `viewer/navigate_find.js`, called from the
existing selection hook `_updateSelCost`: when the active building already has a folded `C_Project`, it
surfaces the existing `#find-erp-open` "open ↗" deep-link (`…window=130&record=<C_Project_ID>`, the SAME
URL `_pushToErp` builds) so the user opens that order instead of re-creating it. **Purely additive** —
does NOT touch cost/push/navigate; guarded (try/catch + `.catch` + race guard `mySet===_lastSelSet`);
tooltips reset each selection. `› ERP` create path unchanged. **Witness W-FIND-OPENLINK 9/9** (node, real
`erp/ad_seed.db`, SQL+URL extracted verbatim: Hospital→990000 link; unfolded→null no-link). `main.js`
navigate_find ?v=42→43; `sw.js` v719→v720.
**Review eyeball:** open the **Hospital** model (building name = `Hospital` = C_Project 990000) → Find →
select a type → the **open ↗** link appears (record 990000) without pushing; confirm cost / › ERP / ▶
unaffected. **If approved:** `gh pr merge 512 --squash` (auto-merge was intentionally NOT enabled).
**FOLLOW-UPS (slice-2, below in this doc):** (a) per-LINE/guid precision (only show when the selected
class has a `C_ProjectLine`; deep-link to the line); (b) read the OPFS `bim_project_orders.db` in
`_ensureErpDb` so PRIOR-SESSION pushes are detected after reload (today: pre-baked + same-session only,
since `_ensureErpDb` caches the mutated in-memory db); (c) optional: relabel `› ERP` to a clearer
"already linked" state when an order exists. See §"The one real watch-out — PERSISTENCE".

**Scope:** In the viewer's **Find panel**, when a selected element's counterpart **already exists** as a
Project Order Line, show an **"Open ↗"** link to that Project Order *instead of* re-creating it. Clicking
the link deep-links to the existing Project Order. Goal = never create what was already created; surface
the existing link and jump to it.
**Standing rules:** Spec-First (write the witness claim before code). **Read the run log after EVERY run**
— exit code is not evidence. Whitebox §-log proof first; Playwright/headless only for wiring. Edit
shipping code in **`~/bim-ootb/viewer/`** (work in a `/tmp/wt-*` worktree off fresh `origin/main`, never
the shared checkout). Bump `sw.js CACHE_VERSION` + the `?v=` query on each deploy. Honour until ✅ DONE.

---

## The request (user, 2026-06-24, verbatim intent)
> In the Find panel, when an element that has its ERP Project Order Line created, it will show an
> OpenLink to that — so it need not create what was created before. Any element that happens to have a
> counterpart in a Project Order will show that link and it will just go to the Project Order.

## Is it easy? — YES, mostly. The rails already exist.
The identity, the existence-lookup, and the deep-link are ALL already in the codebase. This is mostly UI
wiring + one persistence check, not new infrastructure.

**Anchors (verified on `origin/main`, 2026-06-24 — re-read these first, do NOT assume):**
- `viewer/navigate_find.js`
  - `_pushToErp()` (~line 1106) = the **`› ERP`** push that folds the selection → a C_Project via
    `proj_fold.js` (`window.ProjFold`). This is where the new branch goes.
  - ~lines 1045–1052 **already query the existing line**:
    `SELECT C_Project_ID,PlannedAmt,CommittedAmt FROM C_Project WHERE Value=?` (building) then
    `SELECT pl.PlannedAmt, pl.c_projectphase_id FROM C_ProjectLine pl JOIN M_Product p
       ON pl.M_Product_ID=p.M_Product_ID WHERE pl.C_Project_ID=? AND p.Value=?` (pid, **ifcClass**).
    → The "does a line already exist for this selection?" lookup IS ALREADY WRITTEN (used today to show
    the bar's indicative cost). Reuse it as the existence test.
- **Matching key = (project `C_Project.Value` = building) × (line `M_Product.Value` = ifcClass).** So a
  Project Order Line corresponds to an **IFC CLASS within a building project**, not a single guid. The
  user said "any element that has a counterpart" → in practice: *the element's ifcClass already has a
  line in this building's project.* (If true per-guid linkage is wanted later, that needs a new
  element↔line table — out of scope for slice 1; class-level is the natural, shipped grain.)
- **Deep-link already exists:** `viewer/main.js` (S240/B3) posts `{type:'bim:focusRecord', guid, ifcClass}`
  to the embedding ERP, and the Find›ERP deep-link landed in PR#395 (`navigate_find?v=39`). Reuse one of
  these to "go to the Project Order" — confirm which path the Find panel currently uses to open ERP.

## Slice-1 spec (write witness claim FIRST, then build)
**Issue it proves:** Given a Find selection whose ifcClass ALREADY has a C_ProjectLine in the building's
C_Project, the Find panel shows **"Open in Project Order ↗"** (not "› ERP create"), and clicking it
deep-links to that existing Project Order; a selection with NO existing line still shows the create push.
1. **Detect** — on selection change, run the existing (project,ifcClass) lookup. If a line exists →
   `state = LINKED`; else `state = NEW`.
2. **UI** — `LINKED` → replace/augment the `› ERP` button with **`Open ↗`** (+ a small "already in
   Project Order" hint). `NEW` → unchanged push. (Multi-class selection: show per-class state, or a
   summary "N of M already linked" — decide in spec.)
3. **Open** — `Open ↗` fires the existing deep-link to the Project Order record (no new C_Project /
   C_ProjectLine is created — assert this).
4. **Idempotent push** — even on the `NEW` path, `_pushToErp` should be guarded so a double-push for an
   already-linked class is a no-op-that-opens, never a duplicate line.

## ⚠ The one real watch-out — PERSISTENCE (read before trusting the detection)
The "already created" detection is only as reliable as the store it reads. Known issue:
- `prompts/BIM_ERP_ROUNDTRIP_RETHINK.md` + MEMORY "ROUND-TRIP BUG": a pushed Project Order may not appear
  because `idempiere.html` persists the mutated db into the idb cache `ad_seed_v16` and the overlay
  `INSERT OR IGNORE` can skip a new push (BIM band PK ≥ 990000). The detection MUST read the SAME store
  the push writes to (OPFS/idb), or it will say "NEW" for something already pushed (and re-create it).
- Resolve: confirm where the C_Project/C_ProjectLine the Find panel pushes to actually lives at read time
  (viewer-side db vs the embedded ERP's db), and read from there. If the viewer can't see the ERP's
  committed project, the detection must round-trip a query (postMessage `bim:queryLine {building,ifcClass}`
  → ERP answers `{exists, C_Project_ID, line_id}`) — a small addition to the B3 message protocol.

## Witness (whitebox §-log first)
`viewer/tests/find_openlink_witness.js` (or extend `tests/poc_find_erp_link_live.js`): seed a db where
building `X` has a C_Project with a C_ProjectLine for ifcClass `IfcWall` and none for `IfcDoor` →
assert the detector returns `LINKED` for an IfcWall selection (with the right C_Project_ID/line) and
`NEW` for IfcDoor; assert the `Open ↗` path creates ZERO new rows (idempotent), and the `NEW` path still
pushes. Then a headless wiring smoke that the button swaps and the deep-link message fires.

## Related / read-first
`prompts/BIM_EMBED_WINDOW_SESSION.md` (B3 cross-highlight + the postMessage protocol), `viewer/proj_fold.js`
(the fold engine — where lines are created), `viewer/navigate_find.js` (Find panel + push + the existing
lookup), `prompts/BIM_ERP_ROUNDTRIP_RETHINK.md` (the persistence invariant). MEMORY entries: "BIM-in-window
embed", round-trip bug, Spatial BIM→ERP.

## Verdict on effort
**Small–medium, bounded.** Identity (building+ifcClass) ✓ exists, existence-lookup ✓ exists, deep-link ✓
exists. New = (a) UI state swap Create→Open, (b) reuse the deep-link, (c) make detection read the
persisted store (the only place that can bite). One focused session if persistence is already reliable;
two if the round-trip query needs adding.
