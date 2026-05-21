package com.huazhenghai.readergame.dto;

import jakarta.validation.constraints.NotNull;

public class ResolveChallengeRequest {

    @NotNull(message = "challengeId 不能为空")
    private Long challengeId;

    @NotNull(message = "playerId 不能为空")
    private Long playerId;

    private boolean accept;

    public Long getChallengeId() { return challengeId; }
    public void setChallengeId(Long challengeId) { this.challengeId = challengeId; }

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }

    public boolean isAccept() { return accept; }
    public void setAccept(boolean accept) { this.accept = accept; }
}
