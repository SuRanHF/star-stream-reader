package com.huazhenghai.readergame.common;

/**
 * Checks balance changes before writing values stored in signed Java/database integers.
 */
public final class ResourceBalance {

    private ResourceBalance() {
    }

    public static int applyDelta(String resourceName, Integer currentBalance, int delta) {
        return applyDelta(resourceName, currentBalance, (long) delta);
    }

    public static int applyDelta(String resourceName, Integer currentBalance, long delta) {
        long nextBalance;
        try {
            nextBalance = Math.addExact(currentBalance == null ? 0L : currentBalance.longValue(), delta);
        } catch (ArithmeticException exception) {
            if (delta > 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        resourceName + "已达到可保存上限，暂时无法领取");
            }
            throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH,
                    resourceName + "不足，无法完成结算");
        }
        if (nextBalance < 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH, resourceName + "不足，无法完成结算");
        }
        if (nextBalance > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, resourceName + "已达到可保存上限，暂时无法领取");
        }
        return (int) nextBalance;
    }

    /** Adds a signed change to an int-backed progress value without allowing wraparound. */
    public static int applySignedDelta(String resourceName, Integer currentValue, int delta) {
        long nextValue = (currentValue == null ? 0L : currentValue.longValue()) + delta;
        if (nextValue < Integer.MIN_VALUE || nextValue > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    resourceName + "超出可保存范围，操作无法完成");
        }
        return (int) nextValue;
    }
}
