package com.huazhenghai.readergame.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Seed-only graph check: no application context or live database is opened. */
class StoryRouteStaticIntegrityTest {
    private static final Set<String> SECOND_GROWTH = Set.of(
            "chungmuro_council_audit", "chungmuro_shadow_vote", "chungmuro_power_fork",
            "dongmyo_ledger_delivery", "dongmyo_resistance_weigh", "geumho_resistance_muster",
            "chungmuro_paper_summon", "chungmuro_blank_revision", "chungmuro_name_fork",
            "geumho_wall_message", "geumho_nameless_cell", "chungmuro_reader_anchor");
    private final Map<String, JsonNode> events = new LinkedHashMap<>();
    private final Map<String, JsonNode> locations = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();

    @Test
    void everyChoiceCanReachTheNextStoryChapterAndSeedReferencesAreSound() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/db/seed_explore.json")) {
            assertNotNull(input);
            for (JsonNode entry : new ObjectMapper().readTree(input)) {
                if ("event".equals(entry.path("type").asText())) {
                    events.put(entry.path("event_key").asText(), entry);
                } else if ("location".equals(entry.path("type").asText())) {
                    locations.put(entry.path("location_key").asText(), entry);
                }
            }
        }

        validateReferencesAndRates();
        validateChoiceUnlockHints();
        List<String> ordered = topologicalOrder();
        if (problems.stream().noneMatch(message -> message.contains("循环依赖"))) {
            validateChapterExits(ordered);
        }
        assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
    }

    @Test
    void firstGrowthSimulatesFourFreshRoutesToDistinctPendingTips() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/db/seed_explore.json")) {
            assertNotNull(input);
            for (JsonNode entry : new ObjectMapper().readTree(input)) {
                if ("event".equals(entry.path("type").asText())) {
                    events.put(entry.path("event_key").asText(), entry);
                } else if ("location".equals(entry.path("type").asText())) {
                    locations.put(entry.path("location_key").asText(), entry);
                }
            }
        }

        Map<String, List<String>> routes = Map.of(
                "A0", List.of("station_first_trace", "dongmyo_market_story",
                        "dongmyo_whisperer_story", "chungmuro_iron_king_story",
                        "chungmuro_shadow_king_story", "chungmuro_conspiracy_story"),
                "A1", List.of("station_first_trace", "dongmyo_market_story",
                        "dongmyo_whisperer_story", "chungmuro_iron_king_story",
                        "chungmuro_underground_resistance", "chungmuro_compromise_ledger"),
                "U0", List.of("station_first_trace", "geumho_shelter_story",
                        "geumho_underground_story", "chungmuro_unnamed_crossing",
                        "chungmuro_revelation_story", "chungmuro_script_blank"),
                "U1", List.of("station_first_trace", "geumho_shelter_story",
                        "geumho_underground_story", "chungmuro_unnamed_crossing",
                        "chungmuro_nameless_drift", "chungmuro_hidden_reader_wall"));
        Map<String, List<Integer>> selections = Map.of(
                "A0", List.of(0, 0, 1, 0, 1, 0),
                "A1", List.of(0, 1, 2, 1, 0, 1),
                "U0", List.of(1, 0, 1, 0, 1, 0),
                "U1", List.of(1, 1, 0, 1, 1, 1));
        Map<String, List<Integer>> levels = Map.of(
                "A0", List.of(1, 2, 2, 5, 5, 5),
                "A1", List.of(1, 2, 2, 5, 5, 5),
                "U0", List.of(1, 3, 3, 5, 5, 5),
                "U1", List.of(1, 3, 3, 5, 5, 5));
        Set<String> legacyLaterStories = new HashSet<>();
        for (JsonNode event : events.values()) {
            if (deferredStory(event) && !SECOND_GROWTH.contains(event.path("event_key").asText())) {
                legacyLaterStories.add(event.path("event_key").asText());
            }
        }
        assertEquals(20, legacyLaterStories.size());
        for (String key : List.of("chungmuro_unnamed_crossing", "chungmuro_nameless_drift",
                "chungmuro_hidden_reader_wall", "chungmuro_script_blank",
                "chungmuro_compromise_ledger")) {
            JsonNode event = events.get(key);
            assertNotNull(event, "缺少新增节点 " + key);
            assertEquals("vol1_ch3", event.path("stage_key").asText());
            assertEquals("chungmuro", event.path("location_key").asText());
            assertTrue(event.path("rewards").isObject() && event.path("rewards").isEmpty(),
                    key + " 不应在基础奖励重复发奖");
            for (JsonNode option : event.path("choices")) {
                assertTrue(option.path("rewards_override").isObject(),
                        key + " 的选项奖励必须单独配置");
                assertTrue(option.path("title_bias").isObject());
            }
        }

        for (String route : List.of("A0", "A1", "U0", "U1")) {
            List<String> expected = routes.get(route);
            assertEquals(6, expected.size());
            Map<String, Integer> decisions = new LinkedHashMap<>();
            for (int step = 0; step < expected.size(); step++) {
                int level = levels.get(route).get(step);
                Set<String> available = new LinkedHashSet<>();
                for (JsonNode event : events.values()) {
                    if (!"story".equals(event.path("event_type").asText())
                            || decisions.containsKey(event.path("event_key").asText())) continue;
                    JsonNode location = locations.get(event.path("location_key").asText());
                    if (location != null && level >= location.path("min_level").asInt()
                            && level >= location.path("unlock_conditions").path("required_level").asInt()
                            && available(event, decisions)) {
                        available.add(event.path("event_key").asText());
                    }
                }
                assertEquals(Set.of(expected.get(step)), available,
                        route + " step " + step + " must offer only its next story");
                JsonNode event = events.get(expected.get(step));
                assertEquals("story", event.path("event_type").asText());
                assertEquals(0, event.path("repeatable").asInt());
                assertTrue(event.path("choices").size() > selections.get(route).get(step));
                decisions.put(expected.get(step), selections.get(route).get(step));
                assertTrue(legacyLaterStories.stream().noneMatch(key -> available(events.get(key), decisions)),
                        route + " must not enter any old ch4+ story before reaching its tip");
            }
            JsonNode tip = events.get(expected.get(5));
            assertFalse(tip.path("choices").get(selections.get(route).get(5))
                    .has("route_pending"), route + " old tip must continue");
            assertFalse(tip.path("choices").get(1 - selections.get(route).get(5))
                    .has("route_pending"), route + " alternate old tip must continue");
        }

        Set<String> companions = new HashSet<>(routes.get("A0"));
        companions.addAll(routes.get("A1"));
        Set<String> nameless = new HashSet<>(routes.get("U0"));
        nameless.addAll(routes.get("U1"));
        companions.remove("station_first_trace");
        nameless.remove("station_first_trace");
        companions.retainAll(nameless);
        assertTrue(companions.isEmpty(), "同行者与无名者分流后不得共享主线事件");
        for (String tip : List.of("chungmuro_power_fork", "geumho_resistance_muster",
                "chungmuro_name_fork", "chungmuro_reader_anchor")) {
            for (JsonNode event : events.values()) {
                JsonNode conditions = event.path("required_conditions");
                for (JsonNode dependency : conditions.path("required_events")) {
                    assertFalse(tip.equals(dependency.asText()), tip + " is used as a prerequisite");
                }
                for (JsonNode dependency : conditions.path("required_side_events")) {
                    assertFalse(tip.equals(dependency.asText()), tip + " is used as a side prerequisite");
                }
                for (JsonNode gate : conditions.path("choice_unlockers")) {
                    assertFalse(tip.equals(gate.path("event_key").asText()),
                            tip + " is used as a choice prerequisite");
                }
                for (JsonNode gate : conditions.path("required_choices")) {
                    assertFalse(tip.equals(gate.path("event_key").asText()),
                            tip + " is used as a required choice prerequisite");
                }
            }
            for (JsonNode location : locations.values()) {
                JsonNode access = location.path("unlock_conditions");
                for (String field : List.of("prerequisite_events", "prerequisite_events_any")) {
                    for (JsonNode prerequisite : access.path(field)) {
                        assertFalse(tip.equals(prerequisite.asText()),
                                tip + " is used as a location prerequisite");
                    }
                }
            }
        }
    }

    @Test
    void secondGrowthHasSixPendingLeavesAndFourIsolatedNineStoryPaths() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/db/seed_explore.json")) {
            assertNotNull(input);
            for (JsonNode entry : new ObjectMapper().readTree(input)) {
                if ("event".equals(entry.path("type").asText())) {
                    events.put(entry.path("event_key").asText(), entry);
                } else if ("location".equals(entry.path("type").asText())) {
                    locations.put(entry.path("location_key").asText(), entry);
                }
            }
        }
        Map<String, List<String>> trunks = Map.of(
                "A0", List.of("station_first_trace", "dongmyo_market_story",
                        "dongmyo_whisperer_story", "chungmuro_iron_king_story",
                        "chungmuro_shadow_king_story", "chungmuro_conspiracy_story"),
                "A1", List.of("station_first_trace", "dongmyo_market_story",
                        "dongmyo_whisperer_story", "chungmuro_iron_king_story",
                        "chungmuro_underground_resistance", "chungmuro_compromise_ledger"),
                "U0", List.of("station_first_trace", "geumho_shelter_story",
                        "geumho_underground_story", "chungmuro_unnamed_crossing",
                        "chungmuro_revelation_story", "chungmuro_script_blank"),
                "U1", List.of("station_first_trace", "geumho_shelter_story",
                        "geumho_underground_story", "chungmuro_unnamed_crossing",
                        "chungmuro_nameless_drift", "chungmuro_hidden_reader_wall"));
        Map<String, List<String>> growth = Map.of(
                "A0", List.of("chungmuro_council_audit", "chungmuro_shadow_vote", "chungmuro_power_fork"),
                "A1", List.of("dongmyo_ledger_delivery", "dongmyo_resistance_weigh", "geumho_resistance_muster"),
                "U0", List.of("chungmuro_paper_summon", "chungmuro_blank_revision", "chungmuro_name_fork"),
                "U1", List.of("geumho_wall_message", "geumho_nameless_cell", "chungmuro_reader_anchor"));
        Map<String, List<Integer>> selections = Map.of(
                "A0", List.of(0, 0, 1, 0, 1, 0), "A1", List.of(0, 1, 2, 1, 0, 1),
                "U0", List.of(1, 0, 1, 0, 1, 0), "U1", List.of(1, 1, 0, 1, 1, 1));
        Set<String> growthSeen = new HashSet<>();
        Set<String> leafFlags = new HashSet<>();
        Set<String> pendingLeaves = new HashSet<>();
        for (String route : List.of("A0", "A1", "U0", "U1")) {
            List<String> expected = new ArrayList<>(trunks.get(route));
            expected.addAll(growth.get(route));
            assertEquals(9, expected.size());
            for (String key : growth.get(route)) {
                assertTrue(growthSeen.add(key), "主枝重复使用事件 " + key);
                JsonNode event = events.get(key);
                assertNotNull(event);
                assertEquals("story", event.path("event_type").asText());
                assertEquals("vol1_ch4", event.path("stage_key").asText());
                assertEquals(0, event.path("repeatable").asInt());
                assertTrue(event.path("rewards").isObject() && event.path("rewards").isEmpty());
                for (JsonNode choice : event.path("choices")) {
                    assertTrue(choice.path("rewards_override").isObject());
                    assertTrue(choice.path("title_bias").isObject());
                }
                String prose = event.path("description").asText() + event.path("choices").toString();
                for (String forbidden : List.of("死亡", "死去", "尸体", "葬")) {
                    assertFalse(prose.contains(forbidden), key + " contains " + forbidden);
                }
            }
            for (int leafChoice = 0; leafChoice < 2; leafChoice++) {
                Map<String, Integer> decisions = new LinkedHashMap<>();
                for (int step = 0; step < expected.size(); step++) {
                    String key = expected.get(step);
                    JsonNode event = events.get(key);
                    Set<String> candidates = new LinkedHashSet<>();
                    for (JsonNode candidate : events.values()) {
                        String candidateKey = candidate.path("event_key").asText();
                        if ("story".equals(candidate.path("event_type").asText())
                                && !decisions.containsKey(candidateKey)
                                && available(candidate, decisions)) {
                            candidates.add(candidateKey);
                        }
                    }
                    assertEquals(Set.of(key), candidates, route + " step " + step);
                    if (step == 6) {
                        String oldTip = expected.get(5);
                        assertEquals(Set.of(key), Set.copyOf(candidates), "旧枝尖续章必须可抽中");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> conditions = new ObjectMapper().convertValue(
                                event.path("required_conditions"), Map.class);
                        assertTrue(RouteContinuationPolicy.allowsStory(
                                Map.of("routePending", true, "routePendingEventKey", oldTip), conditions));
                    }
                    int choice = step < 6 ? selections.get(route).get(step)
                            : step == 8 ? leafChoice : 0;
                    decisions.put(key, choice);
                    if (step == 5) {
                        JsonNode oldChoice = event.path("choices").get(choice);
                        assertFalse(oldChoice.has("route_pending"));
                        assertEquals(expected.get(6),
                                oldChoice.path("unlock_events").get(0).asText());
                    }
                    if (step == 8) {
                        JsonNode tipChoice = event.path("choices").get(choice);
                        assertTrue(tipChoice.path("route_pending").asBoolean());
                        pendingLeaves.add(route.equals("A0") || route.equals("U0")
                                ? route + leafChoice : route);
                        tipChoice.path("set_story_flags").fieldNames().forEachRemaining(flag ->
                                assertTrue(leafFlags.add(flag), "枝尖旗标重复 " + flag));
                    }
                }
            }
        }
        assertEquals(12, growthSeen.size());
        assertEquals(6, pendingLeaves.size());
        assertEquals(8, leafFlags.size());
        assertTrue(locations.get("geumho_station").path("unlock_conditions")
                .path("prerequisite_events").toString().contains("station_first_trace"),
                "A1 已经过站台起点，金湖站应可达");
    }

    @Test
    void oldDecisionHistoriesFollowTheirChosenRootWithoutResettingProgress() throws Exception {
        Map<String, JsonNode> byKey = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream input = getClass().getResourceAsStream("/db/seed_explore.json")) {
            assertNotNull(input);
            for (JsonNode entry : mapper.readTree(input)) {
                if ("event".equals(entry.path("type").asText())) {
                    byKey.put(entry.path("event_key").asText(), entry);
                }
            }
        }
        Map<String, Object> stats = Map.of("level", 5);
        Map<String, Object> unchangedProgress = Map.of("storyEventsTriggered", List.of(
                "station_first_trace", "geumho_shelter_story", "geumho_underground_story"));
        List<Map<String, Object>> companionOldChoices = List.of(
                Map.of("event_key", "station_first_trace", "choice_index", 0),
                Map.of("event_key", "geumho_shelter_story", "choice_index", 1),
                Map.of("event_key", "geumho_underground_story", "choice_index", 0));
        List<Map<String, Object>> namelessOldChoices = List.of(
                Map.of("event_key", "station_first_trace", "choice_index", 1),
                Map.of("event_key", "geumho_shelter_story", "choice_index", 1),
                Map.of("event_key", "geumho_underground_story", "choice_index", 0));

        @SuppressWarnings("unchecked")
        Map<String, Object> companionGate = mapper.convertValue(
                byKey.get("dongmyo_market_story").path("required_conditions"), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> namelessGate = mapper.convertValue(
                byKey.get("chungmuro_unnamed_crossing").path("required_conditions"), Map.class);
        assertTrue(ExploreStoryEligibility.checkRequiredConditions(stats, unchangedProgress,
                companionOldChoices, companionGate));
        assertFalse(ExploreStoryEligibility.checkRequiredConditions(stats, unchangedProgress,
                namelessOldChoices, companionGate));
        assertTrue(ExploreStoryEligibility.checkRequiredConditions(stats, unchangedProgress,
                namelessOldChoices, namelessGate));
        assertFalse(ExploreStoryEligibility.checkRequiredConditions(stats, unchangedProgress,
                companionOldChoices, namelessGate));
        @SuppressWarnings("unchecked")
        Map<String, Object> councilGate = mapper.convertValue(
                byKey.get("chungmuro_conspiracy_story").path("required_conditions"), Map.class);
        List<Map<String, Object>> oldPaidCouncil = List.of(
                Map.of("event_key", "station_first_trace", "choice_index", 0),
                Map.of("event_key", "chungmuro_iron_king_story", "choice_index", 1),
                Map.of("event_key", "chungmuro_shadow_king_story", "choice_index", 0));
        assertFalse(ExploreStoryEligibility.checkRequiredConditions(stats, unchangedProgress,
                oldPaidCouncil, councilGate), "旧档交通行费后已触发影王，也不能进入 A0 会议");
        assertEquals(3, ((List<?>) unchangedProgress.get("storyEventsTriggered")).size(),
                "路线归属不应要求清空旧故事进度");
    }

    @Test
    void sqlSeedAndRepeatableMigrationMatchTheRepairedJsonConfiguration() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode seed;
        String sql;
        String migration;
        try (InputStream jsonInput = getClass().getResourceAsStream("/db/seed_explore.json");
             InputStream sqlInput = getClass().getResourceAsStream("/db/seed_explore.sql");
             InputStream migrationInput = getClass().getResourceAsStream(
                     "/db/migrations/20261031_repair_explore_seed_routes.sql")) {
            assertNotNull(jsonInput);
            assertNotNull(sqlInput);
            assertNotNull(migrationInput);
            seed = mapper.readTree(jsonInput);
            sql = new String(sqlInput.readAllBytes(), StandardCharsets.UTF_8);
            migration = new String(migrationInput.readAllBytes(), StandardCharsets.UTF_8);
        }
        String locationInserts = sql.substring(sql.indexOf("INSERT IGNORE INTO `locations`"),
                sql.indexOf("-- PART 2:"));
        Set<String> removedRateKeys = Set.of(
                "ruined_station.side_story", "geumho_station.boss_clue", "dongmyo.boss_clue",
                "dongdaemun.opportunity", "myeongdong.boss_clue", "gwangjin_bridge.boss_clue",
                "gangnam_station.opportunity", "gangnam_station.hidden", "seoul_forest.boss_clue",
                "demon_gate.boss_clue", "demon_gate.empty");
        int locationCount = 0;
        for (JsonNode entry : seed) {
            if (!"location".equals(entry.path("type").asText())) continue;
            locationCount++;
            String key = entry.path("location_key").asText();
            int start = locationInserts.indexOf("('" + key + "',");
            assertTrue(start >= 0, "SQL 地点缺失: " + key);
            Matcher rates = Pattern.compile("(?m)^\\s*'(\\{[^\\r\\n]*\\})',\\s*$")
                    .matcher(locationInserts.substring(start));
            assertTrue(rates.find(), "SQL 地点缺少解锁条件: " + key);
            assertTrue(rates.find(), "SQL 地点缺少类型权重: " + key);
            JsonNode sqlRates = mapper.readTree(rates.group(1));
            for (String removed : removedRateKeys) {
                if (!removed.startsWith(key + ".")) continue;
                String type = removed.substring(key.length() + 1);
                assertFalse(entry.path("event_rates").has(type), "JSON 死权重仍存在: " + removed);
                assertFalse(sqlRates.has(type), "SQL 死权重仍存在: " + removed);
            }
        }
        assertEquals(10, locationCount);
        String growthMarker = "-- First story tree growth: authoritative final state for the new-library SQL seed.";
        assertTrue(sql.contains(growthMarker), "SQL 新库必须应用第一次生长的最终配置");
        String growthMigration;
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migrations/20261031_story_tree_first_growth.sql")) {
            assertNotNull(input);
            growthMigration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String finalConfiguration = sql.substring(sql.indexOf(growthMarker),
                sql.indexOf("-- Second story tree growth:"));
        String guardedConfiguration = growthMigration.substring(growthMigration.indexOf(growthMarker));
        String newLibraryConfiguration = guardedConfiguration.replaceAll(
                "(?s)(WHERE `(?:event_key|location_key)` = '[^']+')\\R  AND .*?;", "$1;");
        assertEquals(finalConfiguration.strip(), newLibraryConfiguration.strip(),
                "新库种子与旧库迁移必须应用同一组静态配置变更");
        assertFalse(growthMigration.contains("UPDATE `players`")
                || growthMigration.contains("DELETE FROM `players`"));
        assertEquals(5, count(growthMigration, "INSERT IGNORE INTO `exploration_events`"));
        assertEquals(32, count(growthMigration, "UPDATE `exploration_events`")
                + count(growthMigration, "UPDATE `locations`"));
        Matcher growthUpdates = Pattern.compile("UPDATE `([^`]+)`[\\s\\S]*?;")
                .matcher(growthMigration);
        int guardedUpdates = 0;
        while (growthUpdates.find()) {
            guardedUpdates++;
            String update = growthUpdates.group();
            assertTrue(Set.of("exploration_events", "locations").contains(growthUpdates.group(1)));
            assertTrue(update.contains("WHERE `event_key` = '")
                    || update.contains("WHERE `location_key` = '"));
            assertTrue(update.contains("\n  AND ") || update.contains("\r\n  AND "),
                    "旧库迁移每条更新都必须有旧值守卫");
        }
        assertEquals(32, guardedUpdates);
        assertFalse(growthMigration.contains("DELETE ") || growthMigration.contains("UPDATE `players`"));
        assertEquals(4, count(migration, "UPDATE `exploration_events`"));
        assertEquals(11, count(migration, "UPDATE `locations`"));
        assertEquals(11, count(migration, "JSON_REMOVE(`event_rates_json`"));
        assertEquals(2, count(migration, "JSON_SET(`required_conditions_json`"));
        assertEquals(2, count(migration, "JSON_SET(`choices_json`"));
        assertEquals(2, count(migration, "= JSON_ARRAY(1);"), "重复执行时旧值守卫应使更新成为空操作");
        assertTrue("20261031_repair_explore_seed_routes.sql".matches("[0-9]{8}_[a-z0-9_]+\\.sql"));
        Set<String> removedRates = new HashSet<>();
        Set<String> updatedEvents = new HashSet<>();
        Set<String> updatedChoiceHints = new HashSet<>();
        Matcher updates = Pattern.compile("UPDATE `([^`]+)`[\\s\\S]*?;").matcher(migration);
        int updateCount = 0;
        while (updates.find()) {
            updateCount++;
            String statement = updates.group();
            if ("locations".equals(updates.group(1))) {
                Matcher key = Pattern.compile("WHERE `location_key` = '([^']+)'").matcher(statement);
                Matcher type = Pattern.compile("JSON_REMOVE\\(`event_rates_json`, '\\$\\.([^']+)'\\)")
                        .matcher(statement);
                assertTrue(key.find() && type.find(), "地点迁移必须按键移除单一倍率: " + statement);
                assertTrue(statement.contains("JSON_UNQUOTE(JSON_EXTRACT(`event_rates_json`, '$."
                        + type.group(1) + "')) = '"), "地点迁移必须保护自定义倍率");
                removedRates.add(key.group(1) + "." + type.group(1));
            } else {
                assertEquals("exploration_events", updates.group(1), "迁移不得更新其他表");
                Matcher key = Pattern.compile("WHERE `event_key` = '([^']+)'").matcher(statement);
                assertTrue(key.find());
                if (statement.contains("SET `required_conditions_json` = JSON_SET(")) {
                    assertTrue(statement.contains("choice_indices') = JSON_ARRAY(1)"),
                            "门槛迁移必须只在旧选项数组上生效");
                    updatedEvents.add(key.group(1));
                } else {
                    assertTrue(statement.contains("SET `choices_json` = JSON_SET("));
                    assertTrue(statement.contains("JSON_CONTAINS_PATH(`choices_json`, 'one', '$[0].unlock_events') = 0"),
                            "提示迁移必须只填补原本缺失的提示");
                    assertTrue(statement.contains("JSON_EXTRACT(`choices_json`, '$[1].unlock_events') = JSON_ARRAY("),
                            "提示迁移必须保护已有选项 1");
                    updatedChoiceHints.add(key.group(1));
                }
            }
        }
        assertEquals(15, updateCount);
        assertEquals(Set.of("geumho_underground_story", "chungmuro_iron_king_story"), updatedEvents);
        assertEquals(Set.of("geumho_shelter_story", "geumho_underground_story"), updatedChoiceHints);
        assertEquals(removedRateKeys, removedRates);
        assertTrue(!migration.contains("UPDATE `players`") && !migration.contains("DELETE FROM `players`"));
        assertTrue(!migration.contains("INSERT INTO") && !migration.contains("DELETE FROM"));
    }

    @Test
    void secondGrowthSqlSeedMatchesGuardedRepeatableMigration() throws Exception {
        String sql;
        String migration;
        try (InputStream sqlInput = getClass().getResourceAsStream("/db/seed_explore.sql");
             InputStream migrationInput = getClass().getResourceAsStream(
                     "/db/migrations/20261101_story_tree_second_growth.sql")) {
            assertNotNull(sqlInput);
            assertNotNull(migrationInput);
            sql = new String(sqlInput.readAllBytes(), StandardCharsets.UTF_8);
            migration = new String(migrationInput.readAllBytes(), StandardCharsets.UTF_8);
        }
        String marker = "-- Second story tree growth: authoritative final state for the new-library SQL seed.";
        assertTrue(sql.contains(marker));
        assertTrue(migration.contains(marker));
        String newLibrary = sql.substring(sql.indexOf(marker));
        String guarded = migration.substring(migration.indexOf(marker));
        String unguarded = guarded.replaceAll(
                "(?s)(WHERE `event_key` = '[^']+')\\R  AND .*?;", "$1;");
        assertEquals(newLibrary.strip(), unguarded.strip());
        assertEquals(4, count(guarded, "UPDATE `exploration_events`"));
        assertEquals(12, count(guarded, "INSERT IGNORE INTO `exploration_events`"));
        Matcher updates = Pattern.compile("UPDATE `exploration_events`[\\s\\S]*?;")
                .matcher(guarded);
        while (updates.find()) {
            String update = updates.group();
            assertTrue(update.contains("WHERE `event_key` = '"));
            assertTrue(update.contains("  AND JSON_CONTAINS(`choices_json`"),
                    "旧枝尖更新必须有旧值守卫");
        }
        assertFalse(guarded.contains("UPDATE `players`") || guarded.contains("DELETE "));
    }

    private static int count(String text, String token) {
        return (text.length() - text.replace(token, "").length()) / token.length();
    }

    @Test
    void previouslyStuckChoiceZeroHistoriesBecomeEligibleWithoutChangingPlayerProgress() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, JsonNode> byKey = new HashMap<>();
        try (InputStream input = getClass().getResourceAsStream("/db/seed_explore.json")) {
            assertNotNull(input);
            for (JsonNode entry : mapper.readTree(input)) {
                if ("event".equals(entry.path("type").asText())) {
                    byKey.put(entry.path("event_key").asText(), entry);
                }
            }
        }
        for (Map.Entry<String, String> gate : Map.of(
                "geumho_underground_story", "geumho_shelter_story",
                "chungmuro_iron_king_story", "dongmyo_whisperer_story").entrySet()) {
            JsonNode target = byKey.get(gate.getKey());
            @SuppressWarnings("unchecked")
            Map<String, Object> conditions = mapper.convertValue(target.path("required_conditions"), Map.class);
            int sourceChoices = byKey.get(gate.getValue()).path("choices").size();
            for (int choice = 0; choice < sourceChoices; choice++) {
                int rootChoice = "geumho_underground_story".equals(gate.getKey()) ? 1 : 0;
                List<Map<String, Object>> history = List.of(
                        Map.of("event_key", "station_first_trace", "choice_index", rootChoice),
                        Map.of("event_key", gate.getValue(), "choice_index", choice));
                assertTrue(ExploreStoryEligibility.checkRequiredConditions(
                        Map.of("level", 99), Map.of(), history, conditions),
                        gate.getKey() + " should admit prior choice " + choice);
                assertFalse(ExploreStoryEligibility.choiceHistoryPermanentlyClosesEvent(conditions, history));
            }
            assertFalse(ExploreStoryEligibility.checkRequiredConditions(
                    Map.of("level", 99), Map.of(), List.of(), conditions),
                    "没有前置选择时仍不能跳过前置事件: " + gate.getKey());
        }
    }

    private void validateReferencesAndRates() {
        for (JsonNode event : events.values()) {
            String key = event.path("event_key").asText();
            JsonNode eventMarker = event.path("progress_effects").path("route_pending");
            if (!eventMarker.isMissingNode() && (!"story".equals(event.path("event_type").asText())
                    || !eventMarker.isBoolean() || !eventMarker.asBoolean()
                    || !event.path("choices").isEmpty())) {
                problems.add(key + " 的事件级 route_pending 只允许无选项 story 且值为 true");
            }
            for (JsonNode choice : event.path("choices")) {
                JsonNode marker = choice.path("route_pending");
                if (!marker.isMissingNode() && (!"story".equals(event.path("event_type").asText())
                        || !marker.isBoolean() || !marker.asBoolean())) {
                    problems.add(key + " 的选项级 route_pending 只允许 story 且值为 true");
                }
            }
            if (!locations.containsKey(event.path("location_key").asText())) {
                problems.add(key + " 引用不存在的地点 " + event.path("location_key").asText());
            }
            for (JsonNode gate : event.path("required_conditions").path("required_choices")) {
                String source = gate.path("event_key").asText();
                JsonNode sourceEvent = events.get(source);
                if (sourceEvent == null) {
                    problems.add(key + " 的 required_choices 引用不存在的事件 " + source);
                    continue;
                }
                JsonNode indices = gate.path("choice_indices");
                if (!indices.isArray() || indices.isEmpty()) {
                    problems.add(key + " 的 required_choices 没有选项索引: " + source);
                } else {
                    for (JsonNode index : indices) {
                        if (!index.isIntegralNumber() || index.asInt() < 0
                                || index.asInt() >= sourceEvent.path("choices").size()) {
                            problems.add(key + " 引用不存在的必需选项 " + source + "[" + index + "]");
                        }
                    }
                }
            }
            for (JsonNode gate : event.path("required_conditions").path("choice_unlockers")) {
                String source = gate.path("event_key").asText();
                JsonNode sourceEvent = events.get(source);
                if (sourceEvent == null) {
                    problems.add(key + " 的 choice_unlockers 引用不存在的事件 " + source);
                    continue;
                }
                JsonNode indices = gate.path("choice_indices");
                if (!indices.isArray() || indices.isEmpty()) {
                    problems.add(key + " 的 choice_unlockers 没有选项索引: " + source);
                } else {
                    for (JsonNode index : indices) {
                        if (!index.isIntegralNumber() || index.asInt() < 0
                                || index.asInt() >= sourceEvent.path("choices").size()) {
                            problems.add(key + " 引用不存在的选项 " + source + "[" + index + "]");
                        }
                    }
                }
            }
            for (String field : List.of("required_events", "required_side_events")) {
                for (JsonNode dependency : event.path("required_conditions").path(field)) {
                    if (!events.containsKey(dependency.asText())) {
                        problems.add(key + " 的 " + field + " 引用不存在的事件 " + dependency.asText());
                    }
                }
            }
        }
        for (JsonNode location : locations.values()) {
            String key = location.path("location_key").asText();
            Set<String> availableTypes = new HashSet<>();
            for (JsonNode event : events.values()) {
                if (key.equals(event.path("location_key").asText())) {
                    availableTypes.add(event.path("event_type").asText());
                }
            }
            location.path("event_rates").fieldNames().forEachRemaining(type -> {
                if (!availableTypes.contains(type)) {
                    problems.add("死权重 " + key + "." + type);
                }
            });
        }
    }

    private void validateChoiceUnlockHints() {
        Map<String, Set<String>> unlockedByChoice = new HashMap<>();
        for (JsonNode target : events.values()) {
            String targetKey = target.path("event_key").asText();
            for (JsonNode gate : target.path("required_conditions").path("choice_unlockers")) {
                String sourceKey = gate.path("event_key").asText();
                if (deferredStory(target) && !SECOND_GROWTH.contains(targetKey)
                        && !deferredStory(events.get(sourceKey))) continue;
                for (JsonNode index : gate.path("choice_indices")) {
                    unlockedByChoice.computeIfAbsent(sourceKey + "[" + index.asInt() + "]",
                            unused -> new HashSet<>()).add(targetKey);
                }
            }
        }
        for (JsonNode source : events.values()) {
            String sourceKey = source.path("event_key").asText();
            JsonNode choices = source.path("choices");
            for (int index = 0; index < choices.size(); index++) {
                String choiceKey = sourceKey + "[" + index + "]";
                Set<String> expected = unlockedByChoice.getOrDefault(choiceKey, Set.of());
                Set<String> hinted = new HashSet<>();
                for (JsonNode hint : choices.get(index).path("unlock_events")) {
                    hinted.add(hint.asText());
                }
                for (String target : expected) {
                    if (!hinted.contains(target)) problems.add("缺少解锁提示 " + choiceKey + " -> " + target);
                }
                for (String target : hinted) {
                    if (!expected.contains(target)) problems.add("虚假解锁提示 " + choiceKey + " -> " + target);
                }
            }
        }
    }

    private static boolean deferredStory(JsonNode event) {
        return event != null && "story".equals(event.path("event_type").asText())
                && !Set.of("vol1_ch1", "vol1_ch2", "vol1_ch3")
                        .contains(event.path("stage_key").asText());
    }

    private List<String> topologicalOrder() {
        List<String> order = new ArrayList<>();
        Map<String, Integer> marks = new HashMap<>();
        for (String key : events.keySet()) {
            visit(key, marks, order);
        }
        return order;
    }

    private void visit(String key, Map<String, Integer> marks, List<String> order) {
        if (marks.getOrDefault(key, 0) == 2) return;
        if (marks.getOrDefault(key, 0) == 1) {
            problems.add("循环依赖涉及 " + key);
            return;
        }
        marks.put(key, 1);
        JsonNode event = events.get(key);
        if (event != null) {
            JsonNode conditions = event.path("required_conditions");
            for (String field : List.of("required_events", "required_side_events")) {
                for (JsonNode dependency : conditions.path(field)) {
                    if (events.containsKey(dependency.asText())) visit(dependency.asText(), marks, order);
                }
            }
            for (JsonNode gate : conditions.path("choice_unlockers")) {
                String source = gate.path("event_key").asText();
                if (events.containsKey(source)) visit(source, marks, order);
            }
            for (JsonNode gate : conditions.path("required_choices")) {
                String source = gate.path("event_key").asText();
                if (events.containsKey(source)) visit(source, marks, order);
            }
            JsonNode location = locations.get(event.path("location_key").asText());
            if (location != null) {
                for (String field : List.of("prerequisite_events", "prerequisite_events_any")) {
                    for (JsonNode dependency : location.path("unlock_conditions").path(field)) {
                        if (events.containsKey(dependency.asText())) visit(dependency.asText(), marks, order);
                    }
                }
            }
        }
        marks.put(key, 2);
        order.add(key);
    }

    private void validateChapterExits(List<String> topological) {
        Map<String, List<String>> chapters = new LinkedHashMap<>();
        for (JsonNode event : events.values()) {
            if ("story".equals(event.path("event_type").asText())) {
                chapters.computeIfAbsent(event.path("stage_key").asText(), unused -> new ArrayList<>())
                        .add(event.path("event_key").asText());
            }
        }
        List<String> stages = chapters.keySet().stream().sorted(Comparator.comparingInt(
                StoryRouteStaticIntegrityTest::stageOrder)).toList();
        for (int index = 0; index + 1 < stages.size(); index++) {
            String stage = stages.get(index);
            // The new tree intentionally stops at four pending tips in ch3.
            // Legacy ch4+ chapters are held for a future branch-specific continuation.
            if (stageOrder(stage) >= stageOrder("vol1_ch3")) break;
            String nextStage = stages.get(index + 1);
            Set<String> relevant = new LinkedHashSet<>(chapters.get(stage));
            for (String target : chapters.get(nextStage)) collectAncestors(target, relevant);
            for (String source : chapters.get(stage)) collectAncestors(source, relevant);
            relevant.removeAll(chapters.get(nextStage));
            List<String> choicesToEnumerate = topological.stream()
                    .filter(key -> relevant.contains(key) && "story".equals(events.get(key).path("event_type").asText()))
                    .toList();
            Set<String> coveredChoices = new HashSet<>();
            enumerate(choicesToEnumerate, 0, new LinkedHashMap<>(), nextStage,
                    chapters.get(nextStage), coveredChoices);
            for (String source : chapters.get(stage)) {
                for (int choice = 0; choice < events.get(source).path("choices").size(); choice++) {
                    if (!coveredChoices.contains(source + "[" + choice + "]")) {
                        problems.add("不可达主线选项 " + source + "[" + choice + "]");
                    }
                }
            }
        }
    }

    private static int stageOrder(String stage) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("vol(\\d+)(?:_ch(\\d+))?").matcher(stage);
        if (!matcher.find()) throw new IllegalArgumentException("未知主线章节顺序: " + stage);
        return Integer.parseInt(matcher.group(1)) * 100
                + (matcher.group(2) == null ? 99 : Integer.parseInt(matcher.group(2)));
    }

    private void collectAncestors(String key, Set<String> result) {
        JsonNode event = events.get(key);
        if (event == null) return;
        JsonNode conditions = event.path("required_conditions");
        for (String field : List.of("required_events", "required_side_events")) {
            for (JsonNode dependency : conditions.path(field)) {
                if (result.add(dependency.asText())) collectAncestors(dependency.asText(), result);
            }
        }
        for (JsonNode gate : conditions.path("choice_unlockers")) {
            String source = gate.path("event_key").asText();
            if (result.add(source)) collectAncestors(source, result);
        }
        for (JsonNode gate : conditions.path("required_choices")) {
            String source = gate.path("event_key").asText();
            if (result.add(source)) collectAncestors(source, result);
        }
        JsonNode location = locations.get(event.path("location_key").asText());
        if (location != null) {
            JsonNode access = location.path("unlock_conditions");
            for (String field : List.of("prerequisite_events", "prerequisite_events_any")) {
                for (JsonNode prerequisite : access.path(field)) {
                    String source = prerequisite.asText();
                    if (events.containsKey(source) && result.add(source)) collectAncestors(source, result);
                }
            }
        }
    }

    private void enumerate(List<String> relevant, int index, Map<String, Integer> decisions,
                           String nextStage, List<String> targets, Set<String> coveredChoices) {
        if (index == relevant.size()) {
            boolean routePending = decisions.entrySet().stream().anyMatch(entry ->
                    events.get(entry.getKey()).path("choices").get(entry.getValue())
                            .path("route_pending").asBoolean(false))
                    || relevant.stream().anyMatch(key -> events.get(key).path("progress_effects")
                            .path("route_pending").asBoolean(false)
                            && available(events.get(key), decisions));
            boolean nextAvailable = routePending
                    || targets.stream().anyMatch(key -> available(events.get(key), decisions));
            if (!nextAvailable) {
                String route = decisions.entrySet().stream()
                        .filter(entry -> targets.stream().anyMatch(target -> dependsOn(target, entry.getKey(), new HashSet<>())))
                        .map(entry -> entry.getKey() + "[" + entry.getValue() + "]")
                        .toList().toString();
                String problem = "断路 " + route + " -> 阻塞链 "
                        + blockedChain(targets.get(0), decisions, new HashSet<>())
                        + " -> " + nextStage + " 无可达主线事件 " + targets;
                if (!problems.contains(problem)) problems.add(problem);
            }
            return;
        }
        String key = relevant.get(index);
        JsonNode event = events.get(key);
        if (!available(event, decisions)) {
            enumerate(relevant, index + 1, decisions, nextStage, targets, coveredChoices);
            return;
        }
        for (int choice = 0; choice < event.path("choices").size(); choice++) {
            coveredChoices.add(key + "[" + choice + "]");
            decisions.put(key, choice);
            enumerate(relevant, index + 1, decisions, nextStage, targets, coveredChoices);
        }
        decisions.remove(key);
    }

    private boolean dependsOn(String target, String source, Set<String> visited) {
        if (!visited.add(target)) return false;
        JsonNode event = events.get(target);
        if (event == null) return false;
        for (JsonNode gate : event.path("required_conditions").path("choice_unlockers")) {
            String key = gate.path("event_key").asText();
            if (source.equals(key) || dependsOn(key, source, visited)) return true;
        }
        for (JsonNode required : event.path("required_conditions").path("required_events")) {
            String key = required.asText();
            if (source.equals(key) || dependsOn(key, source, visited)) return true;
        }
        return false;
    }

    private String blockedChain(String target, Map<String, Integer> decisions, Set<String> visited) {
        if (!visited.add(target)) return target;
        JsonNode event = events.get(target);
        if (event == null) return target;
        for (JsonNode gate : event.path("required_conditions").path("choice_unlockers")) {
            String source = gate.path("event_key").asText();
            if (!decisions.containsKey(source) && events.containsKey(source)
                    && !available(events.get(source), decisions)) {
                return blockedChain(source, decisions, visited) + " -> " + target + " 不可达";
            }
        }
        return target + " 不可达";
    }

    private boolean available(JsonNode event, Map<String, Integer> decisions) {
        if (event == null) return false;
        JsonNode conditions = event.path("required_conditions");
        for (JsonNode required : conditions.path("required_events")) {
            if (!decisions.containsKey(required.asText())) return false;
        }
        for (JsonNode gate : conditions.path("required_choices")) {
            Integer chosen = decisions.get(gate.path("event_key").asText());
            boolean matched = false;
            for (JsonNode index : gate.path("choice_indices")) {
                if (chosen != null && chosen == index.asInt(-1)) matched = true;
            }
            if (!matched) return false;
        }
        JsonNode gates = conditions.path("choice_unlockers");
        if (gates.isArray()) {
            boolean accepted = false;
            for (JsonNode gate : gates) {
                Integer chosen = decisions.get(gate.path("event_key").asText());
                for (JsonNode index : gate.path("choice_indices")) {
                    if (chosen != null && chosen == index.asInt(-1)) accepted = true;
                }
            }
            if (!accepted) return false;
        }
        JsonNode location = locations.get(event.path("location_key").asText());
        if (location == null) return false;
        JsonNode access = location.path("unlock_conditions");
        for (JsonNode required : access.path("prerequisite_events")) {
            if (events.containsKey(required.asText()) && !decisions.containsKey(required.asText())) return false;
        }
        JsonNode any = access.path("prerequisite_events_any");
        if (any.isArray() && !any.isEmpty()) {
            boolean satisfied = false;
            for (JsonNode required : any) {
                if (!events.containsKey(required.asText()) || decisions.containsKey(required.asText())) satisfied = true;
            }
            if (!satisfied) return false;
        }
        return true;
    }
}
