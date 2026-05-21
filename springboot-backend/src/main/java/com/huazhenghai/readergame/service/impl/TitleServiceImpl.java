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
import com.huazhenghai.readergame.vo.PlayerTitleVO;
import com.huazhenghai.readergame.vo.TitleVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class TitleServiceImpl implements TitleService {

    private final TitleMapper titleMapper;
    private final PlayerTitleMapper playerTitleMapper;
    private final PlayerMapper playerMapper;
    private final ObjectMapper objectMapper;

    public TitleServiceImpl(TitleMapper titleMapper,
                            PlayerTitleMapper playerTitleMapper,
                            PlayerMapper playerMapper,
                            ObjectMapper objectMapper) {
        this.titleMapper = titleMapper;
        this.playerTitleMapper = playerTitleMapper;
        this.playerMapper = playerMapper;
        this.objectMapper = objectMapper;
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
            vo.setEffects(parseJsonMap(t.getEffectsJson()));
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<PlayerTitleVO> getPlayerTitles(Long playerId) {
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
            result.add(vo);
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
        return vo;
    }

    @Override
    public List<PlayerTitleVO> evaluateAndUnlockTitles(Long playerId, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null) return Collections.emptyList();
        if (userId != null && !player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");

        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        Map<String, Object> stageProgress = parseJsonMap(player.getStageProgressJson());

        Set<String> alreadyOwned = getOwnedTitleKeys(playerId);

        QueryWrapper<Title> query = new QueryWrapper<>();
        query.eq("enabled", 1);
        List<Title> allTitles = titleMapper.selectList(query);

        List<PlayerTitleVO> newlyUnlocked = new ArrayList<>();

        for (Title def : allTitles) {
            if (alreadyOwned.contains(def.getTitleKey())) continue;

            Map<String, Object> conditions = parseJsonMap(def.getUnlockConditionsJson());
            if (conditions.isEmpty()) continue;

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
                newlyUnlocked.add(vo);
            }
        }

        return newlyUnlocked;
    }

    @Override
    public void grantTitle(Long playerId, String titleKey) {
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
    public PlayerTitleVO equipTitle(Long playerId, String titleKey, Long userId) {
        Player player = playerMapper.selectById(playerId);
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

        return parseJsonMap(def.getEffectsJson());
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
