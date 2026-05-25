package com.huazhenghai.readergame.vo;

import java.util.List;
import java.util.Map;

public class WorldBossVO {

    private Long id;
    private String bossKey;
    private String bossNo;
    private String name;
    private String description;
    private String status;
    private int level;
    private long maxHp;
    private long currentHp;
    private int attack;
    private int defense;
    private int speed;
    private String startAt;
    private String endAt;
    private String killedAt;
    private Map<String, Object> rewards;
    private Map<String, Object> rankRewards;
    private Map<String, Object> worldlineEffects;
    private Map<String, Object> metadata;
    private String createdAt;
    private String updatedAt;

    // player-specific fields
    private Long myDamage;
    private Integer myRank;
    private Boolean canClaimReward;
    private List<WorldBossRankingVO> topRankings;

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
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public long getMaxHp() { return maxHp; }
    public void setMaxHp(long maxHp) { this.maxHp = maxHp; }
    public long getCurrentHp() { return currentHp; }
    public void setCurrentHp(long currentHp) { this.currentHp = currentHp; }
    @com.fasterxml.jackson.annotation.JsonProperty("hp")
    public long getHp() { return currentHp; }
    @com.fasterxml.jackson.annotation.JsonProperty("max_hp")
    public long getMax_hp() { return maxHp; }
    public int getAttack() { return attack; }
    public void setAttack(int attack) { this.attack = attack; }
    public int getDefense() { return defense; }
    public void setDefense(int defense) { this.defense = defense; }
    public int getSpeed() { return speed; }
    public void setSpeed(int speed) { this.speed = speed; }
    public String getStartAt() { return startAt; }
    public void setStartAt(String startAt) { this.startAt = startAt; }
    public String getEndAt() { return endAt; }
    public void setEndAt(String endAt) { this.endAt = endAt; }
    public String getKilledAt() { return killedAt; }
    public void setKilledAt(String killedAt) { this.killedAt = killedAt; }
    public Map<String, Object> getRewards() { return rewards; }
    public void setRewards(Map<String, Object> rewards) { this.rewards = rewards; }
    public Map<String, Object> getRankRewards() { return rankRewards; }
    public void setRankRewards(Map<String, Object> rankRewards) { this.rankRewards = rankRewards; }
    public Map<String, Object> getWorldlineEffects() { return worldlineEffects; }
    public void setWorldlineEffects(Map<String, Object> worldlineEffects) { this.worldlineEffects = worldlineEffects; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public Long getMyDamage() { return myDamage; }
    public void setMyDamage(Long myDamage) { this.myDamage = myDamage; }
    public Integer getMyRank() { return myRank; }
    public void setMyRank(Integer myRank) { this.myRank = myRank; }
    public Boolean getCanClaimReward() { return canClaimReward; }
    public void setCanClaimReward(Boolean canClaimReward) { this.canClaimReward = canClaimReward; }
    public List<WorldBossRankingVO> getTopRankings() { return topRankings; }
    public void setTopRankings(List<WorldBossRankingVO> topRankings) { this.topRankings = topRankings; }
}
