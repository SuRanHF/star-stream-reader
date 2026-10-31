package com.huazhenghai.readergame.service.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.vo.RankingVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Best-effort Redis cache for public ELO leaderboard reads.
 * MySQL remains the source of truth; Redis errors never block the query.
 */
@Component
public class PublicRankingCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicRankingCache.class);
    private static final TypeReference<List<RankingVO>> RANKING_LIST = new TypeReference<>() { };
    private static final long WARNING_INTERVAL_NANOS = Duration.ofMinutes(1).toNanos();

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;
    private final Duration ttl;
    private final AtomicLong lastWarningNanos = new AtomicLong();

    public PublicRankingCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.cache.key-prefix:star-reader}") String keyPrefix,
            @Value("${app.cache.public-ranking.ttl:15s}") Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        String normalizedPrefix = keyPrefix == null ? "" : keyPrefix.trim();
        while (normalizedPrefix.endsWith(":")) {
            normalizedPrefix = normalizedPrefix.substring(0, normalizedPrefix.length() - 1);
        }
        this.keyPrefix = normalizedPrefix.isEmpty() ? "star-reader" : normalizedPrefix;
        this.ttl = ttl;
    }

    public List<RankingVO> getEloLeaderboard(int limit, Supplier<List<RankingVO>> mysqlLoader) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return mysqlLoader.get();
        }

        String key = keyPrefix + ":cache:v1:public-ranking:elo:" + limit;
        String cachedJson = null;
        try {
            cachedJson = redisTemplate.opsForValue().get(key);
        } catch (RuntimeException exception) {
            warnOncePerMinute("read", exception);
        }

        if (cachedJson != null) {
            try {
                List<RankingVO> cachedRows = objectMapper.readValue(cachedJson, RANKING_LIST);
                if (cachedRows != null) {
                    return cachedRows;
                }
            } catch (JsonProcessingException | RuntimeException exception) {
                warnOncePerMinute("deserialize", exception);
            }
        }

        List<RankingVO> rows = mysqlLoader.get();
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(rows), ttl);
        } catch (JsonProcessingException | RuntimeException exception) {
            warnOncePerMinute("write", exception);
        }
        return rows;
    }

    private void warnOncePerMinute(String operation, Exception exception) {
        long now = System.nanoTime();
        long previous = lastWarningNanos.get();
        if ((previous == 0 || now - previous >= WARNING_INTERVAL_NANOS)
                && lastWarningNanos.compareAndSet(previous, now)) {
            LOGGER.warn("Redis public-ranking cache {} failed; continuing with MySQL", operation, exception);
        }
    }
}
