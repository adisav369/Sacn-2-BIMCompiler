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
| `bim-ootb-dev` | Dev/staging — test changes before production | `https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-dev/o/` |

## Live URLs

| URL | Content |
|-----|---------|
| `.../b/bim-ootb/o/index.html` | Duplex viewer (3MB, auto-fly) |
| `.../b/bim-ootb-full/o/index.html` | Landing page — 14 instant buildings + larger (httpvfs) |

## CLI Commands

```bash
export SUPPRESS_LABEL_WARNING=True

# ── bim-ootb-full (25 buildings) ──

# Landing page — DO NOT overwrite with viewer
oci os object put --bucket-name bim-ootb-full --file deploy/sandbox/landing.html --name index.html --content-type text/html --force

# Modular viewer (sandbox/)
oci os object put --bucket-name bim-ootb-full --file deploy/sandbox/index.html --name sandbox/index.html --content-type text/html --force

# All JS modules (run from repo root)
for f in config scene streaming panels tools picking tour measure sitecam issues walk city main loader; do
  oci os object put --bucket-name bim-ootb-full --file "deploy/sandbox/${f}.js" --name "sandbox/${f}.js" --content-type application/javascript --force
done

# ── bim-ootb (Duplex demo) ──

# Viewer directly as index.html (no landing page)
oci os object put --bucket-name bim-ootb --file deploy/sandbox/index.html --name index.html --content-type text/html --force

# All JS modules at bucket root (same level as index.html)
for f in config scene streaming panels tools picking tour measure sitecam issues walk city main loader; do
  oci os object put --bucket-name bim-ootb --file "deploy/sandbox/${f}.js" --name "${f}.js" --content-type application/javascript --force
done

# ── bim-ootb-dev (dev/staging) ──

# Landing (dev)
oci os object put --bucket-name bim-ootb-dev --file deploy/landing2.html --name index.html --content-type text/html --force

# Viewer + JS modules (copy production sandbox, override dev files)
oci os object put --bucket-name bim-ootb-dev --file deploy/sandbox/index.html --name sandbox/index.html --content-type text/html --force
for f in config scene streaming panels tools picking tour measure issues walk city main loader; do
  oci os object put --bucket-name bim-ootb-dev --file "deploy/sandbox/${f}.js" --name "sandbox/${f}.js" --content-type application/javascript --force
done
# Dev overrides (changed files only)
oci os object put --bucket-name bim-ootb-dev --file deploy/dev/sitecam.js --name sandbox/sitecam.js --content-type application/javascript --force
oci os object put --bucket-name bim-ootb-dev --file deploy/dev/boq_charts.html --name boq_charts.html --content-type text/html --force

# ── Common ──

# List bucket
oci os object list --bucket-name bim-ootb-full --query 'data[*].{name:name,size:size}' --output table

# Safe delete — checks live HTML files for references before removing
# Usage: oci_safe_delete <bucket> <object-name>
oci_safe_delete() {
  local bucket="$1" file="$2"
  local base="https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/${bucket}/o"
  for ref in index.html sandbox/index.html boq_charts.html; do
    if curl -s "${base}/${ref}" 2>/dev/null | grep -q "$file"; then
      echo "ABORT: $file is referenced in $ref — investigate before deleting"
      return 1
    fi
  done
  echo "OK: $file not referenced — deleting"
  oci os object delete --bucket-name "$bucket" --name "$file" --force
}

# CORS (required for sql.js)
oci os bucket update --name bim-ootb --cors-config file:///tmp/cors.json
oci os bucket update --name bim-ootb-dev --cors-config file:///tmp/cors.json
```

### Deployment rules
- **NEVER overwrite `index.html` on `bim-ootb-full`** — that's the landing page (`landing.html`)
- The viewer is `sandbox/index.html` — landing page links to it
- On `bim-ootb`, `index.html` IS the viewer (no landing page)
- Old monolith `rtree_browser_demo.html` is retired — deleted from OCI (2026-04-21)
- Always bump version in `<title>` and HUD header before deploying — status bar text gets overwritten

## Files in bim-ootb-full

```
index.html                    ← landing page (manifest-driven)
sandbox/index.html            ← modular viewer (S209)
sandbox/*.js                  ← 15 JS modules
boq_charts.html               ← 4D/5D analytics
manifest.json                 ← 30 archetypes, 11.8KB
sandbox_1M_extracted.db       ← 579MB sandbox (legacy, httpvfs retired)
buildings/
  SampleHouse_extracted.db    ← 132KB
  SampleHouse_library.db      ← 356KB
  Jesse_extracted.db           ← 412KB
  Jesse_library.db             ← 416KB
  Duplex_extracted.db          ← 636KB
  Duplex_library.db            ← 2.1MB
  ... (14 buildings total)
```
