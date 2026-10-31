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
        for (String source : List.of("geumho_shelter_story", "geumho_underground_story")) {
            assertTrue(sql.contains("'event_key', '" + source
                    + "', 'choice_indices', JSON_ARRAY(0, 1)"), "SQL 必经会合门槛未同步: " + source);
        }
        for (String target : List.of("geumho_underground_story", "chungmuro_iron_king_story")) {
            assertEquals(2, count(sql, "\"unlock_events\": [\"" + target + "\"]"),
                    "SQL 两个前置选项都应提示解锁: " + target);
        }
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
                "chungmuro_iron_king_story", "geumho_underground_story").entrySet()) {
            JsonNode target = byKey.get(gate.getKey());
            @SuppressWarnings("unchecked")
            Map<String, Object> conditions = mapper.convertValue(target.path("required_conditions"), Map.class);
            for (int choice = 0; choice < 2; choice++) {
                List<Map<String, Object>> history = List.of(Map.of(
                        "event_key", gate.getValue(), "choice_index", choice));
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
            if (!locations.containsKey(event.path("location_key").asText())) {
                problems.add(key + " 引用不存在的地点 " + event.path("location_key").asText());
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
            boolean nextAvailable = targets.stream().anyMatch(key -> available(events.get(key), decisions));
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
