package com.huazhenghai.readergame.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 按玩家已有进度判断个人剧情是否可进入或已永久关闭。 */
final class ExploreStoryEligibility {

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
                return false;
            }
        }
        // No valid gate, or every gate was decided against, means the event cannot be reached.
        return true;
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
