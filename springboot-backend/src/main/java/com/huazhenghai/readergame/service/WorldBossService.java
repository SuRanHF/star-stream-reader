package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.dto.CreateWorldBossRequest;
import com.huazhenghai.readergame.vo.*;

import java.util.List;

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

    /** 结算 Boss */
    void settleBoss(String bossNo);

    /** 领取奖励 */
    WorldBossRewardVO claimReward(Long playerId, String bossNo, Long userId);

    /** 我的参与记录 */
    List<WorldBossParticipationVO> getMyParticipation(Long playerId, Long userId);

    /** Boss 伤害排行 */
    List<WorldBossRankingVO> getBossRankings(String bossNo);

    /** World Boss 摘要 */
    WorldBossSummaryVO getWorldBossSummary(Long playerId);
}
