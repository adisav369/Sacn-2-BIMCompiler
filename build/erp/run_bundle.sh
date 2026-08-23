#!/usr/bin/env bash
# ⚠ DO NOT REMOVE — Scope guard
# Scope: run_bundle.sh — re-run every TALLY-row witness in docs/internal/ERP_EQUIVALENCE_LEDGER.md in one
#   pass (T-0 item 1, "make the 52-oracle-equivalent ledger ... bundle-re-runnable" —
#   prompts/RESUME_ERP_T0_TRUTH_MAINTENANCE.md). Each script runs via the existing build/erp/run_witness.sh
#   convention (full output -> build/erp/<name>.log, only the tail is echoed here) so nothing here re-invents
#   that contract. READ THE LOGS after every run (exit code alone is not evidence) — this script's own
#   summary line is a POINTER to the logs, not a substitute for reading them.
# Usage: bash build/erp/run_bundle.sh          (46 tally-row scripts)
#        bash build/erp/run_bundle.sh --all    (+ the 3 evidence-only rows, 49 total)
set -uo pipefail
cd "$(dirname "$0")/../.."   # repo root

# 46 tally-row scripts, in ERP_EQUIVALENCE_LEDGER.md row order (# column). Keep this list in sync with that
# file's table — regenerate both together if the source ERP_COVERAGE_MATRIX.md rows change.
TALLY_SCRIPTS="
scripts/test_report_fin.js
scripts/poc_post_harden.js
scripts/poc_fold_complete.js
scripts/poc_money_post.js
scripts/poc_alloc_post.js
scripts/poc_alloc_fx.js
scripts/poc_qtyonhand.js
scripts/poc_movement.js
scripts/poc_movement_fx.js
scripts/poc_matchinv.js
scripts/poc_matchinv_fx.js
scripts/poc_invoice_complete.js
scripts/poc_invoice_post_ap.js
scripts/poc_replenish.js
scripts/poc_gljournal.js
scripts/poc_reverse.js
scripts/poc_valrule_harden.js
scripts/poc_reference_harden.js
scripts/poc_access_harden.js
scripts/poc_callout_harden.js
scripts/poc_factacct_doc.js
scripts/poc_morder_post.js
scripts/poc_fold_inout_gl.js
scripts/poc_morder_save.js
scripts/poc_morder_fsm.js
scripts/poc_minout_save.js
scripts/poc_minout_fsm.js
scripts/poc_minvoice_save.js
scripts/poc_minvoice_fsm.js
scripts/poc_mpayment_save.js
scripts/poc_mpayment_fsm.js
scripts/poc_minventory_family_fsm.js
scripts/poc_mjournal_fsm.js
scripts/poc_mjournal_save.js
scripts/poc_mallochdr_fsm.js
scripts/poc_mallochdr_save.js
scripts/poc_mcash_fsm.js
scripts/poc_mcash_save.js
scripts/poc_mbankstatement_fsm.js
scripts/poc_mbankstatement_save.js
scripts/poc_generic_tail_fsm.js
scripts/poc_generic_tail_save.js
scripts/poc_logic_harden.js
scripts/poc_wf_harden.js
scripts/poc_post_b3.js
scripts/poc_post_tail.js
"

# 3 evidence-only rows (don't count toward the surface tally, but are real re-runnable witnesses) — only
# run with --all.
EVIDENCE_SCRIPTS="
scripts/poc_migrate_postcfg_idmp.js
build/erp/gen_ad_odoo.js
scripts/poc_p4_buyside_live.js
"

SCRIPTS="$TALLY_SCRIPTS"
MODE="tally-only (46 scripts)"
if [ "${1:-}" = "--all" ]; then
  SCRIPTS="$TALLY_SCRIPTS $EVIDENCE_SCRIPTS"
  MODE="all (49 scripts, incl. 3 evidence rows)"
fi

BUNDLE_LOG="build/erp/run_bundle.log"
: > "$BUNDLE_LOG"
PASS=0; FAIL=0; MISSING=0
TOTAL=$(echo "$SCRIPTS" | grep -c '\.js$')

for s in $SCRIPTS; do
  if [ ! -f "$s" ]; then
    echo "── $s MISSING (not on disk) ──" | tee -a "$BUNDLE_LOG"
    MISSING=$((MISSING+1))
    continue
  fi
  OUT=$(bash build/erp/run_witness.sh "$s" 2>&1)
  echo "$OUT" >> "$BUNDLE_LOG"
  EXIT=$(echo "$OUT" | grep -oE 'exit [0-9]+' | head -1 | awk '{print $2}')
  NAME=$(basename "$s" .js)
  if [ "$EXIT" = "0" ]; then
    echo "🟢 $NAME"
    PASS=$((PASS+1))
  else
    echo "🔴 $NAME (exit ${EXIT:-?}) — see build/erp/${NAME}.log"
    FAIL=$((FAIL+1))
  fi
done

echo "──────────────────────────────────────────" | tee -a "$BUNDLE_LOG"
echo "RUN_BUNDLE summary ($MODE): ${PASS}/${TOTAL} PASS, ${FAIL} FAIL, ${MISSING} MISSING" | tee -a "$BUNDLE_LOG"
echo "Full combined output: $BUNDLE_LOG (per-script logs: build/erp/<name>.log)" | tee -a "$BUNDLE_LOG"

[ "$FAIL" -eq 0 ] && [ "$MISSING" -eq 0 ]
