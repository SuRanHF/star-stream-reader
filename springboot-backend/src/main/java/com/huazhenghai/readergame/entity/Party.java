package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("parties")
public class Party {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("party_no")
    private String partyNo;

    @TableField("leader_player_id")
    private Long leaderPlayerId;

    private String name;

    private String description;

    private String status;

    @TableField("max_members")
    private Integer maxMembers;

    @TableField("target_type")
    private String targetType;

    @TableField("target_key")
    private String targetKey;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("disbanded_at")
    private LocalDateTime disbandedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPartyNo() { return partyNo; }
    public void setPartyNo(String partyNo) { this.partyNo = partyNo; }
    public Long getLeaderPlayerId() { return leaderPlayerId; }
    public void setLeaderPlayerId(Long leaderPlayerId) { this.leaderPlayerId = leaderPlayerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getMaxMembers() { return maxMembers; }
    public void setMaxMembers(Integer maxMembers) { this.maxMembers = maxMembers; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetKey() { return targetKey; }
    public void setTargetKey(String targetKey) { this.targetKey = targetKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getDisbandedAt() { return disbandedAt; }
    public void setDisbandedAt(LocalDateTime disbandedAt) { this.disbandedAt = disbandedAt; }
}
