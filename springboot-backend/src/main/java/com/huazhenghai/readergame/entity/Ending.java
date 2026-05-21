package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("endings")
public class Ending {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("ending_key")
    private String endingKey;

    private String name;
    private String description;
    private Integer priority;

    @TableField("conditions_json")
    private String conditionsJson;

    @TableField("is_hidden")
    private Integer isHidden;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEndingKey() { return endingKey; }
    public void setEndingKey(String endingKey) { this.endingKey = endingKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public String getConditionsJson() { return conditionsJson; }
    public void setConditionsJson(String conditionsJson) { this.conditionsJson = conditionsJson; }
    public Integer getIsHidden() { return isHidden; }
    public void setIsHidden(Integer isHidden) { this.isHidden = isHidden; }
}
