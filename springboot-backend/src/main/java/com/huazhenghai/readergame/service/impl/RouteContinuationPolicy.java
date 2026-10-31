package com.huazhenghai.readergame.service.impl;

import com.huazhenghai.readergame.entity.ExplorationEvent;
import com.huazhenghai.readergame.entity.Location;
import com.huazhenghai.readergame.service.LocationUnlockPolicy;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** Server-owned end-of-current-route marker, persisted in stage_progress_json. */
public final class RouteContinuationPolicy {
    private RouteContinuationPolicy() {
    }

    public static boolean isPending(Map<String, Object> progress) {
        return Boolean.TRUE.equals(progress.get("routePending"));
    }

    static void markPending(Map<String, Object> progress, String eventKey) {
        progress.put("routePending", true);
        progress.put("routePendingEventKey", eventKey);
    }

    static boolean allowsStory(Map<String, Object> progress, Map<String, Object> conditions) {
        if (!isPending(progress)) return true;
        Object tip = progress.get("routePendingEventKey");
        if (!(tip instanceof String key) || key.isBlank()) return false;
        Object required = conditions.get("required_events");
        if (required instanceof List<?> events && events.contains(key)) return true;
        Object gates = conditions.get("choice_unlockers");
        if (gates instanceof List<?> unlockers) {
            return unlockers.stream().anyMatch(gate -> gate instanceof Map<?, ?> map
                    && key.equals(map.get("event_key")));
        }
        return false;
    }

    static void clearIfContinued(Map<String, Object> progress, Map<String, Object> conditions) {
        if (isPending(progress) && allowsStory(progress, conditions)) {
            progress.remove("routePending");
            progress.remove("routePendingEventKey");
        }
    }

    /** Availability follows the same eligibility, location and lower-story rules as exploration. */
    public static boolean hasAvailableContinuation(Map<String, Object> progress,
            List<ExplorationEvent> stories, List<Location> locations, Map<String, Object> stats,
            Map<String, Object> storyFlags, String chapter, List<String> triggered,
            List<?> decisions, Function<ExplorationEvent, Map<String, Object>> conditionsFor,
            Function<Location, Map<String, Object>> locationConditionsFor) {
        if (!isPending(progress) || stories == null || locations == null) return false;
        Map<String, Location> byKey = new LinkedHashMap<>();
        for (Location location : locations) byKey.put(location.getLocationKey(), location);
        Map<String, Map<String, Object>> conditionsByKey = new LinkedHashMap<>();
        for (ExplorationEvent event : stories) {
            if (event != null && "story".equals(event.getEventType())
                    && Integer.valueOf(1).equals(event.getEnabled())) {
                conditionsByKey.put(event.getEventKey(), conditionsFor.apply(event));
            }
        }
        Function<String, Map<String, Object>> ancestors = conditionsByKey::get;
        Object pendingValue = progress.get("pendingStorySnapshots");
        Map<?, ?> pending = pendingValue instanceof Map<?, ?> map ? map : Map.of();
        for (ExplorationEvent event : stories) {
            if (event == null || !conditionsByKey.containsKey(event.getEventKey())) continue;
            Map<String, Object> conditions = conditionsByKey.get(event.getEventKey());
            if (!allowsStory(progress, conditions)
                    || triggered.contains(event.getEventKey()) && Integer.valueOf(0).equals(event.getRepeatable())
                    || pending.containsKey(event.getEventKey())
                    || !ExploreStoryEligibility.checkRequiredConditions(stats, progress, decisions, conditions)) continue;
            Location location = byKey.get(event.getLocationKey());
            if (location == null || !LocationUnlockPolicy.canAccessLocation(
                    locationConditionsFor.apply(location), stats, storyFlags, chapter, triggered)) continue;
            boolean lowerBlocked = false;
            if (location.getMinLevel() != null) {
                for (ExplorationEvent lower : stories) {
                    if (lower == null || !conditionsByKey.containsKey(lower.getEventKey())) continue;
                    Location lowerLocation = byKey.get(lower.getLocationKey());
                    if (lowerLocation != null && lowerLocation.getMinLevel() != null
                            && lowerLocation.getMinLevel() < location.getMinLevel()
                            && ExploreStoryEligibility.isUnfinishedStory(lower.getEventKey(),
                                    conditionsByKey.get(lower.getEventKey()), triggered, decisions,
                                    ancestors, progress)) {
                        lowerBlocked = true;
                        break;
                    }
                }
            }
            if (!lowerBlocked) return true;
        }
        return false;
    }

    public static Map<String, Object> response(Map<String, Object> progress, boolean hasContinuation) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", isPending(progress) && !hasContinuation ? "to_be_continued" : "active");
        result.put("event_key", isPending(progress) ? progress.get("routePendingEventKey") : null);
        return result;
    }
}
