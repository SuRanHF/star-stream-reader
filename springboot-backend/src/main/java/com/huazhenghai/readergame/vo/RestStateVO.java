package com.huazhenghai.readergame.vo;

public class RestStateVO {

    private boolean isResting;
    private String restStartedAt;
    private Integer hp;
    private Integer maxHp;
    private Integer stamina;
    private Integer maxStamina;
    private String lastRecoveryAt;

    public boolean getIsResting() { return isResting; }
    public void setIsResting(boolean isResting) { this.isResting = isResting; }
    public String getRestStartedAt() { return restStartedAt; }
    public void setRestStartedAt(String restStartedAt) { this.restStartedAt = restStartedAt; }
    public Integer getHp() { return hp; }
    public void setHp(Integer hp) { this.hp = hp; }
    public Integer getMaxHp() { return maxHp; }
    public void setMaxHp(Integer maxHp) { this.maxHp = maxHp; }
    public Integer getStamina() { return stamina; }
    public void setStamina(Integer stamina) { this.stamina = stamina; }
    public Integer getMaxStamina() { return maxStamina; }
    public void setMaxStamina(Integer maxStamina) { this.maxStamina = maxStamina; }
    public String getLastRecoveryAt() { return lastRecoveryAt; }
    public void setLastRecoveryAt(String lastRecoveryAt) { this.lastRecoveryAt = lastRecoveryAt; }
}
