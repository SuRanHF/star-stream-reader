package com.huazhenghai.readergame.service.impl;

import com.huazhenghai.readergame.entity.ExplorationEvent;
import com.huazhenghai.readergame.entity.Location;
import com.huazhenghai.readergame.service.LocationUnlockPolicy;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** One read-only candidate check shared by exploration, preview and story tree. */
public final class ExploreCandidatePolicy {
    private ExploreCandidatePolicy() { }

    public static boolean canDraw(ExplorationEvent event, Map<String, Object> conditions,
            Location location, Map<String, Object> locationConditions,
            Map<String, Object> stats, Map<String, Object> storyFlags, String chapter,
            Map<String, Object> progress, List<?> decisions,
            List<String> triggeredStories, List<String> triggeredSides,
            Map<?, ?> pendingSnapshots, boolean lowerStoriesBlock,
            Function<String, Map<String, Object>> ancestors) {
        if (event == null || location == null || Integer.valueOf(0).equals(event.getEnabled())) return false;
        if (!LocationUnlockPolicy.canAccessLocation(locationConditions, stats, storyFlags,
                chapter, triggeredStories)) return false;
        boolean story = "story".equals(event.getEventType());
        List<String> triggered = story ? triggeredStories : triggeredSides;
        if (triggered.contains(event.getEventKey()) && Integer.valueOf(0).equals(event.getRepeatable())) return false;
        if (story && pendingSnapshots.containsKey(event.getEventKey())
                && event.getChoicesJson() != null && !event.getChoicesJson().isBlank()
                && !"[]".equals(event.getChoicesJson().trim())) return false;
        if (story && (!RouteContinuationPolicy.allowsStory(progress, conditions)
                || ExploreStoryEligibility.choiceHistoryPermanentlyClosesEvent(
                        conditions, decisions, triggeredStories, ancestors))) return false;
        if (!ExploreStoryEligibility.checkRequiredConditions(stats, progress, decisions, conditions)) return false;
        return !lowerStoriesBlock || !(story || "side_story".equals(event.getEventType()));
    }
}
