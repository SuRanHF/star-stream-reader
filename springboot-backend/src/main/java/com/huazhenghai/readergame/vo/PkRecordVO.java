package com.huazhenghai.readergame.vo;

import java.util.Map;

public class PkRecordVO {

    private Long id;
    private String attackerName;
    private String defenderName;
    private Long winnerId;
    private Long loserId;
    private Map<String, Object> battleData;
    private Map<String, Object> ratingChange;
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAttackerName() { return attackerName; }
    public void setAttackerName(String attackerName) { this.attackerName = attackerName; }

    public String getDefenderName() { return defenderName; }
    public void setDefenderName(String defenderName) { this.defenderName = defenderName; }

    public Long getWinnerId() { return winnerId; }
    public void setWinnerId(Long winnerId) { this.winnerId = winnerId; }

    public Long getLoserId() { return loserId; }
    public void setLoserId(Long loserId) { this.loserId = loserId; }

    public Map<String, Object> getBattleData() { return battleData; }
    public void setBattleData(Map<String, Object> battleData) { this.battleData = battleData; }

    public Map<String, Object> getRatingChange() { return ratingChange; }
    public void setRatingChange(Map<String, Object> ratingChange) { this.ratingChange = ratingChange; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
