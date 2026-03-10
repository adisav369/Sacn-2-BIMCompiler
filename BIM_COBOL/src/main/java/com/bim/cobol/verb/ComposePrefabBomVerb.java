package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * COMPOSE PREFAB BOM &lt;bomId&gt; NAME &lt;name&gt; DESC &lt;desc&gt; TYPE &lt;type&gt;
 *     [CHILD &lt;childBomId&gt; ROLE &lt;role&gt; SEQ &lt;n&gt; MIN_SPACE &lt;mm&gt; ...]
 *
 * <p>Wraps TopologyWriter.writeBom() raw SQL. Idempotent: INSERT OR IGNORE
 * on m_bom PK and m_bom_line rows.
 *
 * <p>Writes to BOM.db — requires bomConn to be writable.
 */
public class ComposePrefabBomVerb implements Verb<ComposePrefabBomVerb.ComposePrefabBomPayload> {

    @Override
    public String keyword() { return "COMPOSE PREFAB BOM"; }

    @Override
    public VerbResult<ComposePrefabBomPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        if (args.length < 6)
            return VerbResult.fail(keyword(),
                "usage: COMPOSE PREFAB BOM <bomId> NAME <name> DESC <desc> TYPE <type> " +
                "[CHILD <childBomId> ROLE <role> SEQ <n> MIN_SPACE <mm> ...]", null);

        String bomId = args[0];

        // Parse named params for the BOM header
        String bomName = null;
        String description = null;
        String bomType = null;

        // Parse children — each CHILD starts a new child block
        List<ChildArg> children = new ArrayList<>();
        ChildArg current = null;

        for (int i = 1; i < args.length; i++) {
            String key = args[i].toUpperCase();
            if (i + 1 < args.length) {
                String val = args[i + 1];
                switch (key) {
                    case "NAME"      -> { bomName = val; i++; continue; }
                    case "DESC"      -> { description = val; i++; continue; }
                    case "TYPE"      -> { bomType = val.toUpperCase(); i++; continue; }
                    case "CHILD"     -> {
                        current = new ChildArg(val, null, 100, 0);
                        children.add(current);
                        i++;
                        continue;
                    }
                    case "ROLE"      -> { if (current != null) current.role = val; i++; continue; }
                    case "SEQ"       -> { if (current != null) current.sequence = Integer.parseInt(val); i++; continue; }
                    case "MIN_SPACE" -> { if (current != null) current.minSpaceMm = Integer.parseInt(val); i++; continue; }
                }
            }
        }

        if (bomName == null)
            return VerbResult.fail(keyword(), "NAME is required", null);
        if (bomType == null)
            return VerbResult.fail(keyword(), "TYPE is required", null);

        Connection conn = ctx.bomConn();

        // INSERT OR IGNORE m_bom
        int bomInserted;
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT OR IGNORE INTO m_bom " +
                "(bom_id, bom_name, description, bom_type, group_by, is_active) " +
                "VALUES (?,?,?,?,?,1)")) {
            stmt.setString(1, bomId);
            stmt.setString(2, bomName);
            stmt.setString(3, description != null ? description : bomName);
            stmt.setString(4, bomType);
            stmt.setString(5, "BUILDING");
            bomInserted = stmt.executeUpdate();
        }

        // INSERT OR IGNORE m_bom_line for each child
        int childrenAdded = 0;
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT OR IGNORE INTO m_bom_line " +
                "(bom_id, child_product_id, component_type, role, sequence, rotation_rule, " +
                " fit_priority, min_space_mm, is_active) " +
                "VALUES (?,?,'MAKE',?,?,?,?,?,1)")) {
            for (ChildArg child : children) {
                stmt.setString(1, bomId);
                stmt.setString(2, child.childBomId);
                stmt.setString(3, child.role);
                stmt.setInt(4, child.sequence);
                stmt.setString(5, "0");
                stmt.setInt(6, 10);
                stmt.setInt(7, child.minSpaceMm);
                childrenAdded += stmt.executeUpdate();
            }
        }

        ComposePrefabBomPayload payload = new ComposePrefabBomPayload(
            bomId, bomInserted > 0, childrenAdded);

        return VerbResult.ok(keyword(),
            String.format("COMPOSE PREFAB BOM %s: inserted=%s, children=%d",
                bomId, bomInserted > 0, childrenAdded),
            payload);
    }

    /** Mutable accumulator for child arguments parsed from the verb line. */
    private static class ChildArg {
        final String childBomId;
        String role;
        int sequence;
        int minSpaceMm;

        ChildArg(String childBomId, String role, int sequence, int minSpaceMm) {
            this.childBomId = childBomId;
            this.role = role;
            this.sequence = sequence;
            this.minSpaceMm = minSpaceMm;
        }
    }

    public record ComposePrefabBomPayload(
        String bomId, boolean bomInserted, int childrenAdded
    ) {}
}
