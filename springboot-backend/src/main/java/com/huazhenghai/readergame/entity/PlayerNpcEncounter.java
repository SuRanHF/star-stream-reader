package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("player_npc_encounters")
public class PlayerNpcEncounter {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("player_id")
    private Long playerId;

    @TableField("ghost_key")
    private String ghostKey;

    @TableField("choice_made")
    private String choiceMade;

    private String outcome;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public String getGhostKey() { return ghostKey; }
    public void setGhostKey(String ghostKey) { this.ghostKey = ghostKey; }
    public String getChoiceMade() { return choiceMade; }
    public void setChoiceMade(String choiceMade) { this.choiceMade = choiceMade; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
