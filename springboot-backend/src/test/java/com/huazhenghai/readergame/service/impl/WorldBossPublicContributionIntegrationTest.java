package com.huazhenghai.readergame.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.service.ChatService;
import com.huazhenghai.readergame.service.CombatService;
import com.huazhenghai.readergame.service.ExploreService;
import com.huazhenghai.readergame.service.NarrativeService;
import com.huazhenghai.readergame.service.RecoveryService;
import com.huazhenghai.readergame.service.WorldBossService;
import com.huazhenghai.readergame.vo.CombatStatsVO;
import com.huazhenghai.readergame.vo.WorldBossAttackResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** Verifies world-boss actions advance boss objectives, never a PK-only public board. */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:world_boss_public_contribution;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.ai-director.llm-api-key=",
        "app.scheduler.enabled=false"
})
@ActiveProfiles("test")
@Sql("/db/combat-persistence-test-schema.sql")
class WorldBossPublicContributionIntegrationTest {

    private static final long USER_ID = 73401L;
    private static final long PLAYER_ID = 84401L;
    private static final String BOSS_NO = "public-contribution-boss";
    private static final String PK_EVENT_KEY = "broadcast_pk_spark";
    private static final String AI_EVENT_KEY = "ai_boss-objective-test";

    @Autowired private WorldBossService worldBossService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private CombatService combatService;
    @MockitoBean private RecoveryService recoveryService;
    @MockitoBean private ChatService chatService;
    @MockitoBean private ExploreService exploreService;
    @MockitoBean private NarrativeService narrativeService;

    @BeforeEach
    void seedIsolatedBossAndPublicEvents() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            assertEquals("jdbc:h2:mem:world_boss_public_contribution",
                    connection.getMetaData().getURL());
        }

        jdbcTemplate.update("DELETE FROM broadcast_contributions WHERE event_key IN (?, ?)",
                PK_EVENT_KEY, AI_EVENT_KEY);
        jdbcTemplate.update("DELETE FROM broadcast_events WHERE event_key IN (?, ?)",
                PK_EVENT_KEY, AI_EVENT_KEY);
        jdbcTemplate.update("DELETE FROM world_boss_participation WHERE boss_no = ?", BOSS_NO);
        jdbcTemplate.update("DELETE FROM world_bosses WHERE boss_no = ?", BOSS_NO);
        jdbcTemplate.update("DELETE FROM player_logs WHERE player_id = ?", PLAYER_ID);
        jdbcTemplate.update("DELETE FROM players WHERE id = ?", PLAYER_ID);

        jdbcTemplate.update("INSERT INTO players (id, user_id, player_name, coins, story_fragments, stats_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                PLAYER_ID, USER_ID, "首领公共目标测试角色", 0, 0, "{\"hp\":100,\"constellation\":\"demon_judge_of_fire\"}");
        jdbcTemplate.update("INSERT INTO world_bosses "
                        + "(boss_key, boss_no, name, status, level, max_hp, current_hp, attack, defense, speed, "
                        + "start_at, end_at, worldline_effects_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "public-contribution-boss", BOSS_NO, "目标映射测试首领", "active", 1,
                100L, 1L, 1, 0, 1, LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(5), "{}");
        insertEvent(PK_EVENT_KEY, "pk_tournament", 20, "{}");
        insertEvent(AI_EVENT_KEY, "world_boss", 30,
                "{\"objectives\":["
                        + "{\"type\":\"defeat_boss\",\"target\":1,\"current\":0,\"score_per_unit\":10},"
                        + "{\"type\":\"win_pk\",\"target\":2,\"current\":0,\"score_per_unit\":5}]} ");

        CombatStatsVO stats = new CombatStatsVO();
        stats.setAttack(50);
        stats.setLevel(1);
        stats.setCritRate(0);
        stats.setCritDamage(1.5);
        when(combatService.calculateCombatPower(PLAYER_ID)).thenReturn(stats);
    }

    @Test
    void defeatingWorldBossAdvancesBossObjectiveWithoutInflatingPkBoard() throws Exception {
        WorldBossAttackResultVO result = worldBossService.attackBoss(PLAYER_ID, BOSS_NO, USER_ID);

        assertTrue(result.isBossDefeated());
        assertEquals("settled", jdbcTemplate.queryForObject(
                "SELECT status FROM world_bosses WHERE boss_no = ?", String.class, BOSS_NO));

        assertEquals(0, currentValue(PK_EVENT_KEY));
        assertEquals(0, count("SELECT COUNT(*) FROM broadcast_contributions WHERE event_key = ?", PK_EVENT_KEY));

        assertEquals(1, currentValue(AI_EVENT_KEY));
        assertEquals(1, objectiveProgress(AI_EVENT_KEY, "defeat_boss"));
        assertEquals(0, objectiveProgress(AI_EVENT_KEY, "win_pk"));
        assertEquals(10, jdbcTemplate.queryForObject(
                "SELECT contribution_value FROM broadcast_contributions WHERE event_key = ? AND player_id = ?",
                Integer.class, AI_EVENT_KEY, PLAYER_ID));
        assertEquals("defeat_boss", jdbcTemplate.queryForObject(
                "SELECT contribution_type FROM broadcast_contributions WHERE event_key = ? AND player_id = ?",
                String.class, AI_EVENT_KEY, PLAYER_ID));
    }

    private void insertEvent(String eventKey, String type, int target, String metadataJson) {
        jdbcTemplate.update("INSERT INTO broadcast_events "
                        + "(event_key, title, type, status, target_value, current_value, metadata_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                eventKey, "隔离公共事件", type, "active", target, 0, metadataJson);
    }

    private int currentValue(String eventKey) {
        return jdbcTemplate.queryForObject(
                "SELECT current_value FROM broadcast_events WHERE event_key = ?", Integer.class, eventKey);
    }

    private int objectiveProgress(String eventKey, String objectiveType) throws Exception {
        String metadata = jdbcTemplate.queryForObject(
                "SELECT metadata_json FROM broadcast_events WHERE event_key = ?", String.class, eventKey);
        JsonNode objectives = objectMapper.readTree(metadata).path("objectives");
        for (JsonNode objective : objectives) {
            if (objectiveType.equals(objective.path("type").asText())) {
                return objective.path("current").asInt();
            }
        }
        throw new AssertionError("Missing objective: " + objectiveType);
    }

    private int count(String sql, Object argument) {
        return jdbcTemplate.queryForObject(sql, Integer.class, argument);
    }
}
