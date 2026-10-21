package com.huazhenghai.readergame.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpponentMatchmakingTest {

    @Test
    void classifiesEvenMatchesUsingBothRatingAndCombatPower() {
        var result = OpponentMatchmaking.assess(1000, 1000, 1100, 1150);

        assertEquals(0, result.band());
        assertEquals(13, result.powerGapPercent());
        assertEquals("势均力敌", result.label());
    }

    @Test
    void labelsModeratelyDifferentOpponentsAsClose() {
        var result = OpponentMatchmaking.assess(1000, 1000, 1250, 1300);

        assertEquals(1, result.band());
        assertEquals(23, result.powerGapPercent());
        assertEquals("实力接近", result.label());
    }

    @Test
    void keepsLargeRatingOrPowerGapsVisibleAsUpsetChallenges() {
        var result = OpponentMatchmaking.assess(1000, 1000, 1700, 2000);

        assertEquals(2, result.band());
        assertEquals(50, result.powerGapPercent());
        assertEquals("越级挑战", result.label());
    }

    @Test
    void treatsMissingOrNonpositivePowerAsZeroWithoutDivisionByZero() {
        var result = OpponentMatchmaking.assess(1000, 0, 1000, 0);

        assertEquals(0, result.band());
        assertEquals(0, result.powerGapPercent());
    }
}
