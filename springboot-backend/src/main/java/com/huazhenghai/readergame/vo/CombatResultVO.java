package com.huazhenghai.readergame.vo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CombatResultVO {

    private String result;
    private Map<String, Object> monster;
    private CombatStatsVO playerStats;
    private int playerHpBefore;
    private int playerHpAfter;
    private int monsterHpAfter;
    private int totalRounds;
    private List<CombatRoundVO> rounds;
    private CombatRewardVO rewards;
    private List<Map<String, Object>> drops;
    private List<Map<String, Object>> newLogs;
    private CombatStatsVO playerSnapshot;

    public CombatResultVO() {
        this.monster = new LinkedHashMap<>();
        this.rounds = new ArrayList<>();
        this.drops = new ArrayList<>();
        this.newLogs = new ArrayList<>();
    }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public Map<String, Object> getMonster() { return monster; }
    public void setMonster(Map<String, Object> monster) { this.monster = monster; }

    public CombatStatsVO getPlayerStats() { return playerStats; }
    public void setPlayerStats(CombatStatsVO playerStats) { this.playerStats = playerStats; }

    public int getPlayerHpBefore() { return playerHpBefore; }
    public void setPlayerHpBefore(int playerHpBefore) { this.playerHpBefore = playerHpBefore; }

    public int getPlayerHpAfter() { return playerHpAfter; }
    public void setPlayerHpAfter(int playerHpAfter) { this.playerHpAfter = playerHpAfter; }

    public int getMonsterHpAfter() { return monsterHpAfter; }
    public void setMonsterHpAfter(int monsterHpAfter) { this.monsterHpAfter = monsterHpAfter; }

    public int getTotalRounds() { return totalRounds; }
    public void setTotalRounds(int totalRounds) { this.totalRounds = totalRounds; }

    public List<CombatRoundVO> getRounds() { return rounds; }
    public void setRounds(List<CombatRoundVO> rounds) { this.rounds = rounds; }

    public CombatRewardVO getRewards() { return rewards; }
    public void setRewards(CombatRewardVO rewards) { this.rewards = rewards; }

    public List<Map<String, Object>> getDrops() { return drops; }
    public void setDrops(List<Map<String, Object>> drops) { this.drops = drops; }

    public List<Map<String, Object>> getNewLogs() { return newLogs; }
    public void setNewLogs(List<Map<String, Object>> newLogs) { this.newLogs = newLogs; }

    public CombatStatsVO getPlayerSnapshot() { return playerSnapshot; }
    public void setPlayerSnapshot(CombatStatsVO playerSnapshot) { this.playerSnapshot = playerSnapshot; }
}
