package com.huazhenghai.readergame.vo;

public class ChatMessageVO {

    private Long id;
    private String channel;
    private Long senderPlayerId;
    private String senderName;
    private String messageType;
    private String content;
    private String createdAt;
    private String senderStage;
    private String senderTitle;
    private ReplyTo replyTo;

    public record ReplyTo(Long id, String senderName, String contentPreview) {}

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
    public String getSenderStage() { return senderStage; }
    public void setSenderStage(String senderStage) { this.senderStage = senderStage; }
    public String getSenderTitle() { return senderTitle; }
    public void setSenderTitle(String senderTitle) { this.senderTitle = senderTitle; }
    public ReplyTo getReplyTo() { return replyTo; }
    public void setReplyTo(ReplyTo replyTo) { this.replyTo = replyTo; }
}
