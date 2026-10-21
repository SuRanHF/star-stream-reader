package com.huazhenghai.readergame.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.huazhenghai.readergame.entity.Monster;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.BroadcastService;
import com.huazhenghai.readergame.service.MonsterService;
import com.huazhenghai.readergame.service.FactionService;
import com.huazhenghai.readergame.service.InventoryService;
import com.huazhenghai.readergame.service.PlayerEffectService;
import com.huazhenghai.readergame.service.PkService;
import com.huazhenghai.readergame.service.QuestService;
import com.huazhenghai.readergame.service.RecoveryService;
import com.huazhenghai.readergame.service.TitleService;
import com.huazhenghai.readergame.service.TradeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Verifies persisted exploration-to-quest-to-growth rewards in a disposable H2 database.
 * This test never connects to a local or deployed player database.
 */
@SpringBootTest(
        properties = {
                "spring.profiles.active=test",
                "spring.datasource.url=jdbc:h2:mem:quest_gameplay_persistence;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "app.ai-director.llm-api-key=",
                "app.scheduler.enabled=false"
        },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@Sql("/db/combat-persistence-test-schema.sql")
class QuestGameplayPersistenceIntegrationTest {

    private static final String QUEST_KEY = "h2_daily_first_exploration";
    private static final String TEST_PASSWORD = "isolated-quest-test-only";

    @Autowired private PlayerMapper playerMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DataSource dataSource;
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private QuestService questService;
    @Autowired private PkService pkService;
    @Autowired private TradeService tradeService;
    @LocalServerPort private int serverPort;

    private Long userId;
    private Long playerId;
    private String accessToken;
    private String testUsername;

    @MockitoBean private BroadcastService broadcastService;
    @MockitoBean private FactionService factionService;
    @MockitoBean private InventoryService inventoryService;
    @MockitoBean private MonsterService monsterService;
    @MockitoBean private PlayerEffectService playerEffectService;
    @MockitoBean private RecoveryService recoveryService;
    @MockitoBean private TitleService titleService;

    @BeforeEach
    void seedOnlyDisposableH2Rows() {
        boolean liveUi = Boolean.getBoolean("reader.e2e.live-ui");
        String exploreLocationKey = liveUi ? "ruined_station" : "h2_quest_zone";
        jdbcTemplate.update("DELETE FROM player_quests WHERE quest_key = ?", QUEST_KEY);
        jdbcTemplate.update("DELETE FROM quest_progress_logs WHERE quest_key = ?", QUEST_KEY);
        jdbcTemplate.update("DELETE FROM quests WHERE quest_key = ?", QUEST_KEY);
        jdbcTemplate.update("DELETE FROM player_quests WHERE quest_key = ?", "h2_daily_first_victory");
        jdbcTemplate.update("DELETE FROM quest_progress_logs WHERE quest_key = ?", "h2_daily_first_victory");
        jdbcTemplate.update("DELETE FROM quests WHERE quest_key = ?", "h2_daily_first_victory");
        jdbcTemplate.update("DELETE FROM player_quests WHERE quest_key = ?", "h2_weekly_boss_attacks");
        jdbcTemplate.update("DELETE FROM quest_progress_logs WHERE quest_key = ?", "h2_weekly_boss_attacks");
        jdbcTemplate.update("DELETE FROM quests WHERE quest_key = ?", "h2_weekly_boss_attacks");
        jdbcTemplate.update("DELETE FROM player_quests WHERE quest_key IN (?, ?)",
                "h2_daily_trade_buy", "h2_daily_trade_sell");
        jdbcTemplate.update("DELETE FROM quest_progress_logs WHERE quest_key IN (?, ?)",
                "h2_daily_trade_buy", "h2_daily_trade_sell");
        jdbcTemplate.update("DELETE FROM quests WHERE quest_key IN (?, ?)",
                "h2_daily_trade_buy", "h2_daily_trade_sell");
        jdbcTemplate.update("DELETE FROM player_quests WHERE quest_key IN (?, ?, ?)",
                "h2_daily_pk_participate", "h2_daily_pk_win", "h2_daily_faction_value");
        jdbcTemplate.update("DELETE FROM quest_progress_logs WHERE quest_key IN (?, ?, ?)",
                "h2_daily_pk_participate", "h2_daily_pk_win", "h2_daily_faction_value");
        jdbcTemplate.update("DELETE FROM quests WHERE quest_key IN (?, ?, ?)",
                "h2_daily_pk_participate", "h2_daily_pk_win", "h2_daily_faction_value");
        jdbcTemplate.update("DELETE FROM exploration_events WHERE event_key = ?", "h2_quest_explore_event");
        jdbcTemplate.update("DELETE FROM exploration_events WHERE event_key = ?", "h2_first_player_story_battle");
        jdbcTemplate.update("DELETE FROM player_equipment WHERE equipment_key = ?", "h2_first_victory_blade");
        jdbcTemplate.update("DELETE FROM equipment WHERE equipment_key = ?", "h2_first_victory_blade");
        jdbcTemplate.update("DELETE FROM locations WHERE location_key = ?", "h2_quest_zone");
        jdbcTemplate.update("DELETE FROM locations WHERE location_key = ?", "h2_story_zone");
        jdbcTemplate.update("DELETE FROM exploration_events WHERE location_key = ?", "ruined_station");
        jdbcTemplate.update("DELETE FROM locations WHERE location_key = ?", "ruined_station");

        jdbcTemplate.update("INSERT INTO locations (location_key, name, description, unlock_conditions_json, "
                        + "event_rates_json, min_level, danger_level, recommended_rank, is_default) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                exploreLocationKey, liveUi ? "废弃车站" : "隔离测试区", "仅供任务联动测试。", "{}", "{\"resource\":100}",
                1, 1, "F", 1);
        jdbcTemplate.update("INSERT INTO exploration_events (event_key, event_type, location_key, name, "
                        + "description, weight, stamina_cost, repeatable, required_conditions_json, "
                        + "rewards_json, choices_json, log_template, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "h2_quest_explore_event", "resource", exploreLocationKey, "测试区采集", "发现了可用材料。",
                1, 5, 1, "{}", liveUi ? "{\"coins\":6,\"storyFragments\":3}" : "{}",
                "[]", "在测试区完成了一次探索。", 1);
        if (liveUi) {
            jdbcTemplate.update("INSERT INTO quests (quest_key, title, description, quest_type, category, "
                            + "target_type, target_value, rewards_json, reset_cycle, enabled) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    "h2_weekly_boss_attacks", "每周试炼", "参与三次世界首领攻击", "weekly", "world_boss",
                    "world_boss_attack_count", 3, "{}", "weekly", 1);
            jdbcTemplate.update("INSERT INTO locations (location_key, name, description, unlock_conditions_json, "
                            + "event_rates_json, min_level, danger_level, recommended_rank, is_default) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    "h2_story_zone", "隔离剧情区", "只供新账号 Edge 战斗旅程使用。", "{}",
                    "{\"story\":100}", 1, 1, "F", 0);
            jdbcTemplate.update("INSERT INTO exploration_events (event_key, event_type, location_key, name, "
                            + "description, weight, stamina_cost, repeatable, required_conditions_json, "
                            + "rewards_json, choices_json, log_template, enabled) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    "h2_first_player_story_battle", "story", "h2_story_zone", "初次战斗试炼",
                    "隔离剧情场景中的确定性测试遭遇。", 1, 5, 0, "{}", "{}",
                    "[{\"label\":\"接受试炼\",\"consequence_text\":\"投影挡在你的道路前。\","
                            + "\"risks\":{\"damagePercent\":0,\"monsterKey\":\"h2_first_victory_beast\","
                            + "\"battleName\":\"初次战斗\",\"battleDescription\":\"迎战这道投影。\"}}]",
                    "你在隔离剧情区遇到了初次战斗试炼。", 1);
        }
        jdbcTemplate.update("INSERT INTO quests (quest_key, title, description, quest_type, category, "
                        + "target_type, target_value, rewards_json, reset_cycle, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                QUEST_KEY, "初次探索", "完成一次探索", "daily", "explore", "explore_count", 1,
                "{\"coins\":25,\"exp\":20}", "daily", 1);
        jdbcTemplate.update("INSERT INTO quests (quest_key, title, description, quest_type, category, "
                        + "target_type, target_value, rewards_json, reset_cycle, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "h2_daily_first_victory", "首次胜利", "赢得一场战斗", "daily", "combat",
                "combat_win_count", 1, "{\"coins\":10,\"exp\":10}", "daily", 1);

        if (liveUi) {
            jdbcTemplate.update("INSERT INTO equipment (equipment_key, name, slot, rarity, description, "
                            + "base_stats_json, special_effects_json, max_durability, repair_cost, sell_price, enabled) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    "h2_first_victory_blade", "H2新手短刃", "mainHand", "common", "Edge 旅程战斗掉落测试装备。",
                    "{\"attack\":5}", "{}", 100, 5, 1, 1);

            jdbcTemplate.update("DELETE FROM skills WHERE skill_key = ?", "steady_breath");
            jdbcTemplate.update("INSERT INTO skills (skill_key, name, type, rarity, description, "
                            + "unlock_conditions_json, effects_json, cost_json, cooldown_seconds, enabled) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    "steady_breath", "稳定呼吸", "passive", "common", "测试新手技能学习和技能槽规则。",
                    "{\"levelMin\":2}", "{\"maxStamina\":5,\"staminaCostReduce\":0.03}",
                    "{\"coins\":50}", 0, 1);

            jdbcTemplate.update("DELETE FROM avatar_rank_configs WHERE rank_key IN ('F', 'E', 'D')");
            seedAvatarRankConfig("F", "临时化身", "F级·临时化身", 1, "E", "[]", "{}");
            seedAvatarRankConfig("E", "剧本幸存者", "E级·剧本幸存者", 2, "D",
                    "[{\"type\":\"levelMin\",\"value\":2,\"label\":\"等级达到2\"},"
                            + "{\"type\":\"storyFragmentsMin\",\"value\":5,\"label\":\"故事碎片≥5\"},"
                            + "{\"type\":\"explorationsByLocation\",\"locationKey\":\"ruined_station\","
                            + "\"count\":2,\"label\":\"探索废弃车站≥2次\"}]",
                    "{\"stats\":{\"maxHp\":20,\"maxStamina\":10},"
                            + "\"log\":\"你已从临时化身晋升为剧本幸存者。\"}");
            seedAvatarRankConfig("D", "频道记录者", "D级·频道记录者", 3, "C",
                    "[{\"type\":\"levelMin\",\"value\":6,\"label\":\"等级达到6\"},"
                            + "{\"type\":\"storyEventsTriggeredMin\",\"value\":1,\"label\":\"触发至少1次主线事件\"},"
                            + "{\"type\":\"bossClue\",\"bossKey\":\"station_keeper\","
                            + "\"count\":1,\"label\":\"发现至少1条看门人线索\"}]",
                    "{\"stats\":{\"attack\":5,\"defense\":5},\"channelHeat\":20}");
        }

        Monster testMonster = new Monster();
        testMonster.setMonsterKey("h2_first_victory_beast");
        testMonster.setName("隔离测试投影");
        testMonster.setType("normal");
        testMonster.setRarity("common");
        testMonster.setLocationKey(liveUi ? "h2_story_zone" : exploreLocationKey);
        testMonster.setStatsJson("{\"hp\":1,\"attack\":0,\"defense\":0,\"speed\":1}");
        testMonster.setRewardsJson("{\"coins\":3,\"exp\":80}");
        testMonster.setDropsJson(liveUi
                ? "{\"equipment\":[{\"equipmentKey\":\"h2_first_victory_blade\",\"dropRate\":1.0}]}"
                : "{}");
        testMonster.setEnabled(1);
        when(monsterService.getMonsterEntity("h2_first_victory_beast")).thenReturn(testMonster);

        testUsername = "h2quest" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        if (!liveUi) {
            JsonNode registered = successfulRequest("/api/auth/register", HttpMethod.POST,
                    Map.of("username", testUsername,
                            "email", testUsername + "@example.invalid",
                            "password", TEST_PASSWORD), false);
            userId = registered.path("id").asLong();
            assertTrue(userId > 0, "注册接口应返回持久化用户 ID");

            JsonNode loggedIn = successfulRequest("/api/auth/login", HttpMethod.POST,
                    Map.of("usernameOrEmail", testUsername, "password", TEST_PASSWORD), false);
            assertEquals(userId, loggedIn.path("id").asLong(), "登录应回到刚注册的测试账号");
            accessToken = loggedIn.path("token").asText();
            assertFalse(accessToken.isBlank(), "登录接口应返回 JWT");

            JsonNode meBeforeCreation = successfulRequest("/api/auth/me", HttpMethod.GET, null, true);
            assertEquals(userId, meBeforeCreation.path("user").path("id").asLong());
            assertTrue(meBeforeCreation.path("player").isMissingNode()
                            || meBeforeCreation.path("player").isNull(),
                    "新账号尚未创建角色");

            JsonNode createdPlayer = successfulRequest("/api/auth/create-player", HttpMethod.POST,
                    Map.of("playerName", "隔离任务角色"), true);
            playerId = createdPlayer.path("id").asLong();
            assertTrue(playerId > 0, "建角接口应返回持久化角色 ID");
            jdbcTemplate.update("UPDATE players SET coins = ? WHERE id = ?", 10, playerId);

            JsonNode constellations = successfulRequest("/api/player/constellations", HttpMethod.GET,
                    null, false).path("constellations");
            assertTrue(constellations.toString().contains("morning_star"),
                    "新角色应从公开配置中选择一个有效背后星");
            successfulRequest("/api/player/select-constellation", HttpMethod.POST,
                    Map.of("playerId", playerId, "constellationKey", "morning_star"), true);

            JsonNode meAfterCreation = successfulRequest("/api/auth/me", HttpMethod.GET, null, true);
            assertEquals(playerId, meAfterCreation.path("player").path("id").asLong(),
                    "当前用户接口应返回刚创建的角色");
        }

        when(recoveryService.applyRecovery(any(Player.class))).thenAnswer(invocation ->
                objectMapper.readValue(((Player) invocation.getArgument(0)).getStatsJson(),
                        new TypeReference<LinkedHashMap<String, Object>>() {}));
        when(titleService.calculateTitleEffects(any())).thenReturn(Map.of());
        when(factionService.getFactionBuff(any())).thenReturn(Map.of());
        when(titleService.evaluateAndUnlockTitles(any(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(List.of());
    }

    @Test
    void successfulExplorationProgressCanBeClaimedOnceIntoResourcesAndExperience() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().contains("jdbc:h2:mem:quest_gameplay_persistence"),
                    "This integration test must only use its isolated in-memory H2 database");
        }

        boolean liveUi = Boolean.getBoolean("reader.e2e.live-ui");
        int expectedStaminaAfterExplorations = liveUi ? 35 : 45;
        if (!liveUi) {
            JsonNode availableQuests = successfulRequest("/api/quests/" + playerId, HttpMethod.GET, null, true);
            JsonNode quest = null;
            for (JsonNode candidate : availableQuests) {
                if (QUEST_KEY.equals(candidate.path("questKey").asText())) {
                    quest = candidate;
                    break;
                }
            }
            assertNotNull(quest, "任务列表接口应创建并返回测试任务");
            assertEquals("active", quest.path("status").asText());
        }

        JsonNode liveFlow = null;
        Map<String, Object> exploreRequest;
        Map<String, Object> claimRequest;
        JsonNode explored;
        if (liveUi) {
            liveFlow = runLiveEdgeQuestFlow();
            userId = jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE username = ?", Long.class, testUsername);
            playerId = jdbcTemplate.queryForObject(
                    "SELECT id FROM players WHERE user_id = ? AND player_name = ?",
                    Long.class, userId, "H2任务旅程角色");
            JsonNode loggedIn = successfulRequest("/api/auth/login", HttpMethod.POST,
                    Map.of("usernameOrEmail", testUsername, "password", TEST_PASSWORD), false);
            accessToken = loggedIn.path("token").asText();
            exploreRequest = objectMapper.convertValue(liveFlow.path("explorationRequest"),
                    new TypeReference<>() {});
            claimRequest = objectMapper.convertValue(liveFlow.path("claimRequests").get(0),
                    new TypeReference<>() {});
            explored = liveFlow.path("explorationResponse").path("data");
        } else {
            jdbcTemplate.update("UPDATE players SET current_location = ? WHERE id = ?", "h2_quest_zone", playerId);
            exploreRequest = Map.of(
                    "playerId", playerId,
                    "locationKey", "h2_quest_zone",
                    "requestKey", "h2-successful-exploration-001");
            claimRequest = Map.of(
                    "playerId", playerId,
                    "questKey", QUEST_KEY,
                    "cycleKey", jdbcTemplate.queryForObject(
                            "SELECT cycle_key FROM player_quests WHERE player_id = ? AND quest_key = ?",
                            String.class, playerId, QUEST_KEY),
                    "requestKey", "h2-claim-exploration-001");
            explored = successfulRequest("/api/explore/start", HttpMethod.POST, exploreRequest, true);
        }

        assertEquals("resource", explored.path("result_type").asText());
        assertEquals(5, explored.path("stamina_cost_paid").asInt());
        assertEquals(liveUi ? "ruined_station" : "h2_quest_zone",
                exploreRequest.get("locationKey"));
        assertEquals(expectedStaminaAfterExplorations,
                objectMapper.readTree(playerMapper.selectById(playerId).getStatsJson())
                .path("stamina").asInt());
        assertEquals(liveUi ? "claimed" : "completed", jdbcTemplate.queryForObject(
                "SELECT status FROM player_quests WHERE player_id = ? AND quest_key = ?",
                String.class, playerId, QUEST_KEY));
        assertEquals(liveUi ? 3 : 1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? "
                        + "AND source_type = 'explore' AND resource_key = 'stamina' "
                        + "AND amount = -5",
                Integer.class, playerId),
                "Edge 旅程中的两次资源探索和一次剧情探索都应分别记录真实体力支出");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quest_progress_logs WHERE player_id = ? AND quest_key = ? "
                        + "AND source = ? AND related_id = ? AND progress_before = 0 AND progress_after = 1",
                Integer.class, playerId, QUEST_KEY, "explore", "h2_quest_explore_event"));

        JsonNode replay = successfulRequest("/api/explore/start", HttpMethod.POST, exploreRequest, true);
        assertTrue(replay.path("request_replayed").asBoolean());
        assertEquals(expectedStaminaAfterExplorations,
                objectMapper.readTree(playerMapper.selectById(playerId).getStatsJson())
                .path("stamina").asInt(), "同请求键重放不得再次扣体力");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quest_progress_logs WHERE player_id = ? AND quest_key = ?",
                Integer.class, playerId, QUEST_KEY), "同请求键重放不得重复推进探索任务");

        String cycleKey = jdbcTemplate.queryForObject(
                "SELECT cycle_key FROM player_quests WHERE player_id = ? AND quest_key = ?",
                String.class, playerId, QUEST_KEY);
        JsonNode reward;
        if (liveUi) {
            reward = liveFlow.path("claimResponse").path("data");
        } else {
            CompletableFuture<ResponseEntity<JsonNode>> firstClaim = CompletableFuture.supplyAsync(
                    () -> request("/api/quests/claim", HttpMethod.POST, claimRequest, true));
            CompletableFuture<ResponseEntity<JsonNode>> concurrentClaim = CompletableFuture.supplyAsync(
                    () -> request("/api/quests/claim", HttpMethod.POST, claimRequest, true));
            ResponseEntity<JsonNode> firstResponse = firstClaim.get(15, TimeUnit.SECONDS);
            ResponseEntity<JsonNode> concurrentResponse = concurrentClaim.get(15, TimeUnit.SECONDS);
            assertTrue(firstResponse.getStatusCode().is2xxSuccessful());
            assertTrue(concurrentResponse.getStatusCode().is2xxSuccessful());
            assertTrue(firstResponse.getBody().path("success").asBoolean());
            assertTrue(concurrentResponse.getBody().path("success").asBoolean());
            reward = firstResponse.getBody().path("data");
            assertEquals(reward, concurrentResponse.getBody().path("data"),
                    "并发同键领奖应返回完全相同的原奖励响应");
        }

        assertTrue(reward.path("claimed").asBoolean());
        assertEquals(25, reward.path("earned").path("coins").asInt());
        Player playerAfterClaim = playerMapper.selectById(playerId);
        int coinsBeforeQuestReward = liveUi ? 6 : 10;
        int expectedCoinsAfterClaim = coinsBeforeQuestReward + 25;
        int expectedCoinsAfterFullUiFlow = liveUi ? 0 : expectedCoinsAfterClaim;
        assertEquals(expectedCoinsAfterFullUiFlow, playerAfterClaim.getCoins());
        JsonNode statsAfterClaim = objectMapper.readTree(playerAfterClaim.getStatsJson());
        assertEquals(liveUi ? 110 : 20, statsAfterClaim.path("exp").asInt());
        assertEquals("claimed", jdbcTemplate.queryForObject(
                "SELECT status FROM player_quests WHERE player_id = ? AND quest_key = ?",
                String.class, playerId, QUEST_KEY));
        assertEquals(liveUi ? 4 : 2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? "
                        + "AND source_type = 'quest_reward'",
                Integer.class, playerId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? "
                        + "AND resource_key = 'coins' AND amount = 25 AND balance_before = ? AND balance_after = ?",
                Integer.class, playerId, coinsBeforeQuestReward, expectedCoinsAfterClaim));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? "
                        + "AND resource_key = 'exp' AND amount = 20 AND balance_before = 0 AND balance_after = 20",
                Integer.class, playerId));
        assertEquals(liveUi ? 2 : 1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_action_receipts WHERE player_id = ? AND action_type = 'quest.claim'",
                Integer.class, playerId), "每次不同任务领奖各自只创建一条操作回执");

        int resourceTransactionsBeforeDuplicateClaim = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ?",
                Integer.class, playerId);
        ResponseEntity<JsonNode> duplicateClaim = request("/api/quests/claim", HttpMethod.POST,
                claimRequest, true);
        assertTrue(duplicateClaim.getStatusCode().is2xxSuccessful(), "同请求键重试应恢复原领奖结果");
        assertNotNull(duplicateClaim.getBody());
        assertTrue(duplicateClaim.getBody().path("success").asBoolean());
        assertTrue(duplicateClaim.getBody().path("data").path("claimed").asBoolean());
        assertEquals(25, duplicateClaim.getBody().path("data").path("earned").path("coins").asInt());
        Map<String, Object> conflictingClaimRequest = new LinkedHashMap<>(claimRequest);
        conflictingClaimRequest.put("cycleKey", cycleKey + "-different");
        ResponseEntity<JsonNode> conflictingClaim = request("/api/quests/claim", HttpMethod.POST,
                conflictingClaimRequest, true);
        assertFalse(conflictingClaim.getStatusCode().is2xxSuccessful(),
                "同一请求键不能用于不同任务周期");
        assertEquals(resourceTransactionsBeforeDuplicateClaim, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ?",
                Integer.class, playerId), "重复领奖不得新增任何资源流水");
        assertEquals(expectedCoinsAfterFullUiFlow, playerMapper.selectById(playerId).getCoins());
        assertEquals(liveUi ? 110 : 20, objectMapper.readTree(playerMapper.selectById(playerId).getStatsJson())
                .path("exp").asInt());
        List<String> rewardLogs = jdbcTemplate.query(
                "SELECT metadata_json FROM player_logs WHERE player_id = ? AND type = 'quest' "
                        + "AND metadata_json IS NOT NULL",
                (resultSet, rowNumber) -> resultSet.getString("metadata_json"), playerId);
        assertTrue(rewardLogs.stream().anyMatch(metadataJson -> {
            try {
                return "quest_reward".equals(objectMapper.readTree(metadataJson).path("kind").asText());
            } catch (Exception exception) {
                throw new AssertionError("Quest reward log metadata should be valid JSON", exception);
            }
        }));

        assertSamePlayerCanWinAndClaimCombatQuest(liveFlow);
    }

    @Test
    void marketPurchaseAdvancesBuyerAndSellerQuestsOnlyOnceWhenRequestIsReplayed() {
        assumeFalse(Boolean.getBoolean("reader.e2e.live-ui"),
                "交易—任务跨服务测试只运行在独立注册的 H2 测试账号模式");
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().contains("jdbc:h2:mem:quest_gameplay_persistence"),
                    "交易—任务集成测试必须只使用隔离的内存 H2 数据库");
        } catch (Exception exception) {
            throw new AssertionError("无法确认隔离 H2 数据源", exception);
        }

        String sellerName = "H2隔离卖家" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        jdbcTemplate.update("INSERT INTO players (user_id, player_name, coins, stats_json) VALUES (?, ?, ?, ?)",
                userId, sellerName, 0, "{}");
        Long sellerPlayerId = jdbcTemplate.queryForObject(
                "SELECT id FROM players WHERE user_id = ? AND player_name = ?", Long.class, userId, sellerName);
        assertNotNull(sellerPlayerId);

        jdbcTemplate.update("INSERT INTO quests (quest_key, title, description, quest_type, category, "
                        + "target_type, target_value, rewards_json, reset_cycle, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "h2_daily_trade_buy", "隔离买入委托", "完成两次市场购买", "daily", "trade",
                "trade_buy_count", 2, "{}", "daily", 1);
        jdbcTemplate.update("INSERT INTO quests (quest_key, title, description, quest_type, category, "
                        + "target_type, target_value, rewards_json, reset_cycle, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "h2_daily_trade_sell", "隔离卖出委托", "完成两次市场出售", "daily", "trade",
                "trade_sell_count", 2, "{}", "daily", 1);
        questService.ensurePlayerQuests(playerId);
        questService.ensurePlayerQuests(sellerPlayerId);

        String itemKey = "h2_trade_token_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        jdbcTemplate.update("INSERT INTO items (item_key, name, item_type, rarity, description, effects_json, "
                        + "consume_on_use, sell_price, max_stack, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                itemKey, "隔离交易凭证", "material", "common", "只存在于 H2 的交易测试物品。",
                "{}", 0, 1, 99, 1);
        jdbcTemplate.update("INSERT INTO player_inventory (player_id, item_key, quantity) VALUES (?, ?, ?)",
                sellerPlayerId, itemKey, 1);
        jdbcTemplate.update("UPDATE players SET coins = ? WHERE id = ?", 100, playerId);

        String nonce = UUID.randomUUID().toString().replace("-", "");
        var listing = tradeService.createItemListing(sellerPlayerId, itemKey, 1, 25,
                "h2-trade-list-" + nonce);
        String purchaseRequestKey = "h2-trade-buy-" + nonce;
        var purchase = tradeService.buyListing(playerId, listing.getListingNo(), purchaseRequestKey);

        assertEquals(playerId, purchase.getBuyerPlayerId());
        assertEquals(sellerPlayerId, purchase.getSellerPlayerId());
        assertEquals(75, purchase.getNewBuyerCoins());
        assertEquals(1, questProgress(playerId, "h2_daily_trade_buy"));
        assertEquals(1, questProgress(sellerPlayerId, "h2_daily_trade_sell"));
        assertEquals(1, countTradeQuestProgressLogs(playerId, "h2_daily_trade_buy", listing.getListingNo()));
        assertEquals(1, countTradeQuestProgressLogs(sellerPlayerId, "h2_daily_trade_sell", listing.getListingNo()));

        var replay = tradeService.buyListing(playerId, listing.getListingNo(), purchaseRequestKey);

        assertEquals(purchase.getListingNo(), replay.getListingNo());
        assertEquals(purchase.getNewBuyerCoins(), replay.getNewBuyerCoins());
        assertEquals(1, questProgress(playerId, "h2_daily_trade_buy"), "同请求键重放不得重复推进买入任务");
        assertEquals(1, questProgress(sellerPlayerId, "h2_daily_trade_sell"), "同请求键重放不得重复推进卖出任务");
        assertEquals(1, countTradeQuestProgressLogs(playerId, "h2_daily_trade_buy", listing.getListingNo()));
        assertEquals(1, countTradeQuestProgressLogs(sellerPlayerId, "h2_daily_trade_sell", listing.getListingNo()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM trade_records WHERE listing_no = ? AND status = 'success'",
                Integer.class, listing.getListingNo()));
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE source_type = 'market_trade' "
                        + "AND source_id = ?", Integer.class, listing.getListingNo()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_logs WHERE player_id = ? AND type = 'trade_buy' "
                        + "AND message LIKE '%隔离交易凭证%'", Integer.class, playerId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_logs WHERE player_id = ? AND type = 'trade_sold' "
                        + "AND message LIKE '%隔离交易凭证%'", Integer.class, sellerPlayerId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT quantity FROM player_inventory WHERE player_id = ? AND item_key = ?",
                Integer.class, playerId, itemKey));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_inventory WHERE player_id = ? AND item_key = ?",
                Integer.class, sellerPlayerId, itemKey));
        assertEquals(75, jdbcTemplate.queryForObject("SELECT coins FROM players WHERE id = ?",
                Integer.class, playerId));
        assertEquals(25, jdbcTemplate.queryForObject("SELECT coins FROM players WHERE id = ?",
                Integer.class, sellerPlayerId));
    }

    @Test
    void acceptedPkAdvancesBothParticipationQuestsAndWinnerQuestOnlyOnceOnReplay() throws Exception {
        assumeFalse(Boolean.getBoolean("reader.e2e.live-ui"),
                "PK—任务跨服务测试只运行在独立注册的 H2 测试账号模式");
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().contains("jdbc:h2:mem:quest_gameplay_persistence"),
                    "PK—任务集成测试必须只使用隔离的内存 H2 数据库");
        }

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String defenderUsername = "h2pk" + suffix;
        jdbcTemplate.update("INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)",
                defenderUsername, defenderUsername + "@example.invalid", "isolated-test-only");
        Long defenderUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, defenderUsername);
        String defenderName = "隔离PK对手" + suffix;
        String defenderStats = "{\"hp\":1000,\"maxHp\":1000,\"attack\":1,\"defense\":0,"
                + "\"speed\":1,\"critRate\":0,\"critDamage\":1.5,\"level\":1,\"exp\":0,"
                + "\"avatarRank\":\"F\",\"constellationBonusApplied\":0}";
        jdbcTemplate.update("INSERT INTO players (user_id, player_name, coins, stats_json) VALUES (?, ?, ?, ?)",
                defenderUserId, defenderName, 0, defenderStats);
        Long defenderPlayerId = jdbcTemplate.queryForObject(
                "SELECT id FROM players WHERE user_id = ? AND player_name = ?",
                Long.class, defenderUserId, defenderName);

        Map<String, Object> attackerStats = objectMapper.readValue(
                playerMapper.selectById(playerId).getStatsJson(), new TypeReference<>() {});
        attackerStats.put("hp", 1000);
        attackerStats.put("maxHp", 1000);
        attackerStats.put("attack", 1000);
        attackerStats.put("defense", 1000);
        attackerStats.put("speed", 1000);
        attackerStats.put("critRate", 0);
        attackerStats.put("critDamage", 1.5);
        jdbcTemplate.update("UPDATE players SET stats_json = ? WHERE id = ?",
                objectMapper.writeValueAsString(attackerStats), playerId);

        jdbcTemplate.update("INSERT INTO quests (quest_key, title, description, quest_type, category, "
                        + "target_type, target_value, rewards_json, reset_cycle, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "h2_daily_pk_participate", "隔离PK参与委托", "参与两次对练", "daily", "pk",
                "pk_participate_count", 2, "{}", "daily", 1);
        jdbcTemplate.update("INSERT INTO quests (quest_key, title, description, quest_type, category, "
                        + "target_type, target_value, rewards_json, reset_cycle, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "h2_daily_pk_win", "隔离PK胜利委托", "赢得两次对练", "daily", "pk",
                "pk_win_count", 2, "{}", "daily", 1);
        questService.ensurePlayerQuests(playerId);
        questService.ensurePlayerQuests(defenderPlayerId);

        Map<String, Object> challenge = pkService.createChallenge(
                playerId, defenderPlayerId, "spar", userId);
        Long challengeId = ((Number) challenge.get("challengeId")).longValue();
        String requestKey = "h2-pk-quest-" + suffix;
        Map<String, Object> result = pkService.resolveChallenge(
                challengeId, true, defenderPlayerId, defenderUserId, requestKey);

        assertTrue((Boolean) result.get("accepted"));
        @SuppressWarnings("unchecked")
        Map<String, Object> battle = (Map<String, Object>) result.get("battle");
        Long winnerPlayerId = ((Number) battle.get("winnerId")).longValue();
        Long loserPlayerId = ((Number) battle.get("loserId")).longValue();
        assertEquals(playerId, winnerPlayerId, "隔离战力配置应使挑战方稳定获胜");
        assertEquals(defenderPlayerId, loserPlayerId);
        String relatedId = playerId + "_vs_" + defenderPlayerId;

        assertEquals(1, questProgress(playerId, "h2_daily_pk_participate"));
        assertEquals(1, questProgress(defenderPlayerId, "h2_daily_pk_participate"));
        assertEquals(1, questProgress(winnerPlayerId, "h2_daily_pk_win"));
        assertEquals(0, questProgress(loserPlayerId, "h2_daily_pk_win"));
        assertEquals(1, countQuestProgressLogs(playerId, "h2_daily_pk_participate", relatedId));
        assertEquals(1, countQuestProgressLogs(defenderPlayerId, "h2_daily_pk_participate", relatedId));
        assertEquals(1, countQuestProgressLogs(winnerPlayerId, "h2_daily_pk_win", relatedId));

        Map<String, Object> replay = pkService.resolveChallenge(
                challengeId, true, defenderPlayerId, defenderUserId, requestKey);

        assertTrue((Boolean) replay.get("accepted"));
        assertEquals(1, questProgress(playerId, "h2_daily_pk_participate"), "PK回执重放不得重复推进挑战方参与任务");
        assertEquals(1, questProgress(defenderPlayerId, "h2_daily_pk_participate"), "PK回执重放不得重复推进应战方参与任务");
        assertEquals(1, questProgress(winnerPlayerId, "h2_daily_pk_win"), "PK回执重放不得重复推进胜者任务");
        assertEquals(1, countQuestProgressLogs(playerId, "h2_daily_pk_participate", relatedId));
        assertEquals(1, countQuestProgressLogs(defenderPlayerId, "h2_daily_pk_participate", relatedId));
        assertEquals(1, countQuestProgressLogs(winnerPlayerId, "h2_daily_pk_win", relatedId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pk_records WHERE attacker_id = ? AND defender_id = ?",
                Integer.class, playerId, defenderPlayerId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_action_receipts WHERE player_id = ? "
                        + "AND action_type = 'pk.challenge.resolve' AND idempotency_key = ?",
                Integer.class, defenderPlayerId, requestKey));
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE source_type = 'pk_battle' "
                        + "AND source_id = ?", Integer.class, challengeId.toString()));
    }

    @Test
    void worldBossAttackAdvancesWeeklyQuestAndCooldownRejectsImmediateDuplicate() throws Exception {
        assumeFalse(Boolean.getBoolean("reader.e2e.live-ui"),
                "世界首领—周任务持久化测试只运行在独立注册的 H2 测试账号模式");
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().contains("jdbc:h2:mem:quest_gameplay_persistence"),
                    "世界首领—周任务测试必须只使用隔离的内存 H2 数据库");
        }

        jdbcTemplate.update("INSERT INTO quests (quest_key, title, description, quest_type, category, "
                        + "target_type, target_value, rewards_json, reset_cycle, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "h2_weekly_boss_attacks", "隔离首领周任务", "攻击世界首领三次", "weekly", "world_boss",
                "world_boss_attack_count", 3, "{}", "weekly", 1);
        questService.ensurePlayerQuests(playerId);

        String bossNo = "h2-boss-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        java.sql.Timestamp startsAt = java.sql.Timestamp.valueOf(
                java.time.LocalDateTime.now().minusMinutes(1));
        java.sql.Timestamp endsAt = java.sql.Timestamp.valueOf(
                java.time.LocalDateTime.now().plusHours(1));
        jdbcTemplate.update("INSERT INTO world_bosses (boss_key, boss_no, name, description, status, level, "
                        + "max_hp, current_hp, attack, defense, speed, start_at, end_at, rewards_json, "
                        + "rank_rewards_json, worldline_effects_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "h2_boss", bossNo, "隔离首领", "只用于任务联动持久化测试。", "active", 1,
                1_000_000L, 1_000_000L, 1, 0, 1, startsAt, endsAt, "{}", "{}", "{}");

        String requestKey = "h2-boss-attack-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Map<String, Object> attackRequest = Map.of(
                "playerId", playerId, "bossNo", bossNo, "requestKey", requestKey);
        JsonNode attack = successfulRequest("/api/world-boss/attack", HttpMethod.POST, attackRequest, true);
        assertTrue(attack.path("success").asBoolean());
        assertEquals(1, attack.path("myAttackCount").asInt());
        assertEquals(1, questProgress(playerId, "h2_weekly_boss_attacks"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quest_progress_logs WHERE player_id = ? AND quest_key = ? "
                        + "AND source = 'worldBoss' AND related_id = ?",
                Integer.class, playerId, "h2_weekly_boss_attacks", bossNo));

        JsonNode replay = successfulRequest("/api/world-boss/attack", HttpMethod.POST, attackRequest, true);
        assertEquals(attack.path("damage").asLong(), replay.path("damage").asLong(),
                "同请求键应返回原攻击结果，而不是冷却错误或再次造成伤害");
        assertEquals(attack.path("myTotalDamage").asLong(), replay.path("myTotalDamage").asLong());

        Map<String, Object> newAttackRequest = Map.of(
                "playerId", playerId, "bossNo", bossNo, "requestKey", requestKey + "-new");
        ResponseEntity<JsonNode> cooldownResponse = request(
                "/api/world-boss/attack", HttpMethod.POST, newAttackRequest, true);
        assertFalse(cooldownResponse.getStatusCode().is2xxSuccessful(),
                "冷却中的新攻击请求仍应被拒绝");
        assertEquals("INVALID_FIELD_VALUE", cooldownResponse.getBody().path("error").path("code").asText());
        assertEquals(1, questProgress(playerId, "h2_weekly_boss_attacks"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT attack_count FROM world_boss_participation WHERE player_id = ? AND boss_no = ?",
                Integer.class, playerId, bossNo));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quest_progress_logs WHERE player_id = ? AND quest_key = ? "
                        + "AND source = 'worldBoss' AND related_id = ?",
                Integer.class, playerId, "h2_weekly_boss_attacks", bossNo));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_action_receipts WHERE player_id = ? "
                        + "AND action_type = 'world_boss.attack' AND idempotency_key = ?",
                Integer.class, playerId, requestKey));
    }

    private int questProgress(Long targetPlayerId, String questKey) {
        return jdbcTemplate.queryForObject(
                "SELECT progress FROM player_quests WHERE player_id = ? AND quest_key = ?",
                Integer.class, targetPlayerId, questKey);
    }

    private int countTradeQuestProgressLogs(Long targetPlayerId, String questKey, String listingNo) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quest_progress_logs WHERE player_id = ? AND quest_key = ? "
                        + "AND source = 'trade' AND related_id = ?",
                Integer.class, targetPlayerId, questKey, listingNo);
    }

    private int countQuestProgressLogs(Long targetPlayerId, String questKey, String relatedId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quest_progress_logs WHERE player_id = ? AND quest_key = ? "
                        + "AND source = 'pk' AND related_id = ?",
                Integer.class, targetPlayerId, questKey, relatedId);
    }


    private void assertSamePlayerCanWinAndClaimCombatQuest(JsonNode liveFlow) {
        boolean liveUi = Boolean.getBoolean("reader.e2e.live-ui");
        JsonNode activeQuests = successfulRequest("/api/quests/" + playerId, HttpMethod.GET, null, true);
        JsonNode battleQuest = findQuest(activeQuests, "h2_daily_first_victory");
        assertNotNull(battleQuest, "同一新角色应有每日首次胜利任务");
        assertEquals(liveUi ? "claimed" : "active", battleQuest.path("status").asText(),
                "Edge 流程应已完成战斗任务领奖；普通 HTTP 流程则从进行中开始");

        JsonNode liveCombatRequest = liveUi ? liveFlow.path("combatRequest") : null;
        String requestKey = liveUi
                ? liveCombatRequest.path("requestKey").asText()
                : "h2-first-player-victory-001";
        assertFalse(requestKey.isBlank(), "战斗请求必须保留原始幂等键");
        Map<String, Object> combatRequest = Map.of(
                "playerId", playerId,
                "monsterKey", "h2_first_victory_beast",
                "requestKey", requestKey);
        JsonNode battle = liveUi
                ? liveFlow.path("combatResponse").path("data")
                : successfulRequest("/api/combat/start", HttpMethod.POST, combatRequest, true);
        assertFalse(battle.isMissingNode(), "Edge 应返回真实战斗结算响应");
        assertEquals("win", battle.path("result").asText());
        assertEquals(3, battle.path("rewards").path("coins").asInt(),
                "战斗星币必须来自怪物配置");
        assertEquals(80, battle.path("rewards").path("exp").asInt(),
                "战斗经验必须来自怪物配置");
        if (liveUi) {
            assertEquals(1, battle.path("drops").size());
            assertEquals("h2_first_victory_blade", battle.path("drops").get(0)
                    .path("equipmentKey").asText());
        } else {
            assertTrue(battle.path("drops").isEmpty());
        }

        JsonNode completedQuests = successfulRequest("/api/quests/" + playerId, HttpMethod.GET, null, true);
        JsonNode completedBattleQuest = findQuest(completedQuests, "h2_daily_first_victory");
        assertNotNull(completedBattleQuest);
        assertEquals(liveUi ? "claimed" : "completed", completedBattleQuest.path("status").asText());
        assertEquals(1, completedBattleQuest.path("progress").asInt());
        String battleCycleKey = completedBattleQuest.path("cycleKey").asText();
        assertFalse(battleCycleKey.isBlank());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quest_progress_logs WHERE player_id = ? AND quest_key = ? "
                        + "AND source = 'combat' AND related_id = 'h2_first_victory_beast' "
                        + "AND progress_before = 0 AND progress_after = 1",
                Integer.class, playerId, "h2_daily_first_victory"));

        Map<String, Object> combatQuestClaimRequest = liveUi
                ? objectMapper.convertValue(liveFlow.path("claimRequests").get(1), new TypeReference<>() {})
                : Map.of("playerId", playerId,
                        "questKey", "h2_daily_first_victory",
                        "cycleKey", battleCycleKey,
                        "requestKey", "h2-claim-first-victory-001");
        JsonNode combatQuestReward = liveUi
                ? liveFlow.path("battleQuestClaimResponse").path("data")
                : successfulRequest("/api/quests/claim", HttpMethod.POST, combatQuestClaimRequest, true);
        assertTrue(combatQuestReward.path("claimed").asBoolean());
        assertEquals(10, combatQuestReward.path("earned").path("coins").asInt());
        assertEquals(10, combatQuestReward.path("earned").path("exp").asInt());

        Player finalPlayer = playerMapper.selectById(playerId);
        assertEquals(liveUi ? 0 : 48, finalPlayer.getCoins(),
                "Edge 角色应累计探索、怪物、任务奖励并支付已学习技能的星币成本");
        JsonNode finalStats;
        try {
            finalStats = objectMapper.readTree(finalPlayer.getStatsJson());
        } catch (Exception exception) {
            throw new AssertionError("战斗后玩家属性 JSON 应有效", exception);
        }
        assertEquals(110, finalStats.path("exp").asInt());
        assertEquals(2, finalStats.path("level").asInt(), "怪物经验应推进新角色升级");
        assertEquals(liveUi ? 113 : 13, finalStats.path("freePoints").asInt(),
                "Edge 位阶校准应发放现有规则配置的 100 点自由属性点");
        if (liveUi) {
            assertEquals("E", finalStats.path("avatarRank").asText());
            JsonNode statsBeforeRankUp = liveFlow.path("playerAfterEquip").path("stats");
            assertEquals(statsBeforeRankUp.path("maxHp").asInt() + 20,
                    finalStats.path("maxHp").asInt(), "E 位格应在当前背后星属性基础上增加 20 最大生命");
            assertEquals(statsBeforeRankUp.path("maxStamina").asInt() + 10,
                    finalStats.path("maxStamina").asInt(), "E 位格应在当前背后星属性基础上增加 10 最大体力");
            assertEquals(6, jdbcTemplate.queryForObject(
                    "SELECT story_fragments FROM players WHERE id = ?", Integer.class, playerId));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_skills WHERE player_id = ? AND skill_key = ? AND equipped = 0",
                    Integer.class, playerId, "steady_breath"),
                    "新手可以先学技能，但 F/E 位阶尚无槽位，因此技能尚未装备");
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? "
                            + "AND source_type = 'skill_unlock' AND resource_key = 'coins' "
                            + "AND amount = -50 AND balance_before = 50 AND balance_after = 0",
                    Integer.class, playerId), "真实技能学习应扣除并记录 50 星币");
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_logs WHERE player_id = ? AND type = 'skill_unlock' "
                            + "AND message LIKE '%稳定呼吸%'",
                    Integer.class, playerId), "技能学习应写入角色历程");
            assertTrue(liveFlow.path("skillUnlockResponse").path("data").path("skillKey")
                    .asText().equals("steady_breath"));
            assertTrue(liveFlow.path("rankUpResponse").path("data").path("currentRank")
                    .path("key").asText().equals("E"));
        }
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM battle_logs WHERE player_id = ? AND request_key = ? "
                        + "AND monster_key = ? AND result = 'win'",
                Integer.class, playerId, requestKey, "h2_first_victory_beast"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? "
                        + "AND source_type = 'combat' AND resource_key = 'coins' "
                        + "AND amount = 3 AND balance_before = ? AND balance_after = ?",
                Integer.class, playerId, liveUi ? 37 : 35, liveUi ? 40 : 38));

        JsonNode replay = successfulRequest("/api/combat/start", HttpMethod.POST, combatRequest, true);
        assertEquals("win", replay.path("result").asText());
        assertEquals(3, replay.path("rewards").path("coins").asInt());
        assertEquals(liveUi ? 0 : 48, playerMapper.selectById(playerId).getCoins(),
                "同一战斗请求键重放不得再次发放怪物奖励");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM battle_logs WHERE player_id = ? AND request_key = ?",
                Integer.class, playerId, requestKey));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quest_progress_logs WHERE player_id = ? AND quest_key = ? "
                        + "AND source = 'combat'",
                Integer.class, playerId, "h2_daily_first_victory"));
        if (liveUi) {
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_equipment WHERE player_id = ? AND equipment_key = ?",
                    Integer.class, playerId, "h2_first_victory_blade"),
                    "真实战斗掉落应保存到新角色装备栏");
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT equipped FROM player_equipment WHERE player_id = ? AND equipment_key = ?",
                    Integer.class, playerId, "h2_first_victory_blade"),
                    "Edge 装备面板的穿戴操作应写回 H2");
            JsonNode playerAfterEquip = liveFlow.path("playerAfterEquip");
            assertTrue(playerAfterEquip.path("stats").path("combatProjection").asBoolean());
            assertEquals(5, playerAfterEquip.path("stats").path("combatBonusSources")
                    .path("equipment").path("attack").asInt(),
                    "玩家属性详情应从真实穿戴记录投影出攻击 +5");
        }
    }

    private JsonNode findQuest(JsonNode quests, String questKey) {
        for (JsonNode quest : quests) {
            if (questKey.equals(quest.path("questKey").asText())) return quest;
        }
        return null;
    }

    private void seedAvatarRankConfig(String key, String name, String displayName, int order,
                                      String nextKey, String requirements, String rewards) {
        jdbcTemplate.update("INSERT INTO avatar_rank_configs (rank_key, rank_name, display_name, "
                        + "order_num, next_rank_key, requirements_json, rewards_json, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 1)",
                key, name, displayName, order, nextKey, requirements, rewards);
    }

    @Test
    void questExperienceCrossingLevelThresholdPersistsLevelAndFreePoints() throws Exception {
        assumeFalse(Boolean.getBoolean("reader.e2e.live-ui"),
                "该纯 HTTP H2 用例不属于 Edge live-ui 模式；live-ui 只运行同类中的新账号浏览器旅程");
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().contains("jdbc:h2:mem:quest_gameplay_persistence"),
                    "This integration test must only use its isolated in-memory H2 database");
        }

        jdbcTemplate.update("UPDATE quests SET rewards_json = ? WHERE quest_key = ?",
                "{\"exp\":100}", QUEST_KEY);
        successfulRequest("/api/quests/" + playerId, HttpMethod.GET, null, true);
        successfulRequest("/api/explore/start", HttpMethod.POST, Map.of(
                "playerId", playerId,
                "locationKey", "h2_quest_zone",
                "requestKey", "h2-level-up-exploration-001"), true);

        String cycleKey = jdbcTemplate.queryForObject(
                "SELECT cycle_key FROM player_quests WHERE player_id = ? AND quest_key = ?",
                String.class, playerId, QUEST_KEY);
        JsonNode reward = successfulRequest("/api/quests/claim", HttpMethod.POST, Map.of(
                "playerId", playerId, "questKey", QUEST_KEY, "cycleKey", cycleKey,
                "requestKey", "h2-claim-level-reward-001"), true);

        assertTrue(reward.path("claimed").asBoolean());
        assertEquals(100, reward.path("earned").path("exp").asInt());
        JsonNode finalStats = objectMapper.readTree(playerMapper.selectById(playerId).getStatsJson());
        assertEquals(2, finalStats.path("level").asInt(), "100 点累计经验应使 1 级角色升至 2 级");
        assertEquals(100, finalStats.path("exp").asInt(), "exp 字段保存生命周期累计经验");
        assertEquals(400, finalStats.path("maxExp").asInt());
        assertEquals(13, finalStats.path("freePoints").asInt(), "升级应增加 3 点自由属性点");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? "
                        + "AND resource_type = 'experience' AND resource_key = 'exp' "
                        + "AND amount = 100 AND balance_before = 0 AND balance_after = 100",
                Integer.class, playerId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_logs WHERE player_id = ? AND type = 'milestone' "
                        + "AND message LIKE '%等级 2%'",
                Integer.class, playerId), "升级历程应记录等级变化和自由属性点提示");
    }

    private JsonNode successfulRequest(String path, HttpMethod method, Object payload, boolean authenticated) {
        ResponseEntity<JsonNode> response = request(path, method, payload, authenticated);
        assertTrue(response.getStatusCode().is2xxSuccessful(),
                () -> path + " should succeed, response=" + response.getBody());
        assertNotNull(response.getBody(), path + " should return a JSON response");
        assertTrue(response.getBody().path("success").asBoolean(),
                () -> path + " should return success=true, response=" + response.getBody());
        return response.getBody().path("data");
    }

    private JsonNode runLiveEdgeQuestFlow() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().contains("jdbc:h2:mem:quest_gameplay_persistence"),
                    "Edge 任务领奖流程必须只连接隔离 H2");
        }

        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path repositoryRoot = Files.isDirectory(workingDirectory.resolve("frontend-player"))
                ? workingDirectory : workingDirectory.getParent();
        assertNotNull(repositoryRoot, "无法定位仓库根目录");
        Path frontendDirectory = repositoryRoot.resolve("frontend-player");
        Path script = frontendDirectory.resolve("e2e/quest-claim-live.mjs");
        assertTrue(Files.isRegularFile(script), "缺少隔离 H2 Edge 探索/任务脚本: " + script);

        ProcessBuilder processBuilder = new ProcessBuilder("node", script.toString())
                .directory(frontendDirectory.toFile())
                .redirectErrorStream(true);
        Map<String, String> environment = processBuilder.environment();
        environment.put("READER_E2E_BASE_URL", "http://localhost:5173");
        environment.put("READER_E2E_LIVE_BACKEND_URL", "http://localhost:" + serverPort);
        environment.put("READER_E2E_TEST_USERNAME", testUsername);
        environment.put("READER_E2E_TEST_EMAIL", testUsername + "@example.invalid");
        environment.put("READER_E2E_TEST_PASSWORD", TEST_PASSWORD);
        environment.put("READER_E2E_LOCATION_KEY",
                Boolean.getBoolean("reader.e2e.live-ui") ? "ruined_station" : "h2_quest_zone");

        Process process = processBuilder.start();
        CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                return "读取 Edge 探索/任务输出失败: " + exception.getMessage();
            }
        });
        boolean completed = process.waitFor(120, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            process.waitFor();
        }
        String output = outputFuture.get(10, TimeUnit.SECONDS);
        assertTrue(completed, "Edge 探索/任务流程超过 120 秒\n" + output);
        assertEquals(0, process.exitValue(), "Edge 探索/任务流程失败\n" + output);

        String resultPrefix = "QUEST_FLOW_RESULT=";
        String resultLine = output.lines()
                .filter(line -> line.startsWith(resultPrefix))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("Edge 未返回真实探索和任务领奖响应\n" + output));
        return objectMapper.readTree(resultLine.substring(resultPrefix.length()));
    }

    private ResponseEntity<JsonNode> request(String path, HttpMethod method, Object payload,
                                             boolean authenticated) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (authenticated) {
            headers.setBearerAuth(accessToken);
        }
        return restTemplate.exchange("http://localhost:" + serverPort + path,
                method, new HttpEntity<>(payload, headers), JsonNode.class);
    }
}
