package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("location_echoes")
public class LocationEcho {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("location_key")
    private String locationKey;

    @TableField("echo_text")
    private String echoText;

    private String narrator;
    private Double weight;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLocationKey() { return locationKey; }
    public void setLocationKey(String locationKey) { this.locationKey = locationKey; }
    public String getEchoText() { return echoText; }
    public void setEchoText(String echoText) { this.echoText = echoText; }
    public String getNarrator() { return narrator; }
    public void setNarrator(String narrator) { this.narrator = narrator; }
    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }
}
