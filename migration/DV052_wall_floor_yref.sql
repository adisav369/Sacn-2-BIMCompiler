-- DV052: Fix WALL_FLOOR yRef — fridge/washer belongs at back wall (MAX-Y), not room center.
-- Evidence: DX Kitchen A IFC fridge Y 11.008–11.813m (back wall maxY=11.813m).
-- Generative was CENTER → mid-Y 10.692m, off by ~720mm.
-- Witness: W-FRIDGE-Y (generative fridge centerY ≈ roomMaxY - halfDepth ±50mm)
-- Implementing S160.

UPDATE ad_placement_offset
SET y_ref = 'MAX', from_edge_y = 0.0
WHERE placement_rule = 'WALL_FLOOR';
