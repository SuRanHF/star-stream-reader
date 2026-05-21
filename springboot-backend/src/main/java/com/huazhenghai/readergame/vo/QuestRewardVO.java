package com.huazhenghai.readergame.vo;

import java.util.List;
import java.util.Map;

public class QuestRewardVO {
    private boolean claimed;
    private String questKey;
    private String questTitle;
    private Map<String, Object> earned;
    private List<String> messages;

    public boolean isClaimed() { return claimed; }
    public void setClaimed(boolean claimed) { this.claimed = claimed; }
    public String getQuestKey() { return questKey; }
    public void setQuestKey(String questKey) { this.questKey = questKey; }
    public String getQuestTitle() { return questTitle; }
    public void setQuestTitle(String questTitle) { this.questTitle = questTitle; }
    public Map<String, Object> getEarned() { return earned; }
    public void setEarned(Map<String, Object> earned) { this.earned = earned; }
    public List<String> getMessages() { return messages; }
    public void setMessages(List<String> messages) { this.messages = messages; }
}
