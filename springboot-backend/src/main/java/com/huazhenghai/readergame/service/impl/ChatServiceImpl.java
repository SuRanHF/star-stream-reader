package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.ChatMessage;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.ChatMessageMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.ChatService;
import com.huazhenghai.readergame.vo.ChatMessageVO;
import com.huazhenghai.readergame.vo.ChatSummaryVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);
    private static final int MAX_CONTENT_LENGTH = 200;
    private static final int MAX_RECENT_LIMIT = 100;

    private final ChatMessageMapper chatMessageMapper;
    private final PlayerMapper playerMapper;
    private final com.huazhenghai.readergame.websocket.WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    // Rate limiting: playerId → lastSendTime
    private final Map<Long, Long> rateLimitMap = new HashMap<>();

    public ChatServiceImpl(ChatMessageMapper chatMessageMapper,
                           PlayerMapper playerMapper,
                           com.huazhenghai.readergame.websocket.WebSocketSessionManager sessionManager,
                           ObjectMapper objectMapper) {
        this.chatMessageMapper = chatMessageMapper;
        this.playerMapper = playerMapper;
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ChatMessageVO sendWorldMessage(Long playerId, String content) {
        // Validate player exists
        Player player = playerMapper.selectById(playerId);
        if (player == null) {
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        }

        // Validate content
        if (content == null || content.trim().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "消息不能为空");
        }
        content = content.trim();
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE,
                    "消息最长" + MAX_CONTENT_LENGTH + "字符");
        }

        // Rate limit: 2 seconds
        long now = System.currentTimeMillis();
        synchronized (rateLimitMap) {
            Long lastSend = rateLimitMap.get(playerId);
            if (lastSend != null && (now - lastSend) < 2000) {
                throw new BusinessException(ErrorCode.RATE_LIMITED, "发送过于频繁，请稍后再试");
            }
            rateLimitMap.put(playerId, now);
        }

        // Save to DB
        ChatMessage msg = new ChatMessage();
        msg.setChannel("world");
        msg.setSenderPlayerId(playerId);
        msg.setSenderName(player.getPlayerName());
        msg.setMessageType("player");
        msg.setContent(content);
        msg.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(msg);

        ChatMessageVO vo = toVO(msg);

        // Broadcast via WebSocket
        Map<String, Object> wsMsg = new LinkedHashMap<>();
        wsMsg.put("type", "chat.message");
        wsMsg.put("data", vo);
        sessionManager.broadcast(wsMsg);

        return vo;
    }

    @Override
    public void saveSystemMessage(String content, String messageType, Map<String, Object> metadata) {
        ChatMessage msg = new ChatMessage();
        msg.setChannel("system");
        msg.setSenderPlayerId(0L);
        msg.setSenderName("系统");
        msg.setMessageType(messageType != null ? messageType : "system");
        msg.setContent(content);
        try {
            if (metadata != null) {
                msg.setMetadataJson(objectMapper.writeValueAsString(metadata));
            }
        } catch (Exception ignored) {
        }
        msg.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(msg);

        ChatMessageVO vo = toVO(msg);

        // Broadcast
        Map<String, Object> wsMsg = new LinkedHashMap<>();
        wsMsg.put("type", "system.message");
        wsMsg.put("data", vo);
        sessionManager.broadcast(wsMsg);
    }

    @Override
    public List<ChatMessageVO> getRecentMessages(String channel, int limit) {
        if (channel == null || channel.isBlank()) channel = "world";
        limit = Math.min(Math.max(limit, 1), MAX_RECENT_LIMIT);

        QueryWrapper<ChatMessage> qw = new QueryWrapper<>();
        qw.eq("channel", channel)
          .orderByDesc("created_at")
          .last("LIMIT " + limit);
        List<ChatMessage> messages = chatMessageMapper.selectList(qw);

        // Reverse to chronological order
        Collections.reverse(messages);

        List<ChatMessageVO> result = new ArrayList<>();
        for (ChatMessage m : messages) {
            result.add(toVO(m));
        }
        return result;
    }

    @Override
    public ChatSummaryVO getChatSummary() {
        ChatSummaryVO vo = new ChatSummaryVO();

        try {
            QueryWrapper<ChatMessage> qw = new QueryWrapper<>();
            qw.eq("channel", "world")
              .orderByDesc("created_at")
              .last("LIMIT 20");
            List<ChatMessage> recent = chatMessageMapper.selectList(qw);
            vo.setRecentMessageCount(recent.size());

            if (!recent.isEmpty()) {
                vo.setLatestMessage(toVO(recent.get(recent.size() - 1)));
            }
        } catch (Exception e) {
            log.warn("Failed to get chat summary: {}", e.getMessage());
        }

        return vo;
    }

    private ChatMessageVO toVO(ChatMessage m) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(m.getId());
        vo.setChannel(m.getChannel());
        vo.setSenderPlayerId(m.getSenderPlayerId());
        vo.setSenderName(m.getSenderName());
        vo.setMessageType(m.getMessageType());
        vo.setContent(m.getContent());
        vo.setCreatedAt(m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
        return vo;
    }
}
