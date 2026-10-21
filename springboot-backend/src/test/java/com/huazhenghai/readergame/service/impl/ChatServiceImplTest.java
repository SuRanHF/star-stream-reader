package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.entity.ChatMessage;
import com.huazhenghai.readergame.mapper.ChatMessageMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.websocket.WebSocketSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock private ChatMessageMapper chatMessageMapper;
    @Mock private PlayerMapper playerMapper;
    @Mock private WebSocketSessionManager sessionManager;

    private ChatServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChatServiceImpl(chatMessageMapper, playerMapper, sessionManager, new ObjectMapper());
    }

    @Test
    void worldHistoryIncludesPersistedSystemAnnouncementsInChronologicalOrder() {
        ChatMessage systemAnnouncement = message("system", "broadcast", "全服目标已经达成");
        ChatMessage worldMessage = message("world", "player", "刚才的探索有新发现");
        when(chatMessageMapper.selectList(any()))
                .thenReturn(new ArrayList<>(List.of(systemAnnouncement, worldMessage)));

        var recent = service.getRecentMessages("world", 50);

        assertEquals(List.of("world", "system"), recent.stream().map(entry -> entry.getChannel()).toList());
        assertEquals(List.of("刚才的探索有新发现", "全服目标已经达成"),
                recent.stream().map(entry -> entry.getContent()).toList());

        ArgumentCaptor<QueryWrapper<ChatMessage>> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(chatMessageMapper).selectList(queryCaptor.capture());
        String sql = queryCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("channel IN"), sql);
        assertTrue(sql.contains("ORDER BY created_at DESC"), sql);
    }

    @Test
    void factionHistoryDoesNotMixInWorldOrSystemMessages() {
        when(chatMessageMapper.selectList(any())).thenReturn(List.of());

        service.getRecentMessages("faction", 50);

        ArgumentCaptor<QueryWrapper<ChatMessage>> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(chatMessageMapper).selectList(queryCaptor.capture());
        String sql = queryCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("channel ="), sql);
        assertFalse(sql.contains("channel IN"), sql);
    }

    private ChatMessage message(String channel, String type, String content) {
        ChatMessage message = new ChatMessage();
        message.setChannel(channel);
        message.setMessageType(type);
        message.setContent(content);
        return message;
    }
}
