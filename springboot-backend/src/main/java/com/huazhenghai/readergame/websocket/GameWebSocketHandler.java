package com.huazhenghai.readergame.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public GameWebSocketHandler(WebSocketSessionManager sessionManager, ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionManager.addSession(session);

        // Send welcome with online count
        Map<String, Object> welcome = new java.util.LinkedHashMap<>();
        welcome.put("type", "welcome");
        Map<String, Object> welcomeData = new java.util.LinkedHashMap<>();
        welcomeData.put("message", "已连接到游戏服务器");
        welcomeData.put("onlineCount", sessionManager.getOnlineCount());
        welcome.put("data", welcomeData);
        sendToSession(session, welcome);

        // Broadcast updated online count to all
        broadcastOnlineCount();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            Map<String, Object> msg = objectMapper.readValue(
                    message.getPayload(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});

            String type = (String) msg.get("type");

            if ("ping".equals(type)) {
                Map<String, Object> pong = new java.util.LinkedHashMap<>();
                pong.put("type", "pong");
                sendToSession(session, pong);
                return;
            }

            log.debug("WS message type={} from session={}", type, session.getId());
        } catch (Exception e) {
            log.warn("Failed to parse WS message: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionManager.removeSession(session);
        broadcastOnlineCount();
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WS transport error session={}: {}", session.getId(), exception.getMessage());
        sessionManager.removeSession(session);
        broadcastOnlineCount();
    }

    private void sendToSession(WebSocketSession session, Object message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.warn("Failed to send WS message: {}", e.getMessage());
        }
    }

    private void broadcastOnlineCount() {
        Map<String, Object> msg = new java.util.LinkedHashMap<>();
        msg.put("type", "online.summary");
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("onlineCount", sessionManager.getOnlineCount());
        msg.put("data", data);
        sessionManager.broadcastToAll(msg);
    }
}
