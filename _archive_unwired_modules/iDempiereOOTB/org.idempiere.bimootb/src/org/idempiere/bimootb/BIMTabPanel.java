/*
 * BIM OOTB — iDempiere BIM Tab
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 *
 * Implementing iDempiereOOTB.md §3.5 — Witness: W-BIM-TAB
 *
 * Custom IADTabpanel that embeds the BIM OOTB viewer in an Iframe.
 * Extends ADTabpanel so all GridTab lifecycle (save, refresh, query)
 * is handled by the superclass against C_Project.
 *
 * The Iframe URL is read from C_Project.Description — the user pastes
 * the OCI viewer URL there. Each project can point to a different viewer.
 * On IFC drop, the viewer sends a postMessage with the IFC filename.
 * The ZK bridge receives it and writes C_Project.Name.
 */
package org.idempiere.bimootb;

import java.util.logging.Level;

import org.adempiere.webui.adwindow.ADTabpanel;
import org.adempiere.webui.util.ZKUpdateUtil;
import org.compiere.util.CLogger;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Div;
import org.zkoss.zul.Iframe;

public class BIMTabPanel extends ADTabpanel {

    private static final long serialVersionUID = 1L;
    private static final CLogger log = CLogger.getCLogger(BIMTabPanel.class);

    private Iframe iframe;

    @Override
    public void createUI() {
        super.createUI();

        String viewerUrl = getViewerUrl();

        if (viewerUrl == null) {
            Div instructions = new Div();
            instructions.setStyle(
                "padding: 40px; text-align: center; font-size: 14px; color: #666;"
                + " background: #f9f9f9; border: 1px dashed #ccc; border-radius: 8px;"
                + " margin: 20px;");
            instructions.setSclass("bim-instructions");
            org.zkoss.zul.Label label = new org.zkoss.zul.Label(
                "Paste the BIM OOTB viewer URL into the Description field "
                + "(e.g. https://objectstorage.../index.html), save, "
                + "then re-open this tab.");
            instructions.appendChild(label);
            this.appendChild(instructions);
            return;
        }

        // Build iframe URL with embedded params
        String sep = viewerUrl.contains("?") ? "&" : "?";
        int recordId = getGridTab().getRecord_ID();
        String iframeUrl = viewerUrl + sep
            + "embedded=true&record=" + recordId;

        iframe = new Iframe(iframeUrl);
        ZKUpdateUtil.setWidth(iframe, "100%");
        ZKUpdateUtil.setHeight(iframe, "800px");
        iframe.setStyle("border: none; min-height: 600px;");

        this.appendChild(iframe);

        installMessageBridge();

        log.info("BIM tab created for C_Project_ID=" + recordId
            + " viewer=" + viewerUrl);
    }

    /**
     * Read viewer URL from C_Project.Description field.
     * Returns null if empty or not a valid URL.
     */
    private String getViewerUrl() {
        Object desc = getGridTab().getValue("Description");
        if (desc == null) return null;
        String url = desc.toString().trim();
        if (url.startsWith("http://") || url.startsWith("https://"))
            return url;
        return null;
    }

    /**
     * Install the postMessage bridge between the Iframe (viewer) and ZK.
     *
     * Viewer sends:
     *   window.parent.postMessage({
     *       type: 'BIM_IFC_LOADED',
     *       name: 'Block_A.ifc',
     *       elementCount: 12345
     *   }, '*')
     *
     * The JS listener on the ZK desktop receives it and forwards to the
     * server as a ZK event. The server-side handler writes C_Project.Name.
     */
    private void installMessageBridge() {
        // Server-side: listen for the custom event on the iframe widget
        iframe.addEventListener("onBIMMessage", new EventListener<Event>() {
            @Override
            public void onEvent(Event event) throws Exception {
                try {
                    Object rawData = event.getData();
                    if (rawData == null) return;
                    String data = rawData.toString();
                    String ifcName = extractJsonField(data, "name");
                    if (ifcName != null && !ifcName.isEmpty()) {
                        // Strip .ifc extension for cleaner project name
                        if (ifcName.toLowerCase().endsWith(".ifc"))
                            ifcName = ifcName.substring(0, ifcName.length() - 4);
                        getGridTab().setValue("Name", ifcName);
                        getGridTab().dataSave(false);
                        log.info("BIM IFC loaded, C_Project.Name set to: " + ifcName);
                    }
                } catch (Exception e) {
                    log.log(Level.WARNING, "Error handling BIM message", e);
                }
            }
        });

        // Client-side: inject JS that relays postMessage to ZK server
        String iframeUuid = iframe.getUuid();
        Clients.evalJavaScript(
            "window.addEventListener('message', function(e) {"
            + "  if (e.data && e.data.type === 'BIM_IFC_LOADED') {"
            + "    var wgt = zk.$('#" + iframeUuid + "');"
            + "    if (wgt) {"
            + "      zAu.send(new zk.Event(wgt, 'onBIMMessage',"
            + "        JSON.stringify(e.data), {toServer:true}));"
            + "    }"
            + "  }"
            + "});"
        );
    }

    /**
     * Extract a string value from a JSON string without a JSON library.
     * Looks for "field":"value" pattern.
     */
    private String extractJsonField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int start = json.indexOf(key);
        if (start < 0) return null;
        start += key.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }
}
