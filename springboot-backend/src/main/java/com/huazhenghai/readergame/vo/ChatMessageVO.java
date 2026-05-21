package com.huazhenghai.readergame.vo;

public class ChatMessageVO {

    private Long id;
    private String channel;
    private Long senderPlayerId;
    private String senderName;
    private String messageType;
    private String content;
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public Long getSenderPlayerId() { return senderPlayerId; }
    public void setSenderPlayerId(Long senderPlayerId) { this.senderPlayerId = senderPlayerId; }
    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
