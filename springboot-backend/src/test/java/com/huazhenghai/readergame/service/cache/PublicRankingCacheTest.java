package com.huazhenghai.readergame.service.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.vo.RankingVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicRankingCacheTest {

    private static final String KEY = "star-reader:test:cache:v1:public-ranking:elo:50";
    private static final Duration TTL = Duration.ofSeconds(15);

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PublicRankingCache cache;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cache = new PublicRankingCache(redisTemplate, objectMapper, "star-reader:test:", TTL);
    }

    @Test
    void cacheMissLoadsFromMysqlAndStoresJsonWithTtl() throws Exception {
        RankingVO expected = row(12L, "First explorer", 1180);
        when(valueOperations.get(KEY)).thenReturn(null);

        List<RankingVO> result = cache.getEloLeaderboard(50, () -> List.of(expected));

        assertSame(expected, result.get(0));
        verify(valueOperations).set(eq(KEY), eq(objectMapper.writeValueAsString(List.of(expected))), eq(TTL));
    }

    @Test
    void cacheHitReturnsCachedRowsWithoutCallingMysql() throws Exception {
        RankingVO expected = row(18L, "Cached explorer", 1250);
        when(valueOperations.get(KEY)).thenReturn(objectMapper.writeValueAsString(List.of(expected)));
        AtomicBoolean mysqlCalled = new AtomicBoolean();

        List<RankingVO> result = cache.getEloLeaderboard(50, () -> {
            mysqlCalled.set(true);
            return List.of();
        });

        assertFalse(mysqlCalled.get());
        assertEquals(18L, result.get(0).getPlayerId());
        assertEquals("Cached explorer", result.get(0).getPlayerName());
        assertEquals(1250, result.get(0).getRating());
        verify(valueOperations, never()).set(anyString(), anyString(), eq(TTL));
    }

    @Test
    void redisReadAndWriteFailuresFallBackToMysql() {
        RankingVO expected = row(21L, "Database explorer", 1010);
        when(valueOperations.get(KEY)).thenThrow(new RedisConnectionFailureException("redis unavailable"));
        doThrow(new RedisConnectionFailureException("redis unavailable"))
                .when(valueOperations).set(eq(KEY), anyString(), eq(TTL));

        List<RankingVO> result = cache.getEloLeaderboard(50, () -> List.of(expected));

        assertSame(expected, result.get(0));
    }

    @Test
    void malformedCachePayloadFallsBackAndReplacesTheEntry() throws Exception {
        RankingVO expected = row(22L, "Recovered explorer", 1090);
        when(valueOperations.get(KEY)).thenReturn("not-json");

        List<RankingVO> result = cache.getEloLeaderboard(50, () -> List.of(expected));

        assertSame(expected, result.get(0));
        verify(valueOperations).set(eq(KEY), eq(objectMapper.writeValueAsString(List.of(expected))), eq(TTL));
    }

    private static RankingVO row(Long playerId, String playerName, int rating) {
        RankingVO row = new RankingVO();
        row.setPlayerId(playerId);
        row.setPlayerName(playerName);
        row.setRating(rating);
        row.setRanked(true);
        row.setRank(1);
        return row;
    }
}
