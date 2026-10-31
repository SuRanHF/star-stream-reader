package com.huazhenghai.readergame.service.impl;

import com.huazhenghai.readergame.entity.ExplorationEvent;

import java.util.List;
import java.util.Map;

/** Only already eligible, player chosen route stories receive the trace weight. */
final class ExploreRouteTrace {
    private ExploreRouteTrace() { }

    static boolean isTracedStory(ExplorationEvent event, Map<String, Object> conditions,
            Map<String, Object> progress, List<?> decisions) {
        if (!"story".equals(event.getEventType())) return false;
        if (RouteContinuationPolicy.isPending(progress)
                && RouteContinuationPolicy.allowsStory(progress, conditions)) return true;
        return matchesChoice(conditions.get("choice_unlockers"), decisions)
                || matchesChoice(conditions.get("required_choices"), decisions);
    }

    private static boolean matchesChoice(Object gatesValue, List<?> decisions) {
        if (!(gatesValue instanceof List<?> gates)) return false;
        for (Object gateValue : gates) {
            if (!(gateValue instanceof Map<?, ?> gate)
                    || !(gate.get("event_key") instanceof String source)
                    || !(gate.get("choice_indices") instanceof List<?> indices)) continue;
            for (Object decisionValue : decisions) {
                if (!(decisionValue instanceof Map<?, ?> decision)) continue;
                Object key = decision.containsKey("event_key") ? decision.get("event_key") : decision.get("eventKey");
                Object index = decision.containsKey("choice_index") ? decision.get("choice_index") : decision.get("choiceIndex");
                if (source.equals(key) && index instanceof Number chosen
                        && indices.stream().anyMatch(allowed -> allowed instanceof Number number
                                && number.intValue() == chosen.intValue())) return true;
            }
        }
        return false;
    }

    static int extraCost(int baseCost, double ratio) {
        return Math.max(1, (int) Math.ceil(baseCost * ratio));
    }
}
