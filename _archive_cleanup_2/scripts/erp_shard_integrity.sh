#!/usr/bin/env bash
# ⚠ DO NOT REMOVE — Scope guard
# Scope: §SHARD-CLOSURE witness for docs/ERP_DATA_COMPLETENESS_POLICY.md §3 Layer 1.
#   ASSERTS the referential-closure INVARIANT (§1) over the paths the renderer follows — 0 dangling FK on
#   login / menu / window / fk graphs — and REPORTS data coverage (present tables) which is the T1/T2 fill
#   surface, NOT a closure defect. Read-only: opens the DB, runs SELECTs, mutates nothing.
#   §-log first: read the emitted §SHARD-CLOSURE line before any conclusion.
# Run:  scripts/erp_shard_integrity.sh /home/red1/bim-ootb/erp/ad_seed.db
set -euo pipefail
DB="${1:?usage: erp_shard_integrity.sh <shard.db>}"
[ -f "$DB" ] || { echo "FATAL: no such DB: $DB"; exit 2; }
q() { sqlite3 "$DB" "$1"; }

echo "=== §SHARD-CLOSURE audit — $DB ==="

# ── §1.1 login graph closure (sentinels: client 0=System, org 0=*) ──
role_client=$(q "SELECT count(*) FROM ad_role r LEFT JOIN ad_client c ON r.ad_client_id=c.ad_client_id WHERE c.ad_client_id IS NULL")
ur_role=$(q "SELECT count(*) FROM ad_user_roles ur LEFT JOIN ad_role r ON ur.ad_role_id=r.ad_role_id WHERE r.ad_role_id IS NULL")
oa_org=$(q "SELECT count(*) FROM ad_role_orgaccess oa LEFT JOIN ad_org o ON oa.ad_org_id=o.ad_org_id WHERE o.ad_org_id IS NULL AND oa.ad_org_id<>0")  # org 0=* is the named sentinel
login=$((role_client + ur_role + oa_org))

# ── §1.2 menu graph closure: active W leaves must target a present AD_Window ──
menu=$(q "SELECT count(*) FROM ad_menu m LEFT JOIN ad_window w ON m.ad_window_id=w.ad_window_id WHERE m.action='W' AND m.isactive='Y' AND m.ad_window_id IS NOT NULL AND w.ad_window_id IS NULL")

# ── §1.3 window graph closure: tabs of menu-reachable windows must have a backing AD_Table row ──
window=$(q "WITH wl AS (SELECT DISTINCT ad_window_id wid FROM ad_menu WHERE action='W' AND isactive='Y' AND ad_window_id IS NOT NULL)
 SELECT count(*) FROM ad_tab tt JOIN wl ON tt.ad_window_id=wl.wid WHERE tt.isactive='Y' AND tt.ad_table_id NOT IN (SELECT ad_table_id FROM ad_table)")

# ── §1.4 fk display closure: ASSERTED (docs/ERP_SHARD_GENERATOR.md §2/§5). Mirrors the renderer's
# resolveFK (ad_data.js:332-339) — CONVENTION-ONLY: target table = ColumnName minus trailing _ID (it does
# NOT read ad_ref_table). Count = distinct target tables that ARE real AD_Tables but are ABSENT as a
# physical table/view in this shard, reached from a DISPLAYED FK field (ref 18/19/30) of a menu-reachable
# window's active tab. Polymorphic names that aren't AD_Tables (Record_ID, …) are skipped, not dangles.
fk=$(q "WITH wl AS (SELECT DISTINCT ad_window_id wid FROM ad_menu WHERE action='W' AND isactive='Y' AND ad_window_id IS NOT NULL),
 fkcol AS (SELECT DISTINCT c.columnname cn FROM ad_field f
   JOIN ad_tab tt ON f.ad_tab_id=tt.ad_tab_id JOIN wl ON tt.ad_window_id=wl.wid
   JOIN ad_column c ON f.ad_column_id=c.ad_column_id
   WHERE f.isdisplayed='Y' AND f.isactive='Y' AND tt.isactive='Y'
     AND c.ad_reference_id IN (18,19,30) AND upper(c.columnname) LIKE '%\_ID' ESCAPE '\'),
 tgt AS (SELECT DISTINCT lower(substr(cn,1,length(cn)-3)) tn FROM fkcol)
 SELECT count(*) FROM tgt WHERE tn IN (SELECT lower(tablename) FROM ad_table)
   AND tn NOT IN (SELECT lower(name) FROM sqlite_master WHERE type IN ('table','view'))")

# ── coverage (REPORTED, not asserted): header tables of menu W-windows present as physical tables ──
read -r tot pres < <(q "WITH wl AS (SELECT DISTINCT ad_window_id wid FROM ad_menu WHERE action='W' AND isactive='Y' AND ad_window_id IS NOT NULL),
 hdr AS (SELECT DISTINCT tbl.tablename tn FROM ad_tab tt JOIN wl ON tt.ad_window_id=wl.wid JOIN ad_table tbl ON tt.ad_table_id=tbl.ad_table_id WHERE tt.tablevel=0 AND tbl.tablename IS NOT NULL)
 SELECT (SELECT count(*) FROM hdr), (SELECT count(*) FROM hdr WHERE lower(tn) IN (SELECT lower(name) FROM sqlite_master WHERE type IN ('table','view')))" | tr '|' ' ')

# sentinels present?
has_client0=$(q "SELECT count(*) FROM ad_client WHERE ad_client_id=0")
sysrole=$(q "SELECT count(*) FROM ad_role WHERE ad_role_id=0")

dangling=$((login + menu + window + fk))

echo "  login-graph dangling   = $login   (role→client=$role_client user_roles→role=$ur_role orgaccess→org=$oa_org [org0=* excluded])"
echo "  menu-graph dangling    = $menu   (active W leaf → AD_Window)"
echo "  window-graph dangling  = $window   (tab → AD_Table)"
echo "  fk-display dangling    = $fk   (displayed FK target table absent; resolveFK convention, _ID-stripped)"
echo "  COVERAGE (reported)    : headerTablesPresent=$pres/$tot   client0(System)=$has_client0   role0(SysAdmin)=$sysrole"
echo ""
echo "§SHARD-CLOSURE shard=$(basename "$DB") login=$login menu=$menu window=$window fk=$fk sentinels=[org0,client0] dangling=$dangling tablesPresent=$pres/$tot"
echo ""
if [ "$dangling" -eq 0 ]; then
  echo "=== RESULT: CLOSED — 0 dangling on renderer-followed paths (coverage is the tier-fill surface, not a defect) ==="
  exit 0
else
  echo "=== RESULT: NOT CLOSED — $dangling dangling reference(s); shard not shippable until the closure generator resolves them ==="
  exit 1
fi
