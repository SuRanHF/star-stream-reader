package com.huazhenghai.readergame.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.entity.ExplorationEvent;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/** Route tips are event nodes, matching the story tree's event-based node_id. */
final class RouteCodexPolicy {
    // These four first-growth tips were made continuable by the next growth.
    // Keep former tip keys here when a later growth removes route_pending from its choices.
    private static final int CURRENT_GROWTH = 2;
    private static final List<String> FORMER_TIPS = List.of(
            "chungmuro_conspiracy_story", "chungmuro_compromise_ledger",
            "chungmuro_script_blank", "chungmuro_hidden_reader_wall");
    // The second growth was committed at this local server time. A later choice of an old
    // tip's event is only traversal, not proof that the player stopped there.
    private static final LocalDateTime SECOND_GROWTH_CUTOFF = LocalDateTime.parse("2026-11-01T03:12:51");

    private RouteCodexPolicy() { }

    static int growth() { return CURRENT_GROWTH; }

    static Map<String, Set<Integer>> tips(Map<String, ExplorationEvent> events, ObjectMapper json) {
        Map<String, Set<Integer>> tips = new LinkedHashMap<>();
        for (String key : FORMER_TIPS) if (events.containsKey(key)) tips.put(key, Set.of());
        for (ExplorationEvent event : events.values().stream()
                .sorted(Comparator.comparing(ExplorationEvent::getEventKey)).toList()) {
            Set<Integer> marked = new LinkedHashSet<>();
            List<?> choices = list(json, event.getChoicesJson());
            for (int i = 0; i < choices.size(); i++)
                if (choices.get(i) instanceof Map<?, ?> choice && Boolean.TRUE.equals(choice.get("route_pending")))
                    marked.add(i);
            if (!marked.isEmpty() || Boolean.TRUE.equals(map(json, event.getProgressEffectsJson()).get("route_pending")))
                tips.put(event.getEventKey(), marked);
        }
        return tips;
    }

    static Set<String> reached(Map<String, Object> progress, List<?> decisions,
            Map<String, Set<Integer>> tips) {
        Set<String> reached = new LinkedHashSet<>();
        if (progress.get("routeReachedTipKeys") instanceof List<?> stored)
            for (Object value : stored) if (value instanceof String key && tips.containsKey(key)) reached.add(key);
        if (RouteContinuationPolicy.isPending(progress)
                && progress.get("routePendingEventKey") instanceof String key && tips.containsKey(key)) reached.add(key);
        Set<String> chosen = new LinkedHashSet<>();
        for (Object value : decisions) if (value instanceof Map<?, ?> decision) {
            Object keyValue = decision.containsKey("event_key") ? decision.get("event_key") : decision.get("eventKey");
            Object indexValue = decision.containsKey("choice_index") ? decision.get("choice_index") : decision.get("choiceIndex");
            if (!(keyValue instanceof String key) || !(indexValue instanceof Number index)
                    || !tips.containsKey(key)) continue;
            chosen.add(key);
            if (FORMER_TIPS.contains(key) ? beforeSecondGrowth(decision) : tips.get(key).contains(index.intValue()))
                reached.add(key);
        }
        if (progress.get("storyEventsTriggered") instanceof List<?> triggered)
            for (Object value : triggered) if (value instanceof String key && tips.containsKey(key)
                    && tips.get(key).isEmpty() && !FORMER_TIPS.contains(key) && !chosen.contains(key))
                reached.add(key);
        return reached;
    }

    private static boolean beforeSecondGrowth(Map<?, ?> decision) {
        Object timestamp = decision.get("timestamp");
        if (!(timestamp instanceof String value)) return false;
        try { return LocalDateTime.parse(value).isBefore(SECOND_GROWTH_CUTOFF); }
        catch (DateTimeParseException ignored) { return false; }
    }

    private static Map<String, Object> map(ObjectMapper json, String source) {
        if (source == null || source.isBlank()) return Map.of();
        try { return json.readValue(source, new TypeReference<>() { }); }
        catch (Exception ignored) { return Map.of(); }
    }

    private static List<?> list(ObjectMapper json, String source) {
        if (source == null || source.isBlank()) return List.of();
        try { return json.readValue(source, List.class); }
        catch (Exception ignored) { return List.of(); }
    }
}
