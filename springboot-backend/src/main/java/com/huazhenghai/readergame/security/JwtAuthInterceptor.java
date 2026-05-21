package com.huazhenghai.readergame.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.Result;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * JWT 认证拦截器.
 * <p>
 * 在请求到达 Controller 之前校验 Authorization 头中的 Bearer Token,
 * 校验通过后将用户信息存入 {@link LoginUserContext}.
 * 同时支持 X-Admin-Key 头作为管理员备选认证通道.
 * </p>
 */
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Value("${admin.key:}")
    private String adminKey;

    public JwtAuthInterceptor(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // X-Admin-Key 备选认证通道 (不依赖 JWT)
        String xAdminKey = request.getHeader("X-Admin-Key");
        if (adminKey != null && adminKey.length() >= 8 && adminKey.equals(xAdminKey)) {
            LoginUser admin = new LoginUser(0L, "admin-key", "admin");
            LoginUserContext.set(admin);
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    ErrorCode.UNAUTHORIZED, "请先登录");
            return false;
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtUtil.validateToken(token);
            LoginUser user = new LoginUser(
                    jwtUtil.getUserId(claims),
                    jwtUtil.getUsername(claims),
                    jwtUtil.getRole(claims)
            );
            LoginUserContext.set(user);
            return true;
        } catch (JwtException e) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    ErrorCode.TOKEN_INVALID, "登录已过期，请重新登录");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler, Exception ex) {
        // 请求结束后清理 ThreadLocal, 防止内存泄漏
        LoginUserContext.clear();
    }

    /**
     * 向响应写入 JSON 格式的错误信息.
     */
    private void writeError(HttpServletResponse response, int status,
                            String code, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        Result<Object> result = Result.fail(code, message);
        String json = objectMapper.writeValueAsString(result);
        response.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
    }
}
