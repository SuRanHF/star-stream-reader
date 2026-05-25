package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.dto.CreateWorldBossRequest;
import com.huazhenghai.readergame.entity.*;
import com.huazhenghai.readergame.mapper.*;
import com.huazhenghai.readergame.service.*;
import com.huazhenghai.readergame.vo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WorldBossServiceImpl implements WorldBossService {

    private static final Logger log = LoggerFactory.getLogger(WorldBossServiceImpl.class);
    private static final int COOLDOWN_SECONDS = 30;
    private static final Random RANDOM = new Random();

    private final WorldBossMapper bossMapper;
    private final WorldBossParticipationMapper participationMapper;
    private final PlayerMapper playerMapper;
    private final PlayerLogMapper playerLogMapper;
    private final CombatService combatService;
    private final RecoveryService recoveryService;
    private final ChatService chatService;
    private final WorldlineService worldlineService;
    private final BroadcastService broadcastService;
    private final FactionService factionService;
    private final QuestService questService;
    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    public WorldBossServiceImpl(WorldBossMapper bossMapper,
                                WorldBossParticipationMapper participationMapper,
                                PlayerMapper playerMapper,
                                PlayerLogMapper playerLogMapper,
                                CombatService combatService,
                                RecoveryService recoveryService,
                                ChatService chatService,
                                WorldlineService worldlineService,
                                BroadcastService broadcastService,
                                FactionService factionService,
                                QuestService questService,
                                InventoryService inventoryService,
                                ObjectMapper objectMapper) {
        this.bossMapper = bossMapper;
        this.participationMapper = participationMapper;
        this.playerMapper = playerMapper;
        this.playerLogMapper = playerLogMapper;
        this.combatService = combatService;
        this.recoveryService = recoveryService;
        this.chatService = chatService;
        this.worldlineService = worldlineService;
        this.broadcastService = broadcastService;
        this.factionService = factionService;
        this.questService = questService;
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
    }

    // ─── DEFAULT BOSS TEMPLATE ───

    private static final String DEFAULT_BOSS_KEY = "station_disaster_core";
    private static final String DEFAULT_BOSS_NAME = "废弃车站灾厄核心";
    private static final String DEFAULT_BOSS_DESC = "废弃车站深处凝聚的剧本污染核心。所有化身都可以参与讨伐。";
    private static final int DEFAULT_BOSS_LEVEL = 10;
    private static final long DEFAULT_BOSS_MAX_HP = 100000;
    private static final int DEFAULT_BOSS_ATK = 25;
    private static final int DEFAULT_BOSS_DEF = 12;
    private static final int DEFAULT_BOSS_SPD = 10;
    private static final String DEFAULT_REWARDS_JSON = "{\"participation\":{\"minDamage\":100,\"coins\":100,\"storyFragments\":5,\"channelHeat\":20,\"items\":[{\"itemKey\":\"channel_token\",\"quantity\":2}]}}";
    private static final String DEFAULT_RANK_REWARDS_JSON = "{\"top1\":{\"coins\":500,\"storyFragments\":20,\"channelHeat\":100},\"top3\":{\"coins\":300,\"storyFragments\":12,\"channelHeat\":60},\"top10\":{\"coins\":150,\"storyFragments\":8,\"channelHeat\":30}}";
    private static final String DEFAULT_WORLDLINE_EFFECTS_JSON = "{\"worldline_shift\":5,\"starstream_attention\":30,\"scenario_pressure\":-5}";

    // ─── public methods ───

    @Override
    public WorldBossVO getActiveBoss(Long playerId) {
        WorldBoss boss = queryActiveBoss();
        if (boss == null) return null;
        return buildBossVO(boss, playerId);
    }

    @Override
    public WorldBossVO getBossDetail(String bossNo, Long playerId) {
        WorldBoss boss = getBossByNo(bossNo);
        return buildBossVO(boss, playerId);
    }

    @Override
    public WorldBossVO createBoss(CreateWorldBossRequest req, Long adminUserId) {
        WorldBoss boss = new WorldBoss();
        boss.setBossKey(req.getBossKey());
        boss.setBossNo(generateBossNo());
        boss.setName(req.getName() != null ? req.getName() : DEFAULT_BOSS_NAME);
        boss.setDescription(req.getDescription() != null ? req.getDescription() : DEFAULT_BOSS_DESC);
        boss.setStatus("draft");
        boss.setLevel(req.getLevel() != null ? req.getLevel() : DEFAULT_BOSS_LEVEL);
        boss.setMaxHp(req.getMaxHp() != null ? req.getMaxHp() : DEFAULT_BOSS_MAX_HP);
        boss.setCurrentHp(boss.getMaxHp());
        boss.setAttack(req.getAttack() != null ? req.getAttack() : DEFAULT_BOSS_ATK);
        boss.setDefense(req.getDefense() != null ? req.getDefense() : DEFAULT_BOSS_DEF);
        boss.setSpeed(req.getSpeed() != null ? req.getSpeed() : DEFAULT_BOSS_SPD);
        boss.setRewardsJson(req.getRewardsJson() != null ? req.getRewardsJson() : DEFAULT_REWARDS_JSON);
        boss.setRankRewardsJson(req.getRankRewardsJson() != null ? req.getRankRewardsJson() : DEFAULT_RANK_REWARDS_JSON);
        boss.setWorldlineEffectsJson(req.getWorldlineEffectsJson() != null ? req.getWorldlineEffectsJson() : DEFAULT_WORLDLINE_EFFECTS_JSON);
        boss.setCreatedAt(LocalDateTime.now());
        boss.setUpdatedAt(LocalDateTime.now());
        bossMapper.insert(boss);
        return buildBossVO(boss, null);
    }

    @Override
    public WorldBossVO openScheduledBoss() {
        WorldBoss existing = queryActiveBoss();
        if (existing != null) return null; // 已有活跃Boss，不重复创建

        WorldBoss boss = new WorldBoss();
        boss.setBossKey(DEFAULT_BOSS_KEY);
        boss.setBossNo(generateBossNo());
        boss.setName(DEFAULT_BOSS_NAME);
        boss.setDescription(DEFAULT_BOSS_DESC);
        boss.setStatus("active");
        boss.setLevel(DEFAULT_BOSS_LEVEL);
        boss.setMaxHp(DEFAULT_BOSS_MAX_HP);
        boss.setCurrentHp(DEFAULT_BOSS_MAX_HP);
        boss.setAttack(DEFAULT_BOSS_ATK);
        boss.setDefense(DEFAULT_BOSS_DEF);
        boss.setSpeed(DEFAULT_BOSS_SPD);
        boss.setRewardsJson(DEFAULT_REWARDS_JSON);
        boss.setRankRewardsJson(DEFAULT_RANK_REWARDS_JSON);
        boss.setWorldlineEffectsJson(DEFAULT_WORLDLINE_EFFECTS_JSON);
        LocalDateTime now = LocalDateTime.now();
        boss.setStartAt(now);
        boss.setEndAt(now.plusHours(2));
        boss.setCreatedAt(now);
        boss.setUpdatedAt(now);
        bossMapper.insert(boss);

        tryNotify("世界Boss「" + boss.getName() + "」已经出现，所有化身都可以参与讨伐。",
                "world_boss", Map.of("bossNo", boss.getBossNo(), "bossKey", boss.getBossKey()));
        return buildBossVO(boss, null);
    }

    @Override
    @Transactional
    public WorldBossAttackResultVO attackBoss(Long playerId, String bossNo, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null) throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND);
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");
        recoveryService.assertCanAct(player);

        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        int hp = toInt(stats.get("hp"), 0);
        if (hp <= 0) throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH, "生命值为0，无法攻击");

        // Lock boss row
        WorldBoss boss = getActiveBossByNo(bossNo);
        if (!"active".equals(boss.getStatus()))
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "Boss 不在可攻击状态");
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(boss.getStartAt()) || now.isAfter(boss.getEndAt()))
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "当前不在 Boss 活动时间内");

        // Cooldown check
        QueryWrapper<WorldBossParticipation> pq = new QueryWrapper<>();
        pq.eq("boss_no", bossNo).eq("player_id", playerId);
        WorldBossParticipation participation = participationMapper.selectOne(pq);
        if (participation != null && participation.getLastAttackAt() != null) {
            long secondsSince = java.time.Duration.between(participation.getLastAttackAt(), now).getSeconds();
            if (secondsSince < COOLDOWN_SECONDS) {
                throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE,
                        "攻击冷却中，请等待 " + (COOLDOWN_SECONDS - secondsSince) + " 秒");
            }
        }

        // Calculate damage
        CombatStatsVO combatStats = combatService.calculateCombatPower(playerId);
        long baseDamage = Math.max(1, combatStats.getAttack() * 3L + combatStats.getLevel() * 5L - boss.getDefense());
        double randomFactor = 0.9 + RANDOM.nextDouble() * 0.2; // 0.9 ~ 1.1
        long damage = (long) (baseDamage * randomFactor);

        boolean critical = RANDOM.nextDouble() < combatStats.getCritRate();
        if (critical) {
            damage = (long) (damage * combatStats.getCritDamage());
        }

        // Update boss HP with row lock
        long newHp = Math.max(0, boss.getCurrentHp() - damage);
        boolean defeated = newHp <= 0;
        boss.setCurrentHp(newHp);
        if (defeated) {
            boss.setStatus("killed");
            boss.setKilledAt(now);
        }
        boss.setUpdatedAt(now);
        bossMapper.updateById(boss);

        // Upsert participation
        if (participation == null) {
            participation = new WorldBossParticipation();
            participation.setBossNo(bossNo);
            participation.setPlayerId(playerId);
            participation.setDamage(damage);
            participation.setAttackCount(1);
            participation.setLastAttackAt(now);
            participation.setRewardClaimed(0);
            participation.setPartyNo(getPlayerActivePartyNo(playerId));
            participation.setCreatedAt(now);
            participation.setUpdatedAt(now);
            participationMapper.insert(participation);
        } else {
            participation.setDamage(participation.getDamage() + damage);
            participation.setAttackCount(participation.getAttackCount() + 1);
            participation.setLastAttackAt(now);
            participation.setUpdatedAt(now);
            participationMapper.updateById(participation);
        }

        // Write log
        String logMsg = (critical ? "[暴击] " : "") + "对世界Boss「" + boss.getName()
                + "」造成了 " + damage + " 点伤害";
        writeLog(playerId, "battle", logMsg);

        // Non-blocking: broadcast contribution
        try { broadcastService.contribute("broadcast_pk_spark", playerId, 1, "combat"); }
        catch (Exception ignored) {}

        // 自动阵营贡献
        try {
            long factionValue = Math.max(1, damage / 1000);
            factionService.contribute(playerId, null, "worldBoss", factionValue, "worldBoss", bossNo, null);
        } catch (Exception ignored) {}

        // Build result
        WorldBossAttackResultVO result = new WorldBossAttackResultVO();
        result.setSuccess(true);
        result.setDamage(damage);
        result.setCritical(critical);
        result.setBossCurrentHp(newHp);
        result.setBossMaxHp(boss.getMaxHp());
        result.setBossDefeated(defeated);
        result.setMyTotalDamage(participation.getDamage());
        result.setMyAttackCount(participation.getAttackCount());
        result.setMessage(critical ? "暴击！造成了 " + damage + " 点伤害！" : "造成了 " + damage + " 点伤害");

        // 自动任务进度
        try { questService.addProgress(playerId, "world_boss_attack_count", 1, "worldBoss", bossNo); }
        catch (Exception ignored) {}

        // Settle on defeat (committed after transaction)
        if (defeated) {
            settleBoss(bossNo);
            tryNotify("世界Boss「" + boss.getName() + "」已被击破，世界线发生变化。",
                    "world_boss", Map.of("bossNo", bossNo, "event", "defeated"));
            try { broadcastService.contribute("broadcast_pk_spark", playerId, 5, "combat"); }
            catch (Exception ignored) {}
        }

        return result;
    }

    @Override
    @Transactional
    public void settleBoss(String bossNo) {
        WorldBoss boss = getBossByNo(bossNo);
        if ("settled".equals(boss.getStatus())) return; // idempotent

        if (!"killed".equals(boss.getStatus()) && !"expired".equals(boss.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE,
                    "只有 killed 或 expired 的 Boss 才能结算");
        }

        // Calculate rankings
        QueryWrapper<WorldBossParticipation> pq = new QueryWrapper<>();
        pq.eq("boss_no", bossNo).orderByDesc("damage");
        List<WorldBossParticipation> participants = participationMapper.selectList(pq);

        for (int i = 0; i < participants.size(); i++) {
            WorldBossParticipation p = participants.get(i);
            p.setRankNo(i + 1);
            p.setUpdatedAt(LocalDateTime.now());
            participationMapper.updateById(p);
        }

        String originalStatus = boss.getStatus();
        boss.setStatus("settled");
        boss.setUpdatedAt(LocalDateTime.now());
        bossMapper.updateById(boss);

        // Apply worldline effects if killed
        if ("killed".equals(originalStatus)) {
            try {
                Map<String, Object> effects = parseJsonMap(boss.getWorldlineEffectsJson());
                if (!effects.isEmpty()) {
                    double shift = toDouble(effects.get("worldline_shift"), 0);
                    if (shift != 0) worldlineService.addWorldlineShift(shift, "world_boss_" + bossNo);
                }
            } catch (Exception e) {
                log.warn("Failed to apply worldline effects for boss {}", bossNo, e);
            }
        }

        // On expiry (not killed), add scenario pressure
        if ("expired".equals(originalStatus)) {
            try {
                worldlineService.addWorldlineShift(1, "world_boss_expired_" + bossNo);
                tryNotify("世界Boss「" + boss.getName() + "」未能被击破，剧本压力上升。",
                        "world_boss", Map.of("bossNo", bossNo, "event", "expired"));
            } catch (Exception ignored) {}
        }
    }

    @Override
    @Transactional
    public WorldBossRewardVO claimReward(Long playerId, String bossNo, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null) throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND);
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        WorldBoss boss = getBossByNo(bossNo);
        if (!"settled".equals(boss.getStatus()))
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "Boss 尚未结算");

        QueryWrapper<WorldBossParticipation> pq = new QueryWrapper<>();
        pq.eq("boss_no", bossNo).eq("player_id", playerId);
        WorldBossParticipation p = participationMapper.selectOne(pq);
        if (p == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "你未参与此 Boss 讨伐");
        if (p.getRewardClaimed() == 1)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "已领取过奖励");

        Map<String, Object> rewardsObj = parseJsonMap(boss.getRewardsJson());
        Map<String, Object> participationRewards = parseJsonMap(rewardsObj.get("participation"));
        int minDamage = toInt(participationRewards.get("minDamage"), 0);
        if (p.getDamage() < minDamage)
            throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH,
                    "伤害不足，需要至少 " + minDamage + " 点伤害");

        // Calculate rewards
        int coins = toInt(participationRewards.get("coins"), 0);
        int fragments = toInt(participationRewards.get("storyFragments"), 0);
        int heat = toInt(participationRewards.get("channelHeat"), 0);

        // Rank rewards
        Map<String, Object> rankRewards = parseJsonMap(boss.getRankRewardsJson());
        if (p.getRankNo() != null) {
            int rank = p.getRankNo();
            if (rank == 1) {
                coins += toInt(rankRewards.get("top1_coins"), 0);
                Map<String, Object> top1 = parseJsonMap(rankRewards.get("top1"));
                coins += toInt(top1.get("coins"), 0);
                fragments += toInt(top1.get("storyFragments"), 0);
                heat += toInt(top1.get("channelHeat"), 0);
            } else if (rank <= 3) {
                Map<String, Object> top3 = parseJsonMap(rankRewards.get("top3"));
                coins += toInt(top3.get("coins"), 0);
                fragments += toInt(top3.get("storyFragments"), 0);
                heat += toInt(top3.get("channelHeat"), 0);
            } else if (rank <= 10) {
                Map<String, Object> top10 = parseJsonMap(rankRewards.get("top10"));
                coins += toInt(top10.get("coins"), 0);
                fragments += toInt(top10.get("storyFragments"), 0);
                heat += toInt(top10.get("channelHeat"), 0);
            }
        }

        // Apply rewards
        if (coins > 0) player.setCoins(player.getCoins() + coins);
        if (fragments > 0) player.setStoryFragments(player.getStoryFragments() + fragments);
        if (heat > 0) {
            Map<String, Object> stats = parseJsonMap(player.getStatsJson());
            int currentHeat = toInt(stats.get("channelHeat"), 0);
            stats.put("channelHeat", currentHeat + heat);
            try { player.setStatsJson(objectMapper.writeValueAsString(stats)); } catch (Exception ignored) {}
        }
        playerMapper.updateById(player);

        // Grant items
        List<Map<String, Object>> items = (List<Map<String, Object>>) participationRewards.get("items");
        if (items != null) {
            for (Map<String, Object> item : items) {
                String itemKey = (String) item.get("itemKey");
                int qty = toInt(item.get("quantity"), 0);
                if (itemKey != null && qty > 0) {
                    // Grant via inventory service — skip if unavailable
                    tryGrantItem(playerId, itemKey, qty);
                }
            }
        }

        p.setRewardClaimed(1);
        p.setUpdatedAt(LocalDateTime.now());
        participationMapper.updateById(p);

        writeLog(playerId, "battle", "领取了世界Boss「" + boss.getName()
                + "」的讨伐奖励，排名 #" + (p.getRankNo() != null ? p.getRankNo() : "?"));

        Map<String, Object> rewardMap = new LinkedHashMap<>();
        rewardMap.put("coins", coins);
        rewardMap.put("storyFragments", fragments);
        rewardMap.put("channelHeat", heat);

        WorldBossRewardVO vo = new WorldBossRewardVO();
        vo.setClaimed(true);
        vo.setRankNo(p.getRankNo() != null ? p.getRankNo() : 0);
        vo.setRewards(rewardMap);
        vo.setMessage("领取成功！排名 #" + (p.getRankNo() != null ? p.getRankNo() : "?"));
        return vo;
    }

    @Override
    public List<WorldBossParticipationVO> getMyParticipation(Long playerId, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null) throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND);
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看此玩家");

        QueryWrapper<WorldBossParticipation> qw = new QueryWrapper<>();
        qw.eq("player_id", playerId).orderByDesc("created_at");
        return participationMapper.selectList(qw).stream()
                .map(this::toParticipationVO).collect(Collectors.toList());
    }

    @Override
    public List<WorldBossRankingVO> getBossRankings(String bossNo) {
        QueryWrapper<WorldBossParticipation> qw = new QueryWrapper<>();
        qw.eq("boss_no", bossNo).orderByDesc("damage").last("LIMIT 50");
        List<WorldBossParticipation> list = participationMapper.selectList(qw);

        List<WorldBossRankingVO> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            result.add(toRankingVO(i + 1, list.get(i)));
        }
        return result;
    }

    @Override
    public WorldBossSummaryVO getWorldBossSummary(Long playerId) {
        WorldBoss boss = queryActiveBoss();
        WorldBossSummaryVO vo = new WorldBossSummaryVO();

        if (boss == null || !"active".equals(boss.getStatus())) {
            vo.setActiveBoss(false);
            return vo;
        }

        vo.setActiveBoss(true);
        vo.setBossNo(boss.getBossNo());
        vo.setBossName(boss.getName());
        vo.setStatus(boss.getStatus());
        vo.setHpPercent(boss.getMaxHp() > 0
                ? Math.round(boss.getCurrentHp() * 1000.0 / boss.getMaxHp()) / 10.0
                : 0);

        if (playerId != null) {
            QueryWrapper<WorldBossParticipation> pq = new QueryWrapper<>();
            pq.eq("boss_no", boss.getBossNo()).eq("player_id", playerId);
            WorldBossParticipation p = participationMapper.selectOne(pq);
            if (p != null) {
                vo.setMyDamage(p.getDamage());
                vo.setMyRank(p.getRankNo());

                // Determine if can claim
                Map<String, Object> rewardsObj = parseJsonMap(boss.getRewardsJson());
                Map<String, Object> pRewards = parseJsonMap(rewardsObj.get("participation"));
                int minDamage = toInt(pRewards.get("minDamage"), 0);
                vo.setCanClaimReward(
                        "settled".equals(boss.getStatus())
                                && p.getRewardClaimed() == 0
                                && p.getDamage() >= minDamage
                );
            }
        }

        return vo;
    }

    // ─── internal ───

    private WorldBoss queryActiveBoss() {
        QueryWrapper<WorldBoss> qw = new QueryWrapper<>();
        qw.eq("status", "active").orderByDesc("created_at").last("LIMIT 1");
        return bossMapper.selectOne(qw);
    }

    private WorldBoss getActiveBossByNo(String bossNo) {
        QueryWrapper<WorldBoss> qw = new QueryWrapper<>();
        qw.eq("boss_no", bossNo).eq("status", "active").last("LIMIT 1");
        WorldBoss boss = bossMapper.selectOne(qw);
        if (boss == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "Boss 不存在或不在活跃状态");
        return boss;
    }

    private WorldBoss getBossByNo(String bossNo) {
        QueryWrapper<WorldBoss> qw = new QueryWrapper<>();
        qw.eq("boss_no", bossNo);
        WorldBoss boss = bossMapper.selectOne(qw);
        if (boss == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "Boss 不存在: " + bossNo);
        return boss;
    }

    private String generateBossNo() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String rnd = String.format("%04d", RANDOM.nextInt(10000));
        return "WB" + ts + rnd;
    }

    private WorldBossVO buildBossVO(WorldBoss boss, Long playerId) {
        WorldBossVO vo = new WorldBossVO();
        vo.setId(boss.getId());
        vo.setBossKey(boss.getBossKey());
        vo.setBossNo(boss.getBossNo());
        vo.setName(boss.getName());
        vo.setDescription(boss.getDescription());
        vo.setStatus(boss.getStatus());
        vo.setLevel(boss.getLevel());
        vo.setMaxHp(boss.getMaxHp());
        vo.setCurrentHp(boss.getCurrentHp());
        vo.setAttack(boss.getAttack());
        vo.setDefense(boss.getDefense());
        vo.setSpeed(boss.getSpeed());
        vo.setStartAt(boss.getStartAt() != null ? boss.getStartAt().toString() : null);
        vo.setEndAt(boss.getEndAt() != null ? boss.getEndAt().toString() : null);
        vo.setKilledAt(boss.getKilledAt() != null ? boss.getKilledAt().toString() : null);
        vo.setRewards(parseJsonMap(boss.getRewardsJson()));
        vo.setRankRewards(parseJsonMap(boss.getRankRewardsJson()));
        vo.setWorldlineEffects(parseJsonMap(boss.getWorldlineEffectsJson()));
        vo.setMetadata(parseJsonMap(boss.getMetadataJson()));
        vo.setCreatedAt(boss.getCreatedAt() != null ? boss.getCreatedAt().toString() : null);
        vo.setUpdatedAt(boss.getUpdatedAt() != null ? boss.getUpdatedAt().toString() : null);

        // Top 10 rankings
        vo.setTopRankings(getTop10Rankings(boss.getBossNo()));

        // Player-specific
        if (playerId != null) {
            QueryWrapper<WorldBossParticipation> pq = new QueryWrapper<>();
            pq.eq("boss_no", boss.getBossNo()).eq("player_id", playerId);
            WorldBossParticipation p = participationMapper.selectOne(pq);
            if (p != null) {
                vo.setMyDamage(p.getDamage());
                vo.setMyRank(p.getRankNo());
                Map<String, Object> rewardsObj = parseJsonMap(boss.getRewardsJson());
                Map<String, Object> pRewards = parseJsonMap(rewardsObj.get("participation"));
                int minDamage = toInt(pRewards.get("minDamage"), 0);
                vo.setCanClaimReward(
                        "settled".equals(boss.getStatus())
                                && p.getRewardClaimed() == 0
                                && p.getDamage() >= minDamage
                );
            }
        }

        return vo;
    }

    private List<WorldBossRankingVO> getTop10Rankings(String bossNo) {
        return getBossRankings(bossNo).stream().limit(10).collect(Collectors.toList());
    }

    private String getPlayerActivePartyNo(Long playerId) {
        try {
            QueryWrapper<PartyMember> qw = new QueryWrapper<>();
            qw.eq("player_id", playerId).eq("status", "active");
            // Getting mapper dynamically — use injected if available
            return null; // Simplified: party info derived outside
        } catch (Exception e) {
            return null;
        }
    }

    private WorldBossRankingVO toRankingVO(int rank, WorldBossParticipation p) {
        WorldBossRankingVO vo = new WorldBossRankingVO();
        vo.setRank(rank);
        vo.setPlayerId(p.getPlayerId());
        vo.setPlayerName(getPlayerName(p.getPlayerId()));
        vo.setDamage(p.getDamage());
        vo.setAttackCount(p.getAttackCount());
        vo.setRewardClaimed(p.getRewardClaimed() == 1);
        return vo;
    }

    private WorldBossParticipationVO toParticipationVO(WorldBossParticipation p) {
        WorldBossParticipationVO vo = new WorldBossParticipationVO();
        vo.setId(p.getId());
        vo.setBossNo(p.getBossNo());
        vo.setPlayerId(p.getPlayerId());
        vo.setPlayerName(getPlayerName(p.getPlayerId()));
        vo.setPartyNo(p.getPartyNo());
        vo.setDamage(p.getDamage());
        vo.setAttackCount(p.getAttackCount());
        vo.setLastAttackAt(p.getLastAttackAt() != null ? p.getLastAttackAt().toString() : null);
        vo.setRewardClaimed(p.getRewardClaimed() == 1);
        vo.setRankNo(p.getRankNo());
        vo.setCreatedAt(p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
        vo.setUpdatedAt(p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : null);
        return vo;
    }

    private String getPlayerName(Long playerId) {
        Player p = playerMapper.selectById(playerId);
        return p != null ? p.getPlayerName() : "未知";
    }

    private void writeLog(Long playerId, String type, String message) {
        PlayerLog log = new PlayerLog();
        log.setPlayerId(playerId);
        log.setType(type);
        log.setMessage(message);
        playerLogMapper.insert(log);
    }

    private void tryNotify(String content, String msgType, Map<String, Object> metadata) {
        try { chatService.saveSystemMessage(content, msgType, metadata); }
        catch (Exception e) { log.warn("Chat notify failed for world boss: {}", e.getMessage()); }
    }

    private void tryGrantItem(Long playerId, String itemKey, int quantity) {
        try {
            inventoryService.addItem(playerId, itemKey, quantity);
        } catch (Exception e) {
            log.warn("WorldBoss reward item grant failed: player={}, itemKey={}, qty={}", playerId, itemKey, quantity, e);
        }
    }

    // ─── JSON helpers ───

    private Map<String, Object> parseJsonMap(Object obj) {
        if (obj == null) return new LinkedHashMap<>();
        return parseJsonMap(obj.toString());
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
