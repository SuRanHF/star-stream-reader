package com.huazhenghai.readergame.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUser;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.PlayerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;

import javax.sql.DataSource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:player_reset_transaction;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.scheduler.enabled=false"
}, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Sql("/db/combat-persistence-test-schema.sql")
class PlayerResetTransactionIntegrationTest {
    private static final long PLAYER_ID = 83201L;
    private static final long USER_ID = 93201L;
    private static final String ORIGINAL_STATS = "{\"level\":8,\"hp\":12,\"coinsMarker\":\"retain?\","
            + "\"constellation\":\"morning_star\",\"constellationFavor\":71}";
    private static final String ORIGINAL_PROGRESS = "{\"storyPity\":5,\"storyEventsTriggered\":[\"old_event\"]}";

    @Autowired private PlayerController playerController;
    @Autowired private PlayerMapper playerMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;
    @Autowired private ObjectMapper objectMapper;
    @MockitoSpyBean private PlayerService playerService;

    @BeforeEach
    void seedDisposableH2Player() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:player_reset_transaction"));
        }
        jdbcTemplate.update("DELETE FROM players");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("INSERT INTO users (id, username, email, password_hash, role, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                USER_ID, "h2-reset-player", "h2-reset@example.invalid", "test-only", "player", "normal");
        jdbcTemplate.update("INSERT INTO players (id, user_id, player_name, current_main_chapter, "
                        + "current_stage, current_chapter, current_location, coins, story_fragments, "
                        + "scenario_proof, stats_json, stage_progress_json, story_flags_json, "
                        + "permanent_flags_json, route_history_json, decision_history_json, titles_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                PLAYER_ID, USER_ID, "重置事务隔离角色", "main_ch03", "vol3", "chapter_3", "dongmyo",
                321, 43, 11, ORIGINAL_STATS, ORIGINAL_PROGRESS, "{\"oldFlag\":true}",
                "{\"permanent\":true}", "[{\"route\":\"A0\"}]",
                "[{\"choice\":0}]", "[\"old_title\"]");
        LoginUserContext.set(new LoginUser(USER_ID, "h2-reset-player", "player"));
    }

    @AfterEach
    void clearLoginUser() {
        LoginUserContext.clear();
    }

    @Test
    void resetClearsCurrentFieldSet() throws Exception {
        assertTrue(playerController.resetPlayer(PLAYER_ID).isSuccess());

        Player actual = playerMapper.selectById(PLAYER_ID);
        assertEquals("main_ch01_paid_service", actual.getCurrentMainChapter());
        assertEquals("ch1_01_last_train", actual.getCurrentChapter());
        assertEquals("", actual.getCurrentLocation());
        assertEquals(0, actual.getCoins());
        assertEquals(0, actual.getStoryFragments());

        Map<String, Object> expectedStats = jsonMap("""
                {"level":1,"exp":0,"hp":100,"maxHp":100,"maxHpConstellationBonus":0,
                 "attack":10,"defense":5,"speed":3,"critRate":0.0,"critDamage":1.5,
                 "stamina":50,"maxStamina":50,"explorationPower":1,"luck":1,"dropRate":0,
                 "rating":1000,"pkWins":0,"pkLosses":0,"pkStreak":0,
                 "worldLineShift":0,"channelHeat":0,"freePoints":10,
                 "allocatedAtk":0,"allocatedDef":0,"allocatedSpd":0,"allocatedCrit":0,
                 "avatarRank":"F","avatarRankName":"临时化身","storyGrade":"ordinary",
                 "isResting":false,"constellation":"morning_star","constellationFavor":71}
                """);
        assertEquals(expectedStats, jsonMap(actual.getStatsJson()));
        assertEquals(jsonMap("""
                {"storyEventsTriggered":[],"sideEventsTriggered":[],"bossClues":{},
                 "opportunityEventsTriggered":[],"hiddenEventsTriggered":[],"storyPity":0,
                 "explorationsByLocation":{}}
                """), jsonMap(actual.getStageProgressJson()));

        // These fields are deliberately outside the legacy reset's current write set.
        assertEquals("vol3", actual.getCurrentStage());
        assertEquals(11, actual.getScenarioProof());
        assertEquals("{\"oldFlag\":true}", actual.getStoryFlagsJson());
        assertEquals("{\"permanent\":true}", actual.getPermanentFlagsJson());
        assertEquals("[{\"route\":\"A0\"}]", actual.getRouteHistoryJson());
        assertEquals("[{\"choice\":0}]", actual.getDecisionHistoryJson());
        assertEquals("[\"old_title\"]", actual.getTitlesJson());
    }

    @Test
    void resetRollsBackWhenAnyStepFails() {
        // Fail after the UPDATE, while the controller builds its response.
        doAnswer(invocation -> {
            assertEquals(0, playerMapper.selectById(PLAYER_ID).getCoins());
            throw new IllegalStateException("response construction failed");
        }).when(playerService).getPlayer(PLAYER_ID);

        assertThrows(IllegalStateException.class, () -> playerController.resetPlayer(PLAYER_ID));

        Player actual = playerMapper.selectById(PLAYER_ID);
        assertEquals("main_ch03", actual.getCurrentMainChapter());
        assertEquals("chapter_3", actual.getCurrentChapter());
        assertEquals("dongmyo", actual.getCurrentLocation());
        assertEquals(321, actual.getCoins());
        assertEquals(43, actual.getStoryFragments());
        assertEquals(ORIGINAL_STATS, actual.getStatsJson());
        assertEquals(ORIGINAL_PROGRESS, actual.getStageProgressJson());
    }

    private Map<String, Object> jsonMap(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<>() {});
    }
}
