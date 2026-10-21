package com.huazhenghai.readergame.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class SellItemRequest {

    @NotNull(message = "玩家ID不能为空")
    private Long playerId;

    @NotBlank(message = "物品key不能为空")
    private String itemKey;

    private int quantity = 1;

    @NotBlank(message = "requestKey 不能为空")
    @Size(max = 64, message = "requestKey 长度不能超过 64")
    private String requestKey;

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public String getItemKey() { return itemKey; }
    public void setItemKey(String itemKey) { this.itemKey = itemKey; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
}
