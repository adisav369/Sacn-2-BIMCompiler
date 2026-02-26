-- Phase RM-3: Add placement_mode toggle to ad_compiler_config.
-- FLAT = read stored oracle (ad_element_placement), RELATIONAL = compute from rules.
-- Start safe in FLAT mode. Toggle to RELATIONAL after verifying compile still works.

INSERT INTO ad_compiler_config (id, config_key, config_value, description, is_active)
VALUES (NULL, 'placement_mode', 'FLAT', 'FLAT = stored oracle, RELATIONAL = computed from rules', 1);
