package com.huazhenghai.readergame.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 前端发送 playerId + friendId, 后端也兼容 targetPlayerId.
 */
public class SendFriendRequestRequest {

    private Long playerId;
    private Long targetPlayerId;

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }

    public Long getTargetPlayerId() { return targetPlayerId; }

    @JsonProperty("targetPlayerId")
    public void setTargetPlayerId(Long targetPlayerId) { this.targetPlayerId = targetPlayerId; }

    /** 兼容前端发送 "friendId" 字段 */
    @JsonProperty("friendId")
    public void setFriendId(Long friendId) {
        if (this.targetPlayerId == null) this.targetPlayerId = friendId;
    }
}
