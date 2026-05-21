package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.dto.*;
import com.huazhenghai.readergame.vo.PlayerVO;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.User;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.UserMapper;
import com.huazhenghai.readergame.security.JwtUtil;
import com.huazhenghai.readergame.service.AuthService;
import com.huazhenghai.readergame.service.PlayerService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现.
 * <p>
 * 处理用户注册、登录、获取 me 信息等业务逻辑.
 * </p>
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PlayerMapper playerMapper;
    private final PlayerService playerService;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthServiceImpl(UserMapper userMapper,
                           PlayerMapper playerMapper,
                           PlayerService playerService,
                           JwtUtil jwtUtil,
                           BCryptPasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.playerMapper = playerMapper;
        this.playerService = playerService;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 用户注册.
     * <ol>
     *   <li>检查用户名/邮箱唯一性</li>
     *   <li>BCrypt 加密密码</li>
     *   <li>保存用户</li>
     *   <li>生成 JWT Token 并返回</li>
     * </ol>
     */
    @Override
    public AuthResult register(RegisterRequest req) {
        // 检查用户名或邮箱是否已被注册
        QueryWrapper<User> query = new QueryWrapper<>();
        query.eq("username", req.getUsername())
             .or()
             .eq("email", req.getEmail());
        User existing = userMapper.selectOne(query);
        if (existing != null) {
            throw new com.huazhenghai.readergame.common.BusinessException(
                    ErrorCode.DUPLICATE_USER, "用户名或邮箱已被注册");
        }

        // BCrypt 加密密码
        String passwordHash = passwordEncoder.encode(req.getPassword());

        // 保存用户
        User user = new User();
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPasswordHash(passwordHash);
        user.setRole("player");
        userMapper.insert(user);

        // 生成 Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        return AuthResult.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .token(token)
                .build();
    }

    /**
     * 用户登录.
     * <ol>
     *   <li>通过用户名或邮箱查找用户</li>
     *   <li>BCrypt 校验密码</li>
     *   <li>生成 JWT Token 并返回</li>
     * </ol>
     */
    @Override
    public AuthResult login(LoginRequest req) {
        // 通过用户名或邮箱查找
        QueryWrapper<User> query = new QueryWrapper<>();
        query.eq("username", req.getUsernameOrEmail())
             .or()
             .eq("email", req.getUsernameOrEmail());
        User user = userMapper.selectOne(query);

        if (user == null) {
            throw new com.huazhenghai.readergame.common.BusinessException(
                    ErrorCode.INVALID_CREDENTIALS, "用户名或密码错误");
        }

        // BCrypt 校验
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new com.huazhenghai.readergame.common.BusinessException(
                    ErrorCode.INVALID_CREDENTIALS, "用户名或密码错误");
        }

        // 生成 Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        return AuthResult.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .token(token)
                .build();
    }

    /**
     * 获取当前用户信息 (含绑定的玩家角色).
     */
    @Override
    public MeResult getMe(Long userId) {
        // 查找用户
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new com.huazhenghai.readergame.common.BusinessException(
                    ErrorCode.UNAUTHORIZED, "用户不存在");
        }

        // 查找绑定的玩家
        Player player = playerService.findByUserId(userId);
        PlayerVO playerVO = null;
        if (player != null) {
            playerVO = playerService.getPlayer(player.getId());
        }

        return MeResult.builder()
                .user(MeResult.MeUser.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .role(user.getRole())
                        .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null)
                        .build())
                .player(playerVO)
                .build();
    }

    /**
     * 退出登录 (无状态 JWT, 服务端无需操作).
     */
    @Override
    public void logout() {
        // JWT 无状态认证, 服务端不维护 Session.
        // 前端删除本地存储的 Token 即可.
        // 预留: 可在此记录登出审计日志.
    }
}
