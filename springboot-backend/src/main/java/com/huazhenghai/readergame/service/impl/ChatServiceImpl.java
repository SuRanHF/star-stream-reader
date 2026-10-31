package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.*;
import com.huazhenghai.readergame.mapper.*;
import com.huazhenghai.readergame.service.ChatService;
import com.huazhenghai.readergame.vo.ChatMessageVO;
import com.huazhenghai.readergame.vo.ChatSummaryVO;
import com.huazhenghai.readergame.websocket.WebSocketSessionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ChatServiceImpl implements ChatService {
    private static final int MAX_RECENT_LIMIT = 100;
    private static final long RATE_LIMIT_MILLIS = 2000;
    private final ChatMessageMapper chatMessageMapper;
    private final PlayerMapper playerMapper;
    private final PartyMemberMapper partyMemberMapper;
    private final PartyMapper partyMapper;
    private final PlayerFactionMapper playerFactionMapper;
    private final PlayerTitleMapper playerTitleMapper;
    private final TitleMapper titleMapper;
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final Map<Long, Long> rateLimitMap = new HashMap<>();

    public ChatServiceImpl(ChatMessageMapper chatMessageMapper, PlayerMapper playerMapper,
                           PartyMemberMapper partyMemberMapper, PartyMapper partyMapper,
                           PlayerFactionMapper playerFactionMapper, PlayerTitleMapper playerTitleMapper,
                           TitleMapper titleMapper, WebSocketSessionManager sessionManager,
                           ObjectMapper objectMapper) {
        this.chatMessageMapper = chatMessageMapper;
        this.playerMapper = playerMapper;
        this.partyMemberMapper = partyMemberMapper;
        this.partyMapper = partyMapper;
        this.playerFactionMapper = playerFactionMapper;
        this.playerTitleMapper = playerTitleMapper;
        this.titleMapper = titleMapper;
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatMessageVO sendWorldMessage(Long playerId, String content) {
        return sendMessage(playerId, content, "world", null);
    }

    @Override
    @Transactional
    public ChatMessageVO sendMessage(Long playerId, String content, String channel, Long replyToId) {
        String normalized = normalizeChannel(channel);
        Player player = playerMapper.selectById(playerId);
        if (player == null) throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        String scopeId = resolveScope(playerId, normalized);
        if (content == null || content.trim().isBlank())
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "消息不能为空");
        content = content.trim();
        if (content.length() > 200)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "消息最长200字符");

        ChatMessage reply = null;
        if (replyToId != null) {
            if (replyToId <= 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "replyToId 必须为正整数");
            reply = chatMessageMapper.selectById(replyToId);
            boolean sameChannel = reply != null && (normalized.equals(reply.getChannel())
                    || ("world".equals(normalized) && "system".equals(reply.getChannel())));
            if (!sameChannel || !Objects.equals(scopeId, reply.getScopeId()))
                throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "无法回复此频道的消息");
        }
        long now = System.currentTimeMillis();
        synchronized (rateLimitMap) {
            Long last = rateLimitMap.get(playerId);
            if (last != null && now - last < RATE_LIMIT_MILLIS)
                throw new BusinessException(ErrorCode.RATE_LIMITED, "发送过于频繁，请稍后再试");
            rateLimitMap.put(playerId, now);
            if (rateLimitMap.size() > 1024)
                rateLimitMap.entrySet().removeIf(entry -> now - entry.getValue() >= RATE_LIMIT_MILLIS);
        }

        ChatMessage msg = new ChatMessage();
        msg.setChannel(normalized);
        msg.setScopeId(scopeId);
        msg.setReplyToId(replyToId);
        msg.setSenderPlayerId(playerId);
        msg.setSenderName(player.getPlayerName());
        msg.setMessageType("player");
        msg.setContent(content);
        msg.setCreatedAt(LocalDateTime.now());
        if (reply != null) {
            try {
                Map<String, Object> snapshot = new LinkedHashMap<>();
                snapshot.put("id", reply.getId());
                snapshot.put("senderName", reply.getSenderName());
                snapshot.put("contentPreview", preview(reply.getContent()));
                msg.setMetadataJson(objectMapper.writeValueAsString(Map.of("replyTo", snapshot)));
            } catch (Exception e) {
                throw new IllegalStateException("无法保存回复快照", e);
            }
        }
        chatMessageMapper.insert(msg);
        ChatMessageVO vo = toVOs(List.of(msg)).get(0);
        Map<String, Object> event = Map.of("type", "chat.message", "data", vo);
        if (scopeId == null) sessionManager.broadcast(event);
        else for (Long recipient : recipients(normalized, scopeId)) sessionManager.sendToPlayer(recipient, event);
        return vo;
    }

    private String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) return "world";
        String value = channel.trim().toLowerCase(Locale.ROOT);
        if (Set.of("world", "team", "faction").contains(value)) return value;
        throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "未知聊天频道");
    }

    private String resolveScope(Long playerId, String channel) {
        if ("world".equals(channel)) return null;
        if (playerId == null || playerId <= 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "playerId 必须为正整数");
        if ("faction".equals(channel)) {
            List<PlayerFaction> memberships = playerFactionMapper.selectList(new QueryWrapper<PlayerFaction>()
                    .eq("player_id", playerId).eq("status", "active").last("LIMIT 1"));
            if (memberships.isEmpty() || memberships.get(0).getFactionKey() == null)
                throw new BusinessException(ErrorCode.FORBIDDEN, "玩家不属于有效阵营");
            return memberships.get(0).getFactionKey();
        }
        List<PartyMember> memberships = partyMemberMapper.selectList(new QueryWrapper<PartyMember>()
                .eq("player_id", playerId).eq("status", "active"));
        for (PartyMember member : memberships) {
            if (member.getPartyNo() == null) continue;
            Long count = partyMapper.selectCount(new QueryWrapper<Party>()
                    .eq("party_no", member.getPartyNo()).eq("status", "active"));
            if (count != null && count > 0) return member.getPartyNo();
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "玩家不属于有效队伍");
    }

    private Set<Long> recipients(String channel, String scopeId) {
        Set<Long> ids = new HashSet<>();
        if ("faction".equals(channel)) {
            for (PlayerFaction member : playerFactionMapper.selectList(new QueryWrapper<PlayerFaction>()
                    .eq("faction_key", scopeId).eq("status", "active"))) ids.add(member.getPlayerId());
        } else {
            for (PartyMember member : partyMemberMapper.selectList(new QueryWrapper<PartyMember>()
                    .eq("party_no", scopeId).eq("status", "active"))) ids.add(member.getPlayerId());
        }
        return ids;
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
            if (metadata != null) msg.setMetadataJson(objectMapper.writeValueAsString(metadata));
        } catch (Exception e) {
            throw new IllegalStateException("无法保存系统消息元数据", e);
        }
        msg.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(msg);
        sessionManager.broadcast(Map.of("type", "system.message", "data", baseVO(msg)));
    }

    @Override
    public List<ChatMessageVO> getRecentMessages(String channel, int limit, Long playerId, Long beforeId) {
        String normalized = normalizeChannel(channel);
        if (beforeId != null && beforeId <= 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "beforeId 必须为正整数");
        String scopeId = resolveScope(playerId, normalized);
        int safeLimit = Math.min(Math.max(limit, 1), MAX_RECENT_LIMIT);
        QueryWrapper<ChatMessage> query = new QueryWrapper<>();
        if ("world".equals(normalized)) query.in("channel", List.of("world", "system"));
        else query.eq("channel", normalized).eq("scope_id", scopeId);
        if (beforeId != null) query.lt("id", beforeId);
        query.orderByDesc("id").last("LIMIT " + safeLimit);
        List<ChatMessage> messages = new ArrayList<>(chatMessageMapper.selectList(query));
        Collections.reverse(messages);
        return toVOs(messages);
    }

    @Override
    public ChatSummaryVO getChatSummary() {
        ChatSummaryVO summary = new ChatSummaryVO();
        List<ChatMessage> messages = chatMessageMapper.selectList(new QueryWrapper<ChatMessage>()
                .eq("channel", "world").orderByDesc("id").last("LIMIT 20"));
        summary.setRecentMessageCount(messages.size());
        if (!messages.isEmpty()) summary.setLatestMessage(toVOs(List.of(messages.get(0))).get(0));
        return summary;
    }

    private List<ChatMessageVO> toVOs(List<ChatMessage> messages) {
        if (messages.isEmpty()) return List.of();
        Set<Long> senderIds = new HashSet<>();
        Set<Long> replyIds = new HashSet<>();
        for (ChatMessage message : messages) {
            if (message.getSenderPlayerId() != null && message.getSenderPlayerId() > 0
                    && !"system".equals(message.getChannel())) senderIds.add(message.getSenderPlayerId());
            if (message.getReplyToId() != null && snapshot(message) == null) replyIds.add(message.getReplyToId());
        }
        Map<Long, Player> players = new HashMap<>();
        if (!senderIds.isEmpty())
            for (Player player : playerMapper.selectBatchIds(senderIds)) players.put(player.getId(), player);
        Map<Long, String> equippedKeys = new HashMap<>();
        if (!senderIds.isEmpty())
            for (PlayerTitle equipped : playerTitleMapper.selectList(new QueryWrapper<PlayerTitle>()
                    .in("player_id", senderIds).eq("equipped", 1)))
                equippedKeys.put(equipped.getPlayerId(), equipped.getTitleKey());
        Map<String, String> titleNames = new HashMap<>();
        if (!equippedKeys.isEmpty())
            for (Title title : titleMapper.selectList(new QueryWrapper<Title>()
                    .in("title_key", new HashSet<>(equippedKeys.values()))))
                titleNames.put(title.getTitleKey(), title.getName());
        Map<Long, ChatMessage> replies = new HashMap<>();
        if (!replyIds.isEmpty())
            for (ChatMessage reply : chatMessageMapper.selectBatchIds(replyIds)) replies.put(reply.getId(), reply);
        List<ChatMessageVO> result = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            ChatMessageVO vo = baseVO(message);
            Player sender = players.get(message.getSenderPlayerId());
            if (sender != null) {
                vo.setSenderStage(stageName(sender.getCurrentStage()));
                vo.setSenderTitle(titleNames.get(equippedKeys.get(sender.getId())));
            }
            if (message.getReplyToId() != null) {
                ChatMessageVO.ReplyTo snap = snapshot(message);
                ChatMessage original = replies.get(message.getReplyToId());
                vo.setReplyTo(snap != null ? snap : new ChatMessageVO.ReplyTo(message.getReplyToId(),
                        original == null ? null : original.getSenderName(),
                        original == null ? null : preview(original.getContent())));
            }
            result.add(vo);
        }
        return result;
    }

    private ChatMessageVO.ReplyTo snapshot(ChatMessage message) {
        if (message.getReplyToId() == null || message.getMetadataJson() == null) return null;
        try {
            Map<String, Object> metadata = objectMapper.readValue(message.getMetadataJson(), new TypeReference<>() {});
            Object value = metadata.get("replyTo");
            return value == null ? null : objectMapper.convertValue(value, ChatMessageVO.ReplyTo.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static String preview(String content) {
        if (content == null) return null;
        int length = content.offsetByCodePoints(0, Math.min(40, content.codePointCount(0, content.length())));
        return content.substring(0, length);
    }

    private static String stageName(String key) {
        if (key == null) return null;
        return switch (key) {
            case "vol1_ch1" -> "第一卷·第一章";
            case "vol1_ch2" -> "第一卷·第二章";
            case "vol1_ch3" -> "第一卷·第三章";
            case "vol1_ch4" -> "第一卷·第四章";
            case "vol1_ch5" -> "第一卷·第五章";
            case "vol2_ch1" -> "第二卷·第一章";
            case "vol2_dark_spring" -> "第二卷·最黑暗春天";
            case "vol12_finale" -> "第十二卷·终章";
            case "stage_1" -> "第一阶段 — 初入星流";
            case "stage_2" -> "第二阶段 — 剧本觉醒";
            case "stage_3" -> "第三阶段 — 星辰试炼";
            case "stage_4" -> "第四阶段 — 终章前夜";
            case "stage_5" -> "第五阶段 — 结局降临";
            default -> null;
        };
    }

    private static ChatMessageVO baseVO(ChatMessage message) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(message.getId());
        vo.setChannel(message.getChannel());
        vo.setSenderPlayerId(message.getSenderPlayerId());
        vo.setSenderName(message.getSenderName());
        vo.setMessageType(message.getMessageType());
        vo.setContent(message.getContent());
        vo.setCreatedAt(message.getCreatedAt() == null ? null : message.getCreatedAt().toString());
        return vo;
    }
}
