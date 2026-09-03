#!/usr/bin/env bash
# ⚠ DO NOT REMOVE
# SCOPE: W-ROOM-MIGRATION-APPLY — proves the ROOM00N_*_spatial_structure_carry.sql / ROOM007 scripts in
# this dir apply cleanly to a fresh copy of bim-ootb main's shipped *_ARC.db and reproduce
# fable/modeller-lod400-livewire's real spatial_structure content EXACTLY (row count AND full content,
# not just count). This is the local-apply path CLAUDE.md's LFS block forces us onto instead of pushing
# regenerated binaries. Read the log after every run — exit code alone is not evidence.
set -euo pipefail

HOME_DB=/home/red1/bim-ootb/modeller
SOURCE_DB=/tmp/wt-fable-livewire/modeller
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRATCH="${SCRATCH:-/tmp/w_room_migration_apply}"
rm -rf "$SCRATCH"; mkdir -p "$SCRATCH"

pass=0 fail=0

check_building() {
  local b="$1" script="$2"
  if [[ ! -f "$HOME_DB/${b}_ARC.db" ]]; then
    echo "§W-ROOM-MIGRATION-APPLY SKIP $b — $HOME_DB/${b}_ARC.db not present locally"
    return
  fi
  if [[ ! -f "$SOURCE_DB/${b}_ARC.db" ]]; then
    echo "§W-ROOM-MIGRATION-APPLY SKIP $b — $SOURCE_DB/${b}_ARC.db not present locally (worktree missing)"
    return
  fi
  cp "$HOME_DB/${b}_ARC.db" "$SCRATCH/${b}_ARC.db"
  sqlite3 "$SCRATCH/${b}_ARC.db" < "$SCRIPT_DIR/$script"
  sqlite3 "$SCRATCH/${b}_ARC.db" "SELECT * FROM spatial_structure ORDER BY guid" > "$SCRATCH/${b}_applied.txt"
  sqlite3 "$SOURCE_DB/${b}_ARC.db" "SELECT * FROM spatial_structure ORDER BY guid" > "$SCRATCH/${b}_source.txt"
  if diff -q "$SCRATCH/${b}_applied.txt" "$SCRATCH/${b}_source.txt" > /dev/null; then
    echo "§W-ROOM-MIGRATION-APPLY PASS $b content matches fable/modeller-lod400-livewire exactly ($(wc -l < "$SCRATCH/${b}_applied.txt") rows)"
    pass=$((pass+1))
  else
    echo "§W-ROOM-MIGRATION-APPLY FAIL $b content mismatch after applying $script"
    diff "$SCRATCH/${b}_applied.txt" "$SCRATCH/${b}_source.txt" | head -10
    fail=$((fail+1))
  fi
}

check_building SampleHouse   ROOM001_SampleHouse_spatial_structure_carry.sql
check_building HHS           ROOM002_HHS_spatial_structure_carry.sql
check_building Clinic        ROOM003_Clinic_spatial_structure_carry.sql
check_building Garage        ROOM004_Garage_spatial_structure_carry.sql
check_building Hospital      ROOM005_Hospital_spatial_structure_carry.sql
check_building SampleCastle  ROOM006_SampleCastle_spatial_structure_carry.sql
check_building Duplex        ROOM007_Duplex_strip_roof.sql

echo "§W-ROOM-MIGRATION-APPLY SUMMARY pass=$pass fail=$fail"
[[ "$fail" -eq 0 ]] || exit 1
