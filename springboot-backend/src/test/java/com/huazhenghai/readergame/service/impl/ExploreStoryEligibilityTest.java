package com.huazhenghai.readergame.service.impl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExploreStoryEligibilityTest {
    private static Map<String, Object> gate(String source, int choice) {
        return Map.of("choice_unlockers", List.of(
                Map.of("event_key", source, "choice_indices", List.of(choice))));
    }

    @Test
    void twoExclusiveRoutesWithTwoDescendantLayersCloseOnlyTheRejectedRoute() {
        Map<String, Map<String, Object>> graph = Map.of(
                "a1", gate("root", 0),
                "a2", Map.of("required_events", List.of("a1")),
                "b1", gate("root", 1),
                "b2", Map.of("required_events", List.of("b1"),
                        "choice_unlockers", List.of(
                                Map.of("event_key", "root", "choice_indices", List.of(0, 1)))));
        Function<String, Map<String, Object>> lookup = graph::get;
        List<?> choseA = List.of(Map.of("event_key", "root", "choice_index", 0));
        List<String> triggered = List.of("root");

        assertFalse(ExploreStoryEligibility.isUnfinishedStory("b2", graph.get("b2"),
                triggered, choseA, lookup, Map.of()));
        assertTrue(ExploreStoryEligibility.isUnfinishedStory("a2", graph.get("a2"),
                triggered, choseA, lookup, Map.of()));
        assertTrue(ExploreStoryEligibility.isUnfinishedStory("b2", graph.get("b2"),
                List.of(), List.of(), lookup, Map.of()), "undecided branches remain potentially reachable");
    }

    @Test
    void alternateChoiceGateRemainsOpenWhenAnyPathCanStillBeReached() {
        Map<String, Map<String, Object>> graph = Map.of("b1", gate("root", 1));
        Map<String, Object> conditions = Map.of("choice_unlockers", List.of(
                Map.of("event_key", "b1", "choice_indices", List.of(0)),
                Map.of("event_key", "root", "choice_indices", List.of(0))));
        assertTrue(ExploreStoryEligibility.isUnfinishedStory("next", conditions,
                List.of("root"), List.of(Map.of("event_key", "root", "choice_index", 0)),
                graph::get, Map.of()));
    }

    @Test
    void cyclicPrerequisitesDoNotRecurseForeverOrSilentlyCloseRoutes() {
        Map<String, Map<String, Object>> graph = Map.of(
                "x", Map.of("required_events", List.of("y")),
                "y", Map.of("required_events", List.of("x")));
        assertTrue(ExploreStoryEligibility.isUnfinishedStory("x", graph.get("x"),
                List.of(), List.of(), graph::get, Map.of()));
    }

    @Test
    void pendingTipAllowsOnlyDirectSuccessorsIncludingChoiceGates() {
        Map<String, Object> pending = Map.of("routePending", true,
                "routePendingEventKey", "my_tip");
        assertTrue(RouteContinuationPolicy.allowsStory(pending,
                Map.of("required_events", List.of("my_tip"))));
        assertTrue(RouteContinuationPolicy.allowsStory(pending, gate("my_tip", 0)));
        assertFalse(RouteContinuationPolicy.allowsStory(pending,
                Map.of("required_events", List.of("other_tip"))));
        assertFalse(RouteContinuationPolicy.allowsStory(pending,
                Map.of("required_events", List.of("successor_of_my_tip"))));
        assertFalse(RouteContinuationPolicy.allowsStory(pending, Map.of()));
        assertTrue(ExploreStoryEligibility.isUnfinishedStory("my_successor",
                Map.of("required_events", List.of("my_tip")), List.of("my_tip"), List.of(),
                key -> null, pending));
        assertFalse(ExploreStoryEligibility.isUnfinishedStory("other_successor",
                Map.of("required_events", List.of("other_tip")), List.of("my_tip"), List.of(),
                key -> null, pending));
    }
}
