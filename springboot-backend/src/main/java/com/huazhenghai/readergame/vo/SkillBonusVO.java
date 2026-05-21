package com.huazhenghai.readergame.vo;

import java.util.Map;

/**
 * 技能属性加成视图对象.
 */
public class SkillBonusVO {

    private Map<String, Object> bonuses;

    public SkillBonusVO() {}
    public SkillBonusVO(Map<String, Object> bonuses) { this.bonuses = bonuses; }

    public Map<String, Object> getBonuses() { return bonuses; }
    public void setBonuses(Map<String, Object> bonuses) { this.bonuses = bonuses; }
}
