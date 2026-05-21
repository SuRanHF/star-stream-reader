package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.LogEntry;

import java.util.List;
import java.util.Map;

public interface PlayerLogService {

    void addLog(Long playerId, String type, String message);

    void addLog(Long playerId, String type, String message, Map<String, Object> metadata);

    List<LogEntry> getRecentLogs(Long playerId, int limit);
}
