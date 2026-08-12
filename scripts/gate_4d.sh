#!/usr/bin/env bash
# gate_4d.sh — ONE command that says whether the 4D lane is intact.
#
# WHY THIS EXISTS (2026-08-12): five changes shipped in one day (#1313 §ZONE_INDEX,
# #1314 §TIER_SERIAL_BY_ZONE, #1315 §CREW_DEMAND, #1317 §ARCH_AREA_WEIGHT, plus #1316 shadow),
# each with its own witness, each run by hand, each green in isolation — and the user still hit
# "electrical outlets and hanging elements appearing bit early". That is the failure mode a per-
# change witness cannot catch: nothing ran them TOGETHER, and no witness owned the predicate that
# actually broke. Run this before and after any 4D change and diff the two logs.
#
# Usage (from bim-compiler/):
#   scripts/gate_4d.sh                  # against ~/bim-ootb/viewer working tree
#   VIEWER_DIR=/tmp/vw scripts/gate_4d.sh   # against an exported revision (see probe header)
# Read the log. Exit code is a convenience, the numbers are the evidence.
set -uo pipefail

VIEWER_DIR="${VIEWER_DIR:-$HOME/bim-ootb/viewer}"
BLD_DIR="${BLD_DIR:-$HOME/bim-ootb/buildings}"
OUT="${OUT:-/tmp/gate_4d_$(date +%H%M%S).log}"
cd "$(dirname "$0")/.." || exit 1

pass=0; fail=0; miss=0
say() { printf '%s\n' "$*" | tee -a "$OUT"; }

say "§GATE_4D viewer=$VIEWER_DIR buildings=$BLD_DIR  $(date -Is)"
say ""

# ── 1. Witnesses — each returns n/N; we keep only the verdict + any failing gate ───────────────
for w in witness_zone_index witness_tier_serial_display witness_crew_demand witness_arch_area_weight witness_hosted_before_host; do
  f="$VIEWER_DIR/tests/$w.js"
  if [ ! -f "$f" ]; then say "MISS  $w  (not in this revision)"; miss=$((miss+1)); continue; fi
  out=$(cd "$VIEWER_DIR/tests" && BLD_DIR="$BLD_DIR" timeout 900 node "$w.js" 2>&1)
  verdict=$(printf '%s' "$out" | grep -E "gates passed|SUMMARY" | tail -1)
  bad=$(printf '%s' "$out" | grep -E "^\s*FAIL" | head -4)
  if [ -n "$bad" ]; then
    say "FAIL  $w  ${verdict:-(no verdict line)}"; printf '%s\n' "$bad" | sed 's/^/        /' | tee -a "$OUT" >/dev/null
    printf '%s\n' "$bad" | sed 's/^/        /'
    fail=$((fail+1))
  else
    say "PASS  $w  ${verdict:-ok}"; pass=$((pass+1))
  fi
done

# ── 2. The probe — the shape numbers, not a re-derivation ──────────────────────────────────────
say ""
say "── §-numbers (compare against prompts/4D_SCHEDULE_PERFECTION.md; drift here is the finding) ──"
VIEWER_DIR="$VIEWER_DIR" BLD_DIR="$BLD_DIR" timeout 1800 node scripts/probe_arch_start.js 2>&1 \
  | grep -E "§DAY_GAP |§DAY_GAP_PHASE_OCC|§TIER_SERIAL_BY_ZONE|§CREW_AUTOSCALE|§ARCH_AREA_WEIGHT classes|§HOSTED_BEFORE_HOST" \
  | tee -a "$OUT"

say ""
say "§GATE_4D_RESULT pass=$pass fail=$fail missing=$miss  log=$OUT"
[ "$fail" -eq 0 ]
