package com.huazhenghai.readergame.service.impl;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.service.BroadcastService;
import com.huazhenghai.readergame.service.CombatService;
import com.huazhenghai.readergame.service.FactionService;
import com.huazhenghai.readergame.service.PartyService;
import com.huazhenghai.readergame.service.QuestService;
import com.huazhenghai.readergame.service.RecoveryService;
import com.huazhenghai.readergame.service.WorldBossService;
import com.huazhenghai.readergame.vo.CombatStatsVO;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:party_boss_action_receipt;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.scheduler.enabled=false"
}, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Sql("/db/combat-persistence-test-schema.sql")
class PartyBossActionReceiptIntegrationTest {

    private static final AtomicLong IDS = new AtomicLong(940_000L);

    @Autowired private PartyService partyService;
    @Autowired private WorldBossService worldBossService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;
    @MockitoBean private CombatService combatService;
    @MockitoBean private RecoveryService recoveryService;
    @MockitoBean private BroadcastService broadcastService;
    @MockitoBean private FactionService factionService;
    @MockitoBean private QuestService questService;

    private long userId;
    private long playerId;
    private long secondPlayerId;
    private String partyNo;
    private String requestKey;
    private String bossNo;

    @BeforeEach
    void seedIsolatedReadyParty() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:party_boss_action_receipt"));
        }

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS parties ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY, party_no VARCHAR(80) NOT NULL UNIQUE, "
                + "leader_player_id BIGINT NOT NULL, name VARCHAR(100) NOT NULL, description CLOB, "
                + "status VARCHAR(24) NOT NULL, max_members INT, target_type VARCHAR(40), target_key VARCHAR(100), "
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "disbanded_at TIMESTAMP)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS party_members ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY, party_no VARCHAR(80) NOT NULL, player_id BIGINT NOT NULL, "
                + "role VARCHAR(24), status VARCHAR(24) NOT NULL, ready INT NOT NULL DEFAULT 0, "
                + "joined_at TIMESTAMP, left_at TIMESTAMP, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

        long id = IDS.addAndGet(10);
        userId = id;
        playerId = id + 100_000L;
        secondPlayerId = playerId + 1;
        String suffix = UUID.randomUUID().toString().replace("-", "");
        partyNo = "PT-IDEMP-" + suffix.substring(0, 16);
        requestKey = "party-boss-" + suffix.substring(0, 24);
        bossNo = "WB-H2-" + suffix.substring(0, 12);

        jdbcTemplate.update("INSERT INTO users (id, username, email, password_hash, role, status) "
                        + "VALUES (?, ?, ?, ?, 'player', 'normal')",
                userId, "party-boss-" + suffix, "party-boss-" + suffix + "@example.invalid", "test-only");
        jdbcTemplate.update("INSERT INTO players "
                        + "(id, user_id, player_name, coins, story_fragments, stats_json, stage_progress_json) "
                        + "VALUES (?, ?, ?, 0, 0, ?, '{}')",
                playerId, userId, "幂等队长", "{\"level\":1,\"hp\":100,\"maxHp\":100}");
        jdbcTemplate.update("INSERT INTO users (id, username, email, password_hash, role, status) "
                        + "VALUES (?, ?, ?, ?, 'player', 'normal')",
                userId + 1, "party-boss-member-" + suffix,
                "party-boss-member-" + suffix + "@example.invalid", "test-only");
        jdbcTemplate.update("INSERT INTO players "
                        + "(id, user_id, player_name, coins, story_fragments, stats_json, stage_progress_json) "
                        + "VALUES (?, ?, ?, 0, 0, ?, '{}')",
                secondPlayerId, userId + 1, "幂等队员", "{\"level\":1,\"hp\":100,\"maxHp\":100}");
        jdbcTemplate.update("INSERT INTO parties (party_no, leader_player_id, name, status, max_members) "
                        + "VALUES (?, ?, ?, 'active', 3)",
                partyNo, playerId, "回执验收队伍");
        jdbcTemplate.update("INSERT INTO party_members "
                        + "(party_no, player_id, role, status, ready, joined_at) "
                        + "VALUES (?, ?, 'leader', 'active', 1, CURRENT_TIMESTAMP)",
                partyNo, playerId);
        jdbcTemplate.update("INSERT INTO party_members "
                        + "(party_no, player_id, role, status, ready, joined_at) "
                        + "VALUES (?, ?, 'member', 'active', 1, DATEADD('SECOND', 1, CURRENT_TIMESTAMP))",
                partyNo, secondPlayerId);
        jdbcTemplate.update("INSERT INTO world_bosses "
                        + "(boss_key, boss_no, name, status, level, max_hp, current_hp, attack, defense, speed, start_at, end_at) "
                        + "VALUES ('h2_boss', ?, '回执首领', 'active', 1, 100000, 100000, 5, 0, 1, "
                        + "DATEADD('MINUTE', -1, CURRENT_TIMESTAMP), DATEADD('HOUR', 2, CURRENT_TIMESTAMP))",
                bossNo);

        CombatStatsVO stats = new CombatStatsVO();
        stats.setLevel(1);
        stats.setAttack(20);
        stats.setCritRate(0);
        stats.setCritDamage(1.5);
        when(combatService.calculateCombatPower(any())).thenReturn(stats);
    }

    @Test
    void sameRequestKeyReplaysOneWholePartyAttack() {
        Map<String, Object> first = partyService.startPartyBossBattle(partyNo, playerId, userId, requestKey);
        Map<String, Object> replay = partyService.startPartyBossBattle(partyNo, playerId, userId, requestKey);

        assertEquals(bossNo, first.get("bossNo"));
        assertEquals(2, ((Number) first.get("attackedMemberCount")).intValue());
        assertSameAttackResult(first, replay);
        assertEquals(1, count("SELECT COUNT(*) FROM player_action_receipts WHERE player_id = ? "
                + "AND action_type = 'party.world_boss.attack' AND idempotency_key = ?", playerId, requestKey));
        assertEquals(1, count("SELECT COUNT(*) FROM player_logs WHERE player_id = ? AND type = 'party_boss'", playerId));
        assertEquals("worldBoss", jdbcTemplate.queryForObject(
                "SELECT target_type FROM parties WHERE party_no = ?", String.class, partyNo));
        assertBossWasDamagedExactlyOnce(first);
    }

    @Test
    void sameKeyCannotBeReusedForAnotherPartyPayload() {
        Map<String, Object> first = partyService.startPartyBossBattle(partyNo, playerId, userId, requestKey);

        assertThrows(BusinessException.class,
                () -> partyService.startPartyBossBattle("PT-DIFFERENT", playerId, userId, requestKey));

        assertEquals(1, count("SELECT COUNT(*) FROM player_logs WHERE player_id = ? AND type = 'party_boss'", playerId));
        assertBossWasDamagedExactlyOnce(first);
    }

    @Test
    void receiptFailureRollsBackPartyTargetAndLog() {
        String rejectedKey = "reject-party-receipt";
        jdbcTemplate.execute("ALTER TABLE player_action_receipts ADD CONSTRAINT ck_test_reject_party_receipt "
                + "CHECK (idempotency_key <> '" + rejectedKey + "')");
        long hpBefore = bossHp();
        try {
            assertThrows(RuntimeException.class,
                    () -> partyService.startPartyBossBattle(partyNo, playerId, userId, rejectedKey));
        } finally {
            jdbcTemplate.execute("ALTER TABLE player_action_receipts DROP CONSTRAINT ck_test_reject_party_receipt");
        }

        assertEquals(null, jdbcTemplate.queryForObject(
                "SELECT target_type FROM parties WHERE party_no = ?", String.class, partyNo));
        assertEquals(0, count("SELECT COUNT(*) FROM player_logs WHERE player_id = ? AND type = 'party_boss'", playerId));
        assertEquals(0, count("SELECT COUNT(*) FROM player_logs WHERE type = 'battle' AND player_id IN (?, ?)",
                playerId, secondPlayerId));
        assertEquals(0, count("SELECT COUNT(*) FROM player_action_receipts WHERE player_id = ?", playerId));
        assertEquals(hpBefore, bossHp());
        assertEquals(0, count("SELECT COUNT(*) FROM world_boss_participation WHERE boss_no = ?", bossNo));
    }

    @Test
    void concurrentSameKeyRequestsExecuteThePartyAttackOnce() throws Exception {
        long hpBefore = bossHp();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Map<String, Object>> first = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                return partyService.startPartyBossBattle(partyNo, playerId, userId, requestKey);
            }, executor);
            CompletableFuture<Map<String, Object>> second = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                await(start);
                return partyService.startPartyBossBattle(partyNo, playerId, userId, requestKey);
            }, executor);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            Map<String, Object> firstResult = first.get(15, TimeUnit.SECONDS);
            Map<String, Object> secondResult = second.get(15, TimeUnit.SECONDS);
            assertEquals(bossNo, firstResult.get("bossNo"));
            assertSameAttackResult(firstResult, secondResult);
            assertEquals(2, ((Number) firstResult.get("attackedMemberCount")).intValue());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertEquals(1, count("SELECT COUNT(*) FROM player_action_receipts WHERE player_id = ? "
                + "AND action_type = 'party.world_boss.attack' AND idempotency_key = ?", playerId, requestKey));
        assertEquals(1, count("SELECT COUNT(*) FROM player_logs WHERE player_id = ? AND type = 'party_boss'", playerId));
        assertEquals(2, count("SELECT COUNT(*) FROM world_boss_participation WHERE boss_no = ?", bossNo));
        assertEquals(2, count("SELECT COUNT(*) FROM world_boss_participation WHERE boss_no = ? AND attack_count = 1", bossNo));
        long damage = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(damage), 0) FROM world_boss_participation WHERE boss_no = ?", Long.class, bossNo);
        assertEquals(hpBefore - damage, bossHp());
        assertEquals(2, count("SELECT COUNT(*) FROM player_logs WHERE type = 'battle' AND player_id IN (?, ?)",
                playerId, secondPlayerId));
    }

    private int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private static void assertSameAttackResult(Map<String, Object> expected, Map<String, Object> actual) {
        assertEquals(expected.get("partyNo"), actual.get("partyNo"));
        assertEquals(expected.get("bossNo"), actual.get("bossNo"));
        assertEquals(((Number) expected.get("totalDamage")).longValue(),
                ((Number) actual.get("totalDamage")).longValue());
        assertEquals(expected.get("bossDefeated"), actual.get("bossDefeated"));
        assertEquals(((Number) expected.get("attackedMemberCount")).intValue(),
                ((Number) actual.get("attackedMemberCount")).intValue());
    }

    private void assertBossWasDamagedExactlyOnce(Map<String, Object> result) {
        long damage = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(damage), 0) FROM world_boss_participation WHERE boss_no = ?", Long.class, bossNo);
        assertEquals(((Number) result.get("totalDamage")).longValue(), damage);
        assertEquals(2, count("SELECT COUNT(*) FROM world_boss_participation WHERE boss_no = ? AND attack_count = 1", bossNo));
        assertEquals(2, count("SELECT COUNT(*) FROM player_logs WHERE type = 'battle' AND player_id IN (?, ?)",
                playerId, secondPlayerId));
    }

    private long bossHp() {
        return jdbcTemplate.queryForObject(
                "SELECT current_hp FROM world_bosses WHERE boss_no = ?", Long.class, bossNo);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting to start concurrent test");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to start concurrent test", exception);
        }
    }
}
