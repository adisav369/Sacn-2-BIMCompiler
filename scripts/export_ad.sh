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

OUT="${ERP_OUT:-build/erp/ad_seed_fullwidth.db}"

echo "§EXPORT starting iDempiere FULL-WIDTH AD export (manifest-driven)..."
docker start postgres >/dev/null 2>&1 || true
sleep 1

node scripts/export_ad_seed.js

# ── MIGRATE_POSTING_CONFIG.md §TWO-PATHS (iDempiere) — Witness: W-MIGRATE-POSTCFG ──────────────
# fact_acct (client 11, GRANULAR — column list MIRRORS scripts/extract_fact_acct.sh): lights the
# TrialBalance honest-empty residual in the default seed. SOURCE SPLIT (named, not hidden): the
# manifest tables come from PG db `idempiere`, but client-11 fact_acct lives ONLY in `idempiere_test`
# (`idempiere` shows 0 posted — extract_fact_acct.sh header note). Verified 2026-06-12: both PG dbs
# carry the IDENTICAL client-11 doc set (same c_order/c_invoice ids + grandtotals), and every
# fact_acct.account_id closes within client-11 c_elementvalue → record-id-consistent pull. EXTRACT,
# DON'T INVENT: every row is a recorded iDempiere posting. INT-typed to match the integer-key seed.
echo "§EXPORT fact_acct pull (client 11 from idempiere_test — the posted oracle) → $OUT"
docker exec postgres psql -U adempiere -d idempiere_test -At --csv -c "
  SELECT fact_acct_id, ad_client_id, ad_org_id, c_acctschema_id, account_id, c_period_id,
         ad_table_id, record_id, line_id, gl_category_id, c_tax_id, postingtype, c_currency_id,
         round(amtsourcedr,2), round(amtsourcecr,2), round(amtacctdr,2), round(amtacctcr,2),
         round(qty,2), m_product_id, c_bpartner_id, replace(coalesce(description,''),chr(10),' ')
  FROM adempiere.fact_acct WHERE ad_client_id=11 ORDER BY fact_acct_id;" > /tmp/seed_fact_acct.csv
sqlite3 "$OUT" "DROP TABLE IF EXISTS fact_acct; CREATE TABLE fact_acct(
  fact_acct_id INT, ad_client_id INT, ad_org_id INT, c_acctschema_id INT, account_id INT, c_period_id INT,
  ad_table_id INT, record_id INT, line_id INT, gl_category_id INT, c_tax_id INT, postingtype TEXT,
  c_currency_id INT, amtsourcedr REAL, amtsourcecr REAL, amtacctdr REAL, amtacctcr REAL,
  qty REAL, m_product_id INT, c_bpartner_id INT, description TEXT, PRIMARY KEY(fact_acct_id));"
sqlite3 "$OUT" ".mode csv" ".import /tmp/seed_fact_acct.csv fact_acct"

# POSTCFG audit — the post_resolver read-set MUST be present + non-empty (these flow in via
# scripts/ad_seed_manifest.json; a manifest regression here would silently re-open the
# coverage:absent gap MIGRATE_POSTING_CONFIG.md documents). FAIL LOUD, never ship a hollow seed.
for t in c_bp_customer_acct m_product_category_acct c_tax_acct c_acctschema_default c_validcombination c_elementvalue fact_acct; do
  n=$(sqlite3 "$OUT" "SELECT count(*) FROM $t;" 2>/dev/null || echo MISSING)
  echo "§EXPORT postcfg $t rows=$n"
  { [ "$n" = "MISSING" ] || [ "$n" = "0" ]; } && { echo "§EXPORT ABORT postcfg table $t absent/empty — posting config would not resolve"; exit 1; }
done
# docs must carry the resolver's linkage keys (bpartner on header, product on line)
sqlite3 "$OUT" "SELECT '§EXPORT postcfg linkage c_order.c_bpartner_id non-null='||sum(CASE WHEN c_bpartner_id IS NOT NULL THEN 1 ELSE 0 END)||'/'||count(*) FROM c_order;"
sqlite3 "$OUT" "SELECT '§EXPORT postcfg linkage c_orderline.m_product_id non-null='||sum(CASE WHEN m_product_id IS NOT NULL THEN 1 ELSE 0 END)||'/'||count(*) FROM c_orderline;"
sqlite3 "$OUT" "SELECT '§EXPORT postcfg fact_acct ΣDR='||round(sum(amtacctdr),2)||' ΣCR='||round(sum(amtacctcr),2)||' balanced='||(CASE WHEN round(sum(amtacctdr)-sum(amtacctcr),2)=0 THEN 'Y' ELSE 'N' END) FROM fact_acct;"

echo "§EXPORT done — verify the §SEED-FW + §EXPORT postcfg lines above, then ship $OUT"
echo "§EXPORT deploy: cp to <worktree>/erp/ad_seed.db + bump IndexedDB key (ad_seed_vNN in"
echo "§EXPORT   idempiere.html AND erp.html) + sw.js CACHE_VERSION (IDMP_FULLWIDTH_SEED §2)"
