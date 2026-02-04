package com.bim.compiler.placement;

/**
 * Phase 63: Exception thrown when element placement is invalid.
 *
 * Fail-fast: thrown at construction time, not during validation.
 */
public class PlacementException extends RuntimeException {

    public PlacementException(String message) {
        super(message);
    }

    public PlacementException(String format, Object... args) {
        super(String.format(format, args));
    }

    public PlacementException(String message, Throwable cause) {
        super(message, cause);
    }
}
