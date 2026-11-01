package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerTitle;
import com.huazhenghai.readergame.entity.Title;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.PlayerTitleMapper;
import com.huazhenghai.readergame.mapper.TitleMapper;
import com.huazhenghai.readergame.service.TitleService;
import com.huazhenghai.readergame.service.TitleSacrificeHistory;
import com.huazhenghai.readergame.service.PlayerMaxHpProjectionService;
import com.huazhenghai.readergame.vo.PlayerTitleVO;
import com.huazhenghai.readergame.vo.TitleVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class TitleServiceImpl implements TitleService {

    private final TitleMapper titleMapper;
    private final PlayerTitleMapper playerTitleMapper;
    private final PlayerMapper playerMapper;
    private final ObjectMapper objectMapper;
    private final PlayerMaxHpProjectionService maxHpProjectionService;

    public TitleServiceImpl(TitleMapper titleMapper,
                            PlayerTitleMapper playerTitleMapper,
                            PlayerMapper playerMapper,
                            ObjectMapper objectMapper,
                            PlayerMaxHpProjectionService maxHpProjectionService) {
        this.titleMapper = titleMapper;
        this.playerTitleMapper = playerTitleMapper;
        this.playerMapper = playerMapper;
        this.objectMapper = objectMapper;
        this.maxHpProjectionService = maxHpProjectionService;
    }

    @Override
    public List<TitleVO> getAllTitles() {
        QueryWrapper<Title> query = new QueryWrapper<>();
        query.eq("enabled", 1).orderByAsc("category", "rarity");
        List<Title> titles = titleMapper.selectList(query);
        List<TitleVO> result = new ArrayList<>();
        for (Title t : titles) {
            TitleVO vo = new TitleVO();
            vo.setTitleKey(t.getTitleKey());
            vo.setName(t.getName());
            vo.setCategory(t.getCategory());
            vo.setRarity(t.getRarity());
            vo.setDescription(t.getDescription());
            TitleEffectAudit.populate(vo, parseJsonMap(t.getEffectsJson()));
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional
    public List<PlayerTitleVO> getPlayerTitles(Long playerId) {
        Player player = playerMapper.selectByIdForUpdate(playerId);
        Map<String, Object> stageProgress = player == null ? Map.of() : parseJsonMap(player.getStageProgressJson());
        int totalExplorations = countTotalExplorations(stageProgress);
        if (player != null && TitleSacrificeHistory.prepare(player, totalExplorations,
                System.currentTimeMillis(), objectMapper) && playerMapper.updateById(player) <= 0)
            throw new IllegalStateException("保存旧称号献祭记录失败");
        QueryWrapper<PlayerTitle> query = new QueryWrapper<>();
        query.eq("player_id", playerId).orderByDesc("unlocked_at");
        List<PlayerTitle> pts = playerTitleMapper.selectList(query);

        QueryWrapper<Title> tQuery = new QueryWrapper<>();
        tQuery.eq("enabled", 1);
        List<Title> allTitles = titleMapper.selectList(tQuery);
        Map<String, Title> titleMap = new LinkedHashMap<>();
        for (Title t : allTitles) {
            titleMap.put(t.getTitleKey(), t);
        }

        List<PlayerTitleVO> result = new ArrayList<>();
        for (PlayerTitle pt : pts) {
            Title def = titleMap.get(pt.getTitleKey());
            if (def == null) continue;
            PlayerTitleVO vo = new PlayerTitleVO();
            vo.setTitleKey(pt.getTitleKey());
            vo.setName(def.getName());
            vo.setCategory(def.getCategory());
            vo.setRarity(def.getRarity());
            vo.setDescription(def.getDescription());
            vo.setEquipped(pt.getEquipped() != null && pt.getEquipped() == 1);
            vo.setSource(pt.getSource());
            vo.setUnlockedAt(pt.getUnlockedAt());
            TitleEffectAudit.populate(vo, parseJsonMap(def.getEffectsJson()));
            if (player != null && TitleSacrificeHistory.isReacquired(player, pt.getTitleKey(), objectMapper)) {
                vo.setReacquired(true);
                vo.setCanSacrifice(false);
            }
            result.add(vo);
        }
        if (player != null) {
            Set<String> owned = getOwnedTitleKeys(playerId);
            for (Title def : allTitles) {
                if (owned.contains(def.getTitleKey())
                        || !TitleSacrificeHistory.contains(player, def.getTitleKey(), objectMapper)) continue;
                PlayerTitleVO vo = titleVo(def, null);
                vo.setOwned(false);
                vo.setSacrificed(true);
                vo.setCanSacrifice(false);
                fillReacquisition(vo, player, def, stageProgress, totalExplorations);
                result.add(vo);
            }
        }
        return result;
    }

    @Override
    public PlayerTitleVO getEquippedTitle(Long playerId) {
        QueryWrapper<PlayerTitle> query = new QueryWrapper<>();
        query.eq("player_id", playerId).eq("equipped", 1);
        PlayerTitle pt = playerTitleMapper.selectOne(query);
        if (pt == null) return null;

        Title def = titleMapper.selectOne(
                new QueryWrapper<Title>().eq("title_key", pt.getTitleKey()));
        if (def == null) return null;

        PlayerTitleVO vo = new PlayerTitleVO();
        vo.setTitleKey(pt.getTitleKey());
        vo.setName(def.getName());
        vo.setCategory(def.getCategory());
        vo.setRarity(def.getRarity());
        vo.setDescription(def.getDescription());
        vo.setEquipped(true);
        vo.setSource(pt.getSource());
        vo.setUnlockedAt(pt.getUnlockedAt());
        TitleEffectAudit.populate(vo, parseJsonMap(def.getEffectsJson()));
        Player player = playerMapper.selectById(playerId);
        if (player != null && TitleSacrificeHistory.isReacquired(player, pt.getTitleKey(), objectMapper)) {
            vo.setReacquired(true);
            vo.setCanSacrifice(false);
        }
        return vo;
    }

    @Override
    @Transactional
    public List<PlayerTitleVO> evaluateAndUnlockTitles(Long playerId, Long userId) {
        Player player = playerMapper.selectByIdForUpdate(playerId);
        if (player == null) return Collections.emptyList();
        if (userId != null && !player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        Map<String, Object> stageProgress = parseJsonMap(player.getStageProgressJson());
        int totalExplorations = countTotalExplorations(stageProgress);
        if (TitleSacrificeHistory.prepare(player, totalExplorations,
                System.currentTimeMillis(), objectMapper) && playerMapper.updateById(player) <= 0)
            throw new IllegalStateException("保存称号重获起点失败");

        Set<String> alreadyOwned = getOwnedTitleKeys(playerId);

        QueryWrapper<Title> query = new QueryWrapper<>();
        query.eq("enabled", 1);
        List<Title> allTitles = titleMapper.selectList(query);

        List<PlayerTitleVO> newlyUnlocked = new ArrayList<>();

        for (Title def : allTitles) {
            if (alreadyOwned.contains(def.getTitleKey())) continue;
            boolean sacrificed = TitleSacrificeHistory.contains(player, def.getTitleKey(), objectMapper);

            Map<String, Object> conditions = parseJsonMap(def.getUnlockConditionsJson());
            if (conditions.isEmpty()) continue;

            if (sacrificed) {
                if (!isRepeatableCountTitle(conditions)
                        || TitleSacrificeHistory.isReacquired(player, def.getTitleKey(), objectMapper)
                        || !evaluateConditions(conditions, player, stats, stageProgress)) continue;
                Map<String, Object> record = TitleSacrificeHistory.details(player, def.getTitleKey(), objectMapper);
                if (!(record.get("progressStartCount") instanceof Number)
                        || toInt(record.get("reacquireExploreCount"), 0) < reacquireTarget(conditions)) continue;
                PlayerTitle pt = new PlayerTitle();
                pt.setPlayerId(playerId);
                pt.setTitleKey(def.getTitleKey());
                pt.setEquipped(0);
                pt.setSource("献祭重获");
                pt.setUnlockedAt(LocalDateTime.now());
                if (playerTitleMapper.insert(pt) <= 0) throw new IllegalStateException("重获称号保存失败");
                TitleSacrificeHistory.markReacquired(player, def.getTitleKey(), objectMapper);
                if (playerMapper.updateById(player) <= 0) throw new IllegalStateException("重获进度保存失败");
                PlayerTitleVO vo = titleVo(def, pt);
                vo.setReacquired(true);
                vo.setCanSacrifice(false);
                newlyUnlocked.add(vo);
                continue;
            }

            if (evaluateConditions(conditions, player, stats, stageProgress)) {
                PlayerTitle pt = new PlayerTitle();
                pt.setPlayerId(playerId);
                pt.setTitleKey(def.getTitleKey());
                pt.setEquipped(0);
                pt.setSource("系统解锁");
                pt.setUnlockedAt(LocalDateTime.now());
                playerTitleMapper.insert(pt);

                PlayerTitleVO vo = new PlayerTitleVO();
                vo.setTitleKey(def.getTitleKey());
                vo.setName(def.getName());
                vo.setCategory(def.getCategory());
                vo.setRarity(def.getRarity());
                vo.setDescription(def.getDescription());
                vo.setEquipped(false);
                vo.setSource("系统解锁");
                vo.setUnlockedAt(pt.getUnlockedAt());
                TitleEffectAudit.populate(vo, parseJsonMap(def.getEffectsJson()));
                newlyUnlocked.add(vo);
            }
        }

        return newlyUnlocked;
    }

    @Override
    public void grantTitle(Long playerId, String titleKey) {
        Player player = playerMapper.selectById(playerId);
        if (player != null && TitleSacrificeHistory.contains(player, titleKey, objectMapper)) return;
        Title title = titleMapper.selectOne(
                new QueryWrapper<Title>().eq("title_key", titleKey));
        if (title == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "称号配置不存在: " + titleKey);
        }

        QueryWrapper<PlayerTitle> ow = new QueryWrapper<>();
        ow.eq("player_id", playerId).eq("title_key", titleKey);
        if (playerTitleMapper.selectCount(ow) > 0) return;

        PlayerTitle pt = new PlayerTitle();
        pt.setPlayerId(playerId);
        pt.setTitleKey(titleKey);
        pt.setEquipped(0);
        pt.setSource("任务奖励");
        pt.setUnlockedAt(LocalDateTime.now());
        playerTitleMapper.insert(pt);
    }

    @Override
    public Map<String, Object> sacrificeTitle(Player player, String titleKey) {
        if (titleKey == null || titleKey.isBlank())
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "请选择要献祭的称号");
        if (TitleSacrificeHistory.isReacquired(player, titleKey, objectMapper))
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "已重获的称号不能再次献祭");
        QueryWrapper<PlayerTitle> ownedQuery = new QueryWrapper<>();
        ownedQuery.eq("player_id", player.getId()).eq("title_key", titleKey);
        PlayerTitle owned = playerTitleMapper.selectOne(ownedQuery);
        List<String> legacy = new ArrayList<>();
        try {
            if (player.getTitlesJson() != null && !player.getTitlesJson().isBlank())
                legacy = objectMapper.readValue(player.getTitlesJson(), new TypeReference<>() {});
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取称号列表失败");
        }
        if (owned == null && !legacy.contains(titleKey))
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "未拥有该称号");
        if (owned != null && playerTitleMapper.deleteById(owned.getId()) <= 0)
            throw new IllegalStateException("删除已献祭称号失败");
        if (legacy.removeIf(titleKey::equals)) {
            try {
                player.setTitlesJson(objectMapper.writeValueAsString(legacy));
            } catch (Exception exception) {
                throw new BusinessException(ErrorCode.SERVER_ERROR, "保存称号列表失败");
            }
        }
        int explorations = countTotalExplorations(parseJsonMap(player.getStageProgressJson()));
        TitleSacrificeHistory.record(player, titleKey, explorations, System.currentTimeMillis(), objectMapper);
        normalizeTitleHp(player);
        Title definition = titleMapper.selectOne(new QueryWrapper<Title>().eq("title_key", titleKey));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title_key", titleKey);
        result.put("name", definition != null ? definition.getName() : titleKey);
        return result;
    }

    @Override
    @Transactional
    public PlayerTitleVO equipTitle(Long playerId, String titleKey, Long userId) {
        Player player = playerMapper.selectByIdForUpdate(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        QueryWrapper<PlayerTitle> ownedQuery = new QueryWrapper<>();
        ownedQuery.eq("player_id", playerId).eq("title_key", titleKey);
        PlayerTitle target = playerTitleMapper.selectOne(ownedQuery);
        if (target == null)
            throw new BusinessException(ErrorCode.TITLE_NOT_OWNED, "尚未拥有该称号");

        // 取消当前装备
        QueryWrapper<PlayerTitle> eqQuery = new QueryWrapper<>();
        eqQuery.eq("player_id", playerId).eq("equipped", 1);
        PlayerTitle current = playerTitleMapper.selectOne(eqQuery);
        if (current != null) {
            current.setEquipped(0);
            playerTitleMapper.updateById(current);
        }

        // 装备新称号
        target.setEquipped(1);
        playerTitleMapper.updateById(target);
        if (normalizeTitleHp(player) && playerMapper.updateById(player) <= 0)
            throw new IllegalStateException("保存佩戴后的生命上限失败");

        Title def = titleMapper.selectOne(
                new QueryWrapper<Title>().eq("title_key", titleKey));

        PlayerTitleVO vo = new PlayerTitleVO();
        vo.setTitleKey(titleKey);
        vo.setName(def != null ? def.getName() : titleKey);
        vo.setCategory(def != null ? def.getCategory() : null);
        vo.setRarity(def != null ? def.getRarity() : null);
        vo.setDescription(def != null ? def.getDescription() : null);
        vo.setEquipped(true);
        vo.setSource(target.getSource());
        vo.setUnlockedAt(target.getUnlockedAt());
        TitleEffectAudit.populate(vo, def == null ? Collections.emptyMap() : parseJsonMap(def.getEffectsJson()));
        if (TitleSacrificeHistory.isReacquired(player, titleKey, objectMapper)) {
            vo.setReacquired(true);
            vo.setCanSacrifice(false);
        }
        return vo;
    }

    @Override
    public Map<String, Object> calculateTitleEffects(Long playerId) {
        // 基础版: 只计算装备称号的效果
        // 后续扩展可合并所有已解锁称号的效果
        PlayerTitleVO equipped = getEquippedTitle(playerId);
        if (equipped == null) return Collections.emptyMap();

        QueryWrapper<Title> query = new QueryWrapper<>();
        query.eq("title_key", equipped.getTitleKey()).eq("enabled", 1);
        Title def = titleMapper.selectOne(query);
        if (def == null) return Collections.emptyMap();

        Map<String, Object> effects = parseJsonMap(def.getEffectsJson());
        capTitleEffect(effects, "channelHeatGainRate", 0.25);
        capTitleEffect(effects, "storyEventBonus", 0.15);
        if (effects.get("maxHp") instanceof Number value)
            effects.put("maxHp", Math.max(0, Math.min(50, value.intValue())));
        return effects;
    }

    @Override
    public void prepareReacquisitionProgress(Player player) {
        int total = countTotalExplorations(parseJsonMap(player.getStageProgressJson()));
        TitleSacrificeHistory.prepare(player, total, System.currentTimeMillis(), objectMapper);
    }

    @Override
    public void recordQualifiedExploration(Player player) {
        TitleSacrificeHistory.countQualifiedExploration(player, System.currentTimeMillis(), objectMapper);
    }

    private boolean normalizeTitleHp(Player player) {
        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        maxHpProjectionService.normalizeForPersistence(stats, player.getId());
        try {
            String updated = objectMapper.writeValueAsString(stats);
            if (Objects.equals(player.getStatsJson(), updated)) return false;
            player.setStatsJson(updated);
            return true;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "保存称号生命上限失败");
        }
    }

    private void capTitleEffect(Map<String, Object> effects, String key, double cap) {
        if (effects.get(key) instanceof Number value && Double.isFinite(value.doubleValue()))
            effects.put(key, Math.min(cap, value.doubleValue()));
    }

    private PlayerTitleVO titleVo(Title def, PlayerTitle owned) {
        PlayerTitleVO vo = new PlayerTitleVO();
        vo.setTitleKey(def.getTitleKey());
        vo.setName(def.getName());
        vo.setCategory(def.getCategory());
        vo.setRarity(def.getRarity());
        vo.setDescription(def.getDescription());
        vo.setEquipped(owned != null && Integer.valueOf(1).equals(owned.getEquipped()));
        if (owned != null) {
            vo.setSource(owned.getSource());
            vo.setUnlockedAt(owned.getUnlockedAt());
        }
        TitleEffectAudit.populate(vo, parseJsonMap(def.getEffectsJson()));
        return vo;
    }

    private void fillReacquisition(PlayerTitleVO vo, Player player, Title def,
                                   Map<String, Object> stageProgress, int totalExplorations) {
        Map<String, Object> conditions = parseJsonMap(def.getUnlockConditionsJson());
        if (!isRepeatableCountTitle(conditions)) return;
        Map<String, Object> record = TitleSacrificeHistory.details(player, def.getTitleKey(), objectMapper);
        if (Boolean.TRUE.equals(record.get("reacquired"))) return;
        int target = reacquireTarget(conditions);
        vo.setReacquireTarget(target);
        long now = System.currentTimeMillis();
        long sacrificedAt = record.get("sacrificedAt") instanceof Number value ? value.longValue() : now;
        long remaining = Math.max(0, TitleSacrificeHistory.COOLDOWN_MILLIS - (now - sacrificedAt));
        vo.setReacquireDaysRemaining((remaining + 86_399_999L) / 86_400_000L);
        if (record.get("progressStartCount") instanceof Number && remaining == 0) {
            int progress = Math.max(0, toInt(record.get("reacquireExploreCount"), 0));
            vo.setReacquireProgress(Math.min(target, progress));
            vo.setCanReacquire(progress >= target
                    && evaluateConditions(conditions, player, parseJsonMap(player.getStatsJson()), stageProgress));
        }
    }

    private boolean isRepeatableCountTitle(Map<String, Object> conditions) {
        return conditions.size() == 1 && (conditions.get("exploreCount") instanceof Number
                || conditions.get("locationExploreCount") instanceof Map<?, ?> locations && !locations.isEmpty()
                && locations.values().stream().allMatch(value -> value instanceof Number)
                || conditions.get("bossClue") instanceof Map<?, ?> clues && !clues.isEmpty()
                && clues.values().stream().allMatch(value -> value instanceof Number));
    }

    private int reacquireTarget(Map<String, Object> conditions) {
        long original = 0;
        if (conditions.get("exploreCount") instanceof Number count) {
            original = count.longValue();
        } else if (conditions.get("locationExploreCount") instanceof Map<?, ?> locations) {
            for (Object value : locations.values()) original += ((Number) value).longValue();
        } else if (conditions.get("bossClue") instanceof Map<?, ?> clues) {
            for (Object value : clues.values()) original += ((Number) value).longValue();
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.max(30L, original * 3L));
    }

    // ─── 条件评估 ───

    @SuppressWarnings("unchecked")
    private boolean evaluateConditions(Map<String, Object> conditions, Player player,
                                       Map<String, Object> stats, Map<String, Object> stageProgress) {
        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();

            switch (key) {
                case "exploreCount": {
                    int required = toInt(val, 1);
                    int total = countTotalExplorations(stageProgress);
                    if (total < required) return false;
                    break;
                }
                case "locationExploreCount": {
                    Map<String, Object> locMap = (Map<String, Object>) val;
                    Map<String, Object> explorations = (Map<String, Object>)
                            stageProgress.getOrDefault("explorationsByLocation", Collections.emptyMap());
                    for (Map.Entry<String, Object> e : locMap.entrySet()) {
                        int count = toInt(explorations.get(e.getKey()), 0);
                        if (count < toInt(e.getValue(), 1)) return false;
                    }
                    break;
                }
                case "storyFragments": {
                    if (player.getStoryFragments() == null || player.getStoryFragments() < toInt(val, 1))
                        return false;
                    break;
                }
                case "channelHeat": {
                    int heat = toInt(stats.get("channelHeat"), 0);
                    if (heat < toInt(val, 1)) return false;
                    break;
                }
                case "bossClue": {
                    Map<String, Object> clueMap = (Map<String, Object>) val;
                    Map<String, Object> bossClues = (Map<String, Object>)
                            stageProgress.getOrDefault("bossClues", Collections.emptyMap());
                    for (Map.Entry<String, Object> e : clueMap.entrySet()) {
                        int count = toInt(bossClues.get(e.getKey()), 0);
                        if (count < toInt(e.getValue(), 1)) return false;
                    }
                    break;
                }
                case "worldLineShift": {
                    int wls = toInt(stats.get("worldLineShift"), 0);
                    if (wls < toInt(val, 1)) return false;
                    break;
                }
                default:
                    break;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private int countTotalExplorations(Map<String, Object> stageProgress) {
        Map<String, Object> byLoc = (Map<String, Object>)
                stageProgress.getOrDefault("explorationsByLocation", Collections.emptyMap());
        int total = 0;
        for (Object v : byLoc.values()) {
            total += toInt(v, 0);
        }
        return total;
    }

    private Set<String> getOwnedTitleKeys(Long playerId) {
        QueryWrapper<PlayerTitle> query = new QueryWrapper<>();
        query.eq("player_id", playerId);
        List<PlayerTitle> pts = playerTitleMapper.selectList(query);
        Set<String> keys = new HashSet<>();
        for (PlayerTitle pt : pts) {
            keys.add(pt.getTitleKey());
        }
        return keys;
    }

    // ─── 工具方法 ───

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
