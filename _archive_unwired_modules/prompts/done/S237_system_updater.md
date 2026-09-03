# S237 — BIM OOTB System Updater

# ⚠ DO NOT REMOVE
Scope: In-browser self-update mechanism — check OCI for new releases, verify integrity, test before accepting.
Read the log after every run.

## Problem
BIM OOTB runs as static files from OCI. Users have no way to know when code or data updates are available, and no safe way to apply them. Currently: manual re-download or cache-bust.

## Architecture

### Version Manifest
Each OCI bucket (live + dev) publishes a `manifest.json`:
```json
{
  "version": "3.2.0",
  "released": "2026-04-28T12:00:00Z",
  "channel": "live",
  "files": {
    "index.html":       { "sha256": "abc123...", "size": 45200 },
    "main.js":          { "sha256": "def456...", "size": 12800 },
    "panels.js":        { "sha256": "ghi789...", "size": 8400 },
    "streaming.js":     { "sha256": "jkl012...", "size": 15600 },
    "nlp.js":           { "sha256": "mno345...", "size": 9200 },
    "rates.js":         { "sha256": "pqr678...", "size": 6100 },
    "locale_loader.js": { "sha256": "stu901...", "size": 3200 },
    "locales/en_MY.js": { "sha256": "vwx234...", "size": 4800 }
  },
  "changelog": [
    "3.2.0 — Locale system, settings gear, updater",
    "3.1.0 — 2D Plans browser DXF viewer",
    "3.0.0 — Wizard, IFC import, Excel export"
  ],
  "min_version": "3.0.0"
}
```

### Update Channels
| Channel | OCI Bucket | Use |
|---------|-----------|-----|
| `live` | `bim-ootb-live` | Production — stable releases only |
| `dev` | `bim-ootb-dev` | Development — latest features, may break |

User selects channel in settings gear. Default = `live`.

### Update Flow
```
User clicks "Check for Updates" in ⚙ panel
  │
  ├─ 1. FETCH manifest.json from selected channel
  │     GET {OCI_BASE}/manifest.json
  │     Status: "Checking for updates..."
  │
  ├─ 2. COMPARE versions
  │     Current version (embedded in index.html meta tag) vs manifest.version
  │     If same → "You're up to date (v3.2.0)" → done
  │     If newer → show changelog + "Update available: v3.2.0 → v3.3.0"
  │
  ├─ 3. USER CONFIRMS → "Apply Update"
  │
  ├─ 4. FETCH changed files only (diff against current sha256)
  │     Status: "Downloading 3 of 7 files..."
  │     Store in IndexedDB('bim_ootb_update_staging')
  │
  ├─ 5. INTEGRITY CHECK (security guard)
  │     For each fetched file:
  │       sha256(content) === manifest.files[name].sha256
  │     If ANY mismatch → ABORT, show which file failed
  │     Status: "Verifying integrity... 7/7 ✓"
  │
  ├─ 6. SMOKE TEST (test before accepting)
  │     Load updated index.html in a hidden iframe
  │     Check: DOM renders, no JS errors, _TRL loads, Three.js initializes
  │     Status: "Testing update..."
  │     If test fails → ABORT, show error, keep current version
  │
  ├─ 7. ACTIVATE
  │     Service worker cache: swap staging → active
  │     Update localStorage version marker
  │     Status: "Update applied! Reloading..."
  │     Auto-reload after 2s
  │
  └─ 8. ROLLBACK (if anything goes wrong post-reload)
        Previous version files kept in IndexedDB('bim_ootb_rollback')
        Settings gear shows "Rollback to v3.2.0" if update < 24hrs old
```

### Security Guard
1. **SHA-256 integrity** — every file verified against manifest hash
2. **HTTPS only** — OCI objectstorage is TLS by default
3. **No eval()** — updated JS files loaded via `<script>` tags, not eval'd
4. **Manifest signature** (future) — sign manifest.json with Ed25519 key
5. **Min version gate** — `manifest.min_version` prevents downgrade below safe floor
6. **Same-origin** — files fetched from same OCI domain as current page
7. **User consent** — never auto-update, always show changelog + confirm

### Smoke Test Details
```js
function smokeTestUpdate(stagedFiles) {
  return new Promise(function(resolve, reject) {
    var iframe = document.createElement('iframe');
    iframe.style.cssText = 'position:fixed;left:-9999px;width:1px;height:1px;';
    iframe.srcdoc = stagedFiles['index.html'];
    var timer = setTimeout(function() {
      reject(new Error('Smoke test timeout (10s)'));
    }, 10000);

    iframe.onload = function() {
      try {
        var doc = iframe.contentDocument;
        // DOM check
        if (!doc.getElementById('canvas')) throw new Error('Missing #canvas');
        // JS error check
        var errors = iframe.contentWindow._OOTB_ERRORS || [];
        if (errors.length) throw new Error('JS errors: ' + errors[0]);
        // _TRL check
        if (!iframe.contentWindow._TRL) throw new Error('Missing _TRL');
        // Three.js check
        if (!iframe.contentWindow.THREE) throw new Error('Missing THREE');

        clearTimeout(timer);
        document.body.removeChild(iframe);
        resolve(true);
      } catch(e) {
        clearTimeout(timer);
        document.body.removeChild(iframe);
        reject(e);
      }
    };
    document.body.appendChild(iframe);
  });
}
```

### Service Worker Integration
`sw.js` already exists (`bim-ootb-v2` cache). Extend it:
```js
// In sw.js — handle update activation message
self.addEventListener('message', function(event) {
  if (event.data.type === 'ACTIVATE_UPDATE') {
    var staging = event.data.files; // {name: content} map
    caches.open(CACHE_NAME).then(function(cache) {
      // Backup current → rollback cache
      // Write staging → active cache
      // Notify client: ready to reload
    });
  }
});
```

### UI in Settings Panel
```
┌─────────────────────────────────┐
│  ⚙ Settings                    │
│                                 │
│  🇲🇾 Language: English (MY)     │
│  💰 Currency: RM / USD          │
│  📊 Rate Source: CIDB 2024      │
│                                 │
│  ─── System ───                 │
│  Channel: [● Live] [○ Dev]     │
│  Version: v3.2.0               │
│  [🔄 Check for Updates]        │
│                                 │
│  [↩ Rollback to v3.1.0]       │  ← only if recent update
│                                 │
│  [Reset to Defaults]           │
└─────────────────────────────────┘
```

## File Inventory
| File | Purpose |
|------|---------|
| `updater.js` | Update check, fetch, verify, smoke test, activate |
| `sw.js` | Service worker cache swap (extend existing) |
| `manifest.json` | Version + file hashes (generated at deploy time) |
| `scripts/generate_manifest.sh` | Build script: hash all deploy files → manifest.json |

## Deploy-Time Manifest Generation
```bash
#!/bin/bash
# scripts/generate_manifest.sh — run before OCI upload
DIR="deploy/dev"
VERSION=$(grep -oP 'v[\d.]+' "$DIR/index.html" | head -1)
echo "{"
echo "  \"version\": \"$VERSION\","
echo "  \"released\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\","
echo "  \"channel\": \"dev\","
echo "  \"files\": {"
first=true
for f in "$DIR"/*.{html,js}; do
  [ -f "$f" ] || continue
  name=$(basename "$f")
  hash=$(sha256sum "$f" | cut -d' ' -f1)
  size=$(stat -c%s "$f")
  $first || echo ","
  printf '    "%s": {"sha256":"%s","size":%d}' "$name" "$hash" "$size"
  first=false
done
echo ""
echo "  }"
echo "}"
```

## Testing (Playwright)

### Spec: `tests/specs/18-updater.spec.js`
| Test | What | §-tag |
|------|------|-------|
| 18.1 | Manifest fetch succeeds | §PW_UPD_MANIFEST |
| 18.2 | Version compare: same = "up to date" | §PW_UPD_CURRENT |
| 18.3 | Version compare: newer = shows changelog | §PW_UPD_AVAILABLE |
| 18.4 | Integrity check: valid hash passes | §PW_UPD_INTEGRITY_PASS |
| 18.5 | Integrity check: tampered file rejects | §PW_UPD_INTEGRITY_FAIL |
| 18.6 | Smoke test: good update passes | §PW_UPD_SMOKE_PASS |
| 18.7 | Smoke test: broken JS rejects | §PW_UPD_SMOKE_FAIL |
| 18.8 | Rollback restores previous version | §PW_UPD_ROLLBACK |

## DO NOT
- Do not auto-update without user consent
- Do not eval() fetched code — load via DOM script injection
- Do not skip integrity check — every byte verified
- Do not delete rollback cache for 24hrs
- Do not update if offline — fail gracefully with "No connection"
- Do not update manifest.json by hand — always generate via script
