# ⚠ DO NOT REMOVE — MIGRATE STATUS PANEL: clipboard relay addition
# Paste-to-start: `proceed with prompts/MIGRATE_STATUS_PANEL_RELAY.md`
# Scope: add the clipboard relay as a live workaround note inside the 🔵 N/A band of
#   docs/migrate_status_panel.html. One short addition, PROPOSE before editing (subjective copy).
#   A LATER session will restructure the full panel into SERVER | NON-SERVER columns.
# READ THE LOG after every run. NON-INVENT: copy from DistributedERP.md §5.2b + PR #300 facts only.
# PROPOSE the exact wording first, wait for approval, then edit. (feedback_propose_before_editing_docs.md)

---

## Context

`docs/migrate_status_panel.html` (served at localhost:7788/migrate_status_panel.html) has four bands:
🟢 Done · 🟠 Pending-data · 🔴 Pending-capability · 🔵 N/A deleted-by-architecture.

The 🔵 band currently lists:
- ZK web UI → browser renderer
- Generated X_* ORM → AD as data
- Server-side HTML lib → no server
- JDBC · OSGi · app server → no server of record
- **Web services tier → the signed op-log is the wire**  ← the relevant line

The clipboard relay (PR #300, shipped 2026-06-14) is the LIVE DEMO of "signed op-log is the wire"
carried by the user's own social channel — exactly `DistributedERP.md §5.2b`. It proves that
cross-device op delivery needs no web-services tier: WhatsApp/email/SMS carry the blob, the
receive box replays it, the shipment appears. The dumb relay server is the same format, no rewrite.

This is NOT a new feature of the panel — it's a concrete witness for the claim already there.

---

## Task

**Step 1 — PROPOSE** (do not edit yet): draft the addition as a `<li>` or `<div class="foot">`
extension inside the 🔵 band's `<div class="foot">` block, OR as a new short paragraph below the
existing foot line. Keep it ≤3 sentences. Source only from:
- DistributedERP.md §5.2b (the social-channel claim)
- PR #300 witness facts: §CL-SERIAL round-trip + §CL-UUID idempotency + clipboard→WhatsApp→paste→Apply
- "Later session will restructure into SERVER | NON-SERVER" — do NOT pre-empt that restructure

**Step 2 — WAIT** for user approval of the wording.

**Step 3 — EDIT** `docs/migrate_status_panel.html` only after approval.

**Step 4 — VERIFY** at `localhost:7788/migrate_status_panel.html` — confirm the 🔵 band renders
the addition correctly, no layout break. (The panel has its own CSS; check on mobile width too.)

---

## Pre-Pinned Facts

- **Source file**: `docs/migrate_status_panel.html` (174 lines). The 🔵 band is lines ~151–167.
  There is also `build/erp/preview_staging/migrate_status_panel.html` (older, 6.6KB — do NOT edit).
- **The foot line to extend** (line ~164):
  `<div class="foot"><span…></span><b style="color:var(--green)">Granted in exchange</b> — the 40%
  deletion is what makes the engine <b>offline-first</b>, <b>serverless</b>, and <b>per-device
  shardable</b>… live today…</div>`
- **Witness to cite**: `scripts/poc_oplog_clipboard.js` (W-OPLOG-CLIPBOARD, PR #300 erp sw v672).
- **The upgrade path sentence** (from DistributedERP.md §5.2b): "The dumb relay server is the
  upgrade path — same op format, different pipe."
- **Do NOT** restructure into SERVER | NON-SERVER — that is a future session's task.
- **Do NOT** touch the preview_staging copy.

---

## OUTSTANDING

- [ ] PROPOSE wording (≤3 sentences, sourced only from §5.2b + PR #300)
- [ ] User approves
- [ ] Edit `docs/migrate_status_panel.html` 🔵 band foot
- [ ] Verify at localhost:7788 — no layout break
