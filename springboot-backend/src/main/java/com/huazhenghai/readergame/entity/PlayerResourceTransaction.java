package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("player_resource_transactions")
public class PlayerResourceTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("player_id")
    private Long playerId;

    @TableField("resource_type")
    private String resourceType;

    @TableField("resource_key")
    private String resourceKey;

    private Long amount;

    @TableField("balance_before")
    private Long balanceBefore;

    @TableField("balance_after")
    private Long balanceAfter;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_id")
    private String sourceId;

    @TableField("transaction_group_id")
    private String transactionGroupId;

    @TableField("idempotency_key")
    private String idempotencyKey;

    @TableField("metadata_json")
    private String metadataJson;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getResourceKey() { return resourceKey; }
    public void setResourceKey(String resourceKey) { this.resourceKey = resourceKey; }
    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
    public Long getBalanceBefore() { return balanceBefore; }
    public void setBalanceBefore(Long balanceBefore) { this.balanceBefore = balanceBefore; }
    public Long getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(Long balanceAfter) { this.balanceAfter = balanceAfter; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getTransactionGroupId() { return transactionGroupId; }
    public void setTransactionGroupId(String transactionGroupId) { this.transactionGroupId = transactionGroupId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
