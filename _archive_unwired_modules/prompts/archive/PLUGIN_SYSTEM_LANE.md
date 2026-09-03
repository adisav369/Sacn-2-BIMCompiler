# ⚠ DO NOT REMOVE — PLUGIN_SYSTEM_LANE scope: implement Fold Engine OSGi-like plugin system. Read the log after every run.

# Fold Engine Plugin System — Implementation Lane

**Status:** NOT STARTED  
**Priority:** Non-blocking background work  
**Branch:** start from fresh `origin/main` or a dedicated `feat/plugin-system` branch

---

## Session Startup — Read These First

In order:
1. `build/erp/kernel_ops.js` — op schema (add `PLUGIN_*` op types here, additive only)
2. `build/erp/ad_callout.js:36` — `registerHandler` pattern to replicate
3. `build/erp/ad_modelval.js:651` — `fireHooks` pattern to replicate
4. `build/erp/ad_process.js:68` — `registerHandler` with `meta.kind` pattern
5. `build/erp/erp_engine.js:1-30` — UMD wrapper + pure-logic contract (no DB binding)
6. `docs/ERP_COVERAGE_MATRIX.md` — current state; plugin system does NOT touch the matrix

---

## Spec

### What Already Exists (do not replace — wrap only)

| File | Hook | Plugin contribution |
|---|---|---|
| `build/erp/ad_modelval.js:654` | `fireHooks(timing, info, ctx)` | BEFORE/AFTER SAVE validator |
| `build/erp/ad_callout.js:36` | `registerHandler(name, fn)` | Field callout |
| `build/erp/ad_process.js:69` | `registerHandler(classname, fn, meta)` | SvrProcess / report |
| `build/erp/post_resolver.js` | token dispatch map | GL token resolver |
| `build/erp/ad_docfsm.js` | FSM transition map | Document lifecycle |
| `build/erp/kernel_ops.js` | `op_type` TEXT column | Lifecycle audit ops |

Engine contract from `erp_engine.js`: **pure logic, no DB binding, host injects `query(sql) → rows[]`.** Preserve this.

---

### Bundle Structure

```
my-plugin.foldbundle/
├── manifest.json
├── activator.js
└── handlers/
    ├── validators.js
    ├── callouts.js
    ├── processes.js
    └── tokens.js
```

**`manifest.json`:**
```json
{
  "id": "com.example.manufacturing",
  "version": "1.0.0",
  "engineVersion": ">=0.9.0",
  "requires": {},
  "contributions": {
    "validator": { "module": "handlers/validators.js", "tables": ["M_Production"] },
    "callout":   { "module": "handlers/callouts.js" },
    "process":   { "module": "handlers/processes.js" },
    "token":     { "module": "handlers/tokens.js" }
  },
  "activator": "activator.js"
}
```

**`activator.js`:**
```javascript
export async function activate(ctx) {
  // ctx.modelval   → ad_modelval module
  // ctx.callout    → ad_callout module
  // ctx.process    → ad_process module
  // ctx.postTokens → post_resolver token map (plain object)
  // ctx.db.query   → read-only SQL
  // ctx.ops.append → append kernel_ops op
}
export async function deactivate(ctx) {}
```

---

### Phase A — `build/erp/plugin_registry.js` (~200 LOC)

New file, same UMD wrapper as `erp_engine.js`. Implement:

1. `installBundle(manifestUrl)` — fetch + validate manifest, write to OPFS, append `PLUGIN_INSTALL` op via existing `appendOp`.
2. `startBundle(id)` — topological sort on `requires`, call `activate(ctx)`.
3. `stopBundle(id)` / `uninstallBundle(id)` — call `deactivate`, remove OPFS files, append op.
4. Lifecycle states: `INSTALLED → RESOLVED → ACTIVE → STOPPED → UNINSTALLED`. Stored in IndexedDB.
5. `resolveDeps(manifests[])` — semver compare (~15 LOC inline) + topological sort. Reject cycles.

### Phase B — Extend `kernel_ops.js` op types (additive)

Add to the `op_type` enum comment block only:
```
PLUGIN_INSTALL | PLUGIN_UNINSTALL | PLUGIN_START | PLUGIN_STOP
```
`parameters` = JSON `{id, version, manifestUrl}`. No schema change.

### Phase C — Three witness plugins (`build/erp/fixtures/plugins/`)

Each runs via `bash build/erp/run_witness.sh`. Each produces `§`-tagged output.

- **C-1** callout plugin — registers on `M_Product.Name`, uppercases. Witness: `§PLUGIN-CALLOUT name=WIDGET`
- **C-2** validator plugin — BEFORE SAVE `M_Production` rejects qty < 0. Witness: `§PLUGIN-VALRULE M_Production qty<0 REJECT`
- **C-3** token plugin — resolves `{Production.WIP}` → account ID from seed. Witness: `§PLUGIN-TOKEN WIP=50100`

---

### Witness Contract

All phases must produce these lines before the session closes:
```
§PLUGIN-INSTALL id=com.example.manufacturing version=1.0.0 opId=<n>
§PLUGIN-START   id=com.example.manufacturing state=ACTIVE
§PLUGIN-CALLOUT name=WIDGET
§PLUGIN-VALRULE M_Production qty<0 REJECT
§PLUGIN-TOKEN   WIP=50100
```
No log line = not done.

---

### Out of Scope (do not build)

- No signing gate (reserve `signature` field in manifest, don't enforce)
- No `ServiceRegistry` abstraction — the five existing `registerHandler`/`fireHooks` APIs are enough
- No CLI packaging tool — a `.foldbundle` is a folder; `installBundle(url)` fetches it
- No iframe sandbox
- No native SQLite C extensions

---

### OSGi Mapping (for iDempiere developers)

| OSGi | Fold Engine equivalent |
|---|---|
| Bundle JAR | `.foldbundle` directory |
| `MANIFEST.MF` | `manifest.json` |
| `BundleActivator` | `activator.js` `activate`/`deactivate` |
| `BundleContext` | `ctx` injected by host |
| Service Registry | Not phase-A |
| Extension Point | `contributions` → existing `registerHandler`/`fireHooks` |
| Fragment Bundle | Out of scope |
| Start Level | `requires` graph order; no numeric levels |

---

### Phase D — Plugin Engine pill (UI, after Phase C witnesses green)

**Icon:** `plug` (Lucide) — add to `ICONS` in `deploy/dev/panels.js`:
```javascript
plug: { svg: '<path d="M12 22v-5"/><path d="M9 8V2"/><path d="M15 8V2"/><path d="M18 8v5a4 4 0 0 1-4 4h-4a4 4 0 0 1-4-4V8z"/>', trl: null, key: null, desc: 'Plugin Engine' },
```

**Bundle format (single-file, no ZIP, no OPFS):**  
Distribute as one ES module on any raw URL (e.g. `raw.githubusercontent.com`). No directory fetching, no ZIP unpacking.
```javascript
// my-plugin.js — the whole bundle, one URL
export const manifest = { id: 'com.example.manufacturing', version: '1.0.0', requires: {} };
export async function activate(ctx) { /* register handlers */ }
export async function deactivate(ctx) { /* cleanup */ }
```
`installBundle(url)` does `import(url)`, reads `manifest`, calls `activate(ctx)`. No OPFS writes needed for the simple case. Bundle state (ACTIVE/STOPPED) lives in IndexedDB under `{id, url, state}`.

**This collapses iDempiere's two-step deploy into one:**  
iDempiere requires: (1) drop JAR into `plugins/` restart, (2) run 2Pack import separately.  
Our pill: paste URL → one click → plugin active. The `sql/setup.sql` equivalent runs inside `activate()`.

**Pill overlay — what it shows:**
- List of installed bundles: `id` / `version` / state chip (green ACTIVE, grey STOPPED)
- Install input: URL field + "Install" button → calls `installBundle(url)`
- Per-row: Start / Stop toggle + Uninstall button
- No settings, no signing UI in Phase D

**Witness:** `§PLUGIN-PILL install url=<url> id=com.example.manufacturing state=ACTIVE`

---

## DONE

**Phase A — `build/erp/plugin_registry.js` (W-PLUGIN)** ✅ — UMD host (`window.PluginRegistry` / node `module.exports`).
`create(host)` → `installBundle`/`startBundle`/`stopBundle`/`uninstallBundle` over the INSTALLED→RESOLVED→ACTIVE→
STOPPED→UNINSTALLED lifecycle; `resolveDeps(manifests[])` = inline semver (`satisfies`/`parseVer`/`cmpVer`) + DFS
topological sort with on-stack cycle detection. PURE host, no DB binding — host injects `db.query`, `ops.append`,
`import`, the engine modules, and (default in-memory) `store`. `engineVersion` gate enforced; `signature` reserved,
NOT enforced (no signing gate, per §Out of Scope).
- §-log: `§PLUGIN-INSTALL id=… version=1.0.0 opId=<n>` · `§PLUGIN-START id=… state=ACTIVE`

**Phase B — `build/erp/kernel_ops.js` op types** ✅ — additive comment-block enum on the `op_type` column:
`PLUGIN_INSTALL | PLUGIN_UNINSTALL | PLUGIN_START | PLUGIN_STOP`, `parameters` = JSON `{id,version,manifestUrl}`.
No schema change (op_type is free TEXT).

**Phase C — three witness bundles + `scripts/poc_plugin.js`** ✅ — `build/erp/fixtures/plugins/*.mjs`, single-file ES
modules (`manifest`+`activate`+`deactivate`), each contributes into a LIVE engine registry:
- C-1 `widget_callout.mjs` → `ad_callout.registerHandler` (M_Product.Name → upper) — `§PLUGIN-CALLOUT name=WIDGET`
- C-2 `production_validator.mjs` → `ad_modelval.registerValidator` (BEFORE_SAVE M_Production qty<0) — `§PLUGIN-VALRULE M_Production qty<0 REJECT`
- C-3 `wip_token.mjs` → contributes `{Production.WIP}` into `post_resolver.TOKENS`, EXTRACTED from seed —
  `§PLUGIN-TOKEN WIP=50005 value=14130 name="Work In Process"` (the spec's `50100` was a placeholder; 50005 is the
  REAL `c_elementvalue_id` in `ad_full.db` — non-invent honored).
Witness `bash build/erp/run_witness.sh scripts/poc_plugin.js` → exit 0, `🟢 W-PLUGIN PASS`. Falsifiers fire: dependency
CYCLE (`X↔Y`) + semver conflict rejected; kernel_ops audit = 3 install / 3 start / 1 stop / 1 uninstall.

**Phase D — Plugin Engine pill** ✅ (deployed erp sw v670) — `plug` icon already in `icons.js`. NEW
`build/erp/plugin_overlay.js` (`window.PluginEngine.open` — paste raw ES-module URL → one click → ACTIVE; bundle state
`{id,url,version,state}` persists in IndexedDB `fold_plugins`, re-activates on open). `pills_idmp.json` +`plugin`
(order 8.5); `idempiere.html` wires `ad_callout.js`+`plugin_registry.js`+`plugin_overlay.js` and binds
`IdmpPillActions.plugin` (injects db/adQ/KO + engine registries). Three example bundles precached under `erp/plugins/`.
Localhost whitebox smoke (`/tmp/wt-plugin/smoke_plugin_pill.js`, Playwright) PASS: scripts load · pill bound from
manifest · overlay opens via real handler · install→`§PLUGIN-PILL install url=… id=com.example.widget-callout state=ACTIVE`
· callout contributed + uppercases · reload → rehydrates ACTIVE from IndexedDB.
