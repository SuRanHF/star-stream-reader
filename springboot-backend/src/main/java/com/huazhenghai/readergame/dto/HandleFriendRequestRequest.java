package com.huazhenghai.readergame.dto;

/**
 * 前端发送 playerId + requestId.
 */
public class HandleFriendRequestRequest {

    private Long playerId;
    private Long requestId;

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
}
