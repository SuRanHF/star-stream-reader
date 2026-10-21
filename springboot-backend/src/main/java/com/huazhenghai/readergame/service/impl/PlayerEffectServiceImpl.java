package com.huazhenghai.readergame.service.impl;

import com.huazhenghai.readergame.service.PlayerEffectService;
import com.huazhenghai.readergame.service.SkillService;
import com.huazhenghai.readergame.service.TitleService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PlayerEffectServiceImpl implements PlayerEffectService {

    private final SkillService skillService;
    private final TitleService titleService;

    public PlayerEffectServiceImpl(SkillService skillService, TitleService titleService) {
        this.skillService = skillService;
        this.titleService = titleService;
    }

    @Override
    public double getChannelHeatGainRate(Long playerId) {
        return finiteBonus(skillService.calculateSkillBonus(playerId))
                + finiteBonus(titleService.calculateTitleEffects(playerId));
    }

    @Override
    public int applyChannelHeatGainRate(Long playerId, int baseGain) {
        if (baseGain <= 0) return baseGain;
        return PlayerEffectService.applyChannelHeatGainRate(baseGain, getChannelHeatGainRate(playerId));
    }

    private double finiteBonus(Map<String, Object> effects) {
        if (effects == null || !(effects.get("channelHeatGainRate") instanceof Number value)) return 0.0;
        double bonus = value.doubleValue();
        return Double.isFinite(bonus) ? bonus : 0.0;
    }
}
