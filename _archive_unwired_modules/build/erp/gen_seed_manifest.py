#!/usr/bin/env python3
# gen_seed_manifest.py — IDMP_FULLWIDTH_SEED §1: derive scripts/ad_seed_manifest.json
# from the DEPLOYED seed inventory (build/erp/seed_recon.log) + export_ad.sh's WHERE rules.
# EXTRACT, don't invent: every table name/case/filter is observed, not authored.
#   - MixedCase stratum (export_ad.sh slice): keep its per-table WHERE + IsActive='Y'.
#   - lowercase stratum (full client pull): WHERE ad_client_id=11 when the recon saw
#     clients=11; no IsActive filter (c_poskey 163 incl. inactive proves the convention).
#   - ADD ad_process + ad_process_para (card §1; client-0 System rows → no client filter).
import json, re, sys

RECON = 'build/erp/seed_recon.log'
OUT = 'scripts/ad_seed_manifest.json'

# export_ad.sh WHERE rules for the MixedCase stratum (verbatim from the script).
MIXED_WHERE = {
    'AD_TreeNodeMM': 'AD_Tree_ID = 10',
    'AD_User': 'AD_Client_ID IN (0, 11)',
    'C_TaxCategory': 'AD_Client_ID IN (0, 11)',
    'C_Tax': 'AD_Client_ID IN (0, 11)',
    'C_DocType': 'AD_Client_ID IN (0, 11)',
}
# MixedCase tables export_ad.sh filtered to client 11 even where the SLICE dropped the
# ad_client_id column (M_ProductPrice, M_Storage) — recon clients='-' is the slice talking.
MIXED_NOFILTER = {'AD_Menu', 'AD_Window', 'AD_Tab', 'AD_Field', 'AD_Column', 'AD_Table',
                  'AD_Reference', 'AD_Ref_List', 'AD_Element', 'C_Currency', 'C_UOM',
                  'C_Country', 'C_Region'}

manifest = []
for line in open(RECON):
    m = re.match(r'§RECON-TBL (\S+) rows=(\d+) cols=(\d+) clients=(\S*)', line)
    if not m:
        continue
    t, rows, cols, clients = m.group(1), int(m.group(2)), int(m.group(3)), m.group(4)
    mixed = t != t.lower()
    if mixed:
        where = MIXED_WHERE.get(t)
        if where is None and t not in MIXED_NOFILTER:
            where = 'AD_Client_ID = 11'
        entry = {'table': t, 'case': 'canonical', 'where': where, 'activeOnly': True}
    else:
        where = 'ad_client_id = 11' if '11' in clients.split(',') else None
        entry = {'table': t, 'case': 'lower', 'where': where, 'activeOnly': False}
    entry['prevRows'] = rows
    entry['prevCols'] = cols
    manifest.append(entry)

# Card §1 additions — System(0) process dictionary, whole table (probe: 476 + 1208 rows).
for t in ('ad_process', 'ad_process_para'):
    manifest.append({'table': t, 'case': 'lower', 'where': None, 'activeOnly': False,
                     'prevRows': 0, 'prevCols': 0})

json.dump(manifest, open(OUT, 'w'), indent=1)
print(f'§MANIFEST tables={len(manifest)} mixed={sum(1 for e in manifest if e["case"]=="canonical")} '
      f'lower={sum(1 for e in manifest if e["case"]=="lower")} out={OUT}')
