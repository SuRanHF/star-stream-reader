package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("player_story_logs")
public class PlayerStoryLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("player_id")
    private Long playerId;

    @TableField("event_key")
    private String eventKey;

    @TableField("location_key")
    private String locationKey;

    @TableField("location_name")
    private String locationName;

    @TableField("event_name")
    private String eventName;

    @TableField("choice_index")
    private Integer choiceIndex;

    @TableField("choice_label")
    private String choiceLabel;

    @TableField("consequence_text")
    private String consequenceText;

    @TableField("rewards_snapshot")
    private String rewardsSnapshot;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }
    public String getLocationKey() { return locationKey; }
    public void setLocationKey(String locationKey) { this.locationKey = locationKey; }
    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }
    public Integer getChoiceIndex() { return choiceIndex; }
    public void setChoiceIndex(Integer choiceIndex) { this.choiceIndex = choiceIndex; }
    public String getChoiceLabel() { return choiceLabel; }
    public void setChoiceLabel(String choiceLabel) { this.choiceLabel = choiceLabel; }
    public String getConsequenceText() { return consequenceText; }
    public void setConsequenceText(String consequenceText) { this.consequenceText = consequenceText; }
    public String getRewardsSnapshot() { return rewardsSnapshot; }
    public void setRewardsSnapshot(String rewardsSnapshot) { this.rewardsSnapshot = rewardsSnapshot; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
