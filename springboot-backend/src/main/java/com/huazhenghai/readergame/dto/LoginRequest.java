package com.huazhenghai.readergame.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求 DTO.
 */
public class LoginRequest {

    @NotBlank(message = "用户名/邮箱不能为空")
    private String usernameOrEmail;

    @NotBlank(message = "密码不能为空")
    private String password;

    public LoginRequest() {
    }

    public String getUsernameOrEmail() {
        return usernameOrEmail;
    }

    public void setUsernameOrEmail(String usernameOrEmail) {
        this.usernameOrEmail = usernameOrEmail;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
