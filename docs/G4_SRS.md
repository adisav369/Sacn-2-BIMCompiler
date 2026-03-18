# G-4 SRS — work_output.db + Validation Engine

**Version:** 1.1 (2026-03-19, session 22 — master-detail DocStatus, AP gate, pointer variants)
**Depends on:** [BIM_Designer.md](BIM_Designer.md) §17.10, [DocValidate.md](DocValidate.md) §15, [ConstructionAsERP.md](ConstructionAsERP.md) §2-3
**Pre-requisite:** TACK-FIX (see [TACK_FIX_SPEC.md](TACK_FIX_SPEC.md))

---

## 1. Scope

G-4 delivers:
1. **work_output.db** — self-contained design workspace (DDL: `migration/W001_work_output_schema.sql`)
2. **ConstructionModelSpawner** — populates work_output.db from BOM templates
3. **WorkOutputDAO** — Save/Recall/listVariants persistence
4. **Wire protocol** — already dispatched (save/recall/listVariants/promote in DesignerServer)
5. **Test specs** — unit + integration for all new code

**Out of scope (G-5+):** BOM Chooser, ambient compliance, assembly builder.

---

## 2. Sequence Diagrams

### 2.1 CreateNew + Spawn

```
User clicks "Create New" in Bonsai panel
│
▼
operator.py: BIM_OT_designer_create_new.execute()
│
├── TCP → {"action":"createNew", "buildingName":"MyHouse", "buildingType":"DM",
│          "jurisdiction":"MY", "siteWidthMm":12000, "siteDepthMm":8000,
│          "numBedrooms":2, "numBathrooms":1, "storeys":1}
│
▼
DesignerServer.dispatch("createNew")
│
▼
DesignerAPIImpl.createNew(request)
│
├── 1. Load DM BOM templates from {PREFIX}_BOM.db
│      → DesignerDAO.loadBomTree("BUILDING_DM_STD")
│
├── 2. RoomLayoutGenerator.generate(request)
│      → Deterministic site → storey → room partitioning
│      → Returns List<DesignBBox> (draft bboxes)
│
├── 3. ConstructionModelSpawner.spawn(workConn, bomConn, compConn, ...)  ← NEW
│      │
│      ├── 3a. CREATE work_output.db (apply W001 migration)
│      │
│      ├── 3b. INSERT W_BuildingConfig (embedded YAML + identity)
│      │
│      ├── 3c. INSERT C_Order (building header, DocStatus='DR')
│      │
│      ├── 3d. Walk BOM tree (BOMWalker):
│      │   For each m_bom encountered:
│      │     → INSERT C_OrderLine (family_ref, host_type, dx/dy/dz, AABB)
│      │     → INSERT CO_EmptySpaceLine (tack_from, capacity)
│      │     → INSERT M_AttributeSetInstance + M_AttributeInstance (defaults)
│      │
│      ├── 3e. INSERT PP_Order_Node (default routing)
│      │   RE: Foundation → Frame → Envelope → MEP → Finishes
│      │
│      ├── 3f. Run PlacementValidator (READONLY mode)
│      │   → INSERT W_Validation_Result per line
│      │   → Attach validation_status to each C_OrderLine
│      │
│      └── 3g. INSERT W_Variant (initial snapshot, label="v0")
│
├── 4. Return CreateNewResponse { bboxes, outputDbPath, orderLineCount }
│
▼
Python: client receives JSON → design_bbox.py renders bboxes
```

### 2.2 Save (Sub-Work-Order Pattern)

```
User clicks "Save" in Design Mode
│
▼
operator.py: BIM_OT_designer_save.execute()
│
├── Collect current bboxes from scene
├── TCP → {"action":"save", "buildingId":"MyHouse",
│          "bboxes":[...], "variantLabel":"wide-rooms"}
│
▼
DesignerServer.dispatch("save")
│
▼
DesignerAPIImpl.save(buildingId, bboxes, variantLabel)
│
├── 1. Open work_output.db connection
│
├── 2. Complete current sub-work-order (if one is active IP)
│      → SET DocStatus = 'CO' on active sub-C_Order
│      → SET W_Variant.is_active = 0 for previous active variant
│
├── 3. Create new sub-C_Order (the saved version)
│      → INSERT C_Order (Parent_Order_ID = master, DocStatus = 'CO')
│      → INSERT C_OrderLine rows from bboxes (copy current state)
│        For each bbox:
│          → INSERT new C_OrderLine (family_ref, dx/dy/dz, ASI FK)
│      → INSERT M_AttributeSetInstance + M_AttributeInstance for overrides
│      → INSERT CO_EmptySpaceLine (mirror tack values from OrderLines)
│
├── 4. INSERT W_Variant pointer
│      → C_Order_ID = new sub-order ID (NOT master)
│      → is_active = 1 (this is the current variant)
│      → orderline_count = COUNT(*) from new sub-order
│      → compliance_status = latest validation run status
│      → NO snapshot_json — sub-order's tables ARE the data
│
├── 5. Return SaveResponse { variantId, subOrderId, outputDbPath }
│
▼
Python: client shows "Saved as 'wide-rooms'"
```

**Key difference from snapshot model:** The sub-C_Order's C_OrderLine rows
ARE the version data. No JSON blob duplication. Each variant is a proper
iDempiere document with its own lifecycle — queryable, reportable, auditable.

### 2.3 Recall (Activate Previous Sub-Work-Order)

```
User picks variant from list
│
▼
operator.py: BIM_OT_designer_recall.execute()
│
├── TCP → {"action":"recall", "buildingId":"MyHouse", "variantId":"3"}
│
▼
DesignerAPIImpl.recall(buildingId, variantId)
│
├── 1. Look up W_Variant → get sub-C_Order_ID
│
├── 2. Spawn new sub-work-order (DR) by copying from recalled version
│      → INSERT C_Order (Parent_Order_ID = master, DocStatus = 'DR')
│      → COPY C_OrderLine rows from recalled sub-order to new sub-order
│      → COPY M_AttributeSetInstance + M_AttributeInstance
│      → COPY CO_EmptySpaceLine
│      → Previous sub-orders stay CO (immutable history)
│
├── 3. INSERT W_Variant pointer for new sub-order
│      → is_active = 1, label = recalled variant label + " (recalled)"
│      → SET is_active = 0 on all other W_Variant rows for this master
│
├── 4. Return RecallResponse { bboxes, variantLabel, newSubOrderId }
│
▼
Python: design_bbox.py replaces scene with recalled bboxes
```

**No data destruction:** Recall does NOT delete or overwrite. It copies the
recalled version into a fresh sub-order (DR). The original stays CO. This is
the iDempiere reversal pattern: void the old, create the new. Full audit trail.

### 2.4 Promote to BOM

```
User clicks "Promote" → confirmation dialog
│
▼
operator.py: BIM_OT_designer_promote.execute()
│
├── TCP → {"action":"promote", "buildingId":"MyHouse",
│          "owner":"red1", "complianceRef":"UBBL 2012 s33",
│          "provenance":"GENERATIVE", "bboxes":[...]}
│
▼
DesignerAPIImpl.promote(request)
│
├── 0. Pre-check: DocStatus must be 'AP' (Approved)
│      → If DocStatus != 'AP' → return PromoteResponse(success=false, error="not approved")
│      → Approval is a separate action that validates compliance + dangles
│
├── 1. Pre-check: PlacementValidator.validateAll() in ACTIVE mode (re-verify)
│      → If ANY BLOCK → return PromoteResponse(success=false, error="validation failed")
│
├── 2. Pre-check: dangle detection
│      → For each C_OrderLine.family_ref:
│        SELECT COUNT(*) FROM M_Product WHERE M_Product_ID = family_ref
│        UNION
│        SELECT COUNT(*) FROM m_bom WHERE bom_id = family_ref
│      → If unresolved → return dangles list
│
├── 3. Walk C_OrderLine tree:
│      For each line:
│        → INSERT INTO m_bom (bom_id, bom_name, ...) in {PREFIX}_BOM.db
│        → INSERT INTO m_bom_line for each child
│        → entity_type = 'U' (User-created, not Dictionary)
│        → Provenance = 'GENERATIVE'
│
├── 4. Return PromoteResponse { bomEntriesCreated, dangles=[] }
│
▼
Python: "Promoted! 12 BOM entries created."
```

---

## 3. State Machine — Design Mode Lifecycle

```
```
MASTER-DETAIL MODEL — iDempiere Order + Sub-Work-Order Pattern

MASTER C_Order (building-level):
                          ┌──────────┐
                          │  BROWSE  │  ← default mode
                          │          │  listBuildings, listCategories
                          └─────┬────┘
                                │ createNew
                                ▼
                          ┌──────────┐
                          │  DESIGN  │  ← master C_Order exists, sub-orders active
                          │          │
                          └──┬───┬───┘
                             │   │
                    save/CO  │   │ approve (strict gate)
                    (sub)    │   │
                             │   ▼
                             │  ┌──────────────┐
                             │  │  APPROVED    │  ← AP: compliance + host tack + dangles
                             │  │              │     EXCLUSIVE gate for BOM creation
                             │  └─────┬────────┘
                             │        │ promote (writes to {PREFIX}_BOM.db)
                             │        ▼
                             │  ┌──────────────┐
                             │  │  PROMOTED    │  ← BOM entries created, master frozen
                             │  └──────────────┘
                             │
                             ▼
SUB-WORK-ORDERS (change sets):
    Each design change spawns a sub C_Order (detail tab / co-tab)

    ┌──────┐   drag/edit   ┌──────┐   save     ┌──────┐
    │  DR  │ ───────────▶  │  IP  │ ────────▶  │  CO  │
    │      │               │      │            │      │  ← saved to output.db
    └──────┘               └──────┘            └──────┘
     (in focus)           (actively editing)   (iteration complete)

    New change → spawn new sub-order DR (previous stays CO)
    User can recall any CO'd sub-order (version browsing)
```

DocStatus mapping:
  MASTER level:
    BROWSE    → no C_Order exists
    DESIGN    → C_Order exists, sub-orders in progress
    AP        → Approved — strict compliance gate for BOM creation
                (host tack tagging verified, dangles resolved, validation PASS)
    PROMOTED  → BOM entries created from AP'd design

  SUB-WORK-ORDER level:
    DR        → Draft — new change set spawned, in focus
    IP        → In Progress — user actively dragging/editing bboxes
    CO        → Complete — this iteration saved to output.db, frozen

Approval gate (DESIGN → AP) — STRICT, exclusive for BOM creation:
  - PlacementValidator.validateAll() must PASS (no BLOCK results)
  - All dangles resolved (every family_ref resolves to M_Product or m_bom)
  - Host tack tagging verified (every C_OrderLine has valid tack_from)
  - W-TACK-1 invariant passes (child within parent, LBD convention)
  - User explicitly approves (confirmation action, not automatic)
  - Only AP'd designs can be promoted to {PREFIX}_BOM.db

Sub-work-order lifecycle:
  - Each edit session (drag a room, resize, add line) is a sub-order
  - DR on creation (spawned in focus), IP when actively edited, CO when saved
  - CO writes the compiled result to output.db (cheap, frequent)
  - Previous CO'd sub-orders are recallable (variant list)
  - Like iDempiere MO Operation Nodes: each is a discrete step
```

---

## 4. Wire Protocol — Complete Action Table

All actions are ndjson over TCP (port 9876). Already dispatched in DesignerServer.

| Action | Request fields | Response type | Status |
|--------|---------------|---------------|--------|
| `compile` | buildingId, bomDbPath, libraryPath, outputDir | CompileResponse | Implemented |
| `compileIncremental` | buildingId, bomDbPath, ..., changes{} | CompileResponse | Implemented |
| `verb` | buildingId, verbLine | VerbResponse | Implemented |
| `createNew` | buildingName, buildingType, jurisdiction, site dims, rooms | CreateNewResponse | Stub |
| `listBuildings` | (none) | List\<BuildingTypeInfo\> | Implemented |
| `listCategories` | docSubType | List\<CategoryInfo\> | Implemented |
| `snap` | bboxes[], jurisdiction, gridMm | SnapResponse | Stub |
| `save` | buildingId, bboxes[], variantLabel | SaveResponse | **G-4: implement** |
| `recall` | buildingId, variantId | RecallResponse | **G-4: implement** |
| `listVariants` | buildingId | List\<VariantInfo\> | **G-4: implement** |
| `approve` | buildingId | ApproveResponse | **G-4: implement** |
| `promote` | buildingId, owner, complianceRef, provenance, bboxes[] | PromoteResponse | **G-4: implement** |

### 4.1 Wire Protocol Additions for G-4

**New request field on `save`:**
```json
{"action":"save", "buildingId":"MyHouse",
 "bboxes":[{"bomId":"LIVING_SET","minX":2.2,...}],
 "variantLabel":"wide-rooms",
 "workOutputDbPath":"output/MyHouse_work.db"}
```

**New response fields on `createNew`:**
```json
{"success":true, "bboxes":[...],
 "workOutputDbPath":"output/MyHouse_work.db",
 "orderLineCount":15, "esLineCount":8,
 "asiCount":12, "ppNodeCount":5,
 "validationSummary":"12 PASS, 3 WARN, 0 BLOCK"}
```

---

## 5. Test Specifications

### 5.1 Unit Tests — WorkOutputDAO

```java
class WorkOutputDAOTest {

    @Test void createWorkOutputDb_appliesSchema() {
        // Given: empty file path
        // When:  WorkOutputDAO.create(path)
        // Then:  9 tables exist (W_BuildingConfig, C_Order, C_OrderLine,
        //        M_AttributeSetInstance, M_AttributeInstance, CO_EmptySpace,
        //        CO_EmptySpaceLine, PP_Order_Node, PP_Order_NodeProduct,
        //        W_Variant, W_Validation_Result, AD_SysConfig)
        //        AD_SysConfig.SCHEMA_VERSION = 'W001'
    }

    @Test void save_createsSubOrderAndVariant() {
        // Given: work_output.db with master C_Order + 5 C_OrderLine rows
        // When:  save("wide-rooms", currentBboxes)
        // Then:  new sub-C_Order created (Parent_Order_ID = master, DocStatus='CO')
        //        sub-order has 5 C_OrderLine rows (copied, not shared)
        //        W_Variant row created: C_Order_ID = sub-order ID, is_active=1
        //        W_Variant.orderline_count = 5
        //        NO snapshot_json column populated (data lives in sub-order tables)
        //        previous W_Variant.is_active set to 0
    }

    @Test void save_previousSubOrderStaysCO() {
        // Given: save v1 (3 lines), then save v2 (5 lines)
        // Then:  v1 sub-C_Order.DocStatus = 'CO' (frozen, immutable)
        //        v2 sub-C_Order.DocStatus = 'CO' (latest save)
        //        v1 C_OrderLine rows unchanged (3 lines still present)
        //        v2 C_OrderLine rows are independent copies (5 lines)
    }

    @Test void recall_spawnsNewSubOrder() {
        // Given: save v1 (3 lines), then save v2 (5 lines)
        // When:  recall(v1)
        // Then:  new sub-C_Order created (DocStatus='DR', 3 lines copied from v1)
        //        v1 sub-order stays CO (not touched)
        //        v2 sub-order stays CO (not touched)
        //        new W_Variant.is_active = 1, label contains "recalled"
        //        total sub-orders = 3 (v1 CO, v2 CO, recalled DR)
    }

    @Test void listVariants_returnsChronological() {
        // Given: 3 saves
        // When:  listVariants("MyHouse")
        // Then:  3 VariantInfo rows, newest first
        //        each has subOrderId, label, compliance_status
    }
}
```

### 5.2 Unit Tests — ConstructionModelSpawner

```java
class ConstructionModelSpawnerTest {

    @Test void spawn_createsOrderFromBom() {
        // Given: DM_BOM.db with BUILDING_DM_STD (3 floors, 6 rooms)
        // When:  spawn(workConn, bomConn, compConn, "BUILDING_DM_STD", "MY")
        // Then:  1 C_Order (DocStatus='DR')
        //
        // Derive expected counts from BOM template at test time:
        //   expectedLines = walk m_bom tree from BUILDING_DM_STD, count nodes
        //   expectedESLines = count ROOM-level BOMs (each gets one ESLine)
        //   expectedASI = count LEAF products with M_AttributeSet_ID != null
        //   expectedPPNodes = count from doc_base_type routing template
        //
        //   assertEquals(expectedLines, actualOrderLineCount)
        //   assertEquals(expectedESLines, actualESLineCount)
        //   assertEquals(expectedASI, actualASICount)
        //   assertEquals(expectedPPNodes, actualPPNodeCount)
        //   1 W_Variant (label="v0", initial snapshot)
    }

    @Test void spawn_embeddsBuildingConfig() {
        // Given: classify_dm.yaml content
        // When:  spawn(...)
        // Then:  W_BuildingConfig.yaml_content = full YAML text
        //        W_BuildingConfig.jurisdiction = "MY"
    }

    @Test void spawn_validationReadonly() {
        // Given: spawn with known UBBL violations
        // When:  spawn(...)
        // Then:  W_Validation_Result rows exist with result='WARN'
        //        C_OrderLine.validation_status reflects results
        //        spawn does NOT block — READONLY mode
    }
}
```

### 5.3 Integration Tests — Save/Recall Round-Trip

```java
class SaveRecallIntegrationTest {

    @Test void saveAndRecall_roundTrips() {
        // Given: spawned work_output.db (master C_Order)
        // When:  modify C_OrderLine dx, save("v1") → sub-order-1 (CO)
        //        modify again, save("v2") → sub-order-2 (CO)
        //        recall("v1") → sub-order-3 (DR, copied from sub-order-1)
        // Then:  active sub-order's C_OrderLine.dx matches v1 state (not v2)
        //        sub-order-1 still exists with original dx (CO, immutable)
        //        sub-order-2 still exists with v2 dx (CO, immutable)
        //        3 sub-C_Order rows total under master
    }

    @Test void promote_createsBomEntries() {
        // Given: spawned + saved work_output.db
        // When:  promote(owner="red1", complianceRef="UBBL")
        // Then:  {PREFIX}_BOM.db has new m_bom rows with entity_type='U'
        //        m_bom_line rows match C_OrderLine state
        //        C_Order.DocStatus = 'CO'
    }

    @Test void approve_setsDocStatusAP() {
        // Given: spawned + saved work_output.db (DocStatus = 'IP')
        //        all validation rules PASS, no dangles
        // When:  approve(buildingId)
        // Then:  C_Order.DocStatus = 'AP'
        //        ApproveResponse.success = true
        //        ApproveResponse.validationSummary shows all PASS
    }

    @Test void approve_blockOnValidationFail() {
        // Given: C_OrderLine violating AD_Val_Rule (e.g., room < minimum)
        // When:  approve(buildingId)
        // Then:  ApproveResponse.success = false
        //        C_Order.DocStatus stays 'IP'
        //        ApproveResponse.blockingRules lists the violations
    }

    @Test void promote_requiresApproved() {
        // Given: work_output.db with DocStatus = 'IP' (not approved)
        // When:  promote(...)
        // Then:  PromoteResponse.success = false
        //        PromoteResponse.error = "not approved"
    }

    @Test void promote_blockOnDangles() {
        // Given: C_OrderLine referencing nonexistent M_Product, DocStatus = 'AP'
        // When:  promote(...)
        // Then:  PromoteResponse.success = false
        //        PromoteResponse.dangles contains the missing ref
    }
}
```

### 5.4 Tack Convention Tests — work_output.db

C_OrderLine.dx/dy/dz in work_output.db must use LBD convention (BBC.md §4),
consistent with m_bom_line in {PREFIX}_BOM.db. The spawner copies from BOM
templates, so LBD convention should propagate — but this must be tested.

```java
class WorkOutputTackTest {

    @Test void spawnedOrderLines_useLbdConvention() {
        // Given: spawned work_output.db from DM BOM
        // When:  read C_OrderLine dx/dy/dz for all LEAF lines
        // Then:  all dx >= 0, dy >= 0, dz >= 0  (LBD = child within parent)
        //        dx + allocated_width_mm/1000 <= host AABB width * 1.01
        //        (same W-TACK-1 invariant as BomValidator)
    }

    @Test void promote_preservesTackConvention() {
        // Given: spawned + saved work_output.db, user moved a bbox
        // When:  promote to {PREFIX}_BOM.db
        // Then:  promoted m_bom_line.dx/dy/dz matches C_OrderLine.dx/dy/dz
        //        all promoted offsets pass W-TACK-1 check
        //        entity_type = 'U', provenance = 'GENERATIVE'
    }
}
```

### 5.5 Wire Protocol Tests

```java
class WorkOutputWireTest {

    @Test void save_overTcp_returnsVariantId() {
        // Given: running DesignerServer + spawned work_output.db
        // When:  send {"action":"save","buildingId":"MyHouse","bboxes":[...],"variantLabel":"v1"}
        // Then:  response has success=true, variantId != null
    }

    @Test void recall_overTcp_returnsBboxesAndSubOrderId() {
        // Given: saved variant "v1"
        // When:  send {"action":"recall","buildingId":"MyHouse","variantId":"1"}
        // Then:  response has success=true, bboxes.size() > 0
        //        response has newSubOrderId (the spawned DR sub-order)
    }

    @Test void listVariants_overTcp_returnsAll() {
        // Given: 2 saved variants
        // When:  send {"action":"listVariants","buildingId":"MyHouse"}
        // Then:  response is array of length 2
    }
}
```

---

## 6. Implementation Order

| Step | File | What | Blocks |
|------|------|------|--------|
| 1 | `migration/W001_work_output_schema.sql` | DDL (DONE) | — |
| 2 | `TACK_FIX_SPEC.md` changes | FIX-1/2/3 (DONE as spec) | — |
| 3 | `WorkOutputDAO.java` (NEW) | create/save/recall/listVariants | step 1 |
| 4 | `ConstructionModelSpawner.java` (NEW) | spawn() — walk BOM, populate work_output.db | steps 1, 3 |
| 5 | `DesignerAPIImpl.java` | Wire save/recall/listVariants/promote to DAO | steps 3, 4 |
| 6 | `DesignerServer.java` | Already dispatched — just needs impl connected | step 5 |
| 7 | Tests | Unit + integration + wire | steps 3-6 |

---

*References:
[BIM_Designer.md](BIM_Designer.md) §17.10 (three-tier persistence) |
[DocValidate.md](DocValidate.md) §15 (code-level specs) |
[ConstructionAsERP.md](ConstructionAsERP.md) §2-3 (C_Order model) |
[TACK_FIX_SPEC.md](TACK_FIX_SPEC.md) (pre-requisite fix) |
[ACTION_ROADMAP.md](ACTION_ROADMAP.md) Phase G (task list)*
