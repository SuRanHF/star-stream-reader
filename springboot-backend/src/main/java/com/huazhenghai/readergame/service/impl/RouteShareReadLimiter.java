package com.huazhenghai.readergame.service.impl;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Per-remote-address fixed window, with a bounded process-local fallback during Redis outages. */
@Component
public class RouteShareReadLimiter {
    private static final int LIMIT = 30;
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(
            "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end; return n",
            Long.class);
    private final StringRedisTemplate redis;
    private final String prefix;
    private final Clock clock;
    private final Map<String, Window> local = new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Window> eldest) {
            return size() > 10_000;
        }
    };

    @Autowired
    public RouteShareReadLimiter(StringRedisTemplate redis,
            @Value("${app.cache.key-prefix:star-reader}") String prefix) {
        this(redis, prefix, Clock.systemUTC());
    }

    RouteShareReadLimiter(StringRedisTemplate redis, String prefix, Clock clock) {
        this.redis = redis;
        this.prefix = prefix;
        this.clock = clock;
    }

    public void check(String remoteAddress) {
        String address = remoteAddress == null ? "unknown" : remoteAddress;
        String key = prefix + ":rate:route-share:" + addressHash(address);
        Long count;
        try { count = redis.execute(SCRIPT, List.of(key)); }
        catch (Exception ignored) { count = null; }
        if (count == null) count = localCount(key);
        if (count > LIMIT) throw new BusinessException(ErrorCode.RATE_LIMITED, "请求过于频繁，请稍后再试");
    }

    private synchronized long localCount(String key) {
        long bucket = clock.instant().getEpochSecond() / 60;
        Window previous = local.get(key);
        int count = previous != null && previous.bucket() == bucket ? previous.count() + 1 : 1;
        local.put(key, new Window(bucket, count));
        return count;
    }

    private String addressHash(String address) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(address.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("Cannot hash remote address", e); }
    }

    private record Window(long bucket, int count) { }
}
