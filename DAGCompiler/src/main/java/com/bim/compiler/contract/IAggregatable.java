/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

/**
 * Layer 4: Aggregation Contract - Controls merge, compose, and deduplicate operations.
 * Determines how elements combine when contexts merge.
 *
 * <p>Maps to: IfcRelAggregates, IfcElementAssembly (ISO 16739-1:2018)
 * <p>Theory: Aggregate Root (DDD), Composite pattern (GoF)
 *
 * <p>This layer is essential for the merge operations in BuildingCompiler:
 * <ul>
 *   <li>Units merging to storey (multi-unit buildings)</li>
 *   <li>Storeys merging to building (multi-storey buildings)</li>
 *   <li>Assemblies combining components (wall framing)</li>
 * </ul>
 *
 * <p>The key insight: during merge, elements with same uniqueKey should be
 * deduplicated, not blindly concatenated with addAll().
 *
 * @see <a href="https://standards.buildingsmart.org/IFC/RELEASE/IFC4/ADD2_TC1/HTML/schema/ifckernel/lexical/ifcrelaggregates.htm">IFC4 IfcRelAggregates</a>
 */
public interface IAggregatable extends IRelatable {

    /**
     * Can this element be merged with another of same uniqueKey?
     * False for elements that should never deduplicate.
     *
     * <p>Contract: If true, mergeWith() must be implemented properly.
     * If false, duplicates will cause validation errors.
     *
     * <p>Examples:
     * <ul>
     *   <li>Corner studs: true (should deduplicate)</li>
     *   <li>Room boundaries: false (distinct even if same location)</li>
     *   <li>Columns: true (deduplicate across units)</li>
     * </ul>
     *
     * @return true if element can be merged with same-key element
     */
    boolean isMergeable();

    /**
     * Merge this element with another of same uniqueKey.
     * Called during context merge (e.g., units merging to storey).
     *
     * <p>Contract: Only called when isMergeable() returns true and
     * both elements have the same uniqueKey(). The result should
     * represent the combined/unified element.
     *
     * <p>Implementation options:
     * <ul>
     *   <li>Return this (prefer first)</li>
     *   <li>Return other (prefer second)</li>
     *   <li>Return new merged instance</li>
     * </ul>
     *
     * @param other Element to merge with (same uniqueKey guaranteed)
     * @return Merged element, or this if not mergeable
     * @throws IllegalArgumentException if uniqueKeys don't match
     */
    IAggregatable mergeWith(IAggregatable other);

    /**
     * Parent assembly this component belongs to.
     * Maps to: IfcRelAggregates.RelatingObject
     *
     * <p>Contract: If non-null, must reference a valid assembly GUID.
     * The assembly is responsible for the component's lifecycle.
     *
     * <p>Example: Stud belongs to wall assembly, outlet belongs to room
     *
     * @return GUID of parent assembly, null if top-level element
     */
    String parentAssembly();

    /**
     * Role of this component within its assembly.
     * Determines ownership vs sharing behavior.
     *
     * <p>Contract:
     * <ul>
     *   <li>INTERNAL: owned by assembly, never shared</li>
     *   <li>BOUNDARY: at edge, may need to reference shared element</li>
     *   <li>SHARED: must reference from SharedElementRegistry</li>
     *   <li>HOSTED: placed by assembly but independent entity</li>
     * </ul>
     *
     * @return component role within parent assembly
     */
    ComponentRole role();
}
