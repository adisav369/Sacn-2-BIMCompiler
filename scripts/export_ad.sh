#!/bin/bash
# export_ad.sh — Export iDempiere AD metadata + GardenWorld data to SQLite
# Source: docker postgres container with idempiere DB
# Output: deploy/dev/ad_seed.sql
# Usage: bash scripts/export_ad.sh
#
# Exports:
#   1. AD metadata tables (AD_Menu, AD_Window, AD_Tab, AD_Field, AD_Column, etc.)
#   2. GardenWorld client data (C_BPartner, M_Product, C_Project, etc.)
#   3. System reference data (AD_Ref_List, AD_Reference, AD_Element)
#
# Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.

set -e

CONTAINER=postgres
DB=idempiere
USER=adempiere
OUTPUT=deploy/dev/ad_seed.sql
TMPDIR=/tmp/ad_export

echo "§EXPORT starting iDempiere AD export..."
echo "§EXPORT container=$CONTAINER db=$DB output=$OUTPUT"

# Ensure postgres is running
docker start $CONTAINER >/dev/null 2>&1 || true
sleep 1

# Verify connection
docker exec $CONTAINER psql -U $USER -d $DB -c "SELECT 1" >/dev/null 2>&1 || {
    echo "§EXPORT ERROR: cannot connect to $CONTAINER/$DB"; exit 1;
}

mkdir -p $TMPDIR
rm -f $TMPDIR/*.csv

# ── Function: export a table with selected columns ────────────────────

export_table() {
    local TABLE=$1
    local COLUMNS=$2
    local WHERE=${3:-"1=1"}
    local OUTFILE=$TMPDIR/${TABLE}.csv

    echo "§EXPORT exporting $TABLE WHERE $WHERE ..."
    docker exec $CONTAINER psql -U $USER -d $DB \
        -c "COPY (SELECT $COLUMNS FROM $TABLE WHERE $WHERE AND IsActive='Y') TO STDOUT WITH (FORMAT csv, HEADER true, NULL '')" \
        > "$OUTFILE" 2>/dev/null

    local COUNT=$(wc -l < "$OUTFILE")
    COUNT=$((COUNT - 1))  # subtract header
    echo "§EXPORT $TABLE rows=$COUNT"
}

# ── Export AD metadata tables ─────────────────────────────────────────

export_table "AD_Menu" \
    "AD_Menu_ID, Name, Description, IsSummary, Action, AD_Window_ID, AD_Process_ID, AD_Form_ID, IsActive"

export_table "AD_TreeNodeMM" \
    "AD_Tree_ID, Node_ID, Parent_ID, SeqNo, IsActive" \
    "AD_Tree_ID = 10"

export_table "AD_Window" \
    "AD_Window_ID, Name, Description, Help, WindowType, IsActive"

export_table "AD_Tab" \
    "AD_Tab_ID, AD_Window_ID, Name, Description, Help, AD_Table_ID, TabLevel, SeqNo, IsSingleRow, IsReadOnly, WhereClause, OrderByClause, IsActive"

export_table "AD_Field" \
    "AD_Field_ID, AD_Tab_ID, AD_Column_ID, Name, Description, Help, SeqNo, IsDisplayed, DisplayLogic, IsMandatory, IsReadOnly, DefaultValue, IsActive"

export_table "AD_Column" \
    "AD_Column_ID, AD_Table_ID, ColumnName, Name, Description, AD_Reference_ID, AD_Val_Rule_ID, FieldLength, IsMandatory, IsKey, IsIdentifier, DefaultValue, ValueMin, ValueMax, IsActive"

export_table "AD_Table" \
    "AD_Table_ID, TableName, Name, Description, AD_Window_ID, IsActive"

export_table "AD_Reference" \
    "AD_Reference_ID, Name, Description, ValidationType, IsActive"

export_table "AD_Ref_List" \
    "AD_Ref_List_ID, AD_Reference_ID, Value, Name, Description, IsActive"

export_table "AD_Element" \
    "AD_Element_ID, ColumnName, Name, PrintName, Description, IsActive"

# ── Export GardenWorld data tables (AD_Client_ID = 11) ────────────────

echo ""
echo "§EXPORT exporting GardenWorld data (AD_Client_ID=11)..."

export_table "C_BPartner" \
    "C_BPartner_ID, AD_Client_ID, AD_Org_ID, Value, Name, Name2, Description, IsCustomer, IsVendor, IsEmployee, C_BP_Group_ID, IsActive" \
    "AD_Client_ID = 11"

export_table "C_BPartner_Location" \
    "C_BPartner_Location_ID, AD_Client_ID, C_BPartner_ID, Name, Phone, Fax, IsActive" \
    "AD_Client_ID = 11"

export_table "AD_User" \
    "AD_User_ID, AD_Client_ID, AD_Org_ID, Name, Description, EMail, Phone, C_BPartner_ID, IsActive" \
    "AD_Client_ID IN (0, 11)"

export_table "M_Product" \
    "M_Product_ID, AD_Client_ID, AD_Org_ID, Value, Name, Description, M_Product_Category_ID, ProductType, C_UOM_ID, IsActive" \
    "AD_Client_ID = 11"

export_table "M_Product_Category" \
    "M_Product_Category_ID, AD_Client_ID, Name, Description, Value, IsActive" \
    "AD_Client_ID = 11"

export_table "M_ProductPrice" \
    "M_Product_ID, M_PriceList_Version_ID, PriceList, PriceStd, PriceLimit, IsActive" \
    "AD_Client_ID = 11"

export_table "C_Project" \
    "C_Project_ID, AD_Client_ID, AD_Org_ID, Value, Name, Description, C_BPartner_ID, DateContract, DateFinish, PlannedAmt, CommittedAmt, Processed, IsActive" \
    "AD_Client_ID = 11"

# ── R2: Full GardenWorld data tables ─────────────────────────────────

echo ""
echo "§EXPORT R2: exporting full GardenWorld transactional + master data..."

# -- Reference / Lookup tables --

export_table "C_Currency" \
    "C_Currency_ID, ISO_Code, CurSymbol, Description, StdPrecision, CostingPrecision, IsActive"

export_table "C_UOM" \
    "C_UOM_ID, X12DE355, Name, Description, StdPrecision, CostingPrecision, IsActive"

export_table "C_BP_Group" \
    "C_BP_Group_ID, AD_Client_ID, AD_Org_ID, Value, Name, Description, IsActive" \
    "AD_Client_ID = 11"

export_table "C_TaxCategory" \
    "C_TaxCategory_ID, AD_Client_ID, Name, Description, IsActive" \
    "AD_Client_ID IN (0, 11)"

export_table "C_Tax" \
    "C_Tax_ID, AD_Client_ID, AD_Org_ID, Name, Description, C_TaxCategory_ID, Rate, IsActive" \
    "AD_Client_ID IN (0, 11)"

export_table "C_DocType" \
    "C_DocType_ID, AD_Client_ID, AD_Org_ID, Name, PrintName, Description, DocBaseType, IsSOTrx, IsActive" \
    "AD_Client_ID IN (0, 11)"

export_table "C_Charge" \
    "C_Charge_ID, AD_Client_ID, AD_Org_ID, Name, Description, ChargeAmt, IsActive" \
    "AD_Client_ID = 11"

# -- Geography --

export_table "C_Country" \
    "C_Country_ID, Name, CountryCode, Description, IsActive"

export_table "C_Region" \
    "C_Region_ID, C_Country_ID, Name, Description, IsActive"

export_table "C_Location" \
    "C_Location_ID, AD_Client_ID, AD_Org_ID, Address1, Address2, City, Postal, C_Country_ID, C_Region_ID, IsActive" \
    "AD_Client_ID = 11"

# -- Pricing --

export_table "M_PriceList" \
    "M_PriceList_ID, AD_Client_ID, AD_Org_ID, Name, Description, C_Currency_ID, IsSOPriceList, IsActive" \
    "AD_Client_ID = 11"

export_table "M_PriceList_Version" \
    "M_PriceList_Version_ID, AD_Client_ID, AD_Org_ID, M_PriceList_ID, Name, Description, ValidFrom, IsActive" \
    "AD_Client_ID = 11"

# -- Sales / Purchase orders --

export_table "C_Order" \
    "C_Order_ID, AD_Client_ID, AD_Org_ID, DocumentNo, DocStatus, IsSOTrx, DateOrdered, DatePromised, C_BPartner_ID, C_BPartner_Location_ID, C_DocType_ID, C_Currency_ID, GrandTotal, TotalLines, Description, IsActive" \
    "AD_Client_ID = 11"

export_table "C_OrderLine" \
    "C_OrderLine_ID, AD_Client_ID, AD_Org_ID, C_Order_ID, Line, M_Product_ID, QtyOrdered, QtyDelivered, QtyInvoiced, PriceActual, PriceList, LineNetAmt, C_Tax_ID, C_UOM_ID, Description, IsActive" \
    "AD_Client_ID = 11"

# -- Invoices --

export_table "C_Invoice" \
    "C_Invoice_ID, AD_Client_ID, AD_Org_ID, DocumentNo, DocStatus, IsSOTrx, DateInvoiced, DateAcct, C_BPartner_ID, C_BPartner_Location_ID, C_DocType_ID, C_Currency_ID, GrandTotal, TotalLines, Description, C_Order_ID, IsActive" \
    "AD_Client_ID = 11"

export_table "C_InvoiceLine" \
    "C_InvoiceLine_ID, AD_Client_ID, AD_Org_ID, C_Invoice_ID, Line, M_Product_ID, QtyInvoiced, PriceActual, PriceList, LineNetAmt, C_Tax_ID, C_UOM_ID, Description, C_OrderLine_ID, IsActive" \
    "AD_Client_ID = 11"

# -- Payments --

export_table "C_Payment" \
    "C_Payment_ID, AD_Client_ID, AD_Org_ID, DocumentNo, DocStatus, DateTrx, DateAcct, C_BPartner_ID, C_DocType_ID, C_Currency_ID, PayAmt, TenderType, IsReceipt, Description, C_Invoice_ID, IsActive" \
    "AD_Client_ID = 11"

export_table "C_BankStatement" \
    "C_BankStatement_ID, AD_Client_ID, AD_Org_ID, Name, DocStatus, StatementDate, BeginningBalance, EndingBalance, Description, IsActive" \
    "AD_Client_ID = 11"

export_table "C_BankStatementLine" \
    "C_BankStatementLine_ID, AD_Client_ID, AD_Org_ID, C_BankStatement_ID, Line, DateAcct, TrxAmt, StmtAmt, C_Payment_ID, C_BPartner_ID, Description, IsActive" \
    "AD_Client_ID = 11"

# -- Inventory / Warehouse --

export_table "M_Warehouse" \
    "M_Warehouse_ID, AD_Client_ID, AD_Org_ID, Value, Name, Description, C_Location_ID, IsActive" \
    "AD_Client_ID = 11"

export_table "M_Locator" \
    "M_Locator_ID, AD_Client_ID, AD_Org_ID, M_Warehouse_ID, Value, X, Y, Z, IsDefault, IsActive" \
    "AD_Client_ID = 11"

export_table "M_Storage" \
    "M_Locator_ID, M_Product_ID, QtyOnHand, QtyReserved, QtyOrdered, IsActive" \
    "AD_Client_ID = 11"

# -- Shipments (Material Receipts / Deliveries) --

export_table "M_InOut" \
    "M_InOut_ID, AD_Client_ID, AD_Org_ID, DocumentNo, DocStatus, IsSOTrx, MovementDate, DateAcct, C_BPartner_ID, C_BPartner_Location_ID, M_Warehouse_ID, C_DocType_ID, C_Order_ID, Description, IsActive" \
    "AD_Client_ID = 11"

export_table "M_InOutLine" \
    "M_InOutLine_ID, AD_Client_ID, AD_Org_ID, M_InOut_ID, Line, M_Product_ID, M_Locator_ID, QtyEntered, MovementQty, C_UOM_ID, C_OrderLine_ID, Description, IsActive" \
    "AD_Client_ID = 11"

# -- Accounting --

export_table "C_ElementValue" \
    "C_ElementValue_ID, AD_Client_ID, AD_Org_ID, Value, Name, Description, AccountType, AccountSign, IsSummary, PostActual, PostBudget, IsActive" \
    "AD_Client_ID = 11"

export_table "C_AcctSchema" \
    "C_AcctSchema_ID, AD_Client_ID, AD_Org_ID, Name, Description, C_Currency_ID, IsActive" \
    "AD_Client_ID = 11"

# -- Projects (phases and tasks) --

export_table "C_ProjectPhase" \
    "C_ProjectPhase_ID, AD_Client_ID, AD_Org_ID, C_Project_ID, Name, Description, SeqNo, PlannedAmt, CommittedAmt, IsActive" \
    "AD_Client_ID = 11"

export_table "C_ProjectTask" \
    "C_ProjectTask_ID, AD_Client_ID, AD_Org_ID, C_ProjectPhase_ID, Name, Description, SeqNo, PlannedAmt, CommittedAmt, IsActive" \
    "AD_Client_ID = 11"

# ── Convert CSV to SQLite INSERTs ─────────────────────────────────────

echo ""
echo "§EXPORT converting CSV to SQLite INSERTs..."

python3 -c "
import csv, os, sys, glob

outfile = '$OUTPUT'
tmpdir = '$TMPDIR'

with open(outfile, 'w') as out:
    out.write('-- ad_seed.sql — iDempiere AD metadata + GardenWorld data\n')
    out.write('-- Auto-generated by scripts/export_ad.sh\n')
    out.write('-- Source: docker postgres / idempiere DB\n')
    out.write('-- Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.\n\n')

    # Schema DDL first
    schemas = {
        'AD_Menu': 'CREATE TABLE IF NOT EXISTS AD_Menu (AD_Menu_ID INTEGER PRIMARY KEY, Name TEXT NOT NULL, Description TEXT, IsSummary TEXT DEFAULT \"N\", Action TEXT, AD_Window_ID INTEGER, AD_Process_ID INTEGER, AD_Form_ID INTEGER, IsActive TEXT DEFAULT \"Y\")',
        'AD_TreeNodeMM': 'CREATE TABLE IF NOT EXISTS AD_TreeNodeMM (AD_Tree_ID INTEGER NOT NULL, Node_ID INTEGER NOT NULL, Parent_ID INTEGER NOT NULL, SeqNo INTEGER DEFAULT 0, IsActive TEXT DEFAULT \"Y\", PRIMARY KEY (AD_Tree_ID, Node_ID))',
        'AD_Window': 'CREATE TABLE IF NOT EXISTS AD_Window (AD_Window_ID INTEGER PRIMARY KEY, Name TEXT NOT NULL, Description TEXT, Help TEXT, WindowType TEXT DEFAULT \"M\", IsActive TEXT DEFAULT \"Y\")',
        'AD_Tab': 'CREATE TABLE IF NOT EXISTS AD_Tab (AD_Tab_ID INTEGER PRIMARY KEY, AD_Window_ID INTEGER NOT NULL, Name TEXT NOT NULL, Description TEXT, Help TEXT, AD_Table_ID INTEGER NOT NULL, TabLevel INTEGER DEFAULT 0, SeqNo INTEGER DEFAULT 10, IsSingleRow TEXT DEFAULT \"N\", IsReadOnly TEXT DEFAULT \"N\", WhereClause TEXT, OrderByClause TEXT, IsActive TEXT DEFAULT \"Y\")',
        'AD_Field': 'CREATE TABLE IF NOT EXISTS AD_Field (AD_Field_ID INTEGER PRIMARY KEY, AD_Tab_ID INTEGER NOT NULL, AD_Column_ID INTEGER NOT NULL, Name TEXT NOT NULL, Description TEXT, Help TEXT, SeqNo INTEGER DEFAULT 10, IsDisplayed TEXT DEFAULT \"Y\", DisplayLogic TEXT, IsMandatory TEXT, IsReadOnly TEXT DEFAULT \"N\", DefaultValue TEXT, IsActive TEXT DEFAULT \"Y\")',
        'AD_Column': 'CREATE TABLE IF NOT EXISTS AD_Column (AD_Column_ID INTEGER PRIMARY KEY, AD_Table_ID INTEGER NOT NULL, ColumnName TEXT NOT NULL, Name TEXT, Description TEXT, AD_Reference_ID INTEGER, AD_Val_Rule_ID INTEGER, FieldLength INTEGER DEFAULT 0, IsMandatory TEXT DEFAULT \"N\", IsKey TEXT DEFAULT \"N\", IsIdentifier TEXT DEFAULT \"N\", DefaultValue TEXT, ValueMin TEXT, ValueMax TEXT, IsActive TEXT DEFAULT \"Y\")',
        'AD_Table': 'CREATE TABLE IF NOT EXISTS AD_Table (AD_Table_ID INTEGER PRIMARY KEY, TableName TEXT NOT NULL, Name TEXT, Description TEXT, AD_Window_ID INTEGER, IsActive TEXT DEFAULT \"Y\")',
        'AD_Reference': 'CREATE TABLE IF NOT EXISTS AD_Reference (AD_Reference_ID INTEGER PRIMARY KEY, Name TEXT NOT NULL, Description TEXT, ValidationType TEXT, IsActive TEXT DEFAULT \"Y\")',
        'AD_Ref_List': 'CREATE TABLE IF NOT EXISTS AD_Ref_List (AD_Ref_List_ID INTEGER PRIMARY KEY, AD_Reference_ID INTEGER NOT NULL, Value TEXT NOT NULL, Name TEXT NOT NULL, Description TEXT, IsActive TEXT DEFAULT \"Y\")',
        'AD_Element': 'CREATE TABLE IF NOT EXISTS AD_Element (AD_Element_ID INTEGER PRIMARY KEY, ColumnName TEXT, Name TEXT NOT NULL, PrintName TEXT, Description TEXT, IsActive TEXT DEFAULT \"Y\")',
        'C_BPartner': 'CREATE TABLE IF NOT EXISTS C_BPartner (C_BPartner_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, AD_Org_ID INTEGER, Value TEXT, Name TEXT NOT NULL, Name2 TEXT, Description TEXT, IsCustomer TEXT DEFAULT \"N\", IsVendor TEXT DEFAULT \"N\", IsEmployee TEXT DEFAULT \"N\", C_BP_Group_ID INTEGER, IsActive TEXT DEFAULT \"Y\")',
        'C_BPartner_Location': 'CREATE TABLE IF NOT EXISTS C_BPartner_Location (C_BPartner_Location_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, C_BPartner_ID INTEGER, Name TEXT, Phone TEXT, Fax TEXT, IsActive TEXT DEFAULT \"Y\")',
        'AD_User': 'CREATE TABLE IF NOT EXISTS AD_User (AD_User_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, AD_Org_ID INTEGER, Name TEXT NOT NULL, Description TEXT, EMail TEXT, Phone TEXT, C_BPartner_ID INTEGER, IsActive TEXT DEFAULT \"Y\")',
        'M_Product': 'CREATE TABLE IF NOT EXISTS M_Product (M_Product_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, AD_Org_ID INTEGER, Value TEXT, Name TEXT NOT NULL, Description TEXT, M_Product_Category_ID INTEGER, ProductType TEXT, C_UOM_ID INTEGER, IsActive TEXT DEFAULT \"Y\")',
        'M_Product_Category': 'CREATE TABLE IF NOT EXISTS M_Product_Category (M_Product_Category_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, Name TEXT NOT NULL, Description TEXT, Value TEXT, IsActive TEXT DEFAULT \"Y\")',
        'M_ProductPrice': 'CREATE TABLE IF NOT EXISTS M_ProductPrice (M_Product_ID INTEGER NOT NULL, M_PriceList_Version_ID INTEGER NOT NULL, PriceList REAL, PriceStd REAL, PriceLimit REAL, IsActive TEXT DEFAULT \"Y\", PRIMARY KEY (M_Product_ID, M_PriceList_Version_ID))',
        'C_Project': 'CREATE TABLE IF NOT EXISTS C_Project (C_Project_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, AD_Org_ID INTEGER, Value TEXT, Name TEXT NOT NULL, Description TEXT, C_BPartner_ID INTEGER, DateContract TEXT, DateFinish TEXT, PlannedAmt REAL, CommittedAmt REAL, Processed TEXT DEFAULT \"N\", IsActive TEXT DEFAULT \"Y\")',
        'C_Currency': 'CREATE TABLE IF NOT EXISTS C_Currency (C_Currency_ID INTEGER PRIMARY KEY, ISO_Code TEXT NOT NULL, CurSymbol TEXT, Description TEXT, StdPrecision INTEGER DEFAULT 2, CostingPrecision INTEGER DEFAULT 4, IsActive TEXT DEFAULT \"Y\")',
        'C_UOM': 'CREATE TABLE IF NOT EXISTS C_UOM (C_UOM_ID INTEGER PRIMARY KEY, X12DE355 TEXT, Name TEXT NOT NULL, Description TEXT, StdPrecision INTEGER DEFAULT 2, CostingPrecision INTEGER DEFAULT 6, IsActive TEXT DEFAULT \"Y\")',
        'C_BP_Group': 'CREATE TABLE IF NOT EXISTS C_BP_Group (C_BP_Group_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, AD_Org_ID INTEGER, Value TEXT, Name TEXT NOT NULL, Description TEXT, IsActive TEXT DEFAULT \"Y\")',
        'C_TaxCategory': 'CREATE TABLE IF NOT EXISTS C_TaxCategory (C_TaxCategory_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, Name TEXT NOT NULL, Description TEXT, IsActive TEXT DEFAULT \"Y\")',
        'C_Tax': 'CREATE TABLE IF NOT EXISTS C_Tax (C_Tax_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, AD_Org_ID INTEGER, Name TEXT NOT NULL, Description TEXT, C_TaxCategory_ID INTEGER, Rate REAL DEFAULT 0, IsActive TEXT DEFAULT \"Y\")',
        'C_DocType': 'CREATE TABLE IF NOT EXISTS C_DocType (C_DocType_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, AD_Org_ID INTEGER, Name TEXT NOT NULL, PrintName TEXT, Description TEXT, DocBaseType TEXT, IsSOTrx TEXT DEFAULT \"Y\", IsActive TEXT DEFAULT \"Y\")',
        'C_Charge': 'CREATE TABLE IF NOT EXISTS C_Charge (C_Charge_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, AD_Org_ID INTEGER, Name TEXT NOT NULL, Description TEXT, ChargeAmt REAL DEFAULT 0, IsActive TEXT DEFAULT \"Y\")',
        'C_Country': 'CREATE TABLE IF NOT EXISTS C_Country (C_Country_ID INTEGER PRIMARY KEY, Name TEXT NOT NULL, CountryCode TEXT, Description TEXT, IsActive TEXT DEFAULT \"Y\")',
        'C_Region': 'CREATE TABLE IF NOT EXISTS C_Region (C_Region_ID INTEGER PRIMARY KEY, C_Country_ID INTEGER, Name TEXT NOT NULL, Description TEXT, IsActive TEXT DEFAULT \"Y\")',
        'C_Location': 'CREATE TABLE IF NOT EXISTS C_Location (C_Location_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, AD_Org_ID INTEGER, Address1 TEXT, Address2 TEXT, City TEXT, Postal TEXT, C_Country_ID INTEGER, C_Region_ID INTEGER, IsActive TEXT DEFAULT \"Y\")',
        'M_PriceList': 'CREATE TABLE IF NOT EXISTS M_PriceList (M_PriceList_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, AD_Org_ID INTEGER, Name TEXT NOT NULL, Description TEXT, C_Currency_ID INTEGER, IsSOPriceList TEXT DEFAULT \"Y\", IsActive TEXT DEFAULT \"Y\")',
        'M_PriceList_Version': 'CREATE TABLE IF NOT EXISTS M_PriceList_Version (M_PriceList_Version_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, AD_Org_ID INTEGER, M_PriceList_ID INTEGER, Name TEXT, Description TEXT, ValidFrom TEXT, IsActive TEXT DEFAULT \"Y\")',
        'C_Order': 'CREATE TABLE IF NOT EXISTS C_Order (C_Order_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, AD_Org_ID INTEGER, DocumentNo TEXT, DocStatus TEXT DEFAULT \"DR\", IsSOTrx TEXT DEFAULT \"Y\", DateOrdered TEXT, DatePromised TEXT, C_BPartner_ID INTEGER, C_BPartner_Location_ID INTEGER, C_DocType_ID INTEGER, C_Currency_ID INTEGER, GrandTotal REAL DEFAULT 0, TotalLines REAL DEFAULT 0, Description TEXT, IsActive TEXT DEFAULT \"Y\")',
        'C_OrderLine': 'CREATE TABLE IF NOT EXISTS C_OrderLine (C_OrderLine_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, AD_Org_ID INTEGER, C_Order_ID INTEGER NOT NULL, Line INTEGER DEFAULT 10, M_Product_ID INTEGER, QtyOrdered REAL DEFAULT 0, QtyDelivered REAL DEFAULT 0, QtyInvoiced REAL DEFAULT 0, PriceActual REAL DEFAULT 0, PriceList REAL DEFAULT 0, LineNetAmt REAL DEFAULT 0, C_Tax_ID INTEGER, C_UOM_ID INTEGER, Description TEXT, IsActive TEXT DEFAULT \"Y\")',
        'C_Invoice': 'CREATE TABLE IF NOT EXISTS C_Invoice (C_Invoice_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, AD_Org_ID INTEGER, DocumentNo TEXT, DocStatus TEXT DEFAULT \"DR\", IsSOTrx TEXT DEFAULT \"Y\", DateInvoiced TEXT, DateAcct TEXT, C_BPartner_ID INTEGER, C_BPartner_Location_ID INTEGER, C_DocType_ID INTEGER, C_Currency_ID INTEGER, GrandTotal REAL DEFAULT 0, TotalLines REAL DEFAULT 0, Description TEXT, C_Order_ID INTEGER, IsActive TEXT DEFAULT \"Y\")',
        'C_InvoiceLine': 'CREATE TABLE IF NOT EXISTS C_InvoiceLine (C_InvoiceLine_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, AD_Org_ID INTEGER, C_Invoice_ID INTEGER NOT NULL, Line INTEGER DEFAULT 10, M_Product_ID INTEGER, QtyInvoiced REAL DEFAULT 0, PriceActual REAL DEFAULT 0, PriceList REAL DEFAULT 0, LineNetAmt REAL DEFAULT 0, C_Tax_ID INTEGER, C_UOM_ID INTEGER, Description TEXT, C_OrderLine_ID INTEGER, IsActive TEXT DEFAULT \"Y\")',
        'C_Payment': 'CREATE TABLE IF NOT EXISTS C_Payment (C_Payment_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, AD_Org_ID INTEGER, DocumentNo TEXT, DocStatus TEXT DEFAULT \"DR\", DateTrx TEXT, DateAcct TEXT, C_BPartner_ID INTEGER, C_DocType_ID INTEGER, C_Currency_ID INTEGER, PayAmt REAL DEFAULT 0, TenderType TEXT, IsReceipt TEXT DEFAULT \"Y\", Description TEXT, C_Invoice_ID INTEGER, IsActive TEXT DEFAULT \"Y\")',
        'C_BankStatement': 'CREATE TABLE IF NOT EXISTS C_BankStatement (C_BankStatement_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, AD_Org_ID INTEGER, Name TEXT, DocStatus TEXT DEFAULT \"DR\", StatementDate TEXT, BeginningBalance REAL DEFAULT 0, EndingBalance REAL DEFAULT 0, Description TEXT, IsActive TEXT DEFAULT \"Y\")',
        'C_BankStatementLine': 'CREATE TABLE IF NOT EXISTS C_BankStatementLine (C_BankStatementLine_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, AD_Org_ID INTEGER, C_BankStatement_ID INTEGER NOT NULL, Line INTEGER DEFAULT 10, DateAcct TEXT, TrxAmt REAL DEFAULT 0, StmtAmt REAL DEFAULT 0, C_Payment_ID INTEGER, C_BPartner_ID INTEGER, Description TEXT, IsActive TEXT DEFAULT \"Y\")',
        'M_Warehouse': 'CREATE TABLE IF NOT EXISTS M_Warehouse (M_Warehouse_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, AD_Org_ID INTEGER, Value TEXT, Name TEXT NOT NULL, Description TEXT, C_Location_ID INTEGER, IsActive TEXT DEFAULT \"Y\")',
        'M_Locator': 'CREATE TABLE IF NOT EXISTS M_Locator (M_Locator_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, AD_Org_ID INTEGER, M_Warehouse_ID INTEGER, Value TEXT, X TEXT, Y TEXT, Z TEXT, IsDefault TEXT DEFAULT \"N\", IsActive TEXT DEFAULT \"Y\")',
        'M_Storage': 'CREATE TABLE IF NOT EXISTS M_Storage (M_Locator_ID INTEGER NOT NULL, M_Product_ID INTEGER NOT NULL, QtyOnHand REAL DEFAULT 0, QtyReserved REAL DEFAULT 0, QtyOrdered REAL DEFAULT 0, IsActive TEXT DEFAULT \"Y\", PRIMARY KEY (M_Locator_ID, M_Product_ID))',
        'M_InOut': 'CREATE TABLE IF NOT EXISTS M_InOut (M_InOut_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, AD_Org_ID INTEGER, DocumentNo TEXT, DocStatus TEXT DEFAULT \"DR\", IsSOTrx TEXT DEFAULT \"Y\", MovementDate TEXT, DateAcct TEXT, C_BPartner_ID INTEGER, C_BPartner_Location_ID INTEGER, M_Warehouse_ID INTEGER, C_DocType_ID INTEGER, C_Order_ID INTEGER, Description TEXT, IsActive TEXT DEFAULT \"Y\")',
        'M_InOutLine': 'CREATE TABLE IF NOT EXISTS M_InOutLine (M_InOutLine_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, AD_Org_ID INTEGER, M_InOut_ID INTEGER NOT NULL, Line INTEGER DEFAULT 10, M_Product_ID INTEGER, M_Locator_ID INTEGER, QtyEntered REAL DEFAULT 0, MovementQty REAL DEFAULT 0, C_UOM_ID INTEGER, C_OrderLine_ID INTEGER, Description TEXT, IsActive TEXT DEFAULT \"Y\")',
        'C_ElementValue': 'CREATE TABLE IF NOT EXISTS C_ElementValue (C_ElementValue_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, AD_Org_ID INTEGER, Value TEXT NOT NULL, Name TEXT NOT NULL, Description TEXT, AccountType TEXT, AccountSign TEXT, IsSummary TEXT DEFAULT \"N\", PostActual TEXT DEFAULT \"Y\", PostBudget TEXT DEFAULT \"Y\", IsActive TEXT DEFAULT \"Y\")',
        'C_AcctSchema': 'CREATE TABLE IF NOT EXISTS C_AcctSchema (C_AcctSchema_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, AD_Org_ID INTEGER, Name TEXT NOT NULL, Description TEXT, C_Currency_ID INTEGER, IsActive TEXT DEFAULT \"Y\")',
        'C_ProjectPhase': 'CREATE TABLE IF NOT EXISTS C_ProjectPhase (C_ProjectPhase_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, AD_Org_ID INTEGER, C_Project_ID INTEGER NOT NULL, Name TEXT NOT NULL, Description TEXT, SeqNo INTEGER DEFAULT 10, PlannedAmt REAL DEFAULT 0, CommittedAmt REAL DEFAULT 0, IsActive TEXT DEFAULT \"Y\")',
        'C_ProjectTask': 'CREATE TABLE IF NOT EXISTS C_ProjectTask (C_ProjectTask_ID INTEGER PRIMARY KEY, AD_Client_ID INTEGER, AD_Org_ID INTEGER, C_ProjectPhase_ID INTEGER NOT NULL, Name TEXT NOT NULL, Description TEXT, SeqNo INTEGER DEFAULT 10, PlannedAmt REAL DEFAULT 0, CommittedAmt REAL DEFAULT 0, IsActive TEXT DEFAULT \"Y\")',
    }

    out.write('-- §SCHEMA DDL\\n')
    for tbl, ddl in schemas.items():
        out.write(ddl + ';\\n')
    out.write('\\n')

    total_rows = 0
    for csvfile in sorted(glob.glob(os.path.join(tmpdir, '*.csv'))):
        tbl = os.path.splitext(os.path.basename(csvfile))[0]
        with open(csvfile, 'r') as f:
            reader = csv.reader(f)
            headers = next(reader)
            rows = list(reader)

        if not rows:
            continue

        out.write(f'-- §SEED_{tbl} rows={len(rows)}\\n')
        for row in rows:
            vals = []
            for v in row:
                if v == '' or v is None:
                    vals.append('NULL')
                else:
                    try:
                        # Try integer
                        int(v)
                        vals.append(v)
                    except ValueError:
                        try:
                            float(v)
                            vals.append(v)
                        except ValueError:
                            vals.append(\"'\" + v.replace(\"'\", \"''\") + \"'\")
            cols = ', '.join(headers)
            valstr = ', '.join(vals)
            out.write(f'INSERT OR IGNORE INTO {tbl} ({cols}) VALUES ({valstr});\\n')
        out.write('\\n')
        total_rows += len(rows)
        print(f'  {tbl}: {len(rows)} rows')

    print(f'\\nTotal: {total_rows} rows written to {outfile}')
"

# ── Verify ────────────────────────────────────────────────────────────

LINECOUNT=$(wc -l < "$OUTPUT")
FILESIZE=$(du -h "$OUTPUT" | cut -f1)
echo ""
echo "§EXPORT done output=$OUTPUT lines=$LINECOUNT size=$FILESIZE"
echo "§EXPORT verify: load in sqlite3 and count tables"
