#!/bin/bash
# export_ad.sh — Export iDempiere AD metadata + GardenWorld data to the browser seed.
# Source: docker postgres container with idempiere DB
# Output: build/erp/ad_seed_fullwidth.db  (→ ships as bim-ootb/erp/ad_seed.db)
# Usage: bash scripts/export_ad.sh
#
# ⚠ DO NOT REMOVE — Scope guard
# FULL-WIDTH since prompts/IDMP_FULLWIDTH_SEED.md §1 (2026-06-11): the original hand-picked
# COLUMN SLICE here was the bug — every §-named UI seed-gap (M_InOut.MovementType absent →
# 4 windows scope 0 rows; c_doctype.iscanbereactivated/docsubtypeso absent → conservative
# no-RE; no ad_process → B-5/C-5 dead) was self-inflicted by the slice, not the engine.
# This is now a thin wrapper over scripts/export_ad_seed.js, which exports EVERY column
# (SELECT *) of EVERY table in scripts/ad_seed_manifest.json (the per-table contract:
# name-case, WHERE filter, IsActive rule — observed from the deployed seed, never authored)
# + DDL from the PG catalog incl. REAL PRIMARY KEYS. EXTRACT, DON'T INVENT.
# READ THE LOG after every run — exit code is not evidence.
#
# Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.

set -euo pipefail
cd "$(dirname "$0")/.."

echo "§EXPORT starting iDempiere FULL-WIDTH AD export (manifest-driven)..."
docker start postgres >/dev/null 2>&1 || true
sleep 1

node scripts/export_ad_seed.js

echo "§EXPORT done — verify the §SEED-FW lines above, then ship build/erp/ad_seed_fullwidth.db"
echo "§EXPORT deploy: cp to <worktree>/erp/ad_seed.db + bump IndexedDB key (ad_seed_vNN in"
echo "§EXPORT   idempiere.html AND erp.html) + sw.js CACHE_VERSION (IDMP_FULLWIDTH_SEED §2)"
