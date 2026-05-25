package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.BattleLog;
import com.huazhenghai.readergame.entity.Monster;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.BattleLogMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.*;
import com.huazhenghai.readergame.vo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class CombatServiceImpl implements CombatService {

    private final MonsterService monsterService;
    private final PlayerMapper playerMapper;
    private final BattleLogMapper battleLogMapper;
    private final RecoveryService recoveryService;
    private final PlayerLogService playerLogService;
    private final TitleService titleService;
    private final EquipmentService equipmentService;
    private final SkillService skillService;
    private final InventoryService inventoryService;
    private final BroadcastService broadcastService;
    private final FactionService factionService;
    private final QuestService questService;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    private static final int MAX_ROUNDS = 30;

    public CombatServiceImpl(MonsterService monsterService,
                             PlayerMapper playerMapper,
                             BattleLogMapper battleLogMapper,
                             RecoveryService recoveryService,
                             PlayerLogService playerLogService,
                             TitleService titleService,
                             EquipmentService equipmentService,
                             SkillService skillService,
                             InventoryService inventoryService,
                             BroadcastService broadcastService,
                             FactionService factionService,
                             QuestService questService,
                             ObjectMapper objectMapper) {
        this.monsterService = monsterService;
        this.playerMapper = playerMapper;
        this.battleLogMapper = battleLogMapper;
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
        // 1. 校验玩家
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        // 2. 应用恢复
        Map<String, Object> stats = recoveryService.applyRecovery(player);

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
                Map<String, Object> pAtk = calcDamage(playerAtk, monsterDef, playerCritRate, playerCritDamage);
                int pDmg = toInt(pAtk.get("damage"), 0);
                monsterBattleHp -= pDmg;
                Map<String, Object> pAction = new LinkedHashMap<>();
                pAction.put("actor", "player");
                pAction.put("type", "attack");
                pAction.put("damage", pDmg);
                pAction.put("critical", pAtk.get("critical"));
                pAction.put("targetHpAfter", (int) Math.max(0, monsterBattleHp));
                round.getActions().add(pAction);

                if (monsterBattleHp <= 0) {
                    result = "win";
                    rounds.add(round);
                    break;
                }

                // Monster attacks
                Map<String, Object> mAtk = calcDamage(monsterAtk, playerDef, 0, 1);
                int mDmg = toInt(mAtk.get("damage"), 0);
                playerBattleHp -= mDmg;
                Map<String, Object> mAction = new LinkedHashMap<>();
                mAction.put("actor", "monster");
                mAction.put("type", "attack");
                mAction.put("damage", mDmg);
                mAction.put("critical", false);
                mAction.put("targetHpAfter", (int) Math.max(0, playerBattleHp));
                round.getActions().add(mAction);

                if (playerBattleHp <= 0) {
                    result = "lose";
                    rounds.add(round);
                    break;
                }
            } else {
                // Monster attacks first
                Map<String, Object> mAtk = calcDamage(monsterAtk, playerDef, 0, 1);
                int mDmg = toInt(mAtk.get("damage"), 0);
                playerBattleHp -= mDmg;
                Map<String, Object> mAction = new LinkedHashMap<>();
                mAction.put("actor", "monster");
                mAction.put("type", "attack");
                mAction.put("damage", mDmg);
                mAction.put("critical", false);
                mAction.put("targetHpAfter", (int) Math.max(0, playerBattleHp));
                round.getActions().add(mAction);

                if (playerBattleHp <= 0) {
                    result = "lose";
                    rounds.add(round);
                    break;
                }

                // Player attacks
                Map<String, Object> pAtk = calcDamage(playerAtk, monsterDef, playerCritRate, playerCritDamage);
                int pDmg = toInt(pAtk.get("damage"), 0);
                monsterBattleHp -= pDmg;
                Map<String, Object> pAction = new LinkedHashMap<>();
                pAction.put("actor", "player");
                pAction.put("type", "attack");
                pAction.put("damage", pDmg);
                pAction.put("critical", pAtk.get("critical"));
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
                double multiplier = expMultiplier((String) stats.getOrDefault("avatarRank", "F"));
                int adjustedExp = Math.max(1, (int) Math.round(exp * multiplier));
                int currentExp = toInt(stats.get("exp"), 0);
                stats.put("exp", currentExp + adjustedExp);
                rewards.setExp(adjustedExp);
                // 升级检查: level = floor(sqrt(totalExp / 100)) + 1
                int newLevel = (int) Math.floor(Math.sqrt((currentExp + adjustedExp) / 100.0)) + 1;
                int oldLevel = toInt(stats.get("level"), 1);
                if (newLevel > oldLevel) {
                    int levelsGained = newLevel - oldLevel;
                    stats.put("level", newLevel);
                    stats.put("hp", toInt(stats.get("maxHp"), 100));
                    stats.put("freePoints", toInt(stats.get("freePoints"), 0) + 3 * levelsGained);
                    playerHpAfter = toInt(stats.get("maxHp"), 100);
                }
            }

            // coins
            int coins = toInt(rewardDef.get("coins"), 0);
            if (coins > 0) {
                player.setCoins(player.getCoins() + coins);
                rewards.setCoins(coins);
            }

            // storyFragments
            int fragments = toInt(rewardDef.get("storyFragments"), 0);
            if (fragments > 0) {
                player.setStoryFragments(player.getStoryFragments() + fragments);
                rewards.setStoryFragments(fragments);
            }

            // channelHeat
            int channelHeat = toInt(rewardDef.get("channelHeat"), 0);
            if (channelHeat > 0) {
                int current = toInt(stats.get("channelHeat"), 0);
                stats.put("channelHeat", current + channelHeat);
                rewards.setChannelHeat(channelHeat);
            }

            // 掉落
            Map<String, Object> dropsDef = parseJsonMap(monster.getDropsJson());
            if (!dropsDef.isEmpty()) {
                // 物品掉落
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) dropsDef.get("items");
                if (items != null) {
                    for (Map<String, Object> itemDrop : items) {
                        String itemKey = (String) itemDrop.get("itemKey");
                        double dropRate = itemDrop.containsKey("dropRate") ?
                                ((Number) itemDrop.get("dropRate")).doubleValue() : 1.0;
                        if (random.nextDouble() < dropRate) {
                            int qty = toInt(itemDrop.get("quantity"), 1);
                            inventoryService.addItem(playerId, itemKey, qty);
                            Map<String, Object> acquired = new LinkedHashMap<>();
                            acquired.put("itemKey", itemKey);
                            acquired.put("quantity", qty);
                            drops.add(acquired);
                        }
                    }
                }
                // 装备掉落
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> equipmentDrops = (List<Map<String, Object>>) dropsDef.get("equipment");
                if (equipmentDrops != null) {
                    for (Map<String, Object> drop : equipmentDrops) {
                        String equipKey = (String) drop.get("equipmentKey");
                        double dropRate = drop.containsKey("dropRate") ?
                                ((Number) drop.get("dropRate")).doubleValue() : 1.0;
                        if (random.nextDouble() < dropRate) {
                            equipmentService.addEquipment(playerId, equipKey, "combat");
                            drops.add(Map.of("equipmentKey", equipKey));
                        }
                    }
                }
            }
        } else {
            // 失败惩罚: 扣少量金币
            int coinsLost = Math.min(player.getCoins(), 10);
            if (coinsLost > 0) {
                player.setCoins(player.getCoins() - coinsLost);
                rewards.setCoins(-coinsLost);
            }
        }

        // 10. 保存玩家状态
        try {
            player.setStatsJson(objectMapper.writeValueAsString(stats));
        } catch (Exception ignored) {}
        playerMapper.updateById(player);

        // 11. 写 player_log
        MonsterVO monsterVO = toMonsterVO(monster);
        String logType = "win".equals(result) ? "battle" : "battle";
        String logMsg = "win".equals(result)
                ? "击败了 " + monsterVO.getName() + "（" + rounds.size() + "回合）"
                : "被 " + monsterVO.getName() + " 击败了";
        playerLogService.addLog(playerId, logType, logMsg);

        // 12. 写 battle_logs
        BattleLog bl = new BattleLog();
        bl.setPlayerId(playerId);
        bl.setMonsterKey(monsterKey);
        bl.setResult(result);
        bl.setRounds(rounds.size());
        try {
            bl.setPlayerSnapshotJson(objectMapper.writeValueAsString(playerStats));
            bl.setMonsterSnapshotJson(objectMapper.writeValueAsString(buildMonsterSnapshot(monster, monsterStats)));
            List<Map<String, Object>> roundsData = new ArrayList<>();
            for (CombatRoundVO r : rounds) {
                Map<String, Object> rd = new LinkedHashMap<>();
                rd.put("round", r.getRound());
                rd.put("actions", r.getActions());
                roundsData.add(rd);
            }
            bl.setRoundsJson(objectMapper.writeValueAsString(roundsData));
            bl.setRewardsJson(objectMapper.writeValueAsString(rewards));
            bl.setDropsJson(objectMapper.writeValueAsString(drops));
        } catch (Exception ignored) {}
        bl.setCreatedAt(LocalDateTime.now());
        battleLogMapper.insert(bl);

        // 12a. 自动贡献广播进度 (不阻断主流程)
        if ("win".equals(result)) {
            try {
                int contribValue = "elite".equals(monster.getRarity()) ? 5 : 2;
                broadcastService.contribute("broadcast_station_cleanup", playerId, contribValue, "combat");
            } catch (Exception ignored) {
            }
            try {
                long factionValue = "elite".equals(monster.getRarity()) ? 5L : 2L;
                factionService.contribute(playerId, null, "combat", factionValue, "combat", monsterKey, null);
            } catch (Exception ignored) {
            }
            try { questService.addProgress(playerId, "combat_win_count", 1, "combat", monsterKey); }
            catch (Exception ignored) {}
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

        return resultVO;
    }

    @Override
    public CombatStatsVO calculateCombatPower(Long playerId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null) return new CombatStatsVO();

        Map<String, Object> stats = parseJsonMap(player.getStatsJson());

        int level = toInt(stats.get("level"), 1);
        int maxHp = toInt(stats.get("maxHp"), 100);
        int hp = toInt(stats.get("hp"), maxHp);
        int attack = toInt(stats.get("attack"), 10);
        int defense = toInt(stats.get("defense"), 5);
        int speed = toInt(stats.get("speed"), 10);
        double critRate = toDouble(stats.get("critRate"), 0.05);
        double critDamage = toDouble(stats.get("critDamage"), 1.5);

        // Level scaling
        attack += (level - 1) * 2;
        defense += (level - 1) * 1;

        // Title bonus
        Map<String, Object> titleEffects = titleService.calculateTitleEffects(playerId);
        Map<String, Object> bonuses = new LinkedHashMap<>();
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
        Map<String, Object> result = new LinkedHashMap<>();

        if ("flee".equals(action)) {
            Player player = playerMapper.selectById(playerId);
            Map<String, Object> stats = recoveryService.applyRecovery(player);
            int hp = toInt(stats.get("hp"), toInt(stats.get("maxHp"), 100));
            result.put("success", true);
            result.put("message", "你逃离了战斗！");
            result.put("battle", Map.of("result", "flee", "totalRounds", 0, "playerHpRemaining", hp));
            return result;
        }

        // For "attack" and other actions, run full combat
        CombatResultVO combatResult = startCombat(playerId, monsterKey, userId);
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

    private Map<String, Object> calcDamage(int atk, int def, double critRate, double critDamage) {
        int baseDmg = Math.max(1, (int) Math.round(atk - def * 0.5));
        boolean crit = random.nextDouble() < critRate;
        int dmg = crit ? (int) Math.round(baseDmg * critDamage) : baseDmg;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("damage", dmg);
        result.put("critical", crit);
        return result;
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
    private double expMultiplier(String rankKey) {
        if (rankKey == null) return 1.0;
        return switch (rankKey.toUpperCase()) {
            case "E" -> 1.5;
            case "D" -> 2.5;
            case "C" -> 4.0;
            case "B" -> 7.0;
            case "A" -> 12.0;
            case "S" -> 20.0;
            case "SS" -> 35.0;
            case "SSS" -> 60.0;
            default -> 1.0;
        };
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

    private double toDouble(Object val, double defaultVal) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        return defaultVal;
    }
}
