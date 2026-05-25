package com.huazhenghai.readergame.vo;

public class RestStateVO {

    private boolean isResting;
    private String restStartedAt;
    private Integer hp;
    private Integer maxHp;
    private Integer stamina;
    private Integer maxStamina;
    private Integer exp;
    private Integer maxExp;
    private String lastRecoveryAt;
    private Integer hpIntervalSeconds;
    private Integer staminaIntervalSeconds;
    private Integer expIntervalSeconds;

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
    public Integer getExp() { return exp; }
    public void setExp(Integer exp) { this.exp = exp; }
    public Integer getMaxExp() { return maxExp; }
    public void setMaxExp(Integer maxExp) { this.maxExp = maxExp; }
    public Integer getHpIntervalSeconds() { return hpIntervalSeconds; }
    public void setHpIntervalSeconds(Integer hpIntervalSeconds) { this.hpIntervalSeconds = hpIntervalSeconds; }
    public Integer getStaminaIntervalSeconds() { return staminaIntervalSeconds; }
    public void setStaminaIntervalSeconds(Integer staminaIntervalSeconds) { this.staminaIntervalSeconds = staminaIntervalSeconds; }
    public Integer getExpIntervalSeconds() { return expIntervalSeconds; }
    public void setExpIntervalSeconds(Integer expIntervalSeconds) { this.expIntervalSeconds = expIntervalSeconds; }
}
