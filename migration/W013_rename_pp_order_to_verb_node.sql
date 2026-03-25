-- W013: Rename PP_Order_Node → W_Verb_Node
-- PP_Order_Node is stale iDempiere Manufacturing naming.
-- These tables store verb execution results, not manufacturing orders.
-- W_ prefix: output.db work-output tables with no iDempiere parallel.

ALTER TABLE PP_Order_Node RENAME TO W_Verb_Node;
ALTER TABLE PP_Order_NodeProduct RENAME TO W_Verb_NodeProduct;
ALTER TABLE W_Verb_Node RENAME COLUMN PP_Order_Node_ID TO W_Verb_Node_ID;
ALTER TABLE W_Verb_NodeProduct RENAME COLUMN PP_Order_NodeProduct_ID TO W_Verb_NodeProduct_ID;
ALTER TABLE W_Verb_NodeProduct RENAME COLUMN PP_Order_Node_ID TO W_Verb_Node_ID;
DROP INDEX IF EXISTS idx_ppnode_order;
CREATE INDEX IF NOT EXISTS idx_verb_node_order ON W_Verb_Node(C_Order_ID);
