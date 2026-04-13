-- DV043: Add rotation + offset correction columns to geometry_hash_redirect
-- Context: S185 — redirected meshes (e.g. sprinkler dedup) may have different
-- local-axis conventions. Corrections applied at viewer load time.
-- Target: library/component_library.db

ALTER TABLE geometry_hash_redirect ADD COLUMN rotation_x_correction REAL DEFAULT 0.0;
ALTER TABLE geometry_hash_redirect ADD COLUMN rotation_y_correction REAL DEFAULT 0.0;
ALTER TABLE geometry_hash_redirect ADD COLUMN rotation_z_correction REAL DEFAULT 0.0;
ALTER TABLE geometry_hash_redirect ADD COLUMN offset_x_correction REAL DEFAULT 0.0;
ALTER TABLE geometry_hash_redirect ADD COLUMN offset_y_correction REAL DEFAULT 0.0;
ALTER TABLE geometry_hash_redirect ADD COLUMN offset_z_correction REAL DEFAULT 0.0;

-- Set correction for pendent sprinkler redirects (Revit/Hospital → Terminal JKR)
-- These meshes were modeled inverted (rotation_x=π) in source IFC.
-- Terminal canonical mesh is right-side up, so cancel the π flip.
UPDATE geometry_hash_redirect
SET rotation_x_correction = -3.14159265358979,
    offset_z_correction = -0.0157
WHERE canonical_hash = '0d509e532be0f5f2'
  AND deprecated_hash IN (
    'a11f25b406f779a8',
    '389dd0da96979230',
    'bae71afd973eed3a',
    'bd5df7dd600f7582',
    '5d058cfe6e236b89',
    '92605cd3f82bcf3d'
  );
