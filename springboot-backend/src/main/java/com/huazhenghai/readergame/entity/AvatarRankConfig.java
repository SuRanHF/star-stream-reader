package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("avatar_rank_configs")
public class AvatarRankConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("rank_key")
    private String rankKey;

    @TableField("rank_name")
    private String rankName;

    @TableField("display_name")
    private String displayName;

    private String description;

    @TableField("order_num")
    private Integer orderNum;

    @TableField("next_rank_key")
    private String nextRankKey;

    @TableField("requirements_json")
    private String requirementsJson;

    @TableField("rewards_json")
    private String rewardsJson;

    @TableField("unlocks_json")
    private String unlocksJson;

    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRankKey() { return rankKey; }
    public void setRankKey(String rankKey) { this.rankKey = rankKey; }
    public String getRankName() { return rankName; }
    public void setRankName(String rankName) { this.rankName = rankName; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getOrderNum() { return orderNum; }
    public void setOrderNum(Integer orderNum) { this.orderNum = orderNum; }
    public String getNextRankKey() { return nextRankKey; }
    public void setNextRankKey(String nextRankKey) { this.nextRankKey = nextRankKey; }
    public String getRequirementsJson() { return requirementsJson; }
    public void setRequirementsJson(String requirementsJson) { this.requirementsJson = requirementsJson; }
    public String getRewardsJson() { return rewardsJson; }
    public void setRewardsJson(String rewardsJson) { this.rewardsJson = rewardsJson; }
    public String getUnlocksJson() { return unlocksJson; }
    public void setUnlocksJson(String unlocksJson) { this.unlocksJson = unlocksJson; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
