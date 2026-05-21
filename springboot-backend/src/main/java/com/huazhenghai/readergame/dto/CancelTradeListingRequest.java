package com.huazhenghai.readergame.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CancelTradeListingRequest {

    private Long sellerPlayerId;
    private String listingNo;

    public Long getSellerPlayerId() { return sellerPlayerId; }
    public void setSellerPlayerId(Long sellerPlayerId) { this.sellerPlayerId = sellerPlayerId; }

    /** 兼容前端 "playerId" 字段 */
    @JsonProperty("playerId")
    public void setPlayerIdCompat(Long playerId) {
        if (this.sellerPlayerId == null) this.sellerPlayerId = playerId;
    }

    public String getListingNo() { return listingNo; }
    public void setListingNo(String listingNo) { this.listingNo = listingNo; }

    /** 兼容前端 "listingId" (int/number) 字段 */
    @JsonProperty("listingId")
    public void setListingId(Object listingId) {
        if (this.listingNo == null && listingId != null) {
            this.listingNo = String.valueOf(listingId);
        }
    }
}
