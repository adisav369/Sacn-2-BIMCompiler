-- W011: Tier 1 — Add Name/Value (SearchKey) columns to output_template.db
-- Reference: docs/ID_NAME_VALUE_STUDY.md §3
-- Convention: iDempiere _ID/Name/Value triad. No PK changes, no FK changes.
-- Skips: co_empty_space* (deprecated S74/W008), PP_Order_NodeProduct (already conformant)

-- PP_Order_Node — already has Name, add Value
ALTER TABLE PP_Order_Node ADD COLUMN Value TEXT;
UPDATE PP_Order_Node SET Value = Name WHERE Value IS NULL;

-- c_orderline — already has Name, add Value
ALTER TABLE c_orderline ADD COLUMN Value TEXT;
UPDATE c_orderline SET Value = Name WHERE Value IS NULL;

-- elements_meta — has element_name, add Name + Value
ALTER TABLE elements_meta ADD COLUMN Name TEXT;
ALTER TABLE elements_meta ADD COLUMN Value TEXT;
UPDATE elements_meta SET Name = element_name WHERE Name IS NULL;
UPDATE elements_meta SET Value = guid WHERE Value IS NULL;

-- simple_qto — no semantic name
ALTER TABLE simple_qto ADD COLUMN Name TEXT;
ALTER TABLE simple_qto ADD COLUMN Value TEXT;
UPDATE simple_qto SET Name = COALESCE(ifc_class, '') || '_' || COALESCE(measurement_type, '') WHERE Name IS NULL;
