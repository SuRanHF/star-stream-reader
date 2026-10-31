package com.huazhenghai.readergame.service.impl;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** 按玩家已有进度判断个人剧情是否可进入或已永久关闭。 */
public final class ExploreStoryEligibility {

    private static final Map<String, Integer> AVATAR_RANK_ORDER = Map.of(
            "F", 1, "E", 2, "D", 3, "C", 4, "B", 5,
            "A", 6, "S", 7, "SS", 8, "SSS", 9);

    private ExploreStoryEligibility() {
    }

    /**
     * Returns true only when every valid alternate choice gate has already been decided against.
     * An unchosen prerequisite remains potentially reachable and must not be silently skipped.
     */
    static boolean choiceHistoryPermanentlyClosesEvent(
            Map<String, Object> conditions,
            List<?> decisionHistory) {
        return choiceHistoryPermanentlyClosesEvent(conditions, decisionHistory,
                Collections.emptyList(), key -> null);
    }

    /** An AND prerequisite closes the event if any ancestor is closed; OR choice gates close it
     * only when every alternative is closed. Unknown ancestors and cycles stay potentially open. */
    public static boolean choiceHistoryPermanentlyClosesEvent(
            Map<String, Object> conditions, List<?> decisionHistory,
            List<String> triggeredStories,
            Function<String, Map<String, Object>> ancestorConditions) {
        return permanentlyClosed(conditions, decisionHistory, triggeredStories,
                ancestorConditions, new HashSet<>());
    }

    public static boolean isUnfinishedStory(String eventKey, Map<String, Object> conditions,
            List<String> triggeredStories, List<?> decisionHistory,
            Function<String, Map<String, Object>> ancestorConditions, Map<String, Object> stageProgress) {
        return RouteContinuationPolicy.allowsStory(stageProgress, conditions)
                && !triggeredStories.contains(eventKey)
                && !choiceHistoryPermanentlyClosesEvent(conditions, decisionHistory,
                        triggeredStories, ancestorConditions);
    }

    private static boolean permanentlyClosed(Map<String, Object> conditions,
            List<?> decisionHistory, List<String> triggeredStories,
            Function<String, Map<String, Object>> ancestorConditions, Set<String> visiting) {
        Object requiredChoicesValue = conditions.get("required_choices");
        if (requiredChoicesValue instanceof List<?> requiredChoices) {
            for (Object value : requiredChoices) {
                if (!(value instanceof Map<?, ?> requiredChoice)
                        || !(requiredChoice.get("event_key") instanceof String source)
                        || !(requiredChoice.get("choice_indices") instanceof List<?> indices)) continue;
                boolean decided = false;
                boolean matched = false;
                for (Object decisionValue : decisionHistory) {
                    if (!(decisionValue instanceof Map<?, ?> decision)) continue;
                    Object decisionKey = decision.containsKey("event_key")
                            ? decision.get("event_key") : decision.get("eventKey");
                    if (!source.equals(decisionKey)) continue;
                    decided = true;
                    Object index = decision.containsKey("choice_index")
                            ? decision.get("choice_index") : decision.get("choiceIndex");
                    if (indices.stream().anyMatch(allowed -> toInt(allowed, -2) == toInt(index, -1))) {
                        matched = true;
                        break;
                    }
                }
                if (decided && !matched || !decided && ancestorClosed(source, decisionHistory,
                        triggeredStories, ancestorConditions, visiting)) return true;
            }
        }
        Object requiredValue = conditions.get("required_events");
        if (requiredValue instanceof List<?> requiredEvents) {
            for (Object required : requiredEvents) {
                if (!(required instanceof String key) || triggeredStories.contains(key)) continue;
                if (ancestorClosed(key, decisionHistory, triggeredStories, ancestorConditions, visiting)) {
                    return true;
                }
            }
        }
        if (!conditions.containsKey("choice_unlockers")) {
            return false;
        }
        Object unlockersValue = conditions.get("choice_unlockers");
        if (!(unlockersValue instanceof List<?> unlockers) || unlockers.isEmpty()) {
            return true;
        }

        for (Object unlockerValue : unlockers) {
            if (!(unlockerValue instanceof Map<?, ?> unlocker)) {
                continue;
            }
            Object requiredEventValue = unlocker.get("event_key");
            Object requiredIndicesValue = unlocker.get("choice_indices");
            if (!(requiredEventValue instanceof String requiredEventKey)
                    || !(requiredIndicesValue instanceof List<?> requiredIndices)
                    || requiredIndices.isEmpty()) {
                continue;
            }

            boolean sourceDecisionFound = false;
            for (Object decisionValue : decisionHistory) {
                if (!(decisionValue instanceof Map<?, ?> decision)) {
                    continue;
                }
                Object decisionEventKey = decision.containsKey("event_key")
                        ? decision.get("event_key") : decision.get("eventKey");
                if (!requiredEventKey.equals(decisionEventKey)) {
                    continue;
                }
                sourceDecisionFound = true;
                Object choiceIndexValue = decision.containsKey("choice_index")
                        ? decision.get("choice_index") : decision.get("choiceIndex");
                int chosenIndex = toInt(choiceIndexValue, -1);
                if (requiredIndices.stream().anyMatch(index -> toInt(index, -2) == chosenIndex)) {
                    return false;
                }
            }
            if (!sourceDecisionFound) {
                if (!ancestorClosed(requiredEventKey, decisionHistory, triggeredStories,
                        ancestorConditions, visiting)) return false;
            }
        }
        // No valid gate, or every gate was decided against, means the event cannot be reached.
        return true;
    }

    private static boolean ancestorClosed(String key, List<?> decisionHistory,
            List<String> triggeredStories,
            Function<String, Map<String, Object>> ancestorConditions, Set<String> visiting) {
        if (triggeredStories.contains(key) || !visiting.add(key)) return false;
        try {
            Map<String, Object> ancestor = ancestorConditions.apply(key);
            return ancestor != null && permanentlyClosed(ancestor, decisionHistory,
                    triggeredStories, ancestorConditions, visiting);
        } finally {
            visiting.remove(key);
        }
    }

    @SuppressWarnings("unchecked")
    static boolean checkRequiredConditions(
            Map<String, Object> stats,
            Map<String, Object> stageProgress,
            List<?> decisionHistory,
            Map<String, Object> conditions) {
        if (conditions.isEmpty()) {
            return true;
        }
        if (conditions.containsKey("required_level")) {
            int required = toInt(conditions.get("required_level"), 0);
            if (toInt(stats.get("level"), 1) < required) {
                return false;
            }
        }
        if (conditions.containsKey("required_events")) {
            List<String> requiredEvents = (List<String>) conditions.get("required_events");
            List<String> triggered = (List<String>) stageProgress.getOrDefault(
                    "storyEventsTriggered", Collections.emptyList());
            for (String requiredEvent : requiredEvents) {
                if (!triggered.contains(requiredEvent)) {
                    return false;
                }
            }
        }
        if (conditions.containsKey("required_side_events")) {
            Object requiredValue = conditions.get("required_side_events");
            Object triggeredValue = stageProgress.get("sideEventsTriggered");
            if (!(requiredValue instanceof List<?> requiredSideEvents) || requiredSideEvents.isEmpty()
                    || !(triggeredValue instanceof List<?> triggeredSideEvents)
                    || !triggeredSideEvents.containsAll(requiredSideEvents)) {
                return false;
            }
        }
        if (conditions.containsKey("required_rank")) {
            String required = String.valueOf(conditions.get("required_rank"));
            String rank = String.valueOf(stats.getOrDefault("avatarRank", "F"));
            Integer currentOrder = AVATAR_RANK_ORDER.get(rank.trim().toUpperCase(Locale.ROOT));
            Integer requiredOrder = AVATAR_RANK_ORDER.get(required.trim().toUpperCase(Locale.ROOT));
            if (currentOrder == null || requiredOrder == null || currentOrder < requiredOrder) {
                return false;
            }
        }
        if (conditions.containsKey("required_choices")) {
            Object requiredChoicesValue = conditions.get("required_choices");
            if (!(requiredChoicesValue instanceof List<?> requiredChoices) || requiredChoices.isEmpty()) {
                return false;
            }
            for (Object value : requiredChoices) {
                if (!(value instanceof Map<?, ?> requiredChoice)
                        || !(requiredChoice.get("event_key") instanceof String source)
                        || !(requiredChoice.get("choice_indices") instanceof List<?> indices)
                        || indices.isEmpty()) return false;
                boolean matched = false;
                for (Object decisionValue : decisionHistory) {
                    if (!(decisionValue instanceof Map<?, ?> decision)) continue;
                    Object decisionKey = decision.containsKey("event_key")
                            ? decision.get("event_key") : decision.get("eventKey");
                    Object index = decision.containsKey("choice_index")
                            ? decision.get("choice_index") : decision.get("choiceIndex");
                    if (source.equals(decisionKey) && indices.stream().anyMatch(allowed ->
                            toInt(allowed, -2) == toInt(index, -1))) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) return false;
            }
        }
        if (conditions.containsKey("choice_unlockers")) {
            Object choiceUnlockersValue = conditions.get("choice_unlockers");
            if (!(choiceUnlockersValue instanceof List<?> choiceUnlockers) || choiceUnlockers.isEmpty()) {
                return false;
            }
            boolean unlocked = false;
            for (Object unlockerValue : choiceUnlockers) {
                if (!(unlockerValue instanceof Map<?, ?> unlocker)) {
                    continue;
                }
                Object requiredEventValue = unlocker.get("event_key");
                Object requiredChoiceIndicesValue = unlocker.get("choice_indices");
                if (!(requiredEventValue instanceof String requiredEventKey)
                        || !(requiredChoiceIndicesValue instanceof List<?> requiredChoiceIndices)) {
                    continue;
                }
                for (Object decisionValue : decisionHistory) {
                    if (!(decisionValue instanceof Map<?, ?> decision)) {
                        continue;
                    }
                    Object decisionEventKey = decision.containsKey("event_key")
                            ? decision.get("event_key") : decision.get("eventKey");
                    Object choiceIndexValue = decision.containsKey("choice_index")
                            ? decision.get("choice_index") : decision.get("choiceIndex");
                    if (!requiredEventKey.equals(decisionEventKey)) {
                        continue;
                    }
                    int chosenIndex = toInt(choiceIndexValue, -1);
                    if (requiredChoiceIndices.stream().anyMatch(index -> toInt(index, -2) == chosenIndex)) {
                        unlocked = true;
                        break;
                    }
                }
                if (unlocked) {
                    break;
                }
            }
            if (!unlocked) {
                return false;
            }
        }
        return true;
    }

    private static int toInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return defaultValue;
    }
}
