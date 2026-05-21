package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("faction_wars")
public class FactionWar {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String warNo;
    private String attackerFactionKey;
    private String defenderFactionKey;
    private String status;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Long attackerScore;
    private Long defenderScore;
    private String winnerFactionKey;
    private String rewardsJson;
    private String metadataJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getWarNo() { return warNo; }
    public void setWarNo(String warNo) { this.warNo = warNo; }
    public String getAttackerFactionKey() { return attackerFactionKey; }
    public void setAttackerFactionKey(String attackerFactionKey) { this.attackerFactionKey = attackerFactionKey; }
    public String getDefenderFactionKey() { return defenderFactionKey; }
    public void setDefenderFactionKey(String defenderFactionKey) { this.defenderFactionKey = defenderFactionKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
    public Long getAttackerScore() { return attackerScore; }
    public void setAttackerScore(Long attackerScore) { this.attackerScore = attackerScore; }
    public Long getDefenderScore() { return defenderScore; }
    public void setDefenderScore(Long defenderScore) { this.defenderScore = defenderScore; }
    public String getWinnerFactionKey() { return winnerFactionKey; }
    public void setWinnerFactionKey(String winnerFactionKey) { this.winnerFactionKey = winnerFactionKey; }
    public String getRewardsJson() { return rewardsJson; }
    public void setRewardsJson(String rewardsJson) { this.rewardsJson = rewardsJson; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
