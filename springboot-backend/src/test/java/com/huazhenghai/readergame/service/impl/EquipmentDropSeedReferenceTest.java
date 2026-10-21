package com.huazhenghai.readergame.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentDropSeedReferenceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern EQUIPMENT_ROW = Pattern.compile(
            "(?m)^\\('([a-z0-9_]+)'\\s*,\\s*'[^']*'\\s*,"
    );
    private static final Pattern EQUIPMENT_DROP_KEY = Pattern.compile(
            "\\\"equipmentKey\\\"\\s*:\\s*\\\"([^\\\"]+)\\\""
    );

    @Test
    void everyMonsterAndExplorationEquipmentRewardReferencesASeededEquipment() throws Exception {
        Set<String> seededEquipmentKeys = new TreeSet<>();
        seededEquipmentKeys.addAll(parseKeys(
                equipmentInsertRows(readResource("/db/seed_equipment.sql")), EQUIPMENT_ROW));
        seededEquipmentKeys.addAll(parseKeys(
                equipmentInsertRows(readResource("/db/seed_synthesis_items.sql")), EQUIPMENT_ROW));

        Set<String> monsterEquipmentKeys = parseKeys(
                readResource("/db/seed_monsters.sql"), EQUIPMENT_DROP_KEY);
        JsonNode exploration = OBJECT_MAPPER.readTree(readResource("/db/seed_explore.json"));
        Set<String> explorationEquipmentKeys = new TreeSet<>();
        for (JsonNode equipmentKey : exploration.findValues("equipmentKey")) {
            if (equipmentKey.isTextual() && !equipmentKey.asText().isBlank()) {
                explorationEquipmentKeys.add(equipmentKey.asText());
            }
        }

        assertFalse(seededEquipmentKeys.isEmpty(), "装备种子中应能解析到装备键");
        assertFalse(monsterEquipmentKeys.isEmpty(), "怪物种子中应能解析到装备掉落键");
        assertFalse(explorationEquipmentKeys.isEmpty(), "探索种子中应能解析到装备奖励键");

        Set<String> missingMonsterEquipment = new TreeSet<>(monsterEquipmentKeys);
        missingMonsterEquipment.removeAll(seededEquipmentKeys);
        assertTrue(missingMonsterEquipment.isEmpty(),
                () -> "怪物掉落引用了未定义的装备：" + missingMonsterEquipment);

        Set<String> missingExplorationEquipment = new TreeSet<>(explorationEquipmentKeys);
        missingExplorationEquipment.removeAll(seededEquipmentKeys);
        assertTrue(missingExplorationEquipment.isEmpty(),
                () -> "探索/剧情奖励引用了未定义的装备：" + missingExplorationEquipment);
    }

    private static Set<String> parseKeys(String source, Pattern pattern) {
        Set<String> keys = new TreeSet<>();
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    private static String equipmentInsertRows(String sql) {
        String insert = "INSERT IGNORE INTO `equipment`";
        int start = sql.indexOf(insert);
        if (start < 0) {
            throw new IllegalArgumentException("装备种子缺少 equipment INSERT 语句");
        }
        int nextInsert = sql.indexOf("INSERT IGNORE INTO", start + insert.length());
        return sql.substring(start, nextInsert < 0 ? sql.length() : nextInsert);
    }

    private static String readResource(String path) throws IOException {
        try (InputStream stream = Objects.requireNonNull(
                EquipmentDropSeedReferenceTest.class.getResourceAsStream(path), path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
