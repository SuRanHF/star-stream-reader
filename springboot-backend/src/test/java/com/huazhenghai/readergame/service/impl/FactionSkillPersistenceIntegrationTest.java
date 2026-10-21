package com.huazhenghai.readergame.service.impl;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.FactionService;
import com.huazhenghai.readergame.vo.FactionSkillVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:faction_skill_persistence;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.scheduler.enabled=false"
}, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Sql("/db/combat-persistence-test-schema.sql")
class FactionSkillPersistenceIntegrationTest {
    private static final long PLAYER_ID = 82001L;
    private static final long USER_ID = 92001L;
    private static final String SKILL_KEY = "h2_faction_skill";

    @Autowired private FactionService factionService;
    @Autowired private PlayerMapper playerMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    @BeforeEach
    void seedIsolatedFactionSkillState() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:faction_skill_persistence"));
        }
        jdbcTemplate.update("DELETE FROM player_faction_skills");
        jdbcTemplate.update("DELETE FROM player_factions");
        jdbcTemplate.update("DELETE FROM faction_skills");
        jdbcTemplate.update("DELETE FROM constellation_factions");
        jdbcTemplate.update("DELETE FROM player_resource_transactions");
        jdbcTemplate.update("DELETE FROM player_action_receipts");
        jdbcTemplate.update("DELETE FROM player_logs");
        jdbcTemplate.update("DELETE FROM players");
        jdbcTemplate.update("DELETE FROM users");

        jdbcTemplate.update("INSERT INTO users (id, username, email, password_hash, role, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                USER_ID, "h2-faction-skill-user", "h2-faction-skill@example.invalid",
                "not-used-by-service-test", "player", "normal");
        jdbcTemplate.update("INSERT INTO players (id, user_id, player_name, coins, story_fragments, stats_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                PLAYER_ID, USER_ID, "阵营技能隔离角色", 500, 0,
                "{\"level\":1,\"maxHp\":100,\"hp\":100,\"attack\":10,\"defense\":5,"
                        + "\"speed\":10,\"constellation\":\"demon_judge_of_fire\"}");
        jdbcTemplate.update("INSERT INTO constellation_factions "
                        + "(faction_key, name, level, exp, member_count, total_contribution, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                "nebula_test", "隔离阵营", 1, 0L, 1, 100L, 1);
        jdbcTemplate.update("INSERT INTO player_factions "
                        + "(player_id, faction_key, role, reputation, contribution_total, joined_at, status) "
                        + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)",
                PLAYER_ID, "nebula_test", "member", 100L, 100L, "active");
        jdbcTemplate.update("INSERT INTO faction_skills "
                        + "(faction_key, skill_key, name, description, unlock_level, effects_json, cost_json, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "nebula_test", SKILL_KEY, "隔离技能", "H2 only", 1,
                "{}", "{\"reputation\":50,\"coins\":100}", 1);
    }

    @Test
    void concurrentLearningReplaysOnceAndRecordsBothResourceCosts() throws Exception {
        String requestKey = "f".repeat(64);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<FactionSkillVO> first = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                return factionService.learnFactionSkill(PLAYER_ID, SKILL_KEY, USER_ID, requestKey);
            }, executor);
            CompletableFuture<FactionSkillVO> second = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                return factionService.learnFactionSkill(PLAYER_ID, SKILL_KEY, USER_ID, requestKey);
            }, executor);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertTrue(first.get(15, TimeUnit.SECONDS).isLearned());
            assertTrue(second.get(15, TimeUnit.SECONDS).isLearned());

            assertEquals(400, playerMapper.selectById(PLAYER_ID).getCoins());
            assertEquals(50L, jdbcTemplate.queryForObject(
                    "SELECT reputation FROM player_factions WHERE player_id = ?", Long.class, PLAYER_ID));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_faction_skills WHERE player_id = ? AND skill_key = ?",
                    Integer.class, PLAYER_ID, SKILL_KEY));
            assertLedger("currency", "coins", -100, 500, 400);
            assertLedger("reputation", "nebula_test", -50, 100, 50);
            assertEquals(2, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_resource_transactions WHERE source_type = 'faction_skill_learn'",
                    Integer.class));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_action_receipts WHERE player_id = ? "
                            + "AND action_type = 'faction.skill-learn' AND idempotency_key = ?",
                    Integer.class, PLAYER_ID, requestKey));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_logs WHERE player_id = ? AND type = 'faction_skill'",
                    Integer.class, PLAYER_ID));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void learningRollsBackAllWritesWhenReceiptCannotBeInserted() {
        String requestKey = "h2-faction-receipt-failure";
        jdbcTemplate.execute("ALTER TABLE player_action_receipts ADD CONSTRAINT "
                + "ck_test_fail_faction_receipt CHECK (idempotency_key <> '" + requestKey + "')");
        try {
            assertThrows(RuntimeException.class, () -> factionService.learnFactionSkill(
                    PLAYER_ID, SKILL_KEY, USER_ID, requestKey));
        } finally {
            jdbcTemplate.execute("ALTER TABLE player_action_receipts "
                    + "DROP CONSTRAINT ck_test_fail_faction_receipt");
        }

        assertEquals(500, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(100L, jdbcTemplate.queryForObject(
                "SELECT reputation FROM player_factions WHERE player_id = ?", Long.class, PLAYER_ID));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_faction_skills", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_resource_transactions", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_logs", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_action_receipts", Integer.class));
    }

    @Test
    void insufficientCoinsOrReputationWritesNothing() {
        jdbcTemplate.update("UPDATE players SET coins = 99 WHERE id = ?", PLAYER_ID);
        assertThrows(BusinessException.class, () -> factionService.learnFactionSkill(
                PLAYER_ID, SKILL_KEY, USER_ID, "h2-faction-insufficient-coins"));

        jdbcTemplate.update("UPDATE players SET coins = 500 WHERE id = ?", PLAYER_ID);
        jdbcTemplate.update("UPDATE player_factions SET reputation = 49 WHERE player_id = ?", PLAYER_ID);
        assertThrows(BusinessException.class, () -> factionService.learnFactionSkill(
                PLAYER_ID, SKILL_KEY, USER_ID, "h2-faction-insufficient-reputation"));

        assertEquals(500, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(49L, jdbcTemplate.queryForObject(
                "SELECT reputation FROM player_factions WHERE player_id = ?", Long.class, PLAYER_ID));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_faction_skills", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_resource_transactions", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_logs", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_action_receipts", Integer.class));
    }

    private void assertLedger(String resourceType, String resourceKey, long amount,
                              long balanceBefore, long balanceAfter) {
        List<Long> values = jdbcTemplate.queryForList(
                "SELECT amount FROM player_resource_transactions WHERE player_id = ? "
                        + "AND resource_type = ? AND resource_key = ? AND source_type = 'faction_skill_learn'",
                Long.class, PLAYER_ID, resourceType, resourceKey);
        assertEquals(List.of(amount), values);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? "
                        + "AND resource_type = ? AND resource_key = ? AND balance_before = ? AND balance_after = ?",
                Integer.class, PLAYER_ID, resourceType, resourceKey, balanceBefore, balanceAfter));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("concurrent test start timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent test interrupted", exception);
        }
    }
}
