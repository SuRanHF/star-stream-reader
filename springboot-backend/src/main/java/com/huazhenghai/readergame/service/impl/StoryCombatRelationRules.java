package com.huazhenghai.readergame.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Explicit, encounter-scoped story interactions derived for this game.
 * These rules are not an original-novel damage affinity table.
 */
public final class StoryCombatRelationRules {

    private static final List<Rule> RULES = List.of(
            new Rule(
                    "gwangjin_boatman_story_anchor",
                    "bridge_hunger_entity",
                    "gwangjin_side_boat",
                    "explicit",
                    "first_incoming_hit_damage_reduction",
                    0.25,
                    5,
                    1,
                    "若不愿承受风险，仍可使用常规撤退；该线索不保证战斗胜利。",
                    "game_derived",
                    "由本游戏的桥下渡船支线与桥下饥饿实体设定推导，不是原著规则。",
                    "你记住了渡船人的提醒。若遭遇桥下的饥饿实体，它首次命中的伤害会降低 25%（最多 5 点）；仍可选择撤退。",
                    "你想起渡船人的提醒，稳住了自己的故事（减伤 %d 点）。"
            )
    );

    private static final List<AttackRule> ATTACK_RULES = List.of(
            new AttackRule(
                    "last_wall_personal_resonance",
                    "last_wall_dokkaebi_echo",
                    "story_final_wall_echo_attuned",
                    "last_wall_echo_personal_story",
                    0.15,
                    8,
                    1,
                    "game_derived",
                    "《星流读者》第12卷描写刘众赫与隐密的谋略家在最后一道墙前发生传说共鸣，力量沿黑天魔刀暴涨（txt/12.txt:14-18）。玩家个人回响和首击数值是本游戏改编，原著没有给出伤害百分比。",
                    "仅在你选择进入终墙个人回响后，对‘终墙鬼怪之王回声’的首次攻击额外造成 15% 伤害（最多 8 点）。这是本游戏改编，不是原著数值；战斗仍可撤退。",
                    "终墙回响使你的首次攻击额外造成 %d 点伤害。"
            )
    );

    private StoryCombatRelationRules() {
    }

    public static Optional<Rule> findForEncounter(String monsterKey, Map<String, Object> playerProgress) {
        if (monsterKey == null || playerProgress == null) return Optional.empty();
        Object triggeredValue = playerProgress.get("sideEventsTriggered");
        if (!(triggeredValue instanceof Iterable<?> triggeredEvents)) return Optional.empty();

        for (Rule rule : RULES) {
            if (!rule.monsterKey().equals(monsterKey)) continue;
            for (Object triggeredEvent : triggeredEvents) {
                if (rule.evidenceEventKey().equals(Objects.toString(triggeredEvent, null))) {
                    return Optional.of(rule);
                }
            }
        }
        return Optional.empty();
    }

    public static Optional<AttackRule> findAttackRuleForEncounter(
            String monsterKey, Map<String, Object> playerStoryFlags) {
        if (monsterKey == null || playerStoryFlags == null) return Optional.empty();
        return ATTACK_RULES.stream()
                .filter(rule -> rule.monsterKey().equals(monsterKey))
                .filter(rule -> Boolean.TRUE.equals(playerStoryFlags.get(rule.requiredStoryFlag())))
                .findFirst();
    }

    public static Optional<String> unlockHintForEvent(String eventKey) {
        if (eventKey == null) return Optional.empty();
        Optional<String> incomingHint = RULES.stream()
                .filter(rule -> rule.evidenceEventKey().equals(eventKey))
                .map(Rule::unlockHint)
                .findFirst();
        return incomingHint.isPresent() ? incomingHint : ATTACK_RULES.stream()
                .filter(rule -> rule.evidenceEventKey().equals(eventKey))
                .map(AttackRule::unlockHint)
                .findFirst();
    }

    public record Rule(
            String ruleKey,
            String monsterKey,
            String evidenceEventKey,
            String hintLevel,
            String effectType,
            double reductionRate,
            int maximumDamageReduction,
            int maximumApplications,
            String countermeasure,
            String sourceCategory,
            String sourceNote,
            String unlockHint,
            String combatNoteTemplate
    ) {
        public Rule {
            if (ruleKey == null || ruleKey.isBlank()
                    || monsterKey == null || monsterKey.isBlank()
                    || evidenceEventKey == null || evidenceEventKey.isBlank()) {
                throw new IllegalArgumentException("故事战斗关系必须标明规则、遭遇和证据");
            }
            if (!(reductionRate > 0 && reductionRate <= 1)
                    || maximumDamageReduction <= 0 || maximumApplications != 1) {
                throw new IllegalArgumentException("故事战斗关系必须有有效比例、伤害上限且每场最多触发一次");
            }
        }

        public HitResult reduceFirstIncomingHit(int incomingDamage) {
            if (incomingDamage <= 1) return new HitResult(incomingDamage, 0, false);
            int proportionalReduction = (int) Math.round(incomingDamage * reductionRate);
            int reduction = Math.min(maximumDamageReduction,
                    Math.min(incomingDamage - 1, proportionalReduction));
            if (reduction <= 0) return new HitResult(incomingDamage, 0, false);
            return new HitResult(incomingDamage - reduction, reduction, true);
        }

        public String combatNote(int actualReduction) {
            return combatNoteTemplate.formatted(actualReduction);
        }

        public Map<String, Object> auditMetadata(int actualReduction) {
            return Map.of(
                    "ruleKey", ruleKey,
                    "sourceCategory", sourceCategory,
                    "sourceNote", sourceNote,
                    "evidenceEventKey", evidenceEventKey,
                    "effectType", effectType,
                    "applicationCount", 1,
                    "damageReduced", actualReduction,
                    "maximumDamageReduction", maximumDamageReduction
            );
        }
    }

    public record HitResult(int finalDamage, int damageReduced, boolean applied) {
    }

    public record AttackRule(
            String ruleKey,
            String monsterKey,
            String requiredStoryFlag,
            String evidenceEventKey,
            double bonusRate,
            int maximumDamageBonus,
            int maximumApplications,
            String sourceCategory,
            String sourceNote,
            String unlockHint,
            String combatNoteTemplate
    ) {
        public AttackRule {
            if (ruleKey == null || ruleKey.isBlank()
                    || monsterKey == null || monsterKey.isBlank()
                    || requiredStoryFlag == null || requiredStoryFlag.isBlank()
                    || evidenceEventKey == null || evidenceEventKey.isBlank()) {
                throw new IllegalArgumentException("故事攻击规则必须标明规则、遭遇、个人旗标和证据");
            }
            if (!(bonusRate > 0 && bonusRate <= 1)
                    || maximumDamageBonus <= 0 || maximumApplications != 1) {
                throw new IllegalArgumentException("故事攻击规则必须有有效比例、伤害上限且每场最多触发一次");
            }
        }

        public AttackResult boostFirstOutgoingHit(int baseDamage) {
            if (baseDamage <= 0) return new AttackResult(baseDamage, 0, false);
            int bonus = Math.min(maximumDamageBonus, (int) Math.round(baseDamage * bonusRate));
            if (bonus <= 0) return new AttackResult(baseDamage, 0, false);
            return new AttackResult(baseDamage + bonus, bonus, true);
        }

        public String combatNote(int actualBonus) {
            return combatNoteTemplate.formatted(actualBonus);
        }

        public Map<String, Object> auditMetadata(int baseDamage, int actualBonus) {
            return Map.of(
                    "ruleKey", ruleKey,
                    "sourceCategory", sourceCategory,
                    "sourceNote", sourceNote,
                    "evidenceEventKey", evidenceEventKey,
                    "evidenceStoryFlag", requiredStoryFlag,
                    "effectType", "first_outgoing_hit_damage_bonus",
                    "applicationCount", 1,
                    "baseDamage", baseDamage,
                    "damageBonus", actualBonus,
                    "maximumDamageBonus", maximumDamageBonus
            );
        }
    }

    public record AttackResult(int finalDamage, int damageBonus, boolean applied) {
    }
}
