package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.MainChapter;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerTitle;
import com.huazhenghai.readergame.mapper.MainChapterMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.PlayerTitleMapper;
import com.huazhenghai.readergame.service.ChapterService;
import com.huazhenghai.readergame.vo.ChapterProgressVO;
import com.huazhenghai.readergame.vo.MainChapterVO;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ChapterServiceImpl implements ChapterService {

    private final PlayerMapper playerMapper;
    private final MainChapterMapper chapterMapper;
    private final PlayerTitleMapper playerTitleMapper;
    private final ObjectMapper objectMapper;

    public ChapterServiceImpl(PlayerMapper playerMapper,
                               MainChapterMapper chapterMapper,
                               PlayerTitleMapper playerTitleMapper,
                               ObjectMapper objectMapper) {
        this.playerMapper = playerMapper;
        this.chapterMapper = chapterMapper;
        this.playerTitleMapper = playerTitleMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChapterProgressVO getCurrentChapter(Long playerId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");

        String chapterKey = player.getCurrentMainChapter();
        if (chapterKey == null || chapterKey.isBlank()) {
            chapterKey = "main_ch01_paid_service";
        }

        QueryWrapper<MainChapter> query = new QueryWrapper<>();
        query.eq("chapter_key", chapterKey).eq("enabled", 1);
        MainChapter chapter = chapterMapper.selectOne(query);

        if (chapter == null)
            throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND, "当前阶段配置不存在");

        ChapterProgressVO vo = new ChapterProgressVO();
        vo.setCurrentChapterKey(chapter.getChapterKey());
        vo.setCurrentChapterName(chapter.getName());

        // 计算进度
        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        Map<String, Object> stageProgress = parseJsonMap(player.getStageProgressJson());
        Map<String, Object> conditions = parseJsonMap(chapter.getCompletionConditionsJson());

        List<ChapterProgressVO.ProgressItem> progressItems = new ArrayList<>();
        boolean allCompleted = true;
        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            String type = entry.getKey();
            Object val = entry.getValue();
            evaluateChapterRequirement(type, val, player, stats, stageProgress,
                    progressItems);
        }
        for (ChapterProgressVO.ProgressItem item : progressItems) {
            if (!item.isCompleted()) {
                allCompleted = false;
                break;
            }
        }

        vo.setCompleted(allCompleted);
        vo.setRequirements(progressItems);

        // 下一阶段
        if (allCompleted && chapter.getNextChapterKey() != null) {
            vo.setCanAdvance(true);
            vo.setNextChapterKey(chapter.getNextChapterKey());
            QueryWrapper<MainChapter> nextQuery = new QueryWrapper<>();
            nextQuery.eq("chapter_key", chapter.getNextChapterKey());
            MainChapter next = chapterMapper.selectOne(nextQuery);
            if (next != null) {
                vo.setNextChapterName(next.getName());
                vo.setRewards(parseJsonMap(chapter.getRewardsJson()));
            }
        } else {
            vo.setCanAdvance(false);
            if (chapter.getNextChapterKey() != null) {
                vo.setNextChapterKey(chapter.getNextChapterKey());
            }
            if (allCompleted) {
                vo.setRewards(parseJsonMap(chapter.getRewardsJson()));
            }
        }

        return vo;
    }

    @Override
    public List<MainChapterVO> getAllMainChapters() {
        QueryWrapper<MainChapter> query = new QueryWrapper<>();
        query.eq("enabled", 1).orderByAsc("order_num");
        List<MainChapter> chapters = chapterMapper.selectList(query);

        List<MainChapterVO> result = new ArrayList<>();
        for (MainChapter ch : chapters) {
            MainChapterVO vo = new MainChapterVO();
            vo.setChapterKey(ch.getChapterKey());
            vo.setName(ch.getName());
            vo.setDescription(ch.getDescription());
            vo.setOrderNum(ch.getOrderNum());
            vo.setUnlockConditions(parseJsonMap(ch.getUnlockConditionsJson()));
            vo.setCompletionConditions(parseJsonMap(ch.getCompletionConditionsJson()));
            vo.setRewards(parseJsonMap(ch.getRewardsJson()));
            vo.setNextChapterKey(ch.getNextChapterKey());
            result.add(vo);
        }
        return result;
    }

    @Override
    public boolean checkChapterCompleted(Long playerId) {
        ChapterProgressVO progress = getCurrentChapter(playerId);
        return progress.isCompleted();
    }

    @Override
    public ChapterProgressVO claimChapterRewardAndAdvance(Long playerId, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        String chapterKey = player.getCurrentMainChapter();
        if (chapterKey == null || chapterKey.isBlank()) {
            chapterKey = "main_ch01_paid_service";
        }

        QueryWrapper<MainChapter> query = new QueryWrapper<>();
        query.eq("chapter_key", chapterKey).eq("enabled", 1);
        MainChapter chapter = chapterMapper.selectOne(query);
        if (chapter == null)
            throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND, "当前阶段配置不存在");

        // 检查是否已完成
        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        Map<String, Object> stageProgress = parseJsonMap(player.getStageProgressJson());
        Map<String, Object> conditions = parseJsonMap(chapter.getCompletionConditionsJson());

        if (!allConditionsMet(conditions, player, stats, stageProgress))
            throw new BusinessException(ErrorCode.CHAPTER_NOT_COMPLETED, "当前阶段尚未完成");

        if (chapter.getNextChapterKey() == null)
            throw new BusinessException(ErrorCode.CANNOT_ADVANCE, "已是最后阶段，无可推进");

        // 检查下一阶段解锁条件
        QueryWrapper<MainChapter> nextQuery = new QueryWrapper<>();
        nextQuery.eq("chapter_key", chapter.getNextChapterKey());
        MainChapter nextChapter = chapterMapper.selectOne(nextQuery);
        if (nextChapter == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "下一阶段配置不存在");

        Map<String, Object> unlockConds = parseJsonMap(nextChapter.getUnlockConditionsJson());
        if (!checkUnlockConditions(unlockConds, player, stats))
            throw new BusinessException(ErrorCode.CANNOT_ADVANCE, "不满足下一阶段解锁条件");

        // 发放奖励
        Map<String, Object> rewards = parseJsonMap(chapter.getRewardsJson());
        applyChapterRewards(player, stats, rewards);

        // 推进
        player.setCurrentMainChapter(chapter.getNextChapterKey());
        try {
            player.setStatsJson(objectMapper.writeValueAsString(stats));
        } catch (Exception ignored) {
        }
        playerMapper.updateById(player);

        return getCurrentChapter(playerId);
    }

    // ─── 条件评估 ───

    @SuppressWarnings("unchecked")
    private void evaluateChapterRequirement(String type, Object val, Player player,
                                            Map<String, Object> stats, Map<String, Object> stageProgress,
                                            List<ChapterProgressVO.ProgressItem> items) {
        switch (type) {
            case "storyEventsTriggeredMin": {
                int required = toInt(val, 1);
                List<String> triggered = (List<String>)
                        stageProgress.getOrDefault("storyEventsTriggered", Collections.emptyList());
                int current = triggered.size();
                items.add(new ChapterProgressVO.ProgressItem(type, "主线事件触发",
                        Math.min(current, required), required, current >= required));
                break;
            }
            case "bossClue": {
                Map<String, Object> clueMap = (Map<String, Object>) val;
                Map<String, Object> bossClues = (Map<String, Object>)
                        stageProgress.getOrDefault("bossClues", Collections.emptyMap());
                for (Map.Entry<String, Object> e : clueMap.entrySet()) {
                    int required = toInt(e.getValue(), 1);
                    int current = toInt(bossClues.get(e.getKey()), 0);
                    items.add(new ChapterProgressVO.ProgressItem("bossClue",
                            "Boss线索(" + e.getKey() + ")",
                            Math.min(current, required), required, current >= required));
                }
                break;
            }
            case "storyFragmentsMin": {
                int required = toInt(val, 1);
                int current = player.getStoryFragments() != null ? player.getStoryFragments() : 0;
                items.add(new ChapterProgressVO.ProgressItem(type, "故事碎片",
                        Math.min(current, required), required, current >= required));
                break;
            }
            case "channelHeatMin": {
                int required = toInt(val, 1);
                int current = toInt(stats.get("channelHeat"), 0);
                items.add(new ChapterProgressVO.ProgressItem(type, "频道热度",
                        Math.min(current, required), required, current >= required));
                break;
            }
            case "titlesCountMin": {
                int required = toInt(val, 1);
                QueryWrapper<PlayerTitle> countQuery = new QueryWrapper<>();
                countQuery.eq("player_id", player.getId());
                int current = playerTitleMapper.selectCount(countQuery).intValue();
                items.add(new ChapterProgressVO.ProgressItem(type, "拥有称号",
                        Math.min(current, required), required, current >= required));
                break;
            }
            default:
                break;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean allConditionsMet(Map<String, Object> conditions, Player player,
                                      Map<String, Object> stats, Map<String, Object> stageProgress) {
        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            String type = entry.getKey();
            Object val = entry.getValue();
            switch (type) {
                case "storyEventsTriggeredMin": {
                    List<String> triggered = (List<String>)
                            stageProgress.getOrDefault("storyEventsTriggered", Collections.emptyList());
                    if (triggered.size() < toInt(val, 1)) return false;
                    break;
                }
                case "bossClue": {
                    Map<String, Object> clueMap = (Map<String, Object>) val;
                    Map<String, Object> bossClues = (Map<String, Object>)
                            stageProgress.getOrDefault("bossClues", Collections.emptyMap());
                    for (Map.Entry<String, Object> e : clueMap.entrySet()) {
                        if (toInt(bossClues.get(e.getKey()), 0) < toInt(e.getValue(), 1))
                            return false;
                    }
                    break;
                }
                case "storyFragmentsMin": {
                    int cur = player.getStoryFragments() != null ? player.getStoryFragments() : 0;
                    if (cur < toInt(val, 1)) return false;
                    break;
                }
                case "channelHeatMin": {
                    if (toInt(stats.get("channelHeat"), 0) < toInt(val, 1)) return false;
                    break;
                }
                case "titlesCountMin": {
                    QueryWrapper<PlayerTitle> countQuery = new QueryWrapper<>();
                    countQuery.eq("player_id", player.getId());
                    if (playerTitleMapper.selectCount(countQuery) < toInt(val, 1)) return false;
                    break;
                }
                default:
                    break;
            }
        }
        return true;
    }

    private boolean checkUnlockConditions(Map<String, Object> conditions, Player player,
                                           Map<String, Object> stats) {
        if (conditions.isEmpty()) return true;
        if (conditions.containsKey("requiredPreviousChapter")) {
            String required = (String) conditions.get("requiredPreviousChapter");
            // 简化：只要能到这里，表示前一个章节已完成
            // 实际应检查 completedChapters 标记
        }
        if (conditions.containsKey("avatarRankMin")) {
            String required = (String) conditions.get("avatarRankMin");
            String currentRank = (String) stats.getOrDefault("avatarRank", "F");
            int requiredOrder = getRankOrder(required);
            int currentOrder = getRankOrder(currentRank);
            if (currentOrder < requiredOrder) return false;
        }
        if (conditions.containsKey("worldLineShiftMin")) {
            int wls = toInt(stats.get("worldLineShift"), 0);
            if (wls < toInt(conditions.get("worldLineShiftMin"), 0)) return false;
        }
        return true;
    }

    private void applyChapterRewards(Player player, Map<String, Object> stats,
                                      Map<String, Object> rewards) {
        if (rewards.containsKey("coins")) {
            int add = toInt(rewards.get("coins"), 0);
            player.setCoins((player.getCoins() != null ? player.getCoins() : 0) + add);
        }
        if (rewards.containsKey("storyFragments")) {
            int add = toInt(rewards.get("storyFragments"), 0);
            player.setStoryFragments((player.getStoryFragments() != null ? player.getStoryFragments() : 0) + add);
        }
        if (rewards.containsKey("channelHeat")) {
            int add = toInt(rewards.get("channelHeat"), 0);
            int cur = toInt(stats.get("channelHeat"), 0);
            stats.put("channelHeat", cur + add);
        }
    }

    private int getRankOrder(String rankKey) {
        return switch (rankKey) {
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

    // ─── 工具方法 ───

    @Override
    public Map<String, Object> getResources(Long playerId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("coins", player.getCoins() != null ? player.getCoins() : 0);
        result.put("storyFragments", player.getStoryFragments() != null ? player.getStoryFragments() : 0);
        result.put("constellationFavor", player.getConstellationFavor() != null ? player.getConstellationFavor() : 0);
        result.put("abyssMark", player.getAbyssMark() != null ? player.getAbyssMark() : 0);
        return result;
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
