package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.*;
import com.huazhenghai.readergame.mapper.*;
import com.huazhenghai.readergame.service.FactionService;
import com.huazhenghai.readergame.service.PlayerActionReceiptService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import com.huazhenghai.readergame.service.QuestService;
import com.huazhenghai.readergame.vo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FactionServiceImpl implements FactionService {

    private static final Logger log = LoggerFactory.getLogger(FactionServiceImpl.class);

    private final ConstellationFactionMapper factionMapper;
    private final PlayerFactionMapper playerFactionMapper;
    private final FactionContributionMapper contributionMapper;
    private final FactionWarMapper warMapper;
    private final FactionSkillMapper skillMapper;
    private final PlayerFactionSkillMapper playerSkillMapper;
    private final PlayerMapper playerMapper;
    private final PlayerLogMapper playerLogMapper;
    private final ObjectMapper objectMapper;
    private final QuestService questService;
    private final PlayerActionReceiptService actionReceiptService;
    private final PlayerResourceTransactionService resourceTransactionService;

    public FactionServiceImpl(ConstellationFactionMapper factionMapper,
                               PlayerFactionMapper playerFactionMapper,
                               FactionContributionMapper contributionMapper,
                               FactionWarMapper warMapper,
                               FactionSkillMapper skillMapper,
                               PlayerFactionSkillMapper playerSkillMapper,
                               PlayerMapper playerMapper,
                               PlayerLogMapper playerLogMapper,
                               ObjectMapper objectMapper,
                               QuestService questService,
                               PlayerActionReceiptService actionReceiptService,
                               PlayerResourceTransactionService resourceTransactionService) {
        this.factionMapper = factionMapper;
        this.playerFactionMapper = playerFactionMapper;
        this.contributionMapper = contributionMapper;
        this.warMapper = warMapper;
        this.skillMapper = skillMapper;
        this.playerSkillMapper = playerSkillMapper;
        this.playerMapper = playerMapper;
        this.playerLogMapper = playerLogMapper;
        this.objectMapper = objectMapper;
        this.questService = questService;
        this.actionReceiptService = actionReceiptService;
        this.resourceTransactionService = resourceTransactionService;
    }

    @Override
    public List<FactionVO> getAllFactions(Long playerId) {
        QueryWrapper<ConstellationFaction> qw = new QueryWrapper<>();
        qw.eq("enabled", 1).orderByDesc("total_contribution");
        return factionMapper.selectList(qw).stream()
                .map(f -> buildFactionVO(f, playerId))
                .collect(Collectors.toList());
    }

    @Override
    public FactionVO getFactionDetail(String factionKey, Long playerId) {
        ConstellationFaction faction = getFactionByKey(factionKey);
        FactionVO vo = buildFactionVO(faction, playerId);
        vo.setTopContributors(getFactionContributionRankings(factionKey));
        return vo;
    }

    @Override
    public PlayerFactionVO getMyFaction(Long playerId, Long userId) {
        validatePlayerOwnership(playerId, userId);
        QueryWrapper<PlayerFaction> qw = new QueryWrapper<>();
        qw.eq("player_id", playerId).eq("status", "active");
        PlayerFaction pf = playerFactionMapper.selectOne(qw);
        if (pf == null) return null;
        return toPlayerFactionVO(pf);
    }

    @Override
    @Transactional
    public PlayerFactionVO joinFaction(Long playerId, String factionKey, Long userId) {
        validatePlayerOwnership(playerId, userId);

        ConstellationFaction faction = getFactionByKey(factionKey);
        if (faction.getEnabled() == 0)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "该阵营不可加入");

        LocalDateTime now = LocalDateTime.now();

        // 检查已有记录：active→拒绝, left→重新激活, 不存在→新建
        QueryWrapper<PlayerFaction> existQ = new QueryWrapper<>();
        existQ.eq("player_id", playerId);
        PlayerFaction existing = playerFactionMapper.selectOne(existQ);

        PlayerFaction pf;
        if (existing != null) {
            if ("active".equals(existing.getStatus()))
                throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "你已有活跃阵营，请先退出");
            // 重新激活旧记录
            pf = existing;
            pf.setFactionKey(factionKey);
            pf.setRole("member");
            pf.setReputation(0L);
            pf.setContributionTotal(0L);
            pf.setJoinedAt(now);
            pf.setLeftAt(null);
            pf.setStatus("active");
            pf.setUpdatedAt(now);
            playerFactionMapper.updateById(pf);
        } else {
            pf = new PlayerFaction();
            pf.setPlayerId(playerId);
            pf.setFactionKey(factionKey);
            pf.setRole("member");
            pf.setReputation(0L);
            pf.setContributionTotal(0L);
            pf.setJoinedAt(now);
            pf.setStatus("active");
            pf.setCreatedAt(now);
            pf.setUpdatedAt(now);
            playerFactionMapper.insert(pf);
        }

        UpdateWrapper<ConstellationFaction> uw = new UpdateWrapper<>();
        uw.eq("faction_key", factionKey)
          .setSql("member_count = member_count + 1");
        factionMapper.update(null, uw);

        writeLog(playerId, "faction", "加入了阵营「" + faction.getName() + "」");
        return toPlayerFactionVO(pf);
    }

    @Override
    @Transactional
    public void leaveFaction(Long playerId, Long userId) {
        validatePlayerOwnership(playerId, userId);

        QueryWrapper<PlayerFaction> qw = new QueryWrapper<>();
        qw.eq("player_id", playerId).eq("status", "active");
        PlayerFaction pf = playerFactionMapper.selectOne(qw);
        if (pf == null)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "你未加入任何活跃阵营");

        LocalDateTime now = LocalDateTime.now();
        pf.setStatus("left");
        pf.setLeftAt(now);
        pf.setUpdatedAt(now);
        playerFactionMapper.updateById(pf);

        UpdateWrapper<ConstellationFaction> uw = new UpdateWrapper<>();
        uw.eq("faction_key", pf.getFactionKey())
          .setSql("member_count = GREATEST(member_count - 1, 0)");
        factionMapper.update(null, uw);

        ConstellationFaction faction = factionMapper.selectOne(
                new QueryWrapper<ConstellationFaction>().eq("faction_key", pf.getFactionKey()));
        String factionName = faction != null ? faction.getName() : pf.getFactionKey();
        writeLog(playerId, "faction", "退出了阵营「" + factionName + "」");
    }

    @Override
    // Combat/exploration treat this as best-effort; isolate failures without committing ahead of them.
    @Transactional(propagation = Propagation.NESTED)
    public FactionContributionVO contribute(Long playerId, String factionKey, String contributionType,
                                            Long value, String source, String relatedId, Long userId) {
        // Manual contributions lock the player here; automatic contributions run inside an
        // exploration/combat transaction that already holds the same player row lock.
        if (userId != null) lockOwnedPlayerForUpdate(playerId, userId);

        if (value == null || value <= 0)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "贡献值必须大于0");

        // Lock reputation before reading it so contributions cannot overwrite a concurrent skill purchase.
        PlayerFaction pf = playerFactionMapper.selectActiveByPlayerIdForUpdate(playerId);
        if (pf == null) return null;

        // auto-contribution: resolve factionKey from player's active faction
        if (factionKey == null || factionKey.isBlank()) {
            factionKey = pf.getFactionKey();
        }
        if (!Objects.equals(pf.getFactionKey(), factionKey)) return null;

        if (contributionType == null || contributionType.isBlank())
            contributionType = "manual";

        LocalDateTime now = LocalDateTime.now();
        FactionContribution fc = new FactionContribution();
        fc.setFactionKey(factionKey);
        fc.setPlayerId(playerId);
        fc.setContributionType(contributionType);
        fc.setValue(value);
        fc.setSource(source);
        fc.setRelatedId(relatedId);
        fc.setCreatedAt(now);
        contributionMapper.insert(fc);

        pf.setContributionTotal(pf.getContributionTotal() + value);
        pf.setReputation(pf.getReputation() + value);
        pf.setUpdatedAt(now);
        playerFactionMapper.updateById(pf);

        UpdateWrapper<ConstellationFaction> uw = new UpdateWrapper<>();
        uw.eq("faction_key", factionKey)
          .setSql("total_contribution = total_contribution + " + value)
          .setSql("exp = exp + " + value);
        factionMapper.update(null, uw);

        ConstellationFaction faction = getFactionByKey(factionKey);
        int newLevel = calculateFactionLevel(faction.getExp());
        if (newLevel > faction.getLevel()) {
            UpdateWrapper<ConstellationFaction> lw = new UpdateWrapper<>();
            lw.eq("faction_key", factionKey).set("level", newLevel);
            factionMapper.update(null, lw);
        }

        writeLog(playerId, "faction", "向阵营贡献 " + value + " 点");

        FactionContributionVO vo = new FactionContributionVO();
        vo.setId(fc.getId());
        vo.setFactionKey(factionKey);
        vo.setPlayerId(playerId);
        vo.setPlayerName(getPlayerName(playerId));
        vo.setContributionType(contributionType);
        vo.setValue(value);
        vo.setSource(source);
        vo.setRelatedId(relatedId);
        vo.setCreatedAt(now.toString());

        // 自动任务进度
        try { questService.addProgress(playerId, "faction_contribution_value", value.intValue(), source, relatedId); }
        catch (Exception e) {
            log.warn("Quest progress failed after faction contribution for player {}", playerId, e);
        }

        return vo;
    }

    @Override
    public List<FactionRankingVO> getFactionRankings() {
        QueryWrapper<ConstellationFaction> qw = new QueryWrapper<>();
        qw.eq("enabled", 1).orderByDesc("total_contribution").orderByAsc("faction_key");
        List<ConstellationFaction> factions = factionMapper.selectList(qw);
        List<FactionRankingVO> result = new ArrayList<>();
        int rank = 0;
        Long previousContribution = null;
        for (int i = 0; i < factions.size(); i++) {
            ConstellationFaction f = factions.get(i);
            long contribution = f.getTotalContribution() == null ? 0L : f.getTotalContribution();
            if (!Objects.equals(previousContribution, contribution)) rank = i + 1;
            FactionRankingVO vo = new FactionRankingVO();
            vo.setRank(rank);
            vo.setFactionKey(f.getFactionKey());
            vo.setFactionName(f.getName());
            vo.setAlignment(f.getAlignment());
            vo.setLevel(f.getLevel());
            vo.setMemberCount(f.getMemberCount());
            vo.setTotalContribution(f.getTotalContribution());
            result.add(vo);
            previousContribution = contribution;
        }
        return result;
    }

    @Override
    public List<FactionRankingVO> getFactionContributionRankings(String factionKey) {
        QueryWrapper<PlayerFaction> pq = new QueryWrapper<>();
        pq.eq("faction_key", factionKey).eq("status", "active")
          .orderByDesc("contribution_total").orderByAsc("player_id").last("LIMIT 50");
        List<PlayerFaction> list = playerFactionMapper.selectList(pq);
        List<FactionRankingVO> result = new ArrayList<>();
        int rank = 0;
        Long previousContribution = null;
        for (int i = 0; i < list.size(); i++) {
            PlayerFaction pf = list.get(i);
            long contribution = pf.getContributionTotal() == null ? 0L : pf.getContributionTotal();
            if (!Objects.equals(previousContribution, contribution)) rank = i + 1;
            FactionRankingVO vo = new FactionRankingVO();
            vo.setRank(rank);
            vo.setPlayerId(pf.getPlayerId());
            vo.setPlayerName(getPlayerName(pf.getPlayerId()));
            vo.setContributionValue(contribution);
            result.add(vo);
            previousContribution = contribution;
        }
        return result;
    }

    @Override
    public List<FactionSkillVO> getFactionSkills(String factionKey) {
        QueryWrapper<FactionSkill> qw = new QueryWrapper<>();
        qw.eq("faction_key", factionKey).eq("enabled", 1).orderByAsc("unlock_level");
        return skillMapper.selectList(qw).stream()
                .map(this::toFactionSkillVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<FactionSkillVO> getPlayerFactionSkills(Long playerId, Long userId) {
        validatePlayerOwnership(playerId, userId);
        PlayerFaction playerFaction = playerFactionMapper.selectOne(new QueryWrapper<PlayerFaction>()
                .eq("player_id", playerId).eq("status", "active"));
        if (playerFaction == null) return Collections.emptyList();

        Player player = playerMapper.selectById(playerId);
        ConstellationFaction faction = getFactionByKey(playerFaction.getFactionKey());
        Set<String> learnedKeys = playerSkillMapper.selectList(new QueryWrapper<PlayerFactionSkill>()
                        .eq("player_id", playerId).eq("faction_key", playerFaction.getFactionKey()))
                .stream().map(PlayerFactionSkill::getSkillKey).collect(Collectors.toSet());

        QueryWrapper<FactionSkill> skillQuery = new QueryWrapper<>();
        skillQuery.eq("faction_key", playerFaction.getFactionKey()).eq("enabled", 1).orderByAsc("unlock_level");
        List<FactionSkillVO> result = new ArrayList<>();
        for (FactionSkill skill : skillMapper.selectList(skillQuery)) {
            FactionSkillVO vo = toFactionSkillVO(skill);
            boolean learned = learnedKeys.contains(skill.getSkillKey());
            vo.setLearned(learned);
            vo.setCanLearn(!learned && canLearnFactionSkill(playerFaction, faction, skill, player));
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional
    public FactionSkillVO learnFactionSkill(Long playerId, String skillKey, Long userId, String requestKey) {
        Player player = lockOwnedPlayerForUpdate(playerId, userId);
        if (skillKey == null || skillKey.isBlank())
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "阵营技能标识不能为空");

        Map<String, Object> requestPayload = Map.of("playerId", playerId, "skillKey", skillKey);
        FactionSkillVO replay = actionReceiptService.findReplay(
                playerId, "faction.skill-learn", requestKey, requestPayload, FactionSkillVO.class);
        if (replay != null) return replay;

        PlayerFaction playerFaction = playerFactionMapper.selectActiveByPlayerIdForUpdate(playerId);
        if (playerFaction == null)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "请先加入阵营");

        FactionSkill skill = skillMapper.selectOne(new QueryWrapper<FactionSkill>()
                .eq("faction_key", playerFaction.getFactionKey())
                .eq("skill_key", skillKey).eq("enabled", 1));
        if (skill == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "阵营技能不存在或不属于当前阵营");
        if (playerSkillMapper.selectCount(new QueryWrapper<PlayerFactionSkill>()
                .eq("player_id", playerId).eq("skill_key", skillKey)) > 0)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "已经学习过该阵营技能");

        ConstellationFaction faction = getFactionByKey(playerFaction.getFactionKey());
        Map<String, Object> cost = parseJsonMap(skill.getCostJson());
        long reputationCost = toLong(cost.get("reputation"), 0);
        long coinsCost = toLong(cost.get("coins"), 0);
        if (reputationCost < 0 || coinsCost < 0)
            throw new IllegalStateException("阵营技能资源消耗配置不能为负数");
        if (!canLearnFactionSkill(playerFaction, faction, skill, player))
            throw new BusinessException(ErrorCode.RESOURCE_NOT_ENOUGH, "不满足阵营技能的等级、声望或星币要求");

        long coinsBefore = player.getCoins() != null ? player.getCoins() : 0L;
        long coinsAfter = Math.subtractExact(coinsBefore, coinsCost);
        long reputationBefore = playerFaction.getReputation() != null ? playerFaction.getReputation() : 0L;
        long reputationAfter = Math.subtractExact(reputationBefore, reputationCost);

        LocalDateTime now = LocalDateTime.now();
        PlayerFactionSkill learned = new PlayerFactionSkill();
        learned.setPlayerId(playerId);
        learned.setFactionKey(playerFaction.getFactionKey());
        learned.setSkillKey(skillKey);
        learned.setLevel(1);
        learned.setLearnedAt(now);
        learned.setCreatedAt(now);
        learned.setUpdatedAt(now);
        if (playerSkillMapper.insert(learned) <= 0)
            throw new IllegalStateException("保存阵营技能学习记录失败");

        if (reputationCost > 0) {
            playerFaction.setReputation(reputationAfter);
            playerFaction.setUpdatedAt(now);
            if (playerFactionMapper.updateById(playerFaction) <= 0)
                throw new IllegalStateException("保存阵营声望扣除失败");
        }
        if (coinsCost > 0) {
            player.setCoins(Math.toIntExact(coinsAfter));
            if (playerMapper.updateById(player) <= 0)
                throw new IllegalStateException("保存阵营技能星币扣除失败");
        }

        String transactionGroupId = "fskill:" + playerId + ":" + UUID.nameUUIDFromBytes(
                (playerId + ":" + skillKey + ":" + requestKey)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Map<String, Object> resourceMetadata = new LinkedHashMap<>();
        resourceMetadata.put("factionKey", playerFaction.getFactionKey());
        resourceMetadata.put("skillKey", skillKey);
        resourceMetadata.put("coinsCost", coinsCost);
        resourceMetadata.put("reputationCost", reputationCost);
        resourceMetadata.put("coinsBefore", coinsBefore);
        resourceMetadata.put("coinsAfter", coinsAfter);
        resourceMetadata.put("reputationBefore", reputationBefore);
        resourceMetadata.put("reputationAfter", reputationAfter);
        if (coinsCost > 0) {
            resourceTransactionService.record(new PlayerResourceTransactionService.Change(
                    playerId, "currency", "coins", -coinsCost, coinsBefore, coinsAfter,
                    "faction_skill_learn", skillKey, transactionGroupId,
                    transactionGroupId + ":coins", resourceMetadata));
        }
        if (reputationCost > 0) {
            resourceTransactionService.record(new PlayerResourceTransactionService.Change(
                    playerId, "reputation", playerFaction.getFactionKey(),
                    -reputationCost, reputationBefore, reputationAfter,
                    "faction_skill_learn", skillKey, transactionGroupId,
                    transactionGroupId + ":reputation", resourceMetadata));
        }
        writeLog(playerId, "faction_skill", "学习了阵营技能「" + skill.getName()
                + "」，消耗 " + coinsCost + " 星币与 " + reputationCost + " 声望", resourceMetadata);

        FactionSkillVO vo = toFactionSkillVO(skill);
        vo.setLearned(true);
        vo.setCanLearn(false);
        actionReceiptService.record(playerId, "faction.skill-learn", requestKey, requestPayload, vo);
        return vo;
    }

    private boolean canLearnFactionSkill(PlayerFaction playerFaction, ConstellationFaction faction,
                                         FactionSkill skill, Player player) {
        int factionLevel = faction != null && faction.getLevel() != null ? faction.getLevel() : 1;
        if (factionLevel < (skill.getUnlockLevel() != null ? skill.getUnlockLevel() : 1)) return false;
        Map<String, Object> cost = parseJsonMap(skill.getCostJson());
        long reputation = playerFaction.getReputation() != null ? playerFaction.getReputation() : 0;
        long reputationCost = toLong(cost.get("reputation"), 0);
        if (reputation < reputationCost) return false;
        long coinsCost = toLong(cost.get("coins"), 0);
        return player == null || (player.getCoins() != null && player.getCoins() >= coinsCost);
    }

    @Override
    public List<FactionWarVO> getFactionWars() {
        QueryWrapper<FactionWar> qw = new QueryWrapper<>();
        qw.orderByDesc("created_at").last("LIMIT 20");
        return warMapper.selectList(qw).stream()
                .map(this::toFactionWarVO)
                .collect(Collectors.toList());
    }

    @Override
    public FactionSummaryVO getFactionSummary(Long playerId) {
        FactionSummaryVO vo = new FactionSummaryVO();
        vo.setJoined(false);

        if (playerId == null) return vo;

        QueryWrapper<PlayerFaction> pq = new QueryWrapper<>();
        pq.eq("player_id", playerId).eq("status", "active");
        PlayerFaction pf = playerFactionMapper.selectOne(pq);
        if (pf == null) return vo;

        ConstellationFaction faction = factionMapper.selectOne(
                new QueryWrapper<ConstellationFaction>().eq("faction_key", pf.getFactionKey()));

        vo.setJoined(true);
        vo.setFactionKey(pf.getFactionKey());
        vo.setFactionName(faction != null ? faction.getName() : "");
        vo.setRole(pf.getRole());
        vo.setReputation(pf.getReputation());
        vo.setContributionTotal(pf.getContributionTotal());
        vo.setFactionLevel(faction != null ? faction.getLevel() : 1);

        // faction rank
        QueryWrapper<ConstellationFaction> rq = new QueryWrapper<>();
        rq.eq("enabled", 1).orderByDesc("total_contribution");
        List<ConstellationFaction> all = factionMapper.selectList(rq);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getFactionKey().equals(pf.getFactionKey())) {
                vo.setFactionRank(i + 1);
                break;
            }
        }

        // 计算阵营加成
        Map<String, Object> buffInfo = getFactionBuff(playerId);
        vo.setFactionBuff((Integer) buffInfo.getOrDefault("buffAtk", 0));
        vo.setDailyContribution((Long) buffInfo.getOrDefault("dailyContribution", 0L));
        vo.setBuffDescription((String) buffInfo.getOrDefault("description", ""));

        return vo;
    }

    @Override
    public Map<String, Object> getFactionBuff(Long playerId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("buffAtk", 0);
        result.put("dailyContribution", 0L);
        result.put("baseAtk", 0);
        result.put("multiplier", 1.0);
        result.put("skillBonuses", new LinkedHashMap<>());
        result.put("description", "未加入阵营，无加成");

        if (playerId == null) return result;

        QueryWrapper<PlayerFaction> pq = new QueryWrapper<>();
        pq.eq("player_id", playerId).eq("status", "active");
        PlayerFaction pf = playerFactionMapper.selectOne(pq);
        if (pf == null) return result;

        ConstellationFaction faction = getFactionByKey(pf.getFactionKey());
        Map<String, Object> buffs = parseJsonMap(faction.getBuffsJson());
        int baseAtk = 0;
        if (buffs.get("atk") instanceof Number) {
            baseAtk = ((Number) buffs.get("atk")).intValue();
        }
        result.put("baseAtk", baseAtk);

        // 读取上次每日结算的 metadata
        Map<String, Object> meta = parseJsonMap(pf.getMetadataJson());
        long dailyContribution = 0;
        if (meta.get("dailyContribution") instanceof Number) {
            dailyContribution = ((Number) meta.get("dailyContribution")).longValue();
        }

        // 如果还没结算过，从贡献表实时计算
        if (dailyContribution == 0) {
            dailyContribution = getTodayContribution(playerId, pf.getFactionKey());
        }

        // 加成倍率：每 500 贡献 x1.0 倍，最高 5 倍
        double multiplier = 1.0 + Math.min((double) dailyContribution / 500.0, 4.0);
        int buffAtk = (int) Math.round(baseAtk * multiplier);
        Map<String, Object> skillBonuses = getLearnedFactionSkillBonuses(playerId, pf.getFactionKey());

        result.put("dailyContribution", dailyContribution);
        result.put("multiplier", Math.round(multiplier * 100.0) / 100.0);
        result.put("buffAtk", buffAtk);
        result.put("skillBonuses", skillBonuses);
        result.put("description", "攻击 +" + buffAtk + "（基础 " + baseAtk
                + " × " + String.format("%.2f", multiplier) + " 倍，今日贡献 " + dailyContribution + "）");

        return result;
    }

    private Map<String, Object> getLearnedFactionSkillBonuses(Long playerId, String factionKey) {
        Map<String, Object> bonuses = new LinkedHashMap<>();
        List<PlayerFactionSkill> learned = playerSkillMapper.selectList(new QueryWrapper<PlayerFactionSkill>()
                .eq("player_id", playerId).eq("faction_key", factionKey));
        for (PlayerFactionSkill learnedSkill : learned) {
            FactionSkill definition = skillMapper.selectOne(new QueryWrapper<FactionSkill>()
                    .eq("faction_key", factionKey).eq("skill_key", learnedSkill.getSkillKey()).eq("enabled", 1));
            if (definition == null) continue;
            for (Map.Entry<String, Object> entry : parseJsonMap(definition.getEffectsJson()).entrySet()) {
                if (!(entry.getValue() instanceof Number number)) continue;
                double current = toDouble(bonuses.get(entry.getKey()), 0);
                bonuses.put(entry.getKey(), current + number.doubleValue());
            }
        }
        return bonuses;
    }

    @Override
    @Transactional
    public Map<String, Object> settleFactionDaily() {
        Map<String, Object> summary = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        summary.put("settledAt", now.toString());
        summary.put("settledFactions", 0);
        summary.put("settledPlayers", 0);

        // 今天已经结算过则跳过
        LocalDateTime todayStart = now.withHour(0).withMinute(0).withSecond(0).withNano(0);
        QueryWrapper<PlayerFaction> checkQw = new QueryWrapper<>();
        checkQw.eq("status", "active")
               .isNotNull("metadata_json")
               .ne("metadata_json", "")
               .ne("metadata_json", "{}")
               .last("LIMIT 1");
        PlayerFaction anyActive = playerFactionMapper.selectOne(checkQw);
        if (anyActive != null) {
            Map<String, Object> existingMeta = parseJsonMap(anyActive.getMetadataJson());
            Object settledAt = existingMeta.get("settledAt");
            if (settledAt != null) {
                try {
                    LocalDateTime settledTime = LocalDateTime.parse(settledAt.toString());
                    if (!settledTime.isBefore(todayStart)) {
                        summary.put("message", "今日已结算，跳过");
                        log.info("阵营每日结算: 今日已结算，跳过");
                        return summary;
                    }
                } catch (Exception ignored) {}
            }
        }

        // 获取所有活跃阵营
        QueryWrapper<ConstellationFaction> fq = new QueryWrapper<>();
        fq.eq("enabled", 1);
        List<ConstellationFaction> factions = factionMapper.selectList(fq);

        int totalPlayers = 0;

        for (ConstellationFaction faction : factions) {
            Map<String, Object> buffs = parseJsonMap(faction.getBuffsJson());
            int baseAtk = 0;
            if (buffs.get("atk") instanceof Number) {
                baseAtk = ((Number) buffs.get("atk")).intValue();
            }

            // 获取该阵营所有活跃成员
            QueryWrapper<PlayerFaction> pq = new QueryWrapper<>();
            pq.eq("faction_key", faction.getFactionKey()).eq("status", "active");
            List<PlayerFaction> members = playerFactionMapper.selectList(pq);

            for (PlayerFaction pf : members) {
                long dailyContrib = getTodayContribution(pf.getPlayerId(), pf.getFactionKey());
                double multiplier = 1.0 + Math.min((double) dailyContrib / 500.0, 4.0);
                int buffAtk = (int) Math.round(baseAtk * multiplier);

                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("dailyContribution", dailyContrib);
                meta.put("buffMultiplier", Math.round(multiplier * 100.0) / 100.0);
                meta.put("buffAtk", buffAtk);
                meta.put("settledAt", now.toString());

                try {
                    pf.setMetadataJson(objectMapper.writeValueAsString(meta));
                } catch (Exception ignored) {
                    pf.setMetadataJson("{}");
                }
                pf.setUpdatedAt(now);
                playerFactionMapper.updateById(pf);
                totalPlayers++;
            }
        }

        summary.put("settledFactions", factions.size());
        summary.put("settledPlayers", totalPlayers);
        log.info("阵营每日结算完成: {} 个阵营, {} 个玩家", factions.size(), totalPlayers);
        return summary;
    }

    private long getTodayContribution(Long playerId, String factionKey) {
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        QueryWrapper<FactionContribution> cq = new QueryWrapper<>();
        cq.eq("player_id", playerId)
          .eq("faction_key", factionKey)
          .ge("created_at", todayStart);
        // 使用 MyBatis-Plus 的 selectList + 求和处理
        List<FactionContribution> list = contributionMapper.selectList(cq);
        return list.stream().mapToLong(FactionContribution::getValue).sum();
    }

    private ConstellationFaction getFactionByKey(String factionKey) {
        QueryWrapper<ConstellationFaction> qw = new QueryWrapper<>();
        qw.eq("faction_key", factionKey);
        ConstellationFaction f = factionMapper.selectOne(qw);
        if (f == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "阵营不存在: " + factionKey);
        return f;
    }

    private FactionVO buildFactionVO(ConstellationFaction f, Long playerId) {
        FactionVO vo = new FactionVO();
        vo.setId(f.getId());
        vo.setFactionKey(f.getFactionKey());
        vo.setName(f.getName());
        vo.setConstellationName(f.getConstellationName());
        vo.setDescription(f.getDescription());
        vo.setAlignment(f.getAlignment());
        vo.setIdeology(f.getIdeology());
        vo.setLevel(f.getLevel());
        vo.setExp(f.getExp());
        vo.setMemberCount(f.getMemberCount());
        vo.setTotalContribution(f.getTotalContribution());
        vo.setBuffs(parseJsonMap(f.getBuffsJson()));
        vo.setUnlocks(parseJsonMap(f.getUnlocksJson()));
        vo.setMetadata(parseJsonMap(f.getMetadataJson()));
        vo.setCreatedAt(f.getCreatedAt() != null ? f.getCreatedAt().toString() : null);
        vo.setUpdatedAt(f.getUpdatedAt() != null ? f.getUpdatedAt().toString() : null);

        if (playerId != null) {
            QueryWrapper<PlayerFaction> pq = new QueryWrapper<>();
            pq.eq("player_id", playerId).eq("faction_key", f.getFactionKey()).eq("status", "active");
            PlayerFaction pf = playerFactionMapper.selectOne(pq);
            if (pf != null) {
                vo.setJoined(true);
                vo.setMyContribution(pf.getContributionTotal());
            } else {
                vo.setJoined(false);
                vo.setMyContribution(0L);
            }
        }

        return vo;
    }

    int calculateFactionLevel(long exp) {
        if (exp <= 0) return 1;
        int level = (int) Math.floor(Math.sqrt((double) exp / 100.0)) + 1;
        return Math.min(level, 10);
    }

    private PlayerFactionVO toPlayerFactionVO(PlayerFaction pf) {
        ConstellationFaction faction = factionMapper.selectOne(
                new QueryWrapper<ConstellationFaction>().eq("faction_key", pf.getFactionKey()));

        PlayerFactionVO vo = new PlayerFactionVO();
        vo.setId(pf.getId());
        vo.setPlayerId(pf.getPlayerId());
        vo.setFactionKey(pf.getFactionKey());
        vo.setFactionName(faction != null ? faction.getName() : pf.getFactionKey());
        vo.setRole(pf.getRole());
        vo.setReputation(pf.getReputation());
        vo.setContributionTotal(pf.getContributionTotal());
        vo.setJoinedAt(pf.getJoinedAt() != null ? pf.getJoinedAt().toString() : null);
        vo.setLeftAt(pf.getLeftAt() != null ? pf.getLeftAt().toString() : null);
        vo.setStatus(pf.getStatus());

        // 前端兼容字段
        if (faction != null) {
            vo.setFactionLevel(faction.getLevel());
            vo.setActiveMembers(faction.getMemberCount());
            vo.setTotalContributionScore(faction.getTotalContribution());
            // factionSkills as list of skill keys
            List<FactionSkill> skills = skillMapper.selectList(
                    new QueryWrapper<FactionSkill>().eq("faction_key", pf.getFactionKey()));
            if (skills != null) {
                vo.setFactionSkills(skills.stream().map(FactionSkill::getSkillKey).collect(Collectors.toList()));
            }
        }
        return vo;
    }

    private FactionSkillVO toFactionSkillVO(FactionSkill s) {
        FactionSkillVO vo = new FactionSkillVO();
        vo.setId(s.getId());
        vo.setFactionKey(s.getFactionKey());
        vo.setSkillKey(s.getSkillKey());
        vo.setName(s.getName());
        vo.setDescription(s.getDescription());
        vo.setUnlockLevel(s.getUnlockLevel());
        vo.setEffects(parseJsonMap(s.getEffectsJson()));
        vo.setCost(parseJsonMap(s.getCostJson()));
        vo.setEnabled(s.getEnabled());
        return vo;
    }

    private FactionWarVO toFactionWarVO(FactionWar w) {
        FactionWarVO vo = new FactionWarVO();
        vo.setId(w.getId());
        vo.setWarNo(w.getWarNo());
        vo.setAttackerFactionKey(w.getAttackerFactionKey());
        vo.setDefenderFactionKey(w.getDefenderFactionKey());
        vo.setStatus(w.getStatus());
        vo.setStartAt(w.getStartAt() != null ? w.getStartAt().toString() : null);
        vo.setEndAt(w.getEndAt() != null ? w.getEndAt().toString() : null);
        vo.setAttackerScore(w.getAttackerScore());
        vo.setDefenderScore(w.getDefenderScore());
        vo.setWinnerFactionKey(w.getWinnerFactionKey());
        vo.setRewards(parseJsonMap(w.getRewardsJson()));
        vo.setMetadata(parseJsonMap(w.getMetadataJson()));
        vo.setCreatedAt(w.getCreatedAt() != null ? w.getCreatedAt().toString() : null);
        return vo;
    }

    @Override
    public List<Map<String, Object>> getFactionMembers(String factionKey, int limit) {
        QueryWrapper<PlayerFaction> pq = new QueryWrapper<>();
        pq.eq("faction_key", factionKey).eq("status", "active")
          .orderByDesc("contribution_total").last("LIMIT " + Math.min(limit, 100));
        List<PlayerFaction> list = playerFactionMapper.selectList(pq);
        List<Map<String, Object>> members = new java.util.ArrayList<>();
        for (PlayerFaction pf : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("playerId", pf.getPlayerId());
            m.put("playerName", getPlayerName(pf.getPlayerId()));
            m.put("factionKey", pf.getFactionKey());
            m.put("role", pf.getRole());
            m.put("contributionTotal", pf.getContributionTotal());
            m.put("reputation", pf.getReputation());
            m.put("joinedAt", pf.getJoinedAt() != null ? pf.getJoinedAt().toString() : null);
            members.add(m);
        }
        return members;
    }

    private void validatePlayerOwnership(Long playerId, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null) throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND);
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");
    }

    private Player lockOwnedPlayerForUpdate(Long playerId, Long userId) {
        Player player = playerMapper.selectByIdForUpdate(playerId);
        if (player == null) throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND);
        if (userId != null && !Objects.equals(player.getUserId(), userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");
        return player;
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

    private void writeLog(Long playerId, String type, String message, Map<String, Object> metadata) {
        PlayerLog log = new PlayerLog();
        log.setPlayerId(playerId);
        log.setType(type);
        log.setMessage(message);
        if (metadata != null && !metadata.isEmpty()) {
            try {
                log.setMetadataJson(objectMapper.writeValueAsString(metadata));
            } catch (Exception exception) {
                throw new IllegalArgumentException("阵营技能消耗日志无法序列化", exception);
            }
        }
        if (playerLogMapper.insert(log) <= 0)
            throw new IllegalStateException("保存阵营技能历程失败");
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

    private long toLong(Object value, long defaultValue) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? defaultValue : Long.parseLong(value.toString()); }
        catch (NumberFormatException ignored) { return defaultValue; }
    }

    private double toDouble(Object value, double defaultValue) {
        if (value instanceof Number number) return number.doubleValue();
        try { return value == null ? defaultValue : Double.parseDouble(value.toString()); }
        catch (NumberFormatException ignored) { return defaultValue; }
    }
}
