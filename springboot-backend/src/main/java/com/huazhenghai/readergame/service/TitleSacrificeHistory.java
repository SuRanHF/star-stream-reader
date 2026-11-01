package com.huazhenghai.readergame.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keeps a permanent tombstone so later unlock evaluation cannot restore a sacrificed title. */
public final class TitleSacrificeHistory {
    private static final String KEY = "sacrificedTitleKeys";
    private static final String RECORDS = "titleSacrifices";
    public static final long COOLDOWN_MILLIS = 7L * 24 * 60 * 60 * 1000;

    private TitleSacrificeHistory() {}

    public static boolean contains(Player player, String titleKey, ObjectMapper mapper) {
        Object value = progress(player, mapper).get(KEY);
        return value instanceof List<?> list && list.contains(titleKey);
    }

    public static void add(Player player, String titleKey, ObjectMapper mapper) {
        Map<String, Object> progress = progress(player, mapper);
        List<String> keys = new ArrayList<>();
        Object previous = progress.get(KEY);
        if (previous instanceof List<?> list) {
            for (Object value : list) keys.add(String.valueOf(value));
        }
        if (!keys.contains(titleKey)) keys.add(titleKey);
        progress.put(KEY, keys);
        save(player, progress, mapper);
    }

    public static void record(Player player, String titleKey, int explorationCount,
                              long now, ObjectMapper mapper) {
        add(player, titleKey, mapper);
        Map<String, Object> progress = progress(player, mapper);
        Map<String, Object> records = records(progress);
        records.put(titleKey, new LinkedHashMap<>(Map.of(
                "sacrificedAt", now,
                "explorationSnapshot", explorationCount,
                "reacquireExploreCount", 0,
                "reacquired", false)));
        save(player, progress, mapper);
    }

    public static boolean isReacquired(Player player, String titleKey, ObjectMapper mapper) {
        return Boolean.TRUE.equals(details(player, titleKey, mapper).get("reacquired"));
    }

    public static Map<String, Object> details(Player player, String titleKey, ObjectMapper mapper) {
        Object value = records(progress(player, mapper)).get(titleKey);
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    /** Initialize legacy tombstones and the counter baseline at the first action after cooldown. */
    public static boolean prepare(Player player, int explorationCount, long now, ObjectMapper mapper) {
        Map<String, Object> progress = progress(player, mapper);
        Object oldKeys = progress.get(KEY);
        if (!(oldKeys instanceof List<?> keys) || keys.isEmpty()) return false;
        Map<String, Object> records = records(progress);
        boolean changed = false;
        for (Object rawKey : keys) {
            String titleKey = String.valueOf(rawKey);
            Object old = records.get(titleKey);
            Map<String, Object> record = old instanceof Map<?, ?> map
                    ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
            if (record.isEmpty()) {
                record.put("sacrificedAt", now);
                record.put("explorationSnapshot", explorationCount);
                record.put("reacquireExploreCount", 0);
                record.put("reacquired", false);
                changed = true;
            }
            long sacrificedAt = record.get("sacrificedAt") instanceof Number n ? n.longValue() : now;
            if (!Boolean.TRUE.equals(record.get("reacquired"))
                    && !record.containsKey("progressStartCount")
                    && now - sacrificedAt >= COOLDOWN_MILLIS) {
                record.put("progressStartCount", explorationCount);
                changed = true;
            }
            records.put(titleKey, record);
        }
        if (changed) save(player, progress, mapper);
        return changed;
    }

    /** Counts only completed explorations after cooldown; unrelated progress edits cannot advance reacquisition. */
    public static void countQualifiedExploration(Player player, long now, ObjectMapper mapper) {
        Map<String, Object> progress = progress(player, mapper);
        Object oldKeys = progress.get(KEY);
        if (!(oldKeys instanceof List<?> keys) || keys.isEmpty()) return;
        Map<String, Object> records = records(progress);
        boolean changed = false;
        for (Object rawKey : keys) {
            String titleKey = String.valueOf(rawKey);
            Object old = records.get(titleKey);
            if (!(old instanceof Map<?, ?> map)) continue;
            Map<String, Object> record = new LinkedHashMap<>((Map<String, Object>) map);
            long sacrificedAt = record.get("sacrificedAt") instanceof Number n ? n.longValue() : now;
            if (Boolean.TRUE.equals(record.get("reacquired"))
                    || now - sacrificedAt < COOLDOWN_MILLIS
                    || !record.containsKey("progressStartCount")) continue;
            int count = record.get("reacquireExploreCount") instanceof Number n ? n.intValue() : 0;
            record.put("reacquireExploreCount", Math.addExact(count, 1));
            records.put(titleKey, record);
            changed = true;
        }
        if (changed) save(player, progress, mapper);
    }

    public static void markReacquired(Player player, String titleKey, ObjectMapper mapper) {
        Map<String, Object> progress = progress(player, mapper);
        Map<String, Object> records = records(progress);
        Object old = records.get(titleKey);
        if (!(old instanceof Map<?, ?> map)) throw new IllegalStateException("缺少称号献祭记录");
        Map<String, Object> record = new LinkedHashMap<>((Map<String, Object>) map);
        if (Boolean.TRUE.equals(record.get("reacquired"))) throw new IllegalStateException("称号已经重获");
        record.put("reacquired", true);
        records.put(titleKey, record);
        save(player, progress, mapper);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> records(Map<String, Object> progress) {
        Object value = progress.get(RECORDS);
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        Map<String, Object> records = new LinkedHashMap<>();
        progress.put(RECORDS, records);
        return records;
    }

    private static void save(Player player, Map<String, Object> progress, ObjectMapper mapper) {
        try {
            player.setTitleProgressJson(mapper.writeValueAsString(progress));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "保存称号献祭记录失败");
        }
    }

    private static Map<String, Object> progress(Player player, ObjectMapper mapper) {
        String json = player.getTitleProgressJson();
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取称号进度失败");
        }
    }
}
