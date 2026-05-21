package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("npc_ghosts")
public class NpcGhost {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("ghost_key")
    private String ghostKey;

    private String name;
    private String description;

    @TableField("dialogue_tree_json")
    private String dialogueTreeJson;

    @TableField("location_keys_json")
    private String locationKeysJson;

    @TableField("encounter_weight")
    private Double encounterWeight;

    @TableField("is_unique")
    private Integer isUnique;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getGhostKey() { return ghostKey; }
    public void setGhostKey(String ghostKey) { this.ghostKey = ghostKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDialogueTreeJson() { return dialogueTreeJson; }
    public void setDialogueTreeJson(String dialogueTreeJson) { this.dialogueTreeJson = dialogueTreeJson; }
    public String getLocationKeysJson() { return locationKeysJson; }
    public void setLocationKeysJson(String locationKeysJson) { this.locationKeysJson = locationKeysJson; }
    public Double getEncounterWeight() { return encounterWeight; }
    public void setEncounterWeight(Double encounterWeight) { this.encounterWeight = encounterWeight; }
    public Integer getIsUnique() { return isUnique; }
    public void setIsUnique(Integer isUnique) { this.isUnique = isUnique; }
}
