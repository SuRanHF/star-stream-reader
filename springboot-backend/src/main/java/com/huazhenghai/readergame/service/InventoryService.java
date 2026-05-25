package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.InventoryItemVO;
import com.huazhenghai.readergame.vo.SellItemResultVO;
import com.huazhenghai.readergame.vo.UseItemResultVO;

import java.util.List;
import java.util.Map;

public interface InventoryService {

    List<InventoryItemVO> getInventory(Long playerId, Long userId);

    int addItem(Long playerId, String itemKey, int quantity);

    boolean removeItem(Long playerId, String itemKey, int quantity);

    UseItemResultVO useItem(Long playerId, String itemKey, Long userId);

    SellItemResultVO sellItem(Long playerId, String itemKey, int quantity, Long userId);

    List<Map<String, Object>> getRecipes();

    Map<String, Object> synthesize(Long playerId, String recipeKey, Long userId);

    Map<String, Object> synthesizeAll(Long playerId, String recipeKey, int times, Long userId);
}
