package com.bim.compiler.contract;

import java.util.List;

/**
 * Contract for LOD400 physical components.
 * Ensures dimensional compatibility within assemblies.
 *
 * <p>Maps to: IfcShapeRepresentation, IfcMaterialProfile
 * <p>Theory: Interference detection, tolerance analysis
 *
 * <p>LOD400 (fabrication-level) components have additional requirements
 * beyond relationship contracts. They must physically fit within
 * assemblies and connect properly to adjacent components.
 *
 * <p>Example: A 90x45mm stud must fit within wall frame with proper
 * clearance to adjacent noggings and connection points to top/bottom plates.
 */
public interface IPhysicalComponent extends IBIMEntity {

    /**
     * Physical dimensions (actual size, not bounding box).
     * For a 90x45mm stud 2.4m tall, returns Dimensions(0.09, 0.045, 2.4).
     *
     * <p>Contract: Dimensions must be accurate to manufacturing tolerances.
     * Width/depth are cross-section, height is length/span.
     *
     * @return physical dimensions in meters
     */
    Dimensions physicalSize();

    /**
     * Required clearance to adjacent component types.
     *
     * <p>From AS 1684: nogging minimum 25mm less than stud depth
     * to allow for installation from the side.
     *
     * <p>Contract: Must return appropriate clearance based on
     * framing code requirements and installation needs.
     *
     * @param adjacentType Type of adjacent component
     * @return Required clearance in meters (0 if no clearance required)
     */
    double clearanceTo(ComponentType adjacentType);

    /**
     * Physical connection points (nail/bolt/screw locations).
     *
     * <p>For stud-to-plate: 2 connection points per end (4 total).
     * Locations relative to component origin.
     *
     * <p>Contract: Must include all required fastener locations
     * per framing code (AS 1684, IRC, etc.).
     *
     * @return list of connection points with fastener specifications
     */
    List<ConnectionPoint> connectionPoints();
}
