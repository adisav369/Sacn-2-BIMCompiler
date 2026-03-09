package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.MBomCategory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * DEFINE CATEGORY &lt;id&gt; &lt;name&gt; [doc_type &lt;type&gt;]
 *
 * <p>Level 4 catalog verb (§18.9). Creates an M_BomCategory row,
 * extending the grammar with new room/zone types. Zero Java needed
 * for new building types — this is pure data.
 *
 * <p>Grammar:
 * <pre>
 *   DEFINE CATEGORY CL CLASSROOM doc_type Commercial
 *   DEFINE CATEGORY GA GARAGE
 * </pre>
 */
public class DefineCategoryVerb implements Verb<DefineCategoryVerb.DefineCategoryPayload> {

    @Override
    public String keyword() { return "DEFINE CATEGORY"; }

    @Override
    public VerbResult<DefineCategoryPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        if (args.length < 2)
            return VerbResult.fail(keyword(),
                "usage: DEFINE CATEGORY <id> <name> [doc_type <type>]", null);

        String categoryId = args[0].toUpperCase();
        String name = args[1];

        // Parse optional doc_type
        String docType = null;
        for (int i = 2; i < args.length - 1; i++) {
            if ("doc_type".equalsIgnoreCase(args[i])) {
                docType = args[i + 1];
                i++;
            }
        }

        Connection conn = ctx.bomConn();

        // Guard: must not already exist
        MBomCategory existing = MBomCategory.get(conn, categoryId);
        if (existing != null)
            return VerbResult.fail(keyword(),
                "category already exists: " + categoryId + " (" + existing.getName() + ")", null);

        // Create the category
        MBomCategory cat = new MBomCategory(conn);
        cat.setCategoryId(categoryId);
        cat.setName(name);
        cat.setValue(name);  // CamelCase search key
        cat.setIsActive(true);
        if (docType != null)
            cat.setDocType(docType);
        cat.save();

        return VerbResult.ok(keyword(),
            String.format("DEFINE CATEGORY %s (%s)%s",
                categoryId, name,
                docType != null ? " doc_type=" + docType : ""),
            new DefineCategoryPayload(categoryId, name, docType));
    }

    public record DefineCategoryPayload(
        String categoryId, String name, String docType
    ) {}
}
