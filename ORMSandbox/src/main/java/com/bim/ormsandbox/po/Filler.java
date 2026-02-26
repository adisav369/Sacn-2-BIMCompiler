package com.bim.ormsandbox.po;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates interstitial filler elements between consecutive items in a SET BOM.
 *
 * <p>Fillers are real IFC bounding-box elements ({@code is_variance=1}) that tile
 * every gap in the strip. N fixed items produce N−1 fillers. Together with the
 * fixed items they perfectly cover the parent width (ground truth).
 *
 * <p>Axis model: Width=SUM (strip packing), Depth=MAX (clearance), Height=MAX (clearance).
 * Only width is distributed to fillers; depth/height are clearance axes.
 *
 * <p>Uses {@link MBOMLine} DAO for all persistence — load, delete, create.
 */
public final class Filler {

    /** Result of a filler fill operation. */
    public record FillResult(List<MBOMLine> created, int remainW, List<String> warnings) {}

    private Filler() {}   // utility class

    /**
     * Delete old buffers and create interstitial fillers between consecutive fixed items.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Load active children via {@link MBOMLine#getByBom}</li>
     *   <li>Partition into fixed ({@code is_variance=0}) and buffer ({@code is_variance=1})</li>
     *   <li>Delete old buffer records</li>
     *   <li>Renumber fixed children: 10, 30, 50, …</li>
     *   <li>Create N−1 interstitial fillers at 20, 40, 60, …</li>
     *   <li>Distribute remaining width evenly (last filler absorbs rounding remainder)</li>
     * </ol>
     *
     * <p>Persists all changes immediately (delete old + save new). Caller does NOT need
     * to call save() — this is a full lifecycle operation.
     *
     * @param conn     JDBC connection (caller owns transaction)
     * @param bomId    the SET BOM to fill
     * @param parentW  parent allocated width in mm
     * @param parentD  parent allocated depth in mm (clearance check only)
     * @param parentH  parent allocated height in mm (clearance check only)
     * @return result with created fillers, remaining width, and any warnings
     */
    public static FillResult fill(Connection conn, String bomId,
                                  int parentW, int parentD, int parentH)
            throws SQLException {

        List<String> warnings = new ArrayList<>();

        // 1. Load and partition
        List<MBOMLine> children = MBOMLine.getByBom(conn, bomId);
        List<MBOMLine> fixed = new ArrayList<>();
        List<MBOMLine> oldBuffers = new ArrayList<>();

        for (MBOMLine child : children) {
            if (child.isBuffer()) {
                oldBuffers.add(child);
            } else {
                fixed.add(child);
            }
        }

        if (fixed.size() < 2) {
            return new FillResult(List.of(), 0, warnings);
        }

        // 2. Delete old buffer records
        for (MBOMLine old : oldBuffers) {
            old.delete();
        }

        // 3. Clearance warnings
        int fixedSumW = 0, maxD = 0, maxH = 0;
        for (MBOMLine f : fixed) {
            fixedSumW += f.getSpaceWidthMm();
            maxD = Math.max(maxD, f.getSpaceDepthMm());
            maxH = Math.max(maxH, f.getSpaceHeightMm());
        }

        if (parentD > 0 && maxD > parentD) {
            warnings.add(bomId + ": maxD=" + maxD + " exceeds parentD=" + parentD);
        }
        if (parentH > 0 && maxH > parentH) {
            warnings.add(bomId + ": maxH=" + maxH + " exceeds parentH=" + parentH);
        }

        // 4. Remaining width (clamped to 0)
        int remainW = parentW - fixedSumW;
        if (remainW < 0) {
            warnings.add(bomId + ": fixedSumW=" + fixedSumW
                         + " exceeds parentW=" + parentW + " — fillers set to 0");
            remainW = 0;
        }

        // 5. Renumber fixed children: 10, 30, 50, …
        for (int i = 0; i < fixed.size(); i++) {
            MBOMLine f = fixed.get(i);
            f.setSequence((i + 1) * 20 - 10);
            f.save();
        }

        // 6. Create N−1 interstitial fillers: 20, 40, 60, …
        int nFillers = fixed.size() - 1;
        int perFiller = remainW / nFillers;
        List<MBOMLine> created = new ArrayList<>(nFillers);

        for (int i = 0; i < nFillers; i++) {
            int w = (i < nFillers - 1) ? perFiller : remainW - perFiller * (nFillers - 1);
            MBOMLine buf = newFiller(conn, bomId, (i + 1) * 20, w);
            buf.save();
            created.add(buf);
        }

        return new FillResult(List.copyOf(created), remainW, warnings);
    }

    // ── Geometry Helpers ────────────────────────────────────────────────────

    /**
     * Factory: create a new filler MBOMLine (unsaved).
     *
     * @param conn   JDBC connection
     * @param bomId  parent SET BOM
     * @param seq    sequence position
     * @param width  filler width in mm
     * @return new MBOMLine ready for save()
     */
    public static MBOMLine newFiller(Connection conn, String bomId, int seq, int width) {
        MBOMLine buf = new MBOMLine(conn);
        buf.setBomId(bomId);
        buf.setRole("BUFFER");
        buf.setSequence(seq);
        buf.setIsVariance(true);
        buf.setIsActive(true);
        buf.setSpaceWidthMm(width);
        buf.setSpaceDepthMm(0);
        buf.setSpaceHeightMm(0);
        return buf;
    }

    /**
     * Partition children of a BOM into fixed items and filler/buffer items.
     *
     * @return int[2] = {fixedCount, fillerCount}
     */
    public static int[] partition(Connection conn, String bomId) throws SQLException {
        List<MBOMLine> children = MBOMLine.getByBom(conn, bomId);
        int fixed = 0, fillers = 0;
        for (MBOMLine c : children) {
            if (c.isBuffer()) fillers++; else fixed++;
        }
        return new int[]{fixed, fillers};
    }

    /**
     * Expected number of interstitial fillers for a BOM.
     * N fixed items → max(0, N−1) fillers.
     */
    public static int expectedFillerCount(Connection conn, String bomId) throws SQLException {
        int[] p = partition(conn, bomId);
        return Math.max(0, p[0] - 1);
    }

    /**
     * Compute total strip width: sum of all children's space_width_mm (fixed + fillers).
     */
    public static int totalStripWidth(Connection conn, String bomId) throws SQLException {
        List<MBOMLine> children = MBOMLine.getByBom(conn, bomId);
        int total = 0;
        for (MBOMLine c : children) {
            total += c.getSpaceWidthMm();
        }
        return total;
    }

    /**
     * Check if the strip is complete: total child widths == parent width.
     * Ground truth invariant for a fully tiled SET BOM.
     *
     * @param conn     JDBC connection
     * @param bomId    the SET BOM
     * @param parentW  expected parent width in mm
     * @return true if fillers + fixed items perfectly tile the parent width
     */
    public static boolean isStripComplete(Connection conn, String bomId, int parentW)
            throws SQLException {
        return totalStripWidth(conn, bomId) == parentW;
    }

    /**
     * Remaining width: parentW minus sum of fixed (non-buffer) children widths.
     * Negative means fixed items overflow the parent.
     */
    public static int computeRemainingWidth(Connection conn, String bomId, int parentW)
            throws SQLException {
        List<MBOMLine> children = MBOMLine.getByBom(conn, bomId);
        int fixedW = 0;
        for (MBOMLine c : children) {
            if (!c.isBuffer()) fixedW += c.getSpaceWidthMm();
        }
        return parentW - fixedW;
    }

    /**
     * Strip layout summary — ordered list of (role, width) pairs for debugging.
     *
     * @return list of "ROLE:WIDTHmm" strings in sequence order
     */
    public static List<String> describeStrip(Connection conn, String bomId)
            throws SQLException {
        List<MBOMLine> children = MBOMLine.getByBom(conn, bomId);
        List<String> desc = new ArrayList<>(children.size());
        for (MBOMLine c : children) {
            desc.add(c.getRole() + ":" + c.getSpaceWidthMm() + "mm");
        }
        return desc;
    }

    /**
     * Measuring tape: distance between two items in the strip.
     *
     * <p>In a linear strip, the distance between item A and item B is the sum of
     * all element widths (fillers + items) that lie strictly between them in sequence order.
     * If A and B are adjacent, this returns just the filler width between them (or 0
     * if no filler exists).
     *
     * @param conn        JDBC connection
     * @param bomId       the SET BOM containing both items
     * @param childIdA    bom_child_id of the first item
     * @param childIdB    bom_child_id of the second item
     * @return distance in mm between the two items (sum of interstitial widths)
     * @throws IllegalArgumentException if either child not found in BOM
     */
    public static int distanceBetween(Connection conn, String bomId,
                                      int childIdA, int childIdB)
            throws SQLException {

        List<MBOMLine> children = MBOMLine.getByBom(conn, bomId);

        int seqA = -1, seqB = -1;
        for (MBOMLine c : children) {
            if (c.getBomChildId() == childIdA) seqA = c.getSequence();
            if (c.getBomChildId() == childIdB) seqB = c.getSequence();
        }
        if (seqA < 0 || seqB < 0) {
            throw new IllegalArgumentException("Child not found in " + bomId
                + ": childIdA=" + childIdA + " childIdB=" + childIdB);
        }

        int lo = Math.min(seqA, seqB);
        int hi = Math.max(seqA, seqB);

        int gap = 0;
        for (MBOMLine c : children) {
            int s = c.getSequence();
            if (s > lo && s < hi) {
                gap += c.getSpaceWidthMm();
            }
        }
        return gap;
    }

    /**
     * Measuring tape (by role): distance between items identified by role name.
     * Convenience wrapper when bom_child_id is not known.
     *
     * @param conn   JDBC connection
     * @param bomId  the SET BOM
     * @param roleA  role of the first item (e.g. "SOFA")
     * @param roleB  role of the second item (e.g. "PIANO")
     * @return distance in mm between the two items
     * @throws IllegalArgumentException if either role not found
     */
    public static int distanceBetweenRoles(Connection conn, String bomId,
                                           String roleA, String roleB)
            throws SQLException {

        List<MBOMLine> children = MBOMLine.getByBom(conn, bomId);

        int idA = -1, idB = -1;
        for (MBOMLine c : children) {
            if (roleA.equals(c.getRole()) && idA < 0) idA = c.getBomChildId();
            if (roleB.equals(c.getRole()) && idB < 0) idB = c.getBomChildId();
        }
        if (idA < 0 || idB < 0) {
            throw new IllegalArgumentException("Role not found in " + bomId
                + ": roleA=" + roleA + " roleB=" + roleB);
        }
        return distanceBetween(conn, bomId, idA, idB);
    }

    /**
     * Clearance envelope: {maxDepth, maxHeight} across all fixed children.
     * Used to check if fixed items fit within the parent depth/height.
     *
     * @return int[2] = {maxDepth, maxHeight} in mm
     */
    public static int[] clearanceEnvelope(Connection conn, String bomId) throws SQLException {
        List<MBOMLine> children = MBOMLine.getByBom(conn, bomId);
        int maxD = 0, maxH = 0;
        for (MBOMLine c : children) {
            if (!c.isBuffer()) {
                maxD = Math.max(maxD, c.getSpaceDepthMm());
                maxH = Math.max(maxH, c.getSpaceHeightMm());
            }
        }
        return new int[]{maxD, maxH};
    }
}
