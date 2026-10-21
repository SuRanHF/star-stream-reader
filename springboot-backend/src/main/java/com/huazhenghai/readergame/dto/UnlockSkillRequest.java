package com.huazhenghai.readergame.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UnlockSkillRequest {

    @NotNull(message = "玩家ID不能为空")
    private Long playerId;

    @NotBlank(message = "技能key不能为空")
    @Size(max = 100, message = "技能key长度不能超过100")
    private String skillKey;

    @NotBlank(message = "requestKey不能为空")
    @Size(max = 64, message = "requestKey长度不能超过64")
    private String requestKey;

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public String getSkillKey() { return skillKey; }
    public void setSkillKey(String skillKey) { this.skillKey = skillKey; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
}
