package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.entity.BattleLog;
import com.huazhenghai.readergame.entity.BroadcastContribution;
import com.huazhenghai.readergame.entity.BroadcastEvent;
import com.huazhenghai.readergame.entity.Monster;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerLog;
import com.huazhenghai.readergame.entity.PlayerStoryLog;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.mapper.BattleLogMapper;
import com.huazhenghai.readergame.mapper.BroadcastContributionMapper;
import com.huazhenghai.readergame.mapper.BroadcastEventMapper;
import com.huazhenghai.readergame.mapper.PlayerInventoryMapper;
import com.huazhenghai.readergame.mapper.PlayerLogMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.PlayerStoryLogMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.service.PlayerActionReceiptService;
import com.huazhenghai.readergame.service.ExploreService;
import com.huazhenghai.readergame.service.BroadcastService;
import com.huazhenghai.readergame.service.FactionService;
import com.huazhenghai.readergame.service.MonsterService;
import com.huazhenghai.readergame.service.PlayerEffectService;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import com.huazhenghai.readergame.service.AiDirectorService;
import com.huazhenghai.readergame.service.AvatarRankService;
import com.huazhenghai.readergame.service.PkService;
import com.huazhenghai.readergame.service.InventoryService;
import com.huazhenghai.readergame.service.NarrativeService;
import com.huazhenghai.readergame.service.WorldBossService;
import com.huazhenghai.readergame.service.WorldlineService;
import com.huazhenghai.readergame.service.ChatService;
import com.huazhenghai.readergame.websocket.WebSocketSessionManager;
import com.huazhenghai.readergame.service.TradeService;
import com.huazhenghai.readergame.service.EquipmentService;
import com.huazhenghai.readergame.service.EndingService;
import com.huazhenghai.readergame.service.ChapterService;
import com.huazhenghai.readergame.service.PlayerService;
import com.huazhenghai.readergame.service.QuestService;
import com.huazhenghai.readergame.service.RecoveryService;
import com.huazhenghai.readergame.service.SkillService;
import com.huazhenghai.readergame.service.TitleService;
import com.huazhenghai.readergame.vo.CombatResultVO;
import com.huazhenghai.readergame.vo.ChapterProgressVO;
import com.huazhenghai.readergame.vo.TradeBuyResultVO;
import com.huazhenghai.readergame.vo.TradeListingVO;
import com.huazhenghai.readergame.vo.WorldBossRewardVO;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uses the configured in-memory H2 database to verify real MyBatis writes for combat settlement.
 * No account or database outside this test JVM is accessed.
 */
@SpringBootTest(
        properties = {
                "spring.profiles.active=test",
                "spring.datasource.url=jdbc:h2:mem:combat_persistence;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "app.ai-director.llm-api-key=",
                "app.scheduler.enabled=false"
        },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/db/combat-persistence-test-schema.sql")
class CombatPersistenceIntegrationTest {

    private static final long PLAYER_ID = 81001L;
    private static final long USER_ID = 91001L;
    private static final String REQUEST_KEY = "h2-combat-persistence-001";
    private static final String TEST_USERNAME = "h2-combat-user";
    private static final String TEST_PASSWORD = "isolated-test-only";

    @Autowired private PlayerMapper playerMapper;
    @Autowired private PlayerStoryLogMapper playerStoryLogMapper;
    @Autowired private PlayerInventoryMapper inventoryMapper;
    @Autowired private PlayerLogMapper playerLogMapper;
    @Autowired private BattleLogMapper battleLogMapper;
    @Autowired private BroadcastEventMapper broadcastEventMapper;
    @Autowired private BroadcastContributionMapper broadcastContributionMapper;
    @Autowired private PlayerLogService playerLogService;
    @Autowired private PlayerResourceTransactionService resourceTransactionService;
    @Autowired private AvatarRankService avatarRankService;
    @Autowired private PlayerActionReceiptService actionReceiptService;
    @Autowired private NarrativeService narrativeService;
    @Autowired private CombatServiceImpl combatService;
    @Autowired private PkService pkService;
    @Autowired private EquipmentService equipmentService;
    @Autowired private PlayerService playerService;
    @Autowired private SkillService skillService;
    @Autowired private InventoryService inventoryService;
    @Autowired private WorldBossService worldBossService;
    @Autowired private WorldlineService worldlineService;
    @Autowired private AiDirectorService aiDirectorService;
    @Autowired private TradeService tradeService;
    @Autowired private ExploreService exploreService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DataSource dataSource;
    @Autowired private MockMvc mockMvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TestRestTemplate testRestTemplate;
    @LocalServerPort private int serverPort;

    @MockitoBean private MonsterService monsterService;
    @MockitoBean private RecoveryService recoveryService;
    @MockitoBean private TitleService titleService;
    @MockitoBean private FactionService factionService;
    @MockitoBean private BroadcastService broadcastService;
    @MockitoBean private QuestService questService;
    @MockitoBean private PlayerEffectService playerEffectService;
    @MockitoBean private EndingService endingService;
    @MockitoBean private ChapterService chapterService;

    @BeforeEach
    void seedDisposableH2State() throws Exception {
        jdbcTemplate.update("DELETE FROM broadcast_contributions");
        jdbcTemplate.update("DELETE FROM broadcast_events");
        jdbcTemplate.update("DELETE FROM worldline_change_logs");
        jdbcTemplate.update("DELETE FROM world_states");
        jdbcTemplate.update("DELETE FROM player_faction_skills");
        jdbcTemplate.update("DELETE FROM player_skills");
        jdbcTemplate.update("DELETE FROM skills");
        jdbcTemplate.update("DELETE FROM avatar_rank_configs");
        jdbcTemplate.update("DELETE FROM player_factions");
        jdbcTemplate.update("DELETE FROM faction_skills");
        jdbcTemplate.update("DELETE FROM constellation_factions");
        jdbcTemplate.update("DELETE FROM pk_records");
        jdbcTemplate.update("DELETE FROM rankings");
        jdbcTemplate.update("DELETE FROM pk_challenges");
        jdbcTemplate.update("DELETE FROM trade_records");
        jdbcTemplate.update("DELETE FROM trade_listings");
        jdbcTemplate.update("DELETE FROM world_boss_participation");
        jdbcTemplate.update("DELETE FROM world_bosses");
        jdbcTemplate.update("DELETE FROM player_resource_transactions");
        jdbcTemplate.update("DELETE FROM player_action_receipts");
        jdbcTemplate.update("DELETE FROM player_npc_encounters");
        jdbcTemplate.update("DELETE FROM npc_ghosts");
        jdbcTemplate.update("DELETE FROM battle_logs");
        jdbcTemplate.update("DELETE FROM player_logs");
        jdbcTemplate.update("DELETE FROM player_story_logs");
        jdbcTemplate.update("DELETE FROM exploration_events");
        jdbcTemplate.update("DELETE FROM player_equipment");
        jdbcTemplate.update("DELETE FROM player_inventory");
        jdbcTemplate.update("DELETE FROM synthesis_recipes");
        jdbcTemplate.update("DELETE FROM equipment");
        jdbcTemplate.update("DELETE FROM items");
        jdbcTemplate.update("DELETE FROM locations");
        jdbcTemplate.update("DELETE FROM players");
        jdbcTemplate.update("DELETE FROM users");

        String statsJson = "{\"level\":1,\"maxHp\":100,\"hp\":100,\"attack\":1000,"
                + "\"defense\":5,\"speed\":10,\"critRate\":0.0,\"critDamage\":1.5,"
                + "\"exp\":0,\"avatarRank\":\"F\",\"constellation\":\"demon_judge_of_fire\"}";
        jdbcTemplate.update("INSERT INTO users (id, username, email, password_hash, role, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                USER_ID, TEST_USERNAME, "h2-combat-user@example.invalid",
                passwordEncoder.encode(TEST_PASSWORD), "player", "normal");
        jdbcTemplate.update("INSERT INTO players (id, user_id, player_name, coins, story_fragments, stats_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                PLAYER_ID, USER_ID, "隔离测试角色", 10, 0, statsJson);
        jdbcTemplate.update("INSERT INTO locations (location_key, name, recommended_rank) VALUES (?, ?, ?)",
                "h2_test_zone", "内存测试区", "F");
        jdbcTemplate.update("INSERT INTO items (item_key, name, item_type, rarity, max_stack, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "h2_test_herb", "测试草药", "material", "common", 10, 1);
        jdbcTemplate.update("INSERT INTO player_inventory (player_id, item_key, quantity) VALUES (?, ?, ?)",
                PLAYER_ID, "h2_test_herb", 9);
        jdbcTemplate.update("INSERT INTO equipment (equipment_key, name, slot, max_durability, enabled) "
                        + "VALUES (?, ?, ?, ?, ?)",
                "h2_test_blade", "测试短刃", "weapon", 60, 1);

        when(recoveryService.applyRecovery(any(Player.class))).thenAnswer(invocation ->
                objectMapper.readValue(((Player) invocation.getArgument(0)).getStatsJson(),
                        new TypeReference<LinkedHashMap<String, Object>>() {}));
        when(titleService.calculateTitleEffects(PLAYER_ID)).thenReturn(Map.of());
        when(factionService.getFactionBuff(PLAYER_ID)).thenReturn(Map.of());

        Monster monster = testMonster();
        when(monsterService.getMonsterEntity("h2_test_beast")).thenReturn(monster);
    }

    @Test
    void persistedEquippedEquipmentAndSkillFlowIntoThePlayerCombatProjection() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertEquals("jdbc:h2:mem:combat_persistence", connection.getMetaData().getURL());
        }

        jdbcTemplate.update("UPDATE players SET stats_json = ? WHERE id = ?",
                "{\"level\":1,\"maxHp\":100,\"hp\":100,\"attack\":10,\"defense\":5,"
                        + "\"speed\":3,\"critRate\":0.05,\"critDamage\":1.5,\"exp\":0,"
                        + "\"avatarRank\":\"D\"}",
                PLAYER_ID);
        jdbcTemplate.update("UPDATE equipment SET base_stats_json = ? WHERE equipment_key = ?",
                "{\"attack\":5}", "h2_test_blade");
        jdbcTemplate.update("INSERT INTO skills (skill_key, name, type, rarity, effects_json, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "h2_combat_skill", "隔离测试战斗技能", "passive", "common", "{\"attack\":3}", 1);
        jdbcTemplate.update("INSERT INTO player_skills (player_id, skill_key, level, equipped) "
                        + "VALUES (?, ?, ?, ?)",
                PLAYER_ID, "h2_combat_skill", 1, 0);

        assertEquals(1, equipmentService.addEquipment(PLAYER_ID, "h2_test_blade", "h2-combat-projection"));
        assertProjectedAttack(10, 0, 0);

        equipmentService.equip(PLAYER_ID, "h2_test_blade", USER_ID);
        skillService.equipSkill(PLAYER_ID, "h2_combat_skill", USER_ID);

        assertProjectedAttack(18, 5, 3);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT equipped FROM player_equipment WHERE player_id = ? AND equipment_key = ?",
                Integer.class, PLAYER_ID, "h2_test_blade"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT equipped FROM player_skills WHERE player_id = ? AND skill_key = ?",
                Integer.class, PLAYER_ID, "h2_combat_skill"));

        jdbcTemplate.update("UPDATE player_equipment SET durability = 0 WHERE player_id = ? "
                + "AND equipment_key = ?", PLAYER_ID, "h2_test_blade");
        assertProjectedAttack(13, 0, 3);

        skillService.unequipSkill(PLAYER_ID, "h2_combat_skill", USER_ID);
        assertProjectedAttack(10, 0, 0);
    }

    @Test
    void configuredRankProgressionUnlocksTheSkillSlotAndCombatBonusInH2() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertEquals("jdbc:h2:mem:combat_persistence", connection.getMetaData().getURL());
        }

        insertRankConfig("F", "临时化身", "F级·临时化身", 1, "E", "[]", "{}");
        insertRankConfig("E", "剧本幸存者", "E级·剧本幸存者", 2, "D",
                "[{\"type\":\"levelMin\",\"value\":2},"
                        + "{\"type\":\"storyFragmentsMin\",\"value\":5},"
                        + "{\"type\":\"explorationsByLocation\",\"locationKey\":\"ruined_station\",\"count\":2}]",
                "{\"stats\":{\"maxHp\":20,\"maxStamina\":10},"
                        + "\"log\":\"晋升为剧本幸存者。\"}");
        insertRankConfig("D", "频道记录者", "D级·频道记录者", 3, "C",
                "[{\"type\":\"levelMin\",\"value\":6},"
                        + "{\"type\":\"storyEventsTriggeredMin\",\"value\":1},"
                        + "{\"type\":\"bossClue\",\"bossKey\":\"station_keeper\",\"count\":1}]",
                "{\"stats\":{\"attack\":5,\"defense\":5},\"channelHeat\":20,"
                        + "\"log\":\"行动开始被频道记录。\"}");
        insertRankConfig("C", "剧本执行者", "C级·剧本执行者", 4, null, "[]", "{}");

        jdbcTemplate.update("UPDATE players SET stats_json = ?, stage_progress_json = ?, story_fragments = ? "
                        + "WHERE id = ?",
                "{\"level\":1,\"maxHp\":100,\"hp\":100,\"maxStamina\":50,"
                        + "\"attack\":10,\"defense\":5,\"speed\":3,\"critRate\":0.05,"
                        + "\"critDamage\":1.5,\"exp\":0,\"freePoints\":0,"
                        + "\"channelHeat\":0,\"avatarRank\":\"F\"}",
                "{}", 0, PLAYER_ID);
        jdbcTemplate.update("INSERT INTO skills (skill_key, name, type, rarity, "
                        + "unlock_conditions_json, effects_json, cost_json, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "h2_rank_unlocked_skill", "位格解锁测试技能", "passive", "common", "{}",
                "{\"attack\":3}", "{}", 1);
        skillService.unlockSkill(PLAYER_ID, "h2_rank_unlocked_skill", USER_ID, "h2-rank-unlock");

        BusinessException noInitialSlot = assertThrows(BusinessException.class,
                () -> skillService.equipSkill(PLAYER_ID, "h2_rank_unlocked_skill", USER_ID));
        assertEquals(ErrorCode.SKILL_SLOT_FULL, noInitialSlot.getCode());

        BusinessException unmetERequirements = assertThrows(BusinessException.class,
                () -> avatarRankService.rankUp(PLAYER_ID, USER_ID));
        assertEquals(ErrorCode.RANK_REQUIREMENTS_NOT_MET, unmetERequirements.getCode());
        assertEquals("F", objectMapper.readTree(playerMapper.selectById(PLAYER_ID).getStatsJson())
                .path("avatarRank").asText());

        jdbcTemplate.update("UPDATE players SET stats_json = ?, stage_progress_json = ?, story_fragments = ? "
                        + "WHERE id = ?",
                "{\"level\":2,\"maxHp\":100,\"hp\":100,\"maxStamina\":50,"
                        + "\"attack\":10,\"defense\":5,\"speed\":3,\"critRate\":0.05,"
                        + "\"critDamage\":1.5,\"exp\":0,\"freePoints\":0,"
                        + "\"channelHeat\":0,\"avatarRank\":\"F\"}",
                "{\"explorationsByLocation\":{\"ruined_station\":2}}", 5, PLAYER_ID);

        var firstPromotion = avatarRankService.rankUp(PLAYER_ID, USER_ID);
        assertEquals("E", firstPromotion.getCurrentRank().get("key"));
        BusinessException noESlot = assertThrows(BusinessException.class,
                () -> skillService.equipSkill(PLAYER_ID, "h2_rank_unlocked_skill", USER_ID));
        assertEquals(ErrorCode.SKILL_SLOT_FULL, noESlot.getCode());

        jdbcTemplate.update("UPDATE players SET stats_json = ?, stage_progress_json = ?, story_fragments = ? "
                        + "WHERE id = ?",
                "{\"level\":6,\"maxHp\":120,\"hp\":120,\"maxStamina\":60,"
                        + "\"attack\":10,\"defense\":5,\"speed\":3,\"critRate\":0.05,"
                        + "\"critDamage\":1.5,\"exp\":0,\"freePoints\":100,"
                        + "\"channelHeat\":0,\"avatarRank\":\"E\"}",
                "{\"explorationsByLocation\":{\"ruined_station\":2},"
                        + "\"storyEventsTriggered\":[\"h2_story_event\"],"
                        + "\"bossClues\":{\"station_keeper\":1}}", 5, PLAYER_ID);
        when(playerEffectService.applyChannelHeatGainRate(PLAYER_ID, 20)).thenReturn(20);

        var secondPromotion = avatarRankService.rankUp(PLAYER_ID, USER_ID);
        assertEquals("D", secondPromotion.getCurrentRank().get("key"));
        JsonNode storedRankStats = objectMapper.readTree(playerMapper.selectById(PLAYER_ID).getStatsJson());
        assertEquals("D", storedRankStats.path("avatarRank").asText());
        assertEquals(200, storedRankStats.path("freePoints").asInt());
        assertEquals(15, storedRankStats.path("attack").asInt());
        assertEquals(20, storedRankStats.path("channelHeat").asInt());
        assertResourceTransactionFrom("avatar_rank_up", "E->D", "progress", "channel_heat", 20, 0, 20);

        skillService.equipSkill(PLAYER_ID, "h2_rank_unlocked_skill", USER_ID);
        Map<String, Object> projectedStats = playerService.getPlayer(PLAYER_ID).getStats();
        assertTrue(Boolean.TRUE.equals(projectedStats.get("combatProjection")));
        assertEquals(28, ((Number) projectedStats.get("attack")).intValue(),
                "D 级现有属性奖励、等级成长与已装备技能应共同出现在战斗投影");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT equipped FROM player_skills WHERE player_id = ? AND skill_key = ?",
                Integer.class, PLAYER_ID, "h2_rank_unlocked_skill"));
    }

    private void insertRankConfig(String key, String name, String displayName, int order,
                                  String nextKey, String requirements, String rewards) {
        jdbcTemplate.update("INSERT INTO avatar_rank_configs (rank_key, rank_name, display_name, "
                        + "description, order_num, next_rank_key, requirements_json, rewards_json, "
                        + "unlocks_json, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                key, name, displayName, "H2 隔离测试位阶配置", order, nextKey,
                requirements, rewards, "{}", 1);
    }

    private void assertProjectedAttack(int expectedAttack, int expectedEquipmentAttack, int expectedSkillAttack) {
        Map<String, Object> stats = playerService.getPlayer(PLAYER_ID).getStats();
        assertTrue(Boolean.TRUE.equals(stats.get("combatProjection")),
                "玩家属性面板应成功复用权威战斗属性投影");
        assertEquals(expectedAttack, ((Number) stats.get("attack")).intValue());

        @SuppressWarnings("unchecked")
        Map<String, Object> sources = (Map<String, Object>) stats.get("combatBonusSources");
        @SuppressWarnings("unchecked")
        Map<String, Object> equipment = (Map<String, Object>) sources.getOrDefault("equipment", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> skill = (Map<String, Object>) sources.getOrDefault("skill", Map.of());
        assertEquals(expectedEquipmentAttack, number(equipment.get("attack")));
        assertEquals(expectedSkillAttack, number(skill.get("attack")));
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    @Test
    void concurrentPublicWorldlineMutationsAreSerializedInIsolatedH2() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertEquals("jdbc:h2:mem:combat_persistence", connection.getMetaData().getURL());
        }
        jdbcTemplate.update("INSERT INTO world_states (state_key, name, value_json) VALUES (?, ?, ?)",
                "worldline_shift", "世界线偏移", "{\"totalShift\":0}");

        int workers = 6;
        int incrementsPerWorker = 12;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<?>> tasks = new java.util.ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                tasks.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for concurrent start");
                    }
                    for (int increment = 0; increment < incrementsPerWorker; increment++) {
                        worldlineService.addWorldlineShift(1, "concurrent-h2-test");
                    }
                    return null;
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> task : tasks) task.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        String stateJson = jdbcTemplate.queryForObject(
                "SELECT value_json FROM world_states WHERE state_key = ?", String.class, "worldline_shift");
        assertEquals(workers * incrementsPerWorker,
                objectMapper.readTree(stateJson).path("totalShift").asInt());
        assertEquals(workers * incrementsPerWorker, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worldline_change_logs WHERE cause = ?", Integer.class, "concurrent-h2-test"));
    }

    @Test
    void failedWorldBossWorldlineAuditRollsBackSettlementAndCanBeRetried() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertEquals("jdbc:h2:mem:combat_persistence", connection.getMetaData().getURL());
        }
        String bossNo = "worldline-retry-test";
        String worldlineCause = "world_boss_" + bossNo;
        jdbcTemplate.update("INSERT INTO world_states (state_key, name, value_json) VALUES (?, ?, ?)",
                "worldline_shift", "世界线偏移", "{\"totalShift\":0}");
        jdbcTemplate.update("INSERT INTO world_bosses (boss_no, name, status, worldline_effects_json) "
                        + "VALUES (?, ?, ?, ?)",
                bossNo, "隔离重试首领", "killed", "{\"worldline_shift\":5}");
        jdbcTemplate.execute("ALTER TABLE worldline_change_logs ADD CONSTRAINT reject_test_worldline_log "
                + "CHECK (cause <> '" + worldlineCause + "')");

        try {
            assertThrows(RuntimeException.class, () -> worldBossService.settleBoss(bossNo));
        } finally {
            jdbcTemplate.execute("ALTER TABLE worldline_change_logs DROP CONSTRAINT IF EXISTS "
                    + "reject_test_worldline_log");
        }

        assertEquals("killed", jdbcTemplate.queryForObject(
                "SELECT status FROM world_bosses WHERE boss_no = ?", String.class, bossNo));
        String unchangedState = jdbcTemplate.queryForObject(
                "SELECT value_json FROM world_states WHERE state_key = ?", String.class, "worldline_shift");
        assertEquals(0, objectMapper.readTree(unchangedState).path("totalShift").asInt());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worldline_change_logs WHERE cause = ?", Integer.class, worldlineCause));

        worldBossService.settleBoss(bossNo);

        assertEquals("settled", jdbcTemplate.queryForObject(
                "SELECT status FROM world_bosses WHERE boss_no = ?", String.class, bossNo));
        String appliedState = jdbcTemplate.queryForObject(
                "SELECT value_json FROM world_states WHERE state_key = ?", String.class, "worldline_shift");
        assertEquals(5, objectMapper.readTree(appliedState).path("totalShift").asInt());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worldline_change_logs WHERE cause = ?", Integer.class, worldlineCause));
    }

    @Test
    void publicEventSnapshotIgnoresPersonalStoryChoiceLogsInIsolatedH2() {
        for (int i = 0; i < 12; i++) {
            jdbcTemplate.update("INSERT INTO player_logs (player_id, type, message, metadata_json) VALUES (?, ?, ?, ?)",
                    PLAYER_ID, "story", "隔离的个人选择 " + i,
                    "{\"kind\":\"story_choice\",\"requestLocationKey\":\"private-choice-" + i + "\"}");
        }

        Map<String, Object> draft = aiDirectorService.generateBroadcastDraft();

        // The 12 personal choice records must not masquerade as shared exploration actions.
        assertEquals("opportunity_rain", draft.get("eventType"));
    }

    @Test
    void publicEventSnapshotCountsRealExplorationResultsInIsolatedH2() {
        for (int i = 0; i < 11; i++) {
            jdbcTemplate.update("INSERT INTO player_logs (player_id, type, message, metadata_json) VALUES (?, ?, ?, ?)",
                    PLAYER_ID, "exploration", "隔离的公共探索 " + i,
                    "{\"kind\":\"exploration_result\",\"requestLocationKey\":\"h2_test_zone\"}");
        }

        Map<String, Object> draft = aiDirectorService.generateBroadcastDraft();

        assertEquals("story_hunt", draft.get("eventType"));
    }

    @Test
    void resourceLedgerStoresSignedBalancesAndTreatsExactReplayAsIdempotent() {
        PlayerResourceTransactionService.Change change = resourceChange(
                "repair:blade:coins", "currency", "coins", -3, 10, 7,
                "equipment_repair", "h2_test_blade", "repair-group-1");

        assertTrue(resourceTransactionService.record(change));
        assertFalse(resourceTransactionService.record(change));

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ?",
                Integer.class, PLAYER_ID));
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT resource_type, resource_key, amount, balance_before, balance_after, "
                        + "source_type, source_id, transaction_group_id, idempotency_key "
                        + "FROM player_resource_transactions WHERE player_id = ?",
                PLAYER_ID);
        assertEquals("currency", row.get("resource_type"));
        assertEquals("coins", row.get("resource_key"));
        assertEquals(-3L, ((Number) row.get("amount")).longValue());
        assertEquals(10L, ((Number) row.get("balance_before")).longValue());
        assertEquals(7L, ((Number) row.get("balance_after")).longValue());
        assertEquals("equipment_repair", row.get("source_type"));
        assertEquals("h2_test_blade", row.get("source_id"));
        assertEquals("repair-group-1", row.get("transaction_group_id"));
        assertEquals("repair:blade:coins", row.get("idempotency_key"));
    }

    @Test
    void resourceLedgerRejectsMismatchedBalancesAndConflictingIdempotencyReplay() {
        assertThrows(IllegalArgumentException.class, () -> resourceTransactionService.record(resourceChange(
                "invalid-balance", "currency", "coins", -3, 10, 8,
                "equipment_repair", "h2_test_blade", null)));
        assertThrows(IllegalArgumentException.class, () -> resourceTransactionService.record(resourceChange(
                "zero-delta", "currency", "coins", 0, 10, 10,
                "equipment_repair", "h2_test_blade", null)));

        PlayerResourceTransactionService.Change original = resourceChange(
                "unique-action", "currency", "coins", -3, 10, 7,
                "equipment_repair", "h2_test_blade", null);
        assertTrue(resourceTransactionService.record(original));
        assertThrows(IllegalStateException.class, () -> resourceTransactionService.record(resourceChange(
                "unique-action", "currency", "coins", -4, 10, 6,
                "equipment_repair", "h2_test_blade", null)));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ?",
                Integer.class, PLAYER_ID));
    }

    @Test
    void resourceLedgerRollsBackWithTheOwningPlayerAction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            Player player = playerMapper.selectById(PLAYER_ID);
            player.setCoins(7);
            assertTrue(playerMapper.updateById(player) > 0);
            assertTrue(resourceTransactionService.record(resourceChange(
                    "rollback-action", "currency", "coins", -3, 10, 7,
                    "equipment_repair", "h2_test_blade", null)));
            throw new IllegalStateException("simulate a later write failure");
        }));

        assertEquals(10, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ?",
                Integer.class, PLAYER_ID));
    }

    @Test
    void avatarPrestigePersistsClearedExperienceAndResourceLedgerTogether() throws Exception {
        String statsJson = "{\"level\":18,\"maxHp\":180,\"hp\":180,\"attack\":50,"
                + "\"defense\":20,\"speed\":10,\"exp\":321,\"avatarRank\":\"SSS\","
                + "\"prestigeLevel\":0}";
        jdbcTemplate.update("UPDATE players SET stats_json = ? WHERE id = ?", statsJson, PLAYER_ID);

        Map<String, Object> result = avatarRankService.prestige(PLAYER_ID, USER_ID);

        JsonNode updatedStats = objectMapper.readTree(playerMapper.selectById(PLAYER_ID).getStatsJson());
        assertEquals("F", updatedStats.path("avatarRank").asText());
        assertEquals(1, updatedStats.path("level").asInt());
        assertEquals(0, updatedStats.path("exp").asInt());
        assertEquals(321, result.get("experienceCleared"));
        assertResourceTransactionFrom("avatar_prestige", "1", "experience", "exp", -321, 321, 0);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? "
                        + "AND source_type = 'avatar_prestige'", Integer.class, PLAYER_ID));
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT message FROM player_logs WHERE player_id = ? AND type = 'system'",
                String.class, PLAYER_ID).contains("清空经验 321 点"));
    }

    @Test
    void avatarPrestigeDoesNotPersistWhenExperienceLedgerInsertFails() throws Exception {
        String statsJson = "{\"level\":18,\"maxHp\":180,\"hp\":180,\"attack\":50,"
                + "\"defense\":20,\"speed\":10,\"exp\":321,\"avatarRank\":\"SSS\","
                + "\"prestigeLevel\":0}";
        jdbcTemplate.update("UPDATE players SET stats_json = ? WHERE id = ?", statsJson, PLAYER_ID);
        jdbcTemplate.execute("ALTER TABLE player_resource_transactions ADD CONSTRAINT "
                + "ck_test_fail_avatar_prestige CHECK (source_type <> 'avatar_prestige')");

        try {
            assertThrows(RuntimeException.class, () -> avatarRankService.prestige(PLAYER_ID, USER_ID));
        } finally {
            jdbcTemplate.execute("ALTER TABLE player_resource_transactions "
                    + "DROP CONSTRAINT ck_test_fail_avatar_prestige");
        }

        JsonNode unchangedStats = objectMapper.readTree(playerMapper.selectById(PLAYER_ID).getStatsJson());
        assertEquals("SSS", unchangedStats.path("avatarRank").asText());
        assertEquals(321, unchangedStats.path("exp").asInt());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ?",
                Integer.class, PLAYER_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_logs WHERE player_id = ?", Integer.class, PLAYER_ID));
    }

    @Test
    void actionReceiptPersistsReplayFingerprintAndRollsBackWithItsOwningAction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Map<String, Object> request = Map.of("listingNo", "TL-H2-RECEIPT");
        TradeBuyResultVO response = new TradeBuyResultVO();
        response.setListingNo("TL-H2-RECEIPT");
        response.setBuyerPlayerId(PLAYER_ID);
        response.setTotalPrice(7);
        response.setNewBuyerCoins(3);

        transaction.executeWithoutResult(status -> {
            assertNull(actionReceiptService.findReplay(PLAYER_ID, "trade.buy", "h2-receipt-1",
                    request, TradeBuyResultVO.class));
            actionReceiptService.record(PLAYER_ID, "trade.buy", "h2-receipt-1", request, response);
        });

        TradeBuyResultVO replay = transaction.execute(status -> actionReceiptService.findReplay(
                PLAYER_ID, "trade.buy", "h2-receipt-1", request, TradeBuyResultVO.class));
        assertNotNull(replay);
        assertEquals("TL-H2-RECEIPT", replay.getListingNo());
        assertEquals(3, replay.getNewBuyerCoins());
        assertThrows(BusinessException.class, () -> transaction.execute(status ->
                actionReceiptService.findReplay(PLAYER_ID, "trade.buy", "h2-receipt-1",
                        Map.of("listingNo", "TL-OTHER"), TradeBuyResultVO.class)));

        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            actionReceiptService.record(PLAYER_ID, "trade.buy", "h2-receipt-rollback",
                    Map.of("listingNo", "TL-ROLLBACK"), response);
            throw new IllegalStateException("simulate a later market write failure");
        }));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_action_receipts WHERE player_id = ?",
                Integer.class, PLAYER_ID));
    }

    @Test
    void npcDialogueChoiceReplaysReceiptWithoutDuplicatingProgressStatsOrLogs() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertEquals("jdbc:h2:mem:combat_persistence", connection.getMetaData().getURL());
        }

        String dialogue = """
                [{"text":"残影留下的讯息","choices":[
                  {"text":"记住这句话","response":"记忆留下了。",
                   "effects":{"statChange":{"heat":2},"addStoryFlag":"h2_ghost_memory"},
                   "leadToIndex":null},
                  {"text":"当作陌生人","response":"你选择不记住。",
                   "effects":{"statChange":{"heat":5},"addStoryFlag":"h2_ghost_memory_rejected"},
                   "leadToIndex":null}
                ]}]
                """;
        jdbcTemplate.update("INSERT INTO npc_ghosts (ghost_key, name, description, dialogue_tree_json, "
                        + "location_keys_json, encounter_weight, is_unique) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "h2_receipt_ghost", "隔离残影", "只用于 H2 回执测试", dialogue, "[]", 0.2, 1);
        long otherPlayerId = PLAYER_ID + 1;
        insertSecondPlayer(otherPlayerId, 10);

        Map<String, Object> first = narrativeService.processNpcGhostEncounter(
                PLAYER_ID, "h2_receipt_ghost", 0, 0, "h2-npc-choice-replay-1");
        Map<String, Object> replay = narrativeService.processNpcGhostEncounter(
                PLAYER_ID, "h2_receipt_ghost", 0, 0, "h2-npc-choice-replay-1");
        Map<String, Object> otherPlayerChoice = narrativeService.processNpcGhostEncounter(
                otherPlayerId, "h2_receipt_ghost", 0, 1, "h2-npc-choice-replay-2");

        assertEquals(objectMapper.writeValueAsString(first), objectMapper.writeValueAsString(replay));
        assertEquals(1, otherPlayerChoice.get("choiceIndex"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_npc_encounters WHERE player_id = ? AND ghost_key = ?",
                Integer.class, PLAYER_ID, "h2_receipt_ghost"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_npc_encounters WHERE player_id = ? AND ghost_key = ?",
                Integer.class, otherPlayerId, "h2_receipt_ghost"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_action_receipts WHERE player_id = ? AND action_type = ? "
                        + "AND idempotency_key = ?",
                Integer.class, PLAYER_ID, "narrative.ghost.choice", "h2-npc-choice-replay-1"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_action_receipts WHERE player_id = ? AND action_type = ? "
                        + "AND idempotency_key = ?",
                Integer.class, otherPlayerId, "narrative.ghost.choice", "h2-npc-choice-replay-2"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_logs WHERE player_id = ? AND type = ?",
                Integer.class, PLAYER_ID, "story"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_logs WHERE player_id = ? AND type = ?",
                Integer.class, otherPlayerId, "story"));
        List<Map<String, Object>> firstPlayerHistory = narrativeService.getPlayerEncounters(PLAYER_ID, 20);
        List<Map<String, Object>> otherPlayerHistory = narrativeService.getPlayerEncounters(otherPlayerId, 20);
        assertEquals(1, firstPlayerHistory.size());
        assertEquals("记住这句话", firstPlayerHistory.get(0).get("choiceMade"));
        assertEquals(1, otherPlayerHistory.size());
        assertEquals("当作陌生人", otherPlayerHistory.get(0).get("choiceMade"));
        JsonNode stats = objectMapper.readTree(playerMapper.selectById(PLAYER_ID).getStatsJson());
        assertEquals(2, stats.path("channelHeat").asInt());
        JsonNode storyFlags = objectMapper.readTree(playerMapper.selectById(PLAYER_ID).getStoryFlagsJson());
        assertTrue(storyFlags.path("h2_ghost_memory").asBoolean());
        assertFalse(storyFlags.path("h2_ghost_memory_rejected").asBoolean());
        JsonNode otherPlayerStats = objectMapper.readTree(playerMapper.selectById(otherPlayerId).getStatsJson());
        assertEquals(5, otherPlayerStats.path("channelHeat").asInt());
        JsonNode otherPlayerFlags = objectMapper.readTree(playerMapper.selectById(otherPlayerId).getStoryFlagsJson());
        assertFalse(otherPlayerFlags.path("h2_ghost_memory").asBoolean());
        assertTrue(otherPlayerFlags.path("h2_ghost_memory_rejected").asBoolean());
    }

    @Test
    void marketPurchasePersistsAllTransfersAndReplaysOneSuccessfulReceipt() {
        long buyerId = PLAYER_ID + 1;
        insertSecondPlayer(buyerId, 100);
        TradeListingVO listing = tradeService.createItemListing(
                PLAYER_ID, "h2_test_herb", 2, 5, "h2-market-list-1");

        TradeBuyResultVO purchase = tradeService.buyListing(buyerId, listing.getListingNo(), "h2-market-buy-1");
        TradeBuyResultVO replay = tradeService.buyListing(buyerId, listing.getListingNo(), "h2-market-buy-1");

        assertEquals(listing.getListingNo(), purchase.getListingNo());
        assertEquals(90, purchase.getNewBuyerCoins());
        assertEquals(90, replay.getNewBuyerCoins());
        assertEquals(20, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(90, playerMapper.selectById(buyerId).getCoins());
        assertEquals(7, inventoryMapper.selectOne(new QueryWrapper<com.huazhenghai.readergame.entity.PlayerInventory>()
                .eq("player_id", PLAYER_ID).eq("item_key", "h2_test_herb")).getQuantity());
        assertEquals(2, inventoryMapper.selectOne(new QueryWrapper<com.huazhenghai.readergame.entity.PlayerInventory>()
                .eq("player_id", buyerId).eq("item_key", "h2_test_herb")).getQuantity());
        assertEquals("sold", jdbcTemplate.queryForObject(
                "SELECT status FROM trade_listings WHERE listing_no = ?", String.class, listing.getListingNo()));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM trade_records", Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_action_receipts WHERE player_id IN (?, ?)",
                Integer.class, PLAYER_ID, buyerId));
        assertEquals(4, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id IN (?, ?)",
                Integer.class, PLAYER_ID, buyerId));
    }

    @Test
    void inventoryUseSellAndBatchSynthesisPersistAndReplayWithoutDuplicateEffects() throws Exception {
        jdbcTemplate.update("UPDATE players SET stats_json = ? WHERE id = ?",
                "{\"level\":1,\"maxHp\":100,\"hp\":50,\"attack\":1000,"
                        + "\"defense\":5,\"speed\":10,\"exp\":0,\"avatarRank\":\"F\"}",
                PLAYER_ID);
        jdbcTemplate.update("UPDATE items SET sell_price = 3 WHERE item_key = ?", "h2_test_herb");
        jdbcTemplate.update("INSERT INTO items (item_key, name, item_type, rarity, effects_json, "
                        + "consume_on_use, sell_price, max_stack, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "h2_test_potion", "測試藥水", "consumable", "common", "{\"heal_hp\":20}", 1, 0, 10, 1);
        jdbcTemplate.update("INSERT INTO synthesis_recipes (recipe_key, name, result_item_key, "
                        + "result_quantity, ingredients_json, cost_coins, enabled) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "h2_test_potion_recipe", "合成測試藥水", "h2_test_potion", 1,
                "{\"h2_test_herb\":1}", 1, 1);

        Map<String, Object> firstSynthesis = inventoryService.synthesizeAll(
                PLAYER_ID, "h2_test_potion_recipe", 3, USER_ID, "h2-inventory-synthesis-1");
        Map<String, Object> replayedSynthesis = inventoryService.synthesizeAll(
                PLAYER_ID, "h2_test_potion_recipe", 3, USER_ID, "h2-inventory-synthesis-1");
        assertEquals(firstSynthesis, replayedSynthesis);
        assertEquals(6, inventoryQuantity(PLAYER_ID, "h2_test_herb"));
        assertEquals(3, inventoryQuantity(PLAYER_ID, "h2_test_potion"));
        assertEquals(7, playerMapper.selectById(PLAYER_ID).getCoins());

        Map<String, Object> firstUse = inventoryService.useItems(
                PLAYER_ID, "h2_test_potion", 4, USER_ID, "h2-inventory-use-batch-1");
        Map<String, Object> replayedUse = inventoryService.useItems(
                PLAYER_ID, "h2_test_potion", 4, USER_ID, "h2-inventory-use-batch-1");
        assertEquals(firstUse, replayedUse);
        assertEquals(3, firstUse.get("used"));
        assertEquals(0, inventoryQuantity(PLAYER_ID, "h2_test_potion"));
        assertEquals(100, objectMapper.readTree(playerMapper.selectById(PLAYER_ID).getStatsJson())
                .get("hp").asInt());

        var firstSale = inventoryService.sellItem(
                PLAYER_ID, "h2_test_herb", 2, USER_ID, "h2-inventory-sell-1");
        var replayedSale = inventoryService.sellItem(
                PLAYER_ID, "h2_test_herb", 2, USER_ID, "h2-inventory-sell-1");
        assertEquals(firstSale.getTotalCoinsGained(), replayedSale.getTotalCoinsGained());
        assertEquals(13, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(4, inventoryQuantity(PLAYER_ID, "h2_test_herb"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_action_receipts WHERE player_id = ? AND action_type = ?",
                Integer.class, PLAYER_ID, "inventory.synthesize.batch"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_action_receipts WHERE player_id = ? AND action_type = ?",
                Integer.class, PLAYER_ID, "inventory.use.batch"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_action_receipts WHERE player_id = ? AND action_type = ?",
                Integer.class, PLAYER_ID, "inventory.sell"));

        assertThrows(BusinessException.class, () -> inventoryService.sellItem(
                PLAYER_ID, "h2_test_herb", 3, USER_ID, "h2-inventory-sell-1"));
        assertEquals(13, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(4, inventoryQuantity(PLAYER_ID, "h2_test_herb"));
    }

    @Test
    void concurrentSameKeyInventorySalesApplyOnlyOnce() throws Exception {
        jdbcTemplate.update("UPDATE items SET sell_price = 2 WHERE item_key = ?", "h2_test_herb");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                return inventoryService.sellItem(
                        PLAYER_ID, "h2_test_herb", 1, USER_ID, "h2-inventory-sell-concurrent")
                        .getTotalCoinsGained();
            }, executor);
            CompletableFuture<Integer> second = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                return inventoryService.sellItem(
                        PLAYER_ID, "h2_test_herb", 1, USER_ID, "h2-inventory-sell-concurrent")
                        .getTotalCoinsGained();
            }, executor);

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(2, first.get(10, TimeUnit.SECONDS));
            assertEquals(2, second.get(10, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertEquals(12, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(8, inventoryQuantity(PLAYER_ID, "h2_test_herb"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_action_receipts WHERE player_id = ? AND action_type = ?",
                Integer.class, PLAYER_ID, "inventory.sell"));
    }

    @Test
    void concurrentPkResolutionReplaysOnePersistedBattleAndResourceLedger() throws Exception {
        long defenderId = PLAYER_ID + 1;
        long challengeId = 92001L;
        String requestKey = "h2-pk-concurrent-resolve";
        insertSecondPlayer(defenderId, 20);
        jdbcTemplate.update("INSERT INTO pk_challenges (id, attacker_id, defender_id, status, mode) "
                        + "VALUES (?, ?, ?, 'pending', 'spar')",
                challengeId, PLAYER_ID, defenderId);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Map<String, Object>> first = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                return pkService.resolveChallenge(challengeId, true, defenderId, USER_ID + 1, requestKey);
            }, executor);
            CompletableFuture<Map<String, Object>> second = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                return pkService.resolveChallenge(challengeId, true, defenderId, USER_ID + 1, requestKey);
            }, executor);

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            Map<String, Object> firstResult = first.get(15, TimeUnit.SECONDS);
            Map<String, Object> secondResult = second.get(15, TimeUnit.SECONDS);

            assertEquals(objectMapper.writeValueAsString(firstResult),
                    objectMapper.writeValueAsString(secondResult));
            assertEquals(Boolean.TRUE, firstResult.get("accepted"));
            assertEquals("accepted", jdbcTemplate.queryForObject(
                    "SELECT status FROM pk_challenges WHERE id = ?", String.class, challengeId));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pk_records WHERE attacker_id = ? AND defender_id = ?",
                    Integer.class, PLAYER_ID, defenderId));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_action_receipts "
                            + "WHERE player_id = ? AND action_type = 'pk.challenge.resolve' AND idempotency_key = ?",
                    Integer.class, defenderId, requestKey));
            assertEquals(3, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_resource_transactions "
                            + "WHERE source_type = 'pk_battle' AND source_id = ?",
                    Integer.class, String.valueOf(challengeId)));
            assertEquals(60, playerMapper.selectById(PLAYER_ID).getCoins());
            assertEquals(0, objectMapper.readTree(playerMapper.selectById(defenderId).getStatsJson())
                    .get("hp").asInt());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void deadDefenderPkRejectionRollsBackChallengeAcceptanceAndAllBattleWrites() throws Exception {
        long defenderId = PLAYER_ID + 1;
        long challengeId = 92003L;
        insertSecondPlayer(defenderId, 20);
        jdbcTemplate.update("UPDATE players SET stats_json = ? WHERE id = ?",
                "{\"level\":1,\"maxHp\":100,\"hp\":0,\"attack\":10,\"defense\":5,"
                        + "\"speed\":10,\"critRate\":0.0,\"critDamage\":1.5,\"exp\":0,\"avatarRank\":\"F\"}",
                defenderId);
        jdbcTemplate.update("INSERT INTO pk_challenges (id, attacker_id, defender_id, status, mode) "
                        + "VALUES (?, ?, ?, 'pending', 'spar')",
                challengeId, PLAYER_ID, defenderId);

        assertThrows(BusinessException.class, () ->
                pkService.resolveChallenge(challengeId, true, defenderId, USER_ID + 1,
                        "h2-pk-dead-defender"));

        assertEquals("pending", jdbcTemplate.queryForObject(
                "SELECT status FROM pk_challenges WHERE id = ?", String.class, challengeId));
        assertEquals(10, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(20, playerMapper.selectById(defenderId).getCoins());
        assertEquals(0, objectMapper.readTree(playerMapper.selectById(PLAYER_ID).getStatsJson())
                .get("exp").asInt());
        assertEquals(0, objectMapper.readTree(playerMapper.selectById(defenderId).getStatsJson())
                .get("hp").asInt());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pk_records", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rankings", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_resource_transactions",
                Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_action_receipts",
                Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_logs", Integer.class));
    }

    @Test
    void pkBattleAndReceiptRollBackTogetherWhenReceiptCannotBeInserted() throws Exception {
        long defenderId = PLAYER_ID + 1;
        long challengeId = 92002L;
        String requestKey = "h2-pk-receipt-failure";
        insertSecondPlayer(defenderId, 20);
        jdbcTemplate.update("INSERT INTO pk_challenges (id, attacker_id, defender_id, status, mode) "
                        + "VALUES (?, ?, ?, 'pending', 'spar')",
                challengeId, PLAYER_ID, defenderId);
        jdbcTemplate.execute("ALTER TABLE player_action_receipts ADD CONSTRAINT "
                + "ck_test_fail_pk_receipt CHECK (idempotency_key <> '" + requestKey + "')");

        try {
            assertThrows(RuntimeException.class, () ->
                    pkService.resolveChallenge(challengeId, true, defenderId, USER_ID + 1, requestKey));
        } finally {
            jdbcTemplate.execute("ALTER TABLE player_action_receipts DROP CONSTRAINT ck_test_fail_pk_receipt");
        }

        assertEquals("pending", jdbcTemplate.queryForObject(
                "SELECT status FROM pk_challenges WHERE id = ?", String.class, challengeId));
        assertEquals(10, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(20, playerMapper.selectById(defenderId).getCoins());
        assertEquals(0, objectMapper.readTree(playerMapper.selectById(PLAYER_ID).getStatsJson())
                .get("exp").asInt());
        assertEquals(100, objectMapper.readTree(playerMapper.selectById(defenderId).getStatsJson())
                .get("hp").asInt());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pk_records", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rankings", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_logs", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_resource_transactions", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_action_receipts", Integer.class));
    }

    @Test
    void concurrentEquipmentEnhancementReplaysOnceAndWritesItsCoinLedger() throws Exception {
        String requestKey = "k".repeat(64);
        jdbcTemplate.update("UPDATE players SET coins = 100 WHERE id = ?", PLAYER_ID);
        jdbcTemplate.update("INSERT INTO player_equipment "
                        + "(player_id, equipment_key, equipped, slot, durability, enhancement_level, listed) "
                        + "VALUES (?, ?, 0, 'weapon', 60, 0, 0)",
                PLAYER_ID, "h2_test_blade");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Map<String, Object>> first = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                return equipmentService.enhance(PLAYER_ID, "h2_test_blade", USER_ID, requestKey);
            }, executor);
            CompletableFuture<Map<String, Object>> second = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                return equipmentService.enhance(PLAYER_ID, "h2_test_blade", USER_ID, requestKey);
            }, executor);

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            Map<String, Object> firstResult = first.get(15, TimeUnit.SECONDS);
            Map<String, Object> secondResult = second.get(15, TimeUnit.SECONDS);

            assertEquals(objectMapper.writeValueAsString(firstResult),
                    objectMapper.writeValueAsString(secondResult));
            assertEquals(1, firstResult.get("enhancementLevel"));
            assertEquals(80, playerMapper.selectById(PLAYER_ID).getCoins());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT enhancement_level FROM player_equipment WHERE player_id = ? AND equipment_key = ?",
                    Integer.class, PLAYER_ID, "h2_test_blade"));
            assertResourceTransactionFrom("equipment_enhance", "h2_test_blade",
                    "currency", "coins", -20, 100, 80);
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_resource_transactions "
                            + "WHERE source_type = 'equipment_enhance' AND source_id = ?",
                    Integer.class, "h2_test_blade"));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_action_receipts "
                            + "WHERE player_id = ? AND action_type = 'equipment.enhance' AND idempotency_key = ?",
                    Integer.class, PLAYER_ID, requestKey));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_logs WHERE player_id = ? AND type = 'equipment_enhance'",
                    Integer.class, PLAYER_ID));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void equipmentEnhancementRollsBackWhenReceiptCannotBeInserted() {
        String requestKey = "h2-equipment-enhance-receipt-failure";
        jdbcTemplate.update("UPDATE players SET coins = 100 WHERE id = ?", PLAYER_ID);
        jdbcTemplate.update("INSERT INTO player_equipment "
                        + "(player_id, equipment_key, equipped, slot, durability, enhancement_level, listed) "
                        + "VALUES (?, ?, 0, 'weapon', 60, 0, 0)",
                PLAYER_ID, "h2_test_blade");
        jdbcTemplate.execute("ALTER TABLE player_action_receipts ADD CONSTRAINT "
                + "ck_test_fail_equipment_receipt CHECK (idempotency_key <> '" + requestKey + "')");

        try {
            assertThrows(RuntimeException.class, () ->
                    equipmentService.enhance(PLAYER_ID, "h2_test_blade", USER_ID, requestKey));
        } finally {
            jdbcTemplate.execute("ALTER TABLE player_action_receipts "
                    + "DROP CONSTRAINT ck_test_fail_equipment_receipt");
        }

        assertEquals(100, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT enhancement_level FROM player_equipment WHERE player_id = ? AND equipment_key = ?",
                Integer.class, PLAYER_ID, "h2_test_blade"));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_logs", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_resource_transactions", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_action_receipts", Integer.class));
    }

    @Test
    void concurrentAttributeAllocationReplaysOnceAndWritesItsPointLedger() throws Exception {
        seedPlayerForAllocation(5);
        String token = performLogin();
        String requestKey = "h2-allocate-points-once";
        Map<String, Object> payload = Map.of(
                "playerId", PLAYER_ID, "atk", 2, "def", 1, "spd", 0, "crit", 0,
                "requestKey", requestKey);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<ResponseEntity<String>> first = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                return performHttp(HttpMethod.POST, "/api/player/allocate-points", token, payload);
            }, executor);
            CompletableFuture<ResponseEntity<String>> second = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                return performHttp(HttpMethod.POST, "/api/player/allocate-points", token, payload);
            }, executor);

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            ResponseEntity<String> firstResponse = first.get(15, TimeUnit.SECONDS);
            ResponseEntity<String> secondResponse = second.get(15, TimeUnit.SECONDS);
            assertEquals(200, firstResponse.getStatusCode().value(), firstResponse.getBody());
            assertEquals(200, secondResponse.getStatusCode().value());
            JsonNode firstData = objectMapper.readTree(firstResponse.getBody()).path("data");
            JsonNode secondData = objectMapper.readTree(secondResponse.getBody()).path("data");
            assertEquals(firstData, secondData);

            JsonNode stats = objectMapper.readTree(playerMapper.selectById(PLAYER_ID).getStatsJson());
            assertEquals(12, stats.path("attack").asInt());
            assertEquals(2, stats.path("allocatedAtk").asInt());
            assertEquals(6, stats.path("defense").asInt());
            assertEquals(1, stats.path("allocatedDef").asInt());
            assertEquals(2, stats.path("freePoints").asInt());
            assertResourceTransactionFrom("attribute_allocation", String.valueOf(PLAYER_ID),
                    "progress", "free_points", -3, 5, 2);
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_resource_transactions "
                            + "WHERE player_id = ? AND source_type = 'attribute_allocation'",
                    Integer.class, PLAYER_ID));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_action_receipts "
                            + "WHERE player_id = ? AND action_type = 'player.allocate-points' AND idempotency_key = ?",
                    Integer.class, PLAYER_ID, requestKey));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_logs WHERE player_id = ? AND type = 'attribute_allocate'",
                    Integer.class, PLAYER_ID));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void attributeAllocationRejectsNegativeAndOverflowingInputsWithoutWrites() throws Exception {
        seedPlayerForAllocation(5);
        String token = performLogin();

        ResponseEntity<String> negative = performHttp(HttpMethod.POST, "/api/player/allocate-points", token,
                Map.of("playerId", PLAYER_ID, "atk", -1, "def", 1, "spd", 0, "crit", 0,
                        "requestKey", "h2-allocate-negative"));
        ResponseEntity<String> oversizedTotal = performHttp(HttpMethod.POST, "/api/player/allocate-points", token,
                Map.of("playerId", PLAYER_ID, "atk", Integer.MAX_VALUE, "def", 1, "spd", 0, "crit", 0,
                        "requestKey", "h2-allocate-total-overflow"));

        assertEquals(400, negative.getStatusCode().value());
        assertTrue(negative.getBody().contains("分配属性点不能为负数"));
        assertEquals(400, oversizedTotal.getStatusCode().value());
        assertTrue(oversizedTotal.getBody().contains("自由属性点不足"));
        JsonNode stats = objectMapper.readTree(playerMapper.selectById(PLAYER_ID).getStatsJson());
        assertEquals(10, stats.path("attack").asInt());
        assertEquals(5, stats.path("freePoints").asInt());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ?",
                Integer.class, PLAYER_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_action_receipts WHERE player_id = ?",
                Integer.class, PLAYER_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_logs WHERE player_id = ?",
                Integer.class, PLAYER_ID));
    }

    @Test
    void attributeAllocationRollsBackWhenItsReceiptCannotBeInserted() throws Exception {
        seedPlayerForAllocation(5);
        String token = performLogin();
        String requestKey = "h2-allocate-receipt-failure";
        jdbcTemplate.execute("ALTER TABLE player_action_receipts ADD CONSTRAINT "
                + "ck_test_fail_allocate_receipt CHECK (idempotency_key <> '" + requestKey + "')");

        ResponseEntity<String> response;
        try {
            response = performHttp(HttpMethod.POST, "/api/player/allocate-points", token,
                    Map.of("playerId", PLAYER_ID, "atk", 2, "def", 1, "spd", 0, "crit", 0,
                            "requestKey", requestKey));
        } finally {
            jdbcTemplate.execute("ALTER TABLE player_action_receipts "
                    + "DROP CONSTRAINT ck_test_fail_allocate_receipt");
        }

        assertTrue(response.getStatusCode().is5xxServerError());
        JsonNode stats = objectMapper.readTree(playerMapper.selectById(PLAYER_ID).getStatsJson());
        assertEquals(10, stats.path("attack").asInt());
        assertEquals(5, stats.path("freePoints").asInt());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ?",
                Integer.class, PLAYER_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_action_receipts WHERE player_id = ?",
                Integer.class, PLAYER_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_logs WHERE player_id = ?",
                Integer.class, PLAYER_ID));
    }

    @Test
    void concurrentAttributeResetReplaysOnceAndWritesItsCoinLedger() throws Exception {
        seedAllocatedPlayerForReset();
        String token = performLogin();
        String requestKey = "r".repeat(64);
        Map<String, Object> payload = Map.of("playerId", PLAYER_ID, "requestKey", requestKey);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<ResponseEntity<String>> first = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                return performHttp(HttpMethod.POST, "/api/player/reset-allocation", token, payload);
            }, executor);
            CompletableFuture<ResponseEntity<String>> second = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                return performHttp(HttpMethod.POST, "/api/player/reset-allocation", token, payload);
            }, executor);

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            ResponseEntity<String> firstResponse = first.get(15, TimeUnit.SECONDS);
            ResponseEntity<String> secondResponse = second.get(15, TimeUnit.SECONDS);
            assertEquals(200, firstResponse.getStatusCode().value());
            assertEquals(200, secondResponse.getStatusCode().value());
            JsonNode firstData = objectMapper.readTree(firstResponse.getBody()).path("data");
            JsonNode secondData = objectMapper.readTree(secondResponse.getBody()).path("data");
            assertEquals(firstData.toString(), secondData.toString());

            assertEquals(50, playerMapper.selectById(PLAYER_ID).getCoins());
            JsonNode stats = objectMapper.readTree(playerMapper.selectById(PLAYER_ID).getStatsJson());
            assertEquals(1000, stats.get("attack").asInt());
            assertEquals(5, stats.get("defense").asInt());
            assertEquals(0, stats.get("allocatedAtk").asInt());
            assertEquals(0, stats.get("allocatedDef").asInt());
            assertEquals(2, stats.get("freePoints").asInt());
            assertResourceTransactionFrom("attribute_respec", String.valueOf(PLAYER_ID),
                    "currency", "coins", -50, 100, 50);
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_resource_transactions WHERE source_type = 'attribute_respec'",
                    Integer.class));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_action_receipts "
                            + "WHERE player_id = ? AND action_type = 'player.reset-allocation' AND idempotency_key = ?",
                    Integer.class, PLAYER_ID, requestKey));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_logs WHERE player_id = ? AND type = 'attribute_reset'",
                    Integer.class, PLAYER_ID));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void attributeResetRollsBackWhenReceiptCannotBeInserted() throws Exception {
        seedAllocatedPlayerForReset();
        String token = performLogin();
        String requestKey = "h2-respec-receipt-failure";
        jdbcTemplate.execute("ALTER TABLE player_action_receipts ADD CONSTRAINT "
                + "ck_test_fail_respec_receipt CHECK (idempotency_key <> '" + requestKey + "')");

        ResponseEntity<String> response;
        try {
            response = performHttp(HttpMethod.POST, "/api/player/reset-allocation", token,
                    Map.of("playerId", PLAYER_ID, "requestKey", requestKey));
        } finally {
            jdbcTemplate.execute("ALTER TABLE player_action_receipts "
                    + "DROP CONSTRAINT ck_test_fail_respec_receipt");
        }

        assertTrue(response.getStatusCode().is5xxServerError());
        Player player = playerMapper.selectById(PLAYER_ID);
        assertEquals(100, player.getCoins());
        JsonNode stats = objectMapper.readTree(player.getStatsJson());
        assertEquals(1001, stats.get("attack").asInt());
        assertEquals(6, stats.get("defense").asInt());
        assertEquals(1, stats.get("allocatedAtk").asInt());
        assertEquals(1, stats.get("allocatedDef").asInt());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_logs", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_resource_transactions", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_action_receipts", Integer.class));
    }

    @Test
    void attributeResetRejectsFeeOverflowWithoutChangingPlayerOrWritingAuditRows() throws Exception {
        seedAllocatedPlayerForReset();
        String statsJson = "{\"level\":1,\"maxHp\":100,\"hp\":100,\"attack\":120000010,"
                + "\"defense\":6,\"speed\":10,\"critRate\":0.0,\"critDamage\":1.5,"
                + "\"exp\":0,\"avatarRank\":\"F\",\"constellation\":\"demon_judge_of_fire\",\"allocatedAtk\":120000000,"
                + "\"allocatedDef\":0,\"allocatedSpd\":0,\"allocatedCrit\":0,\"freePoints\":0}";
        jdbcTemplate.update("UPDATE players SET stats_json = ? WHERE id = ?", statsJson, PLAYER_ID);
        String token = performLogin();

        ResponseEntity<String> response = performHttp(HttpMethod.POST, "/api/player/reset-allocation", token,
                Map.of("playerId", PLAYER_ID, "requestKey", "h2-respec-cost-overflow"));

        assertTrue(response.getStatusCode().is4xxClientError());
        assertTrue(response.getBody().contains("费用超出可保存范围"));
        Player player = playerMapper.selectById(PLAYER_ID);
        assertEquals(100, player.getCoins());
        assertEquals(statsJson, player.getStatsJson());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_logs", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_resource_transactions", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_action_receipts", Integer.class));
    }

    @Test
    void attributeResetRejectsFreePointOverflowWithoutChangingPlayerOrWritingAuditRows() throws Exception {
        seedAllocatedPlayerForReset();
        String statsJson = "{\"level\":1,\"maxHp\":100,\"hp\":100,\"attack\":11,"
                + "\"defense\":6,\"speed\":10,\"critRate\":0.0,\"critDamage\":1.5,"
                + "\"exp\":0,\"avatarRank\":\"F\",\"constellation\":\"demon_judge_of_fire\",\"allocatedAtk\":1,"
                + "\"allocatedDef\":0,\"allocatedSpd\":0,\"allocatedCrit\":0,\"freePoints\":2147483647}";
        jdbcTemplate.update("UPDATE players SET stats_json = ? WHERE id = ?", statsJson, PLAYER_ID);
        String token = performLogin();

        ResponseEntity<String> response = performHttp(HttpMethod.POST, "/api/player/reset-allocation", token,
                Map.of("playerId", PLAYER_ID, "requestKey", "h2-respec-free-points-overflow"));

        assertTrue(response.getStatusCode().is4xxClientError());
        assertTrue(response.getBody().contains("自由属性点"));
        Player player = playerMapper.selectById(PLAYER_ID);
        assertEquals(100, player.getCoins());
        assertEquals(statsJson, player.getStatsJson());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_logs", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_resource_transactions", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_action_receipts", Integer.class));
    }

    @Test
    void concurrentPaidSelfReviveReplaysOnceAndWritesItsCoinLedger() throws Exception {
        seedPlayerForRevive(500, true);
        String token = performLogin();
        String requestKey = "v".repeat(64);
        Map<String, Object> payload = Map.of(
                "playerId", PLAYER_ID, "method", "coins", "requestKey", requestKey);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<ResponseEntity<String>> first = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                return performHttp(HttpMethod.POST, "/api/player/revive", token, payload);
            }, executor);
            CompletableFuture<ResponseEntity<String>> second = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                return performHttp(HttpMethod.POST, "/api/player/revive", token, payload);
            }, executor);

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            ResponseEntity<String> firstResponse = first.get(15, TimeUnit.SECONDS);
            ResponseEntity<String> secondResponse = second.get(15, TimeUnit.SECONDS);
            assertEquals(200, firstResponse.getStatusCode().value());
            assertEquals(200, secondResponse.getStatusCode().value());
            assertEquals(objectMapper.readTree(firstResponse.getBody()).path("data").toString(),
                    objectMapper.readTree(secondResponse.getBody()).path("data").toString());

            Player player = playerMapper.selectById(PLAYER_ID);
            assertEquals(300, player.getCoins());
            JsonNode stats = objectMapper.readTree(player.getStatsJson());
            assertFalse(stats.get("isDead").asBoolean());
            assertEquals(60, stats.get("hp").asInt());
            assertResourceTransactionFrom("self_revive", String.valueOf(PLAYER_ID),
                    "currency", "coins", -200, 500, 300);
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_resource_transactions WHERE source_type = 'self_revive'",
                    Integer.class));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_action_receipts "
                            + "WHERE player_id = ? AND action_type = 'player.revive' AND idempotency_key = ?",
                    Integer.class, PLAYER_ID, requestKey));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_logs WHERE player_id = ? AND type = 'player_revive'",
                    Integer.class, PLAYER_ID));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void paidSelfReviveRollsBackWhenReceiptCannotBeInserted() throws Exception {
        seedPlayerForRevive(500, true);
        String token = performLogin();
        String requestKey = "h2-revive-receipt-failure";
        jdbcTemplate.execute("ALTER TABLE player_action_receipts ADD CONSTRAINT "
                + "ck_test_fail_revive_receipt CHECK (idempotency_key <> '" + requestKey + "')");

        ResponseEntity<String> response;
        try {
            response = performHttp(HttpMethod.POST, "/api/player/revive", token,
                    Map.of("playerId", PLAYER_ID, "method", "coins", "requestKey", requestKey));
        } finally {
            jdbcTemplate.execute("ALTER TABLE player_action_receipts "
                    + "DROP CONSTRAINT ck_test_fail_revive_receipt");
        }

        assertTrue(response.getStatusCode().is5xxServerError());
        Player player = playerMapper.selectById(PLAYER_ID);
        assertEquals(500, player.getCoins());
        assertTrue(objectMapper.readTree(player.getStatsJson()).get("isDead").asBoolean());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_logs", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_action_receipts", Integer.class));
    }

    @Test
    void selfReviveRejectsLivingOrUnderfundedPlayerWithoutWritingRecords() throws Exception {
        seedPlayerForRevive(99, false);
        String token = performLogin();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("playerId", PLAYER_ID);
        payload.put("method", "coins");
        payload.put("requestKey", "h2-revive-reject-living");
        ResponseEntity<String> aliveResponse = performHttp(
                HttpMethod.POST, "/api/player/revive", token, payload);
        assertEquals(400, aliveResponse.getStatusCode().value());

        seedPlayerForRevive(99, true);
        payload.put("requestKey", "h2-revive-reject-poor");
        ResponseEntity<String> poorResponse = performHttp(
                HttpMethod.POST, "/api/player/revive", token, payload);
        assertEquals(400, poorResponse.getStatusCode().value());
        assertEquals(99, playerMapper.selectById(PLAYER_ID).getCoins());
        assertTrue(objectMapper.readTree(playerMapper.selectById(PLAYER_ID).getStatsJson())
                .get("isDead").asBoolean());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_logs", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_action_receipts", Integer.class));
    }

    @Test
    void titleSelfReviveKeepsItsNonCurrencyBehavior() throws Exception {
        seedPlayerForRevive(0, true);
        String token = performLogin();
        ResponseEntity<String> response = performHttp(HttpMethod.POST, "/api/player/revive", token,
                Map.of("playerId", PLAYER_ID, "method", "title", "requestKey", "h2-title-revive"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(0, playerMapper.selectById(PLAYER_ID).getCoins());
        assertFalse(objectMapper.readTree(playerMapper.selectById(PLAYER_ID).getStatsJson())
                .get("isDead").asBoolean());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_logs WHERE type = 'player_revive'", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_action_receipts WHERE action_type = 'player.revive'",
                Integer.class));
    }

    @Test
    void concurrentPeerReviveLocksBothPlayersAndChargesTheTargetConstellationPriceOnce() throws Exception {
        seedPeerRevivePlayers(500, true);
        String token = performLogin();
        String requestKey = "p".repeat(64);
        Map<String, Object> payload = Map.of(
                "reviverId", PLAYER_ID, "targetId", PLAYER_ID + 1,
                "method", "coins", "requestKey", requestKey);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<ResponseEntity<String>> first = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                return performHttp(HttpMethod.POST, "/api/player/peer-revive", token, payload);
            }, executor);
            CompletableFuture<ResponseEntity<String>> second = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                return performHttp(HttpMethod.POST, "/api/player/peer-revive", token, payload);
            }, executor);

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            ResponseEntity<String> firstResponse = first.get(15, TimeUnit.SECONDS);
            ResponseEntity<String> secondResponse = second.get(15, TimeUnit.SECONDS);
            assertEquals(200, firstResponse.getStatusCode().value());
            assertEquals(200, secondResponse.getStatusCode().value());
            assertEquals(objectMapper.readTree(firstResponse.getBody()).path("data").toString(),
                    objectMapper.readTree(secondResponse.getBody()).path("data").toString());

            assertEquals(400, playerMapper.selectById(PLAYER_ID).getCoins());
            assertEquals(50, playerMapper.selectById(PLAYER_ID + 1).getCoins());
            JsonNode targetStats = objectMapper.readTree(playerMapper.selectById(PLAYER_ID + 1).getStatsJson());
            assertFalse(targetStats.get("isDead").asBoolean());
            assertEquals(60, targetStats.get("hp").asInt());
            assertResourceTransactionFrom("peer_revive", String.valueOf(PLAYER_ID + 1),
                    "currency", "coins", -100, 500, 400);
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_resource_transactions WHERE source_type = 'peer_revive'",
                    Integer.class));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_action_receipts "
                            + "WHERE player_id = ? AND action_type = 'player.peer-revive' AND idempotency_key = ?",
                    Integer.class, PLAYER_ID, requestKey));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_logs WHERE player_id = ? AND type = 'peer_revive_sent'",
                    Integer.class, PLAYER_ID));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_logs WHERE player_id = ? AND type = 'peer_revived'",
                    Integer.class, PLAYER_ID + 1));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void peerReviveChargesFullPriceForNonQueenConstellation() throws Exception {
        seedPeerRevivePlayers(500, true, "demon_judge_of_fire");
        String token = performLogin();
        ResponseEntity<String> response = performHttp(HttpMethod.POST, "/api/player/peer-revive", token,
                Map.of("reviverId", PLAYER_ID, "targetId", PLAYER_ID + 1,
                        "method", "coins", "requestKey", "h2-peer-revive-full-price"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(300, playerMapper.selectById(PLAYER_ID).getCoins());
        assertResourceTransactionFrom("peer_revive", String.valueOf(PLAYER_ID + 1),
                "currency", "coins", -200, 500, 300);
    }

    @Test
    void peerReviveRollsBackBothPlayersWhenReceiptCannotBeInserted() throws Exception {
        seedPeerRevivePlayers(500, true);
        String token = performLogin();
        String requestKey = "h2-peer-revive-receipt-failure";
        jdbcTemplate.execute("ALTER TABLE player_action_receipts ADD CONSTRAINT "
                + "ck_test_fail_peer_revive_receipt CHECK (idempotency_key <> '" + requestKey + "')");

        ResponseEntity<String> response;
        try {
            response = performHttp(HttpMethod.POST, "/api/player/peer-revive", token,
                    Map.of("reviverId", PLAYER_ID, "targetId", PLAYER_ID + 1,
                            "method", "coins", "requestKey", requestKey));
        } finally {
            jdbcTemplate.execute("ALTER TABLE player_action_receipts "
                    + "DROP CONSTRAINT ck_test_fail_peer_revive_receipt");
        }

        assertTrue(response.getStatusCode().is5xxServerError());
        assertEquals(500, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(50, playerMapper.selectById(PLAYER_ID + 1).getCoins());
        assertTrue(objectMapper.readTree(playerMapper.selectById(PLAYER_ID + 1).getStatsJson())
                .get("isDead").asBoolean());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_logs", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_action_receipts", Integer.class));
    }

    @Test
    void peerReviveRejectsAliveTargetsInsufficientFundsAndForeignRevivers() throws Exception {
        seedPeerRevivePlayers(99, false);
        String token = performLogin();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reviverId", PLAYER_ID);
        payload.put("targetId", PLAYER_ID + 1);
        payload.put("method", "coins");
        payload.put("requestKey", "h2-peer-revive-alive");
        ResponseEntity<String> aliveResponse = performHttp(
                HttpMethod.POST, "/api/player/peer-revive", token, payload);
        assertEquals(400, aliveResponse.getStatusCode().value());

        seedPeerRevivePlayers(99, true);
        payload.put("requestKey", "h2-peer-revive-poor");
        ResponseEntity<String> poorResponse = performHttp(
                HttpMethod.POST, "/api/player/peer-revive", token, payload);
        assertEquals(400, poorResponse.getStatusCode().value());

        ResponseEntity<String> foreignReviverResponse = performHttp(HttpMethod.POST,
                "/api/player/peer-revive", token,
                Map.of("reviverId", PLAYER_ID + 1, "targetId", PLAYER_ID,
                        "method", "coins", "requestKey", "h2-peer-revive-foreign"));
        assertEquals(403, foreignReviverResponse.getStatusCode().value());
        assertEquals(99, playerMapper.selectById(PLAYER_ID).getCoins());
        assertTrue(objectMapper.readTree(playerMapper.selectById(PLAYER_ID + 1).getStatsJson())
                .get("isDead").asBoolean());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_logs", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_action_receipts", Integer.class));
    }

    @Test
    void inventoryMutationAndReceiptRollBackTogetherWhenReceiptCannotBeWritten() {
        jdbcTemplate.update("UPDATE items SET sell_price = 2 WHERE item_key = ?", "h2_test_herb");
        jdbcTemplate.execute("ALTER TABLE player_action_receipts ADD CONSTRAINT "
                + "ck_test_reject_inventory_sell CHECK (action_type <> 'inventory.sell')");

        try {
            assertThrows(RuntimeException.class, () -> inventoryService.sellItem(
                    PLAYER_ID, "h2_test_herb", 1, USER_ID, "h2-inventory-sell-rollback"));
        } finally {
            jdbcTemplate.execute("ALTER TABLE player_action_receipts "
                    + "DROP CONSTRAINT ck_test_reject_inventory_sell");
        }

        assertEquals(10, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(9, inventoryQuantity(PLAYER_ID, "h2_test_herb"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_logs WHERE type = 'item_sell'", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_action_receipts WHERE action_type = 'inventory.sell'",
                Integer.class));
    }

    @Test
    void worldBossRewardClaimWritesLedgerAndReplaysTheOriginalResponse() throws Exception {
        String bossNo = "h2-world-boss-claim";
        String requestKey = "h2-world-boss-claim-key";
        jdbcTemplate.update("UPDATE players SET stats_json = ? WHERE id = ?",
                "{\"level\":1,\"maxHp\":100,\"hp\":100,\"attack\":10,"
                        + "\"defense\":5,\"speed\":10,\"channelHeat\":1}", PLAYER_ID);
        jdbcTemplate.update("INSERT INTO items (item_key, name, item_type, rarity, max_stack, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "h2_test_reward", "世界首领奖励", "material", "rare", 10, 1);
        jdbcTemplate.update("INSERT INTO world_bosses (boss_key, boss_no, name, status, rewards_json, "
                        + "rank_rewards_json) VALUES (?, ?, ?, ?, ?, ?)",
                "h2_boss", bossNo, "隔离世界首领", "settled",
                "{\"participation\":{\"minDamage\":1,\"coins\":25,\"storyFragments\":4,"
                        + "\"channelHeat\":5,\"items\":[{\"itemKey\":\"h2_test_reward\",\"quantity\":2}]}}",
                "{\"top1\":{\"coins\":10,\"storyFragments\":2,\"channelHeat\":3}}");
        jdbcTemplate.update("INSERT INTO world_boss_participation "
                        + "(boss_no, player_id, damage, reward_claimed, rank_no) VALUES (?, ?, ?, ?, ?)",
                bossNo, PLAYER_ID, 100, 0, 1);
        when(playerEffectService.applyChannelHeatGainRate(PLAYER_ID, 8)).thenReturn(8);

        WorldBossRewardVO first = worldBossService.claimReward(
                PLAYER_ID, bossNo, USER_ID, requestKey);
        WorldBossRewardVO replay = worldBossService.claimReward(
                PLAYER_ID, bossNo, USER_ID, requestKey);

        assertEquals(objectMapper.writeValueAsString(first), objectMapper.writeValueAsString(replay));
        assertEquals(45, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(6, playerMapper.selectById(PLAYER_ID).getStoryFragments());
        assertEquals(9, objectMapper.readTree(playerMapper.selectById(PLAYER_ID).getStatsJson())
                .get("channelHeat").asInt());
        assertEquals(2, inventoryQuantity(PLAYER_ID, "h2_test_reward"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT reward_claimed FROM world_boss_participation WHERE boss_no = ? AND player_id = ?",
                Integer.class, bossNo, PLAYER_ID));
        assertEquals(4, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE transaction_group_id = ?",
                Integer.class, "world-boss:" + bossNo + ":" + PLAYER_ID));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_action_receipts WHERE player_id = ? AND action_type = ?",
                Integer.class, PLAYER_ID, "world_boss.claim"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_logs WHERE type = 'battle' AND message LIKE '%隔离世界首领%'",
                Integer.class));

        assertThrows(BusinessException.class, () -> worldBossService.claimReward(
                PLAYER_ID, "different-boss", USER_ID, requestKey));
    }

    @Test
    void worldBossRewardClaimRollsBackBalancesItemsAndReceiptWhenRewardCannotFit() {
        String bossNo = "h2-world-boss-no-stack";
        jdbcTemplate.update("INSERT INTO items (item_key, name, item_type, rarity, max_stack, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "h2_test_reward", "世界首领奖励", "material", "rare", 1, 1);
        jdbcTemplate.update("INSERT INTO world_bosses (boss_key, boss_no, name, status, rewards_json, "
                        + "rank_rewards_json) VALUES (?, ?, ?, ?, ?, ?)",
                "h2_boss", bossNo, "隔离世界首领", "settled",
                "{\"participation\":{\"minDamage\":1,\"coins\":20,\"storyFragments\":3,"
                        + "\"items\":[{\"itemKey\":\"h2_test_reward\",\"quantity\":2}]}}", "{}");
        jdbcTemplate.update("INSERT INTO world_boss_participation "
                        + "(boss_no, player_id, damage, reward_claimed, rank_no) VALUES (?, ?, ?, ?, ?)",
                bossNo, PLAYER_ID, 100, 0, 1);

        assertThrows(BusinessException.class, () -> worldBossService.claimReward(
                PLAYER_ID, bossNo, USER_ID, "h2-world-boss-failed-claim"));

        assertEquals(10, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(0, playerMapper.selectById(PLAYER_ID).getStoryFragments());
        assertEquals(0, inventoryQuantity(PLAYER_ID, "h2_test_reward"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT reward_claimed FROM world_boss_participation WHERE boss_no = ? AND player_id = ?",
                Integer.class, bossNo, PLAYER_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_action_receipts WHERE action_type = 'world_boss.claim'",
                Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_logs WHERE type = 'battle' AND message LIKE '%隔离世界首领%'",
                Integer.class));
    }

    @Test
    void failedLateMarketWriteRollsBackCurrencyAssetsListingAndReceiptTogether() {
        long buyerId = PLAYER_ID + 1;
        insertSecondPlayer(buyerId, 100);
        TradeListingVO listing = tradeService.createItemListing(
                PLAYER_ID, "h2_test_herb", 2, 5, "h2-market-list-rollback");
        jdbcTemplate.execute("ALTER TABLE trade_records ADD CONSTRAINT ck_test_reject_trade_total CHECK (total_price < 0)");

        try {
            assertThrows(RuntimeException.class,
                    () -> tradeService.buyListing(buyerId, listing.getListingNo(), "h2-market-buy-rollback"));
        } finally {
            jdbcTemplate.execute("ALTER TABLE trade_records DROP CONSTRAINT ck_test_reject_trade_total");
        }

        assertEquals(10, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(100, playerMapper.selectById(buyerId).getCoins());
        assertEquals(7, inventoryMapper.selectOne(new QueryWrapper<com.huazhenghai.readergame.entity.PlayerInventory>()
                .eq("player_id", PLAYER_ID).eq("item_key", "h2_test_herb")).getQuantity());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_inventory WHERE player_id = ? AND item_key = ?",
                Integer.class, buyerId, "h2_test_herb"));
        assertEquals("active", jdbcTemplate.queryForObject(
                "SELECT status FROM trade_listings WHERE listing_no = ?", String.class, listing.getListingNo()));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM trade_records", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_action_receipts", Integer.class));
    }

    @Test
    void concurrentReplaysOfTheSameMarketPurchaseSettleExactlyOnce() throws Exception {
        long buyerId = PLAYER_ID + 1;
        insertSecondPlayer(buyerId, 100);
        TradeListingVO listing = tradeService.createItemListing(
                PLAYER_ID, "h2_test_herb", 2, 5, "h2-market-list-concurrent");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<TradeBuyResultVO> first = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                return tradeService.buyListing(buyerId, listing.getListingNo(), "h2-market-buy-concurrent");
            }, executor);
            CompletableFuture<TradeBuyResultVO> second = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                return tradeService.buyListing(buyerId, listing.getListingNo(), "h2-market-buy-concurrent");
            }, executor);

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(listing.getListingNo(), first.get(10, TimeUnit.SECONDS).getListingNo());
            assertEquals(listing.getListingNo(), second.get(10, TimeUnit.SECONDS).getListingNo());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertEquals(20, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(90, playerMapper.selectById(buyerId).getCoins());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM trade_records", Integer.class));
        assertEquals(4, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id IN (?, ?)",
                Integer.class, PLAYER_ID, buyerId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_action_receipts WHERE player_id = ? AND action_type = ?",
                Integer.class, buyerId, "trade.buy"));
    }

    @Test
    void concurrentDifferentBuyersCannotBothPurchaseOneMarketListing() throws Exception {
        long firstBuyerId = PLAYER_ID + 1;
        long secondBuyerId = PLAYER_ID + 2;
        insertSecondPlayer(firstBuyerId, 100);
        insertSecondPlayer(secondBuyerId, 100);
        TradeListingVO listing = tradeService.createItemListing(
                PLAYER_ID, "h2_test_herb", 2, 5, "h2-market-list-race");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                try {
                    tradeService.buyListing(firstBuyerId, listing.getListingNo(), "h2-market-buy-race-a");
                    return true;
                } catch (BusinessException expectedUnavailableListing) {
                    return false;
                }
            }, executor);
            CompletableFuture<Boolean> second = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                try {
                    tradeService.buyListing(secondBuyerId, listing.getListingNo(), "h2-market-buy-race-b");
                    return true;
                } catch (BusinessException expectedUnavailableListing) {
                    return false;
                }
            }, executor);

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            boolean firstWon = first.get(10, TimeUnit.SECONDS);
            boolean secondWon = second.get(10, TimeUnit.SECONDS);
            assertTrue(firstWon ^ secondWon, "exactly one different-key buyer should win the listing");
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertEquals("sold", jdbcTemplate.queryForObject(
                "SELECT status FROM trade_listings WHERE listing_no = ?", String.class, listing.getListingNo()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM trade_records WHERE listing_no = ?", Integer.class, listing.getListingNo()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_action_receipts WHERE action_type = 'trade.buy'",
                Integer.class));
        // The listing escrow has its own transaction group; the sale group contains buyer/seller coins and buyer item.
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE transaction_group_id = ?",
                Integer.class, "market:" + listing.getListingNo()));
        assertEquals(20, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(190, playerMapper.selectById(firstBuyerId).getCoins()
                + playerMapper.selectById(secondBuyerId).getCoins());
        assertEquals(2, inventoryQuantity(firstBuyerId, "h2_test_herb")
                + inventoryQuantity(secondBuyerId, "h2_test_herb"));
    }

    @Test
    void concurrentAiBroadcastActionsPersistIndependentObjectiveProgressInIsolatedH2() throws Exception {
        String eventKey = "ai_h2_objective_lock";
        List<Map<String, Object>> objectives = List.of(
                Map.of("type", "explore_location", "target", 1000, "label", "探索", "score_per_unit", 1, "current", 0),
                Map.of("type", "trigger_story", "target", 10, "label", "触发故事", "score_per_unit", 3, "current", 0),
                Map.of("type", "kill_monster", "target", 10, "label", "击败怪物", "score_per_unit", 2, "current", 0),
                Map.of("type", "defeat_boss", "target", 1, "label", "击破首领", "score_per_unit", 10, "current", 0),
                Map.of("type", "win_pk", "target", 5, "label", "PK获胜", "score_per_unit", 5, "current", 0));
        String metadataJson = objectMapper.writeValueAsString(Map.of("objectives", objectives));
        jdbcTemplate.update("INSERT INTO broadcast_events (event_key, title, type, status, target_value, "
                        + "current_value, start_at, end_at, metadata_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                eventKey, "并发目标测试", "mixed", "active", 1026, 0,
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now().plusHours(1), metadataJson);

        BroadcastServiceImpl aiBroadcastService = new BroadcastServiceImpl(
                broadcastEventMapper, broadcastContributionMapper, mock(WorldlineService.class),
                mock(ChatService.class), playerMapper, mock(InventoryService.class), objectMapper,
                mock(WebSocketSessionManager.class), mock(PlayerEffectService.class), resourceTransactionService);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(6);
        CountDownLatch ready = new CountDownLatch(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> concurrentWrites = new java.util.ArrayList<>();
        try {
            for (int worker = 0; worker < 6; worker++) {
                concurrentWrites.add(executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    int writes = 0;
                    for (int i = 0; i < 12; i++) {
                        Integer advanced = transactions.execute(status ->
                                aiBroadcastService.contributeToActiveAiEvents(PLAYER_ID, "explore_location", 1));
                        writes += advanced == null ? 0 : advanced;
                    }
                    return writes;
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int totalWrites = 0;
            for (Future<Integer> write : concurrentWrites) totalWrites += write.get(20, TimeUnit.SECONDS);
            assertEquals(72, totalWrites);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        Integer storyProgress = transactions.execute(status ->
                aiBroadcastService.contributeToActiveAiEvents(PLAYER_ID, "trigger_story", 3));
        Integer monsterProgress = transactions.execute(status ->
                aiBroadcastService.contributeToActiveAiEvents(PLAYER_ID, "kill_monster", 5));
        Integer bossProgress = transactions.execute(status ->
                aiBroadcastService.contributeToActiveAiEvents(PLAYER_ID, "defeat_boss", 1));
        Integer pkProgress = transactions.execute(status ->
                aiBroadcastService.contributeToActiveAiEvents(PLAYER_ID, "win_pk", 2));
        assertEquals(Integer.valueOf(1), storyProgress);
        assertEquals(Integer.valueOf(1), monsterProgress);
        assertEquals(Integer.valueOf(1), bossProgress);
        assertEquals(Integer.valueOf(1), pkProgress);

        BroadcastEvent saved = broadcastEventMapper.selectOne(new QueryWrapper<BroadcastEvent>()
                .eq("event_key", eventKey));
        assertEquals(83, saved.getCurrentValue().intValue());
        Map<String, Integer> progressByType = new java.util.HashMap<>();
        for (JsonNode objective : objectMapper.readTree(saved.getMetadataJson()).path("objectives")) {
            progressByType.put(objective.path("type").asText(), objective.path("current").asInt());
        }
        assertEquals(Map.of("explore_location", 72, "trigger_story", 3, "kill_monster", 5,
                "defeat_boss", 1, "win_pk", 2), progressByType);
        BroadcastContribution contribution = broadcastContributionMapper.selectOne(
                new QueryWrapper<com.huazhenghai.readergame.entity.BroadcastContribution>()
                        .eq("event_key", eventKey).eq("player_id", PLAYER_ID));
        assertEquals(111, contribution.getContributionValue());
        assertEquals("active", saved.getStatus());
    }

    @Test
    void broadcastLeaderboardPositionUsesPersistedH2ContributionsAndKeepsTiesTogether() {
        String eventKey = "ai_h2_rank_position";
        jdbcTemplate.update("INSERT INTO broadcast_events (event_key, title, type, status, target_value) "
                        + "VALUES (?, ?, ?, ?, ?)",
                eventKey, "本场名次测试", "mixed", "active", 100);
        jdbcTemplate.update("INSERT INTO broadcast_contributions (event_key, player_id, contribution_value) "
                        + "VALUES (?, ?, ?)", eventKey, PLAYER_ID, 8);
        jdbcTemplate.update("INSERT INTO broadcast_contributions (event_key, player_id, contribution_value) "
                        + "VALUES (?, ?, ?)", eventKey, PLAYER_ID + 1, 12);
        jdbcTemplate.update("INSERT INTO broadcast_contributions (event_key, player_id, contribution_value) "
                        + "VALUES (?, ?, ?)", eventKey, PLAYER_ID + 2, 8);

        BroadcastServiceImpl leaderboardService = new BroadcastServiceImpl(
                broadcastEventMapper, broadcastContributionMapper, mock(WorldlineService.class),
                mock(ChatService.class), playerMapper, mock(InventoryService.class), objectMapper,
                mock(WebSocketSessionManager.class), mock(PlayerEffectService.class), resourceTransactionService);

        Map<String, Object> position = leaderboardService.getPlayerLeaderboardPosition(eventKey, PLAYER_ID);

        assertEquals(true, position.get("participated"));
        assertEquals(3L, position.get("participantCount"));
        assertEquals(2L, position.get("rank"), "同贡献者共用比赛名次");
        assertEquals(8L, position.get("contribution"));
        assertEquals(5L, position.get("pointsToOvertakeNext"), "至少超过上一名 1 点才能升位");
    }

    @Test
    void battlePlaceholderRewardCleanupMigrationIsRepeatableAndLimitedToListedBattleEvents() throws Exception {
        var migrationStream = getClass().getResourceAsStream(
                "/db/migrations/20260929_add_exploration_encounter_monsters.sql");
        assertNotNull(migrationStream, "奖励清理迁移必须在测试 classpath 中");
        String migration;
        try (migrationStream) {
            migration = new String(migrationStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        int rewardSet = migration.indexOf("SET `rewards_json` = JSON_OBJECT()");
        assertTrue(rewardSet >= 0, "迁移应包含占位事件奖励清理语句");
        int cleanupStart = migration.lastIndexOf("UPDATE `exploration_events`", rewardSet);
        int cleanupEnd = migration.indexOf(';', rewardSet);
        assertTrue(cleanupStart >= 0 && cleanupEnd > rewardSet, "奖励清理语句应完整且可单独执行");
        String cleanupSql = migration.substring(cleanupStart, cleanupEnd + 1);

        var seedStream = getClass().getResourceAsStream("/db/seed_explore.json");
        assertNotNull(seedStream, "探索静态种子必须在测试 classpath 中");
        List<String> battleEventKeys;
        try (seedStream) {
            JsonNode seed = objectMapper.readTree(seedStream);
            battleEventKeys = new java.util.ArrayList<>();
            for (JsonNode row : seed) {
                if ("event".equals(row.path("type").asText())
                        && "battle_placeholder".equals(row.path("event_type").asText())) {
                    String eventKey = row.path("event_key").asText();
                    battleEventKeys.add(eventKey);
                    jdbcTemplate.update("INSERT INTO exploration_events (event_key, event_type, rewards_json) "
                                    + "VALUES (?, ?, ?)", eventKey, "battle_placeholder", "{\"coins\":99}");
                }
            }
        }
        assertEquals(18, battleEventKeys.size(), "应按静态种子覆盖 18 个战斗遭遇");
        jdbcTemplate.update("INSERT INTO exploration_events (event_key, event_type, rewards_json) "
                        + "VALUES (?, ?, ?)",
                "custom_battle_placeholder_not_in_allowlist", "battle_placeholder", "{\"coins\":77}");

        jdbcTemplate.execute(cleanupSql);
        assertAllBattlePlaceholderRewardsEmpty(battleEventKeys);
        jdbcTemplate.execute(cleanupSql);
        assertAllBattlePlaceholderRewardsEmpty(battleEventKeys);
        assertEquals(77, objectMapper.readTree(jdbcTemplate.queryForObject(
                "SELECT rewards_json FROM exploration_events WHERE event_key = ?", String.class,
                "custom_battle_placeholder_not_in_allowlist")).path("coins").asInt(),
                "白名单外的自定义战斗事件不得被清理");

        String scopedEventKey = battleEventKeys.get(0);
        jdbcTemplate.update("UPDATE exploration_events SET event_type = ?, rewards_json = ? WHERE event_key = ?",
                "resource", "{\"coins\":55}", scopedEventKey);
        jdbcTemplate.execute(cleanupSql);
        assertEquals(55, objectMapper.readTree(jdbcTemplate.queryForObject(
                "SELECT rewards_json FROM exploration_events WHERE event_key = ?", String.class,
                scopedEventKey)).path("coins").asInt(), "白名单中的非战斗事件也不得被清理");
        assertEquals(10, playerMapper.selectById(PLAYER_ID).getCoins(), "静态配置迁移不得修改玩家资源");
    }

    private void assertAllBattlePlaceholderRewardsEmpty(List<String> eventKeys) throws Exception {
        for (String eventKey : eventKeys) {
            String rewards = jdbcTemplate.queryForObject(
                    "SELECT rewards_json FROM exploration_events WHERE event_key = ?", String.class, eventKey);
            assertTrue(objectMapper.readTree(rewards).isEmpty(),
                    () -> "战斗占位事件奖励应为空：" + eventKey);
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("concurrency test start timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrency test interrupted", exception);
        }
    }

    private int inventoryQuantity(long playerId, String itemKey) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(quantity), 0) FROM player_inventory WHERE player_id = ? AND item_key = ?",
                Integer.class, playerId, itemKey);
    }

    private void insertSecondPlayer(long playerId, int coins) {
        String statsJson = "{\"level\":1,\"maxHp\":100,\"hp\":100,\"attack\":10,"
                + "\"defense\":5,\"speed\":10,\"critRate\":0.0,\"critDamage\":1.5,"
                + "\"exp\":0,\"avatarRank\":\"F\"}";
        jdbcTemplate.update("INSERT INTO players (id, user_id, player_name, coins, story_fragments, stats_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                playerId, USER_ID + 1, "隔离买家", coins, 0, statsJson);
    }

    private void seedAllocatedPlayerForReset() {
        String statsJson = "{\"level\":1,\"maxHp\":100,\"hp\":100,\"attack\":1001,"
                + "\"defense\":6,\"speed\":10,\"critRate\":0.0,\"critDamage\":1.5,"
                + "\"exp\":0,\"avatarRank\":\"F\",\"constellation\":\"demon_judge_of_fire\","
                + "\"allocatedAtk\":1,"
                + "\"allocatedDef\":1,\"allocatedSpd\":0,\"allocatedCrit\":0,\"freePoints\":0}";
        jdbcTemplate.update("UPDATE players SET coins = ?, stats_json = ? WHERE id = ?",
                100, statsJson, PLAYER_ID);
    }

    private void seedPlayerForAllocation(int freePoints) {
        String statsJson = "{\"level\":1,\"maxHp\":100,\"hp\":100,\"attack\":10,"
                + "\"defense\":5,\"speed\":3,\"critRate\":0.05,\"critDamage\":1.5,"
                + "\"exp\":0,\"avatarRank\":\"F\",\"constellation\":\"demon_judge_of_fire\","
                + "\"allocatedAtk\":0,\"allocatedDef\":0,"
                + "\"allocatedSpd\":0,\"allocatedCrit\":0,\"freePoints\":" + freePoints + "}";
        jdbcTemplate.update("UPDATE players SET stats_json = ? WHERE id = ?", statsJson, PLAYER_ID);
    }

    private void seedPlayerForRevive(int coins, boolean isDead) {
        String statsJson = "{\"level\":2,\"maxHp\":120,\"hp\":0,\"attack\":10,"
                + "\"defense\":5,\"speed\":10,\"critRate\":0.0,\"critDamage\":1.5,"
                + "\"exp\":0,\"avatarRank\":\"F\",\"constellation\":\"demon_judge_of_fire\","
                + "\"isDead\":" + isDead + "}";
        jdbcTemplate.update("UPDATE players SET coins = ?, stats_json = ? WHERE id = ?",
                coins, statsJson, PLAYER_ID);
    }

    private void seedPeerRevivePlayers(int reviverCoins, boolean targetDead) {
        seedPeerRevivePlayers(reviverCoins, targetDead, "queen_of_darkest_spring");
    }

    private void seedPeerRevivePlayers(int reviverCoins, boolean targetDead, String targetConstellation) {
        if (playerMapper.selectById(PLAYER_ID + 1) == null)
            insertSecondPlayer(PLAYER_ID + 1, 50);
        String reviverStats = "{\"level\":2,\"maxHp\":120,\"hp\":120,\"attack\":10,"
                + "\"defense\":5,\"speed\":10,\"critRate\":0.0,\"critDamage\":1.5,"
                + "\"exp\":0,\"avatarRank\":\"F\",\"constellation\":\"demon_judge_of_fire\"}";
        String targetStats = "{\"level\":2,\"maxHp\":120,\"hp\":0,\"attack\":10,"
                + "\"defense\":5,\"speed\":10,\"critRate\":0.0,\"critDamage\":1.5,"
                + "\"exp\":0,\"avatarRank\":\"F\",\"constellation\":\"" + targetConstellation + "\","
                + "\"isDead\":" + targetDead + "}";
        jdbcTemplate.update("UPDATE players SET coins = ?, stats_json = ? WHERE id = ?",
                reviverCoins, reviverStats, PLAYER_ID);
        jdbcTemplate.update("UPDATE players SET player_name = ?, stats_json = ? WHERE id = ?",
                "待援救隔离角色", targetStats, PLAYER_ID + 1);
    }

    private PlayerResourceTransactionService.Change resourceChange(
            String idempotencyKey, String resourceType, String resourceKey,
            long amount, long balanceBefore, long balanceAfter,
            String sourceType, String sourceId, String transactionGroupId) {
        return new PlayerResourceTransactionService.Change(
                PLAYER_ID, resourceType, resourceKey, amount, balanceBefore, balanceAfter,
                sourceType, sourceId, transactionGroupId, idempotencyKey,
                Map.of("testOnly", true));
    }

    private void assertResourceTransaction(String resourceType, String resourceKey,
                                           long amount, long balanceBefore, long balanceAfter) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT amount, balance_before, balance_after FROM player_resource_transactions "
                        + "WHERE player_id = ? AND resource_type = ? AND resource_key = ?",
                PLAYER_ID, resourceType, resourceKey);
        assertEquals(amount, ((Number) row.get("amount")).longValue());
        assertEquals(balanceBefore, ((Number) row.get("balance_before")).longValue());
        assertEquals(balanceAfter, ((Number) row.get("balance_after")).longValue());
    }

    private void assertResourceTransactionFrom(String sourceType, String sourceId,
                                               String resourceType, String resourceKey,
                                               long amount, long balanceBefore, long balanceAfter) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT amount, balance_before, balance_after FROM player_resource_transactions "
                        + "WHERE player_id = ? AND source_type = ? AND source_id = ? "
                        + "AND resource_type = ? AND resource_key = ?",
                PLAYER_ID, sourceType, sourceId, resourceType, resourceKey);
        assertEquals(amount, ((Number) row.get("amount")).longValue());
        assertEquals(balanceBefore, ((Number) row.get("balance_before")).longValue());
        assertEquals(balanceAfter, ((Number) row.get("balance_after")).longValue());
    }

    @Test
    void channelHeatOverflowRollsBackTheEntireCombatSettlementInH2() throws Exception {
        String boundaryStats = "{\"level\":1,\"maxHp\":100,\"hp\":100,\"attack\":1000,"
                + "\"defense\":5,\"speed\":10,\"critRate\":0.0,\"critDamage\":1.5,"
                + "\"exp\":0,\"avatarRank\":\"F\",\"constellation\":\"demon_judge_of_fire\","
                + "\"channelHeat\":" + Integer.MAX_VALUE + "}";
        jdbcTemplate.update("UPDATE players SET stats_json = ? WHERE id = ?", boundaryStats, PLAYER_ID);
        Monster monster = testMonster();
        monster.setRewardsJson("{\"exp\":10,\"coins\":5,\"storyFragments\":2,\"channelHeat\":1}");
        when(monsterService.getMonsterEntity("h2_test_beast")).thenReturn(monster);
        when(playerEffectService.applyChannelHeatGainRate(PLAYER_ID, 1)).thenReturn(1);

        assertThrows(BusinessException.class,
                () -> combatService.startCombat(
                        PLAYER_ID, "h2_test_beast", "h2-channel-heat-overflow", USER_ID));

        Player unchanged = playerMapper.selectById(PLAYER_ID);
        assertEquals(10, unchanged.getCoins());
        assertEquals(0, unchanged.getStoryFragments());
        JsonNode unchangedStats = objectMapper.readTree(unchanged.getStatsJson());
        assertEquals(0, unchangedStats.path("exp").asInt());
        assertEquals(Integer.MAX_VALUE, unchangedStats.path("channelHeat").asInt());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ?",
                Integer.class, PLAYER_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM battle_logs WHERE player_id = ?", Integer.class, PLAYER_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_logs WHERE player_id = ?", Integer.class, PLAYER_ID));
    }

    @Test
    void settlementPersistsExactRewardsAndReplayDoesNotDuplicateAnyWrite() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertEquals("jdbc:h2:mem:combat_persistence", connection.getMetaData().getURL());
        }

        mockMvc.perform(post("/api/combat/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        String token = performLogin();
        CombatResultVO first = performCombatStart(token);

        assertEquals("win", first.getResult());
        assertEquals(15, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(2, playerMapper.selectById(PLAYER_ID).getStoryFragments());
        assertEquals(10, inventoryMapper.selectOne(new QueryWrapper<com.huazhenghai.readergame.entity.PlayerInventory>()
                .eq("player_id", PLAYER_ID).eq("item_key", "h2_test_herb")).getQuantity());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_equipment WHERE player_id = ? AND equipment_key = ?",
                Integer.class, PLAYER_ID, "h2_test_blade"));
        assertEquals(5, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ?",
                Integer.class, PLAYER_ID));
        assertResourceTransaction("currency", "coins", 5, 10, 15);
        assertResourceTransaction("currency", "story_fragments", 2, 0, 2);
        assertResourceTransaction("experience", "exp", 10, 0, 10);
        assertResourceTransaction("item", "h2_test_herb", 1, 9, 10);
        assertResourceTransaction("equipment", "h2_test_blade", 1, 0, 1);

        List<Map<String, Object>> expectedDrops = List.of(
                Map.of("itemKey", "h2_test_herb", "itemName", "测试草药", "quantity", 1, "totalQuantity", 10),
                Map.of("equipmentKey", "h2_test_blade", "equipmentName", "测试短刃"));
        assertEquals(expectedDrops, first.getDrops());

        List<PlayerLog> logs = playerLogMapper.selectList(
                new QueryWrapper<PlayerLog>().eq("player_id", PLAYER_ID));
        PlayerLog combatLog = logs.stream().filter(log -> "battle".equals(log.getType()))
                .findFirst().orElseThrow();
        JsonNode metadata = objectMapper.readTree(combatLog.getMetadataJson());
        assertEquals(objectMapper.valueToTree(expectedDrops), metadata.get("drops"));

        BattleLog receipt = battleLogMapper.selectByPlayerAndRequestKey(PLAYER_ID, REQUEST_KEY);
        assertNotNull(receipt);
        assertEquals(objectMapper.valueToTree(expectedDrops), objectMapper.readTree(receipt.getDropsJson()));
        assertEquals(objectMapper.valueToTree(expectedDrops),
                objectMapper.readTree(receipt.getResultJson()).get("drops"));

        CombatResultVO replay = performCombatStart(token);

        assertEquals(first.getDrops(), replay.getDrops());
        assertEquals(15, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(10, inventoryMapper.selectOne(new QueryWrapper<com.huazhenghai.readergame.entity.PlayerInventory>()
                .eq("player_id", PLAYER_ID).eq("item_key", "h2_test_herb")).getQuantity());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_equipment WHERE player_id = ?",
                Integer.class, PLAYER_ID));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM battle_logs WHERE player_id = ?",
                Integer.class, PLAYER_ID));
        assertEquals(5, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ?",
                Integer.class, PLAYER_ID), "同一战斗请求重放不能重复记资源流水");
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_logs WHERE player_id = ?",
                Integer.class, PLAYER_ID));
    }

    @Test
    void nestedStoryChoiceBattleReceiptCanBeRestoredAndAcknowledgedInIsolatedH2() throws Exception {
        String battleRequestKey = "h2-story-battle-encounter-001";
        String metadata = "{\"kind\":\"story_choice\",\"requestKey\":\"h2-choice-request-001\","
                + "\"battleRequestKey\":\"" + battleRequestKey + "\",\"battleEncounterResolved\":false,"
                + "\"requestResponse\":{\"pending_battle\":{\"result_type\":\"battle\","
                + "\"monster_key\":\"iron_king_trial\",\"result\":{\"event_key\":\"iron-story-choice-0\","
                + "\"event_type\":\"story_battle\",\"name\":\"铁王的钢铁化试炼\",\"description\":\"\"}}}}";
        jdbcTemplate.update("INSERT INTO player_logs (player_id, message, type, metadata_json) VALUES (?, ?, ?, ?)",
                PLAYER_ID, "你选择了拒绝下跪", "story", metadata);

        List<Map<String, Object>> pending = playerLogService.findPendingBattleEncounters(PLAYER_ID);

        assertEquals(1, pending.size());
        assertEquals(battleRequestKey, pending.get(0).get("request_key"));
        assertEquals("iron_king_trial", pending.get(0).get("monster_key"));

        playerLogService.acknowledgeBattleEncounter(PLAYER_ID, battleRequestKey);

        String acknowledgedMetadata = jdbcTemplate.queryForObject(
                "SELECT metadata_json FROM player_logs WHERE player_id = ?", String.class, PLAYER_ID);
        assertTrue(objectMapper.readTree(acknowledgedMetadata)
                .path("battleEncounterResolved").asBoolean());
        assertTrue(playerLogService.findPendingBattleEncounters(PLAYER_ID).isEmpty());
    }

    @Test
    void storyChoiceRiskAndNestedBattleReceiptPersistOnceAcrossReplayInIsolatedH2() throws Exception {
        String eventKey = "h2_iron_king_story";
        String storyInstanceKey = "h2-iron-story-instance";
        String choiceRequestKey = "h2-iron-choice-request";
        jdbcTemplate.update("INSERT INTO locations (location_key, name, unlock_conditions_json, "
                        + "event_rates_json, min_level, recommended_rank) VALUES (?, ?, ?, ?, ?, ?)",
                "chungmuro", "忠武路", "{}", "{}", 1, "F");
        jdbcTemplate.update("UPDATE players SET stats_json = ?, stage_progress_json = ?, story_flags_json = ?, "
                        + "decision_history_json = ? WHERE id = ?",
                "{\"level\":5,\"maxHp\":100,\"hp\":80}",
                "{\"storyEventsTriggered\":[\"" + eventKey + "\"],\"pendingStorySnapshots\":{\""
                        + eventKey + "\":{\"instanceKey\":\"" + storyInstanceKey + "\",\"rewards\":{}}}}",
                "{}", "[]", PLAYER_ID);
        jdbcTemplate.update("INSERT INTO exploration_events (event_key, event_type, location_key, name, "
                        + "description, repeatable, choices_json, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                eventKey, "story", "chungmuro", "忠武路的铁王", "他逼你在铁王面前屈膝。", 0,
                "[{\"label\":\"拒绝下跪\",\"consequence_text\":\"一道投影挡在前方。\","
                        + "\"risks\":{\"damagePercent\":15,\"monsterKey\":\"iron_king_trial\","
                        + "\"battleName\":\"铁王的钢铁化试炼\",\"battleDescription\":\"可迎战或撤退。\"}}]",
                1);
        when(titleService.evaluateAndUnlockTitles(PLAYER_ID, null)).thenReturn(List.of());

        Map<String, Object> first = exploreService.makeChoice(
                PLAYER_ID, eventKey, 0, choiceRequestKey, storyInstanceKey, USER_ID);

        assertEquals(65, objectMapper.readTree(playerMapper.selectById(PLAYER_ID).getStatsJson())
                .path("hp").asInt());
        assertEquals(15, ((Map<?, ?>) first.get("applied_risks")).get("damage"));
        assertEquals(1, playerStoryLogMapper.selectCount(new QueryWrapper<PlayerStoryLog>()
                .eq("player_id", PLAYER_ID).eq("event_key", eventKey)));
        List<Map<String, Object>> pending = playerLogService.findPendingBattleEncounters(PLAYER_ID);
        assertEquals(1, pending.size());
        assertEquals("iron_king_trial", pending.get(0).get("monster_key"));
        assertEquals("chungmuro", pending.get(0).get("location_key"));
        assertEquals("忠武路", pending.get(0).get("location_name"));
        String battleRequestKey = (String) pending.get(0).get("request_key");
        assertNotNull(battleRequestKey);

        Map<String, Object> replay = exploreService.makeChoice(
                PLAYER_ID, eventKey, 0, choiceRequestKey, storyInstanceKey, USER_ID);

        assertEquals(true, replay.get("request_replayed"));
        assertEquals(first.get("pending_battle"), replay.get("pending_battle"));
        assertEquals(65, objectMapper.readTree(playerMapper.selectById(PLAYER_ID).getStatsJson())
                .path("hp").asInt());
        assertEquals(1, playerStoryLogMapper.selectCount(new QueryWrapper<PlayerStoryLog>()
                .eq("player_id", PLAYER_ID).eq("event_key", eventKey)));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_logs WHERE player_id = ? "
                + "AND type = 'story'", Integer.class, PLAYER_ID));
        assertEquals(battleRequestKey,
                playerLogService.findPendingBattleEncounters(PLAYER_ID).get(0).get("request_key"));
    }

    @Test
    void explorationBattleKeepsItsOriginalSceneAfterSwitchAndPendingQueueReload() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertEquals("jdbc:h2:mem:combat_persistence", connection.getMetaData().getURL());
        }
        assertTrue(serverPort > 0 && serverPort != 3174,
                "待战斗地点恢复验收必须只使用隔离 H2 随机端口");

        String eventKey = "h2_pending_battle_location";
        jdbcTemplate.update("UPDATE players SET stats_json = ?, stage_progress_json = ?, current_location = ? "
                        + "WHERE id = ?",
                "{\"level\":1,\"maxHp\":100,\"hp\":100,\"stamina\":50,\"maxStamina\":50,"
                        + "\"attack\":1000,\"defense\":5,\"speed\":10,\"exp\":0,"
                        + "\"avatarRank\":\"F\",\"constellation\":\"demon_judge_of_fire\"}",
                "{}", "h2_test_zone", PLAYER_ID);
        jdbcTemplate.update("UPDATE locations SET event_rates_json = ?, unlock_conditions_json = ? "
                        + "WHERE location_key = ?",
                "{\"battle_placeholder\":100}", "{}", "h2_test_zone");
        jdbcTemplate.update("INSERT INTO locations (location_key, name, unlock_conditions_json, "
                        + "event_rates_json, min_level, recommended_rank) VALUES (?, ?, ?, ?, ?, ?)",
                "h2_alt_zone", "切换验证区", "{}", "{\"resource\":100}", 1, "F");
        jdbcTemplate.update("INSERT INTO exploration_events (event_key, event_type, location_key, name, "
                        + "description, weight, stamina_cost, repeatable, required_conditions_json, "
                        + "rewards_json, risks_json, progress_effects_json, choices_json, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                eventKey, "battle_placeholder", "h2_test_zone", "内存测试兽遭遇", "来自旧场景的测试战斗。",
                1, 5, 1, "{}", "{}", "{\"monsterKey\":\"h2_test_beast\"}", "{}", "[]", 1);
        jdbcTemplate.update("INSERT INTO exploration_events (event_key, event_type, location_key, name, "
                        + "description, weight, stamina_cost, repeatable, required_conditions_json, "
                        + "rewards_json, risks_json, progress_effects_json, choices_json, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "h2_alt_zone_resource", "resource", "h2_alt_zone", "可跳过遭遇的验证事件", "若无服务端拦截将发放星币。",
                1, 0, 1, "{}", "{\"coins\":1}", "{}", "{}", "[]", 1);

        String token = performLogin();
        ResponseEntity<String> exploreResponse = performHttp(HttpMethod.POST, "/api/explore/start", token,
                Map.of("playerId", PLAYER_ID,
                        "locationKey", "h2_test_zone",
                        "requestKey", "h2-pending-location-explore"));
        assertTrue(exploreResponse.getStatusCode().is2xxSuccessful());
        JsonNode explored = objectMapper.readTree(exploreResponse.getBody()).path("data");
        assertEquals("battle", explored.path("result_type").asText());
        assertEquals(eventKey, explored.path("result").path("event_key").asText());
        assertEquals("h2_test_zone", explored.path("location_key").asText());
        assertEquals("内存测试区", explored.path("location_name").asText());
        assertEquals(5, explored.path("stamina_cost_paid").asInt());
        String battleRequestKey = explored.path("request_key").asText();

        ResponseEntity<String> initialQueueResponse = performHttp(
                HttpMethod.GET, "/api/explore/pending-battles/" + PLAYER_ID, token, null);
        assertTrue(initialQueueResponse.getStatusCode().is2xxSuccessful());
        JsonNode initialQueue = objectMapper.readTree(initialQueueResponse.getBody())
                .path("data").path("pending_battles");
        assertEquals(1, initialQueue.size());
        assertEquals(battleRequestKey, initialQueue.get(0).path("request_key").asText());
        assertEquals("内存测试区", initialQueue.get(0).path("location_name").asText());

        ResponseEntity<String> replayExploreResponse = performHttp(HttpMethod.POST, "/api/explore/start", token,
                Map.of("playerId", PLAYER_ID,
                        "locationKey", "h2_test_zone",
                        "requestKey", "h2-pending-location-explore"));
        assertTrue(replayExploreResponse.getStatusCode().is2xxSuccessful(),
                "尚未结算的探索原请求仍必须允许幂等重放");
        assertTrue(objectMapper.readTree(replayExploreResponse.getBody()).path("data")
                .path("request_replayed").asBoolean());

        ResponseEntity<String> switchResponse = performHttp(HttpMethod.POST,
                "/api/player/switch-location", token,
                Map.of("playerId", PLAYER_ID, "locationKey", "h2_alt_zone"));
        assertTrue(switchResponse.getStatusCode().is2xxSuccessful());
        assertEquals("h2_alt_zone", playerMapper.selectById(PLAYER_ID).getCurrentLocation());

        ResponseEntity<String> skipPendingResponse = performHttp(HttpMethod.POST,
                "/api/explore/start", token,
                Map.of("playerId", PLAYER_ID,
                        "locationKey", "h2_alt_zone",
                        "requestKey", "h2-pending-location-skip-attempt"));
        assertEquals(400, skipPendingResponse.getStatusCode().value());
        JsonNode skipPendingEnvelope = objectMapper.readTree(skipPendingResponse.getBody());
        assertFalse(skipPendingEnvelope.path("success").asBoolean());
        assertEquals("PENDING_BATTLE_UNRESOLVED",
                skipPendingEnvelope.path("error").path("code").asText());
        assertEquals(10, playerMapper.selectById(PLAYER_ID).getCoins(),
                "服务端拒绝跳过遭遇后不能发放新地点资源奖励");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? "
                        + "AND source_type = 'explore'",
                Integer.class, PLAYER_ID),
                "待战斗期间新的探索不能再次扣体力或写资源流水");

        // A fresh query after the scene change models a page reload: the old battle remains queued
        // with its original scene context, instead of being rewritten as the newly selected scene.
        ResponseEntity<String> reloadedQueueResponse = performHttp(
                HttpMethod.GET, "/api/explore/pending-battles/" + PLAYER_ID, token, null);
        assertTrue(reloadedQueueResponse.getStatusCode().is2xxSuccessful());
        JsonNode reloadedQueue = objectMapper.readTree(reloadedQueueResponse.getBody())
                .path("data").path("pending_battles");
        assertEquals(1, reloadedQueue.size());
        assertEquals(battleRequestKey, reloadedQueue.get(0).path("request_key").asText());
        assertEquals(eventKey, reloadedQueue.get(0).path("result").path("event_key").asText());
        assertEquals("h2_test_zone", reloadedQueue.get(0).path("location_key").asText());
        assertEquals("内存测试区", reloadedQueue.get(0).path("location_name").asText());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? "
                        + "AND source_type = 'explore' AND source_id = ?",
                Integer.class, PLAYER_ID, eventKey),
                "切换地点和重读待战斗不能再次扣探索体力");

        assertEquals(10, playerMapper.selectById(PLAYER_ID).getCoins(),
                "探索只确定遭遇，不应提前发放怪物战斗奖励");
        assertEquals(9, inventoryMapper.selectOne(new QueryWrapper<com.huazhenghai.readergame.entity.PlayerInventory>()
                .eq("player_id", PLAYER_ID).eq("item_key", "h2_test_herb")).getQuantity());

        CombatResultVO firstBattle = performHttpCombatStart(token, "h2_test_beast", battleRequestKey);
        assertEquals("win", firstBattle.getResult());
        assertEquals(10, firstBattle.getRewards().getExp());
        assertEquals(5, firstBattle.getRewards().getCoins());
        assertEquals(2, firstBattle.getRewards().getStoryFragments());
        List<Map<String, Object>> expectedDrops = List.of(
                Map.of("itemKey", "h2_test_herb", "itemName", "测试草药", "quantity", 1,
                        "totalQuantity", 10),
                Map.of("equipmentKey", "h2_test_blade", "equipmentName", "测试短刃"));
        assertEquals(expectedDrops, firstBattle.getDrops(),
                "战斗掉落只按怪物配置结算，探索占位事件没有重复奖励");
        assertEquals(15, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(2, playerMapper.selectById(PLAYER_ID).getStoryFragments());
        assertEquals(10, inventoryMapper.selectOne(new QueryWrapper<com.huazhenghai.readergame.entity.PlayerInventory>()
                .eq("player_id", PLAYER_ID).eq("item_key", "h2_test_herb")).getQuantity());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_equipment WHERE player_id = ? AND equipment_key = ?",
                Integer.class, PLAYER_ID, "h2_test_blade"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM battle_logs WHERE player_id = ? AND request_key = ?",
                Integer.class, PLAYER_ID, battleRequestKey));
        assertEquals(5, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? "
                        + "AND source_type = 'combat'",
                Integer.class, PLAYER_ID));

        CombatResultVO replayedBattle = performHttpCombatStart(token, "h2_test_beast", battleRequestKey);
        assertEquals(firstBattle.getDrops(), replayedBattle.getDrops());
        assertEquals(15, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(10, inventoryMapper.selectOne(new QueryWrapper<com.huazhenghai.readergame.entity.PlayerInventory>()
                .eq("player_id", PLAYER_ID).eq("item_key", "h2_test_herb")).getQuantity());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM battle_logs WHERE player_id = ? AND request_key = ?",
                Integer.class, PLAYER_ID, battleRequestKey),
                "同请求键重放不能重复结算战斗");
        assertEquals(5, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? "
                        + "AND source_type = 'combat'",
                Integer.class, PLAYER_ID));

        ResponseEntity<String> acknowledgeResponse = performHttp(HttpMethod.POST,
                "/api/explore/pending-battles/" + PLAYER_ID + "/ack", token,
                Map.of("requestKey", battleRequestKey));
        assertTrue(acknowledgeResponse.getStatusCode().is2xxSuccessful());
        assertTrue(playerLogService.findPendingBattleEncounters(PLAYER_ID).isEmpty(),
                "结算并确认后不应再恢复待战斗");
    }

    @Test
    void fleeingAnExplorationBattlePersistsEscapeWithoutMonsterRewardsAndUnblocksExploration() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertEquals("jdbc:h2:mem:combat_persistence", connection.getMetaData().getURL());
        }

        String eventKey = "h2_pending_battle_flee";
        String exploreRequestKey = "h2-pending-battle-flee-explore";
        jdbcTemplate.update("UPDATE players SET stats_json = ?, stage_progress_json = ?, current_location = ? "
                        + "WHERE id = ?",
                "{\"level\":1,\"maxHp\":100,\"hp\":100,\"stamina\":50,\"maxStamina\":50,"
                        + "\"attack\":10,\"defense\":5,\"speed\":10,\"exp\":0,"
                        + "\"avatarRank\":\"F\",\"constellation\":\"demon_judge_of_fire\"}",
                "{}", "h2_test_zone", PLAYER_ID);
        jdbcTemplate.update("UPDATE locations SET event_rates_json = ?, unlock_conditions_json = ? "
                        + "WHERE location_key = ?",
                "{\"battle_placeholder\":100}", "{}", "h2_test_zone");
        jdbcTemplate.update("INSERT INTO exploration_events (event_key, event_type, location_key, name, "
                        + "description, weight, stamina_cost, repeatable, required_conditions_json, "
                        + "rewards_json, risks_json, progress_effects_json, choices_json, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                eventKey, "battle_placeholder", "h2_test_zone", "撤退验证遭遇", "仅用于隔离 H2 的撤退验证。",
                1, 5, 1, "{}", "{\"coins\":99,\"exp\":999}",
                "{\"monsterKey\":\"h2_test_beast\"}", "{}", "[]", 1);

        String token = performLogin();
        ResponseEntity<String> exploreResponse = performHttp(HttpMethod.POST,
                "/api/explore/start", token, Map.of(
                        "playerId", PLAYER_ID,
                        "locationKey", "h2_test_zone",
                        "requestKey", exploreRequestKey));
        assertTrue(exploreResponse.getStatusCode().is2xxSuccessful());
        JsonNode encounter = objectMapper.readTree(exploreResponse.getBody()).path("data");
        assertEquals("battle", encounter.path("result_type").asText());
        assertEquals(5, encounter.path("stamina_cost_paid").asInt());
        String battleRequestKey = encounter.path("request_key").asText();
        assertEquals(10, playerMapper.selectById(PLAYER_ID).getCoins(),
                "战斗占位事件上的哨兵奖励不能在探索阶段发放");

        ResponseEntity<String> fleeResponse = performHttp(HttpMethod.POST, "/api/combat/resolve", token,
                Map.of("playerId", PLAYER_ID, "monsterKey", "h2_test_beast",
                        "action", "flee", "requestKey", battleRequestKey));
        assertTrue(fleeResponse.getStatusCode().is2xxSuccessful(), fleeResponse.getBody());
        JsonNode fleeData = objectMapper.readTree(fleeResponse.getBody()).path("data");
        assertEquals("escape", fleeData.path("battle").path("result").asText());
        assertEquals("你从内存测试兽面前撤退了。", fleeData.path("message").asText());
        assertEquals(10, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(0, playerMapper.selectById(PLAYER_ID).getStoryFragments());
        assertEquals(100, objectMapper.readTree(playerMapper.selectById(PLAYER_ID).getStatsJson())
                .path("hp").asInt(), "撤退不应让未开始的战斗造成伤害");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM battle_logs WHERE player_id = ? AND request_key = ? AND result = 'escape'",
                Integer.class, PLAYER_ID, battleRequestKey));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? "
                        + "AND source_type = 'combat'",
                Integer.class, PLAYER_ID), "撤退不应结算怪物胜利奖励");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? "
                        + "AND source_type = 'explore' AND source_id = ?",
                Integer.class, PLAYER_ID, eventKey), "遭遇撤退后只保留首次探索的体力流水");
        assertTrue(playerLogService.findPendingBattleEncounters(PLAYER_ID).isEmpty(),
                "已保存撤退战报后，不应继续恢复同一待战斗遭遇");

        ResponseEntity<String> fleeReplay = performHttp(HttpMethod.POST, "/api/combat/resolve", token,
                Map.of("playerId", PLAYER_ID, "monsterKey", "h2_test_beast",
                        "action", "flee", "requestKey", battleRequestKey));
        assertTrue(fleeReplay.getStatusCode().is2xxSuccessful());
        assertEquals("你从内存测试兽面前撤退了。",
                objectMapper.readTree(fleeReplay.getBody()).path("data").path("message").asText());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM battle_logs WHERE player_id = ? AND request_key = ?",
                Integer.class, PLAYER_ID, battleRequestKey), "同键重放撤退不能重复写战报");
        assertEquals(10, playerMapper.selectById(PLAYER_ID).getCoins(), "撤退重放不能补发占位奖励");

        ResponseEntity<String> acknowledgeResponse = performHttp(HttpMethod.POST,
                "/api/explore/pending-battles/" + PLAYER_ID + "/ack", token,
                Map.of("requestKey", battleRequestKey));
        assertTrue(acknowledgeResponse.getStatusCode().is2xxSuccessful());
        JsonNode acknowledgedMetadata = objectMapper.readTree(jdbcTemplate.queryForObject(
                "SELECT metadata_json FROM player_logs WHERE player_id = ? AND type = 'battle' "
                        + "ORDER BY id ASC LIMIT 1",
                String.class, PLAYER_ID));
        assertTrue(acknowledgedMetadata.path("battleEncounterResolved").asBoolean());

        jdbcTemplate.update("UPDATE exploration_events SET enabled = 0 WHERE event_key = ?", eventKey);
        jdbcTemplate.update("UPDATE locations SET event_rates_json = ? WHERE location_key = ?",
                "{\"resource\":100}", "h2_test_zone");
        jdbcTemplate.update("INSERT INTO exploration_events (event_key, event_type, location_key, name, "
                        + "description, weight, stamina_cost, repeatable, required_conditions_json, "
                        + "rewards_json, progress_effects_json, choices_json, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "h2_post_flee_resource", "resource", "h2_test_zone", "撤退后继续探索", "待战斗已处理。",
                1, 0, 1, "{}", "{}", "{}", "[]", 1);
        ResponseEntity<String> nextExplore = performHttp(HttpMethod.POST,
                "/api/explore/start", token, Map.of(
                        "playerId", PLAYER_ID,
                        "locationKey", "h2_test_zone",
                        "requestKey", "h2-post-flee-explore"));
        assertTrue(nextExplore.getStatusCode().is2xxSuccessful(), nextExplore.getBody());
        assertEquals("h2_post_flee_resource",
                objectMapper.readTree(nextExplore.getBody()).path("data").path("result").path("event_key")
                        .asText());
    }

    @Test
    void losingAnExplorationBattlePersistsDefeatWithoutRewardsAndResolvesEncounter() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertEquals("jdbc:h2:mem:combat_persistence", connection.getMetaData().getURL());
        }

        String eventKey = "h2_pending_battle_loss";
        String monsterKey = "h2_overwhelming_test_beast";
        String exploreRequestKey = "h2-pending-battle-loss-explore";
        jdbcTemplate.update("UPDATE players SET stats_json = ?, stage_progress_json = ?, current_location = ? "
                        + "WHERE id = ?",
                "{\"level\":1,\"maxHp\":100,\"hp\":100,\"stamina\":50,\"maxStamina\":50,"
                        + "\"attack\":10,\"defense\":5,\"speed\":10,\"exp\":0,"
                        + "\"avatarRank\":\"F\",\"constellation\":\"demon_judge_of_fire\"}",
                "{}", "h2_test_zone", PLAYER_ID);
        jdbcTemplate.update("UPDATE locations SET event_rates_json = ?, unlock_conditions_json = ? "
                        + "WHERE location_key = ?",
                "{\"battle_placeholder\":100}", "{}", "h2_test_zone");
        jdbcTemplate.update("INSERT INTO exploration_events (event_key, event_type, location_key, name, "
                        + "description, weight, stamina_cost, repeatable, required_conditions_json, "
                        + "rewards_json, risks_json, progress_effects_json, choices_json, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                eventKey, "battle_placeholder", "h2_test_zone", "战败验证遭遇", "仅用于隔离 H2 的失败验证。",
                1, 5, 1, "{}", "{\"coins\":99,\"exp\":999}",
                "{\"monsterKey\":\"" + monsterKey + "\"}", "{}", "[]", 1);

        Monster overwhelmingMonster = testMonster();
        overwhelmingMonster.setMonsterKey(monsterKey);
        overwhelmingMonster.setName("内存压制兽");
        overwhelmingMonster.setStatsJson("{\"hp\":10000,\"attack\":1000,\"defense\":0,\"speed\":1000}");
        overwhelmingMonster.setRewardsJson("{\"exp\":999,\"coins\":999,\"storyFragments\":999}");
        when(monsterService.getMonsterEntity(monsterKey)).thenReturn(overwhelmingMonster);

        String token = performLogin();
        ResponseEntity<String> exploreResponse = performHttp(HttpMethod.POST,
                "/api/explore/start", token, Map.of(
                        "playerId", PLAYER_ID,
                        "locationKey", "h2_test_zone",
                        "requestKey", exploreRequestKey));
        assertTrue(exploreResponse.getStatusCode().is2xxSuccessful());
        JsonNode encounter = objectMapper.readTree(exploreResponse.getBody()).path("data");
        assertEquals("battle", encounter.path("result_type").asText());
        String battleRequestKey = encounter.path("request_key").asText();

        CombatResultVO defeat = performHttpCombatStart(token, monsterKey, battleRequestKey);
        assertEquals("lose", defeat.getResult());
        assertEquals(1, defeat.getPlayerHpAfter(),
                "当前战斗失败规则将剩余 HP 保底为 1，而不是让战斗直接造成死亡");
        assertEquals(-10, defeat.getRewards().getCoins(), "失败时只应显示现行 10 星币惩罚");
        assertEquals(0, defeat.getRewards().getExp());
        assertTrue(defeat.getDrops().isEmpty());
        assertEquals(0, playerMapper.selectById(PLAYER_ID).getCoins(),
                "哨兵占位奖励没有发放；仅结算败北 10 星币惩罚");
        assertEquals(0, playerMapper.selectById(PLAYER_ID).getStoryFragments());
        assertEquals(1, objectMapper.readTree(playerMapper.selectById(PLAYER_ID).getStatsJson())
                .path("hp").asInt());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM battle_logs WHERE player_id = ? AND request_key = ? AND result = 'lose'",
                Integer.class, PLAYER_ID, battleRequestKey));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? "
                        + "AND source_type = 'combat'",
                Integer.class, PLAYER_ID), "战败不能发放怪物胜利奖励或掉落");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? "
                        + "AND source_type = 'combat_loss' AND amount = -10 "
                        + "AND balance_before = 10 AND balance_after = 0",
                Integer.class, PLAYER_ID), "败北惩罚应记录一次且前后余额正确");
        assertTrue(playerLogService.findPendingBattleEncounters(PLAYER_ID).isEmpty(),
                "战败战报已提交后不应继续恢复原待战斗遭遇");

        CombatResultVO replayedDefeat = performHttpCombatStart(token, monsterKey, battleRequestKey);
        assertEquals("lose", replayedDefeat.getResult());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM battle_logs WHERE player_id = ? AND request_key = ?",
                Integer.class, PLAYER_ID, battleRequestKey), "同键重放战败不能再次扣血或写战报");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? "
                        + "AND source_type = 'combat_loss' AND idempotency_key = ?",
                Integer.class, PLAYER_ID, battleRequestKey + ":coins"), "重放战败不能重复扣除惩罚");
        assertEquals(0, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(1, objectMapper.readTree(playerMapper.selectById(PLAYER_ID).getStatsJson())
                .path("hp").asInt());

        ResponseEntity<String> acknowledgeResponse = performHttp(HttpMethod.POST,
                "/api/explore/pending-battles/" + PLAYER_ID + "/ack", token,
                Map.of("requestKey", battleRequestKey));
        assertTrue(acknowledgeResponse.getStatusCode().is2xxSuccessful());
        JsonNode encounterMetadata = objectMapper.readTree(jdbcTemplate.queryForObject(
                "SELECT metadata_json FROM player_logs WHERE player_id = ? "
                        + "AND message LIKE ? ORDER BY id ASC LIMIT 1",
                String.class, PLAYER_ID, "%战败验证遭遇%"));
        assertTrue(encounterMetadata.path("battleEncounterResolved").asBoolean());
    }

    @Test
    void personalStoryChoicesPersistSeparatelyForTwoPlayersInIsolatedH2() throws Exception {
        long otherPlayerId = PLAYER_ID + 1;
        insertSecondPlayer(otherPlayerId, 10);
        String eventKey = "h2_personal_story_isolation";
        String firstInstance = "h2-personal-instance-a";
        String secondInstance = "h2-personal-instance-b";
        String firstProgress = "{\"storyEventsTriggered\":[\"" + eventKey
                + "\"],\"pendingStorySnapshots\":{\"" + eventKey
                + "\":{\"instanceKey\":\"" + firstInstance + "\",\"rewards\":{}}}}";
        String secondProgress = "{\"storyEventsTriggered\":[\"" + eventKey
                + "\"],\"pendingStorySnapshots\":{\"" + eventKey
                + "\":{\"instanceKey\":\"" + secondInstance + "\",\"rewards\":{}}}}";
        jdbcTemplate.update("UPDATE players SET stats_json = ?, stage_progress_json = ?, story_flags_json = ?, "
                        + "decision_history_json = ? WHERE id = ?",
                "{\"level\":1,\"maxHp\":100,\"hp\":100,\"worldLineShift\":4}",
                firstProgress, "{}", "[]", PLAYER_ID);
        jdbcTemplate.update("UPDATE players SET stats_json = ?, stage_progress_json = ?, story_flags_json = ?, "
                        + "decision_history_json = ? WHERE id = ?",
                "{\"level\":1,\"maxHp\":100,\"hp\":100,\"worldLineShift\":-1}",
                secondProgress, "{}", "[]", otherPlayerId);
        jdbcTemplate.update("INSERT INTO exploration_events (event_key, event_type, location_key, name, "
                        + "description, repeatable, choices_json, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                eventKey, "story", "h2_test_zone", "不同玩家的故事选择", "每个角色保留自己的时间线。", 0,
                "[{\"label\":\"留在自己的路线 A\",\"rewards_override\":{\"worldLineShift\":2}},"
                        + "{\"label\":\"选择自己的路线 B\",\"rewards_override\":{\"worldLineShift\":-3}}]",
                1);
        when(titleService.evaluateAndUnlockTitles(PLAYER_ID, null)).thenReturn(List.of());
        when(titleService.evaluateAndUnlockTitles(otherPlayerId, null)).thenReturn(List.of());

        exploreService.makeChoice(PLAYER_ID, eventKey, 0, "h2-personal-choice-a", firstInstance, USER_ID);
        exploreService.makeChoice(otherPlayerId, eventKey, 1, "h2-personal-choice-b", secondInstance, USER_ID + 1);

        Player firstPlayer = playerMapper.selectById(PLAYER_ID);
        Player secondPlayer = playerMapper.selectById(otherPlayerId);
        JsonNode firstDecision = objectMapper.readTree(firstPlayer.getDecisionHistoryJson()).get(0);
        JsonNode secondDecision = objectMapper.readTree(secondPlayer.getDecisionHistoryJson()).get(0);
        assertEquals(0, firstDecision.path("choice_index").asInt());
        assertEquals("留在自己的路线 A", firstDecision.path("choice_label").asText());
        assertEquals(1, secondDecision.path("choice_index").asInt());
        assertEquals("选择自己的路线 B", secondDecision.path("choice_label").asText());
        assertEquals(6, objectMapper.readTree(firstPlayer.getStatsJson()).path("worldLineShift").asInt());
        assertEquals(-4, objectMapper.readTree(secondPlayer.getStatsJson()).path("worldLineShift").asInt());
        assertEquals(1, playerStoryLogMapper.selectCount(new QueryWrapper<PlayerStoryLog>()
                .eq("player_id", PLAYER_ID).eq("event_key", eventKey)));
        assertEquals(1, playerStoryLogMapper.selectCount(new QueryWrapper<PlayerStoryLog>()
                .eq("player_id", otherPlayerId).eq("event_key", eventKey)));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> firstTimelineStories = (List<Map<String, Object>>) exploreService
                .getStoryLog(PLAYER_ID).get(0).get("stories");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> secondTimelineStories = (List<Map<String, Object>>) exploreService
                .getStoryLog(otherPlayerId).get(0).get("stories");
        assertEquals("留在自己的路线 A", firstTimelineStories.get(0).get("choice_label"));
        assertEquals("选择自己的路线 B", secondTimelineStories.get(0).get("choice_label"));
        JsonNode firstOutcome = objectMapper.valueToTree(firstTimelineStories.get(0).get("applied_outcomes"));
        JsonNode secondOutcome = objectMapper.valueToTree(secondTimelineStories.get(0).get("applied_outcomes"));
        assertEquals(2, firstOutcome.path("snapshot_version").asInt());
        assertEquals(2, firstOutcome.path("applied_rewards").path("worldLineShift").asInt());
        assertEquals(-3, secondOutcome.path("applied_rewards").path("worldLineShift").asInt());
        org.mockito.Mockito.verifyNoInteractions(broadcastService, factionService, questService);
    }

    @Test
    void explorationLocationMapAndStoryHistoryRejectPlayersOwnedByAnotherUser() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertEquals("jdbc:h2:mem:combat_persistence", connection.getMetaData().getURL());
        }

        long foreignPlayerId = PLAYER_ID + 1;
        insertSecondPlayer(foreignPlayerId, 10);
        jdbcTemplate.update("INSERT INTO player_story_logs (player_id, event_key, location_key, location_name, "
                        + "event_name, choice_index, choice_label, consequence_text) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                foreignPlayerId, "h2_private_story", "h2_test_zone", "内存测试区", "不应泄露的选择", 0,
                "仅属于另一位玩家的选择", "另一位玩家的私人后果。");

        String token = performLogin();
        ResponseEntity<String> ownStoryLog = performHttp(HttpMethod.GET,
                "/api/explore/story-log/" + PLAYER_ID, token, null);
        assertEquals(200, ownStoryLog.getStatusCode().value(), ownStoryLog.getBody());

        for (String path : List.of(
                "/api/explore/locations/" + foreignPlayerId,
                "/api/explore/map-nodes/" + foreignPlayerId,
                "/api/explore/story-log/" + foreignPlayerId)) {
            ResponseEntity<String> anonymous = performHttp(HttpMethod.GET, path, null, null);
            assertEquals(401, anonymous.getStatusCode().value(),
                    () -> "匿名请求必须先经过登录鉴权，路径=" + path + ", 响应=" + anonymous.getBody());

            ResponseEntity<String> response = performHttp(HttpMethod.GET, path, token, null);
            assertEquals(403, response.getStatusCode().value(),
                    () -> "不得读取非本人地点/地图/剧情进度，路径=" + path + ", 响应=" + response.getBody());
            assertTrue(response.getBody() == null || !response.getBody().contains("仅属于另一位玩家的选择"),
                    "拒绝响应不得包含另一位玩家的剧情内容");
        }
    }

    @Test
    void endingArchiveRequiresAuthenticationAndRejectsAnotherPlayersEndingData() throws Exception {
        long foreignPlayerId = PLAYER_ID + 1;
        insertSecondPlayer(foreignPlayerId, 10);
        when(endingService.checkEndings(PLAYER_ID)).thenReturn(List.of(
                Map.of("ending_key", "h2_private_ending", "name", "隔离测试结局")));

        String ownPath = "/api/endings/" + PLAYER_ID;
        String foreignPath = "/api/endings/" + foreignPlayerId;

        ResponseEntity<String> anonymous = performHttp(HttpMethod.GET, ownPath, null, null);
        assertEquals(401, anonymous.getStatusCode().value(),
                "匿名请求必须在控制器读取登录上下文前被 JWT 拦截器拒绝");

        String token = performLogin();
        ResponseEntity<String> ownResponse = performHttp(HttpMethod.GET, ownPath, token, null);
        assertEquals(200, ownResponse.getStatusCode().value(), ownResponse.getBody());
        assertTrue(ownResponse.getBody().contains("h2_private_ending"));

        ResponseEntity<String> foreignResponse = performHttp(HttpMethod.GET, foreignPath, token, null);
        assertEquals(403, foreignResponse.getStatusCode().value(), foreignResponse.getBody());
        assertFalse(foreignResponse.getBody().contains("h2_private_ending"));
        verify(endingService, never()).checkEndings(foreignPlayerId);
    }

    @Test
    void chapterPlayerRoutesRequireAuthenticationWhileChapterDefinitionsRemainPublic() throws Exception {
        long foreignPlayerId = PLAYER_ID + 1;
        insertSecondPlayer(foreignPlayerId, 10);
        ChapterProgressVO progress = new ChapterProgressVO();
        progress.setCurrentChapterKey("h2_chapter");
        when(chapterService.getCurrentChapter(PLAYER_ID)).thenReturn(progress);
        when(chapterService.checkChapterCompleted(PLAYER_ID)).thenReturn(false);
        when(chapterService.getResources(PLAYER_ID)).thenReturn(Map.of("coins", 10));
        when(chapterService.getAllMainChapters()).thenReturn(List.of());

        ResponseEntity<String> publicDefinitions = performHttp(
                HttpMethod.GET, "/api/chapters/all", null, null);
        assertEquals(200, publicDefinitions.getStatusCode().value(),
                "章节定义是公共静态内容，不应被玩家私有路由保护规则误伤");

        String token = performLogin();
        for (String route : List.of("current/", "completed/", "resources/")) {
            String ownPath = "/api/chapters/" + route + PLAYER_ID;
            String foreignPath = "/api/chapters/" + route + foreignPlayerId;

            ResponseEntity<String> anonymous = performHttp(HttpMethod.GET, ownPath, null, null);
            assertEquals(401, anonymous.getStatusCode().value(),
                    () -> "匿名请求必须被拒绝，路径=" + ownPath);

            ResponseEntity<String> own = performHttp(HttpMethod.GET, ownPath, token, null);
            assertEquals(200, own.getStatusCode().value(), own.getBody());

            ResponseEntity<String> foreign = performHttp(HttpMethod.GET, foreignPath, token, null);
            assertEquals(403, foreign.getStatusCode().value(), foreign.getBody());
        }

        ResponseEntity<String> anonymousAdvance = performHttp(HttpMethod.POST,
                "/api/chapters/advance", null,
                Map.of("playerId", PLAYER_ID, "chapterKey", "h2_chapter"));
        assertEquals(401, anonymousAdvance.getStatusCode().value(),
                "章节领奖/推进也必须先经过 JWT 拦截");
    }

    @Test
    void isolatedHttpStoryExplorationChoiceBattleRewardsAndAcknowledgementFormOneFlow() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertEquals("jdbc:h2:mem:combat_persistence", connection.getMetaData().getURL());
        }
        assertTrue(serverPort > 0 && serverPort != 3174,
                "全链路 HTTP 测试必须使用随机本地端口，不得连接开发端口 3174");

        String eventKey = "h2_story_battle_flow";
        String exploreRequestKey = "h2-flow-explore-request";
        String choiceRequestKey = "h2-flow-choice-request";
        jdbcTemplate.update("UPDATE players SET stats_json = ? WHERE id = ?",
                "{\"level\":5,\"maxHp\":100,\"hp\":100,\"stamina\":50,\"maxStamina\":50,"
                        + "\"attack\":1000,\"defense\":5,\"speed\":10,\"exp\":0,"
                        + "\"avatarRank\":\"F\",\"constellation\":\"demon_judge_of_fire\"}",
                PLAYER_ID);
        jdbcTemplate.update("UPDATE equipment SET base_stats_json = ? WHERE equipment_key = ?",
                "{\"attack\":5}", "h2_test_blade");
        jdbcTemplate.update("UPDATE locations SET event_rates_json = ?, unlock_conditions_json = ? "
                        + "WHERE location_key = ?",
                "{\"story\":100}", "{}", "h2_test_zone");
        jdbcTemplate.update("INSERT INTO exploration_events (event_key, event_type, location_key, name, "
                        + "description, weight, stamina_cost, repeatable, rewards_json, progress_effects_json, "
                        + "choices_json, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                eventKey, "story", "h2_test_zone", "铁王的抉择", "你被要求屈膝。", 1, 5, 0,
                "{\"coins\":2}",
                "{}",
                "[{\"label\":\"拒绝下跪\",\"consequence_text\":\"钢铁化投影挡在你面前。\","
                        + "\"risks\":{\"damagePercent\":15,\"monsterKey\":\"iron_king_trial\","
                        + "\"battleName\":\"钢铁化试炼\",\"battleDescription\":\"迎战或撤退。\"}}]",
                1);
        when(titleService.evaluateAndUnlockTitles(PLAYER_ID, null)).thenReturn(List.of());

        Monster trial = testMonster();
        trial.setMonsterKey("iron_king_trial");
        trial.setName("钢铁化投影");
        when(monsterService.getMonsterEntity("iron_king_trial")).thenReturn(trial);

        if (Boolean.getBoolean("reader.e2e.live-ui")) {
            seedLiveEdgeOnboardingScenario();
            seedLiveEdgeRankAndSkillProgression(eventKey);
            runLiveEdgeCharacterCreationFlow();
            assertLiveEdgeCharacterCreationState();
            runLiveEdgeStoryCombatFlow();
            assertLiveEdgeStoryCombatState();
            return;
        }

        String token = performLogin();
        ResponseEntity<String> exploreResponse = performHttp(HttpMethod.POST, "/api/explore/start", token,
                Map.of("playerId", PLAYER_ID,
                        "locationKey", "h2_test_zone",
                        "requestKey", exploreRequestKey));
        assertTrue(exploreResponse.getStatusCode().is2xxSuccessful());
        JsonNode explored = objectMapper.readTree(exploreResponse.getBody()).path("data");
        assertEquals("story", explored.path("result_type").asText());
        assertEquals(eventKey, explored.path("result").path("event_key").asText());
        assertEquals(5, explored.path("stamina_cost_paid").asInt());
        assertEquals(45, explored.path("player").path("stats").path("stamina").asInt());
        assertResourceTransactionFrom("explore", eventKey, "currency", "coins", 2, 10, 12);
        assertResourceTransactionFrom("explore", eventKey, "energy", "stamina", -5, 50, 45);
        String storyInstanceKey = explored.path("story_instance_key").asText();
        assertNotNull(storyInstanceKey);

        ResponseEntity<String> choiceResponse = performHttp(HttpMethod.POST, "/api/explore/choose", token,
                Map.of("playerId", PLAYER_ID,
                        "eventKey", eventKey,
                        "choiceIndex", 0,
                        "requestKey", choiceRequestKey,
                        "storyInstanceKey", storyInstanceKey));
        assertTrue(choiceResponse.getStatusCode().is2xxSuccessful());
        JsonNode choice = objectMapper.readTree(choiceResponse.getBody()).path("data");
        JsonNode pendingBattle = choice.path("pending_battle");
        assertEquals("拒绝下跪", choice.path("choice_label").asText());
        assertEquals(15, choice.path("applied_risks").path("damage").asInt());
        assertResourceTransactionFrom("story_choice", eventKey, "vitality", "hp", -15, 100, 85);
        assertEquals("iron_king_trial", pendingBattle.path("monster_key").asText());
        String battleRequestKey = pendingBattle.path("request_key").asText();
        assertNotNull(battleRequestKey);
        assertEquals(85, objectMapper.readTree(playerMapper.selectById(PLAYER_ID).getStatsJson())
                .path("hp").asInt());

        ResponseEntity<String> pendingResponse = performHttp(
                HttpMethod.GET, "/api/explore/pending-battles/" + PLAYER_ID, token, null);
        assertTrue(pendingResponse.getStatusCode().is2xxSuccessful());
        JsonNode pendingBattles = objectMapper.readTree(pendingResponse.getBody())
                .path("data").path("pending_battles");
        assertEquals(1, pendingBattles.size());
        assertEquals(battleRequestKey, pendingBattles.get(0).path("request_key").asText());

        CombatResultVO first = performHttpCombatStart(token, "iron_king_trial", battleRequestKey);
        assertEquals("win", first.getResult());
        assertEquals("iron_king_trial", first.getMonster().get("monsterKey"));
        assertEquals(2, first.getDrops().size());
        assertTrue(first.getDrops().stream().anyMatch(drop -> "h2_test_herb".equals(drop.get("itemKey"))));
        assertTrue(first.getDrops().stream()
                .anyMatch(drop -> "h2_test_blade".equals(drop.get("equipmentKey"))));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM battle_logs WHERE player_id = ?",
                Integer.class, PLAYER_ID));
        assertEquals(5, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? AND source_type = ?",
                Integer.class, PLAYER_ID, "combat"),
                "重放真实后端战斗不得重复记账");
        assertEquals(5, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? AND source_type = ?",
                Integer.class, PLAYER_ID, "combat"));
        int playerLogCountAfterBattle = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_logs WHERE player_id = ?", Integer.class, PLAYER_ID);
        assertTrue(playerLogCountAfterBattle >= 3,
                "探索、故事选择、战斗与奖励都应写入历程");

        CombatResultVO replay = performHttpCombatStart(token, "iron_king_trial", battleRequestKey);
        assertEquals(first.getDrops(), replay.getDrops());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM battle_logs WHERE player_id = ?",
                Integer.class, PLAYER_ID));
        assertEquals(playerLogCountAfterBattle,
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_logs WHERE player_id = ?",
                        Integer.class, PLAYER_ID),
                "重放战斗请求不得重复追加历程");
        assertEquals(5, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? AND source_type = ?",
                Integer.class, PLAYER_ID, "combat"),
                "重放战斗请求不得重复记资源流水");

        ResponseEntity<String> acknowledgeResponse = performHttp(
                HttpMethod.POST, "/api/explore/pending-battles/" + PLAYER_ID + "/ack", token,
                Map.of("requestKey", battleRequestKey));
        assertTrue(acknowledgeResponse.getStatusCode().is2xxSuccessful());
        assertTrue(playerLogService.findPendingBattleEncounters(PLAYER_ID).isEmpty());

        ResponseEntity<String> equipResponse = performHttp(HttpMethod.POST, "/api/equipment/equip", token,
                Map.of("playerId", PLAYER_ID, "equipmentKey", "h2_test_blade"));
        assertTrue(equipResponse.getStatusCode().is2xxSuccessful());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT equipped FROM player_equipment WHERE player_id = ? AND equipment_key = ?",
                Integer.class, PLAYER_ID, "h2_test_blade"));

        ResponseEntity<String> playerResponse = performHttp(
                HttpMethod.GET, "/api/player/" + PLAYER_ID, token, null);
        assertTrue(playerResponse.getStatusCode().is2xxSuccessful());
        JsonNode projectedPlayer = objectMapper.readTree(playerResponse.getBody()).path("data");
        assertTrue(projectedPlayer.path("stats").path("combatProjection").asBoolean());
        assertEquals(1013, projectedPlayer.path("stats").path("attack").asInt(),
                "等级5的既有+8成长与战斗掉落装备+5应同时进入玩家详情");
        assertEquals(8, projectedPlayer.path("stats").path("combatBonusSources")
                .path("levelGrowth").path("attack").asInt());
        assertEquals(5, projectedPlayer.path("stats").path("combatBonusSources")
                .path("equipment").path("attack").asInt());
    }

    @Test
    void seededRuinedStationStoryAndBossClueAdvanceRankRequirementsOverHttp() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertEquals("jdbc:h2:mem:combat_persistence", connection.getMetaData().getURL());
        }
        assertTrue(serverPort > 0 && serverPort != 3174,
                "种子路线验收必须使用隔离 H2 随机端口，不得连接开发端口 3174");

        JsonNode seededLocation = findExploreSeedRow("location", "ruined_station");
        JsonNode seededStory = findExploreSeedRow("event", "station_first_trace");
        JsonNode seededClue = findExploreSeedRow("event", "station_boss_clue");
        assertEquals("ruined_station", seededStory.path("location_key").asText());
        assertEquals("ruined_station", seededClue.path("location_key").asText());
        assertEquals(1, seededLocation.path("is_default").asInt(),
                "废弃车站应是新角色默认地点");

        jdbcTemplate.update("INSERT INTO locations (location_key, name, description, unlock_conditions_json, "
                        + "event_rates_json, min_level, danger_level, recommended_rank, is_default) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                seededLocation.path("location_key").asText(), seededLocation.path("name").asText(),
                seededLocation.path("description").asText(), seedJson(seededLocation, "unlock_conditions", "{}"),
                seedJson(seededLocation, "event_rates", "{}"), seededLocation.path("min_level").asInt(),
                seededLocation.path("danger_level").asInt(),
                seededLocation.path("recommended_rank").asText(), seededLocation.path("is_default").asInt());

        insertRankConfig("E", "剧本幸存者", "E级·剧本幸存者", 2, "D", "[]", "{}");
        insertRankConfig("D", "频道记录者", "D级·频道记录者", 3, "C",
                "[{\"type\":\"levelMin\",\"value\":6,\"label\":\"等级达到6\"},"
                        + "{\"type\":\"storyEventsTriggeredMin\",\"value\":1,\"label\":\"触发至少1次主线事件\"},"
                        + "{\"type\":\"bossClue\",\"bossKey\":\"station_keeper\",\"count\":1,"
                        + "\"label\":\"发现至少1条看门人线索\"}]", "{}");
        insertRankConfig("C", "剧本执行者", "C级·剧本执行者", 4, null, "[]", "{}");
        jdbcTemplate.update("UPDATE players SET current_location = ?, stats_json = ?, stage_progress_json = ?, "
                        + "story_fragments = ? WHERE id = ?",
                "ruined_station",
                "{\"level\":6,\"maxHp\":100,\"hp\":100,\"stamina\":50,\"maxStamina\":50,"
                        + "\"attack\":10,\"defense\":5,\"speed\":3,\"exp\":0,\"avatarRank\":\"E\","
                        + "\"channelHeat\":0,\"worldLineShift\":0,\"constellation\":\"morning_star\"}",
                "{\"storyEventsTriggered\":[],\"sideEventsTriggered\":[],\"bossClues\":{},"
                        + "\"storyPity\":0,\"explorationsByLocation\":{}}",
                0, PLAYER_ID);

        String token = performLogin();
        JsonNode initialRank = getRankProgress(token);
        assertEquals(0, rankRequirement(initialRank, "storyEventsTriggeredMin").path("current").asInt());
        assertEquals(0, rankRequirement(initialRank, "bossClue").path("current").asInt());
        assertFalse(initialRank.path("canRankUp").asBoolean());

        insertExploreSeedEvent(seededStory);
        ResponseEntity<String> storyExplore = performHttp(HttpMethod.POST, "/api/explore/start", token,
                Map.of("playerId", PLAYER_ID, "locationKey", "ruined_station",
                        "requestKey", "seeded-station-story-route"));
        assertTrue(storyExplore.getStatusCode().is2xxSuccessful(), storyExplore.getBody());
        JsonNode storyData = objectMapper.readTree(storyExplore.getBody()).path("data");
        assertEquals("station_first_trace", storyData.path("result").path("event_key").asText());
        assertEquals("story", storyData.path("result_type").asText());
        ResponseEntity<String> choice = performHttp(HttpMethod.POST, "/api/explore/choose", token,
                Map.of("playerId", PLAYER_ID,
                        "eventKey", "station_first_trace",
                        "choiceIndex", 0,
                        "requestKey", "seeded-station-story-choice",
                        "storyInstanceKey", storyData.path("story_instance_key").asText()));
        assertTrue(choice.getStatusCode().is2xxSuccessful(), choice.getBody());

        JsonNode afterStory = getRankProgress(token);
        assertEquals(1, rankRequirement(afterStory, "storyEventsTriggeredMin").path("current").asInt());
        assertEquals(0, rankRequirement(afterStory, "bossClue").path("current").asInt());
        assertFalse(afterStory.path("canRankUp").asBoolean(),
                "只有主线事件完成时，缺少首领线索仍不能晋升");

        insertExploreSeedEvent(seededClue);
        ResponseEntity<String> clueExplore = performHttp(HttpMethod.POST, "/api/explore/start", token,
                Map.of("playerId", PLAYER_ID, "locationKey", "ruined_station",
                        "requestKey", "seeded-station-boss-clue-route"));
        assertTrue(clueExplore.getStatusCode().is2xxSuccessful(), clueExplore.getBody());
        JsonNode clueData = objectMapper.readTree(clueExplore.getBody()).path("data");
        assertEquals("station_boss_clue", clueData.path("result").path("event_key").asText());
        assertEquals("station_keeper", clueData.path("progress_effects").path("bossClueKey").asText());

        JsonNode readyRank = getRankProgress(token);
        assertEquals(1, rankRequirement(readyRank, "storyEventsTriggeredMin").path("current").asInt());
        assertEquals(1, rankRequirement(readyRank, "bossClue").path("current").asInt());
        assertTrue(readyRank.path("canRankUp").asBoolean());
        JsonNode persistedProgress = objectMapper.readTree(
                playerMapper.selectById(PLAYER_ID).getStageProgressJson());
        assertTrue(persistedProgress.path("storyEventsTriggered").toString().contains("station_first_trace"));
        assertEquals(1, persistedProgress.path("bossClues").path("station_keeper").asInt());
    }

    @Test
    void insufficientStaminaRollsBackRecoveryAndAllowsTheSameRequestKeyToRetry() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertEquals("jdbc:h2:mem:combat_persistence", connection.getMetaData().getURL());
        }

        String originalStats = "{\"level\":1,\"maxHp\":100,\"hp\":100,\"stamina\":10,"
                + "\"maxStamina\":50,\"attack\":10,\"defense\":5,\"speed\":3,"
                + "\"exp\":0,\"avatarRank\":\"F\",\"isResting\":false}";
        jdbcTemplate.update("UPDATE players SET current_location = ?, stats_json = ?, stage_progress_json = ? "
                        + "WHERE id = ?",
                "h2_test_zone", originalStats, "{}", PLAYER_ID);
        jdbcTemplate.update("INSERT INTO exploration_events (event_key, event_type, location_key, name, "
                        + "description, weight, stamina_cost, repeatable, required_conditions_json, "
                        + "rewards_json, progress_effects_json, choices_json, log_template, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "h2_stamina_rollback", "resource", "h2_test_zone", "体力回滚验证", "仅用于隔离事务测试。",
                1, 5, 1, "{}", "{}", "{}", "[]", "完成了隔离事务验证。", 1);

        int[] recoveryCalls = {0};
        when(recoveryService.applyRecovery(any(Player.class))).thenAnswer(invocation -> {
            Player lockedPlayer = invocation.getArgument(0);
            Map<String, Object> recoveredStats = objectMapper.readValue(lockedPlayer.getStatsJson(),
                    new TypeReference<LinkedHashMap<String, Object>>() {});
            recoveryCalls[0] += 1;
            recoveredStats.put("stamina", recoveryCalls[0] == 1 ? 3 : 50);
            recoveredStats.put("exp", 777);
            lockedPlayer.setStatsJson(objectMapper.writeValueAsString(recoveredStats));
            assertEquals(1, playerMapper.updateById(lockedPlayer));
            return recoveredStats;
        });

        assertThrows(BusinessException.class, () -> exploreService.startExplore(
                PLAYER_ID, "h2_test_zone", "h2-stamina-rollback-retry", USER_ID));

        assertEquals(originalStats, playerMapper.selectById(PLAYER_ID).getStatsJson(),
                "体力不足异常应回滚恢复服务在同一事务内写入的体力与经验");
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_logs WHERE player_id = ?", Integer.class, PLAYER_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ?",
                Integer.class, PLAYER_ID));

        Map<String, Object> retry = exploreService.startExplore(
                PLAYER_ID, "h2_test_zone", "h2-stamina-rollback-retry", USER_ID);

        assertEquals(false, retry.get("request_replayed"),
                "失败请求不应留下回执；相同请求键恢复后应执行一次新探索");
        assertEquals("h2_stamina_rollback", ((Map<?, ?>) retry.get("result")).get("event_key"));
        assertEquals(5, ((Number) retry.get("stamina_cost_paid")).intValue());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_logs WHERE player_id = ?", Integer.class, PLAYER_ID));
    }

    private JsonNode findExploreSeedRow(String rowType, String key) throws IOException {
        try (var input = getClass().getResourceAsStream("/db/seed_explore.json")) {
            assertNotNull(input, "找不到正式探索 JSON 种子");
            for (JsonNode row : objectMapper.readTree(input)) {
                if (rowType.equals(row.path("type").asText())
                        && ("location".equals(rowType)
                            ? key.equals(row.path("location_key").asText())
                            : key.equals(row.path("event_key").asText()))) {
                    return row;
                }
            }
        }
        throw new AssertionError("正式探索 JSON 种子缺少 " + rowType + ": " + key);
    }

    private Map<String, Object> drawExpectedStory(long playerId, long userId,
                                                  String expectedEventKey, String requestKey) {
        Map<String, Object> draw = exploreService.startExplore(playerId, "demon_gate", requestKey, userId);
        assertEquals("story", draw.get("result_type"));
        Map<?, ?> event = (Map<?, ?>) draw.get("result");
        assertEquals(expectedEventKey, event.get("event_key"));
        assertEquals(false, draw.get("request_replayed"));
        assertTrue(draw.get("story_instance_key") instanceof String);
        return draw;
    }

    private String seedJson(JsonNode row, String key, String fallback) {
        JsonNode value = row.get(key);
        return value == null || value.isNull() ? fallback : value.toString();
    }

    private void insertExploreSeedEvent(JsonNode event) {
        jdbcTemplate.update("INSERT INTO exploration_events (event_key, event_type, stage_key, location_key, "
                        + "name, description, weight, stamina_cost, repeatable, required_conditions_json, "
                        + "rewards_json, risks_json, progress_effects_json, choices_json, log_template, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                event.path("event_key").asText(), event.path("event_type").asText(),
                event.hasNonNull("stage_key") ? event.path("stage_key").asText() : null,
                event.path("location_key").asText(), event.path("name").asText(),
                event.path("description").asText(), event.path("weight").asInt(),
                event.path("stamina_cost").asInt(), event.path("repeatable").asInt(),
                seedJson(event, "required_conditions", "{}"), seedJson(event, "rewards", "{}"),
                seedJson(event, "risks_json", "{}"), seedJson(event, "progress_effects", "{}"),
                seedJson(event, "choices", "[]"), event.path("log_template").asText(), 1);
    }

    private JsonNode getRankProgress(String token) throws Exception {
        ResponseEntity<String> response = performHttp(HttpMethod.GET,
                "/api/avatar-rank/" + PLAYER_ID, token, null);
        assertTrue(response.getStatusCode().is2xxSuccessful(), response.getBody());
        return objectMapper.readTree(response.getBody()).path("data");
    }

    private JsonNode rankRequirement(JsonNode rankInfo, String type) {
        for (JsonNode requirement : rankInfo.path("requirements")) {
            if (type.equals(requirement.path("type").asText())) return requirement;
        }
        throw new AssertionError("位阶进度缺少条件: " + type);
    }

    private void runLiveEdgeStoryCombatFlow() throws Exception {
        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path repositoryRoot = Files.isDirectory(workingDirectory.resolve("frontend-player"))
                ? workingDirectory : workingDirectory.getParent();
        assertNotNull(repositoryRoot, "无法定位仓库根目录");
        Path frontendDirectory = repositoryRoot.resolve("frontend-player");
        Path script = frontendDirectory.resolve("e2e/story-combat-live.mjs");
        assertTrue(Files.isRegularFile(script), "缺少真实后端 Edge 联调脚本: " + script);

        ProcessBuilder processBuilder = new ProcessBuilder("node", script.toString())
                .directory(frontendDirectory.toFile())
                .redirectErrorStream(true);
        Map<String, String> environment = processBuilder.environment();
        environment.put("READER_E2E_BASE_URL", "http://localhost:5173");
        environment.put("READER_E2E_LIVE_BACKEND_URL", "http://localhost:" + serverPort);
        environment.put("READER_E2E_TEST_USERNAME", TEST_USERNAME);
        environment.put("READER_E2E_TEST_PASSWORD", TEST_PASSWORD);

        Process process = processBuilder.start();
        CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "读取 Edge 联调输出失败: " + e.getMessage();
            }
        });
        boolean completed = process.waitFor(120, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            process.waitFor();
        }
        String output = outputFuture.get(10, TimeUnit.SECONDS);
        assertTrue(completed, "Edge 联调超过 120 秒\n" + output);
        assertEquals(0, process.exitValue(), "Edge 联调失败\n" + output);
    }

    private void runLiveEdgeCharacterCreationFlow() throws Exception {
        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path repositoryRoot = Files.isDirectory(workingDirectory.resolve("frontend-player"))
                ? workingDirectory : workingDirectory.getParent();
        assertNotNull(repositoryRoot, "无法定位仓库根目录");
        Path frontendDirectory = repositoryRoot.resolve("frontend-player");
        Path script = frontendDirectory.resolve("e2e/character-creation-live.mjs");
        assertTrue(Files.isRegularFile(script), "缺少 Edge 注册/建角联调脚本: " + script);

        ProcessBuilder processBuilder = new ProcessBuilder("node", script.toString())
                .directory(frontendDirectory.toFile())
                .redirectErrorStream(true);
        Map<String, String> environment = processBuilder.environment();
        environment.put("READER_E2E_BASE_URL", "http://localhost:5173");
        environment.put("READER_E2E_LIVE_BACKEND_URL", "http://localhost:" + serverPort);

        Process process = processBuilder.start();
        CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "读取 Edge 注册/建角联调输出失败: " + e.getMessage();
            }
        });
        boolean completed = process.waitFor(120, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            process.waitFor();
        }
        String output = outputFuture.get(10, TimeUnit.SECONDS);
        assertTrue(completed, "Edge 注册/建角联调超过 120 秒\n" + output);
        assertEquals(0, process.exitValue(), "Edge 注册/建角联调失败\n" + output);
    }

    private void seedLiveEdgeOnboardingScenario() {
        jdbcTemplate.update("INSERT INTO locations (location_key, name, description, unlock_conditions_json, "
                        + "event_rates_json, min_level, danger_level, recommended_rank, is_default) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "ruined_station", "废弃车站", "新读者的初次观测场景。", "{}", "{\"resource\":100}",
                1, 1, "F", 1);
        jdbcTemplate.update("INSERT INTO exploration_events (event_key, event_type, location_key, name, "
                        + "description, weight, stamina_cost, repeatable, required_conditions_json, "
                        + "rewards_json, choices_json, log_template, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "h2_edge_first_explore", "resource", "ruined_station", "站台初次采集", "第一次踏入废弃车站。",
                1, 5, 1, "{}", "{\"coins\":5}", "[]", "你在废弃车站完成了第一次探索。", 1);
    }

    private void seedLiveEdgeRankAndSkillProgression(String storyEventKey) {
        insertRankConfig("E", "剧本幸存者", "E级·剧本幸存者", 2, "D", "[]", "{}");
        insertRankConfig("D", "频道记录者", "D级·频道记录者", 3, "C",
                "[{\"type\":\"levelMin\",\"value\":6,\"label\":\"等级 6\"},"
                        + "{\"type\":\"storyEventsTriggeredMin\",\"value\":1,\"label\":\"主线事件\"},"
                        + "{\"type\":\"bossClue\",\"bossKey\":\"station_keeper\",\"count\":1,"
                        + "\"label\":\"车站看门人线索\"}]",
                "{\"stats\":{\"attack\":5,\"defense\":5},\"channelHeat\":20,"
                        + "\"log\":\"晋升为频道记录者。\"}");
        insertRankConfig("C", "剧本执行者", "C级·剧本执行者", 4, null, "[]", "{}");

        jdbcTemplate.update("UPDATE players SET coins = ?, story_fragments = ?, stats_json = ?, "
                        + "stage_progress_json = ? WHERE id = ?",
                50, 0,
                "{\"level\":2,\"maxHp\":100,\"hp\":100,\"stamina\":50,\"maxStamina\":50,"
                        + "\"attack\":1000,\"defense\":5,\"speed\":10,\"critRate\":0.0,"
                        + "\"critDamage\":1.5,\"exp\":110,\"maxExp\":400,\"freePoints\":113,"
                        + "\"avatarRank\":\"E\",\"avatarRankName\":\"剧本幸存者\","
                        + "\"constellation\":\"demon_judge_of_fire\"}",
                "{}", PLAYER_ID);
        jdbcTemplate.update("INSERT INTO skills (skill_key, name, type, rarity, description, "
                        + "unlock_conditions_json, effects_json, cost_json, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "stable_breathing", "稳定呼吸", "passive", "common", "让化身在持续探索中稳定星流。",
                "{}", "{\"maxStamina\":5,\"staminaCostReduce\":0.03}", "{\"coins\":50}", 1);
        skillService.unlockSkill(PLAYER_ID, "stable_breathing", USER_ID, "h2-stable-breathing-unlock");

        jdbcTemplate.update("INSERT INTO exploration_events (event_key, event_type, location_key, name, "
                        + "description, weight, stamina_cost, repeatable, required_conditions_json, rewards_json, "
                        + "progress_effects_json, choices_json, log_template, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "h2_e_to_d_trial", "resource", "h2_test_zone", "看门人留下的观测记录",
                "记录中有晋升所需的经验，也标记着车站看门人的线索。", 1, 5, 0,
                "{\"required_rank\":\"E\",\"required_events\":[\"" + storyEventKey + "\"]}",
                "{\"exp\":1593}",
                "{\"bossClueKey\":\"station_keeper\",\"bossClueAdd\":1}",
                "[]", "你整理了看门人留下的观测记录。", 1);
        jdbcTemplate.update("INSERT INTO exploration_events (event_key, event_type, location_key, name, "
                        + "description, weight, stamina_cost, repeatable, required_conditions_json, rewards_json, "
                        + "progress_effects_json, choices_json, log_template, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "h2_stamina_cost_remainder_trial", "resource", "h2_test_zone", "折扣余量复测点",
                "只有完成 D 级晋升后才能重复观测，用于验证技能折扣余量持久化。", 1, 5, 1,
                "{\"required_rank\":\"D\"}", "{}", "{}", "[]",
                "你完成了一次体力折扣复测。", 1);
        jdbcTemplate.update("UPDATE locations SET event_rates_json = ?, unlock_conditions_json = ? "
                        + "WHERE location_key = ?",
                "{\"story\":100,\"resource\":100}", "{}", "h2_test_zone");
        when(playerEffectService.applyChannelHeatGainRate(PLAYER_ID, 20)).thenReturn(20);
    }

    private void assertLiveEdgeCharacterCreationState() {
        Long createdUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, "h2-edge-onboarding");
        assertNotNull(createdUserId, "Edge 注册应在隔离 H2 中创建账号");
        Long createdPlayerId = jdbcTemplate.queryForObject(
                "SELECT id FROM players WHERE user_id = ? AND player_name = ?",
                Long.class, createdUserId, "H2观测者角色");
        assertNotNull(createdPlayerId, "Edge 建角应把新角色绑定到本次注册账号");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM players WHERE user_id = ? AND player_name = ?",
                Integer.class, createdUserId, "H2观测者角色"),
                "Edge 建角应把新角色绑定到本次注册账号");
        Player createdPlayer = playerMapper.selectById(createdPlayerId);
        JsonNode stats;
        try {
            stats = objectMapper.readTree(createdPlayer.getStatsJson());
        } catch (Exception exception) {
            throw new AssertionError("新角色属性 JSON 应有效", exception);
        }
        assertEquals("demon_judge_of_fire", stats.path("constellation").asText(),
                "首次选择背后星应持久化至新角色");
        assertEquals(45, stats.path("stamina").asInt(), "首次探索应只扣除 5 点体力");
        assertEquals("ruined_station", createdPlayer.getCurrentLocation());
        assertEquals(5, createdPlayer.getCoins(), "首次探索资源奖励应写入角色余额");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? "
                        + "AND source_type = 'explore' AND resource_key = 'stamina' AND amount = -5 "
                        + "AND balance_before = 50 AND balance_after = 45",
                Integer.class, createdPlayerId), "首次探索体力扣除应记录流水");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_logs WHERE player_id = ? AND message LIKE ?",
                Integer.class, createdPlayerId, "%第一次探索%"), "首次探索结果应持久化为历程");
    }

    private void assertLiveEdgeStoryCombatState() throws Exception {
        Player persisted = playerMapper.selectById(PLAYER_ID);
        JsonNode stats = objectMapper.readTree(persisted.getStatsJson());
        assertEquals(6, stats.path("stamina").asInt(), "两次主流程探索与七次技能复测的体力都应持久化");
        assertEquals(100, stats.path("hp").asInt(), "升至 6 级后升级恢复应将生命回满");
        assertEquals(7, persisted.getCoins(), "技能学习已扣除 50 星币，探索和战斗奖励仍应入账");
        assertEquals(6, stats.path("level").asInt(), "试炼经验应将角色推进至等级 6");
        assertEquals(2515, stats.path("exp").asInt(), "E 级经验倍率与怪物经验应计入总经验");
        assertEquals("D", stats.path("avatarRank").asText(), "角色应通过真实位阶服务晋升至 D");
        assertEquals(225, stats.path("freePoints").asInt(), "等级成长与 D 级晋升点数均应保存");
        assertEquals(20, stats.path("channelHeat").asInt(), "D 级位格奖励应写入频道热度");
        JsonNode stageProgress = objectMapper.readTree(persisted.getStageProgressJson());
        assertEquals(1, stageProgress.path("bossClues").path("station_keeper").asInt());
        assertEquals(0.05, stageProgress.path("skillStaminaSavingsRemainder").asDouble(), 1.0e-8,
                "7 次 5 点探索在 3% 折扣下应将 0.05 点余量持久化");
        assertTrue(stageProgress.path("storyEventsTriggered").toString().contains("h2_story_battle_flow"));
        assertResourceTransactionFrom("explore", "h2_story_battle_flow",
                "currency", "coins", 2, 0, 2);
        assertResourceTransactionFrom("explore", "h2_story_battle_flow",
                "energy", "stamina", -5, 50, 45);
        assertResourceTransactionFrom("explore", "h2_e_to_d_trial",
                "energy", "stamina", -5, 45, 40);
        assertResourceTransactionFrom("story_choice", "h2_story_battle_flow",
                "vitality", "hp", -15, 100, 85);
        assertResourceTransactionFrom("avatar_rank_up", "E->D",
                "progress", "channel_heat", 20, 0, 20);
        List<Map<String, Object>> staminaDiscountTransactions = jdbcTemplate.queryForList(
                "SELECT amount, balance_before, balance_after FROM player_resource_transactions "
                        + "WHERE player_id = ? AND source_type = 'explore' "
                        + "AND source_id = 'h2_stamina_cost_remainder_trial' ORDER BY id",
                PLAYER_ID);
        assertEquals(7, staminaDiscountTransactions.size(), "每次复测都应各自产生一笔体力流水");
        long staminaBalance = 40;
        for (int index = 0; index < staminaDiscountTransactions.size(); index++) {
            Map<String, Object> transaction = staminaDiscountTransactions.get(index);
            long expectedCost = index < 6 ? -5L : -4L;
            assertEquals(expectedCost, ((Number) transaction.get("amount")).longValue(),
                    "前六次各付 5 点，第七次累计折扣后付 4 点");
            assertEquals(staminaBalance, ((Number) transaction.get("balance_before")).longValue());
            staminaBalance += expectedCost;
            assertEquals(staminaBalance, ((Number) transaction.get("balance_after")).longValue());
        }
        assertEquals(6, staminaBalance, "连续七次探索后应剩余 6 点体力");
        assertEquals(1, playerStoryLogMapper.selectCount(new QueryWrapper<PlayerStoryLog>()
                .eq("player_id", PLAYER_ID).eq("event_key", "h2_story_battle_flow")));
        assertEquals(10, inventoryMapper.selectOne(new QueryWrapper<com.huazhenghai.readergame.entity.PlayerInventory>()
                .eq("player_id", PLAYER_ID).eq("item_key", "h2_test_herb")).getQuantity());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_equipment WHERE player_id = ? AND equipment_key = ?",
                Integer.class, PLAYER_ID, "h2_test_blade"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT equipped FROM player_equipment WHERE player_id = ? AND equipment_key = ?",
                Integer.class, PLAYER_ID, "h2_test_blade"),
                "Edge 装备面板的穿戴操作应写入隔离 H2");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT equipped FROM player_skills WHERE player_id = ? AND skill_key = ?",
                Integer.class, PLAYER_ID, "stable_breathing"),
                "Edge 技能图谱的装备操作应写入隔离 H2");
        Map<String, Object> skillBonus = skillService.calculateSkillBonus(PLAYER_ID);
        assertEquals(5, ((Number) skillBonus.get("maxStamina")).intValue());
        assertEquals(0.03, ((Number) skillBonus.get("staminaCostReduce")).doubleValue(), 1.0e-9);
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM battle_logs WHERE player_id = ?",
                Integer.class, PLAYER_ID));
        assertEquals(5, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? AND source_type = ?",
                Integer.class, PLAYER_ID, "combat"));
        assertTrue(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_logs WHERE player_id = ?",
                Integer.class, PLAYER_ID) >= 3);
        assertTrue(playerLogService.findPendingBattleEncounters(PLAYER_ID).isEmpty(),
                "浏览器确认后真实待战斗记录应清除");
    }

    @Test
    void storyRelationUsesTheCurrentPlayersH2TimelineAndPersistsItsOneHitAudit() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertEquals("jdbc:h2:mem:combat_persistence", connection.getMetaData().getURL());
        }

        String statsJson = "{\"level\":1,\"maxHp\":100,\"hp\":100,\"attack\":100,"
                + "\"defense\":5,\"speed\":10,\"critRate\":0.0,\"critDamage\":1.5,"
                + "\"exp\":0,\"avatarRank\":\"F\",\"constellation\":\"demon_judge_of_fire\"}";
        jdbcTemplate.update("UPDATE players SET stats_json = ?, stage_progress_json = ? WHERE id = ?",
                statsJson, "{\"sideEventsTriggered\":[\"gwangjin_side_boat\"]}", PLAYER_ID);

        Monster storyEater = testMonster();
        storyEater.setMonsterKey("bridge_hunger_entity");
        storyEater.setName("桥下的饥饿实体");
        storyEater.setStatsJson("{\"hp\":300,\"attack\":18,\"defense\":0,\"speed\":20}");
        storyEater.setRewardsJson("{}");
        storyEater.setDropsJson("{}");
        when(monsterService.getMonsterEntity("bridge_hunger_entity")).thenReturn(storyEater);

        CombatResultVO first = combatService.startCombat(
                PLAYER_ID, "bridge_hunger_entity", "h2-story-anchor-one-hit", USER_ID);

        assertEquals("win", first.getResult());
        List<Map<String, Object>> monsterActions = first.getRounds().stream()
                .flatMap(round -> round.getActions().stream())
                .filter(action -> "monster".equals(action.get("actor")))
                .toList();
        assertTrue(monsterActions.size() > 1);
        assertEquals(12, monsterActions.get(0).get("damage"));
        assertTrue(((String) monsterActions.get(0).get("suppressionNote")).contains("减伤 4 点"));
        assertEquals(16, monsterActions.get(1).get("damage"));
        assertFalse(monsterActions.get(1).containsKey("storyRelation"));

        String persistedRounds = jdbcTemplate.queryForObject(
                "SELECT rounds_json FROM battle_logs WHERE player_id = ? AND request_key = ?",
                String.class, PLAYER_ID, "h2-story-anchor-one-hit");
        JsonNode persistedFirstMonsterAction = objectMapper.readTree(persistedRounds).get(0)
                .path("actions").get(0);
        assertEquals(12, persistedFirstMonsterAction.path("damage").asInt());
        assertEquals("game_derived", persistedFirstMonsterAction.path("storyRelation")
                .path("sourceCategory").asText());
        assertEquals("gwangjin_side_boat", persistedFirstMonsterAction.path("storyRelation")
                .path("evidenceEventKey").asText());

        CombatResultVO replay = combatService.startCombat(
                PLAYER_ID, "bridge_hunger_entity", "h2-story-anchor-one-hit", USER_ID);
        assertEquals(objectMapper.valueToTree(first), objectMapper.valueToTree(replay));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM battle_logs WHERE player_id = ?", Integer.class, PLAYER_ID));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_logs WHERE player_id = ?", Integer.class, PLAYER_ID));
    }

    @Test
    void finalWallResonanceUsesPersonalStoryFlagAndPersistsItsFirstAttackAudit() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertEquals("jdbc:h2:mem:combat_persistence", connection.getMetaData().getURL());
        }

        String eventKey = "last_wall_echo_personal_story";
        String choiceRequestKey = "h2-final-wall-story-choice";
        String statsJson = "{\"level\":1,\"maxHp\":100,\"hp\":100,\"attack\":100,"
                + "\"defense\":5,\"speed\":10,\"critRate\":0.0,\"critDamage\":1.5,"
                + "\"exp\":0,\"avatarRank\":\"F\",\"constellation\":\"demon_judge_of_fire\"}";

        long otherPlayerId = PLAYER_ID + 1;
        insertSecondPlayer(otherPlayerId, 10);
        jdbcTemplate.update("INSERT INTO locations (location_key, name, unlock_conditions_json, "
                        + "event_rates_json, min_level, recommended_rank) VALUES (?, ?, ?, ?, ?, ?)",
                "demon_gate", "恶魔之门", "{}", "{\"story\":100}", 1, "F");
        insertExploreSeedEvent(findExploreSeedRow("event", eventKey));

        String completedFinaleProgress = "{\"storyEventsTriggered\":[\"ending_dream_continuation_choice\"]}";
        String continuedDecision = "[{\"event_key\":\"ending_dream_continuation_choice\","
                + "\"choice_index\":0}]";
        String frozenDecision = "[{\"event_key\":\"ending_dream_continuation_choice\","
                + "\"choice_index\":1}]";
        jdbcTemplate.update("UPDATE players SET current_location = ?, stats_json = ?, "
                        + "stage_progress_json = ?, story_flags_json = ?, decision_history_json = ? WHERE id = ?",
                "demon_gate", statsJson, completedFinaleProgress,
                "{\"ending_new_oldest_dream\":true}", continuedDecision, PLAYER_ID);
        jdbcTemplate.update("UPDATE players SET current_location = ?, stats_json = ?, "
                        + "stage_progress_json = ?, story_flags_json = ?, decision_history_json = ? WHERE id = ?",
                "demon_gate", statsJson, completedFinaleProgress,
                "{\"ending_world_frozen\":true}", frozenDecision, otherPlayerId);

        Monster echo = testMonster();
        echo.setMonsterKey("last_wall_dokkaebi_echo");
        echo.setName("终墙鬼怪之王回声");
        echo.setStatsJson("{\"hp\":350,\"attack\":18,\"defense\":0,\"speed\":5}");
        echo.setRewardsJson("{}");
        echo.setDropsJson("{}");
        when(monsterService.getMonsterEntity("last_wall_dokkaebi_echo")).thenReturn(echo);
        when(titleService.evaluateAndUnlockTitles(PLAYER_ID, null)).thenReturn(List.of());
        when(titleService.evaluateAndUnlockTitles(otherPlayerId, null)).thenReturn(List.of());

        Map<String, Object> storyDraw = drawExpectedStory(
                PLAYER_ID, USER_ID, eventKey, "h2-final-wall-story-draw");
        Map<?, ?> drawnStory = (Map<?, ?>) storyDraw.get("result");
        assertEquals(List.of("你选择接续梦境。终墙的光影没有把你的选择改写成原著人物的经历，只为你的时间线留下另一段回声。"),
                drawnStory.get("personal_contexts"));
        String storyInstanceKey = (String) storyDraw.get("story_instance_key");

        Map<String, Object> storyChoice = exploreService.makeChoice(
                PLAYER_ID, eventKey, 0, choiceRequestKey, storyInstanceKey, USER_ID);
        @SuppressWarnings("unchecked")
        Map<String, Object> pendingStoryBattle = (Map<String, Object>) storyChoice.get("pending_battle");
        assertEquals("last_wall_dokkaebi_echo", pendingStoryBattle.get("monster_key"));
        assertNotNull(pendingStoryBattle.get("request_key"));
        JsonNode storedStoryFlags = objectMapper.readTree(playerMapper.selectById(PLAYER_ID).getStoryFlagsJson());
        assertTrue(storedStoryFlags.path("ending_new_oldest_dream").asBoolean(),
                "终局结局旗标必须保留");
        assertTrue(storedStoryFlags.path("story_final_wall_echo_attuned").asBoolean(),
                "进入遭遇的选择必须先将个人战斗旗标写入玩家存档");

        Map<String, Object> otherStoryDraw = drawExpectedStory(
                otherPlayerId, USER_ID + 1, eventKey, "h2-final-wall-other-story-draw");
        Map<?, ?> otherDrawnStory = (Map<?, ?>) otherStoryDraw.get("result");
        assertEquals(List.of("你选择让世界停在此刻。这里不是世界恢复运转，而是终局留下的一段个人回声；它不会撤销你的结局选择。"),
                otherDrawnStory.get("personal_contexts"));
        Map<String, Object> observedChoice = exploreService.makeChoice(
                otherPlayerId, eventKey, 1, "h2-final-wall-observe-choice",
                (String) otherStoryDraw.get("story_instance_key"), USER_ID + 1);
        assertEquals("只记录回声，不介入原著之战", observedChoice.get("choice_label"));
        JsonNode otherPlayerFlagsAfterChoice = objectMapper.readTree(
                playerMapper.selectById(otherPlayerId).getStoryFlagsJson());
        assertTrue(otherPlayerFlagsAfterChoice.path("ending_world_frozen").asBoolean());
        assertTrue(otherPlayerFlagsAfterChoice.path("story_final_wall_echo_observed").asBoolean());
        assertFalse(otherPlayerFlagsAfterChoice.path("story_final_wall_echo_attuned").asBoolean(),
                "只记录回声不能获得个人战斗旗标");

        List<Map<String, Object>> pendingEncounters = playerLogService
                .findPendingBattleEncounters(PLAYER_ID);
        assertEquals(1, pendingEncounters.size());
        assertEquals("last_wall_dokkaebi_echo", pendingEncounters.get(0).get("monster_key"));

        Map<String, Object> replayedStoryChoice = exploreService.makeChoice(
                PLAYER_ID, eventKey, 0, choiceRequestKey, storyInstanceKey, USER_ID);
        assertEquals(true, replayedStoryChoice.get("request_replayed"));
        assertEquals(storyChoice.get("pending_battle"), replayedStoryChoice.get("pending_battle"));

        CombatResultVO first = combatService.startCombat(
                PLAYER_ID, "last_wall_dokkaebi_echo", "h2-final-wall-first-hit", USER_ID);

        assertEquals("win", first.getResult());
        List<Map<String, Object>> playerActions = first.getRounds().stream()
                .flatMap(round -> round.getActions().stream())
                .filter(action -> "player".equals(action.get("actor")))
                .toList();
        assertTrue(playerActions.size() > 2);
        assertEquals(108, playerActions.get(0).get("damage"));
        assertTrue(((String) playerActions.get(0).get("suppressionNote")).contains("额外造成 8 点"));
        assertFalse(playerActions.get(1).containsKey("storyRelation"),
                "个人终墙回响只加成这一场战斗的首次玩家攻击");
        assertEquals(100, playerActions.get(1).get("damage"));

        String persistedRounds = jdbcTemplate.queryForObject(
                "SELECT rounds_json FROM battle_logs WHERE player_id = ? AND request_key = ?",
                String.class, PLAYER_ID, "h2-final-wall-first-hit");
        JsonNode persistedFirstPlayerAction = objectMapper.readTree(persistedRounds).get(0)
                .path("actions").get(0);
        assertEquals(108, persistedFirstPlayerAction.path("damage").asInt());
        assertEquals("game_derived", persistedFirstPlayerAction.path("storyRelation")
                .path("sourceCategory").asText());
        assertEquals("last_wall_echo_personal_story", persistedFirstPlayerAction.path("storyRelation")
                .path("evidenceEventKey").asText());
        assertEquals("story_final_wall_echo_attuned", persistedFirstPlayerAction.path("storyRelation")
                .path("evidenceStoryFlag").asText());
        assertEquals(8, persistedFirstPlayerAction.path("storyRelation").path("damageBonus").asInt());

        CombatResultVO replay = combatService.startCombat(
                PLAYER_ID, "last_wall_dokkaebi_echo", "h2-final-wall-first-hit", USER_ID);
        assertEquals(objectMapper.valueToTree(first), objectMapper.valueToTree(replay));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM battle_logs WHERE player_id = ?", Integer.class, PLAYER_ID));
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_logs WHERE player_id = ?", Integer.class, PLAYER_ID));

        when(titleService.calculateTitleEffects(otherPlayerId)).thenReturn(Map.of());
        when(factionService.getFactionBuff(otherPlayerId)).thenReturn(Map.of());

        CombatResultVO otherTimelineBattle = combatService.startCombat(
                otherPlayerId, "last_wall_dokkaebi_echo", "h2-final-wall-other-player", USER_ID + 1);
        Map<String, Object> otherFirstPlayerAction = otherTimelineBattle.getRounds().stream()
                .flatMap(round -> round.getActions().stream())
                .filter(action -> "player".equals(action.get("actor")))
                .findFirst()
                .orElseThrow();
        assertEquals(100, otherFirstPlayerAction.get("damage"));
        assertFalse(otherFirstPlayerAction.containsKey("storyRelation"),
                "其他玩家没有选择该个人剧情时，不能继承首击加成");
    }

    private ResponseEntity<String> performHttp(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return testRestTemplate.exchange("http://localhost:" + serverPort + path,
                method, new HttpEntity<>(body, headers), String.class);
    }

    private CombatResultVO performHttpCombatStart(String token, String monsterKey, String requestKey)
            throws Exception {
        ResponseEntity<String> response = performHttp(HttpMethod.POST, "/api/combat/start", token,
                Map.of("playerId", PLAYER_ID, "monsterKey", monsterKey, "requestKey", requestKey));
        assertTrue(response.getStatusCode().is2xxSuccessful());
        JsonNode envelope = objectMapper.readTree(response.getBody());
        assertTrue(envelope.path("success").asBoolean());
        return objectMapper.treeToValue(envelope.path("data"), CombatResultVO.class);
    }

    private CombatResultVO performCombatStart(String token) throws Exception {
        return performCombatStart(token, "h2_test_beast", REQUEST_KEY);
    }

    private CombatResultVO performCombatStart(String token, String monsterKey, String requestKey) throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "playerId", PLAYER_ID,
                "monsterKey", monsterKey,
                "requestKey", requestKey));
        MvcResult response = mockMvc.perform(post("/api/combat/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode envelope = objectMapper.readTree(response.getResponse().getContentAsString());
        assertTrue(envelope.path("success").asBoolean());
        return objectMapper.treeToValue(envelope.path("data"), CombatResultVO.class);
    }

    private String performLogin() throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "usernameOrEmail", TEST_USERNAME,
                "password", TEST_PASSWORD));
        MvcResult response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode envelope = objectMapper.readTree(response.getResponse().getContentAsString());
        assertTrue(envelope.path("success").asBoolean());
        assertEquals(USER_ID, envelope.path("data").path("id").asLong());
        return envelope.path("data").path("token").asText();
    }

    private Monster testMonster() {
        Monster monster = new Monster();
        monster.setMonsterKey("h2_test_beast");
        monster.setName("内存测试兽");
        monster.setType("test");
        monster.setRarity("common");
        monster.setLocationKey("h2_test_zone");
        monster.setStatsJson("{\"hp\":1,\"attack\":0,\"defense\":0,\"speed\":1}");
        monster.setRewardsJson("{\"exp\":10,\"coins\":5,\"storyFragments\":2}");
        monster.setDropsJson("{\"items\":[{\"itemKey\":\"h2_test_herb\",\"quantity\":3,"
                + "\"dropRate\":1.0}],\"equipment\":[{\"equipmentKey\":\"h2_test_blade\","
                + "\"dropRate\":1.0}]}");
        monster.setNarrativeTagsJson("[]");
        monster.setEnabled(1);
        return monster;
    }
}
