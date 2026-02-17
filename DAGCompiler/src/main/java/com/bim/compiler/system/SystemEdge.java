package com.bim.compiler.system;

import java.util.Map;

/**
 * Phase 35: An edge connecting two nodes in an MEP system graph.
 *
 * Edges represent physical connections (pipes, cables) or logical flows
 * (drainage direction, power feed).
 */
public record SystemEdge(
    String edgeId,
    String fromNodeId,
    String toNodeId,
    EdgeType type,
    Map<String, Object> properties  // diameter_mm, slope_percent, length_m, cable_size
) {}
