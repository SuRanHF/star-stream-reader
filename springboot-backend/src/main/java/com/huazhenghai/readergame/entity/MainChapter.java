package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("main_chapters")
public class MainChapter {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("chapter_key")
    private String chapterKey;

    private String name;
    private String description;

    @TableField("order_num")
    private Integer orderNum;

    @TableField("unlock_conditions_json")
    private String unlockConditionsJson;

    @TableField("completion_conditions_json")
    private String completionConditionsJson;

    @TableField("rewards_json")
    private String rewardsJson;

    @TableField("next_chapter_key")
    private String nextChapterKey;

    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getChapterKey() { return chapterKey; }
    public void setChapterKey(String chapterKey) { this.chapterKey = chapterKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getOrderNum() { return orderNum; }
    public void setOrderNum(Integer orderNum) { this.orderNum = orderNum; }
    public String getUnlockConditionsJson() { return unlockConditionsJson; }
    public void setUnlockConditionsJson(String unlockConditionsJson) { this.unlockConditionsJson = unlockConditionsJson; }
    public String getCompletionConditionsJson() { return completionConditionsJson; }
    public void setCompletionConditionsJson(String completionConditionsJson) { this.completionConditionsJson = completionConditionsJson; }
    public String getRewardsJson() { return rewardsJson; }
    public void setRewardsJson(String rewardsJson) { this.rewardsJson = rewardsJson; }
    public String getNextChapterKey() { return nextChapterKey; }
    public void setNextChapterKey(String nextChapterKey) { this.nextChapterKey = nextChapterKey; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
