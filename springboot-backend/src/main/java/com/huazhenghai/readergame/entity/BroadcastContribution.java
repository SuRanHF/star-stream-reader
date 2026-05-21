package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("broadcast_contributions")
public class BroadcastContribution {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("event_key")
    private String eventKey;

    @TableField("player_id")
    private Long playerId;

    @TableField("contribution_value")
    private Integer contributionValue;

    @TableField("contribution_type")
    private String contributionType;

    @TableField("reward_claimed")
    private Integer rewardClaimed;

    @TableField("last_contributed_at")
    private LocalDateTime lastContributedAt;

    @TableField("metadata_json")
    private String metadataJson;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public Integer getContributionValue() { return contributionValue; }
    public void setContributionValue(Integer contributionValue) { this.contributionValue = contributionValue; }
    public String getContributionType() { return contributionType; }
    public void setContributionType(String contributionType) { this.contributionType = contributionType; }
    public Integer getRewardClaimed() { return rewardClaimed; }
    public void setRewardClaimed(Integer rewardClaimed) { this.rewardClaimed = rewardClaimed; }
    public LocalDateTime getLastContributedAt() { return lastContributedAt; }
    public void setLastContributedAt(LocalDateTime lastContributedAt) { this.lastContributedAt = lastContributedAt; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
