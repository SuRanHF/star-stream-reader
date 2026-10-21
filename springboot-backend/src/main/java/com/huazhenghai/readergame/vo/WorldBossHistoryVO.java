package com.huazhenghai.readergame.vo;

/**
 * 已结束世界Boss的档案摘要。
 *
 * <p>历史接口只返回展示和回顾需要的数据，不把奖励配置等内部 JSON 直接暴露给玩家端。</p>
 */
public class WorldBossHistoryVO {

    private Long id;
    private String bossNo;
    private String bossKey;
    private String name;
    private String description;
    private String status;
    private Integer level;
    private Long maxHp;
    private Long currentHp;
    private String startAt;
    private String endAt;
    private String killedAt;
    private String createdAt;
    private Integer participantCount;
    private Long totalDamage;
    private String topPlayerName;
    private Long topDamage;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBossNo() { return bossNo; }
    public void setBossNo(String bossNo) { this.bossNo = bossNo; }
    public String getBossKey() { return bossKey; }
    public void setBossKey(String bossKey) { this.bossKey = bossKey; }
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
    public String getStartAt() { return startAt; }
    public void setStartAt(String startAt) { this.startAt = startAt; }
    public String getEndAt() { return endAt; }
    public void setEndAt(String endAt) { this.endAt = endAt; }
    public String getKilledAt() { return killedAt; }
    public void setKilledAt(String killedAt) { this.killedAt = killedAt; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public Integer getParticipantCount() { return participantCount; }
    public void setParticipantCount(Integer participantCount) { this.participantCount = participantCount; }
    public Long getTotalDamage() { return totalDamage; }
    public void setTotalDamage(Long totalDamage) { this.totalDamage = totalDamage; }
    public String getTopPlayerName() { return topPlayerName; }
    public void setTopPlayerName(String topPlayerName) { this.topPlayerName = topPlayerName; }
    public Long getTopDamage() { return topDamage; }
    public void setTopDamage(Long topDamage) { this.topDamage = topDamage; }
}
