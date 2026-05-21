package com.huazhenghai.readergame.vo;

import java.util.List;
import java.util.Map;

/**
 * 游戏启动引导视图对象.
 */
public class GameBootstrapVO {

    private UserVO user;
    private PlayerVO player;
    private String serverTime;
    private List<LogEntry> recentLogs;
    private RestStateVO restState;

    // Phase 3: 成长系统
    private ChapterProgressVO chapterProgress;
    private AvatarRankVO avatarRankInfo;
    private List<PlayerTitleVO> titles;
    private Map<String, Object> starstreamTier;

    // Phase 4: 物品背包
    private List<Map<String, Object>> inventorySummary;

    // Phase 5A: 装备
    private Map<String, Object> equipmentSummary;

    // Phase 5B: 技能
    private SkillSummaryVO skillSummary;

    // Phase 5C: 战斗
    private Map<String, Object> combatSummary;

    // Phase 6: PK + 排行榜
    private Map<String, Object> pkSummary;

    // Phase 7: 星流放送 + 世界线
    private Map<String, Object> broadcastSummary;
    private Map<String, Object> worldlineSummary;

    // Phase 8A: 聊天 + 在线
    private Map<String, Object> chatSummary;
    private Map<String, Object> onlineSummary;

    // Phase 8B: 好友
    private Map<String, Object> friendSummary;

    // Phase 8C: 交易市场
    private Map<String, Object> tradeSummary;

    // Phase 8D: 组队
    private Map<String, Object> partySummary;

    // Phase 8F: 世界Boss
    private Map<String, Object> worldBossSummary;

    // Phase 8G: 阵营
    private Map<String, Object> factionSummary;

    // Phase 8H: 任务
    private QuestSummaryVO questSummary;

    public GameBootstrapVO() {
    }

    public UserVO getUser() { return user; }
    public void setUser(UserVO user) { this.user = user; }
    public PlayerVO getPlayer() { return player; }
    public void setPlayer(PlayerVO player) { this.player = player; }
    public String getServerTime() { return serverTime; }
    public void setServerTime(String serverTime) { this.serverTime = serverTime; }
    public List<LogEntry> getRecentLogs() { return recentLogs; }
    public void setRecentLogs(List<LogEntry> recentLogs) { this.recentLogs = recentLogs; }
    public RestStateVO getRestState() { return restState; }
    public void setRestState(RestStateVO restState) { this.restState = restState; }

    public ChapterProgressVO getChapterProgress() { return chapterProgress; }
    public void setChapterProgress(ChapterProgressVO chapterProgress) { this.chapterProgress = chapterProgress; }
    public AvatarRankVO getAvatarRankInfo() { return avatarRankInfo; }
    public void setAvatarRankInfo(AvatarRankVO avatarRankInfo) { this.avatarRankInfo = avatarRankInfo; }
    public List<PlayerTitleVO> getTitles() { return titles; }
    public void setTitles(List<PlayerTitleVO> titles) { this.titles = titles; }
    public Map<String, Object> getStarstreamTier() { return starstreamTier; }
    public void setStarstreamTier(Map<String, Object> starstreamTier) { this.starstreamTier = starstreamTier; }
    public List<Map<String, Object>> getInventorySummary() { return inventorySummary; }
    public void setInventorySummary(List<Map<String, Object>> inventorySummary) { this.inventorySummary = inventorySummary; }
    public Map<String, Object> getEquipmentSummary() { return equipmentSummary; }
    public void setEquipmentSummary(Map<String, Object> equipmentSummary) { this.equipmentSummary = equipmentSummary; }
    public SkillSummaryVO getSkillSummary() { return skillSummary; }
    public void setSkillSummary(SkillSummaryVO skillSummary) { this.skillSummary = skillSummary; }
    public Map<String, Object> getCombatSummary() { return combatSummary; }
    public void setCombatSummary(Map<String, Object> combatSummary) { this.combatSummary = combatSummary; }
    public Map<String, Object> getPkSummary() { return pkSummary; }
    public void setPkSummary(Map<String, Object> pkSummary) { this.pkSummary = pkSummary; }
    public Map<String, Object> getBroadcastSummary() { return broadcastSummary; }
    public void setBroadcastSummary(Map<String, Object> broadcastSummary) { this.broadcastSummary = broadcastSummary; }
    public Map<String, Object> getWorldlineSummary() { return worldlineSummary; }
    public void setWorldlineSummary(Map<String, Object> worldlineSummary) { this.worldlineSummary = worldlineSummary; }
    public Map<String, Object> getChatSummary() { return chatSummary; }
    public void setChatSummary(Map<String, Object> chatSummary) { this.chatSummary = chatSummary; }
    public Map<String, Object> getOnlineSummary() { return onlineSummary; }
    public void setOnlineSummary(Map<String, Object> onlineSummary) { this.onlineSummary = onlineSummary; }
    public Map<String, Object> getFriendSummary() { return friendSummary; }
    public void setFriendSummary(Map<String, Object> friendSummary) { this.friendSummary = friendSummary; }
    public Map<String, Object> getTradeSummary() { return tradeSummary; }
    public void setTradeSummary(Map<String, Object> tradeSummary) { this.tradeSummary = tradeSummary; }
    public Map<String, Object> getPartySummary() { return partySummary; }
    public void setPartySummary(Map<String, Object> partySummary) { this.partySummary = partySummary; }
    public Map<String, Object> getWorldBossSummary() { return worldBossSummary; }
    public void setWorldBossSummary(Map<String, Object> worldBossSummary) { this.worldBossSummary = worldBossSummary; }
    public Map<String, Object> getFactionSummary() { return factionSummary; }
    public void setFactionSummary(Map<String, Object> factionSummary) { this.factionSummary = factionSummary; }
    public QuestSummaryVO getQuestSummary() { return questSummary; }
    public void setQuestSummary(QuestSummaryVO questSummary) { this.questSummary = questSummary; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UserVO user;
        private PlayerVO player;
        private String serverTime;
        private List<LogEntry> recentLogs;
        private RestStateVO restState;
        private ChapterProgressVO chapterProgress;
        private AvatarRankVO avatarRankInfo;
        private List<PlayerTitleVO> titles;
        private Map<String, Object> starstreamTier;
        private List<Map<String, Object>> inventorySummary;
        private Map<String, Object> equipmentSummary;
        private SkillSummaryVO skillSummary;
        private Map<String, Object> combatSummary;
        private Map<String, Object> pkSummary;
        private Map<String, Object> broadcastSummary;
        private Map<String, Object> worldlineSummary;
        private Map<String, Object> chatSummary;
        private Map<String, Object> onlineSummary;
        private Map<String, Object> friendSummary;
        private Map<String, Object> tradeSummary;
        private Map<String, Object> partySummary;
        private Map<String, Object> worldBossSummary;
        private Map<String, Object> factionSummary;
        private QuestSummaryVO questSummary;

        public Builder user(UserVO user) { this.user = user; return this; }
        public Builder player(PlayerVO player) { this.player = player; return this; }
        public Builder serverTime(String serverTime) { this.serverTime = serverTime; return this; }
        public Builder recentLogs(List<LogEntry> recentLogs) { this.recentLogs = recentLogs; return this; }
        public Builder restState(RestStateVO restState) { this.restState = restState; return this; }
        public Builder chapterProgress(ChapterProgressVO chapterProgress) { this.chapterProgress = chapterProgress; return this; }
        public Builder avatarRankInfo(AvatarRankVO avatarRankInfo) { this.avatarRankInfo = avatarRankInfo; return this; }
        public Builder titles(List<PlayerTitleVO> titles) { this.titles = titles; return this; }
        public Builder starstreamTier(Map<String, Object> starstreamTier) { this.starstreamTier = starstreamTier; return this; }
        public Builder inventorySummary(List<Map<String, Object>> inventorySummary) { this.inventorySummary = inventorySummary; return this; }
        public Builder equipmentSummary(Map<String, Object> equipmentSummary) { this.equipmentSummary = equipmentSummary; return this; }
        public Builder skillSummary(SkillSummaryVO skillSummary) { this.skillSummary = skillSummary; return this; }
        public Builder combatSummary(Map<String, Object> combatSummary) { this.combatSummary = combatSummary; return this; }
        public Builder pkSummary(Map<String, Object> pkSummary) { this.pkSummary = pkSummary; return this; }
        public Builder broadcastSummary(Map<String, Object> broadcastSummary) { this.broadcastSummary = broadcastSummary; return this; }
        public Builder worldlineSummary(Map<String, Object> worldlineSummary) { this.worldlineSummary = worldlineSummary; return this; }
        public Builder chatSummary(Map<String, Object> chatSummary) { this.chatSummary = chatSummary; return this; }
        public Builder onlineSummary(Map<String, Object> onlineSummary) { this.onlineSummary = onlineSummary; return this; }
        public Builder friendSummary(Map<String, Object> friendSummary) { this.friendSummary = friendSummary; return this; }
        public Builder tradeSummary(Map<String, Object> tradeSummary) { this.tradeSummary = tradeSummary; return this; }
        public Builder partySummary(Map<String, Object> partySummary) { this.partySummary = partySummary; return this; }
        public Builder worldBossSummary(Map<String, Object> worldBossSummary) { this.worldBossSummary = worldBossSummary; return this; }
        public Builder factionSummary(Map<String, Object> factionSummary) { this.factionSummary = factionSummary; return this; }
        public Builder questSummary(QuestSummaryVO questSummary) { this.questSummary = questSummary; return this; }

        public GameBootstrapVO build() {
            GameBootstrapVO vo = new GameBootstrapVO();
            vo.user = user;
            vo.player = player;
            vo.serverTime = serverTime;
            vo.recentLogs = recentLogs;
            vo.restState = restState;
            vo.chapterProgress = chapterProgress;
            vo.avatarRankInfo = avatarRankInfo;
            vo.titles = titles;
            vo.starstreamTier = starstreamTier;
            vo.inventorySummary = inventorySummary;
            vo.equipmentSummary = equipmentSummary;
            vo.skillSummary = skillSummary;
            vo.combatSummary = combatSummary;
            vo.pkSummary = pkSummary;
            vo.broadcastSummary = broadcastSummary;
            vo.worldlineSummary = worldlineSummary;
            vo.chatSummary = chatSummary;
            vo.onlineSummary = onlineSummary;
            vo.friendSummary = friendSummary;
            vo.tradeSummary = tradeSummary;
            vo.partySummary = partySummary;
            vo.worldBossSummary = worldBossSummary;
            vo.factionSummary = factionSummary;
            vo.questSummary = questSummary;
            return vo;
        }
    }
}
