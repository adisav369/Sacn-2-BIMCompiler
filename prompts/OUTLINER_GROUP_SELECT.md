# ⚠ DO NOT REMOVE — scope: Outliner category-header double-click selects the whole group, read the log after every run

**Why this exists:** user request, following up on `docs/img/modeller` Find-panel reference behavior
already ported twice this session — Find in the Viewer doesn't just frame a single result, selecting a
GROUP result selects+frames the WHOLE set at once. The user wants the same for the Outliner: clicking a
category/group header should select (and, via already-landed #711, automatically zoom-frame) every leaf
element under it — not just fold/unfold the branch, which is all header-click does today.

**Both prerequisites are merged on `origin/main`, build on top of them, do not re-implement either:**
- **#709** (`collapseAll()`, `bonsai_outliner.js`) — read its exact enumeration pattern below and MIRROR it,
  don't write a second, different tree-walk.
- **#711** (`zoomToSelection()`, `modeller.html`) — already wired into `setSelectionIds`/`window.Bonsai.selectMany`,
  so calling `selectMany(ids)` with a group's leaf ids ALREADY triggers the zoom-frame. Do not add a second
  zoom call — confirm this is true (it should be, per #711's own PR description) rather than assuming, then
  don't duplicate it.

## Exact ground truth (read first, don't re-derive)

**`collapseAll()`'s enumeration pattern, `bonsai_outliner.js` (current, merged shape) — mirror this exactly
for collecting a group's LEAVES instead of collecting collapsible keys:**
```js
collapseAll() {
  let added = 0;
  const set = k => { if (!this._collapsed[k]) { this._collapsed[k] = true; added++; } };
  this._categories.forEach(cat => {
    if (cat.tree) {
      set('tcat|' + cat.key);
      const mark = n => { if ((n.children || []).length) set('bn|' + n.id); (n.children || []).forEach(mark); };
      (this._lastRoots[cat.key] || []).forEach(mark);
    } else set(cat.key);
  });
  this._saveCollapsed();
  console.log(TAG + ' §OLCOLLAPSEALL newlyCollapsed=' + added + ' totalKeys=' + Object.keys(this._collapsed).length);
  this._paint();
},
```
`this._lastRoots[cat.key]` is the already-folded tree for a TREE category (reused across paints per
`§POLISH3`, do not re-fold). Each node `n` has `n.children` (array) and `n.kind` — a LEAF is `n.kind ===
'element'` (confirmed this session, `_renderNodes`: `const isLeaf = n.kind === 'element';`), and its
selectable id is `n.id` (a featureId, the same id space `window.Bonsai.selectMany` already consumes — the
existing `[data-fid]` leaf-row click handler, `bonsai_outliner.js` ~line 465, calls plain `window.Bonsai.select(fid)`
on a leaf click today — confirm the exact id shape `selectMany` expects matches `n.id` directly, don't guess).

**Existing header click handlers to NOT disturb (single-click stays exactly as today):**
```js
root.querySelectorAll('[data-grp]').forEach(d => d.onclick = () => { const k = d.getAttribute('data-grp'); this._collapsed[k] = !this._collapsed[k]; this._saveCollapsed(); this._paint(); });
```
(flat-category headers, `_wireFlat`) and the equivalent for tree-category headers (`tcat|` keys — find the
exact analogous `onclick` wiring, likely near `_wireTree` or similar, mirroring the flat-category shape
above but for `'tcat|' + cat.key`). **Add a `dblclick` listener alongside the existing `onclick`, do not
replace or modify the existing single-click handler at all** — same additive pattern `#bo-root`'s dblclick
already used (added alongside, not instead of, anything).

## Task

1. Add a `selectGroup(catKey)` method (or one for tree categories + one for flat categories if the two
   category shapes need different leaf-collection logic — mirror whatever `collapseAll()` already does to
   handle both shapes in one function, since it already solved this exact "handle both cat.tree and flat"
   branching). Collect every leaf's `n.id` under the category (tree category: walk `this._lastRoots[cat.key]`
   recursively, collect ids where `n.kind === 'element'`; flat category: the category's own row set — check
   how a flat category's members are structured, likely already enumerable via the same `_lastRoots`/fold
   the paint already built, not a fresh DB query).
2. Call `window.Bonsai.selectMany(ids)` with the collected leaf ids. Log something like `§OLGROUPSELECT
   cat=<key> n=<count>` (mirror the `§OLCOLLAPSEALL` log shape/naming convention) so the witness can assert
   on a real log line, not just DOM state.
3. Wire a `dblclick` handler on each category/group header (both tree-category `tcat|` headers and flat
   `data-grp` headers) that calls `selectGroup(catKey)` — additive alongside the existing single-click
   collapse-toggle handler, per the ground truth above. If a category is EMPTY (zero leaves, e.g. after
   filtering), no-op cleanly (mirror how other handlers in this file already guard empty cases).
4. **Do not touch `#bo-root`'s existing dblclick (`collapseAll`)** — that's the whole-tree action, this task
   is per-category. The two behaviors coexist: double-click the root collapses everything; double-click a
   CATEGORY header selects that category's leaves. Different targets, no conflict, but confirm in your
   report that both still work correctly side by side (a real regression check, not an assumption).

## Verification required before reporting done

- Real Puppeteer interaction (mirror the established `e2e_harness.js`/`t.open(key)` pattern) — open a
  resident with multiple categories each containing several elements (Duplex or SampleCastle), double-click
  a specific category header, and assert via hand-derived checks: (a) `window.Bonsai._selSet`/`selectedIds`
  now contains EXACTLY the leaf featureIds under that category (a real, counted, ID-level comparison against
  independently-computed expected ids — not just "selection count > 0"), (b) the `§OLGROUPSELECT` log line
  fired with the correct count, (c) the ALREADY-LANDED zoom-to-selection behavior fired too (check for the
  `§ZOOM-SEL`-equivalent log line #711 added — confirm the exact log tag it uses — proving the two features
  compose correctly without you having to re-wire zoom yourself), (d) single-clicking the SAME header still
  only toggles collapse (regression: the existing behavior is genuinely untouched), (e) double-clicking
  `#bo-root` still collapses everything (regression: the two dblclick handlers on different elements don't
  interfere with each other).
- Regression: run the SAME Outliner-touching witness sweep the collapse-all PR (#709) already ran and
  reported clean (grep `modeller/tests/` for the same set — `witness_ol_persist`, `witness_e2e_olsync`,
  `witness_e2e_olvirt`, `witness_e2e_oleye`, `witness_e2e_olfilter`, `witness_e2e_instpick`,
  `witness_e2e_instance_hide`, `witness_e2e_walk_ifcopen`, plus the new `witness_e2e_outliner_collapseall.js`
  itself) — confirm all still clean. Also re-run `witness_e2e_zoom_to_selection.js` (#711) to confirm this
  change didn't regress it (different file, `modeller.html` vs `bonsai_outliner.js`, but selection flow is
  the shared seam between them).
- Name the new witness `modeller/tests/witness_e2e_outliner_group_select.js`, `t.assert`/K-numbered
  convention, matching established rigor (hand-computed expected id sets, not eyeballed).
- Do NOT deploy or push — commit on a fresh worktree branch cut from `origin/main` (must include both #709
  and #711 — verify `git log --oneline -5 origin/main` shows both before starting), suggest branch name
  `feat/outliner-group-select`. Report back: exact diff, the exact header-wiring line(s) found and used,
  full witness output, regression sweep results including the cross-check against #711's zoom witness.
