package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.Result;
import com.huazhenghai.readergame.dto.AuthResult;
import com.huazhenghai.readergame.dto.CreatePlayerRequest;
import com.huazhenghai.readergame.dto.LoginRequest;
import com.huazhenghai.readergame.dto.MeResult;
import com.huazhenghai.readergame.dto.RegisterRequest;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.AuthService;
import com.huazhenghai.readergame.service.PlayerService;
import com.huazhenghai.readergame.vo.PlayerVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器.
 * <p>
 * 处理用户注册、登录、登出、获取当前用户信息.
 * </p>
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "用户认证接口")
public class AuthController {

    private final AuthService authService;
    private final PlayerService playerService;

    public AuthController(AuthService authService, PlayerService playerService) {
        this.authService = authService;
        this.playerService = playerService;
    }

    /**
     * 用户注册.
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public Result<AuthResult> register(@Valid @RequestBody RegisterRequest req) {
        AuthResult result = authService.register(req);
        return Result.ok(result);
    }

    /**
     * 用户登录.
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public Result<AuthResult> login(@Valid @RequestBody LoginRequest req) {
        AuthResult result = authService.login(req);
        return Result.ok(result);
    }

    /**
     * 获取当前登录用户信息.
     * <p>
     * 需要 Bearer Token, 由 JwtAuthInterceptor 校验.
     * </p>
     */
    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息 (含角色)")
    public Result<MeResult> getMe() {
        Long userId = LoginUserContext.get().getUserId();
        MeResult result = authService.getMe(userId);
        return Result.ok(result);
    }

    /**
     * 创建绑定玩家角色 (前端兼容路径).
     */
    @PostMapping("/create-player")
    @Operation(summary = "创建绑定角色 (Auth路径兼容)")
    public Result<PlayerVO> createPlayer(@Valid @RequestBody CreatePlayerRequest req) {
        Long userId = LoginUserContext.get().getUserId();
        PlayerVO vo = playerService.create(req.getPlayerName(), userId);
        return Result.ok(vo);
    }

    /**
     * 退出登录.
     */
    @PostMapping("/logout")
    @Operation(summary = "退出登录")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok();
    }
}
