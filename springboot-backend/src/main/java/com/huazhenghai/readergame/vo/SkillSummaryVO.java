package com.huazhenghai.readergame.vo;

/**
 * 技能摘要视图对象 (用于 bootstrap).
 */
public class SkillSummaryVO {

    private int ownedCount;
    private int unlockableCount;
    private boolean hasRareSkill;

    public SkillSummaryVO() {}
    public SkillSummaryVO(int ownedCount, int unlockableCount, boolean hasRareSkill) {
        this.ownedCount = ownedCount;
        this.unlockableCount = unlockableCount;
        this.hasRareSkill = hasRareSkill;
    }

    public int getOwnedCount() { return ownedCount; }
    public void setOwnedCount(int ownedCount) { this.ownedCount = ownedCount; }
    public int getUnlockableCount() { return unlockableCount; }
    public void setUnlockableCount(int unlockableCount) { this.unlockableCount = unlockableCount; }
    public boolean isHasRareSkill() { return hasRareSkill; }
    public void setHasRareSkill(boolean hasRareSkill) { this.hasRareSkill = hasRareSkill; }
}
