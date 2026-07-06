# S220 — web-ifc WASM MIME type failure

## Problem
Import IFC drop zone on dev landing page fails with:
```
wasm streaming compile failed: TypeError: WebAssembly: Response has unsupported MIME type 'application/json' expected 'application/wasm'
falling back to ArrayBuffer instantiation
failed to asynchronously prepare wasm: both async and sync fetching of the wasm failed
Aborted(both async and sync fetching of the wasm failed)
```

## Root cause
NOT an OCI MIME type issue. The `web-ifc.wasm` file is NOT hosted on OCI.

The real problem: `import_worker.js` loads `web-ifc-api-iife.js` from unpkg CDN via `importScripts()`. When `ifcApi.Init()` is called, web-ifc internally calls `locateFile('web-ifc.wasm')` which resolves RELATIVE TO THE WORKER'S URL — i.e. it fetches `bim-ootb-dev/sandbox/web-ifc.wasm` from OCI, which returns a 404/JSON error page → wrong MIME type.

## Fix
Pass a `customLocateFileHandler` to `Init()` that points to the CDN:
```js
await ifcApi.Init(function(path) {
  return 'https://unpkg.com/web-ifc@0.0.66/' + path;
}, true);
```

`true` = `forceSingleThread` (workers can't spawn sub-workers reliably).

## Fix applied
In `deploy/dev/import_worker.js`, define `Module.locateFile` BEFORE `importScripts()`:
```js
var Module = { locateFile: function(path) {
  return 'https://unpkg.com/web-ifc@0.0.66/' + path;
}};
importScripts('https://unpkg.com/web-ifc@0.0.66/web-ifc-api-iife.js');
```

## Debug log tags
- `§WASM_LOCATE` — locateFile called, shows path being resolved
- `§WORKER_LOADED` — IIFE bundle loaded successfully
- `§WASM_INIT` — Init() starting/done
- `§IMPORT_FATAL` — unhandled error with message
- `§IMPORT_STACK` — stack trace on failure

## Status: FIXED in deploy/dev/import_worker.js

## Screenshot
`~/Pictures/Screenshots/Screenshot from 2026-04-24 07-33-52.png`
