/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.topology.Discipline;

/**
 * Layer 1: Existence Contract - Base contract for all BIM entities.
 * Enforces mandatory attributes that every element must have.
 *
 * <p>Maps to: IfcRoot (ISO 16739-1:2018)
 * <p>Theory: Entity identity (Domain-Driven Design, Evans 2003)
 *
 * <p>Every BIM element must answer four fundamental questions:
 * <ol>
 *   <li>What is it? (guid)</li>
 *   <li>Where is it? (storey)</li>
 *   <li>What discipline owns it? (discipline)</li>
 *   <li>What space does it occupy? (bounds)</li>
 * </ol>
 *
 * @see <a href="https://standards.buildingsmart.org/IFC/RELEASE/IFC4/ADD2_TC1/HTML/schema/ifckernel/lexical/ifcroot.htm">IFC4 IfcRoot</a>
 */
public interface IBIMEntity {

    /**
     * Globally unique identifier.
     * Maps to IfcRoot.GlobalId (22-character IFC GUID format).
     *
     * <p>Contract: Must be unique across entire building model.
     * Must remain stable across compilations for the same element.
     *
     * @return non-null globally unique identifier
     */
    String guid();

    /**
     * Spatial context (storey name).
     * Maps to IfcRelContainedInSpatialStructure.
     *
     * <p>Contract: Must reference an existing storey in the building.
     * For elements spanning multiple storeys, returns the lowest storey.
     *
     * @return non-null storey name (e.g., "Ground", "Upper", "Level_1")
     */
    String storey();

    /**
     * Discipline classification.
     * Maps to IfcRelAssociatesClassification.
     *
     * <p>Contract: Must be a valid discipline from the extracted set.
     * Determines which coordination view the element belongs to.
     *
     * @return non-null discipline classification
     */
    Discipline discipline();

    /**
     * Axis-aligned bounding box in world coordinates.
     * Derived from IfcProduct.ObjectPlacement + IfcProductRepresentation.
     *
     * <p>Contract: Must accurately represent the spatial extent.
     * All coordinates in meters, relative to building origin.
     *
     * @return non-null bounding box (all values in meters)
     */
    BoundingBox bounds();
}
