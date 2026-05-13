/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */

# ⚠ DO NOT REMOVE — Scope guard
# Scope: Spatial ERP OOTB — doc_engine.js + category_registry + Construction ERP POC.
#        Browser-only. No server. No iDempiere dependency.
#        Replaces prompts/iDempiereOOTB.md — same iDempiere AD thinking,
#        zero iDempiere infrastructure.
#        Requirement source: ~/Downloads/Idempiere Construction ERP.pptx (Sysnova / Kazi Farms Group)
# Read the log after every run. Exit code is not evidence.
# Spec-first: implement only what is described in a § section below.

---

# Spatial ERP OOTB — Construction ERP POC

## Goal

Prove that a Construction ERP — land lead management, FAR planning, BOQ,
project financial control — runs entirely in the browser using SQLite WASM
+ Three.js, with zero server dependency.

The POC domain is **Construction** (land acquisition → development planning →
BOQ → project execution) because:
1. The architect provides IFC — **the 3D view already exists** in BIM OOTB
2. The BOQ engine already exists (boq_charts.html, rates.js)
3. The land plot is the spatial container, the building is the BOM
4. FAR (Floor Area Ratio) is inherently spatial — building volume / plot area
5. The workflow (Lead → Screen → FAR → BOQ → Approve) maps to doc_status

**Requirement source:** Sysnova (Part of Kazi Farms Group) — "Construction ERP
on iDempiere" presentation. Real stakeholders, real workflow, real data fields.

**POC success = one device, all roles: Land team creates lead with plot location
→ Architect sees FAR view with IFC building on plot → Engineering sees BOQ
auto-computed from IFC → Management approves/rejects → role band switches
between all 6 stakeholder perspectives.**

**Full spec:** `docs/SpatialERP_OOTB.md`

---

## §0. What This Replaces

`prompts/iDempiereOOTB.md` planned an OSGi plugin embedding BIM OOTB inside
iDempiere (Iframe in ZK, postMessage bridge, C_Project tab, Java handlers).
That approach required iDempiere server (JVM + PostgreSQL + OSGi bundle).

This prompt achieves the **same ERP semantics** with:
- SQLite WASM (sql.js) in browser
- kernel_ops (already built — commit, undo, redo, replay)
- Three.js + BIM OOTB viewer (already built — IFC rendering, BOQ, 5D)
- Same viewer (deploy/dev/index.html), extended with doc_engine.js

**Build order: core engine FIRST, then UI, then domain data.**

---

## §0b. Core Engine Architecture — Build This First

```
┌─────────────────────────────────────────────────────────────────┐
│                     BROWSER (same for all roles)                │
├─────────────────┬──────────────────────────┬────────────────────┤
│   Adapters      │     Handlers             │   Core             │
│   (UI skin)     │  (domain logic plugins)  │  (immutable)       │
├─────────────────┼──────────────────────────┼────────────────────┤
│ ThreeScene ★    │ LeadCreate  (Land)       │ kernel_ops ★       │
│ SwipeCardStack  │ FARCalc     (Architect)  │ StateMachine       │
│ RoleBand        │ BOQGenerate (Eng/BOQ)    │ JournalEngine      │
│ RoleFilter      │ Approve     (Mgmt)       │ SpatialIndex ★     │
│ QRGateway ★     │ SalesView   (Sales)      │ CategoryRegistry   │
│                 │ LegalClose  (Legal)      │                    │
│  ★ = already    │                          │  ★ = already       │
│    exists       │                          │    exists          │
└─────────────────┴──────────────────────────┴────────────────────┘
                              │
                              ▼
                    SQLite WASM (.db file — 9 tables)
```

### Build priority:

| Priority | Component | File | Depends on |
|---|---|---|---|
| **P0** | Table creation (6 new tables) | `doc_engine.js` | Nothing |
| **P0** | StateMachine (pure function) | `doc_engine.js` | Tables |
| **P0** | JournalEngine (auto-post) | `doc_engine.js` | StateMachine |
| **P0** | kernel_ops user_tag | `kernel_ops.js` | Nothing |
| **P1** | CategoryRegistry reader | `category_loader.js` | Tables |
| **P1** | Construction seed data | `construction.db` | Tables + Registry |
| **P2** | Construction handlers | `handlers/construction.js` | Core + seed data |
| **P3** | SwipeCardStack | `swipe.js` | Registry + handlers |
| **P3** | RoleBand | `role_band.js` | project_metadata |
| **P4** | Integration test | test file | All above |

**P0 must work headless** (no UI) with unit tests proving state transitions
and journal posting before any card is rendered.

---

## §1. Requirement — Sysnova Construction ERP

### §1.1 The business flow (from pptx)

```
Land Lead Management lifecycle:

Lead Creation → Screening → FAR Planning → Sales Visibility
    → BOQ → Negotiation → Approval / Rejection → Closure
```

### §1.2 Stakeholders and roles

| Role | Responsibility | OOTB mode | OOTB scope |
|---|---|---|---|
| **LAND** (Land/Business Dev) | Lead creation, negotiation, full access | `full` | All leads |
| **ARCH** (Architect/Planning) | FAR & development comments, FAR only | `operator` | FAR fields + IFC |
| **ENGR** (Engineering/BOQ) | Cost estimation only | `operator` | BOQ tab only |
| **SALE** (Sales) | Planning review, read-only, NO owner info | `readonly` | No landowner data |
| **MGMT** (Management) | Approval authority, full access | `full` | All leads |
| **LEGL** (Legal) | Post-approval processing | `operator` | Approved leads only |

### §1.3 Functional requirements (from pptx)

**Lead Creation:**
- Lead Code (auto-generated)
- Land Type (Freehold / Leasehold)
- Land Size (Katha)
- Lead Source
- Location details: Plot, Road, Block, Sector, Area
- Facing (East/West/North/South)
- Road Width

**Landowner Information (Confidential — hidden from Sales):**
- Owner Name, Contact Person, Mobile, Email, Address
- Rule: Sales users shall NOT have visibility

**Activity Management:**
- Meeting date, Follow-up date, Activity type, Notes, Attachments

**FAR Calculation & Development Planning:**
- FAR value
- Developable area, Saleable area
- Number of storeys
- Units per floor, Total units
- Parking, Basement

**Development Sales:**
- Sales Price per unit
- Comments

**BOQ:**
- Cost per SF
- Comments
- Links to IFC-derived BOQ (existing boq_charts.html)

**Financial Management:**
- Project cost report, WIP statement
- Budget vs actual, Revenue recognition
- Progress billing (RA Bills), Retention

**Project Phases:** Foundation, Civil Structure, Electrical, Plumbing, Finishing

---

## §2. iDempiere Mapping — AD in SQLite

Every iDempiere concept from the Sysnova pptx maps to browser-side:

| iDempiere (pptx shows) | Browser equivalent |
|---|---|
| Lead Info window (custom table) | `documents` with `doc_type='LAND_LEAD'` |
| Lead Activity tab | `kernel_ops` with `op_type='LEAD_ACTIVITY'` |
| Development Plan window | `documents` with `doc_type='DEV_PLAN'`, linked to lead |
| Development Sales Price tab | `document_lines` with price + comments |
| Development BOQ window | `document_lines` with cost_per_sf + IFC BOQ link |
| C_Project (ABC Construction) | `containers` root — the project |
| Project Phases (Foundation, Civil...) | `containers` children — phases as sub-containers |
| AD_Role (Land Team, Sales, etc.) | `?mode=` + role band + `metadata` field hiding |
| DocStatus workflow | `doc_engine.js` StateMachine |
| Financial reports | `journal` queries (SUM debit, credit GROUP BY account) |

---

## §3. Schema — The Nine Tables

Same five data tables + registry from SpatialERP_OOTB.md §4.
Construction-specific data lives in `metadata` JSON, not new columns.

### §3.1 New tables (created by doc_engine.js on first load)

```sql
-- §TAB_CONTAINERS — spatial hierarchy: Site → Building → Phase → Floor
CREATE TABLE IF NOT EXISTS containers (
    id          TEXT PRIMARY KEY,
    parent_id   TEXT REFERENCES containers(id),
    name        TEXT NOT NULL,
    category    TEXT NOT NULL,       -- SITE, BUILDING, PHASE, FLOOR, PLOT
    geometry_id TEXT,                -- FK to component_geometries (IFC model)
    x REAL DEFAULT 0, y REAL DEFAULT 0, z REAL DEFAULT 0,
    metadata    TEXT DEFAULT '{}'    -- JSON: land_size, facing, far_value, etc.
);

-- §TAB_ITEMS — things in containers: elements, fixtures, materials
CREATE TABLE IF NOT EXISTS items (
    id           TEXT PRIMARY KEY,
    container_id TEXT REFERENCES containers(id),
    product_ref  TEXT,
    name         TEXT,
    qty          REAL DEFAULT 1,
    geometry_id  TEXT,
    x REAL DEFAULT 0, y REAL DEFAULT 0, z REAL DEFAULT 0,
    metadata     TEXT DEFAULT '{}'
);

-- §TAB_DOCUMENTS — leads, dev plans, BOQs, POs, invoices
CREATE TABLE IF NOT EXISTS documents (
    id          TEXT PRIMARY KEY,
    doc_type    TEXT NOT NULL,       -- LAND_LEAD, DEV_PLAN, DEV_BOQ, PURCHASE_ORDER...
    doc_status  TEXT DEFAULT 'DRAFT',
    created     TEXT NOT NULL,
    completed   TEXT,
    description TEXT,
    metadata    TEXT DEFAULT '{}'    -- JSON: all lead fields, landowner info, etc.
);

-- §TAB_DOC_LINES — BOQ lines, sales prices, phase costs
CREATE TABLE IF NOT EXISTS document_lines (
    id           TEXT PRIMARY KEY,
    doc_id       TEXT REFERENCES documents(id),
    item_id      TEXT,
    container_id TEXT,
    qty          REAL,
    unit_price   REAL,
    metadata     TEXT DEFAULT '{}'
);

-- §TAB_JOURNAL — auto-generated on document completion
CREATE TABLE IF NOT EXISTS journal (
    id        TEXT PRIMARY KEY,
    doc_id    TEXT REFERENCES documents(id),
    line_id   TEXT,
    account   TEXT NOT NULL,         -- LAND_COST, CONSTRUCTION_WIP, REVENUE, RETENTION
    debit     REAL DEFAULT 0,
    credit    REAL DEFAULT 0,
    timestamp TEXT NOT NULL
);

-- §TAB_REGISTRY — AD for construction categories
CREATE TABLE IF NOT EXISTS category_registry (
    category        TEXT PRIMARY KEY,
    domain          TEXT,
    json_schema     TEXT,
    default_geometry TEXT,
    actions         TEXT,             -- JSON: allowed actions per category
    heatmap_rule    TEXT,
    label_template  TEXT
);
```

### §3.2 Existing tables (unchanged)

- `kernel_ops` — commit, undo, redo, replay (add `user_tag` column)
- `component_geometries` — IFC 3D shapes
- `project_metadata` — domain config, roles, colours

---

## §4. Construction Seed Data — construction.db

### §4.1 Containers — site/plot/building/phases

```sql
-- §SEED_CONTAINERS
-- The site container — top level
INSERT INTO containers VALUES ('site_gulshan', NULL, 'Gulshan-1 Site', 'SITE', NULL,
    0,0,0, '{"area":"Gulshan-1","city":"Dhaka"}');

-- The land plot — spatial object on the site
INSERT INTO containers VALUES ('plot_60', 'site_gulshan', 'Plot 60, Road 2, Block 4', 'PLOT', NULL,
    60,2,0, '{"plot_no":60,"road_no":2,"block_no":4,"sector":3,"road_width":20,"facing":"East"}');

-- The building — this is where the IFC goes
-- geometry_id links to the architect's IFC model once imported
INSERT INTO containers VALUES ('bldg_test', 'plot_60', 'Proposed Development', 'BUILDING', NULL,
    0,0,0, '{"storeys":40,"far_value":10000,"dev_area":20,"saleable_area":100,"basement_area":2,"total_units":50,"units_per_floor":4,"parking":30}');

-- Project phases (from Sysnova pptx: Foundation, Civil, Electrical, Plumbing, Finishing)
INSERT INTO containers VALUES ('phase_found',  'bldg_test', 'Foundation',      'PHASE', NULL, 0,0,0, '{"sequence":10,"std_phase":"Foundation_Construction"}');
INSERT INTO containers VALUES ('phase_civil',  'bldg_test', 'Civil Structure', 'PHASE', NULL, 0,0,0, '{"sequence":20,"std_phase":"Civil_Structure_Construction"}');
INSERT INTO containers VALUES ('phase_elec',   'bldg_test', 'Electrical',      'PHASE', NULL, 0,0,0, '{"sequence":30,"std_phase":"Electrical_Construction"}');
INSERT INTO containers VALUES ('phase_plumb',  'bldg_test', 'Plumbing',        'PHASE', NULL, 0,0,0, '{"sequence":40,"std_phase":"Plumbing_Construction"}');
INSERT INTO containers VALUES ('phase_finish', 'bldg_test', 'Finishing',       'PHASE', NULL, 0,0,0, '{"sequence":50,"std_phase":"Finishing_Construction"}');
```

### §4.2 Documents — the land lead (main document)

```sql
-- §SEED_LEAD — matches pptx Lead Info screen exactly
INSERT INTO documents VALUES ('LEAD-1000000', 'LAND_LEAD', 'DRAFT', '2026-05-13', NULL,
    'Test lead — Gulshan-1 Plot 60',
    '{
        "lead_code": "1000000",
        "land_type": "Freehold",
        "land_size_katha": 10.0,
        "lead_source": "Others",
        "plot_no": 60, "road_no": 2, "block_no": 4, "sector": 3,
        "area": "Gulshan-1", "facing": "East", "road_width": 20,
        "owner_name": "CONFIDENTIAL — Mr. Rahman",
        "contact_person": "test contact",
        "phone": "0986533223",
        "email": "",
        "address": "",
        "user_contact": "Azmir",
        "container_ref": "plot_60"
    }');

-- §SEED_DEV_PLAN — linked to the lead
INSERT INTO documents VALUES ('DEV-1000000', 'DEV_PLAN', 'DRAFT', '2026-05-13', NULL,
    'Development Plan for LEAD-1000000',
    '{
        "lead_ref": "LEAD-1000000",
        "far_value": 10000.0,
        "total_dev_area": 20.0,
        "total_saleable_area": 100.0,
        "num_storeys": 40,
        "total_units": 50,
        "units_per_floor": 4,
        "total_parking": 30,
        "total_basement_area": 2.0,
        "container_ref": "bldg_test"
    }');
```

### §4.3 Document lines — sales price + BOQ

```sql
-- §SEED_SALES_PRICE
INSERT INTO document_lines VALUES ('SP-001', 'DEV-1000000', NULL, 'bldg_test',
    1, 2000.00, '{"type":"sales_price","comment":"testttt"}');

-- §SEED_BOQ
INSERT INTO document_lines VALUES ('BOQ-001', 'DEV-1000000', NULL, 'bldg_test',
    1, 2300.00, '{"type":"cost_per_sf","comment":"eofuweofowef"}');
```

### §4.4 Category Registry (AD)

```sql
-- §SEED_REGISTRY
INSERT INTO category_registry VALUES ('SITE',     'CONSTRUCTION', NULL, NULL,
    '["CreateLead","ViewLeads","ViewPnL"]',
    '{"field":"active_leads","red_above":10}',
    '{name}');

INSERT INTO category_registry VALUES ('PLOT',     'CONSTRUCTION', NULL, 'plot_3d',
    '["CreateLead","EditLead","ViewFAR","LinkIFC"]',
    '{"field":"lead_status","red_value":"REJECTED"}',
    'Plot {metadata.plot_no} — {metadata.area}');

INSERT INTO category_registry VALUES ('BUILDING', 'CONSTRUCTION', NULL, NULL,
    '["ViewIFC","ComputeBOQ","EditFAR","ApprovePlan"]',
    '{"field":"doc_status","amber_value":"DRAFT","red_value":"REJECTED"}',
    '{name} ({metadata.storeys}F)');

INSERT INTO category_registry VALUES ('PHASE',    'CONSTRUCTION', NULL, NULL,
    '["ViewProgress","AddCost","CompleteMilestone"]',
    '{"field":"pct_complete","red_below":20,"green_above":80}',
    '{name} — Seq {metadata.sequence}');
```

### §4.5 Project Metadata — roles from Sysnova pptx

```sql
-- §SEED_META — maps exactly to pptx Role Based Access Control slide
INSERT INTO project_metadata VALUES ('domain', 'CONSTRUCTION');
INSERT INTO project_metadata VALUES ('roles',
    '["LAND","ARCH","ENGR","SALE","MGMT","LEGL"]');
INSERT INTO project_metadata VALUES ('role_labels',
    '{"LAND":"Land Team","ARCH":"Architect","ENGR":"BOQ Team","SALE":"Sales","MGMT":"Management","LEGL":"Legal"}');
INSERT INTO project_metadata VALUES ('role_colours',
    '{"LAND":"#1565c0","ARCH":"#7b1fa2","ENGR":"#e65100","SALE":"#2e7d32","MGMT":"#b71c1c","LEGL":"#00838f"}');
INSERT INTO project_metadata VALUES ('role_modes',
    '{"LAND":"full","ARCH":"operator","ENGR":"operator","SALE":"readonly","MGMT":"full","LEGL":"operator"}');
INSERT INTO project_metadata VALUES ('role_scopes',
    '{"LAND":"*","ARCH":"far","ENGR":"boq","SALE":"no_owner","MGMT":"*","LEGL":"approved_only"}');
INSERT INTO project_metadata VALUES ('accounts',
    '["LAND_ACQUISITION","CONSTRUCTION_WIP","REVENUE","RETENTION","PROFESSIONAL_FEES"]');
INSERT INTO project_metadata VALUES ('confidential_fields',
    '["owner_name","contact_person","phone","email","address"]');
```

---

## §5. State Machine — Lead Lifecycle

The Sysnova workflow maps to an **extended** state machine. The base 5-state
machine handles all doc_types. The LAND_LEAD has domain-specific status labels:

```
Lead Created (DRAFT)
    ↓ screen
Screening (IN_PROGRESS)
    ↓ plan_far
FAR Planning (IN_PROGRESS — sub-status: FAR)
    ↓ submit_approval
Management Approval (IN_PROGRESS — sub-status: APPROVAL)
    ↓ approve / reject
        → approve → BOQ (IN_PROGRESS — sub-status: BOQ)
                        ↓ negotiate
                     Negotiation (IN_PROGRESS — sub-status: NEGOTIATION)
                        ↓ close
                     Approved / Closure (COMPLETED)
        → reject → Rejected (VOIDED)
```

Implementation: the base StateMachine handles DRAFT → IN_PROGRESS → COMPLETED / VOIDED.
The sub-statuses (FAR, APPROVAL, BOQ, NEGOTIATION) are stored in
`documents.metadata.sub_status` — the state machine doesn't know about them.
Handlers advance the sub_status via commitOp.

```javascript
// §STATE_LEAD_TRANSITIONS
// The base machine sees: DRAFT → IN_PROGRESS → COMPLETED or VOIDED
// The handlers manage sub_status progression within IN_PROGRESS:
//
// handler: screenLead(db, leadId)
//   → sets metadata.sub_status = 'SCREENING'
//   → transition(db, leadId, 'start')  // DRAFT → IN_PROGRESS
//   → commitOp(db, 'LEAD_SCREEN', {lead_id, screened_by})
//
// handler: planFAR(db, leadId, farData)
//   → sets metadata.sub_status = 'FAR'
//   → creates DEV_PLAN document linked to lead
//   → commitOp(db, 'FAR_PLAN', {lead_id, far_value, dev_area, ...})
//
// handler: submitApproval(db, leadId)
//   → sets metadata.sub_status = 'APPROVAL'
//   → commitOp(db, 'SUBMIT_APPROVAL', {lead_id, submitted_by})
//
// handler: approve(db, leadId)
//   → sets metadata.sub_status = 'BOQ'
//   → commitOp(db, 'LEAD_APPROVE', {lead_id, approved_by})
//
// handler: reject(db, leadId, reason)
//   → transition(db, leadId, 'void')  // → VOIDED
//   → commitOp(db, 'LEAD_REJECT', {lead_id, reason})
//
// handler: generateBOQ(db, leadId)
//   → reads IFC-derived element data from extracted .db
//   → creates document_lines with costs from rates.js
//   → sets metadata.sub_status = 'NEGOTIATION'
//   → commitOp(db, 'BOQ_GENERATE', {lead_id, total_cost, line_count})
//
// handler: closeLead(db, leadId)
//   → transition(db, leadId, 'complete')  // → COMPLETED
//   → JournalEngine posts: debit LAND_ACQUISITION, credit CASH
//   → commitOp(db, 'LEAD_CLOSE', {lead_id, final_price})
```

---

## §6. Role-Based Field Visibility

The Sysnova pptx specifies: **"Sales users shall NOT have visibility"** on
landowner information. This is handled by `?mode=` and `confidential_fields`:

```javascript
// §FIELD_FILTER
// When rendering a document card:
// 1. Read current role from role_band
// 2. Read role_scopes from project_metadata
// 3. Read confidential_fields from project_metadata
// 4. If role scope says "no_owner":
//    → strip confidential_fields from displayed metadata
//    → owner_name, phone, email, address → hidden
//
// No server enforcement — this is UI filtering (same as ?mode= pattern).
// The .db contains all data. Filtered views are convenience, not security.
// For true security: generate per-role .db extracts (same as customer QR pattern).
```

| Role | Sees lead fields | Sees owner info | Sees FAR | Sees BOQ | Can approve |
|---|---|---|---|---|---|
| LAND | All | Yes | Yes | Yes | No |
| ARCH | Location only | No | **Yes (edit)** | No | No |
| ENGR | Location + FAR | No | Yes (read) | **Yes (edit)** | No |
| SALE | Location + FAR + Sales Price | **No** | Yes (read) | No | No |
| MGMT | All | Yes | Yes | Yes | **Yes** |
| LEGL | Approved leads only | Yes | Yes | Yes | No |

---

## §7. The Spatial Correlation — IFC Meets ERP

This is where BIM OOTB + Spatial ERP fuse. The architect provides an IFC file.
The existing viewer renders it. The ERP layer wraps documents around it.

### §7.1 The connection

```
Architect provides: residential_project.ifc
    ↓
BIM OOTB viewer: imports → extracts → renders 3D scene
    ↓
Spatial ERP: the 3D building IS the container 'bldg_test'
    ↓
BOQ handler: reads elements_meta from extracted .db
    → auto-populates document_lines with quantities + rates
    ↓
FAR calculation: reads bbox from element_transforms
    → computes total floor area / plot area
    ↓
Phase mapping: IFC disciplines (ARC, STR, MEP) → project phases
    → Foundation = STR ground floor elements
    → Electrical = ELEC discipline elements
    → Plumbing = PLB discipline elements
    ↓
Heatmap: phases colour-coded by completion %
    → Foundation 100% (green), Civil 60% (amber), Finishing 0% (red)
```

### §7.2 BOQ from IFC (already exists)

The viewer's `boq_charts.html` already computes:
```sql
SELECT m.discipline, m.ifc_class, m.storey, COUNT(*) as qty
FROM elements_meta m
GROUP BY m.discipline, m.ifc_class, m.storey
```

With rates from `rates.js` (CIDB Malaysia 2024, or custom template).

The BOQ handler just copies this into `document_lines`:

```javascript
// §HANDLER_BOQ_GENERATE
// Reads the existing QTO computation from the loaded .db
// Creates document_lines under the DEV_PLAN document:
//   line per (discipline, ifc_class, storey) with qty, rate, total
// Links to the building container
// This is the SAME data boq_charts.html shows — just persisted as ERP lines
```

### §7.3 FAR from geometry (new computation)

```javascript
// §HANDLER_FAR_CALC
// FAR = Total Floor Area / Plot Area
// Total Floor Area: SUM of slab areas per storey (from element_transforms bbox)
// Plot Area: from container metadata (land_size_katha × 720 sq ft per katha)
//
// Or: read from architect's input in DEV_PLAN metadata
// The IFC provides the truth — architect's estimate can be validated against it
```

---

## §7b. Two HTML Files, Two Concerns

The ERP layer is **document-centric, not spatial**. No redundant 3D scene.
The spatial experience stays in BIM OOTB. Two HTML files, clean separation:

### erp.html — Universal Document Engine (NEW)

Standalone HTML. **No Three.js.** Pure document handling with TikTok swipe UX.
This is the common foundation for ALL future ERP document handling — construction,
F&B, WMS, back office. Any domain where you swipe through documents.

```
┌──────────────────────────────┐
│  [ENGR] BOQ Team   [QR] [<>]│  Role band
├──────────────────────────────┤
│                              │
│  ┌────────────────────────┐  │
│  │  Lead: LEAD-1000000    │  │  Swipe card — document-centric
│  │  Gulshan-1, Plot 60    │  │  No 3D thumbnail. Just data.
│  │  10 Katha, Freehold    │  │  Clean, fast, phone-first.
│  │                        │  │
│  │  Status: ⬤ SCREENING   │  │  Colour dot = status
│  │  FAR: 10,000           │  │
│  │  BOQ: RM 2,300/SF      │  │
│  │                        │  │
│  │  [ Approve ] [ Reject ]│  │  Action buttons per role
│  └────────────────────────┘  │
│                              │
│  ← swipe →  (next document)  │
│  ↑ swipe ↑  (drill into FAR) │
│  ↓ swipe ↓  (back to list)   │
│                              │
│  [ Open in BIM ] ← link out  │  Opens index.html with same .db
└──────────────────────────────┘
```

**What erp.html contains:**
- `sql.js` (loads the same `.db` from OCI or IndexedDB)
- `doc_engine.js` (StateMachine + JournalEngine)
- `category_loader.js` (registry reader)
- `swipe.js` (card stack + gestures)
- `role_band.js` (role switcher + QR share)
- `handlers/construction.js` (or fnb.js, wms.js — domain handlers)
- **No scene.js. No Three.js. No WebGL.**

**What this means:** erp.html works on ANY device, even a $50 phone with no
GPU. It's a card swipe app that happens to read a SQLite database. The 3D
experience is optional — open BIM OOTB when you need spatial context.

### index.html — BIM OOTB Spatial Scene (EXISTING)

The existing viewer. IFC geometry, 3D scene, measure tool, clash detection.
When the user needs to connect a document to a spatial element, the
**Measure tool pattern** extends to become an ERP overlay panel.

### The Measure Tool Pattern — ERP in BIM

`measure.js` already demonstrates the pattern:
- User activates Measure tool → panel opens over the 3D scene
- User clicks element → panel shows measurement data
- Panel overlays the scene, doesn't replace it

**"Measure for ERP" follows the same pattern:**

```
┌──────────────────────────────────────────┐
│   BIM OOTB 3D Scene                      │
│                                          │
│   IFC building rendered                  │
│                                    ┌─────┤
│                                    │ ERP │
│   User clicks an IfcWall ─────────►│     │
│                                    │Tag  │
│                                    │Lead │
│                                    │Snag │
│                                    │BOQ  │
│                                    │Phase│
│                                    │     │
│                                    └─────┤
│   [Measure] [X-Ray] [Clash] [ERP]       │  ← new toolbar button
└──────────────────────────────────────────┘
```

The [ERP] toolbar button opens a side panel (same as Measure panel).
Click any element → panel shows:
- Which BOQ line this element belongs to
- Which phase (Foundation, Civil, Electrical...)
- Cost for this element type
- Linked documents (lead, snag, PO)
- Action: [Tag] [Link to Lead] [Add Snag] [Mark Complete]

Each action is a `commitOp` — logged in kernel_ops, visible in erp.html.

### The bridge is the .db

```
erp.html                         index.html
(document swipe)                 (3D spatial scene)
     │                                │
     │     SAME .db on OCI            │
     │  ◄─────────────────────────►   │
     │     Same containers            │
     │     Same documents             │
     │     Same kernel_ops            │
     │                                │
     │  Lead created in erp.html      │
     │  → .db updated                 │
     │  → index.html loads same .db   │
     │    → plot coloured by status   │
     │                                │
     │  Element tagged in index.html  │
     │  → kernel_op committed         │
     │  → erp.html loads same .db     │
     │    → BOQ line shows tag        │
```

No postMessage. No WebSocket. No API. **The `.db` file on OCI is the
integration layer.** Both HTML files read the same file. Changes by one
are visible to the other on next load (or if sharing IndexedDB, instantly).

### Desktop God Mode

Two browser tabs side by side:
- Left: `index.html?db=construction.db` (3D scene, IFC, measure)
- Right: `erp.html?db=construction.db` (document swipe, role band, approval)

Both read the same `.db`. The manager sees the building AND the documents.

### Mobile

One app at a time:
- On-site foreman: opens `erp.html` on phone → swipes through phases, marks completion
- Architect at desk: opens `index.html` on laptop → reviews IFC, uses [ERP] panel to tag elements
- Management in meeting: opens `erp.html` on tablet → swipes leads, approves/rejects

The `[Open in BIM]` button on erp.html cards links out to `index.html` with
the same `.db` and `?scope=` — for when you need spatial context.

### Same repo, lazy-loaded

All ERP files live in the BIM OOTB repo (`deploy/dev/`). Nothing is separate.
Users see the full product — BIM + ERP — in one place.

**Lazy-load pattern (same as measure.js):**
- `measure.js` loads only when user clicks [Measure]
- `erp_panel.js` loads only when user clicks [ERP]
- `erp.html` is a standalone entry point that loads the same modules

```
deploy/dev/
  index.html          ← BIM viewer. [ERP] button lazy-loads erp_panel.js
  erp.html            ← Standalone document swipe. Loads same modules.
  doc_engine.js       ← Core: StateMachine + JournalEngine
  category_loader.js  ← Registry reader
  erp_panel.js        ← The panel UI (swipe cards + role band)
                         Loaded by BOTH index.html and erp.html
  swipe.js            ← Card stack + gestures (used by erp_panel.js)
  role_band.js        ← Role switcher + QR (used by erp_panel.js)
  handlers/
    construction.js   ← Lead lifecycle + BOQ + FAR handlers
    fnb.js            ← (future) restaurant handlers
    wms.js            ← (future) warehouse handlers
  measure.js          ← Already exists. Same lazy-load pattern.
  boq_charts.html     ← Already exists. Same standalone pattern.
```

**Two entry points, same modules:**

| Entry | When | What loads |
|---|---|---|
| `erp.html` | Mobile user, document-only, no 3D needed | sql.js + doc_engine + erp_panel + swipe + role_band + handler |
| `index.html` → [ERP] button | Desktop user, wants 3D + documents | Same modules, lazy-loaded into panel overlay |

**Why this works:** The user sees one product with everything in it.
The foreman opens `erp.html` on their phone. The architect opens `index.html`
and clicks [ERP] when they need document context. Same data, same modules,
same role band — two ways in.

### Why this separation wins

| Concern | erp.html (standalone) | index.html + [ERP] panel |
|---|---|---|
| Primary UX | Document swipe (TikTok) | 3D spatial + document overlay |
| Three.js | **None** — pure DOM + CSS | Full WebGL scene |
| Device requirement | Any phone, any browser | GPU-capable device |
| Domain-agnostic | **Yes** — same cards for any domain | Construction/BIM spatial |
| Foundation for | All future ERP document handling | Spatial tagging, element linking |
| File size | Tiny (~50KB + sql.js WASM) | Large (Three.js + IFC geometry) |
| Same modules? | **Yes** — doc_engine, swipe, role_band | **Yes** — lazy-loaded on [ERP] tap |

---

## §8. POC Demo Flow — All Roles, One Device

```
1. Open construction.db in viewer
   Role band: [LAND] (blue) — "Land Team"
   3D view: site plan. Plot 60 shown as a rectangle on the map.
   Plot is grey (no lead yet... wait, seed data has DRAFT lead).
   Plot card shows: "Plot 60 — 10 Katha — Freehold — Gulshan-1"
       ↓
2. Tap Plot 60 → Lead card shows all fields (owner info visible to LAND).
   Tap [Screen]. Lead transitions DRAFT → IN_PROGRESS (Screening).
   commitOp: LEAD_SCREEN. Plot goes amber.
       ↓
3. Tap [<>] → switch to ARCH (purple) — "Architect"
   Scope filters to FAR fields only.
   Tap Plot 60 → Card shows land info + FAR section.
   Owner info HIDDEN (Architect = no owner visibility).
   Tap [LinkIFC] → drop architect's IFC file → building renders on plot.
   Tap [EditFAR] → FAR value auto-computed from IFC geometry.
   commitOp: FAR_PLAN. Sub-status → FAR.
       ↓
4. Tap [<>] → switch to ENGR (orange) — "BOQ Team"
   Scope filters to BOQ tab only.
   Tap building → Card shows IFC element summary.
   Tap [ComputeBOQ] → handler reads elements_meta + rates.js
   → document_lines auto-populated: IfcWall RM 285/M2 × 245 = RM 69,825...
   commitOp: BOQ_GENERATE. Sub-status → NEGOTIATION.
   Card shows total cost: RM 2,300/SF × total SF.
       ↓
5. Tap [<>] → switch to SALE (green) — "Sales"
   Owner info HIDDEN. FAR + BOQ visible (read-only).
   Sales sees: Plot location, FAR, total cost, sales price per unit.
   Cannot edit. Cannot approve. Can review.
       ↓
6. Tap [<>] → switch to MGMT (red) — "Management"
   Full access. ALL fields visible including owner.
   Reviews: lead info, FAR, BOQ, sales price.
   Tap [Approve] or [Reject].
   If approve → commitOp: LEAD_APPROVE. Sub-status → BOQ.
   If reject → transition to VOIDED. Lead card goes red.
       ↓
7. Tap [<>] → switch to LEGL (teal) — "Legal"
   Scope: approved leads only. Sees the approved lead.
   Processes post-approval paperwork.
   Tap [Close] → transition to COMPLETED.
   Journal auto-posts: debit LAND_ACQUISITION, credit CASH.
       ↓
8. Tap [<>] → back to LAND (blue)
   Plot 60 goes green (lead completed).
   Building rendered on plot with phases colour-coded.
   Project financial view: journal entries visible.
   kernel_ops: full audit trail of every step by every role.
       ↓
9. Tap [QR] → generates link for ARCH role.
   Hand to architect friend. They see FAR view + IFC.
   Same viewer. Same .db. Different perspective.
       ↓
   POC COMPLETE.
```

---

## §9. Implementation Order

| Step | Section | Deliverable | Verify |
|---|---|---|---|
| 1 | §3.1 | `doc_engine.js` — CREATE TABLE + StateMachine + JournalEngine | Unit test: transitions + journal |
| 2 | §4 | `construction.db` — seed data (containers, documents, registry) | Load in viewer → site renders |
| 3 | §5 | Lead lifecycle handlers (screen, planFAR, approve, reject, close) | §-log: full lead cycle works |
| 4 | §7.2 | BOQ handler — reads IFC elements, creates document_lines | §-log: BOQ lines match boq_charts |
| 5 | §7.3 | FAR handler — computes from IFC geometry or manual input | §-log: FAR value computed |
| 6 | §6 | Role-based field filtering (confidential_fields hidden for SALE) | §-log: owner info hidden per role |
| 7 | §8 | SwipeCardStack + RoleBand — 6 roles cycling | §-log: role switch changes scope |
| 8 | §8 | End-to-end demo flow | All 9 steps complete on one device |
| 9 | — | Upload construction.db to OCI dev bucket | Accessible via viewer URL |

---

## §10. Files — What Gets Created / Modified

### New files

| File | Lines (est.) | What |
|---|---|---|
| `deploy/dev/doc_engine.js` | ~100 | Core: StateMachine + JournalEngine + table creation |
| `deploy/dev/category_loader.js` | ~30 | Registry reader |
| `deploy/dev/erp_panel.js` | ~150 | ERP panel UI: card renderer + status display. Lazy-loaded by [ERP] button in toolbar, same as measure.js pattern. |
| `deploy/dev/swipe.js` | ~150 | Card stack + touch gestures (used by erp_panel.js) |
| `deploy/dev/role_band.js` | ~80 | Role switcher + QR share (used by erp_panel.js) |
| `deploy/dev/handlers/construction.js` | ~200 | Lead lifecycle + BOQ + FAR handlers |
| `deploy/dev/erp.html` | ~80 | Standalone document-only entry. No Three.js. Loads same modules. For mobile/document-only users. |
| `deploy/dev/buildings/construction.db` | seed data | The POC database |
| `deploy/dev/tests/test_doc_engine.js` | ~80 | Unit tests for core engine |

### Modified files

| File | Change |
|---|---|
| `deploy/dev/main.js` | Load doc_engine.js, category_loader.js. Init tables on DB load. |
| `deploy/dev/kernel_ops.js` | Add user_tag column (idempotent ALTER). |
| `deploy/dev/index.html` | Add [ERP] icon to toolbar (lazy-loads erp_panel.js on tap). Include doc_engine.js. |
| `deploy/dev/tools.js` | Register [ERP] toolbar button alongside Measure, X-Ray, Clash. |

### NOT modified

- `deploy/live/*` — Production — NEVER touch
- `deploy/dev/scene.js` — Three.js scene untouched
- `deploy/dev/measure.js` — Measure tool untouched (pattern to follow, not modify)
- `deploy/dev/boq_charts.html` — BOQ engine reused as-is by handlers
- `deploy/dev/rates.js` — Rate templates reused as-is
- `deploy/dev/share.js` — QR/share reused by role_band.js

---

## §11. Boundaries and Constraints

- **No server.** No iDempiere. No PostgreSQL. Browser only.
- **No redundant 3D scene in erp.html.** Document-centric only. Spatial = BIM OOTB.
- **[ERP] is just another toolbar icon.** Same pattern as [Measure]. Lazy-loaded.
- **erp.html is standalone.** For mobile/document-only users who don't need 3D.
- **IFC = the building.** Architect provides IFC → viewer imports → geometry links to container.
- **BOQ reuses existing engine.** `boq_charts.html` + `rates.js` already compute costs.
- **kernel_ops is primary.** Every mutation goes through commitOp.
- **Confidential fields filtered in UI, not enforced server-side.**
- **Handlers are stateless.** Read → compute → commitOp.
- **Mobile-first.** pointerup not click. Touch targets ≥ 44px.
- **Never touch deploy/live/.**

---

## §12. What This Unlocks (Post-POC)

| Phase | What | Same core? |
|---|---|---|
| F&B domain | New seed .db: restaurant tables. New handlers. | Yes — same doc_engine.js |
| WMS domain | New seed .db: racks, bins, SKUs. | Yes — new handler only |
| Multi-project | Multiple leads/plots on one site. Portfolio view. | Yes — more containers |
| Cloud relay | Upload .db to OCI. Role-based QR links. | Yes — no code change |
| Full P2P | Lead → approve → PO → GR → Invoice → Payment | Yes — P2P handlers (§10b in spec) |
| iDempiere graduation | Export to C_Project, C_ProjectLine, M_Product | Data migration, not rewrite |

---

## §13. Reference

- `docs/SpatialERP_OOTB.md` — full spec (schema, UX, architecture, P2P, market)
- `~/Downloads/Idempiere Construction ERP.pptx` — Sysnova source requirement
- `docs/BOMBasedCompilation.md` §1 — iDempiere entity mapping
- `deploy/dev/kernel_ops.js` — existing OpLog
- `deploy/dev/boq_charts.html` — existing BOQ/QTO engine
- `deploy/dev/rates.js` — existing rate templates
- `deploy/dev/share.js` — existing share mechanism
- `prompts/iDempiereOOTB.md` — the old approach (OSGi). Superseded.

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
