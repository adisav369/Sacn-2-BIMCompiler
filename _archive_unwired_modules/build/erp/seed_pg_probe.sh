#!/bin/bash
# seed_pg_probe.sh — IDMP_FULLWIDTH_SEED §0 recon probes against PG `idempiere`.
# EXTRACT only: counts + catalog facts the full-width exporter needs. Read the log.
P() { docker exec postgres psql -U adempiere -d idempiere -t -A -c "$1"; }
echo "§PROBE ad_process all=$(P "SELECT count(*) FROM adempiere.ad_process") active=$(P "SELECT count(*) FROM adempiere.ad_process WHERE isactive='Y'")"
echo "§PROBE ad_process_para all=$(P "SELECT count(*) FROM adempiere.ad_process_para") active=$(P "SELECT count(*) FROM adempiere.ad_process_para WHERE isactive='Y'")"
echo "§PROBE ad_menu ad_workflow_id col=$(P "SELECT count(*) FROM information_schema.columns WHERE table_schema='adempiere' AND table_name='ad_menu' AND column_name='ad_workflow_id'")"
echo "§PROBE m_inout movementtype col=$(P "SELECT count(*) FROM information_schema.columns WHERE table_schema='adempiere' AND table_name='m_inout' AND column_name='movementtype'")"
echo "§PROBE c_doctype hole cols=$(P "SELECT count(*) FROM information_schema.columns WHERE table_schema='adempiere' AND table_name='c_doctype' AND column_name IN ('iscanbereactivated','docsubtypeso')")"
echo "§PROBE c_order dateacct col=$(P "SELECT count(*) FROM information_schema.columns WHERE table_schema='adempiere' AND table_name='c_order' AND column_name='dateacct'")"
for t in ad_role c_period m_cost ad_sequence c_poskey a_depreciation_table_detail; do
  echo "§PROBE $t client11_all=$(P "SELECT count(*) FROM adempiere.$t WHERE ad_client_id=11") client11_active=$(P "SELECT count(*) FROM adempiere.$t WHERE ad_client_id=11 AND isactive='Y'")"
done
echo "§PROBE ad_pinstance_log all=$(P "SELECT count(*) FROM adempiere.ad_pinstance_log")"
echo "§PROBE pk_tables=$(P "SELECT count(DISTINCT table_name) FROM information_schema.table_constraints WHERE table_schema='adempiere' AND constraint_type='PRIMARY KEY'")"
echo "§PROBE c_doctype client(0,11) all=$(P "SELECT count(*) FROM adempiere.c_doctype WHERE ad_client_id IN (0,11)") active=$(P "SELECT count(*) FROM adempiere.c_doctype WHERE ad_client_id IN (0,11) AND isactive='Y'")"
echo "§PROBE m_inout client11 active=$(P "SELECT count(*) FROM adempiere.m_inout WHERE ad_client_id=11 AND isactive='Y'") movementtypes=$(P "SELECT string_agg(DISTINCT movementtype,',') FROM adempiere.m_inout WHERE ad_client_id=11")"
echo "§PROBE c_doctype reactivatable=$(P "SELECT string_agg(c_doctype_id||':'||docbasetype,',') FROM adempiere.c_doctype WHERE ad_client_id IN (0,11) AND iscanbereactivated='Y' AND isactive='Y'")"
