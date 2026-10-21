package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.service.AvatarRankService;
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
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies the public avatar-rank leaderboard contract against an isolated H2 database. */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:avatar_rank_leaderboard;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.ai-director.llm-api-key=",
        "app.scheduler.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/db/combat-persistence-test-schema.sql")
public class AvatarRankLeaderboardIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;
    @Autowired private AvatarRankService avatarRankService;

    @BeforeEach
    void prepareLeaderboardRows() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            assertEquals("jdbc:h2:mem:avatar_rank_leaderboard", connection.getMetaData().getURL());
        }
        jdbcTemplate.update("DELETE FROM rankings");
        jdbcTemplate.update("DELETE FROM players");
        jdbcTemplate.update("DELETE FROM avatar_rank_configs");

        jdbcTemplate.update("INSERT INTO avatar_rank_configs (rank_key, rank_name, display_name, order_num, enabled) "
                + "VALUES ('F', 'F', 'F', 0, 1), ('E', 'E', 'E', 1, 1), ('D', 'D', 'D', 2, 1)");
        insertPlayer("Lowest", "F", 1);
        insertPlayer("Middle", "E", 2);
        insertPlayer("Highest", "D", 3);
    }

    @Test
    void anonymousLeaderboardHonorsRequestedLimitAndConfiguredOrdering() throws Exception {
        mockMvc.perform(get("/api/avatar-rank/leaderboard").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].PLAYERNAME").value("Highest"))
                .andExpect(jsonPath("$.data[1].PLAYERNAME").value("Middle"));
    }

    @Test
    void leaderboardDefaultsToTwoHundredAndRejectsOutOfRangeLimits() throws Exception {
        mockMvc.perform(get("/api/avatar-rank/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));

        mockMvc.perform(get("/api/avatar-rank/leaderboard").param("limit", "201"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void avatarRankInfoIncludesDeterministicGlobalPositionAndPlayerCount() {
        Long middlePlayerId = jdbcTemplate.queryForObject(
                "SELECT id FROM players WHERE player_name = 'Middle'", Long.class);

        var rankInfo = avatarRankService.getAvatarRankInfo(middlePlayerId);

        assertEquals(2L, rankInfo.getRankPosition());
        assertEquals(3L, rankInfo.getTotalPlayers());
    }

    private void insertPlayer(String name, String rank, int level) {
        jdbcTemplate.update(
                "INSERT INTO players (user_id, player_name, stats_json) VALUES (?, ?, ?)",
                70000L + level,
                name,
                "{\"avatarRank\":\"" + rank + "\",\"level\":" + level + "}");
    }
}
