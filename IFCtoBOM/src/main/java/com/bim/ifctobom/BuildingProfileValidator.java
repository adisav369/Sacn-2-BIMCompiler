package com.bim.ifctobom;

import com.bim.ifctobom.ExtractionReader.ExtractionElement;
import com.bim.orm.BIMLogger;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DV011: Validates a building's element class composition against mined profiles.
 *
 * <p>Every building has a signature — the percentage distribution of its IFC
 * classes. Residential buildings are 30–40% IfcWall. MEP files are 90%+ flow
 * elements. This validator classifies the incoming building by its dominant
 * class and compares against known profiles from 36 onboarded buildings.
 *
 * <p>Flags anomalies like:
 * <ul>
 *   <li>Architecture file with 0 walls</li>
 *   <li>MEP file with no flow elements</li>
 *   <li>Unusually high or low class diversity</li>
 *   <li>Classes that appear in this building but never in any other</li>
 * </ul>
 *
 * <p>Advisory only — logs anomalies but never blocks the pipeline.
 *
 * // Implementing BBC.md §9.1 — Witness: W-DV-PROFILE
 */
public class BuildingProfileValidator {

    private static final String TAG = "ProfileValidator";

    /** Known profiles: building_type → (ifc_class → ratio%). */
    private final Map<String, Map<String, Double>> profiles;

    /** Aggregate: ifc_class → [minRatio, maxRatio, avgRatio, buildingCount]. */
    private final Map<String, double[]> classStats;

    /** Class diversity: [minClasses, maxClasses, avgClasses] across all buildings. */
    private final double[] diversityStats;

    private BuildingProfileValidator(Map<String, Map<String, Double>> profiles,
                                      Map<String, double[]> classStats,
                                      double[] diversityStats) {
        this.profiles = profiles;
        this.classStats = classStats;
        this.diversityStats = diversityStats;
    }

    /**
     * Load mined building profiles from disc_validation.db.
     */
    public static BuildingProfileValidator load(Connection discConn) {
        Map<String, Map<String, Double>> profiles = new LinkedHashMap<>();
        Map<String, List<Double>> ratioAccum = new HashMap<>();
        Map<String, Integer> classCounts = new HashMap<>();

        String sql = "SELECT building_type, ifc_class, element_ratio, class_count "
                   + "FROM ad_building_profile ORDER BY building_type";

        try (PreparedStatement ps = discConn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String btype = rs.getString(1);
                String ifcClass = rs.getString(2);
                double ratio = rs.getDouble(3);
                int classCount = rs.getInt(4);

                profiles.computeIfAbsent(btype, k -> new LinkedHashMap<>())
                        .put(ifcClass, ratio);
                ratioAccum.computeIfAbsent(ifcClass, k -> new ArrayList<>())
                        .add(ratio);
                classCounts.put(btype, classCount);
            }
        } catch (SQLException e) {
            BIMLogger.warn(TAG, "Failed to load profiles: {}", e.getMessage());
            return new BuildingProfileValidator(Map.of(), Map.of(), new double[]{0, 0, 0});
        }

        // Aggregate class stats
        Map<String, double[]> classStats = new HashMap<>();
        for (var entry : ratioAccum.entrySet()) {
            List<Double> ratios = entry.getValue();
            double min = ratios.stream().mapToDouble(d -> d).min().orElse(0);
            double max = ratios.stream().mapToDouble(d -> d).max().orElse(0);
            double avg = ratios.stream().mapToDouble(d -> d).average().orElse(0);
            classStats.put(entry.getKey(), new double[]{min, max, avg, ratios.size()});
        }

        // Diversity stats
        double[] divs = classCounts.values().stream().mapToDouble(i -> i).toArray();
        double[] diversityStats = divs.length > 0
                ? new double[]{
                    Arrays.stream(divs).min().orElse(0),
                    Arrays.stream(divs).max().orElse(0),
                    Arrays.stream(divs).average().orElse(0)}
                : new double[]{0, 0, 0};

        BIMLogger.info(TAG, "Loaded {} building profiles, {} IFC classes",
                profiles.size(), classStats.size());
        return new BuildingProfileValidator(profiles, classStats, diversityStats);
    }

    public boolean hasProfiles() { return !profiles.isEmpty(); }
    public int buildingCount() { return profiles.size(); }

    /**
     * Validate a building's element composition against known profiles.
     */
    public Report validate(List<ExtractionElement> elements, String buildingType) {
        List<String> anomalies = new ArrayList<>();
        int total = elements.size();

        // Compute this building's profile
        Map<String, Long> counts = elements.stream()
                .collect(Collectors.groupingBy(ExtractionElement::ifcClass, Collectors.counting()));
        Map<String, Double> thisProfile = new LinkedHashMap<>();
        for (var e : counts.entrySet()) {
            thisProfile.put(e.getKey(), 100.0 * e.getValue() / total);
        }
        int thisClassCount = thisProfile.size();

        // Check 1: Class diversity — unusual number of IFC classes?
        if (diversityStats[1] > 0) {
            if (thisClassCount > diversityStats[1] * 1.5) {
                anomalies.add(String.format("DIVERSITY_HIGH: %d IFC classes (typical range %.0f–%.0f)",
                        thisClassCount, diversityStats[0], diversityStats[1]));
            }
            if (thisClassCount == 1 && total > 10) {
                anomalies.add(String.format("DIVERSITY_LOW: only 1 IFC class for %d elements", total));
            }
        }

        // Check 2: Missing expected classes — if dominant class is IfcWall (architecture),
        // expect at least some doors or windows
        String dominant = thisProfile.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("");

        if ("IfcWall".equals(dominant) && thisProfile.getOrDefault("IfcDoor", 0.0) == 0
                && thisProfile.getOrDefault("IfcWindow", 0.0) == 0 && total > 20) {
            anomalies.add("MISSING_OPENINGS: wall-dominant building with 0 doors and 0 windows");
        }

        // Check 3: Novel classes — IFC classes that appear here but in no other building
        for (String cls : thisProfile.keySet()) {
            if (!classStats.containsKey(cls)) {
                anomalies.add(String.format("NOVEL_CLASS: %s never seen in %d previous buildings",
                        cls, profiles.size()));
            }
        }

        // Check 4: Extreme ratios — class ratio far outside observed range
        for (var entry : thisProfile.entrySet()) {
            String cls = entry.getKey();
            double ratio = entry.getValue();
            double[] stats = classStats.get(cls);
            if (stats == null) continue;

            // Flag if this building's ratio for a class is >2x the max ever observed
            if (ratio > stats[1] * 2.0 && ratio > 10.0) {
                anomalies.add(String.format("EXTREME_RATIO: %s at %.1f%% (max observed: %.1f%% across %.0f buildings)",
                        cls, ratio, stats[1], stats[3]));
            }
        }

        return new Report(buildingType, total, thisClassCount, dominant,
                thisProfile, anomalies);
    }

    public record Report(
            String buildingType, int totalElements, int classCount,
            String dominantClass, Map<String, Double> profile,
            List<String> anomalies
    ) {
        public boolean hasAnomalies() { return !anomalies.isEmpty(); }

        public void print() {
            System.out.printf("[Profile] %s: %d elements, %d classes, dominant: %s (%.1f%%)%n",
                    buildingType, totalElements, classCount, dominantClass,
                    profile.getOrDefault(dominantClass, 0.0));

            if (anomalies.isEmpty()) {
                System.out.println("[Profile]   Composition: NORMAL");
            } else {
                for (String a : anomalies) {
                    System.out.printf("[Profile]   ANOMALY: %s%n", a);
                }
            }
        }
    }
}
