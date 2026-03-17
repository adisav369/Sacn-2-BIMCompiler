package com.bim.designer.protocol;

import com.bim.designer.compile.ChangeSet;
import com.bim.designer.compile.ChangeSet.ChangeType;
import com.google.gson.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Gson codec for the ndjson wire format between Java server and
 * Bonsai Python addon.
 *
 * <p>Wire format: one JSON object per line (newline-delimited JSON).
 * No HTTP. No framing. Just TCP + newlines.
 */
public class JsonProtocol {

    private static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .create();

    private JsonProtocol() {}

    /** Parse an incoming request line into a typed accessor. */
    public static Request parseRequest(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        return new Request(obj);
    }

    /** Serialize any response object to JSON. */
    public static String toJson(Object obj) {
        return GSON.toJson(obj);
    }

    /** Parse a ChangeSet from a JSON sub-object. */
    public static ChangeSet parseChangeSet(JsonObject obj) {
        if (obj == null) return new ChangeSet(Set.of(), List.of());

        Set<ChangeType> types = new LinkedHashSet<>();
        if (obj.has("types")) {
            for (JsonElement el : obj.getAsJsonArray("types")) {
                types.add(ChangeType.valueOf(el.getAsString()));
            }
        }

        List<String> paths = new ArrayList<>();
        if (obj.has("affectedPaths")) {
            for (JsonElement el : obj.getAsJsonArray("affectedPaths")) {
                paths.add(el.getAsString());
            }
        }

        return new ChangeSet(types, paths);
    }

    /** Typed accessor for a parsed JSON request. */
    public static class Request {
        private final JsonObject obj;

        Request(JsonObject obj) { this.obj = obj; }

        public String action() {
            return obj.get("action").getAsString();
        }

        public String stringField(String name) {
            JsonElement el = obj.get(name);
            return (el == null || el.isJsonNull()) ? null : el.getAsString();
        }

        public JsonObject objectField(String name) {
            JsonElement el = obj.get(name);
            return (el == null || el.isJsonNull()) ? null : el.getAsJsonObject();
        }
    }
}
