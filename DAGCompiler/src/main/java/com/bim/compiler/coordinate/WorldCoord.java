/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.coordinate;

/**
 * Final absolute world position for a placed BIM element.
 *
 * <p>Use x(), y(), z(), rotation() to write to PlacedFurniture / ep.writeElementMeta().
 *
 * <p>CONSTRUCTION CONTRACT — enforced by D8 ArchUnit gate (DriftGuardTest):
 * Only {@link LocalCoord#toWorld(StoreyCoord)} and {@link StoreyCoord#asWorld()} may
 * call {@code new WorldCoord(...)}. Both are in this package. No class outside
 * {@code ..coordinate..} may construct WorldCoord directly — the D8 gate blocks it
 * at build time.
 *
 * <p>Rationale: direct WorldCoord construction bypasses the accumulation chain
 * (LocalCoord + StoreyCoord → WorldCoord). The R7 regression happened because storey Z
 * and local Z were added at the wrong level. The sealed type + D8 gate makes that class
 * of error a compile-time failure, not a silent wrong number.
 */
public record WorldCoord(double x, double y, double z, double rotation)
        implements Coordinate {}
