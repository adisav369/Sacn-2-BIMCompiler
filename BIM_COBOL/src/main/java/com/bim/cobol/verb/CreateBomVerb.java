package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.MBOM;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

/**
 * CREATE BOM &lt;bom_id&gt; TYPE &lt;type&gt; CATEGORY &lt;cat&gt; [DOC_SUB_TYPE &lt;dst&gt;]
 *
 * <p>Level 0 primitive (§18.4). Creates one {@code m_bom} row.
 * Validates that the bom_id uses the SY_ namespace prefix and that
 * the bom_type is one of the five legal values.
 *
 * <p>Writes to BOM.db — requires bomConn to be writable.
 *
 * <p>Grammar:
 * <pre>
 *   CREATE BOM SY_KITCHEN_A TYPE SET CATEGORY KT
 *   CREATE BOM SY_UNIT_01 TYPE UNIT CATEGORY RE DOC_SUB_TYPE SY
 * </pre>
 */
public class CreateBomVerb implements Verb<CreateBomVerb.CreateBomPayload> {

    private static final Set<String> VALID_TYPES =
            Set.of("BUILDING", "FLOOR", "ROOM", "SET", "ITEM");

    @Override
    public String keyword() { return "CREATE BOM"; }

    @Override
    public VerbResult<CreateBomPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        // Parse: CREATE BOM <bom_id> TYPE <type> CATEGORY <cat> [DOC_SUB_TYPE <dst>]
        if (args.length < 4)
            return VerbResult.fail(keyword(),
                "usage: CREATE BOM <bom_id> TYPE <type> CATEGORY <cat> [DOC_SUB_TYPE <dst>]", null);

        String bomId = args[0];

        // Parse named params
        String bomType = null;
        String bomCategory = null;
        String docSubType = null;

        for (int i = 1; i < args.length - 1; i++) {
            String key = args[i].toUpperCase();
            String val = args[i + 1];
            switch (key) {
                case "TYPE"         -> { bomType = val.toUpperCase(); i++; }
                case "CATEGORY"     -> { bomCategory = val.toUpperCase(); i++; }
                case "DOC_SUB_TYPE" -> { docSubType = val.toUpperCase(); i++; }
            }
        }

        if (bomType == null)
            return VerbResult.fail(keyword(), "TYPE is required", null);
        if (bomCategory == null)
            return VerbResult.fail(keyword(), "CATEGORY is required", null);

        // Validate SY_ namespace
        if (!bomId.startsWith("SY_"))
            return VerbResult.fail(keyword(),
                "synthetic BOMs must use SY_ prefix, got: " + bomId, null);

        // Validate type
        if (!VALID_TYPES.contains(bomType))
            return VerbResult.fail(keyword(),
                "invalid bom_type: " + bomType + ", must be one of " + VALID_TYPES, null);

        Connection conn = ctx.bomConn();

        // Check not already exists
        MBOM existing = MBOM.get(conn, bomId);
        if (existing != null)
            return VerbResult.fail(keyword(),
                bomId + " already exists", null);

        // Derive group_by from type — UNIT/FLOOR/ROOM = BUILDING, SET/ITEM = ROOM
        String groupBy = switch (bomType) {
            case "BUILDING", "FLOOR" -> "BUILDING";
            default -> "ROOM";
        };

        // Create the BOM
        MBOM bom = new MBOM(conn);
        bom.setBomId(bomId);
        bom.setBomName(bomId);
        bom.setBomType(bomType);
        bom.setBomCategory(bomCategory);
        bom.setGroupBy(groupBy);
        bom.setIsActive(true);
        bom.setBomLevel(bomType);
        bom.setSeqNo(10);
        bom.setEntityType(MBOM.ENTITYTYPE_User);
        if (docSubType != null)
            bom.setDocSubType(docSubType);
        bom.save();

        CreateBomPayload payload = new CreateBomPayload(
            bomId, bomType, bomCategory, docSubType);

        return VerbResult.ok(keyword(),
            String.format("CREATE BOM %s: type=%s, category=%s%s",
                bomId, bomType, bomCategory,
                docSubType != null ? ", doc_sub_type=" + docSubType : ""),
            payload);
    }

    public record CreateBomPayload(
        String bomId, String bomType, String bomCategory, String docSubType
    ) {}
}
