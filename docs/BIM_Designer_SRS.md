# BIM Designer SRS — UX Requirements & User Journeys

**Version:** 1.0 (2026-03-19)
**Depends on:** [BIM_Designer.md](BIM_Designer.md) §17-18, [G4_SRS.md](G4_SRS.md), [DocValidate.md](DocValidate.md) §15
**Scope:** Testable functional requirements, user journey acceptance criteria, UX edge cases

> This document complements BIM_Designer.md (architecture/vision, 2914 lines) with SRS-grade
> rigour: numbered requirements, acceptance criteria, latency contracts, error states, and
> concrete user journeys. BIM_Designer.md says WHAT and WHY. This document says HOW TO VERIFY.

---

## 1. Requirement Taxonomy

| Prefix | Domain | Count |
|--------|--------|-------|
| UX-F | Functional (user-facing behaviour) | 28 |
| UX-N | Non-functional (latency, capacity) | 10 |
| UX-E | Error/edge-case handling | 12 |

Priority: **P0** = must-have for first usable demo, **P1** = needed for daily use, **P2** = polish.

### 1.1 Foundation Guarantee — Zero Delta Compilation

All three Rosetta Stone buildings compile with **+0.00% volume delta** (G2-VOLUME):
SH (55), DX (1,099), TE (48,428). The BOM→compile→output pipeline is lossless.

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
| UX-F-08 | §17.13 | DesignerAPI.snap | panel.py (slider) | — | SPEC ONLY |
| UX-F-09 | §17.6 | RoomLayoutGenerator | panel.py | — | SPEC ONLY |
| UX-F-10 | §17.9 | — | operator.py | — | SPEC ONLY |
| UX-F-11 | §17.15 | RoomLayoutGenerator | operator.py | — | SPEC ONLY |
| UX-F-12 | §17.17 | — | design_bbox.py (mark_committed) | — | IMPLEMENTED |
| UX-F-13 | §17.10.2 | DesignerAPI.save | operator.py (save) | — | SPEC ONLY (stub) |
| UX-F-14 | §17.10.5 | DesignerAPI.listVariants | panel.py | — | SPEC ONLY (stub) |
| UX-F-15 | G4_SRS §2.3 | DesignerAPI.recall | operator.py (recall) | — | SPEC ONLY (stub) |
| UX-F-16 | §17.10.2 | DesignerAPI.approve | operator.py | — | SPEC ONLY |
| UX-F-17 | §17.10.4 | DesignerAPI.promote | operator.py (promote) | — | SPEC ONLY (stub) |
| UX-F-18 | §18.4 | PlacementValidatorImpl | panel.py (status strip) | — | SPEC ONLY |
| UX-F-19 | §18.4 | ValidationVerdict | panel.py | — | SPEC ONLY |
| UX-F-20 | §18.4, §17.13 | — | panel.py, design_bbox.py | — | SPEC ONLY |
| UX-F-21 | §18.2 | PlacementValidatorImpl | panel.py (dropdown) | — | SPEC ONLY |
| UX-F-22 | §17.18.1 | DesignerDAO (browseItems) | panel.py (chooser) | — | SPEC ONLY |
| UX-F-23 | §17.18.3 | DesignerDAO (AABB filter) | panel.py | — | SPEC ONLY |
| UX-F-24 | §17.18.4 | DesignerAPI | operator.py | — | SPEC ONLY |
| UX-F-25 | §17.18.5 | DesignerAPI | panel.py | — | SPEC ONLY |
| UX-F-26 | §17.11 | — | panel.py, design_bbox.py | — | SPEC ONLY |
| UX-F-27 | §17.19 | — | panel.py | — | SPEC ONLY |
| UX-F-28 | §17.9 | — | operator.py (UNDO) | — | IMPLEMENTED (basic) |

**Summary:** 8 IMPLEMENTED, 20 SPEC ONLY, 0 PASS (no UX-specific witnesses yet).

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
> with tack dx/dy/dz, verb_ref, allocated dimensions) and `YAMLGuide.md`
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

### 11.4 Impact on BIM_COBOL Spatial Predicates

The spatial predicates in BIM_COBOL §20 specced AABB fallbacks for several
operations. With output.db FKs, they can upgrade:

| Predicate | BIM_COBOL §20 spec | Upgraded implementation |
|-----------|-------------------|----------------------|
| `HOST_OF` | "AABB containment in 2/3 axes. Upgrades to FK when R21 lands" | **R21 already landed** — `assembly_components.assembly_guid` IS the host FK. No upgrade needed — use it now. |
| `WITHIN` | "R-tree: minX >= c.minX AND maxX <= c.maxX" | Can also use `rel_contained_in_space` for element→space containment. R-tree for arbitrary containment. |
| `SAME_LEVEL` | "Z-band tolerance" | `rel_contained_in_space` → `spatial_structure.parent_guid` gives exact storey match. Z-band is fallback only. |
| `ALONG_PATH` | "Walk ad_element_dependency" | `system_edges` + `system_nodes` gives MEP connectivity graph directly (for TE: populated). |

### 11.5 Impact on BIM Designer UX

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

### 11.1 Designer-Specific Log Events

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

### 11.2 Log Initialisation Pattern

```java
// In DesignerServer.start()
BIMLogger.initForRun("designer_server");
BIMLogger.setLevel(BIMLogger.Level.FINE);  // FINE for designer — captures snap/validate detail
BIMLogger.info("DESIGNER", "Server started on port {}", port);
```

Log file: `logs/pipeline_designer_server_{timestamp}.log`

### 11.3 Why This Matters for UX

1. **Latency tracking.** Every action logs elapsed time. If UX-N-01 (createNew < 200ms)
   regresses, the log shows it without needing a profiler.
2. **Connection diagnostics.** UX-E-01/E-02 (server not running, disconnection) are
   diagnosed from the log, not from the user's description.
3. **Save verification.** UX-E-03 (crash during save) — the log shows whether the
   save transaction committed before the crash.
4. **Compliance audit.** Every validate call logs per-rule verdicts at FINE level.
   An auditor can verify compliance from the log alone.

---

*References:
[BIM_Designer.md](BIM_Designer.md) (architecture, §17 Design Mode, §18 UI Strategy) |
[G4_SRS.md](G4_SRS.md) (work_output.db, Save/Recall/Promote sequences) |
[DocValidate.md](DocValidate.md) §15 (PlacementValidator, AD_Val_Rule) |
[TestArchitecture.md](TestArchitecture.md) (traceability matrix, witness convention)*
