package com.huazhenghai.readergame.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class StartCombatRequest {

    @NotNull(message = "playerId 不能为空")
    private Long playerId;

    @NotBlank(message = "monsterKey 不能为空")
    private String monsterKey;

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }

    public String getMonsterKey() { return monsterKey; }
    public void setMonsterKey(String monsterKey) { this.monsterKey = monsterKey; }
}
