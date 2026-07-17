#!/usr/bin/env bash
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
# ⚠ DO NOT REMOVE — Scope guard
# Scope: W-POST-B3 §W-2 (prompts/FABLE5_B3_POSTING_ORACLE.md) — GENERATE the 0-seed posting oracle.
#   Clone idempiere_test → SCRATCH idempiere_b3, drive the REAL compiled Doc_<Class> posters via the
#   vendor's own OSGi test harness (org.idempiere.test tycho-surefire hosting
#   scripts/logic_oracle/PostingOracleTest.java — the G-3 "no headless OSGi" landmine's sanctioned
#   resolution), then capture the committed fact_acct + seed-doc source rows into the TEXT fixture
#   build/erp/oracle/post_b3_fixture.json (extract_fact_acct.sh shape; DB-binary ban holds).
#   The shared idempiere_test is NEVER written — posting happens on the clone, which is dropped at
#   the end. NON-INVENT: the fixture is a straight copy of what the real posting engine wrote over
#   an honestly-labelled GardenWorld-model seed (USER RULING 2026-07-17).
# READ THE LOG after every run: build/erp/generate_post_oracle.log (exit ≠ evidence).
# Run:  bash scripts/generate_post_oracle.sh
set -euo pipefail
cd "$(dirname "$0")/.."

CONTAINER=postgres
PGUSER=adempiere
SRC_DB=idempiere_test
SCRATCH_DB=idempiere_b3
IDMP="$HOME/idempiere-dev-setup/idempiere"
B3_HOME=/tmp/idempiere_b3_home
LOG=build/erp/generate_post_oracle.log
FIXTURE=build/erp/oracle/post_b3_fixture.json
ORACLE_SRC=scripts/logic_oracle/PostingOracleTest.java
ORACLE_DST="$IDMP/org.idempiere.test/src/org/idempiere/test/oracle/PostingOracleTest.java"
# the workspace-bundle dep closure of org.idempiere.test (probed 2026-07-17, mvn_probe4-7):
MODULES="org.idempiere.p2.targetplatform,org.apache.ecs,org.adempiere.base,org.adempiere.base.callout,org.adempiere.base.process,org.adempiere.payment.processor,org.adempiere.ui,org.adempiere.ui.zk,org.adempiere.report.jasper,org.adempiere.report.jasper.library,org.idempiere.zk.billboard,org.idempiere.zk.extra,org.adempiere.server,org.idempiere.webservices,org.idempiere.webservices.resources,org.compiere.db.postgresql.provider,org.compiere.db.oracle.provider,org.adempiere.install,org.adempiere.replication,org.adempiere.pipo,org.adempiere.pipo.handlers,org.adempiere.plugin.utils,org.idempiere.hazelcast.service,org.idempiere.tablepartition,org.idempiere.test"

mkdir -p build/erp/oracle
: > "$LOG"
say() { echo "$@" | tee -a "$LOG"; }

say "== §W-2 generate: scratch clone $SCRATCH_DB from $SRC_DB =="
docker exec "$CONTAINER" psql -U "$PGUSER" -d postgres -c \
  "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname IN ('$SRC_DB','$SCRATCH_DB') AND pid<>pg_backend_pid();" >>"$LOG" 2>&1 || true
docker exec "$CONTAINER" psql -U "$PGUSER" -d postgres -c "DROP DATABASE IF EXISTS $SCRATCH_DB;" >>"$LOG" 2>&1
docker exec "$CONTAINER" createdb -U "$PGUSER" -T "$SRC_DB" "$SCRATCH_DB"
say "§GEN scratch=$SCRATCH_DB cloned (template $SRC_DB)"

say "== dictionary alignment on the CLONE (release-13 X classes vs this seed's older AD dictionary) =="
# X_A_Depreciation_Workfile getters cast these to BigDecimal (src-gen), but this seed's AD_Column
# types them Integer (ad_reference_id=11) → ClassCastException in MDepreciationWorkfile.beforeSave:149.
# Retype to Number (22) on the SCRATCH clone only — dictionary seed prep, never touches idempiere_test.
docker exec "$CONTAINER" psql -U "$PGUSER" -d "$SCRATCH_DB" -c "
  UPDATE ad_column SET ad_reference_id=22
  WHERE ad_table_id=(SELECT ad_table_id FROM ad_table WHERE tablename='A_Depreciation_Workfile')
    AND columnname IN ('UseLifeYears','UseLifeYears_F','A_Asset_Life_Years','A_Asset_Life_Years_F')
    AND ad_reference_id=11;" >>"$LOG" 2>&1
say "§GEN dict-align A_Depreciation_Workfile uselife-years columns 11→22 (clone only)"

say "== scratch IDEMPIERE_HOME at $B3_HOME (Connection → $SCRATCH_DB) =="
mkdir -p "$B3_HOME/utils" "$B3_HOME/log"
sed "s/DBname\\\\=idempiere,/DBname\\\\=$SCRATCH_DB,/" "$IDMP/idempiere.properties" > "$B3_HOME/idempiere.properties"
grep -q "DBname\\\\=$SCRATCH_DB" "$B3_HOME/idempiere.properties" || { say "§GEN FATAL properties patch failed"; exit 1; }
cp "$IDMP/org.adempiere.server-feature/utils.unix/getVar.sh" "$B3_HOME/utils/"
chmod +x "$B3_HOME/utils/getVar.sh"
cp "$IDMP/.idpass" "$B3_HOME/.idpass"
sed "s/^ADEMPIERE_DB_NAME=.*/ADEMPIERE_DB_NAME=$SCRATCH_DB/" "$IDMP/idempiereEnv.properties" > "$B3_HOME/idempiereEnv.properties"

say "== place PostingOracleTest into the vendor harness (removed again at the end) =="
mkdir -p "$(dirname "$ORACLE_DST")"
cp "$ORACLE_SRC" "$ORACLE_DST"

say "== drive the REAL compiled posters (tycho-surefire OSGi, scratch DB) — long step =="
set +e
( cd "$IDMP" && ./mvnw verify -pl "$MODULES" -DskipTests=false \
    -Didempiere.home="$B3_HOME" -Dtest=PostingOracleTest ) >>"$LOG" 2>&1
MVN_EXIT=$?
set -e
# keep the checkout clean regardless of outcome
rm -f "$ORACLE_DST"; rmdir "$(dirname "$ORACLE_DST")" 2>/dev/null || true
grep '§ORACLE' "$LOG" | tee build/erp/oracle/post_b3_oracle_run.log || true
if [ $MVN_EXIT -ne 0 ]; then
  say "§GEN FATAL mvn exit=$MVN_EXIT — READ $LOG (posting did not complete; scratch kept for autopsy)"
  exit 1
fi
say "§GEN posting run green (mvn exit 0) — capturing fixture"

say "== capture fixture (extract_fact_acct.sh shape, scoped to the B-3 classes) =="
node scripts/capture_post_b3_fixture.js "$SCRATCH_DB" "$FIXTURE" | tee -a "$LOG"

say "== drop scratch =="
docker exec "$CONTAINER" psql -U "$PGUSER" -d postgres -c \
  "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$SCRATCH_DB' AND pid<>pg_backend_pid();" >>"$LOG" 2>&1 || true
docker exec "$CONTAINER" psql -U "$PGUSER" -d postgres -c "DROP DATABASE IF EXISTS $SCRATCH_DB;" >>"$LOG" 2>&1
say "§GEN scratch dropped; fixture at $FIXTURE — now run: bash build/erp/run_witness.sh scripts/poc_post_b3.js"
