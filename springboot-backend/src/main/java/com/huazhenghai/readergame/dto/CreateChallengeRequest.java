package com.huazhenghai.readergame.dto;

import jakarta.validation.constraints.NotNull;

public class CreateChallengeRequest {

    @NotNull(message = "attackerId 不能为空")
    private Long attackerId;

    @NotNull(message = "defenderId 不能为空")
    private Long defenderId;

    private String mode = "spar";

    public Long getAttackerId() { return attackerId; }
    public void setAttackerId(Long attackerId) { this.attackerId = attackerId; }

    public Long getDefenderId() { return defenderId; }
    public void setDefenderId(Long defenderId) { this.defenderId = defenderId; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}
