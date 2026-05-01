/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.dsl;

/**
 * Sealed position-rule types for element placement dispatch.
 * Each variant carries only the parameters needed for its computation.
 * Invalid combinations (e.g. FRACTION + unknown host) fail at load time.
 */
sealed interface PositionRule
    permits PositionRule.DirectCoordinate,
            PositionRule.WallFraction,
            PositionRule.RoomFraction,
            PositionRule.BomAnchor {

    /** ABSOLUTE, ENVELOPE, BOUNDARY — center coords in mm */
    record DirectCoordinate(double cxMm, double cyMm) implements PositionRule {}

    /** FRACTION + WALL — fraction along wall + perpendicular offset */
    record WallFraction(String wallRef, double fraction, double perpOffsetMm)
        implements PositionRule {}

    /** FRACTION + ROOM — fractional position within room extent */
    record RoomFraction(String roomRef, double fractionX, double fractionY)
        implements PositionRule {}

    /**
     * Phase BOM-2c: FRACTION + BUILDING or UNIT — UNIT/FLOOR Orderline anchor.
     * BUILDING: triggers full UNIT→FLOOR→SET dispatch (computeUnitAnchor).
     * UNIT: metadata-only FLOOR Orderline, no direct placements.
     */
    record BomAnchor(String hostRef, String hostType) implements PositionRule {}

    /**
     * Parse raw DB columns into a typed position rule.
     * Throws at load time if the combination is invalid.
     */
    static PositionRule from(String positionRule, String hostType, String hostRef,
                              Double positionValue, Double positionValue2) {
        return switch (positionRule) {
            case "ABSOLUTE", "ENVELOPE", "BOUNDARY" ->
                new DirectCoordinate(nn(positionValue), nn(positionValue2));
            case "FRACTION" -> switch (hostType) {
                case "WALL" -> new WallFraction(hostRef, nn(positionValue), nn(positionValue2));
                case "ROOM" -> new RoomFraction(hostRef, nn(positionValue),
                                  positionValue2 != null ? positionValue2 : 0.5);
                case "BUILDING" -> new BomAnchor(hostRef, hostType);
                default -> throw new IllegalArgumentException(
                    "Unknown host_type '" + hostType + "' for FRACTION rule on " + hostRef);
            };
            default -> throw new IllegalArgumentException(
                "Unknown position_rule: '" + positionRule + "'");
        };
    }

    private static double nn(Double v) { return v != null ? v : 0.0; }
}
