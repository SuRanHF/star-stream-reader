package com.huazhenghai.readergame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("synthesis_recipes")
public class SynthesisRecipe {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("recipe_key")
    private String recipeKey;

    private String name;

    private String description;

    @TableField("result_item_key")
    private String resultItemKey;

    @TableField("result_quantity")
    private Integer resultQuantity;

    @TableField("ingredients_json")
    private String ingredientsJson;

    @TableField("cost_coins")
    private Integer costCoins;

    private Integer enabled;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRecipeKey() { return recipeKey; }
    public void setRecipeKey(String recipeKey) { this.recipeKey = recipeKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getResultItemKey() { return resultItemKey; }
    public void setResultItemKey(String resultItemKey) { this.resultItemKey = resultItemKey; }
    public Integer getResultQuantity() { return resultQuantity; }
    public void setResultQuantity(Integer resultQuantity) { this.resultQuantity = resultQuantity; }
    public String getIngredientsJson() { return ingredientsJson; }
    public void setIngredientsJson(String ingredientsJson) { this.ingredientsJson = ingredientsJson; }
    public Integer getCostCoins() { return costCoins; }
    public void setCostCoins(Integer costCoins) { this.costCoins = costCoins; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
