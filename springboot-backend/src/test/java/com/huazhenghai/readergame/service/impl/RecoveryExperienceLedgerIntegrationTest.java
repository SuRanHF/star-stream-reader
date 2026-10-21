package com.huazhenghai.readergame.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.RecoveryService;
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
        "spring.datasource.url=jdbc:h2:mem:recovery_experience_ledger;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.scheduler.enabled=false"
}, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Sql("/db/combat-persistence-test-schema.sql")
class RecoveryExperienceLedgerIntegrationTest {
    private static final long PLAYER_ID = 84001L;
    private static final long USER_ID = 94001L;

    @Autowired private RecoveryService recoveryService;
    @Autowired private PlayerMapper playerMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;
    @Autowired private ObjectMapper objectMapper;

    private long initialRecoveryCursor;

    @BeforeEach
    void seedDisposableH2Player() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:recovery_experience_ledger"));
        }
        jdbcTemplate.update("DELETE FROM player_resource_transactions");
        jdbcTemplate.update("DELETE FROM player_logs");
        jdbcTemplate.update("DELETE FROM players");
        jdbcTemplate.update("DELETE FROM users");

        jdbcTemplate.update("INSERT INTO users (id, username, email, password_hash, role, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                USER_ID, "h2-recovery-player", "h2-recovery@example.invalid", "test-only", "player", "normal");
        initialRecoveryCursor = System.currentTimeMillis() - 3_600_000L;
        String statsJson = "{\"hp\":100,\"maxHp\":100,\"stamina\":50,\"maxStamina\":50,"
                + "\"exp\":0,\"level\":1,\"freePoints\":0,\"isResting\":true,"
                + "\"lastRecoveryAt\":" + initialRecoveryCursor + "}";
        jdbcTemplate.update("INSERT INTO players (id, user_id, player_name, coins, story_fragments, stats_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                PLAYER_ID, USER_ID, "恢复流水隔离角色", 0, 0, statsJson);
    }

    @Test
    void passiveExperienceIsRecordedOnceWithLifetimeBalanceAndRecoveryCursor() throws Exception {
        Player player = playerMapper.selectById(PLAYER_ID);

        Map<String, Object> firstResult = recoveryService.applyRecovery(player);
        int awarded = ((Number) firstResult.get("exp")).intValue();
        assertTrue(awarded > 0);
        assertEquals(2, ((Number) firstResult.get("level")).intValue());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? "
                        + "AND resource_type = 'experience' AND resource_key = 'exp' "
                        + "AND source_type = 'passive_recovery'",
                Integer.class, PLAYER_ID));
        assertEquals(awarded, jdbcTemplate.queryForObject(
                "SELECT amount FROM player_resource_transactions WHERE player_id = ? "
                        + "AND source_type = 'passive_recovery'", Long.class, PLAYER_ID).intValue());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT balance_before FROM player_resource_transactions WHERE player_id = ? "
                        + "AND source_type = 'passive_recovery'", Integer.class, PLAYER_ID));
        assertEquals(awarded, jdbcTemplate.queryForObject(
                "SELECT balance_after FROM player_resource_transactions WHERE player_id = ? "
                        + "AND source_type = 'passive_recovery'", Integer.class, PLAYER_ID));

        Map<String, Object> metadata = objectMapper.readValue(
                jdbcTemplate.queryForObject("SELECT metadata_json FROM player_resource_transactions "
                        + "WHERE player_id = ? AND source_type = 'passive_recovery'", String.class, PLAYER_ID),
                new TypeReference<>() {});
        assertEquals(true, metadata.get("isResting"));

        recoveryService.applyRecovery(playerMapper.selectById(PLAYER_ID));
        assertEquals(awarded, readExperience(playerMapper.selectById(PLAYER_ID)));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? "
                        + "AND source_type = 'passive_recovery'", Integer.class, PLAYER_ID));
    }

    @Test
    void ledgerWriteFailureRollsBackRecoveredExperienceAndRecoveryCursor() {
        jdbcTemplate.execute("ALTER TABLE player_resource_transactions ADD CONSTRAINT "
                + "ck_test_recovery_experience CHECK (source_type <> 'passive_recovery')");
        try {
            assertThrows(RuntimeException.class,
                    () -> recoveryService.applyRecovery(playerMapper.selectById(PLAYER_ID)));
        } finally {
            jdbcTemplate.execute("ALTER TABLE player_resource_transactions "
                    + "DROP CONSTRAINT ck_test_recovery_experience");
        }

        Player unchanged = playerMapper.selectById(PLAYER_ID);
        assertEquals(0, readExperience(unchanged));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ?", Integer.class, PLAYER_ID));
        try {
            Map<String, Object> stats = objectMapper.readValue(unchanged.getStatsJson(), new TypeReference<>() {});
            assertEquals(initialRecoveryCursor, ((Number) stats.get("lastRecoveryAt")).longValue());
        } catch (Exception exception) {
            throw new AssertionError("无法检查回滚后的恢复游标", exception);
        }
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_logs WHERE player_id = ?", Integer.class, PLAYER_ID));
    }

    private int readExperience(Player player) {
        try {
            Map<String, Object> stats = objectMapper.readValue(player.getStatsJson(), new TypeReference<>() {});
            return ((Number) stats.get("exp")).intValue();
        } catch (Exception exception) {
            throw new AssertionError("无法读取恢复后的经验", exception);
        }
    }
}
