package com.huazhenghai.readergame.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一 API 响应体.
 * <p>
 * 所有 Controller 均通过此类包装返回数据,
 * 前端根据 success 字段判断请求是否成功.
 * </p>
 *
 * @param <T> 业务数据的具体类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    private boolean success;
    private T data;
    private ErrorInfo error;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public ErrorInfo getError() { return error; }
    public void setError(ErrorInfo error) { this.error = error; }

    // ==================== 工厂方法 ====================

    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.success = true;
        result.data = data;
        return result;
    }

    public static <T> Result<T> ok() {
        Result<T> result = new Result<>();
        result.success = true;
        return result;
    }

    public static <T> Result<T> fail(String code, String message) {
        Result<T> result = new Result<>();
        result.success = false;
        result.error = new ErrorInfo(code, message);
        return result;
    }

    // ==================== 内部类: ErrorInfo ====================

    public static class ErrorInfo {
        private String code;
        private String message;

        public ErrorInfo() {
        }

        public ErrorInfo(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
