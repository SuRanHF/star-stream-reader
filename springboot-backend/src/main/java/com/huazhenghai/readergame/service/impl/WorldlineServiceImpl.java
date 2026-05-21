package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.entity.WorldState;
import com.huazhenghai.readergame.mapper.WorldStateMapper;
import com.huazhenghai.readergame.service.WorldlineService;
import com.huazhenghai.readergame.vo.WorldlineSummaryVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class WorldlineServiceImpl implements WorldlineService {

    private final WorldStateMapper worldStateMapper;
    private final ObjectMapper objectMapper;

    public WorldlineServiceImpl(WorldStateMapper worldStateMapper,
                                ObjectMapper objectMapper) {
        this.worldStateMapper = worldStateMapper;
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
    public void addWorldlineShift(double amount, String cause) {
        addToStateValue("worldline_shift", "totalShift", amount, cause);
    }

    @Override
    public int applyDecay(double decayRate) {
        int affected = 0;
        for (String stateKey : ALL_STATE_KEYS) {
            QueryWrapper<WorldState> qw = new QueryWrapper<>();
            qw.eq("state_key", stateKey);
            WorldState state = worldStateMapper.selectOne(qw);
            if (state == null) continue;

            Map<String, Object> valueMap = parseJsonMap(state.getValueJson());
            boolean changed = false;
            for (Map.Entry<String, Object> entry : new ArrayList<>(valueMap.entrySet())) {
                Object val = entry.getValue();
                if (val instanceof Number) {
                    double current = ((Number) val).doubleValue();
                    if (current == 0) continue;
                    double decayed = current * (1.0 - decayRate);
                    if (Math.abs(current - decayed) < 0.001) continue;
                    valueMap.put(entry.getKey(), Math.round(decayed * 1000.0) / 1000.0);
                    changed = true;
                }
            }
            if (!changed) continue;

            try {
                String newValueJson = objectMapper.writeValueAsString(valueMap);
                UpdateWrapper<WorldState> uw = new UpdateWrapper<>();
                uw.eq("state_key", stateKey)
                  .set("value_json", newValueJson)
                  .set("updated_at", LocalDateTime.now());
                worldStateMapper.update(null, uw);
                affected++;
            } catch (Exception ignored) {
            }
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
        QueryWrapper<WorldState> qw = new QueryWrapper<>();
        qw.eq("state_key", stateKey);
        WorldState state = worldStateMapper.selectOne(qw);

        if (state == null) return;

        Map<String, Object> valueMap = parseJsonMap(state.getValueJson());
        double current = toDouble(valueMap.get(valueKey), 0.0);
        valueMap.put(valueKey, current + amount);

        // Update danger level if scenario_pressure changed
        if ("scenario_pressure".equals(stateKey) && "pressure".equals(valueKey)) {
            int newDangerLevel = calcDangerLevel(current + amount);
            valueMap.put("dangerLevel", newDangerLevel);
        }

        try {
            String newValueJson = objectMapper.writeValueAsString(valueMap);
            UpdateWrapper<WorldState> uw = new UpdateWrapper<>();
            uw.eq("state_key", stateKey)
              .set("value_json", newValueJson)
              .set("updated_by_event_key", updatedBy)
              .set("updated_at", LocalDateTime.now());
            worldStateMapper.update(null, uw);
        } catch (Exception ignored) {
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
}
