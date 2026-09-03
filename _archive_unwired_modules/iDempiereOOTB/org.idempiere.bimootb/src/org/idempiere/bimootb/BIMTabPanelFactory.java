/*
 * BIM OOTB — iDempiere BIM Tab
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package org.idempiere.bimootb;

import org.adempiere.webui.adwindow.IADTabpanel;
import org.adempiere.webui.factory.IADTabPanelFactory;

/**
 * OSGi service that provides a custom tab panel for AD_TabType='BIM'.
 * Registered via OSGI-INF/org.idempiere.bimootb.BIMTabPanelFactory.xml
 * with service.ranking=10 so it is consulted before the default factory.
 */
public class BIMTabPanelFactory implements IADTabPanelFactory {

    @Override
    public IADTabpanel getInstance(String type) {
        if ("BIM".equalsIgnoreCase(type))
            return new BIMTabPanel();
        return null;
    }
}
