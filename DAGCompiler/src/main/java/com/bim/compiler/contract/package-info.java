/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
/**
 * Contract Architecture for BIM Intent Compiler.
 *
 * <p>This package defines a 6-layer contract hierarchy that prevents structural
 * bugs at compile-time by forcing explicit declaration of element relationships
 * and validating mesh geometry at the primitive level.
 *
 * <h2>Layer Model</h2>
 * <pre>
 * Layer 5: SEMANTIC      - Domain-specific validation rules    (IValidatable)
 * Layer 4: AGGREGATION   - Merge, compose, deduplicate         (IAggregatable)
 * Layer 3: RELATIONSHIP  - Connect, host, feed, require        (IRelatable)
 * Layer 2: IDENTITY      - Same, different, continues          (IIdentifiable)
 * Layer 1: EXISTENCE     - Mandatory attributes                (IBIMEntity)
 * Layer 0: GEOMETRY      - Mesh primitive validity             (IGeometryValidatable)
 * </pre>
 *
 * <h2>Layer 0: Geometry Contract</h2>
 * <p>The foundational layer validates mesh topology and geometry BEFORE elements
 * enter the BIM model. Key insight from Manifold Library: "Topological exactness
 * over geometric precision" - topology (integer indices) is exact, geometry
 * (float positions) is bounded.
 * <ul>
 *   <li>{@link com.bim.compiler.contract.IGeometryValidatable} - Mesh validation interface</li>
 *   <li>{@link com.bim.compiler.contract.GeometryViolation} - Geometry-specific violations</li>
 * </ul>
 *
 * <h2>Core Problem Solved</h2>
 * <p>Elements that should be SHARED were instead OWNED by multiple assemblies,
 * causing duplicates at boundaries. The contract architecture forces explicit
 * declaration via {@link com.bim.compiler.contract.IRelatable#connectsTo()}.
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link com.bim.compiler.contract.IGeometryValidatable} - Layer 0: Mesh primitive validation</li>
 *   <li>{@link com.bim.compiler.contract.IBIMEntity} - Layer 1: Base contract with mandatory attributes</li>
 *   <li>{@link com.bim.compiler.contract.IIdentifiable} - Layer 2: Deduplication and continuity keys</li>
 *   <li>{@link com.bim.compiler.contract.IRelatable} - Layer 3: Relationship declarations</li>
 *   <li>{@link com.bim.compiler.contract.IAggregatable} - Layer 4: Merge behavior and assembly membership</li>
 *   <li>{@link com.bim.compiler.contract.IValidatable} - Layer 5: Domain rule validation</li>
 *   <li>{@link com.bim.compiler.contract.SharedElementRegistry} - Central registry for shared elements</li>
 * </ul>
 *
 * <h2>LOD400 Dimensional Contracts</h2>
 * <p>Additional contracts for fabrication-level components:
 * <ul>
 *   <li>{@link com.bim.compiler.contract.IPhysicalComponent} - Physical dimensions and connections</li>
 *   <li>{@link com.bim.compiler.contract.IAssemblyMember} - Fit-within-parent verification</li>
 * </ul>
 *
 * <h2>Theoretical Foundations</h2>
 * <ul>
 *   <li>IFC (ISO 16739-1:2018) - Industry Foundation Classes relationship model</li>
 *   <li>Mereotopology - Formal theory of part-whole spatial relationships</li>
 *   <li>RCC-8 (Cohn et al. 1997) - Region Connection Calculus</li>
 *   <li>DDD (Evans 2003) - Aggregate, Entity, Repository patterns</li>
 *   <li>GoF (Gamma et al. 1994) - Registry, Composite, Flyweight patterns</li>
 *   <li>Euler Characteristic - Topological invariant for mesh validation</li>
 *   <li>Manifold Library - Separation of topological vs geometric validity</li>
 *   <li>CGAL Concepts - Polygon mesh processing validation patterns</li>
 * </ul>
 *
 * <h2>Migration Strategy</h2>
 * <ol>
 *   <li><b>Phase 1:</b> Define interfaces (this package) - NON-BREAKING</li>
 *   <li><b>Phase 2:</b> Implement SharedElementRegistry</li>
 *   <li><b>Phase 3:</b> WallAssemblySpec implements IAggregatable - BREAKING</li>
 *   <li><b>Phase 4:</b> Add deduplication to mergeStoreysAtLevel()</li>
 *   <li><b>Phase 5:</b> Full contract enforcement on all specs</li>
 * </ol>
 *
 * @see <a href="docs/contract-architecture-specification.md">Full Specification</a>
 */
package com.bim.compiler.contract;
