package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("battle_logs")
public class BattleLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("player_id")
    private Long playerId;

    @TableField("monster_key")
    private String monsterKey;

    private String result;
    private Integer rounds;

    @TableField("player_snapshot_json")
    private String playerSnapshotJson;

    @TableField("monster_snapshot_json")
    private String monsterSnapshotJson;

    @TableField("rounds_json")
    private String roundsJson;

    @TableField("rewards_json")
    private String rewardsJson;

    @TableField("drops_json")
    private String dropsJson;

    @TableField("created_at")
    private LocalDateTime createdAt;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }

    public String getMonsterKey() { return monsterKey; }
    public void setMonsterKey(String monsterKey) { this.monsterKey = monsterKey; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public Integer getRounds() { return rounds; }
    public void setRounds(Integer rounds) { this.rounds = rounds; }

    public String getPlayerSnapshotJson() { return playerSnapshotJson; }
    public void setPlayerSnapshotJson(String playerSnapshotJson) { this.playerSnapshotJson = playerSnapshotJson; }

    public String getMonsterSnapshotJson() { return monsterSnapshotJson; }
    public void setMonsterSnapshotJson(String monsterSnapshotJson) { this.monsterSnapshotJson = monsterSnapshotJson; }

    public String getRoundsJson() { return roundsJson; }
    public void setRoundsJson(String roundsJson) { this.roundsJson = roundsJson; }

    public String getRewardsJson() { return rewardsJson; }
    public void setRewardsJson(String rewardsJson) { this.rewardsJson = rewardsJson; }

    public String getDropsJson() { return dropsJson; }
    public void setDropsJson(String dropsJson) { this.dropsJson = dropsJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
