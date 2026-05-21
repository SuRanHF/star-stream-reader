package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("friend_requests")
public class FriendRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("from_player_id")
    private Long fromPlayerId;

    @TableField("to_player_id")
    private Long toPlayerId;

    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFromPlayerId() { return fromPlayerId; }
    public void setFromPlayerId(Long fromPlayerId) { this.fromPlayerId = fromPlayerId; }
    public Long getToPlayerId() { return toPlayerId; }
    public void setToPlayerId(Long toPlayerId) { this.toPlayerId = toPlayerId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
