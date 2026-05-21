package com.huazhenghai.readergame.vo;

import java.util.List;
import java.util.Map;

public class ChapterProgressVO {
    private String currentChapterKey;
    private String currentChapterName;
    private boolean completed;
    private boolean canAdvance;
    private String nextChapterKey;
    private String nextChapterName;
    private List<ProgressItem> requirements;
    private Map<String, Object> rewards;

    public String getCurrentChapterKey() { return currentChapterKey; }
    public void setCurrentChapterKey(String currentChapterKey) { this.currentChapterKey = currentChapterKey; }
    public String getCurrentChapterName() { return currentChapterName; }
    public void setCurrentChapterName(String currentChapterName) { this.currentChapterName = currentChapterName; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public boolean isCanAdvance() { return canAdvance; }
    public void setCanAdvance(boolean canAdvance) { this.canAdvance = canAdvance; }
    public String getNextChapterKey() { return nextChapterKey; }
    public void setNextChapterKey(String nextChapterKey) { this.nextChapterKey = nextChapterKey; }
    public String getNextChapterName() { return nextChapterName; }
    public void setNextChapterName(String nextChapterName) { this.nextChapterName = nextChapterName; }
    public List<ProgressItem> getRequirements() { return requirements; }
    public void setRequirements(List<ProgressItem> requirements) { this.requirements = requirements; }
    public Map<String, Object> getRewards() { return rewards; }
    public void setRewards(Map<String, Object> rewards) { this.rewards = rewards; }

    public static class ProgressItem {
        private String type;
        private String label;
        private int current;
        private int required;
        private boolean completed;

        public ProgressItem() {}
        public ProgressItem(String type, String label, int current, int required, boolean completed) {
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
