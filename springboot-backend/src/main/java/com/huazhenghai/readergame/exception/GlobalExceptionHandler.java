package com.huazhenghai.readergame.exception;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全局异常处理器.
 * <p>
 * 统一拦截 Controller 层抛出的异常,
 * 转换为标准的 {@link Result} 响应格式.
 * </p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Set<String> AUTH_CODES = Set.of(
            ErrorCode.UNAUTHORIZED, ErrorCode.TOKEN_INVALID);
    private static final Set<String> FORBIDDEN_CODES = Set.of(
            ErrorCode.FORBIDDEN);
    private static final Set<String> NOT_FOUND_CODES = Set.of(
            ErrorCode.NOT_FOUND, ErrorCode.PLAYER_NOT_FOUND,
            ErrorCode.CHAPTER_NOT_FOUND, ErrorCode.LOCATION_NOT_FOUND,
            ErrorCode.ITEM_NOT_FOUND, ErrorCode.EQUIP_NOT_FOUND,
            ErrorCode.SKILL_NOT_FOUND, ErrorCode.MONSTER_NOT_FOUND,
            ErrorCode.SAVE_NOT_FOUND, ErrorCode.CHALLENGE_NOT_FOUND);

    /**
     * 业务异常处理.
     * 根据错误码返回不同的 HTTP 状态码:
     * UNAUTHORIZED/TOKEN_INVALID→401, FORBIDDEN→403, NOT_FOUND→404, 其余→400.
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Object> handleBusinessException(BusinessException e, HttpServletResponse response) {
        log.warn("业务异常 [{}]: {}", e.getCode(), e.getMessage());
        if (AUTH_CODES.contains(e.getCode())) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
        } else if (FORBIDDEN_CODES.contains(e.getCode())) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
        } else if (NOT_FOUND_CODES.contains(e.getCode())) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
        } else {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
        }
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验失败处理 (JSR-303 / @Valid).
     * <p>
     * 提取所有字段校验错误, 拼接为错误信息.
     * 返回 HTTP 400.
     * </p>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Object> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return Result.fail(ErrorCode.MISSING_FIELDS, message);
    }

    /**
     * 静态资源未找到处理.
     * <p>
     * 访问无映射的路径时返回 404 而非 500.
     * </p>
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Object> handleNoResourceFound(NoResourceFoundException e) {
        return Result.fail(ErrorCode.NOT_FOUND, "请求的资源不存在");
    }

    /**
     * 通用异常兜底处理.
     * <p>
     * 所有未明确处理的异常统一返回 HTTP 500.
     * 不向客户端暴露详细的异常堆栈.
     * </p>
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Object> handleGeneralException(Exception e) {
        log.error("服务器内部错误: ", e);
        return Result.fail(ErrorCode.SERVER_ERROR, "服务器内部错误");
    }
}
