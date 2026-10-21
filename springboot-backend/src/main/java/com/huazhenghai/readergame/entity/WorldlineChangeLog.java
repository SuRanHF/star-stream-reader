package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("worldline_change_logs")
public class WorldlineChangeLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("state_key")
    private String stateKey;

    @TableField("state_name")
    private String stateName;

    @TableField("value_key")
    private String valueKey;

    @TableField("change_amount")
    private Double changeAmount;

    @TableField("before_value")
    private Double beforeValue;

    @TableField("after_value")
    private Double afterValue;

    private String cause;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStateKey() { return stateKey; }
    public void setStateKey(String stateKey) { this.stateKey = stateKey; }
    public String getStateName() { return stateName; }
    public void setStateName(String stateName) { this.stateName = stateName; }
    public String getValueKey() { return valueKey; }
    public void setValueKey(String valueKey) { this.valueKey = valueKey; }
    public Double getChangeAmount() { return changeAmount; }
    public void setChangeAmount(Double changeAmount) { this.changeAmount = changeAmount; }
    public Double getBeforeValue() { return beforeValue; }
    public void setBeforeValue(Double beforeValue) { this.beforeValue = beforeValue; }
    public Double getAfterValue() { return afterValue; }
    public void setAfterValue(Double afterValue) { this.afterValue = afterValue; }
    public String getCause() { return cause; }
    public void setCause(String cause) { this.cause = cause; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
