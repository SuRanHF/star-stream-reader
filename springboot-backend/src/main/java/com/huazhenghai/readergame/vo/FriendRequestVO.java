package com.huazhenghai.readergame.vo;

public class FriendRequestVO {

    private Long id;
    private Long fromPlayerId;
    private String fromPlayerName;
    private String status;
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFromPlayerId() { return fromPlayerId; }
    public void setFromPlayerId(Long fromPlayerId) { this.fromPlayerId = fromPlayerId; }
    public String getFromPlayerName() { return fromPlayerName; }
    public void setFromPlayerName(String fromPlayerName) { this.fromPlayerName = fromPlayerName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
