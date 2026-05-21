package com.huazhenghai.readergame.vo;

import java.util.LinkedHashMap;
import java.util.Map;

public class CombatStatsVO {

    private int level;
    private int hp;
    private int maxHp;
    private int attack;
    private int defense;
    private int speed;
    private double critRate;
    private double critDamage;
    private Map<String, Object> bonuses;

    public CombatStatsVO() {
        this.bonuses = new LinkedHashMap<>();
    }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public int getHp() { return hp; }
    public void setHp(int hp) { this.hp = hp; }

    public int getMaxHp() { return maxHp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }

    public int getAttack() { return attack; }
    public void setAttack(int attack) { this.attack = attack; }

    public int getDefense() { return defense; }
    public void setDefense(int defense) { this.defense = defense; }

    public int getSpeed() { return speed; }
    public void setSpeed(int speed) { this.speed = speed; }

    public double getCritRate() { return critRate; }
    public void setCritRate(double critRate) { this.critRate = critRate; }

    public double getCritDamage() { return critDamage; }
    public void setCritDamage(double critDamage) { this.critDamage = critDamage; }

    public Map<String, Object> getBonuses() { return bonuses; }
    public void setBonuses(Map<String, Object> bonuses) { this.bonuses = bonuses; }
}
