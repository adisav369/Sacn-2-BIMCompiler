package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.SanityCheckAD;

/**
 * Context for AD-aware sanity checks.
 *
 * Provides access to:
 * - SanityCheckAD for threshold lookups
 * - Building parameters (storeys, occupancy, area, height)
 * - Derived classifications (storey type, building class)
 *
 * Passed to checks that need to query thresholds from the AD.
 *
 * Phase 78: Metadata-driven threshold lookups.
 */
public class ADContext {

    private final SanityCheckAD ad;
    private final int storeys;
    private final String occupancy;
    private final double areaM2;
    private final double heightM;
    private final boolean sprinklered;
    private final String jurisdiction;

    // Derived
    private final String storeyType;
    private final String buildingClass;

    public ADContext(SanityCheckAD ad, int storeys, String occupancy,
                     double areaM2, double heightM, boolean sprinklered, String jurisdiction) {
        this.ad = ad;
        this.storeys = storeys;
        this.occupancy = occupancy;
        this.areaM2 = areaM2;
        this.heightM = heightM;
        this.sprinklered = sprinklered;
        this.jurisdiction = jurisdiction;

        // Derive classifications
        this.storeyType = SanityCheckAD.classifyStoreyType(storeys, heightM);
        this.buildingClass = SanityCheckAD.classifyBuildingClass(occupancy, !"R".equals(occupancy));
    }

    // =========================================================================
    // Threshold Lookups (Convenience Methods)
    // =========================================================================

    /**
     * Get threshold value for a check, applying sprinkler bonus if applicable.
     */
    public Double getThreshold(String checkId, String thresholdName) {
        if (ad == null) return null;
        return ad.getThreshold(checkId, thresholdName, occupancy, storeyType,
                               buildingClass, jurisdiction, sprinklered);
    }

    /**
     * Get threshold value without sprinkler bonus.
     */
    public Double getBaseThreshold(String checkId, String thresholdName) {
        if (ad == null) return null;
        return ad.getThreshold(checkId, thresholdName, occupancy, storeyType,
                               buildingClass, jurisdiction, false);
    }

    /**
     * Get threshold spec for audit trail.
     */
    public SanityCheckAD.ThresholdSpec getThresholdSpec(String checkId, String thresholdName) {
        if (ad == null) return null;
        return ad.getThresholdSpec(checkId, thresholdName, occupancy, storeyType,
                                    buildingClass, jurisdiction);
    }

    // =========================================================================
    // Building Parameters
    // =========================================================================

    public SanityCheckAD getAD() { return ad; }
    public int getStoreys() { return storeys; }
    public String getOccupancy() { return occupancy; }
    public double getAreaM2() { return areaM2; }
    public double getHeightM() { return heightM; }
    public boolean isSprinklered() { return sprinklered; }
    public String getJurisdiction() { return jurisdiction; }

    // Derived
    public String getStoreyType() { return storeyType; }
    public String getBuildingClass() { return buildingClass; }
    public boolean isHighRise() { return "HIGH_RISE".equals(storeyType); }
    public boolean isPublic() { return "PUBLIC".equals(buildingClass); }
    public boolean isResidential() { return "RESIDENTIAL".equals(buildingClass); }

    // =========================================================================
    // Factory
    // =========================================================================

    /**
     * Create a null context (for when AD is not available).
     * Checks should fall back to hardcoded defaults.
     */
    public static ADContext nullContext() {
        return new ADContext(null, 0, null, 0, 0, false, "INTERNATIONAL");
    }

    /**
     * Check if AD is available.
     */
    public boolean hasAD() {
        return ad != null && ad.isAvailable();
    }

    @Override
    public String toString() {
        return String.format("ADContext[storeys=%d, occupancy=%s, area=%.0fm², height=%.1fm, " +
                             "sprinklered=%s, type=%s, class=%s]",
            storeys, occupancy, areaM2, heightM, sprinklered, storeyType, buildingClass);
    }
}
