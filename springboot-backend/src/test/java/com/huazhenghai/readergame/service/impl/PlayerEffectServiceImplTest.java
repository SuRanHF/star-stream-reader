package com.huazhenghai.readergame.service.impl;

import com.huazhenghai.readergame.service.SkillService;
import com.huazhenghai.readergame.service.PlayerEffectService;
import com.huazhenghai.readergame.service.TitleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerEffectServiceImplTest {

    @Mock private SkillService skillService;
    @Mock private TitleService titleService;
    @InjectMocks private PlayerEffectServiceImpl playerEffectService;

    @Test
    void combinesEquippedSkillAndTitleRatesBeforeRoundingReward() {
        when(skillService.calculateSkillBonus(7L)).thenReturn(Map.of("channelHeatGainRate", 0.08));
        when(titleService.calculateTitleEffects(7L)).thenReturn(Map.of("channelHeatGainRate", 0.05));

        assertEquals(0.13, playerEffectService.getChannelHeatGainRate(7L), 1.0e-9);
        assertEquals(23, playerEffectService.applyChannelHeatGainRate(7L, 20));
        verify(skillService, org.mockito.Mockito.times(2)).calculateSkillBonus(7L);
        verify(titleService, org.mockito.Mockito.times(2)).calculateTitleEffects(7L);
    }

    @Test
    void missingEffectsLeaveRewardUnchangedAndNegativeRatesOnlyReducePositiveGains() {
        when(skillService.calculateSkillBonus(7L)).thenReturn(null);
        when(titleService.calculateTitleEffects(7L)).thenReturn(null);

        assertEquals(15, playerEffectService.applyChannelHeatGainRate(7L, 15));
        assertEquals(-10, PlayerEffectService.applyChannelHeatGainRate(-10, 0.5));
        assertEquals(5, PlayerEffectService.applyChannelHeatGainRate(10, -0.5));
    }
}
