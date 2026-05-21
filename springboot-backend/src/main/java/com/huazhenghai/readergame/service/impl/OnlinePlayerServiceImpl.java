package com.huazhenghai.readergame.service.impl;

import com.huazhenghai.readergame.service.OnlinePlayerService;
import com.huazhenghai.readergame.vo.OnlineSummaryVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class OnlinePlayerServiceImpl implements OnlinePlayerService {

    private final com.huazhenghai.readergame.websocket.WebSocketSessionManager sessionManager;

    public OnlinePlayerServiceImpl(com.huazhenghai.readergame.websocket.WebSocketSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void onConnected(Long playerId, String playerName) {
        // SessionManager handles this via WebSocketHandler
    }

    @Override
    public void onDisconnected(Long playerId) {
        // SessionManager handles this via WebSocketHandler
    }

    @Override
    public OnlineSummaryVO getOnlineSummary() {
        OnlineSummaryVO vo = new OnlineSummaryVO();
        vo.setOnlineCount(sessionManager.getOnlineCount());
        return vo;
    }

    @Override
    public List<Map<String, Object>> getOnlinePlayers() {
        return sessionManager.getOnlinePlayers();
    }

    @Override
    public boolean isOnline(Long playerId) {
        return sessionManager.isOnline(playerId);
    }
}
