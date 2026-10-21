package com.huazhenghai.readergame.service.impl;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.BroadcastService;
import com.huazhenghai.readergame.service.EndingService;
import com.huazhenghai.readergame.service.ExploreService;
import com.huazhenghai.readergame.service.FactionService;
import com.huazhenghai.readergame.service.PlayerEffectService;
import com.huazhenghai.readergame.service.QuestService;
import com.huazhenghai.readergame.service.RecoveryService;
import com.huazhenghai.readergame.service.TitleService;
import com.huazhenghai.readergame.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:explore_recovery_rollback;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.ai-director.llm-api-key=",
        "app.scheduler.enabled=false"
}, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Sql("/db/combat-persistence-test-schema.sql")
class ExploreRecoveryRollbackIntegrationTest {
    private static final long PLAYER_ID = 85001L;
    private static final long USER_ID = 95001L;
    private static final String REQUEST_KEY = "h2-real-recovery-shortfall";
    private static final String LOCATION_KEY = "h2_recovery_zone";

    @Autowired private ExploreService exploreService;
    @Autowired private EndingService endingService;
    @Autowired private RecoveryService recoveryService;
    @Autowired private PlayerMapper playerMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    @MockitoBean private TitleService titleService;
    @MockitoBean private BroadcastService broadcastService;
    @MockitoBean private FactionService factionService;
    @MockitoBean private QuestService questService;
    @MockitoBean private PlayerEffectService playerEffectService;

    private String originalStatsJson;

    @BeforeEach
    void seedOnlyTheDisposableH2PlayerAndEncounter() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:explore_recovery_rollback"));
        }
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS endings ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY, ending_key VARCHAR(100) NOT NULL UNIQUE, "
                + "name VARCHAR(100) NOT NULL, description CLOB, priority INT NOT NULL, "
                + "conditions_json CLOB, is_hidden INT NOT NULL DEFAULT 0)");
        jdbcTemplate.update("DELETE FROM endings");
        jdbcTemplate.update("DELETE FROM player_resource_transactions");
        jdbcTemplate.update("DELETE FROM player_action_receipts");
        jdbcTemplate.update("DELETE FROM player_logs");
        jdbcTemplate.update("DELETE FROM exploration_events");
        jdbcTemplate.update("DELETE FROM locations");
        jdbcTemplate.update("DELETE FROM player_skills");
        jdbcTemplate.update("DELETE FROM skills");
        jdbcTemplate.update("DELETE FROM player_titles");
        jdbcTemplate.update("DELETE FROM players");
        jdbcTemplate.update("DELETE FROM users");

        jdbcTemplate.update("INSERT INTO users (id, username, email, password_hash, role, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                USER_ID, "h2-explore-recovery", "h2-explore-recovery@example.invalid",
                "test-only", "player", "normal");

        long recoveryCursor = System.currentTimeMillis() - 1_800_000L;
        originalStatsJson = "{\"hp\":100,\"maxHp\":100,\"stamina\":0,\"maxStamina\":50,"
                + "\"exp\":0,\"level\":1,\"freePoints\":0,\"avatarRank\":\"F\","
                + "\"isResting\":false,\"lastRecoveryAt\":" + recoveryCursor + "}";
        jdbcTemplate.update("INSERT INTO players (id, user_id, player_name, current_location, coins, "
                        + "story_fragments, stats_json, stage_progress_json, story_flags_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                PLAYER_ID, USER_ID, "真实恢复回滚验证", LOCATION_KEY, 0, 0,
                originalStatsJson, "{}", "{}");
        jdbcTemplate.update("INSERT INTO locations (location_key, name, unlock_conditions_json, "
                        + "event_rates_json, min_level, recommended_rank, is_default) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                LOCATION_KEY, "恢复回滚测试区", "{}", "{}", 1, "F", 0);
        jdbcTemplate.update("INSERT INTO exploration_events (event_key, event_type, location_key, name, "
                        + "description, weight, stamina_cost, repeatable, required_conditions_json, "
                        + "rewards_json, progress_effects_json, choices_json, log_template, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "h2_recovery_cost_five", "resource", LOCATION_KEY, "体力消耗验证", "仅供隔离 H2 测试。",
                1, 5, 1, "{}", "{}", "{}", "[]", "完成了体力消耗验证。", 1);

        when(titleService.calculateTitleEffects(PLAYER_ID)).thenReturn(Map.of());
        when(titleService.evaluateAndUnlockTitles(PLAYER_ID, null)).thenReturn(List.of());
    }

    @Test
    void realRecoveryWritesAreRolledBackWhenExploreCannotPayTheDrawnCost() throws Exception {
        assertTrue(AopUtils.isAopProxy(exploreService), "探索服务必须经事务代理调用");
        assertTrue(AopUtils.isAopProxy(recoveryService), "真实恢复服务必须经事务代理调用");

        assertThrows(BusinessException.class, () -> exploreService.startExplore(
                PLAYER_ID, LOCATION_KEY, REQUEST_KEY, USER_ID));

        Player afterFailure = playerMapper.selectById(PLAYER_ID);
        assertEquals(originalStatsJson, afterFailure.getStatsJson(),
                "体力不足应回滚真实恢复服务写入的经验、体力和恢复游标");
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ?",
                Integer.class, PLAYER_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_logs WHERE player_id = ?", Integer.class, PLAYER_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_action_receipts WHERE player_id = ?",
                Integer.class, PLAYER_ID));

        long refreshedCursor = System.currentTimeMillis();
        String payableStats = "{\"hp\":100,\"maxHp\":100,\"stamina\":50,\"maxStamina\":50,"
                + "\"exp\":0,\"level\":1,\"freePoints\":0,\"avatarRank\":\"F\","
                + "\"isResting\":false,\"lastRecoveryAt\":" + refreshedCursor + "}";
        jdbcTemplate.update("UPDATE players SET stats_json = ? WHERE id = ?", payableStats, PLAYER_ID);

        Map<String, Object> retry = exploreService.startExplore(
                PLAYER_ID, LOCATION_KEY, REQUEST_KEY, USER_ID);

        assertEquals(false, retry.get("request_replayed"),
                "失败请求不能留下回执阻止同请求键重新执行");
        assertEquals("h2_recovery_cost_five", ((Map<?, ?>) retry.get("result")).get("event_key"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_logs WHERE player_id = ?", Integer.class, PLAYER_ID));
    }

    @Test
    void finalStoryChoicesPersistAndResolveIndependentlyForTwoPlayers() throws Exception {
        long secondPlayerId = PLAYER_ID + 1;
        long secondUserId = USER_ID + 1;
        String storyStatsJson = "{\"hp\":100,\"maxHp\":100,\"stamina\":50,\"maxStamina\":50,"
                + "\"exp\":0,\"level\":1,\"freePoints\":0,\"avatarRank\":\"F\","
                + "\"isResting\":false,\"lastRecoveryAt\":" + System.currentTimeMillis() + "}";
        jdbcTemplate.update("INSERT INTO users (id, username, email, password_hash, role, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                secondUserId, "h2-ending-player", "h2-ending-player@example.invalid",
                "test-only", "player", "normal");
        jdbcTemplate.update("INSERT INTO players (id, user_id, player_name, current_location, coins, "
                        + "story_fragments, stats_json, stage_progress_json, story_flags_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                secondPlayerId, secondUserId, "第二条隔离终局时间线", LOCATION_KEY, 0, 0,
                storyStatsJson,
                "{\"storyEventsTriggered\":[\"demon_gate_first_step_story\"]}", "{}");
        jdbcTemplate.update("UPDATE players SET stats_json = ? WHERE id = ?", storyStatsJson, PLAYER_ID);
        jdbcTemplate.update("UPDATE players SET story_flags_json = ? WHERE id = ?",
                "{\"oldest_dream_location\":true}", PLAYER_ID);
        jdbcTemplate.update("UPDATE players SET story_flags_json = ? WHERE id = ?",
                "{\"oldest_dream_last_page\":true}", secondPlayerId);

        insertFinaleEvent("ending_old_dream_promise", "履行终局约定",
                "{\"required_events\":[\"demon_gate_first_step_story\"]}",
                "[{\"label\":\"履行约定\",\"consequence_text\":\"旧梦结束。\","
                        + "\"unlock_events\":[\"ending_dream_continuation_choice\"],"
                        + "\"set_story_flags\":{\"ending_old_dream_ended\":true}}]");
        insertFinaleEvent("ending_dream_continuation_choice", "是否延续梦境",
                "{\"required_events\":[\"ending_old_dream_promise\"],"
                        + "\"choice_unlockers\":[{\"event_key\":\"ending_old_dream_promise\","
                        + "\"choice_indices\":[0]}],\"personal_contexts\":["
                        + "{\"story_flag\":\"oldest_dream_location\",\"text\":\"病房与少年\"},"
                        + "{\"story_flag\":\"oldest_dream_last_page\",\"text\":\"尚未写完的最后一页\"}]}",
                "[{\"label\":\"接续梦境\",\"consequence_text\":\"你成为新的最古老之梦。\","
                        + "\"set_story_flags\":{\"ending_dream_continued\":true,"
                        + "\"ending_new_oldest_dream\":true}},"
                        + "{\"label\":\"不再接续\",\"consequence_text\":\"世界时间停止。\","
                        + "\"set_story_flags\":{\"ending_world_frozen\":true}}]");
        jdbcTemplate.update("INSERT INTO endings (ending_key, name, description, priority, conditions_json, is_hidden) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "end_continue_dream", "接续之梦", "终局正史改编", 5,
                "{\"flags\":{\"ending_new_oldest_dream\":true}}", 0);
        jdbcTemplate.update("INSERT INTO endings (ending_key, name, description, priority, conditions_json, is_hidden) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "end_frozen_world", "停滞的世界线", "终局假设分支", 4,
                "{\"flags\":{\"ending_world_frozen\":true}}", 0);

        jdbcTemplate.update("UPDATE locations SET event_rates_json = ? WHERE location_key = ?",
                "{\"story\":100}", LOCATION_KEY);
        jdbcTemplate.update("UPDATE players SET stage_progress_json = ? WHERE id = ?",
                "{\"storyEventsTriggered\":[\"demon_gate_first_step_story\"]}", PLAYER_ID);
        Map<String, Object> firstPromiseDraw = drawExpectedStory(
                PLAYER_ID, USER_ID, "ending_old_dream_promise", "finale-draw-promise-player-one");
        Map<String, Object> secondPromiseDraw = drawExpectedStory(
                secondPlayerId, secondUserId, "ending_old_dream_promise", "finale-draw-promise-player-two");
        Map<String, Object> prematureDraw = exploreService.startExplore(
                PLAYER_ID, LOCATION_KEY, "finale-premature-choice-player-one", USER_ID);
        assertFalse("ending_dream_continuation_choice".equals(
                        ((Map<?, ?>) prematureDraw.get("result")).get("event_key")),
                "仅触发旧梦剧情、尚未作出履约选择时，不得越级抽到接续梦境");
        Map<String, Object> firstPromise = exploreService.makeChoice(
                PLAYER_ID, "ending_old_dream_promise", 0, "finale-promise-player-one",
                (String) firstPromiseDraw.get("story_instance_key"), USER_ID);
        Map<String, Object> secondPromise = exploreService.makeChoice(
                secondPlayerId, "ending_old_dream_promise", 0, "finale-promise-player-two",
                (String) secondPromiseDraw.get("story_instance_key"), secondUserId);
        assertEquals(false, firstPromise.get("request_replayed"));
        assertEquals(false, secondPromise.get("request_replayed"));
        assertEquals(List.of("ending_dream_continuation_choice"), firstPromise.get("unlock_events"));

        Map<String, Object> firstContinuationDraw = drawExpectedStory(
                PLAYER_ID, USER_ID, "ending_dream_continuation_choice", "finale-draw-choice-player-one");
        Map<String, Object> secondContinuationDraw = drawExpectedStory(
                secondPlayerId, secondUserId, "ending_dream_continuation_choice", "finale-draw-choice-player-two");
        assertEquals(List.of("病房与少年"), personalContexts((Map<?, ?>) firstContinuationDraw.get("result")));
        assertEquals(List.of("尚未写完的最后一页"), personalContexts((Map<?, ?>) secondContinuationDraw.get("result")));
        var firstPendingContinuation = exploreService.getPendingStories(PLAYER_ID, USER_ID);
        var secondPendingContinuation = exploreService.getPendingStories(secondPlayerId, secondUserId);
        assertEquals(List.of("病房与少年"), personalContexts(
                (Map<?, ?>) firstPendingContinuation.get(0).get("result")));
        assertEquals(List.of("尚未写完的最后一页"), personalContexts(
                (Map<?, ?>) secondPendingContinuation.get(0).get("result")));
        Map<String, Object> continuation = exploreService.makeChoice(
                PLAYER_ID, "ending_dream_continuation_choice", 0, "finale-choice-player-one",
                (String) firstContinuationDraw.get("story_instance_key"), USER_ID);
        Map<String, Object> refusal = exploreService.makeChoice(
                secondPlayerId, "ending_dream_continuation_choice", 1, "finale-choice-player-two",
                (String) secondContinuationDraw.get("story_instance_key"), secondUserId);

        assertEquals(List.of("end_continue_dream"), endingKeys(PLAYER_ID));
        assertEquals(List.of("end_frozen_world"), endingKeys(secondPlayerId));
        Map<String, Object> firstPlayerFlags = storyFlags(PLAYER_ID);
        Map<String, Object> secondPlayerFlags = storyFlags(secondPlayerId);
        assertEquals(true, firstPlayerFlags.get("ending_old_dream_ended"));
        assertEquals(true, firstPlayerFlags.get("ending_dream_continued"));
        assertEquals(true, firstPlayerFlags.get("ending_new_oldest_dream"));
        assertFalse(firstPlayerFlags.containsKey("ending_world_frozen"));
        assertEquals(true, secondPlayerFlags.get("ending_old_dream_ended"));
        assertEquals(true, secondPlayerFlags.get("ending_world_frozen"));
        assertFalse(secondPlayerFlags.containsKey("ending_new_oldest_dream"));
        assertEquals(Map.of("ending_dream_continued", true, "ending_new_oldest_dream", true),
                continuation.get("story_flags_updated"));
        assertEquals(Map.of("ending_world_frozen", true), refusal.get("story_flags_updated"));

        Map<String, Object> replay = exploreService.makeChoice(
                PLAYER_ID, "ending_dream_continuation_choice", 0, "finale-choice-player-one",
                (String) firstContinuationDraw.get("story_instance_key"), USER_ID);
        assertEquals(true, replay.get("request_replayed"));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_story_logs WHERE player_id = ?", Integer.class, PLAYER_ID));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_story_logs WHERE player_id = ?", Integer.class, secondPlayerId));
    }

    private Map<String, Object> drawExpectedStory(long playerId, long userId,
                                                  String expectedEventKey, String requestKey) {
        Map<String, Object> draw = exploreService.startExplore(playerId, LOCATION_KEY, requestKey, userId);
        assertEquals("story", draw.get("result_type"));
        Map<?, ?> event = (Map<?, ?>) draw.get("result");
        assertEquals(expectedEventKey, event.get("event_key"));
        assertEquals(false, draw.get("request_replayed"));
        assertTrue(draw.get("story_instance_key") instanceof String);
        return draw;
    }

    private List<String> personalContexts(Map<?, ?> eventResult) {
        Object contexts = eventResult.get("personal_contexts");
        if (!(contexts instanceof List<?> values)) return List.of();
        return values.stream().map(String::valueOf).toList();
    }

    private void insertFinaleEvent(String eventKey, String name,
                                   String requiredConditionsJson, String choicesJson) {
        jdbcTemplate.update("INSERT INTO exploration_events (event_key, event_type, stage_key, location_key, "
                        + "name, description, weight, stamina_cost, repeatable, required_conditions_json, "
                        + "rewards_json, progress_effects_json, choices_json, log_template, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                eventKey, "story", "vol12_finale", LOCATION_KEY, name, "隔离 H2 终局测试。",
                100, 0, 0, requiredConditionsJson, "{}", "{}", choicesJson, "终局测试日志。", 1);
    }

    private List<String> endingKeys(long playerId) {
        return endingService.checkEndings(playerId).stream()
                .map(ending -> (String) ending.get("endingKey"))
                .toList();
    }

    private Map<String, Object> storyFlags(long playerId) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                playerMapper.selectById(playerId).getStoryFlagsJson(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    }
}
