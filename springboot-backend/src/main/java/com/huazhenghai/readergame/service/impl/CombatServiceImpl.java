package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.ResourceBalance;
import com.huazhenghai.readergame.entity.BattleLog;
import com.huazhenghai.readergame.entity.Location;
import com.huazhenghai.readergame.entity.Monster;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.BattleLogMapper;
import com.huazhenghai.readergame.mapper.LocationMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.*;
import com.huazhenghai.readergame.vo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class CombatServiceImpl implements CombatService {

    private static final Logger log = LoggerFactory.getLogger(CombatServiceImpl.class);

    private final MonsterService monsterService;
    private final PlayerMapper playerMapper;
    private final PlayerMaxHpProjectionService maxHpProjectionService;
    private final BattleLogMapper battleLogMapper;
    private final LocationMapper locationMapper;
    private final RecoveryService recoveryService;
    private final PlayerLogService playerLogService;
    private final TitleService titleService;
    private final EquipmentService equipmentService;
    private final SkillService skillService;
    private final InventoryService inventoryService;
    private final BroadcastService broadcastService;
    private final FactionService factionService;
    private final QuestService questService;
    private final PlayerEffectService playerEffectService;
    private final PlayerResourceTransactionService resourceTransactionService;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    private static final int MAX_ROUNDS = 30;

    public CombatServiceImpl(MonsterService monsterService,
                             PlayerMapper playerMapper,
                             PlayerMaxHpProjectionService maxHpProjectionService,
                             BattleLogMapper battleLogMapper,
                             LocationMapper locationMapper,
                             RecoveryService recoveryService,
                             PlayerLogService playerLogService,
                             TitleService titleService,
                             EquipmentService equipmentService,
                             SkillService skillService,
                             InventoryService inventoryService,
                             BroadcastService broadcastService,
                             FactionService factionService,
                             QuestService questService,
                             ObjectMapper objectMapper,
                             PlayerEffectService playerEffectService,
                             PlayerResourceTransactionService resourceTransactionService) {
        this.monsterService = monsterService;
        this.playerMapper = playerMapper;
        this.maxHpProjectionService = maxHpProjectionService;
        this.battleLogMapper = battleLogMapper;
        this.locationMapper = locationMapper;
        this.recoveryService = recoveryService;
        this.playerLogService = playerLogService;
        this.titleService = titleService;
        this.equipmentService = equipmentService;
        this.skillService = skillService;
        this.inventoryService = inventoryService;
        this.broadcastService = broadcastService;
        this.factionService = factionService;
        this.questService = questService;
        this.objectMapper = objectMapper;
        this.playerEffectService = playerEffectService;
        this.resourceTransactionService = resourceTransactionService;
    }

    @Override
    public List<MonsterVO> getAllMonsters() {
        return monsterService.getAllMonsters();
    }

    @Override
    public List<MonsterVO> getMonstersByLocation(String locationKey) {
        return monsterService.getMonstersByLocation(locationKey);
    }

    @Override
    @Transactional
    public CombatResultVO startCombat(Long playerId, String monsterKey, Long userId) {
        return startCombat(playerId, monsterKey, UUID.randomUUID().toString(), userId);
    }

    @Override
    @Transactional
    public CombatResultVO startCombat(Long playerId, String monsterKey, String requestKey, Long userId) {
        return startCombatInternal(playerId, monsterKey, requestKey, userId, "fight");
    }

    private CombatResultVO startCombatInternal(Long playerId, String monsterKey,
                                               String requestKey, Long userId,
                                               String requestAction) {
        validateCombatRequest(monsterKey, requestKey);
        // 1. 校验玩家
        Player player = playerMapper.selectByIdForUpdate(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!Objects.equals(player.getUserId(), userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        BattleLog previous = findReplay(playerId, monsterKey, requestKey, requestAction);
        if (previous != null)
            return readCombatReceipt(previous.getResultJson());

        // 2. 应用恢复
        Map<String, Object> stats = recoveryService.applyRecovery(player);
        String statsBeforeBattleSettlement = player.getStatsJson();
        Integer coinsBeforeBattleSettlement = player.getCoins();
        Integer fragmentsBeforeBattleSettlement = player.getStoryFragments();

        // 3. 检查休息状态
        recoveryService.assertCanAct(player);

        // 4. 检查 hp
        int currentHp = toInt(stats.get("hp"), toInt(stats.get("maxHp"), 100));
        if (currentHp <= 0)
            throw new BusinessException(ErrorCode.PLAYER_DEAD, "角色已死亡，无法战斗");

        // 5. 查询怪物
        Monster monster = monsterService.getMonsterEntity(monsterKey);
        if (monster == null)
            throw new BusinessException(ErrorCode.MONSTER_NOT_FOUND, "怪物不存在");
        if (monster.getEnabled() == null || monster.getEnabled() != 1)
            throw new BusinessException(ErrorCode.MONSTER_DISABLED, "怪物已被禁用");

        Map<String, Object> monsterStats = parseJsonMap(monster.getStatsJson());
        StoryCombatRelationRules.Rule storyRelationRule = StoryCombatRelationRules
                .findForEncounter(monsterKey, parseJsonMap(player.getStageProgressJson()))
                .orElse(null);
        StoryCombatRelationRules.AttackRule storyAttackRule = StoryCombatRelationRules
                .findAttackRuleForEncounter(monsterKey, parseJsonMap(player.getStoryFlagsJson()))
                .orElse(null);
        int storyRelationApplicationsRemaining = storyRelationRule == null
                ? 0 : storyRelationRule.maximumApplications();
        int storyAttackApplicationsRemaining = storyAttackRule == null
                ? 0 : storyAttackRule.maximumApplications();

        // 5a. 计算位格压制倍率
        String playerRank = (String) stats.getOrDefault("avatarRank", "F");
        double rankSuppMult = 1.0;
        String rankSuppNote = "";
        QueryWrapper<Location> locQw = new QueryWrapper<Location>().eq("location_key", monster.getLocationKey());
        Location monsterLoc = locationMapper.selectOne(locQw);
        String monsterRecommendedRank = monsterLoc != null && monsterLoc.getRecommendedRank() != null
                ? monsterLoc.getRecommendedRank() : "F";
        int rankDiff = rankToValue(playerRank) - rankToValue(monsterRecommendedRank);
        if (rankDiff != 0) {
            rankSuppMult = Math.max(0.5, Math.min(3.0, 1.0 + rankDiff * 0.15));
            if (rankDiff > 0) {
                rankSuppNote = "化身位格压制：伤害 +" + Math.round((rankSuppMult - 1) * 100) + "%";
            } else {
                rankSuppNote = "位格不足：伤害 -" + Math.round((1 - rankSuppMult) * 100) + "%";
            }
        }

        // 故事/传说关系不由称号或怪物标签的通用循环改写伤害；只有命中明确的
        // 个人旗标、具名遭遇、上限和来源记录时，才由独立规则表结算。

        // 6. 计算玩家战斗属性
        CombatStatsVO playerStats = calculateCombatPower(playerId);
        int playerHpBefore = currentHp;
        int playerMaxHp = toInt(stats.get("maxHp"), 100);

        // 7. 战斗模拟
        int playerAtk = playerStats.getAttack();
        int playerDef = playerStats.getDefense();
        int playerSpd = playerStats.getSpeed();
        double playerCritRate = playerStats.getCritRate();
        double playerCritDamage = playerStats.getCritDamage();

        int monsterHp = toInt(monsterStats.get("hp"), 100);
        int monsterAtk = toInt(monsterStats.get("attack"), 10);
        int monsterDef = toInt(monsterStats.get("defense"), 5);
        int monsterSpd = toInt(monsterStats.get("speed"), 5);

        double playerBattleHp = currentHp;
        double monsterBattleHp = monsterHp;

        List<CombatRoundVO> rounds = new ArrayList<>();
        String result = "draw";
        boolean attackerFirst = playerSpd >= monsterSpd;

        for (int i = 0; i < MAX_ROUNDS; i++) {
            CombatRoundVO round = new CombatRoundVO();
            round.setRound(i + 1);

            if (attackerFirst) {
                // Player attacks
                Map<String, Object> pAtk = calcDamage(playerAtk, monsterDef, playerCritRate, playerCritDamage,
                        rankSuppMult, rankSuppNote);
                int pDmg = toInt(pAtk.get("damage"), 0);
                StoryCombatRelationRules.AttackResult storyAttack = null;
                if (storyAttackRule != null && storyAttackApplicationsRemaining > 0) {
                    storyAttack = storyAttackRule.boostFirstOutgoingHit(pDmg);
                    if (storyAttack.applied()) {
                        pDmg = storyAttack.finalDamage();
                        storyAttackApplicationsRemaining--;
                    }
                }
                monsterBattleHp -= pDmg;
                Map<String, Object> pAction = new LinkedHashMap<>();
                pAction.put("actor", "player");
                pAction.put("type", "attack");
                pAction.put("damage", pDmg);
                pAction.put("critical", pAtk.get("critical"));
                if (pAtk.containsKey("suppressionNote")) {
                    pAction.put("suppressionNote", pAtk.get("suppressionNote"));
                }
                if (storyAttack != null && storyAttack.applied()) {
                    appendSuppressionNote(pAction, storyAttackRule.combatNote(storyAttack.damageBonus()));
                    pAction.put("storyRelation", storyAttackRule.auditMetadata(
                            toInt(pAtk.get("damage"), 0), storyAttack.damageBonus()));
                }
                pAction.put("targetHpAfter", (int) Math.max(0, monsterBattleHp));
                round.getActions().add(pAction);

                if (monsterBattleHp <= 0) {
                    result = "win";
                    rounds.add(round);
                    break;
                }

                // Monster attacks
                Map<String, Object> mAtk = calcDamage(monsterAtk, playerDef, 0, 1,
                        1.0, "");
                int mDmg = toInt(mAtk.get("damage"), 0);
                StoryCombatRelationRules.HitResult storyMitigation = null;
                if (storyRelationRule != null && storyRelationApplicationsRemaining > 0) {
                    storyMitigation = storyRelationRule.reduceFirstIncomingHit(mDmg);
                    if (storyMitigation.applied()) {
                        mDmg = storyMitigation.finalDamage();
                        storyRelationApplicationsRemaining--;
                    }
                }
                playerBattleHp -= mDmg;
                Map<String, Object> mAction = new LinkedHashMap<>();
                mAction.put("actor", "monster");
                mAction.put("type", "attack");
                mAction.put("damage", mDmg);
                mAction.put("critical", false);
                if (storyMitigation != null && storyMitigation.applied()) {
                    mAction.put("suppressionNote", storyRelationRule.combatNote(
                            storyMitigation.damageReduced()));
                    mAction.put("storyRelation", storyRelationRule.auditMetadata(
                            storyMitigation.damageReduced()));
                }
                if (mAtk.containsKey("suppressionNote")) {
                    mAction.put("suppressionNote", mAtk.get("suppressionNote"));
                }
                mAction.put("targetHpAfter", (int) Math.max(0, playerBattleHp));
                round.getActions().add(mAction);

                if (playerBattleHp <= 0) {
                    result = "lose";
                    rounds.add(round);
                    break;
                }
            } else {
                // Monster attacks first
                Map<String, Object> mAtk = calcDamage(monsterAtk, playerDef, 0, 1,
                        1.0, "");
                int mDmg = toInt(mAtk.get("damage"), 0);
                StoryCombatRelationRules.HitResult storyMitigation = null;
                if (storyRelationRule != null && storyRelationApplicationsRemaining > 0) {
                    storyMitigation = storyRelationRule.reduceFirstIncomingHit(mDmg);
                    if (storyMitigation.applied()) {
                        mDmg = storyMitigation.finalDamage();
                        storyRelationApplicationsRemaining--;
                    }
                }
                playerBattleHp -= mDmg;
                Map<String, Object> mAction = new LinkedHashMap<>();
                mAction.put("actor", "monster");
                mAction.put("type", "attack");
                mAction.put("damage", mDmg);
                mAction.put("critical", false);
                if (storyMitigation != null && storyMitigation.applied()) {
                    mAction.put("suppressionNote", storyRelationRule.combatNote(
                            storyMitigation.damageReduced()));
                    mAction.put("storyRelation", storyRelationRule.auditMetadata(
                            storyMitigation.damageReduced()));
                }
                if (mAtk.containsKey("suppressionNote")) {
                    mAction.put("suppressionNote", mAtk.get("suppressionNote"));
                }
                mAction.put("targetHpAfter", (int) Math.max(0, playerBattleHp));
                round.getActions().add(mAction);

                if (playerBattleHp <= 0) {
                    result = "lose";
                    rounds.add(round);
                    break;
                }

                // Player attacks
                Map<String, Object> pAtk = calcDamage(playerAtk, monsterDef, playerCritRate, playerCritDamage,
                        rankSuppMult, rankSuppNote);
                int pDmg = toInt(pAtk.get("damage"), 0);
                StoryCombatRelationRules.AttackResult storyAttack = null;
                if (storyAttackRule != null && storyAttackApplicationsRemaining > 0) {
                    storyAttack = storyAttackRule.boostFirstOutgoingHit(pDmg);
                    if (storyAttack.applied()) {
                        pDmg = storyAttack.finalDamage();
                        storyAttackApplicationsRemaining--;
                    }
                }
                monsterBattleHp -= pDmg;
                Map<String, Object> pAction = new LinkedHashMap<>();
                pAction.put("actor", "player");
                pAction.put("type", "attack");
                pAction.put("damage", pDmg);
                pAction.put("critical", pAtk.get("critical"));
                if (pAtk.containsKey("suppressionNote")) {
                    pAction.put("suppressionNote", pAtk.get("suppressionNote"));
                }
                if (storyAttack != null && storyAttack.applied()) {
                    appendSuppressionNote(pAction, storyAttackRule.combatNote(storyAttack.damageBonus()));
                    pAction.put("storyRelation", storyAttackRule.auditMetadata(
                            toInt(pAtk.get("damage"), 0), storyAttack.damageBonus()));
                }
                pAction.put("targetHpAfter", (int) Math.max(0, monsterBattleHp));
                round.getActions().add(pAction);

                if (monsterBattleHp <= 0) {
                    result = "win";
                    rounds.add(round);
                    break;
                }
            }

            rounds.add(round);
        }

        // 30回合未分胜负，剩余血量高者胜
        if ("draw".equals(result)) {
            if (playerBattleHp > monsterBattleHp) result = "win";
            else result = "lose";
        }

        int playerHpAfter = Math.max(1, (int) Math.round(playerBattleHp));
        int monsterHpAfter = (int) Math.max(0, Math.round(monsterBattleHp));

        // 8. 更新玩家 HP
        stats.put("hp", playerHpAfter);

        // 9. 战斗奖励
        CombatRewardVO rewards = new CombatRewardVO();
        List<Map<String, Object>> drops = new ArrayList<>();

        if ("win".equals(result)) {
            Map<String, Object> rewardDef = parseJsonMap(monster.getRewardsJson());

            // exp (位阶倍率加成)
            int exp = toInt(rewardDef.get("exp"), 0);
            if (exp > 0) {
                int experienceBefore = toInt(stats.get("exp"), 0);
                double multiplier = ExperienceProgression.multiplierForRank(
                        (String) stats.getOrDefault("avatarRank", "F"));
                ExperienceProgression.Result progression = ExperienceProgression.addExperience(stats, exp, multiplier);
                rewards.setExp(progression.experienceAwarded());
                recordResourceChange(playerId, "experience", "exp", experienceBefore,
                        toInt(stats.get("exp"), experienceBefore), "combat", monsterKey,
                        requestKey + ":exp", requestKey, Map.of("rankMultiplier", multiplier));
                if (progression.levelsGained() > 0) {
                    playerHpAfter = toInt(stats.get("maxHp"), 100);
                }
                String levelUpMessage = ExperienceProgression.levelUpMessage(progression);
                if (levelUpMessage != null) playerLogService.addLog(playerId, "milestone", levelUpMessage);
            }

            // coins
            int coins = toInt(rewardDef.get("coins"), 0);
            if (coins > 0) {
                int coinsBefore = toInt(player.getCoins(), 0);
                player.setCoins(ResourceBalance.applyDelta("星币", player.getCoins(), coins));
                rewards.setCoins(coins);
                recordResourceChange(playerId, "currency", "coins", coinsBefore,
                        player.getCoins(), "combat", monsterKey, requestKey + ":coins",
                        requestKey, Map.of());
            }

            // storyFragments
            int fragments = toInt(rewardDef.get("storyFragments"), 0);
            if (fragments > 0) {
                int fragmentsBefore = toInt(player.getStoryFragments(), 0);
                player.setStoryFragments(ResourceBalance.applyDelta("故事碎片", player.getStoryFragments(), fragments));
                rewards.setStoryFragments(fragments);
                recordResourceChange(playerId, "currency", "story_fragments", fragmentsBefore,
                        player.getStoryFragments(), "combat", monsterKey, requestKey + ":story-fragments",
                        requestKey, Map.of());
            }

            // channelHeat
            int channelHeat = playerEffectService.applyChannelHeatGainRate(
                    playerId, toInt(rewardDef.get("channelHeat"), 0));
            if (channelHeat > 0) {
                int current = toInt(stats.get("channelHeat"), 0);
                int channelHeatAfter = ResourceBalance.applySignedDelta("星流热度", current, channelHeat);
                stats.put("channelHeat", channelHeatAfter);
                rewards.setChannelHeat(channelHeat);
                recordResourceChange(playerId, "progress", "channel_heat", current,
                        channelHeatAfter, "combat", monsterKey, requestKey + ":channel-heat",
                        requestKey, Map.of());
            }

            // 掉落
            Map<String, Object> dropsDef = parseJsonMap(monster.getDropsJson());
            if (!dropsDef.isEmpty()) {
                drops.addAll(applyMonsterDrops(playerId, monsterKey, requestKey, dropsDef));
            }
        } else {
            // 失败惩罚: 扣少量金币
            int coinsLost = Math.min(player.getCoins(), 10);
            if (coinsLost > 0) {
                int coinsBefore = toInt(player.getCoins(), 0);
                player.setCoins(coinsBefore - coinsLost);
                rewards.setCoins(-coinsLost);
                recordResourceChange(playerId, "currency", "coins", coinsBefore,
                        player.getCoins(), "combat_loss", monsterKey, requestKey + ":coins",
                        requestKey, Map.of());
            }
        }

        // 10. 保存玩家状态
        String settledStatsJson;
        try {
            settledStatsJson = objectMapper.writeValueAsString(stats);
        } catch (Exception exception) {
            throw new IllegalStateException("战斗状态序列化失败", exception);
        }
        player.setStatsJson(settledStatsJson);
        boolean playerChanged = !Objects.equals(statsBeforeBattleSettlement, settledStatsJson)
                || !Objects.equals(coinsBeforeBattleSettlement, player.getCoins())
                || !Objects.equals(fragmentsBeforeBattleSettlement, player.getStoryFragments());
        if (playerChanged && playerMapper.updateById(player) <= 0) {
            throw new IllegalStateException("战斗结算失败，玩家状态未保存");
        }

        // 11. 写 player_log
        MonsterVO monsterVO = toMonsterVO(monster);
        String logType = "win".equals(result) ? "battle" : "battle";
        String logMsg = "win".equals(result)
                ? "击败了 " + monsterVO.getName() + "（" + rounds.size() + "回合）"
                : "被 " + monsterVO.getName() + " 击败了";
        List<Map<String, Object>> roundsData = new ArrayList<>();
        for (CombatRoundVO round : rounds) {
            Map<String, Object> roundData = new LinkedHashMap<>();
            roundData.put("round", round.getRound());
            roundData.put("actions", round.getActions());
            roundsData.add(roundData);
        }
        Map<String, Object> combatLogMetadata = new LinkedHashMap<>();
        combatLogMetadata.put("kind", "combat_result");
        combatLogMetadata.put("result", result);
        combatLogMetadata.put("monster", Map.of(
                "monsterKey", Objects.toString(monster.getMonsterKey(), ""),
                "name", Objects.toString(monsterVO.getName(), "未知目标")));
        combatLogMetadata.put("totalRounds", rounds.size());
        combatLogMetadata.put("rounds", roundsData);
        combatLogMetadata.put("rewards", rewards);
        combatLogMetadata.put("drops", drops);
        combatLogMetadata.put("newLogs", List.of(Map.of("message", logMsg, "type", logType)));
        playerLogService.addLog(playerId, logType, logMsg, combatLogMetadata);

        // 12. 写 battle_logs
        BattleLog bl = new BattleLog();
        bl.setPlayerId(playerId);
        bl.setRequestKey(requestKey);
        bl.setRequestAction(requestAction);
        bl.setMonsterKey(monsterKey);
        bl.setResult(result);
        bl.setRounds(rounds.size());
        try {
            bl.setPlayerSnapshotJson(objectMapper.writeValueAsString(playerStats));
            bl.setMonsterSnapshotJson(objectMapper.writeValueAsString(buildMonsterSnapshot(monster, monsterStats)));
            bl.setRoundsJson(objectMapper.writeValueAsString(roundsData));
            bl.setRewardsJson(objectMapper.writeValueAsString(rewards));
            bl.setDropsJson(objectMapper.writeValueAsString(drops));
        } catch (Exception exception) {
            throw new IllegalStateException("战斗历史记录序列化失败", exception);
        }
        bl.setCreatedAt(LocalDateTime.now());

        // 12a. 自动贡献广播进度 (不阻断主流程)
        if ("win".equals(result)) {
            try {
                int contribValue = "elite".equals(monster.getRarity()) ? 5 : 2;
                broadcastService.contribute("broadcast_station_cleanup", playerId, contribValue, "combat");
            } catch (Exception e) {
                log.warn("Broadcast contribution failed after combat for player {}", playerId, e);
            }
            try {
                broadcastService.contributeToActiveAiEvents(playerId, "kill_monster", 1);
            } catch (Exception e) {
                log.warn("AI broadcast contribution failed after combat for player {}", playerId, e);
            }
            try {
                long factionValue = "elite".equals(monster.getRarity()) ? 5L : 2L;
                factionService.contribute(playerId, null, "combat", factionValue, "combat", monsterKey, null);
            } catch (Exception e) {
                log.warn("Faction contribution failed after combat for player {}", playerId, e);
            }
            questService.addProgress(playerId, "combat_win_count", 1, "combat", monsterKey);
        }

        // 13. 构建返回
        CombatResultVO resultVO = new CombatResultVO();
        resultVO.setResult(result);
        Map<String, Object> monsterInfo = new LinkedHashMap<>();
        monsterInfo.put("monsterKey", monster.getMonsterKey());
        monsterInfo.put("name", monster.getName());
        monsterInfo.put("type", monster.getType());
        monsterInfo.put("rarity", monster.getRarity());
        resultVO.setMonster(monsterInfo);
        resultVO.setPlayerStats(playerStats);
        resultVO.setPlayerHpBefore(playerHpBefore);
        resultVO.setPlayerHpAfter(playerHpAfter);
        resultVO.setMonsterHpAfter(monsterHpAfter);
        resultVO.setTotalRounds(rounds.size());
        resultVO.setRounds(rounds);
        resultVO.setRewards(rewards);
        resultVO.setDrops(drops);

        List<Map<String, Object>> newLogs = new ArrayList<>();
        Map<String, Object> logEntry = new LinkedHashMap<>();
        logEntry.put("message", logMsg);
        logEntry.put("type", logType);
        newLogs.add(logEntry);
        resultVO.setNewLogs(newLogs);

        CombatStatsVO snapshot = buildPlayerSnapshot(player, stats);
        resultVO.setPlayerSnapshot(snapshot);

        // Keep the replay receipt in the same transaction as rewards and the battle log.
        bl.setResultJson(writeReceipt(resultVO));
        if (battleLogMapper.insert(bl) <= 0) {
            throw new IllegalStateException("战斗回执保存失败");
        }

        return resultVO;
    }

    @Override
    public CombatStatsVO calculateCombatPower(Long playerId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null) return new CombatStatsVO();

        Map<String, Object> stats = parseJsonMap(player.getStatsJson());

        int level = toInt(stats.get("level"), 1);
        // Legacy snapshots without the applied-bonus marker need rank/prestige-aware reconstruction.
        int maxHp = maxHpProjectionService.projectForRead(stats);
        int constellationMaxHp = PlayerMaxHpProjectionService.constellationBonus(stats);
        int baseMaxHp = Math.max(100, maxHp - constellationMaxHp);
        int hp = toInt(stats.get("hp"), maxHp);
        int attack = toInt(stats.get("attack"), 10);
        int defense = toInt(stats.get("defense"), 5);
        int speed = toInt(stats.get("speed"), 10);
        double critRate = toDouble(stats.get("critRate"), 0.05);
        double critDamage = toDouble(stats.get("critDamage"), 1.5);

        Map<String, Object> bonuses = new LinkedHashMap<>();
        bonuses.put("base", Map.of(
                "attack", attack,
                "defense", defense,
                "speed", speed,
                "maxHp", baseMaxHp,
                "critRate", critRate,
                "critDamage", critDamage));
        if (constellationMaxHp > 0) {
            bonuses.put("constellation", Map.of("maxHp", constellationMaxHp));
        }

        // Level scaling
        int levelAttackGrowth = (level - 1) * 2;
        int levelDefenseGrowth = level - 1;
        attack += levelAttackGrowth;
        defense += levelDefenseGrowth;
        if (levelAttackGrowth != 0 || levelDefenseGrowth != 0) {
            Map<String, Object> levelGrowth = new LinkedHashMap<>();
            if (levelAttackGrowth != 0) levelGrowth.put("attack", levelAttackGrowth);
            if (levelDefenseGrowth != 0) levelGrowth.put("defense", levelDefenseGrowth);
            bonuses.put("levelGrowth", levelGrowth);
        }

        // Title bonus
        Map<String, Object> titleEffects = titleService.calculateTitleEffects(playerId);
        if (!titleEffects.isEmpty()) {
            attack += toInt(titleEffects.get("attack"), 0);
            defense += toInt(titleEffects.get("defense"), 0);
            speed += toInt(titleEffects.get("speed"), 0);
            critRate += toDouble(titleEffects.get("critRate"), 0);
            critDamage += toDouble(titleEffects.get("critDamage"), 0);
            maxHp += toInt(titleEffects.get("maxHp"), 0);
            bonuses.put("title", titleEffects);
        }

        // Equipment bonus
        Map<String, Object> equipmentEffects = equipmentService.calculateEquipmentBonus(playerId);
        if (!equipmentEffects.isEmpty()) {
            attack += toInt(equipmentEffects.get("attack"), 0);
            defense += toInt(equipmentEffects.get("defense"), 0);
            speed += toInt(equipmentEffects.get("speed"), 0);
            critRate += toDouble(equipmentEffects.get("critRate"), 0);
            critDamage += toDouble(equipmentEffects.get("critDamage"), 0);
            maxHp += toInt(equipmentEffects.get("maxHp"), 0);
            bonuses.put("equipment", equipmentEffects);
        }

        // Skill bonus
        Map<String, Object> skillEffects = skillService.calculateSkillBonus(playerId);
        if (!skillEffects.isEmpty()) {
            attack += toInt(skillEffects.get("attack"), 0);
            defense += toInt(skillEffects.get("defense"), 0);
            speed += toInt(skillEffects.get("speed"), 0);
            critRate += toDouble(skillEffects.get("critRate"), 0);
            critDamage += toDouble(skillEffects.get("critDamage"), 0);
            maxHp += toInt(skillEffects.get("maxHp"), 0);
            bonuses.put("skill", skillEffects);
        }

        // Faction base buff and learned faction skills
        Map<String, Object> factionEffects = factionService.getFactionBuff(playerId);
        int factionAttack = toInt(factionEffects.get("buffAtk"), 0);
        attack += factionAttack;
        @SuppressWarnings("unchecked")
        Map<String, Object> factionSkillEffects = factionEffects.get("skillBonuses") instanceof Map<?, ?>
                ? (Map<String, Object>) factionEffects.get("skillBonuses") : Collections.emptyMap();
        if (!factionSkillEffects.isEmpty()) {
            attack += toInt(factionSkillEffects.get("attack"), 0);
            defense += toInt(factionSkillEffects.get("defense"), 0);
            speed += toInt(factionSkillEffects.get("speed"), 0);
            critRate += toDouble(factionSkillEffects.get("critRate"), 0);
            maxHp += toInt(factionSkillEffects.get("maxHp"), 0);
            bonuses.put("factionSkill", factionSkillEffects);
        }
        if (factionAttack != 0) bonuses.put("faction", Map.of("attack", factionAttack));

        CombatStatsVO vo = new CombatStatsVO();
        vo.setLevel(level);
        vo.setHp(hp);
        vo.setMaxHp(maxHp);
        vo.setAttack(Math.max(1, attack));
        vo.setDefense(Math.max(0, defense));
        vo.setSpeed(Math.max(1, speed));
        vo.setCritRate(Math.max(0, critRate));
        vo.setCritDamage(Math.max(1.0, critDamage));
        vo.setBonuses(bonuses);
        return vo;
    }

    @Override
    @Transactional
    public Map<String, Object> resolveCombat(Long playerId, String monsterKey, String action, Long userId) {
        return resolveCombat(playerId, monsterKey, action, UUID.randomUUID().toString(), userId);
    }

    @Override
    @Transactional
    public Map<String, Object> resolveCombat(Long playerId, String monsterKey, String action,
                                             String requestKey, Long userId) {
        validateCombatRequest(monsterKey, requestKey);
        Map<String, Object> result = new LinkedHashMap<>();
        String requestAction = "flee".equals(action) ? "flee" : "attack";

        Player lockedPlayer = playerMapper.selectByIdForUpdate(playerId);
        if (lockedPlayer == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!Objects.equals(lockedPlayer.getUserId(), userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        BattleLog previous = findReplay(playerId, monsterKey, requestKey, requestAction);
        if (previous != null)
            return readMapReceipt(previous.getResultJson());

        if ("flee".equals(action)) {
            Map<String, Object> stats = recoveryService.applyRecovery(lockedPlayer);
            recoveryService.assertCanAct(lockedPlayer);
            int hp = toInt(stats.get("hp"), toInt(stats.get("maxHp"), 100));
            if (hp <= 0)
                throw new BusinessException(ErrorCode.PLAYER_DEAD, "角色已死亡，无法逃跑");

            Monster monster = monsterService.getMonsterEntity(monsterKey);
            if (monster == null)
                throw new BusinessException(ErrorCode.MONSTER_NOT_FOUND, "怪物不存在");
            if (monster.getEnabled() == null || monster.getEnabled() != 1)
                throw new BusinessException(ErrorCode.MONSTER_DISABLED, "怪物已被禁用");

            String monsterName = monster.getName() == null ? "未知目标" : monster.getName();
            String message = "你从" + monsterName + "面前撤退了。";
            Map<String, Object> battle = new LinkedHashMap<>();
            battle.put("result", "escape");
            battle.put("totalRounds", 0);
            battle.put("playerHpRemaining", hp);
            battle.put("monster", Map.of("monsterKey", monsterKey, "name", monsterName));

            result.put("success", true);
            result.put("message", message);
            result.put("battle", battle);
            Map<String, Object> combatLogMetadata = new LinkedHashMap<>();
            combatLogMetadata.put("kind", "combat_result");
            combatLogMetadata.put("result", "escape");
            combatLogMetadata.put("monster", Map.of("monsterKey", monsterKey, "name", monsterName));
            combatLogMetadata.put("totalRounds", 0);
            combatLogMetadata.put("rounds", List.of());
            combatLogMetadata.put("rewards", Map.of());
            combatLogMetadata.put("drops", List.of());
            combatLogMetadata.put("newLogs", List.of(Map.of("message", message, "type", "battle")));
            playerLogService.addLog(playerId, "battle", message, combatLogMetadata);

            BattleLog escapeLog = new BattleLog();
            escapeLog.setPlayerId(playerId);
            escapeLog.setRequestKey(requestKey);
            escapeLog.setRequestAction(requestAction);
            escapeLog.setMonsterKey(monsterKey);
            escapeLog.setResult("escape");
            escapeLog.setRounds(0);
            escapeLog.setPlayerSnapshotJson(writeReceipt(stats));
            escapeLog.setMonsterSnapshotJson(writeReceipt(buildMonsterSnapshot(monster, parseJsonMap(monster.getStatsJson()))));
            escapeLog.setRoundsJson("[]");
            escapeLog.setRewardsJson("{}");
            escapeLog.setDropsJson("[]");
            escapeLog.setCreatedAt(LocalDateTime.now());
            escapeLog.setResultJson(writeReceipt(result));
            if (battleLogMapper.insert(escapeLog) <= 0) {
                throw new IllegalStateException("撤退回执保存失败");
            }
            return result;
        }

        // For "attack" and other actions, run full combat
        CombatResultVO combatResult = startCombatInternal(
                playerId, monsterKey, requestKey, userId, requestAction);
        Player player = playerMapper.selectById(playerId);
        Map<String, Object> stats = parseJsonMap(player.getStatsJson());

        result.put("success", "win".equals(combatResult.getResult()));
        result.put("message", "win".equals(combatResult.getResult())
                ? "击败了 " + combatResult.getMonster().get("name")
                : "被 " + combatResult.getMonster().get("name") + " 击败了");
        result.put("damage_taken", combatResult.getPlayerHpBefore() - combatResult.getPlayerHpAfter());

        Map<String, Object> battle = new LinkedHashMap<>();
        battle.put("result", combatResult.getResult());
        battle.put("totalRounds", combatResult.getTotalRounds());
        battle.put("playerHpRemaining", combatResult.getPlayerHpAfter());
        battle.put("monsterHpRemaining", combatResult.getMonsterHpAfter());
        battle.put("playerHpBefore", combatResult.getPlayerHpBefore());
        battle.put("monster", combatResult.getMonster());

        if (combatResult.getRewards() != null) {
            Map<String, Object> rewards = new LinkedHashMap<>();
            CombatRewardVO r = combatResult.getRewards();
            if (r.getCoins() != 0) rewards.put("coins", r.getCoins());
            if (r.getExp() != 0) rewards.put("exp", r.getExp());
            if (r.getStoryFragments() != 0) rewards.put("storyFragments", r.getStoryFragments());
            if (r.getChannelHeat() != 0) rewards.put("channelHeat", r.getChannelHeat());
            if (!rewards.isEmpty()) battle.put("rewards", rewards);
        }
        if (combatResult.getDrops() != null && !combatResult.getDrops().isEmpty()) {
            battle.put("drops", combatResult.getDrops());
        }
        // Build player snapshot for UI
        Map<String, Object> playerData = new LinkedHashMap<>();
        playerData.put("stats", stats);
        playerData.put("id", playerId);
        playerData.put("player_name", player.getPlayerName());
        playerData.put("coins", player.getCoins());

        result.put("battle", battle);
        result.put("player", playerData);

        BattleLog receipt = battleLogMapper.selectByPlayerAndRequestKey(playerId, requestKey);
        if (receipt == null)
            throw new BusinessException(ErrorCode.SERVER_ERROR, "战斗回执未能保存");
        receipt.setResultJson(writeReceipt(result));
        if (battleLogMapper.updateById(receipt) <= 0) {
            throw new IllegalStateException("战斗结果回执更新失败");
        }

        return result;
    }

    @Override
    public List<BattleLogVO> getBattleLogs(Long playerId, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看此玩家");

        QueryWrapper<BattleLog> qw = new QueryWrapper<>();
        qw.eq("player_id", playerId)
          .orderByDesc("created_at")
          .last("LIMIT 50");
        List<BattleLog> logs = battleLogMapper.selectList(qw);

        List<BattleLogVO> result = new ArrayList<>();
        for (BattleLog log : logs) {
            BattleLogVO vo = new BattleLogVO();
            vo.setId(log.getId());
            vo.setPlayerId(log.getPlayerId());
            vo.setMonsterKey(log.getMonsterKey());
            vo.setResult(log.getResult());
            vo.setRounds(log.getRounds());
            vo.setPlayerSnapshot(parseJsonMap(log.getPlayerSnapshotJson()));
            vo.setMonsterSnapshot(parseJsonMap(log.getMonsterSnapshotJson()));
            vo.setRoundDetails(parseJsonList(log.getRoundsJson()));
            vo.setRewards(parseJsonMap(log.getRewardsJson()));
            vo.setDrops(parseJsonList(log.getDropsJson()));
            vo.setCreatedAt(log.getCreatedAt() != null ? log.getCreatedAt().toString() : null);

            // 怪物名称
            Monster monster = monsterService.getMonsterEntity(log.getMonsterKey());
            if (monster != null) {
                vo.setMonsterName(monster.getName());
            }

            result.add(vo);
        }
        return result;
    }

    // ─── 工具方法 ───

    private CombatStatsVO buildPlayerSnapshot(Player player, Map<String, Object> stats) {
        CombatStatsVO vo = new CombatStatsVO();
        vo.setLevel(toInt(stats.get("level"), 1));
        vo.setHp(toInt(stats.get("hp"), 100));
        vo.setMaxHp(toInt(stats.get("maxHp"), 100));
        vo.setAttack(toInt(stats.get("attack"), 10));
        vo.setDefense(toInt(stats.get("defense"), 5));
        vo.setSpeed(toInt(stats.get("speed"), 10));
        vo.setCritRate(toDouble(stats.get("critRate"), 0.05));
        vo.setCritDamage(toDouble(stats.get("critDamage"), 1.5));
        return vo;
    }

    private Map<String, Object> buildMonsterSnapshot(Monster monster, Map<String, Object> monsterStats) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("monsterKey", monster.getMonsterKey());
        snapshot.put("name", monster.getName());
        snapshot.put("type", monster.getType());
        snapshot.put("rarity", monster.getRarity());
        snapshot.putAll(monsterStats);
        return snapshot;
    }

    private MonsterVO toMonsterVO(Monster m) {
        Map<String, Object> stats = parseJsonMap(m.getStatsJson());
        MonsterVO vo = new MonsterVO();
        vo.setMonsterKey(m.getMonsterKey());
        vo.setName(m.getName());
        vo.setType(m.getType());
        vo.setRarity(m.getRarity());
        vo.setStats(stats);
        return vo;
    }

    private Map<String, Object> calcDamage(int atk, int def, double critRate, double critDamage,
                                          double suppressionMult, String suppressionNote) {
        int baseDmg = Math.max(1, (int) Math.round(atk - def * 0.5));
        boolean crit = random.nextDouble() < critRate;
        int dmg = crit ? (int) Math.round(baseDmg * critDamage) : baseDmg;
        dmg = Math.max(1, (int) Math.round(dmg * suppressionMult));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("damage", dmg);
        result.put("critical", crit);
        if (!suppressionNote.isEmpty()) {
            result.put("suppressionNote", suppressionNote);
        }
        return result;
    }

    private void appendSuppressionNote(Map<String, Object> action, String note) {
        if (note == null || note.isBlank()) return;
        Object existing = action.get("suppressionNote");
        if (existing instanceof String text && !text.isBlank()) {
            action.put("suppressionNote", text + "；" + note);
        } else {
            action.put("suppressionNote", note);
        }
    }

    /** 位格转数值: F=1, E=2, ..., SSS=9 */
    private int rankToValue(String rank) {
        if (rank == null) return 1;
        return switch (rank.toUpperCase()) {
            case "F" -> 1;
            case "E" -> 2;
            case "D" -> 3;
            case "C" -> 4;
            case "B" -> 5;
            case "A" -> 6;
            case "S" -> 7;
            case "SS" -> 8;
            case "SSS" -> 9;
            default -> 1;
        };
    }

    private void validateCombatRequest(String monsterKey, String requestKey) {
        if (monsterKey == null || monsterKey.isBlank())
            throw new BusinessException(ErrorCode.BAD_REQUEST, "monsterKey 不能为空");
        if (requestKey == null || requestKey.isBlank() || requestKey.length() > 64)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "requestKey 必须为 1 到 64 个字符");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> applyMonsterDrops(Long playerId, String monsterKey,
                                                        String requestKey, Map<String, Object> dropsDef) {
        List<Map<String, Object>> acquiredDrops = new ArrayList<>();

        List<Map<String, Object>> items = (List<Map<String, Object>>) dropsDef.get("items");
        if (items != null) {
            for (int index = 0; index < items.size(); index++) {
                Map<String, Object> itemDrop = items.get(index);
                String itemKey = (String) itemDrop.get("itemKey");
                int quantity = toInt(itemDrop.get("quantity"), 1);
                double dropRate = itemDrop.containsKey("dropRate")
                        ? toDouble(itemDrop.get("dropRate"), 0) : 1.0;
                if (itemKey == null || itemKey.isBlank() || quantity <= 0
                        || random.nextDouble() >= dropRate) continue;

                var grant = inventoryService.addItemWithResult(playerId, itemKey, quantity);
                if (grant.quantityGranted() > 0) {
                    int quantityAfter = grant.totalQuantity();
                    int quantityBefore = quantityAfter - grant.quantityGranted();
                    recordResourceChange(playerId, "item", itemKey, quantityBefore,
                            quantityAfter, "combat", monsterKey,
                            requestKey + ":item-drop:" + index, requestKey,
                            Map.of("requestedQuantity", quantity));
                    Map<String, Object> acquired = new LinkedHashMap<>();
                    acquired.put("itemKey", itemKey);
                    acquired.put("quantity", grant.quantityGranted());
                    acquired.put("totalQuantity", grant.totalQuantity());
                    if (grant.itemName() != null && !grant.itemName().isBlank()) {
                        acquired.put("itemName", grant.itemName());
                    }
                    acquiredDrops.add(acquired);
                }
            }
        }

        List<Map<String, Object>> equipmentDrops = (List<Map<String, Object>>) dropsDef.get("equipment");
        if (equipmentDrops != null) {
            for (int index = 0; index < equipmentDrops.size(); index++) {
                Map<String, Object> drop = equipmentDrops.get(index);
                String equipmentKey = (String) drop.get("equipmentKey");
                double dropRate = drop.containsKey("dropRate")
                        ? toDouble(drop.get("dropRate"), 0) : 1.0;
                if (equipmentKey == null || equipmentKey.isBlank()
                        || random.nextDouble() >= dropRate) continue;

                EquipmentGrantResult grant = equipmentService.addEquipmentWithResult(
                        playerId, equipmentKey, "combat");
                if (grant.quantityGranted() > 0) {
                    recordResourceChange(playerId, "equipment", equipmentKey, 0, 1,
                            "combat", monsterKey,
                            requestKey + ":equipment-drop:" + index, requestKey, Map.of());
                    Map<String, Object> acquired = new LinkedHashMap<>();
                    acquired.put("equipmentKey", equipmentKey);
                    if (grant.equipmentName() != null && !grant.equipmentName().isBlank()) {
                        acquired.put("equipmentName", grant.equipmentName());
                    }
                    acquiredDrops.add(acquired);
                }
            }
        }

        return acquiredDrops;
    }

    private void recordResourceChange(Long playerId, String resourceType, String resourceKey,
                                      long balanceBefore, long balanceAfter,
                                      String sourceType, String sourceId, String idempotencyKey,
                                      String transactionGroupId, Map<String, Object> metadata) {
        long amount = balanceAfter - balanceBefore;
        if (amount == 0) return;
        resourceTransactionService.record(new PlayerResourceTransactionService.Change(
                playerId, resourceType, resourceKey, amount, balanceBefore, balanceAfter,
                sourceType, sourceId, transactionGroupId, idempotencyKey, metadata));
    }

    /** Must be called while the player's row lock is held. */
    private BattleLog findReplay(Long playerId, String monsterKey,
                                 String requestKey, String requestAction) {
        BattleLog previous = battleLogMapper.selectByPlayerAndRequestKey(playerId, requestKey);
        if (previous == null)
            return null;
        if (!Objects.equals(previous.getMonsterKey(), monsterKey)
                || !Objects.equals(previous.getRequestAction(), requestAction)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "该请求键已用于其他战斗目标或操作，请重新进入遭遇后再试");
        }
        if (previous.getResultJson() == null || previous.getResultJson().isBlank())
            throw new BusinessException(ErrorCode.SERVER_ERROR, "战斗回执缺失，已阻止重复结算");
        return previous;
    }

    private String writeReceipt(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "无法保存战斗回执");
        }
    }

    private CombatResultVO readCombatReceipt(String json) {
        try {
            return objectMapper.readValue(json, CombatResultVO.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "战斗回执无法读取，已阻止重复结算");
        }
    }

    private Map<String, Object> readMapReceipt(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "战斗回执无法读取，已阻止重复结算");
        }
    }

    /** 解析 JSON 字符串数组为 List<String> */
    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank() || "null".equals(json))
            return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseJsonList(String json) {
        if (json == null || json.isBlank() || "null".equals(json))
            return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 位阶经验倍率: 阶梯型增长 */
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

    private double toDouble(Object val, double defaultVal) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        return defaultVal;
    }
}
