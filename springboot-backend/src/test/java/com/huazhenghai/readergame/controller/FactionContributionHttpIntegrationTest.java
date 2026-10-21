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
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies faction rank contribution cannot be injected through client HTTP requests. */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:faction_contribution_http;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.ai-director.llm-api-key=",
        "app.scheduler.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/db/combat-persistence-test-schema.sql")
class FactionContributionHttpIntegrationTest {

    private static final long OWNER_USER_ID = 73201L;
    private static final long OTHER_USER_ID = 73202L;
    private static final long OWNER_PLAYER_ID = 84201L;
    private static final long OTHER_PLAYER_ID = 84202L;
    private static final String FACTION_KEY = "faction-http-guard";
    private static final String REQUEST_BODY = "{\"playerId\":" + OWNER_PLAYER_ID
            + ",\"factionKey\":\"" + FACTION_KEY
            + "\",\"contributionType\":\"manual\",\"value\":2147483647"
            + ",\"source\":\"client\",\"relatedId\":\"forged\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;
    @Autowired private JwtUtil jwtUtil;

    @BeforeEach
    void seedIsolatedFactionState() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            assertEquals("jdbc:h2:mem:faction_contribution_http", connection.getMetaData().getURL());
        }

        jdbcTemplate.update("DELETE FROM faction_contributions WHERE faction_key = ?", FACTION_KEY);
        jdbcTemplate.update("DELETE FROM player_factions WHERE player_id IN (?, ?)",
                OWNER_PLAYER_ID, OTHER_PLAYER_ID);
        jdbcTemplate.update("DELETE FROM constellation_factions WHERE faction_key = ?", FACTION_KEY);
        jdbcTemplate.update("DELETE FROM players WHERE id IN (?, ?)", OWNER_PLAYER_ID, OTHER_PLAYER_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", OWNER_USER_ID, OTHER_USER_ID);

        insertUser(OWNER_USER_ID, "faction-owner");
        insertUser(OTHER_USER_ID, "faction-other");
        insertPlayer(OWNER_PLAYER_ID, OWNER_USER_ID, "阵营测试角色");
        insertPlayer(OTHER_PLAYER_ID, OTHER_USER_ID, "另一名阵营测试角色");
        jdbcTemplate.update("INSERT INTO constellation_factions "
                        + "(faction_key, name, level, exp, member_count, total_contribution, buffs_json, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                FACTION_KEY, "隔离测试阵营", 1, 31L, 2, 45L, "{}", 1);
        insertFactionMember(OWNER_PLAYER_ID, 5L, 7L);
        insertFactionMember(OTHER_PLAYER_ID, 3L, 8L);
        jdbcTemplate.update("INSERT INTO faction_contributions "
                        + "(faction_key, player_id, contribution_type, value, source, related_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                FACTION_KEY, OWNER_PLAYER_ID, "combat", 7L, "combat", "existing-source");
    }

    @Test
    void anonymousClientCannotWriteFactionContribution() throws Exception {
        mockMvc.perform(post("/api/factions/contribute")
                        .contentType(APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        assertFactionStateUnchanged();
    }

    @Test
    void authenticatedMemberCannotInjectContributionIntoFactionRanking() throws Exception {
        mockMvc.perform(post("/api/factions/contribute")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(
                                OWNER_USER_ID, "faction-owner", "player"))
                        .contentType(APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_FIELD_VALUE"))
                .andExpect(jsonPath("$.error.message")
                        .value("阵营贡献只能由探索、战斗、PK、世界首领等实际游戏行动自动累计"));

        assertFactionStateUnchanged();
    }

    @Test
    void authenticatedMemberCannotSubmitContributionForAnotherPlayersCharacter() throws Exception {
        String requestForOtherPlayer = REQUEST_BODY.replace(
                "\"playerId\":" + OWNER_PLAYER_ID, "\"playerId\":" + OTHER_PLAYER_ID);

        mockMvc.perform(post("/api/factions/contribute")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(
                                OWNER_USER_ID, "faction-owner", "player"))
                        .contentType(APPLICATION_JSON)
                        .content(requestForOtherPlayer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        assertFactionStateUnchanged();
    }

    private void assertFactionStateUnchanged() {
        assertEquals(45L, jdbcTemplate.queryForObject(
                "SELECT total_contribution FROM constellation_factions WHERE faction_key = ?",
                Long.class, FACTION_KEY));
        assertEquals(31L, jdbcTemplate.queryForObject(
                "SELECT exp FROM constellation_factions WHERE faction_key = ?", Long.class, FACTION_KEY));
        assertEquals(5L, jdbcTemplate.queryForObject(
                "SELECT reputation FROM player_factions WHERE player_id = ? AND faction_key = ?",
                Long.class, OWNER_PLAYER_ID, FACTION_KEY));
        assertEquals(7L, jdbcTemplate.queryForObject(
                "SELECT contribution_total FROM player_factions WHERE player_id = ? AND faction_key = ?",
                Long.class, OWNER_PLAYER_ID, FACTION_KEY));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM faction_contributions WHERE faction_key = ?", Integer.class, FACTION_KEY));
        assertEquals(7L, jdbcTemplate.queryForObject(
                "SELECT value FROM faction_contributions WHERE faction_key = ? AND player_id = ?",
                Long.class, FACTION_KEY, OWNER_PLAYER_ID));
    }

    private void insertUser(long id, String username) {
        jdbcTemplate.update("INSERT INTO users (id, username, email, password_hash, role, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, username, username + "@example.invalid", "not-used", "player", "normal");
    }

    private void insertPlayer(long id, long userId, String name) {
        jdbcTemplate.update("INSERT INTO players (id, user_id, player_name, stats_json) VALUES (?, ?, ?, ?)",
                id, userId, name, "{\"constellation\":\"demon_judge_of_fire\"}");
    }

    private void insertFactionMember(long playerId, long reputation, long contributionTotal) {
        jdbcTemplate.update("INSERT INTO player_factions "
                        + "(player_id, faction_key, role, reputation, contribution_total, joined_at, status) "
                        + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)",
                playerId, FACTION_KEY, "member", reputation, contributionTotal, "active");
    }
}
