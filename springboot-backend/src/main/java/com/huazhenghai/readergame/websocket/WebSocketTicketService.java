package com.huazhenghai.readergame.websocket;

import com.huazhenghai.readergame.security.LoginUser;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Service
public class WebSocketTicketService {
    public static final int VALID_SECONDS = 30;
    private static final String KEY_PREFIX = "ws:ticket:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    public WebSocketTicketService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String issue(LoginUser user) {
        if (user == null || user.getUserId() == null || user.getUserId() <= 0) {
            throw new IllegalArgumentException("Authenticated user required");
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(KEY_PREFIX + ticket, user.getUserId().toString(), Duration.ofSeconds(VALID_SECONDS));
        return ticket;
    }

    public Long consume(String ticket) {
        if (ticket == null || !ticket.matches("[A-Za-z0-9_-]{43}")) return null;
        String value = redis.opsForValue().getAndDelete(KEY_PREFIX + ticket);
        if (value == null) return null;
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }
}
