package com.huazhenghai.readergame.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CancelTradeListingRequest {

    private Long sellerPlayerId;
    private String listingNo;
    @NotBlank(message = "requestKey 不能为空")
    @Size(max = 64, message = "requestKey 长度不能超过 64")
    private String requestKey;

    public Long getSellerPlayerId() { return sellerPlayerId; }
    public void setSellerPlayerId(Long sellerPlayerId) { this.sellerPlayerId = sellerPlayerId; }

    /** 兼容前端 "playerId" 字段 */
    @JsonProperty("playerId")
    public void setPlayerIdCompat(Long playerId) {
        if (this.sellerPlayerId == null) this.sellerPlayerId = playerId;
    }

    public String getListingNo() { return listingNo; }
    public void setListingNo(String listingNo) { this.listingNo = listingNo; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }

    /** 兼容前端 "listingId" (int/number) 字段 */
    @JsonProperty("listingId")
    public void setListingId(Object listingId) {
        if (this.listingNo == null && listingId != null) {
            this.listingNo = String.valueOf(listingId);
        }
    }
}
