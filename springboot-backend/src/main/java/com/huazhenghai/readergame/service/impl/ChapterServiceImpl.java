package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.ResourceBalance;
import com.huazhenghai.readergame.entity.MainChapter;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerTitle;
import com.huazhenghai.readergame.entity.Title;
import com.huazhenghai.readergame.mapper.MainChapterMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.PlayerTitleMapper;
import com.huazhenghai.readergame.mapper.TitleMapper;
import com.huazhenghai.readergame.service.ChapterService;
import com.huazhenghai.readergame.service.PlayerEffectService;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import com.huazhenghai.readergame.vo.ChapterProgressVO;
import com.huazhenghai.readergame.vo.MainChapterVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ChapterServiceImpl implements ChapterService {

    private final PlayerMapper playerMapper;
    private final MainChapterMapper chapterMapper;
    private final PlayerTitleMapper playerTitleMapper;
    private final TitleMapper titleMapper;
    private final PlayerEffectService playerEffectService;
    private final PlayerLogService playerLogService;
    private final PlayerResourceTransactionService resourceTransactionService;
    private final ObjectMapper objectMapper;

    public ChapterServiceImpl(PlayerMapper playerMapper,
                               MainChapterMapper chapterMapper,
                               PlayerTitleMapper playerTitleMapper,
                               TitleMapper titleMapper,
                               ObjectMapper objectMapper,
                               PlayerEffectService playerEffectService,
                               PlayerLogService playerLogService,
                               PlayerResourceTransactionService resourceTransactionService) {
        this.playerMapper = playerMapper;
        this.chapterMapper = chapterMapper;
        this.playerTitleMapper = playerTitleMapper;
        this.titleMapper = titleMapper;
        this.objectMapper = objectMapper;
        this.playerEffectService = playerEffectService;
        this.playerLogService = playerLogService;
        this.resourceTransactionService = resourceTransactionService;
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
            vo.setNextChapterKey(chapter.getNextChapterKey());
            QueryWrapper<MainChapter> nextQuery = new QueryWrapper<>();
            nextQuery.eq("chapter_key", chapter.getNextChapterKey());
            MainChapter next = chapterMapper.selectOne(nextQuery);
            if (next != null) {
                vo.setNextChapterName(next.getName());
                vo.setRewards(parseJsonMap(chapter.getRewardsJson()));
                vo.setCanAdvance(checkUnlockConditions(
                        parseJsonMap(next.getUnlockConditionsJson()), player, stats));
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
    @Transactional
    public ChapterProgressVO claimChapterRewardAndAdvance(Long playerId, String expectedChapterKey, Long userId) {
        Player player = playerMapper.selectByIdForUpdate(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!Objects.equals(player.getUserId(), userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        String chapterKey = player.getCurrentMainChapter();
        if (chapterKey == null || chapterKey.isBlank()) {
            chapterKey = "main_ch01_paid_service";
        }
        if (!Objects.equals(expectedChapterKey, chapterKey)) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "主线阶段已变化，请刷新后重试");
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
        int coinsBefore = player.getCoins() == null ? 0 : player.getCoins();
        int fragmentsBefore = player.getStoryFragments() == null ? 0 : player.getStoryFragments();
        int channelHeatBefore = toInt(stats.get("channelHeat"), 0);
        Map<String, Object> appliedRewards = applyChapterRewards(player, stats, rewards);

        // 推进
        player.setCurrentMainChapter(chapter.getNextChapterKey());
        try {
            player.setStatsJson(objectMapper.writeValueAsString(stats));
        } catch (Exception exception) {
            throw new IllegalStateException("主线阶段奖励状态序列化失败", exception);
        }
        if (playerMapper.updateById(player) <= 0)
            throw new IllegalStateException("主线阶段奖励与进度保存失败");

        String transactionGroupId = UUID.nameUUIDFromBytes(
                ("chapter-reward:" + playerId + ":" + chapterKey)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        Map<String, Object> resourceMetadata = Map.of(
                "chapterKey", chapterKey,
                "chapterName", Objects.toString(chapter.getName(), ""));
        recordChapterResourceChange(playerId, "currency", "coins", coinsBefore,
                player.getCoins() == null ? 0 : player.getCoins(), chapterKey,
                transactionGroupId, resourceMetadata);
        recordChapterResourceChange(playerId, "currency", "story_fragments", fragmentsBefore,
                player.getStoryFragments() == null ? 0 : player.getStoryFragments(), chapterKey,
                transactionGroupId, resourceMetadata);
        recordChapterResourceChange(playerId, "progress", "channel_heat", channelHeatBefore,
                toInt(stats.get("channelHeat"), 0), chapterKey, transactionGroupId, resourceMetadata);

        Map<String, Object> logMetadata = new LinkedHashMap<>();
        logMetadata.put("kind", "chapter_reward");
        logMetadata.put("chapterKey", chapter.getChapterKey());
        logMetadata.put("chapterName", chapter.getName());
        logMetadata.put("nextChapterKey", nextChapter.getChapterKey());
        logMetadata.put("nextChapterName", nextChapter.getName());
        logMetadata.put("rewards", appliedRewards);
        playerLogService.addLog(playerId, "story",
                "主线推进：「" + chapter.getName() + "」→「" + nextChapter.getName() + "」", logMetadata);

        return getCurrentChapter(playerId);
    }

    // ─── 条件评估 ───

    private void evaluateChapterRequirement(String type, Object val, Player player,
                                            Map<String, Object> stats, Map<String, Object> stageProgress,
                                            List<ChapterProgressVO.ProgressItem> items) {
        switch (type) {
            case "storyEventsTriggeredMin": {
                int required = toInt(val, 1);
                int current = distinctStringCount(stageProgress.get("storyEventsTriggered"));
                items.add(new ChapterProgressVO.ProgressItem(type, "主线事件触发",
                        Math.min(current, required), required, current >= required));
                break;
            }
            case "bossClue": {
                Map<String, Object> clueMap = asMap(val);
                Map<String, Object> bossClues = asMap(stageProgress.get("bossClues"));
                if (clueMap.isEmpty()) {
                    items.add(new ChapterProgressVO.ProgressItem(type, "Boss线索配置无效", 0, 1, false));
                    break;
                }
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
            case "locationExploreCount": {
                Map<String, Object> requirements = asMap(val);
                Map<String, Object> explorations = asMap(stageProgress.get("explorationsByLocation"));
                if (requirements.isEmpty()) {
                    items.add(new ChapterProgressVO.ProgressItem(type, "地点探索配置无效", 0, 1, false));
                    break;
                }
                for (Map.Entry<String, Object> requirement : requirements.entrySet()) {
                    int needed = toInt(requirement.getValue(), -1);
                    int current = toInt(explorations.get(requirement.getKey()), 0);
                    boolean completed = needed > 0 && current >= needed;
                    items.add(new ChapterProgressVO.ProgressItem(type,
                            "地点探索（" + requirement.getKey() + "）",
                            Math.min(Math.max(current, 0), Math.max(needed, 1)),
                            Math.max(needed, 1), completed));
                }
                break;
            }
            case "epicTitleRequired":
            case "legendaryTitleRequired": {
                int minimumRarity = "epicTitleRequired".equals(type) ? 4 : 5;
                boolean completed = ownsTitleAtRarity(player.getId(), minimumRarity);
                String label = minimumRarity == 4 ? "史诗及以上称号" : "传说及以上称号";
                items.add(new ChapterProgressVO.ProgressItem(type, label,
                        completed ? 1 : 0, 1, completed));
                break;
            }
            case "hiddenEventsTriggeredMin": {
                int required = toInt(val, 1);
                int current = distinctStringCount(stageProgress.get("hiddenEventsTriggered"));
                items.add(new ChapterProgressVO.ProgressItem(type, "隐藏事件",
                        Math.min(current, required), required, current >= required));
                break;
            }
            case "avatarRankMin": {
                int required = getRankOrder(Objects.toString(val, ""));
                int current = getRankOrder(Objects.toString(stats.get("avatarRank"), "F"));
                boolean completed = required > 0 && current >= required;
                items.add(new ChapterProgressVO.ProgressItem(type, "化身位阶（" + val + "）",
                        completed ? 1 : 0, 1, completed));
                break;
            }
            case "worldLineShiftMin": {
                int required = toInt(val, -1);
                int current = toInt(stats.get("worldLineShift"), 0);
                boolean completed = required >= 0 && current >= required;
                items.add(new ChapterProgressVO.ProgressItem(type, "世界线偏移",
                        Math.min(Math.max(current, 0), Math.max(required, 0)),
                        Math.max(required, 0), completed));
                break;
            }
            default:
                items.add(new ChapterProgressVO.ProgressItem(type,
                        "未支持的章节条件（需管理员修正配置）", 0, 1, false));
                break;
        }
    }

    private boolean allConditionsMet(Map<String, Object> conditions, Player player,
                                      Map<String, Object> stats, Map<String, Object> stageProgress) {
        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            String type = entry.getKey();
            Object val = entry.getValue();
            switch (type) {
                case "storyEventsTriggeredMin": {
                    if (distinctStringCount(stageProgress.get("storyEventsTriggered")) < toInt(val, 1)) return false;
                    break;
                }
                case "bossClue": {
                    Map<String, Object> clueMap = asMap(val);
                    if (clueMap.isEmpty()) return false;
                    Map<String, Object> bossClues = asMap(stageProgress.get("bossClues"));
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
                case "locationExploreCount": {
                    Map<String, Object> requiredLocations = asMap(val);
                    if (requiredLocations.isEmpty()) return false;
                    Map<String, Object> explorations = asMap(stageProgress.get("explorationsByLocation"));
                    for (Map.Entry<String, Object> requirement : requiredLocations.entrySet()) {
                        int needed = toInt(requirement.getValue(), -1);
                        if (needed <= 0 || toInt(explorations.get(requirement.getKey()), 0) < needed) return false;
                    }
                    break;
                }
                case "epicTitleRequired":
                    if (!Boolean.TRUE.equals(val) || !ownsTitleAtRarity(player.getId(), 4)) return false;
                    break;
                case "legendaryTitleRequired":
                    if (!Boolean.TRUE.equals(val) || !ownsTitleAtRarity(player.getId(), 5)) return false;
                    break;
                case "hiddenEventsTriggeredMin":
                    if (distinctStringCount(stageProgress.get("hiddenEventsTriggered")) < toInt(val, 1)) return false;
                    break;
                case "avatarRankMin": {
                    int required = getRankOrder(Objects.toString(val, ""));
                    int current = getRankOrder(Objects.toString(stats.get("avatarRank"), "F"));
                    if (required <= 0 || current < required) return false;
                    break;
                }
                case "worldLineShiftMin": {
                    int required = toInt(val, -1);
                    if (required < 0 || toInt(stats.get("worldLineShift"), 0) < required) return false;
                    break;
                }
                default:
                    return false;
            }
        }
        return true;
    }

    private boolean checkUnlockConditions(Map<String, Object> conditions, Player player,
                                           Map<String, Object> stats) {
        if (conditions.isEmpty()) return true;
        Set<String> supportedConditions = Set.of("requiredPreviousChapter", "avatarRankMin", "worldLineShiftMin");
        if (!supportedConditions.containsAll(conditions.keySet())) return false;
        if (conditions.containsKey("requiredPreviousChapter")) {
            String required = Objects.toString(conditions.get("requiredPreviousChapter"), "");
            if (required.isBlank() || !Objects.equals(required, player.getCurrentMainChapter())) return false;
        }
        if (conditions.containsKey("avatarRankMin")) {
            String required = Objects.toString(conditions.get("avatarRankMin"), "");
            String currentRank = Objects.toString(stats.get("avatarRank"), "F");
            int requiredOrder = getRankOrder(required);
            int currentOrder = getRankOrder(currentRank);
            if (requiredOrder <= 0 || currentOrder <= 0 || currentOrder < requiredOrder) return false;
        }
        if (conditions.containsKey("worldLineShiftMin")) {
            int wls = toInt(stats.get("worldLineShift"), 0);
            int required = toInt(conditions.get("worldLineShiftMin"), -1);
            if (required < 0 || wls < required) return false;
        }
        return true;
    }

    private Map<String, Object> applyChapterRewards(Player player, Map<String, Object> stats,
                                                    Map<String, Object> rewards) {
        Map<String, Object> applied = new LinkedHashMap<>();
        if (rewards.containsKey("coins")) {
            int add = toInt(rewards.get("coins"), 0);
            player.setCoins(ResourceBalance.applyDelta("星币", player.getCoins(), add));
            if (add != 0) applied.put("coins", add);
        }
        if (rewards.containsKey("storyFragments")) {
            int add = toInt(rewards.get("storyFragments"), 0);
            player.setStoryFragments(ResourceBalance.applyDelta("故事碎片", player.getStoryFragments(), add));
            if (add != 0) applied.put("storyFragments", add);
        }
        if (rewards.containsKey("channelHeat")) {
            int add = playerEffectService.applyChannelHeatGainRate(
                    player.getId(), toInt(rewards.get("channelHeat"), 0));
            int cur = toInt(stats.get("channelHeat"), 0);
            stats.put("channelHeat", Math.addExact(cur, add));
            if (add != 0) applied.put("channelHeat", add);
        }
        return applied;
    }

    private void recordChapterResourceChange(Long playerId, String resourceType, String resourceKey,
                                             long before, long after, String chapterKey,
                                             String transactionGroupId, Map<String, Object> metadata) {
        long amount = after - before;
        if (amount == 0) return;
        String idempotencyKey = transactionGroupId + ":" + resourceType + ":" + resourceKey;
        boolean inserted = resourceTransactionService.record(new PlayerResourceTransactionService.Change(
                playerId, resourceType, resourceKey, amount, before, after,
                "chapter_reward", chapterKey, transactionGroupId, idempotencyKey, metadata));
        if (!inserted) throw new IllegalStateException("主线奖励流水已存在，拒绝重复结算: " + chapterKey);
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
            default -> 0;
        };
    }

    private boolean ownsTitleAtRarity(Long playerId, int minimumRarity) {
        QueryWrapper<PlayerTitle> ownedQuery = new QueryWrapper<>();
        ownedQuery.eq("player_id", playerId);
        List<PlayerTitle> ownedTitles = playerTitleMapper.selectList(ownedQuery);
        if (ownedTitles == null || ownedTitles.isEmpty()) return false;

        List<String> titleKeys = ownedTitles.stream()
                .map(PlayerTitle::getTitleKey)
                .filter(key -> key != null && !key.isBlank())
                .distinct()
                .toList();
        if (titleKeys.isEmpty()) return false;

        QueryWrapper<Title> definitionsQuery = new QueryWrapper<>();
        definitionsQuery.eq("enabled", 1).in("title_key", titleKeys);
        List<Title> definitions = titleMapper.selectList(definitionsQuery);
        return definitions != null && definitions.stream()
                .anyMatch(title -> titleRarityOrder(title.getRarity()) >= minimumRarity);
    }

    private int titleRarityOrder(String rarity) {
        if (rarity == null) return 0;
        return switch (rarity.toLowerCase(Locale.ROOT)) {
            case "common" -> 1;
            case "uncommon" -> 2;
            case "rare" -> 3;
            case "epic" -> 4;
            case "legendary" -> 5;
            case "mythic" -> 6;
            default -> 0;
        };
    }

    private Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Collections.emptyMap();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, entryValue) -> {
            if (key instanceof String stringKey) result.put(stringKey, entryValue);
        });
        return result;
    }

    private int distinctStringCount(Object value) {
        if (!(value instanceof Collection<?> values)) return 0;
        Set<String> uniqueValues = new HashSet<>();
        for (Object entry : values) {
            if (entry instanceof String stringValue && !stringValue.isBlank()) uniqueValues.add(stringValue);
        }
        return uniqueValues.size();
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
