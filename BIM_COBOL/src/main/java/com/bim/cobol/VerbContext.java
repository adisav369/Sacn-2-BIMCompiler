package com.bim.cobol;

import java.sql.Connection;

/**
 * Execution context for BIM COBOL verbs.
 *
 * <p>Carries caller-owned connections. Verbs never close connections.
 *
 * @param bomConn       JDBC connection to BOM.db (read-only for CHECK verbs)
 * @param componentConn JDBC connection to component_library.db (nullable — only needed by geometry verbs)
 */
public record VerbContext(Connection bomConn, Connection componentConn) {

    /** Factory for BOM-only verbs. */
    public static VerbContext ofBom(Connection bomConn) {
        return new VerbContext(bomConn, null);
    }

    /** Factory for verbs needing both BOM and component_library connections. */
    public static VerbContext of(Connection bomConn, Connection componentConn) {
        return new VerbContext(bomConn, componentConn);
    }
}
