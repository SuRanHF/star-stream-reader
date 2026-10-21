package com.huazhenghai.readergame.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class PeerReviveRequest {

    @NotNull(message = "reviverId 不能为空")
    private Long reviverId;

    @NotNull(message = "targetId 不能为空")
    private Long targetId;

    @Size(max = 16, message = "复活方式不合法")
    private String method = "coins";

    @NotBlank(message = "requestKey 不能为空")
    @Size(max = 64, message = "requestKey 长度不能超过 64")
    private String requestKey;

    public Long getReviverId() { return reviverId; }
    public void setReviverId(Long reviverId) { this.reviverId = reviverId; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
}
