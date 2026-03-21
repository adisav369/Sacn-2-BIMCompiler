package com.bim.compiler.validation;

import com.bim.eyes.shape.FingerprintComputer;
import com.bim.eyes.shape.ShapeClassifier;
import com.bim.eyes.compare.MultisetComparator;
import com.bim.eyes.compare.BomTracedComparator;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Thin facade — delegates to BIMEyes module.
 * Preserves inner type names (ShapeArchetype, ScaleBand, Fingerprint, MultisetResult,
 * BomTracedResult, BomTracedElement) for backward compatibility.
 *
 * <p>Phase 1 migration: all logic now lives in com.bim.eyes.*.
 * This class will be removed in Phase 3 when all consumers migrate to Eyes imports.
 *
 * @deprecated Use com.bim.eyes.shape.* and com.bim.eyes.compare.* directly.
 */
@Deprecated
public class GeometricFingerprint {

    // ── Type aliases (backward compat) ──

    /** @deprecated Use {@link com.bim.eyes.shape.ShapeArchetype} */
    public enum ShapeArchetype {
        PLANAR, ELONGATED, COMPACT, MIXED;

        public static ShapeArchetype from(com.bim.eyes.shape.ShapeArchetype e) {
            return valueOf(e.name());
        }
        public com.bim.eyes.shape.ShapeArchetype toEyes() {
            return com.bim.eyes.shape.ShapeArchetype.valueOf(name());
        }
    }

    /** @deprecated Use {@link com.bim.eyes.shape.ScaleBand} */
    public enum ScaleBand {
        ARCHITECTURAL, FURNITURE, FITTING, TINY;

        public static ScaleBand from(com.bim.eyes.shape.ScaleBand e) {
            return valueOf(e.name());
        }
        public com.bim.eyes.shape.ScaleBand toEyes() {
            return com.bim.eyes.shape.ScaleBand.valueOf(name());
        }
    }

    /** @deprecated Use {@link com.bim.eyes.shape.Fingerprint} */
    public record Fingerprint(
        String guid, String ifcClass, String elementName,
        double smallMM, double mediumMM, double largeMM,
        double planarity, double elongation, double squareness,
        double volumeM3, double topologyRatio,
        ShapeArchetype archetype, ScaleBand scaleBand,
        double cx, double cy, double cz
    ) {
        public Fingerprint(String guid, String ifcClass, String elementName,
                double smallMM, double mediumMM, double largeMM,
                double planarity, double elongation, double squareness,
                double volumeM3, double topologyRatio,
                ShapeArchetype archetype, ScaleBand scaleBand) {
            this(guid, ifcClass, elementName, smallMM, mediumMM, largeMM,
                 planarity, elongation, squareness, volumeM3, topologyRatio,
                 archetype, scaleBand, Double.NaN, Double.NaN, Double.NaN);
        }

        public String verifyClassConsistency() {
            var eyesFp = toEyes();
            return eyesFp.verifyClassConsistency();
        }

        com.bim.eyes.shape.Fingerprint toEyes() {
            return new com.bim.eyes.shape.Fingerprint(guid, ifcClass, elementName,
                smallMM, mediumMM, largeMM, planarity, elongation, squareness,
                volumeM3, topologyRatio, archetype.toEyes(), scaleBand.toEyes(),
                cx, cy, cz);
        }

        static Fingerprint fromEyes(com.bim.eyes.shape.Fingerprint e) {
            return new Fingerprint(e.guid(), e.ifcClass(), e.elementName(),
                e.smallMM(), e.mediumMM(), e.largeMM(),
                e.planarity(), e.elongation(), e.squareness(),
                e.volumeM3(), e.topologyRatio(),
                ShapeArchetype.from(e.archetype()), ScaleBand.from(e.scaleBand()),
                e.cx(), e.cy(), e.cz());
        }
    }

    // ── Computation (delegates to FingerprintComputer) ──

    public static List<Fingerprint> computeFromExtracted(String extractedDbPath) {
        return FingerprintComputer.computeFromExtracted(extractedDbPath).stream()
            .map(Fingerprint::fromEyes).collect(Collectors.toList());
    }

    public static List<Fingerprint> computeFromOutput(String outputDbPath) {
        return FingerprintComputer.computeFromOutput(outputDbPath).stream()
            .map(Fingerprint::fromEyes).collect(Collectors.toList());
    }

    // ── Equivalence (delegates to ShapeClassifier) ──

    public static String proveEquivalence(Fingerprint a, Fingerprint b, double epsilon) {
        return ShapeClassifier.proveEquivalence(a.toEyes(), b.toEyes(), epsilon);
    }

    // ── Classification (delegates to ShapeClassifier) ──

    public static ShapeArchetype classifyArchetype(double wMm, double dMm, double hMm) {
        return ShapeArchetype.from(ShapeClassifier.classifyArchetype(wMm, dMm, hMm));
    }

    public static ScaleBand classifyScaleBand(double wMm, double dMm, double hMm) {
        return ScaleBand.from(ShapeClassifier.classifyScaleBand(wMm, dMm, hMm));
    }

    public static boolean isHostedOpening(double wMm, double dMm, double hMm) {
        return ShapeClassifier.isHostedOpening(wMm, dMm, hMm);
    }

    // ── Multiset comparison (delegates to MultisetComparator) ──

    /** @deprecated Use {@link MultisetComparator.MultisetResult} */
    public record MultisetResult(
        int extractedCount, int compiledCount, int matched,
        List<String> mismatches, boolean spatialProof
    ) {
        public MultisetResult(int extractedCount, int compiledCount, int matched, List<String> mismatches) {
            this(extractedCount, compiledCount, matched, mismatches, false);
        }
        public boolean isEquivalent() {
            return extractedCount == compiledCount && mismatches.isEmpty();
        }
        public double matchRate() {
            int pairs = Math.min(extractedCount, compiledCount);
            return pairs > 0 ? (100.0 * matched / pairs) : 0;
        }

        static MultisetResult fromEyes(MultisetComparator.MultisetResult e) {
            return new MultisetResult(e.extractedCount(), e.compiledCount(), e.matched(),
                e.mismatches(), e.spatialProof());
        }
    }

    public static MultisetResult proveMultisetEquivalence(
            List<Fingerprint> extracted, List<Fingerprint> compiled, double epsilon) {
        List<com.bim.eyes.shape.Fingerprint> ext = extracted.stream().map(Fingerprint::toEyes).collect(Collectors.toList());
        List<com.bim.eyes.shape.Fingerprint> cmp = compiled.stream().map(Fingerprint::toEyes).collect(Collectors.toList());
        return MultisetResult.fromEyes(MultisetComparator.proveMultisetEquivalence(ext, cmp, epsilon));
    }

    // ── BOM-Traced comparison (delegates to BomTracedComparator) ──

    /** @deprecated Use {@link BomTracedComparator.BomTracedElement} */
    public record BomTracedElement(
        String elementRef, String ifcClass,
        double extCx, double extCy, double extCz,
        double cmpCx, double cmpCy, double cmpCz,
        double shapeDelta, double posDist,
        boolean shapeOK, boolean posOK
    ) {
        static BomTracedElement fromEyes(BomTracedComparator.BomTracedElement e) {
            return new BomTracedElement(e.elementRef(), e.ifcClass(),
                e.extCx(), e.extCy(), e.extCz(), e.cmpCx(), e.cmpCy(), e.cmpCz(),
                e.shapeDelta(), e.posDist(), e.shapeOK(), e.posOK());
        }
    }

    /** @deprecated Use {@link BomTracedComparator.BomTracedResult} */
    public record BomTracedResult(
        int extractedCount, int compiledCount, int paired,
        int shapeOK, int posOK, int bothOK,
        List<BomTracedElement> failures
    ) {
        public double shapeRate() { return paired > 0 ? 100.0 * shapeOK / paired : 0; }
        public double posRate()   { return paired > 0 ? 100.0 * posOK / paired : 0; }
        public double bothRate()  { return paired > 0 ? 100.0 * bothOK / paired : 0; }

        static BomTracedResult fromEyes(BomTracedComparator.BomTracedResult e) {
            return new BomTracedResult(e.extractedCount(), e.compiledCount(), e.paired(),
                e.shapeOK(), e.posOK(), e.bothOK(),
                e.failures().stream().map(BomTracedElement::fromEyes).collect(Collectors.toList()));
        }
    }

    public static BomTracedResult proveBomTraced(
            String extractedDbPath, String outputDbPath, double epsilon, double posTolerance) {
        return BomTracedResult.fromEyes(
            BomTracedComparator.proveBomTraced(extractedDbPath, outputDbPath, epsilon, posTolerance));
    }
}
