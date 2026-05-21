package com.huazhenghai.readergame.vo;

import java.util.List;
import java.util.Map;

public class AvatarRankVO {
    private String currentRank;
    private String currentRankName;
    private String currentDisplayName;
    private String storyGrade;
    private String storyGradeLabel;
    private String starstreamTier;
    private String starstreamTierLabel;
    private int currentOrder;
    private String nextRank;
    private String nextRankName;
    private String nextDisplayName;
    private boolean canRankUp;
    private boolean isMaxRank;
    private List<RankProgressItem> requirements;
    private Map<String, Object> rewards;

    public String getCurrentRank() { return currentRank; }
    public void setCurrentRank(String currentRank) { this.currentRank = currentRank; }
    public String getCurrentRankName() { return currentRankName; }
    public void setCurrentRankName(String currentRankName) { this.currentRankName = currentRankName; }
    public String getCurrentDisplayName() { return currentDisplayName; }
    public void setCurrentDisplayName(String currentDisplayName) { this.currentDisplayName = currentDisplayName; }
    public String getStoryGrade() { return storyGrade; }
    public void setStoryGrade(String storyGrade) { this.storyGrade = storyGrade; }
    public String getStoryGradeLabel() { return storyGradeLabel; }
    public void setStoryGradeLabel(String storyGradeLabel) { this.storyGradeLabel = storyGradeLabel; }
    public String getStarstreamTier() { return starstreamTier; }
    public void setStarstreamTier(String starstreamTier) { this.starstreamTier = starstreamTier; }
    public String getStarstreamTierLabel() { return starstreamTierLabel; }
    public void setStarstreamTierLabel(String starstreamTierLabel) { this.starstreamTierLabel = starstreamTierLabel; }
    public int getCurrentOrder() { return currentOrder; }
    public void setCurrentOrder(int currentOrder) { this.currentOrder = currentOrder; }
    public String getNextRank() { return nextRank; }
    public void setNextRank(String nextRank) { this.nextRank = nextRank; }
    public String getNextRankName() { return nextRankName; }
    public void setNextRankName(String nextRankName) { this.nextRankName = nextRankName; }
    public String getNextDisplayName() { return nextDisplayName; }
    public void setNextDisplayName(String nextDisplayName) { this.nextDisplayName = nextDisplayName; }
    public boolean isCanRankUp() { return canRankUp; }
    public void setCanRankUp(boolean canRankUp) { this.canRankUp = canRankUp; }
    public boolean isMaxRank() { return isMaxRank; }
    public void setMaxRank(boolean maxRank) { isMaxRank = maxRank; }
    public List<RankProgressItem> getRequirements() { return requirements; }
    public void setRequirements(List<RankProgressItem> requirements) { this.requirements = requirements; }
    public Map<String, Object> getRewards() { return rewards; }
    public void setRewards(Map<String, Object> rewards) { this.rewards = rewards; }

    public static class RankProgressItem {
        private String type;
        private String label;
        private int current;
        private int required;
        private boolean completed;

        public RankProgressItem() {}
        public RankProgressItem(String type, String label, int current, int required, boolean completed) {
            this.type = type; this.label = label; this.current = current;
            this.required = required; this.completed = completed;
        }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public int getCurrent() { return current; }
        public void setCurrent(int current) { this.current = current; }
        public int getRequired() { return required; }
        public void setRequired(int required) { this.required = required; }
        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }
    }
}
