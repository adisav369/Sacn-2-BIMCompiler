# ⚠ DO NOT REMOVE — USER_JOURNEY_LANE punch-list #1: reference values render as human labels, not raw FK ids.
# Scope: the experiential walk (prompts/USER_JOURNEY_LANE.md) found the dominant defect — every reference/list
#   column in the grid AND form renders the RAW integer FK id (Invoice Partner 112, Currency 100, SalesRep 101,
#   Target Document Type 135) or the raw List code (Payment Rule B, Delivery Via P). A lower-literacy user cannot
#   read the data. This kills the thesis (model-agnostic absorption renders it; delight; status-at-a-glance; NON-INVENT).
# READ the witness log after every run — exit code is not evidence.

## W-REFRESOLVE — claim
Given the GardenWorld Sales Order (and any window), every displayed reference column resolves to its human label,
sourced ONLY from AD metadata (EXTRACT, never invent):
  - LIST (AD_Reference_ID=17): label = ad_ref_list.name WHERE ad_reference_id = AD_Column.AD_Reference_Value_ID
    AND value = <stored code>.   PROVES: PaymentRule 'B' → "Cash", DeliveryVia 'P' → "Pickup".
  - TABLE (18) / SEARCH (30): chain AD_Column.AD_Reference_Value_ID → ad_ref_table → (ad_table.tablename,
    ad_key→column, ad_display→column); label = SELECT <display|identifier> FROM <table> WHERE <key> = <id>.
    When ad_display is the key itself, fall back to the FK table's identifier (Name|Value|DocumentNo|ISO_Code).
    PROVES: SalesRep_ID 101 → "GardenAdmin", C_DocTypeTarget_ID 135 → "POS Order", Bill_BPartner_ID 112 → partner name.
  - TABLEDIR (19): table = columnName − '_ID'; identifier = first existing of Name|Value|DocumentNo|ISO_Code|Description.
    PROVES: C_Currency_ID 100 → "USD" (was raw 100 — c_currency has no Name col, only iso_code).
Determinism: read-only queries, memoized; no Date/Math.random. An unresolvable id falls back to String(value)
(honest — never a fabricated label).

## Root cause (confirmed in the walk)
1. ad_data.js resolveFK() derives the FK table by stripping '_ID' from the COLUMN name → wrong for every aliased
   column (Bill_BPartner_ID→table "Bill_BPartner" ✗; C_DocTypeTarget_ID→"C_DocTypeTarget" ✗; SalesRep_ID→"SalesRep" ✗).
2. List refs never handled at all (PaymentRule/DeliveryVia have no _ID, never reach resolveFK).
3. ad_ui.js _resolveDisplay() gates the resolveFK call on colName.indexOf('_ID')>=0 → List columns excluded.
4. The deterministic source ad_ref_table (243 rows) is sliced OUT of ad_seed.db (manifest has AD_Ref_List +
   AD_Reference but not AD_Ref_Table); ad_ref_list (1499 rows) IS present.

## Fix (3 parts)
A. SEED: add AD_Ref_Table to scripts/ad_seed_manifest.json; copy its rows from build/erp/ad_full.db into the
   served ad_seed.db (same extraction → row-id consistent; verified ad_user 101=GardenAdmin, c_doctype 135=POS Order).
B. ENGINE: rewrite build/erp/ad_data.js resolveFK() to the AD reference chain above (List / Table+Search / TableDir),
   string-or-int values, memoized.  Widen ad_ui.js _resolveDisplay() to attempt resolveFK for ALL columns
   (resolveFK returns null → fall back to String(val)).
C. WITNESS: live playwright probe loads idempiere.html with the new seed, opens Sales Order, asserts the leaking
   columns now resolve (DOM shows "Cash"/"GardenAdmin"/"POS Order"/"USD", not 100/101/112/135/B/P), 0 pageerrors.

## Deploy
build/erp (canonical) → sync ad_data.js + ad_seed.db into /tmp/wt-* off origin/main → bump ad_data.js ?v= +
ad_seed IndexedDB key + sw CACHE_VERSION + internal/SW_CHANGELOG.md entry → PR → auto-merge → verify live.
