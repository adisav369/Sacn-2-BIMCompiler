-- migration_ST_docsubtype_fix.sql
-- Fix ST_SH/ST_DX: ST belongs in DocSubType, NOT DocBaseType.
--
-- CRITICAL (from Prime Rule):
--   DocBaseType values: RE, CO, IN
--   DocSubType values: SH, DX, TB, TE, ST
--
-- Before: ST_SH (DocBaseType='ST', DocSubType='SH')  ← WRONG
-- After:  ST_SH (DocBaseType='RE', DocSubType='ST')   ← CORRECT
--
-- M_BomCategory already has it right: doc_type='RE', doc_sub_type='ST'.
-- This aligns C_DocType to match.
--
-- CHECK constraint keeps 'ST' in DocBaseType (historical, harmless).
-- No data will use it — all ST entries now use DocSubType='ST'.

-- Fix ST_SH: RE + ST (was ST + SH)
UPDATE C_DocType SET
    DocBaseType = 'RE',
    DocSubType  = 'ST'
WHERE C_DocType_ID = 'ST_SH';

-- Fix ST_DX: RE + ST (was ST + DX)
UPDATE C_DocType SET
    DocBaseType = 'RE',
    DocSubType  = 'ST'
WHERE C_DocType_ID = 'ST_DX';
