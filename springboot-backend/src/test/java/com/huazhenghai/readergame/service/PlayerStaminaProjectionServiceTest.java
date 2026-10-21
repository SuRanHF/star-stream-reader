package com.huazhenghai.readergame.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.entity.FactionSkill;
import com.huazhenghai.readergame.entity.PlayerFaction;
import com.huazhenghai.readergame.entity.PlayerFactionSkill;
import com.huazhenghai.readergame.mapper.FactionSkillMapper;
import com.huazhenghai.readergame.mapper.PlayerFactionMapper;
import com.huazhenghai.readergame.mapper.PlayerFactionSkillMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class PlayerStaminaProjectionServiceTest {

    private final SkillService skillService = mock(SkillService.class);
    private final TitleService titleService = mock(TitleService.class);
    private final PlayerFactionMapper playerFactionMapper = mock(PlayerFactionMapper.class);
    private final PlayerFactionSkillMapper playerFactionSkillMapper = mock(PlayerFactionSkillMapper.class);
    private final FactionSkillMapper factionSkillMapper = mock(FactionSkillMapper.class);
    private final PlayerStaminaProjectionService projection =
            new PlayerStaminaProjectionService(skillService, titleService, playerFactionMapper,
                    playerFactionSkillMapper, factionSkillMapper, new ObjectMapper());

    @Test
    void combinesEquippedSkillTitleAndFactionSkillCapacityBonuses() {
        when(skillService.calculateSkillBonus(7L)).thenReturn(Map.of("maxStamina", 5));
        when(titleService.calculateTitleEffects(7L)).thenReturn(Map.of("maxStamina", 2));
        PlayerFaction faction = new PlayerFaction();
        faction.setFactionKey("reader_circle");
        when(playerFactionMapper.selectOne(any())).thenReturn(faction);
        PlayerFactionSkill learnedSkill = new PlayerFactionSkill();
        learnedSkill.setSkillKey("expanded_reserve");
        when(playerFactionSkillMapper.selectList(any())).thenReturn(java.util.List.of(learnedSkill));
        FactionSkill skill = new FactionSkill();
        skill.setEffectsJson("{\"maxStamina\":3}");
        when(factionSkillMapper.selectOne(any())).thenReturn(skill);

        assertEquals(60, projection.projectMaxStamina(7L, 50));
    }

    @Test
    void invalidBonusDoesNotBreakBaseCapacityAndNegativeCapacityHasMinimumOne() {
        when(skillService.calculateSkillBonus(8L)).thenReturn(Map.of("maxStamina", Double.NaN));
        when(titleService.calculateTitleEffects(8L)).thenReturn(Map.of());
        when(playerFactionMapper.selectOne(any())).thenReturn(null);
        assertEquals(50, projection.projectMaxStamina(8L, 50));

        when(skillService.calculateSkillBonus(9L)).thenReturn(Map.of("maxStamina", -500));
        when(titleService.calculateTitleEffects(9L)).thenReturn(Map.of());
        when(playerFactionMapper.selectOne(any())).thenReturn(null);
        assertEquals(1, projection.projectMaxStamina(9L, 50));
    }
}
