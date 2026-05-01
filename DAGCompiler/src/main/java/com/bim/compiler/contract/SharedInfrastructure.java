/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

import com.bim.compiler.geometry.Point3D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared MEP infrastructure that serves multiple units/spaces.
 *
 * <p>Shared infrastructure elements are created once and referenced by
 * all fixtures that connect to them. This prevents duplicate stacks,
 * mains, and risers.
 *
 * <p>Maps to IfcDistributionSystem with shared elements.
 * <p>Theory: Stack-first placement (existing pattern in PlumbingPlacer)
 *
 * <p>Examples:
 * <ul>
 *   <li>Plumbing stack serving toilets on multiple floors</li>
 *   <li>Electrical riser serving panels on multiple floors</li>
 *   <li>HVAC main duct serving multiple zones</li>
 * </ul>
 */
public final class SharedInfrastructure {

    /**
     * Type of shared infrastructure.
     */
    public enum InfraType {
        /** Plumbing waste stack */
        WASTE_STACK,
        /** Plumbing vent stack */
        VENT_STACK,
        /** Water supply riser */
        SUPPLY_RISER,
        /** Electrical riser/busway */
        ELECTRICAL_RISER,
        /** HVAC main duct */
        HVAC_MAIN,
        /** Gas riser */
        GAS_RISER
    }

    private final String id;
    private final InfraType type;
    private final Point3D basePosition;
    private final List<String> connectedFixtures;
    private final List<String> servesUnits;

    /**
     * Creates a shared infrastructure element.
     *
     * @param id Unique identifier
     * @param type Type of infrastructure
     * @param basePosition Base location (XY position, Z at lowest connection)
     */
    public SharedInfrastructure(String id, InfraType type, Point3D basePosition) {
        this.id = id;
        this.type = type;
        this.basePosition = basePosition;
        this.connectedFixtures = new ArrayList<>();
        this.servesUnits = new ArrayList<>();
    }

    /**
     * Unique identifier for this infrastructure.
     */
    public String id() {
        return id;
    }

    /**
     * Type of infrastructure.
     */
    public InfraType type() {
        return type;
    }

    /**
     * Base position (XY location, Z at lowest point).
     */
    public Point3D basePosition() {
        return basePosition;
    }

    /**
     * Fixtures connected to this infrastructure.
     * Returns unmodifiable view.
     */
    public List<String> connectedFixtures() {
        return Collections.unmodifiableList(connectedFixtures);
    }

    /**
     * Units served by this infrastructure.
     * Returns unmodifiable view.
     */
    public List<String> servesUnits() {
        return Collections.unmodifiableList(servesUnits);
    }

    /**
     * Connect a fixture to this infrastructure.
     *
     * @param fixtureGuid GUID of the fixture
     */
    public void connectFixture(String fixtureGuid) {
        if (!connectedFixtures.contains(fixtureGuid)) {
            connectedFixtures.add(fixtureGuid);
        }
    }

    /**
     * Register a unit as being served by this infrastructure.
     *
     * @param unitId ID of the unit
     */
    public void servesUnit(String unitId) {
        if (!servesUnits.contains(unitId)) {
            servesUnits.add(unitId);
        }
    }

    /**
     * Check if infrastructure is shared (serves multiple units).
     */
    public boolean isShared() {
        return servesUnits.size() > 1;
    }

    /**
     * Get number of connected fixtures.
     */
    public int fixtureCount() {
        return connectedFixtures.size();
    }

    @Override
    public String toString() {
        return String.format("SharedInfra[%s %s, %d fixtures, %d units]",
            type, id, connectedFixtures.size(), servesUnits.size());
    }
}
