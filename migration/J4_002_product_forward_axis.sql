-- §6.12.2 J4: M_Product forward_axis — propagated from component_library.db
-- Implementing DISC_VALIDATION_DB_SRS.md §6.12.2 §6 — InterimWorkshop
-- IFCtoERP populates from component_definitions.forward_axis at extraction time.
-- Y is the default (library convention: straight pipe/duct runs along Y axis).
ALTER TABLE M_Product ADD COLUMN forward_axis TEXT DEFAULT 'Y';
