package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("world_boss_participation")
public class WorldBossParticipation {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("boss_no")
    private String bossNo;

    @TableField("player_id")
    private Long playerId;

    @TableField("party_no")
    private String partyNo;

    private Long damage;

    @TableField("attack_count")
    private Integer attackCount;

    @TableField("last_attack_at")
    private LocalDateTime lastAttackAt;

    @TableField("reward_claimed")
    private Integer rewardClaimed;

    @TableField("rank_no")
    private Integer rankNo;

    @TableField("metadata_json")
    private String metadataJson;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBossNo() { return bossNo; }
    public void setBossNo(String bossNo) { this.bossNo = bossNo; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public String getPartyNo() { return partyNo; }
    public void setPartyNo(String partyNo) { this.partyNo = partyNo; }
    public Long getDamage() { return damage; }
    public void setDamage(Long damage) { this.damage = damage; }
    public Integer getAttackCount() { return attackCount; }
    public void setAttackCount(Integer attackCount) { this.attackCount = attackCount; }
    public LocalDateTime getLastAttackAt() { return lastAttackAt; }
    public void setLastAttackAt(LocalDateTime lastAttackAt) { this.lastAttackAt = lastAttackAt; }
    public Integer getRewardClaimed() { return rewardClaimed; }
    public void setRewardClaimed(Integer rewardClaimed) { this.rewardClaimed = rewardClaimed; }
    public Integer getRankNo() { return rankNo; }
    public void setRankNo(Integer rankNo) { this.rankNo = rankNo; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
