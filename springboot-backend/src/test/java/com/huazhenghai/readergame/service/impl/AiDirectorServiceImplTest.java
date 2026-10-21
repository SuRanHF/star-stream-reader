package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.entity.PlayerLog;
import com.huazhenghai.readergame.mapper.PkRecordMapper;
import com.huazhenghai.readergame.mapper.PlayerLogMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.WorldBossMapper;
import com.huazhenghai.readergame.service.OnlinePlayerService;
import com.huazhenghai.readergame.service.WorldlineService;
import com.huazhenghai.readergame.vo.OnlineSummaryVO;
import com.huazhenghai.readergame.vo.WorldlineSummaryVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiDirectorServiceImplTest {

    @Mock private WorldlineService worldlineService;
    @Mock private OnlinePlayerService onlinePlayerService;
    @Mock private PlayerMapper playerMapper;
    @Mock private WorldBossMapper worldBossMapper;
    @Mock private PkRecordMapper pkRecordMapper;
    @Mock private PlayerLogMapper playerLogMapper;

    private AiDirectorServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiDirectorServiceImpl(worldlineService, onlinePlayerService, playerMapper,
                worldBossMapper, pkRecordMapper, playerLogMapper, new ObjectMapper());
    }

    @Test
    void fallbackUsesActualAverageLevelAndPublicBossKillCount() {
        stubSnapshot(List.of("{\"level\":6}", "{\"level\":8}"), 4, 2, 0, 0, List.of());

        Map<String, Object> draft = service.generateBroadcastDraft();

        assertEquals("world_boss", draft.get("eventType"));
        assertEquals("Boss击杀数仅 0，适合发布讨伐任务", draft.get("reason"));
        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = (Map<String, Object>) draft.get("worldStateSnapshot");
        assertEquals(4L, snapshot.get("totalPlayers"));
        assertEquals(7, snapshot.get("averageLevel"));
        assertEquals(0L, snapshot.get("totalBossKills"));
    }

    @Test
    void fallbackUsesRecentPvpRecordsInsteadOfAConstantZero() {
        stubSnapshot(List.of("{\"level\":8}", "{\"level\":9}"), 5, 1, 8, 5, List.of());

        Map<String, Object> draft = service.generateBroadcastDraft();

        assertEquals("pk_tournament", draft.get("eventType"));
        assertEquals("最近24小时PK活跃（5次），适合举办PK锦标赛", draft.get("reason"));
    }

    @Test
    void personalStoryChoicesDoNotCountAsPublicExplorationActivity() {
        List<PlayerLog> logs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            logs.add(log("exploration", "{\"kind\":\"exploration_result\",\"requestLocationKey\":\"station\"}"));
        }
        for (int i = 0; i < 15; i++) {
            logs.add(log("story", "{\"kind\":\"story_choice\",\"requestLocationKey\":\"private-route-" + i + "\"}"));
        }
        stubSnapshot(List.of("{\"level\":6}", "{\"level\":7}"), 2, 1, 5, 0, logs);

        Map<String, Object> draft = service.generateBroadcastDraft();

        assertEquals("opportunity_rain", draft.get("eventType"));
    }

    @Test
    void failedAggregateDoesNotInventPopulationAndGenerateAnEventFromFallbackConstants() {
        when(playerMapper.selectCount(isNull())).thenThrow(new IllegalStateException("test-only aggregate failure"));

        assertNull(service.generateBroadcastDraft());
    }

    private void stubSnapshot(List<String> stats, long totalPlayers, int onlinePlayers,
                              long bossKills, long pvpCount, List<PlayerLog> logs) {
        WorldlineSummaryVO worldline = new WorldlineSummaryVO();
        worldline.setWorldlineShift(0);
        OnlineSummaryVO onlineSummary = new OnlineSummaryVO();
        onlineSummary.setOnlineCount(onlinePlayers);
        when(worldlineService.getWorldlineSummary()).thenReturn(worldline);
        when(onlinePlayerService.getOnlineSummary()).thenReturn(onlineSummary);
        when(playerMapper.selectCount(isNull())).thenReturn(totalPlayers);
        when(playerMapper.selectStatsJsonForWorldAggregation()).thenReturn(stats);
        when(worldBossMapper.selectCount(any(Wrapper.class))).thenReturn(bossKills);
        when(pkRecordMapper.selectCount(any(Wrapper.class))).thenReturn(pvpCount);
        when(playerLogMapper.selectList(any(Wrapper.class))).thenReturn(logs);
    }

    private PlayerLog log(String type, String metadataJson) {
        PlayerLog log = new PlayerLog();
        log.setType(type);
        log.setMetadataJson(metadataJson);
        return log;
    }
}
