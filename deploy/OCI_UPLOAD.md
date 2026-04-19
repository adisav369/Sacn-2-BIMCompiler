# OCI Object Storage — BOMTree Demo Upload

## One-time setup

### 1. Create the bucket

```bash
# Login (browser-based, one-time)
oci session authenticate --region ap-sydney-1

# Create bucket (public read, no versioning needed)
oci os bucket create \
  --compartment-id $COMPARTMENT_ID \
  --name bomtree-library \
  --public-access-type ObjectRead \
  --storage-tier Standard
```

### 2. Upload the databases

```bash
# sandbox_1M_extracted.db (~579MB)
oci os object put \
  --bucket-name bomtree-library \
  --file DAGCompiler/lib/input/sandbox_1M_extracted.db \
  --name sandbox_1M_extracted.db \
  --content-type application/x-sqlite3

# component_library.db (~456MB)
oci os object put \
  --bucket-name bomtree-library \
  --file library/component_library.db \
  --name component_library.db \
  --content-type application/x-sqlite3

# manifest.json
oci os object put \
  --bucket-name bomtree-library \
  --file deploy/bomtree_manifest.json \
  --name manifest.json \
  --content-type application/json
```

### 3. Get the public URLs

```bash
# List objects with URLs
oci os object list --bucket-name bomtree-library --output table

# URL format (public read):
# https://objectstorage.{region}.oraclecloud.com/n/{namespace}/b/bomtree-library/o/{filename}
```

### 4. Update the setup script

Edit `scripts/setup_bomtree_demo.sh` — replace the placeholder URLs:
```bash
OCI_BASE="https://objectstorage.ap-sydney-1.oraclecloud.com/n/{YOUR_NAMESPACE}/b/bomtree-library/o"
```

Get your namespace:
```bash
oci os ns get
```

## Verification

After upload, test the public download:
```bash
curl -I "https://objectstorage.ap-sydney-1.oraclecloud.com/n/{NAMESPACE}/b/bomtree-library/o/manifest.json"
# Should return: HTTP/1.1 200 OK
```

## Cost

OCI Free Tier includes:
- 10GB Object Storage (we use ~1GB)
- 10TB/month outbound data transfer
- No compute instance needed

At ~1GB per download, this supports ~10,000 demo downloads per month for free.

## Updating the databases

When sandbox_1M_extracted.db or component_library.db change:

```bash
# Re-upload (overwrites)
oci os object put --bucket-name bomtree-library \
  --file DAGCompiler/lib/input/sandbox_1M_extracted.db \
  --name sandbox_1M_extracted.db --force

# Update manifest checksums
md5sum DAGCompiler/lib/input/sandbox_1M_extracted.db library/component_library.db
# Edit deploy/bomtree_manifest.json with new md5 + size
# Re-upload manifest
oci os object put --bucket-name bomtree-library \
  --file deploy/bomtree_manifest.json --name manifest.json --force

# Update setup script checksums
# Edit scripts/setup_bomtree_demo.sh SANDBOX_MD5 and LIBRARY_MD5
```

## DNS (optional)

To use `bomtree.io` instead of the raw OCI URL:

```bash
# In OCI DNS Zone Management:
# bomtree.io CNAME → objectstorage.ap-sydney-1.oraclecloud.com

# Or use a redirect rule in the bucket's pre-authenticated requests
```

For now, the raw Object Storage URL works fine. DNS is cosmetic.
