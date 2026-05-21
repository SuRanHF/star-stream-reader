package com.huazhenghai.readergame.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类.
 * <p>
 * 负责 Token 的生成与校验.
 * JWT 负载包含 userId / username / role,
 * 过期时间由 {@code app.jwt.expire-hours} 控制 (默认 168 小时 = 7 天).
 * </p>
 */
@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long expireMs;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expire-hours}") long expireHours) {
        // 确保密钥长度 >= 256 bits (jjwt 0.12.x 要求)
        String keyStr = secret;
        if (keyStr.length() < 32) {
            keyStr = String.format("%-32s", keyStr).replace(' ', '0');
        }
        this.secretKey = Keys.hmacShaKeyFor(keyStr.getBytes(StandardCharsets.UTF_8));
        this.expireMs = expireHours * 3600_000L;
    }

    /**
     * 生成 JWT Token.
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param role     角色
     * @return JWT 字符串
     */
    public String generateToken(Long userId, String username, String role) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claim("userId", userId)
                .claim("username", username)
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireMs))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 校验并解析 Token.
     *
     * @param token JWT 字符串
     * @return 解析后的 Claims
     * @throws JwtException Token 无效或过期
     */
    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Claims 中提取 userId.
     */
    public Long getUserId(Claims claims) {
        return claims.get("userId", Long.class);
    }

    /**
     * 从 Claims 中提取 username.
     */
    public String getUsername(Claims claims) {
        return claims.get("username", String.class);
    }

    /**
     * 从 Claims 中提取 role.
     */
    public String getRole(Claims claims) {
        return claims.get("role", String.class);
    }
}
