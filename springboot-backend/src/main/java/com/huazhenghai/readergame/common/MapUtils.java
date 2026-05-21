package com.huazhenghai.readergame.common;

import java.util.Map;

/**
 * 安全解析 @RequestBody Map 的工具方法.
 * 所有取值操作都有 null 防御，缺参数时抛出 BusinessException 而非 NPE.
 */
public final class MapUtils {

    private MapUtils() {}

    public static long getLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) throw new BusinessException(ErrorCode.MISSING_FIELDS, "缺少参数: " + key);
        if (val instanceof Number) return ((Number) val).longValue();
        try { return Long.parseLong(String.valueOf(val)); } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT, key + " 必须是数字");
        }
    }

    public static int getInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) throw new BusinessException(ErrorCode.MISSING_FIELDS, "缺少参数: " + key);
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(String.valueOf(val)); } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT, key + " 必须是数字");
        }
    }

    public static String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        return String.valueOf(val);
    }

    public static String getStringRequired(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null || String.valueOf(val).isBlank())
            throw new BusinessException(ErrorCode.MISSING_FIELDS, "缺少参数: " + key);
        return String.valueOf(val);
    }

    public static int getIntOrDefault(Map<String, Object> map, String key, int defaultValue) {
        Object val = map.get(key);
        if (val == null) return defaultValue;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(String.valueOf(val)); } catch (NumberFormatException e) { return defaultValue; }
    }

    /** 安全转换内部 int */
    public static int toInt(Object val, int defaultVal) {
        if (val instanceof Number) return ((Number) val).intValue();
        if (val == null) return defaultVal;
        try { return Integer.parseInt(String.valueOf(val)); } catch (NumberFormatException e) { return defaultVal; }
    }
}
