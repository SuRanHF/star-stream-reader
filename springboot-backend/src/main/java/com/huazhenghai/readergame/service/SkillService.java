package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.*;

import java.util.List;
import java.util.Map;

public interface SkillService {

    /** 返回所有已启用技能定义 */
    List<SkillVO> getAllSkills();

    /** 返回玩家已解锁技能 (含技能定义) */
    List<PlayerSkillVO> getPlayerSkills(Long playerId, Long userId);

    /** 评估当前可解锁但尚未解锁的技能 */
    List<SkillVO> evaluateUnlockableSkills(Long playerId);

    /** 解锁技能 (含条件校验、资源扣除、事务) */
    PlayerSkillVO unlockSkill(Long playerId, String skillKey, Long userId);

    /** 计算已解锁技能的被动效果加成 (动态计算，不写入 stats_json) */
    Map<String, Object> calculateSkillBonus(Long playerId);

    /** 技能摘要 (用于 bootstrap) */
    SkillSummaryVO getSkillSummary(Long playerId);
}
