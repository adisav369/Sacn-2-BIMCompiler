-- BIM OOTB Tab for C_Project window
-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
-- SPDX-License-Identifier: MIT
-- APPEND ONLY — never modify existing migrations
--
-- Adds a "BIM" tab to C_Project window (AD_Window_ID=130).
-- AD_TabType='BIM' triggers BIMTabPanelFactory via OSGi IADTabPanelFactory.
-- The tab embeds the BIM OOTB viewer in an Iframe.

INSERT INTO AD_Tab (
    AD_Tab_ID,
    AD_Client_ID,
    AD_Org_ID,
    IsActive,
    Created,
    CreatedBy,
    Updated,
    UpdatedBy,
    Name,
    Description,
    AD_Window_ID,
    AD_Table_ID,
    SeqNo,
    TabLevel,
    IsSingleRow,
    IsReadOnly,
    IsInsertRecord,
    IsAdvancedTab,
    IsSortTab,
    IsInfoTab,
    IsTranslationTab,
    HasTree,
    AD_TabType,
    EntityType,
    AD_Tab_UU
) VALUES (
    200500,                 -- AD_Tab_ID (custom range)
    0,                      -- AD_Client_ID (System)
    0,                      -- AD_Org_ID (*)
    'Y',                    -- IsActive
    NOW(),                  -- Created
    100,                    -- CreatedBy (SuperUser)
    NOW(),                  -- Updated
    100,                    -- UpdatedBy
    'BIM',                  -- Name (tab label)
    'BIM OOTB 3D model viewer — paste viewer URL in Description field',
    130,                    -- AD_Window_ID (C_Project window)
    203,                    -- AD_Table_ID (C_Project table)
    900,                    -- SeqNo (last tab)
    0,                      -- TabLevel (peer tab, not sub-tab)
    'Y',                    -- IsSingleRow
    'N',                    -- IsReadOnly
    'N',                    -- IsInsertRecord (viewer only, no new records)
    'N',                    -- IsAdvancedTab
    'N',                    -- IsSortTab
    'N',                    -- IsInfoTab
    'N',                    -- IsTranslationTab
    'N',                    -- HasTree
    'BIM',                  -- AD_TabType (triggers BIMTabPanelFactory)
    'U',                    -- EntityType (User customization)
    'b1a00100-0001-4000-8000-000000000001'  -- AD_Tab_UU
);
