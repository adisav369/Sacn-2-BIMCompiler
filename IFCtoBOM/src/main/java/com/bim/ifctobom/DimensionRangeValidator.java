package com.bim.ifctobom;

import com.bim.eyes.EyesConstants;
import com.bim.ifctobom.ExtractionReader.ExtractionElement;
import com.bim.orm.BIMLogger;

import java.sql.*;
import java.util.*;

/**
 * DV010: Validates extracted IFC element dimensions against mined typical ranges.
 *
 * <p>Reads ad_val_rule from ERP.db — 415 DIMENSION_RANGE rules
 * mined from 20 real buildings. For each IFC class, aggregates the min/max
 * observed typical W/D/H across all provenances, then flags elements whose
 * dimensions fall outside [min/ratio, max×ratio].
 *
 * <p>Advisory only — produces a report of outliers but never blocks the pipeline.
 * Designed to run after ExtractionPopulator.populate() and before BOM builders.
 *
 * <p>Usage:
 * <pre>{@code
 *   DimensionRangeValidator drv = DimensionRangeValidator.load(discConn);
 *   DimensionRangeValidator.Report report = drv.validate(allElements, buildingType);
 *   report.print();
 * }</pre>
 *
 * // Implementing DISC_VALIDATION_DB_SRS.md §DV010 — Witness: W-DV-PIPELINE
 */
public class DimensionRangeValidator {

    private static final String TAG = "DimRangeValidator";

    /** Deviation ratio threshold: flag if actual/typical > RATIO or < 1/RATIO. */
    // Implementing EYES_SRS.md §3.5 — P24 alignment: threshold from EyesConstants
    private static final double RATIO = EyesConstants.DIM_RANGE_RATIO;

    /** Aggregated range per IFC class: [minW, maxW, minD, maxD, minH, maxH]. */
    private final Map<String, double[]> rangeByClass;

    private DimensionRangeValidator(Map<String, double[]> rangeByClass) {
        this.rangeByClass = rangeByClass;
    }

    /**
     * Load mined dimension rules from ERP.db and aggregate per IFC class.
     *
     * @param discConn connection to ERP.db
     * @return validator with aggregated ranges, or empty validator if load fails
     */
    public static DimensionRangeValidator load(Connection discConn) {
        Map<String, double[]> ranges = new HashMap<>();

        String sql =
                "SELECT r.ifc_class, p.param_name, CAST(p.param_value AS REAL) "
              + "FROM ad_val_rule r "
              + "JOIN ad_val_rule_param p ON p.ad_val_rule_id = r.ad_val_rule_id "
              + "WHERE r.is_active = 1 AND r.check_method = 'DIMENSION_RANGE'";

        try (PreparedStatement ps = discConn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String ifcClass = rs.getString(1);
                String paramName = rs.getString(2);
                double value = rs.getDouble(3);
                if (value <= 0) continue;

                double[] r = ranges.computeIfAbsent(ifcClass,
                        k -> new double[]{Double.MAX_VALUE, 0, Double.MAX_VALUE, 0, Double.MAX_VALUE, 0});

                switch (paramName) {
                    case "typical_width_mm"  -> { r[0] = Math.min(r[0], value); r[1] = Math.max(r[1], value); }
                    case "typical_depth_mm"  -> { r[2] = Math.min(r[2], value); r[3] = Math.max(r[3], value); }
                    case "typical_height_mm" -> { r[4] = Math.min(r[4], value); r[5] = Math.max(r[5], value); }
                }
            }
        } catch (SQLException e) {
            BIMLogger.warn(TAG, "Failed to load mined rules: {}", e.getMessage());
            return new DimensionRangeValidator(Map.of());
        }

        BIMLogger.info(TAG, "Loaded mined ranges for {} IFC classes", ranges.size());
        return new DimensionRangeValidator(ranges);
    }

    /** @return true if rules were loaded successfully */
    public boolean hasRules() {
        return !rangeByClass.isEmpty();
    }

    /** @return number of IFC classes with mined data */
    public int classCount() {
        return rangeByClass.size();
    }

    /**
     * Validate a list of extracted elements against mined dimension ranges.
     *
     * @param elements    all extracted elements
     * @param buildingType building type for logging
     * @return validation report with outliers grouped by IFC class
     */
    public Report validate(List<ExtractionElement> elements, String buildingType) {
        Map<String, List<Outlier>> outliersByClass = new LinkedHashMap<>();
        int checked = 0, passed = 0;

        for (ExtractionElement e : elements) {
            double[] range = rangeByClass.get(e.ifcClass());
            if (range == null) continue;  // no mined data for this class
            checked++;

            double w = e.widthMm(), d = e.depthMm(), h = e.heightMm();

            String dimFlag = null;
            double actual = 0, rangeMin = 0, rangeMax = 0;

            // Check width
            if (range[1] > 0 && w > 0) {
                if (w > range[1] * RATIO) {
                    dimFlag = "W"; actual = w; rangeMin = range[0]; rangeMax = range[1];
                } else if (w < range[0] / RATIO) {
                    dimFlag = "W"; actual = w; rangeMin = range[0]; rangeMax = range[1];
                }
            }
            // Check depth (only if width passed)
            if (dimFlag == null && range[3] > 0 && d > 0) {
                if (d > range[3] * RATIO) {
                    dimFlag = "D"; actual = d; rangeMin = range[2]; rangeMax = range[3];
                } else if (d < range[2] / RATIO) {
                    dimFlag = "D"; actual = d; rangeMin = range[2]; rangeMax = range[3];
                }
            }
            // Check height
            if (dimFlag == null && range[5] > 0 && h > 0) {
                if (h > range[5] * RATIO) {
                    dimFlag = "H"; actual = h; rangeMin = range[4]; rangeMax = range[5];
                } else if (h < range[4] / RATIO) {
                    dimFlag = "H"; actual = h; rangeMin = range[4]; rangeMax = range[5];
                }
            }

            if (dimFlag != null) {
                outliersByClass.computeIfAbsent(e.ifcClass(), k -> new ArrayList<>())
                        .add(new Outlier(e.elementRef(), e.storey(), dimFlag,
                                actual, rangeMin, rangeMax, w, d, h));
            } else {
                passed++;
            }
        }

        return new Report(buildingType, elements.size(), checked, passed, outliersByClass);
    }

    /**
     * Write advisory rows for dimension outliers to W_Validation_Advisory.
     * One WARNING per outlier, one SUGGESTION with nearest typical value.
     * Called from IFCtoBOMPipeline after validate().
     *
     * // Implementing BIM_Designer_SRS.md §27.5 — Witness: W-FL-ADVISORY-4
     */
    public static void writeAdvisories(Connection discConn, Report report)
            throws SQLException {
        if (!report.hasOutliers()) return;

        // Clear previous advisories for this building (idempotent re-run)
        try (PreparedStatement del = discConn.prepareStatement(
                "DELETE FROM W_Validation_Advisory WHERE building_type = ? AND layer = 'DIMENSION'")) {
            del.setString(1, report.buildingType());
            del.executeUpdate();
        }

        String insert = """
                INSERT INTO W_Validation_Advisory
                (building_type, element_ref, layer, severity, rule_name, message,
                 actual_value, expected_min, expected_max, suggestion)
                VALUES (?, ?, 'DIMENSION', ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = discConn.prepareStatement(insert)) {
            for (var entry : report.outliersByClass().entrySet()) {
                String ifcClass = entry.getKey();
                for (Outlier o : entry.getValue()) {
                    // WARNING row
                    ps.setString(1, report.buildingType());
                    ps.setString(2, o.elementRef());
                    ps.setString(3, "WARNING");
                    ps.setString(4, "DIMENSION_RANGE_" + o.dimension() + ":" + ifcClass);
                    ps.setString(5, String.format(
                            "%s %s=%.0fmm outside typical range [%.0f-%.0f]mm",
                            ifcClass, o.dimension(), o.actual(), o.rangeMin(), o.rangeMax()));
                    ps.setDouble(6, o.actual());
                    ps.setDouble(7, o.rangeMin());
                    ps.setDouble(8, o.rangeMax());
                    ps.setNull(9, java.sql.Types.VARCHAR);
                    ps.addBatch();

                    // SUGGESTION row — nearest typical midpoint
                    double typical = (o.rangeMin() + o.rangeMax()) / 2.0;
                    ps.setString(1, report.buildingType());
                    ps.setString(2, o.elementRef());
                    ps.setString(3, "SUGGESTION");
                    ps.setString(4, "DIMENSION_RANGE_" + o.dimension() + ":" + ifcClass);
                    ps.setString(5, String.format(
                            "Nearest typical %s for %s: %.0fmm",
                            o.dimension(), ifcClass, typical));
                    ps.setDouble(6, o.actual());
                    ps.setDouble(7, o.rangeMin());
                    ps.setDouble(8, o.rangeMax());
                    ps.setString(9, String.format("Nearest typical: %.0fmm", typical));
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }

        BIMLogger.info(TAG, "Wrote {} dimension advisories for {}",
                report.outlierCount() * 2, report.buildingType());
    }

    /** One outlier element. */
    public record Outlier(
            String elementRef, String storey, String dimension,
            double actual, double rangeMin, double rangeMax,
            double w, double d, double h
    ) {}

    /** Validation report. */
    public record Report(
            String buildingType,
            int totalElements, int checked, int passed,
            Map<String, List<Outlier>> outliersByClass
    ) {
        public int outlierCount() {
            return outliersByClass.values().stream().mapToInt(List::size).sum();
        }

        public boolean hasOutliers() {
            return !outliersByClass.isEmpty();
        }

        /** Print report to stdout (pipeline log). */
        public void print() {
            int outliers = outlierCount();
            System.out.printf("[DimRange] %s: %d/%d elements checked, %d PASS, %d outliers%n",
                    buildingType, checked, totalElements, passed, outliers);

            if (outliers == 0) return;

            for (var entry : outliersByClass.entrySet()) {
                String ifcClass = entry.getKey();
                List<Outlier> list = entry.getValue();
                System.out.printf("[DimRange]   %s: %d outlier(s)%n", ifcClass, list.size());
                // Show first 3 per class
                for (int i = 0; i < Math.min(3, list.size()); i++) {
                    Outlier o = list.get(i);
                    System.out.printf("[DimRange]     %s@%s: %s=%.0fmm (range [%.0f-%.0f]mm, actual %.0fx%.0fx%.0fmm)%n",
                            o.elementRef, o.storey, o.dimension,
                            o.actual, o.rangeMin, o.rangeMax, o.w, o.d, o.h);
                }
                if (list.size() > 3) {
                    System.out.printf("[DimRange]     ... and %d more%n", list.size() - 3);
                }
            }
        }
    }
}
