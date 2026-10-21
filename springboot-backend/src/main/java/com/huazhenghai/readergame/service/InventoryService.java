package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.dto.SellItemCommand;
import com.huazhenghai.readergame.vo.InventoryItemVO;
import com.huazhenghai.readergame.vo.SellItemResultVO;
import com.huazhenghai.readergame.vo.UseItemResultVO;

import java.util.List;
import java.util.Map;

public interface InventoryService {

    List<InventoryItemVO> getInventory(Long playerId, Long userId);

    int addItem(Long playerId, String itemKey, int quantity);

    ItemGrantResult addItemWithResult(Long playerId, String itemKey, int quantity);

    boolean removeItem(Long playerId, String itemKey, int quantity);

    UseItemResultVO useItem(Long playerId, String itemKey, Long userId, String requestKey);

    Map<String, Object> useItems(Long playerId, String itemKey, int quantity, Long userId, String requestKey);

    SellItemResultVO sellItem(Long playerId, String itemKey, int quantity, Long userId, String requestKey);

    Map<String, Object> sellItems(Long playerId, List<SellItemCommand> items, Long userId, String requestKey);

    List<Map<String, Object>> getRecipes();

    Map<String, Object> synthesize(Long playerId, String recipeKey, Long userId, String requestKey);

    Map<String, Object> synthesizeAll(Long playerId, String recipeKey, int times, Long userId, String requestKey);
}
