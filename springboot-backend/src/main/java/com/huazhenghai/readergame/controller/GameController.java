package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.vo.GameBootstrapVO;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.GameBootstrapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 游戏控制器.
 * <p>
 * 提供游戏启动引导、状态同步等接口.
 * </p>
 */
@RestController
@RequestMapping("/api/game")
@Tag(name = "Game", description = "游戏核心接口")
public class GameController {

    private final GameBootstrapService bootstrapService;

    public GameController(GameBootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    /**
     * 游戏启动引导.
     * <p>
     * 前端进入游戏时调用, 获取当前用户、角色、最近日志等完整状态.
     * </p>
     */
    @GetMapping("/bootstrap")
    @Operation(summary = "游戏启动引导 (获取完整游戏状态)")
    public Result<GameBootstrapVO> bootstrap() {
        Long userId = LoginUserContext.get().getUserId();
        GameBootstrapVO vo = bootstrapService.bootstrap(userId);
        return Result.ok(vo);
    }
}
