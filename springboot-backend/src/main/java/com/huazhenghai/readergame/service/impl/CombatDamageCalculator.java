package com.huazhenghai.readergame.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/** 纯战斗数值计算；随机序列由战斗流程统一持有。 */
final class CombatDamageCalculator {

    private CombatDamageCalculator() {
    }

    static Map<String, Object> calculateDamage(
            Random random,
            int attack,
            int defense,
            double criticalRate,
            double criticalDamage,
            double suppressionMultiplier,
            String suppressionNote) {
        int baseDamage = Math.max(1, (int) Math.round(attack - defense * 0.5));
        boolean critical = random.nextDouble() < criticalRate;
        int damage = critical ? (int) Math.round(baseDamage * criticalDamage) : baseDamage;
        damage = Math.max(1, (int) Math.round(damage * suppressionMultiplier));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("damage", damage);
        result.put("critical", critical);
        if (!suppressionNote.isEmpty()) {
            result.put("suppressionNote", suppressionNote);
        }
        return result;
    }

    static void appendSuppressionNote(Map<String, Object> action, String note) {
        if (note == null || note.isBlank()) {
            return;
        }
        Object existing = action.get("suppressionNote");
        if (existing instanceof String text && !text.isBlank()) {
            action.put("suppressionNote", text + "；" + note);
        } else {
            action.put("suppressionNote", note);
        }
    }

    /** 位格转数值：F=1、E=2、……、SSS=9。 */
    static int rankToValue(String rank) {
        if (rank == null) {
            return 1;
        }
        return switch (rank.toUpperCase()) {
            case "F" -> 1;
            case "E" -> 2;
            case "D" -> 3;
            case "C" -> 4;
            case "B" -> 5;
            case "A" -> 6;
            case "S" -> 7;
            case "SS" -> 8;
            case "SSS" -> 9;
            default -> 1;
        };
    }
}
