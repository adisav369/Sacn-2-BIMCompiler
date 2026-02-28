package com.bim.cobol;

import java.sql.SQLException;

/**
 * A BIM COBOL verb — one executable action in the construction language.
 *
 * <p>Verbs are the atoms of the language. Each verb reads BOM state, performs
 * a deterministic computation, and returns a typed result with pass/fail.
 *
 * @param <T> the payload type carried by the verb's result
 */
public interface Verb<T> {

    /** Grammar keyword, e.g. "CHECK BOM", "ROUTE SPRINKLERS". */
    String keyword();

    /** Execute this verb against the given context. */
    VerbResult<T> execute(VerbContext ctx, String... args) throws SQLException;
}
