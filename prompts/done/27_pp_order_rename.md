# DONE — PP_Order_Node → W_Verb_Node rename
> Commit: b2648e03 [S86-ddl-fix]

Rename PP_Order_Node → W_Verb_Node across the entire codebase.

## Why

PP_Order_Node is e-Evolution Manufacturing plugin naming from iDempiere.
This project does NOT use PP_Order (manufacturing work orders). The tables
store verb execution results — which verb fired, on which element, with
what parameters. The DocAction lifecycle (DR→IP→CO→AP) handles document
workflow. PP_Order is stale naming that causes confusion with actual
iDempiere PP_Order semantics.

The W_Verb_Node naming enables future DocAction WfMC (Workflow Management
Coalition) integration — verb nodes can participate in document workflow
transitions (DR→IP→CO→AP) as first-class workflow participants.

## Table renames

| Old | New | Purpose |
|-----|-----|---------|
| PP_Order_Node | W_Verb_Node | Verb execution record |
| PP_Order_NodeProduct | W_Verb_NodeProduct | Verb parameter (name/value) |
| PP_Order_Node_ID | W_Verb_Node_ID | PK column |
| PP_Order_NodeProduct_ID | W_Verb_NodeProduct_ID | PK column |
| PP_Order_Node_ID (FK) | W_Verb_Node_ID | FK in W_Verb_NodeProduct |
| idx_ppnode_order | idx_verb_node_order | Index |

W_ prefix because these are output.db work-output tables with no direct
iDempiere parallel (same convention as W_BuildingConfig, W_Variant).

## Migration (append-only — never modify existing migrations)

Create `migration/W012_rename_pp_order_to_verb_node.sql`:
```sql
ALTER TABLE PP_Order_Node RENAME TO W_Verb_Node;
ALTER TABLE PP_Order_NodeProduct RENAME TO W_Verb_NodeProduct;
ALTER TABLE W_Verb_Node RENAME COLUMN PP_Order_Node_ID TO W_Verb_Node_ID;
ALTER TABLE W_Verb_NodeProduct RENAME COLUMN PP_Order_NodeProduct_ID TO W_Verb_NodeProduct_ID;
ALTER TABLE W_Verb_NodeProduct RENAME COLUMN PP_Order_Node_ID TO W_Verb_Node_ID;
DROP INDEX IF EXISTS idx_ppnode_order;
CREATE INDEX IF NOT EXISTS idx_verb_node_order ON W_Verb_Node(C_Order_ID);
```

Then apply this migration to `library/output_template.db`.

## Java files to update (24 files)

### PO layer (ORMSandbox) — rename classes + all internal references
- `X_PP_Order_Node.java` → `X_W_Verb_Node.java`
- `X_PP_Order_NodeProduct.java` → `X_W_Verb_NodeProduct.java`
- `M_PP_Order_Node.java` → `M_W_Verb_Node.java`
- `M_PP_Order_NodeProduct.java` → `M_W_Verb_NodeProduct.java`
- `PP_Order_NodeTest.java` → `W_Verb_NodeTest.java`
- `OrderLineInterfaceContractTest.java` — update references
- `BuildingInspector.java` — update references
- `MOrderLine.java` — update references
- `X_C_OrderLine.java` — update references

### BIM_COBOL module
- `VerbNodePersister.java` — writes to these tables, update table/column names
- `VerbNodePersisterTest.java` — update
- `BimCobolVerbExecutor.java` — update
- `VerbStageIntegrationTest.java` — update

### DAGCompiler module
- `VerbStage.java` — log message references PP_Order_Node
- `VerbExecutor.java` — javadoc/interface references
- `BuildingWriter.java` — update references
- `MetadataValidator.java` — update references
- `PlacementProver.java` — update references
- `SpatialPlacementVisitor.java` — update references
- `OrderLineWalker.java` — update references
- `OutputTemplateGenerator.java` — update references

### BonsaiBIMDesigner
- `DiffVerbTest.java` — update references
- `DiffVerbService.java` — update references

### BIMBackOffice
- `PrintConfig.java` — update references

## Doc files to update

### BIM_COBOL.md (biggest — ~30 references)
Replace all occurrences of:
- `PP_Order_Node` → `W_Verb_Node`
- `PP_Order_NodeProduct` → `W_Verb_NodeProduct`
- `PP_Order_Node_ID` → `W_Verb_Node_ID`
- `PP_Order` (standalone, meaning work order) → remove or reword
- `PP_Order_BOM` → `C_OrderLine` (it already IS C_OrderLine)
- `PP_Order_Workflow` → remove (not implemented, just spec text)
- Remove §15.6 iDempiere Manufacturing mapping table rows for PP_Order*

### Other docs with PP_Order references
Search all docs/*.md for PP_Order and update. Key files:
- DocAction_SRS.md (PP_Order_Node lineage reference)
- Any Foundation line citing PP_Order_Node

## Verification

After all changes:
1. `mvn compile -q` — must pass
2. `./scripts/run_tests.sh` — full gate must pass
3. `grep -r "PP_Order" --include="*.java" --include="*.md" --include="*.sql"` —
   only hits should be in migration/W001 (historical, never modified) and
   migration/archive/ (historical)
4. `grep -r "PP_Order" docs/` — zero hits except archive/

Commit message prefix: [S86-rename-verb-node]
