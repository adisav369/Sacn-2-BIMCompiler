package com.bim.compiler.system;

/**
 * Phase 35: Role of a node in an MEP system graph.
 */
public enum NodeRole {
    SOURCE,       // Origin: DB panel, water main, MH, AHU
    DISTRIBUTION, // Mid-path: riser, trunk, circuit
    TERMINAL,     // End-point: fixture, outlet, diffuser
    CONNECTOR     // Junction: fitting, tee, elbow
}
