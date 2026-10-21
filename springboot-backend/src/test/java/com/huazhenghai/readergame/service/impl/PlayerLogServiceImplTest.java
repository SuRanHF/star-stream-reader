package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.entity.BattleLog;
import com.huazhenghai.readergame.entity.PlayerLog;
import com.huazhenghai.readergame.mapper.BattleLogMapper;
import com.huazhenghai.readergame.mapper.PlayerLogMapper;
import com.huazhenghai.readergame.vo.LogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerLogServiceImplTest {

    @Mock
    private PlayerLogMapper playerLogMapper;

    @Mock
    private BattleLogMapper battleLogMapper;

    private PlayerLogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PlayerLogServiceImpl(playerLogMapper, battleLogMapper, new ObjectMapper());
    }

    @Test
    void writesStructuredMetadataAndReturnsItInChronologicalOrder() {
        when(playerLogMapper.insert(any(PlayerLog.class))).thenReturn(1);
        service.addLog(7L, "battle", "击败了变异站鼠", Map.of("kind", "combat_result", "totalRounds", 2));

        ArgumentCaptor<PlayerLog> inserted = ArgumentCaptor.forClass(PlayerLog.class);
        verify(playerLogMapper).insert(inserted.capture());
        PlayerLog stored = inserted.getValue();
        assertEquals(7L, stored.getPlayerId());
        assertEquals("battle", stored.getType());
        assertEquals("combat_result", readMetadata(stored.getMetadataJson()).get("kind"));

        PlayerLog newest = new PlayerLog();
        newest.setId(2L);
        newest.setMessage("新日志");
        newest.setType("battle");
        newest.setMetadataJson("{\"kind\":\"combat_result\",\"totalRounds\":2,"
                + "\"requestKey\":\"retry-1\",\"storyInstanceKey\":\"instance-1\","
                + "\"requestLocationKey\":\"station\","
                + "\"requestResponse\":{\"rewards\":{\"coins\":12}}}");
        PlayerLog older = new PlayerLog();
        older.setId(1L);
        older.setMessage("旧日志");
        older.setType("story");
        older.setMetadataJson("{malformed json");
        when(playerLogMapper.selectList(any())).thenReturn(List.of(newest, older));

        List<LogEntry> recent = service.getRecentLogs(7L, 50);

        assertEquals(List.of(1L, 2L), recent.stream().map(LogEntry::getId).toList());
        assertNull(recent.get(0).getMetadata());
        assertEquals("combat_result", recent.get(1).getMetadata().get("kind"));
        assertEquals(2, recent.get(1).getMetadata().get("totalRounds"));
        assertFalse(recent.get(1).getMetadata().containsKey("requestKey"));
        assertFalse(recent.get(1).getMetadata().containsKey("requestResponse"));
        assertFalse(recent.get(1).getMetadata().containsKey("storyInstanceKey"));
    }

    @Test
    void failedLogInsertIsReportedToTransactionalCaller() {
        when(playerLogMapper.insert(any(PlayerLog.class))).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> service.addLog(7L, "battle", "战斗结果", Map.of("kind", "combat_result")));
    }

    @Test
    void redactsPrivateStoryAndBattleReceiptFlagsEvenWhenTheyAreTheOnlyInternalFields() {
        PlayerLog story = new PlayerLog();
        story.setId(3L);
        story.setType("story");
        story.setMessage("你选择了继续前进");
        story.setMetadataJson("{\"kind\":\"story_choice\",\"choiceLabel\":\"继续前进\","
                + "\"storyInstanceKey\":\"private-instance\"}");
        PlayerLog battle = new PlayerLog();
        battle.setId(4L);
        battle.setType("battle");
        battle.setMessage("击败了变异站鼠");
        battle.setMetadataJson("{\"kind\":\"combat_result\",\"battleEncounterResolved\":true}");
        // Mapper query is newest-first; the service reverses results for chronological display.
        when(playerLogMapper.selectList(any())).thenReturn(List.of(battle, story));

        List<LogEntry> recent = service.getRecentLogs(7L, 50);

        assertEquals("继续前进", recent.get(0).getMetadata().get("choiceLabel"));
        assertFalse(recent.get(0).getMetadata().containsKey("storyInstanceKey"));
        assertFalse(recent.get(1).getMetadata().containsKey("battleEncounterResolved"));
        assertEquals("combat_result", recent.get(1).getMetadata().get("kind"));
    }

    @Test
    void recentLogsUseIdAsAStableTieBreakerForMatchingTimestamps() {
        when(playerLogMapper.selectList(any())).thenReturn(List.of());

        service.getRecentLogs(7L, 50);

        ArgumentCaptor<QueryWrapper<PlayerLog>> query = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(playerLogMapper).selectList(query.capture());
        String order = query.getValue().getSqlSegment().replaceAll("\\s+", "");
        assertTrue(order.contains("created_atDESC,idDESC"), order);
    }

    @Test
    void explorationReceiptLookupReturnsPrivateReplayMetadata() {
        PlayerLog stored = new PlayerLog();
        stored.setMetadataJson("{\"kind\":\"exploration_result\","
                + "\"requestKey\":\"retry-1\",\"requestLocationKey\":\"station\","
                + "\"requestResponse\":{\"result_type\":\"resource\"}}");
        when(playerLogMapper.selectList(any())).thenReturn(List.of(stored));

        Map<String, Object> receipt = service.findExplorationReceipt(7L, "retry-1");

        assertNotNull(receipt);
        assertEquals("station", receipt.get("requestLocationKey"));
        assertEquals("resource", ((Map<?, ?>) receipt.get("requestResponse")).get("result_type"));
        verify(playerLogMapper).selectList(any());
    }

    @Test
    void storyChoiceReceiptLookupReturnsThePrivateResponseForReplay() {
        PlayerLog stored = new PlayerLog();
        stored.setMetadataJson("{\"kind\":\"story_choice\",\"eventKey\":\"forked-road\","
                + "\"choiceIndex\":1,\"requestKey\":\"choice-retry-1\","
                + "\"storyInstanceKey\":\"story-instance-1\","
                + "\"requestResponse\":{\"choice_label\":\"继续前进\"}}");
        when(playerLogMapper.selectList(any())).thenReturn(List.of(stored));

        Map<String, Object> receipt = service.findStoryChoiceReceipt(7L, "choice-retry-1");

        assertNotNull(receipt);
        assertEquals("forked-road", receipt.get("eventKey"));
        assertEquals(1, receipt.get("choiceIndex"));
        assertEquals("story-instance-1", receipt.get("storyInstanceKey"));
        assertEquals("继续前进", ((Map<?, ?>) receipt.get("requestResponse")).get("choice_label"));
        verify(playerLogMapper).selectList(any());
    }

    @Test
    void pendingBattleReceiptsExcludeAcknowledgedAndAlreadySettledBattles() {
        PlayerLog pending = battleReceipt("pending-1", false);
        PlayerLog alreadySettled = battleReceipt("settled-1", false);
        PlayerLog acknowledged = battleReceipt("acknowledged-1", true);
        when(playerLogMapper.selectList(any())).thenReturn(List.of(pending, alreadySettled, acknowledged));
        lenient().when(battleLogMapper.selectByPlayerAndRequestKey(7L, "settled-1"))
                .thenReturn(new BattleLog());

        List<Map<String, Object>> result = service.findPendingBattleEncounters(7L);

        assertEquals(1, result.size());
        assertEquals("pending-1", result.get(0).get("request_key"));
        assertEquals("battle", result.get(0).get("result_type"));
    }

    @Test
    void pendingStoryChoiceBattleIsRestoredAndAcknowledgedByItsBattleRequestKey() {
        PlayerLog receipt = storyChoiceBattleReceipt("story-choice-1", "story-battle-1", false);
        when(playerLogMapper.selectList(any())).thenReturn(List.of(receipt));
        when(playerLogMapper.updateById(receipt)).thenReturn(1);

        List<Map<String, Object>> pending = service.findPendingBattleEncounters(7L);

        assertEquals(1, pending.size());
        assertEquals("story-battle-1", pending.get(0).get("request_key"));
        assertEquals("iron_king_trial", pending.get(0).get("monster_key"));

        service.acknowledgeBattleEncounter(7L, "story-battle-1");

        assertEquals(true, readMetadata(receipt.getMetadataJson()).get("battleEncounterResolved"));
        verify(playerLogMapper).updateById(receipt);
    }

    @Test
    void pendingBattleLookupContinuesPastFirstPageOfSettledReceipts() {
        List<PlayerLog> settledReceipts = IntStream.range(0, 100)
                .mapToObj(index -> battleReceipt("settled-" + index, false))
                .toList();
        PlayerLog pending = battleReceipt("pending-after-page", false);
        when(playerLogMapper.selectList(any())).thenReturn(settledReceipts, List.of(pending));
        when(battleLogMapper.selectByPlayerAndRequestKey(eq(7L), anyString())).thenAnswer(invocation -> {
            String requestKey = invocation.getArgument(1);
            return "pending-after-page".equals(requestKey) ? null : new BattleLog();
        });

        List<Map<String, Object>> result = service.findPendingBattleEncounters(7L);

        assertEquals(1, result.size());
        assertEquals("pending-after-page", result.get(0).get("request_key"));
        ArgumentCaptor<QueryWrapper<PlayerLog>> queries = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(playerLogMapper, times(2)).selectList(queries.capture());
        assertTrue(queries.getAllValues().get(1).getSqlSegment().contains("LIMIT 100 OFFSET 100"));
    }

    @Test
    void battleAcknowledgementUpdatesOnlyBattleExplorationReceipt() {
        PlayerLog receipt = battleReceipt("request-1", false);
        when(playerLogMapper.selectList(any())).thenReturn(List.of(receipt));
        when(playerLogMapper.updateById(receipt)).thenReturn(1);

        service.acknowledgeBattleEncounter(7L, "request-1");

        verify(playerLogMapper).updateById(receipt);
        assertEquals(true, readMetadata(receipt.getMetadataJson()).get("battleEncounterResolved"));
    }

    @Test
    void failedBattleAcknowledgementUpdateIsReported() {
        PlayerLog receipt = battleReceipt("request-1", false);
        when(playerLogMapper.selectList(any())).thenReturn(List.of(receipt));
        when(playerLogMapper.updateById(receipt)).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> service.acknowledgeBattleEncounter(7L, "request-1"));
    }

    private PlayerLog battleReceipt(String requestKey, boolean resolved) {
        PlayerLog receipt = new PlayerLog();
        receipt.setPlayerId(7L);
        receipt.setMetadataJson("{\"kind\":\"exploration_result\",\"requestKey\":\"" + requestKey
                + "\",\"battleEncounterResolved\":" + resolved
                + ",\"requestResponse\":{\"result_type\":\"battle\",\"monster_key\":\"station_rat\","
                + "\"result\":{\"event_key\":\"rat-event\",\"event_type\":\"battle\","
                + "\"name\":\"变异站鼠\",\"description\":\"\"}}}");
        return receipt;
    }

    private PlayerLog storyChoiceBattleReceipt(String choiceRequestKey, String battleRequestKey,
                                                boolean resolved) {
        PlayerLog receipt = new PlayerLog();
        receipt.setPlayerId(7L);
        receipt.setMetadataJson("{\"kind\":\"story_choice\",\"requestKey\":\"" + choiceRequestKey
                + "\",\"battleRequestKey\":\"" + battleRequestKey + "\",\"battleEncounterResolved\":"
                + resolved + ",\"requestResponse\":{\"pending_battle\":{\"result_type\":\"battle\","
                + "\"monster_key\":\"iron_king_trial\",\"result\":{\"event_key\":\"iron-story-choice-0\","
                + "\"event_type\":\"story_battle\",\"name\":\"铁王的钢铁化试炼\",\"description\":\"\"}}}}");
        return receipt;
    }

    private Map<String, Object> readMetadata(String json) {
        try {
            return new ObjectMapper().readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception exception) {
            throw new AssertionError("metadata should be valid JSON", exception);
        }
    }
}
