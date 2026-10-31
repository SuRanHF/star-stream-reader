package com.huazhenghai.readergame.config;

import com.huazhenghai.readergame.websocket.GameWebSocketHandler;
import com.huazhenghai.readergame.websocket.WebSocketAuthHandshakeInterceptor;
import com.huazhenghai.readergame.security.AllowedOriginPolicy;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler gameWebSocketHandler;
    private final WebSocketAuthHandshakeInterceptor authInterceptor;
    private final AllowedOriginPolicy allowedOriginPolicy;

    public WebSocketConfig(GameWebSocketHandler gameWebSocketHandler,
                           WebSocketAuthHandshakeInterceptor authInterceptor,
                           AllowedOriginPolicy allowedOriginPolicy) {
        this.gameWebSocketHandler = gameWebSocketHandler;
        this.authInterceptor = authInterceptor;
        this.allowedOriginPolicy = allowedOriginPolicy;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gameWebSocketHandler, "/ws/game")
                .addInterceptors(authInterceptor)
                .setAllowedOrigins(allowedOriginPolicy.origins().toArray(String[]::new));
    }
}
