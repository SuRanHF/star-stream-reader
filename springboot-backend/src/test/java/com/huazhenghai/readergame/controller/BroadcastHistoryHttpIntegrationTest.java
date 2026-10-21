package com.huazhenghai.readergame.controller;

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

/** Public activity history is read-only and never returns personal reward/configuration fields. */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:broadcast_history_http;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.ai-director.llm-api-key=",
        "app.scheduler.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/db/combat-persistence-test-schema.sql")
class BroadcastHistoryHttpIntegrationTest {

    private static final String PREFIX = "history-http-";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    @BeforeEach
    void seedHistoryCases() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            assertEquals("jdbc:h2:mem:broadcast_history_http", connection.getMetaData().getURL());
        }
        jdbcTemplate.update("DELETE FROM broadcast_events WHERE event_key LIKE ?", PREFIX + "%");

        LocalDateTime now = LocalDateTime.now().withNano(0);
        insertEvent(PREFIX + "completed", "completed", 10, 10,
                now.minusDays(1), now.plusHours(1), now.minusMinutes(5),
                "{\"worldline_shift\":3,\"scenario_pressure\":-2}",
                "{\"coins\":9999}", "{\"coins\":100}");
        insertEvent(PREFIX + "expired", "active", 10, 4,
                now.minusDays(2), now.minusHours(1), now.minusHours(3),
                "{\"worldline_shift\":8}", "{\"coins\":5000}", "{\"coins\":50}");
        insertEvent(PREFIX + "still-active", "active", 10, 7,
                now.minusMinutes(5), now.plusHours(1), now.minusMinutes(1),
                "{\"worldline_shift\":2}", "{\"coins\":4000}", "{\"coins\":40}");
        insertEvent(PREFIX + "upcoming", "active", 10, 0,
                now.plusMinutes(5), now.plusHours(2), now,
                "{\"worldline_shift\":1}", "{\"coins\":3000}", "{\"coins\":30}");
        insertEvent(PREFIX + "never-opened", "active", 10, 0,
                now.plusHours(1), now.minusMinutes(1), now.minusMinutes(2),
                "{\"worldline_shift\":9}", "{\"coins\":2000}", "{\"coins\":20}");
    }

    @Test
    void publicHistorySeparatesReachedAndExpiredEventsWithoutLeakingRewards() throws Exception {
        mockMvc.perform(get("/api/broadcast/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].eventKey").value(PREFIX + "completed"))
                .andExpect(jsonPath("$.data[0].status").value("completed"))
                .andExpect(jsonPath("$.data[0].progressPercent").value(100))
                .andExpect(jsonPath("$.data[0].worldlineEffectSummaries[0]").value("世界线偏移 +3"))
                .andExpect(jsonPath("$.data[0].worldlineEffectSummaries[1]").value("剧本压力 -2"))
                .andExpect(jsonPath("$.data[0].rewards").doesNotExist())
                .andExpect(jsonPath("$.data[0].personalRewards").doesNotExist())
                .andExpect(jsonPath("$.data[0].metadata").doesNotExist())
                .andExpect(jsonPath("$.data[1].eventKey").value(PREFIX + "expired"))
                .andExpect(jsonPath("$.data[1].status").value("expired"))
                .andExpect(jsonPath("$.data[1].progressPercent").value(40))
                .andExpect(jsonPath("$.data[1].worldlineEffectSummaries").isEmpty());
    }

    @Test
    void historyLimitIsClampedAndDoesNotMutateEventRows() throws Exception {
        LocalDateTime updateBefore = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM broadcast_events WHERE event_key = ?", LocalDateTime.class,
                PREFIX + "completed");

        mockMvc.perform(get("/api/broadcast/history").param("limit", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        assertEquals(updateBefore, jdbcTemplate.queryForObject(
                "SELECT updated_at FROM broadcast_events WHERE event_key = ?", LocalDateTime.class,
                PREFIX + "completed"));
        assertEquals("active", jdbcTemplate.queryForObject(
                "SELECT status FROM broadcast_events WHERE event_key = ?", String.class,
                PREFIX + "expired"));
    }

    private void insertEvent(String eventKey, String status, int target, int current,
                             LocalDateTime startAt, LocalDateTime endAt, LocalDateTime updatedAt,
                             String effects, String rewards, String personalRewards) {
        jdbcTemplate.update("INSERT INTO broadcast_events "
                        + "(event_key, title, description, type, status, target_value, current_value, "
                        + "start_at, end_at, worldline_effects_json, rewards_json, personal_rewards_json, "
                        + "created_at, updated_at) VALUES (?, ?, ?, 'community', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                eventKey, eventKey, "本轮共同参与记录", status, target, current, startAt, endAt,
                effects, rewards, personalRewards, updatedAt.minusDays(1), updatedAt);
    }
}
