#!/usr/bin/env bash
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
# ⚠ DO NOT REMOVE — Scope guard
# Scope: MIGRATE_FULL_MODEL_FRAME.md — emit a MIGRATED iDempiere tenant SHARD (Client 13) that fits the full
#   model frame. iDempiere is the SAME AD schema as the engine, so "migration" = EXTRACT GardenWorld via the
#   EXISTING install routine (scripts/migrate_pg_to_sqlite.js) against the `idempiere_test` PG (where the posted
#   fact_acct oracle lives — see reference_idempiere_source), then RE-KEY client 11→13 (migrate to a free client id).
#   The frozen engine (doc_poster→post_resolver) then resolves the tenant's accounts == its OWN fact_acct to the cent.
#   NON-INVENT: every row a recorded iDempiere row. Deterministic + re-runnable. READ THE LOG after every run.
# Run:  bash build/erp/gen_ad_idmp.sh   (then: node scripts/poc_idmp_frame_fit.js → §FRAME-FIT … ORACLE-EQUIVALENT)
set -euo pipefail
cd "$(dirname "$0")/../.."                                   # repo root
# FREE-SLOT / TENANT NAMING (NEW_CLIENT_MGMT.md #5, mirrors #2's gen_ad_odoo nextFreeClient/CLIENT_ID/TENANT_NAME):
# the target client id is no longer hardcoded 13 — it is CLIENT_ID (the install UI's choice / next free past the
# installed tenants 11,12) so two migrated iDempiere tenants never collide. The shard file + AD_Client name derive
# from it. NON-INVENT: the id is a surrogate; every NAME/VALUE/AMOUNT stays a recorded iDempiere row.
CL=${CLIENT_ID:-13}
TENANT=${TENANT_NAME:-iDempiere}
DB=${SHARD_OUT:-build/erp/${CL}-idempiere.db}

# the engine's read-set + the AD frame + the fact_acct ORACLE (all from idempiere_test, client-consistent).
TABLES="c_order,c_orderline,c_ordertax,c_invoice,c_invoiceline,c_invoicetax,c_bpartner,m_product,\
m_product_category,c_tax,c_bp_customer_acct,m_product_category_acct,c_tax_acct,c_validcombination,\
c_elementvalue,c_acctschema_default,fact_acct,ad_client,ad_org,ad_role,ad_user,ad_user_roles,\
ad_role_orgaccess,ad_window_access"

echo "== 1) EXTRACT via the existing install routine (idempiere_test PG → $DB) =="
ERP_PG_DB=idempiere_test ERP_OUT="$DB" ERP_TABLES="$TABLES" node scripts/migrate_pg_to_sqlite.js

echo "== 2) RE-KEY GardenWorld(11) → Client $CL \"$TENANT\" (migrate to a free client id; System(0) untouched) =="
for t in $(sqlite3 "$DB" "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE '\_%' ESCAPE '\\';"); do
  has=$(sqlite3 "$DB" "SELECT count(*) FROM pragma_table_info('$t') WHERE lower(name)='ad_client_id';")
  [ "$has" = "1" ] && sqlite3 "$DB" "UPDATE $t SET ad_client_id=$CL WHERE ad_client_id=11;"
done
sqlite3 "$DB" "UPDATE ad_client SET value='$TENANT', name='$TENANT', \
  description='Migrated from iDempiere PG (idempiere_test)' WHERE ad_client_id=$CL;"

echo "== 3) §GEN-AD-IDMP audit =="
sqlite3 "$DB" "SELECT '§GEN-AD-IDMP clients=['||group_concat(ad_client_id||':'||value)||']' FROM ad_client ORDER BY ad_client_id;"
sqlite3 "$DB" "SELECT '§GEN-AD-IDMP client$CL orders='||(SELECT count(*) FROM c_order WHERE ad_client_id=$CL)||\
  ' invoices='||(SELECT count(*) FROM c_invoice WHERE ad_client_id=$CL)||\
  ' bp_cust_acct='||(SELECT count(*) FROM c_bp_customer_acct)||\
  ' validcombinations='||(SELECT count(*) FROM c_validcombination)||\
  ' fact_acct='||(SELECT count(*) FROM fact_acct WHERE ad_client_id=$CL);"
echo "§GEN-AD-IDMP wrote $DB bytes=$(stat -c%s "$DB")"
