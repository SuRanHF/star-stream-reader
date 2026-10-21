package com.huazhenghai.readergame.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Shared authoritative checks for both the location list and exploration entry point. */
public final class LocationUnlockPolicy {

    private LocationUnlockPolicy() {
    }

    public static boolean canAccessLocation(Map<String, Object> conditions,
                                            Map<String, Object> playerStats,
                                            Map<String, Object> storyFlags,
                                            String currentChapter,
                                            Collection<String> triggeredEvents) {
        if (conditions == null || conditions.isEmpty()) return true;
        Map<String, Object> stats = playerStats == null ? Map.of() : playerStats;
        Map<String, Object> flags = storyFlags == null ? Map.of() : storyFlags;

        if (conditions.containsKey("required_level")) {
            int required = integerValue(conditions.get("required_level"), 0);
            int level = integerValue(stats.get("level"), 1);
            if (level < required) return false;
        }

        if (conditions.containsKey("prerequisite_events")
                && !containsAll(conditions.get("prerequisite_events"), triggeredEvents)) {
            return false;
        }

        if (conditions.containsKey("prerequisite_events_any")
                && !containsAny(conditions.get("prerequisite_events_any"), triggeredEvents)) {
            return false;
        }

        if (conditions.containsKey("required_items")
                && !containsAll(conditions.get("required_items"), flags.get("held_items"))) {
            return false;
        }

        if (conditions.containsKey("chapter_min")) {
            int minimumChapter = integerValue(conditions.get("chapter_min"), 0);
            if (currentChapter == null) return minimumChapter <= 1;
            int chapterNumber = 0;
            try {
                chapterNumber = Integer.parseInt(currentChapter.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {
                // An unparseable current chapter does not satisfy a positive minimum.
            }
            if (chapterNumber < minimumChapter) return false;
        }

        if (conditions.containsKey("unlock_by_choice")) {
            Object requiredLocation = conditions.get("unlock_by_choice");
            if (!(requiredLocation instanceof String locationKey)
                    || !containsAll(List.of(locationKey), flags.get("unlocked_locations"))) {
                return false;
            }
        }

        return true;
    }

    private static boolean containsAll(Object requiredValue, Object availableValue) {
        if (!(requiredValue instanceof List<?> required)) return false;
        if (required.isEmpty()) return true;
        if (!(availableValue instanceof Collection<?> available)) return false;
        for (Object entry : required) {
            if (!(entry instanceof String) || !available.contains(entry)) return false;
        }
        return true;
    }

    private static boolean containsAny(Object requiredValue, Object availableValue) {
        if (!(requiredValue instanceof List<?> required) || required.isEmpty()) return false;
        if (!(availableValue instanceof Collection<?> available)) return false;
        for (Object entry : required) {
            if (entry instanceof String && available.contains(entry)) return true;
        }
        return false;
    }

    private static int integerValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }
}
