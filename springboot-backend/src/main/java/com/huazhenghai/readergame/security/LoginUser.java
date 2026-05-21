package com.huazhenghai.readergame.security;

/**
 * 当前登录用户信息.
 * <p>
 * JWT 校验通过后从 Token 中提取,
 * 存入 {@link LoginUserContext} 供整个请求生命周期使用.
 * </p>
 */
public class LoginUser {

    private Long userId;
    private String username;
    private String role;

    public LoginUser() {
    }

    public LoginUser(Long userId, String username, String role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
