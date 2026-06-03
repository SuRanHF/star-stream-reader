package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.entity.HelpBounty;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.HelpBountyMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.CombatService;
import com.huazhenghai.readergame.service.HelpBountyService;
import com.huazhenghai.readergame.vo.CombatResultVO;
import com.huazhenghai.readergame.websocket.WebSocketSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class HelpBountyServiceImpl implements HelpBountyService {

    private static final Logger log = LoggerFactory.getLogger(HelpBountyServiceImpl.class);

    private final HelpBountyMapper bountyMapper;
    private final PlayerMapper playerMapper;
    private final CombatService combatService;
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public HelpBountyServiceImpl(HelpBountyMapper bountyMapper, PlayerMapper playerMapper,
                                  CombatService combatService, WebSocketSessionManager sessionManager,
                                  ObjectMapper objectMapper) {
        this.bountyMapper = bountyMapper;
        this.playerMapper = playerMapper;
        this.combatService = combatService;
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    private void resetDailyIfNeeded(Long playerId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null) return;
        String today = LocalDate.now().toString();
        if (!today.equals(player.getHelpDate())) {
            UpdateWrapper<Player> uw = new UpdateWrapper<>();
            uw.eq("id", playerId)
              .set("daily_help_count", 0)
              .set("daily_assist_count", 0)
              .set("help_date", today);
            playerMapper.update(null, uw);
        }
    }

    @Override
    public Map<String, Object> getDailyCounts(Long playerId) {
        resetDailyIfNeeded(playerId);
        Player player = playerMapper.selectById(playerId);
        if (player == null) return Map.of("helpCount", 0, "assistCount", 0, "maxHelp", 30, "maxAssist", 30);
        return Map.of(
                "helpCount", player.getDailyHelpCount() != null ? player.getDailyHelpCount() : 0,
                "assistCount", player.getDailyAssistCount() != null ? player.getDailyAssistCount() : 0,
                "maxHelp", 30,
                "maxAssist", 30);
    }

    @Override
    @Transactional
    public Map<String, Object> publishBounty(Long playerId, String monsterKey, String locationKey,
                                              String monsterName, int sharePercent, Map<String, Object> combatRewards) {
        resetDailyIfNeeded(playerId);
        Player player = playerMapper.selectById(playerId);
        if (player == null) return Map.of("error", Map.of("code", "PLAYER_NOT_FOUND", "message", "玩家不存在"));

        int helpCount = player.getDailyHelpCount() != null ? player.getDailyHelpCount() : 0;
        if (helpCount >= 30)
            return Map.of("error", Map.of("code", "DAILY_LIMIT_HELP", "message", "今日求助次数已达上限 (30次)"));

        // Cancel existing pending bounty
        UpdateWrapper<HelpBounty> cancelUw = new UpdateWrapper<>();
        cancelUw.eq("owner_id", playerId).eq("status", "pending").set("status", "cancelled");
        bountyMapper.update(null, cancelUw);

        int share = Math.max(10, Math.min(90, sharePercent));

        HelpBounty bounty = new HelpBounty();
        bounty.setOwnerId(playerId);
        bounty.setMonsterKey(monsterKey);
        bounty.setLocationKey(locationKey != null ? locationKey : "");
        bounty.setMonsterName(monsterName != null ? monsterName : "");
        bounty.setSharePercent(share);
        bounty.setBountyRewardsJson(toJson(combatRewards != null ? combatRewards : Map.of()));
        bounty.setStatus("pending");
        bounty.setCreatedAt(LocalDateTime.now());
        bountyMapper.insert(bounty);

        // Increment daily help count
        UpdateWrapper<Player> uw = new UpdateWrapper<>();
        uw.eq("id", playerId).setSql("daily_help_count = daily_help_count + 1");
        playerMapper.update(null, uw);

        // Broadcast via WebSocket
        try {
            Map<String, Object> wsMsg = new LinkedHashMap<>();
            wsMsg.put("type", "assist_invite");
            wsMsg.put("bountyId", bounty.getId());
            wsMsg.put("ownerId", playerId);
            wsMsg.put("ownerName", player.getPlayerName());
            wsMsg.put("monsterName", monsterName != null ? monsterName : monsterKey);
            wsMsg.put("locationKey", locationKey != null ? locationKey : "");
            wsMsg.put("sharePercent", share);
            sessionManager.broadcast(wsMsg);
        } catch (Exception e) {
            log.warn("WebSocket broadcast failed for bounty publish: {}", e.getMessage());
        }

        return Map.of("success", true, "data", Map.of(
                "bountyId", bounty.getId(),
                "message", "悬赏已发布到世界频道！分享比例: " + share + "%"));
    }

    @Override
    @Transactional
    public Map<String, Object> acceptBounty(Long bountyId, Long helperId) {
        resetDailyIfNeeded(helperId);

        QueryWrapper<HelpBounty> qw = new QueryWrapper<>();
        qw.eq("id", bountyId).eq("status", "pending");
        HelpBounty bounty = bountyMapper.selectOne(qw);
        if (bounty == null) return Map.of("error", Map.of("code", "BOUNTY_NOT_FOUND", "message", "悬赏不存在或已过期"));
        if (bounty.getOwnerId().equals(helperId))
            return Map.of("error", Map.of("code", "SELF_ASSIST", "message", "不能接受自己的悬赏"));

        Player helper = playerMapper.selectById(helperId);
        if (helper == null) return Map.of("error", Map.of("code", "PLAYER_NOT_FOUND", "message", "玩家不存在"));

        int assistCount = helper.getDailyAssistCount() != null ? helper.getDailyAssistCount() : 0;
        if (assistCount >= 30)
            return Map.of("error", Map.of("code", "DAILY_LIMIT_ASSIST", "message", "今日助力次数已达上限 (30次)"));

        // Check owner is online
        if (!sessionManager.isOnline(bounty.getOwnerId()))
            return Map.of("error", Map.of("code", "OWNER_OFFLINE", "message", "悬赏发布者已离线"));

        // Resolve combat — helper fights the monster
        CombatResultVO battleResult = combatService.startCombat(helperId, bounty.getMonsterKey(), helper.getUserId());

        // Mark bounty resolved
        UpdateWrapper<HelpBounty> uw = new UpdateWrapper<>();
        uw.eq("id", bountyId)
          .set("status", "resolved")
          .set("helper_id", helperId)
          .set("resolved_at", LocalDateTime.now());
        bountyMapper.update(null, uw);

        // Increment assist count
        UpdateWrapper<Player> puw = new UpdateWrapper<>();
        puw.eq("id", helperId).setSql("daily_assist_count = daily_assist_count + 1");
        playerMapper.update(null, puw);

        // Build result
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", Map.of(
                "battle", battleResult != null ? Map.of("result", battleResult.getResult()) : null,
                "sharePercent", bounty.getSharePercent(),
                "message", "悬赏已解决！"));

        // Notify owner via WS
        try {
            Map<String, Object> notify = new LinkedHashMap<>();
            notify.put("type", "assist_resolved");
            notify.put("content", helper.getPlayerName() + " 已响应你的悬赏！");
            sessionManager.sendToPlayer(bounty.getOwnerId(), notify);
        } catch (Exception e) {
            log.warn("WS notify failed for bounty accept: {}", e.getMessage());
        }

        return result;
    }

    @Override
    public List<Map<String, Object>> getPendingBounties() {
        QueryWrapper<HelpBounty> qw = new QueryWrapper<>();
        qw.eq("status", "pending").orderByDesc("created_at").last("LIMIT 20");
        List<HelpBounty> list = bountyMapper.selectList(qw);
        List<Map<String, Object>> result = new ArrayList<>();
        for (HelpBounty hb : list) {
            Player owner = playerMapper.selectById(hb.getOwnerId());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", hb.getId());
            entry.put("ownerId", hb.getOwnerId());
            entry.put("ownerName", owner != null ? owner.getPlayerName() : "?");
            entry.put("monsterKey", hb.getMonsterKey());
            entry.put("monsterName", hb.getMonsterName());
            entry.put("locationKey", hb.getLocationKey());
            entry.put("sharePercent", hb.getSharePercent());
            entry.put("status", hb.getStatus());
            entry.put("createdAt", hb.getCreatedAt() != null ? hb.getCreatedAt().toString() : null);
            result.add(entry);
        }
        return result;
    }

    @Override
    public Map<String, Object> getMyActiveBounty(Long playerId) {
        QueryWrapper<HelpBounty> qw = new QueryWrapper<>();
        qw.eq("owner_id", playerId).eq("status", "pending").orderByDesc("created_at").last("LIMIT 1");
        HelpBounty bounty = bountyMapper.selectOne(qw);
        if (bounty == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("data", null);
            return empty;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", bounty.getId());
        data.put("monsterKey", bounty.getMonsterKey());
        data.put("monsterName", bounty.getMonsterName());
        data.put("sharePercent", bounty.getSharePercent());
        data.put("status", bounty.getStatus());
        data.put("createdAt", bounty.getCreatedAt() != null ? bounty.getCreatedAt().toString() : null);
        return Map.of("data", data);
    }

    @Override
    @Transactional
    public Map<String, Object> cancelBounty(Long playerId) {
        QueryWrapper<HelpBounty> qw = new QueryWrapper<>();
        qw.eq("owner_id", playerId).eq("status", "pending");
        HelpBounty bounty = bountyMapper.selectOne(qw);
        if (bounty == null) return Map.of("error", Map.of("code", "NO_ACTIVE_BOUNTY", "message", "没有进行中的悬赏"));

        UpdateWrapper<HelpBounty> uw = new UpdateWrapper<>();
        uw.eq("id", bounty.getId()).set("status", "cancelled");
        bountyMapper.update(null, uw);
        return Map.of("success", true, "data", Map.of("message", "悬赏已取消"));
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return "{}"; }
    }
}
