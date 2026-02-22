package com.bim.compiler.topologymaker;

/**
 * Immutable site brief — the inputs to one topology generation order.
 *
 * @param widthMm      Site width in millimetres (X axis, across frontage)
 * @param depthMm      Site depth in millimetres (Y axis, street to back)
 * @param numBedrooms  Number of bedrooms required (2–4)
 * @param hasPorch     Whether a front porch zone should be generated
 */
public record SiteEnvelope(int widthMm, int depthMm, int numBedrooms, boolean hasPorch) {

    public SiteEnvelope {
        if (widthMm <= 0) throw new IllegalArgumentException("widthMm must be positive");
        if (depthMm <= 0) throw new IllegalArgumentException("depthMm must be positive");
        if (numBedrooms < 1 || numBedrooms > 6)
            throw new IllegalArgumentException("numBedrooms must be 1–6, got " + numBedrooms);
    }
}
