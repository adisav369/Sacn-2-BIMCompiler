-- Phase ST-1a: BOM Template Contract — MinQty/MaxQty on M_BomCategoryLine
--
-- Adds quantity constraints to template lines so the BomTemplateContract
-- can validate catalog completeness. MinQty=0 means optional/pipeline-handled;
-- MinQty>0 means the BOM catalog must supply at least that many.

ALTER TABLE M_BomCategoryLine ADD COLUMN MinQty INTEGER NOT NULL DEFAULT 1;
ALTER TABLE M_BomCategoryLine ADD COLUMN MaxQty INTEGER NOT NULL DEFAULT 1;

-- RE level: structural tiers are pipeline-handled, not BOM-selected
UPDATE M_BomCategoryLine SET MinQty=0, MaxQty=1 WHERE M_BomCategory_ID='RE' AND Child_BomCategory_ID='SL';
UPDATE M_BomCategoryLine SET MinQty=1, MaxQty=1 WHERE M_BomCategory_ID='RE' AND Child_BomCategory_ID='GF';
UPDATE M_BomCategoryLine SET MinQty=0, MaxQty=1 WHERE M_BomCategory_ID='RE' AND Child_BomCategory_ID='RF';

-- GF level: room types with realistic quantity ranges
UPDATE M_BomCategoryLine SET MinQty=1, MaxQty=2 WHERE M_BomCategory_ID='GF' AND Child_BomCategory_ID='LI';
UPDATE M_BomCategoryLine SET MinQty=1, MaxQty=4 WHERE M_BomCategory_ID='GF' AND Child_BomCategory_ID='BD';
UPDATE M_BomCategoryLine SET MinQty=0, MaxQty=1 WHERE M_BomCategory_ID='GF' AND Child_BomCategory_ID='DN';
UPDATE M_BomCategoryLine SET MinQty=0, MaxQty=1 WHERE M_BomCategory_ID='GF' AND Child_BomCategory_ID='KT';
UPDATE M_BomCategoryLine SET MinQty=0, MaxQty=2 WHERE M_BomCategory_ID='GF' AND Child_BomCategory_ID='BT';
