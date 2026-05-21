package com.huazhenghai.readergame.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateWorldBossRequest {

    @NotBlank(message = "bossKey 不能为空")
    private String bossKey;

    private String name;
    private String description;
    private Integer level = 10;
    private Long maxHp = 100000L;
    private Integer attack = 25;
    private Integer defense = 12;
    private Integer speed = 10;
    private String rewardsJson;
    private String rankRewardsJson;
    private String worldlineEffectsJson;

    public String getBossKey() { return bossKey; }
    public void setBossKey(String bossKey) { this.bossKey = bossKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public Long getMaxHp() { return maxHp; }
    public void setMaxHp(Long maxHp) { this.maxHp = maxHp; }
    public Integer getAttack() { return attack; }
    public void setAttack(Integer attack) { this.attack = attack; }
    public Integer getDefense() { return defense; }
    public void setDefense(Integer defense) { this.defense = defense; }
    public Integer getSpeed() { return speed; }
    public void setSpeed(Integer speed) { this.speed = speed; }
    public String getRewardsJson() { return rewardsJson; }
    public void setRewardsJson(String rewardsJson) { this.rewardsJson = rewardsJson; }
    public String getRankRewardsJson() { return rankRewardsJson; }
    public void setRankRewardsJson(String rankRewardsJson) { this.rankRewardsJson = rankRewardsJson; }
    public String getWorldlineEffectsJson() { return worldlineEffectsJson; }
    public void setWorldlineEffectsJson(String worldlineEffectsJson) { this.worldlineEffectsJson = worldlineEffectsJson; }
}
