package com.huazhenghai.readergame.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MapUtils 安全解析工具类单元测试。
 */
class MapUtilsTest {

    @Test
    void getLongReturnsValue() {
        Map<String, Object> map = Map.of("id", 42L);
        assertEquals(42L, MapUtils.getLong(map, "id"));
    }

    @Test
    void getLongFromInt() {
        Map<String, Object> map = Map.of("id", 42);
        assertEquals(42L, MapUtils.getLong(map, "id"));
    }

    @Test
    void getLongFromString() {
        Map<String, Object> map = Map.of("id", "42");
        assertEquals(42L, MapUtils.getLong(map, "id"));
    }

    @Test
    void getLongThrowsWhenMissing() {
        Map<String, Object> map = new HashMap<>();
        assertThrows(BusinessException.class, () -> MapUtils.getLong(map, "id"));
    }

    @Test
    void getLongThrowsOnBadFormat() {
        Map<String, Object> map = Map.of("id", "abc");
        assertThrows(BusinessException.class, () -> MapUtils.getLong(map, "id"));
    }

    @Test
    void getIntReturnsValue() {
        Map<String, Object> map = Map.of("val", 100);
        assertEquals(100, MapUtils.getInt(map, "val"));
    }

    @Test
    void getStringReturnsNullForMissing() {
        Map<String, Object> map = new HashMap<>();
        assertNull(MapUtils.getString(map, "key"));
    }

    @Test
    void getStringRequiredThrowsWhenMissing() {
        Map<String, Object> map = new HashMap<>();
        assertThrows(BusinessException.class, () -> MapUtils.getStringRequired(map, "key"));
    }

    @Test
    void getStringRequiredThrowsWhenBlank() {
        Map<String, Object> map = Map.of("key", "   ");
        assertThrows(BusinessException.class, () -> MapUtils.getStringRequired(map, "key"));
    }

    @Test
    void getIntOrDefaultReturnsDefault() {
        Map<String, Object> map = new HashMap<>();
        assertEquals(10, MapUtils.getIntOrDefault(map, "x", 10));
    }
}
