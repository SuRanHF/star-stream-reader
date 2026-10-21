package com.huazhenghai.readergame.vo;

/**
 * 技能摘要视图对象 (用于 bootstrap).
 */
public class SkillSummaryVO {

    private int ownedCount;
    private int unlockableCount;
    private boolean hasRareSkill;
    private int equippedSkillCount;
    private int skillSlotLimit;

    public SkillSummaryVO() {}
    public SkillSummaryVO(int ownedCount, int unlockableCount, boolean hasRareSkill) {
        this(ownedCount, unlockableCount, hasRareSkill, 0, 0);
    }
    public SkillSummaryVO(int ownedCount, int unlockableCount, boolean hasRareSkill,
                          int equippedSkillCount, int skillSlotLimit) {
        this.ownedCount = ownedCount;
        this.unlockableCount = unlockableCount;
        this.hasRareSkill = hasRareSkill;
        this.equippedSkillCount = equippedSkillCount;
        this.skillSlotLimit = skillSlotLimit;
    }

    public int getOwnedCount() { return ownedCount; }
    public void setOwnedCount(int ownedCount) { this.ownedCount = ownedCount; }
    public int getUnlockableCount() { return unlockableCount; }
    public void setUnlockableCount(int unlockableCount) { this.unlockableCount = unlockableCount; }
    public boolean isHasRareSkill() { return hasRareSkill; }
    public void setHasRareSkill(boolean hasRareSkill) { this.hasRareSkill = hasRareSkill; }
    public int getEquippedSkillCount() { return equippedSkillCount; }
    public void setEquippedSkillCount(int equippedSkillCount) { this.equippedSkillCount = equippedSkillCount; }
    public int getSkillSlotLimit() { return skillSlotLimit; }
    public void setSkillSlotLimit(int skillSlotLimit) { this.skillSlotLimit = skillSlotLimit; }
}
