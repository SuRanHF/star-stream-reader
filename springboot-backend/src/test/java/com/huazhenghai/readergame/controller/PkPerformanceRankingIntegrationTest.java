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
        "spring.datasource.url=jdbc:h2:mem:pk_performance_ranking;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.ai-director.llm-api-key=",
        "app.scheduler.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/db/combat-persistence-test-schema.sql")
class PkPerformanceRankingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;
    @Autowired private JwtUtil jwtUtil;

    private LocalDateTime now;
    private long firstPlayerId;
    private long secondPlayerId;
    private long thirdPlayerId;
    private long inactivePlayerId;

    @BeforeEach
    void prepareRecentPkRecords() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:pk_performance_ranking"));
        }
        jdbcTemplate.update("DELETE FROM pk_records");
        jdbcTemplate.update("DELETE FROM rankings");
        jdbcTemplate.update("DELETE FROM players");
        firstPlayerId = insertPlayer("近期定级甲", 92001L);
        secondPlayerId = insertPlayer("近期定级乙", 92002L);
        thirdPlayerId = insertPlayer("近期定级丙", 92003L);
        inactivePlayerId = insertPlayer("近期未参赛", 92004L);
        now = LocalDateTime.now().withNano(0);

        // First player nets +10 from one win and one loss; third player also nets +10.
        insertRecord(firstPlayerId, secondPlayerId, firstPlayerId, secondPlayerId,
                "{\"attacker_change\":20,\"defender_change\":-20}", now.minusDays(2));
        insertRecord(thirdPlayerId, firstPlayerId, thirdPlayerId, firstPlayerId,
                "{\"attacker_change\":10,\"defender_change\":-10}", now.minusDays(1));
        // Both stale and future records must fall outside the rolling seven-day window.
        insertRecord(secondPlayerId, thirdPlayerId, secondPlayerId, thirdPlayerId,
                "{\"attacker_change\":100,\"defender_change\":-100}", now.minusDays(9));
        insertRecord(secondPlayerId, thirdPlayerId, secondPlayerId, thirdPlayerId,
                "{\"attacker_change\":100,\"defender_change\":-100}", now.plusDays(1));
    }

    @Test
    void publicLeaderboardRanksNetRatingChangesAndSharesTies() throws Exception {
        mockMvc.perform(get("/api/rankings/pk-performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].ratingDelta").value(10))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[1].ratingDelta").value(10))
                .andExpect(jsonPath("$.data[1].rank").value(1))
                .andExpect(jsonPath("$.data[2].ratingDelta").value(-20))
                .andExpect(jsonPath("$.data[2].rank").value(3))
                .andExpect(jsonPath("$.data[0].matchCount").value(2))
                .andExpect(jsonPath("$.data[0].winCount").value(1))
                .andExpect(jsonPath("$.data[0].rating").doesNotExist());
    }

    @Test
    void appliesTheRequestedPublicLimit() throws Exception {
        mockMvc.perform(get("/api/rankings/pk-performance").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].rank").value(1));
    }

    @Test
    void personalPositionReportsTheGapEvenOutsideTheDisplayedLeaderboardLimit() throws Exception {
        mockMvc.perform(get("/api/rankings/pk-performance").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(get("/api/rankings/pk-performance/" + secondPlayerId)
                        .header("Authorization", token(92002L, "pk-performance-second")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participated").value(true))
                .andExpect(jsonPath("$.data.rank").value(3))
                .andExpect(jsonPath("$.data.participantCount").value(3))
                .andExpect(jsonPath("$.data.ratingDelta").value(-20))
                .andExpect(jsonPath("$.data.matchCount").value(1))
                .andExpect(jsonPath("$.data.winCount").value(0))
                .andExpect(jsonPath("$.data.nextRankRatingDelta").value(10))
                .andExpect(jsonPath("$.data.pointsToNextRank").value(30));
    }

    @Test
    void inactivePlayerGetsAFirstMatchGoalAndPrivateRouteChecksOwnership() throws Exception {
        mockMvc.perform(get("/api/rankings/pk-performance/" + inactivePlayerId)
                        .header("Authorization", token(92004L, "pk-performance-inactive")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participated").value(false))
                .andExpect(jsonPath("$.data.rank").value(0))
                .andExpect(jsonPath("$.data.participantCount").value(3))
                .andExpect(jsonPath("$.data.nextRankRatingDelta").value(10))
                .andExpect(jsonPath("$.data.pointsToNextRank").value(10));

        mockMvc.perform(get("/api/rankings/pk-performance/" + firstPlayerId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/rankings/pk-performance/" + firstPlayerId)
                        .header("Authorization", token(92004L, "pk-performance-inactive")))
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

    private void insertRecord(long attackerId, long defenderId, long winnerId, long loserId,
                              String ratingChange, LocalDateTime createdAt) {
        jdbcTemplate.update("""
                        INSERT INTO pk_records (attacker_id, defender_id, winner_id, loser_id,
                            rating_change_json, created_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                attackerId, defenderId, winnerId, loserId, ratingChange, Timestamp.valueOf(createdAt));
    }
}
