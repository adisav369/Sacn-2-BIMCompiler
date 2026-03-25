-- W010: Normalize FPR → FP in C_OrderLine.Discipline
-- Implementing DISC_VALIDATION_DB_SRS.md §11.6.5 Step 5-6
--
-- W003 backfill mapped bom_category='FP' → Discipline='FPR'. But AD_Org has
-- Value='FP' (ID=3) and the Discipline enum is FP, not FPR. This migration
-- normalizes all 'FPR' values to 'FP' so TEXT and enum are consistent.
--
-- Prerequisite for bulk TEXT→enum migration (prompt 16).

UPDATE C_OrderLine SET Discipline = 'FP' WHERE Discipline = 'FPR';
