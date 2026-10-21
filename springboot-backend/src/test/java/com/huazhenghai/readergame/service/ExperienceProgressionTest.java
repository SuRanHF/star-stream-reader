package com.huazhenghai.readergame.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExperienceProgressionTest {

    @Test
    void crossingThresholdSynchronizesLevelRewardsAndNextThreshold() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("exp", 99);
        stats.put("level", 1);
        stats.put("freePoints", 4);
        stats.put("hp", 37);
        stats.put("maxHp", 120);

        ExperienceProgression.Result result = ExperienceProgression.addExperience(stats, 1, 1.0);

        assertEquals(1, result.experienceAwarded());
        assertEquals(2, result.currentLevel());
        assertEquals(1, result.levelsGained());
        assertEquals(100, stats.get("exp"));
        assertEquals(2, stats.get("level"));
        assertEquals(7, stats.get("freePoints"));
        assertEquals(120, stats.get("hp"));
        assertEquals(400, stats.get("maxExp"));
        assertEquals("你的修为提升至等级 2，获得 3 点自由属性点。",
                ExperienceProgression.levelUpMessage(result));
    }

    @Test
    void zeroOrNegativeExperienceDoesNotCreateAnAwardButStillRepairsProgressFields() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("exp", 101);
        stats.put("level", 1);
        stats.put("freePoints", 2);
        stats.put("maxHp", 100);

        ExperienceProgression.Result result = ExperienceProgression.addExperience(stats, 0, 1.0);

        assertEquals(0, result.experienceAwarded());
        assertEquals(2, result.currentLevel());
        assertEquals(101, stats.get("exp"));
        assertEquals(5, stats.get("freePoints"));
        assertEquals(400, stats.get("maxExp"));
        assertEquals("你的修为提升至等级 2，获得 3 点自由属性点。",
                ExperienceProgression.levelUpMessage(result));

        ExperienceProgression.Result negative = ExperienceProgression.addExperience(stats, -5, 1.0);
        assertEquals(0, negative.experienceAwarded());
        assertEquals(101, stats.get("exp"));
        assertNull(ExperienceProgression.levelUpMessage(negative));
    }

    @Test
    void rankMultiplierAndMultipleLevelGainUseTheSameThresholdRules() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("exp", 99);
        stats.put("level", 1);
        stats.put("freePoints", 0);
        stats.put("maxHp", 80);

        ExperienceProgression.Result result = ExperienceProgression.addExperience(
                stats, 30, ExperienceProgression.multiplierForRank("D"));

        assertEquals(75, result.experienceAwarded());
        assertEquals(174, stats.get("exp"));
        assertEquals(2, stats.get("level"));
        assertEquals(3, stats.get("freePoints"));
        assertEquals(80, stats.get("hp"));
        assertEquals(400, stats.get("maxExp"));
        assertEquals(2.5, ExperienceProgression.multiplierForRank("D"), 0.0);
    }

    @Test
    void experienceStorageSaturatesInsteadOfOverflowing() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("exp", Integer.MAX_VALUE - 2);
        stats.put("level", 1);
        stats.put("freePoints", 0);

        ExperienceProgression.Result result = ExperienceProgression.addExperience(stats, 30, 60.0);

        assertEquals(2, result.experienceAwarded());
        assertEquals(Integer.MAX_VALUE, stats.get("exp"));
        assertEquals(4_635, stats.get("level"));
        assertEquals(Integer.MAX_VALUE, stats.get("maxExp"));
    }
}
