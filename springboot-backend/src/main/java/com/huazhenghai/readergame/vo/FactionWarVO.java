package com.huazhenghai.readergame.vo;

import java.util.Map;

public class FactionWarVO {

    private Long id;
    private String warNo;
    private String attackerFactionKey;
    private String defenderFactionKey;
    private String status;
    private String startAt;
    private String endAt;
    private Long attackerScore;
    private Long defenderScore;
    private String winnerFactionKey;
    private Map<String, Object> rewards;
    private Map<String, Object> metadata;
    private String createdAt;

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
    public String getStartAt() { return startAt; }
    public void setStartAt(String startAt) { this.startAt = startAt; }
    public String getEndAt() { return endAt; }
    public void setEndAt(String endAt) { this.endAt = endAt; }
    public Long getAttackerScore() { return attackerScore; }
    public void setAttackerScore(Long attackerScore) { this.attackerScore = attackerScore; }
    public Long getDefenderScore() { return defenderScore; }
    public void setDefenderScore(Long defenderScore) { this.defenderScore = defenderScore; }
    public String getWinnerFactionKey() { return winnerFactionKey; }
    public void setWinnerFactionKey(String winnerFactionKey) { this.winnerFactionKey = winnerFactionKey; }
    public Map<String, Object> getRewards() { return rewards; }
    public void setRewards(Map<String, Object> rewards) { this.rewards = rewards; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
