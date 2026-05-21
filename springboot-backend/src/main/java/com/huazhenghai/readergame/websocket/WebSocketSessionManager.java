package com.huazhenghai.readergame.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionManager {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionManager.class);

    private final ObjectMapper objectMapper;

    // sessionId → session
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    // playerId → OnlineEntry
    private final Map<Long, OnlineEntry> onlinePlayers = new ConcurrentHashMap<>();

    public WebSocketSessionManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void addSession(WebSocketSession session) {
        Long playerId = getPlayerId(session);
        String playerName = getPlayerName(session);

        sessions.put(session.getId(), session);

        // Close old connection if same player reconnects
        OnlineEntry old = onlinePlayers.get(playerId);
        if (old != null && !old.sessionId.equals(session.getId())) {
            try {
                WebSocketSession oldSession = sessions.get(old.sessionId);
                if (oldSession != null && oldSession.isOpen()) {
                    oldSession.close();
                }
            } catch (IOException ignored) {
            }
            sessions.remove(old.sessionId);
        }

        onlinePlayers.put(playerId, new OnlineEntry(
                session.getId(), playerId, playerName, LocalDateTime.now()));

        log.info("WS connected: playerId={}, name={}, totalOnline={}",
                playerId, playerName, onlinePlayers.size());
    }

    public void removeSession(WebSocketSession session) {
        String sessionId = session.getId();
        sessions.remove(sessionId);

        Long playerId = getPlayerId(session);
        if (playerId != null) {
            OnlineEntry entry = onlinePlayers.get(playerId);
            if (entry != null && entry.sessionId.equals(sessionId)) {
                onlinePlayers.remove(playerId);
                log.info("WS disconnected: playerId={}, totalOnline={}", playerId, onlinePlayers.size());
            }
        }
    }

    public void sendToPlayer(Long playerId, Object message) {
        OnlineEntry entry = onlinePlayers.get(playerId);
        if (entry == null) return;

        WebSocketSession session = sessions.get(entry.sessionId);
        if (session == null || !session.isOpen()) return;

        sendMessage(session, message);
    }

    public void broadcast(Object message) {
        broadcast(message, null);
    }

    public void broadcast(Object message, Long excludePlayerId) {
        String json = toJson(message);
        if (json == null) return;

        TextMessage textMessage = new TextMessage(json);
        for (OnlineEntry entry : onlinePlayers.values()) {
            if (entry.playerId.equals(excludePlayerId)) continue;
            WebSocketSession session = sessions.get(entry.sessionId);
            if (session != null && session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                } catch (IOException e) {
                    log.warn("Failed to send WS message to playerId={}", entry.playerId);
                }
            }
        }
    }

    public void broadcastToAll(Object message) {
        String json = toJson(message);
        if (json == null) return;

        TextMessage textMessage = new TextMessage(json);
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public int getOnlineCount() {
        return onlinePlayers.size();
    }

    public List<Map<String, Object>> getOnlinePlayers() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (OnlineEntry entry : onlinePlayers.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("playerId", entry.playerId);
            item.put("playerName", entry.playerName);
            item.put("connectedAt", entry.connectedAt.toString());
            result.add(item);
        }
        if (result.size() > 50) {
            result = result.subList(0, 50);
        }
        return result;
    }

    public boolean isOnline(Long playerId) {
        OnlineEntry entry = onlinePlayers.get(playerId);
        if (entry == null) return false;
        WebSocketSession session = sessions.get(entry.sessionId);
        return session != null && session.isOpen();
    }

    private void sendMessage(WebSocketSession session, Object message) {
        String json = toJson(message);
        if (json == null) return;
        try {
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.warn("Failed to send WS message to session {}", session.getId());
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serialize WS message", e);
            return null;
        }
    }

    private Long getPlayerId(WebSocketSession session) {
        Object val = session.getAttributes().get("playerId");
        if (val instanceof Long) return (Long) val;
        if (val instanceof Number) return ((Number) val).longValue();
        return null;
    }

    private String getPlayerName(WebSocketSession session) {
        Object val = session.getAttributes().get("playerName");
        return val != null ? val.toString() : "";
    }

    // ─── inner class ───

    private static class OnlineEntry {
        final String sessionId;
        final Long playerId;
        final String playerName;
        final LocalDateTime connectedAt;

        OnlineEntry(String sessionId, Long playerId, String playerName, LocalDateTime connectedAt) {
            this.sessionId = sessionId;
            this.playerId = playerId;
            this.playerName = playerName;
            this.connectedAt = connectedAt;
        }
    }
}
