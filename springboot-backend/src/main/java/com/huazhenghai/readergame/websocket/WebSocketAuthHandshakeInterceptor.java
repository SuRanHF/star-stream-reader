package com.huazhenghai.readergame.websocket;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthHandshakeInterceptor.class);

    private final JwtUtil jwtUtil;
    private final PlayerMapper playerMapper;

    public WebSocketAuthHandshakeInterceptor(JwtUtil jwtUtil, PlayerMapper playerMapper) {
        this.jwtUtil = jwtUtil;
        this.playerMapper = playerMapper;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String query = request.getURI().getQuery();
        if (query == null) {
            log.warn("WebSocket handshake rejected: no query params");
            return false;
        }

        String token = null;
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                token = param.substring(6);
                break;
            }
        }

        if (token == null || token.isBlank()) {
            log.warn("WebSocket handshake rejected: no token");
            return false;
        }

        try {
            Claims claims = jwtUtil.validateToken(token);
            Long userId = jwtUtil.getUserId(claims);
            String username = jwtUtil.getUsername(claims);

            QueryWrapper<Player> qw = new QueryWrapper<>();
            qw.eq("user_id", userId);
            Player player = playerMapper.selectOne(qw);
            if (player == null) {
                log.warn("WebSocket handshake rejected: no player for userId={}", userId);
                return false;
            }

            attributes.put("userId", userId);
            attributes.put("username", username);
            attributes.put("playerId", player.getId());
            attributes.put("playerName", player.getPlayerName());

            log.info("WebSocket authenticated: playerId={}, playerName={}", player.getId(), player.getPlayerName());
            return true;
        } catch (Exception e) {
            log.warn("WebSocket handshake rejected: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
