package com.huazhenghai.readergame.vo;

/**
 * 登录响应 VO.
 */
public class LoginVO {

    private String token;
    private UserVO user;
    private PlayerSummaryVO player;

    public LoginVO() {
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public UserVO getUser() { return user; }
    public void setUser(UserVO user) { this.user = user; }
    public PlayerSummaryVO getPlayer() { return player; }
    public void setPlayer(PlayerSummaryVO player) { this.player = player; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String token;
        private UserVO user;
        private PlayerSummaryVO player;

        public Builder token(String token) { this.token = token; return this; }
        public Builder user(UserVO user) { this.user = user; return this; }
        public Builder player(PlayerSummaryVO player) { this.player = player; return this; }

        public LoginVO build() {
            LoginVO vo = new LoginVO();
            vo.token = token;
            vo.user = user;
            vo.player = player;
            return vo;
        }
    }
}
