package com.huazhenghai.readergame.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BuyTradeListingRequest {

    private Long buyerPlayerId;
    private String listingNo;

    public Long getBuyerPlayerId() { return buyerPlayerId; }
    public void setBuyerPlayerId(Long buyerPlayerId) { this.buyerPlayerId = buyerPlayerId; }

    /** 兼容前端 "buyerId" 字段 */
    @JsonProperty("buyerId")
    public void setBuyerId(Long buyerId) {
        if (this.buyerPlayerId == null) this.buyerPlayerId = buyerId;
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
