package com.huazhenghai.readergame.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.entity.FactionSkill;
import com.huazhenghai.readergame.entity.PlayerFaction;
import com.huazhenghai.readergame.entity.PlayerFactionSkill;
import com.huazhenghai.readergame.mapper.FactionSkillMapper;
import com.huazhenghai.readergame.mapper.PlayerFactionMapper;
import com.huazhenghai.readergame.mapper.PlayerFactionSkillMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Calculates temporary stamina capacity from currently active abilities. */
@Service
public class PlayerStaminaProjectionService {

    private final SkillService skillService;
    private final TitleService titleService;
    private final PlayerFactionMapper playerFactionMapper;
    private final PlayerFactionSkillMapper playerFactionSkillMapper;
    private final FactionSkillMapper factionSkillMapper;
    private final ObjectMapper objectMapper;

    public PlayerStaminaProjectionService(SkillService skillService,
                                          TitleService titleService,
                                          PlayerFactionMapper playerFactionMapper,
                                          PlayerFactionSkillMapper playerFactionSkillMapper,
                                          FactionSkillMapper factionSkillMapper,
                                          ObjectMapper objectMapper) {
        this.skillService = skillService;
        this.titleService = titleService;
        this.playerFactionMapper = playerFactionMapper;
        this.playerFactionSkillMapper = playerFactionSkillMapper;
        this.factionSkillMapper = factionSkillMapper;
        this.objectMapper = objectMapper;
    }

    public int projectMaxStamina(Long playerId, int storedMaxStamina) {
        double bonus = numericEffect(skillService.calculateSkillBonus(playerId), "maxStamina")
                + numericEffect(titleService.calculateTitleEffects(playerId), "maxStamina");

        bonus += numericEffect(factionSkillBonuses(playerId), "maxStamina");

        double projected = Math.max(1, storedMaxStamina) + bonus;
        if (!Double.isFinite(projected)) return Math.max(1, storedMaxStamina);
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, Math.round(projected)));
    }

    private double numericEffect(Map<?, ?> effects, String key) {
        if (effects == null || !(effects.get(key) instanceof Number value)) return 0;
        double amount = value.doubleValue();
        return Double.isFinite(amount) ? amount : 0;
    }

    private Map<String, Object> factionSkillBonuses(Long playerId) {
        if (playerId == null) return Collections.emptyMap();
        PlayerFaction activeFaction = playerFactionMapper.selectOne(new QueryWrapper<PlayerFaction>()
                .eq("player_id", playerId).eq("status", "active"));
        if (activeFaction == null || activeFaction.getFactionKey() == null) return Collections.emptyMap();

        List<PlayerFactionSkill> learnedSkills = playerFactionSkillMapper.selectList(
                new QueryWrapper<PlayerFactionSkill>()
                        .eq("player_id", playerId).eq("faction_key", activeFaction.getFactionKey()));
        Map<String, Object> bonuses = new LinkedHashMap<>();
        for (PlayerFactionSkill learnedSkill : learnedSkills) {
            FactionSkill definition = factionSkillMapper.selectOne(new QueryWrapper<FactionSkill>()
                    .eq("faction_key", activeFaction.getFactionKey())
                    .eq("skill_key", learnedSkill.getSkillKey()).eq("enabled", 1));
            if (definition == null) continue;
            for (Map.Entry<String, Object> effect : parseEffects(definition.getEffectsJson()).entrySet()) {
                if (!(effect.getValue() instanceof Number amount)) continue;
                double current = numericEffect(bonuses, effect.getKey());
                bonuses.put(effect.getKey(), current + amount.doubleValue());
            }
        }
        return bonuses;
    }

    private Map<String, Object> parseEffects(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("阵营技能效果配置无法解析", exception);
        }
    }
}
