package com.bim.compiler.system;

import java.util.Map;

/**
 * Phase 35: A node in an MEP system graph.
 *
 * Nodes represent elements participating in the system (fixtures, risers, panels)
 * or external connection points (manhole, water main).
 */
public record SystemNode(
    String nodeId,
    String elementGuid,    // References elements_meta.guid (null for external like MH)
    NodeRole role,
    String name,           // Human-readable: "toilet_tandas", "MH1"
    Map<String, Object> properties
) {
    /**
     * Check if this node is external (not modeled in building).
     * Examples: manhole, water main, electrical utility connection.
     */
    public boolean isExternal() {
        return elementGuid == null;
    }
}
