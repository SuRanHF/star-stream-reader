package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("players")
public class Player {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("player_name")
    private String playerName;

    @TableField("current_main_chapter")
    private String currentMainChapter;

    @TableField("current_stage")
    private String currentStage;

    @TableField("current_chapter")
    private String currentChapter;

    @TableField("current_location")
    private String currentLocation;

    private Integer coins;

    @TableField("story_fragments")
    private Integer storyFragments;

    @TableField("scenario_proof")
    private Integer scenarioProof;

    @TableField("constellation_favor")
    private Integer constellationFavor;

    @TableField("king_token")
    private Integer kingToken;

    @TableField("abyss_mark")
    private Integer abyssMark;

    @TableField("final_page")
    private Integer finalPage;

    @TableField("stats_json")
    private String statsJson;

    @TableField("stage_progress_json")
    private String stageProgressJson;

    @TableField("story_flags_json")
    private String storyFlagsJson;

    @TableField("permanent_flags_json")
    private String permanentFlagsJson;

    @TableField("route_history_json")
    private String routeHistoryJson;

    @TableField("decision_history_json")
    private String decisionHistoryJson;

    @TableField("visited_nodes_json")
    private String visitedNodesJson;

    @TableField("activity_history_json")
    private String activityHistoryJson;

    @TableField("titles_json")
    private String titlesJson;

    @TableField("title_progress_json")
    private String titleProgressJson;

    @TableField("relationships_json")
    private String relationshipsJson;

    @TableField("sponsors_json")
    private String sponsorsJson;

    @TableField("daily_help_count")
    private Integer dailyHelpCount;

    @TableField("daily_assist_count")
    private Integer dailyAssistCount;

    @TableField("help_date")
    private String helpDate;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public String getCurrentMainChapter() { return currentMainChapter; }
    public void setCurrentMainChapter(String currentMainChapter) { this.currentMainChapter = currentMainChapter; }

    public String getCurrentStage() { return currentStage; }
    public void setCurrentStage(String currentStage) { this.currentStage = currentStage; }

    public String getCurrentChapter() { return currentChapter; }
    public void setCurrentChapter(String currentChapter) { this.currentChapter = currentChapter; }

    public String getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(String currentLocation) { this.currentLocation = currentLocation; }

    public Integer getCoins() { return coins; }
    public void setCoins(Integer coins) { this.coins = coins; }

    public Integer getStoryFragments() { return storyFragments; }
    public void setStoryFragments(Integer storyFragments) { this.storyFragments = storyFragments; }

    public Integer getScenarioProof() { return scenarioProof; }
    public void setScenarioProof(Integer scenarioProof) { this.scenarioProof = scenarioProof; }

    public Integer getConstellationFavor() { return constellationFavor; }
    public void setConstellationFavor(Integer constellationFavor) { this.constellationFavor = constellationFavor; }

    public Integer getKingToken() { return kingToken; }
    public void setKingToken(Integer kingToken) { this.kingToken = kingToken; }

    public Integer getAbyssMark() { return abyssMark; }
    public void setAbyssMark(Integer abyssMark) { this.abyssMark = abyssMark; }

    public Integer getFinalPage() { return finalPage; }
    public void setFinalPage(Integer finalPage) { this.finalPage = finalPage; }

    public String getStatsJson() { return statsJson; }
    public void setStatsJson(String statsJson) { this.statsJson = statsJson; }

    public String getStageProgressJson() { return stageProgressJson; }
    public void setStageProgressJson(String stageProgressJson) { this.stageProgressJson = stageProgressJson; }

    public String getStoryFlagsJson() { return storyFlagsJson; }
    public void setStoryFlagsJson(String storyFlagsJson) { this.storyFlagsJson = storyFlagsJson; }

    public String getPermanentFlagsJson() { return permanentFlagsJson; }
    public void setPermanentFlagsJson(String permanentFlagsJson) { this.permanentFlagsJson = permanentFlagsJson; }

    public String getRouteHistoryJson() { return routeHistoryJson; }
    public void setRouteHistoryJson(String routeHistoryJson) { this.routeHistoryJson = routeHistoryJson; }

    public String getDecisionHistoryJson() { return decisionHistoryJson; }
    public void setDecisionHistoryJson(String decisionHistoryJson) { this.decisionHistoryJson = decisionHistoryJson; }

    public String getVisitedNodesJson() { return visitedNodesJson; }
    public void setVisitedNodesJson(String visitedNodesJson) { this.visitedNodesJson = visitedNodesJson; }

    public String getActivityHistoryJson() { return activityHistoryJson; }
    public void setActivityHistoryJson(String activityHistoryJson) { this.activityHistoryJson = activityHistoryJson; }

    public String getTitlesJson() { return titlesJson; }
    public void setTitlesJson(String titlesJson) { this.titlesJson = titlesJson; }

    public String getTitleProgressJson() { return titleProgressJson; }
    public void setTitleProgressJson(String titleProgressJson) { this.titleProgressJson = titleProgressJson; }

    public String getRelationshipsJson() { return relationshipsJson; }
    public void setRelationshipsJson(String relationshipsJson) { this.relationshipsJson = relationshipsJson; }

    public String getSponsorsJson() { return sponsorsJson; }
    public void setSponsorsJson(String sponsorsJson) { this.sponsorsJson = sponsorsJson; }

    public Integer getDailyHelpCount() { return dailyHelpCount; }
    public void setDailyHelpCount(Integer dailyHelpCount) { this.dailyHelpCount = dailyHelpCount; }

    public Integer getDailyAssistCount() { return dailyAssistCount; }
    public void setDailyAssistCount(Integer dailyAssistCount) { this.dailyAssistCount = dailyAssistCount; }

    public String getHelpDate() { return helpDate; }
    public void setHelpDate(String helpDate) { this.helpDate = helpDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
