package com.huazhenghai.readergame.vo;

public class WorldBossAttackResultVO {

    private boolean success;
    private long damage;
    private boolean critical;
    private long bossCurrentHp;
    private long bossMaxHp;
    private boolean bossDefeated;
    private Long myTotalDamage;
    private int myAttackCount;
    private String message;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public long getDamage() { return damage; }
    public void setDamage(long damage) { this.damage = damage; }
    public boolean isCritical() { return critical; }
    public void setCritical(boolean critical) { this.critical = critical; }
    public long getBossCurrentHp() { return bossCurrentHp; }
    public void setBossCurrentHp(long bossCurrentHp) { this.bossCurrentHp = bossCurrentHp; }
    public long getBossMaxHp() { return bossMaxHp; }
    public void setBossMaxHp(long bossMaxHp) { this.bossMaxHp = bossMaxHp; }
    public boolean isBossDefeated() { return bossDefeated; }
    public void setBossDefeated(boolean bossDefeated) { this.bossDefeated = bossDefeated; }
    public Long getMyTotalDamage() { return myTotalDamage; }
    public void setMyTotalDamage(Long myTotalDamage) { this.myTotalDamage = myTotalDamage; }
    public int getMyAttackCount() { return myAttackCount; }
    public void setMyAttackCount(int myAttackCount) { this.myAttackCount = myAttackCount; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
