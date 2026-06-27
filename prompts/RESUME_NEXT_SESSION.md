# RESUME — Next Session (handoff 2026-06-27)

```
# ⚠ DO NOT REMOVE
SCOPE: Continuation card for the Terminal-rule-mining + Modeller-trilogy arc. Read this first, then the
canonical detail in prompts/RESUME_TERMINAL_RULE_MINING.md. NON-INVENT + read-the-log rules still apply.
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

## NEXT (pick up here)
- **Router demo on a real-MEP building** — residents have no pipes (Router honest-0). Demo nn-chains on a building WITH
  IfcPipeSegment/DuctSegment to show the Router half live.
- **ELEC/STR round-trip REDs** — HONEST, left as-is (ELEC z-band metric harsh but array GREEN; STR off-grid by design
  = grid-lock crux). Do NOT tune to pass. Separate hard prompt.

## HOUSEKEEPING / FLAGS
- ⚠ bim-ootb local main has an UNPUSHED commit from ANOTHER session: `b261b64 feat(viewer): three r184→r185`
  (viewer-only). main is PR-protected → it needs its OWN PR from that session; do not direct-push.
- Canonical detail: `prompts/RESUME_TERMINAL_RULE_MINING.md` (§PRIOR-ART RECON / §WALKER-EQUIVALENCE / §SHIM all
  marked DONE there). Modeller vision: `prompts/RESUME_GRAPH_MODELLER_INTEGRATION.md §VISION-LOCK`.
```
