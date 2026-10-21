package com.huazhenghai.readergame.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationUnlockPolicyTest {

    @Test
    void allConfiguredPrerequisitesMustBeSatisfied() {
        Map<String, Object> conditions = Map.of(
                "required_level", 5,
                "prerequisite_events", List.of("route_witnessed", "gate_opened"));

        assertFalse(LocationUnlockPolicy.canAccessLocation(
                conditions, Map.of("level", 8), Map.of(), "2", Set.of("route_witnessed")));
        assertTrue(LocationUnlockPolicy.canAccessLocation(
                conditions, Map.of("level", 8), Map.of(), "2",
                Set.of("route_witnessed", "gate_opened")));
    }

    @Test
    void choiceUnlockRequiresItsItemAndChapterFlagsAsWell() {
        Map<String, Object> conditions = Map.of(
                "required_level", 10,
                "required_items", List.of("library_pass"),
                "chapter_min", 2,
                "unlock_by_choice", "seoul_forest");
        Map<String, Object> flags = Map.of(
                "held_items", List.of("library_pass"),
                "unlocked_locations", List.of("seoul_forest"));

        assertFalse(LocationUnlockPolicy.canAccessLocation(
                conditions, Map.of("level", 12), flags, "1", Set.of()));
        assertFalse(LocationUnlockPolicy.canAccessLocation(
                conditions, Map.of("level", 12), Map.of("held_items", List.of("library_pass")),
                "2", Set.of()));
        assertTrue(LocationUnlockPolicy.canAccessLocation(
                conditions, Map.of("level", 12), flags, "2", Set.of()));
    }

    @Test
    void alternatePrerequisiteEventsSupportCurrentAndLegacyStoryKeys() {
        Map<String, Object> conditions = Map.of(
                "required_level", 8,
                "prerequisite_events_any", List.of("chungmuro_tenkings_arrival", "chungmuro_revelation_story"));

        assertTrue(LocationUnlockPolicy.canAccessLocation(
                conditions, Map.of("level", 8), Map.of(), "1", Set.of("chungmuro_revelation_story")));
        assertTrue(LocationUnlockPolicy.canAccessLocation(
                conditions, Map.of("level", 8), Map.of(), "1", Set.of("chungmuro_tenkings_arrival")));
        assertFalse(LocationUnlockPolicy.canAccessLocation(
                conditions, Map.of("level", 8), Map.of(), "1", Set.of("some_other_story")));
        assertFalse(LocationUnlockPolicy.canAccessLocation(
                conditions, Map.of("level", 7), Map.of(), "1", Set.of("chungmuro_revelation_story")));
    }
}
