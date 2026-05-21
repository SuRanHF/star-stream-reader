package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.entity.PlayerLog;
import com.huazhenghai.readergame.mapper.PlayerLogMapper;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.vo.LogEntry;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PlayerLogServiceImpl implements PlayerLogService {

    private final PlayerLogMapper playerLogMapper;
    private final ObjectMapper objectMapper;

    public PlayerLogServiceImpl(PlayerLogMapper playerLogMapper, ObjectMapper objectMapper) {
        this.playerLogMapper = playerLogMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void addLog(Long playerId, String type, String message) {
        addLog(playerId, type, message, null);
    }

    @Override
    public void addLog(Long playerId, String type, String message, Map<String, Object> metadata) {
        PlayerLog log = new PlayerLog();
        log.setPlayerId(playerId);
        log.setType(type);
        log.setMessage(message);
        if (metadata != null) {
            try {
                log.setMetadataJson(objectMapper.writeValueAsString(metadata));
            } catch (JsonProcessingException ignored) {
            }
        }
        playerLogMapper.insert(log);
    }

    @Override
    public List<LogEntry> getRecentLogs(Long playerId, int limit) {
        QueryWrapper<PlayerLog> query = new QueryWrapper<>();
        query.eq("player_id", playerId)
             .orderByDesc("created_at")
             .last("LIMIT " + limit);
        List<PlayerLog> logs = playerLogMapper.selectList(query);

        List<LogEntry> result = new ArrayList<>();
        for (PlayerLog log : logs) {
            result.add(new LogEntry(
                    log.getId(),
                    log.getMessage(),
                    log.getType(),
                    log.getCreatedAt()
            ));
        }
        Collections.reverse(result);
        return result;
    }
}
