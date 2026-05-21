package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.Item;
import com.huazhenghai.readergame.mapper.ItemMapper;
import com.huazhenghai.readergame.service.ItemService;
import com.huazhenghai.readergame.vo.ItemVO;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ItemServiceImpl implements ItemService {

    private final ItemMapper itemMapper;
    private final ObjectMapper objectMapper;

    public ItemServiceImpl(ItemMapper itemMapper, ObjectMapper objectMapper) {
        this.itemMapper = itemMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ItemVO> getAllItems() {
        QueryWrapper<Item> query = new QueryWrapper<>();
        query.eq("enabled", 1);
        return itemMapper.selectList(query).stream()
                .map(this::toItemVO)
                .collect(Collectors.toList());
    }

    @Override
    public ItemVO getItemByKey(String itemKey) {
        QueryWrapper<Item> query = new QueryWrapper<>();
        query.eq("item_key", itemKey).eq("enabled", 1);
        Item item = itemMapper.selectOne(query);
        return item != null ? toItemVO(item) : null;
    }

    @Override
    public void validateItemEnabled(String itemKey) {
        QueryWrapper<Item> query = new QueryWrapper<>();
        query.eq("item_key", itemKey);
        Item item = itemMapper.selectOne(query);
        if (item == null)
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND, "物品不存在: " + itemKey);
        if (item.getEnabled() == null || item.getEnabled() != 1)
            throw new BusinessException(ErrorCode.ITEM_NOT_ENABLED, "物品已被禁用: " + itemKey);
    }

    private ItemVO toItemVO(Item item) {
        ItemVO vo = new ItemVO();
        vo.setItemKey(item.getItemKey());
        vo.setName(item.getName());
        vo.setItemType(item.getItemType());
        vo.setRarity(item.getRarity());
        vo.setDescription(item.getDescription());
        vo.setEffects(parseJsonMap(item.getEffectsJson()));
        vo.setConsumeOnUse(item.getConsumeOnUse() != null && item.getConsumeOnUse() == 1);
        vo.setSellPrice(item.getSellPrice() != null ? item.getSellPrice() : 0);
        vo.setMaxStack(item.getMaxStack() != null ? item.getMaxStack() : 999);
        return vo;
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank() || "null".equals(json))
            return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}
