package com.huazhenghai.readergame.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUser;
import com.huazhenghai.readergame.security.LoginUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import javax.sql.DataSource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:player_constellation_ledger;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.scheduler.enabled=false"
}, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Sql("/db/combat-persistence-test-schema.sql")
class PlayerControllerResourceLedgerIntegrationTest {
    private static final long PLAYER_ID = 83101L;
    private static final long USER_ID = 93101L;
    private static final String NEW_CONSTELLATION = "morning_star";

    @Autowired private PlayerController playerController;
    @Autowired private PlayerMapper playerMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void seedDisposableH2Player() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:player_constellation_ledger"));
        }
        jdbcTemplate.update("DELETE FROM player_resource_transactions");
        jdbcTemplate.update("DELETE FROM player_action_receipts");
        jdbcTemplate.update("DELETE FROM player_logs");
        jdbcTemplate.update("DELETE FROM player_factions");
        jdbcTemplate.update("DELETE FROM players");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("INSERT INTO users (id, username, email, password_hash, role, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                USER_ID, "h2-constellation-player", "h2-constellation@example.invalid",
                "test-only", "player", "normal");
        jdbcTemplate.update("INSERT INTO players (id, user_id, player_name, coins, story_fragments, stats_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                PLAYER_ID, USER_ID, "背后星流水隔离角色", 0, 250,
                "{\"constellation\":\"demon_judge_of_fire\",\"constellationFavor\":80}");
    }

    @AfterEach
    void clearLoginUser() {
        LoginUserContext.clear();
    }

    @Test
    void constellationChangePersistsExactFragmentLedgerAndSameTargetRetryDoesNotChargeAgain() throws Exception {
        LoginUserContext.set(new LoginUser(USER_ID, "h2-constellation-player", "player"));

        var response = playerController.changeConstellation(Map.of(
                "playerId", PLAYER_ID,
                "constellationKey", NEW_CONSTELLATION));

        assertTrue(response.isSuccess());
        assertEquals(50, playerMapper.selectById(PLAYER_ID).getStoryFragments());
        Map<String, Object> stats = objectMapper.readValue(
                playerMapper.selectById(PLAYER_ID).getStatsJson(), new TypeReference<>() {});
        assertEquals(NEW_CONSTELLATION, stats.get("constellation"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? "
                        + "AND source_type = 'constellation_change' AND resource_key = 'story_fragments'",
                Integer.class, PLAYER_ID));
        assertEquals(-200L, jdbcTemplate.queryForObject(
                "SELECT amount FROM player_resource_transactions WHERE player_id = ? "
                        + "AND source_type = 'constellation_change'", Long.class, PLAYER_ID));
        assertEquals(250L, jdbcTemplate.queryForObject(
                "SELECT balance_before FROM player_resource_transactions WHERE player_id = ? "
                        + "AND source_type = 'constellation_change'", Long.class, PLAYER_ID));
        assertEquals(50L, jdbcTemplate.queryForObject(
                "SELECT balance_after FROM player_resource_transactions WHERE player_id = ? "
                        + "AND source_type = 'constellation_change'", Long.class, PLAYER_ID));

        assertThrows(BusinessException.class, () -> playerController.changeConstellation(Map.of(
                "playerId", PLAYER_ID,
                "constellationKey", NEW_CONSTELLATION)));

        assertEquals(50, playerMapper.selectById(PLAYER_ID).getStoryFragments());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? "
                        + "AND source_type = 'constellation_change'", Integer.class, PLAYER_ID));
    }

    @Test
    void ledgerInsertFailureRollsBackPlayerBalanceAndFactionSideEffects() {
        jdbcTemplate.execute("ALTER TABLE player_resource_transactions ADD CONSTRAINT "
                + "ck_test_constellation_ledger CHECK (source_type <> 'constellation_change')");
        LoginUserContext.set(new LoginUser(USER_ID, "h2-constellation-player", "player"));
        try {
            assertThrows(RuntimeException.class, () -> playerController.changeConstellation(Map.of(
                    "playerId", PLAYER_ID,
                    "constellationKey", NEW_CONSTELLATION)));
        } finally {
            jdbcTemplate.execute("ALTER TABLE player_resource_transactions "
                    + "DROP CONSTRAINT ck_test_constellation_ledger");
        }

        Player unchanged = playerMapper.selectById(PLAYER_ID);
        assertEquals(250, unchanged.getStoryFragments());
        try {
            Map<String, Object> stats = objectMapper.readValue(
                    unchanged.getStatsJson(), new TypeReference<>() {});
            assertEquals("demon_judge_of_fire", stats.get("constellation"));
            assertEquals(80, ((Number) stats.get("constellationFavor")).intValue());
        } catch (Exception exception) {
            throw new AssertionError("无法检查回滚后的背后星状态", exception);
        }
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ?", Integer.class, PLAYER_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_logs WHERE player_id = ?", Integer.class, PLAYER_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_factions WHERE player_id = ?", Integer.class, PLAYER_ID));
    }
}
