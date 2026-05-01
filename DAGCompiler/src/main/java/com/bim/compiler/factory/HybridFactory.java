/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.factory;

import com.bim.compiler.library.ComponentLibrary;
import com.bim.compiler.library.ComponentLibrary.ComponentDefinition;
import com.bim.compiler.model.ISpatialElement;
import com.bim.compiler.util.BIMLogger;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Hybrid factory that routes to parametric or library factories.
 *
 * Decision logic:
 * - LibraryPlacementSpec → LibraryFactory
 * - StairPlacementSpec → Library if components found, else Parametric
 * - ParametricSpec → ParametricFactory (WallBuilder, PipeBuilder, etc.)
 * - Unknown → Error
 */
public class HybridFactory implements IElementFactory<ElementSpec> {

    private final LibraryFactory libraryFactory;
    private final ComponentLibrary componentLibrary;
    // ParametricFactory will wrap existing builders (WallBuilder, PipeBuilder, etc.)

    // Tolerance for stair matching (meters)
    private static final double STAIR_TOLERANCE = 0.5;

    public HybridFactory(ComponentLibrary library) throws SQLException {
        this.componentLibrary = library;
        this.libraryFactory = new LibraryFactory(library);
    }

    @Override
    public ISpatialElement create(ElementSpec spec) {
        if (spec instanceof LibraryPlacementSpec lps) {
            if (libraryFactory.canHandle(lps)) {
                BIMLogger.factoryRoute("LIBRARY", spec.getId(), "LibraryPlacementSpec");
                return libraryFactory.create(lps);
            }
        }

        if (spec instanceof ParametricSpec ps) {
            BIMLogger.factoryRoute("PARAMETRIC", spec.getId(), "ParametricSpec");
            return createParametric(ps);
        }

        throw new IllegalArgumentException(
            "Cannot handle spec type: " + spec.getClass().getSimpleName());
    }

    /**
     * Create a stair assembly from library components or parametric fallback.
     *
     * Phase 14A: Library-composed stairs.
     *
     * @param spec Stair placement specification
     * @return List of elements (flights, railings, stringers)
     */
    public List<ISpatialElement> createStair(StairPlacementSpec spec) {
        List<ISpatialElement> elements = new ArrayList<>();

        try {
            // Try to resolve library components
            resolveStairComponents(spec);

            if (spec.hasLibraryComponents()) {
                BIMLogger.factoryRoute("LIBRARY", "stair_assembly", "StairPlacementSpec");
                elements.addAll(createLibraryStair(spec));
            } else {
                BIMLogger.factoryRoute("PARAMETRIC", "stair_assembly", "StairPlacementSpec (fallback)");
                elements.addAll(createParametricStair(spec));
            }

        } catch (SQLException e) {
            BIMLogger.error("HybridFactory", "Stair creation failed: {}", e.getMessage());
            // Fall back to parametric
            elements.addAll(createParametricStair(spec));
        }

        BIMLogger.info("HybridFactory",
            "Created stair assembly: {} elements (library: {})",
            elements.size(), spec.hasLibraryComponents());

        return elements;
    }

    /**
     * Resolve library components for stair spec.
     */
    private void resolveStairComponents(StairPlacementSpec spec) throws SQLException {
        // Find matching stair flight
        ComponentDefinition flight = componentLibrary.findStairFlight(
            spec.getWidth(),
            spec.getFlightRise(),
            STAIR_TOLERANCE
        );
        if (flight != null) {
            spec.setFlightDefinition(flight);
            BIMLogger.debug("HybridFactory",
                "Found library stair: {} (rise: {:.2f}m)",
                flight.name(),
                flight.localBounds().maxZ() - flight.localBounds().minZ());
        }

        // Find matching railing
        double railingLength = spec.getFlightRun();
        double railingHeight = spec.getFlightRise() + 1.1; // flight rise + guard height
        ComponentDefinition railing = componentLibrary.findRailing(
            railingLength, railingHeight, STAIR_TOLERANCE * 2
        );
        if (railing != null) {
            spec.setRailingDefinition(railing);
        }

        // Find matching stringers
        List<ComponentDefinition> stringers = componentLibrary.findStringers(
            spec.getFlightRise(), STAIR_TOLERANCE
        );
        for (ComponentDefinition s : stringers) {
            spec.addStringerDefinition(s);
        }
    }

    /**
     * Create multiple elements from a grid placement spec.
     */
    public List<ISpatialElement> createGrid(GridPlacementSpec spec) throws SQLException {
        BIMLogger.factoryRoute("LIBRARY", spec.getId(), "GridPlacementSpec");
        return libraryFactory.createGrid(spec);
    }

    private ISpatialElement createParametric(ParametricSpec spec) {
        // TODO: Route to existing builders (WallBuilder, PipeBuilder)
        // For now, throw unsupported
        throw new UnsupportedOperationException(
            "Parametric factory not yet implemented for: " + spec.getType());
    }

    /**
     * Create stair assembly from library components.
     */
    private List<ISpatialElement> createLibraryStair(StairPlacementSpec spec) throws SQLException {
        List<ISpatialElement> elements = new ArrayList<>();
        int index = 0;

        ComponentDefinition flight = spec.getFlightDefinition();

        // Create flights
        for (int f = 0; f < spec.getNumFlights(); f++) {
            var pos = spec.getFlightPosition(f);
            double rot = spec.getFlightRotation(f);

            // Calculate scale factors
            double riseScale = spec.getRiseScaleFactor();
            double widthScale = spec.getWidthScaleFactor();

            String id = "stair_flight_" + (++index);
            var flightElement = libraryFactory.createScaled(
                id,
                flight,
                pos,
                rot,
                widthScale,  // X scale
                1.0,         // Y scale (run)
                riseScale    // Z scale (rise)
            );
            elements.add(flightElement);

            BIMLogger.placement("StairFlight", id, pos, rot);
        }

        // Create railings (if found)
        ComponentDefinition railing = spec.getRailingDefinition();
        if (railing != null) {
            var railingPositions = spec.getRailingPositions();

            // Left railing
            String leftId = "stair_railing_left_" + (++index);
            elements.add(libraryFactory.createScaled(
                leftId, railing,
                railingPositions[0],
                spec.getRotation(),
                1.0, 1.0, 1.0  // No scaling for railings
            ));

            // Right railing
            String rightId = "stair_railing_right_" + (++index);
            elements.add(libraryFactory.createScaled(
                rightId, railing,
                railingPositions[1],
                spec.getRotation(),
                1.0, 1.0, 1.0
            ));
        }

        // Create stringers (if found)
        for (var stringer : spec.getStringerDefinitions()) {
            // Stringers go along each side
            String id = "stair_stringer_" + (++index);
            elements.add(libraryFactory.createScaled(
                id, stringer,
                spec.getStartPosition(),
                spec.getRotation(),
                1.0, 1.0, spec.getRiseScaleFactor()
            ));
        }

        return elements;
    }

    /**
     * Create stair using parametric builder (fallback).
     */
    private List<ISpatialElement> createParametricStair(StairPlacementSpec spec) {
        List<ISpatialElement> elements = new ArrayList<>();

        BIMLogger.warn("HybridFactory",
            "Using parametric stair fallback for {}m rise × {}m width",
            spec.getTotalRise(), spec.getWidth());

        // TODO: Route to existing StairBuilder or ShedCompiler stair code
        // For now, log warning and return empty
        // The DSL compiler can catch this and use its existing parametric stair

        return elements;
    }

    @Override
    public boolean canHandle(ElementSpec spec) {
        if (spec instanceof LibraryPlacementSpec lps) {
            return libraryFactory.canHandle(lps);
        }
        if (spec instanceof ParametricSpec) {
            // TODO: Check with parametric builders
            return false;
        }
        return false;
    }

    @Override
    public String getFactoryType() {
        return "HYBRID";
    }

    /**
     * Get the library factory for direct library operations.
     */
    public LibraryFactory getLibraryFactory() {
        return libraryFactory;
    }
}
