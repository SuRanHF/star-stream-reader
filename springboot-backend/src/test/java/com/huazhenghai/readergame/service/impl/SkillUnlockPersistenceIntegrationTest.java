package com.huazhenghai.readergame.service.impl;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.SkillService;
import com.huazhenghai.readergame.vo.PlayerSkillVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import javax.sql.DataSource;
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
        "spring.datasource.url=jdbc:h2:mem:skill_unlock_persistence;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.scheduler.enabled=false"
}, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Sql("/db/combat-persistence-test-schema.sql")
class SkillUnlockPersistenceIntegrationTest {
    private static final long PLAYER_ID = 83001L;
    private static final long USER_ID = 93001L;
    private static final String SKILL_KEY = "h2_regular_skill";
    private static final String OTHER_SKILL_KEY = "h2_other_skill";

    @Autowired private SkillService skillService;
    @Autowired private PlayerMapper playerMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    @BeforeEach
    void seedIsolatedSkillState() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:skill_unlock_persistence"));
        }
        jdbcTemplate.update("DELETE FROM player_action_receipts");
        jdbcTemplate.update("DELETE FROM player_resource_transactions");
        jdbcTemplate.update("DELETE FROM player_logs");
        jdbcTemplate.update("DELETE FROM player_skills");
        jdbcTemplate.update("DELETE FROM skills");
        jdbcTemplate.update("DELETE FROM players");
        jdbcTemplate.update("DELETE FROM users");

        jdbcTemplate.update("INSERT INTO users (id, username, email, password_hash, role, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                USER_ID, "h2-skill-unlock-user", "h2-skill-unlock@example.invalid",
                "not-used-by-service-test", "player", "normal");
        jdbcTemplate.update("INSERT INTO players "
                        + "(id, user_id, player_name, coins, story_fragments, stats_json, stage_progress_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                PLAYER_ID, USER_ID, "技能幂等隔离角色", 500, 10,
                "{\"level\":1,\"avatarRank\":\"F\",\"isResting\":false}", "{}");
        insertSkill(SKILL_KEY, "普通技能");
        insertSkill(OTHER_SKILL_KEY, "另一技能");
    }

    @Test
    void concurrentSameKeyUnlockReplaysOnceAndRecordsTheResourceCosts() throws Exception {
        String requestKey = "h2-skill-unlock-concurrent";
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<PlayerSkillVO> first = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                return skillService.unlockSkill(PLAYER_ID, SKILL_KEY, USER_ID, requestKey);
            }, executor);
            CompletableFuture<PlayerSkillVO> second = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                return skillService.unlockSkill(PLAYER_ID, SKILL_KEY, USER_ID, requestKey);
            }, executor);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(SKILL_KEY, first.get(15, TimeUnit.SECONDS).getSkillKey());
            assertEquals(SKILL_KEY, second.get(15, TimeUnit.SECONDS).getSkillKey());

            assertEquals(460, playerMapper.selectById(PLAYER_ID).getCoins());
            assertEquals(7, playerMapper.selectById(PLAYER_ID).getStoryFragments());
            assertEquals(1, count("player_skills", "player_id", PLAYER_ID));
            assertEquals(2, count("player_resource_transactions", "source_type", "skill_unlock"));
            assertEquals(1, count("player_logs", "type", "skill_unlock"));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM player_action_receipts WHERE player_id = ? "
                            + "AND action_type = 'skill.unlock' AND idempotency_key = ?",
                    Integer.class, PLAYER_ID, requestKey));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void reusingRequestKeyForAnotherSkillIsRejectedWithoutSpendingAgain() {
        String requestKey = "h2-skill-unlock-payload-conflict";
        skillService.unlockSkill(PLAYER_ID, SKILL_KEY, USER_ID, requestKey);

        BusinessException error = assertThrows(BusinessException.class,
                () -> skillService.unlockSkill(PLAYER_ID, OTHER_SKILL_KEY, USER_ID, requestKey));

        assertEquals(ErrorCode.INVALID_FIELD_VALUE, error.getCode());
        assertEquals(460, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(7, playerMapper.selectById(PLAYER_ID).getStoryFragments());
        assertEquals(1, count("player_skills", "player_id", PLAYER_ID));
        assertEquals(2, count("player_resource_transactions", "source_type", "skill_unlock"));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_action_receipts",
                Integer.class));
    }

    @Test
    void receiptInsertFailureRollsBackTheSkillAndItsResourceCosts() {
        String requestKey = "h2-skill-unlock-receipt-failure";
        jdbcTemplate.execute("ALTER TABLE player_action_receipts ADD CONSTRAINT "
                + "ck_test_fail_skill_receipt CHECK (idempotency_key <> '" + requestKey + "')");
        try {
            assertThrows(RuntimeException.class, () -> skillService.unlockSkill(
                    PLAYER_ID, SKILL_KEY, USER_ID, requestKey));
        } finally {
            jdbcTemplate.execute("ALTER TABLE player_action_receipts "
                    + "DROP CONSTRAINT ck_test_fail_skill_receipt");
        }

        assertEquals(500, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(10, playerMapper.selectById(PLAYER_ID).getStoryFragments());
        assertEquals(0, count("player_skills", "player_id", PLAYER_ID));
        assertEquals(0, count("player_resource_transactions", "source_type", "skill_unlock"));
        assertEquals(0, count("player_logs", "type", "skill_unlock"));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_action_receipts",
                Integer.class));
    }

    private void insertSkill(String skillKey, String name) {
        jdbcTemplate.update("INSERT INTO skills "
                        + "(skill_key, name, type, rarity, unlock_conditions_json, effects_json, cost_json, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                skillKey, name, "passive", "common", "{}", "{\"attack\":2}",
                "{\"coins\":40,\"storyFragments\":3}", 1);
    }

    private int count(String table, String column, Object value) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class, value);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("concurrent test start timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent test interrupted", exception);
        }
    }
}
