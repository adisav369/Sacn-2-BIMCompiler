package com.bim.cobol;

import com.google.gson.Gson;

/**
 * Typed result of a verb execution.
 *
 * @param pass    true if the verb completed without errors
 * @param verb    the keyword of the verb that produced this result
 * @param summary human-readable one-liner
 * @param payload typed detail object (verb-specific)
 * @param <T>     payload type
 */
public record VerbResult<T>(boolean pass, String verb, String summary, T payload) {

    private static final Gson GSON = new Gson();

    /** Success factory. */
    public static <T> VerbResult<T> ok(String verb, String summary, T payload) {
        return new VerbResult<>(true, verb, summary, payload);
    }

    /** Failure factory. */
    public static <T> VerbResult<T> fail(String verb, String summary, T payload) {
        return new VerbResult<>(false, verb, summary, payload);
    }

    /** Serialize to JSON (for pipeline integration / logging). */
    public String toJson() {
        return GSON.toJson(this);
    }
}
