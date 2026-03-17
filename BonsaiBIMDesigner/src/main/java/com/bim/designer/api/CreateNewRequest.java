package com.bim.designer.api;

/**
 * Immutable request for creating a new building from scratch.
 *
 * @param buildingName  human-readable project name (e.g. "My Terrace House")
 * @param buildingType  typology: TERRACE, SEMI_D, BUNGALOW
 * @param jurisdiction  country code: MY, US, UK, AU, SG
 * @param siteWidthMm   site width in millimetres
 * @param siteDepthMm   site depth in millimetres
 * @param numBedrooms   number of bedrooms
 * @param numBathrooms  number of bathrooms
 * @param storeys       number of storeys (default 1)
 */
public record CreateNewRequest(
        String buildingName,
        String buildingType,
        String jurisdiction,
        double siteWidthMm,
        double siteDepthMm,
        int numBedrooms,
        int numBathrooms,
        int storeys
) {
    public CreateNewRequest {
        if (buildingName == null || buildingName.isBlank())
            throw new IllegalArgumentException("buildingName must not be blank");
        if (buildingType == null || buildingType.isBlank())
            throw new IllegalArgumentException("buildingType must not be blank");
        if (jurisdiction == null || jurisdiction.isBlank())
            throw new IllegalArgumentException("jurisdiction must not be blank");
        if (siteWidthMm <= 0)
            throw new IllegalArgumentException("siteWidthMm must be positive");
        if (siteDepthMm <= 0)
            throw new IllegalArgumentException("siteDepthMm must be positive");
        if (numBedrooms < 0)
            throw new IllegalArgumentException("numBedrooms must not be negative");
        if (numBathrooms < 0)
            throw new IllegalArgumentException("numBathrooms must not be negative");
        if (storeys <= 0) storeys = 1;
    }
}
