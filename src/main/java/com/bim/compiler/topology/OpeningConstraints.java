package com.bim.compiler.topology;

/**
 * Opening size constraints relative to host wall.
 * From PATTERN G7 in SESSION_STATE.md.
 *
 * Query findings:
 * - avg_width_ratio: 0.555 (55% of wall length)
 * - avg_height_ratio: 0.635 (63.5% of wall height)
 *
 * EXTRACTED - DO NOT INVENT VALUES
 */
public final class OpeningConstraints {

    private OpeningConstraints() {}

    /**
     * Typical opening width as fraction of wall length.
     * From query: avg 0.555
     */
    public static final double TYPICAL_WIDTH_RATIO = 0.55;

    /**
     * Maximum practical opening width ratio.
     * Allow up to 90% of wall length.
     */
    public static final double MAX_WIDTH_RATIO = 0.90;

    /**
     * Typical opening height as fraction of wall height.
     * From query: avg 0.635
     */
    public static final double TYPICAL_HEIGHT_RATIO = 0.65;

    /**
     * Maximum practical opening height ratio.
     * Allow up to 95% of wall height (floor to ceiling).
     */
    public static final double MAX_HEIGHT_RATIO = 0.95;

    /**
     * Minimum edge distance from wall end (meters).
     * From RULE 10: 665mm (UK), 700mm (US) but NOT enforced in this project.
     */
    public static final double MIN_EDGE_DISTANCE = 0.100;  // Relaxed: 100mm minimum

    /**
     * Validate opening dimensions against wall.
     * @param openingWidth opening width in meters
     * @param openingHeight opening height in meters
     * @param wallLength wall length in meters
     * @param wallHeight wall height in meters
     * @return true if opening size is valid
     */
    public static boolean isValidSize(double openingWidth, double openingHeight,
                                       double wallLength, double wallHeight) {
        double widthRatio = openingWidth / wallLength;
        double heightRatio = openingHeight / wallHeight;

        return widthRatio <= MAX_WIDTH_RATIO && heightRatio <= MAX_HEIGHT_RATIO;
    }

    /**
     * Validate opening position within wall.
     * @param openingStart start position along wall (meters from wall start)
     * @param openingWidth opening width (meters)
     * @param wallLength wall length (meters)
     * @return true if opening has sufficient edge clearance
     */
    public static boolean isValidPosition(double openingStart, double openingWidth,
                                          double wallLength) {
        double endDistance = wallLength - (openingStart + openingWidth);
        return openingStart >= MIN_EDGE_DISTANCE && endDistance >= MIN_EDGE_DISTANCE;
    }
}
