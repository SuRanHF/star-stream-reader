package com.huazhenghai.readergame.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 离开队伍请求。前端发 partyId + playerId，后端只需要 playerId。
 */
public class LeavePartyRequest {

    private Long playerId;

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }

    /** 前端发 partyId，兼容接收但不使用（离开时从 playerId 查找队伍） */
    @JsonProperty("partyId")
    public void setPartyId(Object partyId) { /* ignored - derived from playerId */ }
}
