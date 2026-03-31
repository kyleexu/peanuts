package com.ganten.peanuts.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtils() {}

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static JsonNode readTree(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    public static String toJson(Object value) throws Exception {
        return MAPPER.writeValueAsString(value);
    }

    public static ObjectNode createObjectNode() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode createArrayNode() {
        return MAPPER.createArrayNode();
    }
}
