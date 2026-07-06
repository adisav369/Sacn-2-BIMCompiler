# ⚠ DO NOT REMOVE — WH SHIP-LATER CLIPBOARD RELAY
# Paste-to-start: `proceed with prompts/WH_SHIP_LATER_CLIPBOARD.md`
# Scope: DEMO of serverless cross-device op-log transport via clipboard/paste.
#   The relay IS the human + whatever social channel they trust (WhatsApp, email, SMS, Telegram).
#   No server, no Gist, no polling. The op log is self-contained text. Any pipe works.
# READ THE LOG after every run (exit code ≠ evidence).
# ALL poc_* via `bash build/erp/run_witness.sh scripts/poc_X.js`.
# NON-NEGOTIABLE: newVerbs=[] (NO engine/fold changes anywhere) · Lucide-only icons (erp/icons.js) ·
#   NON-INVENT (extract ids from live code, never guess) · bim-ootb edits in /tmp/wt-* off fresh origin/main.

---

## Concept

POS "deliver later" commits a signed op group (C_Order CO + M_InOut DR) to the local kernel.
Today it persists to IDB so the walk page can read it on the SAME device.

This lane adds a SECOND transport: the committed op group is serialized to a compact base64 blob,
copied to clipboard, and shared via any social channel. The WH walk user pastes it into a receive
box and hits "Apply" — the ops replay into the walk's kernel, the pending shipment appears in the
selector, and the pick proceeds exactly as if both users were on the same device.

The dumb relay (erp_relay_server.js) is the same pattern with a persistent URL instead of
clipboard. This demo proves the architecture, not the pipe.

---

## Pre-Pinned Facts (verified 2026-06-14 — do NOT rediscover wrong)

1. **Deliver-later handler**: `bim-ootb/erp/pos_lens.js` ~line 1226 — `dlBtn` click →
   `POS.buildDeliverLaterGroup` → `cfg.KO.commitGroup(cfg.opDb, g.ops.map(...), {})` → `.then(res =>
   cfg.persist())`. The `res.gid` identifies the committed group in `kernel_ops`.

2. **Op structure in kernel**: `kernel_ops` table columns:
   `id, op_uuid, timestamp, op_type, parameters, input_guids, output_guid, prev_hash, op_hash, sig, gid`
   After `commitGroup`, query `SELECT op_uuid, op_type, parameters FROM kernel_ops WHERE gid = ?`
   to get the minimal portable set.

3. **op_uuid is cross-device clash-free** (`kernel_ops.js` line 68): `commitGroup` honours a
   caller-supplied `op_uuid` verbatim (line 297: `src.op_uuid || src.opUuid || mint_uuid()`).
   This makes replay idempotent — same `op_uuid` on the receiving device = same identity, no double-apply.

4. **IDB sidecar is the seam** (`wh_walk.js` line 136-166): the walk reads the sidecar from
   IDB `bim_ootb_cache` / store `dbs` / key `idmp_kanban_proj` — a full SQLite DB blob. It reads
   the `kernel_ops` rows from that blob and folds them via `WHRoute.openPosDocsFromOps(rows)`.
   The write-back path (line 758) does `tx.objectStore('dbs').put(out, 'idmp_kanban_proj')`.
   The RECEIVE flow targets this same key: open the existing blob (or fresh DB), call
   `commitGroup` on it with the incoming ops (honouring `op_uuid`), write the updated blob back.

5. **WH walk selector refresh**: after writing the sidecar, call the same pos-docs check the
   walk runs on load. Grep for it in `wh_walk.js` before coding — the live function name is
   the source of truth. Do NOT invent a new path.

6. **Witness to re-run after any change**:
   - `WT_ROOT=<wt> bash build/erp/run_witness.sh scripts/poc_wh_pos_pick_live.js` — full POS→walk loop
   - `WT_ROOT=<wt> bash build/erp/run_witness.sh scripts/poc_wh_walk_live.js` — W-WH-LIVE

7. **Icons**: `erp/icons.js` — add `clipboard` (Lucide path) if not present. Check first:
   `grep -n 'clipboard' bim-ootb/erp/icons.js`. Do NOT hardcode SVG inline in pos_lens.js.

8. **eslint gate**: `POSCore`/`InOutConfirm` already declared in `eslint.globals.json`. Any new
   global you reference in wh_walk.js must be added there too.

---

## Spec

### §CL-1 — Sender: "Copy op log" button in POS deliver-later receipt

**File**: `bim-ootb/erp/pos_lens.js`
**Where**: inside the `.then(res => ...)` block after the receipt text is set (~line 1255).

After `commitGroup` resolves and `res.gid` is available:
1. Query `cfg.opDb`: `SELECT op_uuid, op_type, parameters FROM kernel_ops WHERE gid = ?` → rows
2. Serialize: `btoa(JSON.stringify({ v: 1, ops: rows.map(r => ({ op_uuid: r[0], op_type: r[1], params: JSON.parse(r[2]) })) }))`
3. Inject a "📋 Copy op log" button below the receipt line (reuse existing receipt container).
   Button id: `pos-copy-oplog`. On click: `navigator.clipboard.writeText(blob)` → button label
   changes to "Copied!" for 2s → reverts.
4. `§`-log: `§POS-OPLOG-COPY gid=<res.gid> ops=<n> bytes=<blob.length>`

No new verbs. No fold changes. UI only.

### §CL-2 — Receiver: paste box in WH walk

**File**: `bim-ootb/viewer/wh_walk.js`
**Where**: near the top of the WH walk panel (above the route list or as a collapsible section).

Add a collapsed "Paste op log" row (collapsed by default, toggle on tap):
- `<details>` + `<summary>` ("Paste incoming op log") — zero extra JS needed for toggle
- Inside: `<textarea id="wh-oplog-paste" rows="3" placeholder="Paste op log here…">` +
  `<button id="wh-oplog-apply">Apply</button>`

On `#wh-oplog-apply` click:
1. Read `#wh-oplog-paste` value, trim.
2. Decode: `JSON.parse(atob(value))` — if it throws, show error in status row and return.
3. Validate: `payload.v === 1 && Array.isArray(payload.ops) && payload.ops.length > 0`
4. Open IDB `bim_ootb_cache` via `_openCacheDB()` (the shared helper, wh_walk.js — same call
   used for the SIDECAR-WRITEBACK at line 740; use it verbatim, never hardcode a version).
5. Read the existing blob at `dbs/idmp_kanban_proj` (may be absent on a fresh device — that's fine,
   start with a fresh `initSqlJs` DB). Open the blob as a sql.js DB.
6. **Pre-gate (idempotency):** `kernel_ops` has NO unique constraint on `op_uuid` (verified). Before
   calling `commitGroup`, query: `SELECT COUNT(*) FROM kernel_ops WHERE op_uuid IN (…)` with the
   incoming uuids. If count > 0 → already applied → show "Already applied" and return. This is the
   paste-twice guard (proven by `§CL-UUID` in `poc_oplog_clipboard.js`).
7. Call `KernelOps.commitGroup(sidecarDb, payload.ops.map(o => ({ op_type: o.op_type, op_uuid: o.op_uuid, params: o.params })), {})`.
7. Export the updated DB: `sidecarDb.export()` → write blob back to `dbs/idmp_kanban_proj`
   (same `tx.objectStore('dbs').put(out, 'idmp_kanban_proj')` pattern as line 758).
8. Trigger the pos-docs reload (grep the live function name in wh_walk.js — do NOT invent).
9. `§`-log: `§WH-OPLOG-APPLY ops=<n> ok=<bool> reason=<if fail>`
10. Clear the textarea on success. Show "Applied — check the selector" in the walk status row.

No new verbs. No engine changes.

### §CL-3 — Witness: W-OPLOG-CLIPBOARD (Node.js, headless)

**File**: `scripts/poc_oplog_clipboard.js`

Proves:
- `§CL-SERIAL` — `buildDeliverLaterGroup` ops → serialize → deserialize → same op_type/params round-trip.
- `§CL-UUID` — deserialized ops carry the original `op_uuid`; `commitGroup` on a fresh db honours it;
  a second `commitGroup` with the SAME `op_uuid` does NOT insert a duplicate row.
- `§CL-DELTA` — after replay, `qtyOnHand` fold over the replayed ops produces the same movement delta
  as the original (movementqty matches to the unit).

Run: `bash build/erp/run_witness.sh scripts/poc_oplog_clipboard.js`

---

## Implementation Order

1. Write `poc_oplog_clipboard.js` witness first (spec §CL-3). Run it. Must pass before touching UI.
2. Add `clipboard` icon to `erp/icons.js` if absent.
3. Implement §CL-1 in `pos_lens.js` (sender button).
4. Implement §CL-2 in `wh_walk.js` (receive box).
5. Re-run regression witnesses:
   - `WT_ROOT=<wt> bash build/erp/run_witness.sh scripts/poc_wh_pos_pick_live.js`
   - `WT_ROOT=<wt> bash build/erp/run_witness.sh scripts/poc_wh_walk_live.js`
   Both must PASS with `§POS-CENT maxDiff=0c` and `§WH PICK-COMPLETE` intact.
6. ONE PR. erp sw bump + viewer sw bump (if wh_walk.js touched). sw.js conflict rule: keep both
   precache hunks, take higher CACHE_VERSION.

---

## Demo Script (for the POS ship-later demo)

1. Open POS tab → load warehouse building → add items to cart → tap "Deliver Later"
2. Receipt appears + "Copy op log" button → tap it → blob on clipboard
3. Paste into WhatsApp/email/SMS → send to colleague (or other device, or other tab)
4. Colleague opens WH walk tab → expands "Paste incoming op log" → pastes → taps "Apply"
5. Pending shipment appears in the walk selector → proceed with normal pick walk

The relay is the human. Any pipe works. The dumb erp_relay_server.js is the same flow
with a persistent URL instead of clipboard — upgrade path, same format, no rewrite.

---

## OUTSTANDING

- [x] §CL-3 witness (`poc_oplog_clipboard.js`) — W-OPLOG-CLIPBOARD PASS (§CL-SERIAL + §CL-UUID + §CL-DELTA)
- [x] §CL-1 sender button (`pos_lens.js`) — "Copy op log" after deliver-later receipt
- [x] §CL-2 receive box (`wh_walk.js`) — collapsed paste box, op_uuid pre-gate, sidecar write, draftPick refresh
- [x] Regression witnesses PASS — W-WH-POS-PICK-LIVE + W-WH-LIVE
- [x] PR #300 — erp sw v672, viewer sw v653, auto-merge enabled 2026-06-14

# DONE — 2026-06-14 (Sonnet session)
