package com.huazhenghai.readergame.vo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CombatRewardVO {

    private int exp;
    private int coins;
    private int storyFragments;
    private int channelHeat;
    private List<Map<String, Object>> items;
    private List<Map<String, Object>> equipment;

    public CombatRewardVO() {
        this.items = new ArrayList<>();
        this.equipment = new ArrayList<>();
    }

    public int getExp() { return exp; }
    public void setExp(int exp) { this.exp = exp; }

    public int getCoins() { return coins; }
    public void setCoins(int coins) { this.coins = coins; }

    public int getStoryFragments() { return storyFragments; }
    public void setStoryFragments(int storyFragments) { this.storyFragments = storyFragments; }

    public int getChannelHeat() { return channelHeat; }
    public void setChannelHeat(int channelHeat) { this.channelHeat = channelHeat; }

    public List<Map<String, Object>> getItems() { return items; }
    public void setItems(List<Map<String, Object>> items) { this.items = items; }

    public List<Map<String, Object>> getEquipment() { return equipment; }
    public void setEquipment(List<Map<String, Object>> equipment) { this.equipment = equipment; }
}
