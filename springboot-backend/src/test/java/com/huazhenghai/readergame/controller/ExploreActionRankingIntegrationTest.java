package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:explore_action_ranking;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.ai-director.llm-api-key=",
        "app.scheduler.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/db/combat-persistence-test-schema.sql")
class ExploreActionRankingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;
    @Autowired private JwtUtil jwtUtil;

    private LocalDateTime now;
    private long activePlayerId;
    private long tiedPlayerId;
    private long otherPlayerId;
    private long inactivePlayerId;

    @BeforeEach
    void prepareExplorationReceipts() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:explore_action_ranking"));
        }
        jdbcTemplate.update("DELETE FROM player_logs");
        jdbcTemplate.update("DELETE FROM pk_records");
        jdbcTemplate.update("DELETE FROM rankings");
        jdbcTemplate.update("DELETE FROM players");
        activePlayerId = insertPlayer("行动较多者", 93001L);
        tiedPlayerId = insertPlayer("行动并列者", 93002L);
        otherPlayerId = insertPlayer("并列玩家", 93003L);
        inactivePlayerId = insertPlayer("近期未探索", 93004L);
        now = LocalDateTime.now().withNano(0);

        // A duplicate receipt with the same request key represents a replay, not a second action.
        insertReceipt(activePlayerId, "explore-a", "exploration", now.minusDays(2));
        insertReceipt(activePlayerId, "explore-a", "battle", now.minusDays(2));
        insertReceipt(activePlayerId, "explore-b", "story", now.minusDays(1));
        insertReceipt(activePlayerId, "explore-c", "battle", now);
        insertReceipt(tiedPlayerId, "tied-a", "resource", now.minusDays(1));
        insertReceipt(tiedPlayerId, "tied-b", "battle", now);
        insertReceipt(otherPlayerId, "other-a", "resource", now.minusDays(1));
        insertReceipt(otherPlayerId, "other-b", "exploration", now);

        // Requests outside the rolling window, future-dated records, and unrelated logs do not count.
        insertReceipt(otherPlayerId, "stale", "exploration", now.minusDays(8));
        insertReceipt(otherPlayerId, "future", "exploration", now.plusDays(1));
        insertLog(otherPlayerId, "{\"kind\":\"story_choice\",\"requestKey\":\"story-only\"}", now);
        insertLog(otherPlayerId, "{\"kind\":\"exploration_result\"}", now);
    }

    @Test
    void publicBoardCountsUniqueAcceptedActionsAndSharesTiedRanks() throws Exception {
        mockMvc.perform(get("/api/rankings/exploration-actions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].playerId").value(activePlayerId))
                .andExpect(jsonPath("$.data[0].actionCount").value(3))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[1].actionCount").value(2))
                .andExpect(jsonPath("$.data[1].rank").value(2))
                .andExpect(jsonPath("$.data[2].actionCount").value(2))
                .andExpect(jsonPath("$.data[2].rank").value(2))
                .andExpect(jsonPath("$.data[0].requestKey").doesNotExist());
    }

    @Test
    void publicLimitDoesNotChangeTheScoreDefinition() throws Exception {
        mockMvc.perform(get("/api/rankings/exploration-actions").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].actionCount").value(3));
    }

    @Test
    void personalPositionShowsNextScoreAndCanBeReadOnlyByTheOwner() throws Exception {
        mockMvc.perform(get("/api/rankings/exploration-actions/" + tiedPlayerId)
                        .header("Authorization", token(93002L, "explore-rank-tied")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participated").value(true))
                .andExpect(jsonPath("$.data.rank").value(2))
                .andExpect(jsonPath("$.data.participantCount").value(3))
                .andExpect(jsonPath("$.data.actionCount").value(2))
                .andExpect(jsonPath("$.data.nextRankActionCount").value(3))
                .andExpect(jsonPath("$.data.actionsToNextRank").value(1));

        mockMvc.perform(get("/api/rankings/exploration-actions/" + tiedPlayerId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/rankings/exploration-actions/" + tiedPlayerId)
                        .header("Authorization", token(93003L, "explore-rank-other")))
                .andExpect(status().isForbidden());
    }

    @Test
    void inactivePlayerGetsTheFirstActionGoal() throws Exception {
        mockMvc.perform(get("/api/rankings/exploration-actions/" + inactivePlayerId)
                        .header("Authorization", token(93004L, "explore-rank-inactive")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participated").value(false))
                .andExpect(jsonPath("$.data.rank").value(0))
                .andExpect(jsonPath("$.data.participantCount").value(3))
                .andExpect(jsonPath("$.data.actionCount").value(0))
                .andExpect(jsonPath("$.data.nextRankActionCount").value(2))
                .andExpect(jsonPath("$.data.actionsToNextRank").value(2));
    }

    private String token(long userId, String username) {
        return "Bearer " + jwtUtil.generateToken(userId, username, "player");
    }

    private long insertPlayer(String name, long userId) {
        jdbcTemplate.update("INSERT INTO players (user_id, player_name, stats_json) VALUES (?, ?, ?)",
                userId, name, "{\"constellation\":\"morning_star\"}");
        return jdbcTemplate.queryForObject("SELECT id FROM players WHERE user_id = ?", Long.class, userId);
    }

    private void insertReceipt(long playerId, String requestKey, String eventType, LocalDateTime createdAt) {
        String metadata = "{\"kind\":\"exploration_result\",\"requestKey\":\"" + requestKey
                + "\",\"requestResponse\":{\"result_type\":\"" + eventType + "\"}}";
        insertLog(playerId, metadata, createdAt);
    }

    private void insertLog(long playerId, String metadata, LocalDateTime createdAt) {
        jdbcTemplate.update("INSERT INTO player_logs (player_id, type, message, metadata_json, created_at) "
                        + "VALUES (?, 'exploration', 'test exploration action', ?, ?)",
                playerId, metadata, Timestamp.valueOf(createdAt));
    }
}
