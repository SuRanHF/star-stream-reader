package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.OnlineSummaryVO;

import java.util.List;
import java.util.Map;

public interface OnlinePlayerService {

    void onConnected(Long playerId, String playerName);

    void onDisconnected(Long playerId);

    OnlineSummaryVO getOnlineSummary();

    List<Map<String, Object>> getOnlinePlayers();

    boolean isOnline(Long playerId);
}
