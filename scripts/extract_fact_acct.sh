#!/usr/bin/env bash
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
# ⚠ DO NOT REMOVE — Scope guard
# Scope: R2 source extract (prompts/CRUD_P_R_REPORT.md §R2, docs/CRUD_P_R_REPORT_SPEC.md §1.2.1) +
#   HARDEN_MATRIX H-1 oracle capture. Pull GardenWorld's REAL posted journal WITH per-document
#   granularity (ad_table_id, record_id, line_id) so post_resolver's derived lines can be diffed
#   PER DOCUMENT, not just on the aggregate balance — and the REAL *_Access grants so the access
#   surface can be oracle-diffed. Non-invent: a straight copy of executed facts. Deterministic + re-runnable.
#   Source DB note: client-11 fact_acct lives in `idempiere_test` (the `idempiere` DB shows 0 posted).
#   fact_reconciliation is 0 rows in the oracle (a process OUTPUT table) — captured as schema-only; the
#   4 AD_Rule (ruletype Q) SQLs run in-Postgres to produce their own oracle (separate step, PG dialect).
# Run:  bash scripts/extract_fact_acct.sh   (then: node scripts/test_report_fin.js — must balance 46574.97)
set -euo pipefail
CONTAINER=postgres
PGDB=idempiere_test
PGUSER=adempiere
DB=build/erp/glassbowl_data.db
PG() { docker exec "$CONTAINER" psql -U "$PGUSER" -d "$PGDB" -At --csv -c "$1"; }

echo "== extract fact_acct (client 11, GRANULAR) + c_elementvalue (client 0,11) from $CONTAINER:$PGDB =="
PG "
  SELECT fact_acct_id, ad_client_id, ad_org_id, c_acctschema_id, account_id, c_period_id,
         ad_table_id, record_id, line_id, gl_category_id, c_tax_id, postingtype, c_currency_id,
         round(amtsourcedr,2), round(amtsourcecr,2), round(amtacctdr,2), round(amtacctcr,2),
         round(qty,2), m_product_id, c_bpartner_id, replace(coalesce(description,''),chr(10),' ')
  FROM adempiere.fact_acct WHERE ad_client_id=11 ORDER BY fact_acct_id;" > /tmp/fact_acct.csv
PG "
  SELECT c_elementvalue_id, ad_client_id, value, name, accounttype, issummary
  FROM adempiere.c_elementvalue WHERE ad_client_id IN (0,11) ORDER BY c_elementvalue_id;" > /tmp/c_elementvalue.csv

echo "== load fact_acct + c_elementvalue into $DB (drop+recreate; -t suppressed the header, so NO --skip) =="
sqlite3 "$DB" "DROP TABLE IF EXISTS fact_acct; CREATE TABLE fact_acct(
  fact_acct_id INT, ad_client_id INT, ad_org_id INT, c_acctschema_id INT, account_id INT, c_period_id INT,
  ad_table_id INT, record_id INT, line_id INT, gl_category_id INT, c_tax_id INT, postingtype TEXT,
  c_currency_id INT, amtsourcedr REAL, amtsourcecr REAL, amtacctdr REAL, amtacctcr REAL,
  qty REAL, m_product_id INT, c_bpartner_id INT, description TEXT);"
sqlite3 "$DB" "DROP TABLE IF EXISTS c_elementvalue; CREATE TABLE c_elementvalue(c_elementvalue_id INT, ad_client_id INT, value TEXT, name TEXT, accounttype TEXT, issummary TEXT);"
sqlite3 "$DB" ".mode csv" ".import /tmp/fact_acct.csv fact_acct"
sqlite3 "$DB" ".mode csv" ".import /tmp/c_elementvalue.csv c_elementvalue"

echo "== capture the REAL *_Access grants (the access-surface oracle) =="
# Uniform grant tables: <pk>, ad_role_id, ad_client_id, ad_org_id, isactive, isreadwrite
for spec in \
  "ad_window_access:ad_window_id" \
  "ad_process_access:ad_process_id" \
  "ad_form_access:ad_form_id" \
  "ad_workflow_access:ad_workflow_id" \
  "ad_task_access:ad_task_id"; do
  t="${spec%%:*}"; pk="${spec##*:}"
  PG "SELECT $pk, ad_role_id, ad_client_id, ad_org_id, isactive, isreadwrite
      FROM adempiere.$t WHERE ad_client_id IN (0,11) ORDER BY ad_role_id, $pk;" > "/tmp/$t.csv"
  sqlite3 "$DB" "DROP TABLE IF EXISTS $t; CREATE TABLE $t($pk INT, ad_role_id INT, ad_client_id INT, ad_org_id INT, isactive TEXT, isreadwrite TEXT);"
  sqlite3 "$DB" ".mode csv" ".import /tmp/$t.csv $t"
done
# Doc-action access has a distinct shape (doctype + action ref) — capture faithfully
PG "SELECT c_doctype_id, ad_ref_list_id, ad_role_id, ad_client_id, ad_org_id, isactive
    FROM adempiere.ad_document_action_access WHERE ad_client_id IN (0,11) ORDER BY ad_role_id, c_doctype_id, ad_ref_list_id;" > /tmp/ad_document_action_access.csv
sqlite3 "$DB" "DROP TABLE IF EXISTS ad_document_action_access; CREATE TABLE ad_document_action_access(c_doctype_id INT, ad_ref_list_id INT, ad_role_id INT, ad_client_id INT, ad_org_id INT, isactive TEXT);"
sqlite3 "$DB" ".mode csv" ".import /tmp/ad_document_action_access.csv ad_document_action_access"

echo "== capture the acct-config + invoice tax (post_resolver deps, for H-1 per-document oracle diff) =="
# Each is a straight copy of real config from idempiere_test (client 0+11). post_resolver reads these to
# resolve {Master.Role} tokens → the SAME natural account iDempiere posted; the diff then proves equivalence.
cap() { # cap <table> <select-cols> <create-def>  — INT-typed keys to match the integer-stored source
  #   tables (c_invoice/m_product et al), so the whole resolve join-path is one storage class.
  local t="$1" cols="$2" def="$3"
  PG "SELECT $cols FROM adempiere.$t WHERE ad_client_id IN (0,11);" > "/tmp/$t.csv"
  sqlite3 "$DB" "DROP TABLE IF EXISTS $t;"
  sqlite3 "$DB" "CREATE TABLE $t($def);"
  sqlite3 "$DB" ".mode csv" ".import /tmp/$t.csv $t"
}
cap c_bp_customer_acct      "c_bpartner_id,c_acctschema_id,c_receivable_acct"      "c_bpartner_id INT,c_acctschema_id INT,c_receivable_acct INT"
# c_bp_vendor_acct.v_liability_acct = the AP-invoice payable (per-vendor, mirrors customer receivable). ADDITIVE.
cap c_bp_vendor_acct        "c_bpartner_id,c_acctschema_id,v_liability_acct"       "c_bpartner_id INT,c_acctschema_id INT,v_liability_acct INT"
# p_inventoryclearing_acct = the matched AP-invoice line DR (51400 Inventory Clearing, via product->category). ADDITIVE.
# p_averagecostvariance_acct added ADDITIVELY for the M_MatchInv avg-cost IPV split (variance share for qty NOT on hand).
cap m_product_category_acct "m_product_category_id,c_acctschema_id,p_revenue_acct,p_cogs_acct,p_asset_acct,p_inventoryclearing_acct,p_averagecostvariance_acct" "m_product_category_id INT,c_acctschema_id INT,p_revenue_acct INT,p_cogs_acct INT,p_asset_acct INT,p_inventoryclearing_acct INT,p_averagecostvariance_acct INT"
# t_credit_acct = input-VAT (AP tax DR), mirrors t_due_acct (output VAT). ADDITIVE.
cap c_tax_acct              "c_tax_id,c_acctschema_id,t_due_acct,t_credit_acct"    "c_tax_id INT,c_acctschema_id INT,t_due_acct INT,t_credit_acct INT"
cap c_validcombination      "c_validcombination_id,account_id"                    "c_validcombination_id INT,account_id INT"
cap c_acctschema_default    "c_acctschema_id"                                     "c_acctschema_id INT"
cap c_invoicetax            "c_invoice_id,c_tax_id,taxamt"                         "c_invoice_id INT,c_tax_id INT,taxamt REAL"
# Bank account acct-config: Doc_Payment posts a receipt DR {Bank.InTransit} / CR {Bank.UnallocatedCash} at payamt.
cap c_bankaccount_acct      "c_bankaccount_id,c_acctschema_id,b_intransit_acct,b_unallocatedcash_acct,b_paymentselect_acct,b_asset_acct" "c_bankaccount_id INT,c_acctschema_id INT,b_intransit_acct INT,b_unallocatedcash_acct INT,b_paymentselect_acct INT,b_asset_acct INT"

echo "== capture inventory + cost oracle (F-1 shipment posting Doc_InOut + StorageOnHand spine, prompts/FOLD_MODEL_LOGIC.md §F-1 1b-ii / step-2) =="
# Doc_InOut posts DR {Product.Cogs} / CR {Product.Asset} at the product's current cost. The accounts
# come from m_product_category_acct (p_cogs_acct/p_asset_acct, just added above); the AMOUNT = movementqty
# × m_cost.currentcostprice. m_storageonhand.qtyonhand is the on-hand oracle the StorageOnHand fold diffs.
cap m_cost          "m_product_id,c_acctschema_id,m_costtype_id,m_costelement_id,currentcostprice,cumulatedamt,cumulatedqty" "m_product_id INT,c_acctschema_id INT,m_costtype_id INT,m_costelement_id INT,currentcostprice REAL,cumulatedamt REAL,cumulatedqty REAL"
cap m_storageonhand "m_product_id,m_locator_id,m_attributesetinstance_id,qtyonhand"                                          "m_product_id INT,m_locator_id INT,m_attributesetinstance_id INT,qtyonhand REAL"
# m_costdetail = the cost-at-movement record. Doc_InOut posts a shipment line's COGS/Inventory at THIS
# amount (current m_cost has drifted — posting-time vs current, same class as the invoice amt-drift). The
# shipment posting amount = Σ|amt| per (m_inoutline_id, c_acctschema_id); accounts still come from the master.
cap m_costdetail    "m_costdetail_id,c_acctschema_id,m_product_id,m_inoutline_id,amt,qty"                                    "m_costdetail_id INT,c_acctschema_id INT,m_product_id INT,m_inoutline_id INT,amt REAL,qty REAL"

echo "== capture BOM recipes (backflush arm Δ-A — AutoBOMOrder recursive explosion oracle) =="
# Denormalised recipe: (parent product, component product, qty). A product IS a BOM iff it has rows here
# (parent_id=it) — presence of lines drives the recursion, no isbom flag needed. NON-INVENT: the real recipe.
PG "SELECT b.m_product_id, l.m_product_id, l.qtybom
    FROM adempiere.pp_product_bom b JOIN adempiere.pp_product_bomline l ON l.pp_product_bom_id=b.pp_product_bom_id
    WHERE b.ad_client_id=11 ORDER BY b.m_product_id, l.m_product_id;" > /tmp/m_product_bom.csv
sqlite3 "$DB" "DROP TABLE IF EXISTS m_product_bom; CREATE TABLE m_product_bom(parent_id INT, comp_id INT, qtybom REAL);"
sqlite3 "$DB" ".mode csv" ".import /tmp/m_product_bom.csv m_product_bom"
# include m_product_category_id — post_resolver hops product->category for {Product.*}; must not be dropped.
cap m_product "m_product_id,name,isbom,m_product_category_id" "m_product_id INT, name TEXT, isbom TEXT, m_product_category_id INT"

echo "== capture allocation oracle (Doc_AllocationHdr posting — Money DEEP half, fact_acct ad_table_id 735) =="
# Doc_AllocationHdr (SO-invoice branch) posts per line:
#   DR {Payment.UnallocatedCash}|{CashBook.CashTransfer} = amount · DR {BPGroup.PayDiscount} = discountamt ·
#   DR {BPGroup.WriteOff} = writeoffamt · CR {BPartner.Receivable} = amount+discount+writeoff;
#   then (C_AcctSchema.TaxCorrectionType!='N') a VAT tax-correction PER invoice tax line:
#     amount = round(tax/total * (discount|writeoff), 2) — DR {Tax.Due}/CR {discount|writeoff acct}.
#   The tax base (total + per-tax amounts) is READ from the invoice's OWN posted fact_acct(318) header lines
#   (line_id NULL) — already captured above, so the correction is a fold of real postings, not invented.
# NON-INVENT: every account RESOLVED from a real config column; amounts from real c_allocationline.
PG "SELECT c_allocationhdr_id, docstatus, c_doctype_id, dateacct FROM adempiere.c_allocationhdr WHERE ad_client_id=11 ORDER BY c_allocationhdr_id;" > /tmp/c_allocationhdr.csv
sqlite3 "$DB" "DROP TABLE IF EXISTS c_allocationhdr; CREATE TABLE c_allocationhdr(c_allocationhdr_id INT, docstatus TEXT, c_doctype_id INT, dateacct TEXT);"
sqlite3 "$DB" ".mode csv" ".import /tmp/c_allocationhdr.csv c_allocationhdr"
PG "SELECT c_allocationline_id, c_allocationhdr_id, c_invoice_id, c_payment_id, c_cashline_id, c_bpartner_id, round(amount,2), round(discountamt,2), round(writeoffamt,2) FROM adempiere.c_allocationline WHERE ad_client_id=11 ORDER BY c_allocationline_id;" > /tmp/c_allocationline.csv
sqlite3 "$DB" "DROP TABLE IF EXISTS c_allocationline; CREATE TABLE c_allocationline(c_allocationline_id INT, c_allocationhdr_id INT, c_invoice_id INT, c_payment_id INT, c_cashline_id INT, c_bpartner_id INT, amount REAL, discountamt REAL, writeoffamt REAL);"
sqlite3 "$DB" ".mode csv" ".import /tmp/c_allocationline.csv c_allocationline"
# acct-config the allocation resolver needs (the discount/writeoff/cash-transfer accounts + the tax-correction policy):
# costingmethod + m_costtype_id added ADDITIVELY for M_Movement cost selection (the schema's costing method picks
# the m_costelement, which keys m_cost.currentcostprice). NON-INVENT: real columns.
cap c_acctschema      "c_acctschema_id,c_currency_id,taxcorrectiontype,ispostifclearingequal,costingmethod,m_costtype_id"  "c_acctschema_id INT,c_currency_id INT,taxcorrectiontype TEXT,ispostifclearingequal TEXT,costingmethod TEXT,m_costtype_id INT"
# v_liability_acct + notinvoicedreceipts_acct added ADDITIVELY for the AP-invoice (purchase) manifest:
#   Doc_Invoice !IsSOTrx posts CR {BPGroup.V_Liability}=GrandTotal / DR {BPGroup.NotInvoicedReceipts}=linenet
#   (matched receipt lines). Both are BP-group accounts (vary by vendor's C_BP_Group). NON-INVENT: real columns.
cap c_bp_group_acct   "c_bp_group_id,c_acctschema_id,paydiscount_exp_acct,writeoff_acct,v_liability_acct,notinvoicedreceipts_acct"  "c_bp_group_id INT,c_acctschema_id INT,paydiscount_exp_acct INT,writeoff_acct INT,v_liability_acct INT,notinvoicedreceipts_acct INT"
cap c_cashbook_acct   "c_cashbook_id,c_acctschema_id,cb_cashtransfer_acct,cb_asset_acct,cb_receipt_acct" "c_cashbook_id INT,c_acctschema_id INT,cb_cashtransfer_acct INT,cb_asset_acct INT,cb_receipt_acct INT"
# bpartner->group hop (discount/writeoff acct is keyed by BP group) + cashline->cashbook hop (cash-transfer acct).
cap c_bpartner        "c_bpartner_id,c_bp_group_id"                                                     "c_bpartner_id INT,c_bp_group_id INT"
PG "SELECT cl.c_cashline_id, c.c_cashbook_id FROM adempiere.c_cashline cl JOIN adempiere.c_cash c ON c.c_cash_id=cl.c_cash_id WHERE cl.ad_client_id=11 ORDER BY cl.c_cashline_id;" > /tmp/c_cashline.csv
sqlite3 "$DB" "DROP TABLE IF EXISTS c_cashline; CREATE TABLE c_cashline(c_cashline_id INT, c_cashbook_id INT);"
sqlite3 "$DB" ".mode csv" ".import /tmp/c_cashline.csv c_cashline"
# FX allocation (schema-200000 EUR): the Spot conversion rate (multiplyrate, USD 100 -> EUR 102, type 114 IsDefault)
# + the CurrencyBalancing account that absorbs the per-doc rounding imbalance. ADDITIVE (H-1 balance preserved).
PG "SELECT c_currency_id, c_currency_id_to, c_conversiontype_id, multiplyrate, validfrom, validto FROM adempiere.c_conversion_rate WHERE ad_client_id IN (0,11) ORDER BY c_currency_id, c_currency_id_to, validfrom;" > /tmp/c_conversion_rate.csv
# multiplyrate kept as TEXT to preserve the exact PG NUMERIC decimal (REAL would float-drift the HALF_UP rounding).
sqlite3 "$DB" "DROP TABLE IF EXISTS c_conversion_rate; CREATE TABLE c_conversion_rate(c_currency_id INT, c_currency_id_to INT, c_conversiontype_id INT, multiplyrate TEXT, validfrom TEXT, validto TEXT);"
sqlite3 "$DB" ".mode csv" ".import /tmp/c_conversion_rate.csv c_conversion_rate"
# intercompany due-to/from added ADDITIVELY for the inter-org M_Movement posting (schema-level GL accounts).
cap c_acctschema_gl   "c_acctschema_id,currencybalancing_acct,intercompanydueto_acct,intercompanyduefrom_acct"  "c_acctschema_id INT,currencybalancing_acct INT,intercompanydueto_acct INT,intercompanyduefrom_acct INT"
# c_allocationhdr has NO C_ConversionType_ID → always the default Spot type; capture isdefault so the fold derives it.
cap c_conversiontype  "c_conversiontype_id,isdefault"           "c_conversiontype_id INT,isdefault TEXT"
# m_costelement.costingmethod maps the schema's costing method to the cost element that keys m_cost (Movement cost).
cap m_costelement     "m_costelement_id,costingmethod,costelementtype"  "m_costelement_id INT,costingmethod TEXT,costelementtype TEXT"

echo "== capture the inter-org M_Movement (FOLD_MODEL_LOGIC.md REMAINING-TAIL — M_Movement posting, fact_acct 323) =="
# Doc_Movement posts at the product's current cost (schema costing method → cost element → m_cost.currentcostprice).
# Inter-org (from-locator org ≠ to-locator org): DR/CR Product Asset (inventory in/out) + DR IntercompanyDueFrom /
# CR IntercompanyDueTo, each = round(movementqty × cost, 2). NON-INVENT: real lines + real cost + real config.
PG "SELECT m_movement_id, ad_org_id, docstatus FROM adempiere.m_movement WHERE ad_client_id=11 ORDER BY m_movement_id;" > /tmp/m_movement.csv
sqlite3 "$DB" "DROP TABLE IF EXISTS m_movement; CREATE TABLE m_movement(m_movement_id INT, ad_org_id INT, docstatus TEXT);"
sqlite3 "$DB" ".mode csv" ".import /tmp/m_movement.csv m_movement"
PG "SELECT m_movementline_id, m_movement_id, m_product_id, round(movementqty,2), m_locator_id, m_locatorto_id FROM adempiere.m_movementline WHERE ad_client_id=11 ORDER BY m_movementline_id;" > /tmp/m_movementline.csv
sqlite3 "$DB" "DROP TABLE IF EXISTS m_movementline; CREATE TABLE m_movementline(m_movementline_id INT, m_movement_id INT, m_product_id INT, movementqty REAL, m_locator_id INT, m_locatorto_id INT);"
sqlite3 "$DB" ".mode csv" ".import /tmp/m_movementline.csv m_movementline"
cap m_warehouse       "m_warehouse_id,ad_org_id"                "m_warehouse_id INT,ad_org_id INT"

echo "== capture the inventory MOVEMENT LEDGER (m_transaction — StorageOnHand qty spine, FOLD_MODEL_LOGIC.md §F-2 task #2) =="
# m_transaction is iDempiere's immutable per-movement ledger; MStorageOnHand.qtyonhand is maintained in lockstep
# by a SEPARATE code path. The fold reconstructs on-hand = Σ (MovementType-sign × |movementqty|) per
# (product,locator,asi) and diffs BOTH against the stored signed movementqty (sign rule) AND m_storageonhand
# (accumulation). The source-line ref columns (m_inoutline_id/m_inventoryline_id/m_movementline_id/
# m_productionline_id — exactly one set) attribute each movement to receipt/shipment/inventory/movement/production.
# NON-INVENT: the real ledger, verbatim (movementqty stored signed; we ALSO recompute the sign to prove the rule).
# movementdate added ADDITIVELY for the M_MatchInv avg-cost split — on-hand AT MATCH = Σqty up to the match date.
PG "SELECT m_transaction_id, m_product_id, m_locator_id, m_attributesetinstance_id, movementtype, round(movementqty,2),
           coalesce(m_inoutline_id,0), coalesce(m_inventoryline_id,0), coalesce(m_movementline_id,0), coalesce(m_productionline_id,0), movementdate::date
    FROM adempiere.m_transaction WHERE ad_client_id=11 ORDER BY m_transaction_id;" > /tmp/m_transaction.csv
sqlite3 "$DB" "DROP TABLE IF EXISTS m_transaction; CREATE TABLE m_transaction(m_transaction_id INT, m_product_id INT, m_locator_id INT, m_attributesetinstance_id INT, movementtype TEXT, movementqty REAL, m_inoutline_id INT, m_inventoryline_id INT, m_movementline_id INT, m_productionline_id INT, movementdate TEXT);"
sqlite3 "$DB" ".mode csv" ".import /tmp/m_transaction.csv m_transaction"

echo "== capture replenishment config (Δ-B replenishment PO — ReplenishReport, FOLD_MODEL_LOGIC.md §F-2 task #3) =="
# ReplenishReport.QtyToOrder formula (lines 294-327): per (product,warehouse) with ReplenishType<>'0',
#   available = QtyOnHand - QtyReserved + QtyOrdered  (reserved/ordered = M_StorageReservation IsSOTrx Y/N)
#   type '1' (reorder-below-min): order Level_Max-available  IFF available<=Level_Min, else 0
#   type '2' (maintain-max):      order Level_Max-available  always
#   then drop rows with QtyToOrder<1. The fold derives QtyOnHand by folding m_transaction (the §F-2 #2 spine)
#   per product WITHIN the warehouse's locators (m_locator→warehouse map — loc 102 is a DIFFERENT warehouse).
cap m_replenish          "m_product_id,m_warehouse_id,replenishtype,level_min,level_max"  "m_product_id INT,m_warehouse_id INT,replenishtype TEXT,level_min REAL,level_max REAL"
cap m_storagereservation "m_product_id,m_warehouse_id,round(qty,2),issotrx"               "m_product_id INT,m_warehouse_id INT,qty REAL,issotrx TEXT"
cap m_locator            "m_locator_id,m_warehouse_id"                                     "m_locator_id INT,m_warehouse_id INT"

echo "== capture M_MatchInv oracle (MInvoice.completeIt PO-side delta — invoice-line ⋈ receipt-line junction) =="
# One M_MatchInv per vendor-invoice line that references a material receipt; the fold's completeInvoice emits
# exactly this set. Oracle = the real junctions (with the invoice they belong to, via c_invoiceline).
# po_price (c_orderline.priceactual via the receipt's order line) + inv_price (c_invoiceline.priceactual) added
# ADDITIVELY for the M_MatchInv POSTING fold: DR NotInvoicedReceipts = matchqty×po_price / CR InventoryClearing =
# matchqty×inv_price; their difference is the Invoice Price Variance (avg-cost split). NON-INVENT: real priceactual.
PG "SELECT mi.m_matchinv_id, mi.c_invoiceline_id, mi.m_inoutline_id, mi.m_product_id, round(mi.qty,2), il.c_invoice_id,
       round(ol.priceactual,4), round(il.priceactual,4)
    FROM adempiere.m_matchinv mi JOIN adempiere.c_invoiceline il ON il.c_invoiceline_id=mi.c_invoiceline_id
    JOIN adempiere.m_inoutline iol ON iol.m_inoutline_id=mi.m_inoutline_id
    LEFT JOIN adempiere.c_orderline ol ON ol.c_orderline_id=iol.c_orderline_id
    WHERE mi.ad_client_id=11 ORDER BY mi.m_matchinv_id;" > /tmp/m_matchinv.csv
sqlite3 "$DB" "DROP TABLE IF EXISTS m_matchinv; CREATE TABLE m_matchinv(m_matchinv_id INT, c_invoiceline_id INT, m_inoutline_id INT, m_product_id INT, qty REAL, c_invoice_id INT, po_price REAL, inv_price REAL);"
sqlite3 "$DB" ".mode csv" ".import /tmp/m_matchinv.csv m_matchinv"
# NOTE: c_invoiceline is NOT re-captured here — the full table (incl. c_invoiceline_id/m_inoutline_id/qtyinvoiced
# that completeInvoice reads) is already present from the base seed; a narrow re-cap would drop columns siblings need.

echo "== capture GL_Journal source (Doc_GLJournal posting — manual journal incl. inter-org balancing, fact_acct 224) =="
# A GL journal's lines post DIRECTLY as fact lines: amtacct = round(amtsource × currencyrate, 2) (the journal is
# entered in the schema's own currency here, so rate=1). The non-trivial half is Fact.balanceAccounting: GardenWorld's
# journal 100 is INTER-ORG (DR Checking@org11 / CR Checking@org12, same natural account 508 via different
# C_ValidCombinations) so each org is balanced by an Intercompany Due-To/From line — the SAME schema-GL accounts +
# rule proven in W-FOLD-MOVEMENT (c_acctschema_gl.intercompanydueto/duefrom_acct → 600/741). We carry the line's
# C_ValidCombination ORG (the fact line's ad_org_id) so the fold can balance per org. NON-INVENT: real journal rows.
PG "SELECT gl_journal_id, c_acctschema_id, c_currency_id, c_conversiontype_id, dateacct::date, postingtype, docstatus
    FROM adempiere.gl_journal WHERE ad_client_id=11 ORDER BY gl_journal_id;" > /tmp/gl_journal.csv
sqlite3 "$DB" "DROP TABLE IF EXISTS gl_journal; CREATE TABLE gl_journal(gl_journal_id INT, c_acctschema_id INT, c_currency_id INT, c_conversiontype_id INT, dateacct TEXT, postingtype TEXT, docstatus TEXT);"
sqlite3 "$DB" ".mode csv" ".import /tmp/gl_journal.csv gl_journal"
# currencyrate kept as TEXT (exact decimal, like multiplyrate); account_id + ad_org_id come from the line's
# C_ValidCombination (the posted fact dimension), falling back to the line's own columns if no combination.
PG "SELECT jl.gl_journalline_id, jl.gl_journal_id, jl.line, coalesce(vc.account_id, jl.account_id),
       coalesce(vc.ad_org_id, jl.ad_org_id), jl.currencyrate,
       round(jl.amtsourcedr,2), round(jl.amtsourcecr,2), round(jl.amtacctdr,2), round(jl.amtacctcr,2)
    FROM adempiere.gl_journalline jl LEFT JOIN adempiere.c_validcombination vc ON vc.c_validcombination_id=jl.c_validcombination_id
    WHERE jl.ad_client_id=11 ORDER BY jl.gl_journal_id, jl.line;" > /tmp/gl_journalline.csv
sqlite3 "$DB" "DROP TABLE IF EXISTS gl_journalline; CREATE TABLE gl_journalline(gl_journalline_id INT, gl_journal_id INT, line INT, account_id INT, ad_org_id INT, currencyrate TEXT, amtsourcedr REAL, amtsourcecr REAL, amtacctdr REAL, amtacctcr REAL);"
sqlite3 "$DB" ".mode csv" ".import /tmp/gl_journalline.csv gl_journalline"

echo "== verify =="
sqlite3 "$DB" "SELECT '§EXTRACT fact_acct rows='||count(*)||' docs='||count(DISTINCT ad_table_id||'/'||record_id)||' Dr='||round(sum(amtacctdr),2)||' Cr='||round(sum(amtacctcr),2)||' diff='||round(sum(amtacctdr-amtacctcr),2) FROM fact_acct;"
sqlite3 "$DB" "SELECT '§EXTRACT c_elementvalue rows='||count(*) FROM c_elementvalue;"
for t in ad_window_access ad_process_access ad_form_access ad_workflow_access ad_task_access ad_document_action_access; do
  sqlite3 "$DB" "SELECT '§EXTRACT $t rows='||count(*) FROM $t;"
done
sqlite3 "$DB" "SELECT '§EXTRACT m_product_category_acct cogs/asset non-null='||sum(CASE WHEN p_cogs_acct IS NOT NULL THEN 1 ELSE 0 END)||'/'||sum(CASE WHEN p_asset_acct IS NOT NULL THEN 1 ELSE 0 END)||' of '||count(*) FROM m_product_category_acct;"
sqlite3 "$DB" "SELECT '§EXTRACT m_cost rows='||count(*)||' nonzero-cost='||sum(CASE WHEN currentcostprice>0 THEN 1 ELSE 0 END) FROM m_cost;"
sqlite3 "$DB" "SELECT '§EXTRACT m_storageonhand rows='||count(*)||' products='||count(DISTINCT m_product_id)||' Σqtyonhand='||round(sum(qtyonhand),2) FROM m_storageonhand;"
sqlite3 "$DB" "SELECT '§EXTRACT m_costdetail rows='||count(*)||' shipment-lines='||count(DISTINCT CASE WHEN m_inoutline_id IS NOT NULL THEN m_inoutline_id END) FROM m_costdetail;"
sqlite3 "$DB" "SELECT '§EXTRACT m_product_bom rows='||count(*)||' parents='||count(DISTINCT parent_id)||' nested-parents='||(SELECT count(DISTINCT parent_id) FROM m_product_bom WHERE comp_id IN (SELECT DISTINCT parent_id FROM m_product_bom)) FROM m_product_bom;"
sqlite3 "$DB" "SELECT '§EXTRACT c_allocationhdr='||count(*)||' lines='||(SELECT count(*) FROM c_allocationline)||' withDisc/WO='||(SELECT count(*) FROM c_allocationline WHERE discountamt<>0 OR writeoffamt<>0) FROM c_allocationhdr;"
sqlite3 "$DB" "SELECT '§EXTRACT alloc-oracle fact_acct(735) rows='||count(*)||' docs='||count(DISTINCT record_id)||' Dr='||round(sum(amtacctdr),2)||' Cr='||round(sum(amtacctcr),2) FROM fact_acct WHERE ad_table_id=735;"
sqlite3 "$DB" "SELECT '§EXTRACT c_acctschema taxcorrectiontype(101)='||taxcorrectiontype FROM c_acctschema WHERE c_acctschema_id=101;"
sqlite3 "$DB" "SELECT '§EXTRACT c_bp_group_acct='||count(*)||' c_cashbook_acct='||(SELECT count(*) FROM c_cashbook_acct)||' c_cashline='||(SELECT count(*) FROM c_cashline) FROM c_bp_group_acct;"
sqlite3 "$DB" "SELECT '§EXTRACT m_transaction rows='||count(*)||' (p,l,asi)-cells='||count(DISTINCT m_product_id||'/'||m_locator_id||'/'||m_attributesetinstance_id)||' Σsigned='||round(sum(movementqty),2)||' types='||(SELECT group_concat(DISTINCT movementtype) FROM m_transaction) FROM m_transaction;"
sqlite3 "$DB" "SELECT '§EXTRACT m_replenish='||count(*)||' active(type<>0)='||sum(CASE WHEN replenishtype<>'0' THEN 1 ELSE 0 END)||' m_locator='||(SELECT count(*) FROM m_locator)||' reservations='||(SELECT count(*) FROM m_storagereservation) FROM m_replenish;"
sqlite3 "$DB" "SELECT '§EXTRACT m_matchinv rows='||count(*)||' PO-invoices='||count(DISTINCT c_invoice_id) FROM m_matchinv;"
sqlite3 "$DB" "SELECT '§EXTRACT gl_journal='||count(*)||' lines='||(SELECT count(*) FROM gl_journalline)||' orgs='||(SELECT count(DISTINCT ad_org_id) FROM gl_journalline)||' fact_acct(224)='||(SELECT count(*) FROM fact_acct WHERE ad_table_id=224) FROM gl_journal;"
