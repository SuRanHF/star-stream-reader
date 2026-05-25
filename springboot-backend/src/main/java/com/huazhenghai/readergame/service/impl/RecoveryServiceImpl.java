package com.huazhenghai.readergame.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.service.RecoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RecoveryServiceImpl implements RecoveryService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryServiceImpl.class);

    private final PlayerMapper playerMapper;
    private final PlayerLogService playerLogService;
    private final ObjectMapper objectMapper;

    // 满恢复时间(秒): 休息模式 HP 2h满，体力 3h满；普通模式 3倍
    private static final int REST_HP_FULL_SECONDS = 2 * 3600;   // 2h
    private static final int REST_STAMINA_FULL_SECONDS = 3 * 3600; // 3h
    private static final int REST_EXP_INTERVAL_SECONDS = 30;     // 休息 30s/1exp
    private static final int NORMAL_MULTIPLIER = 3;

    public RecoveryServiceImpl(PlayerMapper playerMapper,
                               PlayerLogService playerLogService,
                               ObjectMapper objectMapper) {
        this.playerMapper = playerMapper;
        this.playerLogService = playerLogService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> applyRecovery(Player player) {
        Map<String, Object> stats = parseStats(player.getStatsJson());

        int curHp = toInt(stats.get("hp"), 100);
        if (curHp <= 0) return stats;

        long now = System.currentTimeMillis();
        Number lastRecoveryAtNum = (Number) stats.get("lastRecoveryAt");
        long lastRecoveryAt = lastRecoveryAtNum != null ? lastRecoveryAtNum.longValue() : now;
        if (lastRecoveryAt <= 0) lastRecoveryAt = now;

        long elapsedMs = now - lastRecoveryAt;
        if (elapsedMs < 1000) return stats;

        int hpBefore = curHp;
        int staminaBefore = toInt(stats.get("stamina"), 50);
        int expBefore = toInt(stats.get("exp"), 0);
        log.info("[恢复] playerId={} 休息={} 经过秒={} HP前={} 体力前={} 经验前={}",
            player.getId(), Boolean.TRUE.equals(stats.get("isResting")), elapsedMs/1000,
            hpBefore, staminaBefore, expBefore);

        boolean isResting = Boolean.TRUE.equals(stats.get("isResting"));
        int maxStamina = toInt(stats.get("maxStamina"), 50);
        int curStamina = toInt(stats.get("stamina"), 50);

        // 重新计算 maxHp (与 buildPlayerVO 一致: 100 + 背后星加成)
        int constMaxHp = 0;
        String constellation = stats.get("constellation") instanceof String s ? s : null;
        if (constellation != null && !constellation.isEmpty()) {
            Map<String, Object> effects = getConstellationEffects(constellation);
            if (effects != null && effects.get("maxHp") instanceof Number n) {
                constMaxHp = n.intValue();
            }
        }
        int maxHp = 100 + constMaxHp;

        // 上限校验
        if (curHp > maxHp) {
            curHp = maxHp;
            stats.put("hp", curHp);
        }
        stats.put("maxHp", maxHp);

        long elapsedSec = elapsedMs / 1000;

        // 整点恢复: 满恢复时间 / max值 = 每点间隔(秒)
        int hpInterval = Math.max(1, REST_HP_FULL_SECONDS / Math.max(1, maxHp));
        int staminaInterval = Math.max(1, REST_STAMINA_FULL_SECONDS / Math.max(1, maxStamina));
        if (!isResting) {
            hpInterval *= NORMAL_MULTIPLIER;
            staminaInterval *= NORMAL_MULTIPLIER;
        }

        long hpRecovery = elapsedSec / hpInterval;
        if (hpRecovery > 0 && curHp < maxHp) {
            curHp = (int) Math.min(maxHp, curHp + hpRecovery);
            stats.put("hp", curHp);
        }

        long staminaRecovery = elapsedSec / staminaInterval;
        if (staminaRecovery > 0 && curStamina < maxStamina) {
            curStamina = (int) Math.min(maxStamina, curStamina + staminaRecovery);
            stats.put("stamina", curStamina);
        }

        // 经验恢复: 30s/点 (普通模式90s/点)
        int expInterval = REST_EXP_INTERVAL_SECONDS;
        if (!isResting) expInterval *= NORMAL_MULTIPLIER;
        long expRecovery = elapsedSec / expInterval;
        if (expRecovery > 0) {
            int curExp = toInt(stats.get("exp"), 0);
            stats.put("exp", curExp + (int) expRecovery);
        }

        // 只消耗已计算的整秒
        long consumedHpSec = hpRecovery > 0 ? hpRecovery * hpInterval : 0;
        long consumedStaminaSec = staminaRecovery > 0 ? staminaRecovery * staminaInterval : 0;
        long consumedExpSec = expRecovery > 0 ? expRecovery * expInterval : 0;
        long consumedSec = Math.max(Math.max(consumedHpSec, consumedStaminaSec), consumedExpSec);
        stats.put("lastRecoveryAt", lastRecoveryAt + consumedSec * 1000);

        // 暴露当前恢复间隔给前端显示（秒/每+1点）
        stats.put("hpIntervalSeconds", hpInterval);
        stats.put("staminaIntervalSeconds", staminaInterval);
        stats.put("expIntervalSeconds", expInterval);

        log.info("[恢复] playerId={} HP后={} 体力后={} 经验后={} HP恢复={} 体力恢复={} 经验恢复={}",
            player.getId(),
            toInt(stats.get("hp"), 100),
            toInt(stats.get("stamina"), 50),
            toInt(stats.get("exp"), 0),
            hpRecovery, staminaRecovery, expRecovery);

        try {
            player.setStatsJson(objectMapper.writeValueAsString(stats));
        } catch (Exception e) {
            log.error("[恢复] 序列化失败 playerId={}", player.getId(), e);
        }
        playerMapper.updateById(player);

        return stats;
    }

    @Override
    public Map<String, Object> startRest(Long playerId, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        Map<String, Object> stats = parseStats(player.getStatsJson());
        if (Boolean.TRUE.equals(stats.get("isResting")))
            throw new BusinessException(ErrorCode.ALREADY_RESTING, "你已经在休息了。");

        long now = System.currentTimeMillis();
        stats.put("isResting", true);
        stats.put("restStartedAt", now);
        stats.put("lastRecoveryAt", now);

        try {
            player.setStatsJson(objectMapper.writeValueAsString(stats));
        } catch (Exception ignored) {
        }
        playerMapper.updateById(player);

        playerLogService.addLog(playerId, "info", "你进入休息状态，生命与体力恢复速度提高。");

        return stats;
    }

    @Override
    public Map<String, Object> stopRest(Long playerId, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        Map<String, Object> stats = parseStats(player.getStatsJson());
        if (!Boolean.TRUE.equals(stats.get("isResting")))
            throw new BusinessException(ErrorCode.NOT_RESTING, "你当前没有在休息。");

        // 先应用恢复再结束休息
        log.info("[停止休息] playerId={} 调用applyRecovery前 hp={} stamina={} exp={}",
            playerId, toInt(stats.get("hp"), 100), toInt(stats.get("stamina"), 50), toInt(stats.get("exp"), 0));
        stats = applyRecovery(player);

        stats.put("isResting", false);
        stats.put("restStartedAt", null);
        log.info("[停止休息] playerId={} 保存后 hp={} stamina={} exp={}",
            playerId, toInt(stats.get("hp"), 100), toInt(stats.get("stamina"), 50), toInt(stats.get("exp"), 0));

        try {
            player.setStatsJson(objectMapper.writeValueAsString(stats));
        } catch (Exception e) {
            log.error("[停止休息] 序列化失败 playerId={}", playerId, e);
        }
        playerMapper.updateById(player);

        playerLogService.addLog(playerId, "info", "你结束休息。");

        return stats;
    }

    @Override
    public void assertCanAct(Player player) {
        Map<String, Object> stats = parseStats(player.getStatsJson());
        if (Boolean.TRUE.equals(stats.get("isResting"))) {
            throw new BusinessException(ErrorCode.PLAYER_RESTING, "你正在休息，无法进行该操作。");
        }
    }

    // ─── 工具方法 ───

    private Map<String, Object> parseStats(String statsJson) {
        if (statsJson == null || statsJson.isBlank() || "null".equals(statsJson))
            return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(statsJson, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private int toInt(Object val, int defaultVal) {
        if (val instanceof Number) return ((Number) val).intValue();
        return defaultVal;
    }

    // ─── 背后星效果 (与 PlayerServiceImpl 保持一致) ───

    private static final Map<String, Map<String, Object>> CONSTELLATION_EFFECTS = Map.of(
        "demon_judge_of_fire",       Map.of("def", 15, "maxHp", 10),
        "master_of_steel",           Map.of("def", 8, "maxHp", 20),
        "prisoner_of_golden_headband", Map.of("spd", 18, "luck", 4),
        "abyssal_black_flame_dragon", Map.of("atk", 15, "critRate", 0.10),
        "queen_of_darkest_spring",   Map.of("atk", 12, "worldLineShift", 3),
        "father_of_rich_night",      Map.of("atk", 18, "maxHp", 5),
        "scribe_of_heaven",          Map.of("insight", 8, "atk", 5, "def", 5),
        "morning_star",              Map.of("atk", 5, "def", 5, "spd", 5, "maxHp", 5)
    );

    private Map<String, Object> getConstellationEffects(String constellationKey) {
        return CONSTELLATION_EFFECTS.get(constellationKey);
    }
}
