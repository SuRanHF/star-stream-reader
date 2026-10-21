package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.service.BroadcastService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasItems;

/** Verifies the real broadcast service and HTTP endpoint respect an event's time window. */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:broadcast_active_window_http;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.ai-director.llm-api-key=",
        "app.scheduler.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/db/combat-persistence-test-schema.sql")
class BroadcastActiveWindowHttpIntegrationTest {

    private static final String PREFIX = "window-http-";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;
    @Autowired private BroadcastService broadcastService;

    private String expiredKey;

    @BeforeEach
    void seedWindowedEvents() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            assertEquals("jdbc:h2:mem:broadcast_active_window_http", connection.getMetaData().getURL());
        }

        jdbcTemplate.update("DELETE FROM broadcast_contributions WHERE event_key LIKE ?", PREFIX + "%");
        jdbcTemplate.update("DELETE FROM broadcast_events WHERE event_key LIKE ?", PREFIX + "%");

        LocalDateTime now = LocalDateTime.now();
        expiredKey = PREFIX + "expired";
        insertEvent(expiredKey, now.minusDays(1), now.minusSeconds(1));
        insertEvent(PREFIX + "upcoming", now.plusMinutes(1), now.plusHours(1));
        insertEvent(PREFIX + "live", now.minusMinutes(1), now.plusMinutes(1));
        insertEvent(PREFIX + "unbounded", null, null);
    }

    @Test
    void activeEndpointAndSummaryOnlyIncludeEventsInsideTheirWindow() throws Exception {
        mockMvc.perform(get("/api/broadcast/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].eventKey")
                        .value(hasItems(PREFIX + "live", PREFIX + "unbounded")));

        var summary = broadcastService.getBroadcastSummary();
        assertEquals(2, summary.getActiveCount());
        assertEquals(2, summary.getTopEvents().size());
    }

    @Test
    void expiredActiveEventCannotAccumulateContributionEvenIfSchedulerIsDisabled() {
        int currentValue = jdbcTemplate.queryForObject(
                "SELECT current_value FROM broadcast_events WHERE event_key = ?", Integer.class, expiredKey);

        assertThrows(BusinessException.class,
                () -> broadcastService.contribute(expiredKey, 99101L, 1, "exploration"));

        assertEquals(currentValue, jdbcTemplate.queryForObject(
                "SELECT current_value FROM broadcast_events WHERE event_key = ?", Integer.class, expiredKey));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM broadcast_contributions WHERE event_key = ?", Integer.class, expiredKey));
    }

    private void insertEvent(String eventKey, LocalDateTime startAt, LocalDateTime endAt) {
        jdbcTemplate.update("INSERT INTO broadcast_events "
                        + "(event_key, title, description, type, status, target_value, current_value, start_at, end_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                eventKey, eventKey, "隔离的活动时间窗口测试", "community", "active", 10, 0, startAt, endAt);
    }
}
