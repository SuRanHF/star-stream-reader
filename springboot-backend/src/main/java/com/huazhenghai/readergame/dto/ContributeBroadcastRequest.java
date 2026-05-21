package com.huazhenghai.readergame.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class ContributeBroadcastRequest {

    @NotBlank(message = "事件key不能为空")
    private String eventKey;

    @NotNull(message = "玩家ID不能为空")
    private Long playerId;

    @Positive(message = "贡献值必须为正数")
    private Integer value;

    private String contributionType;

    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public Integer getValue() { return value; }
    public void setValue(Integer value) { this.value = value; }
    public String getContributionType() { return contributionType; }
    public void setContributionType(String contributionType) { this.contributionType = contributionType; }
}
