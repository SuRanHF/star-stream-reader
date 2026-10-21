package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.security.JwtUtil;
import com.huazhenghai.readergame.service.BroadcastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies client contribution writes are rejected through the real HTTP/JWT path on isolated H2. */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:broadcast_contribution_http;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.ai-director.llm-api-key=",
        "app.scheduler.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/db/combat-persistence-test-schema.sql")
class BroadcastContributionHttpIntegrationTest {

    private static final long OWNER_USER_ID = 73101L;
    private static final long OTHER_USER_ID = 73102L;
    private static final long OWNER_PLAYER_ID = 84101L;
    private static final long OTHER_PLAYER_ID = 84102L;
    private static final String EVENT_KEY = "broadcast-http-guard";
    private static final String REQUEST_BODY = "{\"eventKey\":\"" + EVENT_KEY
            + "\",\"playerId\":" + OWNER_PLAYER_ID
            + ",\"value\":2147483647,\"contributionType\":\"exploration\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;
    @Autowired private JwtUtil jwtUtil;
    @MockitoBean private BroadcastService broadcastService;

    @BeforeEach
    void seedIsolatedState() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            assertEquals("jdbc:h2:mem:broadcast_contribution_http", connection.getMetaData().getURL());
        }

        jdbcTemplate.update("DELETE FROM broadcast_contributions WHERE event_key = ?", EVENT_KEY);
        jdbcTemplate.update("DELETE FROM broadcast_events WHERE event_key = ?", EVENT_KEY);
        jdbcTemplate.update("DELETE FROM players WHERE id IN (?, ?)", OWNER_PLAYER_ID, OTHER_PLAYER_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", OWNER_USER_ID, OTHER_USER_ID);

        insertUser(OWNER_USER_ID, "broadcast-owner");
        insertUser(OTHER_USER_ID, "broadcast-other");
        insertPlayer(OWNER_PLAYER_ID, OWNER_USER_ID, "放送测试角色");
        insertPlayer(OTHER_PLAYER_ID, OTHER_USER_ID, "另一名测试角色");
        jdbcTemplate.update("INSERT INTO broadcast_events "
                        + "(event_key, title, type, status, target_value, current_value) VALUES (?, ?, ?, ?, ?, ?)",
                EVENT_KEY, "隔离放送", "community", "active", 100, 17);
        jdbcTemplate.update("INSERT INTO broadcast_contributions "
                        + "(event_key, player_id, contribution_value, contribution_type) VALUES (?, ?, ?, ?)",
                EVENT_KEY, OWNER_PLAYER_ID, 7, "exploration");
    }

    @Test
    void anonymousClientCannotWriteContribution() throws Exception {
        mockMvc.perform(post("/api/broadcast/contribute")
                        .contentType(APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        assertProgressUnchanged();
        verify(broadcastService, never()).contribute(anyString(), anyLong(), anyInt(), anyString());
    }

    @Test
    void authenticatedOwnerCannotInjectContributionIntoPublicRanking() throws Exception {
        mockMvc.perform(post("/api/broadcast/contribute")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(
                                OWNER_USER_ID, "broadcast-owner", "player"))
                        .contentType(APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_FIELD_VALUE"))
                .andExpect(jsonPath("$.error.message")
                        .value("公共活动贡献只能由探索、战斗、PK 等实际游戏行动自动累计"));

        assertProgressUnchanged();
        verify(broadcastService, never()).contribute(anyString(), anyLong(), anyInt(), anyString());
    }

    @Test
    void authenticatedPlayerCannotSubmitContributionForAnotherPlayersCharacter() throws Exception {
        String requestForOtherPlayer = REQUEST_BODY.replace(
                "\"playerId\":" + OWNER_PLAYER_ID, "\"playerId\":" + OTHER_PLAYER_ID);

        mockMvc.perform(post("/api/broadcast/contribute")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(
                                OWNER_USER_ID, "broadcast-owner", "player"))
                        .contentType(APPLICATION_JSON)
                        .content(requestForOtherPlayer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        assertProgressUnchanged();
        verify(broadcastService, never()).contribute(anyString(), anyLong(), anyInt(), anyString());
    }

    private void assertProgressUnchanged() {
        assertEquals(17, jdbcTemplate.queryForObject(
                "SELECT current_value FROM broadcast_events WHERE event_key = ?", Integer.class, EVENT_KEY));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM broadcast_contributions WHERE event_key = ?", Integer.class, EVENT_KEY));
        assertEquals(7, jdbcTemplate.queryForObject(
                "SELECT contribution_value FROM broadcast_contributions WHERE event_key = ? AND player_id = ?",
                Integer.class, EVENT_KEY, OWNER_PLAYER_ID));
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
}
