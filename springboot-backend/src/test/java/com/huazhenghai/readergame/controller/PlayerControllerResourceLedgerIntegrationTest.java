package com.huazhenghai.readergame.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.dto.ReviveRequest;
import com.huazhenghai.readergame.dto.PeerReviveRequest;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUser;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.TitleService;
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
    @Autowired private TitleService titleService;

    @BeforeEach
    void seedDisposableH2Player() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:player_constellation_ledger"));
        }
        jdbcTemplate.update("DELETE FROM player_resource_transactions");
        jdbcTemplate.update("DELETE FROM player_action_receipts");
        jdbcTemplate.update("DELETE FROM player_logs");
        jdbcTemplate.update("DELETE FROM player_factions");
        jdbcTemplate.update("DELETE FROM player_titles");
        jdbcTemplate.update("DELETE FROM titles");
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

    private void seedSacrificeTitle(long ownerId, String key) {
        jdbcTemplate.update("INSERT INTO titles (title_key, name, enabled) VALUES (?, ?, 1)", key, "夜行者");
        jdbcTemplate.update("INSERT INTO player_titles (player_id, title_key, equipped) VALUES (?, ?, 1)",
                ownerId, key);
    }

    private void makeDead(long playerId) {
        jdbcTemplate.update("UPDATE players SET stats_json = ? WHERE id = ?",
                "{\"level\":2,\"maxHp\":120,\"hp\":0,\"isDead\":true,\"constellation\":\"demon_judge_of_fire\"}", playerId);
    }

    private ReviveRequest titleRevive(String titleKey, String requestKey) {
        ReviveRequest request = new ReviveRequest();
        request.setPlayerId(PLAYER_ID);
        request.setMethod("title");
        request.setTitleKey(titleKey);
        request.setRequestKey(requestKey);
        return request;
    }

    @Test
    void titleReviveRemovesOwnedTitle() throws Exception {
        makeDead(PLAYER_ID);
        seedSacrificeTitle(PLAYER_ID, "night_walker");
        jdbcTemplate.update("UPDATE players SET titles_json = ? WHERE id = ?",
                "[\"night_walker\"]", PLAYER_ID);
        LoginUserContext.set(new LoginUser(USER_ID, "h2-constellation-player", "player"));

        var result = playerController.revivePlayer(titleRevive("night_walker", "sacrifice-self"));
        assertEquals("title", result.getData().get("method"));
        assertEquals(Map.of("title_key", "night_walker", "name", "夜行者"),
                result.getData().get("sacrificed_title"));
        assertEquals(60, ((Number) objectMapper.readValue(playerMapper.selectById(PLAYER_ID).getStatsJson(),
                new TypeReference<Map<String, Object>>() {}).get("hp")).intValue());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_titles WHERE player_id = ?",
                Integer.class, PLAYER_ID));
        assertEquals("[]", playerMapper.selectById(PLAYER_ID).getTitlesJson());
        assertTrue(playerMapper.selectById(PLAYER_ID).getTitleProgressJson().contains("night_walker"));
        titleService.grantTitle(PLAYER_ID, "night_walker");
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_titles WHERE player_id = ?",
                Integer.class, PLAYER_ID));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_resource_transactions "
                + "WHERE source_type = 'title_sacrifice' AND resource_key = 'night_walker' "
                + "AND source_id = ?", Integer.class, String.valueOf(PLAYER_ID)));
    }

    @Test
    void peerReviveSacrificesReviverTitle() throws Exception {
        jdbcTemplate.update("INSERT INTO users (id, username, email, password_hash, role, status) "
                + "VALUES (?, ?, ?, ?, ?, ?)", USER_ID + 1, "target", "target@example.invalid",
                "test-only", "player", "normal");
        jdbcTemplate.update("INSERT INTO players (id, user_id, player_name, coins, stats_json) "
                + "VALUES (?, ?, ?, ?, ?)", PLAYER_ID + 1, USER_ID + 1, "目标", 0,
                "{\"level\":2,\"maxHp\":120,\"hp\":0,\"isDead\":true}");
        seedSacrificeTitle(PLAYER_ID, "night_walker");
        LoginUserContext.set(new LoginUser(USER_ID, "h2-constellation-player", "player"));
        PeerReviveRequest request = new PeerReviveRequest();
        request.setReviverId(PLAYER_ID);
        request.setTargetId(PLAYER_ID + 1);
        request.setMethod("title");
        request.setTitleKey("night_walker");
        request.setRequestKey("sacrifice-peer");

        var result = playerController.peerRevive(request);
        assertEquals("title", result.getData().get("method"));
        assertEquals(result.getData(), playerController.peerRevive(request).getData());
        request.setTitleKey("another_title");
        assertThrows(BusinessException.class, () -> playerController.peerRevive(request));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_titles WHERE player_id = ?",
                Integer.class, PLAYER_ID));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_resource_transactions "
                + "WHERE player_id = ? AND source_type = 'title_sacrifice' AND source_id = ?",
                Integer.class, PLAYER_ID, String.valueOf(PLAYER_ID + 1)));
        Map<String, Object> targetStats = objectMapper.readValue(playerMapper.selectById(PLAYER_ID + 1)
                .getStatsJson(), new TypeReference<>() {});
        assertEquals(false, targetStats.get("isDead"));
        assertEquals(60, ((Number) targetStats.get("hp")).intValue());
    }

    @Test
    void titleReviveReplayPayloadIncludesTitle() {
        makeDead(PLAYER_ID);
        seedSacrificeTitle(PLAYER_ID, "night_walker");
        LoginUserContext.set(new LoginUser(USER_ID, "h2-constellation-player", "player"));
        var first = playerController.revivePlayer(titleRevive("night_walker", "sacrifice-replay"));
        var replay = playerController.revivePlayer(titleRevive("night_walker", "sacrifice-replay"));
        assertEquals(first.getData(), replay.getData());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM player_resource_transactions "
                + "WHERE source_type = 'title_sacrifice'", Integer.class));
        assertThrows(BusinessException.class, () -> playerController.revivePlayer(
                titleRevive("different_title", "sacrifice-replay")));
    }
}
