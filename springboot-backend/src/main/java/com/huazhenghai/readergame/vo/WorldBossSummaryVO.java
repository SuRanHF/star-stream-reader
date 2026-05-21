package com.huazhenghai.readergame.vo;

public class WorldBossSummaryVO {

    private boolean activeBoss;
    private String bossNo;
    private String bossName;
    private String status;
    private double hpPercent;
    private Long myDamage;
    private Integer myRank;
    private boolean canClaimReward;

    public boolean isActiveBoss() { return activeBoss; }
    public void setActiveBoss(boolean activeBoss) { this.activeBoss = activeBoss; }
    public String getBossNo() { return bossNo; }
    public void setBossNo(String bossNo) { this.bossNo = bossNo; }
    public String getBossName() { return bossName; }
    public void setBossName(String bossName) { this.bossName = bossName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public double getHpPercent() { return hpPercent; }
    public void setHpPercent(double hpPercent) { this.hpPercent = hpPercent; }
    public Long getMyDamage() { return myDamage; }
    public void setMyDamage(Long myDamage) { this.myDamage = myDamage; }
    public Integer getMyRank() { return myRank; }
    public void setMyRank(Integer myRank) { this.myRank = myRank; }
    public boolean isCanClaimReward() { return canClaimReward; }
    public void setCanClaimReward(boolean canClaimReward) { this.canClaimReward = canClaimReward; }
}
