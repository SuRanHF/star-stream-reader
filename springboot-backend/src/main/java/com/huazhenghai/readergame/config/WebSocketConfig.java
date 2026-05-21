package com.huazhenghai.readergame.config;

import com.huazhenghai.readergame.websocket.GameWebSocketHandler;
import com.huazhenghai.readergame.websocket.WebSocketAuthHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler gameWebSocketHandler;
    private final WebSocketAuthHandshakeInterceptor authInterceptor;

    public WebSocketConfig(GameWebSocketHandler gameWebSocketHandler,
                           WebSocketAuthHandshakeInterceptor authInterceptor) {
        this.gameWebSocketHandler = gameWebSocketHandler;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gameWebSocketHandler, "/ws/game")
                .addInterceptors(authInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
