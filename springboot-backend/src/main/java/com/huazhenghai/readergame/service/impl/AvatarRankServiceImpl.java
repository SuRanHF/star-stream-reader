package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.ResourceBalance;
import com.huazhenghai.readergame.entity.AvatarRankConfig;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.Ranking;
import com.huazhenghai.readergame.entity.PlayerSkill;
import com.huazhenghai.readergame.entity.PlayerTitle;
import com.huazhenghai.readergame.mapper.AvatarRankConfigMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.PlayerSkillMapper;
import com.huazhenghai.readergame.mapper.PlayerTitleMapper;
import com.huazhenghai.readergame.mapper.RankingMapper;
import com.huazhenghai.readergame.mapper.TitleMapper;
import com.huazhenghai.readergame.service.AvatarRankService;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.service.PlayerMaxHpProjectionService;
import com.huazhenghai.readergame.service.PlayerEffectService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import com.huazhenghai.readergame.vo.AvatarRankVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class AvatarRankServiceImpl implements AvatarRankService {

    private final PlayerMapper playerMapper;
    private final PlayerSkillMapper playerSkillMapper;
    private final PlayerTitleMapper playerTitleMapper;
    private final AvatarRankConfigMapper rankConfigMapper;
    private final TitleMapper titleMapper;
    private final RankingMapper rankingMapper;
    private final PlayerLogService playerLogService;
    private final PlayerEffectService playerEffectService;
    private final PlayerResourceTransactionService resourceTransactionService;
    private final ObjectMapper objectMapper;

    public AvatarRankServiceImpl(PlayerMapper playerMapper,
                                  PlayerSkillMapper playerSkillMapper,
                                  PlayerTitleMapper playerTitleMapper,
                                  AvatarRankConfigMapper rankConfigMapper,
                                  TitleMapper titleMapper,
                                  RankingMapper rankingMapper,
                                  PlayerLogService playerLogService,
                                  ObjectMapper objectMapper,
                                  PlayerEffectService playerEffectService,
                                  PlayerResourceTransactionService resourceTransactionService) {
        this.playerMapper = playerMapper;
        this.playerSkillMapper = playerSkillMapper;
        this.playerTitleMapper = playerTitleMapper;
        this.rankConfigMapper = rankConfigMapper;
        this.titleMapper = titleMapper;
        this.rankingMapper = rankingMapper;
        this.playerLogService = playerLogService;
        this.objectMapper = objectMapper;
        this.playerEffectService = playerEffectService;
        this.resourceTransactionService = resourceTransactionService;
    }

    @Override
    public AvatarRankVO getAvatarRankInfo(Long playerId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");

        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        Map<String, Object> stageProgress = parseJsonMap(player.getStageProgressJson());

        String currentRankKey = (String) stats.getOrDefault("avatarRank", "F");
        String storyGrade = (String) stats.getOrDefault("storyGrade", "ordinary");

        QueryWrapper<AvatarRankConfig> rankQuery = new QueryWrapper<>();
        rankQuery.eq("rank_key", currentRankKey).eq("enabled", 1);
        AvatarRankConfig currentRank = rankConfigMapper.selectOne(rankQuery);

        int channelHeat = toInt(stats.get("channelHeat"), 0);
        Map<String, Object> starstreamTier = getStarstreamTier(channelHeat);

        AvatarRankVO vo = new AvatarRankVO();

        // currentRank as object (frontend expects .displayName, .description)
        Map<String, Object> crMap = new LinkedHashMap<>();
        crMap.put("key", currentRankKey);
        crMap.put("displayName", currentRank != null ? currentRank.getDisplayName() : currentRankKey);
        crMap.put("description", currentRank != null ? currentRank.getDescription() : "");
        vo.setCurrentRank(crMap);

        vo.setCurrentRankName(currentRank != null ? currentRank.getRankName() : currentRankKey);
        vo.setCurrentDisplayName(currentRank != null ? currentRank.getDisplayName() : currentRankKey);
        vo.setStoryGrade(storyGrade);
        vo.setStoryGradeLabel(storyGradeLabel(storyGrade));
        vo.setStarstreamTier((String) starstreamTier.get("key"));
        vo.setStarstreamTierLabel((String) starstreamTier.get("label"));
        vo.setCurrentOrder(currentRank != null ? currentRank.getOrderNum() : 1);

        int rankOrder = currentRank != null && currentRank.getOrderNum() != null
                ? currentRank.getOrderNum() : 0;
        Ranking currentPvpRanking = rankingMapper.selectByPlayerId(playerId);
        int rating = currentPvpRanking != null && currentPvpRanking.getRating() != null
                ? currentPvpRanking.getRating() : 1000;
        vo.setRankPosition(rankingMapper.countAvatarRankPlayersAhead(
                rankOrder, toInt(stats.get("level"), 1), rating, playerId) + 1);
        vo.setTotalPlayers(rankingMapper.countAvatarRankPlayers());

        // 下一级位阶
        if (currentRank != null && currentRank.getNextRankKey() != null) {
            QueryWrapper<AvatarRankConfig> nextQuery = new QueryWrapper<>();
            nextQuery.eq("rank_key", currentRank.getNextRankKey());
            AvatarRankConfig nextRank = rankConfigMapper.selectOne(nextQuery);
            if (nextRank != null) {
                vo.setNextRankName(nextRank.getRankName());
                vo.setNextDisplayName(nextRank.getDisplayName());
                vo.setRewards(parseJsonMap(nextRank.getRewardsJson()));

                // nextRank as object (frontend expects .displayName, .description, .resourceCost, .breakthroughRate, .requirements)
                Map<String, Object> nrMap = new LinkedHashMap<>();
                nrMap.put("key", nextRank.getRankKey());
                nrMap.put("displayName", nextRank.getDisplayName());
                nrMap.put("description", nextRank.getDescription() != null ? nextRank.getDescription() : "");
                nrMap.put("resourceCost", parseJsonMap(nextRank.getUnlocksJson()));
                nrMap.put("breakthroughRate", null);

                // 计算进度
                List<Map<String, Object>> rawRequirements = parseJsonList(nextRank.getRequirementsJson());
                List<AvatarRankVO.RankProgressItem> progressItems = new ArrayList<>();
                boolean allCompleted = true;
                for (Map<String, Object> req : rawRequirements) {
                    String type = (String) req.get("type");
                    String label = (String) req.getOrDefault("label", type);
                    int required = requiredRequirementValue(req);
                    int current = evaluateRequirement(type, req, player, stats, stageProgress);
                    boolean completed = current >= required;
                    if (!completed) allCompleted = false;
                    AvatarRankVO.RankProgressItem item = new AvatarRankVO.RankProgressItem(
                            type, label, Math.min(current, required), required, completed);
                    String reqKey = (String) req.get("locationKey");
                    if (reqKey == null) reqKey = (String) req.get("bossKey");
                    if (reqKey == null) reqKey = (String) req.get("bossId");
                    item.setReqKey(reqKey);
                    progressItems.add(item);
                }
                vo.setRequirements(progressItems);

                // Build nextRank requirements list for display
                List<Map<String, Object>> nrReqs = new ArrayList<>();
                for (AvatarRankVO.RankProgressItem item : progressItems) {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("type", item.getType());
                    r.put("label", item.getLabel());
                    r.put("current", item.getCurrent());
                    r.put("required", item.getRequired());
                    r.put("completed", item.isCompleted());
                    nrReqs.add(r);
                }
                nrMap.put("requirements", nrReqs);
                vo.setNextRank(nrMap);

                vo.setCanRankUp(allCompleted);
                vo.setMaxRank(false);
            } else {
                vo.setCanRankUp(false);
                vo.setMaxRank(true);
            }
        } else {
            vo.setCanRankUp(false);
            vo.setMaxRank(true);
        }

        return vo;
    }

    @Override
    @Transactional
    public AvatarRankVO rankUp(Long playerId, Long userId) {
        Player player = playerMapper.selectByIdForUpdate(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!Objects.equals(player.getUserId(), userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        Map<String, Object> stageProgress = parseJsonMap(player.getStageProgressJson());
        int channelHeatBefore = toInt(stats.get("channelHeat"), 0);
        int channelHeatAfter = channelHeatBefore;

        String currentRankKey = (String) stats.getOrDefault("avatarRank", "F");

        QueryWrapper<AvatarRankConfig> rankQuery = new QueryWrapper<>();
        rankQuery.eq("rank_key", currentRankKey).eq("enabled", 1);
        AvatarRankConfig currentRank = rankConfigMapper.selectOne(rankQuery);
        if (currentRank == null || currentRank.getNextRankKey() == null)
            throw new BusinessException(ErrorCode.MAX_RANK, "已达最高位阶");

        // 获取下一级位阶
        QueryWrapper<AvatarRankConfig> nextQuery = new QueryWrapper<>();
        nextQuery.eq("rank_key", currentRank.getNextRankKey());
        AvatarRankConfig nextRank = rankConfigMapper.selectOne(nextQuery);
        if (nextRank == null)
            throw new BusinessException(ErrorCode.MAX_RANK, "下一级位阶配置不存在");

        // 检查所有条件
        List<Map<String, Object>> rawRequirements = parseJsonList(nextRank.getRequirementsJson());
        for (Map<String, Object> req : rawRequirements) {
            String type = (String) req.get("type");
            int required = requiredRequirementValue(req);
            int current = evaluateRequirement(type, req, player, stats, stageProgress);
            if (current < required) {
                String label = (String) req.getOrDefault("label", type);
                throw new BusinessException(ErrorCode.RANK_REQUIREMENTS_NOT_MET,
                        "条件未满足: " + label + " (" + current + "/" + required + ")");
            }
        }

        // 应用升阶 — 更新 stats
        stats.put("avatarRank", nextRank.getRankKey());
        stats.put("avatarRankName", nextRank.getRankName());
        // 位阶晋升奖励 100 自由属性点
        int currentFreePoints = toInt(stats.get("freePoints"), 0);
        if (currentFreePoints < 0)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "自由属性点数据无效，无法晋升");
        stats.put("freePoints", ResourceBalance.applyDelta("自由属性点", currentFreePoints, 100));

        // 应用奖励
        Map<String, Object> rewards = parseJsonMap(nextRank.getRewardsJson());
        if (rewards.containsKey("storyGrade")) {
            stats.put("storyGrade", rewards.get("storyGrade"));
        }
        if (rewards.containsKey("channelHeat")) {
            int add = playerEffectService.applyChannelHeatGainRate(
                    player.getId(), toInt(rewards.get("channelHeat"), 0));
            int cur = toInt(stats.get("channelHeat"), 0);
            channelHeatAfter = Math.addExact(cur, add);
            stats.put("channelHeat", channelHeatAfter);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> statsRewards = (Map<String, Object>) rewards.get("stats");
        if (statsRewards != null) {
            for (Map.Entry<String, Object> e : statsRewards.entrySet()) {
                int oldVal = toInt(stats.get(e.getKey()), 0);
                int newVal = ResourceBalance.applySignedDelta(
                        "位阶奖励属性「" + e.getKey() + "」", oldVal, toInt(e.getValue(), 0));
                stats.put(e.getKey(), newVal);
            }
        }

        // 保存
        try {
            player.setStatsJson(objectMapper.writeValueAsString(stats));
        } catch (Exception exception) {
            throw new IllegalStateException("位阶晋升状态序列化失败", exception);
        }
        if (playerMapper.updateById(player) <= 0)
            throw new IllegalStateException("保存位阶晋升结果失败");

        recordRankHeatChange(playerId, currentRankKey, nextRank.getRankKey(),
                channelHeatBefore, channelHeatAfter);

        Object configuredLog = rewards.get("log");
        String rankLog = configuredLog instanceof String message && !message.isBlank()
                ? message
                : "位阶晋升至「" + (nextRank.getDisplayName() != null
                        ? nextRank.getDisplayName() : nextRank.getRankName()) + "」。";
        playerLogService.addLog(playerId, "system", rankLog);

        // 重新获取位阶信息 (递归调用，此时已更新到新位阶)
        return getAvatarRankInfo(playerId);
    }

    private void recordRankHeatChange(Long playerId, String rankBefore, String rankAfter,
                                      int channelHeatBefore, int channelHeatAfter) {
        long amount = (long) channelHeatAfter - channelHeatBefore;
        if (amount == 0) return;
        String transactionGroupId = UUID.nameUUIDFromBytes(
                ("avatar-rank:" + playerId + ":" + rankBefore + ":" + rankAfter)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        Map<String, Object> metadata = Map.of("rankBefore", rankBefore, "rankAfter", rankAfter);
        boolean inserted = resourceTransactionService.record(new PlayerResourceTransactionService.Change(
                playerId, "progress", "channel_heat", amount, channelHeatBefore, channelHeatAfter,
                "avatar_rank_up", rankBefore + "->" + rankAfter, transactionGroupId,
                transactionGroupId + ":progress:channel_heat", metadata));
        if (!inserted) throw new IllegalStateException("位阶晋升热度流水已存在，拒绝重复结算");
    }

    @Override
    @Transactional
    public Map<String, Object> prestige(Long playerId, Long userId) {
        Player player = playerMapper.selectByIdForUpdate(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!Objects.equals(player.getUserId(), userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        String currentRank = Objects.toString(stats.get("avatarRank"), "F");
        if (!"SSS".equals(currentRank))
            throw new BusinessException(ErrorCode.RANK_REQUIREMENTS_NOT_MET, "需要达到SSS位阶才能回归");

        int prestigeLevel;
        try {
            prestigeLevel = Integer.parseInt(Objects.toString(stats.get("prestigeLevel"), "0")) + 1;
        } catch (NumberFormatException ignored) {
            prestigeLevel = 1;
        }
        int prestigeBonus = prestigeLevel * 5;
        int experienceBeforeReset = Math.max(0, toInt(stats.get("exp"), 0));
        if (experienceBeforeReset > 0) {
            String transactionGroupId = "prestige:" + playerId + ":" + prestigeLevel;
            Map<String, Object> metadata = Map.of(
                    "rankBefore", currentRank,
                    "rankAfter", "F",
                    "prestigeLevel", prestigeLevel);
            boolean inserted = resourceTransactionService.record(
                    new PlayerResourceTransactionService.Change(
                            playerId, "experience", "exp", -experienceBeforeReset,
                            experienceBeforeReset, 0, "avatar_prestige",
                            String.valueOf(prestigeLevel), transactionGroupId,
                            transactionGroupId + ":experience:exp", metadata));
            if (!inserted) throw new IllegalStateException("回归经验流水已存在，拒绝重复结算");
        }
        stats.put("avatarRank", "F");
        stats.put("avatarRankName", "临时化身");
        stats.put("prestigeLevel", prestigeLevel);
        stats.put("freePoints", 40 + prestigeLevel * 10);
        stats.put("level", 1);
        stats.put("exp", 0);
        stats.put("hp", 100);
        stats.put("maxHp", 100 + prestigeBonus);
        stats.put(PlayerMaxHpProjectionService.APPLIED_CONSTELLATION_BONUS_KEY, 0);
        stats.put("attack", 10 + prestigeBonus);
        stats.put("defense", 5 + prestigeBonus / 2);
        stats.put("speed", 10 + prestigeBonus / 2);
        stats.put("critRate", 0.05 + prestigeLevel * 0.005);

        try {
            player.setStatsJson(objectMapper.writeValueAsString(stats));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "更新状态失败");
        }

        QueryWrapper<PlayerSkill> equippedQuery = new QueryWrapper<>();
        equippedQuery.eq("player_id", playerId).eq("equipped", 1);
        List<PlayerSkill> equippedSkills = playerSkillMapper.selectList(equippedQuery);
        for (PlayerSkill skill : equippedSkills) {
            skill.setEquipped(0);
            if (playerSkillMapper.updateById(skill) <= 0)
                throw new IllegalStateException("保存回归后的技能卸下状态失败");
        }

        if (playerMapper.updateById(player) <= 0)
            throw new IllegalStateException("保存回归后的位阶数据失败");
        String logMessage = "回归至F级。永久加成 +" + prestigeBonus
                + "，清空经验 " + experienceBeforeReset + " 点；已保留已掌握技能并卸下 "
                + equippedSkills.size() + " 项装备技能。";
        playerLogService.addLog(playerId, "system", logMessage);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "回归成功！你现在是 " + prestigeLevel + " 次回归者，获得永久加成 +"
                + prestigeBonus + "，已保留已掌握技能并卸下 " + equippedSkills.size() + " 项装备技能。");
        result.put("prestigeLevel", prestigeLevel);
        result.put("bonus", prestigeBonus);
        result.put("experienceCleared", experienceBeforeReset);
        result.put("unequippedSkillCount", equippedSkills.size());
        return result;
    }

    @Override
    public Map<String, Object> getStarstreamTier(int channelHeat) {
        Map<String, Object> tier = new LinkedHashMap<>();
        if (channelHeat >= 1000) {
            tier.put("key", "tier_5");
            tier.put("label", "终章注视者");
            tier.put("level", 5);
        } else if (channelHeat >= 500) {
            tier.put("key", "tier_4");
            tier.put("label", "星流核心");
            tier.put("level", 4);
        } else if (channelHeat >= 300) {
            tier.put("key", "tier_3");
            tier.put("label", "星流候选者");
            tier.put("level", 3);
        } else if (channelHeat >= 100) {
            tier.put("key", "tier_2");
            tier.put("label", "频道关注者");
            tier.put("level", 2);
        } else if (channelHeat >= 50) {
            tier.put("key", "tier_1");
            tier.put("label", "星流观测者");
            tier.put("level", 1);
        } else {
            tier.put("key", "tier_0");
            tier.put("label", "无名观测者");
            tier.put("level", 0);
        }
        return tier;
    }

    @Override
    public List<Map<String, Object>> getAvatarRankLeaderboard(int limit) {
        if (limit < 1 || limit > 200) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "排行榜 limit 必须在 1 到 200 之间");
        }
        return rankingMapper.selectAvatarRankLeaderboard(limit);
    }

    // ─── 条件评估 ───

    @SuppressWarnings("unchecked")
    private int evaluateRequirement(String type, Map<String, Object> req,
                                     Player player, Map<String, Object> stats,
                                     Map<String, Object> stageProgress) {
        switch (type) {
            case "levelMin":
                return toInt(stats.get("level"), 1);
            case "storyFragmentsMin":
                return player.getStoryFragments() != null ? player.getStoryFragments() : 0;
            case "explorationsByLocation": {
                String locKey = (String) req.get("locationKey");
                Map<String, Object> byLoc = (Map<String, Object>)
                        stageProgress.getOrDefault("explorationsByLocation", Collections.emptyMap());
                return toInt(byLoc.get(locKey), 0);
            }
            case "storyEventsTriggeredMin": {
                List<String> triggered = (List<String>)
                        stageProgress.getOrDefault("storyEventsTriggered", Collections.emptyList());
                return triggered.size();
            }
            case "bossClue": {
                String bossKey = (String) req.get("bossKey");
                Map<String, Object> bossClues = (Map<String, Object>)
                        stageProgress.getOrDefault("bossClues", Collections.emptyMap());
                return toInt(bossClues.get(bossKey), 0);
            }
            case "titlesCountMin": {
                QueryWrapper<PlayerTitle> countQuery = new QueryWrapper<>();
                countQuery.eq("player_id", player.getId());
                return playerTitleMapper.selectCount(countQuery).intValue();
            }
            case "channelHeatMin":
                return toInt(stats.get("channelHeat"), 0);
            case "pkRatingMin":
                return toInt(stats.get("rating"), 1000);
            case "rareTitleRequired": {
                QueryWrapper<PlayerTitle> ptQuery = new QueryWrapper<>();
                ptQuery.eq("player_id", player.getId());
                List<PlayerTitle> allPt = playerTitleMapper.selectList(ptQuery);
                for (PlayerTitle pt : allPt) {
                    QueryWrapper<com.huazhenghai.readergame.entity.Title> tQuery =
                            new QueryWrapper<>();
                    tQuery.eq("title_key", pt.getTitleKey());
                    com.huazhenghai.readergame.entity.Title def = titleMapper.selectOne(tQuery);
                    if (def != null && isRareOrAbove(def.getRarity())) return 1;
                }
                return 0;
            }
            case "epicTitleRequired": {
                QueryWrapper<PlayerTitle> ptQuery = new QueryWrapper<>();
                ptQuery.eq("player_id", player.getId());
                List<PlayerTitle> allPt = playerTitleMapper.selectList(ptQuery);
                for (PlayerTitle pt : allPt) {
                    QueryWrapper<com.huazhenghai.readergame.entity.Title> tQuery =
                            new QueryWrapper<>();
                    tQuery.eq("title_key", pt.getTitleKey());
                    com.huazhenghai.readergame.entity.Title def = titleMapper.selectOne(tQuery);
                    if (def != null && isEpicOrAbove(def.getRarity())) return 1;
                }
                return 0;
            }
            case "legendaryTitleRequired": {
                QueryWrapper<PlayerTitle> ptQuery = new QueryWrapper<>();
                ptQuery.eq("player_id", player.getId());
                List<PlayerTitle> allPt = playerTitleMapper.selectList(ptQuery);
                for (PlayerTitle pt : allPt) {
                    QueryWrapper<com.huazhenghai.readergame.entity.Title> tQuery =
                            new QueryWrapper<>();
                    tQuery.eq("title_key", pt.getTitleKey());
                    com.huazhenghai.readergame.entity.Title def = titleMapper.selectOne(tQuery);
                    if (def != null && isLegendary(def.getRarity())) return 1;
                }
                return 0;
            }
            case "worldLineShiftMin":
                return toInt(stats.get("worldLineShift"), 0);
            default:
                return 0;
        }
    }

    private boolean isRareOrAbove(String rarity) {
        if (rarity == null) return false;
        return switch (rarity) {
            case "rare", "epic", "legendary" -> true;
            default -> false;
        };
    }

    private boolean isEpicOrAbove(String rarity) {
        if (rarity == null) return false;
        return switch (rarity) {
            case "epic", "legendary" -> true;
            default -> false;
        };
    }

    private boolean isLegendary(String rarity) {
        if (rarity == null) return false;
        return "legendary".equals(rarity);
    }

    private String storyGradeLabel(String grade) {
        if (grade == null) return "普通";
        return switch (grade) {
            case "notable" -> "值得关注";
            case "heroic" -> "英雄";
            case "legendary" -> "传奇";
            case "mythic" -> "神话";
            default -> "普通";
        };
    }

    // ─── 工具方法 ───

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseJsonList(String json) {
        if (json == null || json.isBlank() || "null".equals(json))
            return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
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

    private int requiredRequirementValue(Map<String, Object> requirement) {
        Object value = requirement.containsKey("value")
                ? requirement.get("value") : requirement.get("count");
        if (value instanceof Boolean enabled) return enabled ? 1 : 0;
        return toInt(value, 0);
    }
}
