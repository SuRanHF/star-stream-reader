package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.ItemVO;

import java.util.List;

public interface ItemService {

    List<ItemVO> getAllItems();

    ItemVO getItemByKey(String itemKey);

    void validateItemEnabled(String itemKey);
}
