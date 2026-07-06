# ⚠ DO NOT REMOVE — Scope guard
# Scope: ERP OOTB — Implementation tasks for kernel architecture
#        Theory and architecture: docs/ERP.md §11-§16
#        This prompt: concrete build tasks with acceptance criteria.
# Read the log after every run. Exit code is not evidence.
# Spec-first: implement only what is described in a § section below.

---

# ERP OOTB — Kernel Architecture Tasks

## Foundation

Read `docs/ERP.md` §11-§16 before this prompt. That document defines:
- §11: Three-layer architecture (Presentation / Logic / Data)
- §12: The 5-table foundation + journal
- §13: Compiled manifest — AD as compiler input, not runtime dependency
- §14: Kernel gravity — op log as constellation driver
- §15: Known monsters + 10 invariants + risk matrix
- §16: The revised seismic claim

This prompt contains **implementation tasks** that build on that theory.

---

## §1. The Three Known Monsters

### Monster 1: Two Tabs, One Record

**The scenario:**
User opens Partner "Seed Farm" in browser tab A, changes phone number.
Opens tab B (same browser, same erp.html), changes email.
Both write to the same in-memory SQLite. Tab A saves. Tab B saves.
Phone number is overwritten — silent data loss.

**How iDempiere handles it:**
Server-side row locking. `SELECT ... FOR UPDATE` on the record. Second
user sees "Record changed by another user" dialog. Forced to reload.

**How ERP OOTB can handle it:**
- BroadcastChannel (already in ad_ui.js) — tabs communicate.
- On `commitOp('AD_SAVE')`, broadcast `{type: 'RECORD_SAVED', table, id, timestamp}`.
- Other tabs listen. If they have the same record open, compare timestamps.
- If stale: show "Record updated in another tab — reload?" toast.
- No locking. No blocking. Just awareness.

**Risk level:** LOW for single-user. The user IS both tabs. They know
they're editing in two places. The toast is a reminder, not a gatekeeper.

**When it becomes HIGH:** Multi-device sync (phone + laptop). Future problem.
Solve with CRDT or operational transform when/if multi-device lands.

---

### Monster 2: Orphaned Operations (Document State Machine)

**The scenario:**
1. User creates Purchase Order #1001 (status: Drafted)
2. User completes PO #1001 (status: Completed)
3. User creates Invoice #5001 referencing PO #1001
4. User undoes PO #1001 completion → PO reverts to Drafted
5. Invoice #5001 now references a Draft PO — business logic violation

**How iDempiere handles it:**
DocAction framework. 460 lines of Java in `Doc.java`. State machine:
```
Drafted → InProgress → Completed → Closed
                    ↘ Voided
                    ↘ Reversed (creates counter-document)
```
Each transition checks: "Do downstream documents exist?" If yes, block
the undo/void. The user must reverse the Invoice first, then the PO.

**How ERP OOTB can handle it:**

Option A — **Kernel constraint check (50 lines):**
```javascript
// Before undoing a Completed document, check for dependents
function canUndoComplete(db, table, id) {
  var dependents = DOWNSTREAM[table]; // { C_Order: ['C_Invoice','M_InOut'] }
  if (!dependents) return true;
  for (var i = 0; i < dependents.length; i++) {
    var fk = table + '_ID';
    var count = db.exec(
      "SELECT COUNT(*) FROM [" + dependents[i] + "] WHERE " + fk + " = " + id
    );
    if (count.length && Number(count[0].values[0][0]) > 0) return false;
  }
  return true;
}
```
The `DOWNSTREAM` map is tiny — maybe 10 entries for all GardenWorld documents.
It's the state machine without the framework.

Option B — **Don't enforce, just warn:**
Let the undo happen. Mark the orphan with a visual indicator (red border,
warning icon). The user sees "Invoice #5001 references a Draft PO" and
decides what to do. This is actually what most small businesses prefer —
flexibility over rigidity.

**Risk level:** MEDIUM. Real money is involved. An Invoice referencing a
voided PO is a reconciliation nightmare. But Option A (50 lines) covers
95% of cases. The remaining 5% (multi-level cascading reversals) can wait.

**Recommendation:** Start with Option B (warn, don't block). Add Option A
constraints for Completed→Drafted transitions only. Expand as users hit
real edge cases.

---

### Monster 3: Gravity Lies (Op Log ≠ Business Significance)

**The scenario:**
User edits Partner "Seed Farm" 20 times fixing typos in the address.
kernel_ops records 20 AD_SAVE operations.
Bubble constellation shows Partners as the "hottest" entity.
But the user was just doing data cleanup — not meaningful business activity.

**How iDempiere handles it:**
Dashboard KPIs use document counts and monetary amounts, not edit frequency.
"Open Orders: 5, Total: $125,000" is meaningful. "Records edited: 20" is not.

**How ERP OOTB can handle it:**

Weight ops by significance:
```javascript
var GRAVITY_WEIGHTS = {
  'AD_SAVE_NEW':    1.0,   // Created a new record — high signal
  'AD_SAVE_UPDATE': 0.2,   // Edited existing — low signal (could be typo fix)
  'AD_DELETE':      0.5,   // Deliberate action
  'DOC_COMPLETE':   2.0,   // Business milestone — highest signal
  'DOC_VOID':       1.5,   // Significant decision
  'SESSION_START':  0.0    // Noise
};
```

Better yet — count **distinct record IDs** not raw ops:
```sql
SELECT json_extract(parameters, '$.table') AS tbl,
       COUNT(DISTINCT json_extract(parameters, '$.id')) AS distinct_records,
       COUNT(*) AS total_ops
FROM kernel_ops
WHERE undone = 0 AND timestamp > ?
GROUP BY tbl
```

20 edits on 1 record = gravity 1. 3 edits on 3 records = gravity 3.
The user who created 3 new invoices outweighs the user who fixed 1 address.

**Risk level:** LOW. Gravity is a UX hint, not a business rule. If it's
slightly wrong, the worst case is a bubble that's too bright. The user
taps it, sees 1 record, thinks "oh, that's just Seed Farm." No data loss.

**Recommendation:** Use distinct record count + op type weighting.
Revisit only if users report the constellation feels misleading.

---

## §2. The 10 Invariants

These are the business rules ERP OOTB MUST enforce. If kernel_ops can
handle all 10, the AD field settings become optional structural metadata.
If it can't, we know exactly where the gap is.

| # | Invariant | iDempiere mechanism | Kernel alternative |
|---|-----------|--------------------|--------------------|
| 1 | **Mandatory fields** — Name on Partner, DocumentNo on Order | AD_Field.IsMandatory + ModelValidator | commitOp rejects if manifest says field is mandatory and value is null/empty |
| 2 | **FK integrity** — C_BPartner_ID on Invoice must reference an existing partner | DB foreign key constraints | commitOp checks: does referenced record exist? (single SELECT) |
| 3 | **Document state transitions** — Draft→Complete→Close, not Draft→Close | DocAction state machine (460 lines Java) | Kernel state map: `{Drafted:['InProgress','Completed','Voided'], ...}` (20 lines) |
| 4 | **Downstream protection** — Can't void an Order with completed Invoices | DocAction.processIt() dependency check | `canUndoComplete()` — check DOWNSTREAM map (see §1 Monster 2) |
| 5 | **Sequence numbering** — DocumentNo must be unique and sequential | AD_Sequence table + server-side atomic increment | `MAX(DocumentNo) + 1` — safe in single-user browser. Collision impossible. |
| 6 | **Period closing** — Can't post to a closed accounting period | C_Period.IsActive check on document completion | If C_Period exists in DB, check on DOC_COMPLETE. If not, skip (no accounting). |
| 7 | **Duplicate prevention** — Can't create two Partners with same Value | AD_Column.IsUnique + DB unique constraint | commitOp checks: `SELECT COUNT(*) WHERE Value = ?` before INSERT |
| 8 | **Audit trail completeness** — Every change must be logged | AD_ChangeLog (field-level) | kernel_ops logs every commitOp with full parameters — already complete |
| 9 | **Undo boundary** — Can't undo past a session boundary | Not in iDempiere (no undo) | kernel_ops.compact() prunes ops older than 2 sessions. Already implemented. |
| 10 | **Currency consistency** — Amounts in document currency, not mixed | C_Currency + conversion rate tables | If multi-currency needed: store CurrencyISO in parameters. Otherwise: single currency, no conversion. |

**Assessment:** Invariants 1, 2, 5, 7, 8, 9 are trivial in kernel_ops.
Invariant 3 is 20 lines. Invariant 4 is 50 lines. Invariants 6 and 10
are conditional — only needed if the user has accounting/multi-currency.

**Total kernel enforcement code:** ~150 lines for all 10 invariants.
iDempiere uses ~15,000 lines (DocAction + ModelValidator + Callout framework)
for the same invariants plus 200 others that single-user browser ERP
doesn't need.

---

## §3. What iDempiere's Complexity Actually Buys (That We Keep)

Not everything is bloat. These AD structures earn their weight:

| AD structure | What it gives us | Keep or compile? |
|---|---|---|
| AD_Tab (TabLevel 0,1,2) | Bubble tier hierarchy — which tables are children of which | **Compile** to manifest. Tab order = drill order. |
| AD_Field (SeqNo, Name) | Field ordering and labels in the accordion | **Compile** to manifest. 5 fields per entry. |
| AD_Column (AD_Reference_ID) | Field type — render as text/dropdown/date/toggle | **Compile** to manifest. One integer per field. |
| AD_Column (FK target) | Which table a FK integer points to | **Compile** to manifest. Powers drill cascade. |
| AD_Ref_List | Dropdown options (DocStatus: DR/IP/CO/CL/VO) | **Compile** to manifest. Small lookup tables. |
| AD_Window (Name) | Window labels for constellation and breadcrumb | **Compile** to manifest. One string per window. |
| AD_Menu + AD_TreeNodeMM | Menu tree hierarchy | **Already compiled** to menu_seed.js / initbubble.json |

Everything else (AD_Element descriptions, AD_Process definitions, AD_Form,
AD_Workflow, multi-tenant columns, translation tables) — **leave in
ad_seed.db** as reference. Don't load at runtime. Available if someone
needs to look up a field description via the help panel.

---

## §4. The Compiled Manifest

**Source:** ad_seed.db (13MB, 1003 tables, 20,911 fields)
**Output:** manifest.json (~20KB, 7 curated windows, ~200 fields)
**Generator:** `scripts/compile_manifest.js` — reads ad_seed.db, outputs JSON

```json
{
  "version": 1,
  "generated": "2026-05-28T19:00:00Z",
  "windows": {
    "123": {
      "name": "Business Partner",
      "table": "C_BPartner",
      "tabs": [
        {
          "name": "Partner",
          "table": "C_BPartner",
          "level": 0,
          "fields": [
            {"col": "Name", "type": "string", "mandatory": true, "seq": 10},
            {"col": "Value", "type": "string", "mandatory": true, "seq": 20},
            {"col": "IsCustomer", "type": "yesno", "seq": 30},
            {"col": "IsVendor", "type": "yesno", "seq": 40},
            {"col": "C_BP_Group_ID", "type": "tableDirect", "fk": "C_BP_Group", "seq": 50}
          ]
        },
        {
          "name": "Contact",
          "table": "AD_User",
          "level": 1,
          "fk": "C_BPartner_ID",
          "fields": [
            {"col": "Name", "type": "string", "mandatory": true, "seq": 10},
            {"col": "EMail", "type": "string", "seq": 20},
            {"col": "Phone", "type": "string", "seq": 30}
          ]
        }
      ]
    }
  },
  "state_machine": {
    "C_Order": ["Drafted", "InProgress", "Completed", "Voided", "Closed"],
    "C_Invoice": ["Drafted", "InProgress", "Completed", "Reversed", "Voided"]
  },
  "downstream": {
    "C_Order": ["C_Invoice", "M_InOut"],
    "C_Invoice": ["C_Payment"],
    "M_InOut": []
  },
  "dropdowns": {
    "DocStatus": [
      {"value": "DR", "name": "Drafted"},
      {"value": "IP", "name": "In Progress"},
      {"value": "CO", "name": "Completed"},
      {"value": "VO", "name": "Voided"},
      {"value": "CL", "name": "Closed"},
      {"value": "RE", "name": "Reversed"}
    ]
  }
}
```

**Runtime payload:** initbubble.json (2KB) + manifest.json (20KB) = 22KB.
Everything the globe, accordion, and kernel enforcement need.
WASM + ad_seed.db (13MB) only loaded when user drills into actual records.

---

## §5. The Honest Risk Matrix

| Risk | Severity | Likelihood | Mitigation |
|---|---|---|---|
| Silent data loss (two-tab overwrite) | HIGH | LOW (single user) | BroadcastChannel toast |
| Orphaned document references | HIGH | MEDIUM | Downstream check (50 lines) |
| Misleading constellation gravity | LOW | MEDIUM | Distinct record count weighting |
| Missing validation (AD fields not loaded) | MEDIUM | LOW | Manifest has mandatory flags |
| Undo past safety boundary | LOW | LOW | compact() already limits to 2 sessions |
| Currency mismatch | HIGH | LOW (single currency default) | Skip until multi-currency needed |
| Sequence collision | NONE | NONE | Single-user, MAX+1 is atomic |
| Audit trail gaps | NONE | NONE | kernel_ops logs everything already |

**Overall assessment:** The monsters are real but bounded. Each has a
known solution under 100 lines of code. The total enforcement layer
(~150 lines) replaces ~15,000 lines of iDempiere framework code.

The risk is not technical — it's **conceptual**. Users coming from
iDempiere expect the rigidity. The system that says "No, you can't do
that" feels safe. The system that says "You can, but here's what it
means" feels dangerous. The UX challenge is making freedom feel safe.

---

## §6. Why 5 Tables Changes Everything

ERP OOTB uses 5 core tables (from SpatialERP_POC.md §3.1) instead of
iDempiere's 1003. This collapses the monster surface area dramatically:

| Table | What | Replaces |
|---|---|---|
| `containers` | Spatial hierarchy (Site→Building→Floor) | C_Project, C_ProjectPhase, M_Warehouse |
| `items` | Things in containers | M_Product, C_BPartner, M_BOM |
| `documents` | ALL doc types in one table | C_Order, C_Invoice, C_Payment, M_InOut |
| `document_lines` | Lines for any document | C_OrderLine, C_InvoiceLine, M_InOutLine |
| `journal` | Auto-posted accounting entries | GL_Journal, Fact_Acct |
| + `kernel_ops` | Event log, undo/redo, audit | AD_ChangeLog (no undo equivalent) |

**Impact on monsters:**

Monster 1 (two tabs): Only 5 tables to sync via BroadcastChannel.
Monster 2 (orphans): DOWNSTREAM map = 5 entries (not 40).
Monster 3 (gravity): One query, 5 rows back, 5 auras updated.

**Impact on invariants:**

All 10 invariants simplify because `documents` is generic:
- One state machine for ALL doc types (doc_status column)
- One mandatory check per doc_type, not per table
- 5 FK relationships total, not 400
- One sequence counter per doc_type prefix
- Manifest = ~2KB (5 table definitions), not 20KB

**Impact on constellation:**

The 5 tables ARE the 5 top-level bubbles (plus items and containers
which are structural). The `doc_type` field within `documents` becomes
the sub-tier: tap Documents → see Lead(3), BOQ(2), PO(5), Invoice(4)
as orbit nodes. Same drill mechanic, driven by `doc_type` grouping
instead of separate tables.

**The key insight:** iDempiere has 1003 tables because each module adds
its own. The 5-table design says: a document is a document. PO, Invoice,
Lead — they're all rows in `documents` with different `doc_type`.
Structure is identical. Business rules differ, and those live in the
kernel, not the schema.

---

## §7. Decision: When to Build What

| Phase | What | Why now |
|---|---|---|
| **Now** | Curate initbubble.json to 7 business nodes | First impression |
| **Now** | Uniform bubble size + aura gravity | Mobile readability |
| **S282** | Settings JSON for bubble/pill ordering | Shared with BIM |
| **S283** | compile_manifest.js — AD→20KB JSON | Eliminate 13MB runtime |
| **S283** | kernel_ops invariant enforcement | 10 rules, ~150 lines |
| **Later** | Multi-device sync | Only if users need phone+laptop |
| **Later** | Multi-currency | Only if users operate across currencies |
| **Never** | Full AD runtime loading | Compiled manifest replaces it |
