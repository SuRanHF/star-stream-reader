package com.huazhenghai.readergame.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.ResourceBalance;
import com.huazhenghai.readergame.entity.AvatarRankConfig;
import com.huazhenghai.readergame.mapper.AvatarRankConfigMapper;
import com.huazhenghai.readergame.mapper.PlayerTitleMapper;
import com.huazhenghai.readergame.mapper.TitleMapper;
import com.huazhenghai.readergame.entity.PlayerTitle;
import com.huazhenghai.readergame.entity.Title;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Projects saved maximum HP plus the currently applied constellation maximum-HP effect.
 * The marker lets recovery and constellation changes replace, rather than stack, that effect.
 */
@Service
public class PlayerMaxHpProjectionService {

    public static final String APPLIED_CONSTELLATION_BONUS_KEY = "maxHpConstellationBonus";
    public static final String APPLIED_TITLE_BONUS_KEY = "maxHpTitleBonus";

    private static final Map<String, Integer> CONSTELLATION_MAX_HP = Map.of(
            "demon_judge_of_fire", 10,
            "master_of_steel", 20,
            "father_of_rich_night", 5,
            "morning_star", 5
    );

    private final AvatarRankConfigMapper rankConfigMapper;
    private final ObjectMapper objectMapper;
    private final PlayerTitleMapper playerTitleMapper;
    private final TitleMapper titleMapper;

    @Autowired
    public PlayerMaxHpProjectionService(AvatarRankConfigMapper rankConfigMapper, ObjectMapper objectMapper,
                                        PlayerTitleMapper playerTitleMapper, TitleMapper titleMapper) {
        this.rankConfigMapper = rankConfigMapper;
        this.objectMapper = objectMapper;
        this.playerTitleMapper = playerTitleMapper;
        this.titleMapper = titleMapper;
    }

    // Small unit tests without title repositories use the same projection with no equipped title.
    public PlayerMaxHpProjectionService(AvatarRankConfigMapper rankConfigMapper, ObjectMapper objectMapper) {
        this(rankConfigMapper, objectMapper, null, null);
    }

    /** Returns the effective maximum HP without modifying the supplied snapshot. */
    public int projectForRead(Map<String, Object> stats) {
        return projectForRead(stats, null);
    }

    public int projectForRead(Map<String, Object> stats, Long playerId) {
        int savedMaxHp = Math.max(100, toInt(stats.get("maxHp"), 100));
        int constellationBonus = constellationBonus(stats);
        int titleBonus = titleBonus(playerId);
        Object appliedValue = stats.get(APPLIED_CONSTELLATION_BONUS_KEY);
        int appliedTitleBonus = Math.max(0, toInt(stats.get(APPLIED_TITLE_BONUS_KEY), 0));

        long persistentBase;
        if (appliedValue instanceof Number appliedNumber) {
            persistentBase = Math.max(100L, (long) savedMaxHp - appliedNumber.longValue() - appliedTitleBonus);
        } else {
            persistentBase = inferLegacyPersistentBase(stats, savedMaxHp, constellationBonus);
        }
        return total(persistentBase, constellationBonus + titleBonus);
    }

    /** Normalizes maxHp and records which constellation HP bonus is included in the saved total. */
    public int normalizeForPersistence(Map<String, Object> stats) {
        return normalizeForPersistence(stats, null);
    }

    public int normalizeForPersistence(Map<String, Object> stats, Long playerId) {
        int effectiveMaxHp = projectForRead(stats, playerId);
        stats.put("maxHp", effectiveMaxHp);
        stats.put(APPLIED_CONSTELLATION_BONUS_KEY, constellationBonus(stats));
        stats.put(APPLIED_TITLE_BONUS_KEY, titleBonus(playerId));
        int hp = toInt(stats.get("hp"), effectiveMaxHp);
        if (hp > effectiveMaxHp) stats.put("hp", effectiveMaxHp);
        return effectiveMaxHp;
    }

    /** Marks an explicitly assigned maxHp value as already including the current constellation effect. */
    public static void markCurrentBonusAsApplied(Map<String, Object> stats) {
        stats.put(APPLIED_CONSTELLATION_BONUS_KEY, constellationBonus(stats));
    }

    private int titleBonus(Long playerId) {
        if (playerId == null || playerTitleMapper == null || titleMapper == null) return 0;
        PlayerTitle equipped = playerTitleMapper.selectOne(new QueryWrapper<PlayerTitle>()
                .eq("player_id", playerId).eq("equipped", 1));
        if (equipped == null) return 0;
        Title title = titleMapper.selectOne(new QueryWrapper<Title>()
                .eq("title_key", equipped.getTitleKey()).eq("enabled", 1));
        if (title == null) return 0;
        Object value = parseObject(title.getEffectsJson()).get("maxHp");
        return value instanceof Number number ? Math.max(0, Math.min(50, number.intValue())) : 0;
    }

    /** Current maximum-HP contribution from the selected constellation. */
    public static int constellationBonus(Map<String, Object> stats) {
        Object value = stats.get("constellation");
        return value instanceof String key ? CONSTELLATION_MAX_HP.getOrDefault(key, 0) : 0;
    }

    private long inferLegacyPersistentBase(Map<String, Object> stats, int savedMaxHp, int currentConstellationBonus) {
        long storedBase = Math.max(100L, (long) savedMaxHp - currentConstellationBonus);
        long configuredBase = 100L + cumulativeRankMaxHpBonus(stats) + prestigeMaxHpBonus(stats);
        return Math.max(storedBase, configuredBase);
    }

    private long cumulativeRankMaxHpBonus(Map<String, Object> stats) {
        String currentRank = stats.get("avatarRank") instanceof String rank ? rank : "F";
        List<AvatarRankConfig> configs = rankConfigMapper.selectList(
                new QueryWrapper<AvatarRankConfig>().orderByAsc("order_num"));
        if (configs == null || configs.isEmpty()) return 0;

        int currentOrder = Integer.MIN_VALUE;
        for (AvatarRankConfig config : configs) {
            if (currentRank.equals(config.getRankKey()) && config.getOrderNum() != null) {
                currentOrder = config.getOrderNum();
                break;
            }
        }
        if (currentOrder == Integer.MIN_VALUE) return 0;

        long bonus = 0;
        for (AvatarRankConfig config : configs) {
            if (config.getOrderNum() == null || config.getOrderNum() > currentOrder) continue;
            Map<String, Object> rewards = parseObject(config.getRewardsJson());
            Object rawStats = rewards.get("stats");
            if (!(rawStats instanceof Map<?, ?> rankStats)) continue;
            Object rawMaxHp = rankStats.get("maxHp");
            if (rawMaxHp instanceof Number amount) bonus = Math.addExact(bonus, amount.longValue());
        }
        return bonus;
    }

    private long prestigeMaxHpBonus(Map<String, Object> stats) {
        int prestigeLevel = toInt(stats.get("prestigeLevel"), 0);
        if (prestigeLevel < 0) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "回归次数无效，无法计算最大生命");
        }
        return Math.multiplyExact((long) prestigeLevel, 5L);
    }

    private int calculateTotal(long persistentBase, int constellationBonus) {
        return total(persistentBase, constellationBonus);
    }

    private static int total(long persistentBase, int constellationBonus) {
        long bonus = Math.max(0L, persistentBase - 100L) + constellationBonus;
        return ResourceBalance.applyDelta("最大生命", 100, bonus);
    }

    private Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("位阶最大生命奖励配置无法解析", exception);
        }
    }

    private static int toInt(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
