package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * CLONE BOM &lt;source_bom_id&gt; AS &lt;new_bom_id&gt; — recursive BOM tree copy.
 *
 * <p>Creates a deep copy of the source BOM tree with a new root ID.
 * All nested MAKE children are recursively cloned with new bom_ids
 * derived from the target prefix.
 *
 * <p>Writes to BOM.db — requires bomConn to be writable.
 *
 * <p>Grammar: {@code CLONE BOM EB_SH AS EB_SH_UNIT2}
 */
public class CloneBomVerb implements Verb<CloneBomVerb.CloneBomPayload> {

    @Override
    public String keyword() { return "CLONE BOM"; }

    @Override
    public VerbResult<CloneBomPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        if (args.length < 3 || !"AS".equalsIgnoreCase(args[1]))
            return VerbResult.fail(keyword(),
                "usage: CLONE BOM <source_bom_id> AS <new_bom_id>", null);

        String sourceBomId = args[0];
        String targetBomId = args[2];
        Connection conn = ctx.bomConn();

        MBOM source = MBOM.get(conn, sourceBomId);
        if (source == null)
            return VerbResult.fail(keyword(), sourceBomId + " not found", null);

        // Check target doesn't already exist
        MBOM existing = MBOM.get(conn, targetBomId);
        if (existing != null)
            return VerbResult.fail(keyword(),
                targetBomId + " already exists — use a different target ID", null);

        List<String> clonedBoms = new ArrayList<>();
        int totalLines = cloneRecursive(conn, source, sourceBomId, targetBomId, clonedBoms);

        CloneBomPayload payload = new CloneBomPayload(
            sourceBomId, targetBomId, clonedBoms.size(), totalLines, clonedBoms);

        return VerbResult.ok(keyword(),
            String.format("CLONE %s → %s: %d BOMs, %d lines",
                sourceBomId, targetBomId, clonedBoms.size(), totalLines),
            payload);
    }

    /**
     * Recursively clone a BOM tree. Returns total lines cloned.
     */
    private int cloneRecursive(Connection conn, MBOM source,
                                String sourceBomId, String targetBomId,
                                List<String> clonedBoms) throws SQLException {

        // Clone the BOM header
        MBOM target = new MBOM(conn);
        target.setBomId(targetBomId);
        target.setBomName(source.getBomName() != null
            ? source.getBomName().replace(sourceBomId, targetBomId)
            : targetBomId);
        target.setDescription(source.getDescription());
        target.setTargetIfcClass(source.getTargetIfcClass());
        target.setGroupBy(source.getGroupBy());
        target.setIsActive(true);
        target.setBomLevel(source.getBomLevel());
        target.setBomType(source.getBomType());
        target.setBomCategory(source.getBomCategory());
        target.setDocSubType(source.getDocSubType());
        target.setSeqNo(source.getSeqNo());
        target.setOriginX(source.getOriginX());
        target.setOriginY(source.getOriginY());
        target.setOriginZ(source.getOriginZ());
        target.setEntityType(MBOM.ENTITYTYPE_User);
        target.save();
        clonedBoms.add(targetBomId);

        // Clone children
        List<MBOMLine> children = MBOMLine.getByBom(conn, sourceBomId);
        int lineCount = 0;

        for (MBOMLine srcLine : children) {
            MBOMLine tgtLine = new MBOMLine(conn);
            tgtLine.setBomId(targetBomId);
            tgtLine.setChildProductId(srcLine.getChildProductId());
            tgtLine.setChildElementType(srcLine.getChildElementType());
            tgtLine.setChildNamePattern(srcLine.getChildNamePattern());
            tgtLine.setRole(srcLine.getRole());
            tgtLine.setQtyType(srcLine.getQtyType());
            tgtLine.setSequence(srcLine.getSequence());
            tgtLine.setIsActive(true);
            tgtLine.setZRule(srcLine.getZRule());
            tgtLine.setDx(srcLine.getDx());
            tgtLine.setDy(srcLine.getDy());
            tgtLine.setDz(srcLine.getDz());
            tgtLine.setRotationRule(srcLine.getRotationRule());
            tgtLine.setFitPriority(srcLine.getFitPriority());
            tgtLine.setMinSpaceMm(srcLine.getMinSpaceMm());
            tgtLine.setLocatorRef(srcLine.getLocatorRef());
            tgtLine.setIsVariance(srcLine.isVariance());
            tgtLine.setAnchorFace(srcLine.getAnchorFace());
            tgtLine.setLayoutStrategy(srcLine.getLayoutStrategy());
            tgtLine.setAllocatedWidthMm(srcLine.getAllocatedWidthMm());
            tgtLine.setAllocatedDepthMm(srcLine.getAllocatedDepthMm());
            tgtLine.setAllocatedHeightMm(srcLine.getAllocatedHeightMm());
            tgtLine.setComponentType(srcLine.getComponentType());
            tgtLine.setStorey(srcLine.getStorey());
            tgtLine.setElementRef(srcLine.getElementRef());
            tgtLine.setOrdinal(srcLine.getOrdinal());
            tgtLine.setOrientation(srcLine.getOrientation());
            tgtLine.setMaterialName(srcLine.getMaterialName());
            tgtLine.setMaterialRgba(srcLine.getMaterialRgba());
            tgtLine.setEntityType(MBOM.ENTITYTYPE_User);
            tgtLine.save();
            lineCount++;

            // If child is MAKE (nested BOM), recursively clone the child BOM
            if ("MAKE".equals(srcLine.getComponentType())
                    && srcLine.getChildProductId() != null) {
                String childSource = srcLine.getChildProductId();
                MBOM childBom = MBOM.get(conn, childSource);
                if (childBom != null) {
                    // Derive new child BOM ID by replacing source prefix with target prefix
                    String childTarget = childSource.replace(sourceBomId, targetBomId);
                    if (childTarget.equals(childSource)) {
                        // No overlap in naming — prepend target prefix
                        childTarget = targetBomId + "_" + childSource;
                    }
                    // Update the cloned line to point to the new child BOM
                    tgtLine.setChildProductId(childTarget);
                    tgtLine.save();
                    lineCount += cloneRecursive(conn, childBom,
                        childSource, childTarget, clonedBoms);
                }
            }
        }

        return lineCount;
    }

    public record CloneBomPayload(
        String sourceBomId, String targetBomId,
        int bomsCloned, int linesCloned,
        List<String> clonedBomIds
    ) {}
}
