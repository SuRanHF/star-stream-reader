package com.huazhenghai.readergame.vo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CombatRoundVO {

    private int round;
    private List<Map<String, Object>> actions;

    public CombatRoundVO() {
        this.actions = new ArrayList<>();
    }

    public int getRound() { return round; }
    public void setRound(int round) { this.round = round; }

    public List<Map<String, Object>> getActions() { return actions; }
    public void setActions(List<Map<String, Object>> actions) { this.actions = actions; }
}
