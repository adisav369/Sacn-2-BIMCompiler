# BIM Compiler Memory

## PRINCIPLE: Memory = pointers, not content
Details belong in `docs/` specs and `prompts/`. Memory files are pointers. Do NOT bloat.

## Specs
- Master: `docs/BOMBasedCompilation.md` (BBC.md)
- Traceability: `docs/TestArchitecture.md` §Traceability Matrix
- Conventions: `docs/SourceCodeGuide.md` §Conventions, §Critical Traps
- Data Model: `docs/DATA_MODEL.md` §6.3 (4-DB architecture)
- Doc Map: `docs/INDEX.md`
- Git: `docs/DEPLOYMENT.md` §Git Operations

## Build
- `./scripts/run_tests.sh` — full gate
- `./scripts/run_RosettaStones.sh classify_sh.yaml` — SH only
- `mvn compile -q` — compile only

## External
- Bonsai federation: `/home/red1/IfcOpenShell/src/bonsai/bonsai/bim/module/federation/`

## Feedback (files)
- [Log-first debug](feedback_log_first_debug.md) — debug geometry via pipeline logs, not judgement
- [Test SH/DX/RM only](feedback_test_sh_dx_rm_only.md) — limit fleet testing scope
- [component_library local](feedback_component_library_local.md) — never commit via LFS
- [Prompts to files](feedback_prompts_to_files.md) — write prompts to prompts/ dir
- [2D SVG pristine](feedback_2d_svg_pristine.md) — archive SVGs are reference
- [Browser testing](feedback_browser_testing.md) — user won't check console; write to DOM, test via xvfb+puppeteer
- [OCI deploy cache bust](feedback_deployment.md) — MUST bump ?v= in index.html after uploading changed JS to OCI
- [Browser instancing](feedback_browser_instancing.md) — streaming.js symlink, sql.js bind limit, InstancedMesh gaps, mobile GPU budget
- [Refactor isolation](feedback_refactor_isolation.md) — refactor work → full branch only, dev/live are community-facing
- [OCI DEV frozen](feedback_oci_dev_frozen.md) — NEVER upload to bim-ootb-dev, community is viewing. Deploy to bim-ootb-full only.
- [Commit scope](feedback_commit_scope.md) — never commit files with mixed changes from other work streams
- [No regress keyboard](feedback_no_regress_keyboard.md) — add on top, never delete existing code without discussing
- [Same JS context](feedback_same_context.md) — 4D features sharing cursor/scene must be same context, not cross-tab sync
- [Use extracted metadata](feedback_use_extracted_metadata.md) — use elements_meta.storey, don't re-infer from Z-gaps

## Feedback (in specs, not memory files)
- No parametric mesh → BBC.md §2.2.3
- Tack convention → BBC.md §4 (NEVER bbox-center library meshes)
- Library filled at extraction ONLY → BBC.md §2.2.3
- Three concerns (WHAT/HOW/WHERE) → BBC.md BOM PRINCIPLE
- Schema not geometry → SourceCodeGuide.md §Critical Traps

## Spatial ERP
- [ERP search (S258)](project_erp_search.md) — FTS5, glass overlay, edge swipe, 23 tables indexed, deployed ootb-full
- [S259c Accordion (S259c)](project_s259c_accordion.md) — Properties/Data gateways, cascading drill accordion, colourful cards, new-tab multi-screen, 138 tests
- Roadmap: `docs/ERP_Roadmap.md` — R1+R2+R10+R16 done. Next: table-level faceted filter, child tab field dedup

## Active prompts
- **S254 Hourglass Drawers** → `prompts/S254_HOURGLASS_DRAWERS.md` — NEXT. Mini Gantt + dashboard drawers on hourglass panel.
- **GANTT_ACCURACY** → `prompts/GANTT_ACCURACY.md` — §A DONE (storey-based banding). §B/§C/§D future.
- **2D_031 Card-First Views** → `prompts/2D_031_card_first_views.md` — Persistent view cards, one schema column.
- **2D_029 kernel_ops** → DONE. Undo/redo works, grid drag with cost panel variance.
- **S240/S240b 4D Ghost Glass** → Phase 0-2 DONE. ghostglass.js to be retired (S254 replaces its role).
- S226 localisation → `prompts/S226_localisation.md`

## Project
- [S260c Cinematic Drone](project_s260c_cinematic.md) — two-pass Gantt, IDB JSON cache, PBR-lite, outline+dust+sound, sw v359
- [Card-first views](project_card_first_views.md) — next priority, persistent named view cards
- [2D pick identity](project_2d_pick_identity.md) — UNPROVEN: click 2D items → show IFC name. Need separate responsibility + flatten furniture
- [No artificial geometry](feedback_no_artificial_geometry.md) — no ribbon, no invented colors
- [Proven cutZ](feedback_proven_cutz.md) — floorZ+1.2 only, opening-avg and footprint-area regressed

## Community
- [YouTube prosota_visuals](project_youtube_prosota.md) — first substantive user, drove S220-S223 specs
- [Gerard Tchahba](project_gerard_hdp.md) — HDP user, drove S228 multi-format import

## Reference
- [OCI deployment](reference_oci_deploy.md) — buckets, upload commands, live URLs
- BIM OOTB Browser → `docs/BIM_Designer_Browser.md`
- RTree spec → `docs/RTree.md`
- Mobile → `docs/MOBILE_DEPLOY.md`

## User: [user_profile.md](user_profile.md)
