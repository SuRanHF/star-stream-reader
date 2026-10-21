package com.huazhenghai.readergame.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class LearnFactionSkillRequest {

    @NotNull(message = "playerId 不能为空")
    private Long playerId;

    @NotBlank(message = "skillKey 不能为空")
    @Size(max = 100, message = "skillKey 长度不能超过 100")
    private String skillKey;

    @NotBlank(message = "requestKey 不能为空")
    @Size(max = 64, message = "requestKey 长度不能超过 64")
    private String requestKey;

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public String getSkillKey() { return skillKey; }
    public void setSkillKey(String skillKey) { this.skillKey = skillKey; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
}
