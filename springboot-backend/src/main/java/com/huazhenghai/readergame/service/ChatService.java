package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.ChatMessageVO;
import com.huazhenghai.readergame.vo.ChatSummaryVO;

import java.util.List;
import java.util.Map;

public interface ChatService {

    ChatMessageVO sendWorldMessage(Long playerId, String content);

    ChatMessageVO sendMessage(Long playerId, String content, String channel, Long replyToId);

    default ChatMessageVO sendMessage(Long playerId, String content, String channel) {
        return sendMessage(playerId, content, channel, null);
    }

    void saveSystemMessage(String content, String messageType, Map<String, Object> metadata);

    List<ChatMessageVO> getRecentMessages(String channel, int limit, Long playerId, Long beforeId);

    default List<ChatMessageVO> getRecentMessages(String channel, int limit) {
        return getRecentMessages(channel, limit, null, null);
    }

    ChatSummaryVO getChatSummary();
}
