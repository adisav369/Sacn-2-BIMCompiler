#!/usr/bin/env bash
# lane_check.sh — POST-HOC REPORT for prompts/*.md lanes. Reports, never blocks.
#
# ⚠ DO NOT REMOVE — Scope: report drift in the lane files. Read the output; decide yourself.
#
# WHY THIS EXITS 0 ALWAYS (2026-08-19, user ruling: "rather they be late post script checking, and
# not too aggressive as it can prevent actual work"):
# A guard that BLOCKS creates pressure to satisfy the guard rather than the intent. Measured on
# 2026-08-19: two sessions collided on section §S38 and BOTH "fixed" it in opposite directions —
# one nearly deleted the other's entire block doing so. A pre-commit block would have FORCED that
# under time pressure rather than merely permitting it. And any hook people learn to `--no-verify`
# past is worse than none, because the bypass habit then defeats every hook including the good ones.
#
# So: this prints evidence. You read it and decide. Same contract as the project's Log Mandate.
# The only real gate in this lane is the adversarial vetting pass, because it is the only one with
# judgement. Do not turn this script into a gate.
set -u
F="${1:-prompts/4D_GANTT_TM_REFACTOR.md}"
cd "$(git rev-parse --show-toplevel 2>/dev/null || echo .)" || exit 0
[ -f "$F" ] || { echo "§LANE_CHECK skip — no such file: $F"; exit 0; }
echo "§LANE_CHECK $F  ($(wc -l < "$F") lines)"

# 1. duplicate top-level section numbers — the concurrent-session collision surface
dup=$(grep -oE '^# §[A-Z0-9_]+' "$F" | sort | uniq -d)
if [ -n "$dup" ]; then
  echo "  ⚠ DUPLICATE section ids (two sessions writing the same number?):"
  echo "$dup" | sed 's/^/      /'
  echo "      -> do NOT renumber someone else's section. Pull first, then take a free id."
else
  echo "  ✓ no duplicate section ids"
fi

# 2. headings present at HEAD but absent from the working tree AND from every archive file
if git rev-parse --verify -q HEAD >/dev/null 2>&1; then
  tmp=$(mktemp -d)
  git show "HEAD:$F" 2>/dev/null | grep -E '^#{1,3} ' | sed 's/[[:space:]]*$//' | sort -u > "$tmp/old" || : > "$tmp/old"
  grep -E '^#{1,3} ' "$F" | sed 's/[[:space:]]*$//' | sort -u > "$tmp/new"
  cat prompts/archive/*.md 2>/dev/null | grep -E '^#{1,3} ' | sed 's/[[:space:]]*$//' | sort -u > "$tmp/arch" || : > "$tmp/arch"
  cat "$tmp/new" "$tmp/arch" | sort -u > "$tmp/kept"
  lost=$(comm -23 "$tmp/old" "$tmp/kept")
  n=$(printf '%s' "$lost" | grep -c . || true)
  if [ "$n" -gt 0 ]; then
    echo "  ⚠ $n heading(s) at HEAD are in NEITHER the working tree NOR prompts/archive/:"
    echo "$lost" | head -12 | sed 's/^/      /'
    echo "      -> if deliberate, fine. If not, this is the 2026-08-19 near-miss: two headings sharing"
    echo "         a prefix, a span scan matching only the first, and a whole section silently dropped."
  else
    echo "  ✓ no headings lost vs HEAD (working tree + archive)"
  fi
  rm -rf "$tmp"
fi

# 3. §STATUS staleness — rows that contradict later sections
if grep -q 'ship-ready change (§S25_REVIEW.6)' "$F" 2>/dev/null; then
  echo "  ⚠ §STATUS still calls §S25_REVIEW.6 ship-ready — §S33.2 killed that claim"
fi
if grep -qE 'FROZEN and must not be rebuilt or$' "$F" 2>/dev/null; then
  echo "  ⚠ standing-ruling banner may predate §S32.6 (SCHEDULE tables are writable)"
fi

# 4. ledger pressure — measured findings that never became work
if grep -q '^# §S37' "$F" 2>/dev/null; then
  open=$(sed -n '/^# §S37/,/^# §S[0-9]/p' "$F" | grep -cE '^\| \*\*[A-D][0-9]\*\*' || true)
  echo "  · §S37 ledger carries $open item(s). A measurement is finished only when it is a PR, a"
  echo "    user decision, or a ledger entry saying why it is neither (§S41.2)."
fi
echo "§LANE_CHECK done — REPORT ONLY, exit 0. Nothing was blocked."
exit 0
