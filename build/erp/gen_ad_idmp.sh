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

if [ "${REBAND:-1}" = "0" ]; then
  # REBAND=0 — falsifier fixture only (poc_idmp_reband.js §F reproduces the original PK-collision drop).
  echo "== 2b) PK RE-BAND SKIPPED (REBAND=0 — falsifier fixture, NOT installable) =="
else
echo "== 2b) PK RE-BAND — tenant-owned ids into the client band (IDMP_FULLWIDTH_SEED §4 / NEW_CLIENT_MGMT #5-install) =="
# ROOT (verified): re-keying only AD_Client_ID left GardenWorld's PRIMARY KEYS unchanged → installing the
# shard over the (GardenWorld) ad_seed.db collides every tenant PK → INSERT OR IGNORE silently DROPS the
# tenant rows. FIX = the Odoo DOC=CL*100000 band pattern: uniform offset of every tenant-owned id family
# (PK + all FK columns matching the family name) into the client band. FK-CONSISTENT because each family's
# owner rows are ALL client-$CL (asserted below — a System(0) row in a family would abort, never mis-band).
# Identity families ad_org/ad_role/ad_user ALSO offset (verified collision: the shard's client rows carry
# the SAME ids as the seed's GardenWorld identity rows → un-banded, the tenant is never login-able);
# their System(0) rows (org 0, users 0/10/100, role 0) are NOT in the tenant id-lists → untouched.
# Specials (column name ≠ family): c_validcombination.account_id + fact_acct.account_id → c_elementvalue;
# *_acct-suffixed columns → c_validcombination; fact_acct.record_id/line_id for ad_table_id 259/318 (the
# offset doc families; other ad_table_ids reference doc tables NOT in this shard — left as recorded).
OFF=$((CL*100000))
FAMILIES="c_order_id:c_order c_orderline_id:c_orderline c_invoice_id:c_invoice c_invoiceline_id:c_invoiceline \
c_bpartner_id:c_bpartner m_product_id:m_product m_product_category_id:m_product_category c_tax_id:c_tax \
c_validcombination_id:c_validcombination c_elementvalue_id:c_elementvalue \
ad_org_id:ad_org ad_role_id:ad_role ad_user_id:ad_user"
TBLS=$(sqlite3 "$DB" "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE '\_%' ESCAPE '\\';")
# 1: capture EVERY family's tenant id-list BEFORE any update (sequencing: a family already offset must
#    never feed another family's list — account_id/vc specials read these lists too).
declare -A IDS
for fo in $FAMILIES; do
  fam=${fo%%:*}; own=${fo##*:}
  IDS[$fam]=$(sqlite3 "$DB" "SELECT group_concat($fam) FROM $own WHERE ad_client_id=$CL;")
  nonten=$(sqlite3 "$DB" "SELECT count(*) FROM $own WHERE ad_client_id NOT IN (0,$CL);")
  [ "$nonten" != "0" ] && { echo "§REBAND ABORT $own carries client≠0/$CL rows — uniform offset unsafe"; exit 1; }
done
# ref_* counter-doc aliases (Ref_Order_ID …) would dangle if populated — assert empty, loud.
for t in c_order c_orderline c_invoice c_invoiceline; do
  for rc in ref_order_id ref_orderline_id ref_invoice_id ref_invoiceline_id; do
    has=$(sqlite3 "$DB" "SELECT count(*) FROM pragma_table_info('$t') WHERE lower(name)='$rc';")
    if [ "$has" = "1" ]; then
      n=$(sqlite3 "$DB" "SELECT count(*) FROM $t WHERE $rc IS NOT NULL;")
      [ "$n" != "0" ] && { echo "§REBAND ABORT $t.$rc has $n non-null rows — alias family unhandled"; exit 1; }
    fi
  done
done
# 2: apply — every table, every column named exactly <family>, offset values IN the tenant list.
for fo in $FAMILIES; do
  fam=${fo%%:*}
  [ -z "${IDS[$fam]}" ] && { echo "§REBAND $fam: no tenant ids — skip"; continue; }
  for t in $TBLS; do
    has=$(sqlite3 "$DB" "SELECT count(*) FROM pragma_table_info('$t') WHERE lower(name)='$fam';")
    [ "$has" = "1" ] || continue
    n=$(sqlite3 "$DB" "UPDATE $t SET $fam=$fam+$OFF WHERE $fam IN (${IDS[$fam]}); SELECT changes();")
    [ "$n" != "0" ] && echo "§REBAND $t.$fam +$OFF rows=$n"
  done
done
# 3: specials.
n=$(sqlite3 "$DB" "UPDATE c_validcombination SET account_id=account_id+$OFF WHERE account_id IN (${IDS[c_elementvalue_id]}); SELECT changes();")
echo "§REBAND c_validcombination.account_id(+ev) rows=$n"
n=$(sqlite3 "$DB" "UPDATE fact_acct SET account_id=account_id+$OFF WHERE account_id IN (${IDS[c_elementvalue_id]}); SELECT changes();")
echo "§REBAND fact_acct.account_id(+ev) rows=$n"
for t in $TBLS; do
  for col in $(sqlite3 "$DB" "SELECT name FROM pragma_table_info('$t') WHERE lower(name) LIKE '%\_acct' ESCAPE '\\';"); do
    n=$(sqlite3 "$DB" "UPDATE $t SET $col=$col+$OFF WHERE $col IN (${IDS[c_validcombination_id]}); SELECT changes();")
    [ "$n" != "0" ] && echo "§REBAND $t.$col(+vc) rows=$n"
  done
done
n=$(sqlite3 "$DB" "UPDATE fact_acct SET record_id=record_id+$OFF WHERE ad_table_id IN (259,318); SELECT changes();")
echo "§REBAND fact_acct.record_id(259/318) rows=$n"
n=$(sqlite3 "$DB" "UPDATE fact_acct SET line_id=line_id+$OFF WHERE ad_table_id IN (259,318) AND line_id IS NOT NULL; SELECT changes();")
echo "§REBAND fact_acct.line_id(259/318) rows=$n"
echo "§REBAND done band=$OFF (ids ≥100000 land past the next client's nominal band start — same leak as the
§REBAND   Odoo CL*100000 pattern; collision-free for GardenWorld-derived shards, named not hidden)"
fi

echo "== 3) §GEN-AD-IDMP audit =="
sqlite3 "$DB" "SELECT '§GEN-AD-IDMP clients=['||group_concat(ad_client_id||':'||value)||']' FROM ad_client ORDER BY ad_client_id;"
sqlite3 "$DB" "SELECT '§GEN-AD-IDMP client$CL orders='||(SELECT count(*) FROM c_order WHERE ad_client_id=$CL)||\
  ' invoices='||(SELECT count(*) FROM c_invoice WHERE ad_client_id=$CL)||\
  ' bp_cust_acct='||(SELECT count(*) FROM c_bp_customer_acct)||\
  ' validcombinations='||(SELECT count(*) FROM c_validcombination)||\
  ' fact_acct='||(SELECT count(*) FROM fact_acct WHERE ad_client_id=$CL);"
echo "§GEN-AD-IDMP wrote $DB bytes=$(stat -c%s "$DB")"
