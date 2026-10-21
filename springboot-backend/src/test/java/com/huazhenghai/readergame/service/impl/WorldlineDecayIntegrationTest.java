package com.huazhenghai.readergame.service.impl;

import com.huazhenghai.readergame.service.ChatService;
import com.huazhenghai.readergame.service.ExploreService;
import com.huazhenghai.readergame.service.NarrativeService;
import com.huazhenghai.readergame.service.WorldlineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies scheduled worldline decay against a disposable H2 database. */
@SpringBootTest(
        properties = {
                "spring.profiles.active=test",
                "spring.datasource.url=jdbc:h2:mem:worldline_decay_regression;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "app.scheduler.enabled=false"
        },
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("test")
@Sql("/db/combat-persistence-test-schema.sql")
class WorldlineDecayIntegrationTest {

    @Autowired private WorldlineService worldlineService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    @MockitoBean private ChatService chatService;
    @MockitoBean private ExploreService exploreService;
    @MockitoBean private NarrativeService narrativeService;

    @BeforeEach
    void insertIsolatedWorldlineStates() {
        jdbcTemplate.update("DELETE FROM worldline_change_logs");
        jdbcTemplate.update("DELETE FROM world_states");
        jdbcTemplate.update("DELETE FROM broadcast_events");
        jdbcTemplate.update("DELETE FROM world_bosses");
        insertState("worldline_shift", "世界线偏移", "{\"totalShift\":10,\"revision\":4}");
        insertState("starstream_attention", "星流关注", "{\"attention\":20,\"peak\":99}");
        insertState("scenario_pressure", "剧本压力",
                "{\"pressure\":20.1,\"dangerLevel\":2,\"chapterIndex\":7}");
    }

    @Test
    void decayChangesOnlyContinuousMetricsAndRecomputesIntegerDangerTier() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:worldline_decay_regression"),
                    "Worldline decay regression must never connect to the development database");
        }

        assertEquals(3, worldlineService.applyDecay(0.01));

        Map<String, Map<String, Object>> states = worldlineService.getWorldStates().stream()
                .collect(Collectors.toMap(
                        state -> (String) state.get("stateKey"),
                        state -> castValueMap(state.get("value"))));

        assertEquals(9.9, ((Number) states.get("worldline_shift").get("totalShift")).doubleValue(), 0.0001);
        assertEquals(4, ((Number) states.get("worldline_shift").get("revision")).intValue());
        assertEquals(19.8, ((Number) states.get("starstream_attention").get("attention")).doubleValue(), 0.0001);
        assertEquals(99, ((Number) states.get("starstream_attention").get("peak")).intValue());
        assertEquals(19.899, ((Number) states.get("scenario_pressure").get("pressure")).doubleValue(), 0.0001);
        assertEquals(7, ((Number) states.get("scenario_pressure").get("chapterIndex")).intValue());
        assertInstanceOf(Integer.class, states.get("scenario_pressure").get("dangerLevel"));
        assertEquals(1, states.get("scenario_pressure").get("dangerLevel"));
    }

    @Test
    void historyResolvesBroadcastAndBossKeysToPlayerReadableNames() {
        jdbcTemplate.update("""
                INSERT INTO broadcast_events (event_key, title, type, status)
                VALUES (?, ?, ?, ?)
                """, "daily_story_broadcast", "故事共鸣", "story", "completed");
        jdbcTemplate.update("""
                INSERT INTO world_bosses (boss_no, name, status)
                VALUES (?, ?, ?)
                """, "WB-204", "沉眠的龙", "killed");

        insertHistory("daily_story_broadcast");
        insertHistory("world_boss_WB-204");
        insertHistory("world_boss_expired_WB-205");

        Map<String, String> displayByCause = worldlineService.getWorldlineHistory().stream()
                .collect(Collectors.toMap(
                        entry -> (String) entry.get("cause"),
                        entry -> (String) entry.get("causeDisplay")));

        assertEquals("全服活动「故事共鸣」达成", displayByCause.get("daily_story_broadcast"));
        assertEquals("世界首领「沉眠的龙」被击破", displayByCause.get("world_boss_WB-204"));
        assertEquals("世界首领未能及时击破", displayByCause.get("world_boss_expired_WB-205"));
    }

    private void insertState(String stateKey, String name, String valueJson) {
        jdbcTemplate.update("""
                INSERT INTO world_states (state_key, name, value_json)
                VALUES (?, ?, ?)
                """, stateKey, name, valueJson);
    }

    private void insertHistory(String cause) {
        jdbcTemplate.update("""
                INSERT INTO worldline_change_logs (
                    state_key, state_name, value_key, change_amount, before_value, after_value, cause
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, "worldline_shift", "世界线偏移", "totalShift", 1.0, 0.0, 1.0, cause);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castValueMap(Object value) {
        return (Map<String, Object>) value;
    }
}
