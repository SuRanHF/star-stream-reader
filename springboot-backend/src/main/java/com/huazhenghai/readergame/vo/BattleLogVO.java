package com.huazhenghai.readergame.vo;

import java.util.List;
import java.util.Map;

public class BattleLogVO {

    private Long id;
    private Long playerId;
    private String monsterKey;
    private String monsterName;
    private String result;
    private int rounds;
    private Map<String, Object> playerSnapshot;
    private Map<String, Object> monsterSnapshot;
    private List<Map<String, Object>> roundDetails;
    private Map<String, Object> rewards;
    private List<Map<String, Object>> drops;
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }

    public String getMonsterKey() { return monsterKey; }
    public void setMonsterKey(String monsterKey) { this.monsterKey = monsterKey; }

    public String getMonsterName() { return monsterName; }
    public void setMonsterName(String monsterName) { this.monsterName = monsterName; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public int getRounds() { return rounds; }
    public void setRounds(int rounds) { this.rounds = rounds; }

    public Map<String, Object> getPlayerSnapshot() { return playerSnapshot; }
    public void setPlayerSnapshot(Map<String, Object> playerSnapshot) { this.playerSnapshot = playerSnapshot; }

    public Map<String, Object> getMonsterSnapshot() { return monsterSnapshot; }
    public void setMonsterSnapshot(Map<String, Object> monsterSnapshot) { this.monsterSnapshot = monsterSnapshot; }

    public List<Map<String, Object>> getRoundDetails() { return roundDetails; }
    public void setRoundDetails(List<Map<String, Object>> roundDetails) { this.roundDetails = roundDetails; }

    public Map<String, Object> getRewards() { return rewards; }
    public void setRewards(Map<String, Object> rewards) { this.rewards = rewards; }

    public List<Map<String, Object>> getDrops() { return drops; }
    public void setDrops(List<Map<String, Object>> drops) { this.drops = drops; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
