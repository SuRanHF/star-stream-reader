package com.huazhenghai.readergame.service;

/** Shared calculations for equipped passive effects that modify gameplay rewards. */
public interface PlayerEffectService {

    double getChannelHeatGainRate(Long playerId);

    int applyChannelHeatGainRate(Long playerId, int baseGain);

    static int applyChannelHeatGainRate(int baseGain, double gainRate) {
        if (baseGain <= 0 || !Double.isFinite(gainRate) || gainRate == 0.0) return baseGain;
        double scaled = baseGain * Math.max(0.0, 1.0 + gainRate);
        if (!Double.isFinite(scaled)) return baseGain;
        long rounded = Math.round(scaled);
        return (int) Math.min(Integer.MAX_VALUE, rounded);
    }
}
