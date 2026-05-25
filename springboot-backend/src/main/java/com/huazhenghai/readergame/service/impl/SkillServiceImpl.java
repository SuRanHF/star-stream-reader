package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerSkill;
import com.huazhenghai.readergame.entity.Skill;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.PlayerSkillMapper;
import com.huazhenghai.readergame.mapper.SkillMapper;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.service.SkillService;
import com.huazhenghai.readergame.vo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SkillServiceImpl implements SkillService {

    private static final List<String> RANK_ORDER = List.of(
            "F", "E", "D", "C", "B", "A", "S", "SS", "SSS", "EX");

    // effects_json 中可累加到 bonus 的数值字段
    private static final Set<String> NUMERIC_EFFECTS = Set.of(
            "attack", "defense", "speed", "maxHp", "maxStamina",
            "critRate", "critDamage", "insight", "willpower", "leadership",
            "channelHeat", "worldLineShift",
            "storyEventBonus", "staminaCostReduce", "channelHeatGainRate",
            "narrativePressureBonus");

    private final PlayerMapper playerMapper;
    private final SkillMapper skillMapper;
    private final PlayerSkillMapper playerSkillMapper;
    private final PlayerLogService playerLogService;
    private final ObjectMapper objectMapper;

    public SkillServiceImpl(PlayerMapper playerMapper,
                            SkillMapper skillMapper,
                            PlayerSkillMapper playerSkillMapper,
                            PlayerLogService playerLogService,
                            ObjectMapper objectMapper) {
        this.playerMapper = playerMapper;
        this.skillMapper = skillMapper;
        this.playerSkillMapper = playerSkillMapper;
        this.playerLogService = playerLogService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<SkillVO> getAllSkills() {
        QueryWrapper<Skill> query = new QueryWrapper<>();
        query.eq("enabled", 1);
        return skillMapper.selectList(query).stream()
                .map(this::toSkillVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<PlayerSkillVO> getPlayerSkills(Long playerId, Long userId) {
        validateOwnership(playerId, userId);

        QueryWrapper<PlayerSkill> psQuery = new QueryWrapper<>();
        psQuery.eq("player_id", playerId);
        List<PlayerSkill> psList = playerSkillMapper.selectList(psQuery);

        List<PlayerSkillVO> result = new ArrayList<>();
        for (PlayerSkill ps : psList) {
            Skill skill = skillMapper.selectOne(
                    new QueryWrapper<Skill>().eq("skill_key", ps.getSkillKey()).eq("enabled", 1));
            if (skill == null) continue;
            result.add(toPlayerSkillVO(skill, ps));
        }
        return result;
    }

    @Override
    public List<SkillVO> evaluateUnlockableSkills(Long playerId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null) return Collections.emptyList();

        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        Map<String, Object> stageProgress = parseJsonMap(player.getStageProgressJson());
        Set<String> ownedKeys = getOwnedSkillKeys(playerId);

        List<Skill> allEnabled = skillMapper.selectList(
                new QueryWrapper<Skill>().eq("enabled", 1));

        List<SkillVO> unlockable = new ArrayList<>();
        for (Skill skill : allEnabled) {
            if (ownedKeys.contains(skill.getSkillKey())) continue;
            if (checkUnlockConditions(skill, player, stats, stageProgress)) {
                SkillVO vo = toSkillVO(skill);
                vo.setUnlockable(true);
                unlockable.add(vo);
            }
        }
        return unlockable;
    }

    @Override
    @Transactional
    public PlayerSkillVO unlockSkill(Long playerId, String skillKey, Long userId) {
        validateOwnership(playerId, userId);

        // 检查休息状态
        Player player = playerMapper.selectById(playerId);
        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        if (Boolean.TRUE.equals(stats.get("isResting"))) {
            throw new BusinessException(ErrorCode.PLAYER_RESTING, "休息中无法解锁技能");
        }

        // 技能存在且启用
        Skill skill = skillMapper.selectOne(
                new QueryWrapper<Skill>().eq("skill_key", skillKey));
        if (skill == null)
            throw new BusinessException(ErrorCode.SKILL_NOT_FOUND, "技能不存在: " + skillKey);
        if (skill.getEnabled() == null || skill.getEnabled() != 1)
            throw new BusinessException(ErrorCode.SKILL_DISABLED, "技能已被禁用: " + skillKey);

        // 检查是否已解锁
        QueryWrapper<PlayerSkill> psQuery = new QueryWrapper<>();
        psQuery.eq("player_id", playerId).eq("skill_key", skillKey);
        if (playerSkillMapper.selectCount(psQuery) > 0)
            throw new BusinessException(ErrorCode.SKILL_ALREADY_LEARNED, "已学习该技能");

        // 校验解锁条件
        Map<String, Object> stageProgress = parseJsonMap(player.getStageProgressJson());
        if (!checkUnlockConditions(skill, player, stats, stageProgress))
            throw new BusinessException(ErrorCode.SKILL_NOT_UNLOCKED, "不满足技能解锁条件");

        // 扣除消耗
        Map<String, Object> cost = parseJsonMap(skill.getCostJson());
        if (!cost.isEmpty()) {
            int coinsCost = toInt(cost.get("coins"), 0);
            int fragsCost = toInt(cost.get("storyFragments"), 0);

            if (coinsCost > 0 && player.getCoins() < coinsCost)
                throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH,
                        "金币不足，需要 " + coinsCost + " 金币");
            if (fragsCost > 0 && player.getStoryFragments() < fragsCost)
                throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH,
                        "故事碎片不足，需要 " + fragsCost + " 碎片");

            if (coinsCost > 0) player.setCoins(player.getCoins() - coinsCost);
            if (fragsCost > 0) player.setStoryFragments(player.getStoryFragments() - fragsCost);
        }

        // 插入 player_skills
        PlayerSkill ps = new PlayerSkill();
        ps.setPlayerId(playerId);
        ps.setSkillKey(skillKey);
        ps.setLevel(1);
        ps.setEquipped(0);
        ps.setUnlockedAt(java.time.LocalDateTime.now());
        playerSkillMapper.insert(ps);

        // 更新玩家资源
        playerMapper.updateById(player);

        // 写日志
        playerLogService.addLog(playerId, "skill_unlock", "习得技能: " + skill.getName());

        return toPlayerSkillVO(skill, ps);
    }

    @Override
    @Transactional
    public PlayerSkillVO equipSkill(Long playerId, String skillKey, Long userId) {
        validateOwnership(playerId, userId);

        // 检查休息状态
        Player player = playerMapper.selectById(playerId);
        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        if (Boolean.TRUE.equals(stats.get("isResting"))) {
            throw new BusinessException(ErrorCode.PLAYER_RESTING, "休息中无法装备技能");
        }

        // 查找玩家技能记录
        QueryWrapper<PlayerSkill> psQuery = new QueryWrapper<>();
        psQuery.eq("player_id", playerId).eq("skill_key", skillKey);
        PlayerSkill ps = playerSkillMapper.selectOne(psQuery);
        if (ps == null)
            throw new BusinessException(ErrorCode.SKILL_NOT_UNLOCKED, "尚未学习该技能");

        // 查找技能定义
        Skill skill = skillMapper.selectOne(
                new QueryWrapper<Skill>().eq("skill_key", skillKey));
        if (skill == null)
            throw new BusinessException(ErrorCode.SKILL_NOT_FOUND, "技能不存在");

        // 已装备则无需操作
        if (ps.getEquipped() != null && ps.getEquipped() == 1) {
            return toPlayerSkillVO(skill, ps);
        }

        ps.setEquipped(1);
        playerSkillMapper.updateById(ps);
        playerLogService.addLog(playerId, "skill_equip", "装备技能: " + skill.getName());
        return toPlayerSkillVO(skill, ps);
    }

    @Override
    @Transactional
    public PlayerSkillVO unequipSkill(Long playerId, String skillKey, Long userId) {
        validateOwnership(playerId, userId);

        // 查找玩家技能记录
        QueryWrapper<PlayerSkill> psQuery = new QueryWrapper<>();
        psQuery.eq("player_id", playerId).eq("skill_key", skillKey);
        PlayerSkill ps = playerSkillMapper.selectOne(psQuery);
        if (ps == null)
            throw new BusinessException(ErrorCode.SKILL_NOT_UNLOCKED, "尚未学习该技能");

        // 查找技能定义
        Skill skill = skillMapper.selectOne(
                new QueryWrapper<Skill>().eq("skill_key", skillKey));
        if (skill == null)
            throw new BusinessException(ErrorCode.SKILL_NOT_FOUND, "技能不存在");

        // 已卸下则无需操作
        if (ps.getEquipped() == null || ps.getEquipped() == 0) {
            return toPlayerSkillVO(skill, ps);
        }

        ps.setEquipped(0);
        playerSkillMapper.updateById(ps);
        playerLogService.addLog(playerId, "skill_unequip", "卸下技能: " + skill.getName());
        return toPlayerSkillVO(skill, ps);
    }

    @Override
    public Map<String, Object> calculateSkillBonus(Long playerId) {
        QueryWrapper<PlayerSkill> psQuery = new QueryWrapper<>();
        psQuery.eq("player_id", playerId).eq("equipped", 1);
        List<PlayerSkill> psList = playerSkillMapper.selectList(psQuery);

        Map<String, Object> bonus = new LinkedHashMap<>();
        for (PlayerSkill ps : psList) {
            Skill skill = skillMapper.selectOne(
                    new QueryWrapper<Skill>().eq("skill_key", ps.getSkillKey()));
            if (skill == null) continue;

            Map<String, Object> effects = parseJsonMap(skill.getEffectsJson());
            for (Map.Entry<String, Object> entry : effects.entrySet()) {
                String key = entry.getKey();
                if (!NUMERIC_EFFECTS.contains(key)) continue;
                double add = toDouble(entry.getValue(), 0);
                if (add == 0) continue;
                double current = toDouble(bonus.get(key), 0);
                bonus.put(key, current + add);
            }
        }
        return bonus;
    }

    @Override
    public Map<String, String> getSkillBonusDetail(Long playerId) {
        QueryWrapper<PlayerSkill> psQuery = new QueryWrapper<>();
        psQuery.eq("player_id", playerId).eq("equipped", 1);
        List<PlayerSkill> psList = playerSkillMapper.selectList(psQuery);

        Map<String, StringBuilder> detail = new LinkedHashMap<>();
        for (PlayerSkill ps : psList) {
            Skill skill = skillMapper.selectOne(
                    new QueryWrapper<Skill>().eq("skill_key", ps.getSkillKey()));
            if (skill == null) continue;

            String skillName = skill.getName() != null ? skill.getName() : ps.getSkillKey();
            Map<String, Object> effects = parseJsonMap(skill.getEffectsJson());
            for (Map.Entry<String, Object> entry : effects.entrySet()) {
                String key = entry.getKey();
                if (!NUMERIC_EFFECTS.contains(key)) continue;
                double val = toDouble(entry.getValue(), 0);
                if (val == 0) continue;
                detail.computeIfAbsent(key, k -> new StringBuilder())
                        .append(detail.get(key).isEmpty() ? "" : ", ")
                        .append(skillName).append("+").append(formatBonus(val));
            }
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, StringBuilder> e : detail.entrySet()) {
            result.put(e.getKey(), e.getValue().toString());
        }
        return result;
    }

    private String formatBonus(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.valueOf(Math.round(v * 100.0) / 100.0);
    }

    @Override
    public SkillSummaryVO getSkillSummary(Long playerId) {
        Set<String> owned = getOwnedSkillKeys(playerId);
        int ownedCount = owned.size();
        List<SkillVO> unlockable = evaluateUnlockableSkills(playerId);
        boolean hasRare = false;

        if (ownedCount > 0) {
            for (String key : owned) {
                Skill skill = skillMapper.selectOne(
                        new QueryWrapper<Skill>().eq("skill_key", key));
                if (skill != null && isRare(skill.getRarity())) {
                    hasRare = true;
                    break;
                }
            }
        }

        return new SkillSummaryVO(ownedCount, unlockable.size(), hasRare);
    }

    // ─── 辅助方法 ───

    private void validateOwnership(Long playerId, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");
    }

    private boolean checkUnlockConditions(Skill skill, Player player,
                                           Map<String, Object> stats,
                                           Map<String, Object> stageProgress) {
        Map<String, Object> conditions = parseJsonMap(skill.getUnlockConditionsJson());
        if (conditions.isEmpty()) return true;

        if (conditions.containsKey("levelMin")) {
            int required = toInt(conditions.get("levelMin"), 0);
            if (toInt(stats.get("level"), 1) < required) return false;
        }
        if (conditions.containsKey("storyFragmentsMin")) {
            int required = toInt(conditions.get("storyFragmentsMin"), 0);
            if (player.getStoryFragments() < required) return false;
        }
        if (conditions.containsKey("coinsMin")) {
            int required = toInt(conditions.get("coinsMin"), 0);
            if (player.getCoins() < required) return false;
        }
        if (conditions.containsKey("avatarRankMin")) {
            String required = (String) conditions.get("avatarRankMin");
            String current = stats.get("avatarRank") != null
                    ? stats.get("avatarRank").toString() : "F";
            if (compareRank(current, required) < 0) return false;
        }
        if (conditions.containsKey("channelHeatMin")) {
            int required = toInt(conditions.get("channelHeatMin"), 0);
            if (toInt(stats.get("channelHeat"), 0) < required) return false;
        }
        if (conditions.containsKey("insightMin")) {
            int required = toInt(conditions.get("insightMin"), 0);
            if (toInt(stats.get("insight"), 0) < required) return false;
        }
        if (conditions.containsKey("willpowerMin")) {
            int required = toInt(conditions.get("willpowerMin"), 0);
            if (toInt(stats.get("willpower"), 0) < required) return false;
        }
        if (conditions.containsKey("locationExploreCount")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> requirement = (Map<String, Object>) conditions.get("locationExploreCount");
            @SuppressWarnings("unchecked")
            Map<String, Object> byLoc = (Map<String, Object>) stageProgress.get("explorationsByLocation");
            if (byLoc == null) return false;
            for (Map.Entry<String, Object> entry : requirement.entrySet()) {
                int required = toInt(entry.getValue(), 0);
                int actual = toInt(byLoc.get(entry.getKey()), 0);
                if (actual < required) return false;
            }
        }
        return true;
    }

    private int compareRank(String current, String required) {
        int curIdx = RANK_ORDER.indexOf(current);
        int reqIdx = RANK_ORDER.indexOf(required);
        if (curIdx == -1) curIdx = 0;
        if (reqIdx == -1) reqIdx = 0;
        return Integer.compare(curIdx, reqIdx);
    }

    private Set<String> getOwnedSkillKeys(Long playerId) {
        QueryWrapper<PlayerSkill> query = new QueryWrapper<>();
        query.eq("player_id", playerId).select("skill_key");
        return playerSkillMapper.selectList(query).stream()
                .map(PlayerSkill::getSkillKey)
                .collect(Collectors.toSet());
    }

    private boolean isRare(String rarity) {
        if (rarity == null) return false;
        return switch (rarity.toLowerCase()) {
            case "rare", "epic", "legendary" -> true;
            default -> false;
        };
    }

    @SuppressWarnings("unchecked")
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

    // ─── VO 转换 ───

    private SkillVO toSkillVO(Skill skill) {
        SkillVO vo = new SkillVO();
        vo.setSkillKey(skill.getSkillKey());
        vo.setName(skill.getName());
        vo.setType(skill.getType());
        vo.setRarity(skill.getRarity());
        vo.setDescription(skill.getDescription());
        vo.setUnlockConditions(parseJsonMap(skill.getUnlockConditionsJson()));
        vo.setEffects(parseJsonMap(skill.getEffectsJson()));
        vo.setCost(parseJsonMap(skill.getCostJson()));
        vo.setCooldownSeconds(skill.getCooldownSeconds());
        return vo;
    }

    private PlayerSkillVO toPlayerSkillVO(Skill skill, PlayerSkill ps) {
        PlayerSkillVO vo = new PlayerSkillVO();
        vo.setSkillKey(skill.getSkillKey());
        vo.setName(skill.getName());
        vo.setType(skill.getType());
        vo.setRarity(skill.getRarity());
        vo.setDescription(skill.getDescription());
        vo.setEffects(parseJsonMap(skill.getEffectsJson()));
        vo.setCooldownSeconds(skill.getCooldownSeconds());
        vo.setLevel(ps.getLevel());
        vo.setEquipped(ps.getEquipped());
        vo.setUnlockedAt(ps.getUnlockedAt() != null ? ps.getUnlockedAt().toString() : null);
        return vo;
    }
}
