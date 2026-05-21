package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("pk_records")
public class PkRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("attacker_id")
    private Long attackerId;

    @TableField("defender_id")
    private Long defenderId;

    @TableField("winner_id")
    private Long winnerId;

    @TableField("loser_id")
    private Long loserId;

    @TableField("battle_data_json")
    private String battleDataJson;

    @TableField("rating_change_json")
    private String ratingChangeJson;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAttackerId() { return attackerId; }
    public void setAttackerId(Long attackerId) { this.attackerId = attackerId; }

    public Long getDefenderId() { return defenderId; }
    public void setDefenderId(Long defenderId) { this.defenderId = defenderId; }

    public Long getWinnerId() { return winnerId; }
    public void setWinnerId(Long winnerId) { this.winnerId = winnerId; }

    public Long getLoserId() { return loserId; }
    public void setLoserId(Long loserId) { this.loserId = loserId; }

    public String getBattleDataJson() { return battleDataJson; }
    public void setBattleDataJson(String battleDataJson) { this.battleDataJson = battleDataJson; }

    public String getRatingChangeJson() { return ratingChangeJson; }
    public void setRatingChangeJson(String ratingChangeJson) { this.ratingChangeJson = ratingChangeJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
