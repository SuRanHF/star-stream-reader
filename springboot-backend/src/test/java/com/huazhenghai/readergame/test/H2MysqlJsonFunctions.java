package com.huazhenghai.readergame.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** MySQL JSON function equivalents used only by isolated H2 integration-test schemas. */
public final class H2MysqlJsonFunctions {

    private static final ObjectMapper JSON = new ObjectMapper();

    private H2MysqlJsonFunctions() {}

    public static String jsonExtract(String json, String path) throws Exception {
        if (json == null || path == null) return null;
        String field = path.startsWith("$.") ? path.substring(2) : path;
        JsonNode value = JSON.readTree(json).path(field);
        return value.isMissingNode() ? null : value.toString();
    }

    public static String jsonUnquote(String jsonValue) throws Exception {
        if (jsonValue == null) return null;
        JsonNode value = JSON.readTree(jsonValue);
        return value.isTextual() ? value.asText() : value.toString();
    }
}
