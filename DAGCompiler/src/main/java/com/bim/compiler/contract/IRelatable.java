/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

import java.util.List;

/**
 * Layer 3: Relationship Contract - Declares how element connects to others.
 * Forces explicit declaration of all relationships.
 *
 * <p>Maps to: IfcRelConnects* family (ISO 16739-1:2018)
 * <p>Theory: RCC-8 spatial calculus (Cohn et al. 1997), Mereotopology (Casati & Varzi 1999)
 *
 * <p>This is the critical layer that prevents the "boundary ownership" anti-pattern.
 * Instead of assemblies owning their boundary elements, they declare connections
 * TO shared boundary elements.
 *
 * <p>Example transformation:
 * <pre>
 * // BEFORE (broken): Wall owns corner studs
 * class WallAssemblySpec {
 *     List&lt;FrameSpec&gt; frames;  // includes STUD_L, STUD_R
 * }
 *
 * // AFTER (correct): Wall connects to shared corners
 * class WallAssemblySpec implements IRelatable {
 *     List&lt;JunctionRef&gt; connectsTo();  // references shared corner posts
 * }
 * </pre>
 *
 * @see <a href="https://standards.buildingsmart.org/IFC/RELEASE/IFC4/ADD2_TC1/HTML/schema/ifcproductextension/lexical/ifcrelconnectselements.htm">IFC4 IfcRelConnectsElements</a>
 */
public interface IRelatable extends IIdentifiable {

    /**
     * Junction points this element connects TO (does not own).
     * Critical for boundary sharing.
     *
     * <p>Maps to: IfcRelConnectsPathElements for linear elements (walls, beams)
     * <p>Maps to: IfcRelConnectsPortToElement for MEP elements
     *
     * <p>Contract: Boundary elements must reference shared junctions rather than
     * being owned by the assembly. The junctions are obtained from
     * SharedElementRegistry.getOrCreateJunction().
     *
     * <p>Example: Wall connects to corner junctions at both ends
     * <pre>
     * return List.of(
     *     JunctionRef.startAt("CORNER_0.0_0.0"),
     *     JunctionRef.endAt("CORNER_8.5_0.0")
     * );
     * </pre>
     *
     * @return list of junction references (may be empty if no connections)
     */
    List<JunctionRef> connectsTo();

    /**
     * Element that hosts/contains this one.
     * Maps to: IfcRelContainedInSpatialStructure, IfcRelFillsElement
     *
     * <p>Contract: If non-null, must reference a valid element GUID.
     * Hosted elements are placed BY their host but are not part of the host's
     * internal structure.
     *
     * <p>Examples:
     * <ul>
     *   <li>Door hosted by wall (fills opening)</li>
     *   <li>Light fixture hosted by room (contained in space)</li>
     *   <li>Outlet hosted by wall (surface-mounted)</li>
     * </ul>
     *
     * @return GUID of hosting element, null if not hosted
     */
    String hostedBy();

    /**
     * Elements this one requires to exist first.
     * Defines construction/compilation order.
     *
     * <p>Maps to: IfcRelSequence (construction sequencing)
     *
     * <p>Contract: Elements in the list must be compiled/constructed before
     * this element. Creates a dependency DAG that BuildingCompiler must respect.
     *
     * <p>Examples:
     * <ul>
     *   <li>Lintel requires wall (needs wall to span)</li>
     *   <li>Door requires opening (fills the void)</li>
     *   <li>Light fixture requires ceiling (mounted to it)</li>
     * </ul>
     *
     * @return list of required element GUIDs (may be empty)
     */
    List<String> requires();

    /**
     * Elements this one feeds/supplies in a network.
     * Maps to: IfcRelConnectsPortToElement (MEP networks)
     *
     * <p>Contract: Defines the flow direction in MEP systems.
     * The feeding relationship is directional (upstream to downstream).
     *
     * <p>Examples:
     * <ul>
     *   <li>Electrical panel feeds outlets</li>
     *   <li>Water riser feeds branch pipes</li>
     *   <li>Duct main feeds diffusers</li>
     * </ul>
     *
     * @return list of fed element GUIDs (may be empty)
     */
    List<String> feeds();
}
