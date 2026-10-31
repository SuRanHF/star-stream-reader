package com.huazhenghai.readergame.websocket;

import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUser;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketTicketServiceTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final Map<String, String> ticketStore = new HashMap<>();
    private final WebSocketTicketService tickets = new WebSocketTicketService(redis);

    private void useIsolatedStore() {
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(invocation -> {
            assertEquals(Duration.ofSeconds(WebSocketTicketService.VALID_SECONDS), invocation.getArgument(2));
            ticketStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.getAndDelete(anyString())).thenAnswer(invocation ->
                ticketStore.remove(invocation.getArgument(0)));
    }

    @Test
    void ticketIsConsumedOnlyOnce() {
        useIsolatedStore();
        String ticket = tickets.issue(new LoginUser(19L, "reader", "player"));
        assertTrue(ticket.matches("[A-Za-z0-9_-]{43}"));
        assertEquals(19L, tickets.consume(ticket));
        assertNull(tickets.consume(ticket));
    }

    @Test
    void expiredTicketIsRejected() {
        useIsolatedStore();
        String ticket = tickets.issue(new LoginUser(19L, "reader", "player"));
        ticketStore.clear(); // Isolated store simulates expiry before the Redis GETDEL.
        assertNull(tickets.consume(ticket));
    }

    @Test
    void redisFailureRejectsHandshake() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.getAndDelete(anyString())).thenThrow(new IllegalStateException("Redis unavailable"));
        PlayerMapper players = mock(PlayerMapper.class);
        var interceptor = new WebSocketAuthHandshakeInterceptor(tickets, players);
        assertFalse(interceptor.beforeHandshake(request("ticket=" + "A".repeat(43)),
                mock(ServerHttpResponse.class), mock(WebSocketHandler.class), new HashMap<>()));
        org.mockito.Mockito.verifyNoInteractions(players);
    }

    @Test
    void consumedTicketWithoutPlayerRejectsHandshake() {
        useIsolatedStore();
        String ticket = tickets.issue(new LoginUser(19L, "reader", "player"));
        PlayerMapper players = mock(PlayerMapper.class);
        var interceptor = new WebSocketAuthHandshakeInterceptor(tickets, players);
        Map<String, Object> attributes = new HashMap<>();
        assertFalse(interceptor.beforeHandshake(request("ticket=" + ticket),
                mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes));
        assertTrue(attributes.isEmpty());
        verify(players).selectOne(any());
        assertNull(tickets.consume(ticket));
    }

    private ServerHttpRequest request(String query) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws?" + query));
        return request;
    }
}
