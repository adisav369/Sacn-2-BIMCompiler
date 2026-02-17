package com.bim.compiler.dsl;

/**
 * Window definition from DSL.
 * Example: WINDOW north
 *
 * Windows are placed on exterior walls only.
 *
 * @param direction which wall the window is on
 */
public record WindowDefinition(
    Direction direction
) {
    public static WindowDefinition parse(String directionStr) {
        Direction dir = Direction.fromKeyword(directionStr);
        return new WindowDefinition(dir);
    }
}
