package com.huazhenghai.readergame.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 玩家视图对象 (PlayerVO).
 * <p>
 * 包含解析后的 stats / stageProgress 以及最近日志.
 * </p>
 */
public class PlayerVO {

    private Long id;
    @JsonProperty("player_name")
    private String playerName;
    @JsonProperty("current_chapter")
    private String currentChapter;
    private Integer coins;
    @JsonProperty("story_fragments")
    private Integer storyFragments;
    @JsonProperty("user_id")
    private Long userId;
    private Map<String, Object> stats;
    @JsonProperty("stage_progress")
    private Map<String, Object> stageProgress;
    @JsonProperty("current_main_chapter")
    private String currentMainChapter;
    @JsonProperty("current_location")
    private String currentLocation;
    @JsonProperty("logs")
    private List<LogEntry> recentLogs;
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    public PlayerVO() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public String getCurrentChapter() { return currentChapter; }
    public void setCurrentChapter(String currentChapter) { this.currentChapter = currentChapter; }
    public Integer getCoins() { return coins; }
    public void setCoins(Integer coins) { this.coins = coins; }
    public Integer getStoryFragments() { return storyFragments; }
    public void setStoryFragments(Integer storyFragments) { this.storyFragments = storyFragments; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Map<String, Object> getStats() { return stats; }
    public void setStats(Map<String, Object> stats) { this.stats = stats; }
    public Map<String, Object> getStageProgress() { return stageProgress; }
    public void setStageProgress(Map<String, Object> stageProgress) { this.stageProgress = stageProgress; }
    public String getCurrentMainChapter() { return currentMainChapter; }
    public void setCurrentMainChapter(String currentMainChapter) { this.currentMainChapter = currentMainChapter; }
    public String getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(String currentLocation) { this.currentLocation = currentLocation; }
    public List<LogEntry> getRecentLogs() { return recentLogs; }
    public void setRecentLogs(List<LogEntry> recentLogs) { this.recentLogs = recentLogs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    // ─── 手工 Builder ───

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private String playerName;
        private String currentChapter;
        private Integer coins;
        private Integer storyFragments;
        private Long userId;
        private Map<String, Object> stats;
        private Map<String, Object> stageProgress;
        private String currentMainChapter;
        private String currentLocation;
        private List<LogEntry> recentLogs;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder playerName(String playerName) { this.playerName = playerName; return this; }
        public Builder currentChapter(String currentChapter) { this.currentChapter = currentChapter; return this; }
        public Builder coins(Integer coins) { this.coins = coins; return this; }
        public Builder storyFragments(Integer storyFragments) { this.storyFragments = storyFragments; return this; }
        public Builder userId(Long userId) { this.userId = userId; return this; }
        public Builder stats(Map<String, Object> stats) { this.stats = stats; return this; }
        public Builder stageProgress(Map<String, Object> stageProgress) { this.stageProgress = stageProgress; return this; }
        public Builder currentMainChapter(String currentMainChapter) { this.currentMainChapter = currentMainChapter; return this; }
        public Builder currentLocation(String currentLocation) { this.currentLocation = currentLocation; return this; }
        public Builder recentLogs(List<LogEntry> recentLogs) { this.recentLogs = recentLogs; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public PlayerVO build() {
            PlayerVO vo = new PlayerVO();
            vo.id = id;
            vo.playerName = playerName;
            vo.currentChapter = currentChapter;
            vo.coins = coins;
            vo.storyFragments = storyFragments;
            vo.userId = userId;
            vo.stats = stats;
            vo.stageProgress = stageProgress;
            vo.currentMainChapter = currentMainChapter;
            vo.currentLocation = currentLocation;
            vo.recentLogs = recentLogs;
            vo.createdAt = createdAt;
            vo.updatedAt = updatedAt;
            return vo;
        }
    }
}
