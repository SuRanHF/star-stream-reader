package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.AvatarRankConfig;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerTitle;
import com.huazhenghai.readergame.mapper.AvatarRankConfigMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.PlayerTitleMapper;
import com.huazhenghai.readergame.mapper.TitleMapper;
import com.huazhenghai.readergame.service.AvatarRankService;
import com.huazhenghai.readergame.vo.AvatarRankVO;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AvatarRankServiceImpl implements AvatarRankService {

    private final PlayerMapper playerMapper;
    private final PlayerTitleMapper playerTitleMapper;
    private final AvatarRankConfigMapper rankConfigMapper;
    private final TitleMapper titleMapper;
    private final ObjectMapper objectMapper;

    public AvatarRankServiceImpl(PlayerMapper playerMapper,
                                  PlayerTitleMapper playerTitleMapper,
                                  AvatarRankConfigMapper rankConfigMapper,
                                  TitleMapper titleMapper,
                                  ObjectMapper objectMapper) {
        this.playerMapper = playerMapper;
        this.playerTitleMapper = playerTitleMapper;
        this.rankConfigMapper = rankConfigMapper;
        this.titleMapper = titleMapper;
        this.objectMapper = objectMapper;
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
        vo.setCurrentRank(currentRankKey);
        vo.setCurrentRankName(currentRank != null ? currentRank.getRankName() : currentRankKey);
        vo.setCurrentDisplayName(currentRank != null ? currentRank.getDisplayName() : currentRankKey);
        vo.setStoryGrade(storyGrade);
        vo.setStoryGradeLabel(storyGradeLabel(storyGrade));
        vo.setStarstreamTier((String) starstreamTier.get("key"));
        vo.setStarstreamTierLabel((String) starstreamTier.get("label"));
        vo.setCurrentOrder(currentRank != null ? currentRank.getOrderNum() : 1);

        // 下一级位阶
        if (currentRank != null && currentRank.getNextRankKey() != null) {
            vo.setNextRank(currentRank.getNextRankKey());
            QueryWrapper<AvatarRankConfig> nextQuery = new QueryWrapper<>();
            nextQuery.eq("rank_key", currentRank.getNextRankKey());
            AvatarRankConfig nextRank = rankConfigMapper.selectOne(nextQuery);
            if (nextRank != null) {
                vo.setNextRankName(nextRank.getRankName());
                vo.setNextDisplayName(nextRank.getDisplayName());
                vo.setRewards(parseJsonMap(nextRank.getRewardsJson()));

                // 计算进度
                List<Map<String, Object>> rawRequirements = parseJsonList(nextRank.getRequirementsJson());
                List<AvatarRankVO.RankProgressItem> progressItems = new ArrayList<>();
                boolean allCompleted = true;
                for (Map<String, Object> req : rawRequirements) {
                    String type = (String) req.get("type");
                    String label = (String) req.getOrDefault("label", type);
                    int required = toInt(req.getOrDefault("value", req.get("count")), 0);
                    int current = evaluateRequirement(type, req, player, stats, stageProgress);
                    boolean completed = current >= required;
                    if (!completed) allCompleted = false;
                    progressItems.add(new AvatarRankVO.RankProgressItem(
                            type, label, Math.min(current, required), required, completed));
                }
                vo.setRequirements(progressItems);
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
    public AvatarRankVO rankUp(Long playerId, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        Map<String, Object> stageProgress = parseJsonMap(player.getStageProgressJson());

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
            int required = toInt(req.getOrDefault("value", req.get("count")), 0);
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

        // 应用奖励
        Map<String, Object> rewards = parseJsonMap(nextRank.getRewardsJson());
        if (rewards.containsKey("storyGrade")) {
            stats.put("storyGrade", rewards.get("storyGrade"));
        }
        if (rewards.containsKey("channelHeat")) {
            int add = toInt(rewards.get("channelHeat"), 0);
            int cur = toInt(stats.get("channelHeat"), 0);
            stats.put("channelHeat", cur + add);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> statsRewards = (Map<String, Object>) rewards.get("stats");
        if (statsRewards != null) {
            for (Map.Entry<String, Object> e : statsRewards.entrySet()) {
                int oldVal = toInt(stats.get(e.getKey()), 0);
                stats.put(e.getKey(), oldVal + toInt(e.getValue(), 0));
            }
        }

        // 保存
        try {
            player.setStatsJson(objectMapper.writeValueAsString(stats));
        } catch (Exception ignored) {
        }
        playerMapper.updateById(player);

        // 重新获取位阶信息 (递归调用，此时已更新到新位阶)
        return getAvatarRankInfo(playerId);
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
    public List<Map<String, Object>> getAvatarRankLeaderboard() {
        List<Map<String, Object>> result = new ArrayList<>();

        QueryWrapper<AvatarRankConfig> query = new QueryWrapper<>();
        query.eq("enabled", 1).orderByAsc("order_num");
        List<AvatarRankConfig> ranks = rankConfigMapper.selectList(query);
        // 按 orderNum 降序 (高级位阶在前)
        java.util.Collections.reverse(ranks);

        // 简单版排行: 按位阶 + level + PK评分 (LIMIT 200, 防全表)
        List<Player> allPlayers = playerMapper.selectList(
                new QueryWrapper<Player>().last("LIMIT 200"));
        for (Player p : allPlayers) {
            Map<String, Object> stats = parseJsonMap(p.getStatsJson());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("playerId", p.getId());
            entry.put("playerName", p.getPlayerName());
            entry.put("avatarRank", stats.getOrDefault("avatarRank", "F"));
            entry.put("level", toInt(stats.get("level"), 1));
            entry.put("rating", toInt(stats.get("rating"), 1000));
            result.add(entry);
        }

        // 按位阶排序
        Map<String, Integer> rankOrder = new LinkedHashMap<>();
        for (AvatarRankConfig r : rankConfigMapper.selectList(new QueryWrapper<>())) {
            rankOrder.put(r.getRankKey(), r.getOrderNum());
        }
        result.sort((a, b) -> {
            int ro = Integer.compare(
                    rankOrder.getOrDefault(b.get("avatarRank"), 0),
                    rankOrder.getOrDefault(a.get("avatarRank"), 0));
            if (ro != 0) return ro;
            return Integer.compare(
                    toInt(b.get("level"), 1), toInt(a.get("level"), 1));
        });

        return result;
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
}
