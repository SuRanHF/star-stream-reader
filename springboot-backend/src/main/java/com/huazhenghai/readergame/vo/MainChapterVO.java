package com.huazhenghai.readergame.vo;

import java.util.List;
import java.util.Map;

public class MainChapterVO {
    private String chapterKey;
    private String name;
    private String description;
    private int orderNum;
    private Map<String, Object> unlockConditions;
    private Map<String, Object> completionConditions;
    private Map<String, Object> rewards;
    private String nextChapterKey;

    public String getChapterKey() { return chapterKey; }
    public void setChapterKey(String chapterKey) { this.chapterKey = chapterKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getOrderNum() { return orderNum; }
    public void setOrderNum(int orderNum) { this.orderNum = orderNum; }
    public Map<String, Object> getUnlockConditions() { return unlockConditions; }
    public void setUnlockConditions(Map<String, Object> unlockConditions) { this.unlockConditions = unlockConditions; }
    public Map<String, Object> getCompletionConditions() { return completionConditions; }
    public void setCompletionConditions(Map<String, Object> completionConditions) { this.completionConditions = completionConditions; }
    public Map<String, Object> getRewards() { return rewards; }
    public void setRewards(Map<String, Object> rewards) { this.rewards = rewards; }
    public String getNextChapterKey() { return nextChapterKey; }
    public void setNextChapterKey(String nextChapterKey) { this.nextChapterKey = nextChapterKey; }
}
