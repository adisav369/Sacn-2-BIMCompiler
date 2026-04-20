# OCI Object Storage Setup

## Account
- **Cloud Account:** red1org
- **Username:** red1org@gmail.com
- **Region:** Malaysia West 2 Kulai (`ap-kulai-2`)
- **Tenancy OCID:** `ocid1.tenancy.oc1..aaaaaaaaujcodusm4s3vshjbaho7hcl3pompb2bdo2bjmbgyjap67gz4kcba`
- **User OCID:** `ocid1.user.oc1..aaaaaaaan25loed6f4wrj2xvlwad5s2ivbeopi5b2nd56x32be64u4w6miua`
- **Namespace:** `ax3cp6tzwuy2`
- **API Key Fingerprint:** `62:e4:36:11:e5:9b:56:3f:19:3a:fa:de:e3:de:36:92`
- **Key files:** `~/.oci/oci_api_key.pem` (private), `~/.oci/oci_api_key_public.pem` (public)
- **Config:** `~/.oci/config`
- **Tier:** Always Free (10GB storage, 10TB/mo outbound)

## Buckets

| Bucket | Purpose | Public URL Base |
|--------|---------|----------------|
| `bim-ootb` | Duplex demo (mobile-friendly) | `https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb/o/` |
| `bim-ootb-full` | Landing page + 14 per-building DBs + sandbox | `https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-full/o/` |
| `bim-ootb-duplex` | Duplex-only (backup) | `https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-duplex/o/` |

## Live URLs

| URL | Content |
|-----|---------|
| `.../b/bim-ootb/o/index.html` | Duplex viewer (3MB, auto-fly) |
| `.../b/bim-ootb-full/o/index.html` | Landing page — 14 instant buildings + larger (httpvfs) |

## CLI Commands

```bash
# Upload file
export SUPPRESS_LABEL_WARNING=True
oci os object put --bucket-name bim-ootb --file deploy/file.html --content-type "text/html" --name file.html --no-multipart --force

# List bucket
oci os object list --bucket-name bim-ootb --query 'data[*].{name:name,size:size}' --output table

# CORS (required for sql.js + httpvfs)
oci os bucket update --name bim-ootb --cors-config file:///tmp/cors.json
```

## Files in bim-ootb-full

```
index.html                    ← landing page (manifest-driven)
rtree_browser_demo.html       ← full-download viewer (with progress loader)
rtree_streaming.html          ← httpvfs streaming viewer
boq_charts.html               ← 4D/5D analytics
manifest.json                 ← 30 archetypes, 11.8KB
httpvfs.js                    ← sql.js-httpvfs library
sqlite.worker.js              ← httpvfs web worker
sql-wasm.wasm                 ← SQLite WASM binary
sandbox_1M_extracted.db       ← 579MB sandbox (for httpvfs)
component_library.db          ← 456MB full library (for httpvfs)
buildings/
  SampleHouse_extracted.db    ← 132KB
  SampleHouse_library.db      ← 356KB
  Jesse_extracted.db           ← 412KB
  Jesse_library.db             ← 416KB
  Duplex_extracted.db          ← 636KB
  Duplex_library.db            ← 2.1MB
  ... (14 buildings total)
```
