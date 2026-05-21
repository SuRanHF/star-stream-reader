package com.huazhenghai.readergame.common;

/**
 * 业务异常.
 * <p>
 * 所有 Service 层抛出的已知业务错误都应使用此类.
 * 全局异常处理器 ({@code GlobalExceptionHandler}) 会捕获并转换为 {@link Result#fail}.
 * </p>
 *
 * <p>使用示例:
 * <pre>{@code
 * if (player == null) {
 *     throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在: id=" + playerId);
 * }
 * }</pre>
 * </p>
 */
public class BusinessException extends RuntimeException {

    /** 错误码 (见 {@link ErrorCode}) */
    private final String code;

    /**
     * 通过错误码和描述构造业务异常.
     *
     * @param code    错误码 (推荐使用 {@link ErrorCode} 中的常量)
     * @param message 人类可读的错误描述
     */
    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 仅通过错误码构造, 描述使用默认值.
     *
     * @param code 错误码
     */
    public BusinessException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
