package com.huazhenghai.readergame.vo;

public class ChatSummaryVO {

    private int recentMessageCount;
    private ChatMessageVO latestMessage;

    public int getRecentMessageCount() { return recentMessageCount; }
    public void setRecentMessageCount(int recentMessageCount) { this.recentMessageCount = recentMessageCount; }
    public ChatMessageVO getLatestMessage() { return latestMessage; }
    public void setLatestMessage(ChatMessageVO latestMessage) { this.latestMessage = latestMessage; }
}
