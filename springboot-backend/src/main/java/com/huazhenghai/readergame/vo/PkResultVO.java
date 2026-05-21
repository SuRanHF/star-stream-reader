package com.huazhenghai.readergame.vo;

import java.util.List;
import java.util.Map;

public class PkResultVO {

    private Long winnerId;
    private String winnerName;
    private Long loserId;
    private String loserName;
    private boolean attackerWins;
    private String mode;
    private Map<String, Integer> ratingChange;
    private Map<String, Object> battleLog;
    private List<Map<String, Object>> rounds;
    private int totalRounds;

    public Long getWinnerId() { return winnerId; }
    public void setWinnerId(Long winnerId) { this.winnerId = winnerId; }

    public String getWinnerName() { return winnerName; }
    public void setWinnerName(String winnerName) { this.winnerName = winnerName; }

    public Long getLoserId() { return loserId; }
    public void setLoserId(Long loserId) { this.loserId = loserId; }

    public String getLoserName() { return loserName; }
    public void setLoserName(String loserName) { this.loserName = loserName; }

    public boolean isAttackerWins() { return attackerWins; }
    public void setAttackerWins(boolean attackerWins) { this.attackerWins = attackerWins; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public Map<String, Integer> getRatingChange() { return ratingChange; }
    public void setRatingChange(Map<String, Integer> ratingChange) { this.ratingChange = ratingChange; }

    public Map<String, Object> getBattleLog() { return battleLog; }
    public void setBattleLog(Map<String, Object> battleLog) { this.battleLog = battleLog; }

    public List<Map<String, Object>> getRounds() { return rounds; }
    public void setRounds(List<Map<String, Object>> rounds) { this.rounds = rounds; }

    public int getTotalRounds() { return totalRounds; }
    public void setTotalRounds(int totalRounds) { this.totalRounds = totalRounds; }
}
