# ⚠ DO NOT REMOVE — RESUME CARD: "S4 shopfloor + 360 optics + kanban/pivot" (act-from-here for a FRESH session)
# Parent lane: prompts/TM_4D5D_VARIANCE_LANE.md (§S4). Build spec: prompts/TM_S4_SHOPFLOOR_BUILD.md.
# PRIME RULE: EXTRACT/COMPILE ONLY. Model = iDempiere/libero CONVENTION (never invent a table/widget). The ONLY
# generated layer = documented deterministic VALUES ('generated' marker, fixed seed). Whitebox §-log first (read
# the log; exit code ≠ evidence). Money via site/bigdecimal.js. Verify against ~/idempiere-dev-setup/idempiere — never assume.

## §WHERE EVERYTHING IS (all pushed — verify with `git ls-remote --heads origin <b>`)
- **bim-ootb `feat/tm-360-optics`** (off `feat/tm-variance-s1`) — the 360 viewer work (cost-std + identity thread).
  Worktree may still exist at /tmp/wt-360 (recreate: `git worktree add -b X /tmp/wt-Y origin/feat/tm-360-optics`).
- **bim-ootb `feat/mfg-shopfloor-seed`** (off main) — the E1 seed data (16 PP_Order + 64 PP_Order_Cost on Hospital).
  ⚠ ~/bim-ootb is currently CHECKED OUT on this branch (main is held by worktree /tmp/wt-landing). Clean tree.
- **bim-compiler `feat/erp-substrate-phase012`** — `build/erp/tests/gen_mfg_shopfloor.js` + `bake_mfg_shopfloor.js`
  (E0 generator + E1 bake; re-bake is idempotent `node build/erp/tests/bake_mfg_shopfloor.js --write`).
- ⚠ **The S1/S2/S3 SUBSTRATE the 360 work builds on is on `feat/tm-variance-s1` (PR #447, OPEN/unmerged)** — NOT main.
  Build the rest THERE (or its merge), never off main (main lacks the twin fold / drawer / broadcast).

## §DONE (witnessed — do NOT redo)
- **S4 E0** generator — W-SHOP E0 13/13 (Σ buckets == PlannedAmt to the rupiah · perElementSetup==setup/N · byte-identical).
- **S4 E1** persist — W-SHOP-PERSIST 10/10. Used the EXTRACTED libero schema (`~/idempiere-dev-setup/.../org/eevolution/model`
  X_PP_Order_Cost = per-order×M_CostElement, X_PP_Order_Node = SetupTime/Duration). 9 crews→S_Resource Work-Centers
  (Concrete Gang/Steel Erector/…), PP_Order ETO/MTO-tagged, all C_Project_ID=990000. ON DISK in the seed branch.
- **360-2a Cost standard on info panel** — `showInfoPanel(guid)` folds twin Planned→Committed→Δ for ANY pick (un-priced
  building → cost box hides). navigate_find.js. Regression S2 17/17 intact.
- **360-2b Identity thread Find→TM** — new `window.tmJumpToElement(guid)` (time_machine.js) freezes TM on the element's
  op.end_ts, couples the ⚖ drawer; renderAtTime broadcasts (→ ERP). "⏱ View at this moment" now element-level (guid)
  w/ phase fallback. Regression S1 13/13 · S3 11/11 intact. Live freeze VISUAL deferred (Hospital geom in OPFS).
- **RETIRED:** the custom `mfg_kanban.html/.js` fork (wrong — grouped by phase w/ bespoke cost bars). Gone. Seed data kept.

## §DECISIONS LOCKED (user, this session)
1. **Kanban = MAINTAINED as-is** (docstatus fold + drag=SET_STATUS). MO appears in it because PP_Order has DocStatus —
   the ONLY wiring is registering PP_Order as a kanban-folded table (the **KanbanBoard AD flag** ninja_model already
   models = config/data, NOT code). No MO special-casing anywhere.
2. **Pivot = ADD as an additional view** (there's space). General engine: any table × field × measure (count/cost/qty),
   read-only. NOT manufacturing-specific. Mirror `erp/kanban_lens.html` chrome (convention-first, no Odoo-purple).
3. **Drill = USE Zoom-Across** on kanban cards + pivot cells (the existing FK gesture) — never duplicate it.
4. Both kanban & pivot are LENSES → reachable from the `⋯` pill family, NEVER on the native iDempiere chrome
   (FUNDAMENTAL LAW: iDempiere surface stays indistinguishable). Users are ERP professionals — appeal + convention matter.

## §NEXT — ALL 5 ✅ DONE/PUSHED 2026-06-21 (branch `feat/tm-360-optics`, off `feat/tm-variance-s1`; commits below; NOT merged)
1. ✅ **ERP-side scrub reaction** — `adc99ca` (W-CONNECT-ERP-SCRUB 19/19, `tests/test_connect_erp_scrub.js`). erp/idempiere.html
   loads `../viewer/connect_scene.js`; `_connectEnable()` (idempotent) registers surface 'erp' + enables on FIRST Zoom-Across
   launch (OPT-IN) → subscribes selection+timeline. selection→`_connectLightIfc` (C_ProjectLine JOIN M_Product.value=ifcClass →
   flash `.idmp-connect-lit` row pulse + status "↔ Live: X"); timeline→status "⏱ TM <phase> (NN%)". §CONNECT_ERP logs inbound.
2. ✅ **PP_Order in standard kanban** — `66b95db`. Added `pp_order` to TMAP + tables list in erp/kanban_lens.html (docstatus
   fold + SET_STATUS, ZERO special-casing). §KANBAN-PPORDER fold SQL → 2 MO cards in DR (50000/50001) verified vs ad_seed.db.
3. ✅ **Generalized kanban group-by** — `310ffdd` (W-KANBAN-GROUPBY 20/20, `tests/test_kanban_groupby.js`). "Group by" <select>
   in the embedded kanban pill (idempiere.html); options = AD fields with usable cardinality (≥2,<rows) from `_displayFields`+
   `_distinctCount`, no per-model code. DocStatus first + ✦ (draggable); other field → heat-map. dispatch/onResult extracted to
   `_kbDispatch`/`_kbOnResult`/`_kbMountBoard` (shared write path on re-mount). §KANBAN-GROUPBY field/groups/draggable log.
4. ✅ **Pivot view** — `ddb8ae5` (W-PIVOT-LENS 19/19, `tests/test_pivot_lens.js`). NEW `erp/pivot_lens.html` clones kanban_lens
   chrome: table×row×col×measure (count|GrandTotal|Qty) cross-tab; axis selectors from `catCols` cardinality filter; total
   row+col; cell click=§PIVOT-DRILL log + record-IDs tooltip (Zoom-Across entry per decision #3). Read-only.
5. ✅ **E2b TM shopfloor stacked S-curve** — `638a605` (W-SHOP-SCURVE 19/19, `tests/test_shop_scurve.js`). viewer/time_machine.js
   `_loadShopfloor()` (mirrors `_loadTwin`) folds PP_Order+PP_Order_Cost (16 orders, Σ=64,719,479==C_Project PlannedAmt to the
   rupiah). drawSCurve stacks Material/Labor/Burden/Overhead; each order batch-lights (all elements accrue at DateFinishSchedule,
   monotonic, ends 100%); falls back to op-count curve when no shopfloor. sw v676→**v677** (time_machine.js precached).
   §SCURVE_COMPUTE/§TM_SHOPFLOOR_LOADED. ⚠ Live VISUAL deferred (Hospital geom in OPFS) — whitebox §-log IS the proof.

## §MERGED + LIVE 2026-06-21 (PR #462, squash `88c43a5` on main; sw v684)
The whole stack is LANDED + LIVE on https://red1oon.github.io/bim-ootb/. `feat/tm-360-optics` was a strict superset of
`feat/tm-variance-s1`, so #462 carried S1/S2/S3 + all 5 new features in ONE merge. #447 CLOSED as superseded. Both
feature branches DELETED. Synced main first (was 15 behind; only sw.js conflicted → took v684 > both). All 7 witnesses
re-ran green post-sync. LIVE-verified via curl: sw.js=v684 (minified) · erp/pivot_lens.html HTTP 200 · connect_scene.js
loaded by ERP · §CONNECT-ERP-SCRUB + §KANBAN-GROUPBY markers present · pp_order in TMAP · _loadShopfloor in time_machine.js.
**Docs published 2026-06-21** (bim-compiler `lane/benchmark-clash-resolution` `21747bfd`, pushed; `scripts/safe_gh_deploy.sh`
guard PASS, blessed benign `.nojekyll`, gh-pages `6441f5e6`): `docs/MigrateComparisonPaper.md` Roadmap item 3 extended +
currency-pass→2026-06-21, `docs/FeatureComparison.md` 4D/5D bullets + "Budget-vs-actual variance" row (footnoted: unification,
NOT a CPM engine). PROGRESS.md trimmed to a one-line pointer. LIVE-verified on https://red1oon.github.io/BIMCompiler/.

## §SHIPPED 2026-06-21 (PR #468, squash `a600a77` on main; erp sw v738 / viewer sw v690) — PP_Order → BIM Viewer TimeMachine
- User redirect this session: complete the 360 loop FROM the manufacturing/project-order side. Spec
  `prompts/PP_ORDER_ZOOM_TM_SPEC.md`. **W-PPZOOM-TM 7/7** (`viewer/tests/test_pp_zoom_tm.js`, whitebox; Hospital
  geom in OPFS → live visual deferred). LIVE-verified: erp sw v738, `timemachine` dest + `tmJumpToOrder` in
  served code, served `ad_seed.db` = 18 PP_Order / 64 PP_Order_Cost.
- ERP `erp/idempiere.html`: `_zoomScope()` resolves a BIM-band PP_Order → `{bld, tm:{order}}` by identity; new
  `ZoomAcross.register({id:'timemachine', label:'BIM TimeMachine'})` — the SAME red pill `#pill-zoomacross`
  (2+ targets → existing chooser). Launch → `viewer ?tm=1&pporder=<id>`.
- TM `viewer/time_machine.js`: `window.tmJumpToOrder(id)` — phase token EXTRACTED from `PP_Order.Description`,
  cursor mode=phase (phase on the scene axis) else mode=projected (order finish → `[_projectStart,_projectEnd]`,
  honest label); opens ⚖ drawer + shopfloor S-curve; `§TM_ORDER_JUMP`. `?pporder` deep-link in the `?tm` init.
- SEED: re-baked the S4 E1 shopfloor onto main's ad_seed.db (idempotent, deterministic) → **main now carries the
  18 PP_Order**. ⚠ `feat/mfg-shopfloor-seed`'s seed commit is now SUPERSEDED on main (only its 3 bench-suite
  commits remain unmerged there — separate scope).

## §NEXT-NEXT
- ✅ **E3** PP schedule-vs-actual → projected-from-cost date variance — DONE/LIVE 2026-06-21 (PR #469, viewer sw v691;
  W-SHOP-DATES 9/9). `_computeScheduleProjection` in time_machine.js; ⚖ drawer header "Projected finish (+N d)
  projected from cost" + per-phase slip chips; §SCHED_PROJECT. No seed/actual-date invented.
- ✅ **Pivot lens on the `⋯` rail** — DONE/LIVE 2026-06-21 (PR #470, erp sw v739 / viewer v692; W-PIVOT-PILL 13/13).
  'pivot' pill (Lucide 'table' glyph, verbatim panels.js↔icons.js) → openPivotFor in-page overlay of
  pivot_lens.html?db=ad_seed.db; §PIVOT-PILL.
- ✅ **S5(B) EARN-THE-ACTUAL → cost EVM** — DONE 2026-06-21 (PR #471, viewer sw v693; W-PC-EVM 14/14). User chose
  (b) over (a) (generate-issues declined = circular + invention). Spec `prompts/PC_EVM_SPEC.md`. `_computeEVM`
  folds EV/AC/CPI/CV + forecast EAC/VAC from the existing twin, cursor-driven, in the ⚖ drawer; §EVM_FOLD.
  At completion EAC==87,372,995==real CommittedAmt to the rupiah (forecast reconstructs the actual); CPI 0.741.
  HONESTY: no independent SPI on this twin (schedule has no real actual; slip is cost-projected ⇒ SPI≡CPI) →
  cost EVM only, schedule stays the E3 projected finish. ⚠ TRUE earn-the-actual (option a, real C_ProjectIssue)
  needs the OPFS round-trip (real docs) — same gate as the round-trip lane; NOT fabricated here.

## §USER SCENARIO (the 360 baseline — the thing to make work end-to-end)
ERP Project-Order LINE selected → red-pill Zoom-Across → Viewer Find ghost-highlights the item + IFC info pops with
COST (std, done) + zoom-to → user closes Find, opens TM → it FREEZES at that item's exact construction moment with
4D(scene)+5D(⚖ drawer) variance → scrub → ERP reacts (NEXT #1). Goal = 360 OPTICS (one identity, four folds), not animation.

## §WITNESSES TO RE-RUN (regression before/after any change on the 360 branch)
`viewer/tests/`: test_tm_variance.js (S1 13/13) · test_zoom_cost_panel.js (S2 17/17) · test_tm_broadcast.js (S3 11/11).
`build/erp/tests/` (bim-compiler): gen_mfg_shopfloor.js (E0 13/13) · bake_mfg_shopfloor.js (E1 10/10, --write to persist).
