package com.huazhenghai.readergame;

import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.Result;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 冒烟测试：验证核心数据结构功能正确。
 * 不依赖 Spring 上下文，无需数据库连接。
 */
class SmokeTest {

    // ─── Result<T> 响应封装 ───

    @Test
    void resultOkContainsData() {
        Result<String> r = Result.ok("hello");
        assertTrue(r.isSuccess());
        assertEquals("hello", r.getData());
        assertNull(r.getError());
    }

    @Test
    void resultFailContainsError() {
        Result<String> r = Result.fail("ERR_CODE", "something wrong");
        assertFalse(r.isSuccess());
        assertNotNull(r.getError());
        assertEquals("ERR_CODE", r.getError().getCode());
        assertEquals("something wrong", r.getError().getMessage());
        assertNull(r.getData());
    }

    @Test
    void resultOkWithNullData() {
        Result<Void> r = Result.ok(null);
        assertTrue(r.isSuccess());
        assertNull(r.getData());
    }

    // ─── ErrorCode 常量 ───

    @Test
    void errorCodesAreStrings() {
        assertEquals("PLAYER_NOT_FOUND", ErrorCode.PLAYER_NOT_FOUND);
        assertEquals("UNAUTHORIZED", ErrorCode.UNAUTHORIZED);
        assertEquals("FORBIDDEN", ErrorCode.FORBIDDEN);
        assertEquals("SERVER_ERROR", ErrorCode.SERVER_ERROR);
    }

    @Test
    void errorCodesHaveDistinctValues() {
        assertNotEquals(ErrorCode.UNAUTHORIZED, ErrorCode.FORBIDDEN);
        assertNotEquals(ErrorCode.PLAYER_NOT_FOUND, ErrorCode.SERVER_ERROR);
    }

    // ─── 基本类型安全 ───

    @Test
    void listOfStringsCanBeCreated() {
        List<String> list = List.of("a", "b", "c");
        assertEquals(3, list.size());
    }
}
