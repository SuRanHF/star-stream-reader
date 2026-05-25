package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.*;

import java.util.List;

public interface FactionService {

    /** 获取所有 enabled 阵营 */
    List<FactionVO> getAllFactions(Long playerId);

    /** 获取阵营详情 */
    FactionVO getFactionDetail(String factionKey, Long playerId);

    /** 获取我的阵营 */
    PlayerFactionVO getMyFaction(Long playerId, Long userId);

    /** 加入阵营 */
    PlayerFactionVO joinFaction(Long playerId, String factionKey, Long userId);

    /** 退出阵营 */
    void leaveFaction(Long playerId, Long userId);

    /** 贡献阵营 */
    FactionContributionVO contribute(Long playerId, String factionKey, String contributionType,
                                     Long value, String source, String relatedId, Long userId);

    /** 阵营排行榜 */
    List<FactionRankingVO> getFactionRankings();

    /** 阵营内玩家贡献排行 */
    List<FactionRankingVO> getFactionContributionRankings(String factionKey);

    /** 阵营技能定义 */
    List<FactionSkillVO> getFactionSkills(String factionKey);

    /** 阵营战列表 */
    List<FactionWarVO> getFactionWars();

    /** 阵营摘要 */
    FactionSummaryVO getFactionSummary(Long playerId);

    /** 阵营成员列表 */
    java.util.List<java.util.Map<String, Object>> getFactionMembers(String factionKey, int limit);

    /** 获取阵营加成（当前玩家的攻击加成值） */
    java.util.Map<String, Object> getFactionBuff(Long playerId);

    /** 每日结算阵营贡献，计算并存储各玩家的阵营加成 */
    java.util.Map<String, Object> settleFactionDaily();
}
