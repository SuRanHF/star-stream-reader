package com.huazhenghai.readergame.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SendChatMessageRequest {

    private Long playerId;
    private String playerName;
    private String channel;

    // 兼容前端发 "message" 和后端旧字段 "content"
    private String content;

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    @JsonProperty("content")
    public String getContent() { return content; }

    @JsonProperty("content")
    public void setContent(String content) { this.content = content; }

    /** 兼容前端发送 "message" 字段 */
    @JsonProperty("message")
    public void setMessage(String message) {
        if (this.content == null) this.content = message;
    }
}
