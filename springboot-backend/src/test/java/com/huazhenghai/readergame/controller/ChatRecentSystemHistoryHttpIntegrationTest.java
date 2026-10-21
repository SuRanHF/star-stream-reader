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
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Confirms persisted system broadcasts can be recovered from world-chat history in isolated H2. */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:chat_recent_system_http;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.ai-director.llm-api-key=",
        "app.scheduler.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/db/combat-persistence-test-schema.sql")
class ChatRecentSystemHistoryHttpIntegrationTest {

    private static final long TEST_USER_ID = 99101L;
    private static final String TEST_USERNAME = "chat-history-reader";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;
    @Autowired private JwtUtil jwtUtil;

    @BeforeEach
    void seedChatHistory() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            assertEquals("jdbc:h2:mem:chat_recent_system_http", connection.getMetaData().getURL());
        }

        jdbcTemplate.update("DELETE FROM chat_messages");
        LocalDateTime now = LocalDateTime.now();
        insertMessage("system", "broadcast", "先前的全服公告", now.minusMinutes(3));
        insertMessage("world", "player", "世界频道玩家消息", now.minusMinutes(2));
        insertMessage("system", "broadcast", "最新的全服公告", now.minusMinutes(1));
        insertMessage("faction", "player", "仅阵营频道可见", now);
    }

    @Test
    void worldHistoryReturnsPersistentSystemAnnouncementsInChronologicalOrder() throws Exception {
        mockMvc.perform(get("/api/chat/recent")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(TEST_USER_ID, TEST_USERNAME, "player"))
                        .param("channel", "world").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].content").value("先前的全服公告"))
                .andExpect(jsonPath("$.data[1].content").value("世界频道玩家消息"))
                .andExpect(jsonPath("$.data[2].content").value("最新的全服公告"))
                .andExpect(jsonPath("$.data[0].channel").value("system"))
                .andExpect(jsonPath("$.data[1].channel").value("world"))
                .andExpect(jsonPath("$.data[2].channel").value("system"));
    }

    @Test
    void factionHistoryRemainsIsolatedFromWorldAndSystemMessages() throws Exception {
        mockMvc.perform(get("/api/chat/recent")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(TEST_USER_ID, TEST_USERNAME, "player"))
                        .param("channel", "faction").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].channel").value("faction"))
                .andExpect(jsonPath("$.data[0].content").value("仅阵营频道可见"));
    }

    private void insertMessage(String channel, String messageType, String content, LocalDateTime createdAt) {
        jdbcTemplate.update("INSERT INTO chat_messages (channel, sender_player_id, sender_name, message_type, content, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                channel, "system".equals(channel) ? 0L : 99001L,
                "system".equals(channel) ? "系统" : "隔离测试玩家", messageType, content, createdAt);
    }
}
