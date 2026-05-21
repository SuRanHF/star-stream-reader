package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.entity.Ending;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.EndingMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.EndingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class EndingServiceImpl implements EndingService {

    private static final Logger log = LoggerFactory.getLogger(EndingServiceImpl.class);

    private final EndingMapper endingMapper;
    private final PlayerMapper playerMapper;
    private final ObjectMapper objectMapper;

    public EndingServiceImpl(EndingMapper endingMapper, PlayerMapper playerMapper, ObjectMapper objectMapper) {
        this.endingMapper = endingMapper;
        this.playerMapper = playerMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Map<String, Object>> checkEndings(Long playerId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null) return List.of();

        List<Ending> allEndings = endingMapper.selectList(
                new QueryWrapper<Ending>().orderByDesc("priority"));

        List<String> playerTitles = parseJsonList(player.getTitlesJson());
        Map<String, Object> storyFlags = parseJsonMap(player.getStoryFlagsJson());
        Map<String, Object> permFlags = parseJsonMap(player.getPermanentFlagsJson());
        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        Map<String, Object> relationships = parseJsonMap(player.getRelationshipsJson());
        List<?> sponsors = parseJsonList(player.getSponsorsJson());

        // Collect blocked endings from titles
        Set<String> blockedEndings = new HashSet<>();
        // Title blocking logic can be added if title definitions become available

        List<Map<String, Object>> matched = new ArrayList<>();
        for (Ending ending : allEndings) {
            if (blockedEndings.contains(ending.getEndingKey())) continue;

            Map<String, Object> conditions = parseJsonMap(ending.getConditionsJson());
            if (evaluateConditions(conditions, playerTitles, storyFlags, permFlags, stats, sponsors)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("endingKey", ending.getEndingKey());
                item.put("name", ending.getName());
                item.put("description", ending.getDescription());
                item.put("priority", ending.getPriority());
                item.put("isHidden", ending.getIsHidden() != null && ending.getIsHidden() == 1);
                matched.add(item);
            }
        }
        return matched;
    }

    @SuppressWarnings("unchecked")
    private boolean evaluateConditions(Map<String, Object> conditions,
                                        List<String> playerTitles,
                                        Map<String, Object> storyFlags,
                                        Map<String, Object> permFlags,
                                        Map<String, Object> stats,
                                        List<?> sponsors) {
        // flags
        Map<String, Object> flags = (Map<String, Object>) conditions.get("flags");
        if (flags != null) {
            for (Map.Entry<String, Object> e : flags.entrySet()) {
                Object expected = e.getValue();
                Object actual = storyFlags.getOrDefault(e.getKey(), permFlags.getOrDefault(e.getKey(), false));
                if (!Objects.equals(expected, actual)) return false;
            }
        }

        // permanent_flags with thresholds
        Map<String, Object> permConds = (Map<String, Object>) conditions.get("permanent_flags");
        if (permConds != null) {
            for (Map.Entry<String, Object> e : permConds.entrySet()) {
                if (e.getValue() instanceof Boolean) {
                    boolean expected = (Boolean) e.getValue();
                    boolean actual = Boolean.TRUE.equals(permFlags.get(e.getKey()));
                    if (expected != actual) return false;
                } else if (e.getValue() instanceof Map) {
                    Map<String, Object> threshold = (Map<String, Object>) e.getValue();
                    int val = toInt(permFlags.get(e.getKey()), 0);
                    if (threshold.containsKey("min") && val < toInt(threshold.get("min"), 0)) return false;
                    if (threshold.containsKey("max") && val > toInt(threshold.get("max"), 0)) return false;
                }
            }
        }

        // stats with thresholds
        Map<String, Object> statConds = (Map<String, Object>) conditions.get("stats");
        if (statConds != null) {
            for (Map.Entry<String, Object> e : statConds.entrySet()) {
                Map<String, Object> threshold = (Map<String, Object>) e.getValue();
                int val = toInt(stats.get(e.getKey()), 0);
                if (threshold.containsKey("min") && val < toInt(threshold.get("min"), 0)) return false;
                if (threshold.containsKey("max") && val > toInt(threshold.get("max"), 0)) return false;
            }
        }

        // required titles
        List<String> requiredTitles = (List<String>) conditions.get("titles");
        if (requiredTitles != null) {
            for (String t : requiredTitles) {
                if (!playerTitles.contains(t)) return false;
            }
        }

        // sponsors_count
        Object sponsorsCount = conditions.get("sponsors_count");
        if (sponsorsCount != null) {
            int min = (sponsorsCount instanceof Map)
                    ? toInt(((Map<?, ?>) sponsorsCount).get("min"), 0)
                    : toInt(sponsorsCount, 0);
            if (sponsors.size() < min) return false;
        }

        return true;
    }

    private int toInt(Object val, int def) {
        if (val instanceof Number) return ((Number) val).intValue();
        return def;
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) return new LinkedHashMap<>();
        try { return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {}); }
        catch (Exception e) { return new LinkedHashMap<>(); }
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<List<String>>() {}); }
        catch (Exception e) { return List.of(); }
    }

    private List<?> parseJsonListRaw(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<List<?>>() {}); }
        catch (Exception e) { return List.of(); }
    }
}
