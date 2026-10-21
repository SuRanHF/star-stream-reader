package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.entity.BroadcastEvent;
import com.huazhenghai.readergame.entity.WorldBoss;
import com.huazhenghai.readergame.entity.WorldState;
import com.huazhenghai.readergame.entity.WorldlineChangeLog;
import com.huazhenghai.readergame.mapper.BroadcastEventMapper;
import com.huazhenghai.readergame.mapper.WorldStateMapper;
import com.huazhenghai.readergame.mapper.WorldlineChangeLogMapper;
import com.huazhenghai.readergame.mapper.WorldBossMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorldlineServiceImplTest {

    @Mock private WorldStateMapper worldStateMapper;
    @Mock private WorldlineChangeLogMapper changeLogMapper;
    @Mock private BroadcastEventMapper broadcastEventMapper;
    @Mock private WorldBossMapper worldBossMapper;

    private WorldlineServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WorldlineServiceImpl(
                worldStateMapper, changeLogMapper, broadcastEventMapper, worldBossMapper, new ObjectMapper());

        lenient().when(worldStateMapper.selectByStateKeyForUpdate(anyString())).thenAnswer(invocation -> {
            String stateKey = invocation.getArgument(0);
            WorldState state = new WorldState();
            state.setStateKey(stateKey);
            switch (stateKey) {
                case "worldline_shift" -> {
                    state.setName("世界线偏移");
                    state.setValueJson("{\"totalShift\":10}");
                }
                case "starstream_attention" -> {
                    state.setName("星流关注");
                    state.setValueJson("{\"attention\":20}");
                }
                case "scenario_pressure" -> {
                    state.setName("剧本压力");
                    state.setValueJson("{\"pressure\":40}");
                }
                default -> throw new IllegalArgumentException("Unexpected world state: " + stateKey);
            }
            return state;
        });
    }

    @Test
    void broadcastEffectsLockEachSharedStateBeforeUpdatingAndRecordTheAppliedValues() {
        when(worldStateMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        when(changeLogMapper.insert(any(WorldlineChangeLog.class))).thenReturn(1);
        service.applyBroadcastWorldlineEffects("public-event-1",
                "{\"worldline_shift\":2,\"starstream_attention\":3,\"scenario_pressure\":-5}");

        InOrder order = inOrder(worldStateMapper, changeLogMapper);
        order.verify(worldStateMapper).selectByStateKeyForUpdate("worldline_shift");
        order.verify(worldStateMapper).update(isNull(), any(UpdateWrapper.class));
        order.verify(changeLogMapper).insert(any(WorldlineChangeLog.class));
        order.verify(worldStateMapper).selectByStateKeyForUpdate("starstream_attention");
        order.verify(worldStateMapper).update(isNull(), any(UpdateWrapper.class));
        order.verify(changeLogMapper).insert(any(WorldlineChangeLog.class));
        order.verify(worldStateMapper).selectByStateKeyForUpdate("scenario_pressure");
        order.verify(worldStateMapper).update(isNull(), any(UpdateWrapper.class));
        order.verify(changeLogMapper).insert(any(WorldlineChangeLog.class));

        ArgumentCaptor<WorldlineChangeLog> logs = ArgumentCaptor.forClass(WorldlineChangeLog.class);
        verify(changeLogMapper, times(3)).insert(logs.capture());
        List<WorldlineChangeLog> entries = logs.getAllValues();
        assertEquals("worldline_shift", entries.get(0).getStateKey());
        assertEquals(10.0, entries.get(0).getBeforeValue());
        assertEquals(12.0, entries.get(0).getAfterValue());
        assertEquals("starstream_attention", entries.get(1).getStateKey());
        assertEquals(20.0, entries.get(1).getBeforeValue());
        assertEquals(23.0, entries.get(1).getAfterValue());
        assertEquals("scenario_pressure", entries.get(2).getStateKey());
        assertEquals(40.0, entries.get(2).getBeforeValue());
        assertEquals(35.0, entries.get(2).getAfterValue());
        assertEquals("public-event-1", entries.get(0).getCause());
    }

    @Test
    void scheduledDecayLocksSharedRowsBeforeReadingAndWritingThem() {
        when(worldStateMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        assertEquals(3, service.applyDecay(0.01));

        InOrder order = inOrder(worldStateMapper);
        order.verify(worldStateMapper).selectByStateKeyForUpdate("worldline_shift");
        order.verify(worldStateMapper).update(isNull(), any(UpdateWrapper.class));
        order.verify(worldStateMapper).selectByStateKeyForUpdate("starstream_attention");
        order.verify(worldStateMapper).update(isNull(), any(UpdateWrapper.class));
        order.verify(worldStateMapper).selectByStateKeyForUpdate("scenario_pressure");
        order.verify(worldStateMapper).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void historyIncludesReadableBroadcastAndWorldBossCausesWithoutDroppingOriginalKeys() {
        when(changeLogMapper.selectList(any())).thenReturn(List.of(
                historyLog("daily_story_broadcast"),
                historyLog("world_boss_WB-204"),
                historyLog("world_boss_expired_WB-205")));

        BroadcastEvent event = new BroadcastEvent();
        event.setEventKey("daily_story_broadcast");
        event.setTitle("故事共鸣");
        when(broadcastEventMapper.selectList(any())).thenReturn(List.of(event));

        WorldBoss defeatedBoss = new WorldBoss();
        defeatedBoss.setBossNo("WB-204");
        defeatedBoss.setName("沉眠的龙");
        WorldBoss expiredBoss = new WorldBoss();
        expiredBoss.setBossNo("WB-205");
        expiredBoss.setName("破碎的巨像");
        when(worldBossMapper.selectList(any())).thenReturn(List.of(defeatedBoss, expiredBoss));

        List<Map<String, Object>> history = service.getWorldlineHistory();

        assertEquals("全服活动「故事共鸣」达成", history.get(0).get("causeDisplay"));
        assertEquals("世界首领「沉眠的龙」被击破", history.get(1).get("causeDisplay"));
        assertEquals("世界首领「破碎的巨像」未能及时击破", history.get(2).get("causeDisplay"));
        assertEquals("world_boss_WB-204", history.get(1).get("cause"));
    }

    @Test
    void failedSharedStateUpdateIsReportedInsteadOfSilentlyIgnored() {
        when(worldStateMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.addWorldlineShift(2, "test-event"));

        verify(changeLogMapper, never()).insert(any(WorldlineChangeLog.class));
    }

    @Test
    void failedWorldlineHistoryInsertIsReportedForTheCallerToRollBack() {
        when(worldStateMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        when(changeLogMapper.insert(any(WorldlineChangeLog.class))).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.addWorldlineShift(2, "test-event"));

        verify(worldStateMapper).update(isNull(), any(UpdateWrapper.class));
        verify(changeLogMapper).insert(any(WorldlineChangeLog.class));
    }

    @Test
    void missingRequiredWorldStateIsReportedInsteadOfTreatedAsSuccessful() {
        when(worldStateMapper.selectByStateKeyForUpdate("worldline_shift")).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> service.addWorldlineShift(2, "test-event"));
    }

    @Test
    void scheduledDecayRejectsInvalidRatesAndSurfacesWriteFailures() {
        assertThrows(IllegalArgumentException.class, () -> service.applyDecay(Double.NaN));
        when(worldStateMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.applyDecay(0.01));
    }

    private WorldlineChangeLog historyLog(String cause) {
        WorldlineChangeLog log = new WorldlineChangeLog();
        log.setStateKey("worldline_shift");
        log.setStateName("世界线偏移");
        log.setValueKey("totalShift");
        log.setChangeAmount(1.0);
        log.setBeforeValue(0.0);
        log.setAfterValue(1.0);
        log.setCause(cause);
        return log;
    }
}
