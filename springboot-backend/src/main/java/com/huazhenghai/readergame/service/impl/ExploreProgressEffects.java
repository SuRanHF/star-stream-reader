package com.huazhenghai.readergame.service.impl;

import com.huazhenghai.readergame.entity.ExplorationEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 把一次探索结果写入玩家的阶段进度，并返回本次新增的进度标记。 */
final class ExploreProgressEffects {

    private ExploreProgressEffects() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> apply(
            Map<String, Object> stageProgress,
            ExplorationEvent event,
            Map<String, Object> effects) {
        Map<String, Object> applied = new LinkedHashMap<>();

        // storyPity：story 类型清零，其他类型加一。
        if ("story".equals(event.getEventType())) {
            stageProgress.put("storyPity", 0);
            applied.put("storyPityReset", true);
        } else {
            int current = toInt(stageProgress.get("storyPity"), 0);
            stageProgress.put("storyPity", current + 1);
            applied.put("storyPity", current + 1);
        }

        if ("story".equals(event.getEventType())) {
            List<String> triggered = (List<String>) stageProgress.computeIfAbsent(
                    "storyEventsTriggered", key -> new ArrayList<>());
            if (!triggered.contains(event.getEventKey())) {
                triggered.add(event.getEventKey());
                applied.put("storyEventsTriggered", true);
            }
        } else {
            List<String> sideTriggered = (List<String>) stageProgress.computeIfAbsent(
                    "sideEventsTriggered", key -> new ArrayList<>());
            if (!sideTriggered.contains(event.getEventKey())) {
                sideTriggered.add(event.getEventKey());
                applied.put("sideEventsTriggered", true);
            }
        }

        // 隐藏事件按事件标识累计，避免重复遇到同一事件刷高章节进度。
        if ("hidden".equals(event.getEventType())) {
            List<String> hiddenTriggered = (List<String>) stageProgress.computeIfAbsent(
                    "hiddenEventsTriggered", key -> new ArrayList<>());
            if (!hiddenTriggered.contains(event.getEventKey())) {
                hiddenTriggered.add(event.getEventKey());
                applied.put("hiddenEventsTriggered", true);
            }
        }

        if (effects.containsKey("bossClueKey")) {
            String bossKey = (String) effects.get("bossClueKey");
            int add = toInt(effects.get("bossClueAdd"), 1);
            Map<String, Object> bossClues = (Map<String, Object>) stageProgress.computeIfAbsent(
                    "bossClues", key -> new LinkedHashMap<>());
            int current = toInt(bossClues.get(bossKey), 0);
            bossClues.put(bossKey, current + add);
            applied.put("bossClueKey", bossKey);
            applied.put("bossClueCount", current + add);
        }

        Map<String, Object> byLocation = (Map<String, Object>) stageProgress.computeIfAbsent(
                "explorationsByLocation", key -> new LinkedHashMap<>());
        String locationKey = event.getLocationKey() != null ? event.getLocationKey() : "unknown";
        int locationCount = toInt(byLocation.get(locationKey), 0);
        byLocation.put(locationKey, locationCount + 1);

        return applied;
    }

    private static int toInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return defaultValue;
    }
}
