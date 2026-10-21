package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.ResourceBalance;
import com.huazhenghai.readergame.entity.PkChallenge;
import com.huazhenghai.readergame.entity.PkRecord;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.Ranking;
import com.huazhenghai.readergame.mapper.PkChallengeMapper;
import com.huazhenghai.readergame.mapper.PkRecordMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.RankingMapper;
import com.huazhenghai.readergame.service.*;
import com.huazhenghai.readergame.vo.CombatStatsVO;
import com.huazhenghai.readergame.vo.PkRecordVO;
import com.huazhenghai.readergame.vo.PkChallengeVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class PkServiceImpl implements PkService {

    private final PlayerMapper playerMapper;
    private final PkChallengeMapper challengeMapper;
    private final PkRecordMapper recordMapper;
    private final RankingMapper rankingMapper;
    private final CombatService combatService;
    private final RecoveryService recoveryService;
    private final PlayerLogService playerLogService;
    private final BroadcastService broadcastService;
    private final FactionService factionService;
    private final QuestService questService;
    private final OnlinePlayerService onlinePlayerService;
    private final AvatarRankService avatarRankService;
    private final ObjectMapper objectMapper;
    private final PlayerActionReceiptService actionReceiptService;
    private final PlayerResourceTransactionService resourceTransactionService;
    private final Random random = new Random();

    private static final int MAX_ROUNDS = 30;
    private static final int K_FACTOR = 32;

    public PkServiceImpl(PlayerMapper playerMapper,
                         PkChallengeMapper challengeMapper,
                         PkRecordMapper recordMapper,
                         RankingMapper rankingMapper,
                         CombatService combatService,
                         RecoveryService recoveryService,
                         PlayerLogService playerLogService,
                         BroadcastService broadcastService,
                         FactionService factionService,
                         QuestService questService,
                         OnlinePlayerService onlinePlayerService,
                         AvatarRankService avatarRankService,
                         ObjectMapper objectMapper,
                         PlayerActionReceiptService actionReceiptService,
                         PlayerResourceTransactionService resourceTransactionService) {
        this.playerMapper = playerMapper;
        this.challengeMapper = challengeMapper;
        this.recordMapper = recordMapper;
        this.rankingMapper = rankingMapper;
        this.combatService = combatService;
        this.recoveryService = recoveryService;
        this.playerLogService = playerLogService;
        this.broadcastService = broadcastService;
        this.factionService = factionService;
        this.questService = questService;
        this.onlinePlayerService = onlinePlayerService;
        this.avatarRankService = avatarRankService;
        this.objectMapper = objectMapper;
        this.actionReceiptService = actionReceiptService;
        this.resourceTransactionService = resourceTransactionService;
    }

    @Override
    public List<Map<String, Object>> getOpponents(Long playerId, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        Ranking ownRanking = getRankingEntity(playerId);
        int ownRating = ownRanking != null ? ownRanking.getRating() : 1000;
        int ownPower = estimateCombatPower(combatService.calculateCombatPower(playerId));

        // 查询所有其他玩家
        QueryWrapper<Player> pq = new QueryWrapper<>();
        pq.ne("id", playerId);
        List<Player> allPlayers = playerMapper.selectList(pq);

        // 查询待处理挑战(排除已被挑战者)
        QueryWrapper<PkChallenge> cq = new QueryWrapper<>();
        cq.eq("status", "pending")
          .and(w -> w.eq("attacker_id", playerId).or().eq("defender_id", playerId));
        List<PkChallenge> pendingChallenges = challengeMapper.selectList(cq);
        Set<Long> pendingOpponentIds = new HashSet<>();
        for (PkChallenge ch : pendingChallenges) {
            if (ch.getAttackerId().equals(playerId)) pendingOpponentIds.add(ch.getDefenderId());
            else pendingOpponentIds.add(ch.getAttackerId());
        }

        List<Player> availablePlayers = new ArrayList<>();
        for (Player p : allPlayers) {
            if (pendingOpponentIds.contains(p.getId())) continue;
            if (!onlinePlayerService.isOnline(p.getId())) continue;
            availablePlayers.add(p);
        }

        Map<Long, Ranking> rankingsByPlayerId = new HashMap<>();
        if (!availablePlayers.isEmpty()) {
            QueryWrapper<Ranking> rq = new QueryWrapper<>();
            rq.in("player_id", availablePlayers.stream().map(Player::getId).toList());
            for (Ranking ranking : rankingMapper.selectList(rq)) {
                rankingsByPlayerId.put(ranking.getPlayerId(), ranking);
            }
        }

        List<RecommendedOpponent> opponents = new ArrayList<>();
        for (Player p : availablePlayers) {
            Map<String, Object> stats = parseJsonMap(p.getStatsJson());
            CombatStatsVO combatStats = combatService.calculateCombatPower(p.getId());
            Ranking ranking = rankingsByPlayerId.get(p.getId());
            int rating = ranking != null ? ranking.getRating() : 1000;
            int combatPower = estimateCombatPower(combatStats);
            OpponentMatchmaking.Assessment assessment = OpponentMatchmaking.assess(
                    ownRating, ownPower, rating, combatPower);

            Map<String, Object> opp = new LinkedHashMap<>();
            opp.put("id", p.getId());
            opp.put("player_name", p.getPlayerName());
            opp.put("level", toInt(stats.get("level"), 1));
            opp.put("avatarRank", Objects.toString(stats.get("avatarRank"), "F"));
            opp.put("ranked", ranking != null);
            opp.put("rating", rating);
            opp.put("wins", ranking != null ? ranking.getWins() : 0);
            opp.put("losses", ranking != null ? ranking.getLosses() : 0);
            opp.put("combat_power", combatPower);
            opp.put("match_label", assessment.label());
            opp.put("rating_gap", rating - ownRating);
            opp.put("power_gap_percent", assessment.powerGapPercent());
            opponents.add(new RecommendedOpponent(opp, assessment));
        }

        // 先推荐位阶分数和实际战力都接近的玩家；仍保留越级挑战，不把选择锁死。
        opponents.sort(Comparator
                .comparingInt((RecommendedOpponent candidate) -> candidate.assessment().band())
                .thenComparingInt(candidate -> candidate.assessment().ratingGap())
                .thenComparingInt(candidate -> candidate.assessment().powerGapPercent())
                .thenComparing(candidate -> (Long) candidate.payload().get("id")));
        if (opponents.size() > 20) {
            opponents = opponents.subList(0, 20);
        }
        return opponents.stream().map(RecommendedOpponent::payload).toList();
    }

    @Override
    @Transactional
    public Map<String, Object> createChallenge(Long attackerId, Long defenderId, String mode, Long userId) {
        if (attackerId.equals(defenderId))
            throw new BusinessException(ErrorCode.SELF_CHALLENGE, "不能挑战自己");

        Player attacker = playerMapper.selectById(attackerId);
        Player defender = playerMapper.selectById(defenderId);
        if (attacker == null || defender == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!attacker.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        if (mode == null || mode.isBlank()) mode = "spar";
        if (!List.of("spar", "deathmatch").contains(mode))
            throw new BusinessException(ErrorCode.INVALID_PK_MODE, "无效的PK模式，可选: spar, deathmatch");

        // 检查攻击者休息状态
        recoveryService.assertCanAct(attacker);
        assertAliveForPk(parseJsonMap(attacker.getStatsJson()));
        assertAliveForPk(parseJsonMap(defender.getStatsJson()));

        // 检查是否有待处理的挑战
        QueryWrapper<PkChallenge> eq = new QueryWrapper<>();
        eq.eq("attacker_id", attackerId)
          .eq("defender_id", defenderId)
          .eq("status", "pending");
        if (challengeMapper.selectCount(eq) > 0)
            throw new BusinessException(ErrorCode.DUPLICATE_CHALLENGE, "已有待处理的挑战");

        PkChallenge challenge = new PkChallenge();
        challenge.setAttackerId(attackerId);
        challenge.setDefenderId(defenderId);
        challenge.setStatus("pending");
        challenge.setMode(mode);
        challenge.setCreatedAt(LocalDateTime.now());
        challengeMapper.insert(challenge);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("challengeId", challenge.getId());
        result.put("message", "挑战已发出，等待对方回应");
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> resolveChallenge(Long challengeId, boolean accept, Long playerId,
                                                Long userId, String requestKey) {
        PkChallenge challenge = challengeMapper.selectByIdForUpdate(challengeId);
        if (challenge == null)
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND, "挑战不存在或已过期");

        if (!challenge.getDefenderId().equals(playerId))
            throw new BusinessException(ErrorCode.NOT_YOUR_CHALLENGE, "这不是发给你的挑战");

        Player defender = playerMapper.selectById(playerId);
        if (defender == null || !defender.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        Map<String, Object> requestPayload = new LinkedHashMap<>();
        requestPayload.put("challengeId", challengeId);
        requestPayload.put("accept", accept);
        requestPayload.put("playerId", playerId);
        @SuppressWarnings("unchecked")
        Map<String, Object> replay = actionReceiptService.findReplay(
                playerId, "pk.challenge.resolve", requestKey, requestPayload, Map.class);
        if (replay != null) return replay;
        if (!"pending".equals(challenge.getStatus()))
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND, "挑战不存在或已过期");

        challenge.setResolvedAt(LocalDateTime.now());

        if (!accept) {
            challenge.setStatus("rejected");
            if (challengeMapper.updateById(challenge) <= 0)
                throw new IllegalStateException("保存PK拒绝状态失败");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("accepted", false);
            result.put("message", "已拒绝挑战");
            actionReceiptService.record(playerId, "pk.challenge.resolve", requestKey,
                    requestPayload, result);
            return result;
        }

        challenge.setStatus("accepted");
        if (challengeMapper.updateById(challenge) <= 0)
            throw new IllegalStateException("保存PK接受状态失败");

        // 执行PK战斗
        Map<String, Object> battleResult = executePkBattle(
                challengeId, challenge.getAttackerId(), challenge.getDefenderId(), challenge.getMode());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", true);
        result.put("battle", battleResult);
        actionReceiptService.record(playerId, "pk.challenge.resolve", requestKey,
                requestPayload, result);
        return result;
    }

    @Override
    public List<PkRecordVO> getPKRecords(Long playerId, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看此玩家");

        QueryWrapper<PkRecord> qw = new QueryWrapper<>();
        qw.eq("attacker_id", playerId).or().eq("defender_id", playerId)
          .orderByDesc("created_at")
          .last("LIMIT 20");
        List<PkRecord> records = recordMapper.selectList(qw);

        List<PkRecordVO> result = new ArrayList<>();
        for (PkRecord r : records) {
            PkRecordVO vo = new PkRecordVO();
            vo.setId(r.getId());
            vo.setAttackerName(getPlayerName(r.getAttackerId()));
            vo.setDefenderName(getPlayerName(r.getDefenderId()));
            vo.setWinnerId(r.getWinnerId());
            vo.setLoserId(r.getLoserId());
            vo.setBattleData(parseJsonMap(r.getBattleDataJson()));
            vo.setRatingChange(parseJsonMap(r.getRatingChangeJson()));
            vo.setCreatedAt(r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<PkChallengeVO> getPendingChallenges(Long playerId, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null) {
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        }
        if (!player.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看此玩家的挑战");
        }

        QueryWrapper<PkChallenge> query = new QueryWrapper<>();
        query.eq("defender_id", playerId)
                .eq("status", "pending")
                .orderByDesc("created_at")
                .last("LIMIT 20");
        List<PkChallengeVO> result = new ArrayList<>();
        for (PkChallenge challenge : challengeMapper.selectList(query)) {
            PkChallengeVO vo = new PkChallengeVO();
            vo.setId(challenge.getId());
            vo.setAttackerId(challenge.getAttackerId());
            vo.setAttackerName(getPlayerName(challenge.getAttackerId()));
            vo.setDefenderId(challenge.getDefenderId());
            vo.setDefenderName(getPlayerName(challenge.getDefenderId()));
            vo.setStatus(challenge.getStatus());
            vo.setMode(challenge.getMode());
            vo.setCreatedAt(challenge.getCreatedAt() == null ? null : challenge.getCreatedAt().toString());
            vo.setResolvedAt(challenge.getResolvedAt() == null ? null : challenge.getResolvedAt().toString());
            result.add(vo);
        }
        return result;
    }

    // ─── 内部方法 ───

    /**
     * 执行PK战斗核心逻辑.
     */
    private Map<String, Object> executePkBattle(Long challengeId, Long attackerId,
                                                Long defenderId, String mode) {
        Player attacker;
        Player defender;
        if (Long.compare(attackerId, defenderId) < 0) {
            attacker = playerMapper.selectByIdForUpdate(attackerId);
            defender = playerMapper.selectByIdForUpdate(defenderId);
        } else {
            defender = playerMapper.selectByIdForUpdate(defenderId);
            attacker = playerMapper.selectByIdForUpdate(attackerId);
        }
        if (attacker == null || defender == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");

        // 应用恢复 & 检查休息
        Map<String, Object> atkStats = recoveryService.applyRecovery(attacker);
        Map<String, Object> defStats = recoveryService.applyRecovery(defender);
        recoveryService.assertCanAct(attacker);
        recoveryService.assertCanAct(defender);
        assertAliveForPk(atkStats);
        assertAliveForPk(defStats);

        // 计算战斗属性
        CombatStatsVO atkPower = combatService.calculateCombatPower(attackerId);
        CombatStatsVO defPower = combatService.calculateCombatPower(defenderId);

        double atkHp = atkPower.getHp();
        double defHp = defPower.getHp();

        // 战斗模拟
        List<Map<String, Object>> rounds = new ArrayList<>();
        boolean attackerFirst = atkPower.getSpeed() >= defPower.getSpeed();
        String result = "draw";

        for (int i = 0; i < MAX_ROUNDS; i++) {
            Map<String, Object> round = new LinkedHashMap<>();
            round.put("round", i + 1);

            if (attackerFirst) {
                Map<String, Object> aDmg = calcPKDamage(
                        atkPower.getAttack(), defPower.getDefense(),
                        atkPower.getCritRate(), atkPower.getCritDamage());
                defHp -= toInt(aDmg.get("damage"), 0);
                round.put("attackerDamage", toInt(aDmg.get("damage"), 0));
                round.put("attackerCrit", aDmg.get("critical"));
                round.put("defenderHpAfter", (int) Math.max(0, defHp));

                if (defHp <= 0) {
                    result = "attacker";
                    rounds.add(round);
                    break;
                }

                Map<String, Object> dDmg = calcPKDamage(
                        defPower.getAttack(), atkPower.getDefense(),
                        defPower.getCritRate(), defPower.getCritDamage());
                atkHp -= toInt(dDmg.get("damage"), 0);
                round.put("defenderDamage", toInt(dDmg.get("damage"), 0));
                round.put("defenderCrit", dDmg.get("critical"));
                round.put("attackerHpAfter", (int) Math.max(0, atkHp));

                if (atkHp <= 0) {
                    result = "defender";
                    rounds.add(round);
                    break;
                }
            } else {
                Map<String, Object> dDmg = calcPKDamage(
                        defPower.getAttack(), atkPower.getDefense(),
                        defPower.getCritRate(), defPower.getCritDamage());
                atkHp -= toInt(dDmg.get("damage"), 0);
                round.put("defenderDamage", toInt(dDmg.get("damage"), 0));
                round.put("defenderCrit", dDmg.get("critical"));
                round.put("attackerHpAfter", (int) Math.max(0, atkHp));

                if (atkHp <= 0) {
                    result = "defender";
                    rounds.add(round);
                    break;
                }

                Map<String, Object> aDmg = calcPKDamage(
                        atkPower.getAttack(), defPower.getDefense(),
                        atkPower.getCritRate(), atkPower.getCritDamage());
                defHp -= toInt(aDmg.get("damage"), 0);
                round.put("attackerDamage", toInt(aDmg.get("damage"), 0));
                round.put("attackerCrit", aDmg.get("critical"));
                round.put("defenderHpAfter", (int) Math.max(0, defHp));

                if (defHp <= 0) {
                    result = "attacker";
                    rounds.add(round);
                    break;
                }
            }
            rounds.add(round);
        }

        if ("draw".equals(result)) {
            result = atkHp > defHp ? "attacker" : (defHp > atkHp ? "defender" : "draw");
        }

        boolean attackerWins = "attacker".equals(result);
        Player winner = attackerWins ? attacker : defender;
        Player loser = attackerWins ? defender : attacker;

        // ELO 评分更新
        Ranking winRanking = getOrCreateRanking(winner.getId());
        Ranking loseRanking = getOrCreateRanking(loser.getId());

        int[] ratingChanges = calculateRatingChange(winRanking.getRating(), loseRanking.getRating());
        int winnerChange = ratingChanges[0];
        int loserChange = ratingChanges[1]; // 负数

        updateRanking(winner.getId(),
                winRanking.getRating() + winnerChange,
                winRanking.getWins() + 1,
                winRanking.getLosses());
        updateRanking(loser.getId(),
                Math.max(0, loseRanking.getRating() + loserChange),
                loseRanking.getWins(),
                loseRanking.getLosses() + 1);

        // 胜者奖励
        Map<String, Object> atkStatsRef = attackerWins ? atkStats : defStats;
        Map<String, Object> defStatsRef = attackerWins ? defStats : atkStats;

        int expReward = 30;
        double expMult = avatarRankService.getExpMultiplier((String) atkStatsRef.getOrDefault("avatarRank", "F"));
        int experienceBefore = toInt(atkStatsRef.get("exp"), 0);
        ExperienceProgression.Result progression = ExperienceProgression.addExperience(
                atkStatsRef, expReward, expMult);
        int experienceAfter = toInt(atkStatsRef.get("exp"), experienceBefore);
        int coinsReward = 50;
        int coinsBefore = winner.getCoins() != null ? winner.getCoins() : 0;
        int coinsAfter = ResourceBalance.applyDelta("星币", coinsBefore, coinsReward);
        winner.setCoins(coinsAfter);

        // 败者HP归0
        int loserHpBefore = toInt(defStatsRef.get("hp"), 0);
        defStatsRef.put("hp", 0);

        // 保存双方状态
        try {
            attacker.setStatsJson(objectMapper.writeValueAsString(atkStats));
            defender.setStatsJson(objectMapper.writeValueAsString(defStats));
        } catch (Exception exception) {
            throw new IllegalStateException("保存PK战斗属性失败", exception);
        }
        if (playerMapper.updateById(attacker) <= 0 || playerMapper.updateById(defender) <= 0)
            throw new IllegalStateException("保存PK战斗结果失败");

        String transactionGroupId = "pk:" + challengeId;
        Map<String, Object> resourceMetadata = new LinkedHashMap<>();
        resourceMetadata.put("challengeId", challengeId);
        resourceMetadata.put("winnerId", winner.getId());
        resourceMetadata.put("loserId", loser.getId());
        recordResourceChange(winner.getId(), "currency", "coins", coinsBefore, coinsAfter,
                challengeId, transactionGroupId, transactionGroupId + ":winner:coins", resourceMetadata);
        recordResourceChange(winner.getId(), "experience", "exp", experienceBefore, experienceAfter,
                challengeId, transactionGroupId, transactionGroupId + ":winner:exp", resourceMetadata);
        recordResourceChange(loser.getId(), "vitality", "hp", loserHpBefore, 0,
                challengeId, transactionGroupId, transactionGroupId + ":loser:hp", resourceMetadata);

        // 写日志
        playerLogService.addLog(winner.getId(), "battle",
                "在PK中击败了 " + loser.getPlayerName() + "（" + rounds.size() + "回合）");
        playerLogService.addLog(loser.getId(), "battle",
                "在PK中被 " + winner.getPlayerName() + " 击败了");
        String levelUpMessage = ExperienceProgression.levelUpMessage(progression);
        if (levelUpMessage != null)
            playerLogService.addLog(winner.getId(), "milestone", levelUpMessage);

        // 记录PK
        Map<String, Object> battleData = new LinkedHashMap<>();
        battleData.put("rounds", rounds);
        battleData.put("attackerHpRemaining", Math.max(0, (int) Math.round(atkHp)));
        battleData.put("defenderHpRemaining", Math.max(0, (int) Math.round(defHp)));

        Map<String, Object> ratingChangeData = new LinkedHashMap<>();
        ratingChangeData.put("attacker_change", attackerWins ? winnerChange : loserChange);
        ratingChangeData.put("defender_change", attackerWins ? loserChange : winnerChange);

        PkRecord record = new PkRecord();
        record.setAttackerId(attackerId);
        record.setDefenderId(defenderId);
        record.setWinnerId(winner.getId());
        record.setLoserId(loser.getId());
        try {
            record.setBattleDataJson(objectMapper.writeValueAsString(battleData));
            record.setRatingChangeJson(objectMapper.writeValueAsString(ratingChangeData));
        } catch (Exception ignored) {}
        record.setCreatedAt(LocalDateTime.now());
        if (recordMapper.insert(record) <= 0)
            throw new IllegalStateException("保存PK战报失败");

        // 自动贡献广播进度 (不阻断主流程)
        try {
            broadcastService.contribute("broadcast_pk_spark", attackerId, 1, "pk");
            broadcastService.contribute("broadcast_pk_spark", defenderId, 1, "pk");
            Long winnerId = attackerWins ? attackerId : defenderId;
            broadcastService.contribute("broadcast_pk_spark", winnerId, 1, "pk");
        } catch (Exception ignored) {
        }
        Long broadcastWinnerId = attackerWins ? attackerId : defenderId;
        try {
            broadcastService.contributeToActiveAiEvents(broadcastWinnerId, "win_pk", 1);
        } catch (Exception ignored) {
        }

        // 自动阵营贡献：参与者各+1，胜者额外+2
        try {
            factionService.contribute(attackerId, null, "pk", 1L, "pk", record.getAttackerId() + "_vs_" + record.getDefenderId(), null);
            factionService.contribute(defenderId, null, "pk", 1L, "pk", record.getAttackerId() + "_vs_" + record.getDefenderId(), null);
            Long winnerId = attackerWins ? attackerId : defenderId;
            factionService.contribute(winnerId, null, "pk", 2L, "pk", record.getAttackerId() + "_vs_" + record.getDefenderId(), null);
        } catch (Exception ignored) {
        }

        // 自动任务进度
        try {
            String relatedId = attackerId + "_vs_" + defenderId;
            questService.addProgress(attackerId, "pk_participate_count", 1, "pk", relatedId);
            questService.addProgress(defenderId, "pk_participate_count", 1, "pk", relatedId);
            Long winnerId = attackerWins ? attackerId : defenderId;
            questService.addProgress(winnerId, "pk_win_count", 1, "pk", relatedId);
        } catch (Exception ignored) {}

        // 构建返回
        Map<String, Object> result2 = new LinkedHashMap<>();
        result2.put("winnerId", winner.getId());
        result2.put("winnerName", winner.getPlayerName());
        result2.put("loserId", loser.getId());
        result2.put("loserName", loser.getPlayerName());
        result2.put("attackerWins", attackerWins);
        result2.put("mode", mode);

        Map<String, Integer> ratingChangeResult = new LinkedHashMap<>();
        ratingChangeResult.put("attacker", attackerWins ? winnerChange : loserChange);
        ratingChangeResult.put("defender", attackerWins ? loserChange : winnerChange);
        result2.put("ratingChange", ratingChangeResult);

        Map<String, Object> battleLog = new LinkedHashMap<>();
        battleLog.put("rounds", rounds);
        battleLog.put("totalRounds", rounds.size());
        result2.put("battleLog", battleLog);
        result2.put("rounds", rounds);
        result2.put("totalRounds", rounds.size());

        // 奖励信息
        Map<String, Object> rewards = new LinkedHashMap<>();
        rewards.put("exp", progression.experienceAwarded());
        rewards.put("coins", coinsReward);
        result2.put("rewards", rewards);

        return result2;
    }

    private void recordResourceChange(Long playerId, String resourceType, String resourceKey,
                                      long balanceBefore, long balanceAfter, Long challengeId,
                                      String transactionGroupId, String idempotencyKey,
                                      Map<String, Object> metadata) {
        if (balanceBefore == balanceAfter) return;
        resourceTransactionService.record(new PlayerResourceTransactionService.Change(
                playerId, resourceType, resourceKey, Math.subtractExact(balanceAfter, balanceBefore),
                balanceBefore, balanceAfter, "pk_battle", String.valueOf(challengeId),
                transactionGroupId, idempotencyKey, metadata));
    }

    private void assertAliveForPk(Map<String, Object> stats) {
        int maxHp = toInt(stats.get("maxHp"), 100);
        int hp = toInt(stats.get("hp"), maxHp);
        if (hp <= 0) {
            throw new BusinessException(ErrorCode.PLAYER_DEAD, "角色已死亡，无法参与PK");
        }
    }

    private Map<String, Object> calcPKDamage(int atk, int def, double critRate, double critDamage) {
        int baseDmg = Math.max(1, (int) Math.round(atk - def * 0.5));
        boolean crit = random.nextDouble() < critRate;
        int dmg = crit ? (int) Math.round(baseDmg * critDamage) : baseDmg;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("damage", dmg);
        result.put("critical", crit);
        return result;
    }

    private int[] calculateRatingChange(int winnerRating, int loserRating) {
        double expectedWinner = 1.0 / (1 + Math.pow(10, (loserRating - winnerRating) / 400.0));
        int change = (int) Math.round(K_FACTOR * (1 - expectedWinner));
        int clamped = Math.max(5, Math.min(40, change));
        return new int[]{clamped, -clamped};
    }

    private Ranking getOrCreateRanking(Long playerId) {
        QueryWrapper<Ranking> qw = new QueryWrapper<>();
        qw.eq("player_id", playerId);
        Ranking ranking = rankingMapper.selectOne(qw);
        if (ranking == null) {
            ranking = new Ranking();
            ranking.setPlayerId(playerId);
            ranking.setRating(1000);
            ranking.setWins(0);
            ranking.setLosses(0);
            ranking.setHighestRating(1000);
            ranking.setCreatedAt(LocalDateTime.now());
            ranking.setUpdatedAt(LocalDateTime.now());
            rankingMapper.insert(ranking);
        }
        return ranking;
    }

    private Ranking getRankingEntity(Long playerId) {
        QueryWrapper<Ranking> qw = new QueryWrapper<>();
        qw.eq("player_id", playerId);
        return rankingMapper.selectOne(qw);
    }

    private void updateRanking(Long playerId, int rating, int wins, int losses) {
        Ranking ranking = getRankingEntity(playerId);
        if (ranking == null) return;
        ranking.setRating(rating);
        ranking.setWins(wins);
        ranking.setLosses(losses);
        if (rating > ranking.getHighestRating()) {
            ranking.setHighestRating(rating);
        }
        ranking.setUpdatedAt(LocalDateTime.now());
        rankingMapper.updateById(ranking);
    }

    private int estimateCombatPower(CombatStatsVO stats) {
        return (int) Math.round(
                stats.getAttack() * 5.0 +
                stats.getDefense() * 3.0 +
                stats.getHp() * 0.5 +
                stats.getSpeed() * 2.0 +
                stats.getLevel() * 10.0
        );
    }

    private record RecommendedOpponent(Map<String, Object> payload,
                                       OpponentMatchmaking.Assessment assessment) {}

    private String getPlayerName(Long playerId) {
        Player p = playerMapper.selectById(playerId);
        return p != null ? p.getPlayerName() : "未知";
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

    private int toInt(Object val, int defaultVal) {
        if (val instanceof Number) return ((Number) val).intValue();
        return defaultVal;
    }
}
