package com.bim.ifctobom;

import java.sql.*;
import java.util.*;

/**
 * Holds the {@link ExtractionElement} record type used across the IFCtoBOM pipeline.
 *
 * <p>R13: No DB reads — enriched elements are produced in-memory by
 * {@link ExtractionPopulator#populate} and never persisted as a table.
 */
public class ExtractionReader {

    /**
     * One extracted IFC element with its spatial data.
     * Coordinates in metres (from the reference extraction DB).
     */
    public record ExtractionElement(
            String storey, String ifcClass, String elementRef, int ordinal,
            double minX, double maxX, double minY, double maxY, double minZ, double maxZ,
            String orientation, String discipline, String materialName, String materialRgba,
            String mProductId, String guid
    ) {
        public double centroidX() { return (minX + maxX) / 2; }
        public double centroidY() { return (minY + maxY) / 2; }
        public double centroidZ() { return (minZ + maxZ) / 2; }

        /** Element width in mm. */
        public double widthMm() { return (maxX - minX) * 1000; }
        /** Element depth in mm. */
        public double depthMm() { return (maxY - minY) * 1000; }
        /** Element height in mm. */
        public double heightMm() { return (maxZ - minZ) * 1000; }
    }

    // R13: readByStorey/readAll REMOVED — enriched elements produced in-memory
    // by ExtractionPopulator.populate(). No I_Element_Extraction table exists.
}
