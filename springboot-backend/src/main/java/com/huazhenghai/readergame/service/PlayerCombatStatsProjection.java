package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.CombatStatsVO;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Applies the combat service's authoritative totals and source breakdown to a player view. */
public final class PlayerCombatStatsProjection {

    private PlayerCombatStatsProjection() {}

    public static void apply(Map<String, Object> stats, CombatStatsVO combatStats) {
        stats.put("combatProjection", true);
        stats.put("level", combatStats.getLevel());
        stats.put("hp", combatStats.getHp());
        stats.put("maxHp", combatStats.getMaxHp());
        stats.put("attack", combatStats.getAttack());
        stats.put("defense", combatStats.getDefense());
        stats.put("speed", combatStats.getSpeed());
        stats.put("critRate", combatStats.getCritRate());
        stats.put("critDamage", combatStats.getCritDamage());

        Map<String, Object> bonuses = combatStats.getBonuses() == null
                ? Collections.emptyMap() : new LinkedHashMap<>(combatStats.getBonuses());
        stats.put("combatBonusSources", bonuses);
        Map<String, Object> base = asMap(bonuses.get("base"));
        stats.put("bonusAtkCombatBase", toDouble(base.get("attack")));
        stats.put("bonusDefCombatBase", toDouble(base.get("defense")));
        stats.put("bonusSpdCombatBase", toDouble(base.get("speed")));
        stats.put("bonusCritCombatBase", toDouble(base.get("critRate")));
        stats.put("bonusMaxHpCombatBase", toDouble(base.get("maxHp")));

        double allocatedAtk = toDouble(stats.get("allocatedAtk"));
        double allocatedDef = toDouble(stats.get("allocatedDef"));
        double allocatedSpd = toDouble(stats.get("allocatedSpd"));
        double allocatedCrit = toDouble(stats.get("allocatedCrit"));
        stats.put("bonusAtkBase", 10);
        stats.put("bonusAtkAlloc", allocatedAtk);
        stats.put("bonusAtkStoredGrowth", toDouble(base.get("attack")) - 10 - allocatedAtk);
        stats.put("bonusDefBase", 5);
        stats.put("bonusDefAlloc", allocatedDef);
        stats.put("bonusDefStoredGrowth", toDouble(base.get("defense")) - 5 - allocatedDef);
        stats.put("bonusSpdBase", 3);
        stats.put("bonusSpdAlloc", allocatedSpd);
        stats.put("bonusSpdStoredGrowth", toDouble(base.get("speed")) - 3 - allocatedSpd);
        stats.put("bonusCritBase", 0.0);
        stats.put("bonusCritAlloc", allocatedCrit);
        stats.put("bonusCritStoredGrowth", toDouble(base.get("critRate")) - allocatedCrit * 0.02);
        stats.put("bonusMaxHpBase", 100);
        stats.put("bonusMaxHpAlloc", 0);
        stats.put("bonusMaxHpStoredGrowth", toDouble(base.get("maxHp")) - 100);

        for (String source : List.of("LevelGrowth", "Constellation", "Title", "Equipment",
                "Skill", "Faction", "FactionSkill")) {
            Map<String, Object> sourceBonuses = asMap(bonuses.get(decapitalize(source)));
            if ("Constellation".equals(source)) {
                // Non-HP constellation effects remain visible in the UI, but are not combat inputs today.
                setBonusField(stats, "maxHp", source, toDouble(sourceBonuses.get("maxHp")));
                continue;
            }
            setBonusField(stats, "attack", source, toDouble(sourceBonuses.get("attack")));
            setBonusField(stats, "defense", source, toDouble(sourceBonuses.get("defense")));
            setBonusField(stats, "speed", source, toDouble(sourceBonuses.get("speed")));
            setBonusField(stats, "critRate", source, toDouble(sourceBonuses.get("critRate")));
            setBonusField(stats, "maxHp", source, toDouble(sourceBonuses.get("maxHp")));
        }
    }

    private static String decapitalize(String value) {
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Collections.emptyMap();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, entry) -> {
            if (key instanceof String stringKey) result.put(stringKey, entry);
        });
        return result;
    }

    private static double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private static void setBonusField(Map<String, Object> stats, String statKey, String source, double value) {
        String fieldKey = switch (statKey) {
            case "attack", "atk" -> "bonusAtk" + source;
            case "defense", "def" -> "bonusDef" + source;
            case "speed", "spd" -> "bonusSpd" + source;
            case "critRate" -> "bonusCrit" + source;
            case "maxHp" -> "bonusMaxHp" + source;
            default -> null;
        };
        if (fieldKey != null) stats.put(fieldKey, Math.round(value * 100.0) / 100.0);
    }
}
