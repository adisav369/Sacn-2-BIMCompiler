package com.bim.compiler.drawing2d.model;

/**
 * An elevation level marker (FFL, GRD, SILL, HEAD, CLG, RIDGE, etc.).
 *
 * code: short identifier from 2d_level_marker table
 * displayText: human-readable label ("GRD. FLOOR LEVEL")
 * elevation: Z coordinate in metres
 */
public record Level(String code, String displayText, double elevation) {
}
