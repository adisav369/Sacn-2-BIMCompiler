#!/usr/bin/env bash
# ⚠ DO NOT REMOVE — Witness W-DEPLOY-GUARD for prompts/DOCS_DEPLOY_GUARD.md.
# Drives the REAL guard (safe_gh_deploy.sh GUARD_ONLY) against fixture dirs — no deploy, no network.
# Each test names the issue it proves. A "soft recoverable abort" = exit 1 + gh-pages untouched (we
# inject fixtures so gh-pages is never even reached), which is exactly the fail mode we WANT.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GUARD="$HERE/safe_gh_deploy.sh"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
pass=0; fail=0
ok(){ echo "  ✅ $1"; pass=$((pass+1)); }
no(){ echo "  ❌ $1"; fail=$((fail+1)); }

mklive(){ mkdir -p "$1"; printf '%0.sX' $(seq 1 1000) > "$1/index.html"; printf '%0.sY' $(seq 1 1000) > "$1/glassbowl.html"; }

# run the guard, capture exit code; $1=live $2=new ; extra env via remaining args
guard(){ local live="$1" new="$2"; shift 2; env "$@" GUARD_ONLY=1 GUARD_LIVE_DIR="$live" GUARD_NEW_DIR="$new" bash "$GUARD" >/tmp/wg.out 2>&1; echo $?; }

echo "== §GUARD-DELETE — a live page missing from the new build MUST abort, naming it =="
L="$T/d_live"; N="$T/d_new"; mklive "$L"; mkdir -p "$N"; cp "$L/index.html" "$N/"   # new is missing glassbowl.html
rc=$(guard "$L" "$N")
if [ "$rc" = 1 ] && grep -q 'GUARD-DELETE.*glassbowl.html' /tmp/wg.out; then ok "aborted (exit1) and named glassbowl.html"; else no "did not abort/name the deletion (rc=$rc)"; cat /tmp/wg.out; fi

echo "== §GUARD-SHRINK — a live page truncated >5% in the new build MUST abort (the MCP wipe) =="
L="$T/s_live"; N="$T/s_new"; mklive "$L"; cp -r "$L" "$N"; printf '%0.sX' $(seq 1 800) > "$N/index.html"  # 1000B -> 800B = 20% shrink
rc=$(guard "$L" "$N")
if [ "$rc" = 1 ] && grep -q 'GUARD-SHRINK.*index.html' /tmp/wg.out; then ok "aborted (exit1) on 20% shrink of index.html"; else no "did not abort on shrink (rc=$rc)"; cat /tmp/wg.out; fi

echo "== §GUARD-PASS — a true superset (live + one ADDED page) MUST be allowed =="
L="$T/p_live"; N="$T/p_new"; mklive "$L"; cp -r "$L" "$N"; printf '%0.sZ' $(seq 1 500) > "$N/newpage.html"
rc=$(guard "$L" "$N")
if [ "$rc" = 0 ] && grep -q 'result=PASS' /tmp/wg.out; then ok "allowed (exit0) — superset publishes"; else no "blocked a legit superset (rc=$rc)"; cat /tmp/wg.out; fi

echo "== §GUARD-OVERRIDE — a NAMED shrink is blessed; an UNNAMED shrink still aborts =="
L="$T/o_live"; N="$T/o_new"; mklive "$L"; cp -r "$L" "$N"; printf '%0.sX' $(seq 1 100) > "$N/index.html"  # 1000->100, 90% shrink
rc_named=$(guard "$L" "$N" ALLOW_SHRINK=1 paths="index.html")
rc_unnamed=$(guard "$L" "$N" ALLOW_SHRINK=1 paths="other.html")
if [ "$rc_named" = 0 ] && [ "$rc_unnamed" = 1 ]; then ok "named shrink allowed (exit0), unnamed still aborts (exit1)"; else no "override wrong: named=$rc_named unnamed=$rc_unnamed"; fi

echo
echo "W-DEPLOY-GUARD: $pass passed, $fail failed"
[ "$fail" = 0 ] && echo "§W-DEPLOY-GUARD PASS" || { echo "§W-DEPLOY-GUARD FAIL"; exit 1; }
