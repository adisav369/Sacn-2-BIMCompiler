package com.bim.cobol;

import java.sql.Connection;

/**
 * Execution context for BIM COBOL verbs.
 *
 * <p>Carries caller-owned connections. Verbs never close connections.
 *
 * @param bomConn       JDBC connection to BOM.db (read-only for CHECK verbs)
 * @param componentConn JDBC connection to component_library.db (nullable — only needed by geometry verbs)
 * @param outputConn    JDBC connection to output.db (nullable — only needed by emitting verbs like PLACE BOM)
 */
public record VerbContext(Connection bomConn, Connection componentConn, Connection outputConn) {

    /** Factory for BOM-only verbs. */
    public static VerbContext ofBom(Connection bomConn) {
        return new VerbContext(bomConn, null, null);
    }

    /** Factory for verbs needing both BOM and component_library connections. */
    public static VerbContext of(Connection bomConn, Connection componentConn) {
        return new VerbContext(bomConn, componentConn, null);
    }

    /** Factory for emitting verbs needing BOM, component_library, and output connections. */
    public static VerbContext withOutput(Connection bomConn, Connection componentConn, Connection outputConn) {
        return new VerbContext(bomConn, componentConn, outputConn);
    }
}
