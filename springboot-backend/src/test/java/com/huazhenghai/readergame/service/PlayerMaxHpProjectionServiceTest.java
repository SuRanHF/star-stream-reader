package com.huazhenghai.readergame.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.entity.AvatarRankConfig;
import com.huazhenghai.readergame.mapper.AvatarRankConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerMaxHpProjectionServiceTest {

    @Mock private AvatarRankConfigMapper rankConfigMapper;

    private PlayerMaxHpProjectionService projectionService;

    @BeforeEach
    void setUp() {
        projectionService = new PlayerMaxHpProjectionService(rankConfigMapper, new ObjectMapper());
    }

    @Test
    void legacyRankRewardAndPrestigeBonusSurviveRecoveryMigration() {
        AvatarRankConfig eRank = rank("E", 2, "{\"stats\":{\"maxHp\":20}}");
        when(rankConfigMapper.selectList(any())).thenReturn(List.of(eRank));
        Map<String, Object> stats = new LinkedHashMap<>(Map.of(
                "avatarRank", "E",
                "prestigeLevel", 2,
                "constellation", "master_of_steel",
                "maxHp", 120));

        assertEquals(150, projectionService.normalizeForPersistence(stats));
        assertEquals(20, stats.get("maxHpConstellationBonus"));

        stats.put("constellation", "morning_star");
        assertEquals(135, projectionService.normalizeForPersistence(stats));
        assertEquals(5, stats.get("maxHpConstellationBonus"));
        assertEquals(135, projectionService.projectForRead(stats));
    }

    @Test
    void playerThatAlreadyHasProjectionMarkerDoesNotQueryOrStackConstellationBonus() {
        Map<String, Object> stats = new LinkedHashMap<>(Map.of(
                "maxHp", 140,
                "maxHpConstellationBonus", 20,
                "constellation", "morning_star"));

        assertEquals(125, projectionService.normalizeForPersistence(stats));
        assertEquals(125, projectionService.normalizeForPersistence(stats));
    }

    @Test
    void rejectsUnrepresentableProjectedMaximumHp() {
        Map<String, Object> stats = new LinkedHashMap<>(Map.of(
                "maxHp", Integer.MAX_VALUE,
                "maxHpConstellationBonus", 0,
                "constellation", "master_of_steel"));

        assertThrows(RuntimeException.class, () -> projectionService.projectForRead(stats));
    }

    private AvatarRankConfig rank(String key, int order, String rewards) {
        AvatarRankConfig rank = new AvatarRankConfig();
        rank.setRankKey(key);
        rank.setOrderNum(order);
        rank.setRewardsJson(rewards);
        return rank;
    }
}
