/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.dsl;

import com.bim.compiler.dsl.PlacementLoader.Placement;
import com.bim.orm.BIMLogger;

import java.util.*;

/**
 * Post-placement diagnostic and correction pass.
 *
 * <p>Three passes over the placement list:
 * <ol>
 *   <li><b>HOSTING:</b> For every opening (door/window), find its host wall and log
 *       containment status. Openings not contained in any wall are flagged as ORPHAN.</li>
 *   <li><b>SYMMETRY:</b> For every B_ element, find its A_ counterpart (same productId,
 *       stripped prefix) and log positional drift. Flags pairs where drift exceeds tolerance.</li>
 *   <li><b>SNAP:</b> Walls whose openings protrude on one side only get shifted to contain them.</li>
 * </ol>
 *
 * <p>All diagnostics go to the GEO log (pipeline log file). The corrector does NOT
 * reference IFC input — it reasons purely about compiled output geometry.
 */
public class OpeningContainmentCorrector implements LastInchCorrector {

    private static final Set<String> OPENING_CLASSES = Set.of("IfcDoor", "IfcWindow");
    private static final Set<String> WALL_CLASSES = Set.of("IfcWall", "IfcWallStandardCase");

    private static final double THIN_THRESHOLD_M = 0.500;
    private static final double HOST_PROXIMITY_M = 0.100;  // 100mm — generous for shifted walls
    private static final double SYMMETRY_TOLERANCE_M = 0.050;  // 50mm

    @Override
    public List<Placement> correct(List<Placement> placements) {
        // Index by type and by element ref
        List<Placement> walls = new ArrayList<>();
        List<Placement> openings = new ArrayList<>();
        Map<String, Placement> byRef = new LinkedHashMap<>();
        Map<String, List<Placement>> byProductB = new LinkedHashMap<>();

        for (Placement p : placements) {
            byRef.put(p.elementRef(), p);
            if (WALL_CLASSES.contains(p.ifcClass())) walls.add(p);
            if (OPENING_CLASSES.contains(p.ifcClass())) openings.add(p);
            if (p.elementRef().startsWith("B_")) {
                byProductB.computeIfAbsent(p.productId(), k -> new ArrayList<>()).add(p);
            }
        }

        BIMLogger.info("LASTINCH", "CENSUS walls={} openings={} total={}", walls.size(), openings.size(), placements.size());

        // ── Pass 1: HOSTING — which wall hosts each opening? ─────────────────
        Set<String> hostedOpenings = new LinkedHashSet<>();
        Map<String, List<String>> wallToOpenings = new LinkedHashMap<>();

        for (Placement opening : openings) {
            Placement bestWall = null;
            double bestOverlap = -1;
            ThinAxis bestThin = null;

            for (Placement wall : walls) {
                ThinAxis thin = thinAxis(wall);
                if (thin == null) continue;
                if (!thin.overlapsThick(wall, opening)) continue;

                // Compute overlap on thick axes
                double overlapY = Math.min(wall.maxY(), opening.maxY()) - Math.max(wall.minY(), opening.minY());
                double overlapX = Math.min(wall.maxX(), opening.maxX()) - Math.max(wall.minX(), opening.minX());
                double overlapZ = Math.min(wall.maxZ(), opening.maxZ()) - Math.max(wall.minZ(), opening.minZ());
                double overlap = Math.max(0, overlapX) * Math.max(0, overlapZ);
                if (thin == ThinAxis.X) overlap = Math.max(0, overlapY) * Math.max(0, overlapZ);

                // Check thin-axis proximity
                double gap = Math.max(thin.openingMin(opening) - thin.wallMax(wall),
                                       thin.wallMin(wall) - thin.openingMax(opening));
                if (gap > HOST_PROXIMITY_M) continue;

                if (overlap > bestOverlap) {
                    bestOverlap = overlap;
                    bestWall = wall;
                    bestThin = thin;
                }
            }

            if (bestWall != null) {
                hostedOpenings.add(opening.elementRef());
                wallToOpenings.computeIfAbsent(bestWall.elementRef(), k -> new ArrayList<>()).add(opening.elementRef());

                // Log containment on thin axis
                double oMin = bestThin.openingMin(opening);
                double oMax = bestThin.openingMax(opening);
                double wMin = bestThin.wallMin(bestWall);
                double wMax = bestThin.wallMax(bestWall);
                boolean contained = oMin >= wMin - 0.001 && oMax <= wMax + 0.001;

                BIMLogger.geo("LASTINCH", "HOST  {} → wall {} thin={} opening=[{:.3f},{:.3f}] wall=[{:.3f},{:.3f}] {}",
                    opening.elementRef(), bestWall.elementRef(), bestThin.name,
                    oMin, oMax, wMin, wMax,
                    contained ? "CONTAINED" : String.format("EXPOSED low=%.3f high=%.3f",
                        Math.max(0, wMin - oMin), Math.max(0, oMax - wMax)));
            } else {
                BIMLogger.geo("LASTINCH", "ORPHAN {} {} X=[{:.3f},{:.3f}] Y=[{:.3f},{:.3f}] — no host wall found",
                    opening.elementRef(), opening.ifcClass(),
                    opening.minX(), opening.maxX(), opening.minY(), opening.maxY());
            }
        }

        // Log wall hosting summary
        for (Placement wall : walls) {
            List<String> hosted = wallToOpenings.getOrDefault(wall.elementRef(), List.of());
            if (!hosted.isEmpty()) {
                BIMLogger.geo("LASTINCH", "WALL  {} {} openings={} thin={} X=[{:.3f},{:.3f}] Y=[{:.3f},{:.3f}]",
                    wall.elementRef(), wall.productId(), hosted.size(),
                    thinAxis(wall) != null ? thinAxis(wall).name : "?",
                    wall.minX(), wall.maxX(), wall.minY(), wall.maxY());
            }
        }

        int orphanCount = openings.size() - hostedOpenings.size();
        BIMLogger.geo("LASTINCH", "HOSTING: {}/{} openings hosted, {} orphan",
            hostedOpenings.size(), openings.size(), orphanCount);

        // ── Pass 2: SYMMETRY — A/B pair audit by product+storey ─────────────
        // Group A and B elements by (productId, storey), pair by index, check:
        //   (a) dimensions should match (same product)
        //   (b) A_cx + B_cx ≈ 2*centerX, A_cy + B_cy ≈ 2*centerY (rot=π symmetry)
        Map<String, List<Placement>> aByKey = new LinkedHashMap<>();
        Map<String, List<Placement>> bByKey = new LinkedHashMap<>();
        for (Placement p : placements) {
            String key = p.productId() + "|" + p.storey();
            if (p.elementRef().startsWith("A_")) {
                aByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
            } else if (p.elementRef().startsWith("B_")) {
                bByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
            }
        }

        // Estimate rotation center from all A+B centroids
        double sumCx = 0, sumCy = 0;
        int abCount = 0;
        for (String key : aByKey.keySet()) {
            List<Placement> aList = aByKey.get(key);
            List<Placement> bList = bByKey.getOrDefault(key, List.of());
            int n = Math.min(aList.size(), bList.size());
            for (int i = 0; i < n; i++) {
                sumCx += aList.get(i).cx() + bList.get(i).cx();
                sumCy += aList.get(i).cy() + bList.get(i).cy();
                abCount++;
            }
        }
        double centerX = abCount > 0 ? sumCx / (2 * abCount) : 0;
        double centerY = abCount > 0 ? sumCy / (2 * abCount) : 0;
        BIMLogger.geo("LASTINCH", "SYMMETRY center estimate: ({:.3f}, {:.3f}) from {} pairs", centerX, centerY, abCount);

        int pairCount = 0, dimDrift = 0, posDrift = 0;
        for (String key : aByKey.keySet()) {
            List<Placement> aList = aByKey.get(key);
            List<Placement> bList = bByKey.getOrDefault(key, List.of());
            // Sort both by cross-axis for consistent pairing
            aList.sort(Comparator.comparingDouble(Placement::cy).thenComparingDouble(Placement::cx));
            bList.sort(Comparator.comparingDouble((Placement p) -> -p.cy()).thenComparingDouble(p -> -p.cx()));
            int n = Math.min(aList.size(), bList.size());
            for (int i = 0; i < n; i++) {
                Placement a = aList.get(i);
                Placement b = bList.get(i);
                pairCount++;

                // Dimension check
                double dW = Math.abs(a.dx() - b.dx());
                double dD = Math.abs(a.dy() - b.dy());
                double dH = Math.abs(a.dz() - b.dz());
                if (dW > SYMMETRY_TOLERANCE_M || dD > SYMMETRY_TOLERANCE_M || dH > SYMMETRY_TOLERANCE_M) {
                    dimDrift++;
                    BIMLogger.geo("LASTINCH", "DIMS  {} A={} B={} A=[{:.3f}×{:.3f}×{:.3f}] B=[{:.3f}×{:.3f}×{:.3f}] delta=({:.3f},{:.3f},{:.3f})",
                        a.productId(), a.elementRef(), b.elementRef(),
                        a.dx(), a.dy(), a.dz(), b.dx(), b.dy(), b.dz(), dW, dD, dH);
                }

                // Position check: A_c + B_c should ≈ 2*center
                double symErrX = Math.abs((a.cx() + b.cx()) / 2 - centerX);
                double symErrY = Math.abs((a.cy() + b.cy()) / 2 - centerY);
                if (symErrX > SYMMETRY_TOLERANCE_M || symErrY > SYMMETRY_TOLERANCE_M) {
                    posDrift++;
                    BIMLogger.geo("LASTINCH", "ASYM  {} A={} B={} A_pos=({:.3f},{:.3f}) B_pos=({:.3f},{:.3f}) midpoint=({:.3f},{:.3f}) expected=({:.3f},{:.3f}) err=({:.3f},{:.3f})",
                        a.productId(), a.elementRef(), b.elementRef(),
                        a.cx(), a.cy(), b.cx(), b.cy(),
                        (a.cx() + b.cx()) / 2, (a.cy() + b.cy()) / 2,
                        centerX, centerY, symErrX, symErrY);
                }
            }

            // Count mismatches
            if (aList.size() != bList.size()) {
                BIMLogger.geo("LASTINCH", "COUNT {} storey={}: A={} B={} (mismatch={})",
                    aList.get(0).productId(), aList.get(0).storey(),
                    aList.size(), bList.size(), Math.abs(aList.size() - bList.size()));
            }
        }
        BIMLogger.geo("LASTINCH", "SYMMETRY: {}/{} pairs, {} dim drift, {} pos asymmetry",
            dimDrift + posDrift, pairCount, dimDrift, posDrift);

        // ── Pass 3: SNAP — correct walls with one-sided opening protrusion ───
        List<Placement> result = new ArrayList<>(placements);
        int corrections = 0;

        for (int i = 0; i < result.size(); i++) {
            Placement wall = result.get(i);
            if (!WALL_CLASSES.contains(wall.ifcClass())) continue;

            ThinAxis thin = thinAxis(wall);
            if (thin == null) continue;

            List<String> hostedRefs = wallToOpenings.getOrDefault(wall.elementRef(), List.of());
            if (hostedRefs.isEmpty()) continue;

            // Collect hosted openings
            List<Placement> hostedPlacements = new ArrayList<>();
            for (String ref : hostedRefs) {
                Placement o = byRef.get(ref);
                if (o != null) hostedPlacements.add(o);
            }

            double shift = computeShift(wall, hostedPlacements, thin);
            if (Math.abs(shift) < 0.001) continue;

            Placement corrected = thin.shift(wall, shift);
            result.set(i, corrected);
            corrections++;

            BIMLogger.geo("LASTINCH", "SNAP  {} shift={:.3f}m on {}: [{:.3f},{:.3f}] → [{:.3f},{:.3f}] ({} openings)",
                wall.elementRef(), shift, thin.name,
                thin.wallMin(wall), thin.wallMax(wall),
                thin.wallMin(corrected), thin.wallMax(corrected),
                hostedRefs.size());
        }

        BIMLogger.geo("LASTINCH", "SNAP: {} wall(s) corrected", corrections);

        return result;
    }

    // ── Thin-axis abstraction ────────────────────────────────────────────────

    enum ThinAxis {
        X("X") {
            double wallMin(Placement p) { return p.minX(); }
            double wallMax(Placement p) { return p.maxX(); }
            double extent(Placement p) { return p.dx(); }
            boolean overlapsThick(Placement w, Placement o) {
                return o.minY() < w.maxY() && o.maxY() > w.minY()
                    && o.minZ() < w.maxZ() && o.maxZ() > w.minZ();
            }
            double openingMin(Placement p) { return p.minX(); }
            double openingMax(Placement p) { return p.maxX(); }
            Placement shift(Placement p, double d) {
                return new Placement(p.buildingType(), p.storey(), p.ifcClass(),
                    p.elementRef(), p.ordinal(),
                    p.minX() + d, p.maxX() + d, p.minY(), p.maxY(), p.minZ(), p.maxZ(),
                    p.orientation(), p.discipline(), p.materialName(), p.materialRgba(),
                    p.familyRef(), p.productId());
            }
        },
        Y("Y") {
            double wallMin(Placement p) { return p.minY(); }
            double wallMax(Placement p) { return p.maxY(); }
            double extent(Placement p) { return p.dy(); }
            boolean overlapsThick(Placement w, Placement o) {
                return o.minX() < w.maxX() && o.maxX() > w.minX()
                    && o.minZ() < w.maxZ() && o.maxZ() > w.minZ();
            }
            double openingMin(Placement p) { return p.minY(); }
            double openingMax(Placement p) { return p.maxY(); }
            Placement shift(Placement p, double d) {
                return new Placement(p.buildingType(), p.storey(), p.ifcClass(),
                    p.elementRef(), p.ordinal(),
                    p.minX(), p.maxX(), p.minY() + d, p.maxY() + d, p.minZ(), p.maxZ(),
                    p.orientation(), p.discipline(), p.materialName(), p.materialRgba(),
                    p.familyRef(), p.productId());
            }
        };

        final String name;
        ThinAxis(String name) { this.name = name; }

        abstract double wallMin(Placement p);
        abstract double wallMax(Placement p);
        abstract double extent(Placement p);
        abstract boolean overlapsThick(Placement wall, Placement opening);
        abstract double openingMin(Placement p);
        abstract double openingMax(Placement p);
        abstract Placement shift(Placement p, double delta);
    }

    private static ThinAxis thinAxis(Placement wall) {
        if (wall.dx() < THIN_THRESHOLD_M && wall.dy() >= THIN_THRESHOLD_M) return ThinAxis.X;
        if (wall.dy() < THIN_THRESHOLD_M && wall.dx() >= THIN_THRESHOLD_M) return ThinAxis.Y;
        return null;
    }

    private static double computeShift(Placement wall, List<Placement> hostedOpenings, ThinAxis thin) {
        double wMin = thin.wallMin(wall);
        double wMax = thin.wallMax(wall);

        double oMin = Double.MAX_VALUE, oMax = -Double.MAX_VALUE;
        for (Placement o : hostedOpenings) {
            oMin = Math.min(oMin, thin.openingMin(o));
            oMax = Math.max(oMax, thin.openingMax(o));
        }

        double protrudeLow = wMin - oMin;
        double protrudeHigh = oMax - wMax;
        double wallThickness = wMax - wMin;
        double totalProtrusion = Math.max(protrudeLow, 0) + Math.max(protrudeHigh, 0);

        if (totalProtrusion < 0.010) return 0;
        if (totalProtrusion > wallThickness) return 0;

        if (protrudeLow > 0.001 && protrudeHigh <= 0.001) {
            return -protrudeLow;
        } else if (protrudeHigh > 0.001 && protrudeLow <= 0.001) {
            return protrudeHigh;
        }
        return 0;
    }
}
