package com.huazhenghai.readergame.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplorationEncounterMonsterSeedTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern MONSTER_ROW = Pattern.compile(
            "(?m)^[ \\t]*\\('([^']+)'\\s*,\\s*'[^']*'\\s*,\\s*'[^']*'\\s*,\\s*'[^']*'\\s*,\\s*'([^']+)'"
    );
    private static final Map<String, String> NEW_ENCOUNTER_MONSTERS = Map.of(
            "fire_lord_projection", "dongdaemun",
            "library_hunger_echo", "myeongdong",
            "bridge_hunger_entity", "gwangjin_bridge",
            "dungeon_guardian_program", "gangnam_station",
            "corrupted_forest_beast", "seoul_forest"
    );

    @Test
    void everyBattleEncounterHasASeededMonsterAndOnlyKnownCrossLocationReuseRemains() throws Exception {
        JsonNode exploration = OBJECT_MAPPER.readTree(readResource("/db/seed_explore.json"));
        Map<String, String> monsterLocations = parseMonsterLocations(readResource("/db/seed_monsters.sql"));
        Map<String, String> crossLocationEncounters = new TreeMap<>();

        for (JsonNode entry : exploration) {
            if (!"event".equals(entry.path("type").asText())
                    || !"battle_placeholder".equals(entry.path("event_type").asText())) {
                continue;
            }

            String eventKey = entry.path("event_key").asText();
            String eventLocation = entry.path("location_key").asText();
            String monsterKey = entry.path("risks_json").path("monsterKey").asText();
            assertTrue(monsterLocations.containsKey(monsterKey),
                    () -> "战斗事件引用了未定义的怪物：" + eventKey + " -> " + monsterKey);

            String monsterLocation = monsterLocations.get(monsterKey);
            if (!eventLocation.equals(monsterLocation)) {
                crossLocationEncounters.put(eventKey, monsterKey);
            }
        }

        assertEquals(Map.of(
                "geumho_station_battle_shadow", "broken_avatar_shadow",
                "dongmyo_battle_rat", "station_rat",
                "chungmuro_battle_keeper", "station_keeper_fragment"
        ), crossLocationEncounters,
                "仅保留名称/文案可解释的三项跨地点复用；其他遭遇应使用对应地点的怪物");
    }

    @Test
    void battlePlaceholderRewardsAreClearedAndMigrationTargetsExactlyTheStaticEncounterSet() throws Exception {
        JsonNode exploration = OBJECT_MAPPER.readTree(readResource("/db/seed_explore.json"));
        String migration = readResource("/db/migrations/20260929_add_exploration_encounter_monsters.sql");
        String cleanup = migration.substring(migration.indexOf("SET `rewards_json` = JSON_OBJECT()"));
        int eventKeysStart = cleanup.indexOf("AND `event_key` IN (");
        assertTrue(eventKeysStart >= 0, "静态迁移应使用白名单限定需要清理的事件键");
        Matcher cleanupEventKeysMatcher = Pattern.compile("'([a-z0-9_]+)'")
                .matcher(cleanup.substring(eventKeysStart));
        Map<String, Boolean> cleanupEventKeys = new TreeMap<>();
        while (cleanupEventKeysMatcher.find()) {
            cleanupEventKeys.put(cleanupEventKeysMatcher.group(1), true);
        }
        int battleEventCount = 0;

        assertTrue(cleanup.contains("WHERE `event_type` = 'battle_placeholder'"));
        for (JsonNode entry : exploration) {
            if (!"event".equals(entry.path("type").asText())
                    || !"battle_placeholder".equals(entry.path("event_type").asText())) {
                continue;
            }

            battleEventCount++;
            String eventKey = entry.path("event_key").asText();
            assertTrue(entry.path("rewards").isObject() && entry.path("rewards").isEmpty(),
                    () -> "战斗占位事件不应保留未生效的事件奖励：" + eventKey);
            assertTrue(cleanup.contains("'" + eventKey + "'"),
                    () -> "静态迁移缺少该战斗占位事件的奖励字段清理：" + eventKey);
        }

        assertEquals(18, battleEventCount, "本迁移应只清理当前 18 个静态战斗占位事件");
        assertEquals(18, cleanupEventKeys.size(), "迁移清理范围不能超出 18 个固定事件键");
    }

    @Test
    void newEncounterMonstersAreConsistentBetweenBaseSeedAndStaticMigration() throws Exception {
        String baseSeed = readResource("/db/seed_monsters.sql");
        String migration = readResource("/db/migrations/20260929_add_exploration_encounter_monsters.sql");
        Map<String, String> baseLocations = parseMonsterLocations(baseSeed);
        Map<String, String> migrationLocations = parseMonsterLocations(migration);

        Map<String, String> actualBase = new HashMap<>();
        Map<String, String> actualMigration = new HashMap<>();
        for (String monsterKey : NEW_ENCOUNTER_MONSTERS.keySet()) {
            actualBase.put(monsterKey, baseLocations.get(monsterKey));
            actualMigration.put(monsterKey, migrationLocations.get(monsterKey));
            assertEquals(monsterRow(baseSeed, monsterKey), monsterRow(migration, monsterKey),
                    () -> "基础种子与静态迁移中的怪物定义不一致：" + monsterKey);
        }

        assertEquals(NEW_ENCOUNTER_MONSTERS, actualBase);
        assertEquals(NEW_ENCOUNTER_MONSTERS, actualMigration);

        Map<String, String> correctedEvents = Map.of(
                "dongdaemun_battle_flame", "fire_lord_projection",
                "myeongdong_battle_bookworm", "library_hunger_echo",
                "gwangjin_battle_hunger", "bridge_hunger_entity",
                "gangnam_battle_dungeon", "dungeon_guardian_program",
                "seoul_forest_battle_beast", "corrupted_forest_beast"
        );
        String normalizedMigration = migration.replace("\r\n", "\n");
        for (Map.Entry<String, String> correction : correctedEvents.entrySet()) {
            String update = "JSON_SET(COALESCE(`risks_json`, JSON_OBJECT()), '$.monsterKey', '"
                    + correction.getValue() + "')\nWHERE `event_key` = '" + correction.getKey() + "'";
            assertTrue(normalizedMigration.contains(update),
                    () -> "静态迁移缺少遭遇怪物键更新：" + correction.getKey());
        }
    }

    private static Map<String, String> parseMonsterLocations(String sql) {
        Map<String, String> locations = new HashMap<>();
        Matcher matcher = MONSTER_ROW.matcher(sql);
        while (matcher.find()) {
            locations.put(matcher.group(1), matcher.group(2));
        }
        return locations;
    }

    private static String monsterRow(String sql, String monsterKey) {
        String prefix = "('" + monsterKey + "',";
        int start = sql.indexOf(prefix);
        if (start < 0) return "<missing:" + monsterKey + ">";
        int end = sql.indexOf("\n 1)", start);
        if (end < 0) return "<unterminated:" + monsterKey + ">";
        return sql.substring(start, end + 4).stripTrailing().replaceFirst("[,;]$", "");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream stream = Objects.requireNonNull(
                ExplorationEncounterMonsterSeedTest.class.getResourceAsStream(path), path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
