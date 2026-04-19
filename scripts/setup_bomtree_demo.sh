#!/bin/bash
# ──────────────────────────────────────────────────────────────
# BOMTree Demo Setup — Download reference databases for the
# BIM Federation 1M-element city demo.
#
# Prerequisites:
#   1. Blender 4.2+ installed (https://blender.org/download)
#   2. Bonsai addon installed (https://bonsai.community/download)
#   3. curl available (pre-installed on macOS/Linux)
#
# Usage:
#   chmod +x scripts/setup_bomtree_demo.sh
#   ./scripts/setup_bomtree_demo.sh
#
# What it does:
#   - Creates ~/.bim/City/ directory
#   - Downloads sandbox_1M_extracted.db (~579MB) — 1M elements, 786 buildings
#   - Downloads component_library.db (~456MB) — 124K geometry BLOBs
#   - Verifies checksums
#   - Prints instructions for Blender
#
# After setup:
#   1. Open Blender
#   2. N-panel → BIM tab → set Federation DB path to:
#      ~/.bim/City/sandbox_1M_extracted.db
#   3. Press Ctrl+Shift+A to start Direct Stream
#   4. Buildings stream nearest-first, envelope appears in seconds
#   5. Fly inside a building for full detail
#
# Total download: ~1.0GB. One-time. No server needed.
# ──────────────────────────────────────────────────────────────

set -euo pipefail

# ── Configuration ──
# UPDATE these URLs after uploading to OCI Object Storage
OCI_BASE="https://objectstorage.ap-sydney-1.oraclecloud.com/n/NAMESPACE/b/bomtree-library/o"
SANDBOX_URL="${OCI_BASE}/sandbox_1M_extracted.db"
LIBRARY_URL="${OCI_BASE}/component_library.db"

SANDBOX_MD5="8645da06a40253e260fc22714f65be12"
LIBRARY_MD5="47e075cb872218c7523a8bd4c8971d16"

SANDBOX_SIZE_MB=579
LIBRARY_SIZE_MB=456

CITY_DIR="${HOME}/.bim/City"

# ── Helpers ──
info()  { echo "  [INFO]  $*"; }
ok()    { echo "  [  OK]  $*"; }
fail()  { echo "  [FAIL]  $*" >&2; exit 1; }

check_command() {
    command -v "$1" >/dev/null 2>&1 || fail "$1 not found. Please install it."
}

verify_md5() {
    local file="$1" expected="$2"
    local actual
    if command -v md5sum >/dev/null 2>&1; then
        actual=$(md5sum "$file" | cut -d' ' -f1)
    elif command -v md5 >/dev/null 2>&1; then
        actual=$(md5 -q "$file")
    else
        info "md5 tool not found — skipping checksum verification"
        return 0
    fi
    if [ "$actual" = "$expected" ]; then
        ok "Checksum OK: $(basename "$file")"
        return 0
    else
        fail "Checksum MISMATCH: $(basename "$file") expected=$expected got=$actual"
    fi
}

download_file() {
    local url="$1" dest="$2" size_mb="$3" md5="$4"
    local filename
    filename=$(basename "$dest")

    if [ -f "$dest" ]; then
        info "$filename already exists — verifying..."
        verify_md5 "$dest" "$md5"
        return 0
    fi

    info "Downloading $filename (~${size_mb}MB)..."
    if curl -L --progress-bar -o "${dest}.tmp" "$url"; then
        mv "${dest}.tmp" "$dest"
        verify_md5 "$dest" "$md5"
    else
        rm -f "${dest}.tmp"
        fail "Download failed: $url"
    fi
}

# ── Main ──
echo ""
echo "  BOMTree Demo Setup"
echo "  ══════════════════"
echo "  1,063,911 elements · 786 buildings · 124,591 unique meshes"
echo "  Total download: ~$((SANDBOX_SIZE_MB + LIBRARY_SIZE_MB))MB"
echo ""

check_command curl

# Create City/ directory
mkdir -p "$CITY_DIR"
ok "City directory: $CITY_DIR"

# Download databases
download_file "$SANDBOX_URL"  "$CITY_DIR/sandbox_1M_extracted.db"  "$SANDBOX_SIZE_MB" "$SANDBOX_MD5"
download_file "$LIBRARY_URL"  "$CITY_DIR/component_library.db"     "$LIBRARY_SIZE_MB" "$LIBRARY_MD5"

echo ""
echo "  ✓ Setup complete!"
echo ""
echo "  Next steps:"
echo "  ───────────"
echo "  1. Open Blender (with Bonsai addon enabled)"
echo "  2. In the 3D viewport, press N to open the side panel"
echo "  3. Go to BIM tab → set Federation Database Path to:"
echo ""
echo "     $CITY_DIR/sandbox_1M_extracted.db"
echo ""
echo "  4. Press Ctrl+Shift+A to start Direct Stream"
echo "  5. Buildings appear nearest-first — envelope in seconds"
echo "  6. Fly inside any building for full MEP/ELEC detail"
echo "  7. Press Sun button for presentation-ready viewport"
echo ""
echo "  Files downloaded:"
echo "    $CITY_DIR/sandbox_1M_extracted.db  (${SANDBOX_SIZE_MB}MB)"
echo "    $CITY_DIR/component_library.db     (${LIBRARY_SIZE_MB}MB)"
echo ""
