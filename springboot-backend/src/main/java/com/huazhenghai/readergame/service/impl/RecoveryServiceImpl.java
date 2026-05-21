package com.huazhenghai.readergame.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.service.RecoveryService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RecoveryServiceImpl implements RecoveryService {

    private final PlayerMapper playerMapper;
    private final PlayerLogService playerLogService;
    private final ObjectMapper objectMapper;

    // 普通恢复间隔(秒)
    private static final int NORMAL_STAMINA_INTERVAL = 60;
    private static final int NORMAL_HP_INTERVAL = 120;
    // 休息恢复间隔(秒)
    private static final int REST_STAMINA_INTERVAL = 30;
    private static final int REST_HP_INTERVAL = 60;

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

        Integer hp = toInt(stats.get("hp"), 100);
        if (hp != null && hp <= 0) return stats;

        long now = System.currentTimeMillis();
        Number lastRecoveryAtNum = (Number) stats.get("lastRecoveryAt");
        long lastRecoveryAt = lastRecoveryAtNum != null ? lastRecoveryAtNum.longValue() : now;
        if (lastRecoveryAt <= 0) lastRecoveryAt = now;

        long elapsedMs = now - lastRecoveryAt;
        if (elapsedMs < 1000) return stats;

        boolean isResting = Boolean.TRUE.equals(stats.get("isResting"));
        int maxHp = toInt(stats.get("maxHp"), 100);
        int maxStamina = toInt(stats.get("maxStamina"), 50);
        int curStamina = toInt(stats.get("stamina"), 50);
        int curHp = toInt(stats.get("hp"), maxHp);

        long elapsedSec = elapsedMs / 1000;

        // 体力恢复
        int staminaInterval = isResting ? REST_STAMINA_INTERVAL : NORMAL_STAMINA_INTERVAL;
        long staminaRecovery = elapsedSec / staminaInterval;
        if (staminaRecovery > 0 && curStamina < maxStamina) {
            curStamina = (int) Math.min(maxStamina, curStamina + staminaRecovery);
            stats.put("stamina", curStamina);
        }

        // HP恢复
        int hpInterval = isResting ? REST_HP_INTERVAL : NORMAL_HP_INTERVAL;
        long hpRecoveryTicks = elapsedSec / hpInterval;
        if (hpRecoveryTicks > 0 && curHp < maxHp) {
            int pointsPerTick = isResting ? 2 : 1;
            long totalRecovery = hpRecoveryTicks * pointsPerTick;
            curHp = (int) Math.min(maxHp, curHp + totalRecovery);
            stats.put("hp", curHp);
        }

        // 对齐 lastRecoveryAt: 只消耗已计算的秒数
        long consumedStaminaSec = staminaRecovery > 0 ? staminaRecovery * staminaInterval : 0;
        long consumedHpSec = hpRecoveryTicks > 0 ? hpRecoveryTicks * hpInterval : 0;
        long consumedSec = Math.max(consumedStaminaSec, consumedHpSec);
        stats.put("lastRecoveryAt", lastRecoveryAt + consumedSec * 1000);

        // 保存更新后的stats
        try {
            player.setStatsJson(objectMapper.writeValueAsString(stats));
        } catch (Exception ignored) {
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
        stats = applyRecovery(player);

        stats.put("isResting", false);
        stats.put("restStartedAt", null);

        try {
            player.setStatsJson(objectMapper.writeValueAsString(stats));
        } catch (Exception ignored) {
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
}
