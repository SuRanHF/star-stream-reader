package com.huazhenghai.readergame.dto;

import com.huazhenghai.readergame.vo.PlayerVO;

/**
 * "我的信息" 视图对象 (GET /api/auth/me).
 */
public class MeResult {

    private MeUser user;
    private PlayerVO player;

    public MeResult() {
    }

    public MeResult(MeUser user, PlayerVO player) {
        this.user = user;
        this.player = player;
    }

    public MeUser getUser() {
        return user;
    }

    public void setUser(MeUser user) {
        this.user = user;
    }

    public PlayerVO getPlayer() {
        return player;
    }

    public void setPlayer(PlayerVO player) {
        this.player = player;
    }

    // ─── 手工 Builder ───

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MeUser user;
        private PlayerVO player;

        public Builder user(MeUser user) { this.user = user; return this; }
        public Builder player(PlayerVO player) { this.player = player; return this; }

        public MeResult build() {
            return new MeResult(user, player);
        }
    }

    // ─── 内嵌类: MeUser ───

    public static class MeUser {
        private Long id;
        private String username;
        private String email;
        private String role;
        private String createdAt;

        public MeUser() {
        }

        public MeUser(Long id, String username, String email, String role, String createdAt) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.role = role;
            this.createdAt = createdAt;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Long id;
            private String username;
            private String email;
            private String role;
            private String createdAt;

            public Builder id(Long id) { this.id = id; return this; }
            public Builder username(String username) { this.username = username; return this; }
            public Builder email(String email) { this.email = email; return this; }
            public Builder role(String role) { this.role = role; return this; }
            public Builder createdAt(String createdAt) { this.createdAt = createdAt; return this; }

            public MeUser build() {
                return new MeUser(id, username, email, role, createdAt);
            }
        }
    }
}
