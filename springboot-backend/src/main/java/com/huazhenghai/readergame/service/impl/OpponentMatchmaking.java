package com.huazhenghai.readergame.service.impl;

public final class OpponentMatchmaking {

    private OpponentMatchmaking() {}

    public record Assessment(int band, int ratingGap, int powerGapPercent, String label) {}

    public static Assessment assess(int ownRating, int ownPower, int opponentRating, int opponentPower) {
        int ratingGap = (int) Math.min(Integer.MAX_VALUE,
                Math.abs((long) ownRating - opponentRating));
        long own = Math.max(0, ownPower);
        long opponent = Math.max(0, opponentPower);
        long largestPower = Math.max(own, opponent);
        int powerGapPercent = largestPower == 0 ? 0 : (int) Math.round(
                Math.abs(own - opponent) * 100.0 / largestPower);

        if (ratingGap <= 150 && powerGapPercent <= 20)
            return new Assessment(0, ratingGap, powerGapPercent, "势均力敌");
        if (ratingGap <= 300 && powerGapPercent <= 40)
            return new Assessment(1, ratingGap, powerGapPercent, "实力接近");
        return new Assessment(2, ratingGap, powerGapPercent, "越级挑战");
    }
}
