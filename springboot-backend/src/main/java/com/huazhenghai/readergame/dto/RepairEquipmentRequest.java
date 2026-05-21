package com.huazhenghai.readergame.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 修理装备请求。前端发 slot，后端兼容 equipmentKey。
 */
public class RepairEquipmentRequest {

    private Long playerId;
    private String equipmentKey;
    private String slot;

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }

    public String getEquipmentKey() { return equipmentKey; }
    public void setEquipmentKey(String equipmentKey) { this.equipmentKey = equipmentKey; }

    /** 兼容前端 "slot" 字段 */
    @JsonProperty("slot")
    public void setSlot(String slot) { this.slot = slot; }

    public String getSlot() { return slot; }
}
