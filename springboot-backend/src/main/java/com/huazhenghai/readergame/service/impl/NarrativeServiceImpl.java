package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.entity.*;
import com.huazhenghai.readergame.mapper.*;
import com.huazhenghai.readergame.service.NarrativeService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class NarrativeServiceImpl implements NarrativeService {

    private static final Logger log = LoggerFactory.getLogger(NarrativeServiceImpl.class);

    private final ItemMemoryMapper itemMemoryMapper;
    private final LocationEchoMapper locationEchoMapper;
    private final NpcGhostMapper npcGhostMapper;
    private final PlayerNpcEncounterMapper encounterMapper;
    private final PlayerMapper playerMapper;
    private final PlayerInventoryMapper inventoryMapper;
    private final PlayerSkillMapper skillMapper;
    private final ItemMapper itemMapper;
    private final ObjectMapper objectMapper;

    public NarrativeServiceImpl(ItemMemoryMapper itemMemoryMapper,
                                LocationEchoMapper locationEchoMapper,
                                NpcGhostMapper npcGhostMapper,
                                PlayerNpcEncounterMapper encounterMapper,
                                PlayerMapper playerMapper,
                                PlayerInventoryMapper inventoryMapper,
                                PlayerSkillMapper skillMapper,
                                ItemMapper itemMapper,
                                ObjectMapper objectMapper) {
        this.itemMemoryMapper = itemMemoryMapper;
        this.locationEchoMapper = locationEchoMapper;
        this.npcGhostMapper = npcGhostMapper;
        this.encounterMapper = encounterMapper;
        this.playerMapper = playerMapper;
        this.inventoryMapper = inventoryMapper;
        this.skillMapper = skillMapper;
        this.itemMapper = itemMapper;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void seedNpcGhostsIfEmpty() {
        try {
            Long count = npcGhostMapper.selectCount(null);
            if (count > 0) return;

            ClassPathResource resource = new ClassPathResource("data/seedNpcGhosts.json");
            try (InputStream is = resource.getInputStream()) {
                List<Map<String, Object>> ghosts = objectMapper.readValue(is,
                        new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> g : ghosts) {
                    NpcGhost ghost = new NpcGhost();
                    ghost.setGhostKey(String.valueOf(g.get("ghost_key")));
                    ghost.setName(String.valueOf(g.get("name")));
                    ghost.setDescription(String.valueOf(g.get("description")));
                    ghost.setDialogueTreeJson(objectMapper.writeValueAsString(g.get("dialogue_tree")));
                    ghost.setLocationKeysJson(objectMapper.writeValueAsString(g.get("location_keys")));
                    ghost.setEncounterWeight(toDouble(g.get("encounter_weight"), 0.05));
                    ghost.setIsUnique(toInt(g.get("is_unique"), 0));
                    npcGhostMapper.insert(ghost);
                }
                log.info("Seeded {} NPC ghosts from JSON", ghosts.size());
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

        for (NpcGhost g : ghosts) {
            double effectiveWeight = g.getEncounterWeight() * (1 + luck * 0.02);

            if (g.getIsUnique() != null && g.getIsUnique() == 1) {
                QueryWrapper<PlayerNpcEncounter> eq = new QueryWrapper<>();
                eq.eq("player_id", playerId).eq("ghost_key", g.getGhostKey());
                long prevCount = encounterMapper.selectCount(eq);
                if (prevCount > 0) effectiveWeight *= 0.3;
            }

            if (Math.random() < effectiveWeight) {
                List<Map<String, Object>> dialogueTree = parseJsonList(g.getDialogueTreeJson());

                QueryWrapper<PlayerNpcEncounter> eq = new QueryWrapper<>();
                eq.eq("player_id", playerId).eq("ghost_key", g.getGhostKey());
                long encounterCount = encounterMapper.selectCount(eq);

                int startNodeIndex = (int) Math.min(encounterCount, dialogueTree.size() - 1);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ghostKey", g.getGhostKey());
                result.put("name", g.getName());
                result.put("description", g.getDescription());
                result.put("encounterCount", encounterCount);
                result.put("currentNodeIndex", startNodeIndex);
                result.put("dialogueTree", dialogueTree);
                return result;
            }
        }

        return null;
    }

    @Override
    @Transactional
    public Map<String, Object> processNpcGhostEncounter(Long playerId, String ghostKey, int nodeIndex, int choiceIndex) {
        QueryWrapper<NpcGhost> qw = new QueryWrapper<>();
        qw.eq("ghost_key", ghostKey);
        NpcGhost ghost = npcGhostMapper.selectOne(qw);
        if (ghost == null) return Map.of("error", "ghost_not_found");

        List<Map<String, Object>> dialogueTree = parseJsonList(ghost.getDialogueTreeJson());

        if (nodeIndex < 0 || nodeIndex >= dialogueTree.size())
            return Map.of("error", "invalid_node_index");

        Map<String, Object> node = dialogueTree.get(nodeIndex);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) node.get("choices");
        if (choices == null || choices.isEmpty())
            return Map.of("error", "invalid_node");

        if (choiceIndex < 0 || choiceIndex >= choices.size())
            return Map.of("error", "invalid_choice_index");

        Map<String, Object> choice = choices.get(choiceIndex);

        // Record encounter
        PlayerNpcEncounter enc = new PlayerNpcEncounter();
        enc.setPlayerId(playerId);
        enc.setGhostKey(ghostKey);
        enc.setChoiceMade("[Node " + nodeIndex + "] " + choice.getOrDefault("text", ""));
        enc.setOutcome(String.valueOf(choice.getOrDefault("response", "")));
        enc.setCreatedAt(LocalDateTime.now());
        encounterMapper.insert(enc);

        // Apply effects
        Map<String, Object> effectsApplied = Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> effects = (Map<String, Object>) choice.get("effects");
        if (effects != null && !effects.isEmpty()) {
            effectsApplied = applyNpcEffects(playerId, effects);
        }

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
        result.put("nextNodeIndex", choice.get("leadToIndex"));
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
            entry.put("choiceMade", e.getChoiceMade());
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
                        int oldVal = toInt(stats.getOrDefault(mappedKey, 0), 0);
                        stats.put(mappedKey, oldVal + delta);
                        applied.add(Map.of("stat", mappedKey, "from", oldVal, "to", oldVal + delta, "delta", delta));
                    }

                    // Special HP handling
                    if (statChange.containsKey("hp")) {
                        int hpDelta = toInt(statChange.get("hp"), 0);
                        int currentHp = toInt(stats.getOrDefault("hp", stats.getOrDefault("maxHp", 100)), 100);
                        stats.put("hp", Math.max(0, currentHp + hpDelta));
                        applied.add(Map.of("stat", "hp", "from", currentHp, "to", stats.get("hp"), "delta", hpDelta));
                    }

                    player.setStatsJson(toJson(stats));
                    playerMapper.updateById(player);
                }
            } catch (Exception e) {
                errors.add("statChange: " + e.getMessage());
            }
        }

        // Add items
        Object addItem = effects.get("addItem");
        if (addItem != null) {
            try {
                List<String> items = (addItem instanceof List)
                        ? ((List<?>) addItem).stream().map(String::valueOf).toList()
                        : List.of(String.valueOf(addItem));
                for (String itemKey : items) {
                    QueryWrapper<PlayerInventory> iq = new QueryWrapper<>();
                    iq.eq("player_id", playerId).eq("item_key", itemKey);
                    PlayerInventory existing = inventoryMapper.selectOne(iq);
                    if (existing != null) {
                        existing.setQuantity(existing.getQuantity() + 1);
                        inventoryMapper.updateById(existing);
                    } else {
                        PlayerInventory inv = new PlayerInventory();
                        inv.setPlayerId(playerId);
                        inv.setItemKey(itemKey);
                        inv.setQuantity(1);
                        inventoryMapper.insert(inv);
                    }
                    applied.add(Map.of("type", "addItem", "itemKey", itemKey));
                }
            } catch (Exception e) {
                errors.add("addItem: " + e.getMessage());
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
                for (String flag : flags) {
                    if (!storyFlags.containsKey(flag)) {
                        storyFlags.put(flag, true);
                        applied.add(Map.of("type", "addStoryFlag", "flag", flag));
                    }
                }
                player.setStoryFlagsJson(toJson(storyFlags));
                playerMapper.updateById(player);
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
