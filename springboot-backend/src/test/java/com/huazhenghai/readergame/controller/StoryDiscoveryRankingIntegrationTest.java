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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises the public aggregate story ranking without exposing private story content. */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:story_discovery_ranking;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.ai-director.llm-api-key=",
        "app.scheduler.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/db/combat-persistence-test-schema.sql")
class StoryDiscoveryRankingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;
    @Autowired private JwtUtil jwtUtil;

    private LocalDateTime now;
    private long firstPlayerId;
    private long secondPlayerId;
    private long noDiscoveriesPlayerId;

    @BeforeEach
    void prepareStoryHistory() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:story_discovery_ranking"));
        }
        jdbcTemplate.update("DELETE FROM player_story_logs");
        jdbcTemplate.update("DELETE FROM players");
        firstPlayerId = insertPlayer("First explorer", 81001L);
        secondPlayerId = insertPlayer("Second explorer", 81002L);
        noDiscoveriesPlayerId = insertPlayer("No discoveries", 81003L);
        now = LocalDateTime.now().withNano(0);

        // Repeated choices count once per player and event key.
        insertStory(firstPlayerId, "private_story_alpha", 0, now.minusDays(2));
        insertStory(firstPlayerId, "private_story_alpha", 1, now.minusDays(1));
        insertStory(firstPlayerId, "private_story_beta", 0, now.minusDays(3));
        // Older history and unresolved story prompts are not new discoveries.
        insertStory(firstPlayerId, "private_story_old", 0, now.minusDays(9));
        insertStory(firstPlayerId, "private_story_pending", null, now.minusDays(1));

        insertStory(secondPlayerId, "another_private_story_one", 0, now.minusDays(4));
        insertStory(secondPlayerId, "another_private_story_two", 0, now.minusDays(5));
        // Future timestamps must not be included in the current rolling window.
        insertStory(secondPlayerId, "future_private_story", 0, now.plusDays(1));
    }

    @Test
    void countsUniqueFirstCompletionsInTheRollingSevenDayWindowAndHidesStoryDetails() throws Exception {
        mockMvc.perform(get("/api/rankings/story-discoveries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].playerName").value("First explorer"))
                .andExpect(jsonPath("$.data[0].discoveryCount").value(2))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[1].playerName").value("Second explorer"))
                .andExpect(jsonPath("$.data[1].discoveryCount").value(2))
                .andExpect(jsonPath("$.data[1].rank").value(1))
                .andExpect(jsonPath("$.data[0].eventKey").doesNotExist())
                .andExpect(jsonPath("$.data[0].choiceLabel").doesNotExist())
                .andExpect(jsonPath("$.data[0].consequenceText").doesNotExist());
    }

    @Test
    void appliesRequestedLimitAndUsesCompetitionRanksForTies() throws Exception {
        insertStory(insertPlayer("Third explorer", 81004L), "third_private_story", 0, now.minusDays(1));

        mockMvc.perform(get("/api/rankings/story-discoveries").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[1].rank").value(1));

        mockMvc.perform(get("/api/rankings/story-discoveries").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void returnsOwnedPersonalPositionAndNextScoreGapWithoutStoryContent() throws Exception {
        mockMvc.perform(get("/api/rankings/story-discoveries/" + firstPlayerId)
                        .header("Authorization", token(81001L, "first-explorer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ranked").value(true))
                .andExpect(jsonPath("$.data.rank").value(1))
                .andExpect(jsonPath("$.data.participantCount").value(2))
                .andExpect(jsonPath("$.data.discoveryCount").value(2))
                .andExpect(jsonPath("$.data.nextRankDiscoveryCount").doesNotExist())
                .andExpect(jsonPath("$.data.eventKey").doesNotExist())
                .andExpect(jsonPath("$.data.choiceLabel").doesNotExist());
    }

    @Test
    void computesPositionAndGapEvenWhenThePlayerIsOutsideTheDisplayedLimit() throws Exception {
        long thirdPlayerId = insertPlayer("Third explorer", 81004L);
        insertStory(thirdPlayerId, "third_private_story", 0, now.minusDays(1));

        mockMvc.perform(get("/api/rankings/story-discoveries").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(get("/api/rankings/story-discoveries/" + thirdPlayerId)
                        .header("Authorization", token(81004L, "third-explorer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rank").value(3))
                .andExpect(jsonPath("$.data.participantCount").value(3))
                .andExpect(jsonPath("$.data.discoveryCount").value(1))
                .andExpect(jsonPath("$.data.nextRankDiscoveryCount").value(2))
                .andExpect(jsonPath("$.data.discoveriesToNextRank").value(1));
    }

    @Test
    void playerWithoutRecentDiscoveriesGetsAFirstGoalInsteadOfAnOfficialRank() throws Exception {
        mockMvc.perform(get("/api/rankings/story-discoveries/" + noDiscoveriesPlayerId)
                        .header("Authorization", token(81003L, "new-explorer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ranked").value(false))
                .andExpect(jsonPath("$.data.rank").value(0))
                .andExpect(jsonPath("$.data.participantCount").value(2))
                .andExpect(jsonPath("$.data.discoveryCount").value(0))
                .andExpect(jsonPath("$.data.nextRankDiscoveryCount").value(2))
                .andExpect(jsonPath("$.data.discoveriesToNextRank").value(2));
    }

    @Test
    void personalPositionRequiresLoginAndRejectsOtherPlayers() throws Exception {
        mockMvc.perform(get("/api/rankings/story-discoveries/" + firstPlayerId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/rankings/story-discoveries/" + noDiscoveriesPlayerId)
                        .header("Authorization", token(81001L, "first-explorer")))
                .andExpect(status().isForbidden());
    }

    private String token(long userId, String username) {
        return "Bearer " + jwtUtil.generateToken(userId, username, "player");
    }

    private long insertPlayer(String name, long userId) {
        jdbcTemplate.update("INSERT INTO players (user_id, player_name, stats_json) VALUES (?, ?, ?)",
                userId, name, "{\"constellation\":\"morning_star\"}");
        return jdbcTemplate.queryForObject("SELECT id FROM players WHERE user_id = ?", Long.class, userId);
    }

    private void insertStory(long playerId, String eventKey, Integer choiceIndex, LocalDateTime createdAt) {
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO player_story_logs (player_id, event_key, location_key, choice_index, created_at) "
                            + "VALUES (?, ?, 'test_location', ?, ?)");
            statement.setLong(1, playerId);
            statement.setString(2, eventKey);
            if (choiceIndex == null) statement.setNull(3, java.sql.Types.INTEGER);
            else statement.setInt(3, choiceIndex);
            statement.setTimestamp(4, Timestamp.valueOf(createdAt));
            return statement;
        });
    }
}
