package com.huazhenghai.readergame.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.security.JwtUtil;
import com.huazhenghai.readergame.websocket.WebSocketSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:chat_contract_http;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.ai-director.llm-api-key=",
        "app.scheduler.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/db/combat-persistence-test-schema.sql")
class ChatContractHttpIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private JwtUtil jwt;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private WebSocketSessionManager sessions;

    @BeforeEach
    void setUp() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            assertEquals("jdbc:h2:mem:chat_contract_http", connection.getMetaData().getURL());
        }
        jdbc.update("DELETE FROM chat_messages");
        jdbc.update("DELETE FROM party_members");
        jdbc.update("DELETE FROM parties");
        jdbc.update("DELETE FROM player_factions");
        jdbc.update("DELETE FROM player_titles");
        jdbc.update("DELETE FROM titles");
        jdbc.update("DELETE FROM players");
        for (long id : new long[] {101, 102, 103, 201, 301}) {
            jdbc.update("INSERT INTO players (id, user_id, player_name, current_stage, stats_json, stage_progress_json) VALUES (?, ?, ?, 'vol1_ch3', '{\"constellation\":\"morning_star\"}', '{}')",
                    id, id + 1000, "玩家" + id);
        }
        jdbc.update("INSERT INTO parties (party_no, leader_player_id, status) VALUES ('A', 101, 'active'), ('B', 103, 'active')");
        jdbc.update("INSERT INTO party_members (party_no, player_id, status) VALUES ('A', 101, 'active'), ('A', 102, 'active'), ('B', 103, 'active')");
        jdbc.update("INSERT INTO player_factions (player_id, faction_key, role, reputation, contribution_total, joined_at, status) VALUES (201, 'red', 'member', 0, 0, CURRENT_TIMESTAMP, 'active'), (102, 'red', 'member', 0, 0, CURRENT_TIMESTAMP, 'active'), (103, 'blue', 'member', 0, 0, CURRENT_TIMESTAMP, 'active')");
    }

    private String token(long playerId) {
        return "Bearer " + jwt.generateToken(playerId + 1000, "user" + playerId, "player");
    }

    private String body(long playerId, String channel, String content, Long replyToId) throws Exception {
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("playerId", playerId);
        request.put("channel", channel);
        request.put("content", content);
        if (replyToId != null) request.put("replyToId", replyToId);
        return mapper.writeValueAsString(request);
    }

    @Test
    void teamPushAndHistoryExcludeOtherTeamAndLegacyUnscopedRows() throws Exception {
        jdbc.update("INSERT INTO chat_messages (channel, sender_player_id, sender_name, message_type, content) VALUES ('team', 101, '旧消息', 'player', '无归属旧队伍消息')");
        mvc.perform(post("/api/chat/send").header("Authorization", token(101))
                        .contentType(MediaType.APPLICATION_JSON).content(body(101, "team", "A队私聊", null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.channel").value("team"));
        verify(sessions).sendToPlayer(eq(101L), any());
        verify(sessions).sendToPlayer(eq(102L), any());
        verify(sessions, never()).sendToPlayer(eq(103L), any());
        verify(sessions, never()).broadcast(any());
        assertEquals("A", jdbc.queryForObject("SELECT scope_id FROM chat_messages WHERE content='A队私聊'", String.class));
        mvc.perform(get("/api/chat/recent").header("Authorization", token(101))
                        .param("channel", "team").param("playerId", "101"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].content").value("A队私聊"));
        mvc.perform(get("/api/chat/recent").header("Authorization", token(103))
                        .param("channel", "team").param("playerId", "103"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void factionPushAndHistoryExcludeOtherFactionAndLegacyUnscopedRows() throws Exception {
        jdbc.update("INSERT INTO chat_messages (channel, sender_player_id, sender_name, message_type, content) VALUES ('faction', 201, '旧消息', 'player', '无归属旧阵营消息')");
        mvc.perform(post("/api/chat/send").header("Authorization", token(201))
                        .contentType(MediaType.APPLICATION_JSON).content(body(201, "faction", "红阵营消息", null)))
                .andExpect(status().isOk());
        verify(sessions).sendToPlayer(eq(201L), any());
        verify(sessions).sendToPlayer(eq(102L), any());
        verify(sessions, never()).sendToPlayer(eq(103L), any());
        mvc.perform(get("/api/chat/recent").header("Authorization", token(103))
                        .param("channel", "faction").param("playerId", "103"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/chat/recent").header("Authorization", token(201))
                        .param("channel", "faction").param("playerId", "201"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void unknownAndSystemChannelsAreRejectedForSendAndHistory() throws Exception {
        mvc.perform(post("/api/chat/send").header("Authorization", token(301))
                        .contentType(MediaType.APPLICATION_JSON).content(body(301, "teem", "拼写错误", null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_FIELD_VALUE"));
        mvc.perform(post("/api/chat/send").header("Authorization", token(301))
                        .contentType(MediaType.APPLICATION_JSON).content(body(301, "system", "伪造", null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_FIELD_VALUE"));
        mvc.perform(get("/api/chat/recent").header("Authorization", token(301)).param("channel", "teem"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_FIELD_VALUE"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM chat_messages", Integer.class));
    }

    @Test
    void replyIsScopedAndSnapshotSurvivesOriginalDeletion() throws Exception {
        jdbc.update("INSERT INTO titles (title_key, name, enabled) VALUES ('hero', '勇者', 1)");
        jdbc.update("INSERT INTO player_titles (player_id, title_key, equipped) VALUES (301, 'hero', 1)");
        String originalContent = "公告".repeat(25);
        jdbc.update("INSERT INTO chat_messages (channel, sender_player_id, sender_name, message_type, content) VALUES ('system', 0, '系统', 'system', ?)", originalContent);
        Long originalId = jdbc.queryForObject("SELECT MAX(id) FROM chat_messages", Long.class);
        mvc.perform(post("/api/chat/send").header("Authorization", token(301))
                        .contentType(MediaType.APPLICATION_JSON).content(body(301, "world", "回复公告", originalId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.replyTo.id").value(originalId))
                .andExpect(jsonPath("$.data.replyTo.senderName").value("系统"))
                .andExpect(jsonPath("$.data.senderStage").value("第一卷·第三章"))
                .andExpect(jsonPath("$.data.senderTitle").value("勇者"));
        jdbc.update("DELETE FROM chat_messages WHERE id=?", originalId);
        mvc.perform(get("/api/chat/recent").header("Authorization", token(301)).param("channel", "world"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].replyTo.contentPreview").value("公告".repeat(20)));
        jdbc.update("INSERT INTO chat_messages (channel, scope_id, sender_player_id, sender_name, message_type, content) VALUES ('team', 'B', 103, '玩家103', 'player', 'B队消息')");
        Long otherId = jdbc.queryForObject("SELECT MAX(id) FROM chat_messages", Long.class);
        mvc.perform(post("/api/chat/send").header("Authorization", token(101))
                        .contentType(MediaType.APPLICATION_JSON).content(body(101, "team", "跨队回复", otherId)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_FIELD_VALUE"));
        jdbc.update("INSERT INTO chat_messages (channel, sender_player_id, sender_name, message_type, content) VALUES ('team', 101, '旧消息', 'player', '无归属旧队伍消息')");
        Long legacyId = jdbc.queryForObject("SELECT MAX(id) FROM chat_messages", Long.class);
        mvc.perform(post("/api/chat/send").header("Authorization", token(101))
                        .contentType(MediaType.APPLICATION_JSON).content(body(101, "team", "回复旧消息", legacyId)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_FIELD_VALUE"));
    }

    @Test
    void beforeIdPaginatesByIdDescendingThenReturnsAscending() throws Exception {
        for (int i = 1; i <= 5; i++)
            jdbc.update("INSERT INTO chat_messages (channel, sender_player_id, sender_name, message_type, content, created_at) VALUES ('world', 0, '测试', 'player', ?, DATEADD('MINUTE', ?, CURRENT_TIMESTAMP))",
                    "消息" + i, 5 - i);
        Long thirdId = jdbc.queryForObject("SELECT id FROM chat_messages WHERE content='消息3'", Long.class);
        mvc.perform(get("/api/chat/recent").header("Authorization", token(301))
                        .param("channel", "world").param("limit", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].content").value("消息4"))
                .andExpect(jsonPath("$.data[1].content").value("消息5"));
        mvc.perform(get("/api/chat/recent").header("Authorization", token(301))
                        .param("channel", "world").param("limit", "2").param("beforeId", thirdId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].content").value("消息1"))
                .andExpect(jsonPath("$.data[1].content").value("消息2"));
    }

    @Test
    void costPreviewRequiresOwnedPlayerAndReturnsCurrentLocationRange() throws Exception {
        jdbc.update("UPDATE players SET current_location='station', stats_json='{\"constellation\":\"morning_star\",\"stamina\":19}' WHERE id=101");
        jdbc.update("INSERT INTO locations (location_key, name, unlock_conditions_json, event_rates_json) VALUES ('station', '车站', '{}', '{}')");
        jdbc.update("INSERT INTO exploration_events (event_key, event_type, location_key, weight, stamina_cost, enabled) VALUES ('preview_one', 'resource', 'station', 1, 7, 1)");
        mvc.perform(get("/api/explore/cost-preview/101").header("Authorization", token(101)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.minStaminaCost").value(7))
                .andExpect(jsonPath("$.data.maxStaminaCost").value(7))
                .andExpect(jsonPath("$.data.currentStamina").value(19));
        mvc.perform(get("/api/explore/cost-preview/101").header("Authorization", token(103)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
