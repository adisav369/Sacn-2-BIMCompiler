# BIM Designer SRS — UX Requirements & User Journeys
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md)

**Version:** 1.1 (2026-03-20)
**Depends on:** [BIM_Designer.md](BIM_Designer.md) §17-18, [G4_SRS.md](G4_SRS.md), [DocValidate.md](DocValidate.md) §15,
[BACK_OFFICE_SRS.md](BACK_OFFICE_SRS.md), [ASSEMBLY_BUILDER_SRS.md](ASSEMBLY_BUILDER_SRS.md), [INFRA_DESIGNER_SRS.md](INFRA_DESIGNER_SRS.md)
**Scope:** Testable functional requirements, user journey acceptance criteria, UX edge cases

> This document complements BIM_Designer.md (architecture/vision, 2914 lines) with SRS-grade
> rigour: numbered requirements, acceptance criteria, latency contracts, error states, and
> concrete user journeys. BIM_Designer.md says WHAT and WHY. This document says HOW TO VERIFY.

---

## 1. Requirement Taxonomy

| Prefix | Domain | Count |
|--------|--------|-------|
| UX-F | Functional (user-facing behaviour) | 33 |
| UX-N | Non-functional (latency, capacity) | 10 |
| UX-E | Error/edge-case handling | 12 |
| WF | Wireframe-First interaction (§26) | 25 |

Priority: **P0** = must-have for first usable demo, **P1** = needed for daily use, **P2** = polish.

### 1.1 Foundation Guarantee — Zero Delta Compilation

All five Rosetta Stone buildings compile with verified volume:
SH (55), FK (82), IN (699), DX (1,099), TE (48,428). The BOM→compile→output pipeline is lossless.

This is the bedrock UX guarantee: **what the user designs IS what the compiler
produces.** No approximation, no drift, no geometry surprise. Every UX claim
in this document — slider edits, jurisdiction switches, version recall — rests
on the fact that BOM data deterministically reproduces exact geometry. The
compiler is a pure function: same input → same output, always.

---

## 2. Functional Requirements (UX-F)

### 2.1 Onboarding — "3 Minutes to First Building"

The defining UX claim: a user who has never seen the tool can produce a visible
3D building in under 3 minutes. Every step must feel obvious — no manual, no
tutorial video, no configuration file editing.

| ID | Requirement | Acceptance Criteria | Priority | Spec Ref |
|----|------------|-------------------|----------|----------|
| UX-F-01 | **Zero-config startup.** User opens Blender, enables two addons (Federation + BIM Designer), and the panel stack appears. No `settings.json`, no server IP entry, no DB path selection on first run. | Panel A.1 shows "Connected" within 2s of addon enable if Java server is running; shows "Server not found — Start Server?" button if not. | P0 | §16.7 |
| UX-F-02 | **One-click building.** "Create New" with all defaults produces a valid building. The user need only type a name and click Create. | `createNew` with default buildingType="DM", jurisdiction="MY", 2BR/1BT, 9000×7000 site returns bboxes within 200ms. Viewport shows coloured boxes. | P0 | §17.14 |
| UX-F-03 | **Immediate visual feedback.** After Create New, the user sees coloured bboxes in the 3D viewport — not a blank screen, not a loading spinner. | `design_bbox.enable(bboxes)` renders GPU overlay within 1 frame (16ms) of receiving server response. Bboxes use category colours (§17.8). | P0 | §17.3, §17.8 |
| UX-F-04 | **Guided form.** Create New dialog shows only essential fields with sensible defaults. Advanced fields (grid snap, construction system) are collapsed. | Default form: Name (text), Type (dropdown, default DM), Jurisdiction (dropdown, default MY), Bedrooms (spinner 1-6, default 2), Bathrooms (spinner 1-3, default 1), Site W×D (mm, default 9000×7000), Storeys (spinner 1-3, default 1). All fields have tooltip. | P0 | §Item 2 |
| UX-F-05 | **Discoverable mode toggle.** REAL/DESIGN toggle is a prominent button at the top of the panel, not buried in a submenu. | Toggle button: 44px height, full panel width, label changes between "Enter Design Mode" / "Exit Design Mode". Keyboard shortcut: `D` in Properties panel context. | P0 | §17.1 |

### 2.2 Design Mode — Visual Editing

| ID | Requirement | Acceptance Criteria | Priority | Spec Ref |
|----|------------|-------------------|----------|----------|
| UX-F-06 | **Grey-out context.** Entering Design Mode mutes all existing bboxes to uniform grey. Draft bboxes appear in vivid category colours. | `set_color_override((0.4, 0.4, 0.4, 0.2))` applied to all Federation batches. User can distinguish draft from committed at a glance. | P0 | §17.4 |
| UX-F-07 | **Section focus.** Clicking a room card in the panel highlights that bbox vivid and shows dimension sliders. All other bboxes stay grey/muted. | `focus_section(bomId)` sets one bbox to category colour, all others to grey. Slider panel appears below with Width/Depth fields. | P0 | §17.6 |
| UX-F-08 | **Slider editing.** Dragging a dimension slider updates the bbox in real-time (no server round-trip for visual preview). Server validates on release. | Slider drag: bbox resizes locally via GPU uniform update (0ms latency). On mouse-up: `snap` action sent to server, response adjusts if needed. | P1 | §17.13 |
| UX-F-09 | **Room addition.** "Add Room" button creates a new bbox packed into the next available slot in the current storey. | New bbox inserted at first non-overlapping position. If no space: panel shows "Storey full — expand site or add storey?" prompt. | P1 | §17.6 |
| UX-F-10 | **Room removal.** Selecting a room and pressing Delete removes its bbox. Neighbours do NOT auto-resize (explicit user action). | Bbox removed from scene. C_OrderLine marked IsActive=0 (soft delete). Undo with Ctrl+Z restores bbox + OrderLine. | P1 | §17.9 |
| UX-F-11 | **Storey addition.** "Add Storey" creates a new FLOOR bbox stacked above existing floors. Rooms default to copy of ground floor layout. | FLOOR bbox at Z = existingStoreys × storeyHeight. Room bboxes cloned from GF with same proportions. Panel shows new storey in hierarchy. | P1 | §17.15 |
| UX-F-12 | **Commit visual feedback.** On Save, vivid bboxes briefly pulse (2s alpha animation), then settle to slightly more opaque — the colour shift confirms the save landed. | `mark_committed()` triggers alpha interpolation: 1.0 → 0.7 → SOLID_BOOST over 2 seconds. No modal dialog needed. | P1 | §17.17 |

### 2.3 Persistence — Save / Recall / Approve / Promote

| ID | Requirement | Acceptance Criteria | Priority | Spec Ref |
|----|------------|-------------------|----------|----------|
| UX-F-13 | **One-click Save.** Save creates an immutable version (sub-C_Order CO) with a user-provided label. No "Save As" dialog needed — every Save is a new version. | Save completes in <500ms. Panel shows "Saved: 'wide-rooms' (v3)" confirmation. W_Variant row created. Previous versions unchanged. | P0 | §17.10.2, G4_SRS §2.2 |
| UX-F-14 | **Version list.** "Recall" shows a list of all saved versions with label, timestamp, line count, and compliance status icon. | `listVariants` returns ordered list. Panel renders as scrollable list with ✓/⚠/✗ compliance badges. Most recent at top. | P0 | §17.10.5 |
| UX-F-15 | **Non-destructive Recall.** Recalling a version copies it into a fresh sub-order (DR). The recalled version stays CO. No data destruction. | After recall: previous sub-orders still queryable. New sub-order has same OrderLine data. Panel shows "(recalled from v2)". | P0 | G4_SRS §2.3 |
| UX-F-16 | **Approve gate.** Approve transitions master C_Order IP→AP. Requires all validation PASS and no dangles. Shows clear status of what blocks approval. | If blocked: panel shows categorised blockers — "3 validation failures, 1 dangling reference" with clickable list. Each blocker links to the offending bbox. | P1 | §17.10.2, G4_SRS §3 |
| UX-F-17 | **Promote confirmation.** Promote shows a summary dialog (entries to create, owner, compliance ref, dangles count) before writing to BOM.db. | Confirmation dialog matches §17.10.4 layout. Promote blocked unless DocStatus=AP. Success shows "Promoted! N BOM entries created." | P1 | §17.10.4 |

### 2.4 Ambient Compliance — "Spell-Checker for Buildings"

| ID | Requirement | Acceptance Criteria | Priority | Spec Ref |
|----|------------|-------------------|----------|----------|
| UX-F-18 | **Live status strip.** A persistent bar at the bottom of Design Mode shows per-rule compliance for the focused section. Red/yellow/green indicators. | Strip shows: jurisdiction label, then each applicable AD_Val_Rule result. Updated within 300ms of any dimension change. Always visible, never modal. | P0 | §18.4 |
| UX-F-19 | **Delta display on failure.** When a rule fails, the strip shows the exact shortfall: "2950mm < 3000mm (need +50mm)". | Failed rule text: `{actual} < {required} (need +{delta})`. Green rules show: `{actual} ≥ {required} ✓`. | P0 | §18.4 |
| UX-F-20 | **Click-to-fix.** Clicking a failed rule in the status strip highlights the offending bbox and offers "Auto-fix" which calls Snap to resolve. | Click: `focus_section(failedBomId)` + highlight rule's axis on bbox (thicker edge). "Auto-fix" button sends `snap` action with that rule's axis constraint. | P1 | §18.4, §17.13 |
| UX-F-21 | **Jurisdiction dropdown.** Changing jurisdiction instantly swaps the active AD_Val_Rule set. Status strip updates. No recompile needed — just re-validate. | Dropdown change → `PlacementValidator.setJurisdiction(code)` → re-validate all visible bboxes → status strip redraws within 300ms. | P1 | §18.2 Principle 1 |

### 2.5 BOM Chooser — Product Search

| ID | Requirement | Acceptance Criteria | Priority | Spec Ref |
|----|------------|-------------------|----------|----------|
| UX-F-22 | **Search-first.** BOM Chooser opens with cursor in search box. Typing filters instantly. Category tree is secondary. | Search box auto-focused on open. Results appear within 100ms of typing pause (debounce 150ms). Empty state shows top-level categories. | P1 | §17.18.1 |
| UX-F-23 | **Fit status per item.** Each search result shows FITS/TIGHT/TOO WIDE badge relative to the focused room's AABB. | Badge colours: green=FITS (>100mm clearance), yellow=TIGHT (<100mm), red=TOO WIDE/DEEP/TALL. Items NOT hidden when they don't fit — just badged. | P1 | §17.18.3 |
| UX-F-24 | **Place action.** Selecting an item and clicking "Place" creates a C_OrderLine referencing that product, positioned at its tack point inside the focused room. | Bbox appears at tack position. OrderLine inserted in work_output.db. Undo removes bbox + OrderLine. | P1 | §17.18.4 |
| UX-F-25 | **Set vs individual.** User can place a full set (parent + children) or cherry-pick individual items from a set. | "Place Set" creates parent + child OrderLines. "Pick Individual" shows set's leaves with individual Place buttons. | P2 | §17.18.5 |

### 2.6 Multi-View Sync

| ID | Requirement | Acceptance Criteria | Priority | Spec Ref |
|----|------------|-------------------|----------|----------|
| UX-F-26 | **BBox ↔ ORDER View sync.** Editing a dimension in the ORDER View (tabular) updates the corresponding bbox in the viewport, and vice versa. | Change in either view reflected in the other within 200ms (sync timer). No manual refresh needed. | P1 | §17.11, §18.3 |
| UX-F-27 | **Three views, one truth.** BBox Design, ORDER View, and BOM Outliner all read/write the same C_OrderLine + ASI data in work_output.db. | Concurrent edits from any view produce consistent state. No race conditions: sync timer serialises updates. | P1 | §17.19, §18.3 |
| UX-F-28 | **Undo across views.** Ctrl+Z undoes the last operation regardless of which view triggered it. | Blender undo stack tracks scene property changes. Undo after ORDER View edit restores bbox state. Undo after bbox drag restores ORDER View value. | P1 | §17.9 |

---

## 3. Non-Functional Requirements (UX-N)

### 3.1 Latency Contracts

These are user-perceptible latency budgets. The system must meet these at the
stated scale or the UX falls apart — slow tools teach users not to iterate.

| ID | Operation | Target | Scale | How | Priority |
|----|-----------|--------|-------|-----|----------|
| UX-N-01 | createNew → bboxes visible | < 200ms | 2-storey, 10 rooms | RoomLayoutGenerator in-memory, no DB write | P0 |
| UX-N-02 | Mode toggle (Design ↔ Real) | < 50ms | Any | GPU colour uniform swap only | P0 |
| UX-N-03 | Section focus (click room card) | < 50ms | Any | GPU colour change, slider panel swap | P0 |
| UX-N-04 | Slider drag (visual preview) | 0ms (local) | Any | GPU bbox resize, no server call | P0 |
| UX-N-05 | Snap validation (on slider release) | < 300ms | 20 rooms, 6 jurisdictions | PlacementValidator batch, cached rules | P0 |
| UX-N-06 | Save to work_output.db | < 500ms | 50 OrderLines + ASI | SQLite batch INSERT in transaction | P0 |
| UX-N-07 | BOM Chooser search | < 100ms | 24K products | SQL LIKE + AABB pre-filter, paginated | P1 |
| UX-N-08 | Recall version | < 500ms | 50 OrderLines | COPY rows between sub-orders | P1 |
| UX-N-09 | Full compile (SH-scale) | < 3s | 55 elements | Existing pipeline, no scope limiting | P1 |
| UX-N-10 | Full compile (TE-scale) | < 30s | 48K elements | Existing pipeline, full 9-stage | P2 |

### 3.2 Capacity Contracts

| ID | Metric | Minimum | Notes |
|----|--------|---------|-------|
| UX-N-11 | Max rooms per building | 100 | Residential + commercial mixed-use |
| UX-N-12 | Max storeys | 10 | RoomLayoutGenerator must handle stacking |
| UX-N-13 | Max saved variants per building | 50 | W_Variant rows, all retained |
| UX-N-14 | Max products in BOM Chooser | 25,000 | component_library.db current count: 23,888 |
| UX-N-15 | Max concurrent BBox overlays | 500 | GPU batch limit before frame rate drops below 30fps |

---

## 4. Error & Edge-Case Requirements (UX-E)

Every error state must have a recovery path. The user must never see a raw
exception, a blank screen, or a "something went wrong" without actionable next steps.

### 4.1 Connection & Server

| ID | Scenario | Required Behaviour | Recovery |
|----|----------|-------------------|----------|
| UX-E-01 | **Server not running.** User enables addon but Java server is not started. | Panel A.1 shows: "Server not found" + "Start Server" button. All other sub-panels greyed out with tooltip "Connect to server first". | "Start Server" runs `java -jar bim-compiler.jar --server` via subprocess. Auto-retry connection every 2s for 10s. |
| UX-E-02 | **Server disconnects mid-session.** TCP connection drops during design work. | All unsaved bbox data preserved in scene properties (Blender undo stack). Panel shows "Connection lost — Reconnect?" button. No data loss. | On reconnect: send current state to server for re-validation. Server is stateless — reconnect is seamless. |
| UX-E-03 | **Server crash during Save.** Save action sent, server dies before response. | Client times out after 5s. Panel shows "Save may not have completed — Reconnect and verify." On reconnect: `listVariants` to check if save landed. | If save didn't land: retry. If save landed: show confirmation. Either way, no data corruption — SQLite transactions are atomic. |

### 4.2 Validation & Compliance

| ID | Scenario | Required Behaviour | Recovery |
|----|----------|-------------------|----------|
| UX-E-04 | **All rooms violate rules.** User creates a building smaller than minimum dimensions. | Status strip turns red for every rule. No BLOCK — Design Mode always allows editing. Snap offers to resize all rooms to minimum. | User can: (a) accept Snap auto-fix, (b) change jurisdiction to more permissive code, (c) increase site dimensions. |
| UX-E-05 | **Unknown jurisdiction.** Server has no AD_Val_Rule rows for selected code. | Validation returns UNCHECKED for all rules. Status strip shows: "No rules loaded for XX — compliance unchecked". No BLOCK. | User selects a supported jurisdiction. Future: shows which jurisdictions have rule data. |
| UX-E-06 | **Rule conflict.** Two AD_Val_Rule rows give contradictory bounds for same parameter. | Server applies strictest bound (highest min, lowest max). If result is impossible (min > max), returns CONFLICT verdict with both rule citations. | Panel shows both conflicting rules. User must resolve by editing jurisdiction or requesting rule exception. |

### 4.3 Design Operations

| ID | Scenario | Required Behaviour | Recovery |
|----|----------|-------------------|----------|
| UX-E-07 | **Overlapping rooms.** User drags a room bbox to overlap another. | Overlap drawn with red hatching on the intersection zone. Snap resolves overlap by shifting the moved bbox to nearest non-overlapping position. | Overlap does NOT block editing. Snap resolves on demand. Approve gate blocks if unresolved overlaps remain. |
| UX-E-08 | **Room exceeds storey.** User stretches a room beyond storey boundary. | Room bbox drawn with clipped portion in red outline. Status strip shows: "Room exceeds storey by Nmm on X axis." | Snap adjusts room to fit. Or: "Extend storey?" prompt if room is larger than current floor. |
| UX-E-09 | **Zero-size room.** Slider dragged to 0 or negative dimension. | Slider clamped at minimum (100mm or jurisdiction minimum, whichever is larger). Bbox cannot collapse to zero. | Slider lower bound = max(100, jurisdiction_min_dim). |
| UX-E-10 | **Promote with dangles.** User tries to promote a design with unresolved product references. | Promote blocked. Panel shows dangling references: "WINDOW_CUSTOM_1800 not in catalog". Each dangle links to the OrderLine. | User resolves each: swap to existing product, or create the product in component_library.db first. |
| UX-E-11 | **Promote without Approve.** User clicks Promote when master C_Order is IP (not AP). | Promote greyed out. Tooltip: "Approve design first (compliance gate)". Approve button highlighted. | User clicks Approve. If validation passes → AP → Promote enabled. If validation fails → fix issues first. |
| UX-E-12 | **Empty building.** User creates a building but removes all rooms. | Building bbox remains (cannot delete root). Panel shows: "No rooms — Add Room to continue." Save allowed (empty version is a valid state). | User adds rooms via "Add Room" button. |

---

## 5. User Journeys — Concrete Walkthroughs

### 5.1 Journey 1: First Building (P0 — "Hello World")

**Persona:** Architect, first time using the tool. Has Blender installed with Bonsai.
**Goal:** See a 3D building in under 3 minutes.
**Pre-condition:** Java server running (`java -jar bim-compiler.jar --server`).

```
Step  Action                              System Response                        Verify
─────────────────────────────────────────────────────────────────────────────────────────
1     Enable Federation addon              Panel stack appears: 1-10             Panels visible
      Enable BIM Designer addon            Panel A inserted between #2 and #3    A.1 shows "Connected"
                                                                                 ← UX-F-01

2     Click "Enter Design Mode"            Grey overlay on existing scene        Mode = DESIGN
                                           Create New form appears               ← UX-F-05, UX-N-02

3     Type "My House" in Name field        —                                     Cursor in name field

4     Click [Create]                       Coloured bboxes appear in viewport    ← UX-F-02, UX-F-03
      (all other fields use defaults)      Building envelope (ghost grey)         UX-N-01: < 200ms
                                           Floor slab (blue)
                                           5 rooms (green/yellow/purple/cyan)
                                           Section chooser shows room cards

5     Click "Living Room" card             Living bbox turns vivid green         ← UX-F-07
                                           Others stay grey                       UX-N-03: < 50ms
                                           Width/Depth sliders appear

6     Drag Width slider to 5000mm          Living bbox stretches in real-time    ← UX-F-08
                                           Status strip: "5000 ≥ 3000 ✓"         UX-N-04: 0ms local
                                           On release: Snap validates             UX-N-05: < 300ms

7     Click [Save]                         Pulse animation (2s)                  ← UX-F-12, UX-F-13
                                           "Saved: 'v1' (6 rooms, 1 storey)"     UX-N-06: < 500ms

8     Click "Exit Design Mode"             Bboxes disappear                      ← UX-F-05
                                           Scene returns to standard view
                                           Total time: ~90 seconds
```

**Acceptance:** Steps 1-8 complete in < 3 minutes for a first-time user who can read English.

### 5.2 Journey 2: Jurisdiction Switch (P1 — "What If Malaysia → Singapore?")

**Persona:** Architect exploring design options across markets.
**Goal:** See how the same building complies under a different country's code.
**Pre-condition:** Building "My House" exists with MY jurisdiction, all rules PASS.

```
Step  Action                              System Response                        Verify
─────────────────────────────────────────────────────────────────────────────────────────
1     Enter Design Mode                    Bboxes appear, status strip green     Mode = DESIGN

2     Change Jurisdiction dropdown:        Status strip re-evaluates             ← UX-F-21
      MY → SG                             Bedroom: 3100mm ✓ (SG has no          UX-N-05: < 300ms
                                           explicit min_dim rule for bedroom)
                                           Ceiling: 3000 ≥ 2400 ✓
                                           Door width: 750 < 850 ✗ (SG min)    Red indicator

3     Click red "Door width" rule          Bbox with DOOR_D1 highlighted        ← UX-F-20
                                           "750mm < 850mm (need +100mm)"         UX-F-19

4     Click "Auto-fix"                     Server snaps: DOOR_D1 width → 850    Product swap needed
                                           If no 850mm door in catalog:          → BOM Chooser opens
                                           "No 850mm door — browse catalog?"

5     Select DOOR_D1_850 from Chooser      OrderLine updated                    ← UX-F-24
                                           Status strip: all green
                                           "SG-compliant ✓"

6     Save as "SG variant"                 New W_Variant created                ← UX-F-13
```

**Acceptance:** Jurisdiction change + compliance resolution in < 2 minutes.

### 5.3 Journey 3: Version Exploration (P1 — "What If I Go Back?")

**Persona:** Architect exploring alternatives, wants to compare versions.
**Goal:** Save multiple variants, recall an earlier one, continue editing.
**Pre-condition:** Building with 3 saved variants (v1: default, v2: wide-rooms, v3: SG-compliant).

```
Step  Action                              System Response                        Verify
─────────────────────────────────────────────────────────────────────────────────────────
1     Click [Recall]                       Version list panel appears:           ← UX-F-14
                                             v3: SG-compliant ✓  (current)
                                             v2: wide-rooms ✓
                                             v1: default ✓
                                           Each shows: label, time, lines, badge

2     Click "v1: default"                  Bboxes revert to v1 layout           ← UX-F-15
                                           "(recalled from v1)" label            UX-N-08: < 500ms
                                           v1 stays CO (immutable)
                                           New sub-order DR created

3     Edit: add a storey                   Second floor appears                  ← UX-F-11
                                           Room cards show GF + FF

4     Save as "2-storey from v1"           v4 created, v1/v2/v3 unchanged       ← UX-F-13
                                           Version list now shows 4 entries

5     Ctrl+Z (undo save)                   Reverts to pre-save state             ← UX-F-28
                                           v4 removed from variant list
```

**Acceptance:** Full version round-trip (recall → edit → save → undo) with zero data loss.

### 5.4 Journey 4: BOM Chooser — Furnishing a Room (P1)

**Persona:** Architect furnishing rooms after layout is settled.
**Goal:** Find and place a queen bed in a bedroom.
**Pre-condition:** Building in Design Mode, bedroom focused (3100×3100×3000mm).

```
Step  Action                              System Response                        Verify
─────────────────────────────────────────────────────────────────────────────────────────
1     Click [Add Item] in focused room     BOM Chooser opens                    ← UX-F-22
                                           Cursor in search box
                                           Empty state: category tree shown

2     Type "queen bed"                     3 results appear:                    UX-N-07: < 100ms
                                             BED_QUEEN_1600  ✓ FITS
                                             BED_QUEEN_1500  ✓ FITS
                                             BED_QUEEN_1800  ✗ TOO WIDE        ← UX-F-23

3     Click BED_QUEEN_1600                 Preview: bed bbox at tack position   ← UX-F-24
                                           Ghost outline in focused room
                                           "Default: back wall, 200mm offset"

4     Click [Place]                        Bbox materialises (vivid colour)
                                           C_OrderLine created
                                           Room remaining space updated

5     Click [Show Parent Set]              Expands BD_SET_02 (Queen Set):       ← UX-F-25
                                             BED_QUEEN_1600 ✓ (placed)
                                             BEDSIDE_TABLE ×2 ✓ FITS
                                             WARDROBE_1200 ✓ FITS
                                           "Place remaining set items?"

6     Click [Place Set]                    3 more bboxes appear at tack offsets
                                           4 C_OrderLines total
```

**Acceptance:** Search → preview → place in < 30 seconds.

### 5.5 Journey 5: Promote to BOM — Graduation (P1)

**Persona:** Architect who has finalised a design and wants it as a reusable template.
**Goal:** Promote a design from work_output.db into the BOM catalog.
**Pre-condition:** Building fully designed, all compliance PASS, no dangles.

```
Step  Action                              System Response                        Verify
─────────────────────────────────────────────────────────────────────────────────────────
1     Click [Approve]                      PlacementValidator runs (ACTIVE)     ← UX-F-16
                                           Host tack check (W-TACK-1)
                                           Dangle check
                                           Result: "12 PASS, 0 WARN, 0 BLOCK"
                                           Master C_Order: IP → AP

2     Click [Promote to BOM]               Confirmation dialog:                 ← UX-F-17
                                             "Create 7 BOM entries in BOM.db"
                                             Owner: red1
                                             Compliance: UBBL 2012 (12 rules)
                                             Dangles: 0
                                             Provenance: GENERATIVE

3     Click [Promote]                      BOM entries written                   G4_SRS §2.4
                                           Master C_Order: AP → CO (frozen)
                                           "Promoted! 7 BOM entries created.
                                            Available as templates for future
                                            buildings."

4     Start new building                   BOM Chooser now shows the promoted
                                           rooms as available templates
                                           (compound enrichment)
```

**Acceptance:** Approve + Promote completes. New building can use promoted templates immediately.

---

## 6. UX State Machine — Formal Definition

BIM_Designer.md §17.2 defines the visual state machine informally. This section
formalises it as testable states and transitions.

### 6.1 States

| State | ID | Entry condition | Visual characteristic | Panel state |
|-------|----|-----------------|-----------------------|-------------|
| REAL | S0 | Default / exit Design Mode | Federation colours, no grey overlay | A.2 Compile active, A.3 collapsed |
| DESIGN_CANVAS | S1 | Enter Design Mode, no focus | All bboxes grey, section chooser visible | A.3 form + room cards visible |
| DESIGN_FOCUS | S2 | Click room card | Focused bbox vivid, rest grey | Dimension sliders visible for focused room |
| DESIGN_COMMITTED | S3 | After Save | Saved bbox with SOLID_BOOST, rest grey | "Saved" confirmation, version label shown |
| APPROVE_PENDING | S4 | Click Approve | Validation running (spinner) | Approve button disabled, status strip updating |
| APPROVED | S5 | All validation PASS | Status strip all-green, Promote enabled | Promote button enabled |
| PROMOTED | S6 | Promote completes | Master frozen | Design Mode disabled for this building |

### 6.2 Transitions

```
S0 ──[toggle Design]──→ S1
S1 ──[click room card]──→ S2
S2 ──[click different room]──→ S2 (new focus)
S2 ──[Save]──→ S3
S3 ──[click room card]──→ S2 (continue editing)
S3 ──[toggle Real]──→ S0
S1 ──[toggle Real]──→ S0
S2 ──[toggle Real]──→ S0
S2 ──[Approve]──→ S4
S4 ──[validation PASS]──→ S5
S4 ──[validation FAIL]──→ S2 (with blockers shown)
S5 ──[Promote]──→ S6
S5 ──[edit anything]──→ S2 (AP revoked, back to IP)
S6 ──[toggle Real]──→ S0 (promoted, read-only)
```

### 6.3 Invariants (testable)

| Invariant | Description | Witness |
|-----------|-------------|---------|
| INV-1 | In S0, zero Design Mode GPU draw calls active | W-UX-STATE-1 |
| INV-2 | In S1, all bboxes use GREY_OVERRIDE colour | W-UX-STATE-2 |
| INV-3 | In S2, exactly one bbox uses category colour; all others grey | W-UX-STATE-3 |
| INV-4 | S5 → S2 transition revokes AP (any edit invalidates approval) | W-UX-STATE-4 |
| INV-5 | S6 is terminal — no edits allowed on promoted building | W-UX-STATE-5 |
| INV-6 | Ctrl+Z from any state restores previous state (Blender undo) | W-UX-STATE-6 |

---

## 7. Traceability Matrix

Maps requirements to implementation files and test witnesses.

| Req ID | Spec Section | Java File | Python File | Witness | Status |
|--------|-------------|-----------|-------------|---------|--------|
| UX-F-01 | §16.7 | DesignerServer.java | client.py, panel.py (A.1) | W-UX-CONNECT-1 | IMPLEMENTED (basic) |
| UX-F-02 | §17.14 | DesignerAPIImpl.createNew, RoomLayoutGenerator | operator.py, design_bbox.py | W-DS-26 | IMPLEMENTED |
| UX-F-03 | §17.3, §17.8 | — | design_bbox.py (enable) | W-UX-BBOX-1 | IMPLEMENTED |
| UX-F-04 | §Item 2 | CreateNewRequest.java | panel.py (A.4) | — | SPEC ONLY |
| UX-F-05 | §17.1 | — | operator.py (toggle_mode) | — | IMPLEMENTED |
| UX-F-06 | §17.4 | — | design_bbox.py (grey_out) | — | IMPLEMENTED |
| UX-F-07 | §17.6 | — | design_bbox.py (focus_section) | — | IMPLEMENTED |
| UX-F-08 | §17.13 | — (Python-only slider) | panel.py (slider) | W-SLIDER-1..2 | IMPLEMENTED |
| UX-F-09 | §17.6 | DesignerAPIImpl.addRoom | panel.py | W-LAYOUT-1 | IMPLEMENTED |
| UX-F-10 | §17.9 | DesignerAPIImpl.removeRoom | operator.py | W-LAYOUT-2 | IMPLEMENTED |
| UX-F-11 | §17.15 | DesignerAPIImpl.addStorey | operator.py | W-LAYOUT-3 | IMPLEMENTED |
| UX-F-12 | §17.17 | — | design_bbox.py (mark_committed) | — | IMPLEMENTED |
| UX-F-13 | §17.10.2 | DesignerAPIImpl.save, WorkOutputDAO | operator.py (save) | W-JOURNEY-HELLO-4 | IMPLEMENTED |
| UX-F-14 | §17.10.5 | DesignerAPIImpl.listVariants, WorkOutputDAO | panel.py | W-JOURNEY-HELLO-5 | IMPLEMENTED |
| UX-F-15 | G4_SRS §2.3 | DesignerAPIImpl.recall, WorkOutputDAO | operator.py (recall) | W-JOURNEY-HELLO-6 | IMPLEMENTED |
| UX-F-16 | §17.10.2 | DesignerAPIImpl.approve | operator.py | W-APPROVE-1..4 | IMPLEMENTED |
| UX-F-17 | §17.10.4 | DesignerAPI.promote | operator.py (promote) | — | SPEC ONLY (stub) |
| UX-F-18 | §18.4 | DesignerAPIImpl.snap, PlacementValidatorImpl | panel.py (_draw_status_strip) | W-SNAP-1 | IMPLEMENTED |
| UX-F-19 | §18.4 | ValidationVerdict, Adjustment | panel.py (_draw_status_strip) | W-SNAP-1 | IMPLEMENTED |
| UX-F-20 | §18.4, §17.13 | DesignerAPIImpl.snap (fixRule) | panel.py, design_bbox.py | W-FIX-1..2 | IMPLEMENTED |
| UX-F-21 | §18.2 | DesignerAPIImpl.setJurisdiction | panel.py (dropdown) | W-JURIS-1..3 | IMPLEMENTED |
| UX-F-22 | §17.18.1 | DesignerDAO.browseProducts | panel.py (chooser) | W-BROWSE-1..6 | IMPLEMENTED (session 27) |
| UX-F-23 | §17.18.3 | DesignerAPIImpl.computeFitStatus | panel.py | W-BROWSE-7..9 | IMPLEMENTED (session 27) |
| UX-F-24 | §17.18.4 | DesignerAPIImpl.placeItem | operator.py | W-PLACE-1..4 | IMPLEMENTED |
| UX-F-25 | §17.18.5 | DesignerAPI | panel.py | — | SPEC ONLY |
| UX-F-26 | §17.11 | — | panel.py, design_bbox.py | — | SPEC ONLY |
| UX-F-27 | §17.19 | — | panel.py | — | SPEC ONLY |
| UX-F-28 | §17.9 | — | operator.py (UNDO) | — | IMPLEMENTED (basic) |

**Summary:** 23 IMPLEMENTED, 5 SPEC ONLY (UX-F-04, 17, 25, 26, 27). Witnesses: 87 total across 10 test classes.

---

## 8. P0 Implementation Order — Minimum Viable UX

The P0 requirements define the smallest set that delivers the "3 minutes to
first building" promise. This is the critical path.

```
Gate UX-G1: Connection + Create New
  UX-F-01  Zero-config startup
  UX-F-02  One-click building
  UX-F-03  Immediate visual feedback
  UX-F-04  Guided form
  UX-F-05  Discoverable mode toggle
  UX-N-01  createNew < 200ms
  UX-N-02  Mode toggle < 50ms
  UX-E-01  Server not running → "Start Server" button

Gate UX-G2: Visual Editing
  UX-F-06  Grey-out context
  UX-F-07  Section focus
  UX-N-03  Section focus < 50ms
  UX-N-04  Slider drag 0ms

Gate UX-G3: Compliance + Save
  UX-F-13  One-click Save
  UX-F-18  Live status strip
  UX-F-19  Delta display on failure
  UX-N-05  Snap validation < 300ms
  UX-N-06  Save < 500ms

Dependency: UX-G1 → UX-G2 → UX-G3
```

After UX-G3, the user can: create a building, edit room dimensions visually,
see compliance live, and save versions. This is the MVP.

---

## 9. Witness Claims — Future Test Specs

When implementation moves from SPEC ONLY to code, each requirement gets a
witness claim. Format follows the project convention (BomValidator pattern).

| Witness | Tests | Requirement |
|---------|-------|-------------|
| W-UX-CONNECT-1 | Server auto-detected on addon enable, panel shows "Connected" | UX-F-01 |
| W-UX-CREATE-1 | createNew with defaults returns ≥6 bboxes in <200ms | UX-F-02, UX-N-01 |
| W-UX-BBOX-1 | design_bbox.enable() renders GPU overlay with category colours | UX-F-03 |
| W-UX-GREY-1 | Design Mode entry applies GREY_OVERRIDE to all Federation batches | UX-F-06 |
| W-UX-FOCUS-1 | focus_section(bomId) sets exactly one bbox to category colour | UX-F-07, INV-3 |
| W-UX-SAVE-1 | Save creates sub-C_Order CO + W_Variant in <500ms | UX-F-13, UX-N-06 |
| W-UX-COMPLY-1 | Status strip updates within 300ms of dimension change | UX-F-18, UX-N-05 |
| W-UX-DELTA-1 | Failed rule shows exact mm shortfall and "need +N" text | UX-F-19 |
| W-UX-RECALL-1 | Recall creates new sub-order, previous versions unchanged | UX-F-15 |
| W-UX-STATE-1..6 | State machine invariants (§6.3) | INV-1..INV-6 |

---

## 10. What Makes This UX Unique — Analysis

### 10.1 The Compilation Advantage

Most BIM tools edit geometry directly — every click mutates mesh data. Our tool
edits **metadata** (OrderLine + ASI) and **compiles** geometry. This inversion
enables UX patterns that geometry-first tools cannot match:

| UX Pattern | Why geometry-first tools can't do it | Our approach |
|-----------|--------------------------------------|-------------|
| **Instant jurisdiction switch** | Geometry doesn't know about building codes. Changing country means manual redesign. | OrderLine unchanged. PlacementValidator swaps AD_Val_Rule set. Re-validate, don't redesign. |
| **Non-destructive versioning** | Saving mesh snapshots is expensive (MB per version). Undo is sequential. | Saving OrderLine rows is cheap (KB per version). Recall any version, not just sequential undo. |
| **Ambient compliance** | Checking geometry against rules requires spatial queries on mesh data (slow). | Checking OrderLine dimensions against AD_Val_Rule bounds is a SQL comparison (fast). |
| **Product swap** | Replacing a component means deleting mesh + creating new mesh + repositioning. | Updating family_ref on OrderLine. Compiler generates correct geometry on next compile. |
| **Three-view editing** | Mesh data is hard to render as a table or tree. Views diverge. | OrderLine is inherently tabular. Tree via Parent_OrderLine_ID. BBox via dx/dy/dz. Same data, different projections. |
| **Compound enrichment** | Each project's geometry is siloed. No reuse. | Each Promote adds to BOM catalog. Next project starts richer. |

### 10.2 The "No Save Anxiety" Pattern

Traditional tools: Save overwrites. "Save As" creates confusion. Version chaos.

Our model: **every Save creates a new immutable version.** There is no overwrite.
Recall is a copy, not a restore. The user never loses work. This is the Git model
applied to building design — but with a simpler UX (no branches, no merge conflicts,
just a linear version list with labels).

The psychological effect: users iterate more when saving is safe. TestFit's 2-3x
iteration improvement comes from speed alone. We add safety: iterate freely because
you can always go back. Speed × safety = confidence to explore.

### 10.3 The "Teammate" Interaction Model

The system is opinionated but not authoritarian:

| System behaviour | Authoritarian (Revit) | Teammate (BIM Designer) |
|-----------------|----------------------|------------------------|
| Room too small | Error dialog, blocks placement | Status strip shows shortfall, auto-fix offered, editing continues |
| Product doesn't fit | Hidden from catalog | Shown with red badge + "TOO WIDE". User might resize room. |
| Compliance fails | Separate validation report, post-design | Live strip during design. Fix as you go. |
| Undo | Sequential Ctrl+Z only | Version list — jump to any point |
| Defaults | Generic | Jurisdiction-aware, building-type-aware, history-aware |

The key: **information, not gates.** Design Mode never blocks (only Approve does).
The user sees consequences in real-time but retains full control. This is the
Finch3D insight applied to a BOM-aware system.

---

## 11. Output.db Relationship Discovery — "More Resolved Than We Think"

> **Context:** The main compilation session is still hardening BBC.md SRS and
> processing the output pipeline. This section documents what the output.db
> schema already resolves — grounding future Designer UX in proven data, not
> speculative maths. The stable foundation is `*_BOM.db` (m_bom + m_bom_line
> with tack dx/dy/dz, verb_ref, allocated dimensions) and `WorkOrderGuide.md`
> (pipeline config). The output.db tables below are the **compiled result**
> of that BOM data — relationship tables that the other session is actively
> populating and verifying.

### 11.1 The Finding

The output.db (FederatedModel schema) already contains **rich IFC relationship
data** that the specs have been treating as "missing extraction columns" or
"AABB arithmetic needed." Verified counts from actual compiled output:

| Table | SH (55 el.) | TE (48,428 el.) | What it resolves |
|-------|-------------|-----------------|------------------|
| `assembly_components` | 85 unique (212 raw — 127 dupes) | 263,869 unique (791K raw — 528K dupes) | **Parent-child**: door→wall, pipe→system, plate→roof |
| `element_assemblies` | 8 | 50 | **Container bounds**: per-storey-per-discipline AABB |

> **Data quality note:** `assembly_components` has duplicate (assembly_guid,
> component_guid) rows — same element inserted 2-3× into the same assembly.
> The PRIMARY KEY constraint in output_schema.sql should prevent this but the
> writer inserts without `INSERT OR IGNORE`. The unique pair count is the
> meaningful number. The other session's output pipeline hardening should
> address this duplication. Raw row counts in this document use **unique**
> (deduplicated) values unless noted.
| `rel_contained_in_space` | 55 (100%) | 48,428 (100%) | **Storey containment**: every element → its IfcBuildingStorey |
| `spatial_structure` | 4 | 8 | **Building hierarchy**: Building → Storeys with parent_guid |
| `material_layers` | 12 | 0 (TE has none) | **Wall composition**: layer-by-layer with thickness_mm |
| `element_properties` | 55 | (exists) | **IFC property sets**: Pset_IfcDoorCommon etc. |
| `simple_qto` | 8 | (exists) | **Quantities**: area, volume from IfcQuantitySet |

### 11.2 Impact on Validation Rules — Do Away With More Maths

The `assembly_components` table has columns: `assembly_guid, component_guid,
role, local_x, local_y, local_z, sequence, optional`. The `role` column
currently stores ifc_class (IfcDoor, IfcWall, etc.) but the **parent-child
FK** is the relationship that matters.

**Rules that can now be pure SQL JOINs (not AABB proximity):**

| Rule | Was (AABB arithmetic) | Now (SQL JOIN) | SQL |
|------|----------------------|---------------|-----|
| **M16/M17** Opening host | AABB_PROXIMITY 200mm tolerance | `assembly_components` WHERE role='IfcDoor' → JOIN to parent assembly → host resolved | `SELECT ac.assembly_guid FROM assembly_components ac WHERE ac.component_guid = :door_guid` |
| **M13-15** Vertical continuity | SAME_COLUMN predicate with XY tolerance | `rel_contained_in_space` + `elements_meta.storey` → group by product across storeys | `SELECT storey, COUNT(*) FROM elements_meta em JOIN rel_contained_in_space rc ON em.guid = rc.element_guid WHERE em.ifc_class = :class GROUP BY storey` |
| **Container bounds** | Calculate from children's AABB | `element_assemblies.total_width/depth/height` already has it | Direct read — no computation |
| **Fire rating** | Missing column (M11) | `material_layers.material_name` + thickness → U-value / fire rating derivable | JOIN `material_layers` on wall element_name pattern |
| **Storey membership** | SAME_LEVEL Z-band tolerance | `rel_contained_in_space` — 100% coverage, FK not float | Direct FK lookup |

### 11.3 BOM.db Foundation — What the Designer Reads Directly

The Designer's primary data source is `*_BOM.db`, not output.db. The BOM
schema is the stable, proven foundation (SH/DX/TE all GREEN, 7/7 gates):

**m_bom** — assembly recipes with AABB:
- `bom_id, bom_name, bom_category` — identity + classification
- `aabb_width_mm, aabb_depth_mm, aabb_height_mm` — container bounds
- `origin_x/y/z` — world anchor (only BUILDING non-zero, R16 fix)
- `entity_type` (D/U/A) — governance guard

**m_bom_line** — recipe children with tack and allocation:
- `dx, dy, dz` — LBD tack offset (parent-relative, BBC.md §4)
- `allocated_width/depth/height_mm` — child AABB
- `verb_ref` — factorization verb (TILE, SPRAY, ROUTE, FRAME, or NULL for leaf)
- `qty` — factored count (1 for leaf, N for verb-expanded)
- `anchor_face, rotation_rule, orientation` — placement attributes
- `child_product_id` → component_library.db M_Product FK

The Designer reads BOM.db via `DesignerDAO.java` (proven, tested). The output.db
relationship tables are downstream — produced by the compiler from this BOM data.
The finding below is that the output already resolves more than specs acknowledge,
which means UI verbs can query compiled relationships instead of re-deriving them.

### 11.4 New Rule: **Prefer FK Over Float** (extends Schema-Not-Geometry)

> **When output.db has a foreign key (parent_guid, assembly_guid,
> space_guid), never use AABB float arithmetic to derive the same
> relationship.** FKs are exact. Floats have tolerance drift.

This extends the Schema-Not-Geometry rule (BBC.md §2):
- **Level 1 (existing):** If IFC has a relationship, extract it as a column
- **Level 2 (new):** If output.db already has the FK, use it — don't re-derive from geometry
- **Level 3:** Only use AABB arithmetic when no FK or IFC relationship exists

### 11.5 Impact on BIM_COBOL Spatial Predicates

The spatial predicates in BIM_COBOL §20 specced AABB fallbacks for several
operations. With output.db FKs, they can upgrade:

| Predicate | BIM_COBOL §20 spec | Upgraded implementation |
|-----------|-------------------|----------------------|
| `HOST_OF` | "AABB containment in 2/3 axes. Upgrades to FK when R21 lands" | **R21 already landed** — `assembly_components.assembly_guid` IS the host FK. No upgrade needed — use it now. |
| `WITHIN` | "R-tree: minX >= c.minX AND maxX <= c.maxX" | Can also use `rel_contained_in_space` for element→space containment. R-tree for arbitrary containment. |
| `SAME_LEVEL` | "Z-band tolerance" | `rel_contained_in_space` → `spatial_structure.parent_guid` gives exact storey match. Z-band is fallback only. |
| `ALONG_PATH` | "Walk ad_element_dependency" | `system_edges` + `system_nodes` gives MEP connectivity graph directly (for TE: populated). |

### 11.6 Impact on BIM Designer UX

**For the Designer, this means simpler, faster, more reliable operations:**

| UX Operation | Old approach (maths) | New approach (FK) | UX impact |
|-------------|---------------------|-------------------|-----------|
| "Which wall hosts this door?" | AABB overlap + proximity search | `SELECT assembly_guid FROM assembly_components WHERE component_guid = :door` | Instant, no tolerance issues |
| "Show all elements on this floor" | Z-band filter on R-tree | `SELECT element_guid FROM rel_contained_in_space WHERE space_guid = :storey` | Exact, no Z-drift |
| "What's in this assembly?" | Parent AABB containment | `SELECT component_guid, role FROM assembly_components WHERE assembly_guid = :asm` | Full tree with roles |
| "Wall composition" | Missing — needed extraction | `SELECT * FROM material_layers WHERE layer_set_name LIKE :wall_type` | Layer-by-layer ready for assembly builder |
| "Move door to different wall" | Re-run AABB proximity | UPDATE `assembly_components SET assembly_guid = :new_wall` | FK update, not geometry recomputation |

---

## 12. UI Engine Verbs — BlenderBridge Thin Pipe Extensions

### 12.1 Current State

BlenderBridge.md §3.3 defines ~10 Python-side verbs (place_box, update_position,
etc.) for the thin pipe. These are geometry operations — creating/moving/removing
Blender objects. With the output.db relationship discovery (§11), we can add
**relationship-aware UI verbs** that go through the same thin pipe.

### 12.2 Relationship-Aware UI Verbs (new)

These verbs query the output.db relationship tables and produce viewport
commands. Java does the query (smart), Python does the bpy (dumb).

| Verb | Java action | What Java returns | Python does |
|------|-------------|-------------------|-------------|
| `highlight_assembly` | Query `assembly_components` for assembly_guid | List of component GUIDs | `select_by_ref(guid)` for each, set highlight colour |
| `highlight_host` | Query `assembly_components` for door/window's parent | Parent assembly GUID + its component GUIDs | Highlight host wall + all its children |
| `show_containment` | Query `rel_contained_in_space` for storey | All element GUIDs in that storey | Show/hide by collection, or colour overlay |
| `show_composition` | Query `material_layers` for wall type | Layer stack with materials + thicknesses | Draw stacked colour bands on wall face (GPU overlay) |
| `trace_system` | Query `system_nodes` + `system_edges` for system_id | Ordered node list with edge types | Draw path highlight along MEP route |
| `show_neighbours` | Query `assembly_components` siblings (same parent) | List of sibling GUIDs | Highlight siblings, dim everything else |
| `navigate_tree` | Query `assembly_components` recursively | Tree of parent→children | Build Outliner collection hierarchy |

### 12.3 Wire Protocol Extensions

```json
{"action":"highlightAssembly", "assemblyGuid":"SH_GF_STR_GROUND_FLOOR"}
→ {"components":["MD_DOOR_GROUND_FLOOR_1","MD_WALL_GROUND_FLOOR_5",...], "count":26}

{"action":"queryHost", "elementGuid":"MD_DOOR_GROUND_FLOOR_1"}
→ {"hostGuid":"SH_GF_STR_GROUND_FLOOR", "hostType":"IfcElementAssembly",
   "siblings":["MD_WALL_GROUND_FLOOR_5","MD_WINDOW_GROUND_FLOOR_10",...]}

{"action":"queryComposition", "wallType":"Basic Wall:Wall-Ext_102Bwk-75Ins-100LBlk-12P"}
→ {"layers":[
     {"seq":0,"material":"Brick","thickness_mm":102},
     {"seq":1,"material":"Insulation","thickness_mm":75},
     {"seq":2,"material":"Lightweight Block","thickness_mm":100},
     {"seq":3,"material":"Plaster","thickness_mm":12}
   ], "total_mm":289}

{"action":"traceSystem", "systemId":"SPRINKLER_L04"}
→ {"nodes":[{"id":"n1","type":"SOURCE",...},{"id":"n2","type":"FIXTURE",...}],
   "edges":[{"from":"n1","to":"n2","type":"PIPE"}]}
```

### 12.4 How These Enable Richer UX

**Assembly explorer (clicking a wall shows its family):**
User clicks wall → Python sends `queryHost` → Java returns assembly tree →
Python highlights all siblings (other walls, doors, windows in same assembly).
The user sees: "this wall is part of SH_GF_STR with 26 elements."

**Material layers panel (clicking a wall shows its composition):**
User clicks wall → Python sends `queryComposition` → Java returns layer stack
→ Python draws coloured bands in panel: Brick 102mm | Insulation 75mm | Block 100mm | Plaster 12mm.
This is the data for the assembly builder (BIM_Designer.md §18.4 Principle 4).

**MEP route tracer (clicking a sprinkler shows its system):**
User clicks sprinkler → Python sends `traceSystem` → Java walks `system_edges`
→ Python highlights the entire sprinkler route in the viewport. At TE scale
(16,362 sprinklers, dense pipe network), this is already queryable.

**Floor navigator (clicking a storey shows its contents):**
User clicks storey in section chooser → Python sends `show_containment` →
Java returns all 48,428/storey element GUIDs → Python shows/hides by
collection. No AABB computation needed — `rel_contained_in_space` is exact.

### 12.5 Traceability

| Verb | Output.db table | BIM_COBOL predicate | BIM Designer SRS req |
|------|----------------|--------------------|--------------------|
| highlight_assembly | assembly_components | — (direct query) | UX-F-07 (section focus) |
| queryHost | assembly_components | HOST_OF (§20) | UX-F-20 (click-to-fix) |
| show_containment | rel_contained_in_space | WITHIN (§20) | UX-F-21 (jurisdiction) |
| queryComposition | material_layers | — (direct query) | §18.4 Principle 4 (layer assembly) |
| traceSystem | system_nodes + system_edges | ALONG_PATH (§20) | §18.3 (MEP coordination) |
| show_neighbours | assembly_components | — (sibling query) | UX-F-26 (multi-view sync) |
| navigate_tree | assembly_components (recursive) | — (tree walk) | UX-F-27 (BOM Outliner) |

---

## 13. Logging Contract — BIMLogger Integration

The Java server uses `orm-core/src/main/java/com/bim/orm/BIMLogger.java` for all
server-side logging. BIMLogger is the cross-module levelled logger (ERROR → WARN →
INFO → FINE → DEBUG) that all modules (IFCtoBOM, DAGCompiler, BIM_COBOL,
BonsaiBIMDesigner) share.

### 13.1 Designer-Specific Log Events

Every UX-observable server action must log at INFO level with the `DESIGNER`
component tag. These log lines are the server-side audit trail — when the user
reports "Save didn't work," the log file tells the story.

| Action | Level | Component | Log format | Req |
|--------|-------|-----------|------------|-----|
| Client connects | INFO | DESIGNER | `Client connected from {}:{}` | UX-F-01 |
| Client disconnects | INFO | DESIGNER | `Client disconnected: {}` | UX-E-02 |
| createNew request | INFO | DESIGNER | `createNew: {} type={} jurisdiction={} rooms={}` | UX-F-02 |
| createNew response | INFO | DESIGNER | `createNew → {} bboxes in {}ms` | UX-N-01 |
| snap request | FINE | DESIGNER | `snap: {} bboxes, jurisdiction={}, grid={}mm` | UX-F-08 |
| snap adjustments | INFO | DESIGNER | `snap → {} adjustments ({} grid, {} compliance)` | UX-N-05 |
| save request | INFO | DESIGNER | `save: building={} label='{}' lines={}` | UX-F-13 |
| save complete | INFO | DESIGNER | `save → variant={} subOrder={} in {}ms` | UX-N-06 |
| recall request | INFO | DESIGNER | `recall: building={} variant={}` | UX-F-15 |
| validate batch | FINE | DESIGNER | `validate: {} lines, jurisdiction={} → {} PASS, {} WARN, {} BLOCK` | UX-F-18 |
| approve request | INFO | DESIGNER | `approve: building={} → {}` (AP or blocked reason) | UX-F-16 |
| promote request | INFO | DESIGNER | `promote: building={} → {} BOM entries` | UX-F-17 |
| browseItems | FINE | DESIGNER | `browseItems: search='{}' category={} → {} results in {}ms` | UX-N-07 |
| server error | ERROR | DESIGNER | `Action {} failed: {}` (exception message, no stack on wire) | UX-E-03 |

### 13.2 Log Initialisation Pattern

```java
// In DesignerServer.start()
BIMLogger.initForRun("designer_server");
BIMLogger.setLevel(BIMLogger.Level.FINE);  // FINE for designer — captures snap/validate detail
BIMLogger.info("DESIGNER", "Server started on port {}", port);
```

Log file: `logs/pipeline_designer_server_{timestamp}.log`

### 13.3 Why This Matters for UX

1. **Latency tracking.** Every action logs elapsed time. If UX-N-01 (createNew < 200ms)
   regresses, the log shows it without needing a profiler.
2. **Connection diagnostics.** UX-E-01/E-02 (server not running, disconnection) are
   diagnosed from the log, not from the user's description.
3. **Save verification.** UX-E-03 (crash during save) — the log shows whether the
   save transaction committed before the crash.
4. **Compliance audit.** Every validate call logs per-rule verdicts at FINE level.
   An auditor can verify compliance from the log alone.

---

## 14. Inference Engine — Symbolic Deduction for Generative Path

### 14.1 Why the Generative Path Needs Formal Reasoning

The Rosetta Stone baseline (SH/DX/TE) proves correctness by comparing compiled
output against externally produced reference IFCs. The BIM Designer's generative
path has **no reference** — the user is creating, not copying. Correctness must
be proven from first principles: BOM rules, spatial predicates, and building
code compliance.

This is the domain of **deterministic symbolic deduction**: given a set of
premises (BOM data, AD_Val_Rule, spatial relationships), derive conclusions
(compliant/non-compliant, valid/invalid placement) with a traceable proof chain.

> **Design decision (2026-03-19):** The inference engine uses the architectural
> pattern of symbolic deduction (Horn clause reasoning, forward-chaining,
> proof traceback) implemented as a lightweight rule engine within the existing
> Java pipeline. Not an external AI system — deterministic, offline, auditable.
> Inspired by AlphaGeometry's Deductive Database (DDAR) architecture but
> purpose-built for BIM/ERP rule composition over the 4-database schema.

### 14.2 Core Capabilities

| Capability | What | Gate | Example |
|---|---|---|---|
| **Rule composition** | Rules have dependencies. If M17 (host association) fails, M16 (face-anchor) is meaningless. The engine evaluates in dependency order, skipping downstream rules when premises fail. | G-6 | `M16_face_anchor :- M17_host_resolved(Door, Wall), face_matches(Door, Wall)` |
| **Forward-chaining** | User edit → propagate consequences → re-evaluate affected rules. Room resize → wall positions change → door positions change → M16/M17 re-evaluate → status strip updates. All within the 300ms UX-N-05 budget. | G-6 | Slider release → snap → propagate → re-validate → status strip |
| **Proof traceback** | When validation blocks (Approve gate, Promote gate), the engine explains WHY with a chain of premises: "M3 FAIL: bedroom width 2950mm < 3000mm minimum (AD_Val_Rule 805, jurisdiction MY, UBBL 2012 §8.3)." | G-10 | Promote blocked → show proof tree → user fixes → re-prove |
| **Predicate composition** | Spatial predicates (BIM_COBOL §20) compose: `HOST_OF(door, wall) ∧ WITHIN(wall, room) → WITHIN(door, room)`. No combinatorial code — predicates compose via inference rules. | G-6, G-7 | `SAME_LEVEL(a, b) :- storey(a, S), storey(b, S)` |
| **Constraint satisfaction** | Assembly builder (G-7) places layers subject to constraints: clearance, host attachment, Z-stacking order. The engine finds valid placements or reports which constraints conflict. | G-7 | Layer placement: `fits(Item, Slot) :- aabb(Item, W,D,H), capacity(Slot, CW,CD,CH), W ≤ CW, D ≤ CD, H ≤ CH` |

### 14.3 Architecture — Rule Engine Within PlacementValidator

PlacementValidatorImpl.java currently evaluates AD_Val_Rule rows as a flat list.
The inference engine evolves it in three stages:

**Stage 1 (G-6): Dependency-ordered evaluation**
- AD_Val_Rule gains `depends_on` column (FK to parent rule ID)
- PlacementValidator topologically sorts rules before evaluation
- Downstream rules SKIP when upstream premise fails
- Proof trace: each result carries the chain of premises that led to it

**Stage 2 (G-7): Spatial predicate integration**
- BIM_COBOL §20 predicates (DISTANCE_BETWEEN, HOST_OF, WITHIN, etc.) become
  callable from AD_Val_Rule.check_method
- Predicates compose: `HOST_OF` upgrades from AABB_PROXIMITY to FK join
  transparently when R21 lands (no rule change, just predicate implementation)
- Predicate results cached per evaluation cycle (200ms sync timer budget)

**Stage 3 (G-10): Full proof tree for Promote gate**
- Promote requires all-rules-pass + dangle detection + tack integrity
- Engine produces a proof tree: root = "PROMOTE_SAFE", children = per-rule proofs
- Each leaf cites: AD_Val_Rule ID, jurisdiction, measured value, threshold, §reference
- Failed proof tree serialised to W_Validation_Result for audit trail

### 14.4 Implementation — Datalog-Style Rules Over SQL

The spatial predicates and validation rules are expressed as Datalog-style
Horn clauses evaluated over the 4-database SQL schema. This matches the
Schema-Not-Geometry principle (BBC.md §2): every predicate is a SQL query,
not geometry code.

```
% Rule M3: bedroom minimum width (jurisdiction-parameterised)
compliant(Room, m3_bedroom_width) :-
    room_category(Room, 'BD'),
    dim_width(Room, W),
    jurisdiction_min(m3_bedroom_width, Jurisdiction, MinW),
    W >= MinW.

% Rule M16: opening face-anchor (depends on M17 host resolution)
compliant(Opening, m16_face_anchor) :-
    host_resolved(Opening, Wall),          % ← M17 must pass first
    forward_axis(Opening, FA),
    wall_normal(Wall, WN),
    perpendicular(FA, WN).

% Spatial predicate composition
within(Element, Room) :-
    host_of(Element, Wall),                % FK: assembly_components
    within(Wall, Room).                    % FK: rel_contained_in_space

% Constraint: new placement must not overlap existing
valid_placement(Item, Slot) :-
    aabb(Item, W, D, H),
    capacity(Slot, CW, CD, CH),
    W =< CW, D =< CD, H =< CH,
    \+ overlaps(Item, Slot).               % negation-as-failure
```

Each rule maps to a SQL query pattern. The engine evaluates them in
topological order (dependency DAG), caches intermediate results, and
builds a proof tree from the evaluation trace.

### 14.5 Non-Functional Requirements

| ID | Requirement | Target | Rationale |
|---|---|---|---|
| INF-N-01 | Full rule evaluation cycle | < 300ms | Must fit within UX-N-05 snap validation budget |
| INF-N-02 | Proof tree serialisation | < 50ms | W_Validation_Result INSERT after evaluation |
| INF-N-03 | Rule count capacity | 200 AD_Val_Rule rows | Current 32 rules × 6 jurisdictions = 192 max |
| INF-N-04 | Predicate cache hit rate | > 90% | Most predicates stable between edits (only changed room recalculated) |
| INF-N-05 | Dependency depth | ≤ 5 levels | M17 → M16 → composite rules. Deeper chains indicate rule design smell |

### 14.6 Traceability

| Gate | What inference engine provides | PlacementValidator evolution |
|---|---|---|
| G-6 Ambient Compliance | Rule composition + forward-chaining + status strip proof | Stage 1: dependency-ordered evaluation |
| G-7 Assembly Builder | Constraint satisfaction for layer placement | Stage 2: spatial predicates as check_method |
| G-10 Promote | All-rules-pass proof tree + dangle detection + traceback | Stage 3: full proof tree for governance gate |
| BIM_COBOL §20 | Composable spatial queries with guaranteed semantics | Stage 2: predicate integration |
| UX-F-20 Click-to-fix | Proof trace identifies which rule failed and why | Stage 1: proof trace in ValidationVerdict |
| UX-F-16 Approve gate | Categorised blockers with clickable list | Stage 3: proof tree → UI blocker list |

### 14.7 Witness Claims

| Witness | Tests | Requirement |
|---|---|---|
| W-INF-DEP-1 | Rule with failed dependency returns SKIPPED, not evaluated | Rule composition (§14.2) |
| W-INF-PROP-1 | Room resize triggers re-evaluation of affected rules only, within 300ms | Forward-chaining (§14.2) |
| W-INF-PROOF-1 | Failed Promote produces proof tree with AD_Val_Rule citations | Proof traceback (§14.2) |
| W-INF-PRED-1 | HOST_OF predicate returns same result via AABB_PROXIMITY and FK join | Predicate upgrade transparency (§14.4) |
| W-INF-CACHE-1 | Second evaluation cycle with no edit returns cached results in < 10ms | INF-N-04 cache hit rate |

---

## 15. Place Action — BOM Chooser → OrderLine (G-5 completion)

// Implementing BIM_Designer.md §17.18.4 — Witness: W-PLACE-*

### 15.1 Wire Protocol

```json
{"action": "placeItem",
 "buildingId": "MyHouse",
 "roomBomId": "ROOM_BD_GF_03",
 "productId": "BED_QUEEN_1600",
 "offsetXMm": 200, "offsetYMm": 200, "offsetZMm": 0}
```

Response:
```json
{"success": true,
 "orderLineId": 42,
 "bbox": {"bomId":"ITEM_42", "name":"BED_QUEEN_1600", "bomType":"ITEM",
          "category":"ELEMENT", "ifcClass":"IfcFurnishingElement",
          "storey":"GF", "parentBomId":"ROOM_BD_GF_03",
          "minX":200, "minY":200, "minZ":0,
          "maxX":1800, "maxY":2300, "maxZ":500},
 "error": null}
```

### 15.2 Java Contract

```
DesignerAPI:
  PlaceItemResponse placeItem(PlaceItemRequest request)

PlaceItemRequest(buildingId, roomBomId, productId, offsetXMm, offsetYMm, offsetZMm)
PlaceItemResponse(success, orderLineId, bbox, error)
```

**Sequence:**
1. Look up product from M_Product (DAO.getProduct) → widthMm, depthMm, heightMm
2. Look up room bbox from scene bboxes (passed in request or from work_output.db)
3. Compute placement: `itemMinX = roomMinX + offsetX`, etc.
4. INSERT C_OrderLine in work_output.db via WorkOutputDAO.placeItem()
5. Return new DesignBBox for the placed item

### 15.3 Witness Claims

| Witness | Tests | Requirement |
|---|---|---|
| W-PLACE-1 | placeItem creates C_OrderLine with correct dx/dy/dz and product ref | UX-F-24 |
| W-PLACE-2 | Placed item bbox positioned at room origin + offset | UX-F-24 |
| W-PLACE-3 | placeItem with non-existent product returns error, no partial write | UX-E-10 |
| W-PLACE-4 | placeItem < 100ms on stub data | UX-N-07 |

---

## 16. Layout Editing — Add Room / Remove Room / Add Storey

// Implementing BIM_Designer.md §17.6, §17.9, §17.15 — Witness: W-LAYOUT-*

### 16.1 Wire Protocol

```json
{"action": "addRoom", "category": "BEDROOM", "storey": "GF"}
{"action": "removeRoom", "roomBomId": "ROOM_BD_GF_03"}
{"action": "addStorey"}
```

All three return updated bboxes:
```json
{"success": true, "bboxes": [...], "roomCount": 7, "error": null}
```

### 16.2 Java Contract

```
DesignerAPI:
  LayoutResponse addRoom(String buildingId, String category, String storey)
  LayoutResponse removeRoom(String buildingId, String roomBomId)
  LayoutResponse addStorey(String buildingId)

LayoutResponse(success, bboxes, roomCount, error)
```

**addRoom sequence:**
1. Parse current bboxes from work_output.db (or scene state)
2. Increment room count for the category
3. Recalculate via RoomLayoutGenerator (re-pack proportional widths)
4. Return all updated bboxes (all rooms shift to accommodate)

**removeRoom sequence:**
1. Mark target room's C_OrderLine as IsActive=0 (soft delete)
2. Recalculate remaining rooms via RoomLayoutGenerator
3. Return updated bboxes (remaining rooms expand)

**addStorey sequence:**
1. Clone GF room layout at Z = existingStoreys × storeyHeight
2. Create new FLOOR bbox + room bboxes
3. Adjust BUILDING bbox height
4. Return all bboxes

### 16.3 Witness Claims

| Witness | Tests | Requirement |
|---|---|---|
| W-LAYOUT-1 | addRoom increases room count and recalculates proportional widths | UX-F-09 |
| W-LAYOUT-2 | removeRoom soft-deletes and recalculates (remaining rooms expand) | UX-F-10 |
| W-LAYOUT-3 | addStorey clones GF layout at correct Z offset | UX-F-11 |
| W-LAYOUT-4 | addRoom to full storey returns error with "expand site" suggestion | UX-E-09 |
| W-LAYOUT-5 | removeRoom → undo → room restored (scene property tracking) | UX-F-28 |
| W-LAYOUT-6 | All layout operations < 200ms | UX-N-01 |

---

## 17. Dimension Sliders + Jurisdiction Switch + Click-to-Fix

// Implementing §17.13, §18.2, §18.4 — Witness: W-SLIDER-*, W-JURIS-*, W-FIX-*

### 17.1 Dimension Sliders (UX-F-08)

**Python-side (no server call during drag):**
- Focus a room → sliders for Width/Depth appear in panel
- Drag slider → bbox resizes locally via scene property update (0ms)
- On mouse release → `snap` action sent to server → adjustments applied

**Panel contract:**
```python
# In panel.py, when active_section is a ROOM:
col.prop(props, "room_width_mm", slider=True)   # FloatProperty, min=100
col.prop(props, "room_depth_mm", slider=True)    # FloatProperty, min=100
# On update callback: resize bbox in scene, dirty flag for snap
```

### 17.2 Jurisdiction Switch (UX-F-21)

**Wire protocol:**
```json
{"action": "setJurisdiction", "jurisdiction": "SG"}
```

Response:
```json
{"success": true, "jurisdiction": "SG", "ruleCount": 3,
 "verdicts": [
   {"bomId":"ROOM_BD_GF_03", "rule":"BCA_BEDROOM_MIN_DIM", "result":"PASS", "actual":3100, "required":2500},
   {"bomId":"ROOM_LI_GF_01", "rule":"BCA_DOOR_MIN_WIDTH", "result":"BLOCK", "actual":750, "required":850}
 ]}
```

**Java contract:**
```
DesignerAPI:
  JurisdictionResponse setJurisdiction(String jurisdiction, List<DesignBBox> bboxes)

JurisdictionResponse(success, jurisdiction, ruleCount, verdicts, error)
```

**Sequence:**
1. PlacementValidator.activate(jurisdiction, valConn)
2. Validate ALL bboxes against new rule set
3. Return per-bbox verdicts for status strip

### 17.3 Click-to-Fix (UX-F-20)

**Python-side:**
- Click a failed rule in status strip → `focus_section(failedBomId)`
- "Auto-fix" button → `snap` with constraint:
  ```json
  {"action": "snap", "bboxes": [...], "jurisdiction": "MY", "gridMm": 250,
   "fixRule": "UBBL_BEDROOM_MIN_DIM", "fixBomId": "ROOM_BD_GF_03"}
  ```
- Server adjusts the specific bbox to meet the rule's minimum

**Java extension to snap():**
- If `fixRule` present: force that bbox's dimension to `requiredValue` (round to grid)
- Return adjustment showing what changed

### 17.4 Witness Claims

| Witness | Tests | Requirement |
|---|---|---|
| W-SLIDER-1 | Room bbox dimensions update in scene without server call | UX-F-08, UX-N-04 |
| W-SLIDER-2 | Slider release triggers snap → adjustments returned | UX-F-08, UX-N-05 |
| W-JURIS-1 | setJurisdiction("SG") loads SG rules and re-validates all rooms | UX-F-21 |
| W-JURIS-2 | Jurisdiction switch completes < 300ms | UX-N-05 |
| W-JURIS-3 | Switching to unsupported jurisdiction returns UNCHECKED verdicts | UX-E-05 |
| W-FIX-1 | snap with fixRule adjusts target bbox to meet rule minimum | UX-F-20 |
| W-FIX-2 | Auto-fix rounds to grid and re-validates remaining rules | UX-F-20 |

---

## 18. Approve Gate + Variant Comparison

// Implementing G4_SRS §3, §17.10.2, §17.10.4 — Witness: W-APPROVE-*, W-COMPARE-*

### 18.1 Approve Gate (UX-F-16)

**Wire protocol:**
```json
{"action": "approve", "buildingId": "MyHouse"}
```

Response (pass):
```json
{"success": true, "status": "AP", "rulesPassed": 12, "rulesTotal": 12,
 "dangles": [], "blockers": []}
```

Response (blocked):
```json
{"success": false, "status": "IP",
 "rulesPassed": 10, "rulesTotal": 12,
 "dangles": ["WINDOW_CUSTOM_1800 not in catalog"],
 "blockers": [
   {"bomId":"ROOM_BD_GF_03", "rule":"UBBL_BEDROOM_MIN_DIM",
    "actual": 2950, "required": 3000, "message": "2950mm < 3000mm (need +50mm)"}
 ]}
```

**Java contract:**
```
DesignerAPI:
  ApproveResponse approve(String buildingId)

ApproveResponse(success, status, rulesPassed, rulesTotal, dangles, blockers, error)
```

**Sequence:**
1. Load all bboxes from work_output.db via recall(latest variant)
2. Run PlacementValidator on each ROOM bbox (ACTIVE mode, not READONLY)
3. Check for dangles: C_OrderLines referencing products not in M_Product
4. If all PASS and no dangles → master C_Order DocStatus: IP → AP
5. If any BLOCK → return blockers list with proof trace

### 18.2 Variant Comparison (new feature)

**Wire protocol:**
```json
{"action": "compareVariants",
 "buildingId": "MyHouse",
 "variantIds": ["v1", "v3"]}
```

Response:
```json
{"success": true,
 "variants": [
   {"variantId":"v1", "label":"default", "roomCount":5, "totalAreaSqM":42.0,
    "compliance":"ALL_PASS", "rulesPassed":12},
   {"variantId":"v3", "label":"SG-compliant", "roomCount":5, "totalAreaSqM":48.5,
    "compliance":"ALL_PASS", "rulesPassed":3}
 ],
 "diffs": [
   {"bomId":"ROOM_BD_GF_03", "field":"widthMm", "v1":3100, "v3":3500}
 ]}
```

### 18.3 Witness Claims

| Witness | Tests | Requirement |
|---|---|---|
| W-APPROVE-1 | approve with all-PASS returns AP status | UX-F-16 |
| W-APPROVE-2 | approve with failed rule returns blockers list with proof trace | UX-F-16 |
| W-APPROVE-3 | approve with dangle returns dangle list | UX-E-10 |
| W-APPROVE-4 | approve blocks unless master order exists | UX-E-11 |
| W-COMPARE-1 | compareVariants returns per-variant stats and field diffs | New |
| W-COMPARE-2 | compareVariants with non-existent variant returns error | New |

---

## 19. Inference Engine Stage 1 — Dependency-Ordered Evaluation

// Implementing §14.2-14.3 Stage 1 — Witness: W-INF-*

### 19.1 Schema Extension

```sql
-- AD_Val_Rule gains dependency column
ALTER TABLE AD_Val_Rule ADD COLUMN depends_on INTEGER
  REFERENCES AD_Val_Rule(AD_Val_Rule_ID);
```

Example: M16 (face-anchor) depends on M17 (host association):
```sql
UPDATE AD_Val_Rule SET depends_on = (SELECT AD_Val_Rule_ID FROM AD_Val_Rule WHERE Name = 'M17_HOST_ASSOC')
  WHERE Name = 'M16_FACE_ANCHOR';
```

### 19.2 Java Contract — InferenceEngine

```java
/**
 * Inference engine for dependency-ordered rule evaluation.
 * Evolves PlacementValidatorImpl from flat iteration to DAG traversal.
 *
 * Stage 1 (G-6): topological sort + SKIP on failed dependency + proof trace
 * Stage 2 (G-7): spatial predicates as check_method callables
 * Stage 3 (G-10): full proof tree serialisation to W_Validation_Result
 */
public class InferenceEngine {
    List<RuleResult> evaluate(List<CachedRule> rules, PlacementRequest request);
    ProofTree buildProofTree(List<RuleResult> results);
}
```

**RuleResult record:**
```java
record RuleResult(
    int ruleId, String ruleName, String standardRef,
    Result result,       // PASS | BLOCK | SKIP
    double actualValue, double requiredValue,
    int dependsOn,       // parent rule ID (0 = root)
    String skipReason    // "dependency M17_HOST_ASSOC failed" or null
) {}
```

**ProofTree record:**
```java
record ProofTree(
    String conclusion,   // "APPROVE_SAFE" or "APPROVE_BLOCKED"
    List<ProofNode> nodes
) {}

record ProofNode(
    int ruleId, String ruleName, Result result,
    String citation,     // "AD_Val_Rule 805, UBBL 2012 §8.3"
    List<ProofNode> children  // downstream rules that depend on this
) {}
```

### 19.3 Algorithm — Topological Sort + Forward-Chaining

```
1. Build DAG from AD_Val_Rule.depends_on edges
2. Topological sort (Kahn's algorithm) → evaluation order
3. For each rule in order:
   a. If depends_on rule FAILED or SKIPPED → result = SKIP
   b. Else: evaluate rule (existing checkCategory logic)
   c. Cache result for downstream dependents
4. Build proof tree from results (parent-child via depends_on)
5. Return all RuleResults + proof tree
```

**Performance budget:** < 300ms for 200 rules (INF-N-01).
Topological sort is O(V+E), evaluation is O(R × P) where R=rules, P=params.
Current 32 rules × 3 params avg = 96 checks — well within budget.

### 19.4 Integration with PlacementValidatorImpl

PlacementValidatorImpl.validate() evolves:

**Before (flat):**
```java
for (CachedRule rule : rules) {
    // check each rule, return first BLOCK
}
```

**After (DAG):**
```java
InferenceEngine engine = new InferenceEngine();
List<RuleResult> results = engine.evaluate(rules, request);
// Return first BLOCK, or collect all for proof tree
```

The change is internal to PlacementValidatorImpl — the PlacementValidator
interface contract does not change. DesignerAPIImpl.snap() and approve()
both call validate() and get richer results.

### 19.5 Witness Claims

| Witness | Tests | Requirement |
|---|---|---|
| W-INF-DEP-1 | Rule with failed dependency returns SKIPPED, not evaluated | §14.2 Rule composition |
| W-INF-TOPO-1 | Rules evaluated in topological order (parent before child) | §14.3 Stage 1 |
| W-INF-PROP-1 | Room resize triggers re-evaluation within 300ms | §14.2 Forward-chaining |
| W-INF-PROOF-1 | Failed approve produces proof tree with AD_Val_Rule citations | §14.2 Proof traceback |
| W-INF-CACHE-1 | Second evaluation with no edit returns cached results < 10ms | INF-N-04 |
| W-INF-CYCLE-1 | Circular depends_on detected and rejected at load time | Robustness |

---

## 20. Updated Traceability — Session 27 Implementation Batch

| Req ID | Spec Section | Java File | Witness | Status |
|--------|-------------|-----------|---------|--------|
| UX-F-08 | §17.1 | — (Python-only slider) | W-SLIDER-1, W-SLIDER-2 | IMPLEMENTED |
| UX-F-09 | §16.1 | DesignerAPIImpl.addRoom | W-LAYOUT-1 | IMPLEMENTED |
| UX-F-10 | §16.2 | DesignerAPIImpl.removeRoom | W-LAYOUT-2 | IMPLEMENTED |
| UX-F-11 | §16.2 | DesignerAPIImpl.addStorey | W-LAYOUT-3 | IMPLEMENTED |
| UX-F-16 | §18.1 | DesignerAPIImpl.approve | W-APPROVE-1..4 | IMPLEMENTED |
| UX-F-20 | §17.3 | DesignerAPIImpl.snap (fixRule) | W-FIX-1, W-FIX-2 | IMPLEMENTED |
| UX-F-21 | §17.2 | DesignerAPIImpl.setJurisdiction | W-JURIS-1..3 | IMPLEMENTED |
| UX-F-22 | §17.18.1 | DesignerDAO.browseProducts | W-BROWSE-1..11 | IMPLEMENTED |
| UX-F-23 | §17.18.3 | DesignerAPIImpl.computeFitStatus | W-BROWSE-7 | IMPLEMENTED |
| UX-F-24 | §15.2 | DesignerAPIImpl.placeItem | W-PLACE-1..4 | IMPLEMENTED |
| §14 Inf | §19.2 | InferenceEngine | W-INF-DEP-1..CYCLE-1 | IMPLEMENTED |

**Summary after implementation:**
- 23 IMPLEMENTED, 5 SPEC ONLY (UX-F-04, 17, 25, 26, 27). Witnesses: 87 total across 10 test classes (87/87 GREEN).

---

## 21. Multi-User Server Architecture

### 21.1 Why a Server

The BIM Compiler is Java. Blender/Bonsai is Python. The TCP server bridges
this language gap — but the architecture yields a larger benefit: **shared
resources across multiple designers.**

```
                    ┌─────────────────────────────────┐
                    │        SHARED (read-only)        │
                    │  component_library.db  (catalog)  │
                    │  validation.db         (rules)    │
                    │  {PREFIX}_BOM.db       (templates) │
                    └────────────┬────────────────────┘
                                 │
                    ┌────────────┴────────────────────┐
                    │     Java DesignerServer          │
                    │     (single JVM, port 9876)      │
                    │                                  │
                    │  Per-session state:               │
                    │    sessionId → PlacementValidator │
                    │    sessionId → jurisdiction       │
                    │    sessionId → workOutputConns    │
                    └──┬──────────┬──────────┬────────┘
                       │          │          │
                  TCP 9876   TCP 9876   TCP 9876
                       │          │          │
                 ┌─────┴──┐ ┌────┴───┐ ┌────┴───┐
                 │ Bonsai │ │ Bonsai │ │ Bonsai │
                 │ User A │ │ User B │ │ User C │
                 │        │ │        │ │        │
                 │ work_  │ │ work_  │ │ work_  │
                 │ output │ │ output │ │ output │
                 │ _A.db  │ │ _A.db  │ │ _B.db  │
                 └────────┘ └────────┘ └────────┘
```

### 21.2 Shared vs Per-User Resources

| Resource | Scope | Access | Write path |
|----------|-------|--------|-----------|
| `component_library.db` | **Shared** (all users) | Read-only during design | Extraction pipeline (offline) |
| `validation.db` | **Shared** (all jurisdictions) | Read-only | Migration SQL (admin) |
| `{PREFIX}_BOM.db` | **Shared** (curated templates) | Read-only during design | **Promote gate** (governance-controlled) |
| `work_output_{building}.db` | **Per-building** | Read-write per session | Save/Recall/placeItem |
| `PlacementValidator` state | **Per-session** | In-memory | setJurisdiction per user |

This is the iDempiere pattern:
- `M_Product` = shared catalog (component_library.db)
- `AD_Val_Rule` = shared validation (validation.db)
- `M_BOM` = shared recipes (BOM.db)
- `C_Order` = per-user work orders (work_output.db)

### 21.3 Session Isolation (Stage 1 — Current)

The current implementation uses `ConcurrentHashMap<String, Connection>` for
per-building work_output.db connections. This is sufficient for single-user
but needs extension for multi-user:

**Stage 1 (current):** Single user. One PlacementValidator, one jurisdiction.
All methods share state. Functional for development and demo.

**Stage 2 (beta):** Session handshake. Client sends `{"action":"connect",
"sessionId":"user-A", "jurisdiction":"MY"}`. Server creates per-session state
(validator, jurisdiction, building context). Existing wire protocol unchanged —
sessionId added as header field.

**Stage 3 (production):** Authentication + authorization. Session → user →
role (architect/engineer/QS). Promote gate requires specific role. Audit
trail in W_Validation_Result links to user. Standard iDempiere AD_User
pattern.

### 21.4 Concurrency Model

| Operation | Concurrency | Safety |
|-----------|-------------|--------|
| browseItems, listBuildings, listCategories | **Concurrent** — read-only on shared DBs | Safe (SQLite WAL mode) |
| snap, setJurisdiction | **Per-session** — reads shared rules, no writes | Safe (validator cached in session) |
| save, recall, placeItem | **Per-building** — writes to work_output.db | Safe (SQLite transaction per save, one writer per building) |
| approve, promote | **Serialised** — writes to shared BOM.db | Needs mutex on BOM.db writes (Promote is rare, governance-gated) |
| compile | **Per-building** — reads BOM.db, writes output.db | Safe (output.db is per-building) |

### 21.5 Collaborative Design Scenario

```
User A (architect):                 User B (engineer):
─────────────────                   ─────────────────
createNew "House_1"                 browseItems "sprinkler"
  → bboxes → Design Mode             → 12 results
addRoom BEDROOM                     (waits for A to save)
save "v1"
                                    recall "v1" of House_1
                                    placeItem SPRINKLER_HEAD
                                    save "v2-mep"
snap (MY jurisdiction)
  → sees A's rooms + B's sprinklers
approve
  → InferenceEngine: all PASS
promote
  → House_1 enters shared BOM catalog
```

Both users work on the same building (`House_1`), each saving their own
variants. The server serialises writes to the same work_output.db. Promote
is a governance gate — only one user (with role) can execute it.

### 21.6 Witness Claims

| Witness | Tests | Requirement |
|---|---|---|
| W-MULTI-1 | Two concurrent browseItems return correct results | §21.4 read concurrency |
| W-MULTI-2 | Two concurrent saves to different buildings succeed | §21.4 per-building isolation |
| W-MULTI-3 | Session handshake returns sessionId and loads jurisdiction | §21.3 Stage 2 |
| W-MULTI-4 | Promote serialises (second promote waits, no corruption) | §21.4 serialised writes |

---

## 22. Compile Bridge — Wire to Real Pipeline

### 22.1 The Gap

`DesignerAPIImpl.compile()` is a stub. It returns fake element counts without
running the real 9-stage compilation pipeline. This is the single most
impactful missing connection — it would turn design bboxes into actual 3D
buildings in the Blender viewport.

### 22.2 Wire Protocol (unchanged)

```json
{"action": "compile",
 "buildingId": "MyHouse",
 "bomDbPath": "library/DM_BOM.db",
 "libraryPath": "library/component_library.db",
 "outputDir": "DAGCompiler/lib/output/"}
```

Response:
```json
{"success": true, "elementCount": 19, "elapsedMs": 847,
 "outputDbPath": "DAGCompiler/lib/output/myhouse.db",
 "spatialDigest": "abc123..."}
```

### 22.3 Java Contract — The One-Line Fix

```java
// Current (stub):
return CompileResponse.success(bt.expectedElements(), elapsed,
    request.outputDir() + bt.projectName().toLowerCase() + ".db",
    "stub-digest-" + bt.projectName());

// Target:
CompilationPipeline pipeline = new CompilationPipeline();
CompilationResult result = pipeline.run(
    request.bomDbPath(), request.libraryPath(), request.outputDir());
return CompileResponse.success(result.elementCount(), result.elapsedMs(),
    result.outputDbPath(), result.spatialDigest());
```

### 22.4 Generative Compile Path

For generative buildings (Create New → design → compile), the sequence is:

```
1. createNew → bboxes (in-memory, no DB)
2. User edits: addRoom, placeItem, resize, snap
3. save → work_output.db (C_Order + C_OrderLine)
4. approve → InferenceEngine validates, all PASS → AP
5. promote → writes m_bom + m_bom_line to {PREFIX}_BOM.db   ← G-10
6. compile → CompilationPipeline reads BOM.db → output.db   ← THIS
7. Federation Full Load → output.db → Blender 3D viewport   ← G-8
```

Steps 5 and 7 are stubs. Step 6 is the compile bridge (this section).
The generative path needs Promote (step 5) to produce a BOM.db that
the compiler can read. For beta, we can short-circuit: compile directly
from work_output.db C_OrderLine data without requiring full Promote.

### 22.5 Short-Circuit Compile (Beta Path)

For beta, skip Promote. Compile directly from work_output.db:

```java
// Beta compile: read C_OrderLine from work_output.db,
// build temporary m_bom/m_bom_line in memory,
// feed to CompilationPipeline.
WorkOutputDAO woDao = getWorkOutputDAO(buildingId);
List<DesignBBox> bboxes = woDao.recall(latestVariantId);
// Convert bboxes to m_bom_line format
// Run pipeline
```

This lets users see 3D geometry without the full Promote governance flow.

### 22.6 Implementation Status — DONE (session 29)

**Java wiring:**
- `DesignerAPIImpl.compile()` — replaced stub with real `CompilationPipeline.run(entry)`.
  Loads `BuildingEntry` directly from `bomDbPath` (bypasses `BuildingRegistry` static cache).
  Falls back to DAO-based stub when `bomDbPath` doesn't exist (test mode).
- `MetadataValidator.DB_PATH` — changed from `static final` to dynamic `dbPath()` method.
  Now reads `System.getProperty("bom.db")` at call time, enabling multi-DB compilation.
  Global check cache invalidated per DB path (`globalCheckedPath` tracking).

**Python UI:**
- `panel.py` — "Compile to 3D" button added to Design Mode action bar (§22 bridge).
  Shows element count + compile time on success.
- `operator.py` — Compile operator auto-detects building context from Design/Real mode.
  Real Mode: reads A.2 selector. Design Mode: falls back to `building_name` + `output_db_path`.

### 22.7 Witness Claims

| Witness | Tests | Requirement | Status |
|---|---|---|---|
| W-COMPILE-1 | compile() with SH_BOM.db produces output.db with 55 elements | Pipeline wiring | PASS |
| W-COMPILE-2 | compile() returns outputDbPath that exists on disk | File creation | PASS |
| W-COMPILE-3 | compile() returns spatialDigest matching G3-DIGEST format (SHA-256) | Tamper seal | PASS |
| W-COMPILE-4 | compile() for SH completes in < 3s (549ms actual) | UX-N-09 | PASS |
| W-COMPILE-5 | compile() with unknown buildingId returns failure | Error handling | PASS |
| W-COMPILE-BETA-1 | Short-circuit compile from work_output.db produces geometry | Beta path | SPEC ONLY |

---

## 23. Standalone Server Launcher

### 23.1 Current State

The server is started only by test classes (`DesignerServer.startAsync()` in
`DesignerServerTest.setUp()`). No `main()` method exists. Users cannot start
the server without Maven test infrastructure.

### 23.2 Launcher Spec

```java
// In DesignerServer.java or new DesignerMain.java:
public static void main(String[] args) {
    int port = args.length > 0 ? Integer.parseInt(args[0]) : 9876;
    String libraryPath = args.length > 1 ? args[1] : "library/component_library.db";
    String valPath = args.length > 2 ? args[2] : "library/validation.db";

    Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + libraryPath);
    PlacementValidatorImpl validator = new PlacementValidatorImpl();
    Connection valConn = DriverManager.getConnection("jdbc:sqlite:" + valPath);
    validator.activate("MY", valConn);

    DesignerAPIImpl api = new DesignerAPIImpl(bomConn, validator);
    DesignerServer server = new DesignerServer(api, port);

    Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    System.out.println("BIM Designer Server listening on port " + port);
    server.start();  // blocks
}
```

**Launch commands:**
```bash
# Option 1: Maven exec
mvn exec:java -pl BonsaiBIMDesigner \
  -Dexec.mainClass="com.bim.designer.api.DesignerServer"

# Option 2: Fat jar (future)
java -jar bim-designer-server.jar 9876

# Option 3: Script
./scripts/start_designer_server.sh
```

### 23.3 Witness Claims

| Witness | Tests | Requirement |
|---|---|---|
| W-LAUNCH-1 | Server starts with default args and accepts TCP connections | Standalone launch |
| W-LAUNCH-2 | Server loads validation.db rules on startup | Auto-configuration |
| W-LAUNCH-3 | Ctrl+C gracefully shuts down server (shutdown hook) | Clean exit |

---

## 24. Blender Integration Test Plan

### 24.1 Purpose

All 87 tests are Java unit tests with in-memory SQLite. The Python addon
has never been tested inside Blender. This section defines the visual
verification protocol.

### 24.2 Test Matrix

| Step | Action | Expected Visual | Screenshot |
|------|--------|-----------------|-----------|
| 1 | Enable Federation + BIM Designer addons | Panel A appears under BIM_PT_tabs | `test_01_panel.png` |
| 2 | Click Connect (server running) | "Connected" status | `test_02_connect.png` |
| 3 | Fill Create New defaults, click Generate | Coloured bboxes in viewport | `test_03_create.png` |
| 4 | Click a room card | Focused bbox vivid, others grey | `test_04_focus.png` |
| 5 | Drag width slider, click Apply | Bbox resizes | `test_05_slider.png` |
| 6 | Click Snap | Status strip shows compliance | `test_06_snap.png` |
| 7 | Type "bed" in BOM Chooser search | Results with fit status | `test_07_browse.png` |
| 8 | Click Place on a bed item | New bbox in room | `test_08_place.png` |
| 9 | Click +Bed (Add Room) | Layout recalculates | `test_09_addroom.png` |
| 10 | Click Add Storey | Second floor appears | `test_10_storey.png` |
| 11 | Click jurisdiction SG button | Status strip re-evaluates | `test_11_jurisdiction.png` |
| 12 | Click Fix on a failed rule | Bbox expands to minimum | `test_12_fix.png` |
| 13 | Click Save | Pulse animation | `test_13_save.png` |
| 14 | Toggle REAL mode | Bboxes disappear | `test_14_real.png` |
| 15 | Toggle DESIGN mode | Bboxes restored | `test_15_restore.png` |

Screenshots saved to `~/Pictures/Screenshots/` per CLAUDE.md protocol.

### 24.3 Known Risks

| Risk | Mitigation |
|------|-----------|
| `design_bbox.py` GPU calls fail | Blender version mismatch. Test on 3.6+ and 4.0+. |
| `BIM_PT_tabs` parent not found | Federation addon must be enabled first. Add check in `__init__.py`. |
| bpy.props annotations fail | Python version mismatch. Blender bundles its own Python. |
| TCP connection timeout | Server must be running before Connect. Add auto-retry (UX-E-01). |

### 24.4 Acceptance Criteria

All 15 screenshots match expected visuals. No Python tracebacks in Blender
console. Panel renders within 1 second of each action. Status strip updates
within 300ms of Snap.

---

## 25. Embedding-Assisted Inference — JEPA-Inspired Architecture

> **Inspiration:** Yann LeCun's VL-JEPA (Joint Embedding Predictive Architecture
> for Vision-Language, 2026). Core insight: predict in **embedding space**, not
> in raw data space. Learn abstract representations; match and retrieve via
> embedding similarity rather than exact query.
>
> **Design decision (2026-03-19):** The compiler pipeline remains deterministic
> and symbolic (§14 Inference Engine unchanged). Embeddings augment the
> **BOM Chooser** and **Inference Engine** with semantic similarity search,
> not replace rule evaluation. The symbolic engine is the authority;
> embeddings are the suggestion engine.

### 25.1 Architectural Alignment

The BIM compiler already operates in a JEPA-compatible pattern:

| JEPA Concept | BIM Compiler Equivalent | Where |
|---|---|---|
| **Predict in embedding space** | Compile from intent (BOM), not geometry | BBC.md §2, verb grammar |
| **Joint embedding** | Bridge IFC geometry + BOM semantics + ERP data + building code | 4-database schema |
| **Selective decoding** | Defer to concrete IFC only at final compile step | Tack-fix pipeline, deferred resolution |
| **Target embedding prediction** | Given partial design, predict next likely component | InferenceEngine forward-chaining (§14.2) |

The gap: the current system uses **exact SQL queries** for component lookup
(§2.5 BOM Chooser). Embeddings add a semantic similarity layer on top.

### 25.2 Component Embedding Space

Each M_Product row in `component_library.db` gets a pre-computed embedding
vector encoding its semantic properties:

**Embedding dimensions (input features):**
```
[category, subcategory, material_class, width_mm, depth_mm, height_mm,
 wet_area_flag, load_bearing_flag, exterior_flag, jurisdiction_set,
 typical_host_category, assembly_role, cost_tier]
```

**Storage:** New column on M_Product or a companion table:
```sql
ALTER TABLE M_Product ADD COLUMN embedding BLOB;  -- float32[] serialised
```

**Computation:** Offline batch job. No neural network required for Stage 1 —
a deterministic feature vector from existing columns suffices. Neural
embeddings (Stage 2) can be trained later from user placement histories.

### 25.3 Semantic BOM Chooser — "Find Similar"

Current BOM Chooser (UX-F-22) searches by keyword and category. Embedding
similarity adds a new interaction:

| ID | Requirement | Acceptance Criteria | Priority |
|---|---|---|---|
| UX-F-29 | **Semantic search.** BOM Chooser accepts natural-language queries ("something like this wall but for a wet area") and returns ranked results by embedding distance. | Top-5 results by cosine similarity returned within 200ms. Results show similarity score badge alongside FITS/TIGHT/TOO WIDE. | P2 |
| UX-F-30 | **"More like this."** Selecting an item offers "Find Similar" which queries by that item's embedding vector. | Results ranked by cosine distance to selected item's embedding. Excludes exact match. | P2 |
| UX-F-31 | **Context-aware ranking.** Search results boosted by compatibility with current design context (room category, jurisdiction, existing placements). | Context vector = mean of placed items' embeddings. Results re-ranked by distance to context centroid. | P2 |

**Algorithm:**
```
1. User types query OR clicks "Find Similar" on existing item
2. Query → embedding (keyword: TF-IDF over product descriptions;
   "Find Similar": copy item's embedding vector)
3. Cosine similarity against all M_Product embeddings
4. Filter by FITS/TIGHT/TOO WIDE (existing §17.18.3 logic)
5. Re-rank by context affinity (mean embedding of placed items)
6. Return top-N with similarity + fit badges
```

### 25.4 Predictive Placement — "What Goes Here Next?"

JEPA's core capability: given context, predict the target embedding. Applied
to the BIM Designer, this becomes **predictive component suggestion**.

| ID | Requirement | Acceptance Criteria | Priority |
|---|---|---|---|
| UX-F-32 | **Next-component suggestion.** When a room is focused and empty, the Inference Engine suggests likely components based on room category + jurisdiction + building type. | Suggestion list appears in BOM Chooser sidebar. Items ranked by embedding prediction. "Place Suggested" one-click action. | P2 |
| UX-F-33 | **Learned placement patterns.** After Stage 2 embeddings (trained on user placement histories), suggestions reflect actual usage patterns, not just category rules. | Precision@5 > 80% on held-out placement sequences from completed designs. | P2 (Stage 2) |

**Integration with Inference Engine (§14):**
```
InferenceEngine.evaluate()
  ├── Stage 1: rule dependency DAG (deterministic, authoritative)
  ├── Stage 2: spatial predicates (deterministic)
  └── Stage 3+: embedding prediction (suggestive, non-blocking)
       ├── Input: current design state → context embedding
       ├── Predict: target component embedding
       └── Match: nearest neighbours in component_library
```

The embedding predictor is **advisory only** — it suggests, the symbolic
engine validates. A suggested component still must pass all AD_Val_Rule
checks before placement is accepted.

### 25.5 Joint Embedding Across the 4-Database Schema

VL-JEPA bridges vision and language modalities. The BIM compiler bridges
five data modalities. A shared embedding space enables cross-modal queries:

| Query | Modalities Bridged | Example |
|---|---|---|
| "What BOM line corresponds to this IFC element?" | IFC ↔ BOM | R4/R5 gap resolution |
| "Which products meet this jurisdiction's rules?" | Product catalog ↔ AD_Val_Rule | Compliance-filtered browse |
| "What did similar designs use here?" | Design history ↔ Product catalog | Predictive placement |
| "Which ERP order matches this design intent?" | work_output.db ↔ BOM.db | Promote verification |

Stage 1 uses deterministic feature vectors. Stage 2 trains a lightweight
encoder (MLP or shallow transformer) on the cross-modal alignment task:
given (IFC element, BOM line, OrderLine) triples from Rosetta Stone
buildings, learn an embedding where matched triples are close.

### 25.6 Non-Functional Requirements

| ID | Requirement | Target | Rationale |
|---|---|---|---|
| EMB-N-01 | Embedding computation (per product) | < 1ms | Offline batch; no runtime cost |
| EMB-N-02 | Similarity search (full library) | < 200ms | Brute-force cosine over ~500 products; ANN index if > 5000 |
| EMB-N-03 | Context embedding update | < 50ms | Mean of placed items; incremental on place/remove |
| EMB-N-04 | Embedding dimensionality | 32-64 dims | Sufficient for ~500 products; avoids curse of dimensionality |
| EMB-N-05 | Storage overhead | < 1KB per product | 64 × float32 = 256 bytes + index |

### 25.7 Implementation Stages

| Stage | Gate | What | Deterministic? |
|---|---|---|---|
| **Stage 1: Feature vectors** | G-8 | Compute embeddings from existing M_Product columns. Enable "Find Similar" in BOM Chooser. | Yes — no ML, pure feature engineering |
| **Stage 2: Learned embeddings** | G-11 | Train encoder on Rosetta Stone (IFC, BOM, OrderLine) triples. Enable predictive placement. | No — requires training data + MLP |
| **Stage 3: Cross-modal retrieval** | G-12 | Joint embedding across all 5 databases. Natural-language queries over the full schema. | No — requires VL-JEPA-style encoder |

### 25.8 Witness Claims

| Witness | Tests | Requirement |
|---|---|---|
| W-EMB-SIM-1 | "Find Similar" on a bedroom door returns other doors, not walls | Embedding quality (§25.3) |
| W-EMB-FIT-1 | Similarity results still carry correct FITS/TIGHT/TOO WIDE badges | Integration with §17.18.3 |
| W-EMB-CTX-1 | Context-aware ranking boosts wet-area products when focused room is bathroom | Context affinity (§25.3) |
| W-EMB-PRED-1 | Empty bedroom suggestion list includes bed, wardrobe, window — not toilet | Predictive placement (§25.4) |
| W-EMB-PERF-1 | Full similarity search completes within 200ms for 500-product library | EMB-N-02 |
| W-EMB-DET-1 | Stage 1 embeddings are deterministic: same product → same vector across runs | Reproducibility |

### 25.9 Traceability

| Req ID | Spec Section | Java File | Witness | Status |
|---|---|---|---|---|
| UX-F-29 | §25.3 | DesignerDAO (embedding query) | W-EMB-SIM-1 | SPEC ONLY |
| UX-F-30 | §25.3 | DesignerDAO (find similar) | W-EMB-SIM-1 | SPEC ONLY |
| UX-F-31 | §25.3 | InferenceEngine (context embedding) | W-EMB-CTX-1 | SPEC ONLY |
| UX-F-32 | §25.4 | InferenceEngine (predict next) | W-EMB-PRED-1 | SPEC ONLY |
| UX-F-33 | §25.4 | InferenceEngine (learned patterns) | W-EMB-PRED-1 | SPEC ONLY (Stage 2) |

---

## 26. Wireframe-First Interaction Protocol (WF-BB)

**Version:** 1.0 (2026-03-20)
**Depends on:** §6 State Machine, §17 Design Mode, `design_bbox.py`, `bbox_visualization.py`

### 26.1 Core Principle

> **BBox is the interaction mode. Full geometry is the settled state.**

Every item enters the viewport as a lightweight wireframe bounding box. Full LOD
geometry loads only when the user commits. This is not a fallback — it is the
primary working mode. Performance is built into the UX, not fought against.

### 26.2 Three Rules

| Rule | Trigger | Visual Result |
|------|---------|---------------|
| **WF-R1: First appearance** | First load of BOM, Create New, or Add Room/Storey | Item appears as wireframe bbox (GPU overlay, no Blender objects). Entire layout is bbox wireframe. Category-coloured per `DESIGN_COLORS`. |
| **WF-R2: Edit existing** | Full geometry loaded + user toggles Design Mode + selects item | Selected item → vivid bbox overlay + full-opacity SOLID `display_type`. Unselected items → `display_type = 'BOUNDS'` (native Blender bbox, grey). |
| **WF-R3: Add while editing** | Add Room / Add Storey while in Design Mode with existing geometry | New item appears as bbox wireframe (Rule 1). Existing committed geometry stays as BOUNDS. |

Rules are consistent: bbox = "working on it", full geometry = "settled".

### 26.3 Phase 1 — Initial Load (WF-R1)

No full geometry exists. Everything is GPU overlay wireframe bbox.

**Entry:** `createNew()` response, `addRoom()`, `addStorey()`, first building load.
**Visual:** All bboxes rendered via `design_bbox.py`. Category colours (§17.8).
Focused section vivid, rest grey (existing `focus_section()` behaviour).
**Exit:** CO/Save → Compile to 3D → full geometry loads → transition to Real Mode (S0).
**Latency contract:** Bboxes appear within 1 frame (16ms) of server response (UX-F-03).

This phase is unchanged from current implementation.

### 26.4 Phase 2 — Edit Existing Geometry (WF-R2)

Full geometry exists in scene. User re-enters Design Mode to make changes.

**Entry:** User toggles Design Mode (S0 → S1).
**On toggle Design Mode:**
1. Iterate all objects in Designer collections
2. Store original `display_type` per object (for restore on exit)
3. Set all objects to `display_type = 'BOUNDS'` (native Blender per-object bounding box, grey)
4. No global X-ray — per-object BOUNDS gives spatial context without uniform dimming

**On select/focus (S1 → S2):**
1. Set focused object to `display_type = 'SOLID'` (full geometry visible for this item)
2. Set `obj.show_in_front = True` (draws on top, no depth-sorting confusion)
3. Draw vivid bbox overlay via `design_bbox.py` around the focused item only
4. All other objects remain `display_type = 'BOUNDS'` (grey bboxes)

**On deselect / change focus:**
1. Previous focused object → back to `display_type = 'BOUNDS'`
2. Previous `show_in_front = False`
3. New focused object gets SOLID + show_in_front + vivid bbox

**On exit Design Mode (→ S0):**
1. Restore all objects to original `display_type` (SOLID/TEXTURED)
2. Clear all `show_in_front` flags
3. Disable bbox overlay (`design_bbox.disable()`)

**Key advantage over global X-ray:** Selected item is full-opacity SOLID with vivid
bbox cage. Unselected items are native Blender BOUNDS (grey boxes with spatial
proportions). The contrast ratio is high — user instantly knows what they're editing.

### 26.5 Phase 2 + Add New (WF-R3)

User is in Design Mode with existing geometry (Phase 2) and adds a new item.

**On Add Room / Add Storey:**
1. New item appears as GPU overlay bbox (Phase 1 style) — vivid category colour
2. Existing committed objects remain `display_type = 'BOUNDS'`
3. New item is auto-focused (vivid bbox, no SOLID geometry — it has none yet)
4. On CO → new item compiles to 3D → joins the BOUNDS pool

**Visual distinction:** New items are GPU overlay wireframe (thin lines, category
colour). Existing items are native Blender BOUNDS (thicker, grey, filled faces on
some viewport modes). The user can tell draft from committed at a glance.

### 26.6 Double-Click Inspection (Peek Mode)

**Trigger:** Double-click-and-hold on a focused bbox (Phase 1 or Phase 2).
**Visual:**
1. Properties popup appears near the bbox showing item data from DB/BOM:
   - Product ID, Name, Category
   - Dimensions (W × D × H mm)
   - Material, Construction System
   - Compliance status (PASS/FAIL per rule)
   - BOM line reference
2. Orientation markers drawn on the bbox — three axis arrows at bbox centre:
   - **Red** arrow → Right (+X)
   - **Green** arrow → Front (+Y)
   - **Blue** arrow → Up (+Z)
   - Arrow length = 30% of bbox extent in that axis
3. GPU overlay only — no Blender objects created

**On release:** Popup and markers dismiss. Returns to normal focus state.
**Latency:** Properties read from scene `_design_bboxes` JSON (already loaded).
Orientation markers are 6 vertices (3 line segments). Total cost: zero DB queries, <1ms render.

### 26.7 Implementation — Per-Object Display Control

```python
# Phase 2: Toggle Design Mode — set all to BOUNDS
def _enter_design_phase2(context):
    """Switch existing geometry to BOUNDS display for Design Mode."""
    designer_objects = _get_designer_objects(context)
    for obj in designer_objects:
        obj["_wf_original_display"] = obj.display_type  # store for restore
        obj.display_type = 'BOUNDS'
        obj.display_bounds_type = 'BOX'
        obj.color = (0.4, 0.4, 0.4, 0.3)  # grey, semi-transparent

# Phase 2: Focus item — promote to SOLID
def _focus_phase2(obj):
    """Show focused item as full SOLID with bbox overlay."""
    obj.display_type = 'SOLID'
    obj.show_in_front = True

# Phase 2: Unfocus — demote back to BOUNDS
def _unfocus_phase2(obj):
    """Return item to BOUNDS display."""
    obj.display_type = 'BOUNDS'
    obj.show_in_front = False

# Exit Design Mode — restore all
def _exit_design_phase2(context):
    """Restore all objects to original display type."""
    designer_objects = _get_designer_objects(context)
    for obj in designer_objects:
        original = obj.get("_wf_original_display", 'SOLID')
        obj.display_type = original
        obj.show_in_front = False
        if "_wf_original_display" in obj:
            del obj["_wf_original_display"]
```

### 26.8 State Machine Update

The WF-BB protocol adds sub-states to the existing state machine (§6):

```
S0 (REAL) ──[toggle Design]──→ S1a or S1b

  S1a (DESIGN_CANVAS_PHASE1)     ← no full geometry exists
  │   All GPU overlay bboxes. Current behaviour.
  │   ──[focus]──→ S2a (vivid bbox, rest grey)

  S1b (DESIGN_CANVAS_PHASE2)     ← full geometry exists
  │   All objects → BOUNDS. No bbox overlay yet.
  │   ──[focus]──→ S2b (focused SOLID + bbox overlay, rest BOUNDS)
  │   ──[Add Room]──→ new item enters as S2a bbox, rest stays BOUNDS

S2b ──[double-click hold]──→ S2b-PEEK (props popup + orientation markers)
S2b-PEEK ──[release]──→ S2b
```

Phase detection: `has_full_geometry = any(obj.type == 'MESH' for obj in designer_objects)`

### 26.9 Requirements

| ID | Requirement | Acceptance Criteria | Priority |
|----|------------|-------------------|----------|
| WF-01 | First load always appears as wireframe bbox | `createNew()`, `addRoom()`, `addStorey()` render bboxes via GPU overlay within 16ms | P0 |
| WF-02 | Design Mode with existing geometry uses per-object BOUNDS | All Designer objects switch to `display_type = 'BOUNDS'` on Design toggle. No global X-ray. | P0 |
| WF-03 | Focused item shows as SOLID + vivid bbox | `display_type = 'SOLID'`, `show_in_front = True`, bbox overlay in category colour | P0 |
| WF-04 | Unfocused items show as grey BOUNDS | `display_type = 'BOUNDS'`, `color = (0.4, 0.4, 0.4, 0.3)` | P0 |
| WF-05 | Exit Design Mode restores original display | All objects return to stored `_wf_original_display`. Zero leftover state. | P0 |
| WF-06 | New items in Phase 2 appear as bbox | Add Room while geometry exists → GPU overlay bbox for new item, BOUNDS for rest | P1 |
| WF-07 | Double-click-hold shows properties popup | BOM data + orientation markers (RGB axes) appear on hold, dismiss on release | P1 |
| WF-08 | Orientation markers use standard RGB axes | Red=+X (Right), Green=+Y (Front), Blue=+Z (Up). Length=30% of bbox extent. | P1 |
| WF-09 | Phase detection is automatic | System detects Phase 1 vs Phase 2 by presence of mesh objects in Designer collections | P0 |
| WF-10 | BOUNDS display is instant | Per-object `display_type` switch completes in <50ms for 100 objects | P0 |

### 26.10 Witnesses

| Witness | Tests | Requirement |
|---|---|---|
| W-WF-PHASE1 | Create New → bboxes appear, no mesh objects in scene | WF-01 |
| W-WF-BOUNDS | Toggle Design with loaded geometry → all objects `display_type == 'BOUNDS'` | WF-02, WF-04 |
| W-WF-FOCUS | Focus item in Phase 2 → focused is SOLID + show_in_front, rest BOUNDS | WF-03 |
| W-WF-RESTORE | Exit Design → all objects back to original display_type, no show_in_front | WF-05 |
| W-WF-ADD | Add Room in Phase 2 → new item is GPU bbox, existing stay BOUNDS | WF-06 |
| W-WF-PEEK | Double-click-hold → properties popup visible + 3 axis markers drawn | WF-07, WF-08 |
| W-WF-DETECT | Phase detection: empty scene → Phase 1, scene with mesh → Phase 2 | WF-09 |

### 26.11 Traceability

| Req ID | Spec Section | Python File | Witness | Status |
|---|---|---|---|---|
| WF-01 | §26.3 | design_bbox.py | W-WF-PHASE1 | DONE (existing) |
| WF-02 | §26.4 | operator.py (toggle_mode) | W-WF-BOUNDS | CODE DONE — needs Blender test |
| WF-03 | §26.4 | design_bbox.py + operator.py | W-WF-FOCUS | CODE DONE — needs Blender test |
| WF-04 | §26.4 | operator.py | W-WF-BOUNDS | CODE DONE — needs Blender test |
| WF-05 | §26.4 | operator.py (toggle_mode) | W-WF-RESTORE | CODE DONE — needs Blender test |
| WF-06 | §26.5 | operator.py (add_room) | W-WF-ADD | SPEC ONLY |
| WF-07 | §26.6 | design_bbox.py (enter_peek) | W-WF-PEEK | CODE DONE — needs Blender test |
| WF-08 | §26.6 | design_bbox.py (_draw_phase2_overlay) | W-WF-PEEK | CODE DONE — needs Blender test |
| WF-09 | §26.8 | operator.py (has_full_geometry) | W-WF-DETECT | CODE DONE — needs Blender test |
| WF-10 | §26.4 | operator.py | W-WF-BOUNDS | CODE DONE — needs Blender test |
| WF-11 | §26.12 | clash/visualization.py + design_bbox.py | W-WF-CHAIN | STUB (getChain verb) |
| WF-12 | §26.12 | design_bbox.py | W-WF-GHOST | SPEC ONLY |
| WF-13 | §26.12 | operator.py | W-WF-DRAG-COMMIT | STUB (moveChain verb) |
| WF-14 | §26.12.3 | design_bbox.py + DesignerServer | W-WF-COST | STUB (costOfChange verb) |
| WF-15 | §26.12.3 | design_bbox.py | W-WF-COST | SPEC ONLY |
| WF-16 | §26.12.3 | DesignerServer (costOfChange verb) | W-WF-COST-CANCEL | STUB (returns zero delta) |
| WF-17 | §26.13 | DesignerServer (moveChain → R_Request) | W-WF-CR-SPAWN | SPEC ONLY |
| WF-18 | §26.13 | DesignerServer (discipline assignment) | W-WF-CR-IMPACT | SPEC ONLY |
| WF-19 | §26.13 | DesignerServer (CR attachments) | W-WF-CR-FILES | SPEC ONLY |
| WF-20 | §26.13 | DesignerServer (CR reject → revert) | W-WF-CR-REVERT | SPEC ONLY |
| WF-21 | §26.14 | DAO layer (changelog interceptor) | W-WF-LOG | SPEC ONLY |
| WF-22 | §26.14 | DesignerServer (undoChanges) | W-WF-UNDO | SPEC ONLY |
| WF-23 | §26.14 | DesignerServer (conflict detection) | W-WF-CONFLICT | SPEC ONLY |
| WF-24 | §26.14 | DesignerServer (AD_Session) | W-WF-SESSION | SPEC ONLY |
| WF-25 | §26.14 | DesignerServer (CR↔changelog link) | W-WF-CR-LOG | SPEC ONLY |

### 26.12 Clash-Aware Chain Highlight + Ghost Drag (Future: Federation Bridge)

When WF-BB is used during clash resolution or conduit routing, two additional
behaviours activate. These extend the same bbox-first principle to connected
element chains.

#### 26.12.1 Chain Highlight — "Select One, See the Whole Run"

**Trigger:** User selects an item that has adjacency/connectivity data (conduit
segment, pipe run, duct branch, cable tray span).

**Behaviour:**
1. Selected item → vivid bbox overlay (standard WF-R2 focus)
2. Query adjacency graph for all items connected to the selected item
   (same conduit run, same pipe circuit, same cable tray path)
3. All connected items → tagged with a **single chain colour** (distinct from
   both the vivid focus colour and the grey BOUNDS)
4. Chain colour is per-run — if two conduit runs cross, each gets its own colour
5. Unrelated items remain grey BOUNDS

**Chain colour palette** (high-contrast, colourblind-safe):

| Chain # | Colour | RGBA |
|---------|--------|------|
| 1 | Orange | (1.0, 0.6, 0.1, 0.7) |
| 2 | Teal | (0.0, 0.7, 0.65, 0.7) |
| 3 | Magenta | (0.85, 0.2, 0.6, 0.7) |
| 4 | Lime | (0.5, 0.85, 0.1, 0.7) |
| 5+ | Cycle 1-4 | — |

**Data source:** Federation `elements_meta` table has `system_id` / `circuit_id`
columns. The clash module's `clash_grouping.py` already groups elements by system.
No new DB schema needed — just a query: `SELECT id FROM elements_meta WHERE
system_id = (SELECT system_id FROM elements_meta WHERE guid = ?)`.

**Visual result:** User clicks one conduit elbow → entire conduit run lights up
in orange. They instantly see the full path, all connected fittings, the extent
of what they're affecting. Everything else is grey boxes.

#### 26.12.2 Ghost Drag — "Move the Box, Not the Geometry"

**Trigger:** User grabs (G key or gizmo) a focused item while in Design Mode
(Phase 2 — full geometry exists).

**Behaviour:**
1. The solid geometry of the selected item **stays in place** (frozen at original
   position). It becomes a "ghost" — dimmed to 30% alpha or switched to WIRE
   display, showing where the item currently is.
2. A **drag proxy bbox** appears — a vivid wireframe box at the cursor that moves
   freely with the user's mouse/gizmo input.
3. Chain-highlighted items (§26.12.1) **also spawn ghost drag bboxes** that move
   in sync, maintaining their relative offsets to the selected item. The entire
   conduit run moves as a chain of wireframe boxes.
4. The original solid geometry of chain items also stays frozen as ghosts.
5. The user sees: ghost solids (where things were) + moving bbox wireframes
   (where things will go). Zero ambiguity.

**On commit (Enter / CO):**
1. Solid geometry snaps to the new bbox positions
2. Ghost display removed
3. Positions written to DB
4. Items return to BOUNDS display (Phase 2 normal)

**On cancel (Esc / right-click):**
1. Drag proxy bboxes removed
2. Ghost display removed
3. Everything returns to pre-drag state
4. Zero changes to DB

**Implementation approach:**
```python
# Ghost drag state
_ghost_originals = {}  # obj → (original_location, original_display_type)
_drag_bboxes = []      # list of GPU overlay bboxes tracking cursor

def _enter_ghost_drag(focused_obj, chain_objects):
    """Freeze solids, spawn drag proxy bboxes."""
    all_objs = [focused_obj] + chain_objects
    for obj in all_objs:
        _ghost_originals[obj] = (obj.location.copy(), obj.display_type)
        obj.display_type = 'WIRE'
        obj.color = (0.5, 0.5, 0.5, 0.3)  # ghost

    # Build drag proxy bboxes from object bounds
    # These are GPU overlays that follow cursor offset
    _build_drag_proxies(all_objs)

def _commit_ghost_drag():
    """Snap geometry to new positions, clean up ghosts."""
    for obj, (orig_loc, orig_display) in _ghost_originals.items():
        # obj.location already updated by proxy tracking
        obj.display_type = 'BOUNDS'  # back to Phase 2 normal
        obj.color = (0.4, 0.4, 0.4, 0.3)
    _ghost_originals.clear()
    _drag_bboxes.clear()
```

#### 26.12.3 Cost-of-Change Feedback

**Trigger:** During ghost drag (§26.12.2), while the user is moving proxy bboxes
but before commit.

**Behaviour:**
1. As the drag proxy moves, the frontend sends a lightweight `costOfChange` query
   to the backend with the original and proposed positions of all chain items.
2. The backend computes a **BOM diff** — what changes in the bill of materials
   between the current and proposed positions:

| Delta field | Source | Example |
|-------------|--------|---------|
| Material length | Path recalculation (conduit/pipe/tray meters) | "+2.3m copper pipe" |
| Fittings | Bend detection at new routing angles | "+1 elbow, −1 straight coupling" |
| Labour hours | Fitting count × installation rate | "+0.5 hrs" |
| Cost | `M_Product_PO.PriceStd` × quantity deltas | "+RM 47.50" |
| Compliance | Rule check at new positions (clearance, max run, fire zone) | "PASS" or "EXCEEDS 15m max run" |
| Clash count | R-tree intersection at proposed positions | "0 new clashes" or "2 new clashes (ELEC×PLUMB)" |

3. Results display in a **floating cost strip** near the drag proxy — same GPU
   overlay approach as the properties popup (§26.6). Updates live as user drags.
4. Colour-coded: green = cost-neutral or cheaper, yellow = cost increase,
   red = compliance fail or new clash.

**Backend verb:**
```
costOfChange(chainGuids[], originalPositions[], proposedPositions[])
→ { materialDelta, fittingsDelta, labourHrs, costDelta, currency,
    compliance: [{rule, status, detail}],
    newClashes: [{guidA, guidB, discipline}] }
```

**Data source:** All from existing DB — `M_Product` pricing, `AD_Val_Rule`
compliance rules, `elements_rtree` for clash detection. The verb is a pure
function: no side effects, no DB writes until commit.

**Latency contract:** <200ms per update. Debounced at 100ms during drag (don't
query on every mouse pixel — batch the delta).

**On commit:** The cost strip freezes, showing final delta. The `moveChain` verb
writes new positions + updated BOM lines to DB. The cost becomes the audit trail.

**On cancel:** Cost strip dismissed. Zero DB impact.

**UX impact:** The user never commits blind. Every drag shows its business
consequence. "This reroute saves RM 120" or "This move creates 2 new clashes"
— visible before the decision is made, not discovered in a report two weeks later.

#### 26.12.4 Requirements

| ID | Requirement | Acceptance Criteria | Priority |
|----|------------|-------------------|----------|
| WF-11 | Select connected item → entire chain highlighted in single colour | All items sharing `system_id` get chain colour. Max query time 50ms. | P1 |
| WF-12 | Drag in Design Mode spawns ghost + proxy bbox | Original geometry freezes as WIRE/30% alpha. Proxy bbox follows cursor. Chain items move together. | P1 |
| WF-13 | Commit drag updates positions; Cancel restores originals | Enter → snap + DB write. Esc → full restore. Zero residual state. | P1 |
| WF-14 | Cost-of-change shown live during drag | Floating cost strip: material delta, fittings, labour, cost, compliance, clashes. Updates <200ms debounced. | P2 |
| WF-15 | Cost strip colour-coded by severity | Green = neutral/cheaper. Yellow = cost increase. Red = compliance fail or new clash. | P2 |
| WF-16 | Cost-of-change query is side-effect-free | `costOfChange` verb reads DB only, zero writes. Cancel = zero DB impact. | P2 |

#### 26.12.5 Witnesses

| Witness | Tests | Requirement |
|---|---|---|
| W-WF-CHAIN | Select conduit segment → all segments in same system_id highlighted in chain colour, rest grey | WF-11 |
| W-WF-GHOST | Drag focused item → solid freezes as ghost, proxy bbox moves with cursor | WF-12 |
| W-WF-DRAG-COMMIT | Commit drag → geometry at new position. Cancel → geometry at original position. | WF-13 |
| W-WF-COST | Drag conduit 2m right → cost strip shows material delta + cost delta + compliance status | WF-14, WF-15 |
| W-WF-COST-CANCEL | Drag then cancel → zero DB changes, cost strip dismissed | WF-16 |
| W-WF-CR-SPAWN | Commit drag with cost delta → R_Request created with affected disciplines + approvers | WF-17 |
| W-WF-CR-IMPACT | Change Request shows all affected engineers by discipline | WF-18 |
| W-WF-CR-FILES | Change Request attaches before/after BOM diff + cost summary | WF-19 |

### 26.13 Change Request — Stakeholder Impact on Commit (R_Request)

**Principle:** A design change is not just geometry + cost. It affects people.
When a move crosses discipline boundaries, the affected engineers must know.
This maps directly to iDempiere's `R_Request` (CRM request/change order) model.

#### 26.13.1 Trigger

When the user commits a ghost drag (§26.12.2) and the `costOfChange` response
indicates:
- **Cross-discipline impact** — the moved chain affects items owned by other
  disciplines (e.g., moving ACMV duct crosses ELEC cable tray)
- **Cost delta exceeds threshold** — configurable per project (e.g., >RM 500)
- **New clashes introduced** — any non-zero clash count
- **Compliance failure** — any rule violation at new position

A Change Request is automatically spawned.

#### 26.13.2 Change Request Content

The CR maps to iDempiere `R_Request` with BIM-specific fields:

| R_Request field | BIM source | Example |
|----------------|-----------|---------|
| `DocumentNo` | Auto-generated | CR-2026-0047 |
| `R_RequestType_ID` | "Design Change" | — |
| `Summary` | Auto-generated from verb | "ACMV conduit run C-14 relocated +2.3m east" |
| `Priority` | From compliance status | Red=Urgent, Yellow=Normal, Green=Low |
| `SalesRep_ID` | Initiating user (designer) | — |
| `AD_User_ID` (assigned) | Discipline lead of most-affected discipline | — |

**Attached data (R_RequestUpdate / document attachments):**

| Attachment | Content |
|-----------|---------|
| `bom_diff.json` | Before/after BOM line changes — added, removed, modified items |
| `cost_summary.json` | Material, fittings, labour, total cost delta |
| `affected_disciplines.json` | List of disciplines touched + element count per discipline |
| `clash_report.json` | New clashes introduced (if any) — guidA, guidB, type |
| `position_delta.json` | Original and proposed coordinates for all moved items |

#### 26.13.3 Affected Stakeholder Resolution

**"Who is affected?"** — resolved from the discipline ownership chain:

```
1. Query moved items → get discipline per item from elements_meta
2. Query new clashes → get disciplines of clashing items
3. Union all affected disciplines
4. Look up discipline lead (AD_User) from project assignment table
5. Each affected engineer gets notified via R_Request
```

| Stakeholder role | How identified | What they see |
|-----------------|----------------|---------------|
| **Initiator** | Current user | Full CR with "I did this" |
| **Discipline lead (affected)** | `project_discipline_assignment.AD_User_ID` | "Your {ELEC} elements affected by ACMV change" |
| **Project manager** | `C_Project.SalesRep_ID` | CR summary + cost delta |
| **Approver** | Discipline lead with highest cost impact | CR assigned for approval |

#### 26.13.4 Workflow — DocAction Lifecycle

The CR follows the same `processIt()` lifecycle as all iDempiere documents
(see `DocAction_SRS.md`):

```
DR (Draft) ──→ IP (In Progress) ──→ CO (Complete) ──→ AP (Approved)
     │                                                      │
     │         User commits drag                    Engineer reviews
     └── CR auto-created ──→ Notification sent ──→ Approve/Reject
```

**On Approve (AP):** The position change is finalised. BOM updated. Geometry
committed to master.

**On Reject:** The position reverts to pre-drag state. The initiator is notified
with the rejection reason. The ghost drag effectively becomes a cancel — but
with an audit trail of why.

**On Void:** CR withdrawn by initiator before review. Positions revert.

#### 26.13.5 UX in Blender

The CR is spawned **server-side** by the `moveChain` verb. The Python frontend:

1. Calls `moveChain(guids, positions)` on commit
2. Server returns `{ success: true, changeRequestId: "CR-2026-0047",
   affectedDisciplines: ["ELEC", "PLUMB"], approver: "john.doe" }`
3. Blender shows a non-modal notification:
   **"CR-2026-0047 created — ELEC, PLUMB affected — assigned to john.doe"**
4. The committed items show a small badge/icon in their BOUNDS display
   indicating "pending approval" (optional, P2)

The designer keeps working. They don't wait for approval. If the CR is rejected,
the next time they open the project, the revert is visible.

#### 26.13.6 Backend Verb

```
moveChain(chainGuids[], originalPositions[], proposedPositions[])
→ {
    success: boolean,
    updatedPositions: [...],
    costDelta: { material, fittings, labour, total, currency },
    compliance: [{ rule, status, detail }],
    newClashes: [{ guidA, guidB, discipline }],
    // Change Request (if cross-discipline or threshold exceeded)
    changeRequest: {
      id: "CR-2026-0047",
      affectedDisciplines: ["ELEC", "PLUMB"],
      affectedEngineers: [{ name, discipline, email }],
      approver: { name, discipline },
      bomDiff: { added: [...], removed: [...], modified: [...] },
      costSummary: { before, after, delta, currency }
    }
  }
```

#### 26.13.7 Requirements

| ID | Requirement | Acceptance Criteria | Priority |
|----|------------|-------------------|----------|
| WF-17 | Cross-discipline drag commit spawns R_Request | `moveChain` returns `changeRequest` with CR ID when affected disciplines > 1 or cost > threshold | P2 |
| WF-18 | CR identifies all affected engineers by discipline | `affectedEngineers` list populated from `project_discipline_assignment` | P2 |
| WF-19 | CR attaches BOM diff, cost summary, clash report | All attachment JSONs present in R_Request. Retrievable via `getChangeRequest(crId)`. | P2 |
| WF-20 | CR rejection reverts positions | On AP=Reject, moved items return to original positions. Initiator notified. | P2 |

#### 26.13.8 Witnesses

| Witness | Tests | Requirement |
|---|---|---|
| W-WF-CR-SPAWN | Move ACMV chain across ELEC tray → R_Request created, DocumentNo returned | WF-17 |
| W-WF-CR-IMPACT | CR contains affectedDisciplines=["ELEC"] + engineer name from project assignment | WF-18 |
| W-WF-CR-FILES | CR attachments contain bom_diff with added/removed lines + cost_summary with delta | WF-19 |
| W-WF-CR-REVERT | Reject CR → moved items back at original positions, BOM restored | WF-20 |

### 26.14 ChangeLog — Audit Trail + Multi-User Undo (AD_ChangeLog)

**Principle:** Every change to every field by every user is recorded. This is
iDempiere's `AD_ChangeLog` reimagined for BIM — not just "what changed" but
"who changed what, when, from what value, to what value, and why."

#### 26.14.1 Schema

Maps directly to iDempiere's `AD_ChangeLog` table pattern:

```sql
CREATE TABLE bim_changelog (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    -- Who
    ad_user_id      INTEGER NOT NULL,       -- logged-in user
    ad_session_id   INTEGER NOT NULL,       -- Blender session
    -- When
    created         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- What
    table_name      TEXT NOT NULL,           -- 'element_placement', 'x_m_bom', etc.
    record_id       TEXT NOT NULL,           -- PK of changed record (guid or bomId)
    column_name     TEXT NOT NULL,           -- 'pos_x', 'quantity', 'M_Product_ID'
    -- Delta
    old_value       TEXT,                    -- serialised previous value
    new_value       TEXT,                    -- serialised new value
    -- Context
    change_type     TEXT NOT NULL,           -- 'INSERT', 'UPDATE', 'DELETE'
    r_request_id    TEXT,                    -- link to CR if change was part of one
    undo_sequence   INTEGER,                -- groups atomic changes (one drag = one seq)
    description     TEXT                     -- human-readable: "Moved conduit C-14 +2.3m east"
);

CREATE INDEX idx_changelog_user ON bim_changelog(ad_user_id, created);
CREATE INDEX idx_changelog_record ON bim_changelog(table_name, record_id);
CREATE INDEX idx_changelog_undo ON bim_changelog(undo_sequence);
CREATE INDEX idx_changelog_session ON bim_changelog(ad_session_id);
```

#### 26.14.2 What Gets Logged

Every mutation through the Designer server is logged. The Java DAO layer
intercepts writes and records old→new values automatically.

| Operation | table_name | Columns logged | Example |
|-----------|-----------|---------------|---------|
| Ghost drag commit | `element_placement` | `pos_x, pos_y, pos_z` | old: (5.0, 3.0, 0.0) → new: (7.3, 3.0, 0.0) |
| Room resize | `x_m_bom` | `width_mm, depth_mm` | old: 3000 → new: 3500 |
| Product swap | `x_m_bom_line` | `M_Product_ID` | old: DOOR-001 → new: DOOR-002 |
| Add room | `x_m_bom` | (all columns) | change_type=INSERT |
| Remove room | `x_m_bom` | (all columns) | change_type=DELETE, old values preserved |
| Jurisdiction switch | `x_m_bom` | `jurisdiction` | old: "MY" → new: "SG" |
| Save/CO | `work_output` | `DocStatus` | old: "DR" → new: "CO" |
| Chain move | `element_placement` | `pos_x, pos_y, pos_z` × N items | Same undo_sequence for all |

#### 26.14.3 Undo Levels

Changes are grouped by `undo_sequence` — all mutations from a single user action
share the same sequence number. This enables multi-field, multi-record undo as
one atomic operation.

```
undo(ad_user_id, undo_sequence)
→ For each changelog row with this sequence (in reverse order):
    UPDATE {table_name} SET {column_name} = {old_value} WHERE id = {record_id}
    INSERT new changelog row recording the undo itself
```

**Undo is itself logged.** An undo creates new changelog entries (change_type =
'UNDO'), so the undo can itself be undone (redo). The chain is infinite and
fully auditable.

**Multi-user safety:** A user can only undo their own changes. If user A moves
a conduit and user B later modifies the same conduit, user A's undo is blocked
with: "Conflict: {column} modified by {user B} at {timestamp}. Resolve manually
or create CR."

#### 26.14.4 Multi-User Login (AD_User / AD_Session)

Each Blender instance authenticates against the project's user table:

| Table | Purpose |
|-------|---------|
| `AD_User` | Users: name, email, discipline, role |
| `AD_Session` | Active sessions: user, login time, Blender instance ID |
| `project_discipline_assignment` | User → discipline → project mapping |

**Login flow:**
1. BIM Designer connect (A.1) includes `user_id` + `session_token`
2. Server creates `AD_Session` row
3. All subsequent verbs carry `ad_session_id` → logged in `bim_changelog`
4. Panel header shows: "Connected as {user.name} ({discipline})"

**Concurrent edit visibility:** When two users are editing the same building,
each sees the other's changes via the changelog:
- Sync timer (200ms, already in `design_bbox.py`) polls for new changelog entries
  from other sessions
- Other user's changes appear as a notification: "{user} moved {item}"
- Conflicting edits (same item, same field) show a warning badge

#### 26.14.5 Audit Trail Query

The changelog enables queries that no BIM tool currently supports:

| Query | SQL pattern | Use case |
|-------|------------|----------|
| "Who moved this conduit?" | `WHERE record_id = ? ORDER BY created` | Accountability |
| "What did John change today?" | `WHERE ad_user_id = ? AND created > date('now')` | Daily review |
| "Show all changes in CR-47" | `WHERE r_request_id = 'CR-2026-0047'` | CR review |
| "Undo John's last 3 actions" | `WHERE ad_user_id = ? ORDER BY undo_sequence DESC LIMIT 3` | Supervisor override |
| "What changed since last AP?" | `WHERE created > (SELECT created FROM bim_changelog WHERE description LIKE '%DocStatus%CO→AP%')` | Approval gate |
| "Cost history of this room" | `WHERE record_id = ? AND column_name IN ('width_mm','depth_mm','M_Product_ID')` | Cost tracking |

#### 26.14.6 Backend Verb

```
getChangeLog(filters: { recordId?, userId?, sinceTimestamp?, requestId?, limit? })
→ { entries: [{ id, user, timestamp, table, record, column,
                 oldValue, newValue, changeType, undoSeq, description }] }

undoChanges(userId, undoSequence)
→ { success, undoneCount, conflicts: [{ column, modifiedBy, at }] }
```

#### 26.14.7 Requirements

| ID | Requirement | Acceptance Criteria | Priority |
|----|------------|-------------------|----------|
| WF-21 | Every mutation logged to bim_changelog | INSERT/UPDATE/DELETE on Designer tables → changelog row with old/new values, user, timestamp | P1 |
| WF-22 | Atomic undo by undo_sequence | `undoChanges(user, seq)` reverts all rows in sequence, logs the undo | P1 |
| WF-23 | Multi-user conflict detection | Undo blocked if another user modified the same record+column after the original change | P1 |
| WF-24 | Session-based user identity | Each Blender session has ad_session_id, all verbs carry it, changelog links to user | P1 |
| WF-25 | Changelog linked to Change Request | Mutations from a CR carry r_request_id. CR rejection undoes all linked changes. | P2 |

#### 26.14.8 Witnesses

| Witness | Tests | Requirement |
|---|---|---|
| W-WF-LOG | Move item → changelog row with old/new pos_x, correct ad_user_id | WF-21 |
| W-WF-UNDO | Undo sequence → item returns to original position, undo logged as new entry | WF-22 |
| W-WF-CONFLICT | User A moves item, User B moves same item, User A undo → conflict error with B's details | WF-23 |
| W-WF-SESSION | Connect with user credentials → ad_session_id in all subsequent changelog entries | WF-24 |
| W-WF-CR-LOG | CR rejection → all changelog entries with that r_request_id undone | WF-25 |

---

---

## 27. Flywheel Advisory Panel (FL-2)

**Version:** 1.0 (2026-03-21)
**Depends on:** §17 Design Mode, `PlacementValidatorImpl`, BBC.md §9

### 27.1 Purpose

The Data Flywheel (BBC.md §9) produces structured validation advisories
during IFC onboarding — dimension outliers, profile anomalies, novel
classes. These advisories must be surfaced in the BIM Designer so the
user can act on them, not just read log files.

### 27.2 Requirements

| ID | Requirement | Acceptance Criteria |
|----|-------------|---------------------|
| FL-F-01 | **Advisory list.** `listAdvisories(buildingId)` returns all advisories for a building. | JSON array of {layer, severity, message, suggestion, element_ref} |
| FL-F-02 | **Advisory panel.** Blender panel shows advisories colour-coded by severity. | INFO=blue, WARNING=amber, SUGGESTION=green. Scrollable list. |
| FL-F-03 | **Click-to-highlight.** Clicking an advisory highlights the flagged element in the 3D viewport. | `focus_section(element_ref)` bridges advisory → viewport object. |
| FL-F-04 | **Apply suggestion.** SUGGESTION-type advisories offer "Apply" button. | Calls `updateOrderLine(orderLineId, field, suggestedValue)`. Dimension updates immediately in viewport. |
| FL-F-05 | **CALIBRATE auto-fill.** When placing a new element, dimension fields are pre-populated with typical values from the mined pool. **API DONE (S50)** — `suggestDimensions(ifcClass)` wired. UX auto-fill pending. | `suggestDimensions(ifcClass)` → returns {typicalMinW..typicalMaxH, observationCount, nearestProducts[]}. |
| FL-F-06 | **Refresh on load.** Advisories refresh when a building is loaded or after compilation. | Panel clears and re-queries on `loadBuilding` or `compile`. |

### 27.3 Advisory Severities

| Severity | Meaning | User Action | Example |
|----------|---------|-------------|---------|
| INFO | Observation from mined data | None required | "8 IFC classes detected (typical: 7-12)" |
| WARNING | Element dimension outside observed range | Review element | "IfcWall W=95mm (range [800-10269]mm)" |
| SUGGESTION | System can auto-correct | Click "Apply" | "Nearest typical: 5000mm (from Esplanades)" |

### 27.4 Data Flow

```
IFC onboarding (IFCtoBOMPipeline)
  → DimensionRangeValidator.validate()
  → BuildingProfileValidator.validate()
  → Writes to W_Validation_Advisory table in ERP.db

BIM Designer loads building
  → listAdvisories(buildingId)
  → Reads W_Validation_Advisory
  → Renders in Advisory Panel

User clicks "Apply" on SUGGESTION
  → updateOrderLine(orderLineId, 'aabb_width_mm', suggestedValue)
  → Viewport updates via sync timer (UX-F-26)
```

### 27.5 Output Specification

**Schema (DV012):**
```sql
CREATE TABLE IF NOT EXISTS W_Validation_Advisory (
    advisory_id     INTEGER PRIMARY KEY AUTOINCREMENT,
    building_type   TEXT NOT NULL,
    element_ref     TEXT,           -- element bomId (NULL for profile-level)
    layer           TEXT NOT NULL   -- 'DIMENSION', 'PROFILE', 'COMPLIANCE', 'SHAPE'
                    CHECK(layer IN ('DIMENSION','PROFILE','COMPLIANCE','SHAPE')),
    severity        TEXT NOT NULL   -- 'INFO', 'WARNING', 'SUGGESTION'
                    CHECK(severity IN ('INFO','WARNING','SUGGESTION')),
    rule_name       TEXT,           -- e.g. 'DIMENSION_RANGE_W:IfcWall'
    message         TEXT NOT NULL,  -- human-readable
    actual_value    REAL,
    expected_min    REAL,
    expected_max    REAL,
    suggestion      TEXT,           -- e.g. 'Nearest typical: 5000mm (from Esplanades)'
    created_at      TEXT DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_advisory_building ON W_Validation_Advisory(building_type);
CREATE INDEX IF NOT EXISTS idx_advisory_severity ON W_Validation_Advisory(severity);
```

**Java API record:**
```java
record Advisory(int advisoryId, String buildingType, String elementRef,
                String layer, String severity, String ruleName,
                String message, Double actualValue,
                Double expectedMin, Double expectedMax,
                String suggestion) {}

record ListAdvisoriesResponse(boolean success,
                               List<Advisory> advisories,
                               int infoCount, int warningCount,
                               int suggestionCount, String error) {}
```

**Writers (IFCtoBOMPipeline post-validate):**
- `DimensionRangeValidator.Report.outliersByClass` → one WARNING row per outlier, one SUGGESTION row if nearest typical exists
- `BuildingProfileValidator.Report.anomalies` → one WARNING row per anomaly (DIVERSITY, MISSING_OPENINGS, NOVEL_CLASS, EXTREME_RATIO)
- Profile-level INFO for building stats (class count, dominant class)

**Wire protocol:** `{"action":"listAdvisories","buildingId":"..."}` → `ListAdvisoriesResponse` JSON

### 27.6 Implementation Notes

- **Java:** DesignerAPIImpl +1 method (`listAdvisories`), DesignerServer +1 dispatch case
- **Python:** client.py +1 verb (`list_advisories`), panel.py +1 section (`_draw_advisory_panel`)
- **Schema:** DV012 migration creates `W_Validation_Advisory` table in ERP.db
- **No new validation logic on Python side** — all intelligence stays in Java
- **Backward compatible:** advisory panel is additive, existing panels unchanged
- **Write-side:** DimensionRangeValidator and BuildingProfileValidator gain `writeAdvisories(Connection discConn, Report report)` methods, called from IFCtoBOMPipeline after `validate()`

### 27.7 Witness Claims

| Witness | Claim | Status |
|---------|-------|--------|
| W-FL-ADVISORY-1 | listAdvisories returns advisories for known building | GREEN (S50) |
| W-FL-ADVISORY-2 | SUGGESTION-type advisory includes suggested value and nearest typical | GREEN (S50) |
| W-FL-ADVISORY-3 | Empty advisory list for building with no outliers | GREEN (S50) |
| W-FL-ADVISORY-4 | DimensionRangeValidator.writeAdvisories creates WARNING + SUGGESTION rows for each outlier | GREEN (S50) |
| W-FL-ADVISORY-5 | BuildingProfileValidator.writeAdvisories creates WARNING rows for anomalies | GREEN (S50) |
| W-FL-CALIBRATE-1 | suggestDimensions(IfcWall) returns typical range | GREEN (S50) |
| W-FL-CALIBRATE-2 | suggestDimensions(unknown) returns empty | GREEN (S50) |
| W-FL-SHAPE-1 | ShapeAdvisoryWriter detects class-shape mismatch (FL-5) | GREEN (S50) |
| W-FL-SHAPE-2 | No shape advisories for geometrically consistent elements (FL-5) | GREEN (S50) |

**Test:** `FlyAdvisoryTest` in `IFCtoBOM/src/test/java/com/bim/ifctobom/` — 9/9 GREEN.

---

## 28. BOM Drop — Template-First Building Design

> **Foundation:** [GENERATIVE_HOUSE_SRS.md](GENERATIVE_HOUSE_SRS.md) §2.1 (BOM Drop) · [BBC.md §3.5.1](BOMBasedCompilation.md) (ASI) · [BBC.md §3.5](BOMBasedCompilation.md) (Selection Cascade)
> **Schema:** `ASI_001_attribute_set_instance.sql` · `W001_work_output_schema.sql`

*Date: 2026-03-22. Session: S52b. Replaces: room-by-room cascade (§28 v1).*

### 28.1 Paradigm — Template + BOM Drop + ASI

> **Separation of concerns:** The BOM Drop **engine** (explode BOM tree, resolve
> products, compute placements) lives in the backend — DAGCompiler / BIM_COBOL /
> pipeline layer. The BIM Designer is a **GUI that calls the engine** via
> `DesignerAPI.bomDrop()`. The Designer never walks BOMs, never resolves products,
> never computes geometry. It renders the tree the engine returns and sends user
> edits back as C_OrderLine mutations. Same principle as compile(): the Designer
> calls it, the pipeline does the work.

The Designer is a **tree editor with a product catalog**, not a building generator.

The user starts with a **complete, proven building** (SH, FK, DX — any Rosetta Stone
or previously promoted generative building) and makes targeted edits. The compiler
already knows how to explode BOM trees — that is what it does for every Rosetta Stone.
The generative path is just the ERP order-entry pattern: place an order for a product,
the BOM explodes.

```
Step 1: BOM Drop — pick a building template
        User: "Give me SH" → 1 C_OrderLine: BUILDING_SH_STD
                                              │
Step 2: Tree Navigation — BOM Outliner (§17.19)
        BUILDING_SH_STD                       │
        ├─ SH_GF_STR         (floor structure)│
        ├─ FLOOR_SH_GF_STD   (floor rooms)   │── user navigates this tree
        │  ├─ SH_LIVING_SET                   │
        │  ├─ SH_DINING_SET                   │
        │  ├─ SH_BED_SET                      │
        │  └─ TOILET_BLOCK                    │
        ├─ SH_ROOF_STR       ← user SWAPS this node
        ├─ SH_CW_STR         (curtain wall)   │
        └─ FLOOR_SLAB_GF     (slab)           │
                                              │
Step 3: Edit — swap, add, remove, ASI         │
        Swap: SH_ROOF_STR → FK_PITCHED_ROOF  │
        Add: C_OrderLine for FP sprinklers    │
        ASI: wall length_mm, material         │
                                              │
Step 4: Compile — same compiler, same gates   │
        Explodes modified BOM tree → output.db
```

**Three interaction modes:**

| Mode | User action | Example |
|------|------------|---------|
| **Instant Drop** | Pick template, no edits, compile | TC-1: "Give me SH" → 55 elements |
| **BOM Drop** | Pick template, navigate tree, swap/add/remove | TC-4: SH + swap roof → pitched |
| **From Scratch** | No template, autoPopulate fills from cascade | Fallback: topology maker path |

### 28.2 Why This Is Simpler

The old room-by-room cascade required 8 heavy steps (layout generation → cascade per
room → ASI computation per element → gap-fill per rule → clash detection). BOM Drop
requires 3 steps: pick template → navigate tree → swap what you want.

| Concern | Room-by-room | BOM Drop |
|---------|-------------|----------|
| Roof | Must author BIM_Roof ASI, compute rafters | Already in template (SH flat / FK pitched) |
| Foundation | Must author from scratch | Already in template |
| Structural load path | Must compute beams, columns | Already proven in extracted building |
| Wall-roof junction | Compute top_trim_mm per wall | Template walls already sized correctly |
| Opening gap-fill | Query 103 rules, auto-add | Template already has all openings |
| MEP gap-fill | Query 186 rules, auto-add | Additive only (TC-5: add discipline) |
| FP clash | Check all auto-placed items | Only check items user added/moved |
| Element count | Unknown until cascade fills | Known from template (SH=55, FK=82) |
| Roof-wall trim for curtain wall | Must compute per-panel | Pre-cut in extracted template |

**The construction completeness problem (§8 in GENERATIVE_HOUSE_SRS.md) disappears
for template-based builds.** The extracted building already has roof, foundation,
structural frame, curtain wall panels, all correctly sized. The user only deals with
construction completeness if they create from scratch (fallback path).

### 28.3 Architectural Boundary — Designer vs Engine

```
┌─────────────────────────────────────────────────────┐
│  BIM Designer (GUI layer)                           │
│                                                     │
│  1. Render tree (BOM Outliner)                      │
│  2. Accept user edits (swap/add/remove/ASI)         │
│  3. Write changes to C_OrderLine in work_output.db  │
│  4. Call engine: bomDrop() / compile()              │
│                                                     │
│  NEVER: walks BOMs, resolves products, computes     │
│         geometry, reads m_bom_line, runs verbs      │
└──────────────────────┬──────────────────────────────┘
                       │  save → call engine
                       ▼
┌─────────────────────────────────────────────────────┐
│  BOM Drop Engine (backend — DAGCompiler / pipeline) │
│                                                     │
│  bomDrop():  read C_OrderLine → resolve BUILDING    │
│              product → walk m_bom/m_bom_line →      │
│              return exploded tree for GUI            │
│                                                     │
│  compile():  same 9-stage pipeline as Rosetta Stones│
│              reads the saved C_OrderLine state       │
│              explodes → places → output.db          │
│                                                     │
│  Owns: BOMWalker, PlacementCollector, VerbRegistry, │
│        MeshBinder, BIMEyes, gate checks             │
└─────────────────────────────────────────────────────┘
```

**The pattern is identical to compilation today:**

1. GUI makes changes → writes to work_output.db (C_OrderLine mutations)
2. GUI calls engine (bomDrop or compile)
3. Engine reads the saved state, does all the heavy lifting, returns result
4. GUI renders the result

The Designer never interprets BOM structure. It writes OrderLine rows and calls
the engine. The engine reads those rows, resolves them against the BOM/product
databases, and returns either a tree (for navigation) or output.db (for delivery).
This is the same separation as the existing `compile()` path — `DesignerAPIImpl`
delegates to `CompilationPipeline`, which owns all BOM walking and placement logic.

### 28.4 API Mapping — What the Designer Calls

| Existing API | BOM Drop Role | Change? |
|---|---|---|
| `listBuildingTypes()` | Step 1: user picks template | NO |
| `getBomTree(buildingId)` | Step 2: Outliner shows exploded tree | SMALL — read from BOM.db not just C_OrderLine |
| `browseItems(request)` | Step 3: user clicks node → sees swap candidates | NO |
| `findSimilar(request)` | "Show me similar roof products" | NO |
| `updateOrderLine(id, field, value)` | Swap product on a node | SMALL — `family_ref` as editable field |
| `placeItem(request)` | Add new item (e.g. sprinkler) | NO |
| `readASI(buildingId, olId)` | Inspect per-instance attributes | NO — S52b done |
| `updateASI(buildingId, olId, name, value)` | Tweak ASI (wall length, material) | NO — S52b done |
| `approve(buildingId)` | Pre-promote validation gate | NO |
| `promote(request)` | Freeze order → BOM | NO |
| `compile(request)` | Explode + output | NO |

**One new action needed** (thin delegation to backend engine):

```java
// ── BOM Drop — template-first building creation (§28.1) ──────

/**
 * Create a C_Order with a single C_OrderLine referencing a complete
 * BUILDING product. Delegates to the backend BOM Drop engine to
 * explode the BOM tree. Returns the tree for Outliner navigation.
 *
 * <p>The Designer does NOT walk the BOM tree itself. The engine
 * reads m_bom/m_bom_line, resolves products, and returns the
 * exploded tree as a BomTreeNode hierarchy.
 *
 * <p>Instant Drop: if the user compiles without editing, the output
 * is identical to the Rosetta Stone for that building.
 *
 * <p>BOM Drop: user navigates the returned tree and edits via
 * updateOrderLine(), placeItem(), updateASI().
 *
 * // Implementing GENERATIVE_HOUSE_SRS.md §2.1 — Witness: W-DROP-1
 */
BomDropResponse bomDrop(String buildingProductId);

record BomDropResponse(
    boolean success,
    String orderId,
    int orderLineId,
    BomTreeNode tree,        // exploded BOM tree for Outliner
    int totalElements,       // element count if compiled as-is
    String error
) {}
```

**Demoted APIs** (still exist, now fallback path for "from scratch"):

| API | New Status |
|---|---|
| `createNew()` + `RoomLayoutGenerator` | Fallback — "from scratch" only |
| `autoPopulate()` | Fallback — fills empty rooms when no template |
| `suggestRoomContents()` | Internal helper for swap candidates |
| `addRoom()` / `removeRoom()` / `addStorey()` | Still valid — TC-3 "add bedroom" |

### 28.5 ASI in BOM Drop Context

ASI serves a different role in BOM Drop vs room-by-room:

| | Room-by-room | BOM Drop |
|---|---|---|
| **When ASI is created** | During auto-populate (stretch walls to room) | When user explicitly edits a dimension |
| **Default state** | Every wall gets auto-computed ASI | No ASI needed — template dimensions are correct |
| **User interaction** | Review auto-computed values | Only create ASI if changing something |

In BOM Drop, most elements have **no ASI** — the template's `allocated_*_mm` values
are already correct. ASI only appears when the user actively customizes:

- TC-6 "SH but 20% bigger" → ASI `scale_factor: 1.2` on walls
- TC-4 "Swap roof" → no ASI needed (swapped product has its own dimensions)
- TC-5 "Add sprinklers" → no ASI (sprinklers are IsInstanceAttribute=0)

The ASI CRUD backend (WorkOutputDAO, S52b) remains correct — it is called when the
user edits individual items, not during initial BOM Drop.

### 28.6 ASI Resolution Rule (unchanged)

```
effective_dimension = ASI_override ?? allocated_*_mm ?? M_Product.catalog_default
```

| Tier | Source | When used |
|------|--------|-----------|
| 1 | `M_AttributeInstance.Value` (ASI override) | User changed a dimension |
| 2 | `m_bom_line.allocated_*_mm` (BOM recipe) | Template default (most elements) |
| 3 | `M_Product.width/depth/height` (catalog) | Fallback for missing allocated |

### 28.7 ASI Field Resolution Matrix (unchanged)

| Product Type | M_AttributeSet | IsInstance | Varying (ASI fields) | Fixed (catalog) |
|---|---|---|---|---|
| Wall | BIM_Wall | 1 | length_mm, height_mm, material, finish | thickness |
| Pipe | BIM_Pipe | 1 | length_mm, angle_deg, elevation | diameter |
| Conduit | BIM_Conduit | 1 | length_mm | diameter, material |
| Duct | BIM_Duct | 1 | length_mm, cross_section | material |
| Door | BIM_Door | 1 | swing_side, face_anchor (INT/EXT) | width, height |
| Window | BIM_Window | 1 | sill_height_mm, face_anchor | width, height |
| Slab | BIM_Slab | 1 | area_m2, thickness_mm | material |
| Beam | BIM_Beam | 1 | span_mm | section profile |
| Column | BIM_Column | 1 | height_mm | section profile |
| Furniture | BIM_Component | 0 | — | All dims fixed |
| MEP terminal | BIM_Component | 0 | — | Identical everywhere |
| Fitting | BIM_Fitting | 0 | — | Angle is type, not instance |

### 28.8 WorkOutputDAO ASI Operations (implemented S52b)

| Method | Purpose | Status |
|--------|---------|--------|
| `createASI(attributeSetId, values)` | Create ASI header + name/value pairs | DONE |
| `updateASIAttribute(asiId, name, value)` | Update/upsert single attribute | DONE |
| `readASI(asiId)` | Read all attribute values for an ASI | DONE |
| `linkASI(orderLineId, asiId)` | Set FK on C_OrderLine | DONE |
| `getASIForOrderLine(orderLineId)` | Read linked ASI ID | DONE |
| `listAttributeSets()` | List M_AttributeSet templates | DONE |
| `resolveAttributeSetForProduct(familyRef)` | WALL_* → BIM_Wall, etc. | DONE |
| `isInstanceAttribute(attributeSetId)` | Check if product type needs ASI | DONE |
| `initASISchema()` | Create tables + seed 11 templates | DONE |

### 28.9 UX Interaction — BOM Drop Sequence

```
User opens Designer
  → listBuildingTypes() shows: SH (55 el), FK (82 el), DX (1099 el), ...
  → user clicks "SH — Sample House"
     → bomDrop("BUILDING_SH_STD")
        → C_Order created, 1 C_OrderLine
        → BOM tree returned for Outliner
  → Outliner shows:
     BUILDING_SH_STD
     ├─ SH_GF_STR (floor structure, 5 elements)
     ├─ FLOOR_SH_GF (rooms)
     │  ├─ SH_LIVING_SET (8 elements)    ← user clicks here
     │  │    → browseItems or findSimilar shows swap candidates
     │  │    → user keeps it (or swaps with DX_LIVING_SET)
     │  ├─ SH_BED_SET (4 elements)
     │  └─ TOILET_BLOCK (3 elements)
     ├─ SH_ROOF_STR (2 elements)         ← user clicks "Swap"
     │    → browseItems(category="roof") shows: FK_PITCHED_ROOF, CS_METAL_ROOF
     │    → user picks FK_PITCHED_ROOF
     │    → updateOrderLine(id, "family_ref", "FK_PITCHED_ROOF")
     ├─ SH_CW_STR (21 curtain wall segments)
     └─ FLOOR_SLAB_GF (1 slab)
  → user clicks "Add Sprinklers"
     → placeItem(discipline="FP") per room
     → validation rules compute placement from ad_space_type_mep_bom
  → user clicks on a wall → readASI shows: length_mm=3800, material=Brick
     → user changes material: updateASI(olId, "material", "ConcreteBlock")
  → user clicks "Compile"
     → compile() explodes modified tree → output.db
     → G1-COUNT, G5-PROVENANCE verified
  → user clicks "Promote" → governance gate (§18)
```

### 28.10 Validation in BOM Drop

Validation rules have a **lighter** role in BOM Drop than in room-by-room:

| Rule Source | Room-by-room role | BOM Drop role |
|---|---|---|
| `ad_space_type_opening` (103 rules) | Auto-add missing openings | Verify template openings are present; flag only if user removed one |
| `ad_space_type_mep_bom` (186 rules) | Auto-add missing MEP | Compute placement for user-added disciplines (TC-5) |
| `AD_Val_Rule` (63 rules) | Enforce min area, min height | Same — validate after any swap/resize |
| `AD_Clash_Rule` (FP vs STR) | Check all auto-placed items | Check only user-added items against template structure |

The approve gate (§18) runs all validation before promote. BOM Drop makes validation
faster because most elements are template-proven — only user modifications need checking.

### 28.11 DocAction Lifecycle — iDempiere Document Processing

BOM Drop follows the standard iDempiere DocAction pattern on C_Order:

| DocAction | DocStatus | Backend behaviour |
|-----------|-----------|------------------|
| **bomDrop()** | → DR | Create C_Order + explode BOM tree into C_OrderLine hierarchy (compile DB). Backend auto-explodes; GUI shows collapsed tree. |
| **Save** | — | Blender native save (.blend file). No backend action — BOM is read-only. |
| **Complete** | DR → CO | Full 9-stage compilation from compile DB → output.db (path editable in UI). Preview BBoxes → coloured cubes. Full Load → solid meshes in Blender. |
| **Approve** | CO → AP | Promote as new BOM in catalog. Requires: parent category, qualified name, author/version, children validated. Governance gate. |

**iDempiere OrderLine model:**
- C_OrderLine references `M_Product_ID` (= `family_ref`). No BOMCategory on OrderLine level.
- Product category for swap browsing is resolved from `M_Product.M_Product_Category_ID` or `m_bom.bom_category`.
- Only products with IsBOM=Y (matching `m_bom` entry) are exploded recursively.
- If user doesn't modify the tree, fold back to parent line (thin pipe performance).
- If user expands and modifies, all child C_OrderLine rows flow through.

### 28.12 Witness Claims

| Witness | Claim | Status |
|---------|-------|--------|
| W-DROP-1 | bomDrop creates C_Order + explodes BOM tree into C_OrderLine hierarchy | GREEN (S54) |
| W-DROP-2 | Instant Drop totalElements = 55 (SH Rosetta Stone match) | GREEN (S54) |
| W-DROP-3 | Tree has FLOOR-level children (IsBOM sub-assemblies) | GREEN (S54) |
| W-DROP-4 | FLOOR_SH_GF_STD contains ROOM sub-assemblies with LEAF children | GREEN (S54) |
| W-DROP-5 | Non-BOM product (IsBOM=false) returns error | GREEN (S54) |
| W-DROP-6 | Each node carries bom_category for category-based swap browsing | GREEN (S54) |
| W-ASI-READ-1 | readASI returns correct fields for wall with ASI override | GREEN (S52b) |
| W-ASI-EDIT-1 | updateASI changes single field, effective dimension reflects override | GREEN (S52b) |
| W-ASI-AUTO-1 | autoPopulate fills empty rooms (fallback path) | GREEN (S52b) |
| W-ASI-AUTO-3 | IsInstanceAttribute=0 products have no ASI | GREEN (S52b) |
| W-ASI-RESOLVE-1 | Compiler resolves ASI > allocated > catalog in correct priority | SPEC |

### 28.13 Traceability

| Requirement | Source | Implementation |
|-------------|--------|---------------|
| BOM Drop paradigm | GENERATIVE_HOUSE_SRS.md §2.1 | `DesignerAPI.bomDrop()` — GREEN (S54) |
| Instant Drop = Rosetta Stone | GENERATIVE_HOUSE_SRS.md §7 TC-1 | `BomDropTest` W-DROP-2: 55 elements |
| DocAction lifecycle | iDempiere C_Order processing | Save→validate, Approve→promote, Complete→compile |
| Tree navigation | BIM_Designer.md §17.19 (BOM Outliner) | `getBomTree()` + `explodeBomTree()` |
| Product swap in tree | BIM_Designer.md §17.11 (ORDER View edit) | `updateOrderLine()` + `family_ref` field |
| ASI three-table pattern | BBC.md §3.5.1 | `ASI_001` + `WorkOutputDAO` (S52b DONE) |
| Selection Cascade | BBC.md §3.5 | `DesignerDAO.findMatchingSets()` (swap candidates) |
| Auto-populate fallback | This section §28.1 | `autoPopulate()` (S52b DONE, demoted to fallback) |
| Validation on edit | This section §28.9 | `approve()` + `PlacementValidator` (existing) |

---

## 29. Web UI Frontend (S56)

> **Spec:** [BIM_Designer_UserGuide.md §13](BIM_Designer_UserGuide.md) (tab layout, DocAction flow, tech stack)
> **Architecture:** Web UI for data/control, Bonsai for viewport only (S55 decision)
> **Launch:** `./scripts/run_webui.sh` — opens http://localhost:9878

### 29.1 Server Architecture

| Component | Class | Port | Role |
|-----------|-------|------|------|
| DesignerServer | `DesignerServer.java` | 9876 | TCP/NDJSON — Bonsai addon bridge |
| **WebUIServer** | `WebUIServer.java` | **9878** | **HTTP static files + JSON API** |
| BackOfficeServer | `BackOfficeServer.java` | 9877 | HTTP REST — portfolio/reports |

**WebUIServer** uses JDK `com.sun.net.httpserver.HttpServer` (same proven pattern as
BackOfficeServer). Two contexts:
- `GET /` → serve static files from `webui/` directory
- `POST /api` → JSON action dispatch (same actions as DesignerServer)

**No WebSocket.** Browser uses `fetch()` (HTTP POST) for all actions. This avoids
WebSocket browser compatibility issues and is simpler to debug/proxy/firewall.

**No new dependencies.** Pure JDK HttpServer.

**Standalone operation:** WebUIServer runs independently of Bonsai. User opens browser,
selects building, configures BOM, compiles — all without Bonsai. Bonsai is optional
(viewport only — receives `COMPILE_COMPLETE` via TCP/NDJSON on port 9876).

**Startup guard:** `run_webui.sh` checks if port 9878 is already in use before starting.
If occupied (stale instance), warns and exits — prevents duplicate servers and unwanted
browser tabs. Browser only opens after server confirms startup on the port.

**Idle timeout:** WebUIServer shuts itself down after 4 hours of no API activity
(no `POST /api` requests). A daemon watchdog thread checks every 60 seconds.
This prevents stale server instances from accumulating across dev sessions.

### 29.2 HTTP API Protocol

```
Browser → Server:  POST /api  {"action":"scanLibrary"}
Server → Browser:  200 OK     {"buildings":[...], "count":35}
```

All 47+ DesignerServer wire actions are available, plus two new WebUI-specific actions:

| Action | Source | Purpose |
|--------|--------|---------|
| `scanLibrary` | WebUIServer | Scan `library/*_BOM.db`, query `C_DocType` from each |
| `loadBuildingDetail` | WebUIServer | BOM stats from a specific `*_BOM.db` file |
| `compile`, `bomDrop`, `getBomTree`, ... | DesignerServer | All 47+ existing wire actions |

**`scanLibrary` flow:**
1. List `library/*_BOM.db` files (filesystem scan)
2. For each, open SQLite connection, query `C_DocType` for ProjectName/Name/ExpectedElements
3. Return `{buildings: [{code, name, dbFile, elementCount, bomCount}, ...], count, libraryDir}`

**`_callId` echo:** WebUIServer injects `_callId` from the request into the response
JSON so the browser can correlate responses when multiple requests are in-flight.

### 29.3 Static UI

| File | Lines | Purpose |
|------|-------|---------|
| `webui/index.html` | ~330 | SPA shell — header (search), building selector, 10 tabs (§30.3 aligned), footer (compliance) |
| `webui/app.js` | ~1020 | 30+ functions, `fetch()` API, SSE listener, BOM tree renderer, 4 palettes |
| `webui/style.css` | ~710 | Dark theme (#1a1a2e/#16213e/#0f3460/#e94560), cards, split pane, sync indicators |

**Tab-action mapping (aligned to §30.3 — S60):**

| Tab | Wire Action(s) | UI Widget |
|-----|----------------|-----------|
| 1D Order | `getBomTree`, `listOrderLines`, `bomDrop`, `save`, `approve`, `completeIt`, `promote`, `readASI`, `updateASI` | Split pane: BOM tree + ASI panel. Order Lines table below. DocAction bar. |
| 2D Spatial | `loadBuildingDetail` | Storey browser + containment tree + Import/Export buttons |
| 3D Geometry | `scanLibrary`, `loadBuildingDetail`, `compile`, `applyScheme` | Federation status cards + buildings table + Preview/Full Load/Compile |
| 4D Schedule | `constructionSchedule` | Data table (task, discipline, start/end, duration). Placeholder until ScheduleDAO wired. |
| 5D Cost | `costBreakdown` | Data table with material/labour/equipment + totals row. Placeholder until CostDAO wired. |
| 6D Sustain | `carbonFootprint` | Summary cards (CO2e, mass, intensity) + data table. Placeholder until SustainabilityDAO wired. |
| 7D Facility | `maintenanceSchedule` | Data table (asset, type, interval, cost). Placeholder until FacilityMgmtDAO wired. |
| 8 Validate | `listAdvisories`, `portfolio`, `balancedScorecard` | Compliance summary cards + advisory table + portfolio + scorecard |
| 9 BOM | `getBomTree`, `executeNlpQuery`, `browseItems` | BOM Outliner + NLP search (10 presets) + product catalog search |
| 10 Colour | `applyScheme` (SSE sync) | Bonsai selection sync + 4 palettes (16 colors each) + discipline buttons + apply-to-Bonsai |

**NLP Query** (in Tab 9) mirrors the Bonsai federation NLP module — same 10 suggested queries.
**Global search** in the header bar routes questions to NLP Query, keywords to Product Catalog.

**Color Studio** (Tab 10) renders 4 construction palettes. Color application pushes to Bonsai
viewport via `applyScheme` → `pollCommands`. Bidirectional: Bonsai selection reflects in Tab 10.

### 29.4 Bonsai Panel Integration

Each of the 10 active Bonsai panels has an "Open in Web UI" button:
```python
op = row.operator("bim.launch_web_ui", text="Open in Web UI", icon="URL")
op.tab = "4d"  # opens http://localhost:9878/#tab=4d
```

3 new panels added (1D BOM Designer, 2D Import/Export, 8 ERP Reports) — these are
Web UI-primary tabs that show a brief description + launch button in Bonsai.

**Operator:** `BIM_OT_launch_web_ui` — `webbrowser.open(url)`, `tab` StringProperty.

### 29.5 1D Order Configurator (S57)

The 1D tab is the **Sales Order** — pure iDempiere pattern. No YAML editor.
The YAML is redundant: the Order Lines ARE the building specification. The BOM Drop
explodes a template into C_OrderLine rows; the user edits lines and attributes directly.
YAML can be *generated* from the order state if needed for the backend pipeline.

**Flow:**
1. Building product dropdown: `scanBomProducts` — query `m_bom WHERE bom_type='BUILDING'`
   from each `*_BOM.db`. User picks a template (e.g. `BUILDING_SH_STD`).
2. **BOM Drop** (`bomDrop`): creates `C_Order` + `C_OrderLine`(s) from BOM tree explosion.
3. **OrderLine table** (`listOrderLines`): editable grid — product, category, qty, status.
   Inline edit via `updateOrderLine`. Each line references an `M_Product_ID`.
4. **ASI panel** (`readASI`, `updateASI`): per-line attribute editing — dimensions (W/D/H),
   material, finish, fire rating. From `M_AttributeSetInstance` (see §28, S52b).
5. **Save**: Blender native save (.blend file). BOM is read-only — no backend persist.
6. **Complete** (`compile` / `completeIt`): compile BOM (compile DB) → `output.db` (path editable).
   Push `COMPILE_COMPLETE` to Bonsai for viewport rendering.

**Wire actions (implemented S57):**

| Action | Direction | Purpose |
|--------|-----------|---------|
| `scanBomProducts` | Browser → Server | List BUILDING BOMs from each `*_BOM.db` (34 templates) |
| `bomDrop` | Browser → Server | Explode BOM tree → C_Order + C_OrderLine hierarchy |
| `readASI` | Browser → Server | Read ASI fields for an OrderLine |
| `updateASI` | Browser → Server | Update single ASI attribute value |
| `prepareIt` | Browser → Server | Validate (DocAction DR→IP) |
| `completeIt` | Browser → Server | Compile (DocAction IP→CO) |

**BOM Drop routing:** `scanBomProducts` returns `dbFile` (absolute path to `*_BOM.db`) per product.
Browser sends `bomDbPath` with `bomDrop` request. WebUIServer opens that specific DB with a
temporary `DesignerAPIImpl` — avoids "no such table: m_bom" (constructor `bomConn` points to
`component_library.db`, not the per-building BOM DB).

**UI layout:**
```
┌─ [BOM Template ▼]  [BOM Drop] [Save] [Complete]  status ─┐
├─ BOM Tree (left 60%)      │  ASI Panel (right 40%)       ─┤
├─ OrderLine table (full width, editable, dblclick cells)   ─┤
└───────────────────────────────────────────────────────────┘
```

### 29.6 Bidirectional Sync — Bonsai ↔ Web UI (S57)

**Architecture:** SSE (Server-Sent Events) pushes from server to browser.
HTTP POST + command polling provides Bonsai→server→browser and browser→server→Bonsai.

```
Bonsai (viewport)         WebUIServer (:9878)         Browser (Web UI)
  │ selectionChanged ──→  │ ── SSE push ──────────→  │ highlights in tab 10
  │ pollCommands ←──────  │ ←── applyScheme ────────  │ user picks color
  │ pollCommands ←──────  │ ←── compile complete ───  │ user clicks Complete
  │ auto-load viewport    │                            │
```

**Endpoints:**

| Endpoint | Direction | Purpose |
|----------|-----------|---------|
| `GET /events` | Server → Browser | SSE stream (selectionChanged, compileComplete, schemeApplied) |
| `POST /api selectionChanged` | Bonsai → Server | Push viewport selection metadata |
| `POST /api applyScheme` | Browser → Server | Queue color command for Bonsai |
| `POST /api pollCommands` | Bonsai → Server | Pull pending commands (color, loadOutput) |
| `POST /api getSelection` | Browser → Server | Get current Bonsai selection state |
| `POST /api scanBomProducts` | Browser → Server | List BUILDING-level BOMs from all *_BOM.db |
| `POST /api launchBonsai` | Browser → Server | Fire `blender` process |

**Compile → Auto-Load flow:**
1. Browser sends `compile` or `completeIt`
2. WebUIServer dispatches to DesignerServer, gets CompileResponse with `outputDbPath`
3. WebUIServer SSE-broadcasts `compileComplete` event to browser
4. WebUIServer queues `loadOutput` command with `outputDbPath`
5. Bonsai timer polls `pollCommands`, receives `loadOutput`
6. Bonsai sets `federation_database_path` and calls viewport loader
7. Geometry appears in Bonsai — zero manual steps

**Bonsai addon layout:**
- `BIM_PT_webui_sync` — top panel (`bl_order=0`), always first. "Start Sync + Open Web UI" button.
  Starts 0.5s timer (selection watch + command poll) and opens browser.
- Color Studio (tab 10) — "Start/Stop Sync" buttons, discipline color apply, scheme management.

**Files:**

| File | Role |
|------|------|
| `WebUIServer.java` | SSE endpoint, sync actions, afterCompile hook, launchBonsai |
| `webui/app.js` | EventSource connection, tab 10 sync UI, compileComplete listener |
| `webui/index.html` | Bonsai Selection card, Apply to Bonsai buttons, Launch Bonsai header button |
| `webui/style.css` | sync-card, sync-dot, sync-object styles |
| `webui_sync.py` | Bonsai timer, selection watcher, command poller, loadOutput handler |
| `ui_federation_project.py` | BIM_PT_webui_sync top panel, Color Studio sync buttons |

### 29.7 Witnesses

| Witness | Claim | Test |
|---------|-------|------|
| W-WS-1 | `POST /api` with `scanLibrary` returns buildings array | DesignerServerTest |
| W-WS-2 | `POST /api` with `listBuildings` dispatches to DesignerServer | DesignerServerTest |
| W-WS-3 | `GET /` returns 200 with HTML containing "BIM Designer" | DesignerServerTest |

**Test:** `DesignerServerTest` — 21/21 GREEN. 3 Web UI witnesses (W-WS-1..3).

---

## §30 — Core Engine TODO (S59+)

> **Goal:** A user builds a DemoHouse end-to-end using Work Orders (C_OrderLine →
> M_Product → BOM explosion), driven from either the HTML UI or the Bonsai 10-panel
> stack. Both surfaces control the same pipeline. YAML is retired — the Work Order
> IS the building specification.

### 30.1 Design Paradigm Shift: YAML → Work Order

The pipeline no longer starts from a YAML classify file. It starts from a
**C_Order** (Work Order) containing **C_OrderLines**, each referencing an
**M_Product** from the component library. The compiler explodes the BOM tree
exactly as iDempiere Manufacturing does: Order → OrderLine → BOM → sub-assemblies
→ leaf elements. This is the ERP Manufacturing BOM pattern applied to construction.

**What this means for the Designer:**
- `createNew` = create a C_Order with one C_OrderLine (the BUILDING product)
- `bomDrop` = explode that product's BOM tree into the viewport
- Swap/add/remove = modify C_OrderLines, recompile
- `promote` = write the finalized order back to BOM.db as a new M_BOM
- YAML files remain only as onboarding scripts for legacy IFC buildings

### 30.2 Bidirectional Sync: HTML ↔ Bonsai

**Requirement:** Any action in the HTML UI must be reflected in Bonsai's viewport
and 10-panel stack, and vice versa. The Java server is the single source of truth.

| Direction | Mechanism | Status |
|-----------|-----------|--------|
| HTML → Server | `POST /api` (existing) | DONE |
| Server → Bonsai | `pollCommands` queue (§29.6) | DONE (loadOutput, applyScheme) |
| Bonsai → Server | `selectionChanged` via timer | DONE |
| Server → HTML | SSE `EventSource` broadcast | DONE (compileComplete) |
| **HTML → Bonsai** | Server queues → Bonsai polls via HTTP timer | **DONE** (S59) — `_poll_commands_timer` in operator.py |
| **Bonsai → HTML** | Server SSE-broadcasts state change → browser updates | DONE — SSE `selectionChanged` + `compileComplete` |

**Core actions that must flow both ways:**

| Action | HTML trigger | Bonsai trigger | Server handler |
|--------|-------------|----------------|----------------|
| Select element | Click BOM tree row | Click object in viewport | `selectionChanged` |
| Create OrderLine | "Place" button in BOM Chooser | Click-to-Place operator | `placeItem` |
| Swap product | Dropdown in BOM Outliner | Right-click → Swap | `updateOrderLine` |
| Compile | "Compile" button (tab 1) | Compile operator | `compile` / `completeIt` |
| Change dimension | Slider in property panel | Bbox resize (Design Mode) | `snap` |
| Save variant | "Save" button | Ctrl+S operator | `save` |

### 30.3 Bonsai 10-Panel Stack (must remain fully functional)

The 10 panels map to nD BIM dimensions. All must continue working as standalone
Bonsai UI — the HTML is an alternative surface, not a replacement.

| Panel | nD | Core function | HTML Tab (aligned S60) |
|-------|----|---------------|------------------------|
| 1 — Order | 1D | C_Order status, compile, BOM Drop | **1D Order** — BOM tree + order lines + ASI + DocAction |
| 2 — Spatial | 2D | Storey browser, containment | **2D Spatial** — storey browser + containment tree + import/export |
| 3 — Geometry | 3D | Placement, AABB, mesh preview | **3D Geometry** — federation status + building list + compile |
| 4 — Schedule | 4D | Construction sequence (Gantt) | **4D Schedule** — discipline phases + dependencies (placeholder) |
| 5 — Cost | 5D | Material + labour + equipment | **5D Cost** — material/labour/equipment breakdown (placeholder) |
| 6 — Sustainability | 6D | Carbon, embodied energy | **6D Sustain** — CO2e + mass + intensity (placeholder) |
| 7 — Facility Mgmt | 7D | Maintenance, lifecycle | **7D Facility** — asset lifecycle + intervals (placeholder) |
| 8 — Validation | 8 | Advisories, compliance | **8 Validate** — compliance cards + advisory table + portfolio |
| 9 — BOM Outliner | 9 | BOM tree, swap, add/remove | **9 BOM** — BOM outliner + NLP query + product search |
| 10 — Color Studio | 10 | Discipline colours, schemes | **10 Colour** — Bonsai sync + 4 palettes + apply to viewport |

### 30.4 DemoHouse End-to-End (acceptance test)

**The DM (DemoHouse_2BR) must be buildable from either surface:**

1. User opens HTML UI (or Bonsai panel 1)
2. Selects "Create New" → DM building type, 2BR/1BT
3. Server creates C_Order + 1 C_OrderLine(`BUILDING_DM_STD`)
4. `bomDrop` explodes BOM → 60 elements appear in viewport
5. User swaps a room product via BOM Outliner (panel 9 or tab 9)
6. Recompile → updated geometry
7. Save variant → recall → verify identical output
8. Promote → new M_BOM written to BOM.db

**Witness claims (to implement):**

| Witness | Claim | Status |
|---------|-------|--------|
| W-WO-1 | DemoHouse created from C_OrderLine (no YAML) | **DONE** (S59) — WorkOrderCompileTest 6/6 GREEN |
| W-WO-2 | HTML compile triggers Bonsai viewport update | WIRED — afterCompile queues loadOutput; needs pollCommands timer |
| W-WO-3 | Bonsai selection reflected in HTML BOM Outliner | WIRED — SSE broadcast works; needs Bonsai selection listener |
| W-WO-4 | Product swap via HTML updates Bonsai geometry | WIRED — swapProduct API works; needs recompile + pollCommands |
| W-WO-5 | Save/recall round-trip produces identical element count | Backend DONE — save/recall/promote APIs implemented |

### 30.5 Priority Stack (core engine only)

| # | Item | Blocks | Status |
|---|------|--------|--------|
| 1 | **Work Order compile path** — `bomDrop` → `completeIt` → output.db | W-WO-1 | **DONE** (S59) — 60 elements, equivalence proof |
| 2 | **HTML → Bonsai command flow** — compile in HTML updates viewport | W-WO-2, W-WO-4 | **DONE** (S59) — pollCommands timer + db_loader |
| 3 | **Bonsai → HTML state flow** — selection/edit in Bonsai updates HTML | W-WO-3 | Server SSE DONE. Need: Bonsai selection listener |
| 4 | **BOM Outliner sync** — tree view in both surfaces, swap triggers recompile | W-WO-4 | BOM tree renders after bomDrop. Need: swap UI in tree |
| 5 | **Save/Recall round-trip** — variant persistence from either surface | W-WO-5 | Backend DONE. Need: HTML save/recall buttons wired |
| 6 | **ERP Model Alignment** — compiler walks C_OrderLine, not C_DocType | S60 | Spec: [S60_ERP_ALIGNMENT.md](archive/S60_ERP_ALIGNMENT.md). 10 code changes, 5 schema gaps (U2-U6) |

**Not in scope (deferred):**
- Embedding search (§25)
- Ghost drag / chain interaction (WF-12..25)
- Multi-user changelog (WF-21..25)
- nD stubs (4D-7D return real data only when DAO is wired)
- Portfolio/Kanban/Scorecard back-office views

### 30.6 UAT Gap Analysis — OrderLine Validation Model (S59→S60)

The C_OrderLine schema is structurally ready (16 columns) but semantically
incomplete for the validator to work end-to-end. This section documents the
gaps that must be closed before the DemoHouse acceptance test (§30.4) can
exercise the full BOM Drop → configure → validate → compile → promote flow.

#### 30.6.1 User Scenarios That Must Work

| # | Scenario | User Action | Backend Must Do |
|---|----------|-------------|-----------------|
| U1 | **Swap roof** | Select roof OrderLine by category (RF), pick pitched roof from library | `swapProduct(lineId, FK_DG_STR)` → recompile. **Works today.** |
| U2 | **Add FP discipline** | Add a Fire Protection OrderLine to the order | Insert C_OrderLine with `Discipline='FPR'`, link to `ad_space_type_mep_bom` for per-room sprinkler placement. **Gap: no Discipline column.** |
| U3 | **Set compliance** | Choose jurisdiction (MY/US/UK) for validation | `PlacementValidator.activate(jurisdiction)` selects AD_Val_Rule set. **Gap: no jurisdiction on C_Order.** |
| U4 | **Set occupancy** | Specify Light Hazard / Residential for NFPA 13 rules | AD_Val_Rule_Occupancy join filters rules. **Gap: no occupancy_class on Order.** |
| U5 | **Validate before compile** | Click Approve → see PASS/BLOCK per rule | Hydrate `PlacementRequest` from `C_OrderLine + M_Product`, run `validateAll()`, persist to `W_Validation_Result`. **Gap: no hydration DAO.** |
| U6 | **Override a WARN** | Engineer-approve a specific violation | `AD_Val_Rule_Exception` table exists but not wired. **Gap: no override UI.** |

#### 30.6.2 Schema Gaps — Missing Columns on C_OrderLine

| Column | Type | Why Needed | Source |
|--------|------|-----------|--------|
| `ifc_class` | TEXT | Validator needs it for clash rules, mined dimension rules, discipline resolution | `M_Product.ifc_class` or `m_bom_line.child_element_type` |
| `Discipline` | TEXT DEFAULT 'ARC' | Cross-discipline clash rules, occupancy-filtered rules, spatial predicates | `m_bom.bom_category` or `ad_ifc_class_map` |

#### 30.6.3 Schema Gaps — Missing Context on C_Order

| Column | Type | Why Needed | Current State |
|--------|------|-----------|---------------|
| `jurisdiction` | TEXT DEFAULT 'MY' | Selects AD_Val_Rule set (UBBL vs IRC vs UK NDSS) | `W_BuildingConfig.jurisdiction` exists but not on C_Order |
| `occupancy_class` | TEXT | Filters NFPA 13 sprinkler spacing (LH=4600mm, OH1=3500mm, OH2=2800mm) | `AD_Occupancy_Class` table seeded (6 rows) but no link to Order |

#### 30.6.4 Integration Gaps — Validator ↔ OrderLine Bridge

| Gap | Description | Fix |
|-----|-------------|-----|
| **No hydration DAO** | No code to build `PlacementRequest` from `C_OrderLine + M_Product` | Write `OrderLineValidator.buildRequest()` — resolve ifc_class, discipline, dimensions from product |
| **No result persistence** | `PlacementValidatorImpl.validate()` returns in-memory verdict only | Wire `W_Validation_Result` INSERT on `DocAction.validateLine()` |
| **No batch spatial context** | `validateBatch()` processes elements independently, cannot check sprinkler spacing or MEP clearances | Pass storey grouping + AABB context to SpatialPredicates |
| **InferenceEngine not wired** | Dependency-ordered rule evaluation (Kahn's sort) exists but not called from OrderLine validation | Connect to PlacementValidatorImpl for rule chains (e.g., room size depends on occupancy) |

#### 30.6.5 Lookup Chain — How FP Discipline Gets Added

Current data path for fire protection:
```
ad_space_type_mep_bom (ERP.db)
  → WHERE discipline = 'FPR' AND space_type = 'BEDROOM'
  → Returns: element_type = 'SPRINKLER', qty_per_room = 1, ifc_class = 'IfcFireSuppressionTerminal'

MEPBomAD.getBOM(discipline, spaceType)     -- query layer READY
PlacementValidatorImpl.validateBatch()     -- validation layer EXISTS
```

**Missing link:** No C_OrderLine row is created for the FP discipline.
The BOM Drop explodes the structural BOM only — FP elements come from
`ad_space_type_mep_bom`, not from `m_bom`. The bridge must:
1. User adds an FP OrderLine (or toggles FP discipline on the order)
2. Server queries `ad_space_type_mep_bom` for each room in the order
3. Server inserts child C_OrderLines (sprinkler per room) under the FP line
4. Compile picks up FP OrderLines and places sprinklers

#### 30.6.6 Migration Plan

See [S60_ERP_ALIGNMENT.md](archive/S60_ERP_ALIGNMENT.md) for the full 10-item code change list.

| Migration | DDL | Blocks | S60 Item |
|-----------|-----|--------|----------|
| **W003** | `ALTER TABLE C_OrderLine ADD COLUMN Discipline TEXT DEFAULT 'ARC';` | U2, U5 | #1 |
| **W004** | `ALTER TABLE C_Order ADD COLUMN jurisdiction TEXT DEFAULT 'MY';` | U3 | #1 |
| | `ALTER TABLE C_Order ADD COLUMN occupancy_class TEXT;` | U4 | #1 |
| **W005** | `CREATE TABLE AD_Val_Rule_Exception ...` | U6 | #8 |

**Architectural changes (S60):**
- Compiler walks C_OrderLine tree, not C_DocType (#2)
- run_RosettaStones.sh uses bomDrop + completeIt path (#4)
- The former M_BomCategory, now M_Product_Category (#6)
- OrderLineHydrationDAO bridges C_OrderLine → PlacementRequest (#7)

#### 30.6.7 UAT Acceptance Criteria

The DemoHouse acceptance test (§30.4) passes when:

1. BOM Drop → 60 elements (structural) **[DONE — W-WO-1]**
2. Swap roof → pitched roof compiles correctly **[DONE — W-DM-TC4-1]**
3. Add FP discipline → sprinklers placed per room (NFPA 13 spacing)
4. Set jurisdiction=MY → UBBL rules applied, bedroom min area checked
5. Approve → validation results persisted, blockers shown in UI
6. Complete → compile → output.db with STR + FP elements
7. Save/recall → identical element count **[backend DONE — needs UI test]**
8. Promote → m_bom entries written, order frozen

### 30.7 M_Product_Category — Hierarchical Product Catalog (S61 spec)

<!-- Implementing SpecsAnalysis.txt §3 -->
> **iDempiere parallel:** `M_Product_Category` — flat product classification.
> User browses by category to find products when creating or swapping an OrderLine.
> The cascade (building→floor→room→leaf) is expressed by the BOM tree (M_BOM → M_BOM_Line),
> not by a category tree. Categories stay flat per iDempiere standard.

#### 30.7.1 Schema

```sql
-- component_library.db
CREATE TABLE M_Product_Category (
    M_Product_Category_ID  TEXT PRIMARY KEY,
    Parent_Category_ID     TEXT,          -- **DEPRECATED — to be dropped (see DATA_MODEL.md §7.6)**
    Name                   TEXT NOT NULL,
    Description            TEXT,
    IsActive               INTEGER DEFAULT 1
);

-- Add FK column to existing M_Product
ALTER TABLE M_Product ADD COLUMN M_Product_Category_ID TEXT
    REFERENCES M_Product_Category(M_Product_Category_ID);
```

#### 30.7.2 Category Hierarchy (derived from extraction data)

Data source: `m_bom.bom_category` from TE (48K elements, 8 disciplines) and
SH/FK (architectural). The hierarchy is EXTRACTED, not invented.

```
ALL (root)
├── ARC — Architecture (268 TE products, 24 SH products)
│   ├── ARC_WALL — Walls (IfcWall)
│   │   ├── ARC_WALL_EXT — Exterior (brick, block, curtain)
│   │   └── ARC_WALL_INT — Interior (partition, stud)
│   ├── ARC_DOOR — Doors (IfcDoor)
│   ├── ARC_WINDOW — Windows (IfcWindow)
│   ├── ARC_SLAB — Slabs/Floors (IfcSlab)
│   ├── ARC_ROOF — Roofing (IfcRoof)
│   ├── ARC_COVER — Ceilings (IfcCovering)
│   └── ARC_FURN — Furniture (IfcFurnishingElement)
├── STR — Structural (29 TE products)
│   ├── STR_BEAM — Beams (IfcBeam)
│   ├── STR_COL — Columns (IfcColumn)
│   └── STR_MEMBER — Members/Rafters (IfcMember)
├── FP — Fire Protection (22 TE products)
│   ├── FP_HEAD — Sprinkler heads (IfcFlowTerminal)
│   ├── FP_PIPE — Risers/laterals (IfcPipeSegment)
│   └── FP_ALARM — Alarms (IfcAlarm)
├── ELEC — Electrical (50 TE products)
│   ├── ELEC_LIGHT — Light fixtures (IfcLightFixture)
│   ├── ELEC_OUTLET — Outlets/receptacles (IfcOutlet)
│   ├── ELEC_SWITCH — Switches (IfcSwitchingDevice)
│   └── ELEC_CABLE — Cable trays (IfcCableSegment)
├── CW — Cold Water (50 TE products)
│   ├── CW_PIPE — Pipes (IfcPipeSegment)
│   ├── CW_FITTING — Fittings (IfcPipeFitting)
│   └── CW_TERMINAL — Taps/valves (IfcFlowTerminal)
├── SP — Sanitary/Plumbing (21 TE products)
│   ├── SP_FIXTURE — Basins/WC (IfcSanitaryTerminal)
│   └── SP_PIPE — Waste pipes (IfcPipeSegment)
├── ACMV — HVAC (60 TE products)
│   ├── ACMV_DUCT — Ducts (IfcDuctSegment)
│   ├── ACMV_DIFF — Diffusers (IfcAirTerminal)
│   └── ACMV_FITTING — Fittings (IfcDuctFitting)
└── LPG — Gas (18 TE products)
    └── LPG_PIPE — Gas pipes (IfcPipeSegment)
```

Level 1 = discipline (`m_bom.bom_category`). Level 2 = IFC class grouping.
Level 3 = product type (exterior/interior, fixture subtype).

**RE (room-driven) cascade** — expressed by the BOM tree, not by category hierarchy:

```
M_BOM "SH" (M_Product_Category=RE)
  └─ M_BOM_Line → M_BOM "GF" (M_Product_Category=GF)
       └─ M_BOM_Line → M_BOM "LIVING" (M_Product_Category=LIVING)
            ├─ M_BOM_Line → SOFA_001 (leaf, M_Product_Category=IFC_FURNISHING)
            └─ M_BOM_Line → TABLE_001 (leaf, M_Product_Category=IFC_FURNISHING)
```

RE cascade is room-driven (swap pools at room level), CO cascade is
discipline-driven (ARC, STR, FP sub-trees). Both expressed by BOM tree.
M_Product_Category at each node is a flat tag, not a tree node.

#### 30.7.3 Population Strategy

Categories are derived from extraction data, not invented:

1. **Level 1 (discipline):** Direct from `m_bom.bom_category` — already in every BOM
2. **Level 2 (IFC class):** Direct from `M_Product.ifc_class` — already on every product
3. **Level 3 (product type):** From naming convention (e.g. `Wall-Ext` vs `Wall-Partn`,
   `E_Light` vs `E_Switch`) — pattern-matched, not manual

**Onboarding script:** `scripts/onboard_products.py` (to be created)
- Reads TE_BOM.db `m_bom_line` + `m_bom.bom_category`
- Reads component_library.db existing products
- For each TE product not in library: INSERT M_Product + assign M_Product_Category_ID
- Geometry: copy from TE extraction (component_definitions + component_geometries)

#### 30.7.4 FP Trial — First MEP Discipline via DocEvent

**Goal:** Prove the DocEvent placement chain for FP (Fire Protection) end-to-end.
The BOM does NOT record every pipe length — it records abstract ingredients
(SPRINKLER, RISER). The engine infers quantity from room area, picks the product,
and the compiler determines actual dimensions from the containing space.

**The 5-table chain** (DISC_VALIDATE_SRS.md §9.1):

```
User enables FP for DemoHouse
  → ad_space_type_mep_bom: BEDROOM needs 0.07 sprinklers/m²
  → ad_element_mep: SPRINKLER → IfcFireSuppressionTerminal, host=CEILING
  → ad_fp_coverage: LIGHT hazard → max_spacing=4.6m, coverage=18.6m²
  → M_Product: sprinkler_pendant → width, depth, height (from library)
  → component_definitions: geometry_hash → LOD mesh
```

**What TE extracted:** 22 FP products at many different lengths/placements.
**What the BOM records:** Abstract ingredients (SPRINKLER, RISER, LATERAL).
The BOM is not a catalog of every length — it's a recipe of common elements.
The compiler computes spatial data (dx/dy/dz, actual length) from the
containing space at compile time.

**Steps:**
1. Seed `ad_space_type_mep_bom` rows for DemoHouse room types (from TE mining)
2. Seed `ad_element_mep` rows for FP elements (SPRINKLER, ALARM)
3. Seed `ad_fp_coverage` for LIGHT hazard class
4. Onboard 2-3 FP products into component_library.db M_Product (sprinkler + alarm)
   with M_Product_Category = FP_HEAD / FP_ALARM
5. Copy geometry from TE extraction (component_definitions + component_geometries)
6. Rosetta Stone pipeline (`classify_dm.yaml`): enable FP discipline → DocEvent places sprinklers per room
7. CompleteIt → verify IfcFireSuppressionTerminal appears in output.db

**User swap scenario:** After DocEvent places a pendant sprinkler, user can
swap it for an upright sprinkler — same category (FP_HEAD), different product.
The category constrains the swap list.

**Witness:** W-FP-TRIAL-1: DocEvent FP placement via 5-table chain.

**Success criteria:** DemoHouse compiles with FP elements placed by rules,
not hardcoded. Each room gets sprinklers per ad_space_type_mep_bom schedule.

**S63 findings (Task 4A):**
- DM compilation needs `FP_PIPE_ASSEMBLY` BOM + `m_attribute` seed (spacing, z_offset, diameter) — BuildingWriter calls `BOMRuleAD.loadPlacementParams("FP_PIPE_ASSEMBLY","MAIN")` when FireProtectionResolver detects sprinklers. Without it the compile fails. *(S67: DemoHouseCompileTest removed; DM compiles via standard Rosetta Stone pipeline.)*
- FP elements currently resolve as discipline='MEP' (IFC class fallback: IfcFireSuppressionTerminal → MEP_TERMINAL → MEP). Finer FP discipline requires either: (a) FP-specific SET BOMs with `bom_category='FP'`, or (b) the addDiscipline API setting `C_OrderLine.Discipline='FPR'` explicitly — which is the Task 4B DocEvent path above.
- `m_bom.bom_type` CHECK constraint is `('BUILDING','FLOOR','ROOM','SET','ITEM')` — no 'ASSEMBLY'. FP pipe assemblies should use 'SET'.

#### 30.7.5 Two Browsing Modes — ARC vs MEP

**ARC (manual selection):** User browses the category tree to pick products.

```
User clicks "Swap Product" on a wall OrderLine
  → Category picker: [ARC_WALL ▼]
    → Sub-category: [ARC_WALL_EXT ▼]  [ARC_WALL_INT ▼]
    → Product list: Wall-Ext_102Bwk-75Ins-100LBlk, ...
    → User picks product → UPDATE C_OrderLine.family_ref
```

The `browseItems()` API already exists (`DesignerAPIImpl.java`). It needs a
`category` filter parameter to query `M_Product WHERE M_Product_Category_ID = ?`.

**MEP (rule-driven placement via DocEvent):** User toggles a discipline ON.
The system infers what goes where from validation rules + room geometry.

```
User enables: [✓ FP] for this building
  → DocEvent engine (DocAction_SRS §1.3):
    1. AD_Val_Rule WHERE discipline='FP' AND jurisdiction=order.jurisdiction
       → spacing rules (NFPA 13 / UBBL), coverage, occupancy class
    2. ad_space_type_mep_bom WHERE space_type=room.type AND discipline='FP'
       → product_id (which sprinkler for this room type)
    3. Room AABB from ad_room_boundary
       → pitch = min(max_spacing, dim / ceil(dim / typical_spacing))
       → grid positions computed per room
    4. INSERT C_OrderLine per computed position
       → family_ref from ad_space_type_mep_bom (not user-selected)
       → dx/dy/dz from computed grid
    5. Tier 1 validate each placement against same rules
       → INSERT W_Validation_Result per C_OrderLine
```

**No manual product picking for MEP.** The user says "this building needs FP."
The rules + containing space determine the product, quantity, and position.
This is the DocEvent path — validation rules ARE the placement engine.

---

*References:
[BIM_Designer.md](BIM_Designer.md) (architecture, §17 Design Mode, §18 UI Strategy) |
[BIM_Designer_UserGuide.md §13](BIM_Designer_UserGuide.md) (Web UI spec — tab layout, tech stack) |
[G4_SRS.md](G4_SRS.md) (work_output.db, Save/Recall/Promote sequences) |
[DocValidate.md](DocValidate.md) §15 (PlacementValidator, AD_Val_Rule) |
[TestArchitecture.md](TestArchitecture.md) (traceability matrix, witness convention) |
[BIM_COBOL.md](BIM_COBOL.md) §20 (spatial predicate verbs) |
[BlenderBridge.md](BlenderBridge.md) (Java-smart/Python-dumb, incremental viewport) |
[MANIFESTO.md](MANIFESTO.md) (iDempiere M_Product/C_Order model) |
[WorkOrderGuide.md](WorkOrderGuide.md) (pipeline flow, invention boundary)*
