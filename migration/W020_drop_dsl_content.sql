-- W020: Remove DSL content columns — dsl_*.bim files deleted; YAML is sole intent source (S140)
-- SQLite 3.35+ supports ALTER TABLE ... DROP COLUMN

-- m_bom: dsl_content was populated by IFCtoBOMPipeline from dsl_*.bim files (now deleted)
ALTER TABLE m_bom DROP COLUMN dsl_content;

-- C_DocType: DSLContent was populated by run_RosettaStones.sh seeding (now removed)
ALTER TABLE C_DocType DROP COLUMN DSLContent;

-- c_order: DSLContent was copied from C_DocType during order creation (now removed)
-- Note: c_order in output.db is recreated each compile; this handles existing output DBs
ALTER TABLE c_order DROP COLUMN DSLContent;
