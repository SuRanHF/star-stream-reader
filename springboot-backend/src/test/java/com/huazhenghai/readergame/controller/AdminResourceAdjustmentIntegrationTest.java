package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:admin_resource_adjustment;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.scheduler.enabled=false",
        "admin.key=admin-resource-test-key"
}, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Sql("/db/combat-persistence-test-schema.sql")
class AdminResourceAdjustmentIntegrationTest {
    private static final long PLAYER_ID = 83001L;
    private static final long USER_ID = 93001L;
    private static final String ADMIN_KEY = "admin-resource-test-key";

    @Autowired private AdminController adminController;
    @Autowired private PlayerMapper playerMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    @BeforeEach
    void seedDisposableH2State() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:admin_resource_adjustment"));
        }
        jdbcTemplate.update("DELETE FROM admin_action_logs");
        jdbcTemplate.update("DELETE FROM player_resource_transactions");
        jdbcTemplate.update("DELETE FROM player_action_receipts");
        jdbcTemplate.update("DELETE FROM player_logs");
        jdbcTemplate.update("DELETE FROM avatar_rank_configs");
        jdbcTemplate.update("DELETE FROM players");
        jdbcTemplate.update("DELETE FROM users");

        jdbcTemplate.update("INSERT INTO users (id, username, email, password_hash, role, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                USER_ID, "h2-admin-resource-player", "h2-admin-resource@example.invalid",
                "test-only", "player", "normal");
        jdbcTemplate.update("INSERT INTO players (id, user_id, player_name, coins, story_fragments, "
                        + "constellation_favor, abyss_mark, stats_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                PLAYER_ID, USER_ID, "资源校正隔离角色", 100, 4, 50, 2,
                "{\"level\":1,\"hp\":100,\"maxHp\":100,\"attack\":10,\"defense\":5,\"speed\":10,"
                        + "\"avatarRank\":\"F\",\"channelHeat\":10,\"worldLineShift\":1}");
        jdbcTemplate.update("INSERT INTO avatar_rank_configs (rank_key, rank_name, display_name, order_num, "
                        + "next_rank_key, requirements_json, enabled) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "F", "F", "临时化身", 1, "E", "[]", 1);
        jdbcTemplate.update("INSERT INTO avatar_rank_configs (rank_key, rank_name, display_name, order_num, "
                        + "requirements_json, enabled) VALUES (?, ?, ?, ?, ?, ?)",
                "E", "E", "正式化身", 2,
                "[{\"type\":\"storyFragmentsMin\",\"value\":8},"
                        + "{\"type\":\"channelHeatMin\",\"value\":50},"
                        + "{\"type\":\"worldLineShiftMin\",\"value\":4},"
                        + "{\"type\":\"levelMin\",\"value\":5}]", 1);
    }

    @Test
    void nonAdminAndInvalidNegativeValuesAreRejectedWithoutWrites() {
        var unauthorized = adminController.updateResources(PLAYER_ID,
                request("not-authorized", "测试拒绝", Map.of("coins", 200)), new MockHttpServletRequest());
        assertFalse(unauthorized.isSuccess());
        assertEquals("FORBIDDEN", unauthorized.getError().getCode());

        var negative = adminController.updateResources(PLAYER_ID,
                request("negative-value", "测试负数拒绝", Map.of("coins", -1)), adminRequest());
        assertFalse(negative.isSuccess());
        assertEquals("INVALID_VALUE", negative.getError().getCode());

        var fractional = adminController.updateResources(PLAYER_ID,
                request("fractional-value", "测试小数拒绝", Map.of("coins", 1.5)), adminRequest());
        assertFalse(fractional.isSuccess());
        assertEquals(100, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(0, rowCount("admin_action_logs"));
        assertEquals(0, rowCount("player_resource_transactions"));
        assertEquals(0, rowCount("player_action_receipts"));
    }

    @Test
    void adjustmentWritesSignedLedgerAndAuditableBeforeAfterValuesAndReplayDoesNotDuplicate() {
        Map<String, Object> body = request("adjust-once", "修复活动奖励发放错误", Map.of(
                "coins", 150, "story_fragments", 3, "constellationFavor", 70, "abyssMark", 0));

        var first = adminController.updateResources(PLAYER_ID, body, adminRequest());
        assertTrue(first.isSuccess());
        Player updated = playerMapper.selectById(PLAYER_ID);
        assertEquals(150, updated.getCoins());
        assertEquals(3, updated.getStoryFragments());
        assertEquals(70, updated.getConstellationFavor());
        assertEquals(0, updated.getAbyssMark());
        assertLedger("coins", 50, 100, 150);
        assertLedger("story_fragments", -1, 4, 3);
        assertLedger("constellation_favor", 20, 50, 70);
        assertLedger("abyss_mark", -2, 2, 0);

        assertEquals(1, rowCount("admin_action_logs"));
        assertEquals(PLAYER_ID, jdbcTemplate.queryForObject(
                "SELECT target_player_id FROM admin_action_logs", Long.class));
        String audit = jdbcTemplate.queryForObject("SELECT payload_json FROM admin_action_logs", String.class);
        assertTrue(audit.contains("admin-key"));
        assertTrue(audit.contains("修复活动奖励发放错误"));
        assertTrue(audit.contains("\"before\":100"));
        assertTrue(audit.contains("\"after\":150"));
        assertEquals(1, rowCount("player_logs"));
        assertEquals(1, rowCount("player_action_receipts"));

        var replay = adminController.updateResources(PLAYER_ID, body, adminRequest());
        assertTrue(replay.isSuccess());
        assertEquals(150, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(1, rowCount("admin_action_logs"));
        assertEquals(4, rowCount("player_resource_transactions"));
        assertEquals(1, rowCount("player_logs"));
        assertEquals(1, rowCount("player_action_receipts"));

        Map<String, Object> conflictingBody = request("adjust-once", "不同原因", Map.of("coins", 151));
        assertThrows(BusinessException.class,
                () -> adminController.updateResources(PLAYER_ID, conflictingBody, adminRequest()));
        assertEquals(150, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(1, rowCount("admin_action_logs"));
    }

    @Test
    void concurrentSameKeyReplaysOnceAndDifferentResourceUpdatesDoNotOverwriteEachOther() throws Exception {
        Map<String, Object> sameRequest = request("parallel-replay", "并发重试验收", Map.of("coins", 180));
        var sameKeyResults = runTogether(
                () -> adminController.updateResources(PLAYER_ID, sameRequest, adminRequest()),
                () -> adminController.updateResources(PLAYER_ID, sameRequest, adminRequest()));
        assertTrue(sameKeyResults[0].isSuccess());
        assertTrue(sameKeyResults[1].isSuccess());
        assertEquals(180, playerMapper.selectById(PLAYER_ID).getCoins());
        assertEquals(1, rowCount("admin_action_logs"));
        assertEquals(1, rowCount("player_resource_transactions"));
        assertEquals(1, rowCount("player_action_receipts"));

        jdbcTemplate.update("DELETE FROM admin_action_logs");
        jdbcTemplate.update("DELETE FROM player_resource_transactions");
        jdbcTemplate.update("DELETE FROM player_action_receipts");
        jdbcTemplate.update("DELETE FROM player_logs");
        jdbcTemplate.update("UPDATE players SET coins = 100, story_fragments = 4 WHERE id = ?", PLAYER_ID);
        Map<String, Object> coinsRequest = request("parallel-coins", "并发调整星币", Map.of("coins", 220));
        Map<String, Object> fragmentsRequest = request("parallel-fragments", "并发调整碎片", Map.of("story_fragments", 9));
        var differentResults = runTogether(
                () -> adminController.updateResources(PLAYER_ID, coinsRequest, adminRequest()),
                () -> adminController.updateResources(PLAYER_ID, fragmentsRequest, adminRequest()));
        assertTrue(differentResults[0].isSuccess());
        assertTrue(differentResults[1].isSuccess());
        Player finalPlayer = playerMapper.selectById(PLAYER_ID);
        assertEquals(220, finalPlayer.getCoins());
        assertEquals(9, finalPlayer.getStoryFragments());
        assertEquals(2, rowCount("admin_action_logs"));
        assertEquals(2, rowCount("player_resource_transactions"));
        assertEquals(2, rowCount("player_action_receipts"));
    }

    @Test
    void receiptFailureRollsBackPlayerLedgerHistoryAndAdminAuditTogether() {
        String requestKey = "receipt-write-fails";
        jdbcTemplate.execute("ALTER TABLE player_action_receipts ADD CONSTRAINT "
                + "ck_test_admin_resource_receipt CHECK (idempotency_key <> '" + requestKey + "')");
        try {
            assertThrows(RuntimeException.class, () -> adminController.updateResources(PLAYER_ID,
                    request(requestKey, "验收最终回执失败回滚", Map.of("coins", 300, "abyssMark", 8)), adminRequest()));
        } finally {
            jdbcTemplate.execute("ALTER TABLE player_action_receipts "
                    + "DROP CONSTRAINT ck_test_admin_resource_receipt");
        }

        Player unchanged = playerMapper.selectById(PLAYER_ID);
        assertEquals(100, unchanged.getCoins());
        assertEquals(2, unchanged.getAbyssMark());
        assertEquals(0, rowCount("admin_action_logs"));
        assertEquals(0, rowCount("player_resource_transactions"));
        assertEquals(0, rowCount("player_logs"));
        assertEquals(0, rowCount("player_action_receipts"));
    }

    @Test
    void rankRequirementShortcutWritesProgressLedgerAndDetailedAdminAuditAtomically() {
        var result = adminController.quickAction(PLAYER_ID,
                Map.of("action", "fill_rank_requirements"), adminRequest());

        assertTrue(result.isSuccess());
        Player updated = playerMapper.selectById(PLAYER_ID);
        assertEquals(8, updated.getStoryFragments());
        assertTrue(updated.getStatsJson().contains("\"level\":5"));
        assertTrue(updated.getStatsJson().contains("\"channelHeat\":50"));
        assertTrue(updated.getStatsJson().contains("\"worldLineShift\":4"));
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? "
                        + "AND source_type = 'admin_rank_requirements'",
                Integer.class, PLAYER_ID));
        assertLedgerSource("story_fragments", "currency", 4, 4, 8);
        assertLedgerSource("channel_heat", "progress", 40, 10, 50);
        assertLedgerSource("world_line_shift", "progress", 3, 1, 4);

        assertEquals(1, rowCount("admin_action_logs"));
        assertEquals(PLAYER_ID, jdbcTemplate.queryForObject(
                "SELECT target_player_id FROM admin_action_logs", Long.class));
        String audit = jdbcTemplate.queryForObject(
                "SELECT payload_json FROM admin_action_logs WHERE action = 'fill_rank_reqs'", String.class);
        assertTrue(audit.contains("admin-key"));
        assertTrue(audit.contains("ADMIN_KEY"));
        assertTrue(audit.contains("statsChanges"));
        assertTrue(audit.contains("stageProgressChanges"));
        assertTrue(audit.contains("story_fragments"));
        assertTrue(audit.contains("world_line_shift"));
        assertEquals(1, rowCount("player_logs"));
    }

    @Test
    void rankRequirementLedgerFailureRollsBackProgressAndAdminAudit() {
        jdbcTemplate.execute("ALTER TABLE player_resource_transactions ADD CONSTRAINT "
                + "ck_test_admin_rank_resource CHECK (source_type <> 'admin_rank_requirements')");
        try {
            assertThrows(RuntimeException.class, () -> adminController.quickAction(PLAYER_ID,
                    Map.of("action", "fill_rank_requirements"), adminRequest()));
        } finally {
            jdbcTemplate.execute("ALTER TABLE player_resource_transactions "
                    + "DROP CONSTRAINT ck_test_admin_rank_resource");
        }

        Player unchanged = playerMapper.selectById(PLAYER_ID);
        assertEquals(4, unchanged.getStoryFragments());
        assertTrue(unchanged.getStatsJson().contains("\"level\":1"));
        assertTrue(unchanged.getStatsJson().contains("\"channelHeat\":10"));
        assertTrue(unchanged.getStatsJson().contains("\"worldLineShift\":1"));
        assertEquals(0, rowCount("admin_action_logs"));
        assertEquals(0, rowCount("player_resource_transactions"));
        assertEquals(0, rowCount("player_logs"));
    }

    private void assertLedger(String resourceKey, long amount, long before, long after) {
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? "
                        + "AND resource_key = ? AND amount = ? AND balance_before = ? AND balance_after = ? "
                        + "AND source_type = 'admin_resource_adjustment'",
                Integer.class, PLAYER_ID, resourceKey, amount, before, after));
    }

    private void assertLedgerSource(String resourceKey, String resourceType, long amount, long before, long after) {
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_resource_transactions WHERE player_id = ? "
                        + "AND resource_key = ? AND resource_type = ? AND amount = ? "
                        + "AND balance_before = ? AND balance_after = ? "
                        + "AND source_type = 'admin_rank_requirements'",
                Integer.class, PLAYER_ID, resourceKey, resourceType, amount, before, after));
    }

    private int rowCount(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private MockHttpServletRequest adminRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Admin-Key", ADMIN_KEY);
        return request;
    }

    private Map<String, Object> request(String requestKey, String reason, Map<String, Object> resources) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestKey", requestKey);
        body.put("reason", reason);
        body.putAll(resources);
        return body;
    }

    @SafeVarargs
    private final com.huazhenghai.readergame.common.Result<Map<String, Object>>[] runTogether(
            java.util.function.Supplier<com.huazhenghai.readergame.common.Result<Map<String, Object>>>... actions)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(actions.length);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(actions.length);
        try {
            @SuppressWarnings("unchecked")
            CompletableFuture<com.huazhenghai.readergame.common.Result<Map<String, Object>>>[] futures =
                    new CompletableFuture[actions.length];
            for (int index = 0; index < actions.length; index++) {
                var action = actions[index];
                futures[index] = CompletableFuture.supplyAsync(() -> {
                    ready.countDown();
                    await(start);
                    return action.get();
                }, executor);
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            @SuppressWarnings("unchecked")
            com.huazhenghai.readergame.common.Result<Map<String, Object>>[] results =
                    new com.huazhenghai.readergame.common.Result[actions.length];
            for (int index = 0; index < actions.length; index++) results[index] = futures[index].get(15, TimeUnit.SECONDS);
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("并发测试启动超时");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("并发测试被中断", exception);
        }
    }
}
