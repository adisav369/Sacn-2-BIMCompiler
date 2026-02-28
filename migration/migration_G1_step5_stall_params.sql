-- G-1 Step 5: Data-drive stall divider constants.
-- Before: StoreyCompiler hardcoded stallSpacing=1.3, dividerDepth=1.2, stallHeight=1.8.
-- After:  all three live in m_attribute on the TOILET role line (bom_child_id=58).
--
-- spacing=1.3 already exists (bom_child_id=58). Add the two new params.

INSERT OR IGNORE INTO m_attribute (bom_child_id, param_key, param_value, param_type, unit, description)
VALUES
    (58, 'stall_divider_depth',  '1.2', 'DOUBLE', 'm', 'Depth of partition wall from back wall (m)'),
    (58, 'stall_divider_height', '1.8', 'DOUBLE', 'm', 'Height of partition wall (m)');
