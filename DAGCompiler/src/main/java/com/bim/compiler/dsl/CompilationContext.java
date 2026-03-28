package com.bim.compiler.dsl;

import com.bim.compiler.compliance.ComplianceReport;
import com.bim.compiler.dsl.BuildingRegistry.BuildingEntry;
import com.bim.compiler.dsl.BuildingSpecs.*;
import com.bim.compiler.dsl.CompilationPipeline.PipelineResult;
import com.bim.compiler.validation.GeometryIntegrityChecker;
import com.bim.compiler.validation.PlacementProver;
import com.bim.compiler.validation.SpatialDigest;
import com.bim.ormsandbox.po.BomTemplateComposer;

import java.sql.*;
import java.util.List;

/**
 * Mutable carrier for pipeline state — each stage reads what it needs and writes its output.
 *
 * Immutable input: {@link BuildingEntry} + precomputed flags.
 * Stage outputs: set by their respective stages.
 */
public class CompilationContext {

    // --- Immutable input ---
    private final BuildingEntry entry;
    private final boolean hasRelationalData;

    // --- Stage outputs ---
    private BuildingDefinition definition;
    private BuildingSpec spec;
    private int elementCount;
    private SpatialDigest.DigestReport digestReport;
    private GeometryIntegrityChecker.CheckReport geometryReport;
    private PlacementProver.ProofReport proofReport;
    private boolean proverSkipped;
    private BomTemplateComposer.CompositionReport compositionReport;
    private List<PlacementLoader.Placement> walkedPlacements;
    private ComplianceReport complianceReport;

    public CompilationContext(BuildingEntry entry) {
        this.entry = entry;
        this.hasRelationalData = queryHasRelationalData(entry.id());
    }

    /**
     * Check if a building has relational data (ad_room_boundary rows).
     * Uses building_id directly — no special-case mapping needed.
     */
    private static boolean queryHasRelationalData(String buildingId) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + System.getProperty("bom.db"));
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM ad_room_boundary WHERE building_type = ?")) {
            ps.setString(1, buildingId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /** Convert accumulated stage outputs into the immutable result record. */
    public PipelineResult toResult() {
        return new PipelineResult(
            entry.id(),
            elementCount,
            digestReport != null ? digestReport.digest() : null,
            proofReport,
            geometryReport,
            proverSkipped
        );
    }

    // --- Getters ---
    public BuildingEntry entry() { return entry; }
    public String buildingId() { return entry.id(); }
    public boolean hasRelationalData() { return hasRelationalData; }
    public BuildingDefinition definition() { return definition; }
    public BuildingSpec spec() { return spec; }
    public int elementCount() { return elementCount; }
    public SpatialDigest.DigestReport digestReport() { return digestReport; }
    public GeometryIntegrityChecker.CheckReport geometryReport() { return geometryReport; }
    public PlacementProver.ProofReport proofReport() { return proofReport; }
    public boolean proverSkipped() { return proverSkipped; }
    public BomTemplateComposer.CompositionReport compositionReport() { return compositionReport; }
    public List<PlacementLoader.Placement> walkedPlacements() { return walkedPlacements; }
    public ComplianceReport complianceReport() { return complianceReport; }

    // --- Setters (called by stages) ---
    public void setDefinition(BuildingDefinition definition) { this.definition = definition; }
    public void setSpec(BuildingSpec spec) { this.spec = spec; }
    public void setElementCount(int elementCount) { this.elementCount = elementCount; }
    public void setDigestReport(SpatialDigest.DigestReport digestReport) { this.digestReport = digestReport; }
    public void setGeometryReport(GeometryIntegrityChecker.CheckReport geometryReport) { this.geometryReport = geometryReport; }
    public void setProofReport(PlacementProver.ProofReport proofReport) { this.proofReport = proofReport; }
    public void setProverSkipped(boolean proverSkipped) { this.proverSkipped = proverSkipped; }
    public void setCompositionReport(BomTemplateComposer.CompositionReport r) { this.compositionReport = r; }
    public void setWalkedPlacements(List<PlacementLoader.Placement> p) { this.walkedPlacements = p; }
    public void setComplianceReport(ComplianceReport r) { this.complianceReport = r; }
}
