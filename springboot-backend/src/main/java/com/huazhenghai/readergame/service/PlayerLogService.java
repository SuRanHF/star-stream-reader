package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.LogEntry;

import java.util.List;
import java.util.Map;

public interface PlayerLogService {

    void addLog(Long playerId, String type, String message);

    void addLog(Long playerId, String type, String message, Map<String, Object> metadata);

    /** 查找探索请求回执；回执保存在日志元数据中，不作为普通日志元数据返回给客户端。 */
    Map<String, Object> findExplorationReceipt(Long playerId, String requestKey);

    /** 查找剧情选择回执。 */
    Map<String, Object> findStoryChoiceReceipt(Long playerId, String requestKey);

    /** 获取尚未完成战斗的探索遭遇快照。 */
    List<Map<String, Object>> findPendingBattleEncounters(Long playerId);

    /** 标记探索遭遇已完成战斗；重复确认是安全的。 */
    void acknowledgeBattleEncounter(Long playerId, String requestKey);

    List<LogEntry> getRecentLogs(Long playerId, int limit);
}
