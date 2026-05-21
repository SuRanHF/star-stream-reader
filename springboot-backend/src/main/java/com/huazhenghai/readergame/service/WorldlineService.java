package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.WorldlineSummaryVO;

import java.util.List;
import java.util.Map;

public interface WorldlineService {

    List<Map<String, Object>> getWorldStates();

    WorldlineSummaryVO getWorldlineSummary();

    void applyBroadcastWorldlineEffects(String eventKey, String effectsJson);

    void addWorldlineShift(double amount, String cause);

    /** 对所有世界状态值施加衰减，返回受影响行数 */
    int applyDecay(double decayRate);
}
