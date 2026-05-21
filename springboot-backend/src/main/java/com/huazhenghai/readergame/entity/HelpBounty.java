package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("help_bounties")
public class HelpBounty {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("owner_id")
    private Long ownerId;

    @TableField("monster_key")
    private String monsterKey;

    @TableField("location_key")
    private String locationKey;

    @TableField("monster_name")
    private String monsterName;

    @TableField("share_percent")
    private Integer sharePercent;

    private String status;

    @TableField("helper_id")
    private Long helperId;

    @TableField("bounty_rewards_json")
    private String bountyRewardsJson;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("resolved_at")
    private LocalDateTime resolvedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getMonsterKey() { return monsterKey; }
    public void setMonsterKey(String monsterKey) { this.monsterKey = monsterKey; }
    public String getLocationKey() { return locationKey; }
    public void setLocationKey(String locationKey) { this.locationKey = locationKey; }
    public String getMonsterName() { return monsterName; }
    public void setMonsterName(String monsterName) { this.monsterName = monsterName; }
    public Integer getSharePercent() { return sharePercent; }
    public void setSharePercent(Integer sharePercent) { this.sharePercent = sharePercent; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getHelperId() { return helperId; }
    public void setHelperId(Long helperId) { this.helperId = helperId; }
    public String getBountyRewardsJson() { return bountyRewardsJson; }
    public void setBountyRewardsJson(String bountyRewardsJson) { this.bountyRewardsJson = bountyRewardsJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
