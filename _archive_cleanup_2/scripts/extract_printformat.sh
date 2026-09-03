#!/usr/bin/env bash
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
# ⚠ DO NOT REMOVE — Scope guard
# Scope: REPORTING_LANE Step 2 (foldPrint / W-PRINTFORMAT) — carry the DOCUMENT-PRINT definition into the served
#   bundle: ad_printformat + ad_printformatitem (the layout AS DATA, all 93 formats / 2780 items — generic, no
#   per-format hardcoding) + the MATERIALIZED print views for the seed's invoices (c_invoice_header_v +
#   c_invoice_linetax_v, client 11). The views are evaluated BY POSTGRES at extract time — the engine interprets
#   the FORMAT over these rows and never reimplements the view SQL (non-invent). Source = LIVE idempiere_test,
#   immune to the concurrently-regenerated ad_full.db. Companion to extract_fact_acct.sh + extract_pa_report.sh.
# Run:  bash scripts/extract_printformat.sh
#       (then: node scripts/poc_printformat.js — read build/erp/poc_printformat.log before concluding)
set -euo pipefail
CONTAINER=postgres
PGDB=idempiere_test
PGUSER=adempiere
DB=build/erp/glassbowl_data.db
[ -f "$DB" ] || { echo "FATAL: $DB missing — run extract_fact_acct.sh first"; exit 1; }
PG() { docker exec "$CONTAINER" psql -U "$PGUSER" -d "$PGDB" -At --csv -c "$1"; }
imp() { # imp <table> <create-def>  (csv already in /tmp/<table>.csv, -At suppressed the header)
  local t="$1" def="$2"
  sqlite3 "$DB" "DROP TABLE IF EXISTS $t; CREATE TABLE $t($def);"
  sqlite3 "$DB" ".mode csv" ".import /tmp/$t.csv $t"
}

echo "== extract AD_PrintFormat / AD_PrintFormatItem (the layout definition, client 0+11) =="
PG "SELECT f.ad_printformat_id, f.name, t.tablename, f.isactive
    FROM adempiere.ad_printformat f JOIN adempiere.ad_table t ON t.ad_table_id=f.ad_table_id
    WHERE f.ad_client_id IN (0,11) ORDER BY f.ad_printformat_id;" > /tmp/ad_printformat.csv
imp ad_printformat "ad_printformat_id INT, name TEXT, tablename TEXT, isactive TEXT"

PG "SELECT i.ad_printformatitem_id, i.ad_printformat_id, i.seqno, i.name, COALESCE(i.printname,''),
           i.printformattype, i.isprinted, i.isgroupby, i.issummarized, i.iscounted, i.isaveraged,
           i.sortno, i.isorderby, COALESCE(c.columnname,''), COALESCE(i.ad_printformatchild_id,0),
           COALESCE(i.fieldalignmenttype,''), i.ispagebreak, i.isactive
    FROM adempiere.ad_printformatitem i LEFT JOIN adempiere.ad_column c ON c.ad_column_id=i.ad_column_id
    WHERE i.ad_client_id IN (0,11) ORDER BY i.ad_printformat_id, i.seqno;" > /tmp/ad_printformatitem.csv
imp ad_printformatitem "ad_printformatitem_id INT, ad_printformat_id INT, seqno INT, name TEXT, printname TEXT, printformattype TEXT, isprinted TEXT, isgroupby TEXT, issummarized TEXT, iscounted TEXT, isaveraged TEXT, sortno INT, isorderby TEXT, columnname TEXT, ad_printformatchild_id INT, fieldalignmenttype TEXT, ispagebreak TEXT, isactive TEXT"

echo "== materialize the print views for the seed invoices (PG evaluates; the engine only interprets the format) =="
PG "SELECT c_invoice_id, documentno, dateinvoiced::date, COALESCE(dateordered::date::text,''), name,
           COALESCE(bpvalue,''), COALESCE(referenceno,''), COALESCE(poreference,''), COALESCE(salesrep_name,''),
           COALESCE(documenttype,''), COALESCE(description,''), grandtotal, COALESCE(paymentterm,''),
           COALESCE(c_order_id,0), c_bpartner_id, issotrx, docstatus
    FROM adempiere.c_invoice_header_v WHERE ad_client_id=11 AND ad_language='en_US'
    ORDER BY c_invoice_id;" > /tmp/c_invoice_header_v.csv
imp c_invoice_header_v "c_invoice_id INT, documentno TEXT, dateinvoiced TEXT, dateordered TEXT, name TEXT, bpvalue TEXT, referenceno TEXT, poreference TEXT, salesrep_name TEXT, documenttype TEXT, description TEXT, grandtotal REAL, paymentterm TEXT, c_order_id INT, c_bpartner_id INT, issotrx TEXT, docstatus TEXT"

PG "SELECT c_invoice_id, COALESCE(c_invoiceline_id,0), line, COALESCE(name,''), COALESCE(description,''),
           COALESCE(qtyinvoiced::text,''), COALESCE(qtyentered::text,''), COALESCE(uomsymbol,''),
           COALESCE(priceenteredlist::text,''), COALESCE(discount::text,''), COALESCE(priceentered::text,''),
           COALESCE(priceactual::text,''), COALESCE(linenetamt::text,''), COALESCE(taxamt::text,''),
           COALESCE(taxindicator,''), COALESCE(documentnote,''), COALESCE(resourcedescription,''),
           COALESCE(m_product_id,0)
    FROM adempiere.c_invoice_linetax_v WHERE ad_client_id=11 AND ad_language='en_US'
    ORDER BY c_invoice_id, line;" > /tmp/c_invoice_linetax_v.csv
imp c_invoice_linetax_v "c_invoice_id INT, c_invoiceline_id INT, line INT, name TEXT, description TEXT, qtyinvoiced TEXT, qtyentered TEXT, uomsymbol TEXT, priceenteredlist TEXT, discount TEXT, priceentered TEXT, priceactual TEXT, linenetamt TEXT, taxamt TEXT, taxindicator TEXT, documentnote TEXT, resourcedescription TEXT, m_product_id INT"

echo "== verify (§-log) =="
sqlite3 "$DB" "SELECT '§PRINT-EXTRACT ad_printformat='||count(*)||' ad_printformatitem='||(SELECT count(*) FROM ad_printformatitem) FROM ad_printformat;"
sqlite3 "$DB" "SELECT '§PRINT-EXTRACT P-items='||count(*)||' (master-detail links)' FROM ad_printformatitem WHERE printformattype='P' AND isactive='Y';"
sqlite3 "$DB" "SELECT '§PRINT-EXTRACT header_v rows='||count(*)||' linetax_v rows='||(SELECT count(*) FROM c_invoice_linetax_v) FROM c_invoice_header_v;"
sqlite3 "$DB" "SELECT '§PRINT-EXTRACT format120='||(SELECT name FROM ad_printformat WHERE ad_printformat_id=120)||' -> child='||(SELECT ad_printformatchild_id FROM ad_printformatitem WHERE ad_printformat_id=120 AND printformattype='P' AND isactive='Y');"
