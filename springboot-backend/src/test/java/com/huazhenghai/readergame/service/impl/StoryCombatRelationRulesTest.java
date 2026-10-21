package com.huazhenghai.readergame.service.impl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryCombatRelationRulesTest {

    @Test
    void requiresTheExactPersonalClueAndTheMatchingEncounter() {
        Map<String, Object> personalProgress = Map.of(
                "sideEventsTriggered", List.of("gwangjin_side_boat"));

        assertTrue(StoryCombatRelationRules
                .findForEncounter("bridge_hunger_entity", personalProgress).isPresent());
        assertTrue(StoryCombatRelationRules
                .findForEncounter("another_story_eater", personalProgress).isEmpty());
        assertTrue(StoryCombatRelationRules
                .findForEncounter("bridge_hunger_entity", Map.of(
                        "storyEventsTriggered", List.of("gwangjin_side_boat"))).isEmpty());
        assertTrue(StoryCombatRelationRules
                .findForEncounter("bridge_hunger_entity", Map.of(
                        "sideEventsTriggered", List.of("another_bridge_event"))).isEmpty());
    }

    @Test
    void clueExplainsItsGameDerivedEffectAndKeepsRetreatAsCounterplay() {
        String hint = StoryCombatRelationRules.unlockHintForEvent("gwangjin_side_boat")
                .orElseThrow();

        assertTrue(hint.contains("25%"));
        assertTrue(hint.contains("最多 5 点"));
        assertTrue(hint.contains("撤退"));
        assertTrue(StoryCombatRelationRules.unlockHintForEvent("unrelated_event").isEmpty());
    }

    @Test
    void oneHitMitigationUsesThePercentageAndHardCapWithoutNegatingTheHit() {
        StoryCombatRelationRules.Rule rule = StoryCombatRelationRules
                .findForEncounter("bridge_hunger_entity", Map.of(
                        "sideEventsTriggered", List.of("gwangjin_side_boat")))
                .orElseThrow();

        StoryCombatRelationRules.HitResult ordinaryHit = rule.reduceFirstIncomingHit(16);
        assertTrue(ordinaryHit.applied());
        assertEquals(12, ordinaryHit.finalDamage());
        assertEquals(4, ordinaryHit.damageReduced());

        StoryCombatRelationRules.HitResult cappedHit = rule.reduceFirstIncomingHit(100);
        assertTrue(cappedHit.applied());
        assertEquals(95, cappedHit.finalDamage());
        assertEquals(5, cappedHit.damageReduced());

        StoryCombatRelationRules.HitResult tinyHit = rule.reduceFirstIncomingHit(1);
        assertFalse(tinyHit.applied());
        assertEquals(1, tinyHit.finalDamage());
    }

    @Test
    void finalWallResonanceRequiresTheExactPersonalFlagAndEncounter() {
        Map<String, Object> personalFlags = Map.of("story_final_wall_echo_attuned", true);

        assertTrue(StoryCombatRelationRules
                .findAttackRuleForEncounter("last_wall_dokkaebi_echo", personalFlags).isPresent());
        assertTrue(StoryCombatRelationRules
                .findAttackRuleForEncounter("another_story_echo", personalFlags).isEmpty());
        assertTrue(StoryCombatRelationRules
                .findAttackRuleForEncounter("last_wall_dokkaebi_echo", Map.of()).isEmpty());
        assertTrue(StoryCombatRelationRules
                .findAttackRuleForEncounter("last_wall_dokkaebi_echo",
                        Map.of("story_final_wall_echo_attuned", false)).isEmpty());
        assertTrue(StoryCombatRelationRules
                .findAttackRuleForEncounter("last_wall_dokkaebi_echo",
                        Map.of("story_final_wall_echo_attuned", "true")).isEmpty());
    }

    @Test
    void finalWallResonanceBoostsOnlyOneHitAndHonorsItsHardCap() {
        StoryCombatRelationRules.AttackRule rule = StoryCombatRelationRules
                .findAttackRuleForEncounter("last_wall_dokkaebi_echo",
                        Map.of("story_final_wall_echo_attuned", true))
                .orElseThrow();

        StoryCombatRelationRules.AttackResult ordinaryHit = rule.boostFirstOutgoingHit(35);
        assertTrue(ordinaryHit.applied());
        assertEquals(40, ordinaryHit.finalDamage());
        assertEquals(5, ordinaryHit.damageBonus());

        StoryCombatRelationRules.AttackResult cappedHit = rule.boostFirstOutgoingHit(100);
        assertTrue(cappedHit.applied());
        assertEquals(108, cappedHit.finalDamage());
        assertEquals(8, cappedHit.damageBonus());

        StoryCombatRelationRules.AttackResult tinyHit = rule.boostFirstOutgoingHit(1);
        assertFalse(tinyHit.applied());
        assertEquals(1, tinyHit.finalDamage());

        assertTrue(rule.sourceNote().contains("txt/12.txt:14-18"));
        assertTrue(rule.sourceNote().contains("本游戏改编"));
        assertTrue(StoryCombatRelationRules.unlockHintForEvent("last_wall_echo_personal_story")
                .orElseThrow().contains("最多 8 点"));
    }
}
