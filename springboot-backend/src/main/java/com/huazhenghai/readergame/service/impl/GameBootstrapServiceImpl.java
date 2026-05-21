package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.vo.*;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.BattleLog;
import com.huazhenghai.readergame.entity.PlayerLog;
import com.huazhenghai.readergame.entity.User;
import com.huazhenghai.readergame.entity.PkRecord;
import com.huazhenghai.readergame.entity.Ranking;
import com.huazhenghai.readergame.mapper.BattleLogMapper;
import com.huazhenghai.readergame.mapper.PkRecordMapper;
import com.huazhenghai.readergame.mapper.PlayerLogMapper;
import com.huazhenghai.readergame.mapper.RankingMapper;
import com.huazhenghai.readergame.mapper.UserMapper;
import com.huazhenghai.readergame.service.*;
import com.huazhenghai.readergame.vo.PlayerEquipmentVO;
import com.huazhenghai.readergame.vo.BroadcastSummaryVO;
import com.huazhenghai.readergame.vo.WorldlineSummaryVO;
import com.huazhenghai.readergame.vo.ChatSummaryVO;
import com.huazhenghai.readergame.vo.OnlineSummaryVO;
import com.huazhenghai.readergame.vo.FriendSummaryVO;
import com.huazhenghai.readergame.vo.TradeSummaryVO;
import com.huazhenghai.readergame.vo.PartySummaryVO;
import com.huazhenghai.readergame.vo.WorldBossSummaryVO;
import com.huazhenghai.readergame.vo.FactionSummaryVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏启动引导服务实现.
 * <p>
 * 前端进入游戏时调用, 返回当前用户的完整游戏状态.
 * 包含恢复计算、休息状态、最近日志.
 * </p>
 */
@Service
public class GameBootstrapServiceImpl implements GameBootstrapService {

    private final UserMapper userMapper;
    private final PlayerService playerService;
    private final PlayerLogMapper playerLogMapper;
    private final RecoveryService recoveryService;
    private final TitleService titleService;
    private final AvatarRankService avatarRankService;
    private final ChapterService chapterService;
    private final InventoryService inventoryService;
    private final EquipmentService equipmentService;
    private final SkillService skillService;
    private final BattleLogMapper battleLogMapper;
    private final PkRecordMapper pkRecordMapper;
    private final RankingMapper rankingMapper;
    private final BroadcastService broadcastService;
    private final WorldlineService worldlineService;
    private final ChatService chatService;
    private final OnlinePlayerService onlinePlayerService;
    private final FriendService friendService;
    private final TradeService tradeService;
    private final PartyService partyService;
    private final WorldBossService worldBossService;
    private final FactionService factionService;
    private final QuestService questService;

    public GameBootstrapServiceImpl(UserMapper userMapper,
                                    PlayerService playerService,
                                    PlayerLogMapper playerLogMapper,
                                    RecoveryService recoveryService,
                                    TitleService titleService,
                                    AvatarRankService avatarRankService,
                                    ChapterService chapterService,
                                    InventoryService inventoryService,
                                    EquipmentService equipmentService,
                                    SkillService skillService,
                                    BattleLogMapper battleLogMapper,
                                    PkRecordMapper pkRecordMapper,
                                    RankingMapper rankingMapper,
                                    BroadcastService broadcastService,
                                    WorldlineService worldlineService,
                                    ChatService chatService,
                                    OnlinePlayerService onlinePlayerService,
                                    FriendService friendService,
                                    TradeService tradeService,
                                    PartyService partyService,
                                    WorldBossService worldBossService,
                                    FactionService factionService,
                                    QuestService questService) {
        this.userMapper = userMapper;
        this.playerService = playerService;
        this.playerLogMapper = playerLogMapper;
        this.recoveryService = recoveryService;
        this.titleService = titleService;
        this.avatarRankService = avatarRankService;
        this.chapterService = chapterService;
        this.inventoryService = inventoryService;
        this.equipmentService = equipmentService;
        this.skillService = skillService;
        this.battleLogMapper = battleLogMapper;
        this.pkRecordMapper = pkRecordMapper;
        this.rankingMapper = rankingMapper;
        this.broadcastService = broadcastService;
        this.worldlineService = worldlineService;
        this.chatService = chatService;
        this.onlinePlayerService = onlinePlayerService;
        this.friendService = friendService;
        this.tradeService = tradeService;
        this.partyService = partyService;
        this.worldBossService = worldBossService;
        this.factionService = factionService;
        this.questService = questService;
    }

    /**
     * 获取游戏启动引导数据.
     * <ol>
     *   <li>查找用户信息</li>
     *   <li>查找对应的玩家角色</li>
     *   <li>应用被动恢复 (体力/HP)</li>
     *   <li>获取最近 50 条日志</li>
     *   <li>组装 GameBootstrapVO (含 restState)</li>
     * </ol>
     */
    @Override
    public GameBootstrapVO bootstrap(Long userId) {
        // 1. 查找用户
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new com.huazhenghai.readergame.common.BusinessException(
                    ErrorCode.UNAUTHORIZED, "用户不存在");
        }

        // 2. 查找玩家角色
        Player player = playerService.findByUserId(userId);
        PlayerVO playerVO = null;
        List<LogEntry> recentLogs = new ArrayList<>();
        RestStateVO restState = null;

        // Phase 3 成长信息
        ChapterProgressVO chapterProgress = null;
        AvatarRankVO avatarRankInfo = null;
        List<com.huazhenghai.readergame.vo.PlayerTitleVO> titles = null;
        Map<String, Object> starstreamTier = null;

        if (player != null) {
            // 3. 应用被动恢复
            Map<String, Object> stats = recoveryService.applyRecovery(player);
            playerVO = playerService.getPlayer(player.getId());
            recentLogs = getRecentLogs(player.getId(), 50);

            // 4. 构建休息状态
            restState = new RestStateVO();
            restState.setIsResting(Boolean.TRUE.equals(stats.get("isResting")));
            Object restStartedAt = stats.get("restStartedAt");
            restState.setRestStartedAt(restStartedAt != null ? restStartedAt.toString() : null);
            restState.setHp(toInt(stats.get("hp"), 100));
            restState.setMaxHp(toInt(stats.get("maxHp"), 100));
            restState.setStamina(toInt(stats.get("stamina"), 50));
            restState.setMaxStamina(toInt(stats.get("maxStamina"), 50));
            Object lastRecoveryAt = stats.get("lastRecoveryAt");
            restState.setLastRecoveryAt(lastRecoveryAt != null ? lastRecoveryAt.toString() : null);

            // 5. Phase 3: 获取成长系统信息
            try {
                chapterProgress = chapterService.getCurrentChapter(player.getId());
            } catch (Exception ignored) {
            }
            try {
                avatarRankInfo = avatarRankService.getAvatarRankInfo(player.getId());
            } catch (Exception ignored) {
            }
            try {
                titles = titleService.getPlayerTitles(player.getId());
            } catch (Exception ignored) {
            }
            if (stats != null) {
                int channelHeat = toInt(stats.get("channelHeat"), 0);
                starstreamTier = avatarRankService.getStarstreamTier(channelHeat);
            }
        }

        // Phase 4: 物品背包摘要
        List<Map<String, Object>> inventorySummary = null;
        if (player != null) {
            try {
                List<InventoryItemVO> invItems = inventoryService.getInventory(player.getId(), userId);
                if (invItems != null && !invItems.isEmpty()) {
                    inventorySummary = new ArrayList<>();
                    for (InventoryItemVO item : invItems) {
                        Map<String, Object> summary = new java.util.LinkedHashMap<>();
                        summary.put("itemKey", item.getItemKey());
                        summary.put("itemName", item.getName());
                        summary.put("quantity", item.getQuantity());
                        inventorySummary.add(summary);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // Phase 5A: 装备摘要
        Map<String, Object> equipmentSummary = null;
        if (player != null) {
            try {
                List<PlayerEquipmentVO> peList = equipmentService.getPlayerEquipment(player.getId(), userId);
                if (peList != null) {
                    int ownedCount = peList.size();
                    int equippedCount = 0;
                    boolean hasBroken = false;
                    for (PlayerEquipmentVO pe : peList) {
                        if (pe.isEquipped()) equippedCount++;
                        if (pe.getDurability() <= 0) hasBroken = true;
                    }
                    equipmentSummary = new java.util.LinkedHashMap<>();
                    equipmentSummary.put("ownedCount", ownedCount);
                    equipmentSummary.put("equippedCount", equippedCount);
                    equipmentSummary.put("hasBrokenEquipment", hasBroken);
                }
            } catch (Exception ignored) {
            }
        }

        // Phase 5B: 技能摘要
        SkillSummaryVO skillSummary = null;
        if (player != null) {
            try {
                skillSummary = skillService.getSkillSummary(player.getId());
            } catch (Exception ignored) {
            }
        }

        // Phase 5C: 战斗摘要
        Map<String, Object> combatSummary = null;
        if (player != null) {
            try {
                QueryWrapper<BattleLog> blQuery = new QueryWrapper<>();
                blQuery.eq("player_id", player.getId())
                       .orderByDesc("created_at")
                       .last("LIMIT 10");
                List<BattleLog> recentBattles = battleLogMapper.selectList(blQuery);
                if (recentBattles != null && !recentBattles.isEmpty()) {
                    combatSummary = new java.util.LinkedHashMap<>();
                    combatSummary.put("recentBattleCount", recentBattles.size());
                    combatSummary.put("lastBattleResult", recentBattles.get(0).getResult());
                }
            } catch (Exception ignored) {
            }
        }

        // Phase 6: PK摘要
        Map<String, Object> pkSummary = null;
        if (player != null) {
            try {
                QueryWrapper<PkRecord> prQuery = new QueryWrapper<>();
                prQuery.eq("attacker_id", player.getId()).or().eq("defender_id", player.getId())
                       .orderByDesc("created_at")
                       .last("LIMIT 10");
                List<PkRecord> recentPk = pkRecordMapper.selectList(prQuery);
                if (recentPk != null && !recentPk.isEmpty()) {
                    pkSummary = new java.util.LinkedHashMap<>();
                    pkSummary.put("recentPkCount", recentPk.size());
                    pkSummary.put("lastPkWinnerId", recentPk.get(0).getWinnerId());
                }
            } catch (Exception ignored) {
            }

            try {
                QueryWrapper<Ranking> rQuery = new QueryWrapper<>();
                rQuery.eq("player_id", player.getId());
                Ranking ranking = rankingMapper.selectOne(rQuery);
                if (ranking != null) {
                    if (pkSummary == null) pkSummary = new java.util.LinkedHashMap<>();
                    pkSummary.put("rating", ranking.getRating());
                    pkSummary.put("wins", ranking.getWins());
                    pkSummary.put("losses", ranking.getLosses());
                }
            } catch (Exception ignored) {
            }
        }

        // Phase 7: 星流放送 + 世界线摘要
        Map<String, Object> broadcastSummary = null;
        try {
            BroadcastSummaryVO bs = broadcastService.getBroadcastSummary();
            broadcastSummary = new LinkedHashMap<>();
            broadcastSummary.put("activeCount", bs.getActiveCount());
            broadcastSummary.put("totalContributors", bs.getTotalContributors());
            broadcastSummary.put("topEvents", bs.getTopEvents());
        } catch (Exception ignored) {
        }

        Map<String, Object> worldlineSummary = null;
        try {
            WorldlineSummaryVO ws = worldlineService.getWorldlineSummary();
            worldlineSummary = new LinkedHashMap<>();
            worldlineSummary.put("worldlineShift", ws.getWorldlineShift());
            worldlineSummary.put("starstreamAttention", ws.getStarstreamAttention());
            worldlineSummary.put("scenarioPressure", ws.getScenarioPressure());
            worldlineSummary.put("dangerLevel", ws.getDangerLevel());
        } catch (Exception ignored) {
        }

        // Phase 8A: 聊天 + 在线摘要
        Map<String, Object> chatSummary = null;
        try {
            ChatSummaryVO cs = chatService.getChatSummary();
            chatSummary = new LinkedHashMap<>();
            chatSummary.put("recentMessageCount", cs.getRecentMessageCount());
            if (cs.getLatestMessage() != null) {
                Map<String, Object> lm = new LinkedHashMap<>();
                lm.put("id", cs.getLatestMessage().getId());
                lm.put("senderName", cs.getLatestMessage().getSenderName());
                lm.put("content", cs.getLatestMessage().getContent());
                lm.put("createdAt", cs.getLatestMessage().getCreatedAt());
                chatSummary.put("latestMessage", lm);
            }
        } catch (Exception ignored) {
        }

        Map<String, Object> onlineSummary = null;
        try {
            OnlineSummaryVO os = onlinePlayerService.getOnlineSummary();
            onlineSummary = new LinkedHashMap<>();
            onlineSummary.put("onlineCount", os.getOnlineCount());
        } catch (Exception ignored) {
        }

        // Phase 8B: 好友摘要
        Map<String, Object> friendSummary = null;
        if (player != null) {
            try {
                FriendSummaryVO fs = friendService.getFriendSummary(player.getId());
                friendSummary = new LinkedHashMap<>();
                friendSummary.put("friendCount", fs.getFriendCount());
                friendSummary.put("pendingRequestCount", fs.getPendingRequestCount());
            } catch (Exception ignored) {
            }
        }

        // Phase 8C: 交易摘要
        Map<String, Object> tradeSummary = null;
        if (player != null) {
            try {
                TradeSummaryVO ts = tradeService.getTradeSummary(player.getId());
                tradeSummary = new LinkedHashMap<>();
                tradeSummary.put("activeListingCount", ts.getActiveListingCount());
                tradeSummary.put("soldCount", ts.getSoldCount());
                tradeSummary.put("boughtCount", ts.getBoughtCount());
                tradeSummary.put("recentTradeCount", ts.getRecentTradeCount());
            } catch (Exception ignored) {
            }
        }

        // Phase 8D: 组队摘要
        Map<String, Object> partySummary = null;
        if (player != null) {
            try {
                PartySummaryVO ps = partyService.getPartySummary(player.getId());
                partySummary = new LinkedHashMap<>();
                partySummary.put("inParty", ps.isInParty());
                partySummary.put("partyNo", ps.getPartyNo());
                partySummary.put("partyName", ps.getPartyName());
                partySummary.put("role", ps.getRole());
                partySummary.put("memberCount", ps.getMemberCount());
                partySummary.put("maxMembers", ps.getMaxMembers());
                partySummary.put("onlineMemberCount", ps.getOnlineMemberCount());
            } catch (Exception ignored) {
            }
        }

        // Phase 8F: 世界Boss摘要
        Map<String, Object> worldBossSummary = null;
        if (player != null) {
            try {
                WorldBossSummaryVO wbs = worldBossService.getWorldBossSummary(player.getId());
                worldBossSummary = new LinkedHashMap<>();
                worldBossSummary.put("activeBoss", wbs.isActiveBoss());
                worldBossSummary.put("bossNo", wbs.getBossNo());
                worldBossSummary.put("bossName", wbs.getBossName());
                worldBossSummary.put("status", wbs.getStatus());
                worldBossSummary.put("hpPercent", wbs.getHpPercent());
                worldBossSummary.put("myDamage", wbs.getMyDamage());
                worldBossSummary.put("myRank", wbs.getMyRank());
                worldBossSummary.put("canClaimReward", wbs.isCanClaimReward());
            } catch (Exception ignored) {
            }
        }

        // Phase 8G: 阵营摘要
        Map<String, Object> factionSummary = null;
        if (player != null) {
            try {
                FactionSummaryVO fs = factionService.getFactionSummary(player.getId());
                factionSummary = new LinkedHashMap<>();
                factionSummary.put("joined", fs.isJoined());
                factionSummary.put("factionKey", fs.getFactionKey());
                factionSummary.put("factionName", fs.getFactionName());
                factionSummary.put("role", fs.getRole());
                factionSummary.put("reputation", fs.getReputation());
                factionSummary.put("contributionTotal", fs.getContributionTotal());
                factionSummary.put("factionLevel", fs.getFactionLevel());
                factionSummary.put("factionRank", fs.getFactionRank());
            } catch (Exception ignored) {
            }
        }

        // Phase 8H: 任务摘要
        QuestSummaryVO questSummary = null;
        if (player != null) {
            try {
                questService.ensurePlayerQuests(player.getId());
                questSummary = questService.getQuestSummary(player.getId());
            } catch (Exception ignored) {
            }
        }

        // 6. 组装返回结果
        return GameBootstrapVO.builder()
                .user(UserVO.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .role(user.getRole())
                        .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null)
                        .build())
                .player(playerVO)
                .recentLogs(recentLogs)
                .restState(restState)
                .chapterProgress(chapterProgress)
                .avatarRankInfo(avatarRankInfo)
                .titles(titles)
                .starstreamTier(starstreamTier)
                .inventorySummary(inventorySummary)
                .equipmentSummary(equipmentSummary)
                .skillSummary(skillSummary)
                .combatSummary(combatSummary)
                .pkSummary(pkSummary)
                .broadcastSummary(broadcastSummary)
                .worldlineSummary(worldlineSummary)
                .chatSummary(chatSummary)
                .onlineSummary(onlineSummary)
                .friendSummary(friendSummary)
                .tradeSummary(tradeSummary)
                .partySummary(partySummary)
                .worldBossSummary(worldBossSummary)
                .factionSummary(factionSummary)
                .questSummary(questSummary)
                .build();
    }

    /**
     * 获取玩家最近的 N 条日志.
     */
    private List<LogEntry> getRecentLogs(Long playerId, int limit) {
        QueryWrapper<PlayerLog> query = new QueryWrapper<>();
        query.eq("player_id", playerId)
             .orderByDesc("created_at")
             .last("LIMIT " + limit);
        List<PlayerLog> logs = playerLogMapper.selectList(query);

        List<LogEntry> result = new ArrayList<>();
        for (PlayerLog log : logs) {
            result.add(new LogEntry(
                    log.getId(),
                    log.getMessage(),
                    log.getType(),
                    log.getCreatedAt()
            ));
        }
        Collections.reverse(result);
        return result;
    }

    private int toInt(Object val, int defaultVal) {
        if (val instanceof Number) return ((Number) val).intValue();
        return defaultVal;
    }
}
