package com.huazhenghai.readergame.service;

import java.util.Map;

/** Shared experience, level-up, and level reward rules. */
public final class ExperienceProgression {

    private ExperienceProgression() {
    }

    /**
     * The player's exp field is lifetime experience. Level N starts at
     * 100 * (N - 1)^2 and advances at 100 * N^2.
     */
    public static Result addExperience(Map<String, Object> stats, int baseExperience, double multiplier) {
        long currentExperience = Math.max(0, Math.min(Integer.MAX_VALUE, asLong(stats.get("exp"), 0)));
        int previousLevel = Math.max(1, asInt(stats.get("level"), 1));

        long requestedAward = 0;
        if (baseExperience > 0 && Double.isFinite(multiplier) && multiplier > 0) {
            requestedAward = Math.max(0L, Math.round(baseExperience * multiplier));
        }
        int experienceAwarded = (int) Math.min(requestedAward, Integer.MAX_VALUE - currentExperience);
        long totalExperience = currentExperience + experienceAwarded;
        stats.put("exp", (int) totalExperience);

        int levelFromExperience = (int) Math.floor(Math.sqrt(totalExperience / 100.0)) + 1;
        int currentLevel = Math.max(previousLevel, levelFromExperience);
        int levelsGained = currentLevel - previousLevel;
        if (levelsGained > 0) {
            int currentFreePoints = Math.max(0, asInt(stats.get("freePoints"), 0));
            long updatedFreePoints = (long) currentFreePoints + 3L * levelsGained;
            stats.put("freePoints", (int) Math.min(Integer.MAX_VALUE, updatedFreePoints));
            stats.put("hp", Math.max(1, asInt(stats.get("maxHp"), 100)));
        }

        stats.put("level", currentLevel);
        long maxExperience = 100L * currentLevel * currentLevel;
        stats.put("maxExp", (int) Math.min(Integer.MAX_VALUE, maxExperience));
        return new Result(experienceAwarded, previousLevel, currentLevel, levelsGained);
    }

    /** Keep all experience sources on the same rank bonus table. */
    public static double multiplierForRank(String rankKey) {
        if (rankKey == null) return 1.0;
        return switch (rankKey.toUpperCase()) {
            case "E" -> 1.5;
            case "D" -> 2.5;
            case "C" -> 4.0;
            case "B" -> 7.0;
            case "A" -> 12.0;
            case "S" -> 20.0;
            case "SS" -> 35.0;
            case "SSS" -> 60.0;
            default -> 1.0;
        };
    }

    public static String levelUpMessage(Result result) {
        if (result == null || result.levelsGained() <= 0) return null;
        return "你的修为提升至等级 " + result.currentLevel() + "，获得 "
                + (3 * result.levelsGained()) + " 点自由属性点。";
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        return fallback;
    }

    private static long asLong(Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        return fallback;
    }

    public record Result(int experienceAwarded, int previousLevel, int currentLevel, int levelsGained) {
    }
}
