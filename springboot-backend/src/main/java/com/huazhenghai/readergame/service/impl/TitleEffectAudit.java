package com.huazhenghai.readergame.service.impl;

import com.huazhenghai.readergame.vo.PlayerTitleVO;
import com.huazhenghai.readergame.vo.TitleVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Static classification of title effect keys; this class does not apply gameplay effects. */
final class TitleEffectAudit {
    private static final Set<String> ACTIVE = Set.of(
            "attack", "defense", "speed", "critRate", "critDamage", "channelHeatGainRate");
    private static final Set<String> INACTIVE = Set.of(
            "attackBonus", "channelHeat", "channelHeatBonus", "insight", "insightBonus",
            "narrativePressureBonus", "staminaBonus", "willpower");
    // maxHp reaches CombatStatsVO but combat HP is still read from the saved snapshot.
    // Keep it uncertain until the actual health behavior is specified and verified.

    private TitleEffectAudit() {}

    static void populate(PlayerTitleVO vo, Map<String, Object> rawEffects) {
        AuditResult audit = inspect(rawEffects);
        vo.setEffects(audit.numeric());
        vo.setInactiveEffects(audit.inactive());
        vo.setUncertainEffects(audit.uncertain());
        vo.setEffectScope("equipped");
    }

    static void populate(TitleVO vo, Map<String, Object> rawEffects) {
        AuditResult audit = inspect(rawEffects);
        vo.setEffects(rawEffects);
        vo.setInactiveEffects(audit.inactive());
        vo.setUncertainEffects(audit.uncertain());
        vo.setEffectScope("equipped");
    }

    private static AuditResult inspect(Map<String, Object> rawEffects) {
        Map<String, Number> numeric = new LinkedHashMap<>();
        List<String> inactive = new ArrayList<>();
        List<String> uncertain = new ArrayList<>();
        for (Map.Entry<String, Object> effect : rawEffects.entrySet()) {
            if (!(effect.getValue() instanceof Number value)) {
                uncertain.add(effect.getKey());
                continue;
            }
            numeric.put(effect.getKey(), value);
            if (INACTIVE.contains(effect.getKey())) inactive.add(effect.getKey());
            else if (!ACTIVE.contains(effect.getKey())) uncertain.add(effect.getKey());
        }
        return new AuditResult(numeric, inactive, uncertain);
    }

    private record AuditResult(Map<String, Number> numeric, List<String> inactive, List<String> uncertain) {}
}
