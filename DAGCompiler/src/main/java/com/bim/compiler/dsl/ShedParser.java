/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.dsl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Shed DSL syntax.
 *
 * Example:
 * SHED "garden" size:4x3m height:2.4m {
 *     FOUNDATION slab:150mm
 *     DOOR south size:900x2100
 *     WINDOW north size:1200x833
 *     ROOF pitch:15deg
 * }
 */
public class ShedParser {

    // SHED "name" size:WxDm height:Hm
    private static final Pattern SHED_HEADER = Pattern.compile(
        "SHED\\s+\"([^\"]+)\"\\s+size:(\\d+(?:\\.\\d+)?)x(\\d+(?:\\.\\d+)?)m\\s+height:(\\d+(?:\\.\\d+)?)m"
    );

    // FOUNDATION slab:Tmm
    private static final Pattern FOUNDATION = Pattern.compile(
        "FOUNDATION\\s+slab:(\\d+)mm"
    );

    // DOOR direction size:WxH (W and H in mm)
    private static final Pattern DOOR = Pattern.compile(
        "DOOR\\s+(north|south|east|west)\\s+size:(\\d+)x(\\d+)",
        Pattern.CASE_INSENSITIVE
    );

    // WINDOW direction size:WxH
    private static final Pattern WINDOW = Pattern.compile(
        "WINDOW\\s+(north|south|east|west)\\s+size:(\\d+)x(\\d+)",
        Pattern.CASE_INSENSITIVE
    );

    // ROOF pitch:Ddeg
    private static final Pattern ROOF = Pattern.compile(
        "ROOF\\s+pitch:(\\d+(?:\\.\\d+)?)deg"
    );

    public static ShedDefinition parse(String dsl) {
        String name = null;
        double width = 0, depth = 0, wallHeight = 0;
        double foundationThickness = 0.15;  // Default 150mm
        ShedDefinition.DoorSpec door = null;
        ShedDefinition.WindowSpec window = null;
        ShedDefinition.RoofSpec roof = new ShedDefinition.RoofSpec(15);  // Default 15deg

        // Parse header
        Matcher headerMatcher = SHED_HEADER.matcher(dsl);
        if (headerMatcher.find()) {
            name = headerMatcher.group(1);
            width = Double.parseDouble(headerMatcher.group(2));
            depth = Double.parseDouble(headerMatcher.group(3));
            wallHeight = Double.parseDouble(headerMatcher.group(4));
        } else {
            throw new IllegalArgumentException("Invalid SHED header. Expected: SHED \"name\" size:WxDm height:Hm");
        }

        // Parse foundation
        Matcher foundationMatcher = FOUNDATION.matcher(dsl);
        if (foundationMatcher.find()) {
            foundationThickness = Double.parseDouble(foundationMatcher.group(1)) / 1000.0;
        }

        // Parse door
        Matcher doorMatcher = DOOR.matcher(dsl);
        if (doorMatcher.find()) {
            ShedDefinition.Direction side = ShedDefinition.Direction.valueOf(
                doorMatcher.group(1).toUpperCase()
            );
            double doorWidth = Double.parseDouble(doorMatcher.group(2)) / 1000.0;
            double doorHeight = Double.parseDouble(doorMatcher.group(3)) / 1000.0;
            door = new ShedDefinition.DoorSpec(side, doorWidth, doorHeight);
        }

        // Parse window
        Matcher windowMatcher = WINDOW.matcher(dsl);
        if (windowMatcher.find()) {
            ShedDefinition.Direction side = ShedDefinition.Direction.valueOf(
                windowMatcher.group(1).toUpperCase()
            );
            double winWidth = Double.parseDouble(windowMatcher.group(2)) / 1000.0;
            double winHeight = Double.parseDouble(windowMatcher.group(3)) / 1000.0;
            window = new ShedDefinition.WindowSpec(side, winWidth, winHeight);
        }

        // Parse roof
        Matcher roofMatcher = ROOF.matcher(dsl);
        if (roofMatcher.find()) {
            double pitch = Double.parseDouble(roofMatcher.group(1));
            roof = new ShedDefinition.RoofSpec(pitch);
        }

        return new ShedDefinition(name, width, depth, wallHeight,
                                   foundationThickness, door, window, roof);
    }
}
