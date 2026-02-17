package com.bim.compiler.contract;

import java.util.List;

/**
 * Contract for MEP elements that connect to networks/systems.
 * Defines how elements participate in distribution systems.
 *
 * <p>Maps to: IfcDistributionElement, IfcDistributionPort, IfcRelConnectsPortToElement
 *
 * <p>System Types:
 * <ul>
 *   <li>ELECTRICAL - panels, circuits, outlets, switches</li>
 *   <li>PLUMBING - pipes, fixtures, risers, stacks</li>
 *   <li>HVAC - ducts, diffusers, AHU, VAV</li>
 *   <li>FIRE_PROTECTION - sprinklers, risers, alarms</li>
 *   <li>DATA - network cables, outlets, switches</li>
 * </ul>
 *
 * <p>Connection Flow:
 * <pre>
 * Source (feeds) → Distribution → Terminal (consumes)
 * Panel → Circuit → Outlet
 * Riser → Branch → Fixture
 * Main → Duct → Diffuser
 * FP Riser → Branch → Sprinkler
 * </pre>
 */
public interface IConnectable extends IBIMEntity {

    /**
     * MEP system this element belongs to.
     */
    MEPSystem system();

    /**
     * Role in the distribution network.
     */
    DistributionRole distributionRole();

    /**
     * Connection ports on this element.
     * Each port can connect to one other element.
     *
     * @return list of connection ports
     */
    List<Port> ports();

    /**
     * Elements this one connects to (upstream and downstream).
     * More specific than IRelatable.connectsTo() - includes port info.
     *
     * @return list of connections with port details
     */
    List<Connection> connections();

    /**
     * Upstream element that feeds/supplies this one.
     * Null for source elements (panels, mains, AHU).
     *
     * @return GUID of upstream element, or null
     */
    default String upstreamGuid() {
        return connections().stream()
            .filter(c -> c.direction() == FlowDirection.INLET)
            .map(Connection::targetGuid)
            .findFirst()
            .orElse(null);
    }

    /**
     * MEP distribution systems.
     */
    enum MEPSystem {
        ELECTRICAL,
        PLUMBING_SUPPLY,    // Cold/hot water supply
        PLUMBING_WASTE,     // Drainage/waste
        PLUMBING_VENT,      // Vent stack
        HVAC_SUPPLY,        // Supply air
        HVAC_RETURN,        // Return air
        HVAC_EXHAUST,       // Exhaust
        FIRE_PROTECTION,    // Sprinkler system
        FIRE_ALARM,         // Detection/alarm
        DATA                // Network/telecom
    }

    /**
     * Role in the distribution hierarchy.
     */
    enum DistributionRole {
        SOURCE,         // Origin point (panel, pump, AHU)
        MAIN,           // Primary distribution (riser, main duct)
        BRANCH,         // Secondary distribution (branch circuit, branch pipe)
        TERMINAL,       // End point (outlet, fixture, diffuser)
        JUNCTION,       // Connection point (tee, wye, junction box)
        VALVE,          // Control point (valve, damper, switch)
        METER           // Measurement point (meter, sensor)
    }

    /**
     * Connection port on an element.
     */
    record Port(
        String portId,          // Unique within element
        PortType type,          // INLET, OUTLET, BIDIRECTIONAL
        String portName,        // Human readable ("INLET_1", "SUPPLY")
        Point3D localPosition,  // Position relative to element origin
        double nominalSize      // Nominal diameter/size in meters
    ) {}

    /**
     * Port types for flow direction.
     */
    enum PortType {
        INLET,          // Receives flow
        OUTLET,         // Provides flow
        BIDIRECTIONAL   // Either direction (electrical)
    }

    /**
     * Connection between two elements.
     */
    record Connection(
        String localPortId,     // Port on this element
        String targetGuid,      // Connected element GUID
        String targetPortId,    // Port on target element
        FlowDirection direction // Flow relative to this element
    ) {}

    /**
     * Flow direction relative to this element.
     */
    enum FlowDirection {
        INLET,      // Flow enters this element
        OUTLET,     // Flow exits this element
        NONE        // No flow (electrical, data)
    }

    /**
     * Simple 3D point for port positions.
     */
    record Point3D(double x, double y, double z) {}
}
