package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
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
                         ObjectMapper objectMapper) {
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
    }

    @Override
    public List<Map<String, Object>> getOpponents(Long playerId, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

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

        List<Map<String, Object>> opponents = new ArrayList<>();
        for (Player p : allPlayers) {
            if (pendingOpponentIds.contains(p.getId())) continue;
            if (!onlinePlayerService.isOnline(p.getId())) continue;

            Map<String, Object> stats = parseJsonMap(p.getStatsJson());
            CombatStatsVO combatStats = combatService.calculateCombatPower(p.getId());
            Ranking ranking = getRankingEntity(p.getId());

            Map<String, Object> opp = new LinkedHashMap<>();
            opp.put("id", p.getId());
            opp.put("player_name", p.getPlayerName());
            opp.put("level", toInt(stats.get("level"), 1));
            opp.put("rating", ranking != null ? ranking.getRating() : 1000);
            opp.put("wins", ranking != null ? ranking.getWins() : 0);
            opp.put("losses", ranking != null ? ranking.getLosses() : 0);
            opp.put("combat_power", estimateCombatPower(combatStats));
            opponents.add(opp);
        }

        // 按评分降序
        opponents.sort((a, b) -> Integer.compare(
                toInt(b.get("rating"), 1000), toInt(a.get("rating"), 1000)));
        if (opponents.size() > 20) {
            opponents = opponents.subList(0, 20);
        }
        return opponents;
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
    public Map<String, Object> resolveChallenge(Long challengeId, boolean accept, Long playerId, Long userId) {
        PkChallenge challenge = challengeMapper.selectById(challengeId);
        if (challenge == null || !"pending".equals(challenge.getStatus()))
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND, "挑战不存在或已过期");

        if (!challenge.getDefenderId().equals(playerId))
            throw new BusinessException(ErrorCode.NOT_YOUR_CHALLENGE, "这不是发给你的挑战");

        Player defender = playerMapper.selectById(playerId);
        if (defender == null || !defender.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        challenge.setResolvedAt(LocalDateTime.now());

        if (!accept) {
            challenge.setStatus("rejected");
            challengeMapper.updateById(challenge);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("accepted", false);
            result.put("message", "已拒绝挑战");
            return result;
        }

        challenge.setStatus("accepted");
        challengeMapper.updateById(challenge);

        // 执行PK战斗
        Map<String, Object> battleResult = executePkBattle(
                challenge.getAttackerId(), challenge.getDefenderId(), challenge.getMode());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", true);
        result.put("battle", battleResult);
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

    // ─── 内部方法 ───

    /**
     * 执行PK战斗核心逻辑.
     */
    private Map<String, Object> executePkBattle(Long attackerId, Long defenderId, String mode) {
        Player attacker = playerMapper.selectById(attackerId);
        Player defender = playerMapper.selectById(defenderId);
        if (attacker == null || defender == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");

        // 应用恢复 & 检查休息
        Map<String, Object> atkStats = recoveryService.applyRecovery(attacker);
        Map<String, Object> defStats = recoveryService.applyRecovery(defender);
        recoveryService.assertCanAct(attacker);
        recoveryService.assertCanAct(defender);

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
        int adjustedExp = Math.max(1, (int) Math.round(expReward * expMult));
        int coinsReward = 50;
        int currentExp = toInt(atkStatsRef.get("exp"), 0);
        int totalExp = currentExp + adjustedExp;
        atkStatsRef.put("exp", totalExp);
        // 升级检查
        int newLevel = (int) Math.floor(Math.sqrt(totalExp / 100.0)) + 1;
        int oldLevel = toInt(atkStatsRef.get("level"), 1);
        if (newLevel > oldLevel) {
            int levelsGained = newLevel - oldLevel;
            atkStatsRef.put("level", newLevel);
            atkStatsRef.put("hp", atkStatsRef.get("maxHp"));
            atkStatsRef.put("freePoints", toInt(atkStatsRef.get("freePoints"), 0) + 3 * levelsGained);
        }
        winner.setCoins(winner.getCoins() + coinsReward);

        // 败者HP归0
        defStatsRef.put("hp", 0);

        // 保存双方状态
        try {
            attacker.setStatsJson(objectMapper.writeValueAsString(atkStats));
            defender.setStatsJson(objectMapper.writeValueAsString(defStats));
        } catch (Exception ignored) {}
        playerMapper.updateById(attacker);
        playerMapper.updateById(defender);

        // 写日志
        playerLogService.addLog(winner.getId(), "battle",
                "在PK中击败了 " + loser.getPlayerName() + "（" + rounds.size() + "回合）");
        playerLogService.addLog(loser.getId(), "battle",
                "在PK中被 " + winner.getPlayerName() + " 击败了");

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
        recordMapper.insert(record);

        // 自动贡献广播进度 (不阻断主流程)
        try {
            broadcastService.contribute("broadcast_pk_spark", attackerId, 1, "pk");
            broadcastService.contribute("broadcast_pk_spark", defenderId, 1, "pk");
            Long winnerId = attackerWins ? attackerId : defenderId;
            broadcastService.contribute("broadcast_pk_spark", winnerId, 1, "pk");
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
        rewards.put("exp", expReward);
        rewards.put("coins", coinsReward);
        result2.put("rewards", rewards);

        return result2;
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
