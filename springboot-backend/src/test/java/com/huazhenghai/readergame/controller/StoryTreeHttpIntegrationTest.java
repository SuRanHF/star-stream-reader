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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    void routeCodexHidesUnreachedTipsAndRequiresOwner() throws Exception {
        mvc.perform(get("/api/explore/route-codex/1"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/explore/route-codex/1").header("Authorization", token(2)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        JsonNode codex = fetchCodex(1);
        assertEquals(2, codex.path("growth").asInt());
        assertEquals(8, codex.path("total_tips").asInt());
        assertTrue(codex.path("my_tip_id").isNull());
        List<String> treeIds = new java.util.ArrayList<>();
        for (JsonNode node : fetch(1).path("nodes")) treeIds.add(node.path("node_id").asText());
        for (JsonNode tip : codex.path("tips")) {
            assertTrue(treeIds.contains(tip.path("tip_id").asText()));
            assertFalse(tip.path("reached").asBoolean());
            for (String hidden : List.of("title", "branch", "chapter_label", "event_key", "location_name"))
                assertFalse(tip.has(hidden), hidden);
            assertTrue(tip.path("stats").isNull());
        }
    }

    @Test
    void routeShareRequiresOwnerAndOnlyPublicReadIsAnonymous() throws Exception {
        mvc.perform(post("/api/explore/route-share/1"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/explore/route-share/1").header("Authorization", token(2)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/explore/route-share/1").header("Authorization", token(2)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/public/route-share/invalid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Robots-Tag", "noindex"));
        mvc.perform(get("/api/public/private"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void routeShareFreezesChoicesAndStatsAndStoresOnlyHash() throws Exception {
        jdbc.update("UPDATE players SET decision_history_json=?,stage_progress_json=? WHERE id=1",
                encoded(List.of(choice("station_first_trace", 0))), "{}");
        for (int i = 2; i <= 20; i++) {
            insertUser(i, "player");
            insertPlayer(i, i, encoded(List.of(choice("station_first_trace", i % 2))), "{}", 1);
        }
        JsonNode first = createShare();
        String code = first.path("code").asText();
        assertEquals(43, code.length());
        assertTrue(first.path("expires_at").asText().endsWith("Z"));
        assertEquals(first, createShare());
        Map<String, Object> stored = jdbc.queryForMap(
                "SELECT code_hash,nonce,snapshot_json FROM route_shares WHERE player_id=1");
        assertEquals(64, stored.get("code_hash").toString().length());
        assertFalse(stored.values().toString().contains(code));
        JsonNode before = readShare(code);
        assertTrue(before.path("nodes").size() >= 1);
        assertEquals("20+", before.path("choices").get(0).path("stats").path("sample_band").asText());
        assertTrue(before.path("choices").get(0).path("stats").has("percent"));
        assertFalse(before.path("choices").get(0).path("stats").has("sample"));
        assertFalse(before.toString().matches("(?s).*(player_id|playerId|nickname|player_name|level|resources).*"));
        insertUser(21, "player");
        insertPlayer(21, 21, encoded(List.of(choice("station_first_trace", 1))), "{}", 1);
        jdbc.update("UPDATE players SET decision_history_json=?,stage_progress_json=? WHERE id=1",
                encoded(List.of(choice("station_first_trace", 0), choice("dongmyo_market_story", 1))),
                "{\"routePending\":true,\"routePendingEventKey\":\"dongmyo_market_story\"}");
        assertEquals(before, readShare(code));
        assertEquals(first, createShare());
    }

    @Test
    void routeShareIncludesCurrentTipFromServerProgress() throws Exception {
        jdbc.update("UPDATE players SET decision_history_json=?,stage_progress_json=? WHERE id=1",
                encoded(List.of(choice("station_first_trace", 0), choice("chungmuro_conspiracy_story", 0))),
                "{\"routePending\":true,\"routePendingEventKey\":\"chungmuro_conspiracy_story\"}");
        JsonNode snapshot = readShare(createShare().path("code").asText());
        assertTrue(snapshot.path("current_tip").hasNonNull("title"));
        assertEquals(findByEvent(fetch(1), "chungmuro_conspiracy_story").path("node_id"),
                snapshot.path("current_tip").path("node_id"));
    }

    @Test
    void routeShareSmallSampleOmitsStatsAndExpiredOrRevokedCodesReturn404() throws Exception {
        jdbc.update("UPDATE players SET decision_history_json=? WHERE id=1",
                encoded(List.of(choice("station_first_trace", 0))));
        String first = createShare().path("code").asText();
        JsonNode snapshot = readShare(first);
        assertTrue(snapshot.path("choices").size() > 0, snapshot.toString());
        assertTrue(snapshot.path("choices").get(0).path("stats").isNull());
        assertTrue(snapshot.path("current_tip").isNull());
        jdbc.update("UPDATE route_shares SET expires_at=DATEADD('DAY',-1,CURRENT_TIMESTAMP) WHERE player_id=1");
        mvc.perform(get("/api/public/route-share/" + first)).andExpect(status().isNotFound());
        String second = createShare().path("code").asText();
        assertNotEquals(first, second);
        mvc.perform(delete("/api/explore/route-share/1").header("Authorization", token(1)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/public/route-share/" + second)).andExpect(status().isNotFound());
    }

    @Test
    void routeSharePublicReadIsRateLimited() throws Exception {
        String code = createShare().path("code").asText();
        for (int i = 0; i < 30; i++)
            mvc.perform(get("/api/public/route-share/" + code).with(req -> {
                req.setRemoteAddr("198.51.100.42"); return req;
            })).andExpect(status().isOk());
        mvc.perform(get("/api/public/route-share/" + code).with(req -> {
                    req.setRemoteAddr("198.51.100.42"); return req;
                })).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    private JsonNode createShare() throws Exception {
        String body = mvc.perform(post("/api/explore/route-share/1")
                        .header("Authorization", token(1)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data");
    }

    private JsonNode readShare(String code) throws Exception {
        var response = mvc.perform(get("/api/public/route-share/" + code))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Robots-Tag", "noindex"))
                .andReturn().getResponse();
        return json.readTree(response.getContentAsString()).path("data");
    }

    @Test
    void formerTipStillCountsAfterTreeGrowthAndNewTipKeepsCurrentMarker() throws Exception {
        String oldTip = "chungmuro_conspiracy_story";
        String newTip = "chungmuro_power_fork";
        jdbc.update("UPDATE players SET decision_history_json=?,stage_progress_json=? WHERE id=1",
                encoded(List.of(choice("station_first_trace", 0), legacyChoice(oldTip, 0),
                        choice("chungmuro_council_audit", 0), choice(newTip, 1))),
                encoded(Map.of("routePending", true, "routePendingEventKey", newTip)));
        JsonNode codex = fetchCodex(1);
        JsonNode tree = fetch(1);
        String oldId = findByEvent(tree, oldTip).path("node_id").asText();
        String newId = findByEvent(tree, newTip).path("node_id").asText();
        assertTrue(tipById(codex, oldId).path("reached").asBoolean());
        assertTrue(tipById(codex, newId).path("reached").asBoolean());
        assertTrue(tipById(codex, oldId).hasNonNull("title"));
        assertEquals("companion", tipById(codex, oldId).path("branch").asText());
        assertEquals("第四章", tipById(codex, newId).path("chapter_label").asText());
        assertEquals(newId, codex.path("my_tip_id").asText());
        jdbc.update("UPDATE players SET stage_progress_json=? WHERE id=1",
                "{\"routeReachedTipKeys\":[\"chungmuro_power_fork\"]}");
        JsonNode continued = fetchCodex(1);
        assertTrue(tipById(continued, oldId).path("reached").asBoolean());
        assertTrue(tipById(continued, newId).path("reached").asBoolean());
        assertTrue(continued.path("my_tip_id").isNull());
    }

    @Test
    void passingFormerTipAfterGrowthDoesNotCountAsStoppingThere() throws Exception {
        jdbc.update("UPDATE players SET decision_history_json=?,stage_progress_json=? WHERE id=1",
                encoded(List.of(choice("station_first_trace", 0), Map.of(
                        "event_key", "chungmuro_conspiracy_story", "choice_index", 0,
                        "timestamp", "2026-11-01T05:00:00"))), "{}");
        JsonNode codex = fetchCodex(1);
        for (JsonNode tip : codex.path("tips")) assertFalse(tip.path("reached").asBoolean());
    }

    @Test
    void codexRateUsesCumulativeReachAndTwentyEligiblePlayerThreshold() throws Exception {
        String oldTip = "chungmuro_conspiracy_story";
        jdbc.update("UPDATE players SET decision_history_json=?,stage_progress_json=? WHERE id=1",
                encoded(List.of(choice("station_first_trace", 0), legacyChoice(oldTip, 0))), "{}");
        for (int i = 2; i <= 19; i++) {
            insertUser(i, "player");
            insertPlayer(i, i, encoded(i <= 10
                    ? List.of(choice("station_first_trace", 0), legacyChoice(oldTip, 0))
                    : List.of(choice("station_first_trace", 1))), "{}", 1);
        }
        insertUser(30, "admin");
        insertPlayer(30, 30, encoded(List.of(choice("station_first_trace", 0), legacyChoice(oldTip, 0))), "{}", 1);
        JsonNode below = fetchCodex(1);
        for (JsonNode tip : below.path("tips")) assertTrue(tip.path("stats").isNull());
        insertUser(20, "player");
        insertPlayer(20, 20, encoded(List.of(choice("station_first_trace", 1))), "{}", 1);
        JsonNode atThreshold = fetchCodex(1);
        JsonNode old = null;
        for (JsonNode tip : atThreshold.path("tips"))
            if ("companion".equals(tip.path("branch").asText())
                    && "第三章".equals(tip.path("chapter_label").asText())
                    && tip.path("reached").asBoolean()) { old = tip; break; }
        assertNotNull(old);
        assertEquals(50, old.path("stats").path("percent").asInt());
        assertEquals("20+", old.path("stats").path("sample_band").asText());
        assertFalse(old.path("stats").has("sample"));
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
        assertEquals("20+", fetchCodex(1).path("tips").get(0)
                .path("stats").path("sample_band").asText());
        verify(values, times(1)).get(anyString());
    }

    @Test
    void redisRecoveryAllowsOnlyOneConcurrentProbe() throws Exception {
        var retryAfter = (java.util.concurrent.atomic.AtomicLong)
                ReflectionTestUtils.getField(storyTreeService, "redisRetryAfterNanos");
        assertNotNull(retryAfter);
        retryAfter.set(System.nanoTime() - 1);
        CountDownLatch probeEntered = new CountDownLatch(1);
        CountDownLatch releaseProbe = new CountDownLatch(1);
        when(values.get(anyString())).thenAnswer(invocation -> {
            probeEntered.countDown();
            if (!releaseProbe.await(5, TimeUnit.SECONDS))
                throw new IllegalStateException("probe was not released");
            return null;
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            CompletableFuture<JsonNode> first = CompletableFuture.supplyAsync(() -> {
                try { return fetch(1); } catch (Exception e) { throw new RuntimeException(e); }
            }, pool);
            assertTrue(probeEntered.await(5, TimeUnit.SECONDS));
            CompletableFuture<JsonNode> second = CompletableFuture.supplyAsync(() -> {
                try { return fetch(1); } catch (Exception e) { throw new RuntimeException(e); }
            }, pool);
            assertTrue(second.get(5, TimeUnit.SECONDS).path("nodes").isArray());
            verify(values, times(1)).get(anyString());
            releaseProbe.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS).path("nodes").isArray());
            verify(values, times(1)).get(anyString());
        } finally {
            releaseProbe.countDown();
            retryAfter.set(0);
        }
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

    private JsonNode fetchCodex(long userId) throws Exception {
        String body = mvc.perform(get("/api/explore/route-codex/1")
                        .header("Authorization", token(userId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode wrapper = json.readTree(body);
        assertTrue(wrapper.path("success").asBoolean());
        return wrapper.path("data");
    }

    private JsonNode tipById(JsonNode codex, String id) {
        for (JsonNode tip : codex.path("tips")) if (id.equals(tip.path("tip_id").asText())) return tip;
        fail("Tip not found: " + id); return null;
    }

    private String token(long userId) {
        return "Bearer " + jwt.generateToken(userId, "tree-" + userId, "player");
    }

    private Map<String, Object> choice(String event, int index) {
        return Map.of("event_key", event, "choice_index", index);
    }

    private Map<String, Object> legacyChoice(String event, int index) {
        return Map.of("event_key", event, "choice_index", index,
                "timestamp", "2026-10-31T23:00:00");
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
