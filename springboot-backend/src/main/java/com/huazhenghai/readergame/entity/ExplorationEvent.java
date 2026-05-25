package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("exploration_events")
public class ExplorationEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("event_key")
    private String eventKey;

    @TableField("event_type")
    private String eventType;

    @TableField("stage_key")
    private String stageKey;

    @TableField("location_key")
    private String locationKey;

    private String name;

    private String description;

    private Integer weight;

    @TableField("stamina_cost")
    private Integer staminaCost;

    private Integer repeatable;

    @TableField("required_conditions_json")
    private String requiredConditionsJson;

    @TableField("rewards_json")
    private String rewardsJson;

    @TableField("risks_json")
    private String risksJson;

    @TableField("progress_effects_json")
    private String progressEffectsJson;

    @TableField("choices_json")
    private String choicesJson;

    @TableField("log_template")
    private String logTemplate;

    private Integer enabled;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getStageKey() { return stageKey; }
    public void setStageKey(String stageKey) { this.stageKey = stageKey; }
    public String getLocationKey() { return locationKey; }
    public void setLocationKey(String locationKey) { this.locationKey = locationKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getWeight() { return weight; }
    public void setWeight(Integer weight) { this.weight = weight; }
    public Integer getStaminaCost() { return staminaCost; }
    public void setStaminaCost(Integer staminaCost) { this.staminaCost = staminaCost; }
    public Integer getRepeatable() { return repeatable; }
    public void setRepeatable(Integer repeatable) { this.repeatable = repeatable; }
    public String getRequiredConditionsJson() { return requiredConditionsJson; }
    public void setRequiredConditionsJson(String requiredConditionsJson) { this.requiredConditionsJson = requiredConditionsJson; }
    public String getRewardsJson() { return rewardsJson; }
    public void setRewardsJson(String rewardsJson) { this.rewardsJson = rewardsJson; }
    public String getRisksJson() { return risksJson; }
    public void setRisksJson(String risksJson) { this.risksJson = risksJson; }
    public String getProgressEffectsJson() { return progressEffectsJson; }
    public void setProgressEffectsJson(String progressEffectsJson) { this.progressEffectsJson = progressEffectsJson; }
    public String getChoicesJson() { return choicesJson; }
    public void setChoicesJson(String choicesJson) { this.choicesJson = choicesJson; }
    public String getLogTemplate() { return logTemplate; }
    public void setLogTemplate(String logTemplate) { this.logTemplate = logTemplate; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
