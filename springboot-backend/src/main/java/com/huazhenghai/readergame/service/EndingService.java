package com.huazhenghai.readergame.service;

import java.util.List;
import java.util.Map;

public interface EndingService {

    /** 检查玩家当前满足条件的所有结局 */
    List<Map<String, Object>> checkEndings(Long playerId);
}
