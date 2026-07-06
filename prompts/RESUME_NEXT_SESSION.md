# RESUME — Next Session (handoff 2026-06-27)

```
# ⚠ DO NOT REMOVE
SCOPE: Continuation card for the Terminal-rule-mining + Modeller-trilogy arc. Read this first, then the
canonical detail in prompts/Modeller/DISC_Walker/RESUME_TERMINAL_RULE_MINING.md. NON-INVENT + read-the-log rules still apply.
```

## ⚠ ANTI-AMBIGUITY (user-mandated 2026-06-27) — the TRILOGY
Three OWN top-level app folders in `bim-ootb/`: **`viewer/` · `erp/` · `modeller/`**.
- **"viewer/" is NOT "the Viewer"** — it's a shared bundle dir of ~13 surfaces. The BIM viewer = `viewer/viewer.html`.
- **Modeller = `modeller/modeller.html`** (+ its own JS `disc_walker`/`bonsai_*`/`str_walker*`/`cross_edges`/
  `bom_tree_outliner`/`walker_confidence`, own `lib/`, own `sw.js` `bim-modeller-` prefix, its resident data DBs).
  References `../viewer/connect_scene.js` (cross-surface broker, like erp). **Never put modeller code under `viewer/`.**
- `erp/` = the ERP app. Source-of-truth for the disc-walk engine = `bim-compiler/build/disc_walker.js`.

## DONE THIS SESSION (all pushed / merged)
1. **Prior-art reconciliation** (bim-compiler, lane/benchmark-clash-resolution) — measured Terminal rules flow into
   ERP.db's existing vocab (`ad_mep_anchor`/`ad_mep_pattern` + `ad_placement_measured` + new `ad_place_order`/
   `ad_clash_avoidance`/`ad_routing_measured`); `migration/TRM001_terminal_measured_rules.sql` (wired into
   `rebuild_erp.sh`), `build/reconcile_terminal_rules.py`. **W-TRM-RECONCILE 12/12**.
2. **ERP.db drop-in** — TRM001 adds compat VIEWS so the real `disc_walker.js` walks identically from ERP.db vs
   terminal_rules.db. **W-TRM-WALK-EQUIV 14/14** (`build/witness_disc_walk_erp_equivalence.js`).
3. **SHIM host-attach** — `disc_walker.js` Placer tacks `ref_kind='host'` devices (IfcAlarm) onto REAL walls
   (pos=wall, z=floor+measured dz, yaw=wall rotation_z), count bounded by measured count_per. **W-TRM-SHIM 6/6**
   (`build/witness_disc_walk_shim.js`). Generalize 49/49 + equiv 14/14 regression-clean.
4. **Modeller extracted into its own folder** (bim-ootb **PR #550 MERGED**, sw modeller v1) — the trilogy. Verified
   headless from the new home: W-TERM-WALK 9/9, W-UX-DISC 8/8, W-UX-VERBS 9/9, W-UX-PILL 12/12, W-UX-XEDGE 10/10,
   STRWALK-SMOKE 9/9; eslint+node-check green; viewer untouched. Local `~/bim-ootb` ff'd to origin/main.

## DONE THIS SESSION (cont.)
5. **Tack-chain op-log emit** (bim-ootb PR #553 feat/dw-oplog, auto-merge armed): `_commitDiscWalk(disc, placements)`
   commits each placement as signed `GEOM_INSERT`; `parameters._dw` persists disc/storey/prov/ifc/host in the DB
   (JSON round-trip, survives scrub/replay); SHIM carries `_dw.host = wall guid`; fold re-renders markers after.
   **W-DW-OPLOG 6/6** (O1 committed=55, O2 _dw.disc roundtrip, O3 SHIM host guid, O4 undo−1, O5 redo, O6 clean).
   W-TERM-WALK 9/9 + W-UX-DISC 8/8 regression-clean.
6. **Router half LIVE — nn-chains** (bim-compiler `3939b692`; bim-ootb PR #555 feat/router-nnchain, auto-merge armed):
   `route()` only COUNTED endpoint classes; new `routeChains(disc, bdb)` PRODUCES real nearest-neighbour-3d segments —
   spatial-hash O(n) (not 16M brute pairs), bounded by the measured max gap, **routes the TIGHTER of the two nn
   orientations** (the genuine connectivity direction; pulled DuctFitting→AirTerminal ratio 2.06→0.98). NON-INVENT:
   every segment joins TWO REAL elements at REAL positions, honest no-neighbour skip. Modeller: walk PLB/ACMV on the
   **Terminal** resident (MEP-rich) → whole network (4314 PLB) as instant 3D lines + bounded signed `GEOM_SWEEP` sample
   (cap 60, `_dw.from_guid/to_guid/gap`) to the op-log, undo/redo. **W-NNCHAIN 6/6** (values) + **W-ROUTER-NNCHAIN 8/8**
   (wiring). No regression: generalize 49/49, shim 6/6, erp-equiv 14/14, W-DW-OPLOG/TERM-WALK/UX-DISC all green.

## NEXT (pick up here)
- **ELEC/STR round-trip REDs — the LAST MILE.** Now has a dedicated, sharp handoff: `prompts/RESUME_LASTMILE_STR_ELEC_REDS.md`.
  Diagnosis is in hand (NOT just "it's hard"): the Placer **z-band is the wrong model** for (a) spanning STR
  beams/members — proven by IfcColumn GREEN / IfcBeam RED in the SAME disc — route them like pipes/ducts (Gap-3);
  (b) ELEC IfcElectricAppliance (cover 0.05, multi-height) — SHIM host-attach like FP IfcAlarm; (c) ELEC lights —
  array cadence already GREEN, z-band count is the redundant-harsh lens. Each move anchored to an already-GREEN
  analogue; **earn-don't-tune** (replace wrong model, re-measure; never loosen a threshold). Grid-lock crux
  (~71.5% on-grid by design) = a scope decision to bring to the user, not an extraction. Read that card first.
- **Router `main`/`riser` patterns** (lower-pri): nn-chains done; the horizontal-main + vertical-riser PLB patterns
  stay descriptive (need orientation fits) — a later piece if the demo wants trunk runs, not just nn links.
- **DEPLOY** the SHIM host-attach + ERP.db drop-in to `bim-ootb/viewer/disc_walker.js` (its own deploy session) —
  separate from the modeller copy already shipped.

## HOUSEKEEPING / FLAGS
- ⚠ bim-ootb local main has an UNPUSHED commit from ANOTHER session: `b261b64 feat(viewer): three r184→r185`
  (viewer-only). main is PR-protected → it needs its OWN PR from that session; do not direct-push.
- Canonical detail: `prompts/Modeller/DISC_Walker/RESUME_TERMINAL_RULE_MINING.md` (§PRIOR-ART RECON / §WALKER-EQUIVALENCE / §SHIM all
  marked DONE there). Modeller vision: `prompts/RESUME_GRAPH_MODELLER_INTEGRATION.md §VISION-LOCK`.
```
