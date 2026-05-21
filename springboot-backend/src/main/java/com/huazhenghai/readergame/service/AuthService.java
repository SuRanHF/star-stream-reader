package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.dto.AuthResult;
import com.huazhenghai.readergame.dto.LoginRequest;
import com.huazhenghai.readergame.dto.MeResult;
import com.huazhenghai.readergame.dto.RegisterRequest;

/**
 * 认证服务接口.
 * <p>
 * 负责用户注册、登录、获取当前用户信息.
 * </p>
 */
public interface AuthService {

    /**
     * 用户注册.
     *
     * @param req 注册请求 (username + email + password)
     * @return 注册结果 (用户信息 + JWT Token)
     */
    AuthResult register(RegisterRequest req);

    /**
     * 用户登录.
     *
     * @param req 登录请求 (usernameOrEmail + password)
     * @return 登录结果 (用户信息 + JWT Token)
     */
    AuthResult login(LoginRequest req);

    /**
     * 获取当前用户信息 (含绑定的玩家角色).
     *
     * @param userId 用户 ID
     * @return 用户信息 + 玩家角色 (可能为 null)
     */
    MeResult getMe(Long userId);

    /**
     * 退出登录.
     * <br>
     * 由于使用 JWT 无状态认证, 服务端不维护 Session.
     * 前端删除本地 Token 即可完成登出.
     * 该方法为预留接口, 可用于记录登出日志等后续扩展.
     */
    void logout();
}
