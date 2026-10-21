package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.ResourceBalance;
import com.huazhenghai.readergame.entity.*;
import com.huazhenghai.readergame.mapper.*;
import com.huazhenghai.readergame.service.InventoryService;
import com.huazhenghai.readergame.service.ItemGrantResult;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.service.NarrativeService;
import com.huazhenghai.readergame.service.PlayerActionReceiptService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NarrativeServiceImpl implements NarrativeService {

    private static final Logger log = LoggerFactory.getLogger(NarrativeServiceImpl.class);
    private static final Pattern ENCOUNTER_PROGRESS_PATTERN =
            Pattern.compile("^\\[NARRATIVE node=(\\d+) choice=(\\d+) next=(-?\\d+)]\\s*");

    private record EncounterProgress(int nodeIndex, int choiceIndex, int nextNodeIndex) {}

    private final ItemMemoryMapper itemMemoryMapper;
    private final LocationEchoMapper locationEchoMapper;
    private final NpcGhostMapper npcGhostMapper;
    private final PlayerNpcEncounterMapper encounterMapper;
    private final PlayerMapper playerMapper;
    private final PlayerSkillMapper skillMapper;
    private final ItemMapper itemMapper;
    private final ObjectMapper objectMapper;
    private final InventoryService inventoryService;
    private final PlayerLogService playerLogService;
    private final PlayerActionReceiptService actionReceiptService;

    public NarrativeServiceImpl(ItemMemoryMapper itemMemoryMapper,
                                LocationEchoMapper locationEchoMapper,
                                NpcGhostMapper npcGhostMapper,
                                PlayerNpcEncounterMapper encounterMapper,
                                PlayerMapper playerMapper,
                                PlayerSkillMapper skillMapper,
                                ItemMapper itemMapper,
                                ObjectMapper objectMapper,
                                InventoryService inventoryService,
                                PlayerLogService playerLogService,
                                PlayerActionReceiptService actionReceiptService) {
        this.itemMemoryMapper = itemMemoryMapper;
        this.locationEchoMapper = locationEchoMapper;
        this.npcGhostMapper = npcGhostMapper;
        this.encounterMapper = encounterMapper;
        this.playerMapper = playerMapper;
        this.skillMapper = skillMapper;
        this.itemMapper = itemMapper;
        this.objectMapper = objectMapper;
        this.inventoryService = inventoryService;
        this.playerLogService = playerLogService;
        this.actionReceiptService = actionReceiptService;
    }

    @PostConstruct
    public void seedNpcGhostsIfMissing() {
        try {
            Long count = npcGhostMapper.selectCount(null);
            log.info("Ensuring NPC ghost seed data exists ({} existing ghosts); existing configuration is preserved",
                    count != null ? count : 0);

            ClassPathResource resource = new ClassPathResource("data/seedNpcGhosts.json");
            try (InputStream is = resource.getInputStream()) {
                List<Map<String, Object>> ghosts = objectMapper.readValue(is,
                        new TypeReference<List<Map<String, Object>>>() {});
                int insertedCount = 0;
                for (Map<String, Object> g : ghosts) {
                    String ghostKey = String.valueOf(g.get("ghost_key"));
                    NpcGhost existing = npcGhostMapper.selectOne(
                            new QueryWrapper<NpcGhost>().eq("ghost_key", ghostKey));
                    if (existing != null) continue;

                    NpcGhost ghost = new NpcGhost();
                    ghost.setGhostKey(ghostKey);
                    ghost.setName(String.valueOf(g.get("name")));
                    ghost.setDescription(String.valueOf(g.get("description")));
                    ghost.setDialogueTreeJson(objectMapper.writeValueAsString(g.get("dialogue_tree")));
                    ghost.setLocationKeysJson(objectMapper.writeValueAsString(g.get("location_keys")));
                    ghost.setEncounterWeight(toDouble(g.get("encounter_weight"), 0.05));
                    ghost.setIsUnique(toInt(g.get("is_unique"), 0));
                    npcGhostMapper.insert(ghost);
                    insertedCount++;
                }
                log.info("NPC ghost seed complete: added {} missing entries out of {}", insertedCount, ghosts.size());
            }
        } catch (Exception e) {
            log.warn("NPC ghost seed failed (table may already have data): {}", e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> getItemMemories(String itemKey) {
        QueryWrapper<ItemMemory> qw = new QueryWrapper<>();
        qw.eq("item_key", itemKey);
        return itemMemoryMapper.selectList(qw).stream().map(this::formatMemory).toList();
    }

    @Override
    public List<Map<String, Object>> getAllItemMemories() {
        return itemMemoryMapper.selectList(null).stream().map(this::formatMemory).toList();
    }

    private Map<String, Object> formatMemory(ItemMemory row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.getId());
        m.put("itemKey", row.getItemKey());
        Item item = itemMapper.selectOne(new QueryWrapper<Item>().eq("item_key", row.getItemKey()));
        m.put("itemName", item != null ? item.getName() : row.getItemKey());
        m.put("memoryText", row.getMemoryText());
        m.put("narrator", row.getNarrator());
        return m;
    }

    @Override
    public List<Map<String, Object>> getLocationEchoes(String locationKey) {
        QueryWrapper<LocationEcho> qw = new QueryWrapper<>();
        qw.eq("location_key", locationKey).orderByDesc("weight");
        return locationEchoMapper.selectList(qw).stream().map(this::formatEcho).toList();
    }

    private Map<String, Object> formatEcho(LocationEcho row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.getId());
        m.put("locationKey", row.getLocationKey());
        m.put("echoText", row.getEchoText());
        m.put("narrator", row.getNarrator());
        m.put("weight", row.getWeight());
        return m;
    }

    @Override
    public Map<String, Object> recordLocationEcho(Long playerId, String locationKey) {
        List<Map<String, Object>> echoes = getLocationEchoes(locationKey);
        if (echoes.isEmpty()) return null;

        // Weighted random selection
        double totalWeight = echoes.stream().mapToDouble(e -> toDouble(e.get("weight"), 1.0)).sum();
        double roll = Math.random() * totalWeight;
        double cumulative = 0;
        Map<String, Object> chosen = null;
        for (Map<String, Object> e : echoes) {
            cumulative += toDouble(e.get("weight"), 1.0);
            if (roll <= cumulative) { chosen = e; break; }
        }
        if (chosen == null) chosen = echoes.get(0);
        return chosen;
    }

    @Override
    public Map<String, Object> checkNpcGhostEncounter(Long playerId, String locationKey) {
        Player player = playerMapper.selectById(playerId);
        if (player == null) return null;

        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        int luck = toInt(stats.get("luck"), 1);

        QueryWrapper<NpcGhost> qw = new QueryWrapper<>();
        qw.like("location_keys_json", locationKey);
        List<NpcGhost> ghosts = npcGhostMapper.selectList(qw);
        if (ghosts.isEmpty()) return null;

        // 先恢复已开始但尚未结束的对话，避免其他随机残影抢占交互。
        for (NpcGhost ghost : ghosts) {
            List<Map<String, Object>> dialogueTree = parseJsonList(ghost.getDialogueTreeJson());
            EncounterProgress progress = parseProgress(findLatestEncounter(playerId, ghost.getGhostKey()));
            if (progress == null || progress.nextNodeIndex() < 0) continue;
            if (progress.nextNodeIndex() >= dialogueTree.size()) {
                log.warn("NPC ghost {} has invalid saved next node {}", ghost.getGhostKey(), progress.nextNodeIndex());
                continue;
            }

            QueryWrapper<PlayerNpcEncounter> encounterQuery = new QueryWrapper<>();
            encounterQuery.eq("player_id", playerId).eq("ghost_key", ghost.getGhostKey());
            long encounterCount = encounterMapper.selectCount(encounterQuery);
            return ghostEncounterResult(ghost, dialogueTree, encounterCount, progress.nextNodeIndex());
        }

        for (NpcGhost g : ghosts) {
            List<Map<String, Object>> dialogueTree = parseJsonList(g.getDialogueTreeJson());
            if (dialogueTree.isEmpty()) continue;

            QueryWrapper<PlayerNpcEncounter> encounterQuery = new QueryWrapper<>();
            encounterQuery.eq("player_id", playerId).eq("ghost_key", g.getGhostKey());
            long encounterCount = encounterMapper.selectCount(encounterQuery);
            PlayerNpcEncounter latestEncounter = findLatestEncounter(playerId, g.getGhostKey());
            EncounterProgress progress = parseProgress(latestEncounter);

            boolean completed = false;
            boolean continuing = false;
            int startNodeIndex;
            if (progress != null) {
                if (progress.nextNodeIndex() < 0) {
                    completed = true;
                    startNodeIndex = 0;
                } else if (progress.nextNodeIndex() < dialogueTree.size()) {
                    continuing = true;
                    startNodeIndex = progress.nextNodeIndex();
                } else {
                    log.warn("NPC ghost {} has invalid saved next node {}", g.getGhostKey(), progress.nextNodeIndex());
                    continue;
                }
            } else {
                completed = encounterCount >= dialogueTree.size();
                startNodeIndex = completed ? 0 : (int) encounterCount;
            }

            boolean unique = g.getIsUnique() != null && g.getIsUnique() == 1;
            if (completed && unique) continue;

            double effectiveWeight = g.getEncounterWeight() * (1 + luck * 0.02);
            if (continuing) {
                // A saved conversation should resume reliably instead of requiring another random encounter roll.
                effectiveWeight = 1.0;
            } else if (completed) {
                effectiveWeight *= 0.3;
            }

            if (Math.random() < effectiveWeight) {
                return ghostEncounterResult(g, dialogueTree, encounterCount, startNodeIndex);
            }
        }

        return null;
    }

    @Override
    @Transactional
    public Map<String, Object> processNpcGhostEncounter(Long playerId, String ghostKey, int nodeIndex, int choiceIndex) {
        return processNpcGhostEncounter(playerId, ghostKey, nodeIndex, choiceIndex, null);
    }

    @Override
    @Transactional
    public Map<String, Object> processNpcGhostEncounter(Long playerId, String ghostKey, int nodeIndex,
                                                        int choiceIndex, String requestKey) {
        Player player = playerMapper.selectByIdForUpdate(playerId);
        if (player == null) return Map.of("error", "player_not_found");

        Map<String, Object> requestPayload = Map.of(
                "ghostKey", ghostKey,
                "nodeIndex", nodeIndex,
                "choiceIndex", choiceIndex);
        if (requestKey != null) {
            Map<String, Object> replay = actionReceiptService.findReplay(
                    playerId, "narrative.ghost.choice", requestKey, requestPayload, Map.class);
            if (replay != null) return replay;
        }

        QueryWrapper<NpcGhost> qw = new QueryWrapper<>();
        qw.eq("ghost_key", ghostKey);
        NpcGhost ghost = npcGhostMapper.selectOne(qw);
        if (ghost == null) return Map.of("error", "ghost_not_found");

        List<Map<String, Object>> dialogueTree = parseJsonList(ghost.getDialogueTreeJson());
        if (dialogueTree.isEmpty()) return Map.of("error", "invalid_dialogue_tree");

        if (nodeIndex < 0 || nodeIndex >= dialogueTree.size())
            return Map.of("error", "invalid_node_index");

        QueryWrapper<PlayerNpcEncounter> encounterQuery = new QueryWrapper<>();
        encounterQuery.eq("player_id", playerId).eq("ghost_key", ghostKey);
        long encounterCount = encounterMapper.selectCount(encounterQuery);
        PlayerNpcEncounter latestEncounter = findLatestEncounter(playerId, ghostKey);
        EncounterProgress progress = parseProgress(latestEncounter);

        boolean unique = ghost.getIsUnique() != null && ghost.getIsUnique() == 1;
        int expectedNodeIndex;
        if (progress != null) {
            if (progress.nodeIndex() == nodeIndex && progress.choiceIndex() == choiceIndex)
                return Map.of("error", "choice_already_processed");
            if (progress.nextNodeIndex() < 0) {
                if (unique) return Map.of("error", "dialogue_completed");
                expectedNodeIndex = 0;
            } else {
                expectedNodeIndex = progress.nextNodeIndex();
            }
        } else if (encounterCount >= dialogueTree.size()) {
            if (unique) return Map.of("error", "dialogue_completed");
            expectedNodeIndex = 0;
        } else {
            expectedNodeIndex = (int) encounterCount;
        }
        if (nodeIndex != expectedNodeIndex)
            return Map.of("error", "invalid_node_index");

        Map<String, Object> node = dialogueTree.get(nodeIndex);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) node.get("choices");
        if (choices == null || choices.isEmpty())
            return Map.of("error", "invalid_node");

        if (choiceIndex < 0 || choiceIndex >= choices.size())
            return Map.of("error", "invalid_choice_index");

        Map<String, Object> choice = choices.get(choiceIndex);
        Integer nextNodeIndex = toNullableInt(choice.get("leadToIndex"));
        if (choice.get("leadToIndex") != null
                && (nextNodeIndex == null || nextNodeIndex < 0 || nextNodeIndex >= dialogueTree.size()))
            return Map.of("error", "invalid_next_node_index");

        // Record encounter
        PlayerNpcEncounter enc = new PlayerNpcEncounter();
        enc.setPlayerId(playerId);
        enc.setGhostKey(ghostKey);
        String choiceText = String.valueOf(choice.getOrDefault("text", ""));
        enc.setChoiceMade(String.format("[NARRATIVE node=%d choice=%d next=%d] %s",
                nodeIndex, choiceIndex, nextNodeIndex != null ? nextNodeIndex : -1, choiceText));
        enc.setOutcome(String.valueOf(choice.getOrDefault("response", "")));
        enc.setCreatedAt(LocalDateTime.now());
        if (encounterMapper.insert(enc) <= 0)
            throw new IllegalStateException("记录残影对话进度失败");

        // Apply effects
        Map<String, Object> effectsApplied = Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> effects = (Map<String, Object>) choice.get("effects");
        if (effects != null && !effects.isEmpty()) {
            effectsApplied = applyNpcEffects(playerId, effects);
        }

        Map<String, Object> logMetadata = new LinkedHashMap<>();
        logMetadata.put("ghostKey", ghostKey);
        logMetadata.put("nodeIndex", nodeIndex);
        logMetadata.put("choiceIndex", choiceIndex);
        logMetadata.put("nextNodeIndex", nextNodeIndex);
        logMetadata.put("effectsApplied", effectsApplied);
        playerLogService.addLog(playerId, "story", buildEncounterLogMessage(
                ghost.getName(), choiceText, String.valueOf(choice.getOrDefault("response", "")), effectsApplied),
                logMetadata);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ghostKey", ghostKey);
        result.put("ghostName", ghost.getName());
        result.put("nodeIndex", nodeIndex);
        result.put("choiceIndex", choiceIndex);
        result.put("nodeText", node.getOrDefault("text", ""));
        result.put("playerChoice", choice.getOrDefault("text", ""));
        result.put("npcResponse", choice.getOrDefault("response", ""));
        result.put("effects", effects != null ? effects : Map.of());
        result.put("effectsApplied", effectsApplied);
        result.put("nextNodeIndex", nextNodeIndex);
        if (requestKey != null) {
            actionReceiptService.record(playerId, "narrative.ghost.choice", requestKey, requestPayload, result);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getPlayerEncounters(Long playerId, int limit) {
        QueryWrapper<PlayerNpcEncounter> qw = new QueryWrapper<>();
        qw.eq("player_id", playerId).orderByDesc("created_at").last("LIMIT " + limit);
        List<PlayerNpcEncounter> list = encounterMapper.selectList(qw);

        List<Map<String, Object>> result = new ArrayList<>();
        for (PlayerNpcEncounter e : list) {
            QueryWrapper<NpcGhost> gq = new QueryWrapper<>();
            gq.eq("ghost_key", e.getGhostKey());
            NpcGhost ghost = npcGhostMapper.selectOne(gq);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", e.getId());
            entry.put("ghostKey", e.getGhostKey());
            entry.put("ghostName", ghost != null ? ghost.getName() : e.getGhostKey());
            entry.put("choiceMade", displayChoiceMade(e.getChoiceMade()));
            entry.put("outcome", e.getOutcome());
            entry.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
            result.add(entry);
        }
        return result;
    }

    // ─── NPC effects ───

    private Map<String, Object> applyNpcEffects(Long playerId, Map<String, Object> effects) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> applied = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // Stat changes
        @SuppressWarnings("unchecked")
        Map<String, Object> statChange = (Map<String, Object>) effects.get("statChange");
        if (statChange != null) {
            try {
                Player player = playerMapper.selectById(playerId);
                if (player != null) {
                    Map<String, Object> stats = parseJsonMap(player.getStatsJson());
                    Map<String, Object> updatedStats = new LinkedHashMap<>(stats);
                    List<Map<String, Object>> statChanges = new ArrayList<>();

                    Map<String, String> statMap = Map.ofEntries(
                            Map.entry("strength", "strength"), Map.entry("hp", "maxHp"),
                            Map.entry("maxHp", "maxHp"), Map.entry("defense", "defense"),
                            Map.entry("speed", "speed"), Map.entry("luck", "luck"),
                            Map.entry("insight", "wisdom"), Map.entry("wisdom", "wisdom"),
                            Map.entry("sanity", "sanity"), Map.entry("heat", "channelHeat"),
                            Map.entry("intelligence", "intelligence"), Map.entry("charisma", "charisma"));

                    for (Map.Entry<String, Object> entry : statChange.entrySet()) {
                        String mappedKey = statMap.getOrDefault(entry.getKey(), entry.getKey());
                        int delta = toInt(entry.getValue(), 0);
                        int oldVal = toInt(updatedStats.getOrDefault(mappedKey, 0), 0);
                        int newVal = ResourceBalance.applySignedDelta("剧情属性「" + mappedKey + "」", oldVal, delta);
                        updatedStats.put(mappedKey, newVal);
                        statChanges.add(Map.of("stat", mappedKey, "from", oldVal, "to", newVal, "delta", delta));
                    }

                    // Special HP handling
                    if (statChange.containsKey("hp")) {
                        int hpDelta = toInt(statChange.get("hp"), 0);
                        int currentHp = toInt(updatedStats.getOrDefault("hp", updatedStats.getOrDefault("maxHp", 100)), 100);
                        long hpCandidate = (long) currentHp + hpDelta;
                        if (hpCandidate > Integer.MAX_VALUE) {
                            throw new BusinessException(ErrorCode.BAD_REQUEST, "剧情生命值超出可保存范围，效果未应用");
                        }
                        int newHp = (int) Math.max(0L, hpCandidate);
                        updatedStats.put("hp", newHp);
                        statChanges.add(Map.of("stat", "hp", "from", currentHp, "to", newHp, "delta", hpDelta));
                    }

                    player.setStatsJson(toJson(updatedStats));
                    if (playerMapper.updateById(player) <= 0)
                        throw new IllegalStateException("保存剧情属性变化失败");
                    applied.addAll(statChanges);
                }
            } catch (Exception e) {
                errors.add("statChange: " + e.getMessage());
            }
        }

        // Add items
        Object addItem = effects.get("addItem");
        if (addItem != null) {
            List<String> items = (addItem instanceof List)
                    ? ((List<?>) addItem).stream().map(String::valueOf).toList()
                    : List.of(String.valueOf(addItem));
            for (String itemKey : items) {
                QueryWrapper<Item> itemQuery = new QueryWrapper<>();
                itemQuery.eq("item_key", itemKey).eq("enabled", 1);
                Item item = itemMapper.selectOne(itemQuery);
                String itemName = item != null ? item.getName() : itemKey;
                ItemGrantResult grant = inventoryService.addItemWithResult(playerId, itemKey, 1);
                if (grant.quantityGranted() > 0) {
                    applied.add(Map.of("type", "addItem", "itemKey", itemKey, "itemName", itemName,
                            "quantity", grant.quantityGranted(), "totalQuantity", grant.totalQuantity()));
                } else {
                    errors.add("addItem: 未能获得 " + itemName + "（物品未配置或堆叠已满）");
                }
            }
        }

        // Unlock title
        Object unlockTitle = effects.get("unlockTitle");
        if (unlockTitle != null) {
            try {
                String titleKey = String.valueOf(unlockTitle);
                Player player = playerMapper.selectById(playerId);
                List<String> titles = parseJsonList(player.getTitlesJson()).stream()
                        .map(String::valueOf).collect(java.util.stream.Collectors.toList());
                if (!titles.contains(titleKey)) {
                    titles.add(titleKey);
                    player.setTitlesJson(toJson(titles));
                    playerMapper.updateById(player);
                    applied.add(Map.of("type", "unlockTitle", "titleKey", titleKey));
                }
            } catch (Exception e) {
                errors.add("unlockTitle: " + e.getMessage());
            }
        }

        // Story flags
        Object addStoryFlag = effects.get("addStoryFlag");
        if (addStoryFlag != null) {
            try {
                List<String> flags = (addStoryFlag instanceof List)
                        ? ((List<?>) addStoryFlag).stream().map(String::valueOf).toList()
                        : List.of(String.valueOf(addStoryFlag));
                Player player = playerMapper.selectById(playerId);
                Map<String, Object> storyFlags = parseJsonMap(player.getStoryFlagsJson());
                boolean changed = false;
                List<String> addedFlags = new ArrayList<>();
                for (String flag : flags) {
                    if (!storyFlags.containsKey(flag)) {
                        storyFlags.put(flag, true);
                        addedFlags.add(flag);
                        changed = true;
                    }
                }
                if (changed) {
                    player.setStoryFlagsJson(toJson(storyFlags));
                    if (playerMapper.updateById(player) <= 0)
                        throw new IllegalStateException("保存残影剧情旗标失败");
                    for (String flag : addedFlags) {
                        applied.add(Map.of("type", "addStoryFlag", "flag", flag));
                    }
                }
            } catch (Exception e) {
                errors.add("addStoryFlag: " + e.getMessage());
            }
        }

        // Add skills
        Object addSkill = effects.get("addSkill");
        if (addSkill != null) {
            try {
                List<String> skills = (addSkill instanceof List)
                        ? ((List<?>) addSkill).stream().map(String::valueOf).toList()
                        : List.of(String.valueOf(addSkill));
                for (String skillKey : skills) {
                    QueryWrapper<PlayerSkill> sq = new QueryWrapper<>();
                    sq.eq("player_id", playerId).eq("skill_key", skillKey);
                    if (skillMapper.selectCount(sq) == 0) {
                        PlayerSkill skill = new PlayerSkill();
                        skill.setPlayerId(playerId);
                        skill.setSkillKey(skillKey);
                        skillMapper.insert(skill);
                        applied.add(Map.of("type", "addSkill", "skill", skillKey));
                    }
                }
            } catch (Exception e) {
                errors.add("addSkill: " + e.getMessage());
            }
        }

        result.put("applied", applied);
        result.put("errors", errors);
        return result;
    }

    // ─── utils ───

    private int toInt(Object val, int def) {
        if (val instanceof Number) return ((Number) val).intValue();
        return def;
    }

    private double toDouble(Object val, double def) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        return def;
    }

    private PlayerNpcEncounter findLatestEncounter(Long playerId, String ghostKey) {
        QueryWrapper<PlayerNpcEncounter> query = new QueryWrapper<>();
        query.eq("player_id", playerId).eq("ghost_key", ghostKey).orderByDesc("id").last("LIMIT 1");
        return encounterMapper.selectOne(query);
    }

    private Map<String, Object> ghostEncounterResult(NpcGhost ghost, List<Map<String, Object>> dialogueTree,
                                                       long encounterCount, int nodeIndex) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ghostKey", ghost.getGhostKey());
        result.put("name", ghost.getName());
        result.put("description", ghost.getDescription());
        result.put("encounterCount", encounterCount);
        result.put("currentNodeIndex", nodeIndex);
        result.put("dialogueTree", dialogueTree);
        return result;
    }

    private EncounterProgress parseProgress(PlayerNpcEncounter encounter) {
        if (encounter == null || encounter.getChoiceMade() == null) return null;
        Matcher matcher = ENCOUNTER_PROGRESS_PATTERN.matcher(encounter.getChoiceMade());
        if (!matcher.find()) return null;
        try {
            return new EncounterProgress(Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer toNullableInt(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text) {
            try { return Integer.parseInt(text); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private String displayChoiceMade(String choiceMade) {
        if (choiceMade == null) return "";
        Matcher progressMatcher = ENCOUNTER_PROGRESS_PATTERN.matcher(choiceMade);
        if (progressMatcher.find()) return choiceMade.substring(progressMatcher.end()).trim();
        return choiceMade.replaceFirst("^\\[Node \\d+]\\s*", "");
    }

    private String buildEncounterLogMessage(String ghostName, String choiceText, String response,
                                            Map<String, Object> effectsApplied) {
        StringBuilder message = new StringBuilder("与").append(ghostName).append("交谈：").append(choiceText);
        if (response != null && !response.isBlank()) message.append("。 ").append(response);

        Object rawApplied = effectsApplied.get("applied");
        if (rawApplied instanceof List<?> applied) {
            for (Object value : applied) {
                if (!(value instanceof Map<?, ?> effect)) continue;
                if ("addItem".equals(effect.get("type"))) {
                    message.append(" 获得物品：").append(valueOrFallback(effect, "itemName", "itemKey"));
                    Object quantity = effect.get("quantity");
                    if (quantity instanceof Number number && number.intValue() > 1)
                        message.append(" ×").append(number.intValue());
                } else if ("addEquipment".equals(effect.get("type"))) {
                    message.append(" 获得装备：").append(valueOrFallback(effect, "equipmentName", "equipmentKey"));
                } else if ("addSkill".equals(effect.get("type"))) {
                    message.append(" 习得技能：").append(valueOrFallback(effect, "skillName", "skill"));
                } else if ("unlockTitle".equals(effect.get("type"))) {
                    message.append(" 解锁称号：").append(valueOrFallback(effect, "titleName", "titleKey"));
                } else if (effect.get("stat") != null && effect.get("delta") instanceof Number delta) {
                    message.append(" 属性变化：").append(effect.get("stat"));
                    if (delta.intValue() > 0) message.append('+');
                    message.append(delta.intValue());
                }
            }
        }

        Object rawErrors = effectsApplied.get("errors");
        if (rawErrors instanceof List<?> errors) {
            for (Object error : errors) message.append(" 效果提示：").append(error);
        }
        return message.toString();
    }

    private Object valueOrFallback(Map<?, ?> values, String key, String fallbackKey) {
        Object value = values.get(key);
        return value != null ? value : values.get(fallbackKey);
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return "{}"; }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) return new LinkedHashMap<>();
        try { return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {}); }
        catch (Exception e) { return new LinkedHashMap<>(); }
    }

    private List<Map<String, Object>> parseJsonList(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {}); }
        catch (Exception e) { return List.of(); }
    }

    private <T> List<T> parseJsonList(String json, Class<T> elementType) {
        if (json == null || json.isBlank() || "null".equals(json)) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<List<T>>() {}); }
        catch (Exception e) { return List.of(); }
    }
}
