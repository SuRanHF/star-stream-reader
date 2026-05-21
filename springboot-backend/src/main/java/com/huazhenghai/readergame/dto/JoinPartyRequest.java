package com.huazhenghai.readergame.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 加入队伍请求。前端发 partyId (int)，后端兼容 partyNo (String)。
 */
public class JoinPartyRequest {

    private Long playerId;
    private String partyNo;

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }

    public String getPartyNo() { return partyNo; }
    public void setPartyNo(String partyNo) { this.partyNo = partyNo; }

    /** 兼容前端 "partyId" (int) → 转为 partyNo */
    @JsonProperty("partyId")
    public void setPartyId(Object partyId) {
        if (this.partyNo == null && partyId != null) {
            this.partyNo = String.valueOf(partyId);
        }
    }
}
