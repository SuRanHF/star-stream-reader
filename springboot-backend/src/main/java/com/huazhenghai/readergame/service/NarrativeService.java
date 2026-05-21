package com.huazhenghai.readergame.service;

import java.util.List;
import java.util.Map;

public interface NarrativeService {

    /** 获取物品的所有记忆 */
    List<Map<String, Object>> getItemMemories(String itemKey);

    /** 获取所有物品记忆 */
    List<Map<String, Object>> getAllItemMemories();

    /** 获取地点回响列表 */
    List<Map<String, Object>> getLocationEchoes(String locationKey);

    /** 记录地点回响（加权随机选一条），未命中返回 null */
    Map<String, Object> recordLocationEcho(Long playerId, String locationKey);

    /** 检查 NPC 残影遭遇，未触发返回 null */
    Map<String, Object> checkNpcGhostEncounter(Long playerId, String locationKey);

    /** 处理 NPC 残影对话选择，返回对话结果 */
    Map<String, Object> processNpcGhostEncounter(Long playerId, String ghostKey, int nodeIndex, int choiceIndex);

    /** 玩家遭遇历史 */
    List<Map<String, Object>> getPlayerEncounters(Long playerId, int limit);
}
