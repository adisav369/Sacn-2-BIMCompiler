package com.bim.cobol.forge.rebar;

import java.util.Map;

// MS 1347:2020 Table 4.2 minimum concrete cover
public final class CoverRequirements {
    private CoverRequirements() {}

    private static final Map<ExposureClass, Integer> BASE_COVERS = Map.ofEntries(
        Map.entry(ExposureClass.XC1, 20),
        Map.entry(ExposureClass.XC2, 25),
        Map.entry(ExposureClass.XC3, 25),
        Map.entry(ExposureClass.XC4, 30),
        Map.entry(ExposureClass.XD1, 40),
        Map.entry(ExposureClass.XD2, 45),
        Map.entry(ExposureClass.XD3, 50),
        Map.entry(ExposureClass.XS1, 45),
        Map.entry(ExposureClass.XS2, 50),
        Map.entry(ExposureClass.XS3, 55)
    );

    public static int getCover(ExposureClass exposure, String elementType) {
        int cover = BASE_COVERS.getOrDefault(exposure, 30);
        // Formwork tolerance for slabs, beams, columns
        if ("SLAB".equals(elementType) || "BEAM".equals(elementType) || "COLUMN".equals(elementType)) {
            cover += 5;
        }
        return cover;
    }
}
