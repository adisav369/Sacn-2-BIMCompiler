# prompts/ — the guide

This tree is aligned to the real app suite (`common/whole_history.js`'s own `PAGE_LABEL` map — the actual six
surfaces the product ships: **Viewer, iDempiere, Glassbowl, Gravity, Home, Modeller**). A domain folder exists
only where a real, narrow sub-feature warrants a twig — not as decoration. See
`feedback_prompt_file_organization.md` ("the Watchdog's 1st principle") for the rules this tree follows: one
canonical file per topic, dated sections, git-linked, compact on completion.

## Foundational (git-tracked canon — start here)

**`Modeller/DISC_Walker/`** — the discipline-walking engine (STR/MEP generated onto the ARC substrate):
- `ARC_GEO_FETCH_SPEC.md` — ARC ingestion (DDB vs IFC-open) + "Modeller's first principle" standing agenda.
- `RESUME_MODELLER_WALK_SUBSTRATE.md` — walk orchestration + "Modeller's 2nd principle" standing agenda.
- `STR_ROUTEWALKING_SPEC.md`, `WALKER_GUARDS_ROSETTASTONE_SPEC.md` — STR walker + the universal guard layer.
- `RESUME_DISC_WALKER_ENVELOPE_BOUND.md`, `RESUME_SEED_TRUNK.md` — placement-density fix, MEP seed→corridor trunk.
- `RESUME_MEP_SAMPLECASTLE.md`, `RESUME_DX_MEP_RESIDENTIAL_STANDARD.md` — MEP demo + residential rule standard.
- `RESUME_TERMINAL_RULE_MINING.md`, `RESUME_MODELLER_USER_MANUAL_WALKERS.md` — Terminal rule-mining, user-guide polish.

**`Viewer/HBA/`** — the Human-Asset (HR/BIM-Asset FM) module:
- `RESUME_HR_BIM_ASSET.md` — the one canonical HBA file, all dated sub-lanes live inside it.

**`Viewer/`** — Viewer-specific (not shared across the suite):
- `OPEN_BUTTON_IFC_BCF_MERGE.md` — Drop-IFC→Open button + BCF export.

**Cross-cutting (root — genuinely spans all six surfaces via `common/`, not narrowed to one):**
- `PILL_DRAWER_REORGANIZATION.md` — `common/pill_builder.js`, the shared pill rail every surface loads.
- `RESUME_WORLD_HISTORY_DEDUP_RESTORE.md` — `common/whole_history.js`, the cross-page history log/pill.

## Everything else

The remaining ~190 top-level files, plus `done/` and `archive/`, are not yet triaged into this tree — they
predate this reorg. **Migrate incrementally, not all at once:** when a session next touches one of them,
move it into its real domain folder as part of that touch (git mv, fix cross-refs, same as this pass) rather
than leaving it flat. Don't force a mass reclassification in one sitting — that risks misclassifying files
nobody has re-read recently. `done/` and `archive/` stay as-is (already separated by lifecycle, not by domain).
