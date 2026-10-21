package com.huazhenghai.readergame.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryRouteSeedIntegrityTest {

    @Test
    void seededPersonalStoryRoutesReferenceExistingEventsChoicesAndLocations() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode root;
        try (InputStream input = getClass().getResourceAsStream("/db/seed_explore.json")) {
            assertNotNull(input, "探索剧情种子文件必须在测试 classpath 中");
            root = objectMapper.readTree(input);
        }

        Set<String> locationKeys = new HashSet<>();
        Map<String, JsonNode> eventsByKey = new HashMap<>();
        for (JsonNode entry : root) {
            if ("location".equals(entry.path("type").asText())) {
                locationKeys.add(entry.path("location_key").asText());
            } else if ("event".equals(entry.path("type").asText())) {
                eventsByKey.put(entry.path("event_key").asText(), entry);
            }
        }

        int choiceUnlockLinks = 0;
        int locationGrantReferences = 0;
        int sideEvidenceReferences = 0;
        for (Map.Entry<String, JsonNode> targetEntry : eventsByKey.entrySet()) {
            String targetEventKey = targetEntry.getKey();
            JsonNode targetEvent = targetEntry.getValue();
            for (JsonNode sideEvidence : targetEvent.path("required_conditions").path("required_side_events")) {
                sideEvidenceReferences++;
                String sideEventKey = sideEvidence.asText();
                JsonNode sourceEvent = eventsByKey.get(sideEventKey);
                assertNotNull(sourceEvent,
                        () -> targetEventKey + " 的剧情条件引用了不存在的支线证据 " + sideEventKey);
                assertFalse("story".equals(sourceEvent.path("event_type").asText()),
                        () -> targetEventKey + " 的 required_side_events 必须引用非主线事件 " + sideEventKey);
            }
            JsonNode unlockers = targetEvent.path("required_conditions").path("choice_unlockers");
            for (JsonNode unlocker : unlockers) {
                choiceUnlockLinks++;
                String sourceEventKey = unlocker.path("event_key").asText();
                JsonNode sourceEvent = eventsByKey.get(sourceEventKey);
                assertNotNull(sourceEvent,
                        () -> targetEventKey + " 的分支条件引用了不存在的剧情事件 " + sourceEventKey);
                JsonNode sourceChoices = sourceEvent.path("choices");
                JsonNode choiceIndices = unlocker.path("choice_indices");
                assertTrue(choiceIndices.isArray() && !choiceIndices.isEmpty(),
                        () -> targetEventKey + " 的分支条件没有可触发的选项索引");
                for (JsonNode index : choiceIndices) {
                    int choiceIndex = index.asInt(-1);
                    assertTrue(choiceIndex >= 0 && choiceIndex < sourceChoices.size(),
                            () -> targetEventKey + " 引用了不存在的选项 " + sourceEventKey + "[" + choiceIndex + "]");
                }
            }

            for (JsonNode choice : targetEvent.path("choices")) {
                for (JsonNode unlockedLocation : choice.path("unlock_locations")) {
                    locationGrantReferences++;
                    String locationKey = unlockedLocation.asText();
                    assertTrue(locationKeys.contains(locationKey),
                            () -> targetEventKey + " 的选项解锁了不存在的地点 " + locationKey);
                }
            }
        }

        assertTrue(choiceUnlockLinks > 0, "种子中应存在个人选择后续分支，防止此校验悄然失效");
        assertTrue(locationGrantReferences > 0, "种子中应存在由故事选择发放的地点解锁");
        assertTrue(sideEvidenceReferences > 0, "种子中应存在由个人支线证据开启的可选剧情");
    }

    @Test
    void storyResonanceRequiresBothPersonalEvidenceAndOnlyAddsPrivateLaterContext() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode root;
        String sqlSeed;
        String migration;
        try (InputStream input = getClass().getResourceAsStream("/db/seed_explore.json");
             InputStream sqlInput = getClass().getResourceAsStream("/db/seed_explore.sql");
             InputStream migrationInput = getClass().getResourceAsStream(
                     "/db/migrations/20261001_add_personal_story_resonance.sql")) {
            assertNotNull(input, "探索 JSON 种子必须包含个人剧情交错事件");
            assertNotNull(sqlInput, "人工初始化 SQL 必须包含个人剧情交错事件");
            assertNotNull(migrationInput, "既有库迁移必须包含个人剧情交错事件");
            root = objectMapper.readTree(input);
            sqlSeed = new String(sqlInput.readAllBytes(), StandardCharsets.UTF_8);
            migration = new String(migrationInput.readAllBytes(), StandardCharsets.UTF_8);
        }

        Map<String, JsonNode> eventsByKey = new HashMap<>();
        for (JsonNode entry : root) {
            if ("event".equals(entry.path("type").asText())) {
                eventsByKey.put(entry.path("event_key").asText(), entry);
            }
        }
        JsonNode resonance = eventsByKey.get("darkest_spring_story_stitch");
        assertNotNull(resonance);
        JsonNode conditions = resonance.path("required_conditions");
        assertEquals(List.of("darkest_spring_first_omen", "darkest_spring_forest_vision"),
                objectMapper.convertValue(conditions.path("required_events"), List.class));
        assertEquals(List.of("geumho_echo_whisper"),
                objectMapper.convertValue(conditions.path("required_side_events"), List.class));
        assertEquals("darkest_spring_forest_vision",
                conditions.path("choice_unlockers").get(0).path("event_key").asText());
        JsonNode forestVision = eventsByKey.get("darkest_spring_forest_vision");
        for (int choiceIndex = 0; choiceIndex < 2; choiceIndex++) {
            List<String> unlockedEvents = objectMapper.convertValue(
                    forestVision.path("choices").get(choiceIndex).path("unlock_events"), List.class);
            assertTrue(unlockedEvents.contains("darkest_spring_story_stitch"),
                    "森林幻象的第 " + choiceIndex + " 项必须实际解锁个人支线");
            assertTrue(unlockedEvents.contains("darkest_spring_abyss_gate"),
                    "森林幻象的第 " + choiceIndex + " 项文本都把玩家引向恶魔之门");
        }
        assertEquals(2, resonance.path("choices").size());
        assertTrue(resonance.path("choices").get(0).path("set_story_flags")
                .path("story_dark_spring_interwoven").asBoolean());
        assertTrue(resonance.path("choices").get(1).path("set_story_flags")
                .path("story_dark_spring_separated").asBoolean());

        JsonNode abyssGate = eventsByKey.get("darkest_spring_abyss_gate");
        assertEquals(List.of(0, 1), objectMapper.convertValue(abyssGate.path("required_conditions")
                .path("choice_unlockers").get(0).path("choice_indices"), List.class));
        JsonNode laterContexts = abyssGate.path("required_conditions").path("personal_contexts");
        assertEquals(2, laterContexts.size());
        assertEquals("story_dark_spring_interwoven", laterContexts.get(0).path("story_flag").asText());
        assertEquals("story_dark_spring_separated", laterContexts.get(1).path("story_flag").asText());
        assertTrue(sqlSeed.contains("darkest_spring_story_stitch"));
        assertTrue(migration.contains("darkest_spring_story_stitch"));
        assertTrue(migration.contains("darkest_spring_forest_vision"));
        assertTrue(migration.contains("'$[0].unlock_events'"));
        assertTrue(migration.contains("'$[1].unlock_events'"));
        assertTrue(migration.contains("JSON_ARRAY('darkest_spring_abyss_gate', 'darkest_spring_story_stitch')"));
        assertTrue(migration.contains("JSON_ARRAY(0, 1)"));
        assertFalse(migration.toLowerCase().contains("players"), "故事迁移不得修改任何玩家记录");
    }

    @Test
    void originalFinaleIsAOneWayPromiseFollowedByAPersonalContinuationChoice() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode root;
        String sqlSeed;
        try (InputStream input = getClass().getResourceAsStream("/db/seed_explore.json");
             InputStream sqlInput = getClass().getResourceAsStream("/db/seed_explore.sql")) {
            assertNotNull(input, "探索剧情种子文件必须在测试 classpath 中");
            assertNotNull(sqlInput, "人工初始化 SQL 必须包含终局剧情");
            root = objectMapper.readTree(input);
            sqlSeed = new String(sqlInput.readAllBytes(), StandardCharsets.UTF_8);
        }

        Map<String, JsonNode> eventsByKey = new HashMap<>();
        Set<String> locationKeys = new HashSet<>();
        for (JsonNode entry : root) {
            if ("location".equals(entry.path("type").asText())) {
                locationKeys.add(entry.path("location_key").asText());
            } else if ("event".equals(entry.path("type").asText())) {
                eventsByKey.put(entry.path("event_key").asText(), entry);
            }
        }

        JsonNode promise = eventsByKey.get("ending_old_dream_promise");
        JsonNode finalChoice = eventsByKey.get("ending_dream_continuation_choice");
        assertNotNull(promise, "应先有终结旧梦的个人剧情节点");
        assertNotNull(finalChoice, "终结旧梦后应出现延续梦境的个人选择");
        JsonNode personalContexts = finalChoice.path("required_conditions").path("personal_contexts");
        assertEquals("demon_gate_first_step_story", promise.path("required_conditions")
                .path("required_events").get(0).asText());
        assertTrue(promise.path("choices").get(0).path("set_story_flags")
                .path("ending_old_dream_ended").asBoolean());
        assertEquals("ending_dream_continuation_choice", promise.path("choices").get(0)
                .path("unlock_events").get(0).asText());
        assertEquals("ending_old_dream_promise", finalChoice.path("required_conditions")
                .path("required_events").get(0).asText());
        assertEquals("ending_old_dream_promise", finalChoice.path("required_conditions")
                .path("choice_unlockers").get(0).path("event_key").asText());
        assertEquals(0, finalChoice.path("required_conditions")
                .path("choice_unlockers").get(0).path("choice_indices").get(0).asInt());
        assertEquals(2, finalChoice.path("choices").size());
        assertTrue(finalChoice.path("choices").get(0).path("set_story_flags")
                .path("ending_dream_continued").asBoolean());
        assertTrue(finalChoice.path("choices").get(0).path("set_story_flags")
                .path("ending_new_oldest_dream").asBoolean());
        assertTrue(finalChoice.path("choices").get(1).path("set_story_flags")
                .path("ending_world_frozen").asBoolean());
        assertEquals(2, personalContexts.size(), "终局只回响两条已存在的个人调查线索");
        JsonNode npcSeeds;
        try (InputStream npcInput = getClass().getResourceAsStream("/data/seedNpcGhosts.json")) {
            assertNotNull(npcInput, "NPC 残影种子必须存在，供验证线索旗标来源");
            npcSeeds = objectMapper.readTree(npcInput);
        }
        for (JsonNode context : personalContexts) {
            String storyFlag = context.path("story_flag").asText();
            JsonNode sourceNpc = null;
            for (JsonNode npc : npcSeeds) {
                if (npc.findValues("addStoryFlag").stream()
                        .anyMatch(flag -> storyFlag.equals(flag.asText()))) {
                    sourceNpc = npc;
                    break;
                }
            }
            assertNotNull(sourceNpc, () -> "终局回响引用的线索旗标必须由 NPC 对话真实写入：" + storyFlag);
            boolean sourceIsOnActiveMap = false;
            for (JsonNode location : sourceNpc.path("location_keys")) {
                if (locationKeys.contains(location.asText())) {
                    sourceIsOnActiveMap = true;
                    break;
                }
            }
            assertTrue(sourceIsOnActiveMap,
                    () -> storyFlag + " 的 NPC 来源必须至少绑定一个当前正式探索地点");
            assertFalse(finalChoice.path("required_conditions").path("required_events").toString()
                    .contains(storyFlag), "线索旗标只能个性化文案，不得成为解锁条件");
        }
        assertTrue(sqlSeed.contains("('ending_old_dream_promise', 'story', 'vol12_finale', 'demon_gate'"));
        assertTrue(sqlSeed.contains("('ending_dream_continuation_choice', 'story', 'vol12_finale', 'demon_gate'"));
        assertTrue(sqlSeed.contains("WHERE `event_key` = 'ending_old_dream_promise'"));
        assertTrue(sqlSeed.contains("'event_key', 'ending_old_dream_promise', 'choice_indices', JSON_ARRAY(0)"));
        assertTrue(sqlSeed.contains("'personal_contexts', JSON_ARRAY"));
        assertTrue(sqlSeed.contains("oldest_dream_location"));
        assertTrue(sqlSeed.contains("oldest_dream_last_page"));
        assertTrue(sqlSeed.contains("\"unlock_events\":[\"ending_dream_continuation_choice\"]"));
    }

    @Test
    void finalWallEchoIsAnOptionalPersonalEncounterAndNotARewriteOfTheNovelFight() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode root;
        String monsterSeed;
        String migration;
        try (InputStream exploreInput = getClass().getResourceAsStream("/db/seed_explore.json");
             InputStream monsterInput = getClass().getResourceAsStream("/db/seed_monsters.sql");
             InputStream migrationInput = getClass().getResourceAsStream(
                     "/db/migrations/20261001_add_personal_final_wall_echo.sql")) {
            assertNotNull(exploreInput, "终墙回响事件必须出现在探索种子");
            assertNotNull(monsterInput, "终墙回响怪物必须出现在怪物种子");
            assertNotNull(migrationInput, "终墙回响静态迁移必须存在");
            root = objectMapper.readTree(exploreInput);
            monsterSeed = new String(monsterInput.readAllBytes(), StandardCharsets.UTF_8);
            migration = new String(migrationInput.readAllBytes(), StandardCharsets.UTF_8);
        }

        JsonNode event = null;
        for (JsonNode entry : root) {
            if ("last_wall_echo_personal_story".equals(entry.path("event_key").asText())) {
                event = entry;
                break;
            }
        }
        assertNotNull(event, "应新增终局后的个人回响节点");
        assertEquals("ending_dream_continuation_choice", event.path("required_conditions")
                .path("required_events").get(0).asText());
        assertEquals(0, event.path("repeatable").asInt(), "这段终局回响只记录一次选择");
        assertTrue(event.path("description").asText().contains("本游戏的平衡改编"));
        assertTrue(event.path("description").asText().contains("不是原著数值"));

        JsonNode echoChoice = event.path("choices").get(0);
        assertEquals("last_wall_dokkaebi_echo", echoChoice.path("risks").path("monsterKey").asText());
        assertTrue(echoChoice.path("set_story_flags").path("story_final_wall_echo_attuned").asBoolean());
        assertEquals(2, event.path("choices").size(), "玩家可以选择不介入原著战斗");
        assertTrue(event.path("choices").get(1).path("set_story_flags")
                .path("story_final_wall_echo_observed").asBoolean());
        assertTrue(monsterSeed.contains("('last_wall_dokkaebi_echo', '终墙鬼怪之王回声'"));
        assertTrue(monsterSeed.contains("并非原著鬼怪之王本人"));
        assertTrue(migration.contains("INSERT INTO `exploration_events`"));
        assertTrue(migration.contains("INSERT INTO `monsters`"));
        assertFalse(migration.toLowerCase().contains("players"), "静态迁移不得改写玩家存档");
    }

    @Test
    void finaleMigrationChangesOnlyStaticStoryAndEndingDefinitions() throws Exception {
        String migration;
        String init;
        try (InputStream migrationInput = getClass().getResourceAsStream(
                     "/db/migrations/20260930_rewrite_orv_final_endings.sql");
             InputStream initInput = getClass().getResourceAsStream("/db/init.sql")) {
            assertNotNull(migrationInput, "终局迁移必须存在");
            assertNotNull(initInput, "初始化 SQL 必须存在");
            migration = new String(migrationInput.readAllBytes(), StandardCharsets.UTF_8);
            init = new String(initInput.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(migration.contains("DELETE FROM `endings`"));
        assertTrue(migration.contains("UPDATE `npc_ghosts`"));
        assertTrue(migration.contains("'oldest_dream_fragment'"));
        assertTrue(migration.contains("JSON_ARRAY('demon_gate')"));
        assertTrue(migration.contains("UPDATE `exploration_events`"));
        assertTrue(migration.contains("ending_old_dream_promise"));
        assertTrue(migration.contains("ending_dream_continuation_choice"));
        assertTrue(migration.contains("end_continue_dream"));
        assertTrue(migration.contains("end_frozen_world"));
        assertTrue(migration.contains("ending_new_oldest_dream"));
        assertTrue(migration.contains("ending_world_frozen"));
        assertTrue(migration.contains("oldest_dream_location"));
        assertTrue(migration.contains("oldest_dream_last_page"));
        assertFalse(migration.contains("UPDATE `players`"), "迁移不能改写玩家存档");
        assertFalse(migration.contains("DELETE FROM `players`"), "迁移不能删除玩家存档");
        assertTrue(init.contains("end_continue_dream"));
        assertTrue(init.contains("end_frozen_world"));
        for (String retiredEndingKey : List.of(
                "end_survivor", "end_demon_king", "end_savior", "end_companion",
                "end_collapse", "end_lonely_reader", "end_true")) {
            assertFalse(init.contains("('" + retiredEndingKey + "'"),
                    () -> "新库不能继续种入已退役结局 " + retiredEndingKey);
            assertTrue(migration.contains("'" + retiredEndingKey + "'"),
                    () -> "静态迁移应清理旧结局定义 " + retiredEndingKey);
        }
        assertFalse(init.contains("entered_castle") || init.contains("merged_dream")
                        || init.contains("woke_dreamer") || init.contains("accepted_fate"),
                "旧的不可达终局旗标不能继续出现在新库种子中");
    }
}
