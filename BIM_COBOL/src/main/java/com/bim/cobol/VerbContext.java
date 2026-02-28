package com.bim.cobol;

import java.sql.Connection;

/**
 * Execution context for BIM COBOL verbs.
 *
 * <p>Carries caller-owned connections. Verbs never close connections.
 *
 * @param bomConn JDBC connection to BOM.db (read-only for CHECK verbs)
 */
public record VerbContext(Connection bomConn) {

    /** Factory for BOM-only verbs. */
    public static VerbContext ofBom(Connection bomConn) {
        return new VerbContext(bomConn);
    }
}
