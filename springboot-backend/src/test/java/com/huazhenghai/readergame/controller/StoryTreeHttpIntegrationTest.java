package com.huazhenghai.readergame.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.security.JwtUtil;
import com.huazhenghai.readergame.service.ExploreService;
import com.huazhenghai.readergame.service.impl.StoryTreeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real MyBatis, JWT and HTTP checks against a disposable H2 schema. */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:story_tree_http;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.ai-director.llm-api-key=",
        "app.scheduler.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/db/combat-persistence-test-schema.sql")
class StoryTreeHttpIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private JwtUtil jwt;
    @Autowired private ObjectMapper json;
    @Autowired private ExploreService exploreService;
    @Autowired private StoryTreeService storyTreeService;
    @MockitoBean private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private List<Map<String, Object>> seed;

    @BeforeEach
    void setup() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertEquals("jdbc:h2:mem:story_tree_http", connection.getMetaData().getURL());
        }
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);
        jdbc.update("DELETE FROM exploration_events");
        jdbc.update("DELETE FROM locations");
        jdbc.update("DELETE FROM players");
        jdbc.update("DELETE FROM users");
        try (var input = getClass().getResourceAsStream("/db/seed_explore.json")) {
            seed = json.readValue(input, new TypeReference<>() { });
        }
        for (Map<String, Object> entry : seed) {
            if ("location".equals(entry.get("type"))) jdbc.update(
                    "INSERT INTO locations(location_key,name,unlock_conditions_json,min_level) VALUES(?,?,?,?)",
                    entry.get("location_key"), entry.get("name"), encoded(entry.get("unlock_conditions")),
                    entry.get("min_level"));
            if ("event".equals(entry.get("type")) && "story".equals(entry.get("event_type"))) jdbc.update(
                    "INSERT INTO exploration_events(event_key,event_type,stage_key,location_key,name,"
                            + "required_conditions_json,choices_json,repeatable,weight,stamina_cost,enabled) "
                            + "VALUES(?,?,?,?,?,?,?,?,10,5,1)",
                    entry.get("event_key"), entry.get("event_type"), entry.get("stage_key"),
                    entry.get("location_key"), entry.get("name"), encoded(entry.get("required_conditions")),
                    encoded(entry.get("choices")), entry.get("repeatable"));
        }
        insertUser(1, "player");
        insertPlayer(1, 1, "[]", "{\"storyEventsTriggered\":[]}", 1);
    }

    @Test
    void newSaveHidesStoryAndAuthorizationChecksOwner() throws Exception {
        mvc.perform(get("/api/explore/story-tree/1"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/explore/story-tree/1").header("Authorization", token(2)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        JsonNode data = fetch(1);
        assertEquals(27, data.path("nodes").size());
        assertEquals(4, data.path("chapters").size());
        assertTrue(data.path("route_continuation").path("event_key").isNull());
        assertEquals("available", node(data, "root", "vol1_ch1").path("state").asText());
        for (JsonNode n : data.path("nodes")) {
            assertFalse(n.has("event_key"));
            assertFalse(n.has("title"));
            if (!"available".equals(n.path("state").asText())) assertFalse(n.has("location_name"));
        }
        for (JsonNode e : data.path("edges")) {
            assertTrue(e.path("choice_label").isNull() || e.path("choice_label").isMissingNode());
            assertTrue(e.path("stats").isNull() || e.path("stats").isMissingNode());
        }
        assertFalse(data.toString().contains("chungmuro_script_blank"));
        assertFalse(data.toString().contains("dongdaemun_flame_giant_story"));
    }

    @Test
    void advertisedRootCanBeDrawnByTheRealExplorationService() throws Exception {
        JsonNode tree = fetch(1);
        assertEquals("available", node(tree, "root", "vol1_ch1").path("state").asText());
        Map<String, Object> draw = exploreService.startExplore(1L, "ruined_station", "story-tree-root-draw", 1L);
        assertEquals("story", draw.get("result_type"));
        assertEquals("station_first_trace", ((Map<?, ?>) draw.get("result")).get("event_key"));
    }

    @Test
    void laterAvailableStoryObeysLocationLevelAndRealDraw() throws Exception {
        jdbc.update("UPDATE players SET decision_history_json=?,stage_progress_json=?,stats_json=? WHERE id=1",
                encoded(List.of(choice("station_first_trace", 0))),
                "{\"storyEventsTriggered\":[\"station_first_trace\"]}",
                "{\"level\":1,\"stamina\":100}");
        JsonNode locked = fetch(1);
        assertFalse(hasAvailableAt(locked, "vol1_ch2", "companion"));
        jdbc.update("UPDATE players SET stats_json=? WHERE id=1", "{\"level\":2,\"stamina\":100}");
        JsonNode available = fetch(1);
        assertTrue(hasAvailableAt(available, "vol1_ch2", "companion"));
        Map<String, Object> draw = exploreService.startExplore(1L, "dongmyo", "story-tree-dongmyo-draw", 1L);
        assertEquals("dongmyo_market_story", ((Map<?, ?>) draw.get("result")).get("event_key"));
    }

    @Test
    void companionTipAndOtherBranchAreProjectedWithoutSpoilers() throws Exception {
        List<Map<String, Object>> decisions = List.of(
                choice("station_first_trace", 0), choice("dongmyo_market_story", 0),
                choice("dongmyo_whisperer_story", 0), choice("chungmuro_iron_king_story", 0),
                choice("chungmuro_shadow_king_story", 0), choice("chungmuro_conspiracy_story", 0));
        List<String> triggered = decisions.stream().map(d -> (String) d.get("event_key")).toList();
        jdbc.update("UPDATE players SET decision_history_json=?, stage_progress_json=?, stats_json=? WHERE id=1",
                encoded(decisions), encoded(Map.of("storyEventsTriggered", triggered,
                        "routePending", true, "routePendingEventKey", "chungmuro_conspiracy_story")),
                "{\"level\":6}");
        JsonNode data = fetch(1);
        JsonNode tip = findByEvent(data, "chungmuro_conspiracy_story");
        assertEquals("done", tip.path("state").asText());
        assertEquals("active", data.path("route_continuation").path("status").asText());
        assertTrue(hasAvailableAt(data, "vol1_ch4", "companion"));
        for (JsonNode n : data.path("nodes")) if ("nameless".equals(n.path("branch").asText())) {
            assertEquals("closed", n.path("state").asText());
            assertFalse(n.has("event_key"));
            assertFalse(n.has("title"));
        }
        assertFalse(data.toString().contains("dongdaemun_flame_giant_story"));
        assertTrue(data.path("edges").findValuesAsText("gate").contains("and"));
        assertTrue(data.path("edges").findValuesAsText("gate").stream().anyMatch(g -> g.startsWith("or:")));
        String tipId = tip.path("node_id").asText();
        long andGates = 0;
        long orGates = 0;
        for (JsonNode edge : data.path("edges")) if (tipId.equals(edge.path("to").asText())) {
            if ("and".equals(edge.path("gate").asText())) andGates++;
            if (edge.path("gate").asText().startsWith("or:")) orGates++;
        }
        assertTrue(andGates >= 2, "root and iron-king constraints share one AND gate");
        assertTrue(orGates >= 2, "the direct choice unlocker accepts both options");
    }

    @Test
    void completedOldTipsDrawTheirOwnFirstContinuationAtTheCorrectLocation() throws Exception {
        Map<String, List<Map<String, Object>>> histories = Map.of(
                "A0", List.of(choice("station_first_trace", 0), choice("dongmyo_market_story", 0),
                        choice("dongmyo_whisperer_story", 1), choice("chungmuro_iron_king_story", 0),
                        choice("chungmuro_shadow_king_story", 1), choice("chungmuro_conspiracy_story", 0)),
                "A1", List.of(choice("station_first_trace", 0), choice("dongmyo_market_story", 1),
                        choice("dongmyo_whisperer_story", 2), choice("chungmuro_iron_king_story", 1),
                        choice("chungmuro_underground_resistance", 0), choice("chungmuro_compromise_ledger", 1)),
                "U0", List.of(choice("station_first_trace", 1), choice("geumho_shelter_story", 0),
                        choice("geumho_underground_story", 1), choice("chungmuro_unnamed_crossing", 0),
                        choice("chungmuro_revelation_story", 1), choice("chungmuro_script_blank", 0)),
                "U1", List.of(choice("station_first_trace", 1), choice("geumho_shelter_story", 1),
                        choice("geumho_underground_story", 0), choice("chungmuro_unnamed_crossing", 1),
                        choice("chungmuro_nameless_drift", 1), choice("chungmuro_hidden_reader_wall", 1)));
        Map<String, String> next = Map.of(
                "A0", "chungmuro_council_audit", "A1", "dongmyo_ledger_delivery",
                "U0", "chungmuro_paper_summon", "U1", "geumho_wall_message");
        Map<String, String> locations = Map.of(
                "A0", "chungmuro", "A1", "dongmyo", "U0", "chungmuro", "U1", "geumho_station");
        for (String route : List.of("A0", "A1", "U0", "U1")) {
            List<Map<String, Object>> history = histories.get(route);
            List<String> triggered = history.stream().map(d -> (String) d.get("event_key")).toList();
            String oldTip = triggered.get(5);
            jdbc.update("UPDATE players SET decision_history_json=?,stage_progress_json=?,stats_json=? WHERE id=1",
                    encoded(history), encoded(Map.of("storyEventsTriggered", triggered,
                            "routePending", true, "routePendingEventKey", oldTip)),
                    "{\"level\":6,\"stamina\":100}");
            JsonNode tree = fetch(1);
            assertEquals("active", tree.path("route_continuation").path("status").asText(), route);
            assertTrue(hasAvailableAt(tree, "vol1_ch4", route.startsWith("A") ? "companion" : "nameless"));
            Map<String, Object> draw = exploreService.startExplore(
                    1L, locations.get(route), "second-growth-old-tip-" + route, 1L);
            assertEquals("story", draw.get("result_type"), route);
            assertEquals(next.get(route), ((Map<?, ?>) draw.get("result")).get("event_key"), route);
        }
    }

    @Test
    void aNewDirectContinuationMakesTheOldTipDone() throws Exception {
        List<Map<String, Object>> decisions = List.of(
                choice("station_first_trace", 0), choice("dongmyo_market_story", 0),
                choice("dongmyo_whisperer_story", 0), choice("chungmuro_iron_king_story", 0),
                choice("chungmuro_shadow_king_story", 0), choice("chungmuro_conspiracy_story", 0));
        jdbc.update("UPDATE players SET decision_history_json=?,stage_progress_json=?,stats_json=? WHERE id=1",
                encoded(decisions), encoded(Map.of("storyEventsTriggered",
                        decisions.stream().map(d -> d.get("event_key")).toList(),
                        "routePending", true, "routePendingEventKey", "chungmuro_conspiracy_story")),
                "{\"level\":6}");
        jdbc.update("INSERT INTO exploration_events(event_key,event_type,stage_key,location_key,name,"
                        + "required_conditions_json,choices_json,repeatable,weight,enabled)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,1)",
                "direct_continuation", "story", "vol1_ch4", "chungmuro", "新剧情",
                "{\"choice_unlockers\":[{\"event_key\":\"chungmuro_conspiracy_story\",\"choice_indices\":[0,1]}]}",
                "[]", 0, 10);
        JsonNode data = fetch(1);
        assertEquals("done", findByEvent(data, "chungmuro_conspiracy_story").path("state").asText());
        JsonNode next = findNodeByStage(data, "vol1_ch4");
        assertEquals("available", next.path("state").asText());
        assertFalse(next.has("event_key"));
        assertEquals("active", data.path("route_continuation").path("status").asText());
        assertEquals(4, data.path("chapters").size());
    }

    @Test
    void thresholdBandsExcludeAdministratorsAndDeduplicateCharacters() throws Exception {
        jdbc.update("UPDATE players SET decision_history_json=?, stage_progress_json=? WHERE id=1",
                encoded(List.of(choice("station_first_trace", 0))),
                "{\"storyEventsTriggered\":[\"station_first_trace\"]}");
        for (int i = 2; i <= 19; i++) {
            insertUser(i, "player");
            insertPlayer(i, i, encoded(List.of(choice("station_first_trace", 0),
                    choice("station_first_trace", 1))), "{}", 1);
        }
        insertUser(30, "admin");
        insertPlayer(30, 30, encoded(List.of(choice("station_first_trace", 0))), "{}", 1);
        JsonNode first = fetch(1);
        assertTrue(first.path("edges").findValues("stats").stream().allMatch(JsonNode::isNull));
        insertUser(20, "player");
        insertPlayer(20, 20, encoded(List.of(choice("station_first_trace", 0))), "{}", 1);
        JsonNode second = fetch(1);
        List<JsonNode> stats = second.path("edges").findValues("stats").stream()
                .filter(n -> n.isObject()).toList();
        assertFalse(stats.isEmpty());
        assertEquals("20+", stats.get(0).path("sample_band").asText());
        assertFalse(stats.get(0).has("sample"));
        assertEquals("20+", second.path("route_stats").path("sample_band").asText());
    }

    @Test
    void redisOutageFallsBackToUncachedPlayerAggregation() throws Exception {
        assertInstanceOf(java.util.concurrent.atomic.AtomicLong.class,
                ReflectionTestUtils.getField(storyTreeService, "redisRetryAfterNanos")).set(0);
        jdbc.update("UPDATE players SET decision_history_json=?,stage_progress_json=? WHERE id=1",
                encoded(List.of(choice("station_first_trace", 0))),
                "{\"storyEventsTriggered\":[\"station_first_trace\"]}");
        for (int i = 2; i <= 20; i++) {
            insertUser(i, "player");
            insertPlayer(i, i, encoded(List.of(choice("station_first_trace", 0))), "{}", 1);
        }
        when(values.get(anyString())).thenThrow(new IllegalStateException("isolated Redis outage"));
        doThrow(new IllegalStateException("isolated Redis outage"))
                .when(values).set(anyString(), anyString(), any(java.time.Duration.class));
        JsonNode data = fetch(1);
        assertTrue(data.path("edges").findValues("stats").stream()
                .anyMatch(s -> "20+".equals(s.path("sample_band").asText())));
        fetch(1);
        verify(values, times(1)).get(anyString());
    }

    @Test
    void coldCacheTimingAtFiveHundredCharactersInH2() throws Exception {
        jdbc.update("UPDATE players SET decision_history_json=?,stage_progress_json=? WHERE id=1",
                encoded(List.of(choice("station_first_trace", 0))),
                "{\"storyEventsTriggered\":[\"station_first_trace\"]}");
        for (int i = 2; i <= 500; i++) {
            insertUser(i, "player");
            insertPlayer(i, i, encoded(List.of(choice("station_first_trace", i % 2))), "{}", 1);
        }
        long[] nanos = new long[10];
        for (int i = 0; i < nanos.length; i++) {
            long started = System.nanoTime();
            JsonNode data = fetch(1); // The mocked Redis returns a miss on every request.
            nanos[i] = System.nanoTime() - started;
            assertTrue(data.path("edges").findValues("stats").stream()
                    .anyMatch(s -> "500+".equals(s.path("sample_band").asText())));
        }
        Arrays.sort(nanos);
        System.out.println("STORY_TREE_H2_COLD_P95_MS=" + nanos[9] / 1_000_000.0);
    }

    /** Run explicitly with -Dtest=StoryTreeHttpIntegrationTest#storyTreePerformanceBenchmark -DstoryTreeBenchmark=true. */
    @Test
    @Tag("benchmark")
    @EnabledIfSystemProperty(named = "storyTreeBenchmark", matches = "true")
    @Transactional // All synthetic users and players are rolled back after the benchmark.
    void storyTreePerformanceBenchmark() throws Exception {
        Map<String, Integer> choiceCounts = new java.util.HashMap<>();
        for (Map<String, Object> event : seed) if ("event".equals(event.get("type"))
                && event.get("choices") instanceof List<?> choices)
            choiceCounts.put((String) event.get("event_key"), choices.size());
        for (int route = 0; route < 6; route++) for (Map<String, Object> decision : benchmarkRoute(route)) {
            String key = (String) decision.get("event_key");
            assertTrue(choiceCounts.containsKey(key), key);
            assertTrue((int) decision.get("choice_index") < choiceCounts.get(key), key);
        }
        jdbc.update("UPDATE players SET decision_history_json=? WHERE id=1", encoded(benchmarkRoute(0)));
        for (int total : List.of(1_000, 5_000)) {
            addBenchmarkPlayers(total == 1_000 ? 2 : 1_001, total);
            assertEquals(total, jdbc.queryForObject("SELECT COUNT(*) FROM players", Integer.class));
            assertEquals(total, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM players p JOIN users u ON u.id=p.user_id WHERE u.role='player'",
                    Integer.class));
            for (String mode : List.of("cold", "hot", "redis_down")) {
                assertInstanceOf(java.util.concurrent.atomic.AtomicLong.class,
                        ReflectionTestUtils.getField(storyTreeService, "redisRetryAfterNanos")).set(0);
                reset(values);
                if ("hot".equals(mode)) {
                    AtomicReference<String> cached = new AtomicReference<>();
                    when(values.get(anyString())).thenAnswer(invocation -> cached.get());
                    org.mockito.Mockito.doAnswer(invocation -> {
                        cached.set(invocation.getArgument(1));
                        return null;
                    }).when(values).set(anyString(), anyString(), any(java.time.Duration.class));
                    fetch(1); // Populate this run's Redis substitute before timing.
                    assertNotNull(cached.get());
                } else if ("redis_down".equals(mode)) {
                    when(values.get(anyString())).thenAnswer(invocation -> {
                        Thread.sleep(500); // Simulate the configured Redis timeout, without a real Redis server.
                        throw new IllegalStateException("isolated Redis timeout");
                    });
                    doThrow(new IllegalStateException("isolated Redis outage"))
                            .when(values).set(anyString(), anyString(), any(java.time.Duration.class));
                } else {
                    when(values.get(anyString())).thenReturn(null);
                }
                if (!"redis_down".equals(mode)) for (int i = 0; i < 5; i++) fetch(1);
                double[] millis = new double[20];
                for (int i = 0; i < millis.length; i++) {
                    long start = System.nanoTime();
                    JsonNode data = fetch(1);
                    millis[i] = (System.nanoTime() - start) / 1_000_000.0;
                    assertEquals(27, data.path("nodes").size());
                }
                Arrays.sort(millis);
                System.out.printf(Locale.ROOT,
                        "STORY_TREE_BENCH players=%d mode=%s p50_ms=%.2f p95_ms=%.2f max_ms=%.2f%n",
                        total, mode, millis[9], millis[18], millis[19]);
                if ("redis_down".equals(mode)) {
                    verify(values, times(1)).get(anyString());
                    assertTrue(millis[18] < 200,
                            "Redis outage P95 exceeds the 200 ms story-tree contract");
                }
            }
        }
    }

    @AfterTransaction
    void benchmarkPlayersWereRolledBack() {
        if (Boolean.getBoolean("storyTreeBenchmark"))
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM players WHERE player_name LIKE '树图基准%'", Integer.class));
    }

    private void addBenchmarkPlayers(int from, int to) throws Exception {
        List<String> histories = new java.util.ArrayList<>();
        for (int id = from; id <= to; id++) histories.add(encoded(benchmarkRoute(id % 6)));
        jdbc.batchUpdate("INSERT INTO users(id,username,email,password_hash,role) VALUES(?,?,?,?,?)",
                new BatchPreparedStatementSetter() {
                    @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
                        int id = from + i;
                        ps.setInt(1, id);
                        ps.setString(2, "tree-" + id);
                        ps.setString(3, "tree-" + id + "@example.test");
                        ps.setString(4, "unused");
                        ps.setString(5, "player");
                    }
                    @Override public int getBatchSize() { return histories.size(); }
                });
        jdbc.batchUpdate("INSERT INTO players(id,user_id,player_name,stats_json,story_flags_json,"
                        + "stage_progress_json,decision_history_json,current_chapter) VALUES(?,?,?,?,?,?,?,?)",
                new BatchPreparedStatementSetter() {
                    @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
                        int id = from + i;
                        ps.setInt(1, id);
                        ps.setInt(2, id);
                        ps.setString(3, "树图基准" + id);
                        ps.setString(4, "{\"level\":6,\"stamina\":100}");
                        ps.setString(5, "{}");
                        ps.setString(6, "{}");
                        ps.setString(7, histories.get(i));
                        ps.setString(8, "1");
                    }
                    @Override public int getBatchSize() { return histories.size(); }
                });
    }

    private List<Map<String, Object>> benchmarkRoute(int route) {
        if (route <= 2) return List.of(
                choice("station_first_trace", 0), choice("dongmyo_market_story", route == 2 ? 1 : 0),
                choice("dongmyo_whisperer_story", route == 2 ? 2 : 1),
                choice("chungmuro_iron_king_story", route == 2 ? 1 : 0),
                choice(route == 2 ? "chungmuro_underground_resistance" : "chungmuro_shadow_king_story", 0),
                choice(route == 2 ? "chungmuro_compromise_ledger" : "chungmuro_conspiracy_story", 0),
                choice(route == 2 ? "dongmyo_ledger_delivery" : "chungmuro_council_audit", 0),
                choice(route == 2 ? "dongmyo_resistance_weigh" : "chungmuro_shadow_vote", 0),
                choice(route == 2 ? "geumho_resistance_muster" : "chungmuro_power_fork", route == 2 ? 0 : route));
        return List.of(
                choice("station_first_trace", 1), choice("geumho_shelter_story", route == 5 ? 1 : 0),
                choice("geumho_underground_story", route == 5 ? 0 : 1),
                choice("chungmuro_unnamed_crossing", route == 5 ? 1 : 0),
                choice(route == 5 ? "chungmuro_nameless_drift" : "chungmuro_revelation_story", 1),
                choice(route == 5 ? "chungmuro_hidden_reader_wall" : "chungmuro_script_blank", 0),
                choice(route == 5 ? "geumho_wall_message" : "chungmuro_paper_summon", 0),
                choice(route == 5 ? "geumho_nameless_cell" : "chungmuro_blank_revision", 0),
                choice(route == 5 ? "chungmuro_reader_anchor" : "chungmuro_name_fork", route == 3 ? 0 : 1));
    }

    private void insertUser(long id, String role) {
        jdbc.update("INSERT INTO users(id,username,email,password_hash,role) VALUES(?,?,?,?,?)",
                id, "tree-" + id, "tree-" + id + "@example.test", "unused", role);
    }

    private void insertPlayer(long id, long userId, String decisions, String progress, int level) {
        jdbc.update("INSERT INTO players(id,user_id,player_name,stats_json,story_flags_json,"
                + "stage_progress_json,decision_history_json,current_chapter) VALUES(?,?,?,?,?,?,?,?)",
                id, userId, "树图测试" + id, "{\"level\":" + level + ",\"stamina\":100}",
                "{}", progress, decisions, "1");
    }

    private JsonNode fetch(long userId) throws Exception {
        String body = mvc.perform(get("/api/explore/story-tree/1")
                        .header("Authorization", token(userId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode wrapper = json.readTree(body);
        assertTrue(wrapper.path("success").asBoolean());
        return wrapper.path("data");
    }

    private String token(long userId) {
        return "Bearer " + jwt.generateToken(userId, "tree-" + userId, "player");
    }

    private Map<String, Object> choice(String event, int index) {
        return Map.of("event_key", event, "choice_index", index);
    }

    private String encoded(Object value) throws Exception { return value == null ? null : json.writeValueAsString(value); }

    private JsonNode node(JsonNode data, String branch, String stage) {
        for (JsonNode n : data.path("nodes"))
            if (branch.equals(n.path("branch").asText()) && stage.equals(n.path("stage_key").asText())) return n;
        fail("Node not found"); return null;
    }

    private JsonNode findByEvent(JsonNode data, String key) {
        for (JsonNode n : data.path("nodes")) if (key.equals(n.path("event_key").asText())) return n;
        fail("Event not found: " + key); return null;
    }

    private JsonNode findNodeByStage(JsonNode data, String stage) {
        for (JsonNode n : data.path("nodes")) if (stage.equals(n.path("stage_key").asText())) return n;
        fail("Stage not found: " + stage); return null;
    }

    private boolean hasAvailableAt(JsonNode data, String stage, String branch) {
        for (JsonNode n : data.path("nodes"))
            if (stage.equals(n.path("stage_key").asText())
                    && branch.equals(n.path("branch").asText())
                    && "available".equals(n.path("state").asText())) return true;
        return false;
    }
}
