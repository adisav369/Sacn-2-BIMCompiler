package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * FILL BUFFERS IN BOM &lt;bomId&gt; WIDTH &lt;parentW&gt; DEPTH &lt;parentD&gt; HEIGHT &lt;parentH&gt;
 *
 * <p>Wraps TopologyWriter.fillBuffers() steps 1,3-6:
 * <ol>
 *   <li>Read fixed children (is_variance=0, ordered by sequence)</li>
 *   <li>Clearance warnings for depth/height</li>
 *   <li>Compute remaining width</li>
 *   <li>Renumber fixed children: 10, 30, 50, ...</li>
 *   <li>Insert N-1 interstitial fillers at sequences 20, 40, 60, ...</li>
 * </ol>
 *
 * <p>Does NOT delete existing variance rows (use CLEAR VARIANCE FROM BOM first).
 *
 * <p>Writes to BOM.db — requires bomConn to be writable.
 */
public class FillBuffersVerb implements Verb<FillBuffersVerb.FillBuffersPayload> {

    @Override
    public String keyword() { return "FILL BUFFERS IN BOM"; }

    @Override
    public VerbResult<FillBuffersPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        if (args.length < 7)
            return VerbResult.fail(keyword(),
                "usage: FILL BUFFERS IN BOM <bomId> WIDTH <w> DEPTH <d> HEIGHT <h>", null);

        String bomId = args[0];
        int parentW = 0, parentD = 0, parentH = 0;

        for (int i = 1; i < args.length - 1; i++) {
            String key = args[i].toUpperCase();
            String val = args[i + 1];
            switch (key) {
                case "WIDTH"  -> { parentW = Integer.parseInt(val); i++; }
                case "DEPTH"  -> { parentD = Integer.parseInt(val); i++; }
                case "HEIGHT" -> { parentH = Integer.parseInt(val); i++; }
            }
        }

        Connection conn = ctx.bomConn();
        List<String> warnings = new ArrayList<>();

        // 1. Read fixed children ordered by sequence
        List<int[]> fixed = new ArrayList<>();   // {bom_child_id, allocated_width_mm}
        int fixedSumW = 0, maxD = 0, maxH = 0;

        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT bom_child_id, is_variance, allocated_width_mm, allocated_depth_mm, allocated_height_mm " +
                "FROM m_bom_line WHERE bom_id = ? AND is_active = 1 ORDER BY sequence")) {
            stmt.setString(1, bomId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    if (rs.getInt("is_variance") == 0) {
                        fixed.add(new int[]{rs.getInt("bom_child_id"), rs.getInt("allocated_width_mm")});
                        fixedSumW += rs.getInt("allocated_width_mm");
                        maxD = Math.max(maxD, rs.getInt("allocated_depth_mm"));
                        maxH = Math.max(maxH, rs.getInt("allocated_height_mm"));
                    }
                }
            }
        }

        if (fixed.size() < 2) {
            FillBuffersPayload payload = new FillBuffersPayload(
                bomId, fixed.size(), 0, parentW - fixedSumW, warnings);
            return VerbResult.ok(keyword(),
                String.format("FILL BUFFERS IN BOM %s: %d fixed items, no interstitial gaps",
                    bomId, fixed.size()),
                payload);
        }

        // 2. Clearance warnings
        if (parentD > 0 && maxD > parentD)
            warnings.add(bomId + ": maxD=" + maxD + " exceeds parentD=" + parentD);
        if (parentH > 0 && maxH > parentH)
            warnings.add(bomId + ": maxH=" + maxH + " exceeds parentH=" + parentH);

        // 3. Remaining width (clamped to 0)
        int remainW = parentW - fixedSumW;
        if (remainW < 0) {
            warnings.add(bomId + ": fixedSumW=" + fixedSumW
                    + " exceeds parentW=" + parentW + " — fillers set to 0");
            remainW = 0;
        }

        // 4. Renumber fixed children: 10, 30, 50, ...
        try (PreparedStatement upd = conn.prepareStatement(
                "UPDATE m_bom_line SET sequence = ? WHERE bom_child_id = ?")) {
            for (int i = 0; i < fixed.size(); i++) {
                upd.setInt(1, (i + 1) * 20 - 10);   // 10, 30, 50, ...
                upd.setInt(2, fixed.get(i)[0]);
                upd.executeUpdate();
            }
        }

        // 5. Insert N-1 interstitial fillers: 20, 40, 60, ...
        int nFillers = fixed.size() - 1;
        int perFiller = remainW / nFillers;

        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO m_bom_line " +
                "(bom_id, role, sequence, is_variance, is_active, " +
                " allocated_width_mm, allocated_depth_mm, allocated_height_mm) " +
                "VALUES (?, 'BUFFER', ?, 1, 1, ?, 0, 0)")) {
            for (int i = 0; i < nFillers; i++) {
                int w = (i < nFillers - 1) ? perFiller : remainW - perFiller * (nFillers - 1);
                ins.setString(1, bomId);
                ins.setInt(2, (i + 1) * 20);          // 20, 40, 60, ...
                ins.setInt(3, w);
                ins.executeUpdate();
            }
        }

        FillBuffersPayload payload = new FillBuffersPayload(
            bomId, fixed.size(), nFillers, remainW, warnings);

        return VerbResult.ok(keyword(),
            String.format("FILL BUFFERS IN BOM %s: %d fixed, %d fillers inserted, remainW=%d",
                bomId, fixed.size(), nFillers, remainW),
            payload);
    }

    public record FillBuffersPayload(
        String bomId, int fixedCount, int fillersInserted,
        int remainWidth, List<String> warnings
    ) {}
}
