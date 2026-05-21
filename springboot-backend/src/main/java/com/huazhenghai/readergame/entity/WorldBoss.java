package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("world_bosses")
public class WorldBoss {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("boss_key")
    private String bossKey;

    @TableField("boss_no")
    private String bossNo;

    private String name;
    private String description;
    private String status;
    private Integer level;

    @TableField("max_hp")
    private Long maxHp;

    @TableField("current_hp")
    private Long currentHp;

    private Integer attack;
    private Integer defense;
    private Integer speed;

    @TableField("start_at")
    private LocalDateTime startAt;

    @TableField("end_at")
    private LocalDateTime endAt;

    @TableField("killed_at")
    private LocalDateTime killedAt;

    @TableField("rewards_json")
    private String rewardsJson;

    @TableField("rank_rewards_json")
    private String rankRewardsJson;

    @TableField("worldline_effects_json")
    private String worldlineEffectsJson;

    @TableField("metadata_json")
    private String metadataJson;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBossKey() { return bossKey; }
    public void setBossKey(String bossKey) { this.bossKey = bossKey; }
    public String getBossNo() { return bossNo; }
    public void setBossNo(String bossNo) { this.bossNo = bossNo; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public Long getMaxHp() { return maxHp; }
    public void setMaxHp(Long maxHp) { this.maxHp = maxHp; }
    public Long getCurrentHp() { return currentHp; }
    public void setCurrentHp(Long currentHp) { this.currentHp = currentHp; }
    public Integer getAttack() { return attack; }
    public void setAttack(Integer attack) { this.attack = attack; }
    public Integer getDefense() { return defense; }
    public void setDefense(Integer defense) { this.defense = defense; }
    public Integer getSpeed() { return speed; }
    public void setSpeed(Integer speed) { this.speed = speed; }
    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
    public LocalDateTime getKilledAt() { return killedAt; }
    public void setKilledAt(LocalDateTime killedAt) { this.killedAt = killedAt; }
    public String getRewardsJson() { return rewardsJson; }
    public void setRewardsJson(String rewardsJson) { this.rewardsJson = rewardsJson; }
    public String getRankRewardsJson() { return rankRewardsJson; }
    public void setRankRewardsJson(String rankRewardsJson) { this.rankRewardsJson = rankRewardsJson; }
    public String getWorldlineEffectsJson() { return worldlineEffectsJson; }
    public void setWorldlineEffectsJson(String worldlineEffectsJson) { this.worldlineEffectsJson = worldlineEffectsJson; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
