package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.*;

import java.util.List;
import java.util.Map;

public interface CombatService {

    List<MonsterVO> getAllMonsters();

    List<MonsterVO> getMonstersByLocation(String locationKey);

    CombatResultVO startCombat(Long playerId, String monsterKey, Long userId);

    List<BattleLogVO> getBattleLogs(Long playerId, Long userId);

    CombatStatsVO calculateCombatPower(Long playerId);

    Map<String, Object> resolveCombat(Long playerId, String monsterKey, String action, Long userId);
}
