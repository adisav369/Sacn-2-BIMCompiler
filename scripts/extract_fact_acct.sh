#!/usr/bin/env bash
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
# ⚠ DO NOT REMOVE — Scope guard
# Scope: R2 source extract (prompts/CRUD_P_R_REPORT.md §R2, docs/CRUD_P_R_REPORT_SPEC.md §1.2.1).
#   Pull GardenWorld's REAL posted journal (fact_acct, client 11) + the chart of accounts (c_elementvalue,
#   client 0+11) from the Docker Postgres iDempiere into the glassbowl bundle. Non-invent: a straight copy
#   of executed facts, the same move as the 11 lifecycle tables. Deterministic + re-runnable.
#   Source DB note: client-11 fact_acct lives in `idempiere_test` (the `idempiere` DB shows 0 posted).
# Run:  bash scripts/extract_fact_acct.sh   (then: node scripts/test_report_fin.js — must balance 46574.97)
set -euo pipefail
CONTAINER=postgres
PGDB=idempiere_test
PGUSER=adempiere
DB=build/erp/glassbowl_data.db

echo "== extract fact_acct (client 11) + c_elementvalue (client 0,11) from $CONTAINER:$PGDB =="
docker exec "$CONTAINER" psql -U "$PGUSER" -d "$PGDB" -At --csv -c "
  SELECT fact_acct_id, ad_client_id, ad_org_id, c_acctschema_id, account_id, c_period_id, postingtype,
         round(amtacctdr,2), round(amtacctcr,2)
  FROM adempiere.fact_acct WHERE ad_client_id=11 ORDER BY fact_acct_id;" > /tmp/fact_acct.csv
docker exec "$CONTAINER" psql -U "$PGUSER" -d "$PGDB" -At --csv -c "
  SELECT c_elementvalue_id, ad_client_id, value, name, accounttype, issummary
  FROM adempiere.c_elementvalue WHERE ad_client_id IN (0,11) ORDER BY c_elementvalue_id;" > /tmp/c_elementvalue.csv

echo "== load into $DB (drop+recreate; -t suppressed the header, so NO --skip) =="
sqlite3 "$DB" "DROP TABLE IF EXISTS fact_acct; CREATE TABLE fact_acct(fact_acct_id INT, ad_client_id INT, ad_org_id INT, c_acctschema_id INT, account_id INT, c_period_id INT, postingtype TEXT, amtacctdr REAL, amtacctcr REAL);"
sqlite3 "$DB" "DROP TABLE IF EXISTS c_elementvalue; CREATE TABLE c_elementvalue(c_elementvalue_id INT, ad_client_id INT, value TEXT, name TEXT, accounttype TEXT, issummary TEXT);"
sqlite3 "$DB" ".mode csv" ".import /tmp/fact_acct.csv fact_acct"
sqlite3 "$DB" ".mode csv" ".import /tmp/c_elementvalue.csv c_elementvalue"

echo "== verify balanced to the cent (must match PG: Dr=Cr=46574.97) =="
sqlite3 "$DB" "SELECT '§EXTRACT fact_acct rows='||count(*)||' Dr='||round(sum(amtacctdr),2)||' Cr='||round(sum(amtacctcr),2)||' diff='||round(sum(amtacctdr-amtacctcr),2) FROM fact_acct;"
sqlite3 "$DB" "SELECT '§EXTRACT c_elementvalue rows='||count(*) FROM c_elementvalue;"
