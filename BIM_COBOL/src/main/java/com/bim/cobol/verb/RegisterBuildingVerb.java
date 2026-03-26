package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * REGISTER BUILDING &lt;buildingId&gt; DOC_SUB_TYPE &lt;dst&gt;
 *     NAME &lt;name&gt; DSL &lt;dsl&gt; OUTPUT_PATH &lt;path&gt; REF_PATH &lt;path&gt;
 *     SEQ &lt;n&gt; EXPECTED &lt;n&gt; PROVENANCE &lt;prov&gt; DESC &lt;desc&gt;
 *     GEO_THRESHOLD &lt;n&gt; AABB_W &lt;w&gt; AABB_D &lt;d&gt; AABB_H &lt;h&gt;
 *
 * <p>Wraps CompilationPipeline.copyCOrderToOutput() raw SQL.
 * INSERT c_order with DocStatus='IP', reads C_DocType metadata from bomConn.
 *
 * <p>Requires bomConn (read c_doctype) + outputConn (write c_order).
 */
public class RegisterBuildingVerb implements Verb<RegisterBuildingVerb.RegisterBuildingPayload> {

    @Override
    public String keyword() { return "REGISTER BUILDING"; }

    @Override
    public VerbResult<RegisterBuildingPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        if (args.length < 3)
            return VerbResult.fail(keyword(),
                "usage: REGISTER BUILDING <buildingId> DOC_SUB_TYPE <dst> " +
                "NAME <name> DSL <dsl> OUTPUT_PATH <path> ...", null);

        String buildingId = args[0];

        // Parse named params
        String docSubType = null, name = null, dslContent = null;
        String outputDbPath = null, referenceDbPath = null;
        boolean isActive = true;
        int seqNo = 0, expectedElements = 0, geoThreshold = 0;
        String provenance = null, description = null;
        double aabbW = 0, aabbD = 0, aabbH = 0;

        for (int i = 1; i < args.length - 1; i++) {
            String key = args[i].toUpperCase();
            String val = args[i + 1];
            switch (key) {
                case "DOC_SUB_TYPE"  -> { docSubType = val.toUpperCase(); i++; }
                case "NAME"          -> { name = val; i++; }
                case "DSL"           -> { dslContent = val; i++; }
                case "OUTPUT_PATH"   -> { outputDbPath = val; i++; }
                case "REF_PATH"      -> { referenceDbPath = val; i++; }
                case "ACTIVE"        -> { isActive = "1".equals(val) || "true".equalsIgnoreCase(val); i++; }
                case "SEQ"           -> { seqNo = Integer.parseInt(val); i++; }
                case "EXPECTED"      -> { expectedElements = Integer.parseInt(val); i++; }
                case "PROVENANCE"    -> { provenance = val; i++; }
                case "DESC"          -> { description = val; i++; }
                case "GEO_THRESHOLD" -> { geoThreshold = Integer.parseInt(val); i++; }
                case "AABB_W"        -> { aabbW = Double.parseDouble(val); i++; }
                case "AABB_D"        -> { aabbD = Double.parseDouble(val); i++; }
                case "AABB_H"        -> { aabbH = Double.parseDouble(val); i++; }
            }
        }

        if (docSubType == null)
            return VerbResult.fail(keyword(), "DOC_SUB_TYPE is required", null);

        Connection outputConn = ctx.outputConn();
        if (outputConn == null)
            return VerbResult.fail(keyword(),
                "outputConn required — REGISTER BUILDING writes to output.db", null);

        // Look up C_DocType Value (text key) from bomConn if available
        // Tier 2: C_DocType_ID is INTEGER PK; Value holds the old text key.
        String docTypeId = null;
        Connection bomConn = ctx.bomConn();
        if (bomConn != null) {
            try (PreparedStatement ps = bomConn.prepareStatement(
                    "SELECT Value FROM C_DocType WHERE DocSubType = ?")) {
                ps.setString(1, docSubType);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) docTypeId = rs.getString(1);
                }
            }
        }

        // INSERT c_order with DocStatus='IP'
        // Tier 2: C_Order_ID is INTEGER PK AUTOINCREMENT. Value holds buildingId.
        try (PreparedStatement ins = outputConn.prepareStatement("""
                INSERT INTO c_order (
                    Value, Name, DSLContent,
                    OutputDbPath, ReferenceDbPath, IsActive, SeqNo,
                    ExpectedElements, Provenance, Description,
                    GeometryFailThreshold, DocStatus,
                    AabbWidthMm, AabbDepthMm, AabbHeightMm,
                    CompiledAt, CompilerVersion, C_DocType_ID
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,datetime('now'),?,?)
                """)) {
            ins.setString(1, buildingId);
            ins.setString(2, name);
            ins.setString(3, dslContent);
            ins.setString(4, outputDbPath);
            ins.setString(5, referenceDbPath);
            ins.setInt(6, isActive ? 1 : 0);
            ins.setInt(7, seqNo);
            ins.setInt(8, expectedElements);
            ins.setString(9, provenance);
            ins.setString(10, description);
            ins.setInt(11, geoThreshold);
            ins.setString(12, "IP");
            ins.setObject(13, aabbW > 0 ? aabbW : null);
            ins.setObject(14, aabbD > 0 ? aabbD : null);
            ins.setObject(15, aabbH > 0 ? aabbH : null);
            ins.setString(16, "BIM-Compiler-1.0");
            ins.setString(17, docTypeId);
            ins.executeUpdate();
        }

        RegisterBuildingPayload payload = new RegisterBuildingPayload(
            buildingId, docTypeId, "IP");

        return VerbResult.ok(keyword(),
            String.format("REGISTER BUILDING %s: DocType=%s, DocStatus=IP",
                buildingId, docTypeId),
            payload);
    }

    public record RegisterBuildingPayload(
        String buildingId, String docTypeId, String docStatus
    ) {}
}
