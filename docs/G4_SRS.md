# G-4 SRS — Compile DB (output.db) + Validation Engine
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md)

**Version:** 1.2 (2026-03-19, session 34 — §2.5 postconditions per action, acceptance criteria)
**Depends on:** [BIM_Designer.md](BIM_Designer.md) §17.10, [DocValidate.md](DocValidate.md) §15, [MANIFESTO.md](MANIFESTO.md) §2
**Pre-requisite:** TACK-FIX (see [TACK_FIX_SPEC.md](TACK_FIX_SPEC.md))

---

## 1. Scope

G-4 delivers:
1. **output.db** — self-contained compile DB (DDL: `migration/W001_work_output_schema.sql`)
2. **ConstructionModelSpawner** — populates output.db from BOM templates
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
├── 3. ConstructionModelSpawner.spawn(compileConn, bomConn, compConn, ...)  ← NEW
│      │
│      ├── 3a. CREATE output.db (apply W001 migration)
│      │       → Execute W001_work_output_schema.sql DDL
│      │       → Verify 12 tables created via pragma table_list
│      │
│      ├── 3b. INSERT W_BuildingConfig (embedded YAML + identity)
│      │       → yaml_content  = full classify_{prefix}.yaml text
│      │       → doc_base_type = from YAML building.doc_base_type ('RE')  # deprecated — see DATA_MODEL.md §7
│      │       → doc_sub_type  = from YAML building.doc_sub_type ('DM')  # deprecated — see DATA_MODEL.md §7
│      │       → jurisdiction  = from request.jurisdiction ('MY')
│      │       → aabb_*_mm     = from request site dimensions
│      │       → provenance    = 'GENERATIVE'
│      │
│      ├── 3c. INSERT C_Order (building header, DocStatus='DR')
│      │       → C_Order_ID    = building_id (master — Parent_Order_ID = NULL)
│      │       → C_DocType_ID  = building prefix
│      │       → aabb_*        = from W_BuildingConfig
│      │
│      ├── 3d. Walk BOM tree → C_OrderLine + spatial slots + ASI
│      │   │
│      │   │   INPUT:  m_bom tree rooted at buildingBomId (e.g. "BUILDING_DM_STD")
│      │   │           Read from bomConn ({PREFIX}_BOM.db)
│      │   │
│      │   │   WALK ORDER: BOMWalker pre-order DFS (same walker as compilation)
│      │   │     BUILDING → FLOOR STR → DISCIPLINE SET → ROOM SET → LEAF
│      │   │
│      │   │   For each m_bom node encountered:
│      │   │
│      │   │   ┌─────────────────────────────────────────────────────────────┐
│      │   │   │ STEP 3d-i: INSERT C_OrderLine                             │
│      │   │   │                                                           │
│      │   │   │ family_ref   = m_bom.bom_id                               │
│      │   │   │ host_type    = derive from BOM level:                     │
│      │   │   │                depth 0 → BUILDING                         │
│      │   │   │                depth 1 → FLOOR                            │
│      │   │   │                depth 2 → DISCIPLINE or ROOM (m_product_category_id)│
│      │   │   │                depth 3+ → LEAF                            │
│      │   │   │ m_product_category_id = m_bom.m_product_category_id       │
│      │   │   │ dx/dy/dz     = m_bom_line.dx, .dy, .dz (LBD, BBC.md §4)  │
│      │   │   │ aabb_*_mm    = m_bom.aabb_width/depth/height              │
│      │   │   │ Qty          = m_bom_line.qty (factored count)            │
│      │   │   │ Line         = (parent_line_seq * 10 + child_seq)         │
│      │   │   │ Parent_OrderLine_ID = parent node's C_OrderLine_ID        │
│      │   │   │                                                           │
│      │   │   │ The walker tracks a Map<String bomId, Integer orderLineId>│
│      │   │   │ to resolve parent references during DFS.                  │
│      │   │   └─────────────────────────────────────────────────────────────┘
│      │   │
│      │   │   ┌─────────────────────────────────────────────────────────────┐
│      │   │   │ STEP 3d-ii: Spatial slot (compiler-internal cache)         │
│      │   │   │ INSERT co_empty_space_line (one per OrderLine)            │
│      │   │   │ co_emptyspace_id  = building-level co_empty_space FK      │
│      │   │   │ C_OrderLine_ID    = the just-inserted OrderLine           │
│      │   │   │ bom_line_seq      = m_bom_line.seq                        │
│      │   │   │ bom_id            = m_bom.bom_id                          │
│      │   │   │ bom_line_role     = m_bom_line.role                       │
│      │   │   │ bom_level         = DFS depth                             │
│      │   │   │ tack_from_x/y/z   = dx/dy/dz * 1000 (m → mm)            │
│      │   │   │ capacity_*_mm     = m_bom.aabb_width/depth/height         │
│      │   │   │ storey            = from YAML storeys map (depth 1 nodes) │
│      │   │   │ room_name         = from YAML floor_rooms.spaces[].name   │
│      │   │   │                                                           │
│      │   │   │ Also: INSERT co_empty_space header (one per C_Order):      │
│      │   │   │   origin_x/y/z_mm = building world origin (BOM origin)    │
│      │   │   │   aabb_*          = building AABB                         │
│      │   │   └─────────────────────────────────────────────────────────────┘
│      │   │
│      │   │   ┌─────────────────────────────────────────────────────────────┐
│      │   │   │ STEP 3d-iii: INSERT M_AttributeSetInstance (defaults)     │
│      │   │   │                                                           │
│      │   │   │ For LEAF nodes only (host_type = LEAF):                   │
│      │   │   │   Look up M_Product via compConn (component_library.db)   │
│      │   │   │   If product has M_AttributeSet_ID:                       │
│      │   │   │     → INSERT M_AttributeSetInstance (description = default)│
│      │   │   │     → INSERT M_AttributeInstance rows for each default:   │
│      │   │   │       width_mm, depth_mm, height_mm (from M_Product AABB) │
│      │   │   │       material = 'DEFAULT', finish = 'STANDARD'           │
│      │   │   │     → UPDATE C_OrderLine.M_AttributeSetInstance_ID        │
│      │   │   │                                                           │
│      │   │   │ For non-LEAF nodes (BUILDING, FLOOR, ROOM, DISCIPLINE):   │
│      │   │   │   No ASI — these are structural containers, not products. │
│      │   │   │   User may add ASI later (e.g., room finish package).     │
│      │   │   └─────────────────────────────────────────────────────────────┘
│      │   │
│      │   └── COUNTS tracked: orderLineCount, esLineCount, asiCount
│      │
│      ├── 3e. INSERT W_Verb_Node (default routing from M_Product_Category)
│      │       │
│      │       │ SELECT routing template by M_Product_Category:
│      │       │
│      │       │ RE (Residential):
│      │       │   10: Foundation (STR)
│      │       │   20: Frame (STR, depends_on=10)
│      │       │   30: Envelope (ARC, depends_on=20)
│      │       │   40: Fire Protection (FP, depends_on=30)
│      │       │   50: Plumbing (CW/SP, depends_on=30)
│      │       │   60: HVAC (ACMV, depends_on=30)
│      │       │   70: Electrical (ELEC, depends_on=30)
│      │       │   80: Gas (LPG, depends_on=50)
│      │       │   90: Finishes (ARC, depends_on=40,50,60,70)
│      │       │
│      │       │ CO (Commercial):
│      │       │   10: Substructure (STR)
│      │       │   20: Superstructure (STR, depends_on=10)
│      │       │   30: Envelope (ARC, depends_on=20)
│      │       │   40: MEP Risers (CW/SP/FP/ELEC, depends_on=20)
│      │       │   50: MEP Horizontal (CW/SP/FP/ACMV/ELEC, depends_on=40)
│      │       │   60: Finishes (ARC, depends_on=50)
│      │       │
│      │       └── ppNodeCount tracked
│      │
│      ├── 3f. Run PlacementValidator (READONLY mode)
│      │       │
│      │       │ Open valConn → validation.db (read-only)
│      │       │ For each C_OrderLine:
│      │       │   Tier 1: validateLine(compileConn, valConn, line, jurisdiction)
│      │       │   → INSERT W_Validation_Result (tier=1, result=PASS/WARN/BLOCK)
│      │       │   → UPDATE C_OrderLine.validation_status
│      │       │
│      │       │ READONLY mode: never returns BLOCK — only PASS/WARN.
│      │       │ Spawn must succeed even if rules flag issues.
│      │       │ User sees WARN status in BIM Designer ambient strip.
│      │       │
│      │       └── validationSummary = "12 PASS, 3 WARN, 0 BLOCK"
│      │
│      └── 3g. INSERT W_Variant (initial pointer, label="v0")
│              → C_Order_ID = master C_Order (initial state = master IS v0)
│              → is_active = 1
│              → orderline_count, esline_count, asi_count, ppnode_count
│              → compliance_status from validation summary
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
├── 1. Open output.db connection
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
│      → Spatial slots derived from M_BOM_Line dx/dy/dz (compiler-internal cache)
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
│      → COPY spatial slot cache (co_empty_space_line)
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

### 2.5 Postconditions — Acceptance Criteria per Action

**CreateNew + Spawn postconditions:**

| # | Assertion | Acceptance |
|---|-----------|-----------|
| P-CREATE-1 | C_Order exists with DocStatus='DR' | `SELECT COUNT(*) FROM C_Order WHERE DocStatus='DR'` = 1 |
| P-CREATE-2 | C_OrderLine count matches BOM template | Walk m_bom tree, count nodes = C_OrderLine count |
| P-CREATE-3 | W_BuildingConfig has embedded YAML | yaml_content IS NOT NULL AND length > 0 |
| P-CREATE-4 | W_Variant v0 exists and is_active=1 | Exactly 1 W_Variant row with is_active=1 |
| P-CREATE-5 | All 12 output.db tables exist | `pragma table_list` count = 12 |
| P-CREATE-6 | Validation ran in READONLY | W_Validation_Result rows exist, none with result='BLOCK' |

**Save postconditions:**

| # | Assertion | Acceptance |
|---|-----------|-----------|
| P-SAVE-1 | SaveResponse.success = true | success field is true |
| P-SAVE-2 | SaveResponse.variantId > 0 | Non-null, positive integer |
| P-SAVE-3 | New sub-C_Order created (DocStatus='CO') | `SELECT COUNT(*) FROM C_Order WHERE Parent_Order_ID = master AND DocStatus='CO'` incremented by 1 |
| P-SAVE-4 | W_Variant.is_active = 1 for new variant only | Exactly 1 W_Variant with is_active=1, all others is_active=0 |
| P-SAVE-5 | Sub-order C_OrderLine count = bbox count | Independent copies, not shared with master |
| P-SAVE-6 | Previous sub-order unchanged (CO, immutable) | Previous sub-order's C_OrderLine rows and DocStatus unchanged |

**Recall postconditions:**

| # | Assertion | Acceptance |
|---|-----------|-----------|
| P-RECALL-1 | New sub-C_Order spawned (DocStatus='DR') | New row in C_Order with DocStatus='DR' |
| P-RECALL-2 | C_OrderLine copied from recalled variant | New sub-order line count = recalled sub-order line count |
| P-RECALL-3 | Recalled sub-order stays CO | Original sub-order DocStatus unchanged |
| P-RECALL-4 | New W_Variant.is_active=1 | Exactly 1 active variant |
| P-RECALL-5 | No data destroyed | Total sub-order count = previous + 1 |

**Approve postconditions:**

| # | Assertion | Acceptance |
|---|-----------|-----------|
| P-APPROVE-1 | DocStatus = 'AP' on success | Master C_Order.DocStatus = 'AP' |
| P-APPROVE-2 | All validation PASS (no BLOCK) | `SELECT COUNT(*) FROM W_Validation_Result WHERE result='BLOCK'` = 0 |
| P-APPROVE-3 | All dangles resolved | Every C_OrderLine.family_ref resolves to M_Product or m_bom |
| P-APPROVE-4 | Failure returns blockingRules | If validation BLOCK exists, ApproveResponse.blockingRules[] populated |
| P-APPROVE-5 | Failure does not change DocStatus | DocStatus stays 'IP' on failure |

**Promote postconditions:**

| # | Assertion | Acceptance |
|---|-----------|-----------|
| P-PROMOTE-1 | Requires DocStatus='AP' | If DocStatus ≠ 'AP', PromoteResponse.success=false |
| P-PROMOTE-2 | m_bom rows created in {PREFIX}_BOM.db | m_bom count > 0 with entity_type='U' |
| P-PROMOTE-3 | m_bom_line matches C_OrderLine tree | Line count and dx/dy/dz values match |
| P-PROMOTE-4 | Provenance = 'GENERATIVE' | All promoted m_bom rows have provenance='GENERATIVE' |
| P-PROMOTE-5 | Dangles block promotion | If family_ref unresolved, PromoteResponse.dangles[] populated |

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
| `listOrderLines` | buildingId, parentId (null=root) | List\<OrderLineTree\> | **G-9: implement** |

### 4.1 Wire Protocol Additions for G-4

**New request field on `save`:**
```json
{"action":"save", "buildingId":"MyHouse",
 "bboxes":[{"bomId":"LIVING_SET","minX":2.2,...}],
 "variantLabel":"wide-rooms",
 "outputDbPath":"output/MyHouse.db"}
```

**New response fields on `createNew`:**
```json
{"success":true, "bboxes":[...],
 "outputDbPath":"output/MyHouse.db",
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
        //        M_AttributeSetInstance, M_AttributeInstance, co_empty_space,
        //        co_empty_space_line (compiler-internal), W_Verb_Node, W_Verb_NodeProduct,
        //        W_Variant, W_Validation_Result, AD_SysConfig)
        //        AD_SysConfig.SCHEMA_VERSION = 'W001'
    }

    @Test void save_createsSubOrderAndVariant() {
        // Given: output.db with master C_Order + 5 C_OrderLine rows
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
        // When:  spawn(compileConn, bomConn, compConn, "BUILDING_DM_STD", "MY")
        // Then:  1 C_Order (DocStatus='DR')
        //
        // Derive expected counts from BOM template at test time:
        //   expectedLines = walk m_bom tree from BUILDING_DM_STD, count nodes
        //   expectedESLines = count ROOM-level BOMs (each gets one ESLine)
        //   expectedASI = count LEAF products with M_AttributeSet_ID != null
        //   expectedPPNodes = count from M_Product_Category routing template
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
        // Given: spawned output.db (master C_Order)
        // When:  modify C_OrderLine dx, save("v1") → sub-order-1 (CO)
        //        modify again, save("v2") → sub-order-2 (CO)
        //        recall("v1") → sub-order-3 (DR, copied from sub-order-1)
        // Then:  active sub-order's C_OrderLine.dx matches v1 state (not v2)
        //        sub-order-1 still exists with original dx (CO, immutable)
        //        sub-order-2 still exists with v2 dx (CO, immutable)
        //        3 sub-C_Order rows total under master
    }

    @Test void promote_createsBomEntries() {
        // Given: spawned + saved output.db
        // When:  promote(owner="red1", complianceRef="UBBL")
        // Then:  {PREFIX}_BOM.db has new m_bom rows with entity_type='U'
        //        m_bom_line rows match C_OrderLine state
        //        C_Order.DocStatus = 'CO'
    }

    @Test void approve_setsDocStatusAP() {
        // Given: spawned + saved output.db (DocStatus = 'IP')
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
        // Given: output.db with DocStatus = 'IP' (not approved)
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

### 5.4 Tack Convention Tests — output.db (compile DB)

C_OrderLine.dx/dy/dz in output.db must use LBD convention (BBC.md §4),
consistent with m_bom_line in {PREFIX}_BOM.db. The spawner copies from BOM
templates, so LBD convention should propagate — but this must be tested.

```java
class WorkOutputTackTest {

    @Test void spawnedOrderLines_useLbdConvention() {
        // Given: spawned output.db from DM BOM
        // When:  read C_OrderLine dx/dy/dz for all LEAF lines
        // Then:  all dx >= 0, dy >= 0, dz >= 0  (LBD = child within parent)
        //        dx + allocated_width_mm/1000 <= host AABB width * 1.01
        //        (same W-TACK-1 invariant as BomValidator)
    }

    @Test void promote_preservesTackConvention() {
        // Given: spawned + saved output.db, user moved a bbox
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
        // Given: running DesignerServer + spawned output.db
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

## 6. Non-Disturbance Analysis — M16/M17 Against Rosetta Stones

### 6.1 M16: Opening Face-Anchor Consistency

**Rule:** Opening (IfcDoor/IfcWindow) centroid depth must align with host wall
center within 5mm, unless ASI `face_anchor` override declares INT/EXT.
See `AD_Val_Rule` 812 (V004_mined_rules.sql).

**SH verification (3 doors, 4 windows):**

| Element | Depth axis | Opening center | Wall center | Offset (mm) | Verdict |
|---------|-----------|----------------|-------------|-------------|---------|
| Window (north wall, -6.16..-4.30) | Y | 4.560 | 4.554 | 6 | PASS (≤5mm tol) |
| Window (north wall, -2.16..-0.30) | Y | 4.560 | 4.554 | 6 | PASS |
| Window (south wall, -1.56..0.30) | Y | -1.253 | -1.246 | 7 | **WARN** (>5mm) |
| Window (north wall, 2.96..4.82) | Y | 4.560 | 4.554 | 6 | PASS |
| Door ext (2.96..4.82) | Y | -1.171 | -1.246 | 75 | **EXCEPTION** |
| Door int (partition) | Y | -0.125 | n/a (thin) | — | SKIP (partition) |

**SH finding:** Exterior door offset of 75mm is NOT centered — it's flush with
exterior face (design intent). Interior doors in 95mm partitions have no
meaningful "center" to check. **Adjust rule:** skip openings where host wall
thickness < 150mm (partition walls). Add exception for exterior doors with
`face_anchor=EXT` override.

**DX verification (14 doors, 24 windows):**
- Level 2 doors show 174mm depth (thin partition) — SKIP per partition rule
- Level 1 doors show 914-1402mm depth — these are not wall-depth values but
  Y-extent values (tall narrow doors viewed from wrong axis). **Issue:** The
  "depth" of an opening depends on orientation. For Y-aligned walls, door
  depth is the Y AABB extent; for X-aligned walls, depth is X extent.
  Rule must use MIN(width, depth) as the relevant axis (same ERP-maths
  pattern as M12 clearance).

**TE verification (135 doors, 236 windows):**
- Large dataset; statistical check needed at implementation time.
- Expected: most openings centered, some flush-face exceptions in curtain wall.

**Non-Disturbance decision for M16:**
- Rule ACTIVE with adjustments:
  - Skip host walls < 150mm thick (partitions)
  - Use MIN(width, depth) for opening depth axis
  - Tolerance: 10mm (widened from 5mm — SH shows 6-7mm on centered openings)
  - `face_anchor` ASI override: INT/EXT → skip center check
- AD_Val_Rule_Exception: SH exterior door flush-face (1 instance)

### 6.2 M17: Opening Host Association

**Rule:** Every IfcDoor/IfcWindow must have a host IfcWall.
See `AD_Val_Rule` 813 (V004_mined_rules.sql).

**Critical limitation:** `I_Element_Extraction` has no `host_id` column.
The IFC relationship `IfcRelVoidsElement` carries host-opening associations,
but the extraction pipeline (`ifc_geometry_extractor.py`) does not extract it.

**Verification approach via AABB proximity:**
For each opening, find the nearest wall on the same storey whose AABB
envelope contains the opening's centroid in 2 of 3 axes (the opening sits
WITHIN the wall plane but extends THROUGH it in the depth axis).

**SH spot-check:** All 7 openings have a clear wall match within 100mm
centroid proximity. No orphan openings.

**DX spot-check:** All 38 openings have wall matches. DX has the mirrored
pair complication but openings on each side mirror correctly.

**TE:** 371 openings — statistical AABB proximity check deferred to
implementation. Expected: curtain wall windows may not match standard
IfcWall elements (they void IfcCurtainWall instead).

**Non-Disturbance decision for M17:**
- Rule ACTIVE but check method = AABB_PROXIMITY (not FK check)
- Tolerance: 200mm centroid-to-wall-center proximity
- AD_Val_Rule_Exception: TE curtain wall windows (count TBD at impl time)
- **R20 dependency:** When extraction pipeline adds `host_id` column (R20 in
  LAST_MILE_PROBLEM.md), M17 upgrades from AABB_PROXIMITY to FK_NOT_NULL

### 6.3 Updated V004 Parameters

Based on Non-Disturbance findings, the following V004 params need adjustment:

```sql
-- M16: Widen tolerance from 5mm to 10mm, add partition skip
UPDATE AD_Val_Rule_Param SET value = '10' WHERE ad_val_rule_param_id = 8121;
-- (already in V004 as 5mm — implementation should use 10mm per this analysis)

-- M17: Change check method from FK to AABB proximity
UPDATE AD_Val_Rule_Param SET value = 'AABB_PROXIMITY' WHERE ad_val_rule_param_id = 8133;
-- (already in V004 as HOST_FK_NOT_NULL — implementation should use AABB_PROXIMITY)
```

**Note:** V004 is append-only. These adjustments will be applied as V005
if V004 has already been run, or V004 will be corrected before first run.

---

## 7. YAML v3 MEP ProcessIt() Review

### 7.1 Review Status

WorkOrderGuide.md §Schema v3 defines the MEP rules-based laying pattern.
Reviewed against the now-complete AD_Val_Rule seed data (V002 + V004):

**CONFIRMED correct:**
- ProcessIt() fires ConstructionModelSpawner → PlacementValidator cascade
- AD_Val_Rule is DATA, not CODE — jurisdictions are INSERT rows
- ERP-maths clearance (M12) verified against TE (11 overlaps, 35 under 150mm)
- Three-tier cascade (per-discipline → cross-discipline → vertical) matches
  DocValidate.md §13

**Gap identified — mep.disciplines.FP.occupancy_class:**
```yaml
mep:
  disciplines:
    FP:
      occupancy_class: LH    # drives which AD_Val_Rule rows apply
```

The `occupancy_class` field links to `AD_Occupancy_Class.code` which then
joins via `AD_Val_Rule_Occupancy` to select applicable rules. This is the
C_Tax analogy: occupancy class → rule set, like tax category → tax rate.

**Confirmed:** V002 seeds 6 occupancy classes (LH, OH1, OH2, RES, COM, APT).
V002 links sprinkler rules (601, 602) to LH occupancy. The YAML v3
`occupancy_class: LH` will correctly select NFPA13 rules.

**Gap identified — grid computation spec:**
WorkOrderGuide.md §Schema v3 says:
```
Compute grid: 8000/4500 = 2 cols, 6000/4500 = 2 rows → 4 heads
```

This uses `max_spacing_mm` (4600) as the grid pitch, which is wrong — it
should use `typical_spacing_mm` (e.g., 3000-4000mm from TE mining M1).
The max is a LIMIT, not a TARGET.

**Fix needed in WorkOrderGuide.md:** The spawner should compute grid pitch as:
```
pitch = min(max_spacing_mm, room_dimension / ceil(room_dimension / typical_spacing_mm))
```

Where `typical_spacing_mm` is a new AD_Val_Rule_Param (the observed
dominant grid pitch from mining, distinct from the code maximum).

### 7.2 Recommended AD_Val_Rule_Param Addition

```sql
-- Add typical spacing (target pitch, not max limit) for grid computation
INSERT INTO AD_Val_Rule_Param VALUES (6013, 601, 'typical_spacing_mm', '3500', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (8034, 803, 'typical_spacing_mm', '3000', 'NUM', NULL);
```

This will be included in V005 or appended to V004 before first run.

### 7.3 YAML v3 → AD_Val_Rule Traceability

| YAML v3 field | AD_Val_Rule column | Link |
|---|---|---|
| `mep.jurisdiction` | `jurisdiction` | Direct match → rule selection |
| `mep.disciplines.FP.occupancy_class` | `AD_Val_Rule_Occupancy` → `AD_Occupancy_Class.code` | Join |
| `mep.disciplines.FP.enabled` | `is_active` (per building, not global) | Building-level override |
| `mep.disciplines.ELEC.enabled` | Same pattern | Same |

**Verdict:** YAML v3 schema is sound. Two minor gaps (typical_spacing param,
grid pitch formula) — both addressable as data changes, no schema changes needed.

---

## 8. Implementation Order

| Step | File | What | Blocks |
|------|------|------|--------|
| 1 | `migration/W001_work_output_schema.sql` | DDL (DONE) | — |
| 2 | `TACK_FIX_SPEC.md` changes | FIX-1/2/3 (DONE as spec) | — |
| 3 | `WorkOutputDAO.java` (NEW) | create/save/recall/listVariants | step 1 |
| 4 | `ConstructionModelSpawner.java` (NEW) | spawn() — walk BOM, populate output.db | steps 1, 3 |
| 5 | `DesignerAPIImpl.java` | Wire save/recall/listVariants/promote to DAO | steps 3, 4 |
| 6 | `DesignerServer.java` | Already dispatched — just needs impl connected | step 5 |
| 7 | Tests | Unit + integration + wire | steps 3-6 |

---

*References:
[BIM_Designer.md](BIM_Designer.md) §17.10 (three-tier persistence) |
[DocValidate.md](DocValidate.md) §15 (code-level specs) |
[MANIFESTO.md](MANIFESTO.md) §2 (C_Order model) |
[TACK_FIX_SPEC.md](TACK_FIX_SPEC.md) (pre-requisite fix) |
[ACTION_ROADMAP.md](ACTION_ROADMAP.md) Phase G (task list)*
