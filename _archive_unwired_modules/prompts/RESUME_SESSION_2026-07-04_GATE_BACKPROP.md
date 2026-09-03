# RESUME — Conformity Gate + first W-SDG-BACKPROP slice (session close 2026-07-04)

```
# ⚠ DO NOT REMOVE
START HERE if picking up cold. This session shipped 4 small bim-ootb PRs in sequence, each spec-first
(prompts/*.md in bim-compiler), each node-witnessed before push. Read prompts/RESUME_GRAPH_MODELLER_
INTEGRATION.md §VISION-LOCK first if you haven't this week — it's still the north star.
```

## ✅ WHAT SHIPPED THIS SESSION (all bim-ootb, all node-witnessed, no regressions found)
1. **PR #644 MERGED** — `lane/grid-clear-state-leak-fix`: `#b-clear` was leaking `str_walker_outliner.js`'s
   module-local STR-walker state (`ready`/`__dwBuf`) across a Clear, so a later grid-drag on an unrelated
   building could re-walk a cleared building's still-resident skeleton and collide on `kernel_ops.id` (safe
   rollback, but a spurious error). Fixed with `STRWalkerOutliner.onClear()`. Spec:
   `prompts/GRID_CLEAR_STATE_LEAK_FIX.md`. Decision made: **(B) narrow reset** — only STR's own state, NOT a
   full audit of every walker module. **⚠ Still unaudited: DiscWalker roster / CrossEdges / bom-graph module
   state for the SAME class of leak** (see §OPEN below).
2. **PR #646 MERGED** — `lane/door-width-crush-gate`: `sdg_gate.js`'s `door-out` only tested a filling's
   CENTRE point, so a stretch shrinking a wall below its hosted door's own real width produced zero RED. New
   `door-crush` RED, axis-restricted to the axis the HOST's own extent actually changed (recon found a naive
   3-axis check would never fire — real door frames overhang the wall's thickness axis even pre-edit). Spec:
   `prompts/DOOR_WIDTH_CRUSH_GATE.md`.
3. **PR #647 MERGED** — `lane/abuts-realign-orange`: first slice of **W-SDG-BACKPROP**
   (`SPATIAL_DEPENDENCY_GRAPH.md` Phase 3, confirmed zero-hits-anywhere before this). New one-hop, delta-honest
   `abuts-realign` ORANGE — a real face-touch neighbour left behind by an edit gets flagged with a
   `proposedDelta`. REPORTS ONLY (no accept-button UI — matches how `clearance` ORANGE has shipped since day
   one with none). Spec: `prompts/SDG_BACKPROP_ABUTS_REALIGN.md`. **Two real-data findings surfaced DURING
   implementation** (not caught by the pre-write recon — see §OPEN below).
4. **PR #648 OPEN (not yet merged)** — `docs/modeller-guide-gate-teams`: the in-app User Guide (`#b-guide`
   panel) had ZERO mention of the RED/ORANGE gate at all, despite it shipping since 2026-06-29. Added a
   "Conformity Gate" section. Verified live (headless): panel renders it, 0 console errors. **Deploy is
   automatic** — `bim-ootb/.github/workflows/deploy-pages.yml` triggers on push to `main`, GH Pages source =
   `main` branch root (`gh api repos/red1oon/bim-ootb/pages` confirmed). Once #648 is merged, the live guide
   updates with no extra manual step (unlike bim-compiler's own docs site, which needs `scripts/
   safe_gh_deploy.sh` — that policy does NOT apply to this repo).

## 🔭 OPEN — the real review/refine candidates for the next session
1. **PR #648 needs a merge decision** (only one left open at session close).
2. **Accept/ignore UI for ORANGE suggestions — genuinely unbuilt, a real UX-scope call, not mine to invent.**
   Both `clearance` (since §GATE-1) and the new `abuts-realign` carry a suggestion but nothing lets the user
   click to apply it. Options worth putting in front of the user before building: per-suggestion button in the
   toast, a dedicated panel listing all open ORANGE items, or a keyboard shortcut while a flagged element is
   selected. `abuts-realign`'s `proposedDelta` is already computed and gradient-checked (witnessed) — applying
   it is "commit one more `GEOM_MOVE`," the hard part (detection) is done.
3. **Frame/consistency gap found in PR #647, NOT fixed, NOT audited further:** `cross_edges.js` derives
   `abuts` (and by extension `anchored`/`spans`/`datums`) from raw `element_transforms` directly; SampleHouse's
   2 real seeded abuts pairs are NOT within tolerance when re-measured on the ARC-seeded/`foldInsert` boxes the
   gate (and the live scene) actually use. This is a real discrepancy between two coordinate representations of
   the SAME elements. **Risk:** the EXISTING (already-shipped, already-witnessed) `related()` clash-exclusion
   for abuts pairs may be silently over- or under-excluding on real buildings — nobody has checked whether it
   still does the right thing given this gap. Worth a dedicated audit session: pick a building where clash
   exclusion actually matters (SampleCastle or Terminal, not SampleHouse's 2 thin data points), and directly
   compare `cross_edges.js` abuts numbers against the same pairs' `foldInsert`/scene-frame AABBs.
4. **DiscWalker / CrossEdges / bom-graph module-state audit** — carried over from item 1's decision (B):
   `#b-clear` only resets STR's state. If DiscWalker's roster or the bom-graph tab hold similar module-local
   "last building I saw" state, the SAME class of stale-state bug could exist there too. Nobody has checked.
5. **Next W-SDG-BACKPROP candidates**, ranked by `RESUME_GRAPH_MODELLER_INTEGRATION.md`'s own "THE USEFUL
   DIFF" list (items 1-2 now closed by this session's work; item 1's crush half was already there):
   - Cyclic/chained `abuts` realign — deliberately one-hop only right now (the doctrine's own constraint: no
     unique fixed point on a cycle, needs a real UX decision on how far a chain of suggestions should go before
     this is worth building).
   - Discrete swap-by-interface (roof type, add-a-storey) — a DIFFERENT, non-gradient engine per the doctrine's
     own "two propagation engines" split; nothing built yet.
   - §8E-3 routeChains MEP network render into the modeller canvas — engine proven (`witness_walkback_mep.js`
     8/8), zero render wiring, a pre-existing separate open item (not touched this session).
6. **Watchdog note:** `witness_sdg_gate_smoke.js`/`witness_sdg_cascade_smoke.js` need `playwright`, not
   installed in this dev environment. Not re-checked this whole session (3 PRs' worth) — if a future session
   has `playwright` available, run them once to confirm no drift accumulated across #644/#646/#647/#648.

## Full history / details
Each item above has its own spec doc with full recon + implementation notes:
`prompts/GRID_CLEAR_STATE_LEAK_FIX.md`, `prompts/DOOR_WIDTH_CRUSH_GATE.md`,
`prompts/SDG_BACKPROP_ABUTS_REALIGN.md`. Memory topic file: `project_arc_editable_substrate.md` slices 6-7.

## ✅ CONTINUED 2026-07-04 PM — §OPEN items worked to zero (except the 2 genuine forks)
- **#648 MERGED** (confirmed via `gh pr view`).
- **Item 6 (playwright watchdog)**: playwright IS available this session (cached npx install,
  `NODE_PATH=<npx cache>/node_modules node modeller/tests/witness_sdg_gate_smoke.js` /
  `_cascade_smoke.js`) — both re-ran GREEN 6/6, zero drift across #644/#646/#647/#648.
- **Item 4 (DiscWalker/CrossEdges/bom-graph module-state audit)**: all 3 confirmed real, all 3 FIXED —
  bim-ootb **PR #649** (`lane/clear-state-leak-round2`). `witness_grid_clear_leak_round2.js` 5/5 + zero
  regression on the full suite. Full detail: `prompts/GRID_CLEAR_STATE_LEAK_FIX_ROUND2.md`.
- **Item 3 (cross_edges.js frame-consistency gap) — FULLY FIXED, bim-ootb PR #650.** First audit pass this
  session (pure-node) reached the WRONG conclusion ("furniture-only, 0 disagreement on SampleCastle") because
  it never called `registerRealGeometry` (browser-only wiring) — it silently measured the plain raw-bbox+yaw
  fold, not the real production render. Re-measured via the actual headless-browser Open path before writing
  any fix: **SampleCastle showed 924/9817 (9.4%) real disagreement** — architectural elements, not an edge
  case; SampleHouse's 2 editable pairs were BOTH false positives (~215mm/~391mm actually apart, not touching).
  Fixed `_readBoxes()` to compute the TRUE world AABB from the real per-element vertex blob (rotate by yaw +
  translate by `center_xyz`, reusing `real_geometry.js`'s `buildGeometryIndex`) — falls back to the coarse
  bbox when unresolvable. New `witness_cross_edges_real_aabb.js` (puppeteer, the only way to see the real
  render) 9/9, 0 live-render disagreement post-fix (down from 924). Full regression green, incl. a
  `witness_sdg_gate_smoke.js` fix (its "clean move" leg picked an element that now correctly abuts the floor
  — real new behavior, not a bug). Full detail: `prompts/CROSS_EDGES_REAL_AABB_FIX.md`. **Lesson worth
  keeping: a "pure node" audit of anything with a browser-only wiring step is not ground truth — verify via
  the real Open flow before reporting a root cause as narrow/low-severity.**
- **Items 2 & 5 (accept/ignore UI, cyclic backprop/discrete-swap engine)**: still genuinely unbuilt, still
  real UX/architecture calls per their own original framing — untouched, correctly not mine to invent.
- Housekeeping: local `~/bim-ootb` fast-forwarded to `origin/main` (was 8 commits stale — CLAUDE.md's own
  session-startup rule would have caught this had it been followed at the top of this leg). Stale local
  `/tmp/wt-*` worktrees (all squash-merged, `ancestor_of_main=no` is expected/correct for squash — verified
  each PR's merged state via `gh pr view` before removing) removed to free disk; branches + PRs untouched.
  Left `/tmp/wt-iot-lod400` (`lane/iot-lod400-poc`) alone — belongs to a concurrent session, not mine to touch.

**Session-end status: every item is ✅ except the one genuine user-owned fork left.** Next session's only
real open: accept/ignore UI design for ORANGE suggestions (item 2) — a UX decision, not mine to pick.
