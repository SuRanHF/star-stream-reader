package com.huazhenghai.readergame.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourceBalanceTest {

    @Test
    void acceptsTheLargestPersistableBalance() {
        assertEquals(Integer.MAX_VALUE,
                ResourceBalance.applyDelta("星币", Integer.MAX_VALUE - 1, 1));
    }

    @Test
    void rejectsAnOverflowWithAReadableBusinessError() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> ResourceBalance.applyDelta("星币", Integer.MAX_VALUE, 1));

        assertEquals(ErrorCode.BAD_REQUEST, error.getCode());
        assertEquals("星币已达到可保存上限，暂时无法领取", error.getMessage());
    }

    @Test
    void rejectsLongRewardThatCannotFitInThePersistedBalance() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> ResourceBalance.applyDelta("星币", 0, (long) Integer.MAX_VALUE + 1));

        assertEquals(ErrorCode.BAD_REQUEST, error.getCode());
    }

    @Test
    void rejectsAChangeThatWouldCreateANegativeBalance() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> ResourceBalance.applyDelta("故事碎片", 1, -2));

        assertEquals(ErrorCode.RESOURCE_NOT_ENOUGH, error.getCode());
    }

    @Test
    void treatsAMissingBalanceAsZero() {
        assertEquals(3, ResourceBalance.applyDelta("故事碎片", null, 3));
    }

    @Test
    void signedProgressDeltaAllowsNegativeValuesButRejectsBothIntBoundaries() {
        assertEquals(-2, ResourceBalance.applySignedDelta("世界线偏移", 4, -6));
        assertEquals(Integer.MAX_VALUE,
                ResourceBalance.applySignedDelta("星流热度", Integer.MAX_VALUE - 1, 1));
        assertEquals(Integer.MIN_VALUE,
                ResourceBalance.applySignedDelta("世界线偏移", Integer.MIN_VALUE + 1, -1));

        BusinessException upperOverflow = assertThrows(BusinessException.class,
                () -> ResourceBalance.applySignedDelta("星流热度", Integer.MAX_VALUE, 1));
        BusinessException lowerOverflow = assertThrows(BusinessException.class,
                () -> ResourceBalance.applySignedDelta("世界线偏移", Integer.MIN_VALUE, -1));

        assertEquals(ErrorCode.BAD_REQUEST, upperOverflow.getCode());
        assertEquals(ErrorCode.BAD_REQUEST, lowerOverflow.getCode());
    }
}
