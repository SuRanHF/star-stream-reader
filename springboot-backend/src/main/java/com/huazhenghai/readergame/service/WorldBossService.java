package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.dto.CreateWorldBossRequest;
import com.huazhenghai.readergame.vo.*;

import java.util.List;
import java.util.Map;

public interface WorldBossService {

    /** 获取当前 active Boss */
    WorldBossVO getActiveBoss(Long playerId);

    /** 获取 Boss 详情 */
    WorldBossVO getBossDetail(String bossNo, Long playerId);

    /** Admin 创建 Boss */
    WorldBossVO createBoss(CreateWorldBossRequest req, Long adminUserId);

    /** Scheduler 自动开启 Boss */
    WorldBossVO openScheduledBoss();

    /** 攻击 Boss */
    WorldBossAttackResultVO attackBoss(Long playerId, String bossNo, Long userId);

    /** 攻击 Boss 并通过操作回执支持不确定结果的同键重试。 */
    WorldBossAttackResultVO attackBoss(Long playerId, String bossNo, Long userId, String requestKey);

    /** 队伍攻击 Boss：按当前队伍成员逐个结算，并返回本次队伍贡献明细 */
    Map<String, Object> attackPartyBoss(String partyNo, Long initiatorPlayerId, Long userId);

    /** 结算 Boss */
    void settleBoss(String bossNo);

    /** 领取奖励 */
    WorldBossRewardVO claimReward(Long playerId, String bossNo, Long userId, String requestKey);

    /** 我的参与记录 */
    List<WorldBossParticipationVO> getMyParticipation(Long playerId, Long userId);

    /** 已结束世界Boss历史 */
    List<WorldBossHistoryVO> getHistory();

    /** Boss 伤害排行 */
    List<WorldBossRankingVO> getBossRankings(String bossNo);

    /** World Boss 摘要 */
    WorldBossSummaryVO getWorldBossSummary(Long playerId);
}
