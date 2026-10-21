package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.entity.BroadcastEvent;
import com.huazhenghai.readergame.entity.WorldState;
import com.huazhenghai.readergame.entity.WorldlineChangeLog;
import com.huazhenghai.readergame.entity.WorldBoss;
import com.huazhenghai.readergame.mapper.BroadcastEventMapper;
import com.huazhenghai.readergame.mapper.WorldlineChangeLogMapper;
import com.huazhenghai.readergame.mapper.WorldStateMapper;
import com.huazhenghai.readergame.mapper.WorldBossMapper;
import com.huazhenghai.readergame.service.WorldlineService;
import com.huazhenghai.readergame.vo.WorldlineSummaryVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class WorldlineServiceImpl implements WorldlineService {

    private final WorldStateMapper worldStateMapper;
    private final WorldlineChangeLogMapper changeLogMapper;
    private final BroadcastEventMapper broadcastEventMapper;
    private final WorldBossMapper worldBossMapper;
    private final ObjectMapper objectMapper;

    public WorldlineServiceImpl(WorldStateMapper worldStateMapper,
                                WorldlineChangeLogMapper changeLogMapper,
                                BroadcastEventMapper broadcastEventMapper,
                                WorldBossMapper worldBossMapper,
                                ObjectMapper objectMapper) {
        this.worldStateMapper = worldStateMapper;
        this.changeLogMapper = changeLogMapper;
        this.broadcastEventMapper = broadcastEventMapper;
        this.worldBossMapper = worldBossMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Map<String, Object>> getWorldStates() {
        List<WorldState> states = worldStateMapper.selectList(null);
        List<Map<String, Object>> result = new ArrayList<>();
        for (WorldState ws : states) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("stateKey", ws.getStateKey());
            entry.put("name", ws.getName());
            entry.put("description", ws.getDescription());
            entry.put("value", parseJsonMap(ws.getValueJson()));
            entry.put("level", ws.getLevel());
            entry.put("status", ws.getStatus());
            entry.put("updatedAt", ws.getUpdatedAt() != null ? ws.getUpdatedAt().toString() : null);
            result.add(entry);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getWorldlineHistory() {
        QueryWrapper<WorldlineChangeLog> qw = new QueryWrapper<>();
        qw.orderByDesc("created_at").last("LIMIT 100");
        List<WorldlineChangeLog> logs = changeLogMapper.selectList(qw);

        Set<String> broadcastEventKeys = new LinkedHashSet<>();
        Set<String> bossNumbers = new LinkedHashSet<>();
        for (WorldlineChangeLog log : logs) {
            String cause = log.getCause();
            String bossNo = getBossNoFromCause(cause);
            if (bossNo != null) {
                bossNumbers.add(bossNo);
            } else if (cause != null && !cause.isBlank()) {
                broadcastEventKeys.add(cause);
            }
        }

        Map<String, String> broadcastTitles = new HashMap<>();
        if (!broadcastEventKeys.isEmpty()) {
            QueryWrapper<BroadcastEvent> eventQuery = new QueryWrapper<>();
            eventQuery.in("event_key", broadcastEventKeys);
            for (BroadcastEvent event : broadcastEventMapper.selectList(eventQuery)) {
                broadcastTitles.put(event.getEventKey(), event.getTitle());
            }
        }

        Map<String, String> bossNames = new HashMap<>();
        if (!bossNumbers.isEmpty()) {
            QueryWrapper<WorldBoss> bossQuery = new QueryWrapper<>();
            bossQuery.in("boss_no", bossNumbers);
            for (WorldBoss boss : worldBossMapper.selectList(bossQuery)) {
                bossNames.put(boss.getBossNo(), boss.getName());
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (WorldlineChangeLog log : logs) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", log.getId());
            entry.put("stateKey", log.getStateKey());
            entry.put("stateName", log.getStateName());
            entry.put("valueKey", log.getValueKey());
            entry.put("changeAmount", log.getChangeAmount());
            entry.put("beforeValue", log.getBeforeValue());
            entry.put("afterValue", log.getAfterValue());
            entry.put("cause", log.getCause());
            entry.put("causeDisplay", resolveCauseDisplay(log.getCause(), broadcastTitles, bossNames));
            entry.put("createdAt", log.getCreatedAt() != null ? log.getCreatedAt().toString() : null);
            result.add(entry);
        }
        return result;
    }

    private String resolveCauseDisplay(String cause, Map<String, String> broadcastTitles,
                                       Map<String, String> bossNames) {
        if (cause == null || cause.isBlank()) return "公共世界事件影响";

        if (cause.startsWith("world_boss_expired_")) {
            String bossNo = cause.substring("world_boss_expired_".length());
            String bossName = bossNames.get(bossNo);
            String bossLabel = bossName == null || bossName.isBlank() ? "" : "「" + bossName + "」";
            return "世界首领" + bossLabel + "未能及时击破";
        }
        if (cause.startsWith("world_boss_")) {
            String bossNo = cause.substring("world_boss_".length());
            String bossName = bossNames.get(bossNo);
            String bossLabel = bossName == null || bossName.isBlank() ? "" : "「" + bossName + "」";
            return "世界首领" + bossLabel + "被击破";
        }

        String eventTitle = broadcastTitles.get(cause);
        if (eventTitle != null && !eventTitle.isBlank()) {
            return "全服活动「" + eventTitle + "」达成";
        }
        return "全服活动结算的公共影响";
    }

    private String getBossNoFromCause(String cause) {
        if (cause == null) return null;
        String prefix = cause.startsWith("world_boss_expired_")
                ? "world_boss_expired_"
                : cause.startsWith("world_boss_") ? "world_boss_" : null;
        if (prefix == null) return null;
        String bossNo = cause.substring(prefix.length());
        return bossNo.isBlank() ? null : bossNo;
    }

    @Override
    public WorldlineSummaryVO getWorldlineSummary() {
        WorldlineSummaryVO vo = new WorldlineSummaryVO();

        Map<String, Object> shiftState = getStateValue("worldline_shift");
        Map<String, Object> attentionState = getStateValue("starstream_attention");
        Map<String, Object> pressureState = getStateValue("scenario_pressure");

        double worldlineShift = toDouble(shiftState.get("totalShift"), 0.0);
        double starstreamAttention = toDouble(attentionState.get("attention"), 0.0);
        double scenarioPressure = toDouble(pressureState.get("pressure"), 0.0);

        int dangerLevel = calcDangerLevel(scenarioPressure);

        vo.setWorldlineShift(worldlineShift);
        vo.setStarstreamAttention(starstreamAttention);
        vo.setScenarioPressure(scenarioPressure);
        vo.setDangerLevel(dangerLevel);

        return vo;
    }

    @Override
    @Transactional
    public void applyBroadcastWorldlineEffects(String eventKey, String effectsJson) {
        Map<String, Object> effects = parseJsonMap(effectsJson);
        if (effects.isEmpty()) return;

        // Apply worldline_shift
        if (effects.containsKey("worldline_shift")) {
            double addShift = toDouble(effects.get("worldline_shift"), 0.0);
            addToStateValue("worldline_shift", "totalShift", addShift, eventKey);
        }

        // Apply starstream_attention
        if (effects.containsKey("starstream_attention")) {
            double addAttention = toDouble(effects.get("starstream_attention"), 0.0);
            addToStateValue("starstream_attention", "attention", addAttention, eventKey);
        }

        // Apply scenario_pressure
        if (effects.containsKey("scenario_pressure")) {
            double addPressure = toDouble(effects.get("scenario_pressure"), 0.0);
            addToStateValue("scenario_pressure", "pressure", addPressure, eventKey);
        }
    }

    @Override
    @Transactional
    public void addWorldlineShift(double amount, String cause) {
        addToStateValue("worldline_shift", "totalShift", amount, cause);
    }

    @Override
    @Transactional
    public int applyDecay(double decayRate) {
        if (!Double.isFinite(decayRate) || decayRate < 0 || decayRate > 1) {
            throw new IllegalArgumentException("世界状态衰减比例必须在 0 到 1 之间");
        }
        int affected = 0;
        for (String stateKey : ALL_STATE_KEYS) {
            WorldState state = worldStateMapper.selectByStateKeyForUpdate(stateKey);
            if (state == null) continue;

            Map<String, Object> valueMap = parseJsonMap(state.getValueJson());
            boolean changed = false;
            String metricKey = DECAYABLE_METRIC_KEYS.get(stateKey);
            Object metricValue = valueMap.get(metricKey);
            if (metricValue instanceof Number number) {
                double current = number.doubleValue();
                if (current != 0) {
                    double decayed = current * (1.0 - decayRate);
                    if (Math.abs(current - decayed) >= 0.001) {
                        valueMap.put(metricKey, Math.round(decayed * 1000.0) / 1000.0);
                        changed = true;
                    }
                }
            }

            // dangerLevel is a derived integer tier, not a continuous metric.
            // Keep it aligned with the persisted pressure without decaying it.
            if ("scenario_pressure".equals(stateKey)
                    && valueMap.get("pressure") instanceof Number pressure) {
                int derivedDangerLevel = calcDangerLevel(pressure.doubleValue());
                Object currentDangerLevel = valueMap.get("dangerLevel");
                if (!(currentDangerLevel instanceof Number number)
                        || number.intValue() != derivedDangerLevel
                        || number.doubleValue() != derivedDangerLevel) {
                    valueMap.put("dangerLevel", derivedDangerLevel);
                    changed = true;
                }
            }
            if (!changed) continue;

            String newValueJson;
            try {
                newValueJson = objectMapper.writeValueAsString(valueMap);
            } catch (Exception e) {
                throw new IllegalStateException("共享世界状态序列化失败: " + stateKey, e);
            }
            UpdateWrapper<WorldState> uw = new UpdateWrapper<>();
            uw.eq("state_key", stateKey)
              .set("value_json", newValueJson)
              .set("updated_at", LocalDateTime.now());
            if (worldStateMapper.update(null, uw) <= 0) {
                throw new IllegalStateException("共享世界状态衰减写入失败: " + stateKey);
            }
            affected++;
        }
        return affected;
    }

    // ─── internal ───

    private Map<String, Object> getStateValue(String stateKey) {
        QueryWrapper<WorldState> qw = new QueryWrapper<>();
        qw.eq("state_key", stateKey);
        WorldState state = worldStateMapper.selectOne(qw);
        return parseJsonMap(state != null ? state.getValueJson() : null);
    }

    private void addToStateValue(String stateKey, String valueKey, double amount, String updatedBy) {
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException("共享世界状态变化量必须是有限数值");
        }
        WorldState state = worldStateMapper.selectByStateKeyForUpdate(stateKey);

        if (state == null) {
            throw new IllegalStateException("共享世界状态不存在: " + stateKey);
        }

        Map<String, Object> valueMap = parseJsonMap(state.getValueJson());
        double current = toDouble(valueMap.get(valueKey), 0.0);
        valueMap.put(valueKey, current + amount);

        // Update danger level if scenario_pressure changed
        if ("scenario_pressure".equals(stateKey) && "pressure".equals(valueKey)) {
            int newDangerLevel = calcDangerLevel(current + amount);
            valueMap.put("dangerLevel", newDangerLevel);
        }

        String newValueJson;
        try {
            newValueJson = objectMapper.writeValueAsString(valueMap);
        } catch (Exception e) {
            throw new IllegalStateException("共享世界状态序列化失败: " + stateKey, e);
        }
        UpdateWrapper<WorldState> uw = new UpdateWrapper<>();
        uw.eq("state_key", stateKey)
          .set("value_json", newValueJson)
          .set("updated_by_event_key", updatedBy)
          .set("updated_at", LocalDateTime.now());
        if (worldStateMapper.update(null, uw) <= 0) {
            throw new IllegalStateException("共享世界状态写入失败: " + stateKey);
        }
        if (Math.abs(amount) >= 0.000001) {
            writeChangeLog(state, valueKey, amount, current, current + amount, updatedBy);
        }
    }

    private void writeChangeLog(WorldState state, String valueKey, double changeAmount,
                                double beforeValue, double afterValue, String cause) {
        WorldlineChangeLog log = new WorldlineChangeLog();
        log.setStateKey(state.getStateKey());
        log.setStateName(state.getName());
        log.setValueKey(valueKey);
        log.setChangeAmount(changeAmount);
        log.setBeforeValue(beforeValue);
        log.setAfterValue(afterValue);
        log.setCause(cause);
        log.setCreatedAt(LocalDateTime.now());
        if (changeLogMapper.insert(log) <= 0) {
            throw new IllegalStateException("共享世界状态变更历史写入失败: " + state.getStateKey());
        }
    }

    private int calcDangerLevel(double pressure) {
        if (pressure <= 20) return 1;
        if (pressure <= 50) return 2;
        if (pressure <= 100) return 3;
        return 4;
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank() || "null".equals(json))
            return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private double toDouble(Object val, double defaultVal) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        return defaultVal;
    }

    private static final List<String> ALL_STATE_KEYS = List.of(
            "worldline_shift", "starstream_attention", "scenario_pressure"
    );

    private static final Map<String, String> DECAYABLE_METRIC_KEYS = Map.of(
            "worldline_shift", "totalShift",
            "starstream_attention", "attention",
            "scenario_pressure", "pressure"
    );
}
