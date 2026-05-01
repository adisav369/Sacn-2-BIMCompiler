/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.validation;

/**
 * Thin facade — delegates to {@link com.bim.eyes.diff.SpatialDiff}.
 * Preserves inner type names (Band, ElementDelta, DiffReport) for backward compatibility.
 * @deprecated Use com.bim.eyes.diff.SpatialDiff directly.
 */
@Deprecated
public class SpatialDiff {

    // Re-export inner types from Eyes
    public enum Band { EXACT, DRIFT, SHIFT, MISSING, EXTRA }

    public record ElementDelta(
        String ifcClass, int indexInClass,
        double deltaMinX_mm, double deltaMaxX_mm,
        double deltaMinY_mm, double deltaMaxY_mm,
        double deltaMinZ_mm, double deltaMaxZ_mm,
        Band band
    ) {
        public double maxDelta() {
            return Math.max(Math.abs(deltaMinX_mm),
                   Math.max(Math.abs(deltaMaxX_mm),
                   Math.max(Math.abs(deltaMinY_mm),
                   Math.max(Math.abs(deltaMaxY_mm),
                   Math.max(Math.abs(deltaMinZ_mm),
                            Math.abs(deltaMaxZ_mm))))));
        }

        static ElementDelta fromEyes(com.bim.eyes.diff.SpatialDiff.ElementDelta e) {
            return new ElementDelta(e.ifcClass(), e.indexInClass(),
                e.deltaMinX_mm(), e.deltaMaxX_mm(),
                e.deltaMinY_mm(), e.deltaMaxY_mm(),
                e.deltaMinZ_mm(), e.deltaMaxZ_mm(),
                Band.valueOf(e.band().name()));
        }
    }

    public record DiffReport(
        java.util.List<ElementDelta> deltas,
        java.util.Map<String, int[]> classCounts,
        int exact, int drift, int shift, int missing, int extra
    ) {
        public boolean isClean() {
            return drift == 0 && shift == 0 && missing == 0 && extra == 0;
        }

        public String summary() {
            // Delegate to Eyes for summary generation
            var eyesReport = toEyes();
            return eyesReport.summary();
        }

        com.bim.eyes.diff.SpatialDiff.DiffReport toEyes() {
            var eyesDeltas = deltas.stream()
                .map(d -> new com.bim.eyes.diff.SpatialDiff.ElementDelta(
                    d.ifcClass(), d.indexInClass(),
                    d.deltaMinX_mm(), d.deltaMaxX_mm(),
                    d.deltaMinY_mm(), d.deltaMaxY_mm(),
                    d.deltaMinZ_mm(), d.deltaMaxZ_mm(),
                    com.bim.eyes.diff.SpatialDiff.Band.valueOf(d.band().name())))
                .toList();
            return new com.bim.eyes.diff.SpatialDiff.DiffReport(
                eyesDeltas, classCounts, exact, drift, shift, missing, extra);
        }

        static DiffReport fromEyes(com.bim.eyes.diff.SpatialDiff.DiffReport e) {
            return new DiffReport(
                e.deltas().stream().map(ElementDelta::fromEyes).toList(),
                e.classCounts(), e.exact(), e.drift(), e.shift(), e.missing(), e.extra());
        }
    }

    public static DiffReport diff(String refDbPath, String outDbPath) {
        return DiffReport.fromEyes(com.bim.eyes.diff.SpatialDiff.diff(refDbPath, outDbPath));
    }

    /** Filter diff to the given set of ifc_class values. Null = all classes. */
    public static DiffReport diff(String refDbPath, String outDbPath, java.util.Set<String> ifcClassFilter) {
        return DiffReport.fromEyes(com.bim.eyes.diff.SpatialDiff.diff(refDbPath, outDbPath, ifcClassFilter));
    }
}
