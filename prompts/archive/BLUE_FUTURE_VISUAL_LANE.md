# ⚠ DO NOT REMOVE — NEXT SESSION: BLUE FUTURE visual layer (item 0 browser legs)
# Scope: wire the Blue Future UX skin on idempiere.html over the SHIPPED engine. Spec-first,
# §-log first (READ the log after every run), consume the seam (never fork a verb), GO before deploy.
# Source of truth: prompts/FRONTEND_LANE_MASTER.md §OUTSTANDING → "DOC-PANEL / KERNEL-LOG SAVE+AUDIT BAND".
# Engine = build/erp (canonical); deploy surgically onto a clean origin/main worktree (don't ship drift).

ALREADY DONE — DO NOT REBUILD (verify in witness, then move on):
- Item 3b PER-FIELD LINEAGE = ✅ SHIPPED LIVE (bim-ootb PR #314, erp sw v685). hover-pause blurb +
  CORE.fieldLineage. W-FIELD-LINEAGE 6/6 + W-BLUE-LINEAGE-LIVE 7/7.
- Item 0 BLUE FUTURE ENGINE = ✅ SHIPPED LIVE (same PR). kernel_ops v9: branch_id col (∉ _canonical),
  replayOps(db,type,branch) official-default, branchOps/discardBranch/acceptBranchUpTo.
  W-BLUE-FUTURE 9/9. Primitives are on window.KernelOps live.
- Read-only back-dot + DocStatus→Kanban already wired — confirm, don't rebuild.

THIS SESSION — item 0 BROWSER/VISUAL legs (the engine is ready; this is the skin):
1. Branch mode + dot gestures (COLOR RULE — see card): long-click WHITE dot → enter blue (no blue) OR
   discard-all-blue-onward (blue exists, CONFIRM, destructive); long-click BLUE dot → accept-up-to-here.
   Wire to discardBranch / acceptBranchUpTo; ordinary edits in blue mode commit with {branch_id}.
2. PERVASIVE UNOFFICIAL treatment: blue rim on every window incl. GL + "UNOFFICIAL" banner on top +
   "UNOFFICIAL" watermark on every print/export (safety, not cosmetic).
3. Zoom/drill-through into the blue children (M_InOut/C_Invoice from the branch).
4. Filter the remaining OFFICIAL read-sites in the browser chrome so blue stays invisible there too
   (engine path already filters via replayOps default; the conflict-map lists the sites:
   readTip/listTip/tipValues + list/visibility overlays in crud_overlay.js).
5. Full CompleteIt demo: blue dot runs the real fan-out + Zoom (per DEMO BAR).
HONEST GAP (decision owed, NOT this session unless asked): multi-actor accept = op-vs-op conflict detect
   (two branches editing same record). Single-user accept is clean (chain stays valid). §0.20 rebase = upgrade path.

WITNESS: W-BLUE-FUTURE-LIVE (headless-chrome DOM probe, pattern = erp/tests/poc_blue_lineage_live.js):
enter blue→rim+banner+watermark present & blue ops invisible to official chrome; long-click blue→accept
(rim drops on accepted dots, later stays blue); long-click white→discard (confirm→folded, official untouched);
Zoom opens a blue child; 0 pageerrors. Then deploy: sw bump, worktree off origin/main, PR, auto-merge, VERIFY landed.

THEN keep going down §OUTSTANDING to zero: item 1 (private draft restore / departure model) → 2 (full
DocActions per AD; only CO wired) → 3a (record-info n/t popup) → 4 (grid multi-select + gear batch) →
5 (Process+New/Save/Print in pill for form view). Each: spec → witness → ✅ DONE (witness) / ⛔ BLOCKED.

REFERENCE — key files/anchors:
- Engine (canonical): build/erp/kernel_ops.js (v9), build/erp/crud_overlay.js (CORE.fieldLineage, lineageHover).
- Shipping UI: bim-ootb/erp/idempiere.html (chrome, grid/form render, dot history), crud_overlay.js, sw.js.
- Headless witnesses: scripts/poc_blue_future.js, scripts/poc_field_lineage.js (run via bash build/erp/run_witness.sh).
- Live probe template: bim-ootb/erp/tests/poc_blue_lineage_live.js (served idempiere.html + playwright).
- Dot/history bar: idmp_history.js (_go/_histRestore, read-only back-dot — blue dots layer on this).
- Deploy: edit engine in build/erp → sync to a /tmp/wt-* worktree off origin/main → sw CACHE_VERSION + ?v= bump
  → PR → gh pr merge --auto --squash → VERIFY merged (CLAUDE.md: squash+late-push orphans commits).
