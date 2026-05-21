package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.EndingService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/endings")
public class EndingController {

    private final EndingService endingService;
    private final PlayerMapper playerMapper;

    public EndingController(EndingService endingService, PlayerMapper playerMapper) {
        this.endingService = endingService;
        this.playerMapper = playerMapper;
    }

    @GetMapping("/{playerId}")
    public Result<Map<String, Object>> getEndings(@PathVariable Long playerId) {
        Long userId = LoginUserContext.get().getUserId();
        Player player = playerMapper.selectById(playerId);
        if (player == null || !player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问此玩家");
        List<Map<String, Object>> endings = endingService.checkEndings(playerId);
        return Result.ok(Map.of("endings", endings));
    }
}
