#!/usr/bin/env bash
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
# ⚠ DO NOT REMOVE — Scope guard
# Scope: REPORTING_LANE Step 3 (browser integration) — carry the Financial-Report DEFINITION metadata into the
#   served bundle so report_overlay.foldStatement (W-PA-REPORT) folds a statement IN THE BROWSER, not just in the
#   node witness. Source = LIVE idempiere_test (the SAME oracle scripts/poc_pa_report.js diffs against) so the
#   bundle is immune to the shared ad_full.db being regenerated concurrently. Straight copy of the exact columns
#   the foldStatement contract reads — deterministic, re-runnable, NON-INVENT. Companion to extract_fact_acct.sh
#   (the FACT tier): run AFTER it, because that script DROP+recreates c_elementvalue (no accountsign) and this
#   adds the accountsign column the fold's natural-sign rule needs.
# Run:  bash scripts/extract_fact_acct.sh && bash scripts/extract_pa_report.sh
#       (then: node scripts/poc_pa_report.js — must stay maxDiff=0c; open glassbowl.html → ▤ statement picker)
set -euo pipefail
CONTAINER=postgres
PGDB=idempiere_test
PGUSER=adempiere
DB=build/erp/glassbowl_data.db
TREE=101            # the EV account tree (MReportTree leaf-expansion)
CAL=102            # the GardenWorld calendar (period windows)
[ -f "$DB" ] || { echo "FATAL: $DB missing — run extract_fact_acct.sh first"; exit 1; }
PG() { docker exec "$CONTAINER" psql -U "$PGUSER" -d "$PGDB" -At --csv -c "$1"; }
imp() { # imp <table> <create-def>  (csv already in /tmp/<table>.csv, -At suppressed the header)
  local t="$1" def="$2"
  sqlite3 "$DB" "DROP TABLE IF EXISTS $t; CREATE TABLE $t($def);"
  sqlite3 "$DB" ".mode csv" ".import /tmp/$t.csv $t"
}

echo "== extract Financial-Report metadata (client 0,11) from $CONTAINER:$PGDB =="
PG "SELECT pa_report_id, name, pa_reportlineset_id, pa_reportcolumnset_id, c_acctschema_id, c_calendar_id
    FROM adempiere.pa_report WHERE ad_client_id IN (0,11) ORDER BY pa_report_id;" > /tmp/pa_report.csv
imp pa_report "pa_report_id INT, name TEXT, pa_reportlineset_id INT, pa_reportcolumnset_id INT, c_acctschema_id INT, c_calendar_id INT"

PG "SELECT pa_reportline_id, pa_reportlineset_id, seqno, name, linetype, calculationtype, oper_1_id, oper_2_id, paamounttype, paperiodtype, isactive
    FROM adempiere.pa_reportline WHERE ad_client_id IN (0,11) ORDER BY pa_reportlineset_id, seqno;" > /tmp/pa_reportline.csv
imp pa_reportline "pa_reportline_id INT, pa_reportlineset_id INT, seqno INT, name TEXT, linetype TEXT, calculationtype TEXT, oper_1_id INT, oper_2_id INT, paamounttype TEXT, paperiodtype TEXT, isactive TEXT"

PG "SELECT pa_reportcolumn_id, pa_reportcolumnset_id, seqno, name, columntype, calculationtype, oper_1_id, oper_2_id, paamounttype, paperiodtype, relativeperiod, postingtype, isactive
    FROM adempiere.pa_reportcolumn WHERE ad_client_id IN (0,11) ORDER BY pa_reportcolumnset_id, seqno;" > /tmp/pa_reportcolumn.csv
imp pa_reportcolumn "pa_reportcolumn_id INT, pa_reportcolumnset_id INT, seqno INT, name TEXT, columntype TEXT, calculationtype TEXT, oper_1_id INT, oper_2_id INT, paamounttype TEXT, paperiodtype TEXT, relativeperiod INT, postingtype TEXT, isactive TEXT"

PG "SELECT pa_reportline_id, c_elementvalue_id, elementtype, isactive
    FROM adempiere.pa_reportsource WHERE ad_client_id IN (0,11) ORDER BY pa_reportline_id;" > /tmp/pa_reportsource.csv
imp pa_reportsource "pa_reportline_id INT, c_elementvalue_id INT, elementtype TEXT, isactive TEXT"

echo "== extract the calendar (c_period/c_year) + the EV account tree (ad_treenode $TREE) =="
PG "SELECT c_period_id, c_year_id, name, startdate::date, enddate::date, periodtype, isactive
    FROM adempiere.c_period WHERE ad_client_id IN (0,11) ORDER BY c_period_id;" > /tmp/c_period.csv
imp c_period "c_period_id INT, c_year_id INT, name TEXT, startdate TEXT, enddate TEXT, periodtype TEXT, isactive TEXT"

PG "SELECT c_year_id, c_calendar_id FROM adempiere.c_year WHERE ad_client_id IN (0,11) ORDER BY c_year_id;" > /tmp/c_year.csv
imp c_year "c_year_id INT, c_calendar_id INT"

PG "SELECT ad_tree_id, node_id, parent_id FROM adempiere.ad_treenode WHERE ad_tree_id=$TREE ORDER BY node_id;" > /tmp/ad_treenode.csv
imp ad_treenode "ad_tree_id INT, node_id INT, parent_id INT"

echo "== extract the AD_Menu reporting branch (so the app launches reports the iDempiere way) =="
# iDempiere menu: Performance Analysis and Accounting (278) -> Financial Reporting (280) -> Financial Report (281,
# a Window over table PA_Report). The 3 PA_Reports are the records in that window, run via the FinReport process.
# We carry the whole client-0 menu + the MM tree (small) so the branch is DATA-DRIVEN, and a menu->table map so the
# app knows which menu node opens PA_Report. NON-INVENT: a straight copy of AD_Menu / AD_TreeNodeMM / AD_Tab.
PG "SELECT ad_menu_id, name, action, COALESCE(ad_window_id,0), COALESCE(ad_process_id,0), issummary, isactive
    FROM adempiere.ad_menu WHERE ad_client_id=0 ORDER BY ad_menu_id;" > /tmp/ad_menu.csv
imp ad_menu "ad_menu_id INT, name TEXT, action TEXT, ad_window_id INT, ad_process_id INT, issummary TEXT, isactive TEXT"

PG "SELECT ad_tree_id, node_id, parent_id, seqno FROM adempiere.ad_treenodemm WHERE ad_tree_id=10 ORDER BY parent_id, seqno;" > /tmp/ad_treenodemm.csv
imp ad_treenodemm "ad_tree_id INT, node_id INT, parent_id INT, seqno INT"

# menu->base-table for Window menus: ad_window -> tablevel-0 ad_tab -> ad_table (lowest seqno tab). Lets the app
# resolve 'Financial Report' (281) to PA_Report without hardcoding the window id.
PG "SELECT m.ad_menu_id, t.tablename
    FROM adempiere.ad_menu m
    JOIN adempiere.ad_tab tb ON tb.ad_window_id=m.ad_window_id AND tb.tablevel=0 AND tb.isactive='Y'
       AND tb.seqno=(SELECT MIN(tb2.seqno) FROM adempiere.ad_tab tb2 WHERE tb2.ad_window_id=m.ad_window_id AND tb2.tablevel=0 AND tb2.isactive='Y')
    JOIN adempiere.ad_table t ON t.ad_table_id=tb.ad_table_id
    WHERE m.action='W' AND m.ad_window_id IS NOT NULL AND m.ad_client_id=0;" > /tmp/ad_menu_table.csv
imp ad_menu_table "ad_menu_id INT, tablename TEXT"

echo "== backfill c_elementvalue.accountsign (the fold's natural-sign rule) =="
# extract_fact_acct.sh recreates c_elementvalue WITHOUT accountsign; add it only if absent, then populate by id.
if ! sqlite3 "$DB" "PRAGMA table_info(c_elementvalue);" | grep -qi 'accountsign'; then
  sqlite3 "$DB" "ALTER TABLE c_elementvalue ADD COLUMN accountsign TEXT;"
fi
PG "SELECT c_elementvalue_id, accountsign FROM adempiere.c_elementvalue WHERE ad_client_id IN (0,11) ORDER BY c_elementvalue_id;" > /tmp/ev_sign.csv
sqlite3 "$DB" "DROP TABLE IF EXISTS ev_sign; CREATE TABLE ev_sign(c_elementvalue_id INT, accountsign TEXT);"
sqlite3 "$DB" ".mode csv" ".import /tmp/ev_sign.csv ev_sign"
sqlite3 "$DB" "UPDATE c_elementvalue SET accountsign=(SELECT s.accountsign FROM ev_sign s WHERE s.c_elementvalue_id=c_elementvalue.c_elementvalue_id); DROP TABLE ev_sign;"

echo "== verify (§-log) =="
sqlite3 "$DB" "SELECT '§PA-EXTRACT pa_report='||count(*) FROM pa_report;"
sqlite3 "$DB" "SELECT '§PA-EXTRACT pa_reportline='||count(*)||' pa_reportcolumn='||(SELECT count(*) FROM pa_reportcolumn)||' pa_reportsource='||(SELECT count(*) FROM pa_reportsource) FROM pa_reportline;"
sqlite3 "$DB" "SELECT '§PA-EXTRACT c_period='||count(*)||' c_year='||(SELECT count(*) FROM c_year)||' ad_treenode='||(SELECT count(*) FROM ad_treenode) FROM c_period;"
sqlite3 "$DB" "SELECT '§PA-EXTRACT c_elementvalue accountsign_N='||sum(CASE WHEN accountsign='N' THEN 1 ELSE 0 END)||' of '||count(*) FROM c_elementvalue;"
sqlite3 "$DB" "SELECT '§PA-EXTRACT ad_menu='||count(*)||' ad_treenodemm='||(SELECT count(*) FROM ad_treenodemm)||' menu->table='||(SELECT count(*) FROM ad_menu_table) FROM ad_menu;"
sqlite3 "$DB" "SELECT '§PA-EXTRACT FinancialReport-menu='||ad_menu_id||' \"'||name||'\" action='||action||' opens='||(SELECT tablename FROM ad_menu_table t WHERE t.ad_menu_id=m.ad_menu_id) FROM ad_menu m WHERE ad_menu_id=281;"
