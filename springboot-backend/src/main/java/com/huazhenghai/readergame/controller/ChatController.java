package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.dto.SendChatMessageRequest;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.ChatService;
import com.huazhenghai.readergame.vo.ChatMessageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat", description = "聊天系统")
public class ChatController {

    private final ChatService chatService;
    private final PlayerMapper playerMapper;

    public ChatController(ChatService chatService, PlayerMapper playerMapper) {
        this.chatService = chatService;
        this.playerMapper = playerMapper;
    }

    @GetMapping("/recent")
    @Operation(summary = "获取最近聊天记录")
    public Result<List<ChatMessageVO>> getRecentMessages(
            @RequestParam(defaultValue = "world") String channel,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(chatService.getRecentMessages(channel, limit));
    }

    @PostMapping("/send")
    @Operation(summary = "发送聊天消息")
    public Result<ChatMessageVO> sendMessage(@Valid @RequestBody SendChatMessageRequest req) {
        Long userId = LoginUserContext.get().getUserId();

        Player player = playerMapper.selectById(req.getPlayerId());
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权使用此玩家发言");

        return Result.ok(chatService.sendWorldMessage(req.getPlayerId(), req.getContent()));
    }
}
