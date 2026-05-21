package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("item_memories")
public class ItemMemory {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("item_key")
    private String itemKey;

    @TableField("memory_text")
    private String memoryText;

    private String narrator;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getItemKey() { return itemKey; }
    public void setItemKey(String itemKey) { this.itemKey = itemKey; }
    public String getMemoryText() { return memoryText; }
    public void setMemoryText(String memoryText) { this.memoryText = memoryText; }
    public String getNarrator() { return narrator; }
    public void setNarrator(String narrator) { this.narrator = narrator; }
}
