-- §J4: Move building registry columns from C_DocType to m_bom
-- C_DocType is now a stub for iDempiere 'Construction Order' compatibility only.
-- BuildingRegistry reads from m_bom. IFCtoBOM writes to m_bom.
ALTER TABLE m_bom ADD COLUMN project_name TEXT;
ALTER TABLE m_bom ADD COLUMN dsl_content TEXT;
ALTER TABLE m_bom ADD COLUMN output_db_path TEXT;
ALTER TABLE m_bom ADD COLUMN reference_db_path TEXT;
ALTER TABLE m_bom ADD COLUMN expected_elements INTEGER DEFAULT 0;
ALTER TABLE m_bom ADD COLUMN provenance TEXT DEFAULT 'EXTRACTED';
ALTER TABLE m_bom ADD COLUMN geometry_fail_threshold INTEGER DEFAULT 0;
ALTER TABLE m_bom ADD COLUMN jurisdiction TEXT;
ALTER TABLE m_bom ADD COLUMN code_edition TEXT;

-- Backfill from C_DocType if it exists (safe: no-op if C_DocType absent)
UPDATE m_bom SET
    project_name            = (SELECT c.ProjectName          FROM C_DocType c WHERE c.doc_sub_type = m_bom.doc_sub_type LIMIT 1),
    dsl_content             = (SELECT c.DSLContent           FROM C_DocType c WHERE c.doc_sub_type = m_bom.doc_sub_type LIMIT 1),
    output_db_path          = (SELECT c.OutputDbPath         FROM C_DocType c WHERE c.doc_sub_type = m_bom.doc_sub_type LIMIT 1),
    reference_db_path       = (SELECT c.ReferenceDbPath      FROM C_DocType c WHERE c.doc_sub_type = m_bom.doc_sub_type LIMIT 1),
    expected_elements       = COALESCE((SELECT c.ExpectedElements      FROM C_DocType c WHERE c.doc_sub_type = m_bom.doc_sub_type LIMIT 1), 0),
    provenance              = COALESCE((SELECT c.Provenance            FROM C_DocType c WHERE c.doc_sub_type = m_bom.doc_sub_type LIMIT 1), 'EXTRACTED'),
    geometry_fail_threshold = COALESCE((SELECT c.GeometryFailThreshold FROM C_DocType c WHERE c.doc_sub_type = m_bom.doc_sub_type LIMIT 1), 0),
    jurisdiction            = (SELECT c.Jurisdiction         FROM C_DocType c WHERE c.doc_sub_type = m_bom.doc_sub_type LIMIT 1),
    code_edition            = (SELECT c.CodeEdition          FROM C_DocType c WHERE c.doc_sub_type = m_bom.doc_sub_type LIMIT 1)
WHERE bom_type = 'BUILDING';
