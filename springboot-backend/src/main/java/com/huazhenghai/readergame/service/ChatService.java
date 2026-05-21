package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.ChatMessageVO;
import com.huazhenghai.readergame.vo.ChatSummaryVO;

import java.util.List;
import java.util.Map;

public interface ChatService {

    ChatMessageVO sendWorldMessage(Long playerId, String content);

    void saveSystemMessage(String content, String messageType, Map<String, Object> metadata);

    List<ChatMessageVO> getRecentMessages(String channel, int limit);

    ChatSummaryVO getChatSummary();
}
